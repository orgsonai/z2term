package com.zerotoship.z2term.channel

import jcifs.CIFSContext
import jcifs.SmbResource
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Properties

/** SMB2/3 の共有を共通ファイル画面へ載せるクライアント。SMB1 は明示的に無効。 */
class SmbClient private constructor(
    private val context: CIFSContext,
    private val rootUrl: String
) : RemoteFs {
    override val home: String = "/"
    @Volatile
    override var isAlive: Boolean = true
        private set

    override suspend fun list(path: String): List<SftpEntry> = io {
        val out = ArrayList<SftpEntry>()
        resource(path).use { directory ->
            directory.children().use { children ->
                while (children.hasNext()) {
                    children.next().use { child ->
                        val name = child.name.trimEnd('/')
                        if (name.isNotEmpty()) {
                            val isDir = child.isDirectory
                            out += SftpEntry(
                                name = name,
                                isDir = isDir,
                                isLink = false,
                                size = if (isDir) 0 else child.length(),
                                mtimeSec = child.lastModified() / 1000,
                                permissions = ""
                            )
                        }
                    }
                }
            }
        }
        out.sortWith(compareByDescending<SftpEntry> { it.isDir }.thenBy { it.name.lowercase() })
        if (path != "/") out.add(0, SftpEntry("..", true, false, 0, 0, ""))
        out
    }

    override suspend fun download(remotePath: String, sink: OutputStream) = io {
        resource(remotePath).use { file ->
            file.openInputStream().use { it.copyTo(sink) }
        }
        Unit
    }

    override suspend fun upload(source: InputStream, remotePath: String) = io {
        resource(remotePath).use { file ->
            file.openOutputStream().use { source.copyTo(it) }
        }
        Unit
    }

    override suspend fun mkdir(path: String) = io {
        resource(path).use { it.mkdir() }
    }

    override suspend fun rename(from: String, to: String) = io {
        resource(from).use { source ->
            resource(to).use { destination ->
                source.renameTo(destination, true)
            }
        }
    }

    override suspend fun rm(path: String) = io {
        resource(path).use {
            check(!it.isDirectory) { "Not a file" }
            it.delete()
        }
    }

    override suspend fun rmdir(path: String) = io {
        resource(path).use { directory ->
            directory.children().use { children ->
                check(!children.hasNext()) { "Directory is not empty" }
            }
            directory.delete()
        }
    }

    override fun close() {
        isAlive = false
        runCatching { context.close() }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Throwable) {
            isAlive = false
            throw e
        }
    }

    private fun resource(path: String): SmbResource {
        val suffix = RemotePath.segments(path).joinToString("/") { encodeSegment(it) }
        return context.get(if (suffix.isEmpty()) rootUrl else "$rootUrl$suffix")
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        suspend fun connect(profile: SshProfile): SmbClient = withContext(Dispatchers.IO) {
            require(profile.host.isNotBlank()) { "SMB host is required" }
            val share = profile.remotePath.trim('/')
            require(share.isNotBlank()) { "SMB share is required" }

            val properties = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.connTimeout", "15000")
                setProperty("jcifs.smb.client.responseTimeout", "30000")
                setProperty("jcifs.smb.client.soTimeout", "35000")
            }
            val base = BaseContext(PropertyConfiguration(properties))
            val authenticated = if (profile.user.isBlank()) {
                base.withGuestCrendentials()
            } else {
                base.withCredentials(
                    NtlmPasswordAuthenticator(profile.domain, profile.user, profile.password)
                )
            }
            val authority = buildString {
                append(profile.host.trim())
                if (profile.port != 445) append(':').append(profile.port)
            }
            val root = "smb://$authority/${encodeStatic(share)}/"
            val client = SmbClient(authenticated, root)
            try {
                client.resource("/").use { check(it.exists()) { "SMB share not found" } }
                client
            } catch (e: Throwable) {
                client.close()
                throw e
            }
        }

        private fun encodeStatic(path: String): String =
            path.split('/').filter { it.isNotBlank() }.joinToString("/") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
    }
}
