package app.readribbon.design

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The book, opened by hand.
//
// The way into the book was a button, and the way out was a mark you tapped,
// and between the two the book simply *appeared* and later simply *went*. The
// owner's note: you should be able to take hold of the fire in the middle of
// the room and pull the book open with it.
//
// So there is one number, [BookSheet.progress], that means how open the book
// is — 0 for closed, 1 for open — and three things drive it:
//
//   - the fire, pulled upward on the room (S01),
//   - the Wave, pulled downward at the foot of the book (S02),
//   - and a plain tap on either of them, which animates the same number.
//
// Everything that moves during the opening reads that one number: the book's
// own offset, the room receding behind it, the fire growing under the finger,
// the hint fading out. That is the difference between a transition and a
// gesture — there is no separate "opening animation" to keep in step with the
// drag, because the drag and the animation are the same value.
//
// Reduce motion (§11): the gestures still work and still open the book; the
// number simply jumps between its two ends instead of travelling, because the
// animation spec every `animateTo` here uses branches inside the token.

/**
 * How open the book is, and who is holding it.
 *
 * Owned by the room's stack so that the room, the fire and the book can all
 * read the same value. Everything that reads it does so inside a
 * `graphicsLayer` or a draw, so a finger moving the book never recomposes a
 * word of Scripture.
 */
@Stable
class BookSheet internal constructor(
    private val scope: CoroutineScope,
    /**
     * Read at each settle rather than held.
     *
     * Reduce motion is live — the app watches `ANIMATOR_DURATION_SCALE` — so
     * it can change while the book is open. Keying this whole holder on it
     * would build a fresh one with a fresh `Animatable(0f)` at that moment,
     * leaving the page composed and parked below the bottom of the screen
     * with nothing left able to bring it back.
     */
    private val still: () -> Boolean,
) {

    /**
     * 0 closed, 1 open. Never outside that.
     *
     * A plain piece of snapshot state rather than an `Animatable`, and that is
     * the difference between the gesture tracking a finger and the gesture
     * lagging it.
     *
     * `Animatable.snapTo` is a suspend function — it has to be, it takes the
     * animation mutex — so a drag written on top of one has to say
     * `scope.launch { pull.snapTo(next) }` for every pointer event. That
     * scope's dispatcher is `AndroidUiDispatcher`, which does not run the
     * block where it was launched: it queues it and runs it at the next
     * message-loop turn or the next choreographer frame. A pointer event is
     * itself delivered inside a frame, so the position landed **after** that
     * frame had already drawn, and the book was a whole frame behind the
     * thumb — every frame, for the length of the pull. On a 120 Hz screen
     * that is eight milliseconds of lag that is never made up, plus a
     * coroutine allocated and a mutex taken for each of the hundred-odd touch
     * samples a second the panel reports.
     *
     * The owner reported the pull-up gesture performing badly on a Pixel 9
     * Pro XL, which is not a phone that should struggle to move a rectangle.
     * It was not struggling; it was being told a frame late.
     *
     * Written directly, the drag is one snapshot write inside the frame that
     * is about to draw, so the book is where the finger is. Everything that
     * reads [progress] does so inside a `graphicsLayer` or a draw, so the
     * write invalidates drawing and nothing else — no recomposition, exactly
     * as before.
     */
    private var position by mutableFloatStateOf(0f)

    /**
     * The settle in flight, so a new drag can take the book off it.
     *
     * This is what the animation mutex used to do. `Animatable` cancels
     * whatever is animating it when something else snaps it, which is the one
     * thing that made the `launch` above defensible; with a plain value the
     * cancelling has to be explicit, and [engage] and [drag] both do it.
     */
    private var settling: Job? = null

    /**
     * The travel one full pull is worth, in pixels — how far a finger has to
     * go to open the book. Set by the layer that knows how tall the screen
     * is; read by the hearth, which rides up by exactly this much so that the
     * fire stays under the thumb that is pulling it.
     */
    var travel by mutableFloatStateOf(1f)
        internal set

    /**
     * True from the first millimetre of an opening drag until the book is
     * fully closed again — which is not the same as `progress > 0`, because
     * the book has to be composed *before* it can rise.
     */
    var engaged by mutableStateOf(false)
        private set

    /** Where the book is. Read inside a layer block, never as state. */
    val progress: Float get() = position

    /**
     * The book is open, rather than on its way somewhere.
     *
     * The distinction is not cosmetic and it is the one thing about this
     * gesture that is easy to get wrong. The reading surface has to be
     * *composed* from the first millimetre of a pull — there is nothing to
     * raise otherwise — but it must not be **in** the book until the pull
     * commits: announcing yourself to the room, feeding the fire and
     * scrolling to somebody's note are all things that should not happen
     * because a thumb brushed the fire and thought better of it.
     *
     * True from the moment an opening settles, false again the moment a
     * close begins.
     */
    var committed by mutableStateOf(false)
        private set

    /**
     * Where the pull was when this drag took hold.
     *
     * The commit is measured from here rather than from the closed end, and
     * that is not a refinement. `OPEN_COMMIT` is a third *of the travel*: a
     * third from zero opens, but measured from zero a close would only have
     * committed once the page had been dragged two thirds of the way back —
     * so closing by the Wave asked for twice the drag of opening by the
     * fire, on a gesture that is meant to be the same one in reverse.
     */
    private var grabbed = 0f

    /**
     * Take hold. Called when a drag starts on either handle; composes the
     * book so there is something to pull.
     */
    internal fun engage() {
        settling?.cancel()
        grabbed = position
        engaged = true
    }

    /**
     * Move by [delta] pixels of pull — positive opens.
     *
     * Synchronous, and called straight from the pointer handler: see
     * [position] for why that matters. The cancel is belt and braces — every
     * path into a drag calls [engage] first — but a settle still running under
     * a finger would fight it for the value, and a no-op cancel on a finished
     * job costs nothing next to the alternative.
     */
    internal fun drag(delta: Float) {
        settling?.cancel()
        position = (position + delta / travel).coerceIn(0f, 1f)
    }

    /**
     * Let go at [velocity] pixels per second — positive opening.
     *
     * Distance decides, unless the flick was quick enough to say the person
     * knows the gesture, in which case intent does. The velocity is handed
     * to the spring rather than thrown away, so the book keeps going at the
     * speed the finger let it go at (see the spring section of
     * [RibbonMotion]).
     */
    internal fun release(velocity: Float, onOpened: () -> Unit, onClosed: () -> Unit) {
        val moved = position - grabbed
        val opening = when {
            // Intent first: a flick says the person knows the gesture, and
            // making them drag the whole third anyway is the app not
            // believing them.
            velocity > RibbonMotion.OPEN_FLING -> true
            velocity < -RibbonMotion.OPEN_FLING -> false
            // Then distance, measured from where the hand took hold — so a
            // third of the way is a third of the way in both directions.
            moved >= RibbonMotion.OPEN_COMMIT -> true
            moved <= -RibbonMotion.OPEN_COMMIT -> false
            // Not far enough either way: back where it came from.
            else -> grabbed >= 0.5f
        }
        settle(opening, velocity / travel, onOpened, onClosed)
    }

    /** Open or close with no finger involved: the tap equivalent (§11). */
    fun animate(open: Boolean, onOpened: () -> Unit = {}, onClosed: () -> Unit = {}) {
        if (open) engage()
        settle(open, 0f, onOpened, onClosed)
    }

    /**
     * Put the book down instantly — a close that did not come from this
     * gesture at all: the finishing sequence, a room switch, a tapped invite.
     */
    fun reset() {
        engaged = false
        committed = false
        settling?.cancel()
        position = 0f
    }

    private fun settle(
        open: Boolean,
        initialVelocity: Float,
        onOpened: () -> Unit,
        onClosed: () -> Unit,
    ) {
        // A close un-commits at once rather than when it lands: presence
        // should stop saying you are in the book the moment the book starts
        // leaving, not four hundred milliseconds later.
        if (!open) committed = false
        settling?.cancel()
        settling = scope.launch {
            // Interrupted by another drag or another settle, this coroutine is
            // cancelled at its next frame and ends inside `animate` — which is
            // what should happen. The callbacks below belong to the movement
            // that actually finished.
            animate(
                initialValue = position,
                targetValue = if (open) 1f else 0f,
                initialVelocity = initialVelocity,
                animationSpec = RibbonMotion.cover(still()),
            ) { value, _ -> position = value }
            if (open) {
                committed = true
                onOpened()
            } else {
                engaged = false
                onClosed()
            }
        }
    }
}

@Composable
fun rememberBookSheet(): BookSheet {
    val scope = rememberCoroutineScope()
    val still = rememberUpdatedState(rememberReduceMotion())
    // Keyed on the scope alone. See the constructor's note on `still`.
    return remember(scope) { BookSheet(scope) { still.value } }
}

/**
 * This element is the handle that pulls the book open: the fire, on the room.
 *
 * Upward drag opens. The tap equivalent is the same open, animated — §11 is
 * explicit that no way in or out of the book may be a gesture only, and the
 * way-in button underneath is the other one.
 *
 * @param label what a screen reader is told the tap does.
 */
@Composable
fun Modifier.opensTheBook(
    sheet: BookSheet,
    label: String,
    onEngaged: () -> Unit,
    onOpened: () -> Unit,
    onAbandoned: () -> Unit,
): Modifier {
    val engage by rememberUpdatedState(onEngaged)
    val opened by rememberUpdatedState(onOpened)
    val abandoned by rememberUpdatedState(onAbandoned)

    return this
        // Hand-written rather than `draggable`, and for one reason: the fire
        // is the largest and most central object in the room, and the room
        // scrolls. `draggable` claims the gesture in *both* directions once
        // slop is passed, and a child wins that pass over the scroll it sits
        // in — so a thumb put on the fire and swiped down to read back up the
        // page moved nothing at all, while quietly building a whole reading
        // screen and tearing it down again.
        //
        // Only an upward pull is ours. A downward one is never consumed, so
        // it falls through to the room, which is what a finger on the middle
        // of a page expects.
        .pointerInput(sheet) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)

                var ours = false
                val past = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over < 0f) {
                        ours = true
                        change.consume()
                    }
                }
                if (!ours || past == null) return@awaitEachGesture

                // The book has to exist before it can rise, so taking hold of
                // the fire is what puts it there — composed, at zero, under
                // the room.
                engage()
                sheet.engage()
                velocity.addPosition(past.uptimeMillis, past.position)
                sheet.drag(-past.positionChange().y)

                verticalDrag(past.id) { change ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    sheet.drag(-change.positionChange().y)
                    change.consume()
                }

                // A pull let go of short of the commit is not a close — it is
                // an opening that did not happen, and the page it raised has
                // to be taken back out of the tree. Leaving it composed is
                // not invisible: it would hold the back gesture and go on
                // being a screen nobody can see.
                sheet.release(
                    velocity = -velocity.calculateVelocity().y,
                    onOpened = opened,
                    onClosed = abandoned,
                )
            }
        }
        // The gesture's tap equivalent, and the only thing a screen reader is
        // offered here: a custom click action on a node that is otherwise an
        // inert object. The fire keeps its own "The fire is steady." label —
        // this adds the action, it does not replace the sentence.
        //
        // **Merging is what makes it reachable.** `CampfireView` clears and
        // sets its own semantics, which leaves it an unmerged leaf carrying
        // content — so it takes the screen-reader focus for itself and this
        // node, with the action on it, is never landed on. Merged, the fire's
        // own sentence and the action are one stop.
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(label = label) {
                onEngaged()
                sheet.animate(open = true, onOpened = onOpened)
                true
            }
        }
}

/**
 * This element is the handle that pulls the book closed: the Wave, at the
 * foot of the book.
 *
 * Downward drag closes. Unlike the opening handle this one does not own its
 * own tap — the Wave has been a tappable way out since S02 was written, and
 * that tap stays exactly where it was.
 */
@Composable
fun Modifier.closesTheBook(
    sheet: BookSheet,
    onClosed: () -> Unit,
): Modifier {
    val state = rememberDraggableState { delta -> sheet.drag(-delta) }
    return this.draggable(
        state = state,
        orientation = Orientation.Vertical,
        onDragStarted = { sheet.engage() },
        onDragStopped = { velocity ->
            sheet.release(-velocity, onOpened = {}, onClosed = onClosed)
        },
    )
}
