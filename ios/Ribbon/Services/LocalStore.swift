import Foundation
import RibbonCore

// Local persistence: one Codable state file, written atomically. Everything
// the app knows lives here first — offline is a first-class case (§6.10),
// and the room renders from cache instantly on launch with no skeleton
// (S01). Deliberately a plain file rather than a database: the data is
// small, the shape churns less, and a person's whole shelf can be exported
// by encoding this struct (§13).
//
// The one rule that keeps this safe across releases: every stored struct
// below decodes each field with decodeIfPresent. Swift's synthesized
// Decodable throws on a missing key even when the property has a default,
// so a field added in a later release would otherwise turn every existing
// state.json into an empty AppState — and quietly wipe a person's notes,
// highlights and rooms on the next save. Add a field, add a line to the
// decoder, nothing else.

/// Per-room notification switches (S19). Defaults per the build book: notes
/// on, cards on, "when they open the book" off — the killer feature for
/// couples and the creepiest one for a study, so opt-in per room is the
/// only defensible default.
struct RoomNotificationPrefs: Codable, Hashable {
    var notesLeft = true
    var cardsOpen = true
    var whenTheyOpenTheBook = false
    var thinkingOfYou = true

    init() {}

    enum CodingKeys: String, CodingKey {
        case notesLeft, cardsOpen, whenTheyOpenTheBook, thinkingOfYou
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        notesLeft = try c.decodeIfPresent(Bool.self, forKey: .notesLeft) ?? true
        cardsOpen = try c.decodeIfPresent(Bool.self, forKey: .cardsOpen) ?? true
        whenTheyOpenTheBook = try c.decodeIfPresent(Bool.self, forKey: .whenTheyOpenTheBook) ?? false
        thinkingOfYou = try c.decodeIfPresent(Bool.self, forKey: .thinkingOfYou) ?? true
    }
}

struct AppSettings: Codable, Hashable {
    var scriptureSize: Double = 19
    /// 0, 1, 2 → line-height multiples 1.55, 1.72, 1.9 (S20's three steps).
    var lineSpacingStep: Int = 1
    var redLetter = false
    /// One per person, applying to every room (S19). Minutes from midnight,
    /// local. Default 10 p.m. – 6 a.m.
    var quietHoursStart = 22 * 60
    var quietHoursEnd = 6 * 60
    var roomNotifications: [UUID: RoomNotificationPrefs] = [:]
    /// S21 — keep the current translation whole on this device. Bundled
    /// translations are always whole; this governs licensed ones once they
    /// can be kept (their license decides today — deviations ledger).
    var keepEverythingOnDevice = true

    var lineHeightMultiple: Double {
        [1.55, 1.72, 1.9][max(0, min(2, lineSpacingStep))]
    }

    init() {}

    enum CodingKeys: String, CodingKey {
        case scriptureSize, lineSpacingStep, redLetter, quietHoursStart, quietHoursEnd
        case roomNotifications, keepEverythingOnDevice
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        scriptureSize = try c.decodeIfPresent(Double.self, forKey: .scriptureSize) ?? 19
        lineSpacingStep = try c.decodeIfPresent(Int.self, forKey: .lineSpacingStep) ?? 1
        redLetter = try c.decodeIfPresent(Bool.self, forKey: .redLetter) ?? false
        quietHoursStart = try c.decodeIfPresent(Int.self, forKey: .quietHoursStart) ?? 22 * 60
        quietHoursEnd = try c.decodeIfPresent(Int.self, forKey: .quietHoursEnd) ?? 6 * 60
        roomNotifications = try c.decodeIfPresent([UUID: RoomNotificationPrefs].self, forKey: .roomNotifications) ?? [:]
        keepEverythingOnDevice = try c.decodeIfPresent(Bool.self, forKey: .keepEverythingOnDevice) ?? true
    }
}

/// The whole of what the app remembers.
struct AppState: Codable {
    var me: Person?
    var people: [UUID: Person] = [:]
    var rooms: [Room] = []
    var memberships: [Membership] = []
    var readings: [Reading] = []
    var notes: [Note] = []
    var highlights: [Highlight] = []
    var quietDays: [QuietDay] = []
    var positions: [ReadingPosition] = []
    var invites: [Invite] = []
    /// Reflection cards (§4.6), one per reading and chapter, minted lazily
    /// when a member first reaches that passage end.
    var cards: [ReflectionCard] = []
    /// Open cards this person has seen open — the S01 row "The cards are
    /// open" rests once they have. Local only; a seen-set is not a count.
    var seenOpenCardIDs: Set<UUID> = []
    var currentRoomID: UUID?
    var settings = AppSettings()
    var hasSeenMarginHint = false
    /// The last ink used for a highlight (S06 — pre-selected so the common
    /// case is one tap). Remembered across launches.
    var lastUsedInk: Ink?
    /// The account this person adopted (§6.10). A different account signing
    /// in on this device is someone else, never a re-attribution of what
    /// this person left.
    var boundAccountID: UUID?
    /// Invites the backend has confirmed it holds. A link only works once
    /// it is registered (S16), so the share sheet waits for this.
    var registeredInviteIDs: Set<UUID> = []
    /// Departures made offline (§6.8): the membership delete is replayed
    /// before every pull, so a room left cannot be pulled back in.
    var pendingDepartures: [PendingDeparture] = []
    /// One last-read stamp per person per room (§13) — the whole of what
    /// the presence line remembers past the rolling fuel window, rendered
    /// as "Ruth read Tuesday" and never as a clock time.
    var lastReadAt: [UUID: [UUID: Date]] = [:]

    init() {}

    enum CodingKeys: String, CodingKey {
        case me, people, rooms, memberships, readings, notes, highlights, quietDays, positions
        case invites, cards, seenOpenCardIDs, currentRoomID, settings, hasSeenMarginHint
        case lastUsedInk, boundAccountID, registeredInviteIDs, pendingDepartures, lastReadAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        me = try c.decodeIfPresent(Person.self, forKey: .me)
        people = try c.decodeIfPresent([UUID: Person].self, forKey: .people) ?? [:]
        rooms = try c.decodeIfPresent([Room].self, forKey: .rooms) ?? []
        memberships = try c.decodeIfPresent([Membership].self, forKey: .memberships) ?? []
        readings = try c.decodeIfPresent([Reading].self, forKey: .readings) ?? []
        notes = try c.decodeIfPresent([Note].self, forKey: .notes) ?? []
        highlights = try c.decodeIfPresent([Highlight].self, forKey: .highlights) ?? []
        quietDays = try c.decodeIfPresent([QuietDay].self, forKey: .quietDays) ?? []
        positions = try c.decodeIfPresent([ReadingPosition].self, forKey: .positions) ?? []
        invites = try c.decodeIfPresent([Invite].self, forKey: .invites) ?? []
        cards = try c.decodeIfPresent([ReflectionCard].self, forKey: .cards) ?? []
        seenOpenCardIDs = try c.decodeIfPresent(Set<UUID>.self, forKey: .seenOpenCardIDs) ?? []
        currentRoomID = try c.decodeIfPresent(UUID.self, forKey: .currentRoomID)
        settings = try c.decodeIfPresent(AppSettings.self, forKey: .settings) ?? AppSettings()
        hasSeenMarginHint = try c.decodeIfPresent(Bool.self, forKey: .hasSeenMarginHint) ?? false
        lastUsedInk = try c.decodeIfPresent(Ink.self, forKey: .lastUsedInk)
        boundAccountID = try c.decodeIfPresent(UUID.self, forKey: .boundAccountID)
        registeredInviteIDs = try c.decodeIfPresent(Set<UUID>.self, forKey: .registeredInviteIDs) ?? []
        pendingDepartures = try c.decodeIfPresent([PendingDeparture].self, forKey: .pendingDepartures) ?? []
        lastReadAt = try c.decodeIfPresent([UUID: [UUID: Date]].self, forKey: .lastReadAt) ?? [:]
    }
}

/// A membership delete that hasn't reached the backend yet.
struct PendingDeparture: Codable, Hashable {
    var roomID: UUID
    var personID: UUID
}

actor LocalStore {
    private let url: URL
    private let portraitsURL: URL
    private let audioURL: URL

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Ribbon", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        url = base.appendingPathComponent("state.json")
        portraitsURL = base.appendingPathComponent("portraits", isDirectory: true)
        audioURL = base.appendingPathComponent("audio", isDirectory: true)
        try? FileManager.default.createDirectory(at: portraitsURL, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: audioURL, withIntermediateDirectories: true)
    }

    func load() -> AppState {
        guard let data = try? Data(contentsOf: url) else { return AppState() }
        do {
            return try Self.decoder.decode(AppState.self, from: data)
        } catch {
            // A state this build can't read is kept, not overwritten: the
            // next save would otherwise replace a person's whole shelf with
            // nothing. The copy sits beside the live file for a future
            // build (or a support conversation) to recover.
            let stamp = ISO8601DateFormatter().string(from: Date())
            let backup = url.deletingLastPathComponent()
                .appendingPathComponent("state-unreadable-\(stamp).json")
            try? data.write(to: backup, options: .atomic)
            return AppState()
        }
    }

    func save(_ state: AppState) {
        guard let data = try? Self.encoder.encode(state) else { return }
        try? data.write(to: url, options: .atomic)
    }

    // MARK: Files

    func portraitFileURL(_ name: String) -> URL {
        portraitsURL.appendingPathComponent(name)
    }

    func audioFileURL(_ name: String) -> URL {
        audioURL.appendingPathComponent(name)
    }

    func writePortrait(_ data: Data, personID: UUID) throws -> String {
        let name = "\(personID.uuidString).jpg"
        try data.write(to: portraitsURL.appendingPathComponent(name), options: .atomic)
        return name
    }

    /// Account deletion and "take them back" (§6.8): what a person recorded
    /// or uploaded leaves the disk with them.
    func removeAudio(named names: [String]) {
        for name in names {
            try? FileManager.default.removeItem(at: audioURL.appendingPathComponent(name))
        }
    }

    func removeAllFiles() {
        for directory in [portraitsURL, audioURL] {
            let files = (try? FileManager.default.contentsOfDirectory(
                at: directory, includingPropertiesForKeys: nil)) ?? []
            for file in files { try? FileManager.default.removeItem(at: file) }
        }
    }

    /// Free space on the device, for the one honest count in the product
    /// (S05 — megabytes measure a phone, not a person).
    nonisolated static func freeMegabytes() -> Int? {
        let home = URL(fileURLWithPath: NSHomeDirectory())
        guard
            let values = try? home.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]),
            let capacity = values.volumeAvailableCapacityForImportantUsage
        else { return nil }
        return Int(capacity / 1_000_000)
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
