package app.readribbon.core

import kotlin.test.assertEquals
import org.junit.Test

// Quiet hours, drawn as the night (A67): the band's arithmetic.
// A port of core/Tests/RibbonCoreTests/QuietHoursBandTests.swift, case for case.
class QuietHoursBandTest {
    fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    // Where a time sits

    @Test
    fun testTheBandRunsNoonToNoonWithMidnightInTheMiddle() {
        assertEquals(0.0, QuietHoursBand.position(h(12)), 1e-9)
        assertEquals(0.25, QuietHoursBand.position(h(18)), 1e-9)
        assertEquals(0.5, QuietHoursBand.position(h(0)), 1e-9)
        assertEquals(0.75, QuietHoursBand.position(h(6)), 1e-9)
        assertEquals(10.0 / 24.0, QuietHoursBand.position(h(22)), 1e-9)
        assertEquals(1439.0 / 1440.0, QuietHoursBand.position(h(11, 59)), 1e-9)
    }

    @Test
    fun testAMinuteOutsideOneDayIsTheSameMinuteOfADay() {
        assertEquals(QuietHoursBand.position(h(0)), QuietHoursBand.position(h(24)), 1e-9)
        assertEquals(QuietHoursBand.position(h(23)), QuietHoursBand.position(-60), 1e-9)
        assertEquals(h(23, 59), QuietHoursBand.wrapped(-1))
        assertEquals(h(1), QuietHoursBand.wrapped(h(25)))
    }

    @Test
    fun testTheMarksAreTheEveningMidnightAndTheMorning() {
        assertEquals(listOf(h(18), h(0), h(6)), QuietHoursBand.marks)
        assertEquals(listOf(0.25, 0.5, 0.75), QuietHoursBand.marks.map { QuietHoursBand.position(it) })
    }

    // Which time a finger is over

    @Test
    fun testAPointReadsAsTheNearestQuarterHour() {
        assertEquals(h(0), QuietHoursBand.minute(0.5))
        assertEquals(h(18), QuietHoursBand.minute(0.25))
        assertEquals(h(22), QuietHoursBand.minute(QuietHoursBand.position(h(22, 7))))
        assertEquals(h(22, 15), QuietHoursBand.minute(QuietHoursBand.position(h(22, 8))))
        assertEquals(h(0), QuietHoursBand.minute(QuietHoursBand.position(h(23, 53))))
    }

    @Test
    fun testAPointPastEitherEndIsHeldThere() {
        assertEquals(h(12), QuietHoursBand.minute(-0.2))
        assertEquals(h(12), QuietHoursBand.minute(0.0))
        // The far end is noon again; a handle drawn there stops short of it.
        assertEquals(h(11, 45), QuietHoursBand.minute(1.0))
        assertEquals(h(11, 45), QuietHoursBand.minute(1.4))
    }

    @Test
    fun testEveryQuarterHourComesBackToItself() {
        for (minute in 0 until QuietHoursBand.day step QuietHoursBand.step) {
            assertEquals(minute, QuietHoursBand.minute(QuietHoursBand.position(minute)))
        }
    }

    // A step at a time

    @Test
    fun testAStepIsAQuarterOfAnHourRoundTheClock() {
        assertEquals(h(22, 15), QuietHoursBand.stepped(h(22), 1))
        assertEquals(h(21, 45), QuietHoursBand.stepped(h(22), -1))
        assertEquals(h(0), QuietHoursBand.stepped(h(23, 45), 1))
        assertEquals(h(23, 45), QuietHoursBand.stepped(h(0), -1))
        assertEquals(h(7), QuietHoursBand.stepped(h(6), 4))
    }

    @Test
    fun testATimeBetweenStepsLandsOnOneFirst() {
        assertEquals(h(22, 15), QuietHoursBand.stepped(h(22, 7), 1))
        assertEquals(h(22, 30), QuietHoursBand.stepped(h(22, 8), 1))
        assertEquals(h(0), QuietHoursBand.stepped(h(23, 58), 0))
    }

    // What is drawn quiet

    @Test
    fun testANightIsOneStretch() {
        val spans = QuietHoursBand.spans(h(22), h(6))
        assertEquals(1, spans.size)
        assertEquals(10.0 / 24.0, spans[0].from, 1e-9)
        assertEquals(0.75, spans[0].to, 1e-9)
    }

    @Test
    fun testQuietHoursAcrossNoonAreTheBandsTwoEnds() {
        val spans = QuietHoursBand.spans(h(11), h(13))
        assertEquals(2, spans.size)
        assertEquals(23.0 / 24.0, spans[0].from, 1e-9)
        assertEquals(1.0, spans[0].to, 1e-9)
        assertEquals(0.0, spans[1].from, 1e-9)
        assertEquals(1.0 / 24.0, spans[1].to, 1e-9)
    }

    @Test
    fun testQuietHoursEndingAtNoonRunToTheEnd() {
        val spans = QuietHoursBand.spans(h(22), h(12))
        assertEquals(1, spans.size)
        assertEquals(10.0 / 24.0, spans[0].from, 1e-9)
        assertEquals(1.0, spans[0].to, 1e-9)
    }

    @Test
    fun testQuietHoursBeginningAtNoonRunFromTheStart() {
        assertEquals(listOf(QuietSpan(0.0, 2.0 / 24.0)), QuietHoursBand.spans(h(12), h(14)))
    }

    @Test
    fun testTheSameMinuteAtBothEndsIsNoQuietHours() {
        assertEquals(emptyList(), QuietHoursBand.spans(h(22), h(22)))
        assertEquals(emptyList(), QuietHoursBand.spans(h(0), h(24)))
    }
}
