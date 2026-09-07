package com.zerotoship.z2term.ui.terminal.input

import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 外付け (物理) キーボードが繋がっているかを見る。
 *
 * 繋がっている間は内蔵キーボードを畳んで画面を広く使う。⚠ **畳んでも打つ手段は消えない** —
 * 物理キーは [TerminalInputView.onKeyDown] と `GuiInputView` が拾うので、キーボード領域の
 * 有無とは無関係に PTY / GUI へ届く。手で開き直すときは ⌨ かトグルバーから。
 */

/**
 * 1 台ぶんの判定。⚠ 3 つとも見ないと数え間違える:
 * - [isVirtual]: 画面上のキーボードは「Virtual」という仮想デバイス (id = -1) として
 *   **常に 1 台居る**。除かないと「いつも繋がっている」になる。
 * - [keyboardType]: 音量キーやゲームパッドも `SOURCE_KEYBOARD` を名乗るが文字は打てない
 *   (`KEYBOARD_TYPE_NON_ALPHABETIC`)。畳んでよいのは**文字が打てる相手が居るとき**だけ。
 * - [sources]: `SOURCE_KEYBOARD` のビットが立っていること。
 */
fun isPhysicalKeyboard(sources: Int, keyboardType: Int, isVirtual: Boolean): Boolean =
    !isVirtual &&
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
        keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

/** いま、文字が打てる外付けキーボードが 1 台でも繋がっているか。 */
fun physicalKeyboardConnected(): Boolean = InputDevice.getDeviceIds().any { id ->
    val device = InputDevice.getDevice(id) ?: return@any false
    isPhysicalKeyboard(device.sources, device.keyboardType, device.isVirtual)
}

/**
 * [physicalKeyboardConnected] を抜き差しに追従させた形。
 *
 * ⚠ `Configuration.keyboard` ではなく [InputManager] のリスナーで見る。Configuration が持つのは
 * QWERTY / 12 キー / 無しという粗い分類だけで、**どのデバイスがそう名乗ったのか**が分からない
 * (文字の打てない入力機器でも QWERTY 側に数えられることがある)。1 台ずつ見れば
 * [isPhysicalKeyboard] の 3 条件で選り分けられる。
 */
@Composable
fun rememberPhysicalKeyboardConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(physicalKeyboardConnected()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(InputManager::class.java)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { connected = physicalKeyboardConnected() }
            override fun onInputDeviceRemoved(deviceId: Int) { connected = physicalKeyboardConnected() }
            // 同じ機器が名乗り直すことがある (キーボード付きカバーの開閉など)。
            override fun onInputDeviceChanged(deviceId: Int) { connected = physicalKeyboardConnected() }
        }
        manager?.registerInputDeviceListener(listener, null)
        // 初期値を取ってから登録し終えるまでの隙間で抜き差しされていても拾い直す。
        connected = physicalKeyboardConnected()
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }
    return connected
}
