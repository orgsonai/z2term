package com.zerotoship.z2term.emulator

import androidx.compose.ui.graphics.Color

/**
 * ターミナル配色テーブル。ANSI 16色 + 256色拡張 + RGB 24bit対応。
 *
 * 既定は ZTS Theme。`setColor()` で実行時切替可能。
 */
class TerminalColors {

    /** 256色配列 */
    private val colors = IntArray(NUM_INDEXED_COLORS)

    /** 前景色 (SGR 39 でリセットされる規定値) */
    var defaultForeground: Int = ZtsTheme.foreground
        private set

    /** 背景色 (SGR 49 でリセットされる規定値) */
    var defaultBackground: Int = ZtsTheme.background
        private set

    /** カーソル色 */
    var cursorColor: Int = ZtsTheme.cursor
        private set

    init {
        applyTheme(ZtsTheme)
    }

    /** テーマ適用 */
    fun applyTheme(theme: TerminalTheme) {
        defaultForeground = theme.foreground
        defaultBackground = theme.background
        cursorColor = theme.cursor

        // ANSI 16 色
        colors[0] = theme.black
        colors[1] = theme.red
        colors[2] = theme.green
        colors[3] = theme.yellow
        colors[4] = theme.blue
        colors[5] = theme.magenta
        colors[6] = theme.cyan
        colors[7] = theme.white
        colors[8] = theme.brightBlack
        colors[9] = theme.brightRed
        colors[10] = theme.brightGreen
        colors[11] = theme.brightYellow
        colors[12] = theme.brightBlue
        colors[13] = theme.brightMagenta
        colors[14] = theme.brightCyan
        colors[15] = theme.brightWhite

        // 6×6×6 = 216 色キューブ (16〜231)
        for (red in 0..5) {
            for (green in 0..5) {
                for (blue in 0..5) {
                    val index = 16 + red * 36 + green * 6 + blue
                    colors[index] = rgb(
                        if (red == 0) 0 else 55 + red * 40,
                        if (green == 0) 0 else 55 + green * 40,
                        if (blue == 0) 0 else 55 + blue * 40
                    )
                }
            }
        }

        // グレースケール 24 段階 (232〜255)
        for (i in 0..23) {
            val gray = 8 + i * 10
            colors[232 + i] = rgb(gray, gray, gray)
        }
    }

    /** インデックス色取得 (0〜255) */
    fun getColor(index: Int): Int {
        if (index in 0 until NUM_INDEXED_COLORS) return colors[index]
        return defaultForeground
    }

    /** 色設定 (OSC 4 等で外部から変更) */
    fun setColor(index: Int, color: Int) {
        if (index in 0 until NUM_INDEXED_COLORS) colors[index] = color
    }

    /** 既定前景色を上書き (OSC 10) */
    fun setDefaultForeground(color: Int) { defaultForeground = color }

    /** 既定背景色を上書き (OSC 11) */
    fun setDefaultBackground(color: Int) { defaultBackground = color }

    /** カーソル色を上書き (OSC 12) */
    fun setCursorColor(color: Int) { cursorColor = color }

    fun toComposeColor(argb: Int): Color = Color(argb.toLong() or 0xFF000000)

    companion object {
        const val NUM_INDEXED_COLORS = 256

        /** ARGB int を生成 */
        fun rgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }
}

/**
 * ターミナルテーマ定義。
 * すべて ARGB int。
 */
data class TerminalTheme(
    val name: String,
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val black: Int,
    val red: Int,
    val green: Int,
    val yellow: Int,
    val blue: Int,
    val magenta: Int,
    val cyan: Int,
    val white: Int,
    val brightBlack: Int,
    val brightRed: Int,
    val brightGreen: Int,
    val brightYellow: Int,
    val brightBlue: Int,
    val brightMagenta: Int,
    val brightCyan: Int,
    val brightWhite: Int
)

/** Z2Term デフォルトテーマ */
val ZtsTheme = TerminalTheme(
    name = "ZTS Theme",
    foreground = 0xFFFAFAFA.toInt(),
    background = 0xFF0A0A0A.toInt(),
    cursor = 0xFF22C55E.toInt(),
    black = 0xFF1F1F1F.toInt(),
    red = 0xFFEF4444.toInt(),
    green = 0xFF22C55E.toInt(),
    yellow = 0xFFF59E0B.toInt(),
    blue = 0xFF3B82F6.toInt(),
    magenta = 0xFFA855F7.toInt(),
    cyan = 0xFF06B6D4.toInt(),
    white = 0xFFFAFAFA.toInt(),
    brightBlack = 0xFF525252.toInt(),
    brightRed = 0xFFF87171.toInt(),
    brightGreen = 0xFF4ADE80.toInt(),
    brightYellow = 0xFFFBBF24.toInt(),
    brightBlue = 0xFF60A5FA.toInt(),
    brightMagenta = 0xFFC084FC.toInt(),
    brightCyan = 0xFF22D3EE.toInt(),
    brightWhite = 0xFFFFFFFF.toInt()
)

/** Solarized Dark */
val SolarizedDarkTheme = TerminalTheme(
    name = "Solarized Dark",
    foreground = 0xFF93A1A1.toInt(),
    background = 0xFF002B36.toInt(),
    cursor = 0xFF93A1A1.toInt(),
    black = 0xFF073642.toInt(),
    red = 0xFFDC322F.toInt(),
    green = 0xFF859900.toInt(),
    yellow = 0xFFB58900.toInt(),
    blue = 0xFF268BD2.toInt(),
    magenta = 0xFFD33682.toInt(),
    cyan = 0xFF2AA198.toInt(),
    white = 0xFFEEE8D5.toInt(),
    brightBlack = 0xFF002B36.toInt(),
    brightRed = 0xFFCB4B16.toInt(),
    brightGreen = 0xFF586E75.toInt(),
    brightYellow = 0xFF657B83.toInt(),
    brightBlue = 0xFF839496.toInt(),
    brightMagenta = 0xFF6C71C4.toInt(),
    brightCyan = 0xFF93A1A1.toInt(),
    brightWhite = 0xFFFDF6E3.toInt()
)

/** Dracula */
val DraculaTheme = TerminalTheme(
    name = "Dracula",
    foreground = 0xFFF8F8F2.toInt(),
    background = 0xFF282A36.toInt(),
    cursor = 0xFFF8F8F2.toInt(),
    black = 0xFF000000.toInt(),
    red = 0xFFFF5555.toInt(),
    green = 0xFF50FA7B.toInt(),
    yellow = 0xFFF1FA8C.toInt(),
    blue = 0xFFBD93F9.toInt(),
    magenta = 0xFFFF79C6.toInt(),
    cyan = 0xFF8BE9FD.toInt(),
    white = 0xFFBFBFBF.toInt(),
    brightBlack = 0xFF4D4D4D.toInt(),
    brightRed = 0xFFFF6E67.toInt(),
    brightGreen = 0xFF5AF78E.toInt(),
    brightYellow = 0xFFF4F99D.toInt(),
    brightBlue = 0xFFCAA9FA.toInt(),
    brightMagenta = 0xFFFF92D0.toInt(),
    brightCyan = 0xFF9AEDFE.toInt(),
    brightWhite = 0xFFE6E6E6.toInt()
)

/** Gruvbox Dark */
val GruvboxDarkTheme = TerminalTheme(
    name = "Gruvbox Dark",
    foreground = 0xFFEBDBB2.toInt(),
    background = 0xFF282828.toInt(),
    cursor = 0xFFEBDBB2.toInt(),
    black = 0xFF282828.toInt(),
    red = 0xFFCC241D.toInt(),
    green = 0xFF98971A.toInt(),
    yellow = 0xFFD79921.toInt(),
    blue = 0xFF458588.toInt(),
    magenta = 0xFFB16286.toInt(),
    cyan = 0xFF689D6A.toInt(),
    white = 0xFFA89984.toInt(),
    brightBlack = 0xFF928374.toInt(),
    brightRed = 0xFFFB4934.toInt(),
    brightGreen = 0xFFB8BB26.toInt(),
    brightYellow = 0xFFFABD2F.toInt(),
    brightBlue = 0xFF83A598.toInt(),
    brightMagenta = 0xFFD3869B.toInt(),
    brightCyan = 0xFF8EC07C.toInt(),
    brightWhite = 0xFFEBDBB2.toInt()
)

/** Nord */
val NordTheme = TerminalTheme(
    name = "Nord",
    foreground = 0xFFD8DEE9.toInt(),
    background = 0xFF2E3440.toInt(),
    cursor = 0xFFD8DEE9.toInt(),
    black = 0xFF3B4252.toInt(),
    red = 0xFFBF616A.toInt(),
    green = 0xFFA3BE8C.toInt(),
    yellow = 0xFFEBCB8B.toInt(),
    blue = 0xFF81A1C1.toInt(),
    magenta = 0xFFB48EAD.toInt(),
    cyan = 0xFF88C0D0.toInt(),
    white = 0xFFE5E9F0.toInt(),
    brightBlack = 0xFF4C566A.toInt(),
    brightRed = 0xFFBF616A.toInt(),
    brightGreen = 0xFFA3BE8C.toInt(),
    brightYellow = 0xFFEBCB8B.toInt(),
    brightBlue = 0xFF81A1C1.toInt(),
    brightMagenta = 0xFFB48EAD.toInt(),
    brightCyan = 0xFF8FBCBB.toInt(),
    brightWhite = 0xFFECEFF4.toInt()
)

/** Tokyo Night */
val TokyoNightTheme = TerminalTheme(
    name = "Tokyo Night",
    foreground = 0xFFC0CAF5.toInt(),
    background = 0xFF1A1B26.toInt(),
    cursor = 0xFFC0CAF5.toInt(),
    black = 0xFF15161E.toInt(),
    red = 0xFFF7768E.toInt(),
    green = 0xFF9ECE6A.toInt(),
    yellow = 0xFFE0AF68.toInt(),
    blue = 0xFF7AA2F7.toInt(),
    magenta = 0xFFBB9AF7.toInt(),
    cyan = 0xFF7DCFFF.toInt(),
    white = 0xFFA9B1D6.toInt(),
    brightBlack = 0xFF414868.toInt(),
    brightRed = 0xFFF7768E.toInt(),
    brightGreen = 0xFF9ECE6A.toInt(),
    brightYellow = 0xFFE0AF68.toInt(),
    brightBlue = 0xFF7AA2F7.toInt(),
    brightMagenta = 0xFFBB9AF7.toInt(),
    brightCyan = 0xFF7DCFFF.toInt(),
    brightWhite = 0xFFC0CAF5.toInt()
)

/** 同梱テーマ一覧 */
val AvailableThemes = listOf(
    ZtsTheme,
    SolarizedDarkTheme,
    DraculaTheme,
    GruvboxDarkTheme,
    NordTheme,
    TokyoNightTheme
)
