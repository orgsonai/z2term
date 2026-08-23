package com.zerotoship.z2term.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 定期バックアップ (0.8.386) の「次はいつか」と「どれを消すか」を具体例で確かめる。
 *
 * **なぜテストが要るか**: どちらも**間違っても静か**な計算だから。次回時刻がずれても
 * 「そういうものかな」で済んでしまい、世代整理が行き過ぎても気付くのは消えた後になる。
 * タイムゾーンでずれないよう、判定は常に UTC で組む ([CronScheduleTest] と同じ作法)。
 */
class AutoBackupScheduleTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** UTC の年月日時分 (月は 1-12) からエポックミリ秒。 */
    private fun at(y: Int, mon: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(y, mon - 1, d, h, min, 0)
        }.timeInMillis

    private fun next(
        interval: String,
        from: Long,
        dayOfWeek: Int = Calendar.SUNDAY,
        dayOfMonth: Int = 1,
        hour: Int = 3,
        minute: Int = 0
    ): Long = AutoBackup.nextAt(interval, dayOfWeek, dayOfMonth, hour, minute, from, utc)

    @Test fun daily_sameDayIfBefore() {
        // 2026-08-23 の 1:00 にいるなら、その日の 3:00。
        assertEquals(
            at(2026, 8, 23, 3, 0),
            next(AutoBackup.INTERVAL_DAILY, at(2026, 8, 23, 1, 0))
        )
    }

    @Test fun daily_nextDayIfAtOrAfter() {
        // ちょうど 3:00 は「厳密に後」ではないので翌日へ (同じ時刻に二度走らせない)。
        assertEquals(
            at(2026, 8, 24, 3, 0),
            next(AutoBackup.INTERVAL_DAILY, at(2026, 8, 23, 3, 0))
        )
    }

    @Test fun weekly_findsTheNextMatchingDay() {
        // 2026-08-23 は日曜。月曜指定なら翌日 8/24。
        assertEquals(
            at(2026, 8, 24, 3, 0),
            next(AutoBackup.INTERVAL_WEEKLY, at(2026, 8, 23, 12, 0), dayOfWeek = Calendar.MONDAY)
        )
    }

    @Test fun weekly_todayIfStillBeforeTheTime() {
        // 日曜の 1:00 に日曜指定 = 今日の 3:00 (来週まで飛ばさない)。
        assertEquals(
            at(2026, 8, 23, 3, 0),
            next(AutoBackup.INTERVAL_WEEKLY, at(2026, 8, 23, 1, 0), dayOfWeek = Calendar.SUNDAY)
        )
    }

    @Test fun weekly_wrapsToNextWeekWhenTheTimeHasPassed() {
        // 日曜の 12:00 に日曜指定 = 次の日曜 (8/30)。
        assertEquals(
            at(2026, 8, 30, 3, 0),
            next(AutoBackup.INTERVAL_WEEKLY, at(2026, 8, 23, 12, 0), dayOfWeek = Calendar.SUNDAY)
        )
    }

    @Test fun monthly_nextMonthWhenTheDayHasPassed() {
        // 毎月 1 日。8/23 にいるなら 9/1。
        assertEquals(
            at(2026, 9, 1, 3, 0),
            next(AutoBackup.INTERVAL_MONTHLY, at(2026, 8, 23, 12, 0), dayOfMonth = 1)
        )
    }

    @Test fun monthly_clampsTheDayTo28() {
        // 31 日は無い月があるので 28 に丸める。丸めた結果 2 月でも必ず存在する。
        assertEquals(
            at(2026, 2, 28, 3, 0),
            next(AutoBackup.INTERVAL_MONTHLY, at(2026, 2, 1, 12, 0), dayOfMonth = 31)
        )
    }

    @Test fun stale_keepsTheNewestAndDropsTheRest() {
        val names = listOf(
            "z2term-auto-20260820-0300.zip",
            "z2term-auto-20260821-0300.zip",
            "z2term-auto-20260822-0300.zip",
            "z2term-auto-20260823-0300.zip"
        )
        assertEquals(
            listOf("z2term-auto-20260821-0300.zip", "z2term-auto-20260820-0300.zip"),
            AutoBackup.stale(names, keep = 2)
        )
    }

    @Test fun stale_neverTouchesFilesMadeByHand() {
        // ⚠ ここがこの機能の一番の約束。手で作った `z2term-backup-*` と、無関係なファイルは
        // 同じフォルダにあっても消さない。
        val names = listOf(
            "z2term-backup-20260101-1200.zip",
            "z2term-auto-20260820-0300.zip",
            "z2term-auto-20260823-0300.zip",
            "memo.txt"
        )
        assertEquals(listOf("z2term-auto-20260820-0300.zip"), AutoBackup.stale(names, keep = 1))
    }

    @Test fun stale_keepIsAtLeastOne() {
        // 0 を渡されても最後の 1 本は残す (全部消える指定を作らない)。
        val names = listOf("z2term-auto-20260822-0300.zip", "z2term-auto-20260823-0300.zip")
        assertEquals(listOf("z2term-auto-20260822-0300.zip"), AutoBackup.stale(names, keep = 0))
    }
}
