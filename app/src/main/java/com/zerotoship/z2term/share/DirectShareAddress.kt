package com.zerotoship.z2term.share

import java.net.Inet6Address
import java.net.InetAddress

/** Numeric origin for the private loopback listener. */
internal object DirectShareAddress {
    fun httpOrigin(address: InetAddress, port: Int): String {
        require(port in 1..65535)
        // Numeric address only: never call DNS or an external address-discovery service.
        val host = requireNotNull(address.hostAddress).substringBefore('%')
        return "http://" + (if (address is Inet6Address) "[" + host + "]" else host) + ":" + port
    }
}
