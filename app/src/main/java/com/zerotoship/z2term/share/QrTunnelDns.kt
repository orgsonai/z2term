package com.zerotoship.z2term.share

import android.annotation.SuppressLint
import android.net.DnsResolver
import android.net.ConnectivityManager
import android.content.Context
import android.os.CancellationSignal
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet6Address
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Newly provisioned tunnel names can initially return NXDOMAIN. Do not retain that negative answer. */
internal class QrTunnelDns(context: Context) : Dns {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    // DnsResolver documents combined flags; SDK 35's IntDef omits flag=true.
    @SuppressLint("WrongConstant")
    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(".trycloudflare.com")) return Dns.SYSTEM.lookup(hostname)
        val done = CountDownLatch(1)
        val answer = AtomicReference<List<InetAddress>>(emptyList())
        val cancellation = CancellationSignal()
        val network = connectivity.activeNetwork
        val properties = network?.let(connectivity::getLinkProperties)
        val hasIpv6 = properties == null || properties.linkAddresses.any {
            it.address is Inet6Address && !it.address.isLinkLocalAddress
        }
        try {
            // Uses Android's configured network/DNS (including private DNS), without changing device settings.
            DnsResolver.getInstance().query(network, hostname,
                DnsResolver.FLAG_NO_CACHE_LOOKUP or DnsResolver.FLAG_NO_CACHE_STORE,
                Executor { it.run() }, cancellation, object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(addresses: List<InetAddress>, rcode: Int) {
                        if (rcode == 0) answer.set(addresses.filter { hasIpv6 || it !is Inet6Address }
                            .sortedBy { it is Inet6Address })
                        done.countDown()
                    }
                    override fun onError(error: DnsResolver.DnsException) { done.countDown() }
                })
            if (!done.await(4, TimeUnit.SECONDS) || answer.get().isEmpty()) throw UnknownHostException("Tunnel DNS is not ready")
            return answer.get()
        } finally { cancellation.cancel() }
    }
}
