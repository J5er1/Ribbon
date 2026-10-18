@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// CardsTests.swift's tests, case for case (§4.6, §16.4, A74).

class CardsTest {
    private val ruth = Uuid.parse("00000000-0000-4000-8000-000000000002")
    private val markID = Uuid.parse("00000000-0000-4000-8000-0000000000b1")
    private val jamesID = Uuid.parse("00000000-0000-4000-8000-0000000000b2")

    private fun at(iso: String): Instant = Instant.parse(iso)

    // Ribbon's card, the same on every phone

    @Test
    fun testRibbonsCardIdIsTheReadingsWithTheChapterFoldedIn() {
        assertEquals(Uuid.parse("00000000-0000-8000-8000-0000000000b2"), ChapterCards.ribbonCardID(markID, 3))
        assertEquals(Uuid.parse("00000000-0000-8000-8000-000000000027"), ChapterCards.ribbonCardID(markID, 150))
        assertEquals(
            Uuid.parse("6f9619ff-8b86-8d01-b42d-00cf4fc964ef"),
            ChapterCards.ribbonCardID(Uuid.parse("6f9619ff-8b86-4d01-b42d-00cf4fc964ff"), 16),
        )
    }

    @Test
    fun testEveryChapterAndEveryReadingHasItsOwn() {
        val one = ChapterCards.ribbonCardID(markID, 1)
        assertEquals(one, ChapterCards.ribbonCardID(markID, 1))
        assertNotEquals(one, ChapterCards.ribbonCardID(markID, 2))
        assertNotEquals(one, ChapterCards.ribbonCardID(jamesID, 1))
        assertNotEquals(one, markID)
    }

    @Test
    fun testAStateIsSpelledAsTheServerSpellsIt() {
        assertEquals("set_down", ChapterCards.stored(CardState.setDown))
        assertEquals("sealed", ChapterCards.stored(CardState.sealed))
        assertEquals("open", ChapterCards.stored(CardState.open))
        assertEquals(CardState.setDown, ChapterCards.state("set_down"))
        assertEquals(CardState.open, ChapterCards.state("open"))
        assertNull(ChapterCards.state("gone"))
    }

    // The cards at a chapter's end

    @Test
    fun testRibbonsCardStandsFirstAndASetDownCardIsGone() {
        val ribbon = ReflectionCard(readingID = markID, chapter = 1, question = "Which line held you?")
        val room = ReflectionCard(
            readingID = markID, chapter = 1, question = "What would you have said to him?",
            byTheRoom = true, writtenBy = ruth,
        )
        val setDown = ReflectionCard(
            readingID = markID, chapter = 1, question = "Asked and set down", state = CardState.setDown,
            byTheRoom = true, writtenBy = ruth,
        )
        val elsewhere = listOf(
            ReflectionCard(readingID = markID, chapter = 2, question = "Another chapter"),
            ReflectionCard(readingID = jamesID, chapter = 1, question = "Another book"),
        )
        val standing = ChapterCards.standing(listOf(room, setDown, ribbon) + elsewhere, markID, 1)
        assertEquals(listOf("Which line held you?", "What would you have said to him?"), standing.map { it.question })
    }

    @Test
    fun testTheRoomMayAskWhileNoQuestionOfItsOwnStands() {
        val ribbon = ReflectionCard(readingID = markID, chapter = 1, question = "Which line held you?")
        val room = ReflectionCard(readingID = markID, chapter = 1, question = "Ours", byTheRoom = true, writtenBy = ruth)
        val setDown = room.copy(state = CardState.setDown)
        assertTrue(ChapterCards.roomMayAsk(listOf(ribbon), markID, 1))
        assertFalse(ChapterCards.roomMayAsk(listOf(ribbon, room), markID, 1))
        assertTrue(ChapterCards.roomMayAsk(listOf(ribbon, setDown), markID, 1))
        assertTrue(ChapterCards.roomMayAsk(listOf(ribbon, room), markID, 2))
    }

    // A question, as it is asked

    @Test
    fun testAQuestionIsItsWordsRunTogether() {
        assertEquals("What would you have said?", RoomQuestion.cleaned("  What would\n\nyou   have said? "))
        assertNull(RoomQuestion.cleaned(" \n "))
        assertEquals(200, RoomQuestion.cleaned("a".repeat(200))?.length)
        assertNull(RoomQuestion.cleaned("a".repeat(201)))
    }

    // The shelf says whose question it was

    @Test
    fun testTheShelfSaysWhoAskedTheRoomsQuestion() {
        val ann = Uuid.parse("00000000-0000-4000-8000-000000000003")
        val reading = Reading(
            id = markID, roomID = Uuid.random(), bookID = "MRK", startedAt = at("2026-09-03T12:00:00Z"),
            handiwork = Handiwork(scale = FireScale.small),
        )
        val cards = listOf(
            ReflectionCard(
                readingID = markID, chapter = 1, question = "Ours?", answers = mapOf(ann to "Yes."),
                state = CardState.open, byTheRoom = true, writtenBy = ruth,
            ),
            ReflectionCard(
                readingID = markID, chapter = 1, question = "Ribbon's?", answers = mapOf(ann to "Also."),
                state = CardState.open,
            ),
        )
        val text = ShelfExport.text(
            title = "Us", readings = listOf(reading), notes = emptyList(), highlights = emptyList(), cards = cards,
            name = { if (it == ruth) "Ruth" else "Ann" }, keptAt = at("2026-10-18T09:00:00Z"),
            timeZone = TimeZone.UTC,
        )
        assertEquals(
            """
            Us
            The shelf, kept 18 October 2026.

            Mark
            Begun 3 September 2026, and not finished.

            Cards

            Chapter 1: Ribbon's?
            Ann: Also.

            Chapter 1, asked by Ruth: Ours?
            Ann: Yes.

            """.trimIndent(),
            text,
        )
    }

    // Older cards

    @Test
    fun testACardKeptBeforeTheRoomCouldAskIsRibbons() {
        val json = """
            {"id":"00000000-0000-4000-8000-0000000000c1","readingID":"00000000-0000-4000-8000-0000000000b1",
             "chapter":1,"question":"Which line held you?","state":"sealed","answers":{}}
        """.trimIndent()
        val card = Json { ignoreUnknownKeys = true }.decodeFromString(ReflectionCard.serializer(), json)
        assertFalse(card.byTheRoom)
        assertNull(card.writtenBy)
    }
}
