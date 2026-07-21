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

    /** サーバー 1 件の稼働状態 (status ファイルから読む)。[id] は [ServerEntry.id] に一致する。 */
    data class ServerStatus(val id: String, val state: String, val pid: String?, val command: String?)

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
        val scriptFile = File(rootfs, ServerSupervisorScript.SCRIPT_PATH.trimStart('/'))
        scriptFile.parentFile?.mkdirs()
        scriptFile.writeText(ServerSupervisorScript.generate(entries))
        // world ビットは filesDir 配下 (0700・アプリ UID 所有) なので他 UID には実効性が無い。
        // ゲスト側から確実に読める状態を保つため付けている (ProotLauncher と同じ判断)。
        @Suppress("SetWorldReadable")
        scriptFile.setExecutable(true, false)
        @Suppress("SetWorldReadable")
        scriptFile.setReadable(true, false)
        // 前回の status/want を掃除 (supervisor 冒頭でも消すが、起動失敗時の残骸対策)。
        File(rootfs, ServerSupervisorScript.STATUS_REL).listFiles()
            ?.filter { it.name.endsWith(".status") || it.name.endsWith(".want") }
            ?.forEach { it.delete() }

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
                        fallbackShell = spec.effectiveDefaultShell,
                        loginShell = settings.loginShell,
                    )
                }.getOrNull() ?: launcher.launch(
                    distroId = distroId,
                    command = ServerSupervisorScript.SCRIPT_PATH,
                    rows = 24, cols = 80,
                    fallbackShell = spec.effectiveDefaultShell,
                    loginShell = settings.loginShell,
                )
            } else {
                launcher.launch(
                    distroId = distroId,
                    command = ServerSupervisorScript.SCRIPT_PATH,
                    rows = 24, cols = 80,
                    fallbackShell = spec.effectiveDefaultShell,
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
     *
     * 注意: supervisor 起動後に追加された新規エントリには対応する run ループが無いため反映されない
     * (その場合は全体の再起動が必要)。既存エントリのトグルはこの経路で個別反映される。
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
        val distroId = activeDistroId ?: runBlocking { AppSettings(context).flow.first() }.distroId
        val dir = File(context.filesDir, "distros/$distroId/${ServerSupervisorScript.STATUS_REL}")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".status") } ?: return emptyList()
        return files.sortedBy { it.name }.map { f ->
            var state = "unknown"; var pid: String? = null; var cmd: String? = null
            runCatching {
                f.readLines().forEach { line ->
                    val eq = line.indexOf('=')
                    if (eq <= 0) return@forEach
                    val k = line.substring(0, eq); val v = line.substring(eq + 1)
                    when (k) {
                        "state" -> state = v
                        "pid" -> pid = v
                        "cmd" -> cmd = v
                    }
                }
            }
            ServerStatus(id = f.name.removeSuffix(".status"), state = state, pid = pid, command = cmd)
        }
    }
}
