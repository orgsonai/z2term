package com.zerotoship.z2term.service

/**
 * `z2-when` ルールの**絞り込み** (`if=` / `cooldown=` / `between=` / `days=`・0.8.263) を
 * 判定する部分。[WhenTriggerMatch] と同じく **Android API に触れない**ので単体テストできる。
 *
 * トリガーが「いつ動くか」を決めるのに対し、こちらは「**動いていい状況か**」を決める。
 * どのトリガーにも同じように効き、判定は [WhenManager] の実行入口 1 か所でだけ行う
 * (トリガーを増やしても付け忘れが起きない。一時停止スイッチと同じ置き方)。
 */
object WhenGuard {

    /** 弾いた理由。発火の記録 (`.fired`) にそのまま残すので、**画面と CLI で同じ文字列**を使う。 */
    const val SKIP_IF = "skip:if"
    const val SKIP_BETWEEN = "skip:between"
    const val SKIP_DAYS = "skip:days"
    const val SKIP_COOLDOWN = "skip:cooldown"

    /**
     * `if` 系で見送ったが、`else=` があったので**そちらを走らせた** (0.8.372)。
     *
     * ⚠ `skip:if` で始めてあるのは、既存の「見送り」を数えている読み手 (画面の絞り込み表示・
     * 端末での grep) から見て**同じ仲間だと分かる**ようにするため。走ったのは else の方だと
     * 矢印で示す。
     */
    const val SKIP_IF_ELSE = "skip:if→else"

    /**
     * `if=` の条件をいまの状態 [state] が満たすか。
     *
     * 書式はカンマ区切りの **AND**。頭に `!` を付けると否定。値は `z2-state` が返すものと
     * **同じ語彙**にしてある — 端末で `z2-state` を叩いて確かめたとおりに書ける、というのが要点
     * (別実装で判定すると必ずズレるので、[state] は `z2-state` と同じ収集経路から渡す)。
     *
     *  - `wifi` / `charging` / `screen` / `locked` / `headset` … 真偽 ([truthy])
     *  - `ssid=Home` / `ringer=silent` / `plug=ac` … 一致 (大小文字を区別しない)
     *  - `level<30` / `level>80` / `temp>40` … 数値の比較
     *
     * **知らないキーは不成立** (= 実行しない) にしてある。誤発火より取りこぼしを選ぶ、という
     * 既存の判定 ([WhenTriggerMatch.wifi] の SSID 未取得時) と同じ考え方。打ち間違いで黙って
     * 動かなくなるのを防ぐため、登録時に CLI と画面の側で既知キーかどうかを検査する
     * ([isKnownCondition])。
     */
    fun conditionsMet(spec: String, state: Map<String, String>): Boolean {
        val terms = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        return terms.all { term ->
            val negate = term.startsWith("!")
            val body = if (negate) term.substring(1).trim() else term
            if (body.isEmpty()) return@all false
            val ok = evalTerm(body, state)
            if (negate) !ok else ok
        }
    }

    /**
     * `if_any=` の条件を [state] が満たすか — **どれか 1 つでも成り立てば true** (0.8.372)。
     *
     * 書式は [conditionsMet] と同じで、`,` の意味だけが AND から OR に変わる。空なら絞らない
     * (true)。⚠ **空を false にしない** — `if_any=` を書いていないルールが全部止まる。
     */
    fun anyConditionMet(spec: String, state: Map<String, String>): Boolean {
        val terms = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        return terms.any { term ->
            val negate = term.startsWith("!")
            val body = if (negate) term.substring(1).trim() else term
            if (body.isEmpty()) return@any false
            val ok = evalTerm(body, state)
            if (negate) !ok else ok
        }
    }

    /** 条件 1 つ分。比較演算子があれば数値比較、`=` があれば一致、無ければ真偽。 */
    private fun evalTerm(term: String, state: Map<String, String>): Boolean {
        val lt = term.indexOf('<')
        val gt = term.indexOf('>')
        if (lt > 0 || gt > 0) {
            val op = if (lt > 0) '<' else '>'
            val at = if (lt > 0) lt else gt
            val key = term.substring(0, at).trim()
            val want = term.substring(at + 1).trim().toDoubleOrNull() ?: return false
            val have = state[key]?.trim()?.toDoubleOrNull() ?: return false
            return if (op == '<') have < want else have > want
        }
        val eq = term.indexOf('=')
        if (eq > 0) {
            val key = term.substring(0, eq).trim()
            val want = term.substring(eq + 1).trim()
            val have = state[key] ?: return false
            return want.equals(have.trim(), ignoreCase = true)
        }
        val have = state[term] ?: return false
        return truthy(have)
    }

    /**
     * 状態の値を真偽として読む。`z2-state` は真偽を `true`/`false` で返すが、`screen` だけは
     * `on`/`off` なので両方を受ける (`z2-state screen` の出力をそのまま書いて通じるように)。
     */
    private fun truthy(value: String): Boolean =
        when (value.trim().lowercase()) {
            "true", "on", "1", "yes" -> true
            else -> false
        }

    /** `if=` に書けるキー。ここに無い名前は打ち間違いとして登録時に弾く ([conditionError])。 */
    private val KNOWN_KEYS = setOf(
        "screen", "locked", "idle", "charging", "plug", "level", "wifi", "ssid",
        "ringer", "airplane", "headset", "bt_audio", "temp", "volume", "volume_max",
    )

    fun isKnownCondition(key: String): Boolean = key.trim() in KNOWN_KEYS

    /**
     * `if=` の書式を検査して、問題があれば**その場で直せる 1 行**を返す (問題なければ null)。
     * 登録時 (CLI と画面) に使う — 実行時に黙って不成立にするより、書いた瞬間に気付ける方がよい。
     */
    fun conditionError(spec: String, field: String = "if"): String? {
        val terms = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null
        terms.forEach { term ->
            val body = term.removePrefix("!").trim()
            if (body.isEmpty()) return "$field: 条件が空です: $term"
            val key = body.takeWhile { it != '=' && it != '<' && it != '>' }.trim()
            if (!isKnownCondition(key)) {
                return "$field: 知らない条件です: $key (使えるもの: ${KNOWN_KEYS.sorted().joinToString(" ")})"
            }
        }
        return null
    }

    /**
     * `between=HH:MM-HH:MM` の時間帯に [minuteOfDay] (0〜1439) が入っているか。
     *
     * **開始を含み終了を含まない** (`09:00-17:00` は 17:00 ちょうどには実行しない)。
     * 開始 > 終了は**日跨ぎ**として扱う (`22:00-07:00` は夜通し)。書式が壊れていれば
     * 絞らない (true) — 時間帯の書き間違いでルールが**永久に動かない**状態を作らないため。
     */
    fun inWindow(spec: String, minuteOfDay: Int): Boolean {
        val s = spec.trim()
        if (s.isEmpty()) return true
        val dash = s.indexOf('-')
        if (dash <= 0) return true
        val from = parseHhMm(s.substring(0, dash)) ?: return true
        val to = parseHhMm(s.substring(dash + 1)) ?: return true
        if (from == to) return true          // 24 時間ぶん = 絞らないのと同じ
        return if (from < to) minuteOfDay in from until to
        else minuteOfDay >= from || minuteOfDay < to
    }

    /** `HH:MM` → 0 時からの分。範囲外・書式違いは null。 */
    private fun parseHhMm(text: String): Int? {
        val p = text.trim().split(':')
        if (p.size != 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * `days=` に [dayOfWeek] (**0 = 日曜 … 6 = 土曜**) が入っているか。
     *
     * 書き方はカンマ区切りで、範囲 `-` も使える:
     *  - 曜日名 `mon` `tue` … `sun` (`mon-fri` / `sat,sun`)
     *  - 数字 `0`〜`7` (**cron と同じ**で 0 と 7 がどちらも日曜。`1-5` = 平日)
     *
     * 書式が壊れていれば絞らない (true)。[inWindow] と同じ理由。
     */
    fun dayAllowed(spec: String, dayOfWeek: Int): Boolean {
        val s = spec.trim()
        if (s.isEmpty()) return true
        var sawValid = false
        s.split(',').forEach { part ->
            val item = part.trim()
            if (item.isEmpty()) return@forEach
            val dash = item.indexOf('-')
            if (dash > 0) {
                val from = parseDay(item.substring(0, dash)) ?: return@forEach
                val to = parseDay(item.substring(dash + 1)) ?: return@forEach
                sawValid = true
                // mon-fri のような順方向に加えて、fri-mon (週跨ぎ) も素直に読めるようにする。
                val hit = if (from <= to) dayOfWeek in from..to else dayOfWeek >= from || dayOfWeek <= to
                if (hit) return true
            } else {
                val d = parseDay(item) ?: return@forEach
                sawValid = true
                if (d == dayOfWeek) return true
            }
        }
        // 1 つも読めなかった = 書き間違い。絞らない (永久に動かないルールを作らない)。
        return !sawValid
    }

    private val DAY_NAMES = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

    /** 曜日 1 つ → 0 (日) 〜 6 (土)。数字は cron と同じ 0〜7 (7 も日曜)。読めなければ null。 */
    private fun parseDay(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { return if (it in 0..7) it % 7 else null }
        val idx = DAY_NAMES.indexOfFirst { t.startsWith(it) }
        return if (idx >= 0) idx else null
    }

    /**
     * `cooldown=` → ミリ秒 (`30s` / `10m` / `2h`・単位を省くと分)。読めなければ 0 = 抑制しない。
     *
     * `time:every=` と違って**最短 1 分にクランプしない** — `sensor:shake` の連打を数秒だけ
     * 抑えたい、のような使い方に意味があるため。
     */
    fun cooldownMs(spec: String): Long {
        val s = spec.trim()
        if (s.isEmpty()) return 0
        val unit = s.last()
        val numStr = if (unit.isLetter()) s.dropLast(1) else s
        val n = numStr.trim().toLongOrNull() ?: return 0
        if (n <= 0) return 0
        return when (unit) {
            's' -> n * 1000
            'm' -> n * 60_000
            'h' -> n * 3_600_000
            else -> if (unit.isDigit()) n * 60_000 else 0
        }
    }
}
