package app.readribbon.design

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Figures that stand in a row sit level (A74).
 *
 * Alegreya Sans sets old-style figures unless it is asked for others, and a
 * grid of chapters in them rises and falls: 3, 4, 5, 7 and 9 hang below the
 * line, 6 and 8 stand above it. The chapters are set in the face's lining
 * figures, every one as wide as the next — and nothing else about the
 * interface face changes, so a number there is the size and the face it
 * always was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiguresInARowSitLevelTest {

    @Test fun theFiguresAreLiningAndTabular() {
        assertEquals("lnum, tnum", RibbonType.figures(17f).fontFeatureSettings)
    }

    @Test fun everythingElseIsTheInterfaceFace() {
        val figures = RibbonType.figures(17f)
        assertEquals(RibbonType.ui(17f), figures.copy(fontFeatureSettings = null))
    }
}
