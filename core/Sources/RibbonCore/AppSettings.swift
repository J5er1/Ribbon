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
// cannot read (I42). They live here now, so a test can hold them to what
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
    /// Once the slider has been moved, the stop nearest its value, kept for
    /// a build from before the sliders (A69).
    public var lineSpacingStep: Int
    public var redLetter: Bool
    /// One per person, applying to every room (S19). Minutes from midnight,
    /// local. Default 10 p.m. – 6 a.m.
    public var quietHoursStart: Int
    public var quietHoursEnd: Int
    public var roomNotifications: [UUID: RoomNotificationPrefs]
    /// 0, 1, 2 → Lighter, Book, Heavier (A68, `PageType.weights`). A step
    /// rather than a weight, so the table can be retuned under it. Once the
    /// slider has been moved, the stop nearest its value, kept for a build
    /// from before the sliders (A69).
    public var weightStep: Int
    /// In prose, each numbered verse starts its own line (A68). Poetry,
    /// titles and stanza breaks are set as they always were.
    public var versePerLine: Bool
    /// Verse numbers in a stronger ink, nothing moved (A68).
    public var clearVerseNumbers: Bool
    /// The line spacing slider's value, in hundredths of the multiple
    /// (A69). Nil until a reader moves it: the page is then the step's.
    public var lineHeightHundredths: Int?
    /// The weight slider's value on Literata's axis, before Bold Text
    /// (A69). Nil until a reader moves it: the page is then the step's.
    public var pageWeight: Int?
    /// Room between the letters, in thousandths of an em (A69).
    public var letterSpacingThousandths: Int
    /// The margin asked for, in points a side (A69). The page gives less
    /// when the words would otherwise be too narrow.
    public var marginPoints: Int
    /// The page's face, by its id (A69). A String, never an enum, so a
    /// face a later build adds opens here in Literata rather than costing
    /// the field.
    public var typeface: String

    public init(
        scriptureSize: Double = PageType.defaultSize,
        lineSpacingStep: Int = PageType.defaultLineSpacingStep,
        redLetter: Bool = false,
        quietHoursStart: Int = 22 * 60,
        quietHoursEnd: Int = 6 * 60,
        roomNotifications: [UUID: RoomNotificationPrefs] = [:],
        weightStep: Int = PageType.defaultWeightStep,
        versePerLine: Bool = false,
        clearVerseNumbers: Bool = false,
        lineHeightHundredths: Int? = nil,
        pageWeight: Int? = nil,
        letterSpacingThousandths: Int = 0,
        marginPoints: Int = 0,
        typeface: String = PageFaces.literata.id
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
        self.lineHeightHundredths = lineHeightHundredths
        self.pageWeight = pageWeight
        self.letterSpacingThousandths = letterSpacingThousandths
        self.marginPoints = marginPoints
        self.typeface = typeface
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
        case lineHeightHundredths = "lineHeightHundredths"
        case pageWeight = "pageWeight"
        case letterSpacingThousandths = "letterSpacingThousandths"
        case marginPoints = "marginPoints"
        case typeface = "typeface"
    }

    /// A field missing from an older file, or one this build cannot read,
    /// takes its default and costs only itself (I42).
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
        lineHeightHundredths = read(.lineHeightHundredths, lineHeightHundredths)
        pageWeight = read(.pageWeight, pageWeight)
        letterSpacingThousandths = read(.letterSpacingThousandths, letterSpacingThousandths)
        marginPoints = read(.marginPoints, marginPoints)
        typeface = read(.typeface, typeface)
    }

    /// The slider's value if a reader has moved it, else the old step's
    /// (A69): a file from before the sliders opens on its own page.
    public var lineHeightMultiple: Double {
        PageType.lineHeightMultiple(
            hundredths: PageType.lineHeightHundredths(saved: lineHeightHundredths, legacyStep: lineSpacingStep))
    }

    /// The page's weight on Literata's axis, with the system's Bold Text
    /// folded in (A68): the slider's value if a reader has moved it, else
    /// the old step's (A69). A face draws it through `PageType.faceWeight`.
    public func weight(boldText: Bool) -> Int {
        PageType.weight(saved: pageWeight, legacyStep: weightStep) + (boldText ? PageType.boldTextWeight : 0)
    }

    public var verseNumberAlpha: Double {
        PageType.verseNumberAlpha(clear: clearVerseNumbers)
    }

    /// The page's face. One this build does not have is Literata.
    public var face: PageFace {
        PageFaces.face(id: typeface)
    }

    /// The room between letters, in ems, held to the scale.
    public var letterSpacingEm: Double {
        PageType.letterSpacingEm(letterSpacingThousandths)
    }

    /// The margin asked for, in points, held to the scale. What the page
    /// gives is `PageType.margin(requested:textWidth:size:)`.
    public var marginRequested: Double {
        Double(PageType.marginScale.held(marginPoints))
    }

    /// The line spacing slider's write: the value held to the scale, and
    /// the nearest old step beside it, so that a build from before the
    /// sliders opens on nearly the same page.
    public mutating func setLineHeight(_ hundredths: Int) {
        let held = PageType.lineHeightScale.held(hundredths)
        lineHeightHundredths = held
        lineSpacingStep = PageType.lineSpacingStep(forHundredths: held)
    }

    /// The weight slider's write, as for line spacing.
    public mutating func setWeight(_ weight: Int) {
        let held = PageType.weightScale.held(weight)
        pageWeight = held
        weightStep = PageType.weightStep(forWeight: held)
    }
}
