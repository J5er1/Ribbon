@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Being found (A72). DiscoveryTests.swift's tests, case for case.

class DiscoveryTest {
    private val me = Uuid.parse("00000000-0000-4000-8000-000000000001")
    private val ruth = Uuid.parse("00000000-0000-4000-8000-000000000002")
    private val ann = Uuid.parse("00000000-0000-4000-8000-000000000003")
    private val room = Uuid.parse("00000000-0000-4000-8000-0000000000aa")
    private val start = Instant.fromEpochSeconds(1_790_000_000)

    private fun membership(person: Uuid, yes: Boolean) =
        Membership(roomID = room, personID = person, joinedAt = start, wouldOpen = yes)

    // One line in your own words

    @Test
    fun testALineIsTrimmedAndClosedUp() {
        assertEquals("Reading Mark, slowly.", OwnLine.clean("  Reading   Mark,\n slowly. "))
        assertNull(OwnLine.clean("   \n "))
        assertNull(OwnLine.clean(null))
    }

    @Test
    fun testALongLineIsCutAtAWord() {
        val line = "word ".repeat(40)
        val cut = OwnLine.clean(line)!!
        assertTrue(cut.length <= OwnLine.limit)
        assertFalse(cut.endsWith(" "))
        assertTrue(cut.endsWith("word"))
        // One word longer than the limit is cut where the limit falls.
        assertEquals(OwnLine.limit, OwnLine.clean("a".repeat(200))?.length)
        // An ask may say more.
        assertTrue(OwnLine.clean(line, limit = OwnLine.askLimit)!!.length > OwnLine.limit)
    }

    // Being found

    @Test
    fun testBeingFoundIsOffAndNeedsThirteenSaid() {
        val fresh = BeingFound()
        assertFalse(fresh.findable)
        assertFalse(fresh.canBeFindable)
        assertTrue(BeingFound(saidThirteenAt = start).canBeFindable)
    }

    // A room is open only while everyone has said yes

    @Test
    fun testYouHaveNotSaidYes() {
        assertEquals(RoomOpenness.closed, RoomOpenness.of(listOf(membership(me, false), membership(ruth, true)), me))
    }

    @Test
    fun testYouHaveAndSomebodyHasNot() {
        assertEquals(
            RoomOpenness.waitingForOthers,
            RoomOpenness.of(listOf(membership(me, true), membership(ruth, true), membership(ann, false)), me),
        )
    }

    @Test
    fun testEveryoneHas() {
        assertEquals(RoomOpenness.open, RoomOpenness.of(listOf(membership(me, true), membership(ruth, true)), me))
        // A room of one is open once its one person says yes.
        assertEquals(RoomOpenness.open, RoomOpenness.of(listOf(membership(me, true)), me))
    }

    @Test
    fun testARoomYouAreNotInIsClosedToYou() {
        assertEquals(RoomOpenness.closed, RoomOpenness.of(listOf(membership(ruth, true)), me))
    }

    // Asking to join

    @Test
    fun testAnAskStandsForFourteenDays() {
        val knock = Knock(roomID = room, person = PublicFace(id = ruth, name = "Ruth"), askedAt = start)
        assertTrue(knock.stands(start + 13.days))
        assertFalse(knock.stands(start + 14.days))
    }

    @Test
    fun testStandingAsksAreOldestFirstAndLapsedOnesGo() {
        val now = start + 20.days
        val old = Knock(roomID = room, person = PublicFace(id = ruth, name = "Ruth"), askedAt = start)
        val earlier = Knock(roomID = room, person = PublicFace(id = ann, name = "Ann"), askedAt = now - 5.hours)
        val later = Knock(roomID = room, person = PublicFace(id = me, name = "Me"), askedAt = now - 1.hours)
        assertEquals(listOf(ann, me), Knock.standing(listOf(later, old, earlier), now).map { it.person.id })
    }

    // A found room

    @Test
    fun testAFoundRoomIsCalledByItsNameOrItsPeople() {
        assertEquals("Thursday study", OpenRoom(id = room, name = "  Thursday  study ").shownName)
        val unnamed = OpenRoom(
            id = room,
            faces = listOf(
                PublicFace(id = ruth, name = "Ruth Abel"), PublicFace(id = ann, name = "Ann"),
                PublicFace(id = me, name = "Sam Lee"), PublicFace(id = Uuid.random(), name = "Fourth"),
            ),
        )
        assertNull(unnamed.shownName)
        assertEquals(listOf("Ruth", "Ann", "Sam"), unnamed.firstNames)
    }

    @Test
    fun testAFoundRoomsFireIsReadByTheEngineAndNeverBanked() {
        val now = start + 1.hours
        val fire = Handiwork(
            scale = FireScale.medium, coalDepth = 0.2, lastFuelAt = start, restartAt = null,
            stateAtLastFuel = FireState.burning,
        )
        assertEquals(FireState.burning, OpenRoom(id = room, bookID = "MRK", fire = fire).fireState(now))
        assertNull(OpenRoom(id = room).fireState(now))
    }

    // Finding a book by what someone types

    @Test
    fun testBooksAreFoundByTheirNames() {
        assertEquals(listOf("MRK"), BookSearch.books("mark"))
        assertEquals(listOf("MRK"), BookSearch.books("Mark 4"))
        assertEquals(listOf("MRK"), BookSearch.books("mark 4:12"))
        assertEquals(listOf("1CO"), BookSearch.books("1 cor"))
        assertEquals(listOf("PHP"), BookSearch.books("PHP"))
        assertTrue(BookSearch.books("jo").contains("JHN"))
        assertTrue(BookSearch.books("jo").contains("JOB"))
    }

    @Test
    fun testTooLittleFindsNoBook() {
        assertEquals(emptyList<String>(), BookSearch.books(""))
        assertEquals(emptyList<String>(), BookSearch.books("m"))
        assertEquals(emptyList<String>(), BookSearch.books("Thursday study"))
    }

    // Older saved state

    private val lean = Json { ignoreUnknownKeys = true }

    @Test
    fun testAMembershipSavedBeforeRoomsCouldBeFoundHasSaidNothing() {
        val decoded = lean.decodeFromString(
            Membership.serializer(),
            """{"id":"${Uuid.random()}","roomID":"$room","personID":"$me","joinedAt":"$start"}""",
        )
        assertFalse(decoded.wouldOpen)
        assertNull(decoded.ink)
    }

    @Test
    fun testARoomSavedBeforeRoomsHadALineHasNone() {
        val decoded = lean.decodeFromString(Room.serializer(), """{"id":"$room","createdAt":"$start"}""")
        assertNull(decoded.line)
        assertFalse(decoded.isPaused)
    }
}
