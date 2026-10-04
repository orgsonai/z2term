package com.zerotoship.z2term.backup

import com.zerotoship.z2term.edge.EdgeNote
import com.zerotoship.z2term.edge.EdgeStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/** Portable edge definitions, local files and note histories; never writes external note paths. */
internal object EdgeBackup {
    private const val FILES = "edge/"
    private const val HISTORY = "edge-note-history/"
    private const val STATE = "edge-state"
    data class Snapshot(val entries: Map<String, ByteArray>, val panelCount: Int, val noteCount: Int)
    data class Restore(val files: Map<String, ByteArray>, val enabled: Boolean)

    fun snapshot(store: EdgeStore): Snapshot {
        val panels = store.panels()
        val entries = linkedMapOf(STATE to (if (store.enabled()) "1" else "0").toByteArray())
        tree(store.root).forEach { (name, file) ->
            if (name != "enabled" && ".note-history" !in name.split('/'))
                entries[FILES + name] = file.readBytes()
        }
        var notes = 0
        panels.forEach { panel -> panel.items.filter { it.type == "note" }.forEach { item ->
            val file = store.noteFile(panel.id, item)
            // Read through the note implementation to retain its size/UTF-8 checks. Missing notes
            // represent empty documents, and must also replace existing text on restoration.
            val text = EdgeNote(file).text
            val relative = file.relativeToOrNull(store.root.canonicalFile)?.invariantSeparatorsPath
                ?.takeIf { safeName(it) }
            val destination = relative ?: "${panel.id}/.restored-notes/${item.id}.txt"
            entries[FILES + destination] = text.toByteArray(Charsets.UTF_8)
            if (!item.fields["file"].isNullOrBlank() || relative == null) {
                val fields = item.fields + ("file" to "~/.z2term/edge/$destination")
                entries[FILES + "${panel.id}/${item.id}.item"] = EdgeStore.encode(fields).toByteArray()
            }
            store.noteHistoryFile(file).takeIf { it.isFile }?.let {
                entries[HISTORY + "${panel.id}/${item.id}"] = it.readBytes()
            }
            notes++
        } }
        return Snapshot(entries, panels.size, notes)
    }

    /** Validate the merged panel/tab graph and every destination before changing live settings. */
    fun prepareRestore(store: EdgeStore, entries: Map<String, ByteArray>, scratch: File): Restore? {
        val state = entries[STATE] ?: run {
            require(entries.keys.none { it.startsWith(FILES) || it.startsWith(HISTORY) })
            return null // Older archives leave the current edge setup untouched.
        }
        val enabled = when (state.toString(Charsets.UTF_8)) {
            "1" -> true
            "0" -> false
            else -> throw IllegalArgumentException("Invalid edge state")
        }
        val updates = linkedMapOf<String, ByteArray>()
        entries.filterKeys { it.startsWith(FILES) }.forEach { (name, bytes) ->
            val relative = name.removePrefix(FILES)
            require(safeName(relative) && relative != "enabled") { "Invalid edge path" }
            safeFile(store.root, relative)
            updates[relative] = bytes
        }
        val stage = Files.createTempDirectory(scratch.toPath(), "edge-restore-").toFile()
        try {
            tree(store.root).forEach { (name, file) ->
                val target = safeFile(stage, name)
                target.parentFile!!.mkdirs(); file.copyTo(target)
            }
            updates.forEach { (name, bytes) ->
                val target = safeFile(stage, name)
                target.parentFile!!.mkdirs(); target.writeBytes(bytes)
            }
            val staged = EdgeStore(stage)
            val panels = staged.panels()
            // Restored note references must remain inside the edge directory. An archive cannot
            // cause a later note edit to overwrite an arbitrary external file.
            panels.forEach { panel -> panel.items.filter { it.type == "note" }.forEach note@ { item ->
                if ("${panel.id}/${item.id}.item" !in updates) return@note
                val target = store.noteFile(panel.id, item)
                val relative = target.relativeToOrNull(store.root.canonicalFile)?.invariantSeparatorsPath
                require(relative != null && safeName(relative)) { "External restored note path" }
                EdgeNote(safeFile(stage, relative))
            } }
            entries.filterKeys { it.startsWith(HISTORY) }.forEach { (name, bytes) ->
                val parts = name.removePrefix(HISTORY).split('/')
                require(parts.size == 2 && parts.all(EdgeStore::validId)) { "Invalid note history" }
                val item = staged.item("${parts[0]}:${parts[1]}")
                require(item.type == "note")
                val note = store.noteFile(parts[0], item)
                val owner = note.relativeTo(store.root.canonicalFile).invariantSeparatorsPath.substringBefore('/')
                val prefix = if (EdgeStore.validId(owner) && File(stage, owner).isDirectory) "$owner/" else ""
                val key = MessageDigest.getInstance("SHA-256").digest(note.path.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                val relative = "$prefix.note-history/$key"
                safeFile(store.root, relative)
                updates[relative] = bytes
            }
            return Restore(updates, enabled)
        } finally { stage.deleteRecursively() }
    }

    fun apply(store: EdgeStore, restore: Restore) {
        // Recheck immediately before each atomic replacement, including symlink parents.
        restore.files.forEach { (name, bytes) ->
            val target = safeFile(store.root, name)
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            val temp = File.createTempFile(".edge-backup-", ".tmp", target.parentFile)
            try {
                temp.writeBytes(bytes)
                check(temp.renameTo(target)) { "Cannot restore edge file" }
            } finally { temp.delete() }
        }
        store.enable(restore.enabled)
    }

    private fun safeName(name: String): Boolean = name.isNotEmpty() && '\\' !in name &&
        name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    private fun safeFile(root: File, name: String): File {
        require(safeName(name)) { "Invalid edge path" }
        require(!Files.isSymbolicLink(root.toPath())) { "Symbolic edge directory" }
        val target = File(root.canonicalFile, name)
        require(target.canonicalFile == target.absoluteFile) { "Symbolic edge path" }
        return target
    }

    private fun tree(root: File): Map<String, File> {
        if (!root.exists()) return emptyMap()
        require(!Files.isSymbolicLink(root.toPath())) { "Symbolic edge directory" }
        val result = linkedMapOf<String, File>()
        fun visit(dir: File) {
            val children = dir.listFiles() ?: throw IOException("Cannot read edge directory")
            children.sortedBy { it.name }.forEach { file ->
                val name = file.relativeTo(root).invariantSeparatorsPath
                safeFile(root, name)
                when {
                    file.isDirectory -> visit(file)
                    file.isFile && !file.name.startsWith(".edge-") -> result[name] = file
                    file.isFile -> Unit // In-progress atomic writes are not saved state.
                    else -> throw IOException("Unsupported edge file")
                }
            }
        }
        visit(root)
        return result
    }
}
