package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画面の条件ビルダー ([WhenConditionSpec]) の文字列 ⇄ 構造の変換 (0.8.373)。
 *
 * ⚠ ここが緩いと**保存した瞬間に式が別物へ書き換わる**。端末で書いたルールを画面で開いて
 * 閉じただけで動きが変わる、という一番たちの悪い壊れ方になるので、
 * 「組み立て直せないものは触らない (null)」を具体例で固定する。
 */
class WhenConditionSpecTest {

    @Test
    fun `真偽と否定を読み書きできる`() {
        val parsed = WhenConditionSpec.parse("wifi,!screen")!!
        assertEquals(2, parsed.size)
        assertEquals("wifi", parsed[0].key)
        assertEquals(WhenCondition.Op.TRUTHY, parsed[0].op)
        assertTrue(parsed[1].negate)
        // 往復しても同じ文字列に戻る。
        assertEquals("wifi,!screen", WhenConditionSpec.build(parsed))
    }

    @Test
    fun `一致と数値比較を読み書きできる`() {
        val parsed = WhenConditionSpec.parse("ssid=Home,level<30,temp>40")!!
        assertEquals(WhenCondition.Op.EQ, parsed[0].op)
        assertEquals("Home", parsed[0].value)
        assertEquals(WhenCondition.Op.LT, parsed[1].op)
        assertEquals(WhenCondition.Op.GT, parsed[2].op)
        assertEquals("ssid=Home,level<30,temp>40", WhenConditionSpec.build(parsed))
    }

    @Test
    fun `空は条件なし`() {
        assertEquals(emptyList<WhenCondition>(), WhenConditionSpec.parse(""))
        assertEquals("", WhenConditionSpec.build(emptyList()))
    }

    @Test
    fun `組み立て直せない書き方は触らない`() {
        // 知らないキー (将来増えるもの・端末で書いたもの)
        assertNull(WhenConditionSpec.parse("wifi,future_key"))
        // 型と演算子が噛み合わない (端末では有効だが画面の部品では表せない)
        assertNull(WhenConditionSpec.parse("screen=on"))
        assertNull(WhenConditionSpec.parse("level"))
        assertNull(WhenConditionSpec.parse("ssid<3"))
        // 値が無い / 空の項目
        assertNull(WhenConditionSpec.parse("ssid="))
        assertNull(WhenConditionSpec.parse("wifi,,screen"))
        // 数値のはずが数値でない
        assertNull(WhenConditionSpec.parse("level<abc"))
    }

    @Test
    fun `キーの型で入力欄が決まる`() {
        assertEquals(WhenConditionSpec.Kind.BOOL, WhenConditionSpec.kindOf("wifi"))
        assertEquals(WhenConditionSpec.Kind.TEXT, WhenConditionSpec.kindOf("ssid"))
        assertEquals(WhenConditionSpec.Kind.NUMBER, WhenConditionSpec.kindOf("level"))
    }

    @Test
    fun `if と if_any のどちらを組んでいるかを見分ける`() {
        val all = WhenConditionSpec.builderOf("wifi,!screen", "")
        assertEquals(WhenConditionSpec.Mode.ALL, all.mode)
        assertEquals(2, all.conditions.size)
        assertEquals(false, all.advanced)

        val any = WhenConditionSpec.builderOf("", "wifi,charging")
        assertEquals(WhenConditionSpec.Mode.ANY, any.mode)
        assertEquals(2, any.conditions.size)
    }

    @Test
    fun `両方あるルールは画面では組み立てない`() {
        // 端末からは「if を全部 かつ if_any のどれか」が書ける。画面の「すべて / どれか」
        // 1 つでは表せないので、そのまま見せる (捨てない)。
        val b = WhenConditionSpec.builderOf("charging", "wifi,ssid=Home")
        assertTrue(b.advanced)
    }

    @Test
    fun `読めない式もそのまま見せる`() {
        val b = WhenConditionSpec.builderOf("screen=on", "")
        assertTrue(b.advanced)
        assertEquals(emptyList<WhenCondition>(), b.conditions)
    }
}
