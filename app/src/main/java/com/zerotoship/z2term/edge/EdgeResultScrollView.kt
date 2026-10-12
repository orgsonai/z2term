package com.zerotoship.z2term.edge

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ScrollView

/** Keep result scrolling inside this view; EdgePanelWindow still observes horizontal tab flicks. */
@android.annotation.SuppressLint("ViewConstructor")
internal class EdgeResultScrollView(context: Context) : ScrollView(context) {
    /** Two-finger pinch, when set. The second finger takes the gesture away from scrolling and selection. */
    var pinch: ScaleGestureDetector? = null
    private var pinching = false

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val last = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            pinching = false
        }
        val detector = pinch
        if (detector != null) {
            detector.onTouchEvent(event)
            if (!pinching && event.pointerCount > 1) {
                pinching = true
                // The children saw the first finger go down: end that touch before the pinch owns the rest.
                val cancel = MotionEvent.obtain(event).apply { this.action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (pinching) {
                if (last) parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return try { super.dispatchTouchEvent(event) } finally {
            if (last) parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
