@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

// A mark's original-word anchors (A60), whose version is on a person's page,
// and the model's tolerance of older saved state. A port of
// core/Tests/RibbonCoreTests/VerseRangeAnchorTests.swift, case for case.
class VerseRangeAnchorTest {
    private fun keys(range: VerseRange): Set<String> =
        Json.parseToJsonElement(Json.encodeToString(VerseRange.serializer(), range)).jsonObject.keys

    @Test
    fun testADragMadeUpwardsTurnsItsOffsetsOver() {
        val r = VerseRange("MRK", 4, 9, 3, startChar = 12, endChar = 4)
        assertEquals(3, r.startVerse)
        assertEquals(9, r.endVerse)
        assertEquals(4, r.startChar)
        assertEquals(12, r.endChar)
        val one = VerseRange("MRK", 4, 9, 9, startChar = 30, endChar = 10)
        assertEquals(10, one.startChar)
        assertEquals(30, one.endChar)
        assertFalse(one.isWholeVerses)
    }

    @Test
    fun testWordArraysAreSortedAndUnique() {
        val r = VerseRange(
            "JHN", 1, 1, 2, startChar = 3, endChar = 5, charTranslation = TranslationID.bsb,
            startWords = listOf(3, 1, 3), endWords = listOf(4, 0, 4, 2), wordsSource = "s",
        )
        assertEquals(listOf(1, 3), r.startWords)
        assertEquals(listOf(0, 2, 4), r.endWords)
        assertEquals("s", r.wordsSource)
    }

    @Test
    fun testEmptyWordArraysAreNil() {
        val r = VerseRange(
            "JHN", 1, 1, 2, startChar = 3, endChar = 5, charTranslation = TranslationID.bsb,
            startWords = emptyList(), endWords = emptyList(), wordsSource = "s",
        )
        assertNull(r.startWords)
        assertNull(r.endWords)
        assertNull(r.wordsSource)
        val half = VerseRange(
            "JHN", 1, 1, 2, startChar = 3, endChar = 5, charTranslation = TranslationID.bsb,
            startWords = emptyList(), endWords = listOf(2), wordsSource = "s",
        )
        assertNull(half.startWords)
        assertEquals(listOf(2), half.endWords)
        assertEquals("s", half.wordsSource)
    }

    @Test
    fun testWordsSwapWithTheirEnds() {
        val r = VerseRange(
            "JHN", 1, 5, 3, startChar = 4, endChar = 12, charTranslation = TranslationID.bsb,
            startWords = listOf(7), endWords = listOf(2), wordsSource = "s",
        )
        assertEquals(3, r.startVerse)
        assertEquals(5, r.endVerse)
        assertEquals(12, r.startChar)
        assertEquals(4, r.endChar)
        assertEquals(listOf(2), r.startWords)
        assertEquals(listOf(7), r.endWords)
    }

    @Test
    fun testSingleVerseKeepsOneSet() {
        val r = VerseRange(
            "JHN", 1, 4, 4, startChar = 0, endChar = 9, charTranslation = TranslationID.bsb,
            startWords = listOf(4), endWords = listOf(1, 4), wordsSource = "s",
        )
        assertEquals(listOf(1, 4), r.startWords)
        assertNull(r.endWords)
        val endOnly = VerseRange(
            "JHN", 1, 4, 4, endChar = 9, charTranslation = TranslationID.bsb,
            endWords = listOf(2), wordsSource = "s",
        )
        assertEquals(listOf(2), endOnly.startWords)
        assertNull(endOnly.endWords)
        assertEquals("s", endOnly.wordsSource)
    }

    @Test
    fun testWordsAndSourceNeedEachOther() {
        val sourceOnly = VerseRange(
            "JHN", 1, 1, 1, startChar = 0, endChar = 2, charTranslation = TranslationID.bsb, wordsSource = "s",
        )
        assertNull(sourceOnly.wordsSource)
        val wordsOnly = VerseRange(
            "JHN", 1, 1, 1, startChar = 0, endChar = 2, charTranslation = TranslationID.bsb, startWords = listOf(0),
        )
        assertNull(wordsOnly.startWords)
        assertNull(wordsOnly.wordsSource)
    }

    @Test
    fun testRoundTripWithoutTheNewKeys() {
        val json = """{"bookID":"MRK","chapter":4,"startVerse":9,"endVerse":9,"startChar":3,"endChar":10,"charTranslation":"bsb"}"""
        val r = Json.decodeFromString(VerseRange.serializer(), json)
        assertEquals(3, r.startChar)
        assertEquals(TranslationID.bsb, r.charTranslation)
        assertNull(r.startWords)
        assertNull(r.endWords)
        assertNull(r.wordsSource)
        assertEquals(setOf("bookID", "chapter", "startVerse", "endVerse", "startChar", "endChar", "charTranslation"), keys(r))
        val again = Json.decodeFromString(VerseRange.serializer(), Json.encodeToString(VerseRange.serializer(), r))
        assertEquals(r, again)

        val whole = Json.decodeFromString(VerseRange.serializer(), """{"bookID":"MRK","chapter":4,"startVerse":9,"endVerse":11}""")
        assertTrue(whole.isWholeVerses)
        assertEquals(setOf("bookID", "chapter", "startVerse", "endVerse"), keys(whole))
    }

    @Test
    fun testRoundTripWithTheNewKeys() {
        val json = """{"bookID":"JHN","chapter":1,"startVerse":5,"endVerse":3,"startChar":2,"endChar":20,"charTranslation":"web",""" +
            """"startWords":[2,1],"endWords":[0],"wordsSource":"bsbt-1a2b3c4d"}"""
        val r = Json.decodeFromString(VerseRange.serializer(), json)
        // Decoding goes through the same normalisation: the ends turn over.
        assertEquals(3, r.startVerse)
        assertEquals(listOf(0), r.startWords)
        assertEquals(listOf(1, 2), r.endWords)
        assertEquals("bsbt-1a2b3c4d", r.wordsSource)
        assertEquals(
            setOf(
                "bookID", "chapter", "startVerse", "endVerse", "startChar", "endChar", "charTranslation",
                "startWords", "endWords", "wordsSource",
            ),
            keys(r),
        )
        val again = Json.decodeFromString(VerseRange.serializer(), Json.encodeToString(VerseRange.serializer(), r))
        assertEquals(r, again)
    }

    @Test
    fun testTranslationChoicePage() {
        val ruth = Person(name = "Ruth", translation = TranslationID.web)
        val room = Room(createdAt = Instant.fromEpochSeconds(0), translation = TranslationID.nkjv)
        assertEquals(TranslationID.web, TranslationChoice.page(me = ruth, room = room))
        assertEquals(TranslationID.web, TranslationChoice.page(me = ruth, room = null))
        assertEquals(TranslationID.nkjv, TranslationChoice.page(me = null, room = room))
        assertEquals(TranslationID.bsb, TranslationChoice.page(me = null, room = null))
    }

    @Test
    fun testPersonWithoutTranslationDecodes() {
        val json = """{"id":"6f9619ff-8b86-d011-b42d-00c04fc964ff","name":"Ruth"}"""
        val person = Json.decodeFromString(Person.serializer(), json)
        assertEquals("Ruth", person.name)
        assertNull(person.portraitPath)
        assertEquals(TranslationID.bsb, person.translation)
        val web = Person(name = "Naomi", portraitPath = "naomi.jpg", translation = TranslationID.web)
        assertEquals(web, Json.decodeFromString(Person.serializer(), Json.encodeToString(Person.serializer(), web)))
    }

    @Test
    fun testOnlyBundledVersionsHaveWordLinks() {
        assertTrue(TranslationRegistry.bsb.hasBundledWordLinks)
        assertTrue(TranslationRegistry.web.hasBundledWordLinks)
        assertFalse(TranslationRegistry.nkjv.hasBundledWordLinks)
        assertFalse(TranslationRegistry.niv.hasBundledWordLinks)
        assertFalse(TranslationRegistry.nasb.hasBundledWordLinks)
        assertTrue(TranslationRegistry.hasBundledWordLinks(TranslationID.web))
        assertFalse(TranslationRegistry.hasBundledWordLinks(TranslationID.nkjv))
        assertFalse(TranslationRegistry.hasBundledWordLinks(TranslationID(rawValue = "kjv")))
    }
}
