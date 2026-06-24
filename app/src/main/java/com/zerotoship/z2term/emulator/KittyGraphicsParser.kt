package com.zerotoship.z2term.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Kitty graphics protocol (APC `ESC _ G <key=value,…> ; <base64 payload> ESC \`) の
 * 最小実装パーサ。
 *
 * 仕様: https://sw.kovidgoyal.net/kitty/graphics-protocol/
 *
 * 対応範囲 (本実装):
 *  - `a=T` (transmit and display) のみ。`a=t`/`a=p`/`a=d`/`a=q` 等は破棄 (削除一括は
 *    呼び出し側で `clearAllImages` を別途呼ぶ)。
 *  - `f=100` (PNG) のみ。`f=24`/`f=32` (生 RGB/RGBA) は未対応で破棄。
 *  - `t=d` (direct, base64) のみ。`t=f`/`t=t`/`t=s` 等は破棄。
 *  - `m=1` 連続 + `m=0` 終端のチャンク連結に対応。`m` 省略は単発扱い。
 *  - 表示セル数は **最初の APC ヘッダの `c=N` / `r=N`** を使う。指定が無いときは
 *    Bitmap のピクセルサイズと呼び出し側ヒント (`cellWidthPx`/`lineHeightPx`) から
 *    自動算出する (最低 1 セル、上限なし)。
 *  - `i=N` (image id) を保持する (削除コマンドからの照合用)。
 *  - その他のキー (`p`, `q`, `z`, `X`, `Y`, `s`, `v` …) は読み飛ばす。
 *
 * 本実装は最低限「画像が出る」ことを目的とし、frame 動画 / virtual placement /
 * Unicode placeholder / file 転送等は未対応。
 */
class KittyGraphicsParser {

    /** APC `_` を受けてから ST/BEL を受けるまでの 1 シーケンス分の本文を貯めるバッファ。 */
    private val current = StringBuilder(2048)

    /** チャンク連結中の base64 文字列 (除去済み)。 */
    private val payload = StringBuilder(2048)

    /** チャンク連結中の最初のヘッダから取り出した key=value (`c`,`r`,`i` 等)。 */
    private var headerKeys: Map<String, String> = emptyMap()

    /** 進行中チャンクがあるか。 */
    private var inMultiChunk = false

    /**
     * APC 本文 1 バイトを受け取り蓄積する。
     *
     * @param b 0x00..0xFF。本実装は ASCII 範囲しか期待しないが、APC 本文に non-ASCII が
     *          紛れていても StringBuilder にそのまま積むだけ (どのみち最後に破棄するか
     *          base64 デコードするので、key=value/base64 文字 outside の異物は無視される)。
     */
    fun feedByte(b: Int) {
        if (current.length > MAX_BUFFER_BYTES) return  // 安全上の上限 (パニック防止)
        current.append(b.toChar())
    }

    /**
     * APC 終端 (BEL/ST) で呼ばれる。 完成画像 (`Result`) を返すか、 追加チャンク待ち
     * (`Result.Continue`) か、不要として破棄 (`Result.Discard`) を返す。
     *
     * @param cellWidthPx 1 セルの幅 (px)。 `c=N` 指定なしのとき自動算出に使う。
     * @param lineHeightPx 1 セルの高さ (px)。 `r=N` 指定なしのとき自動算出に使う。
     */
    fun finishSequence(cellWidthPx: Float, lineHeightPx: Float): Result {
        val raw = current.toString()
        current.clear()

        // APC 本文は `G<keys>;<base64>` の形。先頭 `G` でなければ Kitty graphics ではない。
        if (raw.isEmpty() || raw[0] != 'G') return Result.Discard

        val semi = raw.indexOf(';')
        val keysPart = if (semi >= 0) raw.substring(1, semi) else raw.substring(1)
        val bodyPart = if (semi >= 0) raw.substring(semi + 1) else ""

        val keys = parseKeys(keysPart)

        // 初回チャンクならヘッダを覚える。後続チャンクは payload 連結のみ。
        if (!inMultiChunk) headerKeys = keys

        // base64 payload を蓄積 (改行・空白は base64 decode 前に取り除く)。
        payload.append(bodyPart.filter { it != '\n' && it != '\r' && it != ' ' })

        val moreChunks = (keys["m"] ?: "0") == "1"
        if (moreChunks) {
            inMultiChunk = true
            return Result.Continue
        }

        // 終端: ここで決定。
        val header = headerKeys
        val payloadStr = payload.toString()
        headerKeys = emptyMap()
        payload.clear()
        inMultiChunk = false

        val action = header["a"] ?: "T"
        val format = header["f"]?.toIntOrNull() ?: 100
        val transmission = header["t"] ?: "d"
        val imageId = header["i"]?.toIntOrNull() ?: 0

        // a=d (delete) は全消去だけ最小対応。サブパラメータの個別 ID/Z-index 削除は未対応。
        if (action == "d") return Result.ClearAll

        // 本実装が描画対象とするのは a=T, f=100, t=d のみ。他は静かに破棄。
        if (action != "T" || format != 100 || transmission != "d") return Result.Discard

        val bytes = decodeBase64(payloadStr) ?: return Result.Discard
        // BitmapFactory は Android framework が必要。 unit test (Robolectric なし) で
        // 例外を吐く環境でも安全に Discard へ落ちるように runCatching で包む。
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            .getOrNull() ?: return Result.Discard

        val cellsW = header["c"]?.toIntOrNull()
            ?: estimateCells(bitmap.width.toFloat(), cellWidthPx)
        val cellsH = header["r"]?.toIntOrNull()
            ?: estimateCells(bitmap.height.toFloat(), lineHeightPx)

        return Result.Image(
            bitmap = bitmap,
            widthCells = cellsW.coerceAtLeast(1),
            heightCells = cellsH.coerceAtLeast(1),
            imageId = imageId
        )
    }

    /** 進行中のチャンクをリセット (異常終端の救済)。 */
    fun reset() {
        current.clear()
        payload.clear()
        headerKeys = emptyMap()
        inMultiChunk = false
    }

    private fun parseKeys(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        val out = HashMap<String, String>(8)
        for (kv in s.split(',')) {
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val k = kv.substring(0, eq)
            val v = kv.substring(eq + 1)
            out[k] = v
        }
        return out
    }

    private fun decodeBase64(s: String): ByteArray? {
        if (s.isEmpty()) return null
        return runCatching { Base64.decode(s, Base64.DEFAULT) }.getOrNull()
    }

    private fun estimateCells(pixels: Float, perCell: Float): Int {
        if (perCell <= 0f) return 1
        return (pixels / perCell + 0.5f).toInt().coerceAtLeast(1)
    }

    sealed class Result {
        /** チャンク継続 (まだデータが来る)。 呼び出し側は次の APC を待つ。 */
        data object Continue : Result()
        /** 破棄 (未対応 / 不正)。 呼び出し側は何もしない。 */
        data object Discard : Result()
        /** 画像全消去 (`a=d`)。 呼び出し側は `buffer.clearAllImages()` を呼ぶ。 */
        data object ClearAll : Result()
        /** 描画用画像。 呼び出し側はカーソル位置に commit する。 */
        class Image(
            val bitmap: Bitmap,
            val widthCells: Int,
            val heightCells: Int,
            val imageId: Int
        ) : Result()
    }

    companion object {
        /** 1 シーケンスあたりの最大バッファ (~8MiB 相当)。これを超えると以後を捨てる。 */
        private const val MAX_BUFFER_BYTES = 8 * 1024 * 1024
    }
}
