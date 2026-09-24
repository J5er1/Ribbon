@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.VerseAddress
import app.readribbon.services.PresenceBudget.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Presence may speak five times in thirty seconds before the server closes
 * the channel — and a closed channel is the person you follow vanishing from
 * the book. These are the rules that keep it under that, with a clock that
 * can be wound by hand.
 */
class PresenceBudgetTest {

    private var now = 1_000_000L
    private val budget = PresenceBudget(clock = { now })
    private val ruth = Uuid.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    private fun at(verse: Int, following: Uuid? = null, idle: Boolean = false) = Stance(
        position = VerseAddress("MRK", 6, verse),
        isIdle = idle,
        following = following,
        name = "Jonathan",
    )

    /** Asks, and says it if it may — as the channel does. */
    private fun say(stance: Stance?): Verdict {
        val verdict = budget.reconcile(stance)
        if (verdict == Verdict.Track || verdict == Verdict.Untrack) budget.wrote(stance)
        return verdict
    }

    @Test
    fun fourMovesGoThenTheRestWaitsForTheWindow() {
        for (verse in 1..4) {
            assertEquals(Verdict.Track, say(at(verse)))
            now += 1_000
        }
        // The fifth ordinary move waits for the oldest send to leave the
        // window, and a quarter second past it.
        val held = say(at(5))
        assertTrue(held is Verdict.Later)
        assertEquals(30_000L - 4_000L + 250L, (held as Verdict.Later).inMs)
        // Two more moves before it opens are the same wait, not two more.
        now += 500
        assertEquals(Verdict.Later(30_000L - 4_500L + 250L), say(at(6)))
        // When it opens, what goes is the latest place, once.
        now += 30_000L
        assertEquals(Verdict.Track, say(at(7)))
        assertEquals(Verdict.Quiet, say(at(7)))
    }

    @Test
    fun theSameThingTwiceIsNotSaid() {
        assertEquals(Verdict.Track, say(at(1)))
        assertEquals(Verdict.Quiet, say(at(1)))
        // How far down the chapter is not on the stance at all; a name is.
        assertEquals(Verdict.Track, say(at(1).copy(name = "Jon")))
    }

    @Test
    fun startingAFollowTakesTheKeptSlot() {
        repeat(4) { say(at(it + 1)); now += 100 }
        assertTrue(say(at(9)) is Verdict.Later)
        // "Ruth is with you" travels at once.
        assertEquals(Verdict.Track, say(at(9, following = ruth)))
        // And the slot is spent: nothing else now, not even the end of it.
        assertTrue(say(at(9)) is Verdict.Later)
    }

    @Test
    fun leavingTheBookTakesTheKeptSlot() {
        repeat(4) { say(at(it + 1)); now += 100 }
        assertEquals(Verdict.Untrack, say(null))
        // Nothing said, nothing to take back.
        assertEquals(Verdict.Quiet, say(null))
    }

    @Test
    fun aJoinAlwaysSaysWhereYouAre() {
        repeat(5) { say(at(it + 1, following = if (it % 2 == 0) ruth else null)); now += 100 }
        budget.joined()
        assertEquals(Verdict.Track, budget.reconcile(at(5), joining = true))
        // A fresh join with nothing to say says nothing.
        budget.joined()
        assertEquals(Verdict.Quiet, budget.reconcile(null, joining = true))
    }

    @Test
    fun aHeldBackSendIsReplacedByWhatIsTrueWhenItGoes() {
        // The whole window spent.
        repeat(5) { say(at(it + 1, following = if (it % 2 == 0) ruth else null)); now += 100 }
        // Present, then leave, then come back before the window opens: one
        // look later, at whatever is true then.
        assertTrue(say(at(8, following = ruth)) is Verdict.Later)
        assertTrue(say(null) is Verdict.Later)
        val wait = say(at(9, following = ruth))
        assertTrue(wait is Verdict.Later)
        now += (wait as Verdict.Later).inMs
        assertEquals(Verdict.Track, say(at(9, following = ruth)))
        // And the other way: a pending track replaced by leaving.
        repeat(4) { say(at(20 + it, following = ruth)); now += 100 }
        val leaving = say(null)
        assertTrue(leaving == Verdict.Untrack || leaving is Verdict.Later)
    }

    @Test
    fun theServersLimitStopsEverythingForAWindow() {
        say(at(1))
        budget.saturate()
        assertTrue(say(at(2)) is Verdict.Later)
        assertTrue(say(at(2, following = ruth)) is Verdict.Later)
        assertTrue(say(null) is Verdict.Later)
        now += 30_000L
        assertEquals(Verdict.Untrack, say(null))
    }

    @Test
    fun onlyThePresenceLimitSaturates() {
        assertTrue(PresenceBudget.isPresenceLimit("ClientPresenceRateLimitReached", null))
        assertTrue(PresenceBudget.isPresenceLimit("Too many presence messages per second", "presence"))
        assertTrue(PresenceBudget.isPresenceLimit("rate limit reached", "presence"))
        assertFalse(PresenceBudget.isPresenceLimit("Subscribed to PostgreSQL", "postgres_changes"))
        assertFalse(PresenceBudget.isPresenceLimit(null, "presence"))
        assertFalse(PresenceBudget.isPresenceLimit("rate limit reached", "broadcast"))
    }
}
