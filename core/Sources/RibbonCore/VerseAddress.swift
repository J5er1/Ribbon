import Foundation

/// A verse address. Addresses are not scores (build book §01): "Mark 4:9" is
/// an address, and notes pin to verse addresses rather than text offsets so
/// that a note lands on the same verse in either person's translation (§2.6).
public struct VerseAddress: Codable, Hashable, Comparable, Sendable, CustomStringConvertible {
    public var bookID: String
    public var chapter: Int
    public var verse: Int

    public init(bookID: String, chapter: Int, verse: Int) {
        self.bookID = bookID
        self.chapter = chapter
        self.verse = verse
    }

    /// "Mark 4:9"
    public var formatted: String {
        let name = Bible.book(id: bookID)?.name ?? bookID
        return "\(name) \(chapter):\(verse)"
    }

    /// "Mark 4"
    public var chapterFormatted: String {
        let name = Bible.book(id: bookID)?.name ?? bookID
        return "\(name) \(chapter)"
    }

    public var description: String { formatted }

    public static func < (lhs: VerseAddress, rhs: VerseAddress) -> Bool {
        if lhs.bookID != rhs.bookID {
            let l = Bible.books.firstIndex { $0.id == lhs.bookID } ?? .max
            let r = Bible.books.firstIndex { $0.id == rhs.bookID } ?? .max
            return l < r
        }
        if lhs.chapter != rhs.chapter { return lhs.chapter < rhs.chapter }
        return lhs.verse < rhs.verse
    }
}

/// A contiguous run of verses within one chapter. Highlights snap to verse
/// boundaries by default (S06), and a drag never crosses a chapter.
public struct VerseRange: Codable, Hashable, Sendable {
    public var bookID: String
    public var chapter: Int
    public var startVerse: Int
    public var endVerse: Int

    public init(bookID: String, chapter: Int, startVerse: Int, endVerse: Int) {
        self.bookID = bookID
        self.chapter = chapter
        self.startVerse = min(startVerse, endVerse)
        self.endVerse = max(startVerse, endVerse)
    }

    public init(_ address: VerseAddress) {
        self.init(
            bookID: address.bookID, chapter: address.chapter,
            startVerse: address.verse, endVerse: address.verse)
    }

    public var start: VerseAddress { VerseAddress(bookID: bookID, chapter: chapter, verse: startVerse) }
    public var verses: ClosedRange<Int> { startVerse...endVerse }

    public func contains(_ address: VerseAddress) -> Bool {
        address.bookID == bookID && address.chapter == chapter && verses.contains(address.verse)
    }

    public func overlaps(_ other: VerseRange) -> Bool {
        bookID == other.bookID && chapter == other.chapter && verses.overlaps(other.verses)
    }

    /// "Mark 4:9" for one verse, "Mark 4:9–11" for a run.
    public var formatted: String {
        let name = Bible.book(id: bookID)?.name ?? bookID
        if startVerse == endVerse { return "\(name) \(chapter):\(startVerse)" }
        return "\(name) \(chapter):\(startVerse)–\(endVerse)"
    }
}
