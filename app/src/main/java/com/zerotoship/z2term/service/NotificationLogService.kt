package com.zerotoship.z2term.service

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
 * Captures notification payloads, including silent updates, without app-specific extraction rules.
 * Conversation/history and InboxStyle entries use occurrence-aware deduplication. Plain reposts
 * are deduplicated separately, so fresh messages with unchanged text are retained.
 * Only text supplied by Android can be saved: redacted or unpublished content cannot be recovered.
 */
class NotificationLogService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    private val triggers = Executors.newSingleThreadExecutor()
    private val history = NotificationText.History()
    private val settingsReady = CompletableDeferred<Unit>()
    private data class Policy(val capture: Boolean = false, val log: Boolean = true,
        val template: String = "", val prepend: Boolean = false)
    @Volatile private var policy = Policy()
    @Volatile private var connected = false

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            AppSettings(applicationContext).flow.collectLatest {
                policy = Policy(it.notificationCaptureEnabled, it.notificationLogEnabled,
                    it.notificationLogFormat, it.notificationLogPrepend)
                settingsReady.complete(Unit)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        instance = this
    }

    override fun onListenerDisconnected() {
        connected = false
        if (instance === this) instance = null
        super.onListenerDisconnected()
        if (policy.capture) runCatching { requestRebind(ComponentName(this, javaClass)) }
            .onFailure { Log.w(TAG, "notification rebind failed: " + it.message) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Queue events during the initial settings read rather than discarding them as disabled.
        scope.launch(Dispatchers.Main.immediate) {
            settingsReady.await()
            capture(sbn)
        }
    }

    private fun capture(sbn: StatusBarNotification) {
        val settings = policy
        if (!settings.capture || sbn.packageName == applicationContext.packageName) return
        val n = sbn.notification ?: return
        // Snapshot text before queueing. Never replace a posted event with a later active snapshot.
        val extracted = runCatching {
            val ex = n.extras ?: Bundle.EMPTY
            stripBidi(ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()) to extractBody(n, ex)
        }.getOrElse { Log.w(TAG, "notification extraction failed: " + it.message); return }
        val (title, body) = extracted
        if (title.isEmpty() && body.text.isEmpty()) return
        val key = sbn.key ?: sbn.packageName
        val category = n.category.orEmpty()
        val group = if (sbn.isGroup) sbn.groupKey.orEmpty() else ""
        val eventTime = if (category == Notification.CATEGORY_MESSAGE)
            n.`when`.takeIf { it > 0L } ?: sbn.postTime else 0L
        val ctx = applicationContext
        val receivedAt = SystemClock.elapsedRealtime()
        // UserHandle.getIdentifier() is not in the public SDK; this accessor is public.
        @Suppress("DEPRECATION")
        val userId = sbn.userId
        writer.execute {
            val text = history.fresh(key, body, title, eventTime, group,
                app = sbn.packageName + ":" + userId, now = receivedAt) ?: return@execute
            val app = runCatching {
                val pm = packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
            // Persist before scheduling triggers: a slow automation rule must not hold up the log.
            if (settings.log) runCatching {
                val line = render(settings.template, ts = sbn.postTime,
                    time = timestampFormat.format(Date(sbn.postTime)), pkg = sbn.packageName, app = app,
                    title = title, text = text, category = category, key = key)
                LogWriter.write(logFile(ctx), line, settings.prepend)
            }.onFailure { Log.w(TAG, "write failed: " + it.message) }
            triggers.execute {
                runCatching { WhenManager.onNotification(ctx, sbn.packageName, app, title, text, category) }
                    .onFailure { Log.w(TAG, "when notify failed: " + it.message) }
            }
        }
    }

    private fun extractBody(n: Notification, ex: Bundle): NotificationText.Body {
        fun field(name: String) = stripBidi(ex.getCharSequence(name)?.toString().orEmpty())
        return NotificationText.body(
            current = messages(ex, Notification.EXTRA_MESSAGES),
            history = messages(ex, Notification.EXTRA_HISTORIC_MESSAGES),
            bigText = field(Notification.EXTRA_BIG_TEXT), text = field(Notification.EXTRA_TEXT),
            lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
                .mapNotNull { it?.toString()?.let(::stripBidi) },
            fallback = listOf(field(Notification.EXTRA_SUB_TEXT), field(Notification.EXTRA_INFO_TEXT),
                stripBidi(n.tickerText?.toString().orEmpty()))
        )
    }

    /** Android 11+ supplies the platform decoder; Android 10 uses the same Bundle fields. */
    @Suppress("DEPRECATION")
    private fun messages(ex: Bundle, field: String): List<NotificationText.Message> {
        val array = ex.getParcelableArray(field) ?: return emptyList()
        if (Build.VERSION.SDK_INT >= 30) {
            return Notification.MessagingStyle.Message.getMessagesFromBundleArray(array).mapNotNull {
                val text = stripBidi(it.text?.toString().orEmpty())
                if (text.isBlank()) null else NotificationText.Message(it.timestamp, text,
                    it.senderPerson?.let(::senderIdentity) ?: it.sender?.toString().orEmpty())
            }
        }
        return array.mapNotNull {
            val bundle = it as? Bundle ?: return@mapNotNull null
            val text = stripBidi(bundle.getCharSequence("text")?.toString().orEmpty())
            val person = bundle.getParcelable<Person>("sender_person")
            if (text.isBlank()) null else NotificationText.Message(bundle.getLong("time"), text,
                person?.let(::senderIdentity) ?: bundle.getCharSequence("sender")?.toString().orEmpty())
        }
    }

    private fun senderIdentity(person: Person): String =
        person.key ?: person.uri ?: person.name?.toString().orEmpty()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        writer.execute { history.removed(key) }
    }

    override fun onDestroy() {
        connected = false
        if (instance === this) instance = null
        super.onDestroy()
        scope.cancel()
        // Drain pending saves before closing the executor they use to schedule trigger work.
        writer.execute { triggers.shutdown() }
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
            if (!svc.connected) return null
            val list = runCatching { svc.activeNotifications }.getOrNull() ?: return null
            return list.filter { it.packageName != svc.applicationContext.packageName }
                .joinToString("\n") { sbn ->
                    val n = sbn.notification
                    val ex = n?.extras
                    val title = stripBidi(ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
                    // 履歴管理を通さず、差分にしない。`z2-noti list` は「いま出ている通知を
                    // 読む」コマンドなので、会話は載っているものを全部見せる。
                    val text = stripBidi(
                        if (n != null && ex != null) svc.extractBody(n, ex).text else ""
                    )
                    val app = runCatching {
                        val pm = svc.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName)
                    // タブ区切りを壊さないよう、値の中のタブと改行は空白へ寄せる。
                    listOf(sbn.key.orEmpty(), sbn.packageName, app, title, text)
                        .joinToString("\t") { v -> v.replace('\t', ' ').replace('\n', ' ') }
                }
        }

        /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。ターミナルからは `~/.z2term/notifications.jsonl`。 */
        const val LOG_REL = ".z2term/notifications.jsonl"

        /** ログの実ファイル (`filesDir/shared_home/.z2term/notifications.jsonl`)。 */
        fun logFile(context: Context): File =
            File(File(context.filesDir, "shared_home"), LOG_REL)

        private fun oneline(s: String): String =
            s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')

        /**
         * 双方向テキストの制御文字を落とす (0.8.356)。
         *
         * **なぜ要るか**: Android の電話アプリは電話番号を `BidiFormatter` で包んで通知に出すため、
         * 表示は `0120-355-565` でも実体は `U+202A` + 番号 + `U+202C` になる。**画面にも
         * ログにも見えない**ので、番号の形かどうかを見るマクロ (同梱の `unknown-call.sh` は
         * `tr -d '0-9+() -'` で「何も残らない」ことを見る) が**名前と誤判定して黙って何もしない**。
         * 実機で着信を取り落としていたのがこれで、`z2-when fired` には `run` と残るため
         * 「動いているのに何も起きない」という一番読みにくい壊れ方になっていた。
         *
         * ⚠ **落とすのは表示に影響しない制御文字だけ** — 見た目が変わらないものを消しているので、
         * ログの「生のまま残す」方針とはぶつからない。文字を並べ替えたり削ったりはしない。
         * ⚠ トリガー判定 (`notify:title=` の部分一致) とログの**両方**に効かせること。
         * 片方だけだと「ログでは番号なのにルールが一致しない」という食い違いが起きる。
         */
        fun stripBidi(s: String): String =
            if (s.none(::isBidiControl)) s else s.filterNot(::isBidiControl)

        /**
         * 双方向テキストの制御文字か。LRM/RLM/ALM・埋め込みと上書き (`U+202A`〜`U+202E`)・
         * 分離 (`U+2066`〜`U+2069`) の 3 組で、Unicode が定める全部。
         */
        private fun isBidiControl(c: Char): Boolean =
            c == '\u200E' || c == '\u200F' || c == '\u061C' ||
                c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069'

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
