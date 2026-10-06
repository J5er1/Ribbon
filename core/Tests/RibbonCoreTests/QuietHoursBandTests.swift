import XCTest
@testable import RibbonCore

// Quiet hours, drawn as the night (A66): the band's arithmetic.
// QuietHoursBandTest.kt holds the Kotlin port to the same cases.
final class QuietHoursBandTests: XCTestCase {
    func h(_ hour: Int, _ minute: Int = 0) -> Int { hour * 60 + minute }

    // MARK: Where a time sits

    func testTheBandRunsNoonToNoonWithMidnightInTheMiddle() {
        XCTAssertEqual(QuietHoursBand.position(of: h(12)), 0, accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: h(18)), 0.25, accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: h(0)), 0.5, accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: h(6)), 0.75, accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: h(22)), 10.0 / 24.0, accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: h(11, 59)), 1439.0 / 1440.0, accuracy: 1e-9)
    }

    func testAMinuteOutsideOneDayIsTheSameMinuteOfADay() {
        XCTAssertEqual(QuietHoursBand.position(of: h(24)), QuietHoursBand.position(of: h(0)), accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.position(of: -60), QuietHoursBand.position(of: h(23)), accuracy: 1e-9)
        XCTAssertEqual(QuietHoursBand.wrapped(-1), h(23, 59))
        XCTAssertEqual(QuietHoursBand.wrapped(h(25)), h(1))
    }

    func testTheMarksAreTheEveningMidnightAndTheMorning() {
        XCTAssertEqual(QuietHoursBand.marks, [h(18), h(0), h(6)])
        XCTAssertEqual(QuietHoursBand.marks.map(QuietHoursBand.position(of:)), [0.25, 0.5, 0.75])
    }

    // MARK: Which time a finger is over

    func testAPointReadsAsTheNearestQuarterHour() {
        XCTAssertEqual(QuietHoursBand.minute(at: 0.5), h(0))
        XCTAssertEqual(QuietHoursBand.minute(at: 0.25), h(18))
        XCTAssertEqual(QuietHoursBand.minute(at: QuietHoursBand.position(of: h(22, 7))), h(22))
        XCTAssertEqual(QuietHoursBand.minute(at: QuietHoursBand.position(of: h(22, 8))), h(22, 15))
        XCTAssertEqual(QuietHoursBand.minute(at: QuietHoursBand.position(of: h(23, 53))), h(0))
    }

    func testAPointPastEitherEndIsHeldThere() {
        XCTAssertEqual(QuietHoursBand.minute(at: -0.2), h(12))
        XCTAssertEqual(QuietHoursBand.minute(at: 0), h(12))
        // The far end is noon again; a handle drawn there stops short of it.
        XCTAssertEqual(QuietHoursBand.minute(at: 1), h(11, 45))
        XCTAssertEqual(QuietHoursBand.minute(at: 1.4), h(11, 45))
    }

    func testEveryQuarterHourComesBackToItself() {
        for minute in stride(from: 0, to: QuietHoursBand.day, by: QuietHoursBand.step) {
            XCTAssertEqual(QuietHoursBand.minute(at: QuietHoursBand.position(of: minute)), minute)
        }
    }

    // MARK: A step at a time

    func testAStepIsAQuarterOfAnHourRoundTheClock() {
        XCTAssertEqual(QuietHoursBand.stepped(h(22), by: 1), h(22, 15))
        XCTAssertEqual(QuietHoursBand.stepped(h(22), by: -1), h(21, 45))
        XCTAssertEqual(QuietHoursBand.stepped(h(23, 45), by: 1), h(0))
        XCTAssertEqual(QuietHoursBand.stepped(h(0), by: -1), h(23, 45))
        XCTAssertEqual(QuietHoursBand.stepped(h(6), by: 4), h(7))
    }

    func testATimeBetweenStepsLandsOnOneFirst() {
        XCTAssertEqual(QuietHoursBand.stepped(h(22, 7), by: 1), h(22, 15))
        XCTAssertEqual(QuietHoursBand.stepped(h(22, 8), by: 1), h(22, 30))
        XCTAssertEqual(QuietHoursBand.stepped(h(23, 58), by: 0), h(0))
    }

    // MARK: What is drawn quiet

    func testANightIsOneStretch() {
        let spans = QuietHoursBand.spans(start: h(22), end: h(6))
        XCTAssertEqual(spans.count, 1)
        XCTAssertEqual(spans[0].from, 10.0 / 24.0, accuracy: 1e-9)
        XCTAssertEqual(spans[0].to, 0.75, accuracy: 1e-9)
    }

    func testQuietHoursAcrossNoonAreTheBandsTwoEnds() {
        let spans = QuietHoursBand.spans(start: h(11), end: h(13))
        XCTAssertEqual(spans.count, 2)
        XCTAssertEqual(spans[0].from, 23.0 / 24.0, accuracy: 1e-9)
        XCTAssertEqual(spans[0].to, 1, accuracy: 1e-9)
        XCTAssertEqual(spans[1].from, 0, accuracy: 1e-9)
        XCTAssertEqual(spans[1].to, 1.0 / 24.0, accuracy: 1e-9)
    }

    func testQuietHoursEndingAtNoonRunToTheEnd() {
        let spans = QuietHoursBand.spans(start: h(22), end: h(12))
        XCTAssertEqual(spans.count, 1)
        XCTAssertEqual(spans[0].from, 10.0 / 24.0, accuracy: 1e-9)
        XCTAssertEqual(spans[0].to, 1, accuracy: 1e-9)
    }

    func testQuietHoursBeginningAtNoonRunFromTheStart() {
        let spans = QuietHoursBand.spans(start: h(12), end: h(14))
        XCTAssertEqual(spans, [QuietSpan(from: 0, to: 2.0 / 24.0)])
    }

    func testTheSameMinuteAtBothEndsIsNoQuietHours() {
        XCTAssertEqual(QuietHoursBand.spans(start: h(22), end: h(22)), [])
        XCTAssertEqual(QuietHoursBand.spans(start: h(0), end: h(24)), [])
    }
}
