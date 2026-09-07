@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Bible
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.InkDot
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.CampfireView
import app.readribbon.services.PresentPerson
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S01 — the room. Where the app opens, and the only permanent destination.
// Its job is to show the fire, say who's here, and get you into the book in
// one tap. The only chrome is the room's name, top-left, in small caps.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an equivalent ambient value, so the model is a parameter —
// the same call every other screen in this package made. It holds Compose
// snapshot state, so reading `model.presentPeople` here recomposes exactly
// as `@Observable` does.
//
// Swift's `NavigationLink(value: PersonRoute(...))` pairs with a
// `navigationDestination` up in RibbonApp; here the destinations are
// navigation-compose routes owned by the root, so the push is a callback.
// Predictive back — the room peeling in behind a closing screen — belongs to
// that NavHost, and the room is its start destination: nothing here
// intercepts back, and the two sheets below close themselves under it.
//
// The top-level `firstName(_:)` Swift declares at the foot of this file
// already crossed with the copy: it lives beside `Copy` in Copy.kt, because
// every other screen needed it before this one did.

/**
 * The presence portraits: drawn at Swift's `size: 24`, but each one sits in
 * a 44 dp square so the touch target holds (deviation 12 — a control only a
 * stylus can hit is broken; that defect was found on iPad and it is the same
 * defect here). The row's spacing is negative by the difference, so the
 * drawn circles keep exactly the `HStack(spacing: 8)` gap: 8 − (44 − 24) =
 * −12. The square also centres its portrait, which would set the first one
 * 10 in from the leading edge, so the row's own inset is pulled back by that
 * same 10 and the circles start where Swift's `padding(.horizontal, 24)`
 * puts them.
 *
 * Two things this costs, both named rather than hidden. The squares overlap
 * by 12, so a tap in that band goes to the portrait on the right — 10 of the
 * band is empty margin and 2 of it is the left portrait's outer edge. And
 * the line is 44 tall where iOS's is 24; that is paid out of the
 * `frame(minHeight: 12)` below, which only ever mattered when the line was
 * empty anyway.
 */
private val PRESENCE_PORTRAIT = 24.dp
private val PRESENCE_TOUCH = 44.dp
private val PRESENCE_ROW_SPACING = (-12).dp
private val PRESENCE_EDGE_PULLBACK = (PRESENCE_TOUCH - PRESENCE_PORTRAIT) / 2

/** The room's own inset — Swift's `.padding(.horizontal, 24)`. */
private val GUTTER = 24.dp

/** Your own portrait, top-right: Swift's `size: 28` in its 44 dp frame. */
private val HEADER_PORTRAIT = 28.dp

/** The smallest thing a finger is allowed to have to hit (deviation 12). */
private val TOUCH = 44.dp

/**
 * S01 — the room.
 *
 * @param model the store.
 * @param room the room being shown — the root resolves `model.currentRoom`
 *   and hands it down, as Swift's `RootView` does.
 * @param chooserRequested Swift's `@Binding var chooserRequested`, hoisted:
 *   set from outside when "Start another" at a finishing should land in the
 *   chooser (S13). Compose has no two-way binding, so the room reports the
 *   request as handled through [onChooserHandled] instead of writing `false`
 *   back into the caller's state itself.
 * @param onChooserHandled the request was taken; clear it. Swift does this
 *   inside its own `onChange`.
 * @param onOpenReading open the book. The second half is where to open —
 *   nil for your own position, a verse when a waiting row named one (§6.3).
 * @param onOpenRooms the room's name, top-left: your rooms (S14).
 * @param onYou your own portrait, top-right — settings one tap from the
 *   room. Deviation 13, by the owner's call.
 * @param onOpenPerson a portrait or the last-reader line goes to that person
 *   (S12). Swift pushes a `PersonRoute`; the route type lives with the
 *   root's NavHost, so the two halves of it are passed here instead.
 * @param onOpenEmber one book on the shelf, pushed to its record (S11).
 *   Swift's `ShelfView` builds that `NavigationLink` itself; the Kotlin one
 *   takes it as a callback, so the room passes it through.
 */
@Composable
fun RoomScreen(
    model: AppModel,
    room: Room,
    chooserRequested: Boolean,
    onChooserHandled: () -> Unit,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onOpenRooms: () -> Unit,
    onYou: () -> Unit,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    onOpenEmber: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChooser by remember { mutableStateOf(false) }
    var showInviteShare by remember { mutableStateOf(false) }

    val reading: Reading? = model.openReading(room)
    val shelf: List<Reading> = model.shelf(room)

    // Swift's `.onChange(of: chooserRequested)`: take the request, clear it,
    // and put the chooser up.
    LaunchedEffect(chooserRequested) {
        if (chooserRequested) {
            onChooserHandled()
            showChooser = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The unlit room, edge to edge: the ground and its grain run
            // under the system bars and only the content clears them.
            .room(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No scroll indicators: Compose draws none, which is what
                // `.scrollIndicators(.hidden)` asks for.
                .verticalScroll(rememberScrollState()),
        ) {
            // One readable column: the phone layout, centred, instead of a
            // way-in capsule as wide as a tablet.
            Column(
                modifier = Modifier
                    .readableColumn()
                    // The extra bottom inset three-button navigation needs
                    // comes from safeDrawing itself.
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
                horizontalAlignment = Alignment.Start,
            ) {
                RoomHeader(model = model, room = room, onOpenRooms = onOpenRooms, onYou = onYou)

                PresenceLine(
                    model = model,
                    room = room,
                    onOpenPerson = onOpenPerson,
                    modifier = Modifier.padding(top = 6.dp),
                )

                FireSection(
                    model = model,
                    reading = reading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                )

                WayIn(
                    model = model,
                    room = room,
                    reading = reading,
                    onOpenReading = onOpenReading,
                    onPickABook = { showChooser = true },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                WaitingRows(
                    model = model,
                    room = room,
                    reading = reading,
                    onOpenReading = onOpenReading,
                    onSendItAgain = { showInviteShare = true },
                    modifier = Modifier.padding(top = 26.dp, start = 24.dp, end = 24.dp),
                )

                // The shelf, below the fold (S10). No shelf until the first
                // book is finished — an empty shelf is a reproach, so it is
                // absent rather than empty-stated.
                //
                // Swift hangs `.padding(.top, 44)` on the `@ViewBuilder`, and
                // a SwiftUI modifier is applied to each element of the view
                // list it wraps — which is why `wayIn` below pads both the
                // button and the pause line. An absent `if` produces an empty
                // list, so on first run there is no element to pad and no
                // space at all: the padding goes on the shelf itself, not
                // around the place it would have been.
                if (shelf.isNotEmpty()) {
                    ShelfView(
                        room = room,
                        readings = shelf,
                        onStartAnother = { showChooser = true },
                        onOpenEmber = onOpenEmber,
                        modifier = Modifier.padding(top = 44.dp),
                    )
                }

                QuietDayFoot(
                    model = model,
                    room = room,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp, bottom = 40.dp),
                )
            }
        }
    }

    if (showChooser) {
        BookChooserSheet(
            room = room,
            model = model,
            onDismiss = { showChooser = false },
            onChoose = { bookID ->
                showChooser = false
                val started = model.startReading(bookID = bookID, room = room)
                onOpenReading(started, null)
            },
        )
    }

    if (showInviteShare) {
        // Swift asks for `.presentationDetents([.medium])`; the Kotlin
        // `InviteSheet` holds that height itself.
        InviteSheet(room = room, model = model, onDismiss = { showInviteShare = false })
    }
}

// MARK: Header — the entire navigation bar

@Composable
private fun RoomHeader(
    model: AppModel,
    room: Room,
    onOpenRooms: () -> Unit,
    onYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minHeight = TOUCH)
                // Swift's `.accessibilityHint("Opens your rooms")`. Compose
                // has no hint field; the click action's own label is the
                // nearest honest thing, and TalkBack reads it as "double tap
                // to open your rooms" — the same fact, said as the action
                // rather than as its consequence.
                .clickable(
                    role = Role.Button,
                    onClickLabel = Copy.OPEN_YOUR_ROOMS,
                    onClick = onOpenRooms,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            SmallCaps(model.displayName(room), size = 14f)
        }

        Spacer(Modifier.weight(1f))

        // Your own portrait, top-right — settings one tap away, from
        // anywhere the room is. (A departure from S18's two-taps-deep;
        // written down in docs/deviations.md.)
        Box(
            modifier = Modifier
                .size(TOUCH)
                .clickable(
                    role = Role.Button,
                    onClickLabel = Copy.OPEN_YOUR_ACCOUNT,
                    onClick = onYou,
                )
                // Swift's `.accessibilityLabel(Copy.you)`, which replaces the
                // label rather than adding to it: the control is the way to
                // your account, so the portrait's own name is cleared
                // beneath it.
                .semantics { contentDescription = Copy.YOU },
            contentAlignment = Alignment.CenterEnd,
        ) {
            PortraitView(
                person = model.me,
                ink = null,
                size = HEADER_PORTRAIT,
                image = model.me?.let { model.portrait(it.id) },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

// MARK: Presence line

@Composable
private fun PresenceLine(
    model: AppModel,
    room: Room,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val present: List<PresentPerson> =
        if (room.isPaused) emptyList() else model.presentPeople
    val lastReader = if (present.isEmpty()) model.lastReader(room) else null

    Row(
        modifier = modifier
            // The 44 dp square centres its 24 dp portrait, so the leading
            // inset is pulled back by that half-difference while portraits
            // are showing. The last-reader line has no square around it and
            // keeps the plain gutter.
            .padding(
                start = if (present.isEmpty()) GUTTER else GUTTER - PRESENCE_EDGE_PULLBACK,
                end = GUTTER,
            )
            .heightIn(min = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(PRESENCE_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (present.isNotEmpty()) {
            // Tap a portrait → S12. Rows of faces, never "4 people here".
            present.take(6).forEach { person ->
                Box(
                    modifier = Modifier
                        .size(PRESENCE_TOUCH)
                        .clickable(role = Role.Button) { onOpenPerson(person.id, room.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    // The portrait keeps its own label: in a gutter or a
                    // presence line a person's identity is carried by the
                    // accessibility label, never by colour alone (§11).
                    PortraitView(
                        person = model.person(person.id),
                        ink = model.membership(personID = person.id, roomID = room.id)?.ink,
                        size = PRESENCE_PORTRAIT,
                        image = model.portrait(person.id),
                    )
                }
            }
        } else if (lastReader != null) {
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = TOUCH)
                    .clickable(role = Role.Button) { onOpenPerson(lastReader.personID, room.id) },
                contentAlignment = Alignment.CenterStart,
            ) {
                SmallCaps(lastReader.line, size = 12f)
            }
        }
        // Alone: silence. Never a line about being alone (§08).
    }
}

// MARK: The fire

@Composable
private fun FireSection(
    model: AppModel,
    reading: Reading?,
    modifier: Modifier = Modifier,
) {
    val book = reading?.let { Bible.book(it.bookID) }
    if (reading != null && book != null) {
        val state = model.fireState(reading)
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Deliberately inert: it is an object, not a button. Nothing
            // here makes it clickable, and it speaks for itself — the
            // campfire carries its own "The fire is steady." (§11).
            CampfireView(
                state = state,
                scale = reading.handiwork.scale,
                coalDepth = reading.handiwork.coalDepth,
            )
            Text(
                text = book.name,
                style = RibbonType.display(26f),
                color = Palette.text,
            )
            SmallCaps(state.displayName, size = 13f)
        }
    } else {
        // First run: the fire's place holds nothing; in its place, the
        // chooser. The shelf is absent, not empty-stated.
        Column(
            modifier = modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = Copy.PICK_A_BOOK,
                style = RibbonType.display(22f),
                color = Palette.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// MARK: The way in

/**
 * Scripture is never locked (§2.5): a paused room keeps its way in — the
 * pause line is added, the waiting rows go.
 *
 * Swift applies `.padding(.top, 26)` to the whole `@ViewBuilder` group,
 * which means to each view the group produces — so the button carries 26 and
 * the pause line carries 26 of its own before its own 14. That is why the
 * caller passes only the horizontal inset and the tops are set here.
 */
@Composable
private fun WayIn(
    model: AppModel,
    room: Room,
    reading: Reading?,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onPickABook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = reading?.let { Bible.book(it.bookID) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (reading != null && book != null) {
            // Have you been in this book yet? A yes or a no — never how far.
            val hasRead = model.state.positions.any {
                it.readingID == reading.id && it.personID == model.me?.id
            }
            WayInButton(
                title = if (hasRead) Copy.continueIn(book.name) else Copy.begin(book.name),
                modifier = Modifier.padding(top = 26.dp),
            ) {
                onOpenReading(reading, null)
            }
            if (room.isPaused) {
                PausedLine(Modifier.padding(top = 26.dp + 14.dp))
            }
        } else if (room.isPaused) {
            PausedLine(Modifier.padding(top = 26.dp))
        } else {
            WayInButton(
                title = Copy.PICK_A_BOOK,
                modifier = Modifier.padding(top = 26.dp),
                onClick = onPickABook,
            )
        }
    }
}

/**
 * The pause, said once and quietly.
 *
 * SwiftUI's `.frame(maxWidth: .infinity)` centres the line in the column
 * while leaving its wrapped lines leading-aligned; Compose centres both.
 * The line is centred here, under a centred way in, because that is what the
 * sentence looks like on the phone the layout was drawn for.
 */
@Composable
private fun PausedLine(modifier: Modifier = Modifier) {
    Text(
        text = Copy.ROOM_PAUSED,
        style = RibbonType.ui(15f),
        color = Palette.muted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

// MARK: What's waiting — rows, never a count, never a badge

@Composable
private fun WaitingRows(
    model: AppModel,
    room: Room,
    reading: Reading?,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onSendItAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Paused: waiting rows gone (S01) — the pause line stands alone.
    val waiting: List<Note> = if (room.isPaused) emptyList() else model.waitingNotes(room)
    // Counted here and nowhere else: the number decides whether the room is
    // still a room of one, and it never reaches the interface (Law 2).
    val memberCount = model.members(room).size

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        waiting.take(4).forEach { note ->
            val author = model.person(note.authorID)
            if (author != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The drawn row is shorter than a finger; the target
                        // is not (deviation 12).
                        .sizeIn(minHeight = TOUCH)
                        .clickable(role = Role.Button) {
                            // Jumps to that note (§6.3): the reading opens at
                            // its verse, the mark breathing.
                            if (reading != null) onOpenReading(reading, note.verse)
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkDot(
                        ink = model.membership(personID = note.authorID, roomID = room.id)?.ink
                            ?: Ink.clay,
                    )
                    Text(
                        text = Copy.leftYouANote(firstName(author.name), note.verse.formatted),
                        style = RibbonType.ui(15f),
                        color = Palette.text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Room of one, invite still out — the state, not the person.
        if (memberCount == 1 && !room.isPaused) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCaps(Copy.INVITE_STILL_OUT, size = 12f)
                QuietControl(title = Copy.SEND_IT_AGAIN, onClick = onSendItAgain)
            }
        }
    }
}

// MARK: Mark a quiet day

/**
 * Always present, never emphasised. The room sees who banked the fire
 * (§4.7): an act of care, performed in public, above the control rather than
 * in place of it.
 */
@Composable
private fun QuietDayFoot(
    model: AppModel,
    room: Room,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val settle: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)
    val settleSize: FiniteAnimationSpec<IntSize> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    val bankedBy = model.activeQuietDay(room)?.let { model.person(it.personID)?.name }
    // Held across the leaving animation: `AnimatedVisibility` recomposes its
    // content while it goes, and by then the quiet day is over and the name
    // is gone. The live value still wins, so the line is never a frame stale
    // on the way in.
    var lastBanked by remember { mutableStateOf(bankedBy) }
    LaunchedEffect(bankedBy) { if (bankedBy != null) lastBanked = bankedBy }
    val bankedName = bankedBy ?: lastBanked

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Swift wraps `model.markQuietDay(in:)` in `withAnimation(.settle)`;
        // in Compose the animation belongs to the thing that appears.
        AnimatedVisibility(
            visible = bankedBy != null,
            enter = fadeIn(settle) + expandVertically(settleSize),
            exit = fadeOut(settle) + shrinkVertically(settleSize),
            label = "banked-the-fire",
        ) {
            if (bankedName != null) {
                SmallCaps(Copy.bankedTheFire(firstName(bankedName)), size = 12f)
            }
        }
        QuietControl(title = Copy.MARK_A_QUIET_DAY) { model.markQuietDay(room) }
    }
}
