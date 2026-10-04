package app.readribbon.screens

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.TranslationID
import app.readribbon.core.WhatsNewItem
import app.readribbon.core.WhatsNewRelease
import app.readribbon.core.displayName
import app.readribbon.design.Palette
import app.readribbon.design.RibbonFonts
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.ScreenMargin
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.color
import app.readribbon.design.paper
import app.readribbon.design.peeled
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberBackPeel
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room

// What's new (A61, §12.3): one screen, once per release, between the launch
// mark and the room.
//
// The build book forbids exactly this (§6.2: "No 'what's new'"; §6.1: "no
// carousel, no feature walkthrough"), and the owner asked for it anyway —
// "kinda showcase it with little animations and just tell people about it
// when they open on a new version". So it is built as the smallest true
// version of that: one page rather than a carousel, three short things in
// the app's own voice, and a single way on that says where it goes. Nothing
// advances on its own and nothing holds the button; it is in front of the
// room for exactly as long as the person leaves it there.
//
// The "little animations" are drawn, not played: the app's own type on its
// own paper, its own lift, its own ink wash and its own follow line, doing
// what they do on the page. No images, no confetti, no badges, no counts —
// a picture of a feature that looked like an advert for it would undo the
// rest of the screen. Each one loops on a pure clock so that a frame of it
// is a function of the time and can be photographed, and under reduce
// motion it is that function at one moment: the end state, held.

/** How tall a vignette's paper is — inside §12.3's 120–160. */
private val VIGNETTE_HEIGHT = 140.dp

/** How deep the fade is where the scroll meets the pinned control. */
private val SCROLL_FADE = 28.dp

/** How far each vignette starts behind the one above it, so three loops never beat together. */
private const val STAGGER_MS = 600L

/**
 * The screen.
 *
 * @param release what this launch is telling the person about.
 * @param onLeave every way out — the control at the foot, back, Escape. The
 *   caller records the release as seen and takes the cover away.
 * @param frozenAt holds every vignette at this moment of its loop instead of
 *   running the clock. For the look book, which has to be able to photograph
 *   a beat; the app never passes it.
 */
@Composable
fun WhatsNewScreen(
    release: WhatsNewRelease,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    frozenAt: Long? = null,
) {
    // One door, guarded, as the menu's is: a back gesture committed while
    // the button's tap is still being delivered must not leave twice.
    var leaving by remember { mutableStateOf(false) }
    fun leave() {
        if (leaving) return
        leaving = true
        onLeave()
    }

    // Predictive back peels the cover off the room with the same physics as
    // the menu and the book, and a committed pull is carried straight into
    // the slide away. Registered after the room's own handlers, so it wins.
    val peel = rememberBackPeel(enabled = !leaving, onBack = { leave() })

    // Focus starts on the heading (§12.3): what this is, before what is in it.
    val headingFocus = remember { FocusRequester() }
    LaunchedEffect(headingFocus) { runCatching { headingFocus.requestFocus() } }

    Box(
        modifier
            .fillMaxSize()
            .semantics { paneTitle = Copy.WHATS_NEW_HEADING }
            .onPreviewKeyEvent { event ->
                // Esc on a hardware keyboard, as the menu takes it.
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    leave()
                    true
                } else {
                    false
                }
            }
            .peeled { peel.progress },
    ) {
        // The room's ground and its grain — this is a page of the room, not
        // glass over it. It also takes every touch the screen itself does
        // not: a cover drawn in the same Box as the room does not stop the
        // room's header being tapped through it on its own.
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

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            // Scrolls when the screen is short; the way on never does.
            val scroll = rememberScrollState()
            val ground = Palette.ground
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // While there is more below, the words go *into* the
                    // ground above the way on rather than being cut off at
                    // it — a hard edge across a line of type reads as a
                    // layout that ran out of room, not as more to come.
                    .drawWithContent {
                        drawContent()
                        if (scroll.canScrollForward) {
                            val fade = SCROLL_FADE.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ground),
                                    startY = size.height - fade,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height - fade),
                                size = Size(size.width, fade),
                            )
                        }
                    }
                    .verticalScroll(scroll),
            ) {
                Column(
                    Modifier
                        .readableColumn()
                        .padding(horizontal = ScreenMargin + 4.dp),
                ) {
                    Spacer(Modifier.height(44.dp))
                    // The heading and the line under it are one header for a
                    // screen reader — "What's new, The words under the words"
                    // — and the first thing it lands on.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(headingFocus)
                            .focusable()
                            .semantics(mergeDescendants = true) { heading() },
                    ) {
                        SmallCaps(Copy.WHATS_NEW_HEADING, size = 13f, color = Palette.muted)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = Copy.WHATS_NEW_TITLE,
                            style = RibbonType.display(32f),
                            color = Palette.text,
                        )
                    }
                    Spacer(Modifier.height(36.dp))
                    release.items.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(40.dp))
                        WhatsNewEntry(item = item, startAfter = STAGGER_MS * index, frozenAt = frozenAt)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }

            Box(
                Modifier
                    .readableColumn()
                    .padding(horizontal = ScreenMargin + 4.dp)
                    .padding(top = 12.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                WayInButton(title = Copy.WHATS_NEW_DONE, onClick = { leave() })
            }
        }
    }
}

/**
 * One thing that is new: its picture, then what it is, then one or two lines.
 * The picture is decoration and is hidden from a screen reader; the words are
 * one element, read title then body.
 */
@Composable
private fun WhatsNewEntry(item: WhatsNewItem, startAfter: Long, frozenAt: Long?) {
    Column(Modifier.fillMaxWidth()) {
        Vignette(
            item = item,
            startAfter = startAfter,
            frozenAt = frozenAt,
            modifier = Modifier
                .fillMaxWidth()
                .height(VIGNETTE_HEIGHT)
                .clearAndSetSemantics { }
                .paper(RibbonShape.cardShape),
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
            Text(
                text = item.title,
                style = RibbonType.ui(18f, FontWeight.Medium),
                color = Palette.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.body,
                style = RibbonType.ui(16f),
                color = Palette.muted,
            )
        }
    }
}

private val WhatsNewItem.title: String
    get() = when (this) {
        WhatsNewItem.original -> Copy.WHATS_NEW_ORIGINAL_TITLE
        WhatsNewItem.ownVersion -> Copy.WHATS_NEW_OWN_VERSION_TITLE
        WhatsNewItem.followingWords -> Copy.WHATS_NEW_FOLLOWING_TITLE
    }

private val WhatsNewItem.body: String
    get() = when (this) {
        WhatsNewItem.original -> Copy.WHATS_NEW_ORIGINAL_BODY
        WhatsNewItem.ownVersion -> Copy.WHATS_NEW_OWN_VERSION_BODY
        WhatsNewItem.followingWords -> Copy.WHATS_NEW_FOLLOWING_BODY
    }

// MARK: - The clock
//
// Every vignette is a pure function of a time inside its loop: `t` in
// milliseconds, 0 at rest, wrapping at [LOOP_MS]. The values below are that
// function. Keeping the timeline out of coroutines and Animatables is what
// lets reduce motion be one number ([STILL_AT]) rather than a second code
// path, and what lets a test or the look book ask for any frame it likes.
//
// Every movement is on the app's own ease-out and inside the motion
// tokens' 320–480 ms; between the movement and the next loop is a held
// pause of about 1.8 s, so the eye has time to read what just happened.

/** One whole loop of any vignette. */
internal const val LOOP_MS = 4600

/**
 * The moment reduce motion holds every vignette at: inside each one's held
 * pause, where everything that moves has arrived. It is the end state — the
 * word lifted with its original over it, both washes laid down, both lines
 * under the same words — and it never loops (§11).
 */
internal const val STILL_AT = 3000

/** Where a loop is, [elapsedMs] after the screen appeared and [startAfter] late. */
internal fun loopTime(elapsedMs: Long, startAfter: Long): Int =
    if (elapsedMs < startAfter) 0 else ((elapsedMs - startAfter) % LOOP_MS).toInt()

/** A movement starting at [at] and lasting [ms]: 0 before, 1 after, eased out between. */
private fun beat(t: Int, at: Int, ms: Int): Float {
    val raw = ((t - at).toFloat() / ms).coerceIn(0f, 1f)
    return RibbonMotion.EaseOut.transform(raw)
}

/** The original: a press settles on "Word", it lifts, and its Greek arrives over it. */
internal data class OriginalFrame(val press: Float, val lift: Float, val line: Float)

internal fun originalFrame(t: Int): OriginalFrame {
    // All three settle back together, on one 400 ms beat.
    val back = 1f - beat(t, at = 3660, ms = 400)
    return OriginalFrame(
        press = beat(t, at = 500, ms = 320) * back,
        lift = beat(t, at = 900, ms = 400) * back,
        line = beat(t, at = 1380, ms = 480) * back,
    )
}

/** Your own version: the same ink drawn along the same words, in two versions. */
internal data class OwnVersionFrame(val first: Float, val second: Float, val ink: Float)

internal fun ownVersionFrame(t: Int): OwnVersionFrame = OwnVersionFrame(
    first = beat(t, at = 500, ms = 480),
    second = beat(t, at = 1480, ms = 480),
    // The washes fade away rather than undraw: a mark is not taken back
    // stroke by stroke, and the loop is not saying that it is.
    ink = 1f - beat(t, at = 3760, ms = 400),
)

/**
 * Following: the leader's line slides to "all things"; the follower's line
 * appears where the same *fraction* of its own line would be — the old
 * landing — and glides from there to the same words.
 */
internal data class FollowingFrame(
    val leader: Float,
    val follower: Float,
    val followerShown: Float,
    val shown: Float,
)

internal fun followingFrame(t: Int): FollowingFrame = FollowingFrame(
    leader = beat(t, at = 500, ms = 480),
    followerShown = beat(t, at = 1480, ms = 320),
    follower = beat(t, at = 1900, ms = 480),
    shown = 1f - beat(t, at = 4080, ms = 400),
)

/**
 * The loop's clock for one vignette, as state read only while drawing — so
 * the picture moves and nothing recomposes.
 *
 * An *infinite* frame loop, which is what it is: Compose's own tests know to
 * leave one of these alone rather than wait for it to finish.
 */
@Composable
private fun rememberLoopClock(still: Boolean, startAfter: Long, frozenAt: Long?): LongState {
    // Starts where it will be held, so the first frame — and a clock that
    // never runs at all — is already right: at rest, or under reduce motion
    // at the end state.
    val time = remember(still, frozenAt) {
        mutableLongStateOf(frozenAt ?: if (still) STILL_AT.toLong() else 0L)
    }
    LaunchedEffect(still, startAfter, frozenAt) {
        if (still || frozenAt != null) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                time.longValue = loopTime((now - start) / 1_000_000L, startAfter).toLong()
            }
        }
    }
    return time
}

// MARK: - The vignettes

/** The Scripture each picture sets, as the two versions print it (John 1:1, 1:3). */
private const val WORD_LINE = "the Word was with God"
private const val WORD = "Word"
private const val GREEK = "λόγος"
private const val TRANSLIT = "logos"
private const val BEREAN_LINE = "Through Him all things were made"
private const val WORLD_LINE = "All things were made through him"
private const val BEREAN_WORDS = "Through Him"
private const val WORLD_WORDS = "through him"
private const val BEREAN_FOLLOWED = "all things"
private const val WORLD_FOLLOWED = "All things"

/** The page's margin inside the paper. */
private val INSET = 24.dp

/** The wash's geometry, as the page draws it (ChapterText's `drawWashes`). */
private val WASH_BLEED_X = 2.dp
private val WASH_BLEED_Y = 1.2.dp
private val WASH_CORNER = 5.dp
private const val WASH_ABOVE_BASELINE = 0.88f
private const val WASH_BELOW_BASELINE = 0.28f

/** The lift, as the page draws it: a 2 pt rise over a soft shadow (ChapterText). */
private val LIFT_RISE = 2.dp
private val LIFT_SHADOW_Y = 3.dp
private val LIFT_SHADOW_BLUR = 8.dp

/** The reading line: the follow thread's weight and paint, laid under the words. */
private val READING_LINE = 1.5.dp
private const val READING_LINE_ALPHA = 0.7f

/**
 * One picture. Its type is set once per size, in the draw cache — measuring
 * a line of Literata sixty times a second to draw the same words would be
 * the expensive half of an animation that is otherwise a few rectangles —
 * and only the clock is read while drawing.
 */
@Composable
private fun Vignette(item: WhatsNewItem, startAfter: Long, frozenAt: Long?, modifier: Modifier) {
    val still = rememberReduceMotion()
    val clock = rememberLoopClock(still = still, startAfter = startAfter, frozenAt = frozenAt)
    val measurer = rememberTextMeasurer()
    val inks = VignetteInks(
        text = Palette.text,
        muted = Palette.muted,
        accent = Palette.accent,
        wash = Ink.ochre.color.copy(alpha = Palette.HIGHLIGHT_WASH),
    )
    val faces = remember {
        VignetteFaces(
            scripture = RibbonFonts.literata(FontWeight.Normal, 19f),
            italic = RibbonFonts.literata(FontWeight.Normal, 14f, italic = true),
        )
    }
    val smallCaps = RibbonType.smallCaps(11f)
    val ui = RibbonType.ui(15f)
    val drawing = when (item) {
        WhatsNewItem.original -> Modifier.drawWithCache {
            val page = layOutTheWord(measurer, inks, faces, ui)
            onDrawBehind { drawTheWord(page, originalFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.ownVersion -> Modifier.drawWithCache {
            val page = layOutTwoVersions(measurer, inks, faces, smallCaps)
            onDrawBehind { drawOwnVersion(page, inks, ownVersionFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.followingWords -> Modifier.drawWithCache {
            val page = layOutTwoVersions(measurer, inks, faces, smallCaps)
            onDrawBehind { drawFollowing(page, inks, followingFrame(clock.longValue.toInt())) }
        }
    }
    Box(modifier.then(drawing))
}

/** The room's paints, read once in composition and handed to the drawing. */
private class VignetteInks(val text: Color, val muted: Color, val accent: Color, val wash: Color)

private class VignetteFaces(val scripture: FontFamily, val italic: FontFamily)

/**
 * Sets [text] in [style], smaller if it would not fit [maxWidth] — a narrow
 * phone at a large font scale still gets the whole line, never a cut one.
 * One line, always: each picture's geometry is of one line.
 */
private fun Density.fitted(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    maxWidth: Float,
): TextLayoutResult {
    val first = measurer.measure(text, style, softWrap = false, maxLines = 1, density = this)
    val width = first.size.width.toFloat()
    if (width <= maxWidth || width <= 0f) return first
    val scaled = style.copy(fontSize = style.fontSize * (maxWidth / width * 0.98f))
    return measurer.measure(text, scaled, softWrap = false, maxLines = 1, density = this)
}

/** Where [part] of a one-line layout of [of] sits: its horizontal stretch, on its line. */
private fun TextLayoutResult.stretch(of: String, part: String): Rect {
    val start = of.indexOf(part).coerceAtLeast(0)
    val end = (start + part.length).coerceAtMost(of.length)
    val left = getHorizontalPosition(start, usePrimaryDirection = true)
    val right = getHorizontalPosition(end, usePrimaryDirection = true)
    return Rect(left, getLineTop(0), right, getLineBottom(0))
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Draws [block] at [alpha] as one layer, so the runs inside it fade as one. */
private inline fun DrawScope.faded(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 1f) {
        block()
        return
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }
}

// MARK: The original

/**
 * The first picture, set: John 1:1 in Literata with "Word" left out of the
 * line so it can be drawn on its own and lift without the line moving under
 * it, and the line the page shows for a held word — `λόγος · logos · Word`.
 */
private class TheWord(
    val line: TextLayoutResult,
    val lineAt: Offset,
    val word: TextLayoutResult,
    val wordAt: Offset,
    val press: Offset,
    val pressRadius: Float,
    val pressSettledRadius: Float,
    val original: TextLayoutResult,
    val originalAt: Offset,
    val rise: Float,
    val shadowY: Float,
    val shadowBlur: Float,
    val pressInk: Color,
)

private fun CacheDrawScope.layOutTheWord(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    ui: TextStyle,
): TheWord {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 21.sp)

    val sentence = buildAnnotatedString {
        withStyle(SpanStyle(color = inks.text)) {
            val at = WORD_LINE.indexOf(WORD)
            append(WORD_LINE.substring(0, at))
            withStyle(SpanStyle(color = Color.Transparent)) { append(WORD) }
            append(WORD_LINE.substring(at + WORD.length))
        }
    }
    val line = fitted(measurer, sentence, style, room)
    val lineAt = Offset(
        x = (size.width - line.size.width) / 2f,
        y = size.height * 0.6f - line.size.height / 2f,
    )
    val baseline = lineAt.y + line.getLineBaseline(0)
    val stretch = line.stretch(WORD_LINE, WORD).translate(lineAt)

    val word = measurer.measure(
        AnnotatedString(WORD),
        line.layoutInput.style.copy(color = inks.text),
        softWrap = false,
        maxLines = 1,
        density = this,
    )
    val wordAt = Offset(stretch.left, baseline - word.getLineBaseline(0))
    val em = line.layoutInput.style.fontSize.toPx()

    // The original's line: Greek in Literata, how to say it in italic, and
    // the version's own word, the dots between them muted.
    val dot = SpanStyle(color = inks.muted)
    val over = buildAnnotatedString {
        withStyle(SpanStyle(color = inks.text, fontFamily = faces.scripture, fontSize = 19.sp)) { append(GREEK) }
        withStyle(dot) { append("  ·  ") }
        withStyle(SpanStyle(color = inks.muted, fontFamily = faces.italic, fontStyle = FontStyle.Italic)) {
            append(TRANSLIT)
        }
        withStyle(dot) { append("  ·  ") }
        withStyle(SpanStyle(color = inks.text)) { append(WORD) }
    }
    val original = fitted(measurer, over, ui.copy(textDirection = TextDirection.Ltr), room)
    val originalAt = Offset(
        x = (stretch.center.x - original.size.width / 2f)
            .coerceIn(inset, (size.width - inset - original.size.width).coerceAtLeast(inset)),
        y = baseline - em * 1.05f - original.size.height - 8.dp.toPx(),
    )

    return TheWord(
        line = line,
        lineAt = lineAt,
        word = word,
        wordAt = wordAt,
        press = Offset(stretch.center.x, baseline - em * 0.38f),
        pressRadius = 22.dp.toPx(),
        pressSettledRadius = 15.dp.toPx(),
        original = original,
        originalAt = originalAt,
        rise = LIFT_RISE.toPx(),
        shadowY = LIFT_SHADOW_Y.toPx(),
        shadowBlur = LIFT_SHADOW_BLUR.toPx(),
        pressInk = inks.text,
    )
}

private fun DrawScope.drawTheWord(page: TheWord, frame: OriginalFrame) {
    // The press: a soft round of the room's own ink settling to its size, as
    // a fingertip does on glass — larger and fainter first.
    if (frame.press > 0f) {
        val radius = lerp(page.pressRadius, page.pressSettledRadius, frame.press)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(page.pressInk.copy(alpha = 0.2f * frame.press), Color.Transparent),
                center = page.press,
                radius = radius,
            ),
            radius = radius,
            center = page.press,
        )
    }

    drawText(page.line, topLeft = page.lineAt)

    // The word, lifted by the page's own rise and shadow.
    drawText(
        page.word,
        topLeft = page.wordAt - Offset(0f, page.rise * frame.lift),
        shadow = if (frame.lift > 0f) {
            Shadow(
                color = Color.Black.copy(alpha = 0.7f * frame.lift),
                offset = Offset(0f, page.shadowY * frame.lift),
                blurRadius = page.shadowBlur * frame.lift,
            )
        } else {
            null
        },
    )

    faded(frame.line) { drawText(page.original, topLeft = page.originalAt) }
}

// MARK: Two versions

/**
 * The second and third pictures share a page: John 1:3 as the Berean
 * Standard prints it and as the World English does, one above the other,
 * each under its name in small caps. The same words sit at opposite ends of
 * the two lines, which is the whole of what both pictures are about.
 */
private class TwoVersions(
    val first: VersionRow,
    val second: VersionRow,
    val bleedX: Float,
    val bleedY: Float,
    val corner: Float,
    val readingLine: Float,
    val readingLineGap: Float,
)

private class VersionRow(
    val label: TextLayoutResult,
    val labelAt: Offset,
    val line: TextLayoutResult,
    val lineAt: Offset,
    val text: String,
    val em: Float,
) {
    val baseline: Float get() = lineAt.y + line.getLineBaseline(0)
    fun stretch(part: String): Rect = line.stretch(text, part).translate(lineAt)
}

private fun CacheDrawScope.layOutTwoVersions(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): TwoVersions {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 19.sp, color = inks.text)
    // Both lines at one size — whichever fits the narrower — so the picture
    // is of two versions and not of two type sizes.
    val fits = listOf(BEREAN_LINE, WORLD_LINE).minOf {
        fitted(measurer, AnnotatedString(it), style, room).layoutInput.style.fontSize.value
    }
    val set = style.copy(fontSize = fits.sp)
    val label = smallCaps.copy(color = inks.muted)

    fun lay(name: String, text: String): Pair<TextLayoutResult, TextLayoutResult> =
        measurer.measure(AnnotatedString(name), label, softWrap = false, maxLines = 1, density = this) to
            measurer.measure(AnnotatedString(text), set, softWrap = false, maxLines = 1, density = this)

    val (firstLabel, firstLine) = lay(TranslationID.bsb.displayName, BEREAN_LINE)
    val (secondLabel, secondLine) = lay(TranslationID.web.displayName, WORLD_LINE)

    val gap = 2.dp.toPx()
    val block = firstLabel.size.height + gap + firstLine.size.height
    val spare = (size.height - block * 2).coerceAtLeast(0f)
    val top = spare * 0.36f
    val secondTop = top + block + spare * 0.28f

    fun row(labelLayout: TextLayoutResult, lineLayout: TextLayoutResult, at: Float, text: String) = VersionRow(
        label = labelLayout,
        labelAt = Offset(inset, at),
        line = lineLayout,
        lineAt = Offset(inset, at + labelLayout.size.height + gap),
        text = text,
        em = lineLayout.layoutInput.style.fontSize.toPx(),
    )

    return TwoVersions(
        first = row(firstLabel, firstLine, top, BEREAN_LINE),
        second = row(secondLabel, secondLine, secondTop, WORLD_LINE),
        bleedX = WASH_BLEED_X.toPx(),
        bleedY = WASH_BLEED_Y.toPx(),
        corner = WASH_CORNER.toPx(),
        readingLine = READING_LINE.toPx(),
        readingLineGap = 3.dp.toPx(),
    )
}

private fun DrawScope.drawRows(page: TwoVersions) {
    for (row in listOf(page.first, page.second)) {
        drawText(row.label, topLeft = row.labelAt)
        drawText(row.line, topLeft = row.lineAt)
    }
}

/**
 * An ink wash along [part] of [row], [drawn] of the way from its left edge:
 * the pen travelling, as a mark you make yourself is laid on the page,
 * rather than faded up where it lies. Hung off the baseline at the height of
 * the letters, as the page hangs it.
 */
private fun DrawScope.wash(page: TwoVersions, row: VersionRow, part: String, ink: Color, drawn: Float, alpha: Float) {
    if (drawn <= 0f || alpha <= 0f) return
    val stretch = row.stretch(part)
    val top = row.baseline - row.em * WASH_ABOVE_BASELINE - page.bleedY
    val bottom = row.baseline + row.em * WASH_BELOW_BASELINE + page.bleedY
    val left = stretch.left - page.bleedX
    val right = stretch.right + page.bleedX
    clipRect(left = left, top = top, right = lerp(left, right, drawn), bottom = bottom) {
        drawRoundRect(
            color = ink.copy(alpha = ink.alpha * alpha),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(page.corner),
        )
    }
}

private fun DrawScope.drawOwnVersion(page: TwoVersions, inks: VignetteInks, frame: OwnVersionFrame) {
    // Behind the glyphs, as the page lays a wash.
    wash(page, page.first, BEREAN_WORDS, inks.wash, frame.first, frame.ink)
    wash(page, page.second, WORLD_WORDS, inks.wash, frame.second, frame.ink)
    drawRows(page)
}

/** The reading line under a stretch of a row: the follow thread's paint, laid flat. */
private fun DrawScope.readingLine(page: TwoVersions, row: VersionRow, inks: VignetteInks, left: Float, right: Float, alpha: Float) {
    if (alpha <= 0f) return
    val y = row.baseline + row.em * WASH_BELOW_BASELINE + page.readingLineGap
    drawLine(
        color = inks.accent.copy(alpha = READING_LINE_ALPHA * alpha),
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = page.readingLine,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawFollowing(page: TwoVersions, inks: VignetteInks, frame: FollowingFrame) {
    drawRows(page)

    // The leader's line arrives from the start of theirs and slides to the
    // words they are reading.
    val first = page.first
    val from = first.stretch(BEREAN_WORDS.substringBefore(' '))
    val to = first.stretch(BEREAN_FOLLOWED)
    readingLine(
        page, first, inks,
        left = lerp(from.left, to.left, frame.leader),
        right = lerp(from.right, to.right, frame.leader),
        alpha = frame.leader * frame.shown,
    )

    // The follower's appears where the same share of *their* line would put
    // it — where a follow used to land, on other words — and glides from
    // there to the same words.
    val second = page.second
    val target = second.stretch(WORLD_FOLLOWED)
    val share = (to.center.x - first.lineAt.x) / first.line.size.width
    val sameShare = second.lineAt.x + share * second.line.size.width
    val centre = lerp(sameShare, target.center.x, frame.follower)
    val half = target.width / 2f
    readingLine(
        page, second, inks,
        left = centre - half,
        right = centre + half,
        alpha = frame.followerShown * frame.shown,
    )
}
