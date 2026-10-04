package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

// What's new (A61): who sees the screen, and what the phone records after.
// A port of core/Tests/RibbonCoreTests/WhatsNewTests.swift, case for case.
class WhatsNewTest {
    val newer = WhatsNewRelease(id = "2027-03-later", items = listOf(WhatsNewItem.followingWords))
    val older = WhatsNewRelease(
        id = "2026-10-original",
        items = listOf(WhatsNewItem.original, WhatsNewItem.ownVersion, WhatsNewItem.followingWords),
    )

    // Today's release

    @Test
    fun testTodaysRelease() {
        assertEquals("2026-10-original", WhatsNew.releases.firstOrNull()?.id)
        assertEquals(
            listOf(WhatsNewItem.original, WhatsNewItem.ownVersion, WhatsNewItem.followingWords),
            WhatsNew.releases.firstOrNull()?.items,
        )
    }

    @Test
    fun testItemRawValues() {
        assertEquals("original", WhatsNewItem.original.name)
        assertEquals("ownVersion", WhatsNewItem.ownVersion.name)
        assertEquals("followingWords", WhatsNewItem.followingWords.name)
        val json = Json.encodeToString(
            listOf(WhatsNewItem.original, WhatsNewItem.ownVersion, WhatsNewItem.followingWords),
        )
        assertEquals("""["original","ownVersion","followingWords"]""", json)
    }

    // The rules

    @Test
    fun testNoReleasesShowsNothing() {
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true, releases = emptyList()))
        assertNull(
            WhatsNew.seenAfter(
                lastSeen = null, hasHistory = true, plainLaunch = true, shown = true, releases = emptyList(),
            ),
        )
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = false, plainLaunch = true, releases = emptyList()))
        assertNull(
            WhatsNew.seenAfter(
                lastSeen = null, hasHistory = false, plainLaunch = true, shown = false, releases = emptyList(),
            ),
        )
    }

    @Test
    fun testFreshInstallRecordsAndSkips() {
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = false, plainLaunch = true))
        assertEquals(
            "2026-10-original",
            WhatsNew.seenAfter(lastSeen = null, hasHistory = false, plainLaunch = true, shown = false),
        )
        // A fresh install opened from an invite is still a fresh install.
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = false, plainLaunch = false))
        assertEquals(
            "2026-10-original",
            WhatsNew.seenAfter(lastSeen = null, hasHistory = false, plainLaunch = false, shown = false),
        )
        // And the launch after onboarding has nothing to tell.
        assertNull(WhatsNew.toShow(lastSeen = "2026-10-original", hasHistory = true, plainLaunch = true))
    }

    @Test
    fun testUpdateFromPreFeatureBuildShows() {
        assertEquals(older, WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true))
        assertEquals(
            "2026-10-original",
            WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = true),
        )
    }

    @Test
    fun testSameReleaseTwiceShowsOnce() {
        val first = WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true)
        assertEquals(older, first)
        val recorded = WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = first != null)
        assertEquals("2026-10-original", recorded)
        assertNull(WhatsNew.toShow(lastSeen = recorded, hasHistory = true, plainLaunch = true))
        assertNull(WhatsNew.seenAfter(lastSeen = recorded, hasHistory = true, plainLaunch = true, shown = false))
    }

    @Test
    fun testLinkLaunchSkipsAndRecordsNothingThenNextPlainLaunchShows() {
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = false))
        assertNull(WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = false, shown = false))
        assertEquals(older, WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true))
        assertEquals(
            "2026-10-original",
            WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = true),
        )
    }

    @Test
    fun testOlderLastSeenShowsLatestOnly() {
        val releases = listOf(newer, older)
        val shown = WhatsNew.toShow(
            lastSeen = "2026-10-original", hasHistory = true, plainLaunch = true, releases = releases,
        )
        assertEquals(newer, shown)
        assertEquals(listOf(WhatsNewItem.followingWords), shown?.items)
        assertEquals(
            "2027-03-later",
            WhatsNew.seenAfter(
                lastSeen = "2026-10-original", hasHistory = true, plainLaunch = true, shown = true, releases = releases,
            ),
        )
        // Someone two releases behind hears about the latest, not the backlog.
        assertEquals(newer, WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true, releases = releases))
    }

    @Test
    fun testUnknownLastSeenShowsLatest() {
        assertEquals(older, WhatsNew.toShow(lastSeen = "2025-01-withdrawn", hasHistory = true, plainLaunch = true))
        assertEquals(
            "2026-10-original",
            WhatsNew.seenAfter(lastSeen = "2025-01-withdrawn", hasHistory = true, plainLaunch = true, shown = true),
        )
    }

    @Test
    fun testNotShownRecordsNothing() {
        assertNull(WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = false))
        assertNull(
            WhatsNew.seenAfter(lastSeen = "2025-01-withdrawn", hasHistory = true, plainLaunch = true, shown = false),
        )
    }
}
