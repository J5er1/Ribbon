@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalUuidApi::class)

package app.readribbon.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// How the app flows rather than cuts.
//
// The complaint this exists to answer: every screen change in the app was a
// *substitution*. The room faded and the menu slid up over it; the room faded
// and the book appeared; a settings row was replaced by a settings screen. All
// of it moved, none of it continued — and a thing that is in two places on
// either side of a transition, drawn identically and animated separately,
// reads as two things rather than as one thing that went somewhere.
//
// So the pieces that exist on both sides of a change are now the same piece.
// A face at the hearth *is* that person's own screen. The ember on the shelf
// *is* the ember on its own record. A settings row's words *are* the heading
// of the screen it opens.
//
// **What is deliberately not here, and why.** A shared element pairs exactly
// two halves, one leaving and one arriving, and it needs one of them to be on
// its way out. The menu is a *layer over* the room rather than a replacement
// for it (deviation A17, so that predictive back can peel the room in behind
// it), which means the room stays composed and visible underneath for as long
// as the menu is open. A key shared between the two would therefore have two
// permanently live halves with neither leaving — an ambiguity rather than a
// transition. So the room's fire does not travel to its row in the menu and
// your face does not travel to the top of You, however much both would have
// been worth having. Everything that does flow here is a NavHost push, where
// exactly one side is always on its way out.
//
// Mechanically that is one `SharedTransitionLayout` at the root of the whole
// stack and an `AnimatedVisibilityScope` per layer, wired through two
// composition locals so that no screen has to be handed either of them as a
// parameter. A screen that is drawn outside a flow — a preview, a test, a
// sheet — finds both locals null and simply draws, which is why every helper
// here degrades to `this` rather than throwing.

/**
 * The one shared-transition scope, provided at the root of the room's stack.
 *
 * Null wherever the stack isn't — previews and tests — so an element outside
 * it draws in place rather than crashing.
 */
val LocalFlowRoot = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The layer this composition is inside: a NavHost destination, the book, the
 * menu. A shared element needs to know which visibility it belongs to, and
 * this is how it finds out without being told.
 */
val LocalFlowLayer = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The names of the things that travel.
 *
 * Keys are strings built from ids rather than ad-hoc literals, because a
 * shared element whose key is a typo is not an error — it is an element that
 * silently stops flowing, which is exactly the failure this file is here to
 * fix. Everything that flows is named here, once.
 */
object Flows {

    /** A finished reading's ember: on the shelf, and on its own record. */
    fun ember(readingID: Uuid): String = "ember:$readingID"

    /** That ember's book name, which is the same name on both screens. */
    fun emberName(readingID: Uuid): String = "ember-name:$readingID"

    /**
     * A seat at the hearth, and that person's own screen.
     *
     * Keyed by the room as well as the person, because switching rooms
     * cross-fades one room over another and *you* have a seat in both: a
     * person-only key would have had two live halves with neither leaving
     * for the length of every switch.
     */
    fun seat(roomID: Uuid, personID: Uuid): String = "seat:$roomID:$personID"

    // The five settings doors, named once. `settingsTitle` takes a free-form
    // string, and both halves of each pair used to spell theirs out by hand
    // in two different files — which is exactly the failure this object was
    // written to prevent: a key that does not match is not an error, it is an
    // element that silently stops flowing.
    const val TEXT = "text"
    const val NOTIFICATIONS = "notifications"
    const val APPEARANCE = "appearance"
    const val DOWNLOADS = "downloads"
    const val PLAN = "plan"

    /** That screen's heading, which was the row's own words a moment ago. */
    fun settingsTitle(route: String): String = "settings-title:$route"
}

object RibbonFlow {

    /**
     * How a shared element travels.
     *
     * A spring rather than a tween, for the reason the whole spring section of
     * [RibbonMotion] exists: a thing that is going somewhere because a finger
     * sent it there should arrive under its own momentum. Critically damped,
     * so it never overshoots the place it is going.
     *
     * Slightly stiffer than [RibbonMotion.cover] because a travelling element
     * is smaller than a screen and a long, slow flight across a phone reads as
     * a demonstration of the transition rather than as the transition.
     */
    val bounds = BoundsTransform { _, _ ->
        spring(
            dampingRatio = RibbonMotion.DAMPING,
            stiffness = 260f,
            visibilityThreshold = Rect.VisibilityThreshold,
        )
    }

    /** The same, held still: reduce motion puts the element where it lands (§11). */
    val stillBounds = BoundsTransform { _, _ -> snap() }

}

/**
 * This element is the same element as the one under [key] on the other side
 * of whatever change is happening: travel there rather than being replaced.
 *
 * For things that are genuinely identical in both places — a face, a fire, an
 * ember, a line of words.
 *
 * @param zIndex which of two travelling elements is drawn on top. Left at 0
 *   unless two of them cross, which in this app only the hearth and the book
 *   ever do.
 */
@Composable
fun Modifier.flows(key: Any, zIndex: Float = 0f): Modifier {
    val root = LocalFlowRoot.current ?: return this
    val layer = LocalFlowLayer.current ?: return this
    val still = rememberReduceMotion()
    return with(root) {
        this@flows.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = layer,
            boundsTransform = if (still) RibbonFlow.stillBounds else RibbonFlow.bounds,
            zIndexInOverlay = zIndex,
        )
    }
}

/**
 * These two are the same words, set differently: a settings row's title and
 * the heading of the screen it opens, a book's name on the shelf and on its
 * own record.
 *
 * `sharedBounds` rather than [flows], and the difference is not academic.
 * [flows] is for content that is genuinely identical in both places — a face,
 * a fire, an ember — and it carries one drawing between two frames. Words set
 * at 17 sp in the interface face and at 30 sp in the display face are not one
 * drawing, and asking [flows] to carry them stretches the type on the way.
 * This animates the *frame* and cross-fades what is inside it, which is the
 * honest account of a small line becoming a large one.
 */
@Composable
fun Modifier.flowsAsWords(key: Any): Modifier {
    val root = LocalFlowRoot.current ?: return this
    val layer = LocalFlowLayer.current ?: return this
    val still = rememberReduceMotion()
    return with(root) {
        this@flowsAsWords.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = layer,
            enter = fadeIn(RibbonMotion.arrive(still)),
            exit = fadeOut(RibbonMotion.arrive(still)),
            boundsTransform = if (still) RibbonFlow.stillBounds else RibbonFlow.bounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
