package com.zerotoship.z2term.emulator

/**
 * East Asian Width 判定 (UTR #11 の W/F カテゴリ近似)。
 *
 * 完全な Unicode データベース照合ではなく、実用上カバー率の高い範囲のみを
 * 列挙する。CJK Unified Ideographs、Hangul、Hiragana/Katakana、Fullwidth Forms、
 * 主要絵文字、CJK 拡張面までを 2 セル幅として扱う。
 *
 * UTR #11 の "Ambiguous" カテゴリ (罫線素片など) は、CJK ロケールでは 2 セル、
 * それ以外では 1 セルとして表示するのが通例。設定で切替できるよう
 * [isWide] は `ambiguousAsWide` パラメータを受け取る。
 *
 * 制御文字 (0x00–0x1F, 0x7F)、ASCII、Latin/Cyrillic などは 1 セル。
 */
object EastAsianWidth {

    /** Wide / Fullwidth: 常に 2 セル */
    fun isWide(codepoint: Int, ambiguousAsWide: Boolean = false): Boolean {
        if (codepoint < 0x80) return false
        if (isWideStrict(codepoint)) return true
        if (ambiguousAsWide && isAmbiguous(codepoint)) return true
        return false
    }

    private fun isWideStrict(codepoint: Int): Boolean {
        if (codepoint < 0x1100) return false
        return when (codepoint) {
            in 0x1100..0x115F -> true
            in 0x2E80..0x303E -> true
            in 0x3041..0x33FF -> true
            in 0x3400..0x4DBF -> true
            in 0x4E00..0x9FFF -> true
            in 0xA000..0xA4CF -> true
            in 0xAC00..0xD7A3 -> true
            in 0xF900..0xFAFF -> true
            in 0xFE30..0xFE6F -> true
            in 0xFF00..0xFF60 -> true
            in 0xFFE0..0xFFE6 -> true
            in 0x1F300..0x1F64F -> true
            in 0x1F680..0x1F6FF -> true
            in 0x1F900..0x1F9FF -> true
            in 0x20000..0x2FFFD -> true
            in 0x30000..0x3FFFD -> true
            else -> false
        }
    }

    /**
     * Ambiguous (CJK ロケールで wide 扱いされる) 主要範囲。
     * UTR #11 から実用頻度の高いものを抽出。完全な照合は Unicode DB 必要。
     */
    private fun isAmbiguous(cp: Int): Boolean = when (cp) {
        // Latin-1 主要記号
        0x00A1, 0x00A4, 0x00A7, 0x00A8, 0x00AA, 0x00AD, 0x00AE,
        0x00B0, 0x00B1, 0x00B2, 0x00B3, 0x00B4, 0x00B6, 0x00B7, 0x00B8,
        0x00B9, 0x00BA, 0x00BB, 0x00BC, 0x00BD, 0x00BE, 0x00BF,
        0x00C6, 0x00D0, 0x00D7, 0x00D8,
        0x00DE, 0x00DF, 0x00E0, 0x00E1, 0x00E6, 0x00E8, 0x00E9, 0x00EA,
        0x00EC, 0x00ED, 0x00F0, 0x00F2, 0x00F3, 0x00F7, 0x00F8, 0x00F9,
        0x00FA, 0x00FC, 0x00FE -> true
        // Greek (一部)
        in 0x0391..0x03A9 -> true
        in 0x03B1..0x03C9 -> true
        // General punctuation
        in 0x2010..0x2027 -> true
        in 0x2030..0x205E -> true
        // 上付き / 下付き / Roman numerals / arrows / math / etc.
        in 0x2070..0x209F -> true
        in 0x20A0..0x20CF -> true
        in 0x2100..0x214F -> true
        in 0x2150..0x218F -> true  // Roman numerals 等
        in 0x2190..0x21FF -> true  // 矢印
        in 0x2200..0x22FF -> true  // 数学記号
        in 0x2300..0x23FF -> true  // 雑多な技術記号
        in 0x2460..0x24FF -> true  // 囲み英数字
        in 0x2500..0x257F -> true  // 罫線素片 (Box Drawing)
        in 0x2580..0x259F -> true  // ブロック要素
        in 0x25A0..0x25FF -> true  // 図形
        in 0x2600..0x26FF -> true  // その他記号
        in 0x2700..0x27BF -> true  // 装飾記号
        in 0xE000..0xF8FF -> true  // 私用領域
        in 0xFE00..0xFE0F -> true  // Variation Selectors
        0xFFFD -> true
        else -> false
    }
}
