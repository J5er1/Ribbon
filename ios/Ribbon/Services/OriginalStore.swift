import Foundation
import RibbonCore

// The original words under the English (A60): each verse's Hebrew, Aramaic
// or Greek words, Strong's dictionary and the grammar codes' long forms, and
// each version's links from its own text to those words. Like the Scripture
// beside them they ship in the bundle, are read lazily and kept the way
// ScriptureStore keeps a book.
//
// The two bundled versions have their links worked out ahead of time. A
// licensed version's are worked out here, on the phone, from the chapter it
// already holds, lined up against the Berean Standard's words and borrowing
// its links (`PivotAligner`). Nothing worked out here is stored or sent.

final class OriginalStore: @unchecked Sendable {
    static let shared = OriginalStore()

    /// What stays decoded of the original words. A book of them is as heavy
    /// as a book of English — Jeremiah's is a megabyte and a third — and
    /// they share the phone with the English they sit under, so they have a
    /// ceiling of their own rather than a share of Scripture's. Costed in
    /// the bytes each was decoded from, as ScriptureStore's is.
    private static let bookBudget = 8 * 1024 * 1024
    /// The links are a fraction of that: a version's for one book is a few
    /// hundred kilobytes at most.
    private static let alignmentBudget = 4 * 1024 * 1024
    /// One chapter's links by verse, for every version asked about. A
    /// licensed version's took an alignment to make, so they are kept for
    /// as long as there is room; a page asks for them on every redraw.
    private static let chaptersKept = 64
    /// The smallest book of original words, read for the source key when
    /// nothing else has been: every book counts in the same numbering.
    private static let smallestBook = "3JN"

    private let scripture: ScriptureStore
    private let books = NSCache<NSString, Box<OriginalBook>>()
    private let alignments = NSCache<NSString, Box<BookAlignment>>()
    private let chapterLinks = NSCache<NSString, Box<[Int: [AlignmentLink]]>>()

    /// Guards the three below, each read once and kept.
    private let lock = NSLock()
    private var loadedLexicon: Lexicon?
    private var loadedParsings: Parsings?
    private var knownSource: String?

    init(scripture: ScriptureStore = .shared) {
        self.scripture = scripture
        books.totalCostLimit = Self.bookBudget
        alignments.totalCostLimit = Self.alignmentBudget
        chapterLinks.countLimit = Self.chaptersKept
    }

    private final class Box<Value> {
        let value: Value
        init(_ value: Value) { self.value = value }
    }

    // MARK: The words

    /// A book's original words, in original order, verse by verse
    /// (`Scripture/original/<BOOK>.json`).
    func original(_ bookID: String) -> OriginalBook? {
        let key = bookID as NSString
        if let cached = books.object(forKey: key) {
            return cached.value
        }
        guard let loaded = decode(OriginalBook.self, named: bookID, in: "original") else { return nil }
        books.setObject(Box(loaded.value), forKey: key, cost: loaded.cost)
        remember(source: loaded.value.source)
        return loaded.value
    }

    /// One verse's original words, or nil where it has none (a verse the
    /// earliest manuscripts lack).
    func words(_ address: VerseAddress) -> [OriginalWord]? {
        original(address.bookID)?.chapter(address.chapter)?.words(verse: address.verse)
    }

    /// The numbering every bundled anchor counts in. A mark's stored words
    /// are honoured only when they were counted in this one, so it is the
    /// same in every book's file and taken from whichever was read first.
    var source: String? {
        lock.lock()
        let known = knownSource
        lock.unlock()
        if let known { return known }
        return original(Self.smallestBook)?.source
    }

    /// Strong's dictionary (`original/strongs.json`), read the first time it
    /// is asked for.
    ///
    /// Read outside the lock, as the grammar below is: the dictionary is over
    /// a megabyte, and the main thread asks `source` under the same lock on
    /// every reading report. Two first readers may both decode; one wins.
    var lexicon: Lexicon? {
        lock.lock()
        let held = loadedLexicon
        lock.unlock()
        if let held { return held }
        guard let read = decode(Lexicon.self, named: "strongs", in: "original")?.value else { return nil }
        lock.lock()
        defer { lock.unlock() }
        if loadedLexicon == nil { loadedLexicon = read }
        return loadedLexicon
    }

    /// The grammar codes' long forms (`original/parsing.json`), read the
    /// first time they are asked for.
    var parsings: Parsings? {
        lock.lock()
        let held = loadedParsings
        lock.unlock()
        if let held { return held }
        guard let read = decode(Parsings.self, named: "parsing", in: "original")?.value else { return nil }
        lock.lock()
        defer { lock.unlock() }
        if loadedParsings == nil { loadedParsings = read }
        return loadedParsings
    }

    // MARK: The links

    /// A bundled version's links for a whole book
    /// (`Scripture/align/<id>/<BOOK>.json`). Nil for a version that ships
    /// none.
    func alignment(_ bookID: String, translation: TranslationID) -> BookAlignment? {
        guard TranslationRegistry.hasBundledWordLinks(translation) else { return nil }
        let key = "\(translation.rawValue)/\(bookID)" as NSString
        if let cached = alignments.object(forKey: key) {
            return cached.value
        }
        guard let loaded = decode(
            BookAlignment.self, named: bookID, in: "align/\(translation.rawValue)")
        else { return nil }
        alignments.setObject(Box(loaded.value), forKey: key, cost: loaded.cost)
        return loaded.value
    }

    /// One version's links for one chapter, keyed by verse — the shape
    /// `OriginalWords` takes.
    ///
    /// A bundled version's come from its file. Any other version's are lined
    /// up here against the Berean Standard, verse by verse, from
    /// `readerChapter` — the chapter as this phone holds it in that version —
    /// and kept, so a page that redraws does not align again. Without the
    /// chapter there is nothing to line up and the answer is nil, which the
    /// callers take as "whole verses here"; it is asked again next time,
    /// since the chapter may have come by then. `readerChapter` is only
    /// read when it is needed.
    func links(
        _ translation: TranslationID, bookID: String, chapter: Int,
        readerChapter: @autoclosure () -> ScriptureChapter? = nil
    ) -> [Int: [AlignmentLink]]? {
        let key = "\(translation.rawValue)/\(bookID)/\(chapter)" as NSString
        if let cached = chapterLinks.object(forKey: key) {
            return cached.value
        }
        let found: [Int: [AlignmentLink]]?
        if TranslationRegistry.hasBundledWordLinks(translation) {
            found = alignment(bookID, translation: translation)?.chapter(chapter)?.byVerse
        } else if let reader = readerChapter() {
            found = pivoted(reader, bookID: bookID, chapter: chapter)
        } else {
            found = nil
        }
        guard let found else { return nil }
        chapterLinks.setObject(Box(found), forKey: key)
        return found
    }

    /// A chapter of a version with no links of its own, linked through the
    /// Berean Standard's: each verse's words lined up with the Berean's for
    /// the same verse, taking its links wherever the two say the same word.
    /// A poetic line glued to the one before is a word break on both sides.
    /// A verse that comes to no links is left out, as a bundled file leaves
    /// it out.
    private func pivoted(_ reader: ScriptureChapter, bookID: String, chapter: Int) -> [Int: [AlignmentLink]]? {
        guard let pivotChapter = scripture.book(bookID, translation: .bsb)?.chapter(chapter),
              let pivotLinks = links(.bsb, bookID: bookID, chapter: chapter)
        else { return nil }
        let readerTexts = reader.ownTexts()
        let readerBreaks = reader.ownSpanBreaks()
        let pivotTexts = pivotChapter.ownTexts()
        let pivotBreaks = pivotChapter.ownSpanBreaks()
        var result: [Int: [AlignmentLink]] = [:]
        for (verse, text) in readerTexts {
            guard let pivot = pivotTexts[verse], let links = pivotLinks[verse], !links.isEmpty else { continue }
            let aligned = PivotAligner.align(
                reader: text, readerBreaks: readerBreaks[verse] ?? [],
                pivot: pivot, pivotBreaks: pivotBreaks[verse] ?? [],
                pivotLinks: links)
            if !aligned.isEmpty { result[verse] = aligned }
        }
        return result
    }

    // MARK: Reading the bundle

    private func remember(source: String) {
        lock.lock()
        if knownSource == nil { knownSource = source }
        lock.unlock()
    }

    /// A file under `Scripture/<folder>`, decoded, and the bytes it was
    /// decoded from.
    private func decode<Value: Decodable>(
        _ type: Value.Type, named name: String, in folder: String
    ) -> (value: Value, cost: Int)? {
        guard let url = Bundle.main.url(
            forResource: name, withExtension: "json", subdirectory: "Scripture/\(folder)")
        else { return nil }
        do {
            let data = try Data(contentsOf: url)
            let value = try JSONDecoder().decode(Value.self, from: data)
            return (value, data.count)
        } catch {
            return nil
        }
    }
}
