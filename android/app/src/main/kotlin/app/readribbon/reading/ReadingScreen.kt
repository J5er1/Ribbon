@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
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
import app.readribbon.design.grain
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.FireBecomesEmber
import app.readribbon.services.PresentPerson
import app.readribbon.services.VoiceRecorder
import app.readribbon.services.ensureRemoteChapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 * The closing drag: pulled this far past the top, the page settles closed.
 * Always duplicated by the Wave (§11) — no way out of the book is a gesture
 * only.
 */
private val CLOSING_PULL = 90.dp

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
// reduce motion it is a snap, which is a state change with no movement
// (§11). RibbonMotion's own helpers return `AnimationSpec`; a transition
// wants the finite kind, so the tokens are spelled out here as PresenceForm
// spells them out there.
private fun arriveSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
    if (reduceMotion) snap() else tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut)

private fun settleSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
    if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

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
    onClose: () -> Unit,
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
    val translation = model.me?.translation ?: TranslationID.bsb
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
    var composer by remember { mutableStateOf<ComposerState?>(null) }
    val recorder = remember(context) { VoiceRecorder(context) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

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

    LaunchedEffect(room.id, model.me?.id, model.readingQuietly) {
        val me = model.me
        if (!model.readingQuietly && me != null) {
            model.presence.join(room.id, me)
            val pos = openAt ?: model.myPosition(reading)
            model.presence.update(pos, 0.0, false)
        } else {
            model.presence.leave()
        }
    }

    DisposableEffect(room.id) {
        onDispose {
            scope.launch { model.presence.leave() }
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

    fun close() {
        if (closing) return
        closing = true
        if (!model.state.hasSeenMarginHint) {
            model.markMarginHintSeen()
        }
        recordFuel()
        onClose()
    }

    fun clearLift() {
        lifted = null
        liftedChapter = null
        composer = null
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
            for (note in model.notes(reading, address.chapter)) {
                if (note.verse.verse == address.verse) model.markFound(note)
            }
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
        // (§4.2). With no live presence roster this is unreachable; the
        // mechanics are here for when the socket is.
        followBackOffer.beganFollowing(model.myPosition(reading))
        model.followingPersonID = person.id
        person.position?.let { goToChapter(it.chapter) }
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
                    model.presence.update(position = address, scrollFraction = fraction, isIdle = false)
                }
            }
        }
        if (now - lastFuelRecord > FUEL_INTERVAL) {
            recordFuel(address)
        }
    }

    val closeNow by rememberUpdatedState(::close)

    // The closing drag. Swift watches the scroll view's content offset go
    // past -90 while a finger is down; Compose has no negative offset to
    // read, so the same gesture is read where it actually happens — the
    // drag deltas the list cannot use because it is already at the top.
    //
    // `NestedScrollSource.UserInput` is what makes this a finger and not a
    // bounce: a momentum overscroll arrives as `SideEffect` and is ignored,
    // exactly as `fingerDown` gates the Swift.
    val pull = remember { mutableFloatStateOf(0f) }
    val pullThreshold = with(density) { CLOSING_PULL.toPx() }
    val closingDrag = remember(pullThreshold, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) {
                    pull.floatValue = 0f
                    return Offset.Zero
                }
                if (available.y < 0f || listState.canScrollBackward) {
                    // Reading on, or reading back up: not a close.
                    pull.floatValue = 0f
                    return Offset.Zero
                }
                pull.floatValue += available.y
                if (pull.floatValue > pullThreshold) {
                    pull.floatValue = 0f
                    closeNow()
                }
                // Nothing is consumed: the list still rubber-bands, which is
                // the drag's own feedback.
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // The finger is up, so the pull is over whether or not it
                // closed anything. Swift reads an absolute content offset,
                // which cannot carry from one drag to the next; a running
                // tally can, and two unrelated half-pulls would close the
                // book between them. Put it down when the finger lifts.
                pull.floatValue = 0f
                return Velocity.Zero
            }
        }
    }

    // Predictive back: the room peels in behind the closing book. The pull
    // is a progress, not a commitment — releasing mid-gesture puts the book
    // back down where it was.
    var backPull by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = !closing) { progress ->
        try {
            progress.collect { event -> backPull = event.progress }
            close()
            backPull = 0f
        } catch (cancelled: CancellationException) {
            backPull = 0f
            throw cancelled
        }
    }

    // Opening: at your own place, or at the place you were sent to.
    LaunchedEffect(Unit) {
        val position = openAt ?: model.myPosition(reading)
        if (position.chapter > 1) {
            programmaticScrollUntil = Clock.System.now() + PROGRAMMATIC_SCROLL_GRACE
            listState.scrollToItem(itemIndexOfChapter(position.chapter))
        }
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
                .graphicsLayer {
                    val peel = if (reduceMotion) 0f else backPull
                    val shrink = 1f - 0.06f * peel
                    scaleX = shrink
                    scaleY = shrink
                    translationY = peel * 24.dp.toPx()
                    alpha = 1f - 0.28f * peel
                }
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
                            liftedVerses = if (liftedChapter == n) lifted?.verses else null,
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

            if (model.followingPersonID != null) {
                FollowThread(Modifier.align(Alignment.TopEnd))
            }

            // The way out, or the composer.
            BottomChrome(
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
                    clearLift()
                },
                onWrite = { address -> composer = ComposerState.Write(address) },
                onSpeak = { address -> composer = ComposerState.Speak(address) },
                onSaveWritten = { address, body ->
                    val note = editingNote
                    if (note != null) {
                        model.editWrittenNote(note, body)
                    } else {
                        model.leaveWrittenNote(body, address, reading)
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
    liftedVerses: IntRange?,
    measureInset: Dp,
    onRemoteChapter: (ScriptureChapter) -> Unit,
    onLayout: (ChapterLayout) -> Unit,
    onNoteSlot: (Dp) -> Unit,
    onNoteCardHeight: (Dp) -> Unit,
    onFrame: (Rect) -> Unit,
    onLongPressVerse: (Int) -> Unit,
    onDragToVerse: (Int) -> Unit,
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
        animationSpec = arriveSpec(reduceMotion),
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
                verseInks = verseInks(model, reading, n),
                liftedVerses = liftedVerses,
                openNote = openNoteVerse
                    ?.takeIf { it.chapter == n }
                    ?.let { OpenNote(verse = it.verse, height = noteCardHeight) },
                isFirstChapter = n == 1,
                showMarginHint = !model.state.hasSeenMarginHint && n == 1,
                onLayout = onLayout,
                onLongPressVerse = onLongPressVerse,
                onDragToVerse = onDragToVerse,
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
                enter = fadeIn(settleSpec(reduceMotion)),
                exit = fadeOut(settleSpec(reduceMotion)),
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
        Column(
            modifier = Modifier
                .readingMeasure(measureInset)
                .padding(start = 36.dp),
        ) {
            SmallCaps(runningHead, size = 14f, color = Palette.text.copy(alpha = 0.4f))
            Spacer(Modifier.height(320.dp))
        }
        LaunchedEffect(n, licensed) {
            val address = VerseAddress(bookID = reading.bookID, chapter = n, verse = 1)
            model.scripture.ensureRemoteChapter(context, address, licensed)?.let(onRemoteChapter)
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

private fun verseInks(model: AppModel, reading: Reading, chapter: Int): Map<Int, List<Ink>> {
    val result = mutableMapOf<Int, MutableList<Ink>>()
    for (highlight in model.highlights(reading, chapter)) {
        for (verse in highlight.range.verses) {
            result.getOrPut(verse) { mutableListOf() }.add(highlight.ink)
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
    val names = notes.mapNotNull { model.person(it.authorID)?.name }
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

        when (composer) {
            is ComposerState.Toolbar -> Unit

            is ComposerState.Write -> {
                val address = composer.address
                WriteComposer(
                    verse = address,
                    initialText = editingNote?.body ?: "",
                    identity = editingNote?.id?.toString() ?: address.formatted,
                    onSave = { body -> onSaveWritten(address, body) },
                    onCancel = onCancelWritten,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            is ComposerState.Speak -> {
                val address = composer.address
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

            null -> {
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
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 88.dp, minHeight = 52.dp)
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
        enter = fadeIn(arriveSpec(reduceMotion)),
        exit = fadeOut(arriveSpec(reduceMotion)),
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
                .clickable(onClick = onDismiss)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(34.dp))
        HairlineRule(Modifier.padding(start = 36.dp, end = 26.dp))

        if (room != null) {
            val card = model.card(reading, chapter)
            if (card.state != CardState.setDown) {
                ReflectionCardView(
                    card = card,
                    reading = reading,
                    room = room,
                    model = model,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                .clickable(onClick = onContinue),
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
