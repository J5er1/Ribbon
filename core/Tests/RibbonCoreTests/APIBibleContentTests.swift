import XCTest
@testable import RibbonCore

final class APIBibleContentTests: XCTestCase {
    // A miniature of API.Bible's content-type=json chapter shape: prose
    // with two verses (one continuing into red letter), a section heading
    // to skip, a footnote subtree to strip, and a poetic couplet.
    let sample = """
    {"data": {"id": "MRK.1", "content": [
      {"name": "para", "attrs": {"style": "s1"},
       "items": [{"type": "text", "text": "John the Baptist Prepares the Way"}]},
      {"name": "para", "attrs": {"style": "p"}, "items": [
        {"type": "verse", "attrs": {"number": "1", "style": "v"}},
        {"type": "text", "text": "The beginning of the gospel of Jesus Christ."},
        {"type": "verse", "attrs": {"number": "2", "style": "v"}},
        {"type": "text", "text": "Jesus said, "},
        {"type": "char", "attrs": {"style": "wj"},
         "items": [{"type": "text", "text": "“Follow Me.”"}]},
        {"type": "char", "attrs": {"style": "f"},
         "items": [{"type": "text", "text": "1:2 Some manuscripts read otherwise."}]}
      ]},
      {"name": "para", "attrs": {"style": "b"}},
      {"name": "para", "attrs": {"style": "q1"}, "items": [
        {"type": "verse", "attrs": {"number": "3", "style": "v"}},
        {"type": "text", "text": "Prepare the way of the Lord,"}
      ]},
      {"name": "para", "attrs": {"style": "q2"}, "items": [
        {"type": "text", "text": "make His paths straight."}
      ]}
    ]}}
    """

    func testConvertsChapter() throws {
        let chapter = try XCTUnwrap(
            APIBibleContent.chapter(number: 1, from: Data(sample.utf8)))
        XCTAssertEqual(chapter.n, 1)

        // The heading is gone; prose, a stanza break, and two poetic lines
        // remain.
        XCTAssertEqual(chapter.blocks.map(\.s), [.p, .b, .q1, .q2])
        XCTAssertEqual(chapter.verseNumbers, [1, 2, 3])

        XCTAssertEqual(chapter.text(forVerse: 1), "The beginning of the gospel of Jesus Christ.")
        // The footnote is stripped; the red letter survives as part of v2.
        XCTAssertEqual(chapter.text(forVerse: 2), "Jesus said, “Follow Me.”")
        // Verse 3 runs across both poetic lines.
        XCTAssertEqual(chapter.text(forVerse: 3), "Prepare the way of the Lord, make His paths straight.")

        let red = chapter.blocks.flatMap(\.x).filter(\.isRedLetter)
        XCTAssertEqual(red.map(\.t), ["“Follow Me.”"])
    }

    func testMalformedPayloadIsNil() {
        XCTAssertNil(APIBibleContent.chapter(number: 1, from: Data("not json".utf8)))
        XCTAssertNil(APIBibleContent.chapter(number: 1, from: Data("{}".utf8)))
        XCTAssertNil(APIBibleContent.chapter(
            number: 1,
            from: Data(#"{"data":{"content":[{"name":"para","attrs":{"style":"s1"},"items":[{"type":"text","text":"Heading only"}]}]}}"#.utf8)))
    }

    func testRegistry() {
        XCTAssertEqual(TranslationRegistry.bundled.map(\.id), [.bsb, .web])
        XCTAssertTrue(TranslationRegistry.isBundled(.bsb))
        XCTAssertFalse(TranslationRegistry.isBundled(.nkjv))
        // NKJV is registered but unconfigured until the license lands.
        XCTAssertFalse(TranslationRegistry.nkjv.isConfigured)
        // The raw string round-trips through Codable as a bare string, so
        // stored state and the database never migrate.
        let data = try! JSONEncoder().encode(TranslationID.nkjv)
        XCTAssertEqual(String(data: data, encoding: .utf8), "\"nkjv\"")
        XCTAssertEqual(try! JSONDecoder().decode(TranslationID.self, from: data), .nkjv)
    }
}
