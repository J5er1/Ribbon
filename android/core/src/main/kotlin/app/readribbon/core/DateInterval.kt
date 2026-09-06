package app.readribbon.core

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A span between two instants.
 *
 * Foundation gives the Swift core `DateInterval` for free; Kotlin has no
 * equivalent, so the port carries its own with the same three behaviours the
 * fire engine and the quiet-day rules actually use — [contains],
 * [intersection] and [duration]. The semantics deliberately match
 * Foundation's, closed at both ends, so that two intervals which merely touch
 * intersect in a zero-length span rather than in nothing. That is what keeps
 * a quiet day that ends exactly when the next one begins from opening a
 * one-instant hole in the banked time.
 */
data class DateInterval(val start: Instant, val end: Instant) : Comparable<DateInterval> {

    init {
        require(end >= start) { "A DateInterval cannot end before it starts: $start..$end" }
    }

    val duration: Duration get() = end - start

    /** Closed at both ends, as Foundation's is. */
    operator fun contains(instant: Instant): Boolean = instant in start..end

    /**
     * The overlap with [other], or null when they do not meet at all. Two
     * intervals that touch at a single instant return a zero-length interval,
     * not null.
     */
    fun intersection(other: DateInterval): DateInterval? {
        val from = maxOf(start, other.start)
        val to = minOf(end, other.end)
        return if (from <= to) DateInterval(from, to) else null
    }

    fun intersects(other: DateInterval): Boolean = intersection(other) != null

    /** Ordered by where they begin, then by where they end. */
    override fun compareTo(other: DateInterval): Int =
        compareValuesBy(this, other, DateInterval::start, DateInterval::end)
}
