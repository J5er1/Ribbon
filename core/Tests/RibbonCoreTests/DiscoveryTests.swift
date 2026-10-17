import XCTest
@testable import RibbonCore

// Being found (A72). DiscoveryTest.kt holds the Kotlin port to the same
// answers, case for case.

final class DiscoveryTests: XCTestCase {
    let me = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!
    let ruth = UUID(uuidString: "00000000-0000-4000-8000-000000000002")!
    let ann = UUID(uuidString: "00000000-0000-4000-8000-000000000003")!
    let room = UUID(uuidString: "00000000-0000-4000-8000-0000000000aa")!
    let start = Date(timeIntervalSince1970: 1_790_000_000)

    private func membership(_ person: UUID, yes: Bool) -> Membership {
        Membership(roomID: room, personID: person, joinedAt: start, wouldOpen: yes)
    }

    // MARK: One line in your own words

    func testALineIsTrimmedAndClosedUp() {
        XCTAssertEqual(OwnLine.clean("  Reading   Mark,\n slowly. "), "Reading Mark, slowly.")
        XCTAssertNil(OwnLine.clean("   \n "))
        XCTAssertNil(OwnLine.clean(nil))
    }

    func testALongLineIsCutAtAWord() {
        let line = String(repeating: "word ", count: 40)
        let cut = OwnLine.clean(line)!
        XCTAssertLessThanOrEqual(cut.count, OwnLine.limit)
        XCTAssertFalse(cut.hasSuffix(" "))
        XCTAssertTrue(cut.hasSuffix("word"))
        // One word longer than the limit is cut where the limit falls.
        XCTAssertEqual(OwnLine.clean(String(repeating: "a", count: 200))?.count, OwnLine.limit)
        // An ask may say more.
        XCTAssertGreaterThan(OwnLine.clean(line, limit: OwnLine.askLimit)!.count, OwnLine.limit)
    }

    // MARK: Being found

    func testBeingFoundIsOffAndNeedsThirteenSaid() {
        let fresh = BeingFound()
        XCTAssertFalse(fresh.findable)
        XCTAssertFalse(fresh.canBeFindable)
        XCTAssertTrue(BeingFound(saidThirteenAt: start).canBeFindable)
    }

    // MARK: A room is open only while everyone has said yes

    func testYouHaveNotSaidYes() {
        XCTAssertEqual(RoomOpenness.of([membership(me, yes: false), membership(ruth, yes: true)], me: me), .closed)
    }

    func testYouHaveAndSomebodyHasNot() {
        XCTAssertEqual(
            RoomOpenness.of([membership(me, yes: true), membership(ruth, yes: true), membership(ann, yes: false)], me: me),
            .waitingForOthers)
    }

    func testEveryoneHas() {
        XCTAssertEqual(RoomOpenness.of([membership(me, yes: true), membership(ruth, yes: true)], me: me), .open)
        // A room of one is open once its one person says yes.
        XCTAssertEqual(RoomOpenness.of([membership(me, yes: true)], me: me), .open)
    }

    func testARoomYouAreNotInIsClosedToYou() {
        XCTAssertEqual(RoomOpenness.of([membership(ruth, yes: true)], me: me), .closed)
    }

    // MARK: Asking to join

    func testAnAskStandsForFourteenDays() {
        let knock = Knock(roomID: room, person: PublicFace(id: ruth, name: "Ruth"), askedAt: start)
        XCTAssertTrue(knock.stands(at: start.addingTimeInterval(13 * 24 * 3600)))
        XCTAssertFalse(knock.stands(at: start.addingTimeInterval(14 * 24 * 3600)))
    }

    func testStandingAsksAreOldestFirstAndLapsedOnesGo() {
        let now = start.addingTimeInterval(20 * 24 * 3600)
        let old = Knock(roomID: room, person: PublicFace(id: ruth, name: "Ruth"), askedAt: start)
        let earlier = Knock(roomID: room, person: PublicFace(id: ann, name: "Ann"), askedAt: now.addingTimeInterval(-3600 * 5))
        let later = Knock(roomID: room, person: PublicFace(id: me, name: "Me"), askedAt: now.addingTimeInterval(-3600))
        XCTAssertEqual(Knock.standing([later, old, earlier], at: now).map(\.person.id), [ann, me])
    }

    // MARK: A found room

    func testAFoundRoomIsCalledByItsNameOrItsPeople() {
        XCTAssertEqual(OpenRoom(id: room, name: "  Thursday  study ").shownName, "Thursday study")
        let unnamed = OpenRoom(id: room, faces: [
            PublicFace(id: ruth, name: "Ruth Abel"), PublicFace(id: ann, name: "Ann"),
            PublicFace(id: me, name: "Sam Lee"), PublicFace(id: UUID(), name: "Fourth"),
        ])
        XCTAssertNil(unnamed.shownName)
        XCTAssertEqual(unnamed.firstNames, ["Ruth", "Ann", "Sam"])
    }

    func testAFoundRoomsFireIsReadByTheEngineAndNeverBanked() {
        let now = start.addingTimeInterval(3600)
        let fire = Handiwork(
            scale: .medium, coalDepth: 0.2, lastFuelAt: start, restartAt: nil, stateAtLastFuel: .burning)
        XCTAssertEqual(OpenRoom(id: room, bookID: "MRK", fire: fire).fireState(at: now), .burning)
        XCTAssertNil(OpenRoom(id: room).fireState(at: now))
    }

    // MARK: Finding a book by what someone types

    func testBooksAreFoundByTheirNames() {
        XCTAssertEqual(BookSearch.books(matching: "mark"), ["MRK"])
        XCTAssertEqual(BookSearch.books(matching: "Mark 4"), ["MRK"])
        XCTAssertEqual(BookSearch.books(matching: "mark 4:12"), ["MRK"])
        XCTAssertEqual(BookSearch.books(matching: "1 cor"), ["1CO"])
        XCTAssertEqual(BookSearch.books(matching: "PHP"), ["PHP"])
        XCTAssertTrue(BookSearch.books(matching: "jo").contains("JHN"))
        XCTAssertTrue(BookSearch.books(matching: "jo").contains("JOB"))
    }

    func testTooLittleFindsNoBook() {
        XCTAssertEqual(BookSearch.books(matching: ""), [])
        XCTAssertEqual(BookSearch.books(matching: "m"), [])
        XCTAssertEqual(BookSearch.books(matching: "Thursday study"), [])
    }

    // MARK: Older saved state

    func testAMembershipSavedBeforeRoomsCouldBeFoundHasSaidNothing() throws {
        let json = """
        {"id":"\(UUID().uuidString)","roomID":"\(room.uuidString)","personID":"\(me.uuidString)","joinedAt":0}
        """
        let decoded = try JSONDecoder().decode(Membership.self, from: Data(json.utf8))
        XCTAssertFalse(decoded.wouldOpen)
        XCTAssertNil(decoded.ink)
    }

    func testARoomSavedBeforeRoomsHadALineHasNone() throws {
        let json = """
        {"id":"\(room.uuidString)","createdAt":0}
        """
        let decoded = try JSONDecoder().decode(Room.self, from: Data(json.utf8))
        XCTAssertNil(decoded.line)
        XCTAssertFalse(decoded.isPaused)
    }
}
