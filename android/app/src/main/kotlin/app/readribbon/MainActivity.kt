package app.readribbon

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.readribbon.app.RibbonRoot
import app.readribbon.design.Haptics
import app.readribbon.design.RibbonTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

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

    /** Set once the store is loaded; until then the launch ground holds. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The unlit ground, held — never a spinner. Nothing in Ribbon should
        // be visibly loading (§8), and the window background is already
        // `@color/unlit`, so what this holds is the same near-black the room
        // is about to draw. The exit is removed rather than animated: an
        // icon flying away is the interstitial §05 says there isn't one of.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener { it.remove() }

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
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val url = intent.data ?: return
        links.trySend(url)
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
