@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package app.readribbon.reading

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Bible
import app.readribbon.core.BibleBook
import app.readribbon.core.CardState
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.Reading
import app.readribbon.core.RibbonClock
import app.readribbon.core.Room
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.TranslationID
import app.readribbon.core.TranslationRegistry
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.design.BookSheet
import app.readribbon.design.HairlineRule
import app.readribbon.design.InkDot
import app.readribbon.design.Measure
import app.readribbon.design.NoteMark
import app.readribbon.design.Palette
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WaveMark
import app.readribbon.design.WayInButton
import app.readribbon.design.closesTheBook
import app.readribbon.design.grain
import app.readribbon.design.peeled
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberBackPeel
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.FireBecomesEmber
import app.readribbon.screens.ConfirmChoice
import app.readribbon.screens.RibbonConfirmDialog
import app.readribbon.services.PresentPerson
import app.readribbon.services.VoiceRecorder
import app.readribbon.services.ensureRemoteChapter
import java.io.File
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// S02 — the surface everything else exists to protect. No top bar, no back
// button, no toolbar until you ask for one. Two ways out, both at the
// bottom: the Wave mark, and a downward drag from scroll-top that settles
// like a book closing.
//
// On Android there is a third way in to the same door and it is the
// platform's own: the back gesture. §12.2 asks for predictive back, and
// this is the screen it was written for — the room peels in behind the
// closing book as the gesture is pulled, and the book is only let go when
// the gesture completes. A cancelled back leaves the reading exactly where
// it was.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an equivalent ambient value yet, so the model is a
// parameter, exactly as it is in NoteCard and PresenceForm.

/**
 * How long a scroll we asked for ourselves stays ours. Inside this window a
 * moving page is the app moving, not you, so it does not break a follow.
 */
private val PROGRAMMATIC_SCROLL_GRACE = 1500.milliseconds

/** Position saves are cheap but not free (see `trackReading`). */
private val POSITION_SAVE_INTERVAL = 2.seconds

/** Fuel is a coarse record of reading, not a scroll log (§2.8). */
private val FUEL_INTERVAL = 25.seconds

/** A highlight's label names who made it, then goes (S06). */
private val HIGHLIGHT_LABEL_LIFETIME = 2600.milliseconds

/** The gutter's centre line. Left edge, notes only (§4.2). */
private val GUTTER_X = 14.dp

/**
 * Room kept under the last line for the Wave, so Scripture never ends
 * beneath the way out. iOS gets this free from the scroll view's safe area;
 * here the bottom chrome is an overlay over an edge-to-edge list, so the
 * space is asked for.
 */
private val BOTTOM_CHROME_ROOM = 64.dp

// SwiftUI wraps the state change in `withAnimation(RibbonMotion.arrive)` and
// every affected view animates. Compose animates values, not assignments, so
// the token becomes a spec handed to the animation that needs it — and under
// reduce motion it is a snap, which is a state change with no movement (§11).
// Both halves of that live in the token now, so this file asks for
// `RibbonMotion.arrive(reduceMotion)` rather than writing the branch out.

/**
 * Which composer is up, and for which verse.
 *
 * Swift nests this in `ReadingScreen` as an `enum ComposerState: Equatable`;
 * Kotlin has no nested types on a function, so it is a sealed interface in
 * the file. [ComposerMode] in NoteComposer is the same three states without
 * the address — this is the one that carries the verse with it.
 */
sealed interface ComposerState {
    data object Toolbar : ComposerState
    data class Write(val address: VerseAddress) : ComposerState
    data class Speak(val address: VerseAddress) : ComposerState
}

/**
 * The reading surface.
 *
 * @param openAt A named place to open at (a waiting row's note, a quoted
 *   verse) — null opens at your own position.
 * @param onStartAnother "Start another" at the finishing (§6.5) — lands in
 *   the chooser (S13), not back on the room's way-in.
 */
@Composable
fun ReadingScreen(
    model: AppModel,
    room: Room,
    reading: Reading,
    /**
     * How open the book is (design/Hearth.kt). The Wave at the foot of this
     * screen is one of the two handles on it — pull it down and the page goes
     * with the finger, over the room it came out of.
     */
    sheet: BookSheet,
    onClose: () -> Unit,
    /**
     * The page has already been put down by the finger that was holding it.
     * Distinct from [onClose], which *asks* for it to be put down — this one
     * arrives when the movement has already finished, and re-animating it
     * would be a second close over the first.
     */
    onDismissed: () -> Unit,
    onFinished: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
    openAt: VerseAddress? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    val book = remember(reading.bookID) { Bible.book(reading.bookID) }
    val chapterCount = book?.chapterCount ?: 1
    // The book's own words, which are the room's (A42). Not yours: a room
    // reads one version, so a note's quote and a mark on a phrase mean the
    // same thing in both hands.
    val translation = model.words(model.room(reading), reading)
    val bookText = remember(reading.bookID, translation) {
        model.scripture.book(reading.bookID, translation)
    }

    /** Chapters of a licensed translation, as they stream in (§16.8). */
    val remoteChapters = remember(reading.bookID, translation) {
        mutableStateMapOf<Int, ScriptureChapter>()
    }

    fun chapterContent(n: Int): ScriptureChapter? = bookText?.chapter(n) ?: remoteChapters[n]

    // Composition state
    var lifted by remember { mutableStateOf<VerseRange?>(null) }
    var liftedChapter by remember { mutableStateOf<Int?>(null) }

    /**
     * The verse you have this moment marked, so the page can draw the stroke
     * travelling rather than the wash simply being there.
     *
     * Held for exactly as long as the stroke takes and then let go. It is
     * deliberately about *your hand*, not about the highlight: one arriving
     * from the other person eases in where it lies, because it did not happen
     * here and pretending a stroke travelled across your page would be the
     * app acting out something that did not occur.
     */
    var justMarked by remember { mutableStateOf<VerseRange?>(null) }
    var composer by remember { mutableStateOf<ComposerState?>(null) }
    val recorder = remember(context) { VoiceRecorder(context) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    /**
     * Whether the one question about notifications is on screen (§6.1).
     *
     * Raised from the two moments the build book names — a note left, and a
     * note found — and never from anywhere else. `shouldAskAboutNotifications`
     * holds the rest of the conditions: never asked before, not already
     * granted, and somebody else in the room to name.
     */
    var askAboutNotifications by remember { mutableStateOf(false) }

    fun considerAsking() {
        val room = model.room(reading) ?: return
        if (model.shouldAskAboutNotifications(room)) askAboutNotifications = true
    }

    // Open note (one at a time; a stack opens whole)
    var openNoteVerse by remember { mutableStateOf<VerseAddress?>(null) }
    var noteCardHeight by remember { mutableStateOf(120.dp) }
    val noteSlotY = remember { mutableStateMapOf<Int, Dp>() }

    // Layout & tracking
    val chapterLayouts = remember { mutableStateMapOf<Int, ChapterLayout>() }
    var closing by remember { mutableStateOf(false) }

    /**
     * The live viewport height — "the upper third" must mean this screen's
     * third, not a phone's (a hardcoded 240 misplaces the position by half a
     * screen on a 13" tablet).
     */
    var viewportHeight by remember { mutableFloatStateOf(with(density) { 800.dp.toPx() }) }
    var lastFuelRecord by remember { mutableStateOf(Instant.DISTANT_PAST) }
    var lastPositionSave by remember { mutableStateOf(Instant.DISTANT_PAST) }
    var highlightLabel by remember { mutableStateOf<Highlight?>(null) }
    var didReachEnd by remember { mutableStateOf(false) }

    /**
     * After a follow ends, the form quietly offers "back to where you were"
     * for about two minutes, then forgets (§4.2). The pair and its window
     * live in PresenceForm with the rest of the follow.
     */
    val followBackOffer = rememberFollowBackOffer()

    /**
     * Ignore self-originated (programmatic) scrolls when deciding whether a
     * scroll of your own breaks a follow.
     */
    var programmaticScrollUntil by remember { mutableStateOf(Instant.DISTANT_PAST) }

    /**
     * How much room the presence panel needs beside the text. The panel
     * never covers Scripture, so the measure insets instead and the text
     * moves.
     */
    var presenceInset by remember { mutableStateOf(0.dp) }

    // Keyed on `sheet.committed` as well, and that is load-bearing: the page
    // is composed from the first millimetre of the pull that raises it, and
    // announcing yourself into the book because a thumb brushed the fire and
    // thought better of it would put "Jonathan is reading Mark" in front of
    // the whole room for a gesture that never happened.
    LaunchedEffect(room.id, model.me?.id, model.readingQuietly, sheet.committed) {
        if (!sheet.committed) return@LaunchedEffect
        if (!model.readingQuietly && model.me != null) {
            // The channel is the room's and is already open; this is the
            // book's half — saying you are in it (§4.2).
            val pos = openAt ?: model.myPosition(reading)
            model.presence.present(pos, 0.0, isIdle = false, following = model.followingPersonID)
        } else {
            model.presence.withdraw()
        }
    }

    DisposableEffect(room.id) {
        onDispose {
            // Out of the book, still in the room: the line stays open so the
            // room keeps hearing about itself.
            scope.launch { model.presence.withdraw() }
        }
    }

    val listState = rememberLazyListState()

    /**
     * The scroll container's coordinates, so a chapter's frame can be read
     * in the list's own space — SwiftUI's `.scrollView` coordinate space,
     * which Compose has no name for.
     */
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // A chapter and the passage end below it are two rows; Swift asks the
    // ScrollViewReader for `id: n` and this is the same address.
    fun itemIndexOfChapter(n: Int): Int = (n - 1) * 2

    /**
     * Swift keeps a `scrollCommand` in `@State` because a `ScrollViewReader`
     * proxy only exists inside its own closure. A `LazyListState` is an
     * ordinary value held here, so the command is just a call.
     *
     * This is the plain move, the one the passage end makes: Swift's
     * `onContinue` reaches for the proxy directly and claims no grace, so
     * carrying on to the next chapter is still a scroll of your own and
     * still breaks a follow (§4.2).
     */
    fun scrollToChapter(n: Int) {
        scope.launch {
            val index = itemIndexOfChapter(n).coerceIn(0, (chapterCount - 1) * 2)
            // Under reduce motion the page is simply already there (§11).
            if (reduceMotion) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        }
    }

    /**
     * The same move, but ours: the page moving because the app moved it —
     * following someone, or taking the offer back to where you were. Swift's
     * `scrollCommand` path opens the grace window for exactly these two, so
     * the app's own scroll does not read as yours and break the follow.
     */
    fun goToChapter(n: Int) {
        programmaticScrollUntil = Clock.System.now() + PROGRAMMATIC_SCROLL_GRACE
        scrollToChapter(n)
    }

    fun recordFuel(at: VerseAddress? = null) {
        lastFuelRecord = Clock.System.now()
        model.recordReadingActivity(
            reading = reading,
            address = at ?: model.myPosition(reading),
        )
    }

    /**
     * The book has been put down: the last things that belong to having been
     * in it. Separate from [close] because a page can also leave under a
     * finger, which is not a close *request* but a close that has happened.
     */
/**
     * Where this session started, so that closing the book without having
     * read moves nothing.
     *
     * The ribbon goes where you stopped — but opening the book, looking at
     * the page and closing it again is not stopping anywhere, and without
     * this it would drag the room's ribbon back to wherever you happened to
     * be. Worse than doing nothing: it would quietly undo somebody else's
     * ribbon on a glance.
     */
    val openedAt = remember(reading.id) { model.myPosition(reading) }

    fun laidDown() {
        if (!model.state.hasSeenMarginHint) {
            model.markMarginHintSeen()
        }
        recordFuel()
        // The ribbon goes where you stopped (A30). There is no control for
        // this because there is no separate act: closing the book *is* the
        // gesture, the same as it is with a ribbon in a physical Bible. Your
        // own position is written separately and is untouched — the book
        // still opens where you are (§6.2), never where the room is.
        //
        // Only if you actually went somewhere. See `openedAt`.
        val stoppedAt = model.myPosition(reading)
        if (stoppedAt.chapter != openedAt.chapter || stoppedAt.verse != openedAt.verse) {
            model.leaveTheRibbon(reading, stoppedAt)
        }
    }

    fun close() {
        if (closing) return
        closing = true
        laidDown()
        onClose()
    }

    /**
     * A close that was caught on its way down and did not finish.
     *
     * `close` latches so that a second press during the exit cannot fire it
     * twice — but the exit it hands off to is an animation, and an animation
     * a finger interrupts never reaches its own end. Without this, catching
     * the page mid-close left the latch on with the book still open: the
     * Wave's tap returned early, the back gesture was disabled, and there
     * was no way out of the book at all.
     */
    LaunchedEffect(sheet.committed) {
        if (sheet.committed) closing = false
    }

    /** The chapter list is up (A31). */
    var showChapters by remember { mutableStateOf(false) }

    /**
     * The chapter under the thumb, for the running head at the foot.
     *
     * Kept separately from the saved position because a position is written
     * at most every few seconds (see `trackReading`) and a running head that
     * lagged the page by three seconds would be telling you where you were.
     */
    var readingChapter by remember { mutableIntStateOf(model.myPosition(reading).chapter) }


    /**
     * The running head, repeated at the foot as the way into the chapter
     * list. Read from the tracked chapter rather than from the position so it
     * follows the page under the thumb, which is what a running head does.
     */
    val whereYouAre: String = remember(readingChapter, book) {
        book?.chapterHeading(readingChapter) ?: "${reading.bookID} $readingChapter"
    }

    fun clearLift() {
        lifted = null
        liftedChapter = null
        composer = null
        // The note being edited goes with the composer it was being edited
        // in, and it never used to. This is S05's own dismiss route — "the
        // toolbar is dismissed by tapping anywhere in the text" calls exactly
        // this — so opening your note, starting an edit and then tapping the
        // Scripture left `editingNote` set with nothing on screen holding it.
        // The next verse you long-pressed and wrote at opened the composer
        // pre-filled with the *old* note's words, and saving overwrote that
        // note's body while leaving nothing at all at the verse you had
        // picked. An abandoned edit has to be abandoned.
        editingNote = null
    }

    fun beginLift(chapter: Int, verse: Int) {
        liftedChapter = chapter
        lifted = VerseRange(
            bookID = reading.bookID, chapter = chapter, startVerse = verse, endVerse = verse,
        )
        composer = ComposerState.Toolbar
    }

    fun extendLift(chapter: Int, verse: Int) {
        if (liftedChapter != chapter) return
        val current = lifted ?: return
        val extended = VerseRange(
            bookID = reading.bookID,
            chapter = chapter,
            startVerse = minOf(current.startVerse, verse),
            endVerse = maxOf(current.endVerse, verse),
        )
        if (extended != current) {
            lifted = extended
        }
    }

    /**
     * One end of the lift moved, by a handle (S06) or by its tap equivalent.
     *
     * `char` is an offset into that verse's own text, or null for the whole
     * verse at that end — which is what the first or last word comes back as,
     * so marking a whole verse never quietly becomes a mark on all of its
     * words. The translation is stamped only while an offset is actually
     * being carried: those numbers mean nothing in anybody else's words, and
     * a range of whole verses has to stay exactly what it has always been on
     * the wire (A41g).
     */
    fun moveLiftEnd(atStart: Boolean, verse: Int, char: Int?) {
        val current = lifted ?: return
        val moved = if (atStart) {
            current.copy(startVerse = verse, startChar = char)
        } else {
            current.copy(endVerse = verse, endChar = char)
        }
        val stamped = if (moved.isWholeVerses) {
            moved.copy(charTranslation = null)
        } else {
            // The words this mark was made in are the book's, not the
            // marker's: that is what makes the offsets mean the same thing
            // in the other person's hands (A42).
            moved.copy(charTranslation = reading.translation)
        }
        if (stamped != current) {

            lifted = stamped
        }
    }

    fun closeNote() {
        openNoteVerse = null
        noteSlotY.clear()
    }

    fun toggleNote(address: VerseAddress) {
        if (openNoteVerse == address) {
            closeNote()
        } else {
            // Stale geometry from the last open note would place this one
            // wrong for a frame.
            noteSlotY.remove(address.chapter)
            openNoteVerse = address
            var found = false
            for (note in model.notes(reading, address.chapter)) {
                if (note.verse.verse == address.verse) {
                    model.markFound(note)
                    found = true
                }
            }
            // §6.1's second moment: a note has just been found. Asked here
            // rather than when the note was left, because this is the beat
            // the question is actually about — something was waiting, and you
            // only saw it because you happened to open the book.
            if (found) considerAsking()
        }
    }

    fun tapVerse(chapter: Int, verse: Int) {
        // Tapping the text: dismiss the toolbar first; then notes; then a
        // highlight's label.
        if (composer != null) {
            clearLift()
            return
        }
        val address = VerseAddress(bookID = reading.bookID, chapter = chapter, verse = verse)
        val stack = model.notes(reading, chapter).filter { it.verse.verse == verse }
        if (stack.isNotEmpty()) {
            toggleNote(address)
            return
        }
        model.highlights(reading, chapter)
            .firstOrNull { verse in it.range.verses }
            ?.let { highlightLabel = it }
    }

    fun follow(person: PresentPerson) {
        // Tap a portrait to follow — a page-fly, no confirmation dialog
        // (§4.2).
        followBackOffer.beganFollowing(model.myPosition(reading))
        model.followingPersonID = person.id
        person.position?.let { goToChapter(it.chapter) }
        // "Ruth is with you" is the other end of this, and it only ever
        // appears because the follow travels: without this the flag was set
        // on this phone and never left it.
        if (!model.readingQuietly) {
            scope.launch {
                model.presence.present(
                    position = model.myPosition(reading),
                    scrollFraction = 0.0,
                    isIdle = false,
                    following = person.id,
                )
            }
        }
    }

    fun trackReading(chapter: Int, frame: Rect) {
        // The chapter whose top has crossed the upper third is where you
        // are.
        val threshold = viewportHeight * 0.3f
        if (frame.top >= threshold || frame.bottom <= threshold) return
        val now = Clock.System.now()
        // Any scroll of your own breaks the follow — no modal, no "stop
        // following?", you just have your own scroll back (§4.2).
        if (model.followingPersonID != null && now > programmaticScrollUntil) {
            model.followingPersonID = null
        }
        readingChapter = chapter
        val layout = chapterLayouts[chapter]
        val yInChapter = with(density) { (threshold - frame.top).toDp() }
        val verse = layout?.verseFirstLineY
            ?.filterValues { it <= yInChapter }
            ?.maxByOrNull { it.value }
            ?.key ?: 1
        val address = VerseAddress(bookID = reading.bookID, chapter = chapter, verse = verse)
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if (now - lastPositionSave > POSITION_SAVE_INTERVAL) {
            lastPositionSave = now
            model.savePosition(reading = reading, address = address)
            if (!model.readingQuietly) {
                val fraction = (yInChapter.value / maxOf(1f, frame.height)).toDouble().coerceIn(0.0, 1.0)
                scope.launch {
                    model.presence.present(
                        position = address,
                        scrollFraction = fraction,
                        isIdle = false,
                        following = model.followingPersonID,
                    )
                }
            }
        }
        if (now - lastFuelRecord > FUEL_INTERVAL) {
            recordFuel(address)
        }
    }

    // Held rather than captured: the connection below is `remember`ed on the
    // list and the sheet, so a lambda captured into it would go on calling
    // the first composition's `laidDown`. Both of these are the same pair the
    // Wave's own drag ends on — putting the book down, and telling the room's
    // stack the page has gone.
    val laidDownNow by rememberUpdatedState(::laidDown)
    val dismissedNow by rememberUpdatedState(onDismissed)

    // The closing drag. Swift watches the scroll view's content offset go
    // past -90 while a finger is down; Compose has no negative offset to
    // read, so the same gesture is read where it actually happens — the
    // drag deltas the list cannot use because it is already at the top.
    //
    // `NestedScrollSource.UserInput` is what makes this a finger and not a
    // bounce: a momentum overscroll arrives as `SideEffect` and is ignored,
    // exactly as `fingerDown` gates the Swift.
    /**
     * True while *this* drag is taking the book down.
     *
     * `sheet.engaged` cannot answer it: that is true for as long as the page
     * exists, which is the whole time the book is open. This is the narrower
     * question — has a downward drag from the top of the page taken hold of
     * the book — and it is what lets the connection below claim upward
     * movement too, so that catching the page on its way down puts it back
     * instead of scrolling Scripture.
     */
    var closingByDrag by remember { mutableStateOf(false) }

    /**
     * Drag down from scroll-top and the book comes with you (S02).
     *
     * This used to count pixels and then *snap*: it accumulated a running
     * total, and past 90 dp it called `close()` outright. Nothing moved
     * under the finger — it deliberately consumed nothing, so the only
     * feedback was the list's own overscroll glow — and then the book simply
     * went. That is the whole of why the way out felt finicky: an invisible
     * threshold you cannot see approaching, cannot feel, and cannot back out
     * of, on the one gesture that is supposed to feel like closing a book.
     *
     * It predates the sheet. A20's argument is that the drag and the
     * animation are the same number, and this path was written before there
     * was one; the Wave next to it was converted and this was not. So it
     * drives [BookSheet] now, exactly as the fire does in reverse: the page
     * follows the finger from the first millimetre, release decides by
     * distance and velocity under the same physics, and a close caught
     * halfway eases back to where it was.
     *
     * The old 90 dp threshold is gone rather than retuned. `RibbonMotion`
     * already owns what "far enough" means, and a second opinion about it
     * living in this file is how the two halves of one gesture drift apart.
     */
    val closingDrag = remember(listState, sheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Once the book is coming down it owns the gesture in both
                // directions. Without this, pushing back up would scroll
                // Scripture underneath a page that is halfway off the screen.
                if (!closingByDrag) return Offset.Zero
                // Changed your mind and pushed it back up: once the page is
                // fully up the gesture is over, and the rest of the movement
                // belongs to Scripture again. Holding it until the finger
                // lifts would mean pulling down an inch, thinking better of
                // it, and finding the page frozen.
                if (available.y < 0f && sheet.progress >= 1f) {
                    closingByDrag = false
                    return Offset.Zero
                }
                sheet.drag(-available.y)
                return available
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // A downward drag the list could not spend is a drag at the
                // top of the page, which is the gesture S02 describes. A
                // momentum overscroll arrives as a different source and is
                // ignored, so a fling that lands at the top does not close
                // the book out from under the reader.
                if (available.y <= 0f) return Offset.Zero
                if (!closingByDrag) {
                    closingByDrag = true
                    sheet.engage()
                }
                sheet.drag(-available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!closingByDrag) return Velocity.Zero
                closingByDrag = false
                // Down is positive here and opening is positive there.
                sheet.release(
                    velocity = -available.y,
                    onOpened = {},
                    onClosed = {
                        laidDownNow()
                        dismissedNow()
                    },
                )
                return available
            }
        }
    }

    // Predictive back: the room peels in behind the closing book. The pull is
    // a progress, not a commitment — releasing mid-gesture eases the book back
    // down where it was, and committing hands the pull on to the slide that
    // takes it away, so the close is one movement rather than two.
    // Enabled for as long as the page is *on screen*, which is not the same
    // as being in the book. `committed` only becomes true when the opening
    // spring lands, roughly half a second after the way in is tapped — and
    // for that whole window nothing here held back, the NavController's own
    // callback is disabled at the start destination, and a system back
    // closed the app rather than the book. The same hole was open for the
    // length of every close. `close` latches, so a back during the close
    // is consumed and does nothing, which is what it should do.
    val peel = rememberBackPeel(enabled = sheet.engaged, onBack = { close() })

    // Opening, in two halves, because the page is raised before it is
    // entered. Where it opens is settled at once — the page has to rise
    // already showing the right chapter, not jump to it once it lands.
    LaunchedEffect(Unit) {
        val position = openAt ?: model.myPosition(reading)
        if (position.chapter > 1) {
            programmaticScrollUntil = Clock.System.now() + PROGRAMMATIC_SCROLL_GRACE
            listState.scrollToItem(itemIndexOfChapter(position.chapter))
        }
    }

    // And everything that means *being in the book* waits for the pull to
    // commit: feeding the fire off an abandoned drag would be the app
    // recording a reading that did not happen.
    LaunchedEffect(sheet.committed) {
        if (!sheet.committed) return@LaunchedEffect
        recordFuel()
    }

    Box(
        modifier
            .fillMaxSize()
            // Esc closes the book on a hardware keyboard, as
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
            },
    ) {
        // The room the book is closing into. Swift hangs `.room()` on the
        // reading surface itself; here it is the layer underneath, so the
        // back gesture has something true to peel the book off.
        Box(Modifier.fillMaxSize().room())

        Box(
            Modifier
                .fillMaxSize()
                // The pull is read inside the layer block, so a back gesture
                // moves the book without recomposing a word of Scripture.
                // Under reduce motion the book does not move at all; the
                // gesture still closes it (§11).
                .peeled { peel.progress }
                .background(Palette.ground)
                .grain(),
        ) {
            val safeArea = WindowInsets.safeDrawing.asPaddingValues()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(closingDrag)
                    .onSizeChanged { size ->
                        if (size.height > 0) viewportHeight = size.height.toFloat()
                    }
                    .onGloballyPositioned { container = it },
                contentPadding = PaddingValues(
                    top = 26.dp + safeArea.calculateTopPadding(),
                    bottom = BOTTOM_CHROME_ROOM + safeArea.calculateBottomPadding(),
                ),
                // Swift asks for `.scrollIndicators(.hidden)`; a LazyColumn
                // draws none, so the hidden indicator is simply the default
                // and there is nothing to turn off.
            ) {
                for (n in 1..chapterCount) {
                    item(key = "chapter-$n") {
                        ChapterSection(
                            model = model,
                            room = room,
                            reading = reading,
                            book = book,
                            n = n,
                            chapter = chapterContent(n),
                            translation = translation,
                            container = container,
                            layout = chapterLayouts[n] ?: ChapterLayout(),
                            openNoteVerse = openNoteVerse,
                            noteSlotY = noteSlotY[n],
                            noteCardHeight = noteCardHeight,
                            lifted = if (liftedChapter == n) lifted else null,
                            justMarked = justMarked
                                ?.takeIf { it.chapter == n && it.bookID == reading.bookID },
                            onMarkDrawn = { justMarked = null },
                            measureInset = presenceInset,
                            onRemoteChapter = { remoteChapters[n] = it },
                            onLayout = { chapterLayouts[n] = it },
                            onNoteSlot = { noteSlotY[n] = it },
                            onNoteCardHeight = { height ->
                                if (abs((height - noteCardHeight).value) > 1f) {
                                    noteCardHeight = height
                                }
                            },
                            onFrame = { frame -> trackReading(n, frame) },
                            onLongPressVerse = { verse -> beginLift(n, verse) },
                            onDragToVerse = { verse -> extendLift(n, verse) },
                            onExtend = { atStart, verse, char -> moveLiftEnd(atStart, verse, char) },
                            onTapVerse = { verse -> tapVerse(n, verse) },
                            onToggleNote = { address -> toggleNote(address) },
                            onTakeBack = { note, stackSize ->
                                model.takeBack(note)
                                if (stackSize <= 1) closeNote()
                            },
                            onEdit = { note ->
                                editingNote = note
                                composer = ComposerState.Write(note.verse)
                            },
                        )
                    }
                    if (n < chapterCount) {
                        item(key = "passage-end-$n") {
                            PassageEnd(
                                reading = reading,
                                chapter = n,
                                model = model,
                                nextChapterTitle = book?.chapterHeading(n + 1) ?: "${n + 1}",
                                onContinue = { scrollToChapter(n + 1) },
                                onClose = ::close,
                                modifier = Modifier.readingMeasure(presenceInset),
                            )
                        }
                    }
                }
                item(key = "finishing") {
                    FinishingSection(
                        reading = reading,
                        bookName = book?.name ?: "",
                        container = container,
                        viewportHeight = viewportHeight,
                        onReachEnd = {
                            // Finishing means reaching the end (§6.5), not a
                            // lazy list prefetching it: the sequence counts
                            // only once it is actually inside the viewport.
                            if (!didReachEnd) {
                                didReachEnd = true
                                if (!reading.isFinished) model.finishReading(reading)
                            }
                        },
                        onFinished = onFinished,
                        onStartAnother = onStartAnother,
                        modifier = Modifier.readingMeasure(presenceInset),
                    )
                }
            }

            if (!room.isPaused) {
                PresenceForm(
                    model = model,
                    room = room,
                    onFollow = ::follow,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    measure = Measure.reading,
                    onMeasureInset = { presenceInset = it },
                )
            }

            // The thread down the edge is a full-height hairline that used
            // to be switched on and off. Following somebody and stopping are
            // among the quietest things in the product (§4.2); neither is a
            // cut.
            AnimatedVisibility(
                visible = model.followingPersonID != null,
                enter = fadeIn(RibbonMotion.arrive(reduceMotion)),
                exit = fadeOut(RibbonMotion.arrive(reduceMotion)),
                modifier = Modifier.align(Alignment.TopEnd),
                label = "the-follow-thread",
            ) {
                FollowThread()
            }

            // The way out, or the composer.
            BottomChrome(
                whereYouAre = whereYouAre,
                onOpenChapters = { showChapters = true },
                sheet = sheet,
                onDragClosed = {
                    laidDown()
                    onDismissed()
                },
                model = model,
                room = room,
                reading = reading,
                composer = composer,
                lifted = lifted,
                liftedChapter = liftedChapter,
                editingNote = editingNote,
                recorder = recorder,
                followBackOffer = followBackOffer,
                onHighlight = { range, ink ->
                    model.addHighlight(range, ink, reading)
                    justMarked = range
                    clearLift()
                },
                // Said outright at both entry points as well, rather than
                // relying on `clearLift` having been called first: a composer
                // opened from the toolbar is a *new* note, and the one way
                // this defect gets back in is somebody adding a third route
                // in that does not go through the dismiss.
                onWrite = { address ->
                    editingNote = null
                    composer = ComposerState.Write(address)
                },
                onSpeak = { address ->
                    editingNote = null
                    composer = ComposerState.Speak(address)
                },
                onSaveWritten = { address, body ->
                    // Belt to the brace above: an edit only counts as one if
                    // it is still about the verse the note lives at. Anything
                    // else is a new note, wherever `editingNote` came from.
                    val note = editingNote?.takeIf { it.verse == address }
                    if (note != null) {
                        model.editWrittenNote(note, body)
                    } else {
                        model.leaveWrittenNote(body, address, reading)
                        // The first of §6.1's two moments. Never on an edit:
                        // nothing new was left for anybody.
                        considerAsking()
                    }
                    editingNote = null
                    clearLift()
                },
                onCancelWritten = {
                    editingNote = null
                    clearLift()
                },
                onKeepVoice = { address, file, waveform ->
                    model.leaveVoiceNote(file, waveform, address, reading)
                    considerAsking()
                    clearLift()
                },
                onDismissVoice = ::clearLift,
                onGoBack = { address -> goToChapter(address.chapter) },
                onClose = ::close,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            HighlightLabelOverlay(
                model = model,
                highlight = highlightLabel,
                onRemove = { highlight ->
                    model.removeHighlight(highlight)
                    highlightLabel = null
                },
                onDismiss = { highlightLabel = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // §6.1's one question about notifications, asked in context and once.
    //
    // Outside the page's Box for the same reason the chapter list is: it is a
    // dialog over the book, not a thing on the page. It uses the app's own
    // confirmation rather than a system rationale sheet, because the words
    // §6.1 specifies are Ribbon's and the platform's dialog can only carry
    // Android's.
    if (askAboutNotifications) {
        val room = model.room(reading)
        val name = room?.let { model.whoTheAskIsAbout(it) }
        if (room == null || name == null) {
            // Nobody to name means nothing to ask. Should not happen —
            // `shouldAskAboutNotifications` requires a second member — but a
            // question with a blank in it is the one outcome worth a guard.
            askAboutNotifications = false
        } else {
            NotificationAsk(
                name = name,
                onAnswered = {
                    // Asked, whatever the answer. There is no second ask.
                    model.markAskedAboutNotifications()
                    askAboutNotifications = false
                },
            )
        }
    }

    // Everywhere else in this book (A31). Outside the page's own Box so the
    // sheet is not clipped by it, and so the page keeps drawing underneath
    // exactly as the chooser leaves the room drawing underneath.
    if (showChapters) {
        ChaptersSheet(
            model = model,
            reading = reading,
            onDismiss = { showChapters = false },
            onGo = { address ->
                showChapters = false
                // The same jump the follow uses, so arriving from the list and
                // arriving from somebody else's shoulder land the same way —
                // and the grace window keeps the scroll from being read as a
                // scroll of your own, which would break a follow in progress.
                goToChapter(address.chapter)
            },
        )
    }
}

// MARK: Chapters

/**
 * One chapter, the marks in its gutter and the note open in it.
 *
 * @param chapter null while a licensed translation's chapter is still on its
 *   way, or on the unreachable state where the text is neither local nor
 *   fetchable.
 * @param onTakeBack the note, and how many were in its stack — a stack of
 *   one closes when its last note is taken back.
 */
@Composable
private fun ChapterSection(
    model: AppModel,
    room: Room,
    reading: Reading,
    book: BibleBook?,
    n: Int,
    chapter: ScriptureChapter?,
    translation: TranslationID,
    container: LayoutCoordinates?,
    layout: ChapterLayout,
    openNoteVerse: VerseAddress?,
    noteSlotY: Dp?,
    noteCardHeight: Dp,
    lifted: VerseRange?,
    justMarked: VerseRange?,
    onMarkDrawn: () -> Unit,
    measureInset: Dp,
    onRemoteChapter: (ScriptureChapter) -> Unit,
    onLayout: (ChapterLayout) -> Unit,
    onNoteSlot: (Dp) -> Unit,
    onNoteCardHeight: (Dp) -> Unit,
    onFrame: (Rect) -> Unit,
    onLongPressVerse: (Int) -> Unit,
    onDragToVerse: (Int) -> Unit,
    onExtend: (Boolean, Int, Int?) -> Unit,
    onTapVerse: (Int) -> Unit,
    onToggleNote: (VerseAddress) -> Unit,
    onTakeBack: (Note, Int) -> Unit,
    onEdit: (Note) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val runningHead = book?.chapterHeading(n) ?: "${reading.bookID} $n"

    // A licensed chapter fades in when it lands; a bundled one is already
    // here, so this starts at 1 and never animates. SwiftUI gets the same
    // thing from `withAnimation(RibbonMotion.arrive)` around the assignment,
    // and it has to be read here — above the branch — or the value would be
    // born at 1 on the frame the words arrive and there would be no fade.
    val arrival by animateFloatAsState(
        targetValue = if (chapter != null) 1f else 0f,
        animationSpec = RibbonMotion.arrive<Float>(reduceMotion),
        label = "chapter-arrival",
    )

    if (chapter != null) {
        Box(
            modifier = Modifier
                .readingMeasure(measureInset)
                .alpha(arrival)
                .padding(bottom = 8.dp)
                .trackedIn(container, onFrame),
            contentAlignment = Alignment.TopStart,
        ) {
            ChapterText(
                chapter = chapter,
                runningHead = runningHead,
                theme = ReadingTheme(
                    fontSize = model.settings.scriptureSize.toFloat(),
                    lineHeightMultiple = model.settings.lineHeightMultiple.toFloat(),
                    redLetter = model.settings.redLetter,
                ),
                marks = verseMarks(model, reading, n),
                lifted = lifted,
                justMarked = justMarked,
                onMarkDrawn = onMarkDrawn,
                openNote = openNoteVerse
                    ?.takeIf { it.chapter == n }
                    ?.let { OpenNote(verse = it.verse, height = noteCardHeight) },
                isFirstChapter = n == 1,
                showMarginHint = !model.state.hasSeenMarginHint && n == 1,
                onLayout = onLayout,
                onLongPressVerse = onLongPressVerse,
                onDragToVerse = onDragToVerse,
                onExtend = onExtend,
                onDragEnded = {},
                onTapVerse = onTapVerse,
                onNoteSlot = onNoteSlot,
            )

            // The gutter (left edge — notes only).
            val byVerse = model.notes(reading, n).groupBy { it.verse.verse }
            for (verse in byVerse.keys.sorted()) {
                val y = layout.verseFirstLineY[verse] ?: continue
                val stack = byVerse[verse] ?: continue
                GutterStack(
                    model = model,
                    notes = stack,
                    roomID = room.id,
                    onTap = {
                        onToggleNote(
                            VerseAddress(bookID = reading.bookID, chapter = n, verse = verse),
                        )
                    },
                    modifier = Modifier.positioned(x = GUTTER_X, y = y),
                )
            }

            // The note, open in the carve the text made for it.
            val address = openNoteVerse?.takeIf { it.chapter == n }
            val shown = address != null && noteSlotY != null
            // Held through the fade out, so the card does not vanish before
            // it has finished leaving — SwiftUI's `.transition(.opacity)`
            // keeps the removed view alive for exactly this reason. Its slot
            // is held with it: opening a second note in the same chapter
            // clears the slot before the new geometry lands, and a card that
            // read the live value would slide up to the chapter's first line
            // to die there.
            val held = remember { mutableStateOf(address) }
            val heldSlot = remember { mutableStateOf(noteSlotY) }
            if (shown) {
                held.value = address
                heldSlot.value = noteSlotY
            }
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(RibbonMotion.settle<Float>(reduceMotion)),
                exit = fadeOut(RibbonMotion.settle<Float>(reduceMotion)),
            ) {
                val open = held.value
                if (open != null) {
                    val stack = model.notes(reading, n).filter { it.verse.verse == open.verse }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The lambda overload on purpose: the slot is
                            // snapshot state, and reading it here defers the
                            // read to layout instead of recomposing the card
                            // every time the carve settles.
                            .offset { IntOffset(0, (heldSlot.value ?: 0.dp).roundToPx()) }
                            .padding(start = 36.dp, end = 26.dp)
                            .onSizeChanged { size ->
                                onNoteCardHeight(with(density) { size.height.toDp() })
                            },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (note in stack) {
                            NoteCard(
                                model = model,
                                note = note,
                                author = model.person(note.authorID),
                                authorInk = model.membership(note.authorID, room.id)?.ink
                                    ?: Ink.clay,
                                onTakeBack = { onTakeBack(note, stack.size) },
                                onEdit = { onEdit(note) },
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val licensed = TranslationRegistry.translation(translation)
    if (licensed != null && !licensed.isBundled) {
        // A licensed translation's chapter, genuinely fetching (S02): the
        // running head appears and the body fades in — no skeleton lines,
        // which read as fake text. (§12.2 forbids a loading indicator, and
        // this is what stands in its place: nothing, and then the words.)
        // The fetch's outcome is held rather than dropped. It used to be
        // `ensureRemoteChapter(...)?.let(onRemoteChapter)` — and that function
        // swallows every failure into a null — so offline, or on a 503, the
        // `?.let` did nothing, the effect's keys never changed so it could
        // never retry, and nothing watched the network. The page was a
        // running head over 320 dp of nothing, with no line and no way
        // forward (S25's "book won't download").
        var missed by remember(n, licensed) { mutableStateOf(false) }
        var attempt by remember(n, licensed) { mutableIntStateOf(0) }
        Column(
            modifier = Modifier
                .readingMeasure(measureInset)
                .padding(start = 36.dp),
        ) {
            SmallCaps(runningHead, size = 14f, color = Palette.text.copy(alpha = 0.4f))
            if (missed) {
                // §08's shape: name it, name what is intact, offer the one
                // action that helps. The same two parts a failed transcript
                // already uses.
                Text(
                    text = Copy.chapterWouldntCome(
                        Bible.book(reading.bookID)?.name ?: reading.bookID,
                    ),
                    style = RibbonType.ui(15f),
                    color = Palette.muted,
                    modifier = Modifier.padding(top = 18.dp),
                )
                QuietControl(
                    title = Copy.TRY_AGAIN,
                    modifier = Modifier.offset(x = (-8).dp),
                ) { attempt++ }
                Spacer(Modifier.height(180.dp))
            } else {
                // Waiting is wordless: §08 forbids a loading indicator, and a
                // skeleton reads as fake text.
                Spacer(Modifier.height(320.dp))
            }
        }
        // Keyed on the network as well, so a connection coming back retries
        // without anybody having to tap anything.
        LaunchedEffect(n, licensed, attempt, model.isOnline) {
            val address = VerseAddress(bookID = reading.bookID, chapter = n, verse = 1)
            val chapter = model.scripture.ensureRemoteChapter(context, address, licensed)
            if (chapter != null) {
                missed = false
                onRemoteChapter(chapter)
            } else {
                missed = true
            }
        }
        return
    }

    // Text is local or it isn't shown (S02): with bundled translations this
    // is unreachable, but the state exists.
    Text(
        text = Copy.bookNotDownloaded(book?.name ?: reading.bookID),
        style = RibbonType.ui(15f),
        color = Palette.muted,
        modifier = Modifier.readingMeasure(measureInset).padding(40.dp),
    )
}

/**
 * Every highlight on this chapter, as a mark per verse.
 *
 * A mark that names part of a verse only names it in the translation it was
 * made in, because translation belongs to a *person* (S20) and two people in
 * one room can be reading different words for the same verse. When they do
 * not match, the mark widens to the whole verse rather than pointing at words
 * that are not on this page: it says truthfully that somebody marked
 * something here, which is the honest half of what it knows (A41g).
 */
private fun verseMarks(model: AppModel, reading: Reading, chapter: Int): List<VerseMark> {
    val readingIn = reading.translation
    val result = mutableListOf<VerseMark>()
    for (highlight in model.highlights(reading, chapter)) {
        val range = highlight.range
        val readable = range.isWholeVerses ||
            (range.charTranslation != null && range.charTranslation == readingIn)
        for (verse in range.verses) {
            result += VerseMark(
                verse = verse,
                from = if (readable && verse == range.startVerse) range.startChar else null,
                to = if (readable && verse == range.endVerse) range.endChar else null,
                ink = highlight.ink,
            )
        }
    }
    return result
}

/**
 * The marks for one verse, stacked in the gutter.
 *
 * Up to three marks stack; beyond that, three plus a dot triplet (§4.4).
 * Announced by author, never by count (§11).
 */
@Composable
private fun GutterStack(
    model: AppModel,
    notes: List<Note>,
    roomID: Uuid,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine = model.me?.id
    // Swift compares against `model.me?.id ?? UUID()` so a person who does
    // not exist yet has found nothing; a null id here says the same thing
    // without minting a throwaway.
    fun foundByMe(note: Note): Boolean = mine != null && mine in note.foundBy

    // §11, exactly: "Note from Ruth, verse 9, not yet found." A stack
    // announces by author and never by count.
    // §11 quotes the shape of this label exactly — "Note from Ruth, verse 9,
    // not yet found" — and it was announcing "Note from Ruth Alderman". The
    // dedupe below now dedupes on the spoken form, which is the right one.
    val names = notes.mapNotNull { model.person(it.authorID)?.name?.let(::firstName) }
    val unfound = notes.any { !foundByMe(it) && it.authorID != mine }
    val label = Copy.marginNotes(
        authors = names.toSortedSet().toList(),
        verse = notes.firstOrNull()?.verse?.verse ?: 0,
        several = notes.size > 1,
        unfound = unfound,
    )

    Box(
        modifier = modifier
            // The marks are drawn small; the finger's target is not (§11).
            // Swift widens the hit area with `contentShape(inset: -16)`,
            // which reaches past the screen's edge on the left and under the
            // text on the right. Compose does not deliver a touch outside a
            // parent's bounds, and a target that reached under the text
            // would take taps away from Scripture — so the mark keeps the
            // full 44 dp down the axis a thumb actually misses in, and takes
            // the gutter's own width across. The gutter holds that width at
            // every type size (§08).
            .sizeIn(minWidth = GUTTER_X * 2, minHeight = 44.dp)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onTap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (note in notes.take(3)) {
                NoteMark(
                    kind = note.kind,
                    ink = model.membership(note.authorID, roomID)?.ink ?: model.lastUsedInk,
                    found = foundByMe(note),
                    mine = note.authorID == mine,
                    pending = note.isPending,
                )
            }
            if (notes.size > 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(2.dp)
                                .background(Palette.muted, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

// MARK: Bottom chrome — the way out, or the composer

@Composable
private fun BottomChrome(
    model: AppModel,
    room: Room,
    reading: Reading,
    sheet: BookSheet,
    onDragClosed: () -> Unit,
    composer: ComposerState?,
    lifted: VerseRange?,
    liftedChapter: Int?,
    editingNote: Note?,
    recorder: VoiceRecorder,
    followBackOffer: FollowBackOfferState,
    onHighlight: (VerseRange, Ink) -> Unit,
    onWrite: (VerseAddress) -> Unit,
    onSpeak: (VerseAddress) -> Unit,
    onSaveWritten: (VerseAddress, String) -> Unit,
    onCancelWritten: () -> Unit,
    onKeepVoice: (VerseAddress, File, List<Float>) -> Unit,
    onDismissVoice: () -> Unit,
    onGoBack: (VerseAddress) -> Unit,
    onClose: () -> Unit,
    /** The running head, repeated at the foot as the way into the chapters. */
    whereYouAre: String,
    onOpenChapters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            // Edge-to-edge is the rule (§12.2), so the bottom inset is
            // asked for here rather than assumed: under gesture navigation
            // this is a few dp, and under three-button navigation it is the
            // whole bar — the Wave never sits under it either way.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // The toolbar rises and leaves on its own transitions, which
        // NoteComposer hands over as values because in Compose an
        // enter/exit belongs to the parent that decides whether the child is
        // there at all. The range is held through the exit so the bar does
        // not blank out halfway off the screen.
        val range = if (composer is ComposerState.Toolbar && lifted != null && liftedChapter != null) {
            VerseRange(
                bookID = reading.bookID,
                chapter = liftedChapter,
                startVerse = lifted.startVerse,
                endVerse = lifted.endVerse,
            )
        } else {
            null
        }
        val heldRange = remember { mutableStateOf(range) }
        if (range != null) heldRange.value = range
        AnimatedVisibility(
            visible = range != null,
            enter = leaveToolbarEnter(),
            exit = leaveToolbarExit(),
        ) {
            val bar = heldRange.value
            if (bar != null) {
                LeaveToolbar(
                    model = model,
                    room = room,
                    range = bar,
                    roomPaused = room.isPaused,
                    onHighlight = { ink -> onHighlight(bar, ink) },
                    onWrite = { onWrite(bar.start) },
                    onSpeak = { onSpeak(bar.start) },
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }

        // The three things that share the foot of the page cross-fade rather
        // than cut.
        //
        // This was a bare `when (composer)` with no transition of any kind,
        // sitting in the same bottom-aligned box as the toolbar's carefully
        // animated `AnimatedVisibility` — so the instant a verse was
        // long-pressed the Wave and the running-head pill blinked out of
        // existence in one frame while the toolbar slid up over the hole they
        // had left. The composer and its keyboard had no entrance either.
        // §9.1 has no cuts in it, and the one the reading surface actually
        // performs was the loudest in the app.
        //
        // Keyed on the *kind* rather than on the composer itself, so typing
        // into the write composer — which changes nothing about which thing
        // is on screen — does not restart the transition.
        val reduceMotion = rememberReduceMotion()
        val stage = when (composer) {
            null -> BottomStage.TheWayOut
            is ComposerState.Toolbar -> BottomStage.Toolbar
            is ComposerState.Write -> BottomStage.Write
            is ComposerState.Speak -> BottomStage.Speak
        }
        // The leaving branch still needs an address to draw with, exactly as
        // the toolbar's range is held above.
        val heldComposer = remember { mutableStateOf(composer) }
        if (composer != null) heldComposer.value = composer

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                fadeIn(RibbonMotion.arrive(reduceMotion)) togetherWith
                    fadeOut(RibbonMotion.arrive(reduceMotion))
            },
            label = "the-foot-of-the-page",
        ) { showing ->
            when (showing) {
            BottomStage.Toolbar -> Unit

            BottomStage.Write -> {
                val address = (heldComposer.value as? ComposerState.Write)?.address
                    ?: return@AnimatedContent
                WriteComposer(
                    verse = address,
                    initialText = editingNote?.body ?: "",
                    identity = editingNote?.id?.toString() ?: address.formatted,
                    onSave = { body -> onSaveWritten(address, body) },
                    onCancel = onCancelWritten,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            BottomStage.Speak -> {
                val address = (heldComposer.value as? ComposerState.Speak)?.address
                    ?: return@AnimatedContent
                SpeakControl(
                    model = model,
                    ink = model.inkForNewHighlight(room) ?: model.lastUsedInk,
                    recorder = recorder,
                    onKeep = { file, waveform -> onKeepVoice(address, file, waveform) },
                    onDismiss = onDismissVoice,
                    modifier = Modifier
                        .readableColumn()
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 14.dp),
                )
            }

            BottomStage.TheWayOut -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    // After a follow ends: the quiet offer back, for about
                    // two minutes, then it forgets (§4.2).
                    FollowBackOffer(
                        offer = followBackOffer,
                        following = model.followingPersonID != null,
                        onGoBack = onGoBack,
                    )
                    // The way out: the Wave, ~20 dp, muted ivory, centred at
                    // the bottom edge. Nothing else down there. The glass
                    // capsule stays small; the touch target doesn't — a
                    // finger must be able to close the book (44 dp minimum).
                    //
                    // It is also the handle: pull it down and the page goes
                    // with the finger, back over the room. The tap is
                    // untouched — §11 is explicit that no way out of the book
                    // may be a gesture only, and this control has been the
                    // tap since S02 was written. What is new is that the tap
                    // and the drag now run the same movement rather than two
                    // (design/Hearth.kt).
                    // Where you are, and the way to anywhere else in this
                    // book (A31).
                    //
                    // S02 says the way out is the Wave and "nothing else
                    // down there", and this is a deliberate second thing —
                    // owner's call. The gap it fills is real: a book opens at
                    // your own position and is read forward, so reaching
                    // Mark 10 from Mark 1 meant scrolling nine chapters.
                    //
                    // It keeps its distance from the rule it bends. The Wave
                    // still has the bottom edge to itself; this sits above
                    // it, in the same glass, at small-caps size and low
                    // contrast — it says where you are, which is what a
                    // running head does, and it is a door only if you press
                    // it.
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 88.dp, minHeight = 44.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = Copy.CHAPTERS,
                                onClick = onOpenChapters,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .ribbonGlass(CircleShape)
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        ) {
                            SmallCaps(
                                whereYouAre,
                                size = 11f,
                                color = Palette.text.copy(alpha = 0.5f),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 88.dp, minHeight = 52.dp)
                            // The drag's own end, not the tap's: by the time
                            // this runs the page is already down, so it
                            // reports rather than asks.
                            .closesTheBook(sheet = sheet, onClosed = onDragClosed)
                            .clickable(onClick = onClose)
                            .semantics {
                                contentDescription = Copy.CLOSE_THE_BOOK
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .ribbonGlass(CircleShape)
                                .padding(horizontal = 26.dp, vertical = 9.dp),
                        ) {
                            WaveMark(size = 20.dp, tint = Palette.text.copy(alpha = 0.55f))
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Which of the three things that share the foot of the page is showing.
 *
 * The `when` this replaces branched on `ComposerState` itself, which changes
 * identity every keystroke in the write composer — so an `AnimatedContent`
 * keyed on it would restart its transition as somebody typed. This is the
 * only distinction the transition is about.
 */
private enum class BottomStage { TheWayOut, Toolbar, Write, Speak }

/**
 * A small label naming who made a highlight, and remove if it's yours (S06).
 *
 * SwiftUI keeps the value alive through `.transition(.opacity)`; Compose
 * needs the last one held by hand so the fade out has something to draw.
 */
@Composable
private fun HighlightLabelOverlay(
    model: AppModel,
    highlight: Highlight?,
    onRemove: (Highlight) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val shown = remember { mutableStateOf<Highlight?>(null) }
    if (highlight != null) shown.value = highlight

    AnimatedVisibility(
        visible = highlight != null,
        enter = fadeIn(RibbonMotion.arrive<Float>(reduceMotion)),
        exit = fadeOut(RibbonMotion.arrive<Float>(reduceMotion)),
        modifier = modifier,
    ) {
        val label = shown.value ?: return@AnimatedVisibility
        // It says its piece and goes; nothing has to be dismissed by hand,
        // though a tap does it too.
        LaunchedEffect(label.id) {
            delay(HIGHLIGHT_LABEL_LIFETIME)
            onDismiss()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .ribbonGlass(RoundedCornerShape(16.dp))
                // It goes on its own after a moment, and a tap sends it early
                // — but the tap was unannounced, so the one way to dismiss it
                // deliberately was invisible to a screen reader (§11).
                .clickable(
                    role = Role.Button,
                    onClickLabel = Copy.DISMISS,
                    onClick = onDismiss,
                )
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkDot(ink = label.ink)
                Text(
                    text = model.person(label.authorID)?.name ?: "",
                    style = RibbonType.ui(15f),
                    color = Palette.text,
                )
            }
            if (label.authorID == model.me?.id) {
                QuietControl(
                    title = Copy.REMOVE,
                    onClick = { onRemove(label) },
                    size = 12f,
                )
            }
        }
    }
}

// MARK: The finishing sequence (§6.5)

/**
 * The one place that gets to feel like an event — and it still has no
 * confetti, no badge, and no number.
 */
@Composable
private fun FinishingSection(
    reading: Reading,
    bookName: String,
    container: LayoutCoordinates?,
    viewportHeight: Float,
    onReachEnd: () -> Unit,
    onFinished: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .trackedIn(container) { frame ->
                if (frame.top < viewportHeight * 0.85f) onReachEnd()
            },
    ) {
        Spacer(Modifier.height(70.dp))
        FireBecomesEmber(
            scale = reading.handiwork.scale,
            coalDepth = reading.handiwork.coalDepth,
        )
        Text(
            text = bookName,
            style = RibbonType.display(30f),
            color = Palette.text,
            modifier = Modifier.semantics { heading() },
        )
        SmallCaps(
            text = RibbonClock.emberRange(
                start = reading.startedAt,
                end = reading.finishedAt ?: Clock.System.now(),
            ),
            size = 13f,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 60.dp).padding(top = 16.dp),
        ) {
            WayInButton(title = Copy.PUT_IT_ON_THE_SHELF, onClick = onFinished)
            QuietControl(title = Copy.START_ANOTHER, onClick = onStartAnother)
        }
        Spacer(Modifier.height(80.dp))
    }
}

/**
 * S03 — the passage end: the one place with more than one thing to do.
 * Generous space, a hairline rule at the measure's width, the card (S08/S09),
 * the continue control, and the Wave larger here as the deliberate close.
 */
@Composable
fun PassageEnd(
    reading: Reading,
    chapter: Int,
    model: AppModel,
    nextChapterTitle: String,
    onContinue: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = model.room(reading)
    val reduceMotion = rememberReduceMotion()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(34.dp))
        HairlineRule(Modifier.padding(start = 36.dp, end = 26.dp))

        if (room != null) {
            val card = model.card(reading, chapter)
            // Held across its own exit, the way every other leaving thing in
            // this file is (`held`, `heldSlot`, `heldRange`) — and the cards
            // were the one surface that was not. `ReflectionCardView` opens
            // with a guard that returns on a set-down card, and the content
            // lambda recomposes with the new state *during* the exit, so the
            // question, the answers and the controls vanished on the frame the
            // tap landed and an empty container shrank behind them. A retired
            // card should leave looking like a card.
            val held = remember(reading.id, chapter) { mutableStateOf(card) }
            if (card.state != CardState.setDown) held.value = card
            // Setting a card down used to take it out of the composition on
            // the frame the tap landed, which is a cut — and §9.1 has no cuts
            // in it. §4.6's "it leaves without ceremony" is about there being
            // no dialog and no confirmation, not about the card vanishing
            // from under the finger that retired it: it shrinks away on the
            // settle token, the same way a note that has been taken back
            // does, and the continue control comes up to meet the rule.
            AnimatedVisibility(
                visible = card.state != CardState.setDown,
                // Nothing on the way in: a card that is simply there when you
                // reach the end of a chapter has not arrived, it was always
                // waiting. Only the leaving is an event.
                enter = EnterTransition.None,
                exit = fadeOut(RibbonMotion.settle<Float>(reduceMotion)) +
                    shrinkVertically(RibbonMotion.settle<IntSize>(reduceMotion)),
                label = "the-card-set-down",
            ) {
                ReflectionCardView(
                    card = held.value,
                    reading = reading,
                    room = room,
                    model = model,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // The app's forward motion — the one control that carries you out of
        // a finished chapter and into the next — announced as a line of
        // type, because it had a target and a tap and never said what it
        // was. A39a's own finding, on the other end of the same page.
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                .clickable(role = Role.Button, onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = nextChapterTitle,
                style = RibbonType.ui(17f, FontWeight.Medium),
                color = Palette.text,
            )
        }
        // The Wave is drawn at 28 dp; its touch target is not (the
        // pencil-only close on iPad was exactly this).
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(52.dp)
                .clickable(onClick = onClose)
                .semantics {
                    contentDescription = Copy.CLOSE_THE_BOOK
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            WaveMark(size = 28.dp, tint = Palette.text.copy(alpha = 0.45f))
        }
        Spacer(Modifier.height(30.dp))
    }
}

// MARK: Modifiers this screen needs

/**
 * The measure: Scripture holds a readable line length on any canvas — the
 * reading surface is the product, and a 150-character line is not reading.
 *
 * [inset] is the room the presence panel asked for on the trailing side. The
 * text moves rather than being covered: glass never lies over a verse, and
 * neither does the raised panel that stands in for it here.
 */
private fun Modifier.readingMeasure(inset: Dp): Modifier =
    this
        .padding(end = inset)
        .readableColumn(maxWidth = Measure.reading)

/**
 * SwiftUI's `.position(x:y:)`: the child is centred on the point rather than
 * placed by its top-left corner. Compose has no equivalent, and an `offset`
 * would need the child's own size to mean the same thing, so the placement
 * is done where the size is already known.
 */
private fun Modifier.positioned(x: Dp, y: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place(
            x = x.roundToPx() - placeable.width / 2,
            y = y.roundToPx() - placeable.height / 2,
        )
    }
}

/**
 * A child's frame in the scroll container's own space — SwiftUI's
 * `geometry.frame(in: .scrollView)`, which Compose has no named coordinate
 * space for, so the container hands over its coordinates and the child asks
 * them where it is. Fires on every placement, which during a scroll is every
 * frame: the same rate `onGeometryChange` fires at on iOS.
 */
private fun Modifier.trackedIn(
    container: LayoutCoordinates?,
    onFrame: (Rect) -> Unit,
): Modifier = this.onGloballyPositioned { coordinates ->
    val root = container ?: return@onGloballyPositioned
    if (!root.isAttached || !coordinates.isAttached) return@onGloballyPositioned
    val top = root.localPositionOf(coordinates, Offset.Zero).y
    onFrame(
        Rect(
            left = 0f,
            top = top,
            right = coordinates.size.width.toFloat(),
            bottom = top + coordinates.size.height,
        ),
    )
}

/**
 * Ribbon's floating chrome material (§12.1), as NoteComposer draws it: the
 * raised surface under a 0.72 tint of the unlit ground, with a hairline edge
 * that catches a little more light along the top. Android has no backdrop
 * material to make real glass out of — the reasoning is written out in full
 * over NoteComposer's copy, and in docs/deviations.md.
 *
 * It is written twice because Kotlin's `private` is file scope and the
 * material is one file over; neither file owns the other.
 *
 * Where glass never appears, on either platform: over Scripture, on the
 * fire, the shelf, embers, or as a screen background.
 */
@Composable
private fun Modifier.ribbonGlass(shape: Shape): Modifier = this
    .background(Palette.raised.copy(alpha = 0.55f), shape)
    .background(Palette.ground.copy(alpha = 0.72f), shape)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Palette.text.copy(alpha = 0.14f), Palette.rule),
        ),
        shape = shape,
    )

/**
 * "Tell you when Ruth leaves a note?" — §6.1's exact question, asked once.
 *
 * Two answers and no third. "Not now" is the shape that makes a permission
 * prompt feel like a negotiation, and it only exists in apps that intend to
 * ask again; this one does not, so it is not offered.
 *
 * On "Tell me" the system dialog follows, which is the only way a permission
 * can be requested on Android. On "Don't" it never appears — the person has
 * answered Ribbon's question and Android's would be the same question again,
 * in somebody else's words. Either way the app remembers that it asked.
 *
 * What happens after a refusal is nothing: no banner, no second ask, no dead
 * control. S19 grows one line admitting the OS is silencing it, and that is
 * the whole of the refusal path.
 */
@Composable
private fun NotificationAsk(name: String, onAnswered: () -> Unit) {
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // The answer itself changes nothing here. Granted, the next arrival
        // posts; refused, it does not — and either way the question is spent.
        onAnswered()
    }

    RibbonConfirmDialog(question = Copy.tellYouWhen(name), onDismiss = onAnswered) {
        ConfirmChoice(
            title = Copy.TELL_ME,
            onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
        ConfirmChoice(title = Copy.DONT_TELL_ME, onClick = onAnswered)
    }
}
