package app.readribbon.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.readribbon.app.Copy
import app.readribbon.design.Palette
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps

/**
 * A segmented progress indicator for Ribbon's onboarding walkthrough,
 * styled in warm chartreuse over dark ground.
 */
@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onBack,
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
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until totalSteps) {
                val targetColor = if (i <= currentStep) Palette.chartreuse else Palette.rule
                val color by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(RibbonMotion.SETTLE_MS),
                    label = "segmentColor$i",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }

        if (onSignIn != null) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onSignIn,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SmallCaps(
                    text = Copy.SIGN_IN,
                    size = 12f,
                    color = Palette.muted,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }
    }
}
