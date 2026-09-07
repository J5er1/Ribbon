package app.readribbon.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Time, days, and zones (build book §4.9).
 *
 * Timestamps are relative and coarse — "this morning", "last night",
 * "Tuesday" — never "14 hours ago", which is a count, and never a wall-clock
 * time that would reveal that someone was awake at 3 a.m. A quiet day is
 * displayed relative to the viewer's clock. Date ranges on an ember are
 * absolute ("March – June") because they are addresses in time, not
 * durations.
 */
object RibbonClock {

    /**
     * A coarse, warm phrase for when something happened, written to sit
     * mid-sentence: "Ruth read this morning". Standalone small-caps uses
     * take the same string; the face does the capitalization work.
     */
    fun phrase(
        date: Instant,
        now: Instant = Clock.System.now(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        if (date > now) return "just now"

        val at = date.toLocalDateTime(zone)
        val today = now.toLocalDateTime(zone)

        if (at.date == today.date) {
            val hour = at.hour
            if (hour < 5) return "in the night"
            if (hour < 12) return "this morning"
            if (hour < 17) return "this afternoon"
            return "this evening"
        }

        val yesterday = today.date.minus(1, DateTimeUnit.DAY)
        if (at.date == yesterday) {
            val hour = at.hour
            return if (hour >= 21 || hour < 5) "last night" else "yesterday"
        }

        // Within the last six days: the weekday, the way a person says it.
        // Swift steps six calendar days back from `now` and compares
        // instants, so the comparison keeps the time of day — this does the
        // same rather than subtracting a flat 144 hours, which would drift
        // by an hour across a daylight-saving boundary.
        val weekAgo = LocalDateTime(today.date.minus(6, DateTimeUnit.DAY), today.time).toInstant(zone)
        if (date >= weekAgo) {
            return weekdayNames[at.dayOfWeek.isoDayNumber - 1]
        }

        // Older: the month — an address, not a distance.
        val month = monthNames[at.month.number - 1]
        if (at.year == today.year) {
            return month
        }
        return "$month ${at.year}"
    }

    // The phrases are product copy, not locale output — they must read the
    // same on every device and every CI box (a stripped-down ICU renders
    // "M03" for March, which is both wrong and a leaked numeral).
    // Localization, when it comes, goes through string resources like all
    // other copy.
    //
    // Monday-first, because that is the order ISO day numbers come in;
    // Swift's array is Sunday-first because Foundation's weekday component
    // is. Same days, same strings, different index arithmetic.
    private val weekdayNames = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    )
    private val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /**
     * The date range on an ember record: "March – June", or "March" when a
     * book was read within one month, with years only when the range crosses
     * one: "November 2026 – January 2027".
     */
    fun emberRange(
        start: Instant,
        end: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val from = start.toLocalDateTime(zone)
        val to = end.toLocalDateTime(zone)
        val startMonth = monthNames[from.month.number - 1]
        val endMonth = monthNames[to.month.number - 1]

        if (from.year != to.year) {
            return "$startMonth ${from.year} – $endMonth ${to.year}"
        }
        if (startMonth == endMonth) {
            return startMonth
        }
        return "$startMonth – $endMonth"
    }
}
