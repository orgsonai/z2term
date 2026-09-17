package com.zerotoship.z2term.edge

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeFormEditorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun referencesShowNamesAndReorderIdsWithoutExposingThem() = instrumentation.runOnMainSync {
        val first = EdgeStore.Item("item_opaque_one", mapOf("type" to "argument", "label" to "Source"))
        val second = EdgeStore.Item("item_opaque_two", mapOf("type" to "argument", "label" to "Language"))
        val entry = EditText(context).apply { setText("${first.id},${second.id}") }
        val group = LinearLayout(context).apply { addView(entry) }
        EdgeItemPickers.bindings(context, group, entry, listOf(first, second), arguments = true)
        assertEquals(View.GONE, entry.visibility)
        fun visibleTexts() = descendants(group).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }
        assertTrue(visibleTexts().any { it.text.contains("Source") })
        assertTrue(visibleTexts().none { it.text.contains("item_opaque") })
        val down = descendants(group).first { it.contentDescription?.toString()?.contains("Source") == true && it is TextView && it.text == "↓" }
        down.performClick()
        assertEquals("${second.id},${first.id}", entry.text.toString())
        val remove = descendants(group).first { it is TextView && it.text == "×" }
        remove.performClick()
        assertEquals(first.id, entry.text.toString())
    }

    @Test fun resultToolsRequireOptInAndClosingRemainsAvailable() = instrumentation.runOnMainSync {
        for (controls in listOf(false, true)) {
            var closed = false
            val panel = EdgeStore.Panel("test", emptyMap(), emptyList())
            val form = EdgeMacroUi(context, panel, EdgeRunner(context), inlineClose = { closed = true })
            val row = LinearLayout(context)
            form.addResult(row, EdgeStore.Item("result", mapOf("type" to "result") +
                if (controls) mapOf("result-controls" to "on") else emptyMap()))
            val views = descendants(row)
            for (label in listOf(R.string.edge_terminal_stop, R.string.edge_terminal_copy, R.string.edge_terminal_clear)) {
                assertEquals(controls, views.filterIsInstance<TextView>().any { it.text == context.getString(label) })
            }
            views.first { it.contentDescription == context.getString(R.string.edge_close) }.performClick()
            assertTrue(closed)
            assertTrue(views.filterIsInstance<TextView>().any { it.tag == "edge-result:result" && it.isTextSelectable })
            form.dispose()
        }
    }
}
