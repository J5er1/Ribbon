package app.readribbon.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier.Node
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 12.2 — Material 3 Expressive, taken for its shape system and its motion
// physics, with its colour system declined.
//
// Ribbon opts out of dynamic colour. Material You would repaint the app from
// the user's wallpaper, and Ribbon's entire visual argument is one accent on
// one ground: a Ribbon tinted lavender because someone's wallpaper is
// lavender is not Ribbon. The constant below exists so that the opt-out is
// explicit rather than an omission — so nobody turns it on later thinking it
// was an oversight.
const val USE_DYNAMIC_COLOR = false

/**
 * The fixed scheme. Every role is one of the six palette values; there is no
 * light scheme to fall back to, because dark is the product, not a
 * preference (§12).
 */
private val RibbonColors = darkColorScheme(
    primary = Palette.chartreuse,
    onPrimary = Palette.ground,
    secondary = Palette.chartreuse,
    onSecondary = Palette.ground,
    background = Palette.ground,
    onBackground = Palette.text,
    surface = Palette.surface,
    onSurface = Palette.text,
    surfaceVariant = Palette.raised,
    onSurfaceVariant = Palette.muted,
    outline = Palette.rule,
    outlineVariant = Palette.rule,
    // The product produces no error surfaces in its own colour — §S25's
    // failure copy is set in the ordinary voice on the ordinary ground.
    error = Palette.flameDeep,
    onError = Palette.text,
    scrim = Palette.ground,
)

private val RibbonTypography: Typography
    @Composable get() = Typography().let { base ->
        base.copy(
            displayLarge = RibbonType.display(57f),
            displayMedium = RibbonType.display(45f),
            displaySmall = RibbonType.display(36f),
            headlineLarge = RibbonType.display(32f),
            headlineMedium = RibbonType.display(28f),
            headlineSmall = RibbonType.display(24f),
            titleLarge = RibbonType.ui(22f, FontWeight.Medium),
            titleMedium = RibbonType.ui(16f, FontWeight.Medium),
            titleSmall = RibbonType.ui(14f, FontWeight.Medium),
            bodyLarge = RibbonType.scripture(17f),
            bodyMedium = RibbonType.ui(14f),
            bodySmall = RibbonType.ui(12f),
            labelLarge = RibbonType.smallCaps(14f, FontWeight.Medium),
            labelMedium = RibbonType.smallCaps(12f, FontWeight.Medium),
            labelSmall = RibbonType.smallCaps(11f),
        )
    }

/**
 * The state layer that stands in for the ripple (§12.2).
 *
 * "Ripple replaced with a soft state layer at low opacity. No bounded ripple
 * over Scripture — a ripple across a verse is exactly the 'interface'
 * feeling Literata is there to prevent." So this draws a flat wash that
 * fades in and out with the interaction and never expands from the touch
 * point. It is deliberately not configured ripple; a ripple with its radius
 * turned down is still a ripple, and it still animates outward.
 *
 * The fade is the half that was missing. The wash used to be switched on and
 * off at full strength on the frame the touch landed and the frame it left,
 * which is a flash — and a flash across a verse is the thing this whole
 * indication exists to avoid. It is also the most-seen animation in the app:
 * every row of the menu, every settings line, every quiet control goes
 * through here, so a snap here is a snap everywhere at once. Now it comes up
 * in [RibbonMotion.PRESS_IN_MS] and goes down in [RibbonMotion.PRESS_OUT_MS].
 */
private object RibbonIndication : androidx.compose.foundation.IndicationNodeFactory {

    private const val PRESSED = 0.06f
    private const val HOVERED = 0.04f
    private const val FOCUSED = 0.10f

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        StateLayerNode(interactionSource)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this

    private class StateLayerNode(
        private val interactionSource: InteractionSource,
    ) : Node(), DrawModifierNode {

        private var pressed = false
        private var hovered = false
        private var focused = false

        /** Where the wash is now, as opposed to where the interaction wants it. */
        private val wash = Animatable(0f)

        /** The fade in flight, so a new interaction replaces it rather than races it. */
        private var fade: Job? = null

        /**
         * The strongest wash this touch asked for, held until the wash is back
         * at rest.
         *
         * A tap can be over in less time than the wash takes to come up, and a
         * press whose feedback never became visible reads as a press that was
         * never received. So the wash finishes arriving before it leaves, which
         * is the same thing Material's ripple calls a minimum duration.
         */
        private var crest = 0f

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                        is HoverInteraction.Enter -> hovered = true
                        is HoverInteraction.Exit -> hovered = false
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                    }
                    fadeToward()
                }
            }
        }

        /**
         * Animate the wash to whatever the interactions now add up to.
         *
         * The draw is invalidated from the animation itself rather than by
         * observing the value: a state layer is a draw and nothing else, so
         * nothing above it needs to recompose or re-measure for it.
         */
        private fun fadeToward() {
            val target = when {
                pressed -> PRESSED
                focused -> FOCUSED
                hovered -> HOVERED
                else -> 0f
            }
            if (target > crest) crest = target
            if (target == wash.targetValue && !wash.isRunning) return
            fade?.cancel()
            fade = coroutineScope.launch {
                if (target < crest && wash.value < crest) {
                    wash.animateTo(crest, fadeSpec(RibbonMotion.PRESS_IN_MS)) { invalidateDraw() }
                }
                val out = target <= wash.value
                wash.animateTo(
                    targetValue = target,
                    animationSpec = fadeSpec(
                        if (out) RibbonMotion.PRESS_OUT_MS else RibbonMotion.PRESS_IN_MS,
                    ),
                ) { invalidateDraw() }
                if (target == 0f) crest = 0f
            }
        }

        private fun fadeSpec(millis: Int) = tween<Float>(millis, easing = RibbonMotion.EaseOut)

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = wash.value
            if (alpha > 0f) {
                drawRect(color = Palette.text.copy(alpha = alpha))
            }
        }
    }
}

/** The room's haptics, hung off the composition so a screen can just ask. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
fun RibbonTheme(
    haptics: Haptics? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalIndication provides RibbonIndication,
        LocalContentColor provides Palette.text,
        LocalHaptics provides haptics,
        // Asked of the system once, here, and read by every screen through
        // `rememberReduceMotion()` — see Components.kt.
        LocalReduceMotion provides observeReduceMotion(),
    ) {
        MaterialTheme(
            colorScheme = RibbonColors,
            typography = RibbonTypography,
            content = content,
        )
    }
}
