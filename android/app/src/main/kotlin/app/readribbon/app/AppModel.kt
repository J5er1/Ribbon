@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.readribbon.core.Bible
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.FireTuning
import app.readribbon.core.FuelEvent
import app.readribbon.core.Handiwork
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.Invite
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.QuietDay
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.RibbonClock
import app.readribbon.core.Room
import app.readribbon.core.TranscriptState
import app.readribbon.core.Translation
import app.readribbon.core.TranslationID
import app.readribbon.core.TranslationRegistry
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.core.bankedIntervals
import app.readribbon.data.AppSettings
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.data.RoomNotificationPrefs
import app.readribbon.data.ScriptureStore
import app.readribbon.data.SupabaseConfig
import app.readribbon.services.LocalPresenceService
import app.readribbon.services.PresenceService
import app.readribbon.services.PresentPerson
import app.readribbon.services.RemoteSync
import app.readribbon.services.RoomGraph
import app.readribbon.services.SupabaseClient
import app.readribbon.services.SupabaseError
import app.readribbon.services.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.core.net.toUri

// The app's one store. Local-first: every mutation lands in AppState and is
// persisted; a remote backend (when configured and signed in) syncs the
// same objects later. Cold start renders from this state instantly — no
// splash, no skeleton (§05).

/** The join a tapped invite link is waiting to run (S16). */
data class PendingInvite(val token: Uuid) {
    val id: Uuid get() = token
}

/**
 * The room's last activity line and the person it belongs to.
 *
 * Swift returns an unlabelled tuple `(personID:line:)`; Kotlin has no
 * named tuples, so this carries the same two labels.
 */
data class LastReader(val personID: Uuid, val line: String)

/**
 * Swift marks this class `@MainActor @Observable`. On Android it is a
 * `ViewModel` whose published fields are Compose snapshot state — the
 * closest equivalent, and the one that lets a composable read a field
 * directly. Main-thread confinement is a convention rather than an
 * annotation: every mutation below runs on `viewModelScope`, which is
 * main-dispatched, and the blocking work (disk, sockets, keystore) is moved
 * off it inside `LocalStore`, `SupabaseClient` and `Transcriber`.
 */
class AppModel(
    context: Context,
    // Swift's init label is `state:`; the constructor parameter is renamed
    // here only because a Kotlin property initializer cannot otherwise refer
    // past a same-named parameter.
    initialState: AppState,
    val store: LocalStore,
    val presence: PresenceService,
    /**
     * Swift reads `ScriptureStore.shared`; the Kotlin store needs a
     * `Context`, so the model holds one instead of a process singleton.
     */
    val scripture: ScriptureStore = ScriptureStore(context),
) : ViewModel() {

    /**
     * The application context, for the two services that need one:
     * transcription and the session keystore.
     */
    private val appContext: Context = context.applicationContext

    /**
     * `neverEqualPolicy` rather than the default structural one, and this is
     * load-bearing: `Handiwork` is a struct in Swift and a mutable class in
     * the Kotlin core, so feeding a fire changes an object that both the old
     * and the new `AppState` point at. Under structural equality the two
     * states would compare equal and a feeding would never reach the screen.
     */
    var state: AppState by mutableStateOf(initialState, neverEqualPolicy())
        private set

    /**
     * The backend, when configured (SupabaseConfig.REMOTE_ENABLED). Nil
     * means fully local — every remote call below is best-effort and
     * nothing blocks reading.
     */
    var remote: RemoteSync? by mutableStateOf(null)
        private set

    /** Set by an opened invite link; RootView and onboarding watch it. */
    var pendingInvite: PendingInvite? by mutableStateOf(null)

    /** Who is in the book right now (empty means the form is absent). */
    var presentPeople: List<PresentPerson> by mutableStateOf(emptyList())
        private set

    /**
     * Per-session only (§4.2): read quietly is never remembered across
     * launches — slipping in invisibly is a choice made each time.
     */
    var readingQuietly by mutableStateOf(false)

    /** The person being followed, if any. */
    var followingPersonID: Uuid? by mutableStateOf(null)

    /** Portraits cache (person id → image). */
    private val portraits = mutableStateMapOf<Uuid, ImageBitmap>()

    /**
     * Swift persists from `Task.detached(priority: .utility)`, which is not
     * tied to the model's lifetime. `viewModelScope` is, and a save started
     * as the app goes away is exactly the save that must not be cancelled,
     * so persistence gets a scope of its own. It is deliberately never
     * cancelled: it ends with the process.
     */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun persist() {
        val snapshot = state
        persistScope.launch { store.save(snapshot) }
    }

    // MARK: - Me, rooms, membership

    val me: Person? get() = state.me

    val isOnboarded: Boolean get() = state.me != null && currentRoom != null

    val currentRoom: Room?
        get() = state.rooms.firstOrNull { it.id == state.currentRoomID }
            ?: state.rooms.firstOrNull()

    fun person(id: Uuid): Person? =
        if (id == state.me?.id) state.me else state.people[id]

    fun portrait(id: Uuid): ImageBitmap? = portraits[id]

    fun members(room: Room): List<Membership> =
        state.memberships
            .filter { it.roomID == room.id }
            .sortedBy { it.joinedAt }

    fun membership(personID: Uuid, roomID: Uuid): Membership? =
        state.memberships.firstOrNull { it.roomID == roomID && it.personID == personID }

    fun myMembership(room: Room): Membership? {
        val me = state.me ?: return null
        return membership(personID = me.id, roomID = room.id)
    }

    /**
     * Ink semantics (§4.5): a room of two draws from the whole palette
     * freely; at three or more, ink is identity.
     */
    fun inkIsIdentity(room: Room): Boolean = members(room).size >= 3

    /** The display name of a room: its own, or the members' first names. */
    fun displayName(room: Room): String {
        val name = room.name
        if (name != null && name.isNotEmpty()) return name
        // Swift reads `name.split(separator: " ").first`, and that split omits
        // empty pieces: a name of nothing but spaces yields no first name at
        // all, and its owner is left out of the derived title. `firstName`
        // hands the blank back instead, so that one case is filtered here and
        // the two readings stay identical.
        val names = members(room).mapNotNull { member ->
            val full = person(member.personID)?.name ?: return@mapNotNull null
            if (full.all { it == ' ' }) return@mapNotNull null
            firstName(full)
        }
        if (names.isEmpty()) return Copy.YOUR_ROOM
        return names.joinToString(" & ")
    }

    // MARK: - Onboarding & rooms

    /**
     * `startRoom: false` is the joiner's path (S16): the person exists
     * first, the room they land in is the one the invite names.
     */
    suspend fun completeOnboarding(
        name: String,
        portraitData: ByteArray?,
        startRoom: Boolean = true,
    ) {
        // A person exists once, ever: re-running the thread must never
        // mint a second identity and orphan what the first one left.
        if (state.me != null) {
            updateMe(name = name)
            if (portraitData != null) {
                setPortrait(portraitData)
            }
            if (startRoom && currentRoom == null) {
                createRoom(name = null)
            }
            return
        }
        // The keystore outlives the app: after a reinstall the session is
        // already signed in while local state is empty. The person must
        // then be the account — a random id here would fail every RLS
        // check and orphan the account's rooms.
        val personID = remote?.userID ?: Uuid.random()
        var portraitPath: String? = null
        if (portraitData != null) {
            portraitPath =
                runCatching { store.writePortrait(portraitData, personID = personID) }.getOrNull()
            decodeImage(portraitData)?.let { portraits[personID] = it }
        }
        val person = Person(
            id = personID, name = name, portraitPath = portraitPath,
            translation = TranslationID.bsb)
        state = state.copy(me = person)
        persist()
        if (isSignedIn) {
            // Reinstall: the account's rooms and profile come back —
            // before any fresh room is minted, so an account that already
            // has rooms doesn't gain an empty stray one.
            reconcileOwnProfile()
            refreshFromRemote()
            pushLocalGraph()
        }
        if (startRoom && currentRoom == null) {
            createRoom(name = null)
        }
        persist()
    }

    fun createRoom(name: String?): Room {
        val me = state.me ?: error("room before person")
        val room = Room(name = name, createdAt = Clock.System.now())
        state = state.copy(
            rooms = state.rooms + room,
            memberships = state.memberships +
                Membership(roomID = room.id, personID = me.id, joinedAt = Clock.System.now()),
            currentRoomID = room.id)
        persist()
        return room
    }

    fun switchRoom(roomID: Uuid) {
        state = state.copy(currentRoomID = roomID)
        followingPersonID = null
        persist()
    }

    fun createInvite(room: Room): Invite {
        val me = state.me ?: error("invite before person")
        // Reuse a live invite rather than minting link after link.
        val invite: Invite
        val existing = state.invites.firstOrNull {
            it.roomID == room.id && it.expiresAt > Clock.System.now()
        }
        if (existing != null) {
            invite = existing
        } else {
            invite = Invite(roomID = room.id, createdBy = me.id, createdAt = Clock.System.now())
            state = state.copy(invites = state.invites + invite)
            persist()
        }
        // The link only works once the backend knows it — push it (and the
        // room, in case this room predates sign-in) whenever it's handed
        // out.
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            val membership = myMembership(room)
            viewModelScope.launch {
                runCatching { remote.push(room = room) }
                if (membership != null) runCatching { remote.push(membership = membership) }
                runCatching { remote.push(invite = invite) }
            }
        }
        return invite
    }

    fun isFull(room: Room): Boolean = members(room).size >= Room.capacity

    /**
     * Rooms whose rename hasn't landed remotely — merge() must not let a
     * stale pull revert an edit that was never pushed. In-memory only: a
     * relaunch before the push lands re-exposes the edge, accepted for a
     * rename.
     */
    private val pendingRenamePushes: MutableSet<Uuid> = mutableSetOf()

    /** Naming a room after the fact (S15's naming half, reachable later). */
    fun renameRoom(room: Room, name: String?) {
        val i = state.rooms.indexOfFirst { it.id == room.id }
        if (i < 0) return
        val trimmed = name?.trim()
        val rooms = state.rooms.toMutableList()
        rooms[i] = rooms[i].copy(name = if (trimmed.isNullOrEmpty()) null else trimmed)
        state = state.copy(rooms = rooms)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            val updated = state.rooms[i]
            pendingRenamePushes.add(updated.id)
            viewModelScope.launch {
                if (runCatching { remote.push(room = updated) }.isSuccess) {
                    pendingRenamePushes.remove(updated.id)
                }
            }
        }
    }

    /**
     * Leaving (§6.8): one confirmation, plainly worded, no guilt. Notes
     * default to staying — they were left for the other person.
     */
    fun leaveRoom(room: Room, keepNotesBehind: Boolean) {
        val me = state.me ?: return
        val readingIDs = state.readings.filter { it.roomID == room.id }.map { it.id }.toSet()
        var next = state
        if (!keepNotesBehind) {
            next = next.copy(notes = next.notes.filterNot {
                readingIDs.contains(it.readingID) && it.authorID == me.id
            })
        }
        // Highlights stay, always — a mark on a shared page, not a
        // possession.
        next = next.copy(
            memberships = next.memberships.filterNot {
                it.roomID == room.id && it.personID == me.id
            },
            // local copy of a departed room
            rooms = next.rooms.filterNot { it.id == room.id })
        state = next.copy(currentRoomID = next.rooms.firstOrNull()?.id)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            val roomID = room.id
            val personID = me.id
            viewModelScope.launch {
                runCatching { remote.deleteMembership(roomID = roomID, personID = personID) }
            }
        }
    }

    fun pickInk(ink: Ink, room: Room) {
        val me = state.me ?: return
        val index = state.memberships.indexOfFirst {
            it.roomID == room.id && it.personID == me.id
        }
        if (index < 0) return
        val memberships = state.memberships.toMutableList()
        memberships[index] = memberships[index].copy(ink = ink)
        state = state.copy(memberships = memberships)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            val membership = state.memberships[index]
            viewModelScope.launch {
                runCatching { remote.push(membership = membership) }
                // Remembered beside the membership, so that leaving — which
                // deletes the membership — does not also delete the choice
                // (§6.10).
                remote.rememberInk(ink = ink, roomID = room.id, personID = me.id)
            }
        }
    }

    /**
     * Coming back to a room you were in before: put your own ink on again.
     *
     * §6.10 asks that a re-invited person's ink and notes reattach rather
     * than duplicating. The notes always did — they are keyed by the author's
     * account id. The ink could not, because it lives on the membership row
     * and leaving deletes it; `room_inks` is the memory that outlives it, and
     * this is where it is put back on.
     *
     * Never over somebody else: in a room of three or more ink *is* identity
     * (§4.5), so a colour that has since been taken stays taken and the room
     * asks for a new one the way it always would.
     */
    private suspend fun restoreInk(roomID: Uuid) {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        val me = state.me ?: return
        val room = state.rooms.firstOrNull { it.id == roomID } ?: return
        val mine = state.memberships.firstOrNull {
            it.roomID == roomID && it.personID == me.id
        } ?: return
        if (mine.ink != null) return
        val remembered = remote.rememberedInk(roomID = roomID, personID = me.id) ?: return
        if (members(room).any { it.ink == remembered }) return
        pickInk(ink = remembered, room = room)
    }

    // MARK: - Readings and the fire

    /** The room's one open reading. */
    fun openReading(room: Room): Reading? =
        state.readings.firstOrNull { it.roomID == room.id && !it.isFinished }

    /** The shelf: every finished reading, oldest first (S10). */
    fun shelf(room: Room): List<Reading> =
        state.readings
            .filter { it.roomID == room.id && it.isFinished }
            .sortedBy { it.finishedAt ?: Instant.DISTANT_PAST }

    fun startReading(bookID: String, room: Room): Reading {
        val scale = Bible.book(bookID)?.scale ?: FireScale.medium
        val reading = Reading(
            roomID = room.id, bookID = bookID, startedAt = Clock.System.now(),
            handiwork = Handiwork(scale = scale))
        state = state.copy(readings = state.readings + reading)
        persist()
        pushReadingRemote(reading)
        return reading
    }

    private fun pushReadingRemote(reading: Reading) {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        val snapshot = reading.snapshot()
        viewModelScope.launch { runCatching { remote.push(reading = snapshot) } }
    }

    fun quietDays(room: Room): List<QuietDay> =
        state.quietDays.filter { it.roomID == room.id }

    fun fireState(reading: Reading, now: Instant = Clock.System.now()): FireState {
        val room = state.rooms.firstOrNull { it.id == reading.roomID } ?: return FireState.catching
        return reading.handiwork.state(
            now = now, bankedIntervals = quietDays(room).bankedIntervals)
    }

    /**
     * Fuel is reading (§4.1). Called as Scripture scrolls under the
     * reader; the engine's own throttle makes frequency harmless.
     */
    fun recordReadingActivity(reading: Reading, address: VerseAddress) {
        val me = state.me ?: return
        val index = state.readings.indexOfFirst { it.id == reading.id }
        if (index < 0) return
        val room = state.rooms.firstOrNull { it.id == reading.roomID } ?: return
        val banked = quietDays(room).bankedIntervals
        // Swift mutates the handiwork struct in place through the array.
        // Handiwork is a class here, so `feed` is called on the object
        // directly and the reading is written back through a fresh list —
        // the assignment is what tells the snapshot system the fire moved.
        val fed = state.readings[index]
        fed.handiwork.feed(personID = me.id, now = Clock.System.now(), bankedIntervals = banked)
        val readings = state.readings.toMutableList()
        readings[index] = fed
        state = state.copy(readings = readings)
        savePosition(reading = reading, address = address)
        persist()
        // The other phone learns of this feeding through the rolling
        // window — steady needs to know two people fed the same fire. One
        // push per credited event, and the fire row rides along.
        val remote = this.remote
        val event = state.readings[index].handiwork.recentFuel.lastOrNull()
        if (remote != null && remote.isSignedIn && event != null &&
            event.personID == me.id && event.at != lastPushedFuelAt
        ) {
            lastPushedFuelAt = event.at
            val updated = state.readings[index].snapshot()
            viewModelScope.launch {
                // The reading row first: a fuel event landing before its
                // reading exists fails the foreign key and is lost.
                runCatching { remote.push(reading = updated) }
                runCatching { remote.push(fuel = event, readingID = updated.id) }
            }
        }
    }

    private var lastPushedFuelAt: Instant = Instant.DISTANT_PAST

    fun savePosition(reading: Reading, address: VerseAddress) {
        val me = state.me ?: return
        val position = ReadingPosition(
            readingID = reading.id, personID = me.id,
            chapter = address.chapter, verse = address.verse,
            updatedAt = Clock.System.now())
        val index = state.positions.indexOfFirst {
            it.readingID == reading.id && it.personID == me.id
        }
        if (index >= 0) {
            val positions = state.positions.toMutableList()
            positions[index] = position
            state = state.copy(positions = positions)
        } else {
            state = state.copy(positions = state.positions + position)
        }
    }

    /** Where I am in a reading — mine, not the room's (§03). */
    fun myPosition(reading: Reading): VerseAddress {
        val me = state.me
        val position = if (me == null) {
            null
        } else {
            state.positions.firstOrNull {
                it.readingID == reading.id && it.personID == me.id
            }
        }
        if (position == null) {
            return VerseAddress(bookID = reading.bookID, chapter = 1, verse = 1)
        }
        return VerseAddress(
            bookID = reading.bookID, chapter = position.chapter, verse = position.verse)
    }

    /**
     * The room's last activity line ("Ruth read this morning") — from the
     * one last-read stamp the rolling record keeps (§13). The line is a
     * person, so tapping it goes to them (S12).
     */
    fun lastReader(room: Room): LastReader? {
        val reading = openReading(room) ?: shelf(room).lastOrNull() ?: return null
        val lastFuel = reading.handiwork.lastFuelAt ?: return null
        val feeder = reading.handiwork.recentFuel.lastOrNull()?.personID ?: return null
        // Named `reader` rather than Swift's `person`, so the local cannot
        // shadow the `person(_:)` lookup it is calling.
        val reader = person(feeder) ?: return null
        if (reader.id == state.me?.id) return null
        val name = firstName(reader.name)
        return LastReader(
            personID = reader.id,
            line = Copy.readRecently(name, RibbonClock.phrase(date = lastFuel)))
    }

    /**
     * Marking a quiet day (§4.7) banks the fire for the room. The room
     * sees who did it — an act of care, performed in public.
     */
    fun markQuietDay(room: Room) {
        val me = state.me ?: return
        val day = QuietDay(
            roomID = room.id, personID = me.id, markedAt = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault())
        if (state.quietDays.any {
                it.roomID == room.id && it.personID == me.id && it.localDate == day.localDate
            }
        ) {
            return
        }
        state = state.copy(quietDays = state.quietDays + day)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            viewModelScope.launch { runCatching { remote.push(quietDay = day) } }
        }
    }

    fun activeQuietDay(room: Room, now: Instant = Clock.System.now()): QuietDay? =
        quietDays(room).firstOrNull { it.bankedInterval?.contains(now) == true }

    /** Finishing a book (§6.5): the handiwork becomes an ember. */
    fun finishReading(reading: Reading) {
        val index = state.readings.indexOfFirst { it.id == reading.id }
        if (index < 0) return
        val readings = state.readings.toMutableList()
        readings[index] = readings[index].copy(finishedAt = Clock.System.now())
        state = state.copy(readings = readings)
        persist()
        pushReadingRemote(state.readings[index])
    }

    // MARK: - Notes

    fun notes(reading: Reading): List<Note> =
        state.notes
            .filter { it.readingID == reading.id }
            .sortedBy { it.verse }

    fun notes(reading: Reading, chapter: Int): List<Note> =
        notes(reading).filter { it.verse.chapter == chapter }

    /**
     * Notes left for me that I haven't found yet — the room's waiting rows
     * (S01). Rows, never a count, never a badge.
     */
    fun waitingNotes(room: Room): List<Note> {
        val me = state.me ?: return emptyList()
        val reading = openReading(room) ?: return emptyList()
        return notes(reading).filter { it.authorID != me.id && !it.foundBy.contains(me.id) }
    }

    fun leaveWrittenNote(body: String, verse: VerseAddress, reading: Reading): Note {
        val me = state.me ?: error("note before person")
        val note = Note(
            readingID = reading.id, authorID = me.id, verse = verse,
            kind = NoteKind.written, body = body, createdAt = Clock.System.now())
        state = state.copy(notes = state.notes + note)
        recordReadingActivity(reading = reading, address = verse)
        return note
    }

    /**
     * Swift takes the recording as a `URL` and stores its last path
     * component; the Kotlin recorder hands back a `File` in the store's own
     * audio directory, and its name is that same component.
     */
    fun leaveVoiceNote(
        audioFile: File,
        waveform: List<Float>,
        verse: VerseAddress,
        reading: Reading,
    ): Note {
        val me = state.me ?: error("note before person")
        val note = Note(
            readingID = reading.id, authorID = me.id, verse = verse,
            kind = NoteKind.voice, audioPath = audioFile.name,
            waveform = waveform, transcriptState = TranscriptState.pending,
            createdAt = Clock.System.now())
        state = state.copy(notes = state.notes + note)
        recordReadingActivity(reading = reading, address = verse)
        val noteID = note.id
        viewModelScope.launch {
            val transcript = Transcriber.transcribe(appContext, audioFile)
            setTranscript(noteID = noteID, transcript = transcript)
        }
        return note
    }

    fun retryTranscript(note: Note) {
        val path = note.audioPath ?: return
        setTranscriptState(noteID = note.id, newState = TranscriptState.pending)
        viewModelScope.launch {
            val file = store.audioFile(path)
            val transcript = Transcriber.transcribe(appContext, file)
            setTranscript(noteID = note.id, transcript = transcript)
        }
    }

    private fun setTranscript(noteID: Uuid, transcript: String?) {
        val index = state.notes.indexOfFirst { it.id == noteID }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        notes[index] = notes[index].copy(
            transcript = transcript,
            transcriptState =
                if (transcript == null) TranscriptState.failed else TranscriptState.ready)
        state = state.copy(notes = notes)
        persist()
    }

    private fun setTranscriptState(noteID: Uuid, newState: TranscriptState) {
        val index = state.notes.indexOfFirst { it.id == noteID }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        notes[index] = notes[index].copy(transcriptState = newState)
        state = state.copy(notes = notes)
    }

    /**
     * The mark settles to found. The author is never told (§6.3 — no read
     * receipts).
     */
    fun markFound(note: Note) {
        val me = state.me ?: return
        if (note.authorID == me.id) return
        val index = state.notes.indexOfFirst { it.id == note.id }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        notes[index] = notes[index].copy(foundBy = notes[index].foundBy + me.id)
        state = state.copy(notes = notes)
        persist()
    }

    /**
     * Take back your own note: the mark and the note vanish with no
     * tombstone (S04).
     */
    fun takeBack(note: Note) {
        if (note.authorID != state.me?.id) return
        val path = note.audioPath
        if (path != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { store.audioFile(path).delete() } }
            }
        }
        state = state.copy(notes = state.notes.filterNot { it.id == note.id })
        persist()
    }

    fun editWrittenNote(note: Note, body: String) {
        if (note.authorID != state.me?.id) return
        val index = state.notes.indexOfFirst { it.id == note.id }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        notes[index] = notes[index].copy(body = body)
        state = state.copy(notes = notes)
        persist()
    }

    // MARK: - Highlights

    fun highlights(reading: Reading, chapter: Int): List<Highlight> =
        state.highlights.filter { it.readingID == reading.id && it.range.chapter == chapter }

    fun highlights(reading: Reading): List<Highlight> =
        state.highlights
            .filter { it.readingID == reading.id }
            .sortedBy { it.range.start }

    /**
     * The last ink I used — pre-selected so the common case is one tap
     * (S06).
     */
    var lastUsedInk: Ink by mutableStateOf(Ink.ochre)
        private set

    fun addHighlight(range: VerseRange, ink: Ink, reading: Reading) {
        val me = state.me ?: return
        state = state.copy(
            highlights = state.highlights + Highlight(
                readingID = reading.id, authorID = me.id, range = range, ink = ink,
                createdAt = Clock.System.now()))
        lastUsedInk = ink
        recordReadingActivity(reading = reading, address = range.start)
        persist()
    }

    /** You cannot remove someone else's mark (S06). */
    fun removeHighlight(highlight: Highlight) {
        if (highlight.authorID != state.me?.id) return
        state = state.copy(highlights = state.highlights.filterNot { it.id == highlight.id })
        persist()
    }

    /**
     * My ink for a highlight right now: my membership ink when the room is
     * three or more, else free choice.
     */
    fun inkForNewHighlight(room: Room): Ink? {
        if (inkIsIdentity(room)) {
            return myMembership(room)?.ink
        }
        return null // free palette; the toolbar offers all eight
    }

    // MARK: - Settings

    val settings: AppSettings get() = state.settings

    /**
     * Swift takes an `inout` transform; `AppSettings` is an immutable data
     * class here, so the transform returns the new value instead.
     */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        state = state.copy(settings = transform(state.settings))
        persist()
    }

    fun notificationPrefs(room: Room): RoomNotificationPrefs =
        state.settings.roomNotifications[room.id] ?: RoomNotificationPrefs()

    fun setNotificationPrefs(prefs: RoomNotificationPrefs, room: Room) {
        state = state.copy(
            settings = state.settings.copy(
                roomNotifications = state.settings.roomNotifications + (room.id to prefs)))
        persist()
    }

    /**
     * What the translation picker offers: the bundled two always, plus
     * the licensed editions (NKJV, NIV, NASB 1995 — §16.8, decided).
     * Streaming needs no account: the proxy is public-read behind the
     * publishable key, so licensed translations don't wait for sync.
     */
    val availableTranslations: List<Translation>
        get() = TranslationRegistry.bundled + TranslationRegistry.licensed.filter { it.isConfigured }

    fun setTranslation(translation: TranslationID) {
        state = state.copy(me = state.me?.copy(translation = translation))
        persist()
        pushProfileRemote()
    }

    fun updateMe(name: String) {
        state = state.copy(me = state.me?.copy(name = name))
        persist()
        pushProfileRemote()
    }

    private fun pushProfileRemote(portraitData: ByteArray? = null) {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        val me = state.me ?: return
        viewModelScope.launch {
            runCatching { remote.push(profile = me, portraitData = portraitData) }
        }
    }

    fun markMarginHintSeen() {
        state = state.copy(hasSeenMarginHint = true)
        persist()
    }

    /**
     * Account deletion (§6.8). The notes question is asked once, at
     * deletion, and the answer travels with the remote delete when sync
     * exists; locally both paths clear this device.
     */
    fun deleteAccount(keepNotesBehind: Boolean) {
        // The backend forgets the person: deleting the profile cascades
        // memberships, invites, fuel, quiet days and positions; shared
        // rooms and their content stay for the people still in them.
        // (Notes aren't remote yet, so the keep/take answer is local-only
        // until the full sync engine; the bare auth user — an email and
        // nothing else — needs a service-role function and rides along
        // then too.)
        //
        // Swift writes `_ = keepNotesBehind` to silence its unused-value
        // warning; Kotlin needs no such line, and the parameter stays in the
        // signature because the question is asked at the call site today and
        // the answer travels the moment notes are remote.
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            viewModelScope.launch {
                remote.deleteAccountData()
                remote.signOut()
            }
        }
        state = AppState()
        portraits.clear()
        persist()
    }

    // MARK: - The account and the room surface of sync (§6.10, S16)

    val isSignedIn: Boolean get() = remote?.isSignedIn ?: false
    val accountEmail: String? get() = remote?.email

    suspend fun sendSignInCode(email: String) {
        val remote = this.remote ?: throw SupabaseError.NotSignedIn
        remote.sendCode(to = email)
    }

    /**
     * Verifying the emailed code is account creation and sign-in both.
     * The local person adopts the account's identity — one person, ever,
     * even across the local-first-then-signed-in seam.
     */
    suspend fun verifySignInCode(email: String, code: String) {
        val remote = this.remote ?: throw SupabaseError.NotSignedIn
        val uid = remote.verify(email = email, code = code)
        if (state.me == null) {
            // Signing in before this device has a person: a new phone, or a
            // reinstall the keystore didn't outlive. Carrying your room
            // between phones is the whole reason an account exists (§6.10),
            // and the account's own profile *is* the person — minting a
            // second local identity here and merging it afterwards is how a
            // person ends up with two of themselves.
            restorePerson(uid)
        }
        adoptRemoteIdentity(uid)
        reconcileOwnProfile()
        // Pull before push: a room this account left on another device is
        // removed by the merge, so the push can't quietly re-join it.
        refreshFromRemote()
        pushLocalGraph()
    }

    /**
     * The account's profile, made this device's person.
     *
     * Null means an account with no profile yet — an email that was verified
     * and never finished onboarding — and there is nothing to restore, so
     * the caller asks for a name as it would have anyway.
     */
    private suspend fun restorePerson(uid: Uuid) {
        val remote = this.remote ?: return
        val row = runCatching { remote.fetchOwnProfile() }.getOrNull() ?: return
        state = state.copy(
            me = Person(
                id = uid,
                name = row.name,
                portraitPath = null,
                translation = TranslationID(rawValue = row.translation)))
        persist()
        // The face is left to `reconcileOwnProfile`, which runs next and
        // asks the same question — fetching it here would download it twice.
    }

    /**
     * The account is the elder truth: signing in on a fresh device must
     * not upsert its just-typed defaults over the profile the room
     * already knows. When the account has a profile, its name,
     * translation and portrait win here; the push that follows then
     * carries the reconciled values.
     */
    private suspend fun reconcileOwnProfile() {
        // runCatching flattens the optionals: null is "no profile" and
        // "couldn't ask" alike, and both mean this device's values stand.
        val remote = this.remote ?: return
        val row = runCatching { remote.fetchOwnProfile() }.getOrNull() ?: return
        val me = (state.me ?: return).copy(
            name = row.name,
            translation = TranslationID(rawValue = row.translation))
        state = state.copy(me = me)
        if (row.portraitPath != null) refreshPortrait(me.id)
        persist()
    }

    suspend fun signOutRemote() {
        remote?.signOut()
    }

    /**
     * A person exists once, ever. Before sign-in their id was minted on
     * this device; the account's id replaces it everywhere it appears.
     * (The fuel window's person ids age out on their own within ~36 h —
     * at worst a just-adopted fire counts its own reader twice, briefly.)
     */
    private fun adoptRemoteIdentity(uid: Uuid) {
        val me = state.me ?: return
        if (me.id == uid) return
        val old = me.id
        portraits.remove(old)?.let { portraits[uid] = it }
        state = state.copy(
            me = me.copy(id = uid),
            memberships = state.memberships.map {
                if (it.personID == old) it.copy(personID = uid) else it
            },
            notes = state.notes.map { note ->
                var updated = note
                if (updated.authorID == old) updated = updated.copy(authorID = uid)
                if (updated.foundBy.contains(old)) {
                    updated = updated.copy(foundBy = updated.foundBy - old + uid)
                }
                updated
            },
            highlights = state.highlights.map {
                if (it.authorID == old) it.copy(authorID = uid) else it
            },
            positions = state.positions.map {
                if (it.personID == old) it.copy(personID = uid) else it
            },
            quietDays = state.quietDays.map {
                if (it.personID == old) it.copy(personID = uid) else it
            },
            invites = state.invites.map {
                if (it.createdBy == old) it.copy(createdBy = uid) else it
            })
        persist()
    }

    /**
     * Everything this device can honestly claim on the backend: my
     * profile and portrait, my rooms and membership, live invites, the
     * readings and their fires, my quiet days.
     */
    suspend fun pushLocalGraph() {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        val me = state.me ?: return
        var portraitData: ByteArray? = null
        val path = me.portraitPath
        if (path != null) {
            portraitData = runCatching {
                withContext(Dispatchers.IO) { store.portraitFile(path).readBytes() }
            }.getOrNull()
        }
        runCatching { remote.push(profile = me, portraitData = portraitData) }
        for (room in state.rooms) {
            runCatching { remote.push(room = room) }
            val mine = myMembership(room)
            if (mine != null) {
                runCatching { remote.push(membership = mine) }
            }
            for (invite in state.invites) {
                if (invite.roomID != room.id || invite.expiresAt <= Clock.System.now()) continue
                runCatching { remote.push(invite = invite) }
            }
            for (reading in state.readings) {
                if (reading.roomID != room.id) continue
                runCatching { remote.push(reading = reading.snapshot()) }
            }
            for (day in quietDays(room)) {
                if (day.personID != me.id) continue
                runCatching { remote.push(quietDay = day) }
            }
        }
    }

    /**
     * Pull every room I'm in and fold it into local state. Called on
     * launch, on foreground, and after joining.
     */
    suspend fun refreshFromRemote() {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        // An unpushed rename goes first, so the pull can't revert it.
        for (roomID in pendingRenamePushes.toList()) {
            val room = state.rooms.firstOrNull { it.id == roomID }
            if (room != null && runCatching { remote.push(room = room) }.isSuccess) {
                pendingRenamePushes.remove(roomID)
            }
        }
        val graph = runCatching { remote.pullRooms() }.getOrNull() ?: return
        merge(graph)
    }

    /**
     * Accepting an invite (S16): join on the backend and pull the room.
     * The caller decides whether to land in it — a join whose screen was
     * set down mid-flight still joins, but must not switch the room
     * underneath whatever the person is doing now.
     */
    suspend fun joinRoom(inviteToken: Uuid): Uuid {
        val remote = this.remote ?: throw SupabaseError.NotSignedIn
        if (!remote.isSignedIn) throw SupabaseError.NotSignedIn
        val roomID = remote.acceptInvite(token = inviteToken)
        refreshFromRemote()
        restoreInk(roomID)
        return roomID
    }

    /**
     * Swift takes a `URL`; a tapped link arrives on Android as the
     * intent's `Uri`, which is the same thing at this seam.
     */
    fun handleInviteURL(uri: Uri) {
        val token = inviteToken(from = uri) ?: return
        pendingInvite = PendingInvite(token = token)
    }

    private fun merge(graph: RoomGraph) {
        val me = state.me ?: return

        // Swift mutates `state` in place, step by step, and every later step
        // reads what the earlier ones wrote. AppState is immutable here, so
        // the same walk is done over one local value and published once at
        // the end.
        var next = state

        val rooms = next.rooms.toMutableList()
        for (row in graph.rooms) {
            val i = rooms.indexOfFirst { it.id == row.id }
            if (i >= 0) {
                // Remote wins on the multi-author name — except over a
                // local rename that hasn't landed there yet.
                var room = rooms[i]
                if (!pendingRenamePushes.contains(row.id)) {
                    room = room.copy(name = row.name)
                }
                rooms[i] = room.copy(isPaused = row.isPaused)
            } else {
                rooms.add(
                    Room(
                        id = row.id, name = row.name, createdAt = row.createdAt,
                        isPaused = row.isPaused))
            }
        }
        next = next.copy(rooms = rooms)

        val memberships = next.memberships.toMutableList()
        for (row in graph.memberships) {
            val ink = row.ink?.let { raw -> Ink.entries.firstOrNull { it.name == raw } }
            val i = memberships.indexOfFirst {
                it.roomID == row.roomId && it.personID == row.personId
            }
            if (i >= 0) {
                // My ink is authored here; everyone else's is authored
                // there.
                var membership = memberships[i]
                if (row.personId != me.id) membership = membership.copy(ink = ink)
                memberships[i] = membership.copy(joinedAt = row.joinedAt)
            } else {
                memberships.add(
                    Membership(
                        id = row.id, roomID = row.roomId, personID = row.personId,
                        ink = ink, joinedAt = row.joinedAt))
            }
        }
        // Departures propagate: a membership the backend no longer has is
        // gone here too. Mine stays within a pulled room — leaving already
        // removed it locally, and a pull racing my own join must not undo
        // the join.
        val pulledRooms = graph.rooms.map { it.id }.toSet()
        memberships.removeAll { membership ->
            membership.personID != me.id &&
                pulledRooms.contains(membership.roomID) &&
                graph.memberships.none {
                    it.roomId == membership.roomID && it.personId == membership.personID
                }
        }
        next = next.copy(memberships = memberships)

        // My own departures, made on another device: a room the backend
        // shared with other people that no longer lists me doesn't come
        // back in the pull at all. A room of one stays — it may simply
        // never have been pushed.
        // (Swift asks `members(of:)`, which reads the state it has already
        // written; the same read is done against `next` here.)
        val departed = next.rooms.filter { room ->
            !pulledRooms.contains(room.id) &&
                next.memberships.any { it.roomID == room.id && it.personID != me.id }
        }.map { it.id }.toSet()
        if (departed.isNotEmpty()) {
            next = next.copy(
                rooms = next.rooms.filterNot { departed.contains(it.id) },
                memberships = next.memberships.filterNot { departed.contains(it.roomID) })
            val current = next.currentRoomID
            if (current != null && departed.contains(current)) {
                next = next.copy(currentRoomID = next.rooms.firstOrNull()?.id)
            }
        }

        val people = next.people.toMutableMap()
        for (row in graph.profiles) {
            // Your own row is here too, and its face is asked about on the
            // same terms: a face changed on your other phone has to reach
            // this one, and only the object's tag can say that it did.
            if (row.portraitPath != null) refreshPortrait(row.id)
            if (row.id == me.id) continue
            val translation = TranslationID(rawValue = row.translation)
            val profile = (people[row.id]
                ?: Person(id = row.id, name = row.name, translation = translation))
                .copy(name = row.name, translation = translation)
            people[row.id] = profile
        }
        next = next.copy(people = people)

        val quietDays = next.quietDays.toMutableList()
        for (row in graph.quietDays) {
            if (quietDays.any {
                    it.roomID == row.roomId && it.personID == row.personId &&
                        it.localDate == row.localDate
                }
            ) {
                continue
            }
            quietDays.add(
                QuietDay(
                    id = row.id, roomID = row.roomId, personID = row.personId,
                    localDate = row.localDate, timeZoneID = row.timeZone,
                    markedAt = row.markedAt))
        }
        next = next.copy(quietDays = quietDays)

        val fires = graph.fires.associateBy { it.readingId }
        val fuelByReading = graph.fuelEvents.groupBy { it.readingId }
        val readings = next.readings.toMutableList()
        for (row in graph.readings) {
            val events = (fuelByReading[row.id] ?: emptyList())
                .map { FuelEvent(personID = it.personId, at = it.at) }
            val i = readings.indexOfFirst { it.id == row.id }
            if (i >= 0) {
                var reading = readings[i]
                if (reading.finishedAt == null) {
                    reading = reading.copy(finishedAt = row.finishedAt)
                }
                readings[i] = reading.copy(
                    handiwork = mergedHandiwork(
                        local = reading.handiwork, remote = fires[row.id], events = events))
            } else {
                val scale = FireScale.entries.firstOrNull { it.name == row.scale }
                    ?: FireScale.medium
                var handiwork = Handiwork(scale = scale)
                val fire = fires[row.id]
                if (fire != null) {
                    handiwork = Handiwork(
                        scale = scale, coalDepth = fire.coalDepth,
                        lastFuelAt = fire.lastFuelAt, restartAt = fire.restartAt,
                        stateAtLastFuel = FireState.entries
                            .firstOrNull { it.name == fire.stateAtLastFuel } ?: FireState.catching,
                        recentFuel = pruned(events))
                }
                readings.add(
                    Reading(
                        id = row.id, roomID = row.roomId, bookID = row.bookId,
                        startedAt = row.startedAt, finishedAt = row.finishedAt,
                        handiwork = handiwork))
            }
        }
        next = next.copy(readings = readings)

        state = next
        persist()
    }

    /**
     * When each face was last asked about, this launch. A conditional GET is
     * cheap — a 304 with no body — but it is still a request, and the room
     * refreshes on every foreground.
     */
    private val portraitCheckedAt = mutableMapOf<Uuid, Instant>()

    /**
     * Ask whether this person's face has changed, and take it if it has.
     *
     * The old rule was "fetch it once, if this device has nothing" — which is
     * why a changed face never travelled: every device that had already seen
     * the old one kept it forever. Nothing on the profile row can say the
     * object changed (the path is `<person id>.jpg`, always), so the object's
     * own tag is asked instead.
     */
    private fun refreshPortrait(personID: Uuid) {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        val now = Clock.System.now()
        val last = portraitCheckedAt[personID]
        if (last != null && now - last < PORTRAIT_RECHECK) return
        portraitCheckedAt[personID] = now
        viewModelScope.launch {
            val found = remote.fetchPortrait(
                personID = personID,
                ifNoneMatch = state.portraitETags[personID])
            val changed = found as? SupabaseClient.PortraitFetch.Changed ?: return@launch
            val path = runCatching {
                store.writePortrait(changed.data, personID = personID)
            }.getOrNull() ?: return@launch
            var next = state
            val mine = next.me
            next = if (mine != null && mine.id == personID) {
                next.copy(me = mine.copy(portraitPath = path))
            } else {
                val person = next.people[personID]
                if (person == null) {
                    next
                } else {
                    next.copy(people = next.people + (personID to person.copy(portraitPath = path)))
                }
            }
            val etag = changed.etag
            next = next.copy(
                portraitETags = if (etag == null) {
                    next.portraitETags - personID
                } else {
                    next.portraitETags + (personID to etag)
                })
            state = next
            decodeImage(changed.data)?.let { portraits[personID] = it }
            persist()
        }
    }

    // MARK: - Portraits

    private suspend fun loadPortraits() {
        val all = state.people.values.toMutableList()
        state.me?.let { all.add(it) }
        for (person in all) {
            val path = person.portraitPath ?: continue
            val file = store.portraitFile(path)
            val data = runCatching {
                withContext(Dispatchers.IO) { file.readBytes() }
            }.getOrNull() ?: continue
            decodeImage(data)?.let { portraits[person.id] = it }
        }
    }

    suspend fun setPortrait(data: ByteArray) {
        val me = state.me ?: return
        val path = runCatching { store.writePortrait(data, personID = me.id) }.getOrNull() ?: return
        state = state.copy(me = me.copy(portraitPath = path))
        decodeImage(data)?.let { portraits[me.id] = it }
        // This device is the truth for this face until the upload lands.
        // Without the hold-off a refresh a second later would fetch the face
        // being replaced and put it back.
        portraitCheckedAt[me.id] = Clock.System.now()
        persist()
        pushProfileRemote(portraitData = data)
    }

    /**
     * Swift's `UIImage(data:)`. Anything undecodable is the same nothing a
     * missing portrait is — the monogram renders and nothing comments on it.
     */
    private fun decodeImage(data: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
            .getOrNull()

    /**
     * A value snapshot of a reading, for a push that runs later. Swift
     * captures a struct copy; `Handiwork` is a class here, so without this
     * a push in flight would report whatever the fire had become by the
     * time it ran.
     */
    private fun Reading.snapshot(): Reading = copy(handiwork = handiwork.copy())

    companion object {

        /**
         * How long a face is taken on trust before it is asked about again.
         * A room holds six; this is a handful of tiny requests an hour, and
         * the product's own pace says a new face can take a few minutes to
         * arrive.
         */
        private val PORTRAIT_RECHECK = 15.minutes

        suspend fun load(context: Context): AppModel {
            val app = context.applicationContext
            val store = LocalStore(app)
            val state = store.load()
            val model = AppModel(app, state, store, LocalPresenceService())
            model.loadPortraits()
            if (SupabaseConfig.REMOTE_ENABLED) {
                model.remote = RemoteSync.restore(app)
            }
            return model
        }

        /**
         * https://readribbon.app/i/&lt;token&gt; or ribbon://i/&lt;token&gt;.
         *
         * Swift writes the host literally; `SupabaseConfig.INVITE_HOST`
         * holds the same value and is what the rest of this build reads.
         * `Uri.pathSegments` has already dropped the separators Swift
         * filters out of `pathComponents`, and the filter is kept so the
         * two read alike.
         */
        fun inviteToken(from: Uri): Uuid? {
            val parts = from.pathSegments.filter { it != "/" }
            val host = from.host?.lowercase()
            if (host != SupabaseConfig.INVITE_HOST && from.scheme?.lowercase() != "ribbon") {
                return null
            }
            if (parts.firstOrNull() != "i" && host != "i") return null
            val last = parts.lastOrNull() ?: return null
            return runCatching { Uuid.parse(last) }.getOrNull()
        }

        /**
         * The onboarding paste field takes whatever they have — the link, or
         * just the code out of it.
         */
        fun inviteToken(fromPasted: String): Uuid? {
            val trimmed = fromPasted.trim()
            // Swift guards on `URL(string:)` returning nil; `Uri.parse` never
            // does, and a string that is no URL simply matches nothing above.
            inviteToken(from = trimmed.toUri())?.let { return it }
            val match = UUID_PATTERN.find(trimmed) ?: return null
            return runCatching { Uuid.parse(match.value) }.getOrNull()
        }

        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /**
         * Two devices fed the same fire: keep the later feeding's read of
         * the state, the deeper bed, and the union of the window's fuel — so
         * a fire fed by two people on two phones still finds its way to
         * steady on the next feeding.
         */
        private fun mergedHandiwork(
            local: Handiwork,
            remote: RemoteSync.FireRow?,
            events: List<FuelEvent>,
        ): Handiwork {
            val union = local.recentFuel.toMutableSet()
            union.addAll(events)
            val localLast = local.lastFuelAt ?: Instant.DISTANT_PAST
            val remoteLast = remote?.lastFuelAt ?: Instant.DISTANT_PAST
            val laterIsRemote = remote != null && remoteLast > localLast
            val last = maxOf(localLast, remoteLast)
            return Handiwork(
                scale = local.scale,
                coalDepth = maxOf(local.coalDepth, remote?.coalDepth ?: 0.0),
                lastFuelAt = if (last == Instant.DISTANT_PAST) null else last,
                // Inside both branches `laterIsRemote` has already proved
                // `remote` non-null, so the compiler smart-casts it here.
                restartAt = if (laterIsRemote) remote.restartAt else local.restartAt,
                stateAtLastFuel = if (laterIsRemote) {
                    FireState.entries.firstOrNull { it.name == remote.stateAtLastFuel }
                        ?: FireState.catching
                } else {
                    local.stateAtLastFuel
                },
                recentFuel = pruned(union.toList()))
        }

        private fun pruned(events: List<FuelEvent>): List<FuelEvent> {
            val cutoff =
                Clock.System.now() - (FireTuning.standard.fuelWindowHours * 3600).seconds
            return events.filter { it.at >= cutoff }.sortedBy { it.at }
        }
    }
}
