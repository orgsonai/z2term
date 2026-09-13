package com.zerotoship.z2term.edge

/** Recreates saved definitions using the public CLI; generating a backup never executes its actions. */
internal object EdgePanelCommands {
    fun generate(panels: List<EdgeStore.Panel>, selectedId: String): String {
        val selected = panels.firstOrNull { it.id == selectedId }
            ?: throw IllegalArgumentException("No panel: $selectedId")
        val parent = panels.firstOrNull { selectedId in it.tabs }
        return buildString {
            // Keep shell variables and fail-fast behavior local even when pasted into a terminal.
            appendLine("(")
            appendLine("set -e")
            if (parent != null) {
                appendLine("if z2_edge_parent=$(z2-edge get ${quote(parent.id)} 2>/dev/null); then")
                appendLine("  :")
                appendLine("else")
                appendLine(panelCommand(parent.copy(fields = parent.fields - "tabs")))
                appendLine("  z2_edge_parent=''")
                appendLine("fi")
            }
            // A parent may reference only existing children. Preserve their saved tab order.
            for (id in selected.tabs) {
                val child = panels.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("Missing tab: $id")
                appendPanel(child)
            }
            appendPanel(selected)
            if (parent != null) {
                appendLine("z2_edge_tabs=$(printf '%s\\n' \"\$z2_edge_parent\" | sed -n 's/^tabs=//p')")
                appendLine("case ,\"\$z2_edge_tabs\", in")
                appendLine("  *,${quote(selected.id)},*) ;;")
                appendLine("  *) z2-edge panel ${quote(parent.id)} \"tabs=\${z2_edge_tabs:+\$z2_edge_tabs,}${selected.id}\" ;;")
                appendLine("esac")
            }
            appendLine(")")
        }
    }

    private fun StringBuilder.appendPanel(panel: EdgeStore.Panel) {
        appendLine(panelCommand(panel))
        panel.items.forEach { item ->
            val fields = item.fields.ifEmpty { mapOf("type" to item.type) }
            appendLine(command("set", "${panel.id}:${item.id}", fields))
        }
    }

    private fun panelCommand(panel: EdgeStore.Panel): String = command("panel", panel.id,
        panel.fields.ifEmpty { mapOf("handle" to panel.handle) })

    private fun command(verb: String, target: String, fields: Map<String, String>): String =
        "z2-edge $verb ${quote(target)}" + fields.entries.joinToString("") {
            " \\\n  ${quote("${it.key}=${it.value}")}"
        }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
