@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Test

// Your shelf (A67): every book you have finished, in every room you have
// read in, and who each was read with — never as a count.
// A port of core/Tests/RibbonCoreTests/YourShelfTests.swift, case for case.
class YourShelfTest {
    val me = Uuid.parse("00000000-0000-0000-0000-000000000001")
    val ruth = Uuid.parse("00000000-0000-0000-0000-000000000002")
    val caleb = Uuid.parse("00000000-0000-0000-0000-000000000003")
    val faith = Uuid.parse("00000000-0000-0000-0000-000000000004")
    val hope = Uuid.parse("00000000-0000-0000-0000-000000000005")

    val couple = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    val study = Uuid.parse("00000000-0000-0000-0000-0000000000a2")
    val left = Uuid.parse("00000000-0000-0000-0000-0000000000a3")

    fun day(n: Int): Instant = Instant.fromEpochSeconds(1_780_000_000L + n * 86_400L)

    fun reading(id: Int, room: Uuid, book: String, finished: Int?): Reading =
        Reading(
            id = Uuid.parse("00000000-0000-0000-0000-" + id.toString().padStart(12, '0')),
            roomID = room, bookID = book, startedAt = day(0),
            finishedAt = finished?.let { day(it) }, handiwork = Handiwork(scale = FireScale.small),
        )

    fun member(person: Uuid, room: Uuid, joined: Int) =
        Membership(roomID = room, personID = person, joinedAt = day(joined))

    // The embers

    @Test
    fun testEveryFinishedBookTheFirstFinishedFirst() {
        val readings = listOf(
            reading(1, couple, "MRK", finished = 30),
            reading(2, study, "PHM", finished = 10),
            reading(3, couple, "RUT", finished = null),
            reading(5, study, "PHP", finished = 20),
        )
        assertEquals(listOf("PHM", "PHP", "MRK"), YourShelf.embers(readings).map { it.bookID })
    }

    // Leaving says "You'll keep the books on your shelf" (§6.8).
    @Test
    fun testARoomYouHaveLeftKeepsItsBooksOnYourShelf() {
        val readings = listOf(reading(1, couple, "MRK", finished = 30), reading(4, left, "JON", finished = 5))
        assertEquals(listOf("JON", "MRK"), YourShelf.embers(readings).map { it.bookID })
        val rooms = listOf(Room(id = couple, createdAt = day(0)))
        // Leaving takes only your own membership; the others' may linger on
        // the phone, and still nobody is named.
        val members = listOf(
            member(me, couple, 0), member(ruth, couple, 1),
            member(caleb, left, 1),
        )
        assertEquals(
            ShelfCompany.Alone,
            YourShelf.company(reading(4, left, "JON", finished = 5), rooms, members, me),
        )
    }

    @Test
    fun testTwoFinishedTheSameMomentKeepOneOrder() {
        val a = reading(7, couple, "JON", finished = 3)
        val b = reading(6, couple, "OBA", finished = 3)
        assertEquals(listOf("OBA", "JON"), YourShelf.embers(listOf(a, b)).map { it.bookID })
        assertEquals(listOf("OBA", "JON"), YourShelf.embers(listOf(b, a)).map { it.bookID })
    }

    @Test
    fun testNothingFinishedIsAnEmptyShelf() {
        assertEquals(emptyList(), YourShelf.embers(listOf(reading(1, couple, "MRK", finished = null))))
        assertEquals(emptyList(), YourShelf.embers(emptyList()))
    }

    // Who it was read with

    @Test
    fun testTwoOfYouIsTheirName() {
        val rooms = listOf(Room(id = couple, name = "Us", createdAt = day(0)))
        val members = listOf(member(me, couple, 0), member(ruth, couple, 1))
        assertEquals(
            ShelfCompany.People(listOf(ruth), andOthers = false),
            YourShelf.company(reading(1, couple, "MRK", finished = 3), rooms, members, me),
        )
    }

    @Test
    fun testANamedRoomOfThreeIsItsName() {
        val rooms = listOf(Room(id = study, name = "  Thursday study ", createdAt = day(0)))
        val members = listOf(member(caleb, study, 2), member(me, study, 0), member(ruth, study, 1))
        assertEquals(
            ShelfCompany.Room("Thursday study"),
            YourShelf.company(reading(1, study, "PHP", finished = 3), rooms, members, me),
        )
    }

    @Test
    fun testAnUnnamedRoomNamesTwoInTheOrderTheyCame() {
        val rooms = listOf(Room(id = study, name = "   ", createdAt = day(0)))
        val three = listOf(member(caleb, study, 2), member(me, study, 0), member(ruth, study, 1))
        val reading = reading(1, study, "PHP", finished = 3)
        assertEquals(
            ShelfCompany.People(listOf(ruth, caleb), andOthers = false),
            YourShelf.company(reading, rooms, three, me),
        )
        val five = three + listOf(member(hope, study, 4), member(faith, study, 3))
        assertEquals(
            ShelfCompany.People(listOf(ruth, caleb), andOthers = true),
            YourShelf.company(reading, rooms, five, me),
        )
    }

    @Test
    fun testOnlyThatRoomsPeopleAreCounted() {
        val rooms = listOf(Room(id = couple, createdAt = day(0)), Room(id = study, name = "Thursday study", createdAt = day(0)))
        val members = listOf(
            member(me, couple, 0), member(ruth, couple, 1),
            member(me, study, 0), member(caleb, study, 1), member(faith, study, 2),
        )
        assertEquals(
            ShelfCompany.People(listOf(ruth), andOthers = false),
            YourShelf.company(reading(1, couple, "MRK", finished = 3), rooms, members, me),
        )
    }

    @Test
    fun testNobodyElseInTheRoomIsAlone() {
        val rooms = listOf(Room(id = couple, name = "Thursday study", createdAt = day(0)))
        val members = listOf(member(me, couple, 0))
        assertEquals(
            ShelfCompany.Alone,
            YourShelf.company(reading(1, couple, "MRK", finished = 3), rooms, members, me),
        )
    }

    @Test
    fun testWithNobodySignedInEveryoneIsCompany() {
        val rooms = listOf(Room(id = couple, createdAt = day(0)))
        val members = listOf(member(ruth, couple, 1))
        assertEquals(
            ShelfCompany.People(listOf(ruth), andOthers = false),
            YourShelf.company(reading(1, couple, "MRK", finished = 3), rooms, members, null),
        )
    }
}
