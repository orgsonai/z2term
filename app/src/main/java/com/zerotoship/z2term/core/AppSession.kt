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

    /**
     * このセッションに紐づく仮想 X ディスプレイ番号 (`:N`)。RFB ポートは `5900+display`。
     *
     * 端末タブと GUI タブで同じ番号を共有することで「この端末タブ ↔ この GUI タブ」のペアを表す
     * (P3 = CUI⇄GUI 連動)。端末タブは proot 環境変数 `DISPLAY=:N` / `Z2_DISPLAY=N` を受け取り、
     * その中で `z2run <gui-app>` を打つと同じ :N の Xvnc が立ち上がり、対応する GUI タブが
     * z2term 側で自動的に開く / 前面化される。
     *
     * [SessionManager] が払い出し、close 時は他に同じ番号を使うセッションが残っていなければ
     * pool に返却する (1:1 でも 1:多 でも面倒なく扱えるよう参照カウント風に管理)。
     */
    val display: Int

    /**
     * このタブで「何かが動作中」か (= 誤って閉じると作業を失いうる状態か)。
     *
     * タブのダブルタップ削除で削除確認ダイアログを出すか / 即削除するかの判定に使う。
     * 端末タブは PTY の前景プロセスがログインシェル以外なら true (子プロセス実行中)。
     * 既定は false で、判定手段を持たないセッション種別は従来どおり即削除にする。
     */
    val isBusy: Boolean
        get() = false

    /**
     * [isBusy] が**実態を表しているか**。false のタブでは動作中の印を出してはいけない。
     *
     * 端末タブでもチャネルによっては判定できず ([ProcessChannel.supportsForegroundChild])、
     * その場合 [isBusy] は安全側に倒れて常に true になる。閉じる確認では「多めに確認する」で
     * 済むが、**タブの印にすると嘘が出っぱなしになる**ので、表示側はこちらを見る。
     */
    val busyKnown: Boolean
        get() = false

    /** セッション終了 (リソース解放)。SessionManager.close から呼ばれる。 */
    fun shutdown()
}
