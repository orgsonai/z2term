package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * すべての `z2-*` が `--help` で**先頭のヘルプコメントを出す**こと (0.8.331)。
     *
     * 元バグ: ヘルプ本文は各スクリプトの冒頭に `#` コメントで入っていたのに、それを表示する
     * 手段がどこにも無く、`z2-tile help` は 1 行の usage を返して終わっていた
     * (`cat $(command -v z2-tile)` しか読む方法が無かった)。
     *
     * ⚠ **冒頭だけ出て合格にしない**。取り出しは awk で「2 行目から最初のコード行まで」を拾う
     * 作りなので、途中で止まっても「ヘルプは出ている」ように見える。ここでは Kotlin 側で数え直した
     * 全文と**1 行ずつ突き合わせる** (z2-macro が 0.8.286 まで冒頭 3 行しか出していなかった罠)。
     */
    @Test
    fun everyScriptPrintsItsHelp() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for ((name, body) in scripts + scriptsEn.mapKeys { "${it.key} (en)" }) {
            // z2api は内部用ディスパッチャで、人が直接叩くものではない。
            if (name.startsWith("z2api")) continue
            // シェバンの次から、最初のコード行までが本文 (`# ` を剥がしたもの)。
            val want = body.lines().drop(1).takeWhile { it.startsWith("#") }
                .map { it.removePrefix("#").removePrefix(" ") }
            assertTrue("$name: 先頭にヘルプコメントが無い", want.isNotEmpty())
            val tmp = File.createTempFile("z2help", ".sh")
            try {
                tmp.writeText(body)
                val proc = ProcessBuilder(sh!!, tmp.absolutePath, "--help").redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                val rc = proc.waitFor()
                assertEquals("$name: --help が正常終了しない:\n$out", 0, rc)
                assertEquals("$name: --help の出力が先頭コメントと違う", want, out.trimEnd('\n').lines())
            } finally {
                tmp.delete()
            }
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
     * A6: 絞り込み (`if=` / `cooldown=` / `between=` / `days=`・0.8.263) をルールファイルへ書くこと。
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
     * A6: 名前 (`name=`・0.8.303) をルールファイルへ書き、`list` にも出すこと。
     *
     * 名前は絞り込みと**同じ位置** (トリガーの直後・`run` より前) に置く。取り違えると名前が
     * コマンドの一部として食われるか、逆にコマンドの一部が名前として食われる。空白を含む
     * 文字列を渡して、`run` の後ろが丸ごとコマンドのままであることまで確かめる。
     */
    @Test
    fun whenNameIsWrittenAndListed() {
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

            run("time:daily=07:00", "name=朝の日報", "cooldown=1h", "run", "echo hi", "there")
            val rule = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                .orEmpty().single().readText()
            assertTrue("name= が書かれていない: $rule", "name=朝の日報\n" in rule)
            assertTrue("コマンドが欠けている: $rule", "run=echo hi there\n" in rule)

            // 名前を付けなければ `name=` 行は付かない (既存ルールに余計な行を足さない)。
            run("charge:stop", "run", "echo plain")
            val plain = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                .orEmpty().map { it.readText() }.single { "echo plain" in it }
            assertTrue("余計な name= が付いている: $plain", plain.lines().none { it.startsWith("name=") })

            // 一覧では名前が絞り込みと同じ末尾の [] に出る (カラム数は増やさない)。
            val list = run("list")
            assertTrue("list に名前が出ていない: $list", "[name=朝の日報" in list)
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /**
     * A6: 「どれか満たす」(`if_any=`) と「そうでないとき」(`else=`) をルールファイルへ書くこと (0.8.372)。
     *
     * ⚠ **`else=` は `run` の手前**に置く。`run` の後ろは全部コマンドという読み方は変えないので、
     * 位置を間違えると else がコマンドの一部として食われる (逆も同じ)。else の中身は空白も引用符も
     * 含むコマンドなので、1 引数のまま書かれることまで確かめる。
     */
    @Test
    fun whenIfAnyAndElseAreWritten() {
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
            fun rules() = File(home, ".z2term/when").listFiles { f -> f.name.endsWith(".rule") }
                .orEmpty().map { it.readText() }

            run(
                "time:daily=07:00", "if=charging", "if_any=wifi,ssid=Home",
                "else=z2-notify \"見送りました\"", "run", "sync.sh", "--now",
            )
            val rule = rules().single()
            assertTrue("if= が書かれていない: $rule", "if=charging\n" in rule)
            assertTrue("if_any= が書かれていない: $rule", "if_any=wifi,ssid=Home\n" in rule)
            assertTrue("else= が書かれていない: $rule", "else=z2-notify \"見送りました\"\n" in rule)
            // ⚠ run の後ろは丸ごとコマンド。else に食われていないこと。
            assertTrue("コマンドが欠けている: $rule", "run=sync.sh --now\n" in rule)

            // 付けなければその行は付かない (既存ルールに余計な行を足さない)。
            run("charge:stop", "run", "echo plain")
            val plain = rules().single { "echo plain" in it }
            assertTrue(
                "余計な行が付いている: $plain",
                plain.lines().none { it.startsWith("if_any=") || it.startsWith("else=") },
            )

            // if_any= のキーも if= と同じ語彙で検査する (打ち間違いを登録の時点で止める)。
            val before = rules().size
            val out = run("boot", "if_any=wifi,nosuchkey", "run", "echo x")
            assertTrue("知らないキーを弾いていない: $out", out.isNotBlank())
            assertEquals("弾いたのにルールが増えた", before, rules().size)
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /**
     * A6: 綴り違いのトリガーを**登録の時点で**弾くこと (0.8.265)。
     *
     * トリガーが 1 文字違っても登録は成功し、**一度も発火しないルール**ができるだけだった。
     * `z2-when list` にも普通に並ぶので、外から見て正しいルールと区別が付かない。`if=` のキーを
     * 登録時に検査しているのと同じ理由でここも止める。実際に `sh` で走らせて、**弾いたときに
     * ルールファイルが作られていないこと**まで確かめる (エラーを出しつつ登録されていたら無意味)。
     */
    @Test
    fun whenRejectsMisspelledTriggersAtRegistration() {
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
            fun rules() = File(home, ".z2term/when")
                .listFiles { f -> f.name.endsWith(".rule") }.orEmpty().map { it.readText() }

            // 種別そのものが違う。
            assertTrue("知らない種別が弾かれていない", "nework" in run("nework:online", "run", "echo x"))
            // 種別は合っているが引数の綴りが違う (一番起きやすい)。
            assertTrue("net の引数違いが弾かれていない", run("net:onlien", "run", "echo x").isNotBlank())
            assertTrue("charge の引数違いが弾かれていない", run("charge:begin", "run", "echo x").isNotBlank())
            // 引数を取らない boot に引数を付けた / 引数が要るものが空。
            assertTrue("boot の余計な引数が弾かれていない", run("boot:now", "run", "echo x").isNotBlank())
            assertTrue("空の file: が弾かれていない", run("file:new=", "run", "echo x").isNotBlank())
            assertTrue("share の引数違いが弾かれていない", run("share:txet", "run", "echo x").isNotBlank())
            assertTrue("弾いたのにルールが作られている: ${rules()}", rules().none { "echo x" in it })

            // 正しい書き方は今までどおり通ること (検査が厳しすぎて実用を壊していない)。
            listOf(
                "boot", "net:online", "net:mobile", "charge:start", "battery:below=20",
                "time:cron=0 3 * * *", "wifi:ssid=Home", "sms:otp", "notify:pkg=mail",
                "notify:category=call", "notify:category=missed_call",
                "sensor:light>500", "sensor:proximity=near", "file:new=/sdcard/Download",
                "share:any", "share:text", "share:ext=pdf", "event:ringer_*",
            ).forEach { trigger ->
                run(trigger, "run", "echo ok-$trigger")
                assertTrue(
                    "正しいトリガーが弾かれた: $trigger (${run("list")})",
                    rules().any { "echo ok-$trigger" in it },
                )
            }
        } finally {
            script.delete()
            home.deleteRecursively()
        }
    }

    /**
     * `z2-ask` が**引数を検査してから**アプリを呼ぶこと (0.8.267)。
     *
     * `-t` は待ち時間 (秒) をそのまま `Z2API_WAIT` の算術に渡すので、数字でない値が来ると
     * `${'$'}((secs*10))` が sh によっては構文エラーで落ち、**質問が出ないまま失敗する**。
     * 質問が出ないのに理由も出ない、が一番困るので、呼ぶ前に弾いて usage を出す。
     * ここは `z2api` が無い環境でも通る (弾く側は exec の手前で終わるため)。
     */
    @Test
    fun askValidatesArgumentsBeforeCallingTheApp() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val script = File.createTempFile("z2ask", ".sh").apply { writeText(scripts["z2-ask"]!!) }
        try {
            fun run(vararg args: String): Pair<Int, String> {
                val pb = ProcessBuilder(listOf(sh!!, script.absolutePath) + args).redirectErrorStream(true)
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText()
                return proc.waitFor() to out
            }
            // 質問が無い。
            val (rcNoArg, outNoArg) = run()
            assertEquals("質問なしが弾かれていない: $outNoArg", 1, rcNoArg)
            assertTrue("usage が出ていない: $outNoArg", "z2-ask" in outNoArg)
            // -t が数字でない / 0。
            assertEquals("-t の非数値が弾かれていない", 1, run("-t", "abc", "質問").first)
            assertEquals("-t 0 が弾かれていない", 1, run("-t", "0", "質問").first)
            assertEquals("-t の負値が弾かれていない", 1, run("-t", "-5", "質問").first)
        } finally {
            script.delete()
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
        // 0.8.275 から「.sh で終わるのに置き場に無い名前」は弾かれるので、HOME を作り替えて
        // 導入済みマクロを 1 本置く (実機で backup.sh を install した状態と同じにする)。
        val home = File(dir, "home").apply { File(this, ".z2term/macros").mkdirs() }
        File(home, ".z2term/macros/backup.sh").writeText("#!/bin/sh\n")
        fun run(vararg args: String): String {
            val pb = ProcessBuilder(listOf(sh!!, script.absolutePath) + args)
                .redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            return out
        }
        try {
            assertEquals("[1]\n[tile]\n[set]\n[1]\n[backup.sh]\n[]\n[]\n[]", run("set", "1", "backup.sh"))
            // 表示名が後ろでも前でも、コマンドは同じに読めること。
            val expected = "[1]\n[tile]\n[set]\n[2]\n[z2-screen keepon 1h]\n[消灯しない]\n[]\n[]"
            assertEquals(expected, run("set", "2", "z2-screen", "keepon", "1h", "-l", "消灯しない"))
            assertEquals(expected, run("set", "2", "-l", "消灯しない", "z2-screen", "keepon", "1h"))
            // --off から後ろは「切るときのコマンド」。⚠ ここが混ざると、切るつもりの引数が
            // 入のコマンドに足されて毎回おかしなものが走る。表示名はどこに書いてもよい。
            val pair = "[1]\n[tile]\n[set]\n[3]\n[z2-torch on]\n[ライト]\n[z2-torch off]\n[]"
            assertEquals(pair, run("set", "3", "z2-torch", "on", "--off", "z2-torch", "off", "-l", "ライト"))
            assertEquals(pair, run("set", "3", "-l", "ライト", "z2-torch", "on", "--off", "z2-torch", "off"))
            // 引用符でひとまとまりにしても同じ。
            assertEquals(pair, run("set", "3", "z2-torch on", "--off", "z2-torch off", "-l", "ライト"))
            assertEquals("[1]\n[tile]\n[list]", run())
            assertEquals("[1]\n[tile]\n[clear]\n[all]", run("clear", "all"))
            // コマンドが無い / 枠だけ / 未知のサブコマンド / --off が空 は usage で終わり、
            // 割り当てに行かない (空の --off を通すと「押しても切れないトグル」になる)。
            for (args in listOf(
                listOf("set"), listOf("set", "1"), listOf("bogus"),
                listOf("set", "3", "z2-torch", "on", "--off")
            )) {
                val out = run(*args.toTypedArray())
                assertTrue("usage が出ていない (${args.joinToString(" ")}): $out", out.contains("usage:"))
            }
            // 絵の名前 (-i) は最後の欄へ回る。⚠ **この欄を足した 0.8.357 で期待値を直し
            // 忘れ、テストが赤いまま残っていた**ので、渡す側も固定しておく。
            assertEquals(
                "[1]\n[tile]\n[set]\n[4]\n[backup.sh]\n[バックアップ]\n[]\n[sync]",
                run("set", "4", "backup.sh", "-l", "バックアップ", "-i", "sync")
            )
            // 引数付きのマクロ名はそのまま通る (タイル側が先頭の語で判定する・0.8.275)。
            assertEquals(
                "[1]\n[tile]\n[set]\n[1]\n[backup.sh --now]\n[]\n[]\n[]",
                run("set", "1", "backup.sh --now")
            )
            // ⚠ 置き場に無い .sh は**割り当てに行かない**。通すとコマンド扱いで PATH から
            // 探され、見つからず「押しても無反応」になる (理由は tile/run.log にしか出ない)。
            for (args in listOf(
                listOf("set", "1", "nope.sh"),
                listOf("set", "1", "nope.sh", "ask"),
                listOf("set", "1", "backup.sh", "--off", "nope.sh")
            )) {
                val out = run(*args.toTypedArray())
                assertFalse("置き場に無いマクロが割り当てられた (${args.joinToString(" ")}): $out", out.contains("[tile]"))
                assertTrue("理由が出ていない (${args.joinToString(" ")}): $out", out.contains("nope.sh"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * `z2-update` が**フラグをそのままアプリ側へ渡す**こと (0.8.371)。
     *
     * ⚠ 渡す欄は「サブコマンド / APK を残すか / 落とし先」の 3 つで、順番が入れ替わると
     * **黙って別の意味になる** (`--keep` のつもりが保存先になる等)。実際の `sh` で押さえる。
     * ⚠ 知らないフラグは**アプリを呼ばずに usage で終わる**こと。通すと「打ち間違えたのに
     * 更新が走った」になり、確認画面まで出てしまう。
     */
    @Test
    fun updatePassesFlagsThrough() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val dir = Files.createTempDirectory("z2update").toFile()
        val stub = File(dir, "z2api").apply {
            writeText("#!/bin/sh\nfor a in \"\$@\"; do echo \"[\$a]\"; done\n")
            setExecutable(true)
        }
        val script = File(dir, "z2-update").apply {
            writeText(scripts["z2-update"]!!.replace("/usr/local/bin/z2api", stub.absolutePath))
        }
        fun run(vararg args: String): String {
            val proc = ProcessBuilder(listOf(sh!!, script.absolutePath) + args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            return out
        }
        try {
            // 引数なし = 落として入れ替えるところまで。keep は 0、落とし先は空 (= 設定に従う)。
            assertTrue("run が渡っていない: ${run()}", run().endsWith("[1]\n[update]\n[run]\n[0]\n[]"))
            assertTrue("check が渡っていない", run("--check").endsWith("[1]\n[update]\n[check]\n[0]\n[]"))
            assertTrue("keep が渡っていない", run("--keep").endsWith("[1]\n[update]\n[run]\n[1]\n[]"))
            assertTrue(
                "dir が渡っていない",
                run("--dir", "/sdcard/Download").endsWith("[1]\n[update]\n[run]\n[0]\n[/sdcard/Download]")
            )
            // 並べても取り違えないこと (--keep が保存先の欄へ回らない)。
            assertTrue(
                "並べたときに取り違えている",
                run("--keep", "--dir", "/tmp/x").endsWith("[1]\n[update]\n[run]\n[1]\n[/tmp/x]")
            )
            // 知らないフラグ・値の無い --dir は usage で終わり、アプリを呼ばない。
            for (args in listOf(listOf("--bogus"), listOf("--dir"), listOf("install"))) {
                val out = run(*args.toTypedArray())
                assertTrue("usage が出ていない (${args.joinToString(" ")}): $out", out.contains("usage:"))
                assertFalse("アプリを呼んでしまった (${args.joinToString(" ")}): $out", out.contains("[update]"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * F: 常駐サーバーを操る `z2-server` が同梱され、4 つのサブコマンドを持つこと。
     *
     * ⚠ **`z2-when` から枠 (FGS + WakeLock + WifiLock) の中でサーバーを上げる唯一の経路**なので、
     * これが欠けると「ルールで起動したのに画面を消すとつながらない」に逆戻りする。
     */
    @Test
    fun serverHelperCoversAllSubcommands() {
        val body = scripts["z2-server"]
        assertTrue("z2-server が同梱されていない", body != null)
        for (sub in listOf("list", "status")) {
            assertTrue("z2-server に $sub が無い", body!!.contains("server $sub"))
        }
        // start / stop は同じ枝から "$sub" で渡すので、分岐そのものを見る。
        assertTrue("z2-server に start|stop の分岐が無い", body!!.contains("start|stop"))
    }

    /** A1: タブを操る `z2-session` が同梱され、5 つのサブコマンドを持つこと。 */
    @Test
    fun sessionHelperCoversAllSubcommands() {
        val body = scripts["z2-session"]
        assertTrue("z2-session が同梱されていない", body != null)
        for (sub in listOf("list", "new", "send", "key", "capture", "close")) {
            assertTrue("z2-session に $sub が無い", body!!.contains("session $sub"))
        }
    }

    /** USB Host の列挙・許可 helper が同梱されること。 */
    @Test
    fun usbHelperIsBundled() {
        val body = scripts["z2-usb"]
        assertTrue("z2-usb が同梱されていない", body != null)
        assertTrue("z2-usb list が usb API を呼ばない", body!!.contains(" usb list"))
        assertTrue("z2-usb allow が usb API を呼ばない", body.contains(" usb allow"))
    }

    /**
     * `z2-icon` が**絵をそのまま届ける**こと。
     *
     * ドット絵は改行を含む数百バイトの塊で、生のまま引数に載せるとリクエストファイルの
     * 「1 行 = 1 引数」が壊れる。base64 に畳んで渡すのが約束で、ここが崩れると
     * **2 行目から先が黙って落ちた絵**が届く (端末側からは正常に見える)。
     * ファイルからでも標準入力からでも同じものが渡ることを実際の `sh` で押さえる。
     */
    @Test
    fun iconSendsTheDrawingAsBase64() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        assumeTrue("base64 が無い環境なのでスキップ", File("/usr/bin/base64").canExecute() ||
            File("/bin/base64").canExecute())
        val dir = Files.createTempDirectory("z2icon").toFile()
        val stub = File(dir, "z2api").apply {
            writeText("#!/bin/sh\nfor a in \"\$@\"; do echo \"[\$a]\"; done\n")
            setExecutable(true)
        }
        val script = File(dir, "z2-icon").apply {
            writeText(scripts["z2-icon"]!!.replace("/usr/local/bin/z2api", stub.absolutePath))
        }
        val art = "..##..\n.####.\n..##..\n"
        val artFile = File(dir, "art.txt").apply { writeText(art) }
        val b64 = java.util.Base64.getEncoder().encodeToString(art.toByteArray())
        fun run(stdin: String? = null, vararg args: String): String {
            val proc = ProcessBuilder(listOf(sh!!, script.absolutePath) + args)
                .redirectErrorStream(true).start()
            proc.outputStream.use { if (stdin != null) it.write(stdin.toByteArray()) }
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            return out
        }
        // ⚠ 絵を返すものには**端末の桁数**が末尾に付く (プレビューを畳むかの判断に使う)。
        // テストは tty ではないので 0 = 「分からない」。
        try {
            assertEquals("[1]\n[icon]\n[set]\n[1]\n[$b64]\n[0]", run(null, "set", "1", artFile.absolutePath))
            // ファイルを省いたときと `-` は同じ = 標準入力から読む。
            assertEquals("[1]\n[icon]\n[set]\n[notify]\n[$b64]\n[0]", run(art, "set", "notify", "-"))
            assertEquals("[1]\n[icon]\n[set]\n[notify]\n[$b64]\n[0]", run(art, "set", "notify"))
            // 引数の数で読み分ける (無し=一覧 / 1 つ=表示 / 2 つ=対象へ入れる)。
            assertEquals("[1]\n[icon]\n[samples]", run(null, "sample"))
            assertEquals("[1]\n[icon]\n[sample-show]\n[bell]\n[0]", run(null, "sample", "bell"))
            assertEquals("[1]\n[icon]\n[sample]\n[2]\n[bell]\n[0]", run(null, "sample", "2", "bell"))
            assertEquals("[1]\n[icon]\n[scale]\n[2]\n[48]\n[0]", run(null, "scale", "2", "48"))
            assertEquals("[1]\n[icon]\n[grid]\n[48]", run(null, "grid", "48"))
            assertEquals("[1]\n[icon]\n[grid]", run(null, "grid"))
            assertEquals("[1]\n[icon]\n[list]", run(null))
            assertEquals("[1]\n[icon]\n[clear]\n[all]", run(null, "clear", "all"))
            // 自分で入れた絵を捨てて割り当てから選び直す道 (これが無いと自動へ戻せない)。
            assertEquals("[1]\n[icon]\n[auto]\n[1]", run(null, "auto", "1"))
            assertEquals("[1]\n[icon]\n[auto]\n[all]", run(null, "auto", "all"))
            // ⚠ 無いファイルは**ブリッジまで行かせない**。空を渡すと「1 点も塗られていない」
            // という、打ったつもりのファイル名とは無関係な理由で断られる。
            val missing = run(null, "set", "1", File(dir, "nope.txt").absolutePath)
            assertTrue("無いファイルが通っている: $missing", !missing.contains("[icon]"))
            // 対象が無い呼び方は usage で終わる。
            for (args in listOf(
                listOf("set"), listOf("show"), listOf("clear"), listOf("auto"), listOf("bogus")
            )) {
                val out = run(null, *args.toTypedArray())
                assertTrue("usage が出ていない (${args.joinToString(" ")}): $out", out.contains("usage:"))
            }
        } finally {
            dir.deleteRecursively()
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
