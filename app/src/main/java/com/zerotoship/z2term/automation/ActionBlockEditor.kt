package com.zerotoship.z2term.automation

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeSettingsUi

/** The source model owns moves; delimiters never appear as independently movable rows. */
internal object ActionBlockEditor {
    fun render(context: Context, parent: LinearLayout, document: ActionBlockDocument,
        edit: (Int, String) -> Unit, add: (Int, String) -> Unit, change: (String) -> Unit,
        show: (AlertDialog) -> Unit, failure: (Exception) -> Unit) {
        val source = document.text.replace("\r\n", "\n").lines()
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
                show(AlertDialog.Builder(context).setTitle(R.string.action_block_move)
                    .setItems(choices.map(::groupName).toTypedArray()) { _, selected ->
                        mutate { document.moveTo(item.line, choices[selected].start) }
                    }.setNegativeButton(android.R.string.cancel, null).create())
            }
            entries += R.string.action_edit_duplicate to { mutate { document.duplicate(item.line) } }
            if (item.verb == "if") entries += (if (item.otherwise == null) R.string.action_block_add_else else R.string.action_block_remove_else) to {
                mutate { if (item.otherwise == null) document.addElse(item.line) else document.removeElse(item.line) }
            }
            entries += R.string.action_edit_remove to { mutate { document.remove(item.line) } }
            show(AlertDialog.Builder(context).setTitle(item.path + ". " + source[item.line].trim())
                .setItems(entries.map { context.getString(it.first) }.toTypedArray()) { _, selected -> entries[selected].second() }
                .setNegativeButton(android.R.string.cancel, null).create())
        }
        // A macro is a listing: number in its own column, the line itself in a fixed pitch, and the
        // per-step menu at the right edge. Nesting is a left rule, so depth survives past two levels.
        fun renderGroup(group: ActionBlockDocument.Group, container: LinearLayout) {
            group.items.forEachIndexed { index, item ->
                container.addView(EdgeSettingsUi.hairline(context))
                val row = EdgeSettingsUi.row(context).apply {
                    minimumHeight = EdgeSettingsUi.dp(context, 48)
                    setPadding(EdgeSettingsUi.dp(context, 12), EdgeSettingsUi.dp(context, 6),
                        EdgeSettingsUi.dp(context, 4), EdgeSettingsUi.dp(context, 6))
                    background = EdgeSettingsUi.ripple(context, null)
                    isClickable = true
                    contentDescription = item.path + ". " + source[item.line].trim()
                    setOnClickListener { edit(item.line, source[item.line]) }
                }
                row.addView(EdgeSettingsUi.index(context, item.path).apply {
                    setPadding(0, 0, EdgeSettingsUi.dp(context, 10), 0)
                }, LinearLayout.LayoutParams(EdgeSettingsUi.dp(context, 34), -2))
                row.addView(EdgeSettingsUi.mono(context, source[item.line].trim()).apply {
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(EdgeSettingsUi.iconButton(context, "⋮") { options(item, index, group.items.size) }.apply {
                    contentDescription = context.getString(R.string.action_block_options, item.path)
                }, LinearLayout.LayoutParams(EdgeSettingsUi.dp(context, 40), EdgeSettingsUi.dp(context, 40)))
                container.addView(row)
                listOfNotNull(item.body, item.otherwise).forEach { child ->
                    val nested = EdgeSettingsUi.indent(context, container)
                    if (child.branch != "repeat") nested.addView(EdgeSettingsUi.caption(context, groupName(child)).apply {
                        setPadding(EdgeSettingsUi.dp(context, 12), EdgeSettingsUi.dp(context, 8), 0, 0)
                    })
                    renderGroup(child, nested)
                }
            }
            container.addView(EdgeSettingsUi.button(context, context.getString(R.string.action_edit_add)) {
                val labels = intArrayOf(R.string.action_edit_add, R.string.action_edit_add_repeat, R.string.action_edit_add_branch)
                val drafts = listOf("wait 800", "repeat 3", "if charging")
                show(AlertDialog.Builder(context).setTitle(groupName(group))
                    .setItems(labels.map { context.getString(it) }.toTypedArray()) { _, position -> add(group.start, drafts[position]) }
                    .setNegativeButton(android.R.string.cancel, null).create())
            }, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(EdgeSettingsUi.dp(context, 12), EdgeSettingsUi.dp(context, 10),
                    EdgeSettingsUi.dp(context, 12), EdgeSettingsUi.dp(context, 10))
            })
        }
        renderGroup(document.root, parent)
    }
}
