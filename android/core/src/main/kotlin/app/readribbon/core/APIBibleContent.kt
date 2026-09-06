// Converts an API.Bible chapter payload (`content-type=json`) into the same
// page model the bundled translations use, so the reading surface never
// knows where text came from. Their JSON is a tree of paragraph nodes with
// USFM-style block styles; verse markers arrive as items inside them —
// close enough to USFX that the same style mapping applies.
//
// Parsing is deliberately lenient: unknown node shapes are walked for
// their text rather than rejected, because a licensed feed that changes
// shape must degrade to readable prose, never to a blank page.

package app.readribbon.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object APIBibleContent {

    /**
     * Decode `{"data": {"content": [...]}}` (or a bare content array)
     * into a chapter. Returns null when no verse text could be found.
     */
    fun chapter(number: Int, from: ByteArray): ScriptureChapter? {
        val root = try {
            Json.parseToJsonElement(from.decodeToString())
        } catch (e: Exception) {
            return null
        }

        var content: List<JsonElement>? = null
        val obj = root.asObject()
        if (obj != null) {
            val dataObject = obj["data"].asObject()
            content = if (dataObject != null) {
                dataObject["content"].asArray()
            } else {
                obj["content"].asArray()
            }
        } else {
            val array = root.asArray()
            if (array != null) {
                content = array
            }
        }
        val nodes = content ?: return null

        val builder = Builder()
        for (node in nodes) {
            builder.walkBlock(node)
        }
        builder.closeBlock()

        val blocks = builder.blocks
        if (blocks.none { it.x.isNotEmpty() }) return null
        return ScriptureChapter(n = number, blocks = blocks)
    }

    // The USFM paragraph styles worth keeping, mapped exactly like the
    // USFX converter (tools/usfx_to_json.py).
    internal val styleMap: Map<String, BlockStyle> = mapOf(
        "p" to BlockStyle.p, "pc" to BlockStyle.p, "pi1" to BlockStyle.p, "pi2" to BlockStyle.p, "po" to BlockStyle.p,
        "m" to BlockStyle.m, "mi" to BlockStyle.m, "nb" to BlockStyle.m, "pmo" to BlockStyle.m, "pm" to BlockStyle.m, "pmc" to BlockStyle.m, "pmr" to BlockStyle.m, "cls" to BlockStyle.m,
        "q" to BlockStyle.q1, "q1" to BlockStyle.q1, "li1" to BlockStyle.q1, "qm1" to BlockStyle.q1,
        "q2" to BlockStyle.q2, "q3" to BlockStyle.q2, "q4" to BlockStyle.q2, "qr" to BlockStyle.q2, "li2" to BlockStyle.q2, "li3" to BlockStyle.q2, "qm2" to BlockStyle.q2, "qm3" to BlockStyle.q2,
        "d" to BlockStyle.d, "qa" to BlockStyle.d, "sp" to BlockStyle.d, "qd" to BlockStyle.d,
        "b" to BlockStyle.b,
    )

    /** Styles whose whole subtree is headings or apparatus, not Scripture. */
    internal val skippedStyles: Set<String> = setOf(
        "s", "s1", "s2", "s3", "ms", "ms1", "r", "mt", "mt1", "mt2", "mt3",
        "mr", "sr", "cl", "cp", "f", "x", "fe", "note",
    )

    private class Builder {
        var blocks: MutableList<ScriptureBlock> = mutableListOf()
        var spans: MutableList<ScriptureSpan> = mutableListOf()
        var blockStyle: BlockStyle = BlockStyle.p
        var pendingVerse: Int? = null
        var redLetterDepth = 0

        fun walkBlock(node: JsonElement) {
            val obj = node.asObject() ?: return
            val style = obj["attrs"].asObject()?.get("style").asString() ?: "p"
            if (APIBibleContent.skippedStyles.contains(style)) return
            val mapped = APIBibleContent.styleMap[style]
            closeBlock()
            if (mapped == BlockStyle.b) {
                val last = blocks.lastOrNull()
                if (last != null && last.s != BlockStyle.b) {
                    blocks.add(ScriptureBlock(s = BlockStyle.b, x = emptyList()))
                }
                return
            }
            blockStyle = mapped ?: BlockStyle.p
            walkItems(obj["items"].asArray() ?: emptyList())
            closeBlock()
        }

        fun walkItems(items: List<JsonElement>) {
            // The live feed's node shape (verified against the real NKJV/
            // NIV/NASB payloads): text nodes are {type:"text", text},
            // everything else is {type:"tag", name, attrs.style}. A verse
            // marker is name "verse" with attrs.number — and its own items
            // repeat the number as text, so a marker's subtree is never
            // walked.
            for (item in items) {
                val obj = item.asObject() ?: continue
                val name = obj["name"].asString()
                val type = obj["type"].asString()
                val attrs = obj["attrs"].asObject()
                val style = attrs?.get("style").asString()

                if (type == "text") {
                    val text = obj["text"].asString()
                    if (text != null) {
                        push(text)
                    }
                    continue
                }
                if (name == "verse" || style == "v") {
                    val number = attrs?.get("number").asString()
                        ?.takeWhile { it.isDigit() }
                        ?.toIntOrNull()
                    if (number != null) {
                        pendingVerse = number
                    }
                    continue
                }
                if (style != null && APIBibleContent.skippedStyles.contains(style)) {
                    continue  // a footnote or cross-reference subtree
                }
                val isRed = style == "wj"
                if (isRed) redLetterDepth += 1
                walkItems(obj["items"].asArray() ?: emptyList())
                if (isRed) redLetterDepth -= 1
            }
        }

        fun push(raw: String) {
            // Swift ran `\s+` through NSRegularExpression, whose ICU `\s` is
            // [\t\n\f\r\p{Z}] — it collapses the Unicode spaces (NBSP and
            // friends) that publisher USFM carries. Java's `\s` is ASCII-only
            // and would leave them in the text, so `\p{Z}` is added back to
            // keep both apps producing byte-identical verse text.
            val text = raw.replace(Regex("[\\s\\p{Z}]+"), " ")
            if (text.trim().isEmpty() && spans.isEmpty()) return
            val red = redLetterDepth > 0
            val last = spans.lastOrNull()
            if (pendingVerse == null && last != null && last.isRedLetter == red) {
                spans[spans.size - 1] = last.copy(t = last.t + text)
            } else {
                spans.add(ScriptureSpan(v = pendingVerse, t = text, w = if (red) true else null))
                pendingVerse = null
            }
        }

        fun closeBlock() {
            val cleaned: MutableList<ScriptureSpan> = mutableListOf()
            for ((index, span) in spans.withIndex()) {
                var text = span.t
                if (index == 0) {
                    text = text.dropWhile { it == ' ' }
                }
                if (index == spans.size - 1) {
                    text = text.dropLastWhile { it == ' ' }
                }
                if (text.isEmpty()) continue
                cleaned.add(ScriptureSpan(v = span.v, t = text, w = span.w))
            }
            if (cleaned.isNotEmpty()) {
                blocks.add(ScriptureBlock(s = blockStyle, x = cleaned))
            }
            spans = mutableListOf()
        }
    }
}

// JSONSerialization hands Swift untyped `Any` values, which the walk above
// narrows with `as? [String: Any]`, `as? [Any]` and `as? String`.
// kotlinx-serialization's JsonElement tree is the same untyped shape, so
// these three stand in for exactly those casts — and, as in Swift, only a
// JSON string reads as a String, so a number or null narrows to nil/null.
private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
