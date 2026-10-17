package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「見ていないタブの出力が止まった」の判定 ([AttentionState])。 */
class AttentionStateTest {

    @Test
    fun alertsOnceAfterOutputThenSilence() {
        val a = AttentionState(armBytes = 100)
        a.output(now = 0, bytes = 60)
        a.output(now = 1_000, bytes = 60)
        assertEquals(31_000L, a.dueAt(30_000))
        assertFalse(a.silent(now = 30_999, silenceMs = 30_000))
        assertTrue(a.silent(now = 31_000, silenceMs = 30_000))
        // 1 度知らせたら、止まったままでは鳴り続けない。
        assertFalse(a.silent(now = 120_000, silenceMs = 30_000))
        assertNull(a.dueAt(30_000))
    }

    @Test
    fun smallOutputDoesNotArm() {
        // プロンプトの描き直しのような少しの出力では「止まった」を出さない。
        val a = AttentionState(armBytes = 100)
        a.output(now = 0, bytes = 20)
        assertNull(a.dueAt(30_000))
        assertFalse(a.silent(now = 60_000, silenceMs = 30_000))
    }

    @Test
    fun newOutputPushesTheDeadline_andLookingDisarms() {
        val a = AttentionState(armBytes = 10)
        a.output(now = 0, bytes = 50)
        a.output(now = 20_000, bytes = 50)
        assertFalse(a.silent(now = 30_000, silenceMs = 30_000))
        assertEquals(50_000L, a.dueAt(30_000))
        a.seen()
        assertFalse(a.silent(now = 60_000, silenceMs = 30_000))
    }
}
