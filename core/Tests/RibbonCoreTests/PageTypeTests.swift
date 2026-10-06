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
}
