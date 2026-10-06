@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Membership
import app.readribbon.core.PageType
import app.readribbon.core.Person
import app.readribbon.core.Room
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page, the way you read it (S20, A68).
 *
 * The page's group took three more rows — a weight, three stops from Lighter
 * to Heavier with Book where the page has always been; a line for every
 * verse; clearer verse numbers — between the spacing and the red letter, in
 * the order the iPhone has them. Each writes a step or a switch, never a
 * value an older build could not read. A picture shows the rows; what it
 * cannot show is asserted here: which stop says it is chosen, that a tap
 * moves it, what each row writes, and that the piece of page under the size
 * is set the way the page now is. The size itself runs to 28.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ThePageAsYouReadItTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val room = Room(createdAt = now)

    /** The Text screen with no book open, so the piece of page is John 1:1–2. */
    private fun show(): AppModel {
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(Membership(roomID = room.id, personID = me.id, joinedAt = now)),
            currentRoomID = room.id,
        )
        val model = AppModel(context, state, LocalStore(context), LocalPresenceService())
        compose.setContent {
            RibbonTheme { TextSettingsScreen(model = model, onBack = {}) }
        }
        // The words are read off the main thread, so they arrive a moment
        // after the screen does.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(Copy.specimenAt("John 1:1")).fetchSemanticsNodes().isNotEmpty()
        }
        return model
    }

    /**
     * One of Weight's stops, scrolled to: "Book" is Line spacing's middle
     * word too, so a stop is found beside Weight's own ends.
     */
    private fun weightStop(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            (hasAnySibling(hasText(Copy.WEIGHT_LIGHTER)) or hasAnySibling(hasText(Copy.WEIGHT_HEAVIER))),
    ).performScrollTo()

    private fun switchRow(title: String): SemanticsNodeInteraction =
        compose.onNode(hasText(title) and isToggleable()).performScrollTo()

    @Test fun thePagesRowsAreInTheIPhonesOrder() {
        show()
        val titles = listOf(
            Copy.TEXT_SIZE,
            Copy.LINE_SPACING,
            Copy.WEIGHT,
            Copy.VERSE_LINES,
            Copy.CLEAR_NUMBERS,
            Copy.RED_LETTER,
        )
        // Where each title is, whether or not the screen has been scrolled
        // to it: a node's bounds are clipped to the screen, its place is not.
        val tops = titles.map { title ->
            compose.onNode(hasText(title), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals("the rows top to bottom", tops.sorted(), tops)
    }

    @Test fun weightsStopsSayWhichIsChosenAndATapChoosesAnother() {
        val model = show()
        weightStop(Copy.WEIGHT_BOOK).assertIsSelected()
        weightStop(Copy.WEIGHT_LIGHTER).assertIsNotSelected()
        weightStop(Copy.WEIGHT_HEAVIER).assertIsNotSelected()

        weightStop(Copy.WEIGHT_HEAVIER).performClick()
        compose.waitForIdle()
        assertEquals("Heavier is the last step", 2, model.settings.weightStep)
        assertEquals("drawn at 470", 470, model.settings.weight(boldText = false))
        weightStop(Copy.WEIGHT_HEAVIER).assertIsSelected()
        weightStop(Copy.WEIGHT_BOOK).assertIsNotSelected()

        weightStop(Copy.WEIGHT_LIGHTER).performClick()
        compose.waitForIdle()
        assertEquals("Lighter is the first", 0, model.settings.weightStep)
        weightStop(Copy.WEIGHT_LIGHTER).assertIsSelected()
        assertEquals("and the spacing is untouched", PageType.defaultLineSpacingStep, model.settings.lineSpacingStep)
    }

    @Test fun aLineForEveryVerseIsASwitchAndThePieceOfPageShowsIt() {
        val model = show()
        // John 1:1 and 1:2 are one paragraph in the Berean Standard, so they
        // run on in the piece of page until the switch is on.
        compose.onNode(hasText("God. 2 He was with God", substring = true)).assertExists()

        switchRow(Copy.VERSE_LINES).assertIsOff()
        switchRow(Copy.VERSE_LINES).performClick()
        compose.waitForIdle()
        switchRow(Copy.VERSE_LINES).assertIsOn()
        assertTrue(model.settings.versePerLine)
        compose.onNode(hasText("God. \n2 He was with God", substring = true)).assertExists()
        assertFalse("nothing else is set by it", model.settings.clearVerseNumbers)

        switchRow(Copy.VERSE_LINES).performClick()
        compose.waitForIdle()
        assertFalse(model.settings.versePerLine)
    }

    @Test fun clearerVerseNumbersIsASwitch() {
        val model = show()
        switchRow(Copy.CLEAR_NUMBERS).assertIsOff()
        switchRow(Copy.CLEAR_NUMBERS).performClick()
        compose.waitForIdle()
        switchRow(Copy.CLEAR_NUMBERS).assertIsOn()
        assertTrue(model.settings.clearVerseNumbers)
        assertEquals(PageType.clearVerseNumberAlpha, model.settings.verseNumberAlpha, 0.0)
        assertFalse("nothing else is set by it", model.settings.versePerLine)
    }

    @Test fun theSizeRunsToTwentyEightInHalves() {
        show()
        val slider = compose.onNodeWithContentDescription(Copy.TEXT_SIZE).fetchSemanticsNode()
        val range = slider.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(16f, range.range.start, 0f)
        assertEquals(28f, range.range.endInclusive, 0f)
        assertEquals("twenty-three stops between the ends", 23, range.steps)
    }

    /**
     * At the largest font scale on a narrow phone, each of Weight's stops is
     * still one whole word on one line: "Heavier" is wider than a third of
     * the control there, and it gives way in size rather than being cut.
     */
    @Config(qualifiers = "w320dp-h700dp-xhdpi", fontScale = 2f)
    @Test fun theWeightsStopsAreWholeAtTheLargestFontScale() {
        show()
        for (word in listOf(Copy.WEIGHT_LIGHTER, Copy.WEIGHT_HEAVIER)) {
            val node = compose.onNodeWithText(word, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("$word is one line", 1, layout.lineCount)
            assertFalse("$word is not cut", layout.hasVisualOverflow)
        }
    }
}

