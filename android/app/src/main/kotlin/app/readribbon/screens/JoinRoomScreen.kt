@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.Person
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.well
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.WayInButton
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.room
import app.readribbon.services.InvitePreview
import app.readribbon.services.SupabaseError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// S16 — accepting an invite. The screen shows a person, not a product:
// who is inviting, the room's name, one Join control. Account creation
// (§6.10 — an emailed code, no passwords) happens here when it has to,
// because joining is the first moment an account is genuinely needed.
//
// Swift reaches the store through `@Environment(AppModel.self)` and closes
// itself through `@Environment(\.dismiss)`; nothing in this build has an
// ambient equivalent, so both are parameters, as they are on every other
// screen here.

/** `VStack(spacing: 24)` — the whole screen, and the name step. */
private val Gap = 24.dp

/** `VStack(spacing: 20)` — the preview, email and code steps. */
private val StepGap = 20.dp

/** `VStack(spacing: 18)` — the dead end. */
private val DeadGap = 18.dp

/** `.frame(maxWidth: 420)`: this screen keeps its own measure, narrower
 *  than the readable column, because it is one sentence and one control. */
private val ColumnWidth = 420.dp

/** `.padding(.horizontal, 44)` around a centred sentence. */
private val SentenceMargin = 44.dp

/** `.padding(.horizontal, 80)` around the way-in capsule. */
private val ButtonMargin = 80.dp

/** `.padding(.horizontal, 40)` / `60` around a line of typing. */
private val FieldMargin = 40.dp
private val CodeFieldMargin = 60.dp

/** The portrait well: `.frame(width: 96, height: 96)`. */
private val PortraitSize = 96.dp

/** The visible field is small; the tappable field is never under 44 dp. */
private val TouchTarget = 44.dp

/**
 * Where the flow is. Swift nests this as `JoinFlow.Phase` and makes it
 * `Equatable`; the data class carries the dead line and its equality.
 */
private sealed interface JoinPhase {
    data object Loading : JoinPhase
    data object Preview : JoinPhase
    data object Name : JoinPhase

    /**
     * The one sign-in thread (§6.10), whatever it is on this build — this
     * screen does not get its own.
     */
    data object SignIn : JoinPhase
    data object Joining : JoinPhase

    /** expired, full, unreachable — the line to show */
    data class Dead(val line: String) : JoinPhase
}

/** Which field is asking for the keyboard. Swift's `JoinFlow.Field`. */
private enum class Field { Name }

/**
 * Whether what went wrong is simply that this phone has no usable account.
 *
 * `withAuthRetry` signs a person out when the refresh token is gone
 * (RemoteSync), and `joinRoom` throws `NotSignedIn` outright when there is
 * no account at all — and both of those used to land in the same place as an
 * expired invite: a dead end whose only control is Close, shown to somebody
 * who had just been signed out by the very screen refusing them. The sign-in
 * step is already in this file and is the one thread this build has (§6.10),
 * so the honest next screen for a missing account is the one that asks for
 * it.
 */
private fun needsAnAccount(error: Throwable): Boolean =
    error is SupabaseError.NotSignedIn ||
        (
            error is SupabaseError.Http &&
                (error.status == 401 || error.body.contains("not_signed_in"))
            )

/**
 * The database names what happened; the screen says it plainly.
 */
private fun deadLine(error: Throwable): String {
    if (error is SupabaseError.Http) {
        if (error.body.contains("room_full")) return Copy.ROOM_FULL_FOR_JOINER
        if (error.body.contains("invite_expired")) return Copy.INVITE_EXPIRED
        if (error.body.contains("invite_not_found")) return Copy.INVITE_NOT_FOUND
    }
    return Copy.SERVER_UNREACHABLE
}

/**
 * Accepting an invite (S16).
 *
 * Every state the link can land in: the preview of who is inviting, an
 * invite that has expired, a room that is already full, an account that has
 * to exist first, and the join itself.
 *
 * @param token the invite the link carried.
 * @param model the store.
 * @param onDone Joined; the room is current. The caller decides what
 *   "arriving" looks like.
 * @param onDismiss Swift's `@Environment(\.dismiss)`: presented over the
 *   room, a dead end still needs its own way out, not only the swipe.
 * @param onStartInstead Onboarding only: the quiet way out to starting a
 *   room of your own.
 */
@Composable
fun JoinFlow(
    token: Uuid,
    model: AppModel,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onStartInstead: (() -> Unit)? = null,
    wayOut: String = Copy.CLOSE,
) {
    var phase by remember { mutableStateOf<JoinPhase>(JoinPhase.Loading) }
    var preview by remember { mutableStateOf<InvitePreview?>(null) }
    var name by remember { mutableStateOf("") }
    var portraitData by remember { mutableStateOf<ByteArray?>(null) }
    var portraitImage by remember { mutableStateOf<ImageBitmap?>(null) }

    /**
     * Set down mid-join (the sheet swiped away): the join completes —
     * they did join — but arriving must not happen underneath them.
     */
    var wasSetDown by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { wasSetDown = true } }

    // One field per step — a shared Bool doesn't reliably carry focus
    // from a disappearing field to an appearing one. A FocusRequester binds
    // to a single node, so each field brings its own and asks for focus as
    // it arrives, which is what Swift's `.onAppear { focused = … }` per step
    // amounts to.
    val focusRequesters = remember {
        Field.entries.associateWith { FocusRequester() }
    }

    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    // The picked portrait is decoded and downsampled off the main thread;
    // nothing about it survives the screen, so it rides the UI's own scope.
    // The downsampler is onboarding's — Swift's `downsampledJPEG` is one free
    // function used by both threads, and so is this one.
    val portraitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uiScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            } ?: return@launch
            val jpeg = withContext(Dispatchers.Default) { downsampledJpeg(bytes) }
            if (jpeg != null) {
                portraitData = jpeg
                portraitImage = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
                }
            }
        }
    }

    // MARK: Movement
    //
    // Swift runs the steps below in unstructured `Task {}`s, which are tied
    // to nothing and outlive the view. `rememberCoroutineScope()` is tied to
    // composition and would cancel a join the moment the sheet was swiped
    // away — which is precisely the case `wasSetDown` exists to get right.
    // So the work that must finish runs on the model's own scope, and only
    // the preview (Swift's `.task`, cancelled on disappear) runs on the
    // screen's.

    fun join() {
        phase = JoinPhase.Joining
        model.viewModelScope.launch {
            try {
                val roomID = model.joinRoom(inviteToken = token)
                // Only if it is still the one this flow is about: a second
                // link tapped while this join was in flight has already
                // replaced it, and clearing that would throw away an invite
                // nobody has answered yet.
                if (model.pendingInvite?.token == token) model.pendingInvite = null
                if (wasSetDown) return@launch
                model.switchRoom(roomID)
                onDone()
            } catch (error: Throwable) {
                // An account that has gone stale is a step, not a wall. The
                // sign-in phase below retries the join itself on success, so
                // this is the whole of the recovery.
                phase = if (needsAnAccount(error)) {
                    JoinPhase.SignIn
                } else {
                    JoinPhase.Dead(deadLine(error))
                }
            }
        }
    }

    fun advanceFromPreview() {
        phase = when {
            model.me == null -> JoinPhase.Name
            !model.isSignedIn -> JoinPhase.SignIn
            else -> JoinPhase.Joining
        }
        if (phase == JoinPhase.Joining) join()
    }

    fun advanceFromName() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        model.viewModelScope.launch {
            model.completeOnboarding(
                name = trimmed, portraitData = portraitData, startRoom = false)
            phase = if (model.isSignedIn) JoinPhase.Joining else JoinPhase.SignIn
            if (phase == JoinPhase.Joining) join()
        }
    }

    LaunchedEffect(token) {
        val remote = model.remote
        if (remote == null) {
            phase = JoinPhase.Dead(Copy.SERVER_UNREACHABLE)
            return@LaunchedEffect
        }
        var attempts = 0
        while (attempts < 3) {
            try {
                val found = remote.invitePreview(token)
                if (found != null) {
                    preview = found
                    phase = when {
                        found.expired -> JoinPhase.Dead(Copy.INVITE_EXPIRED)
                        found.full -> JoinPhase.Dead(Copy.ROOM_FULL_FOR_JOINER)
                        else -> JoinPhase.Preview
                    }
                    return@LaunchedEffect
                }
            } catch (_: Throwable) {
                phase = JoinPhase.Dead(Copy.SERVER_UNREACHABLE)
                return@LaunchedEffect
            }
            attempts++
            if (attempts < 3) {
                delay(1200L)
            }
        }
        phase = JoinPhase.Dead(Copy.INVITE_NOT_FOUND)
    }

    Box(modifier = modifier.fillMaxSize().room()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ColumnWidth)
                .fillMaxSize()
                // Edge to edge: the ground and its grain run under the system
                // bars, and only the content clears them. The extra bottom
                // inset three-button navigation needs — and the keyboard, for
                // the three steps that have a field — come from safeDrawing
                // itself.
                .windowInsetsPadding(WindowInsets.safeDrawing),
            verticalArrangement = Arrangement.spacedBy(Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `Spacer(); step; Spacer(); Spacer()` — the step sits a third of
            // the way down, not in the middle.
            Spacer(Modifier.weight(1f))

            // Swift animates the phase change with `withAnimation`; the
            // honest Compose equivalent is a cross-fade over the same token,
            // with the height easing rather than jumping. The dead line
            // arrives without animation there, so it arrives without one
            // here. Under reduce motion every change is instant (§11).
            AnimatedContent(
                targetState = phase,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    val spec: FiniteAnimationSpec<Float> = when {
                        targetState is JoinPhase.Dead -> snap()
                        initialState == JoinPhase.Loading && targetState == JoinPhase.Preview ->
                            RibbonMotion.arrive(reduceMotion)
                        else -> RibbonMotion.settle(reduceMotion)
                    }
                    (fadeIn(spec) togetherWith fadeOut(spec))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                RibbonMotion.settle(reduceMotion)
                            },
                        )
                },
                label = "join-phase",
            ) { current ->
                when (current) {
                    // A held beat, not a spinner. The preview answers fast or
                    // the dead line takes its place.
                    JoinPhase.Loading -> SmallCaps(
                        Copy.WORDMARK,
                        size = 12f,
                        color = Palette.muted.copy(alpha = 0.6f),
                    )

                    JoinPhase.Preview -> PreviewStep(
                        inviteLine = inviteLine(preview),
                        inviterName = preview?.inviterName,
                        roomName = preview?.roomName,
                        joiningAs = model.me?.name
                            ?.takeIf { model.isSignedIn }
                            ?.let(::firstName),
                        onJoin = { advanceFromPreview() },
                        onJoinAsSomeoneElse = if (model.isSignedIn) {
                            {
                                model.viewModelScope.launch {
                                    model.signOutRemote()
                                    phase = JoinPhase.SignIn
                                }
                            }
                        } else {
                            null
                        },
                        onStartInstead = onStartInstead,
                    )

                    JoinPhase.Name -> NameStep(
                        name = name,
                        onNameChange = { name = it },
                        portrait = portraitImage,
                        onPickPortrait = {
                            portraitPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        focusRequester = focusRequesters.getValue(Field.Name),
                        onSubmit = { advanceFromName() },
                    )

                    JoinPhase.SignIn -> SignInStep(
                        model = model,
                        onSignedIn = { join() },
                        onStartInstead = onStartInstead,
                    )

                    JoinPhase.Joining -> SmallCaps(Copy.JOINING, size = 12f)

                    is JoinPhase.Dead -> DeadStep(
                        line = current.line,
                        onStartInstead = onStartInstead,
                        onDismiss = onDismiss,
                        wayOut = wayOut,
                    )
                }
            }

            Spacer(Modifier.weight(2f))
        }
    }
}

/** Who is inviting, said in their first name — or, without one, plainly. */
private fun inviteLine(preview: InvitePreview?): String {
    val inviter = preview?.inviterName
    if (inviter != null && inviter.isNotEmpty()) {
        return Copy.wantsToReadWithYou(firstName(inviter))
    }
    return Copy.SOMEONE_WANTS_TO_READ_WITH_YOU
}

// MARK: Steps

@Composable
private fun PreviewStep(
    inviteLine: String,
    inviterName: String?,
    roomName: String?,
    joiningAs: String?,
    onJoin: () -> Unit,
    onJoinAsSomeoneElse: (() -> Unit)?,
    onStartInstead: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(StepGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // S16's anatomy is "who invited you, **their portrait**, the room's
        // name, and one control", and this screen had never drawn a face at
        // all — on the one screen the build book says "shows a person, not a
        // product", and which it calls the most important conversion surface
        // in the product.
        //
        // A monogram rather than the real photograph, and the reason is the
        // backend rather than the design: the portraits bucket is readable
        // only by co-members, and a person holding an invite is not one yet.
        // Reaching the real face needs an edge function that takes the token,
        // validates it and streams the portrait — worth doing, not an
        // Android-only change, and recorded in docs/deviations.md A36. A
        // monogram in the right recess is a person; nothing at all is a form.
        if (!inviterName.isNullOrBlank()) {
            PortraitView(person = Person(name = inviterName), size = PortraitSize)
        }
        Text(
            text = inviteLine,
            style = RibbonType.display(24f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SentenceMargin),
        )
        if (roomName != null && roomName.isNotEmpty()) {
            SmallCaps(roomName, size = 13f)
        }
        WayInButton(
            title = Copy.JOIN,
            modifier = Modifier.padding(horizontal = ButtonMargin).padding(top = 8.dp),
            onClick = onJoin,
        )
        // S16's last state: "signed in as someone else (offers to switch,
        // does not silently join)". It did not exist — a tap on Join seated
        // whoever this phone happened to be signed in as, without ever saying
        // who that was, and the only route to another account was the sign-out
        // control two taps deep in the menu. Said only when there is somebody
        // to name; signed out, there is nobody.
        if (joiningAs != null) {
            Text(
                text = Copy.joiningAs(joiningAs),
                style = RibbonType.ui(13f),
                color = Palette.muted,
                textAlign = TextAlign.Center,
            )
            onJoinAsSomeoneElse?.let { switch ->
                QuietControl(title = Copy.JOIN_AS_SOMEONE_ELSE, onClick = switch)
            }
        }
        onStartInstead?.let { start ->
            QuietControl(title = Copy.START_A_ROOM_INSTEAD, onClick = start)
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    portrait: ImageBitmap?,
    onPickPortrait: () -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // `PhotosPicker(matching: .images)` — the photo picker, which on
        // Android is the same thing: a system picker that hands back one
        // image and never asks for access to the library.
        Box(
            modifier = Modifier
                .size(PortraitSize)
                // A well, not a card — the same recess onboarding asks for
                // the same face in. Two screens that want one thing should
                // not have two opinions about what the empty slot is.
                .then(if (portrait == null) Modifier.well(CircleShape) else Modifier.clip(CircleShape))
                .clickable(onClick = onPickPortrait)
                .semantics(mergeDescendants = true) {
                    contentDescription = Copy.ADD_A_PORTRAIT
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(PortraitSize),
                )
            } else {
                SmallCaps(Copy.ADD_A_PORTRAIT, size = 11f)
            }
        }
        Text(
            text = Copy.PORTRAIT_REASON,
            style = RibbonType.ui(15f),
            color = Palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        CentredTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = Copy.YOUR_NAME,
            size = 20f,
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.padding(horizontal = FieldMargin),
        )
        WayInButton(
            title = Copy.THATS_ME,
            modifier = Modifier.padding(horizontal = ButtonMargin),
            enabled = name.trim().isNotEmpty(),
            onClick = onSubmit,
        )
    }
}

/**
 * Joining is the first moment an account is genuinely needed, so this is
 * where it happens — and it is the same sign-in the rest of the app offers
 * (Auth0 where the build has it, a passkey where the platform does, an
 * emailed code underneath either). A second, hand-rolled email-and-code pair
 * here would quietly mint a *different* kind of account from everyone
 * else's, and the room would seat a stranger.
 */
@Composable
private fun SignInStep(
    model: AppModel,
    onSignedIn: () -> Unit,
    onStartInstead: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(StepGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Copy.ACCOUNT_REASON,
            style = RibbonType.ui(16f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SentenceMargin),
        )
        SignInInline(
            model = model,
            modifier = Modifier.padding(horizontal = ButtonMargin),
            onSignedIn = onSignedIn,
        )
        if (onStartInstead != null) {
            // Never a step without a way out.
            QuietControl(title = Copy.START_A_ROOM_INSTEAD, onClick = onStartInstead)
        }
    }
}

@Composable
private fun DeadStep(
    line: String,
    onStartInstead: (() -> Unit)?,
    onDismiss: () -> Unit,
    wayOut: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DeadGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line,
            style = RibbonType.ui(17f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SentenceMargin),
        )
        if (onStartInstead != null) {
            QuietControl(title = Copy.START_A_ROOM_INSTEAD, onClick = onStartInstead)
        } else {
            // Presented over the room: a dead end still needs its own way
            // out, not only the swipe. Pushed inside the menu it goes back
            // one step instead — to the paste field, which is exactly where
            // "ask for a new one" lands — so the caller names it.
            QuietControl(title = wayOut, onClick = onDismiss)
        }
    }
}

