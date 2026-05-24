package com.zerotoship.z2term.core

import kotlinx.coroutines.flow.StateFlow

/**
 * タブとして並べられるセッションの共通インターフェース。
 *
 * 実装は 2 種類:
 *  - [TerminalSession]      … PTY ベースの端末タブ
 *  - [com.zerotoship.z2term.gui.GuiSession] … Xvnc + RFB の Linux GUI タブ
 *
 * [SessionManager] はこの型でまとめて保持し、タブバー (TerminalScreen) は
 * [id] と [label] だけ見てチップを描く。中身の描画は型で分岐する。
 */
interface AppSession {
    /** 不変のセッション識別子 (タブ選択・クローズのキー)。 */
    val id: String

    /** タブ表示名 (StateFlow なので変化が UI に追従する)。 */
    val label: StateFlow<String>

    /** セッション終了 (リソース解放)。SessionManager.close から呼ばれる。 */
    fun shutdown()
}
