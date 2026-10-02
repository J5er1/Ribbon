import Foundation

// Word links for a version the app does not ship links for (A60) — the
// licensed ones, whose text only ever reaches the phone a chapter at a time.
// Nothing is stored or sent anywhere: the links are worked out on the phone,
// from the chapter it already holds, by lining the version's words up against
// the Berean Standard's for the same verse and borrowing the Berean
// Standard's links to the original wherever the two say the same word.
//
// tools/pivot_align.py is the reference: this file ports it step for step,
// the Kotlin port matches both, and Fixtures/pivot_cases.json holds the three
// to the same answers. The stop words and the irregular forms are generated
// into PivotLexicon.generated.swift by that script; edit them there.

public enum PivotAligner {
    /// A word of a verse's own text. `start` and `end` are UTF-16 offsets,
    /// half-open; `norm` is the word lower-cased with apostrophes taken out;
    /// `stem` is what it is matched on.
    public struct Token: Hashable, Sendable {
        public var start: Int
        public var end: Int
        public var norm: String
        public var stem: String
        public var isContent: Bool

        public init(start: Int, end: Int, norm: String, stem: String, isContent: Bool) {
            self.start = start
            self.end = end
            self.norm = norm
            self.stem = stem
            self.isContent = isContent
        }
    }

    // MARK: - Tokens

    /// ASCII letters and digits, and the Latin-1 and Latin Extended letters
    /// (U+00C0–U+024F) without the two signs among them, × and ÷.
    static func isTokenUnit(_ u: UInt16) -> Bool {
        switch u {
        case 0x30...0x39, 0x41...0x5A, 0x61...0x7A: return true
        case 0xD7, 0xF7: return false
        case 0xC0...0x24F: return true
        default: return false
        }
    }

    static func isApostrophe(_ u: UInt16) -> Bool { u == 0x27 || u == 0x2019 }

    /// One unit lower-cased by a fixed table, so both platforms agree to the
    /// letter whatever their Unicode library says: ASCII, Latin-1 and Latin
    /// Extended-A. Anything else is left as it is.
    static func lower(_ u: UInt16) -> UInt16 {
        switch u {
        case 0x41...0x5A: return u + 0x20
        case 0xC0...0xDE where u != 0xD7: return u + 0x20
        case 0x130: return 0x69  // İ → i, the simple mapping
        case 0x100...0x137 where u % 2 == 0: return u + 1
        case 0x139...0x148 where u % 2 == 1: return u + 1
        case 0x14A...0x177 where u % 2 == 0: return u + 1
        case 0x178: return 0xFF  // Ÿ → ÿ
        case 0x179...0x17E where u % 2 == 1: return u + 1
        default: return u
        }
    }

    /// The words of a verse's own text. A word is a run of letters and
    /// digits; an apostrophe belongs to a word only between two of its
    /// letters ("Lord’s", "o’er"), and a hyphen never does. `spanBreaks` are
    /// the offsets where the page starts a new span — a poetic line glued to
    /// the one before with no space — and always end a word.
    public static func tokens(_ text: String, spanBreaks: [Int] = []) -> [Token] {
        let units = Array(text.utf16)
        let breaks = Set(spanBreaks)
        var tokens: [Token] = []
        var start: Int? = nil
        var norm: [UInt16] = []

        func close(at end: Int) {
            guard let s = start else { return }
            let word = String(decoding: norm, as: UTF16.self)
            tokens.append(Token(
                start: s, end: end, norm: word, stem: stem(word),
                isContent: !PivotLexicon.stopWords.contains(word)))
            start = nil
            norm = []
        }

        for i in 0..<units.count {
            let u = units[i]
            if breaks.contains(i) { close(at: i) }
            if isTokenUnit(u) {
                if start == nil { start = i }
                norm.append(lower(u))
            } else if isApostrophe(u), start != nil, i + 1 < units.count,
                      isTokenUnit(units[i + 1]), !breaks.contains(i + 1) {
                // Inside a word, and dropped from its normal form.
                continue
            } else {
                close(at: i)
            }
        }
        close(at: units.count)
        return tokens
    }

    // MARK: - Stems

    /// What a word is matched on: its irregular base form when it has one
    /// ("spake" → "speak", "brethren" → "brother"), else its Porter stem.
    public static func stem(_ norm: String) -> String {
        PivotLexicon.irregular[norm] ?? porter(norm)
    }

    /// M. F. Porter's suffix-stripping algorithm, as published in 1980
    /// ("An algorithm for suffix stripping", Program 14(3)) — the original,
    /// not its later revisions. Expects a lower-case word.
    public static func porter(_ word: String) -> String {
        var w = Array(word.utf16)
        Porter.step1a(&w)
        Porter.step1b(&w)
        Porter.step1c(&w)
        Porter.step2(&w)
        Porter.step3(&w)
        Porter.step4(&w)
        Porter.step5(&w)
        return String(decoding: w, as: UTF16.self)
    }

    // MARK: - Alignment

    /// Links for a reader's verse, borrowed from the pivot's. Both texts are
    /// one verse's own text; the breaks are where each one's spans begin.
    ///
    /// The reader's words are lined up with the pivot's by the longest common
    /// subsequence of stems, a content word weighing three times a function
    /// word. Each reader word paired with a linked pivot word takes that
    /// link's original words. Content words keep theirs; a function word
    /// keeps its only between two content words that did, or beside one
    /// that took exactly the same words ("shall not perish"). Neighbouring
    /// words with the same original words become one link.
    public static func align(
        reader: String, readerBreaks: [Int],
        pivot: String, pivotBreaks: [Int],
        pivotLinks: [AlignmentLink]
    ) -> [AlignmentLink] {
        let r = tokens(reader, spanBreaks: readerBreaks)
        let p = tokens(pivot, spanBreaks: pivotBreaks)
        let n = r.count
        let m = p.count
        guard n > 0, m > 0 else { return [] }

        // 1. Each pivot word's original words: those of the link it sits in.
        let pivotWords: [[Int]?] = p.map { token in
            pivotLinks.first { $0.start <= token.start && token.end <= $0.end }?.words
        }

        // 2. Weighted longest common subsequence.
        func matches(_ i: Int, _ j: Int) -> Bool { r[i].stem == p[j].stem }
        func weight(_ i: Int) -> Int { r[i].isContent ? 3 : 1 }
        var d = Array(repeating: Array(repeating: 0, count: m + 1), count: n + 1)
        for i in 1...n {
            for j in 1...m {
                var best = max(d[i - 1][j], d[i][j - 1])
                if matches(i - 1, j - 1) {
                    best = max(best, d[i - 1][j - 1] + weight(i - 1))
                }
                d[i][j] = best
            }
        }
        var paired = [Int?](repeating: nil, count: n)
        var i = n
        var j = m
        while i > 0 && j > 0 {
            if matches(i - 1, j - 1) && d[i][j] == d[i - 1][j - 1] + weight(i - 1) {
                paired[i - 1] = j - 1
                i -= 1
                j -= 1
            } else if d[i][j] == d[i - 1][j] {
                i -= 1
            } else {
                j -= 1
            }
        }

        // 3. Candidates, and which of them stay.
        var candidate = [[Int]?](repeating: nil, count: n)
        for k in 0..<n {
            if let pj = paired[k], let words = pivotWords[pj] { candidate[k] = words }
        }
        let content = (0..<n).filter { candidate[$0] != nil && r[$0].isContent }
        var kept = [[Int]?](repeating: nil, count: n)
        for k in content { kept[k] = candidate[k] }
        for k in 0..<n where candidate[k] != nil && !r[k].isContent {
            let words = candidate[k]
            let between = content.contains { $0 < k } && content.contains { $0 > k }
            let besideSame =
                (k > 0 && r[k - 1].isContent && candidate[k - 1] != nil && candidate[k - 1] == words)
                || (k + 1 < n && r[k + 1].isContent && candidate[k + 1] != nil && candidate[k + 1] == words)
            if between || besideSame { kept[k] = words }
        }

        // 4. Neighbours with the same words become one link.
        var links: [AlignmentLink] = []
        var previous: Int? = nil
        for k in 0..<n {
            guard let words = kept[k] else { previous = nil; continue }
            if let prev = previous, kept[prev] == words, var last = links.popLast() {
                last.end = r[k].end
                links.append(last)
            } else {
                links.append(AlignmentLink(start: r[k].start, end: r[k].end, words: words))
            }
            previous = k
        }
        return links.sorted { $0.start < $1.start }
    }
}

/// The steps of Porter's algorithm over a lower-case word's UTF-16 units.
/// Any unit that is not a, e, i, o, u, or a y after a consonant, counts as a
/// consonant — which is what Porter's definition says for letters outside
/// the English alphabet too.
enum Porter {
    typealias Word = [UInt16]

    static func units(_ s: String) -> Word { Array(s.utf16) }

    static func isConsonant(_ w: Word, _ i: Int) -> Bool {
        switch w[i] {
        case 0x61, 0x65, 0x69, 0x6F, 0x75: return false  // a e i o u
        case 0x79: return i == 0 ? true : !isConsonant(w, i - 1)  // y
        default: return true
        }
    }

    /// m in [C](VC)^m[V], over the first `length` units.
    static func measure(_ w: Word, _ length: Int) -> Int {
        var n = 0
        var i = 0
        while i < length && isConsonant(w, i) { i += 1 }
        while i < length {
            while i < length && !isConsonant(w, i) { i += 1 }
            if i >= length { break }
            while i < length && isConsonant(w, i) { i += 1 }
            n += 1
        }
        return n
    }

    /// *v* — the first `length` units contain a vowel.
    static func hasVowel(_ w: Word, _ length: Int) -> Bool {
        (0..<length).contains { !isConsonant(w, $0) }
    }

    /// *d — the first `length` units end with a double consonant.
    static func endsDouble(_ w: Word, _ length: Int) -> Bool {
        length >= 2 && w[length - 1] == w[length - 2] && isConsonant(w, length - 1)
    }

    /// *o — the first `length` units end consonant–vowel–consonant, the last
    /// not w, x or y.
    static func endsCVC(_ w: Word, _ length: Int) -> Bool {
        guard length >= 3,
              isConsonant(w, length - 3), !isConsonant(w, length - 2), isConsonant(w, length - 1)
        else { return false }
        let last = w[length - 1]
        return last != 0x77 && last != 0x78 && last != 0x79
    }

    static func ends(_ w: Word, _ suffix: String) -> Bool {
        let s = units(suffix)
        return w.count >= s.count && Array(w[(w.count - s.count)...]) == s
    }

    static func replace(_ w: inout Word, _ suffix: String, with replacement: String) {
        w.removeLast(suffix.utf16.count)
        w.append(contentsOf: units(replacement))
    }

    static func step1a(_ w: inout Word) {
        if ends(w, "sses") { replace(&w, "sses", with: "ss") }
        else if ends(w, "ies") { replace(&w, "ies", with: "i") }
        else if ends(w, "ss") { return }
        else if ends(w, "s") { w.removeLast() }
    }

    static func step1b(_ w: inout Word) {
        var tidy = false
        if ends(w, "eed") {
            if measure(w, w.count - 3) > 0 { replace(&w, "eed", with: "ee") }
        } else if ends(w, "ed") {
            if hasVowel(w, w.count - 2) { w.removeLast(2); tidy = true }
        } else if ends(w, "ing") {
            if hasVowel(w, w.count - 3) { w.removeLast(3); tidy = true }
        }
        guard tidy else { return }
        if ends(w, "at") { replace(&w, "at", with: "ate") }
        else if ends(w, "bl") { replace(&w, "bl", with: "ble") }
        else if ends(w, "iz") { replace(&w, "iz", with: "ize") }
        else if endsDouble(w, w.count), let last = w.last,
                last != 0x6C, last != 0x73, last != 0x7A {  // not l, s, z
            w.removeLast()
        } else if measure(w, w.count) == 1 && endsCVC(w, w.count) {
            w.append(0x65)  // e
        }
    }

    static func step1c(_ w: inout Word) {
        if ends(w, "y") && hasVowel(w, w.count - 1) {
            w[w.count - 1] = 0x69  // i
        }
    }

    /// The longest of `rules` the word ends with — and only that one, even
    /// when its condition fails, as the paper specifies.
    static func longest(_ w: Word, _ rules: [(String, String)]) -> (String, String)? {
        rules.filter { ends(w, $0.0) }.max { $0.0.utf16.count < $1.0.utf16.count }
    }

    static let step2Rules: [(String, String)] = [
        ("ational", "ate"), ("tional", "tion"), ("enci", "ence"), ("anci", "ance"),
        ("izer", "ize"), ("abli", "able"), ("alli", "al"), ("entli", "ent"),
        ("eli", "e"), ("ousli", "ous"), ("ization", "ize"), ("ation", "ate"),
        ("ator", "ate"), ("alism", "al"), ("iveness", "ive"), ("fulness", "ful"),
        ("ousness", "ous"), ("aliti", "al"), ("iviti", "ive"), ("biliti", "ble"),
    ]

    static let step3Rules: [(String, String)] = [
        ("icate", "ic"), ("ative", ""), ("alize", "al"), ("iciti", "ic"),
        ("ical", "ic"), ("ful", ""), ("ness", ""),
    ]

    static let step4Suffixes: [String] = [
        "al", "ance", "ence", "er", "ic", "able", "ible", "ant", "ement", "ment",
        "ent", "ion", "ou", "ism", "ate", "iti", "ous", "ive", "ize",
    ]

    static func step2(_ w: inout Word) {
        guard let rule = longest(w, step2Rules) else { return }
        let (suffix, replacement) = rule
        if measure(w, w.count - suffix.utf16.count) > 0 { replace(&w, suffix, with: replacement) }
    }

    static func step3(_ w: inout Word) {
        guard let rule = longest(w, step3Rules) else { return }
        let (suffix, replacement) = rule
        if measure(w, w.count - suffix.utf16.count) > 0 { replace(&w, suffix, with: replacement) }
    }

    static func step4(_ w: inout Word) {
        guard let suffix = step4Suffixes.filter({ ends(w, $0) }).max(by: { $0.utf16.count < $1.utf16.count })
        else { return }
        let stem = w.count - suffix.utf16.count
        guard measure(w, stem) > 1 else { return }
        if suffix == "ion" {
            guard stem > 0, w[stem - 1] == 0x73 || w[stem - 1] == 0x74 else { return }  // s or t
        }
        w.removeLast(suffix.utf16.count)
    }

    static func step5(_ w: inout Word) {
        // 5a
        if ends(w, "e") {
            let stem = w.count - 1
            let m = measure(w, stem)
            if m > 1 || (m == 1 && !endsCVC(w, stem)) { w.removeLast() }
        }
        // 5b
        if measure(w, w.count) > 1 && endsDouble(w, w.count) && w.last == 0x6C {  // l
            w.removeLast()
        }
    }
}
