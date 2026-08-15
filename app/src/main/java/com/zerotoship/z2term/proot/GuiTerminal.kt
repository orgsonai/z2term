package com.zerotoship.z2term.proot

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * GUI セッション (Xvnc + openbox) の中で起動するターミナルエミュレータの選択肢。
 *
 * - [binary]      : PATH 上の実行ファイル名 (`has` で導入済み判定・起動に使う)
 * - [packageName] : パッケージ名。**ここに挙げる端末は apk / apt / pacman で同名**なので 1 つで足りる
 *   (Xvnc / openbox / フォントの名前は distro ごとに違うので [z2guiScript] が distro 判定して切替える)。
 * - [noteRes]     : 設定 UI 用の一言 (言語追従)。
 *
 * 既定は [XTERM] (最軽量・全 distro 標準)。[KONSOLE] は KDE 系で初回導入が大きい。
 */
enum class GuiTerminal(
    val id: String,
    val displayName: String,
    val binary: String,
    val packageName: String,
    @StringRes val noteRes: Int
) {
    XTERM("xterm", "xterm", "xterm", "xterm", R.string.gui_terminal_xterm_desc),
    URXVT("urxvt", "rxvt-unicode (urxvt)", "urxvt", "rxvt-unicode", R.string.gui_terminal_urxvt_desc),
    LXTERMINAL("lxterminal", "LXTerminal", "lxterminal", "lxterminal", R.string.gui_terminal_lxterminal_desc),
    KONSOLE("konsole", "Konsole", "konsole", "konsole", R.string.gui_terminal_konsole_desc);

    companion object {
        val ALL: List<GuiTerminal> = values().toList()
        fun byId(id: String): GuiTerminal = ALL.firstOrNull { it.id == id } ?: XTERM

        /**
         * その distro でその端末が動かないと分かっている組み合わせ (0.8.353)。
         *
         * ⚠ **Alpine の Konsole は起動できない** — 窓を作る最後の段階で必ず segfault する
         * (2026-08-15 に実機で確認)。GL・フォント・ロケール・キャッシュ・依存パッケージ・
         * ファイル欠落はすべて否定済みで、**同じ画面で xterm / urxvt / LXTerminal は動く**。
         * KDE の初期化自体は通る (`konsole --list-profiles` は正常終了する) ので、
         * 導入不足ではなく Alpine の konsole そのものの問題。
         *
         * ⭐ **選べてしまうと「GUI を開いたが真っ黒のまま」になり、原因が分からない。**
         * 組み合わせの成立を止めて、その場で別の端末に替えられるようにする。
         */
        fun isUnsupported(terminalId: String, distroId: String): Boolean =
            terminalId == KONSOLE.id && distroId == "alpine"
    }
}
