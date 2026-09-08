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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    @SerialName("is_auth0") val isAuth0: Boolean = false,
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

    /**
     * Sets an authenticated session using an Auth0-issued ID token.
     * Supabase validates this token using Auth0's OIDC Discovery JWKS.
     */
    suspend fun setAuth0Session(idToken: String, userUuid: Uuid, email: String? = null, refreshToken: String = ""): SupabaseSession {
        val auth0Session = SupabaseSession(
            accessToken = idToken,
            refreshToken = refreshToken,
            user = SupabaseUser(id = userUuid, email = email),
            isAuth0 = true,
        )
        lock.withLock { session = auth0Session }
        return auth0Session
    }

    suspend fun restore(session: SupabaseSession) = lock.withLock { this.session = session }

    suspend fun signOut() = lock.withLock { session = null }

    /** The live session, for persisting across launches — the tokens are
     *  credentials, not state. */
    suspend fun currentSession(): SupabaseSession? = lock.withLock { session }

    suspend fun userID(): Uuid? = currentSession()?.user?.id

    suspend fun refresh() {
        val current = currentSession() ?: throw SupabaseError.NotSignedIn
        if (current.isAuth0) {
            if (current.refreshToken.isBlank()) return
            val (newIdToken, newRefreshToken) = Auth0Service.refreshTokens(current.refreshToken)
            val updated = current.copy(accessToken = newIdToken, refreshToken = newRefreshToken)
            lock.withLock { session = updated }
            return
        }
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

    /**
     * The face, asked for conditionally.
     *
     * The object's path is `<person id>.jpg` and never changes, so nothing
     * in the profile row can say the bytes behind it did. The object's own
     * tag can: offer the one this device stored, and a 304 with no body is
     * the server saying the face is still the face.
     *
     * Its own connection rather than [requestBytes], which treats every
     * status outside 200..299 as a failure — and 304 is not one.
     */
    suspend fun downloadPortrait(personID: Uuid, ifNoneMatch: String?): PortraitFetch {
        val bearer = (currentSession() ?: throw SupabaseError.NotSignedIn).accessToken
        val target = url("storage/v1/object/authenticated/portraits/$personID.jpg")
        return withContext(Dispatchers.IO) {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                // HttpURLConnection would follow its own cache and hand back
                // 200 with the old bytes, hiding the 304 this turns on.
                useCaches = false
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $bearer")
                ifNoneMatch?.let { setRequestProperty("If-None-Match", it) }
            }
            try {
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> PortraitFetch.Unchanged
                    HttpURLConnection.HTTP_NOT_FOUND -> PortraitFetch.Missing
                    in 200..299 -> PortraitFetch.Changed(
                        data = connection.inputStream.use { it.readBytes() },
                        etag = connection.getHeaderField("ETag"),
                    )
                    else -> {
                        val error = runCatching {
                            connection.errorStream?.readBytes()?.decodeToString()
                        }.getOrNull().orEmpty()
                        throw SupabaseError.Http(status, error)
                    }
                }
            } catch (io: IOException) {
                throw SupabaseError.Http(0, io.message.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }

    /** What a conditional portrait download found. */
    sealed interface PortraitFetch {
        /** The server's copy is the copy this device already has. */
        data object Unchanged : PortraitFetch

        /** A different face, and the tag to offer next time. */
        data class Changed(val data: ByteArray, val etag: String?) : PortraitFetch {
            // A ByteArray in a data class compares by identity, which would
            // make two equal faces unequal. Nothing here compares one, and
            // the compiler is right to insist the choice be made explicit.
            override fun equals(other: Any?): Boolean = this === other

            override fun hashCode(): Int = System.identityHashCode(this)
        }

        /** No portrait behind the row — deleted, or never uploaded. */
        data object Missing : PortraitFetch
    }

    suspend fun deletePortrait(personID: Uuid) {
        request(
            method = "DELETE",
            url = url("storage/v1/object/portraits/$personID.jpg"),
            authenticated = true,
        )
    }

    // MARK: Passkeys (§6.10 — "a passkey where available")
    //
    // Supabase Auth's own WebAuthn support, through its two-step API: the
    // server hands out a challenge and the WebAuthn options that go with it,
    // CredentialManager runs the ceremony, and the signed result comes back
    // here. The options and the credential are passed through as JSON rather
    // than modelled: they are the W3C shapes, they are the platform's to read
    // and to produce, and anything this client understood about them would
    // only be a second place for them to be wrong.

    /** A challenge, and the WebAuthn options that belong to it. */
    data class PasskeyChallenge(val challengeID: String, val optionsJson: String)

    /** Start registering a passkey for the account that is signed in. */
    suspend fun passkeyRegistrationOptions(): PasskeyChallenge =
        passkeyChallenge("auth/v1/passkeys/registration/options", authenticated = true)

    /**
     * Finish registering. The account keeps the passkey; the session is
     * unchanged, because it was already signed in.
     */
    suspend fun verifyPasskeyRegistration(challengeID: String, credentialJson: String) {
        postPasskey(
            path = "auth/v1/passkeys/registration/verify",
            challengeID = challengeID, credentialJson = credentialJson, authenticated = true)
    }

    /**
     * Start signing in with a passkey. Discoverable credentials: no email is
     * asked for, because the authenticator already knows which account this
     * is.
     */
    suspend fun passkeyAuthenticationOptions(): PasskeyChallenge =
        passkeyChallenge("auth/v1/passkeys/authentication/options", authenticated = false)

    /** Finish signing in. This is the call that returns a session. */
    suspend fun verifyPasskeyAuthentication(
        challengeID: String,
        credentialJson: String,
    ): SupabaseSession {
        val body = postPasskey(
            path = "auth/v1/passkeys/authentication/verify",
            challengeID = challengeID, credentialJson = credentialJson, authenticated = false)
        val next = json.decodeFromString<SupabaseSession>(body)
        session = next
        return next
    }

    private suspend fun passkeyChallenge(
        path: String,
        authenticated: Boolean,
    ): PasskeyChallenge {
        val body = if (authenticated) {
            request(method = "POST", url = url(path), authenticated = true)
        } else {
            request(
                method = "POST", url = url(path), authenticated = false,
                // Signing in has no session to bear; the publishable key is
                // the whole of the credential, as it is for the emailed code.
                headers = mapOf("apikey" to key))
        }
        val root = Json.parseToJsonElement(body).jsonObject
        val challengeID = root["challenge_id"]?.jsonPrimitive?.content
            ?: throw SupabaseError.Http(0, body)
        val options = root["options"] ?: throw SupabaseError.Http(0, body)
        return PasskeyChallenge(challengeID = challengeID, optionsJson = options.toString())
    }

    private suspend fun postPasskey(
        path: String,
        challengeID: String,
        credentialJson: String,
        authenticated: Boolean,
    ): String {
        val payload = buildJsonObject {
            put("challenge_id", JsonPrimitive(challengeID))
            put("credential", Json.parseToJsonElement(credentialJson))
        }.toString()
        return if (authenticated) {
            request(
                method = "POST", url = url(path), body = payload.toByteArray(),
                authenticated = true)
        } else {
            request(
                method = "POST", url = url(path), body = payload.toByteArray(),
                authenticated = false, headers = mapOf("apikey" to key))
        }
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
