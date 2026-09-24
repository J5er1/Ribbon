@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.ReadingPoint
import app.readribbon.core.ReadingReport
import app.readribbon.core.VerseAddress
import app.readribbon.data.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The room's live line: Supabase Realtime over Phoenix Channels (§4.2, §4.3).
 *
 * The port of `ios/Ribbon/Services/RoomChannel.swift`, wire-for-wire: the two
 * clients join the same topic with the same payload shapes, because a couple
 * on two different platforms is still one room. The wire format is asserted
 * in `RoomChannelWireTest`.
 *
 * One socket, one channel — `realtime:room:<room id>` — and four things
 * travel on it:
 *
 *  * **Presence.** Who is in the book right now, where they are, whether they
 *    have gone still, and who they are following. Announced only while
 *    someone is actually reading: opening the channel says nothing. Every
 *    presence send is rationed by [PresenceBudget], because the server closes
 *    the channel on a client that speaks too often.
 *  * **The reading line.** Where a reader's eyes are, finer than a verse —
 *    sent only while somebody can be seen following them (§4.2).
 *  * **Thinking of you.** The contentless tap (§4.3).
 *  * **A change nudge.** "Something in this room moved" — no content, no
 *    second copy of the truth, just a reason for the other phone to pull now
 *    instead of at its next launch.
 *
 * The channel is **private**: the account's own access token goes up with the
 * join, and the server checks it against `realtime.messages` (see
 * 20260914120000_ribbon_realtime_room_channel.sql). A project whose Realtime
 * Authorization policies are not in place yet refuses every private join, so
 * the first refusal downgrades this connection to a public one and says so.
 *
 * Every field below is touched under [mutex] only: OkHttp answers on its own
 * threads, and the reconnect, the heartbeat and the idle watch each have a
 * coroutine of their own.
 */
class RoomChannel(
    /**
     * The account's access token, asked for fresh on every (re)join — tokens
     * are short-lived and a socket outlives them.
     */
    private val accessToken: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PresenceService {

    private val _events = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 32)
    override val events: Flow<PresenceEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private enum class Phase { CLOSED, JOINING, JOINED }

    /**
     * What this device is announcing about itself. Null is the honest "not in
     * the book" — the channel can be wide open and still say nothing about
     * you.
     */
    private data class Announcement(
        val position: VerseAddress?,
        val scrollFraction: Double,
        val isIdle: Boolean,
        val following: Uuid?,
    )

    /** The last word of where this reader's line is, for a line that reopens. */
    private data class LastReading(
        val book: String,
        val at: ReadingPoint,
        val end: ReadingPoint?,
        val carried: Boolean,
    )

    private var webSocket: WebSocket? = null
    private var roomID: Uuid? = null
    private var person: Person? = null
    private var phase = Phase.CLOSED
    private var announcement: Announcement? = null
    private val presenceStore = mutableMapOf<String, JsonObject>()

    /** Every presence send, rationed — across reconnects, not per socket. */
    private val budget = PresenceBudget()

    /** A send the budget held back, and when it is due. */
    private var pendingJob: Job? = null
    private var pendingDue = 0L

    /**
     * Put down by [suspend] rather than closed: the room, the person and the
     * announcement are all still here, waiting for the same room to be asked
     * for again.
     */
    private var suspended = false

    /** The join after a [suspend]: the reader has just come back to the app. */
    private var returning = false

    /** Where this reader's line last was, while they are in the book. */
    private var lastReading: LastReading? = null

    /**
     * A join has happened since the reading line last went, and the first
     * roster after it has not been looked at yet: if somebody follows, they
     * are owed where the line is.
     */
    private var owesReading = false

    /**
     * Which stream this is, among the `reading` lines one person can send
     * from two devices. Random, made when a room is joined, and never
     * written anywhere.
     */
    private var source = newSource()

    /**
     * Bumped on every open and close; every coroutine checks it before
     * touching state, so a socket that is already gone cannot speak for the
     * one that replaced it.
     */
    private var generation = 0
    private var ref = 0
    private var joinRef: String? = null

    /** Downgraded to false once a server refuses the private join. */
    private var wantsPrivate = true
    private var reconnectAttempt = 0
    private var pendingHeartbeats = 0

    /** A change that happened while the line was down, to send on arrival. */
    private var pendingAnnounce = false

    /** Sender-side collapse for the tap (§4.3). */
    private val lastTap = mutableMapOf<Uuid, Long>()

    /** The last scroll or position update, for the idle threshold. */
    private var lastActivity = System.currentTimeMillis()

    private var heartbeatJob: Job? = null
    private var idleJob: Job? = null
    private var reconnectJob: Job? = null

    private companion object {
        /** ~4 minutes with no movement is "here, but still" (§4.2). */
        const val IDLE_AFTER_MS = 240_000L
        const val HEARTBEAT_MS = 25_000L
        const val TAP_COLLAPSE_MS = 180_000L
    }

    // MARK: - PresenceService

    override suspend fun connect(roomID: Uuid, person: Person) {
        mutex.withLock {
            if (this.roomID == roomID && this.person?.id == person.id) {
                // The same room, asked for again — on every return to the app.
                // It used to be a close and a reopen whenever the line was
                // anything but open, and the close forgot what this device
                // was saying: a reader who came back from a glance at a
                // message was quietly no longer in the book.
                this.person = person
                when {
                    // Put down while the app was away: pick it up again.
                    suspended -> {
                        suspended = false
                        returning = true
                        reconnectAttempt = 0
                        openLocked()
                    }
                    // A new name is news; the budget decides when it goes.
                    phase == Phase.JOINED -> reconcileLocked()
                    // Down, with nothing bringing it back or with a reconnect
                    // still waiting out its backoff — which, for a reader back
                    // from a glance at a message after a blip in the network,
                    // could be half a minute of not being in the book. Asked
                    // for again, it goes now.
                    phase == Phase.CLOSED -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        reconnectAttempt = 0
                        openLocked()
                    }
                    // Joining already.
                    else -> Unit
                }
                return
            }
            closeLocked()
            this.roomID = roomID
            this.person = person
            source = newSource()
            // A fresh room gets a fresh answer to the private question: the
            // migration may well have landed since the last refusal.
            wantsPrivate = true
            reconnectAttempt = 0
            openLocked()
        }
    }

    override suspend fun disconnect() {
        mutex.withLock { closeLocked() }
        _events.tryEmit(PresenceEvent.Roster(emptyList()))
    }

    override suspend fun suspend() {
        mutex.withLock {
            if (roomID == null || suspended) return
            shutLocked()
            suspended = true
        }
        // No empty roster: the room it last knew stays as it was, exactly as
        // it does through a reconnect, and comes back true with the line.
    }

    override suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
        activity: Boolean,
    ) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (activity && !isIdle) lastActivity = now
            // A page carried by a follow is not the reader moving it, so it
            // does not wake a reader who has gone still — and a reader who
            // has not touched the page in four minutes is still, whoever is
            // carrying it (§4.2).
            val still = isIdle || now - lastActivity >= IDLE_AFTER_MS
            announcement = Announcement(position, scrollFraction, still, following)
            if (idleJob == null) startIdleWatchLocked()
            reconcileLocked()
        }
    }

    override suspend fun withdraw() {
        mutex.withLock {
            // Out of the book, the line has nowhere to be.
            lastReading = null
            if (announcement == null) return
            announcement = null
            idleJob?.cancel()
            idleJob = null
            reconcileLocked()
        }
    }

    override suspend fun sendReading(
        book: String,
        at: ReadingPoint,
        end: ReadingPoint?,
        settled: Boolean,
        carried: Boolean,
    ) {
        mutex.withLock {
            lastReading = LastReading(book, at, end, carried)
            sendReadingLocked(settled)
        }
    }

    override suspend fun sendThinkingOfYou(to: Uuid) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastTap[to]
            if (last != null && now - last < TAP_COLLAPSE_MS) return
            lastTap[to] = now
            val me = person ?: return
            val room = roomID ?: return
            if (phase != Phase.JOINED) return
            send(RoomChannelWire.thinkingOfYou(room, me.name, to, nextRefLocked()))
        }
    }

    override suspend fun announceChange() {
        mutex.withLock {
            val room = roomID ?: return
            if (phase != Phase.JOINED) {
                pendingAnnounce = true
                return
            }
            send(RoomChannelWire.roomChanged(room, nextRefLocked()))
        }
    }

    // MARK: - The socket

    private fun topicLocked(): String? = roomID?.let { RoomChannelWire.topic(it) }

    /** Close the line and forget it: see [disconnect]. */
    private fun closeLocked() {
        shutLocked()
        suspended = false
        returning = false
        roomID = null
        person = null
        announcement = null
        lastReading = null
    }

    /**
     * Close the socket and stop everything that runs on it. What this device
     * is saying about itself is left alone — [closeLocked] forgets it, and
     * [suspend] keeps it.
     */
    private fun shutLocked() {
        generation += 1
        heartbeatJob?.cancel(); heartbeatJob = null
        idleJob?.cancel(); idleJob = null
        reconnectJob?.cancel(); reconnectJob = null
        cancelPendingLocked()
        val room = roomID
        if (phase == Phase.JOINED && room != null) {
            send(RoomChannelWire.leave(room, nextRefLocked()))
        }
        webSocket?.close(1000, "Leaving the room")
        webSocket = null
        phase = Phase.CLOSED
        joinRef = null
        owesReading = false
        presenceStore.clear()
        pendingHeartbeats = 0
    }

    private fun openLocked() {
        if (roomID == null) return
        val me = person ?: return
        generation += 1
        val mine = generation

        val base = SupabaseConfig.URL.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://")
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://")
            else -> "wss://$base"
        }
        val url = "$wsBase/realtime/v1/websocket" +
            "?apikey=${SupabaseConfig.PUBLISHABLE_KEY}&vsn=1.0.0"

        phase = Phase.JOINING
        pendingHeartbeats = 0
        webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    scope.launch {
                        mutex.withLock {
                            if (mine != generation) return@withLock
                            sendJoinLocked(me.id, mine)
                            startHeartbeatLocked(mine)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch {
                        mutex.withLock {
                            if (mine != generation) return@withLock
                            pendingHeartbeats = 0
                            // Whatever arrives is somebody else's shape. A
                            // message this build cannot read is dropped, not
                            // thrown: this scope has no handler, and an
                            // exception here would take the app down.
                            runCatching { handleLocked(text) }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch {
                        mutex.withLock {
                            if (mine != generation) return@withLock
                            scheduleReconnectLocked()
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch {
                        mutex.withLock {
                            if (mine != generation) return@withLock
                            scheduleReconnectLocked()
                        }
                    }
                }
            },
        )
    }

    private suspend fun sendJoinLocked(personID: Uuid, mine: Int) {
        val token = accessToken()
        if (mine != generation) return
        val room = roomID ?: return
        val reference = nextRefLocked()
        joinRef = reference
        send(RoomChannelWire.join(room, personID, wantsPrivate, token, reference))
    }

    private fun startHeartbeatLocked(mine: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                mutex.withLock {
                    if (mine != generation) return@launch
                    // Three unanswered beats is a socket that is open on this
                    // side and gone on the other — the exact failure OkHttp
                    // does not always report.
                    if (pendingHeartbeats >= 3) {
                        scheduleReconnectLocked()
                        return@launch
                    }
                    pendingHeartbeats += 1
                    send(RoomChannelWire.heartbeat(nextRefLocked()))
                }
            }
        }
    }

    private fun startIdleWatchLocked() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(30_000)
                mutex.withLock {
                    val current = announcement ?: return@launch
                    val still = System.currentTimeMillis() - lastActivity >= IDLE_AFTER_MS
                    if (still == current.isIdle) return@withLock
                    announcement = current.copy(isIdle = still)
                    reconcileLocked()
                }
            }
        }
    }

    /**
     * Reconnect with a widening gap. The room does not blink while this
     * happens — the roster it last knew stays on screen until the socket says
     * otherwise, because a flapping network is not the same news as somebody
     * leaving.
     */
    private fun scheduleReconnectLocked() {
        if (roomID == null || reconnectJob != null) return
        phase = Phase.CLOSED
        joinRef = null
        cancelPendingLocked()
        webSocket?.cancel()
        webSocket = null
        heartbeatJob?.cancel(); heartbeatJob = null
        val attempt = reconnectAttempt
        reconnectAttempt = min(attempt + 1, 6)
        val backoff = min(2.0.pow(attempt), 30.0) + Random.nextDouble(0.0, 0.75)
        reconnectJob = scope.launch {
            delay((backoff * 1000).toLong())
            mutex.withLock {
                reconnectJob = null
                if (roomID == null) return@withLock
                openLocked()
            }
        }
    }

    // MARK: - Messages out

    private fun nextRefLocked(): String {
        ref += 1
        return ref.toString()
    }

    /**
     * The one road every presence send takes: what this device wants to be
     * saying, against what the room last heard, through the budget. A send
     * the budget holds back becomes one pending look, later, at whatever is
     * true by then.
     *
     * @param joining the join's own re-track, which always goes.
     */
    private fun reconcileLocked(joining: Boolean = false) {
        if (phase != Phase.JOINED) return
        val room = roomID ?: return
        val me = person ?: return
        val current = announcement
        val desired = current?.let { Stance(it.position, it.isIdle, it.following, me.name) }
        when (val verdict = budget.reconcile(desired, joining)) {
            PresenceBudget.Verdict.Quiet -> cancelPendingLocked()
            PresenceBudget.Verdict.Track -> {
                cancelPendingLocked()
                if (current == null) return
                val wrote = send(
                    RoomChannelWire.track(
                        roomID = room,
                        person = me,
                        position = current.position,
                        scrollFraction = current.scrollFraction,
                        isIdle = current.isIdle,
                        following = current.following,
                        ref = nextRefLocked(),
                    ),
                )
                if (wrote) budget.wrote(desired)
            }
            PresenceBudget.Verdict.Untrack -> {
                cancelPendingLocked()
                if (send(RoomChannelWire.untrack(room, nextRefLocked()))) budget.wrote(null)
            }
            is PresenceBudget.Verdict.Later -> schedulePendingLocked(verdict.inMs)
        }
    }

    private fun schedulePendingLocked(inMs: Long) {
        val due = System.currentTimeMillis() + inMs
        // One look is enough; an earlier one is kept.
        if (pendingJob != null && pendingDue <= due) return
        pendingJob?.cancel()
        pendingDue = due
        val mine = generation
        pendingJob = scope.launch {
            delay(inMs)
            mutex.withLock {
                if (mine != generation) return@withLock
                pendingJob = null
                reconcileLocked()
            }
        }
    }

    private fun cancelPendingLocked() {
        pendingJob?.cancel()
        pendingJob = null
    }

    private fun sendReadingLocked(settled: Boolean) {
        if (phase != Phase.JOINED) return
        val room = roomID ?: return
        val me = person ?: return
        val line = lastReading ?: return
        send(
            RoomChannelWire.reading(
                roomID = room,
                personID = me.id,
                source = source,
                book = line.book,
                at = line.at,
                end = line.end,
                settled = settled,
                carried = line.carried,
                ref = nextRefLocked(),
            ),
        )
    }

    /**
     * True only when the frame was handed to an open socket — the one case
     * in which a send can be said to have happened.
     */
    private fun send(message: JsonObject): Boolean =
        webSocket?.send(message.toString()) ?: false

    // MARK: - Messages in

    private fun handleLocked(text: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (root["event"]?.jsonPrimitive?.content) {
            "phx_reply" -> handleReplyLocked(root)

            // The channel itself went away (a token that aged out, a server
            // restart). Only ours matters.
            "phx_error", "phx_close" -> {
                if (root["topic"]?.jsonPrimitive?.content == topicLocked()) {
                    scheduleReconnectLocked()
                }
            }

            "presence_state" -> {
                val payload = root["payload"]?.jsonObject ?: return
                presenceStore.clear()
                for ((key, value) in payload) {
                    firstMeta(value)?.let { presenceStore[key] = it }
                }
                emitRosterLocked()
                // Back in the room with somebody following: they hear where
                // the line is now, rather than at the next keepalive.
                if (owesReading) {
                    owesReading = false
                    if (someoneFollowsMeLocked()) sendReadingLocked(settled = true)
                }
            }

            "presence_diff" -> {
                val payload = root["payload"]?.jsonObject ?: return
                payload["leaves"]?.jsonObject?.keys?.forEach { presenceStore.remove(it) }
                payload["joins"]?.jsonObject?.forEach { (key, value) ->
                    firstMeta(value)?.let { presenceStore[key] = it }
                }
                emitRosterLocked()
            }

            "broadcast" -> handleBroadcastLocked(root)

            "system" -> handleSystemLocked(root)
        }
    }

    /**
     * The server's own word about the channel. Only its status and kind are
     * ever written down — never what it carried. The one it matters for is
     * presence's limit: it closes the channel behind it, the reconnect below
     * brings the line back, and nothing more is sent for a whole window.
     */
    private fun handleSystemLocked(root: JsonObject) {
        val payload = root["payload"] as? JsonObject ?: return
        val status = (payload["status"] as? JsonPrimitive)?.contentOrNull
        val extension = (payload["extension"] as? JsonPrimitive)?.contentOrNull
        println("[RoomChannel] system: ${status ?: "-"} ${extension ?: "-"}")
        val message = (payload["message"] as? JsonPrimitive)?.contentOrNull
        if (PresenceBudget.isPresenceLimit(message, extension)) budget.saturate()
    }

    private fun handleReplyLocked(root: JsonObject) {
        val reference = root["ref"]?.jsonPrimitive?.content ?: return
        if (reference != joinRef) return
        val status = root["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.content
        if (status == "ok") {
            phase = Phase.JOINED
            reconnectAttempt = 0
            budget.joined()
            if (returning) {
                // Coming back to the app is the reader, not the page.
                returning = false
                lastActivity = System.currentTimeMillis()
                announcement = announcement?.copy(isIdle = false)
            }
            if (announcement != null && idleJob == null) startIdleWatchLocked()
            // Whatever this device was already saying about itself goes up
            // again: a reconnect must not quietly withdraw a reader from a
            // room they never left. Budget or no budget — appearing matters.
            reconcileLocked(joining = true)
            owesReading = lastReading != null
            if (pendingAnnounce) {
                pendingAnnounce = false
                roomID?.let { room -> send(RoomChannelWire.roomChanged(room, nextRefLocked())) }
            }
            return
        }
        // A refused private join on a project whose Realtime Authorization
        // policies are not in place yet. Say so once, come back public, and
        // try the private join again the next time the room is opened.
        if (wantsPrivate) {
            wantsPrivate = false
            println(
                "[RoomChannel] private join refused; falling back to a public channel. " +
                    "Apply 20260914120000_ribbon_realtime_room_channel.sql to close it.",
            )
            val me = person ?: return
            val mine = generation
            scope.launch { mutex.withLock { if (mine == generation) sendJoinLocked(me.id, mine) } }
            return
        }
        scheduleReconnectLocked()
    }

    private fun handleBroadcastLocked(root: JsonObject) {
        val payload = root["payload"] as? JsonObject ?: return
        val inner = (payload["event"] as? JsonPrimitive)?.contentOrNull ?: return
        val body = payload["payload"] as? JsonObject
        when (inner) {
            "thinking_of_you" -> {
                val to = body?.get("toPersonID")?.jsonPrimitive?.content ?: return
                val me = person?.id?.let { RoomChannelWire.id(it) } ?: return
                if (to != me) return
                val from = body["fromName"]?.jsonPrimitive?.content ?: return
                _events.tryEmit(PresenceEvent.ThinkingOfYou(from))
            }
            // Our own broadcasts do not come back (`self: false`), so this is
            // always someone else's news.
            "room_changed" -> roomID?.let { _events.tryEmit(PresenceEvent.RoomChanged(it)) }

            "reading" -> {
                val heard = RoomChannelWire.heard(body) ?: return
                if (heard.personID == person?.id) return
                _events.tryEmit(
                    PresenceEvent.Reading(
                        personID = heard.personID,
                        source = heard.source,
                        book = heard.book,
                        report = ReadingReport(
                            at = heard.at,
                            end = heard.end,
                            settled = heard.settled,
                            carried = heard.carried,
                            received = Clock.System.now(),
                        ),
                    ),
                )
            }
        }
    }

    /** Whether anybody on the roster is following this device's person. */
    private fun someoneFollowsMeLocked(): Boolean {
        val me = person?.id?.let { RoomChannelWire.id(it) } ?: return false
        return presenceStore.values.any { meta ->
            (meta["followingPersonID"] as? JsonPrimitive)?.contentOrNull?.lowercase() == me
        }
    }

    private fun firstMeta(value: kotlinx.serialization.json.JsonElement): JsonObject? =
        runCatching { value.jsonObject["metas"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()

    private fun emitRosterLocked() {
        val me = person?.id?.let { RoomChannelWire.id(it) } ?: return
        val roster = mutableListOf<PresentPerson>()
        for ((key, meta) in presenceStore) {
            // Your own portrait is never in the presence line (§4.2, S07).
            if (key == me) continue
            val idText = meta["id"]?.jsonPrimitive?.content ?: key
            val id = runCatching { Uuid.parse(idText) }.getOrNull() ?: continue
            if (RoomChannelWire.id(id) == me) continue
            val name = meta["name"]?.jsonPrimitive?.content.orEmpty()
            if (name.isEmpty()) continue

            var position: VerseAddress? = null
            val raw = meta["position"]?.jsonObject
            if (raw != null) {
                val book = raw["book"]?.jsonPrimitive?.content
                val chapter = raw["chapter"]?.jsonPrimitive?.intOrNull
                val verse = raw["verse"]?.jsonPrimitive?.intOrNull
                if (book != null && chapter != null && verse != null) {
                    position = VerseAddress(book, chapter, verse)
                }
            }
            roster.add(
                PresentPerson(
                    id = id,
                    name = name,
                    position = position,
                    scrollFraction = meta["scrollFraction"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    isIdle = meta["isIdle"]?.jsonPrimitive?.booleanOrNull ?: false,
                    followingPersonID = meta["followingPersonID"]?.jsonPrimitive?.content
                        ?.let { runCatching { Uuid.parse(it) }.getOrNull() },
                ),
            )
        }
        // A stable order: the line must not reshuffle itself every time
        // somebody scrolls.
        roster.sortBy { it.dashedID }
        _events.tryEmit(PresenceEvent.Roster(roster))
    }
}

private val PresentPerson.dashedID: String get() = RoomChannelWire.id(id)

/** Eight hex digits, fresh each time — a name for a stream, not for a person. */
private fun newSource(): String = Random.nextInt().toUInt().toString(16).padStart(8, '0')
