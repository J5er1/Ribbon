@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Room
import app.readribbon.core.WhatsNew
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.LocalFlowRoot
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What's new, read again from You (A65): every release, newest first, each
 * under its day and its own title — and reading them decides nothing. The
 * launch's screen is the model's (`AppModel.whatsNew`), and the history
 * neither records a release as seen nor takes one that is owed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class WhatsNewHistoryTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val latest = WhatsNew.releases.first()
    private val oldest = WhatsNew.releases.last()

    private fun model(seen: String?): AppModel {
        val me = Person(name = "Jonathan")
        val room = Room(createdAt = Clock.System.now())
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(
                Membership(roomID = room.id, personID = me.id, ink = Ink.teal, joinedAt = Clock.System.now()),
            ),
            currentRoomID = room.id,
            whatsNewSeen = seen,
        )
        return AppModel(context, state, LocalStore(context), LocalPresenceService())
    }

    /** You, mounted as `RibbonRoot` mounts it. */
    private fun openYou(m: AppModel) {
        val appearance = Appearance(context).apply { wallpaperColour = false }
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalFlowRoot provides this) {
                        MenuScreen(model = m, entry = MenuEntry.YOU, onDismiss = {}, onSwitch = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun youOpensEveryReleaseNewestFirstAndDoneComesBack() {
        val m = model(seen = latest.id)
        openYou(m)
        compose.onNodeWithText(Copy.WHATS_NEW_ROW_SUB).assertExists()
        compose.onNodeWithText(Copy.WHATS_NEW_ROW).performClick()
        compose.waitForIdle()

        // The newest first, under its day, as a heading.
        compose.onNode(isHeading() and hasText(Copy.whatsNewTitle(latest.id), substring = true)).assertExists()
        compose.onNode(hasText(Copy.whatsNewReleased(latest.released)!!)).assertExists()

        // And the oldest further down, with its own day and title.
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText(Copy.whatsNewTitle(oldest.id)))
        compose.onNode(isHeading() and hasText(Copy.whatsNewTitle(oldest.id), substring = true)).assertExists()
        compose.onNode(hasText(Copy.whatsNewReleased(oldest.released)!!)).assertExists()
        for (item in oldest.items) {
            list.performScrollToNode(hasText(item.title))
            compose.onNode(hasText(item.title, substring = true)).assertExists()
        }

        compose.onNodeWithText(Copy.WHATS_NEW_HISTORY_DONE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(Copy.WHATS_NEW_ROW).assertExists()
        assertEquals("reading them again records nothing new", latest.id, m.state.whatsNewSeen)
    }

    /**
     * A release still owed to this phone stays owed: reading the list is not
     * the launch's screen being shown, and leaving it is not leaving that.
     */
    @Test fun readingThemAgainLeavesTheLaunchsDecisionAlone() {
        val m = model(seen = null)
        m.decideWhatsNew(plainLaunch = true)
        assertEquals(latest, m.whatsNew)

        openYou(m)
        compose.onNodeWithText(Copy.WHATS_NEW_ROW).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(Copy.WHATS_NEW_HISTORY_DONE).performClick()
        compose.waitForIdle()

        assertEquals("the launch's release is still the launch's", latest, m.whatsNew)
        assertNull("and nothing was recorded", m.state.whatsNewSeen)
    }

    /** The launch itself says only the latest, under "What's new" and its own title. */
    @Test fun theLaunchShowsOnlyTheLatestUnderItsOwnTitle() {
        val appearance = Appearance(context)
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                WhatsNewScreen(release = latest, onLeave = {})
            }
        }
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(Copy.WHATS_NEW_HEADING, substring = true)).assertExists()
        compose.onNode(hasText(Copy.whatsNewTitle(latest.id))).assertExists()
        compose.onNode(hasText(Copy.whatsNewTitle(oldest.id))).assertDoesNotExist()
        compose.onNodeWithText(Copy.WHATS_NEW_DONE).assertExists()
    }
}
