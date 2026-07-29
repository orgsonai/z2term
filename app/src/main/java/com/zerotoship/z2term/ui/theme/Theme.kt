package com.zerotoship.z2term.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 現在の [AppColors] パレットから Material3 ColorScheme を生成する。
 * AppColors は snapshot state なので、テーマ変更時にこの関数を読む Composable は
 * 再コンポーズされ、配色が追従する。ライト背景テーマなら lightColorScheme を使う。
 */
@Composable
private fun currentColorScheme() = if (AppColors.isLight) {
    lightColorScheme(
        primary = ZtsGreen,
        onPrimary = ZtsBgPrimary,
        primaryContainer = ZtsGreenDim,
        onPrimaryContainer = ZtsTextPrimary,
        secondary = ZtsGreenBright,
        onSecondary = ZtsBgPrimary,
        background = ZtsBgPrimary,
        onBackground = ZtsTextPrimary,
        surface = ZtsBgSecondary,
        onSurface = ZtsTextPrimary,
        surfaceVariant = ZtsBgCard,
        onSurfaceVariant = ZtsTextSecondary,
        outline = ZtsBorder,
        outlineVariant = ZtsBorder,
        error = ZtsError,
        onError = ZtsBgPrimary
    )
} else {
    darkColorScheme(
        primary = ZtsGreen,
        onPrimary = ZtsBgPrimary,
        primaryContainer = ZtsGreenDim,
        onPrimaryContainer = ZtsTextPrimary,
        secondary = ZtsGreenBright,
        onSecondary = ZtsBgPrimary,
        background = ZtsBgPrimary,
        onBackground = ZtsTextPrimary,
        surface = ZtsBgSecondary,
        onSurface = ZtsTextPrimary,
        surfaceVariant = ZtsBgCard,
        onSurfaceVariant = ZtsTextSecondary,
        outline = ZtsBorder,
        outlineVariant = ZtsBorder,
        error = ZtsError,
        onError = ZtsTextPrimary
    )
}

@Composable
fun Z2TermTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = currentColorScheme()

    val view = LocalView.current
    // ⚠ ステータスバーの色を触れるのは Activity の中だけ。入力メソッド (Z2ImeService) から
    // このテーマを使うと context は Service なので、決め打ちのキャストだと
    // ClassCastException でキーボードが出た瞬間に落ちる。窓が無いときは色合わせを飛ばす。
    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null) {
        val isLight = AppColors.isLight
        val barColor = colorScheme.background.toArgb()
        SideEffect {
            val window = activity.window
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            val insets = WindowCompat.getInsetsController(window, view)
            // ライト背景なら濃いアイコン、ダーク背景なら明るいアイコン
            insets.isAppearanceLightStatusBars = isLight
            insets.isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Z2TermTypography,
        content = content
    )
}
