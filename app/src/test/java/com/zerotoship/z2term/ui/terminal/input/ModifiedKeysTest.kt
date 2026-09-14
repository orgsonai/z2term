package com.zerotoship.z2term.ui.terminal.input

import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifiedKeysTest {
    private fun assertKey(name: String, expected: String) {
        val result = AndroidKeyMapper.keyBytesFor(name) { error("Modified key lost its modifiers") }
        assertTrue(result is AndroidKeyMapper.KeyBytes.Ok)
        assertArrayEquals(name, expected.toByteArray(), (result as AndroidKeyMapper.KeyBytes.Ok).bytes)
    }

    @Test fun arrowsUseAllSevenModifierCombinations() {
        for ((prefix, parameter) in listOf(
            "S-" to 2, "M-" to 3, "S-M-" to 4, "C-" to 5,
            "S-C-" to 6, "M-C-" to 7, "S-M-C-" to 8,
        )) {
            for ((key, final) in listOf("Up" to "A", "Down" to "B", "Right" to "C", "Left" to "D")) {
                assertKey(prefix + key, "\u001b[1;" + parameter + final)
            }
        }
    }

    @Test fun editingAndFunctionKeysRetainModifiers() {
        for ((name, expected) in listOf(
            "M-Home" to "\u001b[1;3H", "C-End" to "\u001b[1;5F",
            "S-Ins" to "\u001b[2;2~", "C-Del" to "\u001b[3;5~",
            "M-PgUp" to "\u001b[5;3~", "S-C-PgDn" to "\u001b[6;6~",
            "M-F1" to "\u001b[1;3P", "C-F2" to "\u001b[1;5Q",
            "S-F3" to "\u001b[1;2R", "C-M-F4" to "\u001b[1;7S",
            "S-M-F5" to "\u001b[15;4~", "C-F6" to "\u001b[17;5~",
            "M-F7" to "\u001b[18;3~", "S-F8" to "\u001b[19;2~",
            "C-F9" to "\u001b[20;5~", "M-F10" to "\u001b[21;3~",
            "S-F11" to "\u001b[23;2~", "C-M-S-F12" to "\u001b[24;8~",
        )) assertKey(name, expected)
    }

    @Test fun plainArrowsFollowApplicationModeButModifiedArrowsRemainCsi() {
        val emulator = TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)
        for ((mode, plain) in listOf("\u001b[?1l" to "\u001b[A", "\u001b[?1h" to "\u001bOA")) {
            emulator.processBytes(mode.toByteArray())
            assertArrayEquals(plain.toByteArray(),
                AndroidKeyMapper.namedKeyBytes(NamedKey.UP, KeyModifiers(), emulator::cursorKeyBytes))
            assertArrayEquals("\u001b[1;3A".toByteArray(),
                AndroidKeyMapper.namedKeyBytes(NamedKey.UP, KeyModifiers(alt = true), emulator::cursorKeyBytes))
        }
    }

    @Test fun controlAndMetaCharactersKeepTheirLegacyEncoding() {
        assertKey("C-Space", "\u0000")
        assertKey("C-@", "\u0000")
        assertKey("M-C-Space", "\u001b\u0000")
        assertKey("M-x", "\u001bx")
        assertKey("C-M-c", "\u001b\u0003")
        assertKey("M-Enter", "\u001b\r")
        assertKey("M-BS", "\u001b\u007f")
        assertKey("C-BS", "\b")
        assertKey("S-Tab", "\u001b[Z")
        assertKey("M-S-Tab", "\u001b\u001b[Z")
    }
}
