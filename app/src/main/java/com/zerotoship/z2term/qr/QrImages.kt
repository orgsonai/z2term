package com.zerotoship.z2term.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.ByteArrayOutputStream

internal object QrImages {
    /** 端末へ渡す QR の上限 (バイト)。[encode] の画面表示と同じ。 */
    const val MAX_BYTES = 2000

    fun encode(text: String): Bitmap {
        require(text.toByteArray(Charsets.UTF_8).size <= 2000)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * QR の**升目そのもの** (拡大前の白黒モジュール) を作る。余白 (quiet zone) 込み。
     *
     * ⚠ [encode] が使う `QRCodeWriter` は**指定した画素数へ引き伸ばした結果**しか返さないので、
     * 「1 升を何画素で描くか」を呼ぶ側が決められない。端末に出す QR は升が画素の整数倍でないと
     * 縁がにじんでカメラが読み落とすため、ここは低層の [Encoder] から升目を取る。
     * ⚠ 訂正レベルは **L** (`QRCodeWriter` の既定・`qrencode` の既定と同じ)。ここを上げると
     * 同じ内容でも升が増え、「前と同じ大きさで出ない」になる。
     */
    fun encodeModules(text: String, margin: Int = 4): Array<BooleanArray> {
        require(text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val code = Encoder.encode(text, ErrorCorrectionLevel.L,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val m = requireNotNull(code.matrix) { "no matrix" }
        val pad = margin.coerceIn(0, 8)
        val size = m.width + pad * 2
        return Array(size) { y ->
            BooleanArray(size) { x ->
                val mx = x - pad
                val my = y - pad
                mx >= 0 && my >= 0 && mx < m.width && my < m.height && m.get(mx, my).toInt() == 1
            }
        }
    }

    /**
     * 升目を PNG にする。1 升 = [scale] 画素の**整数倍**で描く (にじませない)。
     *
     * @param targetPx 収めたい一辺の画素数。これに入る最大の整数倍を選ぶ。
     */
    fun modulesToPng(modules: Array<BooleanArray>, targetPx: Int): ByteArray {
        val n = modules.size
        require(n > 0)
        val scale = (targetPx / n).coerceIn(2, 40)
        val px = n * scale
        val pixels = IntArray(px * px)
        for (y in 0 until px) {
            val row = modules[y / scale]
            val base = y * px
            for (x in 0 until px) {
                pixels[base + x] = if (row[x / scale]) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            }
        }
        val bmp = Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
        try {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    fun decode(context: Context, uri: Uri): List<String> {
        require(uri.scheme == "content")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Bound decoded pixels while preserving screenshot QR detail; ImageDecoder handles EXIF.
            val scale = minOf(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
        }
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            val results = runCatching { QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(source)), hints) }
                .recoverCatching { QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(source.invert())), hints) }.getOrThrow()
            return results.map { it.text }.filter { it.isNotBlank() && it.length <= QrContent.MAX_TEXT }.distinct()
        } finally { bitmap.recycle() }
    }
}
