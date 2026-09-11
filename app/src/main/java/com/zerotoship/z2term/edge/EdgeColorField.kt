package com.zerotoship.z2term.edge

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.zerotoship.z2term.R

/** Presets and an editable RGB value share the existing item draft and its Save/Cancel handling. */
internal object EdgeColorField {
    fun add(context: Context, parent: LinearLayout, entry: EditText, label: Int) {
        entry.setSingleLine(true)
        entry.hint = "#RRGGBB"
        val colors = listOf("#FFFFFF", "#000000", "#FFF4BD", "#DDF1FF", "#E4F1DC", "#FFE1E1", "#333333", "#1F3B5B")
        val row = LinearLayout(context)
        val buttons = colors.map { code ->
            val color = EdgeNoteColor.parse(code)!!
            val ink = if (ColorUtils.calculateLuminance(color) > 0.179) Color.BLACK else Color.WHITE
            Button(context).apply {
                setPadding(0, 0, 0, 0)
                minWidth = 0; minimumWidth = 0
                minHeight = 0; minimumHeight = 0
                setTextColor(ink)
                background = GradientDrawable().apply {
                    setColor(color)
                    setStroke(EdgeEditorUi.dp(context, 1).coerceAtLeast(1), EdgeSettingsUi.strong(context))
                }
                contentDescription = context.getString(label) + " " + code
                tooltipText = code
                setOnClickListener { entry.setText(code) }
                row.addView(this, LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 48), EdgeEditorUi.dp(context, 48)).apply {
                    marginEnd = EdgeEditorUi.dp(context, 6)
                })
            }
        }
        parent.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_option_auto)) {
            entry.setText("")
        })
        parent.addView(EdgeSettingsUi.body(context, context.getString(R.string.edge_note_colors_help)))
        fun update() {
            val raw = entry.text.toString().trim()
            entry.error = if (raw.isNotEmpty() && EdgeNoteColor.parse(raw) == null)
                context.getString(R.string.edge_color_invalid) else null
            buttons.forEachIndexed { index, button ->
                button.isSelected = colors[index].equals(raw, ignoreCase = true)
                button.text = if (button.isSelected) "\u2713" else ""
            }
        }
        entry.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        update()
    }
}
