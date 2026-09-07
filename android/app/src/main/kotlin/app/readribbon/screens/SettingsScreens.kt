@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

import android.content.Intent
import android.content.res.AssetManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Room
import app.readribbon.core.TranslationID
import app.readribbon.data.RoomNotificationPrefs
import app.readribbon.design.HairlineRule
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.readableColumn
import app.readribbon.design.room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.uuid.ExperimentalUuidApi

// S19–S22 — the four screens the menu pushes to: text and translation,
// notifications, downloads, the plan.
//
// You itself (S18) is no longer a sheet of its own. It is a section of the
// menu, with the rooms and the room you are in — MenuScreen.kt, and
// docs/deviations.md 14. What is still not here is what was never here: no
// theme picker (dark is the product), no accent picker (chartreuse is the
// brand's, not the user's), no app-icon picker.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it
// is on every other screen here. It holds Compose snapshot state, so reading
// `model.settings` or `model.state.rooms` recomposes exactly as `@Observable`
// does.

/**
 * A group's label, announced as the heading it is drawn as. TalkBack's
 * heading swipe is how a screen reader skims a screen; without this, reaching
 * quiet hours past four rooms of switches means swiping through every one of
 * them (§11).
 */
private val HeadingModifier: Modifier = Modifier.semantics { heading() }

/** Each pushed settings screen's own `.padding(24)`. */
private val ScreenPadding = 24.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the glyph;
 * Swift grows the target outward instead and leaves the words where they
 * were. Pulling the control back by that same 8 dp puts it on the screen's
 * own margin — the same correction the menu makes.
 */
private val QuietControlInset = (-8).dp

/**
 * Where a subscription is managed on this platform. The store owns billing,
 * and §6.11 says a member never sees it — this is the way out for whoever
 * does.
 */
private const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

/**
 * The smallest a control may be tapped at (§11, deviation 12). Every gesture
 * has a tap equivalent and every target clears a finger, even where the drawn
 * thing is smaller.
 */
private val MinTarget: Dp = 44.dp

/** The selected-translation dot: Swift's `Circle().frame(width: 6, height: 6)`. */
private val SelectionDot = 6.dp

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

// S20 — text and translation. Translation is personal, not shared (§2.6);
// changing it never moves your position or breaks a note's anchor. The
// size slider previews live over real Scripture — the verse you were last
// reading, which is a small thing and the kind of small thing this product
// is made of.

/**
 * Text and translation (S20).
 *
 * @param onBack pop back to You. iOS draws this chevron for free.
 */
@Composable
fun TextSettingsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScroll(modifier = modifier) {
        BackControl(onBack)
        Column(
            modifier = Modifier.padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCaps(Copy.TRANSLATION, size = 12f, modifier = HeadingModifier)
                // Bundled translations always; licensed ones (NKJV first)
                // appear the day their edition is configured on the proxy —
                // never as a dead row.
                model.availableTranslations.forEach { translation ->
                    val chosen = model.me?.translation == translation.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = MinTarget)
                            .clickable(role = Role.Button) {
                                model.setTranslation(translation.id)
                            }
                            // The chartreuse dot is a colour, and colour is
                            // never the only signal (§11): the row also
                            // announces itself as the chosen one.
                            .semantics { selected = chosen }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = translation.displayName,
                            style = RibbonType.ui(16f),
                            color = Palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (chosen) {
                            Box(
                                Modifier
                                    .size(SelectionDot)
                                    .clip(CircleShape)
                                    .background(Palette.chartreuse),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCaps(Copy.TEXT_SIZE, size = 12f, modifier = HeadingModifier)
                Slider(
                    value = model.settings.scriptureSize.toFloat(),
                    onValueChange = { size ->
                        model.updateSettings { it.copy(scriptureSize = size.toDouble()) }
                    },
                    valueRange = SCRIPTURE_MIN..SCRIPTURE_MAX,
                    steps = SCRIPTURE_STEPS,
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.chartreuse,
                        activeTrackColor = Palette.chartreuse,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = Palette.rule,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.semantics { contentDescription = Copy.TEXT_SIZE },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCaps(Copy.LINE_SPACING, size = 12f, modifier = HeadingModifier)
                val steps = listOf(
                    Copy.LINE_SPACING_CLOSE,
                    Copy.LINE_SPACING_BOOK,
                    Copy.LINE_SPACING_OPEN,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    steps.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = model.settings.lineSpacingStep == index,
                            onClick = {
                                model.updateSettings { it.copy(lineSpacingStep = index) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, steps.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Palette.raised,
                                activeContentColor = Palette.text,
                                activeBorderColor = Palette.rule,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = Palette.muted,
                                inactiveBorderColor = Palette.rule,
                            ),
                            // No checkmark: the selected segment is already
                            // the selected segment, and a tick is one more
                            // piece of chrome in a room that has none.
                            icon = {},
                            modifier = Modifier.heightIn(min = MinTarget),
                        ) {
                            Text(text = label, style = RibbonType.ui(15f))
                        }
                    }
                }
            }

            RibbonToggle(
                title = Copy.RED_LETTER,
                value = model.settings.redLetter,
            ) { on ->
                model.updateSettings { it.copy(redLetter = on) }
            }

            TextSettingsPreview(model)
        }
    }
}

/**
 * The live preview: the verse you were last reading, in your translation, at
 * your size.
 *
 * Swift adds its leading with `.lineSpacing`, which is the gap *between*
 * lines; Compose sets the line box itself, so the multiple is applied to the
 * whole line height — the same arithmetic the reading surface does.
 */
@Composable
private fun TextSettingsPreview(model: AppModel) {
    val me = model.me ?: return
    val room = model.currentRoom ?: return
    val reading = model.openReading(room) ?: return
    val position = model.myPosition(reading)
    val text = model.scripture.verseText(position, translation = me.translation) ?: return

    val size = model.settings.scriptureSize.toFloat()
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HairlineRule()
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

// S19 — notifications, per room, not global: you want everything from your
// wife and almost nothing from the Thursday study. The finished-book note
// has no switch — it fires a handful of times a year and is an invitation
// back, not an absence notification. Nothing here is about absence,
// lapses, streaks, or reminders to read, because those notifications
// don't exist.

/**
 * Notifications (S19).
 *
 * @param onBack pop back to You.
 */
@Composable
fun NotificationSettingsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScroll(modifier = modifier) {
        BackControl(onBack)
        Column(
            modifier = Modifier.padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            model.state.rooms.forEach { room ->
                NotificationRoomSection(model = model, room = room)
            }
            QuietHours(model)
        }
    }
}

@Composable
private fun NotificationRoomSection(model: AppModel, room: Room) {
    val prefs = model.notificationPrefs(room)

    fun set(next: RoomNotificationPrefs) = model.setNotificationPrefs(next, room)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SmallCaps(model.displayName(room), size = 12f, modifier = HeadingModifier)
        RibbonToggle(Copy.NOTES_LEFT_FOR_YOU, prefs.notesLeft) { on ->
            set(prefs.copy(notesLeft = on))
        }
        RibbonToggle(Copy.CARDS_OPEN, prefs.cardsOpen) { on ->
            set(prefs.copy(cardsOpen = on))
        }
        RibbonToggle(Copy.WHEN_THEY_OPEN_THE_BOOK, prefs.whenTheyOpenTheBook) { on ->
            set(prefs.copy(whenTheyOpenTheBook = on))
        }
        RibbonToggle(Copy.THINKING_OF_YOU, prefs.thinkingOfYou) { on ->
            set(prefs.copy(thinkingOfYou = on))
        }
    }
}

@Composable
private fun QuietHours(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallCaps(Copy.QUIET_HOURS, size = 12f, modifier = HeadingModifier)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MinuteControl(
                minutes = model.settings.quietHoursStart,
                onChange = { m -> model.updateSettings { it.copy(quietHoursStart = m) } },
            )
            Text(
                text = Copy.QUIET_HOURS_TO,
                style = RibbonType.ui(15f),
                color = Palette.muted,
            )
            MinuteControl(
                minutes = model.settings.quietHoursEnd,
                onChange = { m -> model.updateSettings { it.copy(quietHoursEnd = m) } },
            )
        }
        Text(
            text = Copy.THINKING_OF_YOU_STILL_ARRIVES,
            style = RibbonType.ui(13f),
            color = Palette.muted,
        )
    }
}

/**
 * One end of quiet hours, as minutes from midnight.
 *
 * Swift uses a compact `DatePicker` with `.hourAndMinute`, which shows the
 * time and opens a wheel over it. Compose has no compact time field: the
 * platform's time control is Material's dial, in a dialog. So the time is
 * shown here, in the app's own small caps and in the device's own 12- or
 * 24-hour convention, and tapping it opens the dial.
 *
 * The dial has no confirm button, because Swift's picker has none either —
 * it applies as it is turned. Closing the dial, by back or by a tap outside,
 * takes what it is showing.
 */
@Composable
private fun MinuteControl(minutes: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    val label = remember(minutes, context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        DateFormat.getTimeFormat(context).format(calendar.time)
    }

    Box(
        modifier = Modifier
            .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.raised)
            .clickable(role = Role.Button) { picking = true }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        SmallCaps(label, size = 13f, color = Palette.text)
    }

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
            Box(Modifier.clip(RoundedCornerShape(20.dp))) {
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

// S21 — downloads. Megabytes are a fine number: they measure a device, not
// a person. Both launch translations ship in the app, whole.

/**
 * Downloads (S21).
 *
 * @param onBack pop back to You.
 */
@Composable
fun DownloadsScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assets = remember(context) { context.assets }

    SettingsScroll(modifier = modifier) {
        BackControl(onBack)
        Column(
            modifier = Modifier.padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SmallCaps(Copy.onThisPhone(context), size = 12f, modifier = HeadingModifier)
            model.availableTranslations.forEach { translation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = translation.fullName,
                        style = RibbonType.ui(16f),
                        color = Palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (translation.isBundled) {
                        val megabytes = remember(assets, translation.id) {
                            bundledMegabytes(assets, translation.id)
                        }
                        SmallCaps(Copy.megabytes(megabytes), size = 12f)
                    } else {
                        // Licensed text streams; the book being read stays on
                        // the phone, the rest doesn't — its license, not our
                        // design.
                        SmallCaps(Copy.STREAMS, size = 12f)
                    }
                }
            }
            Text(
                text = Copy.voiceNotesPolicy(context),
                style = RibbonType.ui(14f),
                color = Palette.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
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

// S22 — the plan. The first book is free, all the way through — not a
// 7-day trial, because a clock is a count. The ask appears in exactly two
// places: the shelf after the first ember, and here. A non-paying member
// never sees a price and never learns who pays.

/**
 * The plan (S22).
 *
 * @param onBack pop back to You.
 */
@Composable
fun PlanScreen(
    model: AppModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SettingsScroll(modifier = modifier) {
        BackControl(onBack)
        Column(
            modifier = Modifier.padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = Copy.FIRST_BOOK_FREE,
                style = RibbonType.ui(17f),
                color = Palette.text,
            )
            val room = model.currentRoom
            if (room != null && room.isPaused) {
                // S22's anatomy is "Start the room again if paused · manage
                // in the store", and the restore half needs billing that
                // does not exist yet on either platform (deviation 11). What
                // stood here was a chartreuse capsule with an empty body: a
                // control that says exactly what happens and then does not
                // do it, which is worse than no control and reads as a
                // failure the app never names. So the room says the true
                // thing instead, and the store is where the other half of it
                // lives.
                Text(
                    text = Copy.ROOM_PAUSED,
                    style = RibbonType.ui(15f),
                    color = Palette.text,
                )
                QuietControl(
                    title = Copy.MANAGE_IN_STORE,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, PLAY_SUBSCRIPTIONS.toUri()),
                    )
                }
            }
            Text(
                text = Copy.THE_ASK_COMES_ONCE,
                style = RibbonType.ui(15f),
                color = Palette.muted,
            )
        }
    }
}

// MARK: - Shared pieces

/**
 * A settings screen's scroll, on the room's own ground.
 *
 * Swift's `ScrollView { … }.scrollIndicators(.hidden).room()`, plus the two
 * things Compose has to be told: the paper is painted behind the content
 * rather than by the scroll (`.room()` hides its node from accessibility, and
 * a scroll that hid itself would take the settings with it), and three-button
 * navigation's inset is added under the last row so a screen scrolls behind
 * the bar rather than stopping above it.
 *
 * Both insets are theirs to carry. They used to live inside a bottom sheet,
 * which cleared the status bar for them; the menu is a full-screen layer
 * now (deviations 14, A17) and nothing above them clears anything, so a
 * screen without the top inset would draw its own back chevron underneath
 * the clock.
 */
@Composable
private fun SettingsScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().room())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A Compose scroll draws no indicator of its own, so
                // `.scrollIndicators(.hidden)` has nothing to hide.
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .readableColumn()
                    .padding(top = statusBar, bottom = bottomBar),
            ) {
                content()
            }
        }
    }
}

/**
 * The way back out of a pushed screen — these four, and the menu's own join.
 *
 * iOS gets this for free from `NavigationStack` — a chevron, named for
 * VoiceOver. Compose draws nothing, and a screen whose only way back is a
 * gesture has no tap equivalent, so the chevron is drawn here at the same
 * 44 dp every other control clears.
 */
@Composable
internal fun BackControl(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = ScreenPadding - 12.dp, top = 8.dp)
            .size(MinTarget)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = Copy.BACK },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MinTarget)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val arm = 5.dp.toPx()
            val stroke = 1.6.dp.toPx()
            drawLine(
                color = Palette.muted,
                start = Offset(centre.x + arm * 0.6f, centre.y - arm),
                end = Offset(centre.x - arm * 0.6f, centre.y),
                strokeWidth = stroke,
            )
            drawLine(
                color = Palette.muted,
                start = Offset(centre.x - arm * 0.6f, centre.y),
                end = Offset(centre.x + arm * 0.6f, centre.y + arm),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * One switch and its line of text — Swift's `Toggle` with `.tint(chartreuse)`,
 * used both for the four per-room notification switches (S19) and for red
 * letter (S20), where Swift writes the same control twice.
 *
 * The whole row is the target and announces as one switch, which is what a
 * `Toggle` is; the drawn switch is smaller than a finger, so the row carries
 * the 44 dp minimum for it.
 */
@Composable
private fun RibbonToggle(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MinTarget)
            .toggleable(value = value, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = RibbonType.ui(16f),
            color = Palette.text,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = value,
            // The row owns the gesture and the semantics; the switch is the
            // drawing of the state.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.ground,
                checkedTrackColor = Palette.chartreuse,
                checkedBorderColor = Palette.chartreuse,
                uncheckedThumbColor = Palette.muted,
                uncheckedTrackColor = Palette.raised,
                uncheckedBorderColor = Palette.rule,
            ),
        )
    }
}
