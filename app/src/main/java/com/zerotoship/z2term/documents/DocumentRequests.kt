package com.zerotoship.z2term.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.zerotoship.z2term.share.SharedIntake
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object DocumentRequests {
    class Request(val save: File?, val name: String, val mime: String) {
        val result = CompletableFuture<String>()
        val started = AtomicBoolean(false)
    }
    private val pending = ConcurrentHashMap<String, Request>()
    private val busy = AtomicBoolean(false)
    private val copies = Executors.newSingleThreadExecutor { Thread(it, "document-copy").apply { isDaemon = true } }
    fun get(id: String): Request? = pending[id]

    /** Runs off the API worker: the picker can wait while other commands continue. */
    fun command(context: Context, args: List<String>): String {
        require(args.size in 2..4 && args[0] in listOf("pick", "save")) { "z2-file pick MIME | save STAGE NAME MIME" }
        val source: File?
        val name: String
        val mime: String
        if (args[0] == "pick") {
            require(args.size == 2)
            source = null; name = ""; mime = args[1]
        } else {
            require(args.size == 4 && Regex("file-[A-Za-z0-9]+").matches(args[1]))
            val base = File(context.getExternalFilesDir(null), "z2api").canonicalFile
            val dir = File(base, args[1])
            require(dir.canonicalFile.parentFile == base && dir.canonicalFile == dir.absoluteFile)
            source = File(dir, "data")
            require(source.canonicalFile == source.absoluteFile && source.isFile && source.length() <= DocumentTransfer.MAX_BYTES)
            name = args[2].substringAfterLast('/').take(180).ifBlank { "document" }
            mime = args[3]
        }
        require(mime.length <= 128 && mime.contains('/') && mime.none { it.isWhitespace() }) { "Invalid MIME type" }
        check(busy.compareAndSet(false, true)) { "A document picker is already open" }
        val id = UUID.randomUUID().toString()
        val request = Request(source, name, mime)
        pending[id] = request
        try {
            context.startActivity(Intent(context, DocumentPickerActivity::class.java)
                .putExtra("request", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return request.result.get(300, TimeUnit.SECONDS)
        } finally {
            request.result.completeExceptionally(IllegalStateException("Document operation expired"))
            pending.remove(id)
            busy.set(false)
        }
    }

    fun selected(context: Context, id: String, uri: Uri?) {
        val request = pending[id] ?: return
        if (uri == null) { cancel(id); return }
        if (!request.started.compareAndSet(false, true)) return
        copies.execute {
            try {
                check(!request.result.isDone)
                require(uri.scheme == "content") { "Not a document URI" }
                if (request.save == null) {
                    val path = SharedIntake.importDocument(context, uri) { !request.result.isDone }
                    if (!request.result.complete(path)) {
                        File(context.filesDir, "shared_home/$path").parentFile?.deleteRecursively()
                    }
                } else {
                    try {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                            request.save.inputStream().use { input ->
                                DocumentTransfer.copy(input, output) { !request.result.isDone }
                            }
                        }
                        check(request.result.complete("")) { "Document operation cancelled" }
                    } catch (e: Exception) {
                        // ACTION_CREATE_DOCUMENT created this document for this operation.
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                        throw e
                    }
                }
            } catch (e: Exception) { request.result.completeExceptionally(e) }
        }
    }

    fun cancel(id: String) { pending[id]?.result?.completeExceptionally(IllegalStateException("Document selection cancelled")) }
}
