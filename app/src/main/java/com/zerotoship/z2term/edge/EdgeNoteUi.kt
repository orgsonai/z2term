package com.zerotoship.z2term.edge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/** Note controls use the panel palette and the text layout's actual line positions. */
internal object EdgeNoteUi {
    fun preview(context: Context, ruled: Boolean): TextView = object : TextView(context) {
        private val rules = Rules(this, ruled)
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
    }.apply { style(this) }

    fun editor(context: Context, ruled: Boolean): EditText = object : EditText(context) {
        private val rules = Rules(this, ruled)
        override fun onDraw(canvas: Canvas) {
            rules.draw(canvas)
            super.onDraw(canvas)
        }
    }.apply {
        background = null
        style(this)
    }

    private fun style(view: TextView) = with(view) {
        gravity = Gravity.TOP or Gravity.START
        setTextColor(EdgeEditorUi.foreground(context))
        setHintTextColor(EdgeEditorUi.muted(context))
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

    private class Rules(private val view: TextView, private val enabled: Boolean) {
        private val paint = Paint().apply {
            color = EdgeEditorUi.line(view.context)
            strokeWidth = view.resources.displayMetrics.density
        }
        fun draw(canvas: Canvas) {
            if (!enabled) return
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
