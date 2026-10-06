@file:OptIn(ExperimentalLayoutApi::class)

package app.readribbon.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Room
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.TranslationRegistry
import app.readribbon.data.OriginalStore
import app.readribbon.data.ScriptureStore
import app.readribbon.services.cachedRemoteChapter
import app.readribbon.services.ensureRemoteChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.readribbon.core.AlignmentLink
import app.readribbon.core.Lexicon
import app.readribbon.core.OriginalChapter
import app.readribbon.core.OriginalLanguage
import app.readribbon.core.OriginalWord
import app.readribbon.core.OriginalWords
import app.readribbon.core.Parsings
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.core.displayName
import app.readribbon.design.Palette
import app.readribbon.design.RibbonFonts
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion

// The original, on the page (A60, §7). Selecting English shows the Hebrew,
// Aramaic or Greek words under exactly what was selected — the word, how to
// say it, what your version says for it — and one of those words opens its
// dictionary form, its definition, its grammar and its number. Under them,
// how the room's other versions say the same words, grouped by what they
// say (A62, RoomSection.kt).
//
// It is not a sheet and it is not glass over a verse. It is the foot of the
// page, the way the write and speak composers are: the toolbar cross-fades
// into it, the selection stays lifted above it with its handles live, and
// the panel follows the selection as the handles move. Tapping the text
// leaves it, the same way a composer is left, and the lift goes with it.
//
// Everything that decides *what* the panel says is a plain function below,
// over the core's pure moves, so it can be held to answers without a screen;
// the composables only set it.

/** Where a finger held a word: a verse, and an offset into its own text. */
@Immutable
data class HeldWord(val verse: Int, val offset: Int)

/**
 * One original word as the panel sets it: the word, and what your version
 * says for it — or the Berean Standard's, where yours folds it into another
 * word — or null where neither says it on its own.
 */
@Immutable
data class OriginalColumn(
    val verse: Int,
    val index: Int,
    val word: OriginalWord,
    val rendering: String?,
)

/** A verse's chosen words, in original order. */
@Immutable
data class OriginalVerseWords(
    val verse: Int,
    val columns: List<OriginalColumn>,
)

/**
 * What the panel shows for one selection.
 *
 * @property chosen per verse, the positions of the words the selection
 *   covers, in the bundled numbering — the same question every other
 *   version in the room is asked.
 * @property wholeVerse somewhere in the selection this version could not be
 *   matched word for word, so that verse's words are all shown, and the
 *   panel says so.
 */
@Immutable
data class OriginalReading(
    val range: VerseRange,
    val language: OriginalLanguage,
    val verses: List<OriginalVerseWords>,
    val chosen: Map<Int, List<Int>>,
    val wholeVerse: Boolean,
) {
    val isHebrew: Boolean get() = language != OriginalLanguage.greek
}

/**
 * The quiet line over the toolbar (§7.5): the held word, or the selection's
 * words, in the original, then how to say them, then — for one word — what
 * your version says for it.
 */
@Immutable
data class OriginalLine(
    val words: List<OriginalWord>,
    val rendering: String?,
    val language: OriginalLanguage,
    /**
     * The held word, as a verse and a position, whose detail the panel opens
     * on when the line or the verb opens it; null once the handles have moved.
     */
    val opens: Pair<Int, Int>? = null,
) {
    val isHebrew: Boolean get() = language != OriginalLanguage.greek
    val text: String get() = words.joinToString(" ") { it.text }
    val translit: String get() = words.joinToString(" ") { it.translit }

    /** The line read aloud: how to say it, and what it says. */
    val spoken: String get() = Copy.originalWordSpoken(translit, rendering ?: "", "")
}

// MARK: - What the panel says

/** Whether the selection stops part-way through [verse]. */
internal fun isPartial(range: VerseRange, verse: Int): Boolean {
    val single = range.startVerse == range.endVerse
    if (verse == range.startVerse) return range.startChar != null || (single && range.endChar != null)
    if (verse == range.endVerse) return range.endChar != null
    return false
}

/** The selection's own-text offsets at [verse]. */
internal fun offsets(range: VerseRange, verse: Int): Pair<Int?, Int?> {
    val single = range.startVerse == range.endVerse
    if (verse == range.startVerse) return range.startChar to (if (single) range.endChar else null)
    if (verse == range.endVerse) return null to range.endChar
    return null to null
}

/**
 * Hebrew, Aramaic or Greek, by the words themselves: the New Testament is
 * Greek; in the Old, the passages Daniel and Ezra keep in Aramaic are named
 * for it when most of the chosen words are.
 */
internal fun languageOf(bookID: String, words: List<OriginalWord>): OriginalLanguage {
    val book = OriginalWords.language(bookID)
    if (book == OriginalLanguage.greek || words.isEmpty()) return book
    val aramaic = words.count { it.isAramaic }
    return if (aramaic * 2 > words.size) OriginalLanguage.aramaic else OriginalLanguage.hebrew
}

/**
 * The positions of [verse]'s [count] words a selection covers, and whether
 * they are all of them only because this version cannot say which: a whole
 * verse is all its words; a part of one, the words under it in [verseLinks]
 * with the unrendered words between them filled in — or all of them, where
 * this version has no links for the verse or nothing in the part is linked.
 */
private fun chosenIn(
    range: VerseRange,
    verse: Int,
    count: Int,
    verseLinks: List<AlignmentLink>?,
): Pair<List<Int>, Boolean> {
    val all = (0 until count).toList()
    if (verseLinks == null) return all to true
    if (!isPartial(range, verse)) return all to false
    val (from, to) = offsets(range, verse)
    val under = OriginalWords.filledInterior(
        OriginalWords.words(verseLinks, from, to),
        OriginalWords.linked(verseLinks),
    ).filter { it in 0 until count }
    return if (under.isEmpty()) all to true else under to false
}

/**
 * The original words under a selection, verse by verse.
 *
 * A verse the selection covers whole gives all its words. One it stops
 * part-way through gives the words under that part in this version's links,
 * with the unrendered words between them filled in (the Greek article in
 * "with God"); where nothing in the part is linked, the verse's words are
 * all given — the panel does not guess which of them were meant. Either way,
 * where this version has no links for a verse, [OriginalReading.wholeVerse]
 * says so: the words it shows are said in the Berean Standard's, not yours.
 *
 * [links] and [texts] are this version's, for the chapter; [pivotLinks] and
 * [pivotTexts] the Berean Standard's, for the rendering of a word this
 * version folds into another. Null when the chapter has no original words.
 */
internal fun readOriginal(
    range: VerseRange,
    original: OriginalChapter,
    links: Map<Int, List<AlignmentLink>>?,
    texts: Map<Int, String>,
    pivotLinks: Map<Int, List<AlignmentLink>>?,
    pivotTexts: Map<Int, String>,
    breaks: Map<Int, List<Int>> = emptyMap(),
    pivotBreaks: Map<Int, List<Int>> = emptyMap(),
): OriginalReading? {
    val verses = mutableListOf<OriginalVerseWords>()
    val chosen = linkedMapOf<Int, List<Int>>()
    var whole = false
    for (verse in range.verses) {
        val words = original.words(verse) ?: continue
        if (words.isEmpty()) continue
        val verseLinks = links?.get(verse)?.takeIf { it.isNotEmpty() }
        // A whole verse is all its words either way; but where this version
        // has no links for it, what each word is said as is the Berean
        // Standard's rather than yours, and the panel says so.
        val (picked, fallback) = chosenIn(range, verse, words.size, verseLinks)
        if (fallback) whole = true
        val text = texts[verse]
        val pivotText = pivotTexts[verse]
        val pivot = pivotLinks?.get(verse)
        chosen[verse] = picked
        verses += OriginalVerseWords(
            verse = verse,
            columns = picked.map { index ->
                val mine = if (verseLinks != null && text != null) {
                    renderingOf(index, verseLinks, text, breaks[verse].orEmpty())
                } else {
                    null
                }
                val theirs = if (mine == null && pivot != null && pivotText != null) {
                    renderingOf(index, pivot, pivotText, pivotBreaks[verse].orEmpty())
                } else {
                    null
                }
                OriginalColumn(verse, index, words[index], (mine ?: theirs)?.trim())
            },
        )
    }
    if (verses.isEmpty()) return null
    return OriginalReading(
        range = range,
        language = languageOf(range.bookID, verses.flatMap { v -> v.columns.map { it.word } }),
        verses = verses,
        chosen = chosen,
        wholeVerse = whole,
    )
}

/**
 * The line over the toolbar (§7.5).
 *
 * Held: the word under the finger — the link the press point falls in on this
 * version's own text — and nothing at all when no link is there, so the line
 * stays quiet rather than naming a neighbour. Once the handles have moved
 * ([held] is null): the selection's words in original order, from the verses
 * this version links. The line never names a verse this version has no links
 * for — a licensed chapter before it has arrived — because there it could
 * only be wrong.
 */
internal fun originalLine(
    range: VerseRange,
    held: HeldWord?,
    original: OriginalChapter,
    links: Map<Int, List<AlignmentLink>>?,
    texts: Map<Int, String>,
    breaks: Map<Int, List<Int>> = emptyMap(),
): OriginalLine? {
    if (links == null) return null
    val picked = mutableListOf<Pair<Int, Int>>()
    if (held != null && held.verse in range.verses) {
        val verseLinks = links[held.verse] ?: return null
        // The press point is a caret between two letters, so a finger on a
        // word's last letter can land just past it; the letter before the
        // caret is still the word under the finger.
        val under = OriginalWords.words(verseLinks, held.offset, held.offset + 1)
            .ifEmpty { OriginalWords.words(verseLinks, held.offset - 1, held.offset) }
        under.forEach { picked += held.verse to it }
    } else {
        for (verse in range.verses) {
            val words = original.words(verse) ?: continue
            // A verse this version cannot match word for word adds nothing:
            // the line names what links and is quiet about the rest.
            val verseLinks = links[verse]?.takeIf { it.isNotEmpty() } ?: continue
            val indices = if (!isPartial(range, verse)) {
                words.indices.toList()
            } else {
                val (from, to) = offsets(range, verse)
                OriginalWords.filledInterior(
                    OriginalWords.words(verseLinks, from, to),
                    OriginalWords.linked(verseLinks),
                )
            }
            indices.forEach { picked += verse to it }
        }
    }
    val words = picked.mapNotNull { (verse, index) -> original.words(verse)?.getOrNull(index) }
    if (words.isEmpty()) return null
    // Held, the panel opens on the word under the finger, as the line names it.
    val opens = if (held == null) {
        null
    } else {
        picked.firstOrNull { (verse, index) -> original.words(verse)?.getOrNull(index) != null }
    }
    val rendering = picked.singleOrNull()?.let { (verse, index) ->
        val verseLinks = links[verse]
        val text = texts[verse]
        if (verseLinks != null && text != null) renderingOf(index, verseLinks, text, breaks[verse].orEmpty())?.trim() else null
    }
    return OriginalLine(words, rendering, languageOf(range.bookID, words), opens)
}

/**
 * `text[from, to)` as a reader would see it: a line of poetry is glued to
 * the next in a verse's own text ("O LORD?Who is like You"), because the
 * page breaks the line instead; here, out of the page, the break becomes a
 * space.
 */
internal fun spoken(text: String, from: Int, to: Int, breaks: List<Int>): String {
    val low = from.coerceIn(0, text.length)
    val high = to.coerceIn(low, text.length)
    val out = StringBuilder()
    for (i in low until high) {
        if (i > low && i in breaks && !text[i - 1].isWhitespace() && !text[i].isWhitespace()) out.append(' ')
        out.append(text[i])
    }
    return out.toString()
}

/**
 * What a version says for one original word: [OriginalWords.rendering], with
 * each piece read the way [spoken] reads it, so a word whose rendering runs
 * across a line of poetry ("O LORD? Who") is not glued at the break.
 */
private fun renderingOf(word: Int, links: List<AlignmentLink>, text: String, breaks: List<Int>): String? {
    val pieces = links
        .filter { word in it.words }
        .sortedBy { it.start }
        .map { spoken(text, it.start, it.end, breaks).trim() }
        .filter { it.isNotEmpty() }
    return if (pieces.isEmpty()) null else pieces.joinToString(" … ")
}

// MARK: - From the phone's own stores

/**
 * What the toolbar needs to know about the original under the lift: the verb
 * — named for the language — and the line over the inks. Null where this
 * phone has no original words for the chapter.
 */
@Immutable
data class OriginalUnderLift(
    val verb: String,
    val line: OriginalLine?,
)

/**
 * The toolbar's verb and line for [range], read on [translation]'s page
 * ([content] is that page's chapter, [texts] its verses' own text, which the
 * caller keeps for the chapter rather than rebuilding it on every step of a
 * handle). Map lookups once the book is warm (the room warms it as a book
 * opens), so it is cheap enough to ask as the handles move.
 */
internal fun originalUnderLift(
    original: OriginalStore,
    range: VerseRange,
    held: HeldWord?,
    translation: TranslationID,
    content: ScriptureChapter,
    texts: Map<Int, String> = content.ownTexts(),
): OriginalUnderLift? {
    val chapter = original.original(range.bookID)?.chapter(range.chapter) ?: return null
    val links = original.links(translation, range.bookID, range.chapter, content)
    // Named for the words the panel will show, as its heading is: a part of
    // Daniel 2:4 can be Hebrew in a verse that is mostly Aramaic.
    val words = range.verses.flatMap { verse ->
        val all = chapter.words(verse).orEmpty()
        val verseLinks = links?.get(verse)?.takeIf { it.isNotEmpty() }
        chosenIn(range, verse, all.size, verseLinks).first.map { all[it] }
    }
    if (words.isEmpty()) return null
    return OriginalUnderLift(
        verb = Copy.originalVerb(languageOf(range.bookID, words)),
        line = originalLine(range, held, chapter, links, texts, content.ownSpanBreaks()),
    )
}

/**
 * The panel's words for [range] on [translation]'s page, with the Berean
 * Standard's renderings standing in where this version folds a word into
 * another. Asked only while the panel is open: it reads a second version.
 */
internal fun originalReadingOf(
    original: OriginalStore,
    scripture: ScriptureStore,
    range: VerseRange,
    translation: TranslationID,
    content: ScriptureChapter,
    texts: Map<Int, String> = content.ownTexts(),
): OriginalReading? {
    val chapter = original.original(range.bookID)?.chapter(range.chapter) ?: return null
    val links = original.links(translation, range.bookID, range.chapter, content)
    val pivot = if (translation == TranslationID.bsb) {
        content
    } else {
        scripture.chapter(VerseAddress(range.bookID, range.chapter, 1), TranslationID.bsb)
    }
    val pivotLinks = pivot?.let { original.links(TranslationID.bsb, range.bookID, range.chapter, it) }
    return readOriginal(
        range = range,
        original = chapter,
        links = links,
        texts = texts,
        pivotLinks = pivotLinks,
        pivotTexts = pivot?.ownTexts().orEmpty(),
        breaks = content.ownSpanBreaks(),
        pivotBreaks = pivot?.ownSpanBreaks().orEmpty(),
    )
}

// MARK: - The line

/** Isolates a run of another direction inside a line (FSI … PDI). */
private fun isolated(text: String): String = "\u2068$text\u2069"

/**
 * The original line, directly above the inks (§7.5): `λόγος · logos · Word`.
 * One line, truncating; a button that opens the panel for the current
 * selection, labelled with what it says and what it does.
 *
 * It sits on the toolbar's own material, hugging what it says, because it
 * is drawn over the page: bare, the verses ran straight through it, and a
 * muted Hebrew word with its points under a line of Literata could not be
 * read (A66). The original word is set larger than the words around it and
 * at full strength — Hebrew a step larger again, for its points; how to say
 * it and what your version says stay muted beside it.
 */
@Composable
fun OriginalLineView(
    line: OriginalLine,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalSize = if (line.isHebrew) LINE_HEBREW_SIZE else LINE_GREEK_SIZE
    val originalFamily = remember(line.isHebrew) {
        if (line.isHebrew) RibbonFonts.hebrew else RibbonFonts.literata(FontWeight.Normal, originalSize)
    }
    val italicFamily = remember { RibbonFonts.literata(FontWeight.Normal, 14f, italic = true) }
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = originalFamily, fontSize = originalSize.sp, color = Palette.text)) {
            append(isolated(line.text))
        }
        append("  ·  ")
        withStyle(SpanStyle(fontFamily = italicFamily, fontStyle = FontStyle.Italic)) {
            append(isolated(line.translit))
        }
        line.rendering?.let {
            append("  ·  ")
            append(isolated(it))
        }
    }
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .ribbonGlass(CircleShape)
            .clickable(role = Role.Button, onClickLabel = Copy.ORIGINAL_LINE_ACTION, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = line.spoken
                role = Role.Button
                onClick(label = Copy.ORIGINAL_LINE_ACTION) { onOpen(); true }
            }
            // Small to read, a finger's height to take (§11).
            .sizeIn(minHeight = 44.dp)
            .padding(horizontal = 18.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // The line's height is the faces' own, not the interface's: a
            // Hebrew word's points reach past a 14 sp line.
            style = RibbonType.ui(14f).copy(lineHeight = TextUnit.Unspecified, textDirection = TextDirection.Ltr),
            color = Palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The held word in the line: Greek in Literata, Hebrew a step larger for its points. */
internal const val LINE_GREEK_SIZE = 18f
internal const val LINE_HEBREW_SIZE = 21f

// MARK: - The panel

/** The panel's ceiling, as a share of the screen: the page stays the page. */
private const val PANEL_MAX_SHARE = 0.55f

/** The original words are set this much larger than the body (§7.2). */
private const val ORIGINAL_SCALE = 1.35f

/** The body size the panel's words are scaled from. */
private const val PANEL_BODY = 17f

/**
 * The original words for the lifted selection (§7.2).
 *
 * @param reading what the selection covers, worked out by [readOriginal].
 * @param versionName your version's display name, for the whole-verse line.
 * @param lexicon Strong's dictionary, once read; a word's detail waits for it.
 * @param parsings the grammar codes' long forms, once read.
 * @param room how the room's versions say the words, grouped ([roomSectionOf]);
 *   null while that is being worked out. It says nothing when everyone
 *   reads one version — your own words are already in the row above.
 * @param initiallyOpen a word whose detail is open to begin with: the held
 *   word the line named, when the line or the verb opened the panel (§7.5).
 * @param scroll where the panel is scrolled to, past its 55% ceiling.
 */
@Composable
fun OriginalPanel(
    reading: OriginalReading,
    versionName: String,
    lexicon: Lexicon?,
    parsings: Parsings?,
    room: RoomSection?,
    modifier: Modifier = Modifier,
    initiallyOpen: Pair<Int, Int>? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize.height
    val ceiling = with(density) { (screen * PANEL_MAX_SHARE).toDp() }
        .takeIf { it > 0.dp } ?: 480.dp

    // One word open at a time. Kept by its verse and position, so it stays
    // open while the handles move and the word is still in the selection,
    // and closes on its own when the selection moves off it.
    var open by remember(initiallyOpen) { mutableStateOf(initiallyOpen) }
    val openWord = open?.takeIf { (verse, index) ->
        reading.verses.any { v -> v.verse == verse && v.columns.any { it.index == index } }
    }

    Column(
        modifier = modifier
            .readableColumn()
            .padding(horizontal = 16.dp)
            .heightIn(max = ceiling)
            .background(Palette.surface, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.rule, RoundedCornerShape(14.dp))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallCaps(
            Copy.originalHeading(reading.language, reading.range.formatted),
            size = 12f,
            modifier = Modifier.semantics { heading() },
        )

        val several = reading.verses.size > 1
        for (verse in reading.verses) key(verse.verse) {
            if (several) {
                SmallCaps(
                    VerseAddress(reading.range.bookID, reading.range.chapter, verse.verse).formatted,
                    size = 11f,
                    color = Palette.muted.copy(alpha = 0.8f),
                )
            }
            WordRow(
                verse = verse,
                hebrew = reading.isHebrew,
                parsings = parsings,
                open = openWord,
                onToggle = { index ->
                    val key = verse.verse to index
                    open = if (openWord == key) null else key
                },
            )
            val here = openWord?.takeIf { it.first == verse.verse }
            val column = here?.let { (_, index) -> verse.columns.firstOrNull { it.index == index } }
            // Held through the close, so the detail has something to draw
            // while it settles away.
            val shown = remember { mutableStateOf(column) }
            if (column != null) shown.value = column
            AnimatedVisibility(
                visible = column != null,
                enter = fadeIn(RibbonMotion.settle(reduceMotion)) +
                    expandVertically(RibbonMotion.settle(reduceMotion)),
                exit = fadeOut(RibbonMotion.settle(reduceMotion)) +
                    shrinkVertically(RibbonMotion.settle(reduceMotion)),
            ) {
                shown.value?.let { WordDetail(it, reading.isHebrew, lexicon, parsings) }
            }
        }

        if (reading.wholeVerse) {
            Text(
                text = Copy.originalWholeVerse(versionName),
                style = RibbonType.ui(14f),
                color = Palette.muted,
            )
        }

        // How the room's other versions say the same words (A62, §13.3):
        // grouped by what they say, yours first. A phrase is set large, as
        // it is on the page; a whole verse, or more, a step smaller.
        if (room != null) {
            val range = reading.range
            val phrase = range.startVerse == range.endVerse && isPartial(range, range.startVerse)
            InThisRoom(room, wordsSize = if (phrase) 19f else 16f)
        }
    }
}

/**
 * One verse's words, each a small column, in original order — right to left
 * for Hebrew and Aramaic, so the first word is the rightmost, as it is on
 * the page it came from.
 */
@Composable
private fun WordRow(
    verse: OriginalVerseWords,
    hebrew: Boolean,
    parsings: Parsings?,
    open: Pair<Int, Int>?,
    onToggle: (Int) -> Unit,
) {
    val direction = if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (column in verse.columns) key(column.index) {
                WordColumn(
                    column = column,
                    hebrew = hebrew,
                    grammar = column.word.parse?.let { parsings?.describe(it) },
                    selected = open == (verse.verse to column.index),
                    onClick = { onToggle(column.index) },
                )
            }
        }
    }
}

/**
 * One original word: the word, how to say it, and what your version says
 * for it. One element to a screen reader — "logos. Word. Noun - Nominative
 * Masculine Singular." — and a button that opens its detail.
 */
@Composable
private fun WordColumn(
    column: OriginalColumn,
    hebrew: Boolean,
    grammar: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rendering = column.rendering ?: Copy.ORIGINAL_NOT_ON_ITS_OWN
    val spoken = Copy.originalWordSpoken(column.word.translit, rendering, grammar ?: "")
    Column(
        modifier = Modifier
            .widthIn(min = 44.dp, max = 132.dp)
            .sizeIn(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                this.selected = selected
                onClick { onClick(); true }
            }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // Each line says which way it runs. The row is laid out right to
        // left for Hebrew, and a transliteration that opens on a neutral
        // mark ("‘ō·śêh") or ends on one ("mî-") would otherwise take the
        // row's direction and set its punctuation at the wrong end.
        Text(
            text = column.word.text,
            style = RibbonType.original(PANEL_BODY * ORIGINAL_SCALE, hebrew).copy(
                textDirection = if (hebrew) TextDirection.Rtl else TextDirection.Ltr,
            ),
            color = Palette.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = column.word.translit,
            style = RibbonType.scriptureItalic(13f).copy(textDirection = TextDirection.Ltr),
            color = Palette.muted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = rendering,
            style = RibbonType.ui(13f).copy(
                fontStyle = if (column.rendering == null) FontStyle.Italic else FontStyle.Normal,
                textDirection = TextDirection.Ltr,
            ),
            color = if (column.rendering == null) Palette.muted else Palette.text.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
        // The chosen word is marked the way the selection's handles are: in
        // the app's own accent, which is nobody's ink.
        Crossfade(
            targetState = selected,
            animationSpec = tween(RibbonMotion.ARRIVE_MS, easing = RibbonMotion.EaseOut),
            label = "chosen-word",
        ) { chosen ->
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(18.dp)
                    .height(1.5.dp)
                    .background(if (chosen) Palette.accent else Palette.accent.copy(alpha = 0f)),
            )
        }
    }
}

/**
 * A word's detail, under its row: its dictionary form and how to say that,
 * Strong's own definition, the grammar of this occurrence, and the number,
 * small. Nothing about how often it occurs.
 */
@Composable
private fun WordDetail(
    column: OriginalColumn,
    hebrew: Boolean,
    lexicon: Lexicon?,
    parsings: Parsings?,
) {
    val strongs = column.word.strongs
    val entry = strongs?.let { lexicon?.entry(it) }
    val grammar = column.word.parse?.let { parsings?.describe(it) }
    val lemmaFamily = remember(hebrew) {
        if (hebrew) RibbonFonts.hebrew else RibbonFonts.literata(FontWeight.Normal, 20f)
    }
    val italicFamily = remember { RibbonFonts.literata(FontWeight.Normal, 15f, italic = true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.rule, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val lemma = entry?.lemma ?: column.word.text
        val say = entry?.translit ?: column.word.translit
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = lemmaFamily, fontSize = 20.sp)) {
                    append(isolated(lemma))
                }
                append("  ·  ")
                withStyle(SpanStyle(fontFamily = italicFamily, fontStyle = FontStyle.Italic, color = Palette.muted)) {
                    append(isolated(say))
                }
            },
            style = RibbonType.ui(15f).copy(
                textDirection = TextDirection.Ltr,
                lineHeight = 32.sp,
            ),
            color = Palette.text,
        )
        entry?.definition?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = RibbonType.ui(15f), color = Palette.text)
        }
        grammar?.let {
            Text(text = it, style = RibbonType.ui(14f), color = Palette.muted)
        }
        strongs?.let {
            SmallCaps(Copy.originalStrongs(it), size = 11f, color = Palette.muted.copy(alpha = 0.8f))
        }
    }
}
