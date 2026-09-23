package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID

/** Settings editing writes the same definitions used by the CLI. */
object EdgePanelEditor {
    fun create(context: Context, root: EdgeStore.Panel, panel: EdgeStore.Panel,
        store: EdgeStore, session: EdgeEditorSession, remove: (Collection<String>) -> Unit, reopen: (String) -> Unit): View {
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
        val all = store.panels()
        addRemoval(context, section, root, all, session, ::attempt, remove)
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

    /**
     * One tick per panel and per tab, across every panel, so several can go in one pass. Ticking a
     * panel takes its tabs with it (they show as ticked and locked); a tab ticked on its own leaves
     * the rest of its panel alone. The first tab is the panel itself, so it only goes with the panel.
     */
    // The warning lists translated lines, one per panel; only the line breaks are joined here.
    @SuppressLint("SetTextI18n")
    private fun addRemoval(context: Context, section: LinearLayout, current: EdgeStore.Panel,
        all: List<EdgeStore.Panel>, session: EdgeEditorSession, attempt: (() -> Unit) -> Unit,
        remove: (Collection<String>) -> Unit) {
        fun name(panel: EdgeStore.Panel) = panel.fields["label"]?.takeIf { it.isNotBlank() } ?: panel.id
        val byId = all.associateBy { it.id }
        val roots = all.filter { candidate -> all.none { candidate.id in it.tabs } }
            .sortedBy { if (it.id == current.id) 0 else 1 }
        val parents = linkedMapOf<String, CheckBox>()
        val tabs = linkedMapOf<String, Pair<String, CheckBox>>()
        section.addView(EdgeSettingsUi.note(context, context.getString(R.string.edge_delete_select_help)),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 8) })
        val confirm = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            tag = "edge-panels-delete-confirm"
            background = EdgeSettingsUi.frame(context, stroke = EdgeSettingsUi.danger(context))
            setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12),
                EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12))
        }
        val warning = EdgeSettingsUi.body(context, "").apply {
            textSize = 13f
            setLineSpacing(EdgeEditorUi.dp(context, 3).toFloat(), 1f)
        }
        val start = EdgeSettingsUi.button(context, "", EdgeSettingsUi.Kind.DANGER) {}.apply {
            tag = "edge-panels-delete"
        }
        fun selection(): List<String> = parents.filter { it.value.isChecked }.keys.toList() +
            tabs.filter { (_, entry) -> entry.second.isChecked && parents[entry.first]?.isChecked != true }.keys
        fun count() = parents.values.count { it.isChecked } + tabs.values.count { it.second.isChecked }
        fun sync() {
            val selected = count()
            start.text = context.getString(R.string.edge_delete_selected, selected)
            start.isEnabled = selected > 0
            start.alpha = if (selected > 0) 1f else 0.4f
            // A changed selection invalidates the list the warning named.
            confirm.visibility = View.GONE
        }
        roots.forEach { panel ->
            val kind = if (panel.tabs.isEmpty()) context.getString(R.string.edge_delete_kind_panel)
                else context.getString(R.string.edge_delete_whole_panel, panel.tabs.size + 1)
            val detail = if (panel.id == current.id) "$kind · ${context.getString(R.string.edge_delete_current)}" else kind
            val children = mutableListOf<CheckBox>()
            val (line, box) = EdgeSettingsUi.checkRow(context, name(panel), detail) { checked ->
                children.forEach { it.isChecked = checked; it.isEnabled = !checked }
                sync()
            }
            box.tag = "edge-panel-check:${panel.id}"
            parents[panel.id] = box
            section.addView(line, LinearLayout.LayoutParams(-1, -2))
            if (panel.tabs.isNotEmpty()) {
                val nested = EdgeSettingsUi.indent(context, section)
                panel.tabs.mapNotNull { byId[it] }.forEach { tab ->
                    val (tabLine, tabBox) = EdgeSettingsUi.checkRow(context, name(tab),
                        context.getString(R.string.edge_delete_kind_tab)) { sync() }
                    tabBox.tag = "edge-panel-check:${tab.id}"
                    children += tabBox
                    tabs[tab.id] = panel.id to tabBox
                    nested.addView(tabLine, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
        start.setOnClickListener {
            val lines = selection().map { id ->
                val panel = byId.getValue(id)
                val parent = tabs[id]?.first?.let { byId.getValue(it) }
                val kind = when {
                    parent != null -> context.getString(R.string.edge_delete_tab_of, name(parent))
                    panel.tabs.isEmpty() -> context.getString(R.string.edge_delete_kind_panel)
                    else -> context.getString(R.string.edge_delete_whole_panel, panel.tabs.size + 1)
                }
                context.getString(R.string.edge_delete_line, name(panel), kind)
            }
            warning.text = (listOf(context.getString(R.string.edge_delete_selected_warning, count())) + lines)
                .joinToString("\n")
            confirm.visibility = View.VISIBLE
        }
        section.addView(start, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = EdgeEditorUi.dp(context, 10); bottomMargin = EdgeEditorUi.dp(context, 10)
        })
        confirm.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 12) })
        val choice = EdgeSettingsUi.row(context)
        choice.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_delete),
            EdgeSettingsUi.Kind.DANGER) {
            val ids = selection()
            if (ids.isNotEmpty()) session.leave { attempt { remove(ids) } }
        }.apply { tag = "edge-panels-delete-accept" }, LinearLayout.LayoutParams(0, -2, 1f))
        choice.addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
            confirm.visibility = View.GONE
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = EdgeEditorUi.dp(context, 8) })
        confirm.addView(choice)
        section.addView(confirm, LinearLayout.LayoutParams(-1, -2))
        sync()
    }
}
