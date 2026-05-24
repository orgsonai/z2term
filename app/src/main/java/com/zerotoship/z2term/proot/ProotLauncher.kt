package com.zerotoship.z2term.proot

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.distro.DistroBundle
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
     * proot が動的リンクする libtalloc を、SONAME 通り `libtalloc.so.2` で
     * 配置するディレクトリ。jniLibs は `lib<name>.so` 規約しか扱えないため、
     * 実行時にコピーして提供する。`LD_LIBRARY_PATH` でこのパスを通す。
     */
    private val prootLibsDir: File
        get() = File(context.filesDir, "proot-libs")

    /** jniLibs の libtalloc.so を SONAME 名 (libtalloc.so.2) で展開 */
    private fun ensureProotLibs() {
        val src = File(context.applicationInfo.nativeLibraryDir, "libtalloc.so")
        if (!src.exists()) {
            Log.w(TAG, "libtalloc.so not in nativeLibraryDir — proot will fail to link")
            return
        }
        prootLibsDir.mkdirs()
        val dst = File(prootLibsDir, "libtalloc.so.2")
        val needsCopy = !dst.exists() || dst.length() != src.length() ||
            dst.lastModified() < src.lastModified()
        if (needsCopy) {
            src.copyTo(dst, overwrite = true)
            dst.setReadable(true, false)
            dst.setExecutable(true, false)
            Log.i(TAG, "Provisioned libtalloc.so.2 at ${dst.absolutePath}")
        }
    }

    /**
     * 指定ディストロを PRoot で起動。
     *
     * @param distroId ディストロ識別子（"alpine", "ubuntu" 等）
     * @param command 実行コマンド（デフォルトは /bin/sh）
     * @param rows 端末行数
     * @param cols 端末列数
     * @param extraArgs command に続けて proot へ渡す追加引数。
     *        GUI ランチャを `command="/usr/local/bin/z2gui", extraArgs=["start","1280x720"]`
     *        のように引数付きで起動するために使う（command は単一トークンなので別枠で渡す）。
     */
    fun launch(
        distroId: String = "alpine",
        command: String = "/bin/sh",
        rows: Int = 24,
        cols: Int = 80,
        fallbackShell: String = "/bin/sh",
        extraArgs: List<String> = emptyList(),
        guiTerminal: GuiTerminal = GuiTerminal.XTERM
    ): PtyProcess {
        val rootfs = File(distrosDir, distroId)
        if (!rootfs.exists()) {
            throw IllegalStateException("Rootfs not found: ${rootfs.absolutePath}")
        }
        if (!prootBinary.exists()) {
            throw IllegalStateException("PRoot binary not found: ${prootBinary.absolutePath}")
        }

        // 指定シェルが rootfs に存在しなければ fallback → /bin/sh の順に解決。
        // (Ubuntu base に zsh が無い、等で起動不能になるのを防ぐ)
        val resolvedCommand = resolveShell(rootfs, command, fallbackShell)
        // 環境変数 SHELL は必ず「実体シェル」を指すようにする。command が z2gui の
        // ようにシェル以外だと、子プロセス (xterm 等) が $SHELL を起動して再帰・誤動作
        // する (M8-3 で Xvnc が即死した罠の真因)。command がシェルならそのまま使う。
        val shellForEnv = resolveLoginShell(rootfs, resolvedCommand, fallbackShell)

        // 共有ホーム作成 + libtalloc 配置
        sharedHomeDir.mkdirs()
        ensureProotLibs()
        // 再起動後もコマンド履歴を辿れるよう、shell rc に履歴設定を流し込む。
        ensureShellHistoryConfig(rootfs)
        // `sshd` コマンドで dropbear が立ち上がるよう wrapper を配置 (OpenSSH sshd は
        // proot で privsep 破綻 / sshd_config の UsePrivilegeSeparation で起動不可)。
        ensureSshdWrapper(rootfs)
        // `z2gui` で Linux GUI (Xvnc + WM) を起動できるよう launcher を配置。
        // GUI 内ターミナルは設定由来 (GuiSession が渡す)。端末起動では既定 xterm のまま。
        ensureGuiScript(rootfs, guiTerminal)
        // Android 外部ストレージを cd できるようマウント先を用意。
        File(rootfs, "sdcard").mkdirs()
        File(rootfs, "storage/app").mkdirs()

        // PRoot 引数の組み立て
        val args = mutableListOf<String>().apply {
            add("proot")                                  // argv[0]
            add("--kill-on-exit")
            add("-0")                                     // fake root
            // ハードリンクを symlink でエミュレート。Android のアプリ内ストレージは
            // link() を拒否する (EACCES) ため、これが無いと dpkg が status-old の
            // バックリンク作成に失敗し apt install が壊れる (Ubuntu/Kali)。
            add("--link2symlink")
            add("-r"); add(rootfs.absolutePath)           // rootfs
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("${sharedHomeDir.absolutePath}:/root")
            // Android 外部ストレージを /sdcard にマウント (cd /sdcard で OS 共有領域へ)。
            // 全ファイルアクセス権が無い場合は中身が読めないが、設定画面から許可できる。
            for ((src, dst) in externalStorageBinds()) {
                add("-b"); add("$src:$dst")
            }
            add("-w"); add("/root")                       // working dir
            // command (+ 追加引数: GUI ランチャの "start 1280x720" 等)
            add(resolvedCommand)
            extraArgs.forEach { add(it) }
        }

        // 環境変数
        val env = arrayOf(
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR=/tmp",
            "SHELL=$shellForEnv",
            // コマンド履歴を充実化。bash は PROMPT_COMMAND='history -a' で 1 コマンド毎に
            // 即 .bash_history へ追記 → proot が SIGKILL されても履歴が残る。
            // (zsh 用の INC_APPEND_HISTORY 等は ensureShellHistoryConfig が rc に書く)
            "HISTSIZE=10000",
            "HISTFILESIZE=20000",
            "SAVEHIST=10000",
            "HISTCONTROL=ignoredups",
            "PROMPT_COMMAND=history -a",
            // proot は libtalloc.so.2 にリンクされている (Termux RUNPATH 由来)。
            // ensureProotLibs() で展開した SONAME 通りのファイルパスを LD_LIBRARY_PATH に追加。
            "LD_LIBRARY_PATH=${prootLibsDir.absolutePath}",
            // PRoot 自身の動作用
            "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
            "PROOT_LOADER=${File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath}"
        )

        Log.i(TAG, "Launching PRoot: distro=$distroId, command=$resolvedCommand (requested=$command)")
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
     * 指定シェルが rootfs 内に実体として存在するか確認し、無ければ
     * fallbackShell → /bin/sh の順に解決する。
     *
     * `/bin` が `usr/bin` への symlink (Ubuntu の usrmerge) のケースを考慮し、
     * `<rootfs><path>` と `<rootfs>/usr<path>` の双方を見る。
     */
    private fun resolveShell(rootfs: File, requested: String, fallbackShell: String): String {
        for (candidate in listOf(requested, fallbackShell, "/bin/sh", "/bin/bash")) {
            if (candidate.isBlank()) continue
            if (shellExists(rootfs, candidate)) return candidate
        }
        // どれも見つからなければ要求値のまま (proot 側でエラーにさせる)
        return requested
    }

    /**
     * 環境変数 SHELL に入れる「実体シェル」を解決する。
     *
     * SHELL は xterm 等の子プロセスが「ユーザのログインシェル」として起動する値
     * なので、必ず本物のシェルを指していなければならない。command がシェル
     * (sh/bash/ash/zsh ...) ならそのまま使い、z2gui のようにシェルでなければ
     * fallbackShell → /bin/bash → /bin/ash → /bin/sh の順で rootfs に在るシェルへ
     * 振り替える。
     *
     * (M8-3 の罠の恒久対応: command="/usr/local/bin/z2gui" のとき SHELL=z2gui に
     *  なり、xterm が $SHELL=z2gui を起動 → z2gui start が再帰 → 動作中の Xvnc を
     *  kill して即死する、という問題を ProotLauncher 側で断つ。)
     */
    private fun resolveLoginShell(rootfs: File, resolvedCommand: String, fallbackShell: String): String {
        if (isShellPath(resolvedCommand)) return resolvedCommand
        for (candidate in listOf(fallbackShell, "/bin/bash", "/bin/ash", "/bin/sh")) {
            if (candidate.isNotBlank() && isShellPath(candidate) && shellExists(rootfs, candidate)) {
                return candidate
            }
        }
        return "/bin/sh"
    }

    /** パスの basename が既知のシェル名なら true。 */
    private fun isShellPath(path: String): Boolean =
        path.substringAfterLast('/') in KNOWN_SHELLS

    private fun shellExists(rootfs: File, absPath: String): Boolean {
        val rel = absPath.trimStart('/')
        if (File(rootfs, rel).exists()) return true
        // usrmerge: /bin/bash → /usr/bin/bash
        if (rel.startsWith("bin/") || rel.startsWith("sbin/")) {
            if (File(rootfs, "usr/$rel").exists()) return true
        }
        return false
    }

    /**
     * Android 外部ストレージを proot 内へバインドするための (src, dst) ペアを返す。
     *  - 端末の共有ストレージ全体 (/storage/emulated/0) → /sdcard
     *    (全ファイルアクセス権が無いと中身は EACCES だが、設定で許可すれば読める)
     *  - アプリ専用外部領域 (権限不要・常に読み書き可) → /storage/app
     */
    private fun externalStorageBinds(): List<Pair<String, String>> {
        val binds = mutableListOf<Pair<String, String>>()
        runCatching {
            val ext = android.os.Environment.getExternalStorageDirectory()
            if (ext != null && ext.exists()) binds += ext.absolutePath to "/sdcard"
        }
        runCatching {
            val appExt = context.getExternalFilesDir(null)
            if (appExt != null) { appExt.mkdirs(); binds += appExt.absolutePath to "/storage/app" }
        }
        return binds
    }

    /**
     * shell の rc に履歴設定を流し込む (再起動後も履歴を辿れるように)。
     * マーカーで二重書き込みを防ぐ idempotent な処理。launch 毎に呼ばれるので
     * 既存インストールの distro にも後付けで効く。
     *
     *  - bash: /etc/bash.bashrc に histappend + PROMPT_COMMAND='history -a'
     *  - zsh : /etc/zsh/zshrc に INC_APPEND_HISTORY 等 (+ HISTFILE)
     */
    private fun ensureShellHistoryConfig(rootfs: File) {
        val marker = "# >>> z2term history >>>"

        val bashBlock = """
            |$marker
            |if [ -n "${'$'}BASH_VERSION" ]; then
            |  export HISTSIZE=10000
            |  export HISTFILESIZE=20000
            |  export HISTCONTROL=ignoredups:erasedups
            |  shopt -s histappend 2>/dev/null
            |  case ":${'$'}PROMPT_COMMAND:" in
            |    *"history -a"*) ;;
            |    *) PROMPT_COMMAND="history -a${'$'}{PROMPT_COMMAND:+; ${'$'}PROMPT_COMMAND}" ;;
            |  esac
            |fi
            |# <<< z2term history <<<
        """.trimMargin()

        val zshBlock = """
            |$marker
            |export HISTFILE="${'$'}HOME/.zsh_history"
            |export HISTSIZE=10000
            |export SAVEHIST=10000
            |setopt INC_APPEND_HISTORY SHARE_HISTORY HIST_IGNORE_DUPS 2>/dev/null
            |# <<< z2term history <<<
        """.trimMargin()

        // bash: /etc/bash.bashrc (Arch/Debian/Ubuntu/Kali が interactive で source)
        appendOnceWithMarker(File(rootfs, "etc/bash.bashrc"), marker, bashBlock)
        // zsh: /etc/zsh/zshrc (Alpine 等)。zsh が無い distro でも無害。
        appendOnceWithMarker(File(rootfs, "etc/zsh/zshrc"), marker, zshBlock)
    }

    /**
     * `/usr/local/sbin/sshd` に dropbear 起動 wrapper を配置する。
     * PATH 上 /usr/local/sbin が /usr/sbin より優先されるので、端末で `sshd` と
     * 打つと OpenSSH ではなく dropbear (proot で動く) が立ち上がる。launch 毎に
     * 上書きするので内容は常に最新。
     */
    private fun ensureSshdWrapper(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/sbin").apply { mkdirs() }
            val f = File(dir, "sshd")
            f.writeText(dropbearBootstrapScript())
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "sshd wrapper 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2gui` に Linux GUI ランチャを配置する (PATH 上で使える)。
     * 端末や GUI セッションから `z2gui start [WxH]` で Xvnc + openbox + アプリが立ち上がる。
     * launch 毎に上書きするので内容は常に最新。
     */
    private fun ensureGuiScript(rootfs: File, guiTerminal: GuiTerminal) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val f = File(dir, "z2gui")
            f.writeText(
                z2guiScript(
                    terminalBinary = guiTerminal.binary,
                    terminalPackage = guiTerminal.packageName
                )
            )
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2gui script 配置失敗", it) }
    }

    /** marker を含まなければ block を追記。親 dir が無ければ作る。失敗は握り潰す。 */
    private fun appendOnceWithMarker(file: File, marker: String, block: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val existing = if (file.exists()) file.readText() else ""
            if (existing.contains(marker)) return
            val sep = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
            file.appendText("$sep\n$block\n")
        }.onFailure { Log.w(TAG, "history rc 書込失敗: ${file.absolutePath}", it) }
    }

    /**
     * フォールバック: PRoot を使わずに Android の /system/bin/sh を起動。
     *
     * Android mksh は /system/etc/mkshrc を強制的に読み込み、複雑な多行 PS1
     * (exit code 付き、cwd 全文、場合により改行入り) を設定する。
     * これがキーストロークごとのライン再描画と相まって、入力が「縦に積まれた
     * バラバラの行」に見える原因になる。
     *
     * 対策として、我々の制御下にある mkshrc.local を filesDir に書き出し、
     * `ENV=<path>` で mksh に読ませる。mkshrc.local は /system/etc/mkshrc の
     * 後で評価されるので、ここで設定する PS1 が最終値として採用される。
     */
    fun launchAndroidSh(rows: Int = 24, cols: Int = 80): PtyProcess {
        Log.i(TAG, "Launching Android /system/bin/sh (fallback mode)")
        val rcFile = ensureCleanShellRc()
        return PtyProcess.create(
            command = "/system/bin/sh",
            args = arrayOf("sh"),
            env = arrayOf(
                "HOME=${context.filesDir.absolutePath}",
                "TERM=xterm-256color",
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                "TMPDIR=${context.cacheDir.absolutePath}",
                // mksh が interactive 起動時に source する rc ファイル。
                // ここで PS1 をクリーンな 1 行プロンプトに固定する。
                "ENV=${rcFile.absolutePath}",
                "PS1=$ ",
                "PS2=> "
            ),
            cwd = context.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
    }

    /**
     * mksh 用 rc を filesDir に毎回書き出す (バージョン更新時の確実反映を兼ねて)。
     * 単一行 PS1 + シンプル PS2 + シェルが余計な color/format を吐かないよう
     * stty も整える。
     */
    private fun ensureCleanShellRc(): java.io.File {
        val rc = java.io.File(context.filesDir, ".z2term_mkshrc")
        rc.writeText(
            """
            # z2term auto-generated mkshrc — keep prompt minimal so the terminal
            # emulator can render typing on a single line without re-flow.
            export PS1='$ '
            export PS2='> '
            # disable mksh emacs-style line redraw escapes that confuse simple
            # emulators (best-effort; the actual flag varies between builds).
            set +o multiline 2>/dev/null
            """.trimIndent() + "\n"
        )
        return rc
    }

    /**
     * ディストロが展開済みか確認。
     *
     * `/bin/sh` は `/bin/busybox` への **絶対パス** シンボリックリンク
     * (rootfs 内では正解だが host から見ると壊れリンク) になっており、
     * `File("bin/sh").exists()` は常に false を返す → 毎回再展開してしまう。
     * 実体ファイルである busybox を見ることで正しく検出する。
     *
     * 加えて `.z2term-version` マーカーを比較し、APK 同梱版より古ければ
     * not-ready 扱いにして自動再展開を促す ([DistroBundle.ROOTFS_VERSION])。
     */
    fun isDistroReady(distroId: String): Boolean {
        val rootfs = File(distrosDir, distroId)
        if (!rootfs.exists()) return false
        val hasBinaries = File(rootfs, "bin/busybox").exists() ||
            File(rootfs, "bin/bash").exists() ||
            File(rootfs, "usr/bin/busybox").exists() ||
            File(rootfs, "usr/bin/bash").exists()
        if (!hasBinaries) return false

        // バージョンマーカーは postInstall の最後に書かれる = 「設定完了」の証。
        // 無い場合は展開途中 or postInstall 失敗の半端な状態なので再展開させる
        // (非同梱 distro はキャッシュ済みアーカイブから再展開され再 DL は不要)。
        val versionFile = File(rootfs, DistroBundle.VERSION_MARKER)
        if (!versionFile.exists()) {
            Log.i(TAG, "Distro $distroId has no version marker (incomplete install) -> not ready")
            return false
        }

        // ROOTFS_VERSION 比較は同梱 distro (Alpine) のみ。非同梱 (DL) まで version で
        // 弾くと Alpine の bump 毎に Ubuntu/Arch/Kali を再 DL させてしまうため。
        if (distroId == DistroBundle.BUNDLED_DISTRO_ID) {
            val installed = versionFile.readText().trim().toIntOrNull() ?: 0
            if (installed < DistroBundle.ROOTFS_VERSION) {
                Log.i(TAG, "Distro $distroId is outdated: installed=$installed vs bundled=${DistroBundle.ROOTFS_VERSION}")
                return false
            }
        }
        return true
    }

    /** proot バイナリが配置されているか確認 */
    fun isProotAvailable(): Boolean = prootBinary.exists()

    companion object {
        private const val TAG = "ProotLauncher"

        /** SHELL に採用してよい既知のシェル basename (これ以外は実体シェルへ振り替える)。 */
        private val KNOWN_SHELLS = setOf("sh", "bash", "ash", "dash", "zsh", "ksh", "mksh")
    }
}
