import Foundation

// The original words under the English (A60). Each verse's Hebrew, Aramaic
// or Greek words ship in original order, one file per book
// (`Scripture/original/<BOOK>.json`, from the public-domain Berean Standard
// Bible translation tables and the Westminster Leningrad Codex, produced by
// tools/original_to_json.py). A word's 0-based position in its verse's list
// is the unit a mark is anchored to, so a mark made in one version can land
// on the same words in every other.
//
// The links between a version's own text and those words ship per version
// that has them (`Scripture/align/<id>/<BOOK>.json`); a version without them
// is linked on the phone by `PivotAligner`. The shapes on disk are compact
// tuples, so the decoding below is written out by hand.

/// The language a verse's original words are in. "The Hebrew", "the
/// Aramaic", "the Greek" in what a person reads; never "the original".
public enum OriginalLanguage: String, Codable, Hashable, Sendable {
    case hebrew
    case aramaic
    case greek
}

/// One original word: `[text, translit, strongs, parse]`, with a fifth
/// element `"a"` only when the word is Aramaic. An empty string on disk is
/// nil here — the tables give no Strong's number for some Hebrew suffixed
/// prepositions, and no parse for a few words.
public struct OriginalWord: Codable, Hashable, Sendable {
    public var text: String
    public var translit: String
    public var strongs: String?
    public var parse: String?
    public var isAramaic: Bool

    public init(text: String, translit: String, strongs: String? = nil, parse: String? = nil, isAramaic: Bool = false) {
        self.text = text
        self.translit = translit
        self.strongs = strongs?.isEmpty == true ? nil : strongs
        self.parse = parse?.isEmpty == true ? nil : parse
        self.isAramaic = isAramaic
    }

    public init(from decoder: Decoder) throws {
        var c = try decoder.unkeyedContainer()
        let text = try c.decode(String.self)
        let translit = c.isAtEnd ? "" : try c.decode(String.self)
        let strongs = c.isAtEnd ? nil : try c.decode(String.self)
        let parse = c.isAtEnd ? nil : try c.decode(String.self)
        let flag = c.isAtEnd ? nil : try c.decode(String.self)
        self.init(text: text, translit: translit, strongs: strongs, parse: parse, isAramaic: flag == "a")
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.unkeyedContainer()
        try c.encode(text)
        try c.encode(translit)
        try c.encode(strongs ?? "")
        try c.encode(parse ?? "")
        if isAramaic { try c.encode("a") }
    }

    /// Greek in the New Testament; in the Old, Hebrew unless the word is
    /// one of the Aramaic passages' (Daniel, Ezra, a verse of Jeremiah).
    public func language(in bookID: String) -> OriginalLanguage {
        OriginalWords.isNewTestament(bookID) ? .greek : (isAramaic ? .aramaic : .hebrew)
    }
}

/// One verse's original words, in original order: `{"v":1,"w":[…]}`.
public struct OriginalVerse: Codable, Hashable, Sendable {
    public var v: Int
    public var words: [OriginalWord]

    public init(v: Int, words: [OriginalWord]) {
        self.v = v
        self.words = words
    }

    enum CodingKeys: String, CodingKey {
        case v
        case words = "w"
    }
}

public struct OriginalChapter: Codable, Hashable, Sendable {
    public var n: Int
    public var verses: [OriginalVerse]

    public init(n: Int, verses: [OriginalVerse]) {
        self.n = n
        self.verses = verses
    }

    /// The verse's words, or nil when it has none here (a verse the
    /// earliest manuscripts lack, a psalm title's verse 0 when absent).
    public func words(verse: Int) -> [OriginalWord]? {
        verses.first { $0.v == verse }?.words
    }
}

/// One book's original words. `source` names the numbering the positions
/// count in; a stored anchor is honoured only when its source is this one.
public struct OriginalBook: Codable, Hashable, Sendable {
    public var id: String
    public var source: String
    public var chapters: [OriginalChapter]

    public init(id: String, source: String, chapters: [OriginalChapter]) {
        self.id = id
        self.source = source
        self.chapters = chapters
    }

    public func chapter(_ n: Int) -> OriginalChapter? {
        chapters.first { $0.n == n }
    }
}

/// A dictionary entry: `["λόγος","lógos","something said …"]` — the
/// dictionary form, how to say it, and Strong's own definition.
public struct LexiconEntry: Codable, Hashable, Sendable {
    public var lemma: String
    public var translit: String
    public var definition: String

    public init(lemma: String, translit: String, definition: String) {
        self.lemma = lemma
        self.translit = translit
        self.definition = definition
    }

    public init(from decoder: Decoder) throws {
        var c = try decoder.unkeyedContainer()
        lemma = try c.decode(String.self)
        translit = c.isAtEnd ? "" : try c.decode(String.self)
        definition = c.isAtEnd ? "" : try c.decode(String.self)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.unkeyedContainer()
        try c.encode(lemma)
        try c.encode(translit)
        try c.encode(definition)
    }
}

/// Strong's dictionary, keyed `"G3056"`, `"H430"` (`original/strongs.json`).
public struct Lexicon: Codable, Hashable, Sendable {
    public var entries: [String: LexiconEntry]

    public init(entries: [String: LexiconEntry]) {
        self.entries = entries
    }

    public init(from decoder: Decoder) throws {
        entries = try decoder.singleValueContainer().decode([String: LexiconEntry].self)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(entries)
    }

    /// The entry for a Strong's number. Leading zeros and a lower-case
    /// letter are forgiven ("g03056" finds G3056); the files never carry
    /// them, but a number typed or pasted might.
    public func entry(_ strongs: String) -> LexiconEntry? {
        if let entry = entries[strongs] { return entry }
        guard let key = Self.normalised(strongs) else { return nil }
        return entries[key]
    }

    static func normalised(_ strongs: String) -> String? {
        let trimmed = strongs.trimmingCharacters(in: .whitespaces)
        guard let first = trimmed.first, first.isASCII, first.isLetter else { return nil }
        let digits = trimmed.dropFirst()
        guard !digits.isEmpty, digits.allSatisfy({ $0.isASCII && $0.isNumber }), let n = Int(digits) else { return nil }
        return first.uppercased() + String(n)
    }
}

/// The grammar codes' long forms, `{"N-DFS":"Noun - Dative Feminine
/// Singular", …}` (`original/parsing.json`).
public struct Parsings: Codable, Hashable, Sendable {
    public var long: [String: String]

    public init(long: [String: String]) {
        self.long = long
    }

    public init(from decoder: Decoder) throws {
        long = try decoder.singleValueContainer().decode([String: String].self)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(long)
    }

    /// "Noun - Dative Feminine Singular" for "N-DFS"; nil for an empty
    /// code or one the file does not know.
    public func describe(_ short: String) -> String? {
        guard !short.isEmpty, let long = long[short], !long.isEmpty else { return nil }
        return long
    }
}

/// `[start, end, [word…]]`: a half-open range of one version's own text for
/// one verse, in UTF-16 units, and the positions of the original words that
/// range renders. A verse's links are sorted by start and never overlap.
public struct AlignmentLink: Codable, Hashable, Sendable {
    public var start: Int
    public var end: Int
    public var words: [Int]

    public init(start: Int, end: Int, words: [Int]) {
        self.start = start
        self.end = end
        self.words = words
    }

    public init(from decoder: Decoder) throws {
        var c = try decoder.unkeyedContainer()
        start = try c.decode(Int.self)
        end = try c.decode(Int.self)
        words = try c.decode([Int].self)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.unkeyedContainer()
        try c.encode(start)
        try c.encode(end)
        try c.encode(words)
    }
}

/// One verse's links: `{"v":1,"l":[…]}`.
public struct VerseLinks: Codable, Hashable, Sendable {
    public var v: Int
    public var links: [AlignmentLink]

    public init(v: Int, links: [AlignmentLink]) {
        self.v = v
        self.links = links
    }

    enum CodingKeys: String, CodingKey {
        case v
        case links = "l"
    }
}

public struct AlignmentChapter: Codable, Hashable, Sendable {
    public var n: Int
    public var verses: [VerseLinks]

    public init(n: Int, verses: [VerseLinks]) {
        self.n = n
        self.verses = verses
    }

    public func links(verse: Int) -> [AlignmentLink]? {
        verses.first { $0.v == verse }?.links
    }

    /// Every verse's links, keyed by verse — the shape the pure functions
    /// below take.
    public var byVerse: [Int: [AlignmentLink]] {
        var result: [Int: [AlignmentLink]] = [:]
        for verse in verses { result[verse.v] = verse.links }
        return result
    }
}

/// One version's links for one book. `basis` fingerprints the text the
/// ranges were measured in, so a re-converted text is detectably out of step.
public struct BookAlignment: Codable, Hashable, Sendable {
    public var id: String
    public var translation: TranslationID
    public var source: String
    public var basis: String
    public var chapters: [AlignmentChapter]

    public init(id: String, translation: TranslationID, source: String, basis: String, chapters: [AlignmentChapter]) {
        self.id = id
        self.translation = translation
        self.source = source
        self.basis = basis
        self.chapters = chapters
    }

    public func chapter(_ n: Int) -> AlignmentChapter? {
        chapters.first { $0.n == n }
    }
}

/// A half-open range of a verse's own text, in UTF-16 units.
public struct TextRange: Codable, Hashable, Sendable {
    public var start: Int
    public var end: Int

    public init(start: Int, end: Int) {
        self.start = start
        self.end = end
    }
}

/// Where a mark lands on one reader's page in one verse. `from` and `to`
/// are offsets into the verse's own text; both nil is the whole verse.
public struct MarkedSpan: Hashable, Sendable {
    public var verse: Int
    public var from: Int?
    public var to: Int?

    public init(verse: Int, from: Int? = nil, to: Int? = nil) {
        self.verse = verse
        self.from = from
        self.to = to
    }

    public var isWholeVerse: Bool { from == nil && to == nil }
}

/// The moves between a version's own text and the original words under it.
/// Pure, so both platforms can be held to the same answers.
public enum OriginalWords {
    static func isNewTestament(_ bookID: String) -> Bool {
        guard let index = Bible.books.firstIndex(where: { $0.id == bookID }) else { return false }
        return index >= 39
    }

    /// The language of a book's original words, setting Aramaic aside —
    /// Greek for the New Testament, Hebrew for the Old.
    public static func language(of bookID: String) -> OriginalLanguage {
        isNewTestament(bookID) ? .greek : .hebrew
    }

    /// Every position any link of a verse renders.
    public static func linked(_ links: [AlignmentLink]) -> Set<Int> {
        Set(links.flatMap(\.words))
    }

    /// The original words under `[from, to)` of a verse's own text: every
    /// link that overlaps it, sorted, each position once. A link that only
    /// touches the range's edge is not under it.
    public static func words(in links: [AlignmentLink], from: Int?, to: Int?) -> [Int] {
        let low = from ?? 0
        let high = to ?? .max
        var result = Set<Int>()
        for link in links where link.start < high && link.end > low {
            result.formUnion(link.words)
        }
        return result.sorted()
    }

    /// `words` with the untranslated words between the chosen ones filled
    /// in — the Greek article in "with God", which no English word renders.
    /// Only positions no link renders are added; a word the version puts
    /// somewhere else stays out.
    public static func filledInterior(_ words: [Int], linked: Set<Int>) -> [Int] {
        guard let low = words.min(), let high = words.max() else { return [] }
        var result = Set(words)
        if high - low > 1 {
            for index in (low + 1)..<high where !linked.contains(index) {
                result.insert(index)
            }
        }
        return result.sorted()
    }

    /// Where a set of original words sits in one version's verse: the ranges
    /// of every link that renders any of them, joined across gaps that hold
    /// only spaces, punctuation and words the version leaves unlinked.
    /// Scattered function words alone ("the … of … and") are what a weak
    /// link looks like, so a set that comes to nothing more gives no ranges
    /// and the caller marks the whole verse; a single run like "who is" is
    /// kept.
    public static func ranges(for words: Set<Int>, in links: [AlignmentLink], text: String) -> [TextRange] {
        let hits = links
            .filter { !words.isDisjoint(with: $0.words) }
            .sorted { $0.start < $1.start }
        var result: [TextRange] = []
        for link in hits {
            if let last = result.last,
               !links.contains(where: { $0.start < link.start && $0.end > last.end }) {
                result[result.count - 1].end = max(last.end, link.end)
            } else {
                result.append(TextRange(start: link.start, end: link.end))
            }
        }
        if result.count >= 2 {
            // Poetry lines are glued with no space ("of itall the days"), so
            // the text alone can run two words into one and pass a function
            // word off as a content word. Every link edge was a word break
            // when the links were made, so the edges stand in for the span
            // breaks this function is not given.
            let edges = links.flatMap { [$0.start, $0.end] }
            let tokens = PivotAligner.tokens(text, spanBreaks: edges)
            let inside = tokens.filter { token in
                result.contains { token.start < $0.end && token.end > $0.start }
            }
            if inside.allSatisfy({ !$0.isContent }) { return [] }
        }
        return result
    }

    /// The words a mark covers, worked out the moment it is made. Each end
    /// that stops part-way through its verse gets the original words under
    /// its part; an end that is a whole verse needs none. Without links or a
    /// source, or where nothing is linked, that end is left without words
    /// and readers on other versions see the whole verse there.
    public static func anchored(_ range: VerseRange, links: [Int: [AlignmentLink]]?, source: String?) -> VerseRange {
        guard let links, let source, !range.isWholeVerses else { return range }
        let single = range.startVerse == range.endVerse
        // Through the initialiser, so the sets are normalised and a mark
        // with no words anywhere carries no source either.
        return VerseRange(
            bookID: range.bookID, chapter: range.chapter,
            startVerse: range.startVerse, endVerse: range.endVerse,
            startChar: range.startChar, endChar: range.endChar,
            charTranslation: range.charTranslation,
            startWords: derived(range, verse: range.startVerse, links: links),
            endWords: single ? nil : derived(range, verse: range.endVerse, links: links),
            wordsSource: source)
    }

    /// Whether the mark stops part-way through `verse`.
    static func isPartial(_ range: VerseRange, at verse: Int) -> Bool {
        let single = range.startVerse == range.endVerse
        if verse == range.startVerse {
            return range.startChar != nil || (single && range.endChar != nil)
        }
        if verse == range.endVerse {
            return range.endChar != nil
        }
        return false
    }

    /// The author's offsets at `verse`: the start verse runs from its start
    /// offset (to the end offset, inside one verse); the end verse runs to
    /// its end offset.
    static func offsets(_ range: VerseRange, at verse: Int) -> (from: Int?, to: Int?) {
        let single = range.startVerse == range.endVerse
        if verse == range.startVerse {
            return (range.startChar, single ? range.endChar : nil)
        }
        if verse == range.endVerse {
            return (nil, range.endChar)
        }
        return (nil, nil)
    }

    /// The words under the author's part of `verse`, or nil when there are
    /// none to be had.
    static func derived(_ range: VerseRange, verse: Int, links: [Int: [AlignmentLink]]?) -> [Int]? {
        guard isPartial(range, at: verse), let verseLinks = links?[verse] else { return nil }
        let at = offsets(range, at: verse)
        let chosen = words(in: verseLinks, from: at.from, to: at.to)
        let filled = filledInterior(chosen, linked: linked(verseLinks))
        return filled.isEmpty ? nil : filled
    }

    /// Where one reader sees a mark, verse by verse.
    ///
    /// A verse the mark covers whole is whole. Where it stops part-way:
    /// a reader on the author's version sees the author's exact phrase;
    /// anyone else sees whatever their version says for the same original
    /// words — the words stored with the mark when its source is the one
    /// bundled, or else the words under the author's offsets in the author's
    /// version (`authorLinks`), which is how a mark made before marks
    /// carried words still follows them. Where neither is to be had, or the
    /// reader's version has no links for the verse, or the words come to
    /// nothing on their page, the reader sees the whole verse.
    public static func resolve(
        _ range: VerseRange,
        reader: TranslationID,
        readerLinks: [Int: [AlignmentLink]]?,
        readerTexts: [Int: String],
        source: String?,
        authorLinks: [Int: [AlignmentLink]]?
    ) -> [MarkedSpan] {
        var spans: [MarkedSpan] = []
        for verse in range.startVerse...range.endVerse {
            guard isPartial(range, at: verse) else {
                spans.append(MarkedSpan(verse: verse))
                continue
            }
            if range.charTranslation == reader {
                let at = offsets(range, at: verse)
                spans.append(MarkedSpan(verse: verse, from: at.from, to: at.to))
                continue
            }
            let stored = verse == range.startVerse ? range.startWords : range.endWords
            let set: [Int]?
            if let stored, let source, range.wordsSource == source {
                set = stored
            } else {
                set = derived(range, verse: verse, links: authorLinks)
            }
            if let set, let links = readerLinks?[verse], let text = readerTexts[verse] {
                let found = ranges(for: Set(set), in: links, text: text)
                if !found.isEmpty {
                    spans.append(contentsOf: found.map { MarkedSpan(verse: verse, from: $0.start, to: $0.end) })
                    continue
                }
            }
            spans.append(MarkedSpan(verse: verse))
        }
        return spans
    }

    /// What a version says for one original word: the text of every link
    /// that renders it, joined with " … " where the version splits it.
    public static func rendering(of word: Int, in links: [AlignmentLink], text: String) -> String? {
        let pieces = links
            .filter { $0.words.contains(word) }
            .sorted { $0.start < $1.start }
            .map { slice(text, from: $0.start, to: $0.end) }
            .filter { !$0.isEmpty }
        return pieces.isEmpty ? nil : pieces.joined(separator: " … ")
    }

    /// The words from the first range's start to the last range's end.
    public static func phrase(for ranges: [TextRange], text: String) -> String {
        guard let first = ranges.first, let last = ranges.last else { return "" }
        return slice(text, from: first.start, to: last.end)
    }

    // Following lands on the same words. A follower hears where the person
    // they follow is reading as a verse and how far down it their reading
    // line is (A58), and has always set that fraction against their own
    // page. In one version that is exact; across two it drifts, because the
    // versions put the words in a different order and take a different
    // length to say them. "Through Him all things were made" and "All
    // things were made through him" are the same verse with its first
    // words at opposite ends. So the person being followed also says which
    // original word is under their line, and the follower goes to wherever
    // their own version says that word. It adds onto the guess at where
    // someone is reading, as the owner put it, and brings everyone to the
    // same words, not just the same share of the verse.

    /// The original word under a reading line `part` of the way down a
    /// verse, on the page of the person being followed: the first link that
    /// ends past that point (the one under it, or the next one when the line
    /// sits in a gap), else the last, and the first of its words. Nil for a
    /// verse with no text or no links — the follower then keeps the fraction.
    public static func word(at part: Double, text: String, links: [AlignmentLink]) -> Int? {
        let length = text.utf16.count
        guard length > 0, !links.isEmpty else { return nil }
        let offset = Int((fraction(part) * Double(length)).rounded(.down))
        let sorted = links.sorted { $0.start < $1.start }
        let link = sorted.first { $0.end > offset } ?? sorted[sorted.count - 1]
        return link.words.min()
    }

    /// How far down a verse one original word sits on the follower's page:
    /// where the first link that renders it starts. A word this version
    /// leaves unsaid is placed at the next word up that it does say, so the
    /// line lands just after it rather than nowhere. Nil when the version
    /// says no word from there to the verse's end, or the verse has no text.
    public static func part(ofWord w: Int, text: String, links: [AlignmentLink]) -> Double? {
        let length = text.utf16.count
        guard length > 0 else { return nil }
        let sorted = links.sorted { $0.start < $1.start }
        let link = sorted.first { $0.words.contains(w) }
            ?? sorted
                .compactMap { link in link.words.min().map { (low: $0, link: link) } }
                .filter { $0.low >= w }
                .min { $0.low < $1.low }?.link
        guard let link else { return nil }
        return fraction(Double(link.start) / Double(length))
    }

    /// A heard reading point, set in the follower's version. When the
    /// person followed reads another version and said which original word
    /// was under their line — counted in the numbering bundled here — the
    /// point moves to where that word is on the follower's page. Otherwise
    /// it comes back as it was heard: on the same version the fraction is
    /// already exact, and without the word, the links or the text there is
    /// nothing truer to put in its place.
    public static func carried(
        _ point: ReadingPoint,
        word: Int?,
        wordsSource: String?,
        from: TranslationID?,
        to: TranslationID,
        source: String?,
        links: [AlignmentLink]?,
        text: String?
    ) -> ReadingPoint {
        guard let from, from != to,
              let word,
              let source, wordsSource == source,
              let links, let text,
              let part = part(ofWord: word, text: text, links: links)
        else { return point }
        var moved = point
        moved.part = part
        return moved
    }

    /// `part` held to 0...1. A fraction that is not a number reads as the
    /// top of the verse rather than reaching the arithmetic.
    static func fraction(_ part: Double) -> Double {
        part.isNaN ? 0 : min(max(part, 0), 1)
    }

    /// `text[from..<to]` in UTF-16 units, clamped to the text.
    static func slice(_ text: String, from: Int, to: Int) -> String {
        let units = Array(text.utf16)
        let low = max(0, min(from, units.count))
        let high = max(low, min(to, units.count))
        return String(decoding: units[low..<high], as: UTF16.self)
    }
}
