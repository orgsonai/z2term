package com.zerotoship.z2term.gui.rdp

/** RFC 1320 の MD4。Android/JCA が MD4 を保証しないため NTLMv2 用に one-shot 実装する。 */
internal object Md4 {
    fun digest(message: ByteArray): ByteArray {
        val padding = (56 - (message.size + 1) % 64 + 64) % 64
        val input = ByteArray(message.size + 1 + padding + 8)
        message.copyInto(input)
        input[message.size] = 0x80.toByte()
        val bitLength = message.size.toLong() * 8L
        repeat(8) { input[input.size - 8 + it] = (bitLength ushr (8 * it)).toByte() }

        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476
        val x = IntArray(16)
        for (block in input.indices step 64) {
            repeat(16) { i ->
                val p = block + i * 4
                x[i] = (input[p].toInt() and 0xFF) or
                    ((input[p + 1].toInt() and 0xFF) shl 8) or
                    ((input[p + 2].toInt() and 0xFF) shl 16) or
                    ((input[p + 3].toInt() and 0xFF) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0

            // Round 1.
            a = ff(a, b, c, d, x[0], 3); d = ff(d, a, b, c, x[1], 7)
            c = ff(c, d, a, b, x[2], 11); b = ff(b, c, d, a, x[3], 19)
            a = ff(a, b, c, d, x[4], 3); d = ff(d, a, b, c, x[5], 7)
            c = ff(c, d, a, b, x[6], 11); b = ff(b, c, d, a, x[7], 19)
            a = ff(a, b, c, d, x[8], 3); d = ff(d, a, b, c, x[9], 7)
            c = ff(c, d, a, b, x[10], 11); b = ff(b, c, d, a, x[11], 19)
            a = ff(a, b, c, d, x[12], 3); d = ff(d, a, b, c, x[13], 7)
            c = ff(c, d, a, b, x[14], 11); b = ff(b, c, d, a, x[15], 19)

            // Round 2.
            a = gg(a, b, c, d, x[0], 3); d = gg(d, a, b, c, x[4], 5)
            c = gg(c, d, a, b, x[8], 9); b = gg(b, c, d, a, x[12], 13)
            a = gg(a, b, c, d, x[1], 3); d = gg(d, a, b, c, x[5], 5)
            c = gg(c, d, a, b, x[9], 9); b = gg(b, c, d, a, x[13], 13)
            a = gg(a, b, c, d, x[2], 3); d = gg(d, a, b, c, x[6], 5)
            c = gg(c, d, a, b, x[10], 9); b = gg(b, c, d, a, x[14], 13)
            a = gg(a, b, c, d, x[3], 3); d = gg(d, a, b, c, x[7], 5)
            c = gg(c, d, a, b, x[11], 9); b = gg(b, c, d, a, x[15], 13)

            // Round 3.
            a = hh(a, b, c, d, x[0], 3); d = hh(d, a, b, c, x[8], 9)
            c = hh(c, d, a, b, x[4], 11); b = hh(b, c, d, a, x[12], 15)
            a = hh(a, b, c, d, x[2], 3); d = hh(d, a, b, c, x[10], 9)
            c = hh(c, d, a, b, x[6], 11); b = hh(b, c, d, a, x[14], 15)
            a = hh(a, b, c, d, x[1], 3); d = hh(d, a, b, c, x[9], 9)
            c = hh(c, d, a, b, x[5], 11); b = hh(b, c, d, a, x[13], 15)
            a = hh(a, b, c, d, x[3], 3); d = hh(d, a, b, c, x[11], 9)
            c = hh(c, d, a, b, x[7], 11); b = hh(b, c, d, a, x[15], 15)

            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }
        return ByteArray(16).also { out ->
            putIntLe(out, 0, a0)
            putIntLe(out, 4, b0)
            putIntLe(out, 8, c0)
            putIntLe(out, 12, d0)
        }
    }

    private fun f(x: Int, y: Int, z: Int): Int = (x and y) or (x.inv() and z)
    private fun g(x: Int, y: Int, z: Int): Int = (x and y) or (x and z) or (y and z)
    private fun h(x: Int, y: Int, z: Int): Int = x xor y xor z
    private fun ff(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int) =
        Integer.rotateLeft(a + f(b, c, d) + x, s)
    private fun gg(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int) =
        Integer.rotateLeft(a + g(b, c, d) + x + 0x5A827999, s)
    private fun hh(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int) =
        Integer.rotateLeft(a + h(b, c, d) + x + 0x6ED9EBA1, s)

    private fun putIntLe(out: ByteArray, offset: Int, value: Int) {
        repeat(4) { out[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
