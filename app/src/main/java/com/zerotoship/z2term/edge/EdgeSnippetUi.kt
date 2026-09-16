package com.zerotoship.z2term.edge

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetGroup
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.snippets.SnippetTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

/** Uses the app's existing snippets; the panel has no separate command registry. */
@android.annotation.SuppressLint("ViewConstructor")
internal class EdgeSnippetUi(context: Context, private val draft: () -> String,
    private val insert: (String) -> Boolean, private val close: () -> Unit) : LinearLayout(context) {
    private val store = SnippetStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = EdgeResultScrollView(context).apply { addView(rows) }
    private val group = Spinner(context)
    private var selectedGroup: String? = null
    private var snippets = emptyList<Snippet>()
    private var groups = emptyList<SnippetGroup>()
    private var editing = false

    init {
        orientation = VERTICAL
        tag = "edge-snippets"
        val header = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(EdgeSettingsUi.body(context, context.getString(R.string.snippets_title)), LayoutParams(0, -2, 1f))
        header.addView(EdgeSettingsUi.button(context, context.getString(R.string.snippets_new)) {
            edit(Snippet(UUID.randomUUID().toString(), "", draft(), groupId = selectedGroup.orEmpty()))
        })
        header.addView(EdgePanelControls.close(context, close))
        addView(header)
        group.contentDescription = context.getString(R.string.snippets_group_field)
        group.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = when (position) { 0 -> null; 1 -> ""; else -> groups.getOrNull(position - 2)?.id }
                if (selectedGroup != next) { selectedGroup = next; if (!editing) list() }
            }
        }
        addView(group, LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, 0, 1f))
        scope.launch {
            store.snippets.combine(store.groups) { commands, shelves -> commands to shelves }.catch { e ->
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }.collect { (commands, shelves) ->
                snippets = commands
                if (groups != shelves || group.adapter == null) {
                    groups = shelves
                    if (selectedGroup != null && selectedGroup != "" && groups.none { it.id == selectedGroup }) selectedGroup = null
                    group.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                        listOf(context.getString(R.string.snippets_group_all), context.getString(R.string.snippets_group_none)) + groups.map { it.name })
                    group.setSelection(when (selectedGroup) { null -> 0; "" -> 1; else -> groups.indexOfFirst { it.id == selectedGroup } + 2 })
                }
                if (!editing) list()
            }
        }
    }

    private fun list() {
        editing = false
        group.visibility = if (groups.isEmpty()) GONE else VISIBLE
        val position = scroll.scrollY
        rows.removeAllViews()
        val shown = snippets.filter { selectedGroup == null || it.groupId == selectedGroup }
        if (shown.isEmpty()) rows.addView(EdgeSettingsUi.body(context, context.getString(R.string.snippets_empty)))
        shown.forEach { snippet ->
            val row = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            val label = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(EdgeSettingsUi.body(context, snippet.label.ifBlank { snippet.command }).apply { maxLines = 2 })
                if (snippet.label.isNotBlank()) addView(EdgeSettingsUi.caption(context, snippet.command).apply {
                    typeface = Typeface.MONOSPACE; maxLines = 2
                })
                setOnClickListener { choose(snippet) }
                tag = "edge-snippet:${snippet.id}"
            }
            row.addView(label, LayoutParams(0, -2, 1f))
            row.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_edit)) { edit(snippet) })
            row.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_delete), EdgeSettingsUi.Kind.DANGER) {
                confirmDelete(snippet)
            })
            rows.addView(row); rows.addView(EdgeSettingsUi.hairline(context))
        }
        scroll.post { scroll.scrollTo(0, position) }
    }

    private fun form() {
        editing = true; group.visibility = GONE
        rows.removeAllViews(); scroll.scrollTo(0, 0)
    }

    private fun field(label: Int, text: String, multiline: Boolean = false): EditText =
        EdgeSettingsUi.field(context).apply {
            contentDescription = context.getString(label)
            setSingleLine(!multiline)
            if (multiline) { minLines = 2; maxLines = 6 }
            setText(text)
            EdgeSettingsUi.labeled(context, rows, context.getString(label), this)
        }

    private fun edit(snippet: Snippet) {
        form()
        val label = field(R.string.snippets_label_field, snippet.label)
        val command = field(R.string.snippets_command_field, snippet.command, multiline = true)
        val inputForm = EdgeSettingsUi.switchOf(context, snippet.inputForm).apply { text = context.getString(R.string.snippet_inputs_enable) }
        val shareAction = EdgeSettingsUi.switchOf(context, snippet.shareAction).apply { text = context.getString(R.string.snippet_share_action) }
        rows.addView(inputForm); rows.addView(shareAction)
        val choices = listOf(SnippetGroup("", context.getString(R.string.snippets_group_none))) + groups
        val shelf = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, choices.map { it.name })
            setSelection(choices.indexOfFirst { it.id == snippet.groupId }.coerceAtLeast(0))
        }
        if (groups.isNotEmpty()) EdgeSettingsUi.labeled(context, rows, context.getString(R.string.snippets_group_field), shelf)
        actions(R.string.action_save) {
            val text = command.text.toString()
            if (text.isBlank()) { command.error = context.getString(R.string.snippets_command_field); return@actions }
            if (inputForm.isChecked && runCatching { SnippetTemplate.fields(text) }.isFailure) {
                command.error = context.getString(R.string.snippet_inputs_invalid); return@actions
            }
            save { store.upsert(snippet.copy(label = label.text.toString().trim(), command = text,
                groupId = if (groups.isEmpty()) snippet.groupId else choices[shelf.selectedItemPosition].id,
                inputForm = inputForm.isChecked, shareAction = shareAction.isChecked)) }
        }
    }

    private fun confirmDelete(snippet: Snippet) {
        form()
        rows.addView(EdgeSettingsUi.body(context, context.getString(R.string.confirm_delete_item_msg,
            snippet.label.ifBlank { snippet.command })))
        actions(R.string.edge_delete) { save { store.delete(snippet.id) } }
    }

    private fun choose(snippet: Snippet) {
        if (!snippet.inputForm) { if (insert(snippet.command)) close(); return }
        val fields = runCatching { SnippetTemplate.fields(snippet.command) }.getOrElse {
            Toast.makeText(context, R.string.snippet_inputs_invalid, Toast.LENGTH_LONG).show(); return
        }
        form()
        val values = fields.associate { field ->
            val read: () -> String
            if (field.kind == SnippetTemplate.Kind.CHOICE) {
                val picker = Spinner(context).apply {
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, field.choices)
                }
                EdgeSettingsUi.labeled(context, rows, field.name, picker)
                read = { field.choices[picker.selectedItemPosition] }
            } else {
                val entry = EdgeSettingsUi.field(context).apply { setSingleLine(true); setText(field.initial) }
                EdgeSettingsUi.labeled(context, rows, field.name, entry)
                read = { entry.text.toString() }
            }
            field.name to read
        }
        actions(R.string.share_intake_insert) {
            runCatching { SnippetTemplate.render(snippet.command, values.mapValues { it.value() }) }
                .onSuccess { if (insert(it)) close() }
                .onFailure { Toast.makeText(context, R.string.snippet_inputs_invalid, Toast.LENGTH_LONG).show() }
        }
    }

    private fun actions(label: Int, action: () -> Unit) {
        rows.addView(LinearLayout(context).apply {
            addView(EdgeSettingsUi.button(context, context.getString(android.R.string.cancel)) { list() }, LayoutParams(0, -2, 1f))
            addView(EdgeSettingsUi.button(context, context.getString(label), action = action), LayoutParams(0, -2, 1f))
        })
    }

    private fun save(action: suspend () -> Unit) {
        scope.launch {
            try { action(); list() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
        }
    }

    fun dispose() { scope.cancel() }
}
