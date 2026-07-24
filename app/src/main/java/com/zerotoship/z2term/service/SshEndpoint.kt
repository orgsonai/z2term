package com.zerotoship.z2term.service

import android.content.Context
import com.zerotoship.z2term.proot.Z2TERM_SSHD_PORT
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 「PC からこの端末へ入るための ssh 接続先」を組み立てる共通ロジック。
 *
 * 設定シートの SSH ヘルパー ([com.zerotoship.z2term.ui.settings.SshAccessHelper]) と
 * ホーム画面ウィジェット ([com.zerotoship.z2term.widget.StatusWidgetProvider]) が同じ値を
 * 出す必要があるため、ここに 1 本化する (片方だけ直して食い違う状態を作らない)。
 *
 * どちらも**表示のみ**で、sshd の起動はターミナルからコマンドで行う。
 */
object SshEndpoint {

    /** ネットワークインターフェース 1 本の IPv4 アドレス。[name] は `wlan0` など。 */
    data class Nic(val name: String, val ip: String)

    /**
     * NetworkInterface を全列挙して、ループバック/リンクローカルでない IPv4 を返す。
     * 通常 Wi-Fi の `wlan0` が `192.168.x.x` を返す。
     */
    fun nics(): List<Nic> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().map { iface.name to it } }
            .filter { (_, addr) -> addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress }
            .mapNotNull { (name, addr) -> addr.hostAddress?.let { Nic(name, it) } }
    }.getOrDefault(emptyList())

    /**
     * [distroId] の `/etc/ssh/sshd_config` から `Port` を読む。
     * `sshd` コマンド (dropbear ラッパー) と同じ優先順 (config の Port、無ければ既定 2222)。
     * コメント行や `PortForwarding` 等の別ディレクティブは無視する。
     */
    fun configuredPort(context: Context, distroId: String): Int {
        val cfg = File(context.filesDir, "distros/$distroId/etc/ssh/sshd_config")
        if (!cfg.isFile) return Z2TERM_SSHD_PORT
        return runCatching {
            cfg.readLines().firstNotNullOfOrNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2 && parts[0].equals("Port", ignoreCase = true)) parts[1].toIntOrNull()
                else null
            }
        }.getOrNull() ?: Z2TERM_SSHD_PORT
    }

    /** `ssh -p <port> root@<ip>` の 1 行。IP が取れなければ null。 */
    fun sshCommand(context: Context, distroId: String): String? {
        val ip = nics().firstOrNull()?.ip ?: return null
        return "ssh -p ${configuredPort(context, distroId)} root@$ip"
    }

}
