package com.zerotoship.z2term.edge

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView

/** Explicit height is a scrollable viewport, so shrinking an item never hides its controls. */
internal class EdgeItemFrame(context: Context, private val item: EdgeStore.Item,
    private val panelHeight: Int, content: View) : FrameLayout(context) {
    init {
        val child = if (item.fields["height"].isNullOrBlank()) content else ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        addView(child, LayoutParams(-1, -1))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val density = resources.displayMetrics.density
        val width = item.fields["width"]?.takeIf { it.isNotBlank() }
            ?.let { EdgeStore.dimensionPixels(it, available, density) } ?: available
        val height = item.fields["height"]?.takeIf { it.isNotBlank() }
            ?.let { EdgeStore.dimensionPixels(it, panelHeight, density) }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            height?.let { MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY) } ?: heightMeasureSpec)
    }
    companion object {
        fun gravity(item: EdgeStore.Item): Int = when (item.fields["align"]) {
            "center" -> Gravity.CENTER_HORIZONTAL
            "end" -> Gravity.END
            else -> Gravity.START
        }
    }
}
