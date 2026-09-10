package com.zerotoship.z2term.edge

import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** A small UTF-8 document with bounded undo history and conflict-safe atomic saves. */
class EdgeNote(val file: File, private val historyFile: File? = null) {
    private var original: String = read(file)
    var text: String = original
        private set
    private var checkpoint = original
    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()
    private var historyDirty = false
    val dirty get() = text != checkpoint
    val needsSave get() = dirty || historyDirty
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    init { loadHistory() }

    fun edit(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= LIMIT) { "Note exceeds 64 KiB" }
        if (value == text) return
        past.addLast(text)
        future.clear(); text = value
        changedHistory()
    }
    fun undo(): String {
        if (past.isNotEmpty()) { future.addLast(text); text = past.removeLast(); changedHistory() }
        return text
    }
    fun redo(): String {
        if (future.isNotEmpty()) { past.addLast(text); text = future.removeLast(); changedHistory() }
        return text
    }
    fun save() {
        if (!needsSave) return
        check(read(file) == original) { "Note changed outside the panel: ${file.name}" }
        if (dirty) {
            write(file, text)
            original = text; checkpoint = text
        }
        if (historyDirty && historyFile != null) {
            atomicWrite(historyFile) { temp ->
                DataOutputStream(temp.outputStream().buffered()).use { output ->
                    output.writeInt(HISTORY_VERSION)
                    output.writeText(text)
                    for (entries in listOf(past, future)) {
                        output.writeInt(entries.size)
                        entries.forEach { output.writeText(it) }
                    }
                }
            }
        }
        historyDirty = false
    }

    private fun changedHistory() {
        var bytes = (past + future).sumOf { it.toByteArray(Charsets.UTF_8).size }
        while (past.size + future.size > HISTORY_COUNT || bytes > HISTORY_BYTES) {
            val removed = if (past.isNotEmpty()) past.removeFirst() else future.removeFirst()
            bytes -= removed.toByteArray(Charsets.UTF_8).size
        }
        historyDirty = historyFile != null
    }

    /** Ignore stale or damaged history; the UTF-8 document remains authoritative. */
    private fun loadHistory() {
        val source = historyFile?.takeIf { it.isFile } ?: return
        runCatching {
            require(source.length() <= LIMIT + HISTORY_BYTES + HISTORY_COUNT * 4 + 16L)
            DataInputStream(source.inputStream().buffered()).use { input ->
                require(input.readInt() == HISTORY_VERSION)
                require(input.readText() == original)
                val loaded = List(2) {
                    val count = input.readInt()
                    require(count in 0..HISTORY_COUNT)
                    List(count) { input.readText() }
                }
                require(input.read() == -1)
                require(loaded.sumOf { it.size } <= HISTORY_COUNT)
                require(loaded.flatten().sumOf { it.toByteArray(Charsets.UTF_8).size } <= HISTORY_BYTES)
                past.addAll(loaded[0]); future.addAll(loaded[1])
            }
        }
    }

    /** Preserve edits separately when the original changed or cannot be written. */
    fun recover(directory: File): File {
        val target = File(directory, "note-${java.util.UUID.randomUUID()}.txt")
        write(target, text)
        checkpoint = text
        historyDirty = false
        return target
    }

    companion object {
        const val LIMIT = 65536
        const val HISTORY_COUNT = 100
        const val HISTORY_BYTES = 262144
        private const val HISTORY_VERSION = 1
        private fun DataOutputStream.writeText(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size); write(bytes)
        }
        private fun DataInputStream.readText(): String {
            val size = readInt()
            require(size in 0..LIMIT)
            val bytes = ByteArray(size)
            readFully(bytes)
            return decode(bytes)
        }
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
            return decode(bytes)
        }
        private fun decode(bytes: ByteArray): String =
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        private fun write(file: File, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size <= LIMIT) { "Note exceeds 64 KiB" }
            atomicWrite(file) { temp -> temp.outputStream().use { it.write(bytes) } }
        }
        private fun atomicWrite(file: File, write: (File) -> Unit) {
            file.parentFile!!.mkdirs()
            val temp = File.createTempFile(".note-", ".tmp", file.parentFile)
            try {
                write(temp)
                check(temp.renameTo(file)) { "Cannot save note: ${file.name}" }
            } finally { temp.delete() }
        }
    }
}
