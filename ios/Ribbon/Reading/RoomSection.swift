import Foundation
import RibbonCore

// "In this room" (A62, §13.3): what the original panel says about how the
// room's other versions say the words you chose. Grouped by what is said,
// not listed person by person, so a room of eleven on four versions is a
// handful of blocks rather than eleven lines.
//
// Yours comes first, with no card: a small-caps label says it is yours.
// Every other block names its versions, sets its words with the ones that
// are not in yours at full strength, and shows who reads it — at most three
// faces, then first names, ending "and others" past three. Never a number.
// When every version here says what yours says, the section is one muted
// line; when everyone reads your version, it is not there at all.
//
// The grouping itself is RibbonCore's `RoomRenderings`, shared with
// Android. This is the rest of it, worked out without SwiftUI so the same
// answers can be checked without a phone; `OriginalPanel` only draws it.

/// Someone else in the room, and the version they read.
struct RoomPerson: Equatable {
    var id: UUID
    /// Their whole name, as the room has it.
    var name: String
    var version: TranslationID
}

/// One face in a block, and the name said beside it — a first name, or
/// "you".
struct RoomReader: Equatable, Identifiable {
    var id: UUID
    var name: String
}

/// One block of the section: yours, another rendering, or the versions
/// whose words are not on this phone.
struct RoomBlock: Equatable, Identifiable {
    enum Kind: Equatable {
        case yours
        case other
        case notOnThisPhone
    }

    var id: String
    var kind: Kind
    /// Small caps over the words: "Yours · Berean Standard", or the versions
    /// that say it, "World English and American Standard".
    var label: String
    /// The words, as the first of the block's versions says them; for the
    /// versions not on this phone, the sentence that says so.
    var words: String
    /// The words of `words` that are not in yours, as UTF-16 ranges. Empty
    /// for yours, and for a whole verse standing in.
    var differing: [TextRange]
    /// The version could not say the words part by part, so its whole
    /// verse stands in, muted — and is not set against yours word by word.
    var isWholeVerse: Bool
    /// Everyone who reads it, you first in yours.
    var readers: [RoomReader]
    /// The faces shown: the first three readers.
    var faces: [RoomReader]
    /// The names beside the faces: everyone while that is three or fewer;
    /// past that, three and then "and others".
    var names: String
    /// What VoiceOver reads for the whole block: the words, then the
    /// versions, then every first name — the ones past "and others" too.
    var spoken: String

    /// `words` cut where it starts and stops differing from yours, in order:
    /// joined, the runs are `words` exactly. A range out of order, empty,
    /// or past the end is passed over rather than trusted.
    var runs: [(text: String, differs: Bool)] {
        let units = words.utf16
        var runs: [(text: String, differs: Bool)] = []
        var at = 0
        func take(_ from: Int, _ to: Int, differs: Bool) {
            guard to > from else { return }
            let lo = units.index(units.startIndex, offsetBy: from)
            let hi = units.index(units.startIndex, offsetBy: to)
            runs.append((String(words[lo..<hi]), differs))
        }
        for range in differing where range.start >= at && range.end > range.start && range.end <= units.count {
            take(at, range.start, differs: false)
            take(range.start, range.end, differs: true)
            at = range.end
        }
        take(at, units.count, differs: false)
        return runs
    }
}

/// The single muted line for a room whose versions all say what yours
/// says.
struct RoomAgreement: Equatable {
    var line: String
    /// Up to three of the others' faces.
    var faces: [RoomReader]
    /// The line, then every other first name.
    var spoken: String
}

enum RoomSection: Equatable {
    /// Everyone here reads your version: nothing to compare, nothing said.
    case omitted
    /// Every version here says the words as yours does: no heading, one line.
    case agrees(RoomAgreement)
    /// The heading, then one block per thing said, yours first.
    case blocks([RoomBlock])

    /// Faces shown in a row before the names take over.
    static let visibleFaces = 3

    /// The section for one selection.
    ///
    /// - `yours`: the page's version. `me` leads its readers.
    /// - `others`: everyone else in the room, in any order; they are set by
    ///   name, so the same room always reads the same way.
    /// - `saying`: a version's words for the selection, as
    ///   `OriginalSelection.says` gives them; nil when its text is not on
    ///   this phone. Asked once per version.
    /// - `versionName`: a version's display name, "World English".
    /// - `notOnThisPhone`: the sentence said for versions not here yet.
    static func of(
        yours: TranslationID,
        me: UUID?,
        others: [RoomPerson],
        saying: (TranslationID) -> [OriginalSaying]?,
        versionName: (TranslationID) -> String,
        notOnThisPhone: String
    ) -> RoomSection {
        let people = others
            .filter { $0.id != me }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        var firstNames: [UUID: String] = [:]
        for person in people {
            let first = firstName(person.name).trimmingCharacters(in: .whitespaces)
            firstNames[person.id] = first.isEmpty ? Copy.someone : first
        }

        // Each version once: its words, and whether a whole verse stands in.
        var said: [TranslationID: (phrase: String?, whole: Bool)] = [:]
        func phrase(_ version: TranslationID) -> (phrase: String?, whole: Bool) {
            if let known = said[version] { return known }
            let pieces = saying(version)
            let found = (
                phrase: pieces.map { $0.map(\.text).joined(separator: " ") },
                whole: pieces?.contains(where: \.isWholeVerse) ?? false)
            said[version] = found
            return found
        }

        // Yours with you first, then each other version in the order of the
        // first person (by name) who reads it.
        var mine: [UUID] = me.map { [$0] } ?? []
        var order: [TranslationID] = []
        var readers: [TranslationID: [UUID]] = [:]
        for person in people {
            if person.version == yours {
                mine.append(person.id)
                continue
            }
            if readers[person.version] == nil { order.append(person.version) }
            readers[person.version, default: []].append(person.id)
        }
        let renderings = RoomRenderings.of(
            yours: RoomRenderings.Said(version: yours, phrase: phrase(yours).phrase, readers: mine),
            others: order.map {
                RoomRenderings.Said(version: $0, phrase: phrase($0).phrase, readers: readers[$0] ?? [])
            })

        if renderings.isOneVersion { return .omitted }

        func reader(_ id: UUID) -> RoomReader {
            RoomReader(id: id, name: id == me ? Copy.originalYou : firstNames[id] ?? Copy.someone)
        }

        if renderings.allAgree {
            let others = renderings.groups[0].readers.filter { $0 != me }.map(reader)
            let line = others.count == 1 ? Copy.roomOneAgrees(others[0].name) : Copy.roomAllAgree
            return .agrees(RoomAgreement(
                line: line,
                faces: Array(others.prefix(visibleFaces)),
                spoken: others.count == 1 ? line : spoken([line, Copy.listed(others.map(\.name))])))
        }

        var blocks: [RoomBlock] = renderings.groups.map { group in
            let versions = group.versions.map(versionName)
            let whole = !group.isYours && phrase(group.versions[0]).whole
            return block(
                id: group.isYours ? "yours" : "said:" + group.versions.map(\.rawValue).joined(separator: ","),
                kind: group.isYours ? .yours : .other,
                label: group.isYours ? Copy.roomYours(versions) : Copy.listed(versions),
                words: group.phrase,
                differing: whole ? [] : group.differing,
                isWholeVerse: whole,
                readers: group.readers.map(reader))
        }
        if let missing = renderings.notOnThisPhone {
            blocks.append(block(
                id: "missing",
                kind: .notOnThisPhone,
                label: Copy.listed(missing.versions.map(versionName)),
                words: notOnThisPhone,
                differing: [],
                isWholeVerse: false,
                readers: missing.readers.map(reader)))
        }
        return .blocks(blocks)
    }

    private static func block(
        id: String, kind: RoomBlock.Kind, label: String, words: String,
        differing: [TextRange], isWholeVerse: Bool, readers: [RoomReader]
    ) -> RoomBlock {
        RoomBlock(
            id: id, kind: kind, label: label, words: words,
            differing: differing, isWholeVerse: isWholeVerse,
            readers: readers,
            faces: Array(readers.prefix(visibleFaces)),
            names: names(readers.map(\.name)),
            spoken: spoken([words, label, Copy.listed(readers.map(\.name))]))
    }

    /// Everyone by name while that is three or fewer; past that, the first
    /// three and "and others".
    static func names(_ all: [String]) -> String {
        guard all.count > visibleFaces else { return Copy.listed(all) }
        return all.prefix(visibleFaces).joined(separator: ", ") + " " + Copy.roomAndOthers
    }

    /// Parts read one after another as sentences, each closed by a full
    /// stop unless it already ends a sentence — looking past a closing quote
    /// or bracket, so `made.”` is not read as `made.”.`.
    static func spoken(_ parts: [String]) -> String {
        parts
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .map { part in
                let last = part.last { !"”’\"')]".contains($0) }
                return last.map { ".?!…".contains($0) } == true ? part : part + "."
            }
            .joined(separator: " ")
    }
}
