package com.zerotoship.z2term.ui.terminal.input

import android.view.KeyEvent
import com.zerotoship.z2term.emulator.TerminalEmulator

/**
 * Android KeyEvent → PTY 送出バイト列の変換テーブル。
 *
 * 設計方針:
 * - 物理キーボード (BT / USB) と OS のソフト IME 経由の物理キー path 双方で同じロジックを使う。
 * - 矢印キーはエミュレータの `applicationCursorKeys` モードによってバイト列が変わるので
 *   呼び出し側から `cursorBytes` ラムダで取得する。
 * - Ctrl / Alt 修飾は KeyEvent の meta から取るが、SpecialKeyBar の sticky-Ctrl も加味できるよう
 *   外から override 可能。
 *
 * 参考: termux/terminal-emulator KeyHandler、xterm(1) ctlseqs、ECMA-48。
 */
object AndroidKeyMapper {

    /**
     * KeyEvent から PTY バイトを生成する。
     *
     * @param event Android のキーイベント (KEY_DOWN を想定)
     * @param ctrlSticky SpecialKeyBar 等で Ctrl がトグル ON の場合 true
     * @param cursorBytes 矢印キー押下時の VT バイト列ファクトリ (emulator のモード依存)
     * @return PTY に送るべきバイト列、または null (このイベントは無視 / IME に委ねる)
     */
    fun mapKeyEvent(
        event: KeyEvent,
        ctrlSticky: Boolean = false,
        cursorBytes: (TerminalEmulator.CursorKey) -> ByteArray
    ): ByteArray? {
        val ctrl = ctrlSticky || event.isCtrlPressed
        val alt = event.isAltPressed
        val shift = event.isShiftPressed

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                return byteArrayOf(0x0D)
            KeyEvent.KEYCODE_TAB ->
                return if (shift) CSI_BACKTAB else byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE ->
                return byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DEL ->
                return byteArrayOf(0x7F) // BS = DEL (modern shells)
            KeyEvent.KEYCODE_FORWARD_DEL ->
                return csi("3~")
            KeyEvent.KEYCODE_DPAD_UP -> return cursorBytes(TerminalEmulator.CursorKey.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> return cursorBytes(TerminalEmulator.CursorKey.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> return cursorBytes(TerminalEmulator.CursorKey.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return cursorBytes(TerminalEmulator.CursorKey.RIGHT)
            KeyEvent.KEYCODE_MOVE_HOME -> return csi("1~")
            KeyEvent.KEYCODE_MOVE_END -> return csi("4~")
            KeyEvent.KEYCODE_PAGE_UP -> return csi("5~")
            KeyEvent.KEYCODE_PAGE_DOWN -> return csi("6~")
            KeyEvent.KEYCODE_INSERT -> return csi("2~")
            KeyEvent.KEYCODE_F1 -> return ss3("P")
            KeyEvent.KEYCODE_F2 -> return ss3("Q")
            KeyEvent.KEYCODE_F3 -> return ss3("R")
            KeyEvent.KEYCODE_F4 -> return ss3("S")
            KeyEvent.KEYCODE_F5 -> return csi("15~")
            KeyEvent.KEYCODE_F6 -> return csi("17~")
            KeyEvent.KEYCODE_F7 -> return csi("18~")
            KeyEvent.KEYCODE_F8 -> return csi("19~")
            KeyEvent.KEYCODE_F9 -> return csi("20~")
            KeyEvent.KEYCODE_F10 -> return csi("21~")
            KeyEvent.KEYCODE_F11 -> return csi("23~")
            KeyEvent.KEYCODE_F12 -> return csi("24~")
        }

        // Ctrl / Alt を外して unicode を取り直すことで「Ctrl+a」が a として取れる。
        val stripMask = (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()
        val unicode = event.getUnicodeChar(event.metaState and stripMask)
        if (unicode == 0) return null
        val ch = unicode.toChar()

        // Ctrl 修飾あり: 制御コード
        if (ctrl) {
            val cb = controlByteFor(ch)
            if (cb != null) {
                return if (alt) byteArrayOf(0x1B, cb) else byteArrayOf(cb)
            }
        }

        // Alt 修飾あり: ESC プレフィックス
        val charBytes = ch.toString().toByteArray(Charsets.UTF_8)
        return if (alt) byteArrayOf(0x1B) + charBytes else charBytes
    }

    /** Ctrl 修飾なしで「ASCII 文字 → 対応する control code」を引く */
    fun controlByteFor(ch: Char): Byte? = when {
        ch in 'a'..'z' -> (ch.code - 'a'.code + 1).toByte()
        ch in 'A'..'Z' -> (ch.code - 'A'.code + 1).toByte()
        ch == ' ' -> 0
        ch == '[' -> 0x1B
        ch == '\\' -> 0x1C
        ch == ']' -> 0x1D
        ch == '^' -> 0x1E
        ch == '_' -> 0x1F
        ch == '?' -> 0x7F
        else -> null
    }

    private fun csi(suffix: String): ByteArray =
        ("[" + suffix).toByteArray(Charsets.US_ASCII)

    private fun ss3(suffix: String): ByteArray =
        ("O" + suffix).toByteArray(Charsets.US_ASCII)

    private val CSI_BACKTAB: ByteArray = byteArrayOf(0x1B, 0x5B, 0x5A) // ESC [ Z
}
