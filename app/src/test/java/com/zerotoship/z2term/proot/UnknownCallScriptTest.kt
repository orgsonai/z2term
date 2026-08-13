package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 着信サンプル (`unknown-call.sh`) の**発信者の見分け方**を実際の `sh` で確かめる。
 *
 * ⚠ ここが要 — このマクロの答えは「電話帳に無い相手か」の 1 点で、判定を誤ると
 * **電話帳にいる相手にまで通知が出る**。通知に出るのが名前か番号かで
 * 決めているので、名前側 (かな・漢字・英字) と番号側 (区切り記号入り・国番号付き) の
 * 両方を固定する。
 *
 * ⚠ 判定は `case` の `[!...]` で書けない (パターン中の `)` が `case` の区切りに読まれて
 * 構文エラーになる)。`sh -n` を通すテストで、その書き方に戻る退行も止める。
 */
class UnknownCallScriptTest {

    private val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }

    private fun script(lang: String): String = z2MacroSamples(lang)["unknown-call.sh"]!!

    /**
     * 通知の題名 [title] / 本文 [text] / 種別 [category] を渡してサンプルを走らせ、
     * 偽の `z2-*` が受け取った引数を返す。
     */
    private fun run(
        lang: String = "ja",
        title: String = "",
        text: String = "",
        category: String = "call"
    ): String {
        val f = File.createTempFile("unknown-call", ".sh").apply { writeText(script(lang)) }
        val home = File.createTempFile("unknown-call-home", "").apply { delete(); mkdirs() }
        val bin = File(home, "bin").apply { mkdirs() }
        val trace = File(home, "trace.log")
        try {
            for (name in listOf("z2-clip", "z2-notify")) {
                File(bin, name).apply {
                    writeText("#!/bin/sh\necho \"$name \$*\" >> ${trace.absolutePath}\nexit 0\n")
                    setExecutable(true)
                }
            }
            val pb = ProcessBuilder(sh!!, f.absolutePath).redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            pb.environment()["PATH"] = bin.absolutePath + ":" + System.getenv("PATH")
            pb.environment()["Z2_WHEN_NOTI_TITLE"] = title
            pb.environment()["Z2_WHEN_NOTI_TEXT"] = text
            pb.environment()["Z2_WHEN_NOTI_CATEGORY"] = category
            val p = pb.start()
            p.inputStream.bufferedReader().readText()
            p.waitFor()
            return if (trace.exists()) trace.readText() else ""
        } finally {
            f.delete()
            home.deleteRecursively()
        }
    }

    @Test
    fun `両言語とも POSIX sh として構文が通る`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en")) {
            val f = File.createTempFile("unknown-call", ".sh").apply { writeText(script(lang)) }
            try {
                val p = ProcessBuilder(sh!!, "-n", f.absolutePath).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("unknown-call.sh ($lang): sh -n が構文エラーを報告した:\n$out", 0, p.waitFor())
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun `電話帳に無い番号は通知のコピーボタンで渡す`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (num in listOf("09012345678", "090-1234-5678", "+81 90-1234-5678", "(03) 1234-5678")) {
            val trace = run(title = num)
            assertTrue("[$num] をコピーボタンに載せていない: $trace", trace.contains("-c $num"))
        }
    }

    /**
     * ⚠ **裏から z2-clip set を呼ばないこと**。Android 10+ は前面のアプリしかクリップボードに
     * 書けないので、着信中 (前面は電話アプリ) に呼んでも黙って捨てられる。「コピーしたつもり」
     * で終わっていた退行を止める (0.8.335・実機で番号が入らなかった報告)。
     */
    @Test
    fun `直にクリップボードへ書こうとしない`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val trace = run(title = "09012345678")
        assertTrue("z2-clip set を呼んでいる (裏では届かない): $trace", !trace.contains("z2-clip set"))
    }

    /** 題名が「着信中」等で、本文側に番号が出る電話アプリでも拾えること。 */
    @Test
    fun `本文側に番号が出ていても拾う`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val trace = run(title = "着信中", text = "09012345678")
        assertTrue("本文の番号を拾えていない: $trace", trace.contains("-c 09012345678"))
    }

    @Test
    fun `電話帳にある相手は名前が出るので何もしない`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (name in listOf("山田太郎", "John Smith", "ヤマダ", "会社 (03-1234-5678)")) {
            val trace = run(title = name)
            assertEquals("[$name] で動いてはいけない: $trace", "", trace)
        }
    }

    @Test
    fun `非通知や桁の足りない表示では動かない`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (s in listOf("非通知", "不明な発信者", "Unknown caller", "", "2", "1234", "1234567890123456")) {
            val trace = run(title = s)
            assertEquals("[$s] で動いてはいけない: $trace", "", trace)
        }
    }

    /** 着信中と不在着信は、同じ番号でも知らせ方の文言が変わる (どちらで動いたか分かるように)。 */
    @Test
    fun `不在着信は着信と違う文言で知らせる`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val incoming = run(title = "09012345678", category = "call")
        val missed = run(title = "09012345678", category = "missed_call")
        assertTrue("着信の通知が出ていない: $incoming", incoming.contains("z2-notify"))
        assertTrue("不在着信の通知が出ていない: $missed", missed.contains("z2-notify"))
        val incomingNoti = incoming.lines().first { it.startsWith("z2-notify") }
        val missedNoti = missed.lines().first { it.startsWith("z2-notify") }
        assertTrue("着信と不在着信で文言が同じ: $incomingNoti", incomingNoti != missedNoti)
    }
}
