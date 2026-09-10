package com.zerotoship.z2term.automation

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.text.InputFilter
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AppCatalog
import com.zerotoship.z2term.edge.EdgeEditorUi

/** A single source line is replaced only after Apply. The activity retains the form draft. */
internal object ActionStepEditor {
    private data class Kind(val verb: String, val label: Int, val fields: List<Int>, val defaults: List<String>)
    private val kinds = listOf(
        Kind("repeat", R.string.action_edit_add_repeat, listOf(R.string.action_block_count), listOf("3")),
        Kind("if", R.string.action_edit_add_branch, listOf(R.string.action_block_condition), listOf("charging")),
        Kind("click", R.string.action_ui_click, listOf(R.string.action_ui_selector), listOf("text=")),
        Kind("long-click", R.string.action_ui_hold, listOf(R.string.action_ui_selector), listOf("text=")),
        Kind("wait-ui", R.string.action_ui_wait, listOf(R.string.action_edit_ms, R.string.action_ui_selector), listOf("5000", "text=")),
        Kind("call", R.string.action_edit_call, listOf(R.string.action_edit_macro_name), listOf("")),
        Kind("launch", R.string.action_edit_launch, listOf(R.string.action_edit_package), listOf("")),
        Kind("target", R.string.action_edit_target, listOf(R.string.action_edit_package), listOf("")),
        Kind("wait", R.string.action_edit_wait, listOf(R.string.action_edit_ms), listOf("800")),
        Kind("key", R.string.action_edit_key, listOf(R.string.action_edit_key), listOf("back")),
        Kind("tap", R.string.action_edit_tap, listOf(R.string.action_edit_unit, R.string.action_edit_x, R.string.action_edit_y), listOf("percent", "50", "50")),
        Kind("long-press", R.string.action_edit_hold, listOf(R.string.action_edit_unit, R.string.action_edit_x, R.string.action_edit_y, R.string.action_edit_ms), listOf("percent", "50", "50", "700")),
        Kind("swipe", R.string.action_edit_swipe, listOf(R.string.action_edit_unit, R.string.action_edit_x, R.string.action_edit_y, R.string.action_edit_x2, R.string.action_edit_y2, R.string.action_edit_ms), listOf("percent", "50", "75", "50", "25", "450")),
        Kind("scroll", R.string.action_edit_scroll, listOf(R.string.action_edit_speed, R.string.action_edit_ms, R.string.action_edit_window_x, R.string.action_edit_window_y), listOf("-600", "2000", "50", "50")),
        Kind("command", R.string.action_edit_command, listOf(R.string.action_edit_command), listOf("")),
    )

    fun supports(line: String): Boolean {
        val words = line.trim().split(Regex("\\s+"))
        val kind = kinds.firstOrNull { it.verb == words.first() } ?: return false
        return kind.verb in listOf("command", "if", "repeat", "click", "long-click", "wait-ui") || words.size == kind.fields.size + 1
    }

    fun show(context: Context, initial: String, screen: ActionDefinition.Screen,
        apps: () -> List<AppCatalog.LaunchableApp>, draft: (String) -> Unit, apply: (String) -> Unit,
        cancel: () -> Unit, pick: (String) -> Unit, macros: () -> List<String> = { emptyList() }, pickElement: ((String) -> Unit)? = null): AlertDialog {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val fields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val error = EdgeEditorUi.label(context, "", secondary = true)
        val trimmed = initial.trim()
        val initialVerb = trimmed.split(Regex("\\s+")).first()
        val available = if (initialVerb in listOf("repeat", "if")) kinds.filter { it.verb == initialVerb }
            else kinds.filterNot { it.verb in listOf("repeat", "if") }
        var kind = available.firstOrNull { it.verb == trimmed.split(Regex("\\s+")).first() }
            ?: kinds.first { it.verb == "wait" }
        var values = if (kind.verb in listOf("command", "if", "click", "long-click")) listOf(trimmed.removePrefix(kind.verb).trimStart())
            else if (kind.verb == "wait-ui") trimmed.split(Regex("\\s+"), limit = 3).drop(1)
            else trimmed.split(Regex("\\s+")).drop(1)
        if (values.size != kind.fields.size) values = kind.defaults
        var current = initial
        var rendering = false
        var revision = 0
        val picker = Spinner(context).apply {
            contentDescription = context.getString(R.string.action_edit_type)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, available.map { context.getString(it.label) })
            setSelection(available.indexOf(kind))
        }
        body.addView(picker); body.addView(fields); body.addView(error)
        lateinit var dialog: AlertDialog
        var appDialog: AlertDialog? = null
        fun publish() {
            if (rendering) return
            current = kind.verb + " " + values.joinToString(" ")
            draft(current); error.text = ""
        }
        fun render() {
            rendering = true
            val renderedRevision = ++revision
            fields.removeAllViews()
            kind.fields.forEachIndexed { index, label ->
                fields.addView(EdgeEditorUi.label(context, context.getString(label), secondary = true))
                val choices = when (label) {
                    R.string.action_edit_unit -> listOf("px", "percent")
                    R.string.action_edit_key -> listOf("back", "home", "recents", "shade", "quicksettings", "screenshot", "split")
                    else -> emptyList()
                }
                if (choices.isNotEmpty()) {
                    // Keep an invalid existing value visible until the user deliberately fixes it.
                    val options = if (values[index] in choices) choices else choices + values[index]
                    fields.addView(Spinner(context).apply {
                        contentDescription = context.getString(label)
                        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
                        setSelection(options.indexOf(values[index]))
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if (revision != renderedRevision || values[index] == options[position]) return
                                values = values.toMutableList().apply { this[index] = options[position] }; publish()
                            }
                        }
                    })
                } else {
                    val input = EditText(context).apply {
                        setSingleLine(true)
                        contentDescription = context.getString(label)
                        inputType = if (label in listOf(R.string.action_edit_package, R.string.action_edit_command, R.string.action_edit_macro_name,
                            R.string.action_ui_selector, R.string.action_block_count, R.string.action_block_condition))
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                        filters = arrayOf(InputFilter.LengthFilter(ActionDefinition.MAX_BYTES))
                        setText(values[index])
                        doAfterTextChanged {
                            if (revision == renderedRevision) {
                                values = values.toMutableList().apply { this[index] = it.toString() }; publish()
                            }
                        }
                    }
                    fields.addView(input)
                    if (label == R.string.action_block_count) fields.addView(EdgeEditorUi.label(context,
                        context.getString(R.string.action_block_count_hint), secondary = true))
                    if (label == R.string.action_block_condition) fields.addView(EdgeEditorUi.label(context,
                        context.getString(R.string.action_block_condition_hint), secondary = true))
                    if (label == R.string.action_ui_selector) {
                        fields.addView(EdgeEditorUi.label(context, context.getString(R.string.action_ui_hint), secondary = true))
                        if (pickElement != null) fields.addView(EdgeEditorUi.button(context, context.getString(R.string.action_ui_pick)) {
                            try { pickElement(current); dialog.dismiss() }
                            catch (e: Exception) { error.text = e.message }
                        })
                    }
                    if (label == R.string.action_edit_macro_name) {
                        fields.addView(EdgeEditorUi.button(context, context.getString(R.string.action_edit_pick_macro)) {
                            val names = macros()
                            val selectionDialog = AlertDialog.Builder(context).setTitle(R.string.action_edit_pick_macro)
                                .setItems(names.toTypedArray()) { _, position -> input.setText(names[position]) }
                                .setNegativeButton(android.R.string.cancel, null).create()
                            appDialog = selectionDialog
                            selectionDialog.show()
                        })
                    }
                    if (label == R.string.action_edit_package) {
                        fields.addView(EdgeEditorUi.button(context, context.getString(R.string.edge_pick_app)) {
                            val entries = apps()
                            val search = EditText(context).apply { hint = context.getString(R.string.edge_search_apps); setSingleLine(true) }
                            val list = ListView(context)
                            val contents = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(search); addView(list, LinearLayout.LayoutParams(-1, EdgeEditorUi.dp(context, 300)))
                            }
                            var visible = entries
                            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf<String>())
                            fun filter() {
                                val query = search.text.toString()
                                visible = entries.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                                adapter.clear(); adapter.addAll(visible.map { it.label + "\n" + it.packageName })
                            }
                            list.adapter = adapter
                            search.doAfterTextChanged { filter() }; filter()
                            val selectionDialog = AlertDialog.Builder(context).setTitle(R.string.edge_pick_app).setView(contents)
                                .setNegativeButton(android.R.string.cancel, null).create()
                            list.setOnItemClickListener { _, _, position, _ -> input.setText(visible[position].packageName); selectionDialog.dismiss() }
                            appDialog = selectionDialog
                            selectionDialog.show()
                        })
                    }
                }
            }
            if (kind.verb in listOf("tap", "long-press", "swipe")) {
                fields.addView(EdgeEditorUi.button(context, context.getString(R.string.action_edit_pick)) {
                    try { pick(current); dialog.dismiss() }
                    catch (e: Exception) { error.text = e.message }
                })
            }
            rendering = false
        }
        render()
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (kind == available[position]) return
                kind = available[position]; values = kind.defaults; publish(); render()
            }
        }
        dialog = AlertDialog.Builder(context).setTitle(R.string.action_edit_step)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.action_edit_apply, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancel() }
            .setOnCancelListener { cancel() }.create()
        dialog.setOnDismissListener { appDialog?.dismiss() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    ActionEditorDocument.singleLine(current)
                    // The entire document, including the real target/order, is checked on save.
                    val executable = if (kind.verb in listOf("repeat", "if")) current + "\nwait 0\nend" else current
                    ActionDefinition.parse("version=2\nscreen=$screen\ntarget org.example.target\n$executable\nwait 0")
                    apply(current); dialog.dismiss()
                } catch (e: Exception) { error.text = e.message }
            }
        }
        dialog.show()
        return dialog
    }
}
