package com.zerotoship.z2term.proot

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.pty.PtyProcess
import java.io.File

/**
 * PRoot プロセスを起動するためのマネージャ。
 *
 * M1 段階での想定:
 * - Alpine rootfs を `${filesDir}/distros/alpine` に展開済み
 * - proot バイナリを `${nativeLibraryDir}/libproot.so` に同梱
 *   (jniLibs に .so 拡張子で配置することで APK 展開時に実行可能領域に配置される)
 * - PTY 経由で proot → ash (Alpine) → bash (インストール済みなら) を起動
 *
 * Android のセキュリティ制約により、APK 内の実行ファイルは
 * `libxxx.so` の命名規約で jniLibs に置く必要がある。
 * 通常のファイルとしての配布は Android 10+ で動作しなくなった。
 */
class ProotLauncher(private val context: Context) {

    /** distros の格納場所 */
    private val distrosDir: File
        get() = File(context.filesDir, "distros")

    /** 共有ホームの格納場所 */
    private val sharedHomeDir: File
        get() = File(context.filesDir, "shared_home")

    /** proot バイナリのパス */
    private val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    /**
     * 指定ディストロを PRoot で起動。
     *
     * @param distroId ディストロ識別子（"alpine", "ubuntu" 等）
     * @param command 実行コマンド（デフォルトは /bin/sh）
     * @param rows 端末行数
     * @param cols 端末列数
     */
    fun launch(
        distroId: String = "alpine",
        command: String = "/bin/sh",
        rows: Int = 24,
        cols: Int = 80
    ): PtyProcess {
        val rootfs = File(distrosDir, distroId)
        if (!rootfs.exists()) {
            throw IllegalStateException("Rootfs not found: ${rootfs.absolutePath}")
        }
        if (!prootBinary.exists()) {
            throw IllegalStateException("PRoot binary not found: ${prootBinary.absolutePath}")
        }

        // 共有ホーム作成
        sharedHomeDir.mkdirs()

        // PRoot 引数の組み立て
        val args = mutableListOf<String>().apply {
            add("proot")                                  // argv[0]
            add("--kill-on-exit")
            add("-0")                                     // fake root
            add("-r"); add(rootfs.absolutePath)           // rootfs
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("${sharedHomeDir.absolutePath}:/root")
            add("-w"); add("/root")                       // working dir
            // command
            add(command)
        }

        // 環境変数
        val env = arrayOf(
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR=/tmp",
            "SHELL=$command",
            // PRoot 自身の動作用
            "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
            "PROOT_LOADER=${File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath}"
        )

        Log.i(TAG, "Launching PRoot: distro=$distroId, command=$command")
        Log.d(TAG, "Args: ${args.joinToString(" ")}")

        return PtyProcess.create(
            command = prootBinary.absolutePath,
            args = args.toTypedArray(),
            env = env,
            cwd = context.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
    }

    /**
     * フォールバック: PRoot を使わずに Android の /system/bin/sh を起動。
     *
     * これは M1 開発時の動作確認用。
     * PRoot 整備前でもターミナルが動くことを確認できる。
     */
    fun launchAndroidSh(rows: Int = 24, cols: Int = 80): PtyProcess {
        Log.i(TAG, "Launching Android /system/bin/sh (fallback mode)")
        return PtyProcess.create(
            command = "/system/bin/sh",
            args = arrayOf("sh"),
            env = arrayOf(
                "HOME=${context.filesDir.absolutePath}",
                "TERM=xterm-256color",
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                "TMPDIR=${context.cacheDir.absolutePath}",
                "PS1=z2term:android $ "
            ),
            cwd = context.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
    }

    /** ディストロが展開済みか確認 */
    fun isDistroReady(distroId: String): Boolean {
        val rootfs = File(distrosDir, distroId)
        return rootfs.exists() && File(rootfs, "bin/sh").exists()
    }

    /** proot バイナリが配置されているか確認 */
    fun isProotAvailable(): Boolean = prootBinary.exists()

    companion object {
        private const val TAG = "ProotLauncher"
    }
}
