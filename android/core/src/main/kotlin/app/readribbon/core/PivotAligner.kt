// Word links for a version the app does not ship links for (A60) — the
// licensed ones, whose text only ever reaches the phone a chapter at a time.
// Nothing is stored or sent anywhere: the links are worked out on the phone,
// from the chapter it already holds, by lining the version's words up against
// the Berean Standard's for the same verse and borrowing the Berean
// Standard's links to the original wherever the two say the same word.
//
// tools/pivot_align.py is the reference: this file ports it step for step,
// the Swift port matches both, and Fixtures/pivot_cases.json holds the three
// to the same answers. The stop words and the irregular forms are generated
// into PivotLexicon.generated.kt by that script; edit them there.

package app.readribbon.core

object PivotAligner {
    /**
     * A word of a verse's own text. [start] and [end] are UTF-16 offsets,
     * half-open; [norm] is the word lower-cased with apostrophes taken out;
     * [stem] is what it is matched on.
     */
    data class Token(
        val start: Int,
        val end: Int,
        val norm: String,
        val stem: String,
        val isContent: Boolean,
    )

    // Tokens

    /**
     * ASCII letters and digits, and the Latin-1 and Latin Extended letters
     * (U+00C0–U+024F) without the two signs among them, × and ÷.
     */
    internal fun isTokenUnit(u: Char): Boolean = when (u.code) {
        in 0x30..0x39, in 0x41..0x5A, in 0x61..0x7A -> true
        0xD7, 0xF7 -> false
        in 0xC0..0x24F -> true
        else -> false
    }

    internal fun isApostrophe(u: Char): Boolean = u.code == 0x27 || u.code == 0x2019

    /**
     * One unit lower-cased by a fixed table, so both platforms agree to the
     * letter whatever their Unicode library says: ASCII, Latin-1 and Latin
     * Extended-A. Anything else is left as it is.
     */
    internal fun lower(u: Char): Char {
        val c = u.code
        val lowered = when {
            c in 0x41..0x5A -> c + 0x20
            c in 0xC0..0xDE && c != 0xD7 -> c + 0x20
            c == 0x130 -> 0x69 // İ → i, the simple mapping
            c in 0x100..0x137 && c % 2 == 0 -> c + 1
            c in 0x139..0x148 && c % 2 == 1 -> c + 1
            c in 0x14A..0x177 && c % 2 == 0 -> c + 1
            c == 0x178 -> 0xFF // Ÿ → ÿ
            c in 0x179..0x17E && c % 2 == 1 -> c + 1
            else -> c
        }
        return lowered.toChar()
    }

    /**
     * The words of a verse's own text. A word is a run of letters and digits;
     * an apostrophe belongs to a word only between two of its letters
     * ("Lord’s", "o’er"), and a hyphen never does. [spanBreaks] are the
     * offsets where the page starts a new span — a poetic line glued to the
     * one before with no space — and always end a word.
     */
    fun tokens(text: String, spanBreaks: List<Int> = emptyList()): List<Token> {
        val breaks = spanBreaks.toSet()
        val tokens = mutableListOf<Token>()
        var start: Int? = null
        val norm = StringBuilder()

        fun close(end: Int) {
            val s = start ?: return
            val word = norm.toString()
            tokens.add(Token(s, end, word, stem(word), word !in PivotLexicon.stopWords))
            start = null
            norm.setLength(0)
        }

        for (i in text.indices) {
            val u = text[i]
            if (i in breaks) close(i)
            if (isTokenUnit(u)) {
                if (start == null) start = i
                norm.append(lower(u))
            } else if (isApostrophe(u) && start != null && i + 1 < text.length &&
                isTokenUnit(text[i + 1]) && (i + 1) !in breaks
            ) {
                // Inside a word, and dropped from its normal form.
                continue
            } else {
                close(i)
            }
        }
        close(text.length)
        return tokens
    }

    // Stems

    /**
     * What a word is matched on: its irregular base form when it has one
     * ("spake" → "speak", "brethren" → "brother"), else its Porter stem.
     */
    fun stem(norm: String): String = PivotLexicon.irregular[norm] ?: porter(norm)

    /**
     * M. F. Porter's suffix-stripping algorithm, as published in 1980 ("An
     * algorithm for suffix stripping", Program 14(3)) — the original, not its
     * later revisions. Expects a lower-case word.
     */
    fun porter(word: String): String {
        val w = StringBuilder(word)
        Porter.step1a(w)
        Porter.step1b(w)
        Porter.step1c(w)
        Porter.step2(w)
        Porter.step3(w)
        Porter.step4(w)
        Porter.step5(w)
        return w.toString()
    }

    // Alignment

    /**
     * Links for a reader's verse, borrowed from the pivot's. Both texts are
     * one verse's own text; the breaks are where each one's spans begin.
     *
     * The reader's words are lined up with the pivot's by the longest common
     * subsequence of stems, a content word weighing three times a function
     * word. Each reader word paired with a linked pivot word takes that link's
     * original words. Content words keep theirs; a function word keeps its
     * only between two content words that did, or beside one that took
     * exactly the same words ("shall not perish"). Neighbouring words with the
     * same original words become one link.
     */
    fun align(
        reader: String,
        readerBreaks: List<Int>,
        pivot: String,
        pivotBreaks: List<Int>,
        pivotLinks: List<AlignmentLink>,
    ): List<AlignmentLink> {
        val r = tokens(reader, readerBreaks)
        val p = tokens(pivot, pivotBreaks)
        val n = r.size
        val m = p.size
        if (n == 0 || m == 0) return emptyList()

        // 1. Each pivot word's original words: those of the link it sits in.
        val pivotWords: List<List<Int>?> = p.map { token ->
            pivotLinks.firstOrNull { it.start <= token.start && token.end <= it.end }?.words
        }

        // 2. Weighted longest common subsequence.
        fun matches(i: Int, j: Int): Boolean = r[i].stem == p[j].stem
        fun weight(i: Int): Int = if (r[i].isContent) 3 else 1
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                var best = maxOf(d[i - 1][j], d[i][j - 1])
                if (matches(i - 1, j - 1)) best = maxOf(best, d[i - 1][j - 1] + weight(i - 1))
                d[i][j] = best
            }
        }
        val paired = arrayOfNulls<Int>(n)
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            if (matches(i - 1, j - 1) && d[i][j] == d[i - 1][j - 1] + weight(i - 1)) {
                paired[i - 1] = j - 1
                i -= 1
                j -= 1
            } else if (d[i][j] == d[i - 1][j]) {
                i -= 1
            } else {
                j -= 1
            }
        }

        // 3. Candidates, and which of them stay.
        val candidate = arrayOfNulls<List<Int>>(n)
        for (k in 0 until n) {
            val pj = paired[k] ?: continue
            candidate[k] = pivotWords[pj]
        }
        val content = (0 until n).filter { candidate[it] != null && r[it].isContent }
        val kept = arrayOfNulls<List<Int>>(n)
        for (k in content) kept[k] = candidate[k]
        for (k in 0 until n) {
            val words = candidate[k] ?: continue
            if (r[k].isContent) continue
            val between = content.any { it < k } && content.any { it > k }
            val besideSame =
                (k > 0 && r[k - 1].isContent && candidate[k - 1] != null && candidate[k - 1] == words) ||
                    (k + 1 < n && r[k + 1].isContent && candidate[k + 1] != null && candidate[k + 1] == words)
            if (between || besideSame) kept[k] = words
        }

        // 4. Neighbours with the same words become one link.
        val links = mutableListOf<AlignmentLink>()
        var previous: Int? = null
        for (k in 0 until n) {
            val words = kept[k]
            if (words == null) {
                previous = null
                continue
            }
            val prev = previous
            if (prev != null && kept[prev] == words && links.isNotEmpty()) {
                val last = links.removeAt(links.size - 1)
                links.add(last.copy(end = r[k].end))
            } else {
                links.add(AlignmentLink(r[k].start, r[k].end, words))
            }
            previous = k
        }
        return links.sortedBy { it.start }
    }
}

/**
 * The steps of Porter's algorithm over a lower-case word's UTF-16 units. Any
 * unit that is not a, e, i, o, u, or a y after a consonant, counts as a
 * consonant — which is what Porter's definition says for letters outside the
 * English alphabet too.
 */
internal object Porter {
    fun isConsonant(w: CharSequence, i: Int): Boolean = when (w[i]) {
        'a', 'e', 'i', 'o', 'u' -> false
        'y' -> if (i == 0) true else !isConsonant(w, i - 1)
        else -> true
    }

    /** m in [C](VC)^m[V], over the first [length] units. */
    fun measure(w: CharSequence, length: Int): Int {
        var n = 0
        var i = 0
        while (i < length && isConsonant(w, i)) i += 1
        while (i < length) {
            while (i < length && !isConsonant(w, i)) i += 1
            if (i >= length) break
            while (i < length && isConsonant(w, i)) i += 1
            n += 1
        }
        return n
    }

    /** *v* — the first [length] units contain a vowel. */
    fun hasVowel(w: CharSequence, length: Int): Boolean = (0 until length).any { !isConsonant(w, it) }

    /** *d — the first [length] units end with a double consonant. */
    fun endsDouble(w: CharSequence, length: Int): Boolean =
        length >= 2 && w[length - 1] == w[length - 2] && isConsonant(w, length - 1)

    /** *o — the first [length] units end consonant–vowel–consonant, the last not w, x or y. */
    fun endsCVC(w: CharSequence, length: Int): Boolean {
        if (length < 3) return false
        if (!isConsonant(w, length - 3) || isConsonant(w, length - 2) || !isConsonant(w, length - 1)) return false
        val last = w[length - 1]
        return last != 'w' && last != 'x' && last != 'y'
    }

    fun ends(w: CharSequence, suffix: String): Boolean = w.endsWith(suffix)

    fun replace(w: StringBuilder, suffix: String, replacement: String) {
        w.setLength(w.length - suffix.length)
        w.append(replacement)
    }

    fun removeLast(w: StringBuilder, count: Int = 1) {
        w.setLength(w.length - count)
    }

    fun step1a(w: StringBuilder) {
        when {
            ends(w, "sses") -> replace(w, "sses", "ss")
            ends(w, "ies") -> replace(w, "ies", "i")
            ends(w, "ss") -> return
            ends(w, "s") -> removeLast(w)
        }
    }

    fun step1b(w: StringBuilder) {
        var tidy = false
        if (ends(w, "eed")) {
            if (measure(w, w.length - 3) > 0) replace(w, "eed", "ee")
        } else if (ends(w, "ed")) {
            if (hasVowel(w, w.length - 2)) {
                removeLast(w, 2)
                tidy = true
            }
        } else if (ends(w, "ing")) {
            if (hasVowel(w, w.length - 3)) {
                removeLast(w, 3)
                tidy = true
            }
        }
        if (!tidy) return
        when {
            ends(w, "at") -> replace(w, "at", "ate")
            ends(w, "bl") -> replace(w, "bl", "ble")
            ends(w, "iz") -> replace(w, "iz", "ize")
            endsDouble(w, w.length) && w[w.length - 1] != 'l' && w[w.length - 1] != 's' && w[w.length - 1] != 'z' ->
                removeLast(w)
            measure(w, w.length) == 1 && endsCVC(w, w.length) -> w.append('e')
        }
    }

    fun step1c(w: StringBuilder) {
        if (ends(w, "y") && hasVowel(w, w.length - 1)) w.setCharAt(w.length - 1, 'i')
    }

    /**
     * The longest of [rules] the word ends with — and only that one, even when
     * its condition fails, as the paper specifies.
     */
    fun longest(w: CharSequence, rules: List<Pair<String, String>>): Pair<String, String>? =
        rules.filter { ends(w, it.first) }.maxByOrNull { it.first.length }

    val step2Rules: List<Pair<String, String>> = listOf(
        "ational" to "ate", "tional" to "tion", "enci" to "ence", "anci" to "ance",
        "izer" to "ize", "abli" to "able", "alli" to "al", "entli" to "ent",
        "eli" to "e", "ousli" to "ous", "ization" to "ize", "ation" to "ate",
        "ator" to "ate", "alism" to "al", "iveness" to "ive", "fulness" to "ful",
        "ousness" to "ous", "aliti" to "al", "iviti" to "ive", "biliti" to "ble",
    )

    val step3Rules: List<Pair<String, String>> = listOf(
        "icate" to "ic", "ative" to "", "alize" to "al", "iciti" to "ic",
        "ical" to "ic", "ful" to "", "ness" to "",
    )

    val step4Suffixes: List<String> = listOf(
        "al", "ance", "ence", "er", "ic", "able", "ible", "ant", "ement", "ment",
        "ent", "ion", "ou", "ism", "ate", "iti", "ous", "ive", "ize",
    )

    fun step2(w: StringBuilder) {
        val (suffix, replacement) = longest(w, step2Rules) ?: return
        if (measure(w, w.length - suffix.length) > 0) replace(w, suffix, replacement)
    }

    fun step3(w: StringBuilder) {
        val (suffix, replacement) = longest(w, step3Rules) ?: return
        if (measure(w, w.length - suffix.length) > 0) replace(w, suffix, replacement)
    }

    fun step4(w: StringBuilder) {
        val suffix = step4Suffixes.filter { ends(w, it) }.maxByOrNull { it.length } ?: return
        val stem = w.length - suffix.length
        if (measure(w, stem) <= 1) return
        if (suffix == "ion") {
            if (stem <= 0 || (w[stem - 1] != 's' && w[stem - 1] != 't')) return
        }
        removeLast(w, suffix.length)
    }

    fun step5(w: StringBuilder) {
        // 5a
        if (ends(w, "e")) {
            val stem = w.length - 1
            val m = measure(w, stem)
            if (m > 1 || (m == 1 && !endsCVC(w, stem))) removeLast(w)
        }
        // 5b
        if (measure(w, w.length) > 1 && endsDouble(w, w.length) && w[w.length - 1] == 'l') removeLast(w)
    }
}
