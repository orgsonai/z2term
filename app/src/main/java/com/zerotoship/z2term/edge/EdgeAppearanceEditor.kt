package com.zerotoship.z2term.edge

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R

/** The draft is local to this view; only Save writes definitions. */
object EdgeAppearanceEditor {
    fun create(context: Context, panel: EdgeStore.Panel, store: EdgeStore, screenWidth: Int, screenHeight: Int,
        preview: (Map<String, String>) -> Unit, finish: () -> Unit): View {
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        outer.addView(Button(context).apply {
            text = context.getString(R.string.edge_adjust)
            setOnClickListener { content.visibility = View.VISIBLE }
        })
        outer.addView(content)
        val entries = linkedMapOf<String, EditText>()
        val defaults = mapOf("size" to if (panel.handle == "button") "48" else "6",
            "length" to "6", "alpha" to "1", "width" to "360", "height" to "72%",
            "title" to "off", "close" to "off", "tabbar" to "off", "add" to "off", "settings" to "off",
            "labels" to "", "fit" to "content", "place" to "handle", "at" to "", "flow" to "",
            "columns" to "auto", "icon-size" to "40", "handle" to "off", "side" to "right", "open" to "",
            "offset" to "30", "x" to "85", "y" to "30")
        val save = Button(context).apply { text = context.getString(R.string.edge_save) }
        fun values(): Map<String, String> = entries.mapValues { it.value.text.toString().trim() }
        fun update() {
            val draft = values()
            val valid = runCatching {
                EdgeStore.validatePanel(panel.fields + draft)
            }.isSuccess
            save.isEnabled = valid
            if (valid) runCatching { preview(draft) }.onFailure {
                save.isEnabled = false
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        }
        fun control(key: String, label: Int, low: Int, high: Int, convert: (Int) -> String) {
            content.addView(TextView(context).apply { text = context.getString(label) })
            val entry = EditText(context).apply {
                setSingleLine(true); setText(panel.fields[key] ?: defaults.getValue(key))
                contentDescription = context.getString(label)
            }
            entries[key] = entry
            content.addView(entry)
            fun sliderValue(raw: String): Int {
                val number = raw.removeSuffix("%").toFloatOrNull() ?: return low
                if (!number.isFinite()) return low
                val value = when {
                    key == "alpha" -> number * 100
                    key in listOf("width", "height") && !raw.endsWith("%") ->
                        number * context.resources.displayMetrics.density * 100 /
                            (if (key == "width") screenWidth else screenHeight).coerceAtLeast(1)
                    else -> number
                }
                return value.toInt().coerceIn(low, high)
            }
            val slider = SeekBar(context).apply {
                min = low; max = high
                progress = sliderValue(entry.text.toString())
                contentDescription = context.getString(label)
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) entry.setText(convert(progress))
                }
            })
            entry.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    slider.progress = sliderValue(s.toString().trim())
                    update()
                }
            })
            content.addView(slider)
        }
        fun entry(key: String, label: Int) {
            content.addView(TextView(context).apply { text = context.getString(label) })
            val input = EditText(context).apply {
                setSingleLine(true); setText(panel.fields[key] ?: defaults.getValue(key))
                contentDescription = context.getString(label)
            }
            entries[key] = input
            content.addView(input)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { update() }
            })
        }
        fun choice(key: String, label: Int, options: List<String>, names: List<Int>) {
            content.addView(TextView(context).apply { text = context.getString(label) })
            val input = EditText(context).apply { setText(panel.fields[key] ?: defaults.getValue(key)) }
            entries[key] = input
            val picker = android.widget.Spinner(context)
            picker.contentDescription = context.getString(label)
            picker.adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                names.map { context.getString(it) })
            picker.setSelection(options.indexOf(input.text.toString()).coerceAtLeast(0))
            picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (input.text.toString() != options[position]) { input.setText(options[position]); update() }
                }
            }
            content.addView(picker)
        }
        val onOff = listOf(R.string.edge_option_off, R.string.edge_option_on)
        choice("title", R.string.edge_show_title, listOf("off", "on"), onOff)
        choice("close", R.string.edge_show_close, listOf("off", "on"), onOff)
        choice("tabbar", R.string.edge_show_tabs, listOf("off", "on", "auto"), onOff + R.string.edge_option_auto)
        choice("add", R.string.edge_show_add, listOf("off", "on"), onOff)
        choice("settings", R.string.edge_show_settings, listOf("off", "on"), onOff)
        choice("labels", R.string.edge_show_labels, listOf("", "on", "off"), listOf(R.string.edge_option_auto, R.string.edge_option_on, R.string.edge_option_off))
        choice("fit", R.string.edge_fit, listOf("content", "fixed"), listOf(R.string.edge_fit_content, R.string.edge_fit_fixed))
        choice("place", R.string.edge_place, listOf("handle", "left", "right", "top", "bottom", "center"),
            listOf(R.string.edge_place_handle, R.string.edge_place_left, R.string.edge_place_right, R.string.edge_place_top, R.string.edge_place_bottom, R.string.edge_place_center))
        entry("at", R.string.edge_at)
        choice("flow", R.string.edge_flow, listOf("", "vertical", "horizontal", "grid"),
            listOf(R.string.edge_option_auto, R.string.edge_flow_vertical, R.string.edge_flow_horizontal, R.string.edge_flow_grid))
        entry("columns", R.string.edge_columns)
        control("icon-size", R.string.edge_icon_size, 16, 192) { it.toString() }
        choice("handle", R.string.edge_handle_kind, listOf("off", "bar", "button"),
            listOf(R.string.edge_option_off, R.string.edge_handle_bar, R.string.edge_handle_button))
        choice("side", R.string.edge_handle_side, listOf("left", "right"), listOf(R.string.edge_place_left, R.string.edge_place_right))
        choice("open", R.string.edge_handle_open, listOf("", "tap", "swipe", "both"),
            listOf(R.string.edge_option_auto, R.string.edge_open_tap, R.string.edge_open_swipe, R.string.edge_open_both))
        control("size", R.string.edge_adjust_size, 2, 96) { it.toString() }
        control("length", R.string.edge_adjust_length, 1, 100) { it.toString() }
        control("alpha", R.string.edge_adjust_alpha, 5, 100) { (it / 100f).toString() }
        control("offset", R.string.edge_handle_offset, 0, 100) { it.toString() }
        control("x", R.string.edge_handle_x, 0, 100) { it.toString() }
        control("y", R.string.edge_handle_y, 0, 100) { it.toString() }
        control("width", R.string.edge_adjust_width, 1, 100) { "$it%" }
        control("height", R.string.edge_adjust_height, 1, 100) { "$it%" }
        content.addView(TextView(context).apply { text = context.getString(R.string.edge_preview_help) })
        save.setOnClickListener {
            runCatching {
                val draft = values()
                EdgeStore.validatePanel(panel.fields + draft)
                val changed = draft.filter { (key, value) -> value != (panel.fields[key] ?: defaults[key]) }
                val current = store.panel(panel.id)
                check(changed.keys.all { current.fields[it] == panel.fields[it] }) {
                    context.getString(R.string.edge_edit_conflict)
                }
                store.setPanel(panel.id, changed)
                finish()
            }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
        content.addView(save)
        content.addView(Button(context).apply {
            text = context.getString(android.R.string.cancel); setOnClickListener { finish() }
        })
        return outer
    }
}
