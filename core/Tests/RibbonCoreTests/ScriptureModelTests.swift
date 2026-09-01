import XCTest
@testable import RibbonCore

final class ScriptureModelTests: XCTestCase {
    // A miniature of the converter's output: prose with two verses, then a
    // poetic couplet whose second line continues the verse.
    let sample = """
    {"id":"MRK","name":"Mark","chapters":[{"n":1,"blocks":[
      {"s":"p","x":[{"v":1,"t":"The beginning of the Good News."},{"v":2,"t":"As it is written,"}]},
      {"s":"q1","x":[{"t":"\\u201cBehold, I send my messenger,"}]},
      {"s":"q2","x":[{"t":"who will prepare your way.\\u201d"}]},
      {"s":"p","x":[{"v":3,"t":"He said,"},{"t":" ","w":false},{"v":4,"t":"\\u201cCome.\\u201d","w":true}]}
    ]}]}
    """

    func decoded() throws -> ScriptureBookText {
        try JSONDecoder().decode(ScriptureBookText.self, from: Data(sample.utf8))
    }

    func testDecodes() throws {
        let book = try decoded()
        XCTAssertEqual(book.id, "MRK")
        XCTAssertEqual(book.chapters.count, 1)
        XCTAssertEqual(book.chapter(1)?.blocks.count, 4)
        XCTAssertNil(book.chapter(2))
    }

    func testVerseNumbers() throws {
        let chapter = try XCTUnwrap(decoded().chapter(1))
        XCTAssertEqual(chapter.verseNumbers, [1, 2, 3, 4])
    }

    func testVerseTextSpansBlocks() throws {
        let chapter = try XCTUnwrap(decoded().chapter(1))
        XCTAssertEqual(chapter.text(forVerse: 1), "The beginning of the Good News.")
        // Verse 2 runs from the prose block through both poetic lines.
        XCTAssertEqual(
            chapter.text(forVerse: 2),
            "As it is written, “Behold, I send my messenger, who will prepare your way.”")
        XCTAssertEqual(chapter.text(forVerse: 4), "“Come.”")
    }

    func testRedLetter() throws {
        let chapter = try XCTUnwrap(decoded().chapter(1))
        let redSpans = chapter.blocks.flatMap(\.x).filter(\.isRedLetter)
        XCTAssertEqual(redSpans.map(\.t), ["“Come.”"])
    }

    func testBundledConverterOutputDecodes() throws {
        // When the converted corpus is reachable from the test's working
        // tree, decode every book of both translations end-to-end.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()  // RibbonCoreTests
            .deletingLastPathComponent()  // Tests
            .deletingLastPathComponent()  // core
            .deletingLastPathComponent()  // repo root
            .appendingPathComponent("ios/Ribbon/Resources/Scripture")
        guard FileManager.default.fileExists(atPath: root.path) else {
            throw XCTSkip("converted Scripture not present")
        }
        for translation in TranslationRegistry.bundled {
            for book in Bible.books {
                let url = root
                    .appendingPathComponent(translation.id.rawValue)
                    .appendingPathComponent("\(book.id).json")
                let data = try Data(contentsOf: url)
                let text = try JSONDecoder().decode(ScriptureBookText.self, from: data)
                XCTAssertEqual(text.chapters.count, book.chapterCount, "\(translation) \(book.id)")
                XCTAssertFalse(text.chapters.isEmpty)
            }
        }
    }
}
