package app.readribbon.reading

import androidx.compose.ui.graphics.Color
import app.readribbon.core.Ink
import app.readribbon.design.Brand
import app.readribbon.design.color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * S06's last line: *"Overlap — two inks over the same words blend to a third
 * color. This is desirable and must survive both themes — **check every one of
 * the 28 pairs against the ground before ship**."*
 *
 * A check to be done by hand before every ship is a check that gets done once,
 * and this one had never been done at all. When it finally was, **all 28 pairs
 * failed**: the washes were multiplied, which is how two *pigments* combine on
 * white paper, and Ribbon's page is unlit ground with translucent light laid
 * over it. Every overlap came out dimmer than either ink on its own — crimson
 * over teal at half the luminance of teal alone — so the single most meaningful
 * thing that can happen on the reading surface, two people marking the same
 * verse, drew a hole in the page. See deviations A41b.
 *
 * So the check lives here instead, and runs on every build.
 */
class HighlightWashTest {

    /** The unlit ground a wash is laid over. */
    private val ground = Brand.ground

    /** Ivory: Scripture itself, which has to stay readable over any of it. */
    private val scripture = Brand.text

    @Test fun everyOneOfTheTwentyEightPairsIsBrighterThanEitherInk() {
        val inks = Ink.entries
        var pairs = 0
        for (i in inks.indices) {
            for (j in i + 1 until inks.size) {
                pairs++
                val a = inks[i]
                val b = inks[j]
                val alone = maxOf(luminanceOf(washFor(listOf(a))), luminanceOf(washFor(listOf(b))))
                val together = luminanceOf(washFor(listOf(a, b)))
                assertTrue(
                    "${a.name} over ${b.name} is no brighter than one ink alone " +
                        "($together vs $alone) — §4.5 says an overlap deepens",
                    together > alone,
                )
            }
        }
        assertTrue("S06 names 28 pairs; found $pairs", pairs == 28)
    }

    /**
     * The other half of "survive both themes": a wash is behind Scripture, and
     * eight people on one verse must not cost anybody the words underneath.
     * 4.5:1 is the floor for body text.
     */
    @Test fun scriptureStaysReadableOverAnyDepthOfWash() {
        var stack = emptyList<Ink>()
        for (ink in Ink.entries) {
            stack = stack + ink
            val wash = washFor(stack)
            val over = composite(wash.color, wash.alpha, ground)
            val ratio = contrast(scripture, over)
            assertTrue(
                "Scripture over ${stack.size} inks is only ${"%.2f".format(ratio)}:1",
                ratio >= 4.5f,
            )
        }
    }

    /** An overlap is a *third* colour, not a deeper version of either ink. */
    @Test fun anOverlapIsNeitherOfItsInks() {
        val inks = Ink.entries
        for (i in inks.indices) {
            for (j in i + 1 until inks.size) {
                val together = washFor(listOf(inks[i], inks[j])).color
                assertTrue(
                    "${inks[i].name} over ${inks[j].name} came back as one of them",
                    together != inks[i].color && together != inks[j].color,
                )
            }
        }
    }

    private fun luminanceOf(wash: Wash): Float =
        relativeLuminance(composite(wash.color, wash.alpha, ground))

    private fun composite(over: Color, alpha: Float, under: Color) = Color(
        red = over.red * alpha + under.red * (1f - alpha),
        green = over.green * alpha + under.green * (1f - alpha),
        blue = over.blue * alpha + under.blue * (1f - alpha),
    )

    private fun contrast(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float) =
            if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }
}
