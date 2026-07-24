package com.zerotoship.z2term.service

import java.util.Calendar
import java.util.TimeZone

/**
 * 5 フィールドの cron 式 (`分 時 日 月 曜日`) を解釈し、次回発火時刻を求める純粋ロジック
 * (A6 `z2-when` の `time:cron=...` トリガー・stage 2)。Android に一切依存しないので
 * ユニットテストで具体例検証できる ([CronSchedule.nextAfter])。
 *
 * 対応するフィールドの書式 (標準的な cron のサブセット):
 *  - アスタリスク       すべて
 *  - アスタリスク/n     min から n 刻み
 *  - `a`                単一値
 *  - `a-b`              範囲
 *  - `a-b/n`            範囲を n 刻み
 *  - `a,b,c`            リスト (各要素は上のいずれか)
 *
 * フィールドの範囲: 分 0-59 / 時 0-23 / 日 1-31 / 月 1-12 / 曜日 0-7 (0 と 7 が日曜)。
 *
 * 日 (dom) と曜日 (dow) がどちらも `*` でない場合は **どちらか一致で発火** (標準 cron の仕様)。
 * 片方が `*` の場合はもう片方だけで判定する。
 */
object CronSchedule {

    /** パース済みの cron 式。各フィールドは許可値の集合として持つ。 */
    private class Parsed(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val doms: Set<Int>,
        val months: Set<Int>,
        val dows: Set<Int>,
        val domStar: Boolean,
        val dowStar: Boolean,
    )

    /** [expr] が解釈可能な 5 フィールド cron 式なら true。 */
    fun isValid(expr: String): Boolean = parse(expr) != null

    /**
     * [from] (エポックミリ秒) より**厳密に後**の、最初に cron 式が一致する時刻 (エポックミリ秒)。
     * 秒は 0 に切り捨てて分単位で探す。不正な式、または探索上限内に一致が無ければ 0。
     */
    fun nextAfter(expr: String, from: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val p = parse(expr) ?: return 0
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = from
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1) // from と同分は返さない (厳密に後)
        }
        // 探索上限。到達し得ない式 (例: 2 月 30 日) で無限ループしないための安全弁。
        // 分割スキップするので通常は数百回未満で決まる。
        var guard = 0
        while (guard++ < 500_000) {
            val month = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH は 0 始まり
            if (month !in p.months) {
                // 翌月 1 日 0:00 へ。日を先に 1 にしてから月を進める (末日での桁上がりを避ける)。
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.add(Calendar.MONTH, 1)
                continue
            }
            if (!dayMatches(cal, p)) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                continue
            }
            if (cal.get(Calendar.HOUR_OF_DAY) !in p.hours) {
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.set(Calendar.MINUTE, 0)
                continue
            }
            if (cal.get(Calendar.MINUTE) !in p.minutes) {
                cal.add(Calendar.MINUTE, 1)
                continue
            }
            return cal.timeInMillis
        }
        return 0
    }

    /** dom/dow の組み合わせ判定 (どちらも指定なら OR、片方 `*` ならもう片方だけ)。 */
    private fun dayMatches(cal: Calendar, p: Parsed): Boolean {
        val domOk = cal.get(Calendar.DAY_OF_MONTH) in p.doms
        // Calendar.DAY_OF_WEEK は SUNDAY=1..SATURDAY=7 → cron の 0(日)..6(土) へ。
        val dowOk = (cal.get(Calendar.DAY_OF_WEEK) - 1) in p.dows
        return when {
            p.domStar && p.dowStar -> true
            p.domStar -> dowOk
            p.dowStar -> domOk
            else -> domOk || dowOk
        }
    }

    private fun parse(expr: String): Parsed? {
        val fields = expr.trim().split(Regex("\\s+"))
        if (fields.size != 5) return null
        val minutes = parseField(fields[0], 0, 59) ?: return null
        val hours = parseField(fields[1], 0, 23) ?: return null
        val doms = parseField(fields[2], 1, 31) ?: return null
        val months = parseField(fields[3], 1, 12) ?: return null
        // 曜日は 7 も日曜として受け、0 に正規化する。
        val dowsRaw = parseField(fields[4], 0, 7) ?: return null
        val dows = dowsRaw.map { if (it == 7) 0 else it }.toSet()
        return Parsed(
            minutes = minutes, hours = hours, doms = doms, months = months, dows = dows,
            domStar = fields[2] == "*", dowStar = fields[4] == "*",
        )
    }

    /** 1 フィールドを許可値の集合へ。範囲外・書式不正は null。 */
    private fun parseField(field: String, min: Int, max: Int): Set<Int>? {
        val out = HashSet<Int>()
        for (term in field.split(',')) {
            if (term.isEmpty()) return null
            // ステップ `.../n` を分離。
            val slash = term.indexOf('/')
            val body = if (slash >= 0) term.substring(0, slash) else term
            val step = if (slash >= 0) term.substring(slash + 1).toIntOrNull()?.takeIf { it > 0 } ?: return null else 1
            val lo: Int
            val hi: Int
            when {
                body == "*" -> { lo = min; hi = max }
                body.contains('-') -> {
                    val dash = body.indexOf('-')
                    lo = body.substring(0, dash).toIntOrNull() ?: return null
                    hi = body.substring(dash + 1).toIntOrNull() ?: return null
                }
                else -> {
                    val v = body.toIntOrNull() ?: return null
                    // 単一値 + ステップ (`5/10`) は「5 から max まで step」とみなす。
                    lo = v; hi = if (slash >= 0) max else v
                }
            }
            if (lo < min || hi > max || lo > hi) return null
            var v = lo
            while (v <= hi) { out.add(v); v += step }
        }
        return if (out.isEmpty()) null else out
    }
}
