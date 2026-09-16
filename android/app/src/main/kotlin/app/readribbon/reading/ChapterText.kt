package app.readribbon.reading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.Shadow
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

/**
 * One chapter, set as a page.
 *
 * @param runningHead the running head, fully formed: "Mark 4", "Psalm 23".
 * @param verseInks inks covering each verse. One ink washes at 24%;
 *   overlapping inks multiply into a third colour — the correct emotional
 *   result (§4.5).
 * @param liftedVerses verses currently lifted by a long-press (drawn raised,
 *   with a soft shadow).
 * @param justMarked the verses you have this moment highlighted yourself, so
 *   the wash is drawn travelling across them rather than appearing on them.
 *   Null for everything else, including a highlight arriving from somebody
 *   else's phone.
 * @param onMarkDrawn the stroke has finished travelling and [justMarked] can
 *   be let go of.
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
    justMarked: IntRange?,
    onMarkDrawn: () -> Unit,
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
        animationSpec = RibbonMotion.settle(reduceMotion),
        finishedListener = { if (it <= 0.dp) slotVerse = null },
        label = "note-slot",
    )

    // Rebuild the page only when something that *sets* it changed. The
    // carve's height is deliberately not in this key: the height lives in
    // the placeholder, which is passed alongside the string, so an
    // animating gap re-lays out the text without re-typesetting it.
    val room = LocalRoomColours.current
    val page = remember(
        chapter, runningHead, theme, liftedVerses,
        isFirstChapter, showMarginHint, slotVerse, density,
        bodyStyle, descriptorStyle, room,
    ) {
        buildChapterPage(
            chapter = chapter,
            runningHead = runningHead,
            theme = theme,
            room = room,
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

    // The washes, already eased to whatever they are part-way through
    // becoming. Computed here rather than in the draw because none of it
    // needs the layout: only the rectangles do.
    val washes = rememberArrivingWashes(verseInks, justMarked, onMarkDrawn, reduceMotion)

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
                    drawWashes(result, page, washes, theme.fontSize, density)
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

        // **The two handles S06 asks for.**
        //
        // "Extending — drag handles at both ends of the selection, snapping to
        // verse boundaries." They did not exist. The only way to select more
        // than one verse was to keep the finger down after the long press and
        // drag; once it lifted, the selection was final. Overshoot by a verse
        // — which is easy, because the thing under your thumb is the thing you
        // cannot see — and the only way back was to mark it wrongly, tap it,
        // and remove it. On the app's central act.
        //
        // They snap to verse boundaries and *only* to verse boundaries. S06's
        // second clause, word boundaries on a slow drag, is not here and is
        // not an oversight: `VerseRange` holds a start verse and an end verse,
        // so a sub-verse highlight has nowhere to be stored. It is a change to
        // the shared model on both platforms and the backend, not an Android
        // drawing question. Written down in A41e rather than half-built.
        val lifting = layout
        if (liftedVerses != null && lifting != null) {
            // The first line of the first verse and the last line of the last,
            // not the corners of the box the selection fits inside. A verse
            // that wraps is wider than its own last line, so a bounding box
            // put the tail handle out at the end of the widest line — which,
            // on a selection ending mid-paragraph, is somewhere in the middle
            // of the *next* verse.
            val head = page.verseRanges[liftedVerses.first]
                ?.let { enclosingRects(lifting, it) }?.firstOrNull()
            val tail = page.verseRanges[liftedVerses.last]
                ?.let { enclosingRects(lifting, it) }?.lastOrNull()
            if (head != null && tail != null) {
                SelectionHandle(
                    x = head.left,
                    y = head.top,
                    label = Copy.WHERE_THE_MARK_STARTS,
                    density = density,
                    onMoved = { point -> verseAt(point)?.let(currentDragTo) },
                    onSettled = { currentDragEnded() },
                    onStep = { forward ->
                        val to = if (forward) liftedVerses.first + 1 else liftedVerses.first - 1
                        if (page.verseText.containsKey(to)) currentDragTo(to)
                    },
                )
                SelectionHandle(
                    x = tail.right,
                    y = tail.bottom,
                    label = Copy.WHERE_THE_MARK_ENDS,
                    density = density,
                    onMoved = { point -> verseAt(point)?.let(currentDragTo) },
                    onSettled = { currentDragEnded() },
                    onStep = { forward ->
                        val to = if (forward) liftedVerses.last + 1 else liftedVerses.last - 1
                        if (page.verseText.containsKey(to)) currentDragTo(to)
                    },
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
                        // (§11 Motor). These nodes used to carry a label and
                        // nothing else, so the app's central act — leaving a
                        // note at a verse — had a long-press-and-drag as its
                        // only door. The lift plays its haptic here too, at
                        // the moment the verse lifts, exactly as the drag's
                        // own start does (§9.3).
                        .clearAndSetSemantics {
                            contentDescription = label
                            onClick(label = Copy.OPEN_WHATS_HERE) {
                                currentTap(verse)
                                true
                            }
                            customActions = listOf(
                                CustomAccessibilityAction(Copy.LEAVE_SOMETHING_HERE) {
                                    haptics?.verseLifts()
                                    currentLongPress(verse)
                                    true
                                },
                            )
                        },
                )
            }
        }
    }
}

/** Whether this touch has already lifted a verse. */
private class GestureState {
    var lifted: Boolean = false
}

/**
 * One end of a lifted selection: a small knob you can pull, in the accent —
 * this is the app's own furniture rather than anybody's ink, and it is drawn
 * in the same chartreuse as the caret for that reason (§4.5 keeps chartreuse
 * out of the eight and out of the reader's hands).
 *
 * The knob is 10 dp and the target is 44 (§11, deviation 12), hung off the
 * corner it marks so the drawn part sits on the text's edge while the part a
 * thumb has to find is the size of a thumb.
 *
 * Every drag has the tap equivalent §11 requires, as two custom actions on the
 * handle itself — move this end on a verse, either way — because a handle you
 * can only *drag* is a handle that does not exist for half the people S06 was
 * written for.
 */
@Composable
private fun SelectionHandle(
    x: Float,
    y: Float,
    label: String,
    density: Density,
    onMoved: (Offset) -> Unit,
    onSettled: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
) {
    val accent = Palette.accent
    val target = with(density) { HANDLE_TARGET.toPx() }
    val knob = with(density) { HANDLE_KNOB.toPx() }
    // Where the finger last was, in the text's own coordinates, so a drag can
    // be hit-tested against the page exactly as the long-press drag is.
    var travel by remember(x, y) { mutableStateOf(Offset(x, y)) }

    Box(
        modifier = Modifier
            // Centred on the corner it marks: the top-left of the first verse
            // and the bottom-right of the last, which is where a hand expects
            // the ends of a run of text to be held.
            .offset {
                IntOffset(
                    (x - target / 2f).roundToInt(),
                    (y - target / 2f).roundToInt(),
                )
            }
            .size(HANDLE_TARGET)
            .pointerInput(x, y) {
                detectDragGestures(
                    onDragStart = { travel = Offset(x, y) },
                    onDragEnd = { onSettled() },
                    onDragCancel = { onSettled() },
                ) { change, delta ->
                    change.consume()
                    travel += delta
                    onMoved(travel)
                }
            }
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(Copy.A_VERSE_FURTHER_ON) {
                        onStep(true)
                        true
                    },
                    CustomAccessibilityAction(Copy.A_VERSE_BACK) {
                        onStep(false)
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(HANDLE_TARGET)) {
            drawCircle(color = accent, radius = knob / 2f)
        }
    }
}

/** The knob, and the target around it (§11, deviation 12). */
private val HANDLE_KNOB = 10.dp
private val HANDLE_TARGET = 44.dp

// MARK: - The washes

/**
 * One verse's wash, as it is drawn this frame: the colour the inks on it
 * make, how far up it is, and — when you are the one who just made it — how
 * far along the words the stroke has got.
 */
@Immutable
internal data class Wash(val color: Color, val alpha: Float, val drawn: Float = 1f)

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
    verseInks: Map<Int, List<Ink>>,
    justMarked: IntRange?,
    onMarkDrawn: () -> Unit,
    still: Boolean,
): Map<Int, Wash> {
    val settled = remember(verseInks) {
        verseInks.mapNotNull { (verse, inks) ->
            if (inks.isEmpty()) null else verse to washFor(inks)
        }.toMap()
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
    LaunchedEffect(justMarked) {
        if (justMarked == null) return@LaunchedEffect
        stroke.snapTo(0f)
        stroke.animateTo(1f, RibbonMotion.settle(still))
        markDrawn()
    }

    val t = travel.value
    val pen = stroke.value
    val striking = if (pen < 1f) justMarked else null
    if (t >= 1f && striking == null) return settled

    val drawn = LinkedHashMap<Int, Wash>(settled.size + from.size)
    for ((verse, now) in settled) {
        // A verse you are marking right now is at full colour from the first
        // frame and is revealed along its length instead: the ink is not
        // getting darker, the pen is moving.
        if (striking != null && verse in striking) {
            drawn[verse] = now.copy(drawn = pen)
            continue
        }
        val was = from[verse]
        drawn[verse] = if (was == null) {
            now.copy(alpha = now.alpha * t)
        } else {
            Wash(lerp(was.color, now.color, t), was.alpha + (now.alpha - was.alpha) * t)
        }
    }
    // Taken back: down to nothing rather than gone between two frames.
    for ((verse, was) in from) {
        if (verse !in settled) drawn[verse] = was.copy(alpha = was.alpha * (1f - t))
    }
    return drawn
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
    washes: Map<Int, Wash>,
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

    val ordered = washes.entries
        .mapNotNull { (verse, wash) ->
            if (wash.alpha <= 0f) return@mapNotNull null
            val range = page.verseRanges[verse] ?: return@mapNotNull null
            range to wash
        }
        .sortedBy { it.first.first }

    for ((range, wash) in ordered) {
        val rects = enclosingRects(layout, range)
        if (rects.isEmpty()) continue

        // Each line's band, and the single shape they make together.
        val bands = ArrayList<WashRect>(rects.size)
        var union: Path? = null
        rects.forEachIndexed { index, rect ->
            // The pen lifting: a stable hash, so it does not shimmer on
            // redraw, and horizontal only.
            val wobble = ((range.first * 31 + index * 7) % 3 - 1) * wobbleUnit
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
        var left = bands.sumOf { (it.right - it.left).toDouble() }.toFloat() * wash.drawn
        for (band in bands) {
            if (left <= 0f) break
            val width = band.right - band.left
            val reach = min(width, left)
            left -= reach
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
                    // The tip itself: the last few millimetres run out into
                    // nothing, the way the wet end of a stroke does. A hard
                    // vertical edge travelling across Scripture is a wipe
                    // transition, and it is the one part of this the eye
                    // reads as a screen doing something rather than as ink.
                    val tip = min(feather, reach)
                    val solid = (reach - tip) / reach
                    drawPath(
                        shape,
                        Brush.horizontalGradient(
                            solid to color,
                            1f to color.copy(alpha = 0f),
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

    val ivory = room.text
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
