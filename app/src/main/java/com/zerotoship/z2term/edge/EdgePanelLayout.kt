package com.zerotoship.z2term.edge

/** Shared tab geometry is decided by the whole group, never by the selected page. */
internal object EdgePanelLayout {
    val interactiveTypes = setOf("terminal", "macro", "argument", "result")

    fun bounded(root: EdgeStore.Panel, panels: List<EdgeStore.Panel>): Boolean {
        val ids = root.tabs.toSet() + root.id
        return root.fields["flow"] == "free" || panels.any { it.id in ids && it.items.any { item -> item.type in interactiveTypes || EdgeItemComponent.linked(item) } }
    }

    fun label(item: EdgeStore.Item, applicationName: String? = null, buttonLabel: String = "▶"): String {
        val label = item.fields["label"]?.trim()
        if (!label.isNullOrBlank()) return label
        if (item.type != "run") return ""
        // An icon can stand alone, but a button must retain either an icon or a name.
        if (label != null && (!item.fields["icon"].isNullOrBlank() || !applicationName.isNullOrBlank())) return ""
        return applicationName?.takeIf { it.isNotBlank() } ?: buttonLabel
    }

}
