package com.zerotoship.z2term.proot

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生成シェルスクリプトに `trimMargin` のマージン文字 `|` が漏れていないことの回帰テスト。
 *
 * 元バグ: `z2MacroScript` の usage 部で、raw string 側が既に `|` を出している行に対して
 * `joinToString` の各要素にも `|` を付けていたため **1 行目だけ `||`** になり、`trimMargin()`
 * が 1 個だけ剥がした結果 `|  echo 'usage: ...' >&2` が残った。シェルは関数定義もパース時に
 * 読むため、行頭 `|` は構文エラーになり **`z2-macro` がどのサブコマンドでも起動不能**だった
 * (`syntax error near unexpected token '|'`)。
 *
 * 行頭の `|` は POSIX sh では常に構文エラーなので、「生成物のどの行も `|` で始まらない」は
 * そのまま健全性の判定として使える。
 */
class GeneratedScriptMarginTest {

    private fun assertNoMarginLeak(name: String, script: String) {
        val bad = script.lines()
            .withIndex()
            .filter { (_, line) -> line.startsWith("|") }
        assertTrue(
            "$name: 行頭に trimMargin のマージン `|` が残っている " +
                bad.joinToString { "(line ${it.index + 1}) ${it.value}" },
            bad.isEmpty()
        )
    }

    @Test
    fun z2MacroScript_hasNoMarginLeak() {
        for (lang in listOf("ja", "en")) {
            assertNoMarginLeak("z2MacroScript($lang)", z2MacroScript(lang))
        }
    }

    @Test
    fun z2MacroSamples_haveNoMarginLeak() {
        for (lang in listOf("ja", "en")) {
            for ((name, body) in z2MacroSamples(lang)) {
                assertNoMarginLeak("z2MacroSamples($lang)/$name", body)
            }
        }
    }

    /** usage 行が「`|` 無し・先頭 2 スペース」の正しい形で出ていること (退行の直接確認)。 */
    @Test
    fun z2MacroScript_usageLinesAreWellFormed() {
        val script = z2MacroScript("ja")
        assertTrue(
            "usage の 1 行目が正しい形で出ていない",
            script.contains("\n  echo 'usage: z2-macro <サブコマンド>' >&2")
        )
    }

    /** SMS 版 OTP サンプルが同梱され、通知ログではなく sms.jsonl を見ていること。 */
    @Test
    fun otpSmsSample_readsSmsLog() {
        val samples = z2MacroSamples("ja")
        val body = samples["otp-sms.sh"]
        assertTrue("otp-sms.sh が同梱されていない", body != null)
        assertTrue("otp-sms.sh が sms.jsonl を見ていない", body!!.contains(".z2term/sms.jsonl"))
    }
}
