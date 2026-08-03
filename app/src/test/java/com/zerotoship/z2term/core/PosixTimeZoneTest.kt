package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * distro へ渡す `TZ` 文字列 ([PosixTimeZone]) の検証。
 *
 * ⚠ **ここが間違っていると、時計が静かにずれる**。端末の上では「予約した時刻に鳴らない」
 * としか見えず、原因に辿り着けない (実際 UTC 固定だったときは 9 時間ずれていた)。
 * 代表的なゾーンについて、**出来上がりの文字列**と**その文字列が指す時刻**の両方を押さえる。
 */
class PosixTimeZoneTest {

    private val summer: Instant = ZonedDateTime.parse("2026-07-15T12:00:00Z").toInstant()
    private val winter: Instant = ZonedDateTime.parse("2026-01-15T12:00:00Z").toInstant()

    private fun tz(zone: String, at: Instant = summer) =
        PosixTimeZone.of(ZoneId.of(zone), at)

    /** 夏時間の無いところは、オフセット 1 つだけの短い形になる。 */
    @Test
    fun zonesWithoutDaylightSavingAreJustAnOffset() {
        // ⚠ 符号は日常の言い方と逆。UTC+9 は POSIX では -9。
        assertEquals("<+09>-9", tz("Asia/Tokyo"))
        assertEquals("<+08>-8", tz("Asia/Shanghai"))
        assertEquals("<-03>3", tz("America/Sao_Paulo"))
        assertEquals("<+00>0", tz("UTC"))
    }

    /** 30 分・45 分ずれる国も書ける（分は捨てない）。 */
    @Test
    fun halfHourZonesKeepTheirMinutes() {
        assertEquals("<+0530>-5:30", tz("Asia/Kolkata"))
        assertEquals("<+0545>-5:45", tz("Asia/Kathmandu"))
    }

    /** 夏時間があるところは、標準時 / 夏時間 / 切り替え規則の 3 つを書く。 */
    @Test
    fun daylightSavingIsWrittenAsMonthWeekDay() {
        // 3 月の第 2 日曜 2 時に始まり、11 月の第 1 日曜 2 時に戻る。
        assertEquals("<-05>5<-04>4,M3.2.0/2,M11.1.0/2", tz("America/New_York"))
        // 3 月の最終日曜 1 時 (UTC 指定なので標準時では 1 時) に始まり、10 月の最終日曜に戻る。
        assertEquals("<+00>0<+01>-1,M3.5.0/1,M10.5.0/2", tz("Europe/London"))
        // 南半球は「10 月に始まって 4 月に終わる」= 開始月のほうが後ろに来る。
        assertTrue(tz("Australia/Sydney").startsWith("<+10>-10<+11>-11,M10."))
    }

    /**
     * ⚠ **書いた文字列が指す時刻が、本物と一致すること**。
     *
     * 形が合っていても中身が 1 時間ずれていては意味がないので、生成した文字列を
     * `TimeZone` に読ませ直して、夏と冬の両方で本物のゾーンと同じオフセットになるか確かめる。
     */
    @Test
    fun theStringResolvesToTheSameOffsetAsTheRealZone() {
        listOf(
            "Asia/Tokyo", "Asia/Kolkata", "UTC", "Europe/Paris",
            "America/New_York", "America/Los_Angeles", "Europe/London", "Australia/Sydney"
        ).forEach { id ->
            val real = ZoneId.of(id)
            listOf(summer, winter).forEach { at ->
                val spec = PosixTimeZone.of(real, at)
                val expected = real.rules.getOffset(at).totalSeconds
                val fromSpec = offsetOf(spec, at, real)
                assertEquals("$id ($at) の指すオフセットが違う: $spec", expected, fromSpec)
            }
        }
    }

    /** 生成した文字列から、その瞬間のオフセット（秒）を読み取る。 */
    private fun offsetOf(spec: String, at: Instant, zone: ZoneId): Int {
        // 夏時間の有無は本物のゾーンに聞き、対応する側のオフセットを文字列から取り出す。
        val inDst = zone.rules.isDaylightSavings(at)
        val parts = spec.substringBefore(",")
        val std = parts.substringAfter(">").substringBefore("<")
        val dst = if (parts.count { it == '<' } > 1) parts.substringAfterLast(">") else null
        val picked = if (inDst && dst != null) dst else std
        return -parseOffsetSeconds(picked)
    }

    /** `-9` `5` `-5:30` を秒に。 */
    private fun parseOffsetSeconds(s: String): Int {
        val negative = s.startsWith("-")
        val body = s.removePrefix("-").removePrefix("+")
        val hm = body.split(":")
        val seconds = hm[0].toInt() * 3600 + (hm.getOrNull(1)?.toInt() ?: 0) * 60
        return if (negative) -seconds else seconds
    }

    /** 端末の設定をそのまま読む口も動くこと（値は環境しだいなので形だけ見る）。 */
    @Test
    fun currentReturnsSomethingUsable() {
        val spec = PosixTimeZone.current()
        assertTrue("空でない", spec.isNotEmpty())
        assertTrue("数字表記の略称で始まる: $spec", spec.startsWith("<"))
    }
}
