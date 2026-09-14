package com.zerotoship.z2term.share

import org.junit.Assert.*
import org.junit.Test

class ShareRelayConfigTest {
    @Test fun requiresAnExplicitProfileAndHttpsOrigin() {
        val config = ShareRelayConfig.parse("saved-id", " HTTPS://Share.Example:443/ ", 8080, 15)
        assertEquals("saved-id", config.profileId)
        assertEquals("https://share.example", config.origin)
        assertEquals(8080, config.remotePort)
        for (origin in listOf("", "http://share.example", "https://user:pass@share.example",
            "https://share.example/path", "https://share.example?token=secret", "https://share.example#x")) {
            assertTrue(origin, runCatching { ShareRelayConfig.parse("saved-id", origin, 8080, 15) }.isFailure)
        }
        for (id in listOf("", " ", "x".repeat(257))) {
            assertTrue(runCatching { ShareRelayConfig.parse(id, "https://share.example", 8080, 15) }.isFailure)
        }
    }

    @Test fun remotePortAndLifetimeAreBoundedIndependentlyOfHttpsPort() {
        val config = ShareRelayConfig.parse("id", "https://share.example:8443", 1024, 5)
        assertEquals(1024, config.remotePort)
        assertEquals("https://share.example:8443", config.origin)
        for (port in listOf(0, 80, 1023, 65536)) {
            assertTrue(runCatching { ShareRelayConfig.parse("id", config.origin, port, 5) }.isFailure)
        }
        for (minutes in listOf(0, 1, 61)) {
            assertTrue(runCatching { ShareRelayConfig.parse("id", config.origin, 8080, minutes) }.isFailure)
        }
    }
}
