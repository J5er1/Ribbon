@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
 * A tablet is any device that has never reported a smallest width under
 * 600 dp. The same test `Copy.deviceNoun` uses (deviation A8): a property of
 * the hardware rather than of the current window, so a phone in a freeform
 * window is still a phone and a tablet in a narrow split is still a tablet.
 */
private const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * The shortest the mark is allowed to be on screen, in milliseconds.
 *
 * Not a duration — a floor. The splash is held by the store loading and
 * nothing else; this only stops a warm launch cutting the unfurl off after
 * three frames, which reads as a glitch rather than as a mark. Set just
 * above the unfurl's own 440 ms (`animator/splash_unfurl.xml`) so the ribbon
 * always finishes coming down.
 */
private const val MARK_FLOOR_MS = 480L

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

    /** Set once the store is loaded; until then the launch ground holds. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The Wave, unfurling on the unlit ground, and then the room.
        //
        // §05 says there is no splash screen. That rule survives here because
        // nothing is inserted: Android 12 and later show a system splash on
        // every cold start whether an app asks for one or not, so the only
        // real choice is whether it carries our mark or the launcher icon on
        // a plate. Owner's call (deviation A28) — it carries the mark.
        //
        // What §05 is actually protecting is the *time*: "nothing may be
        // inserted between opening the app and reading". So the splash is
        // held by the store coming off disk, exactly as before, and the floor
        // below is the one concession — the mark's unfurl is 440 ms and a
        // fast warm launch would otherwise show three frames of a ribbon and
        // cut. A mark that flickers is worse than no mark. It is a floor, not
        // a duration: on the cold start that actually needs the time, the
        // store is slower than this and the floor costs nothing at all.
        val splash = installSplashScreen()
        val launchedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !ready || SystemClock.uptimeMillis() - launchedAt < MARK_FLOOR_MS
        }
        // Handed over rather than cut. The mark does not fly anywhere — §13's
        // never-ship list is largely a list of logo animations — it simply
        // stops being there, over the same fade the app uses for anything
        // arriving (§9.1's arrive token, 240 ms), onto the room that has
        // already drawn underneath.
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
        if (savedInstanceState == null) deliver(intent)

        val haptics = Haptics(this)

        setContent {
            RibbonTheme(haptics = haptics) {
                RibbonRoot(
                    links = linkStream,
                    destinations = destinationStream,
                    onReady = { ready = true },
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
