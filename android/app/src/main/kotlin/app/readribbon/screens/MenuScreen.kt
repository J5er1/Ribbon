@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.Room
import app.readribbon.design.HairlineRule
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.CampfireGlyph
import app.readribbon.services.Passkeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S14 + S18, made one screen: the menu.
//
// The book gives the rooms a sheet of their own (S14) and puts You behind it
// (S18); deviation 13 had already pulled You out to a second sheet, off the
// room's portrait. That left two half-height sheets, each a flat pile of
// controls with a heading on none of them — and, because neither of them was
// ever about an invite you had been *sent*, no way at all to accept one for a
// second room once you had a room of your own. The tapped link was the whole
// mechanism, and a link that landed in an email on a laptop had nowhere to go.
//
// This is the two of them made one full-screen menu, in named sections, with
// the two doors that were missing: **Invite someone**, for the room you are
// already in, and **Join with an invite**, for a room somebody has asked you
// into. Recorded in docs/deviations.md 14.
//
// Hierarchy is carried by three things and no others: a small-caps head over
// each section with a hairline under it; ivory 17 sp rows for what you go to
// or do; quiet muted small caps for what undoes (leave a room, delete an
// account). No chevrons, no disclosure triangles, no preference categories —
// this is still a room, not a Settings app.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it is
// on every other screen here.

/**
 * Where the menu opens. The room's name and your own portrait are two
 * different questions, and they arrive at two different places on one screen.
 */
enum class MenuEntry {
    /** The room's name, top-left: the rooms. */
    ROOMS,

    /**
     * Your portrait, top-right: You. Deviation 13 promised settings one tap
     * from the room, and one tap is what this still is — the menu opens
     * already scrolled to your own section.
     */
    YOU,
}

/**
 * The screens the menu pushes. The four settings screens are S19–S22
 * unchanged; the join is the new door. Swift pushes values onto a
 * `NavigationPath`; a navigation-compose route is a string, so the join's
 * token travels as a path segment.
 */
private object MenuRoute {
    const val ROOT = "menu"
    const val TEXT = "text"
    const val NOTIFICATIONS = "notifications"
    const val DOWNLOADS = "downloads"
    const val PLAN = "plan"
    const val JOIN_WITH_INVITE = "join-with-invite"
    const val JOIN_PATTERN = "join/{token}"
    const val TOKEN = "token"

    fun join(token: Uuid): String = "join/$token"
}

/** `.padding(.horizontal, 24)` down the whole menu. */
private val Margin = 24.dp

/** `VStack(spacing: 34)` between the menu's sections. */
private val SectionGap = 34.dp

/**
 * The smallest a control may be tapped at (§11, deviation 12). Every gesture
 * has a tap equivalent and every target clears a finger, even where the drawn
 * thing is smaller.
 */
private val MinTarget: Dp = 44.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the glyph;
 * Swift grows the target outward instead and leaves the words where they
 * were. Pulling the control back by that same 8 dp puts "Leave this room"
 * back on the menu's margin, flush with the rows above it.
 */
private val QuietControlInset = (-8).dp

/** The chartreuse hairline that marks the current room (S14). */
private val MarkWidth = 2.dp
private val MarkHeight = 34.dp

/** The members' portraits on a room row, overlapped, and the face on You. */
private val RowPortrait = 18.dp
private val RowPortraitOverlap = (-5).dp
private val YouPortrait = 56.dp

/** A room whose invite is being handed out, and whether it was just made. */
private data class InviteTarget(
    val room: Room,
    /**
     * A room made a moment ago: closing its invite should leave you in the
     * new room rather than back in the menu (S15 — naming and inviting are
     * two steps that should feel like one, and the third step is being
     * there).
     */
    val isNew: Boolean,
)

/**
 * The menu (S14 + S18).
 *
 * Drawn over the room rather than pushed into its stack, the way the book is
 * — Swift presents it with `.fullScreenCover`, and the nearest true thing
 * here is a layer of its own with its own back.
 *
 * @param model the store.
 * @param entry which of the room header's two doors was used.
 * @param onDismiss close the menu and leave the room where it was.
 */
@Composable
fun MenuScreen(
    model: AppModel,
    entry: MenuEntry,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val reduceMotion = rememberReduceMotion()

    // Predictive back (§12.2): the room peels in behind the closing menu, the
    // same gesture and the same physics the book uses. Registered *before*
    // the NavHost below, because back callbacks are taken in reverse order of
    // registration — so a pushed settings screen pops first, and only an
    // unpushed menu closes.
    var backPull by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { event -> backPull = event.progress }
            onDismiss()
            backPull = 0f
        } catch (cancelled: CancellationException) {
            backPull = 0f
            throw cancelled
        }
    }

    val push = tween<Float>(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)
    val slide = tween<IntOffset>(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    Box(
        modifier
            .fillMaxSize()
            // Esc closes the menu on a hardware keyboard, as
            // `.keyboardShortcut(.cancelAction)` does on iOS — and on the
            // devices that route Escape to the back gesture instead, the
            // predictive-back handler above catches it.
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                // Under reduce motion the menu does not move at all; the
                // gesture still closes it (§11).
                val peel = if (reduceMotion) 0f else backPull
                val shrink = 1f - 0.06f * peel
                scaleX = shrink
                scaleY = shrink
                translationY = peel * 24.dp.toPx()
                alpha = 1f - 0.28f * peel
            },
    ) {
        // The ground and its grain, painted behind everything the menu draws:
        // `.room()` hides its node from accessibility (the paper is texture,
        // not information), and a scroll that hid itself would take the menu
        // with it.
        //
        // It also swallows every touch that the menu itself does not take. A
        // `.fullScreenCover` is a presentation and stops the room being
        // tapped through it for free; a layer in the same Box does not, and
        // the room's own header sits directly under this one.
        Box(
            Modifier
                .fillMaxSize()
                .room()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )

        NavHost(
            navController = navController,
            startDestination = MenuRoute.ROOT,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideInHorizontally(slide) { it / 4 } + fadeIn(push) },
            exitTransition = { fadeOut(push) },
            popEnterTransition = { fadeIn(push) },
            popExitTransition = { slideOutHorizontally(slide) { it / 4 } + fadeOut(push) },
        ) {
            composable(
                MenuRoute.ROOT,
                // The root never slides: it is what the menu opened onto.
                enterTransition = { EnterTransition.None },
                exitTransition = { fadeOut(push) },
                popEnterTransition = { fadeIn(push) },
                popExitTransition = { ExitTransition.None },
            ) {
                MenuRoot(
                    model = model,
                    entry = entry,
                    onOpen = { route -> navController.navigate(route) },
                    onDismiss = onDismiss,
                )
            }
            composable(MenuRoute.TEXT) {
                TextSettingsScreen(model = model, onBack = { navController.popBackStack() })
            }
            composable(MenuRoute.NOTIFICATIONS) {
                NotificationSettingsScreen(model = model, onBack = { navController.popBackStack() })
            }
            composable(MenuRoute.DOWNLOADS) {
                DownloadsScreen(model = model, onBack = { navController.popBackStack() })
            }
            composable(MenuRoute.PLAN) {
                PlanScreen(model = model, onBack = { navController.popBackStack() })
            }
            composable(MenuRoute.JOIN_WITH_INVITE) {
                JoinWithInviteScreen(
                    onBack = { navController.popBackStack() },
                    onToken = { token -> navController.navigate(MenuRoute.join(token)) },
                )
            }
            composable(
                MenuRoute.JOIN_PATTERN,
                arguments = listOf(navArgument(MenuRoute.TOKEN) { type = NavType.StringType }),
            ) { entryArgs ->
                val token = entryArgs.arguments?.getString(MenuRoute.TOKEN)
                    ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                if (token == null) {
                    // Nothing under a NavHost destination to show through, so
                    // the nearest honest thing is the unlit ground.
                    Box(Modifier.fillMaxSize().room())
                } else {
                    // The same S16 thread a tapped link runs, pushed rather
                    // than presented — so the join is inside the menu it was
                    // started from, and nothing has to be handed across two
                    // presentations. The key discards a half-finished join
                    // if a second token ever lands on this destination.
                    key(token) {
                        JoinFlow(
                            token = token,
                            model = model,
                            // Joined, and `joinRoom` has already made it the
                            // current room: the menu gets out of the way so
                            // you arrive in it.
                            onDone = onDismiss,
                            onDismiss = { navController.popBackStack() },
                            // A dead invite here goes back to the field
                            // rather than out of the menu — "ask for a new
                            // one" and paste the new one.
                            wayOut = Copy.BACK,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The menu itself: the rooms, the room you are in, you, and your account.
 *
 * @param onOpen push one of the menu's screens.
 * @param onDismiss close the menu.
 */
@Composable
private fun MenuRoot(
    model: AppModel,
    entry: MenuEntry,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewRoom by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf<InviteTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scroll = rememberScrollState()

    // Opened from the portrait: land on your own section rather than making
    // you scroll past the rooms to reach it. The offset is read off the You
    // section once it has been placed, and the jump is instant — this is
    // where the menu opened, not somewhere it travelled to.
    var youOffset by remember { mutableIntStateOf(-1) }
    // Saveable, because `rememberScrollState` is: after a rotation the scroll
    // is already where the person left it, and landing on You a second time
    // would throw it away.
    var landed by rememberSaveable { mutableStateOf(entry != MenuEntry.YOU) }
    LaunchedEffect(youOffset, landed) {
        if (!landed && youOffset >= 0) {
            scroll.scrollTo(youOffset)
            landed = true
        }
    }

    // Edge-to-edge is mandatory (§12.2), so the menu carries both insets
    // itself: the status bar above the way out, the navigation bar under the
    // last line.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .readableColumn()
                    .padding(horizontal = Margin)
                    .padding(top = statusBar, bottom = bottomBar + 44.dp),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                // The way out. Back closes the menu too, and does it with the
                // system gesture; a screen whose only way out is a gesture
                // has no tap equivalent, so the control is drawn (§11).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    QuietControl(
                        title = Copy.CLOSE,
                        modifier = Modifier.offset(x = 8.dp),
                        onClick = onDismiss,
                    )
                }

                // Rooms (S14).
                Column {
                    SectionHead(Copy.ROOMS)
                    model.state.rooms.forEach { room ->
                        key(room.id) {
                            MenuRoomRow(
                                model = model,
                                room = room,
                                onClick = {
                                    // Tap a room → switch, the menu closes,
                                    // the room screen cross-fades (S14).
                                    model.switchRoom(room.id)
                                    onDismiss()
                                },
                            )
                        }
                    }
                    Column(Modifier.padding(top = 6.dp)) {
                        MenuRow(Copy.START_A_ROOM_CONTROL) { showNewRoom = true }
                        MenuRow(Copy.JOIN_WITH_AN_INVITE) { onOpen(MenuRoute.JOIN_WITH_INVITE) }
                    }
                }

                // This room (S12/S15) — the controls for the room you are in.
                model.currentRoom?.let { room ->
                    Column {
                        SectionHead(Copy.THIS_ROOM, detail = model.displayName(room))
                        if (model.isFull(room)) {
                            // S15's full state, said where the invite would
                            // have been.
                            Text(
                                text = Copy.ROOM_HOLDS_SIX,
                                style = RibbonType.ui(15f),
                                color = Palette.muted,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        } else {
                            MenuRow(Copy.INVITE_SOMEONE) {
                                inviting = InviteTarget(room = room, isNew = false)
                            }
                        }
                        RoomControls(model = model, room = room, onLeft = onDismiss)
                    }
                }

                // You (S18).
                Column(
                    Modifier.onGloballyPositioned { coordinates ->
                        youOffset = coordinates.positionInParent().y.roundToInt()
                    },
                ) {
                    SectionHead(Copy.YOU)
                    YouIdentityRow(model = model)
                    MenuRow(Copy.TEXT_AND_TRANSLATION) { onOpen(MenuRoute.TEXT) }
                    MenuRow(Copy.NOTIFICATIONS) { onOpen(MenuRoute.NOTIFICATIONS) }
                    MenuRow(Copy.DOWNLOADS) { onOpen(MenuRoute.DOWNLOADS) }
                    MenuRow(Copy.PLAN) { onOpen(MenuRoute.PLAN) }
                }

                // The account (§6.10).
                Column {
                    SectionHead(Copy.ACCOUNT)
                    AccountControls(model = model)
                    QuietControl(
                        title = Copy.DELETE_ACCOUNT,
                        modifier = Modifier.offset(x = QuietControlInset),
                    ) { confirmDelete = true }
                }

                SmallCaps(
                    Copy.versionLine(version),
                    size = 11f,
                    color = Palette.muted.copy(alpha = 0.7f),
                )
            }
        }
    }

    if (showNewRoom) {
        // Naming and inviting are two steps that should feel like one (S15) —
        // the invite follows the naming, both over the menu, and the menu
        // closes behind them.
        NewRoomSheet(
            model = model,
            onCreated = { newRoom -> inviting = InviteTarget(room = newRoom, isNew = true) },
            onDismiss = { showNewRoom = false },
        )
    }

    inviting?.let { target ->
        InviteSheet(
            room = target.room,
            model = model,
            onDismiss = {
                inviting = null
                if (target.isNew) onDismiss()
            },
        )
    }

    if (confirmDelete) {
        // §6.8: the "leave your notes behind?" question, asked once, at
        // deletion. Leaving them is never not the default.
        RibbonConfirmDialog(
            question = Copy.LEAVE_NOTES_QUESTION,
            onDismiss = { confirmDelete = false },
        ) {
            ConfirmChoice(
                title = Copy.DELETE_AND_LEAVE_THEM,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    model.deleteAccount(keepNotesBehind = true)
                },
            )
            ConfirmChoice(
                title = Copy.DELETE_AND_TAKE_THEM_BACK,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    model.deleteAccount(keepNotesBehind = false)
                },
            )
            // iOS supplies this button itself; Compose's dialog has only the
            // choices it is handed, and a question with no way to say no is
            // not asked plainly.
            ConfirmChoice(title = Copy.STAY, onClick = { confirmDelete = false })
        }
    }
}

// MARK: Section furniture

/**
 * A section's head: small caps, a hairline under it, and — for the room you
 * are in — the room's own name beside it, so "This room" is never a question.
 */
@Composable
private fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier = modifier.padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallCaps(title, size = 12f, color = Palette.text.copy(alpha = 0.75f))
            if (detail != null) SmallCaps(detail, size = 12f)
        }
        HairlineRule()
    }
}

/**
 * One row of the menu: a thing you go to, or a thing you do. Ivory, 17 sp,
 * and never shorter than a finger (§11, deviation 12).
 */
@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
    }
}

/**
 * One room: its name, who is in it, what it is reading, and its fire.
 *
 * The book asks for the name, the portraits and the fire (S14). The book's
 * name is added here: with more than one room the fires are the same object
 * drawn small, and what a room is *reading* is the thing that tells them
 * apart at a glance. A book's name is an address, not a score (Law 2).
 */
@Composable
private fun MenuRoomRow(
    model: AppModel,
    room: Room,
    onClick: () -> Unit,
) {
    val isCurrent = room.id == model.currentRoom?.id
    val reading = model.openReading(room)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // The chartreuse hairline is the only drawn sign of which room
            // you are in, and colour is never the only signal (§11), so the
            // same fact is carried in the row's state for a screen reader —
            // which announces it as selected, and says no number about it.
            .semantics { selected = isCurrent }
            .sizeIn(minHeight = MinTarget)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(MarkWidth)
                .height(MarkHeight)
                .background(if (isCurrent) Palette.chartreuse else Color.Transparent),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = model.displayName(room),
                style = RibbonType.ui(16f),
                color = Palette.text,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Who is in it, as portraits. Rows, never a count (Law 2):
                // the faces are the answer to "who", and there is no
                // "+2 more".
                Row(horizontalArrangement = Arrangement.spacedBy(RowPortraitOverlap)) {
                    model.members(room).forEach { membership ->
                        PortraitView(
                            person = model.person(membership.personID),
                            ink = membership.ink,
                            size = RowPortrait,
                            image = model.portrait(membership.personID),
                        )
                    }
                }
                reading?.let { open ->
                    Bible.book(open.bookID)?.let { book -> SmallCaps(book.name, size = 11f) }
                }
            }
        }

        if (room.isPaused) {
            SmallCaps(Copy.PAUSED, size = 11f)
        }

        reading?.let { open ->
            val state = model.fireState(open)
            // The glyph clears its own semantics everywhere else, because it
            // always sits beside the words it illustrates; on a room row
            // there are no such words, so the box around it says the state —
            // a state, never a number (Law 2, §11).
            Box(Modifier.semantics { contentDescription = state.displayName }) {
                // A paused room's fire is drawn in whatever state it actually
                // holds — never banked by a lapse (S14).
                CampfireGlyph(
                    state = state,
                    scale = open.handiwork.scale,
                    height = 22.dp,
                )
            }
        }
    }
}

// MARK: You

/**
 * Portrait and name, editable in place (S18) — presence is faces, so the face
 * can be added or changed here, not only at onboarding.
 */
@Composable
private fun YouIdentityRow(model: AppModel) {
    var editingName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Swift's `PhotosPicker` plus its `.onChange` — the picked image is read
    // and downsampled off the main thread, then handed to the store. Setting
    // a portrait must finish whatever happens to this screen, so it runs on
    // the model's own scope rather than the composition's.
    val portraitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { downsampledJpeg(it) }
            }
            if (jpeg != null) model.setPortrait(jpeg)
        }
    }

    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                .clickable(role = Role.Button) {
                    portraitPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                // Swift's `.accessibilityLabel(Copy.addAPortrait)`, which
                // replaces the label rather than adding to it: the control is
                // the way to a portrait, so the portrait's own name is
                // cleared beneath it.
                .semantics { contentDescription = Copy.ADD_A_PORTRAIT },
            contentAlignment = Alignment.Center,
        ) {
            PortraitView(
                person = model.me,
                ink = null,
                size = YouPortrait,
                image = model.me?.let { model.portrait(it.id) },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (editingName) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = RibbonType.ui(18f).copy(color = Palette.text),
                cursorBrush = SolidColor(Palette.chartreuse),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) model.updateMe(name = trimmed)
                        editingName = false
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTarget)
                    .focusRequester(focus),
            )
        } else {
            Box(
                // A short name draws a short word, and the word is the whole
                // control: the target keeps its 44 dp in both directions so
                // "Jo" is no harder to tap than "Jonathan" (§11, deviation 12).
                modifier = Modifier
                    .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                    .clickable(role = Role.Button) {
                        name = model.me?.name ?: ""
                        editingName = true
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = model.me?.name ?: "",
                    style = RibbonType.ui(18f),
                    color = Palette.text,
                )
            }
        }
    }
}

/**
 * The current room's own controls: its name, your ink, the way out. These
 * lived only on your S12, which a fresh room of one couldn't reach
 * (deviations 9a); they moved to You (deviation 13) and now sit under the
 * room they are about.
 *
 * @param onLeft the room was left; the menu closes, because the room it was
 *   opened over is gone.
 */
@Composable
private fun RoomControls(
    model: AppModel,
    room: Room,
    onLeft: () -> Unit,
) {
    var editingRoomName by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var showInkPicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    Column {
        if (editingRoomName) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (roomName.isEmpty()) {
                    Text(
                        text = Copy.ROOM_NAME,
                        style = RibbonType.ui(17f),
                        color = Palette.muted,
                    )
                }
                BasicTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    singleLine = true,
                    textStyle = RibbonType.ui(17f).copy(color = Palette.text),
                    cursorBrush = SolidColor(Palette.chartreuse),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            model.renameRoom(room, name = roomName)
                            editingRoomName = false
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        } else {
            MenuRow(Copy.NAME_THIS_ROOM) {
                roomName = room.name ?: ""
                editingRoomName = true
            }
        }

        // Ink is identity from three people up (§4.5); below that the room
        // draws from the whole palette freely and there is nothing to choose.
        if (model.inkIsIdentity(room)) {
            MenuRow(Copy.CHANGE_YOUR_INK) { showInkPicker = true }
        }

        // The way out is quiet, never emphasised, and never hidden.
        QuietControl(
            title = Copy.LEAVE_THIS_ROOM,
            modifier = Modifier.offset(x = QuietControlInset),
        ) { confirmLeave = true }
    }

    if (showInkPicker) {
        InkPickerSheet(model = model, room = room, onDismiss = { showInkPicker = false })
    }

    if (confirmLeave) {
        // The same two questions S12 asks — the confirmation, and then §6.8's
        // "leave your notes behind?", where leaving them is the default and
        // taking them back is possible and never the default.
        LeaveRoomDialogs(
            model = model,
            room = room,
            onDismiss = { confirmLeave = false },
            onLeft = {
                confirmLeave = false
                onLeft()
            },
        )
    }
}

/**
 * The account (§6.10): an emailed code, no passwords. Signed out is a state,
 * not a nag — one quiet line, and the reason stated plainly.
 */
@Composable
private fun AccountControls(model: AppModel) {
    var signingIn by remember { mutableStateOf(false) }
    var passkeyLine by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    fun addPasskey() {
        val host = activity ?: return
        passkeyLine = null
        scope.launch {
            passkeyLine = try {
                model.registerPasskey(host)
                Copy.PASSKEY_ADDED
            } catch (_: Passkeys.Cancelled) {
                // Dismissed the sheet. Nothing happened, and nothing is said.
                null
            } catch (_: Throwable) {
                Copy.PASSKEY_DIDNT_WORK
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            model.isSignedIn -> {
                model.accountEmail?.let { address -> SmallCaps(address, size = 12f) }
                // §6.10 wants a passkey where there is one. Offered here, on
                // the account, because that is what it belongs to — and only
                // ever added to the emailed code, never in place of it.
                if (model.passkeysAvailable && activity != null) {
                    QuietControl(
                        title = Copy.ADD_A_PASSKEY,
                        modifier = Modifier.offset(x = QuietControlInset),
                    ) { addPasskey() }
                    Text(
                        text = passkeyLine ?: Copy.PASSKEY_REASON,
                        style = RibbonType.ui(13f),
                        color = Palette.muted,
                    )
                }
                QuietControl(
                    title = Copy.SIGN_OUT,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) {
                    signingIn = false
                    // Signing out must finish whatever happens to this screen.
                    model.viewModelScope.launch { model.signOutRemote() }
                }
            }

            // Remote is not configured in this build; no dead control.
            model.remote == null -> Unit

            signingIn -> SignInInline(
                model = model,
                onSignedIn = { signingIn = false },
                onCancel = { signingIn = false },
            )

            else -> {
                QuietControl(
                    title = Copy.SIGN_IN,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) { signingIn = true }
                Text(
                    text = Copy.ACCOUNT_REASON,
                    style = RibbonType.ui(13f),
                    color = Palette.muted,
                )
            }
        }
    }
}

// MARK: Joining a second room (S16, from the menu)

/**
 * The door that was missing.
 *
 * A tapped invite link runs S16 by itself, from anywhere — but a link that
 * arrived in an email on a laptop, or in a thread this phone can't open, had
 * nowhere to go once you already had a room: the paste field lived on a page
 * of onboarding nobody sees twice. This is that field, kept.
 *
 * It takes the link, or the code out of it (`AppModel.inviteToken(fromPasted)`),
 * and hands the token straight to the same join thread.
 *
 * @param onBack pop back to the menu. iOS draws this chevron for free.
 * @param onToken a live-looking token; push the join.
 */
@Composable
private fun JoinWithInviteScreen(
    onBack: () -> Unit,
    onToken: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pasted by remember { mutableStateOf("") }
    var missed by remember { mutableStateOf(false) }

    fun accept() {
        val token = AppModel.inviteToken(fromPasted = pasted)
        if (token == null) {
            if (pasted.trim().isNotEmpty()) missed = true
            return
        }
        onToken(token)
    }

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().room())

        Column(Modifier.fillMaxSize().padding(top = statusBar, bottom = bottomBar)) {
            BackControl(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .readableColumn(),
                verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The screen says its own name: it is pushed under nothing
                // but a chevron, and a screen nobody can name is a screen
                // nobody can go back to on purpose.
                SmallCaps(Copy.JOIN_WITH_AN_INVITE, size = 12f)
                Text(
                    text = Copy.THE_LINK_BRINGS_YOU_IN,
                    style = RibbonType.ui(17f),
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                // Onboarding's own field, lifted whole: the same centred
                // line of typing, the same box around it, so the two places
                // a link can be pasted are one thing drawn twice.
                CentredTextField(
                    value = pasted,
                    onValueChange = { text ->
                        pasted = text
                        // A pasted link is complete the moment it lands —
                        // don't make them find a go button (S17's field,
                        // kept).
                        missed = false
                        if (AppModel.inviteToken(fromPasted = text) != null) accept()
                    },
                    placeholder = Copy.PASTE_INVITE_PROMPT,
                    size = 16f,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        // A link is never what autocorrect thinks it is.
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { accept() }),
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.surface)
                        .border(1.dp, Palette.rule, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                if (missed) {
                    Text(
                        text = Copy.THAT_LINK_ISNT_AN_INVITE,
                        style = RibbonType.ui(14f),
                        color = Palette.muted,
                    )
                }
            }
        }
    }
}
