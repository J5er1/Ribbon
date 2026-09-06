package app.readribbon.core

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelativeTimeTest {

    // Swift's test fixture is a Gregorian `Calendar` pinned to New York.
    // kotlinx-datetime has no Calendar type and its LocalDate is always
    // proleptic Gregorian, so all that survives the port is the zone.
    private val zone: TimeZone = TimeZone.of("America/New_York")

    private fun date(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Instant =
        LocalDateTime(y, mo, d, h, mi).toInstant(zone)

    @Test
    fun testSameDayPhrases() {
        val now = date(2026, 8, 31, 21)
        assertEquals("this morning", RibbonClock.phrase(date(2026, 8, 31, 6, 40), now, zone))
        assertEquals("this afternoon", RibbonClock.phrase(date(2026, 8, 31, 14), now, zone))
        assertEquals("this evening", RibbonClock.phrase(date(2026, 8, 31, 20), now, zone))
        assertEquals("in the night", RibbonClock.phrase(date(2026, 8, 31, 3), now, zone))
    }

    @Test
    fun testYesterdayAndLastNight() {
        val now = date(2026, 8, 31, 9)
        assertEquals("last night", RibbonClock.phrase(date(2026, 8, 30, 22, 15), now, zone))
        assertEquals("yesterday", RibbonClock.phrase(date(2026, 8, 30, 10), now, zone))
    }

    @Test
    fun testWeekday() {
        // 2026-08-31 is a Monday; 2026-08-27 was the previous Thursday.
        val now = date(2026, 8, 31, 9)
        assertEquals("Thursday", RibbonClock.phrase(date(2026, 8, 27, 12), now, zone))
    }

    @Test
    fun testOlderBecomesMonth() {
        val now = date(2026, 8, 31, 9)
        assertEquals("March", RibbonClock.phrase(date(2026, 3, 2, 12), now, zone))
        assertEquals("November 2025", RibbonClock.phrase(date(2025, 11, 2, 12), now, zone))
    }

    @Test
    fun testNeverACount() {
        // The phrase must never contain a digit — "14 hours ago" is a count,
        // and a wall-clock time would reveal someone was awake at 3 a.m.
        val now = date(2026, 8, 31, 21)
        // (A year on a months-old date, like "November 2025", is an address,
        // not a count — this sweep stays inside the year to check everything
        // nearer than that.)
        for (hoursBack in 1..24 * 200 step 7) {
            val phrase = RibbonClock.phrase(now - hoursBack.hours, now, zone)
            assertFalse(
                "phrase leaked a number: $phrase",
                phrase.any { it.isDigit() }
            )
        }
    }

    @Test
    fun testEmberRanges() {
        assertEquals(
            "March – June",
            RibbonClock.emberRange(date(2026, 3, 3, 8), date(2026, 6, 20, 8), zone)
        )
        assertEquals(
            "March",
            RibbonClock.emberRange(date(2026, 3, 3, 8), date(2026, 3, 28, 8), zone)
        )
        assertEquals(
            "November 2026 – January 2027",
            RibbonClock.emberRange(date(2026, 11, 3, 8), date(2027, 1, 20, 8), zone)
        )
    }
}
