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
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import com.zerotoship.z2term.R
import java.util.UUID

/**
 * A local draft; closing or cancelling the editor never updates the definition.
 * The form is a few named blocks (kind, display, command, value, connections) with the rarely used
 * settings folded at the foot. A block with nothing to show for the chosen kind is hidden whole,
 * so the form only ever lists what the selected component and behaviour actually use.
 */
object EdgeItemEditor {
    fun create(context: Context, panelId: String, item: EdgeStore.Item?, store: EdgeStore,
        beforeSave: () -> Unit, saved: () -> Unit, expanded: Boolean = false,
        cancelled: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
        session: EdgeEditorSession): View {
        val gutter = EdgeEditorUi.dp(context, EdgeSettingsUi.GUTTER)
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val draft = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding(gutter, EdgeEditorUi.dp(context, 8), gutter, EdgeEditorUi.dp(context, 16))
            background = EdgeSettingsUi.attached(context)
        }
        // The button that opens the draft also closes it; its label says which it will do.
        val openLabel = context.getString(if (item == null) R.string.edge_add_item else R.string.edge_edit_item)
        val toggle = if (expanded) null else EdgeSettingsUi.button(context, openLabel) {}.also {
            outer.addView(it, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(gutter, EdgeEditorUi.dp(context, 4), gutter, EdgeEditorUi.dp(context, 10))
            })
        }
        outer.addView(draft)
        val appCommand = item?.command?.takeIf { AppLaunchCommand.packageFrom(it) != null }
        val initialMode = AppLaunchCommand.modeFrom(appCommand.orEmpty())
        val initialScale = AppLaunchCommand.scalesFreeform(appCommand.orEmpty())
        var launchMode = initialMode
        var scaleFreeform = initialScale
        val initial = EdgeItemComponent.from(item)
        var selection = initial

        val blocks = mutableListOf<LinearLayout>()
        fun group(name: Int, rule: Boolean = true) =
            EdgeSettingsUi.group(context, draft, context.getString(name), rule).also { blocks += it }
        fun fold(name: Int) = EdgeSettingsUi.fold(context, draft, context.getString(name)).also { blocks += it }
        val kind = group(if (appCommand == null) R.string.edge_group_kind else R.string.edge_group_launch, rule = false)
        if (appCommand == null) {
            val guide = EdgeSettingsUi.fold(context, kind, context.getString(R.string.edge_help_choose))
            guide.addView(EdgeSettingsUi.note(context, context.getString(R.string.edge_help_components)))
        }
        val display = group(R.string.edge_group_display)
        val commands = group(R.string.edge_group_command)
        val valueBlock = group(R.string.edge_group_value)
        val binding = group(R.string.edge_group_binding)
        val advanced = fold(R.string.edge_advanced)
        val layout = fold(R.string.edge_item_layout)
        fun LinearLayout.addNote(message: Int): View = EdgeSettingsUi.note(context, context.getString(message)).also {
            addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 12) })
        }

        val components = EdgeItemComponent.actions.keys.toList()
        val component = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                components.map { context.getString(EdgeComponentLabels.component(it)) })
            contentDescription = context.getString(R.string.edge_component)
            setSelection(components.indexOf(initial.component))
        }
        val action = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            contentDescription = context.getString(R.string.edge_component_action)
        }
        fun showActions() {
            val choices = EdgeItemComponent.actions.getValue(selection.component)
            action.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                choices.map { context.getString(EdgeComponentLabels.action(it)) })
            action.setSelection(choices.indexOf(selection.action))
        }
        showActions()
        var windowMode: Spinner? = null
        var scale: Spinner? = null
        if (appCommand == null) {
            EdgeSettingsUi.labeled(context, kind, context.getString(R.string.edge_component), component)
            EdgeSettingsUi.labeled(context, kind, context.getString(R.string.edge_component_action), action)
        } else {
            val mode = EdgeSettingsUi.dress(context, Spinner(context)).apply {
                contentDescription = context.getString(R.string.edge_window_mode)
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    listOf(R.string.edge_window_full, R.string.edge_window_freeform, R.string.edge_window_split, R.string.edge_window_ask).map { context.getString(it) })
                setSelection(AppLaunch.modes.indexOf(launchMode))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { launchMode = AppLaunch.modes[position] }
                }
            }
            EdgeSettingsUi.labeled(context, kind, context.getString(R.string.edge_window_mode), mode)
            windowMode = mode
            val scaled = EdgeSettingsUi.dress(context, Spinner(context)).apply {
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
            }
            EdgeSettingsUi.labeled(context, kind, context.getString(R.string.edge_freeform_scale), scaled)
            scale = scaled
        }
        val behaviorHelp = EdgeSettingsUi.note(context, "").apply {
            tag = "edge-behavior-help"
            visibility = if (appCommand == null) View.VISIBLE else View.GONE
        }
        kind.addView(behaviorHelp)

        val labels = mapOf("label" to R.string.edge_item_label, "icon" to R.string.edge_item_icon,
            "run" to R.string.edge_item_run, "off" to R.string.edge_item_off, "state" to R.string.edge_item_state,
            "on-select" to R.string.edge_item_select, "every" to R.string.edge_item_every,
            "timeout" to R.string.edge_item_timeout, "out" to R.string.edge_item_out, "file" to R.string.edge_item_file,
            "note-background" to R.string.edge_note_background, "note-color" to R.string.edge_note_color,
            "args" to R.string.edge_macro_args, "result" to R.string.edge_macro_result,
            "argument-kind" to R.string.edge_argument_kind, "default" to R.string.edge_argument_default,
            "choices" to R.string.edge_argument_choices, "required" to R.string.edge_argument_required,
            "rows" to R.string.edge_form_rows, "stdin" to R.string.edge_item_stdin,
            "width" to R.string.edge_item_width, "height" to R.string.edge_item_height,
            "align" to R.string.edge_item_align, "at" to R.string.edge_item_at)
        val entries = linkedMapOf<String, EditText>()
        val groups = linkedMapOf<String, LinearLayout>()
        val examples = linkedMapOf<String, TextView>()
        val exampleButtons = linkedMapOf<String, Button>()
        fun field(key: String, into: LinearLayout) {
            val label = labels.getValue(key)
            val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            group.addView(EdgeSettingsUi.caption(context, context.getString(label)))
            val entry = EdgeSettingsUi.field(context).apply {
                setText(item?.fields?.get(key).orEmpty())
                contentDescription = context.getString(label)
            }
            entries[key] = entry; groups[key] = group
            group.addView(entry)
            EdgeItemHelp.field(key)?.let { message ->
                group.addView(EdgeSettingsUi.note(context, context.getString(message)))
            }
            val example = EdgeSettingsUi.note(context, "").apply {
                tag = "edge-example:$key"
                setTextIsSelectable(true)
            }
            examples[key] = example
            group.addView(example)
            val useExample = EdgeSettingsUi.button(context, context.getString(R.string.edge_help_use_example),
                EdgeSettingsUi.Kind.QUIET) {
                // Examples only fill an empty draft; they never save or execute commands.
                if (entry.text.isEmpty()) EdgeItemHelp.example(key, selection)?.let { entry.setText(it) }
            }
            exampleButtons[key] = useExample
            group.addView(useExample, LinearLayout.LayoutParams(-2, -2))
            entry.doAfterTextChanged {
                useExample.visibility = if (appCommand == null && entry.text.isEmpty() &&
                    EdgeItemHelp.example(key, selection) != null) View.VISIBLE else View.GONE
            }
            if (key in setOf("note-background", "note-color")) EdgeColorField.add(context, group, entry, label)
            if (key == "icon") EdgeItemPickers.icons(context, group, entry)
            if (key in setOf("args", "stdin", "result")) EdgeItemPickers.bindings(context, group, entry,
                store.panel(panelId).items, key != "result", multiple = key == "args")
            if (key == "align") EdgeItemPickers.options(context, group, entry,
                listOf("start", "center", "end"), listOf(R.string.edge_align_start, R.string.edge_align_center, R.string.edge_align_end))
            if (key == "out") EdgeItemPickers.options(context, group, entry,
                listOf("none", "panel", "toast", "notify"), listOf(R.string.edge_out_none, R.string.edge_out_panel,
                    R.string.edge_out_toast, R.string.edge_out_notify))
            if (key == "required") EdgeItemPickers.options(context, group, entry, listOf("off", "on"),
                listOf(R.string.edge_argument_optional, R.string.edge_argument_mandatory))
            if (key == "run" || key == "off") EdgeItemPickers.macros(context, group, entry)
            group.addView(EdgeSettingsUi.spacer(context, 12))
            into.addView(group)
        }

        // Display: what the item looks like in the panel.
        field("label", display); field("icon", display); field("rows", display)
        field("note-background", display); field("note-color", display)
        val noteLines = EdgeSettingsUi.switchOf(context, item?.fields?.get("note-lines") == "on").apply {
            text = context.getString(R.string.edge_note_lines)
            setTextColor(EdgeSettingsUi.foreground(context))
            minHeight = EdgeEditorUi.dp(context, 48)
        }
        display.addView(noteLines, LinearLayout.LayoutParams(-1, -2))
        val initialViewOptions = com.zerotoship.z2term.viewer.ViewerOptions.from(item?.fields.orEmpty())
        fun viewSwitch(label: Int, checked: Boolean) = EdgeSettingsUi.switchOf(context, checked).apply {
            text = context.getString(label)
            setTextColor(EdgeSettingsUi.foreground(context))
            minHeight = EdgeEditorUi.dp(context, 48)
            display.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        val viewRefreshButton = viewSwitch(R.string.viewer_show_refresh, initialViewOptions.showRefresh)
        val viewExpandButton = viewSwitch(R.string.viewer_show_expand, initialViewOptions.showExpand)

        // Command: what runs, and for an ON/OFF button how its state is found.
        val buttonStateHelp = commands.addNote(R.string.edge_button_state_desc)
        field("run", commands); field("off", commands); field("state", commands)
        val viewUpdateGroup = EdgeSettingsUi.column(context)
        val viewUpdateMode = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            contentDescription = context.getString(R.string.viewer_update_mode)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.viewer_update_manual, R.string.viewer_update_auto).map { context.getString(it) })
            setSelection(if (initialViewOptions.automatic) 1 else 0)
        }
        EdgeSettingsUi.labeled(context, viewUpdateGroup, context.getString(R.string.viewer_update_mode), viewUpdateMode)
        viewUpdateGroup.addNote(R.string.viewer_update_help)
        commands.addView(viewUpdateGroup)
        val sourceGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val source = EdgeSettingsUi.dress(context, Spinner(context)).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.edge_source_auto, R.string.edge_source_torch, R.string.edge_source_screen,
                    R.string.edge_source_process, R.string.edge_source_remember).map { context.getString(it) })
            setSelection(EdgeButtonSource.choices.indexOf(item?.fields?.get("button-source") ?: "auto").coerceAtLeast(0))
            contentDescription = context.getString(R.string.edge_button_source)
        }
        EdgeSettingsUi.labeled(context, sourceGroup, context.getString(R.string.edge_button_source), source)
        commands.addView(sourceGroup)
        field("on-select", commands)
        val qr = EdgeSettingsUi.button(context, context.getString(R.string.qr_tools_command_qr)) {
            com.zerotoship.z2term.qr.QrToolsActivity.showCommand(context,
                entries["label"]?.text.toString(), entries["run"]?.text.toString())
        }
        commands.addView(qr, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = EdgeEditorUi.dp(context, 12) })

        // Value: what an input or fixed field holds.
        field("default", valueBlock); field("choices", valueBlock); field("required", valueBlock); field("argument-kind", valueBlock)

        // Connections: which items feed a command and where its result goes.
        val formHelp = binding.addNote(R.string.edge_macro_help)
        val elsewhere = binding.addNote(R.string.edge_binding_elsewhere)
        field("args", binding); field("stdin", binding); field("result", binding)

        val resultControls = EdgeSettingsUi.switchOf(context, item?.fields?.get("result-controls") == "on").apply {
            text = context.getString(R.string.edge_result_controls)
            setTextColor(EdgeSettingsUi.foreground(context))
            minHeight = EdgeEditorUi.dp(context, 48)
        }
        advanced.addView(resultControls, LinearLayout.LayoutParams(-1, -2))
        field("every", advanced); field("timeout", advanced); field("out", advanced); field("file", advanced)

        layout.addNote(R.string.edge_item_layout_help)
        field("width", layout); field("height", layout); field("align", layout); field("at", layout)

        fun showFields() {
            val selected = EdgeItemComponent.type(selection, item)
            behaviorHelp.text = context.getString(EdgeItemHelp.behavior(selection))
            examples.forEach { (key, view) ->
                val example = if (appCommand == null) EdgeItemHelp.example(key, selection) else null
                view.text = example?.let { context.getString(R.string.edge_help_example, it) }.orEmpty()
                view.visibility = if (example == null) View.GONE else View.VISIBLE
                exampleButtons.getValue(key).visibility =
                    if (example != null && entries.getValue(key).text.isEmpty()) View.VISIBLE else View.GONE
            }
            val stateButton = selection.action == "state_button"
            val bound = selected == "macro" || (selected == "run" && !stateButton && appCommand == null)
            formHelp.visibility = if (bound) View.VISIBLE else View.GONE
            elsewhere.visibility = if (selected in setOf("argument", "result", "note", "input", "text")) View.VISIBLE else View.GONE
            qr.visibility = if (selected == "run" && !stateButton && appCommand == null) View.VISIBLE else View.GONE
            buttonStateHelp.visibility = if (stateButton) View.VISIBLE else View.GONE
            sourceGroup.visibility = if (stateButton) View.VISIBLE else View.GONE
            noteLines.visibility = if (selected == "note") View.VISIBLE else View.GONE
            listOf(viewRefreshButton, viewExpandButton, viewUpdateGroup).forEach {
                it.visibility = if (selected == "view") View.VISIBLE else View.GONE
            }
            resultControls.visibility = if (selected in setOf("result", "macro") || bound) View.VISIBLE else View.GONE
            groups.forEach { (key, group) ->
                val visible = when (key) {
                    "state" -> selected == "toggle" || stateButton
                    "off" -> stateButton
                    "on-select" -> selected == "list"
                    "every" -> selected in listOf("text", "toggle", "list") || stateButton ||
                        (selected == "view" && viewUpdateMode.selectedItemPosition == 1)
                    "file", "note-background", "note-color" -> selected == "note"
                    "args", "stdin", "result" -> bound
                    "argument-kind" -> false
                    "default" -> selected == "argument"
                    "choices" -> selection.component == "choice"
                    "required" -> selected == "argument" && selection.component != "display"
                    "rows" -> selected in setOf("argument", "result", "macro", "run")
                    "out" -> selected !in setOf("note", "terminal", "argument", "result", "view")
                    "run", "timeout" -> selected !in setOf("note", "terminal", "argument", "result")
                    else -> true
                }
                group.visibility = if (visible) View.VISIBLE else View.GONE
            }
            blocks.forEach { content ->
                (content.parent as View).visibility =
                    if (content.children.any { it.visibility == View.VISIBLE }) View.VISIBLE else View.GONE
            }
        }
        component.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = components[position]
                if (next != selection.component) {
                    selection = EdgeItemComponent.Selection(next, EdgeItemComponent.actions.getValue(next).first())
                    showActions()
                }
                showFields()
            }
        }
        action.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val choices = EdgeItemComponent.actions.getValue(selection.component)
                if (position in choices.indices) selection = selection.copy(action = choices[position])
                showFields()
            }
        }
        viewUpdateMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { showFields() }
        }
        showFields()
        session.track(outer) {
            selection != initial ||
                EdgeButtonSource.choices[source.selectedItemPosition] != (item?.fields?.get("button-source") ?: "auto") ||
                launchMode != initialMode ||
                scaleFreeform != initialScale ||
                noteLines.isChecked != (item?.fields?.get("note-lines") == "on") ||
                resultControls.isChecked != (item?.fields?.get("result-controls") == "on") ||
                viewRefreshButton.isChecked != initialViewOptions.showRefresh ||
                viewExpandButton.isChecked != initialViewOptions.showExpand ||
                (viewUpdateMode.selectedItemPosition == 1) != initialViewOptions.automatic ||
                entries.any { (key, entry) -> entry.text.toString() != item?.fields?.get(key).orEmpty() }
        }
        fun reset() {
            entries.forEach { (key, entry) -> entry.setText(item?.fields?.get(key).orEmpty()) }
            selection = initial; component.setSelection(components.indexOf(initial.component)); showActions()
            source.setSelection(EdgeButtonSource.choices.indexOf(item?.fields?.get("button-source") ?: "auto").coerceAtLeast(0))
            noteLines.isChecked = item?.fields?.get("note-lines") == "on"
            resultControls.isChecked = item?.fields?.get("result-controls") == "on"
            viewRefreshButton.isChecked = initialViewOptions.showRefresh
            viewExpandButton.isChecked = initialViewOptions.showExpand
            viewUpdateMode.setSelection(if (initialViewOptions.automatic) 1 else 0)
            launchMode = initialMode; windowMode?.setSelection(AppLaunch.modes.indexOf(initialMode))
            scaleFreeform = initialScale; scale?.setSelection(if (initialScale) 0 else 1)
            showFields()
        }
        // Closing is cancelling: the draft goes back to the saved definition either way.
        fun close() {
            reset()
            if (cancelled != null) cancelled() else {
                draft.visibility = View.GONE
                toggle?.text = openLabel
            }
        }
        toggle?.let { button ->
            button.setOnClickListener {
                if (draft.visibility == View.VISIBLE) session.discard(outer) { close() }
                else {
                    draft.visibility = View.VISIBLE
                    button.text = context.getString(R.string.edge_close)
                }
            }
        }
        // Cancel and Save close the draft, so they share one line at its foot.
        draft.addView(EdgeSettingsUi.hairline(context))
        val commit = EdgeSettingsUi.row(context).apply { setPadding(0, EdgeEditorUi.dp(context, 14), 0, 0) }
        commit.addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) {
            session.discard(outer) { close() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        commit.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_save), EdgeSettingsUi.Kind.PRIMARY) {
            runCatching {
                val values = item?.fields.orEmpty().toMutableMap()
                EdgeItemComponent.apply(selection, item, values)
                if (values["type"] == "run") values["button-state"] = if (selection.action == "state_button") "on" else "off"
                if (values["type"] == "run" && (selection.action == "state_button")) {
                    val selected = EdgeButtonSource.choices[source.selectedItemPosition]
                    if (selected == "auto") values.remove("button-source") else values["button-source"] = selected
                }
                if (values["type"] == "note") values["note-lines"] = if (noteLines.isChecked) "on" else "off"
                if (values["type"] == "view") {
                    values["view-refresh"] = if (viewUpdateMode.selectedItemPosition == 1) "auto" else "manual"
                    values["view-refresh-button"] = if (viewRefreshButton.isChecked) "on" else "off"
                    values["view-expand-button"] = if (viewExpandButton.isChecked) "on" else "off"
                }
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
                if (resultControls.visibility == View.VISIBLE) values["result-controls"] = if (resultControls.isChecked) "on" else "off"
                if (values["type"] == "run" && values["label"].isNullOrBlank() && values["icon"].isNullOrBlank() &&
                    AppLaunchCommand.packageFrom(values["run"].orEmpty()) == null) {
                    values["label"] = context.getString(R.string.edge_run)
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
                context.getString(R.string.edge_delete_item_warning, item?.let { EdgeComponentLabels.item(context, it) }.orEmpty())).apply {
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
