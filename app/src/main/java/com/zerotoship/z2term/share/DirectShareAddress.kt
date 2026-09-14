package com.zerotoship.z2term.share

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** A locally assigned public address is a URL candidate, not proof of inbound reachability. */
internal object DirectShareAddress {
    fun select(addresses: List<InetAddress>): InetAddress? =
        addresses.firstOrNull { it is Inet4Address && isPublicCandidate(it) }
            ?: addresses.firstOrNull { isPublicCandidate(it) }

    fun isPublicCandidate(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        return when (address) {
            is Inet4Address -> {
                b[0] != 0 && b[0] < 224 &&
                    !(b[0] == 100 && b[1] in 64..127) &&
                    !(b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) &&
                    !(b[0] == 192 && b[1] == 88 && b[2] == 99) &&
                    !(b[0] == 198 && b[1] in 18..19) &&
                    !(b[0] == 198 && b[1] == 51 && b[2] == 100) &&
                    !(b[0] == 203 && b[1] == 0 && b[2] == 113)
            }
            is Inet6Address -> {
                // Native global unicast only; exclude protocol assignments, documentation and 6to4.
                (b[0] and 0xe0) == 0x20 &&
                    !(b[0] == 0x20 && b[1] == 0x01 && b[2] < 2) &&
                    !(b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) &&
                    !(b[0] == 0x20 && b[1] == 0x02) &&
                    !(b[0] == 0x3f && b[1] == 0xff && (b[2] and 0xf0) == 0)
            }
            else -> false
        }
    }

    fun httpOrigin(address: InetAddress, port: Int): String {
        require(port in 1..65535)
        // Numeric address only: never call DNS or an external address-discovery service.
        val host = requireNotNull(address.hostAddress).substringBefore('%')
        return "http://" + (if (address is Inet6Address) "[" + host + "]" else host) + ":" + port
    }
}
