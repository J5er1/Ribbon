package app.readribbon.fire

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.design.Palette
import app.readribbon.design.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.readribbon.design.RibbonMotion

// The campfire (§4.1) — a single warm object, abstract, never a cartoon
// flame, never a number anywhere near it.
//
// Law 4: the frame is fixed by the book's scale and never changes during a
// reading. The state drives the flames; the coal bed beneath deepens over a
// long read and throws a wider warm light. The flicker is a slow 3.4 s
// irregular loop, seeded per instance so no two fires — and no two
// on-screen copies — ever breathe in step.
//
// How the drawing stays honest about being fire without becoming a picture
// of one: each tongue is an outline of short curve segments whose edges are
// pushed by value noise that travels upward, so the flame necks and bulges
// the way convected air does instead of waving like a flag. Each tongue is
// three nested bodies — a turbulent deep-orange sheath, a core-orange body,
// and a short bright heart that hugs the coals — because heat lives low; a
// flame whose brightest point is its tip reads as clip-art. Everything that
// is light adds (BlendMode.Plus); only ash occludes.
//
// The one structural difference from the SwiftUI original: SwiftUI's
// GraphicsContext carries a mutable `blendMode` and a `drawLayer` that can
// take a blur filter, so the Swift sets `.plusLighter` once and leaves it
// set for a whole passage of the drawing. Compose's DrawScope has neither:
// the blend mode is an argument to each individual draw call, and there is
// no per-layer blur. So every additive draw here passes
// `blendMode = BlendMode.Plus` itself, and the two blurred passes (the
// sheath, and the hot air above the tips) go through [fillAdditive], which
// blurs one path at a time with a Skia BlurMaskFilter instead of blurring a
// whole offscreen group. Gaussian blur is linear and additive blending is
// commutative, so blurring each tongue and adding is the same picture as
// adding the tongues and blurring the group — what differs is that a mask
// filter blurs the shape's alpha rather than the composited result, which
// softens the edge without smearing the gradient inside it.

/** The 30 Hz redraw the Swift asks for with `.animation(minimumInterval:)`. */
private const val FRAME_INTERVAL_SECONDS = 1.0 / 30.0

/**
 * The width at which the room counts as a big room — the honest equivalent
 * of iOS's regular horizontal size class.
 *
 * iOS has no dp number here; it has a size class, and on iPhone that class
 * only reaches `.regular` on a landscape Plus/Max. The matching Android test
 * is the Material window size class breakpoint: a *container* (not device)
 * width of 600 dp or more is medium-or-expanded, which is a tablet, an
 * unfolded foldable, or a wide split. Measuring the window and not the
 * hardware is deliberate — a tablet holding Ribbon in a narrow split pane is
 * a small room, and the fire should be drawn for the room it is in.
 */
private val REGULAR_WIDTH = 600.dp

@Composable
fun CampfireView(
    state: FireState,
    scale: FireScale,
    /** 0...1, from `Handiwork.coalDepth`. */
    coalDepth: Double,
    modifier: Modifier = Modifier,
    /** Dim by ~8% when rendering a last-known state offline (S01). */
    dimmed: Boolean = false,
) {
    // The per-instance desync. This is the one genuinely random number in
    // the fire, exactly as in Swift (`Double.random(in: 0..<1000)`): it is
    // drawn once when the view appears and then held, so that two fires on
    // one screen never breathe together. Everything downstream of it is
    // deterministic.
    //
    // Saved rather than merely remembered: SwiftUI's `@State` survives a
    // rotation, and a re-rolled seed would jump the fire's breath mid-read.
    val seed = rememberSaveable { Random.nextDouble(0.0, 1000.0) }
    val reduceMotion = rememberReduceMotion()

    /**
     * The frame is fixed by the book's scale (Law 4) — but the fixed frame
     * is a display measure, and a tablet's room is a bigger room: every
     * scale draws proportionally larger there, so relative sizes (Isaiah
     * still towers over Philemon) are untouched.
     */
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val regularWidth = with(LocalDensity.current) { containerWidth.toDp() } >= REGULAR_WIDTH
    val frameHeight: Dp = (scale.frameHeight * if (regularWidth) 1.45 else 1.0).dp

    // The frame clock. SwiftUI's TimelineView(.animation) becomes a frame
    // loop; under reduce motion the loop never starts and the fire holds one
    // instant (§11) — the seed — which the deterministic hash guarantees is
    // a fire rather than a roll of the dice.
    //
    // The clock's origin is the first frame rather than a reference date:
    // every use of `time` runs through a period or a hash, so the origin was
    // always arbitrary, and starting near zero keeps the hash's sin() well
    // inside the range where a Double still has bits to spare.
    val time by produceState(seed, seed, reduceMotion) {
        if (reduceMotion) {
            value = seed
            return@produceState
        }
        var origin = Long.MIN_VALUE
        var lastEmitted = Double.NEGATIVE_INFINITY
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (origin == Long.MIN_VALUE) origin = nanos
                val t = (nanos - origin) / 1_000_000_000.0 + seed
                if (t - lastEmitted >= FRAME_INTERVAL_SECONDS) {
                    lastEmitted = t
                    value = t
                }
            }
        }
    }

    // The fire crosses from one state to the next rather than redrawing as a
    // different fire on one frame.
    //
    // `FirePainter.draw` switches tongue geometry, character and warm throw
    // discretely, and nothing interpolated between two of them — so catching
    // → burning → steady → banked was a cut, while the *word* under the fire
    // took the settle token to say the same thing, under a comment asserting
    // that "a word swapped on one frame under a fire that took its time
    // getting there reads as a correction". The fire was not taking its time.
    //
    // Two stacked passes, the outgoing one fading out as the incoming one
    // fades in, both inside the one offscreen layer so the additive blending
    // still accumulates in the fire's own buffer (A15). They share `time`,
    // `scale`, `coalDepth` and the seed, so nothing moves except the flame —
    // the same property the fire→ember become already relies on. Under
    // reduce motion the token snaps and this is exactly the cut it was.
    val dim = animateFloatAsState(
        targetValue = if (dimmed) 0.92f else 1f,
        animationSpec = RibbonMotion.settle(reduceMotion),
        label = "offline",
    )
    val leaving = remember { mutableStateOf(state) }
    val crossing = remember { Animatable(1f) }
    LaunchedEffect(state) {
        // A state that changes again mid-cross keeps whatever was leaving —
        // the effect is cancelled and relaunched, so `leaving` is only ever
        // written when a cross has finished. Two states deep the older one is
        // simply dropped, which is right: a fire that has moved twice in
        // 400 ms is not a thing anybody is watching the middle of.
        crossing.snapTo(0f)
        crossing.animateTo(1f, RibbonMotion.settle(reduceMotion))
        leaving.value = state
    }
    val from = leaving.value

    Canvas(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(frameHeight)
            // SwiftUI's Canvas is its own layer: `.plusLighter` accumulates
            // inside it and the result composites over the room normally,
            // and anything drawn past the edge is clipped. An offscreen
            // compositing strategy gives Compose the same two properties,
            // and carries the offline dimming with it.
            .graphicsLayer {
                // Eased, not stepped. `dimmed` is the room's connectivity,
                // which flips whenever the socket drops or comes back, so on
                // a flaky line the one warm object on the screen used to step
                // down and back up in brightness in single frames, repeatedly.
                // S01 asks for the fire to render "in its last known state,
                // dimmed by ~8%" (A26); a step is not how a fire dims.
                alpha = dim.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // Law 2, and §11: a state, a full stop. Never a percentage,
            // never a count, never a duration.
            .clearAndSetSemantics { contentDescription = Copy.fireIs(state.displayName) },
    ) {
        val t = crossing.value
        if (from != state) {
            FirePainter.draw(
                into = this, time = time,
                state = from, scale = scale, coalDepth = coalDepth,
                opacity = 1f - t,
            )
        }
        FirePainter.draw(
            into = this, time = time,
            state = state, scale = scale, coalDepth = coalDepth,
            opacity = if (from != state) t else 1f,
        )
    }
}

/**
 * A tiny static fire — the chooser's length indicator (S13) and the rooms
 * sheet's state glyph (S14).
 */
@Composable
fun CampfireGlyph(
    state: FireState,
    scale: FireScale,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
) {
    Canvas(
        modifier = modifier
            .size(width = height * 1.4f, height = height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // `.accessibilityHidden(true)`: the glyph always sits beside the
            // words it illustrates, so it says nothing of its own.
            .clearAndSetSemantics { },
    ) {
        FirePainter.draw(
            into = this, time = 402.7,
            state = state, scale = scale, coalDepth = 0.3,
        )
    }
}

object FirePainter {

    /**
     * A smooth pseudo-noise: two incommensurate sines around the 3.4 s
     * breath ([app.readribbon.design.RibbonMotion.FLICKER_PERIOD_SECONDS]),
     * so the loop never visibly repeats. EmberView leans on this too — the
     * signature is load-bearing.
     */
    fun breath(t: Double, phase: Double): Double {
        val a = sin((t / 3.4 + phase) * 2 * PI)
        val b = sin((t / 2.13 + phase * 1.7) * 2 * PI + 1.1)
        val c = sin((t / 7.9 + phase * 0.31) * 2 * PI + 4.2)
        return a * 0.5 + b * 0.35 + c * 0.15
    }

    /**
     * A deterministic hash → 0..<1. Everything "random" about the fire —
     * lump shapes, spark clocks, shed gates — comes through here, never
     * through a RNG, so the same (time, state, scale, coalDepth) always
     * draws the same frame. Reduce-motion holds one arbitrary instant
     * (§11), and that instant must be a fire, not a roll of the dice.
     */
    fun hash(n: Double): Double {
        val s = sin(n * 127.1 + 311.7) * 43758.5453123
        return s - floor(s)
    }

    /**
     * 1-D value noise in -1...1, C¹-smooth. Callers sample it at
     * (time · speed − height · k) so the perturbation climbs the flame;
     * wobble that travels upward is most of what separates fire from
     * jelly.
     */
    fun noise(x: Double, seed: Double): Double {
        val i = floor(x)
        val f = x - i
        val u = f * f * (3 - 2 * f)
        val a = hash(i + seed * 57.31)
        val b = hash(i + 1 + seed * 57.31)
        return (a + (b - a) * u) * 2 - 1
    }

    private fun fract(x: Double): Double = x - floor(x)

    /**
     * Smooths a polyline into quad curves through segment midpoints —
     * short segments in, one continuous organic edge out.
     *
     * The polyline arrives as two coordinate arrays and a count rather than a
     * `List<Offset>`, which is not a style preference. `Offset` is a value
     * class over a packed `Long`, so it costs nothing on its own — but a
     * `List<Offset>` cannot hold a value class unboxed, and every point put
     * in one is an object. This is called for every tongue of every flame
     * layer and every coal in the bed, thirty times a second, and it was
     * boxing on the order of four hundred points a frame: about fourteen
     * thousand short-lived objects a second, all of it on the thread that is
     * also meant to be tracking a finger. Two reused `FloatArray`s box
     * nothing and the arithmetic below is unchanged, so the picture is
     * identical.
     */
    private fun addSmoothSpine(path: Path, xs: FloatArray, ys: FloatArray, count: Int) {
        if (count <= 2) {
            if (count > 0) path.lineTo(xs[count - 1], ys[count - 1])
            return
        }
        for (i in 1 until count - 1) {
            val midX = (xs[i] + xs[i + 1]) / 2
            val midY = (ys[i] + ys[i + 1]) / 2
            path.quadraticTo(xs[i], ys[i], midX, midY)
        }
        path.lineTo(xs[count - 1], ys[count - 1])
    }

    /**
     * One tongue outline. Both edges are sample points perturbed by
     * upward-travelling value noise — left and right sample different
     * lanes, so the tongue is never symmetric — then smoothed. The base
     * is planted (the noise envelope is zero at the coals) and the width
     * necks to nothing at the tip, with a slight belly low down.
     */
    private fun tonguePath(
        baseX: Double, baseY: Double, height: Double, halfWidth: Double,
        time: Double, phase: Double, agitation: Double, tempo: Double,
        sway: Double,
    ): Path {
        val segments = 8
        // The outline is the left edge read upward and then the right edge
        // read back down with its topmost point dropped (the two edges meet
        // at the tip), which is 2·segments + 1 points. Both edges are written
        // straight into their final places in one pass — the left edge
        // forward from 0, the right edge backward from the end — so there are
        // no intermediate lists to build, reverse and concatenate.
        val xs = spineXs
        val ys = spineYs
        val travel = time * 0.55 * tempo
        for (j in 0..segments) {
            val u = j.toDouble() / segments
            val planted = u * u * (3 - 2 * u)                     // 0 at the coals
            val profile = (1 - u) * (1 + 1.6 * u - 0.4 * u * u)   // belly low, neck high
            val squeezeL = noise(travel - u * 2.6, phase + 3.1)
            val squeezeR = noise(travel - u * 2.6 + 11.7, phase + 7.7)
            val bend = noise(travel * 0.7 - u * 1.8, phase + 13.0)
            val mid = baseX + sway * planted +
                bend * agitation * planted * halfWidth * 0.5
            val reach = halfWidth * profile
            val y = (baseY - height * u).toFloat()
            xs[j] = (mid - reach * (1 + 0.38 * agitation * planted * squeezeL)).toFloat()
            ys[j] = y
            if (j < segments) {
                xs[2 * segments - j] =
                    (mid + reach * (1 + 0.38 * agitation * planted * squeezeR)).toFloat()
                ys[2 * segments - j] = y
            }
        }
        val path = tongueScratch
        path.reset()
        path.moveTo(xs[0], ys[0])
        addSmoothSpine(path, xs, ys, 2 * segments + 1)
        path.close()
        return path
    }

    /**
     * An irregular rounded coal: six vertices with hashed radius jitter,
     * smoothed through midpoints, flattened as if seen at the bed's angle.
     */
    private fun lumpPath(centerX: Double, centerY: Double, radius: Double, seed: Double): Path {
        val vertices = 6
        // The coal's own buffer rather than the tongues': a lump is built and
        // drawn inside the bed's loop, and the tongues are built later, but
        // sharing one buffer across two shapes is the kind of saving that
        // turns into a bug the moment the order of the drawing changes.
        val xs = lumpXs
        val ys = lumpYs
        for (v in 0 until vertices) {
            val angle = v.toDouble() / vertices * 2 * PI
            val rx = radius * (0.72 + 0.56 * hash(seed + v * 3.77))
            val ry = radius * 0.62 * (0.72 + 0.56 * hash(seed + v * 9.13))
            xs[v] = (centerX + cos(angle) * rx).toFloat()
            ys[v] = (centerY + sin(angle) * ry).toFloat()
        }
        val path = lumpScratch
        path.reset()
        val firstMidX = (xs[vertices - 1] + xs[0]) / 2
        val firstMidY = (ys[vertices - 1] + ys[0]) / 2
        path.moveTo(firstMidX, firstMidY)
        for (v in 0 until vertices) {
            val next = (v + 1) % vertices
            path.quadraticTo(
                xs[v], ys[v],
                (xs[v] + xs[next]) / 2, (ys[v] + ys[next]) / 2,
            )
        }
        path.close()
        return path
    }

    /**
     * Draws the whole fire into [into].
     *
     * The Swift takes `size` as an argument because a GraphicsContext does
     * not carry one; a DrawScope does, so it is read from there. [scale] is
     * likewise unused in the body and kept for signature parity: the book's
     * scale reaches the drawing through the height of the frame it is given,
     * never as a number the painter reads.
     *
     * @param opacity how much of this pass to composite, for the cross-fade
     *   between two fire states. Applied as one layer over the whole pass
     *   rather than to each draw inside it: the fire is built out of additive
     *   blends, and fading the individual strokes would change how they
     *   accumulate rather than how much of the result shows.
     */
    fun draw(
        into: DrawScope,
        time: Double,
        state: FireState,
        scale: FireScale,
        coalDepth: Double,
        opacity: Float = 1f,
    ) {
        if (opacity <= 0f) return
        if (opacity >= 1f) {
            paint(into, time, state, scale, coalDepth)
            return
        }
        val canvas = into.drawContext.canvas
        // Kept rather than made: a cross lasts the settle token, which is
        // twelve frames of a fresh native Paint for something whose only
        // varying property is its alpha.
        canvas.saveLayer(
            Rect(Offset.Zero, into.size),
            crossFadePaint.apply { alpha = opacity },
        )
        paint(into, time, state, scale, coalDepth)
        canvas.restore()
    }

    private fun paint(
        into: DrawScope,
        time: Double,
        state: FireState,
        scale: FireScale,
        coalDepth: Double,
    ) {
        with(into) {
            // One device pixel per point, so the handful of absolute point
            // measures below (hairline widths, spark radii, blur radii) stay
            // the sizes the Swift chose while everything footprint-derived
            // stays in pixels.
            val pt = 1.dp.toPx().toDouble()

            val w = size.width.toDouble()
            val h = size.height.toDouble()
            val baseY = h * 0.88
            val cx = w / 2

            // The fire's footprint grows with the book's scale.
            val footprint = min(w * 0.6, h * 1.05)

            // Below this the fire is a glyph (S13/S14): skip blur passes,
            // sparks, shed blobs and hot air so it stays a crisp, cheap mark.
            val detailed = h >= 48 * pt
            val banked = state == FireState.banked

            // --- The warm throw ---------------------------------------------
            // A radial bloom behind everything, additive so the flames sit in
            // their own light. Steady throws wide and even; banked is a dim,
            // close glow; the coal bed widens it as it deepens.
            val (throwRadius, throwOpacity) = when (state) {
                FireState.catching -> footprint * (0.5 + 0.25 * coalDepth) to 0.16
                FireState.burning -> footprint * (0.75 + 0.3 * coalDepth) to 0.24
                FireState.steady -> footprint * (1.0 + 0.35 * coalDepth) to 0.3
                FireState.banked -> footprint * (0.42 + 0.2 * coalDepth) to 0.13
            }
            val glowBreath = 1 + 0.05 * breath(time, 0.13)
            val glowRect = rectOf(
                x = cx - throwRadius * glowBreath,
                y = baseY - throwRadius * glowBreath * 0.62,
                width = throwRadius * 2 * glowBreath,
                height = throwRadius * glowBreath * 1.05,
            )
            // SwiftUI's radial gradient takes a start and an end radius;
            // Compose takes a centre and one radius. Every gradient here
            // starts at 0, so the stops fall in the same places and the end
            // radius is simply Compose's radius.
            fillAdditive(
                ovalPath(glowRect),
                Brush.radialGradient(
                    colors = listOf(
                        Palette.flameCore.opacity(throwOpacity),
                        Palette.flameDeep.opacity(throwOpacity * 0.4),
                        Color.Transparent,
                    ),
                    center = Offset(cx.toFloat(), baseY.toFloat()),
                    radius = (throwRadius * glowBreath).toFloat(),
                ),
            )

            // --- The coal bed -------------------------------------------------
            // The one thing that may grow (Law 4). Not a single ellipse: a low
            // mound carrying eleven seeded, irregular lumps, each pulsing on
            // its own slow clock, with bright fissures where heat shows between
            // them. Width and warmth come from coalDepth — a fact you can feel
            // but never count (Law 2). The bed is a thing, so it blends normal;
            // only its fissures are light.
            val bedWidth = footprint * (0.5 + 0.42 * coalDepth)
            val bedHeight = footprint * (0.1 + 0.05 * coalDepth)
            val bedRect = rectOf(
                x = cx - bedWidth / 2, y = baseY - bedHeight / 2,
                width = bedWidth, height = bedHeight,
            )
            val bedGlow = if (banked) {
                0.45 + 0.08 * breath(time / 2.6, 0.71)   // banked coals pulse, very slowly
            } else {
                0.7 + 0.1 * breath(time, 0.44)
            }
            drawPath(
                ovalPath(bedRect),
                Brush.radialGradient(
                    colors = listOf(
                        Palette.coal.opacity(0.85 * bedGlow),
                        Palette.coalDim.opacity(0.75 * bedGlow),
                        Palette.coalDim.opacity(0.0),
                    ),
                    center = Offset(cx.toFloat(), baseY.toFloat()),
                    radius = (bedWidth / 2).toFloat(),
                ),
            )

            for (i in 0 until 11) {
                val fi = i.toDouble()
                val hx = hash(fi * 12.99 + 4.1)
                val hy = hash(fi * 78.23 + 9.7)
                val hr = hash(fi * 39.43 + 2.3)
                val px = cx + (hx - 0.5) * bedWidth * 0.88
                val py = baseY + (hy - 0.5) * bedHeight * 0.5
                val r = footprint * (0.024 + 0.02 * hr) * (0.85 + 0.3 * coalDepth)
                // Lumps near the middle run hotter; each one's glow drifts on
                // its own long period so the bed never beats in unison.
                val centerBias = 1 - min(1.0, abs(px - cx) / (bedWidth / 2))
                val pulse = 0.5 + 0.5 * breath(time / (2.1 + 2.3 * hash(fi + 31.7)), fi * 0.618)
                var heat = (0.3 + 0.55 * centerBias) * (0.55 + 0.45 * pulse)
                if (banked) heat *= 0.5
                drawPath(
                    lumpPath(centerX = px, centerY = py, radius = r, seed = fi * 5.77),
                    Brush.radialGradient(
                        colors = listOf(
                            Palette.coal.opacity(0.55 + 0.4 * heat),
                            Palette.coalDim.opacity(0.9),
                        ),
                        center = Offset(px.toFloat(), (py - r * 0.25).toFloat()),
                        radius = (r * 1.5).toFloat(),
                    ),
                )
            }

            // Fissures — the heat that shows between coals. Additive, so where
            // two cross, the bed brightens the way real embers do.
            val fissureCount = if (detailed) 7 else 4
            for (k in 0 until fissureCount) {
                val fk = k.toDouble()
                val x0 = cx + (hash(fk * 3.37 + 7.2) - 0.5) * bedWidth * 0.72
                val y0 = baseY + (hash(fk * 5.11 + 2.9) - 0.5) * bedHeight * 0.42
                val len = bedWidth * (0.07 + 0.09 * hash(fk * 9.23 + 1.1))
                val angle = hash(fk * 4.71 + 6.6) * PI
                val dx = cos(angle) * len
                val dy = sin(angle) * len * 0.3   // the bed is seen at an angle
                val fissure = fissureScratch
                fissure.reset()
                fissure.moveTo((x0 - dx / 2).toFloat(), (y0 - dy / 2).toFloat())
                fissure.quadraticTo(
                    (x0 + dy * 0.8).toFloat(), (y0 - dx * 0.15).toFloat(),
                    (x0 + dx / 2).toFloat(), (y0 + dy / 2).toFloat(),
                )
                val flick = 0.5 + 0.5 * breath(time / 1.9, fk * 0.77 + 0.2)
                val glow = if (banked) 0.1 + 0.1 * flick else 0.22 + 0.3 * flick
                drawPath(
                    path = fissure,
                    brush = Brush.linearGradient(
                        0f to Palette.flameDeep.opacity(glow),
                        0.5f to Palette.flameCore.opacity(glow * 0.85),
                        1f to Palette.flameDeep.opacity(glow * 0.5),
                        start = Offset((x0 - dx / 2).toFloat(), y0.toFloat()),
                        end = Offset((x0 + dx / 2).toFloat(), y0.toFloat()),
                    ),
                    style = Stroke(
                        width = max(0.7 * pt, footprint * 0.012).toFloat(),
                        cap = StrokeCap.Round,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }

            // --- Ash, when banked ----------------------------------------------
            // Banked is an act, never a lapse — and it must never be mistaken
            // for catching. So: nothing flame-shaped past this point, and the
            // ash blends normal so it genuinely occludes the glow it covers.
            if (banked) {
                val ashRect = Rect(
                    left = bedRect.left - (bedWidth * 0.06).toFloat(),
                    top = bedRect.top - (bedHeight * 0.14).toFloat(),
                    right = bedRect.right + (bedWidth * 0.06).toFloat(),
                    bottom = bedRect.bottom + (bedHeight * 0.14).toFloat(),
                )
                drawPath(
                    ovalPath(ashRect),
                    Brush.linearGradient(
                        colors = listOf(
                            Palette.smoke.opacity(0.12),
                            Palette.ash.opacity(0.18),
                        ),
                        start = Offset(cx.toFloat(), ashRect.top),
                        end = Offset(cx.toFloat(), ashRect.bottom),
                    ),
                )
                // A thinner drift off one side, so the veil reads settled by a
                // hand, not stamped by a machine.
                val driftRect = rectOf(
                    x = cx - bedWidth * 0.18, y = baseY - bedHeight * 0.52,
                    width = bedWidth * 0.62, height = bedHeight * 0.5,
                )
                drawPath(ovalPath(driftRect), Palette.ash.opacity(0.1))
                return  // coals under ash, a dim glow, no flame
            }

            // --- Flames ---------------------------------------------------------
            // Tongue geometry per state (§4.1): x, height and width are
            // fractions of the footprint; phase desyncs the tongues from one
            // another (the per-instance seed already desyncs whole fires).
            val tongues: List<Tongue> = when (state) {
                // Small, low, a few licks, working at it.
                FireState.catching -> listOf(
                    Tongue(-0.10, 0.30, 0.16, 0.21),
                    Tongue(0.08, 0.24, 0.13, 0.57),
                )
                // Full, active, irregular.
                FireState.burning -> listOf(
                    Tongue(-0.16, 0.52, 0.22, 0.13),
                    Tongue(0.00, 0.78, 0.28, 0.41),
                    Tongue(0.15, 0.44, 0.19, 0.74),
                )
                // Broad, even, almost calm.
                FireState.steady -> listOf(
                    Tongue(-0.20, 0.46, 0.30, 0.11),
                    Tongue(0.00, 0.62, 0.40, 0.36),
                    Tongue(0.20, 0.48, 0.30, 0.67),
                )
                FireState.banked -> emptyList()
            }

            // Character per state. Steady is the return for reading together
            // and the return is a mood: its turbulence is both damped and
            // slowed, not merely shrunk. Shed is the chance a given cycle lets
            // a tip go — burning sheds most, catching only the odd lick that
            // lifts and dies.
            val character: Character = when (state) {
                FireState.catching -> Character(0.8, 0.85, 1.0, 0.25)
                FireState.burning -> Character(1.0, 1.0, 1.2, 0.8)
                FireState.steady -> Character(0.35, 0.45, 0.6, 0.18)
                FireState.banked -> Character(0.0, 0.0, 0.0, 0.0) // unreachable — returned above
            }

            // Three nested bodies per tongue. The sheath is widest and most
            // turbulent; the heart is short, calm, and hugs the coals. Every
            // gradient puts its brightest stop at the base and dies into
            // translucent deep orange at the tip — heat lives low. Additive
            // blending is commutative, so the layers can be batched.
            fun drawFlameLayer(
                heightF: Double, widthF: Double, agitationF: Double,
                swayF: Double, phaseShift: Double,
                stops: Array<Pair<Float, Color>>, blurPx: Float,
            ) {
                for (tongue in tongues) {
                    val phase = tongue.phase + phaseShift
                    val sway = breath(time, tongue.phase) * 0.055 * character.liveliness *
                        footprint * swayF
                    val rise = 1 + breath(time * 1.13, phase + 0.3) * 0.11 * character.liveliness
                    val height = tongue.height * footprint * rise * heightF
                    val halfW = tongue.width * footprint / 2 * widthF
                    val baseX = cx + tongue.x * footprint
                    val path = tonguePath(
                        baseX = baseX, baseY = baseY, height = height, halfWidth = halfW,
                        time = time, phase = phase,
                        agitation = character.agitation * agitationF,
                        tempo = character.tempo, sway = sway,
                    )
                    fillAdditive(
                        path,
                        Brush.linearGradient(
                            *stops,
                            start = Offset(baseX.toFloat(), baseY.toFloat()),
                            end = Offset((baseX + sway).toFloat(), (baseY - height).toFloat()),
                        ),
                        blurPx = blurPx,
                    )
                }
            }

            val sheathStops = arrayOf(
                0f to Palette.flameDeep.opacity(0.55),
                0.45f to Palette.flameDeep.opacity(0.32),
                1f to Palette.flameDeep.opacity(0.0),
            )
            val bodyStops = arrayOf(
                0f to Palette.flameCore.opacity(0.85),
                0.5f to Palette.flameCore.opacity(0.5),
                1f to Palette.flameDeep.opacity(0.0),
            )
            val heartStops = arrayOf(
                0f to Palette.flameBright.opacity(0.95),
                0.55f to Palette.flameCore.opacity(0.55),
                1f to Palette.flameCore.opacity(0.0),
            )

            // Only the sheath is softened; the heart stays crisp.
            drawFlameLayer(
                heightF = 1.0, widthF = 1.0, agitationF = 1.0,
                swayF = 1.0, phaseShift = 0.0, stops = sheathStops,
                blurPx = if (detailed) (2.2 * pt).toFloat() else 0f,
            )
            drawFlameLayer(
                heightF = 0.72, widthF = 0.68, agitationF = 0.65,
                swayF = 0.8, phaseShift = 0.14, stops = bodyStops, blurPx = 0f,
            )
            drawFlameLayer(
                heightF = 0.42, widthF = 0.42, agitationF = 0.35,
                swayF = 0.55, phaseShift = 0.27, stops = heartStops, blurPx = 0f,
            )

            // --- Tip shedding ---------------------------------------------------
            // Now and then a tongue lets a small body go: it lifts, shrinks and
            // dissolves. Whether a given cycle sheds is a hash gate on the
            // cycle number, and the blob's envelope is zero at both ends of its
            // life — nothing pops, and a frozen frame (§11) can never catch a
            // half-born artifact.
            if (detailed && character.shed > 0) {
                for (tongue in tongues) {
                    val period = 3.6 + 2.9 * hash(tongue.phase * 71.3)
                    val cycle = (time + tongue.phase * 47.1) / period
                    val turn = floor(cycle)
                    if (hash(turn + tongue.phase * 91.7) >= character.shed) continue
                    val age = cycle - turn
                    val lift = age * footprint * 0.24
                    val blobX = cx + tongue.x * footprint +
                        breath(time, tongue.phase) * 0.055 * character.liveliness * footprint +
                        breath(time * 0.9, tongue.phase + 2.4) * 0.02 * footprint
                    val blobY = baseY - tongue.height * footprint * 0.9 - lift
                    val r = footprint * (0.022 + 0.04 * tongue.width) * (1 - 0.6 * age)
                    val fade = (1 - age) * min(1.0, age * 5)
                    fillAdditive(
                        ovalPath(
                            rectOf(
                                x = blobX - r, y = blobY - r * 1.2,
                                width = r * 2, height = r * 2.4,
                            ),
                        ),
                        Brush.radialGradient(
                            colors = listOf(
                                Palette.flameCore.opacity(0.5 * fade),
                                Palette.flameDeep.opacity(0.28 * fade),
                                Palette.flameDeep.opacity(0.0),
                            ),
                            center = Offset(blobX.toFloat(), blobY.toFloat()),
                            radius = (r * 1.4).toFloat(),
                        ),
                    )
                }
            }

            // --- Sparks ---------------------------------------------------------
            // A few motes born on the bed that rise, wander on the breath,
            // shrink and cool from bright to deep. Deterministic per (i, time);
            // subtle by construction — never a fountain.
            val sparkCount = when (state) {
                FireState.burning -> 6
                FireState.steady -> 3
                FireState.catching -> 2
                FireState.banked -> 0
            }
            if (detailed) {
                for (i in 0 until sparkCount) {
                    val fi = i.toDouble()
                    val period = 2.7 + 2.6 * hash(fi * 7.31 + 5.2)
                    val age = fract(time / period + hash(fi * 13.7 + 1.3))
                    val born = cx + (hash(fi * 29.4 + 8.8) - 0.5) * bedWidth * 0.6
                    val riseH = footprint * (0.45 + 0.4 * hash(fi * 3.93 + 2.6))
                    val x = born + breath(time * 0.7, fi * 0.83) * 0.05 * age * footprint
                    val y = baseY - bedHeight * 0.2 - riseH * age
                    val r = max(0.5, (0.9 + 0.7 * hash(fi * 17.3 + 3.4)) * (1 - 0.55 * age)) * pt
                    val fade = (1 - age) * min(1.0, age * 7)
                    fillAdditive(
                        ovalPath(
                            rectOf(x = x - r, y = y - r, width = r * 2, height = r * 2),
                        ),
                        Brush.radialGradient(
                            colors = listOf(
                                Palette.flameBright.opacity(0.55 * fade),
                                Palette.flameDeep.opacity(0.3 * fade),
                                Palette.flameDeep.opacity(0.0),
                            ),
                            center = Offset(x.toFloat(), y.toFloat()),
                            radius = (r * 1.4).toFloat(),
                        ),
                    )
                }
            }

            // --- Hot air ---------------------------------------------------------
            // Two nearly invisible warm wisps above the tips, 3–4% at their
            // peak. The book title sits right below the fire, so this must
            // never read as smoke or distortion — only a suggestion that the
            // air up there is warm. Room-size fires only.
            //
            // A radial gradient rather than a flat fill behind a blur, which
            // is the same picture arrived at for a fraction of the cost. A
            // mask-filter blur is not a cheap operation: Skia renders the
            // shape's coverage to an offscreen mask, blurs that, and draws
            // through it, so every blurred path is its own small render pass.
            // At 30 Hz the fire was asking for five of them a frame — three
            // sheath tongues and these two wisps — and these two were paying
            // for it to soften the rim of an oval that is 3.5% opaque at its
            // brightest. A gradient that reaches zero at the edge has no rim
            // to soften; it is what a blurred flat oval is trying to look
            // like. The flat middle is kept out to 0.55 so the wisp still has
            // body rather than becoming a point of light.
            if (detailed && h >= 120 * pt) {
                val crest = tongues.maxOfOrNull { it.height } ?: 0.5
                for (i in 0 until 2) {
                    val fi = i.toDouble()
                    val age = fract(time / (5.5 + fi * 1.7) + fi * 0.5)
                    val wx = cx + breath(time * 0.5, fi + 0.9) * 0.08 * footprint
                    val wy = baseY - crest * footprint - age * footprint * 0.22
                    val ww = footprint * 0.3
                    val wh = footprint * 0.1
                    val fade = 0.035 * sin(PI * age)
                    fillAdditive(
                        ovalPath(
                            rectOf(x = wx - ww / 2, y = wy - wh / 2, width = ww, height = wh),
                        ),
                        Brush.radialGradient(
                            0.0f to Palette.flameDeep.opacity(fade),
                            0.55f to Palette.flameDeep.opacity(fade),
                            1.0f to Palette.flameDeep.opacity(0.0),
                            center = Offset(wx.toFloat(), wy.toFloat()),
                            radius = (ww / 2).toFloat(),
                        ),
                    )
                }
            }
        }
    }

    /** One tongue's geometry: fractions of the footprint, plus its phase. */
    private data class Tongue(
        val x: Double,
        val height: Double,
        val width: Double,
        val phase: Double,
    )

    /** How a state burns. */
    private data class Character(
        val liveliness: Double,
        val agitation: Double,
        val tempo: Double,
        val shed: Double,
    )
}

/** SwiftUI's `Color.opacity(_:)`, which clamps; `Color.copy(alpha =)` does not. */
private fun Color.opacity(a: Double): Color = copy(alpha = a.toFloat().coerceIn(0f, 1f))

/** `CGRect(x:y:width:height:)`, which Compose's [Rect] does not offer. */
private fun rectOf(x: Double, y: Double, width: Double, height: Double): Rect =
    Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

/**
 * The scratch the fire draws through — the paths, and the coordinate buffers
 * the paths are built out of.
 *
 * Every shape here used to be a fresh `Path`, which is a native object with a
 * native allocation behind it, built inside the draw and thrown away at the
 * end of it: eleven coals, seven fissures, two ovals and a tongue per flame
 * layer, thirty times a second, for as long as a fire is on the screen. Each
 * one is built and then immediately drawn, and `drawPath` copies what it
 * needs into the display list, so one path per *kind* of shape serves every
 * frame — four objects for the life of the process instead of a few hundred a
 * second.
 *
 * Four rather than one because nothing guarantees an oval is not wanted while
 * a tongue is still being built, and a shared scratch would then be asked to
 * be two shapes at once.
 *
 * The float buffers are the same saving one level down: see [addSmoothSpine]
 * for what a `List<Offset>` costs per point.
 *
 * Single-threaded by construction, the same reasoning as [SoftPaints]:
 * Compose draws on one thread, and every shape here is built and drawn before
 * anything else can ask for the buffer. Two fires on one screen are drawn one
 * after the other, not at once.
 */
private val ovalScratch = Path()
private val tongueScratch = Path()
private val lumpScratch = Path()
private val fissureScratch = Path()

/** A tongue's outline: 2·8 + 1 points, the two edges meeting at the tip. */
private val spineXs = FloatArray(17)
private val spineYs = FloatArray(17)

/** A coal's six vertices. */
private val lumpXs = FloatArray(6)
private val lumpYs = FloatArray(6)

/** The layer paint the state cross-fade composites through. */
private val crossFadePaint = Paint()

/** `Path(ellipseIn:)`. */
private fun ovalPath(rect: Rect): Path = ovalScratch.apply {
    reset()
    addOval(rect)
}

/**
 * Fills [path] additively — the `.plusLighter` of the Swift — optionally
 * softening it by [blurPx].
 *
 * The blur is the one place Compose cannot say what SwiftUI says. SwiftUI
 * pushes a layer, hangs a `.blur` filter on it, draws a group into it and
 * composites the blurred result; a DrawScope has no such layer, so the blur
 * is attached to the paint as a Skia mask filter and each path is softened
 * on its own. Because Gaussian blur is linear, blurring each tongue and
 * adding them is the same result as adding them and blurring the group. What
 * genuinely differs is that a mask filter blurs the shape's coverage rather
 * than its filled pixels, so the edge softens while the gradient inside it
 * stays where it was put — for a sheath that is fading to nothing at its own
 * edge anyway, that is the nearest honest thing.
 */
private fun DrawScope.fillAdditive(path: Path, brush: Brush, blurPx: Float = 0f) {
    if (blurPx <= 0f) {
        drawPath(path = path, brush = brush, blendMode = BlendMode.Plus)
        return
    }
    val paint = SoftPaints.at(blurPx)
    brush.applyTo(size, paint, 1f)
    drawIntoCanvas { it.drawPath(path, paint) }
}

/**
 * The paints the softened passes use, kept rather than made.
 *
 * A `Paint` and a `BlurMaskFilter` are both native objects, and this made a
 * fresh pair on **every blurred draw of every frame** — five a frame before
 * the hot air stopped needing one, three after, at the 30 Hz this redraws
 * at, for as long as a fire is on the screen. Each one also has to be
 * finalised. The fire is the one thing in the app that is always moving, so
 * that cost is always being paid, and it is paid on the CPU.
 *
 * There is only one radius left and it is fixed at a given density, so this
 * is a one-entry map that fills once and is then only read. It is written as
 * a map anyway because the radius is a parameter of [fillAdditive] and a
 * second caller with a second radius should get a second paint rather than
 * silently reuse the first one's blur. The paint is handed back for the
 * brush to be applied to, which is the one property that genuinely differs
 * per draw; blend mode and mask filter are set once when the paint is made.
 *
 * Single-threaded by construction: Compose draws on one thread, and every
 * caller applies its brush and draws before anyone else can ask for a paint.
 */
private object SoftPaints {
    private val byRadius = HashMap<Float, Paint>(4)

    fun at(blurPx: Float): Paint = byRadius.getOrPut(blurPx) {
        Paint().apply {
            blendMode = BlendMode.Plus
            nativePaint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
}
