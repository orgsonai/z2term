package com.zerotoship.z2term.service

import kotlin.math.sqrt

/**
 * 加速度センサーのサンプル列から「振った (shake)」ジェスチャを検出する純ロジック
 * (A6 `z2-when` の `sensor:shake` トリガー・stage2)。Android に依存しないのでユニットテスト可能。
 *
 * 合成加速度が [thresholdG] (重力 g の倍数) を超えたら 1 回の shake とみなし、[minIntervalMs] の
 * 間は次を発火させない (1 回の振りが連続サンプルで多重発火しないようにする debounce)。
 *
 * **既定値は実機の誤発火から決めた値**: 当初の 2.7g / 1 秒では、端末をポケットに入れて**歩いているだけで
 * 連続発火した** (2026-07-24 の実機検証で 3.5 時間に 255 回・発火間隔は debounce の 1〜2 秒に張り付き)。
 * 歩行の加速度ピークを外し、意識して振ったときだけ拾うために 4.0g / 3 秒へ引き上げてある。
 * **下げるときは「歩いて誤発火しないか」を実機で必ず確認すること。**
 */
class ShakeDetector(
    private val thresholdG: Float = 4.0f,
    private val minIntervalMs: Long = 3000L,
) {
    // 初回は null (未発火)。差分演算のオーバーフローを避けるため 0 等の番兵値は使わない。
    private var lastShakeMs: Long? = null

    /** 1 サンプル ([x],[y],[z] は m/s²、[tMs] は単調増加のミリ秒) を渡す。shake なら true。 */
    fun onSample(x: Float, y: Float, z: Float, tMs: Long): Boolean {
        val gForce = sqrt(x * x + y * y + z * z) / GRAVITY
        if (gForce <= thresholdG) return false
        val last = lastShakeMs
        if (last == null || tMs - last > minIntervalMs) {
            lastShakeMs = tMs
            return true
        }
        return false
    }

    private companion object {
        const val GRAVITY = 9.80665f
    }
}
