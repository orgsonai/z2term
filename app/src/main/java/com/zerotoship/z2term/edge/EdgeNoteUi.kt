package com.zerotoship.z2term.edge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Per-note paper and ink, retaining theme defaults and actual line positions. */
internal object EdgeNoteUi {
    fun preview(context: Context, ruled: Boolean, ink: Int? = null, paper: Int? = null): TextView = object : TextView(context) {
        private val rules = Rules(this, ruled, ink != null || paper != null)
        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onDoubleTap(event: MotionEvent): Boolean = performClick()
        }).apply { setIsLongpressEnabled(false) }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            taps.onTouchEvent(event)
            // Do not send single taps to TextView's click handler. Parent scrolling can still cancel.
            return true
        }

        // Keep the normal click action for accessibility services and keyboard activation.
        override fun performClick(): Boolean = super.performClick()

        override fun onDraw(canvas: Canvas) {
            rules.draw(canvas)
            super.onDraw(canvas)
        }
    }.apply { style(this, ink, paper) }

    fun editor(context: Context, ruled: Boolean, ink: Int? = null, paper: Int? = null): EditText = object : EditText(context) {
        private val rules = Rules(this, ruled, ink != null || paper != null)
        override fun onDraw(canvas: Canvas) {
            rules.draw(canvas)
            super.onDraw(canvas)
        }
    }.apply {
        background = null
        style(this, ink, paper)
    }

    private fun style(view: TextView, ink: Int?, paper: Int?) = with(view) {
        gravity = Gravity.TOP or Gravity.START
        val custom = ink != null || paper != null
        val foreground = ink ?: if (paper != null) {
            if (ColorUtils.calculateLuminance(paper) > 0.179) Color.BLACK else Color.WHITE
        } else EdgeEditorUi.foreground(context)
        setTextColor(foreground)
        setHintTextColor(if (custom) ColorUtils.setAlphaComponent(foreground, 160) else EdgeEditorUi.muted(context))
        paper?.let { setBackgroundColor(it) }
        if (custom) {
            highlightColor = ColorUtils.setAlphaComponent(foreground, 64)
            if (this is EditText) {
                textCursorDrawable?.mutate()?.let { it.setTint(foreground); textCursorDrawable = it }
                textSelectHandle?.mutate()?.let { it.setTint(foreground); setTextSelectHandle(it) }
                textSelectHandleLeft?.mutate()?.let { it.setTint(foreground); setTextSelectHandleLeft(it) }
                textSelectHandleRight?.mutate()?.let { it.setTint(foreground); setTextSelectHandleRight(it) }
            }
        }
        setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 8),
            EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 8))
    }

    fun button(context: Context, icon: Int, description: Int): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        contentDescription = context.getString(description)
        tooltipText = contentDescription
        imageTintList = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(EdgeEditorUi.muted(context), EdgeEditorUi.foreground(context)))
        background = EdgeSettingsUi.ripple(context, null)
        setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12),
            EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12))
        layoutParams = LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 48), EdgeEditorUi.dp(context, 48))
    }

    private class Rules(private val view: TextView, private val enabled: Boolean, private val custom: Boolean) {
        private val paint = Paint().apply {
            color = EdgeEditorUi.line(view.context)
            strokeWidth = view.resources.displayMetrics.density
        }
        fun draw(canvas: Canvas) {
            if (!enabled) return
            if (custom) paint.color = ColorUtils.setAlphaComponent(view.currentTextColor, 64)
            val layout = view.layout ?: return
            val top = view.extendedPaddingTop.toFloat()
            val first = layout.getLineForVertical((view.scrollY - top).toInt().coerceAtLeast(0))
            val bottom = view.scrollY + view.height - view.extendedPaddingBottom
            for (line in first until layout.lineCount) {
                val y = top + layout.getLineBottom(line) - paint.strokeWidth / 2
                if (y > bottom) break
                canvas.drawLine(view.paddingLeft.toFloat(), y, (view.width - view.paddingRight).toFloat(), y, paint)
            }
            var y = top + layout.height + view.lineHeight - paint.strokeWidth / 2
            while (y <= bottom) {
                canvas.drawLine(view.paddingLeft.toFloat(), y, (view.width - view.paddingRight).toFloat(), y, paint)
                y += view.lineHeight.coerceAtLeast(1)
            }
        }
    }
}
