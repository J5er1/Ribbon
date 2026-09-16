package app.readribbon.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quiet hours (S19), which are the one part of the notification layer a unit
 * test can hold onto — the look book cannot see a notification and CI has no
 * device to post one to.
 *
 * The case worth having a test for at all is the default window. It runs
 * 10 p.m. to 6 a.m., so `start` is *after* `end`, which means the obvious
 * `minute >= start && minute < end` is false for every minute of the window
 * it is meant to cover and true for the whole of the day it is meant to leave
 * alone. That is the bug inverted rather than missing: it silences the two
 * hours §1 says the app is actually opened in — 6:40 a.m. and 10:15 p.m. —
 * and lets everything through at 3 a.m. Nothing about it looks wrong in a
 * diff, and a person would experience it as the app simply never telling them
 * anything.
 */
class QuietHoursTest {

    private val defaults = AppSettings()

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `the default window wraps midnight`() {
        // Inside: the evening side, across midnight, and the morning side.
        assertTrue("10 p.m. is the start of quiet hours", defaults.isQuietAt(at(22)))
        assertTrue(defaults.isQuietAt(at(23)))
        assertTrue(defaults.isQuietAt(at(0)))
        assertTrue(defaults.isQuietAt(at(2)))
        assertTrue(defaults.isQuietAt(at(5, 59)))

        // Outside: the moment it ends, and the moment before it begins.
        assertFalse("6 a.m. is the end, and ends are exclusive", defaults.isQuietAt(at(6)))
        assertFalse("the morning read", defaults.isQuietAt(at(6, 40)))
        assertFalse(defaults.isQuietAt(at(12)))
        assertFalse(defaults.isQuietAt(at(21, 59)))
    }

    @Test
    fun `a window inside one day is the ordinary comparison`() {
        val afternoon = AppSettings(quietHoursStart = at(13), quietHoursEnd = at(14))
        assertFalse(afternoon.isQuietAt(at(12, 59)))
        assertTrue(afternoon.isQuietAt(at(13)))
        assertTrue(afternoon.isQuietAt(at(13, 30)))
        assertFalse(afternoon.isQuietAt(at(14)))
        assertFalse(afternoon.isQuietAt(at(23)))
    }

    @Test
    fun `both ends at the same time means never, not always`() {
        // A person who drags both rows to the same minute has said "no quiet
        // hours". Reading it as "always" would take the app away from them.
        val none = AppSettings(quietHoursStart = at(9), quietHoursEnd = at(9))
        assertFalse(none.isQuietAt(at(9)))
        assertFalse(none.isQuietAt(at(3)))
        assertFalse(none.isQuietAt(at(21)))
    }

    @Test
    fun `midnight to midnight is also never`() {
        val none = AppSettings(quietHoursStart = 0, quietHoursEnd = 0)
        assertFalse(none.isQuietAt(0))
        assertFalse(none.isQuietAt(at(12)))
    }
}
