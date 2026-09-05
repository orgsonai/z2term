package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 画面を持たない実行 (headless run) の共通経路。
 *
 * 「いま選ばれている distro で `sh -lc '<script>'` を 1 回だけ走らせ、出力をログファイルへ流し切る」
 * という形は [WhenManager] (z2-when のルール実行) と
 * [com.zerotoship.z2term.widget.StatusWidgetProvider] (ウィジェットからのマクロ実行) で同じなので、
 * ここに 1 本化する。呼び元が違っても挙動 (ログの肥大対策・pty の詰まり回避) がズレない。
 *
 * 実行は常に [ProotLauncher.launch] (proot/z2root)。root chroot モードでも `launchChroot` は
 * 追加引数を取らないため、単発実行はエンジン経路に統一する (同じ distro で動くので挙動は変わらない)。
 *
 * **背景へ逃がしたプロセスは生き残る**: スクリプトが `sshd --lan` のようにデーモンを起こして
 * 自分は終了した場合、そのデーモンは実行が終わっても止めない。proot/z2root は
 * `--kill-on-exit` (`PTRACE_O_EXITKILL`) 付きで動くため、後始末でルートを kill すると配下ごと
 * カーネルに殺される — だから終了後は [PtyProcess.close] ではなく
 * [PtyProcess.waitFor] → [PtyProcess.detach] で畳む。明示的に止めるのは [stop] だけ。
 * ⚠ デーモンはアプリのプロセスが生きている間だけ生き残る (proot ルートの子であるため)。
 */
object HeadlessRun {

    private const val TAG = "HeadlessRun"

    /** 実行ログが肥大しないよう、この閾値を超えていたら実行前に空にする。 */
    const val LOG_RESET_BYTES = 128L * 1024

    /**
     * いま走っている headless 実行 (`name` → プロセス)。**アプリのプロセス内メモリだけ**に持つ。
     *
     * アプリが死ねば起動した子プロセスも道連れになるので、プロセスが作り直されたときに
     * 空から始まるのは正しい (「動いていないのに動いている表示」にならない)。
     * ウィジェットのボタンを「実行中はタップで停止」に変えるために使う。
     */
    private val running = ConcurrentHashMap<String, PtyProcess>()

    /** [name] の実行が今も生きているか。 */
    fun isRunning(name: String): Boolean = running[name]?.isAlive == true

    /**
     * [name] の実行を止める。止めるものが無ければ false。
     *
     * [PtyProcess.close] は SIGHUP → 最大 1 秒待って SIGKILL まで行うので**呼び出しはブロックする**。
     * ブロードキャスト受信スレッドから直接呼ばないこと (呼び元でスレッドへ逃がす)。
     */
    fun stop(name: String): Boolean {
        val p = running.remove(name) ?: return false
        Log.i(TAG, "stopping $name")
        runCatching { p.close() }
        return true
    }

    /**
     * [script] を headless で実行する。起動できたら true。
     *
     * @param logFile 出力の追記先。null なら出力は捨てる (それでも pty は読み切る)。
     * @param header  実行の頭に書く 1 行 (null なら書かない)。誰がいつ走らせたかを残すために使う。
     * @param name    ログ/スレッド名に使う短い識別子。[isRunning] / [stop] のキーも兼ねる。
     * @param onExit  実行が終わったときに呼ばれる (別スレッド)。表示を戻すために使う。
     */
    fun launch(
        context: Context,
        script: String,
        logFile: File?,
        name: String,
        header: String? = null,
        onExit: (() -> Unit)? = null,
    ): Boolean {
        val settings = runCatching { runBlocking { AppSettings(context).flow.first() } }.getOrNull() ?: return false
        val distroId = settings.distroId
        val rootfs = File(context.filesDir, "distros/$distroId")
        if (!rootfs.exists()) {
            Log.w(TAG, "rootfs missing for $distroId; cannot run $name")
            return false
        }
        if (logFile != null) {
            runCatching {
                logFile.parentFile?.mkdirs()
                if (logFile.length() > LOG_RESET_BYTES) logFile.writeText("")
                if (header != null) logFile.appendText(header)
            }
        }

        val spec = DistroSpec.byId(distroId) ?: DistroSpec.ALPINE
        val launcher = ProotLauncher(context)
        val process = runCatching {
            launcher.launch(
                distroId = distroId, command = "/bin/sh", rows = 24, cols = 80,
                fallbackShell = spec.defaultShell,
                extraArgs = listOf("-lc", script),
                // スクリプトが背景へ逃がしたデーモンを、実行終了と同時に道連れにしないため。
                // これが無いと `sshd --lan` は起動に成功した直後にエンジンごと消える。
                waitTracees = true,
            )
        }.getOrElse { e ->
            Log.e(TAG, "failed to launch $name", e)
            if (logFile != null) runCatching { logFile.appendText("(起動に失敗しました: ${e.message})\n") }
            return false
        }

        Log.i(TAG, "launched $name on $distroId")
        // 同名の前回分が残っていたら畳んでおく (キーを上書きして取り違えないように)。
        running.put(name, process)?.let { old -> runCatching { old.close() } }
        // 出力を流し切る (誰も読まないと pty バッファが埋まり、実行側の書込みが詰まる)。
        // プロセス終了で EOF → close。
        val threadName = "headless-$name"
        Thread {
            val buf = ByteArray(4096)
            try {
                val out = logFile?.let { FileOutputStream(it, true) }
                out.use { sink ->
                    while (true) {
                        val n = process.reader.read(buf)
                        if (n < 0) break
                        sink?.write(buf, 0, n)
                    }
                }
            } catch (_: Exception) {
                // 終了時 close の例外は正常終了扱い。
            } finally {
                // stop() が既に別のプロセスを登録し直している場合を壊さないよう、
                // 自分が登録した実体と一致するときだけ外す。
                running.remove(name, process)
                runCatching { onExit?.invoke() }
                // ⚠ ここで close() してはいけない。EOF は「前景スクリプトが終わった」だけで、
                // ルールが背景へ逃がしたデーモン (`sshd --lan` の dropbear 等) はまだ生きている。
                // proot/z2root は --kill-on-exit (PTRACE_O_EXITKILL) 付きなので、ルートを
                // kill すると配下ごとカーネルに殺される — ログに「listening」と出たのに
                // 直後には誰も listen していない、という形で表に出る。
                // ルートが自分から終わるのを待ってから、シグナルを送らずに畳む。
                // 背景に何も残らない普通のルールでは waitFor は即座に返る。
                runCatching { process.waitFor() }
                runCatching { process.detach() }
            }
        }.apply { isDaemon = true; this.name = threadName; start() }
        return true
    }

    /** 任意文字列を sh の単一引用符へ安全に埋め込む (`'` を `'\''` へ割る)。 */
    fun shSingleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** ログの頭に書く `--- 2026-07-24 14:32:10 <what> ---` 行。 */
    fun logHeader(what: String): String {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        return "--- $ts $what ---\n"
    }
}
