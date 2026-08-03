package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WhenRule] のトリガー分解と読み書きを具体例で検証する。
 *
 * 種別 ([WhenRule.kind]) の取り出しは**すべてのトリガーの入口**で、ここがズレるとルールが
 * 黙って動かなくなる。`boot` (0.8.264) で `:` の無い書式を許したので、既存の `種別:引数` が
 * 巻き添えで変わっていないことも一緒に押さえる。
 */
class WhenRuleTest {

    private fun rule(trigger: String) = WhenRule(id = "r1", trigger = trigger, run = "true")

    @Test fun kindAndSpec_splitOnFirstColon() {
        assertEquals("charge", rule("charge:start").kind)
        assertEquals("start", rule("charge:start").spec)
        assertEquals("battery", rule("battery:below=20").kind)
        assertEquals("below=20", rule("battery:below=20").spec)
    }

    @Test fun spec_keepsLaterColons() {
        // time:daily=07:30 の引数には `:` が残る (最初の 1 つだけで割る)。
        assertEquals("time", rule("time:daily=07:30").kind)
        assertEquals("daily=07:30", rule("time:daily=07:30").spec)
    }

    @Test fun triggerWithoutColon_isAllKind() {
        // boot は引数を取らないので `:` を書かせない (0.8.264)。
        assertEquals("boot", rule("boot").kind)
        assertEquals("", rule("boot").spec)
        // `boot:` と書かれても同じ種別に落ちる (spec は見ないので同じルールとして動く)。
        assertEquals("boot", rule("boot:").kind)
    }

    @Test fun netTrigger_splitsLikeTheOthers() {
        assertEquals("net", rule("net:online").kind)
        assertEquals("online", rule("net:online").spec)
    }

    @Test fun parse_readsFiltersAndRoundTrips() {
        val text = """
            trigger=net:online
            run=~/.z2term/macros/sync.sh
            enabled=1
            if=wifi,!screen
            cooldown=30m
            between=22:00-07:00
            days=mon-fri
        """.trimIndent()
        val r = WhenRule.parse("sync", text)!!
        assertEquals("net:online", r.trigger)
        assertEquals("wifi,!screen", r.condition)
        assertEquals("30m", r.cooldown)
        assertEquals("22:00-07:00", r.between)
        assertEquals("mon-fri", r.days)
        assertTrue(r.hasFilters)
        // 書き戻して読み直しても同じ (CLI と画面のどちらから触っても壊れない)。
        assertEquals(r, WhenRule.parse("sync", r.serialize()))
    }

    @Test fun parse_readsNameAndRoundTrips() {
        // 名前は表示だけの項目 (0.8.303)。中の空白は保つ (前後だけ落とす)。
        val r = WhenRule.parse("r", "trigger=boot\nrun=true\nenabled=1\nname=夜の バックアップ\n")!!
        assertEquals("夜の バックアップ", r.name)
        assertEquals("夜の バックアップ", r.label)
        assertEquals(r, WhenRule.parse("r", r.serialize()))
    }

    @Test fun label_fallsBackToTrigger() {
        // 未記入のときだけトリガーが見出しになる (今まで登録したルールの見え方を変えない)。
        assertEquals("charge:start", rule("charge:start").label)
    }

    @Test fun parse_ignoresUnknownKeys() {
        // 「知らないキーは黙って無視」= 新しい項目を足しても古い版が読める、の担保。
        val r = WhenRule.parse("r", "trigger=boot\nrun=true\nenabled=1\nfuture=whatever\n")!!
        assertEquals("boot", r.trigger)
        assertTrue(r.enabled)
    }

    @Test fun parse_needsTriggerAndRun() {
        assertNull(WhenRule.parse("r", "run=true\n"))
        assertNull(WhenRule.parse("r", "trigger=boot\n"))
    }

    @Test fun serialize_omitsUnsetFilters() {
        // 端末から登録したままのルールに余計な行を足さない。
        val out = rule("boot").serialize()
        assertEquals("trigger=boot\nrun=true\nenabled=1\n", out)
    }
}
