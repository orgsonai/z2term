package com.zerotoship.z2term.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ShakeDetector] の shake 判定と debounce を具体例で検証する (A6 `z2-when sensor:shake`)。 */
class ShakeDetectorTest {

    // 重力 g ≒ 9.81 m/s²。静止時は合成加速度 ≒ 1g。
    private val rest = 9.81f

    @Test fun restingDoesNotShake() {
        val d = ShakeDetector()
        assertFalse(d.onSample(0f, 0f, rest, 0L))
        assertFalse(d.onSample(0f, 0f, rest, 100L))
    }

    @Test fun strongJoltFires() {
        val d = ShakeDetector()
        // 3g 相当 (既定しきい値 2.7g 超) → 発火。
        assertTrue(d.onSample(0f, 0f, 3f * rest, 0L))
    }

    @Test fun debounceSuppressesRapidRefire() {
        val d = ShakeDetector()
        assertTrue(d.onSample(0f, 0f, 3f * rest, 0L))
        // 既定 1000ms 未満の連続サンプルは同じ振りとみなし発火させない。
        assertFalse(d.onSample(0f, 0f, 3f * rest, 300L))
        assertFalse(d.onSample(0f, 0f, 3f * rest, 900L))
        // 1000ms を超えたら次の振りとして発火。
        assertTrue(d.onSample(0f, 0f, 3f * rest, 1200L))
    }

    @Test fun customThreshold() {
        val d = ShakeDetector(thresholdG = 1.5f)
        assertFalse(d.onSample(0f, 0f, rest, 0L))       // 1g は下回る
        assertTrue(d.onSample(0f, 0f, 2f * rest, 100L)) // 2g で超える
    }
}
