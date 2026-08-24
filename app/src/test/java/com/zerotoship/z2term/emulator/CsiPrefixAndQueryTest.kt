package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSI の **プレフィックス (`?` / `>` / `<`) 別の振り分け** と **問い合わせへの応答** の回帰テスト。
 *
 * 元バグ 2 件:
 *  - `CSI c` (DA1 = 「そちらは何者か」) に応答していなかった。TUI の土台になっている
 *    ライブラリは「機能の問い合わせを投げ、DA1 の応答が返った時点で判定を打ち切る」形で
 *    書かれていることが多く、応答しないと判定が終わらず起動途中で待たされる。
 *  - `>` / `<` プレフィックス付きのシーケンスを、プレフィックスを見ずに終端文字だけで
 *    [TerminalEmulator] の standard 経路へ流していた。結果 `CSI > 4 ; N m` (XTMODKEYS) が
 *    SGR として適用されて下線が点き、`CSI > N u` / `CSI < u` (kitty keyboard protocol の
 *    push/pop) が SCORC (カーソル復元) として実行されてカーソルが飛んでいた。
 *    どちらも TUI が起動時・終了時に無条件で送るため、「TUI を開くと表示が崩れる」形で出る。
 */
class CsiPrefixAndQueryTest {
    private val ESC = "\u001b"   // ⚠ 生の ESC を書かない (目に見えず、抜けてもテストが黙って空振りする)
    private val TEST_VERSION_NAME = "9.9.9-test"
    private val TEST_VERSION_CODE = 12345

    private fun emu(sink: StringBuilder, rows: Int = 5, cols: Int = 20) =
        TerminalEmulator(
            output = { b -> sink.append(String(b, Charsets.US_ASCII)) },
            initialRows = rows,
            initialColumns = cols,
            // 応答文字列を固定するため、実際の BuildConfig ではなく既知の値を渡す。
            versionName = TEST_VERSION_NAME,
            versionCode = TEST_VERSION_CODE
        )

    private fun feed(e: TerminalEmulator, s: String) = e.processBytes(s.toByteArray(Charsets.US_ASCII))

    private fun underlineAt(e: TerminalEmulator, row: Int, col: Int) =
        (e.buffer.getScreenRow(row).getCell(col).fgAttr and SgrAttribute.FLAG_UNDERLINE) != 0

    // --- DA1 -------------------------------------------------------------------

    @Test
    fun da1_isAnswered() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[c")
        assertEquals("$ESC[?62;22c", out.toString())
    }

    @Test
    fun da1_withExplicitZero_isAnswered() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[0c")
        assertEquals("$ESC[?62;22c", out.toString())
    }

    // --- DA2 / XTVERSION (0.8.394) ---------------------------------------------

    @Test
    fun da2_isAnswered() {
        val out = StringBuilder()
        // `CSI > c` (DA2) = 「型と版は何か」。⚠ DA1 の応答を返してはいけない
        // (問い合わせの種類が違うので、受け取った側の判定が食い違う)。
        feed(emu(out), "$ESC[>c")
        assertEquals("$ESC[>1;$TEST_VERSION_CODE;0c", out.toString())
    }

    @Test
    fun da2_withExplicitZero_isAnswered() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[>0c")
        assertEquals("$ESC[>1;$TEST_VERSION_CODE;0c", out.toString())
    }

    @Test
    fun da2_announcesVt220_notXterm() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[>c")
        // ⭐ 他の端末を騙らない。xterm を名乗る (Pp = 41) と、持っていない機能を前提に
        // 話しかけられて壊れ方が分かりにくくなる。DA1 の `?62`(VT220 相当) と揃える。
        assertTrue(out.toString().startsWith("$ESC[>1;"))
        assertFalse(out.toString().startsWith("$ESC[>41;"))
    }

    @Test
    fun da2_withNonZeroParam_isIgnored() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[>1c")
        assertEquals("", out.toString())
    }

    @Test
    fun xtversion_isAnswered() {
        val out = StringBuilder()
        // XTVERSION の応答は DCS `> |` … ST。DA2 が数値なのに対しこちらは自由書式。
        feed(emu(out), "$ESC[>q")
        assertEquals("${ESC}P>|z2term($TEST_VERSION_NAME)$ESC\\", out.toString())
    }

    @Test
    fun xtversion_withNonZeroParam_isIgnored() {
        val out = StringBuilder()
        feed(emu(out), "$ESC[>2q")
        assertEquals("", out.toString())
    }

    @Test
    fun lessThanPrefix_isNeverAnswered() {
        val out = StringBuilder()
        // `<` プレフィックスは kitty keyboard protocol の pop で使われるだけで、
        // 問い合わせではない。⚠ `>` と同じ経路に載っているので取り違えない。
        feed(emu(out), "$ESC[<c$ESC[<q")
        assertEquals("", out.toString())
    }

    // --- '>' / '<' プレフィックスが standard 経路へ落ちないこと ----------------

    @Test
    fun xtmodkeys_doesNotApplySgr() {
        val out = StringBuilder()
        val e = emu(out)
        // `CSI > 4 ; 2 m` を standard へ流すと SGR [4,2] = 下線 + dim として適用されていた。
        feed(e, "$ESC[>4;2mX")
        assertFalse("XTMODKEYS を SGR として適用してはいけない", underlineAt(e, 0, 0))
    }

    @Test
    fun kittyKeyboardPush_doesNotRestoreCursor() {
        val out = StringBuilder()
        val e = emu(out)
        feed(e, "$ESC[3;6H")               // カーソルを 3 行 6 列 (0 始まりで 2,5) へ
        feed(e, "$ESC[>1u")                // standard へ流すと SCORC で (0,0) へ飛んでいた
        assertEquals("kitty push でカーソル行が動いてはいけない", 2, e.cursorRow)
        assertEquals("kitty push でカーソル列が動いてはいけない", 5, e.cursorCol)
    }

    @Test
    fun kittyKeyboardPop_doesNotRestoreCursor() {
        val out = StringBuilder()
        val e = emu(out)
        feed(e, "$ESC[3;6H")
        feed(e, "$ESC[<u")                 // TUI 終了時の pop。こちらも SCORC になっていた
        assertEquals(2, e.cursorRow)
        assertEquals(5, e.cursorCol)
    }

    // --- 既存の応答が壊れていないこと ------------------------------------------

    @Test
    fun dsr6_stillReportsCursorPosition() {
        val out = StringBuilder()
        val e = emu(out)
        feed(e, "$ESC[2;3H$ESC[6n")
        assertEquals("$ESC[2;3R", out.toString())
    }

    @Test
    fun scorc_stillWorksWithoutPrefix() {
        val out = StringBuilder()
        val e = emu(out)
        feed(e, "$ESC[2;3H")               // 保存位置 (1,2)
        feed(e, "$ESC[s")                  // SCOSC
        feed(e, "$ESC[5;9H")               // どこかへ移動
        feed(e, "$ESC[u")                  // プレフィックス無しの SCORC は従来どおり効く
        assertEquals(1, e.cursorRow)
        assertEquals(2, e.cursorCol)
    }
}
