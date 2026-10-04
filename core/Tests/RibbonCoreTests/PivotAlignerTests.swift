import XCTest
@testable import RibbonCore

// The on-phone aligner (A60). The hand-written cases pin the rules; the
// fixture cases (Fixtures/pivot_cases.json, written by tools/pivot_align.py)
// hold this port, the Kotlin port and the Python reference to the same
// answers on real verses. PivotAlignerTest.kt runs the same cases.

final class PivotAlignerTests: XCTestCase {
    /// start, end, norm, stem, isContent — the fixture's token shape.
    func shape(_ tokens: [PivotAligner.Token]) -> [[String]] {
        tokens.map { ["\($0.start)", "\($0.end)", $0.norm, $0.stem, "\($0.isContent)"] }
    }

    // MARK: Tokens

    func testTokensSplitOnSpacesAndPunctuation() {
        XCTAssertEqual(shape(PivotAligner.tokens("In the beginning, God created.")), [
            ["0", "2", "in", "in", "false"],
            ["3", "6", "the", "the", "false"],
            ["7", "16", "beginning", "begin", "true"],
            ["18", "21", "god", "god", "true"],
            ["22", "29", "created", "creat", "true"],
        ])
        XCTAssertEqual(PivotAligner.tokens("").count, 0)
        XCTAssertEqual(PivotAligner.tokens(" — ").count, 0)
    }

    func testTokensKeepInnerApostrophes() {
        XCTAssertEqual(shape(PivotAligner.tokens("the LORD\u{2019}s house; o'er the sea, 'twas brothers' ")), [
            ["0", "3", "the", "the", "false"],
            ["4", "10", "lords", "lord", "true"],
            ["11", "16", "house", "hous", "true"],
            ["18", "22", "oer", "oer", "true"],
            ["23", "26", "the", "the", "false"],
            ["27", "30", "sea", "sea", "true"],
            ["33", "37", "twas", "twa", "true"],
            ["38", "46", "brothers", "brother", "true"],
        ])
    }

    func testTokensBreakOnHyphensAndSigns() {
        XCTAssertEqual(shape(PivotAligner.tokens("burnt-offering a\u{D7}b")), [
            ["0", "5", "burnt", "burn", "true"],
            ["6", "14", "offering", "offer", "true"],
            ["15", "16", "a", "a", "false"],
            ["17", "18", "b", "b", "true"],
        ])
    }

    func testTokensBreakAtSpanStarts() {
        // Two poetic lines glued with no space: "my rock" + "in whom".
        XCTAssertEqual(shape(PivotAligner.tokens("my rockin whom", spanBreaks: [7])), [
            ["0", "2", "my", "my", "false"],
            ["3", "7", "rock", "rock", "true"],
            ["7", "9", "in", "in", "false"],
            ["10", "14", "whom", "whom", "false"],
        ])
        // An apostrophe before a break is outside the word.
        XCTAssertEqual(shape(PivotAligner.tokens("it\u{2019}s", spanBreaks: [3])), [
            ["0", "2", "it", "it", "false"],
            ["3", "4", "s", "", "true"],
        ])
    }

    func testTokensLowerLatinLetters() {
        XCTAssertEqual(PivotAligner.tokens("\u{C9}LAN \u{152}uvre \u{130}stanbul \u{178} \u{1C5}").map(\.norm), [
            "\u{E9}lan", "\u{153}uvre", "istanbul", "\u{FF}", "\u{1C5}",
        ])
    }

    // MARK: Stems

    func testPorterStemsThePapersExamples() {
        let cases: [(String, String)] = [
            ("caresses", "caress"), ("ponies", "poni"), ("ties", "ti"), ("caress", "caress"), ("cats", "cat"),
            ("feed", "feed"), ("agreed", "agre"), ("plastered", "plaster"), ("bled", "bled"),
            ("motoring", "motor"), ("sing", "sing"), ("conflated", "conflat"), ("troubled", "troubl"),
            ("sized", "size"), ("hopping", "hop"), ("tanned", "tan"), ("falling", "fall"),
            ("hissing", "hiss"), ("fizzed", "fizz"), ("failing", "fail"), ("filing", "file"),
            ("happy", "happi"), ("sky", "sky"),
            ("relational", "relat"), ("conditional", "condit"), ("rational", "ration"),
            ("valenci", "valenc"), ("digitizer", "digit"), ("conformabli", "conform"),
            ("radicalli", "radic"), ("differentli", "differ"), ("vileli", "vile"),
            ("analogousli", "analog"), ("vietnamization", "vietnam"), ("predication", "predic"),
            ("operator", "oper"), ("feudalism", "feudal"), ("decisiveness", "decis"),
            ("hopefulness", "hope"), ("callousness", "callous"), ("formaliti", "formal"),
            ("sensitiviti", "sensit"), ("sensibiliti", "sensibl"),
            ("triplicate", "triplic"), ("formative", "form"), ("formalize", "formal"),
            ("electriciti", "electr"), ("electrical", "electr"), ("hopeful", "hope"), ("goodness", "good"),
            ("revival", "reviv"), ("allowance", "allow"), ("inference", "infer"), ("airliner", "airlin"),
            ("gyroscopic", "gyroscop"), ("adjustable", "adjust"), ("defensible", "defens"),
            ("irritant", "irrit"), ("replacement", "replac"), ("adjustment", "adjust"),
            ("dependent", "depend"), ("adoption", "adopt"), ("homologou", "homolog"),
            ("communism", "commun"), ("activate", "activ"), ("angulariti", "angular"),
            ("homologous", "homolog"), ("effective", "effect"), ("bowdlerize", "bowdler"),
            ("probate", "probat"), ("rate", "rate"), ("cease", "ceas"), ("controll", "control"), ("roll", "roll"),
            ("generalizations", "gener"), ("oscillators", "oscil"),
        ]
        for (word, stem) in cases {
            XCTAssertEqual(PivotAligner.porter(word), stem, word)
        }
    }

    func testStemUsesIrregularForms() {
        XCTAssertEqual(PivotAligner.stem("spake"), "speak")
        XCTAssertEqual(PivotAligner.stem("brethren"), "brother")
        XCTAssertEqual(PivotAligner.stem("was"), "be")
        // The table's values are Porter stems of the base form, so "said"
        // meets "saying" and "say".
        XCTAssertEqual(PivotAligner.stem("said"), "sai")
        XCTAssertEqual(PivotAligner.stem("saying"), PivotAligner.stem("said"))
        XCTAssertEqual(PivotAligner.stem("loved"), "love")
    }

    // MARK: Alignment

    let pivot = "For God so loved the world "
    let pivotLinks = [
        AlignmentLink(start: 0, end: 3, words: [0]),
        AlignmentLink(start: 4, end: 7, words: [4]),
        AlignmentLink(start: 8, end: 10, words: [1]),
        AlignmentLink(start: 11, end: 16, words: [2]),
        AlignmentLink(start: 17, end: 26, words: [6, 7]),
    ]

    func testAlignBorrowsThePivotsLinks() {
        // The same words: everything but the opening "For", a function word
        // with no content word before it, carries over; "the world" is one
        // link because its two words share a set.
        XCTAssertEqual(
            PivotAligner.align(reader: "For God so loved the world, ", readerBreaks: [],
                               pivot: pivot, pivotBreaks: [], pivotLinks: pivotLinks),
            [
                AlignmentLink(start: 4, end: 7, words: [4]),
                AlignmentLink(start: 8, end: 10, words: [1]),
                AlignmentLink(start: 11, end: 16, words: [2]),
                AlignmentLink(start: 17, end: 26, words: [6, 7]),
            ])
        // Other words in another order: what lines up carries over.
        XCTAssertEqual(
            PivotAligner.align(reader: "God loved the world so much ", readerBreaks: [],
                               pivot: pivot, pivotBreaks: [], pivotLinks: pivotLinks),
            [
                AlignmentLink(start: 0, end: 3, words: [4]),
                AlignmentLink(start: 4, end: 9, words: [2]),
                AlignmentLink(start: 10, end: 19, words: [6, 7]),
            ])
    }

    func testAlignKeepsStopWordsOnlyBetweenOrBeside() {
        // "not" stands beside "perish", which took the same words.
        XCTAssertEqual(
            PivotAligner.align(reader: "should not perish ", readerBreaks: [],
                               pivot: "shall not perish ", pivotBreaks: [],
                               pivotLinks: [AlignmentLink(start: 0, end: 16, words: [5, 6])]),
            [AlignmentLink(start: 7, end: 17, words: [5, 6])])
        // Function words alone never make a link.
        XCTAssertEqual(
            PivotAligner.align(reader: "In the start ", readerBreaks: [],
                               pivot: "In the beginning ", pivotBreaks: [],
                               pivotLinks: [AlignmentLink(start: 0, end: 2, words: [0]),
                                            AlignmentLink(start: 3, end: 16, words: [1])]),
            [])
    }

    func testAlignWithoutPivotLinksIsEmpty() {
        XCTAssertEqual(
            PivotAligner.align(reader: "In the beginning ", readerBreaks: [],
                               pivot: "In the beginning ", pivotBreaks: [], pivotLinks: []),
            [])
        XCTAssertEqual(
            PivotAligner.align(reader: "", readerBreaks: [],
                               pivot: pivot, pivotBreaks: [], pivotLinks: pivotLinks),
            [])
    }

    // MARK: The fixture

    struct FixtureToken: Decodable, Equatable {
        var start: Int
        var end: Int
        var norm: String
        var stem: String
        var isContent: Bool

        init(_ token: PivotAligner.Token) {
            start = token.start
            end = token.end
            norm = token.norm
            stem = token.stem
            isContent = token.isContent
        }

        init(from decoder: Decoder) throws {
            var c = try decoder.unkeyedContainer()
            start = try c.decode(Int.self)
            end = try c.decode(Int.self)
            norm = try c.decode(String.self)
            stem = try c.decode(String.self)
            isContent = try c.decode(Bool.self)
        }
    }

    struct TokenCase: Decodable {
        var text: String
        var breaks: [Int]
        var tokens: [FixtureToken]
    }

    struct AlignCase: Decodable {
        var name: String
        var reader: String
        var readerBreaks: [Int]
        var pivot: String
        var pivotBreaks: [Int]
        var pivotLinks: [AlignmentLink]
        var expected: [AlignmentLink]
    }

    struct Fixture: Decodable {
        var stems: [[String]]
        var tokens: [TokenCase]
        var align: [AlignCase]
    }

    func fixture() throws -> Fixture {
        // Read from the repository by path, as the corpus tests are, so the
        // package needs no resources of its own.
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Fixtures/pivot_cases.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("pivot_cases.json not present; run tools/pivot_align.py --kjv")
        }
        return try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: url))
    }

    func testFixtureStems() throws {
        let stems = try fixture().stems
        XCTAssertFalse(stems.isEmpty)
        for pair in stems {
            XCTAssertEqual(pair.count, 2)
            XCTAssertEqual(PivotAligner.stem(pair[0]), pair[1], pair[0])
        }
    }

    func testFixtureTokens() throws {
        let cases = try fixture().tokens
        XCTAssertFalse(cases.isEmpty)
        for c in cases {
            XCTAssertEqual(PivotAligner.tokens(c.text, spanBreaks: c.breaks).map(FixtureToken.init), c.tokens, c.text)
        }
    }

    func testFixtureAlignments() throws {
        let cases = try fixture().align
        XCTAssertFalse(cases.isEmpty)
        for c in cases {
            let links = PivotAligner.align(
                reader: c.reader, readerBreaks: c.readerBreaks,
                pivot: c.pivot, pivotBreaks: c.pivotBreaks, pivotLinks: c.pivotLinks)
            XCTAssertEqual(links, c.expected, c.name)
        }
    }
}
