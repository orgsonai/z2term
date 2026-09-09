package com.zerotoship.z2term.edge

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Keep every enabled tool visible; narrow panels stack controls instead of clipping siblings. */
internal class EdgeToolRow(context: Context, private val compact: Boolean = false) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val visibleCount = (0 until childCount).count { getChildAt(it).visibility != View.GONE }
        if (compact && visibleCount > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val width = (available / visibleCount).coerceIn(
                EdgeEditorUi.dp(context, 24), EdgeEditorUi.dp(context, 32))
            for (i in 0 until childCount) getChildAt(i).layoutParams.width = width
        }
        var required = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val lp = child.layoutParams as? LayoutParams
            required += (lp?.width?.takeIf { it >= 0 } ?: child.measuredWidth) +
                (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
        }
        orientation = if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && required > available)
            VERTICAL else HORIZONTAL
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
