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
    }
}
