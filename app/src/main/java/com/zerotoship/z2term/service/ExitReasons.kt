package com.zerotoship.z2term.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **前回までの「なぜ落ちたか」** (0.8.376)。
 *
 * アプリが突然消える症状は、これまで端末側から原因を辿る手段が無かった。理由は 2 つ:
 *
 *  1. **落ち方の多くはログを残さない。** Java の例外なら `logcat -b crash` に残るが、
 *     メモリ不足でカーネルに殺された (SIGKILL) 場合は**アプリは自分が死んだことすら
 *     知らない**まま消える。ネイティブ側のクラッシュも tombstone は system uid の管轄で、
 *     アプリからは読めない。
 *  2. **logcat は当てにならない。** 実機で測ると、このアプリの uid から読めるバッファは
 *     **十数分ぶんしか残らない** (エンジン下の exec が SELinux 監査行を大量に出すため)。
 *     数時間に 1 回の事象は、気付いて見に行った時にはもう流れている。
 *
 * OS 側は [ActivityManager.getHistoricalProcessExitReasons] に**直近 16 回ぶんの死因**を
 * 持っている (時刻・理由・シグナル・死んだ時の RSS・OS の説明文)。ここはそれを
 *
 *  - アプリの起動時に読んで logcat へ出し、
 *  - **`~/.z2term/exits.jsonl` へ写して残す** (logcat が流れても後から読めるように)
 *
 * ための入口。z2doctor の「前回までの終了」欄もここを通る。
 *
 * ⚠ **API 30 未満では OS がこの記録を持っていない**。その場合は静かに何もしない
 * (minSdk 29 なので存在しうる)。
 *
 * ⚠ **原因を判定しない。** 事実 (OS が言っている理由) だけを出す。z2doctor の他の欄と
 * 同じ約束で、解釈は読む人に任せる。
 */
object ExitReasons {

    private const val TAG = "ExitReasons"

    /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。端末からは `~/.z2term/exits.jsonl`。 */
    const val LOG_REL = ".z2term/exits.jsonl"

    /** OS から一度に受け取る件数 (OS 側の保持上限も 16 件)。 */
    private const val MAX_RECORDS = 16

    /** ファイルに残す行数。古い順に捨てる。 */
    private const val KEEP_LINES = 200

    /** z2doctor に出す件数。 */
    const val REPORT_MAX = 5

    // ⚠ SimpleDateFormat は**スレッド安全でない**。ここは起動時の記録 (IO) と z2doctor の
    // 問い合わせ (z2api のワーカー) から同時に呼ばれうるので、必ず下の 2 つを通す。
    // 診断が出す時刻が壊れると、落ちた時刻という**一番肝心な事実**が嘘になる。
    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    private val SHORT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun isoTime(ts: Long): String = synchronized(ISO) { ISO.format(Date(ts)) }
    private fun shortTime(ts: Long): String = synchronized(SHORT) { SHORT.format(Date(ts)) }

    /** ログの実ファイル (`filesDir/shared_home/.z2term/exits.jsonl`)。 */
    fun logFile(context: Context): File =
        File(File(context.filesDir, "shared_home"), LOG_REL)

    /** 死因 1 件。OS が言っていることをそのまま持つ (解釈しない)。 */
    data class Record(
        val ts: Long,
        val reason: Int,
        val status: Int,
        val importance: Int,
        val rssKb: Long,
        val pid: Int,
        val processName: String,
        val description: String,
    ) {
        /** 気にすべき終了か (自分から終わった / 入れ替え / ユーザー操作は除く)。 */
        val abnormal: Boolean get() = isAbnormal(reason)

        /** 人が読む 1 行。 */
        fun line(): String = buildString {
            append(shortTime(ts))
            append("  ").append(reasonLabel(reason))
            when (reason) {
                ApplicationExitInfo.REASON_SIGNALED -> append("(").append(signalLabel(status)).append(")")
                ApplicationExitInfo.REASON_EXIT_SELF -> append("(exit=").append(status).append(")")
            }
            if (rssKb > 0) append("  rss=").append(rssKb / 1024).append("MB")
            append("  ").append(importanceLabel(importance))
            val d = description.trim()
            if (d.isNotEmpty()) append("  ").append(if (d.length > 60) d.take(59) + "…" else d)
        }

        /** ファイルへ残す 1 行 (JSONL)。 */
        fun json(): String = JSONObject().apply {
            put("ts", ts)
            put("time", isoTime(ts))
            put("reason", reasonLabel(reason))
            put("reason_code", reason)
            put("status", status)
            put("importance", importanceLabel(importance))
            put("rss_kb", rssKb)
            put("pid", pid)
            put("process", processName)
            put("description", description)
            put("abnormal", abnormal)
        }.toString()
    }

    /** OS が持っている死因を新しい順に返す。API 30 未満・取得できない場合は空。 */
    fun recent(context: Context, max: Int = MAX_RECORDS): List<Record> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, max).map { i ->
                Record(
                    ts = i.timestamp,
                    reason = i.reason,
                    status = i.status,
                    importance = i.importance,
                    rssKb = i.rss,
                    pid = i.pid,
                    processName = i.processName.orEmpty(),
                    description = i.description.orEmpty(),
                )
            }
        }.getOrElse {
            Log.w(TAG, "exit reasons unavailable: ${it.message}")
            emptyList()
        }
    }

    /**
     * 起動時に 1 回呼ぶ。死因を logcat へ出し、`~/.z2term/exits.jsonl` へ**まだ書いていない
     * ぶんだけ**足す。
     *
     * ⚠ 重複は時刻 (`ts`) で弾く。OS は同じ記録を毎回返してくるので、素直に追記すると
     * 起動のたびに同じ行が増える。
     */
    fun record(context: Context) {
        val records = recent(context)
        if (records.isEmpty()) return

        records.take(REPORT_MAX).forEach { r ->
            if (r.abnormal) Log.w(TAG, "前回の終了: ${r.line()}")
            else Log.i(TAG, "前回の終了: ${r.line()}")
        }

        runCatching {
            val f = logFile(context)
            val known = if (f.isFile) knownTimestamps(f.readLines()) else emptySet()
            val fresh = records.filter { it.ts !in known }.sortedBy { it.ts }
            if (fresh.isEmpty()) return@runCatching
            f.parentFile?.mkdirs()
            fresh.forEach { f.appendText(it.json() + "\n") }
            // 際限なく伸びないように後ろだけ残す (ここは解析対象ではなく、直近を見るためのもの)。
            val lines = f.readLines()
            if (lines.size > KEEP_LINES) {
                f.writeText(lines.takeLast(KEEP_LINES).joinToString("\n") + "\n")
            }
        }.onFailure { Log.w(TAG, "exits.jsonl 書き込み失敗: ${it.message}") }
    }

    /**
     * z2doctor に出すテキスト (1 件 1 行・新しい順)。**気にすべき終了だけ**を出す。
     * 1 件も無ければ空文字を返す (呼ぶ側が「記録なし」と出す)。
     */
    fun report(context: Context, max: Int = REPORT_MAX): String =
        recent(context).filter { it.abnormal }.take(max).joinToString("\n") { it.line() }

    /** 既存行から `ts` だけを抜く。 */
    private val TS_RE = Regex("\"ts\"\\s*:\\s*(\\d+)")

    /**
     * 既にファイルへ書いてある時刻の一覧 (重複を弾くのに使う)。
     *
     * ⚠ **JSON として解析しない。** 途中で電源が落ちれば**書きかけの行**が残りうるし、
     * 解析に失敗した 1 行のせいで他の行の重複判定まで巻き込まれてはいけない。欲しいのは
     * `ts` の値 1 つだけなので、拾えた行だけ拾う (拾えない行は「まだ書いていない」側に倒れる
     * ＝最悪でも同じ記録がもう 1 行増えるだけで、記録が消えることはない)。
     */
    internal fun knownTimestamps(lines: List<String>): Set<Long> =
        lines.mapNotNull { l -> TS_RE.find(l)?.groupValues?.get(1)?.toLongOrNull() }
            .filter { it > 0L }
            .toSet()

    /**
     * 気にすべき終了か。**自分から終わった / 入れ替え / ユーザーが止めた**は日常なので外す
     * (開発中は入れ替えが何度も入るため、これを混ぜると本当の事故が埋もれる)。
     */
    internal fun isAbnormal(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        REASON_PACKAGE_STATE_CHANGE,
        REASON_PACKAGE_UPDATED,
        REASON_FREEZER -> false
        else -> true
    }

    /** OS の理由コードを名前に (訳さない — 検索できる語のまま残すほうが役に立つ)。 */
    internal fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        REASON_FREEZER -> "FREEZER"
        REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    /**
     * 死んだ瞬間にアプリがどれだけ「大事に扱われていた」か。
     *
     * ⚠ ここが CACHED なら「畳まれた後に片付けられた」= 普通のこと。**FOREGROUND_SERVICE の
     * まま殺されていたら、常駐が守れていない**という別の話になるので必ず出す。
     */
    internal fun importanceLabel(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "CANT_SAVE_STATE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
        else -> "importance=$importance"
    }

    /** SIGNALED のときの番号を名前に (9 = SIGKILL = 外から強制終了された、が一番多い)。 */
    internal fun signalLabel(status: Int): String = when (status) {
        4 -> "SIGILL"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        15 -> "SIGTERM"
        else -> "signal=$status"
    }

    // API 31/33 で足された理由コード。古い端末から返ることは無いので数値で持つ
    // (minSdk 29 のまま、参照だけで API レベルの警告を出さないため)。
    private const val REASON_FREEZER = 14
    private const val REASON_PACKAGE_STATE_CHANGE = 15
    private const val REASON_PACKAGE_UPDATED = 16
}
