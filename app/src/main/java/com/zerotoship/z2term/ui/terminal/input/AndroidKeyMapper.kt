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

    // --- キー名 → バイト列 (`z2-session key`・0.8.311) ---

    /** [keyBytesFor] の結果。⚠ **「送れない」と「そんな名前は無い」を分ける** — 前者は端末の
     * 仕組みの話で、名前を直しても解決しないため、返す文言も変わる。 */
    sealed interface KeyBytes {
        /** 送るバイト列。 */
        class Ok(val bytes: ByteArray) : KeyBytes

        /** 表に無い名前。 */
        data class Unknown(val name: String) : KeyBytes

        /**
         * 端末では**区別できない**組み合わせ (Shift + 文字)。
         * [equivalentTo] は「そう書けば同じものが送れる」キー。
         */
        data class NotDistinguishable(val asWritten: String, val equivalentTo: String) : KeyBytes
    }

    /**
     * `C-c` `M-x` `F5` `Up` のような**キー名**を PTY バイト列へ (`z2-session key`)。
     *
     * **なぜここに置くか**: 内蔵キーボード ([mapKeyEvent]) と CLI で送るバイトが違うと、
     * **片方でしか再現しない不具合**ができる。表は 1 か所に置いて両方から引く。
     *
     * 文法:
     *  - 修飾は `C-` (Ctrl) と `M-` / `A-` (Meta = Alt)。`C-M-a` のように重ねられる。
     *  - 特殊キーは `Up` `Down` `Left` `Right` `Home` `End` `PgUp` `PgDn` `Ins` `Del`
     *    `Tab` `S-Tab` `Enter` `Esc` `Space` `BS` `F1`〜`F12` (大文字小文字は問わない)。
     *  - どれでもない 1 文字はその文字そのもの。
     *
     * ⛔ **`C-S-a` のような Shift 付きは受け取らない** ([KeyBytes.NotDistinguishable])。
     * 端末では Shift が文字に畳み込まれ、`C-a` と**同じ 1 バイト**になって区別できない
     * ([controlByteFor] が `a..z` と `A..Z` を同じ値に潰しているのがそれ)。⚠ 黙って `C-a` を
     * 送ると「送ったはずなのに効かない」の原因が追えなくなるので、**送らずに理由を返す**。
     * 区別する規格 (xterm の modifyOtherKeys / Kitty keyboard protocol) は未実装。
     * ⚠ ただし **`S-Tab` は端末が区別できる**ので通す (`ESC [ Z`)。断る基準は「Shift が付くか」
     * ではなく「**端末が区別できるか**」。
     *
     * @param cursorBytes 矢印の VT バイト列ファクトリ (DECCKM 依存なので emulator に組ませる)
     */
    fun keyBytesFor(
        name: String,
        cursorBytes: (TerminalEmulator.CursorKey) -> ByteArray
    ): KeyBytes {
        var rest = name
        var ctrl = false
        var alt = false
        var shift = false
        // 修飾子を剥がす。⚠ `C-M-a` のように重なるのでループで見る。
        // ⚠ 長さ 2 より大きいことを条件にするのは、`-` そのものを送りたいとき (`C--`) に
        // 修飾子の途中と読まないため。
        while (rest.length > 2 && rest[1] == '-') {
            when (rest[0].uppercaseChar()) {
                'C' -> ctrl = true
                'M', 'A' -> alt = true
                'S' -> shift = true
                else -> return KeyBytes.Unknown(name)
            }
            rest = rest.substring(2)
        }

        // 特殊キー。⚠ Shift+Tab だけは端末が区別できるので通す。
        val special: ByteArray? = when (rest.lowercase()) {
            "up" -> cursorBytes(TerminalEmulator.CursorKey.UP)
            "down" -> cursorBytes(TerminalEmulator.CursorKey.DOWN)
            "left" -> cursorBytes(TerminalEmulator.CursorKey.LEFT)
            "right" -> cursorBytes(TerminalEmulator.CursorKey.RIGHT)
            "home" -> csi("1~")
            "end" -> csi("4~")
            "pgup", "pageup" -> csi("5~")
            "pgdn", "pagedown" -> csi("6~")
            "ins", "insert" -> csi("2~")
            "del", "delete" -> csi("3~")
            "bs", "backspace" -> byteArrayOf(0x7F)
            "enter", "return", "cr" -> byteArrayOf(0x0D)
            "esc", "escape" -> byteArrayOf(0x1B)
            "space" -> byteArrayOf(0x20)
            "tab" -> if (shift) CSI_BACKTAB else byteArrayOf(0x09)
            "backtab" -> CSI_BACKTAB
            "f1" -> ss3("P")
            "f2" -> ss3("Q")
            "f3" -> ss3("R")
            "f4" -> ss3("S")
            "f5" -> csi("15~")
            "f6" -> csi("17~")
            "f7" -> csi("18~")
            "f8" -> csi("19~")
            "f9" -> csi("20~")
            "f10" -> csi("21~")
            "f11" -> csi("23~")
            "f12" -> csi("24~")
            else -> null
        }
        if (special != null) {
            // ⚠ 特殊キーへの Ctrl はここでは表現しない (CSI の修飾パラメータが要り、
            // 受け手の対応もまちまち)。Alt だけは ESC 前置で素直に通るので許す。
            return KeyBytes.Ok(if (alt) byteArrayOf(0x1B) + special else special)
        }

        // ここから先は 1 文字のキー。Shift は文字そのものに畳み込まれる。
        if (rest.length != 1) return KeyBytes.Unknown(name)
        val ch = rest[0]
        if (shift) {
            // ⚠ 単独の `S-a` も断る。`A` と書けば済むものを 2 通りで受けると、
            // 「効く Shift と効かない Shift」ができて混乱するため。
            val equivalent = if (ctrl) "C-" + ch.lowercaseChar() else ch.uppercaseChar().toString()
            return KeyBytes.NotDistinguishable(name, equivalent)
        }
        if (ctrl) {
            val cb = controlByteFor(ch) ?: return KeyBytes.Unknown(name)
            return KeyBytes.Ok(if (alt) byteArrayOf(0x1B, cb) else byteArrayOf(cb))
        }
        val body = ch.toString().toByteArray(Charsets.UTF_8)
        return KeyBytes.Ok(if (alt) byteArrayOf(0x1B) + body else body)
    }
}
