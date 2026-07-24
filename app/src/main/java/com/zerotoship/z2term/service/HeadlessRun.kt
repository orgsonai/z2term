package com.zerotoship.z2term.service

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

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
 */
object HeadlessRun {

    private const val TAG = "HeadlessRun"

    /** 実行ログが肥大しないよう、この閾値を超えていたら実行前に空にする。 */
    const val LOG_RESET_BYTES = 128L * 1024

    /**
     * [script] を headless で実行する。起動できたら true。
     *
     * @param logFile 出力の追記先。null なら出力は捨てる (それでも pty は読み切る)。
     * @param header  実行の頭に書く 1 行 (null なら書かない)。誰がいつ走らせたかを残すために使う。
     * @param name    ログ/スレッド名に使う短い識別子。
     */
    fun launch(
        context: Context,
        script: String,
        logFile: File?,
        name: String,
        header: String? = null,
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
                fallbackShell = spec.effectiveDefaultShell, loginShell = settings.loginShell,
                extraArgs = listOf("-lc", script),
            )
        }.getOrElse { e ->
            Log.e(TAG, "failed to launch $name", e)
            if (logFile != null) runCatching { logFile.appendText("(起動に失敗しました: ${e.message})\n") }
            return false
        }

        Log.i(TAG, "launched $name on $distroId")
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
                runCatching { process.close() }
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
