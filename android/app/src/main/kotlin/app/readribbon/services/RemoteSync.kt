@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import android.content.Context
import app.readribbon.core.FuelEvent
import app.readribbon.core.Ink
import app.readribbon.core.Invite
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.QuietDay
import app.readribbon.core.Reading
import app.readribbon.core.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The sign-in thread (§6.10) and the room surface of sync: accounts,
// invites, joining, and the graph a room renders from — rooms, memberships,
// profiles, readings, fires, and the rolling fuel window. Notes, highlights
// and positions still live on-device only; they ride the full sync engine,
// which is the next piece of work (docs/deviations.md).
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
        return session.user.id
    }

    // MARK: Passkeys (§6.10)

    /**
     * Register a passkey for the account that is already signed in.
     *
     * @param context an Activity context — the system sheet needs a window.
     */
    suspend fun registerPasskey(context: Context) {
        val challenge = client.passkeyRegistrationOptions()
        val credential = Passkeys(context).register(context, challenge.optionsJson)
        client.verifyPasskeyRegistration(
            challengeID = challenge.challengeID, credentialJson = credential)
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
        return session.user.id
    }

    suspend fun signOut() {
        client.signOut()
        withContext(Dispatchers.IO) { sessions.clear() }
        userID = null
        email = null
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
                    RoomRow(id = room.id, name = room.name, isPaused = room.isPaused, createdAt = room.createdAt),
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
                        startedAt = reading.startedAt, finishedAt = reading.finishedAt),
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
    suspend fun deleteAccountData() {
        val userID = userID ?: return
        runCatching {
            withAuthRetry { client.deletePortrait(personID = userID) }
        }
        runCatching {
            withAuthRetry {
                client.delete(
                    table = "profiles",
                    query = listOf("id" to "eq.${userID.lowercased()}"))
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
        val readingIDs = readings.map { it.id }
        var fires: List<FireRow> = emptyList()
        var fuelEvents: List<FuelEventRow> = emptyList()
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
        }
        // Swift mutates one `graph` as it goes; RoomGraph is immutable here,
        // so the pieces are gathered above and assembled once. Same order,
        // same requests, same partial-graph outcomes.
        return RoomGraph(
            rooms = rooms, memberships = memberships, profiles = profiles,
            readings = readings, fires = fires, fuelEvents = fuelEvents,
            quietDays = quietDays)
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

    companion object {
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
