package com.zerotoship.z2term.widget

import android.content.Context
import java.io.File

/**
 * ライブ tail ウィジェット (D2) の保存データと、見せるファイルの候補集め。
 *
 * D1 ([WidgetStore]) と同じ理由で **SharedPreferences**（ウィジェットはアプリのプロセスが
 * 生きていない状態で描かれるので、同期で読める必要がある）。
 *
 * 保存するのは「どのファイルを何行出すか」だけで、ファイル本体はユーザーのもの
 * (`~/` 配下) が正本。
 */
object TailStore {

    private const val PREFS = "z2term_widget_tail"
    private const val KEY_PATH_PREFIX = "tail_path_"
    private const val KEY_LINES_PREFIX = "tail_lines_"

    /** 既定の表示行数。 */
    const val DEFAULT_LINES = 8

    /** 選べる行数。ウィジェットの高さに合わせて選ぶ。 */
    val LINE_CHOICES = listOf(4, 6, 8, 12, 16)

    /** 候補として拾う拡張子。端末で `tail` したくなるのはこの辺り。 */
    private val EXTENSIONS = setOf("log", "jsonl", "txt", "out", "err")

    /** 候補を探す深さ (`~/` からの階層)。深追いすると重くなるので浅く。 */
    private const val SCAN_DEPTH = 3

    /** 候補の上限。 */
    private const val MAX_CANDIDATES = 60

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
        val f = File(home(context), rel)
        return if (f.isFile) f else null
    }

    fun lines(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt(KEY_LINES_PREFIX + appWidgetId, DEFAULT_LINES)

    fun set(context: Context, appWidgetId: Int, relPath: String, lines: Int) {
        prefs(context).edit()
            .putString(KEY_PATH_PREFIX + appWidgetId, relPath)
            .putInt(KEY_LINES_PREFIX + appWidgetId, lines)
            .apply()
    }

    /** ウィジェットが削除されたときに設定を捨てる。 */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(KEY_PATH_PREFIX + appWidgetId)
            .remove(KEY_LINES_PREFIX + appWidgetId)
            .apply()
    }

    // --- 候補集め ---

    /**
     * `~` 配下から tail できそうなファイルを集めて、**新しい順**に返す
     * (いま動かしているものが上に来るので選びやすい)。
     *
     * 隠しディレクトリも見る (`~/.z2term/` にログが集まるため) が、深さは [SCAN_DEPTH] まで。
     * 戻り値は `~` からの相対パス。
     */
    fun candidates(context: Context): List<String> {
        val home = home(context)
        if (!home.isDirectory) return emptyList()
        val found = ArrayList<File>()
        collect(home, depth = 0, into = found)
        return found
            .sortedByDescending { it.lastModified() }
            .take(MAX_CANDIDATES)
            .map { it.relativeTo(home).path }
    }

    private fun collect(dir: File, depth: Int, into: MutableList<File>) {
        if (depth > SCAN_DEPTH || into.size >= MAX_CANDIDATES * 4) return
        val entries = dir.listFiles() ?: return
        entries.forEach { f ->
            when {
                f.isDirectory -> collect(f, depth + 1, into)
                f.isFile && f.extension.lowercase() in EXTENSIONS && f.length() > 0 -> into.add(f)
            }
        }
    }
}
