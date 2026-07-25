package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 発火記録 (`~/.z2term/when/.fired`) の行づくりと読み戻しの検証。
 *
 * この記録は「なぜ動かないのか」「さっき何が走ったのか」を追う唯一の手がかりなので、
 * **1 行が壊れると調べる手段そのものが失われる**。TSV の区切りを守れているか、
 * 古い形式や壊れた行が混ざっても落ちないかを押さえる。
 */
class WhenFiredLogTest {

    @Test
    fun lineIsTabSeparated() {
        assertEquals(
            "2026-07-25T21:40:12\tw1\tevent:screen_on\trun",
            WhenManager.firedLine("2026-07-25T21:40:12", "w1", "event:screen_on", "run")
        )
    }

    @Test
    fun tabsAndNewlinesInValuesDoNotBreakTheRow() {
        // トリガー文字列はユーザーが書いたファイル由来。タブや改行が混ざっても
        // 1 発火 = 1 行を守る (崩れると以降の行がすべてズレて読めなくなる)。
        val line = WhenManager.firedLine("t", "w1", "event:a\tb\nc", "run")
        assertEquals(1, line.lines().size)
        assertEquals(3, line.count { it == '\t' })
        assertEquals("event:a b c", WhenManager.parseFired(line)!!.trigger)
    }

    @Test
    fun parseRoundTrips() {
        val line = WhenManager.firedLine("2026-07-25T09:00:00", "w42", "time:daily=09:00", "paused")
        val got = WhenManager.parseFired(line)!!
        assertEquals("2026-07-25T09:00:00", got.time)
        assertEquals("w42", got.ruleId)
        assertEquals("time:daily=09:00", got.trigger)
        assertEquals("paused", got.status)
    }

    @Test
    fun brokenLineIsIgnored() {
        // 古い形式や書きかけの行が混ざっても、そこだけ捨てて残りを読めること。
        assertNull(WhenManager.parseFired(""))
        assertNull(WhenManager.parseFired("2026-07-25T09:00:00\tw1"))
    }

    @Test
    fun trimKeepsNewestAndDropsOldest() {
        val lines = (1..10).map { "line$it" }
        assertEquals(listOf("line8", "line9", "line10"), WhenManager.trimFired(lines, 3))
    }

    @Test
    fun trimSkipsBlankLines() {
        // 末尾の空行を数に含めると、実際に残る件数が指定より減ってしまう。
        assertEquals(
            listOf("a", "b"),
            WhenManager.trimFired(listOf("a", "", "b", ""), 2)
        )
    }
}
