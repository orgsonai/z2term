package com.zerotoship.z2term.automation

import com.zerotoship.z2term.proot.z2UiScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** AI 用ガイドが命令を書き漏らしていないこと、`z2-ui --help` がガイドそのものを出すこと。 */
class UiBridgeGuideTest {
    @Test fun everyCommandIsInTheGuide() {
        for (verb in UiBridgeGuide.VERBS)
            assertTrue("ガイドに z2-ui $verb が無い", Regex("z2-ui $verb(?![\\w-])").containsMatchIn(UiBridgeGuide.TEXT))
    }

    /** 受付が受ける命令とガイドの名簿を揃える (命令を足してガイドを忘れると、ここで落ちる)。 */
    @Test fun theGuideListsWhatTheDeskAccepts() {
        val whole = java.io.File("src/main/java/com/zerotoship/z2term/automation/UiBridge.kt").readText()
        // 命令を振り分けている handle() の中だけを見る (承認の文面などの分岐を拾わない)。
        val source = whole.substring(whole.indexOf("private fun handle("), whole.indexOf("private class LookFlags"))
        val accepted = Regex("^ {12}((?:\"[a-z-]+\"(?:, )?)+) ->", RegexOption.MULTILINE).findAll(source)
            .flatMap { m -> Regex("\"([a-z-]+)\"").findAll(m.groupValues[1]).map { it.groupValues[1] } }.toSet()
        assertTrue("受付の命令を読み取れていない: $accepted", "look" in accepted && "noti" in accepted)
        assertEquals(accepted - "mcp", UiBridgeGuide.VERBS.toSet() - "mcp")
    }

    @Test fun guideIsSafeToEmbed() {
        assertFalse("生文字列の中の $ は埋め込みになる", '$' in UiBridgeGuide.TEXT)
        assertTrue(UiBridgeGuide.TEXT.startsWith("# z2-ui guide for AI agents"))
    }

    @Test fun helpPrintsTheGuideVerbatim() {
        val dir = Files.createTempDirectory("ui-guide").toFile()
        try {
            val script = dir.resolve("z2-ui").apply { writeText(z2UiScript("ja")) }
            for (args in listOf(listOf("--help"), emptyList())) {
                val p = ProcessBuilder(listOf("sh", script.path) + args).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals(0, p.waitFor())
                assertEquals(UiBridgeGuide.TEXT + "\n", out)
            }
        } finally { dir.deleteRecursively() }
    }
}
