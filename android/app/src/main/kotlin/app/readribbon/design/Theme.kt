package app.readribbon.design

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
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = when {
                pressed -> PRESSED
                focused -> FOCUSED
                hovered -> HOVERED
                else -> 0f
            }
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
    ) {
        MaterialTheme(
            colorScheme = RibbonColors,
            typography = RibbonTypography,
            content = content,
        )
    }
}
