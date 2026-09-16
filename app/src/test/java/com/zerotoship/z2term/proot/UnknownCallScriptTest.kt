package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 電話番号コピー通知の生成スクリプトを実際の sh で検証する。
 * 電話帳の登録有無は判定しない。題名・本文のどちらに番号があっても拾い、
 * 名前しか無ければ通知しない。番号の書式と POSIX sh の構文も確認する。
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
            val output = p.inputStream.bufferedReader().readText()
            assertEquals("unknown-call.sh failed: $output", 0, p.waitFor())
            return if (trace.exists()) trace.readText() else ""
        } finally {
            f.delete()
            home.deleteRecursively()
        }
    }

    @Test
    fun `全言語とも POSIX sh として構文が通る`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) {
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
    fun `電話番号は通知のコピーボタンで渡す`() {
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
    fun `名前しか通知されない場合はコピー通知を出さない`() {
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
    @Test
    fun `登録名と番号が両方ある通知も番号をコピーできる`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en", "zh-CN", "zh-TW", "es", "ko")) {
            for (category in listOf("call", "missed_call")) {
                for ((title, text) in listOf("山田太郎" to "090-1234-5678", "090-1234-5678" to "John Smith")) {
                    val trace = run(lang, title, text, category)
                    assertTrue("$lang / $category / $title / $text: $trace", trace.contains("-c 090-1234-5678"))
                }
            }
        }
    }

    @Test
    fun `通知の題名は未登録と断定せず電話番号のコピーを案内する`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for ((lang, label) in mapOf(
            "ja" to "電話番号のコピー通知", "en" to "Copy phone number",
            "zh-CN" to "复制电话号码", "zh-TW" to "複製電話號碼",
            "es" to "Copiar número de teléfono", "ko" to "전화번호 복사"
        )) {
            for (category in listOf("call", "missed_call")) {
                val trace = run(lang, title = "山田太郎", text = "090-1234-5678", category = category)
                assertTrue("$lang / $category: $trace", trace.contains(": $label"))
            }
        }
    }
}
