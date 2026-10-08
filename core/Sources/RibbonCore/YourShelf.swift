import Foundation

// Your shelf (A67): every book you have finished, in every room you have
// read in, as one row of embers under your name on You. A room's shelf
// (S10) is that room's; this one is yours — what the printed keepsake
// would be if it were made of a person rather than of a room.
//
// It is a row of objects, never a tally. Nothing here is counted or
// returned as a count, and the screen draws no number near it (§17's first
// question). Each ember says what book it was and who it was read with,
// and that is all.

/// Who a finished book was read with, as an ember on your shelf says it.
public enum ShelfCompany: Equatable, Hashable, Sendable {
    /// Nobody else is in that room now.
    case alone
    /// The others, in the order they came into the room, and whether there
    /// are more than are named — said as "and others", never as a number.
    case people([UUID], andOthers: Bool)
    /// A room of three or more that has a name of its own is said by it:
    /// "with the Thursday study" rather than a list of first names.
    case room(String)
}

public enum YourShelf {
    /// The most people an ember names before "and others".
    public static let named = 2

    /// Every finished reading this phone holds, the first finished first —
    /// the order the embers were made in. That includes the books of a room
    /// you have since left: leaving says "You'll keep the books on your
    /// shelf" (§6.8), and until there was a shelf of your own there was
    /// nowhere for that to be true. Leaving takes the room and your
    /// membership; its readings stay on the phone, and so on here.
    public static func embers(readings: [Reading]) -> [Reading] {
        readings
            .filter(\.isFinished)
            .sorted { a, b in
                let first = a.finishedAt ?? .distantPast
                let second = b.finishedAt ?? .distantPast
                if first != second { return first < second }
                return a.id.uuidString.lowercased() < b.id.uuidString.lowercased()
            }
    }

    /// Who a reading was read with: everybody else in its room now, in the
    /// order they joined. A room of three or more with a name is said by its
    /// name; otherwise the first `named` people are named, and "and others"
    /// carries the rest. A book of a room you have left is simply yours:
    /// `.alone`, said by nothing — whatever memberships of that room the
    /// phone still holds (leaving takes only your own), because the people
    /// of a room you have walked out of are not named on your page.
    public static func company(
        of reading: Reading, rooms: [Room], memberships: [Membership], me: UUID?
    ) -> ShelfCompany {
        guard let room = rooms.first(where: { $0.id == reading.roomID }) else { return .alone }
        let others = memberships
            .filter { $0.roomID == reading.roomID && $0.personID != me }
            .sorted { a, b in
                if a.joinedAt != b.joinedAt { return a.joinedAt < b.joinedAt }
                return a.personID.uuidString.lowercased() < b.personID.uuidString.lowercased()
            }
            .map(\.personID)
        if others.isEmpty { return .alone }
        let name = room.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if others.count >= 2, !name.isEmpty { return .room(name) }
        return .people(Array(others.prefix(named)), andOthers: others.count > named)
    }
}
