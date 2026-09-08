package app.readribbon.services

import android.app.Activity
import app.readribbon.data.Auth0Config
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.Uuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Result of an Auth0 credentials exchange.
 */
data class Auth0User(
    val idToken: String,
    val userUuid: Uuid,
    val email: String?,
    val refreshToken: String = "",
)

sealed class Auth0Exception(message: String) : Exception(message) {
    data object NotConfigured : Auth0Exception("Auth0 domain or client ID is not configured")
    data object Cancelled : Auth0Exception("Auth0 login was cancelled by user")
    class Failed(val details: String) : Auth0Exception(details)
}

object Auth0Service {
    private const val NAMESPACE_DNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

    /**
     * Launch the Auth0 Universal Login flow using Android Custom Tabs.
     */
    suspend fun login(activity: Activity): Auth0User {
        if (!Auth0Config.isConfigured) {
            throw Auth0Exception.NotConfigured
        }

        val account = Auth0.getInstance(Auth0Config.CLIENT_ID, Auth0Config.DOMAIN)

        val credentials = suspendCancellableCoroutine<Credentials> { continuation ->
            WebAuthProvider.login(account)
                .withScheme(Auth0Config.SCHEME)
                .withScope("openid profile email offline_access")
                .start(activity, object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        continuation.resume(result)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        if (error.isCanceled) {
                            continuation.resumeWithException(Auth0Exception.Cancelled)
                        } else {
                            continuation.resumeWithException(
                                Auth0Exception.Failed(error.getDescription() ?: error.message ?: "Authentication failed")
                            )
                        }
                    }
                })
        }

        val idToken = credentials.idToken
        val parsed = parseIdToken(idToken)
        return Auth0User(
            idToken = idToken,
            userUuid = parsed.userUuid,
            email = parsed.email,
            refreshToken = credentials.refreshToken ?: "",
        )
    }

    /**
     * Parses an Auth0-issued ID token (JWT) without remote verification (since the token
     * is passed directly to Supabase which validates the signature via OIDC JWKS).
     * Extracts user_uuid (or computes deterministic UUIDv5 from 'sub'), and email.
     */
    fun parseIdToken(idToken: String): Auth0User {
        val parts = idToken.split(".")
        if (parts.size < 2) {
            throw IllegalArgumentException("Malformed JWT: expected at least 2 segments")
        }

        val payloadBase64 = parts[1]
        val decodedBytes = Base64.getUrlDecoder().decode(padBase64(payloadBase64))
        val json = Json.parseToJsonElement(String(decodedBytes, Charsets.UTF_8)).jsonObject

        val sub = json["sub"]?.jsonPrimitive?.contentOrNull ?: ""
        val email = json["email"]?.jsonPrimitive?.contentOrNull

        // Priority 1: user_uuid injected by our Auth0 Post-Login Action
        val customUuid = json["user_uuid"]?.jsonPrimitive?.contentOrNull
        val userUuid = if (!customUuid.isNullOrBlank()) {
            runCatching { Uuid.parse(customUuid) }.getOrNull() ?: computeUuidV5(sub)
        } else {
            computeUuidV5(sub)
        }

        return Auth0User(
            idToken = idToken,
            userUuid = userUuid,
            email = email,
        )
    }

    private fun padBase64(input: String): String {
        val remainder = input.length % 4
        return if (remainder > 0) input + "=".repeat(4 - remainder) else input
    }

    /**
     * Generates a deterministic RFC 4122 Version 5 UUID using SHA-1 and the standard DNS namespace,
     * identical to PostgreSQL's extensions.uuid_generate_v5 and Node's uuidv5.
     */
    fun computeUuidV5(name: String, namespace: String = NAMESPACE_DNS): Uuid {
        val cleanNs = namespace.replace("-", "")
        val nsBytes = ByteArray(16) { i ->
            cleanNs.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(nsBytes)
        digest.update(nameBytes)
        val hash = digest.digest()

        // Set version 5 (bits 4-7 of byte 6 = 0101)
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        // Set variant to RFC 4122 (bits 6-7 of byte 8 = 10)
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80.toByte().toInt()).toByte()

        val hex = StringBuilder()
        for (i in 0 until 16) {
            val b = hash[i].toInt() and 0xff
            if (b < 0x10) hex.append('0')
            hex.append(b.toString(16))
        }
        val formatted = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        return Uuid.parse(formatted)
    }
}
