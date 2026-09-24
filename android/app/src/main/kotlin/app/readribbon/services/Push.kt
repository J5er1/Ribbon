@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import android.content.Context
import app.readribbon.BuildConfig
import app.readribbon.app.Copy
import app.readribbon.core.VerseAddress
import app.readribbon.data.SupabaseConfig
import app.readribbon.design.Haptics
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Push (S19): the phone's end of the server's half — FCM's side of what
// Push.swift is on iOS, and the reason RoomWatch's header no longer has to
// say that a note left for you arrives fifteen minutes late at best.
//
// Every one of the six is now a row in the database before it is anything
// else, and the backend's `push` function delivers it the moment it is
// written: a data message, always, so that what arrives here is posted by
// the same `Notifications.post` — the same channels, the same ids, the same
// one line — as what the app finds in a pull. Two decisions sit on top of
// that and are worth reading twice:
//
// **One voice, never two.** While the server is delivering for this phone
// the app posts none of the six itself: the same note announced by a push
// and again by the next pull would be the app repeating itself about the
// one thing it should say once. Whether the server is delivering is asked,
// not assumed — the sender answers with whether its FCM account is set —
// and a phone whose registration did not go through keeps speaking for
// itself. The answer is kept in preferences, because the worker and this
// service both run with no screen and no model.
//
// **Firebase is optional in the build.** Its configuration is read out of
// `app/google-services.json` at build time (no plugin — see the build file).
// A build without the file has no Firebase at all: nothing initialises,
// nothing registers, and the app is exactly the app it was before push.

object Push {
    private const val PREFS = "ribbon.push"
    private const val DELIVERING = "delivering"

    /**
     * The room on screen, for the third gate. Mirrored from
     * `AppModel.visibleRoomID`, because a message can arrive with no model
     * at all — a process started by the message itself — and then nothing
     * is on screen.
     */
    @Volatile
    var visibleRoomID: Uuid? = null

    /** Whether the sender has an FCM account, asked once per process. */
    @Volatile
    private var transportLive: Boolean? = null

    /** Whether this process's registration reached the server. */
    @Volatile
    private var registered = false

    /** Set up Firebase from the build's own configuration, if it has one. */
    fun configure(context: Context) {
        if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        }
    }

    fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** This install's FCM token, or null when there is no Firebase or no answer. */
    suspend fun token(context: Context): String? {
        if (!available(context)) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    /** Whether the server is saying the six for this phone right now. */
    fun delivering(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DELIVERING, false)

    /**
     * The sender's own answer about whether it can reach FCM. Asked without
     * an account: it says nothing about anybody.
     */
    suspend fun learnWhetherTheServerDelivers(context: Context) {
        if (transportLive != null) return
        val answer = withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    URL("${SupabaseConfig.URL}/functions/v1/push").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    if (connection.responseCode != 200) return@runCatching null
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    JSONObject(body).optBoolean("android", false)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } ?: return
        transportLive = answer
        settle(context)
    }

    fun registration(context: Context, succeeded: Boolean) {
        registered = succeeded
        settle(context)
    }

    /** Signed out: off the server's list, and speaking for itself at once. */
    fun forgotten(context: Context) {
        registered = false
        remember(context, false)
    }

    /**
     * Until the sender has answered, the remembered answer stands — a
     * launch is not a reason to start saying things twice.
     */
    private fun settle(context: Context) {
        val live = transportLive ?: return
        remember(context, registered && live)
    }

    private fun remember(context: Context, now: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DELIVERING, false) == now) return
        prefs.edit().putBoolean(DELIVERING, now).apply()
    }

    /**
     * One reader's "is reading" notification — the Android stand-in for the
     * Live Activity (S24): posted when they arrive, kept current while they
     * read, and taken away when they leave.
     */
    fun readingID(room: Uuid, person: Uuid?): Int =
        Notifications.id(room, NotificationKind.inTheBook) + (person?.hashCode() ?: 0)

    /** What arrived, posted exactly the way the app posts what it finds itself. */
    fun received(context: Context, data: Map<String, String>) {
        val room = data["room"]?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: return
        val person = data["person"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val notify = data["notify"] ?: return
        if (notify == "left") {
            Notifications.forget(context, listOf(readingID(room, person)))
            return
        }
        val kind = NotificationKind.entries.firstOrNull { it.name == notify } ?: return
        // The server has judged the switch and the quiet hours; the third
        // gate is only knowable here. A phone in your hand is not told what
        // it is showing you — a finished book and a touch pass regardless,
        // as they do in `shouldPost`.
        val gated = kind == NotificationKind.notesLeft ||
            kind == NotificationKind.cardsOpen ||
            kind == NotificationKind.inTheBook
        if (gated && room == visibleRoomID) return
        // Android says each of the six in one line (Notifications.post), so a
        // finished book is its sentence rather than a title over it.
        val line = data["body"] ?: data["title"] ?: return
        val to = destination(room, data)
        when (kind) {
            NotificationKind.notesLeft -> Notifications.post(
                context = context,
                // The same id the pull uses — per room and author — so a push
                // and a pull about the same person replace each other.
                id = Notifications.id(room, NotificationKind.notesLeft) + (person?.hashCode() ?: 0),
                kind = kind,
                line = line,
                to = to,
                silent = data["quiet"] == "1",
            )
            NotificationKind.inTheBook -> Notifications.post(
                context = context,
                id = readingID(room, person),
                kind = kind,
                line = line,
                to = to,
                // Only an arrival is said aloud; the heartbeat keeps the line
                // standing without a sound.
                silent = data["fresh"] != "1",
                ongoing = true,
                // A reader whose phone died never says "left": the line goes
                // on its own once the heartbeat has stopped for long enough.
                timeoutMillis = 25 * 60_000L,
            )
            NotificationKind.thinkingOfYou -> {
                // A touch first, and only then a name (§4.3).
                Haptics(context).tapOnTheShoulder()
                Notifications.post(
                    context = context,
                    id = Notifications.id(room, kind),
                    kind = kind,
                    line = Copy.notifThinkingOfYou(line),
                    to = to,
                )
            }
            else -> Notifications.post(
                context = context,
                id = Notifications.id(room, kind),
                kind = kind,
                line = line,
                to = to,
            )
        }
    }

    private fun destination(room: Uuid, data: Map<String, String>): Destination {
        val reading = data["reading"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val chapter = data["chapter"]?.toIntOrNull()
        return when (data["dest"]) {
            "verse" -> {
                val book = data["book"]
                val verse = data["verse"]?.toIntOrNull()
                if (reading != null && book != null && chapter != null && verse != null) {
                    Destination.Verse(room, reading, VerseAddress(bookID = book, chapter = chapter, verse = verse))
                } else {
                    Destination.Room(room)
                }
            }
            "cards" ->
                if (reading != null && chapter != null) {
                    Destination.Cards(room, reading, chapter)
                } else {
                    Destination.Room(room)
                }
            else -> Destination.Room(room)
        }
    }
}

/**
 * Where FCM hands over. Kept to the two calls it has to make: everything
 * the message means is decided in [Push.received], and a new token is
 * registered by the model on its next launch or return — the one that knows
 * the switches and the quiet hours that go with it.
 */
class RibbonMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Push.received(applicationContext, message.data)
    }

    override fun onNewToken(token: String) {
        // Nothing to do with it here: this process may have no account
        // restored and no model, and a registration without the switches is
        // a second way to be wrong. The next launch registers it whole.
    }
}
