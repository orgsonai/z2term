package com.zerotoship.z2term.clipboard

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.zerotoship.z2term.gui.ClipboardFiles
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * クリップボードで受け渡すファイルの Android 側。
 *
 * ⭐ **保存先を選ばせない。** 相手から届いたファイルは **「ダウンロード / z2term」** に入れる
 * (`MediaStore`)。場所を毎回選ばせると、渡すたびに手が止まるうえに「どこへ入れたのか」が
 * 分からなくなる。ダウンロードなら**他のアプリのファイル選択からそのまま見える**し、
 * 保存の権限も要らない (Android 10 以降のスコープ付きストレージ)。
 */
object ClipboardFileTransfer {
    private const val TAG = "ClipboardFileTransfer"

    /**
     * 端末側の置き場。⭐ **RDP のフォルダ共有もここを既定にする**
     * ([com.zerotoship.z2term.gui.rdp.RdpShareDefaults])。受け取ったファイルと共有フォルダが
     * 別々の場所だと、戻したファイルをどちらで探せばいいのか分からなくなる。
     */
    const val FOLDER = "Download/z2term"
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * 相手から届いたファイルの置き場。
     *
     * ⛔ **受信スレッドで書き込まない** ([ClipboardFiles.Sink] の約束)。ここで待つと RDP の
     * 受信ループごと止まり、**画面と入力まで固まる**。⇒ 1 本のスレッドへ順に流す。
     *
     * @param onSaved 1 件保存できるたびに、それまでに保存できた全部の URI を渡す。
     */
    class Downloads(
        private val context: Context,
        private val onSaved: (List<Uri>) -> Unit,
    ) : ClipboardFiles.Sink {
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "clip-file-save").apply { isDaemon = true }
        }
        private val saved = mutableListOf<Uri>()

        @Volatile private var uri: Uri? = null
        @Volatile private var stream: OutputStream? = null
        @Volatile private var failed = false

        override fun begin(entry: ClipboardFiles.Entry): Boolean {
            if (entry.size > MAX_FILE_BYTES) {
                Log.w(TAG, "skipped ${entry.name}: ${entry.size} bytes is too large")
                return false
            }
            submit {
                failed = false
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, entry.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(entry.name))
                    put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
                    // ⚠ 書き終わるまで他のアプリから見せない。途中の中身を開かせないため。
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (target == null) {
                    failed = true
                    Log.w(TAG, "could not create ${entry.name} in $FOLDER")
                    return@submit
                }
                uri = target
                stream = resolver.openOutputStream(target)
                if (stream == null) {
                    failed = true
                    resolver.delete(target, null, null)
                    uri = null
                }
            }
            return true
        }

        override fun write(data: ByteArray) {
            submit { stream?.write(data) }
        }

        override fun finish(complete: Boolean) {
            submit {
                val target = uri
                val out = stream
                uri = null
                stream = null
                runCatching { out?.flush() }
                runCatching { out?.close() }
                if (target == null) return@submit
                val resolver = context.contentResolver
                if (!complete || failed) {
                    // ⚠ 書きかけを残さない。中途半端なファイルは「壊れている」と分からない。
                    runCatching { resolver.delete(target, null, null) }
                    Log.w(TAG, "discarded an incomplete file")
                    return@submit
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                runCatching { resolver.update(target, done, null, null) }
                saved += target
                onSaved(saved.toList())
            }
        }

        fun close() {
            runCatching { worker.shutdown() }
        }

        private fun submit(action: () -> Unit) {
            runCatching {
                worker.execute {
                    runCatching(action).onFailure {
                        failed = true
                        Log.w(TAG, "clipboard file write failed", it)
                    }
                }
            }
        }
    }

    /**
     * Android のクリップボードにあるファイルを相手へ渡す。ファイルが 1 つも無ければ null。
     *
     * ⚠ **`content://` の中身は毎回開き直すと遅い。** 相手は前から順に読むので、続きなら
     * 開いたままの流れを使い回し、位置が飛んだときだけ開き直す。
     */
    fun fromClip(context: Context, clip: ClipData?): ClipboardFiles.Source? {
        val uris = (0 until (clip?.itemCount ?: 0)).mapNotNull { clip?.getItemAt(it)?.uri }
        return fromUris(context, uris)
    }

    /**
     * ファイル選択から受け取った URI を RDP の遅延読み込み source にする。
     * Android の「コピー」はファイル管理アプリ内部だけで完結して system clipboard に URI を
     * 載せない実装も多いため、明示的なファイル選択経路でも同じ source を使う。
     */
    fun fromUris(context: Context, candidates: List<Uri>): ClipboardFiles.Source? {
        val uris = candidates.filter { it.scheme == "content" }.distinct().take(MAX_FILES)
        if (uris.isEmpty()) return null
        val described = uris.mapNotNull { uri -> describe(context, uri)?.let { uri to it } }
        if (described.isEmpty()) return null
        return ClipSource(context, described.map { it.first }, described.map { it.second })
    }

    private class ClipSource(
        private val context: Context,
        private val uris: List<Uri>,
        override val entries: List<ClipboardFiles.Entry>,
    ) : ClipboardFiles.Source {
        private var openIndex = -1
        private var openPosition = 0L
        private var stream: InputStream? = null

        @Synchronized
        override fun read(index: Int, position: Long, length: Int): ByteArray? {
            val uri = uris.getOrNull(index) ?: return null
            if (length <= 0) return ByteArray(0)
            val source = streamAt(index, position, uri) ?: return null
            val buffer = ByteArray(length)
            var filled = 0
            while (filled < length) {
                val n = runCatching { source.read(buffer, filled, length - filled) }.getOrElse { -1 }
                if (n <= 0) break
                filled += n
            }
            openPosition = position + filled
            return if (filled == length) buffer else buffer.copyOf(filled)
        }

        private fun streamAt(index: Int, position: Long, uri: Uri): InputStream? {
            if (index == openIndex && position == openPosition && stream != null) return stream
            closeStream()
            val opened = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: return null
            var skipped = 0L
            while (skipped < position) {
                val n = runCatching { opened.skip(position - skipped) }.getOrElse { 0L }
                if (n <= 0) break
                skipped += n
            }
            if (skipped != position) {
                runCatching { opened.close() }
                return null
            }
            openIndex = index
            openPosition = position
            stream = opened
            return opened
        }

        @Synchronized
        override fun close() = closeStream()

        private fun closeStream() {
            runCatching { stream?.close() }
            stream = null
            openIndex = -1
            openPosition = 0
        }
    }

    private fun describe(context: Context, uri: Uri): ClipboardFiles.Entry? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    ?: uri.lastPathSegment
                    ?: return@use null
                val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getLong(it) }
                    ?: context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    ?: return@use null
                if (size < 0) return@use null
                ClipboardFiles.Entry(name.substringAfterLast('/'), size)
            }
        }.getOrNull()
    }

    private fun mimeTypeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private const val MAX_FILES = 512
}
