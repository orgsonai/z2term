package com.zerotoship.z2term.edge

/** Shared tab geometry is decided by the whole group, never by the selected page. */
internal object EdgePanelLayout {
    val interactiveTypes = setOf("terminal", "macro", "argument", "result")

    fun bounded(root: EdgeStore.Panel, panels: List<EdgeStore.Panel>): Boolean {
        val ids = root.tabs.toSet() + root.id
        return panels.any { it.id in ids && it.items.any { item -> item.type in interactiveTypes } }
    }

    fun label(item: EdgeStore.Item, applicationName: String? = null): String =
        item.fields["label"]?.trim() ?: if (item.type == "run") applicationName ?: item.id else ""

}
