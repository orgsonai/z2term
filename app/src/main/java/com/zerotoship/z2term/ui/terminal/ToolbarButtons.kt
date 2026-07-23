package com.zerotoship.z2term.ui.terminal

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * ツールバー (端末 / GUI の上部バー) に置けるボタン 1 種類の定義。
 *
 * @param id      並び順 ([com.zerotoship.z2term.settings.AppSettings.Data.toolbarOrder]) と
 *                非表示指定 (同 `toolbarHidden`) の保存キー。**改名しないこと** (保存値が孤児になる)。
 * @param icon    設定画面のプレビューに出す代表アイコン。状態でアイコンが変わるボタン
 *                (🔅/💡・🔓/🔒) は OFF 側を代表とする。
 * @param labelRes ボタンの説明 (ツールバー長押しのポップアップと設定画面で共用)。
 * @param canHide  false のボタンは設定画面から隠せない。⚙ 設定を隠すと設定画面に戻れなくなるため。
 * @param terminalOnly 端末タブにしか無いボタン (GUI タブでは一覧に出さない)。
 */
data class ToolbarButtonSpec(
    val id: String,
    val icon: String,
    @param:StringRes val labelRes: Int,
    val canHide: Boolean = true,
    val terminalOnly: Boolean = false
)

/**
 * ツールバーのボタン一覧と、非表示指定 (カンマ区切り id) の読み書き。
 *
 * ボタンの実体 (アイコン・動作・状態) は [TerminalScreen] 側の TopBar / GuiTopBar が組み立てる。
 * ここが持つのは**設定画面と表示側で共有する「どんなボタンが在るか」**だけ。
 *
 * ⚙ 設定は [ToolbarButtonSpec.canHide] = false かつツールバーの**右端固定**で、
 * 並べ替えの対象にもしない (要望)。他のボタンをどう並べ替えても設定の位置が動かないので、
 * 「設定はいつもここ」が崩れない。
 */
object ToolbarButtons {
    const val PASTE = "paste"
    const val SNIPPETS = "snippets"
    const val SCREEN_ON = "screen_on"
    const val KEEP_ALIVE = "keep_alive"
    const val SEARCH = "search"
    const val KEYBOARD = "keyboard"
    const val LOG = "log"
    const val SETTINGS = "settings"

    /** 設定画面に並べる既定順 (= ツールバーの既定の並び)。 */
    val CATALOG: List<ToolbarButtonSpec> = listOf(
        ToolbarButtonSpec(PASTE, "📋", R.string.tb_paste),
        ToolbarButtonSpec(SNIPPETS, "📜", R.string.tb_snippets),
        ToolbarButtonSpec(SCREEN_ON, "🔅", R.string.tb_screen_on),
        ToolbarButtonSpec(KEEP_ALIVE, "🔓", R.string.tb_keep_alive),
        ToolbarButtonSpec(SEARCH, "🔍", R.string.tb_search, terminalOnly = true),
        ToolbarButtonSpec(KEYBOARD, "⌨", R.string.tb_keyboard),
        ToolbarButtonSpec(LOG, "⏺", R.string.tb_log, terminalOnly = true),
        ToolbarButtonSpec(SETTINGS, "⚙", R.string.tb_settings, canHide = false)
    )

    /** 非表示指定 (カンマ区切り) を集合にする。未知 id はそのまま残す (将来版で復活させるため)。 */
    fun parseHidden(csv: String): Set<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** [id] を隠す / 出すを切り替えた新しいカンマ区切り文字列を返す。隠せないボタンは無視する。 */
    fun toggleHidden(csv: String, id: String): String {
        if (CATALOG.any { it.id == id && !it.canHide }) return csv
        val cur = parseHidden(csv).toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        // 保存順はカタログ順に正規化する (差分が読みやすい)。
        return CATALOG.map { it.id }.filter { it in cur }.joinToString(",")
    }
}
