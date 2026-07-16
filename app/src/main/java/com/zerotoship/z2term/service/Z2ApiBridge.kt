package com.zerotoship.z2term.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
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
import com.zerotoship.z2term.R
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
            "notify" -> { doNotify(context, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty()); null }
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
            else -> throw IllegalArgumentException("unknown cmd: $cmd")
        }
    }

    // --- 各機能 ---

    // POST_NOTIFICATIONS 未許可は下の runCatching で握って Log に流すので、lint の権限チェックは抑止する。
    @SuppressLint("MissingPermission")
    private fun doNotify(context: Context, titleArg: String, textArg: String) {
        // 引数が 1 つだけなら本文として扱い、タイトルはアプリ名にする。
        val title = if (textArg.isBlank()) context.getString(R.string.app_name) else titleArg
        val text = if (textArg.isBlank()) titleArg else textArg
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifyId.incrementAndGet(), n)
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
        val uri = Uri.parse(target)
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
        val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) bm.isCharging else
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
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
                "-d", "--data" -> intent.data = Uri.parse(next())
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
