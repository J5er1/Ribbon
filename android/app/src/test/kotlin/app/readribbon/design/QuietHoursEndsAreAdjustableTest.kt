package app.readribbon.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.readribbon.app.Copy
import app.readribbon.core.QuietHoursBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both ends of the quiet hours can be moved without dragging them.
 *
 * The two rows that each opened Material's clock are one drawn band now
 * (A66). A clock is something TalkBack can already set; a drawing is
 * nothing to a screen reader until it is told what it is, and a band that
 * could only be dragged would have taken quiet hours away from anybody who
 * cannot drag. So what this asserts is the half of the band no look book
 * shot can show: each end is its own control, named for which end it is,
 * saying its time, and one swipe moves it one quarter of an hour — the same
 * step a finger takes. And, for the finger, that it is the nearer end that
 * comes to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class QuietHoursEndsAreAdjustableTest {

    @get:Rule val compose = createComposeRule()

    @Test fun eachEndIsItsOwnControlAndASwipeIsAQuarterOfAnHour() {
        var start by mutableIntStateOf(22 * 60)
        var end by mutableIntStateOf(6 * 60)
        compose.setContent {
            RibbonTheme {
                QuietHoursBandControl(
                    start = start,
                    end = end,
                    onStart = { start = it },
                    onEnd = { end = it },
                )
            }
        }

        val begins = compose.onNodeWithContentDescription(Copy.QUIET_HOURS_BEGIN)
        val range = begins.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        // Ten in the evening is ten hours along a band that begins at noon.
        assertEquals(40f, range.current, 0.001f)
        assertEquals(0f..95f, range.range)
        assertEquals("a swipe is one quarter of an hour", 94, range.steps)
        val saidBefore = begins.fetchSemanticsNode().config[SemanticsProperties.StateDescription]

        begins.performSemanticsAction(SemanticsActions.SetProgress) { it(range.current + 1f) }
        compose.waitForIdle()
        assertEquals("the beginning is a quarter later", 22 * 60 + 15, start)
        assertEquals("and the end has not moved", 6 * 60, end)
        assertNotEquals(
            "and it says its new time",
            saidBefore,
            compose.onNodeWithContentDescription(Copy.QUIET_HOURS_BEGIN)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )

        val ends = compose.onNodeWithContentDescription(Copy.QUIET_HOURS_END)
        val endRange = ends.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        // Six in the morning is three quarters of the way along.
        assertEquals(72f, endRange.current, 0.001f)
        ends.performSemanticsAction(SemanticsActions.SetProgress) { it(endRange.current - 1f) }
        compose.waitForIdle()
        assertEquals("the end is a quarter earlier", 5 * 60 + 45, end)
        assertEquals("and the beginning has not moved", 22 * 60 + 15, start)
    }

    @Test fun aFingerTakesTheNearerEnd() {
        var start by mutableIntStateOf(22 * 60)
        var end by mutableIntStateOf(6 * 60)
        compose.setContent {
            RibbonTheme {
                QuietHoursBandControl(
                    start = start,
                    end = end,
                    onStart = { start = it },
                    onEnd = { end = it },
                )
            }
        }

        val width = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
        val y = compose.onNodeWithContentDescription(Copy.QUIET_HOURS_END)
            .fetchSemanticsNode().boundsInRoot.center.y
        // Just past six in the morning, which is far nearer the end of the
        // night than its beginning; then a little way back toward midnight.
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.75f + 4f, y))
            moveTo(Offset(width * 0.70f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the beginning was not the nearer end", 22 * 60, start)
        assertEquals("the end followed the finger", QuietHoursBand.minute(0.70), end)
    }

    @Test fun withNoQuietHoursADragDrawsTheNightOut() {
        var start by mutableIntStateOf(22 * 60)
        var end by mutableIntStateOf(22 * 60)
        compose.setContent {
            RibbonTheme {
                QuietHoursBandControl(
                    start = start,
                    end = end,
                    onStart = { start = it },
                    onEnd = { end = it },
                )
            }
        }

        val width = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
        val y = compose.onNodeWithContentDescription(Copy.QUIET_HOURS_END)
            .fetchSemanticsNode().boundsInRoot.center.y
        // Both ends on one minute, and the finger on it: a tie, which goes
        // to the end — so the night is drawn out to the right, rather than
        // its beginning pushed back into the evening.
        val at = width * QuietHoursBand.position(22 * 60).toFloat()
        compose.onRoot().performTouchInput {
            down(Offset(at, y))
            moveTo(Offset(width * 0.75f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the beginning stayed where it was", 22 * 60, start)
        assertEquals("the end was drawn out to six", 6 * 60, end)
    }
}
