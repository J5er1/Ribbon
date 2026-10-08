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

    // The sliders (A69). They run through the stops a reader could choose
    // before them, and start where the page already was.
    @Test
    fun testTheOldStopsAreOnTheNewScales() {
        val scales = listOf(
            PageType.lineHeightScale, PageType.weightScale, PageType.letterSpacingScale, PageType.marginScale,
        )
        for (scale in scales) {
            assertEquals(13, scale.count)
            assertEquals(scale.values.sorted(), scale.values)
            assertEquals(scale.count, scale.values.toSet().size, "no two places are the same value")
        }
        assertEquals(PageType.lineHeightMultiples.map { (it * 100).roundToInt() }, PageType.lineSpacingNamed)
        for (stop in PageType.lineSpacingNamed) {
            assertTrue(stop in PageType.lineHeightScale.values)
        }
        for (stop in PageType.weights) {
            assertTrue(stop in PageType.weightScale.values)
        }
        assertEquals(PageType.lineSpacingNamed[PageType.defaultLineSpacingStep], PageType.lineHeightScale.book)
        assertEquals(PageType.weights[PageType.defaultWeightStep], PageType.weightScale.book)
        assertEquals(0, PageType.letterSpacingScale.book)
        assertEquals(0, PageType.marginScale.book)
        assertEquals(1.72, PageType.lineHeightMultipleOf(hundredths = 172))
    }

    // A value from a build with wider ends, or a damaged file, still sets a
    // page: it is held at the nearer end, however wild the number.
    @Test
    fun testAValuePastEitherEndIsHeldThere() {
        assertEquals(145, PageType.lineHeightScale.held(100))
        assertEquals(210, PageType.lineHeightScale.held(300))
        assertEquals(350, PageType.weightScale.held(0))
        assertEquals(470, PageType.weightScale.held(999))
        assertEquals(0, PageType.letterSpacingScale.held(-5))
        assertEquals(60, PageType.letterSpacingScale.held(99))
        assertEquals(0, PageType.marginScale.held(-1))
        assertEquals(48, PageType.marginScale.held(1000))
        assertEquals(145, PageType.lineHeightScale.held(Int.MIN_VALUE))
        assertEquals(210, PageType.lineHeightScale.held(Int.MAX_VALUE))
    }

    // Between two places a value lands on the nearer. Exactly halfway, it
    // lands on the side Book is on, so a value drifts toward today's page
    // and never away from it.
    @Test
    fun testAValueBetweenStopsLandsOnTheNearestAndATieGoesTowardBook() {
        assertEquals(165, PageType.lineHeightScale.held(168))
        assertEquals(172, PageType.lineHeightScale.held(169))
        assertEquals(172, PageType.lineHeightScale.held(175), "a tie, and Book is one of the two")
        assertEquals(145, PageType.lineHeightScale.held(147))
        assertEquals(178, PageType.lineHeightScale.held(181), "a tie between 178 and 184: Book is below")
        assertEquals(400, PageType.weightScale.held(405), "a tie, and Book is one of the two")
        assertEquals(460, PageType.weightScale.held(465), "a tie, and Book is below")
        assertEquals(400, PageType.weightScale.held(395), "a tie, and Book is one of the two")
        assertEquals(360, PageType.weightScale.held(355), "a tie, and Book is above")
        assertEquals(350, PageType.weightScale.held(354))
        assertEquals(360, PageType.weightScale.held(356))
        assertEquals(0, PageType.letterSpacingScale.held(2))
        assertEquals(5, PageType.letterSpacingScale.held(3))
    }

    // Every place on a track is a place a finger can put the thumb and find
    // it again.
    @Test
    fun testEveryPositionComesBackToItself() {
        val scales = listOf(
            PageType.lineHeightScale, PageType.weightScale, PageType.letterSpacingScale, PageType.marginScale,
        )
        for (scale in scales) {
            for (i in 0 until scale.count) {
                assertEquals(i, scale.index(of = scale.value(at = i)))
                assertEquals(i, scale.index(at = scale.fraction(of = scale.value(at = i))))
            }
            assertEquals(0, scale.index(at = Double.NaN))
            assertEquals(0, scale.index(at = -1.0))
            assertEquals(scale.count - 1, scale.index(at = 2.0))
        }
    }

    // What a screen reader is told: the nearest named stop, and how many
    // places from it.
    @Test
    fun testTheNearestNamedStopNamesTheValue() {
        val spacing = PageType.lineSpacingNamed
        assertEquals(NamedReading(index = 1, offset = 0), PageType.lineHeightScale.nearestNamed(172, named = spacing))
        assertEquals(NamedReading(index = 2, offset = -1), PageType.lineHeightScale.nearestNamed(184, named = spacing))
        assertEquals(NamedReading(index = 0, offset = 1), PageType.lineHeightScale.nearestNamed(160, named = spacing))
        assertEquals(NamedReading(index = 1, offset = 1), PageType.lineHeightScale.nearestNamed(178, named = spacing))
        val weights = PageType.weights
        assertEquals(NamedReading(index = 1, offset = 2), PageType.weightScale.nearestNamed(420, named = weights))
        assertEquals(NamedReading(index = 2, offset = -3), PageType.weightScale.nearestNamed(440, named = weights))
        assertEquals(NamedReading(index = 0, offset = 0), PageType.weightScale.nearestNamed(350, named = weights))
    }

    // A file from before the sliders opens on the page it was set to: the
    // old steps read through the same tables as ever.
    @Test
    fun testOldStepsKeepTheirPage() {
        assertEquals(listOf(155, 172, 190), (0..2).map { PageType.lineHeightHundredths(saved = null, legacyStep = it) })
        assertEquals(listOf(350, 400, 470), (0..2).map { PageType.weight(saved = null, legacyStep = it) })
        assertEquals(155, PageType.lineHeightHundredths(saved = null, legacyStep = -3))
        assertEquals(190, PageType.lineHeightHundredths(saved = null, legacyStep = 9))
        assertEquals(350, PageType.weight(saved = null, legacyStep = -3))
        assertEquals(470, PageType.weight(saved = null, legacyStep = 9))
        assertEquals(
            1.9,
            PageType.lineHeightMultipleOf(hundredths = PageType.lineHeightHundredths(saved = null, legacyStep = 2)),
        )
    }

    // Once a slider has been moved its value is the page, whatever step is
    // beside it, and it is held to the scale like any other.
    @Test
    fun testASavedValueWinsAndIsHeld() {
        assertEquals(160, PageType.lineHeightHundredths(saved = 160, legacyStep = 2))
        assertEquals(178, PageType.lineHeightHundredths(saved = 180, legacyStep = 1))
        assertEquals(210, PageType.lineHeightHundredths(saved = 999, legacyStep = 0))
        assertEquals(440, PageType.weight(saved = 440, legacyStep = 0))
        assertEquals(410, PageType.weight(saved = 415, legacyStep = 1), "a tie, and Book is below")
    }

    // The old step written beside a new value is the nearest stop, so an
    // older build opens on nearly the same page. Exactly halfway, Book.
    @Test
    fun testTheNearestOldStepIsWrittenBeside() {
        assertEquals(0, PageType.lineSpacingStep(forHundredths = 155))
        assertEquals(0, PageType.lineSpacingStep(forHundredths = 163))
        assertEquals(1, PageType.lineSpacingStep(forHundredths = 164))
        assertEquals(1, PageType.lineSpacingStep(forHundredths = 172))
        assertEquals(1, PageType.lineSpacingStep(forHundredths = 181))
        assertEquals(2, PageType.lineSpacingStep(forHundredths = 182))
        assertEquals(2, PageType.lineSpacingStep(forHundredths = 190))
        assertEquals(0, PageType.weightStep(forWeight = 350))
        assertEquals(0, PageType.weightStep(forWeight = 374))
        assertEquals(1, PageType.weightStep(forWeight = 375))
        assertEquals(1, PageType.weightStep(forWeight = 400))
        assertEquals(1, PageType.weightStep(forWeight = 435))
        assertEquals(2, PageType.weightStep(forWeight = 436))
        assertEquals(2, PageType.weightStep(forWeight = 470))
    }

    // Medium (500) is how the original panel says a word differs (A62).
    // The heaviest place on the slider stays below it, as Heavier did.
    @Test
    fun testHeavierIsStillNeverTheDifferingWordsMedium() {
        assertTrue(PageType.weightScale.values.last() < 500)
        assertEquals(PageType.weightScale.values.last(), PageType.weightScale.values.max())
        assertEquals(PageType.weights.last(), PageType.weightScale.values.last())
    }

    // The size keeps its key and its halves; the slider counts them as
    // places, and every place comes back to its size.
    @Test
    fun testTheSizeHasTwentyFivePositions() {
        assertEquals(25, PageType.sizePositions)
        assertEquals(0, PageType.sizeIndex(of = 16.0))
        assertEquals(6, PageType.sizeIndex(of = 19.0))
        assertEquals(24, PageType.sizeIndex(of = 28.0))
        assertEquals(24, PageType.sizeIndex(of = 40.0))
        assertEquals(0, PageType.sizeIndex(of = 10.0))
        assertEquals(19.0, PageType.size(at = 6))
        assertEquals(28.0, PageType.size(at = 99))
        assertEquals(16.0, PageType.size(at = -1))
        for (i in 0 until PageType.sizePositions) {
            assertEquals(i, PageType.sizeIndex(of = PageType.size(at = i)))
        }
    }

    // The faces (A69). Set nothing and the page is in Literata, at the size,
    // weight and leading it has always had.
    @Test
    fun testLiterataIsTheFirstFaceAndTodaysPage() {
        assertEquals(PageFaces.literata, PageFaces.all.first())
        assertEquals(19.0, PageType.pointSize(19.0, face = PageFaces.literata))
        for (weight in listOf(350, 400, 470, 620)) {
            assertEquals(weight, PageType.faceWeight(weight, face = PageFaces.literata))
        }
        assertEquals(1.72, PageType.naturalLineMultiple(1.72, face = PageFaces.literata))
    }

    // A file naming a face this build does not have, or none, is still set.
    @Test
    fun testAnUnknownFaceIsLiterata() {
        assertEquals(PageFaces.literata, PageFaces.face(id = null))
        assertEquals(PageFaces.literata, PageFaces.face(id = ""))
        assertEquals(PageFaces.literata, PageFaces.face(id = "comicSans"))
        for (face in PageFaces.all) {
            assertEquals(face, PageFaces.face(id = face.id))
        }
    }

    // The ids are what every saved file holds, and the stems are the files'
    // names: spelled out, so that a rename has to be meant.
    @Test
    fun testFaceIDsAreStable() {
        assertEquals(listOf("literata", "sourceSerif", "ebGaramond", "alegreya", "atkinson"), PageFaces.all.map { it.id })
        assertEquals(
            listOf("Literata", "Source Serif", "EB Garamond", "Alegreya", "Atkinson Hyperlegible"),
            PageFaces.all.map { it.name },
        )
        assertEquals(
            listOf("Literata", "SourceSerif4", "EBGaramond", "Alegreya", "AtkinsonHyperlegibleNext"),
            PageFaces.all.map { it.fileStem },
        )
    }

    // Every weight the page asks for, Bold Text included, is one the face
    // can draw, in order: Lighter is lighter and Heavier heavier in every
    // face, even one whose axis begins at Book.
    @Test
    fun testEveryFaceDrawsTheWholeWeightRangeInOrder() {
        for (face in PageFaces.all) {
            val drawn = listOf(350, 400, 470, 500, 550, 620).map { PageType.faceWeight(it, face = face) }
            for (weight in drawn) {
                assertTrue(weight in face.weightRange, "${face.id} draws $weight")
            }
            assertEquals(drawn.sorted(), drawn, face.id)
            assertTrue(PageType.faceWeight(350, face = face) < PageType.faceWeight(400, face = face), face.id)
            assertTrue(PageType.faceWeight(470, face = face) > PageType.faceWeight(400, face = face), face.id)
        }
    }

    // On the iPhone a face's lines fall as far apart as Literata's at every
    // place on the slider, though each face's own line is its own height.
    @Test
    fun testAFaceKeepsTheLinePitch() {
        for (face in PageFaces.all) {
            for (m in PageType.lineHeightScale.values) {
                val multiple = m.toDouble() / 100
                val pitch = 19 * face.sizeMatch * face.naturalLine * PageType.naturalLineMultiple(multiple, face = face)
                assertEquals(19 * 1.485 * multiple, pitch, 1e-9, "${face.id} at $m")
            }
        }
    }

    // The iPhone's page window adds to a face's natural line exactly what
    // brings it to the page's.
    @Test
    fun testTheWindowsLeadingIsThePages() {
        for (face in PageFaces.all) {
            val natural = 19 * face.sizeMatch * face.naturalLine
            assertEquals(
                19 * 1.485 * 1.72, natural + PageType.extraLeading(size = 19.0, multiple = 1.72, face = face),
                1e-9, face.id,
            )
        }
    }

    // A margin is room either side of the words, until the words would be
    // narrower than thirteen ems; then the margin gives way, to nothing at
    // the largest sizes. 331 is the words' width on a 393-point phone.
    @Test
    fun testTheMarginsGiveWayToTheWords() {
        assertEquals(40.0, PageType.margin(requested = 40.0, textWidth = 331.0, size = 19.0))
        assertEquals(42.0, PageType.margin(requested = 48.0, textWidth = 331.0, size = 19.0))
        assertEquals((331.0 - 312.0) / 2, PageType.margin(requested = 48.0, textWidth = 331.0, size = 24.0))
        assertEquals(9.5, PageType.margin(requested = 48.0, textWidth = 331.0, size = 24.0))
        assertEquals(0.0, PageType.margin(requested = 48.0, textWidth = 331.0, size = 28.0))
        assertEquals(0.0, PageType.margin(requested = 0.0, textWidth = 331.0, size = 19.0))
        for (size in (0..24).map { 16.0 + 0.5 * it }) {
            for (requested in PageType.marginScale.values) {
                val margin = PageType.margin(requested = requested.toDouble(), textWidth = 331.0, size = size)
                assertTrue(margin >= 0.0)
                assertTrue(margin <= requested.toDouble())
            }
        }
    }

    // Letter spacing is kept in thousandths and given in ems, held to the
    // scale.
    @Test
    fun testLetterSpacingIsHeldInThousandths() {
        assertEquals(0.025, PageType.letterSpacingEm(25))
        assertEquals(0.06, PageType.letterSpacingEm(61))
        assertEquals(0.0, PageType.letterSpacingEm(-4))
    }
}
