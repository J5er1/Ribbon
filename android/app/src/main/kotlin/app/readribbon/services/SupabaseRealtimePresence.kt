@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.VerseAddress
import app.readribbon.data.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Supabase Realtime presence and broadcast service over Phoenix Channels WebSocket (§4.2, §4.3).
 *
 * Connects to `/realtime/v1/websocket`, joins the active room's topic, tracks presence,
 * and handles "thinking of you" broadcast events.
 */
class SupabaseRealtimePresenceService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : PresenceService {

    private val _events = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)
    override val events: Flow<PresenceEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var currentRoomID: Uuid? = null
    private var currentPerson: Person? = null
    private var currentPosition: VerseAddress? = null
    private var currentScrollFraction: Double = 0.0
    private var currentIsIdle: Boolean = false
    private var refCount = 0

    private val presenceStore = mutableMapOf<String, JsonObject>()

    override suspend fun join(roomID: Uuid, person: Person) {
        if (currentRoomID == roomID && webSocket != null) {
            currentPerson = person
            return
        }
        leave()

        currentRoomID = roomID
        currentPerson = person
        presenceStore.clear()

        val baseUrl = SupabaseConfig.url.trimEnd('/')
        val wsBase = when {
            baseUrl.startsWith("https://") -> baseUrl.replaceFirst("https://", "wss://")
            baseUrl.startsWith("http://") -> baseUrl.replaceFirst("http://", "ws://")
            else -> "wss://$baseUrl"
        }
        val url = "$wsBase/realtime/v1/websocket?apikey=${SupabaseConfig.publishableKey}&vsn=1.0.0"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                startHeartbeat()
                sendJoin(roomID, person.id)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    delay(2000)
                    val r = currentRoomID
                    val p = currentPerson
                    if (r != null && p != null && r == roomID) {
                        join(r, p)
                    }
                }
            }
        })
    }

    override suspend fun leave() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        val roomID = currentRoomID
        if (roomID != null) {
            sendLeave(roomID)
        }

        webSocket?.close(1000, "Leaving room")
        webSocket = null
        currentRoomID = null
        currentPerson = null
        presenceStore.clear()
        _events.tryEmit(PresenceEvent.Roster(emptyList()))
    }

    override suspend fun update(position: VerseAddress?, scrollFraction: Double, isIdle: Boolean) {
        currentPosition = position
        currentScrollFraction = scrollFraction
        currentIsIdle = isIdle

        val roomID = currentRoomID ?: return
        val person = currentPerson ?: return
        if (webSocket == null) return
        sendTrack(roomID, person, position, scrollFraction, isIdle)
    }

    override suspend fun sendThinkingOfYou(to: Uuid) {
        val roomID = currentRoomID ?: return
        val me = currentPerson ?: return
        val ws = webSocket ?: return

        refCount++
        val topic = "realtime:room:${roomID.lowercased()}"
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", "broadcast")
            putJsonObject("payload") {
                put("type", "broadcast")
                put("event", "thinking_of_you")
                putJsonObject("payload") {
                    put("fromName", me.name)
                    put("toPersonID", to.lowercased())
                }
            }
            put("ref", "toy-$refCount")
        }
        ws.send(msg.toString())
    }

    // MARK: - Phoenix Wire Protocol

    private fun sendJoin(roomID: Uuid, personID: Uuid) {
        val ws = webSocket ?: return
        refCount++
        val topic = "realtime:room:${roomID.lowercased()}"
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", "phx_join")
            putJsonObject("payload") {
                putJsonObject("config") {
                    putJsonObject("broadcast") {
                        put("ack", false)
                        put("self", false)
                    }
                    putJsonObject("presence") {
                        put("key", personID.lowercased())
                    }
                }
            }
            put("ref", "join-$refCount")
        }
        ws.send(msg.toString())
    }

    private fun sendLeave(roomID: Uuid) {
        val ws = webSocket ?: return
        refCount++
        val topic = "realtime:room:${roomID.lowercased()}"
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", "phx_leave")
            putJsonObject("payload") {}
            put("ref", "leave-$refCount")
        }
        ws.send(msg.toString())
    }

    private fun sendTrack(
        roomID: Uuid,
        person: Person,
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean
    ) {
        val ws = webSocket ?: return
        refCount++
        val topic = "realtime:room:${roomID.lowercased()}"
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", "presence")
            putJsonObject("payload") {
                put("type", "presence")
                put("event", "track")
                putJsonObject("payload") {
                    put("id", person.id.lowercased())
                    put("name", person.name)
                    if (position != null) {
                        putJsonObject("position") {
                            put("book", position.book)
                            put("chapter", position.chapter)
                            put("verse", position.verse)
                        }
                    }
                    put("scrollFraction", scrollFraction)
                    put("isIdle", isIdle)
                }
            }
            put("ref", "track-$refCount")
        }
        ws.send(msg.toString())
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25000)
                refCount++
                val hb = buildJsonObject {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    putJsonObject("payload") {}
                    put("ref", "hb-$refCount")
                }
                webSocket?.send(hb.toString())
            }
        }
    }

    private fun handleIncoming(text: String) {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val root = element.jsonObject
        val event = root["event"]?.jsonPrimitive?.content ?: return

        when (event) {
            "phx_reply" -> {
                val ref = root["ref"]?.jsonPrimitive?.content
                if (ref != null && ref.startsWith("join-")) {
                    val r = currentRoomID
                    val p = currentPerson
                    if (r != null && p != null) {
                        sendTrack(r, p, currentPosition, currentScrollFraction, currentIsIdle)
                    }
                }
            }

            "presence_state" -> {
                val payload = root["payload"]?.jsonObject ?: return
                presenceStore.clear()
                for ((key, metaElement) in payload) {
                    val metas = metaElement.jsonObject["metas"] as? JsonArray
                    val first = metas?.firstOrNull()?.jsonObject
                    if (first != null) {
                        presenceStore[key] = first
                    }
                }
                emitRoster()
            }

            "presence_diff" -> {
                val payload = root["payload"]?.jsonObject ?: return
                val leaves = payload["leaves"]?.jsonObject
                leaves?.keys?.forEach { presenceStore.remove(it) }

                val joins = payload["joins"]?.jsonObject
                if (joins != null) {
                    for ((key, metaElement) in joins) {
                        val metas = metaElement.jsonObject["metas"] as? JsonArray
                        val first = metas?.firstOrNull()?.jsonObject
                        if (first != null) {
                            presenceStore[key] = first
                        }
                    }
                }
                emitRoster()
            }

            "broadcast" -> {
                val payload = root["payload"]?.jsonObject ?: return
                val innerEvent = payload["event"]?.jsonPrimitive?.content
                if (innerEvent == "thinking_of_you") {
                    val innerPayload = payload["payload"]?.jsonObject ?: return
                    val toID = innerPayload["toPersonID"]?.jsonPrimitive?.content
                    val myID = currentPerson?.id?.lowercased()
                    if (toID != null && myID != null && toID == myID) {
                        val fromName = innerPayload["fromName"]?.jsonPrimitive?.content ?: ""
                        _events.tryEmit(PresenceEvent.ThinkingOfYou(fromName))
                    }
                }
            }
        }
    }

    private fun emitRoster() {
        val myID = currentPerson?.id?.lowercased() ?: return
        val result = mutableListOf<PresentPerson>()

        for ((key, meta) in presenceStore) {
            if (key == myID) continue

            val idStr = meta["id"]?.jsonPrimitive?.content ?: key
            val id = runCatching { Uuid.parse(idStr) }.getOrNull() ?: continue
            val name = meta["name"]?.jsonPrimitive?.content ?: ""

            var pos: VerseAddress? = null
            val posObj = meta["position"]?.jsonObject
            if (posObj != null) {
                val book = posObj["book"]?.jsonPrimitive?.content
                val ch = posObj["chapter"]?.jsonPrimitive?.intOrNull
                val v = posObj["verse"]?.jsonPrimitive?.intOrNull
                if (book != null && ch != null && v != null) {
                    pos = VerseAddress(book, ch, v)
                }
            }

            val scroll = meta["scrollFraction"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val isIdle = meta["isIdle"]?.jsonPrimitive?.booleanOrNull ?: false
            val followStr = meta["followingPersonID"]?.jsonPrimitive?.content
            val following = followStr?.let { runCatching { Uuid.parse(it) }.getOrNull() }

            result.add(
                PresentPerson(
                    id = id,
                    name = name,
                    position = pos,
                    scrollFraction = scroll,
                    isIdle = isIdle,
                    followingPersonID = following,
                )
            )
        }

        _events.tryEmit(PresenceEvent.Roster(result))
    }

    private fun Uuid.lowercased(): String = toHexString().lowercase()
}
