package app.readribbon.reading

import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.Copy
import app.readribbon.core.OriginalLanguage
import app.readribbon.core.OriginalWord
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.data.OriginalStore
import app.readribbon.data.ScriptureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the original panel and the line over the toolbar say (A60, §7), read
 * from the words, links and versions the app ships.
 *
 * John 1:1 throughout: "the Word was with God" has a Greek article between
 * "with" and "God" that no English word says, which is exactly the word the
 * panel has to show and the line has to leave unnamed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OriginalPanelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scripture = ScriptureStore(context)
    private val original = OriginalStore(context, scripture)
    private val john = scripture.chapter(VerseAddress("JHN", 1, 1), TranslationID.bsb)!!
    private val johnText = john.ownText(1)!!

    private fun phrase(words: String): VerseRange {
        val from = johnText.indexOf(words)
        return VerseRange(
            bookID = "JHN", chapter = 1, startVerse = 1, endVerse = 1,
            startChar = from, endChar = from + words.length,
            charTranslation = TranslationID.bsb,
        )
    }

    @Test fun aPhraseShowsItsWordsInOriginalOrderWithTheUnsaidOnesBetween() {
        val reading = originalReadingOf(original, scripture, phrase("the Word was with God"), TranslationID.bsb, john)!!
        assertEquals(OriginalLanguage.greek, reading.language)
        assertFalse(reading.wholeVerse)
        val columns = reading.verses.single().columns
        assertEquals(listOf(6, 7, 8, 9, 10, 11), columns.map { it.index })
        assertEquals(listOf("ὁ", "Λόγος", "ἦν", "πρὸς", "τὸν", "Θεόν"), columns.map { it.word.text })
        assertEquals("Word", columns[1].rendering)
        // τὸν: the article no English word says by itself.
        assertNull(columns[4].rendering)
        assertEquals(mapOf(1 to listOf(6, 7, 8, 9, 10, 11)), reading.chosen)
    }

    @Test fun aWholeVerseShowsAllItsWords() {
        val reading = originalReadingOf(original, scripture, VerseRange("JHN", 1, 1, 1), TranslationID.bsb, john)!!
        assertEquals(17, reading.verses.single().columns.size)
        assertFalse(reading.wholeVerse)
    }

    @Test fun aVersionWithNoLinksShowsTheWholeVerseAndSaysSo() {
        val chapter = original.original("JHN")!!.chapter(1)!!
        val reading = readOriginal(
            range = phrase("the Word was with God"),
            original = chapter,
            links = null,
            texts = john.ownTexts(),
            pivotLinks = null,
            pivotTexts = emptyMap(),
        )!!
        assertTrue(reading.wholeVerse)
        assertEquals(17, reading.verses.single().columns.size)
    }

    @Test fun aWholeVerseThisVersionCannotMatchSaysSo() {
        // Every word, as for any whole verse; but each is said in the Berean
        // Standard's words rather than this version's, and the panel says so.
        val chapter = original.original("JHN")!!.chapter(1)!!
        val reading = readOriginal(
            range = VerseRange("JHN", 1, 1, 1),
            original = chapter,
            links = mapOf(1 to emptyList()),
            texts = john.ownTexts(),
            pivotLinks = original.links(TranslationID.bsb, "JHN", 1, john),
            pivotTexts = john.ownTexts(),
        )!!
        assertTrue(reading.wholeVerse)
        assertEquals(17, reading.verses.single().columns.size)
        assertEquals("Word", reading.verses.single().columns[7].rendering)
    }

    @Test fun theLineLeavesOutAVerseThisVersionCannotMatch() {
        // John 1:1–2, with no links for verse 2: the line still names verse
        // 1's words rather than going quiet over the whole selection.
        val chapter = original.original("JHN")!!.chapter(1)!!
        val links = original.links(TranslationID.bsb, "JHN", 1, john)!!.filterKeys { it == 1 }
        val line = originalLine(VerseRange("JHN", 1, 1, 2), held = null, chapter, links, john.ownTexts())!!
        assertEquals(chapter.words(1)!!.map { it.text }, line.words.map { it.text })
    }

    @Test fun theLineNamesTheWordUnderTheFinger() {
        val at = johnText.indexOf("Word") + 1
        val under = originalUnderLift(original, VerseRange("JHN", 1, 1, 1), HeldWord(1, at), TranslationID.bsb, john)!!
        assertEquals(Copy.originalVerb(OriginalLanguage.greek), under.verb)
        val line = under.line!!
        assertEquals("Λόγος", line.text)
        assertEquals("Logos", line.translit)
        assertEquals("Word", line.rendering)
        assertEquals("Logos. Word.", line.spoken)
    }

    @Test fun aFingerJustPastAWordStillNamesIt() {
        val end = johnText.indexOf("Word") + "Word".length
        val line = originalUnderLift(original, VerseRange("JHN", 1, 1, 1), HeldWord(1, end), TranslationID.bsb, john)?.line
        assertEquals("Λόγος", line?.text)
    }

    @Test fun theLineIsQuietWhereNothingLinksUnderTheFinger() {
        // Between the comma after "Word" and the space before "and".
        val comma = johnText.indexOf(",") + 1
        val under = originalUnderLift(original, VerseRange("JHN", 1, 1, 1), HeldWord(1, comma), TranslationID.bsb, john)!!
        assertNull(under.line)
    }

    @Test fun onceTheHandlesMoveTheLineSaysTheSelection() {
        val line = originalUnderLift(original, phrase("the Word was with God"), held = null, TranslationID.bsb, john)!!.line!!
        assertEquals("ὁ Λόγος ἦν πρὸς τὸν Θεόν", line.text)
        assertNull("several words carry no rendering", line.rendering)
    }

    @Test fun theLineIsQuietForALicensedVersionWithoutItsText() {
        val chapter = original.original("JHN")!!.chapter(1)!!
        assertNull(originalLine(VerseRange("JHN", 1, 1, 1), HeldWord(1, 3), chapter, links = null, texts = emptyMap()))
    }

    @Test fun exodusIsHebrew() {
        val exodus = scripture.chapter(VerseAddress("EXO", 15, 1), TranslationID.bsb)!!
        val under = originalUnderLift(original, VerseRange("EXO", 15, 11, 11), null, TranslationID.bsb, exodus)!!
        assertEquals("the hebrew", under.verb)
    }

    @Test fun aramaicIsNamedWhenMostOfTheWordsAreAramaic() {
        val aramaic = OriginalWord("מַלְכָּא", "mal·kā", isAramaic = true)
        val hebrew = OriginalWord("וַיְדַבְּרוּ", "way·ḏab·bə·rū")
        assertEquals(OriginalLanguage.aramaic, languageOf("DAN", listOf(aramaic, aramaic, hebrew)))
        assertEquals(OriginalLanguage.hebrew, languageOf("DAN", listOf(aramaic, hebrew)))
        assertEquals(OriginalLanguage.greek, languageOf("JHN", listOf(aramaic)))
        assertEquals("the aramaic", Copy.originalVerb(OriginalLanguage.aramaic))
        assertEquals("the Aramaic · Daniel 2:4", Copy.originalHeading(OriginalLanguage.aramaic, "Daniel 2:4"))
    }

    @Test fun theRoomsVersionsAreYoursFirstThenByName() {
        val readers = roomReaders(
            mine = TranslationID.bsb,
            others = listOf(
                "Ruth Alderman" to TranslationID.web,
                "Ann Brooke" to TranslationID.bsb,
                "Mara Lee" to TranslationID.web,
            ),
        )
        assertEquals(
            listOf(TranslationID.bsb to "you and Ann", TranslationID.web to "Mara and Ruth"),
            readers,
        )
    }

    @Test fun eachVersionSaysTheSameWordsInItsOwn() {
        val chosen = mapOf(1 to listOf(6, 7, 8, 9, 10, 11))
        val web = scripture.chapter(VerseAddress("JHN", 1, 1), TranslationID.web)!!
        val links = original.links(TranslationID.web, "JHN", 1, web)
        val line = roomVersionLine(TranslationID.web, "Ruth", chosen, links, web.ownTexts())
        assertEquals("the Word was with God", line.words)
        assertFalse(line.whole)

        val unlinked = roomVersionLine(TranslationID.nkjv, "Ann", chosen, links = null, texts = web.ownTexts())
        assertTrue("no links: the whole verse, said as such", unlinked.whole)
        assertEquals(web.ownText(1)!!.trim(), unlinked.words)

        val missing = roomVersionLine(TranslationID.niv, "Ann", chosen, links = null, texts = null)
        assertNull("not on this phone", missing.words)
        assertNotNull(Copy.ORIGINAL_NOT_ON_THIS_PHONE)
    }

    @Test fun aWordIsReadOutWithoutEmptyParts() {
        assertEquals("logos. Word. Noun.", Copy.originalWordSpoken("logos", "Word", "Noun"))
        assertEquals("ton.", Copy.originalWordSpoken("ton", "", ""))
    }
}
