package app.readribbon.core

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    // A new line for every verse (A68)

    @Test
    fun testVerseLinesBreakBeforeEachVerseInProse() {
        val block = ScriptureBlock(
            s = BlockStyle.m,
            x = listOf(
                ScriptureSpan(v = 1, t = "The beginning of the Good News. "),
                ScriptureSpan(v = 2, t = "As it is written. "),
                ScriptureSpan(v = 3, t = "He said. "),
            ),
        )
        assertEquals(listOf(1, 2), block.verseLineStarts())
        // An indented paragraph breaks the same way.
        assertEquals(listOf(1, 2), ScriptureBlock(s = BlockStyle.p, x = block.x).verseLineStarts())
    }

    @Test
    fun testVerseLinesNeverBreakBeforeABlocksFirstSpan() {
        // A paragraph that opens in the middle of a verse breaks only where
        // the next verse begins: its first line is already a line.
        val midVerse = ScriptureBlock(
            s = BlockStyle.p,
            x = listOf(
                ScriptureSpan(t = "who will prepare your way. "),
                ScriptureSpan(v = 5, t = "He said. "),
            ),
        )
        assertEquals(listOf(1), midVerse.verseLineStarts())
        // A span with no number continues its verse, red letter or not.
        val words = ScriptureBlock(
            s = BlockStyle.p,
            x = listOf(
                ScriptureSpan(v = 3, t = "He said,"),
                ScriptureSpan(t = " ", w = false),
                ScriptureSpan(v = 4, t = "“Come.”", w = true),
            ),
        )
        assertEquals(listOf(2), words.verseLineStarts())
        assertEquals(
            emptyList(),
            ScriptureBlock(s = BlockStyle.m, x = listOf(ScriptureSpan(v = 1, t = "One verse."))).verseLineStarts(),
        )
        assertEquals(emptyList(), ScriptureBlock(s = BlockStyle.m, x = emptyList()).verseLineStarts())
    }

    @Test
    fun testVerseLinesLeavePoetryAndTitlesAlone() {
        val spans = listOf(ScriptureSpan(v = 1, t = "O LORD, "), ScriptureSpan(v = 2, t = "how many rise up!"))
        for (style in listOf(BlockStyle.q1, BlockStyle.q2, BlockStyle.d, BlockStyle.b)) {
            assertEquals(emptyList(), ScriptureBlock(s = style, x = spans).verseLineStarts(), "$style")
        }
    }

    @Test
    fun testVerseLinesInBundledMark1() {
        // The Berean Standard's Mark 1, as the page is given it: verses 1
        // and 2 share a paragraph, Isaiah's words follow as poetry with
        // verse 3 opening a line of it, and 6–8 share another paragraph.
        val root = scriptureRoot()
        assumeTrue("converted Scripture not present", root != null)
        val file = File(File(root!!, "bsb"), "MRK.json")
        val text = Json.decodeFromString(ScriptureBookText.serializer(), file.readText())
        val blocks = assertNotNull(text.chapter(1)).blocks
        fun numbersStartingLines(block: ScriptureBlock): List<Int?> =
            block.verseLineStarts().map { block.x[it].v }

        assertEquals(BlockStyle.m, blocks[0].s)
        assertEquals(listOf<Int?>(2), numbersStartingLines(blocks[0]))
        val baptist = assertNotNull(blocks.firstOrNull { it.x.firstOrNull()?.v == 6 })
        assertEquals(listOf<Int?>(7, 8), numbersStartingLines(baptist))
        // Poetry keeps its own lines, the one verse 3 opens among them.
        assertTrue(blocks.any { it.s == BlockStyle.q1 && it.x.firstOrNull()?.v == 3 })
        for (block in blocks.filter { it.s == BlockStyle.q1 || it.s == BlockStyle.q2 }) {
            assertEquals(emptyList(), block.verseLineStarts())
        }
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
