package com.zerotoship.z2term.emulator

/**
 * バイト列を UTF-8 として解釈し、コードポイント単位で取り出すためのストリームデコーダ。
 *
 * 受信が途中で切れる場合 (PTY からの partial read) も内部に保留して
 * 次回呼び出し時に続きを処理できる。
 *
 * 不正バイト・継続不足は U+FFFD (REPLACEMENT CHARACTER) として通知する。
 */
class Utf8Decoder {
    private var pending: Int = 0       // 蓄積中のコードポイント
    private var remaining: Int = 0      // 残り継続バイト数
    private var minValue: Int = 0       // overlong 検出用の最小値

    /**
     * 1 バイトを与え、コードポイントが完成すれば返す。
     * 未完成なら null、エラー時は 0xFFFD を返す。
     */
    fun feed(byte: Int): Int? {
        val b = byte and 0xFF
        if (remaining == 0) {
            return when {
                b < 0x80 -> b                                // ASCII
                b < 0xC0 -> REPLACEMENT                     // 継続バイトが先頭に来た
                b < 0xE0 -> { startSeq(b and 0x1F, 1, 0x80); null }
                b < 0xF0 -> { startSeq(b and 0x0F, 2, 0x800); null }
                b < 0xF8 -> { startSeq(b and 0x07, 3, 0x10000); null }
                else -> REPLACEMENT
            }
        } else {
            if ((b and 0xC0) != 0x80) {
                // 継続バイトでない → エラー、状態リセットして再評価
                reset()
                return REPLACEMENT
            }
            pending = (pending shl 6) or (b and 0x3F)
            remaining--
            if (remaining == 0) {
                val cp = pending
                val min = minValue
                reset()
                if (cp < min || cp > 0x10FFFF || (cp in 0xD800..0xDFFF)) return REPLACEMENT
                return cp
            }
            return null
        }
    }

    private fun startSeq(initial: Int, remainingBytes: Int, min: Int) {
        pending = initial
        remaining = remainingBytes
        minValue = min
    }

    fun reset() {
        pending = 0
        remaining = 0
        minValue = 0
    }

    companion object {
        const val REPLACEMENT = 0xFFFD
    }
}
