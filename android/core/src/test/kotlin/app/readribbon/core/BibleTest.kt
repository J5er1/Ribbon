package app.readribbon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleTest {
    @Test
    fun testCanonHasSixtySixBooksInOrder() {
        assertEquals(66, Bible.books.size)
        assertEquals("GEN", Bible.books.firstOrNull()?.id)
        assertEquals("REV", Bible.books.lastOrNull()?.id)
        assertEquals("Mark", Bible.book(id = "MRK")?.name)
    }

    @Test
    fun testFireScalesMatchTheBuildBookExamples() {
        // §4.1's example table is the contract.
        for (id in listOf("PHM", "JUD", "2JN", "OBA")) {
            assertEquals("$id should be small", FireScale.small, Bible.book(id = id)?.scale)
        }
        for (id in listOf("PHP", "RUT", "JAS", "MRK")) {
            assertEquals("$id should be medium", FireScale.medium, Bible.book(id = id)?.scale)
        }
        for (id in listOf("ISA", "PSA", "GEN", "JER")) {
            assertEquals("$id should be large", FireScale.large, Bible.book(id = id)?.scale)
        }
    }

    @Test
    fun testChapterCounts() {
        assertEquals(16, Bible.book(id = "MRK")?.chapterCount)
        assertEquals(150, Bible.book(id = "PSA")?.chapterCount)
        assertEquals(1, Bible.book(id = "PHM")?.chapterCount)
        assertEquals(21, Bible.book(id = "JHN")?.chapterCount)
    }

    @Test
    fun testGoodPlacesToStart() {
        // Mark, Ruth, Philippians, John, Psalms — editorial, not algorithmic.
        assertEquals(listOf("MRK", "RUT", "PHP", "JHN", "PSA"), Bible.goodPlacesToStart)
        for (id in Bible.goodPlacesToStart) {
            assertNotNull(Bible.book(id = id))
        }
    }

    @Test
    fun testVerseAddressFormatting() {
        val address = VerseAddress(bookID = "MRK", chapter = 4, verse = 9)
        assertEquals("Mark 4:9", address.formatted)
        assertEquals("Mark 4", address.chapterFormatted)

        val range = VerseRange(bookID = "MRK", chapter = 4, startVerse = 9, endVerse = 11)
        assertEquals("Mark 4:9–11", range.formatted)
        assertTrue(range.contains(address))
        assertFalse(range.contains(VerseAddress(bookID = "MRK", chapter = 4, verse = 12)))
    }

    @Test
    fun testVerseOrdering() {
        val a = VerseAddress(bookID = "MRK", chapter = 4, verse = 9)
        val b = VerseAddress(bookID = "MRK", chapter = 4, verse = 11)
        val c = VerseAddress(bookID = "JHN", chapter = 1, verse = 1)
        assertTrue(a < b)
        assertTrue(a < c) // Mark precedes John in the canon
    }

    @Test
    fun testInkPaletteIsEightAndExcludesChartreuse() {
        assertEquals(8, Ink.entries.size)
        // Chartreuse is the brand's, not the user's.
        assertFalse(Ink.entries.any { it.darkHex.uppercase() == "D6E45C" })
        val taken: List<Ink> = listOf(Ink.teal, Ink.ochre)
        assertEquals(6, Ink.remaining(taken = taken).size)
    }
}
