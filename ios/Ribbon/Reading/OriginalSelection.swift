import Foundation
import RibbonCore

// The original words under a selection (A60, §7.2 and §7.5): what the line
// over the toolbar says while a verse is held, and what the panel lays out
// when it opens. Worked out from what the page already has — the chapter's
// original words, this version's links and own text, and the Berean
// Standard's for a word this version folds into another. Pure, so the same
// answers can be checked without a phone.

/// Everything one chapter of the page knows about its original words.
struct OriginalContext {
    var bookID: String
    var chapter: Int
    /// The chapter's original words; nil where the bundle has none.
    var original: OriginalChapter?
    /// This page's links, by verse — bundled, or worked out on the phone.
    /// Nil while a licensed chapter is still coming.
    var readerLinks: [Int: [AlignmentLink]]?
    var readerTexts: [Int: String]
    /// Where a poetic line is glued to the one before in each verse's own
    /// text (`ScriptureChapter.ownSpanBreaks()`), so what a word is said as
    /// reads with the space the page shows as a line break.
    var readerBreaks: [Int: [Int]]
    /// The Berean Standard's links and text, for a word this page's
    /// version does not say on its own. Nil when the page is the Berean.
    var pivotLinks: [Int: [AlignmentLink]]?
    var pivotTexts: [Int: String]
    var pivotBreaks: [Int: [Int]]

    /// The selection's original words, verse by verse, in original order.
    /// Nil when none of its verses has any (a verse the earliest
    /// manuscripts lack).
    func selection(_ range: VerseRange) -> OriginalSelection? {
        guard let original else { return nil }
        var verses: [OriginalSelection.Verse] = []
        for verse in range.startVerse...range.endVerse {
            guard let all = original.words(verse: verse), !all.isEmpty else { continue }
            let at = Self.offsets(range, at: verse)
            let partial = at.from != nil || at.to != nil
            let links = readerLinks?[verse]
            var chosen: [Int] = []
            var fallback = false
            if partial {
                if let links {
                    chosen = OriginalWords.filledInterior(
                        OriginalWords.words(in: links, from: at.from, to: at.to),
                        linked: OriginalWords.linked(links))
                    chosen = chosen.filter { all.indices.contains($0) }
                }
                // Nothing this version links under the part chosen: the
                // whole verse, said to be the whole verse.
                if chosen.isEmpty {
                    chosen = Array(all.indices)
                    fallback = true
                }
            } else {
                chosen = Array(all.indices)
                // A whole verse is its whole words either way; but where this
                // version has no links for it, what each word is said as is
                // the Berean Standard's, not yours, and the panel says so.
                fallback = links?.isEmpty != false
            }
            verses.append(OriginalSelection.Verse(
                verse: verse, from: at.from, to: at.to,
                words: chosen.map { index in
                    OriginalSelection.Word(
                        verse: verse, index: index, word: all[index],
                        rendering: rendering(of: index, verse: verse))
                },
                isLinked: links?.isEmpty == false,
                isWholeFallback: fallback))
        }
        guard !verses.isEmpty else { return nil }
        let words = verses.flatMap(\.words).map(\.word)
        return OriginalSelection(
            bookID: bookID, chapter: chapter, range: range,
            language: Self.language(of: words, in: bookID), verses: verses)
    }

    /// The quiet line over the toolbar (§7.5). Held, it is the word under
    /// the finger — the link there, which may be one word or the few a
    /// phrase renders together. Moved, it is whatever the selection links.
    /// Nothing at all where nothing links, or the version has no links yet:
    /// quiet rather than wrong. A held offset below zero is a hold that
    /// found no word — the verse's number — and is quiet too; nil is a
    /// lift the handles (or VoiceOver) hold, not a finger.
    func line(_ range: VerseRange, held: Int?) -> OriginalLine? {
        if let held {
            guard held >= 0 else { return nil }
            let verse = range.startVerse
            guard let links = readerLinks?[verse], let all = original?.words(verse: verse) else { return nil }
            let indices = OriginalWords.words(in: links, from: held, to: held + 1)
                .filter { all.indices.contains($0) }
            guard let first = indices.first else { return nil }
            let words = indices.map {
                OriginalSelection.Word(verse: verse, index: $0, word: all[$0], rendering: rendering(of: $0, verse: verse))
            }
            return OriginalLine(
                words: words, language: Self.language(of: words.map(\.word), in: bookID),
                opens: OriginalSelection.WordID(verse: verse, index: first))
        }
        guard let selection = selection(range) else { return nil }
        let words = selection.verses
            .filter { $0.isLinked && !$0.isWholeFallback }
            .flatMap(\.words)
        guard !words.isEmpty else { return nil }
        return OriginalLine(words: words, language: selection.language, opens: nil)
    }

    /// What this page's version says for one original word; failing that,
    /// what the Berean Standard says; nil when neither says it on its own.
    func rendering(of index: Int, verse: Int) -> String? {
        if let links = readerLinks?[verse], let text = readerTexts[verse],
           let said = Self.rendering(of: index, in: links, text: text, breaks: readerBreaks[verse] ?? []) {
            return said
        }
        if let links = pivotLinks?[verse], let text = pivotTexts[verse] {
            return Self.rendering(of: index, in: links, text: text, breaks: pivotBreaks[verse] ?? [])
        }
        return nil
    }

    /// `OriginalWords.rendering`, read the way the page shows it: a link
    /// that runs over a poetic line break says the break as a space.
    static func rendering(of index: Int, in links: [AlignmentLink], text: String, breaks: [Int]) -> String? {
        let pieces = links
            .filter { $0.words.contains(index) }
            .sorted { $0.start < $1.start }
            .map { OriginalSelection.spoken(text, from: $0.start, to: $0.end, breaks: breaks) }
            .filter { !$0.isEmpty }
        return pieces.isEmpty ? nil : pieces.joined(separator: " … ")
    }

    /// The selection's own stretch of `verse`: from its start offset in the
    /// first verse, to its end offset in the last, inside one verse both.
    /// Nil, nil is the whole verse.
    static func offsets(_ range: VerseRange, at verse: Int) -> (from: Int?, to: Int?) {
        let single = range.startVerse == range.endVerse
        if verse == range.startVerse { return (range.startChar, single ? range.endChar : nil) }
        if verse == range.endVerse { return (nil, range.endChar) }
        return (nil, nil)
    }

    /// Greek in the New Testament. In the Old, Aramaic where most of the
    /// words are — Daniel 2:4 turns from one to the other mid-verse — and
    /// Hebrew otherwise.
    static func language(of words: [OriginalWord], in bookID: String) -> OriginalLanguage {
        let base = OriginalWords.language(of: bookID)
        guard base == .hebrew else { return base }
        let aramaic = words.filter(\.isAramaic).count
        return aramaic * 2 > words.count ? .aramaic : .hebrew
    }
}

struct OriginalSelection: Equatable {
    struct WordID: Hashable {
        var verse: Int
        var index: Int
    }

    struct Word: Equatable, Identifiable {
        var verse: Int
        /// Its position in the verse's original words.
        var index: Int
        var word: OriginalWord
        /// What your version says for it, or the Berean Standard where yours
        /// does not say it on its own; nil where neither does.
        var rendering: String?

        var id: WordID { WordID(verse: verse, index: index) }
    }

    struct Verse: Equatable, Identifiable {
        var verse: Int
        /// The selection's own stretch of the verse; nil, nil is all of it.
        var from: Int?
        var to: Int?
        /// The words under it, in original order.
        var words: [Word]
        /// This page's version has links for the verse.
        var isLinked: Bool
        /// Nothing this version links is under what was chosen — or it has
        /// no links for the verse at all — so these are the whole verse's
        /// words, and the panel says so (§7.2).
        var isWholeFallback: Bool

        var id: Int { verse }
        var isPartial: Bool { from != nil || to != nil }
        var chosen: Set<Int> { Set(words.map(\.index)) }
    }

    var bookID: String
    var chapter: Int
    var range: VerseRange
    var language: OriginalLanguage
    var verses: [Verse]

    var hasWholeFallback: Bool { verses.contains(where: \.isWholeFallback) }
    var spansVerses: Bool { verses.count > 1 }

    /// What one version says for the selection, verse by verse: the words
    /// its links give for the same original words, from the first to the
    /// last; or, where it cannot say them part by part, the whole verse,
    /// marked as such. `exact` is for the page's own version, which says
    /// exactly what was selected. Nil when the version's text is not here.
    /// `breaks` are the version's poetic line breaks, verse by verse
    /// (`ScriptureChapter.ownSpanBreaks()`): every piece is read through
    /// them, so "O LORD?" and "Who is like You" are two lines with a space
    /// between, not one word.
    func says(
        texts: [Int: String], breaks: [Int: [Int]], links: [Int: [AlignmentLink]]?, exact: Bool
    ) -> [OriginalSaying]? {
        var pieces: [OriginalSaying] = []
        for verse in verses {
            guard let text = texts[verse.verse] else { continue }
            let length = text.utf16.count
            let lines = breaks[verse.verse] ?? []
            if !verse.isPartial {
                pieces.append(OriginalSaying(
                    text: Self.spoken(text, from: 0, to: length, breaks: lines), isWholeVerse: false))
                continue
            }
            if exact {
                pieces.append(OriginalSaying(
                    text: Self.spoken(text, from: verse.from ?? 0, to: verse.to ?? length, breaks: lines),
                    isWholeVerse: false))
                continue
            }
            if let verseLinks = links?[verse.verse] {
                let found = OriginalWords.ranges(for: verse.chosen, in: verseLinks, text: text)
                if let first = found.first, let last = found.last {
                    let phrase = Self.spoken(text, from: first.start, to: last.end, breaks: lines)
                    if !phrase.isEmpty {
                        pieces.append(OriginalSaying(text: phrase, isWholeVerse: false))
                        continue
                    }
                }
            }
            pieces.append(OriginalSaying(
                text: Self.spoken(text, from: 0, to: length, breaks: lines), isWholeVerse: true))
        }
        return pieces.isEmpty ? nil : pieces
    }

    /// `text[from..<to]` (UTF-16 units, clamped) as a reader would see it,
    /// trimmed. A line of poetry is glued to the next in a verse's own text
    /// ("O LORD?Who is like You"), because the page breaks the line
    /// instead; here, out of the page, each break between two letters
    /// becomes a space. The same reading as Android's `spoken`.
    static func spoken(_ text: String, from: Int, to: Int, breaks: [Int]) -> String {
        let units = Array(text.utf16)
        let low = max(0, min(from, units.count))
        let high = max(low, min(to, units.count))
        let breaks = Set(breaks)
        var out: [UInt16] = []
        out.reserveCapacity(high - low)
        for i in low..<high {
            if i > low, breaks.contains(i), !isSpace(units[i - 1]), !isSpace(units[i]) {
                out.append(0x20)
            }
            out.append(units[i])
        }
        return String(decoding: out, as: UTF16.self)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func isSpace(_ unit: UInt16) -> Bool {
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return CharacterSet.whitespacesAndNewlines.contains(scalar)
    }
}

/// One piece of a version's words for a selection.
struct OriginalSaying: Equatable {
    var text: String
    /// The whole verse standing in for words this version could not match.
    var isWholeVerse: Bool
}

/// The quiet line over the toolbar (§7.5): `λόγος · logos · Word` for one
/// word; for several, the words and then how to say them, no rendering.
struct OriginalLine: Equatable {
    var words: [OriginalSelection.Word]
    var language: OriginalLanguage
    /// The word the panel opens on when the line is tapped — the held one.
    var opens: OriginalSelection.WordID?

    var isHebrew: Bool { language != .greek }
    /// The original words, in original order, isolated so a right-to-left
    /// run sits whole inside a left-to-right line (FSI … PDI).
    var original: String { "\u{2068}" + words.map(\.word.text).joined(separator: " ") + "\u{2069}" }
    var translit: String { words.map(\.word.translit).joined(separator: " ") }
    /// Your version's words, for one word only.
    var rendering: String? { words.count == 1 ? words[0].rendering : nil }
}
