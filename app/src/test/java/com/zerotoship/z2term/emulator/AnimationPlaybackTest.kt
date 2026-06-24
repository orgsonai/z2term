package com.zerotoship.z2term.emulator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kitty graphics animation 再生 (段階 8) の state machine だけを検証する。
 *
 * Bitmap を要する経路 ([TerminalBuffer.addAnimationFrame] 経由) は unit test 環境では
 * `android.graphics.Bitmap` が構築不能なため到達できない。 ここでは frame 未登録時の
 * **静止挙動**: hasActiveAnimations が false、 advanceAnimations が false、
 * currentBitmap が null を返すことだけを固定する。
 * frame が複数並んで loop する経路は実機検証で確認する。
 */
class AnimationPlaybackTest {

    @Test
    fun hasActiveAnimationsIsFalseInitially() {
        val buf = TerminalBuffer(24, 80)
        assertFalse("初期は animation 無し", buf.hasActiveAnimations())
    }

    @Test
    fun advanceReturnsFalseWhenNoAnimations() {
        val buf = TerminalBuffer(24, 80)
        // 何度呼んでも state 変化なし (= false)。
        assertFalse(buf.advanceAnimations(nowMs = 0L))
        assertFalse(buf.advanceAnimations(nowMs = 1_000_000L))
    }

    @Test
    fun currentBitmapForUnknownIdReturnsNull() {
        val buf = TerminalBuffer(24, 80)
        // imageId=0 は常に null。 未登録 id も null (cache 未登録)。
        assertNull(buf.currentBitmap(imageId = 0))
        assertNull(buf.currentBitmap(imageId = 12345))
    }
}
