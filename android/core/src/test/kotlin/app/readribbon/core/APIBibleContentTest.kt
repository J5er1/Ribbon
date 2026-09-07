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
