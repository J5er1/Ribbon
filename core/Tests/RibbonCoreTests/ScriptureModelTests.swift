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

    // A psalm's shape: a title, a stanza break, then a verse whose second
    // poetic line is glued to the first with no space. And Zechariah 12's: a
    // title that carries the verse number the next paragraph continues.
    let psalm = """
    {"n":3,"blocks":[
      {"s":"d","x":[{"t":"A Psalm of David."}]},
      {"s":"b","x":[]},
      {"s":"q1","x":[{"v":1,"t":"O LORD, how my foes have increased!"}]},
      {"s":"q2","x":[{"t":"How many rise up against me!"}]},
      {"s":"q1","x":[{"v":2,"t":"Many say of me, "},{"t":"\\u201cGod will not deliver him.\\u201d"}]}
    ]}
    """

    let burden = """
    {"n":12,"blocks":[
      {"s":"d","x":[{"v":1,"t":"This is the burden of the word of the LORD."}]},
      {"s":"b","x":[]},
      {"s":"m","x":[{"t":"Thus declares the LORD."}]},
      {"s":"p","x":[{"v":2,"t":"Behold. "}]}
    ]}
    """

    func testOwnTextGluesSpans() throws {
        let chapter = try XCTUnwrap(decoded().chapter(1))
        let texts = chapter.ownTexts()
        XCTAssertEqual(texts[1], "The beginning of the Good News.")
        XCTAssertEqual(texts[2], "As it is written,\u{201C}Behold, I send my messenger,who will prepare your way.\u{201D}")
        XCTAssertEqual(texts[3], "He said, ")
        XCTAssertEqual(texts[4], "\u{201C}Come.\u{201D}")
        XCTAssertEqual(chapter.ownText(verse: 4), "\u{201C}Come.\u{201D}")
        XCTAssertNil(chapter.ownText(verse: 5))
        XCTAssertEqual(chapter.ownSpanBreaks()[2], [17, 46])
        XCTAssertEqual(chapter.ownSpanBreaks()[3], [8])
        XCTAssertEqual(chapter.ownSpanBreaks()[1], [])
    }

    func testOwnTextLeavesOutTitles() throws {
        let chapter = try JSONDecoder().decode(ScriptureChapter.self, from: Data(psalm.utf8))
        let texts = chapter.ownTexts()
        XCTAssertEqual(Set(texts.keys), [1, 2])
        XCTAssertEqual(texts[1], "O LORD, how my foes have increased!How many rise up against me!")
        XCTAssertEqual(texts[1]?.utf16.count, 63)
        // Offsets count UTF-16 units: the curly quotes are one each.
        XCTAssertEqual(texts[2], "Many say of me, \u{201C}God will not deliver him.\u{201D}")
        XCTAssertEqual(texts[2]?.utf16.count, 43)
        XCTAssertEqual(chapter.ownSpanBreaks()[1], [35])
        XCTAssertEqual(chapter.ownSpanBreaks()[2], [16])
    }

    func testOwnTextFollowsAVerseNumberOnATitle() throws {
        let chapter = try JSONDecoder().decode(ScriptureChapter.self, from: Data(burden.utf8))
        XCTAssertEqual(chapter.ownTexts(), [1: "Thus declares the LORD.", 2: "Behold. "])
    }

    // MARK: A new line for every verse (A68)

    func testVerseLinesBreakBeforeEachVerseInProse() {
        let block = ScriptureBlock(s: .m, x: [
            ScriptureSpan(v: 1, t: "The beginning of the Good News. "),
            ScriptureSpan(v: 2, t: "As it is written. "),
            ScriptureSpan(v: 3, t: "He said. "),
        ])
        XCTAssertEqual(block.verseLineStarts(), [1, 2])
        // An indented paragraph breaks the same way.
        XCTAssertEqual(ScriptureBlock(s: .p, x: block.x).verseLineStarts(), [1, 2])
    }

    func testVerseLinesNeverBreakBeforeABlocksFirstSpan() {
        // A paragraph that opens in the middle of a verse breaks only where
        // the next verse begins: its first line is already a line.
        let midVerse = ScriptureBlock(s: .p, x: [
            ScriptureSpan(t: "who will prepare your way. "),
            ScriptureSpan(v: 5, t: "He said. "),
        ])
        XCTAssertEqual(midVerse.verseLineStarts(), [1])
        // A span with no number continues its verse, red letter or not.
        let words = ScriptureBlock(s: .p, x: [
            ScriptureSpan(v: 3, t: "He said,"),
            ScriptureSpan(t: " ", w: false),
            ScriptureSpan(v: 4, t: "\u{201C}Come.\u{201D}", w: true),
        ])
        XCTAssertEqual(words.verseLineStarts(), [2])
        XCTAssertEqual(ScriptureBlock(s: .m, x: [ScriptureSpan(v: 1, t: "One verse.")]).verseLineStarts(), [])
        XCTAssertEqual(ScriptureBlock(s: .m, x: []).verseLineStarts(), [])
    }

    func testVerseLinesLeavePoetryAndTitlesAlone() {
        let spans = [ScriptureSpan(v: 1, t: "O LORD, "), ScriptureSpan(v: 2, t: "how many rise up!")]
        for style in [BlockStyle.q1, .q2, .d, .b] {
            XCTAssertEqual(ScriptureBlock(s: style, x: spans).verseLineStarts(), [], "\(style)")
        }
    }

    func testVerseLinesInBundledMark1() throws {
        // The Berean Standard's Mark 1, as the page is given it: verses 1
        // and 2 share a paragraph, Isaiah's words follow as poetry with
        // verse 3 opening a line of it, and 6–8 share another paragraph.
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("ios/Ribbon/Resources/Scripture/bsb/MRK.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("converted Scripture not present")
        }
        let text = try JSONDecoder().decode(ScriptureBookText.self, from: Data(contentsOf: url))
        let blocks = try XCTUnwrap(text.chapter(1)).blocks
        func numbersStartingLines(_ block: ScriptureBlock) -> [Int?] {
            block.verseLineStarts().map { block.x[$0].v }
        }

        XCTAssertEqual(blocks[0].s, .m)
        XCTAssertEqual(numbersStartingLines(blocks[0]), [2])
        let baptist = try XCTUnwrap(blocks.first { $0.x.first?.v == 6 })
        XCTAssertEqual(numbersStartingLines(baptist), [7, 8])
        // Poetry keeps its own lines, the one verse 3 opens among them.
        XCTAssertTrue(blocks.contains { $0.s == .q1 && $0.x.first?.v == 3 })
        for block in blocks where block.s == .q1 || block.s == .q2 {
            XCTAssertEqual(block.verseLineStarts(), [])
        }
    }

    func testBundledOwnTextLengths() throws {
        // The own text is what a mark's offsets and the bundled word links
        // count in, so two known lengths pin it to the committed text.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("ios/Ribbon/Resources/Scripture/bsb")
        guard FileManager.default.fileExists(atPath: root.path) else {
            throw XCTSkip("converted Scripture not present")
        }
        func own(_ book: String, _ chapter: Int, _ verse: Int) throws -> String? {
            let data = try Data(contentsOf: root.appendingPathComponent("\(book).json"))
            let text = try JSONDecoder().decode(ScriptureBookText.self, from: data)
            return text.chapter(chapter)?.ownText(verse: verse)
        }
        XCTAssertEqual(try own("PSA", 3, 1)?.utf16.count, 63)
        XCTAssertEqual(try own("JHN", 1, 1)?.utf16.count, 80)
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
