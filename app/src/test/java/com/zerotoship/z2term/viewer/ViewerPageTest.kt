package com.zerotoship.z2term.viewer

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ViewerPageTest {
    @Test fun multilineTitlesAndHtmlRoundTripWithoutMixingPages() {
        val page = ViewerPage("<!doctype html>\n<p>日本語 &amp; 🐈</p>", "題名\nwith = and + /")
        assertEquals(page, ViewerPage.decode(page.encode()))
        val other = ViewerPage("<h1>other</h1>", "other")
        assertEquals(other, ViewerPage.decode(other.encode()))
        assertEquals(page, ViewerPage.decode(page.encode()))
    }

    @Test fun limitCountsBytesAndRejectsOversizeInsteadOfTruncating() {
        val file = File.createTempFile("viewer-limit", ".html")
        try {
            file.writeText("あいう")
            assertEquals("あいう", ViewerPage.read(file, 9))
            assertThrows(IllegalArgumentException::class.java) { ViewerPage.read(file, 8) }
            file.writeText("")
            assertEquals("", ViewerPage.read(file, 0))
        } finally { file.delete() }
    }

    @Test fun textBecomesAnEscapedPageWithoutColourCodes() {
        val page = ViewerPage.fromText("\u001B[1;31mred\u001B[0m <b>&\r\nnext\u001B]0;title\u0007\u0001line")
        assertTrue(page.contains("<pre>red &lt;b&gt;&amp;\nnextline</pre>"))
        assertFalse(page.contains("<b>"))
        assertFalse(page.contains("\u001B"))
    }

    @Test fun commandOutputWithBrokenUtf8IsStillShown() {
        val file = File.createTempFile("viewer-text", ".txt")
        try {
            file.writeBytes(byteArrayOf(0x61, 0xFF.toByte(), 0x62))
            assertThrows(Exception::class.java) { ViewerPage.read(file, 10) }
            assertEquals("a\uFFFDb", ViewerPage.read(file, 10, lenient = true))
        } finally { file.delete() }
    }
}
