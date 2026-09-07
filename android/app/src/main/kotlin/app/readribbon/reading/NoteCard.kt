@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.RibbonClock
import app.readribbon.core.TranscriptState
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.rememberReduceMotion
import app.readribbon.services.VoicePlayer
import java.io.File
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi

// A note, open (S04) — inline in the text, never a sheet. The single most
// important emotional moment in the app: reading what someone left.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an equivalent ambient value yet, so the model is a
// parameter. It is a ViewModel holding Compose snapshot state, so reading
// `model.me` or `model.portrait(...)` here recomposes on change exactly as
// `@Observable` does.

/**
 * One note, opened in place.
 *
 * @param author the reader's view of the author.
 */
@Composable
fun NoteCard(
    model: AppModel,
    note: Note,
    author: Person?,
    authorInk: Ink,
    onTakeBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { VoicePlayer(context) }
    var transcriptShown by remember { mutableStateOf(false) }

    // Swift awaits `model.store.audioFileURL(path)` only because LocalStore
    // is an actor there; the call itself joins a path and touches nothing.
    // The Kotlin store is an ordinary object, so the file is resolved here
    // rather than in a coroutine — the `.task` had no work to wait for.
    val audioFile = remember(note.audioPath) { note.audioPath?.let(model.store::audioFile) }

    val isMine = note.authorID == model.me?.id
    var menuShown by remember { mutableStateOf(false) }

    // onDisappear { player.stop() }. Disposal additionally ends the player's
    // progress scope, because a card that leaves the composition is gone for
    // good — ARC does that half on iOS without being asked.
    DisposableEffect(player) { onDispose { player.dispose() } }

    val name = author?.name ?: ""
    val accessibilityText = when (note.kind) {
        NoteKind.written -> Copy.noteFrom(name, note.body ?: "")
        NoteKind.voice -> Copy.voiceNoteFrom(name, note.transcript ?: "")
    }

    // .contextMenu — long-press anywhere on the card, your own note only.
    // The same two actions are published as accessibility actions, so a
    // screen reader reaches them without holding a press (§11: every gesture
    // has an equivalent that is not a gesture).
    val menuActions =
        if (isMine) {
            listOf(
                CustomAccessibilityAction(Copy.EDIT) { onEdit(); true },
                CustomAccessibilityAction(Copy.TAKE_BACK) { onTakeBack(); true },
            )
        } else {
            emptyList()
        }

    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .pointerInput(isMine) {
                    if (!isMine) return@pointerInput
                    detectTapGestures(onLongPress = { menuShown = true })
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityText
                    if (menuActions.isNotEmpty()) customActions = menuActions
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PortraitView(
                    person = author,
                    ink = authorInk,
                    size = 22.dp,
                    image = author?.let { model.portrait(it.id) },
                )
                SmallCaps(RibbonClock.phrase(note.createdAt), size = 12f)
            }

            when (note.kind) {
                NoteKind.written ->
                    Text(
                        text = note.body ?: "",
                        style = RibbonType.ui(16f),
                        color = Palette.text,
                    )

                NoteKind.voice ->
                    VoiceBody(
                        model = model,
                        note = note,
                        authorInk = authorInk,
                        player = player,
                        transcriptShown = transcriptShown,
                        onToggleTranscript = { transcriptShown = !transcriptShown },
                        onTogglePlayback = {
                            togglePlayback(player, audioFile)
                        },
                    )
            }

            // When the note quotes the verse, the quote renders in the
            // author's translation, small — you see the words they were
            // looking at (§2.6).
            val quote = authorTranslationQuote(model, note, author)
            if (quote != null) {
                Text(
                    text = quote,
                    style = RibbonType.scripture(13f),
                    color = Palette.muted,
                )
            }
        }

        DropdownMenu(expanded = menuShown, onDismissRequest = { menuShown = false }) {
            DropdownMenuItem(
                text = { Text(Copy.EDIT, style = RibbonType.ui(16f), color = Palette.text) },
                onClick = { menuShown = false; onEdit() },
            )
            DropdownMenuItem(
                // SwiftUI's destructive role, which has no Compose
                // equivalent: the scheme's error colour is the palette's
                // deep flame, which is the one warm red the room owns.
                text = {
                    Text(
                        Copy.TAKE_BACK,
                        style = RibbonType.ui(16f),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { menuShown = false; onTakeBack() },
            )
        }
    }
}

@Composable
private fun VoiceBody(
    model: AppModel,
    note: Note,
    authorInk: Ink,
    player: VoicePlayer,
    transcriptShown: Boolean,
    onToggleTranscript: () -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WaveformView(
            peaks = note.waveform ?: emptyList(),
            ink = authorInk,
            progress = player.progress,
            onScrub = { player.scrub(it) },
            onTap = onTogglePlayback,
        )

        when (note.transcriptState) {
            TranscriptState.pending -> SmallCaps(Copy.TRANSCRIPT_COMING, size = 12f)

            TranscriptState.failed ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Copy.NO_TRANSCRIPT,
                        style = RibbonType.ui(14f),
                        color = Palette.muted,
                    )
                    // The drawn control is one line of interface text; the
                    // touch target is 44 dp regardless (§11).
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable { model.retryTranscript(note) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = Copy.TRY_AGAIN,
                            style = RibbonType.ui(14f),
                            color = Palette.text,
                        )
                    }
                }

            TranscriptState.ready, null -> {
                val transcript = note.transcript
                if (transcript != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onToggleTranscript)
                            // The fold opens on a settle; reduce motion
                            // takes the same two states with no travel
                            // between them (§11).
                            .animateContentSize(
                                animationSpec =
                                    if (reduceMotion) {
                                        snap()
                                    } else {
                                        tween(
                                            RibbonMotion.SETTLE_MS,
                                            easing = RibbonMotion.EaseOut,
                                        )
                                    },
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (transcriptShown) {
                            Text(
                                text = transcript,
                                style = RibbonType.ui(14f),
                                color = Palette.muted,
                            )
                        } else {
                            SmallCaps(Copy.TRANSCRIPT, size = 12f)
                        }
                    }
                }
            }
        }
    }
}

private fun togglePlayback(player: VoicePlayer, audioFile: File?) {
    if (player.isPlaying) {
        player.pause()
    } else if (player.progress > 0.0 && player.progress < 1.0) {
        player.resume()
    } else if (audioFile != null) {
        player.play(audioFile)
    }
}

private fun authorTranslationQuote(model: AppModel, note: Note, author: Person?): String? {
    if (author == null || author.translation == model.me?.translation) return null
    val text = model.scripture.verseText(note.verse, author.translation) ?: return null
    return "“$text”"
}

/**
 * The waveform, drawn in the author's ink, filling left-to-right as it
 * plays. No timer, no duration readout — a duration is a count and it
 * makes people self-conscious about how long they talked.
 */
@Composable
fun WaveformView(
    peaks: List<Float>,
    ink: Ink,
    progress: Double,
    onScrub: (Double) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = if (peaks.isEmpty()) EMPTY_PEAKS else peaks
    val color = ink.color
    // The gesture handler is started once and reads the current callbacks;
    // keying it on the lambdas would tear the touch stream down and rebuild
    // it on every frame of playback, which is every frame this card has.
    val scrub by rememberUpdatedState(onScrub)
    val tap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The waveform draws 34 dp tall; the strip that answers a finger
            // is 44 (§11), because a control only a stylus can hit is broken.
            .height(TOUCH_HEIGHT)
            .pointerInput(Unit) {
                // Tap to play or pause, drag to scrub — one gesture stream,
                // so a drag never also reads as a tap. Swift sets the drag's
                // minimum distance to 8 pt; the same distance is used here
                // rather than the platform slop, so the two builds let go of
                // a tap at the same moment.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = SCRUB_SLOP.toPx()
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragging &&
                            (change.position - down.position).getDistance() > slop
                        ) {
                            dragging = true
                        }
                        if (dragging) {
                            val width = max(1f, size.width.toFloat())
                            scrub((change.position.x / width).toDouble().coerceIn(0.0, 1.0))
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (!dragging) tap()
                            break
                        }
                    }
                }
            }
            // A merge root of its own, so the card's combined label does not
            // swallow the one control inside it: SwiftUI's `.combine` keeps
            // a child's actions, Compose's merge does not. The tap is
            // published as a semantics action so a screen reader plays the
            // note without ever finding the gesture.
            .semantics(mergeDescendants = true) {
                contentDescription = Copy.PLAY_THE_VOICE_NOTE
                role = Role.Button
                onClick { tap(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(WAVE_HEIGHT)) {
            val barWidth = BAR_WIDTH.toPx()
            val step = barWidth + BAR_GAP.toPx()
            val floor = BAR_MIN_HEIGHT.toPx()
            val full = BAR_FULL_HEIGHT.toPx()
            bars.forEachIndexed { index, peak ->
                val played = index.toDouble() / max(1, bars.size).toDouble() <= progress
                val barHeight = max(floor, peak * full)
                drawRoundRect(
                    color = color.copy(alpha = if (played) 1f else 0.35f),
                    topLeft = Offset(index * step, (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }
    }
}

/** A note with no stored peaks still draws a thread rather than a gap. */
private val EMPTY_PEAKS = List(40) { 0.2f }

private val BAR_WIDTH = 2.dp
private val BAR_GAP = 1.5.dp
private val BAR_MIN_HEIGHT = 3.dp
private val BAR_FULL_HEIGHT = 30.dp
private val WAVE_HEIGHT = 34.dp
private val TOUCH_HEIGHT = 44.dp
private val SCRUB_SLOP = 8.dp
