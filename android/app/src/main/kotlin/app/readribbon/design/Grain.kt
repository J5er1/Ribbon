package app.readribbon.design

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

// Texture (build book §9.2). A tileable paper grain over the ground at 3.5%
// opacity. At #0B0B0A a flat fill reads as switched-off app chrome; the
// grain is the entire difference between a near-black screen and an unlit
// room. Applied above the ground and below all content, never over Scripture
// glyphs themselves.

private const val GRAIN_OPACITY = 0.035f

@Composable
private fun grainBitmap(): ImageBitmap {
    val context = LocalContext.current
    return remember(context) {
        context.assets.open("paper_grain.png").use { stream ->
            BitmapFactory.decodeStream(stream).asImageBitmap()
        }
    }
}

/**
 * The unlit room: ground plus grain behind this content.
 *
 * The grain is a repeating shader rather than a stack of tiled images, so it
 * costs one draw call at any size and stays put while content scrolls over
 * it — paper does not parallax.
 */
@Composable
fun Modifier.room(): Modifier {
    val bitmap = grainBitmap()
    val brush = remember(bitmap) {
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    return this
        .background(Palette.ground)
        .drawBehind { drawRect(brush = brush, alpha = GRAIN_OPACITY) }
        .semantics { hideFromAccessibility() }
}

/** The grain alone, for a surface that already paints its own ground. */
@Composable
fun Modifier.grain(opacity: Float = GRAIN_OPACITY): Modifier {
    val bitmap = grainBitmap()
    val brush = remember(bitmap) {
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    return this.drawBehind { drawRect(brush = brush, alpha = opacity) }
}
