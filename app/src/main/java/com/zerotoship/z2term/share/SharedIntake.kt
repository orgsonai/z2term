package com.zerotoship.z2term.share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Saves a complete share receipt (body, subject and attachments) on a worker thread.
 * The proposed terminal insertion never includes Enter; saved content is not executed here.
 */
object SharedIntake {

    /** 受け取ったファイルの置き場 (ホームからの相対)。端末からは `~/z2term-inbox/`。 */
    const val INBOX_DIR = "z2term-inbox"

    private const val TAG = "SharedIntake"
    /** 受け取るファイルの上限。これを超えるものは黙ってコピーせず、失敗として扱う。 */
    private const val MAX_BYTES = 512L * 1024 * 1024

    /** 共有が**テキスト**だったことを表す [Intake.kind] の値。 */
    const val KIND_TEXT = "text"

    /** 共有が**ファイル**だったことを表す [Intake.kind] の値。 */
    const val KIND_FILE = "file"
    const val KIND_MIXED = "mixed"

    /** [text] is the proposed insertion; [files] and [manifest] are relative to shell HOME. */
    data class Intake(
        val kind: String, val text: String, val fileNames: List<String>,
        val body: String = "", val files: List<String> = emptyList(), val manifest: String = ""
    )

    /**
     * [intent] が共有なら、受け取った内容を返す。共有でない / 中身が無いときは null。
     *
     * ファイルが複数のときは、それぞれのパスを**空白区切り**で並べる (そのままコマンドの
     * 引数として使えるように、必要ならクォートする)。
     */
    fun intakeFrom(context: Context, intent: Intent): Intake? {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: (0 until (intent.clipData?.itemCount ?: 0)).mapNotNull {
                intent.clipData?.getItemAt(it)?.text?.toString()
            }.joinToString("\n")
        val streams = if (action == Intent.ACTION_SEND) listOfNotNull(getStream(intent)) else getStreams(intent)
        val uris = (streams + (0 until (intent.clipData?.itemCount ?: 0)).mapNotNull {
            intent.clipData?.getItemAt(it)?.uri?.takeIf { uri -> uri.scheme == "content" }
        }).distinct()
        if (body.isEmpty() && uris.isEmpty()) return null
        return save(context, body, uris, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
    }

    /** A document chosen for a snippet is imported, without firing share rules. */
    fun importDocument(context: Context, uri: Uri): String = save(context, "", listOf(uri), "").files.single()

    private fun save(context: Context, body: String, uris: List<Uri>, subject: String): Intake {
        require(uris.size <= 32) { "At most 32 files may be received together" }
        require(uris.all { it.scheme == "content" }) { "Only shared content URIs are accepted" }
        val id = UUID.randomUUID().toString()
        val relative = "$INBOX_DIR/$id"
        val dir = File(File(context.filesDir, "shared_home"), relative)
        check(dir.mkdirs()) { "Cannot create inbox directory" }
        try {
            // Copy the entire selection or fail; a missing attachment must not look like success.
            File(dir, "manifest.json").writeText("")
            val names = uris.map { requireNotNull(copyIn(context, it, dir)) { "Cannot import attachment" } }
            val files = names.map { "$relative/$it" }
            val manifest = "$relative/manifest.json"
            val json = JSONObject().put("version", 1).put("id", id)
                .put("receivedAt", System.currentTimeMillis()).put("text", body).put("subject", subject)
                .put("files", JSONArray(files.mapIndexed { index, path ->
                    JSONObject().put("name", names[index]).put("path", path)
                        .put("size", File(dir, names[index]).length())
                }))
            File(dir, "manifest.json").writeText(json.toString(2) + "\n")
            val kind = SharedPayload.kind(body, files)
            return Intake(kind, SharedPayload.insertion(body, files), names, body, files, manifest)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    @Suppress("DEPRECATION")  // getParcelableExtra(String, Class) は API 33+。minSdk 29 のため旧 API を使う。
    private fun getStream(intent: Intent): Uri? =
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    @Suppress("DEPRECATION")
    private fun getStreams(intent: Intent): List<Uri> =
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().filterNotNull()

    /** [uri] の中身を [dir] にコピーし、置いたファイル名を返す。失敗したら null。 */
    private fun copyIn(context: Context, uri: Uri, dir: File): String? = runCatching {
        val name = displayName(context.contentResolver, uri)
        val target = uniqueFile(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            var total = 0L
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        // 途中で打ち切った中途半端なファイルは残さない。
                        output.close()
                        target.delete()
                        Log.w(TAG, "too large: $uri")
                        return null
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: return null
        target.name
    }.getOrElse {
        Log.w(TAG, "copy failed: ${it.message}")
        null
    }

    /**
     * 共有元が名乗るファイル名を取り出す。取れないときは URI の末尾、それも無ければ既定名。
     * **パス区切りなど危険な文字は必ず落とす** (`../` で置き場の外に書かせない)。
     */
    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val raw = fromProvider ?: uri.lastPathSegment ?: "shared"
        val safe = raw.substringAfterLast('/').replace(UNSAFE, "_").trim('.', ' ')
        return SharedPayload.limitFileName(safe.ifBlank { "shared" })
    }

    /** 同名があれば `-2` `-3` … を足して、受け取ったものを取りこぼさない (上書きしない)。 */
    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (n in 2..99) {
            val f = File(dir, "$stem-$n$ext")
            if (!f.exists()) return f
        }
        return File(dir, "$stem-${System.currentTimeMillis()}$ext")
    }

    /** Remove separators, control characters and incompatible filename characters. */
    private val UNSAFE = Regex("[/\\\\:*?\"<>|\\$`!\\x00-\\x1F\\x7F]")
}
