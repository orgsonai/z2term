package com.zerotoship.z2term.edge

import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EdgePanelLayoutWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext

    private fun window(): EdgePanelWindow = EdgeRuntime::class.java.getDeclaredField("panelView").let {
        it.isAccessible = true; it.get(EdgeRuntime) as EdgePanelWindow
    }

    private fun bounds(view: View): Rect {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }

    private fun find(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), text)?.let { return it }
        return null
    }

    @Test fun tabsAndCloseKeepTheirBoundsAcrossMemoTerminalAndEmptyPages() {
        assumeTrue(Settings.canDrawOverlays(app))
        val store = EdgeRuntime.store(app)
        val enabled = store.enabled()
        val id = "layout_" + UUID.randomUUID().toString().replace("-", "")
        try {
            store.setPanel(id, mapOf("label" to "Memo", "width" to "77%", "height" to "61%", "at" to "0%,100%",
                "title" to "off", "close" to "off", "add" to "off", "settings" to "off", "tabbar" to "on"))
            store.setItem("$id:note", mapOf("type" to "note", "label" to ""))
            store.addTab(id, "${id}_shell", "Terminal")
            store.addTab(id, "${id}_empty", "Empty")
            store.setItem("${id}_shell:hidden_terminal_id", mapOf("type" to "terminal", "label" to ""))
            EdgeRuntime.on(app)
            var original: EdgePanelWindow? = null
            var frame: Rect? = null
            var positions: List<Rect>? = null
            for (tab in listOf(id, "${id}_shell", "${id}_empty", id, "${id}_shell")) {
                EdgeRuntime.open(id, tabId = tab)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val current = window()
                    if (original == null) original = current else assertSame(original, current)
                    val p = current.layoutParams as WindowManager.LayoutParams
                    val nextFrame = Rect(p.x, p.y, p.x + p.width, p.y + p.height)
                    if (frame == null) frame = nextFrame else assertEquals(frame, nextFrame)
                    val nav = current.findViewWithTag<ViewGroup>("edge-navigation")
                    val close = nav.getChildAt(nav.childCount - 1)
                    assertEquals(app.getString(R.string.edge_close), close.contentDescription)
                    val nextPositions = listOf(bounds(nav), bounds(close)) +
                        listOf("Memo", "Terminal", "Empty").map { bounds(find(nav, it)!!) }
                    if (positions == null) positions = nextPositions else assertEquals(positions, nextPositions)
                    if (tab.endsWith("_shell")) {
                        assertNull(find(current, "hidden_terminal_id"))
                        assertNull(find(current, app.getString(R.string.edge_terminal_help)))
                        val input = current.findViewById<EditText>(R.id.edge_terminal_input)
                        val terminal = input.parent.parent as EdgeTerminalUi
                        assertSame(input.parent, terminal.getChildAt(terminal.childCount - 1))
                        assertTrue(bounds(input).bottom > bounds(current).bottom - 32 * app.resources.displayMetrics.density)
                        assertTrue(current.findViewWithTag<View>("edge-terminal-scroll").height > 0)
                    }
                }
            }
        } finally {
            EdgeRuntime.close()
            if (!enabled) EdgeRuntime.off(app)
            if (store.directory(id).isDirectory) store.removePanel(id)
            if (enabled) EdgeRuntime.reload(app)
        }
    }

    private class ResultGestureFixture(app: android.content.Context, horizontalTabs: Boolean) {
        val window = EdgePanelWindow(app)
        val outer = ScrollView(app)
        val rows = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        val result = EdgeResultScrollView(app)
        val tabs = mutableListOf<Boolean>()
        var cancellations = 0
        private var start = 0L
        private val density = app.resources.displayMetrics.density
        private fun dp(value: Int) = (value * density).toInt()

        init {
            result.addView(object : TextView(app) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) cancellations++
                    return super.onTouchEvent(event)
                }
            }.apply {
                text = (1..200).joinToString("\n") { "Result line $it" }
                setTextIsSelectable(true)
            })
            rows.addView(result, LinearLayout.LayoutParams(dp(400), dp(200)))
            rows.addView(View(app), LinearLayout.LayoutParams(dp(400), dp(1200)))
            outer.addView(rows); window.addView(outer)
            window.swipeArea = rows
            window.horizontalTabSwipe = horizontalTabs
            window.changeTab = { tabs.add(it) }
            window.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(500), View.MeasureSpec.EXACTLY))
            window.layout(0, 0, dp(400), dp(500))
        }

        fun touch(action: Int, x: Float, y: Float, elapsed: Long) {
            if (action == MotionEvent.ACTION_DOWN) start = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(start, start + elapsed, action, x * density, y * density, 0)
            window.dispatchTouchEvent(event); event.recycle()
        }
    }

    @Test fun verticalResultDragScrollsInsideTheResultInEitherPanelLayout() {
        instrumentation.runOnMainSync {
            for (horizontalTabs in listOf(true, false)) {
                val f = ResultGestureFixture(app, horizontalTabs)
                f.touch(MotionEvent.ACTION_DOWN, 100f, 180f, 0)
                f.touch(MotionEvent.ACTION_MOVE, 100f, 120f, 30)
                f.touch(MotionEvent.ACTION_MOVE, 100f, 30f, 60)
                f.touch(MotionEvent.ACTION_UP, 100f, 30f, 90)
                assertTrue("The result must scroll", f.result.scrollY > 0)
                assertEquals(0, f.outer.scrollY)
                assertTrue(f.tabs.isEmpty())
                f.result.scrollTo(0, Int.MAX_VALUE)
                assertFalse("The final result line is reachable", f.result.canScrollVertically(1))
            }
        }
    }

    @Test fun horizontalResultFlicksSelectBothNeighbouringTabsWithoutScrolling() {
        instrumentation.runOnMainSync {
            for (horizontalTabs in listOf(true, false)) {
                val f = ResultGestureFixture(app, horizontalTabs)
                for ((from, to) in listOf(300f to 100f, 100f to 300f)) {
                    f.touch(MotionEvent.ACTION_DOWN, from, 100f, 0)
                    f.touch(MotionEvent.ACTION_MOVE, (from + to) / 2, 100f, 30)
                    f.touch(MotionEvent.ACTION_MOVE, to, 100f, 60)
                    f.touch(MotionEvent.ACTION_UP, to, 100f, 90)
                }
                assertEquals(listOf(true, false), f.tabs)
                assertEquals("Each tab gesture cancels the result's touch", 2, f.cancellations)
                assertEquals(0, f.outer.scrollY)
                assertEquals(0, f.result.scrollY)
            }
        }
    }

    @Test fun verticalStartLongPressAndCancelledFlickDoNotChangeTabs() {
        instrumentation.runOnMainSync {
            val f = ResultGestureFixture(app, true)
            val slop = android.view.ViewConfiguration.get(app).scaledTouchSlop /
                app.resources.displayMetrics.density + 1f
            f.touch(MotionEvent.ACTION_DOWN, 300f, 100f, 0)
            f.touch(MotionEvent.ACTION_MOVE, 300f, 100f - slop, 20)
            f.touch(MotionEvent.ACTION_MOVE, 100f, 100f - slop, 40)
            f.touch(MotionEvent.ACTION_UP, 100f, 100f - slop, 60)
            assertTrue("A vertical scroll cannot turn into tab navigation", f.tabs.isEmpty())
            val held = android.view.ViewConfiguration.getLongPressTimeout().toLong() + 1
            f.touch(MotionEvent.ACTION_DOWN, 300f, 100f, 0)
            f.touch(MotionEvent.ACTION_MOVE, 100f, 100f, held)
            f.touch(MotionEvent.ACTION_UP, 100f, 100f, held + 20)
            assertTrue("Keep long presses for text selection", f.tabs.isEmpty())
            f.touch(MotionEvent.ACTION_DOWN, 300f, 100f, 0)
            f.touch(MotionEvent.ACTION_MOVE, 100f, 100f, 30)
            f.touch(MotionEvent.ACTION_CANCEL, 100f, 100f, 60)
            assertTrue(f.tabs.isEmpty())
        }
    }

    @Test fun listDeletionNeedsNoEditorAndCancelKeepsTheItem() {
        assumeTrue(Settings.canDrawOverlays(app))
        val store = EdgeRuntime.store(app)
        val enabled = store.enabled()
        val id = "delete_" + UUID.randomUUID().toString().replace("-", "")
        try {
            store.setPanel(id, emptyMap())
            store.setItem("$id:keep", mapOf("type" to "note"))
            store.setItem("$id:remove", mapOf("type" to "terminal"))
            EdgeRuntime.on(app); EdgeRuntime.open(id, settings = true)
            instrumentation.runOnMainSync {
                window().findViewWithTag<View>("edge-delete:remove").performClick()
                assertEquals(View.VISIBLE, window().findViewWithTag<View>("edge-delete-confirm:remove").visibility)
                window().findViewWithTag<View>("edge-delete-cancel:remove").performClick()
                assertEquals(2, store.panel(id).items.size)
                window().findViewWithTag<View>("edge-delete:remove").performClick()
                window().findViewWithTag<View>("edge-delete-accept:remove").performClick()
                assertEquals(listOf("keep"), store.panel(id).items.map { it.id })
                assertNull(window().findViewWithTag<View>("edge-delete:remove"))
            }
        } finally {
            EdgeRuntime.close()
            if (!enabled) EdgeRuntime.off(app)
            if (store.directory(id).isDirectory) store.removePanel(id)
            if (enabled) EdgeRuntime.reload(app)
        }
    }
}
