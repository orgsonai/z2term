package com.zerotoship.z2term.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * システムイベント検知の常駐部 (汎用入口 / 通知検知の姉妹機能)。
 *
 * 画面 ON/OFF・ロック解除 (USER_PRESENT)・電池残量変化 (BATTERY_CHANGED) 等は Android 8+ では
 * **manifest 宣言のレシーバでは配信されない** (暗黙ブロードキャスト制限)。そのため生きたプロセス内で
 * `registerReceiver` した動的レシーバでしか拾えない。この専用フォアグラウンドサービスを opt-in で常駐
 * させ、その中で各イベントの動的レシーバを登録する。
 *
 * 設定 [AppSettings.systemEventCaptureEnabled] が ON のとき、拾ったイベントを [logFile]
 * (`~/.z2term/events.jsonl`) へ 1 行 1 イベントで追記する。加工・絞り込み・配信は一切ハードコードせず、
 * ユーザーがターミナル側 (tail / 自作スクリプト / 常駐サーバー) で自由に組む。完全ローカル・外部送信なし。
 *
 * 拾うイベント (`{event}` の値):
 *  - `screen_on` / `screen_off`  … 画面点灯 / 消灯
 *  - `unlocked`                  … ロック解除 (USER_PRESENT)
 *  - `power_connected` / `power_disconnected` … 充電開始 / 停止 (`{level}` に残量%)
 *  - `battery_low` / `battery_okay`           … 電池残量 低下 / 回復 (`{level}` に残量%)
 *  - `battery_level`                          … 残量が 10% 刻みの境界を跨いだとき (`{level}` に残量%)
 *  - `wifi_connected` / `wifi_disconnected`   … Wi‑Fi 接続 / 切断 (`{ssid}` に SSID・取得可能な場合のみ)
 *  - `headset_plugged` / `headset_unplugged`  … 有線ヘッドセットの抜き差し
 *  - `airplane_on` / `airplane_off`           … 機内モード ON / OFF
 *  - `ringer_normal` / `ringer_vibrate` / `ringer_silent` … マナーモード切替
 *  - `bt_audio_connected` / `bt_audio_disconnected` … Bluetooth オーディオ (A2DP/SCO) の接続 / 切断
 *
 * このサービスとは別に、時刻トリガー ([AlarmScheduler] / `z2-alarm`) が同じ events.jsonl へ
 * `alarm` イベント (`{name}` 付き) を書く。そちらはこのサービスの ON/OFF に依存しない。
 */
class SystemEventService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var captureEnabled = false
    @Volatile private var formatTemplate = ""
    @Volatile private var prepend = false
    @Volatile private var lastWifiConnected: Boolean? = null

    @Volatile private var lastBatteryBucket = -1

    /**
     * Bluetooth オーディオ (A2DP/SCO) の抜き差しを拾うコールバック。
     *
     * 有線は `ACTION_HEADSET_PLUG` で拾えるが、**ワイヤレスイヤホンには相当するブロードキャストが
     * 無い**ため「イヤホンを繋いだら再生」のような定番マクロが無線で書けなかった。
     * `AudioDeviceCallback` なら**追加権限なし**で接続/切断とデバイス種別が取れる
     * (`BLUETOOTH_CONNECT` が要るのはデバイス名の取得で、ここでは名前を出さない)。
     *
     * 登録直後に「既に繋がっているデバイス」で `onAudioDevicesAdded` が 1 度呼ばれる仕様なので、
     * [btCallbackPrimed] が立つまでは発火しない (サービス起動＝接続、と誤検知しないため)。
     */
    @Volatile private var btCallbackPrimed = false
    @Volatile private var lastBtAudio = false

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) = syncBtAudio()
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) = syncBtAudio()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> emit("screen_on")
                Intent.ACTION_SCREEN_OFF -> emit("screen_off")
                Intent.ACTION_USER_PRESENT -> emit("unlocked")
                Intent.ACTION_POWER_CONNECTED -> emit("power_connected", level = batteryLevel())
                Intent.ACTION_POWER_DISCONNECTED -> emit("power_disconnected", level = batteryLevel())
                Intent.ACTION_BATTERY_LOW -> emit("battery_low", level = batteryLevel())
                Intent.ACTION_BATTERY_OKAY -> emit("battery_okay", level = batteryLevel())
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryLevel(intent)
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> handleWifi()
                Intent.ACTION_HEADSET_PLUG ->
                    emit(if (intent.getIntExtra("state", 0) == 1) "headset_plugged" else "headset_unplugged")
                Intent.ACTION_AIRPLANE_MODE_CHANGED ->
                    emit(if (intent.getBooleanExtra("state", false)) "airplane_on" else "airplane_off")
                AudioManager.RINGER_MODE_CHANGED_ACTION -> handleRinger(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundInternal()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // 設定を購読してキャッシュ (イベントごとに DataStore を叩かない)。
        scope.launch {
            AppSettings(applicationContext).flow.collectLatest {
                captureEnabled = it.systemEventCaptureEnabled
                formatTemplate = it.systemEventLogFormat
                prepend = it.systemEventLogPrepend
            }
        }
        // 動的レシーバ登録 (manifest では配信されないイベント群を拾うため)。
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        // Bluetooth オーディオの抜き差しはブロードキャストでは拾えないのでコールバックで受ける。
        runCatching {
            val am = getSystemService(AUDIO_SERVICE) as? AudioManager
            am?.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(mainLooper))
        }.onFailure { Log.w(TAG, "audio device callback 登録失敗", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
        runCatching {
            (getSystemService(AUDIO_SERVICE) as? AudioManager)
                ?.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        scope.cancel()
        writer.shutdown()
    }

    private fun batteryLevel(): Int? = runCatching {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()

    /**
     * Wi‑Fi の接続/切断を状態変化として 1 回だけ発火 (連続する同一状態は抑制)。
     *
     * 接続判定は `ConnectivityManager` で行う。`WifiManager.connectionInfo` は Android 12+ で
     * **呼び出し元がフォアグラウンドでないと無効値 (networkId = -1) を返す**ため、画面消灯中など
     * まさにイベントを拾いたい場面で「常に未接続」に見え、`wifi_connected` を取りこぼしていた
     * (`z2-state` 側で実機再現。同じ理由でそちらも ConnectivityManager へ寄せてある)。
     * SSID だけは `WifiInfo` 経由でしか取れず位置情報権限も要るので、取れなければ空文字。
     */
    private fun handleWifi() {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val connected = runCatching {
            val net = cm?.activeNetwork ?: return@runCatching false
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        if (connected == lastWifiConnected) return
        lastWifiConnected = connected
        if (connected) {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val raw = runCatching { wm?.connectionInfo?.ssid }.getOrNull().orEmpty()
            val ssid = raw.trim('"').let { if (it.isBlank() || it == "<unknown ssid>") "" else it }
            emit("wifi_connected", ssid = ssid)
            // z2-when (A6 stage2) の wifi トリガー。状態変化のときだけ呼ばれる (上の抑制で担保)。
            runCatching { WhenManager.onWifi(applicationContext, connected = true, ssid = ssid) }
        } else {
            emit("wifi_disconnected")
            runCatching { WhenManager.onWifi(applicationContext, connected = false, ssid = "") }
        }
    }

    /**
     * ACTION_BATTERY_CHANGED は高頻度なので、残量が **10% 刻みの境界を跨いだとき**だけ
     * `battery_level` を発火する (サービス起動直後の初回はベースライン設定のみで発火しない)。
     */
    private fun handleBatteryLevel(intent: Intent) {
        val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (lvl < 0 || scale <= 0) return
        val pct = lvl * 100 / scale
        val bucket = pct / 10
        if (lastBatteryBucket == -1) { lastBatteryBucket = bucket; return }
        if (bucket != lastBatteryBucket) {
            lastBatteryBucket = bucket
            emit("battery_level", level = pct)
            // z2-when (A6) の電池しきい値も 10% 刻みの境界で評価する。検知 OFF でも受動的な
            // 電源/低電池ブロードキャストで評価されるが、検知 ON のときはここで細かく拾える。
            // エッジ判定なので二重に呼ばれても跨いだ瞬間しか発火しない。
            runCatching { WhenManager.onBatteryChanged(applicationContext, pct) }
        }
    }

    /**
     * Bluetooth オーディオ出力の有無を見て、変化したときだけ 1 回発火する。
     * 対象は A2DP (音楽) と SCO (通話用ヘッドセット)。デバイス名は権限が要るので出さない。
     */
    private fun syncBtAudio() {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val connected = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }.getOrDefault(false)
        // 登録直後の初回コールバックは現状の取り込みだけ行い、イベントは出さない。
        if (!btCallbackPrimed) {
            btCallbackPrimed = true
            lastBtAudio = connected
            return
        }
        if (connected == lastBtAudio) return
        lastBtAudio = connected
        emit(if (connected) "bt_audio_connected" else "bt_audio_disconnected")
    }

    /** マナーモード変化を normal/vibrate/silent として発火。 */
    private fun handleRinger(intent: Intent) {
        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        val name = when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> "ringer_normal"
            AudioManager.RINGER_MODE_VIBRATE -> "ringer_vibrate"
            AudioManager.RINGER_MODE_SILENT -> "ringer_silent"
            else -> return
        }
        emit(name)
    }

    private fun emit(event: String, level: Int? = null, ssid: String = "") {
        if (!captureEnabled) return
        val now = System.currentTimeMillis()
        val line = render(
            formatTemplate,
            ts = now, time = ISO.format(Date(now)),
            event = event, level = level, ssid = ssid
        )
        val ctx = applicationContext
        val prependNow = prepend
        writer.execute {
            runCatching {
                LogWriter.write(logFile(ctx), line, prependNow)
            }.onFailure { Log.w(TAG, "write failed: ${it.message}") }
        }
    }

    private fun startForegroundInternal() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.event_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.event_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, SystemEventService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.event_notification_title))
            .setContentText(getString(R.string.event_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPending)
            .addAction(0, getString(R.string.event_action_stop), stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "SystemEvent"
        private const val CHANNEL_ID = "z2term_events"
        private const val NOTIFICATION_ID = 1003
        const val ACTION_STOP = "com.zerotoship.z2term.EVENTS_STOP"
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

        /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。ターミナルからは `~/.z2term/events.jsonl`。 */
        const val LOG_REL = ".z2term/events.jsonl"

        /** ログの実ファイル (`filesDir/shared_home/.z2term/events.jsonl`)。 */
        fun logFile(context: Context): File =
            File(File(context.filesDir, "shared_home"), LOG_REL)

        /** 設定 ON のとき FG サービスを起動、OFF のとき停止。idempotent。 */
        fun sync(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, SystemEventService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SystemEventService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        private fun oneline(s: String): String =
            s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')

        /**
         * 1 イベントを [template] に沿って 1 行分の文字列 (末尾改行なし) にする。
         * [template] が空なら JSONL。プレースホルダ `{time}` `{ts}` `{event}` `{level}` `{ssid}`
         * `{name}` `{action}` と、エスケープ `\n` `\t` `\\` に対応。該当しないイベントでは空文字になる。
         * [name] は仕掛けたときの識別名 (時刻トリガー `alarm` / 通知 `notify_action`)、
         * [action] は押された通知ボタンのラベル。
         */
        fun render(
            template: String,
            ts: Long, time: String, event: String, level: Int?, ssid: String,
            name: String = "", action: String = ""
        ): String {
            if (template.isBlank()) {
                return JSONObject().apply {
                    put("ts", ts)
                    put("time", time)
                    put("event", event)
                    if (level != null) put("level", level)
                    if (ssid.isNotEmpty()) put("ssid", ssid)
                    if (name.isNotEmpty()) put("name", oneline(name))
                    if (action.isNotEmpty()) put("action", oneline(action))
                }.toString()
            }
            val vars = mapOf(
                "ts" to ts.toString(),
                "time" to time,
                "event" to event,
                "level" to (level?.toString() ?: ""),
                "ssid" to oneline(ssid),
                "name" to oneline(name),
                "action" to oneline(action),
            )
            val sb = StringBuilder(template.length + 32)
            var i = 0
            while (i < template.length) {
                val c = template[i]
                when {
                    c == '\\' && i + 1 < template.length -> {
                        when (template[i + 1]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t')
                            '\\' -> sb.append('\\'); else -> { sb.append('\\'); sb.append(template[i + 1]) }
                        }
                        i += 2
                    }
                    c == '{' -> {
                        val end = template.indexOf('}', i + 1)
                        if (end < 0) { sb.append(c); i++ }
                        else {
                            val name = template.substring(i + 1, end)
                            sb.append(vars[name] ?: "{$name}")   // 未知プレースホルダはそのまま残す
                            i = end + 1
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            return sb.toString()
        }
    }
}
