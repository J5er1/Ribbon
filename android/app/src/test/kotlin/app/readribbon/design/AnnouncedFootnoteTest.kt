package app.readribbon.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A result the person did not ask for is announced.
 *
 * The passkey line on You is the one place in the app where something appears
 * because of an action, with nothing taking focus and nothing else moving. A
 * screen reader is told about that only by a live region — and a live region
 * reports a **change to a node that already exists**. Hung on the text itself
 * it announced nothing, because the text is composed for the first time at the
 * moment there is something to say, and a node that has just been created has
 * no previous content to have changed from.
 *
 * So what this asserts is not "the words appear" — a look book shot shows that
 * — but that **the live region is already on the screen while there is still
 * nothing to say**. That is the property the announcement depends on and the
 * one that was missing. See A49.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AnnouncedFootnoteTest {

    @get:Rule val compose = createComposeRule()

    @Test fun theRegionIsThereBeforeThereIsAnythingToAnnounce() {
        var result by mutableStateOf<String?>(null)
        compose.setContent {
            RibbonTheme {
                Column {
                    SettingsGroup(count = 1, footnote = result, footnoteAnnounces = true) {
                        Setting(title = "Add a passkey", onClick = {}, chevron = false)
                    }
                }
            }
        }

        // Nothing has happened yet, and the region is already standing there.
        val before = compose.onNodeWithTag(ANNOUNCED_FOOTNOTE).fetchSemanticsNode()
        assertEquals(
            "the footnote is a live region before it has words",
            LiveRegionMode.Polite,
            before.config[SemanticsProperties.LiveRegion],
        )

        result = "Adding the passkey didn't finish. You can try again."
        compose.waitForIdle()

        // The same node, now with something in it: a content change on a node
        // that was already there, which is what gets read aloud.
        val after = compose.onNodeWithTag(ANNOUNCED_FOOTNOTE).fetchSemanticsNode()
        assertEquals(
            "and it is still one",
            LiveRegionMode.Polite,
            after.config[SemanticsProperties.LiveRegion],
        )
        assertEquals("and it is the same node", before.id, after.id)
        compose.onNodeWithText("Adding the passkey didn't finish. You can try again.")
            .assertExists()
    }

    /** A standing footnote is prose, and is not announced at all. */
    @Test fun anOrdinaryFootnoteIsNotALiveRegion() {
        compose.setContent {
            RibbonTheme {
                Column {
                    SettingsGroup(count = 1, footnote = "An account carries your room.") {
                        Setting(title = "Sign in", onClick = {}, chevron = false)
                    }
                }
            }
        }

        compose.onNodeWithTag(ANNOUNCED_FOOTNOTE).assertDoesNotExist()
        compose.onNodeWithText("An account carries your room.").assertExists()
    }
}
