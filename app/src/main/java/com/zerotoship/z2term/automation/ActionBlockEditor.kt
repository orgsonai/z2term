package com.zerotoship.z2term.automation

import android.app.Dialog
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import com.zerotoship.z2term.R

/** The source model owns moves; delimiters never appear as independently movable rows. */
internal object ActionBlockEditor {
    fun render(context: Context, parent: LinearLayout, document: ActionBlockDocument,
        edit: (Int, String) -> Unit, add: (Int, String) -> Unit, change: (String) -> Unit,
        show: (Dialog) -> Unit, failure: (Exception) -> Unit) {
        val source = document.text.replace("\r\n", "\n").lines()
        fun dp(value: Int) = ActionUi.dp(context, value)
        fun mutate(block: () -> String) { try { change(block()) } catch (e: Exception) { failure(e) } }
        fun groupName(group: ActionBlockDocument.Group): String = if (group.branch == "root")
            context.getString(R.string.action_block_root) else context.getString(R.string.action_block_location,
                group.path, context.getString(when (group.branch) {
                    "yes" -> R.string.action_branch_true
                    "no" -> R.string.action_branch_false
                    else -> R.string.action_block_body
                }))
        fun options(item: ActionBlockDocument.Item, index: Int, size: Int) {
            val entries = mutableListOf<Pair<Int, () -> Unit>>()
            if (index > 0) entries += R.string.action_edit_up to { mutate { document.move(item.line, -1) } }
            if (index + 1 < size) entries += R.string.action_edit_down to { mutate { document.move(item.line, 1) } }
            entries += R.string.action_block_move to {
                val choices = document.destinations(item.line)
                show(ActionUi.choices(context, context.getString(R.string.action_block_move), choices.map(::groupName)) { selected ->
                    mutate { document.moveTo(item.line, choices[selected].start) }
                })
            }
            entries += R.string.action_edit_duplicate to { mutate { document.duplicate(item.line) } }
            if (item.verb == "if") entries += (if (item.otherwise == null) R.string.action_block_add_else else R.string.action_block_remove_else) to {
                mutate { if (item.otherwise == null) document.addElse(item.line) else document.removeElse(item.line) }
            }
            entries += R.string.action_edit_remove to { mutate { document.remove(item.line) } }
            show(ActionUi.choices(context, item.path + ". " + source[item.line].trim(),
                entries.map { context.getString(it.first) }, danger = setOf(entries.lastIndex)) { selected -> entries[selected].second() })
        }
        // A macro is a listing: number in its own column, the line itself in a fixed pitch, and the
        // per-step menu at the right edge. Each step is a bordered row like the other tabs' rows;
        // nesting is a left rule, so depth survives past two levels.
        fun renderGroup(group: ActionBlockDocument.Group, container: LinearLayout) {
            group.items.forEachIndexed { index, item ->
                val line = source[item.line].trim()
                val row = ActionUi.card(context, vertical = false).apply {
                    setPadding(dp(10), dp(2), dp(2), dp(2))
                    foreground = ActionUi.pressable(context, 8)
                    isClickable = true
                    contentDescription = item.path + ". " + line
                    setOnClickListener { edit(item.line, source[item.line]) }
                }
                row.addView(ActionUi.label(context, item.path, 11f, ActionUi.mutedColor).apply {
                    gravity = Gravity.END
                    setPadding(0, 0, dp(10), 0)
                }, LinearLayout.LayoutParams(dp(34), -2))
                row.addView(ActionUi.label(context, line, 12f).apply {
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, dp(8))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(ActionUi.iconCell(context, "⋮", context.getString(R.string.action_block_options, item.path)) {
                    options(item, index, group.items.size)
                })
                ActionUi.add(container, row, gap = 8)
                listOfNotNull(item.body, item.otherwise).forEach { child ->
                    val nested = ActionUi.indent(context, container)
                    if (child.branch != "repeat") ActionUi.add(nested, ActionUi.caption(context, groupName(child)))
                    renderGroup(child, nested)
                }
            }
            ActionUi.add(container, ActionUi.pill(context, context.getString(R.string.action_edit_add)) {
                val labels = intArrayOf(R.string.action_edit_add, R.string.action_edit_add_repeat, R.string.action_edit_add_branch)
                val drafts = listOf("wait 800", "repeat 3", "if charging")
                show(ActionUi.choices(context, groupName(group), labels.map { context.getString(it) }) { position ->
                    add(group.start, drafts[position])
                })
            }, gap = 8)
        }
        renderGroup(document.root, parent)
    }
}
