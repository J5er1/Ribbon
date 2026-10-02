package app.readribbon.core

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assume.assumeTrue
import org.junit.Test

// The original words, the word links, and the moves between a version's own
// text and the words under it (A60). A port of
// core/Tests/RibbonCoreTests/OriginalTextTests.swift, case for case.
class OriginalTextTest {
    // Fixtures

    // John 1:1 in the Berean Standard, with links of the bundled shape. The
    // Greek: Ἐν(0) ἀρχῇ(1) ἦν(2) ὁ(3) λόγος(4) καὶ(5) ὁ(6) λόγος(7) ἦν(8)
    // πρὸς(9) τὸν(10) θεόν(11) καὶ(12) θεὸς(13) ἦν(14) ὁ(15) λόγος(16).
    // τὸν, the article in "with God", is rendered by no English word.
    val john1 = "In the beginning was the Word, and the Word was with God, and the Word was God. "
    val john1Links = listOf(
        AlignmentLink(0, 2, listOf(0)),
        AlignmentLink(3, 16, listOf(1)),
        AlignmentLink(17, 20, listOf(2)),
        AlignmentLink(21, 29, listOf(3, 4)),
        AlignmentLink(31, 34, listOf(5)),
        AlignmentLink(35, 43, listOf(6, 7)),
        AlignmentLink(44, 47, listOf(8)),
        AlignmentLink(48, 52, listOf(9)),
        AlignmentLink(53, 56, listOf(11)),
        AlignmentLink(58, 61, listOf(12)),
        AlignmentLink(62, 70, listOf(15, 16)),
        AlignmentLink(71, 74, listOf(14)),
        AlignmentLink(75, 78, listOf(13)),
    )

    // John 1:2: οὗτος(0) ἦν(1) ἐν(2) ἀρχῇ(3) πρὸς(4) τὸν(5) θεόν(6).
    val john2 = "He was with God in the beginning. "
    val john2Links = listOf(
        AlignmentLink(0, 2, listOf(0)),
        AlignmentLink(3, 6, listOf(1)),
        AlignmentLink(7, 11, listOf(4)),
        AlignmentLink(12, 15, listOf(6)),
        AlignmentLink(16, 18, listOf(2)),
        AlignmentLink(19, 32, listOf(3)),
    )

    // A made-up verse in two versions, the same in verses 1 to 3. The
    // original: ὁ(0) θεὸς(1) ἀγάπη(2) ἐστίν(3).
    val authorText = "God is love. "
    val authorLinks = listOf(
        AlignmentLink(0, 3, listOf(1)),
        AlignmentLink(4, 6, listOf(3)),
        AlignmentLink(7, 11, listOf(2)),
    )
    val readerText = "Love, that is God. "
    val readerLinks = listOf(
        AlignmentLink(0, 4, listOf(2)),
        AlignmentLink(11, 13, listOf(3)),
        AlignmentLink(14, 17, listOf(1)),
    )

    val authorByVerse get() = mapOf(1 to authorLinks, 2 to authorLinks, 3 to authorLinks)
    val readerByVerse get() = mapOf(1 to readerLinks, 2 to readerLinks, 3 to readerLinks)
    val readerTexts get() = mapOf(1 to readerText, 2 to readerText, 3 to readerText)

    private fun strings(json: String): List<String> =
        Json.decodeFromString(ListSerializer(String.serializer()), json)

    // Decoding

    @Test
    fun testOriginalBookDecodes() {
        val json = """{"id":"JHN","source":"bsbt-1a2b3c4d","chapters":[{"n":1,"verses":[{"v":1,"w":[""" +
            """["Ἐν","En","G1722","Prep"],["ἀρχῇ","archē","G746","N-DFS"],["ἦν","ēn","G1510","V-IIA-3S"]]}]}]}"""
        val book = Json.decodeFromString(OriginalBook.serializer(), json)
        assertEquals("JHN", book.id)
        assertEquals("bsbt-1a2b3c4d", book.source)
        val chapter = assertNotNull(book.chapter(1))
        assertNull(book.chapter(2))
        val words = assertNotNull(chapter.words(verse = 1))
        assertNull(chapter.words(verse = 2))
        assertEquals(3, words.size)
        assertEquals(OriginalWord(text = "ἀρχῇ", translit = "archē", strongs = "G746", parse = "N-DFS"), words[1])
        assertEquals("G1510", words[2].strongs)
        assertEquals("V-IIA-3S", words[2].parse)
        assertFalse(words[0].isAramaic)
    }

    @Test
    fun testOriginalWordEmptyStringsAreNil() {
        val word = Json.decodeFromString(OriginalWord.serializer(), """["בְּ","bə","",""]""")
        assertEquals("בְּ", word.text)
        assertEquals("bə", word.translit)
        assertNull(word.strongs)
        assertNull(word.parse)
        assertFalse(word.isAramaic)
        assertEquals(OriginalWord.of(text = "בְּ", translit = "bə", strongs = "", parse = "", isAramaic = false), word)
    }

    @Test
    fun testOriginalWordLanguage() {
        val aramaic = Json.decodeFromString(OriginalWord.serializer(), """["אֱלָהּ","’ĕ·lāh","H426","N-ms","a"]""")
        assertTrue(aramaic.isAramaic)
        assertEquals(OriginalLanguage.aramaic, aramaic.language("DAN"))
        val hebrew = Json.decodeFromString(OriginalWord.serializer(), """["אֱלֹהִים","’ĕ·lō·hîm","H430","N-mp"]""")
        assertEquals(OriginalLanguage.hebrew, hebrew.language("DAN"))
        assertEquals(OriginalLanguage.hebrew, hebrew.language("GEN"))
        assertEquals(OriginalLanguage.hebrew, hebrew.language("MAL"))
        val greek = Json.decodeFromString(OriginalWord.serializer(), """["λόγος","logos","G3056","N-NMS"]""")
        assertEquals(OriginalLanguage.greek, greek.language("MAT"))
        assertEquals(OriginalLanguage.greek, greek.language("REV"))
        assertEquals(OriginalLanguage.greek, OriginalWords.language("JHN"))
        assertEquals(OriginalLanguage.hebrew, OriginalWords.language("PSA"))
        assertEquals("aramaic", OriginalLanguage.aramaic.name)
    }

    @Test
    fun testOriginalWordRoundTrips() {
        val plain = OriginalWord(text = "λόγος", translit = "logos", strongs = "G3056", parse = "N-NMS")
        val plainJson = Json.encodeToString(OriginalWord.serializer(), plain)
        assertEquals(listOf("λόγος", "logos", "G3056", "N-NMS"), strings(plainJson))
        assertEquals(plain, Json.decodeFromString(OriginalWord.serializer(), plainJson))

        val aramaic = OriginalWord(text = "מַלְכָּא", translit = "mal·kā", strongs = null, parse = null, isAramaic = true)
        val aramaicJson = Json.encodeToString(OriginalWord.serializer(), aramaic)
        assertEquals(listOf("מַלְכָּא", "mal·kā", "", "", "a"), strings(aramaicJson))
        assertEquals(aramaic, Json.decodeFromString(OriginalWord.serializer(), aramaicJson))
    }

    @Test
    fun testLexiconLookup() {
        val json = """{"G3056":["λόγος","lógos","something said"],"H430":["אֱלֹהִים","ʼĕlôhîym","gods"]}"""
        val lexicon = Json.decodeFromString(Lexicon.serializer(), json)
        assertEquals(2, lexicon.entries.size)
        assertEquals(LexiconEntry(lemma = "λόγος", translit = "lógos", definition = "something said"), lexicon.entry("G3056"))
        assertEquals("gods", lexicon.entry("H430")?.definition)
        assertEquals("λόγος", lexicon.entry("g03056")?.lemma)
        assertEquals("אֱלֹהִים", lexicon.entry("H0430")?.lemma)
        assertNull(lexicon.entry("G9999"))
        assertNull(lexicon.entry(""))
        assertNull(lexicon.entry("3056"))
        assertNull(lexicon.entry("G"))
        val again = Json.decodeFromString(Lexicon.serializer(), Json.encodeToString(Lexicon.serializer(), lexicon))
        assertEquals(lexicon, again)
    }

    @Test
    fun testParsingsDescribe() {
        val parsings = Json.decodeFromString(Parsings.serializer(), """{"N-DFS":"Noun - Dative Feminine Singular","X":""}""")
        assertEquals("Noun - Dative Feminine Singular", parsings.describe("N-DFS"))
        assertNull(parsings.describe(""))
        assertNull(parsings.describe("X"))
        assertNull(parsings.describe("V-PAI-3S"))
    }

    @Test
    fun testAlignmentDecodes() {
        val json = """{"id":"JHN","translation":"bsb","source":"bsbt-1a2b3c4d","basis":"9f8e7d6c5b4a",""" +
            """"chapters":[{"n":1,"verses":[{"v":1,"l":[[0,2,[0]],[3,16,[1]]]},{"v":2,"l":[]}]}]}"""
        val alignment = Json.decodeFromString(BookAlignment.serializer(), json)
        assertEquals("JHN", alignment.id)
        assertEquals(TranslationID.bsb, alignment.translation)
        assertEquals("bsbt-1a2b3c4d", alignment.source)
        assertEquals("9f8e7d6c5b4a", alignment.basis)
        val chapter = assertNotNull(alignment.chapter(1))
        assertNull(alignment.chapter(2))
        val expected = listOf(AlignmentLink(0, 2, listOf(0)), AlignmentLink(3, 16, listOf(1)))
        assertEquals(expected, chapter.links(verse = 1))
        assertEquals(emptyList(), chapter.links(verse = 2))
        assertNull(chapter.links(verse = 3))
        assertEquals(mapOf(1 to expected, 2 to emptyList()), chapter.byVerse)
        val again = Json.decodeFromString(BookAlignment.serializer(), Json.encodeToString(BookAlignment.serializer(), alignment))
        assertEquals(alignment, again)
        val link = Json.parseToJsonElement(Json.encodeToString(AlignmentLink.serializer(), expected[1])).jsonArray
        assertEquals(3, link.size)
    }

    // words(in:) and filledInterior

    @Test
    fun testWordsInTouchingRangesDoNotOverlap() {
        // "In" ends at 2 and "the beginning" starts at 3: the space between
        // touches both and is under neither.
        assertEquals(emptyList(), OriginalWords.words(john1Links, from = 2, to = 3))
        assertEquals(listOf(0), OriginalWords.words(john1Links, from = 0, to = 2))
        assertEquals(listOf(0, 1), OriginalWords.words(john1Links, from = 1, to = 4))
        assertEquals(emptyList(), OriginalWords.words(john1Links, from = 16, to = 17))
        assertEquals(listOf(9, 11), OriginalWords.words(john1Links, from = 48, to = 56))
        assertEquals(listOf(13, 14, 15, 16), OriginalWords.words(john1Links, from = 62, to = null))
        assertEquals(listOf(0), OriginalWords.words(john1Links, from = null, to = 2))
        assertEquals((0..16).filter { it != 10 }, OriginalWords.words(john1Links, from = null, to = null))
        assertEquals(emptyList(), OriginalWords.words(emptyList(), from = 0, to = 10))
    }

    @Test
    fun testFilledInteriorAddsOnlyUnlinkedWords() {
        val linked = OriginalWords.linked(john1Links)
        assertFalse(10 in linked)
        assertEquals(listOf(9, 10, 11), OriginalWords.filledInterior(listOf(9, 11), linked))
        assertEquals(listOf(9, 10, 11), OriginalWords.filledInterior(listOf(11, 9, 9), linked))
        assertEquals(listOf(2, 8), OriginalWords.filledInterior(listOf(2, 8), linked))
        assertEquals(listOf(5), OriginalWords.filledInterior(listOf(5), linked))
        assertEquals(emptyList(), OriginalWords.filledInterior(emptyList(), linked))
        assertEquals(listOf(0, 1, 2, 3, 4), OriginalWords.filledInterior(listOf(0, 4), emptySet()))
    }

    // ranges

    @Test
    fun testRangesBridgeOverUnlinkedWords() {
        // "with God": the gap between holds only a space and the unrendered
        // article.
        assertEquals(listOf(TextRange(48, 56)), OriginalWords.ranges(setOf(9, 10, 11), john1Links, john1))
        assertEquals(listOf(TextRange(3, 20)), OriginalWords.ranges(setOf(1, 2), john1Links, john1))
        // A link renders a set; one of its words is enough.
        assertEquals(listOf(TextRange(21, 29)), OriginalWords.ranges(setOf(4), john1Links, john1))
        assertEquals(emptyList(), OriginalWords.ranges(setOf(10), john1Links, john1))
        assertEquals(emptyList(), OriginalWords.ranges(emptySet(), john1Links, john1))
    }

    @Test
    fun testRangesDoNotBridgeAcrossAForeignLink() {
        assertEquals(
            listOf(TextRange(53, 56), TextRange(75, 78)),
            OriginalWords.ranges(setOf(11, 13), john1Links, john1),
        )
    }

    @Test
    fun testRangesScatteredStopWordsGiveNothing() {
        // "and … and": two function words with a phrase between them is what
        // a weak link looks like.
        assertEquals(emptyList(), OriginalWords.ranges(setOf(5, 12), john1Links, john1))
        assertEquals(emptyList(), OriginalWords.ranges(setOf(2, 8, 14), john1Links, john1))
    }

    @Test
    fun testRangesSingleStopWordRunIsKept() {
        val text = "Who is this King of glory? "
        val links = listOf(
            AlignmentLink(0, 3, listOf(0)),
            AlignmentLink(4, 6, listOf(1)),
            AlignmentLink(7, 11, listOf(2)),
            AlignmentLink(12, 16, listOf(3)),
            AlignmentLink(20, 25, listOf(4)),
        )
        assertEquals(listOf(TextRange(0, 6)), OriginalWords.ranges(setOf(0, 1), links, text))
        assertEquals(listOf(TextRange(4, 6)), OriginalWords.ranges(setOf(1), links, text))
        assertEquals(listOf(TextRange(31, 34)), OriginalWords.ranges(setOf(5), john1Links, john1))
        // "King of glory" bridges the unlinked "of" and keeps it.
        assertEquals(listOf(TextRange(12, 25)), OriginalWords.ranges(setOf(3, 4), links, text))
    }

    @Test
    fun testRangesBreakWordsAtLinkEdges() {
        // A poetry line glued to the next ("it" + "all") is two words, not
        // one content word that would hide the scatter of "it … the".
        val text = "of itall the days"
        val links = listOf(
            AlignmentLink(0, 2, listOf(0)),
            AlignmentLink(3, 5, listOf(1)),
            AlignmentLink(5, 8, listOf(2)),
            AlignmentLink(9, 12, listOf(3)),
            AlignmentLink(13, 17, listOf(4)),
        )
        assertEquals(emptyList(), OriginalWords.ranges(setOf(1, 3), links, text))
        assertEquals(
            listOf(TextRange(3, 5), TextRange(13, 17)),
            OriginalWords.ranges(setOf(1, 4), links, text),
        )
    }

    // anchored

    @Test
    fun testAnchoredSingleVerse() {
        val range = VerseRange("JHN", 1, 1, 1, startChar = 48, endChar = 56, charTranslation = TranslationID.bsb)
        val anchored = OriginalWords.anchored(range, mapOf(1 to john1Links), "bsbt-test")
        assertEquals(listOf(9, 10, 11), anchored.startWords)
        assertNull(anchored.endWords)
        assertEquals("bsbt-test", anchored.wordsSource)
        assertEquals(48, anchored.startChar)
        assertEquals(56, anchored.endChar)
        assertEquals(TranslationID.bsb, anchored.charTranslation)

        // A mark inside one verse that runs to its end still has a part.
        val tail = VerseRange("JHN", 1, 1, 1, endChar = 2, charTranslation = TranslationID.bsb)
        assertEquals(listOf(0), OriginalWords.anchored(tail, mapOf(1 to john1Links), "s").startWords)
    }

    @Test
    fun testAnchoredMultiVerse() {
        val range = VerseRange("JHN", 1, 1, 2, startChar = 62, endChar = 15, charTranslation = TranslationID.bsb)
        val anchored = OriginalWords.anchored(range, mapOf(1 to john1Links, 2 to john2Links), "bsbt-test")
        assertEquals(listOf(13, 14, 15, 16), anchored.startWords)
        assertEquals(listOf(0, 1, 4, 5, 6), anchored.endWords)
        assertEquals("bsbt-test", anchored.wordsSource)

        // A start that is the whole verse needs no words.
        val wholeStart = VerseRange("JHN", 1, 1, 2, endChar = 15, charTranslation = TranslationID.bsb)
        val end = OriginalWords.anchored(wholeStart, mapOf(1 to john1Links, 2 to john2Links), "s")
        assertNull(end.startWords)
        assertEquals(listOf(0, 1, 4, 5, 6), end.endWords)
        assertEquals("s", end.wordsSource)
    }

    @Test
    fun testAnchoredWholeVerseComesBackUnchanged() {
        val range = VerseRange("JHN", 1, 1, 2)
        assertEquals(range, OriginalWords.anchored(range, mapOf(1 to john1Links, 2 to john2Links), "s"))
    }

    @Test
    fun testAnchoredWithoutLinksLeavesNoWords() {
        val range = VerseRange("JHN", 1, 1, 2, startChar = 62, endChar = 15, charTranslation = TranslationID.bsb)
        assertEquals(range, OriginalWords.anchored(range, null, "s"))
        assertEquals(range, OriginalWords.anchored(range, mapOf(1 to john1Links, 2 to john2Links), null))

        // Links for one end only: the other end is left without words.
        val half = OriginalWords.anchored(range, mapOf(1 to john1Links), "s")
        assertEquals(listOf(13, 14, 15, 16), half.startWords)
        assertNull(half.endWords)
        assertEquals("s", half.wordsSource)

        // A part that holds no linked word: no words, and so no source.
        val gap = VerseRange("JHN", 1, 1, 1, startChar = 56, endChar = 58, charTranslation = TranslationID.bsb)
        val none = OriginalWords.anchored(gap, mapOf(1 to john1Links), "s")
        assertNull(none.startWords)
        assertNull(none.endWords)
        assertNull(none.wordsSource)
    }

    // resolve

    @Test
    fun testResolveAuthorsVersionIsExact() {
        val range = VerseRange("1JN", 4, 1, 3, startChar = 7, endChar = 3, charTranslation = TranslationID.bsb)
        val spans = OriginalWords.resolve(
            range, TranslationID.bsb, readerLinks = authorByVerse,
            readerTexts = emptyMap(), source = "src", authorLinks = authorByVerse,
        )
        assertEquals(listOf(MarkedSpan(1, 7, null), MarkedSpan(2), MarkedSpan(3, null, 3)), spans)
        val single = VerseRange(
            "1JN", 4, 2, 2, startChar = 0, endChar = 6, charTranslation = TranslationID.bsb,
            startWords = listOf(2), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(2, 0, 6)),
            OriginalWords.resolve(single, TranslationID.bsb, null, emptyMap(), "src", null),
        )
    }

    @Test
    fun testResolveDerivesOldMarksFromOffsets() {
        // A mark made before marks carried words: only offsets in the
        // author's version, which the author's links turn into words.
        val range = VerseRange("1JN", 4, 1, 3, startChar = 7, endChar = 3, charTranslation = TranslationID.bsb)
        assertEquals(
            listOf(MarkedSpan(1, 0, 4), MarkedSpan(2), MarkedSpan(3, 14, 17)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", authorByVerse),
        )

        // "God is" is "is God" on the reader's page, one run.
        val single = VerseRange("1JN", 4, 2, 2, startChar = 0, endChar = 6, charTranslation = TranslationID.bsb)
        assertEquals(
            listOf(MarkedSpan(2, 11, 17)),
            OriginalWords.resolve(single, TranslationID.web, readerByVerse, readerTexts, "src", authorByVerse),
        )
    }

    @Test
    fun testResolveUsesStoredWordsFromTheSameSource() {
        // The stored words win over the offsets: they are what the author
        // chose, in the numbering this phone holds.
        val range = VerseRange(
            "1JN", 4, 2, 2, startChar = 0, endChar = 3, charTranslation = TranslationID.bsb,
            startWords = listOf(2), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(2, 0, 4)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", authorByVerse),
        )
        assertEquals(
            listOf(MarkedSpan(2, 0, 4)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", null),
        )
    }

    @Test
    fun testResolveIgnoresWordsFromAnotherSource() {
        val range = VerseRange(
            "1JN", 4, 2, 2, startChar = 0, endChar = 3, charTranslation = TranslationID.bsb,
            startWords = listOf(2), wordsSource = "old",
        )
        assertEquals(
            listOf(MarkedSpan(2, 14, 17)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", authorByVerse),
        )
        assertEquals(
            listOf(MarkedSpan(2)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", null),
        )
        assertEquals(
            listOf(MarkedSpan(2)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, null, null),
        )
    }

    @Test
    fun testResolveWithoutReaderLinksIsWholeVerse() {
        val range = VerseRange(
            "1JN", 4, 2, 2, startChar = 0, endChar = 3, charTranslation = TranslationID.bsb,
            startWords = listOf(1), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(2)),
            OriginalWords.resolve(range, TranslationID.nkjv, null, readerTexts, "src", authorByVerse),
        )
        assertEquals(
            listOf(MarkedSpan(2)),
            OriginalWords.resolve(range, TranslationID.nkjv, mapOf(1 to readerLinks), readerTexts, "src", authorByVerse),
        )
        assertEquals(
            listOf(MarkedSpan(2)),
            OriginalWords.resolve(range, TranslationID.nkjv, readerByVerse, emptyMap(), "src", authorByVerse),
        )
        assertEquals(
            listOf(MarkedSpan(2, 14, 17)),
            OriginalWords.resolve(range, TranslationID.nkjv, readerByVerse, readerTexts, "src", authorByVerse),
        )
    }

    @Test
    fun testResolveMiddleVersesAreWhole() {
        val range = VerseRange(
            "1JN", 4, 1, 3, endChar = 3, charTranslation = TranslationID.bsb,
            endWords = listOf(1), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(1), MarkedSpan(2), MarkedSpan(3, 14, 17)),
            OriginalWords.resolve(range, TranslationID.web, readerByVerse, readerTexts, "src", null),
        )
        val whole = VerseRange("1JN", 4, 1, 3)
        assertEquals(
            listOf(MarkedSpan(1), MarkedSpan(2), MarkedSpan(3)),
            OriginalWords.resolve(whole, TranslationID.web, readerByVerse, readerTexts, "src", authorByVerse),
        )
    }

    @Test
    fun testResolveScatteredStopWordsFallBackToWholeVerse() {
        val text = "It is love, it is. "
        val links = listOf(
            AlignmentLink(3, 5, listOf(3)),
            AlignmentLink(6, 10, listOf(2)),
            AlignmentLink(15, 17, listOf(3)),
        )
        val range = VerseRange(
            "1JN", 4, 1, 1, startChar = 4, endChar = 6, charTranslation = TranslationID.bsb,
            startWords = listOf(3), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(1)),
            OriginalWords.resolve(range, TranslationID.web, mapOf(1 to links), mapOf(1 to text), "src", null),
        )
        // With the content word among them they are kept, and the unlinked
        // "it" between is bridged into one run.
        val more = VerseRange(
            "1JN", 4, 1, 1, startChar = 4, endChar = 11, charTranslation = TranslationID.bsb,
            startWords = listOf(2, 3), wordsSource = "src",
        )
        assertEquals(
            listOf(MarkedSpan(1, 3, 17)),
            OriginalWords.resolve(more, TranslationID.web, mapOf(1 to links), mapOf(1 to text), "src", null),
        )
        // A version that splits the words around a foreign link gives one
        // span per piece.
        assertEquals(
            listOf(MarkedSpan(1, 53, 56), MarkedSpan(1, 75, 78)),
            OriginalWords.resolve(
                VerseRange(
                    "JHN", 1, 1, 1, startChar = 0, endChar = 3, charTranslation = TranslationID.web,
                    startWords = listOf(11, 13), wordsSource = "src",
                ),
                TranslationID.bsb, mapOf(1 to john1Links), mapOf(1 to john1), "src", null,
            ),
        )
    }

    // rendering and phrase

    @Test
    fun testRenderingJoinsSplitRenderings() {
        assertEquals("the Word", OriginalWords.rendering(4, john1Links, john1))
        assertEquals("God", OriginalWords.rendering(13, john1Links, john1))
        assertNull(OriginalWords.rendering(10, john1Links, john1))
        val text = "He shall surely not die. "
        val links = listOf(
            AlignmentLink(0, 2, listOf(0)),
            AlignmentLink(3, 8, listOf(1)),
            AlignmentLink(9, 15, listOf(1)),
            AlignmentLink(16, 23, listOf(2, 3)),
        )
        assertEquals("shall … surely", OriginalWords.rendering(1, links, text))
        assertEquals("not die", OriginalWords.rendering(3, links, text))
    }

    @Test
    fun testPhraseRunsFromFirstToLast() {
        assertEquals("with God", OriginalWords.phrase(listOf(TextRange(48, 52), TextRange(53, 56)), john1))
        assertEquals("God, and the Word was God", OriginalWords.phrase(listOf(TextRange(53, 56), TextRange(75, 78)), john1))
        assertEquals("", OriginalWords.phrase(emptyList(), john1))
        // Offsets are UTF-16 units: the first letter here is two of them.
        assertEquals("𝔊od", OriginalWords.phrase(listOf(TextRange(0, 4)), "𝔊od is love. "))
        // Ranges past the end are clamped, never a crash.
        assertEquals("God. ", OriginalWords.phrase(listOf(TextRange(75, 200)), john1))
    }

    // The bundled corpus

    private fun scriptureRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "ios/Ribbon/Resources/Scripture")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun testBundledOriginalWordsDecode() {
        val root = scriptureRoot()
        val original = root?.let { File(it, "original") }
        assumeTrue("original words not present", original != null && File(original, "GEN.json").isFile)
        val lexicon = Json.decodeFromString(Lexicon.serializer(), File(original!!, "strongs.json").readText())
        val parsings = Json.decodeFromString(Parsings.serializer(), File(original, "parsing.json").readText())
        val sources = mutableSetOf<String>()
        for (book in Bible.books) {
            val text = Json.decodeFromString(OriginalBook.serializer(), File(original, "${book.id}.json").readText())
            assertEquals(book.id, text.id)
            assertTrue(text.source.startsWith("bsbt-"), text.source)
            sources.add(text.source)
            assertFalse(text.chapters.isEmpty(), book.id)
            for (chapter in text.chapters) {
                for (verse in chapter.verses) {
                    assertFalse(verse.words.isEmpty(), "${book.id} ${chapter.n}:${verse.v}")
                    for (word in verse.words) {
                        word.strongs?.let {
                            assertNotNull(lexicon.entry(it), "${book.id} ${chapter.n}:${verse.v} $it")
                        }
                        word.parse?.let {
                            assertNotNull(parsings.describe(it), "${book.id} ${chapter.n}:${verse.v} $it")
                        }
                    }
                }
            }
        }
        assertEquals(1, sources.size)
    }

    @Test
    fun testBundledLinksFitTheirText() {
        val root = scriptureRoot()
        val original = root?.let { File(it, "original") }
        val align = root?.let { File(it, "align") }
        assumeTrue(
            "word links not present",
            original != null && File(original, "GEN.json").isFile && align != null && align.isDirectory,
        )
        for (translation in TranslationRegistry.bundled) {
            assertTrue(translation.hasBundledWordLinks)
            for (book in Bible.books) {
                val words = Json.decodeFromString(OriginalBook.serializer(), File(original!!, "${book.id}.json").readText())
                val links = Json.decodeFromString(
                    BookAlignment.serializer(),
                    File(File(align!!, translation.id.rawValue), "${book.id}.json").readText(),
                )
                val text = Json.decodeFromString(
                    ScriptureBookText.serializer(),
                    File(File(root, translation.id.rawValue), "${book.id}.json").readText(),
                )
                assertEquals(book.id, links.id)
                assertEquals(translation.id, links.translation)
                assertEquals(words.source, links.source, "${translation.id.rawValue} ${book.id}")
                for (chapter in links.chapters) {
                    val own = text.chapter(chapter.n)?.ownTexts() ?: emptyMap()
                    val originalChapter = words.chapter(chapter.n)
                    for (verse in chapter.verses) {
                        val place = "${translation.id.rawValue} ${book.id} ${chapter.n}:${verse.v}"
                        val length = own[verse.v]?.length ?: fail("$place has links but no text")
                        val count = originalChapter?.words(verse.v)?.size ?: fail("$place has links but no original words")
                        var previousEnd = 0
                        for (link in verse.links) {
                            assertTrue(previousEnd <= link.start, place)
                            assertTrue(link.start < link.end, place)
                            assertTrue(link.end <= length, place)
                            assertFalse(link.words.isEmpty(), place)
                            for (index in link.words) {
                                assertTrue(index in 0 until count, "$place word $index")
                            }
                            previousEnd = link.end
                        }
                    }
                }
            }
        }
    }
}
