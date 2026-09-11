package com.zerotoship.z2term.edge

import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R

/** The draft is local to this view; only Save writes definitions. */
object EdgeAppearanceEditor {
    fun create(context: Context, panel: EdgeStore.Panel, store: EdgeStore, screenWidth: Int, screenHeight: Int,
        preview: (Map<String, String>) -> Unit, finish: () -> Unit, session: EdgeEditorSession): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(EdgeSettingsUi.canvas(context))
        }
        val diagram = EdgePanelPreview(context, panel.fields, screenWidth, screenHeight)
        // The diagram and its rule appear and disappear together; the IME leaves no orphan hairline.
        val stage = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(EdgeSettingsUi.surface(context))
            addView(diagram, LinearLayout.LayoutParams(-1, minOf(EdgeEditorUi.dp(context, 140), screenHeight / 5)))
            addView(EdgeSettingsUi.hairline(context))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { _, insets ->
            stage.visibility = if (insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) View.GONE else View.VISIBLE
            insets
        }
        outer.addView(stage)
        val sections = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(ScrollView(context).apply { addView(sections) }, LinearLayout.LayoutParams(-1, 0, 1f))
        var content = sections
        val entries = linkedMapOf<String, EditText>()
        val defaults = mapOf("size" to if (panel.handle == "button") "48" else "6",
            "length" to "6", "alpha" to "1", "width" to "360", "height" to "72%",
            "title" to "off", "close" to "off", "tabbar" to "off", "add" to "off", "settings" to "off",
            "labels" to "", "fit" to "content", "place" to "handle", "at" to "", "flow" to "",
            "columns" to "auto", "icon-size" to "40", "handle" to "off", "bar-color" to "auto", "side" to "right", "open" to "",
            "offset" to "30", "x" to "85", "y" to "30",
            "gesture-up" to "", "gesture-down" to "", "gesture-double-tap" to "",
            "gesture-scroll" to "off", "gesture-speed" to "600", "gesture-range" to "160", "scroll-x" to "50", "scroll-y" to "50").toMutableMap()
        val save = EdgeSettingsUi.button(context, context.getString(R.string.edge_save), EdgeSettingsUi.Kind.PRIMARY) {}
        fun values(): Map<String, String> = entries.mapValues { it.value.text.toString().trim() }
        fun update() {
            val draft = values()
            val valid = runCatching {
                EdgeStore.validatePanel(panel.fields + draft)
            }.isSuccess
            save.isEnabled = valid
            if (valid) runCatching { diagram.update(panel.fields + draft); preview(draft) }.onFailure {
                save.isEnabled = false
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        }
        fun control(key: String, label: Int, low: Int, high: Int, convert: (Int) -> String) {
            val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(TextView(context).apply {
                text = context.getString(label); textSize = 14f
                setTextColor(EdgeSettingsUi.foreground(context))
                setPadding(0, 0, EdgeEditorUi.dp(context, 12), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // The number is the value; the slider only reaches it faster. Keep them on one baseline.
            val entry = EdgeSettingsUi.field(context).apply {
                setText(panel.fields[key] ?: defaults.getValue(key))
                gravity = Gravity.CENTER
                contentDescription = context.getString(label)
            }
            entries[key] = entry
            line.addView(entry, LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 88), -2))
            group.addView(line)
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
            val slider = EdgeSettingsUi.dress(context, SeekBar(context).apply {
                min = low; max = high
                progress = sliderValue(entry.text.toString())
                contentDescription = context.getString(label)
            })
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
            group.addView(slider)
            content.addView(group, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = EdgeEditorUi.dp(context, 10)
            })
        }
        fun entry(key: String, label: Int) {
            val input = EdgeSettingsUi.field(context).apply {
                setText(panel.fields[key] ?: defaults.getValue(key))
                contentDescription = context.getString(label)
            }
            entries[key] = input
            EdgeSettingsUi.labeled(context, content, context.getString(label), input)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { update() }
            })
        }
        fun choice(key: String, label: Int, options: List<String>, names: List<Int>) {
            if (options == listOf("off", "on")) {
                val input = EditText(context).apply { setText(panel.fields[key] ?: defaults.getValue(key)) }
                entries[key] = input
                content.addView(EdgeSettingsUi.toggleRow(context, context.getString(label),
                    input.text.toString() == "on") { checked ->
                    input.setText(if (checked) "on" else "off"); update()
                }, LinearLayout.LayoutParams(-1, -2))
                return
            }
            val input = EditText(context).apply { setText(panel.fields[key] ?: defaults.getValue(key)) }
            entries[key] = input
            val picker = EdgeSettingsUi.dress(context, android.widget.Spinner(context))
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
            EdgeSettingsUi.labeled(context, content, context.getString(label), picker)
        }
        fun help(message: Int) {
            content.addView(EdgeSettingsUi.note(context, context.getString(message)),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = EdgeEditorUi.dp(context, 4); bottomMargin = EdgeEditorUi.dp(context, 14)
                })
        }
        val onOff = listOf(R.string.edge_option_off, R.string.edge_option_on)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_controls))
        choice("title", R.string.edge_show_title, listOf("off", "on"), onOff)
        choice("close", R.string.edge_show_close, listOf("off", "on"), onOff)
        choice("tabbar", R.string.edge_show_tabs, listOf("off", "on", "auto"), onOff + R.string.edge_option_auto)
        choice("add", R.string.edge_show_add, listOf("off", "on"), onOff)
        choice("settings", R.string.edge_show_settings, listOf("off", "on"), onOff)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_size), expanded = true)
        control("width", R.string.edge_adjust_width, 1, 100) { "$it%" }
        control("height", R.string.edge_adjust_height, 1, 100) { "$it%" }
        choice("fit", R.string.edge_fit, listOf("content", "fixed"), listOf(R.string.edge_fit_content, R.string.edge_fit_fixed))
        choice("place", R.string.edge_place, listOf("handle", "left", "right", "top", "bottom", "center"),
            listOf(R.string.edge_place_handle, R.string.edge_place_left, R.string.edge_place_right, R.string.edge_place_top, R.string.edge_place_bottom, R.string.edge_place_center))
        entry("at", R.string.edge_at)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_items))
        choice("labels", R.string.edge_show_labels, listOf("", "on", "off"), listOf(R.string.edge_option_auto, R.string.edge_option_on, R.string.edge_option_off))
        choice("flow", R.string.edge_flow, listOf("", "vertical", "horizontal", "grid"),
            listOf(R.string.edge_option_auto, R.string.edge_flow_vertical, R.string.edge_flow_horizontal, R.string.edge_flow_grid))
        entry("columns", R.string.edge_columns)
        control("icon-size", R.string.edge_icon_size, 16, 192) { it.toString() }
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_handle))
        choice("handle", R.string.edge_handle_kind, listOf("off", "bar", "button"),
            listOf(R.string.edge_option_off, R.string.edge_handle_bar, R.string.edge_handle_button))
        choice("side", R.string.edge_handle_side, listOf("left", "right"), listOf(R.string.edge_place_left, R.string.edge_place_right))
        choice("bar-color", R.string.edge_bar_color, listOf("auto", "white", "black"),
            listOf(R.string.edge_option_auto, R.string.theme_color_white, R.string.theme_color_black))
        help(R.string.edge_bar_color_help)
        control("size", R.string.edge_adjust_size, 2, 96) { it.toString() }
        control("length", R.string.edge_adjust_length, 1, 100) { it.toString() }
        control("alpha", R.string.edge_adjust_alpha, 5, 100) { (it / 100f).toString() }
        control("offset", R.string.edge_handle_offset, 0, 100) { it.toString() }
        control("x", R.string.edge_handle_x, 0, 100) { it.toString() }
        control("y", R.string.edge_handle_y, 0, 100) { it.toString() }
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_gestures))
        help(R.string.edge_gestures_help)
        val gestures = content
        EdgeActions.Trigger.entries.forEach { trigger ->
            val initial = EdgeActions.binding(panel.fields, trigger)
            val encoded = EdgeActions.encode(initial)
            defaults[trigger.key] = encoded
            val value = EditText(context).apply { setText(encoded) }
            entries[trigger.key] = value
            val label = when (trigger) {
                EdgeActions.Trigger.TAP -> R.string.edge_trigger_tap
                EdgeActions.Trigger.DOUBLE_TAP -> R.string.edge_trigger_double_tap
                EdgeActions.Trigger.UP -> R.string.edge_trigger_up
                EdgeActions.Trigger.DOWN -> R.string.edge_trigger_down
                EdgeActions.Trigger.INWARD -> R.string.edge_trigger_inward
                EdgeActions.Trigger.OUTWARD -> R.string.edge_trigger_outward
            }
            val section = EdgeSettingsUi.section(context, gestures, context.getString(label), sub = true)
            section.addView(EdgeActionEditor.create(context, initial) { draft ->
                value.setText(draft); update()
            })
        }
        content = gestures
        content.addView(EdgeSettingsUi.spacer(context, 6))
        control("gesture-speed", R.string.edge_scroll_speed, 50, 40000) { it.toString() }
        control("gesture-range", R.string.edge_scroll_range, 32, 2000) { it.toString() }
        help(R.string.edge_scroll_range_help)
        control("scroll-x", R.string.edge_scroll_x, 10, 90) { it.toString() }
        control("scroll-y", R.string.edge_scroll_y, 10, 90) { it.toString() }
        content.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_accessibility_setup)) {
            runCatching { AndroidActions.command(context, listOf("permission")) }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(-1, -2))
        sections.addView(EdgeSettingsUi.hairline(context))
        sections.addView(EdgeSettingsUi.note(context, context.getString(R.string.edge_preview_help)),
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), EdgeEditorUi.dp(context, 14),
                    EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), EdgeEditorUi.dp(context, 16))
            })
        session.track(outer) {
            values().any { (key, value) -> value != (panel.fields[key] ?: defaults[key]) }
        }
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
        outer.addView(EdgeSettingsUi.hairline(context))
        outer.addView(LinearLayout(context).apply {
            setPadding(EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), EdgeEditorUi.dp(context, 10),
                EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), EdgeEditorUi.dp(context, 12))
            addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
                session.discard(outer, finish)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(View(context), LinearLayout.LayoutParams(EdgeEditorUi.dp(context, 10), 1))
            addView(save, LinearLayout.LayoutParams(0, -2, 1.4f))
        })
        return outer
    }
}
