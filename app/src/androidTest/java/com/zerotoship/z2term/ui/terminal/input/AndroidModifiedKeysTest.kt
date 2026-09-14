package com.zerotoship.z2term.ui.terminal.input

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidModifiedKeysTest {
    private fun bytes(code: Int, meta: Int, sticky: Boolean = false): ByteArray? =
        AndroidKeyMapper.mapKeyEvent(
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.normalizeMetaState(meta)),
            ctrlSticky = sticky,
        ) { byteArrayOf(0x1B, 'O'.code.toByte(), 'A'.code.toByte()) }

    @Test fun physicalModifiersSurviveOnSpecialKeys() {
        assertArrayEquals("\u001b[1;3A".toByteArray(),
            bytes(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_ALT_LEFT_ON))
        assertArrayEquals("\u001b[1;5D".toByteArray(),
            bytes(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_RIGHT_ON))
        assertArrayEquals("\u001b[1;2C".toByteArray(),
            bytes(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_LEFT_ON))
        assertArrayEquals("\u001b[24;8~".toByteArray(),
            bytes(KeyEvent.KEYCODE_F12, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON))
        assertArrayEquals("\u001b[3;5~".toByteArray(),
            bytes(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON))
        assertArrayEquals("\u001b[Z".toByteArray(),
            bytes(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        assertArrayEquals("\u001b\r".toByteArray(),
            bytes(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.META_ALT_ON))
    }

    @Test fun stickyControlCombinesWithThePhysicalModifier() {
        assertArrayEquals("\u001b[1;7A".toByteArray(),
            bytes(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_ALT_ON, sticky = true))
        assertArrayEquals("\u001b[1;5A".toByteArray(),
            bytes(KeyEvent.KEYCODE_DPAD_UP, 0, sticky = true))
        assertArrayEquals("\u001bOA".toByteArray(), bytes(KeyEvent.KEYCODE_DPAD_UP, 0))
    }
}
