@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ReflectionCardTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun reflectionCardJSONRoundTrip() {
        val cardID = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val readingID = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val authorID1 = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val authorID2 = Uuid.parse("44444444-4444-4444-4444-444444444444")

        val card = ReflectionCard(
            id = cardID,
            readingID = readingID,
            chapter = 4,
            question = "What did you notice that the other one probably didn't?",
            answers = mapOf(
                authorID1 to "The silence at the end of the storm.",
                authorID2 to "How quickly fear became awe.",
            ),
            state = CardState.open,
            openedAt = Instant.fromEpochSeconds(1700000000),
        )

        val encoded = json.encodeToString(card)
        val jsonElement = json.parseToJsonElement(encoded).jsonObject
        val answersObj = jsonElement["answers"]?.jsonObject
        assertNotNull(answersObj, "answers must serialize as a JSON object")

        val decoded = json.decodeFromString<ReflectionCard>(encoded)
        assertEquals(cardID, decoded.id)
        assertEquals(readingID, decoded.readingID)
        assertEquals(4, decoded.chapter)
        assertEquals(CardState.open, decoded.state)
        assertEquals("The silence at the end of the storm.", decoded.answers[authorID1])
        assertEquals("How quickly fear became awe.", decoded.answers[authorID2])
    }

    @Test
    fun reflectionPrompts() {
        val prompt1 = ReflectionPrompts.prompt(1)
        val prompt2 = ReflectionPrompts.prompt(2)
        assertTrue(prompt1.isNotEmpty())
        assertTrue(prompt2.isNotEmpty())
        assertNotEquals(prompt1, prompt2)
    }
}
