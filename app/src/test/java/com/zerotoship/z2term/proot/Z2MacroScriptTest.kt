package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * `z2-macro` の**「入れたコピーが古い」を見つけられるか**を実際の `sh` で確かめる (0.8.332)。
 *
 * ⚠ ここが要 — `install` は既存を上書きしない (自分で書き換えた分を守るため。この判断は変えない)。
 * その代わり、**同梱版が直っても端末のコピーは黙って古いまま**になる。実際 `remind.sh` は
 * 2 週間ぶん古いコピーのまま使われ、直したはずの「通知をバナーで出す」が効いていなかった。
 * 一覧に状態が出ること・`install` が「同じ」と「違う」を言い分けることを固定する。
 */
class Z2MacroScriptTest {

    private val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }

    /**
     * 同梱サンプルの置き場は本番では `/usr/local/share/z2term/macros` 固定なので、
     * テストでは生成物の `SRC=` 行だけを差し替えて走らせる (本番へテスト用の抜け道を作らない)。
     */
    private fun scriptFor(src: File, lang: String = "ja"): String {
        val body = z2MacroScript(lang)
        val line = body.lines().first { it.startsWith("SRC=") }
        return body.replace(line, "SRC=${src.absolutePath}")
    }

    private fun run(src: File, home: File, vararg args: String): Pair<Int, String> {
        val f = File.createTempFile("z2macro", ".sh").apply { writeText(scriptFor(src)) }
        try {
            val pb = ProcessBuilder(listOf(sh!!, f.absolutePath) + args).redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        } finally {
            f.delete()
        }
    }

    /** 同梱サンプル 1 本だけの置き場と、空のホームを用意する。 */
    private fun fixture(bundled: String): Pair<File, File> {
        val src = File.createTempFile("z2macro-src", "").apply { delete(); mkdirs() }
        File(src, "demo.sh").writeText("#!/bin/sh\n# demo.sh — 見本\necho $bundled\n")
        val home = File.createTempFile("z2macro-home", "").apply { delete(); mkdirs() }
        return src to home
    }

    @Test
    fun listShowsWhetherTheCopyIsCurrent() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (src, home) = fixture("new")
        try {
            val (_, notInstalled) = run(src, home, "list")
            assertTrue("未導入と出ていない: $notInstalled", notInstalled.contains("未導入"))

            run(src, home, "install", "demo.sh")
            val (_, installed) = run(src, home, "list")
            assertTrue("導入済と出ていない: $installed", installed.contains("導入済"))

            // 端末のコピーだけ古くする (= 同梱版が直った状態と同じ)。
            File(home, ".z2term/macros/demo.sh").writeText("#!/bin/sh\n# demo.sh — 見本\necho old\n")
            val (_, outdated) = run(src, home, "list")
            assertTrue("要更新と出ていない: $outdated", outdated.contains("要更新"))
        } finally {
            src.deleteRecursively(); home.deleteRecursively()
        }
    }

    @Test
    fun installTellsSameFromDifferent() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (src, home) = fixture("new")
        try {
            run(src, home, "install", "demo.sh")

            // 同じ中身なら「同じ」と言う (次の手は要らない)。
            val (_, same) = run(src, home, "install", "demo.sh")
            assertTrue("同じだと言っていない: $same", same.contains("同じ内容がすでに入っています"))
            assertTrue("同じなのに更新を勧めている: $same", !same.contains("install -f"))

            // 違うなら**次に打つ手まで**出す。ここを「既にあります」で終わらせると気付けない。
            File(home, ".z2term/macros/demo.sh").writeText("#!/bin/sh\n# demo.sh — 見本\necho old\n")
            val (_, differs) = run(src, home, "install", "demo.sh")
            assertTrue("違いを伝えていない: $differs", differs.contains("同梱版と中身が違います"))
            assertTrue("更新の仕方が出ていない: $differs", differs.contains("z2-macro install -f demo.sh"))
            assertTrue("差分の見方が出ていない: $differs", differs.contains("z2-macro diff demo.sh"))
        } finally {
            src.deleteRecursively(); home.deleteRecursively()
        }
    }

    /** `diff` は「どちらが自分の側か」が要る。左 (`<`) が端末のコピー。 */
    @Test
    fun diffShowsYourCopyOnTheLeft() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        assumeTrue("diff が無い環境なのでスキップ", File("/usr/bin/diff").canExecute())
        val (src, home) = fixture("new")
        try {
            run(src, home, "install", "demo.sh")
            File(home, ".z2term/macros/demo.sh").writeText("#!/bin/sh\n# demo.sh — 見本\necho old\n")
            val (code, out) = run(src, home, "diff", "demo.sh")
            assertEquals("違いがあるのに 0 で終わっている:\n$out", 1, code)
            assertTrue("端末のコピーが左に出ていない:\n$out", out.contains("< echo old"))
            assertTrue("同梱版が右に出ていない:\n$out", out.contains("> echo new"))
        } finally {
            src.deleteRecursively(); home.deleteRecursively()
        }
    }

    /** `--help` は間違いではないので標準出力へ出して 0 で終わる (usage は stderr + 1)。 */
    @Test
    fun helpExitsZero() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (src, home) = fixture("new")
        try {
            val (code, out) = run(src, home, "--help")
            assertEquals("--help が 0 で終わらない:\n$out", 0, code)
            assertTrue("使い方が出ていない:\n$out", out.contains("diff <名前>"))
        } finally {
            src.deleteRecursively(); home.deleteRecursively()
        }
    }
}
