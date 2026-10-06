package app.readribbon.design

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Person
import app.readribbon.core.QuietHoursBand
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.TimeZone as IcuTimeZone

// Things drawn in the room's own idiom (A66).
//
// One new shape, and what is made of it. The ribbon tail is the Wave's own
// tail made straight — "straight reads calm and bookish" (brief §5) — a band
// that ends in a swallowtail notch a little under half its width deep, which
// is what the Wave's two tails measure. It is the chosen version, the room
// you are in, your rooms on You and your ink in the picker: one shape, so the
// mark that means "this one" is the same mark wherever it means it.
//
// And the quiet hours band. Android keeps Material's switch and slider —
// A18/A29 took Material's structure with Ribbon's paint, and those two are
// structure — but the time picker was never structure here. It was a clock
// face in a dialog, opened twice to say one thing, and the thing it says is a
// night. So the night is drawn, and the two ends of it are what you move.

/**
 * The ribbon's end, drawn into this scope: a band [width] wide and [length]
 * long from [topLeft], cut at the foot into a swallowtail [notch] of its
 * width deep.
 *
 * Hard edges, one fill — no stroke, no gradient, no shadow. A ribbon is a
 * thing laid in a book, not a control asking to be pressed.
 *
 * Plain colours only: a draw scope is not a composition, so whatever reads
 * the room's palette reads it before this and hands the colour in (A18).
 */
fun DrawScope.drawRibbonTail(
    color: Color,
    topLeft: Offset,
    width: Float,
    length: Float,
    // The Wave's own tails measure about 0.43 of their width.
    notch: Float = 0.45f,
) {
    if (width <= 0f || length <= 0f) return
    // Never deeper than the ribbon is long: a ribbon laid in from nothing is
    // a sliver for a frame or two, and a notch that reached past its top
    // would turn it inside out.
    val depth = minOf(notch * width, length)
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topLeft.x + width, topLeft.y)
        lineTo(topLeft.x + width, topLeft.y + length)
        lineTo(topLeft.x + width / 2f, topLeft.y + length - depth)
        lineTo(topLeft.x, topLeft.y + length)
        close()
    }
    drawPath(path = path, color = color)
}

/**
 * A ribbon's end, still: [width] by [length], hanging from its own top edge.
 *
 * Hidden from the screen reader. A ribbon is always the picture of something
 * the row or column it hangs in says in words.
 */
@Composable
fun RibbonTail(
    color: Color,
    width: Dp,
    length: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(width, length)
            .clearAndSetSemantics {},
    ) {
        drawRibbonTail(color, Offset.Zero, size.width, size.height)
    }
}

/**
 * The chosen one's mark: a ribbon laid into the top edge of whatever was
 * chosen, and lifted out of whatever stopped being chosen (A66).
 *
 * It grows down from its top edge on the handled spring — the size of a
 * hand, and a hand is what lays a ribbon into a book — and lifts back up the
 * same way, so the choice moving is two ribbons, one going in and one coming
 * out, each in its own row. Nothing travels down the list between them: a
 * mark that slid past rows nobody chose would say they had been passed
 * through.
 *
 * Under reduce motion it is drawn at its full length and only its opacity
 * changes, on the clock that keeps a fade a fade (I22).
 *
 * Hidden from the screen reader: the row it marks says `selected` itself,
 * which is the signal that is not a colour.
 */
@Composable
fun ChoiceRibbon(
    laid: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
    length: Dp = 22.dp,
) {
    val still = rememberReduceMotion()
    val target = if (laid) 1f else 0f
    val reach = animateFloatAsState(
        targetValue = target,
        animationSpec = RibbonMotion.handled(still),
        label = "a-ribbon-laid-in",
    )
    val shown = remember { Animatable(target) }
    LaunchedEffect(target, still) {
        if (still) {
            withContext(FadesUnderReduceMotion) { shown.animateTo(target, RibbonMotion.arrive()) }
        } else {
            // Moving, the length carries the change and the opacity stays
            // whole; kept in step so that turning reduce motion on mid-way
            // leaves nothing half-faded.
            shown.snapTo(target)
        }
    }
    Canvas(
        modifier = modifier
            .size(width, length)
            .clearAndSetSemantics {}
            // Read in the layer, so the ribbon going in redraws one ribbon
            // rather than recomposing the row it is in.
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = if (still) 1f else reach.value
                alpha = if (still) shown.value else 1f
            },
    ) {
        drawRibbonTail(color, Offset.Zero, size.width, size.height)
    }
}

/**
 * A ribbon hanging from a binding: one of your rooms on You, an ink in the
 * picker (A66).
 *
 * @param length where it hangs to. A change of length is drawn rather than
 *   cut — the ink you choose is pulled down past the others on the handled
 *   spring, the one you leave goes back up — and under reduce motion the
 *   ribbon at its new length fades in over the old one, which fades out.
 *   The ribbon takes the room of its new length at once, so nothing beside
 *   or under it is moved frame by frame.
 * @param color its ink. A faint ribbon — an ink somebody else holds — is
 *   faded as a whole, so the two lengths crossing under reduce motion do
 *   not darken where they overlap.
 * @param layInDelayMillis when set, the ribbon is not there when it first
 *   appears, and after this long it lays in from the binding on `settle` —
 *   or, under reduce motion, fades in. Once per appearance: it is a ribbon
 *   arriving with the page, not one that arrives every time you look. A
 *   row of them laid in one after another is a row of these with growing
 *   delays.
 */
@Composable
fun HangingRibbon(
    color: Color,
    width: Dp,
    length: Dp,
    modifier: Modifier = Modifier,
    layInDelayMillis: Int? = null,
) {
    val still = rememberReduceMotion()

    val laid = remember { Animatable(if (layInDelayMillis == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (layInDelayMillis == null) return@LaunchedEffect
        delay(layInDelayMillis.toLong())
        if (still) {
            withContext(FadesUnderReduceMotion) { laid.animateTo(1f, RibbonMotion.arrive()) }
        } else {
            laid.animateTo(1f, RibbonMotion.settle())
        }
    }

    val reach = animateDpAsState(
        targetValue = length,
        animationSpec = RibbonMotion.handled(still),
        label = "a-ribbon-pulled",
    )

    // Under reduce motion: the length it is leaving, the length it is going
    // to, and how far the one has faded into the other.
    var leaving by remember { mutableStateOf(length) }
    var going by remember { mutableStateOf(length) }
    val crossing = remember { Animatable(1f) }
    LaunchedEffect(length, still) {
        if (!still) {
            leaving = length
            going = length
            crossing.snapTo(1f)
            return@LaunchedEffect
        }
        if (length == going) return@LaunchedEffect
        // Interrupted half way, it leaves whichever of the two was showing
        // more — never one that had already gone.
        if (crossing.value >= 0.5f) leaving = going
        going = length
        crossing.snapTo(0f)
        withContext(FadesUnderReduceMotion) { crossing.animateTo(1f, RibbonMotion.arrive()) }
        leaving = length
    }

    val solid = color.copy(alpha = 1f)
    Canvas(
        modifier = modifier
            .size(width, length)
            .clearAndSetSemantics {}
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = if (still) 1f else laid.value
                alpha = color.alpha * (if (still) laid.value else 1f)
            },
    ) {
        if (!still) {
            drawRibbonTail(solid, Offset.Zero, size.width, reach.value.toPx())
            return@Canvas
        }
        val from = leaving.toPx()
        val to = going.toPx()
        // The shorter of the two is inside the longer, so it stays whole and
        // only the difference fades: a ribbon that dimmed all over while it
        // changed length would read as two ribbons, not one.
        drawRibbonTail(solid, Offset.Zero, size.width, minOf(from, to))
        if (from != to) {
            val longer = if (to > from) crossing.value else 1f - crossing.value
            if (longer > 0f) {
                drawRibbonTail(solid.copy(alpha = longer), Offset.Zero, size.width, maxOf(from, to))
            }
        }
    }
}

// MARK: Quiet hours, drawn as the night (S19, A66)

/** The band: a finger's height, at the nested radius a well inside a tile takes. */
private val BandHeight = 44.dp
private val BandShape = RibbonShape.smallShape

/** A handle: a capsule the height of the night, not of the band. */
private val HandleWidth = 6.dp
private val HandleHeight = 30.dp

/** What a finger, or a screen reader's focus, has to find a handle by (§11). */
private val HandleTarget = 44.dp

/** The marks under the band: a tick from its lower edge, and the hour. */
private val TickWidth = 1.dp
private val TickHeight = 5.dp
private val TickGap = 2.dp
private const val MARK_SIZE = 10f

/**
 * A handle's place, as a screen reader moves it: one quarter of an hour of
 * the band at a time, noon at one end and a quarter to noon at the other.
 */
private const val QUARTERS = QuietHoursBand.day / QuietHoursBand.step
private const val LAST_QUARTER = QUARTERS - 1

/**
 * The quiet hours, as one band (S19, A66): a day from noon to noon, waking
 * hours raised and the night banked into the ground under it, with a handle
 * at each end of the night.
 *
 * Drag anywhere on the band and the nearer end comes to the finger; from
 * there it follows it, a quarter of an hour at a time, and every step is
 * written through to the settings as it is made. No animation while it moves
 * — the finger is the animation — and no haptic (I25).
 *
 * For a screen reader the drawing is nothing and the two ends are
 * everything: each is its own adjustable control, named for which end it is
 * and saying its time, moved a quarter of an hour by each swipe.
 *
 * @param start the minute of the day the quiet hours begin.
 * @param end the minute they end. The same minute as [start] is no quiet
 *   hours at all, and the band is drawn with no night in it.
 */
@Composable
fun QuietHoursBandControl(
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val raised = Palette.raised
    val ground = Palette.ground
    val rule = Palette.rule
    val handle = Palette.text
    val muted = Palette.muted

    val spans = QuietHoursBand.spans(start, end)
    val startAt = QuietHoursBand.position(start).toFloat()
    val endAt = QuietHoursBand.position(end).toFloat()

    // The gesture outlives a composition — it is started once and runs for
    // as long as the band is there — so it reads the ends and the way back
    // to the settings as they are now, not as they were when it began.
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val setStart by rememberUpdatedState(onStart)
    val setEnd by rememberUpdatedState(onEnd)

    val is24 = DateFormat.is24HourFormat(context)
    val locale = LocalConfiguration.current.locales[0]
    val hours = remember(is24, locale) {
        QuietHoursBand.marks.map { hourOnly(it, is24, locale) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(BandHeight)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val across = size.width.toFloat()
                        if (across <= 0f) return@awaitEachGesture
                        val x = down.position.x
                        // The nearer end takes the finger. A tie goes to the
                        // end of the night, so that with no quiet hours at
                        // all — both ends on one minute — a drag draws the
                        // night out rather than pushing it back.
                        val takesStart =
                            abs(x - QuietHoursBand.position(latestStart).toFloat() * across) <
                                abs(x - QuietHoursBand.position(latestEnd).toFloat() * across)
                        var last = if (takesStart) latestStart else latestEnd
                        fun follow(to: Float) {
                            val minute = QuietHoursBand.minute((to / across).toDouble())
                            if (minute == last) return
                            last = minute
                            if (takesStart) setStart(minute) else setEnd(minute)
                        }
                        follow(x)
                        down.consume()
                        // Consumed as it goes, so a drag that wanders up or
                        // down stays the band's rather than becoming the
                        // page's scroll half way through a night.
                        drag(down.id) { change ->
                            follow(change.position.x)
                            change.consume()
                        }
                    }
                },
        ) {
            val across = constraints.maxWidth.toFloat()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(BandShape)
                    .background(raised)
                    .border(1.dp, rule, BandShape)
                    .clearAndSetSemantics {},
            ) {
                // The night: the room's own ground and its grain, banked. One
                // layer cut to the quiet stretches rather than a box for each,
                // so the grain stays where the paper is while an end moves
                // over it — paper does not slide.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            for (span in spans) {
                                clipRect(
                                    left = span.from.toFloat() * size.width,
                                    right = span.to.toFloat() * size.width,
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                        .background(ground)
                        .grain(),
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawHandle(handle, startAt)
                    drawHandle(handle, endAt)
                }
            }
            // Placed by the band's own left edge, as the band is drawn, so a
            // right-to-left page cannot put an end's focus at the far side of
            // the night from the end it names.
            HandleSpoken(
                label = Copy.QUIET_HOURS_BEGIN,
                minute = start,
                spoken = clockTime(context, start),
                centre = startAt * across,
                across = across,
                onChange = onStart,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft),
            )
            HandleSpoken(
                label = Copy.QUIET_HOURS_END,
                minute = end,
                spoken = clockTime(context, end),
                centre = endAt * across,
                across = across,
                onChange = onEnd,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft),
            )
        }

        // Three hours a person already knows where they are in a night by:
        // evening, midnight, morning. Hidden — the ends say their own times.
        Layout(
            content = {
                hours.forEach { hour -> SmallCaps(hour, size = MARK_SIZE, color = muted) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .drawBehind {
                    val tick = TickWidth.toPx()
                    for (mark in QuietHoursBand.marks) {
                        val x = QuietHoursBand.position(mark).toFloat() * size.width
                        drawRect(
                            color = rule,
                            topLeft = Offset(x - tick / 2f, 0f),
                            size = Size(tick, TickHeight.toPx()),
                        )
                    }
                },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val placeables = measurables.map { it.measure(Constraints()) }
            val top = (TickHeight + TickGap).roundToPx()
            val height = top + (placeables.maxOfOrNull { it.height } ?: 0)
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val centre = QuietHoursBand.position(QuietHoursBand.marks[index]) * width
                    val left = (centre - placeable.width / 2.0).roundToInt()
                    // `place`, not `placeRelative`: the hours sit under the
                    // band as it is drawn, which is left to right either way.
                    placeable.place(left.coerceIn(0, max(0, width - placeable.width)), top)
                }
            }
        }
    }
}

/** One end of the night, drawn: a capsule across the band at [at] (0…1). */
private fun DrawScope.drawHandle(color: Color, at: Float) {
    val w = HandleWidth.toPx()
    val h = HandleHeight.toPx()
    val edge = 1.dp.toPx()
    // Held inside the band's own edge, so the end at noon is a handle at the
    // band's end rather than half a handle cut off by it.
    if (size.width < w + edge * 2f) return
    val centre = (at * size.width).coerceIn(w / 2f + edge, size.width - w / 2f - edge)
    drawRoundRect(
        color = color,
        topLeft = Offset(centre - w / 2f, (size.height - h) / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2f),
    )
}

/**
 * One end of the night, as a screen reader has it: a control named for
 * which end it is, saying its time, over the place the end is drawn.
 *
 * Adjustable the way Material's own slider is — a range and a way to set a
 * place in it — so TalkBack moves it by a swipe without a word of new copy.
 * The range is the band's quarter hours, noon to a quarter to noon, which is
 * what makes one swipe one quarter: the same step a finger takes.
 */
@Composable
private fun HandleSpoken(
    label: String,
    minute: Int,
    spoken: String,
    centre: Float,
    across: Float,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quarter = (QuietHoursBand.position(minute) * QUARTERS)
        .toFloat()
        .coerceIn(0f, LAST_QUARTER.toFloat())
    Box(
        modifier = modifier
            .absoluteOffset {
                val target = HandleTarget.toPx()
                val left = (centre - target / 2f).coerceIn(0f, max(0f, across - target))
                IntOffset(left.roundToInt(), 0)
            }
            .size(HandleTarget, BandHeight)
            .semantics {
                contentDescription = label
                stateDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = quarter,
                    range = 0f..LAST_QUARTER.toFloat(),
                    // One fewer than the gaps between quarters, which is how
                    // a range is told its swipe is a single quarter.
                    steps = LAST_QUARTER - 1,
                )
                setProgress { target ->
                    val steps = (target - quarter).roundToInt()
                    if (steps != 0) onChange(QuietHoursBand.stepped(minute, steps))
                    true
                }
            },
    )
}

/**
 * A minute of the day as the phone tells the time — 12 or 24 hours, as its
 * owner set it — the same words the row above the band uses.
 *
 * Formatted on the first of January 1970 in UTC rather than on today here,
 * so that a night that begins at half past two is never told as half past
 * three on the morning the clocks go forward.
 */
private fun clockTime(context: Context, minute: Int): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(QuietHoursBand.wrapped(minute) * MINUTE_MS))
}

/**
 * An hour alone, as this phone says one: "6 PM", or "18" where the day has
 * twenty-four hours. ICU's own formatter, because the locale's best pattern
 * for an hour can carry a day period only ICU knows how to set.
 */
private fun hourOnly(minute: Int, is24: Boolean, locale: Locale): String {
    val format = IcuDateFormat.getInstanceForSkeleton(if (is24) "H" else "ha", locale)
    format.timeZone = IcuTimeZone.getTimeZone("UTC")
    return format.format(Date(QuietHoursBand.wrapped(minute) / 60 * HOUR_MS))
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L

// MARK: A notification, shown before it arrives (S19, A66)

/**
 * What a switch's notification will say, and whose face it will come with —
 * shown under the switch, in a room of two, instead of a sentence about it.
 *
 * @param portrait the person's face where there is one, as [PortraitView]
 *   takes it; without one the monogram in [ink] stands in.
 */
@Immutable
data class SettingExample(
    val person: Person?,
    val ink: Ink?,
    val portrait: ImageBitmap?,
    val sentence: String,
)

/** The face that leads the example: the size of a face in a notification. */
private val ExampleFace = 16.dp

/**
 * A notification, drawn small: the face and the sentence, recessed into the
 * tile as the well a control sits in is, so it reads as a picture of the
 * thing that will arrive rather than as one more line about it.
 *
 * Spoken as one line that says it is the words, not the setting — inside a
 * switch's row the screen reader reads it after the switch's own name.
 */
@Composable
fun SettingExampleView(example: SettingExample, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .well(RibbonShape.smallShape)
            .clearAndSetSemantics {
                contentDescription = Copy.notificationExampleSpoken(example.sentence)
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitView(
            person = example.person,
            ink = example.ink,
            size = ExampleFace,
            image = example.portrait,
        )
        Text(
            text = example.sentence,
            // 13, where iOS sets 14: Android's quiet lines under a title are
            // a point smaller throughout, and this sits where one would.
            style = RibbonType.ui(13f),
            color = Palette.text.copy(alpha = 0.8f),
        )
    }
}
