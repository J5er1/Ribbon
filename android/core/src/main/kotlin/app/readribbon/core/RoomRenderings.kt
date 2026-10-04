@file:OptIn(ExperimentalUuidApi::class)

// "In this room" (A62): how the versions read here say the words you chose,
// grouped by what they say rather than listed person by person — so a room
// of eleven on four versions is a handful of blocks, not eleven lines.
//
// Two versions say the same thing when their words are the same, in the
// same order, with capitals and punctuation set aside ("Through Him," and
// "through him" are one; "him through" is not). The words are PivotAligner's
// tokens, compared on `norm`. Yours comes first. Every other rendering
// carries the words of it that are not in yours — an order-aware word diff,
// a longest common subsequence over the norms — so the panel can set those
// at full strength and the shared words back a step.
//
// A port of core/Sources/RibbonCore/RoomRenderings.swift, case for case;
// RoomRenderingsTest.kt and RoomRenderingsTests.swift hold the two to the
// same answers.

package app.readribbon.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the versions in a room say for one selection, grouped. */
data class RoomRenderings(
    /** Yours first, then every other rendering in the order its first version was given. */
    val groups: List<Group>,
    /** Null when every version here is on this phone. */
    val notOnThisPhone: Unavailable?,
) {
    /**
     * One version as the room reads it, and how it says the selection.
     *
     * @property phrase the version's words for the selection; null when the
     *   version is licensed and has not reached this phone.
     * @property readers who here reads it, in the order their faces should
     *   sit. For yours, you first.
     */
    data class Said(
        val version: TranslationID,
        val phrase: String?,
        val readers: List<Uuid>,
    )

    /**
     * One thing said, by every version that says it.
     *
     * @property versions yours first in your group; otherwise in the order
     *   they were given.
     * @property phrase the words, as the first of [versions] says them
     *   (yours, in yours).
     * @property differing the words of [phrase] that are not in yours, as
     *   UTF-16 ranges into [phrase], one per word, in order. Always empty for
     *   yours.
     * @property readers everyone who reads one of [versions]: the readers of
     *   each version in turn, in the order of [versions] — so in yours, you
     *   first.
     */
    data class Group(
        val versions: List<TranslationID>,
        val phrase: String,
        val differing: List<TextRange>,
        val readers: List<Uuid>,
        val isYours: Boolean,
    )

    /**
     * Versions somebody here reads whose words are not on this phone. They
     * cannot be compared, so they are kept out of [groups] — and while there
     * are any, the room cannot be said to agree with you. Readers follow the
     * order of [versions], as in a group.
     */
    data class Unavailable(
        val versions: List<TranslationID>,
        val readers: List<Uuid>,
    )

    /**
     * Everyone here reads these words as you do: one rendering, and nothing
     * that could not be compared. (A room on one version agrees too; the
     * panel leaves the section out for that by [isOneVersion].)
     */
    val allAgree: Boolean
        get() = groups.size == 1 && notOnThisPhone == null

    /** Everyone here reads your version. */
    val isOneVersion: Boolean
        get() = allAgree && groups[0].versions.size == 1

    companion object {
        /**
         * Groups [others] against [yours]. A version given twice joins the
         * group it is already in. `yours.phrase` is the page's own words;
         * null is read as no words at all.
         */
        fun of(yours: Said, others: List<Said>): RoomRenderings {
            val mine = yours.phrase ?: ""
            // Each group's versions, with each version's readers, in the order
            // they are first given; flattened at the end so readers follow the
            // order of the versions.
            class Found(val said: MutableList<Said>, val key: List<String>)
            val found = mutableListOf(Found(mutableListOf(yours), norms(mine)))
            val missing = mutableListOf<Said>()

            for (said in others) {
                val g = found.indexOfFirst { group -> group.said.any { it.version == said.version } }
                val phrase = said.phrase
                if (g >= 0) {
                    val v = found[g].said.indexOfFirst { it.version == said.version }
                    found[g].said[v] = found[g].said[v].copy(readers = found[g].said[v].readers + said.readers)
                    continue
                }
                val m = missing.indexOfFirst { it.version == said.version }
                if (m >= 0) {
                    missing[m] = missing[m].copy(readers = missing[m].readers + said.readers)
                } else if (phrase != null) {
                    val key = norms(phrase)
                    val same = found.indexOfFirst { it.key == key }
                    if (same >= 0) found[same].said.add(said) else found.add(Found(mutableListOf(said), key))
                } else {
                    missing.add(said)
                }
            }

            val groups = found.mapIndexed { index, group ->
                val phrase = if (index == 0) mine else group.said[0].phrase ?: ""
                Group(
                    versions = group.said.map { it.version },
                    phrase = phrase,
                    differing = if (index == 0) emptyList() else differing(phrase, mine),
                    readers = group.said.flatMap { it.readers },
                    isYours = index == 0,
                )
            }
            val unavailable = if (missing.isEmpty()) {
                null
            } else {
                Unavailable(versions = missing.map { it.version }, readers = missing.flatMap { it.readers })
            }
            return RoomRenderings(groups, unavailable)
        }

        /**
         * The words of [phrase] not on a longest common subsequence with
         * [yours], compared on norms — so a reordering is a difference and a
         * capital or a comma is not. Ties break the same way on both
         * platforms: walking forward, a matching pair is always taken;
         * otherwise the word of [phrase] is passed over when that keeps the
         * subsequence as long.
         */
        fun differing(phrase: String, yours: String): List<TextRange> {
            val tokens = PivotAligner.tokens(phrase)
            val a = tokens.map { it.norm }
            val b = norms(yours)
            val n = a.size
            val m = b.size
            // table[i][j]: the longest common subsequence of a[i..] and b[j..].
            val table = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    table[i][j] = if (a[i] == b[j]) {
                        table[i + 1][j + 1] + 1
                    } else {
                        maxOf(table[i + 1][j], table[i][j + 1])
                    }
                }
            }
            val shared = BooleanArray(n)
            var i = 0
            var j = 0
            while (i < n && j < m) {
                if (a[i] == b[j]) {
                    shared[i] = true
                    i++
                    j++
                } else if (table[i + 1][j] >= table[i][j + 1]) {
                    i++
                } else {
                    j++
                }
            }
            return tokens.indices
                .filter { !shared[it] }
                .map { TextRange(tokens[it].start, tokens[it].end) }
        }

        private fun norms(text: String): List<String> = PivotAligner.tokens(text).map { it.norm }
    }
}
