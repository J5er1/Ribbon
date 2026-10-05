package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class APIBibleContentTest {
    // A miniature of API.Bible's content-type=json chapter shape, mirroring
    // the live feed exactly (verse markers are tags named "verse" whose own
    // items repeat the number — that text must never leak into the page):
    // prose with two verses (one continuing into red letter), a section
    // heading to skip, a footnote subtree to strip, and a poetic couplet.
    val sample = """
    {"data": {"id": "MRK.1", "content": [
      {"name": "para", "type": "tag", "attrs": {"style": "s"},
       "items": [{"type": "text", "text": "John the Baptist Prepares the Way"}]},
      {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
        {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v", "sid": "MRK 1:1"},
         "items": [{"type": "text", "text": "1"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.1"}, "text": "The beginning of the gospel of Jesus Christ."},
        {"name": "verse", "type": "tag", "attrs": {"number": "2", "style": "v", "sid": "MRK 1:2"},
         "items": [{"type": "text", "text": "2"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.2"}, "text": "Jesus said, "},
        {"name": "char", "type": "tag", "attrs": {"style": "wj"},
         "items": [{"type": "text", "text": "“Follow Me.”"}]},
        {"name": "char", "type": "tag", "attrs": {"style": "f"},
         "items": [{"type": "text", "text": "1:2 Some manuscripts read otherwise."}]}
      ]},
      {"name": "para", "type": "tag", "attrs": {"style": "b"}},
      {"name": "para", "type": "tag", "attrs": {"style": "q1"}, "items": [
        {"name": "verse", "type": "tag", "attrs": {"number": "3", "style": "v", "sid": "MRK 1:3"},
         "items": [{"type": "text", "text": "3"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.3"}, "text": "Prepare the way of the Lord,"}
      ]},
      {"name": "para", "type": "tag", "attrs": {"style": "q2"}, "items": [
        {"type": "text", "attrs": {"verseId": "MRK.1.3"}, "text": "make His paths straight."}
      ]}
    ]}}
    """

    @Test
    fun testConvertsChapter() {
        val chapter = assertNotNull(
            APIBibleContent.chapter(number = 1, from = sample.encodeToByteArray()))
        assertEquals(1, chapter.n)

        // The heading is gone; prose, a stanza break, and two poetic lines
        // remain.
        assertEquals(
            listOf(BlockStyle.p, BlockStyle.b, BlockStyle.q1, BlockStyle.q2),
            chapter.blocks.map { it.s })
        assertEquals(listOf(1, 2, 3), chapter.verseNumbers)

        assertEquals("The beginning of the gospel of Jesus Christ.", chapter.text(forVerse = 1))
        // The footnote is stripped; the red letter survives as part of v2.
        assertEquals("Jesus said, “Follow Me.”", chapter.text(forVerse = 2))
        // Verse 3 runs across both poetic lines.
        assertEquals("Prepare the way of the Lord, make His paths straight.", chapter.text(forVerse = 3))

        val red = chapter.blocks.flatMap { it.x }.filter { it.isRedLetter }
        assertEquals(listOf("“Follow Me.”"), red.map { it.t })
    }

    // A licensed edition sends its copyright line with every chapter, and
    // the page shows it under the chapter (A64).
    @Test
    fun testCarriesTheEditionsCopyright() {
        val json = """
        {"data": {"copyright": " New King James Version®, Copyright© 1982, Thomas Nelson. All rights reserved. ",
         "content": [{"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
           {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v"},
            "items": [{"type": "text", "text": "1"}]},
           {"type": "text", "text": "In the beginning."}]}]}}
        """
        val chapter = assertNotNull(APIBibleContent.chapter(number = 1, from = json.encodeToByteArray()))
        assertEquals(
            "New King James Version®, Copyright© 1982, Thomas Nelson. All rights reserved.",
            chapter.copyright,
        )
        val plain = assertNotNull(APIBibleContent.chapter(number = 1, from = sample.encodeToByteArray()))
        assertEquals(null, plain.copyright)
    }

    // The live NKJV sets the divine name in a small-caps character style:
    // {"style":"sc"} around "Lord" (Psalm 3:1, "LORD, how they have
    // increased"). Read plain it was "Lord", which on the page is Adonai.
    @Test
    fun testSmallCapsDivineNameIsCapitals() {
        val json = """
        {"data": {"content": [
          {"name": "para", "type": "tag", "attrs": {"style": "q1"}, "items": [
            {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v"},
             "items": [{"type": "text", "text": "1"}]},
            {"name": "char", "type": "tag", "attrs": {"style": "sc"},
             "items": [{"type": "text", "text": "Lord"}]},
            {"type": "text", "text": ", how they have increased who trouble me! The Lord "},
            {"name": "char", "type": "tag", "attrs": {"style": "nd"},
             "items": [{"type": "text", "text": "God"}]},
            {"type": "text", "text": " is a shield."}
          ]}
        ]}}
        """
        val chapter = assertNotNull(APIBibleContent.chapter(number = 3, from = json.encodeToByteArray()))
        assertEquals(
            "LORD, how they have increased who trouble me! The Lord GOD is a shield.",
            chapter.ownText(verse = 1))
        // Capitals only: the length, and so every offset a mark keeps, stands.
        assertEquals("Lord’s é".length, APIBibleContent.asCapitals("Lord’s é").length)
        assertEquals("LORD’S é", APIBibleContent.asCapitals("Lord’s é"))
    }

    // A verse marker that closes a paragraph, its words in the next: the
    // number must not be lost, or the verse folds into the one before.
    @Test
    fun testVerseMarkerAtParagraphEndKeepsItsNumber() {
        val json = """
        {"data": {"content": [
          {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
            {"name": "verse", "type": "tag", "attrs": {"number": "4", "style": "v"},
             "items": [{"type": "text", "text": "4"}]},
            {"type": "text", "text": "Four. "},
            {"name": "verse", "type": "tag", "attrs": {"number": "5", "style": "v"},
             "items": [{"type": "text", "text": "5"}]},
            {"type": "text", "text": " "}
          ]},
          {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
            {"type": "text", "text": "Five."}
          ]}
        ]}}
        """
        val chapter = assertNotNull(APIBibleContent.chapter(number = 1, from = json.encodeToByteArray()))
        assertEquals(listOf(4, 5), chapter.verseNumbers)
        // Its trailing space stays, as a verse running into the next one keeps it.
        assertEquals("Four. ", chapter.ownText(verse = 4))
        assertEquals("Five.", chapter.ownText(verse = 5))
    }

    @Test
    fun testMalformedPayloadIsNil() {
        assertNull(APIBibleContent.chapter(number = 1, from = "not json".encodeToByteArray()))
        assertNull(APIBibleContent.chapter(number = 1, from = "{}".encodeToByteArray()))
        assertNull(APIBibleContent.chapter(
            number = 1,
            from = """{"data":{"content":[{"name":"para","attrs":{"style":"s1"},"items":[{"type":"text","text":"Heading only"}]}]}}"""
                .encodeToByteArray()))
    }

    @Test
    fun testRegistry() {
        assertEquals(
            listOf(TranslationID.bsb, TranslationID.web),
            TranslationRegistry.bundled.map { it.id })
        assertEquals(
            listOf(TranslationID.nkjv, TranslationID.niv, TranslationID.nasb),
            TranslationRegistry.licensed.map { it.id })
        assertTrue(TranslationRegistry.isBundled(TranslationID.bsb))
        assertFalse(TranslationRegistry.isBundled(TranslationID.nkjv))
        // All three licensed editions are live on the account and carry
        // their catalog ids.
        for (translation in TranslationRegistry.licensed) {
            assertTrue(translation.isConfigured, translation.id.rawValue)
            assertTrue(translation.redLetter)
        }
        // The raw string round-trips through Codable as a bare string, so
        // stored state and the database never migrate.
        val data = Json.encodeToString(TranslationIDSerializer, TranslationID.nkjv)
        assertEquals("\"nkjv\"", data)
        assertEquals(TranslationID.nkjv, Json.decodeFromString(TranslationIDSerializer, data))
    }
}
