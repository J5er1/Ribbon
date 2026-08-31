import XCTest
@testable import RibbonCore

final class RelativeTimeTests: XCTestCase {
    var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/New_York")!
        return cal
    }

    func date(_ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
    }

    func testSameDayPhrases() {
        let now = date(2026, 8, 31, 21)
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 31, 6, 40), now: now, calendar: calendar), "this morning")
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 31, 14), now: now, calendar: calendar), "this afternoon")
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 31, 20), now: now, calendar: calendar), "this evening")
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 31, 3), now: now, calendar: calendar), "in the night")
    }

    func testYesterdayAndLastNight() {
        let now = date(2026, 8, 31, 9)
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 30, 22, 15), now: now, calendar: calendar), "last night")
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 30, 10), now: now, calendar: calendar), "yesterday")
    }

    func testWeekday() {
        // 2026-08-31 is a Monday; 2026-08-27 was the previous Thursday.
        let now = date(2026, 8, 31, 9)
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 8, 27, 12), now: now, calendar: calendar), "Thursday")
    }

    func testOlderBecomesMonth() {
        let now = date(2026, 8, 31, 9)
        XCTAssertEqual(RibbonClock.phrase(for: date(2026, 3, 2, 12), now: now, calendar: calendar), "March")
        XCTAssertEqual(RibbonClock.phrase(for: date(2025, 11, 2, 12), now: now, calendar: calendar), "November 2025")
    }

    func testNeverACount() {
        // The phrase must never contain a digit — "14 hours ago" is a count,
        // and a wall-clock time would reveal someone was awake at 3 a.m.
        let now = date(2026, 8, 31, 21)
        // (A year on a months-old date, like "November 2025", is an address,
        // not a count — this sweep stays inside the year to check everything
        // nearer than that.)
        for hoursBack in stride(from: 1, through: 24 * 200, by: 7) {
            let phrase = RibbonClock.phrase(
                for: now.addingTimeInterval(-Double(hoursBack) * 3600),
                now: now, calendar: calendar)
            XCTAssertNil(
                phrase.rangeOfCharacter(from: .decimalDigits),
                "phrase leaked a number: \(phrase)")
        }
    }

    func testEmberRanges() {
        XCTAssertEqual(
            RibbonClock.emberRange(start: date(2026, 3, 3, 8), end: date(2026, 6, 20, 8), calendar: calendar),
            "March – June")
        XCTAssertEqual(
            RibbonClock.emberRange(start: date(2026, 3, 3, 8), end: date(2026, 3, 28, 8), calendar: calendar),
            "March")
        XCTAssertEqual(
            RibbonClock.emberRange(start: date(2026, 11, 3, 8), end: date(2027, 1, 20, 8), calendar: calendar),
            "November 2026 – January 2027")
    }
}
