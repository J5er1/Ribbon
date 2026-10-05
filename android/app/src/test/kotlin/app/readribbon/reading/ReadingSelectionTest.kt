package app.readribbon.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.FuelEvent
import app.readribbon.core.Handiwork
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPoint
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.BookSheet
import app.readribbon.design.RibbonTheme
import app.readribbon.design.rememberBookSheet
import app.readribbon.design.room
import app.readribbon.services.LineWords
import app.readribbon.services.PresenceEvent
import app.readribbon.services.PresenceService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Native selection on the reading screen itself (A62, §13.2): the toolbar
 * comes up with a selection and acts on it, "the verse" widens it, the
 * original follows it, a composer freezes it, and a tap while anything is
 * selected only lets go. John 1, on the Berean Standard, a room of one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", shadows = [ChapterSelectionTest.ShadowMagnifier::class])
class ReadingSelectionTest {

    @get:Rule val compose = createComposeRule()

    private object Quiet : PresenceService {
        override suspend fun connect(roomID: Uuid, person: Person) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun suspend() = Unit
        override suspend fun present(
            position: VerseAddress?,
            scrollFraction: Double,
            isIdle: Boolean,
            following: Uuid?,
            activity: Boolean,
        ) = Unit
        override suspend fun withdraw() = Unit
        override suspend fun sendThinkingOfYou(to: Uuid) = Unit
        override suspend fun announceChange() = Unit
        override suspend fun sendReading(
            book: String,
            at: ReadingPoint,
            end: ReadingPoint?,
            settled: Boolean,
            carried: Boolean,
            words: LineWords?,
        ) = Unit
        override val events: Flow<PresenceEvent> = emptyFlow()
    }

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val room = Room(name = null, createdAt = now - 40.hours)
    private val open = Reading(
        roomID = room.id,
        bookID = "JHN",
        startedAt = now - 30.hours,
        handiwork = Handiwork(
            scale = FireScale.medium,
            coalDepth = 0.55,
            lastFuelAt = now - 2.hours,
            stateAtLastFuel = FireState.steady,
            recentFuel = listOf(FuelEvent(personID = me.id, at = now - 2.hours)),
        ),
    )

    private lateinit var model: AppModel

    private fun page(): BookSheet {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        model = AppModel(
            context = context,
            initialState = AppState(
                me = me,
                people = mapOf(me.id to me),
                rooms = listOf(room),
                memberships = listOf(
                    Membership(roomID = room.id, personID = me.id, ink = Ink.teal, joinedAt = now - 40.hours),
                ),
                readings = listOf(open),
                currentRoomID = room.id,
            ),
            store = LocalStore(context),
            presence = Quiet,
        )
        val appearance = Appearance(context).apply { wallpaperColour = false }
        lateinit var sheet: BookSheet
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize().room()) {
                    sheet = rememberBookSheet()
                    ReadingScreen(
                        model = model,
                        room = room,
                        reading = open,
                        sheet = sheet,
                        onClose = {},
                        onDismissed = {},
                        onFinished = {},
                        onStartAnother = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { sheet.animate(open = true) }
        compose.waitForIdle()
        return sheet
    }

    private fun verse(n: Int): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("verse $n") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull().orEmpty().startsWith("Verse $n. ")
        },
    )

    private fun action(node: SemanticsNodeInteraction, label: String) {
        val found = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { found.action() }
        compose.waitForIdle()
    }

    private fun shows(text: String): Boolean =
        compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun showsDescribed(text: String): Boolean =
        compose.onAllNodes(hasContentDescription(text), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test fun aLongPressBringsTheToolbarAndTheVerseWidensItToTheWholeVerse() {
        page()
        assertTrue("nothing up before anything is selected", !shows(Copy.WRITE))
        // "In", the first word of the chapter.
        verse(1).performTouchInput { longClick(Offset(12f, 30f)) }
        compose.waitForIdle()
        assertTrue("the toolbar came up", shows(Copy.WRITE))
        assertTrue("the end is part-way, so the verse is offered", shows(Copy.wholeVerseVerb(several = false)))

        compose.onNode(hasText(Copy.wholeVerseVerb(several = false), ignoreCase = true), useUnmergedTree = true)
            .performClick()
        compose.waitForIdle()
        assertTrue(shows(Copy.WRITE))
        assertTrue("whole already, so it goes", !shows(Copy.wholeVerseVerb(several = false)))

        // Mark it: stored as the whole verse, with no offsets and no version.
        compose.onNode(hasContentDescription(Copy.inkNamed(Ink.entries.first().displayName))).performClick()
        compose.waitForIdle()
        val marked = model.highlights(open, 1).single().range
        assertEquals(VerseRange("JHN", 1, 1, 1), marked.copy(startWords = null, endWords = null, wordsSource = null))
        assertTrue(marked.isWholeVerses)
        assertNull(marked.charTranslation)
        assertTrue("the toolbar goes with the mark", !shows(Copy.WRITE))
    }

    @Test fun aWordLongPressedIsMarkedAsThatWordAnchoredToItsGreek() {
        page()
        verse(1).performTouchInput { longClick(Offset(12f, 30f)) }
        compose.waitForIdle()
        compose.onNode(hasContentDescription(Copy.inkNamed(Ink.entries.first().displayName))).performClick()
        compose.waitForIdle()
        val marked = model.highlights(open, 1).single().range
        assertNull(marked.startChar)
        assertEquals(2, marked.endChar)
        assertEquals(app.readribbon.core.TranslationID.bsb, marked.charTranslation)
        // OriginalWords.anchored ran on the way in: "In" is Ἐν, word 0.
        assertEquals(listOf(0), marked.startWords)
    }

    @Test fun theOriginalFollowsTheSelectionAsItsEndMoves() {
        page()
        action(verse(3), Copy.LEAVE_SOMETHING_HERE)
        assertTrue(shows(Copy.WRITE))
        compose.onNode(hasText("the greek", ignoreCase = true), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue("the panel is open on verse three", shows("John 1:3"))

        // The selection is still live under the panel: its end steps on a
        // verse, and the panel says so.
        action(compose.onNode(hasContentDescription(Copy.WHERE_THE_MARK_ENDS)), Copy.A_VERSE_FURTHER_ON)
        assertTrue("the panel followed to verse four", shows("John 1:3–4"))
    }

    @Test fun writingFreezesTheSelectionAndTheNoteLandsOnItsVerse() {
        page()
        action(verse(4), Copy.LEAVE_SOMETHING_HERE)
        compose.onNode(hasText(Copy.WRITE, ignoreCase = true), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // The composer has the focus and the native selection has gone with
        // it; the ends a screen reader steps are gone too.
        assertTrue(showsDescribed(Copy.WHAT_YOU_WANT_TO_SAY))
        assertTrue(!showsDescribed(Copy.WHERE_THE_MARK_ENDS))
        // And the page's own report of "nothing selected" did not close it.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("the composer is still open", showsDescribed(Copy.WHAT_YOU_WANT_TO_SAY))
    }

    @Test fun aTapWhileSelectedOnlyLetsGo() {
        page()
        compose.runOnIdle { model.addHighlight(VerseRange("JHN", 1, 5, 5), Ink.teal, open) }
        compose.waitForIdle()
        action(verse(3), Copy.LEAVE_SOMETHING_HERE)
        assertTrue(shows(Copy.WRITE))

        val five = verse(5).fetchSemanticsNode().boundsInRoot
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(five.center.x, five.top + 20f)) }
        compose.waitForIdle()
        assertTrue("the toolbar went", !shows(Copy.WRITE))
        assertTrue("and the tap did not also open the mark's label", !shows(Copy.REMOVE))

        // The next tap is an ordinary one again.
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(five.center.x, five.top + 20f)) }
        compose.waitForIdle()
        assertTrue(shows(Copy.REMOVE))
    }
}
