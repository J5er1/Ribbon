package app.readribbon.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.BibleBook
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.Room
import app.readribbon.core.TranslationID
import app.readribbon.data.ScriptureStore
import app.readribbon.design.Palette
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.room
import app.readribbon.fire.CampfireGlyph
import app.readribbon.fire.EmberView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// S13 — the book chooser: starting a fire. Deliberately the most
// conventional screen in the app; this is navigation, not atmosphere. The
// drawn fire is the only length indicator — no word counts, no chapter
// counts, no reading-time estimates ("about 14 hours" is a commitment a
// person can fail).

/** Horizontal margin of the whole sheet — Swift's `.padding(.horizontal, 22)`. */
private val SheetMargin = 22.dp

/**
 * The gap between the outer blocks: search field, starters, the library.
 * Swift's `VStack(alignment: .leading, spacing: 26)`.
 */
private val BlockGap = 26.dp

/** Between one section of the library and the next — Swift's `spacing: 22`. */
private val SectionGap = 22.dp

/**
 * The smallest thing a finger is allowed to have to hit. The drawn rows are
 * shorter than this; the target is not (deviation 12 — that defect was found
 * on iPad, and it is the same defect here).
 */
private val TouchTarget = 44.dp

/**
 * The chooser, presented (S13).
 *
 * Swift presents `BookChooserSheet` from the room with `.sheet`, and the
 * sheet's own chrome — `.presentationBackground(Palette.ground)` — is set
 * inside the chooser rather than by the room, so the same seam is kept here:
 * this composable is the sheet, and the room only says whether it is up.
 *
 * The `NavigationStack` Swift wraps the body in exists only to hide its own
 * navigation bar (`.toolbarVisibility(.hidden, for: .navigationBar)`); it
 * carries no destinations, so there is nothing to route and no navigation
 * host here. Dismissal is the sheet's own — Swift declares `@Environment(
 * \.dismiss)` and never calls it, because choosing a book is what closes the
 * chooser and the room is what closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChooserSheet(
    room: Room,
    model: AppModel,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        // `.presentationBackground(Palette.ground)`. The grain goes on top of
        // it, inside the content, exactly as it does in a room.
        containerColor = Palette.ground,
        contentColor = Palette.text,
        // Swift's sheet shows no grabber: `.presentationDragIndicator` is
        // `.automatic`, and automatic draws nothing for a sheet with a single
        // detent. The whole sheet still drags, and predictive back closes it.
        dragHandle = null,
        // The content keeps its own clearance from the system bars — see
        // `BookChooserContent`, which has to add the three-button bar's inset
        // underneath the last book rather than above the scroll.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        BookChooserContent(room = room, model = model, onChoose = onChoose)
    }
}

/**
 * The chooser's body, without the sheet around it: the search field, the
 * starters, and all 66 books in their sections.
 *
 * Split out from [BookChooserSheet] so the same screen can be pushed as a
 * destination — first-run has to choose a book with no room to fall back to
 * — without a sheet inside a sheet.
 */
@Composable
fun BookChooserContent(
    room: Room,
    model: AppModel,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val translation: TranslationID = model.me?.translation ?: TranslationID.bsb
    val onShelf: Set<String> = model.shelf(room).map { it.bookID }.toSet()

    val trimmed = query.trim()
    val searching = trimmed.length >= 2
    val hits = rememberSearchAnswer(model.scripture, trimmed, translation)

    // Three-button navigation eats the bottom of the sheet, so its inset is
    // added under the last row rather than clipped off it: the library
    // scrolls behind the bar, and ends above it.
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        // `.room()` — the ground and its grain — is painted by a box behind
        // the content rather than by the scroll itself, because the modifier
        // hides its node from accessibility (the paper is texture, not
        // information) and a scroll that hid itself would take 66 books with
        // it.
        Box(Modifier.matchParentSize().room())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SheetMargin,
                end = SheetMargin,
                top = 16.dp,
                bottom = 40.dp + bottomBar,
            ),
        ) {
            item(key = "search") {
                SearchField(query = query, onQueryChange = { query = it })
            }

            if (searching && hits != null) {
                searchResults(
                    hits = hits,
                    onShelf = onShelf,
                    onChoose = onChoose,
                )
            } else {
                // Below two characters — and while the first search of the
                // session is still finding its books — the library itself is
                // the screen. Nothing spins in the gap (§8): what a person is
                // already looking at simply stays until there is something
                // truer to show.
                item(key = "starters") {
                    StarterRow(onChoose = onChoose, modifier = Modifier.padding(top = BlockGap))
                }
                allBooks(onShelf = onShelf, onChoose = onChoose, topGap = BlockGap)
            }
        }
    }
}

// MARK: The search field

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(width = 1.dp, color = Palette.rule, shape = shape)
            .heightIn(min = TouchTarget)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                text = Copy.SEARCH,
                style = RibbonType.ui(16f),
                color = Palette.muted,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = RibbonType.ui(16f).copy(color = Palette.text),
            cursorBrush = SolidColor(Palette.chartreuse),
            // `.autocorrectionDisabled()`: autocorrect turns "Habakkuk" into
            // something else on the third letter.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// MARK: Good places to start

@Composable
private fun StarterRow(onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallCaps(Copy.GOOD_PLACES_TO_START, size = 12f)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Bible.goodPlacesToStart.forEach { id ->
                Bible.book(id)?.let { book -> StarterCard(book, onChoose) }
            }
        }
    }
}

@Composable
private fun StarterCard(book: BibleBook, onChoose: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val label = Copy.bookIsAFire(book.name, book.scale.name)
    Column(
        modifier = Modifier
            .size(width = 104.dp, height = 92.dp)
            .clip(shape)
            .background(Palette.surface)
            .border(width = 1.dp, color = Palette.rule, shape = shape)
            .clickable { onChoose(book.id) }
            // Swift's `.accessibilityLabel` on a Button *replaces* what the
            // button would otherwise say. Clearing the subtree is that same
            // replacement: the drawn fire and the name are one label, not
            // three things read in a row. The click stays on the node
            // outside this one.
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CampfireGlyph(state = FireState.burning, scale = book.scale, height = 30.dp)
        Text(
            text = book.name,
            style = RibbonType.ui(15f),
            color = Palette.text,
        )
    }
}

// MARK: All 66

/**
 * Every book, under its section. The sections are editorial, not theological
 * — they exist so 66 rows read as a library, not a list (S13).
 *
 * A `LazyListScope` extension rather than a composable so the rows really are
 * lazy: each row draws its own fire, and 66 canvases built up front is a
 * scroll that stutters on the way in.
 */
private fun LazyListScope.allBooks(
    onShelf: Set<String>,
    onChoose: (String) -> Unit,
    topGap: Dp,
) {
    Bible.sections.forEachIndexed { index, group ->
        item(key = "section-${group.section.name}") {
            SmallCaps(
                text = group.section.raw,
                size = 12f,
                modifier = Modifier.padding(
                    top = if (index == 0) topGap else SectionGap,
                    // Swift's `.padding(.bottom, 6)` plus the section stack's
                    // own 4 points before the first row.
                    bottom = 10.dp,
                ),
            )
        }
        itemsIndexed(group.books, key = { _, book -> book.id }) { position, book ->
            BookRow(
                book = book,
                onShelf = onShelf.contains(book.id),
                onChoose = onChoose,
                modifier = Modifier.padding(top = if (position == 0) 0.dp else 4.dp),
            )
        }
    }
}

@Composable
private fun BookRow(
    book: BibleBook,
    onShelf: Boolean,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = Copy.bookIsAFire(book.name, book.scale.name, onShelf = onShelf)
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The drawn row is about 36 dp tall. The target is 44 (§12.2).
            .heightIn(min = TouchTarget)
            .clickable {
                // Already on the shelf: choosing it again starts a fresh fire
                // and a second ember (S13).
                onChoose(book.id)
            }
            // One label for the row, not a name and then whatever the marks
            // beside it would each have said — see `StarterCard`.
            .clearAndSetSemantics { contentDescription = label }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = book.name,
            style = RibbonType.ui(16f),
            color = Palette.text,
            modifier = Modifier.weight(1f),
        )
        if (onShelf) {
            ShelfEmber()
        }
        CampfireGlyph(state = FireState.burning, scale = book.scale, height = 20.dp)
    }
}

/**
 * The ember that says you have read this one already — an ember at half
 * size, in a 26 × 20 window.
 *
 * `EmberView` sizes itself from the fire it was, so Swift shrinks it with
 * `.scaleEffect(0.5)` and then frames it. `graphicsLayer` is the same move:
 * a drawing transform that leaves the layout alone. The unbounded wrap is
 * what lets the ember measure at its own size inside a smaller window
 * instead of being squeezed into it — a squeezed ember is a different shape,
 * and shape is how this mark reads.
 */
@Composable
private fun ShelfEmber() {
    Box(
        modifier = Modifier.size(width = 26.dp, height = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        EmberView(
            scale = FireScale.small,
            modifier = Modifier
                .wrapContentSize(align = Alignment.Center, unbounded = true)
                .graphicsLayer {
                    scaleX = 0.5f
                    scaleY = 0.5f
                },
        )
    }
}

// MARK: Search (S23)

/**
 * Scripture search (S23), off the main thread and settled into state.
 *
 * Swift searches inside `body`, on the main actor, and gets away with it:
 * SwiftUI re-evaluates that body when `query` changes and no more often.
 * Compose recomposes for reasons of its own, and this search walks every
 * verse of every bundled book, so here it is a side effect whose result is
 * state — one answer per question, found once.
 *
 * `null` is "no answer yet", which is a different thing from an answer of
 * nothing: nothing found says so, and an unfinished search says nothing at
 * all. That is what keeps the screen honest while it works (§8) — while a
 * new answer is being found the previous one stays up, and the first search
 * of a session leaves the library it interrupted exactly where it was
 * rather than showing an emptiness it has not earned. The short wait before
 * searching is a typist's pause, not a delay: it means a five-letter word
 * is searched once instead of five times.
 */
@Composable
private fun rememberSearchAnswer(
    scripture: ScriptureStore,
    trimmed: String,
    translation: TranslationID,
): List<ScriptureStore.SearchHit>? {
    val answer by produceState<List<ScriptureStore.SearchHit>?>(
        null, scripture, trimmed, translation,
    ) {
        if (trimmed.length < 2) {
            // Back below two characters is a new question, not a pause in the
            // old one — the last answer is dropped so it can never reappear
            // under a query it does not belong to.
            value = null
            return@produceState
        }
        delay(TYPING_PAUSE_MILLIS)
        value = withContext(Dispatchers.Default) { scripture.search(trimmed, translation) }
    }
    return answer
}

/** Long enough that a word is searched once; short enough to be invisible. */
private const val TYPING_PAUSE_MILLIS = 120L

/**
 * What the search found. A reference, a book, or a verse — and every one of
 * them opens the book it belongs to, because the chooser's whole job is to
 * start a fire.
 */
private fun LazyListScope.searchResults(
    hits: List<ScriptureStore.SearchHit>,
    onShelf: Set<String>,
    onChoose: (String) -> Unit,
) {
    if (hits.isEmpty()) {
        item(key = "nothing") {
            Text(
                text = Copy.NOTHING_MATCHES,
                style = RibbonType.ui(15f),
                color = Palette.muted,
                modifier = Modifier.padding(top = BlockGap),
            )
        }
        // The list stays visible beneath (S13).
        allBooks(onShelf = onShelf, onChoose = onChoose, topGap = 18.dp)
        return
    }

    itemsIndexed(hits) { index, hit ->
        // Swift's `ForEach(Array(hits.enumerated()), id: \.offset)`, and the
        // 14 points its stack puts between one hit and the next.
        val gap = if (index == 0) BlockGap else 14.dp
        when (hit) {
            is ScriptureStore.SearchHit.Book -> {
                Bible.book(hit.bookID)?.let { book ->
                    BookRow(
                        book = book,
                        onShelf = onShelf.contains(book.id),
                        onChoose = onChoose,
                        modifier = Modifier.padding(top = gap),
                    )
                }
            }

            is ScriptureStore.SearchHit.Reference ->
                AddressHit(
                    address = hit.address.formatted,
                    text = null,
                    onChoose = { onChoose(hit.address.bookID) },
                    modifier = Modifier.padding(top = gap),
                )

            is ScriptureStore.SearchHit.Verse ->
                AddressHit(
                    address = hit.address.formatted,
                    text = hit.text,
                    onChoose = { onChoose(hit.address.bookID) },
                    modifier = Modifier.padding(top = gap),
                )
        }
    }
}

/**
 * One found address: the reference in small caps, and — when the hit was in
 * the text rather than in a reference — the two lines of the verse that
 * matched.
 */
@Composable
private fun AddressHit(
    address: String,
    text: String?,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A reference on its own is a line of 12-point small caps. The
            // target is 44 (§12.2).
            .heightIn(min = TouchTarget)
            .clickable(onClick = onChoose),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        SmallCaps(address, size = 12f, color = Palette.text.copy(alpha = 0.8f))
        if (text != null) {
            Text(
                text = text,
                style = RibbonType.scripture(14f),
                color = Palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
