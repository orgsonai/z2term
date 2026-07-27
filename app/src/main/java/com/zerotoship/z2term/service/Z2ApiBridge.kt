package com.zerotoship.z2term.service

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.ServerEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.tile.TileStore
import com.zerotoship.z2term.tile.Z2TileService
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android API ブリッジ (Termux:API 相当)。端末 (PRoot/Linux 側) から小さな CLI (`z2-*`) を
 * 叩くと、アプリ (Android/Kotlin 側) が Android の機能を代行する。これが「ただの端末」を
 * 「Android 自動化基盤」へ格上げする差別化の核。
 *
 * **通信方式 — ファイル監視ブリッジ** (既存の [com.zerotoship.z2term.gui.GuiEventWatcher] と同方式):
 *  - 共有ディレクトリ `/storage/app/z2api/` (proot 内バインド = Android 側 `getExternalFilesDir(null)`)
 *    の `req/` を [FileObserver] で監視する。
 *  - rootfs のヘルパー (`z2api`) はリクエストを `req/.<id>.tmp` に書いてから `req/<id>.req` へ
 *    **rename** する (rename は atomic → `MOVED_TO` 1 発で完全なファイルが届く)。
 *  - 戻り値が要るコマンド (clip-get / battery) は、アプリが `resp/<id>.resp` を書き、ヘルパーが
 *    それをポーリングして読む。一方向コマンド (notify/toast/share/…) は resp 無し。
 *
 * LocalServerSocket を使わない理由: proot 内からの到達性が未検証で、rootfs に `nc`/`socat` が
 * 要る。ファイル監視は既にこのリポジトリで実証済みで、追加ツール不要・権限不要・proot 越えも
 * 確認済み。
 *
 * **リクエスト形式** (`req/<id>.req`、1 ディレクティブ 1 行):
 * ```
 * CMD <cmd>
 * A <base64(arg0)>
 * A <base64(arg1)>
 * R 1            # 任意。あれば応答を resp/<id>.resp に書く
 * ```
 * base64 は引数中の空白/改行/引用符/Unicode を安全に運ぶため (sh 側のクォート地獄を回避)。
 *
 * **応答形式** (`resp/<id>.resp`、1 行):
 * ```
 * OK <base64(data)>     # 成功
 * ERR <base64(message)> # 失敗
 * ```
 */
object Z2ApiBridge {

    private const val TAG = "Z2ApiBridge"
    private const val DIR_NAME = "z2api"
    private const val CHANNEL_ID = "z2term_api"

    /**
     * ヘッドアップ (画面上部バナー) 表示用の高重要度チャンネル。既存の [CHANNEL_ID] は
     * `IMPORTANCE_DEFAULT` で作成済みのため後から重要度を上げられない (Android 仕様)。
     * バナーを出したいときは別 ID の `IMPORTANCE_HIGH` チャンネルを使う必要があるので分けている。
     * `z2-notify -h`/`--high`/`--banner` 経由のときだけこちらを使う。
     */
    private const val CHANNEL_ID_HIGH = "z2term_api_high"

    /** 通知に出せるボタンの数 (Android が表示するのは 3 つまで)。 */
    private const val MAX_NOTIFY_BUTTONS = 3

    private var observer: FileObserver? = null
    private var reqDir: File? = null
    private var respDir: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    // FileObserver スレッドを塞がないよう、ハンドリングは専用シングルスレッドへ。
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "z2api-bridge").apply { isDaemon = true }
    }
    private val notifyId = AtomicInteger(7000)

    // TTS (z2-say) は初期化が非同期。準備できるまで発話を溜め、ready になったら流す。
    @Volatile private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)
    private val pendingSpeech = ConcurrentLinkedQueue<String>()
    // z2-torch のトグル用に最後に設定した点灯状態を保持 (best-effort)。
    @Volatile private var torchOn = false

    /** Application.onCreate から呼ぶ。idempotent。 */
    fun start(context: Context) {
        if (observer != null) return
        val appCtx = context.applicationContext
        val base = appCtx.getExternalFilesDir(null)
        if (base == null) {
            Log.w(TAG, "external files dir unavailable — bridge disabled")
            return
        }
        val root = File(base, DIR_NAME)
        val req = File(root, "req").apply { mkdirs() }
        val resp = File(root, "resp").apply { mkdirs() }
        reqDir = req
        respDir = resp
        // 起動前に溜まった古いリクエスト/レスポンスは破棄 (要求元プロセスは既に消えている)。
        runCatching { req.listFiles()?.forEach { it.delete() } }
        runCatching { resp.listFiles()?.forEach { it.delete() } }
        ensureChannel(appCtx)

        val obs = object : FileObserver(req, MOVED_TO or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || !path.endsWith(".req")) return
                val f = File(req, path)
                worker.execute { handleRequestFile(appCtx, f) }
            }
        }
        obs.startWatching()
        observer = obs
        Log.i(TAG, "watching ${req.absolutePath}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        reqDir = null
        respDir = null
        runOnMain {
            tts?.shutdown()
            tts = null
            ttsReady.set(false)
            pendingSpeech.clear()
        }
    }

    private fun handleRequestFile(context: Context, file: File) {
        if (!file.exists()) return
        val id = file.name.removeSuffix(".req")
        val cmd: String
        val args: List<String>
        var needResp = false
        try {
            var c: String? = null
            val a = ArrayList<String>()
            file.readLines().forEach { line ->
                when {
                    line.startsWith("CMD ") -> c = line.substring(4).trim()
                    line.startsWith("A ") -> a.add(decode(line.substring(2).trim()))
                    line.startsWith("R ") -> needResp = line.substring(2).trim() == "1"
                }
            }
            cmd = c ?: run { file.delete(); return }
            args = a
        } catch (e: Exception) {
            Log.w(TAG, "bad request ${file.name}", e); runCatching { file.delete() }; return
        }
        // リクエストファイルは消費したら削除 (req ディレクトリを溜めない)。
        runCatching { file.delete() }

        try {
            val result = dispatch(context, cmd, args)
            if (needResp) writeResponse(id, ok = true, data = result ?: "")
        } catch (e: Exception) {
            Log.w(TAG, "cmd '$cmd' failed", e)
            if (needResp) writeResponse(id, ok = false, data = e.message ?: "error")
        }
    }

    /** コマンド分岐。戻り値が要る場合は文字列を返す (それ以外は null)。 */
    private fun dispatch(context: Context, cmd: String, args: List<String>): String? {
        return when (cmd) {
            // notify: title, text, high, name, buttons... (name/buttons は 0.8.169 で追加)
            "notify" -> {
                doNotify(
                    context,
                    titleArg = args.getOrNull(0).orEmpty(),
                    textArg = args.getOrNull(1).orEmpty(),
                    high = args.getOrNull(2) == "high",
                    name = args.getOrNull(3).orEmpty(),
                    buttons = args.drop(4).filter { it.isNotBlank() }
                )
                null
            }
            "toast" -> { val msg = args.joinToString(" "); mainHandler.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }; null }
            "share" -> { doShareText(context, args.joinToString(" ")); null }
            "open" -> { doOpen(context, args.getOrNull(0).orEmpty()); null }
            "clip-set" -> { val text = args.joinToString(" "); runOnMain { setClipboard(context, text) }; null }
            "clip-get" -> runOnMainSync { getClipboard(context) }
            "battery" -> batteryJson(context)
            "vibrate" -> { doVibrate(context, args.getOrNull(0)?.toLongOrNull() ?: 200L); null }
            "say" -> { doSay(context, args.joinToString(" ")); null }
            "torch" -> torchSet(context, args.getOrNull(0).orEmpty())
            "media" -> { doMedia(context, args.getOrNull(0).orEmpty()); null }
            "volume" -> volumeSet(context, args.getOrNull(0).orEmpty())
            "intent" -> { doIntent(context, args); null }
            "sensor" -> sensorRead(context, args.getOrNull(0).orEmpty())
            "alarm" -> alarmCmd(context, args)
            "state" -> stateRead(context, args.getOrNull(0).orEmpty())
            // z2-screen: OS の自動画面消灯を期限つきで止める (🔅 とは別物・[ScreenTimeout] 参照)。
            "screen" -> screenCmd(context, args)
            // z2-tile: クイック設定タイル 4 枠への割り当て。
            "tile" -> tileCmd(context, args)
            // z2doctor 用。**アプリ側にしか無い情報**（許可の有無・設定・常駐の数）をまとめて返す。
            "doctor" -> doctorRead(context)
            // z2-noti: いま出ている通知を読むだけ (押す・消すは提供しない)。
            "noti" -> notiCmd(args)
            "session" -> sessionCmd(context, args)
            // z2-when がルールファイルを書き換えた後に呼ぶ。時刻トリガーの AlarmManager 予約を貼り直す。
            "when-reload" -> { WhenManager.reload(context); null }
            else -> throw IllegalArgumentException("unknown cmd: $cmd")
        }
    }

    // --- 各機能 ---

    /**
     * **アプリ自身のタブを操る** (`z2-session`・A1)。
     *
     * 他の動詞がすべて「Android を叩く」片道なのに対し、これだけはアプリの内側 (タブ) を触る。
     * シェルやマクロから「作業用のタブをもう 1 枚開く」「別のタブへコマンドを置く」
     * 「今の画面を取り出す」ができるようになる。
     *
     * **安全側の既定**: `send` は文字を**入れるだけで実行しない** (改行を付けない)。共有の
     * 受け取り (B1) と同じ約束で、他のタブが勝手に走り出す状態を作らない。実行させたいときだけ
     * `--enter` を明示する。
     *
     * 入れ先の指定 ([resolveSession]) は id / 1 始まりの番号 / タブ名 のどれでもよい。
     * `z2-session list` の番号をそのまま使えるのが実用上いちばん楽なので、番号を第一に扱う。
     */
    private fun sessionCmd(context: Context, args: List<String>): String? {
        return when (val sub = args.getOrNull(0).orEmpty()) {
            // 1 行 1 タブの TSV: <番号> <id> <種別> <印> <名前>
            // jq の無い環境でも awk / cut で拾えるようにする (z2-state をフラット JSON にしたのと同じ理由)。
            // 印は * = 表示中 / ! = 動作中 / ? = まだ起動していない / - = それ以外。
            // ? を出すのは、未起動のタブへ send しても PTY が無く何も起きないため
            // (印が無いと「送ったのに動かない」の理由が分からない)。
            "list" -> {
                val sessions = SessionManager.sessions.value
                val activeId = SessionManager.activeId.value
                sessions.mapIndexed { i, s ->
                    val kind = if (s is TerminalSession) "term" else "gui"
                    val marks = buildString {
                        if (s.id == activeId) append('*')
                        if (s.isBusy) append('!')
                        val idle = (s as? TerminalSession)?.uiState?.value?.state ==
                            TerminalSession.TerminalState.IDLE
                        if (idle) append('?')
                        if (isEmpty()) append('-')
                    }
                    "${i + 1}\t${s.id}\t$kind\t$marks\t${s.label.value}"
                }.joinToString("\n")
            }
            // 新しい端末タブを開き、その番号と id を返す (続けて send する材料になる)。
            "new" -> {
                val name = args.getOrNull(1).orEmpty()
                val created = runOnMainSync {
                    val s = SessionManager.openNew(context)
                    // pinned = true: この後の起動で OS 名 (spec.id) やシェルのタイトルに
                    // 上書きされないようにする。付けた名前が消えると指定の意味が無くなる。
                    if (name.isNotBlank()) s.setLabel(name.take(20), pinned = true)
                    // **ここで起動まで済ませる**。画面側の自動起動は「表示中のタブが IDLE なら」
                    // という条件なので、アプリを開いていない間に作ったタブは開くまで起動せず、
                    // 続けて send しても PTY が無く何も起きない (実機で確認)。
                    // マクロから「タブを開いてコマンドを流す」を成立させるにはここで立ち上げる。
                    // ただし初回ダウンロードが要る distro は勝手に通信を始めない (画面で確認を出す)。
                    if (s.downloadOnStartSpec() == null) s.startTerminal()
                    s
                }
                val index = SessionManager.sessions.value.indexOfFirst { it.id == created.id } + 1
                "$index\t${created.id}"
            }
            // 指定タブへ文字を入れる。既定は入れるだけ、--enter が付いたときだけ実行する。
            "send" -> {
                val target = args.getOrNull(1).orEmpty()
                val rest = args.drop(2)
                val enter = rest.contains("--enter")
                val text = rest.filterNot { it == "--enter" }.joinToString(" ")
                if (text.isEmpty()) throw IllegalArgumentException("session send: nothing to send")
                val session = resolveSession(target) as? TerminalSession
                    ?: throw IllegalArgumentException("session send: no such terminal tab: $target")
                runOnMainSync {
                    SessionManager.insertText(text, session.id)
                    if (enter) session.writeBytes("\n".toByteArray(Charsets.UTF_8))
                }
                null
            }
            // 画面のテキストを取り出す。既定は今見えている分だけ、--all で遡れる分も含める。
            "capture" -> {
                val target = args.getOrNull(1).orEmpty().ifBlank { "." }
                val all = args.contains("--all")
                val session = resolveSession(target) as? TerminalSession
                    ?: throw IllegalArgumentException("session capture: no such terminal tab: $target")
                runOnMainSync {
                    runCatching { session.emulator.buffer.getAllText(includeScrollback = all) }
                        .getOrElse { "" }
                }.trimEnd('\n')
            }
            "close" -> {
                val target = args.getOrNull(1).orEmpty()
                val session = resolveSession(target)
                    ?: throw IllegalArgumentException("session close: no such tab: $target")
                // 最後の 1 枚は閉じない (UI のダブルタップ削除と同じ約束)。
                if (SessionManager.sessions.value.size <= 1) {
                    throw IllegalArgumentException("session close: cannot close the last tab")
                }
                runOnMainSync { SessionManager.close(session.id) }
                null
            }
            else -> throw IllegalArgumentException("session: unknown subcommand: $sub")
        }
    }

    /**
     * `z2-session` のタブ指定を解決する。**番号 (1 始まり) → id → タブ名** の順に見る。
     *
     * `.` はアクティブなタブ。番号は `z2-session list` の 1 列目そのままで、いちばんよく使う形。
     * タブ名は完全一致を優先し、無ければ前方一致で 1 件に絞れるときだけ採用する
     * (複数に当たる指定で「たまたま先頭のタブ」に文字が入る事故を作らない)。
     */
    private fun resolveSession(target: String): com.zerotoship.z2term.core.AppSession? {
        val sessions = SessionManager.sessions.value
        if (target.isBlank() || target == ".") {
            return sessions.firstOrNull { it.id == SessionManager.activeId.value } ?: sessions.firstOrNull()
        }
        target.toIntOrNull()?.let { n -> return sessions.getOrNull(n - 1) }
        sessions.firstOrNull { it.id == target }?.let { return it }
        sessions.firstOrNull { it.label.value == target }?.let { return it }
        val prefix = sessions.filter { it.label.value.startsWith(target) }
        return if (prefix.size == 1) prefix[0] else null
    }

    /**
     * 端末の**現在の状態**を 1 回で返す (`z2-state`)。
     *
     * events.jsonl は「変化した瞬間」しか流れてこないので、マクロが「今どうなっているか」で
     * 分岐したいとき (画面が点いていれば通知しない、充電中なら重い処理を回す…) に必要になる。
     * これまでは `z2-battery` しか現在値を取る手段が無かった。
     *
     * 引数なしなら全項目を **フラットな JSON** で返す (入れ子にしないのは jq 無しの sed/grep でも
     * 拾いやすくするため)。[key] を渡すとその値だけを生で返すので、`[ "$(z2-state charging)" = "true" ]`
     * のようにそのまま条件式に書ける。すべて**追加権限なし**で取れるものだけ。
     */
    private fun stateRead(context: Context, key: String): String {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val screen = if (pm?.isInteractive == true) "on" else "off"
        val locked = km?.isKeyguardLocked == true
        val idle = pm?.isDeviceIdleMode == true

        // 充電状態はスティッキーな ACTION_BATTERY_CHANGED から取る (plug 種別まで分かる)。
        val batt = runCatching {
            context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val lvl = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val level = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
        val plugged = batt?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }
        val charging = plugged != 0

        // Wi-Fi 接続の有無は ConnectivityManager で見る。`WifiManager.connectionInfo` は
        // Android 12+ で**呼び出し元がフォアグラウンドでないと無効値** (networkId=-1) を返すため、
        // マクロが多用する「バックグラウンドからの問い合わせ」では常に未接続に見えてしまう。
        // NetworkCapabilities は ACCESS_NETWORK_STATE (宣言のみで付与) だけで、その制限を受けない。
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifiConnected = runCatching {
            val net = cm?.activeNetwork ?: return@runCatching false
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        // SSID は WifiInfo からしか取れず、位置情報権限とフォアグラウンド制限の両方が掛かる。
        // 取れないときは空文字 (events.jsonl の `ssid` と同じ扱い)。
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ssid = runCatching { wm?.connectionInfo?.ssid }.getOrNull().orEmpty().trim('"')
            .let { if (it.isBlank() || it == "<unknown ssid>") "" else it }

        val ringer = when (am?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            else -> ""
        }
        val airplane = runCatching {
            android.provider.Settings.Global.getInt(
                context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
        }.getOrDefault(false)
        // 有線ヘッドセット/ヘッドホン/USB ヘッドセットのいずれかが挿さっているか。
        val headset = runCatching {
            am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            } == true
        }.getOrDefault(false)
        // Bluetooth オーディオ (A2DP/SCO) が繋がっているか。デバイス名は権限が要るので出さない。
        val btAudio = runCatching {
            am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true
        }.getOrDefault(false)
        // 電池温度 (BATTERY_CHANGED は 0.1℃ 単位の整数で持っている)。取れなければ -1。
        val tempRaw = batt?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = if (tempRaw == Int.MIN_VALUE) "-1" else (tempRaw / 10.0).toString()
        val volume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        val volumeMax = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1

        // key 指定は生値を返す (シェルの比較にそのまま使えるように)。
        if (key.isNotBlank()) {
            return when (key) {
                "screen" -> screen
                "locked" -> locked.toString()
                "idle" -> idle.toString()
                "charging" -> charging.toString()
                "plug" -> plug
                "level" -> level.toString()
                "wifi" -> wifiConnected.toString()
                "ssid" -> ssid
                "ringer" -> ringer
                "airplane" -> airplane.toString()
                "headset" -> headset.toString()
                "bt_audio" -> btAudio.toString()
                "temp" -> temp
                "volume" -> volume.toString()
                "volume_max" -> volumeMax.toString()
                else -> throw IllegalArgumentException("state: unknown key: $key")
            }
        }
        return JSONObject().apply {
            put("screen", screen)
            put("locked", locked)
            put("idle", idle)
            put("charging", charging)
            put("plug", plug)
            put("level", level)
            put("wifi", wifiConnected)
            put("ssid", ssid)
            put("ringer", ringer)
            put("airplane", airplane)
            put("headset", headset)
            put("bt_audio", btAudio)
            put("temp", temp.toDoubleOrNull() ?: -1.0)
            put("volume", volume)
            put("volume_max", volumeMax)
        }.toString()
    }

    /**
     * `z2-noti` (0.8.236)。いま出ている通知を TSV で返す**だけ**。
     *
     * ⚠ **「押す」「消す」は意図的に提供しない。** 通知のボタンを押せるということは、
     * 他アプリの決済ボタンや送信ボタンも押せるということで、**誤爆の実害がこのアプリの外に出る**。
     * 読む・きっかけにする (`z2-when notify:`) までなら価値が高く危険は小さい、という線引き
     * (提案 20 の検討でその動詞だけ落とした)。
     */
    private fun notiCmd(args: List<String>): String = when (args.getOrNull(0)) {
        "list", null, "" -> NotificationLogService.activeNotificationsTsv()
            ?: throw IllegalStateException(
                "z2-noti: 通知アクセスが許可されていません (設定 › 常駐サーバー・自動化 › 通知検知)"
            )
        else -> throw IllegalArgumentException("z2-noti: unknown subcommand: ${args[0]}")
    }

    /**
     * `z2doctor` へ渡す「アプリ側にしか分からないこと」を JSON で返す (0.8.230)。
     *
     * 端末から見えるもの (kernel・空き容量・sshd プロセス・PATH) は**シェル側で調べる**。
     * ここで返すのは、許可の有無や設定値のように**シェルからは原理的に見えない**ものだけ。
     * 診断は「動かない理由」を探すためのものなので、**値の解釈はしない** — × の判定と
     * 次の一手の文言は CLI 側に置き、ここは事実だけを返す。
     */
    private fun doctorRead(context: Context): String {
        val settings = runCatching { runBlocking { AppSettings(context).flow.first() } }.getOrNull()
        val pm = context.getSystemService(PowerManager::class.java)

        // 通知を出せるか (POST_NOTIFICATIONS)。OFF だと z2-notify が黙って何も出さない。
        val notifyOk = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        // 通知を読めるか (通知アクセス)。z2-when notify: / 通知検知の前提。
        val notifyListen = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)
        // 電池最適化から除外されているか。除外されていないと常駐サーバーや検知が落とされやすい。
        val battOptIgnored = runCatching {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        // /sdcard 全体が見えるか (MANAGE_EXTERNAL_STORAGE)。無いと `cd /sdcard` が空に見える。
        // API 29 にはこの権限自体が無い (requestLegacyExternalStorage で従来権限が効く) ので、
        // false ではなく null を返して診断側で「--」にする。false にすると直せない NG が出る。
        val storageAll: Any =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
            } else JSONObject.NULL
        val smsOk = runCatching {
            context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        val servers = runCatching { ServerEntry.decode(settings?.serverEntries) }.getOrDefault(emptyList())
        val running = if (ServerDaemonManager.isRunning) {
            runCatching { ServerDaemonManager.readStatus(context) }.getOrDefault(emptyList())
                .count { it.state == "running" }
        } else 0
        val rules = runCatching { WhenManager.loadRules(context) }.getOrDefault(emptyList())

        return JSONObject().apply {
            put("version", com.zerotoship.z2term.BuildConfig.VERSION_NAME)
            put("version_code", com.zerotoship.z2term.BuildConfig.VERSION_CODE)
            put("engine", settings?.executionEngine.orEmpty())
            put("distro", settings?.distroId.orEmpty())
            put("notifications", notifyOk)
            put("notification_access", notifyListen)
            put("battery_opt_ignored", battOptIgnored)
            put("storage_all", storageAll)
            put("sms_permission", smsOk)
            put("event_capture", settings?.systemEventCaptureEnabled ?: false)
            put("servers_enabled", servers.count { it.enabled && it.command.isNotBlank() })
            put("servers_running", running)
            put("rules_total", rules.size)
            put("rules_enabled", rules.count { it.enabled })
            put("rules_paused", runCatching { WhenManager.isPaused(context) }.getOrDefault(false))
        }.toString()
    }

    /**
     * OS の自動画面消灯 (`z2-screen`)。CLI 側で `keepon <秒>` / `keepon off` / `status` に
     * 正規化済み (相対時間 `1h` の秒への変換だけ sh 側で行う。`z2-alarm in` と同じ分担)。
     *
     * ⚠ ツールバーの 🔅 (アプリを開いている間だけ点けておく) とは**別の機能**。あちらは触らない。
     */
    private fun screenCmd(context: Context, args: List<String>): String = when (args.getOrNull(0)) {
        "keepon" -> {
            val secs = args.getOrNull(1)?.toLongOrNull()
                ?: throw IllegalArgumentException("screen: bad duration")
            ScreenTimeout.keepOn(context, secs)
        }
        "off" -> ScreenTimeout.cancel(context)
        "status", null, "" -> ScreenTimeout.statusJson(context)
        else -> throw IllegalArgumentException("screen: unknown subcommand: ${args[0]}")
    }

    /**
     * クイック設定タイル (`z2-tile`)。CLI 側で `set <枠> <コマンド> <ラベル>` / `list` /
     * `clear <枠|all>` に正規化済み。
     *
     * `list` は **1 行 1 枠の TSV** (`<枠> <ラベル> <コマンド>`)。未割り当ての枠も `-` として出す —
     * 「どの番号が空いているか」が分からないと次に何番へ入れればよいか決められないため。
     */
    private fun tileCmd(context: Context, args: List<String>): String = when (args.getOrNull(0)) {
        "set" -> {
            val n = args.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalArgumentException("z2-tile: 枠は 1〜${TileStore.COUNT} です")
            TileStore.set(context, n, args.getOrNull(2).orEmpty(), args.getOrNull(3).orEmpty())
            Z2TileService.requestUpdate(context, n)
            tileListTsv(context)
        }
        "clear" -> {
            val key = args.getOrNull(1).orEmpty()
            val targets = if (key == "all") (1..TileStore.COUNT).toList()
            else listOf(
                key.toIntOrNull()
                    ?: throw IllegalArgumentException("z2-tile: 枠は 1〜${TileStore.COUNT} か all です")
            )
            targets.forEach { TileStore.clear(context, it); Z2TileService.requestUpdate(context, it) }
            tileListTsv(context)
        }
        "list", null, "" -> tileListTsv(context)
        else -> throw IllegalArgumentException("z2-tile: unknown subcommand: ${args[0]}")
    }

    private fun tileListTsv(context: Context): String = (1..TileStore.COUNT).joinToString("\n") { n ->
        val s = TileStore.get(context, n)
        if (s == null) "$n\t-\t-" else "$n\t${s.label}\t${s.command}"
    }

    /**
     * 時刻トリガー (`z2-alarm`)。サブコマンドは CLI 側で正規化済みで、ここには
     * `once <hour> <minute> <name>` / `at <epochMillis> <name>` / `daily <hour> <minute> <name>` /
     * `list` / `cancel <key>` が来る。「今日の HH:MM、過ぎていれば明日」の判定は Calendar が要るので
     * sh 側でなくここで行う (`in 5m` のような相対指定だけ sh 側で epoch に直して `at` で来る)。
     */
    private fun alarmCmd(context: Context, args: List<String>): String = when (args.getOrNull(0)) {
        "once" -> {
            val h = args.getOrNull(1)?.toIntOrNull() ?: -1
            val m = args.getOrNull(2)?.toIntOrNull() ?: -1
            if (h !in 0..23 || m !in 0..59) throw IllegalArgumentException("alarm: bad HH:MM")
            AlarmScheduler.add(
                context, args.getOrNull(3).orEmpty(), AlarmScheduler.KIND_ONCE,
                AlarmScheduler.nextDailyAt(h, m), -1, -1
            )
        }
        "at" -> {
            val at = args.getOrNull(1)?.toLongOrNull()
                ?: throw IllegalArgumentException("alarm: bad time")
            if (at <= System.currentTimeMillis()) throw IllegalArgumentException("alarm: time is in the past")
            AlarmScheduler.add(context, args.getOrNull(2).orEmpty(), AlarmScheduler.KIND_ONCE, at, -1, -1)
        }
        "daily" -> {
            val h = args.getOrNull(1)?.toIntOrNull() ?: -1
            val m = args.getOrNull(2)?.toIntOrNull() ?: -1
            if (h !in 0..23 || m !in 0..59) throw IllegalArgumentException("alarm: bad HH:MM")
            AlarmScheduler.add(
                context, args.getOrNull(3).orEmpty(), AlarmScheduler.KIND_DAILY,
                AlarmScheduler.nextDailyAt(h, m), h, m
            )
        }
        "list" -> AlarmScheduler.listJson(context)
        "cancel" -> {
            val key = args.getOrNull(1).orEmpty()
            if (key.isBlank()) throw IllegalArgumentException("alarm: cancel needs <id|name|all>")
            "{\"cancelled\":${AlarmScheduler.cancel(context, key)}}"
        }
        else -> throw IllegalArgumentException("alarm: unknown subcommand")
    }

    // POST_NOTIFICATIONS 未許可は下の runCatching で握って Log に流すので、lint の権限チェックは抑止する。
    @SuppressLint("MissingPermission")
    private fun doNotify(
        context: Context,
        titleArg: String,
        textArg: String,
        high: Boolean = false,
        name: String = "",
        buttons: List<String> = emptyList()
    ) {
        // 引数が 1 つだけなら本文として扱い、タイトルはアプリ名にする。
        val title = if (textArg.isBlank()) context.getString(R.string.app_name) else titleArg
        val text = if (textArg.isBlank()) titleArg else textArg
        // high=true のときだけ IMPORTANCE_HIGH チャンネル + PRIORITY_HIGH で画面上部にバナー表示する。
        val channel = if (high) CHANNEL_ID_HIGH else CHANNEL_ID
        val id = notifyId.incrementAndGet()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        // ボタン付きなら、押されたラベルを events.jsonl へ返す (対話型マクロ)。
        // Android の仕様上ボタンは 3 つまでしか表示されないので、超えた分は無視される。
        buttons.take(MAX_NOTIFY_BUTTONS).forEachIndexed { i, label ->
            val intent = Intent(context, NotifyActionReceiver::class.java)
                .setAction(NotifyActionReceiver.ACTION_TAP)
                .putExtra(NotifyActionReceiver.EXTRA_NAME, name)
                .putExtra(NotifyActionReceiver.EXTRA_LABEL, label)
                .putExtra(NotifyActionReceiver.EXTRA_NOTIF_ID, id)
            // requestCode は通知ごと・ボタンごとに一意にする (同じだと extras が使い回される)。
            val pi = PendingIntent.getBroadcast(
                context, id * MAX_NOTIFY_BUTTONS + i, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, label, pi)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }.onFailure { Log.w(TAG, "notify failed (POST_NOTIFICATIONS 未許可?)", it) }
    }

    private fun doShareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun doOpen(context: Context, target: String) {
        if (target.isBlank()) return
        val uri = target.toUri()
        val view = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(view)
    }

    private fun setClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
    }

    private fun getClipboard(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
    }

    private fun batteryJson(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return JSONObject().apply {
            put("level", level)
            put("charging", charging)
        }.toString()
    }

    private fun doVibrate(context: Context, ms: Long) {
        val duration = ms.coerceIn(1L, 5000L)
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * 端末標準の TTS で読み上げる。TTS エンジンの初期化は非同期なので、初回は発話を [pendingSpeech] に
     * 溜め、`onInit` が SUCCESS になった時点でまとめて流す。言語は端末の既定 Locale。
     */
    private fun doSay(context: Context, text: String) {
        if (text.isBlank()) return
        val t = tts
        if (t != null && ttsReady.get()) {
            t.speak(text, TextToSpeech.QUEUE_ADD, null, "z2say-${notifyId.incrementAndGet()}")
            return
        }
        pendingSpeech.add(text)
        if (tts == null) {
            // TextToSpeech は Main スレッドで生成し、コールバックも Main で受ける。
            runOnMain {
                if (tts != null) return@runOnMain
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady.set(true)
                        val engine = tts ?: return@TextToSpeech
                        var s = pendingSpeech.poll()
                        while (s != null) {
                            engine.speak(s, TextToSpeech.QUEUE_ADD, null, "z2say-${notifyId.incrementAndGet()}")
                            s = pendingSpeech.poll()
                        }
                    } else {
                        Log.w(TAG, "TTS init failed (status=$status)")
                        pendingSpeech.clear()
                    }
                }
            }
        }
    }

    /**
     * フラッシュライト (トーチ) を制御する。[mode] は `on` / `off` / `toggle`。
     * `CameraManager.setTorchMode` は権限不要。フラッシュ付きカメラが無ければ例外。
     * 戻り値は結果の点灯状態 (`on` / `off`)。
     */
    private fun torchSet(context: Context, mode: String): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: throw IllegalStateException("no camera flash available")
        val on = when (mode.lowercase()) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            "toggle", "" -> !torchOn
            else -> throw IllegalArgumentException("usage: on | off | toggle")
        }
        cm.setTorchMode(camId, on)
        torchOn = on
        return if (on) "on" else "off"
    }

    /**
     * メディア再生を制御する。[action] = `play`/`pause`/`playpause`/`next`/`previous`(`prev`)/`stop`。
     * `AudioManager.dispatchMediaKeyEvent` にメディアキーを流すだけ (権限不要)。実際の応答は
     * フォアグラウンドのメディアアプリ次第。
     */
    private fun doMedia(context: Context, action: String) {
        val keyCode = when (action.lowercase()) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "playpause", "toggle", "" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> throw IllegalArgumentException("usage: play|pause|playpause|next|previous|stop")
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /**
     * メディア音量を制御する。[arg] = `up`/`down`/`mute`/`unmute`/`N`(0〜max の直値)/`N%`(0〜100%)。
     * `STREAM_MUSIC` を対象に `AudioManager` で設定 (権限不要)。戻り値は結果の `current/max`。
     */
    private fun volumeSet(context: Context, arg: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val a = arg.lowercase()
        when {
            a == "up" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
            a == "down" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
            a == "mute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
            a == "unmute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            a.endsWith("%") -> {
                val pct = a.dropLast(1).toIntOrNull()
                    ?: throw IllegalArgumentException("usage: up|down|mute|unmute|N|N%")
                am.setStreamVolume(stream, (max * pct.coerceIn(0, 100)) / 100, 0)
            }
            else -> {
                val v = a.toIntOrNull()
                    ?: throw IllegalArgumentException("usage: up|down|mute|unmute|N|N%")
                am.setStreamVolume(stream, v.coerceIn(0, max), 0)
            }
        }
        return "${am.getStreamVolume(stream)}/$max"
    }

    /**
     * 汎用 Intent 発火。`am start` に似たフラグで任意の Intent を組み、既定で `startActivity`
     * (`--broadcast` で sendBroadcast、`--service` で startService) する。これ 1 本でアプリ起動・
     * 設定画面表示・アラーム設定・共有などを網羅できるマクロの要。権限不要 (呼び先が要求する権限は別)。
     *
     * 対応フラグ (順不同): `-a/--action <ACTION>` `-d/--data <URI>` `-t/--type <MIME>`
     * `-p/--package <PKG>` `-n/--component <PKG/CLS>` `-f/--flags <int>`
     * `--es <K> <V>`(文字列) `--ez <K> <true|false>`(真偽) `--ei <K> <int>`(整数)
     * `--broadcast` `--service`。先頭の非フラグ引数は action として扱う。
     */
    private fun doIntent(context: Context, args: List<String>) {
        val intent = Intent()
        var mode = "activity"
        var i = 0
        fun next(): String =
            if (i < args.size) args[i++] else throw IllegalArgumentException("intent: missing value")
        while (i < args.size) {
            val tok = args[i++]
            when (tok) {
                "-a", "--action" -> intent.action = next()
                "-d", "--data" -> intent.data = next().toUri()
                "-t", "--type" -> intent.type = next()
                "-p", "--package" -> intent.setPackage(next())
                "-n", "--component" -> {
                    val cn = ComponentName.unflattenFromString(next())
                        ?: throw IllegalArgumentException("intent: bad component (want PKG/CLS)")
                    intent.component = cn
                }
                "-f", "--flags" -> intent.addFlags(
                    next().toIntOrNull() ?: throw IllegalArgumentException("intent: --flags wants int")
                )
                "--es" -> intent.putExtra(next(), next())
                "--ez" -> intent.putExtra(next(), next().toBoolean())
                "--ei" -> intent.putExtra(
                    next(), next().toIntOrNull() ?: throw IllegalArgumentException("intent: --ei wants int")
                )
                "--broadcast" -> mode = "broadcast"
                "--service" -> mode = "service"
                else -> if (intent.action == null && !tok.startsWith("-")) intent.action = tok
                        else throw IllegalArgumentException("intent: unknown arg '$tok'")
            }
        }
        when (mode) {
            "broadcast" -> context.sendBroadcast(intent)
            "service" -> { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startService(intent) }
            else -> { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }
        }
    }

    /**
     * センサーを 1 回だけ読んで JSON で返す。[kind] = `light`(照度) / `accel`(加速度) / `proximity`(近接)。
     * いずれも権限不要。リスナを登録して最初の値を受け取ったら解除する (常時オンにしない=電池に優しい)。
     * light → `{"lux":F}` / proximity → `{"distance":F}` / accel → `{"x":F,"y":F,"z":F}`。
     */
    private fun sensorRead(context: Context, kind: String): String {
        val type = when (kind.lowercase()) {
            "light" -> Sensor.TYPE_LIGHT
            "accel", "accelerometer" -> Sensor.TYPE_ACCELEROMETER
            "proximity", "prox" -> Sensor.TYPE_PROXIMITY
            else -> throw IllegalArgumentException("usage: light|accel|proximity")
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(type) ?: throw IllegalStateException("no $kind sensor")
        val latch = java.util.concurrent.CountDownLatch(1)
        val out = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (latch.count == 0L) return
                System.arraycopy(e.values, 0, out, 0, minOf(e.values.size, 3))
                latch.countDown()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)
        try {
            if (!latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
                throw IllegalStateException("sensor read timeout")
        } finally {
            sm.unregisterListener(listener)
        }
        return when (type) {
            Sensor.TYPE_LIGHT -> JSONObject().put("lux", out[0].toDouble()).toString()
            Sensor.TYPE_PROXIMITY -> JSONObject().put("distance", out[0].toDouble()).toString()
            else -> JSONObject()
                .put("x", out[0].toDouble()).put("y", out[1].toDouble()).put("z", out[2].toDouble())
                .toString()
        }
    }

    // --- 補助 ---

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.api_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.api_channel_desc) }
            )
        }
        // バナー (ヘッドアップ) 用の高重要度チャンネル。`z2-notify -h` のときだけ使う。
        if (nm.getNotificationChannel(CHANNEL_ID_HIGH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_HIGH,
                    context.getString(R.string.api_channel_high_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.api_channel_high_desc) }
            )
        }
    }

    private fun writeResponse(id: String, ok: Boolean, data: String) {
        val dir = respDir ?: return
        val prefix = if (ok) "OK " else "ERR "
        val tmp = File(dir, ".$id.tmp")
        val out = File(dir, "$id.resp")
        runCatching {
            tmp.writeText(prefix + encode(data) + "\n")
            tmp.renameTo(out)
        }.onFailure { Log.w(TAG, "write response failed", it) }
    }

    private fun decode(b64: String): String =
        runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")

    private fun encode(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** clip-get のように Main で値を取って返す必要があるときに使う同期実行。 */
    private fun <T> runOnMainSync(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = java.util.concurrent.CountDownLatch(1)
        @Suppress("UNCHECKED_CAST")
        var result: T? = null
        var error: Throwable? = null
        mainHandler.post {
            try { result = block() } catch (e: Throwable) { error = e } finally { latch.countDown() }
        }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
