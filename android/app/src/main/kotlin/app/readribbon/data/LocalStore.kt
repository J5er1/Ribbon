@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.data

import android.content.Context
import android.os.StatFs
import app.readribbon.core.Highlight
import app.readribbon.core.Invite
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.Person
import app.readribbon.core.QuietDay
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Local persistence: one serializable state file, written atomically.
// Everything the app knows lives here first — offline is a first-class case
// (§6.10), and the room renders from cache instantly on launch with no
// skeleton (S01). Deliberately a plain file rather than a database: the data
// is small, the shape churns less, and a person's whole shelf can be
// exported by encoding this class (§13).

/**
 * Per-room notification switches (S19). Defaults per the build book: notes
 * on, cards on, "when they open the book" off — the killer feature for
 * couples and the creepiest one for a study, so opt-in per room is the only
 * defensible default.
 */
@Serializable
data class RoomNotificationPrefs(
    val notesLeft: Boolean = true,
    val cardsOpen: Boolean = true,
    val whenTheyOpenTheBook: Boolean = false,
    val thinkingOfYou: Boolean = true,
)

@Serializable
data class AppSettings(
    val scriptureSize: Double = 19.0,
    /** 0, 1, 2 → line-height multiples 1.55, 1.72, 1.9 (S20's three steps). */
    val lineSpacingStep: Int = 1,
    val redLetter: Boolean = false,
    /** One per person, applying to every room (S19). Minutes from midnight,
     *  local. Default 10 p.m. – 6 a.m. */
    val quietHoursStart: Int = 22 * 60,
    val quietHoursEnd: Int = 6 * 60,
    val roomNotifications: Map<Uuid, RoomNotificationPrefs> = emptyMap(),
) {
    val lineHeightMultiple: Double
        get() = listOf(1.55, 1.72, 1.9)[lineSpacingStep.coerceIn(0, 2)]
}

/** The whole of what the app remembers. */
@Serializable
data class AppState(
    val me: Person? = null,
    val people: Map<Uuid, Person> = emptyMap(),
    val rooms: List<Room> = emptyList(),
    val memberships: List<Membership> = emptyList(),
    val readings: List<Reading> = emptyList(),
    val notes: List<Note> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val quietDays: List<QuietDay> = emptyList(),
    val positions: List<ReadingPosition> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val currentRoomID: Uuid? = null,
    val settings: AppSettings = AppSettings(),
    val hasSeenMarginHint: Boolean = false,
)

/**
 * The one store.
 *
 * A mutex stands in for the Swift actor: every read and write of the state
 * file is serialised, so a save triggered by a mutation can never interleave
 * with the load a cold start is doing.
 */
class LocalStore(context: Context) {

    private val base = File(context.filesDir, "Ribbon").apply { mkdirs() }
    private val stateFile = File(base, "state.json")
    private val portraitsDir = File(base, "portraits").apply { mkdirs() }
    private val audioDir = File(base, "audio").apply { mkdirs() }
    private val filesDirPath = context.filesDir.absolutePath

    private val lock = Mutex()

    suspend fun load(): AppState = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!stateFile.exists()) return@withContext AppState()
            runCatching { json.decodeFromString<AppState>(stateFile.readText()) }
                .getOrElse {
                    // A state file we cannot read is a state file we do not
                    // trust. Starting empty loses local-only work, which is
                    // bad; carrying a half-decoded graph forward is worse,
                    // because every later write would persist the damage.
                    AppState()
                }
        }
    }

    suspend fun save(state: AppState) = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                // Atomic: write beside the target, then rename over it, so a
                // kill mid-write leaves the previous state intact rather
                // than a truncated file.
                val temp = File(base, "state.json.tmp")
                temp.writeText(json.encodeToString(state))
                if (!temp.renameTo(stateFile)) {
                    stateFile.writeText(temp.readText())
                    temp.delete()
                }
            }
            Unit
        }
    }

    // MARK: Files

    fun portraitFile(name: String): File = File(portraitsDir, name)

    fun audioFile(name: String): File = File(audioDir, name)

    suspend fun writePortrait(bytes: ByteArray, personID: Uuid): String =
        withContext(Dispatchers.IO) {
            val name = "$personID.jpg"
            File(portraitsDir, name).writeBytes(bytes)
            name
        }

    /**
     * Free space on the device, for the one honest count in the product
     * (S05 — megabytes measure a phone, not a person).
     */
    fun freeMegabytes(): Int? = runCatching {
        val stat = StatFs(filesDirPath)
        (stat.availableBytes / 1_000_000L).toInt()
    }.getOrNull()

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
