package com.zerotoship.z2term.edge

import com.zerotoship.z2term.R

internal object EdgeComponentLabels {
    fun item(context: android.content.Context, item: EdgeStore.Item): String =
        item.fields["label"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(component(EdgeItemComponent.from(item).component))

    fun component(value: String): Int = when (value) {
        "display" -> R.string.edge_component_display
        "entry" -> R.string.edge_component_entry
        "choice" -> R.string.edge_component_choice
        "switch" -> R.string.edge_component_switch
        "list" -> R.string.edge_component_list
        else -> R.string.edge_component_button
    }
    fun action(value: String): Int = when (value) {
        "state_button" -> R.string.edge_button_state
        "receive" -> R.string.edge_action_receive
        "fixed" -> R.string.edge_action_fixed
        "read" -> R.string.edge_action_read
        "view" -> R.string.viewer_component
        "value" -> R.string.edge_action_value
        "file" -> R.string.edge_action_file
        "send" -> R.string.edge_action_send
        "shell" -> R.string.edge_action_shell
        "state" -> R.string.edge_action_state
        "select" -> R.string.edge_action_select
        else -> R.string.edge_action_execute
    }
}
