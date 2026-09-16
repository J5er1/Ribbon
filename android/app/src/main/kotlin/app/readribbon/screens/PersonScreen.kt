@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.HairlineRule
import app.readribbon.design.Air
import app.readribbon.design.InkDot
import app.readribbon.design.NoteMark
import app.readribbon.design.BackChevron
import app.readribbon.design.Flows
import app.readribbon.design.Palette
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonScreen
import app.readribbon.design.Seam
import app.readribbon.design.SectionLabel
import app.readribbon.design.flows
import app.readribbon.design.paper
import app.readribbon.design.pressable
import app.readribbon.design.pressablePaper
import app.readribbon.design.RibbonMotion
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.TextInset
import app.readribbon.design.color
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.rememberSheetExit
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

/** The portrait, under the screen's own title rather than instead of it. */
private val PortraitSize: Dp = 96.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the words it
 * draws; pulling it back by the same 8 dp puts the words themselves on the
 * page's margin. The menu's quiet controls make the same correction.
 */
private val QuietControlInset: Dp = (-8).dp

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

    // RibbonScreen, like every other pushed screen (deviations A23, A29).
    //
    // This was the last screen in the app still standing on its own page
    // furniture, and A22a is why: that pass gave it a head, tiles and a way
    // back *before* RibbonScreen existed, and nothing came back for it. The
    // difference was not cosmetic. Its back chevron lived inside the scrolling
    // content, so a person with more than a screenful of notes scrolled the
    // only tap route back off the top of the screen (§11's motor rule, with
    // the gesture as the sole survivor); the name was a centred line rather
    // than the screen's heading, so a screen reader got no heading node for
    // the thing the screen is about; and the whole page was centre-aligned and
    // 24 dp wide where every other pushed screen is start-aligned on
    // `ScreenMargin`.
    //
    // Nothing in S12 asked for any of that. Its anatomy is a portrait, a name,
    // an ink and a list, and a collapsing title holds all four better than a
    // centred column does.
    //
    // Two deliberate differences from the settings screens, both recorded in
    // A33a. **No lede**: the one slot on this screen that invites a sentence
    // about a person is exactly where a join date or a "last seen" would
    // arrive, and S12's anatomy ends "nothing else". **The face is on the bare
    // ground**, where S18's identity tile puts its face on paper — here the
    // face is the subject of the screen, there it is a control beside another
    // control, and a tile is what a row of controls is for.
    RibbonScreen(
        // Never blank: a seat can be tapped before its profile has synced,
        // and a screen with no name on it reads as a failure rather than a
        // wait.
        title = person?.name ?: Copy.SOMEONE,
        onBack = onDismiss,
        modifier = modifier,
    ) {
        PortraitView(
            person = person,
            ink = membership?.ink,
            // Smaller than the 108 dp it was: it no longer carries the top of
            // the screen on its own, because the title does.
            size = PortraitSize,
            image = model.portrait(personID),
            // The same face that was tapped in the room's seats, or on a room
            // row in the menu: it travels here and grows.
            modifier = Modifier.flows(Flows.seat(room.id, personID)),
        )

        val ink = membership?.ink
        if (ink != null) {
            Air(14.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // A 6 dp dot has no semantics of its own, so without this
                // TalkBack read the single word "Crimson" floating between a
                // name and a list — and §11 is explicit that identity carried
                // by an ink cannot rest on the colour.
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = Copy.inkSpoken(yours = isMe, ink = ink.displayName)
                },
            ) {
                InkDot(ink = ink)
                SmallCaps(ink.displayName, size = 12f)
            }
            // Directly under the ink it changes, rather than at the foot of
            // the page. It used to share a stack with "Leave this room" below
            // every note in the room, which put a screenful between a colour
            // and the control for it and made changing a colour look like the
            // same class of act as leaving.
            if (isMe && model.inkIsIdentity(room)) {
                QuietControl(
                    title = Copy.CHANGE_YOUR_INK,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) { showInkPicker = true }
            }
        }

        Air(28.dp)

        // What they've left in this room, in verse order.
        if (theirNotes.isEmpty()) {
            // No head and no tile. The head reads "What Ruth left", and over
            // nothing that is a head naming the person who has not done the
            // thing (§10.1); a drawn container announcing an absence is §4.2's
            // placeholder mistake. One impersonal line instead, true of your
            // own screen as well as theirs.
            Text(
                text = Copy.NOTHING_LEFT_HERE_YET,
                style = RibbonType.ui(15f),
                color = Palette.muted,
            )
        } else {
            SectionLabel(
                if (isMe) {
                    Copy.WHAT_YOU_LEFT
                } else {
                    Copy.whatTheyLeft(firstName(person?.name ?: "").ifBlank { Copy.SOMEONE })
                },
            )
            Air(10.dp)
            // One group, not a stack of loose tiles. Every row took the same
            // `rowShape` before, so eight notes were eight identically-rounded
            // receipts rather than one record with the group's own outer
            // corners. The shapes are computed here rather than through
            // `GroupScope` because `slot()` is private to Surfaces.kt and a
            // custom row cannot reach it — which is also why this is not
            // simply a `SettingsGroup`.
            Column(verticalArrangement = Arrangement.spacedBy(Seam)) {
                theirNotes.forEachIndexed { index, note ->
                    PersonNoteRow(
                        note = note,
                        ink = membership?.ink ?: Ink.clay,
                        mine = isMe,
                        // A note's own words are shown here only once it has
                        // been found in the margin, or if it is yours. §6.3's
                        // whole beat is being found later, and a list that
                        // reads every unfound note aloud would spend it before
                        // anybody opened the book. An unfound one gives its
                        // address, which is an invitation to go.
                        found = isMe || note.foundBy.contains(model.me?.id),
                        shape = RibbonShape.inGroup(index, theirNotes.size),
                        onOpen = { onOpenVerse(note.verse, note.readingID) },
                    )
                }
            }
        }

        if (isMe) {
            Air(36.dp)
            // The one undoing control, alone on the bare ground, which is
            // where every undoing control in the app stands (A23).
            QuietControl(
                title = Copy.LEAVE_THIS_ROOM,
                modifier = Modifier.offset(x = QuietControlInset),
            ) { confirmLeave = true }
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
 * One note they left: its mark, its address, and — once it has been found —
 * what it actually says. Tapping it opens the verse where it lives.
 *
 * It was a mark and an address on a bare ground, which made this screen a
 * column of references to things you could not read. On a tile, with the
 * note's own words or its transcript under the address, it is a record you
 * would come back to.
 *
 * The mark is drawn found and never pending: this is a record of what is
 * already here, not the margin (§4.4).
 */
@Composable
private fun PersonNoteRow(
    note: Note,
    ink: Ink,
    mine: Boolean,
    found: Boolean,
    shape: Shape,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A voice note's transcript is the thing to show: transcripts are how the
    // deaf read this app and how anyone finds a note again in six months
    // (§11), and no duration is ever displayed (S04).
    val words = if (found) (note.body ?: note.transcript)?.takeIf { it.isNotBlank() } else null

    // The kind is drawn — a filled dot against an open ring — and `NoteMark`
    // is a bare canvas with no semantics, so a voice note's transcript and a
    // written note's body announced identically. The room's waiting rows were
    // given the same fix; this is the other place that had it wrong.
    // An unfound note and a found one with nothing to show rendered
    // identically — an address and no more — so neither the eye nor a screen
    // reader could tell "go and find this" from "this one has no words".
    // §11 specifies the clause for a gutter mark and `Copy.marginNotes`
    // already carries it; this was the one place a note's found state was
    // spoken nowhere.
    val waiting = !found

    val spoken = buildString {
        append(
            if (note.kind == NoteKind.voice) {
                Copy.aVoiceNoteAt(note.verse.formatted)
            } else {
                Copy.aNoteAt(note.verse.formatted)
            },
        )
        if (words != null) append(". $words")
        if (waiting) append(", ${Copy.NOT_YET_FOUND}")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MIN_TARGET + 8.dp)
            .pressablePaper(shape, role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            // The group's own inset, so these words share an optical edge
            // with the head above them — which is the one thing that head is
            // for, and which 16 dp against SectionLabel's 20 missed by four.
            .padding(horizontal = TextInset, vertical = 12.dp),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SmallCaps(
                note.verse.formatted,
                size = 12f,
                color = Palette.text.copy(alpha = 0.8f),
            )
            if (words != null) {
                Text(
                    text = words,
                    style = RibbonType.ui(15f),
                    color = Palette.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (waiting) {
                // A state, in the running-head voice, rather than a sentence:
                // the note is not saying this, the row is. A found note with
                // no transcript keeps the address alone — the note's own view
                // owns "No transcript" and "Try again", and this row is a
                // record rather than a repair site.
                SmallCaps(Copy.NOT_YET_FOUND, size = 12f)
            }
        }
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
 *   equivalent. The palette's deep flame, read directly rather than through
 *   Material's `error` role: a wallpaper's scheme does not set `error`, so
 *   under Material You that role is Material's own pink. The one warm red the
 *   room owns is the fire's, and the fire never follows the wallpaper.
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
            color = if (destructive) Palette.flameDeep else Palette.text,
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
    // Picking an ink closes the sheet; it should slide away with the choice
    // made rather than blink out from under the finger that made it.
    val leave = rememberSheetExit(sheetState)

    ModalBottomSheet(
        // Already animated away by the sheet itself: the person dismissed it.
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
                                leave(onDismiss)
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
    // The ring is the answer to the tap, and the sheet slides away a moment
    // later — so the ring has to be *seen* arriving, not merely be there in the
    // frame before the slide. Eased in on the arrive token, which is long
    // enough to read and short enough to finish before the sheet goes.
    val ringed by animateFloatAsState(
        targetValue = if (isMine) 1f else 0f,
        animationSpec = RibbonMotion.arrive(rememberReduceMotion()),
        label = "your-ink",
    )

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
        // Read out here: a draw lambda is not a composition, and the room's
        // ink is a composition local now.
        val ivory = Palette.text
        Canvas(Modifier.size(SWATCH_TARGET)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = ink.color.copy(alpha = if (isTaken) 0.2f else 1f),
                radius = SWATCH_DIAMETER.toPx() / 2f,
                center = centre,
            )
            if (ringed > 0f) {
                // Swift's `.padding(-4)` on a stroked border: the ring sits
                // 4 pt outside the swatch, and `strokeBorder` draws inside
                // that edge rather than centred on it.
                val stroke = 1.6.dp.toPx()
                drawCircle(
                    color = ivory.copy(alpha = 0.8f * ringed),
                    // Closing on the swatch as it fades in, so the ring reads
                    // as something settling around the ink rather than as a
                    // second circle switched on beside it.
                    radius = SWATCH_DIAMETER.toPx() / 2f +
                        (4.dp.toPx() + (1f - ringed) * 5.dp.toPx()) - stroke / 2f,
                    center = centre,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}
