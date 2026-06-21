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
 *    xterm 仕様どおりに並ぶこと (ESC が抜けると nvlg/less が CSI として認識しない)。
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
}
