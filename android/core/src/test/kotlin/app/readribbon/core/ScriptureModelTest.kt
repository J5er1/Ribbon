package app.readribbon.core

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScriptureModelTest {
    // A miniature of the converter's output: prose with two verses, then a
    // poetic couplet whose second line continues the verse.
    val sample = """
    {"id":"MRK","name":"Mark","chapters":[{"n":1,"blocks":[
      {"s":"p","x":[{"v":1,"t":"The beginning of the Good News."},{"v":2,"t":"As it is written,"}]},
      {"s":"q1","x":[{"t":"“Behold, I send my messenger,"}]},
      {"s":"q2","x":[{"t":"who will prepare your way.”"}]},
      {"s":"p","x":[{"v":3,"t":"He said,"},{"t":" ","w":false},{"v":4,"t":"“Come.”","w":true}]}
    ]}]}
    """

    fun decoded(): ScriptureBookText =
        Json.decodeFromString(ScriptureBookText.serializer(), sample)

    @Test
    fun testDecodes() {
        val book = decoded()
        assertEquals("MRK", book.id)
        assertEquals(1, book.chapters.size)
        assertEquals(4, book.chapter(1)?.blocks?.size)
        assertNull(book.chapter(2))
    }

    @Test
    fun testVerseNumbers() {
        val chapter = assertNotNull(decoded().chapter(1))
        assertEquals(listOf(1, 2, 3, 4), chapter.verseNumbers)
    }

    @Test
    fun testVerseTextSpansBlocks() {
        val chapter = assertNotNull(decoded().chapter(1))
        assertEquals("The beginning of the Good News.", chapter.text(forVerse = 1))
        // Verse 2 runs from the prose block through both poetic lines.
        assertEquals(
            "As it is written, “Behold, I send my messenger, who will prepare your way.”",
            chapter.text(forVerse = 2))
        assertEquals("“Come.”", chapter.text(forVerse = 4))
    }

    @Test
    fun testRedLetter() {
        val chapter = assertNotNull(decoded().chapter(1))
        val redSpans = chapter.blocks.flatMap { it.x }.filter { it.isRedLetter }
        assertEquals(listOf("“Come.”"), redSpans.map { it.t })
    }

    // A psalm's shape: a title, a stanza break, then a verse whose second
    // poetic line is glued to the first with no space. And Zechariah 12's: a
    // title that carries the verse number the next paragraph continues.
    val psalm = """
    {"n":3,"blocks":[
      {"s":"d","x":[{"t":"A Psalm of David."}]},
      {"s":"b","x":[]},
      {"s":"q1","x":[{"v":1,"t":"O LORD, how my foes have increased!"}]},
      {"s":"q2","x":[{"t":"How many rise up against me!"}]},
      {"s":"q1","x":[{"v":2,"t":"Many say of me, "},{"t":"\u201cGod will not deliver him.\u201d"}]}
    ]}
    """

    val burden = """
    {"n":12,"blocks":[
      {"s":"d","x":[{"v":1,"t":"This is the burden of the word of the LORD."}]},
      {"s":"b","x":[]},
      {"s":"m","x":[{"t":"Thus declares the LORD."}]},
      {"s":"p","x":[{"v":2,"t":"Behold. "}]}
    ]}
    """

    @Test
    fun testOwnTextGluesSpans() {
        val chapter = assertNotNull(decoded().chapter(1))
        val texts = chapter.ownTexts()
        assertEquals("The beginning of the Good News.", texts[1])
        assertEquals("As it is written,\u201CBehold, I send my messenger,who will prepare your way.\u201D", texts[2])
        assertEquals("He said, ", texts[3])
        assertEquals("\u201CCome.\u201D", texts[4])
        assertEquals("\u201CCome.\u201D", chapter.ownText(verse = 4))
        assertNull(chapter.ownText(verse = 5))
        assertEquals(listOf(17, 46), chapter.ownSpanBreaks()[2])
        assertEquals(listOf(8), chapter.ownSpanBreaks()[3])
        assertEquals(emptyList<Int>(), chapter.ownSpanBreaks()[1])
    }

    @Test
    fun testOwnTextLeavesOutTitles() {
        val chapter = Json.decodeFromString(ScriptureChapter.serializer(), psalm)
        val texts = chapter.ownTexts()
        assertEquals(setOf(1, 2), texts.keys)
        assertEquals("O LORD, how my foes have increased!How many rise up against me!", texts[1])
        assertEquals(63, texts[1]?.length)
        // Offsets count UTF-16 units: the curly quotes are one each.
        assertEquals("Many say of me, \u201CGod will not deliver him.\u201D", texts[2])
        assertEquals(43, texts[2]?.length)
        assertEquals(listOf(35), chapter.ownSpanBreaks()[1])
        assertEquals(listOf(16), chapter.ownSpanBreaks()[2])
    }

    @Test
    fun testOwnTextFollowsAVerseNumberOnATitle() {
        val chapter = Json.decodeFromString(ScriptureChapter.serializer(), burden)
        assertEquals(mapOf(1 to "Thus declares the LORD.", 2 to "Behold. "), chapter.ownTexts())
    }

    @Test
    fun testBundledOwnTextLengths() {
        // The own text is what a mark's offsets and the bundled word links
        // count in, so two known lengths pin it to the committed text.
        val root = scriptureRoot()
        assumeTrue("converted Scripture not present", root != null)
        fun own(book: String, chapter: Int, verse: Int): String? {
            val file = File(File(root!!, "bsb"), "$book.json")
            val text = Json.decodeFromString(ScriptureBookText.serializer(), file.readText())
            return text.chapter(chapter)?.ownText(verse = verse)
        }
        assertEquals(63, own("PSA", 3, 1)?.length)
        assertEquals(80, own("JHN", 1, 1)?.length)
    }

    @Test
    fun testBundledConverterOutputDecodes() {
        // When the converted corpus is reachable from the test's working
        // tree, decode every book of both translations end-to-end.
        //
        // The Swift test walks up from #filePath (RibbonCoreTests → Tests →
        // core → repo root) to reach ios/Ribbon/Resources/Scripture. A JVM
        // test has no #filePath, so this walks up from the test's working
        // directory to the same place.
        val root = scriptureRoot()
        assumeTrue("converted Scripture not present", root != null)
        val base = root!!
        for (translation in TranslationRegistry.bundled) {
            for (book in Bible.books) {
                val file = File(File(base, translation.id.rawValue), "${book.id}.json")
                val text = Json.decodeFromString(ScriptureBookText.serializer(), file.readText())
                assertEquals(book.chapterCount, text.chapters.size, "$translation ${book.id}")
                assertFalse(text.chapters.isEmpty())
            }
        }
    }

    private fun scriptureRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "ios/Ribbon/Resources/Scripture")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
