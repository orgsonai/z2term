package com.zerotoship.z2term.viewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.zerotoship.z2term.edge.EdgeRuntime
import com.zerotoship.z2term.edge.EdgeStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Each panel item owns its snapshot. Updating one page never removes another open page. */
internal object ViewerStore {
    private data class Session(val key: String, val receive: (ViewerPage) -> Unit)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val main = Handler(Looper.getMainLooper())

    fun edgeKey(target: String, producer: String): String {
        EdgeStore.target(target)
        return "edge-" + java.security.MessageDigest.getInstance("SHA-256")
            .digest((target + "\u0000" + producer).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun file(context: Context, key: String): File {
        require(key.matches(Regex("[A-Za-z0-9-]{1,100}"))) { "Invalid viewer key" }
        return File(File(context.cacheDir, "viewer-pages").apply { mkdirs() }, "$key.page")
    }

    @Synchronized fun read(context: Context, key: String): ViewerPage? {
        val atomic = AtomicFile(file(context, key))
        return try { atomic.openRead().use { ViewerPage.decode(ViewerPage.read(it, ViewerPage.SNAPSHOT_LIMIT)) } }
        catch (_: java.io.FileNotFoundException) { null }
    }

    @Synchronized private fun save(context: Context, key: String, page: ViewerPage) {
        val atomic = AtomicFile(file(context, key))
        val stream = atomic.startWrite()
        try { stream.write(page.encode().toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    @Synchronized fun attach(key: String, receive: (ViewerPage) -> Unit): String = UUID.randomUUID().toString().also {
        sessions[it] = Session(key, receive)
    }
    @Synchronized fun detach(token: String) { sessions.remove(token) }

    @Synchronized fun prune(context: Context, panels: List<EdgeStore.Panel>) {
        val keys = panels.flatMap { panel -> panel.items.filter { it.type == "view" }
            .map { edgeKey("${panel.id}:${it.id}", it.command) } }.toSet() + sessions.values.map { it.key }
        File(context.cacheDir, "viewer-pages").listFiles().orEmpty().filter {
            it.name.startsWith("edge-") && it.extension == "page" && it.nameWithoutExtension !in keys
        }.forEach { it.delete() }
    }

    @Synchronized fun publish(context: Context, page: ViewerPage, target: String, token: String) {
        // A late result from a closed/replaced view must not reopen an Activity or overwrite its successor.
        val session = if (token.isNotEmpty()) sessions[token] ?: return else null
        val targetKey = if (target.isNotEmpty()) {
            val item = EdgeRuntime.store(context).item(target)
            require(item.type == "view") { "Target must be a view item: $target" }
            edgeKey(target, item.command)
        } else null
        require(session == null || targetKey == null || session.key == targetKey) { "Viewer target changed" }
        val key = session?.key ?: targetKey ?: "page-${UUID.randomUUID()}"
        save(context, key, page)
        main.post {
            if (session != null && sessions[token] !== session) return@post
            sessions.values.filter { it.key == key }.forEach { it.receive(page) }
            if (session == null && target.isEmpty()) ViewerActivity.open(context, key)
        }
        // Keep recent standalone pages, while protecting every attached page and all panel snapshots.
        val active = sessions.values.map { it.key }.toSet()
        file(context, key).parentFile?.listFiles().orEmpty().filter {
            it.name.startsWith("page-") && it.extension == "page" && it.nameWithoutExtension !in active && it.nameWithoutExtension != key
        }.sortedByDescending { it.lastModified() }.drop(8).forEach { it.delete() }
    }
}
