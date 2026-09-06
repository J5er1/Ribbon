package app.readribbon.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

// Motion tokens (build book §9.1). Everything breathes rather than snaps:
// gentle ease-out, no bounce, no spring overshoot, no parallax. One thing in
// the product is allowed to be fast — the thinking-of-you haptic.
//
// The split with the platform (§12.2): Material 3 Expressive's physics-based
// motion is used damped, with damping near critical, so motion is physical
// but never overshoots. Everything Ribbon itself draws obeys the tokens
// below. Where Expressive would spring, Ribbon eases.

object RibbonMotion {
    /** SwiftUI's .easeOut, matched so the two apps move alike. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val ARRIVE_MS = 320
    const val SETTLE_MS = 400
    const val OPEN_MS = 480
    const val BECOME_MS = 2500
    const val INK_FILL_MS = 700

    /** Presence appearing, sheets, cross-fades between rooms. */
    fun <T> arrive(): AnimationSpec<T> = tween(ARRIVE_MS, easing = EaseOut)

    /** Notes unfurling, the book closing, screen pushes. */
    fun <T> settle(): AnimationSpec<T> = tween(SETTLE_MS, easing = EaseOut)

    /** Cards turning, the presence panel expanding. */
    fun <T> open(): AnimationSpec<T> = tween(OPEN_MS, easing = EaseOut)

    /** Fire → ember, once per book. */
    fun <T> become(): AnimationSpec<T> = tween(BECOME_MS, easing = EaseInOut)

    /** The thinking-of-you fill. */
    fun <T> inkFill(): AnimationSpec<T> = tween(INK_FILL_MS, easing = EaseInOut)

    /**
     * The fire's breath — a period, not an animation. The flicker is drawn
     * from the frame clock and is never synchronised across elements, so no
     * two flames beat together.
     */
    const val FLICKER_PERIOD_SECONDS = 3.4f
}
