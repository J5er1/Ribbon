@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.design.Palette
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.services.VoiceRecorder
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Leaving a note (S05): the toolbar rises from the bottom after the
// long-press — the ink swatches, write, speak. Highlighting (S06) shares
// the toolbar. Dismissed by tapping anywhere in the text.
//
// This is the one floating toolbar in the product. §12.2 allows a
// docked/floating toolbar for exactly this surface and nothing else: there
// is no FAB, no bottom navigation and no tab bar anywhere in Ribbon, and
// this bar exists only while a verse is lifted.

/**
 * Which composer the reading screen is showing. Carried across from the
 * Swift as declared; the reading screen's own `ComposerState` is the one
 * that carries the verse address with it.
 */
enum class ComposerMode {
    toolbar,
    writing,
    speaking,
}

/**
 * Ribbon's floating chrome material (§12.1): system glass tinted so far
 * toward the unlit ground that it reads as depth rather than as glass,
 * legible mostly by its edge.
 *
 * iOS gets this from `Glass.regular.tint(Palette.ground.opacity(0.72))` — a
 * real backdrop material with a specular edge, refraction and an interactive
 * response. Android has no backdrop material: `Modifier.blur` blurs the
 * content it is applied to rather than what lies behind it, and sampling the
 * window's backdrop would mean rendering the reading surface a second time
 * on every frame while a finger is on the toolbar. So the nearest honest
 * thing is drawn instead — the raised surface under the same 0.72 tint of
 * the unlit ground, with a hairline edge that catches a little more light
 * along the top. The edge is what carried the material on iOS, and it is
 * what carries it here. Written down in docs/deviations.md.
 *
 * iOS's `interactive:` has no equivalent to carry across: the theme's flat
 * state layer (§12.2, the ripple's replacement) is what answers a press.
 *
 * Where glass never appears, on either platform: over Scripture, on the
 * fire, the shelf, embers, or as a screen background.
 */
private fun Modifier.ribbonGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Palette.raised.copy(alpha = 0.55f), shape)
    .background(Palette.ground.copy(alpha = 0.72f), shape)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Palette.text.copy(alpha = 0.14f), Palette.rule),
        ),
        shape = shape,
    )

/**
 * The toolbar's arrival, for the reading screen to hand to its
 * `AnimatedVisibility`.
 *
 * SwiftUI hangs `.transition(.move(edge: .bottom).combined(with: .opacity))`
 * on the view itself; in Compose an enter/exit transition belongs to the
 * parent that decides whether the child is there at all, so the transition
 * travels as a value rather than as a modifier. Under reduce motion it is a
 * cross-fade with no movement (§11).
 */
@Composable
fun leaveToolbarEnter(): EnterTransition {
    val fade = fadeIn(tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut))
    if (rememberReduceMotion()) return fade
    return slideInVertically(
        animationSpec = tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut),
        initialOffsetY = { it },
    ) + fade
}

/** The toolbar leaving, the same move in reverse. */
@Composable
fun leaveToolbarExit(): ExitTransition {
    val fade = fadeOut(tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut))
    if (rememberReduceMotion()) return fade
    return slideOutVertically(
        animationSpec = tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut),
        targetOffsetY = { it },
    ) + fade
}

/**
 * The toolbar that rises after the long-press (S05/S06).
 *
 * @param roomPaused Paused rooms: only highlight shows, greyed, with one
 *   line (S02).
 */
@Composable
fun LeaveToolbar(
    model: AppModel,
    room: Room,
    range: VerseRange,
    roomPaused: Boolean,
    onHighlight: (Ink) -> Unit,
    onWrite: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (roomPaused) {
            SmallCaps(Copy.NEW_NOTES_NEED_THE_ROOM, size = 12f)
        }
        Row(
            modifier = Modifier
                .height(TOOLBAR_HEIGHT)
                .ribbonGlass(CircleShape)
                // Eight swatches, a rule and two words are wider than a
                // phone. SwiftUI compresses the row; here it scrolls, so
                // the eighth ink stays reachable instead of being clipped
                // off the end. Nothing else changes: at one swatch — the
                // common case, a room of three or more — the row is
                // narrower than the screen and never moves.
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Two people: eight swatches, pick per highlight, last-used
            // pre-selected. Three or more: one swatch — yours (§4.5).
            // Paused: only highlight shows, greyed and inert — the room
            // reads everything and writes nothing (S02, §08).
            val mine = model.inkForNewHighlight(room)
            if (mine != null) {
                InkSwatch(
                    ink = mine,
                    isSelected = !roomPaused,
                    modifier = Modifier.alpha(if (roomPaused) 0.35f else 1f),
                ) {
                    if (!roomPaused) onHighlight(mine)
                }
            } else {
                // The eight sit in touchable columns that include the gap
                // between them, so the drawn 20 dp swatch keeps its
                // spacing while the finger gets a column the full height
                // of the bar to hit (§11 — a control only a stylus can hit
                // is broken).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ink.entries.forEach { ink ->
                        InkSwatch(
                            ink = ink,
                            isSelected = !roomPaused && ink == model.lastUsedInk,
                            targetWidth = SWATCH_DIAMETER + 14.dp,
                            modifier = Modifier.alpha(if (roomPaused) 0.35f else 1f),
                        ) {
                            if (!roomPaused) onHighlight(ink)
                        }
                    }
                }
            }

            if (!roomPaused) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Palette.rule),
                )

                QuietControl(
                    title = Copy.WRITE,
                    onClick = onWrite,
                    color = Palette.text,
                    size = 13f,
                )

                QuietControl(
                    title = Copy.SPEAK,
                    onClick = onSpeak,
                    color = Palette.text,
                    size = 13f,
                )
            }
        }
    }
}

/**
 * One ink, 20 dp, ringed when it is the one that will be used.
 *
 * @param targetWidth the width of the touch target the drawn swatch sits in
 *   the middle of; the swatch itself is always [SWATCH_DIAMETER].
 */
@Composable
fun InkSwatch(
    ink: Ink,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    targetWidth: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val color = ink.color
    Box(
        modifier = modifier
            .sizeIn(minWidth = targetWidth, minHeight = TOOLBAR_HEIGHT)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = Copy.inkNamed(ink.displayName)
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        // The selection ring sits 3 dp outside the swatch, so the canvas is
        // drawn wider than the swatch rather than the ring being inset.
        Canvas(Modifier.size(SWATCH_DIAMETER + 8.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color,
                radius = SWATCH_DIAMETER.toPx() / 2f,
                center = centre,
            )
            if (isSelected) {
                val stroke = 1.4.dp.toPx()
                drawCircle(
                    color = Palette.text.copy(alpha = 0.7f),
                    radius = SWATCH_DIAMETER.toPx() / 2f + 3.dp.toPx() - stroke / 2f,
                    center = centre,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/**
 * Write (S05): a composer sized to the note, growing as you type, anchored
 * above the keyboard. No formatting controls, no title, no character
 * limit shown. Save is a single control; no draft state.
 *
 * @param identity Changes when the note being edited changes, so a switch
 *   mid-compose starts from that note's own words rather than a stale draft.
 *   Swift gets this from `.id(identity)`, which rebuilds the view and throws
 *   its `@State` away with it; `remember(identity)` is the same instruction.
 */
@Composable
fun WriteComposer(
    verse: VerseAddress,
    initialText: String = "",
    identity: String = "",
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(identity) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }

    // SwiftUI lifts a focused field over the keyboard on its own; on Android
    // the composer says so itself, so it stays anchored above the keyboard
    // wherever the reading screen puts it. The navigation-bar inset belongs
    // to the reading screen, which owns the bottom chrome stack.
    LaunchedEffect(identity) { focusRequester.requestFocus() }

    fun save() {
        val trimmed = text.text.trim()
        if (trimmed.isEmpty()) return
        onSave(trimmed)
    }

    Column(
        modifier = modifier
            .imePadding()
            .readableColumn()
            .padding(horizontal = 16.dp)
            .background(Palette.surface, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.rule, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallCaps(verse.formatted, size = 12f)

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = RibbonType.ui(16f).copy(color = Palette.text),
            cursorBrush = SolidColor(Palette.chartreuse),
            // Swift's `lineLimit(1...12)`: one line to start, twelve before
            // the note scrolls inside itself.
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // ⌘↩ leaves the note — the convention a hardware-keyboard
                // iPad reader expects; ⌃↩ is the same convention on a
                // keyboard attached to an Android tablet, and both are
                // accepted so neither reader has to learn the other's.
                .onPreviewKeyEvent { event ->
                    val chord = event.isMetaPressed || event.isCtrlPressed
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && chord) {
                        save()
                        true
                    } else {
                        false
                    }
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Copy.TAKE_BACK,
                    style = RibbonType.ui(15f),
                    color = Palette.muted,
                )
            }
            Spacer(Modifier.weight(1f))
            QuietControl(
                title = Copy.LEAVE_IT,
                onClick = { save() },
                color = Palette.chartreuse,
                size = 13f,
            )
        }
    }
}

/**
 * Speak (S05): press and hold. The waveform draws live in your ink.
 * Release keeps it; drag away discards, with the waveform receding rather
 * than a confirmation.
 */
@Composable
fun SpeakControl(
    model: AppModel,
    ink: Ink,
    recorder: VoiceRecorder,
    onKeep: (File, List<Float>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var draggedAway by remember { mutableStateOf(false) }
    var deniedRoute by remember { mutableStateOf(false) }
    var storageFull by remember { mutableStateOf(false) }

    val keep by rememberUpdatedState(onKeep)
    val dismiss by rememberUpdatedState(onDismiss)

    fun beginRecording() {
        recorder.begin(model.store.audioFile("${Uuid.random()}.m4a"))
    }

    // The microphone is asked for at the moment the person chooses to speak
    // and never at launch (§6.1). Android can only raise the dialog from an
    // activity, so the ask lives here and the recorder is told it happened;
    // refused, the control routes to Settings once and never asks again
    // (S25).
    val microphone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recorder.noteAccessAsked()
        if (granted) beginRecording() else deniedRoute = true
    }

    /** Release: keep what was said, unless it was a mis-touch. */
    fun release() {
        val kept = recorder.finish()
        if (kept != null) keep(kept.file, kept.waveform) else dismiss()
    }

    /** Drag away: the recording goes, with no confirmation to answer. */
    fun throwAway() {
        recorder.discard()
        dismiss()
    }

    Column(
        modifier = modifier
            .ribbonGlass(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                val awayVertical = DRAG_AWAY_VERTICAL.toPx()
                val awayHorizontal = DRAG_AWAY_HORIZONTAL.toPx()
                awaitEachGesture {
                    // `DragGesture(minimumDistance: 0)`: the gesture starts
                    // at touch-down, so a finger that never moves still
                    // ends it.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var away = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val translation = change.position - down.position
                        away = abs(translation.y) > awayVertical ||
                            abs(translation.x) > awayHorizontal
                        draggedAway = away
                        if (!change.pressed) break
                    }
                    if (away) throwAway() else release()
                }
            }
            // Every gesture has a tap equivalent (§11). The press-and-hold
            // is two outcomes, so it announces as two actions rather than
            // as an instruction to hold a finger somewhere.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(Copy.LEAVE_IT) { release(); true },
                    CustomAccessibilityAction(Copy.TAKE_BACK) { throwAway(); true },
                )
            }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            storageFull ->
                // S25: a count about a device, not about a person.
                Text(
                    text = Copy.noRoomOnPhone(context, RECORDING_MEGABYTES),
                    style = RibbonType.ui(15f),
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                )

            deniedRoute -> {
                // Refused once: one route to Settings, then never asked
                // again (S25).
                Text(
                    text = Copy.MIC_NEEDED,
                    style = RibbonType.ui(15f),
                    color = Palette.text,
                )
                QuietControl(
                    title = Copy.OPEN_SETTINGS,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    color = Palette.chartreuse,
                    size = 13f,
                )
            }

            else -> {
                // The live waveform, in your ink. The bars are drawn rather
                // than laid out as eighty capsule views: the meter lands a
                // new peak every 50 ms, and one canvas redraws where eighty
                // recomposing shapes would not. SwiftUI's 50 ms linear
                // animation between peak counts is the sampling interval
                // itself, so what is drawn is the same shape arriving at
                // the same cadence.
                val peaks = recorder.livePeaks.takeLast(LIVE_BARS)
                val barColor = ink.color.copy(alpha = if (draggedAway) 0.25f else 1f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    val barWidth = BAR_WIDTH.toPx()
                    val pitch = barWidth + BAR_GAP.toPx()
                    val drawn = peaks.size
                    if (drawn == 0) return@Canvas
                    val total = drawn * pitch - BAR_GAP.toPx()
                    var x = (size.width - total) / 2f
                    peaks.forEach { peak ->
                        val height = max(MIN_BAR_HEIGHT.toPx(), peak * BAR_SCALE.toPx())
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, (size.height - height) / 2f),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                        x += pitch
                    }
                }

                SmallCaps(
                    text = if (draggedAway) Copy.LET_GO_TO_DISCARD else Copy.RELEASE_TO_LEAVE_IT,
                    size = 12f,
                    color = if (draggedAway) Palette.muted else Palette.text.copy(alpha = 0.7f),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val free = model.store.freeMegabytes()
        if (free != null && free < FREE_MEGABYTES_FLOOR) {
            storageFull = true
            return@LaunchedEffect
        }
        when {
            recorder.microphoneUndecided ->
                microphone.launch(VoiceRecorder.MICROPHONE_PERMISSION)

            !recorder.hasMicrophoneAccess -> deniedRoute = true

            else -> beginRecording()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // The composer leaving the screen for any reason — a tap in
            // the text, the book closing — ends the recording. The mic is
            // never left hot.
            if (recorder.isRecording) {
                recorder.discard()
            }
        }
    }
}

/** The bar's height, and the height of the swatch column's touch target. */
private val TOOLBAR_HEIGHT = 52.dp

/** The drawn swatch. The touch target around it is larger (§11). */
private val SWATCH_DIAMETER = 20.dp

/** How many live peaks the waveform shows — the tail of the recording. */
private const val LIVE_BARS = 80

private val BAR_WIDTH = 2.5.dp
private val BAR_GAP = 2.dp

/** A silent moment still draws a thread. */
private val MIN_BAR_HEIGHT = 3.dp

/** Full deflection, for a peak of 1. */
private val BAR_SCALE = 36.dp

/** Drag past this and the note goes. Vertical is the shorter throw: the
 *  finger came down from the toolbar, so up-and-away is the movement a hand
 *  makes to abandon something. */
private val DRAG_AWAY_VERTICAL = 70.dp
private val DRAG_AWAY_HORIZONTAL = 90.dp

/**
 * The floor for starting a recording, and the size the line names. They are
 * two different numbers on iOS as well, and both are carried across: twenty
 * megabytes free is the room a recording needs to be safe to start, and
 * about five is what a note of ordinary length actually takes — which is the
 * number worth saying to a person clearing space.
 */
private const val FREE_MEGABYTES_FLOOR = 20
private const val RECORDING_MEGABYTES = 5
