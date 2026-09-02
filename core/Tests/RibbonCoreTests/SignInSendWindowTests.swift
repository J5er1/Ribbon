import XCTest
@testable import RibbonCore

final class SignInSendWindowTests: XCTestCase {
    let start = Date(timeIntervalSince1970: 1_772_000_000)

    func testFirstCodeGoesStraightOut() {
        let window = SignInSendWindow()
        XCTAssertTrue(window.maySend(now: start))
        XCTAssertEqual(window.secondsUntilNextSend(now: start), 0)
    }

    func testAMinuteBetweenCodes() {
        var window = SignInSendWindow()
        window.record(at: start)

        XCTAssertFalse(window.maySend(now: start))
        XCTAssertEqual(window.secondsUntilNextSend(now: start), 60)
        XCTAssertEqual(window.secondsUntilNextSend(now: start.addingTimeInterval(30)), 30)
        XCTAssertFalse(window.maySend(now: start.addingTimeInterval(59)))
        XCTAssertTrue(window.maySend(now: start.addingTimeInterval(60)))
    }

    func testSixAnHourThenTheCeilingHolds() {
        var window = SignInSendWindow()
        // Six codes, each a minute and a half apart — every one clears the
        // per-code floor, so it is the ceiling that stops the seventh.
        var moment = start
        for _ in 0..<SignInSendWindow.maxInWindow {
            XCTAssertTrue(window.maySend(now: moment), "the ceiling closed early")
            window.record(at: moment)
            moment = moment.addingTimeInterval(90)
        }

        XCTAssertFalse(window.maySend(now: moment))
        XCTAssertTrue(window.isHourlyLimit(now: moment))

        // The window reopens an hour after the FIRST of the six, which is
        // the instant the server's window expires too.
        let reopens = start.addingTimeInterval(SignInSendWindow.windowLength)
        XCTAssertFalse(window.maySend(now: reopens.addingTimeInterval(-1)))
        XCTAssertTrue(window.maySend(now: reopens))
    }

    func testTheMinuteFloorIsNotTheHourlyCeiling() {
        var window = SignInSendWindow()
        window.record(at: start)
        // One send: held by the floor, not the ceiling. The distinction is
        // what the two different lines of copy hang on.
        XCTAssertFalse(window.maySend(now: start))
        XCTAssertFalse(window.isHourlyLimit(now: start))
    }

    func testOldSendsFallOutOfTheWindow() {
        var window = SignInSendWindow()
        for index in 0..<SignInSendWindow.maxInWindow {
            window.record(at: start.addingTimeInterval(Double(index) * 90))
        }
        // Well past the window: everything has aged out and the count is
        // clear again.
        let later = start.addingTimeInterval(SignInSendWindow.windowLength * 2)
        XCTAssertTrue(window.maySend(now: later))
        XCTAssertFalse(window.isHourlyLimit(now: later))
    }

    func testServerRefusalOutranksTheLocalModel() {
        // The local model says "fine" — another phone spent the window, so
        // the server says otherwise and the server wins.
        var window = SignInSendWindow()
        XCTAssertTrue(window.maySend(now: start))

        window.hold(until: start.addingTimeInterval(300))
        XCTAssertFalse(window.maySend(now: start))
        XCTAssertEqual(window.secondsUntilNextSend(now: start), 300)
        XCTAssertTrue(window.maySend(now: start.addingTimeInterval(300)))
    }

    func testAShorterServerHoldNeverLoosensALongerOne() {
        var window = SignInSendWindow()
        window.hold(until: start.addingTimeInterval(600))
        window.hold(until: start.addingTimeInterval(60))
        XCTAssertEqual(window.secondsUntilNextSend(now: start), 600)
    }

    func testCountdownRoundsUpSoItNeverOverpromises() {
        var window = SignInSendWindow()
        window.record(at: start)
        // Half a second short of the floor must still read as a second to
        // wait, never as zero.
        let almost = start.addingTimeInterval(59.5)
        XCTAssertEqual(window.secondsUntilNextSend(now: almost), 1)
        XCTAssertFalse(window.maySend(now: almost))
    }
}
