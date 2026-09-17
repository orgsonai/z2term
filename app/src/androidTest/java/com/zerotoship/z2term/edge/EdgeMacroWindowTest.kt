package com.zerotoship.z2term.edge

import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EdgeMacroWindowTest {
    @Test fun argumentsResultsCancellationAndReopeningWorkWithoutAnActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        assumeTrue("Overlay permission required", Settings.canDrawOverlays(app))
        val store = EdgeRuntime.store(app)
        val enabled = store.enabled()
        val id = "form_test_" + UUID.randomUUID().toString().replace("-", "")
        val marker = File(app.filesDir, "shared_home/.$id")
        fun window(): EdgePanelWindow = EdgeRuntime::class.java.getDeclaredField("panelView").let {
            it.isAccessible = true; it.get(EdgeRuntime) as EdgePanelWindow
        }
        fun find(view: View, label: String): View? {
            if (view.contentDescription == label || view is TextView && view.text.toString() == label) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), label)?.let { return it }
            return null
        }
        fun until(block: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!block() && System.nanoTime() < end) Thread.sleep(50)
            assertTrue(block())
        }
        val literal = "日本語 '\" \$(exit 9);\nsecond line"
        try {
            store.setPanel(id, mapOf("handle" to "bar", "width" to "300", "height" to "70%", "close" to "off"))
            store.setItem("$id:result", mapOf("type" to "result", "order" to "0", "rows" to "3", "result-controls" to "on"))
            store.setItem("$id:argument", mapOf("type" to "argument", "order" to "1", "rows" to "3"))
            store.setItem("$id:language", mapOf("type" to "argument", "argument-kind" to "choice", "choices" to "auto|ja|en", "default" to "ja", "order" to "2"))
            store.setItem("$id:fixed", mapOf("type" to "argument", "argument-kind" to "fixed", "default" to "constant", "order" to "3"))
            store.setItem("$id:macro", mapOf("type" to "macro", "run" to "sh -c 'sleep 1; printf \"<%s>\" \"\$@\"' form", "args" to "argument,language,fixed", "result" to "result", "order" to "3"))
            EdgeRuntime.on(app); EdgeRuntime.open(id)
            lateinit var original: EdgePanelWindow
            instrumentation.runOnMainSync {
                original = window()
                assertNotNull(find(original, app.getString(R.string.edge_close)))
                original.findViewWithTag<EditText>("edge-argument:argument").setText(literal)
                find(original, app.getString(R.string.edge_run))!!.performClick()
                original.findViewWithTag<EditText>("edge-argument:argument").setText("changed during the run")
                assertEquals("", original.findViewWithTag<TextView>("edge-result:result").text.toString())
            }
            until { EdgeRuntime.onMain { original.findViewWithTag<TextView>("edge-result:result").text.toString() == "<$literal><ja><constant>" } }
            instrumentation.runOnMainSync {
                val image = android.graphics.Bitmap.createBitmap(original.width, original.height, android.graphics.Bitmap.Config.ARGB_8888)
                original.draw(android.graphics.Canvas(image))
                File(app.cacheDir, "edge-macro-form.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                original.findViewWithTag<EditText>("edge-argument:argument").requestFocus()
                original.back(); assertSame(original, window())
                assertFalse(original.findViewWithTag<EditText>("edge-argument:argument").hasFocus())
                find(original, app.getString(R.string.edge_run))!!.performClick()
                assertEquals("", original.findViewWithTag<TextView>("edge-result:result").text.toString())
                find(original, app.getString(R.string.edge_terminal_clear))!!.performClick()
            }
            Thread.sleep(1400)
            instrumentation.runOnMainSync {
                assertEquals("", original.findViewWithTag<TextView>("edge-result:result").text.toString())
                find(original, app.getString(R.string.edge_close))!!.performClick()
            }
            EdgeRuntime.open(id)
            instrumentation.runOnMainSync {
                assertEquals("", window().findViewWithTag<EditText>("edge-argument:argument").text.toString())
                assertEquals("", window().findViewWithTag<TextView>("edge-result:result").text.toString())
            }
            EdgeRuntime.close()
            store.setItem("$id:macro", mapOf("run" to "sh -c 'sleep 3; touch \"\$HOME/.$id\"' form"))
            EdgeRuntime.reload(app); EdgeRuntime.open(id)
            instrumentation.runOnMainSync { find(window(), app.getString(R.string.edge_run))!!.performClick() }
            Thread.sleep(500)
            EdgeRuntime.close()
            Thread.sleep(3500)
            assertFalse("Closing must stop the command", marker.exists())
        } finally {
            EdgeRuntime.close()
            if (!enabled) EdgeRuntime.off(app)
            if (store.directory(id).isDirectory) store.removePanel(id)
            if (enabled) EdgeRuntime.reload(app)
            marker.delete()
        }
    }
}
