package com.zerotoship.z2term.proot

/**
 * GUI セッション (Xvnc + openbox) の中で起動するターミナルエミュレータの選択肢。
 *
 * - [binary]      : PATH 上の実行ファイル名 (`has` で導入済み判定・起動に使う)
 * - [packageName] : パッケージ名。**ここに挙げる端末は apk / apt / pacman で同名**なので 1 つで足りる
 *   (Xvnc / openbox / フォントの名前は distro ごとに違うので [z2guiScript] が distro 判定して切替える)。
 * - [note]        : 設定 UI 用の一言。
 *
 * 既定は [XTERM] (最軽量・全 distro 標準)。[KONSOLE] は KDE 系で初回導入が大きい。
 */
enum class GuiTerminal(
    val id: String,
    val displayName: String,
    val binary: String,
    val packageName: String,
    val note: String = ""
) {
    XTERM("xterm", "xterm", "xterm", "xterm", "最軽量・全 distro 標準"),
    URXVT("urxvt", "rxvt-unicode (urxvt)", "urxvt", "rxvt-unicode", "軽量・日本語表示可"),
    LXTERMINAL("lxterminal", "LXTerminal", "lxterminal", "lxterminal", "軽量 GTK"),
    KONSOLE("konsole", "Konsole", "konsole", "konsole", "KDE 系・初回導入が大きい");

    companion object {
        val ALL: List<GuiTerminal> = values().toList()
        fun byId(id: String): GuiTerminal = ALL.firstOrNull { it.id == id } ?: XTERM
    }
}
