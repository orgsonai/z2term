package com.zerotoship.z2term.gui

import android.view.KeyEvent

/**
 * Android の入力 → **X11 keysym** 変換テーブル（GUI = VNC 用）。
 *
 * 端末用 [AndroidKeyMapper][com.zerotoship.z2term.ui.terminal.input.AndroidKeyMapper] は
 * 「VT バイト列」を作るが、VNC(RFB) のキーイベントは **keysym**（X11 の論理キー番号）を送る。
 * そのため GUI には別経路を用意する（M8-GUI-HANDOFF「3-3」「次の人向け」参照）。
 *
 * keysym のルール:
 *  - ASCII 印字可能 (0x20–0x7E) と Latin-1 上位 (0xA0–0xFF) は **コードポイントそのまま**。
 *  - それ以外の Unicode は `0x01000000 + コードポイント`（X11 の Unicode keysym 規約）。
 *  - 矢印 / Enter / BS / Tab / Esc / Home 等の機能キー・修飾キーは専用の固定値（下記定数）。
 *
 * 参考: X11 `keysymdef.h`、RFB 3.8 仕様 (KeyEvent)。
 */
object GuiKeyMapper {

    // --- 機能キー (0xFF00 帯) ---
    const val XK_BackSpace = 0xFF08
    const val XK_Tab = 0xFF09
    const val XK_Return = 0xFF0D
    const val XK_Escape = 0xFF1B
    const val XK_Home = 0xFF50
    const val XK_Left = 0xFF51
    const val XK_Up = 0xFF52
    const val XK_Right = 0xFF53
    const val XK_Down = 0xFF54
    const val XK_Page_Up = 0xFF55
    const val XK_Page_Down = 0xFF56
    const val XK_End = 0xFF57
    const val XK_Insert = 0xFF63
    const val XK_Delete = 0xFFFF
    private const val XK_F1 = 0xFFBE // F1..F12 は連番 (F_n = XK_F1 + n-1)

    // --- 修飾キー ---
    private const val XK_Shift_L = 0xFFE1
    private const val XK_Shift_R = 0xFFE2
    private const val XK_Control_L = 0xFFE3
    private const val XK_Control_R = 0xFFE4
    private const val XK_Caps_Lock = 0xFFE5
    private const val XK_Alt_L = 0xFFE9
    private const val XK_Alt_R = 0xFFEA
    private const val XK_Super_L = 0xFFEB
    private const val XK_Super_R = 0xFFEC

    /** Unicode コードポイント → keysym。 */
    fun keysymForCodePoint(cp: Int): Int = when {
        cp in 0x20..0x7E -> cp                 // ASCII 印字可能
        cp in 0xA0..0xFF -> cp                 // Latin-1 上位
        cp == 0x0A || cp == 0x0D -> XK_Return  // 改行は Return
        cp == 0x09 -> XK_Tab
        cp == 0x08 -> XK_BackSpace
        cp == 0x1B -> XK_Escape
        cp >= 0x100 -> 0x01000000 + cp         // X11 Unicode keysym
        else -> 0                              // その他の制御文字は無視
    }

    /**
     * Android [KeyEvent] → keysym。機能キー・修飾キーを優先し、印字キーは getUnicodeChar で解決する。
     * @return keysym。0 なら「このイベントは送らない」。
     */
    fun keysymForKeyEvent(event: KeyEvent): Int {
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return XK_Return
            KeyEvent.KEYCODE_TAB -> return XK_Tab
            KeyEvent.KEYCODE_ESCAPE -> return XK_Escape
            KeyEvent.KEYCODE_DEL -> return XK_BackSpace       // 端末では DEL=0x7F だが GUI は BackSpace
            KeyEvent.KEYCODE_FORWARD_DEL -> return XK_Delete
            KeyEvent.KEYCODE_DPAD_UP -> return XK_Up
            KeyEvent.KEYCODE_DPAD_DOWN -> return XK_Down
            KeyEvent.KEYCODE_DPAD_LEFT -> return XK_Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> return XK_Right
            KeyEvent.KEYCODE_MOVE_HOME -> return XK_Home
            KeyEvent.KEYCODE_MOVE_END -> return XK_End
            KeyEvent.KEYCODE_PAGE_UP -> return XK_Page_Up
            KeyEvent.KEYCODE_PAGE_DOWN -> return XK_Page_Down
            KeyEvent.KEYCODE_INSERT -> return XK_Insert
            KeyEvent.KEYCODE_SHIFT_LEFT -> return XK_Shift_L
            KeyEvent.KEYCODE_SHIFT_RIGHT -> return XK_Shift_R
            KeyEvent.KEYCODE_CTRL_LEFT -> return XK_Control_L
            KeyEvent.KEYCODE_CTRL_RIGHT -> return XK_Control_R
            KeyEvent.KEYCODE_ALT_LEFT -> return XK_Alt_L
            KeyEvent.KEYCODE_ALT_RIGHT -> return XK_Alt_R
            KeyEvent.KEYCODE_META_LEFT -> return XK_Super_L
            KeyEvent.KEYCODE_META_RIGHT -> return XK_Super_R
            KeyEvent.KEYCODE_CAPS_LOCK -> return XK_Caps_Lock
        }
        if (event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            return XK_F1 + (event.keyCode - KeyEvent.KEYCODE_F1)
        }
        // 印字キー: 修飾も含めた現在の metaState で Unicode を取得（Shift+a → 'A' 等）。
        val unicode = event.unicodeChar
        if (unicode != 0) return keysymForCodePoint(unicode)
        return 0
    }
}
