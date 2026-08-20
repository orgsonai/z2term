package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画面のルール → 端末の `z2-when` 1 行 ([WhenCommandLine]・0.8.375)。
 *
 * ⚠ ここが緩いと、**画面に出ている 1 行を貼ったら別のルールができる**。見せるだけの表示に
 * 見えて、実際にはコピーして端末で使われるものなので、クォートの要否を具体例で固定する。
 */
class WhenCommandLineTest {

    private fun rule(
        trigger: String = "charge:start",
        run: String = "~/.z2term/macros/backup.sh",
        name: String = "",
        condition: String = "",
        conditionAny: String = "",
        otherwise: String = "",
        cooldown: String = "",
        between: String = "",
        days: String = "",
    ) = WhenRule(
        id = "w1", trigger = trigger, run = run, condition = condition, cooldown = cooldown,
        between = between, days = days, name = name, conditionAny = conditionAny,
        otherwise = otherwise,
    )

    @Test
    fun `最小のルールはトリガーと run だけ`() {
        assertEquals(
            "z2-when charge:start run ~/.z2term/macros/backup.sh",
            WhenCommandLine.of(rule()),
        )
    }

    @Test
    fun `絞り込みは usage と同じ並びで付く`() {
        val line = WhenCommandLine.of(
            rule(
                name = "夜のバックアップ",
                condition = "wifi",
                conditionAny = "charging,plug=ac",
                otherwise = "z2-notify skipped",
                cooldown = "30m",
                between = "22:00-07:00",
                days = "mon-fri",
            )
        )
        assertEquals(
            "z2-when charge:start name=夜のバックアップ if=wifi if_any=charging,plug=ac " +
                "else='z2-notify skipped' cooldown=30m between=22:00-07:00 days=mon-fri " +
                "run ~/.z2term/macros/backup.sh",
            line,
        )
    }

    @Test
    fun `glob と比較記号を含むトリガーはクォートする`() {
        // 裸だと `*` が glob、`>` がリダイレクトに読まれ、貼った瞬間に別の意味になる。
        assertEquals(
            "z2-when 'event:ringer_*' run x.sh",
            WhenCommandLine.of(rule(trigger = "event:ringer_*", run = "x.sh")),
        )
        assertEquals(
            "z2-when 'sensor:light>50' run x.sh",
            WhenCommandLine.of(rule(trigger = "sensor:light>50", run = "x.sh")),
        )
        assertEquals(
            "z2-when 'time:cron=0 3 * * *' run x.sh",
            WhenCommandLine.of(rule(trigger = "time:cron=0 3 * * *", run = "x.sh")),
        )
    }

    @Test
    fun `空白や引用符を含むコマンドは 1 引数にまとめる`() {
        // run の後ろは "$*" として読まれるので、まとめてクォートしないと引用符が落ちる。
        assertEquals(
            """z2-when boot run 'z2-toast "hello world"'""",
            WhenCommandLine.of(rule(trigger = "boot", run = """z2-toast "hello world"""")),
        )
        // 中の ' は '\'' で閉じ直す。
        assertEquals(
            """z2-when boot run 'echo '\''hi'\'''""",
            WhenCommandLine.of(rule(trigger = "boot", run = "echo 'hi'")),
        )
    }

    @Test
    fun `否定を含む条件はクォートする`() {
        // `!` は対話シェルでヒストリ展開に読まれることがある。
        assertEquals(
            "z2-when boot if='wifi,!screen' run x.sh",
            WhenCommandLine.of(rule(trigger = "boot", run = "x.sh", condition = "wifi,!screen")),
        )
    }
}
