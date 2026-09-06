@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.HairlineRule
import app.readribbon.design.LocalHaptics
import app.readribbon.design.Measure
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.rememberReduceMotion
import app.readribbon.services.PresentPerson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The presence form (§4.2, S07): a soft form half-emerged from the right
// edge. Left edge is the gutter and belongs to notes; the right edge
// belongs to people. Nothing crosses.
//
// Five states: absent (nobody here — not greyed, not a placeholder),
// someone here, several here (a short vertical stack, never a row of
// shrinking avatars), following (a ring in their ink and a chartreuse
// thread down the edge), and reading quietly (you see a small closed
// shape; others see nothing at all).
//
// iOS draws both the lozenge and the panel in Liquid Glass, and this is the
// ideal case for it. §12.2 gives Android the shape system and morphing
// instead — "the presence form's lozenge→panel morph is exactly what shape
// morphing is for" — with damped physics and no overshoot. There is no
// glass here and none is faked: a raised surface with a hairline edge, the
// same geometry, and a real Morph between the lozenge's corners and the
// panel's, driven by RibbonMotion's open token.
//
// Glass never lies over a verse on iOS, and the panel is capped there to
// the space outside the measure. The rule holds here: the panel reports the
// width it needs on the trailing side through [PresenceForm]'s
// `onMeasureInset`, and where the screen is too narrow to hold both, the
// measure insets and the text moves — the panel never covers Scripture.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an equivalent ambient value yet, so the model is a
// parameter, exactly as it is in NoteCard.

/** The lozenge: 44 × 64, half of it off the screen's edge. */
private val LOZENGE_WIDTH = 44.dp
private val LOZENGE_HEIGHT = 64.dp

/** Reading quietly, collapsed: a small closed shape, 20 × 34. */
private val QUIET_WIDTH = 20.dp
private val QUIET_HEIGHT = 34.dp

/** The panel's width on iOS, and the most it may ever take here. */
private val PANEL_WIDTH = 200.dp

private val LOZENGE_CORNER = 20.dp
private val PANEL_CORNER = 22.dp

/** `.padding(.trailing, -14)` — the form sits half off-screen. */
private val HALF_OFF_SCREEN = 14.dp

/** A finger's tap is a blunt thing, so nothing here is smaller than this to
 *  touch, however small it is drawn (§11). */
private val TOUCH_TARGET = 44.dp

/** `DragGesture(minimumDistance: 12)` opening or closing on 20 points. */
private val DRAG_TO_TOGGLE = 20.dp

/** `onLongPressGesture(minimumDuration: 0.35)` on the lozenge. */
private const val LOZENGE_HOLD_MS = 350L

/** `onLongPressGesture(minimumDuration: 0.7)` in a row — the same 700 ms the
 *  ink takes to fill, so the fill completing *is* the hold completing. */
private val THINKING_HOLD_MS = RibbonMotion.INK_FILL_MS.toLong()

/** The release when a hold is let go early. */
private const val HOLD_RELEASE_MS = 150

private fun <T> openSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(RibbonMotion.OPEN_MS, easing = RibbonMotion.EaseOut)

private fun <T> arriveSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut)

/**
 * The presence form: the lozenge at the right edge, and the panel it
 * becomes.
 *
 * @param onFollow tap a portrait to follow — a page-fly, no confirmation
 *   dialog (§4.2).
 * @param measure the reading measure the caller is setting its text at. The
 *   panel is capped to the space outside it.
 * @param onMeasureInset how much room the panel needs on the trailing side
 *   beyond the space outside the measure — 0 while the form is closed, and
 *   0 on a screen wide enough to hold the panel beside the text. The
 *   reading surface insets its measure by this, so the text moves rather
 *   than being covered. This is the Android shape of the iOS rule that
 *   glass never lies over a verse; Swift gets it from the panel's own
 *   geometry because a phone's overlay there is already outside the
 *   measure.
 */
@Composable
fun PresenceForm(
    model: AppModel,
    room: Room,
    onFollow: (PresentPerson) -> Unit,
    modifier: Modifier = Modifier,
    measure: Dp = Measure.reading,
    onMeasureInset: (Dp) -> Unit = {},
) {
    val people = model.presentPeople
    val reduceMotion = rememberReduceMotion()

    var expanded by remember { mutableStateOf(false) }

    /** "Ruth is with you" appears once per follower, then rests. */
    val announcedFollowers = remember { mutableStateListOf<Uuid>() }

    /** Someone whose scroll is yours: their portrait tucks against the form. */
    val follower = model.me?.id?.let { me -> people.firstOrNull { it.followingPersonID == me } }

    // Absence is the honest rendering of absence.
    val present = people.isNotEmpty() || model.readingQuietly

    // Predictive back closes the panel: the room peels in behind it as the
    // gesture is pulled, and the panel is only actually let go when the
    // gesture completes. A cancelled back leaves the panel exactly as it
    // was, which is the whole point of the API.
    var backPull by remember { mutableStateOf(0f) }
    PredictiveBackHandler(enabled = expanded) { progress ->
        try {
            progress.collect { event -> backPull = event.progress }
            expanded = false
            backPull = 0f
        } catch (cancelled: CancellationException) {
            backPull = 0f
            throw cancelled
        }
    }

    // 0 is the lozenge, 1 is the panel; the back gesture pulls it back
    // toward the lozenge it came from without committing to it. Damped, and
    // held rather than moved under reduce-motion (§11).
    val opened by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else RibbonMotion.open(),
        label = "presence-morph",
    )
    val morph = (opened * (1f - backPull)).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            // Edge to edge is the room, not an effect; the form still keeps
            // clear of a cutout on the side it lives on. The collapsed
            // lozenge's half-off-screen offset is measured from this safe
            // edge rather than from the glass, so it is half off the edge a
            // thumb can actually reach.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val panelWidth = minOf(PANEL_WIDTH, maxWidth - TOUCH_TARGET).coerceAtLeast(QUIET_WIDTH)
        // The space outside the measure is the panel's by right; anything
        // more than that has to come out of the text's width instead.
        val outsideMeasure = ((maxWidth - measure) / 2).coerceAtLeast(0.dp)
        val inset = (panelWidth - outsideMeasure).coerceAtLeast(0.dp)
        LaunchedEffect(expanded, inset) { onMeasureInset(if (expanded) inset else 0.dp) }

        AnimatedVisibility(
            visible = present,
            enter = slideInHorizontally(arriveSpec(reduceMotion)) { it } +
                fadeIn(arriveSpec(reduceMotion)),
            exit = slideOutHorizontally(arriveSpec(reduceMotion)) { it } +
                fadeOut(arriveSpec(reduceMotion)),
            label = "presence-form",
        ) {
            Column(
                // Half off-screen while it is a lozenge, fully on once it is
                // a panel. Swift uses a negative trailing padding, which
                // Compose has no equivalent of, so the form is offset
                // instead — and the tucked portrait and the line under it
                // travel with it, exactly as they do inside Swift's stack.
                modifier = Modifier.offset(x = HALF_OFF_SCREEN * (1f - morph)),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Box(contentAlignment = Alignment.CenterEnd) {
                    PresenceSurface(
                        model = model,
                        room = room,
                        people = people,
                        expanded = expanded,
                        morph = morph,
                        backPull = backPull,
                        panelWidth = panelWidth,
                        reduceMotion = reduceMotion,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false },
                        onFollow = onFollow,
                    )
                    // Being followed is visible but small: their portrait
                    // tucks against yours (§4.2). Never a count of
                    // followers — one person, tucked, or nothing.
                    if (follower != null && !expanded) {
                        PortraitView(
                            person = model.person(follower.id),
                            ink = model.membership(follower.id, room.id)?.ink,
                            size = 20.dp,
                            image = model.portrait(follower.id),
                            modifier = Modifier.offset(x = (-30).dp, y = 24.dp),
                        )
                    }
                }

                // "Ruth is with you" is said once, four seconds after they
                // arrive behind you, and then rests. It is never said again
                // for the same person, and it is never a number.
                if (follower != null) {
                    LaunchedEffect(follower.id) {
                        delay(4.seconds)
                        if (follower.id !in announcedFollowers) announcedFollowers.add(follower.id)
                    }
                }
                val announcing = follower?.takeIf {
                    !expanded && it.id !in announcedFollowers
                }
                AnimatedVisibility(
                    visible = announcing != null,
                    enter = fadeIn(arriveSpec(reduceMotion)),
                    exit = fadeOut(arriveSpec(reduceMotion)),
                    label = "is-with-you",
                ) {
                    val name = announcing?.let { model.person(it.id)?.name ?: it.name }
                    if (name != null) {
                        SmallCaps(
                            Copy.isWithYou(firstName(name)),
                            size = 11f,
                            modifier = Modifier.padding(end = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one surface, in both of its shapes.
 *
 * The lozenge and the panel are the same raised form: the size carries the
 * change and the outline is a genuine [Morph] between the lozenge's
 * near-stadium corners and the panel's softer ones, so the corners
 * interpolate as geometry rather than as a number (§12.2). Under
 * reduce-motion the morph is a cross-fade between two held shapes (§11).
 */
@Composable
private fun PresenceSurface(
    model: AppModel,
    room: Room,
    people: List<PresentPerson>,
    expanded: Boolean,
    morph: Float,
    backPull: Float,
    panelWidth: Dp,
    reduceMotion: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onFollow: (PresentPerson) -> Unit,
) {
    val shape = LozengePanelShape(morph, LOZENGE_CORNER, PANEL_CORNER)

    val collapsedGestures = Modifier
        .pointerInput(model.readingQuietly, people) {
            var travelled = 0f
            val threshold = DRAG_TO_TOGGLE.toPx()
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled < -threshold) onExpand() },
                onDragCancel = { travelled = 0f },
            ) { change, amount ->
                travelled += amount
                change.consume()
            }
        }
        .pointerInput(model.readingQuietly, people) {
            // `onTapGesture` and `onLongPressGesture(minimumDuration: 0.35)`
            // on one form. Compose's own long-press detector fires at the
            // platform timeout, which is longer, so the 350 ms is timed
            // here and the tap is the same touch letting go before it.
            awaitEachGesture {
                awaitFirstDown()
                var released = false
                val heldLongEnough = withTimeoutOrNull(LOZENGE_HOLD_MS) {
                    released = waitForUpOrCancellation() != null
                } == null
                if (heldLongEnough) {
                    onExpand()
                    // The finger is still down; it must not also read as a tap.
                    waitForUpOrCancellation()
                } else if (released) {
                    if (model.readingQuietly || people.size > 1) {
                        onExpand()
                    } else {
                        people.firstOrNull()?.let(onFollow)
                    }
                }
            }
        }

    val panelGestures = Modifier
        .pointerInput(Unit) {
            var travelled = 0f
            val threshold = DRAG_TO_TOGGLE.toPx()
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled > threshold) onCollapse() },
                onDragCancel = { travelled = 0f },
            ) { change, amount ->
                travelled += amount
                change.consume()
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { onCollapse() }
        }

    Box(
        modifier = Modifier
            .graphicsLayer {
                // The panel shrinks back toward the edge it came out of as
                // the back gesture is pulled.
                val scale = 1f - 0.06f * backPull
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            // Every touch target is at least 44 dp even when the drawn
            // control is smaller — the quiet shape is 20 dp wide.
            .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
            .then(if (expanded) panelGestures else collapsedGestures),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedContent(
            targetState = expanded,
            modifier = Modifier
                .background(Palette.raised, shape)
                // No glass: a hairline edge instead, the same 1 pt rule the
                // iOS capsule strokes itself with.
                .border(1.dp, Palette.rule, shape),
            transitionSpec = {
                (fadeIn(openSpec(reduceMotion)) togetherWith fadeOut(openSpec(reduceMotion)))
                    .using(SizeTransform(clip = false) { _, _ -> openSpec(reduceMotion) })
            },
            contentAlignment = Alignment.CenterEnd,
            label = "presence-panel",
        ) { isPanel ->
            if (isPanel) {
                PresencePanel(
                    model = model,
                    room = room,
                    people = people,
                    panelWidth = panelWidth,
                    reduceMotion = reduceMotion,
                    onFollow = onFollow,
                    onCollapse = onCollapse,
                )
            } else {
                CollapsedForm(model = model, room = room, people = people)
            }
        }
    }
}

/** The lozenge, or the small closed shape you read behind. */
@Composable
private fun CollapsedForm(
    model: AppModel,
    room: Room,
    people: List<PresentPerson>,
) {
    if (model.readingQuietly) {
        // A small closed shape at the edge, so you never forget you're
        // invisible. Stays tappable even when you're alone — the surface
        // around it carries the 44 dp target.
        Box(
            Modifier
                .size(QUIET_WIDTH, QUIET_HEIGHT)
                .clearAndSetSemantics { contentDescription = Copy.READING_QUIETLY_SPOKEN },
        )
        return
    }
    val front = people.firstOrNull() ?: return
    // Read in composition, not inside the semantics lambda, so the label is
    // rebuilt when the roster changes rather than when it is asked for.
    val label = presenceLabel(model, people, front, othersCount = people.size - 1)
    Box(
        modifier = Modifier
            .size(LOZENGE_WIDTH, LOZENGE_HEIGHT)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Several here is a short vertical stack, never a row of shrinking
        // avatars: a hairline of the next one behind.
        val stackedShape = LozengePanelShape(0f, LOZENGE_CORNER, PANEL_CORNER)
        if (people.size > 1) {
            Box(
                Modifier
                    .offset(x = 5.dp, y = 8.dp)
                    .size(LOZENGE_WIDTH, LOZENGE_HEIGHT)
                    .alpha(0.6f)
                    .background(Palette.raised, stackedShape),
            )
        }
        PersonPortrait(model = model, room = room, person = front)
    }
}

/** Who's here, and the one gesture. */
@Composable
private fun PresencePanel(
    model: AppModel,
    room: Room,
    people: List<PresentPerson>,
    panelWidth: Dp,
    reduceMotion: Boolean,
    onFollow: (PresentPerson) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(panelWidth)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        people.forEach { person ->
            PersonRow(
                model = model,
                room = room,
                people = people,
                person = person,
                reduceMotion = reduceMotion,
                onFollow = onFollow,
            )
        }
        if (model.readingQuietly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same closed shape as the one at the edge, in miniature.
                val closed = LozengePanelShape(0f, LOZENGE_CORNER, PANEL_CORNER)
                Box(
                    Modifier
                        .size(14.dp, 22.dp)
                        .background(Palette.raised, closed)
                        .border(1.dp, Palette.rule, closed),
                )
                SmallCaps(Copy.ONLY_YOU_CAN_SEE_YOU, size = 11f)
            }
        }
        HairlineRule()
        QuietControl(
            title = Copy.READ_QUIETLY,
            onClick = {
                model.readingQuietly = !model.readingQuietly
                onCollapse()
            },
            color = if (model.readingQuietly) Palette.chartreuse else Palette.muted,
            size = 12f,
        )
    }
}

/**
 * One person in the panel: tap to follow, hold to let them know you're
 * thinking of them (§4.3).
 *
 * Swift hoists `holdTarget` beside `holdProgress` because one `@State` pair
 * serves every row; here the pair lives in the row that is being held. Only
 * one row can be under a finger, so the two are the same state — this one
 * simply cannot be left pointing at a row that is gone.
 */
@Composable
private fun PersonRow(
    model: AppModel,
    room: Room,
    people: List<PresentPerson>,
    person: PresentPerson,
    reduceMotion: Boolean,
    onFollow: (PresentPerson) -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val fill = remember(person.id) { Animatable(0f) }
    var holding by remember(person.id) { mutableStateOf(false) }

    val myInk = model.currentRoom?.let { model.myMembership(it)?.ink } ?: model.lastUsedInk
    val name = model.person(person.id)?.name ?: person.name
    val label = presenceLabel(model, people, person, othersCount = 0)

    val sendThinkingOfYou = {
        haptics?.completeThinkingOfYouHold()
        scope.launch { model.presence.sendThinkingOfYou(person.id) }
        Unit
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = TOUCH_TARGET)
            .pointerInput(person.id, reduceMotion) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    haptics?.beginThinkingOfYouHold()
                    // A `PointerInputScope` is not a coroutine scope, so the
                    // fill runs on the composition's — which is also what
                    // lets it finish its release after the gesture is over.
                    val filling = scope.launch {
                        if (reduceMotion) {
                            // An instant state change with the haptic
                            // intact (§11).
                            fill.snapTo(1f)
                        } else {
                            // The portrait fills with your ink over ~700 ms.
                            fill.animateTo(1f, RibbonMotion.inkFill())
                        }
                    }
                    var released = false
                    val heldLongEnough = withTimeoutOrNull(THINKING_HOLD_MS) {
                        released = waitForUpOrCancellation() != null
                    } == null
                    filling.cancel()
                    if (heldLongEnough) {
                        // Release completes it.
                        sendThinkingOfYou()
                        holding = false
                        scope.launch { fill.snapTo(0f) }
                        // The lift is the completion, never also a follow.
                        waitForUpOrCancellation()
                    } else {
                        haptics?.cancelThinkingOfYouHold()
                        holding = false
                        scope.launch {
                            fill.animateTo(
                                0f,
                                tween(HOLD_RELEASE_MS, easing = RibbonMotion.EaseOut),
                            )
                        }
                        if (released) onFollow(person)
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                // Every gesture has an equivalent that is not a gesture
                // (§11): the hold is published as an action, so it is
                // reachable without holding a press.
                onClick(label = Copy.FOLLOW) { onFollow(person); true }
                customActions = listOf(
                    CustomAccessibilityAction(Copy.THINKING_OF_YOU) {
                        sendThinkingOfYou()
                        true
                    },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PersonPortrait(model = model, room = room, person = person, box = 34.dp)
            if (holding || fill.value > 0f) {
                val ink = myInk.color
                val progress = fill.value
                // `requiredSize`, not `size`: the ring is drawn a little
                // larger than the portrait it runs around, and a fixed-size
                // parent would otherwise shrink it to fit.
                Canvas(Modifier.requiredSize(38.dp)) {
                    val stroke = 2.5.dp.toPx()
                    drawArc(
                        color = ink,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = RibbonType.ui(15f),
                color = Palette.text,
            )
            SmallCaps(
                text = if (person.isIdle) {
                    Copy.HERE_BUT_STILL
                } else {
                    // Where they are — an address, never a percentage.
                    person.position?.chapterFormatted ?: ""
                },
                size = 11f,
            )
        }
    }
}

/** A portrait, dimmed when they are here but still, ringed in their ink
 *  while you are following them. */
@Composable
private fun PersonPortrait(
    model: AppModel,
    room: Room,
    person: PresentPerson,
    box: Dp = 38.dp,
) {
    val ink = model.membership(person.id, room.id)?.ink
    Box(
        modifier = Modifier.size(box),
        contentAlignment = Alignment.Center,
    ) {
        PortraitView(
            person = model.person(person.id),
            ink = ink,
            size = 38.dp,
            image = model.portrait(person.id),
            modifier = Modifier
                .requiredSize(38.dp)
                .alpha(if (person.isIdle) 0.6f else 1f),
        )
        if (model.followingPersonID == person.id && ink != null) {
            val ringColor = ink.color
            Canvas(Modifier.requiredSize(40.dp)) {
                val stroke = 1.6.dp.toPx()
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/**
 * How presence announces itself (§11). Never a count of people — name who
 * else is here instead, so a stack announces by author.
 */
private fun presenceLabel(
    model: AppModel,
    people: List<PresentPerson>,
    person: PresentPerson,
    othersCount: Int,
): String {
    val name = model.person(person.id)?.name ?: person.name
    val base = if (person.isIdle) Copy.personIsHereButStill(name) else Copy.personIsReading(name)
    if (othersCount > 0) {
        val others = people.drop(1).map { model.person(it.id)?.name ?: it.name }
        return Copy.alsoHere(base, others)
    }
    return base
}

/**
 * The outline the form wears, between lozenge and panel.
 *
 * Both shapes are built at the size actually being drawn, so the corners
 * never smear with the aspect ratio the way a normalised shape's would, and
 * the radius is clamped to what the box can hold — at 20 × 34 that clamp is
 * what makes the quiet shape a capsule, exactly as the iOS `Capsule()` is.
 */
private data class LozengePanelShape(
    private val progress: Float,
    private val lozengeCorner: Dp,
    private val panelCorner: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }
        val limit = minOf(size.width, size.height) / 2f
        val start = with(density) { lozengeCorner.toPx() }.coerceAtMost(limit)
        val end = with(density) { panelCorner.toPx() }.coerceAtMost(limit)
        val lozenge = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            rounding = CornerRounding(start),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        val panel = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            rounding = CornerRounding(end),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(Morph(lozenge, panel).toPath(progress).asComposePath())
    }
}

/**
 * The chartreuse thread down the screen's right edge while following.
 *
 * It is drawn, not announced: following is already spoken by the ring in
 * their ink on their portrait, and a hairline has nothing of its own to say.
 */
@Composable
fun FollowThread(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Palette.chartreuse.copy(alpha = 0.7f))
            .clearAndSetSemantics { },
    )
}

// MARK: The end of a follow (§4.2)
//
// Swift keeps these two beside the scroll, in ReadingScreen: a scroll of
// your own breaks the follow, and for about two minutes afterwards the form
// quietly offers the way back. They are the last two states of the follow
// the form starts, so they are kept here with it; ReadingScreen owns the
// scroll and wires them.

/** About two minutes, then it forgets. */
val FOLLOW_BACK_WINDOW = 120.seconds

/**
 * The offer back to where you were, and its expiry.
 *
 * Swift holds a `(address, until)` tuple in `@State`; this is the same pair,
 * with the two-minute window named rather than added at the call site.
 */
@Stable
class FollowBackOfferState {
    var address: VerseAddress? by mutableStateOf(null)
        private set
    var until: Instant? by mutableStateOf(null)
        private set

    /** Remember where you were, at the moment a follow starts. */
    fun beganFollowing(from: VerseAddress, now: Instant = Clock.System.now()) {
        address = from
        until = now + FOLLOW_BACK_WINDOW
    }

    fun forget() {
        address = null
        until = null
    }

    /** Where you were, while the offer still stands. */
    fun addressIfLive(now: Instant = Clock.System.now()): VerseAddress? {
        val deadline = until ?: return null
        return if (now < deadline) address else null
    }
}

@Composable
fun rememberFollowBackOffer(): FollowBackOfferState = remember { FollowBackOfferState() }

/**
 * After a follow ends: the quiet offer back, for about two minutes, then it
 * forgets (§4.2).
 *
 * Nothing is said when it expires, and nothing is said about the follow
 * ending — a scroll of your own breaks a follow with no modal and no "stop
 * following?", you just have your own scroll back.
 */
@Composable
fun FollowBackOffer(
    offer: FollowBackOfferState,
    following: Boolean,
    onGoBack: (VerseAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two minutes, and then it forgets — on its own, without waiting for
    // something else on the screen to change and notice.
    val deadline = offer.until
    LaunchedEffect(deadline) {
        if (deadline != null) {
            val remaining = deadline - Clock.System.now()
            if (remaining.isPositive()) delay(remaining)
            offer.forget()
        }
    }
    val address = offer.addressIfLive()
    if (address == null || following) return
    QuietControl(
        title = Copy.BACK_TO_WHERE_YOU_WERE,
        onClick = {
            onGoBack(address)
            offer.forget()
        },
        modifier = modifier,
    )
}
