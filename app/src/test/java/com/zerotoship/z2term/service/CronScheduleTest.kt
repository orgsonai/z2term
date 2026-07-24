package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * [CronSchedule] の次回発火計算を、時刻がずれない固定タイムゾーン (UTC) で具体例検証する
 * (A6 `z2-when time:cron`)。ローカルタイムゾーンに依存しないよう常に UTC で組む。
 */
class CronScheduleTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** UTC の年月日時分 (月は 1-12) からエポックミリ秒。 */
    private fun at(y: Int, mon: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(y, mon - 1, d, h, min, 0)
        }.timeInMillis

    private fun next(expr: String, from: Long): Long = CronSchedule.nextAfter(expr, from, utc)

    @Test fun everyMinute_advancesOneMinute() {
        // 2026-07-24 10:30:00 の次は 10:31。
        assertEquals(at(2026, 7, 24, 10, 31), next("* * * * *", at(2026, 7, 24, 10, 30)))
    }

    @Test fun dailyAtThree_sameDayIfBefore() {
        // 毎日 3:00。まだ 3:00 前 (2:00) なら同日 3:00。
        assertEquals(at(2026, 7, 24, 3, 0), next("0 3 * * *", at(2026, 7, 24, 2, 0)))
    }

    @Test fun dailyAtThree_nextDayIfAtOrAfter() {
        // ちょうど 3:00 は「厳密に後」なので翌日 3:00。
        assertEquals(at(2026, 7, 25, 3, 0), next("0 3 * * *", at(2026, 7, 24, 3, 0)))
    }

    @Test fun stepMinutes_everyFifteen() {
        // */15 は 0,15,30,45。10:07 の次は 10:15。
        assertEquals(at(2026, 7, 24, 10, 15), next("*/15 * * * *", at(2026, 7, 24, 10, 7)))
    }

    @Test fun list_andRange() {
        // 分が 0 か 30、時が 9-17 の範囲。9:45 の次は 10:00。
        assertEquals(at(2026, 7, 24, 10, 0), next("0,30 9-17 * * *", at(2026, 7, 24, 9, 45)))
    }

    @Test fun dayOfWeek_sunday_bothZeroAndSeven() {
        // 2026-07-24 は金曜。日曜 (0) 0:00 は 07-26。
        val expected = at(2026, 7, 26, 0, 0)
        assertEquals(expected, next("0 0 * * 0", at(2026, 7, 24, 10, 0)))
        // 7 も日曜として同じ結果になる。
        assertEquals(expected, next("0 0 * * 7", at(2026, 7, 24, 10, 0)))
    }

    @Test fun domAndDow_orSemantics() {
        // 日=1 または 曜日=月。2026-07-24(金) の次は 07-27(月)。1 日より月曜が先に来る。
        assertEquals(at(2026, 7, 27, 0, 0), next("0 0 1 * 1", at(2026, 7, 24, 10, 0)))
    }

    @Test fun monthRollover_febFirst() {
        // 2 月 1 日 0:00。12 月半ばから見ると翌年 2 月。
        assertEquals(at(2027, 2, 1, 0, 0), next("0 0 1 2 *", at(2026, 12, 15, 12, 0)))
    }

    @Test fun impossibleDate_returnsZero() {
        // 2 月 30 日は存在しない → 探索上限まで見つからず 0。
        assertEquals(0L, next("0 0 30 2 *", at(2026, 1, 1, 0, 0)))
    }

    @Test fun invalidExpressions_returnZeroAndInvalid() {
        listOf(
            "",                 // 空
            "* * * *",          // フィールド不足
            "* * * * * *",      // フィールド過多
            "60 * * * *",       // 分が範囲外
            "* 24 * * *",       // 時が範囲外
            "* * 0 * *",        // 日は 1 始まり
            "* * * 13 *",       // 月が範囲外
            "* * * * 8",        // 曜日が範囲外
            "5-1 * * * *",      // 逆範囲
            "*/0 * * * *",      // ステップ 0
            "a * * * *",        // 非数値
        ).forEach { expr ->
            assertFalse("should be invalid: '$expr'", CronSchedule.isValid(expr))
            assertEquals("invalid must yield 0: '$expr'", 0L, next(expr, at(2026, 7, 24, 0, 0)))
        }
    }

    @Test fun valid_isValidTrue() {
        assertTrue(CronSchedule.isValid("0 3 * * *"))
        assertTrue(CronSchedule.isValid("*/15 9-17 * * 1-5"))
        assertTrue(CronSchedule.isValid("0,30 0 1,15 * *"))
    }
}
