@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.VerseAddress
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What an announcement says that is worth spending a send on: where you
 * are, whether you have gone still, who you follow, and the name the room
 * knows you by. How far down the chapter you are is not on this list — it
 * rides along with the next send that is, and is never a send of its own.
 */
internal data class Stance(
    val position: VerseAddress?,
    val isIdle: Boolean,
    val following: Uuid?,
    val name: String,
)

/**
 * How often presence may speak (§4.2).
 *
 * Realtime allows a client five presence messages in thirty seconds, and a
 * sixth closes the channel — which on the other phone is the person simply
 * vanishing from the book. The live project's logs showed it happening dozens
 * of times a day: a scroll re-tracked every two seconds, and a follow made
 * that worse, not better. So every presence send goes through here.
 *
 * Four tracks in a window are anybody's; the fifth slot is kept for the two
 * things that must travel at once — leaving the book, and starting or ending
 * a follow, which is what "Ruth is with you" appears and goes on. Anything
 * else waits for the window to open again, and what goes then is whatever is
 * true by then, not a queue of what was true on the way.
 *
 * A join always says where you are, budget or no budget: appearing matters
 * more than the rule, and a join is its own channel on the server's side.
 *
 * The send times outlive a reconnect — they are this client's, not one
 * socket's — and the clock is handed in so the rule can be tested without
 * waiting thirty seconds for it.
 */
internal class PresenceBudget(private val clock: () -> Long = System::currentTimeMillis) {

    /** What to do with the announcement as it now stands. */
    sealed interface Verdict {
        /** The room already knows. */
        data object Quiet : Verdict

        /** Say it: a track. */
        data object Track : Verdict

        /** Say you have left the book: an untrack. */
        data object Untrack : Verdict

        /** Not yet. Look again in this many milliseconds. */
        data class Later(val inMs: Long) : Verdict
    }

    /** When each send in the last window went, oldest first. */
    private val sends = ArrayDeque<Long>()

    /**
     * What this join last said: null for nothing — a fresh join, or an
     * untrack.
     */
    private var said: Stance? = null

    /**
     * What to do about [desired] — null for "not in the book".
     *
     * @param joining this is the join's own re-track, which always goes.
     */
    fun reconcile(desired: Stance?, joining: Boolean = false): Verdict {
        val now = clock()
        forgetBefore(now)
        if (desired == said) return Verdict.Quiet
        val send = if (desired == null) Verdict.Untrack else Verdict.Track
        if (joining) return send
        val urgent = desired == null || desired.following != said?.following
        val allowed = if (urgent) SLOTS else TRACKS
        if (sends.size < allowed) return send
        // Enough of the window has to pass for one slot to open.
        val opens = sends[sends.size - allowed] + WINDOW_MS + MARGIN_MS
        return Verdict.Later((opens - now).coerceAtLeast(MARGIN_MS))
    }

    /**
     * A frame actually went out on an open socket — only then does it count,
     * and only then is it what the room knows.
     */
    fun wrote(stance: Stance?) {
        val now = clock()
        forgetBefore(now)
        sends.addLast(now)
        said = stance
    }

    /**
     * A new join: the server has nothing from this device on it yet. The
     * window stays — it is the client's.
     */
    fun joined() {
        said = null
    }

    /**
     * The server said the limit was reached. Nothing more goes for a whole
     * window: the next one would close the channel again.
     */
    fun saturate() {
        val now = clock()
        sends.clear()
        repeat(SLOTS) { sends.addLast(now) }
    }

    private fun forgetBefore(now: Long) {
        while (sends.isNotEmpty() && now - sends.first() >= WINDOW_MS) sends.removeFirst()
    }

    companion object {
        /** Realtime's window for presence messages. */
        const val WINDOW_MS = 30_000L

        /** Realtime's allowance in that window. */
        const val SLOTS = 5

        /** What ordinary movement may use of it; the rest is kept. */
        const val TRACKS = 4

        /** A little past the window's edge, so a send never lands on it. */
        const val MARGIN_MS = 250L

        /**
         * Whether a `system` message is the server saying presence hit its
         * limit. Matched loosely — the wording is the server's to change —
         * but only ever on the message, which is never logged.
         */
        fun isPresenceLimit(message: String?, extension: String?): Boolean {
            val text = message?.lowercase() ?: return false
            val limited = ("rate" in text && "limit" in text) || "ratelimit" in text ||
                "too many" in text
            val presence = "presence" in text || extension.equals("presence", ignoreCase = true)
            return limited && presence
        }
    }
}
