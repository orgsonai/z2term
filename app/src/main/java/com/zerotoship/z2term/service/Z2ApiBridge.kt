package com.zerotoship.z2term.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
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
