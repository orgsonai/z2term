package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.ServerSupervisorScript
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.ServerEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 常駐サーバーの起動/停止を司るプロセス管理。UI・対話セッションから独立して 1 本の
 * **supervisor エンジンプロセス**を保持する ([ServerSupervisorScript] 参照)。
 *
 * proot/z2root では全プロセスがエンジンプロセスの子になるため、この 1 本を生かし続ければ
 * 全サーバーが常駐し、[stop] で kill すればまとめて止まる。前面維持 (Android にプロセスを
 * 殺されない) は [ServerDaemonService] のフォアグラウンド化が担う。
 */
object ServerDaemonManager {

    private const val TAG = "ServerDaemon"

    /**
     * サーバー 1 件の稼働状態 (status ファイルから読む)。[id] は [ServerEntry.id] に一致する。
     *
     * @param restarts supervisor がそのサーバーを再起動した回数。増え続けているなら
     *                 「起動しては落ちる」を繰り返している (クラッシュループ) と分かる。
     * @param lastExit 直近の終了コード。0 以外なら異常終了。
     */
    data class ServerStatus(
        val id: String,
        val state: String,
        val pid: String?,
        val command: String?,
        val restarts: Int = 0,
        val lastExit: String? = null
    )

    private var pty: PtyProcess? = null
    private var drainThread: Thread? = null
    @Volatile private var activeDistroId: String? = null

    val isRunning: Boolean
        get() = synchronized(this) { pty?.isAlive == true }

    /**
     * enabled なサーバーがあれば supervisor をエンジン上で起動する。既に起動済みなら一旦停止して
     * 起動し直す (設定変更の反映も兼ねる)。起動したら true、対象が無ければ false。
     */
    fun start(context: Context): Boolean = synchronized(this) {
        stopLocked()
        val settings = runBlocking { AppSettings(context).flow.first() }
        // enabled/disabled を問わず全件を焼き込む (個別トグルで稼働中に ON へ切替えられるように)。
        // ただし 1 件も enabled が無ければ起動しない (通知だけ出る空常駐を避ける)。
        val entries = ServerEntry.decode(settings.serverEntries).filter { it.command.isNotBlank() }
        if (entries.none { it.enabled }) {
            Log.i(TAG, "No enabled servers; not starting")
            return false
        }
        val distroId = settings.distroId
        val rootfs = File(context.filesDir, "distros/$distroId")
        if (!rootfs.exists()) {
            Log.w(TAG, "Rootfs missing for $distroId; cannot start servers")
            return false
        }

        // supervisor スクリプトを rootfs に配置 (実行権付き)。
        // スクリプトはエントリを焼き込まない固定文字列で、サーバーの定義はジョブファイルで渡す。
        val scriptFile = File(rootfs, ServerSupervisorScript.SCRIPT_PATH.trimStart('/'))
        scriptFile.parentFile?.mkdirs()
        scriptFile.writeText(ServerSupervisorScript.generate())
        // world ビットは filesDir 配下 (0700・アプリ UID 所有) なので他 UID には実効性が無い。
        // ゲスト側から確実に読める状態を保つため付けている (ProotLauncher と同じ判断)。
        @Suppress("SetWorldReadable")
        scriptFile.setExecutable(true, false)
        @Suppress("SetWorldReadable")
        scriptFile.setReadable(true, false)
        // 前回の残骸を掃除 (supervisor 冒頭でも消すが、起動失敗時の取りこぼし対策)。
        // ログと終了履歴は前回の分を残す (落ちた理由を後から見るためのものなので消さない)。
        File(rootfs, ServerSupervisorScript.STATUS_REL).listFiles()
            ?.filter { f -> STALE_SUFFIXES.any { f.name.endsWith(it) } }
            ?.forEach { it.delete() }
        // サーバーの定義をジョブファイルとして書き出す。以後の追加・変更・削除も同じ経路で
        // 反映され、supervisor は動いたまま拾う (無停止リロード)。
        writeJobs(rootfs, entries)

        val spec = DistroSpec.byId(distroId) ?: DistroSpec.ALPINE
        val launcher = ProotLauncher(context)
        val useChroot = settings.executionEngine == AppSettings.ENGINE_CHROOT && settings.rootChrootUnlocked
        val process = runCatching {
            if (useChroot) {
                runCatching {
                    launcher.launchChroot(
                        distroId = distroId,
                        command = ServerSupervisorScript.SCRIPT_PATH,
                        rows = 24, cols = 80,
                        fallbackShell = spec.defaultShell,
                        loginShell = settings.loginShell,
                    )
                }.getOrNull() ?: launcher.launch(
                    distroId = distroId,
                    command = ServerSupervisorScript.SCRIPT_PATH,
                    rows = 24, cols = 80,
                    fallbackShell = spec.defaultShell,
                    loginShell = settings.loginShell,
                )
            } else {
                launcher.launch(
                    distroId = distroId,
                    command = ServerSupervisorScript.SCRIPT_PATH,
                    rows = 24, cols = 80,
                    fallbackShell = spec.defaultShell,
                    loginShell = settings.loginShell,
                )
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to launch server supervisor", e)
            return false
        }

        pty = process
        activeDistroId = distroId
        // PTY 出力を捨て続ける (誰も読まないと pty バッファが埋まりサーバーの stdout 書込みが詰まる)。
        drainThread = Thread {
            val buf = ByteArray(4096)
            try {
                while (true) {
                    val n = process.reader.read(buf)
                    if (n < 0) break
                }
            } catch (_: Exception) {
                // プロセス終了時の close で例外＝正常終了扱い。
            }
        }.apply { isDaemon = true; name = "server-supervisor-drain"; start() }

        Log.i(TAG, "Server supervisor started (distro=$distroId, servers=${entries.size}, pid=${process.shellPid})")
        return true
    }

    /** 全サーバーを停止 (supervisor エンジンを kill)。 */
    fun stop() = synchronized(this) { stopLocked() }

    /**
     * 稼働中の supervisor に対し、id で指定した 1 サーバーだけを起動/停止する。`<id>.want` フラグを
     * 書き換えるだけで、supervisor 本体や他サーバーは止めない (~1 秒で反映)。supervisor 未起動なら
     * false (この場合は設定 [ServerEntry.enabled] の永続化のみで、次回起動時に反映される)。
     */
    fun setWant(context: Context, id: String, enabled: Boolean): Boolean = synchronized(this) {
        if (pty?.isAlive != true) return false
        val distroId = activeDistroId ?: return false
        val dir = File(context.filesDir, "distros/$distroId/${ServerSupervisorScript.STATUS_REL}")
        return runCatching {
            dir.mkdirs()
            File(dir, "$id.want").writeText(if (enabled) "1" else "0")
            Log.i(TAG, "setWant id=$id enabled=$enabled")
            true
        }.getOrElse {
            Log.w(TAG, "setWant failed for id=$id", it)
            false
        }
    }

    /**
     * サーバーの追加・編集・削除を**稼働中の supervisor に反映**する (A3・無停止リロード)。
     *
     * ジョブファイルを書き換えるだけなので、supervisor も他のサーバーも止まらない。
     *  - 新規 → `<id>.job` が現れ、監視ループ (2 秒周期) が run ループを起こす
     *  - 編集 → `<id>.job` の中身が変わり、そのサーバーだけ再起動する
     *  - 削除 → `<id>.job` が消え、そのサーバーの run ループが片付けて抜ける
     *
     * supervisor が動いていないときは何もしない (次回 [start] でまとめて書き出される)。
     */
    fun syncEntries(context: Context, entries: List<ServerEntry>): Boolean = synchronized(this) {
        if (pty?.isAlive != true) return false
        val distroId = activeDistroId ?: return false
        val rootfs = File(context.filesDir, "distros/$distroId")
        return runCatching {
            writeJobs(rootfs, entries.filter { it.command.isNotBlank() })
            true
        }.getOrElse {
            Log.w(TAG, "syncEntries failed", it)
            false
        }
    }

    /**
     * [entries] をジョブファイル (`<id>.job` / `<id>.want`) として書き出し、**一覧に無い id の
     * ジョブは消す**。消えたサーバーの status/log が残り続けないよう、後片付けも行う。
     *
     * `.job` は中身が同じなら書き直さない — 書き直すと supervisor が「コマンドが変わった」と
     * 見なして、変更していないサーバーまで再起動してしまうため。
     */
    private fun writeJobs(rootfs: File, entries: List<ServerEntry>) {
        val dir = File(rootfs, ServerSupervisorScript.STATUS_REL)
        dir.mkdirs()
        val keep = entries.map { it.id }.toSet()
        for (e in entries) {
            val jobFile = File(dir, "${e.id}.job")
            val current = runCatching { if (jobFile.exists()) jobFile.readText() else null }.getOrNull()
            if (current != e.command) jobFile.writeText(e.command)
            File(dir, "${e.id}.want").writeText(if (e.enabled) "1" else "0")
        }
        // 一覧から消えたサーバーの残骸を片付ける。.job を消すと run ループ側も自分で抜ける。
        dir.listFiles()?.forEach { f ->
            val id = ORPHAN_SUFFIXES.firstOrNull { f.name.endsWith(it) }
                ?.let { f.name.removeSuffix(it) } ?: return@forEach
            if (id !in keep) f.delete()
        }
    }

    private fun stopLocked() {
        pty?.let { p ->
            runCatching { p.close() }
            Log.i(TAG, "Server supervisor stopped")
        }
        pty = null
        drainThread = null
        activeDistroId = null
    }

    /** 各サーバーの稼働状態を status ファイルから読む (UI 表示用)。未起動なら空。 */
    fun readStatus(context: Context): List<ServerStatus> {
        val dir = statusDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".status") } ?: return emptyList()
        return files.sortedBy { it.name }.map { f ->
            var state = "unknown"; var pid: String? = null; var cmd: String? = null
            var restarts = 0; var lastExit: String? = null
            runCatching {
                f.readLines().forEach { line ->
                    val eq = line.indexOf('=')
                    if (eq <= 0) return@forEach
                    val k = line.substring(0, eq); val v = line.substring(eq + 1)
                    when (k) {
                        "state" -> state = v
                        "pid" -> pid = v
                        "cmd" -> cmd = v
                        "restarts" -> restarts = v.trim().toIntOrNull() ?: 0
                        "last_exit" -> lastExit = v
                    }
                }
            }
            ServerStatus(
                id = f.name.removeSuffix(".status"),
                state = state, pid = pid, command = cmd,
                restarts = restarts, lastExit = lastExit
            )
        }
    }

    /**
     * サーバー 1 件のログ (標準出力・標準エラー) の**末尾**を読む (A3)。
     *
     * 常駐サーバーが黙って落ちていても気付けるように、supervisor は各サーバーの出力を
     * `<id>.log` へ落としている。ここでは表示用に末尾 [maxBytes] だけを読む
     * (先頭が切れた場合、途中から始まる文字化けを避けるため最初の改行までは捨てる)。
     */
    fun readLog(context: Context, id: String, maxBytes: Int = LOG_TAIL_BYTES): String {
        val f = File(statusDir(context), "$id.log")
        if (!f.isFile) return ""
        return runCatching {
            val len = f.length()
            if (len <= maxBytes) return@runCatching f.readText()
            f.inputStream().use { input ->
                input.skip(len - maxBytes)
                val text = input.readBytes().toString(Charsets.UTF_8)
                val nl = text.indexOf('\n')
                if (nl >= 0) text.substring(nl + 1) else text
            }
        }.getOrElse { "" }
    }

    /** サーバー 1 件のログを空にする (UI の「ログを消す」)。 */
    fun clearLog(context: Context, id: String) {
        runCatching { File(statusDir(context), "$id.log").writeText("") }
    }

    /** サーバー 1 件のログの現在のサイズ (バイト)。無ければ 0。 */
    fun logSize(context: Context, id: String): Long =
        runCatching { File(statusDir(context), "$id.log").length() }.getOrElse { 0L }

    private fun statusDir(context: Context): File {
        val distroId = activeDistroId ?: runBlocking { AppSettings(context).flow.first() }.distroId
        return File(context.filesDir, "distros/$distroId/${ServerSupervisorScript.STATUS_REL}")
    }

    /** supervisor 起動時に消す残骸 (実行状態を表すもの)。ログと終了履歴は残す。 */
    private val STALE_SUFFIXES = listOf(".status", ".want", ".claimed", ".job")

    /** サーバーが一覧から消えたときに片付ける対象。 */
    private val ORPHAN_SUFFIXES = listOf(".job", ".want", ".status", ".claimed", ".log", ".exits")

    /** UI に出すログの末尾サイズ。 */
    private const val LOG_TAIL_BYTES = 64 * 1024
}
