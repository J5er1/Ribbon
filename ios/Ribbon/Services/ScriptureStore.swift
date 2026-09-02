import Foundation
import RibbonCore

// Scripture lives in the bundle: both launch translations, whole, so
// offline reading is a first-class case and nothing is ever "locked"
// (§2.5). One JSON file per book per translation, loaded lazily and cached.
//
// (S21 shows download management for the day translations outgrow the
// bundle; at launch, everything is already on the phone.)

final class ScriptureStore: @unchecked Sendable {
    static let shared = ScriptureStore()

    private let cache = NSCache<NSString, CachedBook>()

    private final class CachedBook {
        let book: ScriptureBookText
        init(_ book: ScriptureBookText) { self.book = book }
    }

    func book(_ bookID: String, translation: TranslationID) -> ScriptureBookText? {
        let key = "\(translation.rawValue)/\(bookID)" as NSString
        if let cached = cache.object(forKey: key) {
            return cached.book
        }
        guard
            let url = Bundle.main.url(
                forResource: bookID, withExtension: "json",
                subdirectory: "Scripture/\(translation.rawValue)")
                ?? Bundle.main.url(forResource: bookID, withExtension: "json")
        else { return nil }
        do {
            let data = try Data(contentsOf: url)
            let book = try JSONDecoder().decode(ScriptureBookText.self, from: data)
            cache.setObject(CachedBook(book), forKey: key)
            return book
        } catch {
            return nil
        }
    }

    func chapter(_ address: VerseAddress, translation: TranslationID) -> ScriptureChapter? {
        book(address.bookID, translation: translation)?.chapter(address.chapter)
    }

    /// The words of one verse, in a translation. A licensed translation
    /// answers from its cache of the open book; failing that, the bundled
    /// Berean text stands in — Scripture is never locked (§2.5), and a
    /// quote, a preview or an ember's highlight must never simply vanish.
    func verseText(_ address: VerseAddress, translation: TranslationID) -> String? {
        if let text = chapter(address, translation: translation)?.text(forVerse: address.verse) {
            return text
        }
        if let licensed = TranslationRegistry.translation(for: translation), !licensed.isBundled,
           let text = cachedRemoteChapter(address, translation: licensed)?.text(forVerse: address.verse) {
            return text
        }
        return chapter(address, translation: .bsb)?.text(forVerse: address.verse)
    }

    /// The words of a run of verses, joined.
    func rangeText(_ range: VerseRange, translation: TranslationID) -> String? {
        let parts = range.verses.compactMap { verse in
            verseText(VerseAddress(bookID: range.bookID, chapter: range.chapter, verse: verse), translation: translation)
        }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    /// Scripture search (S23): matches book names, references, and text.
    /// Lives in the chooser, where the thing being searched lives.
    func search(_ query: String, translation: TranslationID, limit: Int = 40) -> [SearchHit] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { return [] }

        var hits: [SearchHit] = []

        // A reference like "Mark 4:9" or "Mark 4".
        if let ref = Self.parseReference(trimmed) {
            hits.append(.reference(ref))
        }

        // Book names.
        for book in Bible.books where book.name.localizedCaseInsensitiveContains(trimmed) {
            hits.append(.book(book.id))
        }

        // Text, across the books on this phone. Licensed translations keep
        // only the open book locally, so their text search runs over the
        // bundled Berean text instead — the hits are addresses, and an
        // address opens in the reader's own translation.
        let needle = trimmed.lowercased()
        let textTranslation = TranslationRegistry.isBundled(translation) ? translation : .bsb
        outer: for book in Bible.books {
            guard let text = self.book(book.id, translation: textTranslation) else { continue }
            for chapter in text.chapters {
                var verse = 0
                for block in chapter.blocks {
                    for span in block.x {
                        if let v = span.v { verse = v }
                        if span.t.lowercased().contains(needle), verse > 0 {
                            let address = VerseAddress(bookID: book.id, chapter: chapter.n, verse: verse)
                            if case .verse(let last, _)? = hits.last, last == address { continue }
                            hits.append(.verse(address, chapter.text(forVerse: verse) ?? span.t))
                            if hits.count >= limit { break outer }
                        }
                    }
                }
            }
        }
        return hits
    }

    enum SearchHit: Hashable {
        case reference(VerseAddress)
        case book(String)
        case verse(VerseAddress, String)
    }

    /// "Mark 4:9", "mark 4", "1 john 3:2"
    static func parseReference(_ query: String) -> VerseAddress? {
        let parts = query.split(separator: " ")
        guard parts.count >= 2, let last = parts.last else { return nil }
        let numbers = last.split(separator: ":")
        guard let chapter = numbers.first.flatMap({ Int($0) }) else { return nil }
        var verse = 1
        if numbers.count == 2 {
            guard let v = Int(numbers[1]) else { return nil }
            verse = v
        } else if numbers.count > 2 {
            return nil
        }
        let name = parts.dropLast().joined(separator: " ")
        guard
            !name.isEmpty,
            let book = Bible.books.first(where: {
                $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame
                    || $0.name.lowercased().hasPrefix(name.lowercased())
            })
        else { return nil }
        guard chapter >= 1, chapter <= book.chapterCount else { return nil }
        return VerseAddress(bookID: book.id, chapter: chapter, verse: verse)
    }
}
