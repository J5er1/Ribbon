import Foundation

// "In this room" (A62): how the versions read here say the words you chose,
// grouped by what they say rather than listed person by person — so a room
// of eleven on four versions is a handful of blocks, not eleven lines.
//
// Two versions say the same thing when their words are the same, in the
// same order, with capitals and punctuation set aside ("Through Him," and
// "through him" are one; "him through" is not). The words are PivotAligner's
// tokens, compared on `norm`. Yours comes first. Every other rendering
// carries the words of it that are not in yours — an order-aware word diff,
// a longest common subsequence over the norms — so the panel can set those
// at full strength and the shared words back a step.
//
// The Kotlin port (RoomRenderings.kt) matches this case for case;
// RoomRenderingsTests.swift and RoomRenderingsTest.kt hold the two to the
// same answers.

/// What the versions in a room say for one selection, grouped.
public struct RoomRenderings: Hashable, Sendable {
    /// One version as the room reads it, and how it says the selection.
    public struct Said: Hashable, Sendable {
        public var version: TranslationID
        /// The version's words for the selection; nil when the version is
        /// licensed and has not reached this phone.
        public var phrase: String?
        /// Who here reads it, in the order their faces should sit. For yours,
        /// you first.
        public var readers: [UUID]

        public init(version: TranslationID, phrase: String?, readers: [UUID]) {
            self.version = version
            self.phrase = phrase
            self.readers = readers
        }
    }

    /// One thing said, by every version that says it.
    public struct Group: Hashable, Sendable {
        /// Yours first in your group; otherwise in the order they were given.
        public var versions: [TranslationID]
        /// The words, as the first of `versions` says them (yours, in yours).
        public var phrase: String
        /// The words of `phrase` that are not in yours, as UTF-16 ranges into
        /// `phrase`, one per word, in order. Always empty for yours.
        public var differing: [TextRange]
        /// Everyone who reads one of `versions`: the readers of each version
        /// in turn, in the order of `versions` — so in yours, you first.
        public var readers: [UUID]
        public var isYours: Bool

        public init(versions: [TranslationID], phrase: String, differing: [TextRange], readers: [UUID], isYours: Bool) {
            self.versions = versions
            self.phrase = phrase
            self.differing = differing
            self.readers = readers
            self.isYours = isYours
        }
    }

    /// Versions somebody here reads whose words are not on this phone. They
    /// cannot be compared, so they are kept out of `groups` — and while there
    /// are any, the room cannot be said to agree with you. Readers follow
    /// the order of `versions`, as in a group.
    public struct Unavailable: Hashable, Sendable {
        public var versions: [TranslationID]
        public var readers: [UUID]

        public init(versions: [TranslationID], readers: [UUID]) {
            self.versions = versions
            self.readers = readers
        }
    }

    /// Yours first, then every other rendering in the order its first version
    /// was given.
    public var groups: [Group]
    /// Nil when every version here is on this phone.
    public var notOnThisPhone: Unavailable?

    public init(groups: [Group], notOnThisPhone: Unavailable?) {
        self.groups = groups
        self.notOnThisPhone = notOnThisPhone
    }

    /// Everyone here reads these words as you do: one rendering, and nothing
    /// that could not be compared. (A room on one version agrees too; the
    /// panel leaves the section out for that by `isOneVersion`.)
    public var allAgree: Bool {
        groups.count == 1 && notOnThisPhone == nil
    }

    /// Everyone here reads your version.
    public var isOneVersion: Bool {
        allAgree && groups[0].versions.count == 1
    }

    /// Groups `others` against `yours`. A version given twice joins the
    /// group it is already in. `yours.phrase` is the page's own words; nil is
    /// read as no words at all.
    public static func of(yours: Said, others: [Said]) -> RoomRenderings {
        let mine = yours.phrase ?? ""
        // Each group's versions, with each version's readers, in the order
        // they are first given; flattened at the end so readers follow the
        // order of the versions.
        var found: [(said: [Said], key: [String])] = [([yours], norms(mine))]
        var missing: [Said] = []

        for said in others {
            if let g = found.firstIndex(where: { $0.said.contains { $0.version == said.version } }) {
                let v = found[g].said.firstIndex { $0.version == said.version }!
                found[g].said[v].readers += said.readers
            } else if let v = missing.firstIndex(where: { $0.version == said.version }) {
                missing[v].readers += said.readers
            } else if let phrase = said.phrase {
                let key = norms(phrase)
                if let g = found.firstIndex(where: { $0.key == key }) {
                    found[g].said.append(said)
                } else {
                    found.append(([said], key))
                }
            } else {
                missing.append(said)
            }
        }

        let groups = found.enumerated().map { index, group -> Group in
            let phrase = index == 0 ? mine : group.said[0].phrase ?? ""
            return Group(
                versions: group.said.map(\.version),
                phrase: phrase,
                differing: index == 0 ? [] : differing(phrase, from: mine),
                readers: group.said.flatMap(\.readers),
                isYours: index == 0)
        }
        let unavailable = missing.isEmpty ? nil : Unavailable(
            versions: missing.map(\.version), readers: missing.flatMap(\.readers))
        return RoomRenderings(groups: groups, notOnThisPhone: unavailable)
    }

    /// The words of `phrase` not on a longest common subsequence with
    /// `yours`, compared on norms — so a reordering is a difference and a
    /// capital or a comma is not. Ties break the same way on both platforms:
    /// walking forward, a matching pair is always taken; otherwise the word
    /// of `phrase` is passed over when that keeps the subsequence as long.
    public static func differing(_ phrase: String, from yours: String) -> [TextRange] {
        let tokens = PivotAligner.tokens(phrase)
        let a = tokens.map(\.norm)
        let b = norms(yours)
        let n = a.count
        let m = b.count
        // table[i][j]: the longest common subsequence of a[i...] and b[j...].
        var table = Array(repeating: Array(repeating: 0, count: m + 1), count: n + 1)
        if n > 0 && m > 0 {
            for i in stride(from: n - 1, through: 0, by: -1) {
                for j in stride(from: m - 1, through: 0, by: -1) {
                    table[i][j] = a[i] == b[j]
                        ? table[i + 1][j + 1] + 1
                        : max(table[i + 1][j], table[i][j + 1])
                }
            }
        }
        var shared = Array(repeating: false, count: n)
        var i = 0
        var j = 0
        while i < n && j < m {
            if a[i] == b[j] {
                shared[i] = true
                i += 1
                j += 1
            } else if table[i + 1][j] >= table[i][j + 1] {
                i += 1
            } else {
                j += 1
            }
        }
        return tokens.indices
            .filter { !shared[$0] }
            .map { TextRange(start: tokens[$0].start, end: tokens[$0].end) }
    }

    private static func norms(_ text: String) -> [String] {
        PivotAligner.tokens(text).map(\.norm)
    }
}
