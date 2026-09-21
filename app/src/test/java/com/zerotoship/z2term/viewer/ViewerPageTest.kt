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
}
