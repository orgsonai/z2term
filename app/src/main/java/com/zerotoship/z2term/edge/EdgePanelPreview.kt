package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.zerotoship.z2term.R

/** A scaled display diagram shows the requested size/limit and placement without resizing the editor. */
@SuppressLint("ViewConstructor") // Built in code only, never inflated from XML.
internal class EdgePanelPreview(context: Context, private var fields: Map<String, String>,
    private val screenWidth: Int, private val screenHeight: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { contentDescription = context.getString(R.string.edge_size_preview) }
    fun update(values: Map<String, String>) { fields = values; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = 12 * density
        val caption = 24 * density
        val scale = minOf((width - 2 * padding) / screenWidth, (height - 2 * padding - caption) / screenHeight).coerceAtLeast(0f)
        val w = screenWidth * scale
        val h = screenHeight * scale
        val left = (width - w) / 2f
        val top = padding
        paint.color = EdgeSettingsUi.strong(context); paint.style = Paint.Style.STROKE; paint.strokeWidth = density
        canvas.drawRect(left, top, left + w, top + h, paint)
        val panelW = EdgeStore.dimensionPixels(fields["width"] ?: "360", screenWidth, density) * scale
        val panelH = EdgeStore.dimensionPixels(fields["height"] ?: "72%", screenHeight, density) * scale
        val (x, y) = EdgePanelPosition.fractions(fields)
        val px = left + (w - panelW) * x
        val py = top + (h - panelH) * y
        paint.style = Paint.Style.FILL; paint.color = EdgeSettingsUi.tint(EdgeSettingsUi.accent(context), 0x2E)
        canvas.drawRect(px, py, px + panelW, py + panelH, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density * 1.5f; paint.color = EdgeSettingsUi.accent(context)
        canvas.drawRect(px, py, px + panelW, py + panelH, paint)
        paint.style = Paint.Style.FILL; paint.color = EdgeSettingsUi.muted(context); paint.textSize = 11 * resources.displayMetrics.scaledDensity
        val label = context.getString(if (fields["fit"] == "fixed") R.string.edge_size_preview_fixed else R.string.edge_size_preview_limit)
        canvas.drawText(label, padding, height - padding, paint)
    }
}
