@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.AlignmentLink
import app.readribbon.core.Ink
import app.readribbon.core.OriginalWords
import app.readribbon.core.Person
import app.readribbon.core.Room
import app.readribbon.core.RoomRenderings
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.TextRange
import app.readribbon.core.TranslationID
import app.readribbon.core.TranslationRegistry
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.core.displayName
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.RibbonFonts
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.rememberReduceMotion
import app.readribbon.services.cachedRemoteChapter
import app.readribbon.services.ensureRemoteChapter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// "In this room" (A62, §13.3): what the original panel says about how the
// room's other versions say the words you chose. Grouped by what is said,
// not listed person by person, so a room of eleven on four versions is a
// handful of blocks rather than eleven lines.
//
// Yours comes first, with no card: a small-caps label says it is yours.
// Every other block names its versions, sets its words with the ones that
// are not in yours at full strength and Medium, and shows who reads it — at
// most three faces, then first names, ending "and others" past three. Never
// a number. When every version here says what yours says, the section is
// one muted line; when everyone reads your version, it is not there at all.
//
// The grouping itself is the core's `RoomRenderings`, shared with the
// iPhone. The rest is worked out here without a screen — `roomSection` —
// so the same answers can be held to tests; the composables only set it.
// The iPhone's `RoomSection.swift` is the same shape, case for case.

/**
 * Someone in the room, the version they read, and what their face is drawn
 * with: their ink here, and their portrait when there is one.
 */
@Immutable
data class RoomPerson(
    val person: Person,
    val version: TranslationID,
    val ink: Ink? = null,
    val portrait: ImageBitmap? = null,
)

/** One face in a block, and the name said beside it — a first name, or "you". */
@Immutable
data class RoomReader(val who: RoomPerson, val name: String) {
    val id: Uuid get() = who.person.id
}

/**
 * How one version says the selection, as this phone holds it.
 *
 * @property wholeVerse the version could not be matched word for word, so
 *   [phrase] is its whole verse standing in.
 */
@Immutable
data class RoomSaid(val phrase: String, val wholeVerse: Boolean = false)

/** One block of the section: yours, another rendering, or the versions not on this phone. */
@Immutable
data class RoomBlock(
    val id: String,
    val kind: Kind,
    /**
     * Small caps over the words: "Yours · Berean Standard", or the versions
     * that say it, "World English and American Standard".
     */
    val label: String,
    /**
     * The words, as the first of the block's versions says them; for the
     * versions not on this phone, the sentence that says so.
     */
    val words: String,
    /** The words of [words] that are not in yours. Empty for yours, and for a whole verse standing in. */
    val differing: List<TextRange>,
    /** The version's whole verse stands in, muted, and is not set against yours word by word. */
    val isWholeVerse: Boolean,
    /** Everyone who reads it, you first in yours. */
    val readers: List<RoomReader>,
) {
    enum class Kind { Yours, Other, NotOnThisPhone }

    /** The faces shown: the first three readers. */
    val faces: List<RoomReader> get() = readers.take(VISIBLE_FACES)

    /** Everyone by name while that is three or fewer; past that, three and "and others". */
    val names: String get() = namesBeside(readers.map { it.name })

    /** What TalkBack reads for the whole block: the words, the versions, then every first name. */
    val spoken: String get() = sentences(listOf(words, label, Copy.listed(readers.map { it.name })))

    /**
     * [words] cut where it starts and stops differing from yours, in order:
     * joined, the runs are [words] exactly. A range out of order, empty or
     * past the end is passed over rather than trusted.
     */
    val runs: List<Pair<String, Boolean>>
        get() {
            val out = mutableListOf<Pair<String, Boolean>>()
            var at = 0
            fun take(from: Int, to: Int, differs: Boolean) {
                if (to > from) out += words.substring(from, to) to differs
            }
            for (range in differing) {
                if (range.start < at || range.end <= range.start || range.end > words.length) continue
                take(at, range.start, differs = false)
                take(range.start, range.end, differs = true)
                at = range.end
            }
            take(at, words.length, differs = false)
            return out
        }
}

/** The single muted line for a room whose versions all say what yours says. */
@Immutable
data class RoomAgreement(
    val line: String,
    /** Up to three of the others' faces. */
    val faces: List<RoomReader>,
    /** The line, then every other first name. */
    val spoken: String,
)

/** What "In this room" says for one selection. */
@Immutable
sealed interface RoomSection {
    /** Everyone here reads your version: nothing to compare, nothing said. */
    data object Omitted : RoomSection

    /** Every version here says the words as yours does: no heading, one line. */
    data class Agrees(val agreement: RoomAgreement) : RoomSection

    /** The heading, then one block per thing said, yours first. */
    data class Blocks(val blocks: List<RoomBlock>) : RoomSection

    /** Somebody here reads a version whose words have not reached this phone. */
    val waitsForAVersion: Boolean
        get() = this is Blocks && blocks.any { it.kind == RoomBlock.Kind.NotOnThisPhone }
}

/** Faces shown in a row before the names take over. */
internal const val VISIBLE_FACES = 3

/** Everyone by name while that is three or fewer; past that, the first three and "and others". */
internal fun namesBeside(all: List<String>): String =
    if (all.size <= VISIBLE_FACES) {
        Copy.listed(all)
    } else {
        all.take(VISIBLE_FACES).joinToString(", ") + " " + Copy.ROOM_AND_OTHERS
    }

/**
 * Parts read one after another as sentences, each closed by a full stop
 * unless it already ends one — looking past a closing quote or bracket, so
 * `made.”` is not read as `made.”.`.
 */
internal fun sentences(parts: List<String>): String =
    parts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { part ->
            val last = part.lastOrNull { it !in "”’\"')]" }
            if (last != null && last in ".?!…") part else "$part."
        }

/**
 * The section for one selection.
 *
 * @param yours the page's version. [me] leads its readers.
 * @param others everyone else in the room, in any order; they are set by
 *   name, so the same room always reads the same way. [me] among them is
 *   passed over.
 * @param saying a version's words for the selection; null when its text is
 *   not on this phone. Asked once per version.
 * @param versionName a version's name, "World English".
 */
internal fun roomSection(
    yours: TranslationID,
    me: RoomPerson?,
    others: List<RoomPerson>,
    saying: (TranslationID) -> RoomSaid?,
    versionName: (TranslationID) -> String = { it.displayName },
): RoomSection {
    val myID = me?.person?.id
    val people = others
        .filter { it.person.id != myID }
        .sortedBy { it.person.name.trim().lowercase() }

    val said = mutableMapOf<TranslationID, RoomSaid?>()
    fun phrase(version: TranslationID): RoomSaid? {
        if (version !in said) said[version] = saying(version)
        return said[version]
    }

    // Yours with you first, then each other version in the order of the
    // first person (by name) who reads it.
    val mine = listOfNotNull(myID).toMutableList()
    val readers = linkedMapOf<TranslationID, MutableList<Uuid>>()
    for (person in people) {
        if (person.version == yours) {
            mine += person.person.id
        } else {
            readers.getOrPut(person.version) { mutableListOf() } += person.person.id
        }
    }
    val renderings = RoomRenderings.of(
        yours = RoomRenderings.Said(yours, phrase(yours)?.phrase, mine),
        others = readers.map { (version, ids) -> RoomRenderings.Said(version, phrase(version)?.phrase, ids) },
    )
    if (renderings.isOneVersion) return RoomSection.Omitted

    val byID = (listOfNotNull(me) + people).associateBy { it.person.id }
    fun reader(id: Uuid): RoomReader? {
        val who = byID[id] ?: return null
        val name = if (id == myID) {
            Copy.ORIGINAL_YOU
        } else {
            firstName(who.person.name).trim().ifEmpty { Copy.SOMEONE }
        }
        return RoomReader(who, name)
    }

    if (renderings.allAgree) {
        val all = renderings.groups[0].readers.filter { it != myID }.mapNotNull(::reader)
        val line = all.singleOrNull()?.let { Copy.roomOneAgrees(it.name) } ?: Copy.ROOM_ALL_AGREE
        return RoomSection.Agrees(
            RoomAgreement(
                line = line,
                faces = all.take(VISIBLE_FACES),
                spoken = if (all.size == 1) line else sentences(listOf(line, Copy.listed(all.map { it.name }))),
            ),
        )
    }

    val blocks = renderings.groups.map { group ->
        val names = group.versions.map(versionName)
        val whole = !group.isYours && phrase(group.versions[0])?.wholeVerse == true
        RoomBlock(
            id = if (group.isYours) "yours" else "said:" + group.versions.joinToString(",") { it.rawValue },
            kind = if (group.isYours) RoomBlock.Kind.Yours else RoomBlock.Kind.Other,
            label = if (group.isYours) Copy.roomYours(names) else Copy.listed(names),
            words = group.phrase,
            differing = if (whole) emptyList() else group.differing,
            isWholeVerse = whole,
            readers = group.readers.mapNotNull(::reader),
        )
    }.toMutableList()
    renderings.notOnThisPhone?.let { missing ->
        blocks += RoomBlock(
            id = "missing",
            kind = RoomBlock.Kind.NotOnThisPhone,
            label = Copy.listed(missing.versions.map(versionName)),
            words = Copy.ORIGINAL_NOT_ON_THIS_PHONE,
            differing = emptyList(),
            isWholeVerse = false,
            readers = missing.readers.mapNotNull(::reader),
        )
    }
    return RoomSection.Blocks(blocks)
}

// MARK: - What each version says

/**
 * Your own words for the selection: exactly what is selected on the page,
 * verse by verse — the page is yours, so there is nothing to match.
 */
internal fun yourSaying(range: VerseRange, texts: Map<Int, String>, breaks: Map<Int, List<Int>> = emptyMap()): RoomSaid? {
    val pieces = range.verses.mapNotNull { verse ->
        val text = texts[verse] ?: return@mapNotNull null
        val (from, to) = offsets(range, verse)
        spoken(text, from ?: 0, to ?: text.length, breaks[verse].orEmpty()).trim().takeIf { it.isNotEmpty() }
    }
    return if (pieces.isEmpty()) null else RoomSaid(pieces.joinToString(" "))
}

/**
 * How another version says the chosen words: a verse you selected whole is
 * its verse whole; in a verse you selected part of, the ranges its links
 * give for the chosen words, read from its own text — and where that comes
 * to nothing, the verse whole, which [RoomSaid.wholeVerse] says is standing
 * in. Null where none of the chosen verses is in [texts].
 *
 * @param wholeVerses the verses the selection covers whole.
 */
internal fun roomSaying(
    chosen: Map<Int, List<Int>>,
    links: Map<Int, List<AlignmentLink>>?,
    texts: Map<Int, String>,
    breaks: Map<Int, List<Int>> = emptyMap(),
    wholeVerses: Set<Int> = emptySet(),
): RoomSaid? {
    var whole = false
    val pieces = mutableListOf<String>()
    for ((verse, words) in chosen.toSortedMap()) {
        val text = texts[verse] ?: continue
        if (verse in wholeVerses) {
            pieces += spoken(text, 0, text.length, breaks[verse].orEmpty()).trim()
            continue
        }
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
    if (pieces.isEmpty()) return null
    return RoomSaid(pieces.joinToString(" "), whole)
}

/**
 * How [version] says the selection, from what this phone holds: yours from
 * the page itself; a bundled version from its text; a licensed one from its
 * cache, or — when [fetch] is set and the phone is online — through the
 * licensed path the page itself uses. Null when it cannot be had.
 */
internal suspend fun roomSayingOnPhone(
    model: AppModel,
    context: Context,
    reading: OriginalReading,
    version: TranslationID,
    mine: TranslationID,
    myContent: ScriptureChapter?,
    fetch: Boolean,
): RoomSaid? {
    val range = reading.range
    if (version == mine && myContent != null) {
        return yourSaying(range, myContent.ownTexts(), myContent.ownSpanBreaks())
    }
    val address = VerseAddress(range.bookID, range.chapter, 1)
    val licensed = TranslationRegistry.translation(version)?.takeIf { !it.isBundled }
    val content = when {
        licensed == null -> withContext(Dispatchers.IO) { model.scripture.chapter(address, version) }
        else -> withContext(Dispatchers.IO) {
            model.scripture.cachedRemoteChapter(context, address, licensed)
        } ?: if (fetch && model.isOnline) {
            model.scripture.ensureRemoteChapter(context, address, licensed)
        } else {
            null
        }
    } ?: return null
    val links = withContext(Dispatchers.IO) { model.original.links(version, range.bookID, range.chapter, content) }
    val wholeVerses = range.verses.filterNot { isPartial(range, it) }.toSet()
    return roomSaying(reading.chosen, links, content.ownTexts(), content.ownSpanBreaks(), wholeVerses)
}

/**
 * "In this room" for [reading] (§13.3), from the room's members as this
 * phone knows them. Off the main thread where it reads.
 *
 * @param mine your version, whose chapter is [myContent] — the page's own.
 */
internal suspend fun roomSectionOf(
    model: AppModel,
    context: Context,
    room: Room,
    reading: OriginalReading,
    mine: TranslationID,
    myContent: ScriptureChapter?,
    fetch: Boolean,
): RoomSection {
    val myID = model.me?.id
    val people = model.members(room).mapNotNull { member ->
        model.person(member.personID)?.let { RoomPerson(it, it.translation, member.ink, model.portrait(it.id)) }
    }
    val me = people.firstOrNull { it.person.id == myID }?.copy(version = mine)
        ?: model.me?.let { RoomPerson(it, mine, portrait = model.portrait(it.id)) }
    val versions = (listOf(mine) + people.filter { it.person.id != myID }.map { it.version }).distinct()
    val said = versions.associateWith { roomSayingOnPhone(model, context, reading, it, mine, myContent, fetch) }
    return roomSection(mine, me, people, saying = { said[it] })
}

// MARK: - The section

/** A face, and the step to the next one, which sits behind it. */
private val FACE: Dp = 30.dp
private val FACE_STEP: Dp = 21.dp

/**
 * "In this room", or the one line that stands for it, or nothing (§13.3).
 * Each block arrives on `settle`, and at once under reduce motion; a block
 * already there stays put as the handles move and its words change.
 *
 * @param wordsSize a phrase is set large, as it is on the page; a whole
 *   verse, or more than one, a step smaller so the blocks stay a few lines.
 */
@Composable
internal fun InThisRoom(section: RoomSection, wordsSize: Float, modifier: Modifier = Modifier) {
    when (section) {
        RoomSection.Omitted -> Unit
        // No heading, and the words are not said a third time: the page and
        // the row above already say them. The others' faces, then the line.
        is RoomSection.Agrees -> key("agrees") {
            Arriving(modifier) { AgreementLine(section.agreement) }
        }
        is RoomSection.Blocks -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            key("heading") {
                Arriving {
                    SmallCaps(
                        Copy.ORIGINAL_IN_THIS_ROOM,
                        size = 12f,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    )
                }
            }
            for (block in section.blocks) key(block.id) {
                Arriving { RoomBlockView(block, wordsSize) }
            }
        }
    }
}

/** Arrives once, on `settle` — or is simply there under reduce motion. */
@Composable
private fun Arriving(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val still = rememberReduceMotion()
    val shown = remember { MutableTransitionState(still).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = shown,
        modifier = modifier,
        enter = fadeIn(RibbonMotion.settle(still)) +
            expandVertically(RibbonMotion.settle(still), expandFrom = Alignment.Top),
    ) {
        content()
    }
}

/** The one muted line when every version here agrees with yours, beside up to three faces. */
@Composable
private fun AgreementLine(agreement: RoomAgreement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clearAndSetSemantics { contentDescription = agreement.spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (agreement.faces.isNotEmpty()) RoomFaces(agreement.faces)
        Text(text = agreement.line, style = RibbonType.ui(14f), color = Palette.muted)
    }
}

/**
 * One thing said: the versions that say it in small caps, the words in
 * Literata, and who reads it. Yours has no card — its label says it is
 * yours. One element to TalkBack: the words, the versions, every name.
 */
@Composable
private fun RoomBlockView(block: RoomBlock, wordsSize: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = block.spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallCaps(block.label, size = 11f, color = Palette.muted)
        RoomWords(block, wordsSize)
        if (block.readers.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoomFaces(block.faces)
                SmallCaps(block.names, size = 11f, color = Palette.muted)
            }
        }
    }
}

/**
 * Yours at full strength. Another rendering with the words yours does not
 * use at full strength and Medium, and the words it shares a step back —
 * weight as well as tone, so the difference is never colour alone. A whole
 * verse standing in is muted throughout; a version not here says so.
 */
@Composable
private fun RoomWords(block: RoomBlock, size: Float) {
    when {
        block.kind == RoomBlock.Kind.NotOnThisPhone ->
            Text(text = block.words, style = RibbonType.ui(14f), color = Palette.muted)
        block.kind == RoomBlock.Kind.Yours ->
            Text(text = block.words, style = RibbonType.scripture(size), color = Palette.text)
        block.isWholeVerse ->
            Text(text = block.words, style = RibbonType.scripture(size), color = Palette.muted)
        else -> {
            val medium = remember(size) { RibbonFonts.literata(FontWeight.Medium, size) }
            val shared = Palette.text.copy(alpha = 0.7f)
            val text: AnnotatedString = buildAnnotatedString {
                for ((piece, differs) in block.runs) {
                    if (differs) {
                        withStyle(SpanStyle(fontFamily = medium, fontWeight = FontWeight.Medium)) { append(piece) }
                    } else {
                        withStyle(SpanStyle(color = shared)) { append(piece) }
                    }
                }
            }
            Text(text = text, style = RibbonType.scripture(size), color = Palette.text)
        }
    }
}

/**
 * A few faces, overlapping, first on top: the app's portrait — a photo, or
 * a monogram in the person's ink — each cut from the one under it by a ring
 * of the panel's own surface. Not a shadow, not glass.
 */
@Composable
private fun RoomFaces(readers: List<RoomReader>) {
    val width = FACE + FACE_STEP * (readers.size - 1).coerceAtLeast(0)
    Box(Modifier.size(width = width, height = FACE)) {
        readers.forEachIndexed { i, reader ->
            Box(
                Modifier
                    .offset(x = FACE_STEP * i)
                    .zIndex((readers.size - i).toFloat())
                    .size(FACE)
                    .background(Palette.surface, CircleShape)
                    .padding(2.dp)
                    .border(1.dp, Palette.rule, CircleShape),
            ) {
                PortraitView(
                    person = reader.who.person,
                    ink = reader.who.ink,
                    size = FACE - 4.dp,
                    image = reader.who.portrait,
                )
            }
        }
    }
}
