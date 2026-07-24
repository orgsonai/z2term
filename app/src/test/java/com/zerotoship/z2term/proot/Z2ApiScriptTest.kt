package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `z2-*` ヘルパー (rootfs 側 CLI) の生成物を検証する。
 *
 * これらは rootfs に書き出されて端末から直接叩かれるだけで、アプリ側からは中身が見えない。
 * 壊れていても「そのコマンドだけが動かない」としか現れないので、生成した時点で
 * **実際の `sh` に構文を見てもらう**。
 */
class Z2ApiScriptTest {

    private val scripts = z2ApiScripts()

    /** 行頭の `|` (trimMargin の剥がし漏れ) は POSIX sh では常に構文エラー (0.8.187 の事故)。 */
    @Test
    fun noMarginLeak() {
        for ((name, body) in scripts) {
            val bad = body.lines().withIndex().filter { (_, line) -> line.startsWith("|") }
            assertTrue(
                "$name: 行頭に trimMargin のマージン `|` が残っている " +
                    bad.joinToString { "(line ${it.index + 1}) ${it.value}" },
                bad.isEmpty()
            )
        }
    }

    /** すべてのヘルパーが `sh -n` を通ること (sh が無い環境ではスキップ)。 */
    @Test
    fun allScriptsAreValidPosixShell() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for ((name, body) in scripts) {
            val tmp = File.createTempFile("z2script", ".sh")
            try {
                tmp.writeText(body)
                val proc = ProcessBuilder(sh!!, "-n", tmp.absolutePath).redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText()
                val rc = proc.waitFor()
                assertEquals("$name: sh -n が構文エラーを報告した:\n$output", 0, rc)
            } finally {
                tmp.delete()
            }
        }
    }

    /** すべてシェバンで始まり、改行で終わること (書き出してそのまま実行されるため)。 */
    @Test
    fun allScriptsStartWithShebang() {
        for ((name, body) in scripts) {
            assertTrue("$name: シェバンが無い", body.startsWith("#!/bin/sh\n"))
            assertTrue("$name: 改行で終わっていない", body.endsWith("\n"))
        }
    }

    /**
     * A6: `z2-when` を**続けて実行してもルール id が衝突しない**こと (0.8.211 の回帰テスト)。
     *
     * 元バグ: id が `w<epoch><awk の乱数>` だったが、awk の `srand()` は**秒**で seed されるため
     * 同一秒では乱数まで同値になり、`w<epoch><乱数>` が丸ごと一致した。結果、続けて登録した
     * ルールが**同じファイルへ黙って上書き**され、7 個登録して 3 個しか残らない事故になった
     * (実機で確認)。セットアップスクリプトで複数ルールを並べるのは普通の使い方なので実害が大きい。
     */
    @Test
    fun whenRuleIdsDoNotCollide() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val script = File.createTempFile("z2when", ".sh").apply { writeText(scripts["z2-when"]!!) }
        val home = Files.createTempDirectory("z2home").toFile()
        val want = 5
        try {
            // 同一秒に収まる速さで連続登録する (衝突していれば 1 件しか残らない)。
            repeat(want) { i ->
                val pb = ProcessBuilder(sh!!, script.absolutePath, "charge:start", "run", "echo $i")
                    .redirectErrorStream(true)
                pb.environment()["HOME"] = home.absolutePath
                val proc = pb.start()
                proc.inputStream.bufferedReader().readText()
                proc.waitFor()
            }
            val rules = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }.orEmpty()
            assertEquals(
                "連続登録したルールが上書きされている (id 衝突): 残ったのは " +
                    rules.joinToString { it.name },
                want, rules.size
            )
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /** A1: タブを操る `z2-session` が同梱され、5 つのサブコマンドを持つこと。 */
    @Test
    fun sessionHelperCoversAllSubcommands() {
        val body = scripts["z2-session"]
        assertTrue("z2-session が同梱されていない", body != null)
        for (sub in listOf("list", "new", "send", "capture", "close")) {
            assertTrue("z2-session に $sub が無い", body!!.contains("session $sub"))
        }
    }

    /**
     * `z2-session send` が**勝手に実行しない**こと。
     *
     * 引数をそのままブリッジへ渡すだけで、ヘルパー側が改行や `--enter` を足さないのが約束。
     * ここが崩れると「文字を置いただけのつもりが他のタブでコマンドが走る」ことになる。
     */
    @Test
    fun sessionSendDoesNotExecuteByItself() {
        val body = scripts["z2-session"]!!
        val sendLine = body.lines().first { it.contains("z2api 1 session send") }
        assertTrue(
            "send の行が --enter を勝手に付けている: $sendLine",
            !sendLine.replace("\"\$@\"", "").contains("--enter")
        )
        assertTrue(
            "send が引数をそのまま渡していない: $sendLine",
            sendLine.contains("\"\$@\"")
        )
    }
}
