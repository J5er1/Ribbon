import XCTest
@testable import RibbonCore

final class BibleTests: XCTestCase {
    func testCanonHasSixtySixBooksInOrder() {
        XCTAssertEqual(Bible.books.count, 66)
        XCTAssertEqual(Bible.books.first?.id, "GEN")
        XCTAssertEqual(Bible.books.last?.id, "REV")
        XCTAssertEqual(Bible.book(id: "MRK")?.name, "Mark")
    }

    func testFireScalesMatchTheBuildBookExamples() {
        // §4.1's example table is the contract.
        for id in ["PHM", "JUD", "2JN", "OBA"] {
            XCTAssertEqual(Bible.book(id: id)?.scale, .small, "\(id) should be small")
        }
        for id in ["PHP", "RUT", "JAS", "MRK"] {
            XCTAssertEqual(Bible.book(id: id)?.scale, .medium, "\(id) should be medium")
        }
        for id in ["ISA", "PSA", "GEN", "JER"] {
            XCTAssertEqual(Bible.book(id: id)?.scale, .large, "\(id) should be large")
        }
    }

    func testChapterCounts() {
        XCTAssertEqual(Bible.book(id: "MRK")?.chapterCount, 16)
        XCTAssertEqual(Bible.book(id: "PSA")?.chapterCount, 150)
        XCTAssertEqual(Bible.book(id: "PHM")?.chapterCount, 1)
        XCTAssertEqual(Bible.book(id: "JHN")?.chapterCount, 21)
    }

    func testGoodPlacesToStart() {
        // Mark, Ruth, Philippians, John, Psalms — editorial, not algorithmic.
        XCTAssertEqual(Bible.goodPlacesToStart, ["MRK", "RUT", "PHP", "JHN", "PSA"])
        for id in Bible.goodPlacesToStart {
            XCTAssertNotNil(Bible.book(id: id))
        }
    }

    func testVerseAddressFormatting() {
        let address = VerseAddress(bookID: "MRK", chapter: 4, verse: 9)
        XCTAssertEqual(address.formatted, "Mark 4:9")
        XCTAssertEqual(address.chapterFormatted, "Mark 4")

        let range = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 11)
        XCTAssertEqual(range.formatted, "Mark 4:9–11")
        XCTAssertTrue(range.contains(address))
        XCTAssertFalse(range.contains(VerseAddress(bookID: "MRK", chapter: 4, verse: 12)))
    }

    func testVerseOrdering() {
        let a = VerseAddress(bookID: "MRK", chapter: 4, verse: 9)
        let b = VerseAddress(bookID: "MRK", chapter: 4, verse: 11)
        let c = VerseAddress(bookID: "JHN", chapter: 1, verse: 1)
        XCTAssertLessThan(a, b)
        XCTAssertLessThan(a, c) // Mark precedes John in the canon
    }

    func testInkPaletteIsEightAndExcludesChartreuse() {
        XCTAssertEqual(Ink.allCases.count, 8)
        // Chartreuse is the brand's, not the user's.
        XCTAssertFalse(Ink.allCases.contains { $0.darkHex.uppercased() == "D6E45C" })
        let taken: [Ink] = [.teal, .ochre]
        XCTAssertEqual(Ink.remaining(taken: taken).count, 6)
    }
}
