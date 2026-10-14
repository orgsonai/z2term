package com.zerotoship.z2term.edge

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.settings.CustomThemeStore

/**
 * The colours of one open panel when it does not follow the app palette (panel field `scheme`).
 *
 * A scheme is the name of a terminal theme, so a panel offers the same choices as the terminal and
 * needs no second set of colour definitions. The palette travels with the view context
 * ([EdgePaletteContext]); controls ask for their colours through [EdgeSettingsUi] / [EdgeEditorUi]
 * with the context they were built from, so nothing global changes while a panel is open.
 */
internal data class EdgePalette(
    val canvas: Int, val raised: Int, val surface: Int, val line: Int,
    val foreground: Int, val muted: Int, val strong: Int, val accent: Int, val danger: Int,
) {
    val light: Boolean get() = ColorUtils.calculateLuminance(canvas) > 0.5

    companion object {
        /** Same derivation as the app palette (`AppColors.applyFrom`): surfaces lifted off the ground, text faded into it. */
        fun from(theme: TerminalTheme): EdgePalette {
            val bg = theme.background
            val fg = theme.foreground
            return EdgePalette(
                canvas = bg, raised = ColorUtils.blendARGB(bg, fg, 0.05f),
                surface = ColorUtils.blendARGB(bg, fg, 0.10f), line = ColorUtils.blendARGB(bg, fg, 0.22f),
                foreground = fg, muted = ColorUtils.blendARGB(fg, bg, 0.35f),
                strong = ColorUtils.blendARGB(fg, bg, 0.55f), accent = theme.green, danger = theme.red,
            )
        }

        /** Scheme names a panel can choose now: the built-in themes and the user's own theme, if any. */
        fun schemes(): List<String> =
            (AvailableThemes.map { it.name } + listOfNotNull(CustomThemeStore.theme.value?.name)).distinct()

        /** Null when the panel follows the app palette, or names a theme that no longer exists. */
        fun named(scheme: String?): EdgePalette? {
            if (scheme.isNullOrEmpty()) return null
            val theme = AvailableThemes.firstOrNull { it.name == scheme }
                ?: CustomThemeStore.theme.value?.takeIf { it.name == scheme }
            return theme?.let(::from)
        }

        /** The palette carried by [context] or by any context it wraps (dialogs wrap the one they are given). */
        fun of(context: Context): EdgePalette? {
            var current: Context? = context
            while (current != null) {
                if (current is EdgePaletteContext) return current.palette
                current = (current as? ContextWrapper)?.baseContext
            }
            return null
        }
    }
}

/** View context of a panel window. Stock widgets take a light or dark base to match the palette. */
internal class EdgePaletteContext(base: Context, val palette: EdgePalette?) : ContextThemeWrapper(
    base,
    when {
        palette == null -> android.R.style.Theme_DeviceDefault_DayNight
        palette.light -> android.R.style.Theme_DeviceDefault_Light
        else -> android.R.style.Theme_DeviceDefault
    },
)
