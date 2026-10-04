package app.readribbon.core

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

// The on-phone aligner (A60). A port of
// core/Tests/RibbonCoreTests/PivotAlignerTests.swift, case for case: the
// hand-written cases pin the rules; the fixture cases
// (core/Tests/RibbonCoreTests/Fixtures/pivot_cases.json, written by
// tools/pivot_align.py) hold both ports and the Python reference to the same
// answers on real verses.
class PivotAlignerTest {
    /** start, end, norm, stem, isContent — the fixture's token shape. */
    private fun shape(tokens: List<PivotAligner.Token>): List<List<String>> =
        tokens.map { listOf("${it.start}", "${it.end}", it.norm, it.stem, "${it.isContent}") }

    // Tokens

    @Test
    fun testTokensSplitOnSpacesAndPunctuation() {
        assertEquals(
            listOf(
                listOf("0", "2", "in", "in", "false"),
                listOf("3", "6", "the", "the", "false"),
                listOf("7", "16", "beginning", "begin", "true"),
                listOf("18", "21", "god", "god", "true"),
                listOf("22", "29", "created", "creat", "true"),
            ),
            shape(PivotAligner.tokens("In the beginning, God created.")),
        )
        assertEquals(0, PivotAligner.tokens("").size)
        assertEquals(0, PivotAligner.tokens(" — ").size)
    }

    @Test
    fun testTokensKeepInnerApostrophes() {
        assertEquals(
            listOf(
                listOf("0", "3", "the", "the", "false"),
                listOf("4", "10", "lords", "lord", "true"),
                listOf("11", "16", "house", "hous", "true"),
                listOf("18", "22", "oer", "oer", "true"),
                listOf("23", "26", "the", "the", "false"),
                listOf("27", "30", "sea", "sea", "true"),
                listOf("33", "37", "twas", "twa", "true"),
                listOf("38", "46", "brothers", "brother", "true"),
            ),
            shape(PivotAligner.tokens("the LORD’s house; o'er the sea, 'twas brothers' ")),
        )
    }

    @Test
    fun testTokensBreakOnHyphensAndSigns() {
        assertEquals(
            listOf(
                listOf("0", "5", "burnt", "burn", "true"),
                listOf("6", "14", "offering", "offer", "true"),
                listOf("15", "16", "a", "a", "false"),
                listOf("17", "18", "b", "b", "true"),
            ),
            shape(PivotAligner.tokens("burnt-offering a×b")),
        )
    }

    @Test
    fun testTokensBreakAtSpanStarts() {
        // Two poetic lines glued with no space: "my rock" + "in whom".
        assertEquals(
            listOf(
                listOf("0", "2", "my", "my", "false"),
                listOf("3", "7", "rock", "rock", "true"),
                listOf("7", "9", "in", "in", "false"),
                listOf("10", "14", "whom", "whom", "false"),
            ),
            shape(PivotAligner.tokens("my rockin whom", spanBreaks = listOf(7))),
        )
        // An apostrophe before a break is outside the word.
        assertEquals(
            listOf(
                listOf("0", "2", "it", "it", "false"),
                listOf("3", "4", "s", "", "true"),
            ),
            shape(PivotAligner.tokens("it’s", spanBreaks = listOf(3))),
        )
    }

    @Test
    fun testTokensLowerLatinLetters() {
        assertEquals(
            listOf("élan", "œuvre", "istanbul", "ÿ", "ǅ"),
            PivotAligner.tokens("ÉLAN Œuvre İstanbul Ÿ ǅ").map { it.norm },
        )
    }

    // Stems

    @Test
    fun testPorterStemsThePapersExamples() {
        val cases = listOf(
            "caresses" to "caress", "ponies" to "poni", "ties" to "ti", "caress" to "caress", "cats" to "cat",
            "feed" to "feed", "agreed" to "agre", "plastered" to "plaster", "bled" to "bled",
            "motoring" to "motor", "sing" to "sing", "conflated" to "conflat", "troubled" to "troubl",
            "sized" to "size", "hopping" to "hop", "tanned" to "tan", "falling" to "fall",
            "hissing" to "hiss", "fizzed" to "fizz", "failing" to "fail", "filing" to "file",
            "happy" to "happi", "sky" to "sky",
            "relational" to "relat", "conditional" to "condit", "rational" to "ration",
            "valenci" to "valenc", "digitizer" to "digit", "conformabli" to "conform",
            "radicalli" to "radic", "differentli" to "differ", "vileli" to "vile",
            "analogousli" to "analog", "vietnamization" to "vietnam", "predication" to "predic",
            "operator" to "oper", "feudalism" to "feudal", "decisiveness" to "decis",
            "hopefulness" to "hope", "callousness" to "callous", "formaliti" to "formal",
            "sensitiviti" to "sensit", "sensibiliti" to "sensibl",
            "triplicate" to "triplic", "formative" to "form", "formalize" to "formal",
            "electriciti" to "electr", "electrical" to "electr", "hopeful" to "hope", "goodness" to "good",
            "revival" to "reviv", "allowance" to "allow", "inference" to "infer", "airliner" to "airlin",
            "gyroscopic" to "gyroscop", "adjustable" to "adjust", "defensible" to "defens",
            "irritant" to "irrit", "replacement" to "replac", "adjustment" to "adjust",
            "dependent" to "depend", "adoption" to "adopt", "homologou" to "homolog",
            "communism" to "commun", "activate" to "activ", "angulariti" to "angular",
            "homologous" to "homolog", "effective" to "effect", "bowdlerize" to "bowdler",
            "probate" to "probat", "rate" to "rate", "cease" to "ceas", "controll" to "control", "roll" to "roll",
            "generalizations" to "gener", "oscillators" to "oscil",
        )
        for ((word, stem) in cases) {
            assertEquals(stem, PivotAligner.porter(word), word)
        }
    }

    @Test
    fun testStemUsesIrregularForms() {
        assertEquals("speak", PivotAligner.stem("spake"))
        assertEquals("brother", PivotAligner.stem("brethren"))
        assertEquals("be", PivotAligner.stem("was"))
        // The table's values are Porter stems of the base form, so "said"
        // meets "saying" and "say".
        assertEquals("sai", PivotAligner.stem("said"))
        assertEquals(PivotAligner.stem("said"), PivotAligner.stem("saying"))
        assertEquals("love", PivotAligner.stem("loved"))
    }

    // Alignment

    val pivot = "For God so loved the world "
    val pivotLinks = listOf(
        AlignmentLink(0, 3, listOf(0)),
        AlignmentLink(4, 7, listOf(4)),
        AlignmentLink(8, 10, listOf(1)),
        AlignmentLink(11, 16, listOf(2)),
        AlignmentLink(17, 26, listOf(6, 7)),
    )

    @Test
    fun testAlignBorrowsThePivotsLinks() {
        // The same words: everything but the opening "For", a function word
        // with no content word before it, carries over; "the world" is one
        // link because its two words share a set.
        assertEquals(
            listOf(
                AlignmentLink(4, 7, listOf(4)),
                AlignmentLink(8, 10, listOf(1)),
                AlignmentLink(11, 16, listOf(2)),
                AlignmentLink(17, 26, listOf(6, 7)),
            ),
            PivotAligner.align("For God so loved the world, ", emptyList(), pivot, emptyList(), pivotLinks),
        )
        // Other words in another order: what lines up carries over.
        assertEquals(
            listOf(
                AlignmentLink(0, 3, listOf(4)),
                AlignmentLink(4, 9, listOf(2)),
                AlignmentLink(10, 19, listOf(6, 7)),
            ),
            PivotAligner.align("God loved the world so much ", emptyList(), pivot, emptyList(), pivotLinks),
        )
    }

    @Test
    fun testAlignKeepsStopWordsOnlyBetweenOrBeside() {
        // "not" stands beside "perish", which took the same words.
        assertEquals(
            listOf(AlignmentLink(7, 17, listOf(5, 6))),
            PivotAligner.align(
                "should not perish ", emptyList(), "shall not perish ", emptyList(),
                listOf(AlignmentLink(0, 16, listOf(5, 6))),
            ),
        )
        // Function words alone never make a link.
        assertEquals(
            emptyList(),
            PivotAligner.align(
                "In the start ", emptyList(), "In the beginning ", emptyList(),
                listOf(AlignmentLink(0, 2, listOf(0)), AlignmentLink(3, 16, listOf(1))),
            ),
        )
    }

    @Test
    fun testAlignWithoutPivotLinksIsEmpty() {
        assertEquals(
            emptyList(),
            PivotAligner.align("In the beginning ", emptyList(), "In the beginning ", emptyList(), emptyList()),
        )
        assertEquals(emptyList(), PivotAligner.align("", emptyList(), pivot, emptyList(), pivotLinks))
    }

    // The fixture

    private fun fixture(): JsonElement? {
        // Read from the repository by path, as the corpus tests are. The Swift
        // suite walks up from its own file; a JVM test walks up from its
        // working directory to the same place.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "core/Tests/RibbonCoreTests/Fixtures/pivot_cases.json")
            if (candidate.isFile) return Json.parseToJsonElement(candidate.readText())
            dir = dir.parentFile
        }
        return null
    }

    private fun ints(element: JsonElement): List<Int> = element.jsonArray.map { it.jsonPrimitive.int }

    private fun links(element: JsonElement): List<AlignmentLink> =
        element.jsonArray.map { Json.decodeFromJsonElement(AlignmentLink.serializer(), it) }

    @Test
    fun testFixtureStems() {
        val doc = fixture()
        assumeTrue("pivot_cases.json not present; run tools/pivot_align.py --kjv", doc != null)
        val stems = doc!!.jsonObject.getValue("stems").jsonArray
        assertFalse(stems.isEmpty())
        for (pair in stems) {
            val (word, stem) = pair.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(stem, PivotAligner.stem(word), word)
        }
    }

    @Test
    fun testFixtureTokens() {
        val doc = fixture()
        assumeTrue("pivot_cases.json not present; run tools/pivot_align.py --kjv", doc != null)
        val cases = doc!!.jsonObject.getValue("tokens").jsonArray
        assertFalse(cases.isEmpty())
        for (case in cases) {
            val o = case.jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val expected = o.getValue("tokens").jsonArray.map {
                val t = it as JsonArray
                PivotAligner.Token(
                    start = t[0].jsonPrimitive.int,
                    end = t[1].jsonPrimitive.int,
                    norm = t[2].jsonPrimitive.content,
                    stem = t[3].jsonPrimitive.content,
                    isContent = t[4].jsonPrimitive.boolean,
                )
            }
            assertEquals(expected, PivotAligner.tokens(text, ints(o.getValue("breaks"))), text)
        }
    }

    @Test
    fun testFixtureAlignments() {
        val doc = fixture()
        assumeTrue("pivot_cases.json not present; run tools/pivot_align.py --kjv", doc != null)
        val cases = doc!!.jsonObject.getValue("align").jsonArray
        assertFalse(cases.isEmpty())
        for (case in cases) {
            val o = case.jsonObject
            val got = PivotAligner.align(
                reader = o.getValue("reader").jsonPrimitive.content,
                readerBreaks = ints(o.getValue("readerBreaks")),
                pivot = o.getValue("pivot").jsonPrimitive.content,
                pivotBreaks = ints(o.getValue("pivotBreaks")),
                pivotLinks = links(o.getValue("pivotLinks")),
            )
            assertEquals(links(o.getValue("expected")), got, o.getValue("name").jsonPrimitive.content)
        }
    }
}
