@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import app.readribbon.services.RemoteSync
import app.readribbon.services.SessionStore
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Use a passkey" is a control somebody can actually reach.
 *
 * It was not. The guard read
 *
 *     if (model.passkeysAvailable && !model.auth0Available && activity != null)
 *
 * and `auth0Available` is `remote != null && Auth0Config.isConfigured`, with a
 * real Auth0 domain and client id shipped in the only source set — so
 * `!auth0Available` was false on every device, the control was never composed,
 * and `AppModel.signInWithPasskey` had no reachable caller anywhere in the
 * app. A person could add a passkey and then had no way at all to sign in with
 * one. See A49.
 *
 * **This is the shape of defect nothing else here catches.** It compiles, it
 * lints, and it does not appear in any look book shot — because the frame the
 * shot captures is one where the control is correctly absent. The only way to
 * see it is to satisfy the condition the control claims to want and then look
 * for the control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PasskeyIsReachableTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * A model with a backend attached, which is load-bearing rather than
     * scenery: `auth0Available` is `remote != null && Auth0Config.isConfigured`,
     * so a model with no `remote` makes `!auth0Available` **true** and the old
     * broken guard would have passed this test. The bug only exists on a
     * device that has a backend, so the test only has teeth with one.
     */
    private fun model(): AppModel =
        AppModel(context, AppState(), LocalStore(context), LocalPresenceService()).apply {
            remote = RemoteSync(SessionStore(context))
        }

    @Test fun whenTheProjectOffersPasskeysTheControlIsDrawn() {
        val m = model()
        check(m.auth0Available) {
            "this build no longer configures Auth0, so this test proves nothing"
        }
        // What `learnWhatAuthOffers` sets once the project has answered yes.
        m.passkeysAvailable = true

        compose.setContent { RibbonTheme { SignInInline(model = m) } }

        compose.onNodeWithText(Copy.USE_A_PASSKEY).assertIsDisplayed()
    }

    /**
     * And the other half of the condition still holds: a project with passkeys
     * off offers nothing, which is what A48 was about.
     */
    @Test fun whenTheProjectDoesNotTheControlIsAbsent() {
        val m = model()

        compose.setContent { RibbonTheme { SignInInline(model = m) } }

        compose.onNodeWithText(Copy.USE_A_PASSKEY).assertDoesNotExist()
    }
}
