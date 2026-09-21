package com.zerotoship.z2term.edge

import com.zerotoship.z2term.R

/** Contextual guidance follows the same component/behavior vocabulary as the editor. */
internal object EdgeItemHelp {
    fun behavior(selection: EdgeItemComponent.Selection): Int = when (selection.action) {
        "state_button" -> R.string.edge_help_state_button
        "receive" -> R.string.edge_help_receive
        "fixed" -> R.string.edge_help_fixed
        "read" -> R.string.edge_help_read
        "view" -> R.string.edge_help_view
        "value" -> if (selection.component == "choice") R.string.edge_help_choice else R.string.edge_help_value
        "file" -> R.string.edge_help_file
        "send" -> R.string.edge_help_send
        "shell" -> R.string.edge_help_shell
        "state" -> R.string.edge_help_state
        "select" -> R.string.edge_help_select
        else -> R.string.edge_help_execute
    }

    fun field(key: String): Int? = when (key) {
        "run" -> R.string.edge_help_command
        "every" -> R.string.edge_help_every
        "timeout" -> R.string.edge_help_timeout
        "file" -> R.string.edge_help_note_path
        "default" -> R.string.edge_help_default
        "choices" -> R.string.edge_help_choices
        "rows" -> R.string.edge_help_rows
        "width", "height" -> R.string.edge_help_size
        else -> null
    }

    fun example(key: String, selection: EdgeItemComponent.Selection): String? = when (key) {
        "run" -> when (selection.action) {
            "execute", "read" -> "date"
            "view" -> "z2-view \"\$HOME/page.html\""
            "send" -> "cat"
            "select" -> "printf 'One\\nTwo\\n'"
            else -> null
        }
        "state" -> "printf 'off\\n'"
        "on-select" -> "z2-toast \"\$1\""
        "choices" -> "one|two|three"
        "every" -> "30"
        "timeout" -> "60"
        "rows" -> "3"
        "width" -> "100%"
        "height" -> "360"
        "at" -> "50%,50%"
        else -> null
    }
}
