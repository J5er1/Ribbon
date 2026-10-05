import XCTest
@testable import RibbonCore

// Who is in a room, a connection at a time. PresenceLedgerTest.kt holds the
// Kotlin port to the same cases.
final class PresenceLedgerTests: XCTestCase {
    typealias Ledger = PresenceLedger<String>
    func c(_ ref: String?, _ meta: String) -> Ledger.Connection { .init(ref: ref, meta: meta) }

    func testASecondPhoneLeavingKeepsThePerson() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone")]])
        room.apply(joins: ["ana": [c("2", "tablet")]], leaves: [:])
        room.apply(joins: [:], leaves: ["ana": [c("2", "tablet")]])
        XCTAssertEqual(room.latest("ana"), "phone")
    }

    func testAReconnectWhoseOldSocketLeavesLateKeepsThePerson() {
        // The new socket joins; the old one times out after it.
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "old socket")]])
        room.apply(joins: ["ana": [c("2", "new socket")]], leaves: [:])
        room.apply(joins: [:], leaves: ["ana": [c("1", "old socket")]])
        XCTAssertEqual(room.latest("ana"), "new socket")
        XCTAssertEqual(room.people["ana"]?.count, 1)
    }

    func testATrackReplacesItsOwnConnection() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "verse 1")]])
        room.apply(joins: ["ana": [c("2", "verse 2")]], leaves: ["ana": [c("1", "verse 1")]])
        XCTAssertEqual(room.people["ana"]?.map(\.meta), ["verse 2"])
    }

    func testTheLastConnectionLeavingTakesThePerson() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone")], "ben": [c("3", "phone")]])
        room.apply(joins: [:], leaves: ["ana": [c("1", "phone")]])
        XCTAssertNil(room.latest("ana"))
        XCTAssertEqual(Set(room.keys), ["ben"])
    }

    func testTheLatestIsWhicheverConnectionSpokeLast() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone, verse 1"), c("2", "tablet")]])
        room.apply(joins: ["ana": [c("4", "phone, verse 2")]], leaves: ["ana": [c("1", "phone, verse 1")]])
        XCTAssertEqual(room.latest("ana"), "phone, verse 2")
        XCTAssertEqual(room.latest("ana", where: { $0 == "tablet" }), "tablet")
    }

    func testALeaveNamingNoConnectionTakesThePerson() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone"), c("2", "tablet")]])
        room.apply(joins: [:], leaves: ["ana": [c(nil, "?")]])
        XCTAssertNil(room.latest("ana"))
    }

    func testALeaveForSomeoneNotHereIsNothing() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone")]])
        room.apply(joins: [:], leaves: ["ben": [c("9", "phone")]])
        XCTAssertEqual(Set(room.keys), ["ana"])
    }

    func testAStateReplacesTheWholeRoom() {
        var room = Ledger()
        room.reset(to: ["ana": [c("1", "phone")]])
        room.reset(to: ["ben": [c("3", "phone")], "cy": []])
        XCTAssertEqual(Set(room.keys), ["ben"])
        XCTAssertEqual(room.allMetas, ["phone"])
    }
}
