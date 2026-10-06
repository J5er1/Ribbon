import XCTest
@testable import RibbonCore

// What's new (A61): who sees the screen, and what the phone records after.
final class WhatsNewTests: XCTestCase {
    let newer = WhatsNewRelease(id: "2027-03-later", items: [.followingWords])
    let older = WhatsNewRelease(id: "2026-10-original", items: [.original, .ownVersion, .followingWords])
    /// Whatever release is newest: the rules hold for every one of them.
    let latest = WhatsNew.releases[0]

    // MARK: Today's release

    func testTodaysRelease() {
        XCTAssertEqual(WhatsNew.releases.first?.id, "2026-10-flyleaf")
        XCTAssertEqual(
            WhatsNew.releases.first?.items,
            [.flyleaf, .yourShelf, .versionsByReading, .notificationsByName, .quietHoursNight])
    }

    // Read again from You (A65): every release, newest first, each dated,
    // and every thing new told in exactly one of them.
    func testTheListReadsNewestFirstAndTellsEachThingOnce() {
        let ids = WhatsNew.releases.map(\.id)
        XCTAssertEqual(ids, ["2026-10-flyleaf", "2026-10-following", "2026-10-original"])
        XCTAssertEqual(Set(ids).count, ids.count)
        let days = WhatsNew.releases.map(\.released)
        XCTAssertTrue(days.allSatisfy { $0.count == 10 })
        XCTAssertEqual(days, days.sorted(by: >))
        let told = WhatsNew.releases.flatMap(\.items)
        XCTAssertEqual(Set(told), Set(WhatsNewItem.allCases))
        XCTAssertEqual(told.count, WhatsNewItem.allCases.count)
    }

    // Someone who saw an earlier release's screen sees this one's, once —
    // and someone two behind hears about this one, not the backlog.
    func testAnUpdaterWhoSawAnEarlierOneSeesThisOne() {
        XCTAssertEqual(
            WhatsNew.toShow(lastSeen: "2026-10-following", hasHistory: true, plainLaunch: true)?.id,
            "2026-10-flyleaf")
        XCTAssertEqual(
            WhatsNew.toShow(lastSeen: "2026-10-original", hasHistory: true, plainLaunch: true)?.id,
            "2026-10-flyleaf")
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: "2026-10-following", hasHistory: true, plainLaunch: true, shown: true),
            "2026-10-flyleaf")
        XCTAssertNil(WhatsNew.toShow(lastSeen: "2026-10-flyleaf", hasHistory: true, plainLaunch: true))
    }

    func testItemRawValues() throws {
        XCTAssertEqual(WhatsNewItem.original.rawValue, "original")
        XCTAssertEqual(WhatsNewItem.ownVersion.rawValue, "ownVersion")
        XCTAssertEqual(WhatsNewItem.followingWords.rawValue, "followingWords")
        XCTAssertEqual(WhatsNewItem.followingStays.rawValue, "followingStays")
        XCTAssertEqual(WhatsNewItem.nativeSelection.rawValue, "nativeSelection")
        XCTAssertEqual(WhatsNewItem.roomGroups.rawValue, "roomGroups")
        XCTAssertEqual(WhatsNewItem.lordReadsLord.rawValue, "lordReadsLord")
        XCTAssertEqual(WhatsNewItem.flyleaf.rawValue, "flyleaf")
        XCTAssertEqual(WhatsNewItem.yourShelf.rawValue, "yourShelf")
        XCTAssertEqual(WhatsNewItem.versionsByReading.rawValue, "versionsByReading")
        XCTAssertEqual(WhatsNewItem.notificationsByName.rawValue, "notificationsByName")
        XCTAssertEqual(WhatsNewItem.quietHoursNight.rawValue, "quietHoursNight")
        let json = try JSONEncoder().encode([WhatsNewItem.original, .ownVersion, .followingWords])
        XCTAssertEqual(String(decoding: json, as: UTF8.self), #"["original","ownVersion","followingWords"]"#)
    }

    // MARK: The rules

    func testNoReleasesShowsNothing() {
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true, releases: []))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: true, releases: []))
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: false, plainLaunch: true, releases: []))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: false, plainLaunch: true, shown: false, releases: []))
    }

    func testFreshInstallRecordsAndSkips() {
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: false, plainLaunch: true))
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: false, plainLaunch: true, shown: false),
            latest.id)
        // A fresh install opened from an invite is still a fresh install.
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: false, plainLaunch: false))
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: false, plainLaunch: false, shown: false),
            latest.id)
        // And the launch after onboarding has nothing to tell.
        XCTAssertNil(WhatsNew.toShow(lastSeen: latest.id, hasHistory: true, plainLaunch: true))
    }

    func testUpdateFromPreFeatureBuildShows() {
        XCTAssertEqual(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true), latest)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: true),
            latest.id)
    }

    func testSameReleaseTwiceShowsOnce() {
        let first = WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true)
        XCTAssertEqual(first, latest)
        let recorded = WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: first != nil)
        XCTAssertEqual(recorded, latest.id)
        XCTAssertNil(WhatsNew.toShow(lastSeen: recorded, hasHistory: true, plainLaunch: true))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: recorded, hasHistory: true, plainLaunch: true, shown: false))
    }

    func testLinkLaunchSkipsAndRecordsNothingThenNextPlainLaunchShows() {
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: false))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: false, shown: false))
        XCTAssertEqual(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true), latest)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: true),
            latest.id)
    }

    func testOlderLastSeenShowsLatestOnly() {
        let releases = [newer, older]
        let shown = WhatsNew.toShow(lastSeen: "2026-10-original", hasHistory: true, plainLaunch: true, releases: releases)
        XCTAssertEqual(shown, newer)
        XCTAssertEqual(shown?.items, [.followingWords])
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: "2026-10-original", hasHistory: true, plainLaunch: true, shown: true, releases: releases),
            "2027-03-later")
        // Someone two releases behind hears about the latest, not the backlog.
        XCTAssertEqual(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true, releases: releases), newer)
    }

    func testUnknownLastSeenShowsLatest() {
        XCTAssertEqual(WhatsNew.toShow(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true), latest)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true, shown: true),
            latest.id)
    }

    func testNotShownRecordsNothing() {
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: false))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true, shown: false))
    }
}
