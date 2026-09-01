package com.zerotoship.z2term.gui.rdp

import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.RemoteDesktopClient
import java.io.ByteArrayOutputStream

/** X11 keysym / pointer state を RDP slow-path Input Event へ変換する state machine。 */
internal class RdpInput {
    internal sealed interface Event {
        data class ScanCode(
            val code: Int,
            val extended: Boolean,
            val down: Boolean,
            val repeat: Boolean = false,
        ) : Event
        data class Unicode(
            val codeUnit: Int,
            val down: Boolean,
            val repeat: Boolean = false,
        ) : Event
        data class Mouse(val flags: Int, val x: Int, val y: Int) : Event
    }

    private sealed interface KeyMapping {
        data class ScanCode(val code: Int, val extended: Boolean = false) : KeyMapping
        data class Unicode(val codeUnits: IntArray) : KeyMapping
    }

    private var pointerButtons = 0
    private val modifiers = linkedSetOf<Int>()
    private val pressedKeys = mutableMapOf<Int, KeyMapping>()

    fun reset() {
        pointerButtons = 0
        modifiers.clear()
        pressedKeys.clear()
    }

    fun pointerEvents(mask: Int, x: Int, y: Int): List<Event> {
        val px = x.coerceIn(0, 0xFFFF)
        val py = y.coerceIn(0, 0xFFFF)
        val current = mask and POINTER_BUTTONS
        val changed = pointerButtons xor current
        val events = mutableListOf<Event>(Event.Mouse(PTR_MOVE, px, py))
        BUTTON_MAP.forEach { (common, rdp) ->
            if (changed and common != 0) {
                events += Event.Mouse(rdp or if (current and common != 0) PTR_DOWN else 0, px, py)
            }
        }
        if (mask and RemoteDesktopClient.BUTTON_WHEEL_UP != 0) {
            events += Event.Mouse(PTR_WHEEL or 120, px, py)
        }
        if (mask and RemoteDesktopClient.BUTTON_WHEEL_DOWN != 0) {
            events += Event.Mouse(PTR_WHEEL or (-120 and 0x01FF), px, py)
        }
        pointerButtons = current
        return events
    }

    fun keyEvents(keysym: Int, down: Boolean): List<Event> {
        if (keysym == 0) return emptyList()
        val repeat = down && keysym in pressedKeys
        val mapping = if (down) {
            pressedKeys[keysym] ?: mappingFor(keysym)?.also { pressedKeys[keysym] = it }
        } else {
            pressedKeys.remove(keysym) ?: mappingFor(keysym)
        } ?: return emptyList()
        val events = when (mapping) {
            is KeyMapping.ScanCode ->
                listOf(Event.ScanCode(mapping.code, mapping.extended, down, repeat))
            is KeyMapping.Unicode ->
                mapping.codeUnits.map { Event.Unicode(it, down, repeat) }
        }
        if (keysym in MODIFIERS) {
            if (down) modifiers += keysym else modifiers -= keysym
        }
        return events
    }

    private fun mappingFor(keysym: Int): KeyMapping? {
        SPECIAL_KEYS[keysym]?.let { return it }
        if (keysym in GuiKeyMapper.XK_F1..GuiKeyMapper.XK_F10) {
            return KeyMapping.ScanCode(0x3B + keysym - GuiKeyMapper.XK_F1)
        }
        if (modifiers.isNotEmpty()) asciiScanCode(keysym)?.let { return it }
        val cp = when {
            keysym in 0x20..0x7E || keysym in 0xA0..0xFF -> keysym
            keysym and 0xFF000000.toInt() == 0x01000000 ->
                (keysym and 0x00FFFFFF).takeIf(Character::isValidCodePoint)
            else -> null
        } ?: return null
        return KeyMapping.Unicode(Character.toChars(cp).map(Char::code).toIntArray())
    }

    private fun asciiScanCode(keysym: Int): KeyMapping.ScanCode? {
        val cp = keysym.toChar().lowercaseChar()
        val (row, start) = when {
            cp in "1234567890" -> "1234567890" to 0x02
            cp in "qwertyuiop" -> "qwertyuiop" to 0x10
            cp in "asdfghjkl" -> "asdfghjkl" to 0x1E
            cp in "zxcvbnm" -> "zxcvbnm" to 0x2C
            cp == ' ' -> return KeyMapping.ScanCode(0x39)
            cp == '-' || cp == '_' -> return KeyMapping.ScanCode(0x0C)
            cp == '=' || cp == '+' -> return KeyMapping.ScanCode(0x0D)
            cp == '[' || cp == '{' -> return KeyMapping.ScanCode(0x1A)
            cp == ']' || cp == '}' -> return KeyMapping.ScanCode(0x1B)
            cp == ';' || cp == ':' -> return KeyMapping.ScanCode(0x27)
            cp == '`' || cp == '~' -> return KeyMapping.ScanCode(0x29)
            cp == '\\' || cp == '|' -> return KeyMapping.ScanCode(0x2B)
            cp == ',' || cp == '<' -> return KeyMapping.ScanCode(0x33)
            cp == '.' || cp == '>' -> return KeyMapping.ScanCode(0x34)
            cp == '/' || cp == '?' -> return KeyMapping.ScanCode(0x35)
            else -> return null
        }
        return KeyMapping.ScanCode(start + row.indexOf(cp))
    }

    companion object {
        private const val TYPE_SCANCODE = 0x0004
        private const val TYPE_UNICODE = 0x0005
        private const val TYPE_MOUSE = 0x8001
        private const val KBD_EXTENDED = 0x0100
        private const val KBD_DOWN = 0x4000
        private const val KBD_RELEASE = 0x8000
        private const val PTR_WHEEL = 0x0200
        private const val PTR_MOVE = 0x0800
        private const val PTR_DOWN = 0x8000
        private const val POINTER_BUTTONS = 0x07
        private val BUTTON_MAP = listOf(
            RemoteDesktopClient.BUTTON_LEFT to 0x1000,
            RemoteDesktopClient.BUTTON_RIGHT to 0x2000,
            RemoteDesktopClient.BUTTON_MIDDLE to 0x4000,
        )
        private val MODIFIERS = setOf(
            GuiKeyMapper.XK_Shift_L, GuiKeyMapper.XK_Shift_R,
            GuiKeyMapper.XK_Control_L, GuiKeyMapper.XK_Control_R,
            GuiKeyMapper.XK_Alt_L, GuiKeyMapper.XK_Alt_R,
            GuiKeyMapper.XK_Super_L, GuiKeyMapper.XK_Super_R,
        )
        private fun key(code: Int, extended: Boolean = false) =
            KeyMapping.ScanCode(code, extended)
        private val SPECIAL_KEYS = mapOf(
            GuiKeyMapper.XK_BackSpace to key(0x0E), GuiKeyMapper.XK_Tab to key(0x0F),
            GuiKeyMapper.XK_Return to key(0x1C), GuiKeyMapper.XK_Escape to key(0x01),
            GuiKeyMapper.XK_Home to key(0x47, true), GuiKeyMapper.XK_Left to key(0x4B, true),
            GuiKeyMapper.XK_Up to key(0x48, true), GuiKeyMapper.XK_Right to key(0x4D, true),
            GuiKeyMapper.XK_Down to key(0x50, true), GuiKeyMapper.XK_Page_Up to key(0x49, true),
            GuiKeyMapper.XK_Page_Down to key(0x51, true), GuiKeyMapper.XK_End to key(0x4F, true),
            GuiKeyMapper.XK_Insert to key(0x52, true), GuiKeyMapper.XK_Delete to key(0x53, true),
            GuiKeyMapper.XK_Shift_L to key(0x2A), GuiKeyMapper.XK_Shift_R to key(0x36),
            GuiKeyMapper.XK_Control_L to key(0x1D), GuiKeyMapper.XK_Control_R to key(0x1D, true),
            GuiKeyMapper.XK_Caps_Lock to key(0x3A), GuiKeyMapper.XK_Alt_L to key(0x38),
            GuiKeyMapper.XK_Alt_R to key(0x38, true), GuiKeyMapper.XK_Super_L to key(0x5B, true),
            GuiKeyMapper.XK_Super_R to key(0x5C, true), GuiKeyMapper.XK_F11 to key(0x57),
            GuiKeyMapper.XK_F12 to key(0x58), GuiKeyMapper.XK_Zenkaku_Hankaku to key(0x29),
            GuiKeyMapper.XK_Henkan to key(0x79), GuiKeyMapper.XK_Muhenkan to key(0x7B),
            GuiKeyMapper.XK_Hiragana_Katakana to key(0x70), GuiKeyMapper.XK_Eisu_toggle to key(0x3A),
        )

        /** TS_INPUT_PDU_DATA body。各 slow-path event は常に 12 bytes。 */
        fun encode(events: List<Event>): ByteArray {
            require(events.size in 1..0xFFFF)
            return Writer().apply {
                le16(events.size)
                le16(0)
                events.forEach { event ->
                    le32(0)
                    when (event) {
                        is Event.ScanCode -> {
                            le16(TYPE_SCANCODE)
                            le16(
                                (if (event.extended) KBD_EXTENDED else 0) or
                                    (if (!event.down) KBD_RELEASE else 0) or
                                    (if (event.repeat) KBD_DOWN else 0),
                            )
                            le16(event.code)
                            le16(0)
                        }
                        is Event.Unicode -> {
                            le16(TYPE_UNICODE)
                            le16(if (!event.down) KBD_RELEASE else 0)
                            le16(event.codeUnit)
                            le16(0)
                        }
                        is Event.Mouse -> {
                            le16(TYPE_MOUSE)
                            le16(event.flags)
                            le16(event.x)
                            le16(event.y)
                        }
                    }
                }
            }.array()
        }

        private class Writer {
            private val out = ByteArrayOutputStream()
            fun le16(value: Int) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
            }
            fun le32(value: Int) = repeat(4) {
                out.write((value ushr (it * 8)) and 0xFF)
            }
            fun array(): ByteArray = out.toByteArray()
        }
    }
}
