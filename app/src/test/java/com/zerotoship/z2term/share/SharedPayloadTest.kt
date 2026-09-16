package com.zerotoship.z2term.share

import com.zerotoship.z2term.service.WhenTriggerMatch
import org.junit.Assert.*
import org.junit.Test

class SharedPayloadTest {
    @Test fun longUnicodeFilenamesFitTheFilesystemWithoutSplittingCharacters() {
        val name = "写真😀".repeat(100)
        val limited = SharedPayload.limitFileName(name)
        assertTrue(limited.toByteArray(Charsets.UTF_8).size <= 220)
        assertTrue(name.startsWith(limited))
        assertEquals(limited, limited.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertEquals("report.pdf", SharedPayload.limitFileName("report.pdf"))
    }

    @Test fun mixedSharesRetainTextAndMatchBothKinds() {
        val paths = listOf("z2term-inbox/id/one.png")
        val kind = SharedPayload.kind("caption and https://example.test", paths)
        assertEquals("mixed", kind)
        assertEquals("caption and https://example.test", SharedPayload.insertion("caption and https://example.test", paths))
        for (spec in listOf("any", "text", "file", "ext=png", "contains=caption"))
            assertTrue(spec, WhenTriggerMatch.share(spec, kind, "caption", listOf("one.png")))
        assertFalse(WhenTriggerMatch.share("contains=one", "file", "one.png", listOf("one.png")))
    }

    @Test fun selectedCommandReceivesDataAndAnAbsoluteReceiptPath() {
        val text = "x' \$(printf injected)\nsecond line"
        val command = SharedPayload.command("printf '%s|%s' \"\$Z2_SHARE_TEXT\" \"\$Z2_SHARE_MANIFEST\"", text, "z2term-inbox/id/manifest.json")
        val process = ProcessBuilder("sh", "-c", command).apply { environment()["HOME"] = "/tmp/test home" }.start()
        assertEquals("$text|/tmp/test home/z2term-inbox/id/manifest.json", process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
    }
}
