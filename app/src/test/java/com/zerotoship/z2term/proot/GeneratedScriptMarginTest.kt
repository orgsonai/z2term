package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

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

    /** OTP サンプルが `z2-when` のトリガーで動く形になっていること（0.8.273）。 */
    @Test
    fun otpSamples_areTriggerBased() {
        val samples = z2MacroSamples("ja")
        val sms = samples["otp-sms.sh"]
        val clip = samples["otp-clip.sh"]
        assertTrue("otp-sms.sh が同梱されていない", sms != null)
        assertTrue("otp-clip.sh が同梱されていない", clip != null)
        // 抽出済みのコードを使う（本文を自分で解析しない）。
        assertTrue("otp-sms.sh が Z2_WHEN_OTP を使っていない", sms!!.contains("Z2_WHEN_OTP"))
        assertTrue("otp-clip.sh が Z2_WHEN_OTP を使っていない", clip!!.contains("Z2_WHEN_OTP"))
        // 動かし方の案内 (`z2-macro install` が出す行) が z2-when 側を指していること。
        assertTrue("otp-sms.sh の z2-run 行が sms:otp でない", sms.contains("# z2-run: z2-when sms:otp"))
        assertTrue("otp-clip.sh の z2-run 行が notify:otp でない", clip.contains("# z2-run: z2-when notify:otp"))
    }

    /**
     * **`z2-when` で表現できるサンプルは監視ループを持たない**こと（0.8.273）。
     *
     * 0.8.272 まで OTP・電池・日報のサンプルは「ログを 2 秒ごとに見張る常駐スクリプト」で、
     * 常駐サーバーに登録して使う案内が付いていた。エンジン(proot/z2root)下では外部コマンドを
     * 1 回起こすだけで ptrace 越しに数千 syscall になるため、実際に動かしていた端末では
     * **待っているだけでエンジンが CPU を常時 5% 前後**使い続けていた（実測）。同じことが
     * `z2-when` のトリガーで常駐なしに書けるので、常駐版へ戻さないことをここで固定する。
     *
     * `watch-basic.sh` だけは例外 — **ログ差分の読み方そのものを見せる教材**で、`z2-when` に
     * 無いきっかけを自分で拾いたいときの雛形だから、監視ループがあることに意味がある。
     */
    @Test
    fun triggerBasedSamples_doNotPoll() {
        val mayPoll = setOf("watch-basic.sh")
        for (lang in listOf("ja", "en")) {
            for ((name, body) in z2MacroSamples(lang)) {
                if (name in mayPoll) continue
                assertTrue(
                    "$lang/$name が監視ループ (while :;) を持っている — z2-when で書けるはず",
                    !body.contains("while :;")
                )
            }
        }
    }

    /** 雛形の見張り間隔が詰められていないこと（エンジン下ではここが電池に直結する）。 */
    @Test
    fun pollingSample_usesARelaxedInterval() {
        val body = z2MacroSamples("ja")["watch-basic.sh"]!!
        val poll = Regex("""^POLL=(\d+)""", RegexOption.MULTILINE)
            .find(body)?.groupValues?.get(1)?.toInt()
        assertTrue("watch-basic.sh に POLL が無い", poll != null)
        assertTrue("POLL=$poll は短すぎる (エンジン下では電池に出る)", poll!! >= 10)
    }

    /**
     * リマインダーサンプルが**単発と繰り返しで置き場を分けている**こと（0.8.275）。
     *
     * 単発まで `z2-when` のルールにすると、鳴り終わった死んだルールが自動化タブに溜まる。
     * 逆に繰り返しを `z2-alarm` だけで組むと、拾い役を予定の数だけ常設することになる。
     * どちらに寄せても壊れないので、**両方を使い分けている**ことをここで押さえる。
     */
    @Test
    fun remindSample_splitsOneShotAndRepeating() {
        for (lang in listOf("ja", "en")) {
            val body = z2MacroSamples(lang)["remind.sh"]
            assertTrue("$lang: remind.sh が同梱されていない", body != null)
            assertTrue("$lang: 単発が z2-alarm の予約になっていない", body!!.contains("z2-alarm ${'$'}SPEC"))
            assertTrue("$lang: 繰り返しが z2-when のルールになっていない", body.contains("z2-when \"${'$'}SPEC\" run"))
            // 受け口は 2 本だけ (予定を足しても増えない)。
            assertTrue("$lang: 発火の受け口が無い", body.contains("z2-when 'event:alarm' run"))
            assertTrue("$lang: 通知ボタンの受け口が無い", body.contains("z2-when 'event:notify_action' run"))
        }
    }

    /** 同梱サンプルが実際の `sh` の構文検査を通ること（sh が無い環境ではスキップ）。 */
    @Test
    fun samples_areValidPosixShell() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en")) {
            for ((name, body) in z2MacroSamples(lang)) {
                val tmp = File.createTempFile("macro-$lang-", "-$name")
                try {
                    tmp.writeText(body)
                    val proc = ProcessBuilder(sh!!, "-n", tmp.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = proc.inputStream.bufferedReader().readText()
                    assertEquals("$lang/$name: sh -n が構文エラーを報告した:\n$output", 0, proc.waitFor())
                } finally {
                    tmp.delete()
                }
            }
        }
    }
}
