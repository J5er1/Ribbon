package app.readribbon.reading

import androidx.compose.ui.unit.dp
import app.readribbon.core.ChapterRuler
import app.readribbon.core.ReadingEstimate
import app.readribbon.core.ReadingPoint
import app.readribbon.core.ReadingReport
import app.readribbon.core.TranslationID
import app.readribbon.services.HeardReading
import app.readribbon.services.LineWords
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a line falls on a chapter set on this page, and where a point is on
 * it — the two halves of following that are this page's rather than core's.
 * The `reading` line is measured with the first, and a follow maps the guess
 * back onto the page with the second, so they have to agree.
 */
class FollowAlongTest {

    private val onePixel = 0.4.dp

    /**
     * Mark 4:14–15 on a narrow column: two verses beginning on the same line.
     * Verse 16 is the next line down; the chapter ends at 200.
     */
    private val mark4 = ChapterLayout(
        verseFirstLineY = linkedMapOf(13 to 20.dp, 14 to 60.dp, 15 to 60.dp, 16 to 100.dp),
        height = 200.dp,
    )

    @Test
    fun versesThatShareALineAreTheLastOfThem() {
        val point = mark4.pointAt(chapter = 4, line = 80.dp, onePixel = onePixel)
        assertEquals(15, point.verse)
        // Halfway from their shared line to the next one down, never x / 0.
        assertEquals(0.5, point.part, 1e-9)
    }

    @Test
    fun theLastVerseRunsToTheChaptersFoot() {
        val point = mark4.pointAt(chapter = 4, line = 150.dp, onePixel = onePixel)
        assertEquals(16, point.verse)
        assertEquals(0.5, point.part, 1e-9)
        assertEquals(16, mark4.lastVerse)
    }

    @Test
    fun aLineAboveTheFirstVerseIsItsStart() {
        // The running head.
        val point = mark4.pointAt(chapter = 4, line = 5.dp, onePixel = onePixel)
        assertEquals(ReadingPoint(chapter = 4, verse = 13, part = 0.0), point)
    }

    @Test
    fun aGapUnderAPixelIsNoGap() {
        val squeezed = ChapterLayout(
            verseFirstLineY = linkedMapOf(1 to 10.dp, 2 to 10.2.dp),
            height = 10.3.dp,
        )
        val point = squeezed.pointAt(chapter = 1, line = 10.25.dp, onePixel = onePixel)
        assertEquals(2, point.verse)
        assertEquals(0.0, point.part, 0.0)
        assertTrue(point.part.isFinite())
    }

    @Test
    fun aPointGoesBackWhereItWasMeasured() {
        for (line in listOf(20, 35, 60, 61, 99, 100, 140, 199)) {
            val point = mark4.pointAt(chapter = 4, line = line.dp, onePixel = onePixel)
            val height = mark4.heightOf(point)!!
            assertEquals("line $line", line.toFloat(), height.value, 1e-3f)
        }
    }

    @Test
    fun aVerseThisVersionLeavesOutIsWhereTheOneBeforeEnds() {
        val gapped = ChapterLayout(
            verseFirstLineY = linkedMapOf(36 to 0.dp, 38 to 40.dp),
            height = 80.dp,
        )
        assertEquals(40f, gapped.heightOf(ReadingPoint(chapter = 9, verse = 37))!!.value, 0f)
        assertEquals(40f, gapped.heightOf(ReadingPoint(chapter = 9, verse = 37, part = 0.5))!!.value, 0f)
        // Before every verse on the page is the page's first line.
        assertEquals(0f, gapped.heightOf(ReadingPoint(chapter = 9, verse = 1))!!.value, 0f)
        // A part that is not a number is the start of the verse.
        assertEquals(
            40f,
            gapped.heightOf(ReadingPoint(chapter = 9, verse = 38, part = Double.NaN))!!.value,
            0f,
        )
    }

    @Test
    fun theBandGivesLessTheFurtherItIsPulled() {
        val viewport = 2000f
        assertEquals(1f, bandGive(0f, viewport), 0f)
        val near = bandGive(100f, viewport)
        val far = bandGive(800f, viewport)
        assertTrue(near < 1f && far < near && far > 0f)
        // The same either way.
        assertEquals(bandGive(-800f, viewport), far, 0f)
    }

    // A line heard twice (A60). The follower reads the New King James; the
    // person followed reads the World English, at rest at 5:3 with word 7
    // under their line. Heard before this page held chapter 5, the line kept
    // their share of the verse, 0.40; heard again once the chapter is here,
    // their word puts it at 0.48.

    private val start = Instant.fromEpochSeconds(1_000_000)
    private val theirLine = ReadingPoint(chapter = 5, verse = 3, part = 0.4)

    private fun heard(at: ReadingPoint = theirLine, after: Int = 0, word: Int = 7) = HeardReading(
        book = "JHN",
        report = ReadingReport(
            at = at,
            end = ReadingPoint(chapter = 5, verse = 6, part = 0.5),
            settled = true,
            received = start + after.seconds,
        ),
        source = null,
        words = LineWords(translation = TranslationID.web, word = word, wordsSource = "sblgnt"),
    )

    private fun setAt(heard: HeardReading, part: Double) =
        heard.report.copy(at = heard.report.at.copy(part = part))

    @Test
    fun aRepeatOfTheirLineIsSetWhereItWasSetFirst() {
        val first = heard()
        val (fed, line) = setAsHeard(first, setAt(first, 0.4), TranslationID.nkjv, last = null)
        assertEquals(0.4, fed.at.part, 0.0)
        val again = heard(after = 20)
        val (repeat, kept) = setAsHeard(again, setAt(again, 0.48), TranslationID.nkjv, line)
        assertEquals(theirLine, repeat.at)
        assertEquals(again.report.received, repeat.received)
        assertSame(line, kept)
    }

    @Test
    fun aLineThatMovedIsSetAfresh() {
        val first = heard()
        val (_, line) = setAsHeard(first, setAt(first, 0.4), TranslationID.nkjv, last = null)
        // On a few words, under another word.
        val on = heard(at = theirLine.copy(part = 0.6), after = 20, word = 9)
        assertEquals(0.66, setAsHeard(on, setAt(on, 0.66), TranslationID.nkjv, line).first.at.part, 0.0)
        // The same line, but this page is in another version now.
        val again = heard(after = 20)
        assertEquals(0.52, setAsHeard(again, setAt(again, 0.52), TranslationID.bsb, line).first.at.part, 0.0)
    }

    @Test
    fun aRepeatTeachesTheGuessNothing() {
        // Twenty verses of thirty words; chapter 5 is measured only once it
        // is here.
        val ruler = ChapterRuler(chapter = 5, verses = (1..20).toList(), words = List(20) { 30 })
        var here = false
        val rulers: (Int) -> ChapterRuler? = { if (here && it == 5) ruler else null }

        val first = heard()
        val (fed, line) = setAsHeard(first, setAt(first, 0.4), TranslationID.nkjv, last = null)
        val estimate = ReadingEstimate().apply { observe(fed, rulers) }
        val untouched = ReadingEstimate().apply { observe(fed, rulers) }

        here = true
        val again = heard(after = 20)
        estimate.observe(setAsHeard(again, setAt(again, 0.48), TranslationID.nkjv, line).first, rulers)

        // The guess reads on from their line as if the repeat had never come:
        // no scroll of a few words learned, and not pulled back to the line.
        for (later in listOf(21, 25, 40)) {
            val now = start + later.seconds
            assertEquals(untouched.point(now, rulers), estimate.point(now, rulers))
        }
    }
}
