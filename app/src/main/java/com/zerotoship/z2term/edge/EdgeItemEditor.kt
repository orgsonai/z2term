package com.zerotoship.z2term.edge

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID

/** A local draft; closing or cancelling the editor never updates the definition. */
object EdgeItemEditor {
    fun create(context: Context, panelId: String, item: EdgeStore.Item?, store: EdgeStore,
        beforeSave: () -> Unit, saved: () -> Unit, expanded: Boolean = false,
        cancelled: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
        session: EdgeEditorSession): View {
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val draft = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding(EdgeEditorUi.dp(context, 16), 0, EdgeEditorUi.dp(context, 16), EdgeEditorUi.dp(context, 12))
        }
        if (!expanded) outer.addView(EdgeEditorUi.button(context,
            context.getString(if (item == null) R.string.edge_add_item else R.string.edge_edit_item)) {
            draft.visibility = if (draft.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(-1, -2))
        outer.addView(draft)
        val appCommand = item?.command?.takeIf { AppLaunchCommand.packageFrom(it) != null }
        var launchMode = AppLaunchCommand.modeFrom(appCommand.orEmpty())
        val types = listOf("run", "text", "toggle", "list", "input", "note")
        val type = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.edge_type_run, R.string.edge_type_text, R.string.edge_type_toggle,
                    R.string.edge_type_list, R.string.edge_type_input, R.string.edge_note).map { context.getString(it) })
            contentDescription = context.getString(R.string.edge_item_type)
            setSelection(types.indexOf(item?.type ?: "run"))
        }
        if (appCommand == null) {
            draft.addView(EdgeEditorUi.label(context, context.getString(R.string.edge_item_type)))
            draft.addView(type)
        }
        if (appCommand != null) {
            draft.addView(EdgeEditorUi.label(context, context.getString(R.string.edge_window_mode)))
            draft.addView(Spinner(context).apply {
                contentDescription = context.getString(R.string.edge_window_mode)
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    listOf(R.string.edge_window_full, R.string.edge_window_freeform, R.string.edge_window_split, R.string.edge_window_ask).map { context.getString(it) })
                setSelection(AppLaunch.modes.indexOf(launchMode))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { launchMode = AppLaunch.modes[position] }
                }
            })
        }
        val entries = linkedMapOf<String, EditText>()
        val groups = linkedMapOf<String, LinearLayout>()
        val basic = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        draft.addView(basic)
        val advanced = EdgeEditorUi.section(context, draft, R.string.edge_advanced)
        val fields = linkedMapOf("label" to R.string.edge_item_label, "icon" to R.string.edge_item_icon,
            "run" to R.string.edge_item_run, "state" to R.string.edge_item_state,
            "on-select" to R.string.edge_item_select, "every" to R.string.edge_item_every,
            "timeout" to R.string.edge_item_timeout, "out" to R.string.edge_item_out, "file" to R.string.edge_item_file)
        fields.forEach { (key, label) ->
            val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            group.addView(TextView(context).apply { text = context.getString(label) })
            val entry = EditText(context).apply {
                setSingleLine(true); setText(item?.fields?.get(key).orEmpty())
                contentDescription = context.getString(label)
            }
            entries[key] = entry; groups[key] = group
            group.addView(entry)
            val optional = key in listOf("icon", "every", "timeout", "out", "file") || (appCommand != null && key == "run")
            (if (optional) advanced else basic).addView(group)
        }
        fun showFields() {
            val selected = types[type.selectedItemPosition]
            groups.forEach { (key, group) ->
                val visible = when (key) {
                    "state" -> selected == "toggle"
                    "on-select" -> selected == "list"
                    "every" -> selected in listOf("text", "toggle", "list")
                    "file" -> selected == "note"
                    "run", "timeout", "out" -> selected != "note"
                    else -> true
                }
                group.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showFields()
        }
        showFields()
        session.track(outer) {
            types[type.selectedItemPosition] != (item?.type ?: "run") ||
                launchMode != AppLaunchCommand.modeFrom(appCommand.orEmpty()) ||
                entries.any { (key, entry) -> entry.text.toString() != item?.fields?.get(key).orEmpty() }
        }
        draft.addView(Button(context).apply {
            text = context.getString(R.string.edge_save)
            setOnClickListener {
                runCatching {
                    val values = item?.fields.orEmpty().toMutableMap()
                    values["type"] = types[type.selectedItemPosition]
                    entries.forEach { (key, entry) ->
                        // Preserve hidden values; only visible fields are edited.
                        if (groups.getValue(key).visibility == View.VISIBLE) {
                            val value = entry.text.toString()
                            if (value.isEmpty()) values.remove(key) else values[key] = value
                        }
                    }
                    if (appCommand != null && AppLaunchCommand.packageFrom(values["run"].orEmpty()) != null) {
                        values["run"] = AppLaunchCommand.withMode(values.getValue("run"), launchMode)
                    }
                    EdgeStore.validateItem(values)
                    session.leave(except = outer) {
                        runCatching {
                            beforeSave()
                            val id = item?.id ?: "item_" + UUID.randomUUID().toString().replace("-", "")
                            store.saveItemDraft("$panelId:$id", values, item?.fields)
                            saved()
                        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        })
        draft.addView(Button(context).apply {
            text = context.getString(android.R.string.cancel)
            setOnClickListener {
                session.discard(outer) {
                    entries.forEach { (key, entry) -> entry.setText(item?.fields?.get(key).orEmpty()) }
                    type.setSelection(types.indexOf(item?.type ?: "run"))
                    if (cancelled != null) cancelled() else draft.visibility = View.GONE
                }
            }
        })
        if (onDelete != null) {
            val confirm = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            draft.addView(EdgeEditorUi.button(context, context.getString(R.string.edge_delete)) { confirm.visibility = View.VISIBLE })
            confirm.addView(EdgeEditorUi.label(context, context.getString(R.string.edge_delete_item_warning, item?.fields?.get("label") ?: item?.id.orEmpty())))
            confirm.addView(EdgeEditorUi.button(context, context.getString(R.string.edge_delete)) {
                session.leave(except = outer) {
                    runCatching(onDelete).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                }
            })
            confirm.addView(EdgeEditorUi.button(context, context.getString(android.R.string.cancel)) { confirm.visibility = View.GONE })
            draft.addView(confirm)
        }
        return outer
    }
}
