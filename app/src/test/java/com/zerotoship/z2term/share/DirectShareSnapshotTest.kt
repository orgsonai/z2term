package com.zerotoship.z2term.share

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectShareSnapshotTest {
    @get:Rule val files = TemporaryFolder()

    private class Node(override val id: String, override val name: String,
                       override val directory: Boolean, var bytes: ByteArray = byteArrayOf()) : DirectShareSource {
        var nodes: List<DirectShareSource> = emptyList()
        var reader: (() -> InputStream)? = null
        var listingFailure = false
        override fun children(): List<DirectShareSource> {
            if (listingFailure) throw IOException("Listing unavailable")
            return nodes
        }
        override fun open(): InputStream = reader?.invoke() ?: ByteArrayInputStream(bytes)
    }
    private fun directory(id: String, name: String = id, vararg children: DirectShareSource) =
        Node(id, name, true).apply { nodes = children.toList() }
    private fun file(id: String, name: String = id, text: String = id) =
        Node(id, name, false, text.toByteArray())

    @Test fun nestedFoldersEmptyFoldersAndDuplicateBasenamesRemainDistinct() {
        val nested = file("a", "同名.txt", "nested")
        val root = directory("root", "共有", directory("docs", "資料", nested),
            directory("empty", "空"), file("b", "同名.txt", "root"))
        val cache = files.newFolder()
        val content = DirectShareSnapshot.prepare(root, cache, {})
        assertTrue(content.folder)
        assertEquals("共有", content.root.name)
        assertEquals(2, content.fileCount)
        assertEquals(10L, content.size)
        val docs = content.items.single { it.name == "資料" }
        assertEquals("nested", content.children(docs.id).single().file!!.readText())
        val empty = content.items.single { it.name == "空" }
        assertTrue(content.children(empty.id).isEmpty())
        assertEquals("root", content.children(0).single { !it.directory }.file!!.readText())
        nested.bytes = "changed".toByteArray()
        assertEquals("nested", content.children(docs.id).single().file!!.readText())
        assertEquals(content.items.indices.toList(), content.items.map { it.id })
    }

    @Test fun providerNamesNeverBecomeCachePaths() {
        val cache = files.newFolder()
        val content = DirectShareSnapshot.prepare(directory("root", "root",
            file("one", "../../outside.txt", "one"), file("two", "/absolute", "two")), cache, {})
        assertEquals(2, cache.listFiles()!!.size)
        content.items.filter { !it.directory }.forEach {
            assertEquals(cache.canonicalFile, it.file!!.canonicalFile.parentFile)
            assertEquals(it.id.toString() + ".bin", it.file.name)
        }
    }

    @Test fun unreadableFilesAndFailedListingsNeverLeavePartialSelections() {
        val broken = file("broken").apply { reader = { throw IOException("Read denied") } }
        val badListing = directory("bad").apply { listingFailure = true }
        for (source in listOf(directory("root", "root", file("a"), broken), badListing)) {
            val cache = files.newFolder()
            assertTrue(runCatching { DirectShareSnapshot.prepare(source, cache, {}) }.isFailure)
            assertFalse(cache.exists())
        }
    }

    @Test fun aggregateLimitAppliesAcrossAllFilesAndSingleFileStillWorks() {
        val cache = files.newFolder()
        val selection = directory("root", "root", file("a", text = "1234"), file("b", text = "5678"))
        assertTrue(runCatching { DirectShareSnapshot.prepare(selection, cache, {}, maxBytes = 7) }.isFailure)
        assertFalse(cache.exists())
        val single = DirectShareSnapshot.prepare(file("single", text = "1234"), files.newFolder(), {}, maxBytes = 4)
        assertFalse(single.folder)
        assertEquals(4L, single.size)
        assertEquals("1234", single.root.file!!.readText())
    }

    @Test fun stoppingDuringCopyClosesTheInputAndDeletesPreparedFiles() {
        var cancel = false
        var closed = false
        var tracked: InputStream? = null
        val source = file("a").apply {
            reader = {
                object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        cancel = true
                        return super.read(bytes, offset, length)
                    }
                    override fun close() { closed = true; super.close() }
                }
            }
        }
        val cache = files.newFolder()
        val result = runCatching { DirectShareSnapshot.prepare(source, cache,
            { if (cancel) throw CancellationException() }, { tracked = it }) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(closed)
        assertNull(tracked)
        assertFalse(cache.exists())
    }

    @Test fun cyclesAndExcessiveDepthAreRejectedBeforePublication() {
        val cycle = directory("cycle").apply { nodes = listOf(this) }
        var deep: DirectShareSource = directory("leaf")
        repeat(DirectShareSnapshot.MAX_DEPTH + 1) { deep = directory(it.toString(), it.toString(), deep) }
        for (source in listOf(cycle, deep)) {
            val cache = files.newFolder()
            assertTrue(runCatching { DirectShareSnapshot.prepare(source, cache, {}) }.isFailure)
            assertFalse(cache.exists())
        }
    }
}
