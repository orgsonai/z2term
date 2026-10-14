package com.zerotoship.z2term.viewer

/** Per-item presentation and refresh policy; independent of the content supplied by a macro. */
internal data class ViewerOptions(
    val automatic: Boolean = false,
    val showRefresh: Boolean = true,
    val showExpand: Boolean = true,
    val showFind: Boolean = false,
    /** Text size of the page in percent; a pinch changes it (WebSettings.textZoom). */
    val textZoom: Int = TEXT_ZOOM
) {
    fun interval(seconds: Long): Long = if (automatic) seconds else 0

    companion object {
        fun from(fields: Map<String, String>): ViewerOptions {
            fields["view-refresh"]?.let { require(it in setOf("manual", "auto")) { "view-refresh: manual|auto" } }
            for (key in listOf("view-refresh-button", "view-expand-button", "view-find-button")) {
                fields[key]?.let { require(it in setOf("on", "off")) { "$key: on|off" } }
            }
            fields["view-text-size"]?.let {
                require(it.toIntOrNull()?.let { n -> n in MIN_TEXT_ZOOM..MAX_TEXT_ZOOM } == true) { "view-text-size: $MIN_TEXT_ZOOM–$MAX_TEXT_ZOOM percent" }
            }
            // Find is new chrome on an existing panel, so it appears only when the user turns it on.
            return ViewerOptions(fields["view-refresh"] == "auto",
                fields["view-refresh-button"] != "off", fields["view-expand-button"] != "off",
                fields["view-find-button"] == "on", fields["view-text-size"]?.toInt() ?: TEXT_ZOOM)
        }

        const val TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 300
    }
}
