import Foundation

// Translations. The two launch translations are public domain and ship in
// the app, whole. Licensed translations (NKJV first, two more undecided)
// arrive through API.Bible — build book horizon §15 / open question §16.8,
// decided: they stream through the room's own proxy and cache only the
// book being read, because their licenses forbid shipping the text.
//
// Translation is a personal setting, not a room setting (§2.6): notes pin
// to verse addresses, so a note lands on the same verse whichever
// translation each person reads.

/// A translation's identity — a stable, lowercase key. Extensible: stored
/// state and the database carry the raw string, so adding a translation
/// never migrates anything.
public struct TranslationID: RawRepresentable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }

    public static let bsb = TranslationID(rawValue: "bsb")
    public static let web = TranslationID(rawValue: "web")
    public static let nkjv = TranslationID(rawValue: "nkjv")
}

extension TranslationID: Codable {
    public init(from decoder: Decoder) throws {
        rawValue = try decoder.singleValueContainer().decode(String.self)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

public extension TranslationID {
    /// Name as it appears in S20: "Berean Standard", "World English".
    var displayName: String {
        TranslationRegistry.translation(for: self)?.displayName ?? rawValue.uppercased()
    }

    var fullName: String {
        TranslationRegistry.translation(for: self)?.fullName ?? rawValue.uppercased()
    }
}

/// Everything the app knows about one translation.
public struct Translation: Hashable, Identifiable, Sendable {
    public enum Source: Hashable, Sendable {
        /// Public domain, converted by tools/usfx_to_json.py, shipped in
        /// the bundle, whole. Offline is first-class.
        case bundled
        /// Licensed, served by API.Bible through the backend proxy (the
        /// key never ships in the app). `bibleID` is API.Bible's id for
        /// the exact edition; empty until the license lands.
        case apiBible(bibleID: String)
    }

    public var id: TranslationID
    public var displayName: String
    public var fullName: String
    public var source: Source
    /// Whether the text data carries words-of-Jesus markup (S20's
    /// red-letter setting).
    public var redLetter: Bool

    public init(id: TranslationID, displayName: String, fullName: String, source: Source, redLetter: Bool) {
        self.id = id
        self.displayName = displayName
        self.fullName = fullName
        self.source = source
        self.redLetter = redLetter
    }

    public var isBundled: Bool {
        if case .bundled = source { return true }
        return false
    }

    /// The proxy can only serve an edition that has been named.
    public var isConfigured: Bool {
        switch source {
        case .bundled:
            return true
        case .apiBible(let bibleID):
            return !bibleID.isEmpty
        }
    }
}

public enum TranslationRegistry {
    public static let bsb = Translation(
        id: .bsb, displayName: "Berean Standard", fullName: "Berean Standard Bible",
        source: .bundled, redLetter: false)

    public static let web = Translation(
        id: .web, displayName: "World English", fullName: "World English Bible",
        source: .bundled, redLetter: true)

    /// NKJV via API.Bible. The bibleID is filled in when Thomas Nelson's
    /// license is approved on the API.Bible account; until then the
    /// translation is registered but unavailable, and never shown.
    public static let nkjv = Translation(
        id: .nkjv, displayName: "New King James", fullName: "New King James Version",
        source: .apiBible(bibleID: ""), redLetter: false)

    /// Shipped in the app, whole.
    public static let bundled: [Translation] = [bsb, web]

    /// Licensed, streamed. Two more slots are planned and undecided —
    /// they join this list the day they are chosen.
    public static let licensed: [Translation] = [nkjv]

    public static let all: [Translation] = bundled + licensed

    public static func translation(for id: TranslationID) -> Translation? {
        all.first { $0.id == id }
    }

    public static func isBundled(_ id: TranslationID) -> Bool {
        translation(for: id)?.isBundled ?? false
    }
}
