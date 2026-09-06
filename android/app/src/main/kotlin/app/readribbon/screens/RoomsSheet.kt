@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Room
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.room
import app.readribbon.fire.CampfireGlyph
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S14 — rooms: switching between rooms, and making a new one. One row per
// room: its name, its members' portraits, and its fire drawn small in its
// current state. "You" at the bottom is how settings is reached.
//
// Rooms never interact; nothing crosses between them (§2.4). This sheet is
// the only place they are ever seen at once, and even here every row reads
// its own room and nothing else — no shared totals, no ordering by activity.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it
// is on every other screen here. It holds Compose snapshot state, so reading
// `model.state.rooms` recomposes exactly as `@Observable` does.

/** Horizontal margin of the sheet — Swift's `.padding(.horizontal, 22)`. */
private val SheetMargin = 22.dp

/** `.padding(.top, 26)` above the first room. */
private val ListTop = 26.dp

/** `VStack(alignment: .leading, spacing: 6)` between rooms. */
private val RowSpacing = 6.dp

/** The foot's own `.padding(22)`, and its `spacing: 18`. */
private val FootPadding = 22.dp
private val FootSpacing = 18.dp

/**
 * The chartreuse hairline that marks the current room: Swift's
 * `Rectangle().frame(width: 2, height: 34)`.
 */
private val MarkWidth = 2.dp
private val MarkHeight = 34.dp

/** `HStack(spacing: 12)` across a room row, `spacing: 3` down its titles. */
private val RowSpan = 12.dp
private val TitleSpacing = 3.dp

/** `.padding(.vertical, 8)` around a room row. */
private val RowPadding = 8.dp

/**
 * The members' portraits, overlapped: Swift's `HStack(spacing: -5)` on 18 pt
 * portraits. They are not targets of their own here — the whole row is one
 * button — so the marks stay 18 dp and only the row carries the 44 dp
 * minimum.
 */
private val PortraitSize = 18.dp
private val PortraitOverlap = (-5).dp

/** Every row is a target, so no row is shorter than a finger (deviation 12). */
private val RowMinHeight = 44.dp

/** `spacing: 10` between your portrait and the word "You". */
private val YouSpan = 10.dp
private val YouPortrait = 26.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the glyph;
 * Swift grows the target outward instead (`.contentShape(Rectangle().inset(
 * by: -8))`) and leaves the words where they were. The control is pulled
 * back by that same 8 dp so "Start a room" begins on the sheet's margin,
 * flush with "You" beneath it, exactly as it does on iOS.
 */
private val QuietControlInset = (-8).dp

/**
 * The rooms sheet (S14).
 *
 * Swift's `.presentationDetents([.medium, .large])` is Compose's default
 * sheet state: a partial detent and an expanded one. The difference is where
 * the partial stop lands — iOS puts `.medium` at half the screen whatever
 * the content is, while a Material sheet stops at half the screen only when
 * the content is taller than that and otherwise sits at its own height. A
 * room list of two or three rooms therefore opens shorter here than on iOS.
 * Nothing is lost — the same rows, the same foot, the same drag to full
 * height.
 *
 * `.presentationBackground(Palette.ground)` is the sheet's container colour;
 * the grain goes on top of it, inside the content, as it does in a room.
 * Material's drag handle stays: Swift's drag indicator is `.automatic`, and
 * automatic draws a grabber for a sheet with more than one detent.
 *
 * @param model the store.
 * @param onSwitch make this room the current one — Swift's `onSwitch`.
 * @param onStartRoom start a new room (S15).
 * @param onYou open "You", which is how settings is reached (S17).
 * @param onDismiss close the sheet. Swift's `@Environment(\.dismiss)`, which
 *   the row calls for itself after switching; starting a room and opening
 *   "You" are left to the caller to close, exactly as they are there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsSheet(
    model: AppModel,
    onSwitch: (Uuid) -> Unit,
    onStartRoom: () -> Unit,
    onYou: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Palette.ground,
        contentColor = Palette.text,
        // The foot keeps its own clearance from the system bars, so the
        // rooms can scroll behind a three-button bar rather than be cut
        // short above it.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        RoomsContent(
            model = model,
            onSwitch = onSwitch,
            onStartRoom = onStartRoom,
            onYou = onYou,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The sheet's body without the sheet around it: the rooms, and the foot.
 *
 * Split out from [RoomsSheet] for the same reason the chooser is — so the
 * list can be shown somewhere that is already a sheet — and so the sheet
 * itself stays nothing but presentation.
 */
@Composable
fun RoomsContent(
    model: AppModel,
    onSwitch: (Uuid) -> Unit,
    onStartRoom: () -> Unit,
    onYou: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Three-button navigation eats the bottom of the sheet, so its inset is
    // added under the foot.
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxWidth()) {
        // `.room()` — the ground and its grain — is painted by a box behind
        // the content rather than by the scroll itself: the modifier hides
        // its node from accessibility (the paper is texture, not
        // information), and a scroll that hid itself would take the rooms
        // with it.
        Box(Modifier.matchParentSize().room())

        Column(Modifier.fillMaxWidth()) {
            LazyColumn(
                // `fill = false` so a short list leaves the sheet short
                // rather than stretching a handful of rooms down a screen.
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(
                    start = SheetMargin,
                    end = SheetMargin,
                    top = ListTop,
                ),
                verticalArrangement = Arrangement.spacedBy(RowSpacing),
            ) {
                // `.scrollIndicators(.hidden)`: a Compose scroll draws no
                // indicator of its own, so there is nothing to hide.
                items(model.state.rooms, key = { it.id.toString() }) { room ->
                    RoomRow(
                        room = room,
                        model = model,
                        onSwitch = onSwitch,
                        onDismiss = onDismiss,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FootPadding,
                        end = FootPadding,
                        top = FootPadding,
                        bottom = FootPadding + bottomBar,
                    ),
                verticalArrangement = Arrangement.spacedBy(FootSpacing),
            ) {
                QuietControl(
                    title = Copy.START_A_ROOM_CONTROL,
                    onClick = onStartRoom,
                    modifier = Modifier.offset(x = QuietControlInset),
                )

                // "You" — your own face, and the way settings is reached
                // (S17). Not a row of options, not a gear: the person.
                Row(
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onYou)
                        .sizeIn(minHeight = RowMinHeight),
                    horizontalArrangement = Arrangement.spacedBy(YouSpan),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PortraitView(
                        person = model.me,
                        ink = null,
                        size = YouPortrait,
                        image = model.me?.let { model.portrait(it.id) },
                    )
                    SmallCaps(Copy.YOU, size = 13f, color = Palette.text.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/**
 * One room: its name, who is in it, and its fire.
 *
 * Tapping it switches and closes — Swift's `onSwitch(room.id)` then
 * `dismiss()`.
 */
@Composable
private fun RoomRow(
    room: Room,
    model: AppModel,
    onSwitch: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val isCurrent = room.id == model.currentRoom?.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onSwitch(room.id)
                onDismiss()
            }
            // The chartreuse hairline is the only drawn sign of which room
            // you are in, and colour is never the only signal (§11), so the
            // same fact is carried in the row's state for a screen reader —
            // which announces it as selected, and says no number about it.
            .semantics { selected = isCurrent }
            .sizeIn(minHeight = RowMinHeight)
            .padding(vertical = RowPadding),
        horizontalArrangement = Arrangement.spacedBy(RowSpan),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The current room marked with a chartreuse hairline.
        Box(
            Modifier
                .width(MarkWidth)
                .height(MarkHeight)
                .background(if (isCurrent) Palette.chartreuse else Color.Transparent),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TitleSpacing),
        ) {
            Text(
                text = model.displayName(room),
                style = RibbonType.ui(16f),
                color = Palette.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PortraitOverlap)) {
                // Who is in it, as portraits. Rows, never a count (Law 2):
                // the faces are the answer to "who", and there is no
                // "+2 more".
                model.members(room).forEach { membership ->
                    PortraitView(
                        person = model.person(membership.personID),
                        ink = membership.ink,
                        size = PortraitSize,
                        image = model.portrait(membership.personID),
                    )
                }
            }
        }

        if (room.isPaused) {
            SmallCaps(Copy.PAUSED, size = 11f)
        }

        model.openReading(room)?.let { reading ->
            // A paused room's fire is drawn in whatever state it
            // actually holds — never banked by a lapse (S14).
            CampfireGlyph(
                state = model.fireState(reading),
                scale = reading.handiwork.scale,
                height = 22.dp,
            )
        }
    }
}
