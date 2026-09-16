package app.readribbon.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService

// Whether there is a network, for the two things in the room that are
// allowed to know.
//
// S01's offline state is deliberately almost invisible: "Fire renders in its
// last known state, dimmed by ~8%. Presence line absent. No banner." The
// presence half needs nothing — presence comes off the wire, so it is
// already absent — and the dimming needs exactly this: one boolean, read by
// one call site.
//
// What it is emphatically not is a connectivity *feature*. No banner, no
// retry control, no "you're offline" sheet, nothing queued or counted. §13
// puts a connectivity banner on the never-ship list and the reason is the
// product's whole posture: a room that is patient does not announce the
// weather. The fire going a shade quieter is the entire user-visible
// surface of this file.
//
// `NET_CAPABILITY_VALIDATED` rather than merely connected, because a Wi-Fi
// network that cannot reach anything is offline as far as the room is
// concerned, and that is the common case (a captive portal, a router that
// has lost its uplink) where "connected" lies.

/**
 * Whether the device has a validated network, live.
 *
 * Snapshot state, so a fire dims on the frame the network drops. Starts from
 * the current network so the first frame is right rather than optimistic.
 */
@Stable
class Connectivity(context: Context) {

    private val manager = context.applicationContext.getSystemService<ConnectivityManager>()

    /** True when there is a network that can actually reach something. */
    var online: Boolean by mutableStateOf(manager.hasInternet())
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            online = manager.hasInternet()
        }

        override fun onLost(network: Network) {
            online = manager.hasInternet()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            online = manager.hasInternet()
        }
    }

    init {
        // A default-network callback rather than a request: the question is
        // "can this device reach anything", not "is there a Wi-Fi".
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
    }

    /** Stop listening. Paired with the model's own clear-down. */
    fun stop() {
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }
}

/**
 * Re-asked rather than inferred from the callback that fired.
 *
 * Every callback here answers the same question the same way, because the
 * one that fired is not necessarily the one that matters: `onLost` for Wi-Fi
 * while mobile data is up is not going offline. Asking the manager is the
 * only answer that is true for the device rather than for one network.
 *
 * A null manager (which only happens where there is no connectivity service
 * at all) reads as online: an unknown network is not a reason to dim a fire.
 */
private fun ConnectivityManager?.hasInternet(): Boolean {
    if (this == null) return true
    val caps = runCatching { getNetworkCapabilities(activeNetwork) }.getOrNull() ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
