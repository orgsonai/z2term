package com.zerotoship.z2term.qr

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.util.Locale

/** Untrusted external input; extracting it never opens a URL, copies a file or runs a command. */
internal data class QrIncoming(val images: List<Uri> = emptyList(), val text: String = "") {
    companion object {
        const val MAX_IMAGES = 8

        fun from(intent: Intent): QrIncoming = runCatching {
            val type = intent.type.orEmpty().substringBefore(';').lowercase(Locale.ROOT)
            when (intent.action) {
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                    if (type.startsWith("image/")) {
                        val uris = LinkedHashSet<Uri>()
                        fun add(uri: Uri?) {
                            if (uri == null) return
                            require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.toString().length <= 4096)
                            uris.add(uri)
                            require(uris.size <= MAX_IMAGES)
                        }
                        if (intent.action == Intent.ACTION_SEND) {
                            add(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                        } else {
                            val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                            require(streams == null || streams.size <= MAX_IMAGES)
                            streams?.forEach { add(it) }
                        }
                        intent.clipData?.let { clip ->
                            require(clip.itemCount <= MAX_IMAGES)
                            for (index in 0 until clip.itemCount) add(clip.getItemAt(index).uri)
                        }
                        require(uris.isNotEmpty())
                        // Captions / screenshot URLs are not substituted for QR contents.
                        QrIncoming(images = uris.toList())
                    } else {
                        require(intent.action == Intent.ACTION_SEND && type in setOf("text/plain", "text/uri-list"))
                        val value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                            ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text
                        require(value != null && value.isNotBlank() && value.length <= QrContent.MAX_TEXT)
                        QrIncoming(text = value.toString())
                    }
                }
                Intent.ACTION_VIEW -> {
                    val value = requireNotNull(intent.data).toString()
                    val parsed = QrContent.parse(value)
                    require(parsed.kind in setOf(QrContent.Kind.SSH, QrContent.Kind.COMMAND))
                    QrIncoming(text = value)
                }
                else -> error("Unsupported QR input")
            }
        }.getOrElse { QrIncoming() }
    }
}
