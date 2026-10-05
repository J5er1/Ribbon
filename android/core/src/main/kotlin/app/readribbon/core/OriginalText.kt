// The original words under the English (A60). Each verse's Hebrew, Aramaic
// or Greek words ship in original order, one file per book
// (`Scripture/original/<BOOK>.json`, from the public-domain Berean Standard
// Bible translation tables and the Westminster Leningrad Codex, produced by
// tools/original_to_json.py). A word's 0-based position in its verse's list
// is the unit a mark is anchored to, so a mark made in one version can land
// on the same words in every other.
//
// The links between a version's own text and those words ship per version
// that has them (`Scripture/align/<id>/<BOOK>.json`); a version without them
// is linked on the phone by `PivotAligner`. The shapes on disk are compact
// tuples, so the serializers below are written out by hand, over JSON
// elements — the Swift side decodes the same arrays from unkeyed containers.

package app.readribbon.core

import kotlin.math.floor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The language a verse's original words are in. "The Hebrew", "the
 * Aramaic", "the Greek" in what a person reads; never "the original".
 */
@Serializable
enum class OriginalLanguage {
    hebrew,
    aramaic,
    greek,
}

/**
 * One original word: `[text, translit, strongs, parse]`, with a fifth
 * element `"a"` only when the word is Aramaic. An empty string on disk is
 * null here — the tables give no Strong's number for some Hebrew suffixed
 * prepositions, and no parse for a few words.
 */
@Serializable(with = OriginalWordSerializer::class)
data class OriginalWord(
    val text: String,
    val translit: String,
    val strongs: String? = null,
    val parse: String? = null,
    val isAramaic: Boolean = false,
) {
    /**
     * Greek in the New Testament; in the Old, Hebrew unless the word is one
     * of the Aramaic passages' (Daniel, Ezra, a verse of Jeremiah).
     */
    fun language(bookID: String): OriginalLanguage = when {
        OriginalWords.isNewTestament(bookID) -> OriginalLanguage.greek
        isAramaic -> OriginalLanguage.aramaic
        else -> OriginalLanguage.hebrew
    }

    companion object {
        /** Swift's memberwise init, which turns "" into nil. */
        fun of(text: String, translit: String, strongs: String?, parse: String?, isAramaic: Boolean): OriginalWord =
            OriginalWord(
                text = text,
                translit = translit,
                strongs = strongs?.takeIf { it.isNotEmpty() },
                parse = parse?.takeIf { it.isNotEmpty() },
                isAramaic = isAramaic,
            )
    }
}

/** One verse's original words, in original order: `{"v":1,"w":[…]}`. */
@Serializable
data class OriginalVerse(
    val v: Int,
    @SerialName("w") val words: List<OriginalWord>,
)

@Serializable
data class OriginalChapter(
    val n: Int,
    val verses: List<OriginalVerse>,
) {
    /**
     * The verse's words, or null when it has none here (a verse the earliest
     * manuscripts lack, a psalm title's verse 0 when absent).
     */
    fun words(verse: Int): List<OriginalWord>? = verses.firstOrNull { it.v == verse }?.words
}

/**
 * One book's original words. [source] names the numbering the positions
 * count in; a stored anchor is honoured only when its source is this one.
 */
@Serializable
data class OriginalBook(
    val id: String,
    val source: String,
    val chapters: List<OriginalChapter>,
) {
    fun chapter(n: Int): OriginalChapter? = chapters.firstOrNull { it.n == n }
}

/**
 * A dictionary entry: `["λόγος","lógos","something said …"]` — the
 * dictionary form, how to say it, and Strong's own definition.
 */
@Serializable(with = LexiconEntrySerializer::class)
data class LexiconEntry(
    val lemma: String,
    val translit: String,
    val definition: String,
)

/** Strong's dictionary, keyed `"G3056"`, `"H430"` (`original/strongs.json`). */
@Serializable(with = LexiconSerializer::class)
data class Lexicon(val entries: Map<String, LexiconEntry>) {
    /**
     * The entry for a Strong's number. Leading zeros and a lower-case letter
     * are forgiven ("g03056" finds G3056); the files never carry them, but a
     * number typed or pasted might.
     */
    fun entry(strongs: String): LexiconEntry? {
        entries[strongs]?.let { return it }
        val key = normalised(strongs) ?: return null
        return entries[key]
    }

    companion object {
        internal fun normalised(strongs: String): String? {
            val trimmed = strongs.trim()
            if (trimmed.isEmpty()) return null
            val first = trimmed[0]
            if (first !in 'A'..'Z' && first !in 'a'..'z') return null
            val digits = trimmed.substring(1)
            if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
            val n = digits.toLongOrNull() ?: return null
            return first.uppercaseChar().toString() + n.toString()
        }
    }
}

/**
 * The grammar codes' long forms, `{"N-DFS":"Noun - Dative Feminine
 * Singular", …}` (`original/parsing.json`).
 */
@Serializable(with = ParsingsSerializer::class)
data class Parsings(val long: Map<String, String>) {
    /**
     * "Noun - Dative Feminine Singular" for "N-DFS"; null for an empty code
     * or one the file does not know.
     */
    fun describe(short: String): String? {
        if (short.isEmpty()) return null
        return long[short]?.takeIf { it.isNotEmpty() }
    }
}

/**
 * `[start, end, [word…]]`: a half-open range of one version's own text for
 * one verse, in UTF-16 units, and the positions of the original words that
 * range renders. A verse's links are sorted by start and never overlap.
 */
@Serializable(with = AlignmentLinkSerializer::class)
data class AlignmentLink(
    val start: Int,
    val end: Int,
    val words: List<Int>,
)

/** One verse's links: `{"v":1,"l":[…]}`. */
@Serializable
data class VerseLinks(
    val v: Int,
    @SerialName("l") val links: List<AlignmentLink>,
)

@Serializable
data class AlignmentChapter(
    val n: Int,
    val verses: List<VerseLinks>,
) {
    fun links(verse: Int): List<AlignmentLink>? = verses.firstOrNull { it.v == verse }?.links

    /** Every verse's links, keyed by verse — the shape the pure functions take. */
    val byVerse: Map<Int, List<AlignmentLink>>
        get() = verses.associate { it.v to it.links }
}

/**
 * One version's links for one book. [basis] fingerprints the text the ranges
 * were measured in, so a re-converted text is detectably out of step.
 */
@Serializable
data class BookAlignment(
    val id: String,
    val translation: TranslationID,
    val source: String,
    val basis: String,
    val chapters: List<AlignmentChapter>,
) {
    fun chapter(n: Int): AlignmentChapter? = chapters.firstOrNull { it.n == n }
}

/** A half-open range of a verse's own text, in UTF-16 units. */
@Serializable
data class TextRange(val start: Int, val end: Int)

/**
 * Where a mark lands on one reader's page in one verse. [from] and [to] are
 * offsets into the verse's own text; both null is the whole verse.
 */
data class MarkedSpan(
    val verse: Int,
    val from: Int? = null,
    val to: Int? = null,
) {
    val isWholeVerse: Boolean get() = from == null && to == null
}

/**
 * The moves between a version's own text and the original words under it.
 * Pure, so both platforms can be held to the same answers.
 */
object OriginalWords {
    internal fun isNewTestament(bookID: String): Boolean {
        val index = Bible.books.indexOfFirst { it.id == bookID }
        return index >= 39
    }

    /**
     * The language of a book's original words, setting Aramaic aside —
     * Greek for the New Testament, Hebrew for the Old.
     */
    fun language(bookID: String): OriginalLanguage =
        if (isNewTestament(bookID)) OriginalLanguage.greek else OriginalLanguage.hebrew

    /** Every position any link of a verse renders. */
    fun linked(links: List<AlignmentLink>): Set<Int> = links.flatMap { it.words }.toSet()

    /**
     * The original words under `[from, to)` of a verse's own text: every link
     * that overlaps it, sorted, each position once. A link that only touches
     * the range's edge is not under it.
     */
    fun words(links: List<AlignmentLink>, from: Int?, to: Int?): List<Int> {
        val low = from ?: 0
        val high = to ?: Int.MAX_VALUE
        val result = sortedSetOf<Int>()
        for (link in links) {
            if (link.start < high && link.end > low) result.addAll(link.words)
        }
        return result.toList()
    }

    /**
     * [words] with the untranslated words between the chosen ones filled in —
     * the Greek article in "with God", which no English word renders. Only
     * positions no link renders are added; a word the version puts somewhere
     * else stays out.
     */
    fun filledInterior(words: List<Int>, linked: Set<Int>): List<Int> {
        val low = words.minOrNull() ?: return emptyList()
        val high = words.maxOrNull() ?: return emptyList()
        val result = words.toSortedSet()
        for (index in (low + 1) until high) {
            if (index !in linked) result.add(index)
        }
        return result.toList()
    }

    /**
     * Where a set of original words sits in one version's verse: the ranges of
     * every link that renders any of them, joined across gaps that hold only
     * spaces, punctuation and words the version leaves unlinked. Scattered
     * function words alone ("the … of … and") are what a weak link looks
     * like, so a set that comes to nothing more gives no ranges and the caller
     * marks the whole verse; a single run like "who is" is kept.
     */
    fun ranges(words: Set<Int>, links: List<AlignmentLink>, text: String): List<TextRange> {
        val hits = links
            .filter { link -> link.words.any { it in words } }
            .sortedBy { it.start }
        val result = mutableListOf<TextRange>()
        for (link in hits) {
            val last = result.lastOrNull()
            if (last != null && links.none { it.start < link.start && it.end > last.end }) {
                result[result.size - 1] = TextRange(last.start, maxOf(last.end, link.end))
            } else {
                result.add(TextRange(link.start, link.end))
            }
        }
        if (result.size >= 2) {
            // Poetry lines are glued with no space ("of itall the days"), so
            // the text alone can run two words into one and pass a function
            // word off as a content word. Every link edge was a word break
            // when the links were made, so the edges stand in for the span
            // breaks this function is not given.
            val edges = links.flatMap { listOf(it.start, it.end) }
            val inside = PivotAligner.tokens(text, edges).filter { token ->
                result.any { token.start < it.end && token.end > it.start }
            }
            if (inside.all { !it.isContent }) return emptyList()
        }
        return result
    }

    /**
     * The words a mark covers, worked out the moment it is made. Each end that
     * stops part-way through its verse gets the original words under its
     * part; an end that is a whole verse needs none. Without links or a
     * source, or where nothing is linked, that end is left without words and
     * readers on other versions see the whole verse there.
     */
    fun anchored(range: VerseRange, links: Map<Int, List<AlignmentLink>>?, source: String?): VerseRange {
        if (links == null || source == null || range.isWholeVerses) return range
        val single = range.startVerse == range.endVerse
        // Through the constructor, so the sets are normalised and a mark with
        // no words anywhere carries no source either.
        return VerseRange(
            bookID = range.bookID,
            chapter = range.chapter,
            startVerse = range.startVerse,
            endVerse = range.endVerse,
            startChar = range.startChar,
            endChar = range.endChar,
            charTranslation = range.charTranslation,
            startWords = derived(range, range.startVerse, links),
            endWords = if (single) null else derived(range, range.endVerse, links),
            wordsSource = source,
        )
    }

    /** Whether the mark stops part-way through [verse]. */
    internal fun isPartial(range: VerseRange, verse: Int): Boolean {
        val single = range.startVerse == range.endVerse
        if (verse == range.startVerse) return range.startChar != null || (single && range.endChar != null)
        if (verse == range.endVerse) return range.endChar != null
        return false
    }

    /**
     * The author's offsets at [verse]: the start verse runs from its start
     * offset (to the end offset, inside one verse); the end verse runs to its
     * end offset.
     */
    internal fun offsets(range: VerseRange, verse: Int): Pair<Int?, Int?> {
        val single = range.startVerse == range.endVerse
        if (verse == range.startVerse) return range.startChar to (if (single) range.endChar else null)
        if (verse == range.endVerse) return null to range.endChar
        return null to null
    }

    /** The words under the author's part of [verse], or null when there are none to be had. */
    internal fun derived(range: VerseRange, verse: Int, links: Map<Int, List<AlignmentLink>>?): List<Int>? {
        if (!isPartial(range, verse)) return null
        val verseLinks = links?.get(verse) ?: return null
        val (from, to) = offsets(range, verse)
        val chosen = words(verseLinks, from, to)
        val filled = filledInterior(chosen, linked(verseLinks))
        return filled.ifEmpty { null }
    }

    /**
     * Where one reader sees a mark, verse by verse.
     *
     * A verse the mark covers whole is whole. Where it stops part-way: a
     * reader on the author's version sees the author's exact phrase; anyone
     * else sees whatever their version says for the same original words — the
     * words stored with the mark when its source is the one bundled, or else
     * the words under the author's offsets in the author's version
     * ([authorLinks]), which is how a mark made before marks carried words
     * still follows them. Where neither is to be had, or the reader's version
     * has no links for the verse, or the words come to nothing on their page,
     * the reader sees the whole verse.
     */
    fun resolve(
        range: VerseRange,
        reader: TranslationID,
        readerLinks: Map<Int, List<AlignmentLink>>?,
        readerTexts: Map<Int, String>,
        source: String?,
        authorLinks: Map<Int, List<AlignmentLink>>?,
    ): List<MarkedSpan> {
        val spans = mutableListOf<MarkedSpan>()
        for (verse in range.startVerse..range.endVerse) {
            if (!isPartial(range, verse)) {
                spans.add(MarkedSpan(verse))
                continue
            }
            if (range.charTranslation == reader) {
                val (from, to) = offsets(range, verse)
                spans.add(MarkedSpan(verse, from, to))
                continue
            }
            val stored = if (verse == range.startVerse) range.startWords else range.endWords
            val set = if (stored != null && source != null && range.wordsSource == source) {
                stored
            } else {
                derived(range, verse, authorLinks)
            }
            val links = readerLinks?.get(verse)
            val text = readerTexts[verse]
            if (set != null && links != null && text != null) {
                val found = ranges(set.toSet(), links, text)
                if (found.isNotEmpty()) {
                    found.forEach { spans.add(MarkedSpan(verse, it.start, it.end)) }
                    continue
                }
            }
            spans.add(MarkedSpan(verse))
        }
        return spans
    }

    /**
     * What a version says for one original word: the text of every link that
     * renders it, joined with " … " where the version splits it.
     */
    fun rendering(word: Int, links: List<AlignmentLink>, text: String): String? {
        val pieces = links
            .filter { word in it.words }
            .sortedBy { it.start }
            .map { slice(text, it.start, it.end) }
            .filter { it.isNotEmpty() }
        return if (pieces.isEmpty()) null else pieces.joinToString(" … ")
    }

    /** The words from the first range's start to the last range's end. */
    fun phrase(ranges: List<TextRange>, text: String): String {
        val first = ranges.firstOrNull() ?: return ""
        val last = ranges.last()
        return slice(text, first.start, last.end)
    }

    // Following lands on the same words. A follower hears where the person
    // they follow is reading as a verse and how far down it their reading
    // line is (A58), and has always set that fraction against their own
    // page. In one version that is exact; across two it drifts, because the
    // versions put the words in a different order and take a different
    // length to say them. "Through Him all things were made" and "All
    // things were made through him" are the same verse with its first words
    // at opposite ends. So the person being followed also says which
    // original word is under their line, and the follower goes to wherever
    // their own version says that word. It adds onto the guess at where
    // someone is reading, as the owner put it, and brings everyone to the
    // same words, not just the same share of the verse.

    /** How far past the line a link may start and still be the word under
     * it: the space and the comma between two words, no more. */
    internal const val WORD_REACH = 2

    /**
     * The original word under a reading line [part] of the way down a verse,
     * on the page of the person being followed: the link under that point, or
     * one that starts just after it (the line on the space or the comma before
     * a word), and the first of its words. Null for a verse with no text or no
     * links, and null when the line sits in a stretch the links leave out —
     * the follower then keeps the fraction. A licensed version is linked
     * through the Berean Standard and leaves four words in ten out, and the
     * next link along can be a line further on: tried on the KJV against the
     * Berean Standard, sending it put the follower eight or more words ahead
     * twice as often as the fraction did.
     */
    fun word(part: Double, text: String, links: List<AlignmentLink>): Int? {
        val length = text.length
        if (length == 0 || links.isEmpty()) return null
        val offset = floor(fraction(part) * length.toDouble()).toInt()
        val sorted = links.sortedBy { it.start }
        val link = sorted.firstOrNull { it.end > offset }
        if (link != null) {
            if (link.start - offset > WORD_REACH) return null
            return link.words.minOrNull()
        }
        // Past every link: the last, if the line is on what follows it.
        val last = sorted.last()
        if (offset - last.end > WORD_REACH) return null
        return last.words.minOrNull()
    }

    /**
     * How far down a verse one original word sits on the follower's page:
     * where the first link that renders it starts. A word this version leaves
     * unsaid is placed at the next word up that it does say, so the line
     * lands just after it rather than nowhere. Null when the version says no
     * word from there to the verse's end, or the verse has no text.
     */
    fun part(word: Int, text: String, links: List<AlignmentLink>): Double? {
        val length = text.length
        if (length == 0) return null
        val sorted = links.sortedBy { it.start }
        val link = sorted.firstOrNull { word in it.words }
            ?: sorted
                .mapNotNull { link -> link.words.minOrNull()?.let { it to link } }
                .filter { it.first >= word }
                .minByOrNull { it.first }?.second
            ?: return null
        return fraction(link.start.toDouble() / length.toDouble())
    }

    /**
     * A heard reading point, set in the follower's version. When the person
     * followed reads another version and said which original word was under
     * their line — counted in the numbering bundled here — the point moves to
     * where that word is on the follower's page. Otherwise it comes back as
     * it was heard: on the same version the fraction is already exact, and
     * without the word, the links or the text there is nothing truer to put
     * in its place.
     */
    fun carried(
        point: ReadingPoint,
        word: Int?,
        wordsSource: String?,
        from: TranslationID?,
        to: TranslationID,
        source: String?,
        links: List<AlignmentLink>?,
        text: String?,
    ): ReadingPoint {
        if (from == null || from == to) return point
        if (word == null || source == null || wordsSource != source) return point
        if (links == null || text == null) return point
        val part = part(word, text, links) ?: return point
        return point.copy(part = part)
    }

    /**
     * [part] held to 0..1. A fraction that is not a number reads as the top
     * of the verse rather than reaching the arithmetic.
     */
    internal fun fraction(part: Double): Double = if (part.isNaN()) 0.0 else part.coerceIn(0.0, 1.0)

    /** `text[from, to)` in UTF-16 units, clamped to the text. */
    internal fun slice(text: String, from: Int, to: Int): String {
        val low = from.coerceIn(0, text.length)
        val high = to.coerceIn(low, text.length)
        return text.substring(low, high)
    }
}

// The tuple shapes. Each reads a JSON array and writes one back, so a value
// round-trips to the bytes the pipeline wrote.

private fun Decoder.array(what: String): JsonArray {
    val json = this as? JsonDecoder ?: throw SerializationException("$what decodes from JSON only")
    return json.decodeJsonElement().jsonArray
}

private fun Encoder.json(what: String): JsonEncoder =
    this as? JsonEncoder ?: throw SerializationException("$what encodes to JSON only")

internal object OriginalWordSerializer : KSerializer<OriginalWord> {
    override val descriptor: SerialDescriptor = SerialDescriptor("app.readribbon.core.OriginalWord", JsonArray.serializer().descriptor)

    override fun deserialize(decoder: Decoder): OriginalWord {
        val a = decoder.array("OriginalWord")
        fun at(i: Int): String? = a.getOrNull(i)?.jsonPrimitive?.content
        return OriginalWord.of(
            text = at(0) ?: throw SerializationException("an original word needs its text"),
            translit = at(1) ?: "",
            strongs = at(2),
            parse = at(3),
            isAramaic = at(4) == "a",
        )
    }

    override fun serialize(encoder: Encoder, value: OriginalWord) {
        encoder.json("OriginalWord").encodeJsonElement(buildJsonArray {
            add(JsonPrimitive(value.text))
            add(JsonPrimitive(value.translit))
            add(JsonPrimitive(value.strongs ?: ""))
            add(JsonPrimitive(value.parse ?: ""))
            if (value.isAramaic) add(JsonPrimitive("a"))
        })
    }
}

internal object LexiconEntrySerializer : KSerializer<LexiconEntry> {
    override val descriptor: SerialDescriptor = SerialDescriptor("app.readribbon.core.LexiconEntry", JsonArray.serializer().descriptor)

    override fun deserialize(decoder: Decoder): LexiconEntry {
        val a = decoder.array("LexiconEntry")
        fun at(i: Int): String? = a.getOrNull(i)?.jsonPrimitive?.content
        return LexiconEntry(
            lemma = at(0) ?: throw SerializationException("a dictionary entry needs its lemma"),
            translit = at(1) ?: "",
            definition = at(2) ?: "",
        )
    }

    override fun serialize(encoder: Encoder, value: LexiconEntry) {
        encoder.json("LexiconEntry").encodeJsonElement(buildJsonArray {
            add(JsonPrimitive(value.lemma))
            add(JsonPrimitive(value.translit))
            add(JsonPrimitive(value.definition))
        })
    }
}

internal object AlignmentLinkSerializer : KSerializer<AlignmentLink> {
    override val descriptor: SerialDescriptor = SerialDescriptor("app.readribbon.core.AlignmentLink", JsonArray.serializer().descriptor)

    override fun deserialize(decoder: Decoder): AlignmentLink {
        val a = decoder.array("AlignmentLink")
        if (a.size < 3) throw SerializationException("a link is [start, end, [words]]")
        return AlignmentLink(
            start = a[0].jsonPrimitive.int,
            end = a[1].jsonPrimitive.int,
            words = a[2].jsonArray.map { it.jsonPrimitive.int },
        )
    }

    override fun serialize(encoder: Encoder, value: AlignmentLink) {
        encoder.json("AlignmentLink").encodeJsonElement(buildJsonArray {
            add(JsonPrimitive(value.start))
            add(JsonPrimitive(value.end))
            add(buildJsonArray { value.words.forEach { add(JsonPrimitive(it)) } })
        })
    }
}

internal object LexiconSerializer : KSerializer<Lexicon> {
    private val map = MapSerializer(String.serializer(), LexiconEntrySerializer)
    override val descriptor: SerialDescriptor = SerialDescriptor("app.readribbon.core.Lexicon", map.descriptor)

    override fun deserialize(decoder: Decoder): Lexicon = Lexicon(map.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Lexicon) = map.serialize(encoder, value.entries)
}

internal object ParsingsSerializer : KSerializer<Parsings> {
    private val map = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("app.readribbon.core.Parsings", map.descriptor)

    override fun deserialize(decoder: Decoder): Parsings = Parsings(map.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Parsings) = map.serialize(encoder, value.long)
}
