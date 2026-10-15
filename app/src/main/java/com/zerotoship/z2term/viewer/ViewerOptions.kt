package com.zerotoship.z2term.viewer

/** Per-item presentation and refresh policy; independent of the content supplied by a macro. */
internal data class ViewerOptions(
    val automatic: Boolean = false,
    val showRefresh: Boolean = true,
    val showExpand: Boolean = true,
    val showFind: Boolean = false,
    /** Text size of the page in percent; a text pinch changes it (WebSettings.textZoom). */
    val textZoom: Int = TEXT_ZOOM,
    /** A pinch zooms the whole page (WebView zoom) instead of sizing the text. */
    val pagePinch: Boolean = false
) {
    fun interval(seconds: Long): Long = if (automatic) seconds else 0

    companion object {
        fun from(fields: Map<String, String>): ViewerOptions {
            fields["view-refresh"]?.let { require(it in setOf("manual", "auto")) { "view-refresh: manual|auto" } }
            for (key in listOf("view-refresh-button", "view-expand-button", "view-find-button")) {
                fields[key]?.let { require(it in setOf("on", "off")) { "$key: on|off" } }
            }
            fields["view-text-size"]?.let { textZoom(it, "view-text-size") }
            fields["view-pinch"]?.let { pagePinch(it, "view-pinch") }
            // Find is new chrome on an existing panel, so it appears only when the user turns it on.
            return ViewerOptions(fields["view-refresh"] == "auto",
                fields["view-refresh-button"] != "off", fields["view-expand-button"] != "off",
                fields["view-find-button"] == "on", fields["view-text-size"]?.toInt() ?: TEXT_ZOOM,
                fields["view-pinch"] == "page")
        }

        /** Shared by the item field and `z2-view --text-size`. */
        fun textZoom(value: String, name: String): Int = value.toIntOrNull()?.takeIf { it in MIN_TEXT_ZOOM..MAX_TEXT_ZOOM }
            ?: throw IllegalArgumentException("$name: $MIN_TEXT_ZOOM–$MAX_TEXT_ZOOM percent")

        /** Shared by the item field and `z2-view --pinch`: `text` (default) sizes the text, `page` zooms the page. */
        fun pagePinch(value: String, name: String): Boolean {
            require(value in setOf("text", "page")) { "$name: text|page" }
            return value == "page"
        }

        /** `z2-view --no-refresh / --no-find`, sent as a comma list: the full screen shows both unless told not to. */
        fun hiddenButtons(value: String): Set<String> {
            val names = value.split(',').filter { it.isNotEmpty() }.toSet()
            require(names.all { it in setOf("refresh", "find") }) { "hide: refresh|find" }
            return names
        }

        const val TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 300
    }
}
