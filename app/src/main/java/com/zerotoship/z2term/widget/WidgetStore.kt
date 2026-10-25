package com.zerotoship.z2term.widget

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * ホーム画面のマクロウィジェット (マス目型 [StatusWidgetProvider] / 一覧型 [MacroListWidgetProvider])
 * の保存データ。
 *
 * ウィジェットは**アプリのプロセスが生きていない状態**で描画・タップされるので、DataStore
 * (非同期・コルーチン前提) ではなく SharedPreferences を使う。ブロードキャスト受信中に
 * 同期で読めることが要件。
 *
 * 保存するのは「そのウィジェットに並べるマクロ」だけで、マクロ本体は
 * `~/.z2term/macros/` 配下の `.sh` (= ユーザーのファイル) が正本。ウィジェットは参照するだけなので、
 * 端末側で `z2-macro install` して増やせば設定画面にそのまま出てくる。
 */
object WidgetStore {

    private const val PREFS = "z2term_widget"
    private const val KEY_MACROS_PREFIX = "macros_"
    private const val KEY_TEXT_SP_PREFIX = "status_text_sp_"
    private const val KEY_LAST_RUN_NAME = "last_run_name"
    private const val KEY_LAST_RUN_AT = "last_run_at"
    private const val KEY_LAST_FINISH_NAME = "last_finish_name"
    private const val KEY_LAST_FINISH_AT = "last_finish_at"

    /** マクロごとの「最後に開始した時刻」。キーは `run_at_<ファイル名>`。 */
    private const val KEY_RUN_AT_PREFIX = "run_at_"

    /** マクロごとの「最後に終わった時刻」。キーは `fin_at_<ファイル名>`。 */
    private const val KEY_FIN_AT_PREFIX = "fin_at_"

    /** マクロごとの終了コード (取れなかったら書かない)。キーは `rc_<ファイル名>`。 */
    private const val KEY_RC_PREFIX = "rc_"

    /** ウィジェットから止めた印。キーは `stop_<ファイル名>`。 */
    private const val KEY_STOP_PREFIX = "stop_"

    /** マス目型に並べられるマクロの上限 (レイアウトのマス数と一致させること)。 */
    const val MAX_MACROS = 4

    /**
     * 一覧型に並べられるマクロの上限。一覧は中でスクロールするので数に見た目の制約は無いが、
     * 設定画面で選びきれる数・描画のたびに読む説明の数を抑えるために区切る。
     */
    const val MAX_LIST_MACROS = 30

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- ウィジェットごとのマクロ選択 ---

    /**
     * [appWidgetId] に割り当てられたマクロ名 (`backup.sh` のようなファイル名) を返す。
     *
     * 未設定なら**マクロディレクトリの先頭 [MAX_MACROS] 件**を返す。設定を省略して置いた
     * (API 31+ の `configuration_optional`) ウィジェットでも空にならず、そのまま使えるようにするため。
     * 実在しなくなったマクロは落とす。
     */
    fun macros(context: Context, appWidgetId: Int, max: Int = MAX_MACROS): List<String> = resolve(
        saved = prefs(context).getString(KEY_MACROS_PREFIX + appWidgetId, null),
        available = availableMacros(context),
        max = max,
    )

    /**
     * [macros] の判断部分だけを切り出したもの (Android 非依存・テスト用)。
     *
     * [saved] が null (＝未設定) なら [available] の先頭 [max] 件。保存済みなら、その並び順の
     * まま実在するものだけを残す (`z2-macro` で消したマクロのボタンが残らない)。空を保存した場合は
     * 空のまま — 「1 つも出さない」という選択を先頭 4 件で上書きしない。
     */
    internal fun resolve(saved: String?, available: List<String>, max: Int = MAX_MACROS): List<String> {
        if (saved == null) return available.take(max)
        return saved.split('\n').filter { it.isNotBlank() && it in available }.take(max)
    }

    /** [appWidgetId] のマクロ選択を保存する。 */
    fun setMacros(context: Context, appWidgetId: Int, names: List<String>, max: Int = MAX_MACROS) {
        prefs(context).edit {
            putString(KEY_MACROS_PREFIX + appWidgetId, names.take(max).joinToString("\n"))
        }
    }

    /** ウィジェットが削除されたときに設定を捨てる。 */
    /**
     * 本文の文字サイズ (sp)。既定 [DEFAULT_TEXT_SP] は 0.8.254 までのレイアウト固定値なので、
     * **触らなければ今までと同じ**。行 (ssh / 状態 / フッター) の相対的な大小は保ったまま、
     * まとめて同じ差分だけ動かす ([scaled] 参照) — 1 行だけ大きくすると釣り合いが崩れるため。
     */
    const val DEFAULT_TEXT_SP = 11
    const val MIN_TEXT_SP = 9
    const val MAX_TEXT_SP = 20

    fun textSp(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt(KEY_TEXT_SP_PREFIX + appWidgetId, DEFAULT_TEXT_SP)
            .coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)

    fun setTextSp(context: Context, appWidgetId: Int, sp: Int) {
        prefs(context).edit {
            putInt(KEY_TEXT_SP_PREFIX + appWidgetId, sp.coerceIn(MIN_TEXT_SP, MAX_TEXT_SP))
        }
    }

    /**
     * レイアウトで [base] sp と書かれている行の、いまの文字サイズ。
     *
     * 既定からの**差分**を足す (倍率ではない)。倍率だと小さい行だけ潰れたり、
     * 大きい行だけ極端に伸びたりして、行同士の釣り合いが崩れる。
     */
    fun scaled(context: Context, appWidgetId: Int, base: Float): Float =
        scaledBy(base, textSp(context, appWidgetId))

    /** [scaled] の計算部分 (Android 非依存・テスト用)。 */
    internal fun scaledBy(base: Float, textSp: Int): Float =
        (base + (textSp - DEFAULT_TEXT_SP)).coerceAtLeast(6f)

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit {
            remove(KEY_MACROS_PREFIX + appWidgetId)
            remove(KEY_TEXT_SP_PREFIX + appWidgetId)
        }
    }

    // --- 実行の記録 ---

    /**
     * [name] を開始したことを記録する。**マクロごとに**持つのが要点で、
     * 全体で 1 件しか覚えていなかった 0.8.215 までは**複数走らせるとどれがいつのものか
     * 分からなかった** (実機フィードバック 2026-07-24)。ボタンの 2 行目に出す。
     */
    fun setRunStart(context: Context, name: String) {
        prefs(context).edit {
            putLong(KEY_RUN_AT_PREFIX + name, System.currentTimeMillis())
            // 前回の結果を持ち越さない (開始したのに「失敗」や「停止」と出さない)。
            remove(KEY_FIN_AT_PREFIX + name)
            remove(KEY_RC_PREFIX + name)
            remove(KEY_STOP_PREFIX + name)
            putString(KEY_LAST_RUN_NAME, name)
            putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
        }
    }

    /** [name] を最後に開始した時刻 (一度も無ければ 0)。 */
    fun runStartAt(context: Context, name: String): Long =
        prefs(context).getLong(KEY_RUN_AT_PREFIX + name, 0L)

    /**
     * [name] が終わったことを記録する。**終了を表示するため**に要る。
     * すぐ終わるマクロだと `■` が一瞬で消えるので「勝手に停止された」ように見え、
     * 正常終了と停止の区別が付かなかった (同上のフィードバック)。
     *
     * [exitCode] はマクロの終了コード。取れなかったとき (止めた・途中で殺された) は null。
     */
    fun setRunFinish(context: Context, name: String, exitCode: Int? = null) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            putString(KEY_LAST_FINISH_NAME, name)
            putLong(KEY_LAST_FINISH_AT, now)
            putLong(KEY_FIN_AT_PREFIX + name, now)
            if (exitCode != null) putInt(KEY_RC_PREFIX + name, exitCode) else remove(KEY_RC_PREFIX + name)
        }
    }

    /**
     * [name] をウィジェットから止めたことを記録する。止める**前に**呼ぶ — 終了の通知
     * ([setRunFinish]) が先に来ても「停止」と出せるようにするため。
     */
    fun markStopped(context: Context, name: String) {
        prefs(context).edit { putBoolean(KEY_STOP_PREFIX + name, true) }
    }

    /** [name] のいまの状態 (今日の記録だけを見る。[MacroStatus.of] 参照)。 */
    fun status(
        context: Context,
        name: String,
        running: Boolean,
        now: Long = System.currentTimeMillis(),
    ): MacroStatus {
        val p = prefs(context)
        return MacroStatus.of(
            running = running,
            startAt = p.getLong(KEY_RUN_AT_PREFIX + name, 0L),
            finishAt = p.getLong(KEY_FIN_AT_PREFIX + name, 0L),
            exitCode = if (p.contains(KEY_RC_PREFIX + name)) p.getInt(KEY_RC_PREFIX + name, 0) else null,
            stopped = p.getBoolean(KEY_STOP_PREFIX + name, false),
            now = now,
        )
    }

    /** 直近に終わったマクロ名と時刻 (無ければ null)。 */
    fun lastFinish(context: Context): Pair<String, Long>? {
        val p = prefs(context)
        val name = p.getString(KEY_LAST_FINISH_NAME, null) ?: return null
        return name to p.getLong(KEY_LAST_FINISH_AT, 0L)
    }

    /** 直近に走らせたマクロ名と時刻 (無ければ null)。 */
    fun lastRun(context: Context): Pair<String, Long>? {
        val p = prefs(context)
        val name = p.getString(KEY_LAST_RUN_NAME, null) ?: return null
        return name to p.getLong(KEY_LAST_RUN_AT, 0L)
    }

    /**
     * 実行の記録をすべて捨てる (ボタンの `✓` と時刻・フッターの「最後に終わった」が消える)。
     *
     * `✓` は「一度でも実行した」印なので、放っておくと**永久に残る**。ウィジェットを置き直しても
     * ([clear] はマクロ選択しか消さないので) 消えず、アプリのデータ削除しか手が無かった
     * (実機フィードバック 2026-07-25)。設定画面のリセットボタンからここを呼ぶ。
     */
    fun clearRunHistory(context: Context) {
        val p = prefs(context)
        p.edit {
            p.all.keys.filter { key ->
                PER_MACRO_PREFIXES.any { key.startsWith(it) }
            }.forEach { remove(it) }
            remove(KEY_LAST_RUN_NAME)
            remove(KEY_LAST_RUN_AT)
            remove(KEY_LAST_FINISH_NAME)
            remove(KEY_LAST_FINISH_AT)
        }
    }

    /**
     * [a] と [b] が同じ日か (端末のタイムゾーン)。どちらかが 0 以下なら false。
     *
     * `✓` と時刻表示を**当日限り**にするために使う。ボタンの時刻は `HH:mm` しか出せないので、
     * 日付をまたいだ記録を残すと「その 07:12 は今日なのか 3 日前なのか」が分からなくなる。
     */
    internal fun isSameDay(a: Long, b: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (a <= 0L || b <= 0L) return false
        val ca = Calendar.getInstance(zone).apply { timeInMillis = a }
        val cb = Calendar.getInstance(zone).apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * [name] を**今日**開始した時刻 (今日でない・一度も無いなら 0)。
     *
     * 日付が変われば自動で 0 に戻るので、`✓` は翌日には消えて無印へ戻る。
     */
    fun runStartAtToday(context: Context, name: String, now: Long = System.currentTimeMillis()): Long {
        val at = runStartAt(context, name)
        return if (isSameDay(at, now)) at else 0L
    }

    /** マクロごとに持つ実行記録のキーの頭 ([clearRunHistory] でまとめて消す)。 */
    private val PER_MACRO_PREFIXES =
        listOf(KEY_RUN_AT_PREFIX, KEY_FIN_AT_PREFIX, KEY_RC_PREFIX, KEY_STOP_PREFIX)

    // --- マクロディレクトリ ---

    /** マクロディレクトリ (`filesDir/shared_home/.z2term/macros` = 端末からは `~/.z2term/macros`)。 */
    fun macroDir(context: Context): File =
        File(File(context.applicationContext.filesDir, "shared_home"), ".z2term/macros")

    /** 導入済みマクロのファイル名を名前順で返す (`z2-macro install` で増える)。 */
    fun availableMacros(context: Context): List<String> =
        macroDir(context).listFiles { f -> f.isFile && f.name.endsWith(".sh") }
            ?.map { it.name }?.sorted() ?: emptyList()

    /** ボタンに出す短い表示名 (`backup.sh` → `backup`)。 */
    fun label(fileName: String): String = fileName.removeSuffix(".sh")

    /**
     * 設定画面に出す 1 行説明を [fileName] のスクリプトから読む (取れなければ空)。
     *
     * ファイル名だけでは何のマクロか分からない、という実機フィードバック (2026-07-24) を受けて追加。
     * マクロは**先頭にコメントで説明を書く**のが `z2-macro install` で入る同梱マクロの書き方なので、
     * それをそのまま拾う。読むのは先頭 [DESC_SCAN_LINES] 行だけ (全文読み込みを避ける)。
     */
    fun description(context: Context, fileName: String): String = runCatching {
        File(macroDir(context), fileName).useLines { lines ->
            describe(fileName, lines.take(DESC_SCAN_LINES).toList())
        }
    }.getOrDefault("")

    /** [description] の判断部分 (Android 非依存・テスト用)。 */
    internal fun describe(fileName: String, lines: List<String>): String {
        val base = label(fileName)
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#!")) continue          // シェバン
            if (line.isEmpty()) continue                 // シェバンと説明の間の空行
            if (!line.startsWith("#")) break             // コードが始まったら諦める
            var text = line.removePrefix("#").trim()
            if (text.isEmpty()) continue
            // 「# ~/.z2term/macros/away-timer.sh」のような自己言及の行は説明ではない。
            if (text.endsWith(".sh") && text.contains(base)) continue
            // 「# battery-alert.sh — 電池が…」の頭のファイル名を落とす (区切りは — / – / -)。
            for (sep in DESC_SEPARATORS) {
                val i = text.indexOf(sep)
                if (i > 0 && text.take(i).trim().removeSuffix(".sh") == base) {
                    text = text.substring(i + sep.length).trim()
                    break
                }
            }
            if (text.isEmpty()) continue
            return if (text.length > DESC_MAX_CHARS) text.take(DESC_MAX_CHARS - 1) + "…" else text
        }
        return ""
    }

    /** 説明を探すために読む行数。 */
    private const val DESC_SCAN_LINES = 8

    /** 説明の最大文字数 (設定画面の 1 行に収める)。 */
    private const val DESC_MAX_CHARS = 60

    /** 「ファイル名 <区切り> 説明」の区切りとして認める文字。 */
    private val DESC_SEPARATORS = listOf("—", "–", " - ", ":", "：")
}

/**
 * マクロ 1 つの、ウィジェットに出す状態。
 *
 * 0.8.215〜1.0.54 は「実行中 / 今日走った (✓) / まだ」の 3 つで、**失敗しても ✓** が付いていた。
 * 終了コードと「止めた」印を持つようにして、終わり方を 3 通りに分ける。
 */
data class MacroStatus(
    val kind: Kind,
    /** 今日開始した時刻 (今日まだなら 0)。 */
    val startAt: Long,
    /** 今日終わった時刻 (終わっていなければ 0)。 */
    val finishAt: Long,
    /** [Kind.FAILED] のときの終了コード。 */
    val exitCode: Int?,
) {
    enum class Kind {
        /** 今日はまだ走らせていない。 */
        IDLE,
        /** いま動いている (タップで止める)。 */
        RUNNING,
        /** 終了コード 0 で終わった。 */
        DONE,
        /** 0 以外で終わった。 */
        FAILED,
        /** ウィジェットから止めた / 終わり方が分からない (アプリごと落ちた等)。 */
        STOPPED,
    }

    companion object {
        /**
         * 記録から状態を決める (Android 非依存・`WidgetStoreTest` が押さえる)。
         *
         * **当日限り**は従来どおり: 開始が今日でなければ、何が残っていても [Kind.IDLE]。
         * `HH:mm` しか出せない以上、昨日の結果を今日の印として出すと読み違える。
         *
         * 開始したのに終わりの記録が無く、いまも動いていないもの (アプリのプロセスごと
         * 落ちた等) は [Kind.STOPPED]。0.8.224 以降は「✓」が付いていたが、正常に終わったとは
         * 言えないので完了扱いにしない。
         */
        fun of(
            running: Boolean,
            startAt: Long,
            finishAt: Long,
            exitCode: Int?,
            stopped: Boolean,
            now: Long,
            zone: TimeZone = TimeZone.getDefault(),
        ): MacroStatus {
            val start = if (WidgetStore.isSameDay(startAt, now, zone)) startAt else 0L
            if (running) return MacroStatus(Kind.RUNNING, start, 0L, null)
            if (start == 0L) return MacroStatus(Kind.IDLE, 0L, 0L, null)
            val fin = if (finishAt >= start) finishAt else 0L
            return when {
                stopped || fin == 0L || exitCode == null -> MacroStatus(Kind.STOPPED, start, fin, null)
                exitCode == 0 -> MacroStatus(Kind.DONE, start, fin, 0)
                else -> MacroStatus(Kind.FAILED, start, fin, exitCode)
            }
        }
    }
}
