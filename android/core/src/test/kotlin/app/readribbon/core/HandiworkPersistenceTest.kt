package app.readribbon.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Not a port of a Swift test — this one exists only on Android, to hold down
 * an invariant the port could plausibly lose.
 *
 * The Swift `Handiwork` keeps `lastCoalCreditAt` private, and a Swift
 * struct's synthesized Codable still encodes it, so on iOS the coal-credit
 * clock survives a save and reload. The Kotlin twin keeps it private in the
 * class body, and it survives for a different reason: kotlinx-serialization
 * encodes every property with a backing field. That is a fact about the
 * serialization library rather than about this file, which is exactly the
 * kind of thing a later refactor breaks without noticing — so it is asserted
 * here.
 *
 * If this test ever fails, the two platforms have started deepening their
 * coal beds on different schedules (§4.1, Law 4).
 */
@OptIn(ExperimentalUuidApi::class)
class HandiworkPersistenceTest {

    // The same configuration LocalStore persists with. encodeDefaults
    // matters here: with it off, kotlinx omits any property still equal to
    // its default, and lastCoalCreditAt's default is lastFuelAt.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lean = Json { ignoreUnknownKeys = true }
    private val reader = Uuid.random()
    private val start = Instant.fromEpochSeconds(1_780_000_000)

    @Test
    fun testCoalCreditClockSurvivesARoundTrip() {
        val fire = Handiwork(scale = FireScale.medium)
        fire.feed(personID = reader, now = start)
        val depthAfterFirst = fire.coalDepth
        assertTrue("the first feeding should credit the bed", depthAfterFirst > 0.0)

        val encoded = json.encodeToString(fire)
        assertTrue(
            "the store's encoder must write the credit clock",
            encoded.contains("lastCoalCreditAt"),
        )

        // What actually has to hold is behavioural, and it has to hold on
        // both routes: whether the clock was written and read back, or
        // omitted and re-seeded from lastFuelAt by its initializer, a reload
        // must land on the same schedule. The four-hour credit window is the
        // whole reason nobody can read progress off a coal bed.
        for ((label, encoder) in listOf("store" to json, "lean" to lean)) {
            val reloaded = encoder.decodeFromString<Handiwork>(encoder.encodeToString(fire))
            reloaded.feed(personID = Uuid.random(), now = start + 1.hours)
            assertEquals(
                "a reload ($label) must not hand the bed a fresh credit",
                depthAfterFirst,
                reloaded.coalDepth,
                1e-12,
            )
        }
    }

    @Test
    fun testARebuiltFireSeedsItsCreditClockFromItsLastFeeding() {
        // The sync path: a row from the backend carries no credit clock of
        // its own, and Swift's restoring initializer seeds it from the last
        // feeding rather than leaving it empty.
        val rebuilt = Handiwork(
            scale = FireScale.large,
            coalDepth = 0.4,
            lastFuelAt = start,
            stateAtLastFuel = FireState.burning,
        )
        rebuilt.feed(personID = reader, now = start + 1.hours)
        assertEquals(
            "a rebuilt fire must respect the credit window it was rebuilt into",
            0.4,
            rebuilt.coalDepth,
            1e-12,
        )
    }

    @Test
    fun testBankedIsNeverRestorableState() {
        // Banking lives on quiet days, never on the fire (§4.7).
        val rebuilt = Handiwork(
            scale = FireScale.small,
            lastFuelAt = start,
            stateAtLastFuel = FireState.banked,
        )
        assertEquals(FireState.catching, rebuilt.stateAtLastFuel)
    }

    @Test
    fun testCoalDepthIsClampedOnRebuild() {
        assertEquals(1.0, Handiwork(scale = FireScale.medium, coalDepth = 1.5).coalDepth, 1e-12)
        assertEquals(0.0, Handiwork(scale = FireScale.medium, coalDepth = -0.5).coalDepth, 1e-12)
    }
}
