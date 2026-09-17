@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import app.readribbon.design.Air
import app.readribbon.design.BackChevron
import app.readribbon.design.Chevron
import app.readribbon.design.Flows
import app.readribbon.design.LocalAppearance
import app.readribbon.design.LocalFlowLayer
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonScreen
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SectionLabel
import app.readribbon.design.Setting
import app.readribbon.design.SettingValue
import app.readribbon.design.SettingsGroup
import app.readribbon.design.SmallCaps
import app.readribbon.design.flows
import app.readribbon.design.flowsAsWords
import app.readribbon.design.grain
import app.readribbon.design.paper
import app.readribbon.design.peeled
import app.readribbon.design.pressable
import app.readribbon.design.pressablePaper
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberBackPeel
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.CampfireGlyph
import app.readribbon.services.Passkeys
import app.readribbon.services.UpdateState
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** The room's name: the room you are in, and the rooms you are in. */
    const val ROOM = "room"

    /** Your portrait: you, this phone, and the account. */
    const val YOU = "you"
    const val TEXT = "text"
    const val NOTIFICATIONS = "notifications"
    const val APPEARANCE = "appearance"
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

/** The update card's corner and the height of its progress bar. */
private val UpdateCardRadius = 8.dp
private val UpdateBarHeight = 4.dp

/** The chartreuse hairline that marks the current room (S14). */
private val MarkWidth = 2.dp
private val MarkHeight = 34.dp

/** The members' portraits on a room row, overlapped, and the face on You. */
private val RowPortrait = 18.dp
private val RowPortraitOverlap = (-5).dp
/**
 * Your own face on your own screen.
 *
 * Larger than a seat at the hearth, because here it is the subject rather
 * than one of six. Still nowhere near a hero image — 88 dp is a face you can
 * see, not a face being celebrated.
 */
private val YouPortrait = 88.dp

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
    onSwitch: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val reduceMotion = rememberReduceMotion()

    // Disabled the moment the menu is going, because `AnimatedContent` keeps
    // the outgoing content composed for the whole 400 ms exit and a back press
    // in that window would otherwise be eaten by a handler whose `onDismiss`
    // is already a no-op. The book's own handler stands down the same way.
    var closing by remember { mutableStateOf(false) }

    // Every way out goes through one door, so the guard above covers all of
    // them: the drawn control, a room row, leaving a room, deleting the
    // account, Escape, and the gesture itself.
    fun close() {
        closing = true
        onDismiss()
    }

    // Predictive back (§12.2): the room peels in behind the closing menu, the
    // same gesture and the same physics the book uses — `rememberBackPeel` is
    // where both of them now live, so letting go eases the menu back down and
    // committing hands the pull straight to the exit below. Registered
    // *before* the NavHost, because back callbacks are taken in reverse order
    // of registration — so a pushed settings screen pops first, and only an
    // unpushed menu closes.
    val peel = rememberBackPeel(enabled = !closing, onBack = { close() })

    // Above the NavHost, because the menu root is one of its destinations and
    // is disposed while a settings screen is showing — iOS keeps these on the
    // screen itself for the same reason, one level above its NavigationStack.
    var showNewRoom by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf<InviteTarget?>(null) }

    // A push, and under reduce motion a cut (§11) — the token takes that
    // branch itself, here as in every other NavHost in the app.
    val push: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val slide: FiniteAnimationSpec<IntOffset> = RibbonMotion.settle(reduceMotion)

    // A key event reaches only the focused node and its ancestors, and
    // nothing in the menu is focused when it opens — so without this the
    // handler below is never visited and the shortcut can never fire.
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }

    Box(
        modifier
            .fillMaxSize()
            .focusRequester(keys)
            .focusable()
            // Esc closes the menu on a hardware keyboard, as
            // `.keyboardShortcut(.cancelAction)` does on iOS — and on the
            // devices that route Escape to the back gesture instead, the
            // predictive-back handler above catches it.
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    close()
                    true
                } else {
                    false
                }
            }
            // Under reduce motion the menu does not move at all; the gesture
            // still closes it (§11), which the peel decides for itself.
            .peeled { peel.progress },
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
            // Which door was used *is* which screen this is, rather than
            // where a single scroll happens to land (deviation A29).
            startDestination = if (entry == MenuEntry.ROOMS) MenuRoute.ROOM else MenuRoute.YOU,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideInHorizontally(slide) { it / 4 } + fadeIn(push) },
            exitTransition = { fadeOut(push) },
            popEnterTransition = { fadeIn(push) },
            popExitTransition = { slideOutHorizontally(slide) { it / 4 } + fadeOut(push) },
        ) {
            composable(
                MenuRoute.ROOM,
                // A root never slides: it is what the menu opened onto.
                enterTransition = { EnterTransition.None },
                exitTransition = { fadeOut(push) },
                popEnterTransition = { fadeIn(push) },
                popExitTransition = { ExitTransition.None },
            ) {
                // A root is a layer like the screens it pushes, and it is the
                // one that holds the *leaving* half of every settings-title
                // flow. Without this its rows bound themselves to the menu's
                // own outer scope, which stays visible for as long as the
                // menu is open — so both halves of the key were live and
                // neither was going anywhere.
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    RoomMenu(
                        model = model,
                        onOpen = { route -> navController.navigate(route) },
                        onDismiss = { close() },
                        onSwitch = onSwitch,
                        onStartRoom = { showNewRoom = true },
                        onInvite = { room -> inviting = InviteTarget(room = room, isNew = false) },
                    )
                }
            }
            composable(
                MenuRoute.YOU,
                enterTransition = { EnterTransition.None },
                exitTransition = { fadeOut(push) },
                popEnterTransition = { fadeIn(push) },
                popExitTransition = { ExitTransition.None },
            ) {
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    YouMenu(
                        model = model,
                        onOpen = { route -> navController.navigate(route) },
                        onDismiss = { close() },
                    )
                }
            }
            composable(MenuRoute.TEXT) {
                // Same shared-transition scope as the room's stack, a new
                // layer: the words of the row that was tapped become the
                // heading of the screen it opened (design/Flow.kt).
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    TextSettingsScreen(model = model, onBack = { navController.popBackStack() })
                }
            }
            composable(MenuRoute.NOTIFICATIONS) {
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    NotificationSettingsScreen(
                        model = model,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(MenuRoute.APPEARANCE) {
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(MenuRoute.DOWNLOADS) {
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    DownloadsScreen(model = model, onBack = { navController.popBackStack() })
                }
            }
            composable(MenuRoute.PLAN) {
                CompositionLocalProvider(LocalFlowLayer provides this) {
                    PlanScreen(model = model, onBack = { navController.popBackStack() })
                }
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
                            onDone = { close() },
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

        if (showNewRoom) {
            // Naming and inviting are two steps that should feel like one
            // (S15) — the invite follows the naming, both over the menu, and
            // the menu closes behind them.
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
                    if (target.isNew) close()
                },
            )
        }
    }
}

/**
 * The room: who is in it, what it tells you, and the rooms you are in.
 *
 * One of the menu's two doors, and now genuinely its own screen rather than
 * a place a single scroll landed (deviation A29). The split is not
 * cosmetic — it falls along a line the data already draws. Notifications are
 * per room (`RoomNotificationPrefs`, and S19 opens by saying so). The plan
 * entitles a room, not a person (§14). The invite, the inks and leaving are
 * all about this room. None of that belongs beside your name and your text
 * size, and putting it there was what made one screen long enough to need
 * landing at an offset.
 *
 * The title is the room's own name, because a large Material title names the
 * thing you are looking at, and "Settings" is not a thing anybody is looking
 * at.
 */
@Composable
private fun RoomMenu(
    model: AppModel,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onSwitch: (Uuid) -> Unit,
    onStartRoom: () -> Unit,
    onInvite: (Room) -> Unit,
    modifier: Modifier = Modifier,
) {
    val here = model.currentRoom
    RibbonScreen(
        title = here?.let { model.displayName(it) } ?: Copy.ROOMS,
        lede = Copy.ROOM_LEDE,
        modifier = modifier,
        actions = { CloseControl(onDismiss) },
    ) {
        // This room (S12/S15).
        here?.let { room ->
            if (model.isFull(room)) {
                // S15's full state, said where the invite would have been.
                Text(
                    text = Copy.ROOM_HOLDS_SIX,
                    style = RibbonType.ui(15f),
                    color = Palette.muted,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            } else if (model.remote != null) {
                // The link resolves through the backend, so without one there
                // is nothing to hand out and no row for it.
                SettingsGroup(count = 1) {
                    Setting(
                        title = Copy.INVITE_SOMEONE,
                        subtitle = Copy.INVITE_SEND,
                        onClick = { onInvite(room) },
                    )
                }
                Air(SectionGap)
            }

            // The two doors that are about this room rather than about you.
            SettingsGroup(count = 2) {
                Setting(
                    title = Copy.NOTIFICATIONS,
                    subtitle = Copy.NOTIFICATIONS_SUB,
                    onClick = { onOpen(MenuRoute.NOTIFICATIONS) },
                    modifier = Modifier.flowsAsWords(Flows.settingsTitle(Flows.NOTIFICATIONS)),
                )
                Setting(
                    title = Copy.PLAN,
                    subtitle = Copy.PLAN_SUB,
                    onClick = { onOpen(MenuRoute.PLAN) },
                    modifier = Modifier.flowsAsWords(Flows.settingsTitle(Flows.PLAN)),
                )
            }

            Air(SectionGap)
            RoomControls(model = model, room = room, onLeft = onDismiss)
            Air(SectionGap)
        }

        // The rooms (S14).
        SectionLabel(Copy.YOUR_ROOMS)
        Air(10.dp)
        // A seam of grained ground between tiles rather than a rule between
        // lines — the divider every group in the app now uses.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            model.state.rooms.forEach { room ->
                key(room.id) {
                    MenuRoomRow(
                        model = model,
                        room = room,
                        onClick = {
                            // Tap a room → switch, the menu closes, the room
                            // screen cross-fades (S14). The book closes with
                            // it: a reading belongs to the room it is in, and
                            // leaving it open over another room would put
                            // somebody else's fire under somebody else's page.
                            onSwitch(room.id)
                            model.switchRoom(room.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(Copy.START_A_ROOM_CONTROL, onClick = onStartRoom)
            // A join goes through the backend and cannot happen without one.
            // No dead control (§6.1) — the same rule the account keeps.
            if (model.remote != null) {
                MenuRow(Copy.JOIN_WITH_AN_INVITE) { onOpen(MenuRoute.JOIN_WITH_INVITE) }
            }
        }
    }
}

/**
 * You: your name and face, how Scripture sets, and what this phone keeps.
 *
 * The menu's other door. Everything here is yours or this device's —
 * translation and text size are personal by §2.6, the wallpaper's colours are
 * a property of the phone, and the downloads are megabytes on it. Nothing on
 * this screen is about a room, which is the whole point of there being two.
 */
@Composable
private fun YouMenu(
    model: AppModel,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (model.updateState is UpdateState.Idle) {
            model.checkForUpdates()
        }
    }

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    RibbonScreen(
        title = Copy.YOU,
        // No lede. It used to list the three things below it — your name and
        // face, how Scripture sets, what this phone keeps — and then the
        // sections said the same three things again twenty pixels lower. A
        // lede that is a table of contents for the headings under it is a
        // sentence the reader has to get past twice. Your own face is the
        // better opening, and the line under it says the only thing a lede
        // was really carrying.
        modifier = modifier,
        actions = { CloseControl(onDismiss) },
    ) {
        YouIdentity(model = model)
        Air(SectionGap)

        // Grouped by what each thing is *about*, rather than by which screen
        // it happens to open. Translation and text size are how Scripture
        // sets for you; the wallpaper's colours are how the room is painted;
        // the downloads are megabytes on a device. The first two belong
        // together and the third does not, and putting all three in one list
        // called "Reading" was filing by convenience.
        SectionLabel(Copy.HOW_YOU_READ)
        Air(10.dp)
        SettingsGroup(count = 2) {
            Setting(
                title = Copy.TEXT_AND_TRANSLATION,
                subtitle = Copy.TEXT_SUB,
                onClick = { onOpen(MenuRoute.TEXT) },
                modifier = Modifier.flowsAsWords(Flows.settingsTitle(Flows.TEXT)),
            )
            Setting(
                title = Copy.APPEARANCE,
                // The state *is* the subtitle here, rather than a sentence
                // about the screen with the state squeezed in beside it:
                // "From your wallpaper" says both what the row is about and
                // where it currently stands, in four words.
                subtitle = if (LocalAppearance.current.wallpaperColour) {
                    Copy.FROM_YOUR_WALLPAPER
                } else {
                    Copy.RIBBONS_OWN
                },
                onClick = { onOpen(MenuRoute.APPEARANCE) },
                modifier = Modifier.flowsAsWords(Flows.settingsTitle(Flows.APPEARANCE)),
            )
        }

        Air(SectionGap)
        SectionLabel(Copy.THIS_PHONE)
        Air(10.dp)
        SettingsGroup(count = 1) {
            Setting(
                title = Copy.DOWNLOADS,
                subtitle = Copy.downloadsSub(context),
                onClick = { onOpen(MenuRoute.DOWNLOADS) },
                modifier = Modifier.flowsAsWords(Flows.settingsTitle(Flows.DOWNLOADS)),
            )
        }

        // The account (§6.10). It draws its own heading, because there are
        // builds where it draws nothing at all and a heading over nothing is
        // worse than no heading — see [AccountSection].
        AccountSection(model = model, onDelete = { confirmDelete = true })

        Air(SectionGap)
        UpdateSection(model = model)

        // Tapping the version checks for one, and the line says so while it
        // looks. The words cross-fade rather than swap: the check is usually
        // over in well under a second, and a line that flicked to "checking"
        // and back would read as a glitch rather than as an answer. The
        // control is the box around them, so the target does not move as they
        // change.
        Box(
            modifier = Modifier
                .padding(top = 18.dp)
                .clickable(role = Role.Button) { model.checkForUpdates() },
        ) {
            AnimatedContent(
                targetState = if (model.updateState is UpdateState.Checking) {
                    Copy.CHECKING_FOR_UPDATES
                } else {
                    Copy.versionLine(version)
                },
                transitionSpec = {
                    fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                        fadeOut(RibbonMotion.arrive(reduceMotion))
                },
                label = "the-version",
            ) { line ->
                SmallCaps(line, size = 11f, color = Palette.muted.copy(alpha = 0.7f))
            }
        }
    }

    if (confirmDelete) {
        // §6.8: the "leave your notes behind?" question, asked once, at
        // deletion. Leaving them is never not the default.
        RibbonConfirmDialog(
            question = Copy.LEAVE_NOTES_QUESTION,
            onDismiss = { confirmDelete = false },
        ) {
            // The menu closes first, the way leaving a room does: the person
            // it was about is gone. The way in replaces this whole branch a
            // moment later, so this is belt to that brace rather than the only
            // thing holding it — iOS, where the menu is a presentation of its
            // own, genuinely needs it.
            ConfirmChoice(
                title = Copy.DELETE_AND_LEAVE_THEM,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    onDismiss()
                    model.deleteAccount(keepNotesBehind = true)
                },
            )
            ConfirmChoice(
                title = Copy.DELETE_AND_TAKE_THEM_BACK,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    onDismiss()
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

/**
 * The way out, in the app bar's action slot.
 *
 * Back closes the menu too, and does it with the system gesture; a screen
 * whose only way out is a gesture has no tap equivalent, so the control is
 * drawn (§11). It sits top-right because that is where a Material action
 * goes, and because the door it closes was top-left or top-right on the room.
 */
@Composable
private fun CloseControl(onDismiss: () -> Unit) {
    QuietControl(title = Copy.CLOSE, onClick = onDismiss)
}

// MARK: Section furniture

/**
 * A section's head — and, for the room you are in, the room's own name beside
 * it, so "This room" is never a question.
 *
 * The hairline under it has gone. Four ruled lines down one screen is the
 * church-bulletin energy §13 forbids in its most literal form, and the edge
 * of the tile below now does the dividing. [SectionLabel] is the same head
 * the settings screens use, so the menu and the five screens it pushes are
 * plainly one place.
 */
@Composable
private fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    SectionLabel(title = title, detail = detail, modifier = modifier)
}

/**
 * One row of the menu: a thing you go to, or a thing you do.
 *
 * A tile now, like everything else you can press in this app — surface,
 * grain, a large corner, and a press that gives under the finger. The bare
 * 17 sp line on a black ground it used to be is what made the menu read as a
 * list of words rather than as a set of doors.
 */
@Composable
private fun MenuRow(
    title: String,
    subtitle: String? = null,
    // Last, so the trailing-lambda form binds the click handler rather than
    // the subtitle — the same trap QuietControl's own signature names.
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 58.dp)
            .pressablePaper(RibbonShape.rowShape, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
            if (subtitle != null) {
                Text(text = subtitle, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        Chevron()
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
            .sizeIn(minHeight = 64.dp)
            .pressablePaper(RibbonShape.rowShape, role = Role.Button, onClick = onClick)
            // The chartreuse mark is the only drawn sign of which room you
            // are in, and colour is never the only signal (§11), so the same
            // fact is carried in the row's state for a screen reader — which
            // announces it as selected, and says no number about it.
            .semantics { selected = isCurrent }
            .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 8.dp)
                .width(MarkWidth)
                .height(MarkHeight)
                .clip(RoundedCornerShape(MarkWidth / 2))
                .background(if (isCurrent) Palette.accent else Color.Transparent),
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
            Box(Modifier.semantics { contentDescription = Copy.fireIs(state.displayName) }) {
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
 * You: your face, and your name under it.
 *
 * It was a wide tile with a circle at one end and a word beside it, and most
 * of it was empty. Two things were wrong and only one of them was the space.
 *
 * The first: nothing said either half was a control. A portrait you can
 * change and a name you can edit looked exactly like a portrait and a name,
 * so the only way to find out was to press them and see. The line underneath
 * now says what they are for, and the small caps under the face says it is a
 * control — which is the app's own idiom for a quiet one everywhere else.
 *
 * The second, and the reason it is centred: this is the one screen in the
 * app that is *about you*, and a row is how you list a setting, not how you
 * show somebody themselves. Presence is faces (§4.2) and the portrait is
 * "close to required" (§03) — so on your own screen your face is the subject
 * rather than an accessory to a text field.
 *
 * What keeps it from being a profile page, which §13 would not have: nothing
 * is counted. No rooms joined, no books finished, no member-since, no badge.
 * A face, a name, and one sentence about where they are seen.
 */
@Composable
private fun YouIdentity(model: AppModel) {
    // Saveable, not remembered: on Android the menu root *is* a NavHost
    // destination, so pushing one of the settings screens disposes it and
    // popping back would otherwise throw away a half-typed name. iOS keeps
    // its root composed under a push and needs none of this.
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }

    /**
     * Keep what was typed, wherever the edit ended.
     *
     * The field used to commit in exactly one place — the keyboard's Done key
     * — so tapping the face beside it, tapping anything that took focus,
     * pressing back, closing the menu or pushing a settings screen all threw
     * the edit away with nothing said. S18 calls the name "editable in place",
     * and an in-place edit that only one specific soft-keyboard key can land
     * is not one. It is also the most load-bearing field in the app: the name
     * is the one thing S17 will not let a person skip, and it is what their
     * partner sees on every seat and every note.
     *
     * An empty name reverts silently. A person cannot delete their own name,
     * and §10.1's unbothered interface does not scold them for trying.
     */
    fun commitName() {
        if (!editingName) return
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) model.updateMe(name = trimmed)
        editingName = false
    }

    // An edit still in flight when this screen is disposed — the menu closed,
    // a settings screen pushed over it — lands rather than evaporating.
    DisposableEffect(Unit) { onDispose { commitName() } }

    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val hasFace = model.me?.let { model.portrait(it.id) } != null

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

    // **Left, like the rest of the screen.** This was a centred island: an
    // 88 dp circle in the middle, a small-caps line under it, the name under
    // that and a sentence under that — four things stacked on an axis nothing
    // else on You uses, above three sections that are all flush with the
    // margin. The heading above it is left too. That is most of what "the
    // profile area isn't designed very well" is: not the pieces, the axis.
    //
    // The face still opens the screen and is still the largest thing on it —
    // the reasoning that put it here is untouched — it simply stands beside
    // the name rather than above it, which is also how a person appears
    // everywhere else in this app: a seat at the hearth, a row in the rooms
    // sheet, the head of their own screen.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    portraitPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                // Swift's `.accessibilityLabel(Copy.addAPortrait)`, which
                // replaces the label rather than adding to it: the control is
                // the way to a portrait, so the portrait's own name is
                // cleared beneath it. What it says depends on whether there
                // is a face behind it — it should not go on saying "add" to
                // somebody who has one.
                .semantics {
                    contentDescription = if (hasFace) {
                        Copy.CHANGE_YOUR_PORTRAIT
                    } else {
                        Copy.ADD_A_PORTRAIT
                    }
                },
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        // Your name becomes the field it is edited in, in the same place, at
        // the same size. Two identical lines of type trading places on one
        // frame reads as a flinch; a cross-fade reads as the one becoming the
        // other, which is what it is.
        AnimatedContent(
            targetState = editingName,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.arrive(reduceMotion))
            },
            label = "your-name",
        ) { editing ->
            if (editing) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
                // The prompt is drawn *behind* the caret rather than above the
                // field, which is SwiftUI's `prompt:` and what the room's own
                // name field one section down already does.
                Box(contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = RibbonType.display(26f).copy(color = Palette.text),
                    cursorBrush = SolidColor(Palette.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTarget)
                        .focusRequester(focus)
                        .onFocusChanged { if (!it.isFocused) commitName() }
                        // With no prompt drawn when the field is empty there
                        // is nothing on screen to take a label from, and this
                        // was the one typing surface in the app with neither
                        // (§11, and A25 found the same class of defect across
                        // onboarding's three fields).
                        .semantics { contentDescription = Copy.YOUR_NAME },
                )
                // Cleared of its text the field was a bare caret. The room's
                // own name field one section down already draws its prompt
                // this way; this is the same, at the same size, so the prompt
                // sits exactly where the text will.
                if (name.isEmpty()) {
                    Text(
                        text = Copy.YOUR_NAME,
                        style = RibbonType.ui(18f),
                        color = Palette.muted,
                    )
                }
                }
            } else {
                Box(
                    // A short name draws a short word, and the word is the
                    // whole control: the target keeps its 44 dp in both
                    // directions so "Jo" is no harder to tap than "Jonathan"
                    // (§11, deviation 12).
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = MinTarget)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = Copy.EDITS_YOUR_NAME,
                        ) {
                            name = model.me?.name ?: ""
                            editingName = true
                        },
                    // Start, now that the block runs along the screen's own
                    // axis: a name centred in the space left over beside a
                    // portrait lands in a different place for every length of
                    // name, which is the one thing worse than being centred.
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = model.me?.name ?: "",
                        style = RibbonType.display(26f),
                        color = Palette.text,
                    )
                }
            }
        }

        // The face's own label, and why any of it is asked for, in one line
        // rather than two.
        //
        // It used to be both: a small-caps "Add a portrait" under the circle
        // *and* a sentence under the name saying the room is the only place
        // either is ever seen. Beside each other rather than stacked, the
        // small-caps line is the one that has to go — it labelled a control
        // that is now plainly a face you can touch, and it was cleared from
        // the screen reader anyway because the portrait already carried the
        // action. The sentence stays, because it is the part that makes a
        // portrait read as a courtesy rather than as a profile field, and it
        // takes the tap as its own label so §11's tap-equivalent survives the
        // line it used to live on.
        Text(
            text = if (hasFace) Copy.YOUR_FACE_REASON else Copy.ADD_A_PORTRAIT_REASON,
            style = RibbonType.ui(14f),
            color = Palette.muted,
        )
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
    // Saveable for the same reason the name above is.
    var editingRoomName by rememberSaveable { mutableStateOf(false) }
    var roomName by rememberSaveable { mutableStateOf("") }
    var showInkPicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The row becomes the field, in place — the same cross-fade your own
        // name gets one section up, for the same reason.
        AnimatedContent(
            targetState = editingRoomName,
            transitionSpec = {
                fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.arrive(reduceMotion))
            },
            label = "the-room-name",
        ) { editing ->
            if (editing) {
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
                            // The prompt belongs to the field, which says its
                            // own name below — the same shape the name field
                            // one section up already keeps.
                            modifier = Modifier.clearAndSetSemantics {},
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .semantics { contentDescription = Copy.ROOM_NAME },
                    )
                }
            } else {
                MenuRow(Copy.NAME_THIS_ROOM) {
                    roomName = room.name ?: ""
                    editingRoomName = true
                }
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
private fun AccountSection(model: AppModel, onDelete: () -> Unit) {
    // Saveable: a code already emailed must still be being waited for after
    // a push and a pop.
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var passkeyLine by rememberSaveable { mutableStateOf<String?>(null) }

    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    // Nothing at all, heading included. This is a build with no backend
    // configured, and the section used to draw its heading anyway: "Your
    // account" over a gap, with "Delete account" hanging under it offering to
    // delete an account that cannot exist. A heading over nothing is the
    // clearest kind of design defect and it was on the screen the owner said
    // was not designed well.
    if (model.remote == null) return

    fun addPasskey() {
        val host = activity ?: return
        passkeyLine = null
        // The model's scope, not the composition's. A ceremony launched from
        // `rememberCoroutineScope` dies with the screen — and a rotation, a
        // pushed settings screen or the menu being closed all kill this
        // screen. The credential is made on the authenticator *before* the
        // verify call goes out, so a cancellation in that window leaves a
        // passkey on the phone that the account has never heard of, offered
        // at every future sign-in and refused every time. The ceremony has to
        // outlive the screen that started it.
        //
        // The line it writes afterwards may land on a composition that has
        // gone, which costs nothing: the work that mattered finished.
        model.viewModelScope.launch {
            passkeyLine = try {
                model.registerPasskey(host)
                Copy.PASSKEY_ADDED
            } catch (_: Passkeys.Cancelled) {
                // Dismissed the sheet. Nothing happened, and nothing is said.
                null
            } catch (_: Throwable) {
                Copy.PASSKEY_WASNT_ADDED
            }
        }
    }

    // Three states, and until now they traded places on a single frame:
    // tapping Sign in replaced a control and a sentence with the whole inline
    // form, and signing out replaced the form with them again — the section
    // changing height under your thumb with nothing moving. What it says
    // cross-fades, and the section grows or shrinks to fit.
    //
    // The heading sits *outside* the cross-fade. It is the one thing here
    // that does not change between the states, and a heading that fades out
    // and back in while the rows beneath it change is a heading drawing
    // attention to itself for no reason.
    val phase = when {
        model.isSignedIn -> AccountPhase.signedIn
        signingIn -> AccountPhase.signingIn
        else -> AccountPhase.signedOut
    }

    // The gap above the section is the section's own, so that a build with no
    // backend loses the space along with everything else rather than leaving
    // a hole where the account used to be.
    Air(SectionGap)
    SectionLabel(Copy.YOUR_ACCOUNT)
    Air(10.dp)

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (
                fadeIn(RibbonMotion.settle(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.settle(reduceMotion))
                ).using(
                SizeTransform(clip = false) { _, _ -> RibbonMotion.settle(reduceMotion) },
            )
        },
        label = "your-account",
    ) { shown ->
        when (shown) {
            // **Tiles, like everything else on this screen.** The account was
            // the one section built out of loose quiet controls and bare
            // lines of type — an address in small caps, then two underlined
            // words, then a sentence — while Text, Appearance and Downloads
            // directly above it were all grouped rows with a title and a
            // subtitle. It did not look unfinished by accident; it was the
            // only part of You that had never been given the rest of the
            // screen's language.
            AccountPhase.signedIn -> {
                // `canAddAPasskey`, not `passkeysAvailable`: the project
                // having passkeys on is only half of it, and the other half
                // is which kind of session this is. See A49 — an Auth0
                // session cannot register one, and Auth0 is the first way in
                // this build offers, so the row used to be drawn for most
                // people and fail for all of them.
                val passkey = model.canAddAPasskey && activity != null
                SettingsGroup(
                    count = if (passkey) 3 else 2,
                    // Only after something has been attempted. The reason a
                    // passkey is worth having is the row's own subtitle; a
                    // footnote saying it again would be the screen explaining
                    // itself twice.
                    footnote = passkeyLine,
                    // And it is announced when it appears. This line is the
                    // only thing on You that changes because of something the
                    // person just did, with nothing taking focus and nothing
                    // else moving — so without this a screen reader is told
                    // neither that the passkey was added nor that it was not.
                    // §11's rule that colour is never alone has a twin: a
                    // result is never silent.
                    footnoteAnnounces = true,
                ) {
                    SettingValue(
                        title = model.accountEmail ?: Copy.YOUR_EMAIL,
                        subtitle = Copy.ACCOUNT_REASON,
                    )
                    if (passkey) {
                        Setting(
                            title = Copy.ADD_A_PASSKEY,
                            subtitle = Copy.PASSKEY_REASON,
                            // Nothing is pushed: the system's own sheet comes
                            // up over this screen, so a chevron would be
                            // promising a place to go.
                            chevron = false,
                            onClick = { addPasskey() },
                        )
                    }
                    Setting(
                        title = Copy.SIGN_OUT,
                        chevron = false,
                        onClick = {
                            signingIn = false
                            // Signing out must finish whatever happens to
                            // this screen.
                            model.viewModelScope.launch { model.signOutRemote() }
                        },
                    )
                }
            }

            AccountPhase.signingIn -> SignInInline(
                model = model,
                onSignedIn = { signingIn = false },
                onCancel = { signingIn = false },
            )

            AccountPhase.signedOut -> SettingsGroup(
                count = 1,
                footnote = Copy.ACCOUNT_REASON,
            ) {
                Setting(
                    title = Copy.SIGN_IN,
                    chevron = false,
                    onClick = { signingIn = true },
                )
            }
        }
    }

    // Quiet, and kept quiet: §6.8's one destructive act does not get a tile,
    // because a tile is an invitation. Air above it so it is not read as the
    // last row of the group it is not part of.
    Air(18.dp)
    QuietControl(
        title = Copy.DELETE_ACCOUNT,
        modifier = Modifier.offset(x = QuietControlInset),
        onClick = onDelete,
    )
}

/**
 * What the account section is showing. One of three, and it eases between
 * them.
 *
 * There used to be a fourth, `noAccounts`, which drew nothing — under a
 * heading that drew itself anyway. [AccountSection] returns before any of
 * this now, so the case has no state to be in.
 */
private enum class AccountPhase { signedIn, signingIn, signedOut }

/**
 * The update card (§A-OTA): one card, four things it can say.
 *
 * It used to be four cards, written out four times, each appearing and
 * vanishing on the frame its state changed — so *found*, then *downloading*,
 * then *ready* read as three separate panels flickering in the same place
 * rather than one panel getting further along. It is one card now: it grows in
 * when there is something to say and shrinks away when there isn't, and what it
 * says cross-fades in place while the card itself stays put.
 *
 * Nothing is drawn at all when there is no update and while one is being looked
 * for: an update is not news until there is one (§6.1).
 */
@Composable
private fun UpdateSection(model: AppModel) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val settleSize: FiniteAnimationSpec<IntSize> = RibbonMotion.settle(reduceMotion)

    val state = model.updateState
    val hasNews = state !is UpdateState.Idle && state !is UpdateState.Checking

    AnimatedVisibility(
        visible = hasNews,
        enter = fadeIn(settle) + expandVertically(settleSize),
        exit = fadeOut(settle) + shrinkVertically(settleSize),
        label = "the-update",
    ) {
        // Held across the leaving animation: `AnimatedVisibility` recomposes its
        // content while it goes, and by then the update has been dismissed and
        // there is nothing left to draw. The same hold the banked-fire line on
        // the room uses, for the same reason.
        var shown by remember { mutableStateOf(state) }
        LaunchedEffect(state) { if (hasNews) shown = state }

        UpdateCard(
            state = shown,
            reduceMotion = reduceMotion,
            onSkip = {
                if (shown is UpdateState.Error) {
                    model.dismissUpdateError()
                } else {
                    model.dismissUpdate()
                }
            },
            onUpdate = {
                if (shown is UpdateState.Error) {
                    model.dismissUpdateError()
                    model.checkForUpdates()
                } else {
                    model.triggerUpdate(context)
                }
            },
        )
    }
}

/**
 * The card itself: a line, and under it either the two controls or the bar.
 *
 * A download has nothing to decide, so it is given no controls — "Update now"
 * over a bar already doing it would be a control that does nothing.
 */
@Composable
private fun UpdateCard(
    state: UpdateState,
    reduceMotion: Boolean,
    onSkip: () -> Unit,
    onUpdate: () -> Unit,
) {
    val downloading = state as? UpdateState.Downloading

    // Held across the leaving fade, the way every other departing thing in this
    // app holds its last value. The bar's own half of the cross-fade is
    // recomposed as it goes, and by then the download is over and `downloading`
    // is null — so a bar reading the live value would be handed nothing and ease
    // itself back down to the stub. The download *completing* would look like
    // the download being undone.
    var lastProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(downloading) { downloading?.let { lastProgress = it.progress } }

    val line: UpdateLine = when (state) {
        is UpdateState.Available ->
            UpdateLine(Copy.updateAvailable(state.info.versionName), Palette.text, 13f)

        is UpdateState.Downloading ->
            UpdateLine(Copy.updateDownloading((state.progress * 100).toInt()), Palette.muted, 12f)

        is UpdateState.ReadyToInstall ->
            UpdateLine(Copy.UPDATE_READY_TO_INSTALL, Palette.chartreuse, 13f)

        is UpdateState.Error ->
            UpdateLine(Copy.UPDATE_FAILED, Palette.muted, 12f)

        // Never drawn: the card is not composed without news. Said rather than
        // defaulted, so adding a state to `UpdateState` fails here loudly.
        is UpdateState.Idle, is UpdateState.Checking ->
            UpdateLine("", Palette.muted, 12f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UpdateCardRadius))
            .background(Palette.surface)
            .border(1.dp, Palette.rule, RoundedCornerShape(UpdateCardRadius))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The line is cross-faded on the words rather than on the state, so a
        // download reporting itself a hundred times on the way down is a
        // hundred separate one-percent cross-fades of one number and never a
        // new card. Found → downloading → ready is the same card, changing what
        // it says.
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.arrive(reduceMotion))
            },
            label = "the-update-line",
        ) { said ->
            // The colour and the size travel *with* the words rather than being
            // read from the live state, so the half on its way out keeps the
            // voice it was written in — chartreuse "Ready to install" fading out
            // as muted grey would be the wrong sentence in the wrong colour.
            SmallCaps(said.words, color = said.color, size = said.size)
        }

        AnimatedContent(
            targetState = downloading != null,
            transitionSpec = {
                val arrive = RibbonMotion.arrive<Float>(reduceMotion)
                (fadeIn(arrive) togetherWith fadeOut(arrive)).using(
                    SizeTransform(clip = false) { _, _ -> RibbonMotion.settle(reduceMotion) },
                )
            },
            label = "the-update-controls",
        ) { bar ->
            if (bar) {
                UpdateBar(progress = lastProgress, reduceMotion = reduceMotion)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietControl(
                        title = Copy.SKIP_PORTRAIT,
                        color = Palette.muted,
                        onClick = onSkip,
                    )
                    Spacer(Modifier.width(12.dp))
                    QuietControl(
                        title = Copy.UPDATE_NOW,
                        color = Palette.chartreuse,
                        onClick = onUpdate,
                    )
                }
            }
        }
    }
}

/** What the update card says, and the voice it says it in. */
private data class UpdateLine(val words: String, val color: Color, val size: Float)

/** How far along a download is. */
@Composable
private fun UpdateBar(progress: Float, reduceMotion: Boolean) {
    // Eased to, not stepped to: a downloader reports itself in lumps, and a bar
    // that lurches from lump to lump reads as a bar that has stopped in between
    // them.
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = RibbonMotion.arrive(reduceMotion),
        label = "update-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UpdateBarHeight)
            .clip(RoundedCornerShape(UpdateBarHeight / 2))
            .background(Palette.rule),
    ) {
        Box(
            modifier = Modifier
                // A bar at nothing at all says the download has not begun; it
                // has.
                .fillMaxWidth(shown.coerceAtLeast(0.02f))
                .height(UpdateBarHeight)
                .clip(RoundedCornerShape(UpdateBarHeight / 2))
                .background(Palette.chartreuse),
        )
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
            BackChevron(
                onBack = onBack,
                label = Copy.BACK,
                modifier = Modifier.padding(start = Margin - 12.dp, top = 8.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .readableColumn(),
                // `Spacer(1f); content; Spacer(2f)` — the block sits a third
                // of the way down, which is where onboarding's paste field
                // and both join flows put theirs. Centring it here was this
                // screen's own invention.
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
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
                Spacer(Modifier.weight(2f))
            }
        }
    }
}
