@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.app

import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.reading.ReadingScreen
import app.readribbon.screens.EmberRecordScreen
import app.readribbon.screens.InviteSheet
import app.readribbon.screens.JoinFlow
import app.readribbon.screens.NewRoomSheet
import app.readribbon.screens.OnboardingFlow
import app.readribbon.screens.PersonScreen
import app.readribbon.screens.RoomScreen
import app.readribbon.screens.RoomsSheet
import app.readribbon.screens.YouSheet
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Cold start goes to the room you were last in. No splash screen, no
// "welcome back," no interstitial — the app opening on the room is the app
// saying nothing, which is correct (§05). The fastest path from launch to
// Scripture is the product (§6.2).
//
// Swift's `@main struct RibbonApp: App` is a Scene; on Android the scene is
// MainActivity, so this file is the two halves of the Swift one: the model's
// loading, the buffered link and the lifecycle refresh (Swift's
// `WindowGroup`), and the navigation, the sheets and the book (Swift's
// `RootView`).

/**
 * A push to someone in a room (S12) — from a portrait, anywhere.
 *
 * Swift pushes the value itself onto a `NavigationPath` and matches it with
 * `navigationDestination(for: PersonRoute.self)`. A navigation-compose route
 * is a string, so the same two fields travel as path segments: [path] writes
 * one, [from] reads one back. `Hashable` is `data class` here.
 */
data class PersonRoute(val personID: Uuid, val roomID: Uuid) {

    /** This route as the NavHost spells it. */
    val path: String get() = "person/$personID/$roomID"

    companion object {
        /** The pattern the destination is registered under. */
        const val PATTERN: String = "person/{personID}/{roomID}"

        const val PERSON_ID: String = "personID"
        const val ROOM_ID: String = "roomID"

        /**
         * The route the back stack entry is carrying, or null if either id
         * is missing or unparseable — which can only happen if something
         * outside the app navigated here.
         */
        fun from(arguments: Bundle?): PersonRoute? {
            val person = arguments?.getString(PERSON_ID)?.let { uuid(it) } ?: return null
            val room = arguments.getString(ROOM_ID)?.let { uuid(it) } ?: return null
            return PersonRoute(personID = person, roomID = room)
        }
    }
}

private fun uuid(raw: String): Uuid? = runCatching { Uuid.parse(raw) }.getOrNull()

/**
 * The room's stack. Swift's `NavigationStack` has one root and two value
 * destinations; these are the same three, as routes.
 */
private object Route {
    const val ROOM = "room"
    const val EMBER_PATTERN = "ember/{readingID}"
    const val READING_ID = "readingID"

    fun ember(readingID: Uuid): String = "ember/$readingID"
}

/**
 * Where the loaded store lives across a configuration change.
 *
 * Swift keeps the model in `@State` on the `WindowGroup`, and a SwiftUI
 * scene is not torn down and rebuilt when the device rotates. An Activity
 * is, so a plain `remember` would reload `AppState` from disk on every
 * rotation and blank the room while it did — a splash by another name,
 * which §05 forbids. A retained `ViewModel` is the Android equivalent of
 * the scene-lived `@State`, and it holds nothing but the one reference.
 *
 * It deliberately does not clear the model in `onCleared`: `AppModel` owns a
 * persistence scope that is meant to outlive the screen (a save started as
 * the app goes away is exactly the save that must not be cancelled), and the
 * store is process-lived on both platforms.
 */
internal class RootHolder : ViewModel() {
    var model: AppModel? by mutableStateOf(null)
}

/**
 * The app root: load the store, then draw either the way in or the room.
 *
 * @param links tapped invite links, in the order they arrive —
 *   `readribbon.app/i/<token>` through the verified App Link,
 *   `ribbon://i/<token>` as the plain-scheme fallback (S16). Swift's
 *   `.onOpenURL`; on Android the intent belongs to the Activity, so it is
 *   handed down as a stream. A link that arrives while the app is open sets
 *   `pendingInvite` and nothing else — the join rides over whatever the
 *   person is doing and never switches the room underneath them.
 * @param onReady the state is in and the room is about to draw. MainActivity
 *   holds the unlit launch ground until this is called; there is nothing to
 *   show in the meantime and nothing that would be honest to show.
 */
@Composable
fun RibbonRoot(
    links: Flow<Uri>,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val holder: RootHolder = viewModel()
    val model = holder.model

    /**
     * A URL that arrived before the model finished loading — the normal case
     * when tapping an invite link cold-starts the app (S16).
     */
    var bufferedURL by remember { mutableStateOf<Uri?>(null) }

    // An invite link, tapped. Swift's `.onOpenURL`, with the same two cases:
    // hand it straight over, or hold it for the model that is still loading.
    LaunchedEffect(links) {
        links.collect { url ->
            val loaded = holder.model
            if (loaded != null) loaded.handleInviteURL(url) else bufferedURL = url
        }
    }

    LaunchedEffect(Unit) {
        if (holder.model == null) {
            val loaded = AppModel.load(context)
            bufferedURL?.let { url ->
                loaded.handleInviteURL(url)
                bufferedURL = null
            }
            holder.model = loaded
        }
        onReady()
    }

    // The room renders from local state instantly; the backend catches up
    // behind it.
    //
    // Swift refreshes twice over: once from `.task` at launch and again
    // whenever `scenePhase` becomes `.active`. Resuming is the Android name
    // for both of those, so one rule covers them — and a pull in flight when
    // the app goes away is cancelled with the scope rather than finishing
    // into a screen nobody is looking at.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (model != null) {
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.refreshFromRemote()
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (model == null) {
            // One frame of the unlit ground while state loads from disk —
            // indistinguishable from the launch screen.
            Box(Modifier.fillMaxSize().room())
        } else {
            RootContent(model)
        }
    }
}

/**
 * Swift's `RootView`: the way in or the room, and everything the room can
 * put on top of itself.
 *
 * `.preferredColorScheme(.dark)` has no counterpart here — `RibbonTheme` has
 * no light scheme to be asked not to use (§12).
 */
@Composable
private fun RootContent(model: AppModel) {
    /**
     * Once the way in is showing it stays showing until it says it is done,
     * so that creating the person mid-flow does not yank the screen away
     * under the invite step. Swift's `@State private var onboarding` with
     * `.onAppear { onboarding = true }`.
     */
    var onboarding by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()
    val settle: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    // Swift's `.animation(RibbonMotion.settle, value: onboarding)` over a
    // `Group` of two branches is a cross-fade on the settle token. Under
    // reduce motion it is a cut (§11).
    //
    // The test is Swift's `isOnboardedPerson` — a person exists — and not
    // `AppModel.isOnboarded`, which also asks for a room. A person who left
    // their last room has no room and must not be walked through a second
    // onboarding for it; they get a fresh room of one, below (§6.8).
    Crossfade(
        targetState = onboarding || model.me == null,
        animationSpec = settle,
        label = "the-way-in",
    ) { wayIn ->
        if (wayIn) {
            LaunchedEffect(Unit) { onboarding = true }
            OnboardingFlow(model = model, onDone = { onboarding = false })
        } else {
            RoomStack(model)
        }
    }
}

/**
 * The room and its stack: Swift's `roomStack`.
 *
 * The book is drawn over the whole stack rather than pushed onto it. It is a
 * full-screen cover in spirit, but drawn in-tree so the closing drag settles
 * like a book (S02) — sliding down over the room rather than a system sheet
 * — and so a note tapped on a person's screen opens the book over *that*
 * screen, as it does on iOS.
 */
@Composable
private fun RoomStack(model: AppModel) {
    val room: Room? = model.currentRoom
    if (room == null) {
        // A person with no rooms (left their last one): a fresh room of one,
        // quietly — reading continues (§6.8).
        Box(Modifier.fillMaxSize().room())
        LaunchedEffect(Unit) {
            if (model.me != null) model.createRoom(name = null)
        }
    } else {
        RoomStack(model = model, room = room)
    }
}

/** The stack itself, once there is a room for it to be about. */
@Composable
private fun RoomStack(model: AppModel, room: Room) {
    var openReading by remember { mutableStateOf<Reading?>(null) }

    /**
     * Where the reading should open, when a row or a quoted verse named a
     * place (§6.3, S11). Null means your own position.
     */
    var openTarget by remember { mutableStateOf<VerseAddress?>(null) }
    var showRooms by remember { mutableStateOf(false) }
    var showYou by remember { mutableStateOf(false) }
    var showNewRoom by remember { mutableStateOf(false) }

    /**
     * A just-created room whose invite half is due (S15 — naming and
     * inviting are two steps that should feel like one).
     */
    var inviteRoom by remember { mutableStateOf<Room?>(null) }

    /** The finishing sequence's "Start another" lands in the chooser (S13). */
    var chooserRequested by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val reduceMotion = rememberReduceMotion()

    val arrive: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut)
    val settle: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)
    val slide: FiniteAnimationSpec<IntOffset> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    fun openBook(reading: Reading, target: VerseAddress?) {
        openTarget = target
        openReading = reading
    }

    fun closeBook() {
        openReading = null
        openTarget = null
    }

    fun openPerson(personID: Uuid, roomID: Uuid) {
        navController.navigate(PersonRoute(personID = personID, roomID = roomID).path)
    }

    Box(Modifier.fillMaxSize()) {
        // Predictive back (§12.2): the room peels in behind a closing screen.
        // The pop transitions below are what the system gesture drives, so
        // the peel is Android's own and not a back button we drew — which is
        // the one place the platform gesture beats anything we would design.
        // Swift hides the navigation bar entirely (`.toolbarVisibility`);
        // a NavHost draws no chrome of its own, so there is none to hide.
        NavHost(
            navController = navController,
            startDestination = Route.ROOM,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { slideInHorizontally(slide) { it / 4 } + fadeIn(settle) },
            exitTransition = { fadeOut(settle) },
            popEnterTransition = { fadeIn(settle) },
            popExitTransition = { slideOutHorizontally(slide) { it / 4 } + fadeOut(settle) },
        ) {
            composable(
                Route.ROOM,
                // The room never slides: it is what the app opened onto.
                enterTransition = { EnterTransition.None },
                exitTransition = { fadeOut(settle) },
                popEnterTransition = { fadeIn(settle) },
                popExitTransition = { ExitTransition.None },
            ) {
                // Swift switches rooms inside `withAnimation(RibbonMotion.arrive)`,
                // and the arrive token's own note calls this out: "cross-fades
                // between rooms". Keyed on the id so a rename crosses nothing.
                AnimatedContent(
                    targetState = room,
                    contentKey = { it.id },
                    transitionSpec = {
                        (fadeIn(arrive) togetherWith fadeOut(arrive))
                            .using(SizeTransform(clip = false))
                    },
                    label = "the-room",
                ) { current ->
                    RoomScreen(
                        model = model,
                        room = current,
                        chooserRequested = chooserRequested,
                        onChooserHandled = { chooserRequested = false },
                        onOpenReading = { reading, target -> openBook(reading, target) },
                        onOpenRooms = { showRooms = true },
                        onYou = { showYou = true },
                        onOpenPerson = { personID, roomID -> openPerson(personID, roomID) },
                        onOpenEmber = { readingID -> navController.navigate(Route.ember(readingID)) },
                    )
                }
            }

            composable(
                Route.EMBER_PATTERN,
                arguments = listOf(navArgument(Route.READING_ID) { type = NavType.StringType }),
            ) { entry ->
                val readingID = entry.arguments?.getString(Route.READING_ID)?.let { uuid(it) }
                val reading = model.state.readings.firstOrNull { it.id == readingID }
                if (reading == null) {
                    // Swift's `if let` falls through to an empty view. There
                    // is nothing under a NavHost destination to show through,
                    // so the nearest honest thing is the unlit ground — the
                    // same nothing the room-less branch draws.
                    Box(Modifier.fillMaxSize().room())
                } else {
                    EmberRecordScreen(
                        model = model,
                        reading = reading,
                        onOpenVerse = { verse ->
                            // A quoted verse opens the reading at that verse
                            // (S11) — the finished book's own pages, not a
                            // copy.
                            openBook(reading, verse)
                        },
                        onReadAgain = { bookID ->
                            navController.popBackStack(Route.ROOM, inclusive = false)
                            val new = model.startReading(bookID = bookID, room = room)
                            openBook(new, null)
                        },
                        onOpenPerson = { personID, roomID -> openPerson(personID, roomID) },
                    )
                }
            }

            composable(
                PersonRoute.PATTERN,
                arguments = listOf(
                    navArgument(PersonRoute.PERSON_ID) { type = NavType.StringType },
                    navArgument(PersonRoute.ROOM_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val route = PersonRoute.from(entry.arguments)
                val personRoom = route?.let { r -> model.state.rooms.firstOrNull { it.id == r.roomID } }
                if (route == null || personRoom == null) {
                    Box(Modifier.fillMaxSize().room())
                } else {
                    PersonScreen(
                        model = model,
                        personID = route.personID,
                        room = personRoom,
                        onOpenVerse = { verse, readingID ->
                            // The note names its reading — a finished book's
                            // note opens that book, not the open one.
                            val reading = model.state.readings.firstOrNull { it.id == readingID }
                            if (reading != null) openBook(reading, verse)
                        },
                        onDismiss = { navController.popBackStack() },
                    )
                }
            }
        }

        // The book, over the stack. Swift's `.overlay` with
        // `.transition(.asymmetric(insertion: .opacity, removal:
        // .move(edge: .bottom).combined(with: .opacity)))`: it arrives by
        // fading in on the arrive token and leaves by sliding down off the
        // bottom on the settle token.
        //
        // `AnimatedContent` rather than `AnimatedVisibility` because the
        // book has to stay drawn while it slides away, and the reading it is
        // drawing is the state that just went null.
        //
        // Composing after the NavHost is load-bearing: back callbacks are
        // taken in reverse order of registration, so the reading's own
        // predictive back — which peels the book off the room — wins over
        // the NavHost's for as long as the book is open.
        AnimatedContent(
            targetState = openReading,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it?.id },
            transitionSpec = {
                val transform = if (targetState != null) {
                    fadeIn(arrive) togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith
                        (slideOutVertically(slide) { it } + fadeOut(settle))
                }
                transform.using(SizeTransform(clip = false))
            },
            label = "the-book",
        ) { book ->
            if (book != null) {
                ReadingScreen(
                    model = model,
                    room = room,
                    reading = book,
                    onClose = { closeBook() },
                    onFinished = { closeBook() },
                    onStartAnother = {
                        closeBook()
                        chooserRequested = true
                    },
                    openAt = openTarget,
                )
            }
        }

        // The sheets. Swift's four `.sheet` modifiers hang off the same
        // container the book does, so they present over the book as well as
        // over the room; these are drawn last for the same reason.

        if (showRooms) {
            RoomsSheet(
                model = model,
                onSwitch = { roomID -> model.switchRoom(roomID) },
                onStartRoom = {
                    showRooms = false
                    showNewRoom = true
                },
                onYou = {
                    showRooms = false
                    showYou = true
                },
                onDismiss = { showRooms = false },
            )
        }

        if (showNewRoom) {
            // Naming and inviting are two steps that should feel like one
            // (S15) — the invite sheet follows the naming sheet.
            NewRoomSheet(
                model = model,
                onCreated = { newRoom -> inviteRoom = newRoom },
                onDismiss = { showNewRoom = false },
            )
        }

        inviteRoom?.let { newRoom ->
            // Swift asks for `.presentationDetents([.medium])`; the sheet
            // sizes its own content to that fraction, so there is no detent
            // to ask for here.
            InviteSheet(
                room = newRoom,
                model = model,
                onDismiss = { inviteRoom = null },
            )
        }

        if (showYou) {
            YouSheet(model = model, onDismiss = { showYou = false })
        }

        model.pendingInvite?.let { pending ->
            // A tapped invite while already onboarded: the join flow rides
            // over the room (S16). The key keeps a second link from
            // inheriting the first one's half-finished state — Swift's
            // `.id(pending.token)`, and here it discards the sheet's own
            // state with it.
            key(pending.token) {
                ModalBottomSheet(
                    onDismissRequest = { model.pendingInvite = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    // `.presentationBackground(Palette.ground)`. The join
                    // flow lays its own grain over it, as a room does.
                    containerColor = Palette.ground,
                    contentColor = Palette.text,
                    dragHandle = null,
                    // The flow clears the system bars itself, so the ground
                    // runs to the very edges of the sheet.
                    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                ) {
                    JoinFlow(
                        token = pending.token,
                        model = model,
                        onDone = { model.pendingInvite = null },
                        onDismiss = { model.pendingInvite = null },
                    )
                }
            }
        }
    }
}
