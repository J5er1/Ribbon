package app.readribbon.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Quiet hours, drawn as the night (A66). S19 set the two ends with two
// pickers, one under each row — the most borrowed-looking thing in the app.
// They are one band now: a day laid out from noon to noon, so that a night
// sits whole in the middle of it and never breaks at an edge, with the
// quiet stretch banked and a handle at each end of it.
//
// This file is the band's arithmetic and nothing else: where a minute of
// the day sits on it, which minute a finger is over, how far one step moves
// a handle, and which stretch to draw. Kept here so that both phones put a
// handle on the same place for the same time, and a test can say so.
// A port of core/Sources/RibbonCore/QuietHoursBand.swift, case for case.

/** A stretch of the band, as fractions of its width from its left edge. */
data class QuietSpan(val from: Double, val to: Double)

object QuietHoursBand {
    /** Minutes in a day. */
    const val day = 24 * 60

    /** Where the band begins: noon, so a night is whole in its middle. */
    const val origin = 12 * 60

    /**
     * How far a handle moves: a quarter of an hour, by a finger and by a
     * screen reader's swipe alike.
     */
    const val step = 15

    /**
     * The hours marked under the band, left to right: six in the evening,
     * midnight, six in the morning. A mark, not a scale — three times a
     * person already knows where they are in a night.
     */
    val marks: List<Int> = listOf(18 * 60, 0, 6 * 60)

    /**
     * Where a minute of the day sits on the band: 0 is noon, ½ is midnight,
     * and the band ends just short of 1, a minute before the next noon.
     */
    fun position(minute: Int): Double = wrapped(minute - origin).toDouble() / day.toDouble()

    /**
     * The minute of the day under a point on the band, to the nearest step.
     * A point past either end is held at that end. The far end is noon
     * again, so it is read as the last step before it: a handle drawn hard
     * to the right stops at a quarter to twelve rather than jumping back to
     * the left.
     */
    fun minute(position: Double, step: Int = QuietHoursBand.step): Int {
        val held = min(max(position, 0.0), 1.0)
        // Never negative here, so Kotlin's half-up rounding is Swift's
        // half-away-from-zero.
        var minutes = (held * day.toDouble() / step.toDouble()).roundToInt() * step
        if (minutes >= day) minutes = day - step
        return wrapped(origin + minutes)
    }

    /**
     * A minute moved by whole steps, round the clock: a screen reader's
     * increment and decrement. A time between steps lands on the nearest
     * one first, so every move after it is a whole step.
     */
    fun stepped(minute: Int, steps: Int, step: Int = QuietHoursBand.step): Int {
        val snapped = (wrapped(minute).toDouble() / step.toDouble()).roundToInt() * step
        return wrapped(snapped + steps * step)
    }

    /**
     * The stretches of the band to draw as quiet, left to right in the order
     * they are reached from the start. One for a night. Two when the quiet
     * hours reach across noon — the end of the band and its start. None when
     * the two ends are the same minute, which is no quiet hours at all (S19,
     * and `AppSettings.isQuiet`).
     */
    fun spans(start: Int, end: Int): List<QuietSpan> {
        if (wrapped(start) == wrapped(end)) return emptyList()
        val from = position(start)
        val to = position(end)
        if (from < to) return listOf(QuietSpan(from, to))
        return listOf(QuietSpan(from, 1.0), QuietSpan(0.0, to)).filter { it.to > it.from }
    }

    /** Any whole number of minutes, as a minute of one day. */
    fun wrapped(minute: Int): Int = ((minute % day) + day) % day
}
