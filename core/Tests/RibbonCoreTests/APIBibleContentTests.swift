import XCTest
@testable import RibbonCore

final class APIBibleContentTests: XCTestCase {
    // A miniature of API.Bible's content-type=json chapter shape, mirroring
    // the live feed exactly (verse markers are tags named "verse" whose own
    // items repeat the number — that text must never leak into the page):
    // prose with two verses (one continuing into red letter), a section
    // heading to skip, a footnote subtree to strip, and a poetic couplet.
    let sample = """
    {"data": {"id": "MRK.1", "content": [
      {"name": "para", "type": "tag", "attrs": {"style": "s"},
       "items": [{"type": "text", "text": "John the Baptist Prepares the Way"}]},
      {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
        {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v", "sid": "MRK 1:1"},
         "items": [{"type": "text", "text": "1"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.1"}, "text": "The beginning of the gospel of Jesus Christ."},
        {"name": "verse", "type": "tag", "attrs": {"number": "2", "style": "v", "sid": "MRK 1:2"},
         "items": [{"type": "text", "text": "2"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.2"}, "text": "Jesus said, "},
        {"name": "char", "type": "tag", "attrs": {"style": "wj"},
         "items": [{"type": "text", "text": "“Follow Me.”"}]},
        {"name": "char", "type": "tag", "attrs": {"style": "f"},
         "items": [{"type": "text", "text": "1:2 Some manuscripts read otherwise."}]}
      ]},
      {"name": "para", "type": "tag", "attrs": {"style": "b"}},
      {"name": "para", "type": "tag", "attrs": {"style": "q1"}, "items": [
        {"name": "verse", "type": "tag", "attrs": {"number": "3", "style": "v", "sid": "MRK 1:3"},
         "items": [{"type": "text", "text": "3"}]},
        {"type": "text", "attrs": {"verseId": "MRK.1.3"}, "text": "Prepare the way of the Lord,"}
      ]},
      {"name": "para", "type": "tag", "attrs": {"style": "q2"}, "items": [
        {"type": "text", "attrs": {"verseId": "MRK.1.3"}, "text": "make His paths straight."}
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
        XCTAssertEqual(TranslationRegistry.licensed.map(\.id), [.nkjv, .niv, .nasb])
        XCTAssertTrue(TranslationRegistry.isBundled(.bsb))
        XCTAssertFalse(TranslationRegistry.isBundled(.nkjv))
        // All three licensed editions are live on the account and carry
        // their catalog ids.
        for translation in TranslationRegistry.licensed {
            XCTAssertTrue(translation.isConfigured, translation.id.rawValue)
            XCTAssertTrue(translation.redLetter)
        }
        // The raw string round-trips through Codable as a bare string, so
        // stored state and the database never migrate.
        let data = try! JSONEncoder().encode(TranslationID.nkjv)
        XCTAssertEqual(String(data: data, encoding: .utf8), "\"nkjv\"")
        XCTAssertEqual(try! JSONDecoder().decode(TranslationID.self, from: data), .nkjv)
    }
}
