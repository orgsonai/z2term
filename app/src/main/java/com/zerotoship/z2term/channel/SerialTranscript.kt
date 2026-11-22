package com.zerotoship.z2term.channel

/**
 * シリアルで**受け取った**バイトの控え (1.0.208)。AI の `serial_send` / `serial_read` が使う。
 *
 * 画面 (`z2-session capture`) から読まないのは、送ったあとに「何が返ってきたか」だけを切り出せないため。
 * 画面は折り返し・上書き・スクロールで形が変わるが、ここは届いた順のバイトをそのまま持つ。
 *
 * [total] は通算の受信バイト数。送る直前に控えておき、[since] でそれ以降に届いた分だけを取り出す。
 * 古い分は [capacity] を超えたところから捨てる (取り出せるのは最後の [capacity] バイトまで)。
 * 書くのは受信スレッド、読むのは AI の呼び出しのスレッドなので、全部 `synchronized` で守る。
 */
class SerialTranscript(private val capacity: Int = DEFAULT_CAPACITY) {
    private val ring = ByteArray(capacity)
    private var start = 0
    private var size = 0
    private var written = 0L

    /** 通算の受信バイト数。 */
    val total: Long get() = synchronized(this) { written }

    fun append(data: ByteArray) = synchronized(this) {
        if (data.isEmpty()) return@synchronized
        written += data.size
        // 控えより大きい塊は、後ろの capacity バイトだけ残せば足りる。
        val from = maxOf(0, data.size - capacity)
        for (i in from until data.size) {
            val at = (start + size) % capacity
            ring[at] = data[i]
            if (size < capacity) size++ else start = (start + 1) % capacity
        }
    }

    /**
     * 通算 [mark] バイト目より後に届いた分。控えから消えた部分は返せないので、そのぶん短くなり、
     * [Slice.lost] が true になる。
     */
    fun since(mark: Long): Slice = synchronized(this) {
        val want = (written - mark).coerceAtLeast(0L)
        val have = minOf(want, size.toLong()).toInt()
        Slice(copyLast(have), lost = want > have)
    }

    /** 最後の [n] バイト。 */
    fun tail(n: Int): ByteArray = synchronized(this) { copyLast(minOf(n.coerceAtLeast(0), size)) }

    private fun copyLast(n: Int): ByteArray {
        val out = ByteArray(n)
        val first = start + size - n
        for (i in 0 until n) out[i] = ring[(first + i) % capacity]
        return out
    }

    class Slice(val bytes: ByteArray, val lost: Boolean)

    companion object {
        const val DEFAULT_CAPACITY = 64 * 1024
    }
}
