// Following (build book §4.2): "Your scroll is theirs."
//
// A follower's phone hears where the person they follow is reading only now
// and then — when a scroll of theirs comes to rest, a sample a second while
// they scroll, and a keepalive when nothing moves. People read a still
// screen top to bottom and scroll a few lines at a time, so between those
// words their eyes are somewhere further down than the last one said. This
// file is the guess at where: the last word, carried on at the reader's own
// pace, and never further than the scroll they would make next — nobody
// reads past the place they would have scrolled from.
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
    /**
     * Their page was being carried by a follow of their own: it is where the
     * page is, not where they read to, and nothing is guessed from it.
     */
    val carried: Boolean = false,
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
     * A guess runs on no further than this share of the scroll they usually
     * make. People scroll when their eyes near the bottom of the part of the
     * screen they like to read in, so the next scroll is the honest limit —
     * and a guess that runs on to the bottom of the screen during a pause is
     * a page carried past them, then brought back.
     */
    val shareOfTheirScroll: Double = 0.75,
    /**
     * Before a scroll of theirs has been seen: this share of what their
     * screen shows below the reading line.
     */
    val shareOfTheirScreen: Double = 0.5,
    /** How much each new scroll counts toward the one they usually make. */
    val scrollMemory: Double = 0.3,
    /**
     * How far past their line a guess may run when they didn't say where
     * their screen ends (an older app): about two verses.
     */
    val leadWithoutEnd: Double = 60.0,
    /**
     * The reading line sits under the top third of a screen, so what a
     * screen shows below it is this share of the whole.
     */
    val belowTheLine: Double = 0.7,
    /** A screen, in words, when they didn't say where theirs ends. */
    val screenWithoutEnd: Double = 100.0,
    /**
     * A report this share of their screen behind where they last came to rest
     * is them going back, not a scroll catching up.
     */
    val goingBack: Double = 0.2,
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

    /**
     * When a report last put them well behind where they had come to rest —
     * a look back, which is theirs to make and the page's to follow.
     */
    var wentBackAt: Instant? = null
        private set

    /** The latest word, which the guess runs on from. */
    private var latest: ReadingReport? = null

    /** The latest word sent at rest and not carried, which a pace is learned against. */
    private var lastRest: ReadingReport? = null

    /**
     * The guess as it stood when a scroll of theirs was first seen in flight.
     * The first sample of a scroll is where the last one ended, behind a guess
     * that has been reading on since; it must not pull the guess back.
     */
    private var beforeTheScroll: ReadingPoint? = null

    /** The size of the scroll they usually make, in words. */
    private var usualScroll: Double? = null

    /** Where they were when they went still, if they have. */
    private var heldAt: ReadingPoint? = null
    private var paceSeconds = 0.0
    private var paceWords = 0.0

    /** The place their phone last reported — their line itself, not the guess run on from it. */
    val reported: ReadingPoint?
        get() = latest?.at

    /** Take a new word from their phone. */
    fun observe(report: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        val latest = latest
        // Two roads bring words — the reading line and presence — and an
        // older word arriving second is not where they are now.
        if (latest != null && report.received < latest.received) return
        if (report.carried) {
            this.latest = report
            beforeTheScroll = null
            heldAt = null
            return
        }
        if (latest != null && !latest.carried && report.settled && same(report.at, latest.at)) {
            if (latest.settled) {
                // A repeat is not news. Someone reading down a still screen
                // sends the same place every so often, and taking it as a new
                // start would pull the guess back to the top of what they are
                // reading. Only the bottom of their screen may have moved — a
                // note opened, a size changed.
                if (report.end != null) this.latest = latest.copy(end = report.end)
            } else {
                // A scroll seen in flight has come to rest here: reading on
                // from it starts now.
                lastRest?.let { learn(from = it, to = report, rulers = rulers) }
                lastRest = report
                this.latest = report
                beforeTheScroll = null
            }
            return
        }
        var goingBack = false
        val rest = lastRest
        if (rest != null) {
            val back = distance(from = rest.at, to = report.at, rulers = rulers)
            if (back != null && back < -tuning.goingBack * screen(of = rest, rulers = rulers)) {
                wentBackAt = report.received
                goingBack = true
            }
        }
        if (!report.settled) {
            if (goingBack) {
                beforeTheScroll = null
            } else if (latest != null && (latest.settled || latest.carried)) {
                beforeTheScroll = point(now = report.received, rulers = rulers)
            }
        }
        if (report.settled) {
            rest?.let { learn(from = it, to = report, rulers = rulers) }
            lastRest = report
            beforeTheScroll = null
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
        if (latest.carried) return latest.at
        if (!latest.settled) {
            // In the middle of a scroll the page is where it is — unless it
            // is behind the guess and they are not going back, in which case
            // it is a scroll catching up with where they already are.
            val before = beforeTheScroll ?: return latest.at
            val ahead = distance(from = before, to = latest.at, rulers = rulers) ?: return latest.at
            return if (ahead < 0) before else latest.at
        }
        // Without the chapter's words there is nothing to run on with.
        if (rulers(latest.at.chapter) == null) return latest.at
        val elapsed = maxOf(0.0, (now - latest.received).toDouble(DurationUnit.SECONDS))
        var room = tuning.leadWithoutEnd
        val end = latest.end
        if (end != null) {
            distance(from = latest.at, to = end, rulers = rulers)?.let { span ->
                val scroll = usualScroll ?: (tuning.shareOfTheirScreen * span)
                room = maxOf(0.0, minOf(span - tuning.endMargin, tuning.shareOfTheirScroll * scroll))
            }
        }
        return advance(latest.at, by = minOf(pace * elapsed, room), rulers = rulers)
    }

    private fun same(a: ReadingPoint, b: ReadingPoint): Boolean =
        a.chapter == b.chapter && a.verse == b.verse && abs(a.part - b.part) < tuning.samePart

    /** Their whole screen, in words, from what a rest said about it. */
    private fun screen(of: ReadingReport, rulers: (Int) -> ChapterRuler?): Double {
        val end = of.end ?: return tuning.screenWithoutEnd
        val below = distance(from = of.at, to = end, rulers = rulers)
        if (below == null || below <= 0) return tuning.screenWithoutEnd
        return below / tuning.belowTheLine
    }

    /**
     * A stretch between two rests is reading if it went forward, not too
     * far, and took long enough to mean something. Then it says how far they
     * usually scroll — a pause before it included — and, if it went at a pace
     * plausibly the same person's, how fast they read. Anything else — a
     * fidget, a skim, a jump, a look back — says nothing.
     */
    private fun learn(from: ReadingReport, to: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        val seconds = (to.received - from.received).toDouble(DurationUnit.SECONDS)
        if (seconds < tuning.shortestStretch) return
        val words = distance(from = from.at, to = to.at, rulers = rulers) ?: return
        if (words <= 0 || words > tuning.longestStretch) return
        // A reading scroll keeps some of what was read on screen; more than a
        // screen at once is going somewhere.
        if (words <= screen(of = from, rulers = rulers)) {
            usualScroll = usualScroll?.let { it + tuning.scrollMemory * (words - it) } ?: words
        }
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
 * A page that follows is moved the way the person followed moves their own:
 * held still while they read down it, then carried several lines at once
 * when the guess leaves the upper half — "your scroll is theirs" (§4.2).
 * Still text read in steps beats text that glides (Kolers 1981; Öquist &
 * Lundin 2007), and a person's own page is still between their scrolls.
 */
object FollowCarriage {
    /** How the page may move for the person reading it. */
    enum class Manner {
        /** Steps, easing. */
        Moving,

        /** Reduce motion: fewer, larger steps, each a fade rather than a travel (§11). */
        Calm,

        /**
         * A screen reader is speaking the page: it moves only when their line
         * has left the screen, so the voice is never pulled out from under the
         * listener.
         */
        Spoken,
    }

    /**
     * The line a guess is brought to: a little above the upper third a page
     * reads its own place from, so a step shows what is coming.
     */
    const val LANDING_LINE = 0.25

    /** A guess further down the screen than this is carried up. */
    const val STEP_LINE = 0.55

    /** Under reduce motion, further down still. */
    const val CALM_STEP_LINE = 0.75

    /** A guess higher than this — they went back — is brought down. */
    const val TOP_LINE = 0.08

    /**
     * No step lifts their own line above this: the page never runs ahead of
     * what their phone actually said.
     */
    const val REPORTED_LINE = 0.08

    /**
     * @param y The guess's height on this screen, from the top of the
     *   viewport, in the page's own units; null when this page hasn't laid
     *   out the place yet (then the page flies there, unless their reported
     *   line is on screen, which it may step toward instead).
     * @param reported The height of the line their phone last reported, in
     *   the same units; null when it isn't laid out or came from an older
     *   app's presence, which is always a scroll behind.
     * @param viewport The viewport's height, in the same units.
     * @param minStep A step smaller than this is not worth taking.
     * @param realign Bring the guess to the landing line even from inside the
     *   band — the first move of a follow.
     * @param wentBack They have gone back since the page last moved.
     */
    fun move(
        y: Double?,
        reported: Double? = null,
        viewport: Double,
        minStep: Double = 0.0,
        realign: Boolean = false,
        wentBack: Boolean = false,
        manner: Manner = Manner.Moving,
    ): FollowMove {
        if (viewport <= 0) return FollowMove.Fly
        if (manner == Manner.Spoken) {
            val line = reported ?: y ?: return FollowMove.Fly
            if (line >= 0 && line <= viewport && !realign) return FollowMove.Hold
            val distance = line - LANDING_LINE * viewport
            return if (abs(distance) < 1) FollowMove.Hold else FollowMove.Step(distance)
        }
        if (y == null) {
            // The guess has run on into a place this page hasn't set out —
            // the next chapter, below a passage end — while their own line is
            // still here. Flying there would put the page past them; it goes
            // as far as their line allows, and waits.
            if (reported == null || reported < 0 || reported > viewport) return FollowMove.Fly
            val step = reported - REPORTED_LINE * viewport
            return if (step < maxOf(minStep, 1.0)) FollowMove.Hold else FollowMove.Step(step)
        }
        val distance = y - LANDING_LINE * viewport
        if (realign) {
            return if (abs(distance) < 1) FollowMove.Hold else FollowMove.Step(distance)
        }
        val stepAt = if (manner == Manner.Calm) CALM_STEP_LINE else STEP_LINE
        if (y > stepAt * viewport) {
            var step = distance
            if (reported != null) step = minOf(step, reported - REPORTED_LINE * viewport)
            return if (step < maxOf(minStep, 1.0)) FollowMove.Hold else FollowMove.Step(step)
        }
        if (y < TOP_LINE * viewport) {
            // The guess never goes back on its own; only a report does. A
            // report behind a guess that ran on is the guess being wrong, and
            // turning the page back for it is the overshoot §9.1 forbids
            // (I30). Their going back, or their line having left the top of
            // the screen, is theirs. An older app's presence, which says no
            // line at all, only ever goes back by going back.
            if (!wentBack && (reported == null || reported >= 0)) return FollowMove.Hold
            return FollowMove.Step(distance)
        }
        return FollowMove.Hold
    }
}
