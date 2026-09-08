@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import app.readribbon.core.Ink
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Invite
import app.readribbon.design.Palette
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WaveMark
import app.readribbon.design.WayInButton
import app.readribbon.design.readableColumn
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.design.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.core.graphics.scale

// S17 — onboarding: a thread, not a screen. Four questions, no tour, no
// carousel, no permission prompts at launch, no account wall. The Wave and
// the tagline are the only branded moment in the product.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it
// is on every other screen here.

/**
 * Which question the thread is on. Swift nests this as `OnboardingFlow.Step`
 * and gets `Equatable` for free; a sealed interface of objects and one data
 * class is the same closed set, and `Join` compares by its token exactly as
 * the Swift case does.
 */
private sealed interface Step {
    data object Mark : Step
    data class Tour(val index: Int) : Step
    data object Intent : Step
    data object Who : Step
    data object FromInvite : Step
    data object SignIn : Step
    data object Name : Step
    data object Invite : Step
    data class Join(val token: Uuid) : Step
}

/** The mark holds for about 750 ms and then dissolves into the tour. */
private const val MARK_HOLD_MS = 750L

/** That dissolve is its own beat — slower than a settle, eased both ways. */
private const val MARK_DISSOLVE_MS = 500

/** The portrait well, and the face in it. */
private val PortraitSide = 96.dp

/** The whole thread is laid out for one column, centred on a wide screen. */
private val ColumnMeasure = 420.dp

/** The visible field is small; the tappable field is never under 44 dp. */
private val TouchTarget = 44.dp

private fun <T> settleSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

private fun <T> dissolveSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(MARK_DISSOLVE_MS, easing = RibbonMotion.EaseInOut)

/**
 * The whole way in: the mark, feature tour, reader intent, a name and a face,
 * and the invite — or the join a tapped link brought them here for.
 *
 * @param onDone Onboarded; the caller decides what arriving looks like. In
 *   Swift this is the trailing closure `OnboardingFlow { onboarding = false }`.
 */
@Composable
fun OnboardingFlow(
    model: AppModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step: Step by remember { mutableStateOf<Step>(Step.Mark) }
    var selectedIntent by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var portraitData: ByteArray? by remember { mutableStateOf<ByteArray?>(null) }
    // Swift decodes the picked data to a `UIImage` inline at draw time; a
    // Compose `ImageBitmap` is decoded once, here, and drawn from.
    var portraitImage: ImageBitmap? by remember { mutableStateOf<ImageBitmap?>(null) }
    var pastedInvite by remember { mutableStateOf("") }
    var pasteMissed by remember { mutableStateOf(false) }
    var invite: Invite? by remember { mutableStateOf<Invite?>(null) }

    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    // A tapped invite link is the strongest possible statement of intent —
    // it wins over whatever step was showing (S16). Swift asks for
    // `initial: true`; a `LaunchedEffect` keyed on the value runs on arrival
    // by nature, which is the same thing.
    LaunchedEffect(model.pendingInvite) {
        model.pendingInvite?.let { pending -> step = Step.Join(pending.token) }
    }

    fun acceptPasted() {
        val token = AppModel.inviteToken(fromPasted = pastedInvite)
        if (token == null) {
            if (pastedInvite.trim().isNotEmpty()) pasteMissed = true
            return
        }
        step = Step.Join(token)
    }

    fun advanceFromName() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            model.completeOnboarding(name = trimmed, portraitData = portraitData)
            step = Step.Invite
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // `.room()` — the ground and its grain — is painted by a box behind
        // the content rather than by the content itself: the modifier hides
        // its node from accessibility (the paper is texture, not
        // information), and the thread in front of it must not go with it.
        Box(Modifier.matchParentSize().room())

        // Swift's three stacked frames: fill, then a 420-point column, then
        // fill again so the ground still runs edge to edge behind it.
        //
        // Edge-to-edge is mandatory (§12.2), so the column keeps its own
        // clearance from the status bar, the gesture pill or the
        // three-button bar, and the cutout. `safeDrawing` carries the
        // keyboard too, which is what the name and paste fields need.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.readableColumn(ColumnMeasure).fillMaxHeight(),
                transitionSpec = {
                    // Every step change is `withAnimation(RibbonMotion.settle)`
                    // over `.transition(.opacity)`. The one exception is the
                    // mark dissolving, which takes its own slower beat.
                    val spec: FiniteAnimationSpec<Float> =
                        if (initialState is Step.Mark) {
                            dissolveSpec(reduceMotion)
                        } else {
                            settleSpec(reduceMotion)
                        }
                    fadeIn(spec) togetherWith fadeOut(spec)
                },
                label = "onboarding-step",
            ) { current ->
                when (current) {
                    Step.Mark -> MarkMoment(
                        onElapsed = { if (step is Step.Mark) step = Step.Tour(0) },
                    )

                    is Step.Tour -> TourStep(
                        index = current.index,
                        onContinue = {
                            step = if (current.index < 3) Step.Tour(current.index + 1) else Step.Intent
                        },
                        onBack = if (current.index > 0) {
                            { step = Step.Tour(current.index - 1) }
                        } else null,
                        onSignIn = { step = Step.SignIn },
                        onHaveInvite = { step = Step.FromInvite },
                    )

                    Step.Intent -> IntentStep(
                        selectedIntent = selectedIntent,
                        onIntentSelected = { selectedIntent = it },
                        onContinue = { step = Step.Name },
                        onBack = { step = Step.Tour(3) },
                        onSignIn = { step = Step.SignIn },
                    )

                    Step.Who -> WhoStep(
                        onStartARoom = { step = Step.Name },
                        onHaveAnInvite = { step = Step.FromInvite },
                        onSignIn = if (model.remote == null) null else {
                            { step = Step.SignIn }
                        },
                    )

                    Step.SignIn -> SignInStep(
                        model = model,
                        onSignedIn = {
                            if (model.me != null) onDone() else step = Step.Name
                        },
                        onCancel = { step = Step.Tour(0) },
                    )

                    Step.FromInvite -> FromInviteStep(
                        pasted = pastedInvite,
                        onPastedChange = { text ->
                            pastedInvite = text
                            pasteMissed = false
                            if (AppModel.inviteToken(fromPasted = text) != null) acceptPasted()
                        },
                        pasteMissed = pasteMissed,
                        onSubmit = { acceptPasted() },
                        onStartInstead = { step = Step.Name },
                    )

                    Step.Name -> NameStep(
                        name = name,
                        onNameChange = { name = it },
                        portrait = portraitImage,
                        onPortraitPicked = { data, image ->
                            portraitData = data
                            portraitImage = image
                        },
                        onBack = { step = Step.Intent },
                        onSubmit = { advanceFromName() },
                    )

                    Step.Invite -> InviteStep(
                        model = model,
                        invite = invite,
                        onInvite = { invite = it },
                        onDone = onDone,
                    )

                    is Step.Join -> JoinFlow(
                        model = model,
                        token = current.token,
                        onDone = onDone,
                        // Onboarding presents the join as a step, not as a
                        // sheet over the room, so there is no set-down
                        // gesture and nothing to return to — the Swift's
                        // JoinFlow has no dismissal here either. The
                        // sheet-presented call site is where set-down
                        // matters, and it handles it.
                        onDismiss = {},
                        onStartInstead = {
                            // Declining the join forgets it — otherwise the
                            // pending token re-presents the join over the
                            // room they start instead.
                            model.pendingInvite = null
                            step = Step.Name
                        },
                    )
                }
            }
        }
    }
}

/** The mark, and one line. It holds for about 900 ms and then dissolves. */
@Composable
private fun MarkMoment(onElapsed: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(MARK_HOLD_MS)
        onElapsed()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaveMark(size = 84.dp)
        Text(
            text = Copy.TAGLINE,
            style = RibbonType.display(22f),
            color = Palette.text,
        )
    }
}

@Composable
private fun TourStep(
    index: Int,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
    onSignIn: () -> Unit,
    onHaveInvite: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingProgressBar(
            currentStep = index,
            totalSteps = 6,
            onBack = onBack,
            onSignIn = onSignIn,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingTourCard(index = index)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WayInButton(
                title = Copy.CONTINUE_TOUR,
                modifier = Modifier.padding(horizontal = 40.dp),
                onClick = onContinue,
            )

            if (index == 0) {
                QuietControl(
                    title = Copy.ALREADY_HAVE_ACCOUNT,
                    onClick = onSignIn,
                )
            } else {
                QuietControl(
                    title = Copy.HAVE_AN_INVITE,
                    onClick = onHaveInvite,
                )
            }
        }
    }
}

@Composable
private fun IntentStep(
    selectedIntent: Int,
    onIntentSelected: (Int) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingProgressBar(
            currentStep = 4,
            totalSteps = 6,
            onBack = onBack,
            onSignIn = onSignIn,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = Copy.WALKTHROUGH_INTENT_TITLE,
            style = RibbonType.display(24f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val intents = listOf(
                Pair(Copy.WALKTHROUGH_INTENT_SPOUSE, Ink.rose),
                Pair(Copy.WALKTHROUGH_INTENT_FRIEND, Ink.teal),
                Pair(Copy.WALKTHROUGH_INTENT_GROUP, Ink.ochre),
                Pair(Copy.WALKTHROUGH_INTENT_SOLO, Ink.plum),
            )

            intents.forEachIndexed { i, (title, ink) ->
                val isSelected = selectedIntent == i
                val dotColor = ink.color

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Palette.raised else Palette.surface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Palette.chartreuse.copy(alpha = 0.6f) else Palette.rule,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onIntentSelected(i) },
                        )
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(dotColor, CircleShape),
                        )
                        Text(
                            text = title,
                            style = RibbonType.ui(16f),
                            color = if (isSelected) Palette.text else Palette.muted,
                        )
                    }

                    if (isSelected) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val stroke = 2.dp.toPx()
                            drawLine(
                                color = Palette.chartreuse,
                                start = Offset(size.width * 0.2f, size.height * 0.5f),
                                end = Offset(size.width * 0.45f, size.height * 0.75f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = Palette.chartreuse,
                                start = Offset(size.width * 0.45f, size.height * 0.75f),
                                end = Offset(size.width * 0.8f, size.height * 0.25f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        WayInButton(
            title = Copy.CONTINUE_TOUR,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .padding(bottom = 24.dp),
            onClick = onContinue,
        )
    }
}

@Composable
private fun WhoStep(
    onStartARoom: () -> Unit,
    onHaveAnInvite: () -> Unit,
    onSignIn: (() -> Unit)?,
) {
    StepColumn(spacing = 26.dp) {
        Text(
            text = Copy.WHO_IS_READING,
            style = RibbonType.display(24f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WayInButton(title = Copy.START_A_ROOM, onClick = onStartARoom)
            QuietControl(title = Copy.HAVE_AN_INVITE, onClick = onHaveAnInvite)
            // The third answer, for the person this is not the first time
            // for: a new phone, or a reinstall. Without it the only way back
            // to your own rooms was to make a stranger and a stray room
            // first, and find Sign in underneath them.
            if (onSignIn != null) {
                QuietControl(title = Copy.SIGN_IN, onClick = onSignIn)
            }
        }
    }
}

/**
 * The way back to a room you already have.
 *
 * The account is the only thing that carries one between phones (§6.10), so
 * this is where a second phone starts — and when the account already has a
 * profile, its person and its rooms come back and there is nothing left to
 * ask.
 */
@Composable
private fun SignInStep(
    model: AppModel,
    onSignedIn: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onCancel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val arm = 4.5.dp.toPx()
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        color = Palette.muted,
                        start = Offset(centre.x + arm * 0.5f, centre.y - arm),
                        end = Offset(centre.x - arm * 0.5f, centre.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Palette.muted,
                        start = Offset(centre.x - arm * 0.5f, centre.y),
                        end = Offset(centre.x + arm * 0.5f, centre.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = Copy.ACCOUNT_REASON,
            style = RibbonType.ui(17f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp),
        )

        Spacer(Modifier.height(24.dp))

        SignInInline(
            model = model,
            onSignedIn = onSignedIn,
            onCancel = onCancel,
            modifier = Modifier.padding(horizontal = 40.dp),
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    portrait: ImageBitmap?,
    onPortraitPicked: (ByteArray, ImageBitmap?) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // `PhotosPicker(matching: .images)`. The photo picker asks for no
    // permission and hands back one image — no gallery read, nothing at
    // launch (§6.1).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            } ?: return@launch
            val small = withContext(Dispatchers.Default) { downsampledJpeg(bytes) } ?: return@launch
            val image = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(small, 0, small.size)?.asImageBitmap()
            }
            onPortraitPicked(small, image)
        }
    }

    // `@FocusState private var nameFocused` with `.onAppear { nameFocused =
    // true }`: the field is a new node each time this step arrives, so its
    // own arrival is the moment focus is asked for.
    val nameFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingProgressBar(
            currentStep = 5,
            totalSteps = 6,
            onBack = onBack,
            onSignIn = null,
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(PortraitSide)
                .clip(CircleShape)
                .then(
                    if (portrait == null) {
                        Modifier
                            .background(Palette.surface)
                            .border(1.dp, Palette.rule, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(role = Role.Button) {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    // The empty well is labelled by the small caps inside it;
                    // once a face fills it the control would otherwise have
                    // no name at all, so the face carries it (§11).
                    contentDescription = Copy.ADD_A_PORTRAIT,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(PortraitSide),
                )
            } else {
                SmallCaps(Copy.ADD_A_PORTRAIT, size = 11f)
            }
        }

        Spacer(Modifier.height(24.dp))

        // The portrait is asked for with the one reason that is true.
        Text(
            text = Copy.PORTRAIT_REASON,
            style = RibbonType.ui(15f),
            color = Palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        CentredTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = Copy.YOUR_NAME,
            size = 20f,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.padding(horizontal = 40.dp),
            focusRequester = nameFocus,
        )

        Spacer(Modifier.height(24.dp))

        // Swift dims this control to 0.3 while the name is empty and lets
        // `advanceFromName` guard the tap. `enabled` does both at once — it
        // dims and it refuses — which is the same offer, said once, and it
        // is what a screen reader needs to hear.
        WayInButton(
            title = Copy.THATS_ME,
            modifier = Modifier.padding(horizontal = 80.dp),
            enabled = name.trim().isNotEmpty(),
            onClick = onSubmit,
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

/**
 * The link is the whole mechanism (S15): opening it lands here via the app
 * link — and pasting it works when the link was sent somewhere this device
 * can't tap it from.
 */
@Composable
private fun FromInviteStep(
    pasted: String,
    onPastedChange: (String) -> Unit,
    pasteMissed: Boolean,
    onSubmit: () -> Unit,
    onStartInstead: () -> Unit,
) {
    StepColumn(spacing = 22.dp) {
        Text(
            text = Copy.OPEN_THE_LINK,
            style = RibbonType.ui(17f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        )
        CentredTextField(
            value = pasted,
            onValueChange = onPastedChange,
            placeholder = Copy.PASTE_INVITE_PROMPT,
            size = 16f,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                // A link is never what autocorrect thinks it is.
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.surface)
                .border(1.dp, Palette.rule, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        if (pasteMissed) {
            Text(
                text = Copy.THAT_LINK_ISNT_AN_INVITE,
                style = RibbonType.ui(14f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        QuietControl(title = Copy.START_A_ROOM_INSTEAD, onClick = onStartInstead)
    }
}

@Composable
private fun InviteStep(
    model: AppModel,
    invite: Invite?,
    onInvite: (Invite) -> Unit,
    onDone: () -> Unit,
) {
    // `.onAppear`
    LaunchedEffect(Unit) {
        model.currentRoom?.let { room -> onInvite(model.createInvite(room)) }
    }

    StepColumn(spacing = 24.dp) {
        Text(
            text = Copy.INVITE_SEND,
            style = RibbonType.ui(17f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp),
        )

        if (model.remote != null && !model.isSignedIn) {
            // A link handed out signed-out is a dead link — the account
            // happens here, where it's honestly needed. The quiet ways past
            // (pick a book, invite later) stand.
            Text(
                text = Copy.INVITE_NEEDS_SIGN_IN,
                style = RibbonType.ui(15f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            )
            SignInInline(
                model = model,
                modifier = Modifier.padding(horizontal = 40.dp),
                onSignedIn = {
                    model.currentRoom?.let { room -> onInvite(model.createInvite(room)) }
                },
            )
        } else if (invite != null) {
            SendTheInviteCapsule(invite)
        }

        // You can read alone immediately while the invite is out — the
        // room's first-run state is the book chooser, so picking a book and
        // starting is the next thing that happens (§6.1).
        WayInButton(
            title = Copy.PICK_A_BOOK,
            modifier = Modifier.padding(horizontal = 56.dp),
            onClick = onDone,
        )
        QuietControl(title = Copy.INVITE_LATER, onClick = onDone)
    }
}

/**
 * SwiftUI's `ShareLink`, said in Android's terms: one plain-text intent
 * through the system chooser. The link is the whole item, exactly as it is
 * there — no subject, no preview title, nothing about the room.
 *
 * The invite sheet (S15) draws the same capsule and keeps its own copy;
 * neither file owns the other, and this one is the onboarding step's.
 */
@Composable
private fun SendTheInviteCapsule(invite: Invite) {
    val context = LocalContext.current
    Text(
        text = Copy.SEND_THE_INVITE,
        style = RibbonType.ui(17f, FontWeight.Medium),
        color = Palette.ground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Palette.chartreuse)
            .clickable(role = Role.Button) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, invite.url())
                }
                context.startActivity(Intent.createChooser(send, null))
            }
            .heightIn(min = TouchTarget)
            .padding(horizontal = 28.dp, vertical = 13.dp),
    )
}

/**
 * One step's stack: `VStack(spacing:) { Spacer(); … ; Spacer(); Spacer() }`.
 *
 * Two spacers below and one above is how every step in the thread sits —
 * a little above centre, so the keyboard has somewhere to go.
 */
@Composable
private fun StepColumn(
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        content()
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

/**
 * One centred, undecorated line of typing — the plain `TextField` Swift
 * uses, which draws no box and lets the prompt stand in the muted voice
 * until a character arrives. A step that wants the box (the paste field)
 * passes it in through [modifier], exactly as Swift layers it on.
 *
 * @param focusRequester when a step's field takes focus as it arrives. Only
 *   the name field does, which is Swift's one `.onAppear { nameFocused =
 *   true }` — the paste field waits to be tapped, as it does there.
 */
@Composable
internal fun CentredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    size: Float,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    if (focusRequester != null) {
        LaunchedEffect(focusRequester) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = RibbonType.ui(size),
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = RibbonType.ui(size).copy(
                color = Palette.text,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(Palette.chartreuse),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/**
 * Portraits are small; keep them that way on disk.
 *
 * Swift decodes the whole picked image and redraws it at the smaller size.
 * A phone camera's JPEG is tens of megabytes decoded, so this reads the
 * bounds first and lets `BitmapFactory` decode at roughly the size wanted
 * before the exact scale — the same result, without the allocation that
 * would kill it on a low-memory device.
 *
 * Returns null when the bytes are not an image the device can decode, which
 * is the nil Swift returns for the same reason.
 */
fun downsampledJpeg(data: ByteArray, maxSide: Int = 512): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        inSampleSize = sample
    }
    val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null

    val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height).toFloat())
    val resized = if (scale < 1f) {
        decoded.scale(
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
        )
    } else {
        decoded
    }

    return ByteArrayOutputStream().use { out ->
        // `jpegData(compressionQuality: 0.82)`.
        if (!resized.compress(Bitmap.CompressFormat.JPEG, 82, out)) return null
        out.toByteArray()
    }
}
