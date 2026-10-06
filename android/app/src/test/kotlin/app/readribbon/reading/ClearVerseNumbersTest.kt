package app.readribbon.reading

import androidx.compose.ui.graphics.Color
import app.readribbon.core.Ink
import app.readribbon.core.PageType
import app.readribbon.data.AppSettings
import app.readribbon.design.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Clearer verse numbers (A68), checked the way the washes are
 * (HighlightWashTest): on every build, against the ground and under the
 * washes a number sits in.
 *
 * S02 sets the numbers at ~45% so they are there without shouting, and at
 * that ink they stand at about 4:1 on the ground, a little under the 4.5:1
 * body text is held to: fine for a number most readers read past, short for
 * one who finds verses by them. The switch is for that reader: past 7:1 on
 * the ground, and still past 4.5:1 under any one person's wash, which is
 * where a marked verse's number sits. Only the ink changes; the numbers do
 * not move.
 */
class ClearVerseNumbersTest {

    /** The unlit ground. */
    private val ground = Brand.ground

    /** Ivory, which the numbers are a share of. */
    private val ivory = Brand.text

    private val quiet = PageType.quietVerseNumberAlpha.toFloat()
    private val clear = PageType.clearVerseNumberAlpha.toFloat()

    @Test fun clearNumbersArePastSevenToOneOnTheGround() {
        val ratio = contrast(composite(ivory, clear, ground), ground)
        assertTrue("clear numbers on the ground are only ${"%.2f".format(ratio)}:1", ratio >= 7f)
    }

    @Test fun clearNumbersStayReadableUnderAnyOnesWash() {
        for (ink in Ink.entries) {
            val wash = washFor(listOf(ink))
            val under = composite(wash.color, wash.alpha, ground)
            val ratio = contrast(composite(ivory, clear, under), under)
            assertTrue(
                "clear numbers under ${ink.name}'s wash are only ${"%.2f".format(ratio)}:1",
                ratio >= 4.5f,
            )
        }
    }

    /**
     * Set nothing and the numbers are the ones the page has always had: the
     * settings, the theme and the core all start at S02's 45%, and the
     * clearer ink is brighter than it, never the same.
     */
    @Test fun quietNumbersStayAsThePageHasAlwaysSetThem() {
        assertEquals(0.45f, quiet, 0f)
        assertEquals(0.45f, ReadingTheme(fontSize = 19f, lineHeightMultiple = 1.72f, redLetter = false).verseNumberAlpha, 0f)
        assertEquals(0.45, AppSettings().verseNumberAlpha, 0.0)
        val quietly = contrast(composite(ivory, quiet, ground), ground)
        val clearly = contrast(composite(ivory, clear, ground), ground)
        assertTrue("quiet numbers stand at about 4:1 (${"%.2f".format(quietly)})", quietly in 3.9f..4.3f)
        assertTrue("and the clearer ink is clearer", clearly > quietly)
    }

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
