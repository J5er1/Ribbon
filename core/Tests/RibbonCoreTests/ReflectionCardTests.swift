import XCTest
@testable import RibbonCore

final class ReflectionCardTests: XCTestCase {
    func testReflectionCardJSONRoundTrip() throws {
        let cardID = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
        let readingID = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
        let authorID1 = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!
        let authorID2 = UUID(uuidString: "44444444-4444-4444-4444-444444444444")!

        let card = ReflectionCard(
            id: cardID,
            readingID: readingID,
            chapter: 4,
            question: "What did you notice that the other one probably didn't?",
            answers: [
                authorID1: "The silence at the end of the storm.",
                authorID2: "How quickly fear became awe."
            ],
            state: .open,
            openedAt: Date(timeIntervalSince1970: 1700000000)
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(card)
        let jsonObject = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        XCTAssertNotNil(jsonObject)

        // Answers must be encoded as a JSON dictionary object with lowercase UUID keys, not an alternating array
        let answersObj = jsonObject?["answers"] as? [String: String]
        XCTAssertNotNil(answersObj, "answers should be encoded as a dictionary")
        XCTAssertEqual(answersObj?[authorID1.uuidString.lowercased()], "The silence at the end of the storm.")
        XCTAssertEqual(answersObj?[authorID2.uuidString.lowercased()], "How quickly fear became awe.")

        // Decode back
        let decoder = JSONDecoder()
        let decoded = try decoder.decode(ReflectionCard.self, from: data)
        XCTAssertEqual(decoded.id, cardID)
        XCTAssertEqual(decoded.readingID, readingID)
        XCTAssertEqual(decoded.chapter, 4)
        XCTAssertEqual(decoded.question, card.question)
        XCTAssertEqual(decoded.state, .open)
        XCTAssertEqual(decoded.answers[authorID1], "The silence at the end of the storm.")
        XCTAssertEqual(decoded.answers[authorID2], "How quickly fear became awe.")
    }

    func testReflectionPrompts() {
        let prompt1 = ReflectionPrompts.prompt(for: 1)
        let prompt2 = ReflectionPrompts.prompt(for: 2)
        XCTAssertFalse(prompt1.isEmpty)
        XCTAssertFalse(prompt2.isEmpty)
        XCTAssertNotEqual(prompt1, prompt2)
    }
}
