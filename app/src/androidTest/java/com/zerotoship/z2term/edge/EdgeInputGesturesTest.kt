package com.zerotoship.z2term.edge

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeInputGesturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Fixture(context: Context, horizontalTabs: Boolean = true) {
        val changes = mutableListOf<Boolean>()
        val window = EdgePanelWindow(context)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val entry = EdgeSettingsUi.field(context, 4).apply {
            setText("first line\nsecond line\nthird line\nfourth line")
        }
        private val density = context.resources.displayMetrics.density
        private var start = 0L

        init {
            body.addView(entry, LinearLayout.LayoutParams(-1, (300 * density).toInt()))
            window.addView(body)
            window.swipeArea = body
            window.horizontalTabSwipe = horizontalTabs
            window.changeTab = { changes += it }
            window.measure(View.MeasureSpec.makeMeasureSpec((400 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((400 * density).toInt(), View.MeasureSpec.EXACTLY))
            window.layout(0, 0, (400 * density).toInt(), (400 * density).toInt())
            window.requestFocus()
        }

        fun touch(action: Int, x: Float, y: Float, elapsed: Long) {
            if (action == MotionEvent.ACTION_DOWN) start = SystemClock.uptimeMillis()
            MotionEvent.obtain(start, start + elapsed, action, x * density, y * density, 0).also {
                window.dispatchTouchEvent(it)
                it.recycle()
            }
        }

        fun swipe(horizontal: Boolean, forward: Boolean) {
            val from = if (forward) 240f else 80f
            val to = if (forward) 80f else 240f
            touch(MotionEvent.ACTION_DOWN, if (horizontal) from else 180f,
                if (horizontal) 180f else from, 0)
            touch(MotionEvent.ACTION_MOVE, if (horizontal) (from + to) / 2 else 180f,
                if (horizontal) 180f else (from + to) / 2, 20)
            touch(MotionEvent.ACTION_UP, if (horizontal) to else 180f,
                if (horizontal) 180f else to, 40)
        }
    }

    @Test fun inputSwipesChangeBothNeighboursWithoutEnteringEditing() = instrumentation.runOnMainSync {
        for (horizontal in listOf(true, false)) {
            val f = Fixture(context, horizontal)
            val original = f.entry.text.toString()
            f.swipe(horizontal, true)
            f.swipe(horizontal, false)
            assertEquals(listOf(true, false), f.changes)
            assertFalse("Swiping an unfocused input must not focus it", f.entry.isFocused)
            assertEquals(original, f.entry.text.toString())
        }
    }

    @Test fun onlyCompletedTapsFocusTheInput() = instrumentation.runOnMainSync {
        val f = Fixture(context)
        f.touch(MotionEvent.ACTION_DOWN, 180f, 180f, 0)
        assertFalse("Do not start editing on DOWN", f.entry.isFocused)
        f.touch(MotionEvent.ACTION_MOVE, 180f, 180f, 20)
        assertFalse("Do not start editing while deciding the gesture", f.entry.isFocused)
        f.touch(MotionEvent.ACTION_UP, 180f, 180f, 40)
        assertTrue("A tap retains native EditText focus", f.entry.isFocused)
        assertTrue(f.changes.isEmpty())
    }

    @Test fun swipesStillChangeTabsWhenTheInputIsAlreadyFocused() = instrumentation.runOnMainSync {
        for (horizontal in listOf(true, false)) {
            val f = Fixture(context, horizontal)
            f.entry.requestFocus()
            assertTrue(f.entry.isFocused)
            f.swipe(horizontal, true)
            f.swipe(horizontal, false)
            assertEquals(listOf(true, false), f.changes)
        }
    }

    @Test fun cancelledDragsAndCrossAxisScrollingDoNotEnterEditing() = instrumentation.runOnMainSync {
        val f = Fixture(context)
        f.touch(MotionEvent.ACTION_DOWN, 180f, 180f, 0)
        f.touch(MotionEvent.ACTION_CANCEL, 180f, 180f, 20)
        assertFalse(f.entry.isFocused)
        f.swipe(horizontal = false, forward = true)
        assertFalse("Scrolling must not start typing", f.entry.isFocused)
        assertTrue(f.changes.isEmpty())
        f.touch(MotionEvent.ACTION_DOWN, 240f, 180f, 0)
        f.touch(MotionEvent.ACTION_MOVE, 80f, 180f, 20)
        f.touch(MotionEvent.ACTION_CANCEL, 80f, 180f, 40)
        assertTrue("Cancelled swipes must not navigate", f.changes.isEmpty())
        assertFalse(f.entry.isFocused)
        f.touch(MotionEvent.ACTION_DOWN, 180f, 180f, 0)
        f.touch(MotionEvent.ACTION_UP, 180f, 180f, 20)
        assertTrue("Cancellation must not break the next tap", f.entry.isFocused)
    }

    @Test fun holdingAnUnfocusedInputDoesNotBecomeATapOrTabSwipe() = instrumentation.runOnMainSync {
        val f = Fixture(context)
        val held = ViewConfiguration.getLongPressTimeout().toLong() + 1
        f.touch(MotionEvent.ACTION_DOWN, 240f, 180f, 0)
        f.touch(MotionEvent.ACTION_MOVE, 80f, 180f, held)
        f.touch(MotionEvent.ACTION_UP, 80f, 180f, held + 20)
        assertFalse(f.entry.isFocused)
        assertTrue(f.changes.isEmpty())
    }

    @Test fun settingsAndPanelsWithoutTabsKeepNativeTouchDelivery() = instrumentation.runOnMainSync {
        val f = Fixture(context)
        f.window.swipeArea = null
        f.swipe(horizontal = true, forward = true)
        assertTrue(f.changes.isEmpty())
        f.touch(MotionEvent.ACTION_DOWN, 180f, 180f, 0)
        f.touch(MotionEvent.ACTION_UP, 180f, 180f, 20)
        assertTrue(f.entry.isFocused)
    }
}
