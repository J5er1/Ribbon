package app.readribbon.reading

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.core.ReadingPoint
import kotlin.math.abs

// Following (§4.2), the half of it that belongs to this page: where a
// reading line falls on a chapter set here, where somebody else's line would
// fall, and what the reader's own hands are doing while a follow carries the
// page. The guess itself — where the person followed is reading between the
// words their phone sends — is core's (`Following.kt`), and so is the rule
// for when the page moves.

/**
 * The point at a line through a chapter: the verse whose first line is the
 * last one at or above it, and how far the line is from that first line to
 * the next one down.
 *
 * Measured from the page as it is laid out, never from where the page was
 * sent: this is what somebody following sees of you. Verses that begin on
 * the same line (a short verse, a genealogy) are the last of them, so the
 * next line down is never the same line and the fraction always has
 * something to divide by; a gap under a pixel counts as none, and a line
 * above the chapter's first verse — its running head — is that verse's
 * start.
 *
 * @param line how far down the chapter the line is.
 * @param onePixel a pixel, in the same units.
 */
internal fun ChapterLayout.pointAt(chapter: Int, line: Dp, onePixel: Dp): ReadingPoint {
    var verse: Int? = null
    var top = 0.dp
    var first: Int? = null
    var firstTop = 0.dp
    for ((v, y) in verseFirstLineY) {
        if (first == null || y < firstTop || (y == firstTop && v < first)) {
            first = v
            firstTop = y
        }
        if (y > line) continue
        val current = verse
        if (current == null || y > top || (y == top && v > current)) {
            verse = v
            top = y
        }
    }
    val at = verse ?: return ReadingPoint(chapter = chapter, verse = first ?: 1)
    val gap = lineAfter(top) - top
    val part = if (gap < onePixel) 0.0 else ((line - top) / gap).toDouble()
    return ReadingPoint(chapter = chapter, verse = at, part = heldInside(part))
}

/**
 * How far down this chapter a point is: the first line of its verse, and
 * the same fraction of the way to the next line down. A verse this version
 * leaves out is where the one before it ends — the same place the core's
 * ruler measures it at. Null for a chapter with no lines yet.
 */
internal fun ChapterLayout.heightOf(point: ReadingPoint): Dp? {
    if (verseFirstLineY.isEmpty()) return null
    val own = verseFirstLineY[point.verse]
    if (own == null) {
        val before = verseFirstLineY.filterKeys { it < point.verse }.maxByOrNull { it.key }?.value
            ?: return verseFirstLineY.values.min()
        return lineAfter(before)
    }
    val gap = (lineAfter(own) - own).coerceAtLeast(0.dp)
    return own + gap * heldInside(point.part).toFloat()
}

/** The chapter's last verse on the page — the one a line past its end is in. */
internal val ChapterLayout.lastVerse: Int?
    get() = verseFirstLineY.entries
        .maxWithOrNull(compareBy<Map.Entry<Int, Dp>>({ it.value }, { it.key }))
        ?.key

/** The next first line strictly below [top], or the chapter's own foot. */
private fun ChapterLayout.lineAfter(top: Dp): Dp =
    verseFirstLineY.values.filter { it > top }.minOrNull() ?: height

/** A fraction of a verse: inside 0…1, and a number. */
private fun heldInside(part: Double): Double = if (part.isNaN()) 0.0 else part.coerceIn(0.0, 1.0)

/**
 * How much of a finger's movement the page takes, [stretched] into the
 * rubber band a follow meets on the first scroll of your own (§4.2): all of
 * it at first, less and less the further it is pulled, never none. The same
 * curve the platform gives the edge of a list.
 */
internal fun bandGive(stretched: Float, viewport: Float): Float {
    if (viewport <= 0f) return 1f
    val reach = 1f + abs(stretched) / (0.55f * viewport)
    return 1f / (reach * reach)
}

/**
 * What the reader's own hands are doing, for the follow to keep out of their
 * way — read by the follow on its own clock, and by the list's scroll
 * connection, which is not in a composition at all. Only [fingerDown] is
 * state, so that the reading line can wait for a finger to lift; nothing on
 * screen draws from any of it.
 */
internal class FollowHands {
    /** A finger (or a pressed button) is on the page, whatever it is doing. */
    var fingerDown by mutableStateOf(false)

    /** A move the follow is making right now, which is the app's and not yours. */
    var ownMove = false

    /** This follow's one rubber band has been used. */
    var bandSpent = false

    /** A finger is pulling against the band. */
    var bandHeld = false

    /** The band is easing the page back to where it was. */
    var bandReturning = false

    /** How far the band has been pulled, in the drag's own direction and px. */
    var stretched = 0f

    /**
     * When the page last said where its line is, on the clock that only runs
     * forward (`SystemClock.elapsedRealtime`): set back, the wall clock would
     * leave the line unsaid for as long as it went back.
     */
    var lineSentAt = 0L

    /** In the band, or on the way back from it: nothing moves or speaks. */
    val banding: Boolean get() = bandHeld || bandReturning

    /** A new follow, or none: one band each. */
    fun freshBand() {
        bandSpent = false
        bandHeld = false
        stretched = 0f
    }
}

/**
 * Notices a finger on the page without taking anything from it: the first
 * look at every pointer event, before the text or the list has seen it.
 */
internal fun Modifier.noticingFingers(hands: FollowHands): Modifier =
    pointerInput(hands) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                hands.fingerDown = event.changes.any { it.pressed }
            }
        }
    }

/**
 * Whether a screen reader is exploring the screen by touch — TalkBack, or
 * anything else that takes the page's touches for its own. Watched, because
 * it is turned on and off while the app is open.
 */
@Composable
internal fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        on = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}
