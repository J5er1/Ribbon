@file:OptIn(
    ExperimentalUuidApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)

package app.readribbon.app

import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.readribbon.core.Reading
import app.readribbon.services.Destination
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.LocalFlowLayer
import app.readribbon.design.LocalFlowRoot
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.peeled
import app.readribbon.design.rememberBookSheet
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.rememberSheetExit
import app.readribbon.design.room
import app.readribbon.reading.ReadingScreen
import app.readribbon.screens.EmberRecordScreen
import app.readribbon.screens.JoinFlow
import app.readribbon.screens.MenuEntry
import app.readribbon.screens.MenuScreen
import app.readribbon.screens.OnboardingFlow
import app.readribbon.screens.PersonScreen
import app.readribbon.screens.RoomScreen
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
// `WindowGroup`), and the navigation, the menu and the book (Swift's
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
    destinations: Flow<Destination> = emptyFlow(),
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

    /** The same, for a notification tapped while the app was cold (S19). */
    var bufferedDestination by remember { mutableStateOf<Destination?>(null) }

    // An invite link, tapped. Swift's `.onOpenURL`, with the same two cases:
    // hand it straight over, or hold it for the model that is still loading.
    LaunchedEffect(links) {
        links.collect { url ->
            val loaded = holder.model
            if (loaded != null) loaded.handleInviteURL(url) else bufferedURL = url
        }
    }

    // A tapped notification, on the same two terms as a link: hand it over,
    // or hold it for a model that is still loading. Cold-started by a
    // notification is the *ordinary* case for this stream rather than the
    // edge — a notification is tapped precisely when the app is not running.
    LaunchedEffect(destinations) {
        destinations.collect { destination ->
            val loaded = holder.model
            if (loaded != null) {
                loaded.pendingDestination = destination
            } else {
                bufferedDestination = destination
            }
        }
    }

    LaunchedEffect(Unit) {
        if (holder.model == null) {
            val loaded = AppModel.load(context)
            bufferedURL?.let { url ->
                loaded.handleInviteURL(url)
                bufferedURL = null
            }
            bufferedDestination?.let { destination ->
                loaded.pendingDestination = destination
                bufferedDestination = null
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
                // What the person is actually looking at, as opposed to
                // which room is selected. Set here and cleared in the same
                // `finally` as the socket, so a process that went away can
                // never leave it reading true and silence the notifications
                // it was supposed to suppress (§6.3).
                model.visibleRoomID = model.currentRoom?.id
                // The room's live line comes back with the app, and only
                // with it: a phone in a pocket is not present, and saying
                // otherwise is the one lie presence must never tell (§4.2).
                // `repeatOnLifecycle` cancels this block on the way out, so
                // the socket closes exactly when the app stops being looked
                // at — Swift does the same from `.background`.
                try {
                    model.openRoomChannel()
                    awaitCancellation()
                } finally {
                    model.visibleRoomID = null
                    withContext(NonCancellable) { model.closeRoomChannel() }
                }
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
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)

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
    /**
     * The menu, when it is open, and which of the room header's two doors was
     * used (S14 + S18 in one screen — MenuScreen.kt, deviations 14). Null is
     * the room, which is the only permanent destination.
     */
    var menu by rememberSaveable { mutableStateOf<MenuEntry?>(null) }

    /** The finishing sequence's "Start another" lands in the chooser (S13). */
    var chooserRequested by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val reduceMotion = rememberReduceMotion()

    /**
     * How open the book is (design/Hearth.kt).
     *
     * The book used to arrive by fading in and leave by sliding out, as two
     * unrelated transitions hung off `AnimatedContent`. It is now one number
     * that three things drive — the fire pulled up on the room, the Wave
     * pulled down at the foot of the page, and a tap on either — and that
     * everything affected reads: the page's own offset, the room receding
     * behind it, the hearth riding up under the thumb. A gesture and a
     * transition made of the same value cannot fall out of step with each
     * other, which is what they used to do.
     */
    val sheet = rememberBookSheet()

    // One branch for reduce motion, taken inside the token (§11).
    val arrive: FiniteAnimationSpec<Float> = RibbonMotion.arrive(reduceMotion)
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val slide: FiniteAnimationSpec<IntOffset> = RibbonMotion.settle(reduceMotion)

    /** Compose the book under the room so a pull has something to raise. */
    fun beginOpening(reading: Reading) {
        openTarget = null
        openReading = reading
    }

    /** Open it outright — a waiting row, a quoted verse, the way in. */
    fun openBook(reading: Reading, target: VerseAddress?) {
        openTarget = target
        openReading = reading
        sheet.animate(open = true)
    }

    fun closeBook() {
        sheet.animate(open = false) {
            openReading = null
            openTarget = null
        }
    }

    /**
     * Put the book down without closing it by hand: the finishing sequence,
     * a room switch, a tapped invite. There is nothing to animate, because
     * whatever asked for it is drawing over the top already.
     */
    fun dropBook() {
        sheet.reset()
        openReading = null
        openTarget = null
    }

    /**
     * A tapped notification, honoured (S19).
     *
     * The room is switched first if it is not the one the notification was
     * about — a notification that cannot reach its own room is worse than no
     * notification, and a person reading Mark with their wife should not have
     * to find the Thursday study by hand to read what somebody left there.
     *
     * §6.3 asks for the reading to open *at that verse, the mark breathing*,
     * which is the path `openBook` already drives for a waiting row. A
     * destination with no verse — a finished book — lands on the room, which
     * is where the ember is and the way to the shelf.
     *
     * The menu and the chooser are put away on the way: whatever the person
     * had open, they asked to be somewhere else.
     */
    LaunchedEffect(model.pendingDestination) {
        val destination = model.pendingDestination ?: return@LaunchedEffect
        model.pendingDestination = null

        if (destination.roomID != room.id) {
            if (model.room(destination.roomID) == null) return@LaunchedEffect
            model.switchRoom(destination.roomID)
        }
        menu = null
        chooserRequested = false

        when (destination) {
            is Destination.Verse -> {
                val reading = model.state.readings
                    .firstOrNull { it.id == destination.readingID }
                if (reading != null) openBook(reading, destination.verse)
            }

            is Destination.Cards -> {
                // The cards sit at the end of a chapter, so the chapter's
                // last verse is where the page has to land for them to be
                // on screen at all.
                val reading = model.state.readings
                    .firstOrNull { it.id == destination.readingID }
                if (reading != null) {
                    openBook(
                        reading,
                        VerseAddress(
                            bookID = reading.bookID,
                            chapter = destination.chapter,
                            verse = 1,
                        ),
                    )
                }
            }

            // S01 is a destination in its own right, and switching the room
            // above was the whole of it.
            is Destination.Room -> dropBook()
        }
    }

    fun openPerson(personID: Uuid, roomID: Uuid) {
        navController.navigate(PersonRoute(personID = personID, roomID = roomID).path)
    }

    // One shared-transition scope over the whole stack, so that the pieces
    // which exist on both sides of a screen change are the *same* piece and
    // travel rather than being replaced: a seat at the hearth and that
    // person's own screen, an ember on the shelf and the same ember on its
    // record, a settings row's words and the heading it opens.
    //
    // Nothing pairs the room with the menu, and design/Flow.kt's header says
    // why — a flow needs one of its halves to be leaving, and the menu is a
    // layer over a room that stays composed underneath it. Everything that
    // flows here is a NavHost push.
    SharedTransitionLayout(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalFlowRoot provides this) {
        Box(
            Modifier
                .fillMaxSize()
                // The pull's own travel: how far a finger has to go to open
                // the book, and how far the hearth rides up with it.
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        sheet.travel = size.height * RibbonMotion.OPEN_TRAVEL
                    }
                },
        ) {
            // The room's own ground, behind everything.
            //
            // Nothing used to paint one: whatever showed through the stack
            // was the Activity's window background, which is `@color/unlit`
            // and hard-coded to the brand's near-black. That was invisible
            // while the room was the same near-black and it is not any more.
            // The peel shrinks the room six percent, so under a wallpaper
            // whose neutrals carry any chroma at all a frame of a *different*
            // black appeared around the receding room every time the book was
            // pulled open. The window background stays what it is, because it
            // is what the splash holds before any palette is known.
            Box(Modifier.fillMaxSize().room())
        // The room and the book, together, so that the menu drawn over
        // them can be taken out of a screen reader's path in one place. A
        // layer that covers the screen visually does not cover it for
        // TalkBack: without this, swiping past the last thing in the menu
        // walks straight into the room's own header underneath it (§11).
        Box(
            Modifier
                .fillMaxSize()
                .then(if (menu != null) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            // Predictive back (§12.2): the room peels in behind a closing screen.
            // The pop transitions below are what the system gesture drives, so
            // the peel is Android's own and not a back button we drew — which is
            // the one place the platform gesture beats anything we would design.
            // Swift hides the navigation bar entirely (`.toolbarVisibility`);
            // a NavHost draws no chrome of its own, so there is none to hide.
            NavHost(
                navController = navController,
                startDestination = Route.ROOM,
                modifier = Modifier
                    .fillMaxSize()
                    // The room recedes as the book rises over it: the same
                    // six-percent shrink, twenty-four-dp drop and fade that
                    // predictive back uses to peel a screen *off* the room,
                    // run in the other direction. Opening the book and
                    // closing it are then plainly the same movement, which
                    // is what a gesture has to be if it is going to be
                    // believed. Read inside the layer block, so a finger
                    // moving the book recomposes nothing in the room.
                    .peeled { sheet.progress },
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
                    // between rooms". So the id is the state, and the room is
                    // looked up from it.
                    //
                    // The room used to be the state, with `contentKey = { it.id }`
                    // to stop a rename crossing anything — and it does stop the
                    // cross-fade, but not the transition. `AnimatedContent` keys
                    // its content by the state *value*, so a renamed or paused
                    // room is a new target under an unchanged key: no outgoing
                    // content to fade out, but the incoming content still runs
                    // its enter, and the whole room fades in over itself. An id
                    // is equal to itself, so nothing happens at all.
                    // Which layer this composition is in, so that anything
                    // drawn here can travel to the same thing drawn somewhere
                    // else. A NavHost destination's content receiver *is* an
                    // `AnimatedVisibilityScope`, which is exactly what a
                    // shared element needs and what it would otherwise have
                    // to be handed as a parameter.
                    CompositionLocalProvider(LocalFlowLayer provides this) {
                    AnimatedContent(
                        targetState = room.id,
                        transitionSpec = {
                            (fadeIn(arrive) togetherWith fadeOut(arrive))
                                .using(SizeTransform(clip = false))
                        },
                        label = "the-room",
                    ) { roomID ->
                        RoomScreen(
                            model = model,
                            // The room this half of the cross-fade is about. The
                            // outgoing one is still in state — a switch does not
                            // remove it — so both halves draw a real room.
                            room = model.state.rooms.firstOrNull { it.id == roomID } ?: room,
                            sheet = sheet,
                            chooserRequested = chooserRequested,
                            onChooserHandled = { chooserRequested = false },
                            onOpenReading = { reading, target -> openBook(reading, target) },
                            onBeginOpening = { reading -> beginOpening(reading) },
                            onAbandonOpening = { dropBook() },
                            onOpenRooms = { menu = MenuEntry.ROOMS },
                            onYou = { menu = MenuEntry.YOU },
                            onOpenPerson = { personID, roomID -> openPerson(personID, roomID) },
                            onOpenEmber = { readingID -> navController.navigate(Route.ember(readingID)) },
                        )
                    }
                    }
                }

                composable(
                    Route.EMBER_PATTERN,
                    arguments = listOf(navArgument(Route.READING_ID) { type = NavType.StringType }),
                ) { entry ->
                    val readingID = entry.arguments?.getString(Route.READING_ID)?.let { uuid(it) }
                    val reading = model.state.readings.firstOrNull { it.id == readingID }
                    CompositionLocalProvider(LocalFlowLayer provides this) {
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
                            onBack = { navController.popBackStack() },
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
                    CompositionLocalProvider(LocalFlowLayer provides this) {
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
            }

            // The book, over the stack, and no longer a transition at all.
            //
            // It used to arrive by fading in and leave by sliding out, as two
            // unrelated `AnimatedContent` specs — which is why opening it and
            // closing it never looked like the same thing happening twice.
            // Now it is a sheet at `sheet.progress`: fully off the bottom at
            // 0, in place at 1, and wherever the finger has taken it in
            // between. The fire pulls it up, the Wave pulls it down, and both
            // taps run the identical movement.
            //
            // Composed after the NavHost is load-bearing: back callbacks are
            // taken in reverse order of registration, so the reading's own
            // predictive back — which peels the book off the room — wins over
            // the NavHost's for as long as the book is open.
            openReading?.let { book ->
                key(book.id) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // The offset is read inside the layer block, so a
                            // finger dragging the page never recomposes a
                            // word of Scripture. At rest it costs nothing at
                            // all, because at rest there is no page: the
                            // layer only exists once `beginOpening` or
                            // `openBook` has put a reading here.
                            .graphicsLayer {
                                translationY = (1f - sheet.progress) * size.height
                            },
                    ) {
                        ReadingScreen(
                            model = model,
                            room = room,
                            reading = book,
                            sheet = sheet,
                            onClose = { closeBook() },
                            onDismissed = { dropBook() },
                            // The finishing closes the book like everything
                            // else does. It used to cut it away in one frame:
                            // `dropBook` is for a page something else is
                            // already drawing over, and "Put it on the shelf"
                            // is a plain control inside the page's own scroll
                            // with nothing over it at all — so the app's most
                            // emotional transition (§6.5) was the one place
                            // the page vanished rather than left.
                            onFinished = { closeBook() },
                            onStartAnother = {
                                dropBook()
                                chooserRequested = true
                            },
                            openAt = openTarget,
                        )
                    }
                }
            }
        }

        // The menu, full screen, over the stack and over the book — Swift
        // presents it with `.fullScreenCover`, off the same container the
        // book hangs on. It is one screen rather than the book's two sheets,
        // and it holds the rooms, the room you are in, and you, with the two
        // doors that were missing from both (deviations 14).
        //
        // Composed after the book for the same reason the sheets were: back
        // callbacks are taken in reverse order of registration, so the menu's
        // own predictive back wins for as long as the menu is open.
        AnimatedContent(
            targetState = menu,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // A cover arrives from the bottom of the screen and leaves
                // the same way. Under reduce motion both are a cut (§11).
                val transform = if (targetState != null) {
                    (slideInVertically(slide) { it } + fadeIn(arrive)) togetherWith
                        ExitTransition.None
                } else {
                    EnterTransition.None togetherWith
                        (slideOutVertically(slide) { it } + fadeOut(settle))
                }
                transform.using(SizeTransform(clip = false))
            },
            label = "the-menu",
        ) { open ->
            // Same scope, new layer — not so that anything travels between
            // the room and the menu (nothing can; see design/Flow.kt), but so
            // that the menu's own NavHost pushes have a layer above them to
            // hang their flows on.
            CompositionLocalProvider(LocalFlowLayer provides this) {
                if (open != null) {
                    MenuScreen(
                        model = model,
                        entry = open,
                        onDismiss = { menu = null },
                        // The book belongs to the room it was opened in.
                        onSwitch = { dropBook() },
                    )
                }
            }
        }

        model.pendingInvite?.let { pending ->
            // A tapped invite while already onboarded: the join flow rides
            // over the room (S16). The key keeps a second link from
            // inheriting the first one's half-finished state — Swift's
            // `.id(pending.token)`, and here it discards the sheet's own
            // state with it.
            // A tapped invite link is the strongest possible statement of
            // intent (S16): it closes the menu rather than arriving
            // underneath it, the way it wins over onboarding's step.
            LaunchedEffect(pending.token) { menu = null }
            key(pending.token) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                // Joined, or done with it: the sheet slides back down over the
                // room rather than blinking out of it.
                val leave = rememberSheetExit(sheetState)
                ModalBottomSheet(
                    // Already animated away by the sheet itself: the person
                    // dismissed it.
                    onDismissRequest = { model.pendingInvite = null },
                    sheetState = sheetState,
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
                        onDone = { leave { model.pendingInvite = null } },
                        onDismiss = { leave { model.pendingInvite = null } },
                    )
                }
            }
        }
        }
      }
    }
}
