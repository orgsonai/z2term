package com.zerotoship.z2term.edge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.widget.WidgetStore
import java.util.UUID

/** One opening owns the input snapshots and result leases; closing cancels its commands. */
internal class EdgeMacroUi(private val context: Context, private val panel: EdgeStore.Panel,
    private val runner: EdgeRunner, private var inlineClose: (() -> Unit)? = null) {
    private class ResultBox(val text: TextView, val status: TextView, val scroll: EdgeResultScrollView) {
        var job: String? = null
        var revision = 0
        fun output(value: String) {
            text.text = value
            scroll.scrollTo(0, 0)
        }
    }
    private val inputs = mutableMapOf<String, () -> String>()
    private val outputs = mutableMapOf<String, ResultBox>()
    private var disposed = false

    fun addArgument(parent: LinearLayout, item: EdgeStore.Item) {
        val default = item.fields["default"].orEmpty()
        val label = item.fields["label"].orEmpty()
        when (item.fields["argument-kind"] ?: "text") {
            "fixed" -> {
                parent.addView(EdgeSettingsUi.body(context, default))
                inputs[item.id] = { default }
            }
            "choice" -> {
                val choices = EdgeMacroForm.choices(item.fields["choices"].orEmpty())
                val spinner = EdgeSettingsUi.dress(context, Spinner(context)).apply {
                    contentDescription = label.ifBlank { context.getString(R.string.edge_type_argument) }
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, choices)
                    setSelection(choices.indexOf(default).coerceAtLeast(0))
                }
                parent.addView(spinner)
                inputs[item.id] = { choices[spinner.selectedItemPosition] }
            }
            else -> {
                val entry = EdgeSettingsUi.field(context).apply {
                    tag = "edge-argument:${item.id}"
                    contentDescription = label.ifBlank { context.getString(R.string.edge_type_argument) }; hint = label
                    val rows = item.fields["rows"]?.toIntOrNull() ?: 1
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        (if (rows > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
                    setSingleLine(rows == 1)
                    minLines = rows; maxLines = rows
                    filters = arrayOf(InputFilter.LengthFilter(16384))
                    setText(default)
                }
                parent.addView(entry)
                inputs[item.id] = { entry.text.toString() }
            }
        }
    }

    fun addResult(parent: LinearLayout, item: EdgeStore.Item, id: String = item.id) {
        val output = TextView(context).apply {
            tag = "edge-result:$id"
            textSize = 14f; setTextColor(EdgeEditorUi.foreground(context))
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = item.fields["label"].orEmpty().ifBlank { context.getString(R.string.edge_type_result) }
        }
        val state = EdgeSettingsUi.caption(context, "").apply { visibility = android.view.View.GONE }
        val scroll = EdgeResultScrollView(context).apply {
            tag = "edge-result-scroll:$id"
            background = EdgeSettingsUi.frame(context)
            addView(output)
        }
        val box = ResultBox(output, state, scroll)
        outputs[id] = box
        val tools = LinearLayout(context)
        tools.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_stop)) {
            stop(box)
        })
        tools.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_copy)) {
            if (output.text.isNotEmpty()) context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(output.contentDescription, output.text))
        })
        tools.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_clear)) {
            stop(box); box.output(""); state.visibility = android.view.View.GONE
        })
        addClose(tools)
        parent.addView(tools); parent.addView(state)
        val rows = item.fields["rows"]?.toIntOrNull() ?: 6
        parent.addView(scroll, LinearLayout.LayoutParams(-1, (output.lineHeight * rows + dp(16)).coerceAtLeast(dp(48))))
    }

    fun addAction(parent: LinearLayout, item: EdgeStore.Item) {
        val tools = LinearLayout(context)
        tools.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_run)) {
            runCatching { execute(item) }.onFailure {
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        })
        addClose(tools); parent.addView(tools)
        if (item.fields["result"].isNullOrEmpty()) addResult(parent, item, "action:${item.id}")
    }

    private fun addClose(row: LinearLayout) {
        // Result controls share one row even on narrow panels; Close must not add a row.
        for (i in 0 until row.childCount) (row.getChildAt(i) as TextView).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setSingleLine(true)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        inlineClose?.let { row.addView(EdgePanelControls.close(context, it)); inlineClose = null }
    }

    private fun execute(item: EdgeStore.Item) {
        if (disposed) return
        require(item.command.isNotBlank()) { context.getString(R.string.edge_no_command) }
        val args = EdgeMacroForm.resolve(item, panel.items) { inputs[it]?.invoke() }
        val box = outputs[item.fields["result"].orEmpty().ifEmpty { "action:${item.id}" }]
            ?: throw IllegalArgumentException("Result box is not visible")
        if (box.job != null) {
            Toast.makeText(context, R.string.edge_busy, Toast.LENGTH_SHORT).show(); return
        }
        val job = "form:${UUID.randomUUID()}"
        val revision = box.revision + 1
        val command = EdgeMacroCommand.resolve(item.command, WidgetStore.availableMacros(context))
        val accepted = runner.run(job, command, item.timeout, arguments = args) { result ->
            if (disposed || box.revision != revision || box.job != job) return@run
            box.job = null
            box.output(result.output)
            box.status.text = result.error ?: context.getString(R.string.edge_macro_done)
        }
        if (accepted) {
            box.revision = revision; box.job = job
            box.output("")
            box.status.visibility = android.view.View.VISIBLE
            box.status.setText(R.string.edge_updating)
        } else Toast.makeText(context, R.string.edge_busy, Toast.LENGTH_SHORT).show()
    }

    private fun stop(box: ResultBox) {
        box.revision++
        box.job?.let { runner.cancelJob(it); box.status.setText(R.string.edge_macro_stopped) }
        box.job = null
    }

    fun dispose() {
        disposed = true
        outputs.values.forEach(::stop)
        inputs.clear(); outputs.clear()
    }

    private fun dp(value: Int) = EdgeEditorUi.dp(context, value)
}
