package app.readribbon.reading

import android.widget.Magnifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.Copy
import app.readribbon.core.BlockStyle
import app.readribbon.core.ScriptureBlock
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.ScriptureSpan
import app.readribbon.core.VerseRange
import app.readribbon.design.Appearance
import app.readribbon.design.RibbonTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * A page that selects the way every other page on the phone does (A62,
 * §13.2): the platform's long-press, handles and magnifier, read back as
 * verses and words — and Ribbon's own taps, verbs and screen-reader ends
 * around it.
 *
 * Three verses of John 1, each its own paragraph so a verse's number sits at
 * the start of its line, and each verse's own text ending in the space before
 * the next — which is how the bundled text is, and where a mark reaching a
 * verse's last word used to be stored as a part of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", shadows = [ChapterSelectionTest.ShadowMagnifier::class])
class ChapterSelectionTest {

    /**
     * `android.widget.Magnifier` needs a real Surface, which Robolectric does
     * not have; this stands in for it and counts what the platform was asked
     * to show.
     */
    @Implements(Magnifier::class)
    class ShadowMagnifier {
        @Suppress("FunctionName", "UNUSED_PARAMETER")
        @Implementation protected fun __constructor__(builder: Magnifier.Builder) = Unit
        @Implementation protected fun show(x: Float, y: Float) { shown++ }
        @Implementation protected fun show(x: Float, y: Float, mx: Float, my: Float) { shown++ }
        @Implementation protected fun dismiss() = Unit
        @Implementation protected fun update() = Unit
        @Implementation protected fun getWidth(): Int = 100
        @Implementation protected fun getHeight(): Int = 48

        companion object {
            @JvmStatic var shown = 0
        }
    }

    @get:Rule val compose = createComposeRule()

    private val one = "In the beginning was the Word, and the Word was with God, and the Word was God. "
    private val two = "He was with God in the beginning. "
    private val three = "Through him all things were made."

    private val chapter = ScriptureChapter(
        n = 1,
        blocks = listOf(
            ScriptureBlock(s = BlockStyle.m, x = listOf(ScriptureSpan(v = 1, t = one))),
            ScriptureBlock(s = BlockStyle.m, x = listOf(ScriptureSpan(v = 2, t = two))),
            ScriptureBlock(s = BlockStyle.m, x = listOf(ScriptureSpan(v = 3, t = three))),
        ),
    )

    private class Page {
        val selection = PageSelection()
        val reports = mutableListOf<PageRange?>()
        val taps = mutableListOf<Int>()
        val numberTaps = mutableListOf<Int>()
        val wholeVerses = mutableListOf<Int>()
        val lifted = mutableStateOf<VerseRange?>(null)
        val held = mutableStateOf<VerseRange?>(null)
        val last: PageRange? get() = reports.lastOrNull()
    }

    @Composable
    private fun PageOf(page: Page) {
        ChapterText(
            chapter = chapter,
            runningHead = "John 1",
            theme = ReadingTheme(fontSize = 19f, lineHeightMultiple = 1.62f, redLetter = false),
            marks = emptyList(),
            selection = page.selection,
            lifted = page.lifted.value,
            held = page.held.value,
            justMarked = null,
            onMarkDrawn = {},
            openNote = null,
            isFirstChapter = true,
            showMarginHint = false,
            onLayout = {},
            onSelected = { page.reports += it },
            onTapVerse = { page.taps += it },
            onNoteSlot = {},
            onSelectVerse = { page.wholeVerses += it },
            onTapVerseNumber = { page.numberTaps += it },
            modifier = Modifier.testTag("page"),
        )
    }

    private fun setPage(around: @Composable (@Composable () -> Unit) -> Unit = { it() }): Page {
        val page = Page()
        val appearance = Appearance(ApplicationProvider.getApplicationContext()).apply {
            wallpaperColour = false
        }
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize()) { around { PageOf(page) } }
            }
        }
        compose.waitForIdle()
        return page
    }

    private fun verse(n: Int, text: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(Copy.verseSpoken(n, text.trim())))

    private fun bounds(n: Int, text: String): Rect = verse(n, text).fetchSemanticsNode().boundsInRoot

    /** A point in the page's own coordinates, from one in the root's. */
    private fun inPage(point: Offset): Offset {
        val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        return point - page.topLeft
    }

    private fun px(dp: Float): Float = dp * 2f // xhdpi

    // ── The platform's gesture, read back ──────────────────────────────

    @Test fun aLongPressSelectsAWordAndThatWordIsTheHeldWord() {
        val page = setPage()
        val v1 = bounds(1, one)
        // The first word of verse one, "In".
        val at = inPage(Offset(v1.left + px(4f), v1.top + px(14f)))
        compose.onNodeWithTag("page").performTouchInput { longClick(at) }
        compose.waitForIdle()

        val selected = page.last
        assertNotNull("a long-press selected something", selected)
        // Its start is the verse's own edge, so it is stored as the verse's
        // start; its end is part-way, after "In".
        assertEquals(PageRange(1, null, 1, 2, HeldWord(1, 0)), selected)
        assertTrue(page.selection.isSelecting)
        assertTrue("a long-press is not also a tap", page.taps.isEmpty())
    }

    @Test fun aLongPressAndDragExtendsByWordsAndShowsThePlatformMagnifier() {
        val page = setPage()
        val v1 = bounds(1, one)
        val v2 = bounds(2, two)
        val from = inPage(Offset(v1.left + px(4f), v1.top + px(14f)))
        // Into the middle of verse two's first line.
        val to = inPage(Offset(v2.left + px(90f), v2.top + px(14f)))
        val before = ShadowMagnifier.shown
        compose.onNodeWithTag("page").performTouchInput {
            down(from)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            repeat(12) { i ->
                advanceEventTime(16)
                moveTo(from + (to - from) * ((i + 1) / 12f))
            }
            up()
        }
        compose.waitForIdle()

        val selected = page.last!!
        assertEquals(1, selected.startVerse)
        assertNull("from verse one's first word", selected.startChar)
        assertEquals(2, selected.endVerse)
        val endChar = selected.endChar!!
        // Snapped to the end of a word, never mid-word.
        assertTrue(endChar in 1 until two.trimEnd().length)
        assertTrue(two[endChar].isWhitespace())
        assertNull("several words hold no word", selected.word)
        assertTrue("the platform magnifier was shown", ShadowMagnifier.shown > before)
    }

    @Test fun aSelectionToAVersesLastWordIsTheWholeVerse() {
        val page = setPage()
        // Verse two's own text ends in a space. Its last word, "beginning.",
        // is its edge.
        val last = two.trimEnd().lastIndexOf(' ') + 1
        compose.runOnIdle {
            page.selection.select(VerseRange("JHN", 1, 2, 2, startChar = last, endChar = two.trimEnd().length))
        }
        compose.waitForIdle()
        assertEquals(PageRange(2, last, 2, null, HeldWord(2, last)), page.last)

        compose.runOnIdle { page.selection.select(VerseRange("JHN", 1, 1, 2, startChar = 3)) }
        compose.waitForIdle()
        assertEquals(PageRange(1, 3, 2, null), page.last)
    }

    @Test fun selectingWholeVersesStoresWholeVerses() {
        val page = setPage()
        compose.runOnIdle { page.selection.select(VerseRange("JHN", 1, 2, 3)) }
        compose.waitForIdle()
        assertEquals(PageRange(2, null, 3, null), page.last)

        compose.runOnIdle { page.selection.clear() }
        compose.waitForIdle()
        assertNull("letting go reports nothing selected", page.last)
        assertFalse(page.selection.isSelecting)
    }

    @Test fun aSelectionAskedForBeforeThePageIsLaidOutLandsWhenItIs() {
        val page = Page()
        page.selection.select(VerseRange("JHN", 1, 3, 3))
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        compose.setContent { RibbonTheme(appearance = appearance) { PageOf(page) } }
        compose.waitForIdle()
        assertEquals(PageRange(3, null, 3, null), page.last)
    }

    // ── Ribbon's own taps ──────────────────────────────────────────────

    @Test fun aTapWhileSomethingIsSelectedOnlyLetsGo() {
        val page = setPage()
        compose.runOnIdle { page.selection.select(VerseRange("JHN", 1, 2, 2)) }
        compose.waitForIdle()
        assertTrue(page.selection.isSelecting)

        val v3 = bounds(3, three)
        compose.onNodeWithTag("page").performTouchInput {
            click(inPage(Offset(v3.left + px(60f), v3.top + px(14f))))
        }
        compose.waitForIdle()
        assertFalse(page.selection.isSelecting)
        assertNull(page.last)
        assertTrue("the tap that let go did not also open what is there", page.taps.isEmpty())
    }

    @Test fun aLongPressWhileSomethingIsSelectedSelectsAfresh() {
        val page = setPage()
        compose.runOnIdle { page.selection.select(VerseRange("JHN", 1, 2, 2)) }
        compose.waitForIdle()
        assertEquals(PageRange(2, null, 2, null), page.last)

        // "Through", verse three's first word: the hold is the platform's,
        // and the finger coming up is not a tap that lets go of it.
        val v3 = bounds(3, three)
        compose.onNodeWithTag("page").performTouchInput {
            longClick(inPage(Offset(v3.left + px(30f), v3.top + px(14f))))
        }
        compose.waitForIdle()
        assertTrue("the new word is still selected", page.selection.isSelecting)
        assertEquals(3, page.last?.startVerse)
        assertNotNull("one word, the held word", page.last?.word)
        assertTrue(page.taps.isEmpty())
    }

    @Test fun aTapOnAVerseOpensWhatIsThere() {
        val page = setPage()
        val v3 = bounds(3, three)
        compose.onNodeWithTag("page").performTouchInput {
            click(inPage(Offset(v3.left + px(120f), v3.top + px(14f))))
        }
        compose.waitForIdle()
        assertEquals(listOf(3), page.taps)
        assertTrue(page.numberTaps.isEmpty())
    }

    @Test fun aTapNearAVerseNumberTakesTheVerse() {
        val page = setPage()
        val v2 = bounds(2, two)
        // Beside the small superscript "2", not on it: a thumb's width counts.
        compose.onNodeWithTag("page").performTouchInput {
            click(inPage(Offset(v2.left + px(10f), v2.top + px(16f))))
        }
        compose.waitForIdle()
        assertEquals(listOf(2), page.numberTaps)
        assertTrue(page.taps.isEmpty())
    }

    @Test fun holdingAVerseNumberTakesTheWholeVerse() {
        val page = setPage()
        val v2 = bounds(2, two)
        compose.onNodeWithTag("page").performTouchInput {
            longClick(inPage(Offset(v2.left + px(3f), v2.top + px(10f))))
        }
        compose.waitForIdle()
        assertEquals(PageRange(2, null, 2, null), page.last)
    }

    @Test fun aHandlePassingOverTheGapBetweenVersesKeepsTheWords() {
        val page = setPage()
        compose.runOnIdle { page.selection.select(VerseRange("JHN", 1, 1, 2)) }
        compose.waitForIdle()
        val before = page.last
        assertEquals(PageRange(1, null, 2, null), before)

        // A handle drawn back over "God. ²" leaves only the blank and the
        // number between the two verses selected, for a moment. That is not
        // a reason to let go of the selection under the finger, nor to jump
        // it to a whole verse: the words it had stand until it reaches words.
        val selected = page.selection.state.selectedTexts.joinToString("") { it.text }
        val from = page.selection.toPage!!(VerseRange("JHN", 1, 1, 2))!!.start
        val start = from + selected.indexOf("God.") + "God.".length
        compose.runOnIdle { page.selection.state.select(TextRange(start, start + 3)) }
        compose.waitForIdle()
        assertTrue("still selecting", page.selection.isSelecting)
        assertEquals("the words it had stand", before, page.last)
    }

    @Test fun holdingTheRunningHeadSelectsNothing() {
        val page = setPage()
        val head = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("page").performTouchInput {
            longClick(Offset(px(44f), px(10f)))
        }
        compose.waitForIdle()
        assertTrue(head.height > 0f)
        assertFalse("the head is not a verse to mark", page.selection.isSelecting)
        assertTrue(page.reports.all { it == null })
    }

    // ── No glass over a verse ──────────────────────────────────────────

    @Test fun thePlatformsFloatingToolbarIsNeverAskedFor() {
        var menus = 0
        var legacy = 0
        val outerMenu = object : TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                menus++
                awaitCancellation()
            }
        }
        val outerToolbar = object : TextToolbar {
            override val status = TextToolbarStatus.Hidden
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                legacy++
            }
            override fun hide() = Unit
        }
        val page = setPage { content ->
            CompositionLocalProvider(
                LocalTextContextMenuToolbarProvider provides outerMenu,
                LocalTextToolbar provides outerToolbar,
            ) { content() }
        }
        val v1 = bounds(1, one)
        compose.onNodeWithTag("page").performTouchInput {
            longClick(inPage(Offset(v1.left + px(60f), v1.top + px(14f))))
        }
        compose.waitForIdle()
        assertTrue(page.selection.isSelecting)
        assertEquals("the page's own menu stands in for the platform's", 0, menus)
        assertEquals(0, legacy)
    }

    // ── Composing, and the screen reader ───────────────────────────────

    @Test fun theWordsAComposerIsAboutAreDrawnHeld() {
        val page = setPage()
        val plain = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        compose.runOnIdle { page.held.value = VerseRange("JHN", 1, 2, 2) }
        compose.waitForIdle()
        val held = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        assertFalse("nothing natively selected while held", page.selection.isSelecting)

        val v2 = bounds(2, two)
        val origin = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.topLeft
        // A point in verse two's line, between letters: the ground before,
        // the held tint after.
        var differs = 0
        val y = (v2.top - origin.y + px(6f)).toInt()
        for (x in (v2.left - origin.x).toInt() until (v2.right - origin.x).toInt() step 4) {
            if (plain.getPixel(x, y) != held.getPixel(x, y)) differs++
        }
        assertTrue("verse two is tinted while held ($differs)", differs > 10)
    }

    @Test fun theScreenReadersEndsStepTheSelection() {
        val page = setPage()
        val range = VerseRange("JHN", 1, 2, 2)
        compose.runOnIdle {
            page.selection.select(range)
            page.lifted.value = range
        }
        compose.waitForIdle()

        val end = compose.onNode(hasContentDescription(Copy.WHERE_THE_MARK_ENDS))
        val back = end.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == Copy.A_WORD_BACK }
        compose.runOnIdle { back.action() }
        compose.waitForIdle()
        // "beginning." is let go of; the mark now ends after "the".
        val afterThe = two.indexOf("the beginning") + "the".length
        assertEquals(PageRange(2, null, 2, afterThe), page.last)

        compose.runOnIdle { page.lifted.value = range.copy(endChar = afterThe) }
        compose.waitForIdle()
        val start = compose.onNode(hasContentDescription(Copy.WHERE_THE_MARK_STARTS))
        val verseBack = start.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == Copy.A_VERSE_BACK }
        compose.runOnIdle { verseBack.action() }
        compose.waitForIdle()
        assertEquals(PageRange(1, null, 2, afterThe), page.last)
    }

    @Test fun leaveSomethingHereAsksForTheWholeVerse() {
        val page = setPage()
        val leave = verse(2, two).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == Copy.LEAVE_SOMETHING_HERE }
        compose.runOnIdle { leave.action() }
        assertEquals(listOf(2), page.wholeVerses)
    }
}

/**
 * A page selection already holding [range], for pictures and tests: selected
 * as soon as the page is laid out.
 */
@Composable
fun rememberSelected(range: VerseRange?): PageSelection {
    val selection = remember { PageSelection() }
    LaunchedEffect(range) {
        if (range != null) selection.select(range) else selection.clear()
    }
    return selection
}
