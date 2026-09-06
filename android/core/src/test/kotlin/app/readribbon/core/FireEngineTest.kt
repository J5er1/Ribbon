@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FireEngineTest {
    val ruth = Uuid.random()
    val jacob = Uuid.random()
    val epoch = Instant.fromEpochSeconds(1_900_000_000)

    fun hours(h: Double): Instant = epoch + (h * 3600).seconds

    @Test
    fun testNewFireIsCatching() {
        val fire = Handiwork(scale = FireScale.medium)
        assertEquals(FireState.catching, fire.state(now = epoch))
    }

    @Test
    fun testFirstFuelCatches() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        // First light is a restart: the fire catches, it does not jump to
        // burning.
        assertEquals(FireState.catching, fire.state(now = hours(1.0)))
    }

    @Test
    fun testReturningLiftsToBurning() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = ruth, now = hours(10.0))
        assertEquals(FireState.burning, fire.state(now = hours(11.0)))
    }

    @Test
    fun testTwoPeopleInWindowIsSteady() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = jacob, now = hours(20.0))
        assertEquals(FireState.steady, fire.state(now = hours(21.0)))
    }

    @Test
    fun testMondayNightToWednesdayMorningNeverLapses() {
        // §4.1: someone who reads at 10 p.m. Monday and 9 a.m. Wednesday
        // never experiences a lapse — 35 elapsed hours sit inside the window.
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = ruth, now = hours(1.0))
        assertEquals(FireState.burning, fire.state(now = hours(1.0 + 35)))
        fire.feed(personID = ruth, now = hours(1.0 + 35))
        assertEquals(FireState.burning, fire.state(now = hours(1.0 + 35)))
    }

    @Test
    fun testSteadyEasesToBurningThenCatching() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = jacob, now = hours(2.0))
        assertEquals(FireState.steady, fire.state(now = hours(3.0)))
        // Window closes: eases to burning.
        assertEquals(FireState.burning, fire.state(now = hours(2.0 + 40)))
        // Days later: comes to rest at catching — the floor. Never banked.
        assertEquals(FireState.catching, fire.state(now = hours(2.0 + 130)))
        assertEquals(FireState.catching, fire.state(now = hours(2000.0)))
    }

    @Test
    fun testBurningComesToRestAtCatching() {
        val fire = Handiwork(scale = FireScale.small)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = ruth, now = hours(1.0))
        assertEquals(FireState.burning, fire.state(now = hours(30.0)))
        assertEquals(FireState.burning, fire.state(now = hours(1.0 + 90)))
        assertEquals(FireState.catching, fire.state(now = hours(1.0 + 100)))
    }

    @Test
    fun testFuelAfterLongQuietCatchesAgain() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = ruth, now = hours(1.0))
        // A long quiet, then fuel: catching, not burning.
        fire.feed(personID = ruth, now = hours(300.0))
        assertEquals(FireState.catching, fire.state(now = hours(300.0)))
        // Reading again soon after lifts it.
        fire.feed(personID = ruth, now = hours(305.0))
        assertEquals(FireState.burning, fire.state(now = hours(305.0)))
    }

    @Test
    fun testBankedOnlyViaQuietDay() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        // Swift's DateInterval(start:duration:); the ported DateInterval is
        // built from its two ends, so the duration is added to the start here.
        val banked = listOf(DateInterval(start = hours(5.0), end = hours(5.0) + 24.hours))
        assertEquals(FireState.banked, fire.state(now = hours(10.0), bankedIntervals = banked))
        assertEquals(FireState.catching, fire.state(now = hours(40.0), bankedIntervals = banked))
    }

    @Test
    fun testQuietDayPausesSubsiding() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = ruth, now = epoch)
        fire.feed(personID = ruth, now = hours(1.0))
        assertEquals(FireState.burning, fire.state(now = hours(2.0)))
        // A quiet day covers hours 10–34. At hour 60, raw elapsed since
        // fuel is 59h, but 24 banked hours are excluded: 35h — still inside
        // the window, still burning.
        val banked = listOf(DateInterval(start = hours(10.0), end = hours(10.0) + 24.hours))
        assertEquals(FireState.burning, fire.state(now = hours(60.0), bankedIntervals = banked))
        // Without the quiet day it would have eased past the window.
        assertEquals(FireState.burning, fire.state(now = hours(60.0))) // 59h < burningRestsAt
        assertEquals(FireState.catching, fire.state(now = hours(1.0 + 97)))
        assertEquals(
            FireState.burning,
            fire.state(now = hours(1.0 + 97), bankedIntervals = banked)
        )
    }

    @Test
    fun testCoalsDeepenAndNeverRecede() {
        val fire = Handiwork(scale = FireScale.large)
        assertEquals(0.0, fire.coalDepth, 0.0)
        fire.feed(personID = ruth, now = epoch)
        val d1 = fire.coalDepth
        assertTrue(d1 > 0)
        // Feeding again inside the coal-credit window doesn't pump the bed.
        fire.feed(personID = ruth, now = hours(1.0))
        assertEquals(d1, fire.coalDepth, 0.0)
        // Days of tending deepen it, asymptotically, never reaching 1.
        var depth = d1
        for (day in 1..400) {
            fire.feed(personID = ruth, now = hours(day.toDouble() * 24))
            assertTrue(fire.coalDepth >= depth)
            depth = fire.coalDepth
        }
        assertTrue(fire.coalDepth < 1)
    }

    @Test
    fun testRecentFuelStaysPruned() {
        val fire = Handiwork(scale = FireScale.medium)
        for (day in 0 until 60) {
            fire.feed(personID = ruth, now = hours(day.toDouble() * 24))
            fire.feed(personID = jacob, now = hours(day.toDouble() * 24 + 2))
        }
        // §13: the rolling window is the only per-person reading record.
        assertTrue(fire.recentFuel.size <= 6)
        val cutoff = hours(59.0 * 24 + 2 - 36)
        assertTrue(fire.recentFuel.all { it.at >= cutoff })
    }

    @Test
    fun testQuietDayBankedInterval() {
        val zone = TimeZone.of("America/New_York")
        val marked = Instant.fromEpochSeconds(1_900_000_000)
        val day = QuietDay(roomID = Uuid.random(), personID = ruth, markedAt = marked, timeZone = zone)
        val interval = day.bankedInterval
        assertNotNull(interval)
        checkNotNull(interval)
        assertTrue(interval.contains(marked))
        assertEquals(
            24 * 3600.0,
            interval.duration.toDouble(DurationUnit.SECONDS),
            3700.0
        ) // DST tolerance
    }

    @Test
    fun testMergedIntervalsOverlap() {
        val a = DateInterval(start = hours(0.0), end = hours(0.0) + 10.hours)
        val b = DateInterval(start = hours(5.0), end = hours(5.0) + 10.hours)
        val c = DateInterval(start = hours(30.0), end = hours(30.0) + 1.hours)
        val merged = Handiwork.merged(listOf(c, a, b))
        assertEquals(2, merged.size)
        assertEquals((15 * 3600).seconds, merged[0].duration)
    }
}
