@file:OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)

package app.readribbon.screens

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

// S18 — You: account and app-wide settings. One tap from the room now (the
// portrait in the room's header), by the owner's call — the book buried it
// two taps deep; docs/deviations.md records the change. Still not here: no
// theme picker (dark is the product), no accent picker (chartreuse is the
// brand's, not the user's), no app-icon picker.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it
// is on every other screen here. It holds Compose snapshot state, so reading
// `model.settings` or `model.state.rooms` recomposes exactly as `@Observable`
// does.

/** The face at the top of You — Swift's `size: 56`. */
private val YouPortraitSize = 56.dp

/** `HStack(spacing: 14)` across the portrait and the name. */
private val YouHeaderSpan = 14.dp

/** `.padding(.top, 26)` above the portrait, `.padding(.horizontal, 24)`
 *  down the whole sheet, and the `spacing: 28` between its sections. */
private val YouTop = 26.dp
private val YouMargin = 24.dp
private val YouSectionGap = 28.dp

/** Each pushed settings screen's own `.padding(24)`. */
private val ScreenPadding = 24.dp

/**
 * The smallest a control may be tapped at (§11, deviation 12). Every gesture
 * has a tap equivalent and every target clears a finger, even where the drawn
 * thing is smaller.
 */
private val MinTarget: Dp = 44.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the glyph;
 * Swift grows the target outward instead and leaves the words where they
 * were. Pulling the control back by that same 8 dp puts "Name this room"
 * back on the sheet's margin, flush with the small caps above it — the same
 * correction the rooms sheet makes.
 */
private val QuietControlInset = (-8).dp

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

/**
 * The routes You pushes to. Swift's `NavigationLink { TextSettingsScreen() }`
 * is a value destination in a `NavigationStack`; these are the same four
 * destinations as navigation-compose routes, in a graph the sheet owns —
 * which keeps `YouSheet` a single thing to present, exactly as the Swift is.
 */
private object Route {
    const val YOU = "you"
    const val TEXT = "text"
    const val NOTIFICATIONS = "notifications"
    const val DOWNLOADS = "downloads"
    const val PLAN = "plan"
}

/**
 * You (S18): your face, your name, the four settings screens, this room's own
 * controls, the account, and the way out of all of it.
 *
 * Swift presents this with `.sheet`, over the room, with
 * `.presentationBackground(Palette.ground)`; a modal bottom sheet is the same
 * presentation here, and predictive back peels it away to leave the room
 * behind it.
 *
 * @param model the store.
 * @param onDismiss close the sheet. Swift's `@Environment(\.dismiss)` — which
 *   leaving the room calls for itself, because the room this belongs to is
 *   gone.
 */
@Composable
fun YouSheet(
    model: AppModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        // `.presentationBackground(Palette.ground)`. The grain goes on top of
        // it, inside the content, exactly as it does in a room.
        containerColor = Palette.ground,
        contentColor = Palette.text,
        // The content carries its own clearance from the system bars, so a
        // pushed screen can scroll behind a three-button bar rather than be
        // cut short above it.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        YouNavigation(model = model, onDismiss = onDismiss)
    }
}

/**
 * The sheet's body without the sheet around it: You, and the four screens it
 * pushes to.
 *
 * Swift's `NavigationStack` is a nav graph here. Back — including a
 * predictive back gesture — pops a pushed screen first and only then closes
 * the sheet, which is what a `NavigationStack` inside a `.sheet` does on iOS.
 * The push eases rather than springs (§9.1): Material 3 Expressive's physics
 * is taken damped, and nothing in Ribbon overshoots.
 */
@Composable
fun YouNavigation(
    model: AppModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val push = tween<Float>(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)
    val slide = tween<IntOffset>(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

    NavHost(
        navController = navController,
        startDestination = Route.YOU,
        modifier = modifier,
        enterTransition = { slideInHorizontally(slide) { it / 4 } + fadeIn(push) },
        exitTransition = { fadeOut(push) },
        popEnterTransition = { fadeIn(push) },
        popExitTransition = { slideOutHorizontally(slide) { it / 4 } + fadeOut(push) },
    ) {
        composable(
            Route.YOU,
            // The root never slides: it is what the sheet opened onto.
            enterTransition = { EnterTransition.None },
            exitTransition = { fadeOut(push) },
            popEnterTransition = { fadeIn(push) },
            popExitTransition = { ExitTransition.None },
        ) {
            YouContent(
                model = model,
                onOpen = { route -> navController.navigate(route) },
                onDismiss = onDismiss,
            )
        }
        composable(Route.TEXT) {
            TextSettingsScreen(model = model, onBack = { navController.popBackStack() })
        }
        composable(Route.NOTIFICATIONS) {
            NotificationSettingsScreen(model = model, onBack = { navController.popBackStack() })
        }
        composable(Route.DOWNLOADS) {
            DownloadsScreen(model = model, onBack = { navController.popBackStack() })
        }
        composable(Route.PLAN) {
            PlanScreen(model = model, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * You itself (S18).
 *
 * @param onOpen push one of the four settings screens.
 * @param onDismiss close the sheet.
 */
@Composable
fun YouContent(
    model: AppModel,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Swift's `PhotosPicker` plus its `.onChange` — the picked image is read
    // and downsampled off the main thread, then handed to the store. Setting
    // a portrait must finish whatever happens to this sheet, so it runs on
    // the model's own scope rather than the composition's, which is the same
    // call the join flow makes about work that must land.
    val portraitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { downsampledJpeg(it) }
            }
            if (jpeg != null) model.setPortrait(jpeg)
        }
    }

    // The build, in the quietest voice there is. Swift reads the bundle's
    // short version string; the package's own version name is the same fact
    // on this platform, and needs no generated BuildConfig to say it.
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    SettingsScroll(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = YouMargin),
            verticalArrangement = Arrangement.spacedBy(YouSectionGap),
        ) {
            Row(
                modifier = Modifier.padding(top = YouTop),
                horizontalArrangement = Arrangement.spacedBy(YouHeaderSpan),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Portrait and name, editable in place (S18) — presence is
                // faces, so the face can be added or changed here, not only
                // at onboarding.
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = MinTarget, minHeight = MinTarget)
                        .clickable(role = Role.Button) {
                            portraitPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        // Swift's `.accessibilityLabel(Copy.addAPortrait)`,
                        // which replaces the label rather than adding to it:
                        // the control is the way to a portrait, so the
                        // portrait's own name is cleared beneath it.
                        .semantics { contentDescription = Copy.ADD_A_PORTRAIT },
                    contentAlignment = Alignment.Center,
                ) {
                    PortraitView(
                        person = model.me,
                        ink = null,
                        size = YouPortraitSize,
                        image = model.me?.let { model.portrait(it.id) },
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                if (editingName) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = RibbonType.ui(18f).copy(color = Palette.text),
                        cursorBrush = SolidColor(Palette.chartreuse),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val trimmed = name.trim()
                                if (trimmed.isNotEmpty()) model.updateMe(name = trimmed)
                                editingName = false
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTarget)
                            .focusRequester(focus),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .sizeIn(minHeight = MinTarget)
                            .clickable(role = Role.Button) {
                                name = model.me?.name ?: ""
                                editingName = true
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = model.me?.name ?: "",
                            style = RibbonType.ui(18f),
                            color = Palette.text,
                        )
                    }
                }
            }

            Column {
                // Swift's `spacing: 20` between the four links is folded into
                // each row's own 44 dp minimum rather than sitting as dead
                // space between two targets a finger can miss — 44 dp of
                // pitch against the iOS layout's 42, and no gap between them
                // that looks tappable and isn't.
                SettingsRow(Copy.TEXT_AND_TRANSLATION) { onOpen(Route.TEXT) }
                SettingsRow(Copy.NOTIFICATIONS) { onOpen(Route.NOTIFICATIONS) }
                SettingsRow(Copy.DOWNLOADS) { onOpen(Route.DOWNLOADS) }
                SettingsRow(Copy.PLAN) { onOpen(Route.PLAN) }
            }

            model.currentRoom?.let { room ->
                RoomSection(model = model, room = room, onLeft = onDismiss)
            }

            AccountSection(model = model)

            QuietControl(
                title = Copy.DELETE_ACCOUNT,
                modifier = Modifier.offset(x = QuietControlInset),
            ) { confirmDelete = true }

            SmallCaps(
                Copy.versionLine(version),
                size = 11f,
                color = Palette.muted.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(YouSectionGap))
    }

    if (confirmDelete) {
        // §6.8: the "leave your notes behind?" question, asked once, at
        // deletion. Leaving them is never not the default.
        RibbonConfirmDialog(
            question = Copy.LEAVE_NOTES_QUESTION,
            onDismiss = { confirmDelete = false },
        ) {
            ConfirmChoice(
                title = Copy.DELETE_AND_LEAVE_THEM,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    model.deleteAccount(keepNotesBehind = true)
                },
            )
            ConfirmChoice(
                title = Copy.DELETE_AND_TAKE_THEM_BACK,
                destructive = true,
                onClick = {
                    confirmDelete = false
                    model.deleteAccount(keepNotesBehind = false)
                },
            )
            // iOS supplies this button itself; Compose's dialog has only the
            // choices it is handed, and a question with no way to say no is
            // not asked plainly.
            ConfirmChoice(title = Copy.STAY, onClick = { confirmDelete = false })
        }
    }
}

/**
 * One of You's four rows. Swift's `NavigationLink(Copy.textAndTranslation)`,
 * which draws its own chevron; here the row is the target and the words are
 * the whole of it, as they are everywhere else in this app.
 */
@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MinTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
    }
}

/**
 * The current room's own controls: its name, your ink, the way out. These
 * lived only on your S12, which a fresh room of one couldn't reach
 * (deviations 9a) — now they're one tap away with the rest of You.
 *
 * @param onLeft the room was left; Swift calls `dismiss()`, because the room
 *   this sheet was opened over is gone.
 */
@Composable
private fun RoomSection(
    model: AppModel,
    room: Room,
    onLeft: () -> Unit,
) {
    var editingRoomName by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var showInkPicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SmallCaps(model.displayName(room), size = 12f)
        if (editingRoomName) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (roomName.isEmpty()) {
                    Text(
                        text = Copy.ROOM_NAME,
                        style = RibbonType.ui(16f),
                        color = Palette.muted,
                    )
                }
                BasicTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    singleLine = true,
                    textStyle = RibbonType.ui(16f).copy(color = Palette.text),
                    cursorBrush = SolidColor(Palette.chartreuse),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            model.renameRoom(room, name = roomName)
                            editingRoomName = false
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        } else {
            QuietControl(
                title = Copy.NAME_THIS_ROOM,
                modifier = Modifier.offset(x = QuietControlInset),
            ) {
                roomName = room.name ?: ""
                editingRoomName = true
            }
        }
        if (model.inkIsIdentity(room)) {
            QuietControl(
                title = Copy.CHANGE_YOUR_INK,
                modifier = Modifier.offset(x = QuietControlInset),
            ) { showInkPicker = true }
        }
        QuietControl(
            title = Copy.LEAVE_THIS_ROOM,
            modifier = Modifier.offset(x = QuietControlInset),
        ) { confirmLeave = true }
    }

    if (showInkPicker) {
        InkPickerSheet(model = model, room = room, onDismiss = { showInkPicker = false })
    }

    if (confirmLeave) {
        // The same two questions S12 asks — the confirmation, and then §6.8's
        // "leave your notes behind?", where leaving them is the default and
        // taking them back is possible and never the default. They live with
        // the person screen so the whole way out is one thing to present.
        LeaveRoomDialogs(
            model = model,
            room = room,
            onDismiss = { confirmLeave = false },
            onLeft = {
                confirmLeave = false
                onLeft()
            },
        )
    }
}

/**
 * The account (§6.10): an emailed code, no passwords. Signed out is a state,
 * not a nag — one quiet line, and the reason stated plainly.
 */
@Composable
private fun AccountSection(model: AppModel) {
    var signingIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            model.isSignedIn -> {
                model.accountEmail?.let { address -> SmallCaps(address, size = 12f) }
                QuietControl(
                    title = Copy.SIGN_OUT,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) {
                    signingIn = false
                    // Signing out must finish whatever happens to this sheet.
                    model.viewModelScope.launch { model.signOutRemote() }
                }
            }

            // Remote is not configured in this build; no dead control.
            model.remote == null -> Unit

            signingIn -> SignInInline(
                model = model,
                onSignedIn = { signingIn = false },
                onCancel = { signingIn = false },
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietControl(
                    title = Copy.SIGN_IN,
                    modifier = Modifier.offset(x = QuietControlInset),
                ) { signingIn = true }
                Text(
                    text = Copy.ACCOUNT_REASON,
                    style = RibbonType.ui(13f),
                    color = Palette.muted,
                )
            }
        }
    }
}

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
                SmallCaps(Copy.TRANSLATION, size = 12f)
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
                SmallCaps(Copy.TEXT_SIZE, size = 12f)
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
                SmallCaps(Copy.LINE_SPACING, size = 12f)
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
        SmallCaps(model.displayName(room), size = 12f)
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
        SmallCaps(Copy.QUIET_HOURS, size = 12f)
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
            SmallCaps(Copy.onThisPhone(context), size = 12f)
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
                WayInButton(title = Copy.START_THE_ROOM_AGAIN) {
                    // Swift: "StoreKit arrives with the backend; nothing to
                    // restore locally." Play Billing is the same story here.
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
 * These screens live inside a bottom sheet, which already clears the status
 * bar, so only the bottom inset is theirs to carry.
 */
@Composable
private fun SettingsScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    .padding(bottom = bottomBar),
            ) {
                content()
            }
        }
    }
}

/**
 * The way back out of a pushed settings screen.
 *
 * iOS gets this for free from `NavigationStack` — a chevron, named for
 * VoiceOver. Compose draws nothing, and a screen whose only way back is a
 * gesture has no tap equivalent, so the chevron is drawn here at the same
 * 44 dp every other control clears.
 */
@Composable
private fun BackControl(onBack: () -> Unit) {
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
