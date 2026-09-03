package com.zerotoship.z2term.channel

import android.util.Log
import com.jcraft.jsch.Session
import com.zerotoship.z2term.net.HostAddress

/**
 * [PortForward] を JSch のセッションへ実際に張る 1 か所。
 *
 * ⚠ **ここを共有しているのが要点。** 以前は SSH タブ ([SshChannel]) が `reverse` を見ずに
 * 必ず `-L` を張っており、`-R` を書いた接続先をタブから開くと**向きが黙って逆になっていた**
 * (常駐トンネル [com.zerotoship.z2term.service.TunnelManager] だけが正しく分岐していた)。
 * 張り方が 2 か所にあると、片方だけ直る事故がまた起きる。
 */
object PortForwarding {

    private const val TAG = "PortForwarding"

    /**
     * @property established 張れた転送の説明 (UI バナー用。`-L 127.0.0.1:8080 → localhost:80`)
     * @property failed 張れなかった転送。常駐は**これを持っておいて張り直す**。
     */
    data class Result(
        val established: List<String>,
        val failed: List<PortForward>,
    )

    /**
     * まとめて張る。**1 本失敗しても残りは張る** — 転送が 1 つ使えないことと、接続そのものが
     * 使えないことは別。
     *
     * ⚠ `-L` の待ち受けポートに 0 を書くと OS が空き番号を選ぶので、説明には**実際に割り当て
     * られた番号**を出す (0 のままだと「どこへ繋げばいいのか」が分からない)。
     */
    fun apply(session: Session, forwards: List<PortForward>): Result {
        val established = ArrayList<String>(forwards.size)
        val failed = ArrayList<PortForward>()
        forwards.forEach { fwd ->
            runCatching {
                if (fwd.reverse) {
                    session.setPortForwardingR(
                        HostAddress.normalize(fwd.bindAddress),
                        fwd.remotePort,
                        HostAddress.normalize(fwd.remoteHost),
                        fwd.localPort,
                    )
                    fwd.describe()
                } else {
                    val assigned = session.setPortForwardingL(
                        HostAddress.normalize(fwd.bindAddress),
                        fwd.localPort,
                        HostAddress.normalize(fwd.remoteHost),
                        fwd.remotePort,
                    )
                    "-L ${HostAddress.hostPort(fwd.bindAddress, assigned)} → " +
                        HostAddress.hostPort(fwd.remoteHost, fwd.remotePort)
                }
            }.onSuccess {
                established += it
                Log.i(TAG, "forward up: $it")
            }.onFailure { e ->
                Log.w(TAG, "forward failed (${fwd.describe()}): ${e.message}")
                failed += fwd
            }
        }
        return Result(established, failed)
    }
}
