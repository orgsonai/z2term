package com.zerotoship.z2term.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.zerotoship.z2term.emulator.TerminalTheme

/**
 * アプリ UI 全体の動的カラーパレット。
 *
 * 選択中のターミナルテーマ ([TerminalTheme]) の前景/背景/ANSI 色から導出し、
 * TopBar・タブ・各シート・キーボードなどアプリ全域の配色に反映する。
 *
 * `Color.kt` の `Zts*` はこの object のプロパティを読む getter になっているため、
 * [applyFrom] でここを更新すると `Zts*` を参照する全 Composable が再コンポーズされる
 * (各プロパティは Compose の snapshot state)。
 *
 * 初期値は従来の ZTS ダークパレットと一致させてあるので、テーマ適用前でも
 * これまでと同じ見た目になる。
 */
object AppColors {
    var bgPrimary by mutableStateOf(Color(0xFF0A0A0A)); private set
    var bgSecondary by mutableStateOf(Color(0xFF171717)); private set
    var bgCard by mutableStateOf(Color(0xFF1F1F1F)); private set
    var border by mutableStateOf(Color(0xFF2A2A2A)); private set

    var textPrimary by mutableStateOf(Color(0xFFFAFAFA)); private set
    var textSecondary by mutableStateOf(Color(0xFFA3A3A3)); private set
    var textTertiary by mutableStateOf(Color(0xFF737373)); private set

    var accent by mutableStateOf(Color(0xFF22C55E)); private set
    var accentBright by mutableStateOf(Color(0xFF4ADE80)); private set
    var accentDim by mutableStateOf(Color(0xFF16A34A)); private set

    var error by mutableStateOf(Color(0xFFEF4444)); private set
    var warning by mutableStateOf(Color(0xFFF59E0B)); private set
    var info by mutableStateOf(Color(0xFF3B82F6)); private set

    /** 背景が明るいテーマ (ライトモード相当)。ステータスバーのアイコン色切替に使う。 */
    var isLight by mutableStateOf(false); private set

    /** 現在適用中テーマ名 (重複適用の抑制用) */
    private var currentThemeName: String? = null

    /**
     * ターミナルテーマからアプリ全体パレットを導出して適用する。
     *
     * - 背景/前景はテーマそのまま。
     * - カード/枠線は背景を前景方向へ少しずつ寄せて生成 (ライト/ダーク両対応)。
     * - 副次/三次テキストは前景を背景方向へ寄せて減衰。
     * - アクセントはテーマの green 系 (Z2Term のブランドがグリーンのため)。
     */
    fun applyFrom(theme: TerminalTheme) {
        if (theme.name == currentThemeName) return
        currentThemeName = theme.name

        val bg = Color(theme.background)
        val fg = Color(theme.foreground)
        isLight = bg.luminance() > 0.5f

        bgPrimary = bg
        bgSecondary = lerp(bg, fg, 0.05f)
        bgCard = lerp(bg, fg, 0.10f)
        border = lerp(bg, fg, 0.22f)

        textPrimary = fg
        textSecondary = lerp(fg, bg, 0.35f)
        textTertiary = lerp(fg, bg, 0.55f)

        accent = Color(theme.green)
        accentBright = Color(theme.brightGreen)
        accentDim = lerp(Color(theme.green), bg, 0.35f)

        error = Color(theme.red)
        warning = Color(theme.yellow)
        info = Color(theme.blue)
    }
}
