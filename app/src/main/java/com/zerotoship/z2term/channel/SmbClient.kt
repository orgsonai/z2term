package com.zerotoship.z2term.channel

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zerotoship.z2term.net.HostAddress
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** SMB2/3 の共有を共通ファイル画面へ載せるクライアント。SMB1 は使用しない。 */
class SmbClient private constructor(
    private val smbClient: SMBClient,
    private val connection: Connection,
    private val session: Session,
    private val share: DiskShare,
) : RemoteFs {
    override val home: String = "/"

    @Volatile
    private var open: Boolean = true

    override val isAlive: Boolean
        get() = open && connection.isConnected && share.isConnected

    override suspend fun list(path: String): List<SftpEntry> = io {
        val entries = share.list(relativePath(path))
            .asSequence()
            .filterNot { it.fileName == "." || it.fileName == ".." }
            .map { entry ->
                val isDirectory = entry.fileAttributes and
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                SftpEntry(
                    name = entry.fileName,
                    isDir = isDirectory,
                    isLink = false,
                    size = if (isDirectory) 0 else entry.endOfFile,
                    mtimeSec = entry.lastWriteTime.toEpochMillis() / 1000,
                    permissions = "",
                )
            }
            .sortedWith(compareByDescending<SftpEntry> { it.isDir }.thenBy { it.name.lowercase() })
            .toMutableList()
        if (path != "/") entries.add(0, SftpEntry("..", true, false, 0, 0, ""))
        entries
    }

    override suspend fun download(remotePath: String, sink: OutputStream) = io {
        share.openFile(
            relativePath(remotePath),
            EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        ).use { file ->
            file.inputStream.use { it.copyTo(sink) }
        }
        Unit
    }

    override suspend fun upload(source: InputStream, remotePath: String) = io {
        share.openFile(
            relativePath(remotePath),
            EnumSet.of(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_WRITE_ATTRIBUTES),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        ).use { file ->
            file.outputStream.use { source.copyTo(it) }
        }
        Unit
    }

    override suspend fun mkdir(path: String) = io {
        share.mkdir(relativePath(path))
    }

    override suspend fun rename(from: String, to: String) = io {
        val source = relativePath(from)
        val options = if (share.folderExists(source)) {
            EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)
        } else {
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        }
        share.open(
            source,
            EnumSet.of(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            options,
        ).use { entry ->
            entry.rename(relativePath(to), true)
        }
    }

    override suspend fun rm(path: String) = io {
        share.rm(relativePath(path))
    }

    override suspend fun rmdir(path: String) = io {
        share.rmdir(relativePath(path), false)
    }

    override fun close() {
        if (!open) return
        open = false
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { smbClient.close() }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Throwable) {
            if (!connection.isConnected) open = false
            throw e
        }
    }

    companion object {
        suspend fun connect(profile: SshProfile): SmbClient = withContext(Dispatchers.IO) {
            connectInternal(
                host = profile.host,
                port = profile.port,
                shareName = profile.remotePath,
                domain = profile.domain,
                user = profile.user,
                password = profile.password,
            )
        }

        suspend fun connect(service: RemoteService, routeHost: String, routePort: Int): SmbClient =
            withContext(Dispatchers.IO) {
                connectInternal(
                    host = routeHost,
                    port = routePort,
                    shareName = service.path,
                    domain = service.domain,
                    user = service.user,
                    password = service.password,
                )
            }

        private fun connectInternal(
            host: String,
            port: Int,
            shareName: String,
            domain: String,
            user: String,
            password: String,
        ): SmbClient {
            require(host.isNotBlank()) { "SMB host is required" }
            require(port in 1..65535) { "SMB port is invalid" }
            val normalizedShare = shareName.trim().trim('/', '\\')
            require(normalizedShare.isNotBlank()) { "SMB share is required" }
            require('/' !in normalizedShare && '\\' !in normalizedShare) {
                "SMB share must be a share name, not a path"
            }

            val anonymous = user.isBlank()
            val configBuilder = SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(35, TimeUnit.SECONDS)
                // SSH 転送先から DFS の別ホストへ迂回するとトンネル外へ出るため無効化する。
                .withDfsEnabled(false)
            if (anonymous) {
                // SMBJ 0.15.0 は Samba の SMB 3.x 匿名セッションで鍵導出に失敗する。
                // 匿名時だけ SMB 2.1 に限定する。ユーザー認証時は SMB 3.x を含む既定値を維持。
                configBuilder.withDialects(SMB2Dialect.SMB_2_1)
            }
            val config = configBuilder.build()
            val client = SMBClient(config)
            var connection: Connection? = null
            var session: Session? = null
            var diskShare: DiskShare? = null
            try {
                connection = client.connect(HostAddress.normalize(host), port)
                val authentication = if (anonymous) {
                    AuthenticationContext.anonymous()
                } else {
                    AuthenticationContext(user, password.toCharArray(), domain)
                }
                session = connection.authenticate(authentication)
                diskShare = session.connectShare(normalizedShare) as? DiskShare
                    ?: error("SMB share is not a disk share")
                // 接続時点で共有ルートを列挙し、認証・共有名の誤りを早期に表示する。
                diskShare.list("")
                return SmbClient(client, connection, session, diskShare)
            } catch (e: Throwable) {
                runCatching { diskShare?.close() }
                runCatching { session?.close() }
                runCatching { connection?.close() }
                runCatching { client.close() }
                throw e
            }
        }

        internal fun relativePath(path: String): String =
            RemotePath.segments(path).joinToString("\\")
    }
}
