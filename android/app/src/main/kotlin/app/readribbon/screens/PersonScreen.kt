@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.HairlineRule
import app.readribbon.design.InkDot
import app.readribbon.design.NoteMark
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.readableColumn
import app.readribbon.design.room
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S12 — a person: someone in this room. Not a profile — there is nothing to
// follow, nothing to score. No join date, no activity, no counts of
// anything they've done.

/** The drawn ink swatch in the picker. */
private val SWATCH_DIAMETER: Dp = 30.dp

/**
 * The width each swatch is tapped by. Swift draws 30 pt and widens the hit
 * shape to ~44 pt with `contentShape(Rectangle().inset(by: -7))`, keeping
 * the 16 pt gaps it lays out with; Compose has no hit-shape-only inset, so
 * the gap is folded into the target instead — eight 44 dp columns come to
 * exactly the 352 dp the Swift row occupies, and the drawn circles keep a
 * 14 dp gap rather than 16. Widening the frames and keeping the gaps would
 * overflow a 360 dp phone, which is the same arithmetic Swift's comment
 * does for 375 pt.
 */
private val SWATCH_TARGET: Dp = 44.dp

/** The smallest a row may be tapped at (§11, deviation 12). */
private val MIN_TARGET: Dp = 44.dp

/**
 * A person (S12): their face, their name, their ink, and what they have left
 * in this room. Your own adds the two things only you can do — change your
 * ink, and leave.
 *
 * @param onOpenVerse the verse and the reading it lives in — a finished
 *   book's note opens that book (S12), not the open one.
 * @param onDismiss Swift's `@Environment(\.dismiss)`: leaving the room pops
 *   this screen, because the room it belongs to is gone.
 */
@Composable
fun PersonScreen(
    model: AppModel,
    personID: Uuid,
    room: Room,
    onOpenVerse: (VerseAddress, Uuid) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLeave by remember { mutableStateOf(false) }
    var showInkPicker by remember { mutableStateOf(false) }

    val person = model.person(personID)
    val isMe = personID == model.me?.id
    val membership = model.membership(personID = personID, roomID = room.id)

    // What they've left in this room — the whole room, not only the open
    // reading — in verse order (S12).
    val readingIDs = model.state.readings.filter { it.roomID == room.id }.map { it.id }.toSet()
    val theirNotes: List<Note> = model.state.notes
        .filter { readingIDs.contains(it.readingID) && it.authorID == personID }
        .sortedBy { it.verse }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Edge to edge: the ground and its grain run under the system
            // bars, and only the content clears them. `.room()` hides its own
            // node from accessibility — the paper is texture, not information
            // — so it is painted by this box rather than by the scroll.
            .room(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No scroll indicators: Compose draws none, which is what
                // `.scrollIndicators(.hidden)` asks for.
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .readableColumn()
                    // The extra bottom inset three-button navigation needs
                    // comes from safeDrawing itself.
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PortraitView(
                    person = person,
                    ink = membership?.ink,
                    size = 108.dp,
                    image = model.portrait(personID),
                    modifier = Modifier.padding(top = 40.dp),
                )
                Text(
                    text = person?.name ?: "",
                    style = RibbonType.display(26f),
                    color = Palette.text,
                )
                val ink = membership?.ink
                if (ink != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkDot(ink = ink)
                        SmallCaps(ink.displayName, size = 12f)
                    }
                }

                // What they've left in this room, in verse order.
                if (theirNotes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp)
                            .padding(top = 20.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        // Swift's 12 pt VStack spacing is folded into each
                        // row's own 44 dp minimum rather than sitting as dead
                        // space between two targets a finger can miss — the
                        // same call the highlight bar's ink columns make.
                        theirNotes.forEach { note ->
                            PersonNoteRow(
                                note = note,
                                ink = membership?.ink ?: Ink.clay,
                                mine = isMe,
                                onOpen = { onOpenVerse(note.verse, note.readingID) },
                            )
                        }
                    }
                }

                if (isMe) {
                    Column(
                        modifier = Modifier.padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (model.inkIsIdentity(room)) {
                            QuietControl(title = Copy.CHANGE_YOUR_INK) { showInkPicker = true }
                        }
                        QuietControl(title = Copy.LEAVE_THIS_ROOM) { confirmLeave = true }
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }

    if (confirmLeave) {
        LeaveRoomDialogs(
            model = model,
            room = room,
            onDismiss = { confirmLeave = false },
            onLeft = {
                confirmLeave = false
                onDismiss()
            },
        )
    }

    if (showInkPicker) {
        InkPickerSheet(model = model, room = room, onDismiss = { showInkPicker = false })
    }
}

/**
 * One note they left, as a mark and an address. Tapping it opens the verse
 * where it lives.
 *
 * The mark is drawn found and never pending: this is a record of what is
 * already here, not the margin (§4.4).
 */
@Composable
private fun PersonNoteRow(
    note: Note,
    ink: Ink,
    mine: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MIN_TARGET)
            .clickable(role = Role.Button, onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoteMark(
            kind = note.kind,
            ink = ink,
            found = true,
            mine = mine,
            pending = false,
        )
        SmallCaps(
            note.verse.formatted,
            size = 12f,
            color = Palette.text.copy(alpha = 0.8f),
        )
    }
}

/**
 * Leaving (§6.8): asked once, plainly, with no guilt, and then the one
 * question that follows it — notes default to staying, because they were
 * left for the other person.
 *
 * Shown as a pair of confirmations because that is what the Swift is: two
 * `confirmationDialog`s, the second raised by the first. The second step
 * lives here rather than in the caller so the whole way out is one thing to
 * present; `You`'s room section asks exactly the same two questions.
 *
 * @param onDismiss the flow was backed out of — nothing happened.
 * @param onLeft the room was left; whoever presented this has a room fewer.
 */
@Composable
fun LeaveRoomDialogs(
    model: AppModel,
    room: Room,
    onDismiss: () -> Unit,
    onLeft: () -> Unit,
) {
    var askAboutNotes by remember { mutableStateOf(false) }

    fun leave(keepNotes: Boolean) {
        model.leaveRoom(room, keepNotesBehind = keepNotes)
        onLeft()
    }

    if (!askAboutNotes) {
        RibbonConfirmDialog(question = Copy.LEAVE_ROOM_CONFIRM, onDismiss = onDismiss) {
            ConfirmChoice(
                title = Copy.LEAVE_THIS_ROOM,
                destructive = true,
                onClick = { askAboutNotes = true },
            )
            ConfirmChoice(title = Copy.STAY, onClick = onDismiss)
        }
    } else {
        RibbonConfirmDialog(question = Copy.LEAVE_NOTES_QUESTION, onDismiss = onDismiss) {
            // Leaving them is the default; taking them back is possible and
            // never the default (§6.8).
            ConfirmChoice(title = Copy.LEAVE_THEM, onClick = { leave(keepNotes = true) })
            ConfirmChoice(title = Copy.TAKE_THEM_BACK, onClick = { leave(keepNotes = false) })
            ConfirmChoice(title = Copy.STAY, onClick = onDismiss)
        }
    }
}

/**
 * What stands in for SwiftUI's `confirmationDialog`: the question, visible,
 * and the choices under it.
 *
 * Compose has no action sheet, and Material's `AlertDialog` brings a
 * container colour, a shape and rippling text buttons that belong to another
 * app; `BasicAlertDialog` brings only the scrim and the back handling, which
 * is the part worth having. Back — including a predictive back gesture — and
 * a tap outside both dismiss, and the question always carries an explicit way
 * out besides, because iOS supplies one for free and a question with no way
 * to say no is not asked plainly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RibbonConfirmDialog(
    question: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    choices: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Box(Modifier.clip(RoundedCornerShape(20.dp))) {
            // The ground and its grain sit behind the content rather than
            // under it, because `.room()` hides its node from accessibility
            // and a dialog that hid itself would take the question with it.
            Box(Modifier.matchParentSize().room())
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = question,
                    style = RibbonType.ui(15f),
                    color = Palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                )
                HairlineRule()
                choices()
            }
        }
    }
}

/**
 * One answer to a [RibbonConfirmDialog]'s question. A control says exactly
 * what happens, so the choices are the verbs and never "OK".
 *
 * @param destructive SwiftUI's destructive role, which has no Compose
 *   equivalent: the scheme's error colour is the palette's deep flame, which
 *   is the one warm red the room owns.
 */
@Composable
fun ConfirmChoice(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MIN_TARGET)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = RibbonType.ui(17f),
            color = if (destructive) MaterialTheme.colorScheme.error else Palette.text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Picking an ink when color is identity (§4.5, §6.7) — an invitation, not
 * an interruption.
 *
 * Swift asks for a 220 pt detent and `.presentationSizing(.fitted)` so an
 * iPad does not float eight swatches at the top of a vast form sheet; a
 * modal bottom sheet is already the height of what it holds, on every size
 * of screen, so there is nothing to pin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkPickerSheet(
    model: AppModel,
    room: Room,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        // `.presentationBackground(Palette.ground)`. The grain goes on top of
        // it, inside the content, exactly as it does in a room.
        containerColor = Palette.ground,
        contentColor = Palette.text,
        // Swift shows no grabber: `.presentationDragIndicator` is
        // `.automatic`, and automatic draws nothing for a sheet with a single
        // detent. The whole sheet still drags, and predictive back closes it.
        dragHandle = null,
        // The swatches carry their own clearance from the navigation bar,
        // below the row rather than around the sheet.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().room())
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                SmallCaps(
                    Copy.YOUR_INK,
                    size = 13f,
                    modifier = Modifier.padding(top = 30.dp),
                )
                val taken = model.members(room).mapNotNull { it.ink }
                val mine = model.myMembership(room)?.ink
                Row(
                    modifier = Modifier.padding(
                        bottom = 34.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ink.entries.forEach { ink ->
                        val isTaken = taken.contains(ink) && ink != mine
                        InkPickerSwatch(
                            ink = ink,
                            isMine = ink == mine,
                            isTaken = isTaken,
                        ) {
                            if (!isTaken) {
                                model.pickInk(ink, room)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One ink to choose: 30 dp drawn, 44 dp tappable, ringed when it is already
 * yours and washed out when it is already someone else's. Which someone is
 * never said — an ink that is taken is only taken.
 */
@Composable
private fun InkPickerSwatch(
    ink: Ink,
    isMine: Boolean,
    isTaken: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(SWATCH_TARGET)
            .clickable(enabled = !isTaken, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = Copy.inkSwatchSpoken(
                    name = ink.displayName,
                    yours = isMine,
                    taken = isTaken,
                )
                if (isTaken) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SWATCH_TARGET)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = ink.color.copy(alpha = if (isTaken) 0.2f else 1f),
                radius = SWATCH_DIAMETER.toPx() / 2f,
                center = centre,
            )
            if (isMine) {
                // Swift's `.padding(-4)` on a stroked border: the ring sits
                // 4 pt outside the swatch, and `strokeBorder` draws inside
                // that edge rather than centred on it.
                val stroke = 1.6.dp.toPx()
                drawCircle(
                    color = Palette.text.copy(alpha = 0.8f),
                    radius = SWATCH_DIAMETER.toPx() / 2f + 4.dp.toPx() - stroke / 2f,
                    center = centre,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}
