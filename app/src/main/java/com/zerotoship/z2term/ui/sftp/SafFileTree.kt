package com.zerotoship.z2term.ui.sftp

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.zerotoship.z2term.channel.RemoteFs
import com.zerotoship.z2term.channel.RemotePath
import com.zerotoship.z2term.channel.SftpEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

internal interface LocalFileTree {
    val rootId: String
    suspend fun list(documentId: String): List<LocalFileEntry>
    suspend fun ensureDirectory(parentId: String, name: String): String
    suspend fun openOutput(parentId: String, name: String, mimeType: String): OutputStream
    fun openInput(entry: LocalFileEntry): InputStream
}

/**
 * SAF のツリー権限内を、システムのファイル選択画面へ移動せずアプリ内で表示するための薄い境界。
 * Uri は外部ストレージ固有のパスへ変換せず、provider が返した documentId のまま扱う。
 */
internal class SafFileTree(
    private val resolver: ContentResolver,
    val treeUri: Uri,
) : LocalFileTree {
    override val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    override suspend fun list(documentId: String): List<LocalFileEntry> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            val idAt = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameAt = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeAt = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeAt = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedAt = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idAt)
                    val mime = cursor.getString(mimeAt).orEmpty()
                    add(
                        LocalFileEntry(
                            uri = documentUri(id),
                            documentId = id,
                            name = cursor.getString(nameAt).orEmpty().ifBlank { id.substringAfterLast('/') },
                            mimeType = mime,
                            isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (cursor.isNull(sizeAt)) 0 else cursor.getLong(sizeAt),
                            modifiedMs = if (cursor.isNull(modifiedAt)) 0 else cursor.getLong(modifiedAt),
                        )
                    )
                }
            }.sortedWith(compareByDescending<LocalFileEntry> { it.isDir }.thenBy { it.name.lowercase() })
        } ?: error("The selected local folder is no longer available")
    }

    override suspend fun ensureDirectory(parentId: String, name: String): String = withContext(Dispatchers.IO) {
        list(parentId).firstOrNull { it.isDir && it.name == name }?.documentId
            ?: DocumentsContract.createDocument(
                resolver,
                documentUri(parentId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )?.let { uri -> DocumentsContract.getDocumentId(uri) }
            ?: error("Could not create local folder: $name")
    }

    override suspend fun openOutput(
        parentId: String,
        name: String,
        mimeType: String,
    ): OutputStream = withContext(Dispatchers.IO) {
        val uri = list(parentId).firstOrNull { !it.isDir && it.name == name }?.uri
            ?: DocumentsContract.createDocument(
                resolver,
                documentUri(parentId),
                mimeType.ifBlank { "application/octet-stream" },
                name,
            )
            ?: error("Could not create local file: $name")
        resolver.openOutputStream(uri, "wt") ?: error("Could not write the selected local file")
    }

    override fun openInput(entry: LocalFileEntry) =
        resolver.openInputStream(entry.uri) ?: error("Could not open local file: ${entry.name}")
}

/**
 * 全ファイルアクセスが許可された端末共有ストレージ／物理 SD カードを直接辿る。
 * canonical path を選択ルート内に制限し、リンク等で外へ抜けないようにする。
 */
internal class FileSystemTree(root: File) : LocalFileTree {
    private val root = root.canonicalFile.also {
        require(it.isDirectory) { "Local storage is not available: ${it.path}" }
    }
    override val rootId: String = this.root.path

    override suspend fun list(documentId: String): List<LocalFileEntry> = withContext(Dispatchers.IO) {
        val directory = resolve(documentId)
        check(directory.isDirectory) { "Not a folder: ${directory.path}" }
        val children = directory.listFiles() ?: error("Could not read folder: ${directory.path}")
        children.mapNotNull { child ->
            runCatching {
                val file = child.canonicalFile
                requireInsideRoot(file)
                LocalFileEntry(
                    uri = Uri.fromFile(file),
                    documentId = file.path,
                    name = file.name,
                    mimeType = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
                        else mimeTypeForLocalName(file.name),
                    isDir = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    modifiedMs = file.lastModified(),
                )
            }.getOrNull()
        }.sortedWith(compareByDescending<LocalFileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    override suspend fun ensureDirectory(parentId: String, name: String): String =
        withContext(Dispatchers.IO) {
            val directory = File(resolve(parentId), name).canonicalFile
            requireInsideRoot(directory)
            check((directory.isDirectory || directory.mkdirs()) && directory.isDirectory) {
                "Could not create local folder: $name"
            }
            directory.path
        }

    override suspend fun openOutput(
        parentId: String,
        name: String,
        mimeType: String,
    ): OutputStream = withContext(Dispatchers.IO) {
        val file = File(resolve(parentId), name).canonicalFile
        requireInsideRoot(file)
        check(!file.isDirectory) { "A local folder already uses this name: $name" }
        FileOutputStream(file, false)
    }

    override fun openInput(entry: LocalFileEntry): InputStream =
        FileInputStream(resolve(entry.documentId))

    private fun resolve(documentId: String): File =
        File(documentId).canonicalFile.also(::requireInsideRoot)

    private fun requireInsideRoot(file: File) {
        check(file.path == root.path || file.path.startsWith(root.path + File.separator)) {
            "Local path is outside the selected storage"
        }
    }
}

internal data class LocalFileEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedMs: Long,
)

internal data class LocalFolder(
    val documentId: String,
    val name: String,
)

/**
 * 端末側のファイル/フォルダをリモートへ再帰アップロードする。
 * シンボリックリンクは SAF では通常ファイルとして見えるため、provider のストリームだけを読む。
 */
internal suspend fun uploadLocalTree(
    client: RemoteFs,
    local: LocalFileTree,
    entry: LocalFileEntry,
    remoteParent: String,
    onProgress: (String) -> Unit,
    depth: Int = 0,
) {
    check(depth <= MAX_TRANSFER_DEPTH) { "Folder nesting is too deep" }
    val remotePath = RemotePath.resolve(remoteParent, entry.name)
    onProgress(entry.name)
    if (!entry.isDir) {
        withContext(Dispatchers.IO) { local.openInput(entry).use { client.upload(it, remotePath) } }
        return
    }

    val existing = client.list(remoteParent).firstOrNull { it.name == entry.name }
    when {
        existing == null -> client.mkdir(remotePath)
        !existing.isDir -> error("A remote file already uses this folder name: ${entry.name}")
    }
    for (child in local.list(entry.documentId)) {
        uploadLocalTree(client, local, child, remotePath, onProgress, depth + 1)
    }
}

/**
 * リモート側のファイル/フォルダを SAF ツリーへ再帰ダウンロードする。
 * リンクをディレクトリとして辿ると循環し得るため、リンクはフォルダ再帰の対象にしない。
 */
internal suspend fun downloadRemoteTree(
    client: RemoteFs,
    local: LocalFileTree,
    entry: SftpEntry,
    remotePath: String,
    localParentId: String,
    mimeType: (String) -> String,
    onProgress: (String) -> Unit,
    depth: Int = 0,
) {
    check(depth <= MAX_TRANSFER_DEPTH) { "Folder nesting is too deep" }
    onProgress(entry.name)
    if (!entry.isDir || entry.isLink) {
        withContext(Dispatchers.IO) {
            local.openOutput(localParentId, entry.name, mimeType(entry.name)).use {
                client.download(remotePath, it)
            }
        }
        return
    }

    val localDirId = local.ensureDirectory(localParentId, entry.name)
    for (child in client.list(remotePath).filter { it.name != "." && it.name != ".." }) {
        downloadRemoteTree(
            client = client,
            local = local,
            entry = child,
            remotePath = RemotePath.resolve(remotePath, child.name),
            localParentId = localDirId,
            mimeType = mimeType,
            onProgress = onProgress,
            depth = depth + 1,
        )
    }
}

private const val MAX_TRANSFER_DEPTH = 64

private fun mimeTypeForLocalName(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}
