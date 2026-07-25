package com.zerotoship.z2term.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * タブの「見ていない間に終わった」印 ([nextEndedIds]) の検証。
 *
 * ホーム画面ならぬタブバーの表示は**動かしてみないと分からない**うえ、
 * 「終わったのに印が出ない」「終わっていないのに ✓ が出る」はどちらも気付きにくい。
 * 判断部分だけ切り出して、規則を具体例で固定する。
 */
class TabMarkTest {

    @Test
    fun busyToIdleMarksEnded() {
        // 裏のタブでビルドが終わった → 次に見たとき ✓ が付いている。
        assertEquals(
            setOf("t2"),
            nextEndedIds(prevEnded = emptySet(), prevBusy = setOf("t2"), nowBusy = emptySet(), activeId = "t1")
        )
    }

    @Test
    fun activeTabNeverKeepsTheMark() {
        // 開いているタブに印は出さない (見た時点で役目が終わる)。
        assertEquals(
            emptySet<String>(),
            nextEndedIds(prevEnded = setOf("t1"), prevBusy = setOf("t1"), nowBusy = emptySet(), activeId = "t1")
        )
    }

    @Test
    fun restartedTabDropsTheMark() {
        // ✓ が付いたタブでまた何か走り出したら、✓ は嘘になるので消す。
        assertEquals(
            emptySet<String>(),
            nextEndedIds(prevEnded = setOf("t2"), prevBusy = emptySet(), nowBusy = setOf("t2"), activeId = "t1")
        )
    }

    @Test
    fun stillRunningIsNotEnded() {
        // 動き続けている間は ✓ を付けない (■ のまま)。
        assertEquals(
            emptySet<String>(),
            nextEndedIds(prevEnded = emptySet(), prevBusy = setOf("t2"), nowBusy = setOf("t2"), activeId = "t1")
        )
    }

    @Test
    fun markSurvivesUntilTheTabIsOpened() {
        // 一度付いた ✓ は、そのタブを開くまで残る (別のタブを見ている間は消えない)。
        var ended = nextEndedIds(emptySet(), setOf("t2"), emptySet(), activeId = "t1")
        repeat(3) { ended = nextEndedIds(ended, emptySet(), emptySet(), activeId = "t1") }
        assertEquals(setOf("t2"), ended)
        // t2 を開いた瞬間に消える。
        assertEquals(emptySet<String>(), nextEndedIds(ended, emptySet(), emptySet(), activeId = "t2"))
    }

    @Test
    fun severalTabsAreTrackedIndependently() {
        val ended = nextEndedIds(
            prevEnded = setOf("t2"),
            prevBusy = setOf("t3", "t4"),
            nowBusy = setOf("t4"),
            activeId = "t1"
        )
        assertEquals(setOf("t2", "t3"), ended)
    }
}
