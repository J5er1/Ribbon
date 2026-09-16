package app.readribbon.screens

import androidx.compose.animation.animateColorAsState
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
import app.readribbon.design.BackChevron
import app.readribbon.design.Palette
import app.readribbon.design.QuietControl
import app.readribbon.design.rememberReduceMotion
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonType
import app.readribbon.design.SmallCaps

/**
 * A segmented progress indicator for Ribbon's onboarding walkthrough,
 * styled in warm chartreuse over dark ground.
 */
/** The two controls that flank the bar, and the space one keeps when it
 *  is not there. The floor every target in the app keeps (deviation 12). */
private val ControlSize = 44.dp

@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
) {
    val still = rememberReduceMotion()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The app's own back chevron rather than a second hand-drawn one.
        // This was a 36 dp box — eight under the floor §11 and deviation 12
        // set — holding an unlabelled `Canvas`, with `indication = null`, so
        // it was undersized, silent to a screen reader and gave nothing back
        // under a finger. `BackChevron` is 44 dp, says what it is, and takes
        // the app's soft state layer (§12.2).
        if (onBack != null) {
            BackChevron(onBack = onBack, label = Copy.BACK)
        } else {
            Spacer(modifier = Modifier.size(ControlSize))
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
                    // The token, not its duration. `tween(SETTLE_MS)` with no easing
            // argument takes Compose's default — `FastOutSlowInEasing`, an
            // ease-in-*out* — so this was the one thing in the app moving on
            // a curve §9.1's table does not contain, and the only animation
            // left that reduce motion could not reach.
            animationSpec = RibbonMotion.settle(still),
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

        // `QuietControl` is this exact control — small caps, muted, a 44 dp
        // target and a role the screen reader can hear — and this was a
        // second copy of it eight dp short, with its indication switched off.
        if (onSignIn != null) {
            QuietControl(title = Copy.SIGN_IN, size = 12f, onClick = onSignIn)
        } else {
            Spacer(modifier = Modifier.size(ControlSize))
        }
    }
}
