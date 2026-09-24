// Following (build book §4.2): "Your scroll is theirs."
//
// A follower's phone hears where the person they follow is reading only now
// and then — when a scroll of theirs comes to rest, a sample a second while
// they scroll, and a keepalive when nothing moves. People read a still
// screen top to bottom and scroll a few lines at a time, so between those
// words their eyes are somewhere further down than the last one said. This
// file is the guess at where: the last word, carried on at the reader's own
// pace, and never past the bottom of their own screen — nobody reads what
// their phone isn't showing them.
//
// It measures in words, not points. Two phones set the same chapter at
// different widths and sizes; a word is the same word on both.
//
// §13 — the pace this learns is a reading speed, and a reading speed is
// exactly what the product never collects. It is inferred here, on the
// follower's phone, held in memory for as long as the follow lasts, and
// never persisted, sent, logged or shown. Nothing in this file may ever
// surface it: there is no number anywhere in following.
//
// A port of core/Sources/RibbonCore/Following.swift, case for case.

package app.readribbon.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Finer than a verse: the verse, and how far through it the line is —
 * 0 at its first line, 1 where the next verse begins.
 */
@Serializable
data class ReadingPoint(
    val chapter: Int,
    val verse: Int,
    val part: Double = 0.0,
)

/**
 * How long each verse of a chapter is, in words — the ruler a guess is
 * measured along. A psalm's title comes before its first verse and is not
 * on it: nobody is "in" a title for long, and a presence line never says so.
 */
class ChapterRuler(val chapter: Int, verses: List<Int>, words: List<Int>) {
    /** The verses in page order. */
    val verses: List<Int>

    /** Each verse's length in words, never less than one. */
    val words: List<Int>
    private val starts: List<Double>

    init {
        val count = minOf(verses.size, words.size)
        this.verses = verses.take(count)
        this.words = words.take(count).map { maxOf(1, it) }
        val starts = mutableListOf<Double>()
        var total = 0.0
        for (length in this.words) {
            starts.add(total)
            total += length
        }
        this.starts = starts
    }

    /** Every word in the chapter. */
    val length: Double
        get() = if (starts.isEmpty()) 0.0 else starts.last() + words.last()

    /**
     * Words from the head of the chapter to the point. A verse this version
     * leaves out is where the verse before it ends.
     */
    fun offset(of: ReadingPoint): Double {
        val index = verses.indexOf(of.verse)
        if (index >= 0) {
            return starts[index] + of.part.coerceIn(0.0, 1.0) * words[index]
        }
        val before = verses.indexOfLast { it < of.verse }
        if (before < 0) return 0.0
        return starts[before] + words[before]
    }

    /** The point so many words into the chapter, held inside it. */
    fun point(at: Double): ReadingPoint {
        if (verses.isEmpty()) return ReadingPoint(chapter = chapter, verse = 1)
        val clamped = at.coerceIn(0.0, length)
        val index = starts.indexOfLast { it <= clamped }.coerceAtLeast(0)
        val part = minOf(1.0, (clamped - starts[index]) / words[index])
        return ReadingPoint(chapter = chapter, verse = verses[index], part = part)
    }

    override fun equals(other: Any?): Boolean =
        other is ChapterRuler && other.chapter == chapter &&
            other.verses == verses && other.words == words

    override fun hashCode(): Int = (chapter * 31 + verses.hashCode()) * 31 + words.hashCode()

    companion object {
        /**
         * One pass over the chapter's blocks: a span with a verse number
         * begins that verse, and every span after it belongs to it until the
         * next. Words are counted the way the book table counts them — split
         * on whitespace. Null for a chapter with no verses in it.
         */
        fun measuring(chapter: ScriptureChapter): ChapterRuler? {
            val verses = mutableListOf<Int>()
            val words = mutableListOf<Int>()
            var current: Int? = null
            for (block in chapter.blocks) {
                for (span in block.x) {
                    val v = span.v
                    if (v != null) {
                        // A verse the page comes back to is still that verse.
                        val index = verses.indexOf(v)
                        if (index >= 0) {
                            current = index
                        } else {
                            verses.add(v)
                            words.add(0)
                            current = verses.size - 1
                        }
                    }
                    val at = current ?: continue
                    words[at] += wordCount(span.t)
                }
            }
            if (verses.isEmpty()) return null
            return ChapterRuler(chapter = chapter.n, verses = verses, words = words)
        }

        private fun wordCount(text: String): Int {
            var count = 0
            var inWord = false
            for (c in text) {
                if (c.isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
            return count
        }
    }
}

/**
 * One word from the phone of the person followed: where their reading line
 * is, how far down their own screen goes, and whether they had stopped.
 * Stamped when it arrived here — nothing on the wire carries a time.
 */
data class ReadingReport(
    val at: ReadingPoint,
    /** The last thing on their screen, if they said. */
    val end: ReadingPoint? = null,
    /** Sent because the scroll came to rest, rather than in the middle of it. */
    val settled: Boolean,
    val received: Instant,
)

/** Internal mechanics, never surfaced. */
data class FollowingTuning(
    /**
     * The pace a guess starts from, in words a second: a little under the
     * 238 a minute adults read at silently (Brysbaert 2019), and over the
     * 183 of reading aloud.
     */
    val startingPace: Double = 3.6,
    /**
     * How much a sitting's evidence has to outweigh the starting pace, as
     * seconds of reading it counts for.
     */
    val startingWeight: Double = 45.0,
    val slowestPace: Double = 1.2,
    val fastestPace: Double = 9.0,
    /** A pace drifts over a sitting; older evidence fades on this scale, in seconds. */
    val paceMemory: Double = 240.0,
    /** Two rests closer together than this, in seconds, are a fidget, not reading. */
    val shortestStretch: Double = 3.0,
    /** More words than this between two rests is going somewhere, not reading there. */
    val longestStretch: Double = 400.0,
    /**
     * A stretch read slower or faster than these multiples of the pace so far
     * was a pause or a skim, and says nothing about the pace.
     */
    val slowerThan: Double = 0.3,
    val fasterThan: Double = 3.0,
    /** Nobody finishes the very last line their screen shows. */
    val endMargin: Double = 3.0,
    /**
     * How far past their line a guess may run when they didn't say where
     * their screen ends (an older app): about two verses.
     */
    val leadWithoutEnd: Double = 60.0,
    /** Close enough to the same place to be the same place. */
    val samePart: Double = 0.02,
)

/**
 * The guess at where someone is reading, between the words their phone
 * sends. The page that follows holds one for as long as the follow lasts,
 * and throws it away with it.
 */
class ReadingEstimate(val tuning: FollowingTuning = FollowingTuning()) {
    /** Their pace, in words a second — in memory only (§13). */
    var pace: Double = tuning.startingPace
        private set

    /** The latest word, which the guess runs on from. */
    private var latest: ReadingReport? = null

    /** The latest word sent at rest, which a pace is learned against. */
    private var lastRest: ReadingReport? = null

    /** Where they were when they went still, if they have. */
    private var heldAt: ReadingPoint? = null
    private var paceSeconds = 0.0
    private var paceWords = 0.0

    /** Take a new word from their phone. */
    fun observe(report: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        val latest = latest
        // Two roads bring words — the reading line and presence — and an
        // older word arriving second is not where they are now.
        if (latest != null && report.received < latest.received) return
        if (latest != null && report.settled && same(report.at, latest.at)) {
            // A repeat is not news. Someone reading down a still screen sends
            // the same place every so often, and taking it as a new start
            // would pull the guess back to the top of what they are reading.
            // The one thing it can say is that a scroll seen in flight has
            // come to rest here — which is when reading on from it starts.
            if (!latest.settled) {
                this.latest = report
                lastRest = report
            }
            return
        }
        if (report.settled) {
            lastRest?.let { learn(from = it, to = report, rulers = rulers) }
            lastRest = report
        }
        this.latest = report
        heldAt = null
    }

    /**
     * They have gone still (§4.2's "here, but still"): the guess stops where
     * it is until they move again.
     */
    fun hold(now: Instant, rulers: (Int) -> ChapterRuler?) {
        if (heldAt != null) return
        heldAt = point(now = now, rulers = rulers)
    }

    /** Where they are most likely reading now. Null before any word. */
    fun point(now: Instant, rulers: (Int) -> ChapterRuler?): ReadingPoint? {
        val latest = latest ?: return null
        heldAt?.let { return it }
        // In the middle of a scroll the page is where it is; and without the
        // chapter's words there is nothing to run on with.
        if (!latest.settled || rulers(latest.at.chapter) == null) return latest.at
        val elapsed = maxOf(0.0, (now - latest.received).toDouble(DurationUnit.SECONDS))
        var room = tuning.leadWithoutEnd
        val end = latest.end
        if (end != null) {
            distance(from = latest.at, to = end, rulers = rulers)?.let { span ->
                room = maxOf(0.0, span - tuning.endMargin)
            }
        }
        return advance(latest.at, by = minOf(pace * elapsed, room), rulers = rulers)
    }

    private fun same(a: ReadingPoint, b: ReadingPoint): Boolean =
        a.chapter == b.chapter && a.verse == b.verse && abs(a.part - b.part) < tuning.samePart

    /**
     * A stretch between two rests is reading only if it went forward, took
     * long enough to mean something, and went at a pace that is plausibly
     * the same person's. Anything else — a pause, a skim, a jump, a look
     * back — says nothing about how fast they read.
     */
    private fun learn(from: ReadingReport, to: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        val seconds = (to.received - from.received).toDouble(DurationUnit.SECONDS)
        if (seconds < tuning.shortestStretch) return
        val words = distance(from = from.at, to = to.at, rulers = rulers) ?: return
        if (words <= 0 || words > tuning.longestStretch) return
        val rate = words / seconds
        if (rate < pace * tuning.slowerThan || rate > pace * tuning.fasterThan) return
        val fade = exp(-seconds / tuning.paceMemory)
        paceSeconds = paceSeconds * fade + seconds
        paceWords = paceWords * fade + words
        val learned = (paceWords + tuning.startingPace * tuning.startingWeight) /
            (paceSeconds + tuning.startingWeight)
        pace = learned.coerceIn(tuning.slowestPace, tuning.fastestPace)
    }

    /**
     * Words from one point to another, when both chapters are measured and no
     * further apart than neighbours.
     */
    private fun distance(from: ReadingPoint, to: ReadingPoint, rulers: (Int) -> ChapterRuler?): Double? {
        val start = rulers(from.chapter) ?: return null
        if (from.chapter == to.chapter) return start.offset(of = to) - start.offset(of = from)
        if (to.chapter == from.chapter + 1) {
            val next = rulers(to.chapter) ?: return null
            return start.length - start.offset(of = from) + next.offset(of = to)
        }
        if (to.chapter == from.chapter - 1) {
            val before = rulers(to.chapter) ?: return null
            return -(before.length - before.offset(of = to) + start.offset(of = from))
        }
        return null
    }

    /**
     * So many words on from a point, into the next chapter when it is
     * measured, and otherwise to the end of this one.
     */
    private fun advance(point: ReadingPoint, by: Double, rulers: (Int) -> ChapterRuler?): ReadingPoint {
        val ruler = rulers(point.chapter) ?: return point
        val offset = ruler.offset(of = point) + by
        if (offset > ruler.length) {
            rulers(point.chapter + 1)?.let { return it.point(at = offset - ruler.length) }
        }
        return ruler.point(at = offset)
    }
}

/** What the page that follows does next. */
sealed interface FollowMove {
    /** Stay still: they are reading somewhere on this screen. */
    data object Hold : FollowMove

    /**
     * Move the page by this much, in the page's own units — down the page
     * when positive.
     */
    data class Step(val by: Double) : FollowMove

    /** They are somewhere this page hasn't set out yet: go there. */
    data object Fly : FollowMove
}

/**
 * A page that follows is moved the way a reader moves their own: held still
 * while the guess is somewhere comfortable on screen, then carried a few
 * lines at once, easing, when it isn't. Moving text is harder to read than
 * still text (Kolers 1981; Öquist & Lundin 2007), and a glide at reading
 * pace is the scroll-linked motion §9.1 forbids by another name.
 */
object FollowCarriage {
    /** The line a guess is brought to — the same upper third a page reads its own place from. */
    const val READING_LINE = 0.30

    /** A guess further down the screen than this is carried back up. */
    const val STEP_LINE = 0.50

    /** A guess higher than this — they went back — is brought down. */
    const val TOP_LINE = 0.08

    /**
     * @param y The guess's height on this screen, from the top of the
     *   viewport, in the page's own units; null when this page hasn't laid
     *   out the place yet.
     * @param viewport The viewport's height, in the same units.
     * @param realign Bring the guess to the reading line even from inside the
     *   band — the first move of a follow, and the way back after a gesture
     *   of your own.
     */
    fun move(y: Double?, viewport: Double, realign: Boolean = false): FollowMove {
        if (y == null || viewport <= 0) return FollowMove.Fly
        val distance = y - READING_LINE * viewport
        if (realign) {
            return if (abs(distance) < 1) FollowMove.Hold else FollowMove.Step(distance)
        }
        if (y > STEP_LINE * viewport || y < TOP_LINE * viewport) {
            return FollowMove.Step(distance)
        }
        return FollowMove.Hold
    }
}
