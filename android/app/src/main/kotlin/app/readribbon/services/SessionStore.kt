package app.readribbon.services

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the session tokens live between launches.
 *
 * The iOS build puts them in the Keychain, on the grounds that they are
 * credentials rather than state; this is the same reasoning applied to
 * Android. The tokens are sealed with an AES-GCM key that lives in the
 * hardware-backed keystore and never leaves it, and only the ciphertext
 * touches a preference file — so the tokens are not readable from a backup,
 * from another app, or from an adb pull on a rooted device.
 *
 * This is hand-rolled rather than `EncryptedSharedPreferences` because
 * androidx.security.crypto is deprecated and on its way out; the pattern
 * below is the one that library was wrapping.
 *
 * Two consequences the sign-in thread has to cope with:
 *
 * 1. Unlike the Keychain, a keystore key does NOT survive a reinstall — it
 *    is destroyed with the app's data, and it is deliberately not backed up
 *    (a key restored onto a different device could not decrypt anything
 *    anyway). So on Android a reinstall signs you out, where on iOS it can
 *    leave you signed in with empty local state. That makes Android's path
 *    the simpler of the two, and the `adoptRemoteIdentity` handling on the
 *    iOS side still matters here for the ordinary sign-in case.
 * 2. Keystore access can genuinely fail — a device mid-migration, a key
 *    invalidated by a lock-screen change. Failing to read a session is
 *    survivable: it means signing in again. So a failure clears the stored
 *    blob and returns null rather than throwing on launch.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): SupabaseSession? {
        val stored = prefs.getString(SESSION_KEY, null) ?: return null
        val plaintext = runCatching { decrypt(stored) }.getOrElse {
            // An undecryptable blob is dead weight; keeping it would only
            // fail again on every launch.
            clear()
            return null
        }
        return runCatching { json.decodeFromString<SupabaseSession>(plaintext) }.getOrNull()
    }

    fun save(session: SupabaseSession) {
        runCatching {
            val sealed = encrypt(json.encodeToString(session))
            prefs.edit().putString(SESSION_KEY, sealed).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(SESSION_KEY).apply()
    }

    // MARK: The key

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring user authentication: the room
                // has to render from cache the instant the app opens (S01),
                // and a lock-screen prompt before the fire draws would be a
                // splash screen by another name.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(plaintext.toByteArray())
        // iv || ciphertext, base64'd — the IV is generated per encryption by
        // the provider and is not secret, only unique.
        val packed = cipher.iv + body
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val packed = Base64.decode(stored, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val body = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body).decodeToString()
    }

    private companion object {
        const val FILE = "ribbon.session"
        const val SESSION_KEY = "session"
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "ribbon.session.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        val json = Json { ignoreUnknownKeys = true }
    }
}
