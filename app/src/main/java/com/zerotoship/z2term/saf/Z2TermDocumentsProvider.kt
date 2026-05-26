package com.zerotoship.z2term.saf

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import com.zerotoship.z2term.R
import java.io.File
import java.io.FileNotFoundException

/**
 * Z2Term の作業フォルダ (Alpine rootfs + 各ユーザー HOME) を SAF 経由で
 * 他のファイラーアプリから開けるようにする DocumentsProvider。
 *
 * Termux の `TermuxDocumentsProvider` と同じ発想:
 *  - rootfs は `filesDir/distros/<distro>/` に普通の Linux ディレクトリとして
 *    展開されているので、PRoot を介さず直接 SAF で読み書きできる。
 *  - PRoot のシンボリックリンク (`bin/sh -> /bin/busybox` 等、絶対リンク) は
 *    SAF からは素の Linux と同じ解釈になるため一部リンク先は辿れないが、
 *    これは Termux でも同じ制約。実ファイル/ディレクトリの操作は問題ない。
 *
 * 公開ルート:
 *  - "ホーム (共有)" → shared_home  (端末が /root にマウントしている実体。
 *    これにより端末の ~ とファイラーで見えるフォルダが **一致** する)
 *  - インストール済み distro ごとに "ルート (/)" → distros/<distro>  (= rootfs 全体)
 *
 * 旧実装は distros/<distro>/root を「ホーム」として公開していたが、端末側は
 * shared_home を /root にバインドしているため両者がズレていた。それを修正。
 *
 * documentId はファイルの絶対パスをそのまま使う。安全のため、解決後の
 * canonical パスが許可ルート ([allowedRoots]) 配下に無いリクエストは拒否する
 * (パストラバーサル防止)。
 */
class Z2TermDocumentsProvider : DocumentsProvider() {

    private fun ctx() = context ?: error("Provider context is null")

    private val distrosDir: File
        get() = File(ctx().filesDir, "distros")

    private val sharedHomeDir: File
        get() = File(ctx().filesDir, "shared_home")

    /** SAF からアクセスを許可するサブツリー (これ以外は traversal として拒否)。 */
    private val allowedRoots: List<File>
        get() = listOf(sharedHomeDir, distrosDir)

    override fun onCreate(): Boolean = true

    // ---- Roots --------------------------------------------------------------

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        // 共有ホーム = 端末の /root と同一実体。常に公開 (無ければ作る)。
        val home = sharedHomeDir.apply { if (!exists()) mkdirs() }
        addRoot(
            result,
            rootId = "home-shared",
            title = ctx().getString(R.string.saf_home_title),
            summary = ctx().getString(R.string.saf_home_summary),
            dir = home
        )
        // 各 distro の rootfs 全体 (/) も公開。
        val distros = distrosDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        for (distro in distros) {
            addRoot(
                result,
                rootId = "${distro.name}-rootfs",
                title = "Z2Term ${distro.name}",
                summary = ctx().getString(R.string.saf_rootfs_summary),
                dir = distro
            )
        }
        return result
    }

    private fun addRoot(cursor: MatrixCursor, rootId: String, title: String, summary: String, dir: File) {
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, rootId)
            add(Root.COLUMN_DOCUMENT_ID, docIdOf(dir))
            add(Root.COLUMN_TITLE, title)
            add(Root.COLUMN_SUMMARY, summary)
            // CREATE: 新規ファイル/フォルダ作成可、IS_CHILD: 階層問い合わせ対応、
            // LOCAL_ONLY: ネットワークではない、SUPPORTS_SEARCH は付けない (未実装)
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY
            )
            add(Root.COLUMN_ICON, R.drawable.ic_notification)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
    }

    // ---- Documents ----------------------------------------------------------

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, resolveDoc(documentId))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = resolveDoc(parentDocumentId)
        parent.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { includeFile(result, it) }
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = File(parentDocumentId).canonicalPath
        val child = File(documentId).canonicalPath
        return child.startsWith("$parent${File.separator}")
    }

    override fun getDocumentType(documentId: String): String = mimeOf(resolveDoc(documentId))

    // ---- Open / Create / Delete / Rename ------------------------------------

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = resolveDoc(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = resolveDoc(parentDocumentId)
        val target = uniqueFile(parent, displayName)
        try {
            if (Document.MIME_TYPE_DIR == mimeType) {
                if (!target.mkdir()) throw FileNotFoundException(ctx().getString(R.string.saf_error_mkdir_failed, target.path))
            } else {
                if (!target.createNewFile()) throw FileNotFoundException(ctx().getString(R.string.saf_error_create_failed, target.path))
            }
        } catch (e: Exception) {
            throw FileNotFoundException(ctx().getString(R.string.saf_error_create_failed_msg, e.message ?: ""))
        }
        return docIdOf(target)
    }

    override fun deleteDocument(documentId: String) {
        val file = resolveDoc(documentId)
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!ok) throw FileNotFoundException(ctx().getString(R.string.saf_error_delete_failed, file.path))
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = resolveDoc(documentId)
        val dest = uniqueFile(file.parentFile ?: throw FileNotFoundException(ctx().getString(R.string.saf_error_no_parent)), displayName)
        if (!file.renameTo(dest)) throw FileNotFoundException(ctx().getString(R.string.saf_error_rename_failed, file.path))
        return docIdOf(dest)
    }

    // ---- Helpers ------------------------------------------------------------

    /** documentId (= 絶対パス) を File へ。許可ルート配下でなければ拒否 (traversal 防止)。 */
    private fun resolveDoc(documentId: String): File {
        val file = File(documentId)
        val canonical = file.canonicalPath
        val ok = allowedRoots.any { root ->
            val base = root.canonicalPath
            canonical == base || canonical.startsWith("$base${File.separator}")
        }
        if (!ok) throw FileNotFoundException(ctx().getString(R.string.saf_error_out_of_scope, documentId))
        return file
    }

    private fun docIdOf(file: File): String = file.absolutePath

    private fun includeFile(cursor: MatrixCursor, file: File) {
        if (!file.exists()) return
        var flags = 0
        if (file.isDirectory) {
            if (file.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        val parentWritable = file.parentFile?.canWrite() == true
        if (parentWritable) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docIdOf(file))
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, mimeOf(file))
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** 同名衝突時に "name (1).ext" のように連番を付ける。 */
    private fun uniqueFile(parent: File, displayName: String): File {
        var candidate = File(parent, displayName)
        if (!candidate.exists()) return candidate
        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(parent, "$stem ($i)$ext")
            i++
        }
        return candidate
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? = null

    companion object {
        private const val TAG = "Z2TermDocsProvider"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS
        )
    }
}
