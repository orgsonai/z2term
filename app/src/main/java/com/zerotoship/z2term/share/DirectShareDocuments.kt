package com.zerotoship.z2term.share

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns

/** Every descendant URI retains the user's tree grant. No raw paths or persistent storage permission. */
internal object DirectShareDocuments {
    fun source(resolver: ContentResolver, uri: Uri, folder: Boolean, cancellation: CancellationSignal,
               checkActive: () -> Unit): DirectShareSource {
        require(uri.scheme == "content")
        if (folder) {
            require(DocumentsContract.isTreeUri(uri))
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, rootId)
            val root = query(resolver, document, cancellation, checkActive).single()
            require(root.id == rootId && root.directory)
            return Node(resolver, uri, root, cancellation, checkActive)
        }
        checkActive()
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, cancellation)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty().ifBlank { "download" }
        return object : DirectShareSource {
            override val id = uri.toString()
            override val name = displayName
            override val directory = false
            override fun children(): List<DirectShareSource> = error("Not a directory")
            override fun open() = DirectShareDocuments.open(resolver, uri, cancellation, checkActive)
        }
    }

    private data class Metadata(val id: String, val name: String, val directory: Boolean)

    private class Node(
        private val resolver: ContentResolver, private val tree: Uri, private val metadata: Metadata,
        private val cancellation: CancellationSignal, private val checkActive: () -> Unit,
    ) : DirectShareSource {
        override val id get() = metadata.id
        override val name get() = metadata.name
        override val directory get() = metadata.directory
        override fun children(): List<DirectShareSource> {
            require(directory)
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            return query(resolver, uri, cancellation, checkActive).map {
                Node(resolver, tree, it, cancellation, checkActive)
            }
        }
        override fun open(): java.io.InputStream {
            require(!directory)
            return DirectShareDocuments.open(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id), cancellation, checkActive)
        }
    }

    private fun query(resolver: ContentResolver, uri: Uri, cancellation: CancellationSignal,
                      checkActive: () -> Unit): List<Metadata> {
        checkActive()
        val result = ArrayList<Metadata>()
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        requireNotNull(resolver.query(uri, columns, null, null, null, cancellation)).use { cursor ->
            while (cursor.moveToNext()) {
                checkActive()
                require(result.size < DirectShareSnapshot.MAX_ENTRIES)
                val id = requireNotNull(cursor.getString(0))
                val name = requireNotNull(cursor.getString(1))
                val mime = requireNotNull(cursor.getString(2))
                result.add(Metadata(id, name, mime == Document.MIME_TYPE_DIR))
            }
            // A provider's temporary/failed listing must not be published as a complete folder.
            require(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false))
            require(cursor.extras.getString(DocumentsContract.EXTRA_ERROR).isNullOrEmpty())
        }
        return result
    }

    private fun open(resolver: ContentResolver, uri: Uri, cancellation: CancellationSignal,
                     checkActive: () -> Unit): java.io.InputStream {
        checkActive()
        val descriptor = requireNotNull(resolver.openAssetFileDescriptor(uri, "r", cancellation))
        return try { descriptor.createInputStream() }
        catch (e: Throwable) { descriptor.close(); throw e }
    }
}
