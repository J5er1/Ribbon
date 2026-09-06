@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.Note
import app.readribbon.core.Reading
import app.readribbon.core.RibbonClock
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.design.NoteMark
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.fire.EmberView
import app.readribbon.reading.NoteCard
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S10 — the shelf: every book this room has read, as embers on a shared
// baseline with a faint warm bloom beneath. No drawn shelf, no sorting, no
// filtering, no list view — a shelf you can sort is a database.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an equivalent ambient value, so the model is a parameter —
// the same call NoteCard made. It holds Compose snapshot state, so reading
// `model.notes(...)` here recomposes exactly as `@Observable` does.
//
// Swift's `NavigationLink(value:)` pairs with a `navigationDestination` up in
// RibbonApp; here the destinations are navigation-compose routes owned by
// the root, so both screens take the push as a callback rather than reaching
// for a NavController of their own. Predictive back — the room peeling in
// behind the closing record — belongs to that NavHost too, and nothing here
// intercepts back.

/** The adaptive column: `GridItem(.adaptive(minimum: 88))`. */
private val SHELF_COLUMN_MIN = 88.dp

/** `spacing: 18` between columns, `spacing: 26` between rows. */
private val SHELF_COLUMN_SPACING = 18.dp
private val SHELF_ROW_SPACING = 26.dp

/** The record's ember, drawn large: Swift's `.scaleEffect(1.6)`. */
private const val EMBER_RECORD_SCALE = 1.6f

/**
 * The overlapping portraits, said in Compose's terms.
 *
 * Swift's `HStack(spacing: -6)` overlaps 30 pt portraits by 6. Here each
 * portrait sits in a 44 dp square so the touch target holds (deviation 12 —
 * a control only a stylus can hit is broken), and the row's spacing is
 * negative by the difference, so the drawn circles keep exactly the -6
 * overlap and the group stays centred: 44 − 20 = 24 = 30 − 6.
 */
private val PORTRAIT_TOUCH = 44.dp
private val PORTRAIT_ROW_SPACING = (-20).dp

/**
 * The shelf, below the fold on the room screen (S10).
 *
 * @param room the room whose shelf this is. Carried for parity with the
 *   Swift view, which takes it for the same reason: the readings were
 *   already resolved by the caller.
 * @param readings the finished readings, oldest first — `model.shelf(room)`.
 *   The caller shows this view only when that list is non-empty: no shelf
 *   until the first book is finished, because an empty shelf is a reproach,
 *   so it is absent rather than empty-stated.
 * @param onOpenEmber the push to one ember's record (S11) — Swift's
 *   `NavigationLink(value: reading.id)`.
 */
@Composable
fun ShelfView(
    room: Room,
    readings: List<Reading>,
    onStartAnother: () -> Unit,
    onOpenEmber: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // SwiftUI's adaptive `LazyVGrid` inside the room's own scroll view.
        // A `LazyVerticalGrid` cannot nest in a vertical scroll, and a shelf
        // is a room's finished books — a handful, not a feed — so the grid
        // is measured and laid out by hand instead. Nothing is lost: the
        // columns are still as many 88 dp cells as the width allows, and the
        // last row still fills from the leading edge.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            val columnCount = (
                (maxWidth + SHELF_COLUMN_SPACING) / (SHELF_COLUMN_MIN + SHELF_COLUMN_SPACING)
                ).toInt().coerceAtLeast(1)
            val columnWidth =
                (maxWidth - SHELF_COLUMN_SPACING * (columnCount - 1)) / columnCount

            Column(verticalArrangement = Arrangement.spacedBy(SHELF_ROW_SPACING)) {
                readings.chunked(columnCount).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SHELF_COLUMN_SPACING),
                        // `GridItem(..., alignment: .bottom)`: embers of
                        // three different sizes sit on one baseline, which
                        // is the whole argument of the shelf.
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        row.forEach { reading ->
                            ShelfEmber(
                                reading = reading,
                                onOpen = { onOpenEmber(reading.id) },
                                modifier = Modifier.width(columnWidth),
                            )
                        }
                        // The short last row stays left, the way a leading
                        // grid alignment leaves it.
                        repeat(columnCount - row.size) {
                            Spacer(Modifier.width(columnWidth))
                        }
                    }
                }
            }
        }

        if (readings.size == 1) {
            // The moment the keepsake idea first appears.
            SmallCaps(
                Copy.ONE_DAY_THIS_IS_A_BOOK,
                size = 12f,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        QuietControl(
            title = Copy.START_ANOTHER,
            onClick = onStartAnother,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** One book on the shelf: its ember, and its name beneath. */
@Composable
private fun ShelfEmber(
    reading: Reading,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = Bible.book(reading.bookID)?.name ?: reading.bookID
    Column(
        modifier = modifier
            .sizeIn(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The ember clears its own semantics, so the cell announces by the
        // book's name and nothing else — no place in a sequence, no count.
        //
        // A large book's ember is wider than a narrow column, and SwiftUI's
        // fixed `.frame` lets it overflow its cell rather than be squeezed —
        // Isaiah has to look like Isaiah. `Modifier.size` obeys the incoming
        // constraint, so the ember is measured unbounded and allowed to
        // spill the same way.
        EmberView(
            scale = reading.handiwork.scale,
            modifier = Modifier.wrapContentWidth(
                align = Alignment.CenterHorizontally,
                unbounded = true,
            ),
        )
        SmallCaps(name, size = 12f)
    }
}

/**
 * S11 — an ember: one finished book's complete record. Immutable, and the
 * source of the printed keepsake.
 *
 * @param onOpenVerse a quoted verse opens the reading at that verse — the
 *   finished book's own pages, not a copy.
 * @param onOpenPerson a portrait goes to its person (S12). Swift pushes a
 *   `PersonRoute`; the route type lives with the root's NavHost, so the two
 *   halves of it are passed here instead.
 */
@Composable
fun EmberRecordScreen(
    model: AppModel,
    reading: Reading,
    onOpenVerse: (VerseAddress) -> Unit,
    onReadAgain: (String) -> Unit,
    onOpenPerson: (personID: Uuid, roomID: Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = Bible.book(reading.bookID)
    val notes: List<Note> = model.notes(reading)
    val highlights: List<Highlight> = model.highlights(reading)
    val reduceMotion = rememberReduceMotion()

    /**
     * Ember → ember record. iOS gets this from the zoom navigation
     * transition, where the tapped ember *is* the one that grows. Compose's
     * shared-element transition would need a `SharedTransitionLayout`
     * wrapping the whole NavHost, which is the root's to own and not this
     * screen's — so the become is a damped scale here: the record's ember
     * arrives at the shelf's size and settles up to 1.6× on the settle
     * token's ease-out curve. Eased, never sprung, so it never overshoots.
     * Under reduce motion it is simply drawn at its full size (§11).
     */
    var grown by remember(reading.id) { mutableStateOf(false) }
    LaunchedEffect(reading.id) { grown = true }
    val emberScale by animateFloatAsState(
        targetValue = if (grown) EMBER_RECORD_SCALE else 1f,
        animationSpec =
            if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut),
        label = "ember-becomes-a-record",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .room(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No scroll indicators: Compose draws none, which is what
                // `.scrollIndicators(.hidden)` asks for.
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .readableColumn()
                    // Edge to edge: the ground and its grain run under the
                    // system bars, and only the content clears them. The
                    // extra bottom inset three-button navigation needs comes
                    // from safeDrawing itself.
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmberView(
                        scale = reading.handiwork.scale,
                        modifier = Modifier
                            .padding(top = 30.dp, bottom = 16.dp)
                            .graphicsLayer {
                                scaleX = emberScale
                                scaleY = emberScale
                            },
                    )
                    Text(
                        text = book?.name ?: reading.bookID,
                        style = RibbonType.display(30f),
                        color = Palette.text,
                    )
                    SmallCaps(
                        RibbonClock.emberRange(
                            start = reading.startedAt,
                            end = reading.finishedAt ?: reading.startedAt,
                        ),
                        size = 13f,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(PORTRAIT_ROW_SPACING),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Who read it, as portraits — and a portrait goes
                        // to its person (S12).
                        //
                        // Swift asks the store for the members of a room it
                        // builds on the spot from the reading's room id;
                        // `members` matches on that id alone, so the same
                        // stand-in room is built here.
                        val readingRoom = Room(id = reading.roomID, createdAt = Clock.System.now())
                        model.members(readingRoom).forEach { membership ->
                            Box(
                                modifier = Modifier
                                    .size(PORTRAIT_TOUCH)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) {
                                        onOpenPerson(membership.personID, reading.roomID)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                PortraitView(
                                    person = model.person(membership.personID),
                                    ink = membership.ink,
                                    size = 30.dp,
                                    image = model.portrait(membership.personID),
                                )
                            }
                        }
                    }
                }

                if (notes.isEmpty() && highlights.isEmpty()) {
                    // A book with no notes — possible and not a failure.
                    Text(
                        text = Copy.STRAIGHT_THROUGH,
                        style = RibbonType.ui(15f),
                        color = Palette.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }

                if (notes.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        notes.forEach { note ->
                            EmberNoteRow(
                                model = model,
                                note = note,
                                roomID = reading.roomID,
                                onOpenVerse = onOpenVerse,
                            )
                        }
                    }
                }

                if (highlights.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        highlights.forEach { highlight ->
                            QuotedHighlight(
                                model = model,
                                highlight = highlight,
                                onOpenVerse = onOpenVerse,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    QuietControl(
                        title = Copy.READ_IT_AGAIN,
                        onClick = { onReadAgain(reading.bookID) },
                    )
                    // "Make this a book" arrives with the printed keepsake
                    // (§15 horizon).
                }
            }
        }
    }
}

@Composable
private fun EmberNoteRow(
    model: AppModel,
    note: Note,
    roomID: Uuid,
    onOpenVerse: (VerseAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var open by remember(note.id) { mutableStateOf(false) }

    val settle: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)
    val settleSize: FiniteAnimationSpec<IntSize> =
        if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    val ink = model.membership(personID = note.authorID, roomID = roomID)?.ink ?: Ink.clay

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The drawn row is a 6 dp mark and a line of small caps; the
                // finger gets the full 44 dp (deviation 12).
                .sizeIn(minHeight = 44.dp)
                .clickable(role = Role.Button) { open = !open },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteMark(
                kind = note.kind,
                ink = ink,
                found = true,
                mine = note.authorID == model.me?.id,
                pending = false,
            )
            SmallCaps(note.verse.formatted, size = 12f, color = Palette.text.copy(alpha = 0.8f))
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(settle) + expandVertically(settleSize),
            exit = fadeOut(settle) + shrinkVertically(settleSize),
            label = "ember-note",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NoteCard(
                    model = model,
                    note = note,
                    author = model.person(note.authorID),
                    authorInk = ink,
                    // The record is immutable: a finished book's notes are
                    // read, never taken back or edited.
                    onTakeBack = {},
                    onEdit = {},
                    modifier = Modifier.padding(start = 16.dp),
                )
                // Sharing: plain text only, and only your own words or the
                // verse itself — never someone else's note (S11).
                val body = note.body
                if (note.authorID == model.me?.id && body != null) {
                    QuietControl(
                        title = Copy.SHARE,
                        onClick = {
                            // SwiftUI's ShareLink, said in Android's terms:
                            // one plain-text intent through the system
                            // chooser.
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    Copy.sharedNote(note.verse.formatted, body),
                                )
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        modifier = Modifier.padding(start = 16.dp),
                        size = 11f,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotedHighlight(
    model: AppModel,
    highlight: Highlight,
    onOpenVerse: (VerseAddress) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = model.me
    val text = me?.let { model.scripture.verseText(highlight.range.start, it.translation) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 44.dp)
            .clickable(role = Role.Button) { onOpenVerse(highlight.range.start) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (text != null) {
            Text(
                text = text,
                style = RibbonType.scripture(15f),
                color = Palette.text,
                modifier = Modifier
                    .background(
                        color = highlight.ink.color.copy(alpha = Palette.HIGHLIGHT_WASH),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(10.dp),
            )
        }
        SmallCaps(highlight.range.formatted, size = 11f)
    }
}
