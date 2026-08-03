package com.zerotoship.z2term.widget

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * ホーム画面ウィジェット (D1) の保存データ。
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

    /** 1 つのウィジェットに並べられるマクロの上限 (レイアウトのボタン数と一致させること)。 */
    const val MAX_MACROS = 4

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
    fun macros(context: Context, appWidgetId: Int): List<String> = resolve(
        saved = prefs(context).getString(KEY_MACROS_PREFIX + appWidgetId, null),
        available = availableMacros(context),
    )

    /**
     * [macros] の判断部分だけを切り出したもの (Android 非依存・テスト用)。
     *
     * [saved] が null (＝未設定) なら [available] の先頭 [MAX_MACROS] 件。保存済みなら、その並び順の
     * まま実在するものだけを残す (`z2-macro` で消したマクロのボタンが残らない)。空を保存した場合は
     * 空のまま — 「1 つも出さない」という選択を先頭 4 件で上書きしない。
     */
    internal fun resolve(saved: String?, available: List<String>): List<String> {
        if (saved == null) return available.take(MAX_MACROS)
        return saved.split('\n').filter { it.isNotBlank() && it in available }.take(MAX_MACROS)
    }

    /** [appWidgetId] のマクロ選択を保存する。 */
    fun setMacros(context: Context, appWidgetId: Int, names: List<String>) {
        prefs(context).edit {
            putString(KEY_MACROS_PREFIX + appWidgetId, names.take(MAX_MACROS).joinToString("\n"))
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
    fun scaled(context: Context, appWidgetId: Int, base: Int): Float =
        (base + (textSp(context, appWidgetId) - DEFAULT_TEXT_SP))
            .coerceAtLeast(6).toFloat()

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
     */
    fun setRunFinish(context: Context, name: String) {
        prefs(context).edit {
            putString(KEY_LAST_FINISH_NAME, name)
            putLong(KEY_LAST_FINISH_AT, System.currentTimeMillis())
        }
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
            p.all.keys.filter { it.startsWith(KEY_RUN_AT_PREFIX) }.forEach { remove(it) }
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
