import Foundation

// The reader's own settings (S19, S20), as this phone keeps them in its
// state file. They are only ever written here, never synced (A44), so a
// file this build cannot read costs a person their size, spacing, quiet
// hours and every room's switches, and nothing on the backend brings them
// back.
//
// They lived in the app and decoded with the synthesized decoder, which
// rejects an object missing any key. A field added to them would have
// turned every saved state from the build before into a file this build
// cannot read (I41). They live here now, so a test can hold them to what
// the last release wrote, and they decode by hand: every field is read on
// its own, and one that is missing or unreadable takes its default and
// costs nothing else. The encoder is still the generated one.

/// Per-room notification switches (S19). Defaults per the build book: notes
/// on, cards on, "when they open the book" off — the killer feature for
/// couples and the creepiest one for a study, so opt-in per room is the
/// only defensible default.
public struct RoomNotificationPrefs: Codable, Hashable, Sendable {
    public var notesLeft: Bool
    public var cardsOpen: Bool
    public var whenTheyOpenTheBook: Bool
    public var thinkingOfYou: Bool

    public init(
        notesLeft: Bool = true, cardsOpen: Bool = true,
        whenTheyOpenTheBook: Bool = false, thinkingOfYou: Bool = true
    ) {
        self.notesLeft = notesLeft
        self.cardsOpen = cardsOpen
        self.whenTheyOpenTheBook = whenTheyOpenTheBook
        self.thinkingOfYou = thinkingOfYou
    }

    /// Spelled out: these are the names in every file already saved. A
    /// property may be renamed; its key may not.
    enum CodingKeys: String, CodingKey {
        case notesLeft = "notesLeft"
        case cardsOpen = "cardsOpen"
        case whenTheyOpenTheBook = "whenTheyOpenTheBook"
        case thinkingOfYou = "thinkingOfYou"
    }

    /// A room's switches from before a switch existed keep their own, and
    /// the new one starts at its default.
    public init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func read<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T {
            (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback
        }
        notesLeft = read(.notesLeft, notesLeft)
        cardsOpen = read(.cardsOpen, cardsOpen)
        whenTheyOpenTheBook = read(.whenTheyOpenTheBook, whenTheyOpenTheBook)
        thinkingOfYou = read(.thinkingOfYou, thinkingOfYou)
    }
}

public struct AppSettings: Codable, Hashable, Sendable {
    /// Scripture's size in points, before Dynamic Type (`PageType.sizeRange`).
    public var scriptureSize: Double
    /// 0, 1, 2 → Close, Book, Open (S20's three steps, `PageType.lineHeightMultiples`).
    public var lineSpacingStep: Int
    public var redLetter: Bool
    /// One per person, applying to every room (S19). Minutes from midnight,
    /// local. Default 10 p.m. – 6 a.m.
    public var quietHoursStart: Int
    public var quietHoursEnd: Int
    public var roomNotifications: [UUID: RoomNotificationPrefs]
    /// 0, 1, 2 → Lighter, Book, Heavier (A68, `PageType.weights`). A step
    /// rather than a weight, so the table can be retuned under it.
    public var weightStep: Int
    /// In prose, each numbered verse starts its own line (A68). Poetry,
    /// titles and stanza breaks are set as they always were.
    public var versePerLine: Bool
    /// Verse numbers in a stronger ink, nothing moved (A68).
    public var clearVerseNumbers: Bool

    public init(
        scriptureSize: Double = PageType.defaultSize,
        lineSpacingStep: Int = PageType.defaultLineSpacingStep,
        redLetter: Bool = false,
        quietHoursStart: Int = 22 * 60,
        quietHoursEnd: Int = 6 * 60,
        roomNotifications: [UUID: RoomNotificationPrefs] = [:],
        weightStep: Int = PageType.defaultWeightStep,
        versePerLine: Bool = false,
        clearVerseNumbers: Bool = false
    ) {
        self.scriptureSize = scriptureSize
        self.lineSpacingStep = lineSpacingStep
        self.redLetter = redLetter
        self.quietHoursStart = quietHoursStart
        self.quietHoursEnd = quietHoursEnd
        self.roomNotifications = roomNotifications
        self.weightStep = weightStep
        self.versePerLine = versePerLine
        self.clearVerseNumbers = clearVerseNumbers
    }

    /// Spelled out: these are the names in every file already saved. A
    /// property may be renamed; its key may not.
    enum CodingKeys: String, CodingKey {
        case scriptureSize = "scriptureSize"
        case lineSpacingStep = "lineSpacingStep"
        case redLetter = "redLetter"
        case quietHoursStart = "quietHoursStart"
        case quietHoursEnd = "quietHoursEnd"
        case roomNotifications = "roomNotifications"
        case weightStep = "weightStep"
        case versePerLine = "versePerLine"
        case clearVerseNumbers = "clearVerseNumbers"
    }

    /// A field missing from an older file, or one this build cannot read,
    /// takes its default and costs only itself (I41).
    public init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func read<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T {
            (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback
        }
        scriptureSize = read(.scriptureSize, scriptureSize)
        lineSpacingStep = read(.lineSpacingStep, lineSpacingStep)
        redLetter = read(.redLetter, redLetter)
        quietHoursStart = read(.quietHoursStart, quietHoursStart)
        quietHoursEnd = read(.quietHoursEnd, quietHoursEnd)
        roomNotifications = read(.roomNotifications, roomNotifications)
        weightStep = read(.weightStep, weightStep)
        versePerLine = read(.versePerLine, versePerLine)
        clearVerseNumbers = read(.clearVerseNumbers, clearVerseNumbers)
    }

    public var lineHeightMultiple: Double {
        PageType.lineHeightMultiple(step: lineSpacingStep)
    }

    /// The page's weight on Literata's axis, with the system's Bold Text
    /// folded in (A68).
    public func weight(boldText: Bool) -> Int {
        PageType.weight(step: weightStep, boldText: boldText)
    }

    public var verseNumberAlpha: Double {
        PageType.verseNumberAlpha(clear: clearVerseNumbers)
    }
}
