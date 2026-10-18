@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ClosingTests.swift's tests, case for case (§6.8, A73).

class ClosingTest {
    private val me = Uuid.parse("00000000-0000-4000-8000-000000000001")
    private val ruth = Uuid.parse("00000000-0000-4000-8000-000000000002")
    private val ann = Uuid.parse("00000000-0000-4000-8000-000000000003")
    private val roomID = Uuid.parse("00000000-0000-4000-8000-0000000000aa")
    private val markID = Uuid.parse("00000000-0000-4000-8000-0000000000b1")
    private val jamesID = Uuid.parse("00000000-0000-4000-8000-0000000000b2")

    private fun at(iso: String): Instant = Instant.parse(iso)

    private fun room(closedAt: Instant? = null) =
        Room(id = roomID, name = "Thursday study", createdAt = at("2026-09-01T12:00:00Z"), closedAt = closedAt)

    private fun membership(person: Uuid, closing: Boolean, joined: String = "2026-09-01T12:00:00Z") =
        Membership(roomID = roomID, personID = person, joinedAt = at(joined), wouldClose = closing)

    // Where a room stands

    @Test
    fun testYouHaveNotAsked() {
        assertEquals(
            RoomClosing.open,
            RoomClosing.of(room(), listOf(membership(me, closing = false), membership(ruth, closing = true)), me),
        )
    }

    @Test
    fun testYouHaveAskedAndAreWaiting() {
        assertEquals(
            RoomClosing.waitingForOthers,
            RoomClosing.of(room(), listOf(membership(me, closing = true), membership(ruth, closing = false)), me),
        )
    }

    @Test
    fun testAClosedRoomIsClosedWhateverAnyoneSaid() {
        val closed = room(closedAt = at("2026-10-18T09:00:00Z"))
        assertEquals(RoomClosing.closed, RoomClosing.of(closed, listOf(membership(me, closing = false)), me))
    }

    @Test
    fun testARoomYouAreNotInIsOpenToYou() {
        assertEquals(RoomClosing.open, RoomClosing.of(room(), listOf(membership(ruth, closing = true)), me))
    }

    // Thirty quiet days

    @Test
    fun testTheLastThingHeardIsTheLatestOfAnything() {
        val fire = Handiwork(
            scale = FireScale.medium, coalDepth = 0.1, lastFuelAt = at("2026-09-20T08:00:00Z"),
            restartAt = null, stateAtLastFuel = FireState.burning,
        )
        val reading = Reading(id = markID, roomID = roomID, bookID = "MRK", startedAt = at("2026-09-03T12:00:00Z"), handiwork = fire)
        val note = Note(
            readingID = markID, authorID = ann, verse = VerseAddress("MRK", 1, 1),
            kind = NoteKind.written, body = "Later", createdAt = at("2026-09-25T08:00:00Z"),
        )
        val elsewhere = Note(
            readingID = Uuid.random(), authorID = ann, verse = VerseAddress("MRK", 1, 1),
            kind = NoteKind.written, body = "Another room", createdAt = at("2026-10-15T08:00:00Z"),
        )
        val heard = RoomSilence.lastHeard(
            room = room(), memberships = listOf(membership(me, closing = false)), readings = listOf(reading),
            notes = listOf(note, elsewhere), highlights = emptyList(),
        )
        assertEquals(at("2026-09-25T08:00:00Z"), heard)
    }

    @Test
    fun testThirtyDaysIsQuietAndTwentyNineIsNot() {
        val heard = at("2026-09-01T12:00:00Z")
        assertFalse(RoomSilence.isQuiet(lastHeard = heard, now = at("2026-09-30T12:00:00Z")))
        assertTrue(RoomSilence.isQuiet(lastHeard = heard, now = at("2026-10-02T12:00:00Z")))
    }

    // The shelf, kept

    @Test
    fun testTheFileIsNamedForTheRoom() {
        assertEquals("Ribbon - Thursday study.txt", ShelfExport.fileName("Thursday study"))
        assertEquals("Ribbon - A-B- c.txt", ShelfExport.fileName("A/B: c"))
        assertEquals("Ribbon - A room.txt", ShelfExport.fileName("   "))
    }

    @Test
    fun testTheWholeShelfReadsAsItWasRead() {
        val readings = listOf(
            Reading(id = jamesID, roomID = roomID, bookID = "JAS", startedAt = at("2026-10-13T12:00:00Z"), handiwork = Handiwork(scale = FireScale.small)),
            Reading(
                id = markID, roomID = roomID, bookID = "MRK", startedAt = at("2026-09-03T12:00:00Z"),
                finishedAt = at("2026-10-12T12:00:00Z"), handiwork = Handiwork(scale = FireScale.medium),
            ),
        )
        val notes = listOf(
            Note(readingID = markID, authorID = ann, verse = VerseAddress("MRK", 4, 9),
                kind = NoteKind.written, body = "Ears to hear.", createdAt = at("2026-09-10T12:00:00Z")),
            Note(readingID = markID, authorID = ruth, verse = VerseAddress("MRK", 1, 1),
                kind = NoteKind.voice, transcript = "Read it slowly.", createdAt = at("2026-09-05T12:00:00Z")),
            Note(readingID = markID, authorID = ruth, verse = VerseAddress("MRK", 1, 1),
                kind = NoteKind.written, body = "Beginning again.", createdAt = at("2026-09-04T12:00:00Z")),
        )
        val highlights = listOf(
            Highlight(readingID = markID, authorID = ann, range = VerseRange(bookID = "MRK", chapter = 4, startVerse = 9, endVerse = 9),
                ink = Ink.teal, createdAt = at("2026-09-10T12:00:00Z")),
            Highlight(readingID = markID, authorID = ruth, range = VerseRange(bookID = "MRK", chapter = 1, startVerse = 1, endVerse = 3),
                ink = Ink.ochre, createdAt = at("2026-09-04T12:00:00Z")),
        )
        val cards = listOf(
            ReflectionCard(readingID = markID, chapter = 2, question = "Still sealed?",
                answers = mapOf(ruth to "Not yet read by Ann"), state = CardState.sealed),
            ReflectionCard(readingID = markID, chapter = 1, question = "What stayed with you?",
                answers = mapOf(ruth to "The voice in the wilderness.", ann to "Straightway."), state = CardState.open),
        )
        val names = mapOf(ruth to "Ruth", ann to "Ann")
        val text = ShelfExport.text(
            title = "Thursday study", readings = readings, notes = notes, highlights = highlights, cards = cards,
            name = { names[it] ?: "Someone" }, keptAt = at("2026-10-18T09:00:00Z"), timeZone = TimeZone.UTC,
        )
        assertEquals(
            """
            Thursday study
            The shelf, kept 18 October 2026.

            Mark
            Read from 3 September 2026 to 12 October 2026.

            Notes

            Mark 1:1, Ruth, 4 September 2026
            Beginning again.

            Mark 1:1, Ruth, 5 September 2026
            A voice note: “Read it slowly.”

            Mark 4:9, Ann, 10 September 2026
            Ears to hear.

            Marks

            Mark 1:1–3, Ruth
            Mark 4:9, Ann

            Cards

            Chapter 1: What stayed with you?
            Ann: Straightway.
            Ruth: The voice in the wilderness.

            James
            Begun 13 October 2026, and not finished.

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun testAnEmptyShelfSaysSo() {
        val text = ShelfExport.text(
            title = "Our room", readings = emptyList(), notes = emptyList(), highlights = emptyList(), cards = emptyList(),
            name = { "Someone" }, keptAt = at("2026-10-18T09:00:00Z"), timeZone = TimeZone.UTC,
        )
        assertEquals("Our room\nThe shelf, kept 18 October 2026.\n\nNothing was read here.\n", text)
    }

    @Test
    fun testTheDayIsTheDayWhereYouAre() {
        val late = at("2026-10-18T23:30:00Z")
        assertEquals("18 October 2026", ShelfExport.day(late, TimeZone.UTC))
        assertEquals("19 October 2026", ShelfExport.day(late, TimeZone.of("Australia/Sydney")))
    }

    // Older saved state

    @Test
    fun testOlderRoomsAndMembershipsHaveSaidNothingAboutClosing() {
        val json = Json { ignoreUnknownKeys = true }
        val membership = json.decodeFromString<Membership>(
            """{"roomID":"$roomID","personID":"$me","joinedAt":"2026-09-01T12:00:00Z"}""",
        )
        assertFalse(membership.wouldClose)
        val room = json.decodeFromString<Room>("""{"id":"$roomID","createdAt":"2026-09-01T12:00:00Z"}""")
        assertNull(room.closedAt)
    }
}
