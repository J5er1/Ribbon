@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import android.content.Context
import app.readribbon.core.FuelEvent
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
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
import app.readribbon.core.TranslationID
import app.readribbon.data.RoomNotificationPrefs
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The sign-in thread (§6.10) and the room surface of sync: accounts,
// invites, joining, and the graph a room renders from — rooms, memberships,
// profiles, readings, fires, the rolling fuel window, notes, highlights,
// positions, and reflection cards.
//
// The app stays local-first: everything here is best-effort and
// fire-and-forget from the UI's point of view. Nothing blocks reading.

/**
 * What the join screen shows before joining (S16): a person, not a
 * product.
 */
@Serializable
data class InvitePreview(
    val inviterName: String? = null,
    val roomName: String? = null,
    val expired: Boolean,
    val full: Boolean,
)

/** The room-surface graph, as pulled from the backend. */
data class RoomGraph(
    val rooms: List<RemoteSync.RoomRow> = emptyList(),
    val memberships: List<RemoteSync.MembershipRow> = emptyList(),
    val profiles: List<RemoteSync.ProfileRow> = emptyList(),
    val readings: List<RemoteSync.ReadingRow> = emptyList(),
    val fires: List<RemoteSync.FireRow> = emptyList(),
    val fuelEvents: List<RemoteSync.FuelEventRow> = emptyList(),
    val quietDays: List<RemoteSync.QuietDayRow> = emptyList(),
    val invites: List<RemoteSync.InviteRow> = emptyList(),
    val notes: List<RemoteSync.NoteRow> = emptyList(),
    val noteFounds: List<RemoteSync.NoteFoundRow> = emptyList(),
    val highlights: List<RemoteSync.HighlightRow> = emptyList(),
    val positions: List<RemoteSync.PositionRow> = emptyList(),
    val ribbons: List<RemoteSync.RibbonRow> = emptyList(),
    val cards: List<RemoteSync.CardRow> = emptyList(),
    val cardAnswers: List<RemoteSync.CardAnswerRow> = emptyList(),
    /**
     * Whether the notes and highlights in this graph are the *whole* of what
     * the backend holds for these readings.
     *
     * Both selects are wrapped in `runCatching { … }.getOrDefault(emptyList())`
     * — deliberately, because a room must still render when one table is
     * unreachable — and that makes "this room has no notes" and "the notes
     * request failed" the same value. Harmless while the merge only ever
     * added rows; fatal the moment it started removing them, because one
     * failed request would delete every note in the room from the phone.
     *
     * So the two cases are told apart here, once, at the only place that
     * knows which it was.
     */
    val notesComplete: Boolean = false,
    val highlightsComplete: Boolean = false,
)

/**
 * Swift marks this class `@MainActor`. There is no such annotation here, so
 * the confinement is a convention instead: every method is a suspend
 * function called from `viewModelScope` (main-dispatched), and the two
 * mirrored properties below are read and written on that thread only. The
 * blocking work — sockets, keystore, disk — is moved off it inside
 * `SupabaseClient` and at the `SessionStore` call sites here.
 */
class RemoteSync(
    /**
     * Where the session tokens live between launches. Swift's counterpart
     * is a private `SessionKeychain` enum at the bottom of the same file;
     * on Android the keystore work is large enough to be its own file, and
     * the reasoning that made it a credential store rather than state lives
     * in SessionStore.kt.
     */
    private val sessions: SessionStore,
    private val client: SupabaseClient = SupabaseClient(),
) {
    /** Mirrored from the client so synchronous UI checks don't await. */
    var userID: Uuid? = null
        private set
    var email: String? = null
        private set

    val isSignedIn: Boolean get() = userID != null

    // MARK: - Sign-in: an emailed code, no passwords (§6.10)

    suspend fun sendCode(to: String) {
        client.sendCode(to = to)
    }

    suspend fun verify(email: String, code: String): Uuid {
        val session = client.verifyCode(email = email, code = code)
        withContext(Dispatchers.IO) { sessions.save(session) }
        userID = session.user.id
        this.email = session.user.email ?: email
        signedInWithAuth0 = false
        return session.user.id
    }

    // MARK: Passkeys (§6.10)

    /**
     * Whether this project has passkeys switched on. Null until asked.
     *
     * Not a constant and not a guess. §6.10 says "a passkey where available,
     * an emailed code otherwise", and *available* was being read as "the
     * platform has `CredentialManager`", which every phone does. It is a
     * project setting, and on this project it is off — so the control was
     * offered to everybody, raised the system's own sheet, and answered
     * "that passkey didn't work" every time. See [SupabaseClient.authSettings].
     */
    var passkeysEnabled: Boolean? = null
        private set

    /**
     * Whether the session in hand was issued by Auth0 rather than by GoTrue.
     *
     * Mirrored from [SupabaseClient.SupabaseSession.isAuth0] for the same
     * reason [userID] and [email] are mirrored: the UI has to decide what to
     * draw without suspending.
     *
     * **It decides whether a passkey can be added at all.** Signing in through
     * Auth0 stores the *Auth0 ID token* as the session's access token — which
     * is right for PostgREST, where third-party auth is exactly what that
     * token is for — but the `auth/v1/passkeys` endpoints are GoTrue's own,
     * and GoTrue
     * authenticates the bearer against its own signing key and resolves the
     * `sub` to a row in `auth.users`. An Auth0 token satisfies neither: the
     * signature is not GoTrue's and the subject is `auth0|…`. So the register
     * call cannot succeed for an Auth0 session, no system sheet ever appears,
     * and the app used to answer "That passkey didn't work" — having never
     * asked anything of the person at all. A control that cannot work is not
     * drawn (A49).
     */
    var signedInWithAuth0: Boolean = false
        private set

    /**
     * Ask the project what it has switched on, once per launch.
     *
     * Failures are silent and leave the answer null, which reads as *not yet
     * known* rather than as no: an offline launch should not decide that
     * passkeys are unavailable for the rest of the session, and a control
     * that is simply absent until the app can say otherwise is the honest
     * shape of not knowing.
     */
    suspend fun learnWhatAuthOffers() {
        if (passkeysEnabled != null) return
        // Sticky: a failure never writes over an answer. This assigned the
        // `getOrNull()` unconditionally, and there are two callers now — the
        // launch and every resume — so a signed-out cold start fires both
        // before either has landed. If the first request succeeded and the
        // second lost the network, the second wrote **null** over the `true`
        // that had already arrived and the passkey control vanished off the
        // sign-in screen. Answering is a one-way door: not-yet-known can
        // become an answer, an answer cannot become not-yet-known.
        val answer = runCatching { client.authSettings().passkeysEnabled }.getOrNull()
            ?: return
        passkeysEnabled = answer
    }

    /**
     * Register a passkey for the account that is already signed in.
     *
     * @param context an Activity context — the system sheet needs a window.
     */
    suspend fun registerPasskey(context: Context) {
        // `withAuthRetry`, like every other authenticated call here. Both legs
        // of this bear a token, and an access token that expired while the
        // person was reading the screen turned the whole ceremony into "that
        // passkey didn't work" — after the system sheet, after the fingerprint.
        // Worse on the second leg: the credential exists on the authenticator
        // by then, and losing the verify leaves it stranded there, offered at
        // every future sign-in for an account that has never heard of it.
        //
        // Re-POSTing the same `{challenge_id, credential}` after a refresh is
        // safe: the challenge is consumed inside the verify that authenticates,
        // so a call that 401ed consumed nothing.
        val challenge = withAuthRetry { client.passkeyRegistrationOptions() }
        val credential = Passkeys(context).register(context, challenge.optionsJson)
        withAuthRetry {
            client.verifyPasskeyRegistration(
                challengeID = challenge.challengeID, credentialJson = credential)
        }
    }

    /**
     * Sign in with a passkey. Nothing is typed and nothing is emailed: the
     * authenticator names the account, and the session comes back with it.
     */
    suspend fun signInWithPasskey(context: Context): Uuid {
        val challenge = client.passkeyAuthenticationOptions()
        val credential = Passkeys(context).assert(context, challenge.optionsJson)
        val session = client.verifyPasskeyAuthentication(
            challengeID = challenge.challengeID, credentialJson = credential)
        withContext(Dispatchers.IO) { sessions.save(session) }
        userID = session.user.id
        email = session.user.email
        signedInWithAuth0 = false
        return session.user.id
    }

    /**
     * Sign in using an Auth0 ID token. Sets the Supabase session, saves it to persistent storage,
     * and initializes the local user identity.
     */
    suspend fun signInWithAuth0(
        idToken: String,
        userUuid: Uuid,
        email: String?,
        refreshToken: String = "",
    ): Uuid {
        val session = client.setAuth0Session(
            idToken = idToken,
            userUuid = userUuid,
            email = email,
            refreshToken = refreshToken,
        )
        withContext(Dispatchers.IO) { sessions.save(session) }
        userID = session.user.id
        this.email = session.user.email ?: email
        signedInWithAuth0 = true
        return session.user.id
    }

    suspend fun signOut() {
        client.signOut()
        withContext(Dispatchers.IO) { sessions.clear() }
        userID = null
        email = null
        signedInWithAuth0 = false
    }

    /**
     * A token fit for the Realtime socket (§4.2).
     *
     * A socket outlives a token — Ribbon's are open for as long as the room
     * is on screen — and Realtime refuses an expired one outright rather than
     * asking for a new one. So the token is checked before every join and
     * refreshed when it is within a minute of the end; a channel that cannot
     * be authenticated is a room that silently stops being live.
     */
    suspend fun realtimeToken(): String? {
        val token = client.currentSession()?.accessToken ?: return null
        if (!expiresSoon(token)) return token
        runCatching { client.refresh() }
        val refreshed = client.currentSession() ?: return null
        withContext(Dispatchers.IO) { sessions.save(refreshed) }
        userID = refreshed.user.id
        // Still dead — a refresh token that was itself revoked, or an Auth0
        // session with none. Joining with no token at all is better than
        // joining with one the server will reject: the channel comes up
        // public and the room is live, where the other way it is nothing.
        return if (expiresSoon(refreshed.accessToken)) null else refreshed.accessToken
    }

    // MARK: - Invites and joining (S16)

    suspend fun invitePreview(token: Uuid): InvitePreview? {
        val data = client.rpcAnon(
            "invite_preview", body = mapOf("invite_token" to token.lowercased()))
        val rows = SupabaseClient.json.decodeFromString<List<InvitePreview>>(data)
        return rows.firstOrNull()
    }

    /**
     * Joins the invite's room; returns its id. The database enforces
     * expiry and the six-person ceiling.
     */
    suspend fun acceptInvite(token: Uuid): Uuid {
        val data = withAuthRetry {
            client.rpc(
                "accept_invite", body = mapOf("invite_token" to token.lowercased()))
        }
        // The function answers with a bare JSON string, so this decodes a
        // scalar rather than a row.
        return SupabaseClient.json.decodeFromString<Uuid>(data)
    }

    // MARK: - Push notifications (S19)

    /**
     * This phone, as it holds itself: the token, every room's switches, the
     * quiet hours and the zone they are kept in. All of it, every time — it
     * is small, and a partial update is a second way to be wrong.
     */
    suspend fun registerPushDevice(
        token: String,
        zone: String,
        quietFrom: Int,
        quietUntil: Int,
        rooms: Map<Uuid, RoomNotificationPrefs>,
    ) {
        val body = buildJsonObject {
            put("device_token", token)
            put("device_platform", "android")
            put("apns_environment", "production")
            put("zone", zone)
            put("quiet_from", quietFrom)
            put("quiet_until", quietUntil)
            putJsonObject("room_prefs") {
                rooms.forEach { (roomID, prefs) ->
                    putJsonObject(roomID.lowercased()) {
                        put("notesLeft", prefs.notesLeft)
                        put("cardsOpen", prefs.cardsOpen)
                        put("inTheBook", prefs.whenTheyOpenTheBook)
                        put("thinkingOfYou", prefs.thinkingOfYou)
                    }
                }
            }
            put("live_start", JsonNull)
        }
        withAuthRetry { client.rpcJson("register_push_device", body.toString()) }
    }

    /** Signing out takes this phone off the server's list. */
    suspend fun forgetPushDevice(token: String) {
        runCatching {
            withAuthRetry { client.rpc("forget_push_device", body = mapOf("device_token" to token)) }
        }
    }

    /**
     * The book is open (§4.2) — said on opening and every ten minutes after,
     * never while reading quietly. The server turns an arrival into "Ruth is
     * reading Mark" for whoever asked to hear it.
     */
    suspend fun iAmReading(room: Uuid, reading: Uuid) {
        withAuthRetry {
            client.rpc(
                "i_am_reading",
                body = mapOf("room" to room.lowercased(), "reading" to reading.lowercased()),
            )
        }
    }

    suspend fun iHaveLeft(room: Uuid) {
        withAuthRetry { client.rpc("i_have_left", body = mapOf("room" to room.lowercased())) }
    }

    /** Thinking of you (§4.3), for a phone the room's socket cannot reach. */
    suspend fun thinkOf(room: Uuid, person: Uuid) {
        withAuthRetry {
            client.rpc(
                "think_of",
                body = mapOf("room" to room.lowercased(), "person" to person.lowercased()),
            )
        }
    }

    // MARK: - Push: the room surface this device knows

    suspend fun push(profile: Person, portraitData: ByteArray?) {
        if (portraitData != null) {
            runCatching {
                withAuthRetry {
                    client.uploadPortrait(personID = profile.id, data = portraitData)
                }
            }
        }
        // The remote path is deterministic — <person id>.jpg — so a name
        // edit never clears a portrait uploaded earlier.
        val row = ProfileRow(
            id = profile.id, name = profile.name,
            portraitPath = if (profile.portraitPath == null) {
                null
            } else {
                "${profile.id.lowercased()}.jpg"
            },
            translation = profile.translation.rawValue)
        withAuthRetry {
            client.upsert(
                table = "profiles",
                rowsJson = SupabaseClient.json.encodeToString(listOf(row)))
        }
    }

    /**
     * The memory of an ink you chose in a room (§6.10). Best-effort on
     * purpose: it is a nicety, and a build talking to a project without the
     * table yet must behave exactly as it did before.
     */
    suspend fun rememberInk(ink: Ink, roomID: Uuid, personID: Uuid) {
        runCatching {
            withAuthRetry {
                client.upsert(
                    table = "room_inks",
                    rowsJson = SupabaseClient.json.encodeToString(listOf(
                        RoomInkRow(roomId = roomID, personId = personID, ink = ink.name),
                    )))
            }
        }
    }

    /** The ink this account last chose in this room, if it ever chose one. */
    suspend fun rememberedInk(roomID: Uuid, personID: Uuid): Ink? = runCatching {
        val rows = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<RoomInkRow>>(
                client.select(
                    table = "room_inks",
                    query = listOf(
                        "room_id" to "eq.${roomID.toString().lowercase()}",
                        "person_id" to "eq.${personID.toString().lowercase()}",
                    )))
        }
        rows.firstOrNull()?.let { row -> Ink.entries.firstOrNull { it.name == row.ink } }
    }.getOrNull()

    suspend fun push(room: Room) {
        withAuthRetry {
            client.upsert(
                table = "rooms",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    RoomRow(
                        id = room.id, name = room.name, isPaused = room.isPaused,
                        createdAt = room.createdAt,
                        translation = room.translation.rawValue),
                )))
        }
    }

    suspend fun push(membership: Membership) {
        withAuthRetry {
            client.upsert(
                table = "memberships",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    MembershipRow(
                        id = membership.id, roomId = membership.roomID,
                        personId = membership.personID,
                        ink = membership.ink?.name, joinedAt = membership.joinedAt),
                )),
                onConflict = "room_id,person_id")
        }
    }

    suspend fun push(invite: Invite) {
        withAuthRetry {
            client.upsert(
                table = "invites",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    InviteRow(
                        id = invite.id, roomId = invite.roomID, createdBy = invite.createdBy,
                        createdAt = invite.createdAt, expiresAt = invite.expiresAt),
                )))
        }
    }

    suspend fun push(reading: Reading) {
        withAuthRetry {
            client.upsert(
                table = "readings",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    ReadingRow(
                        id = reading.id, roomId = reading.roomID, bookId = reading.bookID,
                        scale = reading.handiwork.scale.name,
                        startedAt = reading.startedAt, finishedAt = reading.finishedAt,
                        translation = reading.translation.rawValue),
                )))
        }
        withAuthRetry {
            client.upsert(
                table = "fires",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    FireRow(
                        readingId = reading.id,
                        coalDepth = reading.handiwork.coalDepth,
                        lastFuelAt = reading.handiwork.lastFuelAt,
                        restartAt = reading.handiwork.restartAt,
                        stateAtLastFuel = reading.handiwork.stateAtLastFuel.name),
                )))
        }
    }

    /**
     * One reader's feeding, into the rolling window the schema prunes.
     * Duplicates are harmless — the engine counts distinct people, never
     * events.
     */
    suspend fun push(fuel: FuelEvent, readingID: Uuid) {
        withAuthRetry {
            client.upsert(
                table = "fuel_events",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    FuelEventRow(id = Uuid.random(), readingId = readingID, personId = fuel.personID, at = fuel.at),
                )))
        }
    }

    /**
     * Leaving a room (§6.8): the membership goes; notes and highlights
     * stay by design (their sync rides the full engine later).
     */
    suspend fun deleteMembership(roomID: Uuid, personID: Uuid) {
        withAuthRetry {
            client.delete(
                table = "memberships",
                query = listOf(
                    "room_id" to "eq.${roomID.lowercased()}",
                    "person_id" to "eq.${personID.lowercased()}",
                ))
        }
    }

    /**
     * Account deletion (§6.8): the profile row goes, and the cascade
     * takes everything authored by the person that the backend holds.
     * Best-effort on the portrait object first (its policy is the
     * person's own).
     */
    /**
     * Forget the person, and keep what they wrote for the room to decide
     * about (§6.8).
     *
     * This used to `delete` the profiles row, and that one line quietly
     * overruled the question the app had just asked. `notes.author_id` and
     * `highlights.author_id` are both `references public.profiles (id) on
     * delete cascade`, so deleting the profile deleted every note and every
     * highlight the person had ever left — whichever answer they gave to
     * "leave your notes behind?", and in flat contradiction of §6.8's "their
     * highlights: stay, always". S11 needs the same row for a different
     * reason: a departed member's notes must "render normally, with their
     * portrait", and nothing marks them as gone.
     *
     * So the row is blanked rather than removed. The name goes to the
     * app's own word for somebody it has no profile for, the portrait path
     * goes to null, and the portrait object is deleted outright. Nothing
     * personal survives; the rows that hang off the row do.
     *
     * The caller deals with the notes themselves, because the caller is the
     * one that knows the answer to §6.8's question.
     */
    suspend fun forgetProfile(neutralName: String) {
        val userID = userID ?: return
        runCatching {
            withAuthRetry { client.deletePortrait(personID = userID) }
        }
        runCatching {
            withAuthRetry {
                client.upsert(
                    table = "profiles",
                    rowsJson = SupabaseClient.json.encodeToString(
                        listOf(
                            ProfileRow(
                                id = userID,
                                name = neutralName,
                                portraitPath = null,
                                translation = TranslationID.bsb.rawValue,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    /** A marked quiet day banks the fire for the room — on every device. */
    suspend fun push(quietDay: QuietDay) {
        withAuthRetry {
            client.upsert(
                table = "quiet_days",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    QuietDayRow(
                        id = quietDay.id, roomId = quietDay.roomID, personId = quietDay.personID,
                        localDate = quietDay.localDate, timeZone = quietDay.timeZoneID,
                        markedAt = quietDay.markedAt),
                )),
                onConflict = "room_id,person_id,local_date")
        }
    }

    suspend fun push(note: Note, audioFile: File? = null) {
        if (audioFile != null && audioFile.exists()) {
            withAuthRetry {
                client.uploadAudio(readingID = note.readingID, noteID = note.id, file = audioFile)
            }
        }
        withAuthRetry {
            client.upsert(
                table = "notes",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    NoteRow(
                        id = note.id,
                        readingId = note.readingID,
                        authorId = note.authorID,
                        bookId = note.verse.bookID,
                        chapter = note.verse.chapter,
                        verse = note.verse.verse,
                        kind = note.kind.name,
                        body = note.body,
                        audioPath = note.audioPath,
                        waveform = note.waveform,
                        transcript = note.transcript,
                        transcriptState = note.transcriptState?.name,
                        createdAt = note.createdAt,
                    )
                )),
                onConflict = "id"
            )
        }
    }

    suspend fun push(noteFoundID: Uuid, personID: Uuid) {
        withAuthRetry {
            client.upsert(
                table = "note_founds",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    NoteFoundRow(
                        noteId = noteFoundID,
                        personId = personID,
                        foundAt = kotlin.time.Clock.System.now(),
                    )
                )),
                onConflict = "note_id,person_id"
            )
        }
    }

    /**
     * Take a note back, everywhere (S04 — "no tombstone").
     *
     * @param readingID and [voice] together say whether there is a recording
     *   behind it. The object goes first and the row second, in that order
     *   and each in its own `runCatching`: `voice_notes_delete` checks the
     *   note's author against the `notes` row, so deleting the row first
     *   would make the object permanently unreachable rather than deleted.
     *   And one failing must not stop the other — a row left behind is a
     *   note that comes back, an object left behind is a recording of
     *   somebody's voice that does not go away.
     */
    suspend fun deleteNote(id: Uuid, readingID: Uuid? = null, voice: Boolean = false) {
        if (voice && readingID != null) {
            runCatching { withAuthRetry { client.deleteAudio(readingID = readingID, noteID = id) } }
        }
        withAuthRetry {
            client.delete(
                table = "notes",
                query = listOf("id" to "eq.${id.lowercased()}"))
        }
    }

    suspend fun push(highlight: Highlight) {
        withAuthRetry {
            client.upsert(
                table = "highlights",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    HighlightRow(
                        id = highlight.id,
                        readingId = highlight.readingID,
                        authorId = highlight.authorID,
                        bookId = highlight.range.bookID,
                        chapter = highlight.range.chapter,
                        startVerse = highlight.range.startVerse,
                        endVerse = highlight.range.endVerse,
                        ink = highlight.ink.name,
                        createdAt = highlight.createdAt,
                        startChar = highlight.range.startChar,
                        endChar = highlight.range.endChar,
                        charTranslation = highlight.range.charTranslation?.rawValue,
                    )
                )),
                onConflict = "id"
            )
        }
    }

    suspend fun deleteHighlight(id: Uuid) {
        withAuthRetry {
            client.delete(
                table = "highlights",
                query = listOf("id" to "eq.${id.lowercased()}"))
        }
    }

    suspend fun push(position: ReadingPosition) {
        withAuthRetry {
            client.upsert(
                table = "positions",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    PositionRow(
                        readingId = position.readingID,
                        personId = position.personID,
                        chapter = position.chapter,
                        verse = position.verse,
                        updatedAt = position.updatedAt,
                    )
                )),
                onConflict = "reading_id,person_id"
            )
        }
    }

    /**
     * The room's ribbon. One row per reading, so the conflict target is the
     * reading alone — anybody in the room may move it, and the last person to
     * set the book down is where it is.
     */
    suspend fun push(ribbon: Ribbon) {
        withAuthRetry {
            client.upsert(
                table = "ribbons",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    RibbonRow(
                        readingId = ribbon.readingID,
                        personId = ribbon.personID,
                        chapter = ribbon.chapter,
                        verse = ribbon.verse,
                        placedAt = ribbon.placedAt,
                    )
                )),
                onConflict = "reading_id"
            )
        }
    }

    suspend fun push(card: ReflectionCard) {
        withAuthRetry {
            client.upsert(
                table = "cards",
                rowsJson = SupabaseClient.json.encodeToString(listOf(
                    CardRow(
                        id = card.id,
                        readingId = card.readingID,
                        chapter = card.chapter,
                        question = card.question,
                        state = card.state.name,
                        openedAt = card.openedAt,
                        createdAt = card.openedAt ?: kotlin.time.Clock.System.now(),
                    )
                )),
                onConflict = "id"
            )
        }
    }

    suspend fun push(cardAnswer: CardAnswerRow) {
        withAuthRetry {
            client.upsert(
                table = "card_answers",
                rowsJson = SupabaseClient.json.encodeToString(listOf(cardAnswer)),
                onConflict = "card_id,person_id"
            )
        }
    }

    suspend fun downloadAudio(readingID: Uuid, noteID: Uuid, to: File) {
        withAuthRetry {
            client.downloadAudio(readingID = readingID, noteID = noteID, destination = to)
        }
    }

    // MARK: - Pull: every room I'm in

    suspend fun pullRooms(): RoomGraph {
        val userID = userID ?: return RoomGraph()

        val mine = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<MembershipRow>>(
                client.select(
                    table = "memberships",
                    query = listOf("person_id" to "eq.${userID.lowercased()}")))
        }
        val roomIDs = mine.map { it.roomId }
        if (roomIDs.isEmpty()) return RoomGraph()
        val roomList = "in.(${roomIDs.joinToString(",") { it.lowercased() }})"

        val rooms = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<RoomRow>>(
                client.select(table = "rooms", query = listOf("id" to roomList)))
        }
        val memberships = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<MembershipRow>>(
                client.select(table = "memberships", query = listOf("room_id" to roomList)))
        }
        val personIDs = memberships.map { it.personId }.toSet()
        var profiles: List<ProfileRow> = emptyList()
        if (personIDs.isNotEmpty()) {
            val personList = "in.(${personIDs.joinToString(",") { it.lowercased() }})"
            profiles = withAuthRetry {
                SupabaseClient.json.decodeFromString<List<ProfileRow>>(
                    client.select(table = "profiles", query = listOf("id" to personList)))
            }
        }
        val readings = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<ReadingRow>>(
                client.select(table = "readings", query = listOf("room_id" to roomList)))
        }
        val quietDays = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<QuietDayRow>>(
                client.select(table = "quiet_days", query = listOf("room_id" to roomList)))
        }
        // The link is the whole mechanism (S15), and there should be one of
        // it per room: without this, a second device has no live invite to
        // find and mints another rather than re-offering the one that is
        // already out there.
        val invites = runCatching {
            withAuthRetry {
                SupabaseClient.json.decodeFromString<List<InviteRow>>(
                    client.select(table = "invites", query = listOf("room_id" to roomList)))
            }
        }.getOrDefault(emptyList())
        val readingIDs = readings.map { it.id }
        var fires: List<FireRow> = emptyList()
        var fuelEvents: List<FuelEventRow> = emptyList()
        var notes: List<NoteRow> = emptyList()
        var noteFounds: List<NoteFoundRow> = emptyList()
        var highlights: List<HighlightRow> = emptyList()
        var notesComplete = false
        var highlightsComplete = false
        var positions: List<PositionRow> = emptyList()
        var ribbons: List<RibbonRow> = emptyList()
        var cards: List<CardRow> = emptyList()
        var cardAnswers: List<CardAnswerRow> = emptyList()

        if (readingIDs.isNotEmpty()) {
            val readingList = "in.(${readingIDs.joinToString(",") { it.lowercased() }})"
            fires = withAuthRetry {
                SupabaseClient.json.decodeFromString<List<FireRow>>(
                    client.select(table = "fires", query = listOf("reading_id" to readingList)))
            }
            fuelEvents = withAuthRetry {
                SupabaseClient.json.decodeFromString<List<FuelEventRow>>(
                    client.select(table = "fuel_events", query = listOf("reading_id" to readingList)))
            }
            // The two that the merge now *prunes* against carry whether they
            // actually arrived, because an empty list from a failed request
            // would otherwise read as "the room has none" and take every note
            // in it off the phone.
            withAuthRetry {
                runCatching {
                    SupabaseClient.json.decodeFromString<List<NoteRow>>(
                        client.select(table = "notes", query = listOf("reading_id" to readingList)))
                }
            }.onSuccess { notes = it; notesComplete = true }
            withAuthRetry {
                runCatching {
                    SupabaseClient.json.decodeFromString<List<HighlightRow>>(
                        client.select(
                            table = "highlights",
                            query = listOf("reading_id" to readingList),
                        ),
                    )
                }
            }.onSuccess { highlights = it; highlightsComplete = true }
            positions = withAuthRetry {
                runCatching {
                    SupabaseClient.json.decodeFromString<List<PositionRow>>(
                        client.select(table = "positions", query = listOf("reading_id" to readingList)))
                }.getOrDefault(emptyList())
            }
            ribbons = withAuthRetry {
                runCatching {
                    SupabaseClient.json.decodeFromString<List<RibbonRow>>(
                        client.select(table = "ribbons", query = listOf("reading_id" to readingList)))
                }.getOrDefault(emptyList())
            }
            cards = withAuthRetry {
                runCatching {
                    SupabaseClient.json.decodeFromString<List<CardRow>>(
                        client.select(table = "cards", query = listOf("reading_id" to readingList)))
                }.getOrDefault(emptyList())
            }

            val noteIDs = notes.map { it.id }
            if (noteIDs.isNotEmpty()) {
                val noteList = "in.(${noteIDs.joinToString(",") { it.lowercased() }})"
                noteFounds = withAuthRetry {
                    runCatching {
                        SupabaseClient.json.decodeFromString<List<NoteFoundRow>>(
                            client.select(table = "note_founds", query = listOf("note_id" to noteList)))
                    }.getOrDefault(emptyList())
                }
            }

            val cardIDs = cards.map { it.id }
            if (cardIDs.isNotEmpty()) {
                val cardList = "in.(${cardIDs.joinToString(",") { it.lowercased() }})"
                cardAnswers = withAuthRetry {
                    runCatching {
                        SupabaseClient.json.decodeFromString<List<CardAnswerRow>>(
                            client.select(table = "card_answers", query = listOf("card_id" to cardList)))
                    }.getOrDefault(emptyList())
                }
            }
        }
        // Swift mutates one `graph` as it goes; RoomGraph is immutable here,
        // so the pieces are gathered above and assembled once. Same order,
        // same requests, same partial-graph outcomes.
        return RoomGraph(
            rooms = rooms, memberships = memberships, profiles = profiles,
            readings = readings, fires = fires, fuelEvents = fuelEvents,
            quietDays = quietDays, invites = invites, notes = notes,
            noteFounds = noteFounds, highlights = highlights,
            positions = positions, ribbons = ribbons, cards = cards, cardAnswers = cardAnswers,
            notesComplete = notesComplete, highlightsComplete = highlightsComplete)
    }

    /**
     * The face, asked for conditionally (§2.7). A network failure reads as
     * `Unchanged`: the device keeps the face it has, which is what it would
     * have done anyway.
     */
    suspend fun fetchPortrait(
        personID: Uuid,
        ifNoneMatch: String?,
    ): SupabaseClient.PortraitFetch =
        runCatching {
            withAuthRetry {
                client.downloadPortrait(personID = personID, ifNoneMatch = ifNoneMatch)
            }
        }.getOrNull() ?: SupabaseClient.PortraitFetch.Unchanged

    /**
     * The account's own profile row, if the account has one — the seam
     * where a fresh device learns it belongs to an older person.
     */
    suspend fun fetchOwnProfile(): ProfileRow? {
        val userID = userID ?: return null
        val rows = withAuthRetry {
            SupabaseClient.json.decodeFromString<List<ProfileRow>>(
                client.select(
                    table = "profiles",
                    query = listOf("id" to "eq.${userID.lowercased()}")))
        }
        return rows.firstOrNull()
    }

    // MARK: - Plumbing


    /**
     * Access tokens are short-lived; a 401 means refresh and retry once.
     * A refresh the server itself refuses (revoked or rotated-away
     * token) means this session is dead — clear it, so signed-out is a
     * state the interface can see and offer sign-in for, never a
     * permanent silent failure. A network failure clears nothing.
     */
    private suspend fun <T> withAuthRetry(work: suspend () -> T): T {
        return try {
            work()
        } catch (error: SupabaseError.Http) {
            // Swift pattern-matches `SupabaseError.http(401, _)` in the catch
            // clause itself; Kotlin catches the case and re-throws the rest.
            if (error.status != 401) throw error
            try {
                client.refresh()
            } catch (refreshError: SupabaseError) {
                if (refreshError is SupabaseError.Http && refreshError.status in 400..<500) {
                    signOut()
                }
                throw refreshError
            }
            client.currentSession()?.let { refreshed ->
                withContext(Dispatchers.IO) { sessions.save(refreshed) }
                userID = refreshed.user.id
            }
            work()
        }
    }

    // MARK: - Rows (PostgREST shapes; snake_case via the client's coders)

    @Serializable
    data class ProfileRow(
        val id: Uuid,
        val name: String,
        val portraitPath: String? = null,
        val translation: String,
    )

    @Serializable
    data class RoomInkRow(
        val roomId: Uuid,
        val personId: Uuid,
        val ink: String,
    )

    @Serializable
    data class RoomRow(
        val id: Uuid,
        val name: String? = null,
        val isPaused: Boolean,
        val createdAt: Instant,
        /** The words this room reads, shared by everyone in it (A42). Null
         *  from a client that predates the column, and from iOS. */
        val translation: String? = null,
    )

    @Serializable
    data class MembershipRow(
        val id: Uuid,
        val roomId: Uuid,
        val personId: Uuid,
        val ink: String? = null,
        val joinedAt: Instant,
    )

    @Serializable
    data class InviteRow(
        val id: Uuid,
        val roomId: Uuid,
        val createdBy: Uuid,
        val createdAt: Instant,
        val expiresAt: Instant,
    )

    @Serializable
    data class ReadingRow(
        val id: Uuid,
        val roomId: Uuid,
        val bookId: String,
        val scale: String,
        val startedAt: Instant,
        val finishedAt: Instant? = null,
        /** The words this book was read in. A finished one keeps them (S11). */
        val translation: String? = null,
    )

    @Serializable
    data class FireRow(
        val readingId: Uuid,
        val coalDepth: Double,
        val lastFuelAt: Instant? = null,
        val restartAt: Instant? = null,
        val stateAtLastFuel: String,
    )

    @Serializable
    data class FuelEventRow(
        val id: Uuid,
        val readingId: Uuid,
        val personId: Uuid,
        val at: Instant,
    )

    /**
     * local_date is a bare Postgres date ("2026-09-01") — a String here,
     * matching QuietDay.localDate; the timestamp decoder would refuse it.
     */
    @Serializable
    data class QuietDayRow(
        val id: Uuid,
        val roomId: Uuid,
        val personId: Uuid,
        val localDate: String,
        val timeZone: String,
        val markedAt: Instant,
    )

    @Serializable
    data class NoteRow(
        val id: Uuid,
        val readingId: Uuid,
        val authorId: Uuid,
        val bookId: String,
        val chapter: Int,
        val verse: Int,
        val kind: String,
        val body: String? = null,
        val audioPath: String? = null,
        val waveform: List<Float>? = null,
        val transcript: String? = null,
        val transcriptState: String? = null,
        val createdAt: Instant,
    )

    @Serializable
    data class NoteFoundRow(
        val noteId: Uuid,
        val personId: Uuid,
        val foundAt: Instant,
    )

    @Serializable
    data class HighlightRow(
        val id: Uuid,
        val readingId: Uuid,
        val authorId: Uuid,
        val bookId: String,
        val chapter: Int,
        val startVerse: Int,
        val endVerse: Int,
        val ink: String,
        val createdAt: Instant,
        /**
         * A mark on part of a verse (S06), and the translation its two offsets
         * were measured in. All three are null for a mark on whole verses,
         * which is every mark made before A41g and every mark iOS makes — the
         * columns are nullable and nothing that does not know about them has
         * to change.
         */
        val startChar: Int? = null,
        val endChar: Int? = null,
        val charTranslation: String? = null,
    )

    @Serializable
    data class PositionRow(
        val readingId: Uuid,
        val personId: Uuid,
        val chapter: Int,
        val verse: Int,
        val updatedAt: Instant,
    )

    @Serializable
    data class RibbonRow(
        val readingId: Uuid,
        val personId: Uuid,
        val chapter: Int,
        val verse: Int,
        val placedAt: Instant,
    )

    @Serializable
    data class CardRow(
        val id: Uuid,
        val readingId: Uuid,
        val chapter: Int,
        val question: String,
        val state: String,
        val openedAt: Instant? = null,
        val createdAt: Instant,
    )

    @Serializable
    data class CardAnswerRow(
        val cardId: Uuid,
        val personId: Uuid,
        val answer: String,
        val createdAt: Instant,
    )

    companion object {

        /**
         * The `exp` claim, read without a JWT library: the payload is the
         * middle base64url segment and its expiry is the only field this
         * needs. An unreadable token is treated as fine — the server is the
         * authority on that, and guessing wrong here would refresh on every
         * join.
         */
        internal fun expiresSoon(token: String, withinSeconds: Long = 60): Boolean {
            val parts = token.split(".")
            if (parts.size != 3) return false
            // java.util.Base64 rather than android.util.Base64: the same
            // decoder on every API this app supports, and one a plain JVM
            // test can run (SessionTokenTest).
            val payload = runCatching {
                Base64.getUrlDecoder().decode(parts[1]).toString(Charsets.UTF_8)
            }.getOrNull() ?: return false
            val exp = runCatching {
                SupabaseClient.json.parseToJsonElement(payload)
                    .jsonObject["exp"]?.jsonPrimitive?.longOrNull
            }.getOrNull() ?: return false
            return exp - (System.currentTimeMillis() / 1000) < withinSeconds
        }
        /**
         * Restores a persisted session, if one exists. Always returns a
         * service — signed out is a state, not an absence.
         */
        suspend fun restore(context: Context): RemoteSync =
            restore(SessionStore(context.applicationContext))

        /** The seam a test can hand its own store to. */
        suspend fun restore(sessions: SessionStore): RemoteSync {
            val sync = RemoteSync(sessions)
            // Unlike the Keychain, reading this involves the hardware
            // keystore and a file, so it is done off the main thread.
            val session = withContext(Dispatchers.IO) { sessions.load() }
            if (session != null) {
                sync.client.restore(session)
                sync.userID = session.user.id
                sync.email = session.user.email
                // A relaunch has to remember which kind of session it holds,
                // or the first screen after it would offer a passkey the
                // account cannot take.
                sync.signedInWithAuth0 = session.isAuth0
            }
            return sync
        }
    }
}

/**
 * Swift writes `uuidString.lowercased()` at every PostgREST call site
 * because `UUID.uuidString` is uppercase. Kotlin's `Uuid.toString()` is
 * already lowercase; the call is kept, as it is in Invite.url(), so the two
 * implementations cannot drift if either ever changes.
 */
private fun Uuid.lowercased(): String = toString().lowercase()
