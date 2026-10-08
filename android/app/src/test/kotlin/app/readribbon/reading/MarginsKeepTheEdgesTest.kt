package app.readribbon.reading

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page's margins (A69), and the owner's condition on them: "careful with
 * margin so that it doesnt effect the presence feature". The margin sits
 * outside the gutter and beside the trailing edge; the presence panel takes
 * its room from the column exactly as before, and the margin is worked out
 * from what is left — so the panel opening costs the margin first, never the
 * words — and at the largest sizes the words keep it all.
 */
class MarginsKeepTheEdgesTest {

    private fun theme(margin: Float, size: Float = 19f) =
        ReadingTheme(fontSize = size, lineHeightMultiple = 1.72f, redLetter = false, marginRequested = margin)

    @Test fun aPhoneAtBookGivesWhatTheWordsCanSpare() {
        assertEquals("all of a modest one", 24.dp, theme(24f).marginIn(393.dp, fontScale = 1f))
        // 393 less the gutter, its 8 and the trailing 26 is 331; 13 ems at 19
        // is 247; so 42 either side, and not the 48 asked for.
        assertEquals(42.dp, theme(48f).marginIn(393.dp, fontScale = 1f))
        assertEquals("none asked, none given", 0.dp, theme(0f).marginIn(393.dp, fontScale = 1f))
    }

    @Test fun thePresencePanelTakesTheMarginFirst() {
        // The panel asks for its width beside the text; the column it leaves
        // is narrower, and the margin gives way so the words keep 13 ems.
        val open = theme(48f).marginIn(393.dp - 120.dp, fontScale = 1f)
        assertEquals(0.dp, open)
        // Partly: what is left over the 13 ems, split either side.
        val textWidth = 393f - 60f - 62f
        assertEquals(((textWidth - 13f * 19f) / 2f).dp, theme(48f).marginIn(393.dp - 60.dp, fontScale = 1f))
    }

    @Test fun atTheLargestSizesTheWordsKeepIt() {
        assertEquals(0.dp, theme(48f, size = 28f).marginIn(393.dp, fontScale = 1f))
        assertEquals("the system's font scale counts", 0.dp, theme(48f).marginIn(393.dp, fontScale = 2f))
    }

    @Test fun theGutterAndTheTrailingMarginAreUntouched() {
        val theme = theme(48f).copy(margin = 48.dp)
        assertEquals("the gutter holds its width (S02)", 28.dp, theme.gutterWidth)
        assertEquals(26.dp, theme.trailingMargin)
    }
}
