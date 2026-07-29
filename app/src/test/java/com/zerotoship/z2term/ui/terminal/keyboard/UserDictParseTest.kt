package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ユーザー辞書 ([UserDictStore.parse]) の行解析。
 *
 * ⚠ ここが要 — 利用者が持ち込むファイルは**書き方が 1 つではない**。SKK 形式だけを見ていた
 * 0.8.280 は、変換辞書ツールが書き出す表形式 (よみ→表記→品詞→注釈) を 1 語も読めなかった。
 */
class UserDictParseTest {

    @Test
    fun `表形式 (タブ区切り) を読む`() {
        val text = listOf(
            "あいぎょう\t愛楽\t名詞\t",
            "あいすむ\t相済む\t動詞マ行五段\t",
            "あぎたししゃきんばら\t阿嗜多翅舎欽婆羅\t人名\t",
        ).joinToString("\n")
        val d = UserDictStore.parse(text)
        assertEquals(listOf("愛楽"), d["あいぎょう"])
        assertEquals(listOf("相済む"), d["あいすむ"])
        assertEquals(listOf("阿嗜多翅舎欽婆羅"), d["あぎたししゃきんばら"])
    }

    @Test
    fun `表形式の注釈列は候補にしない`() {
        val text = "あかだ\t阿伽陀\t名詞\t不老長生をもたらすとされる妙薬・霊薬。"
        assertEquals(listOf("阿伽陀"), UserDictStore.parse(text)["あかだ"])
    }

    @Test
    fun `同じ読みの複数行は候補として並ぶ`() {
        val text = "あかだ\t阿伽陀\t名詞\t\nあかだ\t阿掲陀\t名詞\t阿伽陀。"
        assertEquals(listOf("阿伽陀", "阿掲陀"), UserDictStore.parse(text)["あかだ"])
    }

    @Test
    fun `表形式は全角スペースや連続スペースでも割れる`() {
        val text = "あいだ　頃　名詞\nあいみん  哀愍  名詞"
        val d = UserDictStore.parse(text)
        assertEquals(listOf("頃"), d["あいだ"])
        assertEquals(listOf("哀愍"), d["あいみん"])
    }

    @Test
    fun `SKK 形式を読む`() {
        val d = UserDictStore.parse("ずーたーむ /Z2Term/z2term/")
        assertEquals(listOf("Z2Term", "z2term"), d["ずーたーむ"])
    }

    @Test
    fun `SKK 形式の注釈は落とす`() {
        val d = UserDictStore.parse("あかだ /阿伽陀;霊薬/")
        assertEquals(listOf("阿伽陀"), d["あかだ"])
    }

    @Test
    fun `タブ区切りで書かれた SKK 形式も読む`() {
        val d = UserDictStore.parse("ずーたーむ\t/Z2Term/z2term/")
        assertEquals(listOf("Z2Term", "z2term"), d["ずーたーむ"])
    }

    @Test
    fun `読みがひらがなでない行は捨てる`() {
        // 送り仮名あり見出し (SKK okuri-ari) / 英字見出し / 漢字見出し。
        val text = "あいs\t愛\t動詞\nz2term\tZ2Term\t名詞\n愛\tあい\t名詞"
        assertTrue(UserDictStore.parse(text).isEmpty())
    }

    @Test
    fun `コメントと空行は捨てる`() {
        val text = ";; okuri-nasi entries.\n# メモ\n\nあいだ\t頃\t名詞"
        val d = UserDictStore.parse(text)
        assertEquals(1, d.size)
        assertEquals(listOf("頃"), d["あいだ"])
    }

    @Test
    fun `読みと同じ表記や列が足りない行は入れない`() {
        val text = "あいだ\tあいだ\t名詞\nあいだけ\n"
        assertTrue(UserDictStore.parse(text).isEmpty())
    }

    @Test
    fun `長すぎる読みは壊れた行として捨てる`() {
        val reading = "あ".repeat(33)
        assertFalse(UserDictStore.parse("$reading\t亜\t名詞").containsKey(reading))
    }
}
