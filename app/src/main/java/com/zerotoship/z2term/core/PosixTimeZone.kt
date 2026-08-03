package com.zerotoship.z2term.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransitionRule

/**
 * Android のタイムゾーンを、**distro の中でも通じる形**の `TZ` 文字列にする。
 *
 * **なぜ要るか**: distro の中は `TZ` も `/etc/localtime` も無い状態で動いており、`date` は
 * **常に UTC** を返していた。相対の指定 (`30m 後`) は差分の計算なのでずれないが、
 * **`18:30` のような絶対時刻は 9 時間 (JST の場合) ずれて予約され、一覧も同じだけずれて出る**
 * (利用者の報告)。時計が合っていない環境で時刻の自動化を組むことはできない。
 *
 * **なぜゾーン名 (`Asia/Tokyo`) をそのまま渡さないか**: ⚠ それを解決できるのは
 * **tzdata が入っている distro だけ**で、無ければ libc は黙って UTC へ落ちる
 * (Alpine の `tzdata` は既定で入っていない)。パッケージの有無で時計が狂うのは避けたいので、
 * **オフセットと夏時間の規則を書き下した POSIX 形式**を渡す — これは tzdata を読まずに
 * libc だけで解釈でき、glibc でも musl でも busybox の `date` でも同じに効く。
 *
 * 生成する形 (例):
 * ```
 * Asia/Tokyo       → <+09>-9
 * Asia/Kolkata     → <+0530>-5:30
 * America/New_York → <-05>5<-04>,M3.2.0/2,M11.1.0/2
 * UTC              → <+00>0
 * ```
 *
 * ⚠ **符号は日常の言い方と逆**。`UTC+9` は POSIX では `-9` と書く (「その時刻に足すと UTC」)。
 * ⚠ **略称は `<+09>` の形にする**。`JST` のような綴りは端末のロケール次第で
 * `GMT+09:00` のような POSIX では読めない文字列になることがあり、そうなると `TZ` 全体が
 * 無視されて UTC へ落ちる。数字表記なら国を問わず必ず読める。
 */
object PosixTimeZone {

    /**
     * [zone] の [at] 時点の規則を POSIX `TZ` 文字列にする。
     *
     * 夏時間は、**年に 2 回・曜日で決まる**というごく普通の形 (`M月.週.曜日`) のときだけ書く。
     * ⚠ それ以外 (規則が無い / 日付固定 / 年 3 回以上) は**夏時間なしとして今のオフセットを書く**。
     * 表せない規則を無理に書くより、⚠ **いま正しい時刻**を返すほうが実害が小さい
     * (切り替え日をまたいでも、次にタブを開けば作り直される)。
     */
    fun of(zone: ZoneId, at: Instant): String {
        val rules = zone.rules
        val standard = rules.getStandardOffset(at)
        val transitions = rules.transitionRules

        // 夏時間の規則が「開始と終了の 2 本」でないものは、今のオフセットだけを書いて終わる。
        if (rules.isFixedOffset || transitions.size != 2) {
            val now = rules.getOffset(at)
            return name(now) + posixOffset(now)
        }

        val start = transitions.firstOrNull { it.offsetAfter.totalSeconds > it.offsetBefore.totalSeconds }
        val end = transitions.firstOrNull { it.offsetAfter.totalSeconds < it.offsetBefore.totalSeconds }
        if (start == null || end == null) {
            val now = rules.getOffset(at)
            return name(now) + posixOffset(now)
        }
        val daylight = start.offsetAfter
        val startSpec = ruleSpec(start, standard) ?: return name(standard) + posixOffset(standard)
        val endSpec = ruleSpec(end, standard) ?: return name(standard) + posixOffset(standard)

        return name(standard) + posixOffset(standard) +
            name(daylight) + posixOffset(daylight) +
            ",$startSpec,$endSpec"
    }

    /** その端末のいまの設定で。 */
    fun current(): String = of(ZoneId.systemDefault(), Instant.now())

    /**
     * 切り替え規則を `M<月>.<週>.<曜日>/<時刻>` にする。表せない形なら null。
     *
     * ⚠ **時刻は「切り替え直前のその土地の時刻」で書く** — POSIX がそう定めている
     * (夏時間が終わる側は、夏時間のままの時計で何時か、を書く)。`ZoneOffsetTransitionRule` の
     * 時刻は壁時計 / 標準時 / UTC のどれで書かれているか (`timeDefinition`) がまちまちなので、
     * ⚠ そのまま書き写すと**切り替えが 1 時間ずれる**。
     */
    private fun ruleSpec(rule: ZoneOffsetTransitionRule, standard: ZoneOffset): String? {
        val dow = rule.dayOfWeek ?: return null   // 日付固定の規則は M 形式で書けない
        val dom = rule.dayOfMonthIndicator
        // 規則は「その日以降で最初の <曜日>」の形。POSIX の週番号 (1..4、5 = 最終) に直す。
        // ⚠ **最終週を 5 と書くこと**。`(dom - 1) / 7 + 1` だけで数えると、3 月 25 日起点の
        // 「最終日曜」(英国など) が第 4 週になり、⚠ **切り替えが 1 週間早まる**。
        // 起点から 6 日以内に月末が来るなら、そこで見つかる <曜日> は必ずその月の最後になる。
        val lastWeek = dom < 0 || dom + 6 >= rule.month.length(false)
        val week = if (lastWeek) 5 else minOf((dom - 1) / 7 + 1, 5)
        // 日曜が 0。java.time は月曜が 1・日曜が 7。
        val posixDow = dow.value % 7

        val seconds = rule.localTime.toSecondOfDay() +
            when (rule.timeDefinition) {
                // UTC 基準で書かれている → 切り替え直前のオフセットを足して現地時刻にする。
                ZoneOffsetTransitionRule.TimeDefinition.UTC -> rule.offsetBefore.totalSeconds
                // 標準時基準 → 直前が夏時間なら、その差だけ進める。
                ZoneOffsetTransitionRule.TimeDefinition.STANDARD ->
                    rule.offsetBefore.totalSeconds - standard.totalSeconds
                // 壁時計基準 = そのまま。
                else -> 0
            }
        return "M${rule.month.value}.$week.$posixDow/${hms(seconds)}"
    }

    /** POSIX のオフセット表記 (符号が逆・秒は落とす)。 */
    private fun posixOffset(offset: ZoneOffset): String {
        val total = -offset.totalSeconds
        val sign = if (total < 0) "-" else ""
        val abs = kotlin.math.abs(total)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return if (m == 0) "$sign$h" else "$sign$h:${pad(m)}"
    }

    /** 略称のかわりに置く数字表記 (`<+09>` / `<-0530>`)。 */
    private fun name(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val abs = kotlin.math.abs(total)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return if (m == 0) "<$sign${pad(h)}>" else "<$sign${pad(h)}${pad(m)}>"
    }

    /** 切り替え時刻。POSIX の既定は 2:00 なので、そのときは時だけ書く形に合わせる。 */
    private fun hms(secondOfDay: Int): String {
        // 日をまたぐ指定 (24:00 や負) もそのまま書ける (POSIX は -167..167 時を許す)。
        val sign = if (secondOfDay < 0) "-" else ""
        val abs = kotlin.math.abs(secondOfDay)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        val s = abs % 60
        return when {
            m == 0 && s == 0 -> "$sign$h"
            s == 0 -> "$sign$h:${pad(m)}"
            else -> "$sign$h:${pad(m)}:${pad(s)}"
        }
    }

    private fun pad(v: Int): String = if (v < 10) "0$v" else "$v"
}
