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
import androidx.compose.ui.graphics.toArgb
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeToolRow
import com.zerotoship.z2term.ui.theme.AppColors
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
            addView(TextView(context).apply {
                text = field.label; setTextColor(AppColors.textSecondary.toArgb()); setPadding(0, dp(12), 0, dp(4))
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
                "datetime" -> {
                    val format = SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT).apply { isLenient = false }
                    val calendar = Calendar.getInstance().apply {
                        add(Calendar.HOUR_OF_DAY, 1); set(Calendar.SECOND, 0)
                        if (field.initial.matches(Regex("[0-9]{12}"))) runCatching { format.parse(field.initial) }.getOrNull()?.let { time = it }
                    }
                    val row = EdgeToolRow(context)
                    lateinit var date: Button
                    lateinit var time: Button
                    fun labels() {
                        date.text = String.format(Locale.ROOT, "%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
                        time.text = String.format(Locale.ROOT, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
                    }
                    date = button("") {
                        showDialog(DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); labels() },
                            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)))
                    }
                    time = button("") {
                        showDialog(TimePickerDialog(context, { _, h, m -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, m); labels() },
                            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true))
                    }
                    date.contentDescription = field.label; time.contentDescription = field.label
                    labels(); row.addView(date); row.addView(time); addView(row)
                    val value: () -> String = { format.format(calendar.time) }; value
                }
                else -> {
                    val entry = EditText(context).apply {
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        setText(field.initial); contentDescription = field.label
                        setTextColor(AppColors.textPrimary.toArgb()); minLines = 1; maxLines = 4
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
                if (missing != null) error(context.getString(R.string.viewer_required, action.fields[missing].label))
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
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun button(label: String, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; minHeight = dp(48)
        setTextColor(AppColors.textPrimary.toArgb()); setOnClickListener { click() }
    }
}
