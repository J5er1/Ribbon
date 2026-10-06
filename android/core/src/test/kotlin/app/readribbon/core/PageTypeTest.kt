package app.readribbon.core

import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

// The page, the way you read it (A68): the numbers each choice sets.
// A port of core/Tests/RibbonCoreTests/PageTypeTests.swift, case for case.
class PageTypeTest {
    // Nothing set is today's page: S02's leading, the weight it has always
    // been drawn at, the quiet numbers, and the size the slider starts at.
    @Test
    fun testTheBookStopsAreTodaysPage() {
        assertEquals(1, PageType.defaultLineSpacingStep)
        assertEquals(1, PageType.defaultWeightStep)
        assertEquals(1.72, PageType.lineHeightMultiple(PageType.defaultLineSpacingStep))
        assertEquals(400, PageType.weight(PageType.defaultWeightStep, boldText = false))
        assertEquals(0.45, PageType.verseNumberAlpha(clear = false))
        assertEquals(19.0, PageType.defaultSize)
        assertEquals(listOf(1.55, 1.72, 1.9), PageType.lineHeightMultiples)
        assertEquals(listOf(350, 400, 470), PageType.weights)
    }

    // A step from a build with more stops, or a damaged file, still sets a
    // page: it is held at the nearer end.
    @Test
    fun testStepsOutOfRangeClamp() {
        assertEquals(PageType.lineHeightMultiple(0), PageType.lineHeightMultiple(-3))
        assertEquals(PageType.lineHeightMultiple(2), PageType.lineHeightMultiple(9))
        assertEquals(1.55, PageType.lineHeightMultiple(-3))
        assertEquals(1.9, PageType.lineHeightMultiple(9))
        assertEquals(PageType.weight(0, boldText = false), PageType.weight(-3, boldText = false))
        assertEquals(PageType.weight(2, boldText = false), PageType.weight(9, boldText = false))
        assertEquals(350, PageType.weight(-3, boldText = false))
        assertEquals(620, PageType.weight(9, boldText = true))
    }

    @Test
    fun testBoldTextAddsWeightAtEveryStop() {
        assertEquals(150, PageType.boldTextWeight)
        assertEquals(listOf(500, 550, 620), (0..2).map { PageType.weight(it, boldText = true) })
        for (step in 0..2) {
            assertEquals(
                PageType.boldTextWeight,
                PageType.weight(step, boldText = true) - PageType.weight(step, boldText = false),
            )
        }
    }

    // Medium (500) is how the original panel says a word differs (A62).
    // The heaviest stop a reader chooses stays below it.
    @Test
    fun testHeavierIsNeverTheDifferingWordsMedium() {
        assertTrue(PageType.weights[2] < 500)
        assertEquals(PageType.weights[2], PageType.weights.max())
        assertEquals(PageType.weights.sorted(), PageType.weights)
    }

    @Test
    fun testClearNumbersAreBrighterThanQuiet() {
        assertEquals(0.70, PageType.verseNumberAlpha(clear = true))
        assertTrue(PageType.clearVerseNumberAlpha > PageType.quietVerseNumberAlpha)
        assertTrue(PageType.clearVerseNumberAlpha < 1.0, "a number is never the verse's own ink")
    }

    @Test
    fun testTheSizeReachesTwentyEight() {
        assertEquals(16.0, PageType.sizeMin)
        assertEquals(28.0, PageType.sizeMax)
        assertEquals(PageType.sizeMin..PageType.sizeMax, PageType.sizeRange)
        assertTrue(PageType.defaultSize in PageType.sizeRange)
        // The slider lands on both ends and on the default, step by step.
        val steps = (PageType.sizeMax - PageType.sizeMin) / PageType.sizeStep
        assertEquals(steps.roundToInt().toDouble(), steps)
        val fromBottom = (PageType.defaultSize - PageType.sizeMin) / PageType.sizeStep
        assertEquals(fromBottom.roundToInt().toDouble(), fromBottom)
    }
}
