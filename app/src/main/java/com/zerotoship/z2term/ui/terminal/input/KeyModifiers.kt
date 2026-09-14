package com.zerotoship.z2term.ui.terminal.input

/** キーと一緒に運ぶ修飾状態。ESC と矢印を別イベントに分割しない。 */
data class KeyModifiers(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    val isEmpty: Boolean get() = !ctrl && !alt && !shift
    val xtermParameter: Int get() =
        1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
}
