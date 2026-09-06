package app.readribbon.fire

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.rememberReduceMotion
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

// An ember — what you keep when you finish a book (§4.8). The flame goes
// down; the light stays. On the shelf, embers sit on a shared baseline with
// a faint warm bloom beneath them: light on a surface implies the surface,
// so no shelf is drawn.

/**
 * The ember redraws twelve times a second, no faster: SwiftUI asks its
 * timeline for `minimumInterval: 1.0 / 12.0`, and a shelf can hold a whole
 * grid of these. The frame clock here runs at the display's rate, so the
 * throttle is kept by hand.
 */
private const val EMBER_FRAME_INTERVAL_NANOS = 1_000_000_000L / 12

/** The ember breathes at a quarter of the fire's clock — slower than flame. */
private const val EMBER_TIME_SCALE = 0.25

/**
 * iOS's `.regular` horizontal size class, said in Android's terms: a window
 * at least 600 dp wide, the medium/expanded boundary, which is where a
 * tablet's room begins. Read from the window rather than the device, so an
 * app in a narrow split is a phone's room again — exactly how the size class
 * behaves.
 */
@Composable
private fun isRegularWidth(): Boolean {
    val containerWidth = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { containerWidth.toDp() } >= 600.dp
}

@Composable
fun EmberView(
    scale: FireScale,
    modifier: Modifier = Modifier,
) {
    val seed = rememberSaveable { Random.nextDouble(0.0, 1000.0) }
    val reduceMotion = rememberReduceMotion()

    /**
     * Diameter relative to the fire the ember was. The whole point of the
     * shelf is that Isaiah looks like Isaiah — and on a tablet the ember
     * carries the fire's same 1.45× (CampfireView), so the finishing settle
     * doesn't shrink mid-become.
     */
    val emberSize = (scale.frameHeight * 0.30 * if (isRegularWidth()) 1.45 else 1.0).dp

    // Reduce-motion holds one instant instead of flickering (§11); the drawn
    // frame is the seed's, so a held ember is still an ember and never a
    // roll of the dice.
    val time by produceState(seed, seed, reduceMotion) {
        if (reduceMotion) return@produceState
        var lastFrame = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (nanos - lastFrame >= EMBER_FRAME_INTERVAL_NANOS) {
                    lastFrame = nanos
                    value = nanos / 1e9 * EMBER_TIME_SCALE + seed
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(width = emberSize * 1.7f, height = emberSize * 1.35f)
            .clearAndSetSemantics {},
    ) {
        drawEmber(time = time)
    }
}

/**
 * The ember, drawn. Kept apart from the composable the way Swift keeps it a
 * `static func`: the drawing is pure, so the held frame and the animated one
 * go through the same code.
 *
 * [time] is a seconds-like clock. It leans on [FirePainter.breath] rather
 * than carrying its own maths — the shared signature is load-bearing, and an
 * ember that breathed to a different curve than the fire it came from would
 * give the become away.
 */
fun DrawScope.drawEmber(time: Double) {
    val width = size.width
    val height = size.height
    val cx = width / 2f
    val cy = height * 0.52f
    val r = min(width, height) * 0.32f
    val pulse = 0.85 + 0.1 * FirePainter.breath(time, 0.37)

    // The bloom beneath — the light that implies the shelf.
    val bloomRadius = r * 2.4f
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Palette.flameDeep.copy(alpha = (0.20 * pulse).toFloat()),
                Color.Transparent,
            ),
            center = Offset(cx, height * 0.8f),
            radius = bloomRadius,
        ),
        topLeft = Offset(cx - bloomRadius, height * 0.66f),
        size = Size(width = r * 4.8f, height = r * 1.5f),
    )

    // The ember itself: a warm heart in a dark husk.
    //
    // SwiftUI's radial gradient takes a start radius as well as an end one;
    // Compose's takes only the end, so the stops are remapped onto
    // [0, endRadius] by hand — solid out to the start radius, then the three
    // colours spread evenly across what remains. The same gradient, stated
    // differently.
    val huskInner = (r * 0.05f) / (r * 1.1f)
    val heart = Palette.flameCore.copy(alpha = (0.95 * pulse).toFloat())
    drawOval(
        brush = Brush.radialGradient(
            0f to heart,
            huskInner to heart,
            huskInner + (1f - huskInner) * 0.5f to Palette.coal.copy(alpha = 0.95f),
            1f to Palette.coalDim,
            center = Offset(cx - r * 0.2f, cy - r * 0.15f),
            radius = r * 1.1f,
        ),
        topLeft = Offset(cx - r, cy - r * 0.82f),
        size = Size(width = r * 2f, height = r * 1.64f),
    )

    // A seam of heat.
    val seam = Path().apply {
        moveTo(cx - r * 0.55f, cy + r * 0.1f)
        quadraticTo(
            x1 = cx, y1 = cy + r * 0.35f,
            x2 = cx + r * 0.5f, y2 = cy - r * 0.12f,
        )
    }
    drawPath(
        path = seam,
        color = Palette.flameBright.copy(alpha = (0.5 * pulse).toFloat()),
        style = Stroke(width = r * 0.09f, cap = StrokeCap.Round),
    )
}

/**
 * The finishing sequence's centerpiece (§6.5): the fire, drawn large one
 * last time, settling into an ember over ~2.5 s. The flame goes down; the
 * light stays. It goes down the way a real fire does — through smaller: the
 * steady fire gives way to a few last licks before the ember, so the
 * settling reads as subsiding, not a projector cross-fade. The coal beds of
 * the two fires share their seeded geometry, so what actually changes under
 * the cross-fade is only the flame. Same machinery as before — one piece of
 * state and one task; the licks are just a middle value of it.
 */
@Composable
fun FireBecomesEmber(
    scale: FireScale,
    coalDepth: Double,
    modifier: Modifier = Modifier,
) {
    /** 0 = the fire as it was, 1 = the last licks, 2 = the ember. */
    var settling by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()

    // SwiftUI's `.animation(_:value:)` on the stack becomes one shared spec
    // driving the three opacities — the cross-fade is the animation, and
    // nothing else about these views moves.
    val crossFade: AnimationSpec<Float> = tween(
        durationMillis =
            if (reduceMotion) 400 else (RibbonMotion.BECOME_MS * 0.44).toInt(),
        easing = RibbonMotion.EaseInOut,
    )
    val fireAlpha by animateFloatAsState(
        if (settling == 0) 1f else 0f, crossFade, label = "fire",
    )
    val licksAlpha by animateFloatAsState(
        if (settling == 1) 1f else 0f, crossFade, label = "licks",
    )
    val emberAlpha by animateFloatAsState(
        if (settling == 2) 1f else 0f, crossFade, label = "ember",
    )

    LaunchedEffect(reduceMotion) {
        delay(600)
        if (reduceMotion) {
            // One quiet cross-fade; the intermediate flare is motion.
            settling = 2
            return@LaunchedEffect
        }
        settling = 1
        delay(1100)
        settling = 2
    }

    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = Copy.THE_FIRE_SETTLES_INTO_AN_EMBER
        },
        contentAlignment = Alignment.Center,
    ) {
        CampfireView(
            state = FireState.steady,
            scale = scale,
            coalDepth = coalDepth,
            modifier = Modifier.graphicsLayer { alpha = fireAlpha },
        )
        CampfireView(
            state = FireState.catching,
            scale = scale,
            coalDepth = coalDepth,
            modifier = Modifier.graphicsLayer { alpha = licksAlpha },
        )
        EmberView(
            scale = scale,
            modifier = Modifier.graphicsLayer { alpha = emberAlpha },
        )
    }
}
