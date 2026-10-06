@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Bible
import app.readribbon.core.BlockStyle
import app.readribbon.core.Ink
import app.readribbon.core.Person
import app.readribbon.core.QuietHoursBand
import app.readribbon.core.Room
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.Translation
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.data.RoomNotificationPrefs
import app.readribbon.design.Air
import app.readribbon.design.ArrivingLate
import app.readribbon.design.Flows
import app.readribbon.design.RibbonScreen
import app.readribbon.design.LocalAppearance
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.QuietHoursBandControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SectionLabel
import app.readribbon.design.Setting
import app.readribbon.design.SettingChoice
import app.readribbon.design.SettingControl
import app.readribbon.design.SettingExample
import app.readribbon.design.SettingNote
import app.readribbon.design.SettingSwitch
import app.readribbon.design.SettingValue
import app.readribbon.design.SettingsGroup
import app.readribbon.design.SmallCaps
import app.readribbon.design.TextInset
import app.readribbon.design.color
import app.readribbon.design.flowsAsWords
import app.readribbon.design.grain
import app.readribbon.design.pressable
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.paper
import app.readribbon.design.well
import app.readribbon.services.Notifications
import app.readribbon.services.cachedRemoteChapter
import java.util.Date
import java.util.TimeZone
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// S19–S22, and S26 — the screens the menu pushes to.
//
// **The new style** (owner's call — deviation A23). These were five screens
// of bare rows under small-caps heads with hairlines between them, and the
// owner's word for the result was "very condensed". The cause was that the
// app had no drawn container at all, so every screen could only be a column
// of sentences, and the only lever left was how much air to put between them.
//
// So the settings are built from tiles now — design/Surfaces.kt — and three
// things follow from that, in order of how much they matter:
//
//   1. **Every row gained a sentence.** A switch used to be four words on a
//      bare ground and you were left to infer what it did. Almost every row
//      is two lines now, and the second says the true small thing: "A touch
//      on the shoulder. No words." A screen that explains itself is
//      friendlier than a screen that is merely softer.
//   2. **The hairlines went.** There were six ruled lines under headings
//      across these screens, which is the church-bulletin energy §13 forbids
//      in its most literal form. The edge of a tile does that work instead,
//      and a two-dp seam of grained ground between tiles is a warmer divider
//      than a rule.
//   3. **Each screen says what it is.** A display-type title and one line
//      under it, where a pushed screen used to open on a 12 sp small-caps
//      word.
//
// What keeps a screen of rounded rectangles from reading as somebody else's
// Settings app — the real risk here, and worth naming: the grain is carried
// onto every tile, so they are paper rather than panels; the corners are
// large; there are no icons; the undoing controls stay off the tiles
// entirely, on the bare ground; and nothing is drawn that does not say
// something. §14's last test is whether the thing was obviously made by a
// person, and a tile with a sentence in it passes that in a way an
// icon-and-chevron list never does.
//
// What is still not here is what was never here: no theme picker (dark is
// the product), no app-icon picker. Appearance (S26) is new and holds exactly
// one switch — the wallpaper's colours, or the brand's. S18's "no accent
// picker: chartreuse is the brand's, not the user's" survives that: it offers
// two rooms, and never a colour somebody chose by hand.
//
// And since A67 the screens show rather than describe. A version is chosen by
// reading it, the size is previewed on a piece of the page itself, a room of
// two is spoken of by the name of the one other person in it, and the quiet
// hours are a night drawn on a band where there were two rows and a clock
// face in a dialog. Material's switch and slider stay (A18/A29): how those
// behave is the platform's, and every Android hand already knows it.

/** The screen's own margin — the app's one gutter. */
private val Margin = 24.dp

/** Between one group and the next. */
private val GroupGap = 30.dp

/** The smallest a control may be tapped at (§11, deviation 12). */
private val MinTarget: Dp = 44.dp

/** Where a subscription is managed on this platform (§6.11). */
private const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

/** Scripture size runs 16 → 24 in half-point steps (S20). */
private const val SCRIPTURE_MIN = 16f
private const val SCRIPTURE_MAX = 24f
private const val SCRIPTURE_STEP = 0.5f

/**
 * Compose counts the stops *between* the ends, where Swift's `step:` counts
 * the distance between them. 16 → 24 by halves is seventeen positions, so
 * fifteen of them are interior.
 */
private val SCRIPTURE_STEPS =
    ((SCRIPTURE_MAX - SCRIPTURE_MIN) / SCRIPTURE_STEP).toInt() - 1

/** Swift's fallback when the bundle has no folder for a translation (S21). */
private const val FALLBACK_MEGABYTES = 5

/** Where Scripture lives in the assets — one folder per translation. */
private const val SCRIPTURE_ASSETS = "scripture"

/**
 * Where the versions and the preview are shown when no book is open: the
 * first verse of John, the beginning the app's own pictures use (A67).
 */
private val NoBookOpen = VerseAddress(bookID = "JHN", chapter = 1, verse = 1)

/**
 * A room's faces beside its name on Notifications: the size its tile in Your
 * rooms draws them, overlapping as they do there (A67).
 */
private val HeaderFace = 18.dp
private val HeaderFaceOverlap = (-5).dp

/** Between a room's faces and its name. */
private val HeaderFaceGap = 8.dp

/**
 * The space a section label keeps under itself (design/Surfaces.kt). The
 * faces stand on the same, so they sit level with the name and not below it.
 */
private val LabelFoot = 10.dp

// MARK: S20 — text and translation

/**
 * Text and translation (S20).
 *
 * Translation is personal, not shared (§2.6); changing it never moves your
 * position or breaks a note's anchor.
 *
 * A version is chosen by reading it (A67): each row carries the verse you
 * are at, in that version's own words, wherever this phone already holds
 * them. And the size previews over a piece of the page itself — that verse
 * and the next, numbered and coloured the way the page sets them — so the
 * size, the spacing and the red letter all show on it. With no book open,
 * both are the first verses of John.
 */
@Composable
fun TextSettingsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Bundled translations always; licensed ones (NKJV first) appear the
    // day their edition is configured on the proxy — never as a dead row.
    val translations = model.availableTranslations
    val place = readingPlace(model)
    val held = rememberHeldChapters(model, place, translations)
    // Quoted the way a note quotes a verse: the words of it, run together.
    val specimens = translations
        .mapNotNull { translation ->
            held.chapters[translation.id]?.text(place.verse)?.let { translation.id to it }
        }
        .toMap()

    SettingsScaffold(
        route = Flows.TEXT,
        title = Copy.TEXT_AND_TRANSLATION,
        lede = Copy.TEXT_LEDE,
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            count = translations.size,
            title = Copy.TRANSLATION,
            detail = Copy.TRANSLATION_IS_YOURS,
            // Which verse the rows are showing, and only when one of them is
            // showing it: a line about a verse nobody can see is a line
            // about nothing. While the rows are still being read it is
            // there already, so the verses arriving under it do not also
            // bring a line of their own and move the page group down twice.
            footnote = if (specimens.isEmpty() && held.read) null else Copy.specimenAt(place.formatted),
        ) {
            translations.forEach { translation ->
                SettingChoice(
                    title = translation.displayName,
                    subtitle = if (translation.isBundled) {
                        Copy.bundledSub(context)
                    } else {
                        Copy.streamsSub(context)
                    },
                    specimen = specimens[translation.id],
                    chosen = model.words(model.currentRoom) == translation.id,
                    onClick = { model.setTranslation(translation.id) },
                )
            }
        }

        Air(GroupGap)

        SettingsGroup(count = 3, title = Copy.THE_PAGE, detail = Copy.THE_PAGE_IS_YOURS) {
            SettingControl(title = Copy.TEXT_SIZE, detail = Copy.TEXT_SIZE_SUB) {
                ScriptureSizeWell(model)
                ScripturePreview(model, place, held.chapters[model.words(model.currentRoom)])
            }
            SettingControl(title = Copy.LINE_SPACING, detail = Copy.LINE_SPACING_SUB) {
                Segments(
                    labels = listOf(
                        Copy.LINE_SPACING_CLOSE,
                        Copy.LINE_SPACING_BOOK,
                        Copy.LINE_SPACING_OPEN,
                    ),
                    chosenIndex = model.settings.lineSpacingStep,
                    onSelect = { index ->
                        model.updateSettings { it.copy(lineSpacingStep = index) }
                    },
                )
            }
            SettingSwitch(
                title = Copy.RED_LETTER,
                subtitle = Copy.RED_LETTER_SUB,
                value = model.settings.redLetter,
                onChange = { on -> model.updateSettings { it.copy(redLetter = on) } },
            )
        }
    }
}

/**
 * The verse the versions and the preview are shown at: where you are in the
 * current room's open book — yours, not the room's — or, with none open,
 * the first verse of John.
 */
private fun readingPlace(model: AppModel): VerseAddress =
    model.currentRoom
        ?.let { room -> model.openReading(room) }
        ?.let { reading -> model.myPosition(reading) }
        ?: NoBookOpen

/**
 * Each version's chapter at [place], as this phone already holds it: a
 * bundled version always, a licensed one only once that chapter has streamed
 * here for the book being read. Nothing is fetched to fill a settings screen
 * (A67): licensed text streams to be read, and the phone keeps it only for
 * the book being read — a row is no reason to ask the proxy for a chapter.
 *
 * Read off the main thread, as the original panel reads the room's versions
 * (reading/RoomSection.kt): a bundled book is a JSON file to parse and a
 * licensed chapter is a file on disk. The first frame does not wait for them
 * and does not start empty either: whatever book is already parsed — the one
 * being read, almost always — is there from the start, so the version you
 * read, its verse and the page under the size are drawn with the screen and
 * only the others arrive after it (and arrive, rather than appear, in
 * SettingChoice and ScripturePreview). What was read stays up while a new
 * place is.
 */
@Composable
private fun rememberHeldChapters(
    model: AppModel,
    place: VerseAddress,
    translations: List<Translation>,
): HeldChapters {
    val context = LocalContext.current
    val parsed = remember(model, place, translations) {
        buildMap<TranslationID, ScriptureChapter> {
            for (translation in translations) {
                if (!translation.isBundled) continue
                model.scripture.parsedChapter(place, translation.id)?.let { put(translation.id, it) }
            }
        }
    }
    val held by produceState(HeldChapters(parsed, read = false), model, place, translations) {
        val chapters = withContext(Dispatchers.IO) {
            buildMap<TranslationID, ScriptureChapter> {
                for (translation in translations) {
                    val chapter = if (translation.isBundled) {
                        model.scripture.chapter(place, translation.id)
                    } else {
                        model.scripture.cachedRemoteChapter(context, place, translation)
                    }
                    if (chapter != null) put(translation.id, chapter)
                }
            }
        }
        value = HeldChapters(chapters, read = true)
    }
    return held
}

/**
 * What [rememberHeldChapters] holds: each version's chapter it has, and
 * whether the reading for the place has finished — until it has, a version
 * missing from [chapters] may yet arrive.
 */
private data class HeldChapters(
    val chapters: Map<TranslationID, ScriptureChapter>,
    val read: Boolean,
)

/**
 * The size slider, in its well, between a small A and a large one.
 *
 * Material's own slider (A18/A29) with the ticks turned off: seventeen drawn
 * stops is an instrument panel, and this is a book. The two letters are the
 * ends of the scale set in the face the slider sizes, so the control says
 * what it does before it is touched (A67) — a picture, not a word, and
 * nobody hears it. The well is the page's own ground rather than a paler
 * surface, so a control sits *in* its tile rather than on it.
 */
@Composable
private fun ScriptureSizeWell(model: AppModel) {
    val size = model.settings.scriptureSize
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .well(RibbonShape.rowShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScaleEnd(13f)
        Slider(
            value = size.toFloat(),
            onValueChange = { next ->
                model.updateSettings { it.copy(scriptureSize = next.toDouble()) }
            },
            valueRange = SCRIPTURE_MIN..SCRIPTURE_MAX,
            steps = SCRIPTURE_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = Palette.accent,
                activeTrackColor = Palette.accent,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = Palette.rule,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = Copy.TEXT_SIZE
                    // Said as a size in points, a measure of type, where
                    // Material would say how far along the bar it is.
                    stateDescription = Copy.textSizeValue(size)
                },
        )
        ScaleEnd(21f)
    }
}

/** One end of the size scale: the letter A, at that end's size, unheard. */
@Composable
private fun ScaleEnd(size: Float) {
    Text(
        text = "A",
        style = RibbonType.scripture(size),
        color = Palette.muted,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/**
 * The live preview, as a page (A67): the verse you are at and the one after
 * it, in your version, at your size and spacing, with the reference under it.
 *
 * On the page's own ground and grain, because it is a window onto the reading
 * surface and not a sample of it. Swift adds its leading with `.lineSpacing`,
 * which is the gap *between* lines; Compose sets the line box itself, so the
 * multiple is applied to the whole line height — the same arithmetic the
 * reading surface does.
 *
 * @param chapter your version's chapter, as this phone holds it; with none,
 *   no preview, as there never was one for words the phone does not have.
 */
@Composable
private fun ScripturePreview(model: AppModel, place: VerseAddress, chapter: ScriptureChapter?) {
    val size = model.settings.scriptureSize.toFloat()
    val redLetter = model.settings.redLetter
    // Read here, in composition, and handed to the typesetter: the room's
    // ink follows the wallpaper (A18), and the page is set from it.
    val ivory = Palette.text
    val page = remember(chapter, place.verse, size, redLetter, ivory) {
        chapter?.let { pageOf(it, place.verse, size, redLetter, ivory) }
    }
    // A page read after the screen was drawn opens under the size rather
    // than landing on one frame and pushing everything below it down.
    ArrivingLate(page) { shown -> PreviewPage(model, place, shown, size, ivory) }
}

@Composable
private fun PreviewPage(
    model: AppModel,
    place: VerseAddress,
    page: AnnotatedString,
    size: Float,
    ivory: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .well(RibbonShape.rowShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = page,
            style = RibbonType.scripture(size).copy(
                lineHeight = (size * model.settings.lineHeightMultiple.toFloat()).sp,
            ),
            color = ivory,
        )
        SmallCaps(place.formatted, size = 11f)
    }
}

/**
 * Two verses set the way reading/ChapterText.kt sets them, so the preview is
 * the page and not a description of it: a number before each verse but a
 * chapter's first — small caps at 0.62 of the size, ivory at 45%, raised,
 * and a thin space after it — the words of Jesus in the crimson ink when the
 * switch is on, each block of the chapter on a line of its own, and a psalm's
 * title and a stanza break left out.
 *
 * The walk is the page's: a running verse moved by every number, a title's
 * included, so a verse that begins on a title still finds its words in the
 * line after it. Null when the chapter does not have the verse.
 *
 * Plain colours only — this sets type, it does not compose, so whatever reads
 * the room's palette reads it first and hands it in.
 */
private fun pageOf(
    chapter: ScriptureChapter,
    first: Int,
    size: Float,
    redLetter: Boolean,
    ivory: Color,
): AnnotatedString? {
    val number = RibbonType.smallCaps(size * 0.62f).toSpanStyle().copy(
        color = ivory.copy(alpha = 0.45f),
        // The page's own lift: 0.3 of the body size, which as a fraction of
        // the number's own 0.62 is 0.484.
        baselineShift = BaselineShift(0.484f),
        // A superscript numeral wants no tracking, and tracking would open
        // the thin space after it into a word space.
        letterSpacing = 0.sp,
    )
    val red = SpanStyle(color = Ink.crimson.color)
    var running: Int? = null
    val numbered = mutableSetOf<Int>()
    val page = buildAnnotatedString {
        for (block in chapter.blocks) {
            if (block.s == BlockStyle.b) continue
            var wrote = false
            for (span in block.x) {
                val v = span.v
                if (v != null) running = v
                val verse = running
                if (block.s == BlockStyle.d || verse == null) continue
                if (verse != first && verse != first + 1) continue
                if (!wrote && length > 0) append('\n')
                wrote = true
                if (verse != 1 && numbered.add(verse)) {
                    withStyle(number) { append("$verse ") }
                }
                if (redLetter && span.isRedLetter) {
                    withStyle(red) { append(span.t) }
                } else {
                    append(span.t)
                }
            }
        }
    }
    return page.takeIf { it.isNotEmpty() }
}

/**
 * Three choices, one of them lifted.
 *
 * Drawn rather than Material's `SingleChoiceSegmentedButtonRow`, which brings
 * its own outlines, its own tick and its own container colour — three pieces
 * of chrome in a room that has none.
 *
 * The selected segment is a pill that *moves*. That is the whole reason this
 * is hand-drawn: one object sliding between three places is the true account
 * of what happened, and three segments changing colour on the same frame is
 * not. It rides the same critically damped spring every other small moving
 * thing in the app does, so it arrives without overshooting (§9.1).
 */
@Composable
private fun Segments(
    labels: List<String>,
    // Not `selected`: the semantics property of that name is what each
    // segment sets below, and a shadowed parameter there is a silent bug.
    chosenIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val still = rememberReduceMotion()
    val at by animateFloatAsState(
        targetValue = chosenIndex.toFloat(),
        animationSpec = RibbonMotion.handled(still),
        label = "the-chosen-segment",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .well(RibbonShape.rowShape),
    ) {
        val cell = maxWidth / labels.size
        // Through `paper`, not a bare fill: on Ribbon's own palette the pill
        // and the groove it runs in are 1.05:1 apart, so a filled pill is no
        // pill at all and which of three words is chosen would be carried by
        // the text's brightness alone. The edge comes with the helper.
        Box(
            Modifier
                // The lambda overload, because `at` is animating: this way
                // the pill's travel is a layout change per frame rather than
                // a recomposition per frame.
                .offset { IntOffset(((cell * at) + 4.dp).roundToPx(), 4.dp.roundToPx()) }
                .width(cell - 8.dp)
                .height(44.dp)
                .paper(RibbonShape.smallShape),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val chosen = index == chosenIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .sizeIn(minHeight = MinTarget)
                        .clip(RibbonShape.smallShape)
                        .pressable(role = Role.RadioButton) { onSelect(index) }
                        // The lifted pill is a shape, and a shape is not
                        // enough on its own for somebody who cannot see it
                        // (§11): the segment says it is the chosen one.
                        .semantics { selected = chosen },
                    contentAlignment = Alignment.Center,
                ) {
                    // The pill slides on a spring and its three labels used
                    // to change colour on one frame, so the object that moved
                    // and the words it moved between were telling two
                    // different stories about the same event. On the same
                    // token the pill rides, so a word brightens as the pill
                    // reaches it.
                    val wordColour by animateColorAsState(
                        targetValue = if (chosen) Palette.text else Palette.muted,
                        animationSpec = RibbonMotion.handled(still),
                        label = "the-chosen-word",
                    )
                    Text(
                        text = label,
                        style = RibbonType.ui(15f),
                        color = wordColour,
                    )
                }
            }
        }
    }
}

// MARK: S19 — notifications

/**
 * Notifications (S19).
 *
 * Per room, not global: you want everything from your wife and almost
 * nothing from the Thursday study. The finished-book note has no switch — it
 * fires a handful of times a year and is an invitation back, not an absence
 * notification. Nothing here is about absence, lapses, streaks, or reminders
 * to read, because those notifications don't exist.
 *
 * They say who (A67). Each room's switches sit under its faces, and in a
 * room of two — where "they" is one person — the switches name them and show
 * the notification itself, their face beside the words that will arrive. A
 * room of three or more, or one you are alone in, reads as it always did.
 */
@Composable
fun NotificationSettingsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        route = Flows.NOTIFICATIONS,
        title = Copy.NOTIFICATIONS,
        lede = Copy.NOTIFICATIONS_LEDE,
        onBack = onBack,
        modifier = modifier,
    ) {
        AndroidIsSilencingThese()
        model.state.rooms.forEachIndexed { index, room ->
            if (index > 0) Air(GroupGap)
            NotificationRoomGroup(model = model, room = room)
        }
        Air(GroupGap)
        QuietHoursGroup(model)
    }
}

/**
 * The line that appears only when Android is dropping everything this screen
 * configures — refused at the prompt, or turned off later in the OS.
 *
 * On the bare ground rather than in a tile, which is where this screen's
 * undoing controls already stand (deviation A23): a tile is a thing Ribbon
 * decides, and this is a fact about the phone. The switches below stay
 * enabled and keep their values — they are the person's answer to Ribbon's
 * question, and greying them out would make the OS's answer look like ours,
 * which is §12.2's Law 5 backwards.
 *
 * Re-read on every resume rather than once at composition, because the
 * likeliest way this line goes away is the person tapping the control under
 * it, changing the switch in Android's settings, and coming straight back.
 */
@Composable
private fun AndroidIsSilencingThese() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val still = rememberReduceMotion()
    var allowed by remember { mutableStateOf(true) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            allowed = Notifications.allowed(context)
        }
    }

    // Grows in and shrinks away rather than appearing: coming back from
    // Android's settings having just turned notifications on is the one
    // moment anybody watches this line, and §9.1 has no cuts in it.
    AnimatedVisibility(
        visible = !allowed,
        // The token takes the reduce-motion branch itself, as every other
        // call site in this file does — calling it bare left the one line on
        // the screen that still moved for somebody who had asked nothing to.
        enter = fadeIn(RibbonMotion.settle(still)) + expandVertically(RibbonMotion.settle(still)),
        exit = fadeOut(RibbonMotion.settle(still)) + shrinkVertically(RibbonMotion.settle(still)),
        label = "android-is-silencing-these",
    ) {
        Column(
            modifier = Modifier.padding(bottom = GroupGap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = Copy.ANDROID_IS_NOT_PASSING_THESE_ON,
                style = RibbonType.ui(15f),
                color = Palette.muted,
            )
            QuietControl(
                title = Copy.OPEN_ANDROIDS_SETTINGS,
                modifier = Modifier.offset(x = (-8).dp),
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NotificationRoomGroup(model: AppModel, room: Room) {
    val prefs = model.notificationPrefs(room)

    fun set(next: RoomNotificationPrefs) = model.setNotificationPrefs(next, room)

    val reading = model.openReading(room)
    val book = reading?.let { Bible.book(it.bookID)?.name }

    // A room of two names the other person, in the words their
    // notifications will use. A notification that names a verse or a book
    // is shown rather than described — and where there is no verse or book
    // to put in it yet, the switch says what it always said.
    val other = theOther(model, room)
    fun example(from: TheOther, sentence: String) = SettingExample(
        person = from.person,
        ink = from.ink,
        portrait = model.portrait(from.person.id),
        sentence = sentence,
    )
    val notesExample = if (other != null && reading != null) {
        example(other, Copy.notifNoteLeft(other.name, model.myPosition(reading).formatted))
    } else {
        null
    }
    val bookExample = if (other != null && book != null) {
        example(other, Copy.notifReading(other.name, book))
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // What the room is reading, beside its name — the same precedent the
        // menu's room rows set. An address, never a score (Law 2), and with
        // two or three rooms it is the thing that tells them apart.
        FacesAndName(model = model, room = room, detail = book)
        SettingsGroup(count = 4) {
            SettingSwitch(
                title = Copy.NOTES_LEFT_FOR_YOU,
                subtitle = if (notesExample == null) Copy.NOTES_LEFT_FOR_YOU_SUB else null,
                example = notesExample,
                value = prefs.notesLeft,
                onChange = { on -> set(prefs.copy(notesLeft = on)) },
            )
            SettingSwitch(
                title = Copy.CARDS_OPEN,
                subtitle = other?.let { Copy.cardsOpenSubNamed(it.name) } ?: Copy.CARDS_OPEN_SUB,
                value = prefs.cardsOpen,
                onChange = { on -> set(prefs.copy(cardsOpen = on)) },
            )
            SettingSwitch(
                title = other?.let { Copy.whenNameOpensTheBook(it.name) } ?: Copy.WHEN_THEY_OPEN_THE_BOOK,
                subtitle = if (bookExample == null) Copy.WHEN_THEY_OPEN_THE_BOOK_SUB else null,
                example = bookExample,
                value = prefs.whenTheyOpenTheBook,
                onChange = { on -> set(prefs.copy(whenTheyOpenTheBook = on)) },
            )
            SettingSwitch(
                title = Copy.THINKING_OF_YOU,
                subtitle = other?.let { Copy.thinkingOfYouSubNamed(it.name) } ?: Copy.THINKING_OF_YOU_SUB,
                value = prefs.thinkingOfYou,
                onChange = { on -> set(prefs.copy(thinkingOfYou = on)) },
            )
        }
    }
}

/**
 * A room's section label with its faces before it (A67) — the same
 * overlapping faces its tile in Your rooms draws, so the room is known by
 * who is in it as well as by its name.
 *
 * The label stays the heading a screen reader stops at. The faces are a
 * picture and say nothing: read aloud they would be a roll call, and a room
 * is never counted (Law 2).
 */
@Composable
private fun FacesAndName(model: AppModel, room: Room, detail: String?) {
    val members = model.members(room)
    Row(
        modifier = Modifier.padding(start = TextInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (members.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(end = HeaderFaceGap, bottom = LabelFoot)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(HeaderFaceOverlap),
            ) {
                members.forEach { membership ->
                    PortraitView(
                        person = model.person(membership.personID),
                        ink = membership.ink,
                        size = HeaderFace,
                        image = model.portrait(membership.personID),
                    )
                }
            }
        }
        SectionLabel(
            title = model.displayName(room),
            detail = detail,
            modifier = Modifier.spendingLeadingInset(TextInset),
        )
    }
}

/**
 * Lets a section label stand straight after what is drawn before it. The
 * label keeps its own inset from the tile's edge, which beside the faces is
 * already spent: the inset is laid outside the label's leading edge, over
 * the faces' side, and only the rest of the label takes room in the row.
 */
private fun Modifier.spendingLeadingInset(inset: Dp): Modifier = layout { measurable, constraints ->
    val spent = inset.roundToPx()
    val widened = constraints.copy(
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + spent else Constraints.Infinity,
    )
    val placeable = measurable.measure(widened)
    layout(maxOf(0, placeable.width - spent), placeable.height) {
        placeable.placeRelative(-spent, 0)
    }
}

/**
 * The one other person in a room of two, called what their notifications
 * call them: the first word of their name.
 */
private data class TheOther(val person: Person, val ink: Ink?) {
    val name: String get() = firstName(person.name)
}

/**
 * Null alone, at three or more, and for somebody this phone does not know by
 * name yet — a switch never names a blank.
 */
private fun theOther(model: AppModel, room: Room): TheOther? {
    val me = model.me?.id
    val membership = model.members(room).filter { it.personID != me }.singleOrNull() ?: return null
    val person = model.person(membership.personID) ?: return null
    if (person.name.isBlank()) return null
    return TheOther(person = person, ink = membership.ink)
}

/**
 * Quiet hours (S19), as one tile (A67): the night drawn as a band, and over
 * it the two times in a sentence — in the same clock the band's ends are
 * spoken in, so what is read and what is heard agree.
 *
 * It was two rows, each opening Material's clock face in a dialog: the same
 * question asked twice, about one thing. The thing is a night, and a night
 * is a stretch of a day, so that is what is drawn — and moving either end
 * is written through to the settings as it moves, as the dial applied as it
 * was turned.
 */
@Composable
private fun QuietHoursGroup(model: AppModel) {
    val context = LocalContext.current
    val start = model.settings.quietHoursStart
    val end = model.settings.quietHoursEnd
    // Both ends on one minute is no quiet hours at all, not all day — the
    // test the band draws by and the one notifications are held by.
    val title = if (QuietHoursBand.wrapped(start) == QuietHoursBand.wrapped(end)) {
        Copy.NO_QUIET_HOURS
    } else {
        Copy.quietHoursFromUntil(clock(context, start), clock(context, end))
    }

    SettingsGroup(
        count = 1,
        title = Copy.QUIET_HOURS,
        footnote = Copy.THINKING_OF_YOU_STILL_ARRIVES,
    ) {
        SettingControl(title = title, detail = Copy.QUIET_HOURS_BAND_SUB) {
            QuietHoursBandControl(
                start = start,
                end = end,
                onStart = { m -> model.updateSettings { it.copy(quietHoursStart = m) } },
                onEnd = { m -> model.updateSettings { it.copy(quietHoursEnd = m) } },
            )
        }
    }
}

/**
 * A minute of the day as this phone tells the time, in its owner's 12- or
 * 24-hour convention — the clock design/Drawn.kt speaks the band's two ends
 * in, kept the same here so the sentence and the spoken ends never disagree.
 *
 * On the first of January 1970 in UTC rather than today here, so the morning
 * the clocks go forward cannot tell half past two as half past three.
 */
private fun clock(context: Context, minute: Int): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(QuietHoursBand.wrapped(minute) * MINUTE_MS))
}

private const val MINUTE_MS = 60_000L

// MARK: S26 — appearance

/**
 * Appearance (S26 — new, deviation A18).
 *
 * One switch, and the sentence that says what it does. §12.2 declined
 * Material You outright and the owner overruled it; this is the way back for
 * anyone who wants Ribbon's own chartreuse, and the place the app admits, in
 * plain words, that the fire is the one thing the wallpaper never repaints.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppearance.current

    SettingsScaffold(
        route = Flows.APPEARANCE,
        title = Copy.APPEARANCE,
        lede = Copy.APPEARANCE_LEDE,
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            count = 1,
            title = Copy.COLOUR,
            footnote = Copy.THE_FIRE_STAYS_WARM,
        ) {
            SettingSwitch(
                title = Copy.WALLPAPER_COLOUR,
                subtitle = Copy.WALLPAPER_COLOUR_WHY,
                value = appearance.wallpaperColour,
                onChange = { on -> appearance.wallpaperColour = on },
            )
        }
    }
}

// MARK: S21 — downloads

/**
 * Downloads (S21).
 *
 * Megabytes are a fine number: they measure a device, not a person. This is
 * the boundary of Law 2 and it is worth stating so nobody over-applies the
 * rule into unusability. Both launch translations ship in the app, whole.
 */
@Composable
fun DownloadsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assets = remember(context) { context.assets }
    val translations = model.availableTranslations

    SettingsScaffold(
        route = Flows.DOWNLOADS,
        title = Copy.DOWNLOADS,
        lede = Copy.downloadsLede(context),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            count = translations.size,
            title = Copy.onThisPhone(context),
            footnote = Copy.voiceNotesPolicy(context),
        ) {
            translations.forEach { translation ->
                val bundled = translation.isBundled
                val megabytes = remember(assets, translation.id, bundled) {
                    if (bundled) bundledMegabytes(assets, translation.id) else 0
                }
                SettingValue(
                    title = translation.fullName,
                    // Licensed text streams; the book being read stays on the
                    // phone, the rest doesn't — its license, not our design.
                    value = if (bundled) Copy.megabytes(megabytes) else Copy.STREAMS,
                )
            }
        }
    }
}

/**
 * How much of the device one bundled translation is using.
 *
 * Swift adds up the sizes of the JSON files in `Scripture/<id>`; the same
 * files are assets here, and an asset stream reports its own full length
 * without being read, so this costs a header open per book rather than a
 * megabyte of I/O. Like Swift's, it is the size of the text as it is stored
 * rather than as it is packed, which is the number a person would recognise.
 */
private fun bundledMegabytes(assets: AssetManager, translation: TranslationID): Int {
    val folder = "$SCRIPTURE_ASSETS/${translation.rawValue}"
    val names = runCatching { assets.list(folder) }.getOrNull()
    if (names == null || names.isEmpty()) return FALLBACK_MEGABYTES
    var bytes = 0L
    for (name in names) {
        bytes += runCatching {
            assets.open("$folder/$name").use { it.available().toLong() }
        }.getOrDefault(0L)
    }
    return maxOf(1, (bytes / 1_000_000L).toInt())
}

// MARK: S22 — the plan

/**
 * The plan (S22).
 *
 * The first book is free, all the way through — not a 7-day trial, because a
 * clock is a count. The ask appears in exactly two places: the shelf after
 * the first ember, and here. A non-paying member never sees a price and never
 * learns who pays.
 */
@Composable
fun PlanScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val room = model.currentRoom
    val paused = room != null && room.isPaused

    SettingsScaffold(
        route = Flows.PLAN,
        title = Copy.PLAN,
        lede = Copy.PLAN_LEDE,
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            count = if (paused) 2 else 1,
            footnote = Copy.THE_ASK_COMES_ONCE,
        ) {
            SettingNote(Copy.FIRST_BOOK_FREE)
            if (paused) {
                // S22's anatomy is "Start the room again if paused · manage
                // in the store", and the restore half needs billing that does
                // not exist yet on either platform (deviation 11). What stood
                // here once was a chartreuse capsule with an empty body: a
                // control that says exactly what happens and then does not do
                // it, which is worse than no control. So the row says the
                // true thing instead, and the store is where the other half
                // of it lives.
                Setting(
                    title = Copy.MANAGE_IN_STORE,
                    subtitle = Copy.ROOM_PAUSED,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, PLAY_SUBSCRIPTIONS.toUri()),
                        )
                    },
                )
            }
        }
    }
}

// MARK: - The scaffold

/**
 * A pushed settings screen: a way back, a title, a line saying what this is,
 * and its groups.
 *
 * The title carries the flow key of the row that opened it, so the words a
 * finger just touched in the menu are the words that become the heading — the
 * screen grows out of the row rather than replacing it (design/Flow.kt).
 *
 * Both insets are this screen's to carry. These used to live inside a bottom
 * sheet, which cleared the status bar for them; the menu is a full-screen
 * layer now (deviations 14, A17) and nothing above them clears anything.
 */
@Composable
private fun SettingsScaffold(
    route: String,
    title: String,
    lede: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    RibbonScreen(
        title = title,
        lede = lede,
        titleModifier = Modifier.flowsAsWords(Flows.settingsTitle(route)),
        onBack = onBack,
        modifier = modifier,
        content = content,
    )
}
