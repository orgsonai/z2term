package com.zerotoship.z2term.share

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Handler
import android.system.OsConstants
import java.io.Closeable
import java.net.InetAddress

/** Observe the selected default network without switching the phone's Wi-Fi/mobile preference. */
internal class DirectShareNetwork(
    private val connectivity: ConnectivityManager,
    private val handler: Handler,
    private val onChanged: () -> Unit,
) : Closeable {
    class Unavailable(val problem: DirectShareManager.Problem) : Exception()

    private var network: Network? = null
    private var address: InetAddress? = null
    private var registered = false
    private var closed = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!closed && network != this@DirectShareNetwork.network) onChanged()
        }
        override fun onLost(network: Network) {
            if (!closed && network == this@DirectShareNetwork.network) onChanged()
        }
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (!closed && network == this@DirectShareNetwork.network &&
                address != null && address !in usableAddresses(linkProperties)) onChanged()
        }
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!closed && blocked && network == this@DirectShareNetwork.network) onChanged()
        }
    }

    /** Call on the main thread; returns the auto bind address, or null for a manual wildcard bind. */
    fun prepare(automatic: Boolean): InetAddress? {
        network = connectivity.activeNetwork
        address = if (automatic) {
            DirectShareAddress.select(usableAddresses(network?.let { connectivity.getLinkProperties(it) }))
                ?: throw Unavailable(DirectShareManager.Problem.NO_PUBLIC_ADDRESS)
        } else null
        // Manual origins also support localhost/offline LAN use without a default Internet network.
        if (network != null) {
            connectivity.registerDefaultNetworkCallback(callback, handler)
            registered = true
            checkCurrent()
        }
        return address
    }

    /** Synchronous recheck only outside callbacks, including after the snapshot finishes. */
    fun checkCurrent() {
        if (network == null) return
        if (connectivity.activeNetwork != network ||
            (address != null && address !in usableAddresses(network?.let { connectivity.getLinkProperties(it) }))) {
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

    private fun usableAddresses(properties: LinkProperties?): List<InetAddress> {
        val unusable = OsConstants.IFA_F_TENTATIVE or OsConstants.IFA_F_DADFAILED or OsConstants.IFA_F_DEPRECATED
        return properties?.linkAddresses.orEmpty().filter { (it.flags and unusable) == 0 }.map { it.address }
    }
}
