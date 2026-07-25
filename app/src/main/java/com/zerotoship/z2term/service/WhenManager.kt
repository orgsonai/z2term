package com.zerotoship.z2term.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.WhenRule
import com.zerotoship.z2term.widget.StatusWidgetProvider
import com.zerotoship.z2term.widget.TailWidgetProvider
import java.io.File

/**
 * `z2-when` 自動化ハブの中核 (A6・stage 1)。ルールファイル (`~/.z2term/when/<id>.rule`) を読み、
 * トリガー (充電 / 電池 / 時刻) の発火時に [WhenRule.run] を Linux エンジンで実行する。
 *
 * **常駐を増やさない設計** (引き継ぎ書 §10-1):
 *  - 充電 (`charge:*`) / 電池 (`battery:*`) は manifest レシーバ ([WhenReceiver]) が受ける。
 *    `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` は暗黙ブロードキャスト
 *    制限の**例外**なので、専用のフォアグラウンドサービス (= 追加の常駐通知・WakeLock) 無しで拾える。
 *  - 時刻 (`time:*`) は [AlarmManager] (Doze 貫通・権限不要の `setAndAllowWhileIdle`)。
 *
 * 実行は「そのとき選ばれている distro」で `sh -lc '<run>'` を headless に起動し、出力を
 * `~/.z2term/when/<id>.log` へ落とす ([ServerDaemonManager] と同じ launch パターン)。トリガー情報は
 * 環境変数 `Z2_WHEN_*` で渡す (外部入力を `eval` させない安全境界)。
 */
object WhenManager {

    private const val TAG = "WhenManager"

    /** 電池しきい値のエッジ判定に使う直近残量の保存先 (ルールディレクトリ内)。 */
    private const val BATT_STATE = ".battlevel"

    /** 時刻トリガーで武装済みのルール id を覚えておくファイル (reload 時に取りこぼしなく貼り直す)。 */
    private const val ARMED_STATE = ".armed"

    /** ルールディレクトリ (`filesDir/shared_home/.z2term/when` = 端末からは `~/.z2term/when`)。 */
    fun whenDir(context: Context): File =
        File(File(context.filesDir, "shared_home"), ".z2term/when")

    /** ルールファイルをすべて読む。壊れた 1 件は飛ばす。 */
    fun loadRules(context: Context): List<WhenRule> {
        val dir = whenDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".rule") } ?: return emptyList()
        return files.sortedBy { it.name }.mapNotNull { f ->
            runCatching { WhenRule.parse(f.name.removeSuffix(".rule"), f.readText()) }.getOrNull()
        }
    }

    // --- 時刻トリガー (AlarmManager) ---

    /**
     * ルールの変更・端末起動・アプリ起動のたびに呼ぶ。時刻ルールの AlarmManager 予約を貼り直す。
     * 充電/電池はレシーバ駆動なので、ここでは何も武装しない。
     */
    fun reload(context: Context) {
        val app = context.applicationContext
        val dir = whenDir(app).apply { mkdirs() }
        // 前回武装したものを一旦すべて解除 (消えた/無効化されたルールの予約を残さない)。
        readArmed(dir).forEach { id -> cancelTime(app, id) }
        val armed = ArrayList<String>()
        loadRules(app).filter { it.enabled && it.kind == "time" }.forEach { rule ->
            val at = nextTimeAt(rule.spec)
            if (at > 0) {
                scheduleTime(app, rule.id, at)
                armed.add(rule.id)
            } else {
                Log.w(TAG, "bad time spec, skip: ${rule.trigger}")
            }
        }
        writeArmed(dir, armed)
        // 検知サービスが動いていれば、sensor ルールの増減に合わせてセンサー登録を貼り直す。
        SystemEventService.refreshSensorsIfRunning()
        // ホーム画面ウィジェットの「自動化 N」もここで追従させる (ルールの増減・on/off の直後)。
        StatusWidgetProvider.refresh(app)
    }

    /** [WhenReceiver] の時刻発火から呼ぶ。ルールを実行し、繰り返し種別なら次を武装し直す。 */
    fun onTimeFire(context: Context, ruleId: String) {
        val app = context.applicationContext
        val rule = loadRules(app).firstOrNull { it.id == ruleId } ?: return
        if (!rule.enabled) return
        runRule(app, rule, level = -1)
        when {
            // 毎日 / 周期 / cron → 次回を武装。
            rule.spec.startsWith("daily=") || rule.spec.startsWith("every=") ||
                rule.spec.startsWith("cron=") -> {
                val next = nextTimeAt(rule.spec)
                if (next > 0) scheduleTime(app, rule.id, next)
            }
            // 1 回きり (`at=HH:MM`) → 自動で無効化してファイルに書き戻す (再武装しない)。
            rule.spec.startsWith("at=") -> {
                setEnabled(app, rule.id, false)
                cancelTime(app, rule.id)
                writeArmed(whenDir(app), readArmed(whenDir(app)) - rule.id)
            }
        }
    }

    /** `daily=HH:MM` / `at=HH:MM` / `every=Nm|Nh|Ns` の次回発火エポックミリ秒。不正なら 0。 */
    private fun nextTimeAt(spec: String): Long {
        val eq = spec.indexOf('=')
        if (eq <= 0) return 0
        val key = spec.substring(0, eq)
        val value = spec.substring(eq + 1).trim()
        return when (key) {
            "daily", "at" -> {
                val hm = value.split(':')
                val h = hm.getOrNull(0)?.toIntOrNull() ?: return 0
                val m = hm.getOrNull(1)?.toIntOrNull() ?: return 0
                if (h !in 0..23 || m !in 0..59) 0 else AlarmScheduler.nextDailyAt(h, m)
            }
            "every" -> {
                val ms = parseIntervalMs(value)
                if (ms <= 0) 0 else System.currentTimeMillis() + ms
            }
            // cron 式は空白を含むので、`=` の後ろ全体 (trim 済み) をそのまま渡す。
            "cron" -> CronSchedule.nextAfter(value, System.currentTimeMillis())
            else -> 0
        }
    }

    /** `30m` / `2h` / `45s` → ミリ秒。最短 1 分にクランプ (取りこぼし・電池対策)。不正なら 0。 */
    private fun parseIntervalMs(value: String): Long {
        if (value.isEmpty()) return 0
        val unit = value.last()
        val numStr = if (unit.isLetter()) value.dropLast(1) else value
        val n = numStr.toLongOrNull() ?: return 0
        val ms = when (unit) {
            's' -> n * 1000
            'm' -> n * 60_000
            'h' -> n * 3_600_000
            else -> if (unit.isDigit()) n * 60_000 else return 0  // 単位省略は分扱い
        }
        return if (ms <= 0) 0 else ms.coerceAtLeast(60_000)
    }

    private fun scheduleTime(context: Context, ruleId: String, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            // setAndAllowWhileIdle: Doze 貫通・SCHEDULE_EXACT_ALARM 不要 (数分ずれることがある)。
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timePending(context, ruleId))
        }.onFailure { Log.w(TAG, "schedule time failed for $ruleId", it) }
    }

    private fun cancelTime(context: Context, ruleId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(timePending(context, ruleId)) }
    }

    private fun timePending(context: Context, ruleId: String): PendingIntent {
        val intent = Intent(context, WhenReceiver::class.java)
            .setAction(WhenReceiver.ACTION_TIME_FIRE)
            .putExtra(WhenReceiver.EXTRA_RULE_ID, ruleId)
        // requestCode は id ごとに一意にする (同じだと予約が上書きされる)。
        return PendingIntent.getBroadcast(
            context, ruleId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // --- 充電トリガー ---

    /** 充電の開始/停止を受けて `charge:start`/`charge:stop` ルールを実行する。 */
    fun onCharge(context: Context, started: Boolean, level: Int) {
        val app = context.applicationContext
        val want = if (started) "start" else "stop"
        loadRules(app).filter { it.enabled && it.kind == "charge" && it.spec == want }
            .forEach { runRule(app, it, level) }
        // 充電状態が変わったタイミングで電池しきい値も評価しておく (残量の変化点になりやすい)。
        onBatteryChanged(app, level)
    }

    // --- 電池しきい値トリガー ---

    /**
     * 電池残量が変わったときに呼ぶ。`battery:below=N` / `battery:above=N` を**跨いだ瞬間だけ**
     * 発火する (エッジ判定)。直近残量は [BATT_STATE] に保存し、初回は基準設定のみで発火しない。
     *
     * 呼び元は [SystemEventService] だけ (充電の開始/停止・`BATTERY_LOW`/`_OKAY`・残量 1% 変化)。
     * **検知 ON が前提**なのは、電源/電池のブロードキャストが暗黙ブロードキャスト制限の例外ではなく
     * manifest レシーバでは受け取れないため ([SystemEventService.handlePower] のコメント)。
     * 同じ残量で二重に呼ばれてもエッジが立たないので二重発火しない。
     */
    fun onBatteryChanged(context: Context, level: Int) {
        if (level !in 0..100) return
        val app = context.applicationContext
        val dir = whenDir(app).apply { mkdirs() }
        val prev = readBattLevel(dir)
        // 変化なしなら書き戻しもしない (BATTERY_CHANGED は数秒おきに来るので毎回書くと無駄が大きい)。
        if (prev == level) return
        writeBattLevel(dir, level)
        if (prev < 0) return  // 初回は基準を置くだけ
        loadRules(app).filter { it.enabled && it.kind == "battery" }.forEach { rule ->
            val eq = rule.spec.indexOf('=')
            if (eq <= 0) return@forEach
            val op = rule.spec.substring(0, eq)
            val n = rule.spec.substring(eq + 1).trim().toIntOrNull() ?: return@forEach
            val crossed = when (op) {
                "below" -> prev >= n && level < n
                "above" -> prev <= n && level > n
                else -> false
            }
            if (crossed) runRule(app, rule, level)
        }
    }

    private fun readBattLevel(dir: File): Int =
        runCatching { File(dir, BATT_STATE).readText().trim().toInt() }.getOrDefault(-1)

    private fun writeBattLevel(dir: File, level: Int) {
        runCatching { File(dir, BATT_STATE).writeText(level.toString()) }
    }

    // --- Wi-Fi トリガー ---

    /**
     * Wi‑Fi の接続/切断を受けて `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<名前>` を実行する。
     * 呼び元は検知 ON 時の [SystemEventService.handleWifi] (状態変化で 1 回だけ・エッジ判定は呼び元)。
     * Wi‑Fi の接続変化は動的レシーバでしか拾えない (暗黙ブロードキャスト制限の対象) ため、充電/電池と
     * 違い**検知フォアグラウンドサービスが ON のときだけ**働く。SSID は位置情報権限が無いと空になる。
     */
    fun onWifi(context: Context, connected: Boolean, ssid: String) {
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "wifi" }.forEach { rule ->
            if (WhenTriggerMatch.wifi(rule.spec, connected, ssid)) {
                val env = if (ssid.isNotEmpty()) mapOf("Z2_WHEN_SSID" to ssid) else emptyMap()
                runRule(app, rule, level = -1, extraEnv = env)
            }
        }
    }

    // --- SMS トリガー ---

    /**
     * 着信 SMS を受けて `sms:any` / `sms:from=` / `sms:contains=` / `sms:otp` を実行する。
     * 呼び元は [SmsLogReceiver] (`RECEIVE_SMS` 許可があれば OS が着信ごとに起動＝アプリ未起動でも動く)。
     * 送信元・本文は `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`、`sms:otp` のときは抽出コードを
     * `Z2_WHEN_OTP` で渡す (いずれも外部入力なのでシェルへ展開せず env・安全エスケープ)。
     */
    fun onSms(context: Context, from: String, body: String) {
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "sms" }.forEach { rule ->
            if (WhenTriggerMatch.sms(rule.spec, from, body)) {
                val env = HashMap<String, String>()
                env["Z2_WHEN_SMS_FROM"] = from
                env["Z2_WHEN_SMS_BODY"] = body
                if (rule.spec.trim() == "otp") env["Z2_WHEN_OTP"] = WhenTriggerMatch.extractOtp(body)
                runRule(app, rule, level = -1, extraEnv = env)
            }
        }
    }

    // --- 端末イベントトリガー (event:*) ---

    /**
     * 同一ルールの連続発火を抑える最小間隔。`screen_on`/`screen_off` のように**人の操作しだいで
     * 何度でも来る**イベントを名前で拾えるようにした以上、これが無いとルール 1 本で発火の嵐になる。
     * トリガー別ではなく**ルール別**に効かせる (別々のルールは互いを抑制しない)。
     */
    private const val EVENT_MIN_INTERVAL_MS = 10_000L

    /** rule id → 最後に発火した時刻。プロセス内メモリのみ (死んだらリセットで構わない)。 */
    private val eventLastFired = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * `events.jsonl` に書かれる端末イベントを受けて `event:<名前>` ルールを実行する (0.8.226)。
     *
     * **既に検知して記録しているものを、名前で呼べるようにしただけ**の追加で、新しい常駐も新しい権限も
     * 増えない。呼び元は 2 か所 — 受動的なイベントの [SystemEventService.emit] と、ユーザーが自分で
     * 仕掛けた合図の [EventEmitter.emit] (`alarm` / `notify_action` / `unlock_failed` など)。
     * 前者は**検知 ON が前提**、後者は検知の ON/OFF に依存しない (記録と同じ条件に揃えてある)。
     *
     * 環境変数は `Z2_WHEN_EVENT` (イベント名) と、あれば `Z2_WHEN_EVENT_NAME` (仕掛けたときの識別名) /
     * `Z2_WHEN_ACTION` (押された通知ボタン) / `Z2_WHEN_SSID`。数値は既存と同じ `Z2_WHEN_LEVEL`。
     * `Z2_WHEN_NAME` は**ルール id** のままにしてある (既存ルールの意味を変えない)。
     */
    fun onEvent(
        context: Context,
        event: String,
        level: Int? = null,
        name: String = "",
        action: String = "",
        ssid: String = "",
    ) {
        if (event.isBlank()) return
        val app = context.applicationContext
        val rules = runCatching { loadRules(app) }.getOrDefault(emptyList())
            .filter { it.enabled && it.kind == "event" && WhenTriggerMatch.event(it.spec, event) }
        if (rules.isEmpty()) return
        val now = System.currentTimeMillis()
        rules.forEach { rule ->
            val prev = eventLastFired[rule.id]
            if (prev != null && now - prev < EVENT_MIN_INTERVAL_MS) {
                Log.i(TAG, "event $event: ${rule.id} skipped (min interval)")
                return@forEach
            }
            eventLastFired[rule.id] = now
            val env = HashMap<String, String>()
            env["Z2_WHEN_EVENT"] = event
            if (name.isNotEmpty()) env["Z2_WHEN_EVENT_NAME"] = name
            if (action.isNotEmpty()) env["Z2_WHEN_ACTION"] = action
            if (ssid.isNotEmpty()) env["Z2_WHEN_SSID"] = ssid
            runRule(app, rule, level = level ?: -1, extraEnv = env)
        }
    }

    // --- センサートリガー (opt-in・検知 FG サービス前提) ---

    /** センサーのエッジ判定用「直近で条件を満たしていたか」(rule id 単位・プロセス内メモリのみ)。 */
    private val sensorSatisfied = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** shake 判定は連続サンプルにまたがる状態を持つので 1 インスタンスを共有。 */
    private val shakeDetector = ShakeDetector()

    /**
     * enabled な sensor ルールが要求するセンサー種別集合 (`"accel"`/`"light"`/`"proximity"`)。
     * [SystemEventService] が**必要なセンサーだけ**登録するために使う (不使用なら 1 つも登録せず電池ゼロ)。
     */
    fun sensorKindsNeeded(context: Context): Set<String> =
        loadRules(context).asSequence()
            .filter { it.enabled && it.kind == "sensor" }
            .mapNotNull { WhenTriggerMatch.sensorType(it.spec) }
            .toSet()

    /** 加速度サンプル ([tMs] は単調増加ミリ秒)。shake を検出したら `sensor:shake` ルールを実行。 */
    fun onAccel(context: Context, x: Float, y: Float, z: Float, tMs: Long) {
        if (!shakeDetector.onSample(x, y, z, tMs)) return
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "sensor" && it.spec.trim() == "shake" }
            .forEach { runRule(app, it, level = -1, extraEnv = mapOf("Z2_WHEN_SENSOR" to "shake")) }
    }

    /** 照度 [lux]。`sensor:light>N`/`<N` を条件成立の立ち上がり (false→true) で実行。 */
    fun onLight(context: Context, lux: Float) = fireSensorEdge(
        context, "light",
        match = { spec -> WhenTriggerMatch.lightSatisfied(spec, lux) },
        env = { mapOf("Z2_WHEN_SENSOR" to "light", "Z2_WHEN_LUX" to lux.toString()) },
    )

    /** 近接 [near]。`sensor:proximity=near`/`=far` を条件成立の立ち上がりで実行。 */
    fun onProximity(context: Context, near: Boolean) = fireSensorEdge(
        context, "proximity",
        match = { spec -> WhenTriggerMatch.proximitySatisfied(spec, near) },
        env = { mapOf("Z2_WHEN_SENSOR" to if (near) "proximity:near" else "proximity:far") },
    )

    /**
     * 指定 [kind] の sensor ルールについて、[match] が満たされる**立ち上がり (false→true)** で実行する。
     * 直近状態を [sensorSatisfied] に保持し、初回 (prev=null) は基準を置くだけで発火しない (電池しきい値と
     * 同じエッジ判定の思想)。照度のしきい値付近でのばたつきはここでは吸収しない (将来ヒステリシス可)。
     */
    private fun fireSensorEdge(
        context: Context,
        kind: String,
        match: (String) -> Boolean,
        env: () -> Map<String, String>,
    ) {
        val app = context.applicationContext
        loadRules(app).filter {
            it.enabled && it.kind == "sensor" && WhenTriggerMatch.sensorType(it.spec) == kind
        }.forEach { rule ->
            val now = match(rule.spec)
            val prev = sensorSatisfied.put(rule.id, now)
            if (prev == false && now) runRule(app, rule, level = -1, extraEnv = env())
        }
    }

    // --- 実行 ---

    /**
     * ルールの [WhenRule.run] を現在の distro で headless 実行する。トリガー情報は環境変数
     * `Z2_WHEN_TRIGGER` / `Z2_WHEN_NAME` / `Z2_WHEN_LEVEL` と、トリガー固有の [extraEnv]
     * (`Z2_WHEN_SSID` / `Z2_WHEN_SMS_*` / `Z2_WHEN_OTP` 等) で渡す (シェルへ文字列展開しない)。
     * 出力は `~/.z2term/when/<id>.log` へ (肥大したら実行前に空にする)。
     */
    private fun runRule(context: Context, rule: WhenRule, level: Int, extraEnv: Map<String, String> = emptyMap()) {
        // 環境変数 + ユーザーコマンドを 1 本の sh -lc へ。trigger や extraEnv の値 (SSID・SMS 本文
        // 等) は外部文字列を含み得るので単一引用符へ安全にエスケープする ([shSingleQuote])。level は
        // 数値なのでそのまま。ユーザーコマンド (rule.run) は実行対象なのでクォートしない。
        val levelExport = if (level in 0..100) " export Z2_WHEN_LEVEL='$level';" else ""
        val extraExport = extraEnv.entries.joinToString("") { (k, v) -> " export $k=${HeadlessRun.shSingleQuote(v)};" }
        val script = "export Z2_WHEN_TRIGGER=${HeadlessRun.shSingleQuote(rule.trigger)}; " +
            "export Z2_WHEN_NAME=${HeadlessRun.shSingleQuote(rule.id)};" +
            "$levelExport$extraExport cd \"\$HOME\" 2>/dev/null; ${rule.run}"

        val app = context.applicationContext
        HeadlessRun.launch(
            context = context,
            script = script,
            logFile = File(whenDir(context), "${rule.id}.log"),
            name = "when-${rule.id}",
            header = HeadlessRun.logHeader(rule.trigger),
            // ルールのログを見ているライブ tail ウィジェット (D2) があれば描き直す。
            // 置かれていなければ何もしない (常駐は増やさない)。
            onExit = { runCatching { TailWidgetProvider.refresh(app) } },
        )
    }

    // --- ルールファイルの小さな書き換え (CLI と競合しない範囲で) ---

    /** [ruleId] の enabled 行だけを書き換える (`at` の自動無効化などで使う)。 */
    private fun setEnabled(context: Context, ruleId: String, enabled: Boolean) {
        val f = File(whenDir(context), "$ruleId.rule")
        val rule = runCatching { WhenRule.parse(ruleId, f.readText()) }.getOrNull() ?: return
        runCatching { f.writeText(rule.copy(enabled = enabled).serialize()) }
    }

    private fun readArmed(dir: File): List<String> =
        runCatching { File(dir, ARMED_STATE).readLines().map { it.trim() }.filter { it.isNotEmpty() } }
            .getOrDefault(emptyList())

    private fun writeArmed(dir: File, ids: List<String>) {
        runCatching { File(dir, ARMED_STATE).writeText(ids.joinToString("\n")) }
    }
}
