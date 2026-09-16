package com.zerotoship.z2term.edge

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID

/** A local draft; closing or cancelling the editor never updates the definition. */
object EdgeItemEditor {
    fun create(context: Context, panelId: String, item: EdgeStore.Item?, store: EdgeStore,
        beforeSave: () -> Unit, saved: () -> Unit, expanded: Boolean = false,
        cancelled: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
        session: EdgeEditorSession): View {
        val gutter = EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER)
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val draft = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding(gutter, EdgeEditorUi.dp(context, 12), gutter, EdgeEditorUi.dp(context, 14))
        }
        if (!expanded) outer.addView(EdgeSettingsUi.button(context,
            context.getString(if (item == null) R.string.edge_add_item else R.string.edge_edit_item)) {
            draft.visibility = if (draft.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(gutter, EdgeEditorUi.dp(context, 4), gutter, EdgeEditorUi.dp(context, 10))
        })
        outer.addView(draft)
        val appCommand = item?.command?.takeIf { AppLaunchCommand.packageFrom(it) != null }
        var launchMode = AppLaunchCommand.modeFrom(appCommand.orEmpty())
        var scaleFreeform = AppLaunchCommand.scalesFreeform(appCommand.orEmpty())
        val types = EdgeSettingsUi.itemTypes
        val type = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                types.map { context.getString(EdgeSettingsUi.typeLabel(it)) })
            contentDescription = context.getString(R.string.edge_item_type)
            setSelection(types.indexOf(item?.type ?: "run"))
        }
        if (appCommand == null) {
            EdgeSettingsUi.labeled(context, draft, context.getString(R.string.edge_item_type), type)
        }
        if (appCommand != null) {
            EdgeSettingsUi.labeled(context, draft, context.getString(R.string.edge_window_mode),
                EdgeSettingsUi.dress(context, Spinner(context)).apply {
                    contentDescription = context.getString(R.string.edge_window_mode)
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                        listOf(R.string.edge_window_full, R.string.edge_window_freeform, R.string.edge_window_split, R.string.edge_window_ask).map { context.getString(it) })
                    setSelection(AppLaunch.modes.indexOf(launchMode))
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { launchMode = AppLaunch.modes[position] }
                    }
                })
            EdgeSettingsUi.labeled(context, draft, context.getString(R.string.edge_freeform_scale),
                EdgeSettingsUi.dress(context, Spinner(context)).apply {
                    contentDescription = context.getString(R.string.edge_freeform_scale)
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                        listOf(R.string.edge_freeform_scaled, R.string.edge_freeform_unscaled).map { context.getString(it) })
                    setSelection(if (scaleFreeform) 0 else 1)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            scaleFreeform = position == 0
                        }
                    }
                })
        }
        val entries = linkedMapOf<String, EditText>()
        val groups = linkedMapOf<String, LinearLayout>()
        val basic = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        draft.addView(basic)
        val buttonState = EdgeSettingsUi.switchOf(context, item?.isStateButton == true).apply {
            text = context.getString(R.string.edge_button_state)
            setTextColor(EdgeSettingsUi.foreground(context))
            minHeight = EdgeEditorUi.dp(context, 48)
        }
        basic.addView(buttonState)
        val buttonStateHelp = EdgeSettingsUi.body(context, context.getString(R.string.edge_button_state_desc))
        basic.addView(buttonStateHelp)
        val sourceGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val source = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.edge_source_auto, R.string.edge_source_torch, R.string.edge_source_screen,
                    R.string.edge_source_process, R.string.edge_source_remember).map { context.getString(it) })
            setSelection(EdgeButtonSource.choices.indexOf(item?.fields?.get("button-source") ?: "auto").coerceAtLeast(0))
            contentDescription = context.getString(R.string.edge_button_source)
        }
        EdgeSettingsUi.labeled(context, sourceGroup, context.getString(R.string.edge_button_source), source)
        basic.addView(sourceGroup)
        val noteLines = EdgeSettingsUi.switchOf(context, item?.fields?.get("note-lines") == "on").apply {
            text = context.getString(R.string.edge_note_lines)
            setTextColor(EdgeSettingsUi.foreground(context))
            minHeight = EdgeEditorUi.dp(context, 48)
        }
        val advanced = EdgeSettingsUi.section(context, draft, context.getString(R.string.edge_advanced), sub = true)
        val fields = linkedMapOf("label" to R.string.edge_item_label, "icon" to R.string.edge_item_icon,
            "run" to R.string.edge_item_run, "off" to R.string.edge_item_off, "state" to R.string.edge_item_state,
            "on-select" to R.string.edge_item_select, "every" to R.string.edge_item_every,
            "timeout" to R.string.edge_item_timeout, "out" to R.string.edge_item_out, "file" to R.string.edge_item_file,
            "note-background" to R.string.edge_note_background, "note-color" to R.string.edge_note_color,
            "args" to R.string.edge_macro_args, "result" to R.string.edge_macro_result,
            "argument-kind" to R.string.edge_argument_kind, "default" to R.string.edge_argument_default,
            "choices" to R.string.edge_argument_choices, "required" to R.string.edge_argument_required,
            "rows" to R.string.edge_form_rows)
        fields.forEach { (key, label) ->
            val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            group.addView(EdgeSettingsUi.caption(context, context.getString(label)))
            val entry = EdgeSettingsUi.field(context).apply {
                setText(item?.fields?.get(key).orEmpty())
                contentDescription = context.getString(label)
            }
            entries[key] = entry; groups[key] = group
            group.addView(entry)
            if (key in setOf("note-background", "note-color")) EdgeColorField.add(context, group, entry, label)
            if (key == "icon") EdgeItemPickers.icons(context, group, entry)
            if (key == "args" || key == "result") EdgeItemPickers.bindings(context, group, entry,
                store.panel(panelId).items, key == "args")
            if (key == "argument-kind") EdgeItemPickers.options(context, group, entry,
                listOf("text", "choice", "fixed"), listOf(R.string.edge_argument_text, R.string.edge_argument_choice,
                    R.string.edge_argument_fixed))
            if (key == "required") EdgeItemPickers.options(context, group, entry, listOf("off", "on"),
                listOf(R.string.edge_argument_optional, R.string.edge_argument_mandatory))
            if (key == "run" || key == "off") EdgeItemPickers.macros(context, group, entry)
            group.addView(EdgeSettingsUi.spacer(context, 12))
            val optional = key in listOf("every", "timeout", "out", "file")
            (if (optional) advanced else basic).addView(group)
        }
        basic.addView(noteLines, LinearLayout.LayoutParams(-1, -2))
        val qr = EdgeSettingsUi.button(context, context.getString(R.string.qr_tools_command_qr)) {
            com.zerotoship.z2term.qr.QrToolsActivity.showCommand(context,
                entries["label"]?.text.toString(), entries["run"]?.text.toString())
        }
        basic.addView(qr)
        val formHelp = EdgeSettingsUi.body(context, context.getString(R.string.edge_macro_help))
        basic.addView(formHelp)
        if (item != null) basic.addView(EdgeSettingsUi.caption(context, "ID: ${item.id}"))
        fun showFields() {
            val selected = types[type.selectedItemPosition]
            formHelp.visibility = if (selected in setOf("macro", "argument", "result")) View.VISIBLE else View.GONE
            buttonState.visibility = if (selected == "run") View.VISIBLE else View.GONE
            val stateButton = selected == "run" && buttonState.isChecked
            qr.visibility = if (selected == "run" && !stateButton && appCommand == null) View.VISIBLE else View.GONE
            buttonStateHelp.visibility = if (stateButton) View.VISIBLE else View.GONE
            sourceGroup.visibility = if (stateButton) View.VISIBLE else View.GONE
            noteLines.visibility = if (selected == "note") View.VISIBLE else View.GONE
            groups.forEach { (key, group) ->
                val visible = when (key) {
                    "state" -> selected == "toggle" || stateButton
                    "off" -> stateButton
                    "on-select" -> selected == "list"
                    "every" -> selected in listOf("text", "toggle", "list") || stateButton
                    "file", "note-background", "note-color" -> selected == "note"
                    "args", "result" -> selected == "macro"
                    "argument-kind", "default", "choices", "required" -> selected == "argument"
                    "rows" -> selected in setOf("argument", "result", "macro")
                    "out" -> selected !in setOf("note", "terminal", "argument", "result", "macro")
                    "run", "timeout" -> selected !in setOf("note", "terminal", "argument", "result")
                    else -> true
                }
                group.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showFields()
        }
        buttonState.setOnCheckedChangeListener { _, _ -> showFields() }
        showFields()
        session.track(outer) {
            types[type.selectedItemPosition] != (item?.type ?: "run") ||
                buttonState.isChecked != (item?.isStateButton == true) ||
                EdgeButtonSource.choices[source.selectedItemPosition] != (item?.fields?.get("button-source") ?: "auto") ||
                launchMode != AppLaunchCommand.modeFrom(appCommand.orEmpty()) ||
                scaleFreeform != AppLaunchCommand.scalesFreeform(appCommand.orEmpty()) ||
                noteLines.isChecked != (item?.fields?.get("note-lines") == "on") ||
                entries.any { (key, entry) -> entry.text.toString() != item?.fields?.get(key).orEmpty() }
        }
        // Cancel and Save close the draft, so they share one line at its foot.
        val commit = EdgeSettingsUi.row(context)
        commit.addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
            session.discard(outer) {
                entries.forEach { (key, entry) -> entry.setText(item?.fields?.get(key).orEmpty()) }
                type.setSelection(types.indexOf(item?.type ?: "run"))
                buttonState.isChecked = item?.isStateButton == true
                source.setSelection(EdgeButtonSource.choices.indexOf(item?.fields?.get("button-source") ?: "auto").coerceAtLeast(0))
                noteLines.isChecked = item?.fields?.get("note-lines") == "on"
                if (cancelled != null) cancelled() else draft.visibility = View.GONE
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        commit.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_save), EdgeSettingsUi.Kind.PRIMARY) {
            runCatching {
                val values = item?.fields.orEmpty().toMutableMap()
                values["type"] = types[type.selectedItemPosition]
                if (values["type"] == "run") values["button-state"] = if (buttonState.isChecked) "on" else "off"
                if (values["type"] == "run" && buttonState.isChecked) {
                    val selected = EdgeButtonSource.choices[source.selectedItemPosition]
                    if (selected == "auto") values.remove("button-source") else values["button-source"] = selected
                }
                if (values["type"] == "note") values["note-lines"] = if (noteLines.isChecked) "on" else "off"
                entries.forEach { (key, entry) ->
                    // Preserve hidden values; only visible fields are edited.
                    if (groups.getValue(key).visibility == View.VISIBLE) {
                        val value = if (key in setOf("note-background", "note-color")) entry.text.toString().trim() else entry.text.toString()
                        if (key == "label") values[key] = value.trim()
                        else if (value.isEmpty()) values.remove(key) else values[key] = value
                    }
                }
                if (appCommand != null && AppLaunchCommand.packageFrom(values["run"].orEmpty()) != null) {
                    val modeCommand = AppLaunchCommand.withMode(values.getValue("run"), launchMode)
                    values["run"] = if (launchMode == "freeform")
                        AppLaunchCommand.withFreeformScale(modeCommand, scaleFreeform) else modeCommand
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
        }, LinearLayout.LayoutParams(0, -2, 1.4f).apply { leftMargin = EdgeEditorUi.dp(context, 10) })
        draft.addView(commit, LinearLayout.LayoutParams(-1, -2))
        if (onDelete != null) {
            val confirm = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; visibility = View.GONE
                background = EdgeSettingsUi.frame(context, stroke = EdgeSettingsUi.danger(context))
                setPadding(EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12),
                    EdgeEditorUi.dp(context, 12), EdgeEditorUi.dp(context, 12))
            }
            draft.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_delete),
                EdgeSettingsUi.Kind.DANGER) { confirm.visibility = View.VISIBLE },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = EdgeEditorUi.dp(context, 14) })
            confirm.addView(EdgeSettingsUi.body(context,
                context.getString(R.string.edge_delete_item_warning, item?.fields?.get("label") ?: item?.id.orEmpty())).apply {
                textSize = 13f
                setLineSpacing(EdgeEditorUi.dp(context, 3).toFloat(), 1f)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 12) })
            val choice = EdgeSettingsUi.row(context)
            choice.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_delete),
                EdgeSettingsUi.Kind.DANGER) {
                session.leave(except = outer) {
                    runCatching(onDelete).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            choice.addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
                confirm.visibility = View.GONE
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = EdgeEditorUi.dp(context, 8) })
            confirm.addView(choice)
            draft.addView(confirm, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = EdgeEditorUi.dp(context, 10)
            })
        }
        return outer
    }
}
