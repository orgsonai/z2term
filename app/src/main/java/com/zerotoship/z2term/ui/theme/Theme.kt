package com.zerotoship.z2term.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZtsDarkColorScheme = darkColorScheme(
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

@Composable
fun Z2TermTheme(
    darkTheme: Boolean = true,  // M1 段階ではダーク固定
    content: @Composable () -> Unit
) {
    val colorScheme = ZtsDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Z2TermTypography,
        content = content
    )
}
