package com.zerotoship.z2term.edge

import java.io.File

/** Plain UTF-8 definitions: edge/<panel>/panel.conf and edge/<panel>/<item>.item. */
class EdgeStore(val root: File) {
    data class Item(val id: String, val fields: Map<String, String>) {
        val type get() = fields["type"] ?: "run"
        val command get() = fields["run"].orEmpty()
        val isStateButton get() = type == "run" && fields["button-state"] == "on"
        fun actionCommand(on: Boolean) = if (isStateButton && on) fields["off"].orEmpty().ifBlank { command } else command
        val order get() = fields["order"]?.toIntOrNull() ?: 0
        val every get() = fields["every"]?.toLongOrNull() ?: 0L
        val timeout get() = fields["timeout"]?.toLongOrNull() ?: 30L
    }
    data class Panel(val id: String, val fields: Map<String, String>, val items: List<Item>) {
        val handle get() = fields["handle"] ?: "off"
        val tabs get() = fields["tabs"].orEmpty().split(',').filter { it.isNotBlank() }
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
        .also { require(it.size <= 64) { "At most 64 panels" }; validateTabs(it) }

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
        if (!dir.exists()) require(panels().size < 64) { "At most 64 panels" }
        val f = File(dir, "panel.conf")
        val merged = read(f) + values
        validatePanel(merged)
        val all = panels().filter { it.id != id } + Panel(id, merged, emptyList())
        validateTabs(all)
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

    /** Replace an explicitly edited draft, rejecting stale edits and accidental ID reuse. */
    @Synchronized fun saveItemDraft(target: String, values: Map<String, String>, expected: Map<String, String>?) {
        val (panelId, itemId) = target(target)
        val parent = panel(panelId)
        val existing = parent.items.firstOrNull { it.id == itemId }
        check(existing?.fields == expected) { "Item changed outside this editor; reopen it before saving" }
        require(existing != null || parent.items.size < 64) { "At most 64 items" }
        validateItem(values)
        writeAtomic(File(directory(panelId), "$itemId.item"), encode(values))
    }

    @Synchronized fun removeItem(target: String) {
        val (panelId, itemId) = target(target)
        val f = File(directory(panelId), "$itemId.item")
        require(f.isFile) { "No item: $target" }
        check(f.delete()) { "Cannot remove $target" }
        File(directory(panelId), "$itemId.button-state").delete()
    }

    /** Remember successful actions, tied to the command definition so edits never reuse stale state. */
    @Synchronized fun buttonState(panelId: String, item: Item): Boolean? {
        val data = read(File(directory(panelId), "${item.id}.button-state"))
        if (data["signature"] != buttonSignature(item)) return null
        return parseButtonState(data["value"].orEmpty())
    }

    @Synchronized fun buttonSource(panelId: String, item: Item): String? {
        val data = read(File(directory(panelId), "${item.id}.button-state"))
        return data["source"]?.takeIf { data["signature"] == buttonSignature(item) && it in setOf("torch", "screen") }
    }

    @Synchronized fun saveButtonState(panelId: String, item: Item, on: Boolean, source: String? = null): Boolean {
        require(source == null || source in setOf("torch", "screen"))
        if (runCatching { this.item("$panelId:${item.id}") }.getOrNull() != item) return false
        writeAtomic(File(directory(panelId), "${item.id}.button-state"),
            "signature=${buttonSignature(item)}\nvalue=${if (on) "on" else "off"}\n" +
                (source?.let { "source=$it\n" } ?: ""))
        return true
    }

    private fun buttonSignature(item: Item): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(encode(item.fields.filterKeys { it in setOf("type", "run", "off", "state", "button-state", "button-source") }.toSortedMap()).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun noteFile(panelId: String, item: Item): File {
        require(item.type == "note") { "Not a note" }
        val raw = item.fields["file"].orEmpty()
        val home = root.parentFile!!.parentFile!!
        return (when {
            raw.isBlank() -> File(directory(panelId), "${item.id}.txt")
            raw.startsWith("~/") -> File(home, raw.removePrefix("~/"))
            File(raw).isAbsolute -> File(raw)
            else -> File(home, raw)
        }).canonicalFile
    }

    fun noteHistoryFile(file: File): File {
        val path = file.canonicalFile
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest(path.path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        // Internal histories are deleted with their panel; external notes keep theirs.
        val relative = path.relativeToOrNull(root.canonicalFile)?.path.orEmpty()
        val panelId = relative.substringBefore(File.separatorChar)
        val parent = if (validId(panelId) && File(root, panelId).isDirectory) File(root, panelId) else root
        return File(parent, ".note-history/$key")
    }

    @Synchronized fun moveItem(panelId: String, itemId: String, beforeId: String, after: Boolean = false) {
        val items = panel(panelId).items
        require(items.any { it.id == itemId } && items.any { it.id == beforeId }) { "No item to move" }
        if (itemId == beforeId) return
        val ordered = items.toMutableList()
        val moved = ordered.first { it.id == itemId }
        ordered.remove(moved)
        ordered.add(ordered.indexOfFirst { it.id == beforeId } + if (after) 1 else 0, moved)
        ordered.forEachIndexed { index, item -> setItem("$panelId:${item.id}", mapOf("order" to index.toString())) }
    }

    /** Saves a whole arrangement: every item of the tab, row by row, numbered from 1 in reading order. */
    @Synchronized fun arrange(panelId: String, rows: List<List<String>>) {
        val items = panel(panelId).items
        val ids = rows.flatten()
        require(ids.size == ids.toSet().size && ids.toSet() == items.map { it.id }.toSet()) {
            "Arrangement must list every item once"
        }
        require(rows.size <= 64 && rows.none { it.isEmpty() }) { "row: 1–64" }
        var order = 0
        rows.forEachIndexed { index, line -> line.forEach { id ->
            setItem("$panelId:$id", mapOf("order" to (order++).toString(), "row" to (index + 1).toString()))
        } }
    }

    @Synchronized fun addTab(parentId: String, id: String, label: String) {
        val parent = panel(parentId)
        require(!directory(id).exists()) { "Panel already exists: $id" }
        require(panels().none { parentId in it.tabs }) { "Nested tabs are not supported" }
        setPanel(id, mapOf("label" to label, "handle" to "off"))
        try { setPanel(parentId, mapOf("tabs" to (parent.tabs + id).joinToString(","))) }
        catch (e: Exception) { removePanel(id); throw e }
    }

    @Synchronized fun removePanel(id: String): Int {
        val dir = directory(id)
        require(dir.isDirectory) { "No panel: $id" }
        // Detach only references to this panel; deleting a parent preserves its child panels.
        panels().filter { id in it.tabs }.forEach { parent ->
            setPanel(parent.id, mapOf("tabs" to parent.tabs.filter { it != id }.joinToString(",")))
        }
        val count = dir.listFiles().orEmpty().count { it.isFile && it.extension == "item" }
        // Do not follow links inside the panel into user-owned directories.
        java.nio.file.Files.walk(dir.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.delete(it) }
        }
        return count
    }

    /**
     * Delete several panels at once. A listed parent takes every tab it owns with it; a listed tab
     * leaves its parent and siblings in place. Returns the number of items deleted.
     */
    @Synchronized fun removePanels(ids: Collection<String>): Int {
        val all = panels()
        val byId = all.associateBy { it.id }
        ids.forEach { id -> require(byId.containsKey(id)) { "No panel: $id" } }
        val targets = ids.flatMap { id -> listOf(id) + byId.getValue(id).tabs }.toSet()
        val tabs = all.flatMap { it.tabs }.toSet()
        // Tabs first: if a later deletion fails, the parent still exists with a consistent tab list
        // instead of leaving its remaining tabs behind as separate panels.
        return targets.sortedBy { if (it in tabs) 0 else 1 }.sumOf { removePanel(it) }
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
        fun parseButtonState(value: String): Boolean? = when (value.trim()) {
            "on", "true", "1" -> true
            "off", "false", "0" -> false
            else -> null
        }
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
            val allowed = setOf("type", "label", "icon", "run", "off", "button-state", "button-source", "state", "on-select", "order", "every", "timeout", "out", "file", "note-lines", "note-size", "note-background", "note-color", "args", "result", "result-controls", "argument-kind", "default", "choices", "required", "rows", "stdin", "width", "height", "align", "at", "view-refresh", "view-refresh-button", "view-expand-button", "row")
            require(values.keys.all { it in allowed }) { "Unknown item field: ${values.keys - allowed}" }
            val type = values["type"] ?: "run"
            require(type in setOf("run", "text", "toggle", "list", "input", "note", "terminal", "macro", "argument", "result", "view")) { "Unsupported type: $type" }
            values["result-controls"]?.let { require(it in setOf("on", "off")) { "result-controls: on|off" } }
            values["button-state"]?.let { require(it in setOf("on", "off")) { "button-state: on|off" } }
            values["button-source"]?.let { require(it in EdgeButtonSource.choices) { "button-source: auto|torch|screen|process|remember" } }
            listOf("note-background", "note-color").forEach { key ->
                values[key]?.let { require(it.isEmpty() || EdgeNoteColor.parse(it) != null) { "$key: #RRGGBB or empty" } }
            }
            values["note-lines"]?.let { require(it in setOf("on", "off")) { "note-lines: on|off" } }
            values["note-size"]?.let {
                require(it.toIntOrNull()?.let { n -> n in 10..32 } == true) { "note-size: 10–32 sp" }
            }
            require(values["out"].orEmpty() in setOf("", "none", "panel", "toast", "notify")) { "Unknown out" }
            values["every"]?.let { require(it.toLongOrNull()?.let { n -> n == 0L || n in 5..86400 } == true) { "every: 0 or 5–86400 seconds" } }
            values["timeout"]?.let { require(it.toLongOrNull()?.let { n -> n in 1L..300L } == true) { "timeout: 1–300 seconds" } }
            values["order"]?.let { require(it.toIntOrNull() != null) { "order must be an integer" } }
            EdgeItemLayout.validate(values)
            EdgeRows.validate(values)
            EdgeMacroForm.validate(values)
            com.zerotoship.z2term.viewer.ViewerOptions.from(values)
            encode(values)
        }

        fun validatePanel(values: Map<String, String>) {
            val allowed = setOf("label", "handle", "bar-color", "side", "offset", "length", "x", "y", "size", "run", "alpha", "open", "width", "height", "tabs", "layout", "title", "close", "tabbar", "add", "settings", "labels", "fit", "place", "at", "flow", "columns", "icon-size", "tools-place", "gesture-up", "gesture-down", "gesture-double-tap", "gesture-scroll", "gesture-speed", "gesture-range", "scroll-x", "scroll-y", "scroll-how") + EdgeActions.Trigger.entries.map { it.key }
            EdgeActions.Trigger.entries.forEach { trigger -> values[trigger.key]?.let { EdgeActions.decode(it) } }
            listOf("scroll-x", "scroll-y").forEach { key -> values[key]?.let { raw ->
                require(raw.toFloatOrNull()?.let { it.isFinite() && it in 10f..90f } == true) { "$key: 10–90 percent" }
            } }
            require(values.keys.all { it in allowed }) { "Unknown panel field: ${values.keys - allowed}" }
            require(values["handle"].orEmpty() in setOf("", "off", "bar", "button")) { "handle: bar, button or off" }
            require(values["bar-color"].orEmpty() in setOf("", "auto", "white", "black")) { "bar-color: auto|white|black" }
            require(values["side"].orEmpty() in setOf("", "left", "right")) { "side: left or right" }
            listOf("offset", "length", "x", "y").forEach { key -> values[key]?.let {
                require(it.toFloatOrNull()?.let { n -> n.isFinite() && n in 0f..100f } == true) { "$key: 0–100 percent" }
            } }
            values["size"]?.let { require(it.toIntOrNull()?.let { n -> n in 2..96 } == true) { "size: 2–96 dp" } }
            require(values["gesture-scroll"].orEmpty() in setOf("", "off", "variable", "fixed")) {
                "gesture-scroll: off|variable|fixed"
            }
            // ⚠ 空欄は auto。既定でスワイプを注入しない (0.8.627・[AndroidNodeScroll] 参照)。
            require(values["scroll-how"].orEmpty() in setOf("", "auto", "node", "swipe")) {
                "scroll-how: auto|node|swipe"
            }
            values["gesture-range"]?.let {
                require(it.toIntOrNull()?.let { n -> n in 32..2000 } == true) { "gesture-range: 32–2000 dp" }
            }
            values["gesture-speed"]?.let {
                require(it.toIntOrNull()?.let { n -> n in 50..40000 } == true) { "gesture-speed: 50–40000 dp/s" }
            }
            require(values["open"].orEmpty() in setOf("", "tap", "swipe", "both")) { "open: tap, swipe or both" }
            values["alpha"]?.let { require(it.toFloatOrNull()?.let { n -> n.isFinite() && n in 0.05f..1f } == true) { "alpha: 0.05–1" } }
            require(values["layout"].orEmpty() in setOf("", "list", "grid")) { "layout: list|grid" }
            listOf("title", "close", "add", "settings", "labels").forEach { key ->
                require(values[key].orEmpty() in setOf("", "on", "off")) { "$key: on|off" }
            }
            require(values["tabbar"].orEmpty() in setOf("", "on", "off", "auto")) { "tabbar: on|off|auto" }
            require(values["fit"].orEmpty() in setOf("", "content", "fixed")) { "fit: content|fixed" }
            require(values["place"].orEmpty() in setOf("", "handle", "left", "right", "top", "bottom", "center")) { "place: handle|left|right|top|bottom|center" }
            require(values["tools-place"].orEmpty() in setOf("", "top", "bottom")) { "tools-place: top|bottom" }
            require(values["flow"].orEmpty() in setOf("", "vertical", "horizontal", "grid", "free")) { "flow: vertical|horizontal|grid|free" }
            values["columns"]?.let { require(it == "auto" || it.toIntOrNull()?.let { n -> n in 1..16 } == true) { "columns: auto|1–16" } }
            values["icon-size"]?.let { require(it.toIntOrNull()?.let { n -> n in 16..192 } == true) { "icon-size: 16–192 dp" } }
            values["at"]?.takeIf { it.isNotBlank() }?.let { point ->
                val parts = point.split(',')
                require(parts.size == 2 && parts.all { part ->
                    part.endsWith('%') && part.removeSuffix("%").toFloatOrNull()?.let { it.isFinite() && it in 0f..100f } == true
                }) { "at: X%,Y% (0–100)" }
            }
            values["tabs"]?.takeIf { it.isNotEmpty() }?.let { raw ->
                val ids = raw.split(',')
                require(ids.all(::validId) && ids.distinct().size == ids.size) { "tabs: distinct panel IDs separated by commas" }
            }
            listOf("width", "height").forEach { key -> values[key]?.let { dimension(it) } }
            encode(values)
        }

        private fun validateTabs(panels: List<Panel>) {
            val byId = panels.associateBy { it.id }
            val used = mutableSetOf<String>()
            panels.forEach { parent -> parent.tabs.forEach { id ->
                require(id != parent.id && byId.containsKey(id)) { "Missing or self-referencing tab: $id" }
                require(byId.getValue(id).tabs.isEmpty()) { "Nested tabs are not supported: $id" }
                require(used.add(id)) { "Tab already belongs to another panel: $id" }
            } }
        }

        /** A positive dp value, or a percentage of the usable display area. */
        fun dimension(raw: String): Pair<Float, Boolean> {
            val percent = raw.endsWith('%')
            val value = raw.removeSuffix("%").toFloatOrNull()
            require(value != null && value.isFinite() && value > 0f &&
                value <= (if (percent) 100f else 10000f)) { "width/height: >0–100% or >0–10000 dp" }
            return value to percent
        }

        fun dimensionPixels(raw: String, available: Int, density: Float): Int {
            val (value, percent) = dimension(raw)
            return (if (percent) available * (value / 100f) else value * density)
                .toInt().coerceIn(1, available.coerceAtLeast(1))
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
