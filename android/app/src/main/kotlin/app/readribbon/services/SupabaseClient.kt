@file:OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)

package app.readribbon.services

import app.readribbon.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// A small, typed client for the pieces of Supabase Ribbon uses: email-code
// auth (GoTrue), PostgREST reads/writes, and storage. Deliberately
// hand-rolled rather than an SDK dependency — the same call the iOS build
// made, for the same reason: the surface we need is narrow, and every
// request it can make is visible in this file.
//
// It talks to the same project the iOS app does, so the wire format here is
// not a private choice: column names, timestamp shapes and the upsert
// conflict keys all have to match what the other client writes.

@Serializable
data class SupabaseSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: SupabaseUser,
)

@Serializable
data class SupabaseUser(
    val id: Uuid,
    val email: String? = null,
)

sealed class SupabaseError(message: String) : Exception(message) {
    class Http(val status: Int, val body: String) : SupabaseError("HTTP $status: $body")
    data object NotSignedIn : SupabaseError("Not signed in") {
        private fun readResolve(): Any = NotSignedIn
    }
}

class SupabaseClient(
    private val base: String = SupabaseConfig.URL,
    private val key: String = SupabaseConfig.PUBLISHABLE_KEY,
) {

    private val lock = Mutex()
    private var session: SupabaseSession? = null

    // MARK: Auth — an emailed code, no passwords (§6.10)

    @Serializable
    private data class OtpBody(val email: String, @SerialName("create_user") val createUser: Boolean)

    /**
     * Sends the sign-in code. The email field never leaks into a third-party
     * account list — this is first-party mail.
     */
    suspend fun sendCode(to: String) {
        post(
            path = "auth/v1/otp",
            body = json.encodeToString(OtpBody(email = to, createUser = true)),
            authenticated = false,
        )
    }

    suspend fun verifyCode(email: String, code: String): SupabaseSession {
        val body = json.encodeToString(
            mapOf("type" to "email", "email" to email, "token" to code),
        )
        val data = post(path = "auth/v1/verify", body = body, authenticated = false)
        val decoded = json.decodeFromString<SupabaseSession>(data)
        lock.withLock { session = decoded }
        return decoded
    }

    suspend fun restore(session: SupabaseSession) = lock.withLock { this.session = session }

    suspend fun signOut() = lock.withLock { session = null }

    /** The live session, for persisting across launches — the tokens are
     *  credentials, not state. */
    suspend fun currentSession(): SupabaseSession? = lock.withLock { session }

    suspend fun userID(): Uuid? = currentSession()?.user?.id

    suspend fun refresh() {
        val current = currentSession() ?: throw SupabaseError.NotSignedIn
        val data = post(
            path = "auth/v1/token",
            query = listOf("grant_type" to "refresh_token"),
            body = json.encodeToString(mapOf("refresh_token" to current.refreshToken)),
            authenticated = false,
        )
        val decoded = json.decodeFromString<SupabaseSession>(data)
        lock.withLock { session = decoded }
    }

    // MARK: PostgREST

    /** GET a table with PostgREST filters, as raw JSON for the caller to decode. */
    suspend fun select(table: String, query: List<Pair<String, String>>): String =
        request(
            method = "GET",
            url = url("rest/v1/$table", query),
            authenticated = true,
        )

    /**
     * Upsert rows; last-write-wins per object is safe because objects are
     * single-author (§13). [onConflict] names a unique constraint's columns
     * when the merge key isn't the primary key (memberships merge on
     * room_id,person_id — a joiner's row was minted server-side with its own
     * id).
     */
    suspend fun upsert(table: String, rowsJson: String, onConflict: String? = null) {
        val query = onConflict?.let { listOf("on_conflict" to it) } ?: emptyList()
        request(
            method = "POST",
            url = url("rest/v1/$table", query),
            body = rowsJson.toByteArray(),
            authenticated = true,
            headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    suspend fun delete(table: String, query: List<Pair<String, String>>) {
        request(method = "DELETE", url = url("rest/v1/$table", query), authenticated = true)
    }

    /** Call a database function (used for accept_invite). */
    suspend fun rpc(function: String, body: Map<String, String>): String =
        post(
            path = "rest/v1/rpc/$function",
            body = json.encodeToString(body),
            authenticated = true,
        )

    /**
     * Call an anon-callable function (invite_preview — the join screen shows
     * who is inviting before any account exists).
     */
    suspend fun rpcAnon(function: String, body: Map<String, String>): String =
        post(
            path = "rest/v1/rpc/$function",
            body = json.encodeToString(body),
            authenticated = false,
        )

    // MARK: Storage — voice notes, room-scoped paths

    suspend fun uploadAudio(readingID: Uuid, noteID: Uuid, file: File) {
        request(
            method = "POST",
            url = url("storage/v1/object/voice-notes/$readingID/$noteID.m4a"),
            body = withContext(Dispatchers.IO) { file.readBytes() },
            authenticated = true,
            headers = mapOf("Content-Type" to "audio/mp4"),
        )
    }

    suspend fun downloadAudio(readingID: Uuid, noteID: Uuid, destination: File) {
        val bytes = requestBytes(
            method = "GET",
            url = url("storage/v1/object/authenticated/voice-notes/$readingID/$noteID.m4a"),
            authenticated = true,
        )
        withContext(Dispatchers.IO) { destination.writeBytes(bytes) }
    }

    // MARK: Storage — portraits, one per person (presence is faces, §2.7)

    suspend fun uploadPortrait(personID: Uuid, data: ByteArray) {
        request(
            method = "POST",
            url = url("storage/v1/object/portraits/$personID.jpg"),
            body = data,
            authenticated = true,
            headers = mapOf("Content-Type" to "image/jpeg", "x-upsert" to "true"),
        )
    }

    suspend fun downloadPortrait(personID: Uuid): ByteArray = requestBytes(
        method = "GET",
        url = url("storage/v1/object/authenticated/portraits/$personID.jpg"),
        authenticated = true,
    )

    suspend fun deletePortrait(personID: Uuid) {
        request(
            method = "DELETE",
            url = url("storage/v1/object/portraits/$personID.jpg"),
            authenticated = true,
        )
    }

    // MARK: Plumbing

    private fun url(path: String, query: List<Pair<String, String>> = emptyList()): String {
        val q = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.joinToString("&") { (name, value) ->
                "${encode(name)}=${encode(value)}"
            }
        }
        return "$base/$path$q"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun post(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String,
        authenticated: Boolean,
    ): String = request(
        method = "POST",
        url = url(path, query),
        body = body.toByteArray(),
        authenticated = authenticated,
    )

    private suspend fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        authenticated: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): String = requestBytes(method, url, body, authenticated, headers).decodeToString()

    private suspend fun requestBytes(
        method: String,
        url: String,
        body: ByteArray? = null,
        authenticated: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val bearer = if (authenticated) {
            (currentSession() ?: throw SupabaseError.NotSignedIn).accessToken
        } else {
            null
        }
        return withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("apikey", key)
                setRequestProperty("Content-Type", "application/json")
                bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = runCatching {
                        connection.errorStream?.readBytes()?.decodeToString()
                    }.getOrNull().orEmpty()
                    throw SupabaseError.Http(status, error)
                }
                connection.inputStream.use { it.readBytes() }
            } catch (io: IOException) {
                throw SupabaseError.Http(0, io.message.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        /**
         * PostgREST speaks snake_case, and its timestamps carry fractional
         * seconds while GoTrue's do not.
         *
         * Kotlin needs no custom decoder for that second problem the way
         * Swift did: `Instant.parse` accepts both shapes, so the whole
         * fractional/whole-second dance on the iOS side has no counterpart
         * here.
         */
        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
