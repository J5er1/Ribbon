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
//
// A port of core/Sources/RibbonCore/WhatsNew.swift, case for case.

package app.readribbon.core

import kotlinx.serialization.Serializable

/**
 * One thing a release brings, in the order the screen shows them. The name
 * is the one the screen's copy and vignette are found by.
 */
@Serializable
enum class WhatsNewItem {
    /** The Hebrew and Greek under every verse. */
    original,

    /** Everybody reads their own version again. */
    ownVersion,

    /** Following lands on the same words. */
    followingWords,

    /**
     * Following stays with them: the screen stays on, and a dropped
     * connection or a second phone no longer loses them (A64).
     */
    followingStays,

    /** Selecting is the phone's own; a verse number takes the verse (A62). */
    nativeSelection,

    /** "In this room" gathers the room's readings, however many (A62). */
    roomGroups,

    /** The New King James as printed: LORD, and its copyright line (A64). */
    lordReadsLord,

    /**
     * The held word's line, readable over the page: on glass of its own,
     * the Hebrew larger and at full strength (A66).
     */
    originalReadable,

    /**
     * You opens like the front of a Bible: your name, your face, a ribbon
     * for every room you read in, and a colophon at the foot (A67).
     */
    flyleaf,

    /** Every book you have finished, with whoever, on one shelf (A67). */
    yourShelf,

    /** Each version is shown in its own words, and a ribbon marks yours (A67). */
    versionsByReading,

    /**
     * The page, the way you read it: a new line for every verse, a lighter
     * or heavier letter, clearer verse numbers, and a larger size (A68).
     */
    yourPage,

    /** The notification switches say who, in the words the phone will use (A67). */
    notificationsByName,

    /** Quiet hours are one band, the night, rather than two pickers (A67). */
    quietHoursNight,

    /**
     * The page can be set in one of five typefaces, each at a size that
     * matches Literata's (A69).
     */
    typeface,
}

/**
 * A release worth telling someone about. The id is a name, not a version
 * number — a build with nothing new to say keeps the last one's, and nobody
 * sees the screen twice for it.
 */
data class WhatsNewRelease(
    val id: String,
    val items: List<WhatsNewItem>,
    /** The day it was released, `yyyy-MM-dd`: its heading when the list is read again. */
    val released: String = "",
)

object WhatsNew {
    /**
     * Newest first. Only the first is ever shown on a launch: someone who
     * skipped three releases hears about the one they are on, and the rest
     * are simply how the app is now — there for the reading, from You.
     */
    val releases: List<WhatsNewRelease> = listOf(
        WhatsNewRelease(
            id = "2026-10-flyleaf",
            released = "2026-10-08",
            items = listOf(
                WhatsNewItem.flyleaf,
                WhatsNewItem.yourShelf,
                WhatsNewItem.versionsByReading,
                WhatsNewItem.yourPage,
                WhatsNewItem.typeface,
                WhatsNewItem.notificationsByName,
                WhatsNewItem.quietHoursNight,
            ),
        ),
        WhatsNewRelease(
            id = "2026-10-following",
            released = "2026-10-06",
            items = listOf(
                WhatsNewItem.followingStays,
                WhatsNewItem.nativeSelection,
                WhatsNewItem.roomGroups,
                WhatsNewItem.lordReadsLord,
                WhatsNewItem.originalReadable,
            ),
        ),
        WhatsNewRelease(
            id = "2026-10-original",
            released = "2026-10-04",
            items = listOf(WhatsNewItem.original, WhatsNewItem.ownVersion, WhatsNewItem.followingWords),
        ),
    )

    /**
     * The release to show on this launch, or null.
     *
     * - [lastSeen]: the release this phone last recorded as seen, if any.
     * - [hasHistory]: this phone already had Ribbon on it before this launch
     *   — someone has been through the four questions here. With no
     *   [lastSeen], that is a person updating from a build before this
     *   screen existed, and they are told; without it, a fresh install, and
     *   a fresh install gets the four questions, never a tour.
     * - [plainLaunch]: the app was opened, not sent somewhere — no
     *   notification, invite, link, widget or Live Activity behind it. A
     *   launch that came from a tap goes where the tap meant, and the screen
     *   waits for the next plain one.
     */
    fun toShow(
        lastSeen: String?,
        hasHistory: Boolean,
        plainLaunch: Boolean,
        releases: List<WhatsNewRelease> = WhatsNew.releases,
    ): WhatsNewRelease? {
        val latest = releases.firstOrNull() ?: return null
        if (lastSeen == latest.id) return null
        if (lastSeen == null && !hasHistory) return null
        if (!plainLaunch) return null
        // An older id, or one no longer in the list, is still not this one.
        return latest
    }

    /**
     * What to record as seen after this launch's decision (null = leave as
     * is). [shown] is whether the screen was put up and then left — every
     * way out of it counts, the button, back, or a swipe down.
     *
     * A fresh install records the latest straight away, so the first plain
     * launch after onboarding does not tell someone about what they have
     * only just met. A launch from a tap records nothing, so it is still
     * owed. Anything else records the latest once it has been shown.
     */
    fun seenAfter(
        lastSeen: String?,
        hasHistory: Boolean,
        plainLaunch: Boolean,
        shown: Boolean,
        releases: List<WhatsNewRelease> = WhatsNew.releases,
    ): String? {
        val latest = releases.firstOrNull() ?: return null
        if (lastSeen == latest.id) return null
        if (lastSeen == null && !hasHistory) return latest.id
        if (!plainLaunch) return null
        return if (shown) latest.id else null
    }
}
