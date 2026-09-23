package com.zerotoship.z2term.edge

import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R

/**
 * The draft is local to this view; only Save writes definitions. The Panel page and the Actions
 * page are two halves of the same editor: [gestures] picks which fields are built and saved.
 */
object EdgeAppearanceEditor {
    fun create(context: Context, panel: EdgeStore.Panel, store: EdgeStore, screenWidth: Int, screenHeight: Int,
        gestures: Boolean = false,
        preview: (Map<String, String>) -> Unit, finish: () -> Unit, session: EdgeEditorSession): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(EdgeSettingsUi.canvas(context))
        }
        val diagram = EdgePanelPreview(context, panel.fields, screenWidth, screenHeight)
        val sections = EdgeSettingsUi.column(context)
        outer.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(sections)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        var content = sections
        val entries = linkedMapOf<String, EditText>()
        val fields = linkedMapOf<String, View>()
        val labels = linkedMapOf<String, Int>()
        val inputs = linkedMapOf<String, EditText>()
        var refreshVisibility: () -> Unit = {}
        val status = EdgeSettingsUi.caption(context, "").apply {
            setTextColor(EdgeSettingsUi.danger(context))
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val defaults = mapOf("size" to if (panel.handle == "button") "48" else "6",
            "length" to "6", "alpha" to "1", "width" to "360", "height" to "72%",
            "title" to "off", "close" to "off", "tabbar" to "off", "add" to "off", "settings" to "off",
            "labels" to "", "fit" to "content", "place" to "handle", "at" to "", "flow" to "",
            "tools-place" to "",
            "columns" to "auto", "icon-size" to "40", "handle" to "off", "bar-color" to "auto", "side" to "right", "open" to "",
            "offset" to "30", "x" to "85", "y" to "30",
            "gesture-up" to "", "gesture-down" to "", "gesture-double-tap" to "",
            "gesture-scroll" to "off", "gesture-speed" to "600", "gesture-range" to "160", "scroll-x" to "50", "scroll-y" to "50",
            "scroll-how" to "auto").toMutableMap()
        val save = EdgeSettingsUi.button(context, context.getString(R.string.edge_save), EdgeSettingsUi.Kind.PRIMARY) {}
        fun values(): Map<String, String> = entries.mapValues { it.value.text.toString().trim() }
        fun update() {
            refreshVisibility()
            val draft = values()
            val invalid = draft.entries.firstOrNull { (key, value) ->
                runCatching { EdgeStore.validatePanel(mapOf(key to value)) }.isFailure
            }
            inputs.forEach { (key, input) ->
                val invalidInput = runCatching { EdgeStore.validatePanel(mapOf(key to draft.getValue(key))) }.isFailure
                input.error = if (invalidInput) context.getString(R.string.edge_value_invalid) else null
            }
            val validation = runCatching { EdgeStore.validatePanel(panel.fields + draft) }
            save.isEnabled = validation.isSuccess
            status.visibility = if (validation.isSuccess) View.GONE else View.VISIBLE
            status.text = invalid?.key?.let { key ->
                context.getString(R.string.edge_field_invalid, labels[key]?.let(context::getString) ?: key)
            } ?: validation.exceptionOrNull()?.message.orEmpty()
            if (validation.isSuccess) runCatching {
                diagram.update(panel.fields + draft); preview(draft)
            }.onFailure {
                save.isEnabled = false
                status.text = it.message
                status.visibility = View.VISIBLE
            }
        }
        fun control(key: String, label: Int, low: Int, high: Int) {
            val field = EdgeAppearanceValue(context, key, label,
                panel.fields[key] ?: defaults.getValue(key), low, high,
                if (key == "width") screenWidth else screenHeight, ::update)
            entries[key] = field.value
            inputs[key] = field.input
            fields[key] = field.view
            labels[key] = label
            content.addView(field.view, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = EdgeEditorUi.dp(context, 10)
            })
        }
        fun entry(key: String, label: Int) {
            val input = EdgeSettingsUi.field(context).apply {
                setText(panel.fields[key] ?: defaults.getValue(key))
                contentDescription = context.getString(label)
            }
            entries[key] = input
            labels[key] = label
            inputs[key] = input
            val group = EdgeSettingsUi.column(context)
            fields[key] = group
            EdgeSettingsUi.labeled(context, group, context.getString(label), input)
            content.addView(group)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { update() }
            })
        }
        fun choice(key: String, label: Int, options: List<String>, names: List<Int>) {
            labels[key] = label
            if (options == listOf("off", "on")) {
                val input = EditText(context).apply { setText(panel.fields[key] ?: defaults.getValue(key)) }
                entries[key] = input
                val toggle = EdgeSettingsUi.toggleRow(context, context.getString(label),
                    input.text.toString() == "on") { checked ->
                    input.setText(if (checked) "on" else "off"); update()
                }
                fields[key] = toggle
                content.addView(toggle, LinearLayout.LayoutParams(-1, -2))
                return
            }
            val input = EditText(context).apply { setText(panel.fields[key] ?: defaults.getValue(key)) }
            entries[key] = input
            val picker = EdgeSettingsUi.dress(context, android.widget.Spinner(context))
            picker.contentDescription = context.getString(label)
            picker.adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                names.mapIndexed { index, name ->
                    if (key == "columns" && index > 0) context.getString(name, index) else context.getString(name)
                })
            picker.setSelection(options.indexOf(input.text.toString()).coerceAtLeast(0))
            picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (input.text.toString() != options[position]) { input.setText(options[position]); update() }
                }
            }
            val group = EdgeSettingsUi.column(context)
            fields[key] = group
            EdgeSettingsUi.labeled(context, group, context.getString(label), picker)
            content.addView(group)
        }
        fun help(message: Int) {
            content.addView(EdgeSettingsUi.note(context, context.getString(message)),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = EdgeEditorUi.dp(context, 4); bottomMargin = EdgeEditorUi.dp(context, 14)
                })
        }
        val onOff = listOf(R.string.edge_option_off, R.string.edge_option_on)
        var barExplanation: View = content
        if (!gestures) {
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_size), expanded = true)
        help(R.string.edge_appearance_intro)
        content.addView(diagram, LinearLayout.LayoutParams(-1, EdgeEditorUi.dp(context, 160)))
        control("width", R.string.edge_panel_width, 1, 100)
        control("height", R.string.edge_panel_height, 1, 100)
        choice("fit", R.string.edge_fit, listOf("content", "fixed"), listOf(R.string.edge_fit_content, R.string.edge_fit_fixed))
        choice("place", R.string.edge_place, listOf("handle", "left", "right", "top", "bottom", "center"),
            listOf(R.string.edge_place_handle, R.string.edge_place_left, R.string.edge_place_right, R.string.edge_place_top, R.string.edge_place_bottom, R.string.edge_place_center))
        val placement = content
        content = EdgeSettingsUi.fold(context, placement, context.getString(R.string.edge_position_advanced))
        help(R.string.edge_position_help)
        entry("at", R.string.edge_at)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_items))
        choice("labels", R.string.edge_show_labels, listOf("", "on", "off"), listOf(R.string.edge_option_auto, R.string.edge_option_on, R.string.edge_option_off))
        control("icon-size", R.string.edge_icon_size, 16, 192)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_handle))
        choice("handle", R.string.edge_handle_kind, listOf("off", "bar", "button"),
            listOf(R.string.edge_option_off, R.string.edge_handle_bar, R.string.edge_handle_button))
        choice("side", R.string.edge_handle_side, listOf("left", "right"), listOf(R.string.edge_place_left, R.string.edge_place_right))
        choice("bar-color", R.string.edge_bar_color, listOf("auto", "white", "black"),
            listOf(R.string.edge_option_auto, R.string.theme_color_white, R.string.theme_color_black))
        val barHelp = content
        content = EdgeSettingsUi.column(context)
        barExplanation = content
        barHelp.addView(content)
        help(R.string.edge_bar_color_help)
        content = barHelp
        control("size", R.string.edge_adjust_size, 2, 96)
        control("length", R.string.edge_adjust_length, 1, 100)
        control("alpha", R.string.edge_handle_opacity, 5, 100)
        control("offset", R.string.edge_handle_offset, 0, 100)
        control("x", R.string.edge_handle_x, 0, 100)
        control("y", R.string.edge_handle_y, 0, 100)
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_controls))
        choice("title", R.string.edge_show_title, listOf("off", "on"), onOff)
        choice("close", R.string.edge_show_close, listOf("off", "on"), onOff)
        choice("tabbar", R.string.edge_show_tabs, listOf("off", "on", "auto"), onOff + R.string.edge_option_auto)
        choice("add", R.string.edge_show_add, listOf("off", "on"), onOff)
        choice("settings", R.string.edge_show_settings, listOf("off", "on"), onOff)
        choice("tools-place", R.string.edge_tools_place, listOf("", "top", "bottom"),
            listOf(R.string.edge_option_auto, R.string.edge_place_top, R.string.edge_place_bottom))
        } else {
        content = EdgeSettingsUi.section(context, sections, context.getString(R.string.edge_section_gestures), expanded = true)
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
            labels[trigger.key] = label
            val section = EdgeSettingsUi.section(context, gestures, context.getString(label), sub = true)
            section.addView(EdgeActionEditor.create(context, initial) { draft ->
                value.setText(draft); update()
            })
        }
        content = gestures
        content.addView(EdgeSettingsUi.spacer(context, 6))
        choice("scroll-how", R.string.edge_scroll_how, listOf("auto", "node", "swipe"),
            listOf(R.string.edge_option_auto, R.string.edge_scroll_how_node, R.string.edge_scroll_how_swipe))
        help(R.string.edge_scroll_how_help)
        control("gesture-speed", R.string.edge_scroll_speed, 50, 40000)
        control("gesture-range", R.string.edge_scroll_range, 32, 2000)
        help(R.string.edge_scroll_range_help)
        control("scroll-x", R.string.edge_scroll_x, 10, 90)
        control("scroll-y", R.string.edge_scroll_y, 10, 90)
        content.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_accessibility_setup)) {
            runCatching { AndroidActions.command(context, listOf("permission")) }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(-1, -2))
        }
        if (!gestures) refreshVisibility = {
            fun show(key: String, visible: Boolean) {
                fields[key]?.visibility = if (visible) View.VISIBLE else View.GONE
            }
            val handle = entries.getValue("handle").text.toString()
            listOf("side", "bar-color", "length", "offset").forEach { show(it, handle == "bar") }
            listOf("x", "y").forEach { show(it, handle == "button") }
            listOf("size", "alpha").forEach { show(it, handle != "off") }
            barExplanation.visibility = if (handle == "bar") View.VISIBLE else View.GONE
            val sizeLabel = if (handle == "button") R.string.edge_button_diameter else R.string.edge_bar_thickness
            ((fields["size"] as? LinearLayout)?.getChildAt(0) as? TextView)?.text = context.getString(sizeLabel)
            inputs["size"]?.contentDescription = context.getString(sizeLabel)
            labels["size"] = sizeLabel
            val tools = entries.getValue("add").text.toString() == "on" || entries.getValue("settings").text.toString() == "on"
            show("tools-place", tools)
            // Explicit coordinates take precedence over the placement picker; retain that choice.
            show("place", entries.getValue("at").text.isBlank())
        }
        sections.addView(EdgeSettingsUi.hairline(context))
        if (!gestures) sections.addView(EdgeSettingsUi.note(context, context.getString(R.string.edge_preview_help)),
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
        update()
        outer.addView(EdgeSettingsUi.hairline(context))
        outer.addView(status.apply {
            setPadding(EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), EdgeEditorUi.dp(context, 6),
                EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER), 0)
        }, LinearLayout.LayoutParams(-1, -2))
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
