package com.zerotoship.z2term.share

import org.junit.Assert.*
import org.junit.Test

class DirectShareConfigTest {
    @Test fun originMatchesBrowserCanonicalizationAndLocalPortIsIndependent() {
        val https = DirectShareConfig.parse(" HTTPS://Share.Example:443/ ", 8443, 15)
        assertEquals("https://share.example", https.origin)
        assertEquals(8443, https.port)
        assertTrue(https.tls)
        assertEquals("http://share.example", DirectShareConfig.parse("http://share.example:80", 8080, 5).origin)
        val ipv6 = DirectShareConfig.parse("http://[2001:db8::1]:8080", 8080, 60)
        assertTrue(ipv6.ipv6)
        assertEquals("http://[2001:db8::1]:8080", ipv6.origin)
    }

    @Test fun originComparisonHandlesIpv6SpellingButRejectsDifferentPortsOrHosts() {
        assertTrue(DirectShareConfig.sameOrigin("https://[2001:db8::1]", "https://[2001:0db8:0:0:0:0:0:1]:443"))
        assertTrue(DirectShareConfig.sameOrigin("https://Share.Example:443", "https://share.example"))
        assertFalse(DirectShareConfig.sameOrigin("http://share.example", "https://share.example"))
        assertFalse(DirectShareConfig.sameOrigin("https://share.example:8443", "https://share.example"))
        assertFalse(DirectShareConfig.sameOrigin("https://attacker.example", "https://share.example"))
        assertFalse(DirectShareConfig.sameOrigin("null", "https://share.example"))
    }

    @Test fun credentialsPathsFragmentsAndInvalidPortsAreRejected() {
        for (url in listOf("ftp://share.example", "http://user:pass@share.example",
            "http://share.example/files", "http://share.example?x=1", "http://share.example#fragment",
            "http://share.example:0", "http://share.example:65536", "http://", "http://share.example\r\nX: bad")) {
            assertTrue(url, runCatching { DirectShareConfig.parse(url, 8080, 15) }.isFailure)
        }
        for (port in listOf(-1, 0, 80, 65536)) {
            assertTrue(runCatching { DirectShareConfig.parse("https://share.example", port, 15) }.isFailure)
        }
        for (minutes in listOf(0, 1, 61)) {
            assertTrue(runCatching { DirectShareConfig.parse("https://share.example", 8080, minutes) }.isFailure)
        }
    }
}
