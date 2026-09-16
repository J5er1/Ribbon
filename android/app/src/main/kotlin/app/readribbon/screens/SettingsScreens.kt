@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import android.content.Intent
import android.content.res.AssetManager
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.Room
import app.readribbon.core.TranslationID
import app.readribbon.data.RoomNotificationPrefs
import app.readribbon.design.Air
import app.readribbon.design.Flows
import app.readribbon.design.RibbonScreen
import app.readribbon.design.GroupScope
import app.readribbon.design.LocalAppearance
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.Setting
import app.readribbon.design.SettingChoice
import app.readribbon.design.SettingControl
import app.readribbon.design.SettingNote
import app.readribbon.design.SettingSwitch
import app.readribbon.design.SettingValue
import app.readribbon.design.SettingsGroup
import app.readribbon.design.SmallCaps
import app.readribbon.design.flowsAsWords
import app.readribbon.design.grain
import app.readribbon.design.pressable
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.design.paper
import app.readribbon.design.well
import java.util.Calendar
import kotlin.uuid.ExperimentalUuidApi

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

// MARK: S20 — text and translation

/**
 * Text and translation (S20).
 *
 * Translation is personal, not shared (§2.6); changing it never moves your
 * position or breaks a note's anchor. The size slider previews live over real
 * Scripture — the verse you were last reading, which is a small thing and the
 * kind of small thing this product is made of.
 */
@Composable
fun TextSettingsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    SettingsScaffold(
        route = Flows.TEXT,
        title = Copy.TEXT_AND_TRANSLATION,
        lede = Copy.TEXT_LEDE,
        onBack = onBack,
        modifier = modifier,
    ) {
        // Bundled translations always; licensed ones (NKJV first) appear the
        // day their edition is configured on the proxy — never as a dead row.
        val translations = model.availableTranslations
        SettingsGroup(count = translations.size, title = Copy.TRANSLATION) {
            translations.forEach { translation ->
                SettingChoice(
                    title = translation.displayName,
                    subtitle = if (translation.isBundled) {
                        Copy.bundledSub(context)
                    } else {
                        Copy.streamsSub(context)
                    },
                    chosen = model.me?.translation == translation.id,
                    onClick = { model.setTranslation(translation.id) },
                )
            }
        }

        Air(GroupGap)

        SettingsGroup(count = 3, title = Copy.THE_PAGE) {
            SettingControl(title = Copy.TEXT_SIZE, detail = Copy.TEXT_SIZE_SUB) {
                ScriptureSizeWell(model)
                ScripturePreview(model)
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
 * The size slider, in its well.
 *
 * Material's own slider with the ticks turned off: seventeen drawn stops is
 * an instrument panel, and this is a book. The well is the page's own ground
 * rather than a paler surface, so a control sits *in* its tile rather than
 * on it.
 */
@Composable
private fun ScriptureSizeWell(model: AppModel) {
    Box(
        Modifier
            .fillMaxWidth()
            .well(RibbonShape.rowShape)
            .padding(horizontal = 14.dp),
    ) {
        Slider(
            value = model.settings.scriptureSize.toFloat(),
            onValueChange = { size ->
                model.updateSettings { it.copy(scriptureSize = size.toDouble()) }
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
            modifier = Modifier.semantics { contentDescription = Copy.TEXT_SIZE },
        )
    }
}

/**
 * The live preview: the verse you were last reading, in your translation, at
 * your size.
 *
 * On the page's own ground and grain, because it is a window onto the reading
 * surface and not a sample of it. Swift adds its leading with `.lineSpacing`,
 * which is the gap *between* lines; Compose sets the line box itself, so the
 * multiple is applied to the whole line height — the same arithmetic the
 * reading surface does.
 */
@Composable
private fun ScripturePreview(model: AppModel) {
    val me = model.me ?: return
    val room = model.currentRoom ?: return
    val reading = model.openReading(room) ?: return
    val position = model.myPosition(reading)
    val text = model.scripture.verseText(position, translation = me.translation) ?: return

    val size = model.settings.scriptureSize.toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .well(RibbonShape.rowShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = RibbonType.scripture(size).copy(
                lineHeight = (size * model.settings.lineHeightMultiple.toFloat()).sp,
            ),
            color = Palette.text,
        )
        SmallCaps(position.formatted, size = 11f)
    }
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
                    Text(
                        text = label,
                        style = RibbonType.ui(15f),
                        color = if (chosen) Palette.text else Palette.muted,
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
        model.state.rooms.forEachIndexed { index, room ->
            if (index > 0) Air(GroupGap)
            NotificationRoomGroup(model = model, room = room)
        }
        Air(GroupGap)
        QuietHoursGroup(model)
    }
}

@Composable
private fun NotificationRoomGroup(model: AppModel, room: Room) {
    val prefs = model.notificationPrefs(room)

    fun set(next: RoomNotificationPrefs) = model.setNotificationPrefs(next, room)

    SettingsGroup(
        count = 4,
        title = model.displayName(room),
        // What the room is reading, beside its name — the same precedent the
        // menu's room rows set. An address, never a score (Law 2), and with
        // two or three rooms it is the thing that tells them apart.
        detail = model.openReading(room)?.let { Bible.book(it.bookID)?.name },
    ) {
        SettingSwitch(
            title = Copy.NOTES_LEFT_FOR_YOU,
            subtitle = Copy.NOTES_LEFT_FOR_YOU_SUB,
            value = prefs.notesLeft,
            onChange = { on -> set(prefs.copy(notesLeft = on)) },
        )
        SettingSwitch(
            title = Copy.CARDS_OPEN,
            subtitle = Copy.CARDS_OPEN_SUB,
            value = prefs.cardsOpen,
            onChange = { on -> set(prefs.copy(cardsOpen = on)) },
        )
        SettingSwitch(
            title = Copy.WHEN_THEY_OPEN_THE_BOOK,
            subtitle = Copy.WHEN_THEY_OPEN_THE_BOOK_SUB,
            value = prefs.whenTheyOpenTheBook,
            onChange = { on -> set(prefs.copy(whenTheyOpenTheBook = on)) },
        )
        SettingSwitch(
            title = Copy.THINKING_OF_YOU,
            subtitle = Copy.THINKING_OF_YOU_SUB,
            value = prefs.thinkingOfYou,
            onChange = { on -> set(prefs.copy(thinkingOfYou = on)) },
        )
    }
}

@Composable
private fun QuietHoursGroup(model: AppModel) {
    SettingsGroup(
        count = 2,
        title = Copy.QUIET_HOURS,
        footnote = Copy.THINKING_OF_YOU_STILL_ARRIVES,
    ) {
        MinuteRow(
            title = Copy.QUIET_HOURS_FROM,
            minutes = model.settings.quietHoursStart,
            onChange = { m -> model.updateSettings { it.copy(quietHoursStart = m) } },
        )
        MinuteRow(
            title = Copy.QUIET_HOURS_UNTIL,
            minutes = model.settings.quietHoursEnd,
            onChange = { m -> model.updateSettings { it.copy(quietHoursEnd = m) } },
        )
    }
}

/**
 * One end of quiet hours, as minutes from midnight.
 *
 * Swift uses a compact `DatePicker` with `.hourAndMinute`, which shows the
 * time and opens a wheel over it. Compose has no compact time field: the
 * platform's time control is Material's dial, in a dialog. So the time is the
 * row's own trailing value, in the device's own 12- or 24-hour convention,
 * and tapping the row opens the dial.
 *
 * The dial has no confirm button, because Swift's picker has none either —
 * it applies as it is turned. Closing the dial, by back or by a tap outside,
 * takes what it is showing.
 */
@Composable
private fun GroupScope.MinuteRow(
    title: String,
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    val label = remember(minutes, context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        DateFormat.getTimeFormat(context).format(calendar.time)
    }

    Setting(
        title = title,
        value = label,
        chevron = false,
        onClick = { picking = true },
    )

    if (picking) {
        val state = rememberTimePickerState(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        BasicAlertDialog(
            onDismissRequest = {
                onChange(state.hour * 60 + state.minute)
                picking = false
            },
        ) {
            Box(Modifier.clip(RoundedCornerShape(RibbonShape.sheet))) {
                // The ground and its grain sit behind the dial rather than
                // under it: `.room()` hides its node from accessibility, and
                // a dialog that hid itself would take the dial with it.
                Box(Modifier.matchParentSize().room())
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        }
    }
}

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
