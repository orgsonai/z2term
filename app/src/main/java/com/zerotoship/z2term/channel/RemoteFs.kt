package com.zerotoship.z2term.channel

import android.content.Context
import java.io.InputStream
import java.io.OutputStream

/**
 * SFTP / WebDAV / SMB に共通する、ファイル画面から見える最小操作面。
 *
 * UI はプロトコル固有のセッションを知らず、この 8 操作だけを使う。
 */
interface RemoteFs {
    val home: String
    val isAlive: Boolean

    suspend fun list(path: String): List<SftpEntry>
    suspend fun download(remotePath: String, sink: OutputStream)
    suspend fun upload(source: InputStream, remotePath: String)
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun rm(path: String)
    suspend fun rmdir(path: String)
    fun close()
}

object RemotePath {
    /** ".." は 1 階層上がる。戻り値は常に "/" 始まり。 */
    fun resolve(base: String, name: String): String {
        if (name == "..") {
            val trimmed = base.trimEnd('/')
            if (trimmed.isEmpty()) return "/"
            val parent = trimmed.substringBeforeLast('/', "")
            return if (parent.isEmpty()) "/" else parent
        }
        val b = base.trimEnd('/')
        return "$b/$name"
    }

    fun segments(path: String): List<String> =
        path.split('/').filter { it.isNotBlank() && it != "." && it != ".." }
}

object RemoteFsFactory {
    suspend fun connect(profile: SshProfile, context: Context): RemoteFs = when (profile.protocol) {
        ConnectionProtocol.SSH -> SftpClient.connect(profile, context)
        ConnectionProtocol.WEBDAV -> WebDavClient.connect(profile)
        ConnectionProtocol.SMB -> SmbClient.connect(profile)
    }
}
