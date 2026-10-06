package app.readribbon.screens

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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.readribbon.app.Copy
import app.readribbon.core.Ink
import app.readribbon.core.TranslationID
import app.readribbon.core.WhatsNew
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
 * while it is on the screen: read again, eleven pictures and counting would
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
                releases.forEachIndexed { r, release ->
                    item(key = "heading:${release.id}") {
                        val first = r == 0
                        if (first) {
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
                                .padding(top = if (first) 44.dp else BETWEEN_RELEASES)
                                .then(if (first) Modifier.focusRequester(headingFocus).focusable() else Modifier),
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
 * A picture with more to say — three steps and a screen dimming, or a press,
 * a drag and a second press — takes a longer loop rather than a shorter
 * breath: its movements are on the same 320–480 ms beats and the pause after
 * them is the same long one (A65).
 */
internal const val LONG_LOOP_MS = 6200

/** How long one picture's loop is, and the moment reduce motion holds it at. */
internal data class Timeline(val loopMs: Int, val stillAt: Int)

/** Each picture's loop: the first release's three, and the short new ones, on the one clock. */
internal val WhatsNewItem.timeline: Timeline
    get() = when (this) {
        WhatsNewItem.original,
        WhatsNewItem.ownVersion,
        WhatsNewItem.followingWords,
        WhatsNewItem.roomGroups,
        WhatsNewItem.lordReadsLord,
        -> Timeline(LOOP_MS, STILL_AT)
        WhatsNewItem.followingStays -> Timeline(LONG_LOOP_MS, STAYS_STILL_AT)
        WhatsNewItem.nativeSelection -> Timeline(LONG_LOOP_MS, SELECTION_STILL_AT)
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

internal fun roomGroupsFrame(t: Int): RoomGroupsFrame = RoomGroupsFrame(
    wash = beat(t, at = 400, ms = 480),
    faces = listOf(
        beat(t, at = 1000, ms = 320),
        beat(t, at = 1250, ms = 320),
        beat(t, at = 1500, ms = 320),
    ),
    others = beat(t, at = 1900, ms = 320),
    shown = 1f - beat(t, at = 4060, ms = 400),
)

/** The New King James as printed: "Lord" becomes LORD in small capitals, in place ([name]). */
internal data class LordFrame(val name: Float)

internal fun lordFrame(t: Int): LordFrame =
    LordFrame(name = beat(t, at = 900, ms = 480) * (1f - beat(t, at = 3300, ms = 400)))

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
    )
    val faces = remember {
        VignetteFaces(
            scripture = RibbonFonts.literata(FontWeight.Normal, 19f),
            medium = RibbonFonts.literata(FontWeight.Medium, 19f),
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
)

private class VignetteFaces(val scripture: FontFamily, val medium: FontFamily, val italic: FontFamily)

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
