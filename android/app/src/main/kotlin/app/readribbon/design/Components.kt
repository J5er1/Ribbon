package app.readribbon.design

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

// Small shared pieces: portraits, note marks, ink dots, the wide way-in
// control. Each one is specified somewhere in the build book; the section is
// cited where it matters.

/**
 * Whether the system has been asked to still its animations.
 *
 * Android has no single "reduce motion" switch the way iOS does; turning
 * animations off in Developer options or via an accessibility service sets
 * the animator duration scale to zero, and that is the signal every
 * well-behaved app reads. §11 then applies: the fire holds a state instead
 * of flickering, morphs become cross-fades, and the thinking-of-you fill
 * becomes an instant state change with the haptic intact.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
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
    Text(
        text = title,
        style = RibbonType.ui(18f, FontWeight.Medium),
        color = Palette.ground,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .background(if (enabled) Palette.chartreuse else Palette.chartreuse.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
    )
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
