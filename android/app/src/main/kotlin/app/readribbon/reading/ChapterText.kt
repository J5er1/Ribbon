package app.readribbon.reading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.awaitCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.readribbon.app.Copy
import app.readribbon.core.BlockStyle
import app.readribbon.core.Ink
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.VerseRange
import app.readribbon.design.LocalRoomColours
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.RoomColours
import app.readribbon.design.color
import app.readribbon.design.rememberReduceMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// One chapter of Scripture, set like a page (S02): Literata at the reader's
// size, verse numbers in small caps superscript at ~45% opacity, hanging
// indents for poetry, the running head set into the text block and
// scrolling with it.
//
// iOS builds this on TextKit 1 — a UITextView over a custom NSLayoutManager
// — because a plain text view will not do three things: draw highlight
// washes with bleed and multiply blending (§4.5), open the line height in
// place for a note (S04, never a modal and never a sheet), and hand back
// per-verse geometry for hit-testing and for the gutter.
//
// Compose reaches each of those a different way, and the ways are better
// suited than TextKit's in one place and merely equal in the others:
//
//   1. The chapter is one AnnotatedString drawn by one BasicText. The
//      TextLayoutResult captured from onTextLayout gives line boxes, so the
//      washes are drawn in a drawBehind by walking the lines a verse covers
//      — the direct analogue of NSLayoutManager's enumerateEnclosingRects.
//   2. The note's carve is an inline placeholder in the text itself, so the
//      chapter genuinely reflows around a real gap rather than around an
//      exclusion path laid over it. Because a placeholder's size is an
//      ordinary measured value, the gap can animate open — which closes
//      iOS deviation 6 (there the carve appears instantly, because TextKit
//      exclusion paths do not animate).
//   3. Verse hit-testing goes through getOffsetForPosition and back to a
//      verse through the string annotations carried on every glyph of the
//      verse — the analogue of iOS's `ribbonVerse` attribute.
//   4. Selecting is the platform's own (A62): the BasicText sits in a
//      SelectionContainer, so the long-press, the handles, the magnifier
//      and the snapping to words are the ones every other page on the phone
//      has. Every spoken word carries its place on the page as an
//      annotation, which is how what is selected is read back as verses and
//      words — the container's own offsets are not public.

/** What the reading surface needs to know to set a chapter. */
data class ReadingTheme(
    val fontSize: Float,
    val lineHeightMultiple: Float,
    val redLetter: Boolean,
    // iOS carries the reader's `dynamicTypeSize` here so a system type-size
    // change re-sets the page. There is nothing to carry on Android: every
    // size on this surface is an `sp`, and the system font scale reaches the
    // page through the ambient Density — which is itself a key of the typeset
    // page below, so the chapter re-sets when the reader changes type size.
    /**
     * The gutter, left, ~28 dp. Note marks only (§4.2). The gutter holds its
     * width at every type size (§08).
     */
    val gutterWidth: Dp = 28.dp,
    val trailingMargin: Dp = 26.dp,
    /**
     * Literata's weight on its own axis, with the system's Bold Text already
     * folded in (A68): 400 is the page as it has always been set. The page
     * takes no synthesized bold on top of it, so what Bold Text asks for is
     * drawn by the face and only by the face.
     */
    val weight: Int = 400,
    /** In prose, each numbered verse starts a line of its own (A68). */
    val versePerLine: Boolean = false,
    /** The verse numbers' ink: S02's quiet 45%, or clearer (A68). Nothing moves either way. */
    val verseNumberAlpha: Float = 0.45f,
)

/** Where each verse's marks and geometry ended up, for the overlay above. */
data class ChapterLayout(
    /**
     * Verse → the y-midpoint of its first line (marks pin to the first line
     * — S02 edge cases).
     */
    val verseFirstLineY: Map<Int, Dp> = emptyMap(),
    val height: Dp = 0.dp,
)

/** An open note's carve-out: the verse, and the height to open beneath it. */
data class OpenNote(val verse: Int, val height: Dp)

/** Verse number carried on every glyph of the verse, for hit-testing. */
private const val TAG_VERSE = "ribbonVerse"

/** The id of the inline placeholder that holds the open note's carve. */
private const val NOTE_SLOT = "ribbonNoteSlot"

/** Horizontal bleed past the glyph box, per §4.5. */
private val WASH_BLEED_X = 2.dp

/** Vertical bleed. Less than the horizontal: ink spreads along a line. */
private val WASH_BLEED_Y = 1.2.dp

/** How far the end of a line's wash overshoots, one way or the other. */
private val WASH_WOBBLE = 0.6.dp

/** The wash's outer corners. Generous: a highlight is a gesture, not a box. */
private val WASH_CORNER = 5.dp

/** How far the wet end of a stroke runs out over, while it is travelling. */
private val WASH_TIP = 10.dp

/**
 * How far the wash reaches from the baseline, as a fraction of the body size:
 * over the capitals and under the tails, and no further. Everything above and
 * below that is leading, which belongs to the page rather than to the mark.
 */
private const val WASH_ABOVE_BASELINE = 0.88f
private const val WASH_BELOW_BASELINE = 0.28f

/** Every spoken word carries its own place on the page: "pageStart:pageEnd". */
private const val TAG_WORD = "ribbonWord"

/**
 * How far from a verse number's glyphs a tap still counts as a tap on the
 * number (§13.2). The number is a small superscript, and a target only a
 * stylus can hit is broken (§11, deviation 12).
 */
private val NUMBER_REACH = 16.dp

/** The screen reader's two ends of the mark: a thumb's target, drawn as nothing. */
private val END_TARGET = 44.dp

/**
 * How strongly the selection tints the words it covers: the accent, which is
 * the app's furniture rather than anybody's ink, at a strength that cannot be
 * mistaken for one of the eight washes (24%).
 */
internal const val SELECTION_TINT = 0.22f

/**
 * One chapter's native selection (A62), as the reading screen drives it.
 *
 * The page selects the way every other page on the phone does — the
 * platform's long-press, its handles and magnifier, its word snapping — and
 * reports what is selected as verses and offsets. What the reading screen
 * needs to do the other way round — take the selection to the whole verse,
 * step an end by a word, let go of it — goes through here.
 *
 * Created once per chapter on screen, so only one chapter selects at a time:
 * a selection begun in another chapter lets go of this one.
 */
@Stable
class PageSelection {
    internal val state = SelectionState()

    /** Where a range sits on the page, once the page is set and laid out. */
    internal var toPage: ((VerseRange) -> TextRange?)? = null

    /** A selection asked for before the page could place it. */
    internal var pending: VerseRange? = null

    /** Whether anything is selected on this page right now. */
    val isSelecting: Boolean get() = state.selectedTexts.any { it.isNotEmpty() }

    /**
     * Selects [range] on this page — its verses and offsets, whatever chapter
     * and book it says. Held until the page is laid out if it is not yet.
     */
    fun select(range: VerseRange) {
        val at = toPage?.invoke(range)
        if (at == null) {
            pending = range
            return
        }
        pending = null
        state.select(at)
    }

    /** Lets go of whatever is selected on this page. */
    fun clear() {
        pending = null
        state.clear()
    }
}

/**
 * What is selected on a page: its two ends, as offsets into the verses' own
 * text — null at an end that is the verse's own edge, so a selection of whole
 * verses is stored as whole verses — and the word, when it is one word.
 */
@Immutable
data class PageRange(
    val startVerse: Int,
    val startChar: Int?,
    val endVerse: Int,
    val endChar: Int?,
    /** The one word selected, which the original line names (A60 §7.5). */
    val word: HeldWord? = null,
)

/**
 * The platform's floating text toolbar, never drawn (§13.2): no glass over a
 * verse, and the page's own verbs are on Ribbon's toolbar at the foot.
 * Holding the menu open until it is cancelled is what tells the selection the
 * menu is "shown"; nothing is put on screen.
 */
private object NoTextContextMenu : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        awaitCancellation()
    }
}

/** The same, for the older route a selection may take to its menu. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit
}

/**
 * One chapter, set as a page.
 *
 * @param runningHead the running head, fully formed: "Mark 4", "Psalm 23".
 * @param marks every highlight on this chapter, as a mark per verse. One ink
 *   washes at 24%;
 *   overlapping inks multiply into a third colour — the correct emotional
 *   result (§4.5).
 * @param selection this page's native selection (A62).
 * @param lifted what is selected on this page while it is live — the
 *   toolbar or the original is up — for the screen reader's two ends.
 * @param held what was selected when a composer took the focus: the native
 *   selection lets go when the keyboard comes up, so the words being written
 *   about are drawn here, still, until the composer closes.
 * @param justMarked the verses you have this moment highlighted yourself, so
 *   the wash is drawn travelling across them rather than appearing on them.
 *   Null for everything else, including a highlight arriving from somebody
 *   else's phone.
 * @param onMarkDrawn the stroke has finished travelling and [justMarked] can
 *   be let go of.
 * @param onSelected the selection changed, to a range — or to nothing.
 * @param onSelectVerse a verse's "leave something here", for somebody who
 *   cannot hold: the whole verse selected.
 * @param onTapVerseNumber a tap on (or near) a verse's number, which takes
 *   the whole verse (§13.2).
 * @param openNote an open note's carve-out: verse and the height to open
 *   beneath it.
 * @param onNoteSlot y offset (in this composable's coordinates) of the
 *   open-note carve, so the note card can sit in it.
 * @param onOriginal a verse's "the original words" action, for somebody who
 *   cannot hold and then reach for the toolbar: selects the verse and opens
 *   the original (A60). Null leaves the action off.
 */
@Composable
fun ChapterText(
    chapter: ScriptureChapter,
    runningHead: String,
    theme: ReadingTheme,
    marks: List<VerseMark>,
    selection: PageSelection,
    lifted: VerseRange?,
    held: VerseRange?,
    justMarked: VerseRange?,
    onMarkDrawn: () -> Unit,
    openNote: OpenNote?,
    isFirstChapter: Boolean,
    showMarginHint: Boolean,
    onLayout: (ChapterLayout) -> Unit,
    onSelected: (PageRange?) -> Unit,
    onTapVerse: (Int) -> Unit,
    onNoteSlot: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onSelectVerse: (Int) -> Unit = {},
    onTapVerseNumber: (Int) -> Unit = {},
    onOriginal: ((Int) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()

    // The faces, resolved once at this size. Literata is variable on its
    // optical-size axis, so the descriptor's smaller setting is a genuinely
    // different letterform rather than the body face scaled down.
    //
    // And on its weight axis, so the reader's weight is drawn too (A68) —
    // and only drawn. Bold Text asks the platform for 300 more on every
    // weight a style names, and with no heavier face to give it smears a
    // bold over the one it has; the theme's weight has Bold Text in it
    // already, so the page takes no synthesis at all. Its small caps — the
    // running head, the numbers — inherit that from the text's own style,
    // and under Bold Text are Alegreya's real Medium rather than a smeared
    // one. At 400, with Bold Text off, there was never anything to
    // synthesize, and the page is the page it was.
    val bodyStyle = RibbonType.scripture(theme.fontSize, theme.weight)
        .copy(fontSynthesis = FontSynthesis.None)
    val descriptorStyle = RibbonType.scripture(theme.fontSize * 0.82f, theme.weight)
        .copy(fontSynthesis = FontSynthesis.None)

    // The carve animates open and closed. S04 asks for the line height to
    // open over 400 ms; the placeholder's height is an ordinary measured
    // value, so it can. Under reduce motion it is a state change, not a
    // movement (§11).
    var slotVerse by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(openNote?.verse) { openNote?.verse?.let { slotVerse = it } }
    val slotTarget = if (openNote != null) openNote.height + 12.dp else 0.dp
    val slotHeight by animateDpAsState(
        targetValue = slotTarget,
        animationSpec = RibbonMotion.settle(reduceMotion),
        finishedListener = { if (it <= 0.dp) slotVerse = null },
        label = "note-slot",
    )

    // Rebuild the page only when something that *sets* it changed. The
    // carve's height is deliberately not in this key: the height lives in
    // the placeholder, which is passed alongside the string, so an
    // animating gap re-lays out the text without re-typesetting it.
    //
    // Nor is the selection (A62). The lift used to re-typeset the whole
    // chapter — a shadow and a 2 pt rise on every verse it covered — on
    // every verse a drag crossed. The native selection is painted by the
    // text itself and changes nothing about how the page is set.
    val room = LocalRoomColours.current
    val page = remember(
        chapter, runningHead, theme,
        isFirstChapter, showMarginHint, slotVerse, density,
        bodyStyle, descriptorStyle, room,
    ) {
        buildChapterPage(
            chapter = chapter,
            runningHead = runningHead,
            theme = theme,
            room = room,
            isFirstChapter = isFirstChapter,
            showMarginHint = showMarginHint,
            slotVerse = slotVerse,
            bodySpan = bodyStyle.toSpanStyle(),
            descriptorSpan = descriptorStyle.toSpanStyle(),
        )
    }

    // The spacer placeholders are fixed; only the carve breathes.
    val inlineContent = remember(page, slotHeight, density) {
        if (page.noteSlotIndex == null) {
            page.inlineContent
        } else {
            page.inlineContent + (NOTE_SLOT to InlineTextContent(
                Placeholder(
                    width = 1.sp,
                    height = with(density) { slotHeight.coerceAtLeast(0.5.dp).toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Top,
                ),
            ) { })
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // The gesture handlers below outlive the composition that installed
    // them, so the callbacks are read through the composition rather than
    // captured once.
    val currentTap by rememberUpdatedState(onTapVerse)
    val currentTapNumber by rememberUpdatedState(onTapVerseNumber)
    val currentSelectVerse by rememberUpdatedState(onSelectVerse)
    val currentSelected by rememberUpdatedState(onSelected)
    val currentOriginal by rememberUpdatedState(onOriginal)

    // Report geometry once per layout, not once per recomposition: the
    // enclosing screen re-evaluates on every scroll tick, and iOS coalesces
    // the same way through `reportLayoutSoon`.
    val reported = remember { mutableStateOf<ChapterLayout?>(null) }
    LaunchedEffect(layout, page) {
        val result = layout ?: return@LaunchedEffect
        val measured = page.chapterLayout(result, density)
        if (reported.value != measured) {
            reported.value = measured
            onLayout(measured)
        }
        val slot = page.noteSlotIndex
            ?.let { result.placeholderRects.getOrNull(it) }
        if (slot != null) {
            // iOS reports the top of the carve plus the 6 pt the card is
            // inset into it; the slot is 12 dp taller than the card so the
            // inset is even top and bottom. Because the gap animates here,
            // this fires on every frame of the open and the card rides it
            // down.
            onNoteSlot(with(density) { slot.top.toDp() } + 6.dp)
        }
    }

    // **The selection, read back (A62).** The platform owns the gesture, the
    // handles, the magnifier and the snapping to words; what this page owns
    // is what the selection *means*: a verse and a word at each end. The
    // offset a SelectionContainer holds is not public, so it is read back
    // from the words themselves — every spoken word carries its own place on
    // the page, and the first and last of them in what is selected are the
    // two ends, exact to the word, which is all a mark has ever stored.
    //
    // The last report is kept across a re-set page so a page that merely
    // re-lays out does not report the same selection twice, and so the
    // empty selection a chapter is born with is not news.
    val lastReport = remember { arrayOfNulls<PageRange>(1) }
    // The page can place a selection once it is laid out; asked before then,
    // the selection waits (`pending`) and is placed below.
    val laidOut = layout != null
    DisposableEffect(selection, page, laidOut) {
        if (laidOut) selection.toPage = page::pageSelection
        onDispose { selection.toPage = null }
    }
    LaunchedEffect(selection, page, laidOut) {
        if (!laidOut) return@LaunchedEffect
        selection.pending?.let(selection::select)
        snapshotFlow { selection.state.selectedTexts }.collect { texts ->
            val decoded = page.rangeOf(texts)
            if (decoded == null && texts.any { it.isNotEmpty() }) {
                // Something is selected that is not words of a verse: the
                // running head, the hint, a psalm's title, or a verse's
                // number on its own. Holding the number takes its verse;
                // anything else lets go, so there is never a selection the
                // toolbar has nothing to say about.
                //
                // Unless words were selected a moment ago and a handle is
                // passing over the blank and the number between two verses:
                // the selection under the finger is not let go of, nor jumped
                // to a whole verse. The words it had stand until it reaches
                // words again.
                val verse = page.versesIn(texts).singleOrNull()
                val number = verse != null && texts.joinToString("") { it.text }.trim() == verse.toString()
                if (lastReport[0] != null && !number) return@collect
                val whole = verse?.let { VerseRange("", 0, it, it) }
                if (whole != null) selection.select(whole) else selection.state.clear()
                return@collect
            }
            if (decoded != lastReport[0]) {
                lastReport[0] = decoded
                currentSelected(decoded)
            }
        }
    }

    // The washes, already eased to whatever they are part-way through
    // becoming. Computed here rather than in the draw because none of it
    // needs the layout: only the rectangles do.
    val washes = rememberArrivingWashes(
        spans = remember(marks, page) { spansOf(marks) { page.verseText[it]?.length ?: 0 } },
        justMarked = justMarked,
        lengthOf = { page.verseText[it]?.length ?: 0 },
        onMarkDrawn = onMarkDrawn,
        still = reduceMotion,
    )

    // The selection in the app's own colour: handles and tint in the accent,
    // the furniture the old knob was drawn in, kept out of the eight inks
    // and out of the reader's hands (§4.5).
    val accent = Palette.accent
    val selectionColours = remember(accent) {
        TextSelectionColors(
            handleColor = accent,
            backgroundColor = accent.copy(alpha = SELECTION_TINT),
        )
    }
    val heldTint = accent.copy(alpha = SELECTION_TINT)

    Box(
        modifier = modifier.padding(
            // iOS: textContainerInset = (0, gutterWidth + 8, 0, trailingMargin).
            start = theme.gutterWidth + 8.dp,
            end = theme.trailingMargin,
        ),
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides selectionColours,
            LocalTextContextMenuToolbarProvider provides NoTextContextMenu,
            LocalTextToolbar provides NoTextToolbar,
        ) {
            SelectionContainer(state = selection.state) {
                BasicText(
                    text = page.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val result = layout ?: return@drawBehind
                            drawWashes(result, page, washes, theme.fontSize, density)
                            // The words a composer is open about, still
                            // tinted as they were selected (§13.2).
                            if (held != null) drawHeld(result, page, held, heldTint)
                        }
                        // A tap: dismiss what is selected, if anything is;
                        // otherwise a verse's number takes the verse, and
                        // anywhere else opens what is there.
                        .pointerInput(page, selection) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Read before the selection's own tap clears
                                // it, so a tap that lets go of a selection is
                                // never also a tap on what is under it.
                                val hadSelection = selection.isSelecting
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                if (hadSelection) {
                                    selection.clear()
                                    return@awaitEachGesture
                                }
                                // The long-press is the selection's, and so is
                                // whatever it selected: the finger coming up
                                // afterwards is not a tap.
                                val pressed = up.uptimeMillis - down.uptimeMillis
                                if (pressed >= viewConfiguration.longPressTimeoutMillis ||
                                    selection.isSelecting
                                ) {
                                    return@awaitEachGesture
                                }
                                val result = layout ?: return@awaitEachGesture
                                val reach = NUMBER_REACH.toPx()
                                val number = page.numberNear(result, up.position, reach)
                                if (number != null) {
                                    currentTapNumber(number)
                                    return@awaitEachGesture
                                }
                                page.verseAt(result.getOffsetForPosition(up.position))
                                    ?.let(currentTap)
                            }
                        }
                        // Verse-by-verse VoiceOver/TalkBack navigation is
                        // served by the elements below, one per verse, so the
                        // text node itself must not also be read as one long
                        // run.
                        .clearAndSetSemantics { },
                    style = bodyStyle.copy(color = Palette.text),
                    inlineContent = inlineContent,
                    // Never a horizontal scroll (S02 edge cases): the measure
                    // wraps, and a hanging indent narrows the line rather than
                    // widening the page.
                    softWrap = true,
                    onTextLayout = { layout = it },
                )
            }
        }

        // **The two ends of the mark, for the screen reader (§11).**
        //
        // The platform's handles are popups a screen reader cannot reach, and
        // a handle you can only drag is a handle that does not exist for half
        // the people S06 was written for. So each end of a live selection is
        // an element of its own, drawn as nothing and taking no touch, with
        // the tap equivalents of dragging it: a word or a verse, either way.
        val lifting = layout
        if (lifted != null && lifting != null) {
            // The first line of the first verse and the last line of the last,
            // not the corners of the box the selection fits inside.
            val head = page.pageRanges(lifted.startVerse, lifted.startChar, null)
                .firstOrNull()?.let { enclosingRects(lifting, it) }?.firstOrNull()
            val tail = page.pageRanges(lifted.endVerse, null, lifted.endChar)
                .lastOrNull()?.let { enclosingRects(lifting, it) }?.lastOrNull()
            if (head != null && tail != null) {
                fun step(atStart: Boolean, forward: Boolean, byWord: Boolean) {
                    page.steppedEnd(lifted, atStart, forward, byWord)?.let(selection::select)
                }
                MarkEnd(
                    x = head.left,
                    y = (head.top + head.bottom) / 2f,
                    label = Copy.WHERE_THE_MARK_STARTS,
                    density = density,
                    onStep = { forward, byWord -> step(atStart = true, forward, byWord) },
                )
                MarkEnd(
                    x = tail.right,
                    y = (tail.top + tail.bottom) / 2f,
                    label = Copy.WHERE_THE_MARK_ENDS,
                    density = density,
                    onStep = { forward, byWord -> step(atStart = false, forward, byWord) },
                )
            }
        }

        // Verse-by-verse screen-reader navigation (§11): one element per
        // verse, so a swipe moves by verse — and the label obeys Law 2
        // ("Verse nine." then the words; never a position report).
        val result = layout
        if (result != null) {
            for (verse in page.orderedVerses) {
                val body = page.verseText[verse] ?: continue
                val bounds = page.bounds(verse, result) ?: continue
                val label = Copy.verseSpoken(verse, body.trim())
                Box(
                    Modifier
                        .offset {
                            IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                        }
                        .size(
                            width = with(density) { bounds.width.toDp() },
                            height = with(density) { bounds.height.toDp() },
                        )
                        // The two gestures the text carries, said out loud
                        // (§11 Motor). "Leave something here" selects the
                        // whole verse, the same selection the platform's own
                        // gesture makes, so everything after it — the
                        // toolbar, its verbs, the two ends above — is the
                        // same for everybody.
                        .clearAndSetSemantics {
                            contentDescription = label
                            onClick(label = Copy.OPEN_WHATS_HERE) {
                                currentTap(verse)
                                true
                            }
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(Copy.LEAVE_SOMETHING_HERE) {
                                    currentSelectVerse(verse)
                                    true
                                },
                                // The original words, a third way in beside
                                // the toolbar's verb and the line (A60).
                                currentOriginal?.let { open ->
                                    CustomAccessibilityAction(Copy.ORIGINAL_ACTION) {
                                        open(verse)
                                        true
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

/**
 * The edge of the word a character offset falls in.
 *
 * S06 asks for handles that snap "to verse boundaries by default and to word
 * boundaries when dragged slowly". The platform's handles snap to words, and
 * every end the page reports is a word edge — the start of a word or the end
 * of one — because the words themselves are what it reads back. The first and
 * last words of a verse are its edges, so a selection that reaches them is
 * the whole verse and is stored as one; and "the verse" on the toolbar is the
 * default S06 wants, said as a control rather than hidden behind a speed.
 */
private fun wordEdge(text: String, at: Int, atStart: Boolean): Int {
    if (text.isEmpty()) return 0
    val here = at.coerceIn(0, text.length)
    return if (atStart) {
        var i = here
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        i
    } else {
        var i = here
        while (i < text.length && !text[i].isWhitespace()) i++
        // Trailing space belongs to the gap, not to the word.
        while (i > 0 && text[i - 1].isWhitespace()) i--
        i
    }
}

/** The next or previous word edge, for the tap equivalents (§11). */
private fun wordStep(text: String, from: Int, forward: Boolean, atStart: Boolean): Int {
    if (text.isEmpty()) return 0
    var i = from.coerceIn(0, text.length)
    if (forward) {
        while (i < text.length && !text[i].isWhitespace()) i++
        while (i < text.length && text[i].isWhitespace()) i++
    } else {
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        // An end stepping back lands on the end of the word before, not on
        // the end of the word it was already at: from its start, the edge
        // forward is where it began.
        if (!atStart) while (i > 0 && text[i - 1].isWhitespace()) i--
    }
    return wordEdge(text, i, atStart)
}

/**
 * One end of a live selection, for the screen reader: a 44 dp element (§11,
 * deviation 12) centred on the end it names, drawn as nothing and taking no
 * touch — the platform's handle is what a finger holds. Its four actions are
 * the tap equivalents of dragging it.
 */
@Composable
private fun MarkEnd(
    x: Float,
    y: Float,
    label: String,
    density: Density,
    onStep: (forward: Boolean, byWord: Boolean) -> Unit,
) {
    val target = with(density) { END_TARGET.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - target / 2f).roundToInt(),
                    (y - target / 2f).roundToInt(),
                )
            }
            .size(END_TARGET)
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(Copy.A_WORD_FURTHER_ON) {
                        onStep(true, true)
                        true
                    },
                    CustomAccessibilityAction(Copy.A_WORD_BACK) {
                        onStep(false, true)
                        true
                    },
                    CustomAccessibilityAction(Copy.A_VERSE_FURTHER_ON) {
                        onStep(true, false)
                        true
                    },
                    CustomAccessibilityAction(Copy.A_VERSE_BACK) {
                        onStep(false, false)
                        true
                    },
                )
            },
    )
}

/**
 * The words a composer is open about, tinted as the selection tinted them
 * (§13.2). The composer takes the focus, and the platform lets go of a
 * selection when the focus goes; this is the selection, frozen, so the
 * reader can still see what they are writing about. A state, not a movement:
 * it is there on the frame the selection goes.
 */
private fun DrawScope.drawHeld(
    layout: TextLayoutResult,
    page: ChapterPage,
    held: VerseRange,
    tint: Color,
) {
    // One run from the first letter to the last, numbers and gaps between
    // included, exactly as the platform painted the selection it stands in
    // for — not verse by verse, which left a notch at every number.
    val selected = page.pageSelection(held) ?: return
    for (rect in enclosingRects(layout, selected.start until selected.end)) {
        drawRect(
            color = tint,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
        )
    }
}

// MARK: - The washes

/**
 * One person's mark on a verse, or on part of one.
 *
 * [from] and [to] are offsets into the verse's *own* text, half-open; null at
 * either end means the verse's own beginning or end. A whole-verse highlight
 * — every highlight the app could make before A41g — is both of them null,
 * and takes exactly the path it always did.
 */
@Immutable
data class VerseMark(
    val verse: Int,
    val from: Int?,
    val to: Int?,
    val ink: Ink,
)

/** A stretch of one verse that carries the same set of inks all the way. */
@Immutable
private data class SpanKey(val verse: Int, val from: Int, val to: Int)

/**
 * The marks on a chapter, cut into stretches that each carry one set of inks.
 *
 * Two people marking *different* phrases of one verse is the case this
 * exists for: with a wash per verse, either mark would have coloured the
 * whole of it, and the overlap §4.5 is about would have been claimed where
 * there is none. Every mark's two ends become a boundary, and the verse is
 * cut at all of them; each piece then carries exactly the inks that cover it,
 * so an overlap is drawn where the words actually overlap and nowhere else.
 */
private fun spansOf(marks: List<VerseMark>, lengthOf: (Int) -> Int): Map<SpanKey, List<Ink>> {
    if (marks.isEmpty()) return emptyMap()
    val out = LinkedHashMap<SpanKey, List<Ink>>()
    for ((verse, ofVerse) in marks.groupBy { it.verse }) {
        val length = lengthOf(verse)
        if (length <= 0) continue
        val resolved = ofVerse.map { mark ->
            val a = (mark.from ?: 0).coerceIn(0, length)
            val b = (mark.to ?: length).coerceIn(a, length)
            Triple(a, b, mark.ink)
        }.filter { it.second > it.first }
        if (resolved.isEmpty()) continue

        val cuts = sortedSetOf<Int>()
        for ((a, b, _) in resolved) { cuts.add(a); cuts.add(b) }
        val edges = cuts.toList()
        for (i in 0 until edges.size - 1) {
            val lo = edges[i]
            val hi = edges[i + 1]
            if (hi <= lo) continue
            val inks = resolved.filter { it.first <= lo && it.second >= hi }.map { it.third }
            if (inks.isEmpty()) continue
            out[SpanKey(verse, lo, hi)] = inks
        }
    }
    return out
}

/**
 * One verse's wash, as it is drawn this frame: the colour the inks on it
 * make, how far up it is, and — when you are the one who just made it — how
 * far along the words the stroke has got.
 */
@Immutable
internal data class Wash(
    val color: Color,
    val alpha: Float,
    /** How far along the words the pen has got. 1 when nothing is moving. */
    val drawn: Float = 1f,
    /**
     * What is already on this verse, in front of the pen.
     *
     * Only ever set while a stroke of yours is travelling across somebody
     * else's mark: ahead of the tip the verse still shows their ink, behind
     * it the two have mixed. Null when you are marking bare words.
     */
    val beneath: Wash? = null,
)

/**
 * The wash a set of inks settles at: 24% for one, deepening for each ink on
 * top of it, capped so a verse never becomes a block of colour (§4.5).
 *
 * **The inks are screened, not multiplied, and that is the correction.**
 *
 * Multiply is how two pigments combine *on white paper*: each one subtracts,
 * so the overlap is darker than either. Ribbon's page is not paper — it is
 * unlit ground at 0x0B0B0A, and a wash on it is a translucent *light* laid
 * over darkness. Multiplying two inks there produces a near-black pigment, so
 * the overlap came out **dimmer than either ink on its own**: crimson alone
 * sits at 3.2× the ground's luminance, teal at 4.0×, and the two together at
 * 2.7×. §4.5 says an overlap *deepens* and that "the overlap is the point";
 * what it actually did was punch a hole in the page where two people had both
 * marked a verse — the single most meaningful thing that can happen on this
 * surface, drawn as an absence. Raising the alpha, which the old ramp did,
 * made it worse, because it moved the result further toward that near-black.
 *
 * Screen is multiply's mirror for light, and it is symmetric, so the wash does
 * not depend on which ink the loop met first — the fact "these two people both
 * marked this verse" is not an ordered one. Crimson and teal make a warm
 * bronze that is neither of them and brighter than both, which is the third
 * colour §4.5 asks for. Nothing is averaged.
 *
 * Still one arithmetic fill rather than a `BlendMode` pass per ink, and for
 * the original reason: a blend mode composites against whatever is already on
 * the canvas, which here is the ground itself.
 *
 * The ramp: +5% per extra ink, capped at 36%. The cap is what keeps §4.5's
 * "never a block of colour" true now that the colours climb toward white
 * rather than falling toward black — eight inks screened together are very
 * nearly white, and at 36% Scripture still reads over it at 5.3:1, against
 * 8.7:1 for the two-person case this product is actually about.
 *
 * This diverges from iOS, deliberately and knowingly: see deviations A41b.
 *
 * `internal` rather than private because S06 ends "check every one of the 28
 * pairs against the ground before ship", and a check that has to be performed
 * by hand before every ship is a check that gets performed once. It is
 * `HighlightWashTest` now.
 */
internal fun washFor(inks: List<Ink>): Wash {
    var mixed = inks[0].color
    for (other in inks.drop(1)) mixed = screen(mixed, other.color)
    return Wash(mixed, min(WASH_CAP, Palette.HIGHLIGHT_WASH + WASH_STEP * (inks.size - 1)))
}

/** How much each ink past the first deepens the wash, and how far it can go. */
private const val WASH_STEP = 0.05f
private const val WASH_CAP = 0.36f

/**
 * Somebody else's highlight, arriving.
 *
 * This is the moment the product is for — the other person marks a verse and
 * it turns up under your eyes on the page you are already reading — and until
 * now it was the one change in the app that happened on a single frame. A 24%
 * wash simply *was there*, in the periphery, with nothing to say it had just
 * come; §9.1 opens "everything breathes rather than blinks" and this was the
 * blink. Taking one back was the same in reverse, and a second person marking
 * a verse you had already marked stepped the colour to its deeper multiply
 * with a cut.
 *
 * All three are the same animation: the page holds what it last settled on,
 * and every wash eases from there to where it is now. A new wash comes up
 * from nothing, one taken back goes down to nothing, and a deepening one
 * crosses from the old colour to the new. Nothing is keyed to a clock, so a
 * chapter you have just opened draws its highlights already there rather than
 * fading a page of them in at you.
 *
 * `arrive`, not `settle` — §9.1 files presence appearing under the first, and
 * a highlight is somebody being present at a verse.
 */
@Composable
private fun rememberArrivingWashes(
    spans: Map<SpanKey, List<Ink>>,
    justMarked: VerseRange?,
    lengthOf: (Int) -> Int,
    onMarkDrawn: () -> Unit,
    still: Boolean,
): Map<SpanKey, Wash> {
    val settled = remember(spans) {
        spans.mapValues { (_, inks) -> washFor(inks) }
    }

    // What the page is coming *from*. Seeded with the first set it is given,
    // so opening a chapter is not an arrival: those highlights were already
    // there before you turned to the page.
    var from by remember { mutableStateOf(settled) }
    val travel = remember { Animatable(1f) }

    LaunchedEffect(settled) {
        if (from == settled) return@LaunchedEffect
        travel.snapTo(0f)
        travel.animateTo(1f, RibbonMotion.arrive(still))
        from = settled
    }

    // Your own stroke, travelling. It runs on its own clock because it is a
    // different length from the arrival above — a mark being *made* takes the
    // time a hand takes, and a mark that has turned up takes the time
    // anything else takes to arrive.
    val stroke = remember { Animatable(1f) }
    val markDrawn by rememberUpdatedState(onMarkDrawn)

    // What the page showed before this mark went on, held for the length of
    // the stroke.
    //
    // Deliberately captured here rather than read from `from` at draw time.
    // `from` belongs to the arrival above and turns over the moment *that*
    // animation ends — 320 ms against this one's 400 — so a pen crossing
    // somebody else's mark would have lost their colour out from in front of
    // it for the last fifth of the stroke, which is the one moment it is
    // there to show.
    var under by remember { mutableStateOf<Map<SpanKey, Wash>>(emptyMap()) }
    LaunchedEffect(justMarked) {
        if (justMarked == null) return@LaunchedEffect
        under = from
        stroke.snapTo(0f)
        stroke.animateTo(1f, RibbonMotion.settle(still))
        under = emptyMap()
        markDrawn()
    }

    val t = travel.value
    val pen = stroke.value
    val striking = if (pen < 1f) justMarked else null
    if (t >= 1f && striking == null) return settled

    val drawn = LinkedHashMap<SpanKey, Wash>(settled.size + from.size)
    for ((span, now) in settled) {
        // A verse you are marking right now is at full colour from the first
        // frame and is revealed along its length instead: the ink is not
        // getting darker, the pen is moving.
        if (striking != null && span.isInside(striking, lengthOf)) {
            // **Your ink meeting theirs.**
            //
            // Marking a verse somebody else has already marked is the one
            // moment on this surface where the two of you are demonstrably
            // in the same place, and it was drawn as their highlight
            // *disappearing*: the stroke revealed the new combined colour
            // from the left, and ahead of the tip there was nothing at all,
            // because only one wash is drawn per verse and it had already
            // become the mixture.
            //
            // Their ink stays where it is and the pen mixes it as it passes.
            // Ahead of the tip, their colour; behind it, the third colour the
            // two inks make; and at the tip the one crosses into the other
            // over about ten dp, which is what happens when a wet stroke is
            // laid over a dry one. Nothing flashes, nothing overshoots, and
            // nothing is counted — it is just the colour arriving, and it is
            // the whole point of two people reading the same chapter.
            drawn[span] = now.copy(drawn = pen, beneath = under.covering(span))
            continue
        }
        // A span that is *new* because an overlapping mark cut the verse into
        // smaller pieces is not an arrival: the colour under those words was
        // already on the page, it has only been re-described. So the wash it
        // eases out of is whichever settled span used to cover it, not
        // nothing — otherwise marking half of somebody's highlight would fade
        // their whole verse out and three new pieces in.
        val was = from[span] ?: from.covering(span)
        drawn[span] = if (was == null) {
            now.copy(alpha = now.alpha * t)
        } else {
            Wash(lerp(was.color, now.color, t), was.alpha + (now.alpha - was.alpha) * t)
        }
    }
    // Taken back: down to nothing rather than gone between two frames. A span
    // that has merely been re-cut is still covered and does not fade.
    for ((span, was) in from) {
        if (span !in settled && settled.covering(span) == null) {
            drawn[span] = was.copy(alpha = was.alpha * (1f - t))
        }
    }
    return drawn
}

/** The settled wash whose words contain [span]'s, if one does. */
private fun Map<SpanKey, Wash>.covering(span: SpanKey): Wash? {
    val middle = (span.from + span.to) / 2
    for ((key, wash) in this) {
        if (key.verse == span.verse && key.from <= middle && key.to > middle) return wash
    }
    return null
}

/** Whether every word of this span lies inside a range somebody just marked. */
private fun SpanKey.isInside(range: VerseRange, lengthOf: (Int) -> Int): Boolean {
    if (verse < range.startVerse || verse > range.endVerse) return false
    val length = lengthOf(verse)
    val low = if (verse == range.startVerse) (range.startChar ?: 0) else 0
    val high = if (verse == range.endVerse) (range.endChar ?: length) else length
    return from >= low && to <= high
}

/**
 * Highlight washes, drawn behind the glyphs.
 *
 * **One mark, filled once.** Every wash used to be drawn a line at a time:
 * one translucent rounded rectangle per line the verse touched. Three things
 * came of that, and together they are why a highlight never looked like a
 * highlight.
 *
 * *A dark band at every line break.* The rectangles bleed past the glyph box
 * top and bottom, so consecutive lines overlapped by twice the bleed — and
 * translucent over translucent is darker. A verse running over three lines
 * drew two horizontal stripes through itself, at exactly the places the eye
 * travels across.
 *
 * *Lines that did not line up.* Each rectangle was nudged up or down by a
 * stable hash, to read as "ink soaking into paper". Moving the whole line
 * box is not what soaking looks like; it is what a layout bug looks like.
 * The rows staggered, and the dark bands moved with them.
 *
 * *A different shape per line.* The corner radius came from the same hash, so
 * one line of a passage was rounder than the next.
 *
 * All three go away by unioning the line boxes into a single path and filling
 * that once. The seams cannot darken because there is only one fill; the
 * outer corners round and the interior ones vanish; and the shape that comes
 * out is the shape of the words, stepping in and out at the ends of lines —
 * which is the irregularity that was being simulated, and it is free.
 *
 * What is left of the hand-made quality is horizontal: the right-hand edge of
 * each line wobbles by a fraction of a millimetre on a stable hash, the way
 * the end of a pen stroke does. Nothing vertical moves, ever.
 *
 * The colours arrive already made — see [rememberArrivingWashes]. What is
 * left here is the one part that needs the layout: which rectangles a verse
 * encloses.
 */
private fun DrawScope.drawWashes(
    layout: TextLayoutResult,
    page: ChapterPage,
    washes: Map<SpanKey, Wash>,
    bodySize: Float,
    density: Density,
) {
    val bleedX = with(density) { WASH_BLEED_X.toPx() }
    val bleedY = with(density) { WASH_BLEED_Y.toPx() }
    val wobbleUnit = with(density) { WASH_WOBBLE.toPx() }
    val radius = with(density) { WASH_CORNER.toPx() }

    // Scripture is set on generous leading, so a line's *box* is about half
    // again as tall as the letters standing in it. Washing the whole box made
    // a three-line highlight one unbroken slab of colour with the words
    // floating in the middle of it — closer to a selection than to a mark.
    // The wash is hung off the baseline instead, at the height of the letters
    // plus room for their tails, so it sits on the words the way a stroke
    // does and the leading stays open between one line and the next.
    val feather = with(density) { WASH_TIP.toPx() }
    val bodyPx = with(density) { bodySize.sp.toPx() }
    val aboveBaseline = bodyPx * WASH_ABOVE_BASELINE
    val belowBaseline = bodyPx * WASH_BELOW_BASELINE

    // A span is a stretch of one verse's own text, and the page is somewhere
    // else entirely — a verse of poetry is several runs with spacers between
    // them — so each one turns into however many page ranges it actually
    // occupies. A whole-verse mark comes back as the verse's own runs, which
    // is what this drew before there was anything else to draw.
    val ordered = washes.entries
        .mapNotNull { (span, wash) ->
            if (wash.alpha <= 0f) return@mapNotNull null
            val ranges = page.pageRanges(span.verse, span.from, span.to)
            if (ranges.isEmpty()) return@mapNotNull null
            ranges to wash
        }
        .sortedBy { it.first.first().first }

    for ((ranges, wash) in ordered) {
        val rects = ranges.flatMap { enclosingRects(layout, it) }
        if (rects.isEmpty()) continue

        // Each line's band, and the single shape they make together.
        val bands = ArrayList<WashRect>(rects.size)
        var union: Path? = null
        rects.forEachIndexed { index, rect ->
            // The pen lifting: a stable hash, so it does not shimmer on
            // redraw, and horizontal only.
            val wobble = ((ranges.first().first * 31 + index * 7) % 3 - 1) * wobbleUnit
            // Never outside the line's own box: a tall capital or a long
            // descender must not let one line's wash touch the next.
            val band = WashRect(
                left = rect.left - bleedX,
                top = max(rect.top, rect.baseline - aboveBaseline) - bleedY,
                right = rect.right + bleedX + wobble,
                bottom = min(rect.bottom, rect.baseline + belowBaseline) + bleedY,
                baseline = rect.baseline,
            )
            bands += band
            val piece = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = band.left,
                        top = band.top,
                        right = band.right,
                        bottom = band.bottom,
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }
            val current = union
            union = if (current == null) {
                piece
            } else {
                Path().apply { op(current, piece, PathOperation.Union) }
            }
        }
        val shape = union ?: continue
        val color = wash.color.copy(alpha = wash.alpha)

        if (wash.drawn >= 1f) {
            drawPath(shape, color)
            continue
        }

        // **The pen travelling.** A highlight you are making yourself is
        // revealed along the words in reading order — line by line, and left
        // to right within a line — rather than fading up where it lies. It is
        // the one act on this surface that is entirely yours, and the only
        // one the app can honestly show as a movement of a hand: an arriving
        // highlight gets the fade above, because nothing travelled across
        // *your* page when somebody else marked their own.
        //
        // The clips are one per line and disjoint, so the shape is never
        // filled over itself and a half-drawn stroke is exactly as dark as a
        // finished one. Measured in ink laid down rather than in lines, so a
        // verse of four words and a verse of four lines take the same time
        // and travel at visibly different speeds, which is what a pen does.
        // What the page already showed here: somebody else's mark, which the
        // pen is about to mix rather than replace. Transparent when the words
        // were bare.
        val ahead = wash.beneath
            ?.let { it.color.copy(alpha = it.alpha) }
            ?: color.copy(alpha = 0f)

        var left = bands.sumOf { (it.right - it.left).toDouble() }.toFloat() * wash.drawn
        for (band in bands) {
            val width = band.right - band.left
            val reach = (min(width, max(0f, left))).coerceAtLeast(0f)
            left -= reach

            // In front of the tip: their ink, exactly as it was. Drawn first
            // and clipped away from everything behind, so the two colours are
            // never composited over one another and the mixture is the
            // arithmetic one rather than one wash dimmed by another.
            if (reach < width && ahead.alpha > 0f) {
                clipRect(
                    left = band.left + reach,
                    top = band.top,
                    right = band.right,
                    bottom = band.bottom,
                ) {
                    drawPath(shape, ahead)
                }
            }
            if (reach <= 0f) continue

            clipRect(
                left = band.left,
                top = band.top,
                right = band.left + reach,
                bottom = band.bottom,
            ) {
                // Behind the tip, the ink is simply down.
                if (left > 0f || reach >= width) {
                    drawPath(shape, color)
                } else {
                    // The tip itself: the last few millimetres cross from what
                    // is already there into what the two inks make — or run
                    // out into nothing, on bare words. A hard vertical edge
                    // travelling across Scripture is a wipe transition, and it
                    // is the one part of this the eye reads as a screen doing
                    // something rather than as ink.
                    val tip = min(feather, reach)
                    val solid = (reach - tip) / reach
                    drawPath(
                        shape,
                        Brush.horizontalGradient(
                            solid to color,
                            1f to ahead,
                            startX = band.left,
                            endX = band.left + reach,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Two inks screened — the third colour an overlap makes (§4.5).
 *
 * `1 - (1-a)(1-b)`: multiply's mirror. Where multiply asks how much light two
 * pigments both let through, this asks how much two lights together add up
 * to, which is what two translucent washes on an unlit page are.
 */
private fun screen(a: Color, b: Color): Color = Color(
    red = 1f - (1f - a.red) * (1f - b.red),
    green = 1f - (1f - a.green) * (1f - b.green),
    blue = 1f - (1f - a.blue) * (1f - b.blue),
    alpha = 1f,
)

/**
 * The line boxes a character range encloses — the analogue of TextKit's
 * `enumerateEnclosingRects(forGlyphRange:)`. One rect per line the range
 * touches, clipped to the range at both ends so a wash starts and stops on
 * the verse rather than on the paragraph.
 */
private fun enclosingRects(
    layout: TextLayoutResult,
    range: IntRange,
): List<WashRect> {
    val length = layout.layoutInput.text.length
    if (length == 0) return emptyList()
    val start = range.first.coerceIn(0, length - 1)
    val end = (range.last + 1).coerceIn(start + 1, length)
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    val result = mutableListOf<WashRect>()
    for (line in firstLine..lastLine) {
        val visibleStart = layout.getLineStart(line)
        val visibleEnd = layout.getLineEnd(line, visibleEnd = true)
        val lineStart = max(start, visibleStart)
        val lineEnd = min(end, visibleEnd)
        if (lineEnd <= lineStart) continue

        // The left edge is the first glyph the verse owns on this line, which
        // is an offset question and always was. The right edge is not.
        //
        // `getHorizontalPosition` at a line's *own* end offset does not
        // answer with that line's right edge: at a soft wrap the offset
        // already belongs to the line below, so it comes back as the next
        // line's left margin, and on a hard break it lands on the break. So
        // every line a verse covered in full got a right edge somewhere out
        // near the left margin, and `max(a, b)` then collapsed the whole
        // rect to a hairline sitting in the indent.
        //
        // It went unseen because it only shows on a verse that *wraps*, and
        // a wrapping verse has to be highlighted to show anything at all —
        // which is a state the look book had no picture of until now. On
        // poetry, where the lines are short and indented, a highlight across
        // four lines drew one line and three slivers.
        //
        // When the verse runs past this line, the line's own right edge is
        // the answer; only when the verse *stops* part-way along does an
        // offset come into it.
        val left = layout.getHorizontalPosition(lineStart, usePrimaryDirection = true)
        val right = if (lineEnd >= visibleEnd) {
            layout.getLineRight(line)
        } else {
            layout.getHorizontalPosition(lineEnd, usePrimaryDirection = true)
        }
        if (right <= left) continue
        result += WashRect(
            left = min(left, right),
            top = layout.getLineTop(line),
            right = max(left, right),
            bottom = layout.getLineBottom(line),
            baseline = layout.getLineBaseline(line),
        )
    }
    return result
}

private data class WashRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val baseline: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

// MARK: - Setting the page

/**
 * One run of a verse's text, in both coordinate systems at once: where it
 * begins inside the verse, and where it begins on the page.
 */
private data class TextSegment(
    val textStart: Int,
    val pageStart: Int,
    val length: Int,
)


/**
 * The chapter, typeset: the string, the placeholders it reserves space for,
 * and where every verse ended up in it.
 */
private class ChapterPage(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    /** Verse → the union of its runs, for washes and for geometry. */
    val verseRanges: Map<Int, IntRange>,
    val verseText: Map<Int, String>,
    /** Verse → the runs it is made of, in both coordinate systems. */
    val verseSegments: Map<Int, List<TextSegment>>,
    val orderedVerses: List<Int>,
    /** Index of the carve among the string's placeholders, if one is open. */
    val noteSlotIndex: Int?,
    /** Verse → where its number's digits sit on the page (verse 1 has none). */
    val numberRanges: Map<Int, IntRange> = emptyMap(),
) {

    /**
     * Where a verse's words begin in its own text: past any blank it opens
     * with. A selection starting here starts at the verse's own edge.
     */
    fun lead(verse: Int): Int {
        val body = verseText[verse] ?: return 0
        var i = 0
        while (i < body.length && body[i].isWhitespace()) i++
        return i
    }

    /**
     * Where a verse's words end in its own text, before any blank it closes
     * with. A verse's own text very often ends in the space before the next
     * one, and that space is the verse's edge, not a word: a selection ending
     * on the last word is the whole verse (§13.1 — the trailing-space bug).
     */
    fun tail(verse: Int): Int = verseText[verse]?.trimEnd()?.length ?: 0

    /**
     * What a native selection says, as verses and offsets (A62).
     *
     * [selected] is the selection's own text, cut from the page with its
     * annotations still on it. The first and last word annotations in it
     * carry their own places on the page, so the two ends come back exact to
     * the word: a word cut part-way by a handle counts whole, the way a mark
     * always snapped outward to word edges. The verse numbers between, the
     * gaps and the carve carry no words and so add nothing.
     *
     * Null when no word is selected: nothing, or only the running head, the
     * hint, a psalm's title or a number.
     */
    fun rangeOf(selected: List<AnnotatedString>): PageRange? {
        var first = Int.MAX_VALUE
        var last = Int.MIN_VALUE
        var words = 0
        for (piece in selected) {
            for (word in piece.getStringAnnotations(TAG_WORD, 0, piece.length)) {
                val (a, b) = word.item.split(':').map(String::toInt)
                first = min(first, a)
                last = max(last, b)
                words += 1
            }
        }
        if (words == 0) return null
        val startVerse = verseAt(first) ?: return null
        val endVerse = verseAt(last - 1) ?: return null
        val from = textOffset(startVerse, first) ?: return null
        val to = textOffset(endVerse, last) ?: return null
        val startChar = from.takeIf { it > lead(startVerse) }
        val endChar = to.takeIf { it < tail(endVerse) }
        return PageRange(
            startVerse = startVerse,
            startChar = startChar,
            endVerse = endVerse,
            endChar = endChar,
            // One word: the word a long-press picked, which is the held word
            // the original line names (A60 §7.5).
            word = if (words == 1) HeldWord(startVerse, from) else null,
        )
    }

    /** The verses a selection touches through their annotations at all. */
    fun versesIn(selected: List<AnnotatedString>): Set<Int> = buildSet {
        for (piece in selected) {
            for (tag in piece.getStringAnnotations(TAG_VERSE, 0, piece.length)) {
                tag.item.toIntOrNull()?.let(::add)
            }
        }
    }

    /**
     * Where a range sits on the page, as the native selection holds it: from
     * the first letter it covers to the last. Null ends are the verse's own
     * edges — its first and last letters, not the blank either side.
     */
    fun pageSelection(range: VerseRange): TextRange? {
        val from = range.startChar ?: lead(range.startVerse)
        val to = range.endChar ?: tail(range.endVerse)
        val start = pageRanges(range.startVerse, from, null).firstOrNull()?.first ?: return null
        val end = pageRanges(range.endVerse, null, to).lastOrNull()?.last ?: return null
        if (end + 1 <= start) return null
        return TextRange(start, end + 1)
    }

    /**
     * The verse whose number is under, or within [reach] of, a point — the
     * nearest, when two are. The number is a small superscript, so the
     * target is the glyphs and a thumb's width around them (§13.2).
     */
    fun numberNear(layout: TextLayoutResult, point: Offset, reach: Float): Int? {
        val length = layout.layoutInput.text.length
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for ((verse, digits) in numberRanges) {
            if (digits.last >= length) continue
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (i in digits) {
                val box = layout.getBoundingBox(i)
                left = min(left, box.left)
                top = min(top, box.top)
                right = max(right, box.right)
                bottom = max(bottom, box.bottom)
            }
            val dx = max(0f, max(left - point.x, point.x - right))
            val dy = max(0f, max(top - point.y, point.y - bottom))
            if (dx > reach || dy > reach) continue
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = verse
            }
        }
        return best
    }

    /**
     * One end of a range moved a word or a verse, either way — the tap
     * equivalents of dragging a handle (§11). A word step that reaches a
     * verse's first or last word gives the whole verse at that end. Null when
     * the end cannot go that way: off the chapter, or past the other end.
     */
    fun steppedEnd(range: VerseRange, atStart: Boolean, forward: Boolean, byWord: Boolean): VerseRange? {
        val verse = if (atStart) range.startVerse else range.endVerse
        val char = if (atStart) range.startChar else range.endChar
        val body = verseText[verse] ?: return null
        val toVerse: Int
        val toChar: Int?
        if (byWord) {
            val at = char ?: if (atStart) lead(verse) else tail(verse)
            val next = wordStep(body, at, forward, atStart)
            toVerse = verse
            toChar = if (atStart) next.takeIf { it > lead(verse) } else next.takeIf { it < tail(verse) }
        } else {
            toVerse = if (forward) verse + 1 else verse - 1
            if (!verseText.containsKey(toVerse)) return null
            toChar = null
        }
        // Never past the other end: the two ends are a start and an end, and
        // one stepping over the other would quietly swap which is which.
        val start = if (atStart) toVerse to (toChar ?: lead(toVerse)) else
            range.startVerse to (range.startChar ?: lead(range.startVerse))
        val end = if (atStart) range.endVerse to (range.endChar ?: tail(range.endVerse)) else
            toVerse to (toChar ?: tail(toVerse))
        if (start.first > end.first || (start.first == end.first && start.second >= end.second)) return null
        val moved = if (atStart) {
            range.copy(startVerse = toVerse, startChar = toChar)
        } else {
            range.copy(endVerse = toVerse, endChar = toChar)
        }
        return moved.takeIf { it != range }
    }

    /** The verse a character offset belongs to, or null in the chrome. */
    fun verseAt(offset: Int): Int? {
        if (text.isEmpty()) return null
        val at = offset.coerceIn(0, text.length - 1)
        return text.getStringAnnotations(TAG_VERSE, at, at)
            .firstOrNull()?.item?.toIntOrNull()
    }

    /**
     * Where a stretch of one verse's own text sits on the page.
     *
     * [from] and [to] are offsets into `verseText[verse]`, half-open, and
     * null means "from the beginning" and "to the end". The answer is a list
     * rather than a range because a verse can be several runs — every line of
     * a psalm is one — and a phrase can span the break between them.
     */
    fun pageRanges(verse: Int, from: Int?, to: Int?): List<IntRange> {
        val body = verseText[verse] ?: return emptyList()
        val runs = verseSegments[verse] ?: return emptyList()
        val a = (from ?: 0).coerceIn(0, body.length)
        val b = (to ?: body.length).coerceIn(a, body.length)
        if (b <= a) return emptyList()
        val out = mutableListOf<IntRange>()
        for (run in runs) {
            val lo = maxOf(a, run.textStart)
            val hi = minOf(b, run.textStart + run.length)
            if (hi <= lo) continue
            val pageLo = run.pageStart + (lo - run.textStart)
            val pageHi = run.pageStart + (hi - run.textStart)
            out += pageLo until pageHi
        }
        return out
    }

    /** The offset into a verse's own text at a page offset, or null. */
    fun textOffset(verse: Int, pageOffset: Int): Int? {
        val runs = verseSegments[verse] ?: return null
        for (run in runs) {
            if (pageOffset >= run.pageStart && pageOffset <= run.pageStart + run.length) {
                return run.textStart + (pageOffset - run.pageStart)
            }
        }
        return null
    }

    /** The verse's whole box, for the screen reader. */
    fun bounds(verse: Int, layout: TextLayoutResult): WashRect? {
        val rects = enclosingRects(layout, verseRanges[verse] ?: return null)
        if (rects.isEmpty()) return null
        return WashRect(
            left = rects.minOf { it.left },
            top = rects.minOf { it.top },
            right = rects.maxOf { it.right },
            bottom = rects.maxOf { it.bottom },
            baseline = rects.first().baseline,
        )
    }

    fun chapterLayout(layout: TextLayoutResult, density: Density): ChapterLayout {
        val firstLineY = buildMap {
            for (verse in orderedVerses) {
                val range = verseRanges[verse] ?: continue
                val line = layout.getLineForOffset(
                    range.first.coerceIn(0, max(0, text.length - 1)),
                )
                val mid = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                put(verse, with(density) { mid.toDp() })
            }
        }
        return ChapterLayout(
            verseFirstLineY = firstLineY,
            height = with(density) { layout.size.height.toDp() },
        )
    }
}

/**
 * Sets one chapter.
 *
 * Compose has no paragraph spacing — a ParagraphStyle carries indents and a
 * line height and nothing else — so iOS's `paragraphSpacingBefore` and
 * `paragraphSpacing` are set here as short spacer paragraphs holding an
 * inline placeholder of the exact height. The result on the page is the
 * same measured space; only the mechanism differs.
 */
private fun buildChapterPage(
    chapter: ScriptureChapter,
    runningHead: String,
    theme: ReadingTheme,
    // The room, passed rather than read: this typesets a page, it does not
    // compose one, and the room's ink follows the wallpaper now.
    room: RoomColours,
    isFirstChapter: Boolean,
    showMarginHint: Boolean,
    slotVerse: Int?,
    bodySpan: SpanStyle,
    descriptorSpan: SpanStyle,
): ChapterPage {
    val builder = AnnotatedString.Builder()
    val inline = mutableMapOf<String, InlineTextContent>()
    val verseStart = mutableMapOf<Int, Int>()
    val verseEnd = mutableMapOf<Int, Int>()
    val verseText = mutableMapOf<Int, StringBuilder>()
    val segments = mutableMapOf<Int, MutableList<TextSegment>>()
    val ordered = mutableListOf<Int>()
    val numbers = mutableMapOf<Int, IntRange>()
    var placeholderCount = 0
    var noteSlotIndex: Int? = null

    val ivory = room.text
    val em = theme.fontSize
    val lineHeight = (em * theme.lineHeightMultiple).sp

    var paragraphOpen = false

    /** Where the open paragraph began, so an empty one is never closed for another. */
    var paragraphFrom = 0

    fun endParagraph() {
        if (paragraphOpen) {
            builder.pop()
            paragraphOpen = false
        }
    }

    fun beginParagraph(style: ParagraphStyle) {
        endParagraph()
        builder.pushStyle(style)
        paragraphOpen = true
        paragraphFrom = builder.length
    }

    /** A measured band of empty page — iOS's paragraph spacing. */
    fun spacer(height: Float) {
        endParagraph()
        val id = "gap-${placeholderCount}"
        builder.pushStyle(ParagraphStyle(lineHeight = height.sp))
        builder.withStyle(SpanStyle(fontSize = 1.sp)) {
            appendInlineContent(id, "\u200B")
        }
        builder.pop()
        inline[id] = InlineTextContent(
            Placeholder(
                width = 1.sp,
                height = height.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Top,
            ),
        ) { }
        placeholderCount += 1
    }

    /**
     * One run, carrying its verse the way iOS carries `ribbonVerse` on every
     * glyph — so a touch anywhere in the verse, the number included, finds
     * it. [spoken] is false for the number itself: the screen-reader label
     * already opens with "Verse nine", and iOS reads the digits a second
     * time only because it gathers its label from the same attribute.
     */
    fun appendRun(span: SpanStyle, verse: Int?, text: String, spoken: Boolean = true) {
        val start = builder.length
        builder.withStyle(span) { append(text) }
        if (verse != null) {
            builder.addStringAnnotation(TAG_VERSE, verse.toString(), start, builder.length)
            if (!verseStart.containsKey(verse)) {
                verseStart[verse] = start
                ordered += verse
            }
            verseEnd[verse] = builder.length
            if (spoken) {
                // A verse's own text and its place on the page are two
                // different coordinate systems, and they do not run in step:
                // a verse of poetry is several runs with a paragraph spacer
                // appended between them, so the page moves on while the
                // verse's text does not. Every spoken run records both ends
                // of the correspondence, which is what lets a mark on a
                // *phrase* be found again on the page (A41g).
                val body = verseText.getOrPut(verse) { StringBuilder() }
                segments.getOrPut(verse) { mutableListOf() }.add(
                    TextSegment(
                        textStart = body.length,
                        pageStart = start,
                        length = text.length,
                    ),
                )
                body.append(text)
                // And every word in it carries its own place on the page, so
                // a native selection can be read back to the word (A62).
                var i = 0
                while (i < text.length) {
                    if (text[i].isWhitespace()) {
                        i++
                        continue
                    }
                    var j = i
                    while (j < text.length && !text[j].isWhitespace()) j++
                    builder.addStringAnnotation(TAG_WORD, "${start + i}:${start + j}", start + i, start + j)
                    i = j
                }
            }
        }
    }

    // The one-time hint, above the first verse, dismissed by scrolling past
    // it and never returning (§6.1).
    if (showMarginHint && isFirstChapter) {
        beginParagraph(ParagraphStyle())
        builder.withStyle(
            RibbonType.smallCaps(13f).toSpanStyle()
                .copy(color = room.muted, letterSpacing = 0.9.sp),
        ) { append(Copy.FIRST_RUN_HINT) }
        spacer(13f * 1.4f)
    }

    // The running head — book and chapter in small caps at ~40% opacity, set
    // into the text block. Not a bar.
    beginParagraph(ParagraphStyle())
    builder.withStyle(
        RibbonType.smallCaps(14f).toSpanStyle()
            .copy(color = ivory.copy(alpha = 0.4f), letterSpacing = 1.4.sp),
    ) { append(runningHead) }
    spacer(em * 1.6f)

    /** The carve, in its own band, where the text has to flow around it. */
    fun openNoteSlot() {
        endParagraph()
        builder.pushStyle(ParagraphStyle())
        builder.withStyle(SpanStyle(fontSize = 1.sp)) {
            appendInlineContent(NOTE_SLOT, "\u200B")
        }
        builder.pop()
        noteSlotIndex = placeholderCount
        placeholderCount += 1
    }

    var isFirstContentBlock = true
    var afterBreak = false
    var runningVerse: Int? = null
    // True once the open note's verse has been set and the carve is owed to
    // the first thing that follows it.
    var slotPending = false

    for (block in chapter.blocks) {
        if (block.s == BlockStyle.b) {
            afterBreak = true
            continue
        }
        // iOS only spends the stanza break on a continuation paragraph; a
        // break before a poetic line is absorbed. Kept as it is there.
        if (block.s == BlockStyle.m && afterBreak) spacer(em * 0.75f)
        afterBreak = false

        val paragraph = paragraphStyle(block.s, isFirstContentBlock, em, lineHeight)
        val continuation = paragraph.copy(
            textIndent = paragraph.textIndent?.let { TextIndent(it.restLine, it.restLine) },
        )
        var opened = false
        var wrote = false
        // A new line for every verse (A68): the spans the core says a verse
        // starts a line before — in prose, and only there.
        val lineStarts = if (theme.versePerLine) block.verseLineStarts() else emptyList()

        /**
         * A paragraph of this block, opened at span [from], keeping its
         * leading wherever it meets a verse's line (A68). Compose trims the
         * leading off a paragraph's first line and its last, which is right
         * at a block's edges, where the page's own spacing takes over, and
         * wrong between two verses of one block: they would sit closer
         * together than the lines of either. Kept on both sides, the two
         * lines are one line's height apart, as the lines inside a verse
         * are. Without verse lines nothing is kept, and the style is the
         * one it always was.
         */
        fun leading(style: ParagraphStyle, from: Int, opensAVerseLine: Boolean): ParagraphStyle {
            val trimTop = !opensAVerseLine
            val trimBottom = lineStarts.none { it > from }
            if (trimTop && trimBottom) return style
            val trim = when {
                trimTop -> LineHeightStyle.Trim.FirstLineTop
                trimBottom -> LineHeightStyle.Trim.LastLineBottom
                else -> LineHeightStyle.Trim.None
            }
            return style.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, trim))
        }

        for ((index, span) in block.x.withIndex()) {
            val v = span.v
            if (v != null) runningVerse = v

            // The carve goes beneath the last line of the open note's verse:
            // the moment the text moves off that verse, the page opens.
            if (slotPending && runningVerse != slotVerse) {
                endParagraph()
                openNoteSlot()
                slotPending = false
                opened = false
            }
            if (!opened) {
                beginParagraph(leading(if (wrote) continuation else paragraph, index, opensAVerseLine = false))
                opened = true
            } else if (index in lineStarts && builder.length > paragraphFrom) {
                // The verse's line is a paragraph of its own, on the
                // continuation's indent, as the line after the carve is.
                // Compose sets paragraphs apart with no character between
                // them, so the break is outside every verse, as a block's own
                // is: no verse's text, no word's place on the page and no
                // number's offset moves. Never a second start on top of one
                // just made — after the carve, the paragraph it opened is the
                // verse's line already.
                beginParagraph(leading(continuation, index, opensAVerseLine = true))
            }

            if (v != null && v != 1) {
                // The verse number: small caps superscript, ~45% — or the
                // reader's clearer ink (A68), which moves nothing.
                val numberSpan = RibbonType.smallCaps(em * 0.62f).toSpanStyle().copy(
                    color = ivory.copy(alpha = theme.verseNumberAlpha),
                    // iOS lifts it by 0.3 em of the body size; as a fraction
                    // of the number's own 0.62 em size that is 0.484.
                    baselineShift = BaselineShift(0.484f),
                    // RibbonType.smallCaps carries the SmallCaps component's
                    // 7.5% tracking. iOS sets the number straight from
                    // `uiSmallCaps` with no kerning at all — a running head
                    // wants the tracking, a superscript numeral does not, and
                    // here it would also open the thin space below into a
                    // word space.
                    letterSpacing = 0.sp,
                )
                // A thin space after the number, never a word space.
                val digits = builder.length
                appendRun(numberSpan, v, "$v ", spoken = false)
                numbers[v] = digits until digits + v.toString().length
                wrote = true
            }

            val attributes = if (block.s == BlockStyle.d) {
                descriptorSpan.copy(color = room.muted)
            } else {
                bodySpan.copy(
                    color = if (span.isRedLetter && theme.redLetter) {
                        Ink.crimson.color
                    } else {
                        ivory
                    },
                )
            }
            // A descriptor is a psalm title, not a verse: it is never
            // selected as one and never hit-tested.
            val bodyVerse = runningVerse.takeIf { block.s != BlockStyle.d }
            appendRun(attributes, bodyVerse, span.t)
            wrote = true

            if (slotVerse != null && runningVerse == slotVerse) slotPending = true
        }

        if (wrote) {
            isFirstContentBlock = false
            // The descriptor's space sits after it, not before.
            if (block.s == BlockStyle.d) spacer(em * 0.5f)
        } else if (opened) {
            endParagraph()
        }
    }

    // The chapter ended on the open note's verse: the carve is the last
    // thing on the page.
    if (slotPending) {
        endParagraph()
        openNoteSlot()
    }
    endParagraph()

    val ranges = buildMap {
        for (verse in ordered) {
            val start = verseStart[verse] ?: continue
            val end = verseEnd[verse] ?: continue
            if (end > start) put(verse, start until end)
        }
    }

    return ChapterPage(
        text = builder.toAnnotatedString(),
        inlineContent = inline,
        verseRanges = ranges,
        verseText = verseText.mapValues { it.value.toString() },
        verseSegments = segments.mapValues { it.value.toList() },
        orderedVerses = ordered.sorted(),
        noteSlotIndex = noteSlotIndex,
        numberRanges = numbers,
    )
}

/**
 * How a block sits on the page.
 *
 * A printed page: prose takes a first-line indent except where it opens the
 * chapter, poetry takes a hanging indent — the second and later lines of a
 * poetic line sit deeper than the first, so a long line wraps inward and
 * never sideways (S02 edge cases).
 */
private fun paragraphStyle(
    style: BlockStyle,
    isFirstBlock: Boolean,
    em: Float,
    lineHeight: TextUnit,
): ParagraphStyle = when (style) {
    BlockStyle.p -> ParagraphStyle(
        lineHeight = lineHeight,
        textIndent = TextIndent(
            firstLine = if (isFirstBlock) 0.sp else (em * 0.95f).sp,
            restLine = 0.sp,
        ),
    )

    BlockStyle.m -> ParagraphStyle(lineHeight = lineHeight)

    BlockStyle.q1 -> ParagraphStyle(
        lineHeight = lineHeight,
        textIndent = TextIndent(firstLine = (em * 0.6f).sp, restLine = (em * 1.6f).sp),
    )

    BlockStyle.q2 -> ParagraphStyle(
        lineHeight = lineHeight,
        textIndent = TextIndent(firstLine = (em * 1.5f).sp, restLine = (em * 2.5f).sp),
    )

    BlockStyle.d -> ParagraphStyle(lineHeight = lineHeight)

    BlockStyle.b -> ParagraphStyle(lineHeight = lineHeight)
}
