import SwiftUI
import UIKit
import RibbonCore

// One chapter of Scripture, set like a page (S02): Literata at the reader's
// size, verse numbers in small caps superscript at ~45% opacity, hanging
// indents for poetry, the running head set into the text block and
// scrolling with it. Built on TextKit 1 so highlight washes can be drawn
// behind the glyphs as one shape per mark (§4.5, ledger A41), and so a
// note can open the line height and unfurl in place (S04) — never a modal,
// never a sheet.

/// What the reading surface needs to know to set a chapter.
struct ReadingTheme: Equatable {
    var fontSize: CGFloat
    var lineHeightMultiple: CGFloat
    var redLetter: Bool
    /// Carried so a system type-size change re-sets the page (the fonts
    /// themselves scale through UIFontMetrics).
    var dynamicTypeSize: DynamicTypeSize = .large
    /// The gutter, left, ~28 pt. Note marks only (§4.2). The gutter holds
    /// its width at every type size (§08).
    var gutterWidth: CGFloat = 28
    var trailingMargin: CGFloat = 26
}

/// A highlight, as the page needs it: which verse, which stretch of its own
/// text (nil ends mean the whole verse), whose ink, and whether it is yours
/// — because your own mark is revealed along the words, and nobody
/// else's is (A41d).
struct VerseMark: Hashable {
    var id: UUID
    var verse: Int
    var from: Int?
    var to: Int?
    var ink: Ink
    var mine: Bool
}

/// A stretch of one verse's text with one colour on it: the unit a wash is
/// drawn in. A verse with two overlapping marks is three spans.
struct SpanKey: Hashable {
    var verse: Int
    var from: Int
    var to: Int
}

/// Where each verse's marks and geometry ended up, for the SwiftUI overlay.
struct ChapterLayout: Equatable {
    /// Verse → the y-midpoint of its first line (marks pin to the first
    /// line — S02 edge cases).
    var verseFirstLineY: [Int: CGFloat] = [:]
    var height: CGFloat = 0
    /// The two ends of the lifted range, in the view's coordinates: where
    /// the handles go (A41g).
    var liftStart: CGRect?
    var liftEnd: CGRect?
}

/// One run of a verse's text, in both coordinate systems at once: where it
/// begins inside the verse, and where it begins on the page.
struct TextSegment: Equatable {
    var verse: Int
    var textStart: Int
    var pageStart: Int
    var length: Int
}

/// The chapter, typeset: where every verse ended up in the string.
struct ChapterPage {
    var verseText: [Int: String] = [:]
    var verseSegments: [Int: [TextSegment]] = [:]

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

    /// The verse and the offset into its text at a page index, if the index
    /// is in a verse.
    func place(atPageIndex index: Int) -> (verse: Int, offset: Int)? {
        for (verse, segments) in verseSegments {
            for segment in segments where index >= segment.pageStart && index < segment.pageStart + segment.length {
                return (verse, segment.textStart + (index - segment.pageStart))
            }
        }
        return nil
    }

    /// Snap an offset to the nearest word edge in the given direction: the
    /// start of a word going back, the end of one going forward.
    func wordEdge(verse: Int, offset: Int, forward: Bool) -> Int {
        let text = (verseText[verse] ?? "") as NSString
        let length = text.length
        var i = max(0, min(length, offset))
        if forward {
            while i < length, isSpace(text.character(at: i)) { i += 1 }
            while i < length, !isSpace(text.character(at: i)) { i += 1 }
        } else {
            while i > 0, isSpace(text.character(at: i - 1)) { i -= 1 }
            while i > 0, !isSpace(text.character(at: i - 1)) { i -= 1 }
        }
        return i
    }

    /// One word further on, or one back, from an offset that is already on
    /// an edge.
    func wordStep(verse: Int, offset: Int, forward: Bool) -> Int {
        let edge = wordEdge(verse: verse, offset: offset, forward: forward)
        return edge == offset ? wordEdge(verse: verse, offset: forward ? offset + 1 : offset - 1, forward: forward) : edge
    }

    private func isSpace(_ unit: unichar) -> Bool {
        unit == 0x20 || unit == 0x0A || unit == 0x09 || unit == 0x2009 || unit == 0xA0
    }
}

/// A wash, resolved for drawing: the page ranges it covers, its colour, and
/// how far it has arrived.
struct WashSpec {
    var key: SpanKey
    var ranges: [NSRange]
    var color: UIColor
    var alpha: CGFloat
    /// When it began arriving, or nil once settled.
    var arrivedAt: CFTimeInterval?
    /// Yours, just made: revealed along the words rather than faded up.
    var stroke: Bool
    /// What the page already showed here — somebody else's mark that the
    /// pen mixes rather than replaces.
    var beneath: (color: UIColor, alpha: CGFloat)?
    /// When it began lifting off the words — a mark taken back — or nil.
    var leftAt: CFTimeInterval? = nil
}

private extension NSAttributedString.Key {
    /// Verse number carried on every glyph of the verse, for hit-testing.
    static let ribbonVerse = NSAttributedString.Key("ribbonVerse")
}

/// A stretch of the page the carve has moved (S04): its glyphs, and how far
/// they were from where they are now laid out when the move began.
struct CarveStretch: Equatable {
    var range: NSRange
    var offset: CGFloat
}

/// An open note's carve, as the page knows it: the first glyph below it — the
/// start of the line after the note's verse ends — and how tall it is.
struct Carve: Equatable {
    var split: Int
    var height: CGFloat

    /// What a change of carve moves, and by how much: every glyph that was
    /// below the old carve and is not below the new one, or the other way
    /// round, or below both at different heights. A line's old place minus
    /// its new one, stretch by stretch.
    static func travel(from old: Carve?, to new: Carve?, glyphCount: Int) -> [CarveStretch] {
        var cuts: Set<Int> = [0, glyphCount]
        if let old { cuts.insert(min(old.split, glyphCount)) }
        if let new { cuts.insert(min(new.split, glyphCount)) }
        let sorted = cuts.sorted()
        var stretches: [CarveStretch] = []
        for (from, to) in zip(sorted, sorted.dropFirst()) where to > from {
            let was = old.map { from >= $0.split ? $0.height : 0 } ?? 0
            let now = new.map { from >= $0.split ? $0.height : 0 } ?? 0
            if was != now {
                stretches.append(CarveStretch(range: NSRange(location: from, length: to - from), offset: was - now))
            }
        }
        return stretches
    }
}

// MARK: - Layout manager with ink washes

/// Draws highlight washes behind the glyphs (A41b/d/f).
///
/// **One mark, filled once.** The line boxes a mark covers are joined into
/// a single path and filled once, so consecutive lines cannot darken where
/// their bleed overlaps, the outer corners round and the interior ones
/// vanish, and the shape that comes out is the shape of the words. What is
/// left of the hand-made quality is horizontal: the right-hand edge of each
/// line wobbles by a fraction of a point on a stable hash, the way the end
/// of a pen stroke does. Nothing vertical moves, ever.
///
/// The wash hangs off the baseline — the height of the letters plus room
/// for their tails — rather than filling the line box, so it sits on the
/// words the way a stroke does and the leading stays open.
final class InkLayoutManager: NSLayoutManager {
    var washes: [WashSpec] = []
    /// The carve under an open note, moving (S04): stretches of the page
    /// drawn away from where they are now laid out — where they were before
    /// the carve opened, closed or grew — and when the move began. Each eases
    /// home over `settle`, so the line height opens over 400 ms while the
    /// page is laid out once.
    var carveMove: (stretches: [CarveStretch], startedAt: CFTimeInterval)?
    /// The body font's point size, scaled — what the band is measured in.
    var bodySize: CGFloat = 19
    var reduceMotion = false

    static let bleedX: CGFloat = 2
    static let bleedY: CGFloat = 1.2
    static let wobble: CGFloat = 0.6
    static let corner: CGFloat = 5
    static let tip: CGFloat = 10
    static let aboveBaseline: CGFloat = 0.88
    static let belowBaseline: CGFloat = 0.28

    private struct Band {
        var rect: CGRect
    }

    override func drawBackground(forGlyphRange glyphsToShow: NSRange, at origin: CGPoint) {
        if let context = UIGraphicsGetCurrentContext(), !washes.isEmpty {
            context.saveGState()
            let now = CACurrentMediaTime()
            for wash in washes {
                let progress = arrival(of: wash, at: now)
                guard progress.alpha > 0 else { continue }
                let bands = self.bands(for: wash, origin: origin)
                guard !bands.isEmpty else { continue }
                let shape = CGMutablePath()
                for band in bands {
                    shape.addRoundedRect(in: band.rect, cornerWidth: Self.corner, cornerHeight: Self.corner)
                }
                let color = wash.color.withAlphaComponent(progress.alpha).cgColor
                if progress.drawn >= 1 {
                    context.addPath(shape)
                    context.setFillColor(color)
                    context.fillPath()
                    continue
                }
                // The pen travelling: revealed along the words in reading
                // order, measured in ink laid down rather than lines, so a
                // verse of four words and one of four lines take the same
                // time and travel at visibly different speeds.
                let ahead = wash.beneath.map { $0.color.withAlphaComponent($0.alpha).cgColor }
                var left = bands.reduce(0) { $0 + $1.rect.width } * progress.drawn
                for band in bands {
                    let width = band.rect.width
                    let reach = max(0, min(width, left))
                    left -= reach
                    // In front of the tip: their ink, exactly as it was.
                    if reach < width, let ahead {
                        context.saveGState()
                        context.clip(to: CGRect(x: band.rect.minX + reach, y: band.rect.minY, width: width - reach, height: band.rect.height))
                        context.addPath(shape)
                        context.setFillColor(ahead)
                        context.fillPath()
                        context.restoreGState()
                    }
                    guard reach > 0 else { continue }
                    context.saveGState()
                    context.clip(to: CGRect(x: band.rect.minX, y: band.rect.minY, width: reach, height: band.rect.height))
                    if left > 0 || reach >= width {
                        context.addPath(shape)
                        context.setFillColor(color)
                        context.fillPath()
                    } else {
                        // The tip itself: the last few points cross from
                        // what is already there into what the two inks
                        // make — or run out into nothing on bare words. A
                        // hard edge travelling across Scripture is a wipe.
                        let tip = min(Self.tip, reach)
                        let solid = (reach - tip) / reach
                        context.addPath(shape)
                        context.clip()
                        let space = CGColorSpaceCreateDeviceRGB()
                        let aheadColor = ahead ?? wash.color.withAlphaComponent(0).cgColor
                        if let gradient = CGGradient(
                            colorsSpace: space, colors: [color, color, aheadColor] as CFArray,
                            locations: [0, solid, 1]
                        ) {
                            context.drawLinearGradient(
                                gradient,
                                start: CGPoint(x: band.rect.minX, y: band.rect.midY),
                                end: CGPoint(x: band.rect.minX + reach, y: band.rect.midY),
                                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                        }
                    }
                    context.restoreGState()
                }
            }
            context.restoreGState()
        }
        let remaining = carveRemaining(at: CACurrentMediaTime())
        forEachStretch(of: glyphsToShow, remaining: remaining) { range, dy in
            super.drawBackground(forGlyphRange: range, at: CGPoint(x: origin.x, y: origin.y + dy))
        }
    }

    override func drawGlyphs(forGlyphRange glyphsToShow: NSRange, at origin: CGPoint) {
        let remaining = carveRemaining(at: CACurrentMediaTime())
        forEachStretch(of: glyphsToShow, remaining: remaining) { range, dy in
            super.drawGlyphs(forGlyphRange: range, at: CGPoint(x: origin.x, y: origin.y + dy))
        }
    }

    /// A new move of the carve, begun from wherever the lines are drawn this
    /// frame: a card that measures itself taller while it is still opening
    /// carries the gap on from there, rather than jumping it.
    func carveMoves(_ fresh: [CarveStretch]) {
        let now = CACurrentMediaTime()
        let remaining = carveRemaining(at: now)
        var cuts: Set<Int> = []
        for stretch in fresh {
            cuts.insert(stretch.range.location)
            cuts.insert(NSMaxRange(stretch.range))
        }
        if remaining > 0, let move = carveMove {
            for stretch in move.stretches {
                cuts.insert(stretch.range.location)
                cuts.insert(NSMaxRange(stretch.range))
            }
        }
        let sorted = cuts.sorted()
        var merged: [CarveStretch] = []
        for (from, to) in zip(sorted, sorted.dropFirst()) where to > from {
            let carried = carveOffset(forGlyph: from, remaining: remaining)
            let added = fresh.first { NSLocationInRange(from, $0.range) }?.offset ?? 0
            if carried + added != 0 {
                merged.append(CarveStretch(range: NSRange(location: from, length: to - from), offset: carried + added))
            }
        }
        carveMove = merged.isEmpty ? nil : (merged, now)
    }

    /// How much of the carve's move is still to go: 1 as it begins, 0 once
    /// the lines are home. Ease-out, the curve `settle` names.
    private func carveRemaining(at now: CFTimeInterval) -> CGFloat {
        guard let move = carveMove else { return 0 }
        let raw = max(0, min(1, (now - move.startedAt) / RibbonMotion.settleDuration))
        let eased = 1 - pow(1 - raw, 2)
        return CGFloat(1 - eased)
    }

    /// Where a glyph is drawn relative to where it is laid out, this frame.
    private func carveOffset(forGlyph glyph: Int, remaining: CGFloat) -> CGFloat {
        guard remaining > 0, let move = carveMove else { return 0 }
        for stretch in move.stretches where NSLocationInRange(glyph, stretch.range) {
            return stretch.offset * remaining
        }
        return 0
    }

    /// A range of glyphs, cut where the moving stretches begin and end, each
    /// piece handed on with how far it is drawn from home this frame.
    private func forEachStretch(of glyphs: NSRange, remaining: CGFloat, _ body: (NSRange, CGFloat) -> Void) {
        guard remaining > 0, let move = carveMove, !move.stretches.isEmpty else {
            body(glyphs, 0)
            return
        }
        var cuts: Set<Int> = [glyphs.location, NSMaxRange(glyphs)]
        for stretch in move.stretches {
            for edge in [stretch.range.location, NSMaxRange(stretch.range)]
            where edge > glyphs.location && edge < NSMaxRange(glyphs) {
                cuts.insert(edge)
            }
        }
        let sorted = cuts.sorted()
        for (from, to) in zip(sorted, sorted.dropFirst()) where to > from {
            body(NSRange(location: from, length: to - from), carveOffset(forGlyph: from, remaining: remaining))
        }
    }

    /// Whether every wash has settled — the display link's stop signal.
    var isAnimating: Bool {
        let now = CACurrentMediaTime()
        if carveMove != nil, carveRemaining(at: now) > 0 { return true }
        return washes.contains { wash in
            if let left = wash.leftAt { return now - left < RibbonMotion.arriveDuration }
            return arrival(of: wash, at: now).drawn < 1 || wash.arrivedAt.map { now - $0 < RibbonMotion.settleDuration } == true
        }
    }

    private func arrival(of wash: WashSpec, at now: CFTimeInterval) -> (alpha: CGFloat, drawn: CGFloat) {
        if let left = wash.leftAt {
            // Taken back: the ink lifts off the words, on the curve it came
            // on. A fade, and a fade stays one under reduce motion (§11).
            let raw = max(0, min(1, (now - left) / RibbonMotion.arriveDuration))
            let eased = 1 - pow(1 - raw, 2)  // ease-out
            return (wash.alpha * CGFloat(1 - eased), 1)
        }
        guard let started = wash.arrivedAt, !reduceMotion else { return (wash.alpha, 1) }
        let duration = wash.stroke ? RibbonMotion.settleDuration : RibbonMotion.arriveDuration
        let raw = max(0, min(1, (now - started) / duration))
        let eased = 1 - pow(1 - raw, 2)  // ease-out
        if wash.stroke {
            return (wash.alpha, CGFloat(eased))
        }
        return (wash.alpha * CGFloat(eased), 1)
    }

    /// Each line's band for a wash, at the height of the letters, clamped
    /// to the line box so a tall capital or a long descender never lets one
    /// line's wash touch the next.
    private func bands(for wash: WashSpec, origin: CGPoint) -> [Band] {
        var bands: [Band] = []
        var index = 0
        let remaining = carveRemaining(at: CACurrentMediaTime())
        for range in wash.ranges {
            let glyphRange = self.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
            guard glyphRange.location != NSNotFound, glyphRange.length > 0,
                  let container = textContainer(forGlyphAt: glyphRange.location, effectiveRange: nil)
            else { continue }
            enumerateEnclosingRects(
                forGlyphRange: glyphRange, withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0),
                in: container
            ) { rect, _ in
                guard rect.width > 0 else { return }
                let glyph = self.glyphIndex(for: CGPoint(x: rect.midX, y: rect.midY), in: container)
                let line = self.lineFragmentRect(forGlyphAt: glyph, effectiveRange: nil)
                let baseline = line.minY + self.location(forGlyphAt: glyph).y
                let above = self.bodySize * Self.aboveBaseline
                let below = self.bodySize * Self.belowBaseline
                // The pen lifting: a stable hash, so it does not shimmer on
                // redraw, and horizontal only.
                let wobble = CGFloat((range.location &* 31 &+ index &* 7) % 3 - 1) * Self.wobble
                let top = max(rect.minY, baseline - above) - Self.bleedY
                let bottom = min(rect.maxY, baseline + below) + Self.bleedY
                // A wash rides with its words while the carve moves them.
                let band = CGRect(
                    x: rect.minX - Self.bleedX + origin.x,
                    y: top + origin.y + self.carveOffset(forGlyph: glyph, remaining: remaining),
                    width: rect.width + Self.bleedX * 2 + wobble,
                    height: max(1, bottom - top))
                bands.append(Band(rect: band))
                index += 1
            }
        }
        return bands
    }
}

// MARK: - The chapter view

/// The way into the page for the screen around it: the typeset page and
/// the geometry of the lift, without the screen having to hold a UIKit
/// view. One per chapter on screen.
@MainActor
final class ChapterPageHandle {
    fileprivate(set) var page = ChapterPage()
    fileprivate weak var textView: UITextView?

    /// The verse and offset under a point in the chapter view's
    /// coordinates.
    func place(at point: CGPoint) -> (verse: Int, offset: Int)? {
        guard let view = textView, let text = view.attributedText, text.length > 0 else { return nil }
        let inContainer = CGPoint(
            x: point.x - view.textContainerInset.left,
            y: point.y - view.textContainerInset.top)
        let index = view.layoutManager.characterIndex(
            for: inContainer, in: view.textContainer,
            fractionOfDistanceBetweenInsertionPoints: nil)
        guard index < text.length else { return nil }
        if let placed = page.place(atPageIndex: index) { return placed }
        // On a verse number, or in the leading: the verse the glyph belongs
        // to, at its start.
        if let verse = text.attribute(.ribbonVerse, at: index, effectiveRange: nil) as? Int {
            return (verse, 0)
        }
        return nil
    }

    func wordEdge(verse: Int, offset: Int, forward: Bool) -> Int {
        page.wordEdge(verse: verse, offset: offset, forward: forward)
    }

    func wordStep(verse: Int, offset: Int, forward: Bool) -> Int {
        page.wordStep(verse: verse, offset: offset, forward: forward)
    }

    func length(of verse: Int) -> Int { page.length(of: verse) }
}

struct ChapterTextView: UIViewRepresentable {
    let chapter: ScriptureChapter
    /// The running head, fully formed: "Mark 4", "Psalm 23".
    let runningHead: String
    let theme: ReadingTheme
    /// Every mark on this chapter's page.
    let marks: [VerseMark]
    /// A mark you made just now, to be revealed along its words.
    let justMarked: UUID?
    /// The range lifted by a long-press (drawn raised, with a soft shadow),
    /// with its handles.
    let lifted: VerseRange?
    /// An open note's carve-out: verse and the height to open beneath it.
    let openNote: (verse: Int, height: CGFloat)?
    let isFirstChapter: Bool
    let showMarginHint: Bool
    let handle: ChapterPageHandle

    var onLayout: (ChapterLayout) -> Void
    var onLongPressVerse: (Int) -> Void
    var onDragToVerse: (Int) -> Void
    var onDragEnded: () -> Void
    var onTapVerse: (Int) -> Void
    /// Your own mark drawn to its end: the screen may forget `justMarked`.
    var onMarkDrawn: () -> Void
    /// Y offset (in this view's coordinates) of the open-note carve, so the
    /// note card can sit in it.
    var onNoteSlot: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let storage = NSTextStorage()
        let layoutManager = InkLayoutManager()
        storage.addLayoutManager(layoutManager)
        let container = NSTextContainer(size: CGSize(width: 0, height: CGFloat.greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        // The container must follow the view's width or nothing wraps —
        // TextKit 1 treats a fixed 0 as unbounded.
        container.widthTracksTextView = true
        layoutManager.addTextContainer(container)

        let view = UITextView(frame: .zero, textContainer: container)
        view.isEditable = false
        view.isSelectable = false
        view.isScrollEnabled = false
        view.backgroundColor = .clear
        view.textContainerInset = UIEdgeInsets(
            top: 0, left: theme.gutterWidth + 8, bottom: 0, right: theme.trailingMargin)
        view.adjustsFontForContentSizeCategory = true

        let longPress = UILongPressGestureRecognizer(
            target: context.coordinator, action: #selector(Coordinator.longPressed(_:)))
        longPress.minimumPressDuration = 0.45
        view.addGestureRecognizer(longPress)

        let tap = UITapGestureRecognizer(
            target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        view.addGestureRecognizer(tap)

        context.coordinator.textView = view
        handle.textView = view
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.parent = self
        handle.textView = view
        // Rebuild the page only when something that sets it changed — the
        // body re-evaluates on every scroll tick, and NSShadow has no
        // value equality, so an isEqual comparison can't be the gate.
        let buildKey = [
            runningHead, String(chapter.n), String(describing: theme),
            lifted.map(String.init(describing:)) ?? "-",
            String(isFirstChapter), String(showMarginHint),
        ].joined(separator: "|")
        let rebuilt = context.coordinator.builtKey != buildKey
        if rebuilt {
            context.coordinator.builtKey = buildKey
            let (text, page) = Self.attributedText(
                chapter: chapter, runningHead: runningHead, theme: theme,
                lifted: lifted, isFirstChapter: isFirstChapter,
                showMarginHint: showMarginHint)
            view.attributedText = text
            handle.page = page
            context.coordinator.page = page
        }
        if let ink = view.layoutManager as? InkLayoutManager {
            ink.bodySize = RibbonType.uiScripture(theme.fontSize).pointSize
            ink.reduceMotion = UIAccessibility.isReduceMotionEnabled
            context.coordinator.updateWashes(on: ink, view: view)
        }
        // The open note carves space beneath its verse's last line.
        var exclusions: [UIBezierPath] = []
        var carve: Carve?
        if let openNote,
           let range = Self.characterRange(ofVerse: openNote.verse, in: view.attributedText) {
            let glyphRange = view.layoutManager.glyphRange(
                forCharacterRange: range, actualCharacterRange: nil)
            if glyphRange.length > 0 {
                let lastGlyph = max(glyphRange.location, glyphRange.location + glyphRange.length - 1)
                let end = view.layoutManager.boundingRect(
                    forGlyphRange: NSRange(location: lastGlyph, length: 1),
                    in: view.textContainer)
                let slotTop = end.maxY + 6
                exclusions.append(UIBezierPath(rect: CGRect(
                    x: 0, y: slotTop,
                    width: view.textContainer.size.width > 0 ? view.textContainer.size.width : 10_000,
                    height: openNote.height + 12)))
                // What the carve moves begins on the line after the verse
                // ends: the next verse can start on the verse's last line,
                // and those words stay where they are.
                var lastLine = NSRange()
                _ = view.layoutManager.lineFragmentRect(forGlyphAt: lastGlyph, effectiveRange: &lastLine)
                carve = Carve(split: NSMaxRange(lastLine), height: openNote.height + 12)
                DispatchQueue.main.async {
                    onNoteSlot(slotTop + view.textContainerInset.top + 6)
                }
            }
        }
        // Reassigning exclusion paths invalidates layout even when nothing
        // changed — only touch them on a real change.
        if view.textContainer.exclusionPaths.map(\.bounds) != exclusions.map(\.bounds) {
            let before = context.coordinator.carve
            view.textContainer.exclusionPaths = exclusions
            context.coordinator.carve = carve
            // S04: the verse's line height opens over 400 ms (deviation 6,
            // I32). The page is laid out once, with the carve where it now
            // is, and the lines it moved are drawn from where they were,
            // easing home: the gap opens, or closes, without TextKit setting
            // the chapter again every frame. A page just set anew has no
            // "where they were" to start from, and under reduce motion the
            // carve is a change of state rather than a movement (§11).
            if let ink = view.layoutManager as? InkLayoutManager {
                if !rebuilt, !UIAccessibility.isReduceMotionEnabled {
                    ink.carveMoves(Carve.travel(from: before, to: carve, glyphCount: ink.numberOfGlyphs))
                    if ink.carveMove != nil { context.coordinator.startAnimating(on: ink) }
                } else {
                    ink.carveMove = nil
                }
            }
        }
        context.coordinator.reportLayoutSoon()
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0 else { return nil }
        let fit = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: fit.height)
    }

    // MARK: Coordinator

    @MainActor
    final class Coordinator: NSObject {
        var parent: ChapterTextView
        weak var textView: UITextView?
        var builtKey: String?
        var page = ChapterPage()
        private var pendingReport = false
        /// The washes as last settled, so an arrival knows what it is
        /// arriving over.
        private var settled: [SpanKey: WashSpec] = [:]
        private var displayLink: CADisplayLink?
        /// The open note's carve as it was last applied: what the next one
        /// moves from.
        var carve: Carve?

        init(_ parent: ChapterTextView) {
            self.parent = parent
        }

        // MARK: Washes

        /// Two inks screened — the third colour an overlap makes (§4.5).
        /// `1 - (1-a)(1-b)`: how much two lights together add up to, which
        /// is what two translucent washes on an unlit page are.
        static func screen(_ a: UIColor, _ b: UIColor) -> UIColor {
            var (ar, ag, ab, aa): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
            var (br, bg, bb, ba): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
            a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
            b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
            return UIColor(
                red: 1 - (1 - ar) * (1 - br), green: 1 - (1 - ag) * (1 - bg),
                blue: 1 - (1 - ab) * (1 - bb), alpha: 1)
        }

        /// Every span on the page with its colour: the marks on each verse
        /// cut the verse at their ends, and each piece takes the screen of
        /// the inks covering it, a shade darker per extra ink and never past
        /// 36% (A41f).
        private func spans() -> [SpanKey: (color: UIColor, alpha: CGFloat, inside: VerseMark?)] {
            var result: [SpanKey: (color: UIColor, alpha: CGFloat, inside: VerseMark?)] = [:]
            let byVerse = Dictionary(grouping: parent.marks, by: \.verse)
            for (verse, marks) in byVerse {
                let length = page.length(of: verse)
                guard length > 0 else { continue }
                var cuts: Set<Int> = [0, length]
                for mark in marks {
                    cuts.insert(max(0, min(length, mark.from ?? 0)))
                    cuts.insert(max(0, min(length, mark.to ?? length)))
                }
                let sorted = cuts.sorted()
                for (from, to) in zip(sorted, sorted.dropFirst()) where to > from {
                    let covering = marks.filter { mark in
                        (mark.from ?? 0) <= from && (mark.to ?? length) >= to
                    }
                    guard !covering.isEmpty else { continue }
                    var color = covering[0].ink.uiColor
                    for other in covering.dropFirst() { color = Self.screen(color, other.ink.uiColor) }
                    let alpha = CGFloat(min(0.36, Palette.highlightWash + 0.05 * Double(covering.count - 1)))
                    let struck = covering.first { $0.id == parent.justMarked }
                    result[SpanKey(verse: verse, from: from, to: to)] = (color: color, alpha: alpha, inside: struck)
                }
            }
            return result
        }

        private func covering(_ span: SpanKey, in washes: [SpanKey: WashSpec]) -> WashSpec? {
            let middle = (span.from + span.to) / 2
            return washes.values.first { $0.key.verse == span.verse && $0.key.from <= middle && $0.key.to > middle }
        }

        func updateWashes(on ink: InkLayoutManager, view: UITextView) {
            let now = CACurrentMediaTime()
            let current = spans()
            var next: [SpanKey: WashSpec] = [:]
            var anyArriving = false
            for (key, span) in current {
                if var existing = settled[key] {
                    existing.color = span.color
                    existing.alpha = span.alpha
                    // The page may have been set again since — the margin
                    // hint leaving moves every offset — so the words are
                    // found afresh.
                    existing.ranges = page.pageRanges(verse: key.verse, from: key.from, to: key.to)
                    if let started = existing.arrivedAt,
                       now - started > (existing.stroke ? RibbonMotion.settleDuration : RibbonMotion.arriveDuration) {
                        existing.arrivedAt = nil
                        existing.beneath = nil
                    }
                    if existing.arrivedAt != nil { anyArriving = true }
                    next[key] = existing
                    continue
                }
                // A span that is new because an overlapping mark cut the
                // verse into smaller pieces is not an arrival: the colour
                // under those words was already on the page, unless what is
                // arriving is your own pen, which is drawn along the words
                // over what was there.
                let was = covering(key, in: settled)
                let stroke = span.inside != nil && span.inside!.mine
                if was != nil && !stroke {
                    next[key] = WashSpec(key: key, ranges: page.pageRanges(verse: key.verse, from: key.from, to: key.to), color: span.color, alpha: span.alpha, arrivedAt: nil, stroke: false, beneath: nil)
                    continue
                }
                anyArriving = true
                next[key] = WashSpec(
                    key: key, ranges: page.pageRanges(verse: key.verse, from: key.from, to: key.to),
                    color: span.color, alpha: span.alpha, arrivedAt: now, stroke: stroke,
                    beneath: was.map { ($0.color, $0.alpha) })
            }
            // Taken back: down to nothing rather than gone between two frames
            // (Android's A38 had this and iOS never did). A span that has
            // merely been re-cut is still covered by what remains, and is
            // that mark's now, drawn at once.
            for (key, old) in settled where current[key] == nil {
                if let left = old.leftAt {
                    if now - left < RibbonMotion.arriveDuration {
                        next[key] = old
                        anyArriving = true
                    }
                    continue
                }
                let middle = (key.from + key.to) / 2
                let stillWashed = current.keys.contains { $0.verse == key.verse && $0.from <= middle && $0.to > middle }
                guard !stillWashed else { continue }
                var leaving = old
                leaving.leftAt = now
                leaving.arrivedAt = nil
                leaving.stroke = false
                leaving.beneath = nil
                leaving.ranges = page.pageRanges(verse: key.verse, from: key.from, to: key.to)
                next[key] = leaving
                anyArriving = true
            }
            settled = next
            let washes = next.values.sorted { ($0.ranges.first?.location ?? 0) < ($1.ranges.first?.location ?? 0) }
            let changed = washes.count != ink.washes.count
                || zip(washes, ink.washes).contains { a, b in
                    a.key != b.key || a.alpha != b.alpha || a.color != b.color || a.arrivedAt != b.arrivedAt || a.leftAt != b.leftAt
                }
            if changed {
                ink.washes = washes
                // The glyphs live in the text container's own drawing pass;
                // invalidating the view's layer would not repaint them.
                ink.invalidateDisplay(forGlyphRange: NSRange(location: 0, length: ink.numberOfGlyphs))
            }
            if anyArriving {
                startAnimating(on: ink)
            }
        }

        fileprivate func startAnimating(on ink: InkLayoutManager) {
            guard displayLink == nil else { return }
            let link = CADisplayLink(target: self, selector: #selector(tick))
            link.add(to: .main, forMode: .common)
            displayLink = link
        }

        @objc private func tick() {
            guard let view = textView, let ink = view.layoutManager as? InkLayoutManager else {
                displayLink?.invalidate()
                displayLink = nil
                return
            }
            ink.invalidateDisplay(forGlyphRange: NSRange(location: 0, length: ink.numberOfGlyphs))
            if !ink.isAnimating {
                displayLink?.invalidate()
                displayLink = nil
                // What was lifting off has gone, and the carve's lines are
                // home.
                ink.carveMove = nil
                settled = settled.filter { $0.value.leftAt == nil }
                for key in settled.keys { settled[key]?.arrivedAt = nil; settled[key]?.beneath = nil }
                ink.washes = settled.values.sorted { ($0.ranges.first?.location ?? 0) < ($1.ranges.first?.location ?? 0) }
                ink.invalidateDisplay(forGlyphRange: NSRange(location: 0, length: ink.numberOfGlyphs))
                if parent.justMarked != nil { parent.onMarkDrawn() }
            }
        }

        // MARK: Layout

        func reportLayoutSoon() {
            guard !pendingReport else { return }
            pendingReport = true
            DispatchQueue.main.async { [self] in
                pendingReport = false
                reportLayout()
            }
        }

        private func reportLayout() {
            guard let view = textView, let text = view.attributedText, text.length > 0 else { return }
            var layout = ChapterLayout()
            var seen = Set<Int>()
            var verseRect: [Int: CGRect] = [:]
            text.enumerateAttribute(.ribbonVerse, in: NSRange(location: 0, length: text.length)) { value, range, _ in
                guard let verse = value as? Int else { return }
                let glyphRange = view.layoutManager.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
                guard glyphRange.length > 0 else { return }
                if !seen.contains(verse) {
                    seen.insert(verse)
                    var lineRange = NSRange()
                    let lineRect = view.layoutManager.lineFragmentRect(
                        forGlyphAt: glyphRange.location, effectiveRange: &lineRange)
                    layout.verseFirstLineY[verse] = lineRect.midY + view.textContainerInset.top
                }
                let rect = view.layoutManager.boundingRect(forGlyphRange: glyphRange, in: view.textContainer)
                    .offsetBy(dx: view.textContainerInset.left, dy: view.textContainerInset.top)
                verseRect[verse] = verseRect[verse].map { $0.union(rect) } ?? rect
            }
            layout.height = view.sizeThatFits(
                CGSize(width: view.bounds.width, height: .greatestFiniteMagnitude)).height
            if let lifted = parent.lifted {
                let (start, end) = liftEnds(of: lifted, in: view)
                layout.liftStart = start
                layout.liftEnd = end
            }
            rebuildAccessibilityElements(on: view, verseRect: verseRect)
            parent.onLayout(layout)
        }

        /// The glyph boxes at the two ends of the lifted range, in the
        /// view's coordinates.
        private func liftEnds(of lifted: VerseRange, in view: UITextView) -> (CGRect?, CGRect?) {
            let startRanges = page.pageRanges(verse: lifted.startVerse, from: lifted.startChar, to: nil)
            let endRanges = page.pageRanges(verse: lifted.endVerse, from: nil, to: lifted.endChar)
            func box(at index: Int) -> CGRect? {
                let glyph = view.layoutManager.glyphRange(forCharacterRange: NSRange(location: index, length: 1), actualCharacterRange: nil)
                guard glyph.length > 0 else { return nil }
                return view.layoutManager.boundingRect(forGlyphRange: glyph, in: view.textContainer)
                    .offsetBy(dx: view.textContainerInset.left, dy: view.textContainerInset.top)
            }
            let start = startRanges.first.flatMap { box(at: $0.location) }
            let end = endRanges.last.flatMap { box(at: max($0.location, $0.location + $0.length - 1)) }
            return (start, end)
        }

        /// Verse-by-verse VoiceOver navigation (§11): one element per
        /// verse, so a swipe moves by verse — and the label obeys Law 2
        /// ("Verse nine." then the words; never a position report). Each
        /// verse carries the two things a finger can do to it.
        private func rebuildAccessibilityElements(on view: UITextView, verseRect: [Int: CGRect]) {
            var elements: [UIAccessibilityElement] = []
            for verse in page.verseText.keys.sorted() {
                guard let rect = verseRect[verse], let body = page.verseText[verse] else { continue }
                let element = UIAccessibilityElement(accessibilityContainer: view)
                element.accessibilityFrameInContainerSpace = rect
                element.accessibilityLabel = Copy.verseSpoken(verse, body.trimmingCharacters(in: .whitespacesAndNewlines))
                element.accessibilityCustomActions = [
                    UIAccessibilityCustomAction(name: Copy.openWhatsHere) { [weak self] _ in
                        self?.parent.onTapVerse(verse)
                        return true
                    },
                    UIAccessibilityCustomAction(name: Copy.leaveSomethingHere) { [weak self] _ in
                        self?.parent.onLongPressVerse(verse)
                        return true
                    },
                ]
                elements.append(element)
            }
            view.isAccessibilityElement = false
            view.accessibilityElements = elements
        }

        func verse(at point: CGPoint) -> Int? {
            guard let view = textView, let text = view.attributedText, text.length > 0 else { return nil }
            let inContainer = CGPoint(
                x: point.x - view.textContainerInset.left,
                y: point.y - view.textContainerInset.top)
            let index = view.layoutManager.characterIndex(
                for: inContainer, in: view.textContainer,
                fractionOfDistanceBetweenInsertionPoints: nil)
            guard index < text.length else { return nil }
            return text.attribute(.ribbonVerse, at: index, effectiveRange: nil) as? Int
        }

        @objc func longPressed(_ gesture: UILongPressGestureRecognizer) {
            guard let view = textView else { return }
            switch gesture.state {
            case .began:
                if let verse = verse(at: gesture.location(in: view)) {
                    Haptics.shared.verseLifts()
                    parent.onLongPressVerse(verse)
                }
            case .changed:
                if let verse = verse(at: gesture.location(in: view)) {
                    parent.onDragToVerse(verse)
                }
            case .ended, .cancelled:
                parent.onDragEnded()
            default:
                break
            }
        }

        @objc func tapped(_ gesture: UITapGestureRecognizer) {
            guard let view = textView else { return }
            if let verse = verse(at: gesture.location(in: view)) {
                parent.onTapVerse(verse)
            }
        }
    }

    // MARK: Setting the page

    static func characterRange(ofVerse verse: Int, in text: NSAttributedString?) -> NSRange? {
        guard let text, text.length > 0 else { return nil }
        var result: NSRange?
        text.enumerateAttribute(.ribbonVerse, in: NSRange(location: 0, length: text.length)) { value, range, _ in
            guard let v = value as? Int, v == verse else { return }
            if let existing = result {
                result = NSUnionRange(existing, range)
            } else {
                result = range
            }
        }
        return result
    }

    static func attributedText(
        chapter: ScriptureChapter, runningHead: String, theme: ReadingTheme,
        lifted: VerseRange?, isFirstChapter: Bool, showMarginHint: Bool
    ) -> (NSAttributedString, ChapterPage) {
        let result = NSMutableAttributedString()
        var page = ChapterPage()
        let ivory = UIColor(Palette.text)
        let bodyFont = RibbonType.uiScripture(theme.fontSize)
        let em = theme.fontSize

        func paragraphStyle(_ style: BlockStyle, isFirstBlock: Bool, afterBreak: Bool) -> NSParagraphStyle {
            let p = NSMutableParagraphStyle()
            p.lineHeightMultiple = theme.lineHeightMultiple
            switch style {
            case .p:
                // A printed page: first-line indent, except the paragraph
                // that opens the chapter.
                p.firstLineHeadIndent = isFirstBlock ? 0 : em * 0.95
            case .m:
                p.paragraphSpacingBefore = afterBreak ? em * 0.75 : 0
            case .q1:
                p.firstLineHeadIndent = em * 0.6
                p.headIndent = em * 1.6
            case .q2:
                p.firstLineHeadIndent = em * 1.5
                p.headIndent = em * 2.5
            case .d:
                p.paragraphSpacing = em * 0.5
            case .b:
                break
            }
            return p
        }

        // The one-time hint, above the first verse, dismissed by scrolling
        // past it and never returning (§6.1).
        if showMarginHint && isFirstChapter {
            let hint = NSMutableAttributedString(
                string: Copy.firstRunHint + "\n\n",
                attributes: [
                    .font: RibbonType.uiSmallCaps(13),
                    .foregroundColor: UIColor(Palette.muted),
                    .kern: 0.9,
                ])
            result.append(hint)
        }

        // The running head — book and chapter in small caps at ~40%
        // opacity, set into the text block. Not a bar.
        let headStyle = NSMutableParagraphStyle()
        headStyle.paragraphSpacing = em * 1.6
        result.append(NSAttributedString(
            string: runningHead + "\n",
            attributes: [
                .font: RibbonType.uiSmallCaps(14),
                .foregroundColor: ivory.withAlphaComponent(0.4),
                .kern: 1.4,
                .paragraphStyle: headStyle,
            ]))

        var isFirstContentBlock = true
        var afterBreak = false
        var runningVerse: Int?
        for block in chapter.blocks {
            if block.s == .b {
                afterBreak = true
                continue
            }
            let style = paragraphStyle(block.s, isFirstBlock: isFirstContentBlock, afterBreak: afterBreak)
            afterBreak = false

            let blockText = NSMutableAttributedString()
            let blockStart = result.length
            for span in block.x {
                if let verse = span.v { runningVerse = verse }
                if let verse = span.v, verse != 1 {
                    // The verse number: small caps superscript, ~45%.
                    blockText.append(NSAttributedString(
                        string: "\(verse)\u{2009}",
                        attributes: [
                            .font: RibbonType.uiSmallCaps(theme.fontSize * 0.62),
                            .foregroundColor: ivory.withAlphaComponent(0.45),
                            .baselineOffset: theme.fontSize * 0.3,
                            .ribbonVerse: verse,
                            .paragraphStyle: style,
                        ]))
                }
                var attributes: [NSAttributedString.Key: Any] = [
                    .font: block.s == .d
                        ? RibbonType.uiScripture(theme.fontSize * 0.82)
                        : bodyFont,
                    .foregroundColor: block.s == .d
                        ? UIColor(Palette.muted)
                        : (span.isRedLetter && theme.redLetter
                            ? UIColor(Ink.crimson.color)
                            : ivory),
                    .paragraphStyle: style,
                ]
                if let verse = runningVerse, block.s != .d {
                    attributes[.ribbonVerse] = verse
                    // Where this run sits in the verse's own text and on
                    // the page — the two coordinate systems a phrase mark
                    // moves between.
                    let textStart = (page.verseText[verse] as NSString?)?.length ?? 0
                    let length = (span.t as NSString).length
                    page.verseText[verse, default: ""] += span.t
                    page.verseSegments[verse, default: []].append(TextSegment(
                        verse: verse, textStart: textStart, pageStart: blockStart + blockText.length, length: length))
                }
                blockText.append(NSAttributedString(string: span.t, attributes: attributes))
            }
            if blockText.length > 0 {
                blockText.append(NSAttributedString(string: "\n", attributes: [.paragraphStyle: style, .font: bodyFont]))
                result.append(blockText)
                isFirstContentBlock = false
            }
        }

        // The lift: the words in the range raised, with a soft shadow —
        // whole verses, or the phrase between the handles.
        if let lifted {
            for verse in lifted.verses {
                let from = verse == lifted.startVerse ? lifted.startChar : nil
                let to = verse == lifted.endVerse ? lifted.endChar : nil
                for range in page.pageRanges(verse: verse, from: from, to: to) where NSMaxRange(range) <= result.length {
                    let shadow = NSShadow()
                    shadow.shadowColor = UIColor.black.withAlphaComponent(0.7)
                    shadow.shadowBlurRadius = 8
                    shadow.shadowOffset = CGSize(width: 0, height: 3)
                    result.addAttributes([.shadow: shadow, .baselineOffset: 2], range: range)
                }
            }
        }
        return (result, page)
    }
}
