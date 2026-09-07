import Foundation
import RibbonCore

// Local persistence: one Codable state file, written atomically. Everything
// the app knows lives here first — offline is a first-class case (§6.10),
// and the room renders from cache instantly on launch with no skeleton
// (S01). Deliberately a plain file rather than a database: the data is
// small, the shape churns less, and a person's whole shelf can be exported
// by encoding this struct (§13).

/// Per-room notification switches (S19). Defaults per the build book: notes
/// on, cards on, "when they open the book" off — the killer feature for
/// couples and the creepiest one for a study, so opt-in per room is the
/// only defensible default.
struct RoomNotificationPrefs: Codable, Hashable {
    var notesLeft = true
    var cardsOpen = true
    var whenTheyOpenTheBook = false
    var thinkingOfYou = true
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

    var lineHeightMultiple: Double {
        [1.55, 1.72, 1.9][max(0, min(2, lineSpacingStep))]
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
    var currentRoomID: UUID?
    var settings = AppSettings()
    var hasSeenMarginHint = false
    /// The tag of the portrait object each face on this device came from,
    /// so a conditional fetch can be told what it already has. Persisted:
    /// a relaunch must not re-download every face in the room.
    var portraitETags: [UUID: String] = [:]
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
        return (try? Self.decoder.decode(AppState.self, from: data)) ?? AppState()
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
