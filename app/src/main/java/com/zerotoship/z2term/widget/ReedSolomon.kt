package com.zerotoship.z2term.widget

/**
 * QR コードの誤り訂正符号 (Reed-Solomon) を作る。[QrEncoder] から使う。
 *
 * QR は GF(256) を原始多項式 `x^8 + x^4 + x^3 + x^2 + 1` (= 0x11D) で作る体の上で計算する。
 * ここは自前実装で**検算しにくい**ので、規格の既知例 (型番 1-M の "01234567") を
 * `QrEncoderTest` に入れてある。ここが壊れると**読めない QR が黙って出る**。
 */
internal object ReedSolomon {

    /** α^i の表 (添字が 255 を超えても引けるように 2 周ぶん持つ)。 */
    private val EXP = IntArray(512)

    /** log_α の表。 */
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    /**
     * 次数 [degree] の生成多項式 g(x) = Π(x - α^i)。係数は次数の高い順で、先頭は必ず 1。
     */
    private fun generator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]                 // poly * x
                next[j + 1] = next[j + 1] xor mul(poly[j], EXP[i])  // poly * α^i
            }
            poly = next
        }
        return poly
    }

    /** [data] に対する [ecLength] 個の誤り訂正コードワード (多項式の剰余)。 */
    fun encode(data: IntArray, ecLength: Int): IntArray {
        val gen = generator(ecLength)
        val work = IntArray(data.size + ecLength)
        data.copyInto(work)
        for (i in data.indices) {
            val factor = work[i]
            if (factor == 0) continue
            for (j in gen.indices) {
                work[i + j] = work[i + j] xor mul(gen[j], factor)
            }
        }
        return work.copyOfRange(data.size, work.size)
    }
}
