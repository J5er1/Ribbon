import SwiftUI
import UIKit
import RibbonCore

// One chapter of Scripture, set like a page (S02): Literata at the reader's
// size, verse numbers in small caps superscript at ~45% opacity, hanging
// indents for poetry, the running head set into the text block and
// scrolling with it. Built on TextKit 1 so highlight washes can be drawn
// with bleed and multiply blending (§4.5), and so a note can open the line
// height and unfurl in place (S04) — never a modal, never a sheet.

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

/// A highlight, resolved for drawing.
struct WashSpec {
    var range: NSRange
    var color: UIColor
    var alpha: CGFloat
}

private extension NSAttributedString.Key {
    /// Verse number carried on every glyph of the verse, for hit-testing.
    static let ribbonVerse = NSAttributedString.Key("ribbonVerse")
}

// MARK: - Layout manager with ink washes

/// Draws highlight washes behind the glyphs: rounded, bleeding ~2 pt past
/// the glyph box, with slightly irregular edges so it reads as ink soaking
/// into paper rather than a filled rectangle. Overlaps arrive precomputed
/// as multiplied colors, so two people marking the same verse produces a
/// third color.
final class InkLayoutManager: NSLayoutManager {
    var washes: [WashSpec] = []

    override func drawBackground(forGlyphRange glyphsToShow: NSRange, at origin: CGPoint) {
        if let context = UIGraphicsGetCurrentContext() {
            context.saveGState()
            for wash in washes {
                let glyphRange = self.glyphRange(
                    forCharacterRange: wash.range, actualCharacterRange: nil)
                guard glyphRange.location != NSNotFound, glyphRange.length > 0,
                      let container = textContainer(forGlyphAt: glyphRange.location, effectiveRange: nil)
                else { continue }
                context.setFillColor(wash.color.withAlphaComponent(wash.alpha).cgColor)
                enumerateEnclosingRects(
                    forGlyphRange: glyphRange, withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0),
                    in: container
                ) { rect, _ in
                    var r = rect.offsetBy(dx: origin.x, dy: origin.y)
                    // Bleed past the glyph box; jitter by a stable hash so
                    // the edge is irregular but doesn't shimmer on redraw.
                    let h = CGFloat((wash.range.location &* 31 &+ Int(rect.minY)) % 5) - 2
                    r = r.insetBy(dx: -2, dy: -1.2).offsetBy(dx: 0, dy: h * 0.2)
                    let path = UIBezierPath(
                        roundedRect: r,
                        byRoundingCorners: .allCorners,
                        cornerRadii: CGSize(width: 3 + abs(h), height: 4))
                    context.addPath(path.cgPath)
                    context.fillPath()
                }
            }
            context.restoreGState()
        }
        super.drawBackground(forGlyphRange: glyphsToShow, at: origin)
    }
}

// MARK: - The chapter view

/// Where each verse's marks and geometry ended up, for the SwiftUI overlay.
struct ChapterLayout: Equatable {
    /// Verse → the y-midpoint of its first line (marks pin to the first
    /// line — S02 edge cases).
    var verseFirstLineY: [Int: CGFloat] = [:]
    var height: CGFloat = 0
}

struct ChapterTextView: UIViewRepresentable {
    let chapter: ScriptureChapter
    /// The running head, fully formed: "Mark 4", "Psalm 23".
    let runningHead: String
    let theme: ReadingTheme
    /// Inks covering each verse. One ink washes at 24%; overlapping inks
    /// multiply into a third color — the correct emotional result (§4.5).
    let verseInks: [Int: [Ink]]
    /// Verse currently lifted by a long-press (drawn raised, with a soft
    /// shadow).
    let liftedVerses: ClosedRange<Int>?
    /// An open note's carve-out: verse and the height to open beneath it.
    let openNote: (verse: Int, height: CGFloat)?
    let isFirstChapter: Bool
    let showMarginHint: Bool

    var onLayout: (ChapterLayout) -> Void
    /// The reading is a record (a finished book): verses carry no actions.
    var readOnly: Bool = false
    var onLongPressVerse: (Int) -> Void
    var onDragToVerse: (Int) -> Void
    var onDragEnded: () -> Void
    var onTapVerse: (Int) -> Void
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
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.parent = self
        // Rebuild the page only when something that sets it changed — the
        // body re-evaluates on every scroll tick, and NSShadow has no
        // value equality, so an isEqual comparison can't be the gate.
        let buildKey = [
            runningHead, String(chapter.n), String(describing: theme),
            liftedVerses.map(String.init(describing:)) ?? "-",
            String(isFirstChapter), String(showMarginHint),
        ].joined(separator: "|")
        if context.coordinator.builtKey != buildKey {
            context.coordinator.builtKey = buildKey
            view.attributedText = Self.attributedText(
                chapter: chapter, runningHead: runningHead, theme: theme,
                liftedVerses: liftedVerses, isFirstChapter: isFirstChapter,
                showMarginHint: showMarginHint)
        }
        if let ink = view.layoutManager as? InkLayoutManager {
            let washes = verseInks.compactMap { verse, inks -> WashSpec? in
                guard !inks.isEmpty,
                      let range = Self.characterRange(ofVerse: verse, in: view.attributedText)
                else { return nil }
                var color = inks[0].uiColor
                for other in inks.dropFirst() {
                    color = Self.multiply(color, other.uiColor)
                }
                let alpha = min(0.45, Palette.highlightWash + 0.14 * Double(inks.count - 1))
                return WashSpec(range: range, color: color, alpha: alpha)
            }.sorted { $0.range.location < $1.range.location }
            let changed = washes.map(\.range) != ink.washes.map(\.range)
                || washes.map(\.alpha) != ink.washes.map(\.alpha)
                || washes.map(\.color) != ink.washes.map(\.color)
            if changed {
                ink.washes = washes
                // The glyphs live in the text container's own drawing pass;
                // invalidating the view's layer would not repaint them.
                view.layoutManager.invalidateDisplay(
                    forGlyphRange: NSRange(location: 0, length: view.layoutManager.numberOfGlyphs))
            }
        }
        // The open note carves space beneath its verse's last line.
        var exclusions: [UIBezierPath] = []
        if let openNote,
           let range = Self.characterRange(ofVerse: openNote.verse, in: view.attributedText) {
            let glyphRange = view.layoutManager.glyphRange(
                forCharacterRange: range, actualCharacterRange: nil)
            if glyphRange.length > 0 {
                let end = view.layoutManager.boundingRect(
                    forGlyphRange: NSRange(location: max(glyphRange.location, glyphRange.location + glyphRange.length - 1), length: 1),
                    in: view.textContainer)
                let slotTop = end.maxY + 6
                exclusions.append(UIBezierPath(rect: CGRect(
                    x: 0, y: slotTop,
                    width: view.textContainer.size.width > 0 ? view.textContainer.size.width : 10_000,
                    height: openNote.height + 12)))
                DispatchQueue.main.async {
                    onNoteSlot(slotTop + view.textContainerInset.top + 6)
                }
            }
        }
        // Reassigning exclusion paths invalidates layout even when nothing
        // changed — only touch them on a real change.
        if view.textContainer.exclusionPaths.map(\.bounds) != exclusions.map(\.bounds) {
            view.textContainer.exclusionPaths = exclusions
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
        private var pendingReport = false

        init(_ parent: ChapterTextView) {
            self.parent = parent
        }

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
            var verseText: [Int: String] = [:]
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
                verseText[verse, default: ""] += (text.attributedSubstring(from: range).string)
            }
            layout.height = view.sizeThatFits(
                CGSize(width: view.bounds.width, height: .greatestFiniteMagnitude)).height
            rebuildAccessibilityElements(on: view, verseText: verseText, verseRect: verseRect)
            parent.onLayout(layout)
        }

        /// Verse-by-verse VoiceOver navigation (§11): one element per
        /// verse, so a swipe moves by verse — and the label obeys Law 2
        /// ("Verse nine." then the words; never a position report).
        private func rebuildAccessibilityElements(
            on view: UITextView, verseText: [Int: String], verseRect: [Int: CGRect]
        ) {
            var elements: [UIAccessibilityElement] = []
            // The running head (and the one-time hint) are heard, as the
            // heading of the chapter, before its verses.
            let head = UIAccessibilityElement(accessibilityContainer: view)
            head.accessibilityFrameInContainerSpace = CGRect(
                x: 0, y: 0, width: max(1, view.bounds.width), height: 24)
            head.accessibilityLabel = parent.showMarginHint && parent.isFirstChapter
                ? "\(parent.runningHead). \(Copy.firstRunHint)"
                : parent.runningHead
            head.accessibilityTraits = .header
            elements.append(head)
            for verse in verseText.keys.sorted() {
                guard let rect = verseRect[verse], let body = verseText[verse] else { continue }
                let element = UIAccessibilityElement(accessibilityContainer: view)
                element.accessibilityFrameInContainerSpace = rect
                element.accessibilityLabel = "Verse \(verse). \(body.trimmingCharacters(in: .whitespacesAndNewlines))"
                if !parent.readOnly {
                    // The long-press, as actions (§11 motor): a VoiceOver
                    // reader can leave a note or a highlight too.
                    let leave = UIAccessibilityCustomAction(name: Copy.leaveANote) { [weak self] _ in
                        self?.parent.onLongPressVerse(verse)
                        return true
                    }
                    let highlight = UIAccessibilityCustomAction(name: Copy.highlight) { [weak self] _ in
                        self?.parent.onLongPressVerse(verse)
                        return true
                    }
                    element.accessibilityCustomActions = [leave, highlight]
                }
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
            guard let view = textView, !parent.readOnly else { return }
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

    static func multiply(_ a: UIColor, _ b: UIColor) -> UIColor {
        var (ar, ag, ab, aa): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        var (br, bg, bb, ba): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        return UIColor(red: ar * br, green: ag * bg, blue: ab * bb, alpha: 1)
    }

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
        liftedVerses: ClosedRange<Int>?, isFirstChapter: Bool, showMarginHint: Bool
    ) -> NSAttributedString {
        let result = NSMutableAttributedString()
        let ivory = UIColor(Palette.text)
        let bodyFont = RibbonType.uiScripture(theme.fontSize)
        let em = theme.fontSize

        func paragraphStyle(_ style: BlockStyle, isFirstBlock: Bool, afterBreak: Bool) -> NSParagraphStyle {
            let p = NSMutableParagraphStyle()
            p.lineHeightMultiple = theme.lineHeightMultiple
            // A stanza break (the USFX "b" block) opens space before
            // whatever follows it — poetry above all: without this the
            // whole Psalter runs together (S02 edge cases).
            if afterBreak { p.paragraphSpacingBefore = em * 0.75 }
            switch style {
            case .p:
                // A printed page: first-line indent, except the paragraph
                // that opens the chapter.
                p.firstLineHeadIndent = isFirstBlock ? 0 : em * 0.95
            case .m:
                break
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
                }
                if let lifted = liftedVerses,
                   let verse = attributes[.ribbonVerse] as? Int,
                   lifted.contains(verse) {
                    let shadow = NSShadow()
                    shadow.shadowColor = UIColor.black.withAlphaComponent(0.7)
                    shadow.shadowBlurRadius = 8
                    shadow.shadowOffset = CGSize(width: 0, height: 3)
                    attributes[.shadow] = shadow
                    attributes[.baselineOffset] = 2
                }
                blockText.append(NSAttributedString(string: span.t, attributes: attributes))
            }
            if blockText.length > 0 {
                blockText.append(NSAttributedString(string: "\n", attributes: [.paragraphStyle: style, .font: bodyFont]))
                result.append(blockText)
                isFirstContentBlock = false
            }
        }
        return result
    }

}
