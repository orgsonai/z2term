package com.zerotoship.z2term.net

import org.junit.Assert.assertEquals
import org.junit.Test

class HostAddressTest {
    @Test fun normalizesPlainAndBracketedHostsForConnections() {
        assertEquals("example.com", HostAddress.normalize("  example.com "))
        assertEquals("2001:db8::10", HostAddress.normalize("2001:db8::10"))
        assertEquals("2001:db8::10", HostAddress.normalize("[2001:db8::10]"))
    }

    @Test fun hostPortBracketsOnlyIpv6() {
        assertEquals("example.com:22", HostAddress.hostPort("example.com", 22))
        assertEquals("192.168.1.10:445", HostAddress.hostPort("192.168.1.10", 445))
        assertEquals("[2001:db8::10]:22", HostAddress.hostPort("2001:db8::10", 22))
        assertEquals("[2001:db8::10]:22", HostAddress.hostPort("[2001:db8::10]", 22))
    }

    @Test fun authorityOmitsOnlyTheDefaultPort() {
        assertEquals("[2001:db8::10]", HostAddress.authority("2001:db8::10", 443, 443))
        assertEquals("[2001:db8::10]:8443", HostAddress.authority("2001:db8::10", 8443, 443))
    }

    @Test fun knownHostsUsesJschPortFormWithoutDoubleBrackets() {
        assertEquals("2001:db8::10", HostAddress.knownHostKey("[2001:db8::10]", 22))
        assertEquals("[2001:db8::10]:2222", HostAddress.knownHostKey("[2001:db8::10]", 2222))
    }
}
