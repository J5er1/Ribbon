@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.readribbon.app.RibbonRoot
import app.readribbon.core.VerseAddress
import app.readribbon.design.Haptics
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonTheme
import app.readribbon.services.Destination
import app.readribbon.services.Notifications
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// The scene. Swift's `WindowGroup` has no Android counterpart that is also a
// view, so the Activity keeps the three things only it can do — the launch
// ground, the window, and the intents — and hands everything else to
// `RibbonRoot`.

/**
 * Whether an intent with this action sends the app somewhere: a tapped link
 * (an invite, through the App Link or the `ribbon://` fallback) or a tapped
 * notification or home-screen fire, which both arrive as
 * [Notifications.ACTION_OPEN]. The launcher's own `MAIN` does not, and
 * neither does anything else, because [MainActivity] acts on nothing else.
 *
 * Said by the action alone and not by whether the intent parsed: a tap on a
 * notification whose room no longer exists still came from a tap, and the
 * person is waiting to see where it went rather than for a screen of news.
 */
internal fun carriesSomewhereToGo(action: String?): Boolean =
    action == Intent.ACTION_VIEW || action == Notifications.ACTION_OPEN

/**
 * Whether this is a launch the "what's new" screen may stand in (A61): the
 * Activity is new rather than [restored], and nothing sent it anywhere.
 */
internal fun isPlainLaunch(restored: Boolean, action: String?): Boolean =
    !restored && !carriesSomewhereToGo(action)

/**
 * A tablet is any device that has never reported a smallest width under
 * 600 dp. The same test `Copy.deviceNoun` uses (deviation A8): a property of
 * the hardware rather than of the current window, so a phone in a freeform
 * window is still a phone and a tablet in a narrow split is still a tablet.
 */
private const val TABLET_SMALLEST_WIDTH_DP = 600

class MainActivity : ComponentActivity() {

    /**
     * Tapped invite links, in the order they arrive (S16).
     *
     * An unbounded channel and not a flag, because a link routinely lands
     * before there is anything to hand it to: tapping `readribbon.app/i/…`
     * cold-starts the app, and the intent is read in `onCreate` while the
     * store is still coming off disk. The channel holds it until the root
     * collects, and delivers it exactly once — a replayed link would open
     * the join a second time.
     */
    private val links = Channel<Uri>(Channel.UNLIMITED)
    private val linkStream = links.receiveAsFlow()

    /**
     * Where a tapped notification is asking the app to go (S19), on the same
     * terms and for the same reason as [links].
     *
     * A second stream rather than a second kind of Uri on the first one. The
     * `ribbon://` filter in the manifest is exported and BROWSABLE, so any
     * web page can send this Activity a link of that shape; a destination
     * carried as extras on a private action cannot be reached from outside
     * the app at all. Somewhere to *go* is a bigger thing to hand a stranger
     * than an invite token, which the model validates anyway.
     */
    private val destinations = Channel<Destination>(Channel.UNLIMITED)
    private val destinationStream = destinations.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The unlit ground, and then the app.
        //
        // §05 says there is no splash screen. That rule survives here because
        // nothing is inserted: Android 12 and later show a system splash on
        // every cold start whether an app asks for one or not, so the only
        // real choice is whether it carries something of ours or the launcher
        // icon on a plate. It carries the ground (A28, and A45 for why the
        // mark is no longer in it).
        //
        // **Nothing holds this window open any more.** It used to be kept up
        // until the store had loaded, plus a 480 ms floor so a warm launch
        // could not cut the mark's unfurl to three frames — and the unfurl
        // was the thing that did not run. The mark is `design/LaunchMark.kt`
        // now, on the app's own first frame, so the window's job is to be the
        // ground until there is a frame to replace it, which is exactly what
        // the library does by default: it holds until the content view draws.
        // The mark's animation then happens *while* the store comes off disk
        // rather than after the window has already been held for it, which is
        // what §05 is actually protecting — the time.
        val splash = installSplashScreen()
        // Handed over rather than cut. Both sides of this are the same black
        // — `@color/unlit` in the window, `Brand.ground` in the frame
        // underneath — so the fade is a formality that covers the one frame
        // where the window is torn down, not a transition anybody sees.
        splash.setOnExitAnimationListener { screen ->
            screen.view.animate()
                .alpha(0f)
                .setDuration(RibbonMotion.ARRIVE_MS.toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { screen.remove() }
                .start()
        }

        super.onCreate(savedInstanceState)

        orientToTheDevice()

        // Edge to edge is mandatory (§12.2). Both bars are transparent with
        // light contents, and neither gets a contrast scrim — the ground and
        // its grain run under them, and every screen clears them itself.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // The launch intent is read once, on a genuinely new launch. A
        // rotation rebuilds the Activity with the same intent still attached,
        // and replaying it would reopen a join over whatever the person had
        // moved on to.
        //
        // Whether it carried anywhere to go is also the whole of what the
        // "what's new" screen asks of a launch (A61): it waits for one that
        // was simply opened, and never stands between a tap and where the
        // tap meant. Nor is a rebuilt Activity a launch: a rotation keeps the
        // model, so it decides nothing, and a process reclaimed in the pocket
        // is rebuilt when the person comes *back* — to the menu they had
        // open, in the middle of whatever they were doing — which is not the
        // moment to put a page of news in front of it. It waits, as a tap
        // does, for the next time the app is simply opened.
        val plainLaunch = isPlainLaunch(restored = savedInstanceState != null, action = intent?.action)
        if (savedInstanceState == null) deliver(intent)

        val haptics = Haptics(this)

        setContent {
            RibbonTheme(haptics = haptics) {
                RibbonRoot(
                    links = linkStream,
                    destinations = destinationStream,
                    plainLaunch = plainLaunch,
                )
            }
        }
    }

    /**
     * The app is `singleTask`, so a link tapped while it is already running
     * arrives here rather than in a second Activity. It sets the pending
     * invite and nothing else: the join rides over the room, and the room
     * itself does not change underneath whatever is being read.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    // The two kinds of intent below are the only ones that send the app
    // anywhere, and [carriesSomewhereToGo] is kept beside them so that a
    // third one cannot be added here without the launch hearing about it.
    private fun deliver(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(links::trySend)
            Notifications.ACTION_OPEN -> destination(intent)?.let(destinations::trySend)
        }
    }

    /**
     * A tapped notification, as somewhere to go.
     *
     * Read defensively: an intent that has been through the system's parcel
     * machinery and back can be missing anything, and a malformed one should
     * land the person on the room rather than on a crash. A room id that will
     * not parse is the one case with nothing to fall back to, so it returns
     * null and the app simply opens where it was.
     */
    private fun destination(intent: Intent): Destination? {
        val room = intent.getStringExtra(Notifications.EXTRA_ROOM)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: return null
        val reading = intent.getStringExtra(Notifications.EXTRA_READING)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val book = intent.getStringExtra(Notifications.EXTRA_BOOK)
        val chapter = intent.getIntExtra(Notifications.EXTRA_CHAPTER, 0)
        val verse = intent.getIntExtra(Notifications.EXTRA_VERSE, 0)

        return when {
            reading != null && book != null && chapter > 0 && verse > 0 ->
                Destination.Verse(
                    roomID = room,
                    readingID = reading,
                    verse = VerseAddress(bookID = book, chapter = chapter, verse = verse),
                )

            reading != null && chapter > 0 ->
                Destination.Cards(roomID = room, readingID = reading, chapter = chapter)

            else -> Destination.Room(roomID = room)
        }
    }

    /**
     * Portrait on a phone, every orientation on a tablet (deviation 12: iPad
     * — and so a tablet — is a considered surface, one readable column wide;
     * the phone stays portrait-only).
     *
     * The manifest cannot make a decision that depends on the device, so it
     * carries no `screenOrientation` and this does — in one place, before the
     * first frame. `requestedOrientation` overrides a manifest lock anyway,
     * so a lock there as well would only mean a tablet starting
     * portrait-locked and unlocking a frame later.
     *
     * On Android 16 the point is partly moot in the other direction: an app
     * targeting API 36 has its orientation restrictions ignored outright on
     * any display 600 dp or wider, so a tablet would open to every
     * orientation whatever this asked for. The phone is what this still
     * decides, and it decides it on every release.
     */
    private fun orientToTheDevice() {
        val tablet = resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
        val wanted = if (tablet) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }
}
