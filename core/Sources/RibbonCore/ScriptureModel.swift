import Foundation

// The Scripture data model. Text ships as one JSON file per book per
// translation (produced by tools/usfx_to_json.py from the public-domain
// USFX sources at ebible.org), preserving the structure a printed page
// keeps: paragraphs, poetic lines with their indents, and Jesus' words for
// the optional red-letter setting (S20).

/// How a block of text sits on the page.
public enum BlockStyle: String, Codable, Hashable, Sendable {
    /// A prose paragraph.
    case p
    /// A continuation paragraph (no first-line indent).
    case m
    /// A poetic line, first indent level. Rendered with a hanging indent,
    /// never a horizontal scroll (S02 edge cases).
    case q1
    /// A poetic line, second indent level.
    case q2
    /// A descriptor — a psalm title like "A Psalm of David."
    case d
    /// A stanza break.
    case b
}

/// A run of text inside a block. When `v` is present the span begins that
/// verse, and the verse number renders in small caps superscript at ~45%
/// opacity (S02). `w` marks words of Jesus for red-letter.
public struct ScriptureSpan: Codable, Hashable, Sendable {
    public var v: Int?
    public var t: String
    public var w: Bool?

    public init(v: Int? = nil, t: String, w: Bool? = nil) {
        self.v = v
        self.t = t
        self.w = w
    }

    public var isRedLetter: Bool { w == true }
}

public struct ScriptureBlock: Codable, Hashable, Sendable {
    public var s: BlockStyle
    public var x: [ScriptureSpan]

    public init(s: BlockStyle, x: [ScriptureSpan]) {
        self.s = s
        self.x = x
    }
}

public struct ScriptureChapter: Codable, Hashable, Sendable {
    public var n: Int
    public var blocks: [ScriptureBlock]

    public init(n: Int, blocks: [ScriptureBlock]) {
        self.n = n
        self.blocks = blocks
    }

    /// The verses present in this chapter, in order.
    public var verseNumbers: [Int] {
        blocks.flatMap { $0.x.compactMap(\.v) }
    }

    /// The full text of one verse across blocks — what a note quotes.
    public func text(forVerse verse: Int) -> String? {
        var parts: [String] = []
        var inVerse = false
        for block in blocks {
            for span in block.x {
                if let v = span.v {
                    inVerse = (v == verse)
                }
                if inVerse {
                    parts.append(span.t)
                }
            }
        }
        guard !parts.isEmpty else { return nil }
        return parts.joined(separator: " ")
            .replacingOccurrences(of: "  ", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

public struct ScriptureBookText: Codable, Hashable, Sendable {
    public var id: String
    public var name: String
    public var chapters: [ScriptureChapter]

    public init(id: String, name: String, chapters: [ScriptureChapter]) {
        self.id = id
        self.name = name
        self.chapters = chapters
    }

    public func chapter(_ n: Int) -> ScriptureChapter? {
        chapters.first { $0.n == n }
    }
}
