package com.zerotoship.z2term.emulator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TerminalEmulator.encodeMouseEvent] の出力固定テスト。
 *
 * 主眼:
 *  - SGR / URXVT / LEGACY 各エンコーディングの先頭 ESC (0x1B) と本体バイト列が
 *    xterm 仕様どおりに並ぶこと (ESC が抜けると TUI 側が CSI として認識しない)。
 *  - wheel up/down (button 64/65) と left click (button 0) の press/release が正しく
 *    出ること。
 *
 * `mouseProtocol` / `mouseEncoding` は外部から直接代入不可 (private set) なので、
 * DECSET シーケンスを emulator に投げて状態を切替える。
 */
class MouseEncodeTest {

    private val ESC = ""

    private fun newEmulatorWithSgrNormal(): TerminalEmulator {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        // ESC [ ? 1 0 0 0 h → mouseProtocol = NORMAL
        e.processBytes("$ESC[?1000h".toByteArray(Charsets.US_ASCII))
        // ESC [ ? 1 0 0 6 h → mouseEncoding = SGR
        e.processBytes("$ESC[?1006h".toByteArray(Charsets.US_ASCII))
        return e
    }

    private fun newEmulatorWithUrxvtNormal(): TerminalEmulator {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        e.processBytes("$ESC[?1000h".toByteArray(Charsets.US_ASCII))
        e.processBytes("$ESC[?1015h".toByteArray(Charsets.US_ASCII))
        return e
    }

    private fun newEmulatorWithLegacyNormal(): TerminalEmulator {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        // 1000 で NORMAL、encoding は既定 LEGACY のまま
        e.processBytes("$ESC[?1000h".toByteArray(Charsets.US_ASCII))
        return e
    }

    private fun newEmulatorWithSgrButtonEvent(): TerminalEmulator {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        // ESC [ ? 1 0 0 2 h → mouseProtocol = BUTTON_EVENT (押下中の motion を送る)
        e.processBytes("$ESC[?1002h".toByteArray(Charsets.US_ASCII))
        e.processBytes("$ESC[?1006h".toByteArray(Charsets.US_ASCII))
        return e
    }

    @Test
    fun decset1000And1006EnableNormalSgr() {
        val e = newEmulatorWithSgrNormal()
        assertEquals(TerminalEmulator.MouseProtocol.NORMAL, e.mouseProtocol)
        assertEquals(TerminalEmulator.MouseEncoding.SGR, e.mouseEncoding)
        assertTrue(e.mouseEnabled)
    }

    @Test
    fun sgrWheelDownStartsWithEscAndCsiSequence() {
        val e = newEmulatorWithSgrNormal()
        val bytes = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_WHEEL_DOWN,
            col0 = 0, row0 = 0, press = true
        )
        assertNotNull(bytes)
        // 期待: ESC [ < 6 5 ; 1 ; 1 M
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '<'.code.toByte(),
                '6'.code.toByte(), '5'.code.toByte(),
                ';'.code.toByte(), '1'.code.toByte(),
                ';'.code.toByte(), '1'.code.toByte(),
                'M'.code.toByte()),
            bytes
        )
    }

    @Test
    fun sgrWheelUpStartsWithEscAndCsiSequence() {
        val e = newEmulatorWithSgrNormal()
        val bytes = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_WHEEL_UP,
            col0 = 4, row0 = 2, press = true
        )
        assertNotNull(bytes)
        // 期待: ESC [ < 6 4 ; 5 ; 3 M
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '<'.code.toByte(),
                '6'.code.toByte(), '4'.code.toByte(),
                ';'.code.toByte(), '5'.code.toByte(),
                ';'.code.toByte(), '3'.code.toByte(),
                'M'.code.toByte()),
            bytes
        )
    }

    @Test
    fun sgrLeftClickPressAndReleaseDifferByTerminator() {
        val e = newEmulatorWithSgrNormal()
        val press = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT, col0 = 0, row0 = 0, press = true
        )!!
        val release = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT, col0 = 0, row0 = 0, press = false
        )!!
        // press は 'M' 終端、release は 'm' 終端
        assertEquals('M'.code.toByte(), press.last())
        assertEquals('m'.code.toByte(), release.last())
        // 先頭は ESC [ <
        assertEquals(0x1B.toByte(), press[0])
        assertEquals('['.code.toByte(), press[1])
        assertEquals('<'.code.toByte(), press[2])
    }

    @Test
    fun urxvtEncodingStartsWithEsc() {
        val e = newEmulatorWithUrxvtNormal()
        val bytes = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_WHEEL_DOWN,
            col0 = 0, row0 = 0, press = true
        )!!
        // 期待: ESC [ <cb> ; 1 ; 1 M, cb = 65 + 32 = 97
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals('['.code.toByte(), bytes[1])
        // 後続は ASCII 文字列
        val body = String(bytes, 1, bytes.size - 1, Charsets.US_ASCII)
        assertEquals("[97;1;1M", body)
    }

    @Test
    fun legacyEncodingHasEscAndOffsetBytes() {
        val e = newEmulatorWithLegacyNormal()
        val bytes = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_WHEEL_DOWN,
            col0 = 0, row0 = 0, press = true
        )!!
        // 期待: ESC [ M cb cx cy, cb = 65 + 32 = 97, cx = cy = 1 + 32 = 33
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), 'M'.code.toByte(),
                97.toByte(), 33.toByte(), 33.toByte()),
            bytes
        )
    }

    @Test
    fun protocolOffReturnsNull() {
        val e = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        // mouseProtocol = OFF (default)
        val bytes = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT, col0 = 0, row0 = 0, press = true
        )
        assertNull(bytes)
    }

    /**
     * DECRST 1049 で primary に戻るとマウスレポートが OFF に戻ること。一部 TUI が exit 時に
     * DECRST 1000/1006 を送らず DECRST 1049 (rmcup) だけ送るケースで、stale な mouseEnabled
     * が primary シェル上のスワイプから `\e[<...M` を流出させる症状を端末側で吸収するための
     * 保険。
     */
    @Test
    fun decrst1049ForcesMouseProtocolOff() {
        val e = newEmulatorWithSgrNormal()
        // alt screen に入る
        e.processBytes("$ESC[?1049h".toByteArray(Charsets.US_ASCII))
        assertTrue(e.mouseEnabled)
        // alt screen を抜ける (rmcup 相当) — マウスは陽に切られていない
        e.processBytes("$ESC[?1049l".toByteArray(Charsets.US_ASCII))
        assertEquals(TerminalEmulator.MouseProtocol.OFF, e.mouseProtocol)
        assertEquals(false, e.mouseEnabled)
    }

    @Test
    fun decrst1047ForcesMouseProtocolOff() {
        val e = newEmulatorWithSgrNormal()
        e.processBytes("$ESC[?1047h".toByteArray(Charsets.US_ASCII))
        assertTrue(e.mouseEnabled)
        e.processBytes("$ESC[?1047l".toByteArray(Charsets.US_ASCII))
        assertEquals(TerminalEmulator.MouseProtocol.OFF, e.mouseProtocol)
    }

    @Test
    fun decrst47ForcesMouseProtocolOff() {
        val e = newEmulatorWithSgrNormal()
        e.processBytes("$ESC[?47h".toByteArray(Charsets.US_ASCII))
        assertTrue(e.mouseEnabled)
        e.processBytes("$ESC[?47l".toByteArray(Charsets.US_ASCII))
        assertEquals(TerminalEmulator.MouseProtocol.OFF, e.mouseProtocol)
    }

    /**
     * SGR encoding で右クリック (button 2) の press/release を出すこと。
     * 画面タップの長押し→右クリック変換 (TerminalInputView.sendMouseRightClick) の
     * 中核となる encodeMouseEvent パス。
     */
    @Test
    fun sgrRightClickPressAndReleaseHaveExpectedBytes() {
        val e = newEmulatorWithSgrNormal()
        val press = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_RIGHT, col0 = 9, row0 = 4, press = true
        )!!
        val release = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_RIGHT, col0 = 9, row0 = 4, press = false
        )!!
        // 期待: ESC [ < 2 ; 10 ; 5 M / ESC [ < 2 ; 10 ; 5 m
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '<'.code.toByte(),
                '2'.code.toByte(), ';'.code.toByte(),
                '1'.code.toByte(), '0'.code.toByte(), ';'.code.toByte(),
                '5'.code.toByte(), 'M'.code.toByte()),
            press
        )
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '<'.code.toByte(),
                '2'.code.toByte(), ';'.code.toByte(),
                '1'.code.toByte(), '0'.code.toByte(), ';'.code.toByte(),
                '5'.code.toByte(), 'm'.code.toByte()),
            release
        )
    }

    /**
     * SGR encoding で 1 指ドラッグ (motion = true, button = 0) が button 32 として
     * 'M' 終端で出ること。 SGR 仕様では motion 中の左ボタン押下は button code = 0 | 32 = 32。
     * TerminalInputView.sendSgrMouseDrag の中核パス。
     */
    @Test
    fun sgrLeftDragMotionEncodesAsButton32WithMTerminator() {
        // motion を許可する BUTTON_EVENT (1002) で SGR エンコード
        val e = newEmulatorWithSgrButtonEvent()
        val drag = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT,
            col0 = 19, row0 = 9, press = true, motion = true
        )!!
        // 期待: ESC [ < 32 ; 20 ; 10 M
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '<'.code.toByte(),
                '3'.code.toByte(), '2'.code.toByte(), ';'.code.toByte(),
                '2'.code.toByte(), '0'.code.toByte(), ';'.code.toByte(),
                '1'.code.toByte(), '0'.code.toByte(),
                'M'.code.toByte()),
            drag
        )
    }

    /**
     * NORMAL (1000) は motion を送らないので drag (motion=true) は null を返すこと。
     * BUTTON_EVENT (1002) や ANY_EVENT (1003) を有効化していない TUI に対し
     * button 32 を流出させないことの回帰防止。
     */
    @Test
    fun sgrNormalProtocolSuppressesMotionEvents() {
        val e = newEmulatorWithSgrNormal()
        assertEquals(TerminalEmulator.MouseProtocol.NORMAL, e.mouseProtocol)
        val drag = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT,
            col0 = 0, row0 = 0, press = true, motion = true
        )
        assertNull(drag)
    }

    /**
     * BUTTON_EVENT (1002) を有効化すると motion を送れる (ドラッグ送出に必要)。
     */
    @Test
    fun sgrButtonEventProtocolAllowsMotionEvents() {
        val e = newEmulatorWithSgrButtonEvent()
        assertEquals(TerminalEmulator.MouseProtocol.BUTTON_EVENT, e.mouseProtocol)
        val drag = e.encodeMouseEvent(
            button = TerminalEmulator.MOUSE_BTN_LEFT,
            col0 = 0, row0 = 0, press = true, motion = true
        )!!
        // button 32 の SGR press で 'M' 終端
        assertEquals('M'.code.toByte(), drag.last())
        // 先頭は ESC [ <
        assertEquals(0x1B.toByte(), drag[0])
        assertEquals('<'.code.toByte(), drag[2])
    }
}
