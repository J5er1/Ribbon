import Foundation

// What's new (A61, I38) — one screen, once per release, on a plain launch.
//
// The build book says nothing may come between opening the app and reading:
// no "what's new" (§6.2), no tour (§6.1). The owner asked for one anyway —
// "just tell people about it when they open on a new version" — and chose a
// screen on launch over a quiet row in the room. This is that reversal, kept
// as small as it can be and still be true to what was asked. Everything else
// the rule protects stays: one screen, the latest release only and never a
// backlog, one tap from the room, and never in the way of a launch that came
// from something the person tapped. Every release a reader can notice adds
// its entry here, with its pictures and its words on both phones; and the
// whole list can be read again, newest first, from You (A65).
//
// This file only decides. Whether this phone has seen a release is kept on
// the phone, beside the other things it has been told once: it is about this
// phone's build, not about the person, so it is never synced.

/// One thing a release brings, in the order the screen shows them. The raw
/// value is the name the screen's copy and vignette are found by.
public enum WhatsNewItem: String, Codable, Hashable, Sendable, CaseIterable {
    /// The Hebrew and Greek under every verse.
    case original
    /// Everybody reads their own version again.
    case ownVersion
    /// Following lands on the same words.
    case followingWords
    /// Following stays with them: the screen stays on, and a dropped
    /// connection or a second phone no longer loses them (A64).
    case followingStays
    /// Selecting is the phone's own; a verse number takes the verse (A62).
    case nativeSelection
    /// "In this room" gathers the room's readings, however many (A62).
    case roomGroups
    /// The New King James as printed: LORD, and its copyright line (A64).
    case lordReadsLord
    /// You opens like the front of a Bible: your name, your face, a ribbon
    /// for every room you read in, and a colophon at the foot (A67).
    case flyleaf
    /// Every book you have finished, with whoever, on one shelf (A67).
    case yourShelf
    /// Each version is shown in its own words, and a ribbon marks yours (A67).
    case versionsByReading
    /// The page, the way you read it: a new line for every verse, a lighter
    /// or heavier letter, clearer verse numbers, and a larger size (A68).
    case yourPage
    /// The notification switches say who, in the words the phone will use (A67).
    case notificationsByName
    /// Quiet hours are one band, the night, rather than two wheels (A67).
    case quietHoursNight
}

/// A release worth telling someone about. The id is a name, not a version
/// number — a build with nothing new to say keeps the last one's, and nobody
/// sees the screen twice for it.
public struct WhatsNewRelease: Hashable, Sendable {
    public var id: String
    /// The day it was released, `yyyy-MM-dd`: the heading it is listed
    /// under when the list is read again.
    public var released: String
    public var items: [WhatsNewItem]

    public init(id: String, released: String = "", items: [WhatsNewItem]) {
        self.id = id
        self.released = released
        self.items = items
    }
}

public enum WhatsNew {
    /// Newest first. Only the first is ever shown on a launch: someone who
    /// skipped three releases hears about the one they are on, and the rest
    /// are simply how the app is now — there for the reading, from You.
    public static let releases: [WhatsNewRelease] = [
        WhatsNewRelease(
            id: "2026-10-flyleaf", released: "2026-10-07",
            items: [.flyleaf, .yourShelf, .versionsByReading, .yourPage, .notificationsByName, .quietHoursNight]),
        WhatsNewRelease(
            id: "2026-10-following", released: "2026-10-06",
            items: [.followingStays, .nativeSelection, .roomGroups, .lordReadsLord]),
        WhatsNewRelease(
            id: "2026-10-original", released: "2026-10-04",
            items: [.original, .ownVersion, .followingWords]),
    ]

    /// The release to show on this launch, or nil.
    ///
    /// - `lastSeen`: the release this phone last recorded as seen, if any.
    /// - `hasHistory`: this phone already had Ribbon on it before this launch
    ///   — someone has been through the four questions here. With no
    ///   `lastSeen`, that is a person updating from a build before this
    ///   screen existed, and they are told; without it, a fresh install, and
    ///   a fresh install gets the four questions, never a tour.
    /// - `plainLaunch`: the app was opened, not sent somewhere — no
    ///   notification, invite, link, widget or Live Activity behind it. A
    ///   launch that came from a tap goes where the tap meant, and the screen
    ///   waits for the next plain one.
    public static func toShow(
        lastSeen: String?, hasHistory: Bool, plainLaunch: Bool,
        releases: [WhatsNewRelease] = WhatsNew.releases
    ) -> WhatsNewRelease? {
        guard let latest = releases.first else { return nil }
        if lastSeen == latest.id { return nil }
        if lastSeen == nil && !hasHistory { return nil }
        if !plainLaunch { return nil }
        // An older id, or one no longer in the list, is still not this one.
        return latest
    }

    /// What to record as seen after this launch's decision (nil = leave as
    /// is). `shown` is whether the screen was put up and then left — every
    /// way out of it counts, the button, back, or a swipe down.
    ///
    /// A fresh install records the latest straight away, so the first plain
    /// launch after onboarding does not tell someone about what they have
    /// only just met. A launch from a tap records nothing, so it is still
    /// owed. Anything else records the latest once it has been shown.
    public static func seenAfter(
        lastSeen: String?, hasHistory: Bool, plainLaunch: Bool, shown: Bool,
        releases: [WhatsNewRelease] = WhatsNew.releases
    ) -> String? {
        guard let latest = releases.first else { return nil }
        if lastSeen == latest.id { return nil }
        if lastSeen == nil && !hasHistory { return latest.id }
        if !plainLaunch { return nil }
        return shown ? latest.id : nil
    }
}
