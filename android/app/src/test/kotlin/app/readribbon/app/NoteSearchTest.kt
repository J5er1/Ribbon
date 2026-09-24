package app.readribbon.app

import androidx.test.core.app.ApplicationProvider
import app.readribbon.core.FireScale
import app.readribbon.core.Handiwork
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Note search (S23): "finding something someone said", on the shelf, across
 * every reading the room has done.
 *
 * The rules worth holding here are the ones a person would feel without being
 * able to name: that a search is not case- or accent-fussy, that the open book
 * is part of the room's memory and comes first, and — the one that protects
 * §6.3 — that a note left for you and not yet found is never searched, because
 * its words are the verse's to give you and not a search box's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteSearchTest {

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val ruth = Person(name = "Ruth Anne")
    private val room = Room(name = null, createdAt = now - 400.hours)

    /** The book open now. */
    private val mark = Reading(
        roomID = room.id,
        bookID = "MRK",
        startedAt = now - 30.hours,
        handiwork = Handiwork(scale = FireScale.medium),
    )

    /** An ember on the shelf. */
    private val ruthBook = Reading(
        roomID = room.id,
        bookID = "RUT",
        startedAt = now - 300.hours,
        finishedAt = now - 200.hours,
        handiwork = Handiwork(scale = FireScale.small),
    )

    private fun note(
        reading: Reading,
        author: Person,
        verse: Int,
        body: String? = null,
        transcript: String? = null,
        foundBy: Set<Uuid> = emptySet(),
    ) = Note(
        readingID = reading.id,
        authorID = author.id,
        verse = VerseAddress(bookID = reading.bookID, chapter = 1, verse = verse),
        kind = if (body != null) NoteKind.written else NoteKind.voice,
        body = body,
        transcript = transcript,
        createdAt = now - 1.hours,
        foundBy = foundBy,
    )

    private fun model(notes: List<Note>): AppModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppModel(
            context = context,
            initialState = AppState(
                me = me,
                people = mapOf(me.id to me, ruth.id to ruth),
                rooms = listOf(room),
                memberships = listOf(
                    Membership(roomID = room.id, personID = me.id, joinedAt = now - 400.hours),
                    Membership(roomID = room.id, personID = ruth.id, joinedAt = now - 400.hours),
                ),
                readings = listOf(mark, ruthBook),
                notes = notes,
                currentRoomID = room.id,
            ),
            store = LocalStore(context),
            presence = LocalPresenceService(),
        )
    }

    @Test
    fun `words and transcripts, any case, any accent`() {
        val written = note(mark, me, 3, body = "The crowd pressed round him")
        val spoken = note(ruthBook, ruth, 16, transcript = "Where you go, I will go", foundBy = setOf(me.id))
        val accented = note(ruthBook, me, 20, body = "Naomi said: appelez-moi Mara, pas Noémi")
        val m = model(listOf(written, spoken, accented))

        assertEquals(listOf(written), m.notes(room, matching = "CROWD"))
        assertEquals(listOf(spoken), m.notes(room, matching = "i will go"))
        assertEquals(listOf(accented), m.notes(room, matching = "noemi"))
    }

    @Test
    fun `a note left for you and not yet found is not searched`() {
        val waiting = note(mark, ruth, 5, body = "Read this one slowly")
        val found = note(mark, ruth, 6, body = "Read this one twice", foundBy = setOf(me.id))
        val mine = note(mark, me, 7, body = "Read this one aloud")
        val m = model(listOf(waiting, found, mine))

        assertEquals(listOf(found, mine), m.notes(room, matching = "read this"))
    }

    @Test
    fun `the open book first, then the shelf, in verse order inside each`() {
        val shelfLate = note(ruthBook, me, 22, body = "grace at the end")
        val shelfEarly = note(ruthBook, me, 2, body = "grace at the start")
        val openLate = note(mark, me, 40, body = "grace again")
        val openEarly = note(mark, me, 1, body = "grace first")
        val m = model(listOf(shelfLate, shelfEarly, openLate, openEarly))

        assertEquals(
            listOf(openEarly, openLate, shelfEarly, shelfLate),
            m.notes(room, matching = "grace"),
        )
    }

    @Test
    fun `a search begins at two characters`() {
        val m = model(listOf(note(mark, me, 1, body = "a")))
        assertEquals(emptyList<Note>(), m.notes(room, matching = "a"))
        assertEquals(emptyList<Note>(), m.notes(room, matching = "  "))
    }
}
