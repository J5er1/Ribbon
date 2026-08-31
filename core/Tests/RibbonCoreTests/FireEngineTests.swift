import XCTest
@testable import RibbonCore

final class FireEngineTests: XCTestCase {
    let ruth = UUID()
    let jacob = UUID()
    let epoch = Date(timeIntervalSince1970: 1_900_000_000)

    func hours(_ h: Double) -> Date { epoch.addingTimeInterval(h * 3600) }

    func testNewFireIsCatching() {
        let fire = Handiwork(scale: .medium)
        XCTAssertEqual(fire.state(at: epoch), .catching)
    }

    func testFirstFuelCatches() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        // First light is a restart: the fire catches, it does not jump to
        // burning.
        XCTAssertEqual(fire.state(at: hours(1)), .catching)
    }

    func testReturningLiftsToBurning() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: ruth, at: hours(10))
        XCTAssertEqual(fire.state(at: hours(11)), .burning)
    }

    func testTwoPeopleInWindowIsSteady() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: jacob, at: hours(20))
        XCTAssertEqual(fire.state(at: hours(21)), .steady)
    }

    func testMondayNightToWednesdayMorningNeverLapses() {
        // §4.1: someone who reads at 10 p.m. Monday and 9 a.m. Wednesday
        // never experiences a lapse — 35 elapsed hours sit inside the window.
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: ruth, at: hours(1))
        XCTAssertEqual(fire.state(at: hours(1 + 35)), .burning)
        fire.feed(by: ruth, at: hours(1 + 35))
        XCTAssertEqual(fire.state(at: hours(1 + 35)), .burning)
    }

    func testSteadyEasesToBurningThenCatching() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: jacob, at: hours(2))
        XCTAssertEqual(fire.state(at: hours(3)), .steady)
        // Window closes: eases to burning.
        XCTAssertEqual(fire.state(at: hours(2 + 40)), .burning)
        // Days later: comes to rest at catching — the floor. Never banked.
        XCTAssertEqual(fire.state(at: hours(2 + 130)), .catching)
        XCTAssertEqual(fire.state(at: hours(2000)), .catching)
    }

    func testBurningComesToRestAtCatching() {
        var fire = Handiwork(scale: .small)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: ruth, at: hours(1))
        XCTAssertEqual(fire.state(at: hours(30)), .burning)
        XCTAssertEqual(fire.state(at: hours(1 + 90)), .burning)
        XCTAssertEqual(fire.state(at: hours(1 + 100)), .catching)
    }

    func testFuelAfterLongQuietCatchesAgain() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: ruth, at: hours(1))
        // A long quiet, then fuel: catching, not burning.
        fire.feed(by: ruth, at: hours(300))
        XCTAssertEqual(fire.state(at: hours(300)), .catching)
        // Reading again soon after lifts it.
        fire.feed(by: ruth, at: hours(305))
        XCTAssertEqual(fire.state(at: hours(305)), .burning)
    }

    func testBankedOnlyViaQuietDay() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        let banked = [DateInterval(start: hours(5), duration: 24 * 3600)]
        XCTAssertEqual(fire.state(at: hours(10), bankedIntervals: banked), .banked)
        XCTAssertEqual(fire.state(at: hours(40), bankedIntervals: banked), .catching)
    }

    func testQuietDayPausesSubsiding() {
        var fire = Handiwork(scale: .medium)
        fire.feed(by: ruth, at: epoch)
        fire.feed(by: ruth, at: hours(1))
        XCTAssertEqual(fire.state(at: hours(2)), .burning)
        // A quiet day covers hours 10–34. At hour 60, raw elapsed since
        // fuel is 59h, but 24 banked hours are excluded: 35h — still inside
        // the window, still burning.
        let banked = [DateInterval(start: hours(10), duration: 24 * 3600)]
        XCTAssertEqual(fire.state(at: hours(60), bankedIntervals: banked), .burning)
        // Without the quiet day it would have eased past the window.
        XCTAssertEqual(fire.state(at: hours(60)), .burning) // 59h < burningRestsAt
        XCTAssertEqual(fire.state(at: hours(1 + 97)), .catching)
        XCTAssertEqual(fire.state(at: hours(1 + 97), bankedIntervals: banked), .burning)
    }

    func testCoalsDeepenAndNeverRecede() {
        var fire = Handiwork(scale: .large)
        XCTAssertEqual(fire.coalDepth, 0)
        fire.feed(by: ruth, at: epoch)
        let d1 = fire.coalDepth
        XCTAssertGreaterThan(d1, 0)
        // Feeding again inside the coal-credit window doesn't pump the bed.
        fire.feed(by: ruth, at: hours(1))
        XCTAssertEqual(fire.coalDepth, d1)
        // Days of tending deepen it, asymptotically, never reaching 1.
        var depth = d1
        for day in 1...400 {
            fire.feed(by: ruth, at: hours(Double(day) * 24))
            XCTAssertGreaterThanOrEqual(fire.coalDepth, depth)
            depth = fire.coalDepth
        }
        XCTAssertLessThan(fire.coalDepth, 1)
    }

    func testRecentFuelStaysPruned() {
        var fire = Handiwork(scale: .medium)
        for day in 0..<60 {
            fire.feed(by: ruth, at: hours(Double(day) * 24))
            fire.feed(by: jacob, at: hours(Double(day) * 24 + 2))
        }
        // §13: the rolling window is the only per-person reading record.
        XCTAssertLessThanOrEqual(fire.recentFuel.count, 6)
        let cutoff = hours(59 * 24 + 2 - 36)
        XCTAssertTrue(fire.recentFuel.allSatisfy { $0.at >= cutoff })
    }

    func testQuietDayBankedInterval() {
        let zone = TimeZone(identifier: "America/New_York")!
        let marked = Date(timeIntervalSince1970: 1_900_000_000)
        let day = QuietDay(roomID: UUID(), personID: ruth, markedAt: marked, timeZone: zone)
        let interval = day.bankedInterval
        XCTAssertNotNil(interval)
        XCTAssertTrue(interval!.contains(marked))
        XCTAssertEqual(interval!.duration, 24 * 3600, accuracy: 3700) // DST tolerance
    }

    func testMergedIntervalsOverlap() {
        let a = DateInterval(start: hours(0), duration: 10 * 3600)
        let b = DateInterval(start: hours(5), duration: 10 * 3600)
        let c = DateInterval(start: hours(30), duration: 3600)
        let merged = Handiwork.merged([c, a, b])
        XCTAssertEqual(merged.count, 2)
        XCTAssertEqual(merged[0].duration, 15 * 3600)
    }
}
