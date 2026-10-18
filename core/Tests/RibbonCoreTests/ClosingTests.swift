import XCTest
@testable import RibbonCore

// Closing a room (§6.8, A73). ClosingTest.kt holds the Kotlin port to the
// same answers, case for case.

final class ClosingTests: XCTestCase {
    let me = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!
    let ruth = UUID(uuidString: "00000000-0000-4000-8000-000000000002")!
    let ann = UUID(uuidString: "00000000-0000-4000-8000-000000000003")!
    let roomID = UUID(uuidString: "00000000-0000-4000-8000-0000000000aa")!
    let markID = UUID(uuidString: "00000000-0000-4000-8000-0000000000b1")!
    let jamesID = UUID(uuidString: "00000000-0000-4000-8000-0000000000b2")!

    private func at(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    private func room(closedAt: Date? = nil) -> Room {
        Room(id: roomID, name: "Thursday study", createdAt: at("2026-09-01T12:00:00Z"), closedAt: closedAt)
    }

    private func membership(_ person: UUID, closing: Bool, joined: String = "2026-09-01T12:00:00Z") -> Membership {
        Membership(roomID: roomID, personID: person, joinedAt: at(joined), wouldClose: closing)
    }

    // MARK: Where a room stands

    func testYouHaveNotAsked() {
        XCTAssertEqual(RoomClosing.of(room(), [membership(me, closing: false), membership(ruth, closing: true)], me: me), .open)
    }

    func testYouHaveAskedAndAreWaiting() {
        XCTAssertEqual(RoomClosing.of(room(), [membership(me, closing: true), membership(ruth, closing: false)], me: me), .waitingForOthers)
    }

    func testAClosedRoomIsClosedWhateverAnyoneSaid() {
        let closed = room(closedAt: at("2026-10-18T09:00:00Z"))
        XCTAssertEqual(RoomClosing.of(closed, [membership(me, closing: false)], me: me), .closed)
    }

    func testARoomYouAreNotInIsOpenToYou() {
        XCTAssertEqual(RoomClosing.of(room(), [membership(ruth, closing: true)], me: me), .open)
    }

    func testYourYesWaitsWhileSomeoneElseHasNotSaidIt() {
        let ms = [membership(me, closing: true), membership(ruth, closing: true), membership(ann, closing: false)]
        XCTAssertFalse(RoomClosing.closesOnYourYes(room(), ms, me: me, quiet: false))
    }

    func testYourYesClosesItWhenEveryoneElseHasSaidIt() {
        let ms = [membership(me, closing: false), membership(ruth, closing: true), membership(ann, closing: true)]
        XCTAssertTrue(RoomClosing.closesOnYourYes(room(), ms, me: me, quiet: false))
        XCTAssertTrue(RoomClosing.closesOnYourYes(room(), [membership(me, closing: true)], me: me, quiet: false), "a room of one")
    }

    func testYourYesClosesAQuietRoomAndNotAClosedOne() {
        let ms = [membership(me, closing: true), membership(ruth, closing: false)]
        XCTAssertTrue(RoomClosing.closesOnYourYes(room(), ms, me: me, quiet: true))
        XCTAssertFalse(RoomClosing.closesOnYourYes(room(closedAt: at("2026-10-18T09:00:00Z")), ms, me: me, quiet: true))
    }

    // MARK: Thirty quiet days

    func testTheLastThingHeardIsTheLatestOfAnything() {
        let fire = Handiwork(
            scale: .medium, coalDepth: 0.1, lastFuelAt: at("2026-09-20T08:00:00Z"), restartAt: nil,
            stateAtLastFuel: .burning)
        let reading = Reading(id: markID, roomID: roomID, bookID: "MRK", startedAt: at("2026-09-03T12:00:00Z"), handiwork: fire)
        let note = Note(
            readingID: markID, authorID: ann, verse: VerseAddress(bookID: "MRK", chapter: 1, verse: 1),
            kind: .written, body: "Later", createdAt: at("2026-09-25T08:00:00Z"))
        let elsewhere = Note(
            readingID: UUID(), authorID: ann, verse: VerseAddress(bookID: "MRK", chapter: 1, verse: 1),
            kind: .written, body: "Another room", createdAt: at("2026-10-15T08:00:00Z"))
        let heard = RoomSilence.lastHeard(
            room: room(), memberships: [membership(me, closing: false)], readings: [reading],
            notes: [note, elsewhere], highlights: [])
        XCTAssertEqual(heard, at("2026-09-25T08:00:00Z"))
    }

    func testThirtyDaysIsQuietAndTwentyNineIsNot() {
        let heard = at("2026-09-01T12:00:00Z")
        XCTAssertFalse(RoomSilence.isQuiet(lastHeard: heard, at: at("2026-09-30T12:00:00Z")))
        XCTAssertTrue(RoomSilence.isQuiet(lastHeard: heard, at: at("2026-10-02T12:00:00Z")))
    }

    // MARK: The shelf, kept

    func testTheFileIsNamedForTheRoom() {
        XCTAssertEqual(ShelfExport.fileName(title: "Thursday study"), "Ribbon - Thursday study.txt")
        XCTAssertEqual(ShelfExport.fileName(title: "A/B: c"), "Ribbon - A-B- c.txt")
        XCTAssertEqual(ShelfExport.fileName(title: "   "), "Ribbon - A room.txt")
    }

    func testTheWholeShelfReadsAsItWasRead() {
        let readings = [
            Reading(id: jamesID, roomID: roomID, bookID: "JAS", startedAt: at("2026-10-13T12:00:00Z"), handiwork: Handiwork(scale: .small)),
            Reading(
                id: markID, roomID: roomID, bookID: "MRK", startedAt: at("2026-09-03T12:00:00Z"),
                finishedAt: at("2026-10-12T12:00:00Z"), handiwork: Handiwork(scale: .medium)),
        ]
        let notes = [
            Note(readingID: markID, authorID: ann, verse: VerseAddress(bookID: "MRK", chapter: 4, verse: 9),
                 kind: .written, body: "Ears to hear.", createdAt: at("2026-09-10T12:00:00Z")),
            Note(readingID: markID, authorID: ruth, verse: VerseAddress(bookID: "MRK", chapter: 1, verse: 1),
                 kind: .voice, transcript: "Read it slowly.", createdAt: at("2026-09-05T12:00:00Z")),
            Note(readingID: markID, authorID: ruth, verse: VerseAddress(bookID: "MRK", chapter: 1, verse: 1),
                 kind: .written, body: "Beginning again.", createdAt: at("2026-09-04T12:00:00Z")),
        ]
        let highlights = [
            Highlight(readingID: markID, authorID: ann, range: VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 9),
                      ink: .teal, createdAt: at("2026-09-10T12:00:00Z")),
            Highlight(readingID: markID, authorID: ruth, range: VerseRange(bookID: "MRK", chapter: 1, startVerse: 1, endVerse: 3),
                      ink: .ochre, createdAt: at("2026-09-04T12:00:00Z")),
        ]
        let cards = [
            ReflectionCard(readingID: markID, chapter: 2, question: "Still sealed?", answers: [ruth: "Not yet read by Ann"], state: .sealed),
            ReflectionCard(
                readingID: markID, chapter: 1, question: "What stayed with you?",
                answers: [ruth: "The voice in the wilderness.", ann: "Straightway."], state: .open),
        ]
        let names = [ruth: "Ruth", ann: "Ann"]
        let text = ShelfExport.text(
            title: "Thursday study", readings: readings, notes: notes, highlights: highlights, cards: cards,
            name: { names[$0] ?? "Someone" }, keptAt: at("2026-10-18T09:00:00Z"),
            timeZone: TimeZone(identifier: "UTC")!)
        XCTAssertEqual(text, """
        Thursday study
        The shelf, kept 18 October 2026.

        Mark
        Read from 3 September 2026 to 12 October 2026.

        Notes

        Mark 1:1, Ruth, 4 September 2026
        Beginning again.

        Mark 1:1, Ruth, 5 September 2026
        A voice note: \u{201C}Read it slowly.\u{201D}

        Mark 4:9, Ann, 10 September 2026
        Ears to hear.

        Marks

        Mark 1:1–3, Ruth
        Mark 4:9, Ann

        Cards

        Chapter 1: What stayed with you?
        Ann: Straightway.
        Ruth: The voice in the wilderness.

        James
        Begun 13 October 2026, and not finished.

        """)
    }

    func testAnEmptyShelfSaysSo() {
        let text = ShelfExport.text(
            title: "Our room", readings: [], notes: [], highlights: [], cards: [],
            name: { _ in "Someone" }, keptAt: at("2026-10-18T09:00:00Z"), timeZone: TimeZone(identifier: "UTC")!)
        XCTAssertEqual(text, "Our room\nThe shelf, kept 18 October 2026.\n\nNothing was read here.\n")
    }

    func testTheDayIsTheDayWhereYouAre() {
        let late = at("2026-10-18T23:30:00Z")
        XCTAssertEqual(ShelfExport.day(late, TimeZone(identifier: "UTC")!), "18 October 2026")
        XCTAssertEqual(ShelfExport.day(late, TimeZone(identifier: "Australia/Sydney")!), "19 October 2026")
    }

    // MARK: Older saved state

    func testOlderRoomsAndMembershipsHaveSaidNothingAboutClosing() throws {
        let membership = try JSONDecoder().decode(Membership.self, from: Data("""
        {"id":"\(UUID().uuidString)","roomID":"\(roomID.uuidString)","personID":"\(me.uuidString)","joinedAt":0}
        """.utf8))
        XCTAssertFalse(membership.wouldClose)
        let room = try JSONDecoder().decode(Room.self, from: Data("""
        {"id":"\(roomID.uuidString)","createdAt":0}
        """.utf8))
        XCTAssertNil(room.closedAt)
    }
}
