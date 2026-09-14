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

internal object QrImages {
    fun encode(text: String): Bitmap {
        require(text.toByteArray(Charsets.UTF_8).size <= 2000)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
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
