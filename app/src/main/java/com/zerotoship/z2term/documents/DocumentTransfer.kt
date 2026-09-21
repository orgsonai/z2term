package com.zerotoship.z2term.documents

import java.io.InputStream
import java.io.OutputStream

/** Byte streams stay byte streams; no text decoding or shell interpolation. */
internal object DocumentTransfer {
    const val MAX_BYTES = 512L * 1024 * 1024
    fun copy(input: InputStream, output: OutputStream, active: () -> Boolean = { true }): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            check(active()) { "Document operation cancelled" }
            val n = input.read(buffer)
            if (n < 0) return total
            total += n
            require(total <= MAX_BYTES) { "File exceeds 512 MiB" }
            output.write(buffer, 0, n)
        }
    }
}
