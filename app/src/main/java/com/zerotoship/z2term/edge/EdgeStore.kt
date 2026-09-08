package com.zerotoship.z2term.edge

import java.io.File

/** Plain UTF-8 definitions: edge/<panel>/panel.conf and edge/<panel>/<item>.item. */
class EdgeStore(val root: File) {
    data class Item(val id: String, val fields: Map<String, String>) {
        val type get() = fields["type"] ?: "run"
        val command get() = fields["run"].orEmpty()
        val order get() = fields["order"]?.toIntOrNull() ?: 0
        val every get() = fields["every"]?.toLongOrNull() ?: 0L
        val timeout get() = fields["timeout"]?.toLongOrNull() ?: 30L
    }
    data class Panel(val id: String, val fields: Map<String, String>, val items: List<Item>) {
        val handle get() = fields["handle"] ?: "off"
    }

    fun enabled(): Boolean = File(root, "enabled").isFile

    @Synchronized fun enable(on: Boolean) {
        root.mkdirs()
        val f = File(root, "enabled")
        if (on) writeAtomic(f, "1\n") else check(!f.exists() || f.delete()) { "Cannot disable edge" }
    }

    @Synchronized fun panels(): List<Panel> = root.listFiles().orEmpty()
        .filter { it.isDirectory && validId(it.name) }
        .sortedBy { it.name }.map { panel(it.name) }
        .also { require(it.size <= 12) { "At most 12 panels" } }

    @Synchronized fun panel(id: String): Panel {
        val dir = directory(id)
        require(dir.isDirectory) { "No panel: $id" }
        val fields = read(File(dir, "panel.conf"))
        validatePanel(fields)
        val items = dir.listFiles().orEmpty().filter { it.isFile && it.extension == "item" }
            .map { f ->
                require(validId(f.nameWithoutExtension)) { "Invalid item ID: ${f.name}" }
                val values = read(f)
                validateItem(values)
                Item(f.nameWithoutExtension, values)
            }.sortedWith(compareBy<Item> { it.order }.thenBy { it.id })
        require(items.size <= 64) { "At most 64 items per panel" }
        return Panel(id, fields, items)
    }

    @Synchronized fun setPanel(id: String, values: Map<String, String>): Panel {
        val dir = directory(id)
        if (!dir.exists()) require(panels().size < 12) { "At most 12 panels" }
        val f = File(dir, "panel.conf")
        val merged = read(f) + values
        validatePanel(merged)
        writeAtomic(f, encode(merged))
        return panel(id)
    }

    @Synchronized fun setItem(target: String, values: Map<String, String>) {
        val (panelId, itemId) = target(target)
        val panel = panel(panelId)
        require(panel.items.any { it.id == itemId } || panel.items.size < 64) { "At most 64 items" }
        val f = File(directory(panelId), "$itemId.item")
        val merged = read(f) + values
        validateItem(merged)
        writeAtomic(f, encode(merged))
    }

    @Synchronized fun removeItem(target: String) {
        val (panelId, itemId) = target(target)
        val f = File(directory(panelId), "$itemId.item")
        require(f.isFile) { "No item: $target" }
        check(f.delete()) { "Cannot remove $target" }
    }

    fun item(target: String): Item {
        val (panelId, itemId) = target(target)
        return panel(panelId).items.firstOrNull { it.id == itemId }
            ?: throw IllegalArgumentException("No item: $target")
    }

    fun directory(id: String): File {
        require(validId(id)) { "ID must use letters, digits, _ or - (1–64 characters)" }
        return File(root, id).also {
            require(it.canonicalFile.parentFile == root.canonicalFile) { "Panel must be inside edge directory" }
        }
    }

    companion object {
        fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,64}"))
        fun target(raw: String): Pair<String, String> {
            val parts = raw.split(':')
            require(parts.size == 2 && parts.all(::validId)) { "Use panel:item" }
            return parts[0] to parts[1]
        }

        fun read(file: File): Map<String, String> {
            if (!file.exists()) return emptyMap()
            require(file.length() <= 65536) { "Definition too large: ${file.name}" }
            return parse(file.readText())
        }

        fun parse(text: String): Map<String, String> {
            val values = linkedMapOf<String, String>()
            text.lineSequence().forEachIndexed { index, line ->
                if (line.isBlank() || line.trimStart().startsWith('#')) return@forEachIndexed
                require('=' in line) { "Expected key=value on line ${index + 1}" }
                val key = line.substringBefore('=').trim()
                require(key.matches(Regex("[a-z][a-z-]*"))) { "Invalid field: $key" }
                require(!values.containsKey(key)) { "Duplicate field: $key" }
                values[key] = line.substringAfter('=')
            }
            return values
        }

        fun encode(values: Map<String, String>): String = values.entries.joinToString("\n", postfix = "\n") {
            require(!it.value.contains('\n') && !it.value.contains('\r') && !it.value.contains('\u0000')) {
                "Definition values must fit on one line"
            }
            "${it.key}=${it.value}"
        }

        fun validateItem(values: Map<String, String>) {
            val allowed = setOf("type", "label", "icon", "run", "state", "on-select", "order", "every", "timeout", "out")
            require(values.keys.all { it in allowed }) { "Unknown item field: ${values.keys - allowed}" }
            val type = values["type"] ?: "run"
            require(type in setOf("run", "text", "toggle", "list", "input")) { "Unsupported type: $type" }
            require(values["out"].orEmpty() in setOf("", "none", "panel", "toast", "notify")) { "Unknown out" }
            values["every"]?.let { require(it.toLongOrNull()?.let { n -> n == 0L || n in 5..86400 } == true) { "every: 0 or 5–86400 seconds" } }
            values["timeout"]?.let { require(it.toLongOrNull()?.let { n -> n in 1L..300L } == true) { "timeout: 1–300 seconds" } }
            values["order"]?.let { require(it.toIntOrNull() != null) { "order must be an integer" } }
            encode(values)
        }

        fun validatePanel(values: Map<String, String>) {
            val allowed = setOf("label", "handle", "side", "offset", "length", "x", "y", "size", "run", "alpha", "open")
            require(values.keys.all { it in allowed }) { "Unknown panel field: ${values.keys - allowed}" }
            require(values["handle"].orEmpty() in setOf("", "off", "bar", "button")) { "handle: bar, button or off" }
            require(values["side"].orEmpty() in setOf("", "left", "right")) { "side: left or right" }
            listOf("offset", "length", "x", "y").forEach { key -> values[key]?.let {
                require(it.toFloatOrNull()?.let { n -> n.isFinite() && n in 0f..100f } == true) { "$key: 0–100 percent" }
            } }
            values["size"]?.let { require(it.toIntOrNull()?.let { n -> n in 2..96 } == true) { "size: 2–96 dp" } }
            require(values["open"].orEmpty() in setOf("", "tap", "swipe", "both")) { "open: tap, swipe or both" }
            values["alpha"]?.let { require(it.toFloatOrNull()?.let { n -> n.isFinite() && n in 0.05f..1f } == true) { "alpha: 0.05–1" } }
            encode(values)
        }

        private fun writeAtomic(file: File, text: String) {
            require(text.toByteArray().size <= 65536) { "Definition too large" }
            file.parentFile!!.mkdirs()
            val tmp = File.createTempFile(".edge-", ".tmp", file.parentFile)
            try {
                tmp.writeText(text)
                check(tmp.renameTo(file)) { "Cannot save ${file.name}" }
            } finally { tmp.delete() }
        }
    }
}
