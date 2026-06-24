package com.zerotoship.z2term.emulator

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kitty graphics protocol (APC `ESC _ G ... ESC \`) パーサの基本動作テスト。
 *
 * `Image` を返すケースは [android.graphics.BitmapFactory] が unit test 環境
 * (Robolectric なし) で null を返す前提なので、本テストは:
 *  - `m=1` のチャンク継続 → `Continue`
 *  - `a=d` の全削除 → `ClearAll`
 *  - 未対応の `a=t` / `f=24` / `t=f` → `Discard`
 *  - APC `G` 以外で始まる不正本文 → `Discard`
 *  - 異常終端 (ST 不正) でも reset すれば次のシーケンスは独立に扱える
 *  - 単発で base64 が正しくても (unit test では Bitmap 化できないため) `Discard`
 * の 6 系統のみ固定する。
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
        // 2 番目のチャンクも m=1
        feed(p, "Gm=1;BBBB")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Continue, r)
    }

    @Test
    fun deleteActionReturnsClearAll() {
        val p = KittyGraphicsParser()
        feed(p, "Ga=d,d=A;")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.ClearAll, r)
    }

    @Test
    fun transmitOnlyActionIsDiscarded() {
        // a=t (transmit only, no display) は本実装は描画しない → Discard。
        val p = KittyGraphicsParser()
        feed(p, "Ga=t,f=100,t=d;AAAA")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun unsupportedFormatIsDiscarded() {
        // f=24 (生 RGB) は未対応 → Discard。
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=24,t=d,s=10,v=10;AAAA")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun unsupportedTransmissionIsDiscarded() {
        // t=f (file) は未対応 → Discard。
        val p = KittyGraphicsParser()
        feed(p, "Ga=T,f=100,t=f;dHJhc2g=")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.Discard, r)
    }

    @Test
    fun nonKittyApcBodyIsDiscarded() {
        // 先頭が `G` でない APC 本文は Kitty graphics ではない → Discard。
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
        // ここで異常終了 → reset で破棄
        p.reset()
        // 次の単発シーケンスは独立に扱われる (前回チャンクの header を引き継がない)
        feed(p, "Ga=d;")
        val r = p.finishSequence(12f, 24f)
        assertSame(KittyGraphicsParser.Result.ClearAll, r)
    }

    @Test
    fun pngPayloadFallsBackToDiscardWhenBitmapCannotBeDecoded() {
        // unit test 環境では BitmapFactory.decodeByteArray が null を返すか例外を投げる。
        // 実装は runCatching でラップしてあるので、いずれにせよ Discard へ落ちることを保証。
        val p = KittyGraphicsParser()
        // 有効な base64 (中身は PNG ではないが decode は通る)
        feed(p, "Ga=T,f=100,t=d;aGVsbG8=")
        val r = p.finishSequence(12f, 24f)
        assertTrue(
            "expected Discard, got $r",
            r is KittyGraphicsParser.Result.Discard
        )
    }
}
