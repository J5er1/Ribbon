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

    // The live NKJV sets the divine name in a small-caps character style:
    // {"style":"sc"} around "Lord" (Psalm 3:1, "LORD, how they have
    // increased"). Read plain it was "Lord", which on the page is Adonai.
    func testSmallCapsDivineNameIsCapitals() throws {
        let json = #"""
        {"data": {"content": [
          {"name": "para", "type": "tag", "attrs": {"style": "q1"}, "items": [
            {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v"},
             "items": [{"type": "text", "text": "1"}]},
            {"name": "char", "type": "tag", "attrs": {"style": "sc"},
             "items": [{"type": "text", "text": "Lord"}]},
            {"type": "text", "text": ", how they have increased who trouble me! The Lord "},
            {"name": "char", "type": "tag", "attrs": {"style": "nd"},
             "items": [{"type": "text", "text": "God"}]},
            {"type": "text", "text": " is a shield."}
          ]}
        ]}}
        """#
        let chapter = try XCTUnwrap(APIBibleContent.chapter(number: 3, from: Data(json.utf8)))
        let text = try XCTUnwrap(chapter.ownText(verse: 1))
        XCTAssertEqual(text, "LORD, how they have increased who trouble me! The Lord GOD is a shield.")
        // Capitals only: the length, and so every offset a mark keeps, stands.
        XCTAssertEqual(APIBibleContent.asCapitals("Lord’s é").utf16.count, "Lord’s é".utf16.count)
        XCTAssertEqual(APIBibleContent.asCapitals("Lord’s é"), "LORD’S é")
    }

    // A verse marker that closes a paragraph, its words in the next: the
    // number must not be lost, or the verse folds into the one before.
    func testVerseMarkerAtParagraphEndKeepsItsNumber() throws {
        let json = #"""
        {"data": {"content": [
          {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
            {"name": "verse", "type": "tag", "attrs": {"number": "4", "style": "v"},
             "items": [{"type": "text", "text": "4"}]},
            {"type": "text", "text": "Four. "},
            {"name": "verse", "type": "tag", "attrs": {"number": "5", "style": "v"},
             "items": [{"type": "text", "text": "5"}]},
            {"type": "text", "text": " "}
          ]},
          {"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
            {"type": "text", "text": "Five."}
          ]}
        ]}}
        """#
        let chapter = try XCTUnwrap(APIBibleContent.chapter(number: 1, from: Data(json.utf8)))
        XCTAssertEqual(chapter.verseNumbers, [4, 5])
        // Its trailing space stays, as a verse running into the next one keeps it.
        XCTAssertEqual(chapter.ownText(verse: 4), "Four. ")
        XCTAssertEqual(chapter.ownText(verse: 5), "Five.")
    }

    // A licensed edition sends its copyright line with every chapter, and
    // the page shows it under the chapter (A64).
    func testCarriesTheEditionsCopyright() throws {
        let json = #"""
        {"data": {"copyright": " New King James Version®, Copyright© 1982, Thomas Nelson. All rights reserved. ",
         "content": [{"name": "para", "type": "tag", "attrs": {"style": "p"}, "items": [
           {"name": "verse", "type": "tag", "attrs": {"number": "1", "style": "v"},
            "items": [{"type": "text", "text": "1"}]},
           {"type": "text", "text": "In the beginning."}]}]}}
        """#
        let chapter = try XCTUnwrap(APIBibleContent.chapter(number: 1, from: Data(json.utf8)))
        XCTAssertEqual(
            chapter.copyright, "New King James Version®, Copyright© 1982, Thomas Nelson. All rights reserved.")
        let plain = try XCTUnwrap(APIBibleContent.chapter(number: 1, from: Data(sample.utf8)))
        XCTAssertNil(plain.copyright)
        // A bundled chapter says nothing about it when stored.
        let stored = String(decoding: try JSONEncoder().encode(plain), as: UTF8.self)
        XCTAssertFalse(stored.contains("copyright"))
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
