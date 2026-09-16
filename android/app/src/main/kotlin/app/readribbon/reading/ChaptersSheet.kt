@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.Reading
import app.readribbon.core.VerseAddress
import app.readribbon.design.Air
import app.readribbon.design.Palette
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SectionLabel
import app.readribbon.design.SmallCaps
import app.readribbon.design.pressable
import app.readribbon.design.pressablePaper
import app.readribbon.design.rememberSheetExit
import app.readribbon.design.room
import kotlin.uuid.ExperimentalUuidApi

// Where you are, and everywhere else in this book.
//
// Deviation A31, and it answers a gap rather than a preference: a book is
// opened at your own position (§6.2) and read forward, and until now the only
// way to reach Mark 10 from Mark 1 was to scroll through nine chapters. The
// build book never specified a chapter list because S02's page is deliberately
// bare and §6.6 puts *navigation* in the chooser — but the chooser starts a
// reading, it does not move inside one.
//
// So this is the chooser's own argument applied one level down: "the most
// conventional screen in the app, and it should stay that way — this is
// navigation, not atmosphere." A grid of numbers. No progress ring around
// each chapter, no ticks for what has been read, nothing shaded by how far
// anybody got — all of which are the counting Law 2 forbids, and all of which
// a chapter grid is the classic place to smuggle in.
//
// Exactly two things are marked, and both are addresses rather than measures:
// where **you** are, and where the room's ribbon is.

/**
 * The chapters of the book being read, as a sheet.
 *
 * @param onGo jump to this address and close.
 */
@Composable
fun ChaptersSheet(
    model: AppModel,
    reading: Reading,
    onDismiss: () -> Unit,
    onGo: (VerseAddress) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val leave = rememberSheetExit(sheetState)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Palette.ground,
        contentColor = Palette.text,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        ChaptersContent(
            model = model,
            reading = reading,
            onGo = { address -> leave { onGo(address) } },
        )
    }
}

/**
 * The sheet's body, without the sheet around it — split out for the same
 * reason the chooser's is: so it can be rendered and looked at.
 */
@Composable
fun ChaptersContent(
    model: AppModel,
    reading: Reading,
    onGo: (VerseAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = Bible.book(reading.bookID)
    val chapterCount = book?.chapterCount ?: 1
    val here = model.myPosition(reading)
    val ribbon = model.ribbon(reading)
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .room()
            .padding(horizontal = 20.dp),
    ) {
        Air(22.dp)
        Text(
            text = book?.name ?: reading.bookID,
            style = RibbonType.display(28f),
            color = Palette.text,
            // The one line that says what this sheet is about. Every pushed
            // screen gets this from `RibbonScreen`'s top bar; the two screens
            // that draw their own title had to be told (§11 — a screen reader
            // navigates by heading, and a screen with none is a flat list).
            modifier = Modifier.semantics { heading() },
        )

        // The ribbon, at the top, because it is the one line here that is
        // about somebody rather than about the book. It carries its verse —
        // the only verse-precise jump in this sheet, and the only one worth
        // being: a grid of numbers is how you find a chapter, and the ribbon
        // is how you find the exact place another person stopped.
        if (ribbon != null) {
            Air(16.dp)
            val name = model.person(ribbon.personID)?.name
            RibbonRow(
                line = Copy.ribbonIsAt(
                    who = name?.let { firstNameOf(it) },
                    reference = book?.chapterHeading(ribbon.chapter)?.let { "$it:${ribbon.verse}" }
                        ?: "${ribbon.chapter}:${ribbon.verse}",
                ),
                onClick = {
                    onGo(
                        VerseAddress(
                            bookID = reading.bookID,
                            chapter = ribbon.chapter,
                            verse = ribbon.verse,
                        ),
                    )
                },
            )
        }

        Air(24.dp)
        SectionLabel(Copy.CHAPTERS)
        Air(12.dp)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = bottomBar + 28.dp,
            ),
        ) {
            items((1..chapterCount).toList()) { n ->
                ChapterTile(
                    number = n,
                    heading = book?.chapterHeading(n) ?: "$n",
                    youAreHere = n == here.chapter,
                    hasRibbon = ribbon != null && ribbon.chapter == n,
                    onClick = {
                        onGo(VerseAddress(bookID = reading.bookID, chapter = n, verse = 1))
                    },
                )
            }
        }
    }
}

/**
 * One chapter.
 *
 * Two marks, and neither of them counts anything. Where you are is the tile
 * standing up out of the grid — a paper tile among wells, which is the same
 * raised/recessed pair the room uses for its hearth. The ribbon is a hairline
 * in the accent along the tile's foot, which is what a ribbon in a book
 * actually looks like from the outside: a line at the edge of a page.
 *
 * Colour is never the only signal (§11), so both states are spoken as well.
 */
@Composable
private fun ChapterTile(
    number: Int,
    heading: String,
    youAreHere: Boolean,
    hasRibbon: Boolean,
    onClick: () -> Unit,
) {
    val accent = Palette.accent
    val label = when {
        youAreHere && hasRibbon -> Copy.chapterYouAreHereWithRibbon(heading)
        youAreHere -> Copy.chapterYouAreHere(heading)
        hasRibbon -> Copy.chapterHasRibbon(heading)
        else -> heading
    }
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
            .then(
                if (youAreHere) {
                    Modifier.pressablePaper(RibbonShape.smallShape, onClick = onClick)
                } else {
                    // Nothing drawn. A well here would be the ground on the
                    // ground — invisible on most wallpapers and a faint box
                    // on the brand's — so 66 chapters would be 65 containers
                    // that say nothing. The page of numbers with one raised
                    // out of it is the honest picture, and the press still
                    // answers through the state layer.
                    Modifier
                        .clip(RibbonShape.smallShape)
                        .pressable(onClick = onClick)
                },
            )
            .semantics {
                contentDescription = label
                selected = youAreHere
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$number",
            style = RibbonType.ui(17f),
            color = if (youAreHere) Palette.text else Palette.muted,
        )
        if (hasRibbon) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp)
                    .size(width = 16.dp, height = 2.dp)
                    .clip(RibbonShape.smallShape)
                    .background(accent),
            )
        }
    }
}

/** The ribbon's own row: one sentence, and it is a door. */
@Composable
private fun RibbonRow(line: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .pressablePaper(RibbonShape.rowShape, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = line,
            style = RibbonType.ui(16f),
            color = Palette.text,
            modifier = Modifier.weight(1f),
        )
        SmallCaps(Copy.GO_THERE, size = 11f, color = Palette.accent)
    }
}

/** First name only, the way the whole app addresses people. */
internal fun firstNameOf(name: String): String =
    name.trim().substringBefore(' ').ifEmpty { name.trim() }
