@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Room
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.LocalFlowRoot
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opening a settings screen finishes.
 *
 * Owner, on a Pixel 9 Pro XL: *"when you're in your profile, going from
 * Appearance, for example, tapping works very well. Actually, not fully.
 * There's a ton of glitches and stuff."*
 *
 * It was not a transition that looked wrong. It was a screen that **never
 * stopped laying itself out**: `LargeTopAppBar` builds its collapsed and its
 * expanded title from the same `title` lambda, so the shared element that
 * flows a settings row's words into the heading was registered twice under
 * one key, on one screen, with neither half leaving — and the bounds
 * animation between them had no fixed point to settle on. Compose went on
 * recomposing and remeasuring for as long as it was given. See A47.
 *
 * **Why this is a test and not a picture.** The look book photographs the two
 * ends of this transition and both were always right; a frame cannot show a
 * layout pass that never ends. What this asserts is the one thing that was
 * false: that the app becomes idle afterwards. It is also why the defect
 * survived — until `theSettingsFlow` was added, no test in this repo had ever
 * put `MenuScreen` inside a `SharedTransitionLayout`, so `LocalFlowRoot` was
 * null, `flowsAsWords` degraded to `this`, and the flow that the transition is
 * *made of* had never once run under test.
 *
 * A failure here is a sixty-second hang and then an `AppNotIdleException`,
 * which is unpleasant but is the honest shape of the bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsFlowSettlesTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun model(): AppModel {
        val me = Person(name = "Jonathan")
        val room = Room(createdAt = Clock.System.now())
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(
                Membership(
                    roomID = room.id,
                    personID = me.id,
                    ink = Ink.teal,
                    joinedAt = Clock.System.now(),
                ),
            ),
            currentRoomID = room.id,
        )
        return AppModel(context, state, LocalStore(context), LocalPresenceService())
    }

    /**
     * The menu as `RibbonRoot` actually mounts it: inside the one shared
     * scope, with `LocalFlowRoot` provided. Without that provider none of this
     * can fail, which was the whole problem.
     */
    private fun openTheMenu(entry: MenuEntry) {
        val m = model()
        val appearance = Appearance(context).apply { wallpaperColour = false }
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalFlowRoot provides this) {
                        MenuScreen(model = m, entry = entry, onDismiss = {}, onSwitch = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun openingAppearanceSettles() {
        openTheMenu(MenuEntry.YOU)
        compose.onNodeWithText(Copy.APPEARANCE).performClick()
        compose.waitForIdle()
    }

    /** The same flow key, the same bar, a different door. */
    @Test fun openingTextSettles() {
        openTheMenu(MenuEntry.YOU)
        compose.onNodeWithText(Copy.TEXT_AND_TRANSLATION).performClick()
        compose.waitForIdle()
    }

    /** And coming back, which is the half the report was actually about. */
    @Test fun comingBackFromAppearanceSettles() {
        openTheMenu(MenuEntry.YOU)
        compose.onNodeWithText(Copy.APPEARANCE).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescriptionOrLabel(Copy.BACK).performClick()
        compose.waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule
        .onNodeWithContentDescriptionOrLabel(label: String) =
        onNode(
            androidx.compose.ui.test.hasContentDescription(label) or
                androidx.compose.ui.test.hasText(label),
        )
}
