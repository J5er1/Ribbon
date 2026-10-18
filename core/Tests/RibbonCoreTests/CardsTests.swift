import XCTest
@testable import RibbonCore

// Cards the room writes (§4.6, §16.4, A74). CardsTest.kt holds the Kotlin
// port to the same answers, case for case.

final class CardsTests: XCTestCase {
    let ruth = UUID(uuidString: "00000000-0000-4000-8000-000000000002")!
    let markID = UUID(uuidString: "00000000-0000-4000-8000-0000000000b1")!
    let jamesID = UUID(uuidString: "00000000-0000-4000-8000-0000000000b2")!

    private func at(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    // MARK: Ribbon's card, the same on every phone

    func testRibbonsCardIdIsTheReadingsWithTheChapterFoldedIn() {
        XCTAssertEqual(
            ChapterCards.ribbonCardID(readingID: markID, chapter: 3),
            UUID(uuidString: "00000000-0000-8000-8000-0000000000B2")!)
        XCTAssertEqual(
            ChapterCards.ribbonCardID(readingID: markID, chapter: 150),
            UUID(uuidString: "00000000-0000-8000-8000-000000000027")!)
        XCTAssertEqual(
            ChapterCards.ribbonCardID(readingID: UUID(uuidString: "6F9619FF-8B86-4D01-B42D-00CF4FC964FF")!, chapter: 16),
            UUID(uuidString: "6F9619FF-8B86-8D01-B42D-00CF4FC964EF")!)
    }

    func testEveryChapterAndEveryReadingHasItsOwn() {
        let one = ChapterCards.ribbonCardID(readingID: markID, chapter: 1)
        XCTAssertEqual(one, ChapterCards.ribbonCardID(readingID: markID, chapter: 1))
        XCTAssertNotEqual(one, ChapterCards.ribbonCardID(readingID: markID, chapter: 2))
        XCTAssertNotEqual(one, ChapterCards.ribbonCardID(readingID: jamesID, chapter: 1))
        XCTAssertNotEqual(one, markID)
    }

    func testAStateIsSpelledAsTheServerSpellsIt() {
        XCTAssertEqual(ChapterCards.stored(.setDown), "set_down")
        XCTAssertEqual(ChapterCards.stored(.sealed), "sealed")
        XCTAssertEqual(ChapterCards.stored(.open), "open")
        XCTAssertEqual(ChapterCards.state(stored: "set_down"), .setDown)
        XCTAssertEqual(ChapterCards.state(stored: "open"), .open)
        XCTAssertNil(ChapterCards.state(stored: "gone"))
    }

    // MARK: The cards at a chapter's end

    func testRibbonsCardStandsFirstAndASetDownCardIsGone() {
        let ribbon = ReflectionCard(readingID: markID, chapter: 1, question: "Which line held you?")
        let room = ReflectionCard(
            readingID: markID, chapter: 1, question: "What would you have said to him?", byTheRoom: true, writtenBy: ruth)
        let setDown = ReflectionCard(
            readingID: markID, chapter: 1, question: "Asked and set down", state: .setDown, byTheRoom: true, writtenBy: ruth)
        let elsewhere = [
            ReflectionCard(readingID: markID, chapter: 2, question: "Another chapter"),
            ReflectionCard(readingID: jamesID, chapter: 1, question: "Another book"),
        ]
        let standing = ChapterCards.standing([room, setDown, ribbon] + elsewhere, readingID: markID, chapter: 1)
        XCTAssertEqual(standing.map(\.question), ["Which line held you?", "What would you have said to him?"])
    }

    func testTheRoomMayAskWhileNoQuestionOfItsOwnStands() {
        let ribbon = ReflectionCard(readingID: markID, chapter: 1, question: "Which line held you?")
        let room = ReflectionCard(readingID: markID, chapter: 1, question: "Ours", byTheRoom: true, writtenBy: ruth)
        var setDown = room
        setDown.state = .setDown
        XCTAssertTrue(ChapterCards.roomMayAsk([ribbon], readingID: markID, chapter: 1))
        XCTAssertFalse(ChapterCards.roomMayAsk([ribbon, room], readingID: markID, chapter: 1))
        XCTAssertTrue(ChapterCards.roomMayAsk([ribbon, setDown], readingID: markID, chapter: 1))
        XCTAssertTrue(ChapterCards.roomMayAsk([ribbon, room], readingID: markID, chapter: 2))
    }

    // MARK: A question, as it is asked

    func testAQuestionIsItsWordsRunTogether() {
        XCTAssertEqual(RoomQuestion.cleaned("  What would\n\nyou   have said? "), "What would you have said?")
        XCTAssertNil(RoomQuestion.cleaned(" \n "))
        XCTAssertEqual(RoomQuestion.cleaned(String(repeating: "a", count: 200))?.count, 200)
        XCTAssertNil(RoomQuestion.cleaned(String(repeating: "a", count: 201)))
    }

    // MARK: The shelf says whose question it was

    func testTheShelfSaysWhoAskedTheRoomsQuestion() {
        let ann = UUID(uuidString: "00000000-0000-4000-8000-000000000003")!
        let reading = Reading(
            id: markID, roomID: UUID(), bookID: "MRK", startedAt: at("2026-09-03T12:00:00Z"),
            handiwork: Handiwork(scale: .small))
        let cards = [
            ReflectionCard(
                readingID: markID, chapter: 1, question: "Ours?", answers: [ann: "Yes."], state: .open,
                byTheRoom: true, writtenBy: ruth),
            ReflectionCard(readingID: markID, chapter: 1, question: "Ribbon's?", answers: [ann: "Also."], state: .open),
        ]
        let text = ShelfExport.text(
            title: "Us", readings: [reading], notes: [], highlights: [], cards: cards,
            name: { $0 == ruth ? "Ruth" : "Ann" }, keptAt: at("2026-10-18T09:00:00Z"),
            timeZone: TimeZone(identifier: "UTC")!)
        XCTAssertEqual(text, """
        Us
        The shelf, kept 18 October 2026.

        Mark
        Begun 3 September 2026, and not finished.

        Cards

        Chapter 1: Ribbon's?
        Ann: Also.

        Chapter 1, asked by Ruth: Ours?
        Ann: Yes.

        """)
    }

    // MARK: Older cards

    func testACardKeptBeforeTheRoomCouldAskIsRibbons() throws {
        let json = """
        {"id":"00000000-0000-4000-8000-0000000000c1","readingID":"00000000-0000-4000-8000-0000000000b1",
         "chapter":1,"question":"Which line held you?","state":"sealed","answers":{}}
        """
        let card = try JSONDecoder().decode(ReflectionCard.self, from: Data(json.utf8))
        XCTAssertFalse(card.byTheRoom)
        XCTAssertNil(card.writtenBy)
    }
}
