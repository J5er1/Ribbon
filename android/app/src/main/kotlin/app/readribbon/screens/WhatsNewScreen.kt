package app.readribbon.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.Ink
import app.readribbon.core.PageFaces
import app.readribbon.core.PageType
import app.readribbon.core.QuietHoursBand
import app.readribbon.core.TranslationID
import app.readribbon.core.WhatsNew
import app.readribbon.core.WhatsNewItem
import app.readribbon.core.WhatsNewRelease
import app.readribbon.core.displayName
import app.readribbon.design.LocalRoomColours
import app.readribbon.design.Palette
import app.readribbon.design.RibbonFonts
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.ScreenMargin
import app.readribbon.design.Seam
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.color
import app.readribbon.design.drawRibbonTail
import app.readribbon.design.paper
import app.readribbon.design.peeled
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberBackPeel
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.drawEmber
import app.readribbon.reading.LINE_HEBREW_SIZE
import app.readribbon.reading.SELECTION_TINT

// What's new (A61, §12.3): one screen, once per release, between the launch
// mark and the room.
//
// The build book forbids exactly this (§6.2: "No 'what's new'"; §6.1: "no
// carousel, no feature walkthrough"), and the owner asked for it anyway —
// "kinda showcase it with little animations and just tell people about it
// when they open on a new version". So it is built as the smallest true
// version of that: one page rather than a carousel, a few short things in
// the app's own voice, and a single way on that says where it goes. Nothing
// advances on its own and nothing holds the button; it is in front of the
// room for exactly as long as the person leaves it there. Read again from
// You (A65), it is the same page with every release on it, newest first.
//
// The "little animations" are drawn, not played: the app's own type on its
// own paper, its own lift, its own ink wash and its own follow line — and
// since A67 its own ribbons, embers and night — doing what they do in the
// app. No images, no confetti, no badges, no counts —
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

/** The air above a release's heading when it follows another, read again (A65). */
private val BETWEEN_RELEASES = 72.dp

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
    WhatsNewPage(
        releases = listOf(release),
        readAgain = false,
        onLeave = onLeave,
        modifier = modifier,
        frozenAt = frozenAt,
    )
}

/**
 * What's new, read again (A65): the same page, from You, with every release
 * on it — newest first, each under the day it came out and its own title,
 * with all of its pictures and words.
 *
 * It decides nothing and records nothing. Whether the launch shows a release
 * is the model's (`AppModel.whatsNew`), and reading them here neither marks
 * one seen nor brings one back. It is a page in the menu like the others in
 * You, so back is the menu's: it goes back to You the way every row there
 * does, and the foot says "Done" rather than where it goes, because it goes
 * nowhere new.
 *
 * @param onLeave the control at the foot. Back and Escape are the menu's.
 * @param releases newest first; the core's, unless the look book wants others.
 * @param frozenAt as [WhatsNewScreen]'s.
 */
@Composable
fun WhatsNewHistoryScreen(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    releases: List<WhatsNewRelease> = WhatsNew.releases,
    frozenAt: Long? = null,
) {
    WhatsNewPage(
        releases = releases,
        readAgain = true,
        onLeave = onLeave,
        modifier = modifier,
        frozenAt = frozenAt,
    )
}

/**
 * The page both are: the room's ground, the releases one under the other, and
 * one control pinned at the foot. On a launch it is one release under "What's
 * new"; read again it is all of them, each under its day.
 *
 * A lazy list either way, so a picture is drawn — and its clock runs — only
 * while it is on the screen: read again, a dozen pictures and counting would
 * otherwise all be running at once for the three in view.
 */
@Composable
private fun WhatsNewPage(
    releases: List<WhatsNewRelease>,
    readAgain: Boolean,
    onLeave: () -> Unit,
    modifier: Modifier,
    frozenAt: Long?,
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
    // Read again, the page is one of the menu's, and back belongs to the
    // menu's own stack, which takes it back to You as it does every page
    // there.
    val peel = rememberBackPeel(enabled = !leaving && !readAgain, onBack = { leave() })

    // Focus starts on the heading (§12.3): what this is, before what is in it.
    // Asked once; a heading scrolled away and back is not asked again.
    val headingFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .semantics { paneTitle = Copy.WHATS_NEW_HEADING }
            .onPreviewKeyEvent { event ->
                // Esc on a hardware keyboard, as the menu takes it. Read
                // again, the menu's own handler sees it first and goes back.
                if (!readAgain && event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
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
            val list = rememberLazyListState()
            val ground = Palette.ground
            val column = Modifier
                .readableColumn()
                .padding(horizontal = ScreenMargin + 4.dp)
            LazyColumn(
                state = list,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // While there is more below, the words go *into* the
                    // ground above the way on rather than being cut off at
                    // it — a hard edge across a line of type reads as a
                    // layout that ran out of room, not as more to come.
                    .drawWithContent {
                        drawContent()
                        if (list.canScrollForward) {
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
                    },
            ) {
                // Read again, the page is headed as the iPhone's is: "What's
                // new" over the list, where focus starts, and each release
                // under its day and its own title.
                if (readAgain) {
                    item(key = "whats-new") {
                        LaunchedEffect(headingFocus) {
                            if (!focused) {
                                focused = true
                                runCatching { headingFocus.requestFocus() }
                            }
                        }
                        Column(
                            column
                                .padding(top = 44.dp)
                                .fillMaxWidth()
                                .focusRequester(headingFocus)
                                .focusable()
                                .semantics(mergeDescendants = true) { heading() },
                        ) {
                            SmallCaps(Copy.WHATS_NEW_HEADING, size = 13f, color = Palette.muted)
                        }
                    }
                }
                releases.forEachIndexed { r, release ->
                    item(key = "heading:${release.id}") {
                        val first = r == 0
                        if (first && !readAgain) {
                            LaunchedEffect(headingFocus) {
                                if (!focused) {
                                    focused = true
                                    runCatching { headingFocus.requestFocus() }
                                }
                            }
                        }
                        ReleaseHeading(
                            over = if (readAgain) {
                                Copy.whatsNewReleased(release.released) ?: Copy.WHATS_NEW_HEADING
                            } else {
                                Copy.WHATS_NEW_HEADING
                            },
                            title = Copy.whatsNewTitle(release.id),
                            modifier = column
                                .padding(
                                    top = when {
                                        !first -> BETWEEN_RELEASES
                                        readAgain -> 28.dp
                                        else -> 44.dp
                                    },
                                )
                                .then(
                                    if (first && !readAgain) {
                                        Modifier.focusRequester(headingFocus).focusable()
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                    release.items.forEachIndexed { index, item ->
                        item(key = "${release.id}/${item.name}") {
                            WhatsNewEntry(
                                item = item,
                                startAfter = STAGGER_MS * index,
                                frozenAt = frozenAt,
                                modifier = column.padding(top = if (index == 0) 36.dp else 40.dp),
                            )
                        }
                    }
                }
                item(key = "foot") { Spacer(Modifier.height(32.dp)) }
            }

            Box(
                Modifier
                    .readableColumn()
                    .padding(horizontal = ScreenMargin + 4.dp)
                    .padding(top = 12.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
            ) {
                WayInButton(
                    title = if (readAgain) Copy.WHATS_NEW_HISTORY_DONE else Copy.WHATS_NEW_DONE,
                    onClick = { leave() },
                )
            }
        }
    }
}

/**
 * A release's heading: small caps over its title — "What's new" on a launch,
 * the day it came out when read again. One header for a screen reader —
 * "What's new, Staying on the same page" — so heading to heading is release
 * to release.
 */
@Composable
private fun ReleaseHeading(over: String, title: String, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
    ) {
        SmallCaps(over, size = 13f, color = Palette.muted)
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = RibbonType.display(32f),
            color = Palette.text,
        )
    }
}

/**
 * One thing that is new: its picture, then what it is, then one or two lines.
 * The picture is decoration and is hidden from a screen reader; the words are
 * one element, read title then body.
 */
@Composable
private fun WhatsNewEntry(item: WhatsNewItem, startAfter: Long, frozenAt: Long?, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
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

internal val WhatsNewItem.title: String
    get() = when (this) {
        WhatsNewItem.original -> Copy.WHATS_NEW_ORIGINAL_TITLE
        WhatsNewItem.ownVersion -> Copy.WHATS_NEW_OWN_VERSION_TITLE
        WhatsNewItem.followingWords -> Copy.WHATS_NEW_FOLLOWING_TITLE
        WhatsNewItem.followingStays -> Copy.WHATS_NEW_FOLLOWING_STAYS_TITLE
        WhatsNewItem.nativeSelection -> Copy.WHATS_NEW_NATIVE_SELECTION_TITLE
        WhatsNewItem.roomGroups -> Copy.WHATS_NEW_ROOM_GROUPS_TITLE
        WhatsNewItem.lordReadsLord -> Copy.WHATS_NEW_LORD_TITLE
        WhatsNewItem.originalReadable -> Copy.WHATS_NEW_READABLE_TITLE
        WhatsNewItem.flyleaf -> Copy.WHATS_NEW_FLYLEAF_TITLE
        WhatsNewItem.yourShelf -> Copy.WHATS_NEW_SHELF_TITLE
        WhatsNewItem.versionsByReading -> Copy.WHATS_NEW_VERSIONS_TITLE
        WhatsNewItem.yourPage -> Copy.WHATS_NEW_PAGE_TITLE
        WhatsNewItem.typeface -> Copy.WHATS_NEW_TYPEFACE_TITLE
        WhatsNewItem.notificationsByName -> Copy.WHATS_NEW_NOTIFICATIONS_TITLE
        WhatsNewItem.quietHoursNight -> Copy.WHATS_NEW_QUIET_HOURS_TITLE
    }

internal val WhatsNewItem.body: String
    get() = when (this) {
        WhatsNewItem.original -> Copy.WHATS_NEW_ORIGINAL_BODY
        WhatsNewItem.ownVersion -> Copy.WHATS_NEW_OWN_VERSION_BODY
        WhatsNewItem.followingWords -> Copy.WHATS_NEW_FOLLOWING_BODY
        WhatsNewItem.followingStays -> Copy.WHATS_NEW_FOLLOWING_STAYS_BODY
        WhatsNewItem.nativeSelection -> Copy.WHATS_NEW_NATIVE_SELECTION_BODY
        WhatsNewItem.roomGroups -> Copy.WHATS_NEW_ROOM_GROUPS_BODY
        WhatsNewItem.lordReadsLord -> Copy.WHATS_NEW_LORD_BODY
        WhatsNewItem.originalReadable -> Copy.WHATS_NEW_READABLE_BODY
        WhatsNewItem.flyleaf -> Copy.WHATS_NEW_FLYLEAF_BODY
        WhatsNewItem.yourShelf -> Copy.WHATS_NEW_SHELF_BODY
        WhatsNewItem.versionsByReading -> Copy.WHATS_NEW_VERSIONS_BODY
        WhatsNewItem.yourPage -> Copy.WHATS_NEW_PAGE_BODY
        WhatsNewItem.typeface -> Copy.WHATS_NEW_TYPEFACE_BODY
        WhatsNewItem.notificationsByName -> Copy.WHATS_NEW_NOTIFICATIONS_BODY
        WhatsNewItem.quietHoursNight -> Copy.WHATS_NEW_QUIET_HOURS_BODY
    }

// MARK: - The clock
//
// Every vignette is a pure function of a time inside its loop: `t` in
// milliseconds, 0 at rest, wrapping at its loop ([LOOP_MS], or
// [LONG_LOOP_MS] for a picture with more beats). The values below are that
// function. Keeping the timeline out of coroutines and Animatables is what
// lets reduce motion be one number per picture ([Timeline.stillAt]) rather
// than a second code path, and what lets a test or the look book ask for
// any frame it likes.
//
// Every movement is on the app's own ease-out and inside the motion
// tokens' 320–480 ms; between the movement and the next loop is a held
// pause of about 1.8 s, so the eye has time to read what just happened.

/** One whole loop of a vignette. */
internal const val LOOP_MS = 4600

/**
 * The moment reduce motion holds a [LOOP_MS] vignette at: inside each one's held
 * pause, where everything that moves has arrived. It is the end state — the
 * word lifted with its original over it, both washes laid down, both lines
 * under the same words — and it never loops (§11).
 */
internal const val STILL_AT = 3000

/**
 * A picture with more to say — three steps and a screen dimming, a press, a
 * drag and a second press, or a page set again three ways — takes a longer
 * loop rather than a shorter breath: its movements are on the same 320–480 ms
 * beats and the pause after them is the same long one (A65).
 */
internal const val LONG_LOOP_MS = 6200

/** How long one picture's loop is, and the moment reduce motion holds it at. */
internal data class Timeline(val loopMs: Int, val stillAt: Int)

/**
 * Each picture's loop: the first release's three, and the short ones since,
 * on the one clock; the pictures with more beats on the longer one.
 */
internal val WhatsNewItem.timeline: Timeline
    get() = when (this) {
        WhatsNewItem.original,
        WhatsNewItem.ownVersion,
        WhatsNewItem.followingWords,
        WhatsNewItem.roomGroups,
        WhatsNewItem.lordReadsLord,
        WhatsNewItem.flyleaf,
        WhatsNewItem.yourShelf,
        WhatsNewItem.versionsByReading,
        WhatsNewItem.notificationsByName,
        -> Timeline(LOOP_MS, STILL_AT)
        WhatsNewItem.followingStays -> Timeline(LONG_LOOP_MS, STAYS_STILL_AT)
        WhatsNewItem.nativeSelection -> Timeline(LONG_LOOP_MS, SELECTION_STILL_AT)
        WhatsNewItem.originalReadable -> Timeline(LONG_LOOP_MS, READABLE_STILL_AT)
        WhatsNewItem.quietHoursNight -> Timeline(LONG_LOOP_MS, NIGHT_STILL_AT)
        WhatsNewItem.yourPage -> Timeline(LONG_LOOP_MS, PAGE_STILL_AT)
        WhatsNewItem.typeface -> Timeline(LONG_LOOP_MS, TYPEFACE_STILL_AT)
    }

/** Where a loop of [loopMs] is, [elapsedMs] after the screen appeared and [startAfter] late. */
internal fun loopTime(elapsedMs: Long, startAfter: Long, loopMs: Int = LOOP_MS): Int =
    if (elapsedMs < startAfter) 0 else ((elapsedMs - startAfter) % loopMs).toInt()

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

// The second release's four (A65).

/** How far the follower's screen dims before it is kept awake: most of the way to a sleeping phone's. */
private const val STAYS_DIM = 0.6f

/** Following stays: everything has landed and the screen is awake again. */
internal const val STAYS_STILL_AT = 4400

/**
 * Following stays with them: the leader's line steps through the line a group
 * of words at a time — [leader] runs 0 to 2, one for each step — and the
 * follower's follows each a beat behind. Between the second step and the
 * third the follower's row begins to dim, as a screen about to sleep does, and
 * is back to full before it is dark ([awake], 1 for full).
 */
internal data class StaysFrame(
    val leaderShown: Float,
    val leader: Float,
    val followerShown: Float,
    val follower: Float,
    val awake: Float,
    val shown: Float,
)

internal fun staysFrame(t: Int): StaysFrame {
    // Starting to sleep, and then not: the follow keeps it on.
    val dim = beat(t, at = 2440, ms = 480) * (1f - beat(t, at = 3000, ms = 320))
    return StaysFrame(
        leaderShown = beat(t, at = 400, ms = 320),
        leader = beat(t, at = 1400, ms = 480) + beat(t, at = 3000, ms = 480),
        followerShown = beat(t, at = 800, ms = 320),
        follower = beat(t, at = 1880, ms = 480) + beat(t, at = 3480, ms = 480),
        awake = 1f - STAYS_DIM * dim,
        shown = 1f - beat(t, at = 5700, ms = 400),
    )
}

/** Selecting: the whole line taken, its handles at either end, the number let go. */
internal const val SELECTION_STILL_AT = 4200

/**
 * Selecting, the way your phone does: a press settles on "Word" and the
 * phone's tint and handles take it ([selected]); the leading handle travels
 * back to "was" ([reach]); then a tap on the verse's number ([numberPress],
 * pressed and let go) takes the whole verse ([verse]).
 */
internal data class SelectionFrame(
    val wordPress: Float,
    val selected: Float,
    val reach: Float,
    val numberPress: Float,
    val verse: Float,
    val shown: Float,
)

internal fun selectionFrame(t: Int): SelectionFrame = SelectionFrame(
    // The finger leaves the word for the handle as the handle moves.
    wordPress = beat(t, at = 500, ms = 320) * (1f - beat(t, at = 1500, ms = 320)),
    selected = beat(t, at = 820, ms = 320),
    reach = beat(t, at = 1500, ms = 480),
    numberPress = beat(t, at = 2800, ms = 320) * (1f - beat(t, at = 3300, ms = 320)),
    verse = beat(t, at = 3020, ms = 480),
    shown = 1f - beat(t, at = 5600, ms = 400),
)

/**
 * In this room, however many: the words of the other reading that differ
 * from yours are washed forward ([wash]), and its readers' faces arrive one
 * by one, then "and others".
 */
internal data class RoomGroupsFrame(
    val wash: Float,
    val faces: List<Float>,
    val others: Float,
    val shown: Float,
)

// The faces first, then "and others", then the words that differ brought
// forward — the iPhone's order, and the spec's.
internal fun roomGroupsFrame(t: Int): RoomGroupsFrame = RoomGroupsFrame(
    faces = listOf(
        beat(t, at = 400, ms = 320),
        beat(t, at = 650, ms = 320),
        beat(t, at = 900, ms = 320),
    ),
    others = beat(t, at = 1300, ms = 320),
    wash = beat(t, at = 2000, ms = 480),
    shown = 1f - beat(t, at = 4100, ms = 400),
)

/** The New King James as printed: "Lord" becomes LORD in small capitals, in place ([name]). */
internal data class LordFrame(val name: Float)

internal fun lordFrame(t: Int): LordFrame =
    LordFrame(name = beat(t, at = 900, ms = 480) * (1f - beat(t, at = 3300, ms = 400)))

/** The Hebrew, easy to read: the word held, the line on its ground, the Hebrew large and full. */
internal const val READABLE_STILL_AT = 3600

/**
 * The Hebrew, easy to read (A66): a press settles on "majestic" and the
 * page's selection tint takes it ([wash]); the line for it arrives over the
 * line above as it used to, bare — muted, the Hebrew at its old size, the
 * verse running straight through it ([bare]) — and is left there long enough
 * to see the trouble. Then the toolbar's material comes under it ([ground])
 * as the Hebrew grows and comes up to full strength ([grow]), on one beat.
 */
internal data class ReadableFrame(
    val press: Float,
    val wash: Float,
    val bare: Float,
    val ground: Float,
    val grow: Float,
)

internal fun readableFrame(t: Int): ReadableFrame {
    // All of it settles back together, on one 400 ms beat — the iPhone's
    // way, every value going home at once.
    val back = 1f - beat(t, at = 4600, ms = 400)
    // Held bare for 0.9 s, then mended on one beat: the ground and the word
    // together, because that is what one change did.
    val mend = beat(t, at = 2300, ms = 480) * back
    return ReadableFrame(
        press = beat(t, at = 500, ms = 320) * back,
        wash = beat(t, at = 820, ms = 320) * back,
        bare = beat(t, at = 1000, ms = 400) * back,
        ground = mend,
        grow = mend,
    )
}

// The third release's six (A67, and A68's page).

/**
 * Your name in the front of the book: from the binding under Ruth's name her
 * rooms' ribbons are laid in, left to right, each grown down from the binding
 * with its room and its place coming with it ([ribbons]). Then they all fade
 * off together ([shown]) rather than lifting out one by one.
 */
internal data class FlyleafFrame(val ribbons: List<Float>, val shown: Float)

internal fun flyleafFrame(t: Int): FlyleafFrame = FlyleafFrame(
    // Each on the open curve, an arrival's length behind the one before.
    ribbons = listOf(
        beat(t, at = 400, ms = 480),
        beat(t, at = 720, ms = 480),
        beat(t, at = 1040, ms = 480),
    ),
    shown = 1f - beat(t, at = 3360, ms = 400),
)

/**
 * Every book you have finished, on one shelf: the embers rise onto it in
 * turn, a small book's, a middling one's and a long one's, each with its
 * words ([embers]); then the shelf fades as a whole ([shown]) rather than the
 * embers sinking back.
 */
internal data class ShelfFrame(val embers: List<Float>, val shown: Float)

internal fun shelfFrame(t: Int): ShelfFrame = ShelfFrame(
    embers = listOf(
        beat(t, at = 400, ms = 320),
        beat(t, at = 720, ms = 320),
        beat(t, at = 1040, ms = 320),
    ),
    shown = 1f - beat(t, at = 3200, ms = 400),
)

/**
 * Choose a version by reading it: how far the ribbon is laid into each row,
 * 1 for all the way. It lifts out of the first and is laid into the second
 * — one ribbon, so never in both at once — and after a while goes back. At
 * rest it is in the first, where the loop begins and ends.
 */
internal data class VersionsFrame(val first: Float, val second: Float)

internal fun versionsFrame(t: Int): VersionsFrame = VersionsFrame(
    // Lifted on settle, laid on open, as the row's own ribbon is.
    first = 1f - beat(t, at = 400, ms = 400) + beat(t, at = 3520, ms = 480),
    second = beat(t, at = 800, ms = 480) * (1f - beat(t, at = 3120, ms = 400)),
)

/**
 * Notifications say who: the switch turns on ([on]), then the sentence the
 * phone will say writes itself in ([written]); after a while the sentence
 * fades ([said]) as the switch turns off — faded, not unwritten.
 */
internal data class NotificationsFrame(val on: Float, val written: Float, val said: Float)

internal fun notificationsFrame(t: Int): NotificationsFrame {
    val off = beat(t, at = 3200, ms = 400)
    return NotificationsFrame(
        on = beat(t, at = 400, ms = 480) * (1f - off),
        written = beat(t, at = 880, ms = 480),
        said = 1f - off,
    )
}

/** Quiet hours: ten in the evening to six in the morning, drawn, its start at ten. */
internal const val NIGHT_STILL_AT = 4800

/**
 * Quiet hours, drawn as the night: the dark stretch draws itself out from
 * ten in the evening to six in the morning ([drawn]); its start is moved on
 * to eleven and the night follows, rests there, and is moved back ([later],
 * 1 at eleven); then the night fades ([shown]).
 *
 * The rest at eleven is the iPhone's; the rest after the start is back at
 * ten is this phone's own, so that reduce motion's frame — the night as it
 * was set — is inside a held pause as every other picture's is.
 */
internal data class NightFrame(val drawn: Float, val later: Float, val shown: Float)

internal fun nightFrame(t: Int): NightFrame = NightFrame(
    drawn = beat(t, at = 400, ms = 480),
    later = beat(t, at = 1280, ms = 480) - beat(t, at = 3400, ms = 480),
    shown = 1f - beat(t, at = 5560, ms = 400),
)

/** The page as the reader set it: a verse to a line, the numbers clear, the letters heavier. */
internal const val PAGE_STILL_AT = 3600

/**
 * The page, the way you read it (A68): John 1:1–3 set as one paragraph, as
 * the page has always set it; then set again a verse to a line ([lines], the
 * one setting giving way to the other where it lies — the words are set
 * again, they do not travel); then the numbers brighten ([clear]); then the
 * letters take more ink ([heavier]). Four beats, so the longer loop. After
 * the held pause all three go back on one beat, to the page it began as.
 */
internal data class PageFrame(val lines: Float, val clear: Float, val heavier: Float)

internal fun pageFrame(t: Int): PageFrame {
    val back = 1f - beat(t, at = 4560, ms = 480)
    return PageFrame(
        lines = beat(t, at = 400, ms = 480) * back,
        clear = beat(t, at = 1280, ms = 400) * back,
        heavier = beat(t, at = 2080, ms = 480) * back,
    )
}

/**
 * The loop's clock for one vignette, as state read only while drawing — so
 * the picture moves and nothing recomposes.
 *
 * An *infinite* frame loop, which is what it is: Compose's own tests know to
 * leave one of these alone rather than wait for it to finish.
 */
@Composable
private fun rememberLoopClock(still: Boolean, startAfter: Long, frozenAt: Long?, timeline: Timeline): LongState {
    // Starts where it will be held, so the first frame — and a clock that
    // never runs at all — is already right: at rest, or under reduce motion
    // at the end state.
    val time = remember(still, frozenAt, timeline) {
        mutableLongStateOf(frozenAt ?: if (still) timeline.stillAt.toLong() else 0L)
    }
    LaunchedEffect(still, startAfter, frozenAt, timeline) {
        if (still || frozenAt != null) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                time.longValue = loopTime((now - start) / 1_000_000L, startAfter, timeline.loopMs).toLong()
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

/**
 * The second release's Scripture, all of it in public-domain wording: John
 * 1:5 and 1:1 as the World English prints them, and Psalm 23:1 as every
 * English Bible since the King James has — the New King James's own words
 * are licensed and are not drawn here.
 */
private const val LIGHT_LINE = "The light shines in the darkness"
private val LIGHT_STEPS = listOf("The light", "shines in", "the darkness")
private const val LEADER = "Ruth"
private const val FOLLOWER = "You"
private const val BEGINNING_LINE = "In the beginning was the Word"
private const val BEGINNING_NUMBER = "1"
private const val BEGINNING_HELD = "Word"
private const val BEGINNING_REACH = "was the Word"
private const val SHEPHERD_LINE = "The Lord is my shepherd"
private const val NAME = "Lord"

/**
 * The fifth (A66): Exodus 15:11 as the Berean Standard sets it, two lines of
 * the song, and the line the page shows for its "majestic" — the Hebrew,
 * how to say it, and the version's own word.
 */
private const val SONG_UPPER = "Who among the gods is like You, O LORD?"
private const val SONG_LOWER = "Who is like You—majestic in holiness,"
private const val SONG_HELD = "majestic"
private const val SONG_HEBREW = "נֶאְדָּר"
private const val SONG_TRANSLIT = "ne’·dār"

/** The faces' inks in "In this room": the room's own, never the accent. */
private val FACE_INKS = listOf(Ink.teal, Ink.plum, Ink.clay)

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

/** A press: a fingertip's soft round, settling from the larger to the smaller. */
private val PRESS_RADIUS = 22.dp
private val PRESS_SETTLED_RADIUS = 15.dp

/** The selection's handles, drawn as the phone draws its own: a thin bar with a round end. */
private val HANDLE_BAR = 2.dp
private val HANDLE_KNOB = 4.5.dp

/**
 * The held word's line, as OriginalPanel sets it: the Hebrew at its old 17
 * and at its new 21 (`LINE_HEBREW_SIZE`), the words beside it at the
 * interface's 14, on a capsule of the toolbar's material that hugs it.
 */
private const val LINE_UI_SIZE = 14f
private const val HEBREW_WAS = 17f
private const val HEBREW_NOW = LINE_HEBREW_SIZE
private val LINE_PAD_X = 16.dp
private val LINE_PAD_Y = 5.dp

/**
 * The material, drawn as NoteComposer's `ribbonGlass` lays it down: the
 * raised surface, the unlit ground over it nearly opaque, and the hairline
 * edge catching a little more light along the top. Whatever verse is under
 * it is a trace, not a line to read.
 */
private const val GROUND_RAISED = 0.55f
private const val GROUND_TINT = 0.92f
private val GROUND_EDGE = 1.dp

/** Air between the ground's lower edge and the held word's tint under it. */
private val GROUND_CLEARS = 3.dp

/** A reader's face in "In this room", made small: a dot of their ink in a ring of the paper. */
private val FACE_DOT = 6.dp
private val FACE_RING = 1.5.dp
private val FACE_STEP = 9.dp

/**
 * One picture. Its type is set once per size, in the draw cache — measuring
 * a line of Literata sixty times a second to draw the same words would be
 * the expensive half of an animation that is otherwise a few rectangles —
 * and only the clock is read while drawing.
 */
@Composable
private fun Vignette(item: WhatsNewItem, startAfter: Long, frozenAt: Long?, modifier: Modifier) {
    val still = rememberReduceMotion()
    val clock = rememberLoopClock(still = still, startAfter = startAfter, frozenAt = frozenAt, timeline = item.timeline)
    val measurer = rememberTextMeasurer()
    val inks = VignetteInks(
        text = Palette.text,
        muted = Palette.muted,
        accent = Palette.accent,
        wash = Ink.ochre.color.copy(alpha = Palette.HIGHLIGHT_WASH),
        surface = Palette.surface,
        ground = Palette.ground,
        raised = Palette.raised,
        rule = Palette.rule,
        onAccent = Palette.onAccent,
        sheetsNeedEdges = LocalRoomColours.current.tileNeedsEdge,
    )
    val faces = remember {
        VignetteFaces(
            scripture = RibbonFonts.literata(FontWeight.Normal, 19f),
            medium = RibbonFonts.literata(FontWeight.Medium, 19f),
            italic = RibbonFonts.literata(FontWeight.Normal, 14f, italic = true),
            hebrew = RibbonFonts.hebrew,
            display = RibbonFonts.literata(FontWeight.Medium, FLYLEAF_NAME_SIZE),
            specimen = RibbonFonts.literata(FontWeight.Normal, SPECIMEN_SIZE),
            page = RibbonFonts.literata(FontWeight(PAGE_BOOK), PAGE_SIZE),
            pageHeavier = RibbonFonts.literata(FontWeight(PAGE_HEAVIER), PAGE_SIZE),
        )
    }
    val grain = rememberGrain()
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
        WhatsNewItem.followingStays -> Modifier.drawWithCache {
            val page = layOutTwoRows(
                measurer, inks, faces, smallCaps,
                RowText(LEADER, LIGHT_LINE),
                RowText(FOLLOWER, LIGHT_LINE),
            )
            onDrawBehind { drawStays(page, inks, staysFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.nativeSelection -> Modifier.drawWithCache {
            val page = layOutTheVerse(measurer, inks, faces)
            onDrawBehind { drawSelection(page, inks, selectionFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.roomGroups -> Modifier.drawWithCache {
            val page = layOutTheRoom(measurer, inks, faces, smallCaps)
            onDrawBehind { drawRoomGroups(page, inks, roomGroupsFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.lordReadsLord -> Modifier.drawWithCache {
            val page = layOutTheName(measurer, inks, faces)
            onDrawBehind { drawTheName(page, inks, lordFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.originalReadable -> Modifier.drawWithCache {
            val page = layOutTheHeldLine(measurer, inks, faces)
            onDrawBehind { drawTheHeldLine(page, inks, readableFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.flyleaf -> Modifier.drawWithCache {
            val page = layOutTheFlyleaf(measurer, inks, faces, smallCaps)
            onDrawBehind { drawFlyleaf(page, inks, flyleafFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.yourShelf -> Modifier.drawWithCache {
            val page = layOutTheShelf(measurer, inks, smallCaps)
            onDrawBehind {
                val t = clock.longValue.toInt()
                drawShelf(page, shelfFrame(t), t)
            }
        }
        WhatsNewItem.versionsByReading -> Modifier.drawWithCache {
            val page = layOutTheChoice(measurer, inks, faces, smallCaps)
            onDrawBehind { drawVersions(page, inks, grain, versionsFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.yourPage -> Modifier.drawWithCache {
            val page = layOutThePage(measurer, inks, faces, smallCaps)
            onDrawBehind { drawThePage(page, pageFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.typeface -> Modifier.drawWithCache {
            val page = layOutTheTypefaces(measurer, inks, smallCaps)
            onDrawBehind { drawTheTypefaces(page, inks, typefaceFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.notificationsByName -> Modifier.drawWithCache {
            val page = layOutTheSwitch(measurer, inks, ui)
            onDrawBehind { drawNotifications(page, inks, grain, notificationsFrame(clock.longValue.toInt())) }
        }
        WhatsNewItem.quietHoursNight -> Modifier.drawWithCache {
            val page = layOutTheNight(measurer, inks)
            onDrawBehind { drawNight(page, inks, grain, nightFrame(clock.longValue.toInt())) }
        }
    }
    Box(modifier.then(drawing))
}

/** The room's paints, read once in composition and handed to the drawing. */
private class VignetteInks(
    val text: Color,
    val muted: Color,
    val accent: Color,
    val wash: Color,
    val surface: Color,
    val ground: Color,
    val raised: Color,
    val rule: Color,
    val onAccent: Color,
    /** Whether a sheet drawn in a picture takes the rule as its edge, as a tile does (RoomColours.tileNeedsEdge). */
    val sheetsNeedEdges: Boolean,
)

private class VignetteFaces(
    val scripture: FontFamily,
    val medium: FontFamily,
    val italic: FontFamily,
    val hebrew: FontFamily,
    val display: FontFamily,
    val specimen: FontFamily,
    /** The small page's, at Book and at Heavier on Literata's own axis (A68). */
    val page: FontFamily,
    val pageHeavier: FontFamily,
)

/**
 * The paper's grain as a brush, for the sheets a picture draws for itself —
 * a row, a well, the night — which would otherwise be the one smooth thing
 * on a grained page. The same tile and the same repeat as `Modifier.grain`,
 * laid in the picture's own coordinates, so it lines up with the grain of
 * the paper under it rather than sliding against it.
 */
@Composable
private fun rememberGrain(): Brush {
    val context = LocalContext.current
    return remember(context) {
        val tile = context.assets.open(GRAIN_TILE).use { BitmapFactory.decodeStream(it).asImageBitmap() }
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
}

/** The marks every picture makes, in pixels: the wash, the reading line, the press. */
private class Pen(
    val bleedX: Float,
    val bleedY: Float,
    val corner: Float,
    val readingLine: Float,
    val readingLineGap: Float,
    val pressRadius: Float,
    val pressSettledRadius: Float,
)

private fun Density.pen() = Pen(
    bleedX = WASH_BLEED_X.toPx(),
    bleedY = WASH_BLEED_Y.toPx(),
    corner = WASH_CORNER.toPx(),
    readingLine = READING_LINE.toPx(),
    readingLineGap = 3.dp.toPx(),
    pressRadius = PRESS_RADIUS.toPx(),
    pressSettledRadius = PRESS_SETTLED_RADIUS.toPx(),
)

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

/**
 * The press: a soft round of the room's own ink settling to its size, as a
 * fingertip does on glass — larger and fainter first.
 */
private fun DrawScope.press(pen: Pen, at: Offset, ink: Color, amount: Float) {
    if (amount <= 0f) return
    val radius = lerp(pen.pressRadius, pen.pressSettledRadius, amount)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ink.copy(alpha = 0.2f * amount), Color.Transparent),
            center = at,
            radius = radius,
        ),
        radius = radius,
        center = at,
    )
}

/**
 * An ink wash from [left] to [right] on a line whose baseline is [baseline],
 * [drawn] of the way from its left edge: the pen travelling, as a mark you
 * make yourself is laid on the page, rather than faded up where it lies.
 * Hung off the baseline at the height of the letters, as the page hangs it.
 */
private fun DrawScope.wash(
    pen: Pen,
    left: Float,
    right: Float,
    baseline: Float,
    em: Float,
    ink: Color,
    drawn: Float,
    alpha: Float,
) {
    if (drawn <= 0f || alpha <= 0f) return
    val top = baseline - em * WASH_ABOVE_BASELINE - pen.bleedY
    val bottom = baseline + em * WASH_BELOW_BASELINE + pen.bleedY
    val l = left - pen.bleedX
    val r = right + pen.bleedX
    clipRect(left = l, top = top, right = lerp(l, r, drawn), bottom = bottom) {
        drawRoundRect(
            color = ink.copy(alpha = ink.alpha * alpha),
            topLeft = Offset(l, top),
            size = Size(r - l, bottom - top),
            cornerRadius = CornerRadius(pen.corner),
        )
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
    val original: TextLayoutResult,
    val originalAt: Offset,
    val rise: Float,
    val shadowY: Float,
    val shadowBlur: Float,
    val pressInk: Color,
    val pen: Pen,
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
        original = original,
        originalAt = originalAt,
        rise = LIFT_RISE.toPx(),
        shadowY = LIFT_SHADOW_Y.toPx(),
        shadowBlur = LIFT_SHADOW_BLUR.toPx(),
        pressInk = inks.text,
        pen = pen(),
    )
}

private fun DrawScope.drawTheWord(page: TheWord, frame: OriginalFrame) {
    press(page.pen, page.press, page.pressInk, frame.press)

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

// MARK: Two rows

/**
 * Two lines one above the other, each under its name in small caps. The
 * second and third pictures are John 1:3 as the Berean Standard prints it and
 * as the World English does — the same words at opposite ends of the two
 * lines, which is the whole of what both pictures are about. Following stays
 * is one line under two readers, and In this room is two blocks as the
 * section draws them.
 */
private class TwoVersions(
    val first: VersionRow,
    val second: VersionRow,
    val pen: Pen,
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

/** What one row says: its name, and its line — set plain unless it brings its own spans. */
private class RowText(val label: String, val text: String, val set: AnnotatedString = AnnotatedString(text))

private fun CacheDrawScope.layOutTwoVersions(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): TwoVersions = layOutTwoRows(
    measurer, inks, faces, smallCaps,
    RowText(TranslationID.bsb.displayName, BEREAN_LINE),
    RowText(TranslationID.web.displayName, WORLD_LINE),
)

private fun CacheDrawScope.layOutTwoRows(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
    first: RowText,
    second: RowText,
): TwoVersions {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 19.sp, color = inks.text)
    // Both lines at one size — whichever fits the narrower — so the picture
    // is of two versions and not of two type sizes.
    val fits = listOf(first, second).minOf {
        fitted(measurer, it.set, style, room).layoutInput.style.fontSize.value
    }
    val set = style.copy(fontSize = fits.sp)
    val label = smallCaps.copy(color = inks.muted)

    fun lay(row: RowText): Pair<TextLayoutResult, TextLayoutResult> =
        measurer.measure(AnnotatedString(row.label), label, softWrap = false, maxLines = 1, density = this) to
            measurer.measure(row.set, set, softWrap = false, maxLines = 1, density = this)

    val (firstLabel, firstLine) = lay(first)
    val (secondLabel, secondLine) = lay(second)

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
        first = row(firstLabel, firstLine, top, first.text),
        second = row(secondLabel, secondLine, secondTop, second.text),
        pen = pen(),
    )
}

private fun DrawScope.drawRow(row: VersionRow) {
    drawText(row.label, topLeft = row.labelAt)
    drawText(row.line, topLeft = row.lineAt)
}

private fun DrawScope.drawRows(page: TwoVersions) {
    drawRow(page.first)
    drawRow(page.second)
}

/** A wash along [part] of [row]. */
private fun DrawScope.wash(pen: Pen, row: VersionRow, part: String, ink: Color, drawn: Float, alpha: Float) {
    val stretch = row.stretch(part)
    wash(pen, stretch.left, stretch.right, row.baseline, row.em, ink, drawn, alpha)
}

private fun DrawScope.drawOwnVersion(page: TwoVersions, inks: VignetteInks, frame: OwnVersionFrame) {
    // Behind the glyphs, as the page lays a wash.
    wash(page.pen, page.first, BEREAN_WORDS, inks.wash, frame.first, frame.ink)
    wash(page.pen, page.second, WORLD_WORDS, inks.wash, frame.second, frame.ink)
    drawRows(page)
}

/** The reading line under a stretch of a row: the follow thread's paint, laid flat. */
private fun DrawScope.readingLine(pen: Pen, row: VersionRow, inks: VignetteInks, left: Float, right: Float, alpha: Float) {
    if (alpha <= 0f) return
    val y = row.baseline + row.em * WASH_BELOW_BASELINE + pen.readingLineGap
    drawLine(
        color = inks.accent.copy(alpha = READING_LINE_ALPHA * alpha),
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = pen.readingLine,
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
        page.pen, first, inks,
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
        page.pen, second, inks,
        left = centre - half,
        right = centre + half,
        alpha = frame.followerShown * frame.shown,
    )
}

// MARK: Following stays

/**
 * Where a reading line is when it is [step] of the way through [LIGHT_STEPS]
 * — 0 under the first group, 2 under the last, gliding between.
 */
private fun VersionRow.stepped(step: Float): Pair<Float, Float> {
    val last = LIGHT_STEPS.size - 1
    val clamped = step.coerceIn(0f, last.toFloat())
    val from = clamped.toInt().coerceAtMost(last - 1)
    val a = stretch(LIGHT_STEPS[from])
    val b = stretch(LIGHT_STEPS[from + 1])
    val f = clamped - from
    return lerp(a.left, b.left, f) to lerp(a.right, b.right, f)
}

private fun DrawScope.drawStays(page: TwoVersions, inks: VignetteInks, frame: StaysFrame) {
    // Ruth reads on, a few words at a time.
    val leader = page.first
    drawRow(leader)
    val (leaderLeft, leaderRight) = leader.stepped(frame.leader)
    readingLine(page.pen, leader, inks, leaderLeft, leaderRight, frame.leaderShown * frame.shown)

    // Your screen: following her a beat behind onto the same words, and
    // beginning to dim as a screen about to sleep does, then kept awake.
    val follower = page.second
    faded(frame.awake) {
        drawRow(follower)
        val (left, right) = follower.stepped(frame.follower)
        readingLine(page.pen, follower, inks, left, right, frame.followerShown * frame.shown)
    }
}

// MARK: Selecting

/**
 * John 1:1's first words with the verse's number before them, small and
 * muted as the page sets a number, and the geometry of the phone's own
 * selection over them: its tint, and a handle at either end.
 */
private class TheVerse(
    val line: TextLayoutResult,
    val lineAt: Offset,
    val text: String,
    val em: Float,
    val pen: Pen,
    val handleBar: Float,
    val handleKnob: Float,
) {
    val baseline: Float get() = lineAt.y + line.getLineBaseline(0)
    val top: Float get() = lineAt.y + line.getLineTop(0)
    val bottom: Float get() = lineAt.y + line.getLineBottom(0)
    fun stretch(part: String): Rect = line.stretch(text, part).translate(lineAt)
}

private fun CacheDrawScope.layOutTheVerse(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
): TheVerse {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 21.sp, color = inks.text)
    // The number as the page sets it (ChapterText): small caps at 0.62 em,
    // lifted, at 45%. A word space after it rather than the page's thin
    // one, so the handle that comes to rest between them has room.
    val number = RibbonType.smallCaps(11f).toSpanStyle().copy(
        fontSize = 0.62.em,
        color = inks.text.copy(alpha = 0.45f),
        baselineShift = BaselineShift(0.484f),
        letterSpacing = 0.sp,
    )
    val text = "$BEGINNING_NUMBER $BEGINNING_LINE"
    val set = buildAnnotatedString {
        withStyle(number) { append(BEGINNING_NUMBER) }
        append(" ")
        append(BEGINNING_LINE)
    }
    val line = fitted(measurer, set, style, room)
    val lineAt = Offset(
        x = (size.width - line.size.width) / 2f,
        y = size.height * 0.55f - line.size.height / 2f,
    )
    return TheVerse(
        line = line,
        lineAt = lineAt,
        text = text,
        em = line.layoutInput.style.fontSize.toPx(),
        pen = pen(),
        handleBar = HANDLE_BAR.toPx(),
        handleKnob = HANDLE_KNOB.toPx(),
    )
}

/** A selection handle: a bar the height of the line and a round end, above it or below. */
private fun DrawScope.handle(x: Float, top: Float, bottom: Float, knobAbove: Boolean, bar: Float, knob: Float, ink: Color) {
    drawLine(ink, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = bar, cap = StrokeCap.Butt)
    drawCircle(ink, radius = knob, center = Offset(x, if (knobAbove) top - knob * 0.6f else bottom + knob * 0.6f))
}

private fun DrawScope.drawSelection(page: TheVerse, inks: VignetteInks, frame: SelectionFrame) {
    val held = page.stretch(BEGINNING_HELD)
    val reach = page.stretch(BEGINNING_REACH)
    val verse = page.stretch(BEGINNING_LINE)
    val number = page.stretch(BEGINNING_NUMBER)

    press(page.pen, Offset(held.center.x, page.baseline - page.em * 0.38f), inks.text, frame.wordPress)
    press(page.pen, Offset(number.center.x, page.baseline - page.em * 0.52f), inks.text, frame.numberPress)

    // The phone's own selection, in the accent the page tints it with:
    // behind the glyphs, the whole height of the line. The drag carries its
    // edge back along the line under the handle; the tap on the number does
    // not drag anything — the rest of the verse is simply taken, so it comes
    // up where it lies and the handle is there, not travelling.
    val shown = frame.selected * frame.shown
    val dragged = lerp(held.left, reach.left, frame.reach)
    val right = held.right
    fun tint(from: Float, to: Float, alpha: Float) {
        if (alpha <= 0f || to <= from) return
        drawRect(
            color = inks.accent.copy(alpha = SELECTION_TINT * alpha),
            topLeft = Offset(from, page.top),
            size = Size(to - from, page.bottom - page.top),
        )
    }
    tint(dragged, right, shown)
    tint(verse.left, dragged, shown * frame.verse)

    drawText(page.line, topLeft = page.lineAt)

    if (shown > 0f) {
        fun leading(x: Float, alpha: Float) {
            if (alpha <= 0f) return
            val ink = inks.accent.copy(alpha = alpha)
            handle(x, page.top, page.bottom, knobAbove = true, bar = page.handleBar, knob = page.handleKnob, ink = ink)
        }
        leading(dragged, shown * (1f - frame.verse))
        leading(verse.left, shown * frame.verse)
        val ink = inks.accent.copy(alpha = shown)
        handle(right, page.top, page.bottom, knobAbove = false, bar = page.handleBar, knob = page.handleKnob, ink = ink)
    }
}

// MARK: In this room

/**
 * Two blocks as "In this room" draws them: yours first, under "Yours ·
 * Berean Standard", and the World English's with the words yours does not
 * use at full strength and Medium and the words you share a step back.
 * Beside its name, its readers.
 */
private class TheRoom(
    val page: TwoVersions,
    val others: TextLayoutResult,
    val othersAt: Offset,
    val faces: List<Offset>,
    val dot: Float,
    val ring: Float,
)

private fun CacheDrawScope.layOutTheRoom(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): TheRoom {
    val differs = WORLD_LINE.indexOf(WORLD_WORDS)
    val theirs = buildAnnotatedString {
        withStyle(SpanStyle(color = inks.text.copy(alpha = 0.7f))) { append(WORLD_LINE.substring(0, differs)) }
        withStyle(SpanStyle(fontFamily = faces.medium, fontWeight = FontWeight.Medium)) {
            append(WORLD_LINE.substring(differs))
        }
    }
    val page = layOutTwoRows(
        measurer, inks, faces, smallCaps,
        RowText(Copy.roomYours(listOf(TranslationID.bsb.displayName)), BEREAN_LINE),
        RowText(TranslationID.web.displayName, WORLD_LINE, theirs),
    )
    val label = page.second
    val dot = FACE_DOT.toPx()
    val step = FACE_STEP.toPx()
    val centreY = label.labelAt.y + label.label.size.height / 2f
    val firstX = label.labelAt.x + label.label.size.width + 12.dp.toPx() + dot
    val faceAt = FACE_INKS.indices.map { Offset(firstX + step * it, centreY) }
    val others = measurer.measure(
        AnnotatedString(Copy.ROOM_AND_OTHERS),
        smallCaps.copy(color = inks.muted),
        softWrap = false,
        maxLines = 1,
        density = this,
    )
    val othersAt = Offset(
        x = faceAt.last().x + dot + 8.dp.toPx(),
        y = centreY - others.size.height / 2f,
    )
    return TheRoom(
        page = page,
        others = others,
        othersAt = othersAt,
        faces = faceAt,
        dot = dot,
        ring = FACE_RING.toPx(),
    )
}

private fun DrawScope.drawRoomGroups(room: TheRoom, inks: VignetteInks, frame: RoomGroupsFrame) {
    val page = room.page
    wash(page.pen, page.second, WORLD_WORDS, inks.wash, frame.wash, frame.shown)
    drawRows(page)

    // The faces overlap, first on top, each cut from the one under it by a
    // ring of the paper — as the section's are, made small.
    for (i in room.faces.indices.reversed()) {
        val alpha = frame.faces[i] * frame.shown
        if (alpha <= 0f) continue
        drawCircle(inks.surface.copy(alpha = alpha), radius = room.dot + room.ring, center = room.faces[i])
        drawCircle(FACE_INKS[i].color.copy(alpha = alpha), radius = room.dot, center = room.faces[i])
    }
    faded(frame.others * frame.shown) { drawText(room.others, topLeft = room.othersAt) }
}

// MARK: The name

/**
 * Psalm 23:1, with room left in the line for the name at its printed width,
 * and the name set twice to cross between: "Lord" as a plain line reads it,
 * and LORD as the edition prints it — a capital and Literata's own small
 * capitals, not shrunken ones.
 */
private class TheName(
    val line: TextLayoutResult,
    val lineAt: Offset,
    val plain: TextLayoutResult,
    val plainAt: Offset,
    val printed: TextLayoutResult,
    val printedAt: Offset,
    val left: Float,
    val right: Float,
    val baseline: Float,
    val em: Float,
    val pen: Pen,
)

private fun CacheDrawScope.layOutTheName(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
): TheName {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 21.sp, color = inks.text)
    val smallCapitals = SpanStyle(fontFeatureSettings = "smcp")
    fun AnnotatedString.Builder.printedName() {
        append(NAME.take(1))
        withStyle(smallCapitals) { append(NAME.drop(1)) }
    }

    val at = SHEPHERD_LINE.indexOf(NAME)
    val sentence = buildAnnotatedString {
        append(SHEPHERD_LINE.substring(0, at))
        withStyle(SpanStyle(color = Color.Transparent)) { printedName() }
        append(SHEPHERD_LINE.substring(at + NAME.length))
    }
    val line = fitted(measurer, sentence, style, room)
    val lineAt = Offset(
        x = (size.width - line.size.width) / 2f,
        y = size.height * 0.55f - line.size.height / 2f,
    )
    val baseline = lineAt.y + line.getLineBaseline(0)
    val stretch = line.stretch(SHEPHERD_LINE, NAME).translate(lineAt)
    val set = line.layoutInput.style

    fun lay(text: AnnotatedString) = measurer.measure(text, set, softWrap = false, maxLines = 1, density = this)
    val printed = lay(buildAnnotatedString { printedName() })
    val plain = lay(AnnotatedString(NAME))

    return TheName(
        line = line,
        lineAt = lineAt,
        plain = plain,
        plainAt = Offset(stretch.center.x - plain.size.width / 2f, baseline - plain.getLineBaseline(0)),
        printed = printed,
        printedAt = Offset(stretch.left, baseline - printed.getLineBaseline(0)),
        left = stretch.left,
        right = stretch.right,
        baseline = baseline,
        em = set.fontSize.toPx(),
        pen = pen(),
    )
}

private fun DrawScope.drawTheName(page: TheName, inks: VignetteInks, frame: LordFrame) {
    // The soft wash comes up under the name as it changes — laid where it
    // lies rather than drawn along, because nobody is marking it.
    wash(page.pen, page.left, page.right, page.baseline, page.em, inks.wash, drawn = 1f, alpha = frame.name)
    drawText(page.line, topLeft = page.lineAt)
    faded(1f - frame.name) { drawText(page.plain, topLeft = page.plainAt) }
    faded(frame.name) { drawText(page.printed, topLeft = page.printedAt) }
}

// MARK: The held word's line

/**
 * Two lines of Exodus 15:11 at the page's size, "majestic" held in the
 * lower, and the line for it over the middle of the upper — where the
 * reading screen draws it, over whatever verse lies above the toolbar.
 *
 * The Hebrew is set once, at its new size, and drawn smaller while the line
 * is still the old one; the words after it are set once and move along as
 * it grows, so the line stays centred and nothing is measured while the
 * picture runs. The ground is the grown line's, so the line fills it as it
 * grows rather than the ground growing round it.
 */
private class TheHeldLine(
    val upper: TextLayoutResult,
    val upperAt: Offset,
    val lower: TextLayoutResult,
    val lowerAt: Offset,
    /** The held word's selection: its stretch of the lower line, at the height of the letters. */
    val held: Rect,
    val press: Offset,
    val hebrew: TextLayoutResult,
    val rest: TextLayoutResult,
    /** The line's own baseline, the Hebrew's and the words' after it. */
    val baseline: Float,
    val centreX: Float,
    /** The Hebrew's old size, as a share of its new. */
    val was: Float,
    val ground: Rect,
    val edge: Float,
    val pen: Pen,
)

private fun CacheDrawScope.layOutTheHeldLine(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
): TheHeldLine {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val style = TextStyle(fontFamily = faces.scripture, fontSize = 19.sp, color = inks.text)
    // Both lines at one size — whichever fits the longer — as the page sets
    // a song: one size of type, line under line.
    val fits = listOf(SONG_UPPER, SONG_LOWER).minOf {
        fitted(measurer, AnnotatedString(it), style, room).layoutInput.style.fontSize.value
    }
    val set = style.copy(fontSize = fits.sp)
    fun lay(text: String) =
        measurer.measure(AnnotatedString(text), set, softWrap = false, maxLines = 1, density = this)
    val upper = lay(SONG_UPPER)
    val lower = lay(SONG_LOWER)
    val em = set.fontSize.toPx()
    val pen = pen()

    // The line, as OriginalPanel sets it — the Hebrew in its own face, how
    // to say it in Literata's italic, the version's word and the dots in the
    // interface's — smaller all together if its ground would not fit.
    val padX = LINE_PAD_X.toPx()
    fun setLine(scale: Float): Pair<TextLayoutResult, TextLayoutResult> {
        val hebrew = measurer.measure(
            AnnotatedString(SONG_HEBREW),
            TextStyle(fontFamily = faces.hebrew, fontSize = (HEBREW_NOW * scale).sp, color = inks.text),
            softWrap = false,
            maxLines = 1,
            density = this,
        )
        val dot = "  ·  "
        val rest = measurer.measure(
            buildAnnotatedString {
                append(dot)
                withStyle(SpanStyle(fontFamily = faces.italic, fontStyle = FontStyle.Italic)) { append(SONG_TRANSLIT) }
                append(dot)
                append(SONG_HELD)
            },
            RibbonType.ui(LINE_UI_SIZE * scale).copy(
                color = inks.muted,
                lineHeight = TextUnit.Unspecified,
                textDirection = TextDirection.Ltr,
            ),
            softWrap = false,
            maxLines = 1,
            density = this,
        )
        return hebrew to rest
    }
    var (hebrew, rest) = setLine(1f)
    val natural = hebrew.size.width + rest.size.width + padX * 2
    if (natural > room) {
        val scale = (room - padX * 2) / (hebrew.size.width + rest.size.width) * 0.98f
        setLine(scale).let { (h, r) -> hebrew = h; rest = r }
    }

    // Vertically, from the upper line's baseline: the ground centred on the
    // middle of its letters, and the lower line far enough under it that the
    // held word's selection is clear of the ground — at the page's own
    // leading wherever that is already so.
    val ascent = maxOf(hebrew.getLineBaseline(0), rest.getLineBaseline(0))
    val descent = maxOf(
        hebrew.size.height - hebrew.getLineBaseline(0),
        rest.size.height - rest.getLineBaseline(0),
    )
    val groundHeight = ascent + descent + LINE_PAD_Y.toPx() * 2
    val middle = -em * 0.36f
    val groundTop = middle - groundHeight / 2f
    val groundBottom = middle + groundHeight / 2f
    val heldAbove = em * WASH_ABOVE_BASELINE + pen.bleedY
    val heldBelow = em * WASH_BELOW_BASELINE + pen.bleedY
    val lead = maxOf(em * 1.62f, groundBottom + GROUND_CLEARS.toPx() + heldAbove)
    val blockTop = minOf(groundTop, -heldAbove)
    val blockBottom = lead + heldBelow
    val upperBaseline = (size.height - (blockBottom - blockTop)) / 2f - blockTop
    val lowerBaseline = upperBaseline + lead

    // The two lines as one block, centred; the line over the upper's middle.
    val x = (size.width - maxOf(upper.size.width, lower.size.width)) / 2f
    val upperAt = Offset(x, upperBaseline - upper.getLineBaseline(0))
    val lowerAt = Offset(x, lowerBaseline - lower.getLineBaseline(0))
    val centreX = upperAt.x + upper.size.width / 2f
    val word = lower.stretch(SONG_LOWER, SONG_HELD).translate(lowerAt)
    val groundWidth = hebrew.size.width + rest.size.width + padX * 2

    return TheHeldLine(
        upper = upper,
        upperAt = upperAt,
        lower = lower,
        lowerAt = lowerAt,
        held = Rect(word.left, lowerBaseline - heldAbove, word.right, lowerBaseline + heldBelow),
        press = Offset(word.center.x, lowerBaseline - em * 0.38f),
        hebrew = hebrew,
        rest = rest,
        baseline = upperBaseline + groundTop + LINE_PAD_Y.toPx() + ascent,
        centreX = centreX,
        was = HEBREW_WAS / HEBREW_NOW,
        ground = Rect(
            left = centreX - groundWidth / 2f,
            top = upperBaseline + groundTop,
            right = centreX + groundWidth / 2f,
            bottom = upperBaseline + groundBottom,
        ),
        edge = GROUND_EDGE.toPx(),
        pen = pen,
    )
}

private fun DrawScope.drawTheHeldLine(page: TheHeldLine, inks: VignetteInks, frame: ReadableFrame) {
    press(page.pen, page.press, inks.text, frame.press)

    // The page's own selection on the held word, behind its glyphs.
    val held = frame.wash
    if (held > 0f) {
        drawRect(
            color = inks.accent.copy(alpha = SELECTION_TINT * held),
            topLeft = page.held.topLeft,
            size = page.held.size,
        )
    }
    drawText(page.upper, topLeft = page.upperAt)
    drawText(page.lower, topLeft = page.lowerAt)

    // The ground comes under the line, over the verse it used to sit bare
    // on: the toolbar's material — its two layers fading in as one — and
    // its hairline edge.
    val ground = frame.ground
    if (ground > 0f) {
        val g = page.ground
        faded(ground) {
            for (layer in listOf(inks.raised.copy(alpha = GROUND_RAISED), inks.ground.copy(alpha = GROUND_TINT))) {
                drawRoundRect(color = layer, topLeft = g.topLeft, size = g.size, cornerRadius = CornerRadius(g.height / 2f))
            }
        }
        val e = page.edge
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(inks.text.copy(alpha = 0.14f), inks.rule),
                startY = g.top,
                endY = g.bottom,
            ),
            topLeft = g.topLeft + Offset(e / 2f, e / 2f),
            size = Size(g.width - e, g.height - e),
            cornerRadius = CornerRadius((g.height - e) / 2f),
            alpha = ground,
            style = Stroke(width = e),
        )
    }

    // The line: bare and muted at first, the Hebrew at its old size; then
    // the Hebrew grows and comes up to the page's full ink, and the words
    // after it make room.
    faded(frame.bare) {
        val k = lerp(page.was, 1f, frame.grow)
        val hebrewWidth = page.hebrew.size.width * k
        val left = page.centreX - (hebrewWidth + page.rest.size.width) / 2f
        scale(k, pivot = Offset(left, page.baseline)) {
            drawText(
                page.hebrew,
                color = lerpColor(inks.muted, inks.text, frame.grow),
                topLeft = Offset(left, page.baseline - page.hebrew.getLineBaseline(0)),
            )
        }
        drawText(page.rest, topLeft = Offset(left + hebrewWidth, page.baseline - page.rest.getLineBaseline(0)))
    }
}

// MARK: - The third release's pictures (A67)
//
// These five are of things that are not the page — You, a shelf, a row with
// a ribbon in it, a switch, the night's band — so they are drawn from what
// those are drawn from: the ribbon's own tail (`drawRibbonTail`), the ember's
// own hand (`drawEmber`), the room's paper and well and grain. Rows are
// raised off the picture's paper as a chip is, since the picture is itself a
// sheet of paper and a row in the paper's own colour would be no row at all.

/** The other person in every picture, as in the second release's. */
private const val READER = "Ruth"

/** The grain's tile and its strength, as `Modifier.grain` lays it. */
private const val GRAIN_TILE = "paper_grain.png"
private const val GRAIN_ALPHA = 0.035f

/** A sheet's edge inside a picture, where the fill alone cannot carry it: the tile's own hairline. */
private val SHEET_EDGE = 1.dp

/**
 * A room's ribbon on the flyleaf: its ink — none for a room where no ink is
 * yours, which hangs in the accent as You hangs it — how far it hangs, and
 * the room and the place it lies.
 */
private class PicturedRoom(val ink: Ink?, val length: Dp, val room: String, val place: String)

/**
 * Three rooms, no two ribbons the same length — "two ribbons of slightly
 * different length read as two people" (brief §5).
 */
private val FLYLEAF = listOf(
    PicturedRoom(null, 34.dp, "us", "mark 4"),
    PicturedRoom(Ink.teal, 28.dp, "thursday", "ruth 2"),
    PicturedRoom(Ink.plum, 38.dp, "family", "john 1"),
)
private val FLYLEAF_FACE = 30.dp
private const val FLYLEAF_NAME_SIZE = 24f
private val FLYLEAF_RIBBON = 12.dp

/** A finished book on the shelf: the size its fire was, its name, and who it was read with. */
private class PicturedBook(val scale: FireScale, val book: String, val company: String)

/** Philemon small, Mark medium, Isaiah large — each the size its fire was, so Isaiah still looks like Isaiah. */
private val SHELF = listOf(
    PicturedBook(FireScale.small, "philemon", "with jo"),
    PicturedBook(FireScale.medium, "mark", "with ruth"),
    PicturedBook(FireScale.large, "isaiah", "with thursday study"),
)

/** The embers at a little over half the size You sets them, in the same proportion to each other. */
private const val EMBER_SHRINK = 0.6

/** The ember breathes at a quarter of the fire's clock, as `EmberView`'s does. */
private const val EMBER_PACE = 0.25
private val EMBER_RISE = 6.dp
private val SHELF_GAP = 18.dp

/**
 * A version's own words under its name, smaller than the page's: a specimen
 * as the Text screen sets one, small enough that the longer line fits a row.
 */
private const val SPECIMEN_SIZE = 14f

/** Where words start inside a pictured row, and how far in from its trailing edge the ribbon hangs. */
private val ROW_INSET = 14.dp

/** The chosen marker at the picture's size: `ChoiceRibbon`'s 10 × 22, a little shorter. */
private val CHOICE_RIBBON_WIDTH = 10.dp
private val CHOICE_RIBBON_LENGTH = 20.dp

/** Two rows as a group sets them, made small: round where they stand alone, small at the seam. */
private val ROW_CORNER = RibbonShape.row
private val SEAM_CORNER = 6.dp

/** Where Ruth's note was left, in the sentence the phone will say. */
private const val NOTED_AT = "Mark 4:12"
private val NOTE_FACE = 14.dp

/**
 * The row's switch at the picture's size: Material's 52 × 32, which the row
 * keeps (A18/A29), made 40 × 24, its thumb small and muted when off and
 * grown and on the accent when on, as Material's is.
 */
private val SWITCH_WIDTH = 40.dp
private val SWITCH_HEIGHT = 24.dp
private val THUMB_OFF = 12.dp
private val THUMB_ON = 18.dp
private val SWITCH_EDGE = 1.5.dp

/** How far behind the pen a letter takes to come up whole, so a line is written in rather than wiped on. */
private val NIB = 18.dp

/**
 * The picture's night, ten in the evening to six in the morning, and the
 * later start it is moved to — placed on the band from noon to noon by the
 * core's arithmetic, as the setting's band is.
 */
private val NIGHT_FROM = QuietHoursBand.position(22 * 60).toFloat()
private val LATER_FROM = QuietHoursBand.position(23 * 60).toFloat()
private val NIGHT_TO = QuietHoursBand.position(6 * 60).toFloat()

/**
 * The hours under the band, in the order `QuietHoursBand.marks` sets them:
 * words in a picture rather than this phone's clock, so both phones' pictures
 * say the same.
 */
private val NIGHT_MARKS = listOf("6 pm", "midnight", "6 am")

/** The setting's band at the picture's size: 44 high made 28, its corner and its handles in proportion. */
private val NIGHT_BAND = 28.dp
private val NIGHT_CORNER = 8.dp
private val NIGHT_HANDLE = 4.dp
private val NIGHT_HANDLE_HEIGHT = 18.dp
private val NIGHT_TICK = 4.dp

/** A face with no photograph, as `PortraitView` sets one: its initial, in the person's ink. */
private fun monogram(size: Dp): TextStyle = RibbonType.ui(size.value * 0.42f, FontWeight.Medium)

private fun rounded(rect: Rect, corner: Float): Path =
    Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }

/**
 * A sheet drawn inside a picture: its fill, the paper's grain on it, and —
 * where the room's tiles take one — its hairline edge, inside the shape as a
 * tile's border is.
 */
private fun DrawScope.sheet(shape: Path, fill: Color, grain: Brush, edge: Color?, edgeWidth: Float) {
    drawPath(shape, fill)
    drawPath(shape, grain, alpha = GRAIN_ALPHA)
    if (edge != null) {
        // Stroked twice as wide and cut to the shape, so the edge is all
        // inside it.
        clipPath(shape) { drawPath(shape, edge, style = Stroke(edgeWidth * 2f)) }
    }
}

/** A ribbon [laid] of the way into whatever it hangs from: grown down from its top edge, as one is laid into a book. */
private fun DrawScope.laidRibbon(color: Color, at: Offset, width: Float, length: Float, laid: Float) {
    if (laid <= 0f) return
    scale(scaleX = 1f, scaleY = laid, pivot = at) {
        drawRibbonTail(color, at, width, length)
    }
}

// MARK: Your name in the front of the book

/**
 * The top of You as it opens now: Ruth's face and her name in the display
 * face, the binding under them, and from it three rooms' ribbons, each at the
 * left of its third with the room and where it lies beside it.
 */
private class TheFlyleaf(
    val face: Offset,
    val faceRadius: Float,
    val initial: TextLayoutResult,
    val initialAt: Offset,
    val name: TextLayoutResult,
    val nameAt: Offset,
    val binding: Rect,
    val ribbons: List<PicturedRibbon>,
)

private class PicturedRibbon(
    val color: Color,
    val at: Offset,
    val width: Float,
    val length: Float,
    val room: TextLayoutResult,
    val roomAt: Offset,
    val place: TextLayoutResult,
    val placeAt: Offset,
)

private fun CacheDrawScope.layOutTheFlyleaf(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): TheFlyleaf {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val face = FLYLEAF_FACE.toPx()
    val beside = 10.dp.toPx()

    val initial = measurer.measure(
        AnnotatedString(READER.take(1)),
        monogram(FLYLEAF_FACE).copy(color = Ink.teal.color),
        softWrap = false,
        maxLines = 1,
        density = this,
    )
    val name = fitted(
        measurer,
        AnnotatedString(READER),
        TextStyle(fontFamily = faces.display, fontSize = FLYLEAF_NAME_SIZE.sp, color = inks.text),
        (room - face - beside).coerceAtLeast(1f),
    )

    // Beside each ribbon its room, a step back from the page's text, and
    // under that where it lies, muted — as You sets them under its own.
    val ribbonWidth = FLYLEAF_RIBBON.toPx()
    val third = room / FLYLEAF.size
    val wordsGap = 8.dp.toPx()
    val wordsRoom = (third - ribbonWidth - wordsGap * 1.5f).coerceAtLeast(1f)
    val roomStyle = smallCaps.copy(color = inks.text.copy(alpha = 0.8f))
    val placeStyle = smallCaps.copy(color = inks.muted)
    val words = FLYLEAF.map {
        fitted(measurer, AnnotatedString(it.room), roomStyle, wordsRoom) to
            fitted(measurer, AnnotatedString(it.place), placeStyle, wordsRoom)
    }
    val wordsDrop = 4.dp.toPx()
    val lineGap = 2.dp.toPx()
    val hanging = maxOf(
        FLYLEAF.maxOf { it.length.toPx() },
        words.maxOf { (roomLine, placeLine) -> wordsDrop + roomLine.size.height + lineGap + placeLine.size.height },
    )

    val head = maxOf(face, name.size.height.toFloat())
    val below = 12.dp.toPx()
    val hair = 1.dp.toPx()
    val top = ((size.height - (head + below + hair + hanging)) / 2f).coerceAtLeast(0f)
    val faceAt = Offset(inset + face / 2f, top + head / 2f)
    val bindingTop = top + head + below
    val ribbonTop = bindingTop + hair

    return TheFlyleaf(
        face = faceAt,
        faceRadius = face / 2f,
        initial = initial,
        initialAt = faceAt - Offset(initial.size.width / 2f, initial.size.height / 2f),
        name = name,
        nameAt = Offset(inset + face + beside, faceAt.y - name.size.height / 2f),
        binding = Rect(inset, bindingTop, size.width - inset, ribbonTop),
        ribbons = FLYLEAF.mapIndexed { i, pictured ->
            val x = inset + third * i
            val (roomLine, placeLine) = words[i]
            val wordsX = x + ribbonWidth + wordsGap
            PicturedRibbon(
                color = pictured.ink?.color ?: inks.accent,
                at = Offset(x, ribbonTop),
                width = ribbonWidth,
                length = pictured.length.toPx(),
                room = roomLine,
                roomAt = Offset(wordsX, ribbonTop + wordsDrop),
                place = placeLine,
                placeAt = Offset(wordsX, ribbonTop + wordsDrop + roomLine.size.height + lineGap),
            )
        },
    )
}

private fun DrawScope.drawFlyleaf(page: TheFlyleaf, inks: VignetteInks, frame: FlyleafFrame) {
    drawCircle(inks.raised, radius = page.faceRadius, center = page.face)
    drawText(page.initial, topLeft = page.initialAt)
    drawText(page.name, topLeft = page.nameAt)
    drawRect(inks.rule, topLeft = page.binding.topLeft, size = page.binding.size)

    page.ribbons.forEachIndexed { i, ribbon ->
        val laid = frame.ribbons[i]
        laidRibbon(ribbon.color.copy(alpha = ribbon.color.alpha * frame.shown), ribbon.at, ribbon.width, ribbon.length, laid)
        // The words come with their ribbon.
        faded(laid * frame.shown) {
            drawText(ribbon.room, topLeft = ribbon.roomAt)
            drawText(ribbon.place, topLeft = ribbon.placeAt)
        }
    }
}

// MARK: Every book you have finished, on one shelf

/**
 * Three embers standing on one line — no shelf drawn (S10), the line is
 * where they stand — each over its book and who it was read with.
 */
private class TheShelf(val books: List<ShelvedEmber>, val rise: Float)

private class ShelvedEmber(
    val ember: Rect,
    val seed: Double,
    val book: TextLayoutResult,
    val bookAt: Offset,
    val company: TextLayoutResult,
    val companyAt: Offset,
)

private fun CacheDrawScope.layOutTheShelf(
    measurer: TextMeasurer,
    inks: VignetteInks,
    smallCaps: TextStyle,
): TheShelf {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    val gap = SHELF_GAP.toPx()
    val column = ((room - gap * (SHELF.size - 1)) / SHELF.size).coerceAtLeast(1f)
    val bookStyle = RibbonType.smallCaps(12f).copy(color = inks.text.copy(alpha = 0.8f), textAlign = TextAlign.Center)
    val companyStyle = smallCaps.copy(color = inks.muted, textAlign = TextAlign.Center)
    // Up to two lines under an ember, centred, as the shelf sets a long
    // company line — never cut.
    fun lay(text: String, style: TextStyle) = measurer.measure(
        AnnotatedString(text),
        style,
        maxLines = 2,
        constraints = Constraints(maxWidth = column.toInt()),
        density = this,
    )

    // `EmberView`'s own proportions: the size the fire was, a third of it,
    // in a box half as wide again as it is tall.
    val embers = SHELF.map {
        val across = (it.scale.frameHeight * 0.30 * EMBER_SHRINK).dp.toPx()
        Size(across * 1.7f, across * 1.35f)
    }
    val books = SHELF.map { lay(it.book, bookStyle) }
    val companies = SHELF.map { lay(it.company, companyStyle) }
    val widths = SHELF.indices.map {
        maxOf(embers[it].width, books[it].size.width.toFloat(), companies[it].size.width.toFloat())
    }

    val tallest = embers.maxOf { it.height }
    val under = 6.dp.toPx()
    val lineGap = 2.dp.toPx()
    val words = SHELF.indices.maxOf { (books[it].size.height + companies[it].size.height).toFloat() } + lineGap
    val top = ((size.height - (tallest + under + words)) / 2f).coerceAtLeast(0f)
    val standing = top + tallest

    var x = (size.width - (widths.sum() + gap * (SHELF.size - 1))) / 2f
    val shelved = SHELF.indices.map { i ->
        val centre = x + widths[i] / 2f
        x += widths[i] + gap
        val ember = embers[i]
        val book = books[i]
        val company = companies[i]
        val bookAt = Offset(centre - book.size.width / 2f, standing + under)
        ShelvedEmber(
            ember = Rect(Offset(centre - ember.width / 2f, standing - ember.height), ember),
            seed = 0.4 + i * 1.7,
            book = book,
            bookAt = bookAt,
            company = company,
            companyAt = Offset(centre - company.size.width / 2f, bookAt.y + book.size.height + lineGap),
        )
    }
    return TheShelf(books = shelved, rise = EMBER_RISE.toPx())
}

/**
 * The shelf at [frame], its embers breathing at [t] — each at its own time,
 * on the picture's clock, so that a held picture holds them too. The clock
 * wraps only while the shelf has faded, so a breath is never seen to jump.
 */
private fun DrawScope.drawShelf(page: TheShelf, frame: ShelfFrame, t: Int) {
    val time = t / 1000.0 * EMBER_PACE
    page.books.forEachIndexed { i, shelved ->
        val risen = frame.embers[i]
        faded(risen * frame.shown) {
            translate(top = page.rise * (1f - risen)) {
                val ember = shelved.ember
                inset(
                    left = ember.left,
                    top = ember.top,
                    right = size.width - ember.right,
                    bottom = size.height - ember.bottom,
                ) {
                    drawEmber(time + shelved.seed)
                }
                drawText(shelved.book, topLeft = shelved.bookAt)
                drawText(shelved.company, topLeft = shelved.companyAt)
            }
        }
    }
}

// MARK: Choose a version by reading it

/**
 * Two versions as the Text screen now sets them: two rows of a group, each a
 * name over its own words for the verse, and the ribbon that marks yours
 * hanging from a row's top edge at its trailing side, where `SettingChoice`
 * hangs it.
 */
private class TheChoice(val rows: List<PicturedChoice>, val ribbonWidth: Float, val ribbonLength: Float, val edge: Float)

private class PicturedChoice(
    val shape: Path,
    val label: TextLayoutResult,
    val labelAt: Offset,
    val line: TextLayoutResult,
    val lineAt: Offset,
    val ribbonAt: Offset,
)

private fun CacheDrawScope.layOutTheChoice(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): TheChoice {
    val inset = INSET.toPx()
    val left = inset
    val right = size.width - inset
    val pad = ROW_INSET.toPx()
    val ribbonWidth = CHOICE_RIBBON_WIDTH.toPx()
    // The words stop short of the ribbon's room on both rows, as the row
    // keeps it clear whether or not the ribbon is in it.
    val room = (right - left - pad * 2 - ribbonWidth - 8.dp.toPx()).coerceAtLeast(1f)
    val specimen = TextStyle(
        fontFamily = faces.specimen,
        fontSize = SPECIMEN_SIZE.sp,
        color = inks.text.copy(alpha = 0.86f),
    )
    val versions = listOf(
        TranslationID.bsb.displayName to BEREAN_LINE,
        TranslationID.web.displayName to WORLD_LINE,
    )
    // Both at one size, whichever fits the narrower, as the two-row
    // pictures set theirs.
    val fits = versions.minOf { (_, line) ->
        fitted(measurer, AnnotatedString(line), specimen, room).layoutInput.style.fontSize.value
    }
    val set = specimen.copy(fontSize = fits.sp)
    val label = smallCaps.copy(color = inks.muted)
    val laid = versions.map { (name, line) ->
        measurer.measure(AnnotatedString(name), label, softWrap = false, maxLines = 1, density = this) to
            measurer.measure(AnnotatedString(line), set, softWrap = false, maxLines = 1, density = this)
    }

    val gap = 2.dp.toPx()
    val padY = 7.dp.toPx()
    val seam = Seam.toPx()
    val heights = laid.map { (name, line) -> padY * 2 + name.size.height + gap + line.size.height }
    var top = ((size.height - heights.sum() - seam * (laid.size - 1)) / 2f).coerceAtLeast(0f)
    val outer = CornerRadius(ROW_CORNER.toPx())
    val inner = CornerRadius(SEAM_CORNER.toPx())
    val rows = laid.mapIndexed { i, (name, line) ->
        val upper = if (i == 0) outer else inner
        val lower = if (i == laid.size - 1) outer else inner
        val bottom = top + heights[i]
        val row = PicturedChoice(
            shape = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        topLeftCornerRadius = upper,
                        topRightCornerRadius = upper,
                        bottomRightCornerRadius = lower,
                        bottomLeftCornerRadius = lower,
                    ),
                )
            },
            label = name,
            labelAt = Offset(left + pad, top + padY),
            line = line,
            lineAt = Offset(left + pad, top + padY + name.size.height + gap),
            ribbonAt = Offset(right - pad - ribbonWidth, top),
        )
        top = bottom + seam
        row
    }
    return TheChoice(
        rows = rows,
        ribbonWidth = ribbonWidth,
        ribbonLength = CHOICE_RIBBON_LENGTH.toPx(),
        edge = SHEET_EDGE.toPx(),
    )
}

private fun DrawScope.drawVersions(page: TheChoice, inks: VignetteInks, grain: Brush, frame: VersionsFrame) {
    val edge = if (inks.sheetsNeedEdges) inks.rule else null
    val laid = listOf(frame.first, frame.second)
    page.rows.forEachIndexed { i, row ->
        sheet(row.shape, inks.raised, grain, edge, page.edge)
        drawText(row.label, topLeft = row.labelAt)
        drawText(row.line, topLeft = row.lineAt)
        // Inside the row's own corners, as the row's ribbon is inside its tile.
        clipPath(row.shape) {
            laidRibbon(inks.accent, row.ribbonAt, page.ribbonWidth, page.ribbonLength, laid[i])
        }
    }
}

// MARK: Notifications say who

/**
 * The first switch of a room of two: "Notes left for you", and under it, in
 * a small well, Ruth's face and the sentence the phone will say — the
 * example as the row draws it (`SettingExampleView`) — and the switch at the
 * row's trailing end.
 */
private class TheSwitch(
    val shape: Path,
    val title: TextLayoutResult,
    val titleAt: Offset,
    val well: Path,
    val face: Offset,
    val faceRadius: Float,
    val initial: TextLayoutResult,
    val initialAt: Offset,
    val sentence: TextLayoutResult,
    val sentenceAt: Offset,
    val track: Rect,
    val thumbOff: Float,
    val thumbOn: Float,
    val trackEdge: Float,
    val edge: Float,
    val nib: Float,
)

private fun CacheDrawScope.layOutTheSwitch(
    measurer: TextMeasurer,
    inks: VignetteInks,
    ui: TextStyle,
): TheSwitch {
    val inset = INSET.toPx()
    val left = inset
    val right = size.width - inset
    val padX = ROW_INSET.toPx()
    val padY = 12.dp.toPx()
    val trackWidth = SWITCH_WIDTH.toPx()
    val trackHeight = SWITCH_HEIGHT.toPx()
    val wordsLeft = left + padX
    val wordsRoom = (right - padX - trackWidth - 10.dp.toPx() - wordsLeft).coerceAtLeast(1f)

    val title = fitted(measurer, AnnotatedString(Copy.NOTES_LEFT_FOR_YOU), ui.copy(color = inks.text), wordsRoom)

    val wellX = 8.dp.toPx()
    val wellY = 6.dp.toPx()
    val face = NOTE_FACE.toPx()
    val faceGap = 6.dp.toPx()
    val sentence = fitted(
        measurer,
        AnnotatedString(Copy.notifNoteLeft(READER, NOTED_AT)),
        RibbonType.ui(13f).copy(color = inks.text.copy(alpha = 0.8f)),
        (wordsRoom - wellX * 2 - face - faceGap).coerceAtLeast(1f),
    )
    val wellHeight = wellY * 2 + maxOf(face, sentence.size.height.toFloat())
    val wellWidth = wellX * 2 + face + faceGap + sentence.size.width

    val under = 8.dp.toPx()
    val height = padY * 2 + title.size.height + under + wellHeight
    val top = ((size.height - height) / 2f).coerceAtLeast(0f)
    val wellTop = top + padY + title.size.height + under
    val faceAt = Offset(wordsLeft + wellX + face / 2f, wellTop + wellHeight / 2f)
    val initial = measurer.measure(
        AnnotatedString(READER.take(1)),
        monogram(NOTE_FACE).copy(color = Ink.teal.color),
        softWrap = false,
        maxLines = 1,
        density = this,
    )
    val trackTop = top + (height - trackHeight) / 2f

    return TheSwitch(
        shape = rounded(Rect(left, top, right, top + height), RibbonShape.row.toPx()),
        title = title,
        titleAt = Offset(wordsLeft, top + padY),
        well = rounded(Rect(wordsLeft, wellTop, wordsLeft + wellWidth, wellTop + wellHeight), RibbonShape.small.toPx()),
        face = faceAt,
        faceRadius = face / 2f,
        initial = initial,
        initialAt = faceAt - Offset(initial.size.width / 2f, initial.size.height / 2f),
        sentence = sentence,
        sentenceAt = Offset(faceAt.x + face / 2f + faceGap, wellTop + (wellHeight - sentence.size.height) / 2f),
        track = Rect(right - padX - trackWidth, trackTop, right - padX, trackTop + trackHeight),
        thumbOff = THUMB_OFF.toPx(),
        thumbOn = THUMB_ON.toPx(),
        trackEdge = SWITCH_EDGE.toPx(),
        edge = SHEET_EDGE.toPx(),
        nib = NIB.toPx(),
    )
}

private fun DrawScope.drawNotifications(page: TheSwitch, inks: VignetteInks, grain: Brush, frame: NotificationsFrame) {
    val edge = if (inks.sheetsNeedEdges) inks.rule else null
    sheet(page.shape, inks.raised, grain, edge, page.edge)
    drawText(page.title, topLeft = page.titleAt)

    // The well and the face are there throughout; only the words come and go.
    sheet(page.well, inks.ground, grain, edge, page.edge)
    drawCircle(inks.raised, radius = page.faceRadius, center = page.face)
    drawText(page.initial, topLeft = page.initialAt)
    writtenIn(page.sentence, page.sentenceAt, written = frame.written, nib = page.nib, alpha = frame.said)

    drawSwitch(page, inks, frame.on)
}

/**
 * The row's switch, drawn as Material draws it in Ribbon's paint (A18/A29):
 * off, a recess with the rule round it and a small muted thumb at the
 * leading end; on, the accent filling it and the thumb grown, on the accent's
 * own contrast, at the trailing end. One value, [on], so the whole of it
 * moves on the picture's beat.
 */
private fun DrawScope.drawSwitch(page: TheSwitch, inks: VignetteInks, on: Float) {
    val track = page.track
    val round = track.height / 2f
    val edge = page.trackEdge
    drawRoundRect(inks.ground, topLeft = track.topLeft, size = track.size, cornerRadius = CornerRadius(round))
    drawRoundRect(
        inks.rule,
        topLeft = track.topLeft + Offset(edge / 2f, edge / 2f),
        size = Size(track.width - edge, track.height - edge),
        cornerRadius = CornerRadius(round - edge / 2f),
        style = Stroke(edge),
    )
    if (on > 0f) {
        drawRoundRect(
            inks.accent.copy(alpha = inks.accent.alpha * on),
            topLeft = track.topLeft,
            size = track.size,
            cornerRadius = CornerRadius(round),
        )
    }
    drawCircle(
        lerpColor(inks.muted, inks.onAccent, on),
        radius = lerp(page.thumbOff, page.thumbOn, on) / 2f,
        center = Offset(lerp(track.left + round, track.right - round, on), track.center.y),
    )
}

/**
 * A line written in as a pen writes it: left to right, each letter coming
 * up out of nothing over the [nib]'s width behind the pen, rather than a
 * hard edge wiping across the words. [written] is how far along, 0 to 1;
 * the pen runs on past the last letter by the nib, so that it too is whole
 * when the pen stops.
 */
private fun DrawScope.writtenIn(line: TextLayoutResult, at: Offset, written: Float, nib: Float, alpha: Float) {
    if (written <= 0f || alpha <= 0f) return
    if (written >= 1f) {
        faded(alpha) { drawText(line, topLeft = at) }
        return
    }
    val bounds = Rect(at, Size(line.size.width.toFloat(), line.size.height.toFloat()))
    val pen = at.x + (bounds.width + nib) * written
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
        drawText(line, topLeft = at)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = pen - nib, endX = pen),
            topLeft = bounds.topLeft,
            size = bounds.size,
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
}

// MARK: Quiet hours, drawn as the night

/**
 * The quiet hours' band, smaller (`QuietHoursBandControl`): noon to noon,
 * the waking hours raised, the night banked into the ground with its grain,
 * a rule round the whole, a handle at each end of the night, and the three
 * hours a person already knows a night by under it.
 */
private class TheNight(
    val band: Rect,
    val shape: Path,
    val hours: List<PicturedHour>,
    val handle: Size,
    val edge: Float,
)

private class PicturedHour(val tick: Rect, val hour: TextLayoutResult, val hourAt: Offset)

private fun CacheDrawScope.layOutTheNight(measurer: TextMeasurer, inks: VignetteInks): TheNight {
    val inset = INSET.toPx()
    val height = NIGHT_BAND.toPx()
    val tick = NIGHT_TICK.toPx()
    val tickWidth = 1.dp.toPx()
    val gap = 3.dp.toPx()
    val style = RibbonType.smallCaps(9f).copy(color = inks.muted)
    val hours = NIGHT_MARKS.map {
        measurer.measure(AnnotatedString(it), style, softWrap = false, maxLines = 1, density = this)
    }
    val total = height + tick + gap + hours.maxOf { it.size.height }
    val top = ((size.height - total) / 2f).coerceAtLeast(0f)
    val band = Rect(inset, top, size.width - inset, top + height)
    return TheNight(
        band = band,
        shape = rounded(band, NIGHT_CORNER.toPx()),
        hours = QuietHoursBand.marks.mapIndexed { i, minute ->
            val x = band.left + QuietHoursBand.position(minute).toFloat() * band.width
            val hour = hours[i]
            PicturedHour(
                tick = Rect(x - tickWidth / 2f, band.bottom, x + tickWidth / 2f, band.bottom + tick),
                hour = hour,
                hourAt = Offset(
                    x = (x - hour.size.width / 2f)
                        .coerceIn(band.left, (band.right - hour.size.width).coerceAtLeast(band.left)),
                    y = band.bottom + tick + gap,
                ),
            )
        },
        handle = Size(NIGHT_HANDLE.toPx(), NIGHT_HANDLE_HEIGHT.toPx()),
        edge = SHEET_EDGE.toPx(),
    )
}

private fun DrawScope.drawNight(page: TheNight, inks: VignetteInks, grain: Brush, frame: NightFrame) {
    val band = page.band
    fun along(position: Float) = band.left + position * band.width

    drawPath(page.shape, inks.raised)

    // The night draws out from its start, and follows the start when it is
    // moved; the handles come with the night they hold, rather than
    // standing on an empty band.
    val from = lerp(NIGHT_FROM, LATER_FROM, frame.later)
    val reach = lerp(from, NIGHT_TO, frame.drawn)
    if (frame.shown > 0f && reach > from) {
        clipPath(page.shape) {
            val topLeft = Offset(along(from), band.top)
            val stretch = Size(along(reach) - along(from), band.height)
            drawRect(inks.ground.copy(alpha = inks.ground.alpha * frame.shown), topLeft = topLeft, size = stretch)
            drawRect(grain, topLeft = topLeft, size = stretch, alpha = GRAIN_ALPHA * frame.shown)
        }
    }
    clipPath(page.shape) { drawPath(page.shape, inks.rule, style = Stroke(page.edge * 2f)) }

    val held = frame.drawn * frame.shown
    if (held > 0f) {
        for (end in listOf(from, reach)) {
            drawRoundRect(
                inks.text.copy(alpha = inks.text.alpha * held),
                topLeft = Offset(along(end) - page.handle.width / 2f, band.center.y - page.handle.height / 2f),
                size = page.handle,
                cornerRadius = CornerRadius(page.handle.width / 2f),
            )
        }
    }

    for (hour in page.hours) {
        drawRect(inks.rule, topLeft = hour.tick.topLeft, size = hour.tick.size)
        drawText(hour.hour, topLeft = hour.hourAt)
    }
}

/** The typefaces: the ribbon on Literata, where it begins and ends. */
internal const val TYPEFACE_STILL_AT = 400

/**
 * Set in the type you read best (A69): John 1:1's first words in each of
 * the page's five typefaces, and a short ribbon marking the chosen one. It
 * moves down a row every 1200 ms ([row]: 0 on Literata, 4 on Atkinson
 * Hyperlegible), the iPhone's beat, and comes back up to Literata on one.
 */
internal data class TypefaceFrame(val row: Float)

internal fun typefaceFrame(t: Int): TypefaceFrame = TypefaceFrame(
    row = beat(t, at = 1200, ms = 400) + beat(t, at = 2400, ms = 400) +
        beat(t, at = 3600, ms = 400) + beat(t, at = 4800, ms = 400) -
        (PageFaces.all.size - 1) * beat(t, at = 5600, ms = 400),
)

// MARK: - Set in the type you read best (A69)
//
// The page's five typefaces, each from its own bundled file at the size that
// looks like Literata's and the weight that matches its colour — what the
// page itself is set from — with the face's name after it in the page's
// small caps. The chosen row is ivory and the others quieter, by how near the
// ribbon is, so the ribbon moving is what changes.

/** Literata's size for the picture; every other face at the size that looks like it. */
private const val TYPEFACE_SIZE = 12f
private const val TYPEFACE_LINE = "In the beginning was the Word"
private val TYPEFACE_ROW = 24.dp
private val TYPEFACE_RIBBON = DpSize(3.dp, 14.dp)

private class TypefaceRow(val line: TextLayoutResult, val name: TextLayoutResult, val at: Offset, val nameAt: Offset)
private class TheTypefaces(val rows: List<TypefaceRow>, val ribbonX: Float, val top: Float, val rowHeight: Float)

private fun CacheDrawScope.layOutTheTypefaces(
    measurer: TextMeasurer,
    inks: VignetteInks,
    smallCaps: TextStyle,
): TheTypefaces {
    val inset = INSET.toPx()
    val faces = PageFaces.all
    val rowHeight = minOf(TYPEFACE_ROW.toPx(), (size.height - inset) / faces.size)
    val ribbonRoom = 14.dp.toPx()
    val gap = 10.dp.toPx()
    fun lay(scale: Float) = faces.map { face ->
        val pointSize = PageType.pointSize(TYPEFACE_SIZE.toDouble(), face).toFloat() * scale
        val weight = FontWeight(PageType.faceWeight(400, face))
        val style = TextStyle(
            fontFamily = RibbonFonts.page(face, weight, pointSize),
            fontWeight = weight,
            fontSize = pointSize.sp,
            color = inks.text,
        )
        val name = smallCaps.copy(fontSize = (9f * scale).sp, color = inks.muted)
        measurer.measure(TYPEFACE_LINE, style, softWrap = false, maxLines = 1, density = this) to
            measurer.measure(face.name, name, softWrap = false, maxLines = 1, density = this)
    }
    val room = size.width - inset * 2 - ribbonRoom
    var laid = lay(1f)
    val widest = laid.maxOf { (line, name) -> line.size.width + gap + name.size.width }
    if (widest > room && widest > 0f) laid = lay(room / widest * 0.98f)
    val top = (size.height - rowHeight * faces.size) / 2f
    val left = inset + ribbonRoom
    val rows = laid.mapIndexed { i, (line, name) ->
        val lineY = top + rowHeight * i + (rowHeight - line.size.height) / 2f
        val nameY = lineY + line.getLineBaseline(0) - name.getLineBaseline(0)
        TypefaceRow(line, name, Offset(left, lineY), Offset(left + line.size.width + gap, nameY))
    }
    return TheTypefaces(rows, ribbonX = inset, top = top, rowHeight = rowHeight)
}

private fun DrawScope.drawTheTypefaces(page: TheTypefaces, inks: VignetteInks, frame: TypefaceFrame) {
    page.rows.forEachIndexed { i, row ->
        val near = (1f - kotlin.math.abs(frame.row - i)).coerceIn(0f, 1f)
        drawText(row.line, topLeft = row.at, alpha = 0.45f + 0.55f * near)
        drawText(row.name, topLeft = row.nameAt)
    }
    val ribbon = TYPEFACE_RIBBON.toSize()
    drawRect(
        color = inks.accent,
        topLeft = Offset(page.ribbonX, page.top + page.rowHeight * frame.row + (page.rowHeight - ribbon.height) / 2f),
        size = ribbon,
    )
}

// MARK: - The page, the way you read it (A68)
//
// The one picture in its release that is of the page itself, so it is drawn
// from what the page is drawn from: Literata at Book and at Heavier on its
// own weight axis, the numbers in the page's small caps, raised, a thin space
// after each, at the page's quiet ink and its clearer one. Each line is set
// on its own and broken where it is written here, at one size for all of
// them, so the picture breaks alike on every phone and the heavier letters
// never carry a word onto another line.

/**
 * The small page's size: John 1:1–3 in five lines as a paragraph and six a
 * verse to a line, at the width of a phone's picture; set a little smaller
 * where six lines would not fit the paper (`layOutThePage`).
 */
private const val PAGE_SIZE = 13f

/**
 * Its leading, as a multiple of the size: a little closer than the page's
 * own Book (S02's 1.72), so the lines keep the paper's margin round them.
 */
private const val PAGE_LEADING = 1.55f

/** Book and Heavier, from the core's table: the page's own weights, not a picture's. */
private val PAGE_BOOK = PageType.weights[PageType.defaultWeightStep]
private val PAGE_HEAVIER = PageType.weights.last()

/** A run of a pictured line: words, or a verse's number. */
private class PageRun(val text: String, val number: Boolean = false)

private fun words(text: String) = PageRun(text)

/** A number as the page sets one: its digits and a thin space, never a word space. */
private fun number(verse: Int) = PageRun("$verse\u2009", number = true)

/**
 * John 1:1–3 in the Berean Standard, as one paragraph: the page as it has
 * always been set, the first verse unnumbered as a chapter's first is.
 */
private val AS_ONE_PARAGRAPH = listOf(
    listOf(words("In the beginning was the Word, and the Word")),
    listOf(words("was with God, and the Word was God. "), number(2), words("He was")),
    listOf(words("with God in the beginning. "), number(3), words("Through Him all")),
    listOf(words("things were made, and without Him nothing")),
    listOf(words("was made that has been made.")),
)

/**
 * The same three verses, a verse to a line; a verse longer than a line runs
 * on to the next. Broken where the iPhone's picture breaks them
 * (WhatsNewScreen.swift's `verseByVerse`), so both phones show one page.
 */
private val VERSE_BY_VERSE = listOf(
    listOf(words("In the beginning was the Word, and the Word")),
    listOf(words("was with God, and the Word was God.")),
    listOf(number(2), words("He was with God in the beginning.")),
    listOf(number(3), words("Through Him all things were made, and")),
    listOf(words("without Him nothing was made that has")),
    listOf(words("been made.")),
)

/**
 * One line of the small page, set twice over: its words with the numbers
 * left as space, and its numbers with the words left as space — so the
 * numbers' ink can change without the words', and both lie exactly where the
 * one line would.
 */
private class SetLine(val words: TextLayoutResult, val numbers: TextLayoutResult?, val at: Offset)

/** The small page three ways: one paragraph, a verse to a line, and that again heavier. */
private class ThePage(
    val asOneParagraph: List<SetLine>,
    val verseByVerse: List<SetLine>,
    val heavier: List<SetLine>,
)

private fun CacheDrawScope.layOutThePage(
    measurer: TextMeasurer,
    inks: VignetteInks,
    faces: VignetteFaces,
    smallCaps: TextStyle,
): ThePage {
    val inset = INSET.toPx()
    val room = size.width - inset * 2
    // The number as the page sets it (ChapterText): small caps at 0.62 em,
    // lifted, with no tracking — the ink is laid on when it is drawn.
    val numberStyle = smallCaps.toSpanStyle().copy(
        fontSize = 0.62.em,
        color = inks.text,
        baselineShift = BaselineShift(0.484f),
        letterSpacing = 0.sp,
    )
    val unseen = SpanStyle(color = Color.Transparent)

    fun styleOf(family: FontFamily, weight: Int, size: TextUnit) =
        TextStyle(fontFamily = family, fontWeight = FontWeight(weight), fontSize = size, color = inks.text)

    fun lay(line: List<PageRun>, style: TextStyle, numbers: Boolean): TextLayoutResult {
        val set = buildAnnotatedString {
            for (run in line) {
                if (run.number) {
                    withStyle(numberStyle) { withStyle(if (numbers) SpanStyle() else unseen) { append(run.text) } }
                } else {
                    withStyle(if (numbers) unseen else SpanStyle()) { append(run.text) }
                }
            }
        }
        return measurer.measure(set, style, softWrap = false, maxLines = 1, density = this)
    }

    // One size for every line of every setting: the picture's, or smaller if
    // the widest of them — heavier letters are a little wider — would not
    // fit the paper across, or the most lines of them — a verse to a line
    // takes one more than the paragraph — would not fit it down.
    val book = styleOf(faces.page, PAGE_BOOK, PAGE_SIZE.sp)
    val heavy = styleOf(faces.pageHeavier, PAGE_HEAVIER, PAGE_SIZE.sp)
    val widest = (AS_ONE_PARAGRAPH.map { lay(it, book, false) } +
        VERSE_BY_VERSE.map { lay(it, book, false) } +
        VERSE_BY_VERSE.map { lay(it, heavy, false) }).maxOf { it.size.width }.toFloat()
    val lines = maxOf(AS_ONE_PARAGRAPH.size, VERSE_BY_VERSE.size)
    val tallest = PAGE_SIZE.sp.toPx() * PAGE_LEADING * lines
    val across = if (widest <= room || widest <= 0f) 1f else room / widest * 0.98f
    // Down, half the inset above and below: a line's leading already carries
    // air of its own, which the width's measure does not.
    val down = (size.height - inset).let { if (tallest <= it || tallest <= 0f) 1f else it / tallest }
    val scale = minOf(across, down)
    val fontSize = (PAGE_SIZE * scale).sp
    val bookStyle = book.copy(fontSize = fontSize)
    val heavyStyle = heavy.copy(fontSize = fontSize)

    // Every line on one leading, the longer setting's block centred on the
    // paper and its lines flush left, as the page's are; the paragraph
    // starts on the same first line, so changing the setting moves nothing
    // above the first line that changes. Every line stands on its own
    // baseline, so a raised number never lifts the line it opens.
    val advance = fontSize.toPx() * PAGE_LEADING
    val left = (size.width - widest * scale) / 2f
    val top = (size.height - advance * lines) / 2f
    val plain = lay(AS_ONE_PARAGRAPH.first(), bookStyle, false)
    val firstBaseline = top + (advance - plain.size.height) / 2f + plain.getLineBaseline(0)

    fun setAll(lines: List<List<PageRun>>, style: TextStyle, numbered: Boolean) = lines.mapIndexed { i, line ->
        val words = lay(line, style, false)
        val numbers = if (numbered && line.any { it.number }) lay(line, style, true) else null
        SetLine(words, numbers, Offset(left, firstBaseline + advance * i - words.getLineBaseline(0)))
    }
    return ThePage(
        asOneParagraph = setAll(AS_ONE_PARAGRAPH, bookStyle, numbered = true),
        verseByVerse = setAll(VERSE_BY_VERSE, bookStyle, numbered = true),
        // The numbers are small caps, which take no weight from the page,
        // and open their lines: the heavier setting has only its words.
        heavier = setAll(VERSE_BY_VERSE, heavyStyle, numbered = false),
    )
}

/**
 * [from] giving way to [to], [amount] of the way, the two added together in
 * a layer of their own: where the same letters lie in both they stay whole
 * through the middle instead of dimming as a cross-fade's do, so only what
 * changes is seen to change — a line set again, a letter thickening.
 */
private inline fun DrawScope.dissolve(amount: Float, from: DrawScope.() -> Unit, to: DrawScope.() -> Unit) {
    when {
        amount <= 0f -> from()
        amount >= 1f -> to()
        else -> drawIntoCanvas { canvas ->
            val whole = Rect(Offset.Zero, size)
            canvas.saveLayer(whole, Paint())
            canvas.saveLayer(whole, Paint().apply { alpha = 1f - amount })
            from()
            canvas.restore()
            canvas.saveLayer(whole, Paint().apply { alpha = amount; blendMode = BlendMode.Plus })
            to()
            canvas.restore()
            canvas.restore()
        }
    }
}

private fun DrawScope.drawWords(lines: List<SetLine>) {
    for (line in lines) drawText(line.words, topLeft = line.at)
}

private fun DrawScope.drawNumbers(lines: List<SetLine>, alpha: Float) {
    faded(alpha) {
        for (line in lines) line.numbers?.let { drawText(it, topLeft = line.at) }
    }
}

private fun DrawScope.drawThePage(page: ThePage, frame: PageFrame) {
    val quiet = PageType.quietVerseNumberAlpha.toFloat()
    val clear = PageType.clearVerseNumberAlpha.toFloat()
    dissolve(
        frame.lines,
        from = {
            drawWords(page.asOneParagraph)
            drawNumbers(page.asOneParagraph, quiet)
        },
        to = {
            dissolve(frame.heavier, from = { drawWords(page.verseByVerse) }, to = { drawWords(page.heavier) })
            drawNumbers(page.verseByVerse, lerp(quiet, clear, frame.clear))
        },
    )
}
