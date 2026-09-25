package app.readribbon.reading

import androidx.compose.ui.unit.dp
import app.readribbon.core.ReadingPoint
import org.junit.Assert.assertEquals
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
}
