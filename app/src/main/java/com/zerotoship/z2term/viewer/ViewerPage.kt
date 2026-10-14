package com.zerotoship.z2term.viewer

import java.io.File
import java.util.Base64

/** Local HTML snapshot. Titles are encoded separately so embedded newlines cannot change the format. */
internal data class ViewerPage(val html: String, val title: String, val controls: ViewerControls? = null) {
    fun encode(): String = Base64.getEncoder().encodeToString(title.toByteArray(Charsets.UTF_8)) + "\n" +
        Base64.getEncoder().encodeToString(controls?.source.orEmpty().toByteArray(Charsets.UTF_8)) + "\n" + html

    companion object {
        const val HTML_LIMIT = 4 * 1024 * 1024
        const val TITLE_LIMIT = 4096
        const val SNAPSHOT_LIMIT = HTML_LIMIT + TITLE_LIMIT * 2 + ViewerControls.LIMIT * 2

        fun read(file: File, limit: Int, lenient: Boolean = false): String = file.inputStream().use { read(it, limit, lenient) }
        /** [lenient]: command output may hold broken UTF-8; it is shown with replacement marks instead of failing. */
        fun read(input: java.io.InputStream, limit: Int, lenient: Boolean = false): String {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() <= limit) {
                val n = input.read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            require(out.size() <= limit) { "Viewer file exceeds $limit bytes" }
            val action = if (lenient) java.nio.charset.CodingErrorAction.REPLACE else java.nio.charset.CodingErrorAction.REPORT
            return Charsets.UTF_8.newDecoder().onMalformedInput(action).onUnmappableCharacter(action)
                .decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
        }

        private val escapes = Regex("\u001B(\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(\u0007|\u001B\\\\)?|[@-_])")
        private val controls = Regex("[\u0000-\u0008\u000B-\u001F\u007F]")

        /**
         * Plain text (a file or a command's output) as a page: wrapped, monospaced, in the theme's colours.
         * Terminal colour codes are dropped and every character is escaped, so text never becomes markup.
         */
        fun fromText(text: String): String {
            val plain = text.replace(escapes, "").replace("\r\n", "\n").replace('\r', '\n').replace(controls, "")
            val body = plain.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>body{margin:0;background:var(--z2-bg,#fff);color:var(--z2-fg,#111)}" +
                "pre{margin:0;padding:10px 12px;white-space:pre-wrap;overflow-wrap:anywhere;" +
                "font:13px/1.5 monospace;tab-size:8}</style></head><body><pre>" + body + "</pre></body></html>"
        }

        fun decode(text: String): ViewerPage {
            require('\n' in text) { "Invalid viewer snapshot" }
            val title = String(Base64.getDecoder().decode(text.substringBefore('\n')), Charsets.UTF_8)
            val rest = text.substringAfter('\n')
            require('\n' in rest) { "Invalid viewer snapshot" }
            val controls = String(Base64.getDecoder().decode(rest.substringBefore('\n')), Charsets.UTF_8)
            return ViewerPage(rest.substringAfter('\n'), title, controls.takeIf { it.isNotEmpty() }?.let(ViewerControls::parse))
        }
    }
}
