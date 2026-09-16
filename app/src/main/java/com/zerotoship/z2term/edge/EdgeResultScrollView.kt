package com.zerotoship.z2term.edge

import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView

/** Keep result scrolling inside this view; EdgePanelWindow still observes horizontal tab flicks. */
@android.annotation.SuppressLint("ViewConstructor")
internal class EdgeResultScrollView(context: Context) : ScrollView(context) {
    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN)
            parent?.requestDisallowInterceptTouchEvent(true)
        return try { super.dispatchTouchEvent(event) } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
