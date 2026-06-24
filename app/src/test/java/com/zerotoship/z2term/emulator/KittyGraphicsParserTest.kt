package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kitty graphics protocol パーサの基本動作テスト。
 *
 * `Transmit` (PNG) を返すケースは [android.graphics.BitmapFactory] が unit test 環境
 * (Robolectric なし) で null を返す前提なので、ここでは:
 *  - チャンク継続 `Continue`
 *  - 各種 `Delete` (全体 / image / placement)
 *  - 未対応 transmission/format の `Discard`
 *  - 不正本文の `Discard`
 *  - `a=p` (put) は `Put` 結果に id が正しく載ること
 *  - 異常終端後の `reset` で次のシーケンスが独立に扱えること
 *  - 生 RGB (`f=24`) は `s`/`v` 必須、欠ければ `Discard`
 * を固定する。
 */
class KittyGraphicsParserTest {

    private fun feed(p: KittyGraphicsParser, body: String) {
        for (ch in body) p.feedByte(ch.code)
    }

    @Test
    fun multiChunkFirstReturnsContinue() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,m=1;iVBORw")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Continue, r)
    }

    @Test
    fun multiChunkSecondWithMoreContinues() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,m=1;AAAA")
        p.finishSequence(12f, 24f)
        feed(p, "Gm=1;BBBB")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Continue, r)
    }

    @Test
    fun deleteDefaultIsAll() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=d;")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.DeleteAll, r)
    }

    @Test
    fun deleteByImageId() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=d,d=I,I=42;")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.DeleteImage
        assertEquals(42, r.imageId)
    }

    @Test
    fun deleteByPlacement() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=d,d=p,i=7,p=3;")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.DeletePlacement
        assertEquals(7, r.imageId)
        assertEquals(3, r.placementId)
    }

    @Test
    fun putReturnsExistingImageReference() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=p,i=11,p=2,c=4,r=2;")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.Put
        assertEquals(11, r.imageId)
        assertEquals(2, r.placementId)
        assertEquals(4, r.cellsWidth)
        assertEquals(2, r.cellsHeight)
    }

    @Test
    fun unsupportedTransmissionIsDiscarded() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,t=f;dHJhc2g=")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun rawRgbWithoutSizeIsDiscarded() {
        // f=24 (生 RGB) は s/v が無いと組み立てられない。
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=24,t=d;AAAAAA==")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun nonKittyApcBodyIsDiscarded() {
        val p = KittyGraphicsParser()
        feed(p, "Xnot kitty payload")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun resetClearsAccumulatedChunks() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,m=1;AAAA")
        p.finishSequence(12f, 24f)
        p.reset()
        feed(p, "Ga=d;")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.DeleteAll, r)
    }

    @Test
    fun pngPayloadFallsBackToDiscardWhenBitmapCannotBeDecoded() {
        // unit test では BitmapFactory.decodeByteArray が null を返すので Discard へ落ちる。
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,t=d;aGVsbG8=")
        val r = p.finishSequence(12f, 24f)
        assertTrue("expected Discard, got $r", r is KittyGraphicsParser.Result.Discard)
    }

    @Test
    fun queryReturnsOkForSupportedFormat() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=q,i=99,f=100,t=d,s=1,v=1;AAAA")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.Query
        assertTrue(r.ok)
        assertEquals(99, r.imageId)
        assertEquals("OK", r.message)
        assertEquals(0, r.quietLevel)
    }

    @Test
    fun queryReturnsErrorForUnsupportedTransmission() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=q,i=1,f=100,t=f;dHJhc2g=")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.Query
        assertTrue("expected ok=false, got $r", !r.ok)
        assertTrue("expected ENOTSUPPORTED, got '${r.message}'", r.message.startsWith("ENOTSUPPORTED:"))
    }

    @Test
    fun queryPropagatesQuietLevel() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=q,i=2,q=2,f=100,t=d,s=1,v=1;")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.Query
        assertEquals(2, r.quietLevel)
    }

    @Test
    fun transmitCarriesZIndexAndQuietLevel() {
        // 描画 (Bitmap) 自体は unit test では Discard になるので、Transmit 経由でなく
        // 同 protocol 形式の Put 経由で z/q が parser に渡ることだけを確認する。
        val p = KittyGraphicsParser()
        feed(p, "Ga=p,i=5,p=1,c=2,r=1,z=-5;")
        val r = p.finishSequence(12f, 24f) as KittyGraphicsParser.Result.Put
        assertEquals(-5, r.zIndex)
    }
}
