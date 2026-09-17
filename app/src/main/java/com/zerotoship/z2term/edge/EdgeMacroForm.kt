package com.zerotoship.z2term.edge

/** References are local to a panel. Argument order is independent of visual order. */
internal object EdgeMacroForm {
    fun arguments(raw: String): List<String> = if (raw.isBlank()) emptyList() else raw.split(',').map { it.trim() }.also {
        require(it.size <= 16 && it.all(EdgeStore::validId)) { "args: up to 16 item IDs, separated by commas" }
    }

    fun choices(raw: String): List<String> = raw.split('|').map { it.trim() }.also {
        require(it.size <= 100 && it.all { value -> value.isNotEmpty() } && it.distinct().size == it.size) {
            "choices: unique nonempty values separated by | (up to 100)"
        }
    }

    fun validate(fields: Map<String, String>) {
        fields["args"]?.let(::arguments)
        fields["stdin"]?.let { require(it.isEmpty() || EdgeStore.validId(it)) { "stdin: an item ID in this panel" } }
        fields["result"]?.let { require(it.isEmpty() || EdgeStore.validId(it)) { "result: an item ID in this panel" } }
        fields["argument-kind"]?.let { require(it in setOf("text", "choice", "fixed")) { "argument-kind: text|choice|fixed" } }
        fields["required"]?.let { require(it in setOf("on", "off")) { "required: on|off" } }
        fields["rows"]?.let { require(it.toIntOrNull() in 1..20) { "rows: 1–20" } }
        if (fields["type"] == "argument" && fields["argument-kind"] == "choice") {
            val choices = choices(fields["choices"].orEmpty())
            require(fields["default"].isNullOrEmpty() || fields["default"] in choices) { "default must be one of choices" }
        }
    }

    fun resolve(item: EdgeStore.Item, items: List<EdgeStore.Item>, value: (String) -> String?): List<String> {
        val output = item.fields["result"].orEmpty()
        require(output.isEmpty() || items.any { it.id == output && it.type == "result" }) { "Missing result box: $output" }
        return arguments(item.fields["args"].orEmpty()).map { id -> read(id, items, value) }
            .also { values -> require(values.sumOf { it.toByteArray(Charsets.UTF_8).size } <= 65536) { "Arguments exceed 64 KiB" } }
    }

    fun stdin(item: EdgeStore.Item, items: List<EdgeStore.Item>, value: (String) -> String?): String? =
        item.fields["stdin"]?.takeIf { it.isNotEmpty() }?.let { read(it, items, value) }

    private fun read(id: String, items: List<EdgeStore.Item>, value: (String) -> String?): String {
        val source = items.firstOrNull { it.id == id && EdgeItemComponent.source(it) }
            ?: throw IllegalArgumentException("Missing input source: $id")
        val text = value(id) ?: throw IllegalArgumentException("Input is not visible: $id")
        require('\u0000' !in text) { "Input contains NUL: $id" }
        require(source.fields["required"] != "on" || text.isNotBlank()) {
            "${source.fields["label"] ?: id}: input required"
        }
        require(text.toByteArray(Charsets.UTF_8).size <= 65536) { "Input exceeds 64 KiB: $id" }
        return text
    }
}
