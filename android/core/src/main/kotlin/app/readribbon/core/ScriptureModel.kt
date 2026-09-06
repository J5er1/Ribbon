// The Scripture data model. Text ships as one JSON file per book per
// translation (produced by tools/usfx_to_json.py from the public-domain
// USFX sources at ebible.org), preserving the structure a printed page
// keeps: paragraphs, poetic lines with their indents, and Jesus' words for
// the optional red-letter setting (S20).

package app.readribbon.core

import kotlinx.serialization.Serializable

/** How a block of text sits on the page. */
@Serializable
enum class BlockStyle {
    /** A prose paragraph. */
    p,

    /** A continuation paragraph (no first-line indent). */
    m,

    /**
     * A poetic line, first indent level. Rendered with a hanging indent,
     * never a horizontal scroll (S02 edge cases).
     */
    q1,

    /** A poetic line, second indent level. */
    q2,

    /** A descriptor — a psalm title like "A Psalm of David." */
    d,

    /** A stanza break. */
    b
}

/**
 * A run of text inside a block. When `v` is present the span begins that
 * verse, and the verse number renders in small caps superscript at ~45%
 * opacity (S02). `w` marks words of Jesus for red-letter.
 */
@Serializable
data class ScriptureSpan(
    val v: Int? = null,
    val t: String,
    val w: Boolean? = null
) {
    val isRedLetter: Boolean get() = w == true
}

@Serializable
data class ScriptureBlock(
    val s: BlockStyle,
    val x: List<ScriptureSpan>
)

@Serializable
data class ScriptureChapter(
    val n: Int,
    val blocks: List<ScriptureBlock>
) {

    /** The verses present in this chapter, in order. */
    val verseNumbers: List<Int>
        get() = blocks.flatMap { block -> block.x.mapNotNull { it.v } }

    /** The full text of one verse across blocks — what a note quotes. */
    fun text(forVerse: Int): String? {
        val parts: MutableList<String> = mutableListOf()
        var inVerse = false
        for (block in blocks) {
            for (span in block.x) {
                val v = span.v
                if (v != null) {
                    inVerse = (v == forVerse)
                }
                if (inVerse) {
                    parts.add(span.t)
                }
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(separator = " ")
            .replace("  ", " ")
            .trim()
    }
}

@Serializable
data class ScriptureBookText(
    val id: String,
    val name: String,
    val chapters: List<ScriptureChapter>
) {

    fun chapter(n: Int): ScriptureChapter? {
        return chapters.firstOrNull { it.n == n }
    }
}
