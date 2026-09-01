import Foundation

// The object model, straight from build book §03. Three relationships carry
// the whole product:
//   1. Ink lives on membership — a person has a color in a room, not a color.
//   2. Position is per-person, per-reading — there is no shared "where we are."
//   3. Notes belong to the reading, not the person — they were left for you.

// TranslationID and the translation registry live in Translations.swift.

/// An account. Portrait is close to required — presence is faces. Skipping
/// gives a monogram in their ink.
public struct Person: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var name: String
    /// File name of the portrait image in local storage, or a storage path
    /// remotely. Nil renders a monogram in the person's ink.
    public var portraitPath: String?
    public var translation: TranslationID

    public init(id: UUID = UUID(), name: String, portraitPath: String? = nil, translation: TranslationID = .bsb) {
        self.id = id
        self.name = name
        self.portraitPath = portraitPath
        self.translation = translation
    }

    /// The monogram shown when there is no portrait.
    public var monogram: String {
        String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
    }
}

/// 2–6 people. The app's root. A person may hold several rooms; they never
/// interact — nothing crosses between them (§2.4).
public struct Room: Codable, Hashable, Identifiable, Sendable {
    public static let capacity = 6

    public var id: UUID
    /// Optional. When empty, the interface derives a name from members'
    /// first names.
    public var name: String?
    public var createdAt: Date
    /// Subscription lapsed → the room is paused: presence off, new notes
    /// off. Reading and everything already left stays, forever (§2.5).
    public var isPaused: Bool

    public init(id: UUID = UUID(), name: String? = nil, createdAt: Date, isPaused: Bool = false) {
        self.id = id
        self.name = name
        self.createdAt = createdAt
        self.isPaused = isPaused
    }
}

/// Person × Room. Ink lives here, not on Person — you're teal in one room
/// and ochre in another.
public struct Membership: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var roomID: UUID
    public var personID: UUID
    /// Nil while the room is two people (both draw from the whole palette
    /// freely, per highlight) or while an ink pick is still waiting (§4.5).
    public var ink: Ink?
    public var joinedAt: Date

    public init(id: UUID = UUID(), roomID: UUID, personID: UUID, ink: Ink? = nil, joinedAt: Date) {
        self.id = id
        self.roomID = roomID
        self.personID = personID
        self.ink = ink
        self.joinedAt = joinedAt
    }
}

/// A book, in a room. The active unit. A room has one open reading at a time.
public struct Reading: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var roomID: UUID
    public var bookID: String
    public var startedAt: Date
    /// Set when the book is finished; a finished reading is an ember.
    public var finishedAt: Date?
    /// The campfire. `handiwork` is the general mechanic (§2.8) — the code
    /// says handiwork everywhere and fire only in the campfire's own module.
    public var handiwork: Handiwork

    public init(id: UUID = UUID(), roomID: UUID, bookID: String, startedAt: Date, finishedAt: Date? = nil, handiwork: Handiwork) {
        self.id = id
        self.roomID = roomID
        self.bookID = bookID
        self.startedAt = startedAt
        self.finishedAt = finishedAt
        self.handiwork = handiwork
    }

    public var isFinished: Bool { finishedAt != nil }
}

/// What kind a note is: a voice memo or a written thought.
public enum NoteKind: String, Codable, Hashable, Sendable {
    case voice
    case written
}

/// How a fresh voice note's transcript is doing (S04). Transcripts are not
/// optional: they are how the deaf read this app, and how anyone finds a
/// note again six months later (§11).
public enum TranscriptState: String, Codable, Hashable, Sendable {
    case pending    // "Transcript coming"
    case ready
    case failed     // "No transcript for this one." + Try again
}

/// A voice memo or written thought pinned to a verse. Always "left," never
/// posted, shared, or sent — being found later is the emotional beat.
public struct Note: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var readingID: UUID
    public var authorID: UUID
    public var verse: VerseAddress
    public var kind: NoteKind
    /// Written body, for written notes.
    public var body: String?
    /// Local file name / remote storage path of the audio, for voice notes.
    public var audioPath: String?
    /// Normalized waveform peaks (0...1) drawn in the author's ink. The
    /// waveform is the visual; no duration is ever displayed — a duration
    /// is a count and it makes people self-conscious about how long they
    /// talked (S04).
    public var waveform: [Float]?
    public var transcript: String?
    public var transcriptState: TranscriptState?
    public var createdAt: Date
    /// Who has found this note. The record stays; the author is never told
    /// (no read receipts — §6.3).
    public var foundBy: Set<UUID>
    /// Composed offline and not yet landed: the mark renders as a hairline
    /// outline in the author's ink until it lands (§4.4). No spinner, no
    /// toast, no retry button.
    public var isPending: Bool

    public init(
        id: UUID = UUID(), readingID: UUID, authorID: UUID, verse: VerseAddress,
        kind: NoteKind, body: String? = nil, audioPath: String? = nil,
        waveform: [Float]? = nil, transcript: String? = nil,
        transcriptState: TranscriptState? = nil, createdAt: Date,
        foundBy: Set<UUID> = [], isPending: Bool = false
    ) {
        self.id = id
        self.readingID = readingID
        self.authorID = authorID
        self.verse = verse
        self.kind = kind
        self.body = body
        self.audioPath = audioPath
        self.waveform = waveform
        self.transcript = transcript
        self.transcriptState = transcriptState
        self.createdAt = createdAt
        self.foundBy = foundBy
        self.isPending = isPending
    }
}

/// Verse range + ink. Ink semantics change at three people (§4.5). Existing
/// highlights are never recolored. A highlight is a mark on a shared page,
/// not a possession — when someone leaves, their highlights stay (§6.8).
public struct Highlight: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var readingID: UUID
    public var authorID: UUID
    public var range: VerseRange
    public var ink: Ink
    public var createdAt: Date

    public init(id: UUID = UUID(), readingID: UUID, authorID: UUID, range: VerseRange, ink: Ink, createdAt: Date) {
        self.id = id
        self.readingID = readingID
        self.authorID = authorID
        self.range = range
        self.ink = ink
        self.createdAt = createdAt
    }
}

/// A reflection card's life (§4.6). Sealed cards never name who hasn't
/// answered, never show how many have, never expire, never nag. Any member
/// may set a sealed card down for the room; it leaves without ceremony.
public enum CardState: String, Codable, Hashable, Sendable {
    case sealed
    case open
    case setDown
}

/// A reflection. Opens only when every member has answered.
/// (Cards ship in phase two — the model exists now because the object model
/// and the schema should not churn when they arrive.)
public struct ReflectionCard: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var readingID: UUID
    public var chapter: Int
    public var question: String
    /// One answer per member. Answers are visible only to their author
    /// until the card opens.
    public var answers: [UUID: String]
    public var state: CardState
    public var openedAt: Date?

    public init(id: UUID = UUID(), readingID: UUID, chapter: Int, question: String, answers: [UUID: String] = [:], state: CardState = .sealed, openedAt: Date? = nil) {
        self.id = id
        self.readingID = readingID
        self.chapter = chapter
        self.question = question
        self.answers = answers
        self.state = state
        self.openedAt = openedAt
    }
}

/// A marked day — the grace mechanic (§4.7). Declared by a person, never
/// inferred by the app. Marked in the marker's local day. No limit, no
/// ledger: a counted grace is not grace.
public struct QuietDay: Codable, Hashable, Identifiable, Sendable {
    public var id: UUID
    public var roomID: UUID
    public var personID: UUID
    /// The marker's local calendar day, "yyyy-MM-dd".
    public var localDate: String
    /// The marker's time zone identifier at the moment of marking, so the
    /// banked interval is the marker's day everywhere (§4.9 — there is no
    /// midnight, anywhere, for anyone).
    public var timeZoneID: String
    public var markedAt: Date

    public init(id: UUID = UUID(), roomID: UUID, personID: UUID, localDate: String, timeZoneID: String, markedAt: Date) {
        self.id = id
        self.roomID = roomID
        self.personID = personID
        self.localDate = localDate
        self.timeZoneID = timeZoneID
        self.markedAt = markedAt
    }

    public init(id: UUID = UUID(), roomID: UUID, personID: UUID, markedAt: Date, timeZone: TimeZone, calendar: Calendar = Calendar(identifier: .gregorian)) {
        var cal = calendar
        cal.timeZone = timeZone
        let c = cal.dateComponents([.year, .month, .day], from: markedAt)
        let date = String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
        self.init(id: id, roomID: roomID, personID: personID, localDate: date, timeZoneID: timeZone.identifier, markedAt: markedAt)
    }

    /// The interval during which this quiet day banks the fire: the marker's
    /// local day, midnight to midnight in their zone.
    public var bankedInterval: DateInterval? {
        guard let zone = TimeZone(identifier: timeZoneID) else { return nil }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let parts = localDate.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        var comps = DateComponents()
        comps.year = parts[0]; comps.month = parts[1]; comps.day = parts[2]
        guard let start = cal.date(from: comps),
              let end = cal.date(byAdding: .day, value: 1, to: start) else { return nil }
        return DateInterval(start: start, end: end)
    }
}

/// An invitation into a room. The link is the whole mechanism (S15): no
/// contact-list permission, no email field, no invite-by-username.
public struct Invite: Codable, Hashable, Identifiable, Sendable {
    public static let lifetimeDays = 30

    public var id: UUID
    public var roomID: UUID
    public var createdBy: UUID
    public var createdAt: Date
    public var expiresAt: Date

    public init(id: UUID = UUID(), roomID: UUID, createdBy: UUID, createdAt: Date) {
        self.id = id
        self.roomID = roomID
        self.createdBy = createdBy
        self.createdAt = createdAt
        self.expiresAt = createdAt.addingTimeInterval(TimeInterval(Invite.lifetimeDays) * 24 * 3600)
    }

    public func url() -> URL {
        URL(string: "https://ribbon.bible/i/\(id.uuidString.lowercased())")!
    }
}

/// Where a person is in a reading. Never shared as "where we are" — two
/// people in Mark are simply in two places in Mark, and that is not a
/// problem to be solved.
public struct ReadingPosition: Codable, Hashable, Sendable {
    public var readingID: UUID
    public var personID: UUID
    public var chapter: Int
    public var verse: Int
    public var updatedAt: Date

    public init(readingID: UUID, personID: UUID, chapter: Int, verse: Int, updatedAt: Date) {
        self.readingID = readingID
        self.personID = personID
        self.chapter = chapter
        self.verse = verse
        self.updatedAt = updatedAt
    }
}
