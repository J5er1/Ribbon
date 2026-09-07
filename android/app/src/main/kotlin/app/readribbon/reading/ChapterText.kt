package app.readribbon.reading

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.BaselineShift
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
import app.readribbon.design.LocalHaptics
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
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

/**
 * One chapter, set as a page.
 *
 * @param runningHead the running head, fully formed: "Mark 4", "Psalm 23".
 * @param verseInks inks covering each verse. One ink washes at 24%;
 *   overlapping inks multiply into a third colour — the correct emotional
 *   result (§4.5).
 * @param liftedVerses verses currently lifted by a long-press (drawn raised,
 *   with a soft shadow).
 * @param openNote an open note's carve-out: verse and the height to open
 *   beneath it.
 * @param onNoteSlot y offset (in this composable's coordinates) of the
 *   open-note carve, so the note card can sit in it.
 */
@Composable
fun ChapterText(
    chapter: ScriptureChapter,
    runningHead: String,
    theme: ReadingTheme,
    verseInks: Map<Int, List<Ink>>,
    liftedVerses: IntRange?,
    openNote: OpenNote?,
    isFirstChapter: Boolean,
    showMarginHint: Boolean,
    onLayout: (ChapterLayout) -> Unit,
    onLongPressVerse: (Int) -> Unit,
    onDragToVerse: (Int) -> Unit,
    onDragEnded: () -> Unit,
    onTapVerse: (Int) -> Unit,
    onNoteSlot: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val reduceMotion = rememberReduceMotion()

    // The faces, resolved once at this size. Literata is variable on its
    // optical-size axis, so the descriptor's smaller setting is a genuinely
    // different letterform rather than the body face scaled down.
    val bodyStyle = RibbonType.scripture(theme.fontSize)
    val descriptorStyle = RibbonType.scripture(theme.fontSize * 0.82f)

    // The carve animates open and closed. S04 asks for the line height to
    // open over 400 ms; the placeholder's height is an ordinary measured
    // value, so it can. Under reduce motion it is a state change, not a
    // movement (§11).
    var slotVerse by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(openNote?.verse) { openNote?.verse?.let { slotVerse = it } }
    val slotTarget = if (openNote != null) openNote.height + 12.dp else 0.dp
    val slotHeight by animateDpAsState(
        targetValue = slotTarget,
        animationSpec = if (reduceMotion) snap() else RibbonMotion.settle(),
        finishedListener = { if (it <= 0.dp) slotVerse = null },
        label = "note-slot",
    )

    // Rebuild the page only when something that *sets* it changed. The
    // carve's height is deliberately not in this key: the height lives in
    // the placeholder, which is passed alongside the string, so an
    // animating gap re-lays out the text without re-typesetting it.
    val page = remember(
        chapter, runningHead, theme, liftedVerses,
        isFirstChapter, showMarginHint, slotVerse, density,
        bodyStyle, descriptorStyle,
    ) {
        buildChapterPage(
            chapter = chapter,
            runningHead = runningHead,
            theme = theme,
            liftedVerses = liftedVerses,
            isFirstChapter = isFirstChapter,
            showMarginHint = showMarginHint,
            slotVerse = slotVerse,
            bodySpan = bodyStyle.toSpanStyle(),
            descriptorSpan = descriptorStyle.toSpanStyle(),
            density = density,
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
    // Whether this gesture already lifted a verse. A lift and a tap are the
    // same touch until the finger has been down long enough, and only one of
    // them may fire — the same exclusivity UIKit gets from a tap recogniser
    // failing under a long press.
    val gesture = remember { GestureState() }

    // The gesture handlers below outlive the composition that installed
    // them, so the callbacks are read through the composition rather than
    // captured once.
    val currentLongPress by rememberUpdatedState(onLongPressVerse)
    val currentDragTo by rememberUpdatedState(onDragToVerse)
    val currentDragEnded by rememberUpdatedState(onDragEnded)
    val currentTap by rememberUpdatedState(onTapVerse)

    fun verseAt(point: Offset): Int? {
        val result = layout ?: return null
        return page.verseAt(result.getOffsetForPosition(point))
    }

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

    Box(
        modifier = modifier.padding(
            // iOS: textContainerInset = (0, gutterWidth + 8, 0, trailingMargin).
            start = theme.gutterWidth + 8.dp,
            end = theme.trailingMargin,
        ),
    ) {
        BasicText(
            text = page.text,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    drawWashes(result, page, verseInks, density)
                }
                // The long-press threshold is the platform's own
                // (`ViewConfiguration.longPressTimeout`, 500 ms) rather than
                // iOS's 0.45 s — a system value a reader may already have
                // tuned, and not worth overriding.
                .pointerInput(page) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            gesture.lifted = true
                            verseAt(point)?.let { verse ->
                                // The haptic fires the moment the verse
                                // lifts, not on touch-down (§9.3).
                                haptics?.verseLifts()
                                currentLongPress(verse)
                            }
                        },
                        onDrag = { change, _ ->
                            // The drag that extends the selection is the
                            // same gesture that started it.
                            verseAt(change.position)?.let(currentDragTo)
                        },
                        onDragEnd = { currentDragEnded() },
                        onDragCancel = { currentDragEnded() },
                    )
                }
                .pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        gesture.lifted = false
                        val up = waitForUpOrCancellation()
                        if (up != null && !gesture.lifted) {
                            verseAt(up.position)?.let(currentTap)
                        }
                    }
                }
                // Verse-by-verse VoiceOver/TalkBack navigation is served by
                // the elements below, one per verse, so the text node itself
                // must not also be read as one long run.
                .clearAndSetSemantics { },
            style = bodyStyle.copy(color = Palette.text),
            inlineContent = inlineContent,
            // Never a horizontal scroll (S02 edge cases): the measure wraps,
            // and a hanging indent narrows the line rather than widening the
            // page.
            softWrap = true,
            onTextLayout = { layout = it },
        )

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
                        .clearAndSetSemantics { contentDescription = label },
                )
            }
        }
    }
}

/** Whether this touch has already lifted a verse. */
private class GestureState {
    var lifted: Boolean = false
}

// MARK: - The washes

/**
 * Highlight washes, drawn behind the glyphs: rounded, bleeding ~2 dp past
 * the glyph box, with slightly irregular edges so it reads as ink soaking
 * into paper rather than a filled rectangle.
 *
 * Overlaps arrive precomputed as multiplied colours, so two people marking
 * the same verse produces a third colour. The colours are never averaged —
 * the overlap is the point (§4.5).
 *
 * The multiply is arithmetic on the ink values, filled once, exactly as iOS
 * does it — deliberately not a `BlendMode.Multiply` pass per ink. A blend
 * mode multiplies against whatever is already on the canvas, and what is
 * already there is the unlit ground (0x0B0B0A); the overlap would come out
 * darker than a single wash and still carrying the first ink's hue, which is
 * the opposite of the third colour §4.5 asks for.
 */
private fun DrawScope.drawWashes(
    layout: TextLayoutResult,
    page: ChapterPage,
    verseInks: Map<Int, List<Ink>>,
    density: Density,
) {
    val bleedX = with(density) { WASH_BLEED_X.toPx() }
    val bleedY = with(density) { WASH_BLEED_Y.toPx() }
    val jitterUnit = with(density) { 0.2.dp.toPx() }
    val radiusUnit = with(density) { 1.dp.toPx() }

    val washes = verseInks.entries
        .mapNotNull { (verse, inks) ->
            if (inks.isEmpty()) return@mapNotNull null
            val range = page.verseRanges[verse] ?: return@mapNotNull null
            range to inks
        }
        .sortedBy { it.first.first }

    for ((range, inks) in washes) {
        // 24% for one ink; overlapping inks deepen, capped so a verse never
        // becomes a block of colour.
        val alpha = min(0.45f, Palette.HIGHLIGHT_WASH + 0.14f * (inks.size - 1))
        val rects = enclosingRects(layout, range)
        if (rects.isEmpty()) continue
        var multiplied = inks[0].color
        for (other in inks.drop(1)) multiplied = multiply(multiplied, other.color)
        val color: Color = multiplied.copy(alpha = alpha)
        for (rect in rects) {
            // Bleed past the glyph box; jitter by a stable hash so the
            // edge is irregular but doesn't shimmer on redraw.
            val h = (range.first * 31 + rect.top.toInt()) % 5 - 2
            val dy = h * jitterUnit
            val left = rect.left - bleedX
            val top = rect.top - bleedY + dy
            val right = rect.right + bleedX
            val bottom = rect.bottom + bleedY + dy
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(
                    x = (3f + abs(h)) * radiusUnit,
                    y = 4f * radiusUnit,
                ),
            )
        }
    }
}

/** Two inks, multiplied — the third colour an overlap makes (§4.5). */
private fun multiply(a: Color, b: Color): Color = Color(
    red = a.red * b.red,
    green = a.green * b.green,
    blue = a.blue * b.blue,
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
        val lineStart = max(start, layout.getLineStart(line))
        val lineEnd = min(end, layout.getLineEnd(line, visibleEnd = true))
        if (lineEnd <= lineStart) continue
        val a = layout.getHorizontalPosition(lineStart, usePrimaryDirection = true)
        val b = layout.getHorizontalPosition(lineEnd, usePrimaryDirection = true)
        result += WashRect(
            left = min(a, b),
            top = layout.getLineTop(line),
            right = max(a, b),
            bottom = layout.getLineBottom(line),
        )
    }
    return result
}

private data class WashRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

// MARK: - Setting the page

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
    val orderedVerses: List<Int>,
    /** Index of the carve among the string's placeholders, if one is open. */
    val noteSlotIndex: Int?,
) {

    /** The verse a character offset belongs to, or null in the chrome. */
    fun verseAt(offset: Int): Int? {
        if (text.isEmpty()) return null
        val at = offset.coerceIn(0, text.length - 1)
        return text.getStringAnnotations(TAG_VERSE, at, at)
            .firstOrNull()?.item?.toIntOrNull()
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
    liftedVerses: IntRange?,
    isFirstChapter: Boolean,
    showMarginHint: Boolean,
    slotVerse: Int?,
    bodySpan: SpanStyle,
    descriptorSpan: SpanStyle,
    density: Density,
): ChapterPage {
    val builder = AnnotatedString.Builder()
    val inline = mutableMapOf<String, InlineTextContent>()
    val verseStart = mutableMapOf<Int, Int>()
    val verseEnd = mutableMapOf<Int, Int>()
    val verseText = mutableMapOf<Int, StringBuilder>()
    val ordered = mutableListOf<Int>()
    var placeholderCount = 0
    var noteSlotIndex: Int? = null

    val ivory = Palette.text
    val em = theme.fontSize
    val lineHeight = (em * theme.lineHeightMultiple).sp

    // The lift: the verse is drawn raised, over a soft shadow. Compose's
    // BaselineShift is a fraction of the span's own size where UIKit's
    // baselineOffset is in points, so the 2 pt rise is expressed as one.
    val liftShadow = Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(0f, with(density) { 3.dp.toPx() }),
        blurRadius = with(density) { 8.dp.toPx() },
    )
    val liftShift = BaselineShift(2f / em)

    var paragraphOpen = false

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
            if (spoken) verseText.getOrPut(verse) { StringBuilder() }.append(text)
        }
    }

    // The one-time hint, above the first verse, dismissed by scrolling past
    // it and never returning (§6.1).
    if (showMarginHint && isFirstChapter) {
        beginParagraph(ParagraphStyle())
        builder.withStyle(
            RibbonType.smallCaps(13f).toSpanStyle()
                .copy(color = Palette.muted, letterSpacing = 0.9.sp),
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

        for (span in block.x) {
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
                beginParagraph(if (wrote) continuation else paragraph)
                opened = true
            }

            if (v != null && v != 1) {
                // The verse number: small caps superscript, ~45%.
                val numberSpan = RibbonType.smallCaps(em * 0.62f).toSpanStyle().copy(
                    color = ivory.copy(alpha = 0.45f),
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
                appendRun(numberSpan, v, "$v ", spoken = false)
                wrote = true
            }

            var attributes = if (block.s == BlockStyle.d) {
                descriptorSpan.copy(color = Palette.muted)
            } else {
                bodySpan.copy(
                    color = if (span.isRedLetter && theme.redLetter) {
                        Ink.crimson.color
                    } else {
                        ivory
                    },
                )
            }
            // A descriptor is a psalm title, not a verse: it is never lifted
            // and never hit-tested.
            val bodyVerse = runningVerse.takeIf { block.s != BlockStyle.d }
            if (liftedVerses != null && bodyVerse != null && bodyVerse in liftedVerses) {
                attributes = attributes.copy(shadow = liftShadow, baselineShift = liftShift)
            }
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
        orderedVerses = ordered.sorted(),
        noteSlotIndex = noteSlotIndex,
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
