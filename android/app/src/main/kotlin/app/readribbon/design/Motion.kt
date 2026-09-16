package app.readribbon.design

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Motion tokens (build book §9.1). Everything breathes rather than snaps:
// gentle ease-out, no bounce, no spring overshoot, no parallax. One thing in
// the product is allowed to be fast — the thinking-of-you haptic.
//
// The split with the platform (§12.2): Material 3 Expressive's physics-based
// motion is used damped, with damping near critical, so motion is physical
// but never overshoots. Everything Ribbon itself draws obeys the tokens
// below.
//
// The division of labour between the two halves of this file: a thing that
// simply *changes* gets a tween — a word swapping under the fire, a
// cross-dissolve between rooms, a note unfurling. A thing a finger is
// *holding* gets a spring, because a spring can be handed the velocity the
// finger let go at and a tween cannot. The hearth pulling the book open, the
// Wave pulling it closed and a card taking a press are all on the springs at
// the foot of this object.
//
// The one gesture that is not, and it is not an oversight: the predictive
// back peel below. Android hands a back gesture along as a *progress* and
// never as a speed — `BackEventCompat` carries a fraction and a touch point
// and nothing else — so there is no velocity to give a spring. Its two ends
// stay on the tweens they were written with.
//
// Every token comes in two: the motion, and the same thing held still for
// reduce motion (§11). A screen asks for `RibbonMotion.settle(reduceMotion)`
// rather than writing the branch out, so that "under reduce motion this is a
// cut" is decided in one place instead of ten — it used to be written by hand
// in every file that animated anything, and one of them had forgotten to.

object RibbonMotion {
    /** SwiftUI's .easeOut, matched so the two apps move alike. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val ARRIVE_MS = 320
    const val SETTLE_MS = 400
    const val OPEN_MS = 480
    const val BECOME_MS = 2500
    const val INK_FILL_MS = 700

    /**
     * A back gesture let go of halfway.
     *
     * Quicker than [ARRIVE_MS], because nothing is arriving: the screen was
     * never going anywhere and is being put back where it already was. Slow
     * enough that it is a return rather than a rebound.
     */
    const val RELEASE_MS = 220

    /**
     * The state layer's two halves (§12.2).
     *
     * A finger is already on the glass when the wash comes up, so it comes up
     * almost at once — feedback that lags a touch reads as a dropped touch.
     * It goes down at leisure, because by then the finger has gone and the
     * fade is the only thing left saying the tap was received.
     */
    const val PRESS_IN_MS = 90
    const val PRESS_OUT_MS = 220

    /**
     * How far a screen drawn over the room recedes as a back gesture pulls it
     * off: 6% smaller, 24 dp down, and 28% of the way toward the ground.
     *
     * One set of numbers for the book, the menu and the presence panel, so
     * that everything comes away from the room the same way. They were three
     * copies of the same three magic numbers, which is how they drift.
     */
    const val PEEL_SHRINK = 0.06f
    const val PEEL_FADE = 0.28f
    val PEEL_LIFT: Dp = 24.dp

    /** Presence appearing, sheets, cross-fades between rooms. */
    fun <T> arrive(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(ARRIVE_MS, easing = EaseOut)

    /** Notes unfurling, the book closing, screen pushes. */
    fun <T> settle(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(SETTLE_MS, easing = EaseOut)

    /** Cards turning, the presence panel expanding. */
    fun <T> open(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(OPEN_MS, easing = EaseOut)

    /** A back gesture, released: back where it was. */
    fun <T> release(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(RELEASE_MS, easing = EaseOut)

    /** Fire → ember, once per book. */
    fun <T> become(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(BECOME_MS, easing = EaseInOut)

    /** The thinking-of-you fill. */
    fun <T> inkFill(still: Boolean = false): FiniteAnimationSpec<T> =
        if (still) snap() else tween(INK_FILL_MS, easing = EaseInOut)

    /**
     * The fire's breath — a period, not an animation. The flicker is drawn
     * from the frame clock and is never synchronised across elements, so no
     * two flames beat together.
     */
    const val FLICKER_PERIOD_SECONDS = 3.4f

    // MARK: Springs — the half of §12.2 that had never been used
    //
    // "Physics-based motion: yes, damped. Use the spatial spring tokens with
    // damping near critical, so motion is physical but never overshoots."
    // Everything above is a tween, and a tween is right for a thing that
    // simply changes — a word, a fade, a cross-dissolve. It is wrong for a
    // thing a finger is holding.
    //
    // A drag has a velocity when it is let go of, and a tween throws that
    // away: the book would leave the finger's speed behind and travel at the
    // curve's instead, which is the single most common way a gesture reads
    // as cheap. A spring takes the velocity as its initial condition and
    // carries it, so the movement the finger started is the movement that
    // finishes.
    //
    // Damping ratio 1.0 is critical — the fastest approach that never
    // crosses the target — which is how the product gets physical motion
    // without the overshoot §9.1 forbids. Nothing here bounces.

    /** Critically damped: physical, and it never passes the mark. */
    const val DAMPING = 1f

    /**
     * A thing the size of the screen: the book rising, the menu arriving.
     * Low stiffness, because a big object is slow.
     */
    fun <T> cover(still: Boolean = false): AnimationSpec<T> =
        if (still) {
            snap()
        } else {
            spring(dampingRatio = DAMPING, stiffness = 180f)
        }

    /**
     * A thing the size of a hand: a card lifting, the hearth swelling under
     * a press, a row settling into place.
     */
    fun <T> handled(still: Boolean = false): AnimationSpec<T> =
        if (still) {
            snap()
        } else {
            spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMediumLow)
        }

    /**
     * A thing the size of a fingertip: a switch, a dot moving down a list, a
     * chip taking a press. Quick enough to feel like a direct response.
     */
    fun <T> touched(still: Boolean = false): AnimationSpec<T> =
        if (still) {
            snap()
        } else {
            spring(dampingRatio = DAMPING, stiffness = Spring.StiffnessMedium)
        }

    // MARK: The hearth gesture (S01 → S02)

    /**
     * How far up the fire has to be pulled before letting go opens the book:
     * a third of the way.
     *
     * Deliberately short. The gesture's job is to *start* the opening, not
     * to perform all of it — asking for the whole screen's height would make
     * a two-hand gesture out of the app's front door.
     *
     * It was a third, and a third was too far: against [OPEN_TRAVEL] on a
     * tall phone that is about 160 dp of pull, from an object that already
     * sits halfway down the screen. A thumb runs out of room before the book
     * runs out of threshold, which is exactly how a gesture comes to feel
     * finicky — it works, but only if you commit to it twice. At a fifth it
     * is a comfortable single pull with the screen to spare.
     */
    const val OPEN_COMMIT = 0.19f

    /**
     * The flick that opens the book however far it got: px/s, upward.
     *
     * Below this, distance decides. Above it, intent does — a fast short
     * flick is somebody who knows the gesture, and making them drag the
     * whole distance anyway is the app not believing them.
     *
     * Lowered with [OPEN_COMMIT] and for the same reason. 900 px/s is a
     * deliberate throw; a person opening a book they open every morning
     * makes a much softer movement than that, and having it not take is the
     * gesture asking to be performed rather than used.
     */
    const val OPEN_FLING = 480f

    /**
     * How much of the room's height one full pull is worth.
     *
     * Less than all of it: a gesture that has to travel the whole screen to
     * complete reads as a long way to go, and the fire sits around a third
     * of the way down to begin with.
     */
    const val OPEN_TRAVEL = 0.55f
}

/**
 * The pull of a back gesture on something drawn over the room (§12.2).
 *
 * Android's predictive back is a *progress*, not a commitment: the book, the
 * menu and the presence panel each peel off the room as the gesture is
 * pulled, and the two ends of the gesture are what this exists to get right.
 *
 * - **Let go of.** The screen eases back where it was. Dropping the pull to
 *   nothing in a single frame is a snap, and a snap is the one thing §9.1
 *   forbids.
 * - **Committed.** The pull is handed on to whatever takes the screen away,
 *   which picks the motion up mid-flight. Un-peeling first and *then* sliding
 *   out is the same defect twice, and it reads as a bounce.
 *
 * Both of those were wrong in all three places before this existed, in the
 * same two ways, because each screen had written the gesture out again.
 */
@Stable
class BackPeel internal constructor(
    private val pull: Animatable<Float, AnimationVector1D>,
    private val still: Boolean,
) {
    /**
     * 0 where the screen sits, 1 fully peeled off the room.
     *
     * Held at 0 under reduce motion: the gesture still closes the screen, it
     * simply doesn't move it on the way (§11).
     */
    val progress: Float get() = if (still) 0f else pull.value
}

/**
 * A back gesture, as something drawn over the room should feel it.
 *
 * @param enabled whether this layer is the one back belongs to. Back
 *   callbacks are taken in reverse order of registration, so the layer that
 *   is on top registers last — and stands down the moment it starts leaving,
 *   because an exit keeps the outgoing screen composed for the whole of it
 *   and a press in that window would otherwise be eaten by a handler whose
 *   `onBack` is already a no-op.
 * @param carriesOn what a committed pull does next. A screen that *leaves*
 *   carries the pull on to a full peel, so its exit continues the gesture
 *   rather than restarting it; something that collapses back in place — the
 *   presence panel — lets the pull relax to nothing as it closes, and must,
 *   or it would still be held peeled the next time it opened.
 * @param onBack the gesture completed: let the screen go.
 */
@Composable
fun rememberBackPeel(
    enabled: Boolean = true,
    carriesOn: Boolean = true,
    onBack: () -> Unit,
): BackPeel {
    val still = rememberReduceMotion()
    val pull = remember { Animatable(0f) }

    // The settling runs on the composition's scope rather than the gesture's.
    // Both ends of a gesture end the coroutine that was collecting it —
    // committing disables the handler, and letting go cancels it outright —
    // so an animation started inside that coroutine is cancelled in the same
    // breath as the gesture it is finishing. This scope lasts exactly as long
    // as the thing being moved, which is the right lifetime for the movement.
    val settling = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { event -> pull.snapTo(event.progress) }
            onBack()
            settling.launch {
                if (carriesOn) {
                    pull.animateTo(1f, RibbonMotion.settle())
                } else {
                    pull.animateTo(0f, RibbonMotion.open())
                }
            }
        } catch (cancelled: CancellationException) {
            settling.launch { pull.animateTo(0f, RibbonMotion.release()) }
            throw cancelled
        }
    }

    return remember(pull, still) { BackPeel(pull, still) }
}

/**
 * Peel this layer off the room by [peel] — 0 for where it sits, 1 for fully
 * away — using the one set of numbers every such gesture in the app shares.
 *
 * The pull is read inside the layer block, so the gesture moves the screen
 * without recomposing a word of what is drawn on it.
 *
 * @param origin what the shrink is measured from. The default is the middle,
 *   which is right for anything that fills the screen; the presence panel
 *   shrinks back toward the edge it came out of instead.
 */
fun Modifier.peeled(
    origin: TransformOrigin = TransformOrigin.Center,
    peel: () -> Float,
): Modifier = this.graphicsLayer {
    val pull = peel()
    val shrink = 1f - RibbonMotion.PEEL_SHRINK * pull
    scaleX = shrink
    scaleY = shrink
    translationY = pull * RibbonMotion.PEEL_LIFT.toPx()
    alpha = 1f - RibbonMotion.PEEL_FADE * pull
    transformOrigin = origin
}
