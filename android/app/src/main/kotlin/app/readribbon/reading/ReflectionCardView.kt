@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.CardState
import app.readribbon.core.Ink
import app.readribbon.core.Reading
import app.readribbon.core.ReflectionCard
import app.readribbon.core.Room
import app.readribbon.design.HairlineRule
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.QuietControl
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps
import app.readribbon.design.color
import app.readribbon.design.paper
import app.readribbon.design.rememberReduceMotion
import kotlin.uuid.ExperimentalUuidApi

// The cards (S08 / S09, §4.6) — a question everyone answers before anyone
// reads the answers.
//
// This is the one surface the app's two design passes never reached, and it
// showed in five ways at once: every word on it was a literal rather than a
// line of [Copy]; "SET IT DOWN" was typed in capitals at a small-caps face,
// which is exactly the textTransform §09 forbids; the turn was a raw
// `tween(480)` and so was the only animation in the app with no reduce-motion
// path (§11); all four controls were bare `clickable` text well under the
// 44 dp floor (deviation 12); and the card was drawn with a hand-rolled
// 12 dp rectangle and a hairline border while every other card in the app had
// moved to the shape scale and the paper grain (deviation A23).
//
// The two that were not cosmetic:
//
//   1. **The turn was never a turn.** S09 asks for "a slow turn over 480 ms,
//      ease-out, no bounce", and §11 says that under reduce motion "the card
//      turn becomes a fade" — a sentence that only means something if there
//      is a turn to reduce. There was only ever the fade, on both settings.
//      The card turns now, and reduce motion gets §11's fade exactly.
//   2. **A half-typed answer was lost** to anything that disposed the
//      composition — rotating the phone, the keyboard resizing the window,
//      the process being trimmed in the background. `answerDraft` was
//      `remember`, and a card is a thing people think about before typing.
//
// What is deliberately unchanged: the card still cannot be un-answered. §4.6
// gives the room one escape hatch and it is *set it down*, which retires the
// card for everybody without ceremony; taking an answer back on your own
// would leave a card that can never open and no one to say so, which is the
// debt the whole mechanic exists to avoid.
//
// iOS carries the same defects in `ios/Ribbon/Reading/ReflectionCardView.swift`
// and is left alone on purpose — this pass is Android's (docs/deviations.md
// A32). The copy constants above are the shared half and port straight across.

/** `.padding(22)` — the card's own inset, unchanged. */
private val CardPadding = 22.dp

/** `VStack(spacing: 18)` — question to content. */
private val Gap = 18.dp

/** Between the answers on an open card, and down the sealed one. */
private val AnswerGap = 16.dp
private val SealedGap = 14.dp

/**
 * [QuietControl] pads itself by 8 dp so its 44 dp target clears the words it
 * draws. Pulling it back by that same 8 dp puts the words where they would
 * have been without the target, flush with what they sit under.
 */
private val QuietControlInset: Dp = (-8).dp

/**
 * How close the hairline sits under the question when nothing has been typed
 * yet.
 *
 * S08 asks for "an answer field, open, no placeholder text beyond a single
 * hairline", and the first attempt at that read the sentence as a *size* —
 * 72 dp of field with a rule under it. On the page that is a hole: a
 * question, a void, and a faint line a long way beneath it, with nothing
 * anywhere saying that the void is where you write. So the field takes the
 * height of what is in it, one line when that is nothing, and the hairline
 * comes up to meet the question. A line to write on is an invitation; a box
 * of empty space is a gap in the page.
 */
private val FieldLead = 6.dp

/**
 * How far past halfway the turn has to be before the card shows its other
 * face: exactly halfway, where the card is edge-on and there is nothing to
 * see either way. Swapping earlier or later shows one face reversed.
 */
private const val TURN_EDGE_ON = 0.5f

/** How far the card leans as it turns. A half-turn, because the card lands
 *  facing the reader again rather than continuing away from them. */
private const val TURN_DEGREES = 180f

/**
 * One reflection card, sealed or open (S08 / S09).
 *
 * @param card the card as the room currently holds it. A set-down card is
 *   not drawn at all — and the caller animates it away rather than dropping
 *   it in a frame, because §4.6's "without ceremony" is about the absence of
 *   a dialog, not about the absence of motion (§9.1).
 */
@Composable
fun ReflectionCardView(
    card: ReflectionCard,
    reading: Reading,
    room: Room,
    model: AppModel,
    modifier: Modifier = Modifier,
) {
    if (card.state == CardState.setDown) return

    val me = model.state.me
    val myAnswer = me?.let { card.answers[it.id] }
    val myInk = model.myMembership(room)?.ink ?: Ink.ochre
    val still = rememberReduceMotion()

    // Saveable rather than remembered: a card is a thing people think about
    // before they type, and the keyboard resizing the window is enough to
    // dispose this composition. Keyed on the card so two cards in one
    // chapter never share a draft, and on the saved answer so that answering
    // reseeds the field with what was actually kept.
    var answerDraft by rememberSaveable(card.id, myAnswer) {
        mutableStateOf(myAnswer ?: "")
    }
    var isEditing by rememberSaveable(card.id) { mutableStateOf(false) }

    // The turn (S09): 0 sealed, 1 open, on the open token — which is §9.1's
    // 480 ms ease-out, the duration S09 asks for by name. Under reduce
    // motion the token snaps and the cross-fade inside the card is all that
    // is left, which is §11's "the card turn becomes a fade" precisely.
    //
    // Held as a `State` rather than unwrapped with `by`: the angle is read
    // inside the layer block below, so the card *turns* without recomposing
    // a word of what is written on it. Unwrapping it here would read the
    // value in composition and recompose the question, both faces and every
    // answer on every frame of the turn.
    val turn: State<Float> = animateFloatAsState(
        targetValue = if (card.state == CardState.open) 1f else 0f,
        animationSpec = RibbonMotion.open(still),
        label = "the-card-turns",
    )

    // Which face the card is showing. Derived, so the content swaps once —
    // at the moment the card is edge-on, where there is nothing to see
    // either way — rather than on every frame the angle changes.
    val showingOpen by remember(turn) {
        derivedStateOf { turn.value >= TURN_EDGE_ON }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // A turn, not a flip: the card leans away, goes edge-on, and
                // comes back facing the reader. `cameraDistance` is raised
                // well past Compose's default because the default is drawn
                // for a small view and makes a full-width card look as
                // though it is being thrown at the reader — §9.1 has no
                // parallax and this is the same complaint in three
                // dimensions.
                val angle = turn.value * TURN_DEGREES
                cameraDistance = 18f * density
                // Past the halfway point the card's other face is toward the
                // reader, and the content has already swapped to it — so the
                // remaining rotation is taken off rather than drawn, which
                // is what keeps the open face from being read in a mirror.
                rotationY = if (turn.value >= TURN_EDGE_ON) angle - TURN_DEGREES else angle
            }
            .paper(RibbonShape.cardShape)
            .padding(CardPadding)
            // The card grows as its answers arrive. Without this the open
            // face lands at its full height on the frame it appears, which
            // reads as the page jumping rather than the card opening.
            .animateContentSize(animationSpec = RibbonMotion.settle(still)),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        // The question in Literata, generously set (§4.6, S08/S09). It is the
        // card's heading, and says so: a screen reader can move between the
        // cards in a chapter by heading rather than by reading every answer
        // on the way (§11).
        Text(
            text = card.question,
            style = RibbonType.scripture(19f),
            color = Palette.text,
            modifier = Modifier.semantics {
                heading()
                contentDescription = if (showingOpen) {
                    Copy.cardOpenSpoken(card.question)
                } else {
                    Copy.cardSealedSpoken(card.question)
                }
            },
        )

        // Inside the turn, the two faces cross-fade. On a card that is
        // turning this is hidden by the edge-on swap; under reduce motion,
        // where the turn snaps, it is the whole of the transition.
        AnimatedContent(
            targetState = showingOpen,
            transitionSpec = {
                fadeIn(RibbonMotion.open(still)) togetherWith
                    fadeOut(RibbonMotion.open(still))
            },
            label = "the-card's-two-faces",
        ) { open ->
            if (open) {
                OpenCard(card = card, room = room, model = model)
            } else {
                SealedCard(
                    card = card,
                    room = room,
                    model = model,
                    myAnswer = myAnswer,
                    myInk = myInk,
                    isEditing = isEditing,
                    draft = answerDraft,
                    onDraftChange = { answerDraft = it },
                    onStartEditing = {
                        answerDraft = myAnswer ?: ""
                        isEditing = true
                    },
                    onKeepWhatIHad = {
                        answerDraft = myAnswer ?: ""
                        isEditing = false
                    },
                    onAnswered = { isEditing = false },
                )
            }
        }
    }
}

/**
 * S08 — the card, sealed: the question is visible, your answer field is
 * open, and one line says what it is waiting for.
 *
 * The rules that govern every word here are §4.6's: it never names who
 * hasn't answered, and it never says how many have. A sealed card has no
 * expiry, no reminder and no way to nudge anybody, so there is nothing else
 * for this half of the card to hold.
 */
@Composable
private fun SealedCard(
    card: ReflectionCard,
    room: Room,
    model: AppModel,
    myAnswer: String?,
    myInk: Ink,
    isEditing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onKeepWhatIHad: () -> Unit,
    onAnswered: () -> Unit,
) {
    val still = rememberReduceMotion()
    Column(verticalArrangement = Arrangement.spacedBy(SealedGap)) {
        if (myAnswer != null && !isEditing) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = myAnswer,
                    style = RibbonType.ui(16f),
                    // Ivory, not the ink. The eight inks are cut for a 24%
                    // highlight wash and for a name at 12 sp, not for a
                    // paragraph at 16 sp — §11 asks for every ink to be
                    // verified for *the text under the wash*, and a whole
                    // answer set in Moss on the surface is the one place the
                    // palette does not clear. `NoteCard` settled this the
                    // same way: the ink identifies, the ivory is read.
                    color = Palette.text,
                )
                // A real 44 dp target rather than a line of type (§11).
                // [QuietControl] pads itself by 8 dp so the target clears the
                // words; pulling it back by the same 8 dp puts the words
                // themselves flush with the answer above, which is the same
                // correction the menu's quiet controls make.
                QuietControl(
                    title = Copy.EDIT_YOUR_ANSWER,
                    modifier = Modifier.offset(x = QuietControlInset),
                    onClick = onStartEditing,
                )
            }

            // §4.6's one line. It goes under the answer rather than over it,
            // because what you wrote is the thing you came back to see.
            Text(
                text = Copy.CARD_OPENS_WHEN_EVERYONE_HAS_ANSWERED,
                style = RibbonType.ui(14f),
                color = Palette.muted,
            )
        } else {
            AnswerField(
                draft = draft,
                onDraftChange = onDraftChange,
                myInk = myInk,
                focusOnArrival = isEditing,
            )

            // The controls arrive with the words they act on. An empty field
            // had a greyed-out "Answer" sitting under it, which is a control
            // that says exactly what happens and then does not do it — the
            // defect S22's row was already rewritten to remove. There is
            // nothing to keep until something has been typed, so until then
            // there is nothing drawn to keep it with.
            val trimmed = draft.trim()
            AnimatedVisibility(
                visible = trimmed.isNotEmpty() || isEditing,
                enter = fadeIn(RibbonMotion.arrive(still)) +
                    expandVertically(RibbonMotion.arrive(still)),
                exit = fadeOut(RibbonMotion.arrive(still)) +
                    shrinkVertically(RibbonMotion.arrive(still)),
                label = "somewhere-to-put-it",
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QuietControl(
                        title = Copy.ANSWER,
                        modifier = Modifier.offset(x = QuietControlInset),
                        size = 14f,
                        color = if (trimmed.isEmpty()) Palette.muted else Palette.accent,
                    ) {
                        if (trimmed.isNotEmpty()) {
                            model.answerCard(card, trimmed, room)
                            onAnswered()
                        }
                    }
                    if (isEditing) {
                        // "Cancel" says nothing about what happens. This
                        // does: the answer you already gave is the one that
                        // stays.
                        QuietControl(title = Copy.KEEP_WHAT_I_HAD, onClick = onKeepWhatIHad)
                    }
                }
            }
        }

        // §4.6's pressure valve, and the reason a card can wait forever
        // without becoming a debt. Quiet, at the foot, and off to the side:
        // the same place and the same weight every undoing control in the app
        // has (deviation A23).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            QuietControl(
                title = Copy.SET_IT_DOWN,
                size = 12f,
                color = Palette.muted,
            ) { model.setDownCard(card) }
        }
    }
}

/**
 * The answer field: open, with no prompt and no box.
 *
 * S08 asks for "your answer field, open, no placeholder text beyond a single
 * hairline", and the field it replaces had both a box and a "Your thoughts…"
 * prompt — an ellipsis and a piece of chrome on the most considered moment in
 * the product. The hairline is the whole of it. The cursor is your own ink,
 * which is the one place on this card colour is doing work.
 *
 * @param focusOnArrival true when the field arrived because somebody asked to
 *   edit an answer they had already given; the keyboard should be there
 *   waiting. An unanswered card does *not* take focus — S08's field is open,
 *   not demanding, and a keyboard that rises the moment you reach the end of
 *   a chapter is an interruption.
 */
@Composable
private fun AnswerField(
    draft: String,
    onDraftChange: (String) -> Unit,
    myInk: Ink,
    focusOnArrival: Boolean,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnArrival) {
        if (focusOnArrival) runCatching { focus.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            textStyle = RibbonType.ui(16f).copy(color = Palette.text),
            cursorBrush = SolidColor(myInk.color),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                // Not Done: an answer is a sentence or three and the return
                // key is how you get the second one.
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = FieldLead)
                .focusRequester(focus)
                // With no prompt drawn, there is nothing on screen for a
                // screen reader to take the field's name from (§11).
                .semantics { contentDescription = Copy.YOUR_ANSWER },
        )
        HairlineRule()
    }
}

/**
 * S09 — the card, open: every answer, each with its author's portrait and
 * ink.
 *
 * In membership order rather than by time: §S09 forbids ordering by time and
 * forbids "first to answer", and membership order is the one ordering in the
 * room that says nothing about who was quick. No timestamps, no reactions, no
 * replies — a card is a moment, not a thread.
 */
@Composable
private fun OpenCard(
    card: ReflectionCard,
    room: Room,
    model: AppModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AnswerGap)) {
        HairlineRule()

        model.members(room).forEach { member ->
            val answer = card.answers[member.personID] ?: return@forEach
            val person = model.person(member.personID)
            val ink = member.ink ?: Ink.ochre
            // First names only, everywhere a person is named in a line of
            // copy (§10). The name was split by hand here and fell back to
            // the invented word "Reader" — a person with no name is somebody
            // the room is still waiting on, and naming them at all was the
            // bug. `firstName` is the one helper that does this.
            val name = person?.name?.let(::firstName)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = Copy.answerFrom(name ?: "", answer)
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PortraitView(
                        person = person,
                        ink = ink,
                        size = 22.dp,
                        image = person?.let { model.portrait(it.id) },
                    )
                    if (name != null) {
                        SmallCaps(name, size = 12f, color = ink.color)
                    }
                }
                Text(
                    text = answer,
                    style = RibbonType.ui(16f),
                    // Ivory for the same reason the sealed card's is: the
                    // portrait's ring and the name carry whose it is, and an
                    // answer is something to read.
                    color = Palette.text,
                )
            }
        }
    }
}
