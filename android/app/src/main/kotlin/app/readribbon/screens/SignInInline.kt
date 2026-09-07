package app.readribbon.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.design.Palette
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.WayInButton
import app.readribbon.design.rememberReduceMotion
import kotlinx.coroutines.launch

// The sign-in thread, inline (§6.10): an email, then the emailed code, no
// passwords. Small enough to sit inside whatever surface needs an account
// — the invite sheet (a link only works signed in), onboarding's invite
// step, and You. Never a wall: every host keeps its own quiet way past.
//
// Swift reaches the store through `@Environment(AppModel.self)`; nothing in
// this build has an ambient equivalent, so the model is a parameter, as it
// is on every other screen here.

/** Which half of the thread is showing. Swift nests this as `SignInInline.SignInPhase`. */
private enum class SignInPhase { Email, Code }

/** The visible field is small; the tappable field is never under 44 dp. */
private val TouchTarget = 44.dp

private fun <T> settleSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(RibbonMotion.SETTLE_MS, easing = RibbonMotion.EaseOut)

/**
 * The two-step sign-in, laid out inline wherever an account is genuinely
 * needed.
 *
 * @param onSignedIn Called once the session exists and the local graph has
 *   synced.
 * @param onCancel The host's way past, when it has one. Absent, no way-past
 *   control is drawn and the host is expected to offer its own.
 */
@Composable
fun SignInInline(
    model: AppModel,
    modifier: Modifier = Modifier,
    onSignedIn: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    var phase by remember { mutableStateOf(SignInPhase.Email) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var errorLine by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // `@FocusState private var focused` is one flag over whichever field is
    // present; a FocusRequester binds to a single node, so each field brings
    // its own and the phase's own arrival asks for it — which is exactly
    // what `.onAppear` plus `.onChange(of: phase)` amount to.
    val emailFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }

    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    fun sendCode() {
        val address = email.trim()
        if (!address.contains("@") || busy) return
        busy = true
        errorLine = null
        scope.launch {
            try {
                model.sendSignInCode(address)
                code = ""
                phase = SignInPhase.Code
            } catch (_: Throwable) {
                errorLine = Copy.SERVER_UNREACHABLE
            } finally {
                busy = false
            }
        }
    }

    fun verify() {
        val entered = code.trim()
        if (entered.isEmpty() || busy) return
        busy = true
        errorLine = null
        scope.launch {
            try {
                model.verifySignInCode(email = email.trim(), code = entered)
                onSignedIn()
            } catch (_: Throwable) {
                errorLine = Copy.SIGN_IN_CODE_WRONG
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Swift animates the phase change with `withAnimation(RibbonMotion.settle)`;
        // the honest Compose equivalent is a cross-fade over the same token,
        // with the height easing rather than jumping. Under reduce motion it
        // is a state change with no animation (§11).
        AnimatedContent(
            targetState = phase,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (fadeIn(settleSpec(reduceMotion)) togetherWith fadeOut(settleSpec(reduceMotion)))
                    .using(SizeTransform(clip = false) { _, _ -> settleSpec(reduceMotion) })
            },
            label = "sign-in-phase",
        ) { current ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (current) {
                    SignInPhase.Email -> {
                        CentredField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = Copy.YOUR_EMAIL,
                            size = 17f,
                            focusRequester = emailFocus,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                capitalization = KeyboardCapitalization.None,
                                // An address is never what autocorrect thinks
                                // it is.
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { sendCode() }),
                        )
                        WayInButton(
                            title = Copy.SEND_THE_CODE,
                            onClick = { sendCode() },
                            modifier = Modifier.padding(horizontal = 40.dp),
                            enabled = email.contains("@"),
                        )
                    }

                    SignInPhase.Code -> {
                        Text(
                            text = Copy.CODE_ON_ITS_WAY,
                            style = RibbonType.ui(15f),
                            color = Palette.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CentredField(
                            value = code,
                            onValueChange = { code = it },
                            placeholder = Copy.THE_CODE,
                            size = 20f,
                            focusRequester = codeFocus,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { verify() }),
                        )
                        WayInButton(
                            title = Copy.SIGN_IN,
                            onClick = { verify() },
                            modifier = Modifier.padding(horizontal = 40.dp),
                            enabled = code.trim().isNotEmpty(),
                        )
                        QuietControl(title = Copy.SEND_A_NEW_CODE) { sendCode() }
                    }
                }
            }
        }

        // A failure names what happened and offers the one action that helps
        // (S25) — it never replaces the thread, so the field keeps whatever
        // was typed.
        errorLine?.let { line ->
            Text(
                text = line,
                style = RibbonType.ui(14f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        onCancel?.let { cancel ->
            QuietControl(title = Copy.NEVER_MIND, onClick = cancel)
        }
    }
}

/**
 * One centred, undecorated line of typing — the plain `TextField` Swift
 * uses, which draws no box and lets the prompt stand in the muted voice
 * until a character arrives.
 *
 * The field asks for focus as it arrives, which is `.onAppear { focused =
 * true }` and the `.onChange(of: phase)` that follows it: each phase's field
 * is a new node, so its own arrival is the moment.
 */
@Composable
private fun CentredField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    size: Float,
    focusRequester: FocusRequester,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
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
                .focusRequester(focusRequester),
        )
    }
}
