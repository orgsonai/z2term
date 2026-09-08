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
        beforeSave: () -> Unit, saved: () -> Unit): View {
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val draft = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        outer.addView(Button(context).apply {
            text = context.getString(if (item == null) R.string.edge_add_item else R.string.edge_edit_item)
            setOnClickListener { draft.visibility = View.VISIBLE }
        })
        outer.addView(draft)
        val types = listOf("run", "text", "toggle", "list", "input", "note")
        val type = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.edge_type_run, R.string.edge_type_text, R.string.edge_type_toggle,
                    R.string.edge_type_list, R.string.edge_type_input, R.string.edge_note).map { context.getString(it) })
            contentDescription = context.getString(R.string.edge_item_type)
            setSelection(types.indexOf(item?.type ?: "run"))
        }
        draft.addView(type)
        val entries = linkedMapOf<String, EditText>()
        val groups = linkedMapOf<String, LinearLayout>()
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
            group.addView(entry); draft.addView(group)
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
                    EdgeStore.validateItem(values)
                    beforeSave()
                    val id = item?.id ?: "item_" + UUID.randomUUID().toString().replace("-", "")
                    store.saveItemDraft("$panelId:$id", values, item?.fields)
                    saved()
                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        })
        draft.addView(Button(context).apply {
            text = context.getString(android.R.string.cancel)
            setOnClickListener {
                entries.forEach { (key, entry) -> entry.setText(item?.fields?.get(key).orEmpty()) }
                type.setSelection(types.indexOf(item?.type ?: "run"))
                draft.visibility = View.GONE
            }
        })
        return outer
    }
}
