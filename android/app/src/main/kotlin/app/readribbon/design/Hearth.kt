package app.readribbon.design

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    internal fun release(
        velocity: Float,
        onOpened: () -> Unit,
        onClosed: () -> Unit,
        /**
         * How far this particular hand has to have moved the book, as a
         * fraction of [travel].
         *
         * A parameter rather than the constant, because the two handles do
         * not have the same amount of screen to work in and never did. See
         * [closesTheBook] for what that cost the way out.
         */
        commit: Float = RibbonMotion.OPEN_COMMIT,
    ) {
        val moved = position - grabbed
        val opening = when {
            // Intent first: a flick says the person knows the gesture, and
            // making them drag the whole distance anyway is the app not
            // believing them.
            velocity > RibbonMotion.OPEN_FLING -> true
            velocity < -RibbonMotion.OPEN_FLING -> false
            // Then distance, measured from where the hand took hold — so the
            // threshold is the same threshold in both directions.
            moved >= commit -> true
            moved <= -commit -> false
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
 * How far the Wave has to be pulled down before letting go closes the book.
 *
 * **An absolute distance, and that is the whole fix.** [RibbonMotion.OPEN_COMMIT]
 * is a fifth of [RibbonMotion.OPEN_TRAVEL], which on a tall phone is about
 * ninety dp of finger. That is a comfortable pull *upward from the fire*,
 * which sits in the middle of the room. It is not a pull that exists at all
 * from the Wave, which sits at the foot of the page with the navigation bar
 * under it: there is nowhere near ninety dp of glass below it to drag
 * through. So the distance test could never pass, only the flick could, and
 * the way out worked if you threw it and did nothing if you pulled it.
 *
 * The owner's report — *"the Ribbon icon at the bottom of the screen looks
 * like there's some sort of interaction happening, but it doesn't work very
 * well"* — is that exactly: the page moves with the finger, which is the
 * interaction being seen, and then springs back, because the threshold was
 * measured against a screen this handle has no access to.
 *
 * Forty-four dp is one touch target, and it is deliberately short: this is a
 * handle. Somebody who has taken hold of the thing labelled "close the book"
 * and pulled it has said what they want, and a handle that argues about how
 * far is a handle that is in the way.
 */
private val HANDLE_COMMIT = 44.dp

/**
 * This element is the handle that pulls the book closed: the Wave, at the
 * foot of the book.
 *
 * Downward drag closes, the page following the finger, and letting go past
 * [HANDLE_COMMIT] — or flicking — finishes it.
 *
 * **Only downward.** This was `draggable`, which claims a vertical gesture in
 * *both* directions once slop is passed, and an upward drag on the Wave has
 * nowhere to go: the book is already fully open, [BookSheet.drag] clamps, and
 * the gesture was swallowed to move nothing at all. A control that eats a
 * drag and does nothing with it is the worst of both — it is not inert, and
 * it is not working. So an upward drag is never consumed here and falls
 * through, exactly as the fire's handle already declines a downward one.
 *
 * Unlike the opening handle this one does not own its own tap — the Wave has
 * been a tappable way out since S02 was written, and that tap stays exactly
 * where it was.
 */
@Composable
fun Modifier.closesTheBook(
    sheet: BookSheet,
    onClosed: () -> Unit,
): Modifier {
    val closed by rememberUpdatedState(onClosed)
    val commitPx = with(LocalDensity.current) { HANDLE_COMMIT.toPx() }

    return this.pointerInput(sheet) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)

            var ours = false
            val past = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                if (over > 0f) {
                    ours = true
                    change.consume()
                }
            }
            if (!ours || past == null) return@awaitEachGesture

            sheet.engage()
            velocity.addPosition(past.uptimeMillis, past.position)
            sheet.drag(-past.positionChange().y)

            verticalDrag(past.id) { change ->
                velocity.addPosition(change.uptimeMillis, change.position)
                sheet.drag(-change.positionChange().y)
                change.consume()
            }

            sheet.release(
                velocity = -velocity.calculateVelocity().y,
                onOpened = {},
                onClosed = closed,
                // The travel is the screen's; the commit is this handle's.
                commit = if (sheet.travel > 0f) commitPx / sheet.travel else 1f,
            )
        }
    }
}

// MARK: The other direction
//
// Owner: *"dragging down from the fire should do something. Maybe that should
// open profiles or settings. I think settings would be best, or the room
// settings. It should be in-depth room settings when you drag down from the
// fire rather than up."*
//
// The hearth already answers an upward pull with the book. A downward one was
// deliberately never consumed — `opensTheBook` says so in its own comment,
// because `draggable` claims both directions and a thumb put on the fire and
// swiped down to scroll the room moved nothing at all while quietly building
// a whole reading screen. That decision stands: **this only takes the gesture
// when the room has nothing left to scroll up into.** At the top of the room
// a downward drag has nowhere to go and is free; anywhere else it is the
// scroll's, exactly as before.
//
// Symmetry is the point of putting it here rather than on a button. Up is the
// book — the thing this room is for. Down is the room itself — who is in it,
// what it is called, what it reads. One object, two directions, and neither
// of them a menu you have to go and find.

/**
 * How far the fire has to be pulled down before letting go opens the room.
 *
 * An absolute distance for the same reason [HANDLE_COMMIT] is one, and a
 * longer one: this gesture starts from the middle of the screen with the
 * whole lower half to travel through, and it must not fire on the flick of a
 * thumb that meant to scroll.
 */
private val ROOM_COMMIT = 96.dp

/**
 * How far the hearth actually sinks, at most.
 *
 * Far less than the pull itself. The hearth is not going anywhere — it comes
 * back — so this is resistance, the feel of a thing on a spring rather than a
 * thing being moved, and it is what stops the gesture being another invisible
 * one. §9.1 forbids overshoot, not resistance.
 */
private val ROOM_SINK = 28.dp

/**
 * How far the fire has been pulled down, for the hearth to lean on.
 *
 * The same shape as [BookSheet] and for the same reasons: a plain value
 * written synchronously from the pointer handler, read inside a layer block,
 * and a [Job] for the settle so a new drag can take it off one already
 * running. See [BookSheet.position] for why this is not an `Animatable`.
 */
@Stable
class RoomPull internal constructor(private val scope: CoroutineScope) {

    private var sunk by mutableFloatStateOf(0f)
    private var settling: Job? = null

    /** Pixels the hearth is down by. Read inside a layer block, never as state. */
    val offset: Float get() = sunk

    internal fun sink(to: Float) {
        settling?.cancel()
        sunk = to
    }

    internal fun letGo(still: Boolean) {
        settling?.cancel()
        settling = scope.launch {
            animate(
                initialValue = sunk,
                targetValue = 0f,
                animationSpec = RibbonMotion.handled(still),
            ) { value, _ -> sunk = value }
        }
    }
}

@Composable
fun rememberRoomPull(): RoomPull {
    val scope = rememberCoroutineScope()
    return remember(scope) { RoomPull(scope) }
}

/**
 * This element is the handle that pulls the room open: the fire again, the
 * other way.
 *
 * @param enabled whether the gesture is this element's to take. The room's
 *   scroll comes first — see the note above.
 * @param label what a screen reader is told the tap equivalent does (§11).
 *   The tap lives on the same node as the book's, as a second custom action,
 *   because a gesture without one is not shippable.
 */
@Composable
fun Modifier.opensTheRoom(
    pull: RoomPull,
    label: String,
    enabled: () -> Boolean,
    onOpened: () -> Unit,
): Modifier {
    val opened by rememberUpdatedState(onOpened)
    val takeable by rememberUpdatedState(enabled)
    val still = rememberReduceMotion()
    val commitPx = with(LocalDensity.current) { ROOM_COMMIT.toPx() }
    val sinkPx = with(LocalDensity.current) { ROOM_SINK.toPx() }

    return this
        .pointerInput(pull) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!takeable()) return@awaitEachGesture

                var ours = false
                val past = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) {
                        ours = true
                        change.consume()
                    }
                }
                if (!ours || past == null) return@awaitEachGesture

                var pulled = past.positionChange().y
                // A square-root falloff rather than a fraction: the first
                // millimetre moves the hearth almost as far as the finger, so
                // the gesture answers at once, and the last centimetre barely
                // moves it at all, so the cap is arrived at rather than hit.
                fun resist(d: Float): Float =
                    sinkPx * kotlin.math.sqrt((d / commitPx).coerceIn(0f, 1f))

                pull.sink(resist(pulled))
                verticalDrag(past.id) { change ->
                    pulled += change.positionChange().y
                    pull.sink(resist(pulled))
                    change.consume()
                }

                pull.letGo(still)
                if (pulled >= commitPx) opened()
            }
        }
        // §11's tap equivalent, as a **custom action** and not a second
        // `onClick`. `onClick` is one slot in a node's semantics, and this
        // modifier shares its node with `opensTheBook`, which already fills
        // it with the book — a second one does not sit beside the first, it
        // takes its place, and the app's front door would have quietly
        // stopped working for a screen reader.
        //
        // The visible equivalent is the room's name at the top of the screen,
        // which has opened this same screen since S01 was written. That is
        // also why the gesture gets no hint of its own (§6.1): it is a
        // shortcut to something already on the screen, not the only way in.
        .semantics {
            customActions = listOf(CustomAccessibilityAction(label) { opened(); true })
        }
}
