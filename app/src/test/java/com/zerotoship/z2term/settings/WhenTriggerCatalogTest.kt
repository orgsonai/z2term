package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [WhenTriggerCatalog] の候補と検査が食い違っていないことを押さえる。
 *
 * ここがズレると「画面の候補から選んだのに保存できない」「保存できたのに一生発火しない」という、
 * 原因の追えない壊れ方になる。特に [everyOptionIsValid] は**候補を足したときに検査を直し忘れる**
 * のを機械で止めるためのもの。
 */
class WhenTriggerCatalogTest {

    /** 画面に出す候補は、そのまま入れれば必ず正しい形でなければならない。 */
    @Test fun everyOptionIsValid() {
        WhenTriggerCatalog.kinds.forEach { kind ->
            kind.options.forEach { option ->
                assertNull(
                    "候補 ${option.example} が検査を通らない (kind=${kind.id})",
                    WhenTriggerCatalog.triggerProblem(option.example)
                )
            }
        }
    }

    /** 候補の種別 id は、そのトリガー文字列の `:` の手前と一致していること。 */
    @Test fun everyOptionBelongsToItsKind() {
        WhenTriggerCatalog.kinds.forEach { kind ->
            kind.options.forEach { option ->
                assertEquals(kind.id, option.example.substringBefore(':'))
            }
        }
    }

    @Test fun unknownKind_isRejected() {
        assertEquals(
            WhenTriggerCatalog.Problem.UNKNOWN_KIND,
            WhenTriggerCatalog.triggerProblem("batery:below=20")
        )
        assertEquals(
            WhenTriggerCatalog.Problem.UNKNOWN_KIND,
            WhenTriggerCatalog.triggerProblem("charging:start")
        )
    }

    /** 種別は合っているのに引数が違うもの。1 文字違いが一番危ないので具体例で押さえる。 */
    @Test fun badSpec_isRejected() {
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("charge:begin"))
        // 値が無い = `battery:below=` のまま保存された状態。
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("battery:below="))
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("wifi:ssid="))
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("time:hourly=1"))
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("sensor:light"))
        // boot は引数を取らない唯一のトリガー。
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("boot:now"))
        // event: は名前を検査しないが、空は通さない。
        assertEquals(WhenTriggerCatalog.Problem.BAD_SPEC, WhenTriggerCatalog.triggerProblem("event:"))
    }

    @Test fun empty_isRejected() {
        assertEquals(WhenTriggerCatalog.Problem.EMPTY, WhenTriggerCatalog.triggerProblem("   "))
    }

    /** 手書きの正しいトリガー (候補に無い値でも通ること)。 */
    @Test fun handWrittenTriggers_pass() {
        assertNull(WhenTriggerCatalog.triggerProblem("battery:below=15"))
        assertNull(WhenTriggerCatalog.triggerProblem("time:cron=0 11,23 * * *"))
        assertNull(WhenTriggerCatalog.triggerProblem("event:ringer_*"))
        assertNull(WhenTriggerCatalog.triggerProblem("sensor:light>250"))
        assertNull(WhenTriggerCatalog.triggerProblem("boot"))
    }

    /**
     * 改行入りのコマンドを弾く。ルールファイルは 1 行 1 項目なので、これを通すと
     * 2 行目以降が捨てられて**途中で切れたコマンド**になる (0.8.272 の事故)。
     */
    @Test fun runWithNewline_isRejected() {
        assertEquals(
            WhenTriggerCatalog.RunProblem.MULTILINE,
            WhenTriggerCatalog.runProblem("for t in a b; do ping \$t\n done")
        )
        assertEquals(WhenTriggerCatalog.RunProblem.EMPTY, WhenTriggerCatalog.runProblem("  "))
        assertNull(WhenTriggerCatalog.runProblem("sh ~/.z2term/macros/x.sh"))
    }

    /** スクリプト 1 本を指しているときだけパスを返す (中身を出す窓口なので取り違えない)。 */
    @Test fun scriptPath_onlyForASingleScript() {
        assertEquals("~/.z2term/macros/x.sh", WhenTriggerCatalog.scriptPathIn("~/.z2term/macros/x.sh"))
        assertEquals("/root/.z2term/macros/x.sh", WhenTriggerCatalog.scriptPathIn("sh /root/.z2term/macros/x.sh"))
        assertEquals("~/a.sh", WhenTriggerCatalog.scriptPathIn("  bash ~/a.sh  "))
        // 他のコマンドと繋がっているものは「1 本のスクリプト」ではない。
        assertNull(WhenTriggerCatalog.scriptPathIn("~/a.sh && ~/b.sh"))
        assertNull(WhenTriggerCatalog.scriptPathIn("sleep 5; sshd --lan"))
        assertNull(WhenTriggerCatalog.scriptPathIn("z2-toast hello"))
        // 引数付きはどこまでがスクリプトか決められない。
        assertNull(WhenTriggerCatalog.scriptPathIn("~/a.sh --now"))
    }
}
