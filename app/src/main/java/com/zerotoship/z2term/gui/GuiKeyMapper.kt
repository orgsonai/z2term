package com.zerotoship.z2term.gui

import android.view.KeyEvent
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey

/**
 * Android の入力 → **X11 keysym** 変換テーブル（GUI リモートデスクトップ用）。
 *
 * 端末用 [AndroidKeyMapper][com.zerotoship.z2term.ui.terminal.input.AndroidKeyMapper] は
 * 「VT バイト列」を作るが、GUI の共通入力は **keysym**（X11 の論理キー番号）を使う。
 * RFB はそのまま送信し、RDP はクライアント内部で scancode / Unicode input へ変換する。
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
    const val XK_F1 = 0xFFBE
    const val XK_F10 = 0xFFC7
    const val XK_F11 = 0xFFC8
    const val XK_F12 = 0xFFC9

    // --- 日本語入力方式キー (X11 keysymdef.h) ---
    const val XK_Muhenkan = 0xFF22
    const val XK_Henkan = 0xFF23
    const val XK_Zenkaku_Hankaku = 0xFF2A
    const val XK_Hiragana_Katakana = 0xFF27
    const val XK_Eisu_toggle = 0xFF30

    // --- 修飾キー ---
    const val XK_Shift_L = 0xFFE1
    const val XK_Shift_R = 0xFFE2
    const val XK_Control_L = 0xFFE3
    const val XK_Control_R = 0xFFE4
    const val XK_Caps_Lock = 0xFFE5
    const val XK_Alt_L = 0xFFE9
    const val XK_Alt_R = 0xFFEA
    const val XK_Super_L = 0xFFEB
    const val XK_Super_R = 0xFFEC

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
        keysymForKeyCode(event.keyCode).takeIf { it != 0 }?.let { return it }
        // 印字キー: 修飾も含めた現在の metaState で Unicode を取得（Shift+a → 'A' 等）。
        val unicode = event.unicodeChar
        if (unicode != 0) return keysymForCodePoint(unicode)
        return 0
    }

    /** Android keyCode のうち、文字入力を伴わない機能キーを keysym へ変換する。 */
    fun keysymForKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> XK_Return
        KeyEvent.KEYCODE_TAB -> XK_Tab
        KeyEvent.KEYCODE_ESCAPE -> XK_Escape
        KeyEvent.KEYCODE_DEL -> XK_BackSpace       // 端末では DEL=0x7F だが GUI は BackSpace
        KeyEvent.KEYCODE_FORWARD_DEL -> XK_Delete
        KeyEvent.KEYCODE_DPAD_UP -> XK_Up
        KeyEvent.KEYCODE_DPAD_DOWN -> XK_Down
        KeyEvent.KEYCODE_DPAD_LEFT -> XK_Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> XK_Right
        KeyEvent.KEYCODE_MOVE_HOME -> XK_Home
        KeyEvent.KEYCODE_MOVE_END -> XK_End
        KeyEvent.KEYCODE_PAGE_UP -> XK_Page_Up
        KeyEvent.KEYCODE_PAGE_DOWN -> XK_Page_Down
        KeyEvent.KEYCODE_INSERT -> XK_Insert
        KeyEvent.KEYCODE_SHIFT_LEFT -> XK_Shift_L
        KeyEvent.KEYCODE_SHIFT_RIGHT -> XK_Shift_R
        KeyEvent.KEYCODE_CTRL_LEFT -> XK_Control_L
        KeyEvent.KEYCODE_CTRL_RIGHT -> XK_Control_R
        KeyEvent.KEYCODE_ALT_LEFT -> XK_Alt_L
        KeyEvent.KEYCODE_ALT_RIGHT -> XK_Alt_R
        KeyEvent.KEYCODE_META_LEFT -> XK_Super_L
        KeyEvent.KEYCODE_META_RIGHT -> XK_Super_R
        KeyEvent.KEYCODE_CAPS_LOCK -> XK_Caps_Lock
        KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> XK_Zenkaku_Hankaku
        KeyEvent.KEYCODE_HENKAN -> XK_Henkan
        KeyEvent.KEYCODE_MUHENKAN -> XK_Muhenkan
        KeyEvent.KEYCODE_KATAKANA_HIRAGANA -> XK_Hiragana_Katakana
        KeyEvent.KEYCODE_EISU -> XK_Eisu_toggle
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> XK_F1 + (keyCode - KeyEvent.KEYCODE_F1)
        else -> 0
    }

    /** カーソルキー (端末用 enum) → keysym。GUI の独自キーボード矢印で使う。 */
    fun keysymForCursor(key: TerminalEmulator.CursorKey): Int = when (key) {
        TerminalEmulator.CursorKey.UP -> XK_Up
        TerminalEmulator.CursorKey.DOWN -> XK_Down
        TerminalEmulator.CursorKey.LEFT -> XK_Left
        TerminalEmulator.CursorKey.RIGHT -> XK_Right
    }

    /** カスタムキーボードの日本語入力方式キー → X11 keysym。端末側にはこの経路を配線しない。 */
    fun keysymForNamed(key: NamedKey): Int = when (key) {
        NamedKey.ZENKAKU_HANKAKU -> XK_Zenkaku_Hankaku
        NamedKey.HENKAN -> XK_Henkan
        NamedKey.MUHENKAN -> XK_Muhenkan
        NamedKey.KATAKANA_HIRAGANA -> XK_Hiragana_Katakana
        NamedKey.EISU -> XK_Eisu_toggle
        else -> 0
    }

    /** 確定文字列を 1 コードポイントずつ keysym で送る（かな漢字変換の確定・OS IME 確定で使う）。 */
    fun sendText(client: RemoteDesktopClient, text: String) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val ks = keysymForCodePoint(cp)
            if (ks != 0) client.tapKey(ks)
            i += Character.charCount(cp)
        }
    }

    /** keysym を Control 修飾付きで送る（Control_L 押下 → tap → Control_L 解放）。 */
    fun sendKeysymWithCtrl(client: RemoteDesktopClient, keysym: Int) {
        client.sendKeyEvent(XK_Control_L, down = true)
        client.tapKey(keysym)
        client.sendKeyEvent(XK_Control_L, down = false)
    }

    /** コードポイントを Ctrl 修飾付きで送る（特殊キーバーの C-C / C-D / C-L など）。 */
    fun sendCtrlCombo(client: RemoteDesktopClient, codePoint: Int) =
        sendKeysymWithCtrl(client, keysymForCodePoint(codePoint))

    /**
     * 端末用キーボード ([com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard]) が `onBytes` で
     * 吐く **VT バイト列**を、GUI(RFB) 用の **keysym 入力**へ橋渡しする。これで独自キーボード・
     * 日本語フリック・記号などを丸ごと GUI でも使える（HANDOFF「キーボードもGUIもCUIも同じ」要望）。
     *
     * 対応:
     *  - 先頭 ESC(0x1B) + 後続 = Alt 修飾 → Alt_L 押下のまま後続を再帰送出
     *  - 単独 ESC / Tab / Enter(CR/LF) / BackSpace(BS/DEL) → 専用 keysym
     *  - 制御文字 0x01–0x1A = Ctrl+英字 → Control 修飾付きでその英字を送出
     *  - それ以外 = 印字 UTF-8 → コードポイントごとに keysym（かな等は Unicode keysym）
     */
    fun sendBytes(client: RemoteDesktopClient, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        // Alt 修飾 (ESC プレフィックス + 後続 1 文字以上)
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0x1B) {
            client.sendKeyEvent(XK_Alt_L, down = true)
            sendBytes(client, bytes.copyOfRange(1, bytes.size))
            client.sendKeyEvent(XK_Alt_L, down = false)
            return
        }
        if (bytes.size == 1) {
            val b = bytes[0].toInt() and 0xFF
            when {
                b == 0x1B -> { client.tapKey(XK_Escape); return }
                b == 0x09 -> { client.tapKey(XK_Tab); return }
                b == 0x0D || b == 0x0A -> { client.tapKey(XK_Return); return }
                b == 0x08 || b == 0x7F -> { client.tapKey(XK_BackSpace); return }
                b in 0x01..0x1A -> { sendCtrlCombo(client, b + 0x60); return } // 0x01->'a'
            }
        }
        sendText(client, String(bytes, Charsets.UTF_8))
    }
}
