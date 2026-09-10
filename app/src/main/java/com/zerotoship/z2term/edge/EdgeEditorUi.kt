package com.zerotoship.z2term.edge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.zerotoship.z2term.ui.theme.AppColors

/** Shared controls for overlays, which have no Activity theme. */
internal object EdgeEditorUi {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    // Same source as the rest of the app (see EdgeSettingsUi): the panel that floats over other
    // apps has to read as this app's own surface, so it follows the selected terminal theme.
    fun foreground(context: Context): Int = AppColors.textPrimary.toArgb()
    fun muted(context: Context): Int = AppColors.textSecondary.toArgb()
    fun surface(context: Context): Int = AppColors.bgPrimary.toArgb()
    fun line(context: Context): Int = AppColors.border.toArgb()
    fun accent(context: Context): Int = AppColors.accent.toArgb()
    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(line(context)); layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1))
    }
    fun label(context: Context, value: String, secondary: Boolean = false): TextView = TextView(context).apply {
        text = value; textSize = if (secondary) 13f else 16f
        setTextColor(if (secondary) muted(context) else foreground(context))
        setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
    }
    fun button(context: Context, label: String, selected: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        text = label; textSize = 14f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
        setTextColor(if (selected) accent(context) else foreground(context))
        setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        val fill = GradientDrawable().apply {
            setColor(if (selected) (accent(context) and 0x00ffffff) or 0x18000000 else Color.TRANSPARENT)
            cornerRadius = dp(context, 4).toFloat()
        }
        background = RippleDrawable(ColorStateList.valueOf((accent(context) and 0x00ffffff) or 0x30000000), fill, null)
        isSelected = selected; setOnClickListener { action() }
    }
}
