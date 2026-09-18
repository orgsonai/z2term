package com.zerotoship.z2term.edge

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import com.zerotoship.z2term.R
import kotlin.math.roundToInt

/** Numeric editing keeps the stored unit explicit and preserves the draft until Save. */
internal class EdgeAppearanceValue(
    context: Context,
    val key: String,
    label: Int,
    initial: String,
    low: Int,
    high: Int,
    screenExtent: Int,
    changed: () -> Unit,
) {
    val view = EdgeSettingsUi.column(context)
    val value = EditText(context).apply { setText(initial) }
    val input = EdgeSettingsUi.field(context)
    private val dimension = key == "width" || key == "height"
    private var percent = initial.endsWith('%')
    private val density = context.resources.displayMetrics.density
    private val screenDp = screenExtent.coerceAtLeast(1) / density
    private var syncing = false

    init {
        view.addView(EdgeSettingsUi.caption(context, context.getString(label)))
        val line = EdgeSettingsUi.row(context)
        input.apply {
            gravity = Gravity.CENTER
            contentDescription = context.getString(label)
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (key in setOf("size", "icon-size", "gesture-speed", "gesture-range")) 0
                else InputType.TYPE_NUMBER_FLAG_DECIMAL)
            setText(if (key == "alpha") format((initial.toFloatOrNull() ?: 1f) * 100) else initial.removeSuffix("%"))
        }
        line.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val slider = EdgeSettingsUi.dress(context, SeekBar(context)).apply {
            min = low; max = high
            minimumHeight = EdgeEditorUi.dp(context, 48)
            contentDescription = context.getString(label)
        }
        fun syncSlider() {
            val raw = value.text.toString()
            val number = raw.removeSuffix("%").toFloatOrNull() ?: return
            if (!number.isFinite()) return
            slider.progress = when {
                key == "alpha" -> number * 100
                dimension && !raw.endsWith('%') -> number * 100 / screenDp
                else -> number
            }.roundToInt().coerceIn(low, high)
        }
        fun publish() {
            val raw = input.text.toString().trim()
            value.setText(when {
                key == "alpha" -> raw.toFloatOrNull()?.let { (it / 100).toString() } ?: raw
                dimension && percent -> "$raw%"
                else -> raw
            })
            syncSlider()
            changed()
        }
        if (dimension) {
            val unit = EdgeSettingsUi.dress(context, Spinner(context)).apply {
                contentDescription = context.getString(R.string.edge_dimension_unit, context.getString(label))
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                    listOf(context.getString(R.string.edge_unit_percent), "dp"))
                setSelection(if (percent) 0 else 1)
            }
            unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = position == 0
                    if (next == percent) return
                    val old = input.text.toString().toFloatOrNull()
                    percent = next
                    syncing = true
                    if (old != null && old.isFinite()) {
                        val converted = if (next) (old * 100 / screenDp).coerceAtMost(100f) else old * screenDp / 100
                        input.setText(format(converted.coerceAtLeast(0.001f)))
                    }
                    syncing = false
                    publish()
                }
            }
            line.addView(unit, LinearLayout.LayoutParams(-2, -2))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { if (!syncing) publish() }
        })
        syncSlider()
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) input.setText(
                    if (dimension && !percent) format(progress * screenDp / 100) else progress.toString())
            }
        })
        view.addView(line)
        view.addView(slider, LinearLayout.LayoutParams(-1, -2))
    }

    private fun format(number: Float): String =
        String.format(java.util.Locale.US, "%.3f", number).trimEnd('0').trimEnd('.')
}
