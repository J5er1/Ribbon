package app.readribbon.fire

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fire draws out of buffers it keeps rather than buffers it makes.
 *
 * A42b turned every per-frame allocation in [FirePainter] into a reused
 * object: four scratch `Path`s, two pairs of coordinate `FloatArray`s, the
 * blurred passes' `Paint`s, and the cross-fade's layer paint. That is the
 * right shape for something redrawing thirty times a second — but shared
 * mutable scratch has exactly one failure mode, and it is a bad one: a shape
 * built while another is still being drawn, or a buffer left dirty so the
 * *next* fire inherits a stray point.
 *
 * The look book cannot catch it. The fire's breath seed is
 * `Random.nextDouble()` by design (§4.1: no two fires on one screen breathe in
 * step), so two runs of the same code draw different frames and no PNG can be
 * compared against a stored one.
 *
 * So this tests the property that actually matters instead: **drawing is a
 * function of its arguments.** Same arguments, same picture, no matter what
 * was drawn before it. Every scratch-bleed bug breaks that, and nothing else
 * does — a fire that came out wrong but came out wrong identically every time
 * would be a bug in the geometry, which is what the look book is for.
 *
 * One limitation, stated because it decides how to read a failure. The scratch
 * is process-wide, as it has to be, so these four tests are not isolated from
 * one another: a leak corrupts whatever runs next, and a path that is never
 * reset eventually accumulates enough to saturate, after which two draws agree
 * again for the wrong reason. So the *suite* is the assertion, not any one
 * test, and which one fails is not meaningful. Checked by deleting the tongue
 * path's `reset()` on purpose: the suite went red, and one test of the four
 * stayed green.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FireScratchTest {

    /**
     * A fixed instant rather than a clock: the painter is deterministic in
     * `time`, and the whole point here is to vary nothing but the history.
     */
    private val instant = 402.7

    /** Room size — over the 48dp bar, so blur, sparks, shed and hot air all run. */
    private fun roomFire(): IntArray = pixels(width = 320, height = 240) {
        FirePainter.draw(
            into = this, time = instant,
            state = FireState.burning, scale = FireScale.large, coalDepth = 0.4,
        )
    }

    /** Glyph size — under the bar, so the cheap path runs instead. */
    private fun glyphFire(): IntArray = pixels(width = 31, height = 22) {
        FirePainter.draw(
            into = this, time = instant,
            state = FireState.steady, scale = FireScale.small, coalDepth = 0.3,
        )
    }

    /** Mid-cross — the `saveLayer` path, which reuses the cross-fade paint. */
    private fun crossingFire(): IntArray = pixels(width = 320, height = 240) {
        FirePainter.draw(
            into = this, time = instant,
            state = FireState.catching, scale = FireScale.medium, coalDepth = 0.1,
            opacity = 0.45f,
        )
    }

    @Test
    fun theSameFireTwiceIsTheSameFire() {
        val first = roomFire()
        val second = roomFire()
        assertFalse("the fire drew nothing at all", first.all { it == 0 })
        assertArrayEquals(first, second)
    }

    /**
     * The interleaving case, as close as Compose can get to it: a glyph is a
     * different size, a different state and a different set of code paths, and
     * it is drawn between two room fires. In the app this is the rooms sheet
     * over the hearth, or the chooser's length indicators under one. Each
     * Canvas draws in full before the next begins — that is what makes one
     * scratch buffer safe — and this asserts it.
     */
    @Test
    fun drawingSomethingElseInBetweenChangesNothing() {
        val before = roomFire()
        glyphFire()
        crossingFire()
        val after = roomFire()
        assertArrayEquals(before, after)
    }

    @Test
    fun theGlyphIsUnaffectedByARoomFireBeforeIt() {
        val alone = glyphFire()
        roomFire()
        val afterARoomFire = glyphFire()
        assertFalse("the glyph drew nothing at all", alone.all { it == 0 })
        assertArrayEquals(alone, afterARoomFire)
    }

    /**
     * The cross-fade goes through `Canvas.saveLayer` with a kept paint whose
     * only varying property is its alpha. A paint that picked up anything else
     * on a previous pass would show here.
     */
    @Test
    fun aCrossFadeIsUnaffectedByWhatPrecededIt() {
        val alone = crossingFire()
        roomFire()
        glyphFire()
        val afterOthers = crossingFire()
        assertFalse("the cross-fade drew nothing at all", alone.all { it == 0 })
        assertArrayEquals(alone, afterOthers)
    }

    /** Renders [block] into a fresh bitmap and reads it back as ARGB. */
    private fun pixels(
        width: Int,
        height: Int,
        block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): IntArray {
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
