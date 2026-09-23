package com.zerotoship.z2term.channel

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LanRouteTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    @Test fun onlyAddressesInsideTheCurrentNetworkCount() {
        val home = listOf(ip("192.168.0.20") to 24)
        assertTrue(LanRoute.sameNetwork(ip("192.168.0.10"), home))
        assertFalse(LanRoute.sameNetwork(ip("192.168.1.10"), home))
        // A public address is never "at home", even when it is the home's own.
        assertFalse(LanRoute.sameNetwork(ip("203.0.113.10"), home))
        assertFalse(LanRoute.sameNetwork(ip("192.168.0.10"), emptyList()))
    }

    @Test fun prefixesThatDoNotEndOnAByteBoundaryAreMasked() {
        assertTrue(LanRoute.inPrefix(ip("10.0.13.5"), ip("10.0.12.1"), 22))
        assertFalse(LanRoute.inPrefix(ip("10.0.16.5"), ip("10.0.12.1"), 22))
        assertTrue(LanRoute.inPrefix(ip("fd00::1:2"), ip("fd00::9"), 64))
        // IPv4 and IPv6 never match each other.
        assertFalse(LanRoute.inPrefix(ip("192.168.0.10"), ip("fd00::9"), 0))
    }

    @Test fun anEmptyLanPortMeansTheSamePortAsUsual() {
        val profile = SshProfile(id = "p", name = "home", host = "203.0.113.10", port = 60022, user = "u",
            lanHost = "192.168.0.10")
        assertEquals(60022, LanRoute.lanPort(profile))
        assertEquals(65152, LanRoute.lanPort(profile.copy(lanPort = 65152)))
    }
}
