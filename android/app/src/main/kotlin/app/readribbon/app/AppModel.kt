@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.app

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import app.readribbon.data.SupabaseConfig
import app.readribbon.services.Auth0Service
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
import app.readribbon.core.CardState
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.FireTuning
import app.readribbon.core.FuelEvent
import app.readribbon.core.Handiwork
import app.readribbon.core.Highlight
import app.readribbon.core.ReflectionCard
import app.readribbon.core.ReflectionPrompts
import app.readribbon.core.Ink
import app.readribbon.core.Invite
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.QuietDay
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.Ribbon
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
import app.readribbon.design.Haptics
import app.readribbon.services.Connectivity
import app.readribbon.services.Destination
import app.readribbon.services.LocalPresenceService
import app.readribbon.services.NotificationKind
import app.readribbon.services.Notifications
import app.readribbon.services.notesLeftLine
import app.readribbon.services.PresenceEvent
import app.readribbon.services.PresenceService
import app.readribbon.services.PresentPerson
import app.readribbon.services.RemoteSync
import app.readribbon.services.RoomWatch
import app.readribbon.services.ReleaseInfo
import app.readribbon.services.RoomGraph
import app.readribbon.services.SupabaseClient
import app.readribbon.services.SupabaseError
import app.readribbon.services.RoomChannel
import app.readribbon.services.Transcriber
import app.readribbon.services.UpdateService
import app.readribbon.services.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
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
import androidx.lifecycle.viewModelScope

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
     * Whether there is a network — read by the fire, and by nothing else.
     *
     * S01's offline room dims its fire by about 8% and says nothing at all;
     * see [Connectivity] for why there is no banner and never will be. It
     * lives on the model rather than in a composition local because the
     * callback it registers has to be unregistered, and the model is the one
     * thing on this screen with a lifetime.
     */
    private val connectivity = Connectivity(appContext)

    /** True when the room can reach the people in it. */
    val isOnline: Boolean get() = connectivity.online

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

    /**
     * Where a tapped notification is asking the app to go (S19). Cleared by
     * whoever honours it, exactly as [pendingInvite] is.
     */
    var pendingDestination: Destination? by mutableStateOf(null)

    /**
     * The room the person is actually looking at, or null when Ribbon is not
     * in front of them.
     *
     * Not [currentRoom], which is a *selection* and stays set in a pocket.
     * This is what lets a notification stay quiet about something the room is
     * already unfurling in place under the fire — §6.3 asks for "a
     * notification, or nothing at all", and a heads-up sliding over a waiting
     * row about the same note is both at once.
     */
    var visibleRoomID: Uuid? by mutableStateOf(null)

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

    /** OTA updates (GitHub Releases / in-app updater) */
    var updateState: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    /** Portraits cache (person id → image). */
    private val portraits = mutableStateMapOf<Uuid, ImageBitmap>()

    /**
     * One [Haptics] for the model's whole life.
     *
     * It used to be constructed per arriving tap, which re-did the
     * `VibratorManager` lookup and the primitive-support probe every time —
     * on the one interaction in the product that §9.3 says must feel
     * immediate.
     */
    private val haptics = Haptics(appContext)

    init {
        viewModelScope.launch {
            presence.events.collect { event ->
                when (event) {
                    is PresenceEvent.Roster -> {
                        someoneOpenedTheBook(event.people)
                        presentPeople = event.people
                    }
                    is PresenceEvent.ThinkingOfYou -> {
                        thinkingOfYouArrived(event.fromName)
                    }
                    is PresenceEvent.RoomChanged -> {
                        roomChangedRemotely(event.roomID)
                    }
                }
            }
        }
    }

    /** Who was in the book last time the roster spoke. */
    private var wasReading: Set<Uuid> = emptySet()

    /**
     * §10.3's fourth notification — "Ruth is reading Mark" — which had a
     * switch on S19, a channel in Android's settings and no post anywhere.
     *
     * It is the one of the six that cannot ride the fifteen-minute pull:
     * presence is ephemeral and lives only on the socket, and its own switch
     * subtitle is "So you can read at the same time", which a quarter-hour-old
     * version of would be a lie. So it posts from the live roster and only
     * while Ribbon is running — which is the honest shape of the feature and
     * is written down in `RoomWatch`'s header and in docs/deviations.md A33.
     *
     * Once per arrival rather than per heartbeat: the roster repeats, and a
     * notification for every beat of somebody else's presence would be the
     * app tapping a shoulder every thirty seconds. The stable id means a
     * second arrival replaces rather than stacks, exactly as a note does.
     *
     * Never for me, and never when there is no reading to name.
     */
    private fun someoneOpenedTheBook(people: List<PresentPerson>) {
        val now = people.map { it.id }.toSet()
        val arrived = now - wasReading
        wasReading = now
        if (arrived.isEmpty()) return

        val room = currentRoom ?: return
        val reading = openReading(room) ?: return
        val book = Bible.book(reading.bookID)?.name ?: return
        val me = state.me?.id
        val newcomer = arrived.firstOrNull { it != me } ?: return
        val name = person(newcomer)?.name ?: return

        val allowed = Notifications.shouldPost(
            kind = NotificationKind.inTheBook,
            roomID = room.id,
            prefs = notificationPrefs(room),
            settings = state.settings,
            visibleRoomID = visibleRoomID,
        )
        if (!allowed) return
        Notifications.post(
            context = appContext,
            id = Notifications.id(room.id, NotificationKind.inTheBook),
            kind = NotificationKind.inTheBook,
            line = Copy.notifReading(firstName(name), book),
            to = Destination.Room(roomID = room.id),
        )
    }

    /**
     * Somebody held your face for 700 ms (§4.3).
     *
     * What used to be here was one line — a haptic — and it threw away the
     * only thing the gesture carries. §10.3 is unambiguous that the name *is*
     * the payload ("Ruth"), and a buzz with no name is a phone twitching in a
     * pocket for no stated reason. It also consulted nothing: a person who had
     * turned the switch off in S19 was buzzed anyway, and so was a person
     * asleep inside their own quiet hours.
     *
     * The order below is the build book's, and the middle step is the one
     * worth reading twice. S19 says thinking of you "is the only thing
     * permitted to arrive silently inside [quiet hours], as a haptic on an
     * already-woken device" — so inside them there is no notification at all,
     * and the haptic plays only if the screen is already on. That is the
     * difference between a tap on the shoulder and waking somebody up.
     *
     * Outside quiet hours it posts even when Ribbon is in the foreground,
     * which is the one exception to the rule that a visible room stays quiet.
     * There is no in-app surface that carries a name and nothing else, and
     * §4.3 is explicit that the notification *is* the delivery; §6.3's "a
     * notification, or nothing at all" is about notes.
     */
    private fun thinkingOfYouArrived(fromName: String) {
        val room = currentRoom ?: return
        val prefs = notificationPrefs(room)
        // The switch is off: no notification, and no haptic either. A buzz
        // with no explanation is worse than silence, and the person has said no.
        if (!prefs.thinkingOfYou) return

        if (state.settings.isQuietNow()) {
            val power = appContext.getSystemService(PowerManager::class.java)
            if (power?.isInteractive == true) haptics.tapOnTheShoulder()
            return
        }

        haptics.tapOnTheShoulder()
        Notifications.post(
            context = appContext,
            id = Notifications.id(room.id, NotificationKind.thinkingOfYou),
            kind = NotificationKind.thinkingOfYou,
            line = Copy.notifThinkingOfYou(firstName(fromName)),
            to = Destination.Room(roomID = room.id),
        )
    }

    /**
     * The one thing this model registers with the system, unregistered.
     *
     * A `NetworkCallback` outlives the object that made it unless it is
     * handed back, and a leaked one keeps waking a process that has no
     * screen.
     */
    override fun onCleared() {
        connectivity.stop()
    }

    /**
     * Let go of everything this model holds open.
     *
     * `onCleared` is called by a `ViewModelStore`, and the background worker
     * builds a model outside one — so without this, every fifteen minutes the
     * process gained one more orphaned `ConnectivityManager` callback (A26
     * says in so many words that it "has to be unregistered"), one more live
     * Realtime websocket with its own 25-second heartbeat and reconnect loop,
     * and one more never-cancelled scope. A pull that exists to post a
     * notification must not cost more than the notification.
     */
    suspend fun shutDown() {
        connectivity.stop()
        closeRoomChannel()
        viewModelScope.cancel()
    }

    // MARK: - The room's live line (§4.2)

    /**
     * A pull the socket asked for, coalesced. Several people leaving notes at
     * once is one catch-up, not five; and the short wait lets a burst (a
     * join, then the joiner's profile, then their membership) land as one
     * arrival rather than three half-built ones.
     */
    private var catchUpJob: Job? = null

    private fun roomChangedRemotely(roomID: Uuid) {
        if (roomID != currentRoom?.id) return
        catchUpJob?.cancel()
        catchUpJob = viewModelScope.launch {
            delay(600)
            catchUpJob = null
            refreshFromRemote()
        }
    }

    /**
     * Open — or move — the room's channel. Safe to call whenever the room,
     * the person or the account changes; it is a no-op when the channel is
     * already where it should be.
     */
    suspend fun openRoomChannel() {
        val room = currentRoom
        val me = state.me
        if (room == null || me == null || !isSignedIn) {
            presence.disconnect()
            return
        }
        presence.connect(room.id, me)
    }

    /**
     * The app going away. The socket goes with it: a phone in a pocket is not
     * present, and saying otherwise is the one lie presence must never tell
     * (§4.2).
     */
    suspend fun closeRoomChannel() {
        catchUpJob?.cancel()
        catchUpJob = null
        presence.disconnect()
    }

    /**
     * Something this device changed that the room renders from. The other
     * phones hear about it now rather than at their next foreground.
     *
     * Every remote write the *room* renders from goes through here, so a new
     * kind of content cannot quietly forget to be live. Positions and
     * note-founds deliberately do not: a position is already carried by
     * presence, several times a minute, and who found a note is the one thing
     * the room is never told (§6.3).
     */
    private fun pushing(work: suspend () -> Unit) {
        viewModelScope.launch {
            work()
            presence.announceChange()
        }
    }

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

    fun room(id: Uuid): Room? = state.rooms.firstOrNull { it.id == id }

    fun room(reading: Reading): Room? = room(reading.roomID)

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
        presentPeople = emptyList()
        persist()
        viewModelScope.launch { openRoomChannel() }
    }

    /**
     * Pushes everything the backend needs for an invite link to resolve:
     * the creator's profile, the room, their membership, and the invite itself.
     */
    suspend fun pushInvite(invite: Invite, room: Room) {
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
        remote.push(profile = me, portraitData = portraitData)
        remote.push(room = room)
        val membership = myMembership(room)
        if (membership != null) {
            remote.push(membership = membership)
        }
        // Only ever my own. `invites_update` is the creator's, so pushing a
        // link somebody else minted is a 403 — and pointless besides: it is
        // here because the pull brought it back from the row it already has.
        if (invite.createdBy != me.id) return
        pendingInvitePushes.add(invite.id)
        remote.push(invite = invite)
        pendingInvitePushes.remove(invite.id)
    }

    /**
     * Invites minted here whose push hasn't landed — merge() must not let a
     * pull that raced them delete a link that is already in somebody's
     * message thread. The same shape as `pendingRenamePushes`, for the same
     * reason.
     */
    private val pendingInvitePushes: MutableSet<Uuid> = mutableSetOf()

    fun createInvite(room: Room): Invite {
        val me = state.me ?: error("invite before person")
        // Reuse a live invite rather than minting link after link — and
        // since the pull now brings a room's invites back, "live" includes
        // the one another member already sent out. A room has one link, not
        // one per phone. Mine first, so the common case never needs anyone
        // else's row.
        val invite: Invite
        val live = state.invites.filter {
            it.roomID == room.id && it.expiresAt > Clock.System.now()
        }
        val existing = live.firstOrNull { it.createdBy == me.id } ?: live.firstOrNull()
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
        //
        // Written down before it is attempted, and cleared only when it
        // lands. The failure used to be logged and forgotten: the invite sat
        // in local state until the next successful pull, at which point the
        // prune deleted it *because* the backend did not have it, and the
        // link already sitting in somebody's message thread was dead for
        // good.
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            state = state.copy(invitesNotYetPushed = state.invitesNotYetPushed + invite.id)
            persist()
            viewModelScope.launch { pushInviteIfNeeded(invite, room) }
        }
        return invite
    }

    /**
     * Register a link with the backend, and remember whether it landed.
     *
     * Not `pushInvite` itself, which is also called by `pushLocalGraph` at
     * sign-in with a whole graph's worth of invites behind it.
     */
    private suspend fun pushInviteIfNeeded(invite: Invite, room: Room) {
        val landed = runCatching { pushInvite(invite, room) }
        if (landed.isSuccess) {
            state = state.copy(invitesNotYetPushed = state.invitesNotYetPushed - invite.id)
            persist()
        } else {
            Log.e("AppModel", "pushInvite failed for room ${room.id}", landed.exceptionOrNull())
        }
    }

    /**
     * The link left this phone (S15).
     *
     * Called where the share intent is fired, and not where the invite is
     * minted. A chooser the person then backs out of still counts: Android
     * only reports the chosen component through an `EXTRA_CHOSEN_COMPONENT`
     * PendingIntent, and that machinery buys less honesty than it costs —
     * somebody who opened the share sheet and changed their mind is far
     * closer to "the invite is out" than somebody who has never seen it.
     */
    fun inviteWasHandedOut(invite: Invite) {
        if (invite.id in state.invitesHandedOut) return
        state = state.copy(invitesHandedOut = state.invitesHandedOut + invite.id)
        persist()
    }

    fun isFull(room: Room): Boolean = members(room).size >= Room.capacity

    /** Is a link to this room live — actually handed out, and not expired? */
    /**
     * Is there a live link out for this room?
     *
     * "Out", not "minted". Both the onboarding step and the invite sheet mint
     * one the moment they appear, so this used to answer true for anybody who
     * had merely *seen* either — and the room then told a person who had
     * asked nobody that "The invite is still out", with a control to send it
     * again, on the first morning of their room.
     *
     * Somebody else's invite counts without asking: their `invitesHandedOut`
     * is not ours to see, and an invite that reached this phone through a
     * pull is the room's live link by definition.
     */
    fun hasLiveInvite(room: Room, now: Instant = Clock.System.now()): Boolean {
        val me = state.me?.id
        return state.invites.any { invite ->
            invite.roomID == room.id &&
                invite.expiresAt > now &&
                (invite.createdBy != me || invite.id in state.invitesHandedOut)
        }
    }

    /**
     * Is somebody still expected in this room?
     *
     * A room of one always is — its first invite is the whole point of it —
     * and a larger room only when a link is actually live. The room's own
     * open seat reads this: a seat drawn for nobody is an empty state
     * dressed as an object, and a couple who have no intention of being
     * three should not be shown a chair nobody was asked to sit in.
     */
    fun somebodyIsExpected(room: Room, now: Instant = Clock.System.now()): Boolean {
        if (room.isPaused || isFull(room)) return false
        if (members(room).size <= 1) return true
        return hasLiveInvite(room, now)
    }

    /**
     * Rooms whose rename hasn't landed remotely — merge() must not let a
     * stale pull revert an edit that was never pushed. In-memory only: a
     * relaunch before the push lands re-exposes the edge, accepted for a
     * rename.
     */
    private val pendingRenamePushes: MutableSet<Uuid> = mutableSetOf()

    /**
     * Notes taken back whose delete hasn't landed, and notes edited whose
     * push hasn't.
     *
     * The same shape as [pendingRenamePushes], and they exist for a defect
     * that was worse than the rename's. `takeBack` and `editWrittenNote` both
     * wrapped their remote call in `runCatching` and forgot the outcome, and
     * nothing anywhere retried either — the one thing `refreshFromRemote`
     * replayed was a rename. So a take-back made offline was applied here and
     * never sent, the row stayed in Postgres, and the next successful pull
     * put the note back on the phone of the person who had just taken it
     * back. An edit made offline was worse than lost: the merge takes
     * `body = row.body ?: local`, so the server's old words silently
     * overwrote the new ones with nothing on screen to say so.
     *
     * Both are consulted by the merge as well as replayed by the refresh: a
     * pull that races a pending delete must not re-add the row, and a pull
     * that races a pending edit must not take the server's body.
     *
     * In-memory, like the rename queue: a relaunch before the push lands
     * re-exposes the edge. Accepted for the same reason — the alternative is
     * a durable outbox, which is the sync engine's job and not this pass's.
     */
    private val pendingNoteDeletes: MutableSet<Uuid> = mutableSetOf()

    /**
     * What a queued delete needs to know besides the id: the reading the
     * recording lives under, and whether there is one.
     *
     * The note is gone from state the moment it is taken back, so by the time
     * a replay runs there is nothing left to read its kind off — and a replay
     * that dropped the recording would leave the one thing S04 most means by
     * "no tombstone" sitting in the bucket.
     */
    private val pendingNoteDeleteShapes: MutableMap<Uuid, Pair<Uuid, Boolean>> = mutableMapOf()
    private val pendingNotePushes: MutableSet<Uuid> = mutableSetOf()

    /** The same, for a highlight removed while the request could not land. */
    private val pendingHighlightDeletes: MutableSet<Uuid> = mutableSetOf()

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
            pushing {
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
        // Held before the local filter, because they are what has to be
        // deleted *remotely* and in a moment they will not be in state to
        // find. "Take them back" used to be a local filter and nothing else:
        // the rows stayed in Postgres, the recordings stayed in the bucket,
        // and every other member's phone kept its copy — so the one answer
        // §6.8 offers to somebody who wants their words back did nothing
        // except hide them from the person who asked.
        val mine = if (keepNotesBehind) {
            emptyList()
        } else {
            state.notes.filter { readingIDs.contains(it.readingID) && it.authorID == me.id }
        }
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
            // Queued the same way a single take-back is, so a delete that
            // cannot land right now is replayed on the next refresh rather
            // than forgotten.
            mine.forEach {
                pendingNoteDeletes.add(it.id)
                pendingNoteDeleteShapes[it.id] = it.readingID to (it.kind == NoteKind.voice)
            }
            viewModelScope.launch {
                // The notes go before the membership. `notes_delete` keys on
                // the author alone, so it does not need the membership — but
                // the room's own policies do, and leaving first would take
                // away the standing to do anything else here.
                for (note in mine) {
                    val gone = runCatching {
                        remote.deleteNote(
                            id = note.id,
                            readingID = note.readingID,
                            voice = note.kind == NoteKind.voice,
                        )
                    }
                    if (gone.isSuccess) {
                        pendingNoteDeletes.remove(note.id)
                        pendingNoteDeleteShapes.remove(note.id)
                    }
                }
                // The nudge goes next, and it has to: once the membership
                // row is gone the channel's own policy refuses this device,
                // and the room would hear nothing at all. The others pull a
                // beat later, by which time the delete has landed — and their
                // next foreground is the backstop if it hasn't.
                presence.announceChange()
                runCatching { remote.deleteMembership(roomID = roomID, personID = personID) }
                // The room this device is looking at has changed; the line
                // follows it.
                openRoomChannel()
            }
        } else {
            viewModelScope.launch { openRoomChannel() }
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
            pushing {
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
        pushing { runCatching { remote.push(reading = snapshot) } }
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
            pushing {
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
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            viewModelScope.launch {
                runCatching { remote.push(position = position) }
            }
        }
    }

    // MARK: - The ribbon (deviation A30)

    /** Where the room left the ribbon in this book, if anybody has. */
    fun ribbon(reading: Reading): Ribbon? =
        state.ribbons.firstOrNull { it.readingID == reading.id }

    /**
     * Leave the ribbon here.
     *
     * Called when the book is set down, which is the whole of the gesture:
     * you read, you close the book, the ribbon is where you stopped. There is
     * no separate control for it because there is no separate act — that is
     * what a ribbon in a physical Bible is, and inventing a "mark this verse"
     * button would turn a consequence of reading into a chore.
     *
     * One row per reading, replaced in place. Anybody in the room may move
     * it; nothing about moving it moves anybody else (§03 is intact — see
     * [Ribbon]).
     *
     * A reading that is finished keeps the ribbon it had. An ember is a
     * record of a book that was read, and moving its ribbon afterwards would
     * be editing the past.
     */
    fun leaveTheRibbon(reading: Reading, address: VerseAddress) {
        val me = state.me ?: return
        if (reading.isFinished) return
        val ribbon = Ribbon(
            readingID = reading.id,
            personID = me.id,
            chapter = address.chapter,
            verse = address.verse,
            placedAt = Clock.System.now(),
        )
        val existing = state.ribbons.firstOrNull { it.readingID == reading.id }
        // Nothing to write and nothing to say when it has not moved: a
        // ribbon put back exactly where it already was is not an event, and
        // writing it would move `placedAt` and make the room's one quiet line
        // reappear for no reason.
        if (existing != null &&
            existing.chapter == ribbon.chapter &&
            existing.verse == ribbon.verse &&
            existing.personID == ribbon.personID
        ) {
            return
        }
        state = state.copy(
            ribbons = state.ribbons.filterNot { it.readingID == reading.id } + ribbon,
        )
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            // Through `pushing`, not a bare launch. The ribbon is something
            // the *room* renders — it is a line on the room screen — and the
            // whole point of a shared ribbon is that the other person finds
            // it there. Pushed without the nudge it would land in the table
            // and sit unseen until their next foreground, which for the one
            // object in the app that exists to be noticed is the same as not
            // syncing at all.
            pushing { runCatching { remote.push(ribbon = ribbon) } }
        }
    }

    /**
     * The ribbon, when it is worth offering — which is not always.
     *
     * Silent when there is no ribbon, when it is exactly where you already
     * are (you do not need telling where you are), and when the reading is
     * finished. Everything else is a place somebody left, and the room says
     * so once, quietly, as a line you may tap.
     */
    fun ribbonWorthOffering(reading: Reading): Ribbon? {
        if (reading.isFinished) return null
        val ribbon = ribbon(reading) ?: return null
        val mine = myPosition(reading)
        if (ribbon.chapter == mine.chapter && ribbon.verse == mine.verse) return null
        return ribbon
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
            pushing { runCatching { remote.push(quietDay = day) } }
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
            kind = NoteKind.written, body = body, createdAt = Clock.System.now(),
            isPending = true)
        state = state.copy(notes = state.notes + note)
        recordReadingActivity(reading = reading, address = verse)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            // Queued as well as pushed. A note composed offline drew its
            // pending hairline (§4.4) and then waited for a push that was
            // never attempted again — the refresh replayed a rename and
            // nothing else — so it stayed a hairline until the app was
            // restarted, and the person it was left for never got it.
            pendingNotePushes.add(note.id)
            pushing {
                runCatching { remote.push(note = note) }
                    .onSuccess {
                        pendingNotePushes.remove(note.id)
                        markNoteSent(note.id)
                    }
            }
        }
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
            createdAt = Clock.System.now(), isPending = true)
        state = state.copy(notes = state.notes + note)
        recordReadingActivity(reading = reading, address = verse)
        persist()
        val noteID = note.id
        viewModelScope.launch {
            val transcript = Transcriber.transcribe(appContext, audioFile)
            setTranscript(noteID = noteID, transcript = transcript)
        }
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            // The written path was queued and this one was not, so a voice
            // note left on a train drew its pending hairline (§4.4) and then
            // waited for a push that was never attempted again. S25's "note
            // failed to send" row and §6.10's "notes queue with hairline
            // marks" both describe a queue; only half of one existed.
            pendingNotePushes.add(note.id)
            pushing {
                runCatching { remote.push(note = note, audioFile = audioFile) }
                    .onSuccess {
                        pendingNotePushes.remove(note.id)
                        markNoteSent(note.id)
                    }
            }
        }
        return note
    }

    private fun markNoteSent(noteID: Uuid) {
        val index = state.notes.indexOfFirst { it.id == noteID }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        notes[index] = notes[index].copy(isPending = false)
        state = state.copy(notes = notes)
        persist()
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
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            viewModelScope.launch {
                runCatching { remote.push(noteFoundID = note.id, personID = me.id) }
            }
        }
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
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            // Remembered until it lands. Without this a delete that failed —
            // offline, a dropped request — was forgotten on the spot and the
            // next pull put the note back.
            pendingNoteDeletes.add(note.id)
            pendingNoteDeleteShapes[note.id] =
                note.readingID to (note.kind == NoteKind.voice)
            pushing {
                val gone = runCatching {
                    remote.deleteNote(
                        id = note.id,
                        readingID = note.readingID,
                        voice = note.kind == NoteKind.voice,
                    )
                }
                if (gone.isSuccess) {
                    pendingNoteDeletes.remove(note.id)
                    pendingNoteDeleteShapes.remove(note.id)
                }
            }
        }
    }

    fun editWrittenNote(note: Note, body: String) {
        if (note.authorID != state.me?.id) return
        val index = state.notes.indexOfFirst { it.id == note.id }
        if (index < 0) return
        val notes = state.notes.toMutableList()
        // §4.4's pending mark: an edit that has not landed is drawn as a
        // hairline outline in the margin, exactly as a note composed offline
        // is. It never was before — only a *new* note was ever pending — so
        // an edit made on a train looked identical to one the room had.
        notes[index] = notes[index].copy(body = body, isPending = true)
        state = state.copy(notes = notes)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            val updated = notes[index]
            pendingNotePushes.add(updated.id)
            pushing {
                if (runCatching { remote.push(note = updated) }.isSuccess) {
                    pendingNotePushes.remove(updated.id)
                    markNoteSent(updated.id)
                }
            }
        }
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
        val highlight = Highlight(
            readingID = reading.id, authorID = me.id, range = range, ink = ink,
            createdAt = Clock.System.now())
        state = state.copy(highlights = state.highlights + highlight)
        lastUsedInk = ink
        recordReadingActivity(reading = reading, address = range.start)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            pushing {
                runCatching { remote.push(highlight = highlight) }
            }
        }
    }

    /** You cannot remove someone else's mark (S06). */
    fun removeHighlight(highlight: Highlight) {
        if (highlight.authorID != state.me?.id) return
        state = state.copy(highlights = state.highlights.filterNot { it.id == highlight.id })
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            pendingHighlightDeletes.add(highlight.id)
            pushing {
                if (runCatching { remote.deleteHighlight(id = highlight.id) }.isSuccess) {
                    pendingHighlightDeletes.remove(highlight.id)
                }
            }
        }
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

    // MARK: - Reflection Cards (§4.6, S08, S09)

    fun card(reading: Reading, chapter: Int): ReflectionCard {
        val existing = state.cards.firstOrNull { it.readingID == reading.id && it.chapter == chapter }
        if (existing != null) return existing

        val prompt = ReflectionPrompts.prompt(chapter)
        val newCard = ReflectionCard(
            readingID = reading.id,
            chapter = chapter,
            question = prompt,
            state = CardState.sealed
        )
        state = state.copy(cards = state.cards + newCard)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            pushing {
                runCatching { remote.push(card = newCard) }
            }
        }
        return newCard
    }

    fun answerCard(card: ReflectionCard, answer: String, room: Room) {
        val me = state.me ?: return
        val index = state.cards.indexOfFirst { it.id == card.id }
        if (index < 0) return

        val cards = state.cards.toMutableList()
        val newAnswers = cards[index].answers + (me.id to answer)
        val roomMembers = members(room)
        val allAnswered = roomMembers.isNotEmpty() && roomMembers.all { newAnswers.containsKey(it.personID) }
        val newState = if (allAnswered) CardState.open else cards[index].state
        val openedAt = if (allAnswered && cards[index].openedAt == null) Clock.System.now() else cards[index].openedAt

        cards[index] = cards[index].copy(
            answers = newAnswers,
            state = newState,
            openedAt = openedAt
        )
        val updatedCard = cards[index]
        state = state.copy(cards = cards)
        persist()

        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            pushing {
                runCatching {
                    remote.push(
                        RemoteSync.CardAnswerRow(
                            cardId = updatedCard.id,
                            personId = me.id,
                            answer = answer,
                            createdAt = Clock.System.now()
                        )
                    )
                    remote.push(card = updatedCard)
                }
            }
        }
    }

    fun setDownCard(card: ReflectionCard) {
        val index = state.cards.indexOfFirst { it.id == card.id }
        if (index < 0) return
        val cards = state.cards.toMutableList()
        cards[index] = cards[index].copy(state = CardState.setDown)
        val updatedCard = cards[index]
        state = state.copy(cards = cards)
        persist()
        val remote = this.remote
        if (remote != null && remote.isSignedIn) {
            pushing {
                runCatching { remote.push(card = updatedCard) }
            }
        }
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
        pushing {
            runCatching { remote.push(profile = me, portraitData = portraitData) }
        }
    }

    fun markMarginHintSeen() {
        state = state.copy(hasSeenMarginHint = true)
        persist()
    }

    /**
     * Has the fire ever been pulled on this phone?
     *
     * The room's hearth offers its gesture until it has, and then never
     * again (§6.1).
     */
    val hasPulledTheFire: Boolean get() = state.hasPulledTheFire

    /** It has now. Called only from the drag itself, never from the tap. */
    fun markFirePulled() {
        if (state.hasPulledTheFire) return
        state = state.copy(hasPulledTheFire = true)
        persist()
    }

    /**
     * Whether now is the moment to ask about notifications (§6.1).
     *
     * "Notifications: after the first note is left or found — never at
     * launch. In context: *Tell you when Ruth leaves a note?*" Four things
     * have to be true and each is in that sentence:
     *
     *  - a note has just been left or found, which is the caller's business;
     *  - this device has never been asked, because the app asks once and a
     *    question that comes back is worse than no question;
     *  - Android has not already granted it, so we never raise a dialog that
     *    would be answered before it was drawn;
     *  - and there is somebody else in the room. "Tell you when Ruth leaves
     *    a note?" has no name to put in it, and nothing to promise, in a room
     *    of one — S17 forbids a notification pre-prompt and asking a person
     *    reading alone is one in everything but timing.
     */
    fun shouldAskAboutNotifications(room: Room): Boolean =
        !state.hasAskedAboutNotifications &&
            !Notifications.allowed(appContext) &&
            members(room).size > 1

    /**
     * Whoever it would be about — the other person in a room of two, and the
     * one who has most recently left something in a larger room. The ask
     * names a person because §6.1's copy does, and a name is the whole
     * difference between this question and a pre-prompt.
     */
    fun whoTheAskIsAbout(room: Room): String? {
        val me = state.me?.id
        val others = members(room).map { it.personID }.filter { it != me }
        if (others.isEmpty()) return null
        val readingIDs = state.readings.filter { it.roomID == room.id }.map { it.id }.toSet()
        val mostRecent = state.notes
            .filter { readingIDs.contains(it.readingID) && others.contains(it.authorID) }
            .maxByOrNull { it.createdAt }
            ?.authorID
        return person(mostRecent ?: others.first())?.name
    }

    /** Asked, whatever the answer was. Never asked again (§6.1). */
    fun markAskedAboutNotifications() {
        if (state.hasAskedAboutNotifications) return
        state = state.copy(hasAskedAboutNotifications = true)
        persist()
    }

    /**
     * Account deletion (§6.8). The notes question is asked once, at
     * deletion, and the answer travels with the remote delete when sync
     * exists; locally both paths clear this device.
     */
    fun deleteAccount(keepNotesBehind: Boolean) {
        // §6.8's question, finally asked of something.
        //
        // What used to be here read `keepNotesBehind` nowhere at all — its
        // comment said "notes aren't remote yet, so the keep/take answer is
        // local-only", which stopped being true when deviation 10 put notes
        // on the wire. Worse, the answer could not have been honoured either
        // way: `deleteAccountData` deleted the profiles row, and both
        // `notes.author_id` and `highlights.author_id` cascade from it, so
        // *both* answers erased every note and every highlight the person had
        // ever left. §6.8 is explicit that highlights "stay, always", and
        // S11 needs a departed member's notes to render normally with their
        // portrait.
        //
        // So the profile is blanked rather than deleted (see
        // `RemoteSync.forgetProfile`), which leaves the rows that hang off it
        // standing, and the answer decides what happens to the notes:
        //
        //  - leave them behind — the default, because they were left for the
        //    other person — and nothing authored is touched;
        //  - take them back, and the notes go, recordings and all.
        //
        // Highlights are never deleted on either path. A highlight is a mark
        // on a shared page rather than a possession, which is the same
        // reason leaving a room does not take them.
        val me = state.me
        val remote = this.remote
        val mine = if (keepNotesBehind || me == null) {
            emptyList()
        } else {
            state.notes.filter { it.authorID == me.id }
        }
        if (remote != null && remote.isSignedIn) {
            viewModelScope.launch {
                for (note in mine) {
                    runCatching {
                        remote.deleteNote(
                            id = note.id,
                            readingID = note.readingID,
                            voice = note.kind == NoteKind.voice,
                        )
                    }
                }
                remote.forgetProfile(neutralName = Copy.SOMEONE)
                remote.signOut()
            }
        }
        state = AppState()
        portraits.clear()
        persist()
        // Nothing left to watch for, and on this path watching on would be
        // wrong rather than merely pointless.
        RoomWatch.stop(appContext)
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

    /**
     * Whether a passkey is worth offering here: there is a backend to run
     * the ceremony against. Where there is not, the control is absent rather
     * than dead (§6.1).
     *
     * Unlike iOS there is no OS floor to test — CredentialManager is a
     * library, and it is present from this build's minSdk up.
     */
    val passkeysAvailable: Boolean get() = remote != null
    val auth0Available: Boolean get() = remote != null && app.readribbon.data.Auth0Config.isConfigured

    /**
     * Register a passkey for the account that is already signed in.
     *
     * @param context an Activity context — the system sheet needs a window.
     */
    suspend fun registerPasskey(context: Context) {
        val remote = this.remote ?: return
        if (!remote.isSignedIn) return
        remote.registerPasskey(context)
    }

    /**
     * Sign in with a passkey. The whole of [verifySignInCode] after the code,
     * because after the session it is the same thread: adopt the account,
     * restore the person if this device has none, pull, push.
     */
    suspend fun signInWithPasskey(context: Context) {
        val remote = this.remote ?: throw SupabaseError.NotSignedIn
        val uid = remote.signInWithPasskey(context)
        if (state.me == null) restorePerson(uid)
        adoptRemoteIdentity(uid)
        reconcileOwnProfile()
        refreshFromRemote()
        pushLocalGraph()
    }

    /**
     * Sign in using Auth0 Universal Login. Adopts the deterministic user UUID,
     * restores/creates the profile, and starts real-time sync.
     */
    suspend fun signInWithAuth0(activity: Activity) {
        val remote = this.remote ?: throw SupabaseError.NotSignedIn
        val auth0User = Auth0Service.login(activity)
        val uid = remote.signInWithAuth0(
            idToken = auth0User.idToken,
            userUuid = auth0User.userUuid,
            email = auth0User.email,
            refreshToken = auth0User.refreshToken,
        )
        if (state.me == null) restorePerson(uid)
        adoptRemoteIdentity(uid)
        reconcileOwnProfile()
        refreshFromRemote()
        pushLocalGraph()
    }

    suspend fun signOutRemote() {
        remote?.signOut()
        // Nobody to pull for any more.
        RoomWatch.stop(appContext)
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
            // The ribbon carries who left it, and the room says so out loud
            // — a ribbon whose author was a local id nobody has any more
            // would read as left by nobody.
            ribbons = state.ribbons.map {
                if (it.personID == old) it.copy(personID = uid) else it
            },
            quietDays = state.quietDays.map {
                if (it.personID == old) it.copy(personID = uid) else it
            },
            invites = state.invites.map {
                if (it.createdBy == old) it.copy(createdBy = uid) else it
            },
            cards = state.cards.map { card ->
                if (card.answers.containsKey(old)) {
                    val ans = card.answers[old]!!
                    card.copy(answers = card.answers - old + (uid to ans))
                } else card
            })
        persist()
    }

    /**
     * Everything this device can honestly claim on the backend: my
     * profile and portrait, my rooms and membership, live invites, the
     * readings and their fires, my quiet days, notes, highlights, positions, and reflection cards.
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
                if (invite.roomID != room.id || invite.createdBy != me.id) continue
                if (invite.expiresAt <= Clock.System.now()) continue
                runCatching { remote.push(invite = invite) }
            }
            for (reading in state.readings) {
                if (reading.roomID != room.id) continue
                runCatching { remote.push(reading = reading.snapshot()) }
                for (note in state.notes) {
                    if (note.readingID != reading.id || note.authorID != me.id) continue
                    val audioFile = note.audioPath?.let { store.audioFile(it) }
                    runCatching { remote.push(note = note, audioFile = audioFile) }
                }
                for (hl in state.highlights) {
                    if (hl.readingID != reading.id || hl.authorID != me.id) continue
                    runCatching { remote.push(highlight = hl) }
                }
                val pos = state.positions.firstOrNull { it.readingID == reading.id && it.personID == me.id }
                if (pos != null) {
                    runCatching { remote.push(position = pos) }
                }
                for (card in state.cards) {
                    if (card.readingID != reading.id) continue
                    runCatching { remote.push(card = card) }
                    val myAns = card.answers[me.id]
                    if (myAns != null) {
                        runCatching {
                            remote.push(
                                RemoteSync.CardAnswerRow(
                                    cardId = card.id,
                                    personId = me.id,
                                    answer = myAns,
                                    createdAt = card.openedAt ?: Clock.System.now()
                                )
                            )
                        }
                    }
                }
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
    suspend fun refreshFromRemote(): Arrivals {
        val remote = this.remote ?: return Arrivals.none
        if (!remote.isSignedIn) return Arrivals.none
        // Everything this device meant to say goes before the pull that would
        // otherwise contradict it. A rename, a take-back, an edit, a removed
        // highlight — the rename was the only one of the four that was ever
        // replayed, and the other three were the ones that lost data.
        for (roomID in pendingRenamePushes.toList()) {
            val room = state.rooms.firstOrNull { it.id == roomID }
            if (room != null && runCatching { remote.push(room = room) }.isSuccess) {
                pendingRenamePushes.remove(roomID)
            }
        }
        for (noteID in pendingNoteDeletes.toList()) {
            val shape = pendingNoteDeleteShapes[noteID]
            val gone = runCatching {
                remote.deleteNote(
                    id = noteID,
                    readingID = shape?.first,
                    voice = shape?.second ?: false,
                )
            }
            if (gone.isSuccess) {
                pendingNoteDeletes.remove(noteID)
                pendingNoteDeleteShapes.remove(noteID)
            }
        }
        for (noteID in pendingNotePushes.toList()) {
            val note = state.notes.firstOrNull { it.id == noteID } ?: run {
                // Taken back after the edit: there is nothing to push.
                pendingNotePushes.remove(noteID)
                return@run null
            }
            // With its recording. `RemoteSync.push` only uploads audio when
            // it is handed a file, so a replay without one would have sent a
            // voice note's row and never its voice — a waveform on the other
            // person's phone with nothing behind it.
            val audio = note?.audioPath?.let(store::audioFile)
            val sent = note != null &&
                runCatching { remote.push(note = note, audioFile = audio) }.isSuccess
            if (sent) {
                pendingNotePushes.remove(noteID)
                markNoteSent(noteID)
            }
        }
        for (highlightID in pendingHighlightDeletes.toList()) {
            if (runCatching { remote.deleteHighlight(id = highlightID) }.isSuccess) {
                pendingHighlightDeletes.remove(highlightID)
            }
        }
        // A link that was handed out before the backend could be told about
        // it starts working by itself the moment this phone has a network.
        // No banner and no line on the sheet: §6.10 is explicit that the app
        // working is not news, and self-healing is the honest answer.
        for (inviteID in state.invitesNotYetPushed.toList()) {
            val invite = state.invites.firstOrNull { it.id == inviteID }
            val room = invite?.let { i -> state.rooms.firstOrNull { it.id == i.roomID } }
            if (invite == null || room == null) {
                // The room or the invite has gone; there is nothing to register.
                state = state.copy(invitesNotYetPushed = state.invitesNotYetPushed - inviteID)
                persist()
                continue
            }
            pushInviteIfNeeded(invite, room)
        }
        val graph = runCatching { remote.pullRooms() }.getOrNull() ?: return Arrivals.none
        val landed = merge(graph)
        announce(landed)
        return landed
    }

    /**
     * Say what arrived, out loud (S19, §10.3).
     *
     * Called from the one place that can tell an arrival from a row that was
     * already there. Everything it posts goes through
     * [Notifications.shouldPost] first, which is what makes S19's "per room,
     * not global" true even though the channels are per kind.
     *
     * The order of the three is the order the build book puts them in, and
     * the one thing worth saying about it: a note names its verse when it is
     * the only one from that person in that room, and names only the person
     * when several landed (§10.3's two strings). Nothing here ever says how
     * many, in prose or through a group summary — Android writes "+2 more"
     * into a summary of its own accord, which is a count attached to reading
     * posted by the platform, in the last place anybody would look for it.
     */
    private fun announce(arrivals: Arrivals) {
        if (arrivals.isEmpty) return
        if (!Notifications.allowed(appContext)) return
        val settings = state.settings

        fun gate(kind: NotificationKind, roomID: Uuid): Boolean {
            val room = state.rooms.firstOrNull { it.id == roomID } ?: return false
            return Notifications.shouldPost(
                kind = kind,
                roomID = roomID,
                prefs = notificationPrefs(room),
                settings = settings,
                visibleRoomID = visibleRoomID,
            )
        }

        arrivals.notes.forEach { (roomID, notes) ->
            if (!gate(NotificationKind.notesLeft, roomID)) return@forEach
            // By author, because §10.3's collapsed string names a person. Two
            // people who both left something are two posts, not one summary.
            notes.groupBy { it.authorID }.forEach { (authorID, theirs) ->
                val name = person(authorID)?.name ?: return@forEach
                val newest = theirs.maxByOrNull { it.createdAt } ?: return@forEach
                Notifications.post(
                    context = appContext,
                    // Per room *and* author, so a second note from the same
                    // person replaces the first — which is what makes the
                    // collapsed string a replacement rather than a pile.
                    id = Notifications.id(roomID, NotificationKind.notesLeft) + authorID.hashCode(),
                    kind = NotificationKind.notesLeft,
                    line = notesLeftLine(
                        name = name,
                        verse = newest.verse,
                        several = theirs.size > 1,
                    ),
                    to = Destination.Verse(
                        roomID = roomID,
                        readingID = newest.readingID,
                        verse = newest.verse,
                    ),
                )
            }
        }

        arrivals.cardsOpened.forEach { (roomID, cards) ->
            if (!gate(NotificationKind.cardsOpen, roomID)) return@forEach
            // "The room gets one notification: The cards are open" (§4.6).
            // One, however many turned over — the plural is in the noun.
            val card = cards.firstOrNull() ?: return@forEach
            Notifications.post(
                context = appContext,
                id = Notifications.id(roomID, NotificationKind.cardsOpen),
                kind = NotificationKind.cardsOpen,
                line = Copy.NOTIF_CARDS_OPEN,
                to = Destination.Cards(
                    roomID = roomID,
                    readingID = card.readingID,
                    chapter = card.chapter,
                ),
            )
        }

        arrivals.finished.forEach { (roomID, readings) ->
            if (!gate(NotificationKind.bookFinished, roomID)) return@forEach
            val reading = readings.lastOrNull() ?: return@forEach
            val book = Bible.book(reading.bookID)?.name ?: return@forEach
            Notifications.post(
                context = appContext,
                id = Notifications.id(roomID, NotificationKind.bookFinished),
                kind = NotificationKind.bookFinished,
                line = Copy.notifFinished(book),
                to = Destination.Room(roomID = roomID),
            )
        }
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
        // The membership the function minted carries no ink and this device's
        // profile may be newer than the row the room can see, so both go up
        // before the pull that renders them.
        val me = state.me
        if (me != null) {
            val portraitData = me.portraitPath?.let { path ->
                runCatching {
                    withContext(Dispatchers.IO) { store.portraitFile(path).readBytes() }
                }.getOrNull()
            }
            runCatching { remote.push(profile = me, portraitData = portraitData) }
        }
        refreshFromRemote()
        restoreInk(roomID)
        // Arriving is the news the room most wants: whoever invited you sees
        // you appear without putting their phone down and picking it up.
        presence.announceChange()
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

    /**
     * What arrived in a merge that had not been seen before (S19, §10.3).
     *
     * `merge` used to publish one `next` value and say nothing about what was
     * new in it, so a note that landed and a note that had been sitting in
     * the database for a month were indistinguishable by the time anything
     * downstream could look. There was, literally, no event to post a
     * notification from — which is most of why there were no notifications.
     *
     * The diff is taken at the one moment both values are in hand, which is
     * the seam `merge` already had: it builds a whole state locally and
     * publishes it once.
     */
    data class Arrivals(
        /** Notes left for me, by the room they landed in. */
        val notes: Map<Uuid, List<Note>> = emptyMap(),
        /** Readings whose cards turned over, by room. */
        val cardsOpened: Map<Uuid, List<ReflectionCard>> = emptyMap(),
        /** Books this room finished, by room. */
        val finished: Map<Uuid, List<Reading>> = emptyMap(),
    ) {
        val isEmpty: Boolean
            get() = notes.isEmpty() && cardsOpened.isEmpty() && finished.isEmpty()

        companion object {
            val none = Arrivals()
        }
    }

    /**
     * What is new in [next] that was not in [before], and is newer than the
     * watermark.
     *
     * The watermark — `AppState.notifiedThrough` — is the whole of §6.10's
     * protection, and it is worth being explicit about what it prevents: a
     * first sync on a new device restores every room a person is in, which
     * for a couple a year into this is several hundred notes. Without a
     * watermark that is several hundred notifications, in one breath, the
     * first time somebody signs in on a new phone. So on the very first merge
     * the watermark is null, it is set to the newest row seen, and *nothing*
     * is reported. A new phone arrives quiet.
     */
    private fun arrivals(before: AppState, next: AppState): Arrivals {
        val me = next.me?.id ?: return Arrivals.none
        val watermark = before.notifiedThrough ?: return Arrivals.none

        fun roomOf(readingID: Uuid): Uuid? =
            next.readings.firstOrNull { it.id == readingID }?.roomID

        val knownNotes = before.notes.map { it.id }.toSet()
        val notes = next.notes
            .filter { note ->
                note.id !in knownNotes &&
                    note.authorID != me &&
                    !note.foundBy.contains(me) &&
                    note.createdAt > watermark
            }
            .groupBy { roomOf(it.readingID) }
            .mapNotNull { (room, list) -> room?.let { it to list } }
            .toMap()

        val wasOpen = before.cards.filter { it.state == CardState.open }.map { it.id }.toSet()
        val cardsOpened = next.cards
            .filter { card ->
                card.state == CardState.open &&
                    card.id !in wasOpen &&
                    (card.openedAt?.let { it > watermark } ?: false)
            }
            .groupBy { roomOf(it.readingID) }
            .mapNotNull { (room, list) -> room?.let { it to list } }
            .toMap()

        val wasFinished = before.readings.filter { it.finishedAt != null }.map { it.id }.toSet()
        val finished = next.readings
            .filter { reading ->
                reading.finishedAt?.let { it > watermark } == true && reading.id !in wasFinished
            }
            .groupBy { it.roomID }

        return Arrivals(notes = notes, cardsOpened = cardsOpened, finished = finished)
    }

    /**
     * The newest thing this state knows about, whenever that was.
     *
     * Advanced on every merge whether or not anything was posted, so a
     * notification that was suppressed — quiet hours, a switch turned off,
     * the room already on screen — is not re-offered by the next merge.
     */
    private fun newestRow(state: AppState): Instant? = listOfNotNull(
        state.notes.maxOfOrNull { it.createdAt },
        state.cards.mapNotNull { it.openedAt }.maxOrNull(),
        state.readings.mapNotNull { it.finishedAt }.maxOrNull(),
    ).maxOrNull()

    private fun merge(graph: RoomGraph): Arrivals {
        val me = state.me ?: return Arrivals.none

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
                memberships = next.memberships.filterNot { departed.contains(it.roomID) },
                invites = next.invites.filterNot { departed.contains(it.roomID) })
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

        val invites = next.invites.toMutableList()
        for (row in graph.invites) {
            // The link is the room's, not the device's: a live invite the
            // backend already has is the one this phone hands out too, so a
            // room does not accumulate a link per phone (S15).
            val invite = Invite(
                id = row.id, roomID = row.roomId, createdBy = row.createdBy,
                createdAt = row.createdAt, expiresAt = row.expiresAt)
            val i = invites.indexOfFirst { it.id == row.id }
            if (i >= 0) invites[i] = invite else invites.add(invite)
        }
        // An invite the backend no longer has (the room went, or it aged out
        // of a prune) must not go on being offered from here.
        val pulledInvites = graph.invites.map { it.id }.toSet()
        invites.removeAll {
            it.roomID in pulledRooms &&
                it.id !in pulledInvites &&
                it.id !in pendingInvitePushes &&
                // A link this phone minted and has not managed to register
                // yet. The backend cannot see it, and that is exactly why it
                // must not be taken away — it is already in somebody's
                // message thread.
                it.id !in next.invitesNotYetPushed
        }
        next = next.copy(invites = invites)

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

        // Notes
        val notes = next.notes.toMutableList()
        val remote = this.remote
        for (row in graph.notes) {
            val noteKind = NoteKind.entries.firstOrNull { it.name == row.kind } ?: NoteKind.written
            val verse = VerseAddress(bookID = row.bookId, chapter = row.chapter, verse = row.verse)
            val transcriptState = row.transcriptState?.let { raw ->
                TranscriptState.entries.firstOrNull { it.name == raw }
            }
            // A note this device has taken back and not yet managed to
            // delete must not be handed back to it by the pull that raced the
            // delete. Without this the take-back looked like it had worked
            // and the note reappeared a moment later.
            if (pendingNoteDeletes.contains(row.id)) continue
            val i = notes.indexOfFirst { it.id == row.id }
            if (i >= 0) {
                // An edit this device has made and not yet pushed keeps its
                // own words. `body = row.body ?: local` took the server's old
                // body over the new one, silently, which is the one failure
                // on this path that loses something a person wrote.
                val mine = pendingNotePushes.contains(row.id)
                notes[i] = notes[i].copy(
                    body = if (mine) notes[i].body else row.body ?: notes[i].body,
                    waveform = row.waveform ?: notes[i].waveform,
                    transcript = row.transcript ?: notes[i].transcript,
                    transcriptState = transcriptState ?: notes[i].transcriptState,
                    audioPath = row.audioPath ?: notes[i].audioPath,
                    isPending = mine,
                )
            } else {
                val note = Note(
                    id = row.id,
                    readingID = row.readingId,
                    authorID = row.authorId,
                    verse = verse,
                    kind = noteKind,
                    body = row.body,
                    audioPath = row.audioPath,
                    waveform = row.waveform,
                    transcript = row.transcript,
                    transcriptState = transcriptState,
                    createdAt = row.createdAt,
                    foundBy = emptySet(),
                    isPending = false
                )
                notes.add(note)
                if (noteKind == NoteKind.voice && row.audioPath != null && remote != null) {
                    viewModelScope.launch {
                        val file = store.audioFile(row.audioPath)
                        if (!file.exists()) {
                            runCatching { remote.downloadAudio(readingID = row.readingId, noteID = row.id, to = file) }
                        }
                    }
                }
            }
        }

        // Note founds
        for (row in graph.noteFounds) {
            val i = notes.indexOfFirst { it.id == row.noteId }
            if (i >= 0) {
                notes[i] = notes[i].copy(foundBy = notes[i].foundBy + row.personId)
            }
        }
        // **The prune.** Memberships are pruned above ("departures
        // propagate") and invites are pruned below, and notes were not — so a
        // note the backend no longer holds stayed on the phone forever. Both
        // `takeBack` and `removeHighlight` delete the row remotely and nudge
        // the other device to pull immediately; the pull came back without
        // the row, this loop added nothing, removed nothing, and the note the
        // author had taken back sat on the other person's phone permanently.
        // S04 is explicit that a taken-back note vanishes "with no
        // tombstone", and docs/deviations.md:94 already claimed take-backs
        // propagate. They reached the backend and stopped there.
        //
        // Three guards, and each of them is load-bearing:
        //
        //  - `notesComplete`, because the notes select is wrapped in a
        //    `runCatching` that returns an empty list on failure. Pruning
        //    against that would delete every note in the room the first time
        //    one request timed out.
        //  - only readings the pull actually covered, because a graph is
        //    scoped to the rooms this account is in and a reading it never
        //    asked about has nothing to say about its notes.
        //  - never a pending one, which is a note composed offline that the
        //    backend has not been told about yet (§4.4).
        if (graph.notesComplete) {
            val pulledReadings = graph.readings.map { it.id }.toSet()
            val pulledNotes = graph.notes.map { it.id }.toSet()
            val gone = notes.filter { note ->
                note.readingID in pulledReadings &&
                    note.id !in pulledNotes &&
                    !note.isPending &&
                    note.id !in pendingNotePushes
            }
            if (gone.isNotEmpty()) {
                notes.removeAll(gone.toSet())
                // A voice note that has gone takes its recording with it. The
                // author's own device already does this in `takeBack`; this
                // is the same for everybody else's, and without it the room
                // keeps the audio of a note nobody can see.
                val paths = gone.mapNotNull { it.audioPath }
                if (paths.isNotEmpty()) {
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            paths.forEach { runCatching { store.audioFile(it).delete() } }
                        }
                    }
                }
            }
        }
        next = next.copy(notes = notes)

        // Highlights
        val highlights = next.highlights.toMutableList()
        for (row in graph.highlights) {
            if (highlights.none { it.id == row.id }) {
                val range = VerseRange(
                    bookID = row.bookId,
                    chapter = row.chapter,
                    startVerse = row.startVerse,
                    endVerse = row.endVerse
                )
                val ink = Ink.entries.firstOrNull { it.name == row.ink } ?: Ink.ochre
                highlights.add(
                    Highlight(
                        id = row.id,
                        readingID = row.readingId,
                        authorID = row.authorId,
                        range = range,
                        ink = ink,
                        createdAt = row.createdAt
                    )
                )
            }
        }
        // The same prune, for the same reason: S06's "remove if it's yours"
        // removed it from the author's phone and from Postgres and from
        // nowhere else.
        if (graph.highlightsComplete) {
            val pulledReadings = graph.readings.map { it.id }.toSet()
            val pulledHighlights = graph.highlights.map { it.id }.toSet()
            highlights.removeAll { highlight ->
                highlight.readingID in pulledReadings &&
                    highlight.id !in pulledHighlights &&
                    highlight.id !in pendingHighlightDeletes
            }
        }
        next = next.copy(highlights = highlights)

        // Positions
        val positions = next.positions.toMutableList()
        for (row in graph.positions) {
            val i = positions.indexOfFirst { it.readingID == row.readingId && it.personID == row.personId }
            if (i >= 0) {
                if (row.updatedAt > positions[i].updatedAt) {
                    positions[i] = positions[i].copy(
                        chapter = row.chapter,
                        verse = row.verse,
                        updatedAt = row.updatedAt
                    )
                }
            } else {
                positions.add(
                    ReadingPosition(
                        readingID = row.readingId,
                        personID = row.personId,
                        chapter = row.chapter,
                        verse = row.verse,
                        updatedAt = row.updatedAt
                    )
                )
            }
        }
        next = next.copy(positions = positions)

        // The ribbon (A30). One per reading, last placement wins — the same
        // last-write-wins §13 gives every single-author object, except that
        // the "author" here is the room: whoever set the book down last is
        // where the ribbon is.
        val ribbons = next.ribbons.toMutableList()
        for (row in graph.ribbons) {
            val i = ribbons.indexOfFirst { it.readingID == row.readingId }
            val incoming = Ribbon(
                readingID = row.readingId,
                personID = row.personId,
                chapter = row.chapter,
                verse = row.verse,
                placedAt = row.placedAt,
            )
            if (i >= 0) {
                if (row.placedAt > ribbons[i].placedAt) ribbons[i] = incoming
            } else {
                ribbons.add(incoming)
            }
        }
        next = next.copy(ribbons = ribbons)

        // Reflection Cards & Answers
        val cards = next.cards.toMutableList()
        val answersByCard = graph.cardAnswers.groupBy { it.cardId }
        for (row in graph.cards) {
            val remoteAnswers = answersByCard[row.id]?.associate { it.personId to it.answer } ?: emptyMap()
            val remoteState = CardState.entries.firstOrNull { it.name == row.state } ?: CardState.sealed
            val i = cards.indexOfFirst { it.id == row.id }
            if (i >= 0) {
                val mergedAnswers = cards[i].answers + remoteAnswers
                val stateToSet = if (remoteState == CardState.open || remoteState == CardState.setDown) remoteState else cards[i].state
                cards[i] = cards[i].copy(
                    answers = mergedAnswers,
                    state = stateToSet,
                    openedAt = row.openedAt ?: cards[i].openedAt
                )
            } else {
                cards.add(
                    ReflectionCard(
                        id = row.id,
                        readingID = row.readingId,
                        chapter = row.chapter,
                        question = row.question,
                        answers = remoteAnswers,
                        state = remoteState,
                        openedAt = row.openedAt
                    )
                )
            }
        }

        // Check if sealed cards have all answers
        for (i in cards.indices) {
            if (cards[i].state == CardState.sealed) {
                val reading = next.readings.firstOrNull { it.id == cards[i].readingID }
                val room = if (reading != null) next.rooms.firstOrNull { it.id == reading.roomID } else null
                if (room != null) {
                    val roomMembers = next.memberships.filter { it.roomID == room.id }
                    if (roomMembers.isNotEmpty() && roomMembers.all { cards[i].answers.containsKey(it.personID) }) {
                        cards[i] = cards[i].copy(
                            state = CardState.open,
                            openedAt = cards[i].openedAt ?: Clock.System.now()
                        )
                    }
                }
            }
        }
        next = next.copy(cards = cards)

        // The one seam where both states are in hand. Everything downstream
        // that needs to know something *arrived* rather than merely being
        // true reads this.
        val landed = arrivals(before = state, next = next)
        next = next.copy(notifiedThrough = newestRow(next) ?: next.notifiedThrough)

        state = next
        persist()
        return landed
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

    // MARK: - OTA Updates

    /**
     * Checks whether an update is available on GitHub Releases.
     * Silent and non-blocking.
     */
    fun checkForUpdates() {
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        viewModelScope.launch {
            try {
                val info = UpdateService.checkForUpdate(appContext)
                updateState = if (info != null) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.Idle
                }
            } catch (e: Exception) {
                updateState = UpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    /**
     * Triggers installation or download of the available update.
     * If permission is missing, opens settings so the user can allow install from Ribbon.
     */
    fun triggerUpdate(context: Context) {
        val current = updateState
        if (current is UpdateState.ReadyToInstall) {
            UpdateService.installApk(context, current.apkFile)
            return
        }
        val info = (current as? UpdateState.Available)?.info ?: return
        if (!UpdateService.canRequestPackageInstalls(context)) {
            UpdateService.openInstallPermissionSettings(context)
            return
        }
        updateState = UpdateState.Downloading(0f, info)
        viewModelScope.launch {
            try {
                val apkFile = UpdateService.downloadApk(context, info.apkUrl) { progress ->
                    updateState = UpdateState.Downloading(progress, info)
                }
                updateState = UpdateState.ReadyToInstall(info, apkFile)
                UpdateService.installApk(context, apkFile)
            } catch (e: Exception) {
                updateState = UpdateState.Error(e.message ?: "Update download failed")
            }
        }
    }

    fun dismissUpdateError() {
        if (updateState is UpdateState.Error) {
            updateState = UpdateState.Idle
        }
    }

    fun dismissUpdate() {
        updateState = UpdateState.Idle
    }

    companion object {

        /**
         * How long a face is taken on trust before it is asked about again.
         * A room holds six; this is a handful of tiny requests an hour, and
         * the product's own pace says a new face can take a few minutes to
         * arrive.
         */
        private val PORTRAIT_RECHECK = 15.minutes

        /**
         * @param forBackgroundPull skips everything a launch does that a
         *   fifteen-minute pull has no use for: the update check, the room's
         *   websocket, and starting the watcher that is already running. The
         *   caller must `shutDown()` the model it gets back.
         */
        suspend fun load(context: Context, forBackgroundPull: Boolean = false): AppModel {
            val app = context.applicationContext
            val store = LocalStore(app)
            val state = store.load()
            // The backend comes first: the room's live channel authenticates
            // with the account's own token, so it cannot be built before
            // there is an account to ask.
            val remote = if (SupabaseConfig.REMOTE_ENABLED) RemoteSync.restore(app) else null
            val presence: PresenceService = if (remote != null) {
                RoomChannel(accessToken = { remote.realtimeToken() })
            } else {
                LocalPresenceService()
            }
            val model = AppModel(app, state, store, presence)
            model.remote = remote
            if (forBackgroundPull) return model
            model.loadPortraits()
            model.checkForUpdates()
            model.openRoomChannel()
            // Watch for what arrives while the app is closed (S19), but only
            // for somebody there is an account to watch on behalf of: a
            // person who has never signed in has nothing to pull, and waking
            // their phone four times an hour to find that out is a battery
            // cost with no feature behind it.
            if (remote?.isSignedIn == true) RoomWatch.start(app)
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
            val host = from.host?.lowercase()?.removePrefix("www.")
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
