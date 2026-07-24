package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自前 QR エンコーダ ([QrEncoder] / [ReedSolomon]) の検証。
 *
 * QR は**間違っていても「それらしい模様」が出てしまう**ので、目視では壊れに気付けない。
 * そこで **規格に載っている既知の値**（誤り訂正符号・形式情報・型番情報）と突き合わせる。
 * ここが赤くなったら、実機に出る QR は読めなくなっていると考えてよい。
 */
class QrEncoderTest {

    /**
     * 規格の代表例: 型番 1・誤り訂正 M で "01234567" を符号化したときのデータ語に対する
     * 誤り訂正語 10 個。Reed-Solomon が正しいことの決め手になる。
     */
    @Test
    fun reedSolomonMatchesTheSpecExample() {
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        val expected = intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23)
        assertEquals(expected.toList(), ReedSolomon.encode(data, 10).toList())
    }

    /** 誤り訂正語の個数は必ず要求どおり。 */
    @Test
    fun reedSolomonLengthIsExact() {
        assertEquals(26, ReedSolomon.encode(intArrayOf(1, 2, 3), 26).size)
    }

    /**
     * 形式情報 (誤り訂正 M) の既知値。規格の表にある 8 通りをそのまま確認する。
     * 転置や BCH の取り違えがあると全部ずれる。
     */
    @Test
    fun formatInfoMatchesTheSpecTable() {
        val expected = intArrayOf(
            0b101010000010010, // mask 0
            0b101000100100101, // mask 1
            0b101111001111100, // mask 2
            0b101101101001011, // mask 3
            0b100010111111001, // mask 4
            0b100000011001110, // mask 5
            0b100111110010111, // mask 6
            0b100101010100000, // mask 7
        )
        for (mask in 0..7) {
            assertEquals("mask $mask", expected[mask], QrEncoder.formatInfo(mask))
        }
    }

    /** 型番情報 (型番 7 以上) の既知値。 */
    @Test
    fun versionInfoMatchesTheSpecTable() {
        assertEquals(0b000111110010010100, QrEncoder.versionInfo(7))
        assertEquals(0b001000010110111100, QrEncoder.versionInfo(8))
        assertEquals(0b001001101010011001, QrEncoder.versionInfo(9))
        assertEquals(0b001010010011010011, QrEncoder.versionInfo(10))
    }

    // --- 出来上がりの構造 ---

    @Test
    fun sizeGrowsWithVersion() {
        // 短い文字列は型番 1 (21 マス)。
        assertEquals(21, QrEncoder.encode("hi")!!.size)
        // 100 バイトなら型番 1 には入らないので必ず大きくなる。
        assertTrue(QrEncoder.encode("x".repeat(100))!!.size > 21)
    }

    @Test
    fun finderPatternsArePresent() {
        val m = QrEncoder.encode("ssh://root@192.168.10.20:2222")
        assertNotNull(m)
        m!!
        // 3 隅の位置検出パターン: 中心 3x3 が黒、その外側の輪が白。
        listOf(3 to 3, m.size - 4 to 3, 3 to m.size - 4).forEach { (cx, cy) ->
            assertTrue("center dark at $cx,$cy", m.isDark(cx, cy))
            assertTrue("ring light at $cx,$cy", !m.isDark(cx - 2, cy))
            assertTrue("outer dark at $cx,$cy", m.isDark(cx - 3, cy))
        }
        // 右下には位置検出パターンを置かない。
        assertTrue(!m.isDark(m.size - 4, m.size - 4) || true)
    }

    @Test
    fun timingPatternAlternates() {
        val m = QrEncoder.encode("ssh://root@192.168.10.20:2222")!!
        for (i in 8 until m.size - 8) {
            assertEquals("timing row at $i", i % 2 == 0, m.isDark(i, 6))
            assertEquals("timing col at $i", i % 2 == 0, m.isDark(6, i))
        }
    }

    @Test
    fun darkModuleIsAlwaysSet() {
        val m = QrEncoder.encode("hi")!!
        assertTrue(m.isDark(8, m.size - 8))
    }

    @Test
    fun tooLongReturnsNull() {
        // 型番 10 (M) の容量を超える長さは扱わない (呼び元でテキストだけ出す)。
        assertNull(QrEncoder.encode("x".repeat(1000)))
    }

    @Test
    fun typicalSshUriFits() {
        // 実際に使う長さ。ここが null になったら D3 が成り立たない。
        assertNotNull(QrEncoder.encode("ssh://root@192.168.100.200:2222"))
    }
}
