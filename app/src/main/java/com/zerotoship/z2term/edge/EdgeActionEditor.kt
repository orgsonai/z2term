package com.zerotoship.z2term.edge

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.zerotoship.z2term.R

/** The editor reports a draft only; its parent owns Save/Cancel and conflict detection. */
internal object EdgeActionEditor {
    fun create(context: Context, initial: List<EdgeActions.Action>, changed: (String) -> Unit): View {
        val actions = initial.toMutableList()
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows)
        val types = EdgeActions.Type.entries
        val apps by lazy { AppCatalog.launchableApps(context) }
        fun publish() { changed(EdgeActions.encode(actions)) }
        lateinit var add: Button
        var revision = 0
        fun render() {
            val currentRevision = ++revision
            add.isEnabled = actions.size < EdgeActions.MAX_ACTIONS
            rows.removeAllViews()
            actions.forEachIndexed { index, action ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val controls = LinearLayout(context)
                val picker = Spinner(context).apply {
                    contentDescription = context.getString(R.string.edge_action_step, index + 1)
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                        types.map { context.getString(label(it)) })
                    setSelection(types.indexOf(action.type))
                }
                controls.addView(picker, LinearLayout.LayoutParams(0, -2, 1f))
                fun button(label: String, enabled: Boolean, click: () -> Unit) {
                    controls.addView(EdgeEditorUi.button(context, label, action = click).apply { isEnabled = enabled })
                }
                button("↑", index > 0) {
                    val previous = actions[index - 1]; actions[index - 1] = actions[index]; actions[index] = previous
                    publish(); render()
                }
                button("↓", index < actions.lastIndex) {
                    val next = actions[index + 1]; actions[index + 1] = actions[index]; actions[index] = next
                    publish(); render()
                }
                button("×", true) { actions.removeAt(index); publish(); render() }
                row.addView(controls)
                if (action.type == EdgeActions.Type.LAUNCH) {
                    val choices = listOf("" to context.getString(R.string.edge_action_choose_app)) +
                        apps.map { it.packageName to it.label } +
                        if (action.argument.isNotEmpty() && apps.none { it.packageName == action.argument })
                            listOf(action.argument to action.argument) else emptyList()
                    row.addView(Spinner(context).apply {
                        contentDescription = context.getString(R.string.edge_action_choose_app)
                        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, choices.map { it.second })
                        setSelection(choices.indexOfFirst { it.first == action.argument }.coerceAtLeast(0))
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if (revision != currentRevision) return
                                if (choices[position].first != actions[index].argument) {
                                    actions[index] = actions[index].copy(argument = choices[position].first); publish()
                                }
                            }
                        }
                    })
                } else if (action.type.parameter) {
                    row.addView(EditText(context).apply {
                        setSingleLine(true)
                        if (action.type == EdgeActions.Type.WAIT) inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        hint = context.getString(when (action.type) {
                            EdgeActions.Type.WAIT -> R.string.edge_action_wait_hint
                            EdgeActions.Type.LAUNCH -> R.string.edge_action_launch_hint
                            else -> R.string.edge_action_command_hint
                        })
                        contentDescription = hint
                        setText(action.argument)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                if (revision != currentRevision) return
                                actions[index] = actions[index].copy(argument = s.toString()); publish()
                            }
                        })
                    })
                }
                picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (revision != currentRevision) return
                        if (types[position] != actions[index].type) {
                            actions[index] = EdgeActions.Action(types[position], if (types[position] == EdgeActions.Type.WAIT) "500" else "")
                            publish(); render()
                        }
                    }
                }
                rows.addView(row)
            }
        }
        add = EdgeEditorUi.button(context, context.getString(R.string.edge_action_add)) {
            if (actions.size < EdgeActions.MAX_ACTIONS) {
                actions.add(EdgeActions.Action(EdgeActions.Type.PANEL)); publish(); render()
            }
        }
        root.addView(add)
        render()
        return root
    }

    fun label(type: EdgeActions.Type): Int = when (type) {
        EdgeActions.Type.PANEL -> R.string.edge_action_panel
        EdgeActions.Type.BACK -> R.string.edge_action_back
        EdgeActions.Type.HOME -> R.string.edge_action_home
        EdgeActions.Type.RECENTS -> R.string.edge_action_recents
        EdgeActions.Type.SHADE -> R.string.edge_action_shade
        EdgeActions.Type.LAUNCH -> R.string.edge_action_launch
        EdgeActions.Type.WAIT -> R.string.edge_action_wait
        EdgeActions.Type.COMMAND -> R.string.edge_action_command
        EdgeActions.Type.SWIPE_UP -> R.string.edge_action_swipe_up
        EdgeActions.Type.SWIPE_DOWN -> R.string.edge_action_swipe_down
        EdgeActions.Type.SCROLL_VARIABLE -> R.string.edge_scroll_variable
        EdgeActions.Type.SCROLL_FIXED -> R.string.edge_scroll_fixed
        EdgeActions.Type.SCROLL_STOP -> R.string.edge_action_stop
        EdgeActions.Type.SCROLL_FASTER -> R.string.edge_action_faster
        EdgeActions.Type.SCROLL_SLOWER -> R.string.edge_action_slower
        EdgeActions.Type.SCROLL_REVERSE -> R.string.edge_action_reverse
    }
}
