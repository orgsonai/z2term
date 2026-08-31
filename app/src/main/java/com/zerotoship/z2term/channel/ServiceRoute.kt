package com.zerotoship.z2term.channel

import android.content.Context
import com.jcraft.jsch.Session
import com.zerotoship.z2term.gui.RemoteTarget
import com.zerotoship.z2term.gui.rdp.RdpCertificateTrust
import com.zerotoship.z2term.gui.rdp.RdpTarget
import com.zerotoship.z2term.gui.rfb.VncTarget
import com.zerotoship.z2term.net.HostAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet

/**
 * FTP / SMB / WebDAV / VNC / RDP の 1 接続ぶんの経路。
 *
 * SSH 経由なら独立した SSH セッションへ一時的な `-L` を張り、直通なら接続先をそのまま返す。
 * ファイル画面や VNC タブを閉じると [close] されるため、指定ローカルポートを使い終えた後も
 * 待ち受けだけが残ることはない。
 */
class ServiceRoute private constructor(
    private val service: RemoteService,
    private val sshSession: Session?,
    val host: String,
    val port: Int,
) : AutoCloseable {
    private val extraLocalPorts = CopyOnWriteArraySet<Int>()

    val tunneled: Boolean get() = sshSession != null

    /**
     * FTP のデータ接続など、接続中に決まる追加ポートへ Socket を開く。
     * SSH 経由では空きローカルポートを追加で払い出し、Socket を閉じると転送も消す。
     */
    fun openSocket(remotePort: Int, timeoutMs: Int = CONNECT_TIMEOUT_MS): RoutedSocket {
        val session = sshSession
        if (session == null) {
            // 直通時は制御接続と同じ SSH ホストへ接続する。FTP の PASV データ接続だけ
            // サービス個別ホストへ逸れると、制御接続は成功しても一覧取得が失敗する。
            return RoutedSocket(connect(host, remotePort, timeoutMs), null)
        }
        val assigned = session.setPortForwardingL(
            LOOPBACK, 0, HostAddress.normalize(service.host), remotePort
        )
        extraLocalPorts += assigned
        return try {
            RoutedSocket(connect(LOOPBACK, assigned, timeoutMs)) {
                extraLocalPorts -= assigned
                runCatching { session.delPortForwardingL(LOOPBACK, assigned) }
            }
        } catch (e: Throwable) {
            extraLocalPorts -= assigned
            runCatching { session.delPortForwardingL(LOOPBACK, assigned) }
            throw e
        }
    }

    override fun close() {
        val session = sshSession ?: return
        extraLocalPorts.toList().forEach { localPort ->
            runCatching { session.delPortForwardingL(LOOPBACK, localPort) }
        }
        extraLocalPorts.clear()
        runCatching { session.disconnect() }
    }

    class RoutedSocket(
        val socket: Socket,
        private val onClose: (() -> Unit)?,
    ) : AutoCloseable {
        override fun close() {
            runCatching { socket.close() }
            onClose?.invoke()
        }
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 15_000

        suspend fun open(
            sshProfile: SshProfile,
            service: RemoteService,
            context: Context,
        ): ServiceRoute = withContext(Dispatchers.IO) {
            require(service.remotePort in 1..65535) { "Invalid remote port" }
            require(service.localPort in 0..65535) { "Invalid local port" }
            val targetHost = HostAddress.normalize(service.connectionHost(sshProfile))
            require(targetHost.isNotBlank()) { "${service.protocol} host is required" }

            if (!service.useSshTunnel) {
                return@withContext ServiceRoute(
                    service = service,
                    sshSession = null,
                    host = targetHost,
                    port = service.remotePort,
                )
            }
            require(sshProfile.hasSsh) { "SSH profile is required for port forwarding" }

            val session = SshSessionFactory.create(sshProfile, context)
            session.connect(SshSessionFactory.CONNECT_TIMEOUT_MS)
            try {
                val assigned = session.setPortForwardingL(
                    LOOPBACK,
                    service.localPort,
                    targetHost,
                    service.remotePort,
                )
                ServiceRoute(
                    service = service,
                    sshSession = session,
                    host = LOOPBACK,
                    port = assigned,
                )
            } catch (e: Throwable) {
                runCatching { session.disconnect() }
                throw e
            }
        }

        private fun connect(host: String, port: Int, timeoutMs: Int): Socket =
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), timeoutMs)
            }
    }
}

object RemoteServiceConnector {
    /**
     * GUI タブを開くサービス (VNC / RDP) の接続先を、SSH 転送ごと開いて返す。
     *
     * 呼び出し側はプロトコルで分岐せず、[RemoteServiceProtocol.opensDesktopTab] を見て
     * ここへ渡すだけでよい。
     */
    suspend fun desktopTarget(
        sshProfile: SshProfile,
        service: RemoteService,
        context: Context,
    ): RemoteTarget = when (service.protocol) {
        RemoteServiceProtocol.VNC -> vncTarget(sshProfile, service, context)
        RemoteServiceProtocol.RDP -> rdpTarget(sshProfile, service, context)
        else -> throw IllegalArgumentException("${service.protocol} does not open a desktop tab")
    }

    suspend fun vncTarget(
        sshProfile: SshProfile,
        service: RemoteService,
        context: Context,
    ): VncTarget {
        require(service.protocol == RemoteServiceProtocol.VNC) { "VNC service is required" }
        val route = ServiceRoute.open(sshProfile, service, context)
        return VncTarget(
            host = route.host,
            port = route.port,
            password = service.password,
            name = service.name.ifBlank { sshProfile.name.ifBlank { "VNC" } },
            transportCloser = { route.close() },
        )
    }

    /**
     * RDP の接続先。VNC と違う点は 2 つだけ。
     *
     * - **画面の大きさをこちらが決める** — RDP は接続のたびに新しいセッションを作らせるので、
     *   端末の画面から出した値 ([RdpTarget.fitDesktopSize]) を要求する。
     * - **証明書を相手ごとに覚える** — 覚える名前は SSH 転送を通す**前**の本来の宛先にする
     *   (転送中の `127.0.0.1:<毎回変わるポート>` で覚えると毎回「初めての相手」になる)。
     */
    suspend fun rdpTarget(
        sshProfile: SshProfile,
        service: RemoteService,
        context: Context,
    ): RdpTarget {
        require(service.protocol == RemoteServiceProtocol.RDP) { "RDP service is required" }
        val app = context.applicationContext
        val trustKey = HostAddress.hostPort(
            service.connectionHost(sshProfile),
            service.remotePort,
        )
        val metrics = context.resources.displayMetrics
        val (width, height) = RdpTarget.fitDesktopSize(metrics.widthPixels, metrics.heightPixels)
        val route = ServiceRoute.open(sshProfile, service, context)
        return RdpTarget(
            host = route.host,
            port = route.port,
            user = service.user,
            password = service.password,
            domain = service.domain,
            width = width,
            height = height,
            name = service.name.ifBlank { sshProfile.name.ifBlank { "RDP" } },
            trustKey = trustKey,
            certificateVerifier = { certificate ->
                RdpCertificateTrust.verify(app, trustKey, certificate)
            },
            transportCloser = { route.close() },
        )
    }
}
