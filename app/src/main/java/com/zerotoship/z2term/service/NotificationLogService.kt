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
 * = 通知検知デーモン)。本文は [extractBody] で主要な通知スタイル (MessagingStyle の会話・
 * InboxStyle・補助行など) まで走査して取り出す。**会話は前回の続きから**だけを書く
 * ([freshMessageText])。設定 [AppSettings.notificationCaptureEnabled] が ON で、かつ
 * [AppSettings.notificationLogEnabled] が ON のとき、受け取った通知を **生のまま**
 * [logFile] (`~/.z2term/notifications.jsonl`) へ 1 行 1 通知 (JSON) で追記する
 * (⚠ 唯一の例外が [stripBidi]。**画面にも出ない**双方向制御文字だけを落とすので、
 * 読める中身は一字も変わらない)。
 * 保存を OFF にすると検知 (常駐) は続けたままファイルには一切書かない。
 *
 * **重複排除**: Android は 1 つの通知を内容が変わらなくても何度も再 post する (進捗更新・
 * 常駐通知の再掲・グループ集約など)。そのまま書くと同じ行が何本も並ぶため、
 *  - 同じ通知 (`key`) で内容 (title+text) が前回と同一なら書かない
 *  - 別 `key` でも同じアプリ・同じ内容が [DEDUP_WINDOW_MS] 以内に続いたら書かない
 * とし、**1 通知 = 1 行**にする。通知が消された (`onNotificationRemoved`) 後の再掲は
 * 新しい通知として書く。⚠ **会話 (MessagingStyle) だけは行の単位が違う**: 続けて届いた
 * 数通が 1 回の通知にまとまるので、**その回の新着ぶん全部**が (改行で連なって) 1 行になる。
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

    /**
     * 通知 `key` ごとの「最後に書き出した会話メッセージの印」([messageSig]、0.8.358)。
     *
     * ⚠ [onNotificationRemoved] で**忘れない**。通知を払っただけで忘れると、次に同じ会話の
     * 通知が来たときに**会話の履歴がまるごと再記録**される (会話アプリは毎回、直近の数件を
     * 通知に載せてくるため)。⚠ 逆に LRU からあふれた場合は、次の 1 回だけ履歴が多めに出る —
     * 取りこぼすよりは重複するほうがましだ、という判断。
     */
    private val lastMsgByKey = lru<String>(DEDUP_ENTRIES)

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
        // 見えない双方向制御文字はここで落とす ([stripBidi])。**トリガーにもログにも効かせる**ため、
        // 取り出した直後の 1 か所でやる (電話番号が U+202A で包まれて届く。理由は同関数の KDoc)。
        val title = stripBidi(ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
        // 会話の通知は**新着分だけ**が返る (0.8.358)。null = 新着ゼロ = 書くことも起こすことも
        // 無い再掲なので、ここで捨てる。⚠ 空文字と null は別物 — 空文字だと題名だけの行が残る。
        val text = extractBody(n, ex, sbn.key ?: sbn.packageName)?.let(::stripBidi) ?: return
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
        val category = n.category ?: ""
        writer.execute {
            runCatching {
                WhenManager.onNotification(whenCtx, sbn.packageName, app, title, text, category)
            }.onFailure { Log.w(TAG, "when notify failed: ${it.message}") }
        }

        if (!logEnabled) return                                         // 検知のみ (保存しない)

        val line = render(
            formatTemplate,
            ts = sbn.postTime, time = ISO.format(Date(sbn.postTime)),
            pkg = sbn.packageName, app = app, title = title, text = text,
            category = category, key = sbn.key ?: ""
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
     * 通知本文を「中身のある最初のフィールド」から取り出す。
     * 優先: **会話 (MessagingStyle)** > 展開本文 (BigText) > 本文 (Text) > InboxStyle >
     * 補助行 (SubText / InfoText) > ticker。
     *
     * ⭐ **会話を最優先にするのが 0.8.358 の変更点**。以前は `EXTRA_TEXT` を先に見ていたが、
     * 会話アプリは**続けて届いた何通かをまとめて 1 回だけ通知し直す**うえ、`EXTRA_TEXT` には
     * **最新の 1 通を表示用に短くしたもの**しか入れない。⇒ 途中の何通かが記録から丸ごと
     * 抜け落ち、残った 1 通も途中で切れる、という壊れ方をしていた (実機で 4 通中 3 通が消え、
     * 残る 1 通も末尾が欠けていた)。会話の全文は `EXTRA_MESSAGES` の側にある。
     *
     * [key] が非 null なら**記録・トリガー用**で、会話は [freshMessageText] が
     * **前回の続きから**だけを返す (載っている数通は毎回同じものが繰り返し届くため)。
     * null なら**読み出し用** (`z2-noti list`) で、載っているものを全部返す。
     *
     * @return 本文。**null は「新着ゼロ = 書くことも起こすことも無い」** (空文字とは別物 —
     *   空文字にすると題名だけの行が残る)
     */
    private fun extractBody(n: Notification, ex: Bundle, key: String?): String? {
        val msgs = messages(ex)
        if (msgs.isNotEmpty()) {
            if (key == null) return msgs.joinToString("\n") { it.second }
            // 印の読み出しと更新は 1 つの synchronized で行う (通知は複数スレッドから届く)。
            val prev = synchronized(lastMsgByKey) {
                val p = lastMsgByKey[key]
                lastMsgByKey[key] = messageSig(msgs.last())
                p
            }
            return freshMessageText(msgs, prev)
        }
        ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        textLines(ex).let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.let { if (it.isNotEmpty()) return it }
        n.tickerText?.toString()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    /**
     * MessagingStyle の各メッセージを **(時刻, 本文)** で古い順に返す (SMS/チャットの OTP も
     * ここに入る)。⚠ 本文が空の要素 (画像だけの発言など) は落とす — 記録しても読めない。
     */
    @Suppress("DEPRECATION")
    private fun messages(ex: Bundle): List<Pair<Long, String>> {
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return arr.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val t = b.getCharSequence("text")?.toString().orEmpty()
            if (t.isEmpty()) null else b.getLong("time") to t
        }
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
                    val title = stripBidi(ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
                    // ⚠ key を渡さない = 差分にしない。`z2-noti list` は「いま出ている通知を
                    // 読む」コマンドなので、会話は載っているものを全部見せる。
                    val text = stripBidi(
                        if (n != null && ex != null) svc.extractBody(n, ex, null).orEmpty() else ""
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

        /**
         * 会話メッセージ 1 件の印 (0.8.358)。**時刻だけでは足りない** — 同じ時刻に複数届く
         * こともあり、時刻を持たない (0 が並ぶ) 送り手もいるため、本文と対にして持つ。
         */
        fun messageSig(m: Pair<Long, String>): String = "${m.first}\u0000${m.second}"

        /**
         * [all] のうち、前回書き出した印 [prev] より**後ろのメッセージだけ**を改行で連ねる
         * (0.8.358)。会話アプリは通知のたびに**直近の数通を毎回まるごと**載せてくるので、
         * そのまま書くと同じ発言が何度も記録される。
         *
         * ⚠ [prev] が見つからないとき (初回・LRU からあふれた・送り手が印を変えた) は
         * **載っているものを全部返す**。取りこぼすより重複するほうがまし、という判断
         * (重複の一部は既存の [isDuplicate] でも落ちる)。
         * ⚠ [all] の**最後の一致**から後ろを採る — 同じ本文が会話に複数あっても、新しい側を
         * 続きの起点にできる。
         *
         * @return 新着の本文。**null = 新着ゼロ** (= その通知は記録もトリガーもしない)
         */
        fun freshMessageText(all: List<Pair<Long, String>>, prev: String?): String? {
            if (all.isEmpty()) return null
            val cut = if (prev == null) -1 else all.indexOfLast { messageSig(it) == prev }
            val fresh = if (cut >= 0) all.drop(cut + 1) else all
            return if (fresh.isEmpty()) null else fresh.joinToString("\n") { it.second }
        }

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
