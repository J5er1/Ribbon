import XCTest
@testable import RibbonCore

// What's new (A61): who sees the screen, and what the phone records after.
final class WhatsNewTests: XCTestCase {
    let newer = WhatsNewRelease(id: "2027-03-later", items: [.followingWords])
    let older = WhatsNewRelease(id: "2026-10-original", items: [.original, .ownVersion, .followingWords])

    // MARK: Today's release

    func testTodaysRelease() {
        XCTAssertEqual(WhatsNew.releases.first?.id, "2026-10-original")
        XCTAssertEqual(WhatsNew.releases.first?.items, [.original, .ownVersion, .followingWords])
    }

    func testItemRawValues() throws {
        XCTAssertEqual(WhatsNewItem.original.rawValue, "original")
        XCTAssertEqual(WhatsNewItem.ownVersion.rawValue, "ownVersion")
        XCTAssertEqual(WhatsNewItem.followingWords.rawValue, "followingWords")
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
            "2026-10-original")
        // A fresh install opened from an invite is still a fresh install.
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: false, plainLaunch: false))
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: false, plainLaunch: false, shown: false),
            "2026-10-original")
        // And the launch after onboarding has nothing to tell.
        XCTAssertNil(WhatsNew.toShow(lastSeen: "2026-10-original", hasHistory: true, plainLaunch: true))
    }

    func testUpdateFromPreFeatureBuildShows() {
        XCTAssertEqual(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true), older)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: true),
            "2026-10-original")
    }

    func testSameReleaseTwiceShowsOnce() {
        let first = WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true)
        XCTAssertEqual(first, older)
        let recorded = WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: first != nil)
        XCTAssertEqual(recorded, "2026-10-original")
        XCTAssertNil(WhatsNew.toShow(lastSeen: recorded, hasHistory: true, plainLaunch: true))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: recorded, hasHistory: true, plainLaunch: true, shown: false))
    }

    func testLinkLaunchSkipsAndRecordsNothingThenNextPlainLaunchShows() {
        XCTAssertNil(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: false))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: false, shown: false))
        XCTAssertEqual(WhatsNew.toShow(lastSeen: nil, hasHistory: true, plainLaunch: true), older)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: true),
            "2026-10-original")
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
        XCTAssertEqual(WhatsNew.toShow(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true), older)
        XCTAssertEqual(
            WhatsNew.seenAfter(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true, shown: true),
            "2026-10-original")
    }

    func testNotShownRecordsNothing() {
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: nil, hasHistory: true, plainLaunch: true, shown: false))
        XCTAssertNil(WhatsNew.seenAfter(lastSeen: "2025-01-withdrawn", hasHistory: true, plainLaunch: true, shown: false))
    }
}
