@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Membership
import app.readribbon.core.PageFaces
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

    private fun switchRow(title: String): SemanticsNodeInteraction =
        compose.onNode(hasText(title) and isToggleable()).performScrollTo()

    @Test fun thePagesRowsAreInTheIPhonesOrder() {
        show()
        val titles = listOf(
            Copy.TYPEFACE,
            Copy.TEXT_SIZE,
            Copy.LINE_SPACING,
            Copy.WEIGHT,
            Copy.LETTER_SPACING,
            Copy.MARGINS,
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

    @Test fun theSlidersSayTheirValuesAndWriteTheOldStepBeside() {
        val model = show()
        fun slider(label: String) = compose.onNodeWithContentDescription(label)
        fun said(label: String) = slider(label).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(Copy.lineSpacingValue(172), said(Copy.LINE_SPACING))
        assertEquals(Copy.WEIGHT_BOOK, said(Copy.WEIGHT))
        assertEquals(Copy.letterSpacingValue(0), said(Copy.LETTER_SPACING))
        assertEquals(Copy.marginValue(0), said(Copy.MARGINS))
        for (label in listOf(Copy.LINE_SPACING, Copy.WEIGHT, Copy.LETTER_SPACING, Copy.MARGINS)) {
            val range = slider(label).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals("$label runs over thirteen positions", 12f, range.range.endInclusive, 0f)
            assertEquals("eleven of them between the ends", 11, range.steps)
        }

        slider(Copy.WEIGHT).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.waitForIdle()
        assertEquals("Heavier, on the axis", 470, model.settings.pageWeight)
        assertEquals("the old step written beside it", 2, model.settings.weightStep)
        assertEquals(Copy.WEIGHT_HEAVIER, said(Copy.WEIGHT))
        slider(Copy.WEIGHT).performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.waitForIdle()
        assertEquals(420, model.settings.pageWeight)
        assertEquals("nearer Book than Heavier", 1, model.settings.weightStep)
        assertEquals("2 steps heavier than Book", said(Copy.WEIGHT))

        slider(Copy.LINE_SPACING).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        compose.waitForIdle()
        assertEquals(190, model.settings.lineHeightHundredths)
        assertEquals("Open, the old step", 2, model.settings.lineSpacingStep)
        assertEquals(1.9, model.settings.lineHeightMultiple, 0.0)

        slider(Copy.LETTER_SPACING).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.waitForIdle()
        assertEquals(25, model.settings.letterSpacingThousandths)
        slider(Copy.MARGINS).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.waitForIdle()
        assertEquals(48, model.settings.marginPoints)
        assertEquals("the size is untouched", PageType.defaultSize, model.settings.scriptureSize, 0.0)
    }

    @Test fun aTypefaceIsChosenByReadingIt() {
        val model = show()
        val garamond = compose.onNode(hasText(PageFaces.ebGaramond.name) and hasAnySibling(hasText(Copy.TYPEFACE_GARAMOND_SUB)), useUnmergedTree = true)
        garamond.performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("ebGaramond", model.settings.typeface)
        assertEquals(PageFaces.ebGaramond, model.settings.face)
        assertEquals("the colophon follows it", "Set in EB Garamond and Alegreya Sans.", Copy.colophonSetIn(model.settings.face.name))
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
        // Twenty-five positions, 16 to 28 by halves (PageType.size(at:)).
        assertEquals(0f, range.range.start, 0f)
        assertEquals((PageType.sizePositions - 1).toFloat(), range.range.endInclusive, 0f)
        assertEquals("twenty-three stops between the ends", 23, range.steps)
        assertEquals(16.0, PageType.size(0), 0.0)
        assertEquals(28.0, PageType.size(PageType.sizePositions - 1), 0.0)
        assertEquals(Copy.textSizeValue(19.0), slider.config[SemanticsProperties.StateDescription])
    }

    /**
     * The strip of the page keeps one height whatever the page's settings
     * are (A69), so nothing under a finger moves while a slider is dragged —
     * here at the largest font scale on a narrow phone, where it matters most.
     */
    @Config(qualifiers = "w320dp-h700dp-xhdpi", fontScale = 2f)
    @Test fun theStripKeepsOneHeightWhateverTheSize() {
        val model = show()
        val strip = compose.onNodeWithTag(PAGE_STRIP_TAG)
        val before = strip.fetchSemanticsNode().size.height
        assertTrue("the strip is there", before > 0)
        compose.onNodeWithContentDescription(Copy.TEXT_SIZE).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it((PageType.sizePositions - 1).toFloat()) }
        compose.waitForIdle()
        assertEquals(28.0, model.settings.scriptureSize, 0.0)
        assertEquals("the same height at 28", before, strip.fetchSemanticsNode().size.height)
    }
}
