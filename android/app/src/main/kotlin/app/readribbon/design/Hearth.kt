package app.readribbon.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
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
    private val still: Boolean,
) {

    /** 0 closed, 1 open. Never outside that. */
    internal val pull: Animatable<Float, AnimationVector1D> = Animatable(0f)

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
    val progress: Float get() = pull.value

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
     * Take hold. Called when a drag starts on either handle; composes the
     * book so there is something to pull.
     */
    internal fun engage() {
        engaged = true
    }

    /** Move by [delta] pixels of pull — positive opens. */
    internal fun drag(delta: Float) {
        val next = (pull.value + delta / travel).coerceIn(0f, 1f)
        scope.launch { pull.snapTo(next) }
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
        val far = pull.value >= RibbonMotion.OPEN_COMMIT
        val flung = velocity > RibbonMotion.OPEN_FLING
        val shoved = velocity < -RibbonMotion.OPEN_FLING
        val opening = (far || flung) && !shoved
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
        scope.launch { pull.snapTo(0f) }
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
        scope.launch {
            // Interrupted by another drag or another settle, `animateTo`
            // throws through the mutator mutex and this coroutine ends —
            // which is what should happen. The callbacks below belong to the
            // movement that actually finished.
            pull.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = RibbonMotion.cover(still),
                initialVelocity = initialVelocity,
            )
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
    val still = rememberReduceMotion()
    return remember(scope, still) { BookSheet(scope, still) }
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
    val state = rememberDraggableState { delta -> sheet.drag(-delta) }
    return this
        .draggable(
            state = state,
            orientation = Orientation.Vertical,
            // The book has to exist before it can rise, so taking hold of the
            // fire is what puts it there — composed, at zero, under the room.
            // Letting go short of the commit takes it away again.
            onDragStarted = {
                onEngaged()
                sheet.engage()
            },
            onDragStopped = { velocity ->
                // A pull let go of short of the commit is not a close — it is
                // an opening that did not happen, and the page it raised has
                // to be taken back out of the tree. Leaving it composed is
                // not invisible: it would hold the back gesture and go on
                // being a screen nobody can see.
                sheet.release(-velocity, onOpened = onOpened, onClosed = onAbandoned)
            },
        )
        // The gesture's tap equivalent, and the only thing a screen reader is
        // offered here: a custom click action on a node that is otherwise an
        // inert object. The fire keeps its own "The fire is steady." label —
        // this adds the action, it does not replace the sentence.
        .semantics {
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
