package com.zerotoship.z2term.viewer

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeSettingsUi
import com.zerotoship.z2term.edge.EdgeToolRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Native inputs described entirely by a producer's control file. Values stay separate from code. */
@android.annotation.SuppressLint("ViewConstructor") // Created from a runtime definition, never inflated from XML.
internal class ViewerForm(context: Context, action: ViewerControls.Action, showDialog: (Dialog) -> Unit,
    cancel: () -> Unit, submit: (List<String>) -> Unit, error: (String) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12))
        val values = action.fields.map { field ->
            // A switch carries its own label; every other input sits under one.
            if (field.type != "switch") addView(TextView(context).apply {
                text = field.label; setTextColor(EdgeSettingsUi.muted(context)); setPadding(0, dp(12), 0, dp(4))
            })
            when (field.type) {
                "choice" -> {
                    val spinner = Spinner(context).apply {
                        contentDescription = field.label
                        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, field.choices.map { it.second })
                        setSelection(field.choices.indexOfFirst { it.first == field.initial }.coerceAtLeast(0))
                    }
                    addView(spinner, LayoutParams(-1, dp(48)))
                    val value: () -> String = { field.choices[spinner.selectedItemPosition].first }; value
                }
                "switch" -> {
                    val toggle = EdgeSettingsUi.switchOf(context, field.initial == "on").apply {
                        text = field.label; setTextColor(EdgeSettingsUi.foreground(context)); minHeight = dp(48)
                    }
                    addView(toggle, LayoutParams(-1, -2))
                    val value: () -> String = { if (toggle.isChecked) "on" else "off" }; value
                }
                "datetime", "date", "time" -> {
                    val pattern = when (field.type) { "date" -> "yyyyMMdd"; "time" -> "HHmm"; else -> "yyyyMMddHHmm" }
                    val format = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
                    val calendar = Calendar.getInstance().apply {
                        add(Calendar.HOUR_OF_DAY, 1); set(Calendar.SECOND, 0)
                        if (field.initial.length == pattern.length && field.initial.all { it in '0'..'9' }) {
                            runCatching { format.parse(field.initial) }.getOrNull()?.let { parsed ->
                                val given = Calendar.getInstance().apply { time = parsed }
                                if (field.type != "time") set(given.get(Calendar.YEAR), given.get(Calendar.MONTH), given.get(Calendar.DAY_OF_MONTH))
                                if (field.type != "date") { set(Calendar.HOUR_OF_DAY, given.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, given.get(Calendar.MINUTE)) }
                            }
                        }
                    }
                    val row = EdgeToolRow(context)
                    var date: Button? = null
                    var time: Button? = null
                    fun labels() {
                        date?.text = String.format(Locale.ROOT, "%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
                        time?.text = String.format(Locale.ROOT, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
                    }
                    if (field.type != "time") date = button("") {
                        showDialog(DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); labels() },
                            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)))
                    }.also { it.contentDescription = field.label; row.addView(it) }
                    if (field.type != "date") time = button("") {
                        showDialog(TimePickerDialog(context, { _, h, m -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, m); labels() },
                            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true))
                    }.also { it.contentDescription = field.label; row.addView(it) }
                    labels(); addView(row)
                    val value: () -> String = { format.format(calendar.time) }; value
                }
                else -> {
                    val entry = EditText(context).apply {
                        inputType = when (field.type) {
                            "number" -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED or
                                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        }
                        setText(field.initial); contentDescription = field.label
                        setTextColor(EdgeSettingsUi.foreground(context))
                        when (field.type) {
                            "multiline" -> { minLines = 4; maxLines = 12; gravity = android.view.Gravity.TOP or android.view.Gravity.START }
                            "number" -> { minLines = 1; maxLines = 1 }
                            else -> { minLines = 1; maxLines = 4 }
                        }
                        filters = arrayOf(android.text.InputFilter.LengthFilter(4096))
                    }
                    addView(entry, LayoutParams(-1, -2))
                    val value: () -> String = { entry.text.toString() }; value
                }
            }
        }
        addView(EdgeToolRow(context).apply {
            addView(button(context.getString(android.R.string.cancel), cancel))
            addView(button(action.label) {
                val args = values.map { it() }
                val missing = action.fields.indices.firstOrNull { action.fields[it].required && args[it].isBlank() }
                val notNumber = action.fields.indices.firstOrNull { action.fields[it].type == "number" && !numeric(args[it]) }
                if (missing != null) error(context.getString(R.string.viewer_required, action.fields[missing].label))
                else if (notNumber != null) error(context.getString(R.string.viewer_number, action.fields[notNumber].label))
                else submit(args)
            })
        })
    }
    fun enable(enabled: Boolean) {
        fun visit(view: View) {
            view.isEnabled = enabled
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(this)
    }
    /** Blank passes here; whether it may be blank is the field's `required`. */
    private fun numeric(value: String) = value.isBlank() || value.trim().matches(Regex("[-+]?[0-9]+([.][0-9]+)?"))
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun button(label: String, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; minHeight = dp(48)
        setTextColor(EdgeSettingsUi.foreground(context)); setOnClickListener { click() }
    }
}
