package com.zerotoship.z2term.widget

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * ライブ tail ウィジェット (D2) の保存データと、ファイルを選ぶためのディレクトリ走査。
 *
 * D1 ([WidgetStore]) と同じ理由で **SharedPreferences**（ウィジェットはアプリのプロセスが
 * 生きていない状態で描かれるので、同期で読める必要がある）。
 *
 * 保存するのは「どのファイルを見るか」だけ。**行数は保存しない** — ウィジェットの高さから
 * 毎回決める（固定にすると縦に伸ばしたときに下が余る。0.8.220 で自動化）。
 */
object TailStore {

    private const val PREFS = "z2term_widget_tail"
    private const val KEY_PATH_PREFIX = "tail_path_"
    private const val KEY_MODE_PREFIX = "tail_mode_"
    private const val KEY_TEXT_SP_PREFIX = "tail_text_sp_"

    /**
     * ファイルのどちら側を見るか。
     *
     * [TAIL] は末尾（増えていくログを追う）。[HEAD] は先頭（書き出しが 1 回きりの
     * レポートや、設定ファイルの頭を確認する用途）。
     */
    enum class Mode {
        TAIL,
        HEAD;

        companion object {
            fun from(name: String?): Mode = entries.firstOrNull { it.name == name } ?: TAIL
        }
    }

    /** 高さが取れなかったときの行数。 */
    const val DEFAULT_LINES = 8

    /** 自動計算した行数の下限 / 上限。 */
    const val MIN_LINES = 2
    const val MAX_LINES = 30

    /**
     * 本文の文字サイズ (sp)。既定は 10 = 0.8.254 までの固定値なので、**触らなければ今までと同じ**。
     *
     * ⚠ 行数は高さから自動計算しているので、ここを変えると**入る行数も一緒に変わる**
     * (大きくすれば少なく、小さくすれば多く表示される)。行数を別に持たせないのは、
     * 「文字の大きさ」と「入る行数」は同じことを 2 通りで言っているだけで、
     * 両方いじれると矛盾した組み合わせを作れてしまうため。
     */
    const val DEFAULT_TEXT_SP = 10
    const val MIN_TEXT_SP = 8
    const val MAX_TEXT_SP = 20

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 端末から見た `~`（= `filesDir/shared_home`）。 */
    fun home(context: Context): File =
        File(context.applicationContext.filesDir, "shared_home")

    // --- ウィジェットごとの設定 ---

    /** [appWidgetId] が見るファイルの `~` からの相対パス (未設定なら null)。 */
    fun path(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(KEY_PATH_PREFIX + appWidgetId, null)

    /** [appWidgetId] が見るファイルの実体 (未設定・実在しないなら null)。 */
    fun file(context: Context, appWidgetId: Int): File? {
        val rel = path(context, appWidgetId) ?: return null
        val f = resolve(context, rel) ?: return null
        return if (f.isFile) f else null
    }

    fun set(context: Context, appWidgetId: Int, relPath: String) {
        prefs(context).edit { putString(KEY_PATH_PREFIX + appWidgetId, relPath) }
    }

    /** [appWidgetId] がファイルのどちら側を見るか (未設定なら [Mode.TAIL])。 */
    fun mode(context: Context, appWidgetId: Int): Mode =
        Mode.from(prefs(context).getString(KEY_MODE_PREFIX + appWidgetId, null))

    fun setMode(context: Context, appWidgetId: Int, mode: Mode) {
        prefs(context).edit { putString(KEY_MODE_PREFIX + appWidgetId, mode.name) }
    }

    /** [appWidgetId] の本文の文字サイズ (sp)。未設定なら [DEFAULT_TEXT_SP]。 */
    fun textSp(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt(KEY_TEXT_SP_PREFIX + appWidgetId, DEFAULT_TEXT_SP)
            .coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)

    fun setTextSp(context: Context, appWidgetId: Int, sp: Int) {
        prefs(context).edit {
            putInt(KEY_TEXT_SP_PREFIX + appWidgetId, sp.coerceIn(MIN_TEXT_SP, MAX_TEXT_SP))
        }
    }

    /** ウィジェットが削除されたときに設定を捨てる。 */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit {
            remove(KEY_PATH_PREFIX + appWidgetId)
            remove(KEY_MODE_PREFIX + appWidgetId)
            remove(KEY_TEXT_SP_PREFIX + appWidgetId)
        }
    }

    // --- パスの解決 ---

    /**
     * ユーザーが打った / 選んだパスを実ファイルに直す。**`~` の外は見せない。**
     *
     * 受け付ける書き方: `~/.z2term/events.jsonl` / `.z2term/events.jsonl` /
     * `/root/.z2term/events.jsonl`（端末から見た絶対パス）。
     * `..` で `~` の外へ出ようとしたものは null（アプリの内部データを覗ける口にしない）。
     */
    fun resolve(context: Context, input: String): File? {
        val home = home(context)
        var s = input.trim()
        if (s.isEmpty()) return null
        s = s.removePrefix("~/").removePrefix("~")
        // 端末側の HOME は /root。そこからの絶対パスで打たれても受ける。
        s = s.removePrefix("/root/").removePrefix("/root")
        s = s.trimStart('/')
        if (s.isEmpty()) return null
        val f = File(home, s)
        val canonicalHome = runCatching { home.canonicalPath }.getOrNull() ?: return null
        val canonical = runCatching { f.canonicalPath }.getOrNull() ?: return null
        if (canonical != canonicalHome && !canonical.startsWith("$canonicalHome/")) return null
        return f
    }

    /** [file] を `~` からの相対パスにする (`~` の外なら null)。 */
    fun relativize(context: Context, file: File): String? {
        val home = runCatching { home(context).canonicalPath }.getOrNull() ?: return null
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return null
        if (!path.startsWith("$home/")) return null
        return path.removePrefix("$home/")
    }

    // --- フォルダを辿って選ぶ ---

    /** 一覧の 1 行。[isDir] が true ならタップで中へ入る。 */
    data class Entry(val name: String, val relPath: String, val isDir: Boolean, val size: Long, val modified: Long)

    /**
     * [relDir]（`~` からの相対。空なら `~` 自身）の中身を、**フォルダが先・名前順**で返す。
     *
     * 拡張子では絞らない。**候補を機械的に集めて 60 件並べると選べない**（実機フィードバック）ので、
     * ユーザーが自分で辿る前提にした。数が多いディレクトリでも 1 階層ぶんしか出ないので迷わない。
     */
    fun list(context: Context, relDir: String): List<Entry> {
        val dir = resolve(context, relDir.ifEmpty { "." }) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        return entries
            .mapNotNull { f ->
                val rel = relativize(context, f) ?: return@mapNotNull null
                Entry(f.name, rel, f.isDirectory, f.length(), f.lastModified())
            }
            .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** [relDir] の 1 つ上の相対パス (すでに `~` なら null)。 */
    fun parentOf(relDir: String): String? {
        if (relDir.isEmpty()) return null
        val i = relDir.trimEnd('/').lastIndexOf('/')
        return if (i < 0) "" else relDir.substring(0, i)
    }
}
