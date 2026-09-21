package com.zerotoship.z2term.edge

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeViewerGesturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Fixture(context: Context, horizontalTabs: Boolean) {
        val changes = mutableListOf<Boolean>()
        var tapped = 0
        var cancellations = 0
        val window = EdgePanelWindow(context)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val strip = HorizontalScrollView(context)
        private val density = context.resources.displayMetrics.density
        private fun dp(value: Int) = (value * density).toInt()
        private var start = 0L
        val web = object : WebView(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) cancellations++
                super.onTouchEvent(event)
                // A detached WebView has no compositor and rejects DOWN. Retain the touch
                // target so this fixture exercises the real parent's intercept/cancel routing.
                return true
            }
        }.apply {
            // Same request as the real viewer and the native WebView scroll machinery.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }

        init {
            val labels = LinearLayout(context)
            repeat(12) { i ->
                labels.addView(Button(context).apply {
                    text = "Tab $i"
                    setOnClickListener { tapped++ }
                }, LinearLayout.LayoutParams(dp(100), dp(48)))
            }
            strip.addView(labels)
            body.addView(strip, LinearLayout.LayoutParams(-1, dp(48)))
            body.addView(web, LinearLayout.LayoutParams(-1, dp(400)))
            window.addView(body)
            window.swipeArea = body
            window.tabStrip = strip
            window.horizontalTabSwipe = horizontalTabs
            window.changeTab = { changes += it }
            window.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(448), View.MeasureSpec.EXACTLY))
            window.layout(0, 0, dp(400), dp(448))
        }

        fun touch(action: Int, x: Float, y: Float, elapsed: Long) {
            if (action == MotionEvent.ACTION_DOWN) start = SystemClock.uptimeMillis()
            MotionEvent.obtain(start, start + elapsed, action, x * density, y * density, 0).also {
                window.dispatchTouchEvent(it); it.recycle()
            }
        }

        fun drag(from: Float, to: Float, y: Float) {
            touch(MotionEvent.ACTION_DOWN, from, y, 0)
            touch(MotionEvent.ACTION_MOVE, (from + to) / 2, y, 20)
            touch(MotionEvent.ACTION_MOVE, to, y, 40)
            touch(MotionEvent.ACTION_UP, to, y, 60)
        }
        fun dispose() { body.removeView(web); web.destroy() }
    }

    @Test fun overflowingTabStripScrollsWithoutChangingOrClickingTabs() = instrumentation.runOnMainSync {
        for (horizontalTabs in listOf(true, false)) {
            val f = Fixture(context, horizontalTabs)
            try {
                f.drag(300f, 80f, 24f)
                assertTrue("The tab strip must scroll", f.strip.scrollX > 0)
                assertTrue("Dragging labels must not switch the selected tab", f.changes.isEmpty())
                assertEquals("Dragging must cancel the tab button click", 0, f.tapped)
                f.strip.fling(0)
                f.strip.scrollTo(0, 0)
                f.drag(80f, 300f, 24f)
                assertTrue("Even the end of the strip belongs to scrolling", f.changes.isEmpty())
            } finally { f.dispose() }
        }
    }

    @Test fun viewerFlicksChangeBothNeighbouringTabsAndCancelWebTouches() = instrumentation.runOnMainSync {
        for (horizontalTabs in listOf(true, false)) {
            val f = Fixture(context, horizontalTabs)
            try {
                f.drag(300f, 80f, 180f)
                f.drag(80f, 300f, 180f)
                assertEquals(listOf(true, false), f.changes)
                assertEquals("A swipe must not activate an HTML link", 2, f.cancellations)
                assertEquals(0, f.strip.scrollX)
            } finally { f.dispose() }
        }
    }

    @Test fun verticalReadingLongPressAndCancelledSwipesDoNotChangeTabs() = instrumentation.runOnMainSync {
        for (horizontalTabs in listOf(true, false)) {
            val f = Fixture(context, horizontalTabs)
            try {
                f.touch(MotionEvent.ACTION_DOWN, 300f, 280f, 0)
                f.touch(MotionEvent.ACTION_MOVE, 300f, 200f, 20)
                f.touch(MotionEvent.ACTION_MOVE, 80f, 190f, 40)
                f.touch(MotionEvent.ACTION_UP, 80f, 190f, 60)
                assertTrue(f.changes.isEmpty())
                assertEquals("Keep vertical reading with the WebView", 0, f.cancellations)
                val held = ViewConfiguration.getLongPressTimeout().toLong() + 1
                f.touch(MotionEvent.ACTION_DOWN, 300f, 180f, 0)
                f.touch(MotionEvent.ACTION_MOVE, 80f, 180f, held)
                f.touch(MotionEvent.ACTION_UP, 80f, 180f, held + 20)
                assertTrue(f.changes.isEmpty())
                f.touch(MotionEvent.ACTION_DOWN, 300f, 180f, 0)
                f.touch(MotionEvent.ACTION_MOVE, 80f, 180f, 20)
                f.touch(MotionEvent.ACTION_CANCEL, 80f, 180f, 40)
                assertTrue(f.changes.isEmpty())
            } finally { f.dispose() }
        }
    }
}
