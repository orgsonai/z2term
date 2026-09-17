package com.zerotoship.z2term.edge

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EdgeTerminalInputsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext

    private fun find(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text || view.contentDescription == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun field(view: View, label: String): EditText? {
        if (view is EditText && view.contentDescription == label) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) field(view.getChildAt(i), label)?.let { return it }
        return null
    }

    private fun until(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < end) {
            var ok = false
            instrumentation.runOnMainSync { ok = check() }
            if (ok) return
            Thread.sleep(30)
        }
        instrumentation.runOnMainSync { assertTrue(check()) }
    }

    @Test fun existingSnippetsInsertWithoutRunningAndPanelEditsUseTheSameStore() {
        val store = SnippetStore(app)
        val id = "edge-input-test-" + UUID.randomUUID()
        val label = "$id registered"
        val command = "printf '$id'"
        runBlocking { store.upsert(Snippet(id, label, command)) }
        val terminal = EdgeTerminalState { EdgeTerminalSession.create(app) }
        lateinit var ui: EdgeTerminalUi
        var created: EdgeTerminalUi? = null
        try {
            instrumentation.runOnMainSync {
                ui = EdgeTerminalUi(app, "", terminal)
                created = ui
                ui.findViewWithTag<View>("edge-snippets-button").performClick()
            }
            until { ui.findViewWithTag<View>("edge-snippet:$id") != null }
            instrumentation.runOnMainSync {
                ui.findViewWithTag<View>("edge-snippet:$id").performClick()
                assertEquals(command, ui.findViewById<EditText>(R.id.edge_terminal_input).text.toString())
                assertNull("Choosing a snippet must never start a command", terminal.state())
                assertEquals(View.GONE, ui.findViewWithTag<View>("edge-snippets").visibility)
                ui.findViewWithTag<View>("edge-snippets-button").performClick()
                val row = ui.findViewWithTag<View>("edge-snippet:$id").parent as ViewGroup
                row.getChildAt(1).performClick()
                field(ui, app.getString(R.string.snippets_command_field))!!.setText("pwd")
                find(ui, app.getString(R.string.action_save))!!.performClick()
            }
            until { runBlocking { store.snippets.first().first { it.id == id }.command == "pwd" } }
            until { ui.findViewWithTag<View>("edge-snippet:$id") != null }
            instrumentation.runOnMainSync {
                val row = ui.findViewWithTag<View>("edge-snippet:$id").parent as ViewGroup
                row.getChildAt(2).performClick()
                find(ui, app.getString(R.string.edge_delete))!!.performClick()
            }
            until { runBlocking { store.snippets.first().none { it.id == id } } }
            instrumentation.runOnMainSync {
                find(ui, app.getString(R.string.snippets_new))!!.performClick()
                field(ui, app.getString(R.string.snippets_label_field))!!.setText("$label new")
                assertEquals(command, field(ui, app.getString(R.string.snippets_command_field))!!.text.toString())
                find(ui, app.getString(R.string.action_save))!!.performClick()
            }
            until { runBlocking { store.snippets.first().any { it.label == "$label new" && it.command == command } } }
        } finally {
            instrumentation.runOnMainSync { created?.dispose(); terminal.close() }
            runBlocking {
                store.delete(id)
                store.snippets.first().filter { it.label == "$label new" }.forEach { store.delete(it.id) }
            }
        }
    }
}
