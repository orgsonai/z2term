package com.zerotoship.z2term.share

/** Paths in a receipt are relative to the shared shell HOME, never executable fragments. */
internal object SharedPayload {
    fun kind(text: String, files: List<String>): String = when {
        text.isNotEmpty() && files.isNotEmpty() -> "mixed"
        files.isNotEmpty() -> "file"
        else -> "text"
    }

    /** Leave room for a collision suffix within Android's 255-byte filename limit. */
    fun limitFileName(name: String): String = buildString {
        var bytes = 0
        val points = name.codePoints().iterator()
        while (points.hasNext()) {
            val part = String(Character.toChars(points.nextInt()))
            bytes += part.toByteArray(Charsets.UTF_8).size
            if (bytes > 220) break
            append(part)
        }
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    fun homePath(relative: String): String = "\"\$HOME\"/" + quote(relative)
    fun insertion(text: String, files: List<String>): String =
        text.ifEmpty { files.joinToString(" ", transform = ::homePath) }

    /** Run only when the user confirms the inserted command; input remains data. */
    fun command(command: String, body: String, manifest: String): String =
        "Z2_SHARE_MANIFEST=${homePath(manifest)} Z2_SHARE_TEXT=${quote(body)} sh -c ${quote(command)}"
}
