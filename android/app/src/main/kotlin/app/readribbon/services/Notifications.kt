@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import app.readribbon.MainActivity
import app.readribbon.R
import app.readribbon.app.Copy
import app.readribbon.app.firstName
import app.readribbon.core.VerseAddress
import app.readribbon.data.AppSettings
import app.readribbon.data.RoomNotificationPrefs
import app.readribbon.design.Brand
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Notifications (S19, §10.3) — the half of the app that had a settings
// screen and no feature.
//
// Before this file, nothing in the Android build ever posted a notification:
// no channel, no `notify`, no small icon, no PendingIntent. `Copy` held all
// six of §10.3's strings and exactly one of them was ever read — as the text
// of an in-app waiting row. S19, which the build book calls "the setting
// screen that decides whether people keep this app", configured nothing;
// deviation 15 has named that since the screen was built.
//
// Three decisions here are worth arguing with, because each looks like a
// departure from the book and is not.
//
// **Channels are per kind, not per room**, and S19's heading is "per room,
// not global". A channel's importance is the person's to set and cannot be
// changed by the app once created, so four channels times six rooms would be
// twenty-four rows in Android's settings for somebody to curate, and a room
// they left would leave dead ones behind forever with no way to remove them.
// So the *platform's* grouping is by kind, and Ribbon's own code does the
// per-room gating before it ever reaches the platform — [shouldPost] is the
// only thing standing between an arrival and a post, and it reads the room's
// own `RoomNotificationPrefs` every time. §12.2's Law 5 is the authority:
// the platform owns the chrome, Ribbon owns the content, and which rooms a
// person wants to hear from is content.
//
// **No badges, ever.** Every channel is created with `setShowBadge(false)`
// and nothing here calls `setNumber`. §13 forbids "a red number badge on the
// app icon" and on Android a badge is something a channel opts into rather
// than something a post asks for — so it is refused once, at the channel, on
// all five. Law 2's leaks are mostly the ones you never wrote: Android will
// put a count on the icon for free if you let it.
//
// **The collapse is by author, and says no number.** When several notes land
// at once §10.3 gives a second string — "Ruth left you a note", without the
// verse — and the obvious Android shape for that, a group with a summary,
// is exactly what must not be used: Android renders its own summary with
// "+2 more" in it, which is a count attached to reading, posted by the
// platform, in the one place nobody would think to look for a Law 2 breach.
// So several notes from one person replace each other on one id and say the
// collapsed string, and nothing is ever grouped.

/** Which kind of thing arrived. One channel each. */
enum class NotificationKind(val channelId: String) {
    notesLeft("notes"),
    cardsOpen("cards"),
    inTheBook("in_the_book"),
    thinkingOfYou("thinking_of_you"),
    bookFinished("a_book_finished"),
}

/**
 * Where a tapped notification is asking the app to go.
 *
 * Carried as intent extras rather than as a `ribbon://` link: that scheme's
 * intent-filter is exported and BROWSABLE, so anything that can open a URL
 * could send the app anywhere it liked. These extras are unexported and
 * explicit.
 */
sealed interface Destination {
    val roomID: Uuid

    /** §6.3's "tap → the reading opens at that verse, the mark breathing". */
    data class Verse(
        override val roomID: Uuid,
        val readingID: Uuid,
        val verse: VerseAddress,
    ) : Destination

    /** The cards sit at the end of a chapter, so this is that chapter. */
    data class Cards(
        override val roomID: Uuid,
        val readingID: Uuid,
        val chapter: Int,
    ) : Destination

    /** S01. Where a finished book belongs: the ember is on the shelf and the
     *  room is the way to it. */
    data class Room(override val roomID: Uuid) : Destination
}

object Notifications {

    /** The private action a tapped notification carries. Never exported. */
    const val ACTION_OPEN = "app.readribbon.OPEN"

    const val EXTRA_ROOM = "room"
    const val EXTRA_READING = "reading"
    const val EXTRA_BOOK = "book"
    const val EXTRA_CHAPTER = "chapter"
    const val EXTRA_VERSE = "verse"

    /**
     * Create the five channels. Called once from `RibbonApplication.onCreate`
     * — the only place that runs before anything could post and after the
     * process exists.
     *
     * Creating a channel that already exists updates its name and leaves the
     * importance the person chose alone, which is the behaviour we want: the
     * words follow Ribbon's copy, the loudness stays theirs.
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        fun channel(
            kind: NotificationKind,
            name: String,
            importance: Int,
            silent: Boolean = false,
        ) = NotificationChannel(kind.channelId, name, importance).apply {
            // §13: no red number badge on the app icon, in its Android form.
            setShowBadge(false)
            if (silent) {
                setSound(null, null)
                enableVibration(false)
            }
        }

        manager.createNotificationChannels(
            listOf(
                // The names are S19's switch titles verbatim, so Android's
                // own settings page and Ribbon's say the same words about the
                // same thing. A person who goes looking in the OS after
                // turning something off in the app finds the row they expect.
                channel(
                    NotificationKind.notesLeft,
                    Copy.NOTES_LEFT_FOR_YOU,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    NotificationKind.cardsOpen,
                    Copy.CARDS_OPEN,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                // Low: presence is ambient rather than an event. §4.2 gives
                // somebody arriving one soft transient, and a heads-up banner
                // for "Ruth is reading Mark" would be the app shouting about
                // the quietest thing it does.
                channel(
                    NotificationKind.inTheBook,
                    Copy.WHEN_THEY_OPEN_THE_BOOK,
                    NotificationManager.IMPORTANCE_LOW,
                ),
                // Silent at the channel, because §9.3 names an exact envelope
                // for this one tap and §12.2 has already rejected the blunt
                // platform constants for it. The app plays the composition
                // itself; the post carries the name and nothing else.
                channel(
                    NotificationKind.thinkingOfYou,
                    Copy.THINKING_OF_YOU,
                    NotificationManager.IMPORTANCE_DEFAULT,
                    silent = true,
                ),
                // Its own channel rather than folded into the cards, because
                // S19 says this one has no switch — and somebody who silences
                // notes and cards in Android's settings must still be able to
                // learn that their room finished a book. §6.5: it goes to
                // everyone, "including whoever wasn't there when it happened".
                channel(
                    NotificationKind.bookFinished,
                    Copy.A_BOOK_FINISHED,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    /** Whether Android will actually deliver anything we post. */
    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * The one gate. Everything that posts comes through here first, and it is
     * what makes S19's "per room, not global" true given that the channels
     * are per kind.
     *
     * @param visibleRoomID the room the person is looking at *right now*, or
     *   null when Ribbon is not in front of them. §6.3 asks for "a
     *   notification, or nothing at all", and the room already unfurls an
     *   arriving note in place under the fire — a heads-up sliding over the
     *   top of that would shout over the one beat the interface handles well.
     *   A note in *another* room still posts: that is news you would not
     *   otherwise get.
     */
    fun shouldPost(
        kind: NotificationKind,
        roomID: Uuid,
        prefs: RoomNotificationPrefs,
        settings: AppSettings,
        visibleRoomID: Uuid?,
    ): Boolean {
        val wanted = when (kind) {
            NotificationKind.notesLeft -> prefs.notesLeft
            NotificationKind.cardsOpen -> prefs.cardsOpen
            NotificationKind.inTheBook -> prefs.whenTheyOpenTheBook
            NotificationKind.thinkingOfYou -> prefs.thinkingOfYou
            // S19: "The one with no switch." It fires a handful of times a
            // year and is an invitation back, not an absence notification.
            NotificationKind.bookFinished -> true
        }
        if (!wanted) return false

        // Quiet hours (S19). Thinking of you is the only thing permitted to
        // arrive inside them, and even then not as a notification — the
        // caller plays the haptic and posts nothing, which is what "silently,
        // as a touch" means.
        if (settings.isQuietNow()) return false

        return when (kind) {
            // Both of these post regardless of what is on screen. A finished
            // book goes to everyone (§6.5) including the person holding the
            // phone; a tap on the shoulder has no in-app surface that could
            // carry it, so the notification *is* the delivery (§4.3).
            NotificationKind.bookFinished, NotificationKind.thinkingOfYou -> true
            else -> roomID != visibleRoomID
        }
    }

    /**
     * Post one line. There is no body, no large icon, no expanded style and
     * no action button: §10.3's strings are whole sentences and a second line
     * under them would be the app explaining itself.
     *
     * @param id what this post replaces. Several notes from one person in one
     *   room share an id and replace each other, which is §10.3's collapse
     *   without a group summary and therefore without Android's own "+2 more".
     * @param silent a replacement that should not make a sound of its own —
     *   the second note inside half an hour, or a reader's line kept current.
     * @param ongoing a line that stands for as long as something is true —
     *   "Ruth is reading Mark", while she is (S24's Android stand-in).
     * @param timeoutMillis how long an ongoing line may stand without being
     *   kept current before it goes by itself.
     */
    fun post(
        context: Context,
        id: Int,
        kind: NotificationKind,
        line: String,
        to: Destination,
        silent: Boolean = false,
        ongoing: Boolean = false,
        timeoutMillis: Long? = null,
    ) {
        // The same two questions [allowed] asks, asked again here rather than
        // through it. `notify` is permission-gated, and lint can only see a
        // check that is in the same function as the call — a helper one frame
        // up reads to it as no check at all. Cheap, and the alternative is
        // suppressing a check that is right to exist.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            // The one accent, and the brand's rather than the room's: a
            // notification is drawn by the system shade on somebody else's
            // ground, where a wallpaper-derived tint has nothing to sit
            // against and no guarantee of contrast (deviation A18 is about
            // the app's own surfaces).
            .setColor(Brand.chartreuse.toArgb())
            .setContentTitle(line)
            .setContentIntent(pendingIntent(context, id, to))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSilent(silent)
            .setOngoing(ongoing)
            .apply { if (timeoutMillis != null) setTimeoutAfter(timeoutMillis) }
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Every post for a room, taken back — used when a room is left. */
    fun forget(context: Context, ids: List<Int>) {
        val manager = NotificationManagerCompat.from(context)
        ids.forEach { runCatching { manager.cancel(it) } }
    }

    /**
     * A stable id per room and kind, so a second note in the same room
     * replaces the first rather than stacking under it.
     *
     * Derived from the room rather than random: §10.3's collapsed string —
     * "Ruth left you a note", with no verse — is what a replacement says, and
     * a replacement is only possible if the two posts share an id.
     */
    fun id(roomID: Uuid, kind: NotificationKind): Int =
        (roomID.hashCode() * 31 + kind.ordinal) and 0x7FFFFFFF

    private fun pendingIntent(context: Context, id: Int, to: Destination): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            putExtra(EXTRA_ROOM, to.roomID.toString())
            when (to) {
                is Destination.Verse -> {
                    putExtra(EXTRA_READING, to.readingID.toString())
                    putExtra(EXTRA_BOOK, to.verse.bookID)
                    putExtra(EXTRA_CHAPTER, to.verse.chapter)
                    putExtra(EXTRA_VERSE, to.verse.verse)
                }

                is Destination.Cards -> {
                    putExtra(EXTRA_READING, to.readingID.toString())
                    putExtra(EXTRA_CHAPTER, to.chapter)
                }

                is Destination.Room -> Unit
            }
        }
        return PendingIntent.getActivity(
            context,
            // The request code is the notification's own id, so two rooms'
            // notifications cannot overwrite each other's intent — which is
            // what FLAG_UPDATE_CURRENT would otherwise do to them, silently,
            // leaving one of them opening the other's room.
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * The line for one or more notes from the same person, in the same room
 * (§10.3).
 *
 * Two strings and no third: one note names the verse, because the address is
 * the invitation to go; several name only the person, because listing them
 * would be a count in prose. Nothing here ever says how many.
 */
fun notesLeftLine(name: String, verse: VerseAddress?, several: Boolean): String =
    if (several || verse == null) {
        Copy.notifNotesLeft(firstName(name))
    } else {
        Copy.notifNoteLeft(firstName(name), verse.formatted)
    }
