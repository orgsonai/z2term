package com.zerotoship.z2term.share

import java.net.InetAddress
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class DirectShareAddressTest {
    private fun address(value: String) = InetAddress.getByName(value)

    @Test fun generatedOriginsContainTheActualPortAndBracketOnlyIpv6() {
        assertEquals("http://127.0.0.1:54321", DirectShareAddress.httpOrigin(address("127.0.0.1"), 54321))
        val ipv6 = address("::1")
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
