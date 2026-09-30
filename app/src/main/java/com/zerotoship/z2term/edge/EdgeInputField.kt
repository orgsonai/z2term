package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import kotlin.math.abs

/** Waits for a tap before focusing an input in a panel that supports tab swipes. */
@SuppressLint("ViewConstructor") // Created in code only.
internal open class EdgeInputField(context: Context) : EditText(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var pendingDown: MotionEvent? = null
    private var tapCandidate = false

    private fun tabsCanSwipe(): Boolean {
        val area = (rootView as? EdgePanelWindow)?.swipeArea ?: return false
        var view: View? = this
        while (view != null) {
            if (view === area) return true
            view = view.parent as? View
        }
        return false
    }

    // Confirmed taps go through EditText's native handler, including its accessibility click.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearPendingTouch()
            if (isEnabled && !isFocused && tabsCanSwipe()) {
                pendingDown = MotionEvent.obtain(event)
                tapCandidate = true
                return true
            }
        }
        val down = pendingDown ?: return super.onTouchEvent(event)
        if (abs(event.x - down.x) > touchSlop || abs(event.y - down.y) > touchSlop ||
            event.eventTime - down.eventTime >= ViewConfiguration.getLongPressTimeout()) tapCandidate = false
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> tapCandidate = false
            MotionEvent.ACTION_UP -> {
                val tap = tapCandidate
                pendingDown = null
                tapCandidate = false
                try {
                    if (tap) {
                        super.onTouchEvent(down)
                        super.onTouchEvent(event)
                    }
                } finally { down.recycle() }
            }
            MotionEvent.ACTION_CANCEL -> clearPendingTouch()
        }
        return true
    }

    private fun clearPendingTouch() {
        pendingDown?.recycle()
        pendingDown = null
        tapCandidate = false
    }

    override fun onDetachedFromWindow() {
        clearPendingTouch()
        super.onDetachedFromWindow()
    }
}
