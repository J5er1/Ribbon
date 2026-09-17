package app.readribbon.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hearth's two directions, and the way out of the book.
 *
 * Three things the owner found on a Pixel 9 Pro XL, all of them about a
 * gesture that appears to be doing something and then is not:
 *
 *  - *"the Ribbon icon at the bottom of the screen looks like there's some
 *    sort of interaction happening, but it doesn't work very well"* — the
 *    Wave's commit distance was a fifth of the screen's travel, measured
 *    from a control sitting at the foot of the screen (A46).
 *  - *"dragging down from the fire should do something"* — it did not, and
 *    deliberately, because the room has to be able to scroll.
 *  - and the one that is not in the report but would have been the next one:
 *    `opensTheRoom` shares a node with `opensTheBook`, and a second `onClick`
 *    in a node's semantics replaces the first rather than joining it. The
 *    app's front door would have stopped working for a screen reader.
 *
 * None of these is visible in a screenshot, which is why they are here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HearthGesturesTest {

    @get:Rule val compose = createComposeRule()

    /** Long enough that the book's own commit is far out of a thumb's reach. */
    private val travel = 1200f

    private class Log {
        var openedTheBook = 0
        var openedTheRoom = 0
        var closedTheBook = 0
    }

    /**
     * Drags [dy] pixels in six steps with time between them, so the release
     * reads as a deliberate pull rather than a flick. A single `moveBy`
     * followed by `up()` is a fling, and a fling commits by intent — which
     * would pass every test below without any of them meaning anything.
     */
    private fun pull(tag: String, dy: Float) {
        compose.onNodeWithTag(tag).performTouchInput {
            down(center)
            repeat(6) {
                advanceEventTime(24)
                moveBy(Offset(0f, dy / 6f))
            }
            advanceEventTime(120)
            up()
        }
        compose.waitForIdle()
    }

    /** The hearth: up is the book, down is the room. */
    private fun hearth(atTop: Boolean = true): Pair<BookSheet, Log> {
        val log = Log()
        lateinit var sheet: BookSheet
        compose.setContent {
            sheet = rememberBookSheet()
            sheet.travel = travel
            val pull = rememberRoomPull()
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("hearth")
                    .opensTheRoom(
                        pull = pull,
                        label = "open your rooms",
                        enabled = { atTop },
                        onOpened = { log.openedTheRoom++ },
                    )
                    .opensTheBook(
                        sheet = sheet,
                        label = "continue in Mark",
                        onEngaged = {},
                        onOpened = { log.openedTheBook++ },
                        onAbandoned = {},
                    ),
            )
        }
        compose.waitForIdle()
        return sheet to log
    }

    @Test fun pullingTheFireDownOpensTheRoom() {
        val (_, log) = hearth()
        pull("hearth", 260f)
        assertEquals("the room opened", 1, log.openedTheRoom)
        assertEquals("and the book did not", 0, log.openedTheBook)
    }

    /** Short of the commit it is a lean, not a decision. */
    @Test fun aShortPullDownOpensNothing() {
        val (_, log) = hearth()
        pull("hearth", 60f)
        assertEquals(0, log.openedTheRoom)
    }

    /**
     * The room's scroll comes first. This is the decision `opensTheBook` made
     * when it declined downward drags outright, kept: below the top of the
     * room a downward drag is the scroll's, and taking it would be the bug
     * that decision was written about.
     */
    @Test fun belowTheTopOfTheRoomTheScrollKeepsIt() {
        val (_, log) = hearth(atTop = false)
        pull("hearth", 260f)
        assertEquals(0, log.openedTheRoom)
    }

    /** And up is still the book. */
    @Test fun pullingTheFireUpStillOpensTheBook() {
        val (sheet, _) = hearth()
        pull("hearth", -400f)
        assertTrue("the book is open or opening", sheet.progress > 0f)
    }

    /**
     * Both taps survive on one node. A second `onClick` would have taken the
     * book's place; the room's is a custom action, which sits beside it.
     */
    @Test fun bothWaysInHaveATapOfTheirOwn() {
        hearth()
        val semantics = compose.onNodeWithTag("hearth").fetchSemanticsNode().config
        val click = semantics[SemanticsActions.OnClick]
        val custom = semantics[SemanticsActions.CustomActions]
        assertEquals("the book's tap is still the node's click", "continue in Mark", click.label)
        assertTrue(
            "and the room's is a custom action beside it",
            custom.any { it.label == "open your rooms" },
        )
    }

    /** The Wave: a pull of one touch target closes, which is all it ever needed. */
    private fun wave(): Pair<BookSheet, Log> {
        val log = Log()
        lateinit var sheet: BookSheet
        compose.setContent {
            sheet = rememberBookSheet()
            sheet.travel = travel
            // Open, as it is whenever the Wave is on the screen.
            androidx.compose.runtime.LaunchedEffect(Unit) { sheet.animate(open = true) }
            Box(
                Modifier
                    .size(88.dp, 52.dp)
                    .testTag("wave")
                    .closesTheBook(sheet = sheet, onClosed = { log.closedTheBook++ }),
            )
        }
        compose.waitForIdle()
        return sheet to log
    }

    @Test fun aShortPullOnTheWaveClosesTheBook() {
        val (_, log) = wave()
        // 44 dp at this density is 88 px, and there is not much more glass
        // below the Wave than that on a real phone — which is the whole
        // point. Against the old threshold, a fifth of 1200 px, this was
        // nowhere near enough and the page sprang back.
        pull("wave", 120f)
        assertEquals("the book closed", 1, log.closedTheBook)
    }

    /**
     * And an upward drag on it is not swallowed. This was `draggable`, which
     * claims a vertical gesture in both directions — so a pull up moved a
     * book that was already fully open, which is to say it moved nothing, and
     * the gesture was eaten anyway.
     */
    @Test fun anUpwardDragOnTheWaveIsLeftAlone() {
        val (sheet, log) = wave()
        pull("wave", -200f)
        assertEquals("nothing closed", 0, log.closedTheBook)
        assertFalse("and the book did not move", sheet.progress < 1f)
    }
}
