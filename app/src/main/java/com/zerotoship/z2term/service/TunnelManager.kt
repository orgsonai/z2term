package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.jcraft.jsch.Session
import com.zerotoship.z2term.channel.KnownHostsHolder
import com.zerotoship.z2term.channel.PortForward
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.net.HostAddress
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.channel.SshSessionFactory
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * 常駐トンネル (A2)。**SSH タブを閉じてもポート転送を生かし続ける。**
 *
 * 現状の SSH タブ ([com.zerotoship.z2term.channel.SshChannel]) は「接続 → 転送を張る → 画面用の
 * shell を開く」の順で、**転送と画面が 1 本のセッションにぶら下がっている**。だからタブを閉じると
 * 転送も消える。ここは画面を持たない別のセッションを持ち、[ServerDaemonService] の常駐枠
 * (FGS 通知 / WakeLock / WifiLock / 端末起動時の復帰) に相乗りする。**常駐を新規に増やさない。**
 *
 * ⚠ **守っていること** (引き継ぎ書 §6):
 *  1. **明示 opt-in**: [SshProfile.residentTunnel] が true のプロファイルだけ対象。
 *  2. **known_hosts に登録済みのホストだけ**。常駐中はホスト鍵の確認ダイアログを出せないので、
 *     未知のホストは**張らずに理由を残す**（黙って信用しない）。先に SSH タブで 1 度繋いで
 *     ホスト鍵を承認してもらう。
 *  3. **切断したら指数バックオフで再接続**（[backoffMs]）。
 */
object TunnelManager {

    private const val TAG = "TunnelManager"

    private const val CONNECT_TIMEOUT_MS = 15_000

    /** 生存確認の間隔。これを超えて切れていたら再接続へ回す。 */
    private const val POLL_MS = 5_000L

    /** 再接続の初回待ち時間。 */
    internal const val BACKOFF_BASE_MS = 5_000L

    /** 再接続の待ち時間の上限 (5 分)。 */
    internal const val BACKOFF_MAX_MS = 300_000L

    /**
     * ⭐ **keepalive の間隔 (0.8.367)。この値には実測の裏付けがある。**
     *
     * 常駐トンネルは繋いだあと何も流さない状態になりうる。SSH は黙っていても切れないので一見
     * それで良いのだが、**端末が黙ると無線チップが省電力へ入り、同じ LAN の他機から見えなく
     * なる**（ARP はブロードキャストなので、省電力に入った子機が取りこぼす）。CPU は起きていて
     * FGS も WakeLock も効いているのに、電波だけが消える。
     *
     * 実測 (画面消灯・充電中・Wi-Fi・常駐サーバー稼働中):
     *
     * | 端末側の送信 | 期間 | 他機から届かなかった率 |
     * |---|---|---|
     * | 無し (黙っている) | 9 分 | **37%** |
     * | 10 秒ごとに外へ 1 本 | 19 分 | **1%** |
     *
     * ⚠ **[ServerDaemonService] の WifiLock では直せない。** `WIFI_MODE_FULL_HIGH_PERF` は
     * 非機能化して `WIFI_MODE_FULL_LOW_LATENCY` に読み替えられ、そちらは**画面が点いていて
     * アプリが前面のときだけ**効く（＝この用途では常に無効）。
     * **端末側から定期的に喋ることだけが効く。**
     */
    internal const val KEEPALIVE_MS = 10_000

    /**
     * 省電力モード ([AppSettings.serversLowPower]) のときの keepalive 間隔。
     *
     * 省電力 ON は「電池優先・到達性は落ちてよい」という意思表示なので、切れていないかを見る
     * ためだけの間隔まで広げる。**この間隔では [KEEPALIVE_MS] の「消えなくなる」効果は
     * 期待できない。**
     */
    internal const val KEEPALIVE_LOW_POWER_MS = 60_000

    /** 何回続けて返事が無ければ切れたとみなすか。[KEEPALIVE_MS] × これが切断検知にかかる時間。 */
    internal const val KEEPALIVE_COUNT_MAX = 3

    /**
     * 張れなかった転送を張り直しに行く間隔。
     *
     * ⚠ **`-R` は繋ぎ直した直後の 1 回が失敗しうる。** 端末側が落ちても接続先の sshd は
     * しばらく待ち受けポートを握ったままなので、`setPortForwardingR` が「そのポートは使用中」
     * で弾かれる。ここで諦めると**繋がっているのに転送だけ死んだ**状態が固定してしまうため、
     * セッションは畳まずに張り直しへ回す（畳むと生きている他の転送まで巻き添えになる）。
     */
    private const val FORWARD_RETRY_MS = 30_000L

    /** トンネル 1 本の状態。UI と通知に出す。 */
    data class Status(
        val profileId: String,
        val name: String,
        val connected: Boolean,
        /** 張れている転送の説明、または張れない理由。 */
        val detail: String,
        val retries: Int,
    )

    private class Worker(val thread: Thread) {
        @Volatile var stop = false
        @Volatile var session: Session? = null
    }

    private val workers = ConcurrentHashMap<String, Worker>()
    private val statuses = ConcurrentHashMap<String, Status>()

    /** 常駐トンネルが 1 本でも動いているか (通知の文言に使う)。 */
    val isRunning: Boolean get() = workers.isNotEmpty()

    /** いまの状態の一覧 (名前順)。 */
    fun statuses(): List<Status> = statuses.values.sortedBy { it.name }

    /**
     * [n] 回目の再接続までの待ち時間 (ミリ秒)。0 回目 = 最初の失敗直後。
     *
     * 5 秒から倍々にして 5 分で頭打ち。回線が落ちている間に総当たりしないための指数バックオフ。
     */
    internal fun backoffMs(n: Int): Long {
        if (n <= 0) return BACKOFF_BASE_MS
        // 2^n をオーバーフローさせずに上限で止める。
        var ms = BACKOFF_BASE_MS
        repeat(n.coerceAtMost(16)) {
            ms *= 2
            if (ms >= BACKOFF_MAX_MS) return BACKOFF_MAX_MS
        }
        return ms.coerceAtMost(BACKOFF_MAX_MS)
    }

    /** keepalive の間隔 (ミリ秒)。省電力モードでは広げる。 */
    internal fun keepAliveMs(lowPower: Boolean): Int =
        if (lowPower) KEEPALIVE_LOW_POWER_MS else KEEPALIVE_MS

    /**
     * 状態に出す 1 行。**張れなかった転送には `✗` を付ける**ので、繋がってはいるが転送だけ
     * 死んでいる状態が一覧で分かる ([FORWARD_RETRY_MS] ごとに張り直しに行く)。
     */
    internal fun detailOf(forwards: List<PortForward>, pending: List<PortForward>): String =
        forwards.joinToString(" / ") { f ->
            if (pending.contains(f)) "✗ ${f.describe()}" else f.describe()
        }

    /**
     * 常駐対象のプロファイルを読み直して、トンネルを張り直す。
     *
     * 既に動いているものはそのまま (張り替えると転送が一瞬切れる)。対象から外れたものは止める。
     * **バックグラウンドスレッドから呼ぶこと** (DataStore を runBlocking で読む)。
     */
    fun reload(context: Context) {
        val app = context.applicationContext
        val profiles = runCatching {
            runBlocking { SshProfileStore(app).profiles.first() }
        }.getOrDefault(emptyList())
        val wanted = profiles.filter { it.residentTunnel && it.forwards.isNotEmpty() }

        // 対象外になったものを止める。
        workers.keys.filter { id -> wanted.none { it.id == id } }.forEach { stopOne(it) }

        wanted.forEach { profile ->
            if (workers.containsKey(profile.id)) return@forEach
            startOne(app, profile)
        }
    }

    /** すべて止める。 */
    fun stopAll() {
        workers.keys.toList().forEach { stopOne(it) }
        statuses.clear()
    }

    private fun stopOne(id: String) {
        val w = workers.remove(id) ?: return
        w.stop = true
        runCatching { w.session?.disconnect() }
        w.thread.interrupt()
        statuses.remove(id)
    }

    private fun startOne(context: Context, profile: SshProfile) {
        // known_hosts に無いホストは張らない (常駐中に確認ダイアログを出せないため)。
        if (!isKnownHost(context, profile)) {
            statuses[profile.id] = Status(
                profileId = profile.id,
                name = profile.name,
                connected = false,
                detail = "unknown host key — connect once from the SSH tab first",
                retries = 0,
            )
            Log.w(TAG, "skip ${profile.name}: host key not in known_hosts")
            return
        }

        lateinit var worker: Worker
        val t = Thread {
            var retries = 0
            while (!worker.stop) {
                try {
                    val session = SshSessionFactory.create(profile, context)
                    // ⭐ keepalive。**繋ぐ前に**入れること (JSch は接続の最後にこの値をソケットの
                    // 読み取りタイムアウトへ写し、時間切れのたびに keepalive を 1 本送る)。
                    // 省電力モードは接続のたびに読み直す (常駐中に切り替えられても次で効く)。
                    val interval = keepAliveMs(lowPowerNow(context))
                    session.serverAliveInterval = interval
                    session.serverAliveCountMax = KEEPALIVE_COUNT_MAX
                    session.connect(CONNECT_TIMEOUT_MS)
                    worker.session = session
                    retries = 0

                    // 張れなかったものは捨てずに持っておき、繋がったまま張り直す。
                    var pending = applyForwards(session, profile.forwards)
                    statuses[profile.id] = Status(
                        profile.id, profile.name, true, detailOf(profile.forwards, pending), 0
                    )
                    Log.i(TAG, "tunnel up: ${profile.name} (keepalive ${interval}ms)")

                    // 生きている間は待つだけ。転送は JSch 側のスレッドが捌く。
                    var sinceForwardRetry = 0L
                    while (!worker.stop && session.isConnected) {
                        Thread.sleep(POLL_MS)
                        if (pending.isEmpty()) continue
                        sinceForwardRetry += POLL_MS
                        if (sinceForwardRetry < FORWARD_RETRY_MS) continue
                        sinceForwardRetry = 0L
                        val before = pending.size
                        pending = applyForwards(session, pending)
                        if (pending.size != before) {
                            statuses[profile.id] = Status(
                                profile.id, profile.name, true,
                                detailOf(profile.forwards, pending), 0
                            )
                        }
                    }

                    runCatching { session.disconnect() }
                    worker.session = null
                    if (worker.stop) break
                    Log.w(TAG, "tunnel dropped: ${profile.name}")
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "tunnel failed: ${profile.name}: ${e.message}")
                    statuses[profile.id] = Status(
                        profile.id, profile.name, false, e.message.orEmpty(), retries
                    )
                }
                if (worker.stop) break
                val wait = backoffMs(retries)
                retries++
                statuses[profile.id] = statuses[profile.id]?.copy(connected = false, retries = retries)
                    ?: Status(profile.id, profile.name, false, "reconnecting", retries)
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    break
                }
            }
            statuses.remove(profile.id)
        }.apply { isDaemon = true; name = "tunnel-${profile.name}" }

        worker = Worker(t)
        workers[profile.id] = worker
        t.start()
    }

    /** 転送を張って、**張れなかったもの**を返す。空リストなら全部張れた。 */
    private fun applyForwards(session: Session, forwards: List<PortForward>): List<PortForward> {
        val failed = ArrayList<PortForward>()
        forwards.forEach { f ->
            runCatching {
                if (f.reverse) {
                    session.setPortForwardingR(
                        HostAddress.normalize(f.bindAddress),
                        f.remotePort,
                        HostAddress.normalize(f.remoteHost),
                        f.localPort,
                    )
                } else {
                    session.setPortForwardingL(
                        HostAddress.normalize(f.bindAddress),
                        f.localPort,
                        HostAddress.normalize(f.remoteHost),
                        f.remotePort,
                    )
                }
            }.onFailure { e ->
                Log.w(TAG, "forward failed (${f.describe()}): ${e.message}")
                failed += f
            }
        }
        return failed
    }

    /** 省電力モードか。読めなければ OFF 扱い ([ServerDaemonService] のロック取得と同じ既定)。 */
    private fun lowPowerNow(context: Context): Boolean = runCatching {
        runBlocking { AppSettings(context).flow.first().serversLowPower }
    }.getOrDefault(false)

    /**
     * このホストの鍵が known_hosts にあるか。
     *
     * 常駐は「黙って新しい鍵を信用する」をやってはいけない場面なので、ここが false なら張らない。
     */
    private fun isKnownHost(context: Context, profile: SshProfile): Boolean = runCatching {
        val repo = KnownHostsHolder.repository(context)
        // JSch は既定ポート以外を `[host]:port` の形で記録する (SSH タブでの承認時と同じ形)。
        val key = HostAddress.knownHostKey(profile.host, profile.port)
        repo.getHostKey(key, null).isNotEmpty()
    }.getOrDefault(false)
}
