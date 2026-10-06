import XCTest
@testable import RibbonCore

// Your shelf (A67): every book you have finished, in every room you have
// read in, and who each was read with — never as a count.
// YourShelfTest.kt holds the Kotlin port to the same cases.
final class YourShelfTests: XCTestCase {
    let me = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
    let ruth = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
    let caleb = UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
    let faith = UUID(uuidString: "00000000-0000-0000-0000-000000000004")!
    let hope = UUID(uuidString: "00000000-0000-0000-0000-000000000005")!

    let couple = UUID(uuidString: "00000000-0000-0000-0000-0000000000a1")!
    let study = UUID(uuidString: "00000000-0000-0000-0000-0000000000a2")!
    let left = UUID(uuidString: "00000000-0000-0000-0000-0000000000a3")!

    func day(_ n: Int) -> Date { Date(timeIntervalSince1970: 1_780_000_000 + Double(n) * 86_400) }

    func reading(_ id: Int, in room: UUID, _ book: String, finished: Int?) -> Reading {
        Reading(
            id: UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", id))!,
            roomID: room, bookID: book, startedAt: day(0),
            finishedAt: finished.map(day), handiwork: Handiwork(scale: .small))
    }

    func member(_ person: UUID, of room: UUID, joined: Int) -> Membership {
        Membership(roomID: room, personID: person, joinedAt: day(joined))
    }

    // MARK: The embers

    func testEveryFinishedBookTheFirstFinishedFirst() {
        let readings = [
            reading(1, in: couple, "MRK", finished: 30),
            reading(2, in: study, "PHM", finished: 10),
            reading(3, in: couple, "RUT", finished: nil),
            reading(5, in: study, "PHP", finished: 20),
        ]
        XCTAssertEqual(YourShelf.embers(readings: readings).map(\.bookID), ["PHM", "PHP", "MRK"])
    }

    // Leaving says "You'll keep the books on your shelf" (§6.8).
    func testARoomYouHaveLeftKeepsItsBooksOnYourShelf() {
        let readings = [reading(1, in: couple, "MRK", finished: 30), reading(4, in: left, "JON", finished: 5)]
        XCTAssertEqual(YourShelf.embers(readings: readings).map(\.bookID), ["JON", "MRK"])
        let rooms = [Room(id: couple, createdAt: day(0))]
        let members = [member(me, of: couple, joined: 0), member(ruth, of: couple, joined: 1)]
        XCTAssertEqual(
            YourShelf.company(of: reading(4, in: left, "JON", finished: 5), rooms: rooms, memberships: members, me: me),
            .alone)
    }

    func testTwoFinishedTheSameMomentKeepOneOrder() {
        let a = reading(7, in: couple, "JON", finished: 3)
        let b = reading(6, in: couple, "OBA", finished: 3)
        XCTAssertEqual(YourShelf.embers(readings: [a, b]).map(\.bookID), ["OBA", "JON"])
        XCTAssertEqual(YourShelf.embers(readings: [b, a]).map(\.bookID), ["OBA", "JON"])
    }

    func testNothingFinishedIsAnEmptyShelf() {
        XCTAssertEqual(YourShelf.embers(readings: [reading(1, in: couple, "MRK", finished: nil)]), [])
        XCTAssertEqual(YourShelf.embers(readings: []), [])
    }

    // MARK: Who it was read with

    func testTwoOfYouIsTheirName() {
        let rooms = [Room(id: couple, name: "Us", createdAt: day(0))]
        let members = [member(me, of: couple, joined: 0), member(ruth, of: couple, joined: 1)]
        XCTAssertEqual(
            YourShelf.company(of: reading(1, in: couple, "MRK", finished: 3), rooms: rooms, memberships: members, me: me),
            .people([ruth], andOthers: false))
    }

    func testANamedRoomOfThreeIsItsName() {
        let rooms = [Room(id: study, name: "  Thursday study ", createdAt: day(0))]
        let members = [
            member(caleb, of: study, joined: 2), member(me, of: study, joined: 0), member(ruth, of: study, joined: 1),
        ]
        XCTAssertEqual(
            YourShelf.company(of: reading(1, in: study, "PHP", finished: 3), rooms: rooms, memberships: members, me: me),
            .room("Thursday study"))
    }

    func testAnUnnamedRoomNamesTwoInTheOrderTheyCame() {
        let rooms = [Room(id: study, name: "   ", createdAt: day(0))]
        let three = [
            member(caleb, of: study, joined: 2), member(me, of: study, joined: 0), member(ruth, of: study, joined: 1),
        ]
        let reading = reading(1, in: study, "PHP", finished: 3)
        XCTAssertEqual(
            YourShelf.company(of: reading, rooms: rooms, memberships: three, me: me),
            .people([ruth, caleb], andOthers: false))
        let five = three + [member(hope, of: study, joined: 4), member(faith, of: study, joined: 3)]
        XCTAssertEqual(
            YourShelf.company(of: reading, rooms: rooms, memberships: five, me: me),
            .people([ruth, caleb], andOthers: true))
    }

    func testOnlyThatRoomsPeopleAreCounted() {
        let rooms = [Room(id: couple, createdAt: day(0)), Room(id: study, name: "Thursday study", createdAt: day(0))]
        let members = [
            member(me, of: couple, joined: 0), member(ruth, of: couple, joined: 1),
            member(me, of: study, joined: 0), member(caleb, of: study, joined: 1), member(faith, of: study, joined: 2),
        ]
        XCTAssertEqual(
            YourShelf.company(of: reading(1, in: couple, "MRK", finished: 3), rooms: rooms, memberships: members, me: me),
            .people([ruth], andOthers: false))
    }

    func testNobodyElseInTheRoomIsAlone() {
        let rooms = [Room(id: couple, name: "Thursday study", createdAt: day(0))]
        let members = [member(me, of: couple, joined: 0)]
        XCTAssertEqual(
            YourShelf.company(of: reading(1, in: couple, "MRK", finished: 3), rooms: rooms, memberships: members, me: me),
            .alone)
    }

    func testWithNobodySignedInEveryoneIsCompany() {
        let rooms = [Room(id: couple, createdAt: day(0))]
        let members = [member(ruth, of: couple, joined: 1)]
        XCTAssertEqual(
            YourShelf.company(of: reading(1, in: couple, "MRK", finished: 3), rooms: rooms, memberships: members, me: nil),
            .people([ruth], andOthers: false))
    }
}
