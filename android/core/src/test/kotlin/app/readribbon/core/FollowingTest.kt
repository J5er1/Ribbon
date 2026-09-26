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
    fun testEstimateStopsShortOfTheirNextScroll() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0), rulers)
        // A hundred words to the bottom of their screen; before a scroll of
        // theirs is seen, three-quarters of half of that.
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 4, 0.875)
    }

    @Test
    fun testUsualScrollLimitsTheGuess() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 1, end = ReadingPoint(1, 10), settled = true, second = 0.0), rulers)
        // They scrolled forty words: the guess runs on thirty past their
        // line, however long they stay.
        estimate.observe(report(1, 3, end = ReadingPoint(2, 2), settled = true, second = 12.0), rulers)
        assertEquals(3.543860, estimate.pace, 0.0001)
        assertPoint(estimate.point(now = at(112.0), rulers = rulers), 1, 4, 0.5)
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
        estimate.observe(report(1, 3, end = ReadingPoint(1, 10), settled = true, second = 0.0), rulers)
        estimate.observe(report(1, 3, part = 0.01, end = ReadingPoint(1, 10), settled = true, second = 10.0), rulers)
        // Thirty-six words on from where they came to rest, not from the
        // keepalive.
        assertPoint(estimate.point(now = at(10.0), rulers = rulers), 1, 4, 0.8)
    }

    @Test
    fun testRepeatUpdatesTheEndOfTheirScreen() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 10), settled = true, second = 0.0), rulers)
        // A note opened on their phone: their screen now ends a verse down.
        estimate.observe(report(1, 3, end = ReadingPoint(1, 4), settled = true, second = 5.0), rulers)
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 3, 0.375)
    }

    @Test
    fun testRestAfterAScrollInFlightStartsTheReading() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, settled = false, second = 0.0), rulers)
        estimate.observe(report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 2.0), rulers)
        assertPoint(estimate.point(now = at(7.0), rulers = rulers), 1, 3, 0.9)
    }

    @Test
    fun testInFlightSampleBehindTheGuessDoesNotPullItBack() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, end = ReadingPoint(1, 10), settled = true, second = 0.0), rulers)
        // The first sample of their next scroll is where the last one
        // ended — behind a guess that has read on since.
        estimate.observe(report(1, 3, part = 0.1, settled = false, second = 10.0), rulers)
        assertPoint(estimate.point(now = at(11.0), rulers = rulers), 1, 4, 0.8)
        // Once the scroll passes the guess, the page is where it is.
        estimate.observe(report(1, 5, part = 0.5, settled = false, second = 11.5), rulers)
        assertPoint(estimate.point(now = at(12.0), rulers = rulers), 1, 5, 0.5)
    }

    @Test
    fun testGoingBackInFlightIsFollowed() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 6, end = ReadingPoint(1, 10), settled = true, second = 0.0), rulers)
        // Forty words back is more than a fifth of their screen.
        estimate.observe(report(1, 4, settled = false, second = 5.0), rulers)
        assertPoint(estimate.point(now = at(5.0), rulers = rulers), 1, 4, 0.0)
        assertEquals(at(5.0), estimate.wentBackAt)
    }

    @Test
    fun testCarriedReportIsMirrored() {
        val estimate = ReadingEstimate()
        // Their page is being carried by a follow of their own: it is where
        // their page is, and nothing more.
        estimate.observe(
            report(1, 3, end = ReadingPoint(1, 8), settled = true, second = 0.0).copy(carried = true),
            rulers,
        )
        assertPoint(estimate.point(now = at(60.0), rulers = rulers), 1, 3, 0.0)
        estimate.observe(report(1, 6, settled = true, second = 10.0).copy(carried = true), rulers)
        assertPoint(estimate.point(now = at(70.0), rulers = rulers), 1, 6, 0.0)
        assertEquals(3.6, estimate.pace, 0.0001)
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
    fun testRestInFlightRestLearnsThePace() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 1, settled = true, second = 0.0), rulers)
        // Every scroll is seen in flight before it rests; the pace is
        // learned from rest to rest all the same.
        estimate.observe(report(1, 2, settled = false, second = 9.0), rulers)
        estimate.observe(report(1, 4, settled = true, second = 10.0), rulers)
        assertEquals(4.036364, estimate.pace, 0.0001)
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
        assertEquals(at(30.0), estimate.wentBackAt)
    }

    @Test
    fun testGuessCrossesIntoTheNextChapter() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 10, part = 0.5, end = ReadingPoint(2, 6), settled = true, second = 0.0), rulers)
        // Ten words to the end of chapter 1, a hundred on to the bottom of
        // their screen: the guess runs forty-one and a quarter, over the
        // chapter's end.
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 2, 2, 0.5625)
    }

    @Test
    fun testGuessWithoutAnEndStaysInItsChapter() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 9, settled = true, second = 0.0), rulers)
        // Forty words to the end of chapter 1, sixty allowed without an end:
        // nothing says chapter 2 is on their screen, so the guess stops at
        // chapter 1's last word.
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 10, 1.0)
    }

    @Test
    fun testGuessFromAChaptersEndWithoutAnEndStaysThere() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 10, part = 1.0, settled = true, second = 0.0), rulers)
        assertPoint(estimate.point(now = at(100.0), rulers = rulers), 1, 10, 1.0)
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

    @Test
    fun testReportedIsTheirLineNotTheGuess() {
        val estimate = ReadingEstimate()
        estimate.observe(report(1, 3, settled = true, second = 0.0), rulers)
        assertPoint(estimate.reported, 1, 3, 0.0)
    }

    // MARK: The carriage

    @Test
    fun testCarriageHoldsInsideTheBand() {
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 300.0, viewport = 1000.0))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 550.0, viewport = 1000.0))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 80.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageStepsForwardPastTheLine() {
        assertEquals(FollowMove.Step(370.0), FollowCarriage.move(y = 620.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageKeepsTheirLineOnScreen() {
        assertEquals(
            FollowMove.Step(320.0),
            FollowCarriage.move(y = 620.0, reported = 400.0, viewport = 1000.0, minStep = 60.0),
        )
        // Their own line is already near the top: nothing is worth moving.
        assertEquals(
            FollowMove.Hold,
            FollowCarriage.move(y = 620.0, reported = 100.0, viewport = 1000.0, minStep = 60.0),
        )
    }

    @Test
    fun testCarriageStepsBackOnlyWhenTheyWentBack() {
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 40.0, reported = 40.0, viewport = 1000.0))
        assertEquals(
            FollowMove.Step(-210.0),
            FollowCarriage.move(y = 40.0, reported = 40.0, viewport = 1000.0, wentBack = true),
        )
        assertEquals(FollowMove.Step(-210.0), FollowCarriage.move(y = 40.0, reported = -50.0, viewport = 1000.0))
        // An older app's presence says no line: only going back goes back.
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 40.0, viewport = 1000.0))
        assertEquals(FollowMove.Step(-210.0), FollowCarriage.move(y = 40.0, viewport = 1000.0, wentBack = true))
    }

    @Test
    fun testCarriageFliesWhenThePlaceIsNotLaidOut() {
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = null, viewport = 1000.0))
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = 300.0, viewport = 0.0))
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = null, reported = 1200.0, viewport = 1000.0))
    }

    @Test
    fun testCarriageNeverFliesPastTheirLine() {
        // The guess is in the next chapter, not yet set out; their line is
        // still on screen: the page goes as far as their line allows.
        assertEquals(
            FollowMove.Step(320.0),
            FollowCarriage.move(y = null, reported = 400.0, viewport = 1000.0, minStep = 60.0),
        )
        assertEquals(
            FollowMove.Hold,
            FollowCarriage.move(y = null, reported = 100.0, viewport = 1000.0, minStep = 60.0),
        )
    }

    @Test
    fun testCarriageRealigns() {
        assertEquals(FollowMove.Step(150.0), FollowCarriage.move(y = 400.0, viewport = 1000.0, realign = true))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 250.5, viewport = 1000.0, realign = true))
    }

    @Test
    fun testCarriageRealignKeepsTheirLineOnScreen() {
        // The guess has run on below their line — into the next chapter,
        // from a passage end. Brought to the landing line it would lift
        // their line off the top; it stops with their line at the top.
        assertEquals(
            FollowMove.Step(220.0),
            FollowCarriage.move(y = 760.0, reported = 300.0, viewport = 1000.0, realign = true),
        )
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 400.0, reported = 50.0, viewport = 1000.0, realign = true))
        // Back is not capped: their line is below the guess's anyway.
        assertEquals(
            FollowMove.Step(-150.0),
            FollowCarriage.move(y = 100.0, reported = 500.0, viewport = 1000.0, realign = true),
        )
    }

    @Test
    fun testCarriageIsCalmerUnderReduceMotion() {
        val calm = FollowCarriage.Manner.Calm
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 620.0, viewport = 1000.0, manner = calm))
        assertEquals(FollowMove.Step(550.0), FollowCarriage.move(y = 800.0, viewport = 1000.0, manner = calm))
    }

    @Test
    fun testCarriageSpokenMovesOnlyWhenTheirLineLeaves() {
        val spoken = FollowCarriage.Manner.Spoken
        assertEquals(
            FollowMove.Hold,
            FollowCarriage.move(y = 700.0, reported = 500.0, viewport = 1000.0, manner = spoken),
        )
        assertEquals(
            FollowMove.Step(950.0),
            FollowCarriage.move(y = 700.0, reported = 1200.0, viewport = 1000.0, manner = spoken),
        )
        assertEquals(
            FollowMove.Step(-350.0),
            FollowCarriage.move(y = 700.0, reported = -100.0, viewport = 1000.0, manner = spoken),
        )
        assertEquals(FollowMove.Fly, FollowCarriage.move(y = null, viewport = 1000.0, manner = spoken))
        assertEquals(FollowMove.Hold, FollowCarriage.move(y = 700.0, viewport = 1000.0, manner = spoken))
    }
}
