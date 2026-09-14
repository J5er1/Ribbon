@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
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
import kotlinx.serialization.json.booleanOrNull
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
 * One socket, one channel — `realtime:room:<room id>` — and three things
 * travel on it:
 *
 *  * **Presence.** Who is in the book right now, where they are, whether they
 *    have gone still, and who they are following. Announced only while
 *    someone is actually reading: opening the channel says nothing.
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

    private var webSocket: WebSocket? = null
    private var roomID: Uuid? = null
    private var person: Person? = null
    private var phase = Phase.CLOSED
    private var announcement: Announcement? = null
    private val presenceStore = mutableMapOf<String, JsonObject>()

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
            if (this.roomID == roomID && phase != Phase.CLOSED) {
                this.person = person
                return
            }
            closeLocked()
            this.roomID = roomID
            this.person = person
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

    override suspend fun present(
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
    ) {
        mutex.withLock {
            val wasAnnouncing = announcement != null
            announcement = Announcement(position, scrollFraction, isIdle, following)
            if (!isIdle) lastActivity = System.currentTimeMillis()
            if (!wasAnnouncing) startIdleWatchLocked()
            sendTrackLocked()
        }
    }

    override suspend fun withdraw() {
        mutex.withLock {
            if (announcement == null) return
            announcement = null
            idleJob?.cancel()
            idleJob = null
            val room = roomID ?: return
            if (phase != Phase.JOINED) return
            send(RoomChannelWire.untrack(room, nextRefLocked()))
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

    private fun closeLocked() {
        generation += 1
        heartbeatJob?.cancel(); heartbeatJob = null
        idleJob?.cancel(); idleJob = null
        reconnectJob?.cancel(); reconnectJob = null
        val room = roomID
        if (phase == Phase.JOINED && room != null) {
            send(RoomChannelWire.leave(room, nextRefLocked()))
        }
        webSocket?.close(1000, "Leaving the room")
        webSocket = null
        phase = Phase.CLOSED
        joinRef = null
        roomID = null
        person = null
        announcement = null
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
                            handleLocked(text)
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
                    sendTrackLocked()
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

    private fun sendTrackLocked() {
        if (phase != Phase.JOINED) return
        val room = roomID ?: return
        val me = person ?: return
        val current = announcement ?: return
        send(
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
    }

    private fun send(message: JsonObject) {
        webSocket?.send(message.toString())
    }

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
        }
    }

    private fun handleReplyLocked(root: JsonObject) {
        val reference = root["ref"]?.jsonPrimitive?.content ?: return
        if (reference != joinRef) return
        val status = root["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.content
        if (status == "ok") {
            phase = Phase.JOINED
            reconnectAttempt = 0
            // Whatever this device was already saying about itself goes up
            // again: a reconnect must not quietly withdraw a reader from a
            // room they never left.
            if (announcement != null) sendTrackLocked()
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
        val payload = root["payload"]?.jsonObject ?: return
        val inner = payload["event"]?.jsonPrimitive?.content ?: return
        val body = payload["payload"]?.jsonObject
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
