package com.zerotoship.z2term.edge

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R

/** The draft is local to this view; only Save writes definitions. */
object EdgeAppearanceEditor {
    fun create(context: Context, panel: EdgeStore.Panel, store: EdgeStore, screenWidth: Int, screenHeight: Int,
        preview: (Map<String, String>) -> Unit, finish: () -> Unit): View {
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        outer.addView(Button(context).apply {
            text = context.getString(R.string.edge_adjust)
            setOnClickListener { content.visibility = View.VISIBLE }
        })
        outer.addView(content)
        val entries = linkedMapOf<String, EditText>()
        val defaults = mapOf("size" to if (panel.handle == "button") "48" else "6",
            "length" to "6", "alpha" to "1", "width" to "360", "height" to "72%")
        val save = Button(context).apply { text = context.getString(R.string.edge_save) }
        fun values(): Map<String, String> = entries.mapValues { it.value.text.toString().trim() }
        fun update() {
            val draft = values()
            val valid = runCatching {
                EdgeStore.validatePanel(panel.fields + draft)
                draft["size"]?.let {
                    require(it.toInt() in if (panel.handle == "button") 32..96 else 2..48)
                }
            }.isSuccess
            save.isEnabled = valid
            if (valid) runCatching { preview(draft) }.onFailure {
                save.isEnabled = false
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        }
        fun control(key: String, label: Int, low: Int, high: Int, convert: (Int) -> String) {
            content.addView(TextView(context).apply { text = context.getString(label) })
            val entry = EditText(context).apply {
                setSingleLine(true); setText(panel.fields[key] ?: defaults.getValue(key))
                contentDescription = context.getString(label)
            }
            entries[key] = entry
            content.addView(entry)
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
            val slider = SeekBar(context).apply {
                min = low; max = high
                progress = sliderValue(entry.text.toString())
                contentDescription = context.getString(label)
            }
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
            content.addView(slider)
        }
        if (panel.handle != "off") {
            control("size", R.string.edge_adjust_size, if (panel.handle == "button") 32 else 2,
                if (panel.handle == "button") 96 else 48) { it.toString() }
            if (panel.handle == "bar") control("length", R.string.edge_adjust_length, 1, 100) { it.toString() }
            control("alpha", R.string.edge_adjust_alpha, 5, 100) { (it / 100f).toString() }
        }
        control("width", R.string.edge_adjust_width, 1, 100) { "$it%" }
        control("height", R.string.edge_adjust_height, 1, 100) { "$it%" }
        content.addView(TextView(context).apply { text = context.getString(R.string.edge_preview_help) })
        save.setOnClickListener {
            runCatching {
                val draft = values()
                EdgeStore.validatePanel(panel.fields + draft)
                draft["size"]?.let {
                    require(it.toInt() in (if (panel.handle == "button") 32..96 else 2..48))
                }
                val changed = draft.filter { (key, value) -> value != (panel.fields[key] ?: defaults[key]) }
                val current = store.panel(panel.id)
                check(changed.keys.all { current.fields[it] == panel.fields[it] }) {
                    context.getString(R.string.edge_edit_conflict)
                }
                store.setPanel(panel.id, changed)
                finish()
            }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
        content.addView(save)
        content.addView(Button(context).apply {
            text = context.getString(android.R.string.cancel); setOnClickListener { finish() }
        })
        return outer
    }
}
