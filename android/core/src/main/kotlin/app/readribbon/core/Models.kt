@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

// The object model, straight from build book §03. Three relationships carry
// the whole product:
//   1. Ink lives on membership — a person has a color in a room, not a color.
//   2. Position is per-person, per-reading — there is no shared "where we are."
//   3. Notes belong to the reading, not the person — they were left for you.

// TranslationID and the translation registry live in Translations.kt.

/**
 * An account. Portrait is close to required — presence is faces. Skipping
 * gives a monogram in their ink.
 */
@Serializable
data class Person(
    val id: Uuid = Uuid.random(),
    val name: String,
    /**
     * File name of the portrait image in local storage, or a storage path
     * remotely. Nil renders a monogram in the person's ink.
     */
    val portraitPath: String? = null,
    val translation: TranslationID = TranslationID.bsb
) {

    /** The monogram shown when there is no portrait. */
    val monogram: String
        get() {
            // Swift's `prefix(1)` takes one Character — a whole grapheme — so a
            // name beginning with an emoji or any other non-BMP letter yields
            // that letter. Kotlin's String is UTF-16, so a bare `take(1)` would
            // cut a surrogate pair in half and render a replacement box; the
            // pair is kept together here.
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return ""
            val count = if (trimmed[0].isHighSurrogate() && trimmed.length > 1) 2 else 1
            return trimmed.take(count).uppercase()
        }
}

/**
 * 2–6 people. The app's root. A person may hold several rooms; they never
 * interact — nothing crosses between them (§2.4).
 */
@Serializable
data class Room(
    val id: Uuid = Uuid.random(),
    /**
     * Optional. When empty, the interface derives a name from members'
     * first names.
     */
    val name: String? = null,
    val createdAt: Instant,
    /**
     * Subscription lapsed → the room is paused: presence off, new notes
     * off. Reading and everything already left stays, forever (§2.5).
     */
    val isPaused: Boolean = false
) {

    companion object {
        // Swift declares this above the stored properties; in Kotlin a
        // type-level constant has to live in the companion, below them.
        const val capacity = 6
    }
}

/**
 * Person × Room. Ink lives here, not on Person — you're teal in one room
 * and ochre in another.
 */
@Serializable
data class Membership(
    val id: Uuid = Uuid.random(),
    val roomID: Uuid,
    val personID: Uuid,
    /**
     * Nil while the room is two people (both draw from the whole palette
     * freely, per highlight) or while an ink pick is still waiting (§4.5).
     */
    val ink: Ink? = null,
    val joinedAt: Instant
)

/** A book, in a room. The active unit. A room has one open reading at a time. */
@Serializable
data class Reading(
    val id: Uuid = Uuid.random(),
    val roomID: Uuid,
    val bookID: String,
    val startedAt: Instant,
    /** Set when the book is finished; a finished reading is an ember. */
    val finishedAt: Instant? = null,
    /**
     * The campfire. `handiwork` is the general mechanic (§2.8) — the code
     * says handiwork everywhere and fire only in the campfire's own module.
     */
    val handiwork: Handiwork
) {

    val isFinished: Boolean get() = finishedAt != null
}

/** What kind a note is: a voice memo or a written thought. */
@Serializable
enum class NoteKind {
    voice,
    written
}

/**
 * How a fresh voice note's transcript is doing (S04). Transcripts are not
 * optional: they are how the deaf read this app, and how anyone finds a
 * note again six months later (§11).
 */
@Serializable
enum class TranscriptState {
    pending, // "Transcript coming"
    ready,
    failed // "No transcript for this one." + Try again
}

/**
 * A voice memo or written thought pinned to a verse. Always "left," never
 * posted, shared, or sent — being found later is the emotional beat.
 */
@Serializable
data class Note(
    val id: Uuid = Uuid.random(),
    val readingID: Uuid,
    val authorID: Uuid,
    val verse: VerseAddress,
    val kind: NoteKind,
    /** Written body, for written notes. */
    val body: String? = null,
    /** Local file name / remote storage path of the audio, for voice notes. */
    val audioPath: String? = null,
    /**
     * Normalized waveform peaks (0...1) drawn in the author's ink. The
     * waveform is the visual; no duration is ever displayed — a duration
     * is a count and it makes people self-conscious about how long they
     * talked (S04).
     */
    val waveform: List<Float>? = null,
    val transcript: String? = null,
    val transcriptState: TranscriptState? = null,
    val createdAt: Instant,
    /**
     * Who has found this note. The record stays; the author is never told
     * (no read receipts — §6.3).
     */
    val foundBy: Set<Uuid> = emptySet(),
    /**
     * Composed offline and not yet landed: the mark renders as a hairline
     * outline in the author's ink until it lands (§4.4). No spinner, no
     * toast, no retry button.
     */
    val isPending: Boolean = false
)

/**
 * Verse range + ink. Ink semantics change at three people (§4.5). Existing
 * highlights are never recolored. A highlight is a mark on a shared page,
 * not a possession — when someone leaves, their highlights stay (§6.8).
 */
@Serializable
data class Highlight(
    val id: Uuid = Uuid.random(),
    val readingID: Uuid,
    val authorID: Uuid,
    val range: VerseRange,
    val ink: Ink,
    val createdAt: Instant
)

/**
 * A reflection card's life (§4.6). Sealed cards never name who hasn't
 * answered, never show how many have, never expire, never nag. Any member
 * may set a sealed card down for the room; it leaves without ceremony.
 */
@Serializable
enum class CardState {
    sealed,
    open,
    setDown
}

/**
 * A reflection. Opens only when every member has answered.
 * (Cards ship in phase two — the model exists now because the object model
 * and the schema should not churn when they arrive.)
 */
@Serializable
data class ReflectionCard(
    val id: Uuid = Uuid.random(),
    val readingID: Uuid,
    val chapter: Int,
    val question: String,
    /**
     * One answer per member. Answers are visible only to their author
     * until the card opens.
     */
    val answers: Map<Uuid, String> = emptyMap(),
    val state: CardState = CardState.sealed,
    val openedAt: Instant? = null
)

/**
 * A marked day — the grace mechanic (§4.7). Declared by a person, never
 * inferred by the app. Marked in the marker's local day. No limit, no
 * ledger: a counted grace is not grace.
 */
@Serializable
data class QuietDay(
    val id: Uuid = Uuid.random(),
    val roomID: Uuid,
    val personID: Uuid,
    /** The marker's local calendar day, "yyyy-MM-dd". */
    val localDate: String,
    /**
     * The marker's time zone identifier at the moment of marking, so the
     * banked interval is the marker's day everywhere (§4.9 — there is no
     * midnight, anywhere, for anyone).
     */
    val timeZoneID: String,
    val markedAt: Instant
) {

    // Swift's second init. It takes a `calendar` too, defaulted to the
    // Gregorian one; kotlinx-datetime has no Calendar type and its
    // LocalDate is always proleptic Gregorian, so the parameter has nothing
    // left to carry and is dropped.
    constructor(
        id: Uuid = Uuid.random(),
        roomID: Uuid,
        personID: Uuid,
        markedAt: Instant,
        timeZone: TimeZone
    ) : this(
        id = id,
        roomID = roomID,
        personID = personID,
        localDate = localDay(markedAt, timeZone),
        timeZoneID = timeZone.id,
        markedAt = markedAt
    )

    /**
     * The interval during which this quiet day banks the fire: the marker's
     * local day, midnight to midnight in their zone.
     */
    val bankedInterval: DateInterval?
        get() {
            val zone = try {
                TimeZone.of(timeZoneID)
            } catch (e: IllegalTimeZoneException) {
                return null
            }
            val parts = localDate.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return null
            // Swift builds DateComponents and asks the calendar for a date,
            // and guards on that being nil. Foundation's Calendar is lenient
            // where LocalDate is not: a corrupt localDate like "2024-13-01"
            // rolls over to 2025-01-01 there, and returns null here. Null is
            // the deliberate reading — a day that was never marked should bank
            // nothing, not bank some other day's hours.
            val day = try {
                LocalDate(parts[0], parts[1], parts[2])
            } catch (e: IllegalArgumentException) {
                return null
            }
            val start = day.atStartOfDayIn(zone)
            val end = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            return DateInterval(start = start, end = end)
        }

    companion object {

        // Swift's String(format: "%04d-%02d-%02d", …). Kotlin's String.format
        // goes through the default locale, which in some locales renders
        // non-ASCII digits — the stored key has to be the same ten ASCII
        // characters on every device, so the padding is done by hand.
        private fun localDay(markedAt: Instant, timeZone: TimeZone): String {
            val c = markedAt.toLocalDateTime(timeZone).date
            val year = c.year.toString().padStart(4, '0')
            val month = c.month.number.toString().padStart(2, '0')
            val day = c.day.toString().padStart(2, '0')
            return "$year-$month-$day"
        }
    }
}

/**
 * An invitation into a room. The link is the whole mechanism (S15): no
 * contact-list permission, no email field, no invite-by-username.
 */
@Serializable
data class Invite(
    val id: Uuid = Uuid.random(),
    val roomID: Uuid,
    val createdBy: Uuid,
    val createdAt: Instant,
    // Swift's init always derives this from `createdAt` and never accepts it,
    // while its Codable conformance still decodes the stored value. A Kotlin
    // data class cannot compute a stored property in a constructor body and
    // stay @Serializable, so the derivation becomes this parameter's default
    // — same arithmetic, and a decoded invite still restores what was stored.
    val expiresAt: Instant = createdAt + lifetimeDays.days
) {

    /**
     * The link is the whole mechanism (S15). The domain is the one the
     * room owns: readribbon.app (the brief's ribbon.bible was not
     * acquired).
     */
    // Swift lowercases because `UUID.uuidString` is uppercase; Kotlin's
    // Uuid.toString() is already lowercase, and the call is kept so the two
    // implementations cannot drift if either ever changes.
    fun url(): String = "https://readribbon.app/i/${id.toString().lowercase()}"

    companion object {
        // Swift declares this above the stored properties; in Kotlin a
        // type-level constant has to live in the companion, below them.
        const val lifetimeDays = 30
    }
}

/**
 * Where a person is in a reading. Never shared as "where we are" — two
 * people in Mark are simply in two places in Mark, and that is not a
 * problem to be solved.
 */
@Serializable
data class ReadingPosition(
    val readingID: Uuid,
    val personID: Uuid,
    val chapter: Int,
    val verse: Int,
    val updatedAt: Instant
)
