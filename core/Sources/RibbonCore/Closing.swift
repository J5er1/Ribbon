import Foundation

// Closing a room (build book §6.8, A73): "Requires everyone to agree, or 30
// days of silence plus one member's request. On close, every member gets an
// export of the whole shelf — notes, highlights, cards — as a readable
// file. No dark pattern, no retention offer."
//
// The server decides when a room closes (close_room, in
// 20261018120000_ribbon_closing_a_room.sql). This file holds what the
// phones draw from it, and the shelf as a file, written the same on both.
// The Kotlin port (Closing.kt) matches this case for case; ClosingTests and
// ClosingTest hold the two to the same answers.

/// Where a room stands on closing, from your side of it. Who has not asked
/// is never named.
public enum RoomClosing: Equatable, Sendable {
    /// You have not asked.
    case open
    /// You have; somebody else has not, and the room has not been quiet
    /// long enough for one request to be enough.
    case waitingForOthers
    /// It is closed.
    case closed

    public static func of(_ room: Room, _ memberships: [Membership], me: UUID) -> RoomClosing {
        if room.closedAt != nil { return .closed }
        guard let mine = memberships.first(where: { $0.personID == me }), mine.wouldClose else {
            return .open
        }
        return .waitingForOthers
    }

    /// Whether your yes closes the room as you say it: everyone else in it
    /// has said yes already — or left, which a room of one is — or it has
    /// been quiet long enough for one request to be enough. A yes already
    /// given closes the room once this turns true, by being said again.
    public static func closesOnYourYes(
        _ room: Room, _ memberships: [Membership], me: UUID, quiet: Bool
    ) -> Bool {
        if room.closedAt != nil { return false }
        if quiet { return true }
        return memberships
            .filter { $0.roomID == room.id && $0.personID != me }
            .allSatisfy(\.wouldClose)
    }
}

/// Thirty days in which nobody read, wrote or marked anything: then one
/// person's request closes the room.
public enum RoomSilence {
    public static let closesAfter: TimeInterval = 30 * 24 * 60 * 60

    /// When anybody last did anything in the room that this phone knows of:
    /// arrived, read (the fire's last fuel), left a note, or laid a mark.
    /// The server counts card answers too; the phone does not hold their
    /// times, and it only uses this to say which way a request will go.
    public static func lastHeard(
        room: Room, memberships: [Membership], readings: [Reading], notes: [Note], highlights: [Highlight]
    ) -> Date {
        let roomReadings = readings.filter { $0.roomID == room.id }
        let readingIDs = Set(roomReadings.map(\.id))
        var times = [room.createdAt]
        times += memberships.filter { $0.roomID == room.id }.map(\.joinedAt)
        times += roomReadings.compactMap(\.handiwork.lastFuelAt)
        times += notes.filter { readingIDs.contains($0.readingID) }.map(\.createdAt)
        times += highlights.filter { readingIDs.contains($0.readingID) }.map(\.createdAt)
        return times.max() ?? room.createdAt
    }

    public static func isQuiet(lastHeard: Date, at now: Date) -> Bool {
        now.timeIntervalSince(lastHeard) > closesAfter
    }
}

/// The whole shelf of a room as a readable file (§6.8): every book, and in
/// each its notes, marks and the cards the room opened. Plain text, so it
/// opens anywhere and still reads in twenty years.
public enum ShelfExport {
    /// The file's own name: the room's, and what it is.
    public static func fileName(title: String) -> String {
        let safe = title
            .map { "/\\:*?\"<>|".contains($0) ? "-" : $0 }
            .reduce(into: "") { $0.append($1) }
            .trimmingCharacters(in: .whitespaces)
        return "Ribbon - \(safe.isEmpty ? "A room" : safe).txt"
    }

    /// The shelf, written out. `name` says who someone is; `keptAt` is the
    /// day the file is made, and every date is the day in `timeZone`.
    public static func text(
        title: String,
        readings: [Reading],
        notes: [Note],
        highlights: [Highlight],
        cards: [ReflectionCard],
        name: (UUID) -> String,
        keptAt: Date,
        timeZone: TimeZone
    ) -> String {
        var lines: [String] = [title, "The shelf, kept \(day(keptAt, timeZone)).", ""]
        let books = readings.sorted { ($0.startedAt, $0.id.uuidString) < ($1.startedAt, $1.id.uuidString) }
        if books.isEmpty {
            lines += ["Nothing was read here.", ""]
        }
        for reading in books {
            lines.append(Bible.book(id: reading.bookID)?.name ?? reading.bookID)
            if let finished = reading.finishedAt {
                lines.append("Read from \(day(reading.startedAt, timeZone)) to \(day(finished, timeZone)).")
            } else {
                lines.append("Begun \(day(reading.startedAt, timeZone)), and not finished.")
            }
            lines.append("")

            let theirNotes = notes
                .filter { $0.readingID == reading.id }
                .sorted { ($0.verse, $0.createdAt) < ($1.verse, $1.createdAt) }
            if !theirNotes.isEmpty {
                lines += ["Notes", ""]
                for note in theirNotes {
                    lines.append("\(note.verse.formatted), \(name(note.authorID)), \(day(note.createdAt, timeZone))")
                    lines.append(words(of: note))
                    lines.append("")
                }
            }

            let theirMarks = highlights
                .filter { $0.readingID == reading.id }
                .sorted { (start($0), $0.createdAt) < (start($1), $1.createdAt) }
            if !theirMarks.isEmpty {
                lines += ["Marks", ""]
                for mark in theirMarks {
                    lines.append("\(mark.range.formatted), \(name(mark.authorID))")
                }
                lines.append("")
            }

            // Only the cards the room opened: a sealed card's answers are
            // nobody's to read until everyone has answered (§4.6).
            let theirCards = cards
                .filter { $0.readingID == reading.id && $0.state == .open }
                .sorted { $0.chapter < $1.chapter }
            if !theirCards.isEmpty {
                lines += ["Cards", ""]
                for card in theirCards {
                    lines.append("Chapter \(card.chapter): \(card.question)")
                    let answers = card.answers
                        .map { (who: name($0.key), id: $0.key.uuidString, body: $0.value) }
                        .sorted { ($0.who, $0.id) < ($1.who, $1.id) }
                    for answer in answers {
                        lines.append("\(answer.who): \(answer.body)")
                    }
                    lines.append("")
                }
            }
        }
        return lines.joined(separator: "\n")
    }

    private static func start(_ mark: Highlight) -> VerseAddress {
        VerseAddress(bookID: mark.range.bookID, chapter: mark.range.chapter, verse: mark.range.startVerse)
    }

    private static func words(of note: Note) -> String {
        switch note.kind {
        case .written:
            return note.body ?? ""
        case .voice:
            if let transcript = note.transcript, !transcript.isEmpty {
                return "A voice note: \u{201C}\(transcript)\u{201D}"
            }
            return "A voice note."
        }
    }

    /// "3 September 2026", in the day's own zone, set by hand so both phones
    /// write the same.
    static func day(_ date: Date, _ timeZone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        let months = [
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ]
        return "\(parts.day ?? 1) \(months[(parts.month ?? 1) - 1]) \(parts.year ?? 0)"
    }
}
