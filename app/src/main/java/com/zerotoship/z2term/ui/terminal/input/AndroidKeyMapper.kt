package com.zerotoship.z2term.ui.terminal.input

import android.view.KeyEvent
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey

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

        namedKeyForKeyCode(event.keyCode)?.let { key ->
            return namedKeyBytes(key, KeyModifiers(ctrl, alt, shift), cursorBytes)
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

    /** Ctrl と組み合わせた ASCII 文字に対応する制御コード。 */
    fun controlByteFor(ch: Char): Byte? = when {
        ch in 'a'..'z' -> (ch.code - 'a'.code + 1).toByte()
        ch in 'A'..'Z' -> (ch.code - 'A'.code + 1).toByte()
        ch == ' ' || ch == '@' -> 0
        ch == '[' -> 0x1B
        ch == '\\' -> 0x1C
        ch == ']' -> 0x1D
        ch == '^' -> 0x1E
        ch == '_' -> 0x1F
        ch == '?' -> 0x7F
        else -> null
    }

    // 機能キーは物理キー・内蔵キー・IME・CLI がこの表を共有する。
    private val namedKeyCodes = mapOf(
        NamedKey.ESC to KeyEvent.KEYCODE_ESCAPE,
        NamedKey.TAB to KeyEvent.KEYCODE_TAB,
        NamedKey.ENTER to KeyEvent.KEYCODE_ENTER,
        NamedKey.BACKSPACE to KeyEvent.KEYCODE_DEL,
        NamedKey.DELETE to KeyEvent.KEYCODE_FORWARD_DEL,
        NamedKey.UP to KeyEvent.KEYCODE_DPAD_UP,
        NamedKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        NamedKey.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        NamedKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        NamedKey.HOME to KeyEvent.KEYCODE_MOVE_HOME,
        NamedKey.END to KeyEvent.KEYCODE_MOVE_END,
        NamedKey.PAGE_UP to KeyEvent.KEYCODE_PAGE_UP,
        NamedKey.PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
        NamedKey.INSERT to KeyEvent.KEYCODE_INSERT,
        NamedKey.F1 to KeyEvent.KEYCODE_F1,
        NamedKey.F2 to KeyEvent.KEYCODE_F2,
        NamedKey.F3 to KeyEvent.KEYCODE_F3,
        NamedKey.F4 to KeyEvent.KEYCODE_F4,
        NamedKey.F5 to KeyEvent.KEYCODE_F5,
        NamedKey.F6 to KeyEvent.KEYCODE_F6,
        NamedKey.F7 to KeyEvent.KEYCODE_F7,
        NamedKey.F8 to KeyEvent.KEYCODE_F8,
        NamedKey.F9 to KeyEvent.KEYCODE_F9,
        NamedKey.F10 to KeyEvent.KEYCODE_F10,
        NamedKey.F11 to KeyEvent.KEYCODE_F11,
        NamedKey.F12 to KeyEvent.KEYCODE_F12,
    )

    fun keyCodeForNamed(key: NamedKey): Int? = namedKeyCodes[key]

    fun namedKeyForKeyCode(code: Int): NamedKey? =
        if (code == KeyEvent.KEYCODE_NUMPAD_ENTER) NamedKey.ENTER
        else namedKeyCodes.entries.firstOrNull { it.value == code }?.key

    fun namedKeyForCursor(key: TerminalEmulator.CursorKey): NamedKey = when (key) {
        TerminalEmulator.CursorKey.UP -> NamedKey.UP
        TerminalEmulator.CursorKey.DOWN -> NamedKey.DOWN
        TerminalEmulator.CursorKey.LEFT -> NamedKey.LEFT
        TerminalEmulator.CursorKey.RIGHT -> NamedKey.RIGHT
    }

    /**
     * xterm の修飾付き機能キー。修飾なしの矢印だけ DECCKM に従う。
     * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-PC-Style-Function-Keys
     * Alt+Up = CSI 1;3 A、Ctrl+Left = CSI 1;5 D。必ず一つのバイト列で送る。
     */
    fun namedKeyBytes(
        key: NamedKey,
        mods: KeyModifiers = KeyModifiers(),
        cursorBytes: (TerminalEmulator.CursorKey) -> ByteArray,
    ): ByteArray? {
        fun cursor(direction: TerminalEmulator.CursorKey, final: Char): ByteArray =
            if (mods.isEmpty) cursorBytes(direction) else csi("1;" + mods.xtermParameter + final)
        fun tilde(number: Int): ByteArray =
            csi(number.toString() + (if (mods.isEmpty) "" else ";" + mods.xtermParameter) + "~")
        fun letter(final: Char, plain: ByteArray): ByteArray =
            if (mods.isEmpty) plain else csi("1;" + mods.xtermParameter + final)
        fun meta(bytes: ByteArray): ByteArray =
            if (mods.alt) byteArrayOf(0x1B) + bytes else bytes

        return when (key) {
            NamedKey.UP -> cursor(TerminalEmulator.CursorKey.UP, 'A')
            NamedKey.DOWN -> cursor(TerminalEmulator.CursorKey.DOWN, 'B')
            NamedKey.RIGHT -> cursor(TerminalEmulator.CursorKey.RIGHT, 'C')
            NamedKey.LEFT -> cursor(TerminalEmulator.CursorKey.LEFT, 'D')
            NamedKey.HOME -> letter('H', csi("1~"))
            NamedKey.END -> letter('F', csi("4~"))
            NamedKey.INSERT -> tilde(2)
            NamedKey.DELETE -> tilde(3)
            NamedKey.PAGE_UP -> tilde(5)
            NamedKey.PAGE_DOWN -> tilde(6)
            NamedKey.F1 -> letter('P', ss3("P"))
            NamedKey.F2 -> letter('Q', ss3("Q"))
            NamedKey.F3 -> letter('R', ss3("R"))
            NamedKey.F4 -> letter('S', ss3("S"))
            NamedKey.F5 -> tilde(15)
            NamedKey.F6 -> tilde(17)
            NamedKey.F7 -> tilde(18)
            NamedKey.F8 -> tilde(19)
            NamedKey.F9 -> tilde(20)
            NamedKey.F10 -> tilde(21)
            NamedKey.F11 -> tilde(23)
            NamedKey.F12 -> tilde(24)
            // 従来の端末プロトコルでは Ctrl+Tab / Shift+Enter 等は区別できない。
            NamedKey.TAB -> meta(if (mods.shift) csi("Z") else byteArrayOf(0x09))
            NamedKey.ENTER -> meta(byteArrayOf(0x0D))
            NamedKey.ESC -> meta(byteArrayOf(0x1B))
            NamedKey.BACKSPACE -> meta(byteArrayOf((if (mods.ctrl) 0x08 else 0x7F).toByte()))
            else -> null // 日本語入力方式キーは GUI 専用。
        }
    }

    private fun csi(suffix: String): ByteArray =
        ("\u001b[" + suffix).toByteArray(Charsets.US_ASCII)

    private fun ss3(suffix: String): ByteArray =
        ("\u001bO" + suffix).toByteArray(Charsets.US_ASCII)

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
     *  - 修飾は `C-` (Ctrl)、`M-` / `A-` (Alt)、`S-` (Shift)。重ねて指定できる。
     *  - 特殊キーは `Up` `Down` `Left` `Right` `Home` `End` `PgUp` `PgDn` `Ins` `Del`
     *    `Tab` `S-Tab` `Enter` `Esc` `Space` `BS` `F1`〜`F12` (大文字小文字は問わない)。
     *  - どれでもない 1 文字はその文字そのもの。
     *
     * ⛔ **`C-S-a` のような Shift 付きは受け取らない** ([KeyBytes.NotDistinguishable])。
     * 端末では Shift が文字に畳み込まれ、`C-a` と**同じ 1 バイト**になって区別できない
     * ([controlByteFor] が `a..z` と `A..Z` を同じ値に潰しているのがそれ)。⚠ 黙って `C-a` を
     * 送ると「送ったはずなのに効かない」の原因が追えなくなるので、**送らずに理由を返す**。
     * 区別する規格 (xterm の modifyOtherKeys / Kitty keyboard protocol) は未実装。
     * 矢印・編集キー・F キーへの Shift / Ctrl / Alt は xterm の修飾パラメータで区別する。
     * Shift+Tab は従来どおり backtab を送る。
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

        val normalized = rest.lowercase()
        val named = when (normalized) {
            "bs" -> NamedKey.BACKSPACE
            "return", "cr" -> NamedKey.ENTER
            "escape" -> NamedKey.ESC
            "pageup" -> NamedKey.PAGE_UP
            "pagedown" -> NamedKey.PAGE_DOWN
            "ins" -> NamedKey.INSERT
            "del" -> NamedKey.DELETE
            "backtab" -> NamedKey.TAB
            else -> NamedKey.byId(normalized)
        }
        if (named != null) {
            val mods = KeyModifiers(ctrl, alt, shift || normalized == "backtab")
            val special = namedKeyBytes(named, mods, cursorBytes)
                ?: return KeyBytes.Unknown(name)
            return KeyBytes.Ok(special)
        }
        if (normalized == "space") rest = " "

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
