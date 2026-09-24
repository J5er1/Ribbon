@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.ReadingPoint
import app.readribbon.core.ReadingReport
import app.readribbon.core.VerseAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Presence (§4.2): the live signal is ephemeral and socket-only. Nothing
// here persists — the roster is what the socket says right now, and when the
// socket is gone, absence is the honest rendering of absence.
//
// The channel and the presence on it are two different things, and this is
// the seam where that distinction lives. The channel is the room's: it is
// open whenever you have the room on screen, because it is also how the room
// learns that someone joined it, left a note, or fed the fire without
// waiting for your next launch. Presence is the book's: it is announced only
// while you are actually reading, and never while reading quietly.

/** Someone in the book right now. */
data class PresentPerson(
    val id: Uuid,
    val name: String,
    /** Where they are — "Mark 6" in the expanded panel. An address, never a
     *  percentage. */
    val position: VerseAddress? = null,
    /**
     * How far down their chapter they are, 0..1. Still sent, because older
     * builds decode it; nothing reads it any more — a follow is carried by
     * the `reading` line (see [PresenceEvent.Reading]).
     */
    val scrollFraction: Double = 0.0,
    /** ~4 minutes with no scroll: "here, but still." Dimmed, never removed. */
    val isIdle: Boolean = false,
    /** Who they are following, if anyone — how "Ruth is with you" knows to
     *  appear (§4.2). Never a count of followers. */
    val followingPersonID: Uuid? = null,
)

sealed interface PresenceEvent {
    data class Roster(val people: List<PresentPerson>) : PresenceEvent
    data class ThinkingOfYou(val fromName: String) : PresenceEvent

    /**
     * Somebody in this room changed something the room renders from. It
     * carries no content on purpose — it is a nudge to pull, not a second,
     * racing copy of the truth.
     */
    data class RoomChanged(val roomID: Uuid) : PresenceEvent

    /**
     * Where somebody's reading line is, finer than the verse presence
     * carries (§4.2): their phone says it only while it can see that
     * somebody follows them. Stamped here, on arrival — nothing on the wire
     * carries a time.
     *
     * @param source the stream it came from. Two devices of one person are
     *   one person on the roster, and this is how a follow tells them apart.
     */
    data class Reading(
        val personID: Uuid,
        val source: String,
        val book: String,
        val report: ReadingReport,
    ) : PresenceEvent
}

/**
 * The latest word of where someone is reading, as the follow reads it.
 *
 * @param source the `reading` stream it came from; null when it came from
 *   presence instead.
 * @param fromPresence an older app's phone, which never sends `reading`:
 *   the verse from its presence, taken as a report of its own. That verse is
 *   always a scroll behind, so a follow never holds a page to it.
 */
data class HeardReading(
    val book: String,
    val report: ReadingReport,
    val source: String?,
    val fromPresence: Boolean = false,
)

interface PresenceService {
    /**
     * Open the room's channel. Opening it announces nothing: the room hears
     * you when you are reading, and this is only the line being live.
     */
    suspend fun connect(roomID: Uuid, person: Person)

    /**
     * Close it and forget it: the room has changed, or the account has.
     * Everything this device was saying about itself goes with it.
     */
    suspend fun disconnect()

    /**
     * Put the line down for a while — the app has been away past its grace
     * (§4.2). The socket closes, but what this device was saying about
     * itself is kept, so that [connect] to the same room says it again
     * without anybody having to ask.
     */
    suspend fun suspend()

    /**
     * Announce yourself in the book, and keep the announcement current.
     * Idempotent — the first call tracks, every later one updates. Reading
     * quietly never calls it, so others see nothing at all.
     *
     * @param activity whether this is the reader's own doing. A page carried
     *   by a follow moves without anybody touching it, and that is not
     *   reading: it moves the verse presence carries, but not the clock that
     *   turns a reader "here, but still" (§4.2).
     */
    suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
        activity: Boolean = true,
    )

    /**
     * Stop announcing. The channel stays open — you are still in the room,
     * you are simply not in the book.
     */
    suspend fun withdraw()

    /** The contentless signal (§4.3). Repeats inside a few minutes collapse
     *  into one delivery. */
    suspend fun sendThinkingOfYou(to: Uuid)

    /**
     * Tell the room that something it renders from has changed, so the other
     * phones catch up now rather than at their next foreground.
     */
    suspend fun announceChange()

    /**
     * Where your reading line is, for the person following you (§4.2). A
     * point, never a time (§13). Sent only while somebody can be seen
     * following you — the caller holds that gate.
     *
     * @param end the bottom of what your screen shows, when it is known.
     * @param settled the scroll has come to rest, rather than being sampled
     *   in the middle of it.
     * @param carried your own page is being carried by a follow of yours: it
     *   is where the page is, not where you read to.
     */
    suspend fun sendReading(
        book: String,
        at: ReadingPoint,
        end: ReadingPoint?,
        settled: Boolean,
        carried: Boolean,
    )

    val events: Flow<PresenceEvent>
}

/**
 * Presence with no server: nobody else is ever here. The form is simply
 * absent, and nothing comments on that.
 *
 * This is the honest local backend, not a simulation — the same standing the
 * iOS build's is in (docs/deviations.md §9).
 */
class LocalPresenceService : PresenceService {

    private val _events = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)
    override val events: Flow<PresenceEvent> = _events.asSharedFlow()

    override suspend fun connect(roomID: Uuid, person: Person) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun suspend() = Unit
    override suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
        activity: Boolean,
    ) = Unit
    override suspend fun withdraw() = Unit
    override suspend fun sendThinkingOfYou(to: Uuid) = Unit
    override suspend fun announceChange() = Unit
    override suspend fun sendReading(
        book: String,
        at: ReadingPoint,
        end: ReadingPoint?,
        settled: Boolean,
        carried: Boolean,
    ) = Unit
}
