package com.zerotoship.z2term.channel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.zerotoship.z2term.net.HostAddress
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 家の中での宛先 ([SshProfile.lanHost])。
 *
 * ⭐ **外向きの宛先を家の中から使うと、ルーターの折り返し (NAT ループバック) に頼る。** 折り返さない
 * ・時々しか折り返さないルーターでは、家の Wi-Fi にいるときだけ `ConnectException` で繋がらない
 * (利用者の報告。PC からも同じ宛先が失敗することで確認)。⇒ 家の中の宛先を併記しておき、
 * **その宛先と同じネットワークにいて、実際に応答したときだけ**直接繋ぐ。どれかが欠ければ
 * 今までどおり外向きの宛先を使うので、家の外や、別の家の同じ番号の LAN で誤爆しない。
 *
 * ⚠ 家の中の宛先へは**踏み台を通さない** (家の中にいるなら踏み台を経由する理由がない)。
 * ⚠ ホスト鍵は宛先ごとに known_hosts へ載る。初回は確認が出る。確認を出せない常駐トンネル
 * ([knownOnly]) では、家の中の宛先の鍵が未確認なら外向きの宛先を使う。
 * ⚠ 名前解決と試し接続を行うので IO スレッドから呼ぶ ([SshSessionFactory.create] と同じ約束)。
 */
internal object LanRoute {
    private const val TAG = "LanRoute"
    private const val PROBE_TIMEOUT_MS = 1500

    data class Endpoint(val host: String, val port: Int)

    /** 家の中の宛先のポート。空 (0) なら外向きと同じ番号。 */
    fun lanPort(profile: SshProfile): Int = profile.lanPort.takeIf { it in 1..65535 } ?: profile.port

    /** 家の中の宛先を使うなら、その宛先。使わないなら null。 */
    fun choose(context: Context, profile: SshProfile, knownOnly: Boolean): Endpoint? {
        if (!profile.hasSsh || profile.lanHost.isBlank()) return null
        val host = HostAddress.normalize(profile.lanHost)
        val port = lanPort(profile)
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
        if (!sameNetwork(address, localNetworks(context))) return null
        if (knownOnly && !isKnown(context, host, port)) return null
        if (!answers(address, port)) {
            Log.i(TAG, "LAN address did not answer; using the usual one")
            return null
        }
        Log.i(TAG, "using the LAN address for ${profile.name}")
        return Endpoint(host, port)
    }

    /** [target] がいずれかのネットワーク (アドレスと接頭辞の長さ) の中にあるか。 */
    fun sameNetwork(target: InetAddress, networks: List<Pair<InetAddress, Int>>): Boolean =
        networks.any { (local, prefix) -> inPrefix(target, local, prefix) }

    fun inPrefix(target: InetAddress, local: InetAddress, prefix: Int): Boolean {
        val a = target.address
        val b = local.address
        if (a.size != b.size || prefix !in 0..a.size * 8) return false
        val whole = prefix / 8
        for (i in 0 until whole) if (a[i] != b[i]) return false
        val rest = prefix % 8
        if (rest == 0) return true
        val mask = (0xFF shl (8 - rest)) and 0xFF
        return (a[whole].toInt() and mask) == (b[whole].toInt() and mask)
    }

    /** モバイル通信以外の今のネットワーク (Wi-Fi・有線・VPN) のアドレスと接頭辞。 */
    private fun localNetworks(context: Context): List<Pair<InetAddress, Int>> = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION") // getAllNetworks: the callback API cannot answer "now" synchronously.
        cm.allNetworks.filter { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == false
        }.flatMap { network ->
            cm.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address to it.prefixLength }
        }
    }.getOrDefault(emptyList())

    private fun isKnown(context: Context, host: String, port: Int): Boolean = runCatching {
        KnownHostsHolder.repository(context).getHostKey(HostAddress.knownHostKey(host, port), null).isNotEmpty()
    }.getOrDefault(false)

    private fun answers(address: InetAddress, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(address, port), PROBE_TIMEOUT_MS) }
        true
    }.getOrDefault(false)
}
