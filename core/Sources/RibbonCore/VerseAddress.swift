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

    /// "Mark 4:9" — "Psalm 23:1" for the Psalter.
    public var formatted: String {
        let name = Bible.book(id: bookID)?.referenceName ?? bookID
        return "\(name) \(chapter):\(verse)"
    }

    /// "Mark 4" — "Psalm 23" for the Psalter.
    public var chapterFormatted: String {
        let name = Bible.book(id: bookID)?.referenceName ?? bookID
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
    /// Offset into `startVerse`'s own text; nil starts at its first letter.
    /// A mark on a phrase rather than a verse (ledger A41g). The offsets are
    /// only honoured by a reader on `charTranslation`; anyone else sees the
    /// whole verses, which is what the address alone promises.
    public var startChar: Int?
    /// Offset into `endVerse`'s own text; nil runs to its last.
    public var endChar: Int?
    /// The translation `startChar` and `endChar` were measured in.
    public var charTranslation: TranslationID?

    public init(
        bookID: String, chapter: Int, startVerse: Int, endVerse: Int,
        startChar: Int? = nil, endChar: Int? = nil, charTranslation: TranslationID? = nil
    ) {
        self.bookID = bookID
        self.chapter = chapter
        // The two ends are normalised, so a drag made upwards is stored
        // exactly as one made downwards. The character offsets belong to
        // their ends and turn over with them: a range dragged from the middle
        // of verse five back to verse three keeps "the middle of five" as
        // where it *stops*.
        if startVerse > endVerse {
            self.startVerse = endVerse
            self.endVerse = startVerse
            self.startChar = endChar
            self.endChar = startChar
        } else if startVerse == endVerse, let a = startChar, let b = endChar {
            self.startVerse = startVerse
            self.endVerse = endVerse
            self.startChar = min(a, b)
            self.endChar = max(a, b)
        } else {
            self.startVerse = startVerse
            self.endVerse = endVerse
            self.startChar = startChar
            self.endChar = endChar
        }
        self.charTranslation = charTranslation
    }

    enum CodingKeys: String, CodingKey {
        case bookID, chapter, startVerse, endVerse, startChar, endChar, charTranslation
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            bookID: try c.decode(String.self, forKey: .bookID),
            chapter: try c.decode(Int.self, forKey: .chapter),
            startVerse: try c.decode(Int.self, forKey: .startVerse),
            endVerse: try c.decode(Int.self, forKey: .endVerse),
            startChar: try c.decodeIfPresent(Int.self, forKey: .startChar),
            endChar: try c.decodeIfPresent(Int.self, forKey: .endChar),
            charTranslation: try c.decodeIfPresent(TranslationID.self, forKey: .charTranslation))
    }

    /// Whether this is a plain run of whole verses — the common case.
    public var isWholeVerses: Bool { startChar == nil && endChar == nil }

    /// The offsets, if they can be read by someone on `translation`. A phrase
    /// measured in one version means nothing in another, so anyone else gets
    /// the whole verses.
    public func chars(in translation: TranslationID) -> (start: Int?, end: Int?) {
        guard let measured = charTranslation, measured == translation else { return (nil, nil) }
        return (startChar, endChar)
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
        let name = Bible.book(id: bookID)?.referenceName ?? bookID
        if startVerse == endVerse { return "\(name) \(chapter):\(startVerse)" }
        return "\(name) \(chapter):\(startVerse)–\(endVerse)"
    }
}
