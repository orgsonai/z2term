package com.zerotoship.z2term.edge

import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
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
class EdgePanelOutsideTouchTest {
    @Test fun outsideLongPressOpensSettingsForEveryPanelType() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        assumeTrue("Overlay permission required", Settings.canDrawOverlays(app))
        val store = EdgeRuntime.store(app)
        val enabled = store.enabled()
        val id = "outside_test_" + UUID.randomUUID().toString().replace("-", "")
        fun window(): EdgePanelWindow? = EdgeRuntime::class.java.getDeclaredField("panelView").let {
            it.isAccessible = true; it.get(EdgeRuntime) as? EdgePanelWindow
        }
        fun containsText(view: View, label: String): Boolean {
            if (view is TextView && view.text.toString() == label) return true
            return view is ViewGroup && (0 until view.childCount).any {
                containsText(view.getChildAt(it), label)
            }
        }
        fun outsidePress(duration: Long) {
            lateinit var location: IntArray
            instrumentation.runOnMainSync {
                val overlay = window()!!
                val layout = overlay.layoutParams as WindowManager.LayoutParams
                assertEquals(WindowManager.LayoutParams.MATCH_PARENT, layout.width)
                assertEquals(0, layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                location = IntArray(2).also(overlay::getLocationOnScreen)
                val body = overlay.getChildAt(0)
                assertTrue("Top-left point must be outside the body", body.y > 16f)
            }
            val down = SystemClock.uptimeMillis()
            fun event(action: Int) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    location[0] + 8f, location[1] + 8f, 0).also {
                    it.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(instrumentation.uiAutomation.injectInputEvent(it, true)) }
                    finally { it.recycle() }
                }
            }
            event(MotionEvent.ACTION_DOWN)
            try { Thread.sleep(duration) } finally { event(MotionEvent.ACTION_UP) }
            instrumentation.waitForIdleSync()
        }
        try {
            store.setPanel(id, mapOf("handle" to "bar", "width" to "50%",
                "height" to "40%", "at" to "100%,100%", "fit" to "fixed"))
            for (type in listOf("run", "input", "note", "argument", "result", "terminal")) {
                EdgeRuntime.close()
                store.setItem("$id:content", mapOf("type" to type, "label" to "Outside test"))
                EdgeRuntime.on(app); EdgeRuntime.reload(app); EdgeRuntime.open(id)
                instrumentation.waitForIdleSync()
                outsidePress(50)
                instrumentation.runOnMainSync { assertNull("Idle $type must close", window()) }
                EdgeRuntime.open(id)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { window()!!.back(); assertNull(window()) }
                EdgeRuntime.open(id)
                instrumentation.waitForIdleSync()
                outsidePress(ViewConfiguration.getLongPressTimeout().toLong() + 150)
                instrumentation.runOnMainSync {
                    assertNotNull(window())
                    assertTrue("Outside long press must open settings for $type",
                        containsText(window()!!, app.getString(R.string.edge_done)))
                }
            }
        } finally {
            EdgeRuntime.close()
            if (!enabled) EdgeRuntime.off(app)
            if (store.directory(id).isDirectory) store.removePanel(id)
            if (enabled) EdgeRuntime.reload(app)
        }
    }
}
