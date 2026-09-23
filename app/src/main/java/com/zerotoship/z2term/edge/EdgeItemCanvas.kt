package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

/** Free placement within a fixed canvas; items without coordinates retain a vertical order. */
@SuppressLint("ViewConstructor") // Built in code only, never inflated from XML.
internal class EdgeItemCanvas(context: Context, private val canvasHeight: Int) : ViewGroup(context) {
    private val items = mutableListOf<EdgeStore.Item>()
    private val positions = mutableListOf<Pair<Int, Int>>()
    fun cell(item: EdgeStore.Item): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        items.add(item)
        this@EdgeItemCanvas.addView(this, LayoutParams(-1, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        var bottom = 0
        positions.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val item = items[i]
            val childWidth = item.fields["width"]?.takeIf { it.isNotBlank() }
                ?.let { EdgeStore.dimensionPixels(it, width, resources.displayMetrics.density) } ?: width
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val point = EdgeItemLayout.point(item.fields["at"].orEmpty())
            val x = point?.let { EdgeItemLayout.offset(it.first, width, child.measuredWidth) } ?: 0
            val y = point?.let { EdgeItemLayout.offset(it.second, canvasHeight, child.measuredHeight) } ?: bottom
            positions.add(x to y)
            bottom = maxOf(bottom, y + child.measuredHeight)
        }
        setMeasuredDimension(width, maxOf(canvasHeight, bottom))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val (x, y) = positions[i]
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }
}
