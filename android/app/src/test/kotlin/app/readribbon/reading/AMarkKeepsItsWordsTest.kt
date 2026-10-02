package app.readribbon.reading

import app.readribbon.core.Ink
import app.readribbon.core.MarkedSpan
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a mark carries on its way from the handles to storage, and how one
 * mark's pieces are drawn.
 *
 * The first half is here because of a defect nothing caught: the toolbar
 * rebuilt its range from the lift's two verses alone, so the offsets the
 * handles had set, and the version they were measured in, never reached
 * storage, and every mark made on Android was a mark on whole verses. The
 * look book drew phrase marks by hand and never went through the toolbar.
 */
class AMarkKeepsItsWordsTest {

    private val phrase = VerseRange(
        bookID = "JHN", chapter = 1, startVerse = 3, endVerse = 3,
        startChar = 12, endChar = 22, charTranslation = TranslationID.bsb,
    )

    @Test fun theToolbarHandsOnTheWholeLift() {
        val range = toolbarRange(ComposerState.Toolbar, phrase, liftedChapter = 1, bookID = "JHN")
        assertEquals(phrase, range)
        assertEquals(12, range?.startChar)
        assertEquals(22, range?.endChar)
        assertEquals(TranslationID.bsb, range?.charTranslation)
    }

    @Test fun theToolbarKeepsBothEndsOfARunOfVerses() {
        val run = VerseRange(
            bookID = "JHN", chapter = 1, startVerse = 3, endVerse = 5,
            startChar = 34, endChar = 9, charTranslation = TranslationID.web,
        )
        assertEquals(run, toolbarRange(ComposerState.Toolbar, run, liftedChapter = 1, bookID = "JHN"))
    }

    @Test fun noToolbarNoRange() {
        assertNull(toolbarRange(null, phrase, liftedChapter = 1, bookID = "JHN"))
        assertNull(toolbarRange(ComposerState.Write(VerseAddress("JHN", 1, 3)), phrase, 1, "JHN"))
        assertNull(toolbarRange(ComposerState.Toolbar, null, liftedChapter = 1, bookID = "JHN"))
        assertNull(toolbarRange(ComposerState.Toolbar, phrase, liftedChapter = null, bookID = "JHN"))
    }

    @Test fun piecesThatMeetAreOneMark() {
        val marks = marksOf(
            listOf(MarkedSpan(3, 10, 20), MarkedSpan(3, 20, 30), MarkedSpan(3, 25, 40)),
            Ink.teal,
        )
        assertEquals(listOf(VerseMark(verse = 3, from = 10, to = 40, ink = Ink.teal)), marks)
    }

    @Test fun piecesApartStayApart() {
        // Two places in one verse, as a version that orders the words
        // differently can put them: two marks, which never overlap, so the
        // wash draws each once.
        val marks = marksOf(listOf(MarkedSpan(3, 40, 50), MarkedSpan(3, 0, 10)), Ink.teal)
        assertEquals(
            listOf(
                VerseMark(verse = 3, from = 0, to = 10, ink = Ink.teal),
                VerseMark(verse = 3, from = 40, to = 50, ink = Ink.teal),
            ),
            marks,
        )
    }

    @Test fun aWholeVerseTakesInItsPieces() {
        val marks = marksOf(
            listOf(MarkedSpan(3, 12, 22), MarkedSpan(3), MarkedSpan(4, null, 9), MarkedSpan(4, 5, 30)),
            Ink.crimson,
        )
        assertEquals(
            listOf(
                VerseMark(verse = 3, from = null, to = null, ink = Ink.crimson),
                VerseMark(verse = 4, from = null, to = 30, ink = Ink.crimson),
            ),
            marks,
        )
    }

    @Test fun aRunToTheVersesEndTakesInWhatFollows() {
        val marks = marksOf(listOf(MarkedSpan(5, 8, null), MarkedSpan(5, 20, 30)), Ink.moss)
        assertEquals(listOf(VerseMark(verse = 5, from = 8, to = null, ink = Ink.moss)), marks)
    }
}
