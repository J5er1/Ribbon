package app.readribbon.data

import androidx.test.core.app.ApplicationProvider
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.OriginalWords
import app.readribbon.core.ReadingPoint
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.reading.VerseMark
import app.readribbon.reading.verseMarks
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The original words on the phone (A60), read from the assets the app
 * ships, through to where a mark lands and where a follow goes.
 *
 * John 1:3 is the verse throughout because its two versions say it in
 * opposite orders — the Berean Standard's "Through Him all things were
 * made", the World English's "All things were made through him" — so a
 * fraction of the verse lands on the wrong words, and only the original
 * words land on the right ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OriginalStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scripture = ScriptureStore(context)
    private val original = OriginalStore(context, scripture)
    private val john1 = VerseAddress("JHN", 1, 1)

    @Test fun theBundledFilesAreRead() {
        val book = original.original("JHN")!!
        assertEquals(book.source, original.source("JHN"))
        assertEquals("πάντα", book.chapter(1)?.words(3)?.first()?.text)
        assertNotNull(original.alignment("JHN", TranslationID.web))
        assertNull("a licensed version ships no links", original.alignment("JHN", TranslationID.nkjv))
        assertNotNull(original.lexicon?.entry("G3956"))
        assertEquals("Noun - Dative Feminine Singular", original.parsings?.describe("N-DFS"))
    }

    @Test fun aBundledVersionsLinksAreItsOwn() {
        val links = original.links(TranslationID.web, "JHN", 1, readerChapter = null)!!
        val text = scripture.chapter(john1, TranslationID.web)!!.ownText(3)!!
        val first = links[3]!!.first()
        assertEquals("All things", text.substring(first.start, first.end))
        assertEquals(listOf(0), first.words)
    }

    @Test fun aLicensedVersionIsLinkedOnlyFromTextThePhoneHolds() {
        assertNull(original.links(TranslationID.nkjv, "JHN", 1, readerChapter = null))
        // The World English's chapter stands in for a licensed one: any
        // English the phone holds is linked through the Berean Standard's.
        val held = scripture.chapter(john1, TranslationID.web)!!
        val links = original.links(TranslationID.nkjv, "JHN", 1, held)!!
        val text = held.ownText(3)!!
        val things = links[3]!!.first { 0 in it.words }
        assertTrue(text.substring(things.start, things.end).startsWith("All things"))
        // Remembered for the chapter, so the next ask need not hold the text.
        assertEquals(links, original.links(TranslationID.nkjv, "JHN", 1, readerChapter = null))
    }

    @Test fun aMarkLearnsItsWordsWhenItIsMade() {
        val made = original.anchored(phrase(), TranslationID.bsb, readerChapter = null)
        assertEquals(listOf(0), made.startWords)
        assertNull(made.endWords)
        assertEquals(original.source("JHN"), made.wordsSource)

        val whole = VerseRange("JHN", 1, 3, 3)
        assertEquals(whole, original.anchored(whole, TranslationID.bsb, readerChapter = null))
    }

    @Test fun aMarkLandsOnTheSameWordsInAnotherVersion() {
        val made = original.anchored(phrase(), TranslationID.bsb, readerChapter = null)
        val web = scripture.chapter(john1, TranslationID.web)!!
        val bsb = scripture.chapter(john1, TranslationID.bsb)!!

        // "all things" in the Berean Standard, twelve letters in, is "All
        // things" at the very start of the World English.
        assertEquals(
            listOf(VerseMark(verse = 3, from = 0, to = 10, ink = Ink.teal)),
            verseMarks(original, "JHN", 1, listOf(highlight(made)), TranslationID.web, web),
        )
        // On the version it was made in, exactly where it was made.
        assertEquals(
            listOf(VerseMark(verse = 3, from = 12, to = 22, ink = Ink.teal)),
            verseMarks(original, "JHN", 1, listOf(highlight(made)), TranslationID.bsb, bsb),
        )
        // A mark made before marks carried words follows them all the same,
        // through the author's version's links.
        assertEquals(
            listOf(VerseMark(verse = 3, from = 0, to = 10, ink = Ink.teal)),
            verseMarks(original, "JHN", 1, listOf(highlight(phrase())), TranslationID.web, web),
        )
    }

    @Test fun followingLandsOnTheSameWords() {
        // Their line is at the top of 1:3 on the World English: "All things".
        val web = scripture.chapter(john1, TranslationID.web)!!
        val bsb = scripture.chapter(john1, TranslationID.bsb)!!
        val theirLinks = original.links(TranslationID.web, "JHN", 1, web)!![3]!!
        val word = OriginalWords.word(0.0, web.ownText(3)!!, theirLinks)
        assertEquals(0, word)

        // On the Berean Standard those words are twelve letters in.
        val text = bsb.ownText(3)!!
        val heard = ReadingPoint(chapter = 1, verse = 3, part = 0.0)
        val carried = OriginalWords.carried(
            heard, word = word, wordsSource = original.source("JHN"),
            from = TranslationID.web, to = TranslationID.bsb, source = original.source("JHN"),
            links = original.links(TranslationID.bsb, "JHN", 1, bsb)!![3], text = text,
        )
        assertEquals(12.0 / text.length, carried.part, 1e-9)
    }

    private fun phrase() = VerseRange(
        bookID = "JHN", chapter = 1, startVerse = 3, endVerse = 3,
        startChar = 12, endChar = 22, charTranslation = TranslationID.bsb,
    )

    private fun highlight(range: VerseRange) = Highlight(
        readingID = Uuid.random(), authorID = Uuid.random(), range = range, ink = Ink.teal,
        createdAt = Clock.System.now(),
    )
}
