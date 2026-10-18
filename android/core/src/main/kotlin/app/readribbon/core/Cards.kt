@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Cards the room writes (build book §4.6, §16.4; A75). The port of
// Cards.swift, case for case; CardsTests.swift and CardsTest.kt hold the
// two to the same answers.

/** The cards at the end of a chapter. */
object ChapterCards {
    /**
     * Ribbon's card for a chapter has the same id on every phone, made from
     * the reading's and the chapter's, so two people reaching the end of a
     * chapter at once make one card and not two whose answers never meet.
     * The reading's id with the chapter folded into its last four bytes,
     * marked as a version 8 (custom) UUID.
     */
    fun ribbonCardID(readingID: Uuid, chapter: Int): Uuid {
        val bytes = readingID.toByteArray()
        bytes[12] = bytes[12] xor (chapter ushr 24).toByte()
        bytes[13] = bytes[13] xor (chapter ushr 16).toByte()
        bytes[14] = bytes[14] xor (chapter ushr 8).toByte()
        bytes[15] = bytes[15] xor chapter.toByte()
        bytes[6] = (bytes[6] and 0x0F.toByte()) or 0x80.toByte()
        bytes[8] = (bytes[8] and 0x3F.toByte()) or 0x80.toByte()
        return Uuid.fromByteArray(bytes)
    }

    /** The cards standing at a chapter's end, in the order they sit: Ribbon's, then the room's. A card set down is gone. */
    fun standing(cards: List<ReflectionCard>, readingID: Uuid, chapter: Int): List<ReflectionCard> {
        val here = cards.filter { it.readingID == readingID && it.chapter == chapter && it.state != CardState.setDown }
        val ribbon = here.filter { !it.byTheRoom }
        val room = here.filter { it.byTheRoom }.sortedBy { it.id.toString().uppercase() }
        return ribbon + room
    }

    /**
     * A card's state as the server spells it. The phones wrote "setDown",
     * which the table refuses (it says "set_down"), so a card set down was
     * never set down for anyone else.
     */
    fun stored(state: CardState): String = if (state == CardState.setDown) "set_down" else state.name

    /** A state read from the server, or null for one this phone does not know. */
    fun state(stored: String): CardState? =
        if (stored == "set_down") CardState.setDown else CardState.entries.firstOrNull { it.name == stored }

    /** Whether someone may ask the room's own question here: while no question of the room's stands at this chapter. */
    fun roomMayAsk(cards: List<ReflectionCard>, readingID: Uuid, chapter: Int): Boolean =
        standing(cards, readingID, chapter).none { it.byTheRoom }
}

/** A question someone asks the room. */
object RoomQuestion {
    /** A sentence or two. The server holds 280; the field stops well short. */
    const val maxLength = 200

    /** What was typed, as a question: its spaces and line breaks run together, trimmed. Null when there is nothing, or too much. */
    fun cleaned(text: String): String? {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (words.isEmpty() || words.length > maxLength) return null
        return words
    }
}
