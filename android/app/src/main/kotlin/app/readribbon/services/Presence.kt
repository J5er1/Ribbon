@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
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
    /** Scroll offset within their chapter, 0..1 — drives following only. */
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
}

interface PresenceService {
    /**
     * Open the room's channel. Opening it announces nothing: the room hears
     * you when you are reading, and this is only the line being live.
     */
    suspend fun connect(roomID: Uuid, person: Person)

    /** Close it. Called when the room goes off screen or the app goes away. */
    suspend fun disconnect()

    /**
     * Announce yourself in the book, and keep the announcement current.
     * Idempotent — the first call tracks, every later one updates. Reading
     * quietly never calls it, so others see nothing at all.
     */
    suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
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
    override suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
    ) = Unit
    override suspend fun withdraw() = Unit
    override suspend fun sendThinkingOfYou(to: Uuid) = Unit
    override suspend fun announceChange() = Unit
}
