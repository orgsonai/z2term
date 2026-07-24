package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.jcraft.jsch.Session
import com.zerotoship.z2term.channel.KnownHostsHolder
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.channel.SshSessionFactory
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
                    session.connect(CONNECT_TIMEOUT_MS)
                    worker.session = session
                    val detail = applyForwards(session, profile)
                    retries = 0
                    statuses[profile.id] = Status(profile.id, profile.name, true, detail, 0)
                    Log.i(TAG, "tunnel up: ${profile.name} ($detail)")

                    // 生きている間は待つだけ。転送は JSch 側のスレッドが捌く。
                    while (!worker.stop && session.isConnected) Thread.sleep(POLL_MS)

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

    /** 転送を張って、張れたものの説明を返す。 */
    private fun applyForwards(session: Session, profile: SshProfile): String {
        val ok = ArrayList<String>()
        profile.forwards.forEach { f ->
            runCatching {
                if (f.reverse) {
                    session.setPortForwardingR(f.bindAddress, f.remotePort, f.remoteHost, f.localPort)
                } else {
                    session.setPortForwardingL(f.bindAddress, f.localPort, f.remoteHost, f.remotePort)
                }
                ok += f.describe()
            }.onFailure { e ->
                Log.w(TAG, "forward failed (${f.describe()}): ${e.message}")
                ok += "✗ ${f.describe()}"
            }
        }
        return ok.joinToString(" / ")
    }

    /**
     * このホストの鍵が known_hosts にあるか。
     *
     * 常駐は「黙って新しい鍵を信用する」をやってはいけない場面なので、ここが false なら張らない。
     */
    private fun isKnownHost(context: Context, profile: SshProfile): Boolean = runCatching {
        val repo = KnownHostsHolder.repository(context)
        // JSch は既定ポート以外を `[host]:port` の形で記録する (SSH タブでの承認時と同じ形)。
        val key = if (profile.port == 22) profile.host else "[${profile.host}]:${profile.port}"
        repo.getHostKey(key, null).isNotEmpty()
    }.getOrDefault(false)
}
