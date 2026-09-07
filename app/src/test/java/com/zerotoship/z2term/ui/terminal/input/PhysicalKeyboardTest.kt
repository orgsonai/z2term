package com.zerotoship.z2term.ui.terminal.input

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外付けキーボードの判定 ([isPhysicalKeyboard])。
 *
 * ⚠ ここを緩めると**内蔵キーボードが勝手に消える**という形でしか現れず、「キーボードが
 * 出てこない」という報告から原因へ辿り着けない。3 条件を具体例で固定しておく。
 */
class PhysicalKeyboardTest {

    @Test
    fun usbOrBluetoothKeyboardCounts() {
        assertTrue(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = false
            )
        )
    }

    @Test
    fun theVirtualKeyboardNeverCounts() {
        // 画面上のキーボードは「Virtual」(id = -1) として常に 1 台居る。数えると常時 ON になる。
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = true
            )
        )
    }

    @Test
    fun keysThatCannotTypeDoNotCount() {
        // 音量キーやゲームパッドも SOURCE_KEYBOARD を名乗るが、文字は打てない。
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
                isVirtual = false
            )
        )
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
                isVirtual = false
            )
        )
    }

    @Test
    fun aMouseAloneDoesNotCount() {
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_MOUSE,
                keyboardType = InputDevice.KEYBOARD_TYPE_NONE,
                isVirtual = false
            )
        )
    }

    @Test
    fun aKeyboardThatAlsoReportsOtherSourcesStillCounts() {
        // トラックパッド一体型のように、複数の source を立てて名乗る機器がある。
        assertTrue(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = false
            )
        )
    }
}
