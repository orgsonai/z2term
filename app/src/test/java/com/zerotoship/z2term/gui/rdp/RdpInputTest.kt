package com.zerotoship.z2term.gui.rdp

import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.RemoteDesktopClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RdpInputTest {
    @Test
    fun pointerStateBecomesMovePressDragAndReleaseEvents() {
        val input = RdpInput()
        assertEquals(
            listOf(
                RdpInput.Event.Mouse(0x0800, 10, 20),
                RdpInput.Event.Mouse(0x9000, 10, 20),
            ),
            input.pointerEvents(RemoteDesktopClient.BUTTON_LEFT, 10, 20),
        )
        assertEquals(
            listOf(RdpInput.Event.Mouse(0x0800, 11, 21)),
            input.pointerEvents(RemoteDesktopClient.BUTTON_LEFT, 11, 21),
        )
        assertEquals(
            listOf(
                RdpInput.Event.Mouse(0x0800, 11, 21),
                RdpInput.Event.Mouse(0x1000, 11, 21),
            ),
            input.pointerEvents(0, 11, 21),
        )
    }

    @Test
    fun wheelUsesSignedNineBitRotationAndClampsCoordinates() {
        val input = RdpInput()
        assertEquals(
            listOf(
                RdpInput.Event.Mouse(0x0800, 0, 0xFFFF),
                RdpInput.Event.Mouse(0x0278, 0, 0xFFFF),
            ),
            input.pointerEvents(RemoteDesktopClient.BUTTON_WHEEL_UP, -1, 70_000),
        )
        assertEquals(
            RdpInput.Event.Mouse(0x0388, 4, 5),
            input.pointerEvents(RemoteDesktopClient.BUTTON_WHEEL_DOWN, 4, 5).last(),
        )
    }

    @Test
    fun functionKeysUseScancodesAndTextUsesUnicode() {
        val input = RdpInput()
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x4B, extended = true, down = true)),
            input.keyEvents(GuiKeyMapper.XK_Left, down = true),
        )
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x4B, extended = true, down = false)),
            input.keyEvents(GuiKeyMapper.XK_Left, down = false),
        )
        assertEquals(
            listOf(RdpInput.Event.Unicode(0x3042, down = true)),
            input.keyEvents(0x01003042, down = true),
        )
    }

    @Test
    fun modifiersMakeAsciiScancodeShortcutsAndMarkRepeat() {
        val input = RdpInput()
        input.keyEvents(GuiKeyMapper.XK_Control_L, down = true)
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x2E, false, down = true)),
            input.keyEvents('c'.code, down = true),
        )
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x2E, false, down = true, repeat = true)),
            input.keyEvents('c'.code, down = true),
        )
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x2E, false, down = false)),
            input.keyEvents('c'.code, down = false),
        )
        assertEquals(
            listOf(RdpInput.Event.ScanCode(0x0C, false, down = true)),
            input.keyEvents('-'.code, down = true),
        )
    }

    @Test
    fun supplementaryUnicodeKeysymBecomesUtf16CodeUnits() {
        val input = RdpInput()
        assertEquals(
            listOf(
                RdpInput.Event.Unicode(0xD83D, down = true),
                RdpInput.Event.Unicode(0xDE00, down = true),
            ),
            input.keyEvents(0x0101F600, down = true),
        )
    }

    @Test
    fun slowPathBodyUsesDocumentedEventTypesAndFlags() {
        val body = RdpInput.encode(
            listOf(
                RdpInput.Event.ScanCode(0x4B, extended = true, down = false),
                RdpInput.Event.Unicode(0x3042, down = true),
                RdpInput.Event.Unicode(0x3042, down = true, repeat = true),
                RdpInput.Event.Mouse(0x9000, 10, 20),
            ),
        )
        assertArrayEquals(
            le16(4) + le16(0) +
                event(0x0004, 0x8100, 0x004B, 0) +
                event(0x0005, 0, 0x3042, 0) +
                event(0x0005, 0, 0x3042, 0) +
                event(0x8001, 0x9000, 10, 20),
            body,
        )
    }

    @Test
    fun activationWrapsInputAsShareDataPduType1c() {
        val session = RdpMcs.Session(1004, 1003, 0x00080004, 8)
        val active = RdpActivation.ActiveSession(0x11223344, 1002, emptySet(), emptySet())
        val packet = RdpActivation.inputEvents(
            session,
            active,
            listOf(RdpInput.Event.Unicode('A'.code, down = true)),
        )
        assertEquals(packet.size, be16(packet, 2))
        assertEquals(0x1C, packet[28].toInt() and 0xFF)
    }

    private fun event(type: Int, flags: Int, a: Int, b: Int) =
        le32(0) + le16(type) + le16(flags) + le16(a) + le16(b)
    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())
    private fun le32(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
    private fun be16(value: ByteArray, offset: Int) =
        ((value[offset].toInt() and 0xFF) shl 8) or (value[offset + 1].toInt() and 0xFF)
}
