package app.readribbon.fire

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fire's light, and an ember's, die to nothing inside their own canvas
 * (A70).
 *
 * The warm throw was a round gradient cut by a shorter ellipse and then by
 * the canvas floor, and an ember's bloom was an oval bigger than the
 * ember's box; both were still well on where they were cut, so the room
 * showed a crisp arc and a hard flat floor under the fire, and the shelf a
 * lit slab under every ember. Both are gradients squashed to fit now, clear
 * at their own rims, and the property worth holding is the one a reader
 * would see: **nothing lit reaches an edge.** So each is drawn into a clear
 * canvas of its own and the outermost ring of pixels is read.
 *
 * Read as alpha, which on a clear canvas is how much light was laid there.
 * The bound is a few levels rather than none, because a gradient that
 * reaches clear exactly at the frame — the throw does at the top of its
 * breath, and the bloom does at its sides and floor — leaves a level or so
 * in the last row, the half-pixel before its rim, and that is not an edge
 * anybody can see. The old throw's floor sat at about four fifths of its
 * peak, and the old bloom's at three quarters of its own: tens of levels,
 * not a few.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FireLightTest {

    /** A fixed instant: the painter is deterministic in `time`. */
    private val instant = 402.7

    /** The most a pixel on the canvas's edge may carry — under 2% of full. */
    private val faint = 4

    /**
     * Every state at the shallowest and the deepest coal bed, the deepest
     * being the widest throw. Room-sized — over the 48 dp bar, so the whole
     * fire draws — and under the hot air's bar, whose wisps are not what is
     * being measured.
     */
    @Test
    fun theThrowDiesInsideTheFiresCanvas() {
        for (state in FireState.entries) {
            for (coalDepth in listOf(0.0, 1.0)) {
                val fire = pixels(width = 320, height = 240) {
                    FirePainter.draw(
                        into = this, time = instant,
                        state = state, scale = FireScale.large, coalDepth = coalDepth,
                    )
                }
                assertFalse("the $state fire drew nothing at all", fire.all { it == 0 })
                val edge = brightestEdge(fire, width = 320, height = 240)
                assertTrue(
                    "the $state fire at coal depth $coalDepth lights its canvas's edge ($edge)",
                    edge <= faint,
                )
            }
        }
    }

    /** In `EmberView`'s own proportions, at a few breaths. */
    @Test
    fun theBloomDiesInsideTheEmbersBox() {
        for (time in listOf(0.0, 1.3, 2.9)) {
            val ember = pixels(width = 170, height = 135) { drawEmber(time = time) }
            assertFalse("the ember drew nothing at all", ember.all { it == 0 })
            val edge = brightestEdge(ember, width = 170, height = 135)
            assertTrue("the ember lights its box's edge ($edge)", edge <= faint)
        }
    }

    /** The largest alpha in the top and bottom rows and the outer columns. */
    private fun brightestEdge(argb: IntArray, width: Int, height: Int): Int {
        var brightest = 0
        fun look(x: Int, y: Int) {
            brightest = maxOf(brightest, argb[y * width + x] ushr 24)
        }
        for (x in 0 until width) {
            look(x, 0)
            look(x, height - 1)
        }
        for (y in 0 until height) {
            look(0, y)
            look(width - 1, y)
        }
        return brightest
    }

    /** Renders [block] into a fresh, clear bitmap and reads it back as ARGB. */
    private fun pixels(width: Int, height: Int, block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            density = Density(density = 2.75f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(width.toFloat(), height.toFloat()),
            block = block,
        )
        val android: Bitmap = image.asAndroidBitmap()
        val out = IntArray(width * height)
        android.getPixels(out, 0, width, 0, 0, width, height)
        return out
    }
}
