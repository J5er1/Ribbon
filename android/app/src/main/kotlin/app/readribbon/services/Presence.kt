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
}

interface PresenceService {
    /** Join a room's presence channel. Reading quietly joins nothing —
     *  others see nothing at all. */
    suspend fun join(roomID: Uuid, person: Person)

    suspend fun leave()

    suspend fun update(position: VerseAddress?, scrollFraction: Double, isIdle: Boolean)

    /** The contentless signal (§4.3). Repeats inside a few minutes collapse
     *  into one delivery. */
    suspend fun sendThinkingOfYou(to: Uuid)

    val events: Flow<PresenceEvent>
}

/**
 * Presence with no server: nobody else is ever here. The form is simply
 * absent, and nothing comments on that.
 *
 * This is the honest local backend, not a simulation — the same standing
 * the iOS build's is in (docs/deviations.md §9). The Supabase Realtime
 * channel client is the next backend step on both platforms.
 */
class LocalPresenceService : PresenceService {

    private val _events = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)
    override val events: Flow<PresenceEvent> = _events.asSharedFlow()

    override suspend fun join(roomID: Uuid, person: Person) = Unit
    override suspend fun leave() = Unit
    override suspend fun update(position: VerseAddress?, scrollFraction: Double, isIdle: Boolean) = Unit
    override suspend fun sendThinkingOfYou(to: Uuid) = Unit
}
