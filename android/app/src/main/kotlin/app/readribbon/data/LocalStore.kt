@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.data

import android.content.Context
import android.os.StatFs
import app.readribbon.core.Highlight
import app.readribbon.core.Invite
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.PageFace
import app.readribbon.core.PageFaces
import app.readribbon.core.PageType
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
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

/**
 * The reader's own settings (S19, S20).
 *
 * The page's choices are kept as plain steps and switches, never as enums:
 * the file is read without `coerceInputValues`, so a value a later build adds
 * to an enum would make this whole object unreadable to the build before it,
 * and salvage would hand back every default. A step out of range only sets
 * the nearer end of its table ([PageType]), and a slider's value out of range
 * the nearer end of its scale (A69). The face is kept as its id, a String,
 * for the same reason.
 */
@Serializable
data class AppSettings(
    /** Scripture's size in points, before font scale ([PageType.sizeRange]). */
    val scriptureSize: Double = PageType.defaultSize,
    /**
     * 0, 1, 2 → Close, Book, Open (S20's three steps, [PageType.lineHeightMultiples]).
     * Once the slider has been moved, the stop nearest its value, kept for a
     * build from before the sliders (A69).
     */
    val lineSpacingStep: Int = PageType.defaultLineSpacingStep,
    val redLetter: Boolean = false,
    /** One per person, applying to every room (S19). Minutes from midnight,
     *  local. Default 10 p.m. – 6 a.m. */
    val quietHoursStart: Int = 22 * 60,
    val quietHoursEnd: Int = 6 * 60,
    val roomNotifications: Map<Uuid, RoomNotificationPrefs> = emptyMap(),
    /**
     * 0, 1, 2 → Lighter, Book, Heavier (A68, [PageType.weights]). A step
     * rather than a weight, so the table can be retuned under it. Once the
     * slider has been moved, the stop nearest its value, kept for a build
     * from before the sliders (A69).
     */
    val weightStep: Int = PageType.defaultWeightStep,
    /**
     * In prose, each numbered verse starts its own line (A68). Poetry,
     * titles and stanza breaks are set as they always were.
     */
    val versePerLine: Boolean = false,
    /** Verse numbers in a stronger ink, nothing moved (A68). */
    val clearVerseNumbers: Boolean = false,
    /**
     * The line spacing slider's value, in hundredths of the multiple (A69).
     * Null until a reader moves it: the page is then the step's.
     */
    val lineHeightHundredths: Int? = null,
    /**
     * The weight slider's value on Literata's axis, before Bold Text (A69).
     * Null until a reader moves it: the page is then the step's.
     */
    val pageWeight: Int? = null,
    /** Room between the letters, in thousandths of an em (A69). */
    val letterSpacingThousandths: Int = 0,
    /**
     * The margin asked for, in dp a side (A69). The page gives less when the
     * words would otherwise be too narrow.
     */
    val marginPoints: Int = 0,
    /**
     * The page's face, by its id (A69). A String, never an enum, so a face a
     * later build adds opens here in Literata rather than costing the field.
     */
    val typeface: String = PageFaces.literata.id,
) {
    /**
     * The slider's value if a reader has moved it, else the old step's (A69):
     * a file from before the sliders opens on its own page.
     */
    val lineHeightMultiple: Double
        get() = PageType.lineHeightMultipleOf(PageType.lineHeightHundredths(lineHeightHundredths, lineSpacingStep))

    /**
     * The page's weight on Literata's axis, with the system's Bold Text
     * folded in (A68): the slider's value if a reader has moved it, else the
     * old step's (A69). A face draws it through [PageType.faceWeight].
     */
    fun weight(boldText: Boolean): Int =
        PageType.weight(saved = pageWeight, legacyStep = weightStep) + if (boldText) PageType.boldTextWeight else 0

    val verseNumberAlpha: Double
        get() = PageType.verseNumberAlpha(clearVerseNumbers)

    /** The page's face. One this build does not have is Literata. */
    val face: PageFace
        get() = PageFaces.face(typeface)

    /** The room between letters, in ems, held to the scale. */
    val letterSpacingEm: Double
        get() = PageType.letterSpacingEm(letterSpacingThousandths)

    /**
     * The margin asked for, in dp, held to the scale. What the page gives is
     * [PageType.margin].
     */
    val marginRequested: Double
        get() = PageType.marginScale.held(marginPoints).toDouble()

    /**
     * The line spacing slider's write: the value held to the scale, and the
     * nearest old step beside it, so that a build from before the sliders
     * opens on nearly the same page.
     */
    fun withLineHeight(h: Int): AppSettings {
        val held = PageType.lineHeightScale.held(h)
        return copy(lineHeightHundredths = held, lineSpacingStep = PageType.lineSpacingStep(forHundredths = held))
    }

    /** The weight slider's write, as for line spacing. */
    fun withWeight(w: Int): AppSettings {
        val held = PageType.weightScale.held(w)
        return copy(pageWeight = held, weightStep = PageType.weightStep(forWeight = held))
    }

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
    /**
     * The last "what's new" release this phone has shown, or recorded as
     * not needing to (A61) — a release id from `WhatsNew.releases`, never a
     * version number.
     *
     * The same contract as [hasSeenMarginHint], one step wider: a flag says
     * "told once, ever", and this says "told once, per release". Null is
     * what every state file written before the screen existed decodes to,
     * and that is meaningful rather than missing — with a person already
     * here, it is somebody updating from a build that never had the screen,
     * and they are the people it is for. `WhatsNew.toShow` reads it that way.
     *
     * Per install, and never pushed to the backend, for the reason
     * [hasAskedAboutNotifications] is not: it is a fact about the build on
     * this phone, and a second phone on an older build has not seen anything.
     */
    val whatsNewSeen: String? = null,
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
            val text = runCatching { stateFile.readText() }.getOrNull()
                ?: return@withContext AppState()
            runCatching { json.decodeFromString<AppState>(text) }
                .getOrElse { salvage(text) }
        }
    }

    /**
     * What to keep from a state file that will not decode.
     *
     * A state file we cannot read is a state file we do not trust: carrying a
     * half-decoded graph forward is worse than starting empty, because every
     * later write would persist the damage. That much was already true. What
     * was wrong was *starting empty and then saving over it*, which threw
     * away the only part of this file that nothing else in the world has a
     * copy of.
     *
     * **Almost everything here is a cache.** Rooms, readings, notes,
     * highlights, cards, ribbons and people all live on the backend and come
     * back on the next sync, which is why a total reset does not look like a
     * disaster: the app fills back in and nothing appears to be missing. The
     * settings do not come back, because they are only ever written here. So
     * a decode failure presented as exactly one symptom — *"preferences do not
     * stay between updates of the app"*, which is how the owner reported it —
     * and the far larger reset underneath it was invisible.
     *
     * So the fields that cannot be re-fetched are pulled out one at a time,
     * each in its own `runCatching`, so one unreadable field cannot take the
     * rest with it. This is deliberately not a `@Serializable` sub-class: the
     * point is to decode as little as possible and to keep decoding after a
     * failure, and a nested object gives up on both.
     *
     * The unreadable file is kept rather than overwritten, so that whatever
     * broke it can still be looked at afterwards — see [keepTheUnreadableFile].
     */
    private fun salvage(text: String): AppState {
        keepTheUnreadableFile()
        val fields = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return AppState()

        fun <T> saved(name: String, fallback: T, read: (JsonElement) -> T): T {
            val raw = fields[name] ?: return fallback
            return runCatching { read(raw) }.getOrDefault(fallback)
        }

        return AppState(
            // Eyes, sleep, and which rooms may wake the phone. None of it is
            // anywhere else. Read whole if it will be, else field by field,
            // so that one bad value costs only itself (as I42 on the iPhone).
            settings = saved("settings", AppSettings()) {
                runCatching { json.decodeFromJsonElement<AppSettings>(it) }
                    .getOrElse { _ -> salvageSettings(it.jsonObject) }
            },
            // The three "asked once" flags (§6.1). Losing one is not a
            // disaster, it is a hint or a permission prompt coming back — but
            // a hint that comes back is the thing §6.1 is against.
            hasSeenMarginHint = saved("hasSeenMarginHint", false) {
                json.decodeFromJsonElement(it)
            },
            hasPulledTheFire = saved("hasPulledTheFire", false) {
                json.decodeFromJsonElement(it)
            },
            hasAskedAboutNotifications = saved("hasAskedAboutNotifications", false) {
                json.decodeFromJsonElement(it)
            },
            // The fourth thing this phone has been told once (A61). Lost, it
            // would put the release's screen in front of the room a second
            // time, on the morning after the file was damaged — the worst
            // moment to be shown anything.
            whatsNewSeen = saved<String?>("whatsNewSeen", null) {
                json.decodeFromJsonElement(it)
            },
            // An invite that never reached the backend exists only here, and
            // its link may already be in somebody's message thread (A37).
            invitesNotYetPushed = saved("invitesNotYetPushed", emptySet()) {
                json.decodeFromJsonElement(it)
            },
            invitesHandedOut = saved("invitesHandedOut", emptySet()) {
                json.decodeFromJsonElement(it)
            },
            // Deliberately not salvaged: `notifiedThrough`. Null means this
            // device has never merged, which makes the next merge silent
            // (S19) — and after a reset that is exactly right, because
            // everything is about to arrive at once.
        )
    }

    /**
     * The settings read one field at a time, for a settings object that will
     * not decode whole: a field that is missing or unreadable takes its
     * default and costs nothing else. Since the sliders (A69) the settings
     * hold more numbers a later build might write differently, and before
     * this, one bad one cost all of them.
     */
    private fun salvageSettings(fields: JsonObject): AppSettings {
        val book = AppSettings()

        fun <T> saved(name: String, fallback: T, read: (JsonElement) -> T): T {
            val raw = fields[name] ?: return fallback
            return runCatching { read(raw) }.getOrDefault(fallback)
        }

        return AppSettings(
            scriptureSize = saved("scriptureSize", book.scriptureSize) { json.decodeFromJsonElement(it) },
            lineSpacingStep = saved("lineSpacingStep", book.lineSpacingStep) { json.decodeFromJsonElement(it) },
            redLetter = saved("redLetter", book.redLetter) { json.decodeFromJsonElement(it) },
            quietHoursStart = saved("quietHoursStart", book.quietHoursStart) { json.decodeFromJsonElement(it) },
            quietHoursEnd = saved("quietHoursEnd", book.quietHoursEnd) { json.decodeFromJsonElement(it) },
            roomNotifications = saved("roomNotifications", book.roomNotifications) {
                json.decodeFromJsonElement(it)
            },
            weightStep = saved("weightStep", book.weightStep) { json.decodeFromJsonElement(it) },
            versePerLine = saved("versePerLine", book.versePerLine) { json.decodeFromJsonElement(it) },
            clearVerseNumbers = saved("clearVerseNumbers", book.clearVerseNumbers) {
                json.decodeFromJsonElement(it)
            },
            lineHeightHundredths = saved("lineHeightHundredths", book.lineHeightHundredths) {
                json.decodeFromJsonElement(it)
            },
            pageWeight = saved("pageWeight", book.pageWeight) { json.decodeFromJsonElement(it) },
            letterSpacingThousandths = saved("letterSpacingThousandths", book.letterSpacingThousandths) {
                json.decodeFromJsonElement(it)
            },
            marginPoints = saved("marginPoints", book.marginPoints) { json.decodeFromJsonElement(it) },
            typeface = saved("typeface", book.typeface) { json.decodeFromJsonElement(it) },
        )
    }

    /**
     * Moves an unreadable state file aside instead of letting the next save
     * write over it.
     *
     * One copy, replaced each time, at `state.json.unreadable`. It is the only
     * evidence of what actually happened, and the next `save()` is moments
     * away and would otherwise destroy it — which is why the old behaviour
     * could never be diagnosed after the fact.
     *
     * A rename rather than a copy where the filesystem allows one, so this
     * cannot itself fail for want of space on a phone that has run out of it.
     */
    private fun keepTheUnreadableFile() {
        runCatching {
            val kept = File(base, "state.json.unreadable")
            if (kept.exists()) kept.delete()
            if (!stateFile.renameTo(kept)) {
                stateFile.copyTo(kept, overwrite = true)
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
