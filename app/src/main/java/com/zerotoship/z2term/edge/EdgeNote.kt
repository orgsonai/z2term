package com.zerotoship.z2term.edge

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** A small UTF-8 document with bounded undo history and conflict-safe atomic saves. */
class EdgeNote(val file: File) {
    private var original: String = read(file)
    var text: String = original
        private set
    private var checkpoint = original
    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()
    val dirty get() = text != checkpoint
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun edit(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= LIMIT) { "Note exceeds 64 KiB" }
        if (value == text) return
        past.addLast(text)
        while (past.size > 100 || past.sumOf { it.length } > 131072) past.removeFirst()
        future.clear(); text = value
    }
    fun undo(): String {
        if (past.isNotEmpty()) { future.addLast(text); text = past.removeLast() }
        return text
    }
    fun redo(): String {
        if (future.isNotEmpty()) { past.addLast(text); text = future.removeLast() }
        return text
    }
    fun save() {
        if (!dirty) return
        check(read(file) == original) { "Note changed outside the panel: ${file.name}" }
        write(file, text)
        original = text; checkpoint = text
    }

    /** Preserve edits separately when the original changed or cannot be written. */
    fun recover(directory: File): File {
        val target = File(directory, "note-${java.util.UUID.randomUUID()}.txt")
        write(target, text)
        checkpoint = text
        return target
    }

    companion object {
        const val LIMIT = 65536
        private fun read(file: File): String {
            if (!file.exists()) return ""
            val bytes = file.inputStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (out.size() <= LIMIT) {
                    val n = input.read(buffer, 0, minOf(buffer.size, LIMIT + 1 - out.size()))
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            require(bytes.size <= LIMIT) { "Note exceeds 64 KiB: ${file.name}" }
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        private fun write(file: File, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size <= LIMIT) { "Note exceeds 64 KiB" }
            file.parentFile!!.mkdirs()
            val temp = File.createTempFile(".note-", ".tmp", file.parentFile)
            try {
                temp.outputStream().use { it.write(bytes) }
                check(temp.renameTo(file)) { "Cannot save note: ${file.name}" }
            } finally { temp.delete() }
        }
    }
}
