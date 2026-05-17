package com.zerotoship.z2term.emulator

/**
 * East Asian Width 判定 (UTR #11 の W/F カテゴリ近似)。
 *
 * 完全な Unicode データベース照合ではなく、実用上カバー率の高い範囲のみを
 * 列挙する。CJK Unified Ideographs、Hangul、Hiragana/Katakana、Fullwidth Forms、
 * 主要絵文字、CJK 拡張面までを 2 セル幅として扱う。
 *
 * 制御文字 (0x00–0x1F, 0x7F)、ASCII、Latin/Cyrillic などは 1 セル。
 */
object EastAsianWidth {

    fun isWide(codepoint: Int): Boolean {
        if (codepoint < 0x1100) return false
        return when (codepoint) {
            // Hangul Jamo
            in 0x1100..0x115F -> true
            // CJK Radicals Supplement, Kangxi Radicals, IDC, Kanbun
            in 0x2E80..0x303E -> true
            // Hiragana, Katakana, Bopomofo, Hangul Compat, Kanbun, Yijing
            in 0x3041..0x33FF -> true
            // CJK Unified Ideographs Extension A
            in 0x3400..0x4DBF -> true
            // CJK Unified Ideographs
            in 0x4E00..0x9FFF -> true
            // Yi Syllables, Yi Radicals
            in 0xA000..0xA4CF -> true
            // Hangul Syllables
            in 0xAC00..0xD7A3 -> true
            // CJK Compatibility Ideographs
            in 0xF900..0xFAFF -> true
            // Vertical Forms, CJK Compatibility Forms, Small Form Variants
            in 0xFE30..0xFE6F -> true
            // Fullwidth Forms (除外: 0xFF61–0xFF9F 半角カタカナ)
            in 0xFF00..0xFF60 -> true
            in 0xFFE0..0xFFE6 -> true
            // Miscellaneous Symbols And Pictographs / Emoticons / Transport / etc.
            in 0x1F300..0x1F64F -> true
            in 0x1F680..0x1F6FF -> true
            // Supplemental Symbols and Pictographs
            in 0x1F900..0x1F9FF -> true
            // CJK Unified Ideographs Extension B-F
            in 0x20000..0x2FFFD -> true
            // CJK Unified Ideographs Extension G+
            in 0x30000..0x3FFFD -> true
            else -> false
        }
    }
}
