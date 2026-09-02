import XCTest
@testable import RibbonCore

final class CardQuestionsTests: XCTestCase {
    /// The register (brief §12, §10): second person, a question, short, no
    /// exclamation points, none of the never-words.
    func testEveryQuestionKeepsTheVoice() {
        let never = ["streak", "break the chain", "accountability", "engagement", "daily challenge",
                     "crush it", "level up", "unlock", "reward", "devotional", "spiritual walk",
                     "community", "you missed", "back on track", "!"]
        for question in CardQuestions.allQuestions {
            XCTAssertTrue(question.hasSuffix("?"), "Not a question: \(question)")
            XCTAssertLessThan(question.count, 160, "Too long: \(question)")
            for word in never {
                XCTAssertFalse(question.lowercased().contains(word), "'\(word)' in: \(question)")
            }
        }
    }

    func testStarterBooksCarryCardsAndOthersDoNot() {
        XCTAssertNotNil(CardQuestions.question(bookID: "MRK", chapter: 1))
        XCTAssertNotNil(CardQuestions.question(bookID: "MRK", chapter: 16))
        XCTAssertNil(CardQuestions.question(bookID: "MRK", chapter: 17))
        XCTAssertNotNil(CardQuestions.question(bookID: "RUT", chapter: 4))
        XCTAssertNotNil(CardQuestions.question(bookID: "PHP", chapter: 4))
        XCTAssertNotNil(CardQuestions.question(bookID: "JHN", chapter: 21))
        XCTAssertNil(CardQuestions.question(bookID: "GEN", chapter: 1))
        XCTAssertNil(CardQuestions.question(bookID: "ISA", chapter: 40))
    }

    func testPsalmsAreDeterministic() {
        XCTAssertEqual(CardQuestions.question(bookID: "PSA", chapter: 23),
                       "You've probably heard this one before. What did you notice this time?")
        let a = CardQuestions.question(bookID: "PSA", chapter: 2)
        let b = CardQuestions.question(bookID: "PSA", chapter: 2)
        XCTAssertEqual(a, b)
        XCTAssertNotNil(CardQuestions.question(bookID: "PSA", chapter: 150))
        XCTAssertNil(CardQuestions.question(bookID: "PSA", chapter: 0))
    }
}
