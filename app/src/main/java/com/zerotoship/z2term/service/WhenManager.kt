package com.zerotoship.z2term.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.WhenRule
import com.zerotoship.z2term.settings.WhenTriggerCatalog
import com.zerotoship.z2term.widget.StatusWidgetProvider
import com.zerotoship.z2term.widget.TailWidgetProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * `z2-when` 自動化ハブの中核 (A6・stage 1)。ルールファイル (`~/.z2term/when/<id>.rule`) を読み、
 * トリガー (充電 / 電池 / 時刻) の発火時に [WhenRule.run] を Linux エンジンで実行する。
 *
 * **常駐を増やさない設計** (引き継ぎ書 §10-1):
 *  - 充電 (`charge:*`) / 電池 (`battery:*`) は manifest レシーバ ([WhenReceiver]) が受ける。
 *    `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` は暗黙ブロードキャスト
 *    制限の**例外**なので、専用のフォアグラウンドサービス (= 追加の常駐通知・WakeLock) 無しで拾える。
 *  - 時刻 (`time:*`) は [AlarmManager] ([ExactAlarm]: 置けるなら正確・駄目なら Doze 貫通の不正確)。
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

    /**
     * 一時停止フラグ (**存在＝停止中**)。DataStore ではなく**ファイル**で持つのは、
     * `z2-when pause` (端末側 CLI) とアプリの設定画面が**同じ 1 つの真実**を見るため。
     * ルール自体がファイルなのと揃えてあり、端末から `ls ~/.z2term/when/` すれば状態が分かる。
     */
    private const val PAUSED_STATE = ".paused"

    /** 直近の発火の記録 (1 行 1 件・古いものが先頭)。「さっき何が走ったか」を 1 か所で見るため。 */
    private const val FIRED_LOG = ".fired"

    /** [FIRED_LOG] に残す件数。ファイルが太らない範囲で、直近の様子が分かるだけの長さ。 */
    private const val FIRED_KEEP = 50

    /** `cooldown=` 判定用の「最後に実行した時刻」(`id=エポックミリ秒`)。[readLastFire] 参照。 */
    private const val LASTFIRE_STATE = ".lastfire"

    /** ルールディレクトリ (`filesDir/shared_home/.z2term/when` = 端末からは `~/.z2term/when`)。 */
    fun whenDir(context: Context): File =
        File(File(context.filesDir, "shared_home"), ".z2term/when")

    /**
     * ルールファイルをすべて読む。壊れた 1 件は飛ばす。
     *
     * 並びは `order` があるものが先 (その値の順)、無いものは id 順で後ろ。id は登録時刻由来なので、
     * **一度も並べ替えていなければ従来どおり登録順**になり、端末から `z2-when` で足した新しい
     * ルールは (order を持たないので) 末尾に付く。
     */
    fun loadRules(context: Context): List<WhenRule> {
        val dir = whenDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".rule") } ?: return emptyList()
        return files.sortedBy { it.name }.mapNotNull { f ->
            runCatching { WhenRule.parse(f.name.removeSuffix(".rule"), f.readText()) }.getOrNull()
        }.sortedBy { if (it.order == WhenRule.NO_ORDER) Int.MAX_VALUE else it.order }
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
        // ルールが増減したら見張るフォルダも変わる (センサーと同じ扱い)。
        SystemEventService.refreshFileWatchersIfRunning()
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
        // 置き方は [ExactAlarm] に 1 本化 (0.8.332)。⚠ 繰り返しのリマインドはここを通るので、
        // z2-alarm 側だけ正確にしても「毎日 7 時」は遅れたままになる。3 系統を揃えること。
        ExactAlarm.setWakeup(am, at, timePending(context, ruleId), "when $ruleId")
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

    // --- 回線トリガー (net:*) ---

    /**
     * 既定回線の変化を受けて `net:online` / `net:offline` / `net:wifi` / `net:mobile` /
     * `net:ethernet` を実行する (0.8.264)。呼び元は [SystemEventService.handleNet]
     * (種別が変わったときだけ・エッジ判定は [WhenTriggerMatch.net])。
     *
     * `wifi:*` との違いは**モバイル回線も見る**こと。`wifi:disconnect` は「Wi‑Fi が切れた」
     * までしか言えず、そのあとモバイルで通信できているのか本当に圏外なのかを区別できない。
     * 「通信できるようになったら送る」「圏外になったら止める」はここでしか書けない。
     *
     * 環境変数は `Z2_WHEN_NET` (今の回線種別) と `Z2_WHEN_NET_PREV` (直前の種別)。
     * どちらへ変わったかで分岐するマクロを 1 本で書けるようにするため両方渡す。
     */
    fun onNet(context: Context, now: String, prev: String) {
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "net" }.forEach { rule ->
            if (WhenTriggerMatch.net(rule.spec, now, prev)) {
                runRule(
                    app, rule, level = -1,
                    extraEnv = mapOf("Z2_WHEN_NET" to now, "Z2_WHEN_NET_PREV" to prev)
                )
            }
        }
    }

    // --- 共有トリガー (share:*) ---

    /**
     * 他アプリの共有シートから届いたものを受けて `share:*` ルールを実行する (0.8.266)。
     * 呼び元は [com.zerotoship.z2term.MainActivity]（共有の受け口はアプリの起動経路なので、
     * 検知フォアグラウンドサービスには**依存しない**）。
     *
     * **端末への挿入は今までどおり行われる**（ルールが動いても消さない）。共有は「入れるだけ・
     * 実行しない」と約束してある入口で、ルールを 1 本足したら挿入が黙って止まる、では
     * 既にある使い方を壊す。ルールは**足し算**で、入力行に残るのは実行されていないただの文字列。
     *
     * 環境変数は `Z2_WHEN_SHARE`（端末に入るのと同じ文字列＝テキストそのもの、またはファイルの
     * パス）と `Z2_WHEN_SHARE_KIND`（`text` / `file`）。外部入力なので env で渡す（`eval` させない）。
     */
    fun onShare(context: Context, kind: String, text: String, fileNames: List<String>) {
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "share" }.forEach { rule ->
            if (WhenTriggerMatch.share(rule.spec, kind, text, fileNames)) {
                runRule(
                    app, rule, level = -1,
                    extraEnv = mapOf("Z2_WHEN_SHARE" to text, "Z2_WHEN_SHARE_KIND" to kind)
                )
            }
        }
    }

    // --- 起動トリガー (boot) ---

    /**
     * 端末の起動を受けて `boot` ルールを実行する (0.8.264)。呼び元は [BootReceiver]。
     *
     * `BOOT_COMPLETED` は**暗黙ブロードキャスト制限の例外**なので manifest 宣言のレシーバへ
     * 確実に届く。つまり**検知フォアグラウンドサービスが OFF でも動く**数少ないトリガーで、
     * 「再起動したら常駐サーバーを上げ直す」のような後始末をアプリを開かずに書ける。
     *
     * ⚠ 引数を取らないので [WhenRule.spec] は空。`boot:` と書かれても同じルールとして扱う
     * (spec を見ないため)。
     */
    fun onBoot(context: Context) {
        val app = context.applicationContext
        loadRules(app).filter { it.enabled && it.kind == "boot" }.forEach { rule ->
            runRule(app, rule, level = -1)
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

    // --- 通知トリガー (notify:*・通知アクセス許可 + 通知検知 ON が前提) ---

    /**
     * 届いた通知で `notify:*` ルールを実行する (0.8.236)。
     *
     * 呼び元は [NotificationLogService.onNotificationPosted]。**ログ保存 (`notificationLogEnabled`)
     * とは独立**に動く — 「記録はしないがトリガーには使いたい」が普通の使い方で、記録を必須に
     * すると通知本文がずっとファイルに残ることになる。
     *
     * 本文・タイトルは外部由来なので env 渡し (`Z2_WHEN_NOTI_*`)。`notify:otp` のときは
     * 抽出したコードを `Z2_WHEN_OTP` に入れる (`sms:otp` と同じ名前にして覚えることを増やさない)。
     *
     * [category] は通知の種別 (`Notification.category`)。`notify:category=` の判定に使うほか、
     * `Z2_WHEN_NOTI_CATEGORY` として渡す — 同じ電話アプリからでも「着信中 (`call`)」と
     * 「不在着信 (`missed_call`)」を**マクロ側で見分けられる**ようにするため。
     */
    fun onNotification(
        context: Context,
        pkg: String,
        app: String,
        title: String,
        text: String,
        category: String = ""
    ) {
        val ctx = context.applicationContext
        loadRules(ctx).filter { it.enabled && it.kind == "notify" }.forEach { rule ->
            if (!WhenTriggerMatch.notify(rule.spec, pkg, app, title, text, category)) return@forEach
            val env = HashMap<String, String>()
            env["Z2_WHEN_NOTI_PKG"] = pkg
            env["Z2_WHEN_NOTI_APP"] = app
            env["Z2_WHEN_NOTI_TITLE"] = title
            env["Z2_WHEN_NOTI_TEXT"] = text
            env["Z2_WHEN_NOTI_CATEGORY"] = category
            if (rule.spec.trim() == "otp") {
                val code = WhenTriggerMatch.extractOtp(text).ifEmpty { WhenTriggerMatch.extractOtp(title) }
                env["Z2_WHEN_OTP"] = code
            }
            runRule(ctx, rule, level = -1, extraEnv = env)
        }
    }

    // --- ファイル出現トリガー (file:new・検知 FG サービス前提) ---

    /**
     * 直近に処理したファイルのパスと時刻。**同じファイルで二重に走らせない**ために持つ。
     * `CLOSE_WRITE` と `MOVED_TO` は同じ 1 個のファイルに対して両方来ることがあり、
     * 素通しにするとマクロが 2 回走る。
     */
    private val recentFiles = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 同じパスをこの間隔内に見たら無視する。 */
    private const val FILE_DEDUP_MS = 5_000L

    /**
     * enabled な `file:new=…` ルールが見張るフォルダの集合 (0.8.235)。
     *
     * センサーと同じ考え方で、**該当ルールがあるフォルダだけ**を [SystemEventService] が
     * `FileObserver` で監視する。1 件も無ければ 1 つも登録しない = 電池も CPU も使わない。
     */
    fun fileDirsNeeded(context: Context): Set<String> =
        loadRules(context).asSequence()
            .filter { it.enabled && it.kind == "file" }
            .mapNotNull { WhenTriggerMatch.fileDir(it.spec) }
            .toSet()

    /**
     * [dir] に [fileName] が現れたときに `file:new=…` ルールを実行する。
     *
     * 呼び元は検知 ON のときの [SystemEventService] の `FileObserver`。書き込み完了
     * (`CLOSE_WRITE`) と移動 (`MOVED_TO`) を見るので、**コピー途中のファイルは掴まない**。
     * フルパスは `Z2_WHEN_FILE`、フォルダは `Z2_WHEN_DIR` で渡す (外部由来なので env 渡し)。
     */
    fun onFileCreated(context: Context, dir: String, fileName: String) {
        val app = context.applicationContext
        val path = "$dir/$fileName"
        val now = System.currentTimeMillis()
        val prev = recentFiles.put(path, now)
        if (prev != null && now - prev < FILE_DEDUP_MS) return
        // 溜まりすぎたら古いものから捨てる (見張り続けると際限なく増える)。
        if (recentFiles.size > 256) {
            recentFiles.entries.removeIf { now - it.value > FILE_DEDUP_MS * 4 }
        }
        loadRules(app).filter {
            it.enabled && it.kind == "file" &&
                WhenTriggerMatch.fileDir(it.spec) == dir &&
                WhenTriggerMatch.fileMatches(it.spec, fileName)
        }.forEach { rule ->
            runRule(
                app, rule, level = -1,
                extraEnv = mapOf("Z2_WHEN_FILE" to path, "Z2_WHEN_DIR" to dir)
            )
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

    // --- 一時停止 (キルスイッチ) と発火の記録 ---

    /**
     * 自動化が一時停止中か。**トリガーで勝手に走るものだけ**を止めるスイッチで、
     * ウィジェットのボタンや `z2-macro` のように**人が押して走らせるもの**は対象外
     * (「暴走を止めたい」のであって「自分で動かすのも禁じたい」わけではない)。
     */
    fun isPaused(context: Context): Boolean =
        File(whenDir(context), PAUSED_STATE).exists()

    /**
     * 一時停止を切り替える。時刻トリガーの AlarmManager 予約は**解除しない** —
     * 予約を捨てると再開時に貼り直しが要り、`time:at` の「次の 1 回」も失われる。
     * 発火はしても [runRule] の入口で弾くので、止まっていることに変わりはない。
     */
    fun setPaused(context: Context, paused: Boolean) {
        val f = File(whenDir(context).apply { mkdirs() }, PAUSED_STATE)
        runCatching { if (paused) f.writeText("paused\n") else f.delete() }
        Log.i(TAG, if (paused) "automation paused" else "automation resumed")
    }

    /**
     * 直近の発火を新しい順に [limit] 件返す。1 行 = 1 件で、[firedLine] の TSV。
     * 「いま何が走っているか」ではなく「**さっき何が走ったか**」を見るためのもの。
     */
    fun recentFires(context: Context, limit: Int = 20): List<String> =
        runCatching { File(whenDir(context), FIRED_LOG).readLines() }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() }
            .takeLast(limit)
            .asReversed()

    /** 発火 1 件。[status] は `run` (実行した) / `paused` (止められた) / `manual` (画面から試した)。 */
    data class Fired(val time: String, val ruleId: String, val trigger: String, val status: String)

    /** 発火 1 件の TSV 行 (Android 非依存・テスト用に切り出す)。 */
    internal fun firedLine(timeIso: String, id: String, trigger: String, status: String): String =
        listOf(timeIso, id, trigger, status).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    /** [firedLine] の逆。壊れた行は null (古い形式が混ざっても落ちない)。 */
    internal fun parseFired(line: String): Fired? {
        val p = line.split('\t')
        if (p.size < 4) return null
        return Fired(time = p[0], ruleId = p[1], trigger = p[2], status = p[3])
    }

    /** 直近の発火を新しい順にパースして返す (画面表示用)。 */
    fun recentFiredEntries(context: Context, limit: Int = 20): List<Fired> =
        recentFires(context, limit).mapNotNull { parseFired(it) }

    /** ルール id → 最後に発火した時刻 (`paused` で止まった分は含めない)。 */
    fun lastFiredByRule(context: Context): Map<String, String> {
        val out = HashMap<String, String>()
        // recentFires は新しい順なので、最初に見つかったものが最新。
        recentFires(context, FIRED_KEEP).mapNotNull { parseFired(it) }
            .filter { it.status != "paused" }
            .forEach { out.putIfAbsent(it.ruleId, it.time) }
        return out
    }

    /** 記録を [keep] 件に切り詰める (古い方から捨てる)。 */
    internal fun trimFired(lines: List<String>, keep: Int): List<String> =
        lines.filter { it.isNotBlank() }.takeLast(keep)

    /** 発火 (または一時停止でスキップしたこと) を [FIRED_LOG] へ 1 行足す。 */
    private fun appendFired(context: Context, rule: WhenRule, status: String) {
        runCatching {
            val dir = whenDir(context).apply { mkdirs() }
            val f = File(dir, FIRED_LOG)
            // SimpleDateFormat はスレッド安全でなく、ここは複数スレッドから呼ばれるので都度作る。
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            val line = firedLine(iso, rule.id, rule.trigger, status)
            val kept = trimFired(runCatching { f.readLines() }.getOrDefault(emptyList()) + line, FIRED_KEEP)
            f.writeText(kept.joinToString("\n", postfix = "\n"))
        }.onFailure { Log.w(TAG, "fired log failed: ${it.message}") }
    }

    // --- 絞り込み (if / between / days / cooldown) ---

    /**
     * このルールを**いま実行してよいか**を見て、駄目なら理由 ([WhenGuard] の `skip:*`) を返す。
     * 実行してよければ null。
     *
     * 判定は**安い順**に並べてある — 時計を見るだけの `between`/`days`、ファイルを 1 つ読む
     * `cooldown`、端末の状態を集める `if` の順。手前で弾ければ後ろは評価しない。
     */
    private fun skipReason(context: Context, rule: WhenRule): String? {
        val cal = Calendar.getInstance()
        if (rule.between.isNotEmpty()) {
            val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (!WhenGuard.inWindow(rule.between, minute)) return WhenGuard.SKIP_BETWEEN
        }
        if (rule.days.isNotEmpty()) {
            // Calendar は 1 = 日曜。WhenGuard は 0 = 日曜なので 1 引いて渡す。
            if (!WhenGuard.dayAllowed(rule.days, cal.get(Calendar.DAY_OF_WEEK) - 1)) {
                return WhenGuard.SKIP_DAYS
            }
        }
        if (rule.cooldown.isNotEmpty()) {
            val ms = WhenGuard.cooldownMs(rule.cooldown)
            val last = readLastFire(context)[rule.id] ?: 0L
            if (ms > 0 && last > 0 && System.currentTimeMillis() - last < ms) {
                return WhenGuard.SKIP_COOLDOWN
            }
        }
        if (rule.condition.isNotEmpty()) {
            if (!WhenGuard.conditionsMet(rule.condition, Z2ApiBridge.stateSnapshot(context))) {
                return WhenGuard.SKIP_IF
            }
        }
        return null
    }

    /**
     * `cooldown=` 判定用の「最後に実行した時刻」(`id=エポックミリ秒` の行)。
     *
     * [FIRED_LOG] は 50 件で切り詰めるので**判定の根拠にはできない** (発火の多いルールが
     * 混ざると、直前に走ったことが記録から押し出される)。専用の小さなファイルで持つ。
     */
    private fun readLastFire(context: Context): Map<String, Long> =
        runCatching {
            File(whenDir(context), LASTFIRE_STATE).readLines().mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val t = line.substring(eq + 1).trim().toLongOrNull() ?: return@mapNotNull null
                line.substring(0, eq).trim() to t
            }.toMap()
        }.getOrDefault(emptyMap())

    /** [ruleId] の最終実行時刻を今にする ([cooldown][WhenRule.cooldown] があるルールだけ呼ぶ)。 */
    private fun markFired(context: Context, ruleId: String) {
        runCatching {
            val dir = whenDir(context).apply { mkdirs() }
            val updated = readLastFire(context) + (ruleId to System.currentTimeMillis())
            File(dir, LASTFIRE_STATE)
                .writeText(updated.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
        }.onFailure { Log.w(TAG, "lastfire write failed: ${it.message}") }
    }

    /** ルールを消したときに [LASTFIRE_STATE] の行も落とす (消えた id の記録を残さない)。 */
    private fun forgetLastFire(context: Context, ruleId: String) {
        runCatching {
            val rest = readLastFire(context) - ruleId
            val f = File(whenDir(context), LASTFIRE_STATE)
            if (rest.isEmpty()) f.delete()
            else f.writeText(rest.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
        }
    }

    // --- 実行 ---

    /**
     * ルールの [WhenRule.run] を現在の distro で headless 実行する。トリガー情報は環境変数
     * `Z2_WHEN_TRIGGER` / `Z2_WHEN_NAME` / `Z2_WHEN_LEVEL` と、トリガー固有の [extraEnv]
     * (`Z2_WHEN_SSID` / `Z2_WHEN_SMS_*` / `Z2_WHEN_OTP` 等) で渡す (シェルへ文字列展開しない)。
     * 出力は `~/.z2term/when/<id>.log` へ (肥大したら実行前に空にする)。
     */
    private fun runRule(
        context: Context,
        rule: WhenRule,
        level: Int,
        extraEnv: Map<String, String> = emptyMap(),
        manual: Boolean = false,
    ) {
        // キルスイッチ。**全トリガー共通の 1 か所**で弾くので、トリガーを増やしても止め忘れが起きない。
        // 止めたことも記録する — 「なぜ動かないのか」が分からないまま探させないため。
        // [manual] (画面の ▶ で試した) は**人が押したもの**なので止めない。キルスイッチは
        // 「勝手に走るもの」を止めるためのもので、自分で動かすことまで禁じる設定ではない。
        if (!manual && isPaused(context)) {
            appendFired(context, rule, "paused")
            Log.i(TAG, "paused: skip ${rule.id} (${rule.trigger})")
            return
        }
        // 絞り込み (`if=` / `between=` / `days=` / `cooldown=`)。**キルスイッチと同じくここ 1 か所**で
        // 見るので、トリガーを増やしても効かせ忘れが起きない。[manual] (画面の ▶) は素通り —
        // 条件で動かないと「試して確かめる」手段が無くなるため (一時停止と同じ扱い)。
        if (!manual && rule.hasFilters) {
            val skip = skipReason(context, rule)
            if (skip != null) {
                appendFired(context, rule, skip)
                Log.i(TAG, "$skip: ${rule.id} (${rule.trigger})")
                return
            }
        }
        appendFired(context, rule, if (manual) "manual" else "run")
        if (rule.cooldown.isNotEmpty()) markFired(context, rule.id)

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

    // --- 画面 (自動化タブ) から使う操作 ---

    /**
     * ルールを**トリガーを待たずに 1 回実行する**（画面の ▶）。
     *
     * 「充電したらバックアップ」を確かめるのに充電を抜き差しさせるのは無理があるので、
     * 出口をここに用意する。一時停止中でも動く（[runRule] の `manual` 参照）。
     * 環境変数は実際の発火と同じ形で渡すが、トリガー固有の値（SSID・SMS 本文など）は
     * **無い**（作り物の値を渡すと「試したら動いたのに本番で動かない」を生むため）。
     */
    fun runNow(context: Context, rule: WhenRule) {
        runRule(context, rule, level = -1, extraEnv = mapOf("Z2_WHEN_MANUAL" to "1"), manual = true)
    }

    /** ルールの有効 / 無効を切り替えて、時刻トリガーを貼り直す。 */
    fun setRuleEnabled(context: Context, ruleId: String, enabled: Boolean) {
        setEnabled(context, ruleId, enabled)
        reload(context)
    }

    /**
     * 画面から作った / 直したルールを書く（0.8.272）。書けたら true。
     *
     * **正本はテキストファイル**という設計は変えないので、書き先も書式も CLI と同じ
     * （[WhenRule.serialize]）。画面で直したものを端末の `z2-when list` がそのまま読めるし、
     * 端末で足したものを画面から直せる。時刻トリガーを貼り直すため、書けたら [reload] する。
     *
     * ⚠ 呼ぶ前に [com.zerotoship.z2term.settings.WhenTriggerCatalog] で検査すること。
     * ここは書くだけで、書式は見ない（CLI から来た手書きのルールを画面が拒めると、
     * 直すために端末へ戻らされて本末転倒になる）。
     */
    fun saveRule(context: Context, rule: WhenRule): Boolean {
        val f = File(whenDir(context), "${rule.id}.rule")
        val ok = runCatching {
            f.parentFile?.mkdirs()
            f.writeText(rule.serialize())
        }.onFailure { Log.w(TAG, "saveRule failed (${rule.id}): ${it.message}") }.isSuccess
        if (ok) reload(context)
        return ok
    }

    /**
     * 新しいルールの id。
     *
     * CLI (`z2-when`) の `w<エポック秒><pid>` と**同じ見た目**にする（一覧に並んだときに
     * どちらで作ったか分からない方がよい）。アプリには pid の代わりが無いのでミリ秒を使い、
     * 万一ぶつかったら連番を足す（CLI と同じ二重の防御）。
     */
    fun newRuleId(context: Context): String {
        val dir = whenDir(context)
        val now = System.currentTimeMillis()
        val base = "w${now / 1000}${(now % 1000).toString().padStart(3, '0')}"
        var id = base
        var n = 0
        while (File(dir, "$id.rule").exists()) {
            n++
            id = "$base-$n"
        }
        return id
    }

    /**
     * `run` が指しているスクリプトの中身（末尾 [maxChars] 文字）。読めなければ null。
     *
     * 自動化タブで「このルールが何をするのか」を見るための**読み取り専用**の窓口
     * （0.8.272）。ルール行に出るのはコマンドの 1 行だけで、それがスクリプトのパスだと
     * 中身が分からず、端末を開くまで何をするルールなのか確かめられなかった。
     *
     * 読めるのは**共有 HOME の下だけ**。`~/` はそこへ読み替え、絶対パスも共有 HOME の
     * 中に収まっているものだけ返す — ルールの文字列を変えれば端末のどこでも覗ける、
     * という穴を開けないため。
     */
    fun readRunScript(context: Context, run: String, maxChars: Int = 8000): String? {
        val path = WhenTriggerCatalog.scriptPathIn(run) ?: return null
        val home = File(context.filesDir, "shared_home")
        val file = when {
            path.startsWith("~/") -> File(home, path.removePrefix("~/"))
            path.startsWith("/root/") -> File(home, path.removePrefix("/root/"))
            else -> return null
        }
        return runCatching {
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(home.canonicalFile.path)) return null
            if (!canonical.isFile) return null
            val text = canonical.readText()
            if (text.length > maxChars) text.takeLast(maxChars) else text
        }.getOrNull()
    }

    /**
     * 自動化タブでドラッグして決めた並びを、各ルールファイルの `order=` として書く（0.8.249）。
     *
     * **並び順専用のファイルは作らない** — ルールファイルが正本という設計を崩さないため。
     * CLI (`z2-when`) は `order` を書かないが、`on` / `off` は enabled 行だけを sed で
     * 書き換えるので、ここで書いた並びは端末から操作しても消えない。
     * 実行やトリガーには一切影響しない（表示順だけ）。
     */
    fun reorderRules(context: Context, ids: List<String>) {
        val dir = whenDir(context)
        ids.forEachIndexed { index, id ->
            val f = File(dir, "$id.rule")
            val rule = runCatching { WhenRule.parse(id, f.readText()) }.getOrNull()
                ?: return@forEachIndexed
            if (rule.order == index) return@forEachIndexed
            runCatching { f.writeText(rule.copy(order = index).serialize()) }
                .onFailure { Log.w(TAG, "reorder failed ($id): ${it.message}") }
        }
    }

    /** ルールとそのログを消して、時刻トリガーを貼り直す。 */
    fun removeRule(context: Context, ruleId: String) {
        runCatching { File(whenDir(context), "$ruleId.rule").delete() }
        runCatching { File(whenDir(context), "$ruleId.log").delete() }
        forgetLastFire(context, ruleId)
        reload(context)
    }

    /** ルールの実行ログ（末尾 [maxChars] 文字）。無ければ空。 */
    fun readRuleLog(context: Context, ruleId: String, maxChars: Int = 8000): String =
        runCatching {
            val text = File(whenDir(context), "$ruleId.log").readText()
            if (text.length > maxChars) text.takeLast(maxChars) else text
        }.getOrDefault("")

    /** ルールの実行ログを空にする。 */
    fun clearRuleLog(context: Context, ruleId: String) {
        runCatching { File(whenDir(context), "$ruleId.log").writeText("") }
    }

    /**
     * 有効なルールのうち、**検知（[SystemEventService]）が ON でないと動かない**ものの件数。
     * 検知 OFF のまま登録しても**黙って動かない**ので、画面で警告を出すために使う。
     * 時刻と SMS は検知に依存しない。`event:` は受動イベントなら依存するが、`alarm` /
     * `notify_action` のように自分で仕掛けたものは依存しない — 名前だけでは判別できないので、
     * 「依存しうる」側に数えて警告する（黙って動かないより、余計に注意する方がまし）。
     * `boot` は manifest 宣言のレシーバで受けるので**依存しない**（0.8.264）。
     */
    fun rulesNeedingDetection(context: Context): Int =
        loadRules(context).count {
            it.enabled && it.kind in setOf("charge", "battery", "wifi", "net", "sensor", "event")
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
