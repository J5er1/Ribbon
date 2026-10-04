package app.readribbon.data

import android.content.Context
import app.readribbon.core.AlignmentLink
import app.readribbon.core.BookAlignment
import app.readribbon.core.Lexicon
import app.readribbon.core.OriginalBook
import app.readribbon.core.OriginalWords
import app.readribbon.core.Parsings
import app.readribbon.core.PivotAligner
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.TranslationID
import app.readribbon.core.TranslationRegistry
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// The original words under the English, and the links from each version to
// them (A60). They ship beside the Scripture they describe — one Hebrew,
// Aramaic or Greek file per book under `scripture/original/`, one file of
// links per book per version that has them under `scripture/align/<id>/` —
// and are loaded the way the text is: lazily, once, from the assets.
//
// A version with no links of its own (the licensed three) is linked on the
// phone, through the Berean Standard's, from the chapter the phone already
// holds. Nothing worked out that way is stored or sent anywhere: it is a
// reading of text the license lets the phone show, and goes when the app
// does.

class OriginalStore(context: Context, private val scripture: ScriptureStore) {

    private val assets = context.applicationContext.assets

    // A book's original words can run past a megabyte and a half of JSON,
    // and a reader is in one book at a time, so these are held a few at a
    // time rather than all kept — unlike the text, which is smaller and read
    // from every surface.
    private val originals = object : LinkedHashMap<String, OriginalBook>(ORIGINAL_BOOKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OriginalBook>?): Boolean =
            size > ORIGINAL_BOOKS
    }

    // Links are the size of the text they describe, and are kept as it is.
    private val alignments = ConcurrentHashMap<String, BookAlignment>()

    // Each chapter's links by verse, the shape every caller wants, whether
    // they came off the disk or out of the pivot.
    private val chapterLinks = ConcurrentHashMap<String, Map<Int, List<AlignmentLink>>>()

    @Volatile private var knownSource: String? = null
    @Volatile private var loadedLexicon: Lexicon? = null
    @Volatile private var loadedParsings: Parsings? = null

    /** One book's original words (`scripture/original/<BOOK>.json`). */
    fun original(bookID: String): OriginalBook? {
        synchronized(originals) { originals[bookID] }?.let { return it }
        val book = read<OriginalBook>("$ORIGINAL/$bookID.json") ?: return null
        knownSource = knownSource ?: book.source
        synchronized(originals) { originals[bookID] = book }
        return book
    }

    /**
     * One version's links for one book, for a version that ships them
     * (`scripture/align/<id>/<BOOK>.json`). Null for any other.
     */
    fun alignment(bookID: String, translation: TranslationID): BookAlignment? {
        if (!TranslationRegistry.hasBundledWordLinks(translation)) return null
        val key = "${translation.rawValue}/$bookID"
        alignments[key]?.let { return it }
        val alignment = read<BookAlignment>("$ALIGN/$key.json") ?: return null
        knownSource = knownSource ?: alignment.source
        return alignment.also { alignments[key] = it }
    }

    /** Strong's dictionary, read the first time it is asked for. */
    val lexicon: Lexicon?
        get() = loadedLexicon ?: read<Lexicon>("$ORIGINAL/strongs.json")?.also { loadedLexicon = it }

    /** The grammar codes' long forms, read the first time they are asked for. */
    val parsings: Parsings?
        get() = loadedParsings ?: read<Parsings>("$ORIGINAL/parsing.json")?.also { loadedParsings = it }

    /**
     * The dictionary if it has been read, and null rather than reading it —
     * for the main thread, which must not pay for a megabyte of JSON while a
     * panel opens. The panel reads it off the main thread and fills in.
     */
    val lexiconIfRead: Lexicon? get() = loadedLexicon

    /** The grammar codes if they have been read; see [lexiconIfRead]. */
    val parsingsIfRead: Parsings? get() = loadedParsings

    /**
     * The numbering a word's position counts in. It is one key for every
     * book, so whichever file has been read already answers it; the Berean
     * Standard's links for [bookID] are read before its original words
     * because they are a fifth of the size.
     */
    fun source(bookID: String): String? =
        knownSource
            ?: alignment(bookID, TranslationID.bsb)?.source
            ?: original(bookID)?.source

    /**
     * One version's links for one chapter, by verse.
     *
     * The Berean Standard and the World English ship theirs. Any other
     * version is linked here from [readerChapter] — its text, as this phone
     * holds it — verse by verse against the Berean Standard's text and links
     * (`PivotAligner`), and remembered for the chapter. Without that text
     * there is nothing to link, and the answer is null: a mark is then the
     * whole verse, and a follow keeps its fraction.
     */
    fun links(
        translation: TranslationID,
        bookID: String,
        chapter: Int,
        readerChapter: ScriptureChapter?,
    ): Map<Int, List<AlignmentLink>>? {
        val key = "${translation.rawValue}/$bookID/$chapter"
        chapterLinks[key]?.let { return it }
        val links = if (TranslationRegistry.hasBundledWordLinks(translation)) {
            alignment(bookID, translation)?.chapter(chapter)?.byVerse
        } else {
            pivoted(bookID, chapter, readerChapter ?: return null)
        } ?: return null
        return links.also { chapterLinks[key] = it }
    }

    /**
     * A mark as it is made: [range] with the original words under each end
     * that stops part-way through its verse, measured in [translation] — the
     * author's own page (`OriginalWords.anchored`).
     */
    fun anchored(range: VerseRange, translation: TranslationID, readerChapter: ScriptureChapter?): VerseRange {
        if (range.isWholeVerses) return range
        return OriginalWords.anchored(
            range,
            links = links(translation, range.bookID, range.chapter, readerChapter),
            source = source(range.bookID),
        )
    }

    private fun pivoted(bookID: String, chapter: Int, reader: ScriptureChapter): Map<Int, List<AlignmentLink>>? {
        val pivot = scripture.chapter(VerseAddress(bookID, chapter, 1), TranslationID.bsb) ?: return null
        val pivotLinks = alignment(bookID, TranslationID.bsb)?.chapter(chapter)?.byVerse ?: return null
        val pivotTexts = pivot.ownTexts()
        val pivotBreaks = pivot.ownSpanBreaks()
        val readerBreaks = reader.ownSpanBreaks()
        val result = mutableMapOf<Int, List<AlignmentLink>>()
        for ((verse, text) in reader.ownTexts()) {
            val pivotText = pivotTexts[verse] ?: continue
            val links = pivotLinks[verse] ?: continue
            val aligned = PivotAligner.align(
                reader = text,
                readerBreaks = readerBreaks[verse].orEmpty(),
                pivot = pivotText,
                pivotBreaks = pivotBreaks[verse].orEmpty(),
                pivotLinks = links,
            )
            if (aligned.isNotEmpty()) result[verse] = aligned
        }
        return result
    }

    private inline fun <reified T> read(path: String): T? = runCatching {
        val text = assets.open(path).use { it.readBytes().decodeToString() }
        json.decodeFromString<T>(text)
    }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val ORIGINAL = "scripture/original"
        private const val ALIGN = "scripture/align"

        /** The book being read, the one before it, and one more. */
        private const val ORIGINAL_BOOKS = 3
    }
}
