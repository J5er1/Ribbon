package app.readribbon.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
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
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.BookSheet
import app.readribbon.design.RibbonTheme
import app.readribbon.design.rememberBookSheet
import app.readribbon.design.room
import app.readribbon.services.PresenceEvent
import app.readribbon.services.PresenceService
import app.readribbon.services.PresentPerson
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page is set before the pull, and a page nobody has touched says nothing.
 *
 * The owner's report on a Pixel 9 Pro XL, after `A43` had made the drag itself
 * track the finger: *"the performance is better when transitioning from home
 * to scripture but not perfect."* `A50` found the first of what was left — the
 * book's JSON being parsed off the disk inside the composition that the first
 * frame of the gesture triggered — and warmed it. Measuring what remained:
 *
 *  - building the chapter's `AnnotatedString`: **~1 ms** once warm
 *  - Compose measuring that chapter: **30–67 ms, every time**
 *
 * So the remaining cost was never the reading; it was setting a whole chapter
 * of Literata as one `BasicText`, which is how the washes, the note's carve
 * and verse hit-testing all read one `TextLayoutResult`. That measure cannot
 * leave the main thread and cannot be done a visible line at a time, so `A51`
 * stopped trying to make it fast and moved it instead: the room arrives, and
 * then the book is built underneath it, off the bottom of the screen.
 *
 * Which turns "composed" and "being opened" into two different things for the
 * first time, and three things in this screen had been relying on them being
 * one. The tests below are those three.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class StandbyPageTest {

    @get:Rule val compose = createComposeRule()

    /**
     * Presence, remembered rather than sent, so the room can be overheard —
     * and, when a test needs somebody else in the book, a roster that says so.
     */
    private class Overheard(roster: List<PresentPerson> = emptyList()) : PresenceService {
        val said = mutableListOf<String>()
        override suspend fun connect(roomID: Uuid, person: Person) { said += "connect" }
        override suspend fun disconnect() { said += "disconnect" }
        override suspend fun suspend() { said += "suspend" }
        override suspend fun present(
            position: VerseAddress?,
            scrollFraction: Double,
            isIdle: Boolean,
            following: Uuid?,
            activity: Boolean,
        ) { said += "present" }
        override suspend fun withdraw() { said += "withdraw" }
        override suspend fun sendThinkingOfYou(to: Uuid) = Unit
        override suspend fun announceChange() = Unit
        override suspend fun sendReading(
            book: String,
            at: ReadingPoint,
            end: ReadingPoint?,
            settled: Boolean,
            carried: Boolean,
        ) { said += "reading" }
        // After a yield, as a socket's would: the model's collector starts
        // inside its constructor, and a roster delivered on the spot would
        // arrive before the model was finished.
        override val events: Flow<PresenceEvent> =
            if (roster.isEmpty()) {
                emptyFlow()
            } else {
                flow {
                    yield()
                    emit(PresenceEvent.Roster(roster))
                }
            }
    }

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val room = Room(name = null, createdAt = now - 40.hours)
    private val open = Reading(
        roomID = room.id,
        bookID = "MRK",
        startedAt = now - 30.hours,
        handiwork = Handiwork(
            scale = FireScale.medium,
            coalDepth = 0.55,
            lastFuelAt = now - 2.hours,
            stateAtLastFuel = FireState.steady,
            recentFuel = listOf(FuelEvent(personID = me.id, at = now - 2.hours)),
        ),
    )

    /** A second reader, following this one. */
    private val ruth = Person(name = "Ruth")

    /**
     * Composes the page at rest — exactly as it now stands beneath the room —
     * and hands back the sheet so a test can raise and lower it.
     */
    private fun page(presence: Overheard): BookSheet {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = AppModel(
            context = context,
            initialState = AppState(
                me = me,
                people = mapOf(me.id to me),
                rooms = listOf(room),
                memberships = listOf(
                    Membership(
                        roomID = room.id,
                        personID = me.id,
                        ink = Ink.teal,
                        joinedAt = now - 40.hours,
                    ),
                ),
                readings = listOf(open),
                currentRoomID = room.id,
            ),
            store = LocalStore(context),
            presence = presence,
        )
        val appearance = Appearance(context)
        appearance.wallpaperColour = false
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
        return sheet
    }

    /**
     * How many verses have been set and given a box on the page.
     *
     * Counted through the per-verse semantics rather than the text, because
     * the chapter is one `BasicText` that clears its own text semantics and
     * republishes a node per verse so that a screen reader gets verses instead
     * of one four-thousand-character utterance (§11). A verse node with a
     * positive height is a verse that Compose has measured — which is the
     * whole question here.
     */
    private fun versesMeasured(): Int = compose.onAllNodes(
        SemanticsMatcher("a measured verse") { node ->
            val said = node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
                .orEmpty()
            said.startsWith("Verse ") && node.size.height > 0
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /**
     * The precondition A51 rests on, rather than A51 itself.
     *
     * The mount is two lines in `RibbonRoot` and is not exercised here; what
     * is exercised is the thing that would quietly make it worthless — the
     * page declining to set type until it had been committed to. It does not:
     * the sheet below has never moved and the chapter is already measured.
     */
    @Test fun thePageIsAlreadySetBeforeAnybodyTouchesIt() {
        val presence = Overheard()
        page(presence)
        // Mark 1 is forty-five verses. Twenty is a floor that cannot be met
        // by a heading or a stray label, and cannot be met at all unless the
        // chapter really was laid out.
        assertTrue(
            "a chapter should be set and measured before the pull begins",
            versesMeasured() >= 20,
        )
    }

    @Test fun aPageNobodyHasTouchedAnnouncesNothing() {
        val presence = Overheard()
        page(presence)
        // §4.2: the room hears you when you are *reading*. A page standing by
        // is not somebody reading, and "Jonathan is reading Mark" in front of
        // the whole room for a book still shut would be the app inventing it.
        assertEquals(
            "a page on standby must not announce anybody",
            emptyList<String>(),
            presence.said.filter { it == "present" },
        )
    }

    /**
     * The reading line is the finest thing a phone says about its reader,
     * and it is said only to somebody who can be seen following them, from a
     * book that is open (§4.2, Law 3). Somebody following you while the book
     * is still shut beneath the room hears nothing at all.
     */
    @Test fun aPageNobodyHasTouchedSaysNothingOfItsLine() {
        val presence = Overheard(
            roster = listOf(
                PresentPerson(
                    id = ruth.id,
                    name = ruth.name,
                    position = VerseAddress("MRK", 1, 1),
                    followingPersonID = me.id,
                ),
            ),
        )
        val sheet = page(presence)
        // Past the settle, the in-flight sample and the moment of being
        // followed — everything that would say it on an open page.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(
            "a page on standby must not say where its line is",
            emptyList<String>(),
            presence.said.filter { it == "reading" },
        )

        // And it is the book being shut that held it back: open, and
        // followed, the page says where its line is once it comes to rest.
        compose.runOnIdle { sheet.animate(open = true) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertTrue(
            "an open page somebody follows should say where its line is",
            presence.said.contains("reading"),
        )
    }

    @Test fun closingTheBookWithdrawsFromTheRoom() {
        val presence = Overheard()
        val sheet = page(presence)

        compose.runOnIdle { sheet.animate(open = true) }
        compose.waitForIdle()
        assertTrue(
            "committing the pull should announce you in the book",
            presence.said.contains("present"),
        )

        // And putting it down must take it back. This used to ride on the page
        // being disposed — `onDispose { withdraw() }` — which no longer
        // happens, because the page goes back to standing by instead of being
        // thrown away. Without the withdrawal moving to the un-commit, closing
        // the book would leave you reading Mark to the whole room for as long
        // as you stayed in it.
        presence.said.clear()
        compose.runOnIdle { sheet.animate(open = false) }
        compose.waitForIdle()
        assertTrue(
            "closing the book should withdraw you from it",
            presence.said.contains("withdraw"),
        )
    }
}
