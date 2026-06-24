package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kitty graphics protocol の Unicode placeholder (`U+10EEEE`) + combining diacritic で
 * 「セルにタイル参照を記録する」処理が仕様通りに動くことを保証する。
 *
 * 真の描画 (Bitmap タイル切り出し) は [android.graphics.Bitmap] が必要なので unit test
 * 環境では再現できない。 ここではセル単位のメタデータ:
 *  - 直前 SGR の truecolor `\e[38;2;R;G;B` から image id (24bit) を取り出して
 *    [PlaceholderRef.imageId] に格納
 *  - 後続 1〜3 個の combining diacritic (Kitty 固定 297 要素表) で
 *    srcRow / srcCol / placementIdLow を順次更新
 *  - 通常文字が割り込んだら以後の diacritic は通常テキストとして扱う
 *  - placeholder セルはテキスト抽出時に空白へ置換される (孤立サロゲートの混入防止)
 * を固定する。
 */
class KittyPlaceholderCellTest {

    private fun emu(rows: Int = 5, cols: Int = 20) =
        TerminalEmulator(output = {}, initialRows = rows, initialColumns = cols)

    private fun TerminalEmulator.feed(s: String) =
        processBytes(s.toByteArray(Charsets.UTF_8))

    private val PLACEHOLDER = String(Character.toChars(0x10EEEE))

    @Test
    fun placeholderRecordsImageIdFromTruecolorFg() {
        val e = emu()
        // fg = RGB(0x12, 0x34, 0x56) → imageId = 0x123456
        e.feed("[38;2;18;52;86m$PLACEHOLDER")
        val ref = e.buffer.getScreenRow(0).getCell(0).placeholder
        assertNotNull("placeholder cell should be marked", ref)
        assertEquals(0x123456, ref!!.imageId)
        assertEquals(0, ref.srcRow)
        assertEquals(0, ref.srcCol)
        assertEquals(0, ref.placementIdLow)
    }

    @Test
    fun diacriticUpdatesSrcRowAndSrcColAndPlacementId() {
        val e = emu()
        // diacritic 表 0 = U+0305、 1 = U+030D、 2 = U+030E、 3 = U+0310。
        e.feed("[38;2;0;0;7m$PLACEHOLDER")
        e.feed(String(Character.toChars(0x030D)))  // srcRow = 1
        e.feed(String(Character.toChars(0x030E)))  // srcCol = 2
        e.feed(String(Character.toChars(0x0310)))  // placementIdLow = 3
        val ref = e.buffer.getScreenRow(0).getCell(0).placeholder
        assertNotNull(ref)
        assertEquals(7, ref!!.imageId)
        assertEquals(1, ref.srcRow)
        assertEquals(2, ref.srcCol)
        assertEquals(3, ref.placementIdLow)
    }

    @Test
    fun normalCharAfterPlaceholderEndsDiacriticAcceptance() {
        val e = emu()
        e.feed("[38;2;0;0;1m$PLACEHOLDER")
        e.feed("X")  // 通常文字でステージ終了
        // 以後の combining mark は通常文字扱い (= 次セルに書かれる) で、 placeholder ref は不変
        e.feed(String(Character.toChars(0x030D)))
        val row0 = e.buffer.getScreenRow(0)
        val placeholderRef = row0.getCell(0).placeholder
        assertNotNull("placeholder cell stays intact", placeholderRef)
        assertEquals(0, placeholderRef!!.srcRow)
        assertEquals(0, placeholderRef.srcCol)
        assertEquals('X', row0.getCell(1).char)
        assertNull("normal char must not be a placeholder", row0.getCell(1).placeholder)
    }

    @Test
    fun multiplePlaceholdersOccupyConsecutiveCells() {
        val e = emu()
        // 同じ image id で 3 個並べる: 描画タイルは emulator では決定しないが、
        // 各セルが独立に placeholder を持ち、後段で diacritic を当てると個別に更新される。
        e.feed("[38;2;0;0;42m$PLACEHOLDER$PLACEHOLDER$PLACEHOLDER")
        val row0 = e.buffer.getScreenRow(0)
        for (c in 0..2) {
            val ref = row0.getCell(c).placeholder
            assertNotNull("col=$c should be a placeholder", ref)
            assertEquals(42, ref!!.imageId)
        }
        // 直前 placeholder は col=2 なので、 ここに diacritic を当てると col=2 だけ更新。
        e.feed(String(Character.toChars(0x030D)))  // srcRow = 1
        assertEquals(0, row0.getCell(0).placeholder!!.srcRow)
        assertEquals(0, row0.getCell(1).placeholder!!.srcRow)
        assertEquals(1, row0.getCell(2).placeholder!!.srcRow)
    }

    @Test
    fun textExtractionReplacesPlaceholderWithSpace() {
        val e = emu()
        e.feed("[38;2;0;0;1m$PLACEHOLDER" + "X")
        val text = e.buffer.getScreenRow(0).toText()
        // placeholder は ' ' に置換され、後続の通常文字はそのまま残る
        assertEquals(" X", text)
    }

    @Test
    fun underlineColorAddsUpperEightBitsOfImageId() {
        val e = emu()
        // fg = RGB(0x12, 0x34, 0x56) → 下位 24bit = 0x123456
        // underline = RGB(0xAB, 0, 0) → 上位 8bit = 0xAB
        // 期待: imageId = 0xAB123456 (Int としては負値)
        e.feed("[38;2;18;52;86m")
        e.feed("[58;2;171;0;0m")  // SGR 58:2:R:G:B
        e.feed(PLACEHOLDER)
        val ref = e.buffer.getScreenRow(0).getCell(0).placeholder
        assertNotNull(ref)
        assertEquals(0xAB123456.toInt(), ref!!.imageId)
    }

    @Test
    fun sgr59ResetsUnderlineColorSoImageIdStays24bit() {
        val e = emu()
        e.feed("[38;2;0;0;7m")
        e.feed("[58;2;255;0;0m")  // ひとまず上位 8bit を有効化
        e.feed("[59m")            // underline color reset
        e.feed(PLACEHOLDER)
        val ref = e.buffer.getScreenRow(0).getCell(0).placeholder
        assertNotNull(ref)
        assertEquals(7, ref!!.imageId)
    }

    @Test
    fun sgrResetClearsUnderlineColorToo() {
        val e = emu()
        e.feed("[58;2;200;0;0m")    // underline color を一旦設定
        e.feed("[0m")               // SGR 0 = 全リセット
        e.feed("[38;2;0;0;5m")
        e.feed(PLACEHOLDER)
        val ref = e.buffer.getScreenRow(0).getCell(0).placeholder
        assertNotNull(ref)
        assertEquals(5, ref!!.imageId)
    }

    @Test
    fun overwritingPlaceholderCellWithNormalCharClearsRef() {
        val e = emu()
        e.feed("[38;2;0;0;1m$PLACEHOLDER")
        // 1 セル戻って通常文字で上書き
        e.feed("[1D")  // CUB 1
        e.feed("Z")
        val cell = e.buffer.getScreenRow(0).getCell(0)
        assertEquals('Z', cell.char)
        assertNull("placeholder ref must be cleared when overwritten", cell.placeholder)
    }
}
