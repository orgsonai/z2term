package com.zerotoship.z2term.automation

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Shared across Linux environments. Validate before atomically replacing a named definition. */
internal class ActionStore(private val root: File) {
    private fun file(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Macro names use 1..64 letters, digits, _ or -" }
        val file = File(root, "$name.actions")
        require(!Files.isSymbolicLink(file.toPath())) { "Macro files cannot be symbolic links" }
        return file
    }
    fun names(): List<String> = synchronized(lock) {
        root.listFiles().orEmpty().filter { it.isFile && it.extension == "actions" && !Files.isSymbolicLink(it.toPath()) }
            .map { it.nameWithoutExtension }.sorted()
    }
    fun snapshot(name: String): ActionProgram = synchronized(lock) { ActionProgram.load(name, ::read) }

    fun read(name: String): ActionDefinition = ActionDefinition.parse(readText(name))

    /** The editor must also be able to repair a definition that no longer parses. */
    fun readText(name: String): String = synchronized(lock) {
        val file = file(name)
        require(file.isFile) { "No action macro: $name" }
        val text = file.inputStream().use { input ->
            val bytes = ByteArray(ActionDefinition.MAX_BYTES + 1)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            require(size <= ActionDefinition.MAX_BYTES) { "Definition exceeds 64 KiB" }
            String(bytes, 0, size, Charsets.UTF_8)
        }
        text
    }

    /** Null means a new name. Check and replace under the same lock as CLI saves. */
    fun saveEdited(name: String, raw: String, screen: ActionDefinition.Screen, expected: String?): ActionDefinition = synchronized(lock) {
        checkUnchanged(name, expected)
        save(name, raw, screen)
    }

    fun deleteEdited(name: String, expected: String) = synchronized(lock) {
        checkUnchanged(name, expected)
        delete(name)
    }

    private fun checkUnchanged(name: String, expected: String?) {
        val actual = if (file(name).exists()) readText(name) else null
        if (actual != expected) throw EditConflict()
    }

    class EditConflict : IllegalStateException("The saved macro changed. Reopen it or save under another name.")

    fun save(name: String, raw: String, screen: ActionDefinition.Screen): ActionDefinition = synchronized(lock) {
        val file = file(name)
        val definition = ActionDefinition.parse(raw, screen)
        require(file.exists() || names().size < 64) { "At most 64 action macros" }
        check(root.isDirectory || root.mkdirs()) { "Cannot create action macro directory" }
        val temp = Files.createTempFile(root.toPath(), ".save-", ".tmp")
        try {
            temp.toFile().writeText(definition.text)
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        definition
    }
    fun delete(name: String) = synchronized(lock) {
        val file = file(name)
        require(file.isFile) { "No action macro: $name" }
        check(file.delete()) { "Cannot delete action macro: $name" }
    }
    companion object {
        private val lock = Any()
    }
}
