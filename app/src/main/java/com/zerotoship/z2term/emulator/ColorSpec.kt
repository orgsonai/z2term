package com.zerotoship.z2term.emulator

/**
 * OSC で使われる色指定文字列を ARGB int へパースする。
 *
 * 対応書式:
 *   - `rgb:RRRR/GGGG/BBBB` (xterm 16bit 表記)
 *   - `rgb:RR/GG/BB`       (8bit)
 *   - `#RRGGBB`            (8bit)
 *   - `#RRRRGGGGBBBB`      (16bit)
 *
 * 解析できない場合は null を返す。
 */
object ColorSpec {

    fun parse(spec: String): Int? {
        val s = spec.trim()
        return when {
            s.startsWith("rgb:") -> parseRgbForm(s.substring(4))
            s.startsWith("#") -> parseHashForm(s.substring(1))
            else -> null
        }
    }

    private fun parseRgbForm(body: String): Int? {
        val parts = body.split('/')
        if (parts.size != 3) return null
        val r = parseHexComponent(parts[0]) ?: return null
        val g = parseHexComponent(parts[1]) ?: return null
        val b = parseHexComponent(parts[2]) ?: return null
        return argb(r, g, b)
    }

    private fun parseHashForm(body: String): Int? = when (body.length) {
        6 -> {
            val r = body.substring(0, 2).toIntOrNull(16) ?: return null
            val g = body.substring(2, 4).toIntOrNull(16) ?: return null
            val b = body.substring(4, 6).toIntOrNull(16) ?: return null
            argb(r, g, b)
        }
        12 -> {
            val r = body.substring(0, 4).toIntOrNull(16)?.let { it shr 8 } ?: return null
            val g = body.substring(4, 8).toIntOrNull(16)?.let { it shr 8 } ?: return null
            val b = body.substring(8, 12).toIntOrNull(16)?.let { it shr 8 } ?: return null
            argb(r, g, b)
        }
        else -> null
    }

    /** xterm 流の "RRRR" や "RR" を 0–255 に圧縮 */
    private fun parseHexComponent(hex: String): Int? {
        if (hex.isEmpty() || hex.length > 4) return null
        val v = hex.toIntOrNull(16) ?: return null
        return when (hex.length) {
            1 -> (v * 0xFF) / 0xF      // 1 桁 → 0-255
            2 -> v                      // 既に 8 bit
            3 -> (v * 0xFF) / 0xFFF
            4 -> v shr 8                // 16 bit → 8 bit
            else -> null
        }
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
