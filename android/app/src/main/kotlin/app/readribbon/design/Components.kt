package app.readribbon.design

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.core.Ink
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import kotlinx.coroutines.launch

// Small shared pieces: portraits, note marks, ink dots, the wide way-in
// control. Each one is specified somewhere in the build book; the section is
// cited where it matters.

/**
 * Whether the system has been asked to still its animations, as [RibbonTheme]
 * answered it for the whole app.
 *
 * Every note mark in a chapter and every swatch in the ink picker asks this
 * question, so it is answered once and handed down rather than worked out
 * again at each of them: watching the setting means an IPC registration, and
 * one per note in Genesis 1 is not a thing to do to a phone.
 *
 * `false` outside the theme — a preview, a test — which is the honest default:
 * nothing has said to hold still.
 */
@Composable
fun rememberReduceMotion(): Boolean = LocalReduceMotion.current

/** Where [RibbonTheme] puts the answer. */
internal val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * The answer itself, worked out once, at the theme.
 *
 * Android has no single "reduce motion" switch the way iOS does; turning
 * animations off in Developer options or via an accessibility service sets
 * the animator duration scale to zero, and that is the signal every
 * well-behaved app reads. §11 then applies: the fire holds a state instead
 * of flickering, morphs become cross-fades, and the thinking-of-you fill
 * becomes an instant state change with the haptic intact.
 *
 * Read once and then *watched*, because it is a setting somebody turns on —
 * usually because the app in front of them is already making them unwell.
 * Read once and remembered, the app would go on moving until it was force
 * quit and launched again, which is the one moment the answer matters least.
 * A `ContentObserver` costs nothing and makes the switch take effect on the
 * screen the person is looking at.
 */
@Composable
internal fun observeReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var still by remember(resolver) { mutableStateOf(animatorsAreOff(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                still = animatorsAreOff(resolver)
            }
        }
        // A device that refuses the registration is a device whose answer
        // cannot change under us either; the value read above stands.
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    return still
}

/** The animator duration scale, as a yes or a no. */
private fun animatorsAreOff(resolver: ContentResolver): Boolean = runCatching {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/**
 * A sheet's way out, so that it leaves the way it came.
 *
 * A `ModalBottomSheet` animates itself away when the *person* dismisses it —
 * a swipe down, a tap on the scrim, the back gesture. A sheet dismissed by
 * the *app*, because a book was chosen or a room was named or an ink was
 * picked, is a different thing entirely: the flag it hangs on goes false, the
 * composable is gone on the next frame, and the sheet does not leave so much
 * as stop existing. Nothing in this product should stop existing (§9.1), and
 * the moment it happened was always the moment something good had just been
 * decided.
 *
 * So the sheet slides down first, and only then is the caller told — which is
 * what flips the flag.
 *
 * ```
 * val leave = rememberSheetExit(sheetState)
 * // …
 * onChoose = { book -> leave { chose(book) } }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetExit(sheetState: SheetState): (then: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        // The sheet goes down once, however many things are waiting on the far
        // side of it, and they are run in the order they were asked for: the
        // naming form closes itself *and* hands over its new room's invite
        // (S15), and two slides racing each other would cut the first one
        // short and drop the sheet mid-flight.
        val waiting = mutableListOf<() -> Unit>()
        var leaving = false

        fun exit(then: () -> Unit) {
            waiting += then
            if (leaving) return
            leaving = true
            scope.launch {
                // `finally`: a hide cut short by something else still hands
                // over, because a sheet left standing over a room it no longer
                // belongs to is worse than a sheet that cut away.
                try {
                    sheetState.hide()
                } finally {
                    val queued = waiting.toList()
                    waiting.clear()
                    leaving = false
                    queued.forEach { it() }
                }
            }
        }

        ::exit
    }
}

/** A person's face — or, without a portrait, a monogram in their ink. */
@Composable
fun PortraitView(
    person: Person?,
    modifier: Modifier = Modifier,
    ink: Ink? = null,
    size: Dp = 44.dp,
    image: ImageBitmap? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (image == null) Modifier.background(Palette.raised) else Modifier)
            .semantics { person?.name?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = person?.monogram ?: "·",
                style = RibbonType.ui(size.value * 0.42f, FontWeight.Medium),
                color = ink?.color ?: Palette.muted,
            )
        }
    }
}

/**
 * The marks in the gutter (§4.4): a solid 6 dp dot for a voice note, an open
 * 6 dp ring (1.4 dp stroke) for a written one, in the author's ink.
 *
 * Unfound marks breathe — 0.65 → 1.0 over 4 s, eased both ways, slow enough
 * that it never reads as an alert. Your own marks never breathe. Pending
 * marks render hairline until they land (§4.4: no spinner, no toast, no
 * retry button).
 */
@Composable
fun NoteMark(
    kind: NoteKind,
    ink: Ink,
    found: Boolean,
    mine: Boolean,
    pending: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val breathes = !mine && !found && !pending && !reduceMotion

    val breath = if (breathes) {
        val transition = rememberInfiniteTransition(label = "note-mark-breath")
        transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = RibbonMotion.EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "note-mark-opacity",
        ).value
    } else {
        when {
            mine -> 0.8f
            found -> 0.55f
            else -> 0.65f
        }
    }

    val color = ink.color
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(6.dp)
            .alpha(if (pending) 0.9f else breath),
    ) {
        val radius = size.minDimension / 2f
        when (kind) {
            NoteKind.voice ->
                if (pending) {
                    drawCircle(
                        color = color,
                        radius = radius - 0.35.dp.toPx(),
                        style = Stroke(width = 0.7.dp.toPx()),
                    )
                } else {
                    drawCircle(color = color, radius = radius)
                }

            NoteKind.written -> {
                val stroke = if (pending) 0.7.dp.toPx() else 1.4.dp.toPx()
                drawCircle(
                    color = color,
                    radius = radius - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/** A 6 dp ink dot — the quiet marker on the room's waiting rows (S01). */
@Composable
fun InkDot(ink: Ink, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(ink.color),
    )
}

/** The way in (S01): one wide control. A control says exactly what happens. */
@Composable
fun WayInButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Last, for the same reason QuietControl's is: so the trailing-lambda
    // form binds the click handler rather than `enabled`.
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .background(if (enabled) Palette.chartreuse else Palette.chartreuse.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A control says exactly what happens, and in the room what happens
        // changes: the way in is "Begin Genesis", then "Continue in Genesis"
        // once you have been in it, then "Pick a book" again when it is
        // finished. The fire above it cross-fades on the arrive token, so the
        // words do too — a control that snapped while the fire dissolved would
        // make one change look like two. Everywhere else the title is a
        // constant and this never transitions at all.
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (
                    fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                        fadeOut(RibbonMotion.arrive(reduceMotion))
                    ).using(
                    SizeTransform(clip = false) { _, _ -> RibbonMotion.arrive(reduceMotion) },
                )
            },
            label = "way-in-title",
        ) { label ->
            Text(
                text = label,
                style = RibbonType.ui(18f, FontWeight.Medium),
                color = Palette.ground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A quiet, low-emphasis text control — small caps, muted: "Mark a quiet
 * day", "set it down", "Send it again".
 *
 * Quiet in emphasis, not in touch: the visible text stays small while the
 * tappable area meets the 44 dp minimum. A finger's tap is a blunt thing,
 * and a control only a stylus can hit is broken (deviation 12 — that defect
 * was found on iPad, and it is the same defect here).
 */
@Composable
fun QuietControl(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.muted,
    size: Float = 13f,
    // Last, so that the Compose trailing-lambda form reads naturally:
    // `QuietControl(title = Copy.LEAVE_THIS_ROOM) { confirmLeave = true }`.
    // With onClick earlier, that form binds the lambda to `size` instead —
    // a type error rather than a silent bug, but a trap either way.
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            // The words are the whole control, so the role has to be said:
            // without it TalkBack reads the text and never that it is a
            // thing you can press (§11).
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        SmallCaps(title, size = size, color = color)
    }
}

/** A line of small caps metadata — the running-head voice used all over the
 *  app: room names, states, relative times, quiet controls. */
@Composable
fun SmallCaps(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 13f,
    color: Color = Palette.muted,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = RibbonType.smallCaps(size, weight),
        color = color,
        modifier = modifier,
    )
}

/** A hairline rule at the measure's width. */
@Composable
fun HairlineRule(modifier: Modifier = Modifier, color: Color = Palette.rule) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/**
 * One readable column, centred.
 *
 * The book designs phone screens; on a tablet or an unfolded foldable the
 * same layouts otherwise stretch edge to edge — 150-plus character Scripture
 * lines, a way-in capsule a thousand dp wide. A no-op at phone widths, so
 * nothing branches on a size class.
 */
fun Modifier.readableColumn(maxWidth: Dp = 620.dp): Modifier =
    this
        .fillMaxWidth()
        .wrapContentSize(Alignment.TopCenter)
        .widthIn(max = maxWidth)

/** Kept so the reading page can ask for its own, wider measure (680). */
object Measure {
    val readable: Dp = 620.dp
    val reading: Dp = 680.dp
}
