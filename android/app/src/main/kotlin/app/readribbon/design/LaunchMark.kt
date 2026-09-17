package app.readribbon.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.readribbon.R
import kotlinx.coroutines.launch

// The launch mark: the Wave, coming down on the unlit ground, and then the
// room.
//
// **Why this is a composable at all**, when the app already had an
// `AnimatedVectorDrawable` in the launch theme (A28). The owner, on a Pixel 9
// Pro XL: *"the splash screen with the Ribbon being animated doesn't work. It
// just kind of shows it and then fades to it."* Which is a mark that appears
// whole and then fades — the unfurl never running.
//
// The drawable was not the problem, and that was checked before anything was
// changed: inflated, started, and drawn frame by frame, the animated vector
// closed its clip band to nothing and opened it again over 440 ms — so the
// vector, both animators and both target names were correct. What could not
// be established is why the platform declined to play it on that phone, and
// that is the point. The system splash is drawn by the system, from the app's
// theme, in another process, before this app exists: there is nothing in it
// to see, to test, or to fix from here, and no guarantee it behaves the same
// on the next phone. §05 gives the launch one job — be the mark and the
// ground, and get out of the way — and a job that only works on some devices
// is not being done.
//
// So the mark is the app's to draw. The launch window carries the ground and
// nothing else (`splash_ground.xml`), the app's very first frame is this, and
// the unfurl runs on the same Compose clock as everything else in the
// product: under our own reduce-motion token (§11), in the look book, and
// testable.
//
// **What it costs, stated plainly.** The system splash used to show the mark
// during process start; now it shows the unlit ground. On a cold start that
// is the ground alone for as long as the process takes to come up — a few
// hundred milliseconds on the phone in question — where before there was a
// static mark. That is the trade: a launch that is briefly only the ground
// and then unfurls, against one that shows the mark whole and never moves.
// The owner's report is that the second of those reads as broken.
//
// It does not *add* time. The old build held the splash for a 480 ms floor
// (`MARK_FLOOR_MS`) so a warm launch could not cut the unfurl to three
// frames; that floor is gone, and the mark's own animation is the hold now.
// The difference is that the animation happens *while* the store is coming
// off disk rather than after the window has already been held for it.

/** The mark's own grid — `splash_wave.xml`'s viewport. */
private const val GRID = 108f

/**
 * The clip band, in grid units: the group's origin and its height.
 *
 * The same band `animator/splash_unfurl.xml` opens, and the same reason it
 * exists — a ribbon does not fade in, it comes down, so the mark is uncovered
 * from its own top edge rather than revealed all at once. The group in
 * `splash_wave.xml` is translated to (22, 22) and the mark occupies about 64
 * units below that, which is what these two numbers are.
 */
private const val BAND_TOP = 22f
private const val BAND_HEIGHT = 64f

/**
 * The box the mark is drawn in.
 *
 * 288 dp is what Android gives a splash icon that has no icon background,
 * which is what this replaces — so a device that shows the system mark for a
 * moment first (an older build still installed, a launch the platform does
 * draw) hands over at the same size rather than jumping.
 */
private val MARK_BOX = 288.dp

/** The ribbon coming down. §9.1's arrive shape, at the drawable's own length. */
private const val UNFURL_MS = 440

/**
 * The settle: the mark arrives a shade large and comes to rest, which is the
 * weight the unfurl on its own does not have — a ribbon is cloth with a
 * length behind it, not a wipe. 1.04 to 1.0 and no more; §13's never-ship
 * list is mostly a list of logo animations.
 */
private const val SETTLE_MS = 560
private const val SWELL = 1.04f

/** How long the whole mark takes, and so the shortest launch there can be. */
const val LAUNCH_MARK_MS = SETTLE_MS

/**
 * The launch surface: the ground, the mark, and then nothing.
 *
 * @param ready the app has what it needs to draw the room. The mark will not
 *   leave before this is true *or* before it has finished coming down —
 *   whichever is later. A mark cut off after three frames reads as a glitch,
 *   and a mark that outstays the room is inserted time (§05).
 * @param onDone the mark has gone. Called once.
 */
@Composable
fun LaunchMark(ready: Boolean, onDone: () -> Unit) {
    val still = rememberReduceMotion()
    val done by rememberUpdatedState(onDone)

    val unfurl = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    val leaving = remember { Animatable(1f) }

    LaunchedEffect(still) {
        if (still) {
            // §11: the mark is a mark, not a performance. It is simply there.
            unfurl.snapTo(1f)
            settle.snapTo(1f)
            return@LaunchedEffect
        }
        launch { unfurl.animateTo(1f, tween(UNFURL_MS, easing = RibbonMotion.EaseOut)) }
        settle.animateTo(1f, tween(SETTLE_MS, easing = RibbonMotion.EaseOut))
    }

    // Two conditions, and the `&&` is the whole of the timing: the mark goes
    // when the room can be drawn and the ribbon has finished coming down.
    // Neither alone is right — leaving on `ready` cuts the animation on a
    // warm launch, and leaving on the animation holds a room that was ready
    // half a second ago.
    val settled = settle.isRunning.not() && settle.value >= 1f
    LaunchedEffect(ready, settled) {
        if (!ready || !settled) return@LaunchedEffect
        leaving.animateTo(0f, RibbonMotion.arrive(still))
        done()
    }

    Box(
        Modifier
            .fillMaxSize()
            // The brand's ground rather than the palette's, and deliberately
            // no grain: this is the colour `@color/unlit` paints the launch
            // window, and the hand-over from that window to this frame has to
            // be a hand-over of the same black. The room's own ground arrives
            // underneath when the mark goes.
            .background(Brand.ground)
            .graphicsLayer { alpha = leaving.value }
            // Nothing here is for a screen reader, and while it is up it
            // covers the room: without this, TalkBack would walk into a room
            // nobody can see yet (§11).
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_wave),
            contentDescription = null,
            modifier = Modifier
                .size(MARK_BOX)
                .graphicsLayer {
                    val swell = SWELL - (SWELL - 1f) * settle.value
                    scaleX = swell
                    scaleY = swell
                }
                .drawWithContent {
                    // The band, in the mark's own grid. `splash_wave.xml`
                    // carries its clip-path resting open — which is what
                    // shows if this never runs — so the opening is done here,
                    // over the top of it, rather than by morphing the
                    // drawable's own path.
                    val open = (BAND_TOP + BAND_HEIGHT * unfurl.value) / GRID
                    clipRect(bottom = size.height * open) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}
