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
    /// A point on Literata's weight axis (A68): the reader's Lighter, Book
    /// or Heavier, with the system's Bold Text folded in. 400 is Book, the
    /// page as it always was.
    var weight: Int = 400
    /// In prose, each numbered verse starts its own line (A68).
    var versePerLine: Bool = false
    /// The verse numbers' ink: S02's quiet 45%, or clearer (A68).
    var verseNumberAlpha: CGFloat = 0.45
    /// Carried so a system type-size change re-sets the page (the fonts
    /// themselves scale through UIFontMetrics).
    var dynamicTypeSize: DynamicTypeSize = .large
    /// The gutter, left, ~28 pt. Note marks only (§4.2). The gutter holds
    /// its width at every type size (§08).
    var gutterWidth: CGFloat = 28
    var trailingMargin: CGFloat = 26
    /// The reader's typeface (A69). `fontSize` stays Literata's size: the
    /// face is set at the size that looks the same (`PageType.pointSize`),
    /// and the verse numbers, the indents and the running head keep to it.
    var face: PageFace = PageFaces.literata
    /// Room between the letters, in thousandths of an em (A69): on the
    /// words only, never on a verse number.
    var letterSpacing: Int = 0
    /// The margin the page gives either side at this width and size (A69):
    /// outside the gutter on the left, so notes stay beside their words,
    /// and beside the trailing edge on the right, which the presence form
    /// keeps as it always has.
    var margin: CGFloat = 0
}

extension ReadingTheme {
    /// The reader's page, from their settings (S20, A68). The book and the
    /// Text screen's preview of it are both set from here, so the preview
    /// cannot show a page the reader will not get. Bold Text is passed in
    /// by whoever reads it from the environment, so that turning it on sets
    /// the page again.
    ///
    /// `columnWidth` is the width the page is set in, which the margin
    /// gives way to (A69); with none, the page has no margin.
    init(_ settings: AppSettings, boldText: Bool, dynamicTypeSize: DynamicTypeSize, columnWidth: CGFloat = 0) {
        self.init(
            fontSize: settings.scriptureSize,
            lineHeightMultiple: settings.lineHeightMultiple,
            redLetter: settings.redLetter,
            weight: settings.weight(boldText: boldText),
            versePerLine: settings.versePerLine,
            verseNumberAlpha: settings.verseNumberAlpha,
            dynamicTypeSize: dynamicTypeSize,
            face: settings.face,
            letterSpacing: settings.letterSpacingThousandths)
        margin = Self.margin(
            requested: settings.marginRequested, columnWidth: columnWidth, fontSize: fontSize,
            gutterWidth: gutterWidth, trailingMargin: trailingMargin)
    }

    /// The margin a column this wide can give at this size (A69): what the
    /// reader asked for, less whatever would leave the words narrower than
    /// thirteen ems of Literata at the size Dynamic Type makes it.
    static func margin(
        requested: Double, columnWidth: CGFloat, fontSize: CGFloat,
        gutterWidth: CGFloat = 28, trailingMargin: CGFloat = 26
    ) -> CGFloat {
        guard requested > 0, columnWidth > 0 else { return 0 }
        let size = Double(RibbonType.uiScripture(fontSize).pointSize)
        let textWidth = Double(columnWidth - gutterWidth - 8 - trailingMargin)
        return CGFloat(PageType.margin(requested: requested, textWidth: textWidth, size: size))
    }

    /// Where the words begin and end inside the text view.
    var textInsets: UIEdgeInsets {
        UIEdgeInsets(top: 0, left: margin + gutterWidth + 8, bottom: 0, right: trailingMargin + margin)
    }
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
}

// `ChapterPage` — the chapter typeset, in plain characters, and the
// selection mapping over it — is in ChapterPage.swift (A62).

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
    /// The verse a note is being left on, while the composer has the focus
    /// and the selection has let go (S05, A62): its glyphs, drawn raised
    /// with a soft shadow. Drawn, never typeset — it costs a redraw, not a
    /// layout. Under reduce motion it is the same still state; it never
    /// travels.
    var held: [NSRange] = []

    /// How far a held verse is raised.
    static let lift: CGFloat = 2

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
            forEachHeld(of: range) { piece, raised in
                guard raised, let context = UIGraphicsGetCurrentContext() else {
                    super.drawGlyphs(forGlyphRange: piece, at: CGPoint(x: origin.x, y: origin.y + dy))
                    return
                }
                // The lift the long-press used to set into the text, drawn
                // here instead: the same shadow, the same 2 pt.
                context.saveGState()
                context.setShadow(
                    offset: CGSize(width: 0, height: 3), blur: 8,
                    color: UIColor.black.withAlphaComponent(0.7).cgColor)
                super.drawGlyphs(forGlyphRange: piece, at: CGPoint(x: origin.x, y: origin.y + dy - Self.lift))
                context.restoreGState()
            }
        }
    }

    /// A range of glyphs, cut where the held verse begins and ends.
    private func forEachHeld(of glyphs: NSRange, _ body: (NSRange, Bool) -> Void) {
        guard !held.isEmpty else {
            body(glyphs, false)
            return
        }
        var cuts: Set<Int> = [glyphs.location, NSMaxRange(glyphs)]
        for range in held {
            for edge in [range.location, NSMaxRange(range)]
            where edge > glyphs.location && edge < NSMaxRange(glyphs) {
                cuts.insert(edge)
            }
        }
        let sorted = cuts.sorted()
        for (from, to) in zip(sorted, sorted.dropFirst()) where to > from {
            body(NSRange(location: from, length: to - from), held.contains { NSLocationInRange(from, $0) })
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
/// its selection, without the screen having to hold a UIKit view. One per
/// chapter on screen.
@MainActor
final class ChapterPageHandle {
    fileprivate(set) var page = ChapterPage()
    fileprivate weak var textView: UITextView?
    fileprivate weak var coordinator: ChapterTextView.Coordinator?

    /// Selects a stretch of the page, the way a finger would (A62): the
    /// toolbar's "the verse", a verse's number tapped, VoiceOver's "leave
    /// something here" and "the original words". The system draws it and
    /// gives it handles; what it now holds is reported as any selection is.
    func select(_ ends: PageEnds) {
        coordinator?.select(ends)
    }

    /// Lets go of whatever this page has selected.
    func clearSelection() {
        coordinator?.clearSelection()
    }

    /// The stretch widened to whole words, for a highlight (A62). Read from
    /// the page as the view last set it: a handle made again before the
    /// screen kept it has never been handed a page of its own.
    func outwardToWords(_ ends: PageEnds) -> PageEnds {
        (coordinator?.page ?? page).outwardToWords(ends)
    }

    /// The VoiceOver element that reads a verse, for a move that wants to
    /// hand the listener the verse it came to.
    func accessibilityElement(forVerse verse: Int) -> Any? {
        textView?.accessibilityElements?.first { ($0 as? VerseElement)?.verse == verse }
    }
}

/// One verse, as VoiceOver reads it. Kept from one layout to the next —
/// the element the listener is on has to be the same object after the page
/// moves, or VoiceOver lets go of it.
final class VerseElement: UIAccessibilityElement {
    var verse = 0
}

/// One end of the selection, as VoiceOver reaches it (§11, A41g, A62). The
/// system's handles have no tap equivalent; these do: swipe up or down to
/// move the end a word, and the actions for a verse either way.
final class SelectionEndElement: UIAccessibilityElement {
    var step: ((_ forward: Bool) -> Void)?

    override func accessibilityIncrement() { step?(true) }
    override func accessibilityDecrement() { step?(false) }
}

/// The chapter's text view. Selectable, never editable (A62): the system
/// gives the hold its word, the handles, the loupe, a pointer's drag and
/// shift-arrow on a keyboard. What it does not give is its edit menu — a
/// glass callout over a verse, which the brief never allows, with Copy and
/// Look Up on licensed words — nor its double- and triple-tap, which S02
/// keeps reserved.
final class PageTextView: UITextView {
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        // Selecting is the system's own interaction and stays; every verb
        // the menu would offer is Ribbon's toolbar's instead.
        action == #selector(UIResponderStandardEditActions.select(_:))
    }

    override func addGestureRecognizer(_ gestureRecognizer: UIGestureRecognizer) {
        super.addGestureRecognizer(gestureRecognizer)
        quietMultipleTaps()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        quietMultipleTaps()
    }

    /// Double-tap: nothing, reserved (S02). The text view's own double- and
    /// triple-tap would select a word and a paragraph — and race the tap
    /// that opens a verse's notes. Turned off wherever UIKit adds them.
    func quietMultipleTaps() {
        for case let tap as UITapGestureRecognizer in gestureRecognizers ?? []
        where tap.numberOfTapsRequired >= 2 && tap.isEnabled {
            tap.isEnabled = false
        }
    }
}

struct ChapterTextView: UIViewRepresentable {
    let chapter: ScriptureChapter
    /// The version `chapter` is in. A version changed while the book is
    /// open sets the page again (A60): the words are new, and the marks
    /// are found afresh in them.
    let translation: TranslationID
    /// The running head, fully formed: "Mark 4", "Psalm 23".
    let runningHead: String
    let theme: ReadingTheme
    /// Every mark on this chapter's page.
    let marks: [VerseMark]
    /// A mark you made just now, to be revealed along its words.
    let justMarked: UUID?
    /// The verse a note is being left on while the composer has the focus
    /// (S05): drawn raised, with a soft shadow, because the selection that
    /// chose it has let go of the page (A62).
    let heldVerse: Int?
    /// An open note's carve-out: verse and the height to open beneath it.
    let openNote: (verse: Int, height: CGFloat)?
    let isFirstChapter: Bool
    let showMarginHint: Bool
    let handle: ChapterPageHandle

    var onLayout: (ChapterLayout) -> Void
    /// The selection on this page, as the verses' own text (A62) — and,
    /// when it is one word, where that word starts: the word held, which
    /// the original line says (A60, §7.5). Nil when the page lets go.
    var onSelection: (PageEnds?, Int?) -> Void
    /// A verse's number tapped: the whole verse, selected.
    var onVerseNumber: (Int) -> Void
    /// A tap on the page while it had a selection: that tap only lets go
    /// (S02 — dismiss first), whatever the system did with it first.
    var onLetGo: () -> Void
    var onTapVerse: (Int) -> Void
    /// VoiceOver's "leave something here": the verse, selected whole.
    var onLeaveSomethingHere: (Int) -> Void
    /// VoiceOver's way to the original words of a verse (A60): select it and
    /// open the panel in one action.
    var onOriginalWords: (Int) -> Void
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

        let view = PageTextView(frame: .zero, textContainer: container)
        view.isEditable = false
        // Native selection (A62): the system's hold, handles and loupe, in
        // the accent the old knob had. Its menu, its drag and drop, its
        // writing tools and its multiple taps are turned away.
        view.isSelectable = true
        view.tintColor = UIColor(Palette.chartreuse)
        view.textDragInteraction?.isEnabled = false
        view.writingToolsBehavior = .none
        view.isFindInteractionEnabled = false
        view.dataDetectorTypes = []
        view.delegate = context.coordinator
        view.isScrollEnabled = false
        view.backgroundColor = .clear
        view.textContainerInset = theme.textInsets
        view.adjustsFontForContentSizeCategory = true

        // Ours, beside the system's own: it reads the touch before the
        // system's tap can clear a selection, so a tap on a selected page
        // only lets go.
        let tap = UITapGestureRecognizer(
            target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        tap.delegate = context.coordinator
        view.addGestureRecognizer(tap)
        view.quietMultipleTaps()

        context.coordinator.textView = view
        handle.textView = view
        handle.coordinator = context.coordinator
        return view
    }

    /// A page leaving the lazy stack takes its selection with it.
    static func dismantleUIView(_ view: UITextView, coordinator: Coordinator) {
        coordinator.pageLeft()
    }

    func updateUIView(_ view: UITextView, context: Context) {
        // The margin follows the reader's setting and the page's width (A69).
        if view.textContainerInset != theme.textInsets {
            view.textContainerInset = theme.textInsets
            view.invalidateIntrinsicContentSize()
        }
        context.coordinator.parent = self
        handle.textView = view
        handle.coordinator = context.coordinator
        // Rebuild the page only when something that sets it changed — the
        // body re-evaluates on every scroll tick. A selection is not one of
        // those things (A62): the system draws it over the page as set, so
        // a handle crossing a word costs no typesetting at all.
        let buildKey = [
            runningHead, String(chapter.n), translation.rawValue, String(describing: theme),
            String(isFirstChapter), String(showMarginHint),
        ].joined(separator: "|")
        let rebuilt = context.coordinator.builtKey != buildKey
        if rebuilt {
            context.coordinator.builtKey = buildKey
            let (text, page) = Self.attributedText(
                chapter: chapter, runningHead: runningHead, theme: theme,
                isFirstChapter: isFirstChapter, showMarginHint: showMarginHint)
            context.coordinator.setPage(text, page: page, on: view)
            handle.page = page
        }
        if let ink = view.layoutManager as? InkLayoutManager {
            ink.bodySize = RibbonType.uiScripture(theme.fontSize).pointSize
            ink.reduceMotion = UIAccessibility.isReduceMotionEnabled
            context.coordinator.updateWashes(on: ink, view: view)
            context.coordinator.updateHeld(on: ink, verse: heldVerse, rebuilt: rebuilt)
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
    final class Coordinator: NSObject, UITextViewDelegate, UIGestureRecognizerDelegate {
        var parent: ChapterTextView
        weak var textView: UITextView?
        var builtKey: String?
        private(set) var page = ChapterPage()
        private var pendingReport = false
        /// The washes as last settled, so an arrival knows what it is
        /// arriving over.
        private var settled: [SpanKey: WashSpec] = [:]
        private var displayLink: CADisplayLink?
        /// The open note's carve as it was last applied: what the next one
        /// moves from.
        var carve: Carve?
        /// The verses VoiceOver reads, one element each, and the elements
        /// last handed to the view, in order.
        private var verseElements: [Int: VerseElement] = [:]
        private var elementOrder: [ObjectIdentifier] = []
        /// Where each verse was last laid out, in the view's coordinates.
        private var verseRects: [Int: CGRect] = [:]
        /// The selection's two ends as VoiceOver reaches them, by which end
        /// (true is the start). Kept, like the verses', so focus stays put.
        private var endElements: [Bool: SelectionEndElement] = [:]
        /// What this page last said it had selected, so each change is said
        /// once (A62).
        private var selected: PageEnds?
        /// Whether the page had a selection when the finger came down: the
        /// system's own tap may clear it before ours is told.
        private var hadSelectionAtTouch = false
        /// The page's own changes to the selection, being made: not the
        /// reader's, and said once they are done.
        private var quiet = false
        /// The verse drawn held for a composer, as last drawn.
        private var heldVerse: Int?

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

        // MARK: Setting

        /// The page set anew: new words, so a selection on the old ones is
        /// let go of rather than carried onto letters it never held.
        func setPage(_ text: NSAttributedString, page: ChapterPage, on view: UITextView) {
            quiet = true
            view.attributedText = text
            // Not carried onto the new words: the system keeps a range,
            // clamped, and would draw it over letters nobody chose.
            if view.selectedRange.length > 0 {
                view.selectedRange = NSRange(location: 0, length: 0)
            }
            quiet = false
            self.page = page
            letGoQuietly()
        }

        /// The page has left the lazy stack, and its selection with it.
        func pageLeft() {
            letGoQuietly()
        }

        /// Tells the screen this page holds nothing now — on the next turn,
        /// because this is asked in the middle of a view update.
        private func letGoQuietly() {
            guard selected != nil else { return }
            selected = nil
            let parent = self.parent
            DispatchQueue.main.async { parent.onSelection(nil, nil) }
        }

        /// The held verse for a composer (S05, A62): drawn, not typeset.
        func updateHeld(on ink: InkLayoutManager, verse: Int?, rebuilt: Bool) {
            guard rebuilt || verse != heldVerse else { return }
            heldVerse = verse
            let held = verse.map { verse in
                page.pageRanges(verse: verse, from: nil, to: nil).map {
                    ink.glyphRange(forCharacterRange: $0, actualCharacterRange: nil)
                }
            } ?? []
            guard held != ink.held else { return }
            ink.held = held
            ink.invalidateDisplay(forGlyphRange: NSRange(location: 0, length: ink.numberOfGlyphs))
        }

        // MARK: Selection (A62)

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard !quiet else { return }
            selectionMoved(in: textView)
        }

        /// No edit menu. An empty one, because nil asks for the system's:
        /// a glass callout over a verse, which is never drawn there.
        func textView(
            _ textView: UITextView, editMenuForTextIn range: NSRange, suggestedActions: [UIMenuElement]
        ) -> UIMenu? {
            UIMenu(children: [])
        }

        /// The system's selection, read as the verses' own text — on every
        /// change, a binary search and no typesetting.
        private func selectionMoved(in view: UITextView) {
            let range = view.selectedRange
            if let ends = page.ends(of: range) {
                report(ends)
                return
            }
            guard range.length > 0 else {
                report(nil)
                return
            }
            // Nothing of a verse's own words is under it: the running head,
            // the hint, a psalm's title — which hold nothing, so the
            // selection goes — or a verse's number, which holds its verse.
            let verse = page.verse(numberUnder: range)
            // Or only the space between two words, a handle passing over
            // it: what was said stands until the handle comes to a letter.
            // Let go of here, the toolbar would leave and come back, and
            // the selection would be taken out from under the finger.
            if verse == nil, selected != nil, page.ordered.contains(where: {
                NSIntersectionRange(NSRange(location: $0.pageStart, length: $0.length), range).length > 0
            }) {
                return
            }
            report(nil)
            DispatchQueue.main.async { [weak self, weak view] in
                guard let self, let view, view.selectedRange == range else { return }
                if let verse {
                    self.select(.whole(verse))
                } else {
                    self.setSelectedRange(NSRange(location: range.location, length: 0), on: view)
                }
            }
        }

        private func report(_ ends: PageEnds?) {
            guard ends != selected else { return }
            let lifts = selected == nil && ends != nil
            selected = ends
            // One transient the moment something is lifted, and no ticks as
            // the handles move (build book §9.3).
            if lifts { Haptics.shared.verseLifts() }
            parent.onSelection(ends, ends.flatMap { page.heldWord($0) })
            if let view = textView { updateAccessibilityElements(on: view) }
        }

        /// Selects a stretch, the way a finger would.
        func select(_ ends: PageEnds) {
            guard let view = textView, let range = page.selection(of: ends) else { return }
            if !view.isFirstResponder { _ = view.becomeFirstResponder() }
            setSelectedRange(range, on: view)
        }

        func clearSelection() {
            guard let view = textView else { return }
            if view.selectedRange.length > 0 {
                setSelectedRange(NSRange(location: view.selectedRange.location, length: 0), on: view)
            } else {
                report(nil)
            }
        }

        private func setSelectedRange(_ range: NSRange, on view: UITextView) {
            quiet = true
            view.selectedRange = range
            quiet = false
            selectionMoved(in: view)
        }

        // MARK: Taps

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            hadSelectionAtTouch = selected != nil || (textView?.selectedRange.length ?? 0) > 0
            return true
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc func tapped(_ gesture: UITapGestureRecognizer) {
            guard let view = textView else { return }
            // Tapping the text: dismiss first (S02). A tap on a selected
            // page only lets go, and never opens what is under it.
            if hadSelectionAtTouch {
                hadSelectionAtTouch = false
                parent.onLetGo()
                return
            }
            let location = gesture.location(in: view)
            if let verse = verseNumber(near: location, in: view) {
                parent.onVerseNumber(verse)
                return
            }
            if let verse = verse(at: location) {
                parent.onTapVerse(verse)
            }
        }

        /// The verse whose number is under a tap, or within 16 pt of it
        /// along its line: the figures are a small superscript, and a finger
        /// is not. Up and down it reaches only a little past its own line —
        /// the figures' box is already the line's height, and 16 pt more
        /// would take the words of the lines above and below, whose notes a
        /// tap there opens.
        private func verseNumber(near point: CGPoint, in view: UITextView) -> Int? {
            let reach: CGFloat = 16
            let reachAcrossLines: CGFloat = 6
            var nearest: (verse: Int, distance: CGFloat)?
            for (verse, range) in page.numbers {
                // The figures, without the thin space after them.
                let figures = NSRange(location: range.location, length: max(1, range.length - 1))
                let glyphs = view.layoutManager.glyphRange(forCharacterRange: figures, actualCharacterRange: nil)
                guard glyphs.length > 0 else { continue }
                let rect = view.layoutManager.boundingRect(forGlyphRange: glyphs, in: view.textContainer)
                    .offsetBy(dx: view.textContainerInset.left, dy: view.textContainerInset.top)
                guard rect.insetBy(dx: -reach, dy: -reachAcrossLines).contains(point) else { continue }
                let distance = hypot(point.x - rect.midX, point.y - rect.midY)
                if nearest.map({ distance < $0.distance }) ?? true { nearest = (verse, distance) }
            }
            return nearest?.verse
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
            if let verse = text.attribute(.ribbonVerse, at: index, effectiveRange: nil) as? Int {
                return verse
            }
            // A tap in the blank beside a line's end lands on what ends the
            // line — a block's newline, or the separator a verse to a line
            // puts there (A68) — which is no verse's own. It belongs to the
            // verse whose words end there, as it does on Android; a title's
            // or the running head's end stays nobody's.
            let unit = (text.string as NSString).character(at: index)
            guard index > 0, unit == 0x0A || unit == 0x2028 else { return nil }
            return text.attribute(.ribbonVerse, at: index - 1, effectiveRange: nil) as? Int
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
            verseRects = verseRect
            updateAccessibilityElements(on: view)
            parent.onLayout(layout)
        }

        // MARK: VoiceOver

        /// Verse-by-verse VoiceOver navigation (§11): one element per
        /// verse, so a swipe moves by verse — and the label obeys Law 2
        /// ("Verse nine." then the words; never a position report). Each
        /// verse carries the two things a finger can do to it, and the way
        /// to its original words (A60). While something is selected, its two
        /// ends follow the verses they are in (A62): the system's handles
        /// have no tap equivalent, and these do.
        ///
        /// The elements are made once and kept. The page is laid out again
        /// whenever anything above it redraws — a follow's step among them
        /// — and a fresh set every time took VoiceOver's focus away from the
        /// listener at every step. Now a frame or a label is touched only
        /// when it changed, and the view is handed a new list only when the
        /// elements on the page did.
        private func updateAccessibilityElements(on view: UITextView) {
            var elements: [UIAccessibilityElement] = []
            var order: [Int] = []
            let ends = selected
            for verse in page.verseText.keys.sorted() {
                guard let rect = verseRects[verse], let body = page.verseText[verse] else { continue }
                order.append(verse)
                let label = Copy.verseSpoken(verse, body.trimmingCharacters(in: .whitespacesAndNewlines))
                let element = verseElements[verse] ?? makeElement(verse: verse, in: view)
                if element.accessibilityFrameInContainerSpace != rect {
                    element.accessibilityFrameInContainerSpace = rect
                }
                if element.accessibilityLabel != label {
                    element.accessibilityLabel = label
                }
                elements.append(element)
                if let ends, verse == ends.startVerse, let start = placeEnd(start: true, of: ends, in: view) {
                    elements.append(start)
                }
                if let ends, verse == ends.endVerse, let end = placeEnd(start: false, of: ends, in: view) {
                    elements.append(end)
                }
            }
            if verseElements.count != order.count {
                verseElements = verseElements.filter { order.contains($0.key) }
            }
            let identities = elements.map { ObjectIdentifier($0) }
            guard identities != elementOrder || view.isAccessibilityElement else { return }
            elementOrder = identities
            view.isAccessibilityElement = false
            view.accessibilityElements = elements
        }

        private func makeElement(verse: Int, in view: UITextView) -> VerseElement {
            let element = VerseElement(accessibilityContainer: view)
            element.verse = verse
            element.accessibilityCustomActions = [
                UIAccessibilityCustomAction(name: Copy.openWhatsHere) { [weak self] _ in
                    self?.parent.onTapVerse(verse)
                    return true
                },
                UIAccessibilityCustomAction(name: Copy.leaveSomethingHere) { [weak self] _ in
                    self?.parent.onLeaveSomethingHere(verse)
                    return true
                },
                UIAccessibilityCustomAction(name: Copy.originalAction) { [weak self] _ in
                    self?.parent.onOriginalWords(verse)
                    return true
                },
            ]
            verseElements[verse] = element
            return element
        }

        /// One end of the selection, placed on its letter and saying the
        /// word it is on.
        private func placeEnd(start: Bool, of ends: PageEnds, in view: UITextView) -> SelectionEndElement? {
            let ranges = start
                ? page.pageRanges(verse: ends.startVerse, from: ends.startChar, to: nil)
                : page.pageRanges(verse: ends.endVerse, from: nil, to: ends.endChar)
            guard let range = start ? ranges.first : ranges.last else { return nil }
            let index = start ? range.location : max(range.location, NSMaxRange(range) - 1)
            let glyph = view.layoutManager.glyphRange(
                forCharacterRange: NSRange(location: index, length: 1), actualCharacterRange: nil)
            guard glyph.length > 0 else { return nil }
            let box = view.layoutManager.boundingRect(forGlyphRange: glyph, in: view.textContainer)
                .offsetBy(dx: view.textContainerInset.left, dy: view.textContainerInset.top)
            let element = endElements[start] ?? makeEnd(start: start, in: view)
            // A finger's width, on the end's letter (§11).
            let frame = CGRect(x: (start ? box.minX : box.maxX) - 22, y: box.midY - 22, width: 44, height: 44)
            if element.accessibilityFrameInContainerSpace != frame {
                element.accessibilityFrameInContainerSpace = frame
            }
            let word = endWord(start: start, of: ends)
            if element.accessibilityValue != word {
                element.accessibilityValue = word
            }
            return element
        }

        /// The word an end is on: the first of the selection, or the last.
        private func endWord(start: Bool, of ends: PageEnds) -> String {
            let verse = start ? ends.startVerse : ends.endVerse
            let text = (page.verseText[verse] ?? "") as NSString
            var from: Int, to: Int
            if start {
                from = ends.startChar ?? ChapterPage.leadingBlank(text)
                to = page.wordEdge(verse: verse, offset: from, forward: true)
            } else {
                to = ends.endChar ?? (text.length - ChapterPage.trailingBlank(text))
                from = page.wordEdge(verse: verse, offset: to, forward: false)
            }
            from = max(0, min(from, text.length))
            to = max(from, min(to, text.length))
            return text.substring(with: NSRange(location: from, length: to - from))
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        private func makeEnd(start: Bool, in view: UITextView) -> SelectionEndElement {
            let element = SelectionEndElement(accessibilityContainer: view)
            element.accessibilityLabel = start ? Copy.whereTheMarkStarts : Copy.whereTheMarkEnds
            element.accessibilityTraits = .adjustable
            element.step = { [weak self] forward in
                _ = self?.stepEnd(start: start, byVerse: false, forward: forward)
            }
            element.accessibilityCustomActions = [
                UIAccessibilityCustomAction(name: Copy.aVerseFurtherOn) { [weak self] _ in
                    self?.stepEnd(start: start, byVerse: true, forward: true) ?? false
                },
                UIAccessibilityCustomAction(name: Copy.aVerseBack) { [weak self] _ in
                    self?.stepEnd(start: start, byVerse: true, forward: false) ?? false
                },
                UIAccessibilityCustomAction(name: Copy.aWordFurtherOn) { [weak self] _ in
                    self?.stepEnd(start: start, byVerse: false, forward: true) ?? false
                },
                UIAccessibilityCustomAction(name: Copy.aWordBack) { [weak self] _ in
                    self?.stepEnd(start: start, byVerse: false, forward: false) ?? false
                },
            ]
            endElements[start] = element
            return element
        }

        /// One end moved a word or a verse — the drag a finger would make,
        /// made by an action (§11).
        private func stepEnd(start: Bool, byVerse: Bool, forward: Bool) -> Bool {
            guard let ends = selected,
                  let moved = page.stepped(ends, start: start, byVerse: byVerse, forward: forward)
            else { return false }
            select(moved)
            // A verse away, the end is somewhere else in the list: the
            // listener is kept on it.
            if byVerse, let element = endElements[start] {
                UIAccessibility.post(notification: .layoutChanged, argument: element)
            }
            return true
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
        isFirstChapter: Bool, showMarginHint: Bool
    ) -> (NSAttributedString, ChapterPage) {
        let result = NSMutableAttributedString()
        var page = ChapterPage()
        let ivory = UIColor(Palette.text)
        // The reader's weight is the page's: its words, a psalm's title and
        // the newline that closes a block (A68). The numbers and the running
        // head are Alegreya Sans's, and keep their own.
        let bodyFont = RibbonType.uiScripture(theme.fontSize, weight: theme.weight, face: theme.face)
        let titleFont = RibbonType.uiScripture(theme.fontSize * 0.82, weight: theme.weight, face: theme.face)
        // Letter spacing (A69): a kern on the words alone, in each run's own
        // points, which moves no character a mark or a selection counts in.
        let spacing = CGFloat(PageType.letterSpacingEm(theme.letterSpacing))
        let em = theme.fontSize

        func paragraphStyle(_ style: BlockStyle, isFirstBlock: Bool, afterBreak: Bool) -> NSParagraphStyle {
            let p = NSMutableParagraphStyle()
            // Multiplies the face's own line, so a face other than
            // Literata takes the multiple that keeps Literata's pitch (A69).
            p.lineHeightMultiple = CGFloat(PageType.naturalLineMultiple(Double(theme.lineHeightMultiple), face: theme.face))
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
            // A verse to a line (A68, I42): where the core says a verse in
            // prose starts a line, a LINE SEPARATOR goes in ahead of its
            // number. One paragraph still, so its first line keeps its
            // indent and its space before; and never verse text, so it is
            // not handed to `page.append`, and every mark, selection,
            // number and landing counts in the verse's own text as before.
            let lineStarts: Set<Int> = theme.versePerLine ? Set(block.verseLineStarts()) : []
            for (index, span) in block.x.enumerated() {
                if let verse = span.v { runningVerse = verse }
                if lineStarts.contains(index) {
                    blockText.append(NSAttributedString(
                        string: "\u{2028}",
                        attributes: [.paragraphStyle: style, .font: bodyFont]))
                }
                if let verse = span.v, verse != 1 {
                    // The verse number: small caps superscript, ~45%, or
                    // clearer (A68). Where it is set is kept, for a tap on
                    // it (A62).
                    let number = "\(verse)\u{2009}"
                    page.number(verse, at: blockStart + blockText.length, length: (number as NSString).length)
                    blockText.append(NSAttributedString(
                        string: number,
                        attributes: [
                            .font: RibbonType.uiSmallCaps(theme.fontSize * 0.62),
                            .foregroundColor: ivory.withAlphaComponent(theme.verseNumberAlpha),
                            .baselineOffset: theme.fontSize * 0.3,
                            .ribbonVerse: verse,
                            .paragraphStyle: style,
                        ]))
                }
                var attributes: [NSAttributedString.Key: Any] = [
                    .font: block.s == .d ? titleFont : bodyFont,
                    .foregroundColor: block.s == .d
                        ? UIColor(Palette.muted)
                        : (span.isRedLetter && theme.redLetter
                            ? UIColor(Ink.crimson.color)
                            : ivory),
                    .paragraphStyle: style,
                ]
                if spacing > 0 {
                    attributes[.kern] = spacing * (block.s == .d ? titleFont : bodyFont).pointSize
                }
                if let verse = runningVerse, block.s != .d {
                    attributes[.ribbonVerse] = verse
                    // Where this run sits in the verse's own text and on
                    // the page — the two coordinate systems a phrase mark
                    // moves between.
                    page.append(span.t, verse: verse, at: blockStart + blockText.length)
                }
                blockText.append(NSAttributedString(string: span.t, attributes: attributes))
            }
            if blockText.length > 0 {
                blockText.append(NSAttributedString(string: "\n", attributes: [.paragraphStyle: style, .font: bodyFont]))
                result.append(blockText)
                isFirstContentBlock = false
            }
        }
        // The verse texts built here are the chapter's own text
        // (`ScriptureChapter.ownTexts()` in the core), the coordinates every
        // phrase mark and every word link counts in (A41g, A60). This loop
        // keeps its own walk because it sets the page as it goes; the two
        // must never disagree.
        assert(page.verseText == chapter.ownTexts(), "the page's own text has left the core's")
        return (result, page)
    }
}
