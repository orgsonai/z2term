package com.zerotoship.z2term.ime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 物理キーボードの**かなモード**が ON か。
 *
 * ⚠ **状態を見せる場所は端末画面のツールバー**（⌨ が「あ」に変わる）。入力メソッド
 * ([Z2ImeService]) と端末画面は同じプロセスなので、間に挟むのはこの 1 つで足りる。
 *
 * ⛔ **入力メソッドの画面には出さない**（0.8.530・利用者の指摘）。候補バーの席に印を置くと
 * **端末の文字と重なる** — 入力メソッドの窓は端末の上に浮くので、印の下にある行が読めなくなる。
 */
object KanaModeState {

    private val _enabled = MutableStateFlow(false)

    /** ON なら物理キーボードの打鍵がかなへ変換される。 */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(value: Boolean) {
        _enabled.value = value
    }
}
