package app.readribbon.services

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException

// §6.10: "Sign-in is a passkey where available, an emailed code otherwise."
// This is the passkey half — the ceremony, run by the platform.
//
// Supabase Auth hands out a challenge and the W3C options that go with it,
// CredentialManager puts the system sheet on the screen and returns a signed
// credential, and the signed credential goes back. For a sign-in a session
// comes out of it. Nothing about the account is typed, and on a new phone
// nothing about it is even asked: passkeys here are discoverable, so the
// authenticator names the account itself.
//
// CredentialManager speaks WebAuthn's own JSON in both directions, which is
// the whole reason this file is short: the options string goes in exactly as
// the server sent it and the response string comes out exactly as the server
// expects it. Nothing here reads either one, so there is nothing here to get
// wrong about a shape that is not ours.
//
// It works only where the domain says it does. `readribbon.app` must serve a
// `.well-known/assetlinks.json` naming this package and the SHA-256 of the
// certificate the app is signed with (web/build.mjs emits it from
// RIBBON_ANDROID_CERT_SHA256), and the project's WebAuthn relying party must
// be that same bare domain with this app's origin among its allowed ones.
// Until that is true this offers nothing and says nothing — the emailed code
// is the way in, as it always was, and a passkey is only ever an addition.

/** The ceremony, and the two shapes it produces. */
class Passkeys(context: Context) {

    private val manager = CredentialManager.create(context)

    /**
     * The person dismissed the sheet, or had no credential to offer.
     *
     * Never an error surface (§25): a passkey declined is not a failure, it
     * is a person choosing the other way in.
     */
    class Cancelled : Exception()

    /**
     * Make a passkey for this account.
     *
     * @param context an Activity context — the system sheet needs a window,
     *   and an application context has none.
     * @param optionsJson `PublicKeyCredentialCreationOptions`, verbatim.
     * @return the registration response JSON, verbatim.
     */
    suspend fun register(context: Context, optionsJson: String): String {
        val response = try {
            manager.createCredential(
                context = context,
                request = CreatePublicKeyCredentialRequest(requestJson = optionsJson),
            )
        } catch (cancelled: CreateCredentialCancellationException) {
            throw Cancelled()
        }
        return (response as? CreatePublicKeyCredentialResponse)
            ?.registrationResponseJson
            ?: throw Cancelled()
    }

    /**
     * Sign in with a passkey already on this device or in the person's
     * password manager. Discoverable: no account is named going in.
     *
     * @param optionsJson `PublicKeyCredentialRequestOptions`, verbatim.
     * @return the authentication response JSON, verbatim.
     */
    suspend fun assert(context: Context, optionsJson: String): String {
        val response = try {
            manager.getCredential(
                context = context,
                request = GetCredentialRequest(
                    listOf(GetPublicKeyCredentialOption(requestJson = optionsJson)),
                ),
            )
        } catch (cancelled: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (none: NoCredentialException) {
            // No passkey for this domain on this device. The same nothing as
            // a dismissal, and the email field is still there.
            throw Cancelled()
        }
        return (response.credential as? PublicKeyCredential)
            ?.authenticationResponseJson
            ?: throw Cancelled()
    }
}
