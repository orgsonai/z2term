package com.zerotoship.z2term.gui.direct

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** XWD v7 header. Xvfb writes header words in network order and declares pixel order separately. */
internal data class XwdFrame(val width: Int, val height: Int, val offset: Long, val order: ByteOrder) {
    val bytes: Int get() = width * height * 4
    companion object {
        fun parse(header: ByteArray, length: Long): XwdFrame {
            require(header.size >= 100) { "Incomplete XWD header" }
            val h = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            fun word(index: Int) = h.getInt(index * 4)
            require(word(1) == 7 && word(2) == 2 && word(3) == 24) { "Expected XWD v7, ZPixmap, depth 24" }
            val width = word(4); val height = word(5)
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 8_388_608) { "Direct display is limited to 8 million pixels" }
            require(word(6) == 0 && word(11) == 32 && word(12) == width * 4 && word(13) == 4) { "Unsupported XWD pixel layout" }
            require(word(14) == 0xFF0000 && word(15) == 0xFF00 && word(16) == 0xFF) { "Unsupported XWD color masks" }
            val headerSize = word(0); val colors = word(19)
            require(headerSize in 100..65536 && colors in 0..65536)
            val offset = headerSize.toLong() + colors.toLong() * 12
            require(length >= offset + width.toLong() * height * 4) { "Incomplete XWD framebuffer" }
            val order = when (word(7)) { 0 -> ByteOrder.LITTLE_ENDIAN; 1 -> ByteOrder.BIG_ENDIAN; else -> error("Invalid XWD byte order") }
            return XwdFrame(width, height, offset, order)
        }
    }
}
