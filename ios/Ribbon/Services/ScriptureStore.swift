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

    /// A ceiling on what stays decoded. Reading wants one book at a time
    /// and the cache was sized for that — but `search` reads all sixty-six,
    /// and an uncapped cache then holds the whole translation (five and a
    /// half megabytes of JSON, several times that once decoded) for the
    /// rest of the session. The system reclaims the app long before it
    /// reclaims a cache it was never told the size of. Costed in the bytes
    /// each book was decoded from, which is proportional and free to know.
    private static let cacheBudget = 12 * 1024 * 1024

    init() {
        cache.totalCostLimit = Self.cacheBudget
    }

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
            cache.setObject(CachedBook(book), forKey: key, cost: data.count)
            return book
        } catch {
            return nil
        }
    }

    func chapter(_ address: VerseAddress, translation: TranslationID) -> ScriptureChapter? {
        book(address.bookID, translation: translation)?.chapter(address.chapter)
    }

    func verseText(_ address: VerseAddress, translation: TranslationID) -> String? {
        chapter(address, translation: translation)?.text(forVerse: address.verse)
    }

    /// Scripture search (S23): matches book names, references, and text.
    /// Lives in the chooser, where the thing being searched lives.
    ///
    /// Off the main thread, and cancelled by the next keystroke. Reading
    /// the text of every book is about fifty thousand spans and nearly
    /// four million characters; on a cold cache it is also sixty-six files
    /// off the disk. None of that can happen between a finger landing and
    /// the next frame, and a main thread that tries is one the system
    /// eventually stops waiting for.
    func hits(matching query: String, translation: TranslationID, limit: Int = 40) async -> [SearchHit] {
        // Detached, because the work must leave whatever actor asked for it
        // — and then wired back to the caller by hand, because a detached
        // task inherits no cancellation and the loop's `Task.isCancelled`
        // would otherwise never come true.
        let work = Task.detached(priority: .userInitiated) { [self] in
            search(query, translation: translation, limit: limit)
        }
        return await withTaskCancellationHandler {
            await work.value
        } onCancel: {
            work.cancel()
        }
    }

    /// The search itself. Synchronous, and deliberately not called from
    /// anywhere a person is waiting on a frame — see `hits(matching:)`.
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
        let textTranslation = TranslationRegistry.isBundled(translation) ? translation : .bsb
        outer: for book in Bible.books {
            // A word half-typed is a search already superseded: the next
            // keystroke cancels this one, and it stops at the next book
            // rather than reading on to Revelation for nobody.
            if Task.isCancelled { return [] }
            guard let text = self.book(book.id, translation: textTranslation) else { continue }
            for chapter in text.chapters {
                var verse = 0
                for block in chapter.blocks {
                    for span in block.x {
                        if let v = span.v { verse = v }
                        // Case-insensitively, without lowercasing the span:
                        // a copy of every one of them is the greater part
                        // of what this loop used to cost.
                        guard verse > 0,
                              span.t.range(of: trimmed, options: .caseInsensitive) != nil
                        else { continue }
                        let address = VerseAddress(bookID: book.id, chapter: chapter.n, verse: verse)
                        if case .verse(let last, _)? = hits.last, last == address { continue }
                        hits.append(.verse(address, chapter.text(forVerse: verse) ?? span.t))
                        if hits.count >= limit { break outer }
                    }
                }
            }
        }
        return hits
    }

    enum SearchHit: Hashable, Sendable {
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
