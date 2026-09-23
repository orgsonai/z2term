package com.zerotoship.z2term.share

import android.annotation.SuppressLint
import java.io.File
import java.io.InputStream

/** All browser routes use these IDs, never a URI or a filesystem path supplied by a visitor. */
internal class DirectShareContent(items: List<Item>) {
    data class Item(val id: Int, val name: String, val parent: Int?, val file: File?) {
        val directory = file == null
        val size = file?.length() ?: 0L
    }
    val items = items.toList()
    val root = this.items.first()
    val folder = root.directory
    val size = this.items.sumOf { it.size }
    val fileCount = this.items.count { !it.directory }
    private val children = this.items.drop(1).groupBy { it.parent }

    init {
        require(root.parent == null)
        this.items.forEachIndexed { index, item ->
            require(item.id == index && (item.file == null || item.file.isFile))
            if (index > 0) {
                val parent = requireNotNull(item.parent)
                require(parent in 0 until index && this.items[parent].directory)
            }
        }
    }

    fun item(id: Int): Item? = items.getOrNull(id)
    fun children(id: Int): List<Item> = children[id].orEmpty()

    companion object {
        fun single(file: File, name: String) = DirectShareContent(listOf(Item(0, name, null, file)))
    }
}

/** Provider-independent tree interface; unreadable children must throw instead of disappearing. */
internal interface DirectShareSource {
    val id: String
    val name: String
    val directory: Boolean
    fun children(): List<DirectShareSource>
    fun open(): InputStream
}

internal object DirectShareSnapshot {
    const val MAX_BYTES = 1024L * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    const val MAX_DEPTH = 64

    /** Prepare the complete selection before any listener starts. The caller owns the empty cache directory. */
    // A floor checked on every read while copying; getAllocatableBytes is an IPC too slow for that loop.
    @SuppressLint("UsableSpace")
    fun prepare(root: DirectShareSource, cache: File, checkActive: () -> Unit,
                onInput: (InputStream?) -> Unit = {}, maxBytes: Long = MAX_BYTES): DirectShareContent {
        require(cache.isDirectory && cache.listFiles()?.isEmpty() == true && maxBytes >= 0)
        val items = ArrayList<DirectShareContent.Item>()
        val ancestors = HashSet<String>()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        fun visit(source: DirectShareSource, parent: Int?, depth: Int) {
            checkActive()
            require(depth <= MAX_DEPTH && items.size < MAX_ENTRIES)
            val id = items.size
            val name = source.name.replace(Regex("[\\p{Cntrl}]"), "_").take(255).ifBlank { "download" }
            if (source.directory) {
                require(ancestors.add(source.id)) { "Cyclic document tree" }
                items.add(DirectShareContent.Item(id, name, parent, null))
                try {
                    val children = source.children()
                    require(children.size <= MAX_ENTRIES - items.size)
                    children.sortedWith(compareBy<DirectShareSource>({ !it.directory }, { it.name }, { it.id }))
                        .forEach { visit(it, id, depth + 1) }
                } finally { ancestors.remove(source.id) }
            } else {
                val file = File(cache, id.toString() + ".bin")
                val input = source.open()
                try {
                    onInput(input)
                    input.use {
                        file.outputStream().use { output ->
                            while (true) {
                                checkActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= maxBytes && cache.usableSpace > 16 * 1024 * 1024L)
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                } finally { runCatching { input.close() }; onInput(null) }
                items.add(DirectShareContent.Item(id, name, parent, file))
            }
        }
        try {
            visit(root, null, 0)
            checkActive()
            return DirectShareContent(items)
        } catch (e: Throwable) {
            cache.deleteRecursively()
            throw e
        }
    }
}
