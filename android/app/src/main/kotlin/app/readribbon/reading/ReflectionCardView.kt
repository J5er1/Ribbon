@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.readribbon.app.AppModel
import app.readribbon.core.CardState
import app.readribbon.core.Ink
import app.readribbon.core.Reading
import app.readribbon.core.ReflectionCard
import app.readribbon.core.Room
import app.readribbon.design.HairlineRule
import app.readribbon.design.Palette
import app.readribbon.design.PortraitView
import app.readribbon.design.RibbonType
import app.readribbon.design.color
import kotlin.uuid.ExperimentalUuidApi

/**
 * Reflection Card (S08 / S09):
 * A question everyone answers before anyone reads the answers.
 *
 * Sealed (S08):
 * - Question in Literata, generously set
 * - Answer field (or answered view with tap to edit)
 * - "This opens when everyone has answered."
 * - "set it down" button (small caps, low contrast)
 * - Rules: never names who hasn't answered, never shows count
 *
 * Open (S09):
 * - 480 ms ease-out transition
 * - Every answer with author's portrait, first name, and ink color
 * - No timestamps, no reactions, no replies
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
    val myMembership = room.let { model.myMembership(it) }
    val myInk = myMembership?.ink ?: Ink.ochre

    var answerDraft by remember(card.id, myAnswer) { mutableStateOf(myAnswer ?: "") }
    var isEditing by remember(card.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.rule, RoundedCornerShape(12.dp))
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Question in Literata (§4.6, S08/S09)
            Text(
                text = card.question,
                style = RibbonType.scripture(19f),
                color = Palette.text,
            )

            AnimatedContent(
                targetState = card.state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(480)) togetherWith
                        fadeOut(animationSpec = tween(480))
                },
                label = "CardFlipTransition"
            ) { state ->
                if (state == CardState.open) {
                    // S09: Open card
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HairlineRule()

                        val members = model.members(room)
                        for (member in members) {
                            val answer = card.answers[member.personID]
                            if (answer != null) {
                                val person = model.person(member.personID)
                                val ink = member.ink ?: Ink.ochre
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PortraitView(
                                            person = person,
                                            ink = ink,
                                            size = 22.dp,
                                            image = person?.let { model.portraits[it.id] }
                                        )
                                        val firstName = person?.name?.split(" ")?.firstOrNull() ?: "Reader"
                                        Text(
                                            text = firstName,
                                            style = RibbonType.smallCaps(12f),
                                            color = ink.color
                                        )
                                    }

                                    Text(
                                        text = answer,
                                        style = RibbonType.ui(16f),
                                        color = ink.color
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // S08: Sealed card
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (myAnswer != null && !isEditing) {
                            // Answered state
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = myAnswer,
                                    style = RibbonType.ui(16f),
                                    color = myInk.color
                                )

                                Text(
                                    text = "Edit your answer",
                                    style = RibbonType.smallCaps(12f),
                                    color = Palette.muted,
                                    modifier = Modifier.clickable {
                                        answerDraft = myAnswer
                                        isEditing = true
                                    }
                                )
                            }

                            // The quiet waiting line (§4.6, S08):
                            // Never names who hasn't answered · never shows how many have
                            Text(
                                text = "This opens when everyone has answered.",
                                style = RibbonType.ui(14f),
                                color = Palette.muted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            // Unanswered or editing state
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Palette.ground.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .border(1.dp, Palette.rule, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    if (answerDraft.isEmpty()) {
                                        Text(
                                            text = "Your thoughts...",
                                            style = RibbonType.ui(16f),
                                            color = Palette.muted.copy(alpha = 0.5f)
                                        )
                                    }
                                    BasicTextField(
                                        value = answerDraft,
                                        onValueChange = { answerDraft = it },
                                        textStyle = RibbonType.ui(16f).copy(color = Palette.text),
                                        cursorBrush = SolidColor(Palette.chartreuse),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val trimmed = answerDraft.trim()
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, Palette.chartreuse.copy(alpha = 0.4f), CircleShape)
                                            .clickable(enabled = trimmed.isNotEmpty()) {
                                                model.answerCard(card, trimmed, room)
                                                isEditing = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Answer",
                                            style = RibbonType.ui(15f, FontWeight.Medium),
                                            color = if (trimmed.isNotEmpty()) Palette.chartreuse else Palette.muted
                                        )
                                    }

                                    if (isEditing) {
                                        Text(
                                            text = "Cancel",
                                            style = RibbonType.ui(15f),
                                            color = Palette.muted,
                                            modifier = Modifier
                                                .clickable {
                                                    answerDraft = myAnswer ?: ""
                                                    isEditing = false
                                                }
                                                .padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Set it down action (S08)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "SET IT DOWN",
                                style = RibbonType.smallCaps(12f),
                                color = Palette.muted.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clickable { model.setDownCard(card) }
                                    .padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
