package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import org.junit.Test

// A port of core/Tests/RibbonCoreTests/FollowingTests.swift, case for case.
class FollowingTest {
    val t0 = Instant.fromEpochSeconds(1_900_000_000)

    fun at(seconds: Double): Instant = t0 + (seconds * 1000).toLong().milliseconds

    // A psalm-shaped chapter: a title before the first verse, a verse that
    // runs from prose into a poetic line, and a verse with no words at all.
    val psalm = ScriptureChapter(
        n = 3,
        blocks = listOf(
            ScriptureBlock(s = BlockStyle.d, x = listOf(ScriptureSpan(t = "A Psalm of David."))),
            ScriptureBlock(
                s = BlockStyle.p,
                x = listOf(ScriptureSpan(v = 1, t = "one two three four five six seven eight nine ten")),
            ),
            ScriptureBlock(s = BlockStyle.p, x = listOf(ScriptureSpan(v = 2, t = "a b c d e"))),
            ScriptureBlock(s = BlockStyle.q1, x = listOf(ScriptureSpan(t = "f g h i j"))),
            ScriptureBlock(
                s = BlockStyle.p,
                x = listOf(
                    ScriptureSpan(v = 3, t = "w w w w w w w w w w"),
                    ScriptureSpan(t = " w w w w w w w w w w", w = true),
                    ScriptureSpan(v = 4, t = ""),
                ),
            ),
        ),
    )

    /** Two chapters of ten verses, twenty words each. */
    val rulers: (Int) -> ChapterRuler? = { chapter ->
        if (chapter == 1 || chapter == 2) {
            ChapterRuler(chapter = chapter, verses = (1..10).toList(), words = List(10) { 20 })
        } else {
            null
        }
    }

    fun assertPoint(point: ReadingPoint?, chapter: Int, verse: Int, part: Double) {
        assertNotNull(point, "no point")
        assertEquals(chapter, point.chapter)
        assertEquals(verse, point.verse)
        assertEquals(part, point.part, 0.0001)
    }

    fun report(chapter: Int, verse: Int, part: Double = 0.0, end: ReadingPoint? = null, settled: Boolean, second: Double) =
        ReadingReport(
            at = ReadingPoint(chapter = chapter, verse = verse, part = part),
            end = end,
            settled = settled,
            received = at(second),
        )

    // MARK: The ruler

    @Test
    fun testRulerCountsWordsPerVerse() {
        val ruler = assertNotNull(ChapterRuler.measuring(psalm))
        assertEquals(3, ruler.chapter)
        assertEquals(listOf(1, 2, 3, 4), ruler.verses)
        // The title is not on the ruler; verse 2 carries on into its poetic
        // line; a verse of no words still counts as one.
        assertEquals(listOf(10, 10, 20, 1), ruler.words)
        assertEquals(41.0, ruler.length, 0.0)
    }

    @Test
    fun testRulerOffsetAndPointRoundTrip() {
        val ruler = assertNotNull(ChapterRuler.measuring(psalm))
        assertEquals(15.0, ruler.offset(of = ReadingPoint(chapter = 3, verse = 2, part = 0.5)), 0.0)
        assertPoint(ruler.point(at = 15.0), 3, 2, 0.5)
        assertPoint(ruler.point(at = 0.0), 3, 1, 0.0)
        assertPoint(ruler.point(at = 41.0), 3, 4, 1.0)
        assertPoint(ruler.point(at = 100.0), 3, 4, 1.0)
        assertPoint(ruler.point(at = -5.0), 3, 1, 0.0)
    }

    @Test
    fun testRulerVerseNotOnTheRuler() {
        val ruler = ChapterRuler(chapter = 7, verses = listOf(1, 2, 4), words = listOf(10, 10, 10))
        // A verse this version leaves out is where the one before it ends.
        assertEquals(20.0, ruler.offset(of = ReadingPoint(chapter = 7, verse = 3, part = 0.5)), 0.0)
        assertEquals(0.0, ruler.offset(of = ReadingPoint(chapter = 7, verse = 0)), 0.0)
    }

    @Test
    fun testRulerNilWithoutVerses() {
        val title = ScriptureChapter(
            n = 1,
            blocks = listOf(ScriptureBlock(s = BlockStyle.d, x = listOf(ScriptureSpan(t = "Of David.")))),
        )
        assertNull(ChapterRuler.measuring(title))
    }

    // MARK: The guess

    @Test
    fun testEstimateIsNilBeforeAnyReport() {
        val estimate = ReadingEstimate()
        assertNull(estimate.point(now = t0, rulers = rulers))
    }

    @Test
    fun testEstimateMirrorsWhileScrolling() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, part = 0.5, settled = false, second = 0.0), rulers)
        assertPoint(estimate.point(now = at(10.0), rulers = rulers), 1, 3, 0.5)
    }

    @Test
    fun testEstimateReadsOnAtTheStartingPace() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0), rulers)
        // 3.6 words a second for five seconds: eighteen words into verse 3.
        assertPoint(estimate.point(now = at(5.0), rulers = rulers), 1, 3, 0.9)
    }

    @Test
    fun testEstimateStopsShortOfTheEndOfTheirScreen() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0), rulers)
        // A hundred words to the bottom of their screen, less three.
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 7, 0.85)
    }

    @Test
    fun testEstimateWithoutAnEndLeadsTwoVerses() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, settled = true, second = 0.0), rulers)
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 6, 0.0)
    }

    @Test
    fun testRepeatDoesNotMoveTheGuessBack() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0), rulers)
        estimate.observe(report(1, 3, part = 0.01, end = ReadingPoint(1, 8), settled = true, second = 20.0), rulers)
        // Seventy-two words on from where they came to rest, not from the
        // keepalive.
        assertPoint(estimate.point(now = at(20.0), rulers = rulers), 1, 6, 0.6)
    }

    @Test
    fun testRestAfterAScrollInFlightStartsTheReading() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, settled = false, second = 0.0), rulers)
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 2.0), rulers)
        assertPoint(estimate.point(now = at(7.0), rulers = rulers), 1, 3, 0.9)
    }

    @Test
    fun testLearnsAFasterReader() {
        val estimate = ReadingEstimate()
        // Sixty words every ten seconds: six a second.
        for ((second, verse) in listOf(0.0 to 1, 10.0 to 4, 20.0 to 7, 30.0 to 10)) {
            estimate.observe(report(1, verse, settled = true, second = second), rulers)
        }
        assertEquals(4.536433, estimate.pace, 0.0001)
    }

    @Test
    fun testPauseTeachesNothing() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 1, settled = true, second = 0.0), rulers)
        // Forty words in two minutes is somebody who stopped.
        estimate.observe(report(1, 3, settled = true, second = 120.0), rulers)
        assertEquals(3.6, estimate.pace, 0.0001)
    }

    @Test
    fun testJumpTeachesNothing() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 1, settled = true, second = 0.0), rulers)
        // Two hundred and eighty words in ten seconds is going somewhere.
        estimate.observe(report(2, 5, settled = true, second = 10.0), rulers)
        // And a chapter nobody has measured is not a distance at all.
        estimate.observe(report(5, 1, settled = true, second = 20.0), rulers)
        assertEquals(3.6, estimate.pace, 0.0001)
    }

    @Test
    fun testHoldsWhenTheyGoStill() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0), rulers)
        estimate.hold(now = at(5.0), rulers = rulers)
        assertPoint(estimate.point(now = at(60.0), rulers = rulers), 1, 3, 0.9)
        estimate.observe(report(1, 5, settled = true, second = 70.0), rulers)
        assertPoint(estimate.point(now = at(70.0), rulers = rulers), 1, 5, 0.0)
    }

    @Test
    fun testLookingBackMovesTheGuessBack() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 6, settled = true, second = 0.0), rulers)
        estimate.observe(report(1, 2, settled = true, second = 30.0), rulers)
        assertPoint(estimate.point(now = at(30.0), rulers = rulers), 1, 2, 0.0)
        assertEquals(3.6, estimate.pace, 0.0001)
    }

    @Test
    fun testGuessCrossesIntoTheNextChapter() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 10, part = 0.5, end = ReadingPoint(2, 2), settled = true, second = 0.0), rulers)
        // Ten words to the end of chapter 1 and twenty into chapter 2, less
        // three.
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 2, 1, 0.85)
    }

    @Test
    fun testWithoutTheChapterMeasuredTheGuessStaysPut() {
        val estimate = ReadingEstimate()
        estimate.observe(report(3, 4, part = 0.5, settled = true, second = 0.0), rulers)
        assertPoint(estimate.point(now = at(50.0), rulers = rulers), 3, 4, 0.5)
    }

    @Test
    fun testOlderWordArrivingLateIsIgnored() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 5, settled = false, second = 10.0), rulers)
        estimate.observe(report(1, 2, settled = true, second = 5.0), rulers)
        assertPoint(estimate.point(now = at(10.0), rulers = rulers), 1, 5, 0.0)
    }

    // MARK: The carriage

    @Test
    fun testCarriageHoldsInsideTheBand() {
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 300.0, viewport = 1000.0))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 500.0, viewport = 1000.0))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 80.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageStepsForwardPastTheMiddle() {
        assertEquals(FollowMove.Step(320.0), FollowCarriage.move(y = 620.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageStepsBackWhenTheyWentBack() {
        assertEquals(FollowMove.Step(-260.0), FollowCarriage.move(y = 40.0, viewport = 1000.0))
        assertEquals(FollowMove.Step(-800.0), FollowCarriage.move(y = -500.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageFliesWhenThePlaceIsNotLaidOut() {
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = null, viewport = 1000.0))
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = 300.0, viewport = 0.0))
    }

    @Test
    fun testCarriageRealigns() {
        assertEquals(FollowMove.Step(100.0), FollowCarriage.move(y = 400.0, viewport = 1000.0, realign = true))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 300.5, viewport = 1000.0, realign = true))
    }
}
