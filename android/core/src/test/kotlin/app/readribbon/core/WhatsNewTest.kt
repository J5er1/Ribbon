package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    /** Whatever release is newest: the rules hold for every one of them. */
    val latest = WhatsNew.releases[0]

    // Today's release

    @Test
    fun testTodaysRelease() {
        assertEquals("2026-10-flyleaf", WhatsNew.releases.firstOrNull()?.id)
        assertEquals(
            listOf(
                WhatsNewItem.flyleaf,
                WhatsNewItem.yourShelf,
                WhatsNewItem.versionsByReading,
                WhatsNewItem.yourPage,
                WhatsNewItem.notificationsByName,
                WhatsNewItem.quietHoursNight,
            ),
            WhatsNew.releases.firstOrNull()?.items,
        )
        // The Hebrew made easy to read (A66) belongs to the release before.
        assertEquals("2026-10-following", WhatsNew.releases.getOrNull(1)?.id)
        assertEquals(
            listOf(
                WhatsNewItem.followingStays,
                WhatsNewItem.nativeSelection,
                WhatsNewItem.roomGroups,
                WhatsNewItem.lordReadsLord,
                WhatsNewItem.originalReadable,
            ),
            WhatsNew.releases.getOrNull(1)?.items,
        )
    }

    // Read again from You (A65): every release, newest first, each dated,
    // and every thing new told in exactly one of them.
    @Test
    fun testTheListReadsNewestFirstAndTellsEachThingOnce() {
        val ids = WhatsNew.releases.map { it.id }
        assertEquals(listOf("2026-10-flyleaf", "2026-10-following", "2026-10-original"), ids)
        assertEquals(ids.size, ids.toSet().size)
        val days = WhatsNew.releases.map { it.released }
        assertTrue(days.all { it.length == 10 })
        assertEquals(days.sortedDescending(), days)
        val told = WhatsNew.releases.flatMap { it.items }
        assertEquals(WhatsNewItem.entries.toSet(), told.toSet())
        assertEquals(WhatsNewItem.entries.size, told.size)
    }

    // Someone who saw an earlier release's screen sees this one's, once —
    // and someone two behind hears about this one, not the backlog.
    @Test
    fun testAnUpdaterWhoSawAnEarlierOneSeesThisOne() {
        assertEquals(
            "2026-10-flyleaf",
            WhatsNew.toShow(lastSeen = "2026-10-following", hasHistory = true, plainLaunch = true)?.id,
        )
        assertEquals(
            "2026-10-flyleaf",
            WhatsNew.toShow(lastSeen = "2026-10-original", hasHistory = true, plainLaunch = true)?.id,
        )
        assertEquals(
            "2026-10-flyleaf",
            WhatsNew.seenAfter(lastSeen = "2026-10-following", hasHistory = true, plainLaunch = true, shown = true),
        )
        assertNull(WhatsNew.toShow(lastSeen = "2026-10-flyleaf", hasHistory = true, plainLaunch = true))
    }

    @Test
    fun testItemRawValues() {
        assertEquals("original", WhatsNewItem.original.name)
        assertEquals("ownVersion", WhatsNewItem.ownVersion.name)
        assertEquals("followingWords", WhatsNewItem.followingWords.name)
        assertEquals("followingStays", WhatsNewItem.followingStays.name)
        assertEquals("nativeSelection", WhatsNewItem.nativeSelection.name)
        assertEquals("roomGroups", WhatsNewItem.roomGroups.name)
        assertEquals("lordReadsLord", WhatsNewItem.lordReadsLord.name)
        assertEquals("originalReadable", WhatsNewItem.originalReadable.name)
        assertEquals("flyleaf", WhatsNewItem.flyleaf.name)
        assertEquals("yourShelf", WhatsNewItem.yourShelf.name)
        assertEquals("versionsByReading", WhatsNewItem.versionsByReading.name)
        assertEquals("yourPage", WhatsNewItem.yourPage.name)
        assertEquals("notificationsByName", WhatsNewItem.notificationsByName.name)
        assertEquals("quietHoursNight", WhatsNewItem.quietHoursNight.name)
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
            latest.id,
            WhatsNew.seenAfter(lastSeen = null, hasHistory = false, plainLaunch = true, shown = false),
        )
        // A fresh install opened from an invite is still a fresh install.
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = false, plainLaunch = false))
        assertEquals(
            latest.id,
            WhatsNew.seenAfter(lastSeen = null, hasHistory = false, plainLaunch = false, shown = false),
        )
        // And the launch after onboarding has nothing to tell.
        assertNull(WhatsNew.toShow(lastSeen = latest.id, hasHistory = true, plainLaunch = true))
    }

    @Test
    fun testUpdateFromPreFeatureBuildShows() {
        assertEquals(latest, WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true))
        assertEquals(
            latest.id,
            WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = true),
        )
    }

    @Test
    fun testSameReleaseTwiceShowsOnce() {
        val first = WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true)
        assertEquals(latest, first)
        val recorded = WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = true, shown = first != null)
        assertEquals(latest.id, recorded)
        assertNull(WhatsNew.toShow(lastSeen = recorded, hasHistory = true, plainLaunch = true))
        assertNull(WhatsNew.seenAfter(lastSeen = recorded, hasHistory = true, plainLaunch = true, shown = false))
    }

    @Test
    fun testLinkLaunchSkipsAndRecordsNothingThenNextPlainLaunchShows() {
        assertNull(WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = false))
        assertNull(WhatsNew.seenAfter(lastSeen = null, hasHistory = true, plainLaunch = false, shown = false))
        assertEquals(latest, WhatsNew.toShow(lastSeen = null, hasHistory = true, plainLaunch = true))
        assertEquals(
            latest.id,
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
        assertEquals(latest, WhatsNew.toShow(lastSeen = "2025-01-withdrawn", hasHistory = true, plainLaunch = true))
        assertEquals(
            latest.id,
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
