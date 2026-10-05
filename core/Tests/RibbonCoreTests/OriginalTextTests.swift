import XCTest
@testable import RibbonCore

// The original words, the word links, and the moves between a version's own
// text and the words under it (A60). OriginalTextTest.kt holds the Kotlin
// core to the same cases, name for name.

final class OriginalTextTests: XCTestCase {
    // MARK: Fixtures

    // John 1:1 in the Berean Standard, with links of the bundled shape. The
    // Greek: Ἐν(0) ἀρχῇ(1) ἦν(2) ὁ(3) λόγος(4) καὶ(5) ὁ(6) λόγος(7) ἦν(8)
    // πρὸς(9) τὸν(10) θεόν(11) καὶ(12) θεὸς(13) ἦν(14) ὁ(15) λόγος(16).
    // τὸν, the article in "with God", is rendered by no English word.
    let john1 = "In the beginning was the Word, and the Word was with God, and the Word was God. "
    let john1Links: [AlignmentLink] = [
        AlignmentLink(start: 0, end: 2, words: [0]),
        AlignmentLink(start: 3, end: 16, words: [1]),
        AlignmentLink(start: 17, end: 20, words: [2]),
        AlignmentLink(start: 21, end: 29, words: [3, 4]),
        AlignmentLink(start: 31, end: 34, words: [5]),
        AlignmentLink(start: 35, end: 43, words: [6, 7]),
        AlignmentLink(start: 44, end: 47, words: [8]),
        AlignmentLink(start: 48, end: 52, words: [9]),
        AlignmentLink(start: 53, end: 56, words: [11]),
        AlignmentLink(start: 58, end: 61, words: [12]),
        AlignmentLink(start: 62, end: 70, words: [15, 16]),
        AlignmentLink(start: 71, end: 74, words: [14]),
        AlignmentLink(start: 75, end: 78, words: [13]),
    ]

    // John 1:2: οὗτος(0) ἦν(1) ἐν(2) ἀρχῇ(3) πρὸς(4) τὸν(5) θεόν(6).
    let john2 = "He was with God in the beginning. "
    let john2Links: [AlignmentLink] = [
        AlignmentLink(start: 0, end: 2, words: [0]),
        AlignmentLink(start: 3, end: 6, words: [1]),
        AlignmentLink(start: 7, end: 11, words: [4]),
        AlignmentLink(start: 12, end: 15, words: [6]),
        AlignmentLink(start: 16, end: 18, words: [2]),
        AlignmentLink(start: 19, end: 32, words: [3]),
    ]

    // A made-up verse in two versions, the same in verses 1 to 3. The
    // original: ὁ(0) θεὸς(1) ἀγάπη(2) ἐστίν(3).
    let authorText = "God is love. "
    let authorLinks: [AlignmentLink] = [
        AlignmentLink(start: 0, end: 3, words: [1]),
        AlignmentLink(start: 4, end: 6, words: [3]),
        AlignmentLink(start: 7, end: 11, words: [2]),
    ]
    let readerText = "Love, that is God. "
    let readerLinks: [AlignmentLink] = [
        AlignmentLink(start: 0, end: 4, words: [2]),
        AlignmentLink(start: 11, end: 13, words: [3]),
        AlignmentLink(start: 14, end: 17, words: [1]),
    ]

    var authorByVerse: [Int: [AlignmentLink]] { [1: authorLinks, 2: authorLinks, 3: authorLinks] }
    var readerByVerse: [Int: [AlignmentLink]] { [1: readerLinks, 2: readerLinks, 3: readerLinks] }
    var readerTexts: [Int: String] { [1: readerText, 2: readerText, 3: readerText] }

    func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }

    func strings(_ data: Data) throws -> [String] {
        try JSONDecoder().decode([String].self, from: data)
    }

    // MARK: Decoding

    func testOriginalBookDecodes() throws {
        let json = """
        {"id":"JHN","source":"bsbt-1a2b3c4d","chapters":[{"n":1,"verses":[{"v":1,"w":[\
        ["Ἐν","En","G1722","Prep"],["ἀρχῇ","archē","G746","N-DFS"],["ἦν","ēn","G1510","V-IIA-3S"]]}]}]}
        """
        let book = try decode(OriginalBook.self, json)
        XCTAssertEqual(book.id, "JHN")
        XCTAssertEqual(book.source, "bsbt-1a2b3c4d")
        let chapter = try XCTUnwrap(book.chapter(1))
        XCTAssertNil(book.chapter(2))
        let words = try XCTUnwrap(chapter.words(verse: 1))
        XCTAssertNil(chapter.words(verse: 2))
        XCTAssertEqual(words.count, 3)
        XCTAssertEqual(words[1], OriginalWord(text: "ἀρχῇ", translit: "archē", strongs: "G746", parse: "N-DFS"))
        XCTAssertEqual(words[2].strongs, "G1510")
        XCTAssertEqual(words[2].parse, "V-IIA-3S")
        XCTAssertFalse(words[0].isAramaic)
    }

    func testOriginalWordEmptyStringsAreNil() throws {
        let word = try decode(OriginalWord.self, #"["בְּ","bə","",""]"#)
        XCTAssertEqual(word.text, "בְּ")
        XCTAssertEqual(word.translit, "bə")
        XCTAssertNil(word.strongs)
        XCTAssertNil(word.parse)
        XCTAssertFalse(word.isAramaic)
        XCTAssertEqual(word, OriginalWord(text: "בְּ", translit: "bə", strongs: "", parse: ""))
    }

    func testOriginalWordLanguage() throws {
        let aramaic = try decode(OriginalWord.self, #"["אֱלָהּ","’ĕ·lāh","H426","N-ms","a"]"#)
        XCTAssertTrue(aramaic.isAramaic)
        XCTAssertEqual(aramaic.language(in: "DAN"), .aramaic)
        let hebrew = try decode(OriginalWord.self, #"["אֱלֹהִים","’ĕ·lō·hîm","H430","N-mp"]"#)
        XCTAssertEqual(hebrew.language(in: "DAN"), .hebrew)
        XCTAssertEqual(hebrew.language(in: "GEN"), .hebrew)
        XCTAssertEqual(hebrew.language(in: "MAL"), .hebrew)
        let greek = try decode(OriginalWord.self, #"["λόγος","logos","G3056","N-NMS"]"#)
        XCTAssertEqual(greek.language(in: "MAT"), .greek)
        XCTAssertEqual(greek.language(in: "REV"), .greek)
        XCTAssertEqual(OriginalWords.language(of: "JHN"), .greek)
        XCTAssertEqual(OriginalWords.language(of: "PSA"), .hebrew)
        XCTAssertEqual(OriginalLanguage.aramaic.rawValue, "aramaic")
    }

    func testOriginalWordRoundTrips() throws {
        let plain = OriginalWord(text: "λόγος", translit: "logos", strongs: "G3056", parse: "N-NMS")
        let plainData = try JSONEncoder().encode(plain)
        XCTAssertEqual(try strings(plainData), ["λόγος", "logos", "G3056", "N-NMS"])
        XCTAssertEqual(try JSONDecoder().decode(OriginalWord.self, from: plainData), plain)

        let aramaic = OriginalWord(text: "מַלְכָּא", translit: "mal·kā", strongs: nil, parse: nil, isAramaic: true)
        let aramaicData = try JSONEncoder().encode(aramaic)
        XCTAssertEqual(try strings(aramaicData), ["מַלְכָּא", "mal·kā", "", "", "a"])
        XCTAssertEqual(try JSONDecoder().decode(OriginalWord.self, from: aramaicData), aramaic)
    }

    func testLexiconLookup() throws {
        let json = #"{"G3056":["λόγος","lógos","something said"],"H430":["אֱלֹהִים","ʼĕlôhîym","gods"]}"#
        let lexicon = try decode(Lexicon.self, json)
        XCTAssertEqual(lexicon.entries.count, 2)
        XCTAssertEqual(lexicon.entry("G3056"), LexiconEntry(lemma: "λόγος", translit: "lógos", definition: "something said"))
        XCTAssertEqual(lexicon.entry("H430")?.definition, "gods")
        XCTAssertEqual(lexicon.entry("g03056")?.lemma, "λόγος")
        XCTAssertEqual(lexicon.entry("H0430")?.lemma, "אֱלֹהִים")
        XCTAssertNil(lexicon.entry("G9999"))
        XCTAssertNil(lexicon.entry(""))
        XCTAssertNil(lexicon.entry("3056"))
        XCTAssertNil(lexicon.entry("G"))
        let again = try JSONDecoder().decode(Lexicon.self, from: JSONEncoder().encode(lexicon))
        XCTAssertEqual(again, lexicon)
    }

    func testParsingsDescribe() throws {
        let parsings = try decode(Parsings.self, #"{"N-DFS":"Noun - Dative Feminine Singular","X":""}"#)
        XCTAssertEqual(parsings.describe("N-DFS"), "Noun - Dative Feminine Singular")
        XCTAssertNil(parsings.describe(""))
        XCTAssertNil(parsings.describe("X"))
        XCTAssertNil(parsings.describe("V-PAI-3S"))
    }

    func testAlignmentDecodes() throws {
        let json = """
        {"id":"JHN","translation":"bsb","source":"bsbt-1a2b3c4d","basis":"9f8e7d6c5b4a",\
        "chapters":[{"n":1,"verses":[{"v":1,"l":[[0,2,[0]],[3,16,[1]]]},{"v":2,"l":[]}]}]}
        """
        let alignment = try decode(BookAlignment.self, json)
        XCTAssertEqual(alignment.id, "JHN")
        XCTAssertEqual(alignment.translation, .bsb)
        XCTAssertEqual(alignment.source, "bsbt-1a2b3c4d")
        XCTAssertEqual(alignment.basis, "9f8e7d6c5b4a")
        let chapter = try XCTUnwrap(alignment.chapter(1))
        XCTAssertNil(alignment.chapter(2))
        let expected = [AlignmentLink(start: 0, end: 2, words: [0]), AlignmentLink(start: 3, end: 16, words: [1])]
        XCTAssertEqual(chapter.links(verse: 1), expected)
        XCTAssertEqual(chapter.links(verse: 2), [])
        XCTAssertNil(chapter.links(verse: 3))
        XCTAssertEqual(chapter.byVerse, [1: expected, 2: []])
        let again = try JSONDecoder().decode(BookAlignment.self, from: JSONEncoder().encode(alignment))
        XCTAssertEqual(again, alignment)
        let link = try JSONSerialization.jsonObject(with: JSONEncoder().encode(expected[1])) as? [Any]
        XCTAssertEqual(link?.count, 3)
    }

    // MARK: words(in:) and filledInterior

    func testWordsInTouchingRangesDoNotOverlap() {
        // "In" ends at 2 and "the beginning" starts at 3: the space between
        // touches both and is under neither.
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 2, to: 3), [])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 0, to: 2), [0])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 1, to: 4), [0, 1])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 16, to: 17), [])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 48, to: 56), [9, 11])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: 62, to: nil), [13, 14, 15, 16])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: nil, to: 2), [0])
        XCTAssertEqual(OriginalWords.words(in: john1Links, from: nil, to: nil), Array(0...16).filter { $0 != 10 })
        XCTAssertEqual(OriginalWords.words(in: [], from: 0, to: 10), [])
    }

    func testFilledInteriorAddsOnlyUnlinkedWords() {
        let linked = OriginalWords.linked(john1Links)
        XCTAssertFalse(linked.contains(10))
        XCTAssertEqual(OriginalWords.filledInterior([9, 11], linked: linked), [9, 10, 11])
        XCTAssertEqual(OriginalWords.filledInterior([11, 9, 9], linked: linked), [9, 10, 11])
        XCTAssertEqual(OriginalWords.filledInterior([2, 8], linked: linked), [2, 8])
        XCTAssertEqual(OriginalWords.filledInterior([5], linked: linked), [5])
        XCTAssertEqual(OriginalWords.filledInterior([], linked: linked), [])
        XCTAssertEqual(OriginalWords.filledInterior([0, 4], linked: []), [0, 1, 2, 3, 4])
    }

    // MARK: ranges

    func testRangesBridgeOverUnlinkedWords() {
        // "with God": the gap between holds only a space and the unrendered
        // article.
        XCTAssertEqual(
            OriginalWords.ranges(for: [9, 10, 11], in: john1Links, text: john1),
            [TextRange(start: 48, end: 56)])
        XCTAssertEqual(
            OriginalWords.ranges(for: [1, 2], in: john1Links, text: john1),
            [TextRange(start: 3, end: 20)])
        // A link renders a set; one of its words is enough.
        XCTAssertEqual(
            OriginalWords.ranges(for: [4], in: john1Links, text: john1),
            [TextRange(start: 21, end: 29)])
        XCTAssertEqual(OriginalWords.ranges(for: [10], in: john1Links, text: john1), [])
        XCTAssertEqual(OriginalWords.ranges(for: [], in: john1Links, text: john1), [])
    }

    func testRangesDoNotBridgeAcrossAForeignLink() {
        XCTAssertEqual(
            OriginalWords.ranges(for: [11, 13], in: john1Links, text: john1),
            [TextRange(start: 53, end: 56), TextRange(start: 75, end: 78)])
    }

    func testRangesScatteredStopWordsGiveNothing() {
        // "and … and": two function words with a phrase between them is what
        // a weak link looks like.
        XCTAssertEqual(OriginalWords.ranges(for: [5, 12], in: john1Links, text: john1), [])
        XCTAssertEqual(OriginalWords.ranges(for: [2, 8, 14], in: john1Links, text: john1), [])
    }

    func testRangesSingleStopWordRunIsKept() {
        let text = "Who is this King of glory? "
        let links = [
            AlignmentLink(start: 0, end: 3, words: [0]),
            AlignmentLink(start: 4, end: 6, words: [1]),
            AlignmentLink(start: 7, end: 11, words: [2]),
            AlignmentLink(start: 12, end: 16, words: [3]),
            AlignmentLink(start: 20, end: 25, words: [4]),
        ]
        XCTAssertEqual(OriginalWords.ranges(for: [0, 1], in: links, text: text), [TextRange(start: 0, end: 6)])
        XCTAssertEqual(OriginalWords.ranges(for: [1], in: links, text: text), [TextRange(start: 4, end: 6)])
        XCTAssertEqual(OriginalWords.ranges(for: [5], in: john1Links, text: john1), [TextRange(start: 31, end: 34)])
        // "King of glory" bridges the unlinked "of" and keeps it.
        XCTAssertEqual(OriginalWords.ranges(for: [3, 4], in: links, text: text), [TextRange(start: 12, end: 25)])
    }

    func testRangesBreakWordsAtLinkEdges() {
        // A poetry line glued to the next ("it" + "all") is two words, not
        // one content word that would hide the scatter of "it … the".
        let text = "of itall the days"
        let links = [
            AlignmentLink(start: 0, end: 2, words: [0]),
            AlignmentLink(start: 3, end: 5, words: [1]),
            AlignmentLink(start: 5, end: 8, words: [2]),
            AlignmentLink(start: 9, end: 12, words: [3]),
            AlignmentLink(start: 13, end: 17, words: [4]),
        ]
        XCTAssertEqual(OriginalWords.ranges(for: [1, 3], in: links, text: text), [])
        XCTAssertEqual(
            OriginalWords.ranges(for: [1, 4], in: links, text: text),
            [TextRange(start: 3, end: 5), TextRange(start: 13, end: 17)])
    }

    // MARK: anchored

    func testAnchoredSingleVerse() {
        let range = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1,
                               startChar: 48, endChar: 56, charTranslation: .bsb)
        let anchored = OriginalWords.anchored(range, links: [1: john1Links], source: "bsbt-test")
        XCTAssertEqual(anchored.startWords, [9, 10, 11])
        XCTAssertNil(anchored.endWords)
        XCTAssertEqual(anchored.wordsSource, "bsbt-test")
        XCTAssertEqual(anchored.startChar, 48)
        XCTAssertEqual(anchored.endChar, 56)
        XCTAssertEqual(anchored.charTranslation, .bsb)

        // A mark inside one verse that runs to its end still has a part.
        let tail = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1, endChar: 2, charTranslation: .bsb)
        XCTAssertEqual(OriginalWords.anchored(tail, links: [1: john1Links], source: "s").startWords, [0])
    }

    func testAnchoredMultiVerse() {
        let range = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2,
                               startChar: 62, endChar: 15, charTranslation: .bsb)
        let anchored = OriginalWords.anchored(range, links: [1: john1Links, 2: john2Links], source: "bsbt-test")
        XCTAssertEqual(anchored.startWords, [13, 14, 15, 16])
        XCTAssertEqual(anchored.endWords, [0, 1, 4, 5, 6])
        XCTAssertEqual(anchored.wordsSource, "bsbt-test")

        // A start that is the whole verse needs no words.
        let wholeStart = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2,
                                    endChar: 15, charTranslation: .bsb)
        let end = OriginalWords.anchored(wholeStart, links: [1: john1Links, 2: john2Links], source: "s")
        XCTAssertNil(end.startWords)
        XCTAssertEqual(end.endWords, [0, 1, 4, 5, 6])
        XCTAssertEqual(end.wordsSource, "s")
    }

    func testAnchoredWholeVerseComesBackUnchanged() {
        let range = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2)
        XCTAssertEqual(OriginalWords.anchored(range, links: [1: john1Links, 2: john2Links], source: "s"), range)
    }

    func testAnchoredWithoutLinksLeavesNoWords() {
        let range = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2,
                               startChar: 62, endChar: 15, charTranslation: .bsb)
        XCTAssertEqual(OriginalWords.anchored(range, links: nil, source: "s"), range)
        XCTAssertEqual(OriginalWords.anchored(range, links: [1: john1Links, 2: john2Links], source: nil), range)

        // Links for one end only: the other end is left without words.
        let half = OriginalWords.anchored(range, links: [1: john1Links], source: "s")
        XCTAssertEqual(half.startWords, [13, 14, 15, 16])
        XCTAssertNil(half.endWords)
        XCTAssertEqual(half.wordsSource, "s")

        // A part that holds no linked word: no words, and so no source.
        let gap = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1,
                             startChar: 56, endChar: 58, charTranslation: .bsb)
        let none = OriginalWords.anchored(gap, links: [1: john1Links], source: "s")
        XCTAssertNil(none.startWords)
        XCTAssertNil(none.endWords)
        XCTAssertNil(none.wordsSource)
    }

    // MARK: resolve

    func testResolveAuthorsVersionIsExact() {
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 3,
                               startChar: 7, endChar: 3, charTranslation: .bsb)
        let spans = OriginalWords.resolve(range, reader: .bsb, readerLinks: authorByVerse,
                                          readerTexts: [:], source: "src", authorLinks: authorByVerse)
        XCTAssertEqual(spans, [
            MarkedSpan(verse: 1, from: 7, to: nil),
            MarkedSpan(verse: 2),
            MarkedSpan(verse: 3, from: nil, to: 3),
        ])
        let single = VerseRange(bookID: "1JN", chapter: 4, startVerse: 2, endVerse: 2,
                                startChar: 0, endChar: 6, charTranslation: .bsb, startWords: [2], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(single, reader: .bsb, readerLinks: nil, readerTexts: [:], source: "src", authorLinks: nil),
            [MarkedSpan(verse: 2, from: 0, to: 6)])
    }

    func testResolveDerivesOldMarksFromOffsets() {
        // A mark made before marks carried words: only offsets in the
        // author's version, which the author's links turn into words.
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 3,
                               startChar: 7, endChar: 3, charTranslation: .bsb)
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 1, from: 0, to: 4), MarkedSpan(verse: 2), MarkedSpan(verse: 3, from: 14, to: 17)])

        // "God is" is "is God" on the reader's page, one run.
        let single = VerseRange(bookID: "1JN", chapter: 4, startVerse: 2, endVerse: 2,
                                startChar: 0, endChar: 6, charTranslation: .bsb)
        XCTAssertEqual(
            OriginalWords.resolve(single, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2, from: 11, to: 17)])
    }

    func testResolveUsesStoredWordsFromTheSameSource() {
        // The stored words win over the offsets: they are what the author
        // chose, in the numbering this phone holds.
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 2, endVerse: 2,
                               startChar: 0, endChar: 3, charTranslation: .bsb, startWords: [2], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2, from: 0, to: 4)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: nil),
            [MarkedSpan(verse: 2, from: 0, to: 4)])
    }

    func testResolveIgnoresWordsFromAnotherSource() {
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 2, endVerse: 2,
                               startChar: 0, endChar: 3, charTranslation: .bsb, startWords: [2], wordsSource: "old")
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2, from: 14, to: 17)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: nil),
            [MarkedSpan(verse: 2)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: nil, authorLinks: nil),
            [MarkedSpan(verse: 2)])
    }

    func testResolveWithoutReaderLinksIsWholeVerse() {
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 2, endVerse: 2,
                               startChar: 0, endChar: 3, charTranslation: .bsb, startWords: [1], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .nkjv, readerLinks: nil, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .nkjv, readerLinks: [1: readerLinks], readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .nkjv, readerLinks: readerByVerse, readerTexts: [:],
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2)])
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .nkjv, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 2, from: 14, to: 17)])
    }

    func testResolveMiddleVersesAreWhole() {
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 3,
                               endChar: 3, charTranslation: .bsb, endWords: [1], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: nil),
            [MarkedSpan(verse: 1), MarkedSpan(verse: 2), MarkedSpan(verse: 3, from: 14, to: 17)])
        let whole = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 3)
        XCTAssertEqual(
            OriginalWords.resolve(whole, reader: .web, readerLinks: readerByVerse, readerTexts: readerTexts,
                                  source: "src", authorLinks: authorByVerse),
            [MarkedSpan(verse: 1), MarkedSpan(verse: 2), MarkedSpan(verse: 3)])
    }

    func testResolveScatteredStopWordsFallBackToWholeVerse() {
        let text = "It is love, it is. "
        let links = [
            AlignmentLink(start: 3, end: 5, words: [3]),
            AlignmentLink(start: 6, end: 10, words: [2]),
            AlignmentLink(start: 15, end: 17, words: [3]),
        ]
        let range = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 1,
                               startChar: 4, endChar: 6, charTranslation: .bsb, startWords: [3], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(range, reader: .web, readerLinks: [1: links], readerTexts: [1: text],
                                  source: "src", authorLinks: nil),
            [MarkedSpan(verse: 1)])
        // With the content word among them they are kept, and the unlinked
        // "it" between is bridged into one run.
        let more = VerseRange(bookID: "1JN", chapter: 4, startVerse: 1, endVerse: 1,
                              startChar: 4, endChar: 11, charTranslation: .bsb, startWords: [2, 3], wordsSource: "src")
        XCTAssertEqual(
            OriginalWords.resolve(more, reader: .web, readerLinks: [1: links], readerTexts: [1: text],
                                  source: "src", authorLinks: nil),
            [MarkedSpan(verse: 1, from: 3, to: 17)])
        // A version that splits the words around a foreign link gives one
        // span per piece.
        XCTAssertEqual(
            OriginalWords.resolve(
                VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1, startChar: 0, endChar: 3,
                           charTranslation: .web, startWords: [11, 13], wordsSource: "src"),
                reader: .bsb, readerLinks: [1: john1Links], readerTexts: [1: john1],
                source: "src", authorLinks: nil),
            [MarkedSpan(verse: 1, from: 53, to: 56), MarkedSpan(verse: 1, from: 75, to: 78)])
    }

    // MARK: rendering and phrase

    func testRenderingJoinsSplitRenderings() {
        XCTAssertEqual(OriginalWords.rendering(of: 4, in: john1Links, text: john1), "the Word")
        XCTAssertEqual(OriginalWords.rendering(of: 13, in: john1Links, text: john1), "God")
        XCTAssertNil(OriginalWords.rendering(of: 10, in: john1Links, text: john1))
        let text = "He shall surely not die. "
        let links = [
            AlignmentLink(start: 0, end: 2, words: [0]),
            AlignmentLink(start: 3, end: 8, words: [1]),
            AlignmentLink(start: 9, end: 15, words: [1]),
            AlignmentLink(start: 16, end: 23, words: [2, 3]),
        ]
        XCTAssertEqual(OriginalWords.rendering(of: 1, in: links, text: text), "shall … surely")
        XCTAssertEqual(OriginalWords.rendering(of: 3, in: links, text: text), "not die")
    }

    func testPhraseRunsFromFirstToLast() {
        XCTAssertEqual(
            OriginalWords.phrase(for: [TextRange(start: 48, end: 52), TextRange(start: 53, end: 56)], text: john1),
            "with God")
        XCTAssertEqual(
            OriginalWords.phrase(for: [TextRange(start: 53, end: 56), TextRange(start: 75, end: 78)], text: john1),
            "God, and the Word was God")
        XCTAssertEqual(OriginalWords.phrase(for: [], text: john1), "")
        // Offsets are UTF-16 units: the first letter here is two of them.
        XCTAssertEqual(OriginalWords.phrase(for: [TextRange(start: 0, end: 4)], text: "\u{1D50A}od is love. "), "\u{1D50A}od")
        // Ranges past the end are clamped, never a crash.
        XCTAssertEqual(OriginalWords.phrase(for: [TextRange(start: 75, end: 200)], text: john1), "God. ")
    }

    // MARK: Following lands on the same words

    func testWordAtPartFindsTheLinkUnderTheLine() {
        XCTAssertEqual(OriginalWords.word(at: 0, text: john1, links: john1Links), 0)
        // Offset 40 is inside "the Word" (35..<43): its first word.
        XCTAssertEqual(OriginalWords.word(at: 0.5, text: john1, links: john1Links), 6)
        // The foot of the verse is past every link: the last one.
        XCTAssertEqual(OriginalWords.word(at: 1, text: john1, links: john1Links), 13)
        // A line on the comma after "Word" takes the next link along.
        XCTAssertEqual(OriginalWords.word(at: 0.375, text: john1, links: john1Links), 5)
        // Held to the verse.
        XCTAssertEqual(OriginalWords.word(at: -1, text: john1, links: john1Links), 0)
        XCTAssertEqual(OriginalWords.word(at: 2, text: john1, links: john1Links), 13)
        XCTAssertEqual(OriginalWords.word(at: .nan, text: john1, links: john1Links), 0)
        // Links are taken by where they start, whatever order they come in.
        XCTAssertEqual(OriginalWords.word(at: 0.5, text: john1, links: john1Links.reversed()), 6)
    }

    func testWordAtInAStretchTheLinksLeaveOutIsNil() {
        // A licensed version linked through the Berean Standard: "And
        // behold," and "says the Lord" are words the pivot found no partner
        // for. A line in them sends no word, so the follower keeps the share
        // of the verse rather than jumping to the next linked word.
        let text = "And behold, I come quickly, says the Lord. "
        let links = [
            AlignmentLink(start: 12, end: 18, words: [0]),  // I come
            AlignmentLink(start: 19, end: 26, words: [1]),  // quickly
        ]
        XCTAssertEqual(text.utf16.count, 43)
        // On "And" (offset 2): "I come" is ten units on.
        XCTAssertNil(OriginalWords.word(at: 2.0 / 43.0, text: text, links: links))
        // On the space before "I" (offset 11): the word just after it.
        XCTAssertEqual(OriginalWords.word(at: 11.0 / 43.0, text: text, links: links), 0)
        // On the comma after "quickly" (offset 26): the link just before.
        XCTAssertEqual(OriginalWords.word(at: 26.0 / 43.0, text: text, links: links), 1)
        // On "the Lord", past every link and well past the last.
        XCTAssertNil(OriginalWords.word(at: 0.9, text: text, links: links))
    }

    func testWordAtEmptyTextOrNoLinksIsNil() {
        XCTAssertNil(OriginalWords.word(at: 0.5, text: "", links: john1Links))
        XCTAssertNil(OriginalWords.word(at: 0.5, text: john1, links: []))
    }

    func testPartOfAWordInAMultiWordLink() {
        // ὁ λόγος is "the Word" (21..<29): both words are where it starts.
        XCTAssertEqual(OriginalWords.part(ofWord: 3, text: john1, links: john1Links), 21.0 / 80.0)
        XCTAssertEqual(OriginalWords.part(ofWord: 4, text: john1, links: john1Links), 21.0 / 80.0)
        XCTAssertEqual(OriginalWords.part(ofWord: 16, text: john1, links: john1Links), 62.0 / 80.0)
        XCTAssertEqual(OriginalWords.part(ofWord: 0, text: john1, links: john1Links), 0)
    }

    func testPartOfAnUnlinkedWordIsTheNextOneUp() {
        // τὸν, which no English word renders, is placed at θεόν, "God".
        XCTAssertEqual(OriginalWords.part(ofWord: 10, text: john1, links: john1Links), 53.0 / 80.0)
        // Up in the original, not along the page: without the last "God",
        // θεὸς(13) goes to ἦν(14), "was", though "the Word" (15, 16) is
        // earlier on the page.
        let withoutGod = Array(john1Links.dropLast())
        XCTAssertEqual(OriginalWords.part(ofWord: 13, text: john1, links: withoutGod), 71.0 / 80.0)
    }

    func testPartOfAWordPastTheEndIsNil() {
        XCTAssertNil(OriginalWords.part(ofWord: 17, text: john1, links: john1Links))
        XCTAssertNil(OriginalWords.part(ofWord: 0, text: john1, links: []))
        XCTAssertNil(OriginalWords.part(ofWord: 0, text: "", links: john1Links))
    }

    // The person followed reads "God is love." and their line is on "love";
    // the follower reads "Love, that is God."
    var heard: ReadingPoint { ReadingPoint(chapter: 4, verse: 1, part: 0.6) }

    func testCarriedSameVersionIsUntouched() {
        XCTAssertEqual(OriginalWords.word(at: heard.part, text: authorText, links: authorLinks), 2)
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: .web, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
    }

    func testCarriedDifferentVersionMoves() {
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            ReadingPoint(chapter: 4, verse: 1, part: 0))
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 1, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            ReadingPoint(chapter: 4, verse: 1, part: 14.0 / 19.0))
    }

    func testCarriedWrongSourceIsUntouched() {
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "bsbt-old", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: nil, from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
        // Two missing sources are not a match.
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: nil, from: .bsb, to: .web,
                                  source: nil, links: readerLinks, text: readerText),
            heard)
    }

    func testCarriedMissingLinksIsUntouched() {
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: nil, text: readerText),
            heard)
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: [], text: readerText),
            heard)
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: nil),
            heard)
        // An old build sends no word and no version.
        XCTAssertEqual(
            OriginalWords.carried(heard, word: nil, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 2, wordsSource: "src", from: nil, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
        // A word the follower's version cannot place.
        XCTAssertEqual(
            OriginalWords.carried(heard, word: 9, wordsSource: "src", from: .bsb, to: .web,
                                  source: "src", links: readerLinks, text: readerText),
            heard)
    }

    func testFollowingLandsOnTheSameWordsAcrossVersions() {
        // John 1:3, texts and links as bundled. The Greek: πάντα(0) δι’(1)
        // αὐτοῦ(2) ἐγένετο(3) καὶ(4) χωρὶς(5) αὐτοῦ(6) ἐγένετο(7) οὐδὲ(8)
        // ἕν(9) ὃ(10) γέγονεν(11). The Berean Standard opens with "Through
        // Him"; the World English puts it after "all things were made".
        let bsb = "Through Him all things were made, and without Him nothing was made that has been made. "
        let bsbLinks = [
            AlignmentLink(start: 0, end: 7, words: [1]),
            AlignmentLink(start: 8, end: 11, words: [2]),
            AlignmentLink(start: 12, end: 22, words: [0]),
            AlignmentLink(start: 23, end: 32, words: [3]),
            AlignmentLink(start: 34, end: 37, words: [4]),
            AlignmentLink(start: 38, end: 45, words: [5]),
            AlignmentLink(start: 46, end: 49, words: [6]),
            AlignmentLink(start: 50, end: 57, words: [8, 9]),
            AlignmentLink(start: 58, end: 66, words: [7]),
            AlignmentLink(start: 67, end: 71, words: [10]),
            AlignmentLink(start: 72, end: 85, words: [11]),
        ]
        let web = "All things were made through him. Without him, nothing was made that has been made. "
        let webLinks = [
            AlignmentLink(start: 0, end: 10, words: [0]),
            AlignmentLink(start: 11, end: 20, words: [3]),
            AlignmentLink(start: 21, end: 32, words: [4]),
            AlignmentLink(start: 34, end: 41, words: [5]),
            AlignmentLink(start: 42, end: 45, words: [6]),
            AlignmentLink(start: 47, end: 54, words: [8, 9]),
            AlignmentLink(start: 55, end: 63, words: [7]),
            AlignmentLink(start: 64, end: 68, words: [10]),
            AlignmentLink(start: 69, end: 82, words: [11]),
        ]
        XCTAssertEqual(bsb.utf16.count, 87)
        XCTAssertEqual(web.utf16.count, 84)

        func follow(_ part: Double, leader: TranslationID, leaderText: String, leaderLinks: [AlignmentLink],
                    follower: TranslationID, followerText: String, followerLinks: [AlignmentLink]) -> Double {
            let point = ReadingPoint(chapter: 1, verse: 3, part: part)
            let word = OriginalWords.word(at: part, text: leaderText, links: leaderLinks)
            return OriginalWords.carried(point, word: word, wordsSource: "src", from: leader, to: follower,
                                         source: "src", links: followerLinks, text: followerText).part
        }

        // A line on "all things" in the Berean Standard is at the head of the
        // verse in the World English, not 0.15 of the way into "were made".
        XCTAssertEqual(follow(0.15, leader: .bsb, leaderText: bsb, leaderLinks: bsbLinks,
                              follower: .web, followerText: web, followerLinks: webLinks), 0)
        // "without Him" and "nothing", each to the same words.
        XCTAssertEqual(follow(0.45, leader: .bsb, leaderText: bsb, leaderLinks: bsbLinks,
                              follower: .web, followerText: web, followerLinks: webLinks), 34.0 / 84.0)
        XCTAssertEqual(follow(0.6, leader: .bsb, leaderText: bsb, leaderLinks: bsbLinks,
                              follower: .web, followerText: web, followerLinks: webLinks), 47.0 / 84.0)
        // And back: "All things" at the head of the World English is part-way
        // into the Berean Standard.
        XCTAssertEqual(follow(0, leader: .web, leaderText: web, leaderLinks: webLinks,
                              follower: .bsb, followerText: bsb, followerLinks: bsbLinks), 12.0 / 87.0)
    }

    // MARK: The bundled corpus

    static var scripture: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()  // RibbonCoreTests
            .deletingLastPathComponent()  // Tests
            .deletingLastPathComponent()  // core
            .deletingLastPathComponent()  // repo root
            .appendingPathComponent("ios/Ribbon/Resources/Scripture")
    }

    func testBundledOriginalWordsDecode() throws {
        let original = Self.scripture.appendingPathComponent("original")
        guard FileManager.default.fileExists(atPath: original.appendingPathComponent("GEN.json").path) else {
            throw XCTSkip("original words not present")
        }
        let lexicon = try JSONDecoder().decode(
            Lexicon.self, from: Data(contentsOf: original.appendingPathComponent("strongs.json")))
        let parsings = try JSONDecoder().decode(
            Parsings.self, from: Data(contentsOf: original.appendingPathComponent("parsing.json")))
        var sources = Set<String>()
        for book in Bible.books {
            let data = try Data(contentsOf: original.appendingPathComponent("\(book.id).json"))
            let text = try JSONDecoder().decode(OriginalBook.self, from: data)
            XCTAssertEqual(text.id, book.id)
            XCTAssertTrue(text.source.hasPrefix("bsbt-"), text.source)
            sources.insert(text.source)
            XCTAssertFalse(text.chapters.isEmpty, book.id)
            for chapter in text.chapters {
                for verse in chapter.verses {
                    XCTAssertFalse(verse.words.isEmpty, "\(book.id) \(chapter.n):\(verse.v)")
                    for word in verse.words {
                        if let strongs = word.strongs {
                            XCTAssertNotNil(lexicon.entry(strongs), "\(book.id) \(chapter.n):\(verse.v) \(strongs)")
                        }
                        if let parse = word.parse {
                            XCTAssertNotNil(parsings.describe(parse), "\(book.id) \(chapter.n):\(verse.v) \(parse)")
                        }
                    }
                }
            }
        }
        XCTAssertEqual(sources.count, 1)
    }

    func testBundledLinksFitTheirText() throws {
        let original = Self.scripture.appendingPathComponent("original")
        let align = Self.scripture.appendingPathComponent("align")
        guard FileManager.default.fileExists(atPath: original.appendingPathComponent("GEN.json").path),
              FileManager.default.fileExists(atPath: align.path)
        else {
            throw XCTSkip("word links not present")
        }
        for translation in TranslationRegistry.bundled {
            XCTAssertTrue(translation.hasBundledWordLinks)
            for book in Bible.books {
                let words = try JSONDecoder().decode(
                    OriginalBook.self,
                    from: Data(contentsOf: original.appendingPathComponent("\(book.id).json")))
                let links = try JSONDecoder().decode(
                    BookAlignment.self,
                    from: Data(contentsOf: align.appendingPathComponent(translation.id.rawValue)
                        .appendingPathComponent("\(book.id).json")))
                let text = try JSONDecoder().decode(
                    ScriptureBookText.self,
                    from: Data(contentsOf: Self.scripture.appendingPathComponent(translation.id.rawValue)
                        .appendingPathComponent("\(book.id).json")))
                XCTAssertEqual(links.id, book.id)
                XCTAssertEqual(links.translation, translation.id)
                XCTAssertEqual(links.source, words.source, "\(translation.id.rawValue) \(book.id)")
                for chapter in links.chapters {
                    let own = text.chapter(chapter.n)?.ownTexts() ?? [:]
                    let originalChapter = words.chapter(chapter.n)
                    for verse in chapter.verses {
                        let place = "\(translation.id.rawValue) \(book.id) \(chapter.n):\(verse.v)"
                        guard let length = own[verse.v]?.utf16.count else {
                            XCTFail("\(place) has links but no text")
                            continue
                        }
                        guard let count = originalChapter?.words(verse: verse.v)?.count else {
                            XCTFail("\(place) has links but no original words")
                            continue
                        }
                        var previousEnd = 0
                        for link in verse.links {
                            XCTAssertLessThanOrEqual(previousEnd, link.start, place)
                            XCTAssertLessThan(link.start, link.end, place)
                            XCTAssertLessThanOrEqual(link.end, length, place)
                            XCTAssertFalse(link.words.isEmpty, place)
                            for index in link.words {
                                XCTAssertTrue((0..<count).contains(index), "\(place) word \(index)")
                            }
                            previousEnd = link.end
                        }
                    }
                }
            }
        }
    }
}
