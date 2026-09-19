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
    /// One per reading: where the room left off, offered and never applied
    /// (ledger A30).
    var ribbons: [Ribbon] = []
    var cards: [ReflectionCard] = []
    var invites: [Invite] = []
    var currentRoomID: UUID?
    var settings = AppSettings()
    var hasSeenMarginHint = false
    /// Whether the fire has ever been pulled up on this phone. The hearth
    /// says how to open the book until it has been done once (§6.1).
    var hasPulledTheFire = false
    /// Whether this device has been asked about notifications yet (§6.1).
    /// The app asks once, in context, and a question that comes back is
    /// worse than no question. Per install, never pushed: a fact about this
    /// phone, not about the person.
    var hasAskedAboutNotifications = false
    /// The tag of the portrait object each face on this device came from,
    /// so a conditional fetch can be told what it already has. Persisted:
    /// a relaunch must not re-download every face in the room.
    var portraitETags: [UUID: String] = [:]
    /// The newest row this device has already accounted for, for the
    /// purpose of notifications (S19). Nil means this device has never
    /// merged anything, and the first merge sets the watermark and posts
    /// nothing at all: a first sync on a new phone restores every room a
    /// person is in, which for a couple a year into this is several hundred
    /// notes, and several hundred notifications in one breath is the single
    /// most destructive failure this feature has. It advances on every
    /// merge whether or not anything was posted.
    var notifiedThrough: Date?
    /// Invites minted on this phone that the backend has not acknowledged.
    /// Persisted, because a link the sender has already pasted into a
    /// message thread must outlive a pull that cannot see it yet (A37).
    var invitesNotYetPushed: Set<UUID> = []
    /// Invites that actually left this phone — the share sheet was opened
    /// on them. Minting is not sending: S15's pending state is about a link
    /// that was handed out, and only this device knows it pressed share.
    var invitesHandedOut: Set<UUID> = []

    enum CodingKeys: String, CodingKey {
        case me, people, rooms, memberships, readings, notes, highlights, quietDays, positions, ribbons, cards, invites, currentRoomID, settings, hasSeenMarginHint, hasPulledTheFire, hasAskedAboutNotifications, portraitETags, notifiedThrough, invitesNotYetPushed, invitesHandedOut
    }

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        me = try container.decodeIfPresent(Person.self, forKey: .me)
        people = try container.decodeIfPresent([UUID: Person].self, forKey: .people) ?? [:]
        rooms = try container.decodeIfPresent([Room].self, forKey: .rooms) ?? []
        memberships = try container.decodeIfPresent([Membership].self, forKey: .memberships) ?? []
        readings = try container.decodeIfPresent([Reading].self, forKey: .readings) ?? []
        notes = try container.decodeIfPresent([Note].self, forKey: .notes) ?? []
        highlights = try container.decodeIfPresent([Highlight].self, forKey: .highlights) ?? []
        quietDays = try container.decodeIfPresent([QuietDay].self, forKey: .quietDays) ?? []
        positions = try container.decodeIfPresent([ReadingPosition].self, forKey: .positions) ?? []
        ribbons = try container.decodeIfPresent([Ribbon].self, forKey: .ribbons) ?? []
        cards = try container.decodeIfPresent([ReflectionCard].self, forKey: .cards) ?? []
        invites = try container.decodeIfPresent([Invite].self, forKey: .invites) ?? []
        currentRoomID = try container.decodeIfPresent(UUID.self, forKey: .currentRoomID)
        settings = try container.decodeIfPresent(AppSettings.self, forKey: .settings) ?? AppSettings()
        hasSeenMarginHint = try container.decodeIfPresent(Bool.self, forKey: .hasSeenMarginHint) ?? false
        hasPulledTheFire = try container.decodeIfPresent(Bool.self, forKey: .hasPulledTheFire) ?? false
        hasAskedAboutNotifications = try container.decodeIfPresent(Bool.self, forKey: .hasAskedAboutNotifications) ?? false
        portraitETags = try container.decodeIfPresent([UUID: String].self, forKey: .portraitETags) ?? [:]
        notifiedThrough = try container.decodeIfPresent(Date.self, forKey: .notifiedThrough)
        invitesNotYetPushed = try container.decodeIfPresent(Set<UUID>.self, forKey: .invitesNotYetPushed) ?? []
        invitesHandedOut = try container.decodeIfPresent(Set<UUID>.self, forKey: .invitesHandedOut) ?? []
    }
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
        if let state = try? Self.decoder.decode(AppState.self, from: data) { return state }
        return salvage(data)
    }

    /// A state file this build cannot read whole (ledger A43).
    ///
    /// The room's content is the backend's and comes back on the next pull;
    /// what would not come back is what only this phone knows — the
    /// reader's settings, the three "asked once" flags, and the invites that
    /// left this phone before the backend heard of them. Each is read on
    /// its own, so one unreadable field cannot take the rest with it, and
    /// the unreadable file is kept beside the new one rather than
    /// overwritten, so that whatever went wrong can be looked at.
    private func salvage(_ data: Data) -> AppState {
        keepTheUnreadableFile()
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return AppState()
        }
        func saved<T: Decodable>(_ key: String, _ fallback: T) -> T {
            guard let raw = object[key],
                  let bytes = try? JSONSerialization.data(withJSONObject: raw, options: [.fragmentsAllowed]),
                  let value = try? Self.decoder.decode(T.self, from: bytes)
            else { return fallback }
            return value
        }
        var state = AppState()
        state.settings = saved("settings", AppSettings())
        state.hasSeenMarginHint = saved("hasSeenMarginHint", false)
        state.hasPulledTheFire = saved("hasPulledTheFire", false)
        state.hasAskedAboutNotifications = saved("hasAskedAboutNotifications", false)
        state.invitesNotYetPushed = saved("invitesNotYetPushed", Set<UUID>())
        state.invitesHandedOut = saved("invitesHandedOut", Set<UUID>())
        // Deliberately not salvaged: `notifiedThrough`. Nil means this device
        // has never merged, which makes the next merge silent (S19) — and
        // after a reset that is exactly right, because everything is about
        // to arrive at once.
        return state
    }

    /// One copy, replaced each time, at `state.json.unreadable`.
    private func keepTheUnreadableFile() {
        let kept = url.deletingLastPathComponent().appendingPathComponent("state.json.unreadable")
        try? FileManager.default.removeItem(at: kept)
        try? FileManager.default.copyItem(at: url, to: kept)
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
