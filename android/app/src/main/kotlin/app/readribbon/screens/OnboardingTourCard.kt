package app.readribbon.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.Ink
import app.readribbon.fire.CampfireView
import app.readribbon.design.Palette
import app.readribbon.design.RibbonShape
import app.readribbon.design.RibbonType
import app.readribbon.design.paper
import app.readribbon.design.well
import app.readribbon.design.WaveMark
import app.readribbon.design.color

/**
 * Visual card preview for each slide in Ribbon's progressive walkthrough tour.
 */
@Composable
fun OnboardingTourCard(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val title = when (index) {
        0 -> Copy.WALKTHROUGH_VISION_TITLE
        1 -> Copy.WALKTHROUGH_PRESENCE_TITLE
        2 -> Copy.WALKTHROUGH_NOTES_TITLE
        3 -> Copy.WALKTHROUGH_FIRE_TITLE
        else -> ""
    }

    val bodyText = when (index) {
        0 -> Copy.WALKTHROUGH_VISION_BODY
        1 -> Copy.WALKTHROUGH_PRESENCE_BODY
        2 -> Copy.WALKTHROUGH_NOTES_BODY
        3 -> Copy.WALKTHROUGH_FIRE_BODY
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Feature graphic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> VisionGraphic()
                1 -> PresenceGraphic()
                2 -> NotesGraphic()
                3 -> FireGraphic()
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = title,
            style = RibbonType.display(24f),
            color = Palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = bodyText,
            style = RibbonType.ui(16f),
            color = Palette.muted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VisionGraphic() {
    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.chartreuse.copy(alpha = 0.14f), Color.Transparent),
                    ),
                    shape = CircleShape,
                )
        )
        WaveMark(
            size = 80.dp,
            tint = Palette.text,
        )
    }
}

@Composable
private fun PresenceGraphic() {
    val teal = Ink.teal.color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The same card the room draws, rather than a second opinion
            // about what a card is: `paper` fills, grains, and draws an edge
            // only on a palette where the fill alone cannot be seen. The
            // hand-rolled version drew an outline on every wallpaper, which
            // made the front door the one place in the app with borders.
            .paper(RibbonShape.cardShape)
            .padding(vertical = 16.dp, horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "In the beginning was the Word, and the Word was with God, and the Word was God.",
            style = RibbonType.scripture(17f),
            color = Palette.text.copy(alpha = 0.85f),
            lineHeight = 24.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Palette.raised)
                    .border(1.dp, teal.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(teal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "R",
                        style = RibbonType.ui(10f, FontWeight.Medium),
                        color = Palette.ground,
                    )
                }
                Text(
                    text = Copy.WALKTHROUGH_PRESENCE_SAMPLE,
                    style = RibbonType.ui(13f),
                    color = Palette.text,
                )
            }
        }
    }
}

@Composable
private fun NotesGraphic() {
    val ochre = Ink.ochre.color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .paper(RibbonShape.cardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(7.dp)
                    .background(ochre, CircleShape)
            )
            Text(
                text = "The Light shines in the darkness, and the darkness has not overcome it.",
                style = RibbonType.scripture(17f),
                color = Palette.text.copy(alpha = 0.85f),
                lineHeight = 24.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RibbonShape.rowShape)
                .background(Palette.raised)
                .border(1.dp, ochre.copy(alpha = 0.35f), RibbonShape.rowShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(ochre, CircleShape)
                )
                Text(
                    text = Copy.WALKTHROUGH_NOTE_SAMPLE,
                    style = RibbonType.ui(13f),
                    color = Palette.text,
                )
            }

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(ochre, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val ground = Palette.ground
                Canvas(modifier = Modifier.size(9.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.1f)
                        lineTo(size.width * 0.9f, size.height * 0.5f)
                        lineTo(size.width * 0.2f, size.height * 0.9f)
                        close()
                    }
                    drawPath(path, color = ground)
                }
            }
        }
    }
}

/**
 * The fire — the actual one.
 *
 * It was a gradient circle over a bar: a picture of a fire, drawn beside a
 * sentence promising the fire. The product has exactly one central object
 * and this is the card that introduces it, so showing a stand-in here is the
 * one place a stand-in cannot be afforded. [CampfireView] is what the room
 * draws, so it is what this draws — burning, at medium, with the coals half
 * deep, which is what a fire two people are keeping looks like.
 *
 * It costs the tour a 30 Hz canvas for as long as the card is up, and under
 * reduce motion it holds one instant like every other fire in the app.
 */
@Composable
private fun FireGraphic() {
    // In a well, for the reason the room's fire is in one: the fire draws
    // its ambient throw across the whole of its canvas and then clips it at
    // the edge, which on bare ground is a plainly visible rectangle of
    // slightly-warmer dark. The recess owns that edge, so there is nothing
    // to see but the glow inside it — and it is the same hearth the room
    // will hand them in about four taps, which is the better introduction
    // anyway.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .well(RibbonShape.cardShape)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CampfireView(
            state = FireState.burning,
            scale = FireScale.medium,
            coalDepth = 0.5,
        )
    }
}
