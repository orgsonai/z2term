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

        fun read(file: File, limit: Int): String = file.inputStream().use { read(it, limit) }
        fun read(input: java.io.InputStream, limit: Int): String {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (out.size() <= limit) {
                val n = input.read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            require(out.size() <= limit) { "Viewer file exceeds $limit bytes" }
            return Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
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
