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
import app.readribbon.core.Ribbon
import app.readribbon.core.ReflectionCard
import app.readribbon.core.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant
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

    /**
     * Whether the clock is inside quiet hours (S19).
     *
     * The two rows on S19 have been writing these minutes since the screen
     * was built and nothing has ever read them back, which is deviation 15's
     * complaint in its narrowest form: a control the person sets and the app
     * does not honour.
     *
     * The arithmetic is not the obvious arithmetic, and that is the whole
     * reason this is a function rather than a comparison written out at the
     * call site. The default window runs 10 p.m. to 6 a.m., so `start` is
     * *after* `end` — a window that wraps midnight is the normal case here
     * and not the edge case. `minute >= start && minute < end` is false for
     * every minute of the default window and true for the whole of the day
     * it exists to leave alone, which is the bug inverted rather than
     * missing, and the kind that ships.
     *
     * Equal ends mean no quiet hours rather than a silent day: a person who
     * drags both rows to the same time has said "never", and reading it as
     * "always" would take the app away from them.
     *
     * @param minuteOfDay minutes since local midnight, in the zone the phone
     *   is in *now* — §1's friend four time zones away makes a person who has
     *   flown the ordinary case, so this is evaluated at the moment something
     *   would be posted rather than when the setting was made.
     */
    fun isQuietAt(minuteOfDay: Int): Boolean {
        if (quietHoursStart == quietHoursEnd) return false
        return if (quietHoursStart < quietHoursEnd) {
            minuteOfDay >= quietHoursStart && minuteOfDay < quietHoursEnd
        } else {
            minuteOfDay >= quietHoursStart || minuteOfDay < quietHoursEnd
        }
    }

    /** The same, against the clock on this phone right now. */
    fun isQuietNow(now: Instant = Clock.System.now()): Boolean {
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault()).time
        return isQuietAt(local.hour * 60 + local.minute)
    }
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
    /**
     * The room's own place in each open book (deviation A30). One per
     * reading, not one per person — see [Ribbon].
     */
    val ribbons: List<Ribbon> = emptyList(),
    val cards: List<ReflectionCard> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val currentRoomID: Uuid? = null,
    val settings: AppSettings = AppSettings(),
    val hasSeenMarginHint: Boolean = false,
    /**
     * Whether the fire has ever been *pulled* on this device.
     *
     * The room offers the hearth's gesture — "Pull the fire up to open the
     * book" — until it has been used. Same contract as [hasSeenMarginHint]
     * and for the same reason (§6.1): a hint that comes back is worse than
     * no hint.
     *
     * The gesture and not the book, and the distinction is the whole value of
     * the flag. Keyed on the book being open by any route, the first tap of
     * the way-in capsule — which is what everybody does, because the gesture
     * is undiscovered on first run — permanently retired the only sentence
     * that teaches the gesture. The headline interaction of the whole pass
     * would have got one viewing at 11 sp and then never been mentioned
     * again.
     */
    val hasPulledTheFire: Boolean = false,
    /**
     * The tag of the portrait object each face on this device came from, so
     * a conditional fetch can be told what it already has. Persisted: a
     * relaunch must not re-download every face in the room.
     */
    val portraitETags: Map<Uuid, String> = emptyMap(),
    /**
     * The newest row this device has already accounted for, for the purpose
     * of notifications (S19).
     *
     * Null means this device has never merged anything, and that case is the
     * whole reason the field exists: a first sync on a new phone restores
     * every room a person is in, which for a couple a year into this is
     * several hundred notes. Reported naively that is several hundred
     * notifications in one breath, the first time somebody signs in on a new
     * phone — the single most destructive failure this feature has. So the
     * first merge sets the watermark and posts nothing at all (§6.10).
     *
     * It advances on every merge whether or not anything was posted, so a
     * notification that was suppressed — quiet hours, a switch turned off,
     * the room already on screen — is not re-offered by the next one.
     */
    val notifiedThrough: Instant? = null,
    /**
     * Whether this device has been asked about notifications yet (§6.1).
     *
     * The same contract as [hasSeenMarginHint] and [hasPulledTheFire], and
     * for the same reason: the app asks once, in context, and a question that
     * comes back is worse than no question. Android cannot tell "never asked"
     * from "refused for good" — `shouldShowRequestPermissionRationale` is
     * false in both cases — so the app has to remember, exactly as
     * `AudioNotes` already does for the microphone.
     *
     * Per install, and never pushed to the backend: it is a fact about this
     * phone and not about the person.
     */
    val hasAskedAboutNotifications: Boolean = false,
    /**
     * Invites minted on this phone that the backend has not acknowledged.
     *
     * Persisted, and that is the whole point. `pendingInvitePushes` is an
     * in-memory set, so a push that failed — offline, a dropped request —
     * was forgotten at the next launch, and `merge`'s invite prune then
     * deleted the local invite *because* the backend did not have it. The
     * link the sender had already pasted into a message thread resolved to
     * nothing, permanently, and nothing anywhere said so.
     *
     * A link is the whole mechanism (S15), so it has to outlive a pull that
     * cannot see it yet.
     */
    val invitesNotYetPushed: Set<Uuid> = emptySet(),
    /**
     * Invites that actually left this phone — the share sheet was opened on
     * them.
     *
     * Minting is not sending. The invite step of onboarding mints a link on
     * appearance and so does the invite sheet, so every person who has ever
     * *seen* either had a live invite by the room's reckoning, and the room
     * told them "The invite is still out." with a control to send it again —
     * on the first morning of a room they had told nobody about. S15's
     * pending state is about a link that was handed out.
     *
     * Local, and deliberately not a column: whether *this* device pressed
     * share is not the room's business, and an invite pulled from another
     * member's phone is the room's live link either way.
     */
    val invitesHandedOut: Set<Uuid> = emptySet(),
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
