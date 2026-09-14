package com.zerotoship.z2term.qr

import org.junit.Assert.*
import org.junit.Test

class QrContentTest {
    @Test fun commandRoundTripPreservesQuotesSymbolsAndJapaneseName() {
        val text = "printf '%s' 'a&b + 日本語'"
        val result = QrContent.parse(QrContent.command("表示", text))
        assertEquals(QrContent.Kind.COMMAND, result.kind)
        assertEquals("表示", result.name)
        assertEquals(text, result.text)
    }

    @Test fun commandImportsRejectHiddenControlsAndAmbiguousFields() {
        for (text in listOf("echo ok\nreboot", "echo ok\r", "echo\tok", "echo\u001b[31m",
            "echo\u202Eok", "echo\u2028ok", "echo\u0000ok")) {
            assertFalse(QrContent.singleLine(text))
            assertTrue(runCatching { QrContent.command("", text) }.isFailure)
        }
        for (uri in listOf("z2term://command?text=ok&text=bad", "z2term://command?text=ok%0Areboot",
            "z2term://command?text=ok&execute=1", "z2term://command?name=x",
            "z2term://command?text=ok&name=%1B", "z2term://command?text=ok#run")) {
            assertTrue(uri, runCatching { QrContent.parse(uri) }.isFailure)
        }
        assertTrue(runCatching { QrContent.parse("a".repeat(QrContent.MAX_TEXT + 1)) }.isFailure)
    }

    @Test fun sshExportsOnlyAnEndpointAndImportsNeverAcceptPasswords() {
        val result = QrContent.parse(QrContent.ssh("2001:db8::1", 2222, "demo"))
        assertEquals(QrContent.Kind.SSH, result.kind)
        assertEquals("2001:db8::1", result.host)
        assertEquals(2222, result.port)
        assertEquals("demo", result.user)
        assertEquals("", QrContent.parse("ssh://host.example").user)
        for (uri in listOf("ssh://user:password@host.example", "ssh://user%0A@host.example",
            "ssh://user@host.example:65536", "ssh://user@host.example/run",
            "ssh://user@host.example?command=reboot")) {
            assertTrue(uri, runCatching { QrContent.parse(uri) }.isFailure)
        }
    }

    @Test fun onlyHttpUrlsOfferBrowserNavigation() {
        assertEquals(QrContent.Kind.URL, QrContent.parse("https://example.org/a?b=c").kind)
        for (value in listOf("javascript:alert(1)", "intent://example", "file:///sdcard/example",
            "https://user:pass@example.org", "普通のテキスト")) {
            assertEquals(QrContent.Kind.TEXT, QrContent.parse(value).kind)
        }
    }
}
