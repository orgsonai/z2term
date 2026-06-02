package com.zerotoship.z2term.ui.terminal.keyboard

import java.io.InputStream
import java.nio.ByteOrder

/**
 * 集合membership 判定用の Bloom フィルタ (mozc の `ExistenceFilter` 相当)。
 * Phase 4 の共起 (コロケーション) 辞書を、偽陽性ありの省メモリ集合として保持する。
 *
 * バイナリ形式 ([scripts/build-collocation.sh] が生成、リトルエンディアン):
 *   magic  "KCB1" (4 bytes) / m_bits uint32 / k uint32 / n uint32 / bits ceil(m_bits/8) bytes
 *
 * ハッシュは **FNV-1a 64bit を 1 回**計算し、その上下 32bit を 2 つのハッシュとみなす二重ハッシュ
 * (Kirsch-Mitzenmacher)。生成側 (Python) と完全に同じ計算でなければ引けないので変更時は両方直すこと。
 */
class ExistenceFilter private constructor(
    private val bits: ByteArray,
    private val mBits: Int,
    private val k: Int,
    /** 登録要素数 (参考値)。 */
    val size: Int,
) {
    fun mayContain(key: String): Boolean {
        val h = fnv1a64(key)
        val a = h and 0xFFFFFFFFuL
        var b = (h shr 32) and 0xFFFFFFFFuL
        if (b == 0uL) b = 1uL
        val m = mBits.toULong()
        for (i in 0 until k) {
            val pos = ((a + i.toULong() * b) % m).toInt()
            if ((bits[pos ushr 3].toInt() ushr (pos and 7)) and 1 == 0) return false
        }
        return true
    }

    private fun fnv1a64(key: String): ULong {
        var h = 0xCBF29CE484222325uL
        val bytes = key.encodeToByteArray()
        for (byte in bytes) {
            h = h xor (byte.toInt() and 0xFF).toULong()
            h *= 0x100000001B3uL
        }
        return h
    }

    companion object {
        fun load(input: InputStream): ExistenceFilter {
            val all = input.readBytes()
            val bb = java.nio.ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { bb.get(it) }
            require(magic.contentEquals("KCB1".toByteArray(Charsets.US_ASCII))) {
                "bad collocation bloom magic"
            }
            val mBits = bb.int
            val k = bb.int
            val n = bb.int
            val bits = ByteArray(all.size - 16)
            bb.get(bits)
            return ExistenceFilter(bits, mBits, k, n)
        }
    }
}
