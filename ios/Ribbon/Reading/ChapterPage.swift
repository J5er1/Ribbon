import Foundation

// The chapter as typeset, in plain characters: where every verse's own text
// sits on the page, and where its number is printed. Foundation only — no
// UIKit — so the mapping between the page and the verses can be checked
// without a phone (A62): a selection on the page becomes the stretch of
// verses a mark is made of, and a stretch becomes the page range to select.

/// One run of a verse's text, in both coordinate systems at once: where it
/// begins inside the verse, and where it begins on the page.
struct TextSegment: Equatable {
    var verse: Int
    var textStart: Int
    var pageStart: Int
    var length: Int
}

/// A stretch of the page's verses, as a selection holds it: from an offset
/// into the first verse's own text to one into the last's, half-open. A nil
/// end is the whole of that end — a mark on a phrase carries offsets, a mark
/// on whole verses carries none, so it reads the same in another version
/// (A41g).
struct PageEnds: Equatable {
    var startVerse: Int
    var startChar: Int?
    var endVerse: Int
    var endChar: Int?

    /// One verse, all of it.
    static func whole(_ verse: Int) -> PageEnds {
        PageEnds(startVerse: verse, startChar: nil, endVerse: verse, endChar: nil)
    }

    var isWholeVerses: Bool { startChar == nil && endChar == nil }
    /// The same verses, every one of them whole.
    var wholeVerses: PageEnds { PageEnds(startVerse: startVerse, startChar: nil, endVerse: endVerse, endChar: nil) }
}

/// The chapter, typeset: where every verse ended up in the string.
struct ChapterPage {
    private(set) var verseText: [Int: String] = [:]
    private(set) var verseSegments: [Int: [TextSegment]] = [:]
    /// Every segment in page order — the order they are set in — so a
    /// selection is found by a binary search rather than a walk.
    private(set) var ordered: [TextSegment] = []
    /// The verses in the order the page sets them, and each one's place in
    /// that order.
    private(set) var verseOrder: [Int] = []
    private var orderIndex: [Int: Int] = [:]
    /// Where each verse's number is printed — the figures and the thin space
    /// after them. The chapter's first verse has none.
    private(set) var numbers: [Int: NSRange] = [:]

    // MARK: Setting

    /// A run of a verse's own text, set at `pageStart`.
    mutating func append(_ text: String, verse: Int, at pageStart: Int) {
        let textStart = (verseText[verse] as NSString?)?.length ?? 0
        let length = (text as NSString).length
        let segment = TextSegment(verse: verse, textStart: textStart, pageStart: pageStart, length: length)
        verseText[verse, default: ""] += text
        verseSegments[verse, default: []].append(segment)
        assert(ordered.last.map { $0.pageStart <= pageStart } ?? true, "the page is set in order")
        ordered.append(segment)
        if orderIndex[verse] == nil {
            orderIndex[verse] = verseOrder.count
            verseOrder.append(verse)
        }
    }

    /// A verse's number, set at `pageStart`.
    mutating func number(_ verse: Int, at pageStart: Int, length: Int) {
        numbers[verse] = NSRange(location: pageStart, length: length)
    }

    // MARK: Reading

    func length(of verse: Int) -> Int { (verseText[verse] as NSString?)?.length ?? 0 }

    /// Where a stretch of one verse's own text sits on the page. `from` and
    /// `to` are offsets into the verse's text, half-open; nil means "from
    /// the beginning" and "to the end". A list, because a verse can be
    /// several runs — every line of a psalm is one.
    func pageRanges(verse: Int, from: Int?, to: Int?) -> [NSRange] {
        guard let segments = verseSegments[verse] else { return [] }
        let low = from ?? 0
        let high = to ?? length(of: verse)
        var result: [NSRange] = []
        for segment in segments {
            let start = max(low, segment.textStart)
            let end = min(high, segment.textStart + segment.length)
            if end > start {
                result.append(NSRange(location: segment.pageStart + (start - segment.textStart), length: end - start))
            }
        }
        return result
    }

    /// Snap an offset to the nearest word edge in the given direction: the
    /// start of a word going back, the end of one going forward. From an
    /// edge, that is the next word's.
    func wordEdge(verse: Int, offset: Int, forward: Bool) -> Int {
        let text = (verseText[verse] ?? "") as NSString
        let length = text.length
        var i = max(0, min(length, offset))
        if forward {
            while i < length, Self.isBlank(text.character(at: i)) { i += 1 }
            while i < length, !Self.isBlank(text.character(at: i)) { i += 1 }
        } else {
            while i > 0, Self.isBlank(text.character(at: i - 1)) { i -= 1 }
            while i > 0, !Self.isBlank(text.character(at: i - 1)) { i -= 1 }
        }
        return i
    }

    /// One word further on, or one back, from an offset that is already on
    /// an edge.
    func wordStep(verse: Int, offset: Int, forward: Bool) -> Int {
        let edge = wordEdge(verse: verse, offset: offset, forward: forward)
        return edge == offset ? wordEdge(verse: verse, offset: forward ? offset + 1 : offset - 1, forward: forward) : edge
    }

    // MARK: Selection (A62)

    /// A selection on the page as a stretch of the verses' own text: the
    /// first verse letter at or after its start, the last before its end.
    /// What is not a verse's own text — the running head, the first-run
    /// hint, a verse's number, a psalm's title, the paragraph newlines — is
    /// simply not counted. Nil when the selection holds no verse letter.
    func ends(of selection: NSRange) -> PageEnds? {
        guard selection.length > 0 else { return nil }
        let lo = selection.location, hi = NSMaxRange(selection)
        var i = firstEnding(after: lo)
        guard i < ordered.count, ordered[i].pageStart < hi else { return nil }
        let first = ordered[i]
        let start = (verse: first.verse, offset: first.textStart + max(0, lo - first.pageStart))
        while i + 1 < ordered.count, ordered[i + 1].pageStart < hi { i += 1 }
        let last = ordered[i]
        let end = (verse: last.verse, offset: last.textStart + min(last.length, hi - last.pageStart))
        return tidied(start: start, end: end)
    }

    /// The other way: the page range a stretch covers, for the text view's
    /// selection — the toolbar's "the verse", a verse's number tapped,
    /// VoiceOver's "leave something here" and its steps. One range from the
    /// first letter to the last; the numbers between verses fall inside it.
    func selection(of ends: PageEnds) -> NSRange? {
        guard let a = pageRanges(verse: ends.startVerse, from: ends.startChar, to: nil).first,
              let b = pageRanges(verse: ends.endVerse, from: nil, to: ends.endChar).last
        else { return nil }
        return NSRange(location: a.location, length: NSMaxRange(b) - a.location)
    }

    /// Two ends, tidied the way a mark is stored. Blanks at either end are
    /// let go of. An end left with nothing of its own verse — a selection
    /// that begins on a verse's trailing space — moves on to the next
    /// verse's first letter, or back to the last one's. An end at a verse's
    /// edge, give or take its leading or trailing space, is the whole of
    /// that end: nil. Nil when no letter is left between them.
    ///
    /// The trailing space matters: thousands of verses' own text ends in
    /// one, and an end stored one short of the verse's length was a phrase
    /// where the reader had marked the whole verse.
    func tidied(start: (verse: Int, offset: Int), end: (verse: Int, offset: Int)) -> PageEnds? {
        guard var si = orderIndex[start.verse], var ei = orderIndex[end.verse], si <= ei else { return nil }
        var startOffset = max(0, start.offset)
        var endOffset = end.offset
        while true {
            let text = nsText(verseOrder[si])
            while startOffset < text.length, Self.isBlank(text.character(at: startOffset)) { startOffset += 1 }
            if startOffset < text.length || si >= ei { break }
            si += 1
            startOffset = 0
        }
        while true {
            let text = nsText(verseOrder[ei])
            endOffset = max(0, min(endOffset, text.length))
            while endOffset > 0, Self.isBlank(text.character(at: endOffset - 1)) { endOffset -= 1 }
            if endOffset > 0 || ei <= si { break }
            ei -= 1
            endOffset = length(of: verseOrder[ei])
        }
        if si > ei || (si == ei && endOffset <= startOffset) { return nil }
        let startText = nsText(verseOrder[si])
        let endText = nsText(verseOrder[ei])
        return PageEnds(
            startVerse: verseOrder[si],
            startChar: startOffset <= Self.leadingBlank(startText) ? nil : startOffset,
            endVerse: verseOrder[ei],
            endChar: endOffset >= endText.length - Self.trailingBlank(endText) ? nil : endOffset)
    }

    /// One end of a stretch moved — a word or a verse, on or back — the
    /// tap equivalents of dragging a handle (§11). Nil where it cannot go:
    /// past the other end, or off the chapter.
    func stepped(_ ends: PageEnds, start: Bool, byVerse: Bool, forward: Bool) -> PageEnds? {
        var from = (verse: ends.startVerse, offset: ends.startChar ?? 0)
        var to = (verse: ends.endVerse, offset: ends.endChar ?? length(of: ends.endVerse))
        if byVerse {
            guard let place = orderIndex[start ? from.verse : to.verse] else { return nil }
            let next = place + (forward ? 1 : -1)
            guard verseOrder.indices.contains(next) else { return nil }
            let verse = verseOrder[next]
            if start { from = (verse, 0) } else { to = (verse, length(of: verse)) }
        } else if start {
            from.offset = wordStep(verse: from.verse, offset: from.offset, forward: forward)
        } else {
            to.offset = wordStep(verse: to.verse, offset: to.offset, forward: forward)
        }
        return tidied(start: from, end: to)
    }

    /// The stretch widened to whole words: a highlight is made of words,
    /// whatever part of one a handle was let go on.
    func outwardToWords(_ ends: PageEnds) -> PageEnds {
        let startText = nsText(ends.startVerse)
        var from = ends.startChar ?? 0
        while from > 0, from <= startText.length, !Self.isBlank(startText.character(at: from - 1)) { from -= 1 }
        let endText = nsText(ends.endVerse)
        var to = ends.endChar ?? endText.length
        while to < endText.length, !Self.isBlank(endText.character(at: to)) { to += 1 }
        return tidied(start: (ends.startVerse, from), end: (ends.endVerse, to)) ?? ends
    }

    /// The word held, when the stretch is one word: where it starts in its
    /// verse's own text. A long-press selects one word, and that word is
    /// the one the original line says (A60, §7.5). Nil for anything more.
    func heldWord(_ ends: PageEnds) -> Int? {
        guard ends.startVerse == ends.endVerse else { return nil }
        let text = nsText(ends.startVerse)
        let from = ends.startChar ?? Self.leadingBlank(text)
        let to = ends.endChar ?? (text.length - Self.trailingBlank(text))
        guard to > from else { return nil }
        for i in from..<to where Self.isBlank(text.character(at: i)) { return nil }
        return from
    }

    /// The verse whose number a selection lies on, when it holds no verse
    /// letter: a hold on the number takes its verse.
    func verse(numberUnder selection: NSRange) -> Int? {
        numbers.first { NSIntersectionRange($0.value, selection).length > 0 }?.key
    }

    // MARK: Helpers

    private func nsText(_ verse: Int) -> NSString { (verseText[verse] ?? "") as NSString }

    /// The first segment whose end is past `index`.
    private func firstEnding(after index: Int) -> Int {
        var lo = 0, hi = ordered.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if ordered[mid].pageStart + ordered[mid].length <= index { lo = mid + 1 } else { hi = mid }
        }
        return lo
    }

    static func isBlank(_ unit: unichar) -> Bool {
        unit == 0x20 || unit == 0x0A || unit == 0x09 || unit == 0x2009 || unit == 0xA0
    }

    static func leadingBlank(_ s: NSString) -> Int {
        var n = 0
        while n < s.length, isBlank(s.character(at: n)) { n += 1 }
        return n
    }

    static func trailingBlank(_ s: NSString) -> Int {
        var n = 0
        while n < s.length, isBlank(s.character(at: s.length - 1 - n)) { n += 1 }
        return n
    }
}
