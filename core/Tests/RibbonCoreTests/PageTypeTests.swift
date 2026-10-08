import XCTest
@testable import RibbonCore

// The page, the way you read it (A68): the numbers each choice sets.
// PageTypeTest.kt holds the Kotlin port to the same cases.
final class PageTypeTests: XCTestCase {
    // Nothing set is today's page: S02's leading, the weight it has always
    // been drawn at, the quiet numbers, and the size the slider starts at.
    func testTheBookStopsAreTodaysPage() {
        XCTAssertEqual(PageType.defaultLineSpacingStep, 1)
        XCTAssertEqual(PageType.defaultWeightStep, 1)
        XCTAssertEqual(PageType.lineHeightMultiple(step: PageType.defaultLineSpacingStep), 1.72)
        XCTAssertEqual(PageType.weight(step: PageType.defaultWeightStep, boldText: false), 400)
        XCTAssertEqual(PageType.verseNumberAlpha(clear: false), 0.45)
        XCTAssertEqual(PageType.defaultSize, 19)
        XCTAssertEqual(PageType.lineHeightMultiples, [1.55, 1.72, 1.9])
        XCTAssertEqual(PageType.weights, [350, 400, 470])
    }

    // A step from a build with more stops, or a damaged file, still sets a
    // page: it is held at the nearer end.
    func testStepsOutOfRangeClamp() {
        XCTAssertEqual(PageType.lineHeightMultiple(step: -3), PageType.lineHeightMultiple(step: 0))
        XCTAssertEqual(PageType.lineHeightMultiple(step: 9), PageType.lineHeightMultiple(step: 2))
        XCTAssertEqual(PageType.lineHeightMultiple(step: -3), 1.55)
        XCTAssertEqual(PageType.lineHeightMultiple(step: 9), 1.9)
        XCTAssertEqual(PageType.weight(step: -3, boldText: false), PageType.weight(step: 0, boldText: false))
        XCTAssertEqual(PageType.weight(step: 9, boldText: false), PageType.weight(step: 2, boldText: false))
        XCTAssertEqual(PageType.weight(step: -3, boldText: false), 350)
        XCTAssertEqual(PageType.weight(step: 9, boldText: true), 620)
    }

    func testBoldTextAddsWeightAtEveryStop() {
        XCTAssertEqual(PageType.boldTextWeight, 150)
        XCTAssertEqual((0...2).map { PageType.weight(step: $0, boldText: true) }, [500, 550, 620])
        for step in 0...2 {
            XCTAssertEqual(
                PageType.weight(step: step, boldText: true) - PageType.weight(step: step, boldText: false),
                PageType.boldTextWeight)
        }
    }

    // Medium (500) is how the original panel says a word differs (A62).
    // The heaviest stop a reader chooses stays below it.
    func testHeavierIsNeverTheDifferingWordsMedium() {
        XCTAssertLessThan(PageType.weights[2], 500)
        XCTAssertEqual(PageType.weights.max(), PageType.weights[2])
        XCTAssertEqual(PageType.weights, PageType.weights.sorted())
    }

    func testClearNumbersAreBrighterThanQuiet() {
        XCTAssertEqual(PageType.verseNumberAlpha(clear: true), 0.70)
        XCTAssertGreaterThan(PageType.clearVerseNumberAlpha, PageType.quietVerseNumberAlpha)
        XCTAssertLessThan(PageType.clearVerseNumberAlpha, 1, "a number is never the verse's own ink")
    }

    func testTheSizeReachesTwentyEight() {
        XCTAssertEqual(PageType.sizeRange.lowerBound, 16)
        XCTAssertEqual(PageType.sizeRange.upperBound, 28)
        XCTAssertTrue(PageType.sizeRange.contains(PageType.defaultSize))
        // The slider lands on both ends and on the default, step by step.
        let steps = (PageType.sizeRange.upperBound - PageType.sizeRange.lowerBound) / PageType.sizeStep
        XCTAssertEqual(steps, steps.rounded())
        let fromBottom = (PageType.defaultSize - PageType.sizeRange.lowerBound) / PageType.sizeStep
        XCTAssertEqual(fromBottom, fromBottom.rounded())
    }

    // The sliders (A69). They run through the stops a reader could choose
    // before them, and start where the page already was.
    func testTheOldStopsAreOnTheNewScales() {
        let scales = [
            PageType.lineHeightScale, PageType.weightScale, PageType.letterSpacingScale, PageType.marginScale,
        ]
        for scale in scales {
            XCTAssertEqual(scale.count, 13)
            XCTAssertEqual(scale.values, scale.values.sorted())
            XCTAssertEqual(Set(scale.values).count, scale.count, "no two places are the same value")
        }
        XCTAssertEqual(PageType.lineSpacingNamed, PageType.lineHeightMultiples.map { Int(($0 * 100).rounded()) })
        for stop in PageType.lineSpacingNamed {
            XCTAssertTrue(PageType.lineHeightScale.values.contains(stop))
        }
        for stop in PageType.weights {
            XCTAssertTrue(PageType.weightScale.values.contains(stop))
        }
        XCTAssertEqual(PageType.lineHeightScale.book, PageType.lineSpacingNamed[PageType.defaultLineSpacingStep])
        XCTAssertEqual(PageType.weightScale.book, PageType.weights[PageType.defaultWeightStep])
        XCTAssertEqual(PageType.letterSpacingScale.book, 0)
        XCTAssertEqual(PageType.marginScale.book, 0)
        XCTAssertEqual(PageType.lineHeightMultiple(hundredths: 172), 1.72)
    }

    // A value from a build with wider ends, or a damaged file, still sets a
    // page: it is held at the nearer end, however wild the number.
    func testAValuePastEitherEndIsHeldThere() {
        XCTAssertEqual(PageType.lineHeightScale.held(100), 145)
        XCTAssertEqual(PageType.lineHeightScale.held(300), 210)
        XCTAssertEqual(PageType.weightScale.held(0), 350)
        XCTAssertEqual(PageType.weightScale.held(999), 470)
        XCTAssertEqual(PageType.letterSpacingScale.held(-5), 0)
        XCTAssertEqual(PageType.letterSpacingScale.held(99), 60)
        XCTAssertEqual(PageType.marginScale.held(-1), 0)
        XCTAssertEqual(PageType.marginScale.held(1000), 48)
        XCTAssertEqual(PageType.lineHeightScale.held(.min), 145)
        XCTAssertEqual(PageType.lineHeightScale.held(.max), 210)
    }

    // Between two places a value lands on the nearer. Exactly halfway, it
    // lands on the side Book is on, so a value drifts toward today's page
    // and never away from it.
    func testAValueBetweenStopsLandsOnTheNearestAndATieGoesTowardBook() {
        XCTAssertEqual(PageType.lineHeightScale.held(168), 165)
        XCTAssertEqual(PageType.lineHeightScale.held(169), 172)
        XCTAssertEqual(PageType.lineHeightScale.held(175), 172, "a tie, and Book is one of the two")
        XCTAssertEqual(PageType.lineHeightScale.held(147), 145)
        XCTAssertEqual(PageType.lineHeightScale.held(181), 178, "a tie between 178 and 184: Book is below")
        XCTAssertEqual(PageType.weightScale.held(405), 400, "a tie, and Book is one of the two")
        XCTAssertEqual(PageType.weightScale.held(465), 460, "a tie, and Book is below")
        XCTAssertEqual(PageType.weightScale.held(395), 400, "a tie, and Book is one of the two")
        XCTAssertEqual(PageType.weightScale.held(355), 360, "a tie, and Book is above")
        XCTAssertEqual(PageType.weightScale.held(354), 350)
        XCTAssertEqual(PageType.weightScale.held(356), 360)
        XCTAssertEqual(PageType.letterSpacingScale.held(2), 0)
        XCTAssertEqual(PageType.letterSpacingScale.held(3), 5)
    }

    // Every place on a track is a place a finger can put the thumb and find
    // it again.
    func testEveryPositionComesBackToItself() {
        let scales = [
            PageType.lineHeightScale, PageType.weightScale, PageType.letterSpacingScale, PageType.marginScale,
        ]
        for scale in scales {
            for i in 0..<scale.count {
                XCTAssertEqual(scale.index(of: scale.value(at: i)), i)
                XCTAssertEqual(scale.index(at: scale.fraction(of: scale.value(at: i))), i)
            }
            XCTAssertEqual(scale.index(at: .nan), 0)
            XCTAssertEqual(scale.index(at: -1), 0)
            XCTAssertEqual(scale.index(at: 2), scale.count - 1)
        }
    }

    // What a screen reader is told: the nearest named stop, and how many
    // places from it.
    func testTheNearestNamedStopNamesTheValue() {
        let spacing = PageType.lineSpacingNamed
        XCTAssertEqual(PageType.lineHeightScale.nearestNamed(172, named: spacing), NamedReading(index: 1, offset: 0))
        XCTAssertEqual(PageType.lineHeightScale.nearestNamed(184, named: spacing), NamedReading(index: 2, offset: -1))
        XCTAssertEqual(PageType.lineHeightScale.nearestNamed(160, named: spacing), NamedReading(index: 0, offset: 1))
        XCTAssertEqual(PageType.lineHeightScale.nearestNamed(178, named: spacing), NamedReading(index: 1, offset: 1))
        let weights = PageType.weights
        XCTAssertEqual(PageType.weightScale.nearestNamed(420, named: weights), NamedReading(index: 1, offset: 2))
        XCTAssertEqual(PageType.weightScale.nearestNamed(440, named: weights), NamedReading(index: 2, offset: -3))
        XCTAssertEqual(PageType.weightScale.nearestNamed(350, named: weights), NamedReading(index: 0, offset: 0))
    }

    // A file from before the sliders opens on the page it was set to: the
    // old steps read through the same tables as ever.
    func testOldStepsKeepTheirPage() {
        XCTAssertEqual((0...2).map { PageType.lineHeightHundredths(saved: nil, legacyStep: $0) }, [155, 172, 190])
        XCTAssertEqual((0...2).map { PageType.weight(saved: nil, legacyStep: $0) }, [350, 400, 470])
        XCTAssertEqual(PageType.lineHeightHundredths(saved: nil, legacyStep: -3), 155)
        XCTAssertEqual(PageType.lineHeightHundredths(saved: nil, legacyStep: 9), 190)
        XCTAssertEqual(PageType.weight(saved: nil, legacyStep: -3), 350)
        XCTAssertEqual(PageType.weight(saved: nil, legacyStep: 9), 470)
        XCTAssertEqual(
            PageType.lineHeightMultiple(hundredths: PageType.lineHeightHundredths(saved: nil, legacyStep: 2)), 1.9)
    }

    // Once a slider has been moved its value is the page, whatever step is
    // beside it, and it is held to the scale like any other.
    func testASavedValueWinsAndIsHeld() {
        XCTAssertEqual(PageType.lineHeightHundredths(saved: 160, legacyStep: 2), 160)
        XCTAssertEqual(PageType.lineHeightHundredths(saved: 180, legacyStep: 1), 178)
        XCTAssertEqual(PageType.lineHeightHundredths(saved: 999, legacyStep: 0), 210)
        XCTAssertEqual(PageType.weight(saved: 440, legacyStep: 0), 440)
        XCTAssertEqual(PageType.weight(saved: 415, legacyStep: 1), 410, "a tie, and Book is below")
    }

    // The old step written beside a new value is the nearest stop, so an
    // older build opens on nearly the same page. Exactly halfway, Book.
    func testTheNearestOldStepIsWrittenBeside() {
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 155), 0)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 163), 0)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 164), 1)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 172), 1)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 181), 1)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 182), 2)
        XCTAssertEqual(PageType.lineSpacingStep(forHundredths: 190), 2)
        XCTAssertEqual(PageType.weightStep(forWeight: 350), 0)
        XCTAssertEqual(PageType.weightStep(forWeight: 374), 0)
        XCTAssertEqual(PageType.weightStep(forWeight: 375), 1)
        XCTAssertEqual(PageType.weightStep(forWeight: 400), 1)
        XCTAssertEqual(PageType.weightStep(forWeight: 435), 1)
        XCTAssertEqual(PageType.weightStep(forWeight: 436), 2)
        XCTAssertEqual(PageType.weightStep(forWeight: 470), 2)
    }

    // Medium (500) is how the original panel says a word differs (A62).
    // The heaviest place on the slider stays below it, as Heavier did.
    func testHeavierIsStillNeverTheDifferingWordsMedium() {
        XCTAssertLessThan(PageType.weightScale.values.last!, 500)
        XCTAssertEqual(PageType.weightScale.values.max(), PageType.weightScale.values.last)
        XCTAssertEqual(PageType.weightScale.values.last, PageType.weights.last)
    }

    // The size keeps its key and its halves; the slider counts them as
    // places, and every place comes back to its size.
    func testTheSizeHasTwentyFivePositions() {
        XCTAssertEqual(PageType.sizePositions, 25)
        XCTAssertEqual(PageType.sizeIndex(of: 16), 0)
        XCTAssertEqual(PageType.sizeIndex(of: 19), 6)
        XCTAssertEqual(PageType.sizeIndex(of: 28), 24)
        XCTAssertEqual(PageType.sizeIndex(of: 40), 24)
        XCTAssertEqual(PageType.sizeIndex(of: 10), 0)
        XCTAssertEqual(PageType.size(at: 6), 19)
        XCTAssertEqual(PageType.size(at: 99), 28)
        XCTAssertEqual(PageType.size(at: -1), 16)
        for i in 0..<PageType.sizePositions {
            XCTAssertEqual(PageType.sizeIndex(of: PageType.size(at: i)), i)
        }
    }

    // The faces (A69). Set nothing and the page is in Literata, at the size,
    // weight and leading it has always had.
    func testLiterataIsTheFirstFaceAndTodaysPage() {
        XCTAssertEqual(PageFaces.all.first, PageFaces.literata)
        XCTAssertEqual(PageType.pointSize(19, face: PageFaces.literata), 19)
        for weight in [350, 400, 470, 620] {
            XCTAssertEqual(PageType.faceWeight(weight, face: PageFaces.literata), weight)
        }
        XCTAssertEqual(PageType.naturalLineMultiple(1.72, face: PageFaces.literata), 1.72)
    }

    // A file naming a face this build does not have, or none, is still set.
    func testAnUnknownFaceIsLiterata() {
        XCTAssertEqual(PageFaces.face(id: nil), PageFaces.literata)
        XCTAssertEqual(PageFaces.face(id: ""), PageFaces.literata)
        XCTAssertEqual(PageFaces.face(id: "comicSans"), PageFaces.literata)
        for face in PageFaces.all {
            XCTAssertEqual(PageFaces.face(id: face.id), face)
        }
    }

    // The ids are what every saved file holds, and the stems are the files'
    // names: spelled out, so that a rename has to be meant.
    func testFaceIDsAreStable() {
        XCTAssertEqual(PageFaces.all.map(\.id), ["literata", "sourceSerif", "ebGaramond", "alegreya", "atkinson"])
        XCTAssertEqual(
            PageFaces.all.map(\.name),
            ["Literata", "Source Serif", "EB Garamond", "Alegreya", "Atkinson Hyperlegible"])
        XCTAssertEqual(
            PageFaces.all.map(\.fileStem),
            ["Literata", "SourceSerif4", "EBGaramond", "Alegreya", "AtkinsonHyperlegibleNext"])
    }

    // Every weight the page asks for, Bold Text included, is one the face
    // can draw, in order: Lighter is lighter and Heavier heavier in every
    // face, even one whose axis begins at Book.
    func testEveryFaceDrawsTheWholeWeightRangeInOrder() {
        for face in PageFaces.all {
            let drawn = [350, 400, 470, 500, 550, 620].map { PageType.faceWeight($0, face: face) }
            for weight in drawn {
                XCTAssertTrue(face.weightRange.contains(weight), "\(face.id) draws \(weight)")
            }
            XCTAssertEqual(drawn, drawn.sorted(), face.id)
            XCTAssertLessThan(PageType.faceWeight(350, face: face), PageType.faceWeight(400, face: face), face.id)
            XCTAssertGreaterThan(PageType.faceWeight(470, face: face), PageType.faceWeight(400, face: face), face.id)
        }
    }

    // On the iPhone a face's lines fall as far apart as Literata's at every
    // place on the slider, though each face's own line is its own height.
    func testAFaceKeepsTheLinePitch() {
        for face in PageFaces.all {
            for m in PageType.lineHeightScale.values {
                let multiple = Double(m) / 100
                let pitch = 19 * face.sizeMatch * face.naturalLine * PageType.naturalLineMultiple(multiple, face: face)
                XCTAssertEqual(pitch, 19 * 1.485 * multiple, accuracy: 1e-9, "\(face.id) at \(m)")
            }
        }
    }

    // The iPhone's page window adds to a face's natural line exactly what
    // brings it to the page's.
    func testTheWindowsLeadingIsThePages() {
        for face in PageFaces.all {
            let natural = 19 * face.sizeMatch * face.naturalLine
            XCTAssertEqual(
                natural + PageType.extraLeading(size: 19, multiple: 1.72, face: face), 19 * 1.485 * 1.72,
                accuracy: 1e-9, face.id)
        }
    }

    // A margin is room either side of the words, until the words would be
    // narrower than thirteen ems; then the margin gives way, to nothing at
    // the largest sizes. 331 is the words' width on a 393-point phone.
    func testTheMarginsGiveWayToTheWords() {
        XCTAssertEqual(PageType.margin(requested: 40, textWidth: 331, size: 19), 40)
        XCTAssertEqual(PageType.margin(requested: 48, textWidth: 331, size: 19), 42)
        XCTAssertEqual(PageType.margin(requested: 48, textWidth: 331, size: 24), (331 - 312) / 2)
        XCTAssertEqual(PageType.margin(requested: 48, textWidth: 331, size: 24), 9.5)
        XCTAssertEqual(PageType.margin(requested: 48, textWidth: 331, size: 28), 0)
        XCTAssertEqual(PageType.margin(requested: 0, textWidth: 331, size: 19), 0)
        for size in stride(from: 16.0, through: 28.0, by: 0.5) {
            for requested in PageType.marginScale.values {
                let margin = PageType.margin(requested: Double(requested), textWidth: 331, size: size)
                XCTAssertGreaterThanOrEqual(margin, 0)
                XCTAssertLessThanOrEqual(margin, Double(requested))
            }
        }
    }

    // Letter spacing is kept in thousandths and given in ems, held to the
    // scale.
    func testLetterSpacingIsHeldInThousandths() {
        XCTAssertEqual(PageType.letterSpacingEm(25), 0.025)
        XCTAssertEqual(PageType.letterSpacingEm(61), 0.06)
        XCTAssertEqual(PageType.letterSpacingEm(-4), 0)
    }
}
