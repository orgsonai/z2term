package com.zerotoship.z2term.tile

import android.content.Context
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.widget.WidgetStore

/**
 * クイック設定タイル (`z2-tile`) の割り当て。
 *
 * ホーム画面ウィジェット (D1) と同じく**アプリのプロセスが生きていない状態**で読まれるので、
 * DataStore (非同期) ではなく SharedPreferences を使う ([WidgetStore] と同じ理由)。
 *
 * **枠は 4 つ固定**。クイック設定タイルは manifest に 1 個ずつ `TileService` を書く必要があり、
 * 実行中に増やせない (Android の仕様)。D1 ウィジェットのボタンと同じ数に揃えて、
 * 「どこに置いても 4 つ」で覚え方を 1 つにする。
 */
object TileStore {

    /** タイルの枠数。manifest の `Z2Tile1`〜`Z2Tile4` と**必ず一致させること**。 */
    const val COUNT = 4

    private const val PREFS = "z2term_tile"
    private const val KEY_CMD_PREFIX = "cmd_"
    private const val KEY_LABEL_PREFIX = "label_"

    /** タイルに出す表示名の上限。クイック設定は狭いので、長い名前は切り詰めて出す。 */
    const val MAX_LABEL_CHARS = 12

    /**
     * 1 枠の割り当て。[command] は**マクロのファイル名** (`backup.sh`) か
     * **そのまま走らせるコマンド** (`z2-screen keepon 1h`) のどちらか ([scriptFor] 参照)。
     */
    data class Slot(val n: Int, val command: String, val label: String)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [n] (1〜[COUNT]) の割り当て。未設定なら null。 */
    fun get(context: Context, n: Int): Slot? {
        if (n !in 1..COUNT) return null
        val cmd = prefs(context).getString(KEY_CMD_PREFIX + n, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val label = prefs(context).getString(KEY_LABEL_PREFIX + n, null).orEmpty()
        return Slot(n, cmd, labelFor(cmd, label))
    }

    /** 全枠 (未設定の枠は含まない)。 */
    fun all(context: Context): List<Slot> = (1..COUNT).mapNotNull { get(context, it) }

    /**
     * [n] へ割り当てる。[label] が空なら [labelFor] が [command] から作る。
     * @throws IllegalArgumentException 枠番号が範囲外・コマンドが空
     */
    fun set(context: Context, n: Int, command: String, label: String = "") {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        require(command.isNotBlank()) { "z2-tile: 割り当てるコマンドがありません" }
        prefs(context).edit()
            .putString(KEY_CMD_PREFIX + n, command)
            .putString(KEY_LABEL_PREFIX + n, label)
            .apply()
    }

    /** [n] の割り当てを消す。 */
    fun clear(context: Context, n: Int) {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        prefs(context).edit()
            .remove(KEY_CMD_PREFIX + n)
            .remove(KEY_LABEL_PREFIX + n)
            .apply()
    }

    /**
     * タイルに出す名前を決める (Android 非依存・テスト用)。
     *
     * 明示的な名前があればそれ。無ければ、マクロなら拡張子を落とした名前 (`backup.sh` → `backup`)、
     * コマンドなら**先頭の語**(`z2-screen keepon 1h` → `z2-screen`)。**タイルは名前が機能そのもの**
     * なので、コマンド全文を出して切れるより短い手掛かりを出す方が使える。
     */
    internal fun labelFor(command: String, explicit: String): String {
        val raw = explicit.ifBlank {
            if (command.endsWith(".sh")) command.removeSuffix(".sh")
            else command.trim().substringBefore(' ')
        }
        return if (raw.length > MAX_LABEL_CHARS) raw.take(MAX_LABEL_CHARS - 1) + "…" else raw
    }

    /**
     * [command] を実行するスクリプトを組む。
     *
     * **導入済みマクロのファイル名と一致すればマクロとして**、そうでなければ**コマンドとして**
     * 走らせる。⚠ ここで種別を選ばせない (`--macro` のようなフラグを作らない) のは、割り当てる側が
     * 「これはマクロか、コマンドか」を意識せずに済ませるため。`~/.z2term/macros/` にある名前を
     * 打てばマクロが走り、それ以外は端末に打ったのと同じになる。
     *
     * `Z2_TILE` に枠番号が入るので、同じマクロを複数の枠に置いて中で分岐することもできる。
     */
    fun scriptFor(context: Context, slot: Slot): String {
        val n = HeadlessRun.shSingleQuote(slot.n.toString())
        val prefix = "export Z2_TILE=$n; cd \"\$HOME\" 2>/dev/null; "
        return if (slot.command in WidgetStore.availableMacros(context)) {
            val q = HeadlessRun.shSingleQuote(slot.command)
            prefix + "export Z2_TILE_MACRO=$q; sh \"\$HOME/.z2term/macros/\"$q"
        } else {
            prefix + slot.command
        }
    }

    /** [HeadlessRun] の実行キー (ウィジェットの `widget-<名前>` と衝突させない)。 */
    fun runKey(n: Int): String = "tile-$n"

    /** 実行ログの置き場 (`~/.z2term/tile/run.log`)。ウィジェットと分けて混ざらないようにする。 */
    const val LOG_REL = ".z2term/tile/run.log"
}
