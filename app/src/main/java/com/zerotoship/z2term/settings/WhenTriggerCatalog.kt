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
     * @param labelKey 画面に出す「いつ動くか」の文言の識別子（`when_opt_<labelKey>`）。
     *   書式 (`battery:below=20`) だけを並べると、候補が増えたときに**読んで選べない**
     *   （1.0.54。種別が 15、候補が 50 を超えた）。書式は文言の下に小さく残す。
     */
    data class Option(val template: String, val hint: String = "", val labelKey: String = "") {
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
     * @param group    画面で並べる段（[groups]）。
     */
    data class Kind(val id: String, val labelKey: String, val options: List<Option>, val group: String = "")

    /**
     * 画面で種別をまとめる段の並び（1.0.54）。文言は `when_group_<id>`。
     *
     * 種別を 1 列に 15 個並べると、目当てを探すのに全部読むことになる。「何について
     * 起きたか」で 5 段に分け、段の名前で当たりを付けてから種別を選ぶ。
     */
    val groups: List<String> = listOf("time", "power", "link", "apps", "device")

    /** 画面に出す順。段 ([groups]) の中では、よく使うものを先に置く。 */
    // ⚠ /sdcard は利用者へ見せる**入力例**で、ディストロ側から見えるパス。Android の
    // 外部ストレージ API に置き換えると、端末で打つパスと食い違う例になる。
    @Suppress("SdCardPath")
    val kinds: List<Kind> = listOf(
        Kind("time", "time", listOf(
            Option("time:daily=", "07:00", "time_daily"),
            Option("time:at=", "22:30", "time_at"),
            Option("time:every=", "30m", "time_every"),
            Option("time:cron=", "0 3 * * *", "time_cron"),
        ), group = "time"),
        Kind("charge", "charge", listOf(
            Option("charge:start", labelKey = "charge_start"), Option("charge:stop", labelKey = "charge_stop"),
        ), group = "power"),
        Kind("battery", "battery", listOf(
            Option("battery:below=", "20", "battery_below"), Option("battery:above=", "80", "battery_above"),
            Option("battery:temp>", "45", "battery_temp_above"), Option("battery:temp<", "35", "battery_temp_below"),
        ), group = "power"),
        Kind("wifi", "wifi", listOf(
            Option("wifi:connect", labelKey = "wifi_connect"), Option("wifi:disconnect", labelKey = "wifi_disconnect"),
            Option("wifi:ssid=", "Home", "wifi_ssid"),
        ), group = "link"),
        Kind("net", "net", listOf(
            Option("net:online", labelKey = "net_online"), Option("net:offline", labelKey = "net_offline"),
            Option("net:wifi", labelKey = "net_wifi"), Option("net:mobile", labelKey = "net_mobile"),
            Option("net:ethernet", labelKey = "net_ethernet"),
        ), group = "link"),
        Kind("bt", "bt", listOf(
            Option("bt:connect", labelKey = "bt_connect"), Option("bt:disconnect", labelKey = "bt_disconnect"),
            Option("bt:connect=", "Car", "bt_connect_name"), Option("bt:disconnect=", "Car", "bt_disconnect_name"),
        ), group = "link"),
        Kind("app", "app", listOf(
            Option("app:open=", "com.android.settings", "app_open"),
            Option("app:close=", "com.android.settings", "app_close"),
        ), group = "apps"),
        Kind("notify", "notify", listOf(
            Option("notify:any", labelKey = "notify_any"), Option("notify:otp", labelKey = "notify_otp"),
            Option("notify:pkg=", "com.example", "notify_pkg"), Option("notify:title=", "会議", "notify_title"),
            Option("notify:contains=", "至急", "notify_contains"),
            Option("notify:category=", "call", "notify_call"),
            Option("notify:category=", "missed_call", "notify_missed_call"),
        ), group = "apps"),
        Kind("sms", "sms", listOf(
            Option("sms:any", labelKey = "sms_any"), Option("sms:otp", labelKey = "sms_otp"),
            Option("sms:from=", "0120", "sms_from"), Option("sms:contains=", "配達", "sms_contains"),
        ), group = "apps"),
        Kind("share", "share", listOf(
            Option("share:any", labelKey = "share_any"), Option("share:text", labelKey = "share_text"),
            Option("share:file", labelKey = "share_file"),
            Option("share:contains=", "http", "share_contains"), Option("share:ext=", "pdf", "share_ext"),
        ), group = "apps"),
        // event: の名前は増え続けるので、画面では `z2-when events` の一覧から候補を作る
        // (WhenRulesSheet)。ここに 1 件だけ置くのは検査とテストのため。
        Kind("event", "event", listOf(Option("event:", "screen_on", "event")), group = "device"),
        Kind("sensor", "sensor", listOf(
            Option("sensor:shake", labelKey = "sensor_shake"),
            Option("sensor:light>", "100", "sensor_light_above"), Option("sensor:light<", "5", "sensor_light_below"),
            Option("sensor:proximity=near", labelKey = "sensor_near"),
            Option("sensor:proximity=far", labelKey = "sensor_far"),
        ), group = "device"),
        Kind("steps", "steps", listOf(Option("steps:reach=", "8000", "steps_reach")), group = "device"),
        Kind("file", "file", listOf(
            Option("file:new=", "/sdcard/Pictures/Screenshots", "file_new"),
            Option("file:changed=", "/sdcard/Documents", "file_changed"),
            Option("file:deleted=", "/sdcard/Documents", "file_deleted"),
        ), group = "device"),
        Kind("boot", "boot", listOf(Option("boot", labelKey = "boot")), group = "device"),
    )

    /**
     * [trigger] に当たる候補（無ければ null）。画面で「いま選んでいるのはどれか」を
     * 文言で出すのに使う。
     *
     * 値の要らない候補は完全一致、値の要る候補は書式の頭が合えばよい
     * (`battery:below=15` は `battery:below=` の候補)。`notify:category=` のように
     * 同じ頭の候補が複数あるときは、例 ([Option.example]) と完全に一致するものだけを選ぶ
     * (`notify:category=msg` を「着信中の通知」と呼ばないため。どれでもなければ null)。
     */
    fun optionFor(trigger: String): Option? {
        val t = trigger.trim()
        val all = kinds.flatMap { it.options }
        all.firstOrNull { it.example == t }?.let { return it }
        return all.filter { it.needsValue && t.startsWith(it.template) && t.length > it.template.length }
            .singleOrNull()
    }

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
            "battery" -> spec.hasValueFor("below", "above") || spec.hasNumberAfter("temp>", "temp<")
            "time" -> spec.hasValueFor("daily", "at", "every", "cron")
            "wifi" -> spec in setOf("connect", "disconnect") || spec.hasValueFor("ssid")
            "net" -> spec in setOf("online", "offline", "wifi", "mobile", "ethernet")
            "sms" -> spec in setOf("any", "otp") || spec.hasValueFor("from", "contains")
            "notify" -> spec in setOf("any", "otp") ||
                spec.hasValueFor("pkg", "title", "contains", "category")
            "file" -> spec.hasValueFor("new", "changed", "deleted")
            "bt" -> spec in setOf("connect", "disconnect") || spec.hasValueFor("connect", "disconnect")
            "app" -> spec.hasValueFor("open", "close")
            "steps" -> spec.startsWith("reach=") && spec.substringAfter('=').trim().toIntOrNull()?.let { it > 0 } == true
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

    /** `temp>45` のように「[prefixes] のどれか + 数」か。数でないと一生成立しないので弾く。 */
    private fun String.hasNumberAfter(vararg prefixes: String): Boolean =
        prefixes.any { startsWith(it) && substring(it.length).trim().toFloatOrNull() != null }

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
