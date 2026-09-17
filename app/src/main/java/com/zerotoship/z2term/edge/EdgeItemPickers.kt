package com.zerotoship.z2term.edge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.IconStore
import com.zerotoship.z2term.widget.WidgetStore

/** Pick into the draft; selecting a value never saves or executes it. */
internal object EdgeItemPickers {
    fun macros(context: Context, group: LinearLayout, entry: EditText) {
        val commands = linkedMapOf<String, String>()
        WidgetStore.availableMacros(context).forEach { commands[it] = it }
        com.zerotoship.z2term.automation.ActionRuntime.store(context).names().forEach { name ->
            commands["${context.getString(R.string.action_macro_title)}: $name"] = "z2-action start $name"
        }
        picker(context, group, entry, R.string.edge_pick_macro, commands.keys.toList()) { commands.getValue(it) }
    }

    fun bindings(context: Context, group: LinearLayout, entry: EditText,
        items: List<EdgeStore.Item>, arguments: Boolean, multiple: Boolean = arguments) {
        val candidates = items.filter { if (arguments) EdgeItemComponent.source(it) else it.type == "result" }
        val labels = candidates.map { "${it.fields["label"] ?: it.id} (${it.id})" }
        picker(context, group, entry, if (arguments) R.string.edge_pick_argument else R.string.edge_pick_result,
            labels) { label ->
            val id = candidates[labels.indexOf(label)].id
            if (multiple && entry.text.isNotBlank()) "${entry.text},$id" else id
        }
    }

    fun options(context: Context, group: LinearLayout, entry: EditText, values: List<String>, labels: List<Int>) {
        picker(context, group, entry, R.string.edge_choose_value, labels.map { context.getString(it) }) {
            values[labels.indexOfFirst { label -> context.getString(label) == it }]
        }
    }

    fun icons(context: Context, group: LinearLayout, entry: EditText) {
        val names = IconStore.allSampleNames(context).map { it.first }
        picker(context, group, entry, R.string.edge_pick_icon, names, icons = true) { "@z2:$it" }
    }

    private fun picker(context: Context, group: LinearLayout, entry: EditText, label: Int,
        names: List<String>, icons: Boolean = false, value: (String) -> String) {
        val labels = listOf(context.getString(label)) + names
        val choicesAdapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, labels) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                decorate(super.getDropDownView(position, convertView, parent), position)

            private fun decorate(view: View, position: Int): View {
                val text = view as? TextView ?: return view
                val drawable = if (icons && position > 0) runCatching {
                    val mask = IconStore.parse(IconStore.findSample(context, names[position - 1])!!)
                    val size = IconStore.gridOf(mask)
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    mask.forEachIndexed { i, on -> if (on) bitmap.setPixel(i % size, i / size, text.currentTextColor) }
                    BitmapDrawable(context.resources, bitmap).apply {
                        isFilterBitmap = false
                        val side = EdgeEditorUi.dp(context, 28)
                        setBounds(0, 0, side, side)
                    }
                }.getOrNull() else null
                text.setCompoundDrawablesRelative(drawable, null, null, null)
                text.compoundDrawablePadding = EdgeEditorUi.dp(context, 8)
                return text
            }
        }
        group.addView(EdgeSettingsUi.dress(context, Spinner(context)).apply {
            adapter = choicesAdapter
            contentDescription = context.getString(label)
            isEnabled = names.isNotEmpty()
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position > 0) {
                        entry.setText(value(names[position - 1]))
                        setSelection(0)
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = EdgeEditorUi.dp(context, 6) })
    }
}
