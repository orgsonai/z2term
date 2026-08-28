package com.zerotoship.z2term.gui

import android.view.KeyEvent
import com.zerotoship.z2term.ui.terminal.keyboard.NamedKey
import org.junit.Assert.assertEquals
import org.junit.Test

class GuiKeyMapperTest {
    @Test
    fun mapsJapaneseInputNamedKeysToX11Keysyms() {
        assertEquals(0xFF2A, GuiKeyMapper.keysymForNamed(NamedKey.ZENKAKU_HANKAKU))
        assertEquals(0xFF23, GuiKeyMapper.keysymForNamed(NamedKey.HENKAN))
        assertEquals(0xFF22, GuiKeyMapper.keysymForNamed(NamedKey.MUHENKAN))
        assertEquals(0xFF27, GuiKeyMapper.keysymForNamed(NamedKey.KATAKANA_HIRAGANA))
        assertEquals(0xFF30, GuiKeyMapper.keysymForNamed(NamedKey.EISU))
    }

    @Test
    fun ordinaryNamedKeysDoNotUseTheJapaneseInputExit() {
        assertEquals(0, GuiKeyMapper.keysymForNamed(NamedKey.ENTER))
    }

    @Test
    fun mapsPhysicalJapaneseInputKeyCodesToTheSameKeysyms() {
        assertEquals(0xFF2A, GuiKeyMapper.keysymForKeyCode(KeyEvent.KEYCODE_ZENKAKU_HANKAKU))
        assertEquals(0xFF23, GuiKeyMapper.keysymForKeyCode(KeyEvent.KEYCODE_HENKAN))
        assertEquals(0xFF22, GuiKeyMapper.keysymForKeyCode(KeyEvent.KEYCODE_MUHENKAN))
        assertEquals(0xFF27, GuiKeyMapper.keysymForKeyCode(KeyEvent.KEYCODE_KATAKANA_HIRAGANA))
        assertEquals(0xFF30, GuiKeyMapper.keysymForKeyCode(KeyEvent.KEYCODE_EISU))
    }
}
