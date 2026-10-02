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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
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
// how each version read in this room says the same words, and who reads it.
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
) {
    val isHebrew: Boolean get() = language != OriginalLanguage.greek
    val text: String get() = words.joinToString(" ") { it.text }
    val translit: String get() = words.joinToString(" ") { it.translit }

    /** The line read aloud: how to say it, and what it says. */
    val spoken: String get() = Copy.originalWordSpoken(translit, rendering ?: "", "")
}

/**
 * One version read in this room, and how it says the selected words.
 *
 * @property words the phrase, or the whole verse where the version cannot be
 *   matched word for word ([whole]); null when the version is licensed and
 *   has not reached this phone.
 */
@Immutable
data class RoomVersionLine(
    val translation: TranslationID,
    val readers: String,
    val words: String?,
    val whole: Boolean,
)

// MARK: - What the panel says

/** Whether the selection stops part-way through [verse]. */
private fun isPartial(range: VerseRange, verse: Int): Boolean {
    val single = range.startVerse == range.endVerse
    if (verse == range.startVerse) return range.startChar != null || (single && range.endChar != null)
    if (verse == range.endVerse) return range.endChar != null
    return false
}

/** The selection's own-text offsets at [verse]. */
private fun offsets(range: VerseRange, verse: Int): Pair<Int?, Int?> {
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
): OriginalReading? {
    val verses = mutableListOf<OriginalVerseWords>()
    val chosen = linkedMapOf<Int, List<Int>>()
    var whole = false
    for (verse in range.verses) {
        val words = original.words(verse) ?: continue
        if (words.isEmpty()) continue
        val verseLinks = links?.get(verse)?.takeIf { it.isNotEmpty() }
        val all = words.indices.toList()
        val picked = if (!isPartial(range, verse)) {
            // A whole verse is all its words either way; but where this
            // version has no links for it, what each word is said as is the
            // Berean Standard's rather than yours, and the panel says so.
            if (verseLinks == null) whole = true
            all
        } else if (verseLinks == null) {
            whole = true
            all
        } else {
            val (from, to) = offsets(range, verse)
            val under = OriginalWords.filledInterior(
                OriginalWords.words(verseLinks, from, to),
                OriginalWords.linked(verseLinks),
            ).filter { it in words.indices }
            if (under.isEmpty()) {
                whole = true
                all
            } else {
                under
            }
        }
        val text = texts[verse]
        val pivotText = pivotTexts[verse]
        val pivot = pivotLinks?.get(verse)
        chosen[verse] = picked
        verses += OriginalVerseWords(
            verse = verse,
            columns = picked.map { index ->
                val mine = if (verseLinks != null && text != null) {
                    OriginalWords.rendering(index, verseLinks, text)
                } else {
                    null
                }
                val theirs = if (mine == null && pivot != null && pivotText != null) {
                    OriginalWords.rendering(index, pivot, pivotText)
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
    val rendering = picked.singleOrNull()?.let { (verse, index) ->
        val verseLinks = links[verse]
        val text = texts[verse]
        if (verseLinks != null && text != null) OriginalWords.rendering(index, verseLinks, text)?.trim() else null
    }
    return OriginalLine(words, rendering, languageOf(range.bookID, words))
}

/**
 * The versions read in this room, each with who reads it: yours first, then
 * the others in the order of their readers' names. You are "you"; everybody
 * else is their first name.
 */
internal fun roomReaders(
    mine: TranslationID,
    others: List<Pair<String, TranslationID>>,
): List<Pair<TranslationID, String>> {
    val byVersion = linkedMapOf<TranslationID, MutableList<String>>()
    byVersion.getOrPut(mine) { mutableListOf() } += Copy.ORIGINAL_YOU
    for ((name, version) in others.sortedBy { firstName(it.first).lowercase() }) {
        byVersion.getOrPut(version) { mutableListOf() } += firstName(name)
    }
    return byVersion.map { (version, names) -> version to joined(names) }
}

/** "you", "you and Ruth", "you, Ruth and Ann". */
private fun joined(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/**
 * How one version says the chosen words: in each verse, the ranges its links
 * give for them, read from its own text; where that comes to nothing, the
 * verse whole, and [RoomVersionLine.whole] says so. Null [texts] is a
 * version that has not reached this phone.
 */
internal fun roomVersionLine(
    translation: TranslationID,
    readers: String,
    chosen: Map<Int, List<Int>>,
    links: Map<Int, List<AlignmentLink>>?,
    texts: Map<Int, String>?,
    breaks: Map<Int, List<Int>> = emptyMap(),
): RoomVersionLine {
    if (texts == null) return RoomVersionLine(translation, readers, null, whole = false)
    var whole = false
    val pieces = mutableListOf<String>()
    for ((verse, words) in chosen.toSortedMap()) {
        val text = texts[verse] ?: continue
        val verseLinks = links?.get(verse)
        val ranges = if (verseLinks != null) OriginalWords.ranges(words.toSet(), verseLinks, text) else emptyList()
        val (from, to) = if (ranges.isEmpty()) {
            whole = true
            0 to text.length
        } else {
            ranges.first().start to ranges.last().end
        }
        pieces += spoken(text, from, to, breaks[verse].orEmpty()).trim()
    }
    if (pieces.isEmpty()) return RoomVersionLine(translation, readers, null, whole = false)
    return RoomVersionLine(translation, readers, pieces.joinToString(" "), whole)
}

/**
 * `text[from, to)` as a reader would see it: a line of poetry is glued to
 * the next in a verse's own text ("O LORD?Who is like You"), because the
 * page breaks the line instead; here, out of the page, the break becomes a
 * space.
 */
private fun spoken(text: String, from: Int, to: Int, breaks: List<Int>): String {
    val low = from.coerceIn(0, text.length)
    val high = to.coerceIn(low, text.length)
    val out = StringBuilder()
    for (i in low until high) {
        if (i > low && i in breaks && !text[i - 1].isWhitespace() && !text[i].isWhitespace()) out.append(' ')
        out.append(text[i])
    }
    return out.toString()
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
    val words = range.verses.flatMap { chapter.words(it).orEmpty() }
    if (words.isEmpty()) return null
    val links = original.links(translation, range.bookID, range.chapter, content)
    return OriginalUnderLift(
        verb = Copy.originalVerb(languageOf(range.bookID, words)),
        line = originalLine(range, held, chapter, links, texts),
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
    )
}

/**
 * How each version read in [room] says the selected words (§7.2). A bundled
 * version's text is on the phone; a licensed one's comes from its cache, or
 * — when [fetch] is set and the phone is online — through the licensed path
 * the page itself uses. One that cannot be had says so. Off the main thread.
 *
 * @param mine your version, whose chapter is [myContent] — the page's own.
 */
internal suspend fun roomVersionLines(
    model: AppModel,
    context: Context,
    room: Room,
    reading: OriginalReading,
    mine: TranslationID,
    myContent: ScriptureChapter?,
    fetch: Boolean,
): List<RoomVersionLine> {
    val me = model.me?.id
    val others = model.members(room)
        .filter { it.personID != me }
        .mapNotNull { model.person(it.personID) }
        .map { it.name to it.translation }
    val range = reading.range
    val address = VerseAddress(range.bookID, range.chapter, 1)
    return roomReaders(mine, others).map { (translation, readers) ->
        val content = if (translation == mine && myContent != null) {
            myContent
        } else {
            val licensed = TranslationRegistry.translation(translation)?.takeIf { !it.isBundled }
            when {
                licensed == null -> withContext(Dispatchers.IO) { model.scripture.chapter(address, translation) }
                else -> withContext(Dispatchers.IO) {
                    model.scripture.cachedRemoteChapter(context, address, licensed)
                } ?: if (fetch && model.isOnline) {
                    model.scripture.ensureRemoteChapter(context, address, licensed)
                } else {
                    null
                }
            }
        }
        val links = content?.let {
            withContext(Dispatchers.IO) { model.original.links(translation, range.bookID, range.chapter, it) }
        }
        roomVersionLine(
            translation, readers, reading.chosen, links, content?.ownTexts(),
            content?.ownSpanBreaks().orEmpty(),
        )
    }
}

// MARK: - The line

/** Isolates a run of another direction inside a line (FSI … PDI). */
private fun isolated(text: String): String = "⁨$text⁩"

/**
 * The original line, directly above the inks (§7.5): `λόγος · logos · Word`.
 * Muted, one line, truncating; a button that opens the panel for the current
 * selection, labelled with what it says and what it does.
 */
@Composable
fun OriginalLineView(
    line: OriginalLine,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalFamily = remember(line.isHebrew) {
        if (line.isHebrew) RibbonFonts.hebrew else RibbonFonts.literata(FontWeight.Normal, 17f)
    }
    val italicFamily = remember { RibbonFonts.literata(FontWeight.Normal, 14f, italic = true) }
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = originalFamily, fontSize = 17.sp)) {
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
            .readableColumn()
            .padding(horizontal = 28.dp)
            .sizeIn(minHeight = 40.dp)
            .clickable(role = Role.Button, onClickLabel = Copy.ORIGINAL_LINE_ACTION, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = line.spoken
                role = Role.Button
                onClick(label = Copy.ORIGINAL_LINE_ACTION) { onOpen(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = RibbonType.ui(14f).copy(textDirection = TextDirection.Ltr),
            color = Palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

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
 * @param room how each version read in this room says the words; null while
 *   that is being worked out, and the section is left out when everyone
 *   reads one version — your own words are already in the row above.
 * @param initiallyOpen a word whose detail is open to begin with (the look
 *   book's picture of it).
 */
@Composable
fun OriginalPanel(
    reading: OriginalReading,
    versionName: String,
    lexicon: Lexicon?,
    parsings: Parsings?,
    room: List<RoomVersionLine>?,
    modifier: Modifier = Modifier,
    initiallyOpen: Pair<Int, Int>? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize.height
    val ceiling = with(density) { (screen * PANEL_MAX_SHARE).toDp() }
        .takeIf { it > 0.dp } ?: 480.dp

    // One word open at a time. Kept by its verse and position, so it stays
    // open while the handles move and the word is still in the selection,
    // and closes on its own when the selection moves off it.
    var open by remember { mutableStateOf(initiallyOpen) }
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallCaps(
            Copy.originalHeading(reading.language, reading.range.formatted),
            size = 12f,
            modifier = Modifier.semantics { heading() },
        )

        val several = reading.verses.size > 1
        for (verse in reading.verses) {
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

        if (room != null && room.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallCaps(
                    Copy.ORIGINAL_IN_THIS_ROOM,
                    size = 12f,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { heading() },
                )
                for (line in room) RoomLine(line)
            }
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
            for (column in verse.columns) {
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

/**
 * One version read in this room: its name and who reads it, small, and its
 * words for the selection — muted where it is the whole verse, because that
 * is the fallback rather than the answer.
 */
@Composable
private fun RoomLine(line: RoomVersionLine) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SmallCaps(
            "${line.translation.displayName} · ${line.readers}",
            size = 11f,
            color = Palette.muted,
        )
        val words = line.words
        if (words == null) {
            Text(text = Copy.ORIGINAL_NOT_ON_THIS_PHONE, style = RibbonType.ui(14f), color = Palette.muted)
        } else {
            Text(
                text = words,
                style = RibbonType.scripture(16f),
                color = if (line.whole) Palette.muted else Palette.text,
            )
        }
    }
}
