package com.zerotoship.z2term.edge

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID

/** Settings editing writes the same definitions used by the CLI. */
object EdgePanelEditor {
    fun create(context: Context, root: EdgeStore.Panel, panel: EdgeStore.Panel,
        store: EdgeStore, session: EdgeEditorSession, remove: (String) -> Unit, reopen: (String) -> Unit): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var section = EdgeSettingsUi.column(context, gutter = true)
        body.addView(section)
        fun attempt(action: () -> Unit) {
            runCatching(action).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
        fun row(): LinearLayout = EdgeSettingsUi.row(context).also { line ->
            section.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = EdgeEditorUi.dp(context, 8)
            })
        }
        fun button(line: LinearLayout, label: String, kind: EdgeSettingsUi.Kind, action: () -> Unit) {
            line.addView(EdgeSettingsUi.button(context, label, kind) { attempt(action) },
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = EdgeEditorUi.dp(context, 8) })
        }
        // A name and its Save belong on one line: the button is what the entry is for.
        fun nameEntry(label: Int, initial: String, saveLabel: Int, save: (String) -> Unit) {
            section.addView(EdgeSettingsUi.caption(context, context.getString(label)))
            val line = EdgeSettingsUi.row(context)
            val entry = EdgeSettingsUi.field(context).apply {
                setText(initial); contentDescription = context.getString(label)
            }
            session.track(entry) { entry.text.toString() != initial }
            line.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(EdgeSettingsUi.button(context, context.getString(saveLabel), EdgeSettingsUi.Kind.PRIMARY) {
                val name = entry.text.toString().trim()
                if (name.isEmpty()) entry.error = context.getString(R.string.edge_name_required)
                else session.leave(except = entry) { attempt { save(name) } }
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = EdgeEditorUi.dp(context, 8) })
            section.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = EdgeEditorUi.dp(context, 12)
            })
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
            if (index > 0) button(order, context.getString(R.string.edge_tab_previous), EdgeSettingsUi.Kind.OUTLINE) {
                session.leave { attempt { move(-1) } }
            }
            if (index < root.tabs.lastIndex) button(order, context.getString(R.string.edge_tab_next), EdgeSettingsUi.Kind.OUTLINE) {
                session.leave { attempt { move(1) } }
            }
        }
        EdgePanelCommandsUi.add(context, body, store, panel.id)
        section = EdgeSettingsUi.section(context, body, context.getString(R.string.edge_create_panel))
        nameEntry(R.string.edge_new_panel_name, "", R.string.edge_add_panel) { name ->
            val id = "panel_" + UUID.randomUUID().toString().replace("-", "")
            store.setPanel(id, mapOf("label" to name, "handle" to "bar", "side" to "right"))
            reopen(id)
        }
        section = EdgeSettingsUi.section(context, body, context.getString(R.string.edge_remove_panel))
        // Removal states its consequence inside a bordered block, so the confirm is never a stray button.
        val deletion = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = EdgeSettingsUi.frame(context, stroke = EdgeSettingsUi.danger(context))
            setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12),
                EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12))
        }
        val actions = row()
        button(actions, context.getString(R.string.edge_delete_panel), EdgeSettingsUi.Kind.DANGER) {
            deletion.visibility = View.VISIBLE
        }
        deletion.addView(EdgeSettingsUi.body(context,
            context.getString(R.string.edge_delete_panel_warning, panel.fields["label"] ?: panel.id)).apply {
            textSize = 13f
            setLineSpacing(EdgeEditorUi.dp(context, 3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 12) })
        val confirm = EdgeSettingsUi.row(context)
        confirm.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_delete),
            EdgeSettingsUi.Kind.DANGER) { session.leave { attempt { remove(panel.id) } } },
            LinearLayout.LayoutParams(0, -2, 1f))
        confirm.addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
            deletion.visibility = View.GONE
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = EdgeEditorUi.dp(context, 8) })
        deletion.addView(confirm)
        section.addView(deletion)
        val all = store.panels()
        val roots = all.filter { candidate -> all.none { candidate.id in it.tabs } }
        if (roots.size > 1) {
            val choices = EdgeSettingsUi.section(context, body, context.getString(R.string.edge_other_panels))
            roots.filter { it.id != root.id }.forEach { other ->
                choices.addView(EdgeSettingsUi.button(context, other.fields["label"] ?: other.id) {
                    session.leave { attempt { reopen(other.id) } }
                }.apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 8) })
            }
        }
        body.addView(EdgeSettingsUi.hairline(context))
        return body
    }
}
