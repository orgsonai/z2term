package com.zerotoship.z2term.edge

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared controls for overlays, which have no Activity theme. */
internal object EdgeEditorUi {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun dark(context: Context): Boolean = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    fun foreground(context: Context): Int = if (dark(context)) Color.rgb(234, 238, 242) else Color.rgb(27, 34, 42)
    fun muted(context: Context): Int = if (dark(context)) Color.rgb(164, 177, 188) else Color.rgb(87, 102, 115)
    fun surface(context: Context): Int = if (dark(context)) Color.rgb(26, 31, 37) else Color.rgb(248, 250, 252)
    fun line(context: Context): Int = if (dark(context)) Color.rgb(67, 79, 91) else Color.rgb(200, 210, 218)
    fun accent(context: Context): Int = if (dark(context)) Color.rgb(149, 198, 233) else Color.rgb(28, 87, 130)
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
