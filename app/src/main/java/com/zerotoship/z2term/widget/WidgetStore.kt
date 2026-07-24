package com.zerotoship.z2term.widget

import android.content.Context
import java.io.File

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
    private const val KEY_LAST_RUN_NAME = "last_run_name"
    private const val KEY_LAST_RUN_AT = "last_run_at"

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
        prefs(context).edit()
            .putString(KEY_MACROS_PREFIX + appWidgetId, names.take(MAX_MACROS).joinToString("\n"))
            .apply()
    }

    /** ウィジェットが削除されたときに設定を捨てる。 */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(KEY_MACROS_PREFIX + appWidgetId).apply()
    }

    // --- 直近に走らせたマクロ (全ウィジェット共通の表示) ---

    fun setLastRun(context: Context, name: String) {
        prefs(context).edit()
            .putString(KEY_LAST_RUN_NAME, name)
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .apply()
    }

    /** 直近に走らせたマクロ名と時刻 (無ければ null)。 */
    fun lastRun(context: Context): Pair<String, Long>? {
        val p = prefs(context)
        val name = p.getString(KEY_LAST_RUN_NAME, null) ?: return null
        return name to p.getLong(KEY_LAST_RUN_AT, 0L)
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
