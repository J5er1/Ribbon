@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Bible
import app.readribbon.core.CardState
import app.readribbon.core.FireState
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.Air
import app.readribbon.design.BookSheet
import app.readribbon.design.Flows
import app.readribbon.design.HairlineRule
import app.readribbon.design.NoteMark
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SectionLabel
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.flows
import app.readribbon.design.opensTheBook
import app.readribbon.design.paper
import app.readribbon.design.pressable
import app.readribbon.design.pressablePaper
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.grain
import app.readribbon.design.room
import app.readribbon.fire.CampfireGlyph
import app.readribbon.fire.CampfireView
import app.readribbon.services.PresentPerson
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S01 — the room. Where the app opens, and the only permanent destination.
//
// What changed, and why (owner's call, September 2026 — deviation A19). The
// room was six things stacked in the dark with a great deal of air between
// them: a name, a line of faces, a fire, a capsule, some rows, a control. It
// held everything S01 asks for and it read, in the owner's words, as barren —
// and it was worst in exactly the two states a new person actually sees, a
// first run with no fire and a room of one with nobody in it.
//
// The diagnosis was not "too little on the screen". It was that nothing on
// the screen was *grouped*, so a page with six objects on it looked like six
// objects floating rather than like a place. Four changes follow from that:
//
//   1. **The page greets you.** One line, in the display face, by name. The
//      first words used to be the room's own filing label in 12 sp.
//   2. **Presence became a sentence.** `Copy.personIsReading` and its two
//      relatives had existed since the port and were only ever spoken to a
//      screen reader. The warmest copy in the product was invisible.
//   3. **The fire has a hearth.** The fire, the book's name, its state and the
//      way in are one raised object with the room's people seated around it,
//      standing on a hairline, lit by its own light on the floor. It was five
//      things centred in a column; it is one thing you could point at.
//   4. **The empty states carry something true.** First run offers the five
//      good places to start rather than one sentence in the middle of the
//      dark; a room of one shows an open seat rather than an absence.
//
// And the fire is now the way into the book: take hold of it and pull. See
// design/Hearth.kt — one number that the fire, the room and the page all read,
// so the gesture and the transition are the same movement rather than two.
//
// Law 2 is untouched: nothing here counts anything. Not the people (seats are
// drawn, never tallied), not the notes (four rows, never "4"), not the shelf,
// and nothing anywhere near the fire.

/** The room's own inset. */
private val GUTTER = 24.dp

/** Your own portrait, top-right, in its 44 dp frame. */
private val HEADER_PORTRAIT = 28.dp

/** The smallest thing a finger is allowed to have to hit (deviation 12). */
private val TOUCH = 44.dp

/**
 * A seat around the hearth, and the square that carries it.
 *
 * Six of them plus the hearth's own insets need 380 dp, and the commonest
 * Android phone is 360 — so a full room overflowed its card on most of the
 * devices this ships to, and on *any* device once somebody raises their
 * display size. [seatSize] measures the room it has and gives the seats back
 * whatever will fit, never below the 44 dp a finger is owed (§11).
 */
private val SEAT = 38.dp
private val SEAT_TOUCH = 48.dp
private val SEAT_MIN_TOUCH = 44.dp

/** The gap between drawn seats. A `spacedBy` on the touch squares, so the
 *  drawn circles keep the gap rather than the targets doing. */
private val SEAT_GAP = 10.dp - (SEAT_TOUCH - SEAT)

/** The presence ring around a seat, and how far outside the face it sits. */
private val RING = 1.5.dp
private val RING_GAP = 2.dp

/** The hearth's inner padding, and the air between its parts. */
private val HEARTH_PADDING = 22.dp

/**
 * How far the hearth rides up with the finger, as a fraction of the pull's
 * own travel.
 *
 * One, exactly: the fire stays under the thumb that is pulling it. Anything
 * less and the fire slides out from under the finger, which is the tell that
 * a gesture is being *interpreted* rather than obeyed.
 */
private const val HEARTH_FOLLOW = 1f

/**
 * S01 — the room.
 *
 * @param model the store.
 * @param room the room being shown.
 * @param sheet how open the book is (design/Hearth.kt). The hearth pulls it.
 * @param chooserRequested set from outside when "Start another" at a
 *   finishing should land in the chooser (S13).
 * @param onChooserHandled the request was taken; clear it.
 * @param onOpenReading open the book. The second half is where to open —
 *   null for your own position, a verse when a waiting row named one (§6.3).
 * @param onBeginOpening the fire has been taken hold of: compose the book
 *   underneath so there is something for the pull to raise. Not the same as
 *   opening it.
 * @param onAbandonOpening the pull was let go of short of the commit: take
 *   the page back out of the tree. A page left composed off the bottom of
 *   the screen is not invisible — it holds the back gesture.
 * @param onOpenRooms the room's name, top-left: your rooms (S14).
 * @param onYou your own portrait, top-right — settings one tap from the
 *   room. Deviation 13, by the owner's call.
 * @param onOpenPerson a portrait or the last-reader line goes to that person
 *   (S12).
 * @param onOpenEmber one book on the shelf, pushed to its record (S11).
 */
@Composable
fun RoomScreen(
    model: AppModel,
    room: Room,
    sheet: BookSheet,
    chooserRequested: Boolean,
    onChooserHandled: () -> Unit,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onBeginOpening: (Reading) -> Unit,
    onAbandonOpening: () -> Unit,
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
            Column(
                modifier = Modifier
                    .readableColumn()
                    // The extra bottom inset three-button navigation needs
                    // comes from safeDrawing itself.
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
                horizontalAlignment = Alignment.Start,
            ) {
                RoomHeader(model = model, room = room, onOpenRooms = onOpenRooms, onYou = onYou)

                Greeting(
                    model = model,
                    room = room,
                    onOpenPerson = onOpenPerson,
                    modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp),
                )

                Hearth(
                    model = model,
                    room = room,
                    reading = reading,
                    sheet = sheet,
                    onOpenReading = onOpenReading,
                    onBeginOpening = onBeginOpening,
                    onAbandonOpening = onAbandonOpening,
                    onPickABook = { showChooser = true },
                    onOpenPerson = onOpenPerson,
                    onInvite = { showInviteShare = true },
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 22.dp),
                )

                // First run has no fire and no shelf, and used to have one
                // sentence in the middle of the dark. The five good places to
                // start are the chooser's own — the same cards, the same
                // fires, one tap nearer.
                if (reading == null && shelf.isEmpty() && !room.isPaused) {
                    StarterShelf(
                        onChoose = { bookID ->
                            val started = model.startReading(bookID = bookID, room = room)
                            onOpenReading(started, null)
                        },
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }

                WaitingSection(
                    model = model,
                    room = room,
                    reading = reading,
                    onOpenReading = onOpenReading,
                    onSendItAgain = { showInviteShare = true },
                    modifier = Modifier.padding(horizontal = GUTTER),
                )

                // The shelf, below the fold (S10). No shelf until the first
                // book is finished — an empty shelf is a reproach, so it is
                // absent rather than empty-stated, and so is its head.
                if (shelf.isNotEmpty()) {
                    Column(Modifier.padding(top = 40.dp, start = GUTTER, end = GUTTER)) {
                        SectionLabel(Copy.THE_SHELF)
                        ShelfView(
                            room = room,
                            readings = shelf,
                            onStartAnother = { showChooser = true },
                            onOpenEmber = onOpenEmber,
                        )
                    }
                }

                // Never after a lapse. §4.7 ends on exactly that — a quiet
                // day offered to a paused room "would make it an apology" —
                // and the paused room had been drawing the control anyway,
                // under a line that had just said the room was paused. Grace
                // is for the days you chose; it is not the answer to a
                // billing state.
                if (!room.isPaused) {
                    QuietDayFoot(
                        model = model,
                        room = room,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 52.dp, bottom = 40.dp),
                    )
                } else {
                    // The foot was carrying the scroll's bottom inset as well
                    // as the control; the paused room still needs the inset.
                    Air(40.dp)
                }
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
        InviteSheet(room = room, model = model, onDismiss = { showInviteShare = false })
    }
}

// MARK: Header — still the entire navigation bar

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
            .padding(horizontal = GUTTER)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minHeight = TOUCH)
                .clip(RibbonShape.smallShape)
                // Swift's `.accessibilityHint("Opens your rooms")`. Compose
                // has no hint field; the click action's own label is the
                // nearest honest thing, and TalkBack reads it as "double tap
                // to open your rooms" — the same fact, said as the action
                // rather than as its consequence.
                .pressable(onClickLabel = Copy.OPEN_YOUR_ROOMS, onClick = onOpenRooms)
                .padding(horizontal = 6.dp),
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
                .clip(CircleShape)
                .pressable(onClickLabel = Copy.OPEN_YOUR_ACCOUNT, onClick = onYou)
                // Swift's `.accessibilityLabel(Copy.you)`, which replaces the
                // label rather than adding to it: the control is the way to
                // your account, so the portrait's own name is cleared
                // beneath it.
                .semantics { contentDescription = Copy.YOU },
            contentAlignment = Alignment.Center,
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

// MARK: The greeting, and who is here

/**
 * The first words on the screen, and the sentence under them.
 *
 * The greeting is one heading, so a screen reader's heading swipe lands on
 * the top of the page rather than walking six portraits to reach the fire.
 */
@Composable
private fun Greeting(
    model: AppModel,
    room: Room,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour = rememberHourOfDay()
    val reduceMotion = rememberReduceMotion()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Cross-fades rather than cutting when the hour turns under a room
        // that has been left open — which is a small thing, and the kind of
        // small thing this product is made of.
        AnimatedContent(
            targetState = Copy.greeting(model.me?.name, hour),
            transitionSpec = {
                fadeIn(RibbonMotion.settle(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.settle(reduceMotion))
            },
            label = "the-greeting",
        ) { line ->
            Text(
                text = line,
                style = RibbonType.display(30f),
                color = Palette.text,
                modifier = Modifier.semantics { heading() },
            )
        }

        PresenceLine(model = model, room = room, onOpenPerson = onOpenPerson)
    }
}

/**
 * Who is here, in words.
 *
 * Exactly one of three things, and never two: somebody is reading, somebody
 * read recently, or nothing at all. Alone, the line is absent — never a
 * sentence about being alone (§08). A paused room says nothing here either;
 * its own line lives under the way in.
 */
@Composable
private fun PresenceLine(
    model: AppModel,
    room: Room,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val present: List<PresentPerson> = if (room.isPaused) emptyList() else model.presentPeople
    val reduceMotion = rememberReduceMotion()
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val settleSize: FiniteAnimationSpec<IntSize> = RibbonMotion.settle(reduceMotion)

    val lastReader = if (present.isEmpty() && !room.isPaused) model.lastReader(room) else null

    // The sentence, built from the three strings that already existed and had
    // no visible call site: "Ruth is reading", "Ruth is here, but still", and
    // "…, with Ann and Mara" for everybody after the first.
    val sentence: Pair<String, Uuid>? = when {
        present.isNotEmpty() -> {
            val first = present.first()
            val base = if (first.isIdle) {
                Copy.personIsHereButStill(firstName(first.name))
            } else {
                Copy.personIsReading(firstName(first.name))
            }
            val others = present.drop(1).map { firstName(it.name) }
            val line = if (others.isEmpty()) base else Copy.alsoHere(base, others)
            line to first.id
        }

        lastReader != null -> lastReader.line to lastReader.personID
        else -> null
    }

    // Held across the leaving fade: `AnimatedVisibility` recomposes its
    // content while it goes, and by then the sentence is gone.
    var held by remember { mutableStateOf(sentence) }
    LaunchedEffect(sentence) { if (sentence != null) held = sentence }

    AnimatedVisibility(
        visible = sentence != null,
        enter = fadeIn(settle) + expandVertically(settleSize),
        exit = fadeOut(settle) + shrinkVertically(settleSize),
        label = "who-is-here",
        modifier = modifier,
    ) {
        held?.let { (line, personID) ->
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = TOUCH)
                    .clip(RibbonShape.smallShape)
                    .pressable(onClick = { onOpenPerson(personID, room.id) })
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = line,
                    style = RibbonType.ui(16f),
                    color = if (sentence != null && lastReader == null) {
                        Palette.text
                    } else {
                        Palette.muted
                    },
                )
            }
        }
    }
}

// MARK: The hearth

/**
 * The fire, the people around it, what it is, and the way in — one object.
 *
 * This is the whole answer to "barren". Every part of it existed before and
 * was floating separately in a column; grouping them is what turns a list of
 * things into a place. The card is one shade paler than the ground, which is
 * how paper lifts off a table — no shadow, no glow, no glass (§13).
 */
@Composable
private fun Hearth(
    model: AppModel,
    room: Room,
    reading: Reading?,
    sheet: BookSheet,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onBeginOpening: (Reading) -> Unit,
    onAbandonOpening: () -> Unit,
    onPickABook: () -> Unit,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val book = reading?.let { Bible.book(it.bookID) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The hearth rides up with the thumb that is pulling the fire,
            // while the room behind it recedes (RibbonRoot peels that). The
            // whole thing is read inside the layer block, so a finger moving
            // the book recomposes nothing.
            .graphicsLayer {
                val pull = sheet.progress
                if (pull > 0f) {
                    translationY = -pull * sheet.travel * HEARTH_FOLLOW
                    // Fades a little faster than it travels, so the page
                    // arriving over it does not have to cover a bright card.
                    alpha = (1f - pull * 1.25f).coerceIn(0f, 1f)
                }
            }
            .paper(RibbonShape.groupShape)
            .padding(HEARTH_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Seats(
            model = model,
            room = room,
            onOpenPerson = onOpenPerson,
            onInvite = onInvite,
        )

        Air(16.dp)

        // Choosing a book puts a fire where the invitation to choose one was,
        // and finishing it takes the fire away again. Both used to happen
        // between two frames, in the middle of the screen, on the one thing
        // the room is about. §9.1 gives the arrive token to cross-fades
        // between rooms; this is the same substitution inside one room, so it
        // gets the same token — and the height eases on the settle token
        // rather than jumping.
        //
        // The id is the state, not the reading. `AnimatedContent` keys its
        // content by the state *value*, so handing it the reading itself
        // would make a fire that grew from kindling to steady into a new
        // target — and a new target under an unchanged key still runs its
        // enter, fading the fire in over itself every time it was fed. An id
        // is equal to itself.
        AnimatedContent(
            targetState = reading?.id,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (
                    fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                        fadeOut(RibbonMotion.arrive(reduceMotion))
                    ).using(
                    SizeTransform(clip = false) { _, _ -> RibbonMotion.settle(reduceMotion) },
                )
            },
            label = "the-fire",
        ) { readingID ->
            // Looked up rather than captured, so the fire goes on growing
            // under its own cross-fade. A finished book is still in state, so
            // the half on its way out has a real reading to draw too.
            val current = readingID?.let { id -> model.state.readings.firstOrNull { it.id == id } }
            val currentBook = current?.let { Bible.book(it.bookID) }
            // The well: the hearth's own floor, a recess of the room's unlit
            // ground cut into the paler card.
            //
            // It is not decoration, and finding that out is what put it here.
            // The fire draws its own ambient throw across the whole of its
            // canvas and then clips it at the edge — which is invisible
            // against near-black and a plainly drawn rectangle against
            // anything paler. Standing the fire on the ground it was drawn
            // for fixes that, and it is the better picture anyway: a warm
            // object in a dark recess, in a room the wallpaper may have
            // painted any colour at all.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RibbonShape.cardShape)
                    .background(Palette.ground)
                    .grain()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (current != null && currentBook != null) {
                    TheFire(
                        model = model,
                        reading = current,
                        sheet = sheet,
                        onBeginOpening = onBeginOpening,
                        onAbandonOpening = onAbandonOpening,
                        onOpened = { onOpenReading(current, null) },
                    )
                } else {
                    UnlitHearth(paused = room.isPaused)
                }
            }
        }

        Air(18.dp)

        WayIn(
            model = model,
            room = room,
            reading = reading,
            book = book,
            sheet = sheet,
            onOpenReading = onOpenReading,
            onBeginOpening = onBeginOpening,
            onPickABook = onPickABook,
        )
    }
}

/**
 * The fire itself, its book's name and its state — and the handle that opens
 * the book.
 *
 * S01 said "tap fire → nothing (deliberately inert; it is an object, not a
 * button)", and that is now an owner's-call deviation (A18): the fire is the
 * way in. The reasoning against it was that a fire is an object rather than a
 * control; the reasoning for it is that a hearth is an object you can reach
 * into, and a drag is not a button. The tap equivalent §11 requires is on the
 * same node, and the way-in capsule below it is the other one.
 */
@Composable
private fun TheFire(
    model: AppModel,
    reading: Reading,
    sheet: BookSheet,
    onBeginOpening: (Reading) -> Unit,
    onAbandonOpening: () -> Unit,
    onOpened: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val state = model.fireState(reading)
    val book = Bible.book(reading.bookID) ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Grabbing the fire and pulling puts the book up. The fire
                // keeps its own "The fire is steady." label; this adds the
                // action rather than replacing the sentence.
                .opensTheBook(
                    sheet = sheet,
                    label = Copy.continueIn(book.name),
                    onEngaged = {
                        // Taking hold of the fire *is* discovering the
                        // gesture, whether or not the pull goes on to commit.
                        // The hint has done its job and does not come back
                        // (§6.1).
                        model.markFirePulled()
                        onBeginOpening(reading)
                    },
                    onOpened = onOpened,
                    onAbandoned = onAbandonOpening,
                )
                // Grows very slightly as it is pulled, which is the whole of
                // the feedback the fire itself gives: an object being lifted
                // toward you.
                .graphicsLayer {
                    val pull = sheet.progress
                    if (pull > 0f) {
                        val swell = 1f + 0.10f * pull
                        scaleX = swell
                        scaleY = swell
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            CampfireView(
                state = state,
                scale = reading.handiwork.scale,
                coalDepth = reading.handiwork.coalDepth,
                // S01's offline room: "fire renders in its last known state,
                // dimmed by ~8%", and nothing else — no banner, no retry, no
                // line of copy (§13). `CampfireView` has carried this
                // parameter since it was written and nothing ever passed it,
                // so the one visible sign the room had of being offline was
                // not reachable. It is now.
                dimmed = !model.isOnline,
            )
        }

        // The hearthline: the lightest line the palette has, and the whole
        // difference between a fire floating in the dark and a fire standing
        // on something. §15.4 asks for the lightest possible line, and this
        // is it — one hairline at a little under the well's width.
        HairlineRule(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .padding(bottom = 4.dp),
            color = Palette.rule,
        )

        Text(
            text = book.name,
            style = RibbonType.display(30f),
            color = Palette.text,
        )

        // The fire's own name changes under it as the fire changes — kindling
        // to steady to banked. A word swapped on one frame under a fire that
        // took its time getting there reads as a correction rather than as
        // the same fact said twice.
        AnimatedContent(
            targetState = state.displayName,
            transitionSpec = {
                fadeIn(RibbonMotion.settle(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.settle(reduceMotion))
            },
            label = "the-fire-is",
        ) { name -> SmallCaps(name, size = 13f) }
    }
}

/**
 * The hearth with nothing burning in it.
 *
 * No fire-shaped hole is reserved and nothing says the room is empty. The
 * hearthline underneath still draws, which is the point: there is a place for
 * a fire here, and it is simply unlit.
 *
 * @param paused whether the room is paused, in which case the hearth holds
 *   its line and says nothing at all. S01's paused room is "one row: The room
 *   is paused. It can be started again any time." — and that row is printed
 *   by [WayIn] just below this. Rendering the app told on itself here: the
 *   paused hearth was saying "Pick something to read together" over a room
 *   where picking a book is precisely the half that is withheld, which is the
 *   same failure as a control that names what it does and then does not do
 *   it. A hearth that is quiet and a line that explains why agree; an
 *   invitation with no door does not.
 */
@Composable
private fun UnlitHearth(paused: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Air(6.dp)
        if (!paused) {
            Text(
                text = Copy.PICK_A_BOOK,
                style = RibbonType.display(24f),
                color = Palette.text,
                textAlign = TextAlign.Center,
            )
        }
        // The hearthline draws here too — in the paused room most of all. An
        // unlit hearth is still a hearth, and the place a fire will stand is
        // more welcoming than the absence of one.
        HairlineRule(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .padding(vertical = 4.dp),
            color = Palette.rule,
        )
        if (!paused) {
            Text(
                text = Copy.FIRST_FIRE_HINT,
                style = RibbonType.ui(14f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
            )
        }
        Air(2.dp)
    }
}

// MARK: The seats

/**
 * Who belongs in this room, drawn as the seats around the fire.
 *
 * S01 asks for "portraits of whoever is in the book right now"; this draws
 * everyone who is *in the room*, with presence as a ring around the seat.
 * Deviation A19, and the reason is the barren case: a room of one with nobody
 * reading drew nothing at all here, so the app's front door was blank exactly
 * when it belonged to somebody who had just arrived.
 *
 * What keeps it from being the avatar row of a social app — which §3 says
 * this product is not: six at most, because that is the room's size; no names
 * under the faces; no overflow marker; nothing counted; membership order, so
 * nobody is ranked; and absence is never dimmed, because a dimmed face is a
 * judgement.
 */
@Composable
private fun Seats(
    model: AppModel,
    room: Room,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val members = model.members(room).sortedBy { it.joinedAt }
    val present: Set<Uuid> =
        if (room.isPaused) emptySet() else model.presentPeople.map { it.id }.toSet()
    val idle: Set<Uuid> =
        if (room.isPaused) emptySet() else model.presentPeople.filter { it.isIdle }.map { it.id }
            .toSet()
    val reduceMotion = rememberReduceMotion()
    val arrive: FiniteAnimationSpec<Float> = RibbonMotion.arrive(reduceMotion)

    // A seat that has just been vacated is held for the length of one fade,
    // so the row never slides sideways because somebody's heartbeat landed
    // first. Seeded with whoever is already known, because the app opening on
    // the room says nothing (§05) and six faces swelling in at launch would
    // be an entrance.
    val shown = remember(room.id) {
        mutableStateListOf<Uuid>().apply { addAll(members.map { it.personID }) }
    }
    val here = members.map { it.personID }
    LaunchedEffect(here, reduceMotion) {
        here.forEach { if (it !in shown) shown.add(it) }
        if (shown.any { it !in here }) {
            if (!reduceMotion) delay(RibbonMotion.ARRIVE_MS.toLong())
            shown.retainAll { it in here }
        }
    }

    // Not "there is room for another" — "somebody is still expected". The
    // difference matters and it is the difference between a warm fact and a
    // reproach: a couple with no intention of being three would otherwise be
    // shown four empty chairs on their own front door, forever, with nobody
    // invited and nothing pending.
    val seatKept = model.somebodyIsExpected(room)

    // How many squares have to fit: everyone drawn, plus the open seat.
    val places = minOf(shown.size, Room.capacity) + if (seatKept) 1 else 0

    BoxWithConstraints(modifier = modifier) {
        // Six 48 dp squares need 288 dp and a 360 dp phone offers 268 inside
        // the hearth. Rather than wrap a row of faces onto two lines — which
        // would read as a list rather than as a circle round a fire — the
        // seats give up a few dp each until they fit, and stop at the 44 dp
        // minimum a finger is owed. Below even that the row scrolls.
        val touch = if (places <= 1) {
            SEAT_TOUCH
        } else {
            val each = (maxWidth - SEAT_GAP * (places - 1)) / places
            each.coerceIn(SEAT_MIN_TOUCH, SEAT_TOUCH)
        }
        val face = SEAT * (touch / SEAT_TOUCH)

        Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (touch * places + SEAT_GAP * (places - 1) > maxWidth) {
                    Modifier.horizontalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(SEAT_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.take(Room.capacity).forEach { personID ->
            key(personID) {
                val seat = remember { MutableTransitionState(personID in here) }
                seat.targetState = personID in here
                AnimatedVisibility(
                    visibleState = seat,
                    enter = fadeIn(arrive) + scaleIn(arrive, initialScale = 0.82f),
                    exit = fadeOut(arrive) + scaleOut(arrive, targetScale = 0.82f),
                    label = "a-seat",
                ) {
                    Seat(
                        model = model,
                        room = room,
                        personID = personID,
                        present = personID in present,
                        idle = personID in idle,
                        touch = touch,
                        face = face,
                        onClick = { onOpenPerson(personID, room.id) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = seatKept,
            enter = fadeIn(arrive) + scaleIn(arrive, initialScale = 0.82f),
            exit = fadeOut(arrive) + scaleOut(arrive, targetScale = 0.82f),
            label = "an-open-seat",
        ) {
            OpenSeat(touch = touch, face = face, onInvite = onInvite)
        }
        }
    }
}

@Composable
private fun Seat(
    model: AppModel,
    room: Room,
    personID: Uuid,
    present: Boolean,
    idle: Boolean,
    touch: Dp,
    face: Dp,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val accent = Palette.accent
    val ring: Float by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (present) 1f else 0f,
        animationSpec = RibbonMotion.handled(reduceMotion),
        label = "a-seat-ring",
    )

    // The ring is a drawn shape, and a drawn shape is not a signal for
    // somebody who cannot see it (§11). The presence line above says the
    // state of whoever is *first*; everybody after them is named there and
    // nothing else, so a half ring — the whole of here-but-still — was
    // carried by geometry alone. The seat says it too, in the same words.
    val spoken = model.person(personID)?.name?.let { name ->
        when {
            present && idle -> Copy.personIsHereButStill(firstName(name))
            present -> Copy.personIsReading(firstName(name))
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .size(touch)
            .clip(CircleShape)
            .pressable(role = Role.Button, onClick = onClick)
            .then(
                if (spoken != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = spoken
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(face + (RING + RING_GAP) * 2)
                .drawBehind {
                    if (ring <= 0f) return@drawBehind
                    val stroke = RING.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    // Present draws the whole ring; here-but-still draws the
                    // top half of it. A different *shape*, not a dimmer
                    // colour — colour is never the only signal (§11), and a
                    // dimmed face would read as a judgement besides.
                    if (idle) {
                        drawArc(
                            color = accent.copy(alpha = ring),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                stroke / 2f,
                                stroke / 2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                radius * 2f,
                                radius * 2f,
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                        )
                    } else {
                        drawCircle(
                            color = accent.copy(alpha = ring),
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The portrait keeps its own label: in a gutter or a seat a
            // person's identity is carried by the accessibility label, never
            // by colour alone (§11).
            PortraitView(
                person = model.person(personID),
                ink = model.membership(personID = personID, roomID = room.id)?.ink,
                size = face,
                image = model.portrait(personID),
                // A seat and that person's own screen are the same face.
                modifier = Modifier.flows(Flows.seat(room.id, personID)),
            )
        }
    }
}

/**
 * The place kept for somebody who has not arrived yet.
 *
 * A dashed circle, unmistakably a place rather than a person. This is the one
 * element that makes a room of one look like a room with an empty chair in it
 * instead of a room with a defect.
 */
@Composable
private fun OpenSeat(touch: Dp, face: Dp, onInvite: () -> Unit) {
    // Muted rather than `rule`: a divider's colour is right for a line
    // between two things and far too quiet for a seat, which has to be seen
    // as a place before anyone will tap it. On Ribbon's own paint `rule`
    // against a card was very nearly nothing at all.
    val rule = Palette.muted.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .size(touch)
            .clip(CircleShape)
            .pressable(role = Role.Button, onClick = onInvite)
            .semantics { contentDescription = Copy.AN_OPEN_SEAT },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(face)
                .drawBehind {
                    val stroke = 1.4.dp.toPx()
                    drawCircle(
                        color = rule,
                        radius = (size.minDimension - stroke) / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                            ),
                        ),
                    )
                },
        )
    }
}

// MARK: The way in

/**
 * Scripture is never locked (§2.5): a paused room keeps its way in — the
 * pause line is added, the waiting rows go.
 */
@Composable
private fun WayIn(
    model: AppModel,
    room: Room,
    reading: Reading?,
    book: app.readribbon.core.BibleBook?,
    sheet: BookSheet,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onBeginOpening: (Reading) -> Unit,
    onPickABook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (reading != null && book != null) {
            // Have you been in this book yet? A yes or a no — never how far.
            val hasRead = model.state.positions.any {
                it.readingID == reading.id && it.personID == model.me?.id
            }
            WayInButton(
                title = if (hasRead) Copy.continueIn(book.name) else Copy.begin(book.name),
            ) {
                // The same door the drag goes through, and the same movement:
                // the book rises rather than appearing. One way in, two ways
                // to ask for it.
                onOpenReading(reading, null)
            }
            // The gesture, said once. It goes for good the first time the
            // book is opened by any route — the same contract the margin
            // hint keeps (§6.1). A hint that comes back is worse than none.
            if (!model.hasPulledTheFire) {
                SmallCaps(Copy.PULL_THE_FIRE_UP, size = 11f)
            }
        } else if (!room.isPaused) {
            // A control says exactly what happens — and it must not simply
            // repeat the sentence above it, which is what "Pick something to
            // read together" twice on one card was doing.
            //
            // A paused room gets no chooser, which is how it has always been:
            // Scripture is never locked (§2.5) so a room with a book open
            // keeps its way in, but starting a *new* fire is the half a
            // lapsed subscription does hold. The starter shelf below is
            // hidden for the same reason, and the two have to agree.
            WayInButton(title = Copy.PICK_A_BOOK_CONTROL, onClick = onPickABook)
        }

        if (room.isPaused) {
            Text(
                text = Copy.ROOM_PAUSED,
                style = RibbonType.ui(14f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// MARK: Good places to start — first run only

/**
 * The five books the chooser opens on, one tap nearer.
 *
 * Built from the chooser's own list and the same small fire it draws beside
 * each one, so nothing here is a second opinion about where to begin. It
 * appears on first run and never again: somebody with a book behind them does
 * not need suggesting to.
 */
@Composable
private fun StarterShelf(
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionLabel(Copy.GOOD_PLACES_TO_START, modifier = Modifier.padding(start = GUTTER))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = GUTTER, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Bible.goodPlacesToStart.forEach { id ->
                Bible.book(id)?.let { book ->
                    Column(
                        modifier = Modifier
                            .size(width = 108.dp, height = 96.dp)
                            .pressablePaper(RibbonShape.cardShape) { onChoose(book.id) }
                            // The drawn fire and the name are one label, not
                            // two things read in a row.
                            .clearAndSetSemantics {
                                contentDescription = Copy.bookIsAFire(book.name, book.scale.name)
                            },
                        verticalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CampfireGlyph(state = FireState.burning, scale = book.scale, height = 30.dp)
                        Text(text = book.name, style = RibbonType.ui(15f), color = Palette.text)
                    }
                }
            }
        }
    }
}

// MARK: What's waiting — rows, never a count, never a badge

@Composable
private fun WaitingSection(
    model: AppModel,
    room: Room,
    reading: Reading?,
    onOpenReading: (Reading, VerseAddress?) -> Unit,
    onSendItAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Paused: waiting rows gone (S01) — the pause line stands alone.
    val waiting: List<Note> = if (room.isPaused) emptyList() else model.waitingNotes(room).take(4)
    // Counted here and nowhere else: the number decides whether the room is
    // still a room of one, and it never reaches the interface (Law 2).
    val alone = model.members(room).size == 1

    // S01's anatomy lists "the cards are open" as a waiting row and the room
    // had never drawn one. One row however many cards are open — the plural
    // is in the noun, not in a number.
    val cardsOpen = reading != null && !room.isPaused && model.state.cards.any {
        it.readingID == reading.id && it.state == CardState.open
    }

    val hasRows = waiting.isNotEmpty() || cardsOpen
    // A *live link*, not merely a room of one. "The invite is still out."
    // told somebody who had just made their first room and asked nobody that
    // an invite was outstanding, and offered to send it again — and this pass
    // promoted that sentence from a quiet line onto a drawn card, which made
    // a small wrongness a loud one. `somebodyIsExpected` is not the predicate
    // either: it answers true for a room of one by design, which is what the
    // open seat wants and this does not.
    val hasInvite = !room.isPaused && alone && model.hasLiveInvite(room)
    if (!hasRows && !hasInvite) return

    val reduceMotion = rememberReduceMotion()
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val settleSize: FiniteAnimationSpec<IntSize> = RibbonMotion.settle(reduceMotion)

    // Rows that were already waiting when the room drew are simply there: the
    // app opening on the room says nothing (§05), and four notes unfurling at
    // launch would be an entrance. A note that lands while you are *looking*
    // at the room is a different event entirely — somebody left you something
    // a moment ago — and §9.1 gives notes unfurling the settle token.
    val standing = remember(room.id) { waiting.map { it.id }.toSet() }

    Column(modifier = modifier.fillMaxWidth().padding(top = 30.dp)) {
        if (hasRows) SectionLabel(Copy.LEFT_FOR_YOU)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            waiting.forEach { note ->
                val author = model.person(note.authorID)
                if (author != null) {
                    key(note.id) {
                        val landed = remember { MutableTransitionState(note.id in standing) }
                        landed.targetState = true
                        AnimatedVisibility(
                            visibleState = landed,
                            enter = fadeIn(settle) + expandVertically(settleSize),
                            exit = fadeOut(settle) + shrinkVertically(settleSize),
                            label = "a-note-waiting",
                        ) {
                            WaitingRow(
                                mark = {
                                    // A voice note and a written one are told
                                    // apart at a glance, which the single
                                    // undifferentiated dot never allowed.
                                    NoteMark(
                                        kind = note.kind,
                                        ink = model.membership(
                                            personID = note.authorID,
                                            roomID = room.id,
                                        )?.ink ?: Ink.clay,
                                        found = false,
                                        mine = false,
                                        pending = false,
                                    )
                                },
                                text = if (note.kind == NoteKind.voice) {
                                    Copy.leftYouAVoiceNote(
                                        firstName(author.name),
                                        note.verse.formatted,
                                    )
                                } else {
                                    Copy.leftYouANote(
                                        firstName(author.name),
                                        note.verse.formatted,
                                    )
                                },
                                onClick = {
                                    // Jumps to that note (§6.3): the reading
                                    // opens at its verse, the mark breathing.
                                    if (reading != null) onOpenReading(reading, note.verse)
                                },
                            )
                        }
                    }
                }
            }

            if (cardsOpen && reading != null) {
                WaitingRow(
                    mark = {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Palette.accent),
                        )
                    },
                    text = Copy.NOTIF_CARDS_OPEN,
                    onClick = { onOpenReading(reading, null) },
                )
            }
        }

        // Room of one, invite still out — the state, not the person. Already
        // showing when the room drew, so there is no entrance; the exit is
        // the one that matters, because it plays the moment somebody accepts.
        AnimatedVisibility(
            visible = hasInvite,
            enter = fadeIn(settle) + expandVertically(settleSize),
            exit = fadeOut(settle) + shrinkVertically(settleSize),
            label = "the-invite-still-out",
        ) {
            Row(
                modifier = Modifier
                    .padding(top = if (hasRows) 10.dp else 0.dp)
                    .fillMaxWidth()
                    .sizeIn(minHeight = TOUCH + 8.dp)
                    .paper(RibbonShape.rowShape)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCaps(Copy.INVITE_STILL_OUT, size = 12f, modifier = Modifier.weight(1f))
                QuietControl(title = Copy.SEND_IT_AGAIN, onClick = onSendItAgain)
            }
        }
    }
}

/**
 * One thing left for you: a mark, a sentence, and somewhere it goes.
 *
 * On a card now rather than bare on the ground. The rows used to be four
 * sentences floating under the fire with nothing to hold them, which is most
 * of why the bottom of the screen read as unfinished.
 */
@Composable
private fun WaitingRow(
    mark: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = TOUCH + 8.dp)
            .pressablePaper(RibbonShape.rowShape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Text(
            text = text,
            style = RibbonType.ui(15f),
            color = Palette.text,
            modifier = Modifier.weight(1f),
        )
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
    val settle: FiniteAnimationSpec<Float> = RibbonMotion.settle(reduceMotion)
    val settleSize: FiniteAnimationSpec<IntSize> = RibbonMotion.settle(reduceMotion)

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

// MARK: The hour

/**
 * The hour of the day, kept current.
 *
 * A greeting read once at composition would still say "Good evening" at two
 * in the morning on a phone that was left on the room overnight. This sleeps
 * until the next hour turns and then says so — which costs one coroutine and
 * is the difference between a greeting and a stale string.
 */
@Composable
private fun rememberHourOfDay(): Int {
    fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour by produceState(initialValue = now().hour) {
        while (true) {
            val at = now()
            // To the top of the next hour, plus a second so the boundary is
            // crossed rather than landed on.
            val seconds = (60 - at.minute) * 60 - at.second + 1
            delay(seconds.toLong() * 1000L)
            value = now().hour
        }
    }
    return hour
}
