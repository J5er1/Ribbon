@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Invite
import app.readribbon.core.Room
import app.readribbon.design.Palette
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.room
import kotlin.uuid.ExperimentalUuidApi

// S15 — making a room, and inviting. The link is the whole mechanism: no
// contact-list permission, no email field, no invite-by-username. Copy
// assumes one person.
//
// Swift reaches the store through `@Environment(AppModel.self)` and closes
// itself through `@Environment(\.dismiss)`; nothing in this build has an
// ambient equivalent, so both are parameters, as they are on every other
// screen here. The model holds Compose snapshot state, so reading
// `model.isSignedIn` recomposes exactly as `@Observable` does.

/** `VStack(spacing: 22)` — down the invite sheet, and down the naming one. */
private val Gap = 22.dp

/** `.padding(.horizontal, 40)` around the sentence that explains the link. */
private val LineMargin = 40.dp

/** `.padding(.horizontal, 24)` around the inline sign-in thread. */
private val SignInMargin = 24.dp

/** The invite capsule's own `.padding(.horizontal, 28)` / `.vertical, 13`. */
private val CapsuleH = 28.dp
private val CapsuleV = 13.dp

/**
 * Nothing is a target below a finger's width (deviation 12). The capsule is
 * already taller than this at any text size; the floor is here so it cannot
 * quietly stop being.
 */
private val TouchTarget = 44.dp

/**
 * Swift asks for `.presentationDetents([.medium])` at both call sites — the
 * invite sheet is always half a screen with its one sentence centred in it.
 * A Material sheet has no medium detent: it takes the height of what is put
 * in it. So the height is asked for directly, as a fraction of the sheet's
 * own maximum, and the content centres inside that. Same picture, arrived at
 * from the other end.
 */
private const val MediumDetent = 0.5f

/** `.padding(24)`, then `.padding(.top, 20)` / `.padding(.bottom, 12)`. */
private val FormPadding = 24.dp
private val FormTopExtra = 20.dp
private val FormBottomExtra = 12.dp

/** The name field: `RoundedRectangle(cornerRadius: 10)`, inset 14 × 12. */
private val FieldRadius = 10.dp
private val FieldH = 14.dp
private val FieldV = 12.dp

/**
 * Handing out the link (S15).
 *
 * Three states, and only ever one of them: the room is full and there is
 * nothing to hand out; the backend is configured but there is no account
 * yet, so the account happens here; or the link is live and there is a
 * control that sends it.
 *
 * `.presentationBackground(Palette.ground)` is the sheet's container colour;
 * the grain goes on top of it, inside the content, as it does in a room.
 * Swift's drag indicator is `.automatic`, and automatic draws nothing for a
 * sheet with a single detent — so no grabber. The whole sheet still drags,
 * and predictive back closes it.
 *
 * @param room the room being handed out.
 * @param model the store.
 * @param onDismiss close the sheet. Swift's `@Environment(\.dismiss)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    room: Room,
    model: AppModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Palette.ground,
        contentColor = Palette.text,
        dragHandle = null,
        // The content keeps its own clearance from the system bars, so the
        // ground and its grain run to the very bottom of the sheet.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        InviteContent(
            room = room,
            model = model,
            modifier = Modifier.fillMaxHeight(MediumDetent),
        )
    }
}

/**
 * The invite sheet's body without the sheet around it.
 *
 * Split out for the same reason the chooser and the rooms list are — so the
 * same three states can be shown somewhere that is already a sheet, or on a
 * page of onboarding, without a sheet inside a sheet.
 */
@Composable
fun InviteContent(
    room: Room,
    model: AppModel,
    modifier: Modifier = Modifier,
) {
    var invite: Invite? by remember(room.id) { mutableStateOf<Invite?>(null) }
    val full = model.isFull(room)

    // Three-button navigation eats the bottom of the sheet, so its inset is
    // added under the content rather than clipped off it.
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(room.id, full) {
        if (!full) {
            val live = model.createInvite(room)
            invite = live
            runCatching { model.pushInvite(live, room) }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // `.room()` — the ground and its grain — is painted by a box behind
        // the content rather than by the content itself: the modifier hides
        // its node from accessibility (the paper is texture, not
        // information), and the sentence in front of it must not go with it.
        Box(Modifier.matchParentSize().room())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
                .padding(bottom = bottomBar),
            // Swift centres the whole stack between two Spacers.
            verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (full) {
                Text(
                    text = Copy.ROOM_HOLDS_SIX,
                    style = RibbonType.ui(16f),
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                )
            } else if (model.remote != null && !model.isSignedIn) {
                // The link resolves through the backend, and the backend
                // needs your account — so the account happens here, at the
                // moment it's genuinely needed, never as a wall at launch
                // (§6.1, §6.10).
                Text(
                    text = Copy.INVITE_NEEDS_SIGN_IN,
                    style = RibbonType.ui(16f),
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = LineMargin),
                )
                SignInInline(
                    model = model,
                    onSignedIn = {
                        // Re-minting reuses the live invite and registers it
                        // now that the backend knows who's asking.
                        invite = model.createInvite(room)
                    },
                    modifier = Modifier.padding(horizontal = SignInMargin),
                )
            } else {
                Text(
                    text = Copy.INVITE_SEND,
                    style = RibbonType.ui(17f),
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = LineMargin),
                )

                invite?.let { live -> SendTheInvite(invite = live) }
            }
        }
    }
}

/**
 * The one control on the sheet: a chartreuse capsule that hands the link to
 * whatever the person already uses to talk to the person they're inviting.
 *
 * SwiftUI's `ShareLink`, said in Android's terms: one plain-text intent
 * through the system chooser. The link is the whole item, exactly as it is
 * there — no subject, no preview title, nothing about the room.
 */
@Composable
private fun SendTheInvite(invite: Invite) {
    val context = LocalContext.current
    Text(
        text = Copy.SEND_THE_INVITE,
        style = RibbonType.ui(17f, FontWeight.Medium),
        color = Palette.ground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Palette.chartreuse)
            .clickable(role = Role.Button) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, invite.url())
                }
                context.startActivity(Intent.createChooser(send, null))
            }
            .sizeIn(minWidth = TouchTarget, minHeight = TouchTarget)
            .padding(horizontal = CapsuleH, vertical = CapsuleV),
    )
}

// MARK: S15's naming half — used when starting an additional room from S14.

/**
 * Naming a new room, then handing it out.
 *
 * Naming and inviting are two steps that should feel like one (S15): this
 * sheet closes and the invite sheet follows it, which is the caller's job
 * here as it is in Swift.
 *
 * Swift adds `.presentationSizing(.fitted)` because iPad ignores detents and
 * would otherwise wrap one field and one button in a mostly empty form
 * sheet. A Material sheet is fitted already — it takes the height of what is
 * put in it — so there is nothing to ask for, and nothing to undo on a
 * tablet.
 *
 * @param model the store.
 * @param onCreated the room that was just made — Swift's `onCreated`.
 * @param onDismiss close the sheet. Called before [onCreated], in that
 *   order, exactly as Swift does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRoomSheet(
    model: AppModel,
    onCreated: (Room) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Palette.ground,
        contentColor = Palette.text,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NewRoomContent(model = model, onCreated = onCreated, onDismiss = onDismiss)
    }
}

/** The naming form without the sheet around it. */
@Composable
fun NewRoomContent(
    model: AppModel,
    onCreated: (Room) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().room())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(
                    start = FormPadding,
                    end = FormPadding,
                    top = FormPadding + FormTopExtra,
                    bottom = FormPadding + FormBottomExtra + bottomBar,
                ),
            verticalArrangement = Arrangement.spacedBy(Gap),
            horizontalAlignment = Alignment.Start,
        ) {
            SmallCaps(Copy.ROOM_NAME, size = 12f)

            RoomNameField(name = name, onNameChange = { name = it })

            WayInButton(title = Copy.START_A_ROOM_CONTROL) {
                val trimmed = name.trim()
                val room = model.createRoom(name = trimmed.ifEmpty { null })
                onDismiss()
                onCreated(room)
            }
        }
    }
}

/**
 * The name field. A room does not have to be called anything, and the
 * placeholder says so rather than the field being marked optional somewhere
 * else.
 */
@Composable
private fun RoomNameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FieldRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(width = 1.dp, color = Palette.rule, shape = shape)
            .heightIn(min = TouchTarget)
            .padding(horizontal = FieldH, vertical = FieldV),
        contentAlignment = Alignment.CenterStart,
    ) {
        // SwiftUI's `prompt:`, which is drawn behind the text rather than
        // being a label above it.
        if (name.isEmpty()) {
            Text(
                text = Copy.OPTIONAL,
                style = RibbonType.ui(18f),
                color = Palette.muted,
            )
        }
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = RibbonType.ui(18f).copy(color = Palette.text),
            cursorBrush = SolidColor(Palette.chartreuse),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
