import XCTest
@testable import RibbonCore

// A mark's original-word anchors (A60), whose version is on a person's page,
// and the model's tolerance of older saved state. VerseRangeAnchorTest.kt
// holds the Kotlin core to the same cases, name for name.

final class VerseRangeAnchorTests: XCTestCase {
    func keys(_ range: VerseRange) throws -> Set<String> {
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(range)) as? [String: Any]
        return Set(try XCTUnwrap(object).keys)
    }

    func testADragMadeUpwardsTurnsItsOffsetsOver() {
        let r = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 3, startChar: 12, endChar: 4)
        XCTAssertEqual(r.startVerse, 3)
        XCTAssertEqual(r.endVerse, 9)
        XCTAssertEqual(r.startChar, 4)
        XCTAssertEqual(r.endChar, 12)
        let one = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 9, startChar: 30, endChar: 10)
        XCTAssertEqual(one.startChar, 10)
        XCTAssertEqual(one.endChar, 30)
        XCTAssertFalse(one.isWholeVerses)
    }

    func testWordArraysAreSortedAndUnique() {
        let r = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2, startChar: 3, endChar: 5,
                           charTranslation: .bsb, startWords: [3, 1, 3], endWords: [4, 0, 4, 2], wordsSource: "s")
        XCTAssertEqual(r.startWords, [1, 3])
        XCTAssertEqual(r.endWords, [0, 2, 4])
        XCTAssertEqual(r.wordsSource, "s")
    }

    func testEmptyWordArraysAreNil() {
        let r = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2, startChar: 3, endChar: 5,
                           charTranslation: .bsb, startWords: [], endWords: [], wordsSource: "s")
        XCTAssertNil(r.startWords)
        XCTAssertNil(r.endWords)
        XCTAssertNil(r.wordsSource)
        let half = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 2, startChar: 3, endChar: 5,
                              charTranslation: .bsb, startWords: [], endWords: [2], wordsSource: "s")
        XCTAssertNil(half.startWords)
        XCTAssertEqual(half.endWords, [2])
        XCTAssertEqual(half.wordsSource, "s")
    }

    func testWordsSwapWithTheirEnds() {
        let r = VerseRange(bookID: "JHN", chapter: 1, startVerse: 5, endVerse: 3, startChar: 4, endChar: 12,
                           charTranslation: .bsb, startWords: [7], endWords: [2], wordsSource: "s")
        XCTAssertEqual(r.startVerse, 3)
        XCTAssertEqual(r.endVerse, 5)
        XCTAssertEqual(r.startChar, 12)
        XCTAssertEqual(r.endChar, 4)
        XCTAssertEqual(r.startWords, [2])
        XCTAssertEqual(r.endWords, [7])
    }

    func testSingleVerseKeepsOneSet() {
        let r = VerseRange(bookID: "JHN", chapter: 1, startVerse: 4, endVerse: 4, startChar: 0, endChar: 9,
                           charTranslation: .bsb, startWords: [4], endWords: [1, 4], wordsSource: "s")
        XCTAssertEqual(r.startWords, [1, 4])
        XCTAssertNil(r.endWords)
        let endOnly = VerseRange(bookID: "JHN", chapter: 1, startVerse: 4, endVerse: 4, endChar: 9,
                                 charTranslation: .bsb, endWords: [2], wordsSource: "s")
        XCTAssertEqual(endOnly.startWords, [2])
        XCTAssertNil(endOnly.endWords)
        XCTAssertEqual(endOnly.wordsSource, "s")
    }

    func testWordsAndSourceNeedEachOther() {
        let sourceOnly = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1, startChar: 0, endChar: 2,
                                    charTranslation: .bsb, wordsSource: "s")
        XCTAssertNil(sourceOnly.wordsSource)
        let wordsOnly = VerseRange(bookID: "JHN", chapter: 1, startVerse: 1, endVerse: 1, startChar: 0, endChar: 2,
                                   charTranslation: .bsb, startWords: [0])
        XCTAssertNil(wordsOnly.startWords)
        XCTAssertNil(wordsOnly.wordsSource)
    }

    func testRoundTripWithoutTheNewKeys() throws {
        let json = #"{"bookID":"MRK","chapter":4,"startVerse":9,"endVerse":9,"startChar":3,"endChar":10,"charTranslation":"bsb"}"#
        let r = try JSONDecoder().decode(VerseRange.self, from: Data(json.utf8))
        XCTAssertEqual(r.startChar, 3)
        XCTAssertEqual(r.charTranslation, .bsb)
        XCTAssertNil(r.startWords)
        XCTAssertNil(r.endWords)
        XCTAssertNil(r.wordsSource)
        XCTAssertEqual(try keys(r), ["bookID", "chapter", "startVerse", "endVerse", "startChar", "endChar", "charTranslation"])
        let again = try JSONDecoder().decode(VerseRange.self, from: JSONEncoder().encode(r))
        XCTAssertEqual(again, r)

        let whole = try JSONDecoder().decode(
            VerseRange.self, from: Data(#"{"bookID":"MRK","chapter":4,"startVerse":9,"endVerse":11}"#.utf8))
        XCTAssertTrue(whole.isWholeVerses)
        XCTAssertEqual(try keys(whole), ["bookID", "chapter", "startVerse", "endVerse"])
    }

    func testRoundTripWithTheNewKeys() throws {
        let json = """
        {"bookID":"JHN","chapter":1,"startVerse":5,"endVerse":3,"startChar":2,"endChar":20,"charTranslation":"web",\
        "startWords":[2,1],"endWords":[0],"wordsSource":"bsbt-1a2b3c4d"}
        """
        let r = try JSONDecoder().decode(VerseRange.self, from: Data(json.utf8))
        // Decoding goes through the same normalisation: the ends turn over.
        XCTAssertEqual(r.startVerse, 3)
        XCTAssertEqual(r.startWords, [0])
        XCTAssertEqual(r.endWords, [1, 2])
        XCTAssertEqual(r.wordsSource, "bsbt-1a2b3c4d")
        XCTAssertEqual(try keys(r), [
            "bookID", "chapter", "startVerse", "endVerse", "startChar", "endChar", "charTranslation",
            "startWords", "endWords", "wordsSource",
        ])
        let again = try JSONDecoder().decode(VerseRange.self, from: JSONEncoder().encode(r))
        XCTAssertEqual(again, r)
    }

    func testTranslationChoicePage() {
        let ruth = Person(name: "Ruth", translation: .web)
        let room = Room(createdAt: Date(timeIntervalSince1970: 0), translation: .nkjv)
        XCTAssertEqual(TranslationChoice.page(me: ruth, room: room), .web)
        XCTAssertEqual(TranslationChoice.page(me: ruth, room: nil), .web)
        XCTAssertEqual(TranslationChoice.page(me: nil, room: room), .nkjv)
        XCTAssertEqual(TranslationChoice.page(me: nil, room: nil), .bsb)
    }

    func testPersonWithoutTranslationDecodes() throws {
        let json = #"{"id":"6f9619ff-8b86-d011-b42d-00c04fc964ff","name":"Ruth"}"#
        let person = try JSONDecoder().decode(Person.self, from: Data(json.utf8))
        XCTAssertEqual(person.name, "Ruth")
        XCTAssertNil(person.portraitPath)
        XCTAssertEqual(person.translation, .bsb)
        let web = Person(name: "Naomi", portraitPath: "naomi.jpg", translation: .web)
        XCTAssertEqual(try JSONDecoder().decode(Person.self, from: JSONEncoder().encode(web)), web)
    }

    func testOnlyBundledVersionsHaveWordLinks() {
        XCTAssertTrue(TranslationRegistry.bsb.hasBundledWordLinks)
        XCTAssertTrue(TranslationRegistry.web.hasBundledWordLinks)
        XCTAssertFalse(TranslationRegistry.nkjv.hasBundledWordLinks)
        XCTAssertFalse(TranslationRegistry.niv.hasBundledWordLinks)
        XCTAssertFalse(TranslationRegistry.nasb.hasBundledWordLinks)
        XCTAssertTrue(TranslationRegistry.hasBundledWordLinks(.web))
        XCTAssertFalse(TranslationRegistry.hasBundledWordLinks(.nkjv))
        XCTAssertFalse(TranslationRegistry.hasBundledWordLinks(TranslationID(rawValue: "kjv")))
    }
}
