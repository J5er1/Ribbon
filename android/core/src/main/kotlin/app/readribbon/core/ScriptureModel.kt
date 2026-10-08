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
) {

    /**
     * Where a page set verse by verse starts a new line (A68): the indices
     * of the spans it breaks before. Only prose does (`p`, `m`), and only
     * before a span that begins a verse; never before a block's first span,
     * which starts a line already. Poetry keeps its own lines, and a title or
     * a stanza break is not a verse's to break.
     *
     * It adds no characters to any verse's own text ([ScriptureChapter.ownTexts]):
     * a verse always starts a fresh span, so every break falls on a span
     * boundary, and the page builders put it outside the verse's text, as
     * they do a block's own newline.
     */
    fun verseLineStarts(): List<Int> {
        if (s != BlockStyle.p && s != BlockStyle.m) return emptyList()
        return x.indices.filter { it > 0 && x[it].v != null }
    }
}

@Serializable
data class ScriptureChapter(
    val n: Int,
    val blocks: List<ScriptureBlock>,
    /**
     * The edition's own copyright line, as a licensed edition sends it with
     * every chapter (A64). Null for the bundled public-domain texts.
     */
    val copyright: String? = null,
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

    /**
     * Every verse's own text — the string a phrase mark's offsets and a word
     * link's ranges count in (A41g, A60). Not [text], which is for quoting:
     * this one is exactly what the page draws for the verse, spans glued with
     * no separator ("increased!How" at a poetic line break), trailing spaces
     * kept, psalm titles and stanza breaks left out. Offsets into it are
     * UTF-16 code units — Kotlin's `length`, and Swift's `utf16.count`.
     *
     * A verse number on a title still moves the running verse, because the
     * page builders do the same: in Zechariah 12 the burden's title carries
     * verse 1 and the paragraph after it continues that verse.
     */
    fun ownTexts(): Map<Int, String> {
        val texts = linkedMapOf<Int, StringBuilder>()
        var running: Int? = null
        for (block in blocks) {
            if (block.s == BlockStyle.b) continue
            for (span in block.x) {
                val v = span.v
                if (v != null) running = v
                val verse = running
                if (block.s == BlockStyle.d || verse == null) continue
                texts.getOrPut(verse) { StringBuilder() }.append(span.t)
            }
        }
        return texts.mapValues { it.value.toString() }
    }

    /** One verse's own text (see [ownTexts]), or null when it is not here. */
    fun ownText(verse: Int): String? = ownTexts()[verse]

    /**
     * Where each verse's own text starts a new span after its first — the
     * UTF-16 offsets at which a poetic line was glued to the one before. A
     * word never runs across one ([PivotAligner.tokens]). A verse with one
     * span has an empty list.
     */
    fun ownSpanBreaks(): Map<Int, List<Int>> {
        val lengths = mutableMapOf<Int, Int>()
        val breaks = linkedMapOf<Int, MutableList<Int>>()
        var running: Int? = null
        for (block in blocks) {
            if (block.s == BlockStyle.b) continue
            for (span in block.x) {
                val v = span.v
                if (v != null) running = v
                val verse = running
                if (block.s == BlockStyle.d || verse == null) continue
                val length = lengths[verse]
                if (length != null) {
                    breaks.getOrPut(verse) { mutableListOf() }.add(length)
                } else {
                    breaks[verse] = mutableListOf()
                }
                lengths[verse] = (length ?: 0) + span.t.length
            }
        }
        return breaks
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
