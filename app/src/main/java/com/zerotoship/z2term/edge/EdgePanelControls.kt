package com.zerotoship.z2term.edge

import android.content.Context
import android.widget.LinearLayout
import com.zerotoship.z2term.R

internal object EdgePanelControls {
    fun close(context: Context, action: () -> Unit) = EdgeEditorUi.button(context, "×", action = action).apply {
        contentDescription = context.getString(R.string.edge_close)
        tooltipText = contentDescription
        textSize = 24f
        minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 48), EdgeEditorUi.dp(context, 48))
    }
}
