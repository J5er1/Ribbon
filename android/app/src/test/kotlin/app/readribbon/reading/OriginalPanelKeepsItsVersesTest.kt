package app.readribbon.reading

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.data.OriginalStore
import app.readribbon.data.ScriptureStore
import app.readribbon.design.Appearance
import app.readribbon.design.RibbonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A word's detail belongs to its verse, not to a place in the panel (A60).
 * With a word of John 1:5 open, the start handle is dragged up into verse 4:
 * the panel gains a verse above, and the open detail stays under verse 5 —
 * it does not settle away under verse 4 while a second copy appears below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class OriginalPanelKeepsItsVersesTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scripture = ScriptureStore(context)
    private val original = OriginalStore(context, scripture)
    private val john = scripture.chapter(VerseAddress("JHN", 1, 1), TranslationID.bsb)!!

    @Test fun anOpenWordStaysWithItsVerseAsTheSelectionGrowsUpward() {
        val five = originalReadingOf(original, scripture, VerseRange("JHN", 1, 5, 5), TranslationID.bsb, john)!!
        val fourAndFive = originalReadingOf(original, scripture, VerseRange("JHN", 1, 4, 5), TranslationID.bsb, john)!!
        val word = five.verses.single().columns[1]
        val reading = mutableStateOf(five)
        val appearance = Appearance(context).apply { wallpaperColour = false }
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                OriginalPanel(
                    reading = reading.value,
                    versionName = "Berean Standard",
                    lexicon = null,
                    parsings = null,
                    room = null,
                    initiallyOpen = 5 to word.index,
                )
            }
        }
        compose.waitForIdle()
        // The detail's first line: the word and how to say it, each isolated.
        val detail = hasText("\u2068${word.word.translit}\u2069", substring = true)
        assertEquals(1, compose.onAllNodes(detail, useUnmergedTree = true).fetchSemanticsNodes().size)

        compose.mainClock.autoAdvance = false
        reading.value = fourAndFive
        Snapshot.sendApplyNotifications()
        // One frame to see the change, one to start moving.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)

        // Partway through what would be the settle: still one detail, and
        // under verse 5's heading.
        val details = compose.onAllNodes(detail, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, details.size)
        val heading = compose.onAllNodes(hasText("John 1:5", substring = true, ignoreCase = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
        // Where it is set, scrolled out of view or not.
        assertTrue(details.single().positionInRoot.y > heading.positionInRoot.y)
        compose.mainClock.autoAdvance = true
    }
}
