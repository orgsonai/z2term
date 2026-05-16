package com.zerotoship.z2term.emulator

/**
 * SGR (Select Graphic Rendition) 属性。
 * セルあたりの装飾属性を 32bit にパックして保持。
 *
 * Bit layout:
 *   bit  0-23: 前景色 (RGB 24bit) または ANSI インデックス
 *   bit 24-29: 装飾フラグ (bold/italic/underline 等)
 *   bit 30-31: 前景色フォーマット (0=default, 1=ANSI16, 2=ANSI256, 3=RGB)
 *
 * 背景色は別の Int に同じレイアウトで格納。
 */
object SgrAttribute {
    const val FLAG_BOLD = 1 shl 24
    const val FLAG_ITALIC = 1 shl 25
    const val FLAG_UNDERLINE = 1 shl 26
    const val FLAG_BLINK = 1 shl 27
    const val FLAG_INVERSE = 1 shl 28
    const val FLAG_STRIKE = 1 shl 29

    const val FORMAT_MASK = 3 shl 30
    const val FORMAT_DEFAULT = 0 shl 30
    const val FORMAT_INDEXED = 1 shl 30   // ANSI 16/256 共通
    const val FORMAT_RGB = 2 shl 30

    const val COLOR_MASK = 0xFFFFFF

    fun makeIndexed(index: Int): Int = FORMAT_INDEXED or (index and COLOR_MASK)
    fun makeRgb(r: Int, g: Int, b: Int): Int =
        FORMAT_RGB or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun isDefault(attr: Int): Boolean = (attr and FORMAT_MASK) == FORMAT_DEFAULT
    fun isIndexed(attr: Int): Boolean = (attr and FORMAT_MASK) == FORMAT_INDEXED
    fun isRgb(attr: Int): Boolean = (attr and FORMAT_MASK) == FORMAT_RGB

    fun getIndex(attr: Int): Int = attr and COLOR_MASK
    fun getR(attr: Int): Int = (attr shr 16) and 0xFF
    fun getG(attr: Int): Int = (attr shr 8) and 0xFF
    fun getB(attr: Int): Int = attr and 0xFF

    fun hasFlag(attr: Int, flag: Int): Boolean = (attr and flag) != 0

    /** デフォルト属性 (装飾なし、色はdefault) */
    const val DEFAULT = FORMAT_DEFAULT
}

/**
 * 単一セルの情報。
 *
 * char 32bit + fgAttr 32bit + bgAttr 32bit = 12 bytes/セル
 * 80×24 = 1920 セル → 約 23KB / 画面
 * バッファ 5000 行なら ~480 MB ?? いや 80×5000=400000セル×12=4.8MB
 */
data class TerminalCell(
    var char: Char = ' ',
    var fgAttr: Int = SgrAttribute.DEFAULT,
    var bgAttr: Int = SgrAttribute.DEFAULT
) {
    fun copyFrom(other: TerminalCell) {
        char = other.char
        fgAttr = other.fgAttr
        bgAttr = other.bgAttr
    }

    fun clear() {
        char = ' '
        fgAttr = SgrAttribute.DEFAULT
        bgAttr = SgrAttribute.DEFAULT
    }

    fun setClearedWith(fg: Int, bg: Int) {
        char = ' '
        fgAttr = fg
        bgAttr = bg
    }
}
