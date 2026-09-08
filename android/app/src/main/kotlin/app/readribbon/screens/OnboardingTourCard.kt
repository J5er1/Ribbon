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
import app.readribbon.core.Ink
import app.readribbon.design.Palette
import app.readribbon.design.RibbonType
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
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface.copy(alpha = 0.6f))
            .border(1.dp, Palette.rule, RoundedCornerShape(16.dp))
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.raised)
                    .border(1.dp, teal.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
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
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface.copy(alpha = 0.6f))
            .border(1.dp, Palette.rule, RoundedCornerShape(16.dp))
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
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.raised)
                .border(1.dp, ochre.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
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
                Canvas(modifier = Modifier.size(9.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.1f)
                        lineTo(size.width * 0.9f, size.height * 0.5f)
                        lineTo(size.width * 0.2f, size.height * 0.9f)
                        close()
                    }
                    drawPath(path, color = Palette.ground)
                }
            }
        }
    }
}

@Composable
private fun FireGraphic() {
    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Palette.flameCore.copy(alpha = 0.35f),
                            Palette.flameDeep.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Palette.flameBright, Palette.flameCore, Palette.flameDeep),
                        ),
                        shape = CircleShape,
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 6.dp)
                    .background(Palette.coalDim.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            )
        }
    }
}
