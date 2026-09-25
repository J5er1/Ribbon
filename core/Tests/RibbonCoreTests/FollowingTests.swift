import XCTest
@testable import RibbonCore

final class FollowingTests: XCTestCase {
    let t0 = Date(timeIntervalSince1970: 1_900_000_000)

    func at(_ seconds: Double) -> Date { t0.addingTimeInterval(seconds) }

    // A psalm-shaped chapter: a title before the first verse, a verse that
    // runs from prose into a poetic line, and a verse with no words at all.
    let psalm = ScriptureChapter(n: 3, blocks: [
        ScriptureBlock(s: .d, x: [ScriptureSpan(t: "A Psalm of David.")]),
        ScriptureBlock(s: .p, x: [ScriptureSpan(v: 1, t: "one two three four five six seven eight nine ten")]),
        ScriptureBlock(s: .p, x: [ScriptureSpan(v: 2, t: "a b c d e")]),
        ScriptureBlock(s: .q1, x: [ScriptureSpan(t: "f g h i j")]),
        ScriptureBlock(s: .p, x: [
            ScriptureSpan(v: 3, t: "w w w w w w w w w w"),
            ScriptureSpan(t: " w w w w w w w w w w", w: true),
            ScriptureSpan(v: 4, t: ""),
        ]),
    ])

    /// Two chapters of ten verses, twenty words each.
    func rulers(_ chapter: Int) -> ChapterRuler? {
        guard chapter == 1 || chapter == 2 else { return nil }
        return ChapterRuler(chapter: chapter, verses: Array(1...10), words: Array(repeating: 20, count: 10))
    }

    func assertPoint(
        _ point: ReadingPoint?, _ chapter: Int, _ verse: Int, _ part: Double,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        guard let point else { return XCTFail("no point", file: file, line: line) }
        XCTAssertEqual(point.chapter, chapter, file: file, line: line)
        XCTAssertEqual(point.verse, verse, file: file, line: line)
        XCTAssertEqual(point.part, part, accuracy: 0.0001, file: file, line: line)
    }

    // MARK: The ruler

    func testRulerCountsWordsPerVerse() throws {
        let ruler = try XCTUnwrap(ChapterRuler(measuring: psalm))
        XCTAssertEqual(ruler.chapter, 3)
        XCTAssertEqual(ruler.verses, [1, 2, 3, 4])
        // The title is not on the ruler; verse 2 carries on into its poetic
        // line; a verse of no words still counts as one.
        XCTAssertEqual(ruler.words, [10, 10, 20, 1])
        XCTAssertEqual(ruler.length, 41)
    }

    func testRulerOffsetAndPointRoundTrip() throws {
        let ruler = try XCTUnwrap(ChapterRuler(measuring: psalm))
        XCTAssertEqual(ruler.offset(of: ReadingPoint(chapter: 3, verse: 2, part: 0.5)), 15)
        assertPoint(ruler.point(at: 15), 3, 2, 0.5)
        assertPoint(ruler.point(at: 0), 3, 1, 0)
        assertPoint(ruler.point(at: 41), 3, 4, 1)
        assertPoint(ruler.point(at: 100), 3, 4, 1)
        assertPoint(ruler.point(at: -5), 3, 1, 0)
    }

    func testRulerVerseNotOnTheRuler() {
        let ruler = ChapterRuler(chapter: 7, verses: [1, 2, 4], words: [10, 10, 10])
        // A verse this version leaves out is where the one before it ends.
        XCTAssertEqual(ruler.offset(of: ReadingPoint(chapter: 7, verse: 3, part: 0.5)), 20)
        XCTAssertEqual(ruler.offset(of: ReadingPoint(chapter: 7, verse: 0)), 0)
    }

    func testRulerNilWithoutVerses() {
        let title = ScriptureChapter(n: 1, blocks: [
            ScriptureBlock(s: .d, x: [ScriptureSpan(t: "Of David.")]),
        ])
        XCTAssertNil(ChapterRuler(measuring: title))
    }

    // MARK: The guess

    func testEstimateIsNilBeforeAnyReport() {
        let estimate = ReadingEstimate()
        XCTAssertNil(estimate.point(at: t0, rulers: rulers))
    }

    func testEstimateMirrorsWhileScrolling() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3, part: 0.5), settled: false, received: t0),
            rulers: rulers)
        assertPoint(estimate.point(at: at(10), rulers: rulers), 1, 3, 0.5)
    }

    func testEstimateReadsOnAtTheStartingPace() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 8),
                settled: true, received: t0),
            rulers: rulers)
        // 3.6 words a second for five seconds: eighteen words into verse 3.
        assertPoint(estimate.point(at: at(5), rulers: rulers), 1, 3, 0.9)
    }

    func testEstimateStopsShortOfTheirNextScroll() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 8),
                settled: true, received: t0),
            rulers: rulers)
        // A hundred words to the bottom of their screen; before a scroll of
        // theirs is seen, three-quarters of half of that.
        assertPoint(estimate.point(at: at(100), rulers: rulers), 1, 4, 0.875)
    }

    func testUsualScrollLimitsTheGuess() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 1), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: t0),
            rulers: rulers)
        // They scrolled forty words: the guess runs on thirty past their
        // line, however long they stay.
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 2, verse: 2),
                settled: true, received: at(12)),
            rulers: rulers)
        XCTAssertEqual(estimate.pace, 3.543860, accuracy: 0.0001)
        assertPoint(estimate.point(at: at(112), rulers: rulers), 1, 4, 0.5)
    }

    func testEstimateWithoutAnEndLeadsTwoVerses() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3), settled: true, received: t0),
            rulers: rulers)
        assertPoint(estimate.point(at: at(100), rulers: rulers), 1, 6, 0)
    }

    func testRepeatDoesNotMoveTheGuessBack() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: t0),
            rulers: rulers)
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3, part: 0.01), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: at(10)),
            rulers: rulers)
        // Thirty-six words on from where they came to rest, not from the
        // keepalive.
        assertPoint(estimate.point(at: at(10), rulers: rulers), 1, 4, 0.8)
    }

    func testRepeatUpdatesTheEndOfTheirScreen() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: t0),
            rulers: rulers)
        // A note opened on their phone: their screen now ends a verse down.
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 4),
                settled: true, received: at(5)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(100), rulers: rulers), 1, 3, 0.375)
    }

    func testRestAfterAScrollInFlightStartsTheReading() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3), settled: false, received: t0),
            rulers: rulers)
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 8),
                settled: true, received: at(2)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(7), rulers: rulers), 1, 3, 0.9)
    }

    func testInFlightSampleBehindTheGuessDoesNotPullItBack() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: t0),
            rulers: rulers)
        // The first sample of their next scroll is where the last one
        // ended — behind a guess that has read on since.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3, part: 0.1), settled: false, received: at(10)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(11), rulers: rulers), 1, 4, 0.8)
        // Once the scroll passes the guess, the page is where it is.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 5, part: 0.5), settled: false, received: at(11.5)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(12), rulers: rulers), 1, 5, 0.5)
    }

    func testGoingBackInFlightIsFollowed() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 6), end: ReadingPoint(chapter: 1, verse: 10),
                settled: true, received: t0),
            rulers: rulers)
        // Forty words back is more than a fifth of their screen.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 4), settled: false, received: at(5)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(5), rulers: rulers), 1, 4, 0)
        XCTAssertEqual(estimate.wentBackAt, at(5))
    }

    func testCarriedReportIsMirrored() {
        var estimate = ReadingEstimate()
        // Their page is being carried by a follow of their own: it is where
        // their page is, and nothing more.
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 8),
                settled: true, carried: true, received: t0),
            rulers: rulers)
        assertPoint(estimate.point(at: at(60), rulers: rulers), 1, 3, 0)
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 6), settled: true, carried: true, received: at(10)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(70), rulers: rulers), 1, 6, 0)
        XCTAssertEqual(estimate.pace, 3.6, accuracy: 0.0001)
    }

    func testLearnsAFasterReader() {
        var estimate = ReadingEstimate()
        // Sixty words every ten seconds: six a second.
        for (second, verse) in [(0.0, 1), (10, 4), (20, 7), (30, 10)] {
            estimate.observe(
                ReadingReport(at: ReadingPoint(chapter: 1, verse: verse), settled: true, received: at(second)),
                rulers: rulers)
        }
        XCTAssertEqual(estimate.pace, 4.536433, accuracy: 0.0001)
    }

    func testRestInFlightRestLearnsThePace() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 1), settled: true, received: t0),
            rulers: rulers)
        // Every scroll is seen in flight before it rests; the pace is
        // learned from rest to rest all the same.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 2), settled: false, received: at(9)),
            rulers: rulers)
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 4), settled: true, received: at(10)),
            rulers: rulers)
        XCTAssertEqual(estimate.pace, 4.036364, accuracy: 0.0001)
    }

    func testPauseTeachesNothing() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 1), settled: true, received: t0),
            rulers: rulers)
        // Forty words in two minutes is somebody who stopped.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3), settled: true, received: at(120)),
            rulers: rulers)
        XCTAssertEqual(estimate.pace, 3.6, accuracy: 0.0001)
    }

    func testJumpTeachesNothing() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 1), settled: true, received: t0),
            rulers: rulers)
        // Two hundred and eighty words in ten seconds is going somewhere.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 2, verse: 5), settled: true, received: at(10)),
            rulers: rulers)
        // And a chapter nobody has measured is not a distance at all.
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 5, verse: 1), settled: true, received: at(20)),
            rulers: rulers)
        XCTAssertEqual(estimate.pace, 3.6, accuracy: 0.0001)
    }

    func testHoldsWhenTheyGoStill() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 3), end: ReadingPoint(chapter: 1, verse: 8),
                settled: true, received: t0),
            rulers: rulers)
        estimate.hold(at: at(5), rulers: rulers)
        assertPoint(estimate.point(at: at(60), rulers: rulers), 1, 3, 0.9)
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 5), settled: true, received: at(70)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(70), rulers: rulers), 1, 5, 0)
    }

    func testLookingBackMovesTheGuessBack() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 6), settled: true, received: t0),
            rulers: rulers)
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 2), settled: true, received: at(30)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(30), rulers: rulers), 1, 2, 0)
        XCTAssertEqual(estimate.pace, 3.6, accuracy: 0.0001)
        XCTAssertEqual(estimate.wentBackAt, at(30))
    }

    func testGuessCrossesIntoTheNextChapter() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(
                at: ReadingPoint(chapter: 1, verse: 10, part: 0.5), end: ReadingPoint(chapter: 2, verse: 6),
                settled: true, received: t0),
            rulers: rulers)
        // Ten words to the end of chapter 1, a hundred on to the bottom of
        // their screen: the guess runs forty-one and a quarter, over the
        // chapter's end.
        assertPoint(estimate.point(at: at(100), rulers: rulers), 2, 2, 0.5625)
    }

    func testWithoutTheChapterMeasuredTheGuessStaysPut() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 3, verse: 4, part: 0.5), settled: true, received: t0),
            rulers: rulers)
        assertPoint(estimate.point(at: at(50), rulers: rulers), 3, 4, 0.5)
    }

    func testOlderWordArrivingLateIsIgnored() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 5), settled: false, received: at(10)),
            rulers: rulers)
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 2), settled: true, received: at(5)),
            rulers: rulers)
        assertPoint(estimate.point(at: at(10), rulers: rulers), 1, 5, 0)
    }

    func testReportedIsTheirLineNotTheGuess() {
        var estimate = ReadingEstimate()
        estimate.observe(
            ReadingReport(at: ReadingPoint(chapter: 1, verse: 3), settled: true, received: t0),
            rulers: rulers)
        assertPoint(estimate.reported, 1, 3, 0)
    }

    // MARK: The carriage

    func testCarriageHoldsInsideTheBand() {
        XCTAssertEqual(FollowCarriage.move(y: 300, viewport: 1000), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 550, viewport: 1000), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 80, viewport: 1000), .hold)
    }

    func testCarriageStepsForwardPastTheLine() {
        XCTAssertEqual(FollowCarriage.move(y: 620, viewport: 1000), .step(by: 370))
    }

    func testCarriageKeepsTheirLineOnScreen() {
        XCTAssertEqual(FollowCarriage.move(y: 620, reported: 400, viewport: 1000, minStep: 60), .step(by: 320))
        // Their own line is already near the top: nothing is worth moving.
        XCTAssertEqual(FollowCarriage.move(y: 620, reported: 100, viewport: 1000, minStep: 60), .hold)
    }

    func testCarriageStepsBackOnlyWhenTheyWentBack() {
        XCTAssertEqual(FollowCarriage.move(y: 40, reported: 40, viewport: 1000), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 40, reported: 40, viewport: 1000, wentBack: true), .step(by: -210))
        XCTAssertEqual(FollowCarriage.move(y: 40, reported: -50, viewport: 1000), .step(by: -210))
        // An older app's presence says no line: only going back goes back.
        XCTAssertEqual(FollowCarriage.move(y: 40, viewport: 1000), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 40, viewport: 1000, wentBack: true), .step(by: -210))
    }

    func testCarriageFliesWhenThePlaceIsNotLaidOut() {
        XCTAssertEqual(FollowCarriage.move(y: nil, viewport: 1000), .fly)
        XCTAssertEqual(FollowCarriage.move(y: 300, viewport: 0), .fly)
        XCTAssertEqual(FollowCarriage.move(y: nil, reported: 1200, viewport: 1000), .fly)
    }

    func testCarriageNeverFliesPastTheirLine() {
        // The guess is in the next chapter, not yet set out; their line is
        // still on screen: the page goes as far as their line allows.
        XCTAssertEqual(FollowCarriage.move(y: nil, reported: 400, viewport: 1000, minStep: 60), .step(by: 320))
        XCTAssertEqual(FollowCarriage.move(y: nil, reported: 100, viewport: 1000, minStep: 60), .hold)
    }

    func testCarriageRealigns() {
        XCTAssertEqual(FollowCarriage.move(y: 400, viewport: 1000, realign: true), .step(by: 150))
        XCTAssertEqual(FollowCarriage.move(y: 250.5, viewport: 1000, realign: true), .hold)
    }

    func testCarriageIsCalmerUnderReduceMotion() {
        XCTAssertEqual(FollowCarriage.move(y: 620, viewport: 1000, manner: .calm), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 800, viewport: 1000, manner: .calm), .step(by: 550))
    }

    func testCarriageSpokenMovesOnlyWhenTheirLineLeaves() {
        XCTAssertEqual(FollowCarriage.move(y: 700, reported: 500, viewport: 1000, manner: .spoken), .hold)
        XCTAssertEqual(FollowCarriage.move(y: 700, reported: 1200, viewport: 1000, manner: .spoken), .step(by: 950))
        XCTAssertEqual(FollowCarriage.move(y: 700, reported: -100, viewport: 1000, manner: .spoken), .step(by: -350))
        XCTAssertEqual(FollowCarriage.move(y: nil, viewport: 1000, manner: .spoken), .fly)
        XCTAssertEqual(FollowCarriage.move(y: 700, viewport: 1000, manner: .spoken), .hold)
    }
}
