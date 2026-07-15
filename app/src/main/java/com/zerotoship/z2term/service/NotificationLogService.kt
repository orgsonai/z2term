package com.zerotoship.z2term.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
 * 通知検知の常駐部 (汎用入口)。
 *
 * OS の「通知アクセス」許可が本アプリに与えられていると、Android がこの
 * `NotificationListenerService` を自動でバインド・常駐させる (アプリを開かなくても・再起動後も動く
 * = 通知検知デーモン)。設定 [AppSettings.notificationCaptureEnabled] が ON のとき、受け取った通知を
 * **生のまま** [logFile] (`~/.z2term/notifications.jsonl`) へ 1 行 1 通知 (JSON) で追記する。
 *
 * **方針**: 特定アプリの抽出・フィルタ・保存方針・配信は一切ハードコードしない。z2term は「通知を
 * 検知してターミナルから読める形で流すだけ」の汎用機能を提供し、ログ化・絞り込み・配信は
 * ユーザーがターミナル側 (tail / 自作スクリプト / 常駐サーバー) で自由に組む。完全ローカル・外部送信なし。
 */
class NotificationLogService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var captureEnabled = false

    override fun onCreate() {
        super.onCreate()
        // 設定フラグを購読してキャッシュ (通知ごとに DataStore を叩かない)。
        scope.launch {
            AppSettings(applicationContext).flow.collectLatest { captureEnabled = it.notificationCaptureEnabled }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!captureEnabled || sbn == null) return
        val n = sbn.notification ?: return
        if (sbn.packageName == applicationContext.packageName) return   // 自分の通知は除外
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return                   // 実体のない通知は捨てる

        val app = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val line = JSONObject()
            .put("ts", sbn.postTime)
            .put("time", ISO.format(Date(sbn.postTime)))
            .put("pkg", sbn.packageName)
            .put("app", app)
            .put("title", title)
            .put("text", text)
            .put("category", n.category ?: "")
            .put("key", sbn.key ?: "")
            .toString()

        val ctx = applicationContext
        writer.execute {
            runCatching {
                val f = logFile(ctx)
                f.parentFile?.mkdirs()
                f.appendText(line + "\n")
            }.onFailure { Log.w(TAG, "append failed: ${it.message}") }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        writer.shutdown()
    }

    companion object {
        private const val TAG = "NotificationLog"
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

        /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。ターミナルからは `~/.z2term/notifications.jsonl`。 */
        const val LOG_REL = ".z2term/notifications.jsonl"

        /** ログの実ファイル (`filesDir/shared_home/.z2term/notifications.jsonl`)。 */
        fun logFile(context: Context): File =
            File(File(context.filesDir, "shared_home"), LOG_REL)
    }
}
