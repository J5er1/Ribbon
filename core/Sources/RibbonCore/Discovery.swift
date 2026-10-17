import Foundation

// Being found (A72): a person who chooses to be found, a room that everyone
// in it has opened, and a stranger asking to join. The server decides who
// may see what (supabase/migrations/20261016180000_ribbon_being_found.sql);
// this file holds the shapes the phones draw and the few rules they apply
// themselves, so both phones apply them the same way.
//
// What is never here: a count. Not of members, not of asks, not of rooms.
// A room is open or it is not, full or it is not, and the people in it who
// chose to be found are faces, not a number.
//
// The Kotlin port (Discovery.kt) matches this case for case;
// DiscoveryTests.swift and DiscoveryTest.kt hold the two to the same
// answers.

/// One line in your own words, on a profile or a room. Trimmed, inner runs
/// of whitespace closed up, and cut at a word near the limit; empty is no
/// line at all.
public enum OwnLine {
    /// A profile's line or a room's (the server holds them to 140).
    public static let limit = 140
    /// What someone says when they ask to join (the server holds it to 280).
    public static let askLimit = 280

    public static func clean(_ text: String?, limit: Int = OwnLine.limit) -> String? {
        guard let text else { return nil }
        let words = text.split(whereSeparator: \.isWhitespace)
        guard !words.isEmpty else { return nil }
        let joined = words.joined(separator: " ")
        guard joined.count > limit else { return joined }
        // Cut at the last space that fits, so a word is never halved; a
        // single word longer than the limit is cut where the limit falls.
        let cut = String(joined.prefix(limit))
        if let space = cut.lastIndex(of: " ") {
            return String(cut[..<space])
        }
        return cut
    }
}

/// Whether you can be found, and the line people see. Kept on this phone
/// and pushed to your profile; off unless you turn it on, and only once you
/// have said you are thirteen or older.
public struct BeingFound: Codable, Hashable, Sendable {
    public var findable: Bool
    public var line: String?
    /// When you said you are thirteen or older. Nil until you have.
    public var saidThirteenAt: Date?

    public init(findable: Bool = false, line: String? = nil, saidThirteenAt: Date? = nil) {
        self.findable = findable
        self.line = line
        self.saidThirteenAt = saidThirteenAt
    }

    /// Turning it on is only possible once thirteen has been said.
    public var canBeFindable: Bool { saidThirteenAt != nil }
}

/// A face in a found room, or a found person: only what they chose to show.
public struct PublicFace: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var name: String
    public var line: String?
    public var portraitPath: String?

    public init(id: UUID, name: String, line: String? = nil, portraitPath: String? = nil) {
        self.id = id
        self.name = name
        self.line = line
        self.portraitPath = portraitPath
    }
}

/// A room you could ask to join, as `find_rooms` describes it.
public struct OpenRoom: Hashable, Identifiable, Sendable {
    public var id: UUID
    public var name: String?
    public var line: String?
    public var full: Bool
    /// You have asked, and the ask is still standing.
    public var asked: Bool
    /// The book it is reading, if it is reading one.
    public var bookID: String?
    /// The fire as stored; the phone's fire engine reads a state from it.
    public var fire: Handiwork?
    /// The people in it who chose to be found, in the order they joined.
    public var faces: [PublicFace]

    public init(
        id: UUID, name: String? = nil, line: String? = nil, full: Bool = false, asked: Bool = false,
        bookID: String? = nil, fire: Handiwork? = nil, faces: [PublicFace] = []
    ) {
        self.id = id
        self.name = name
        self.line = line
        self.full = full
        self.asked = asked
        self.bookID = bookID
        self.fire = fire
        self.faces = faces
    }

    /// The fire's state, as a glance would report it. Quiet days are the
    /// room's own business, so a found room's fire is never shown banked.
    public func fireState(at now: Date) -> FireState? {
        fire?.state(at: now)
    }

    /// The room's own name, if it has one worth showing.
    public var shownName: String? { OwnLine.clean(name, limit: 80) }

    /// When the room has no name: the first names of up to three people
    /// who chose to be found, for the phone to say in its own words.
    public var firstNames: [String] {
        faces.prefix(3).map { face in
            face.name.split(separator: " ").first.map(String.init) ?? face.name
        }
    }
}

/// Someone asking to join a room you are in.
public struct Knock: Hashable, Identifiable, Sendable {
    /// How long an ask stands before it lapses without anyone saying no.
    public static let lasts: TimeInterval = 14 * 24 * 60 * 60

    public var roomID: UUID
    public var person: PublicFace
    /// What they said when they asked, if anything.
    public var line: String?
    public var askedAt: Date

    public var id: String { "\(roomID.uuidString)/\(person.id.uuidString)" }

    public init(roomID: UUID, person: PublicFace, line: String? = nil, askedAt: Date) {
        self.roomID = roomID
        self.person = person
        self.line = line
        self.askedAt = askedAt
    }

    public func stands(at now: Date) -> Bool {
        now.timeIntervalSince(askedAt) < Self.lasts
    }

    /// The asks worth showing: standing ones, oldest first, so nobody who
    /// asked earlier is pushed down by somebody who asked later.
    public static func standing(_ knocks: [Knock], at now: Date) -> [Knock] {
        knocks.filter { $0.stands(at: now) }.sorted { $0.askedAt < $1.askedAt }
    }
}

/// Whether a room can be found, from your side of it. A room is open only
/// while everyone in it has said yes, the way closing a room asks everyone
/// (§6.8). Who has not said yes is never named: a name attached to
/// something not done is an accusation.
public enum RoomOpenness: Equatable, Sendable {
    /// You have not said yes.
    case closed
    /// You have; somebody else has not yet.
    case waitingForOthers
    /// Everyone has. The room can be found and asked to join.
    case open

    public static func of(_ memberships: [Membership], me: UUID) -> RoomOpenness {
        guard let mine = memberships.first(where: { $0.personID == me }), mine.wouldOpen else {
            return .closed
        }
        return memberships.allSatisfy(\.wouldOpen) ? .open : .waitingForOthers
    }
}

/// Turning what someone types into the books it could mean, so "mark",
/// "Mark 4" or "1 cor" find the rooms reading them. Matches a book's name
/// or its id from the start of a word, ignoring case and a trailing
/// chapter.
public enum BookSearch {
    public static func books(matching query: String) -> [String] {
        let words = query.lowercased()
            .split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
        // A trailing chapter or verse ("4", "4:12") says nothing about which
        // book; drop it, and anything after it.
        var bookWords: [String] = []
        for word in words {
            if word.first?.isNumber == true, !bookWords.isEmpty { break }
            bookWords.append(word)
        }
        let wanted = bookWords.joined(separator: " ")
        guard wanted.count >= 2 else { return [] }
        return Bible.books.compactMap { book in
            let name = book.name.lowercased()
            if name.hasPrefix(wanted) || book.id.lowercased() == wanted { return book.id }
            return nil
        }
    }
}
