package com.zerotoship.z2term.share

import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import java.io.Closeable

/** Observe the selected default network without switching the phone's Wi-Fi/mobile preference. */
internal class DirectShareNetwork(
    private val connectivity: ConnectivityManager,
    private val handler: Handler,
    private val onChanged: () -> Unit,
) : Closeable {
    class Unavailable(val problem: DirectShareManager.Problem) : Exception()

    private var network: Network? = null
    private var registered = false
    private var closed = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!closed && network != this@DirectShareNetwork.network) onChanged()
        }
        override fun onLost(network: Network) {
            if (!closed && network == this@DirectShareNetwork.network) onChanged()
        }
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!closed && blocked && network == this@DirectShareNetwork.network) onChanged()
        }
    }

    /** Call on the main thread; never switch the user's selected connection. */
    fun prepare() {
        network = connectivity.activeNetwork ?: throw Unavailable(DirectShareManager.Problem.RELAY_UNAVAILABLE)
        connectivity.registerDefaultNetworkCallback(callback, handler)
        registered = true
        checkCurrent()
    }

    /** Synchronous recheck only outside callbacks, including after the snapshot finishes. */
    fun checkCurrent() {
        if (network == null) return
        if (connectivity.activeNetwork != network) {
            throw Unavailable(DirectShareManager.Problem.NETWORK_CHANGED)
        }
    }

    override fun close() {
        closed = true
        if (registered) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            registered = false
        }
    }

}
