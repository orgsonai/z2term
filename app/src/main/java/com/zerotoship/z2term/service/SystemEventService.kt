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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon
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
 *  - `net_online` / `net_offline`             … 通信できる回線ができた / 無くなった (0.8.264)
 *  - `net_wifi` / `net_mobile` / `net_ethernet` … 使う回線が切り替わった (0.8.264)
 *  - `headset_plugged` / `headset_unplugged`  … 有線ヘッドセットの抜き差し
 *  - `airplane_on` / `airplane_off`           … 機内モード ON / OFF
 *  - `ringer_normal` / `ringer_vibrate` / `ringer_silent` … マナーモード切替
 *  - `bt_audio_connected` / `bt_audio_disconnected` … Bluetooth オーディオ (A2DP/SCO) の接続 / 切断
 *
 * このサービスとは別に、時刻トリガー ([AlarmScheduler] / `z2-alarm`) が同じ events.jsonl へ
 * `alarm` イベント (`{name}` 付き) を書く。そちらはこのサービスの ON/OFF に依存しない。
 *
 * **z2-when (A6) との関係**: `wifi` / `sensor` に加えて **`charge:*` / `battery:*` もこのサービスが
 * 受け口**になっている (0.8.214〜。[handlePower] のコメント参照)。つまりこれらのトリガーは
 * **検知 ON が前提**。ON/OFF に依存しないのは時刻トリガーと `sms:*` ([SmsLogReceiver] 経由) だけ。
 */
class SystemEventService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var captureEnabled = false
    @Volatile private var formatTemplate = ""
    @Volatile private var prepend = false
    @Volatile private var lastWifiConnected: Boolean? = null

    /** 直前の既定回線の種別 ([netTransport] の値)。null = まだ基準を取っていない。 */
    @Volatile private var lastNetTransport: String? = null

    @Volatile private var lastBatteryBucket = -1

    /** 直近に見た残量% (`events.jsonl` の 10% 刻みとは別に、z2-when の 1% 刻み評価を間引くため)。 */
    @Volatile private var lastBatteryPct = -1

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

    /**
     * Wi‑Fi の接続/切断を拾うコールバック (0.8.248〜)。
     *
     * 以前は `WifiManager.NETWORK_STATE_CHANGED_ACTION` を受けた**その場で**
     * `ConnectivityManager.activeNetwork` を読んでいたが、このブロードキャストは
     * **既定ネットワークが切り替わる前**に飛ぶ。そのため切断直後はまだ Wi‑Fi が見えて
     * `wifi_connected`、接続直後はまだモバイル (または未確定) のままで `wifi_disconnected` と、
     * **接続と切断が入れ替わって記録されていた** (実機の `events.jsonl` で確認: Wi‑Fi ON の
     * まま最後の記録が `wifi_disconnected` になる)。判定式自体は正しく、読むタイミングだけが
     * 早すぎたということ。`z2-state wifi` が常に正しかったのは、聞かれた時点で読むから。
     *
     * `NetworkCallback` は**状態が確定してから**呼ばれるので、この取り違えが原理的に起きない。
     * 既定ネットワークを見るのは [wifiConnectedNow] (= `z2-state wifi`) と判定を揃えるため。
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handleWifi(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            handleNet(netTransport(caps))
        }

        /**
         * 既定ネットワークが無くなった。別の回線へ切り替わる場合は続けて
         * [onCapabilitiesChanged] が来るので、ここでは「Wi‑Fi ではなくなった」だけを見る
         * (元から false なら [handleWifi] 側の抑制で何も起きない)。
         */
        override fun onLost(network: Network) {
            handleWifi(false)
            handleNet(WhenTriggerMatch.NET_NONE)
        }
    }

    /**
     * z2-when (A6 stage2) の `sensor:*` トリガー用。センサーは常時監視が電池を食うので、**該当ルールが
     * あるセンサーだけ** [refreshSensors] で登録する。加速度は shake 判定・照度/近接はしきい値/near-far を
     * [WhenManager] が担う (エッジ判定・shake の debounce ともに WhenManager 側)。
     */
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> WhenManager.onAccel(
                    applicationContext, e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000L
                )
                Sensor.TYPE_LIGHT -> WhenManager.onLight(applicationContext, e.values[0])
                Sensor.TYPE_PROXIMITY ->
                    // 近接センサーは 0 (near) 〜 maximumRange (far) を返す実装が一般的。
                    WhenManager.onProximity(applicationContext, near = e.values[0] < e.sensor.maximumRange)
            }
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    @Volatile private var sensorsRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> emit("screen_on")
                Intent.ACTION_SCREEN_OFF -> emit("screen_off")
                Intent.ACTION_USER_PRESENT -> emit("unlocked")
                Intent.ACTION_POWER_CONNECTED -> handlePower(started = true)
                Intent.ACTION_POWER_DISCONNECTED -> handlePower(started = false)
                Intent.ACTION_BATTERY_LOW -> handleBatteryLowOkay("battery_low")
                Intent.ACTION_BATTERY_OKAY -> handleBatteryLowOkay("battery_okay")
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryLevel(intent)
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
        // detection を ON にしたタイミングで sensor ルールを拾ってセンサー登録する。
        refreshSensors()
        refreshFileWatchers()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        // Wi‑Fi も同じくコールバックで受ける (ブロードキャストだと接続/切断が入れ替わる → networkCallback)。
        // 登録直後に「今の既定ネットワーク」で onCapabilitiesChanged が 1 度呼ばれるので、
        // **サービスの起動を接続イベントと誤検知しない**よう、先に今の状態を基準にしておく
        // (BT オーディオの btCallbackPrimed と同じ理由)。
        lastWifiConnected = wifiConnectedNow()
        lastNetTransport = netTransportNow()
        runCatching {
            applicationContext.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { Log.w(TAG, "network callback 登録失敗", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        runCatching { unregisterReceiver(receiver) }
        runCatching {
            (getSystemService(AUDIO_SERVICE) as? AudioManager)
                ?.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        runCatching {
            applicationContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(networkCallback)
        }
        runCatching { (getSystemService(SENSOR_SERVICE) as? SensorManager)?.unregisterListener(sensorListener) }
        // 見張りも畳む (プロセスが残ったまま監視だけ生き続けるのを防ぐ)。
        fileWatchers.forEach { runCatching { it.stopWatching() } }
        fileWatchers.clear()
        scope.cancel()
        writer.shutdown()
    }

    /**
     * enabled な `sensor:*` ルールが要求するセンサーだけを登録し直す (要求集合が空なら 1 つも登録しない)。
     * ルールの増減・detection ON でその都度呼ぶ。加速度は shake 検出に十分な速度が要るので `UI`、
     * 照度/近接は on-change センサーで低頻度なので `NORMAL`。
     */
    /** いま張っている `FileObserver`。ルールの増減で全部作り直す (差分管理はしない)。 */
    private val fileWatchers = ArrayList<android.os.FileObserver>()

    /**
     * `file:new=…` ルールが見張るフォルダに `FileObserver` を張り直す (0.8.235)。
     *
     * センサーと同じ考え方で、**該当ルールがあるフォルダだけ**を監視する。1 件も無ければ
     * 1 つも張らない。見るのは `CLOSE_WRITE`（書き込み完了）と `MOVED_TO`（別名で書いてから
     * rename する書き方）だけ — `CREATE` を見るとコピー途中の空ファイルを掴む。
     *
     * ⚠ `FileObserver` は**プロセスが生きている間だけ**なので、これは「検知 ON が前提」の
     * トリガー (時刻や SMS のような常時性は無い)。docs にもそう書く。
     */
    private fun refreshFileWatchers() {
        fileWatchers.forEach { runCatching { it.stopWatching() } }
        fileWatchers.clear()
        val dirs = runCatching { WhenManager.fileDirsNeeded(applicationContext) }.getOrDefault(emptySet())
        val mask = android.os.FileObserver.CLOSE_WRITE or android.os.FileObserver.MOVED_TO
        dirs.forEach { dir ->
            runCatching {
                val f = java.io.File(dir)
                if (!f.isDirectory) {
                    Log.w(TAG, "file:new のフォルダが無い: $dir")
                    return@runCatching
                }
                val obs = object : android.os.FileObserver(f, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        val name = path ?: return
                        writer.execute {
                            runCatching { WhenManager.onFileCreated(applicationContext, dir, name) }
                                .onFailure { Log.w(TAG, "file rule failed ($dir/$name): ${it.message}") }
                        }
                    }
                }
                obs.startWatching()
                fileWatchers.add(obs)
            }.onFailure { Log.w(TAG, "file watcher failed for $dir", it) }
        }
    }

    private fun refreshSensors() {
        val sm = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        if (sensorsRegistered) {
            runCatching { sm.unregisterListener(sensorListener) }
            sensorsRegistered = false
        }
        val kinds = runCatching { WhenManager.sensorKindsNeeded(applicationContext) }.getOrDefault(emptySet())
        if (kinds.isEmpty()) return
        val h = android.os.Handler(mainLooper)
        fun reg(type: Int, delay: Int) {
            sm.getDefaultSensor(type)?.let {
                if (sm.registerListener(sensorListener, it, delay, h)) sensorsRegistered = true
            }
        }
        if ("accel" in kinds) reg(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_UI)
        if ("light" in kinds) reg(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL)
        if ("proximity" in kinds) reg(Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun batteryLevel(): Int? = runCatching {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()

    /**
     * いま Wi‑Fi で繋がっているか (= 既定ネットワークが Wi‑Fi か)。`z2-state wifi` と同じ判定。
     *
     * `WifiManager.connectionInfo` は Android 12+ で**呼び出し元がフォアグラウンドでないと無効値
     * (networkId = -1) を返す**ため、画面消灯中などまさにイベントを拾いたい場面で「常に未接続」に
     * 見え、`wifi_connected` を取りこぼしていた (`z2-state` 側で実機再現。同じ理由でそちらも
     * ConnectivityManager へ寄せてある)。
     */
    private fun wifiConnectedNow(): Boolean = runCatching {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork ?: return@runCatching false
        cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    /**
     * Wi‑Fi の接続/切断を状態変化として 1 回だけ発火 (連続する同一状態は抑制)。
     * 呼び元は [networkCallback] だけ ([connected] は確定済みの状態)。
     *
     * SSID は `WifiInfo` 経由でしか取れず位置情報権限も要るので、取れなければ空文字。
     */
    private fun handleWifi(connected: Boolean) {
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
     * 既定回線の種別 (0.8.264)。`wifi` / `mobile` / `ethernet` / `vpn` / `other` /
     * [WhenTriggerMatch.NET_NONE] のいずれかを返す。判定はここだけに置き、
     * `net:*` の発火条件 ([WhenTriggerMatch.net]) へは**この文字列だけ**を渡す
     * (Android の定数を純ロジック側へ持ち込まないため)。
     *
     * ⚠ `NET_CAPABILITY_VALIDATED` (**実際に通信できたか**) が無ければ [WhenTriggerMatch.NET_NONE]
     * 扱いにする。Wi‑Fi に「繋がって」いても認証画面の先へ出られない、圏内なのに通らない、は
     * 珍しくない。`net:online` を「送れるようになった」の合図として使えないと意味が無いので、
     * 繋がったことではなく**通ったこと**を採る。⚠ その代わり、繋がってから検証が終わるまでの
     * わずかな間は `none` のままなので、`net:online` は Wi‑Fi のアイコンが立つより**少し遅れる**。
     */
    private fun netTransport(caps: NetworkCapabilities?): String = when {
        caps == null -> WhenTriggerMatch.NET_NONE
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> WhenTriggerMatch.NET_NONE
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> WhenTriggerMatch.NET_NONE
        // VPN は下に実回線がぶら下がるが、既定回線として見えるのは VPN の方。
        // 「どの回線か」で分岐したい人には嘘になるので、素直に vpn と答える。
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    /** いまの既定回線の種別。サービス起動時の基準づくり用 ([netTransport] と同じ判定)。 */
    private fun netTransportNow(): String = runCatching {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork ?: return@runCatching WhenTriggerMatch.NET_NONE
        netTransport(cm.getNetworkCapabilities(net))
    }.getOrDefault(WhenTriggerMatch.NET_NONE)

    /**
     * 既定回線の種別が変わったときだけ 1 回発火する (0.8.264)。呼び元は [networkCallback]。
     *
     * `onCapabilitiesChanged` は帯域の変化などでも何度も呼ばれるので、**種別が変わったときだけ**に
     * 絞る ([handleWifi] と同じ考え方)。[lastNetTransport] が null のときは基準が無い＝
     * サービスが起きた直後なので、記録だけして発火はしない (起動を「回線が変わった」と誤検知しない)。
     */
    private fun handleNet(now: String) {
        val prev = lastNetTransport
        if (now == prev) return
        lastNetTransport = now
        if (prev == null) return
        // events.jsonl へ残すのは「通じた/途切れた」と「別の回線になった」の 2 種類。
        // wifi_connected と重なるように見えるが、あちらは Wi‑Fi の有無しか言えず、
        // モバイルへ切り替わったのか圏外になったのかを区別できない。
        val online = now != WhenTriggerMatch.NET_NONE
        val wasOnline = prev != WhenTriggerMatch.NET_NONE
        when {
            online && !wasOnline -> emit("net_online")
            !online && wasOnline -> emit("net_offline")
        }
        if (online) emit("net_$now")
        runCatching { WhenManager.onNet(applicationContext, now = now, prev = prev) }
            .onFailure { Log.w(TAG, "net rule failed ($prev -> $now): ${it.message}") }
    }

    /**
     * 充電の開始/停止。`events.jsonl` へ書くのに加えて、**z2-when の `charge:*` トリガーもここで実行する**。
     *
     * `ACTION_POWER_CONNECTED` / `_DISCONNECTED` は**暗黙ブロードキャスト制限の例外ではない**ので、
     * manifest 宣言の [WhenReceiver] には Android 8+ では**永久に届かない** (0.8.205〜0.8.213 の
     * `charge:*` が一度も動かなかった原因。2026-07-24 の実機検証で判明)。生きたプロセスで
     * `registerReceiver` したこのサービスだけが受け取れるため、ここから [WhenManager.onCharge] を呼ぶ。
     * その代償として **`charge:*` / `battery:*` は「検知 ON」が前提**になった (wifi/sms/sensor と同じ)。
     */
    private fun handlePower(started: Boolean) {
        val level = batteryLevel()
        emit(if (started) "power_connected" else "power_disconnected", level = level)
        // 残量が取れなかったときは -1 を渡す (WhenManager 側で「不明」として扱われ、
        // Z2_WHEN_LEVEL を渡さず電池しきい値の評価もしない)。charge:* 自体は発火する。
        runCatching { WhenManager.onCharge(applicationContext, started = started, level = level ?: -1) }
    }

    /** 低電池/回復。[handlePower] と同じ理由でここから電池しきい値も評価する。 */
    private fun handleBatteryLowOkay(event: String) {
        val level = batteryLevel()
        emit(event, level = level)
        runCatching { WhenManager.onBatteryChanged(applicationContext, level ?: -1) }
    }

    /**
     * ACTION_BATTERY_CHANGED は高頻度なので、`battery_level` イベントは残量が **10% 刻みの境界を
     * 跨いだとき**だけ書く (サービス起動直後の初回はベースライン設定のみで発火しない)。
     *
     * 一方 z2-when の `battery:above=N` / `below=N` は **1% 変わるたびに**評価する。10% 刻みで
     * 評価していた 0.8.213 までは「40%→44% で `above=40` が発火しない」「発火しても最大 10% 遅れ、
     * `Z2_WHEN_LEVEL` が実値とズレる」状態で、docs の「N% を跨いだとき」と食い違っていた。
     * [WhenManager.onBatteryChanged] 自体がエッジ判定＋前回値と同じなら即 return なので呼び出しは軽い。
     */
    private fun handleBatteryLevel(intent: Intent) {
        val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (lvl < 0 || scale <= 0) return
        val pct = lvl * 100 / scale
        // BATTERY_CHANGED は電圧/温度が動いただけでも飛んでくるので、残量が変わっていなければ
        // ここで打ち切る (WhenManager 側も同値なら何もしないが、その判定にファイル読みが要る)。
        if (pct != lastBatteryPct) {
            lastBatteryPct = pct
            runCatching { WhenManager.onBatteryChanged(applicationContext, pct) }
        }
        val bucket = pct / 10
        if (lastBatteryBucket == -1) { lastBatteryBucket = bucket; return }
        if (bucket != lastBatteryBucket) {
            lastBatteryBucket = bucket
            emit("battery_level", level = pct)
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
            // 記録したのと同じイベントで `event:<名前>` ルールを実行する (0.8.226)。ここは既に
            // 単一のワーカースレッドなので、ルール読み込み (ファイル I/O) をレシーバのスレッドへ
            // 持ち込まずに済む。捕捉は必須 — 自動化の失敗で検知そのものを止めない。
            runCatching {
                WhenManager.onEvent(ctx, event, level = level, ssid = ssid)
            }.onFailure { Log.w(TAG, "when event failed ($event): ${it.message}") }
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
            .setZ2SmallIcon(this)
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

        /** 稼働中インスタンス (z2-when の sensor 登録を貼り直すため)。onCreate/onDestroy で更新。 */
        @Volatile private var instance: SystemEventService? = null

        /**
         * 検知サービスが**動いていれば**、sensor ルールの増減に合わせてセンサー登録を貼り直す。
         * z2-when のルール変更 ([WhenManager.reload]) から呼ぶ。動いていなければ何もしない
         * (sensor トリガーは検知 ON のときだけ働く。wifi と同じ割り切り)。
         */
        /** ルールが増減したとき、動いていれば `file:new` の監視を張り直す。 */
        fun refreshFileWatchersIfRunning() {
            val svc = instance ?: return
            android.os.Handler(svc.mainLooper).post { runCatching { svc.refreshFileWatchers() } }
        }

        fun refreshSensorsIfRunning() {
            val svc = instance ?: return
            android.os.Handler(svc.mainLooper).post { runCatching { svc.refreshSensors() } }
        }

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
