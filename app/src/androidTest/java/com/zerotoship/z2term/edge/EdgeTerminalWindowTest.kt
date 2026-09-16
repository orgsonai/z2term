package com.zerotoship.z2term.edge

import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.File

@RunWith(AndroidJUnit4::class)
class EdgeTerminalWindowTest {
    @Test fun outsideTapAndBackKeepThePanelButCloseReopensAnEmptySession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        assumeTrue("Overlay permission required for window test", Settings.canDrawOverlays(app))
        val store = EdgeRuntime.store(app)
        val enabled = store.enabled()
        val id = "terminal_test_" + UUID.randomUUID().toString().replace("-", "")
        fun window(): EdgePanelWindow = EdgeRuntime::class.java.getDeclaredField("panelView").let {
            it.isAccessible = true; it.get(EdgeRuntime) as EdgePanelWindow
        }
        fun until(block: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!block() && System.nanoTime() < end) Thread.sleep(50)
            assertTrue(block())
        }
        fun text(view: View, value: String): View? {
            if (view is TextView && view.text.toString() == value) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) text(view.getChildAt(i), value)?.let { return it }
            return null
        }
        try {
            store.setPanel(id, mapOf("handle" to "bar", "width" to "260", "height" to "60%", "at" to "0%,100%", "close" to "off"))
            store.setItem("$id:terminal", mapOf("type" to "terminal", "label" to "Test"))
            EdgeRuntime.on(app); EdgeRuntime.open(id)
            lateinit var original: EdgePanelWindow
            instrumentation.runOnMainSync {
                original = window()
                val layout = original.layoutParams as WindowManager.LayoutParams
                assertTrue(layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
                assertTrue(layout.width > 0)
                assertNotNull("Close is mandatory", text(original, app.getString(R.string.edge_close)))
                original.findViewById<EditText>(R.id.edge_terminal_input).apply {
                    setText("cd /; printf KEEP_RESULT"); onEditorAction(EditorInfo.IME_ACTION_GO)
                }
            }
            until { EdgeRuntime.onMain { original.findViewById<TextView>(R.id.edge_terminal_output).text.contains("KEEP_RESULT") } }
            instrumentation.runOnMainSync {
                val image = android.graphics.Bitmap.createBitmap(original.width, original.height, android.graphics.Bitmap.Config.ARGB_8888)
                original.draw(android.graphics.Canvas(image))
                File(app.cacheDir, "edge-terminal-test.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                val input = original.findViewById<EditText>(R.id.edge_terminal_input)
                input.requestFocus()
                app.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            until { EdgeRuntime.onMain { ViewCompat.getRootWindowInsets(original)?.isVisible(WindowInsetsCompat.Type.ime()) == true } }
            Thread.sleep(350) // Let the platform IME animation finish before recording its final bounds.
            instrumentation.runOnMainSync {
                val origin = IntArray(2); original.getLocationOnScreen(origin)
                val bottom = ViewCompat.getRootWindowInsets(original)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
                File(app.cacheDir, "edge-terminal-ime.txt").writeText("top=${origin[1]} height=${original.height} imeInset=$bottom screen=${app.resources.displayMetrics.heightPixels}\n")
                assertTrue(original.hideKeyboard())
                assertSame(original, window())
                assertEquals("KEEP_RESULT", original.findViewById<TextView>(R.id.edge_terminal_output).text.toString())
            }
            until { EdgeRuntime.onMain { ViewCompat.getRootWindowInsets(original)?.isVisible(WindowInsetsCompat.Type.ime()) == false } }
            // Hit a real point outside the bounded overlay, so Android sends ACTION_OUTSIDE.
            val metrics = app.resources.displayMetrics
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(now, now + action * 20, action, (metrics.widthPixels - 8).toFloat(),
                    (metrics.heightPixels / 2).toFloat(), 0).also {
                    it.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle()
                }
            }
            instrumentation.runOnMainSync {
                assertSame(original, window()); assertTrue(original.isAttachedToWindow)
                original.back()
                assertSame(original, window())
                assertEquals("cd /; printf KEEP_RESULT", original.findViewById<EditText>(R.id.edge_terminal_input).text.toString())
                assertEquals("KEEP_RESULT", original.findViewById<TextView>(R.id.edge_terminal_output).text.toString())
                text(original, app.getString(R.string.edge_close))!!.performClick()
            }
            until { EdgeRuntime.onMain { !original.isAttachedToWindow } }
            EdgeRuntime.open(id)
            instrumentation.runOnMainSync {
                val fresh = window()
                assertNotSame(original, fresh)
                assertEquals("", fresh.findViewById<EditText>(R.id.edge_terminal_input).text.toString())
                assertEquals("", fresh.findViewById<TextView>(R.id.edge_terminal_output).text.toString())
            }
        } finally {
            EdgeRuntime.close()
            if (!enabled) EdgeRuntime.off(app)
            if (store.directory(id).isDirectory) store.removePanel(id)
            if (enabled) EdgeRuntime.reload(app)
        }
    }
}
