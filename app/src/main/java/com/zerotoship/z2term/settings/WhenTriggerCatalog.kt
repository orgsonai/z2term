package com.zerotoship.z2term.settings

/**
 * `z2-when` のトリガー候補と、書式の検査（0.8.272）。
 *
 * **なぜ要るか**: トリガーは綴りが 1 文字違うだけで**登録できてしまい、一生発火しない**。
 * CLI (`z2-when`) は 0.8.265 から登録時に検査しているが、画面から作れるようになると
 * 同じ検査が画面側にも要る。**候補から選ばせれば打ち間違い自体が起きない**ので、
 * 一覧をここに持って画面の候補と検査の両方で使う。
 *
 * ⚠ 一覧と検査は `Z2ApiScript` の `z2-when` の case 文・[WhenRule] の KDoc・マクロガイドの
 * トリガー表と**揃えること**。片方だけ増やすと「画面からは作れるのに CLI が弾く」
 * (またはその逆) というちぐはぐが出る。[WhenTriggerCatalogTest] が
 * 「候補として出すものは全部 [triggerProblem] を通る」ことだけは機械で守る。
 */
object WhenTriggerCatalog {

    /**
     * トリガー 1 つ分の候補。
     *
     * @param template 入力欄へ入れる文字列。`=` `>` `<` で終わるものは**値の入力が要る**
     *   ([needsValue])。`battery:below=` を選んでから `20` を打つ、という順で使う。
     * @param hint     値の書き方の例。入力欄の下に出して「何を打てばいいか」を示す。
     */
    data class Option(val template: String, val hint: String = "") {
        /** 末尾が記号なら、続けて値を打たないと完成しない候補。 */
        val needsValue: Boolean get() = template.endsWith('=') || template.endsWith('>') || template.endsWith('<')

        /**
         * 候補ボタンに出す文字列＝**そのまま入れて動く完成形**（`battery:below=20`）。
         *
         * 値が要る候補を `battery:below=` のまま入れると、値を打つまで**書式違いのルール**が
         * 手元に残る。例まで入れてしまえば、選んだ直後は必ず正しく、あとは数字を直すだけになる。
         */
        val example: String get() = template + hint
    }

    /**
     * 種別 1 つ分（`charge` / `battery` …）。
     *
     * @param id       トリガーの `:` の手前。[WhenRule.kind] と同じもの。
     * @param labelKey 画面に出す短い日本語/英語ラベルの識別子。文字列そのものは
     *   `strings.xml` に置く（`when_kind_<labelKey>`）。ここに直接書くと英語版が作れない。
     */
    data class Kind(val id: String, val labelKey: String, val options: List<Option>)

    /** 画面に出す順。よく使うもの（充電・電池・時刻・Wi-Fi）を先に置く。 */
    val kinds: List<Kind> = listOf(
        Kind("charge", "charge", listOf(Option("charge:start"), Option("charge:stop"))),
        Kind("battery", "battery", listOf(Option("battery:below=", "20"), Option("battery:above=", "80"))),
        Kind("time", "time", listOf(
            Option("time:daily=", "07:00"),
            Option("time:at=", "22:30"),
            Option("time:every=", "30m"),
            Option("time:cron=", "0 3 * * *"),
        )),
        Kind("wifi", "wifi", listOf(
            Option("wifi:connect"), Option("wifi:disconnect"), Option("wifi:ssid=", "Home"),
        )),
        Kind("net", "net", listOf(
            Option("net:online"), Option("net:offline"),
            Option("net:wifi"), Option("net:mobile"), Option("net:ethernet"),
        )),
        Kind("sensor", "sensor", listOf(
            Option("sensor:shake"),
            Option("sensor:light>", "100"), Option("sensor:light<", "5"),
            Option("sensor:proximity=near"), Option("sensor:proximity=far"),
        )),
        Kind("sms", "sms", listOf(
            Option("sms:any"), Option("sms:otp"),
            Option("sms:from=", "0120"), Option("sms:contains=", "配達"),
        )),
        Kind("notify", "notify", listOf(
            Option("notify:any"), Option("notify:otp"),
            Option("notify:pkg=", "com.example"), Option("notify:title=", "会議"),
            Option("notify:contains=", "至急"),
            Option("notify:category=", "call"), Option("notify:category=", "missed_call"),
        )),
        Kind("file", "file", listOf(Option("file:new=", "/sdcard/Pictures/Screenshots"))),
        Kind("share", "share", listOf(
            Option("share:any"), Option("share:text"), Option("share:file"),
            Option("share:contains=", "http"), Option("share:ext=", "pdf"),
        )),
        Kind("event", "event", listOf(Option("event:", "screen_on"))),
        Kind("boot", "boot", listOf(Option("boot"))),
    )

    /** トリガーの書式の問題。文言は画面側で出す（`strings.xml` に置くため）。 */
    enum class Problem {
        /** 空。 */
        EMPTY,
        /** 種別 (`:` の手前) が知らない名前。 */
        UNKNOWN_KIND,
        /** 種別は合っているが引数 (`:` の後ろ) が書式違い。 */
        BAD_SPEC,
    }

    /**
     * トリガーの書式を検査する（問題なければ null）。`z2-when` の case 文と同じ判定。
     *
     * `event:` の名前だけは検査しない — 端末イベントの名前は増え続け、正本は
     * `z2-when events` の一覧だから（CLI も同じ扱い）。空でないことだけ見る。
     */
    fun triggerProblem(trigger: String): Problem? {
        val t = trigger.trim()
        if (t.isEmpty()) return Problem.EMPTY
        val kind = t.substringBefore(':').trim()
        val spec = if (t.contains(':')) t.substringAfter(':') else ""
        val ok = when (kind) {
            "boot" -> spec.isEmpty()
            "charge" -> spec in setOf("start", "stop")
            "battery" -> spec.hasValueFor("below", "above")
            "time" -> spec.hasValueFor("daily", "at", "every", "cron")
            "wifi" -> spec in setOf("connect", "disconnect") || spec.hasValueFor("ssid")
            "net" -> spec in setOf("online", "offline", "wifi", "mobile", "ethernet")
            "sms" -> spec in setOf("any", "otp") || spec.hasValueFor("from", "contains")
            "notify" -> spec in setOf("any", "otp") ||
                spec.hasValueFor("pkg", "title", "contains", "category")
            "file" -> spec.hasValueFor("new")
            "share" -> spec in setOf("any", "text", "file") || spec.hasValueFor("contains", "ext")
            "sensor" -> spec == "shake" ||
                spec.startsWith("light>") && spec.length > "light>".length ||
                spec.startsWith("light<") && spec.length > "light<".length ||
                spec in setOf("proximity=near", "proximity=far")
            // 名前は z2-when events が正本。ここでは空でないことだけ見る。
            "event" -> spec.isNotEmpty()
            else -> return Problem.UNKNOWN_KIND
        }
        return if (ok) null else Problem.BAD_SPEC
    }

    /** `below=20` のように「[keys] のどれか + `=` + 1 文字以上の値」か。 */
    private fun String.hasValueFor(vararg keys: String): Boolean =
        keys.any { startsWith("$it=") && length > it.length + 1 }

    /** 実行コマンドの問題。 */
    enum class RunProblem {
        /** 空。 */
        EMPTY,
        /**
         * 改行を含む。
         *
         * ルールファイルは**1 行 1 項目**なので、改行入りのコマンドを書くと 2 行目以降が
         * 別の項目として読まれ、**黙って途中で切れたルール**ができる（0.8.272 で判明した事故:
         * 折り返して貼り付けたコマンドが `for` の途中で切れ、実行のたび構文エラーになっていた）。
         * 複数行のロジックはスクリプトファイルへ置いて、そのパスを `run` に書く。
         */
        MULTILINE,
    }

    /** 実行コマンドを検査する（問題なければ null）。 */
    fun runProblem(run: String): RunProblem? = when {
        run.isBlank() -> RunProblem.EMPTY
        run.contains('\n') || run.contains('\r') -> RunProblem.MULTILINE
        else -> null
    }

    /**
     * `run` が指しているスクリプトのパス（無ければ null）。中身を画面に出すために使う。
     *
     * `~/.z2term/macros/x.sh` のように**コマンド全体が 1 本のスクリプト**か、
     * `sh ~/.z2term/macros/x.sh` のように**実行するシェルを前に置いた形**だけを拾う。
     * パイプや `&&` で他のコマンドと繋がっているものは「1 本のスクリプト」ではないので拾わない
     * （どれを出せばいいか決められないし、勝手に決めると別のものを見せてしまう）。
     */
    fun scriptPathIn(run: String): String? {
        val words = run.trim().split(' ', '\t').filter { it.isNotEmpty() }
        val path = when {
            words.isEmpty() -> return null
            words.size == 1 -> words[0]
            // `sh <path>` / `bash <path>` / `sh -e <path>` のような形。
            words.size == 2 && words[0] in SHELLS -> words[1]
            else -> return null
        }
        // 引数付き・パイプ付きは上で弾いているが、記号が混ざったものは念のため拾わない。
        if (path.any { it in "|&;<>()$`\"'" }) return null
        return path.takeIf { it.startsWith('/') || it.startsWith("~/") || it.startsWith("./") }
    }

    private val SHELLS = setOf("sh", "bash", "zsh", "dash", "ash", "ksh")
}
