package com.zerotoship.z2term.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
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
 * = 通知検知デーモン)。本文は [extractBody] で主要な通知スタイル (MessagingStyle の SMS/OTP・
 * InboxStyle・補助行など) まで走査して取り出す。設定 [AppSettings.notificationCaptureEnabled] が ON で、かつ
 * [AppSettings.notificationLogEnabled] が ON のとき、受け取った通知を **生のまま**
 * [logFile] (`~/.z2term/notifications.jsonl`) へ 1 行 1 通知 (JSON) で追記する。
 * 保存を OFF にすると検知 (常駐) は続けたままファイルには一切書かない。
 *
 * **重複排除**: Android は 1 つの通知を内容が変わらなくても何度も再 post する (進捗更新・
 * 常駐通知の再掲・グループ集約など)。そのまま書くと同じ行が何本も並ぶため、
 *  - 同じ通知 (`key`) で内容 (title+text) が前回と同一なら書かない
 *  - 別 `key` でも同じアプリ・同じ内容が [DEDUP_WINDOW_MS] 以内に続いたら書かない
 * とし、**1 通知 = 1 行**にする。通知が消された (`onNotificationRemoved`) 後の再掲は
 * 新しい通知として書く。
 *
 * **方針**: 特定アプリの抽出・フィルタ・保存方針・配信は一切ハードコードしない。z2term は「通知を
 * 検知してターミナルから読める形で流すだけ」の汎用機能を提供し、ログ化・絞り込み・配信は
 * ユーザーがターミナル側 (tail / 自作スクリプト / 常駐サーバー) で自由に組む。完全ローカル・外部送信なし。
 */
class NotificationLogService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var captureEnabled = false
    @Volatile private var logEnabled = true
    @Volatile private var formatTemplate = ""
    @Volatile private var prepend = false

    /** 通知 `key` ごとの最終記録内容 (再 post 判定用)。古いものから捨てる LRU。 */
    private val lastSigByKey = lru<String>(DEDUP_ENTRIES)

    /** 「アプリ + 内容」ごとの最終記録時刻 (key を作り直すアプリ向けの短時間 dedup)。 */
    private val lastTimeBySig = lru<Long>(DEDUP_ENTRIES)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 設定を購読してキャッシュ (通知ごとに DataStore を叩かない)。
        scope.launch {
            AppSettings(applicationContext).flow.collectLatest {
                captureEnabled = it.notificationCaptureEnabled
                logEnabled = it.notificationLogEnabled
                formatTemplate = it.notificationLogFormat
                prepend = it.notificationLogPrepend
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!captureEnabled || sbn == null) return
        val n = sbn.notification ?: return
        if (sbn.packageName == applicationContext.packageName) return   // 自分の通知は除外
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extractBody(n, ex)
        if (title.isEmpty() && text.isEmpty()) return                   // 実体のない通知は捨てる
        // 同じ通知の再掲 (進捗更新など) は 1 回だけ扱う。**トリガーの前に**判定するので、
        // ログを保存していなくても連続発火しない。
        if (isDuplicate(sbn.key ?: sbn.packageName, sbn.packageName, title, text)) return

        val app = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        // `notify:*` トリガー (0.8.236)。**ログ保存とは独立**に動かす — 「記録はしないが
        // トリガーには使いたい」が普通の使い方で、記録を必須にすると通知本文がずっと
        // ファイルに残ることになる。ここは通知配信スレッドなので writer へ逃がす。
        val whenCtx = applicationContext
        writer.execute {
            runCatching {
                WhenManager.onNotification(whenCtx, sbn.packageName, app, title, text)
            }.onFailure { Log.w(TAG, "when notify failed: ${it.message}") }
        }

        if (!logEnabled) return                                         // 検知のみ (保存しない)

        val line = render(
            formatTemplate,
            ts = sbn.postTime, time = ISO.format(Date(sbn.postTime)),
            pkg = sbn.packageName, app = app, title = title, text = text,
            category = n.category ?: "", key = sbn.key ?: ""
        )

        val ctx = applicationContext
        val prependNow = prepend
        writer.execute {
            runCatching {
                LogWriter.write(logFile(ctx), line, prependNow)
            }.onFailure { Log.w(TAG, "write failed: ${it.message}") }
        }
    }

    /**
     * 通知本文を「中身のある最初のフィールド」から 1 つ取り出す。標準の TITLE/TEXT だけだと
     * MessagingStyle の SMS・ワンタイムパスワード等 (本文が [Notification.EXTRA_MESSAGES] に入り、
     * TEXT は空) を取りこぼすため、主要な通知スタイルの本文フィールドを優先順に走査する。
     * 優先: 展開本文 (BigText) > 本文 (Text) > MessagingStyle > InboxStyle > 補助行 > ticker。
     */
    private fun extractBody(n: Notification, ex: Bundle): String {
        ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        messagesText(ex).let { if (it.isNotEmpty()) return it }
        textLines(ex).let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        n.tickerText?.toString()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    /** MessagingStyle の各メッセージ本文を古い順に改行連結 (SMS/チャットの OTP はここに入る)。 */
    @Suppress("DEPRECATION")
    private fun messagesText(ex: Bundle): String {
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return ""
        val sb = StringBuilder()
        for (p in arr) {
            val b = p as? Bundle ?: continue
            val t = b.getCharSequence("text")?.toString().orEmpty()
            if (t.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
        }
        return sb.toString()
    }

    /** InboxStyle の複数行 ([Notification.EXTRA_TEXT_LINES]) を改行連結。 */
    private fun textLines(ex: Bundle): String {
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return ""
        return lines.mapNotNull { it?.toString()?.takeIf(String::isNotEmpty) }.joinToString("\n")
    }

    /**
     * 通知が消された (ユーザーが払った / アプリが取り消した) ら記録も忘れる。
     * 同じ内容が改めて通知されたときは「新しい通知」として 1 行書きたいため。
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        synchronized(lastSigByKey) { lastSigByKey.remove(key) }
    }

    /**
     * 既に同じ内容を記録済みなら true (= 今回は書かない)。
     *  - 同じ `key` で内容が前回と同一 → 再 post とみなす
     *  - 別 `key` でも同じアプリ・同じ内容が [DEDUP_WINDOW_MS] 以内 → 作り直しとみなす
     */
    private fun isDuplicate(key: String, pkg: String, title: String, text: String): Boolean {
        val sig = "$title\u0000$text"
        val now = System.currentTimeMillis()
        synchronized(lastSigByKey) {
            if (lastSigByKey[key] == sig) return true
            val sigKey = "$pkg\u0000$sig"
            val prev = lastTimeBySig[sigKey]
            if (prev != null && now - prev < DEDUP_WINDOW_MS) {
                lastSigByKey[key] = sig
                lastTimeBySig[sigKey] = now
                return true
            }
            lastSigByKey[key] = sig
            lastTimeBySig[sigKey] = now
        }
        return false
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        scope.cancel()
        writer.shutdown()
    }

    companion object {
        private const val TAG = "NotificationLog"

        /**
         * 稼働中インスタンス。`z2-noti list` が**いま出ている通知**を読むために要る
         * (`getActiveNotifications()` は `NotificationListenerService` のメソッドで、
         * OS が bind したインスタンスからしか呼べない)。
         */
        @Volatile private var instance: NotificationLogService? = null

        /**
         * いま出ている通知を TSV (key / パッケージ / アプリ名 / タイトル / 本文) で返す (0.8.236)。
         *
         * 通知アクセスが未許可・サービスが bind されていなければ null。**読むだけ**で、
         * 押す・消すはできない — 他アプリの決済や送信のボタンを押せてしまうと、誤爆の実害が
         * このアプリの外に出る (提案 20 の検討でその動詞だけ落とした)。
         */
        fun activeNotificationsTsv(): String? {
            val svc = instance ?: return null
            val list = runCatching { svc.activeNotifications }.getOrNull() ?: return null
            return list.filter { it.packageName != svc.applicationContext.packageName }
                .joinToString("\n") { sbn ->
                    val n = sbn.notification
                    val ex = n?.extras
                    val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val text = if (n != null && ex != null) svc.extractBody(n, ex) else ""
                    val app = runCatching {
                        val pm = svc.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName)
                    // タブ区切りを壊さないよう、値の中のタブと改行は空白へ寄せる。
                    listOf(sbn.key.orEmpty(), sbn.packageName, app, title, text)
                        .joinToString("\t") { v -> v.replace('\t', ' ').replace('\n', ' ') }
                }
        }
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

        /** 重複判定で覚えておく通知の件数 (これを超えたら古いものから忘れる)。 */
        private const val DEDUP_ENTRIES = 256

        /** 別 `key` で同じ内容が来たとき「同じ通知の作り直し」とみなす時間 (ミリ秒)。 */
        private const val DEDUP_WINDOW_MS = 10_000L

        /** 挿入/参照順で最大 [max] 件だけ保持する LRU マップ。 */
        private fun <V> lru(max: Int): LinkedHashMap<String, V> =
            object : LinkedHashMap<String, V>(max, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean =
                    size > max
            }

        /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。ターミナルからは `~/.z2term/notifications.jsonl`。 */
        const val LOG_REL = ".z2term/notifications.jsonl"

        /** ログの実ファイル (`filesDir/shared_home/.z2term/notifications.jsonl`)。 */
        fun logFile(context: Context): File =
            File(File(context.filesDir, "shared_home"), LOG_REL)

        private fun oneline(s: String): String =
            s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')

        /**
         * 1 通知を [template] に沿って 1 行分の文字列 (末尾改行なし) にする。
         * [template] が空なら JSONL。プレースホルダ `{time}` `{ts}` `{pkg}` `{app}` `{title}`
         * `{text}` `{category}` `{key}` と 1 行化 `{text1}` `{title1}`、エスケープ `\n` `\t` `\\` に対応。
         */
        fun render(
            template: String,
            ts: Long, time: String, pkg: String, app: String,
            title: String, text: String, category: String, key: String
        ): String {
            if (template.isBlank()) {
                return JSONObject()
                    .put("ts", ts).put("time", time).put("pkg", pkg).put("app", app)
                    .put("title", title).put("text", text).put("category", category).put("key", key)
                    .toString()
            }
            val vars = mapOf(
                "ts" to ts.toString(), "time" to time, "pkg" to pkg, "app" to app,
                "title" to title, "text" to text, "category" to category, "key" to key,
                "text1" to oneline(text), "title1" to oneline(title)
            )
            val sb = StringBuilder(template.length + 64)
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
