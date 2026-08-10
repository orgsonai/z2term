package com.zerotoship.z2term.ui.terminal.input

import com.zerotoship.z2term.emulator.TerminalEmulator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `z2-session key` のキー名 → バイト列変換 ([AndroidKeyMapper.keyBytesFor])。
 *
 * ⚠ **端末に届くのはバイト列だけ**なので、ここがズレると「送ったのに効かない」という形でしか
 * 現れず、実機でも原因が見えない。仕様として決めたことを数値で固定しておく。
 */
class KeyBytesForTest {

    /** 矢印は DECCKM 依存で emulator が組む。ここでは呼ばれたことが分かる印を返す。 */
    private val cursor: (TerminalEmulator.CursorKey) -> ByteArray = { key ->
        byteArrayOf(0x7B, key.ordinal.toByte())
    }

    private fun bytes(name: String): ByteArray {
        val r = AndroidKeyMapper.keyBytesFor(name, cursor)
        assertTrue("$name が受け取られなかった: $r", r is AndroidKeyMapper.KeyBytes.Ok)
        return (r as AndroidKeyMapper.KeyBytes.Ok).bytes
    }

    /** Ctrl+文字 = 制御コード。⚠ 大文字で書いても同じ (端末では区別が無い)。 */
    @Test
    fun ctrlLetterBecomesAControlCode() {
        assertArrayEquals(byteArrayOf(0x03), bytes("C-c"))
        assertArrayEquals(byteArrayOf(0x03), bytes("C-C"))
        assertArrayEquals(byteArrayOf(0x04), bytes("C-d"))
        assertArrayEquals(byteArrayOf(0x1A), bytes("C-z"))
        // 記号にも割り当てがある (C-[ は ESC そのもの)。
        assertArrayEquals(byteArrayOf(0x1B), bytes("C-["))
        assertArrayEquals(byteArrayOf(0x00), bytes("C- "))
    }

    /** Meta (=Alt) は ESC 前置。⚠ `M-` でも `A-` でも同じもの。 */
    @Test
    fun metaPrefixesEsc() {
        assertArrayEquals(byteArrayOf(0x1B, 'x'.code.toByte()), bytes("M-x"))
        assertArrayEquals(byteArrayOf(0x1B, 'x'.code.toByte()), bytes("A-x"))
        // 重ねられる: Ctrl+Meta+a = ESC + 0x01
        assertArrayEquals(byteArrayOf(0x1B, 0x01), bytes("C-M-a"))
    }

    /** 修飾なしの 1 文字はその文字そのもの (UTF-8)。 */
    @Test
    fun aBareCharacterIsItself() {
        assertArrayEquals(byteArrayOf('a'.code.toByte()), bytes("a"))
        assertArrayEquals(byteArrayOf('A'.code.toByte()), bytes("A"))
        assertArrayEquals("あ".toByteArray(Charsets.UTF_8), bytes("あ"))
    }

    /** 特殊キー。大文字小文字は問わない。 */
    @Test
    fun specialKeysUseTheirVtSequences() {
        assertArrayEquals("[15~".toByteArray(Charsets.US_ASCII), bytes("F5"))
        assertArrayEquals("[15~".toByteArray(Charsets.US_ASCII), bytes("f5"))
        assertArrayEquals("OP".toByteArray(Charsets.US_ASCII), bytes("F1"))
        assertArrayEquals("[1~".toByteArray(Charsets.US_ASCII), bytes("Home"))
        assertArrayEquals("[6~".toByteArray(Charsets.US_ASCII), bytes("PgDn"))
        assertArrayEquals(byteArrayOf(0x0D), bytes("Enter"))
        assertArrayEquals(byteArrayOf(0x7F), bytes("BS"))
        assertArrayEquals(byteArrayOf(0x09), bytes("Tab"))
    }

    /**
     * ⚠ **矢印は自前で組まず emulator に投げる**。DECCKM が ON だと `ESC O A` へ変わるので、
     * ここで固定のバイト列を返すと application cursor keys のアプリで矢印が効かなくなる。
     */
    @Test
    fun arrowsComeFromTheEmulator() {
        assertArrayEquals(byteArrayOf(0x7B, TerminalEmulator.CursorKey.UP.ordinal.toByte()), bytes("Up"))
        assertArrayEquals(byteArrayOf(0x7B, TerminalEmulator.CursorKey.LEFT.ordinal.toByte()), bytes("left"))
    }

    /**
     * ⚠ **`S-Tab` だけは通す。** 断る基準は「Shift が付くか」ではなく
     * 「**端末が区別できるか**」で、backtab は `ESC [ Z` として実在する。
     */
    @Test
    fun shiftTabIsAllowedBecauseTheTerminalCanTellItApart() {
        assertArrayEquals(byteArrayOf(0x1B, 0x5B, 0x5A), bytes("S-Tab"))
        assertArrayEquals(byteArrayOf(0x1B, 0x5B, 0x5A), bytes("BackTab"))
    }

    /**
     * ⛔ **Ctrl+Shift+文字は送らずに断る。** 端末では Shift が文字に畳み込まれ、`C-a` と
     * 同じ 1 バイトになる。⚠ 黙って `C-a` を送ると「送ったはずなのに効かない」の原因が追えない。
     * 代わりに何と書けばよいか ([equivalentTo]) まで返すことを固定する。
     */
    @Test
    fun ctrlShiftIsRefusedWithTheEquivalentSpelledOut() {
        val r = AndroidKeyMapper.keyBytesFor("C-S-a", cursor)
        assertTrue("断られていない: $r", r is AndroidKeyMapper.KeyBytes.NotDistinguishable)
        assertEquals("C-a", (r as AndroidKeyMapper.KeyBytes.NotDistinguishable).equivalentTo)
        // 単独の Shift も同じ扱い (`A` と書けば済むものを 2 通りで受けない)。
        val s = AndroidKeyMapper.keyBytesFor("S-a", cursor)
        assertTrue("断られていない: $s", s is AndroidKeyMapper.KeyBytes.NotDistinguishable)
        assertEquals("A", (s as AndroidKeyMapper.KeyBytes.NotDistinguishable).equivalentTo)
    }

    /** 表に無い名前は名前を添えて返す (何を直せばよいか分かるように)。 */
    @Test
    fun unknownNamesComeBackAsUnknown() {
        val r = AndroidKeyMapper.keyBytesFor("Fnord", cursor)
        assertTrue("Unknown でない: $r", r is AndroidKeyMapper.KeyBytes.Unknown)
        assertEquals("Fnord", (r as AndroidKeyMapper.KeyBytes.Unknown).name)
        // 修飾子だけ、も受け取らない。
        assertTrue(AndroidKeyMapper.keyBytesFor("C-", cursor) is AndroidKeyMapper.KeyBytes.Unknown)
        // 割り当ての無い Ctrl+記号。
        assertTrue(AndroidKeyMapper.keyBytesFor("C-1", cursor) is AndroidKeyMapper.KeyBytes.Unknown)
    }

    /**
     * ⚠ 内蔵キーボードと**同じ表**を引いていること。ここが別実装になると、画面から打った
     * Ctrl+C と `z2-session key C-c` で違うバイトが出て、片方でしか再現しない不具合になる。
     */
    @Test
    fun theSameTableBacksBothPaths() {
        for (ch in listOf('a', 'c', 'z', '[', ']', '\\')) {
            val viaTable = AndroidKeyMapper.controlByteFor(ch)!!
            assertArrayEquals("C-$ch", byteArrayOf(viaTable), bytes("C-$ch"))
        }
    }
}
