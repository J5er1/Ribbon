package app.readribbon.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier.Node
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 12.2 — Material 3 Expressive, taken for its shape system, its motion
// physics — and now its colour system too.
//
// §12.2 declined Material You: "a Ribbon tinted lavender because someone's
// wallpaper is lavender is not Ribbon." The owner overruled that in
// September 2026, and the reasoning is worth keeping rather than quietly
// deleting: the argument for a fixed palette is an argument about the brand,
// and the argument against it is an argument about the person holding the
// phone. Material You is what the Android build was for. A room that takes
// its colour from the wallpaper is a room in *their* house.
//
// So it is on, unharmonised: the six room roles are read straight off
// `dynamicDarkColorScheme`, with no blending back toward chartreuse. What
// does not move is the fire and the eight inks, and Palette.kt says why.
//
// The way back is [Appearance.wallpaperColour] — one switch in Appearance
// (S26), off for Ribbon's own chartreuse. S18's "no accent picker: chartreuse
// is the brand's, not the user's" survives that: this offers the wallpaper's
// colours or the brand's, and never a colour a person chose by hand.

/**
 * The room, in Ribbon's own paint: chartreuse on true black with ivory.
 */
private val BrandScheme = darkColorScheme(
    primary = Brand.chartreuse,
    onPrimary = Brand.ground,
    secondary = Brand.chartreuse,
    onSecondary = Brand.ground,
    background = Brand.ground,
    onBackground = Brand.text,
    surface = Brand.surface,
    onSurface = Brand.text,
    surfaceVariant = Brand.raised,
    onSurfaceVariant = Brand.muted,
    surfaceContainerLowest = Brand.ground,
    surfaceContainerLow = Brand.surface,
    surfaceContainer = Brand.surface,
    surfaceContainerHigh = Brand.raised,
    surfaceContainerHighest = Brand.raised,
    outline = Brand.rule,
    outlineVariant = Brand.rule,
    // The product produces no error surfaces in its own colour — §S25's
    // failure copy is set in the ordinary voice on the ordinary ground.
    error = Palette.flameDeep,
    onError = Brand.text,
    scrim = Brand.ground,
)

/**
 * The six room roles, read off a Material scheme.
 *
 * The mapping is the tonal one Material already draws with, taken at face
 * value: the darkest container is the unlit ground, the ordinary container
 * is a card, the high container is a chip. Nothing is blended, clamped or
 * pulled back toward chartreuse — that was the choice, and a half-dynamic
 * palette would look like neither thing.
 *
 * The one thing that *is* checked is separation, and it is not a matter of
 * taste. Some wallpapers extract to a scheme whose lowest container and
 * ordinary container are within a hair of each other, and the app's whole
 * new register — a card is a shade paler than the ground — would vanish on
 * those phones and nowhere else. So the card takes the first tonal step that
 * can actually be seen against the ground, and the chip takes the first one
 * above that. On the great majority of wallpapers this changes nothing.
 */
private fun ColorScheme.asRoom(): RoomColours {
    val ground = surfaceContainerLowest
    val steps = listOf(
        surfaceContainerLow,
        surfaceContainer,
        surfaceContainerHigh,
        surfaceContainerHighest,
    )
    val separated = steps.firstOrNull { it.separatedFrom(ground) }
    // Nothing above the ground can be told from it: the card draws its own
    // edge instead of pretending a fill it does not have. Ribbon's own
    // palette is permanently in this case by design (Palette.kt), and some
    // extracted wallpapers land in it too.
    val card = separated ?: surfaceContainerHighest
    val chip = steps.drop(steps.indexOf(card) + 1).firstOrNull { it.separatedFrom(card) }
        ?: card.lifted()
    return RoomColours(
        ground = ground,
        surface = card,
        raised = chip,
        text = onSurface,
        muted = onSurfaceVariant,
        rule = outlineVariant,
        accent = primary,
        onAccent = onPrimary,
        tileNeedsEdge = separated == null,
    )
}

/**
 * Far enough apart to be a different surface rather than a rendering
 * artefact.
 *
 * Luminance rather than a channel difference, because the eye reads a step
 * in lightness and not a step in blue. The threshold is deliberately small:
 * the room is meant to be nearly flat, and this is the floor under "nearly",
 * not a contrast ratio.
 */
private fun Color.separatedFrom(other: Color): Boolean =
    abs(luminance() - other.luminance()) >= 0.012f

/**
 * One more step of lightness, by hand, for the scheme that had no room left
 * above the card. A white wash rather than a lighter tone, because there is
 * no lighter tone to ask the scheme for.
 */
private fun Color.lifted(): Color = Color(
    red = (red + 0.055f).coerceAtMost(1f),
    green = (green + 0.055f).coerceAtMost(1f),
    blue = (blue + 0.055f).coerceAtMost(1f),
    alpha = alpha,
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
    ) : Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

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

        /**
         * The wash the last interaction *asked* for, as opposed to the one the
         * `Animatable` has actually started on.
         *
         * These are not the same thing, and the difference is a stuck wash. A
         * press and its release are emitted back to back into the interaction
         * flow and are both handled before the press's own animation has been
         * dispatched — so at the release `wash.targetValue` is still 0 and the
         * animation is not yet running. Guarding on it, the release reads as
         * "already going to 0, nothing to do" and returns; the press's
         * animation then runs anyway, and the row is left washed for good.
         * A fast tap on a settings row would leave a band across it until the
         * row was touched again. What was asked for is the honest guard.
         */
        private var wanted = 0f

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
            if (target == wanted) return
            wanted = target
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
                // Read at draw time rather than captured: the room's ink
                // follows the wallpaper now, and a state layer that had
                // kept a copy from whenever the node attached would be
                // washing a dynamic room in ivory.
                val ink = currentValueOf(LocalRoomColours).text
                drawRect(color = ink.copy(alpha = alpha))
            }
        }
    }
}

/** The room's haptics, hung off the composition so a screen can just ask. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
fun RibbonTheme(
    haptics: Haptics? = null,
    /**
     * The appearance preferences. The app has exactly one and never passes
     * it; a harness that wants to draw the same screen on both palettes
     * passes one it can flip between frames.
     */
    appearance: Appearance? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolved = appearance ?: remember(context) { Appearance(context) }

    // Read once per wallpaper change. `dynamicDarkColorScheme` walks the
    // wallpaper's extracted palette, which is not free and does not change
    // between frames; the configuration is the key because that is what
    // changes when somebody picks a new wallpaper or flips the system theme.
    val configuration = LocalConfiguration.current
    val scheme = remember(context, configuration, resolved.wallpaperColour) {
        // minSdk is 33, so dynamic colour is always there to ask for
        // (deviation A1) — there is no pre-Android-12 branch to write.
        if (resolved.wallpaperColour) dynamicDarkColorScheme(context) else BrandScheme
    }
    val room = remember(scheme) { scheme.asRoom() }

    CompositionLocalProvider(
        LocalIndication provides RibbonIndication,
        LocalContentColor provides room.text,
        LocalHaptics provides haptics,
        LocalAppearance provides resolved,
        LocalRoomColours provides room,
        // Asked of the system once, here, and read by every screen through
        // `rememberReduceMotion()` — see Components.kt.
        LocalReduceMotion provides observeReduceMotion(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RibbonTypography,
            shapes = RibbonShapes,
            content = content,
        )
    }
}
