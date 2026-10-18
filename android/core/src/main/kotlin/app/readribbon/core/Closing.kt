@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

// Closing a room (build book §6.8, A73): "Requires everyone to agree, or 30
// days of silence plus one member's request. On close, every member gets an
// export of the whole shelf — notes, highlights, cards — as a readable file.
// No dark pattern, no retention offer."
//
// The port of Closing.swift, case for case; ClosingTests.swift and
// ClosingTest.kt hold the two to the same answers.

/** Where a room stands on closing, from your side of it. Who has not asked is never named. */
enum class RoomClosing {
    /** You have not asked. */
    open,

    /**
     * You have; somebody else has not, and the room has not been quiet long
     * enough for one request to be enough.
     */
    waitingForOthers,

    /** It is closed. */
    closed;

    companion object {
        fun of(room: Room, memberships: List<Membership>, me: Uuid): RoomClosing {
            if (room.closedAt != null) return closed
            val mine = memberships.firstOrNull { it.personID == me }
            if (mine == null || !mine.wouldClose) return open
            return waitingForOthers
        }

        /**
         * Whether your yes closes the room as you say it: everyone else in it
         * has said yes already — or left, which a room of one is — or it has
         * been quiet long enough for one request to be enough. A yes already
         * given closes the room once this turns true, by being said again.
         */
        fun closesOnYourYes(room: Room, memberships: List<Membership>, me: Uuid, quiet: Boolean): Boolean {
            if (room.closedAt != null) return false
            if (quiet) return true
            return memberships
                .filter { it.roomID == room.id && it.personID != me }
                .all { it.wouldClose }
        }
    }
}

/** Thirty days in which nobody read, wrote or marked anything: then one person's request closes the room. */
object RoomSilence {
    val closesAfter = 30.days

    /**
     * When anybody last did anything in the room that this phone knows of:
     * arrived, read (the fire's last fuel), left a note, or laid a mark. The
     * server counts card answers too; the phone does not hold their times,
     * and it only uses this to say which way a request will go.
     */
    fun lastHeard(
        room: Room,
        memberships: List<Membership>,
        readings: List<Reading>,
        notes: List<Note>,
        highlights: List<Highlight>,
    ): Instant {
        val roomReadings = readings.filter { it.roomID == room.id }
        val readingIDs = roomReadings.map { it.id }.toSet()
        val times = mutableListOf(room.createdAt)
        times += memberships.filter { it.roomID == room.id }.map { it.joinedAt }
        times += roomReadings.mapNotNull { it.handiwork.lastFuelAt }
        times += notes.filter { it.readingID in readingIDs }.map { it.createdAt }
        times += highlights.filter { it.readingID in readingIDs }.map { it.createdAt }
        return times.max()
    }

    fun isQuiet(lastHeard: Instant, now: Instant): Boolean = now - lastHeard > closesAfter
}

/**
 * The whole shelf of a room as a readable file (§6.8): every book, and in
 * each its notes, marks and the cards the room opened. Plain text, so it
 * opens anywhere and still reads in twenty years.
 */
object ShelfExport {
    /** The file's own name: the room's, and what it is. */
    fun fileName(title: String): String {
        val safe = title.map { if (it in "/\\:*?\"<>|") '-' else it }.joinToString("").trim()
        return "Ribbon - ${safe.ifEmpty { "A room" }}.txt"
    }

    /**
     * The shelf, written out. [name] says who someone is; [keptAt] is the day
     * the file is made, and every date is the day in [timeZone].
     */
    fun text(
        title: String,
        readings: List<Reading>,
        notes: List<Note>,
        highlights: List<Highlight>,
        cards: List<ReflectionCard>,
        name: (Uuid) -> String,
        keptAt: Instant,
        timeZone: TimeZone,
    ): String {
        val lines = mutableListOf(title, "The shelf, kept ${day(keptAt, timeZone)}.", "")
        val books = readings.sortedWith(compareBy<Reading>({ it.startedAt }, { it.id.toString().uppercase() }))
        if (books.isEmpty()) {
            lines += listOf("Nothing was read here.", "")
        }
        for (reading in books) {
            lines += Bible.book(reading.bookID)?.name ?: reading.bookID
            val finished = reading.finishedAt
            lines += if (finished != null) {
                "Read from ${day(reading.startedAt, timeZone)} to ${day(finished, timeZone)}."
            } else {
                "Begun ${day(reading.startedAt, timeZone)}, and not finished."
            }
            lines += ""

            val theirNotes = notes
                .filter { it.readingID == reading.id }
                .sortedWith(compareBy<Note>({ it.verse }, { it.createdAt }))
            if (theirNotes.isNotEmpty()) {
                lines += listOf("Notes", "")
                for (note in theirNotes) {
                    lines += "${note.verse.formatted}, ${name(note.authorID)}, ${day(note.createdAt, timeZone)}"
                    lines += words(note)
                    lines += ""
                }
            }

            val theirMarks = highlights
                .filter { it.readingID == reading.id }
                .sortedWith(compareBy<Highlight>({ start(it) }, { it.createdAt }))
            if (theirMarks.isNotEmpty()) {
                lines += listOf("Marks", "")
                for (mark in theirMarks) {
                    lines += "${mark.range.formatted}, ${name(mark.authorID)}"
                }
                lines += ""
            }

            // Only the cards the room opened: a sealed card's answers are
            // nobody's to read until everyone has answered (§4.6).
            val theirCards = cards
                .filter { it.readingID == reading.id && it.state == CardState.open }
                .sortedBy { it.chapter }
            if (theirCards.isNotEmpty()) {
                lines += listOf("Cards", "")
                for (card in theirCards) {
                    lines += "Chapter ${card.chapter}: ${card.question}"
                    val answers = card.answers.entries
                        .map { Triple(name(it.key), it.key.toString().uppercase(), it.value) }
                        .sortedWith(compareBy({ it.first }, { it.second }))
                    for ((who, _, body) in answers) {
                        lines += "$who: $body"
                    }
                    lines += ""
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun start(mark: Highlight): VerseAddress =
        VerseAddress(bookID = mark.range.bookID, chapter = mark.range.chapter, verse = mark.range.startVerse)

    private fun words(note: Note): String = when (note.kind) {
        NoteKind.written -> note.body ?: ""
        NoteKind.voice -> {
            val transcript = note.transcript
            if (!transcript.isNullOrEmpty()) "A voice note: “$transcript”" else "A voice note."
        }
    }

    /** "3 September 2026", in the day's own zone, set by hand so both phones write the same. */
    internal fun day(date: Instant, timeZone: TimeZone): String {
        val local = date.toLocalDateTime(timeZone).date
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        return "${local.day} ${months[local.month.number - 1]} ${local.year}"
    }
}
