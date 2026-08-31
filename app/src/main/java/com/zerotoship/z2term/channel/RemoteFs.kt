package com.zerotoship.z2term.channel

import android.content.Context
import java.io.InputStream
import java.io.OutputStream

/**
 * SFTP / FTP / WebDAV / SMB に共通する、ファイル画面から見える最小操作面。
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
    suspend fun connect(
        profile: SshProfile,
        context: Context,
        service: RemoteService? = null,
    ): RemoteFs {
        // service=null は SFTP と 0.8.438 までの直接 WebDAV/SMB の保存互換経路。
        if (service == null) return when (profile.protocol) {
            ConnectionProtocol.SSH -> SftpClient.connect(profile, context)
            ConnectionProtocol.WEBDAV -> WebDavClient.connect(profile)
            ConnectionProtocol.SMB -> SmbClient.connect(profile)
        }

        // 画面を開くサービス (VNC / RDP) はここへ来ない。来たら呼び出し側の分岐の取りこぼし。
        require(!service.protocol.opensDesktopTab) { "${service.protocol} is not a file service" }
        val route = ServiceRoute.open(profile, service, context)
        return try {
            val client = when (service.protocol) {
                RemoteServiceProtocol.FTP -> FtpClient.connect(route, service)
                RemoteServiceProtocol.SMB -> SmbClient.connect(service, route.host, route.port)
                RemoteServiceProtocol.WEBDAV -> WebDavClient.connect(service, route.host, route.port)
                RemoteServiceProtocol.VNC, RemoteServiceProtocol.RDP ->
                    error("${service.protocol} is not a file service")
            }
            RoutedRemoteFs(client, route)
        } catch (e: Throwable) {
            route.close()
            throw e
        }
    }
}

/** プロトコル本体を閉じたとき、同じ寿命の一時 SSH 転送も必ず閉じる。 */
private class RoutedRemoteFs(
    private val delegate: RemoteFs,
    private val route: ServiceRoute,
) : RemoteFs by delegate {
    override fun close() {
        runCatching { delegate.close() }
        route.close()
    }
}
