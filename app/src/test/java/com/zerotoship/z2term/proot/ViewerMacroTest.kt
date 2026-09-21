package com.zerotoship.z2term.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Execute generated shell with Android operations stubbed; no real notifications or reservations. */
class ViewerMacroTest {
    private class Fixture(lang: String = "ja") : AutoCloseable {
        val home = Files.createTempDirectory("viewer-macro").toFile()
        val bin = File(home, "bin").apply { mkdirs() }
        val data = File(home, ".z2term/remind").apply { mkdirs() }
        val macro = File(home, ".z2term/macros/remind.sh").apply {
            parentFile!!.mkdirs(); writeText(z2MacroSamples(lang).getValue("remind.sh"))
        }
        init {
            script("z2-view", """
                test "${'$'}1" = --controls || exit 9
                cp "${'$'}2" "${'$'}HOME/controls.json"
                cp "${'$'}3" "${'$'}HOME/page.html"
            """.trimIndent())
            script("z2-alarm", """
                printf '%s\n' "alarm ${'$'}*" >> "${'$'}HOME/calls"
                test "${'$'}FAIL_ALARM" != 1
            """.trimIndent())
            script("z2-when", """
                printf '%s\n' "when ${'$'}*" >> "${'$'}HOME/calls"
                case ${'$'}1 in list) exit 0;; remove) exit 0;; *) echo rule1;; esac
            """.trimIndent())
            script("z2-toast", ":")
        }
        fun script(name: String, text: String) = File(bin, name).apply { writeText("#!/bin/sh\n$text\n"); setExecutable(true) }
        fun run(vararg args: String, failAlarm: Boolean = false): Pair<Int, String> {
            val p = ProcessBuilder(listOf("sh", macro.path) + args).apply {
                environment()["HOME"] = home.path
                environment()["PATH"] = bin.path + ":" + System.getenv("PATH")
                environment()["FAIL_ALARM"] = if (failAlarm) "1" else "0"
            }.redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        }
        fun jsonCheck(program: String) {
            val p = ProcessBuilder("python3", "-c", "import json,sys; d=json.load(open(sys.argv[1])); $program", File(home, "controls.json").path)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            assertEquals(out, 0, p.waitFor())
        }
        override fun close() { home.deleteRecursively() }
    }

    @Test fun emptyViewAndTranslatedFormsUseTheGenericProtocol() {
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) Fixture(lang).use { f ->
            val result = f.run("view")
            assertEquals(result.second, 0, result.first)
            f.jsonCheck("assert d['handler']=='remind.sh'; assert d['actions'][0]['args']==['view-add']; assert len(d['actions'][0]['fields'])==4")
            assertTrue(File(f.home, "page.html").readText().contains("</html>"))
        }
    }

    @Test fun untrustedTextIsEscapedAndDeleteActionsCarryStableIds() = Fixture().use { f ->
        File(f.data, "123.txt").writeText("once\ttomorrow\t-\t<script>\" & ${'$'}(touch X)\nsecond line\n")
        File(f.data, "456.txt").writeText("fired\tyesterday\t-\tdone\n")
        val result = f.run("view")
        assertEquals(result.second, 0, result.first)
        val html = File(f.home, "page.html").readText()
        assertTrue(html.contains("&lt;script&gt;&quot; &amp;"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("通知済み"))
        f.jsonCheck("a=next(a for a in d['actions'] if a['id']=='delete-123'); assert a['args']==['view-delete','123']; assert '<script>' in a['confirm']")
    }

    @Test fun deletingRepeatCancelsTheRuleAndAnySnoozeAndKeepsOtherRows() = Fixture().use { f ->
        File(f.data, "123.txt").writeText("repeat\tdaily\trule1\tfirst\n")
        File(f.data, "456.txt").writeText("once\tlater\t-\tsecond\n")
        val result = f.run("view-delete", "123")
        assertEquals(result.second, 0, result.first)
        assertFalse(File(f.data, "123.txt").exists())
        assertTrue(File(f.data, "456.txt").exists())
        val calls = File(f.home, "calls").readText()
        assertTrue(calls.contains("when remove rule1")); assertTrue(calls.contains("alarm cancel r123"))
        assertNotEquals(0, f.run("view-delete", "all").first)
        assertNotEquals(0, f.run("view-delete", "../456").first)
    }

    @Test fun cancellationFailureKeepsTheReminderVisible() = Fixture().use { f ->
        val file = File(f.data, "123.txt").apply { writeText("once\tlater\t-\tkeep\n") }
        assertNotEquals(0, f.run("view-delete", "123", failAlarm = true).first)
        assertTrue(file.exists())
    }

    @Test fun formAddsOnceAndRepeatAndTreatsTextAsData() = Fixture().use { f ->
        val text = "quote '\" ${'$'}(touch ${f.home}/injected) <p>"
        val once = f.run("view-add", text, "209901012130", "once", "")
        assertEquals(once.second, 0, once.first)
        val repeat = f.run("view-add", "repeat", "209901012130", "weekly", "")
        assertEquals(repeat.second, 0, repeat.first)
        assertFalse(File(f.home, "injected").exists())
        val records = f.data.listFiles()!!.filter { it.extension == "txt" }.map { it.readText() }
        assertEquals(2, records.size)
        assertTrue(records.any { it.startsWith("once\t") && it.contains(text) })
        assertTrue(records.any { it.startsWith("repeat\t") })
        f.jsonCheck("assert len(d['actions'])==3")
    }

    @Test fun textScheduleLeavesGlobbingAvailableForRendering() = Fixture().use { f ->
        val result = f.run("view-add", "one", "209901012130", "once", "30m")
        assertEquals(result.second, 0, result.first)
        f.jsonCheck("assert len(d['actions'])==2")
        val before = f.data.listFiles()!!.count { it.extension == "txt" }
        assertNotEquals(0, f.run("view-add", "bad", "209901012130", "once", "not-a-time").first)
        assertEquals(before, f.data.listFiles()!!.count { it.extension == "txt" })
    }

    @Test fun rssViewUsesSavedArticlesAndEscapesTheirTitlesWithoutFetching() = Fixture().use { f ->
        f.macro.writeText(z2MacroSamples("ja").getValue("rss.sh"))
        val rss = File(f.home, ".z2term/rss").apply { mkdirs() }
        File(rss, "articles.tsv").writeText("0\thttps://example.test/a\t<script> & title\n0\tjavascript:bad\tnot allowed\n")
        val result = f.run("view")
        assertEquals(result.second, 0, result.first)
        f.jsonCheck("assert d['handler']=='rss.sh'; assert d['refresh']==['refresh-view']")
        val html = File(f.home, "page.html").readText()
        assertTrue(html.contains("&lt;script&gt; &amp; title"))
        assertTrue(html.contains("https://example.test/a"))
        assertFalse(html.contains("javascript:bad"))
        assertFalse(File(rss, "feeds.txt").exists())
        assertFalse(File(f.home, "calls").exists())
        File(rss, "articles.tsv").delete()
        assertEquals(0, f.run("view").first)
        assertTrue(File(f.home, "page.html").readText().contains("</html>"))
    }

    @Test fun independentExampleMacroUsesTheSameFormProtocolAndKeepsInputLiteral() = Fixture().use { f ->
        var root = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile ?: error("Repository not found")
        f.macro.writeText(File(root, "examples/view-form.sh").readText())
        val text = "<script>& ${'$'}(touch ${f.home}/injected)\nsecond line"
        val result = f.run("save", text, "work")
        assertEquals(result.second, 0, result.first)
        assertEquals(text + "\n", File(f.home, ".z2term/view-form/text.txt").readText())
        assertFalse(File(f.home, "injected").exists())
        assertTrue(File(f.home, "page.html").readText().contains("&lt;script&gt;&amp;"))
        f.jsonCheck("assert d['handler']=='view-form.sh'; assert d['actions'][0]['args']==['save']")
        assertEquals(0, f.run("clear").first)
        assertFalse(File(f.home, ".z2term/view-form/text.txt").exists())
    }
}
