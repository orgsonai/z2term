package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

internal object OutgoingShare {
    fun send(context: Context, args: List<String>) {
        require(args.size >= 2) { "share-files requires a request and filenames" }
        val staging = File(requireNotNull(context.getExternalFilesDir(null)), "z2api")
        val cache = File(context.cacheDir, "shared-files").apply { mkdirs() }
        OutgoingShareSnapshot.cleanup(cache)
        val files = OutgoingShareSnapshot.prepare(staging, cache, args.first(), args.drop(1))
        try {
            context.startActivity(Intent.createChooser(intent(context, files), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            files.first().parentFile?.parentFile?.deleteRecursively()
            throw e
        }
    }

    internal fun intent(context: Context, files: List<File>): Intent {
        require(files.isNotEmpty())
        val uris = ArrayList(files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", it)
        })
        val types = files.map {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension.lowercase()) ?: "application/octet-stream"
        }
        val mime = if (types.distinct().size == 1) types.first()
            else if (types.map { it.substringBefore('/') }.distinct().size == 1) types.first().substringBefore('/') + "/*"
            else "*/*"
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(context.contentResolver, "", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
