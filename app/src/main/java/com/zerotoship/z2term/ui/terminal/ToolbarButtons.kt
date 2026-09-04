package com.zerotoship.z2term.ui.terminal

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * ツールバー (端末 / GUI の上部バー) に置けるボタン 1 種類の定義。
 *
 * @param id      並び順 ([com.zerotoship.z2term.settings.AppSettings.Data.toolbarOrder]) と
 *                非表示指定 (同 `toolbarHidden`) の保存キー。**改名しないこと** (保存値が孤児になる)。
 * @param icon    設定画面のプレビューに出す代表アイコン。状態でアイコンが変わるボタン
 *                (🔅/💡・🔓/🔒・⚪/🔴) は OFF 側を代表とする。
 *                **ツールバーが実際に描く字を必ずそのまま置く**こと。別の字を置くと、設定画面
 *                だけ字形が違って (色付き絵文字の列に 1 つだけ細い記号が混じる) 揃わなくなる。
 * @param labelRes ボタンの説明 (ツールバー長押しのポップアップと設定画面で共用)。
 * @param canHide  false のボタンは設定画面から隠せない。⚙ 設定を隠すと設定画面に戻れなくなるため。
 * @param terminalOnly 端末タブにしか無いボタン (GUI タブでは一覧に出さない)。
 * @param guiOnly GUI タブにしか無いボタン (端末タブでは一覧に出さない)。
 */
data class ToolbarButtonSpec(
    val id: String,
    val icon: String,
    @param:StringRes val labelRes: Int,
    val canHide: Boolean = true,
    val terminalOnly: Boolean = false,
    val guiOnly: Boolean = false
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
    const val APPS = "apps"
    const val SCREEN_ON = "screen_on"
    const val KEEP_ALIVE = "keep_alive"
    const val SEARCH = "search"
    const val KEYBOARD = "keyboard"
    const val LOG = "log"
    const val POINTER_MODE = "pointer_mode"
    const val CLIPBOARD_FILE = "clipboard_file"
    const val SETTINGS = "settings"

    /** 設定画面に並べる既定順 (= ツールバーの既定の並び)。 */
    val CATALOG: List<ToolbarButtonSpec> = listOf(
        ToolbarButtonSpec(PASTE, "📋", R.string.tb_paste),
        ToolbarButtonSpec(SNIPPETS, "📜", R.string.tb_snippets),
        // ☰ は GUI タブだけ。入っている GUI アプリの一覧を出して、選んだものを起動する (0.8.499)。
        // GUI の中にアプリを起こす入口が「デスクトップの右クリック」しか無く、指では出しにくい
        // ので、常設のボタンとして置く (利用者の要望「スタートボタンが欲しい」)。
        ToolbarButtonSpec(APPS, "☰", R.string.tb_apps, guiOnly = true),
        // 🖱 は GUI タブだけ。カーソルの相対/絶対を切り替える (0.8.431)。
        // 以前は 📜 のダブルタップに隠れていて、**画面のどこにも出ていない**ため誰も辿り着けなかった。
        ToolbarButtonSpec(POINTER_MODE, "🖱", R.string.tb_pointer_mode, guiOnly = true),
        ToolbarButtonSpec(CLIPBOARD_FILE, "📎", R.string.tb_clipboard_file, guiOnly = true),
        ToolbarButtonSpec(SCREEN_ON, "🔅", R.string.tb_screen_on),
        ToolbarButtonSpec(KEEP_ALIVE, "🔓", R.string.tb_keep_alive),
        ToolbarButtonSpec(SEARCH, "🔍", R.string.tb_search, terminalOnly = true),
        ToolbarButtonSpec(KEYBOARD, "⌨", R.string.tb_keyboard),
        ToolbarButtonSpec(LOG, "⚪", R.string.tb_log, terminalOnly = true),
        ToolbarButtonSpec(SETTINGS, "⚙", R.string.tb_settings, canHide = false)
    )

    /** 非表示指定 (カンマ区切り) を集合にする。未知 id はそのまま残す (将来版で復活させるため)。 */
    fun parseHidden(csv: String): Set<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** カンマ区切りの並び順を id のリストにする (空要素を落として trim)。 */
    fun parseOrder(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 保存済み並び [saved] と、いま表示すべき [present] をマージした表示順を返す。
     * 保存順のうち present に在るものを優先し、保存に無い (新しく増えた) ボタンを present の
     * 既定順で末尾に補う。ボタンの追加・削除があっても並びが壊れない。
     *
     * **[saved] に同じ id が二重に入っていても 1 つに畳む。** 畳まないと同じボタンが 2 個描かれ、
     * 描画側の `key(id)` が重複して並べ替えの状態まで壊れる (0.8.212 で修正した不具合)。
     * 壊れた保存値は一度書かれると残り続けるので、読む側でも必ず正規化する。
     */
    fun mergeOrder(saved: List<String>, present: List<String>): List<String> {
        val kept = saved.distinct().filter { it in present }
        return kept + present.filter { it !in kept }
    }

    /**
     * 並べ替えを確定したときに**保存する**並び順を組み立てる。
     *
     * 保存値は「隠しているボタンも含めた全体」にする。表示中のボタンだけを保存すると隠した id が
     * 消え、出し直したときに末尾へ飛んでしまうため。全体の並びのうち**表示されている位置だけ**を、
     * いまの表示順 [shownOrder] で埋め直す。
     *
     * @param savedCsv   これまでの保存値。
     * @param allIds     このツールバーが持つ全ボタン id (隠しているものも含む)。
     * @param hiddenIds  非表示指定。
     * @param shownOrder 画面に出ている順の id。
     *
     * 戻り値は **[allIds] がちょうど 1 回ずつ現れる**ことを保証する。[shownOrder] が古くて
     * 隠し済みの id を含んでいても (隠す/出すの切替直後にドラッグを確定した場合に起きる)、
     * 同じ id が 2 か所に入った保存値を書かない — これが「ボタンが二重に出る」原因だった。
     */
    fun normalizeOrder(
        savedCsv: String,
        allIds: List<String>,
        hiddenIds: Set<String>,
        shownOrder: List<String>,
    ): String {
        val base = mergeOrder(parseOrder(savedCsv), allIds).toMutableList()
        val queue = shownOrder.distinct().filter { it in allIds && it !in hiddenIds }
        var k = 0
        for (i in base.indices) {
            if (base[i] !in hiddenIds && k < queue.size) base[i] = queue[k++]
        }
        // 位置埋めの結果として重複や欠落が出ても壊れた値を書かない。先勝ちで畳み、
        // 落ちた id は末尾に補う。
        val seen = LinkedHashSet<String>()
        val deduped = base.filter { seen.add(it) }
        return (deduped + allIds.filter { it !in seen }).joinToString(",")
    }

    /** [id] を隠す / 出すを切り替えた新しいカンマ区切り文字列を返す。隠せないボタンは無視する。 */
    fun toggleHidden(csv: String, id: String): String {
        if (CATALOG.any { it.id == id && !it.canHide }) return csv
        val cur = parseHidden(csv).toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        // 保存順はカタログ順に正規化する (差分が読みやすい)。
        return CATALOG.map { it.id }.filter { it in cur }.joinToString(",")
    }
}
