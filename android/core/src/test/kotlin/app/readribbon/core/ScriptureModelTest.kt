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
