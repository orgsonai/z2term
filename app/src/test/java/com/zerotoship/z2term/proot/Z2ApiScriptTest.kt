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

    /** 英語版。文言だけを差し替える作りなので、**両方**が同じ検証を通らないと意味がない。 */
    private val scriptsEn = z2ApiScripts(lang = "en")

    /** 行頭の `|` (trimMargin の剥がし漏れ) は POSIX sh では常に構文エラー (0.8.187 の事故)。 */
    @Test
    fun noMarginLeak() {
        for ((name, body) in scripts + scriptsEn.mapKeys { "${it.key} (en)" }) {
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
        for ((name, body) in scripts + scriptsEn.mapKeys { "${it.key} (en)" }) {
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
        for ((name, body) in scripts + scriptsEn.mapKeys { "${it.key} (en)" }) {
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

    /**
     * A6: 絞り込み (`if=` / `cooldown=` / `between=` / `days=`・0.8.259) をルールファイルへ書くこと。
     *
     * ここは**書式の合意**そのもの — 絞り込みはトリガーの直後に置き、`run` の後ろは全部コマンド。
     * 取り違えると「コマンドの一部が絞り込みとして食われる」か「絞り込みが黙って無視される」に
     * なり、どちらも実行時まで気付けない。実際に `sh` で走らせて、書かれたファイルで確かめる。
     */
    @Test
    fun whenFiltersAreWrittenToTheRuleFile() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val script = File.createTempFile("z2when", ".sh").apply { writeText(scripts["z2-when"]!!) }
        val home = Files.createTempDirectory("z2home").toFile()
        try {
            fun run(vararg args: String): String {
                val pb = ProcessBuilder(listOf(sh!!, script.absolutePath) + args).redirectErrorStream(true)
                pb.environment()["HOME"] = home.absolutePath
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                return out
            }

            run(
                "charge:start", "if=ssid=Home,!screen", "cooldown=1h",
                "between=22:00-07:00", "days=mon-fri", "run", "echo hi", "there"
            )
            val rule = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                .orEmpty().single().readText()
            assertTrue("if= が書かれていない: $rule", "if=ssid=Home,!screen\n" in rule)
            assertTrue("cooldown= が書かれていない: $rule", "cooldown=1h\n" in rule)
            assertTrue("between= が書かれていない: $rule", "between=22:00-07:00\n" in rule)
            assertTrue("days= が書かれていない: $rule", "days=mon-fri\n" in rule)
            // run の後ろは**全部**コマンド (空白で切れていない)。
            assertTrue("コマンドが欠けている: $rule", "run=echo hi there\n" in rule)

            // 絞り込みを付けなければ今までどおりの 3 行のまま (既存ルールの見え方を変えない)。
            run("charge:stop", "run", "echo plain")
            val plain = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                .orEmpty().map { it.readText() }.single { "echo plain" in it }
            assertTrue("余計な行が付いている: $plain", plain.lines().none { it.startsWith("if=") })

            // 一覧では絞り込みが末尾に出る (端末でそのまま打ち直せる表記のまま)。
            val list = run("list")
            assertTrue("list に絞り込みが出ていない: $list", "[if=ssid=Home,!screen" in list)

            // 知らない条件キーは登録の時点で弾く (実行時に黙って不成立になるより早く気付ける)。
            val bad = run("charge:start", "if=batery<30", "run", "echo nope")
            assertTrue("知らない if= キーが弾かれていない: $bad", "batery" in bad)
            assertTrue(
                "弾いたのにルールが作られている",
                File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                    .orEmpty().none { "echo nope" in it.readText() }
            )
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /**
     * A6: `z2-when events` が `event:<名前>` に書ける名前を実際に一覧すること (0.8.226)。
     *
     * 一覧はヒアドキュメントで持っているので、`|` の剥がれ方や終端 (`EOS`) の位置がずれると
     * **黙って空になる**か、以降のスクリプトごと壊れる。`sh -n` は通ってしまうケースがあるため、
     * 実際に走らせて中身が出ることまで見る。名前が変わったらこのテストも直すこと
     * (= 名前は CLI と実装の合意なので、勝手に変えられると困る、という意思表示でもある)。
     */
    @Test
    fun whenEventsListsEventNames() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val script = File.createTempFile("z2when", ".sh").apply { writeText(scripts["z2-when"]!!) }
        val home = Files.createTempDirectory("z2home").toFile()
        try {
            val pb = ProcessBuilder(sh!!, script.absolutePath, "events").redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals("z2-when events が失敗した:\n$out", 0, proc.waitFor())
            // 受動的なイベント (検知 ON が前提) と、自分で仕掛けるもの (検知に依存しない) の両方。
            for (name in listOf("screen_on", "headset_plugged", "ringer_silent", "alarm", "notify_action")) {
                assertTrue("z2-when events に $name が出ていない:\n$out", out.contains(name))
            }
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /**
     * `z2-screen` の**時間の読み取り**が正しいこと。
     *
     * 1h/30m/90s の秒への変換だけが sh 側の仕事で、あとはアプリ側 ([ScreenTimeout]) が持つ。
     * ここを間違えると「1 時間のつもりが 1 秒」のように**掛かったように見えて掛かっていない**
     * ので、実際に `sh` で走らせてブリッジへ渡る引数そのものを見る (`z2api` はスタブに差し替える)。
     * 先頭 0 (`05m`) を確かめるのは、`$(())` が "05" を 8 進と解釈する実装があるため。
     */
    @Test
    fun screenParsesDurations() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val dir = Files.createTempDirectory("z2screen").toFile()
        val stub = File(dir, "z2api").apply {
            writeText("#!/bin/sh\necho \"ARGS: \$*\"\n")
            setExecutable(true)
        }
        // 生成物そのものを使い、呼び先だけスタブへ向ける (ロジックには手を触れない)。
        val script = File(dir, "z2-screen").apply {
            writeText(scripts["z2-screen"]!!.replace("/usr/local/bin/z2api", stub.absolutePath))
        }
        fun run(vararg args: String): String {
            val proc = ProcessBuilder(listOf(sh!!, script.absolutePath) + args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            return out
        }
        try {
            assertEquals("ARGS: 1 screen keepon 3600", run("keepon", "1h"))
            assertEquals("ARGS: 1 screen keepon 1800", run("keepon", "30m"))
            assertEquals("ARGS: 1 screen keepon 90", run("keepon", "90s"))
            // 単位なしは秒 (z2-alarm in と同じ約束)。
            assertEquals("ARGS: 1 screen keepon 90", run("keepon", "90"))
            assertEquals("ARGS: 1 screen keepon 300", run("keepon", "05m"))
            // 期限を待たずに戻す / 状態を見る。
            assertEquals("ARGS: 1 screen off", run("keepon", "off"))
            assertEquals("ARGS: 1 screen status", run("status"))
            assertEquals("ARGS: 1 screen status", run())
            // 読めない時間は**掛けずに**usage を出す (黙って 0 秒や 1 秒で掛けない)。
            assertTrue("時間が無いのに掛かっている: ${run("keepon")}", run("keepon").contains("usage:"))
            assertTrue(
                "読めない時間で掛かっている: ${run("keepon", "banana")}",
                run("keepon", "banana").contains("usage:")
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * `z2-tile set` が**表示名とコマンドを取り違えない**こと。
     *
     * `-l/--label` は「コマンドの後ろに足したくなる」ものなので、頭だけ見る作りにすると
     * `z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない` の `-l 消灯しない` が**コマンドの一部**に
     * 混ざり、タイルを押すたびに存在しない引数付きで走る。位置に関わらず拾えることを実際の `sh` で押さえる。
     */
    @Test
    fun tileSetSeparatesLabelFromCommand() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val dir = Files.createTempDirectory("z2tile").toFile()
        val stub = File(dir, "z2api").apply {
            // 引数の境目が見えるように 1 引数 1 行で出す (空白を含むコマンドを確かめるため)。
            writeText("#!/bin/sh\nfor a in \"\$@\"; do echo \"[\$a]\"; done\n")
            setExecutable(true)
        }
        val script = File(dir, "z2-tile").apply {
            writeText(scripts["z2-tile"]!!.replace("/usr/local/bin/z2api", stub.absolutePath))
        }
        fun run(vararg args: String): String {
            val proc = ProcessBuilder(listOf(sh!!, script.absolutePath) + args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            return out
        }
        try {
            assertEquals("[1]\n[tile]\n[set]\n[1]\n[backup.sh]\n[]", run("set", "1", "backup.sh"))
            // 表示名が後ろでも前でも、コマンドは同じに読めること。
            val expected = "[1]\n[tile]\n[set]\n[2]\n[z2-screen keepon 1h]\n[消灯しない]"
            assertEquals(expected, run("set", "2", "z2-screen", "keepon", "1h", "-l", "消灯しない"))
            assertEquals(expected, run("set", "2", "-l", "消灯しない", "z2-screen", "keepon", "1h"))
            assertEquals("[1]\n[tile]\n[list]", run())
            assertEquals("[1]\n[tile]\n[clear]\n[all]", run("clear", "all"))
            // コマンドが無い / 枠だけ / 未知のサブコマンドは usage で終わり、割り当てに行かない。
            for (args in listOf(listOf("set"), listOf("set", "1"), listOf("bogus"))) {
                val out = run(*args.toTypedArray())
                assertTrue("usage が出ていない (${args.joinToString(" ")}): $out", out.contains("usage:"))
            }
        } finally {
            dir.deleteRecursively()
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
