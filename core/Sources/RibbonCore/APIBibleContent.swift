import Foundation

// Converts an API.Bible chapter payload (`content-type=json`) into the same
// page model the bundled translations use, so the reading surface never
// knows where text came from. Their JSON is a tree of paragraph nodes with
// USFM-style block styles; verse markers arrive as items inside them —
// close enough to USFX that the same style mapping applies.
//
// Parsing is deliberately lenient: unknown node shapes are walked for
// their text rather than rejected, because a licensed feed that changes
// shape must degrade to readable prose, never to a blank page.

public enum APIBibleContent {

    /// Decode `{"data": {"content": [...]}}` (or a bare content array)
    /// into a chapter. Returns nil when no verse text could be found.
    public static func chapter(number: Int, from data: Data) -> ScriptureChapter? {
        guard let root = try? JSONSerialization.jsonObject(with: data) else { return nil }

        var content: [Any]?
        if let object = root as? [String: Any] {
            if let dataObject = object["data"] as? [String: Any] {
                content = dataObject["content"] as? [Any]
            } else {
                content = object["content"] as? [Any]
            }
        } else if let array = root as? [Any] {
            content = array
        }
        guard let nodes = content else { return nil }

        var builder = Builder()
        for node in nodes {
            builder.walkBlock(node)
        }
        builder.closeBlock()

        let blocks = builder.blocks
        guard blocks.contains(where: { !$0.x.isEmpty }) else { return nil }
        return ScriptureChapter(n: number, blocks: blocks)
    }

    // The USFM paragraph styles worth keeping, mapped exactly like the
    // USFX converter (tools/usfx_to_json.py).
    static let styleMap: [String: BlockStyle] = [
        "p": .p, "pc": .p, "pi1": .p, "pi2": .p, "po": .p,
        "m": .m, "mi": .m, "nb": .m, "pmo": .m, "pm": .m, "pmc": .m, "pmr": .m, "cls": .m,
        "q": .q1, "q1": .q1, "li1": .q1, "qm1": .q1,
        "q2": .q2, "q3": .q2, "q4": .q2, "qr": .q2, "li2": .q2, "li3": .q2, "qm2": .q2, "qm3": .q2,
        "d": .d, "qa": .d, "sp": .d, "qd": .d,
        "b": .b,
    ]

    /// Styles whose whole subtree is headings or apparatus, not Scripture.
    static let skippedStyles: Set<String> = [
        "s", "s1", "s2", "s3", "ms", "ms1", "r", "mt", "mt1", "mt2", "mt3",
        "mr", "sr", "cl", "cp", "f", "x", "fe", "note",
    ]

    private struct Builder {
        var blocks: [ScriptureBlock] = []
        var spans: [ScriptureSpan] = []
        var blockStyle: BlockStyle = .p
        var pendingVerse: Int?
        var redLetterDepth = 0

        mutating func walkBlock(_ node: Any) {
            guard let object = node as? [String: Any] else { return }
            let style = (object["attrs"] as? [String: Any])?["style"] as? String ?? "p"
            if APIBibleContent.skippedStyles.contains(style) { return }
            let mapped = APIBibleContent.styleMap[style]
            closeBlock()
            if mapped == .b {
                if let last = blocks.last, last.s != .b {
                    blocks.append(ScriptureBlock(s: .b, x: []))
                }
                return
            }
            blockStyle = mapped ?? .p
            walkItems(object["items"] as? [Any] ?? [])
            closeBlock()
        }

        mutating func walkItems(_ items: [Any]) {
            // The live feed's node shape (verified against the real NKJV/
            // NIV/NASB payloads): text nodes are {type:"text", text},
            // everything else is {type:"tag", name, attrs.style}. A verse
            // marker is name "verse" with attrs.number — and its own items
            // repeat the number as text, so a marker's subtree is never
            // walked.
            for item in items {
                guard let object = item as? [String: Any] else { continue }
                let name = object["name"] as? String
                let type = object["type"] as? String
                let attrs = object["attrs"] as? [String: Any]
                let style = attrs?["style"] as? String

                if type == "text" {
                    if let text = object["text"] as? String {
                        push(text)
                    }
                    continue
                }
                if name == "verse" || style == "v" {
                    if let raw = attrs?["number"] as? String,
                       let number = Int(raw.prefix(while: \.isNumber)) {
                        pendingVerse = number
                    }
                    continue
                }
                if let style, APIBibleContent.skippedStyles.contains(style) {
                    continue  // a footnote or cross-reference subtree
                }
                let isRed = style == "wj"
                if isRed { redLetterDepth += 1 }
                walkItems(object["items"] as? [Any] ?? [])
                if isRed { redLetterDepth -= 1 }
            }
        }

        mutating func push(_ raw: String) {
            let text = raw.replacingOccurrences(
                of: "\\s+", with: " ", options: .regularExpression)
            guard !text.trimmingCharacters(in: .whitespaces).isEmpty || !spans.isEmpty else { return }
            let red = redLetterDepth > 0
            if pendingVerse == nil,
               var last = spans.last, last.isRedLetter == red {
                last.t += text
                spans[spans.count - 1] = last
            } else {
                spans.append(ScriptureSpan(v: pendingVerse, t: text, w: red ? true : nil))
                pendingVerse = nil
            }
        }

        mutating func closeBlock() {
            var cleaned: [ScriptureSpan] = []
            for (index, span) in spans.enumerated() {
                var text = span.t
                if index == 0 { text = String(text.drop(while: { $0 == " " })) }
                if index == spans.count - 1 {
                    text = String(text.reversed().drop(while: { $0 == " " }).reversed())
                }
                guard !text.isEmpty else { continue }
                cleaned.append(ScriptureSpan(v: span.v, t: text, w: span.w))
            }
            if !cleaned.isEmpty {
                blocks.append(ScriptureBlock(s: blockStyle, x: cleaned))
            }
            spans = []
        }
    }
}
