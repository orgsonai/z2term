package com.zerotoship.z2term.edge

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID

/** Inline editing writes the same definitions used by the CLI. */
object EdgePanelEditor {
    fun create(context: Context, root: EdgeStore.Panel, panel: EdgeStore.Panel,
        store: EdgeStore, remove: (String) -> Unit, reopen: (String) -> Unit): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun attempt(action: () -> Unit) {
            runCatching(action).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
        fun row(): LinearLayout = LinearLayout(context).also { line ->
            body.addView(HorizontalScrollView(context).apply { addView(line) })
        }
        fun button(line: LinearLayout, label: String, action: () -> Unit) {
            line.addView(Button(context).apply { text = label; setOnClickListener { attempt(action) } })
        }
        fun nameEntry(label: Int, initial: String, saveLabel: Int, save: (String) -> Unit) {
            body.addView(TextView(context).apply { text = context.getString(label) })
            val line = LinearLayout(context)
            val entry = EditText(context).apply {
                setSingleLine(true); setText(initial); contentDescription = context.getString(label)
            }
            line.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
            button(line, context.getString(saveLabel)) {
                val name = entry.text.toString().trim()
                if (name.isEmpty()) entry.error = context.getString(R.string.edge_name_required)
                else save(name)
            }
            body.addView(line)
        }
        nameEntry(R.string.edge_panel_name, panel.fields["label"] ?: panel.id, R.string.edge_save) { name ->
            store.setPanel(panel.id, mapOf("label" to name))
            reopen(panel.id)
        }
        if (panel.id != root.id) {
            val order = row()
            fun move(delta: Int) {
                val ids = store.panel(root.id).tabs.toMutableList()
                val index = ids.indexOf(panel.id)
                require(index >= 0) { "Tab no longer belongs to this panel" }
                val next = index + delta
                if (next in ids.indices) {
                    ids.removeAt(index); ids.add(next, panel.id)
                    store.setPanel(root.id, mapOf("tabs" to ids.joinToString(",")))
                    reopen(panel.id)
                }
            }
            val index = root.tabs.indexOf(panel.id)
            if (index > 0) button(order, context.getString(R.string.edge_tab_previous)) { move(-1) }
            if (index < root.tabs.lastIndex) button(order, context.getString(R.string.edge_tab_next)) { move(1) }
        }
        nameEntry(R.string.edge_new_panel_name, "", R.string.edge_add_panel) { name ->
            val id = "panel_" + UUID.randomUUID().toString().replace("-", "")
            store.setPanel(id, mapOf("label" to name, "handle" to "bar", "side" to "right"))
            reopen(id)
        }
        val deletion = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val actions = row()
        button(actions, context.getString(R.string.edge_delete_panel)) { deletion.visibility = View.VISIBLE }
        deletion.addView(TextView(context).apply {
            text = context.getString(R.string.edge_delete_panel_warning, panel.fields["label"] ?: panel.id)
        })
        val confirm = LinearLayout(context)
        button(confirm, context.getString(R.string.edge_delete)) { remove(panel.id) }
        button(confirm, context.getString(android.R.string.cancel)) { deletion.visibility = View.GONE }
        deletion.addView(confirm)
        body.addView(deletion)
        val all = store.panels()
        val roots = all.filter { candidate -> all.none { candidate.id in it.tabs } }
        if (roots.size > 1) {
            body.addView(TextView(context).apply { text = context.getString(R.string.edge_other_panels) })
            val choices = row()
            roots.filter { it.id != root.id }.forEach { other ->
                button(choices, other.fields["label"] ?: other.id) { reopen(other.id) }
            }
        }
        return body
    }
}
