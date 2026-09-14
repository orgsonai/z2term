package com.zerotoship.z2term.share

import java.net.InetAddress
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class DirectShareAddressTest {
    private fun address(value: String) = InetAddress.getByName(value)

    @Test fun localCarrierSharedAndSpecialAddressesAreNeverPublicCandidates() {
        for (value in listOf(
            "0.0.0.0", "10.0.0.2", "172.16.0.2", "192.168.1.2", "127.0.0.1", "169.254.1.2",
            "100.64.0.1", "100.127.255.254", "192.0.0.4", "192.0.2.1", "192.88.99.1",
            "198.18.0.1", "198.19.255.254", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1",
            "::", "::1", "fe80::1", "fd12::1", "fc00::1", "fec0::1", "ff02::1",
            "64:ff9b::1", "2001::1", "2001:2::1", "2001:db8::1", "2002::1", "3fff:fff::1",
        )) assertFalse(value, DirectShareAddress.isPublicCandidate(address(value)))
    }

    @Test fun automaticChoiceUsesAnAssignedPublicAddressOrReturnsNoCandidate() {
        val ipv4 = address("1.1.1.1")
        val ipv6 = address("2606:4700:4700::1111")
        val local = address("192.168.0.2")
        assertEquals(ipv4, DirectShareAddress.select(listOf(local, ipv6, ipv4)))
        assertEquals(ipv6, DirectShareAddress.select(listOf(local, ipv6)))
        assertNull(DirectShareAddress.select(listOf(local, address("100.72.1.2"), address("fe80::1"))))
        assertNull(DirectShareAddress.select(emptyList()))
    }

    @Test fun generatedOriginsContainTheActualPortAndBracketOnlyIpv6() {
        assertEquals("http://1.1.1.1:54321", DirectShareAddress.httpOrigin(address("1.1.1.1"), 54321))
        val ipv6 = address("2606:4700:4700::1111")
        val origin = DirectShareAddress.httpOrigin(ipv6, 32123)
        assertTrue(origin.startsWith("http://["))
        assertEquals(32123, URI(origin).port)
        assertEquals(ipv6, address(URI(origin).host))
        assertEquals(origin, DirectShareConfig.parse(origin, 32123, 15).origin)
        for (port in listOf(0, -1, 65536)) {
            assertTrue(runCatching { DirectShareAddress.httpOrigin(ipv6, port) }.isFailure)
        }
    }
}
