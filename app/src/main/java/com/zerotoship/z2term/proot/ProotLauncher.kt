package com.zerotoship.z2term.proot

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.distro.DistroBundle
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.storage.ExternalStorageDetector
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** [ProotLauncher.probeRootChroot] のセルフテスト結果。 */
sealed class RootProbe {
    /** root + chroot exec 成功 (chroot エンジン利用可)。 */
    object Ok : RootProbe()
    /** su が無い / 許可されない (= 未 root)。 */
    object NoRoot : RootProbe()
    /** root はあるが chroot exec が拒否された (SELinux 等)。detail は端末からの出力。 */
    data class ChrootBlocked(val detail: String) : RootProbe()
}

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
// setReadable/setExecutable の第2引数 false (= world ビット) は、対象がすべて
// `context.filesDir` 配下 (アプリ専用データ領域) のファイルであるため実効性を持たない:
// この領域は 0700・アプリ UID 所有で、他アプリ/他 UID はそもそもディレクトリを辿れない。
// 一方で owner-only へ変えると proot/z2root のゲスト側でファイルが読めなくなる退行リスクがある。
// よって「露出は無いが world ビットは必要」という判断で、このクラスに限り検査を抑制する
// (アプリ外 (/sdcard 等) への world-readable は他の場所で引き続き検出される)。
@Suppress("SetWorldReadable")
class ProotLauncher(private val context: Context) {

    /** distros の格納場所 */
    private val distrosDir: File
        get() = File(context.filesDir, "distros")

    /** 共有ホームの格納場所 */
    private val sharedHomeDir: File
        get() = File(context.filesDir, "shared_home")

    /** ディストリ別 HOME オーバーレイ (arch 依存物の隔離) の格納場所。 */
    private val homeOverlayDir: File
        get() = File(context.filesDir, "home_overlay")

    /**
     * HOME (`/root`) は全ディストリ共有 ([sharedHomeDir]) のままにしつつ、**arch 依存物が入る
     * 一部サブディレクトリだけをディストリ別に隔離**する対象一覧。musl(Alpine)↔glibc(Arch/Ubuntu/
     * Kali) で混在すると壊れる (例: Alpine で入れた claude/node の native が glibc 側で動かない)
     * ため、これらは `${homeOverlayDir}/<distroId>/<sub>` を `/root/<sub>` に bind して分離する。
     * 書類や git リポジトリなど通常ファイルは `/root` 直下のまま共有される。
     */
    private val isolatedHomeSubdirs: List<String> = listOf(
        ".local", ".cache", ".npm", ".npm-global", ".nvm", ".cargo", ".rustup", ".config",
        // claude の native 本体 (`~/.claude/downloads/claude`, 数百 MB の ELF) は arch 依存。
        // `.claude` 直下の認証 (`.credentials.json`)・設定・projects は共有したいので、
        // downloads サブのみ隔離する。これを共有すると musl(Alpine)↔glibc(Arch) で本体が
        // 上書き合い「Not a valid dynamic program」で双方起動不可になる (項目4 の真因)。
        ".claude/downloads"
    )

    /**
     * 指定ディストリの HOME 隔離 bind を `(ホスト実体, "/root/<sub>")` で返す。
     * ホスト実体 (`${homeOverlayDir}/<distroId>/<sub>`) と、共有 HOME 側のマウントポイント
     * (`${sharedHomeDir}/<sub>`) を both 作成しておく (proot/chroot どちらも実在が要るため)。
     */
    private fun isolatedHomeBinds(distroId: String): List<Pair<File, String>> {
        val base = File(homeOverlayDir, distroId)
        return isolatedHomeSubdirs.map { sub ->
            val host = File(base, sub).apply { mkdirs() }
            File(sharedHomeDir, sub).mkdirs()
            host to "/root/$sub"
        }
    }

    /** proot バイナリのパス */
    private val prootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    /**
     * z2root バイナリのパス (自前 ptrace エンジン)。proot 互換 argv subset を受けるので
     * [launch] の引数・env はそのまま流用できる (PROOT_* / libtalloc は z2root が無視する)。
     * APK に未同梱 (build-z2root.sh でビルドした .so を jniLibs に置いていない) の場合は
     * 存在チェックで proot へフォールバックする。
     */
    private val z2rootBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libz2root.so")

    /**
     * accept→accept4 橋渡しシム (LD_PRELOAD 用)。Android の untrusted_app seccomp は
     * accept(202) を禁止する (bionic は accept4 を使う) ため、musl 製サーバ (Alpine の Xvnc /
     * dropbear 等) の accept が SIGSYS で弾かれて GUI(VNC)/SSH が接続を受けられない。
     * このシムを z2root 起動時に LD_PRELOAD し、accept を accept4(...,0) に置き換えて回避する。
     * libc 非依存 (生 svc) なので musl/glibc どちらにも効く。未同梱でも LD_PRELOAD 失敗は
     * ld.so が警告して無視するだけ (非致命)。
     */
    private val z2acceptShim: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libz2accept.so")

    /** z2root エンジンで accept シムを LD_PRELOAD する guest パス。 */
    private val z2acceptShimGuestPath = "/usr/local/lib/libz2accept.so"

    /**
     * proot が動的リンクする libtalloc を、SONAME 通り `libtalloc.so.2` で
     * 配置するディレクトリ。jniLibs は `lib<name>.so` 規約しか扱えないため、
     * 実行時にコピーして提供する。`LD_LIBRARY_PATH` でこのパスを通す。
     */
    private val prootLibsDir: File
        get() = File(context.filesDir, "proot-libs")

    /** jniLibs の libtalloc.so を SONAME 名 (libtalloc.so.2) で展開 */
    private fun ensureProotLibs() {
        // libtalloc は SONAME が libtalloc.so.2 だが jniLibs に置けるのは lib*.so 形式のみ
        // なので、jniLibs の libtalloc.so → proot-libs/libtalloc.so.2 にリネーム展開する。
        provisionProotLib(srcName = "libtalloc.so", sonameFile = "libtalloc.so.2")
        // libandroid-shmem は SONAME が無印 (libandroid-shmem.so) なのでリネーム不要。
        // 新しい Termux proot がこれにリンクするようになったため (不在だと
        // `library "libandroid-shmem.so" not found` で proot が即落ちする) 同様に展開する。
        provisionProotLib(srcName = "libandroid-shmem.so", sonameFile = "libandroid-shmem.so")
    }

    /** nativeLibraryDir の [srcName] を proot-libs/[sonameFile] へ (必要時のみ) コピーする。 */
    private fun provisionProotLib(srcName: String, sonameFile: String) {
        val src = File(context.applicationInfo.nativeLibraryDir, srcName)
        if (!src.exists()) {
            Log.w(TAG, "$srcName not in nativeLibraryDir — proot may fail to link")
            return
        }
        prootLibsDir.mkdirs()
        val dst = File(prootLibsDir, sonameFile)
        val needsCopy = !dst.exists() || dst.length() != src.length() ||
            dst.lastModified() < src.lastModified()
        if (needsCopy) {
            src.copyTo(dst, overwrite = true)
            dst.setReadable(true, false)
            dst.setExecutable(true, false)
            Log.i(TAG, "Provisioned $sonameFile at ${dst.absolutePath}")
        }
    }

    /**
     * accept→accept4 シム (libz2accept.so) を rootfs 内 [z2acceptShimGuestPath] へ配置する。
     * z2root エンジンのときだけ呼ぶ。未同梱なら何もしない (LD_PRELOAD は ld.so が無視する)。
     */
    private fun ensureAcceptShim(rootfs: File) {
        val src = z2acceptShim
        if (!src.exists()) {
            Log.w(TAG, "libz2accept.so not in nativeLibraryDir — accept()→accept4() shim unavailable")
            return
        }
        val dst = File(rootfs, z2acceptShimGuestPath.trimStart('/'))
        dst.parentFile?.mkdirs()
        val needsCopy = !dst.exists() || dst.length() != src.length() ||
            dst.lastModified() < src.lastModified()
        if (needsCopy) {
            src.copyTo(dst, overwrite = true)
            dst.setReadable(true, false)
            dst.setExecutable(true, false)
            Log.i(TAG, "Provisioned accept shim at ${dst.absolutePath}")
        }
    }

    /** z2root エンジン専用 env: accept→accept4 シムを LD_PRELOAD する。proot/chroot では空。 */
    private fun z2rootEnv(useZ2root: Boolean): List<String> {
        if (!useZ2root) return emptyList()
        val out = mutableListOf<String>()
        if (z2acceptShim.exists()) out.add("LD_PRELOAD=$z2acceptShimGuestPath")
        // [DEBUG] 設定「トレースログ」(エンジン選択と同じ 7タップ裏機能内) が ON のときだけ
        // z2root の全 syscall を shared_home/z2root_trace.log へ出す。既定 OFF。ログは膨大で
        // 容量を圧迫するため一般ユーザーは使わない。旧来の .z2root_trace_on sentinel でも有効化できる。
        if (isTraceLogEnabled() || File(sharedHomeDir, ".z2root_trace_on").exists())
            out.add("Z2ROOT_TRACE=${File(sharedHomeDir, "z2root_trace.log").absolutePath}")
        return out
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
        /**
         * 設定「ログインシェル」の値 (空なら未指定 = 従来どおり distro 既定)。
         *
         * 端末タブの `command` だけでなく、**SSH ログイン**と **GUI 内ターミナル**にも同じシェルを
         * 使わせるために使う ([ensureRootLoginShell] で rootfs の `/etc/passwd` を更新 +
         * `Z2_LOGIN_SHELL` / `SHELL` を環境変数に流す)。rootfs に無いシェルなら無視される。
         */
        loginShell: String = "",
        extraArgs: List<String> = emptyList(),
        guiTerminal: GuiTerminal = GuiTerminal.XTERM,
        /**
         * 非 null なら GUI 用の `Z2_DISPLAY` / `Z2_RFBPORT` を環境変数に追加する。
         * z2gui はこれを読んで `:N` / `5900+N` で Xvnc を起動するので、GUI タブごとに
         * 別ディスプレイ・別ポートで並走できる (null のときは z2gui の既定 :1/5901)。
         */
        display: Int? = null,
        /**
         * true なら `DISPLAY=:N` も環境変数に追加する (P3 = CUI⇄GUI 連動)。端末用に使う。
         * これで端末から起動した X クライアント (xclock, `z2run python gui.py` 等) が即座に
         * その :N の Xvnc へ繋がる。GUI 起動 (z2gui) では false にしておかないと、`z2gui stop` の
         * environ 走査が自身を巻き込む恐れがある (詳細は下の displayEnv コメント / GuiScript)。
         */
        exportDisplay: Boolean = false,
        /**
         * 非 null なら GUI 音声ブリッジ用に `Z2_AUDIO=1` / `Z2_AUDIO_PORT=<port>` を環境変数へ追加する。
         * z2gui はこれを見て proot 内に PulseAudio (null-sink + simple-protocol-tcp) を起動し、その
         * monitor を 127.0.0.1:<port> へ流す。null (既定) のときは一切起動しない (依存ゼロ)。
         * 設定「GUI 音声」が ON のときだけ [com.zerotoship.z2term.gui.GuiSession] が渡す。
         */
        guiAudioPort: Int? = null,
        /**
         * true なら z2root に `--wait-tracees` を渡し、**メインのコマンドが終わっても背景に
         * 残ったプロセスが居る限りエンジンを終了させない**。
         *
         * 既定 (false) では、メインの `sh` が exit した瞬間にエンジンが終了し、
         * `PTRACE_O_EXITKILL` (`--kill-on-exit`) でカーネルが残りのプロセスを kill する。
         * そのため `sshd --lan` のようにデーモンを起こすコマンドは、起動に成功した直後に
         * 道連れで消える。単発実行 ([com.zerotoship.z2term.service.HeadlessRun]) だけ true にする。
         *
         * ⚠ proot エンジンには渡さない (未知オプションで起動に失敗するため)。proot を選んで
         * いる場合はこの問題は残る — z2root が本線という前提。
         */
        waitTracees: Boolean = false
    ): PtyProcess {
        val rootfs = File(distrosDir, distroId)
        if (!rootfs.exists()) {
            throw IllegalStateException("Rootfs not found: ${rootfs.absolutePath}")
        }
        // エンジン選択: 裏設定で z2root が選ばれ、かつ libz2root.so が同梱されていれば z2root を使う。
        // foss は proot prebuilt を同梱しない (F-Droid 適合) ため、z2root が同梱されていれば
        // 設定に関わらず z2root を既定エンジンとする (z2root は full/foss 共通で常に同梱)。
        // full のみ: z2root 未同梱 (build-z2root.sh 未実行) なら proot へフォールバックする。
        // foss には倒す先の proot が無いため、z2root 欠落時は下の exists チェックで明確に停止する。
        val useZ2root = (BuildConfig.IS_FOSS || isZ2rootEngineSelected()) && z2rootBinary.exists()
        val engineBinary = if (useZ2root) z2rootBinary else prootBinary
        if (!engineBinary.exists()) {
            throw IllegalStateException("Engine binary not found: ${engineBinary.absolutePath}")
        }

        // 指定シェルが rootfs に存在しなければ fallback → /bin/sh の順に解決。
        // (Ubuntu base に zsh が無い、等で起動不能になるのを防ぐ)
        val resolvedCommand = resolveShell(rootfs, command, fallbackShell)
        // 設定「ログインシェル」を、この rootfs で実際に起動できる形へ解決する
        // (未導入なら distro 既定 → /bin/sh へフォールバック)。空なら未指定。
        val userLoginShell =
            if (loginShell.isBlank()) "" else resolveShell(rootfs, loginShell, fallbackShell)
        // 環境変数 SHELL は必ず「実体シェル」を指すようにする。command が z2gui の
        // ようにシェル以外だと、子プロセス (xterm 等) が $SHELL を起動して再帰・誤動作
        // する (M8-3 で Xvnc が即死した罠の真因)。command がシェルならそのまま使う。
        // command がシェルでないときは設定のログインシェルを最優先で採用する。
        val shellForEnv =
            resolveLoginShell(rootfs, resolvedCommand, userLoginShell.ifBlank { fallbackShell })

        // 共有ホーム作成 + libtalloc 配置 (talloc/loader は proot 専用。z2root では不要なので省く)
        sharedHomeDir.mkdirs()
        if (!useZ2root) ensureProotLibs()
        // z2root のときは accept→accept4 シムを rootfs に配置し、後で LD_PRELOAD する。
        if (useZ2root) ensureAcceptShim(rootfs)
        // 設定のログインシェルを /etc/passwd(root) にも書き、SSH ログインにも効かせる。
        ensureRootLoginShell(rootfs, userLoginShell)
        // 再起動後もコマンド履歴を辿れるよう、shell rc に履歴設定を流し込む。
        ensureShellHistoryConfig(rootfs)
        // セッション復元の cwd 用に、プロンプト毎 OSC 7 (cwd 通知) を出すフックを仕込む。
        ensureOsc7CwdConfig(rootfs)
        // `sshd` コマンドで dropbear が立ち上がるよう wrapper を配置 (OpenSSH sshd は
        // proot で privsep 破綻 / sshd_config の UsePrivilegeSeparation で起動不可)。
        ensureSshdWrapper(rootfs)
        // `z2gui` で Linux GUI (Xvnc + WM) を起動できるよう launcher を配置。
        // GUI 内ターミナルは設定由来 (GuiSession が渡す)。端末起動では既定 xterm のまま。
        ensureGuiScript(rootfs, guiTerminal)
        // `z2run` ランチャ (P3): 端末で `z2run <gui-app>` を打つと、Z2_DISPLAY=:N の Xvnc を
        // 自動起動 + z2term に「OPEN N」を通知 → 該当 GUI タブが自動的に開く / 前面化する。
        ensureZ2RunScript(rootfs)
        // `z2version` で端末からアプリ本体の版数を確認できるようにする (版数不一致の切り分け用)。
        ensureVersionScript(rootfs, if (useZ2root) "z2root" else "proot")
        // 旧「GUI 自動連動」(preexec フック) の後始末。廃止したので既存 rootfs から取り除く。
        removeAutoGuiHook(rootfs)
        // Android API ブリッジのヘルパー (`z2-notify` 等) を配置 (Termux:API 相当)。
        ensureZ2ApiScripts(rootfs)
        // `z2adb` (セルフ adb): 端末自身の adb (ワイヤレスデバッグ) に localhost で繋ぐヘルパー。
        ensureZ2AdbScript(rootfs)
        // `z2help` / `z2term`: 独自 `z2*` コマンドの早見表 (z2term は当面 z2help のエイリアス)。
        ensureZ2HelpScript(rootfs)
        // `z2scan`: 自端末/localhost 限定の脆弱性試験 (自己診断 + nmap/lynis ラッパー)。
        ensureZ2ScanScript(rootfs)
        ensureZ2DoctorScript(rootfs)
        // `z2-macro`: 自動化マクロの同梱サンプル (list/install/show/run)。
        ensureZ2MacroScripts(rootfs)
        // GUI 動画対策: mpv の既定をソフトウェア出力 (vo=x11) にする設定を配置。
        ensureMpvConfig(rootfs)
        // D-Bus セッションバスに必要な machine-id を用意 (空だと「Invalid machine ID」で bus が起動不可)。
        ensureMachineId(rootfs)
        // POSIX 共有メモリ (/dev/shm) の置き場。Android の /dev には shm が無く、ホスト /dev を
        // bind するだけではゲストからも作れない (SELinux で mkdir が EACCES)。
        // 用意しないと shm_open が ENOENT になり、共有メモリ前提の GUI アプリが起動時に自ら中断する。
        // 実体を rootfs 配下の `dev/shm` に置くのは、Kitty graphics の shm 転送
        // (`KittyHostTransferSource`) が shm 名を `<rootfs>/dev/shm/<name>` に rebase するため。
        // ここを別名にすると両者が別の場所を見て転送が空振りする。
        File(rootfs, "dev/shm").mkdirs()
        // XDG_RUNTIME_DIR。GUI タブ配下は z2gui が export するが、端末タブから直接 GUI アプリを
        // 起動する経路には無かった。未設定だと Qt/GTK が警告を出し、D-Bus の socket 置き場も決まらない。
        //
        // z2gui 経由 (`exportDisplay=false`) では **敢えて設定しない**。start_audio 等が
        // `${XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-$DISPLAY_NUM}` と継承値を優先するため、ここで一律に
        // 入れると全ディスプレイが同じディレクトリに集約され、:N 毎の PulseAudio 分離が壊れる。
        val xdgRuntimeDir = when {
            display != null && exportDisplay -> "/tmp/z2gui-xdg-$display"  // 端末から :N へ相乗り
            display == null -> "/tmp/z2-xdg"                               // 端末/SSH 単独
            else -> null                                                   // z2gui 本体は触らない
        }
        if (xdgRuntimeDir != null) {
            File(rootfs, xdgRuntimeDir.trimStart('/')).apply {
                mkdirs()
                setReadable(true, true); setWritable(true, true); setExecutable(true, true)
            }
        }
        // Android 外部ストレージを cd できるようマウント先を用意。
        File(rootfs, "sdcard").mkdirs()
        File(rootfs, "storage/app").mkdirs()
        // 設定で外部 SD 認識が ON のときだけ、検出した物理ボリュームのマウント先も作る。
        // OFF のときは検出も binds 追加も走らず、従来挙動と同じ。
        val externalEnabled = isExternalStorageEnabled()
        val externalVolumes = if (externalEnabled) ExternalStorageDetector.detect(context) else emptyList()
        if (externalVolumes.isNotEmpty()) {
            File(rootfs, "sdcard_ext").mkdirs()
            for (vol in externalVolumes) File(rootfs, vol.trimStart('/')).mkdirs()
        }
        // Android ホスト bind (実験的): ON のとき rootfs 内に /system /apex のマウント先を作る。
        // OFF (既定) は何もしない。
        val androidHostBind = isAndroidHostBindEnabled()
        if (androidHostBind) {
            File(rootfs, "system").mkdirs()
            File(rootfs, "apex").mkdirs()
        }

        // PRoot 引数の組み立て
        val args = mutableListOf<String>().apply {
            add("proot")                                  // argv[0]
            add("--kill-on-exit")
            // z2root 専用。背景に残ったデーモンを道連れにしないため (KDoc 参照)。
            // proot には渡さない — 未知オプションで起動に失敗する。
            if (useZ2root && waitTracees) add("--wait-tracees")
            add("-0")                                     // fake root
            // ハードリンクを symlink でエミュレート。Android のアプリ内ストレージは
            // link() を拒否する (EACCES) ため、これが無いと dpkg が status-old の
            // バックリンク作成に失敗し apt install が壊れる (Ubuntu/Kali)。
            add("--link2symlink")
            add("-r"); add(rootfs.absolutePath)           // rootfs
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            // /dev を bind した「後」に重ねる。ホスト /dev には shm が無いので、これが無いと
            // shm_open("/名前") が ENOENT になる。bind 解決は最長一致なので /dev の上に載る。
            add("-b"); add("${File(rootfs, "dev/shm").absolutePath}:/dev/shm")
            add("-b"); add("${sharedHomeDir.absolutePath}:/root")
            // HOME 内の arch 依存ディレクトリだけをディストリ別オーバーレイで上書き (混在破壊の防止)。
            // /root 全体の bind の後に重ねるので、共有 HOME の上にサブディレクトリだけ差し替わる。
            for ((src, dst) in isolatedHomeBinds(distroId)) {
                add("-b"); add("${src.absolutePath}:$dst")
            }
            // Android 外部ストレージを /sdcard にマウント (cd /sdcard で OS 共有領域へ)。
            // 全ファイルアクセス権が無い場合は中身が読めないが、設定画面から許可できる。
            for ((src, dst) in externalStorageBinds(externalVolumes)) {
                add("-b"); add("$src:$dst")
            }
            // Android ホスト bind (実験的): /system /apex を proot 内へ晒す。Android のリンカ
            // (/system/bin/linker64) と ART ライブラリが見えるようになり、`lzhiyong/termux-ndk`
            // の build-tools (aapt2 等) のように INTERP=/system/bin/linker64 を要求する
            // ARM aarch64 ELF が proot 内で動かせるようになる (= 端末内ビルドの活路)。
            if (androidHostBind) {
                add("-b"); add("/system")
                add("-b"); add("/apex")
            }
            add("-w"); add("/root")                       // working dir
            // command (+ 追加引数: GUI ランチャの "start 1280x720" 等)
            add(resolvedCommand)
            extraArgs.forEach { add(it) }
        }

        // GUI ディスプレイ指定 (非 null のとき)。z2gui がこの番号で Xvnc を立てる。
        // 端末/SSH 起動では null なので何も足さず、従来挙動 (z2gui 既定 :1) のまま。
        // 注意: `exportDisplay=false` のときは DISPLAY を入れない。`z2gui stop` も同じ launch を通る
        // ため、DISPLAY=:N を持たせると stop_x のディスプレイ単位 kill が **自分自身を巻き込む**。
        // GUI 配下の子プロセス (openbox/端末/アプリ) には z2gui の start_x が DISPLAY を export する。
        // 端末/SSH (`exportDisplay=true`) は z2gui 経由ではないので DISPLAY=:N を直接渡してよく、これにより
        // 端末で `z2run <app>` を打つと同じ :N の Xvnc / GUI タブが自動連動する (P3)。
        val displayEnv: List<String> = if (display != null) {
            buildList {
                add("Z2_DISPLAY=$display")
                add("Z2_RFBPORT=${5900 + display}")
                if (exportDisplay) add("DISPLAY=:$display")
                // GUI 音声 (オプトイン)。設定 ON のときだけ port が渡る。z2gui が PulseAudio を起動する。
                if (guiAudioPort != null) {
                    add("Z2_AUDIO=1")
                    add("Z2_AUDIO_PORT=$guiAudioPort")
                }
            }
        } else emptyList()

        // 環境変数
        val env = (listOf(
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            // ⚠ 末尾に**マクロ置き場**を足す (0.8.287)。`z2-macro install remind` で入れたものを
            // `remind.sh …` と名前で打てるようにするため — help も docs もその前提で書いてあるのに
            // PATH に無く、`command not found` になっていた (実機で指摘)。⚠ **末尾**に置くのは、
            // 同名のコマンドがあったときに OS 側を覆わないため。
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$MACRO_DIR",
            "TMPDIR=/tmp",
            "SHELL=$shellForEnv",
            // GUI 内ターミナル (z2gui) 用。z2gui は SHELL を自分自身に上書きされる可能性が
            // あるため SHELL を実体シェルへ張り直すが、その候補としてこれを最優先で見る。
            "Z2_LOGIN_SHELL=$shellForEnv",
            // コマンド履歴を充実化。bash は PROMPT_COMMAND='history -a' で 1 コマンド毎に
            // 即 .bash_history へ追記 → proot が SIGKILL されても履歴が残る。
            // (zsh 用の INC_APPEND_HISTORY 等は ensureShellHistoryConfig が rc に書く)
            "HISTSIZE=10000",
            "HISTFILESIZE=20000",
            "SAVEHIST=10000",
            "HISTCONTROL=ignoredups",
            "PROMPT_COMMAND=history -a",
            // GUI は Xvnc = GPU/DRI 無しのソフトウェア画面。OpenGL アプリ (mpv/SMPlayer の gpu 出力,
            // ブラウザ等) が実機の pvr 等ハードドライバを掴もうとして "failed to load driver" で
            // 映像が出ない/化ける。Mesa を強制的にソフトウェア (llvmpipe/swrast) に倒して回避する。
            "LIBGL_ALWAYS_SOFTWARE=1",
            // proot は libtalloc.so.2 にリンクされている (Termux RUNPATH 由来)。
            // ensureProotLibs() で展開した SONAME 通りのファイルパスを LD_LIBRARY_PATH に追加。
            "LD_LIBRARY_PATH=${prootLibsDir.absolutePath}",
            // PRoot 自身の動作用
            "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
            "PROOT_LOADER=${File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath}"
        ) + listOfNotNull(xdgRuntimeDir?.let { "XDG_RUNTIME_DIR=$it" })
            + displayEnv + z2rootEnv(useZ2root)).toTypedArray()

        val engineName = if (useZ2root) "z2root" else "PRoot"
        Log.i(TAG, "Launching $engineName: distro=$distroId, command=$resolvedCommand (requested=$command)")
        Log.d(TAG, "Args: ${args.joinToString(" ")}")

        return PtyProcess.create(
            command = engineBinary.absolutePath,
            args = args.toTypedArray(),
            env = env,
            cwd = context.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
    }

    /**
     * 裏機能: root 端末で実 `chroot` 起動する。[launch] (PRoot) の代替経路。
     *
     * rootfs・スクリプト注入 (履歴/OSC7/sshd/gui/z2run/z2-api) は PRoot 経路と共通で流用し、
     * proot バイナリの代わりに `su -c` でブートストラップ (bind mount → chroot → login shell) を起動する。
     * libtalloc/proot loader は不要。未 root (su 解決不可) なら例外を投げる (呼び出し側で proot へフォールバック)。
     */
    fun launchChroot(
        distroId: String = "alpine",
        command: String = "/bin/sh",
        rows: Int = 24,
        cols: Int = 80,
        fallbackShell: String = "/bin/sh",
        /** 設定「ログインシェル」。proot 経路と同じく `/etc/passwd`(root) に反映して SSH にも効かせる。 */
        loginShell: String = "",
        guiTerminal: GuiTerminal = GuiTerminal.XTERM,
        display: Int? = null
    ): PtyProcess {
        val rootfs = File(distrosDir, distroId)
        if (!rootfs.exists()) throw IllegalStateException("Rootfs not found: ${rootfs.absolutePath}")
        val su = resolveSu() ?: throw IllegalStateException("su not found (device not rooted)")

        // PRoot 経路と同じ rootfs セットアップ (proot libs / loader は chroot では不要)。
        sharedHomeDir.mkdirs()
        ensureRootLoginShell(
            rootfs,
            if (loginShell.isBlank()) "" else resolveShell(rootfs, loginShell, fallbackShell)
        )
        ensureShellHistoryConfig(rootfs)
        ensureOsc7CwdConfig(rootfs)
        ensureSshdWrapper(rootfs)
        ensureGuiScript(rootfs, guiTerminal)
        ensureZ2RunScript(rootfs)
        // `z2version` で端末からアプリ本体の版数を確認できるようにする (版数不一致の切り分け用)。
        ensureVersionScript(rootfs, "chroot")
        removeAutoGuiHook(rootfs)
        ensureZ2ApiScripts(rootfs)
        // `z2adb` (セルフ adb): 端末自身の adb (ワイヤレスデバッグ) に localhost で繋ぐヘルパー。
        ensureZ2AdbScript(rootfs)
        // `z2help` / `z2term`: 独自 `z2*` コマンドの早見表 (z2term は当面 z2help のエイリアス)。
        ensureZ2HelpScript(rootfs)
        // `z2scan`: 自端末/localhost 限定の脆弱性試験 (自己診断 + nmap/lynis ラッパー)。
        ensureZ2ScanScript(rootfs)
        ensureZ2DoctorScript(rootfs)
        // `z2-macro`: 自動化マクロの同梱サンプル (list/install/show/run)。
        ensureZ2MacroScripts(rootfs)
        ensureMpvConfig(rootfs)
        File(rootfs, "sdcard").mkdirs()
        File(rootfs, "storage/app").mkdirs()
        val externalEnabled = isExternalStorageEnabled()
        val externalVolumes = if (externalEnabled) ExternalStorageDetector.detect(context) else emptyList()
        if (externalVolumes.isNotEmpty()) {
            File(rootfs, "sdcard_ext").mkdirs()
            for (vol in externalVolumes) File(rootfs, vol.trimStart('/')).mkdirs()
        }
        val androidHostBind = isAndroidHostBindEnabled()
        if (androidHostBind) {
            File(rootfs, "system").mkdirs()
            File(rootfs, "apex").mkdirs()
        }

        val resolvedShell = resolveShell(rootfs, command, fallbackShell)
        val script = chrootBootstrap(
            rootfs.absolutePath, sharedHomeDir.absolutePath, resolvedShell,
            display, externalVolumes, androidHostBind, isolatedHomeBinds(distroId)
        )

        Log.i(TAG, "Launching chroot: distro=$distroId, su=$su, shell=$resolvedShell")
        return PtyProcess.create(
            command = su,
            args = arrayOf("su", "-c", script),
            env = arrayOf(
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                "HOME=${context.filesDir.absolutePath}",
                "TERM=xterm-256color"
            ),
            cwd = context.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
    }

    /**
     * root + chroot exec が使えるかをセルフテストする (裏機能解放時に1回呼ぶ)。
     * 1) `su -c id` で uid=0 を確認 (初回は root マネージャの許可ダイアログが出る)。
     * 2) `su -c "chroot <rootfs> /bin/sh -c echo"` で app_data_file の root exec が
     *    SELinux 等に弾かれないかを確認する。
     */
    fun probeRootChroot(distroId: String = DistroBundle.BUNDLED_DISTRO_ID): RootProbe {
        val su = resolveSu() ?: return RootProbe.NoRoot
        val id = runSuCapture(su, "id", 10_000L) ?: return RootProbe.NoRoot
        if (!id.contains("uid=0")) return RootProbe.NoRoot
        val rootfs = File(distrosDir, distroId).takeIf { it.exists() }
            ?: distrosDir.listFiles()?.firstOrNull {
                File(it, "bin/sh").exists() || File(it, "bin/busybox").exists()
            }
            ?: return RootProbe.ChrootBlocked("rootfs not ready")
        val test = runSuCapture(su, "chroot ${shq(rootfs.absolutePath)} /bin/sh -c 'echo Z2OK'", 10_000L)
        return if (test != null && test.contains("Z2OK")) RootProbe.Ok
        else RootProbe.ChrootBlocked((test ?: "").trim().take(200))
    }

    /** PATH 上の su を絶対パスで解決する (Magisk 等は実ファイルが固定パスに無いため `command -v` で探す)。 */
    private fun resolveSu(): String? = runCatching {
        val p = ProcessBuilder("/system/bin/sh", "-c", "command -v su").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("/") }
    }.getOrNull()

    /** `su -c <cmd>` を timeout 付きで実行し、stdout+stderr を返す (失敗/timeout は null)。 */
    private fun runSuCapture(su: String, cmd: String, timeoutMs: Long): String? = runCatching {
        val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
        val finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) { p.destroy(); return@runCatching null }
        p.inputStream.bufferedReader().readText()
    }.getOrNull()

    /** chroot ブートストラップ (root で実行)。bind mount → env -i で login shell を chroot 起動。 */
    private fun chrootBootstrap(
        rootfs: String,
        sharedHome: String,
        shell: String,
        display: Int?,
        externalVolumes: List<String> = emptyList(),
        androidHostBind: Boolean = false,
        homeOverlayBinds: List<Pair<File, String>> = emptyList()
    ): String {
        val rfs = shq(rootfs)
        val home = shq(sharedHome)
        val sh = shq(shell)
        val displayEnv = if (display != null) " DISPLAY=:$display Z2_DISPLAY=$display Z2_RFBPORT=${5900 + display}" else ""
        return buildString {
            append("export PATH=/system/bin:/system/xbin:/vendor/bin:\$PATH\n")
            append("RFS=").append(rfs).append('\n')
            append("SHOME=").append(home).append('\n')
            // 前回 chroot が残したマウントを掃除 (リーク回収)。外部 SD のマウント先も掃除対象に含める。
            // androidHostBind ON のときは /system /apex も掃除対象に。OFF でも掃除を試みるのは
            // 「前回 ON で起動 → OFF に切替 → 再起動」のときマウントが残ったままになるのを防ぐため。
            append("for m in dev/pts dev/shm dev proc sys")
            // HOME 隔離オーバーレイは root より先に剥がす (root の lazy umount で取り残されないよう)。
            for ((_, dst) in homeOverlayBinds) append(' ').append(shq(dst.trimStart('/')))
            append(" root sdcard sdcard_ext system apex")
            for (vol in externalVolumes) {
                append(' ').append(shq(vol.trimStart('/')))
            }
            append("; do umount -l \"\$RFS/\$m\" 2>/dev/null; done\n")
            append("mkdir -p \"\$RFS/dev\" \"\$RFS/dev/pts\" \"\$RFS/proc\" \"\$RFS/sys\" \"\$RFS/root\" \"\$RFS/sdcard\" \"\$RFS/tmp\"\n")
            append("mount -o bind /dev \"\$RFS/dev\"\n")
            append("mount -o bind /dev/pts \"\$RFS/dev/pts\" 2>/dev/null\n")
            // POSIX 共有メモリ。Android の /dev には shm が無いため、bind しただけでは
            // shm_open が ENOENT になり共有メモリ前提の GUI アプリが起動時に自ら中断する。
            // ここは実 root なので tmpfs を直接被せる (mkdir はホスト /dev 側に出るため best-effort)。
            append("mkdir -p \"\$RFS/dev/shm\" 2>/dev/null\n")
            append("mount -t tmpfs -o mode=1777,nosuid,nodev tmpfs \"\$RFS/dev/shm\" 2>/dev/null\n")
            append("mount -o bind /proc \"\$RFS/proc\"\n")
            append("mount -o bind /sys \"\$RFS/sys\"\n")
            append("mount -o bind \"\$SHOME\" \"\$RFS/root\"\n")
            // HOME 内の arch 依存ディレクトリだけをディストリ別オーバーレイで上書き (混在破壊の防止)。
            for ((src, dst) in homeOverlayBinds) {
                val rel = dst.trimStart('/')
                append("mkdir -p \"\$RFS/").append(rel).append("\" 2>/dev/null\n")
                append("mount -o bind ").append(shq(src.absolutePath))
                append(" \"\$RFS/").append(rel).append("\" 2>/dev/null\n")
            }
            append("mount -o bind /sdcard \"\$RFS/sdcard\" 2>/dev/null\n")
            // 外部 SD カード (設定で ON のときだけ呼び出し側が渡す)。
            // /sdcard_ext は最初の1つのエイリアス。proot 経路と同じ取り扱いに揃える。
            for ((i, vol) in externalVolumes.withIndex()) {
                val srcQ = shq(vol)
                val rel = vol.trimStart('/')
                append("mkdir -p \"\$RFS/").append(rel).append("\" 2>/dev/null\n")
                append("mount -o bind ").append(srcQ).append(" \"\$RFS/").append(rel).append("\" 2>/dev/null\n")
                if (i == 0) {
                    append("mkdir -p \"\$RFS/sdcard_ext\" 2>/dev/null\n")
                    append("mount -o bind ").append(srcQ).append(" \"\$RFS/sdcard_ext\" 2>/dev/null\n")
                }
            }
            // Android ホスト bind (実験的): /system /apex を chroot 内に晒す。proot 経路と同じ目的で、
            // Android リンカ + ART ライブラリを使う ARM aarch64 ELF (aapt2 等) を chroot 内で実行可能にする。
            if (androidHostBind) {
                append("mkdir -p \"\$RFS/system\" \"\$RFS/apex\" 2>/dev/null\n")
                append("mount -o bind /system \"\$RFS/system\" 2>/dev/null\n")
                append("mount -o bind /apex \"\$RFS/apex\" 2>/dev/null\n")
            }
            append("exec chroot \"\$RFS\" /usr/bin/env -i HOME=/root TERM=xterm-256color LANG=C.UTF-8 ")
            // ⚠ proot 経路と同じくマクロ置き場を末尾に足す (0.8.287)。
            append("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$MACRO_DIR TMPDIR=/tmp")
            append(displayEnv)
            // 制御端末を取り直してジョブ制御 / Ctrl+C を効かせる。
            // chroot は su(magiskd)経由で起動するため root shell が PTY を制御端末として
            // 所有できず "no job control" になり、Ctrl+C(VINTR)の SIGINT が走行中コマンドへ
            // 届かない。login shell を setsid -c で起動して PTY を制御端末に握り直す。
            // setsid -w(util-linux)が無い環境(busybox 等)は従来どおり素のシェル(回帰なし)。
            append(" SHELL=").append(sh).append(' ').append(sh).append(" -c '")
            append("if command -v setsid >/dev/null 2>&1 && setsid --help 2>&1 | grep -q -- \"-w\"; ")
            append("then exec setsid -w -c \"\$SHELL\" -l; else exec \"\$SHELL\" -l; fi")
            append("'\n")
        }
    }

    /** shell 用シングルクォート。 */
    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

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
     * rootfs の `/etc/passwd` にある **root のログインシェル**を [shell] に揃える (= `chsh` 相当)。
     *
     * 設定「ログインシェル」はアプリの端末タブ (エンジンが直接 exec する) にしか効かず、
     * **SSH ログイン** (dropbear は `/etc/passwd` の shell を起動する) や GUI 内ターミナルでは
     * distro 既定 (bash 等) のままだった。通常の Linux なら利用者が `chsh` でやることを、
     * 設定 1 箇所で全入口に効かせるためここで代行する。
     *
     * [shell] が空 / rootfs に存在しないときは何もしない (既存の passwd を壊さない)。
     */
    private fun ensureRootLoginShell(rootfs: File, shell: String) {
        if (shell.isBlank() || !shellExists(rootfs, shell)) return
        runCatching {
            val passwd = File(rootfs, "etc/passwd")
            if (!passwd.isFile) return
            var changed = false
            val updated = passwd.readLines().map { line ->
                val f = line.split(':')
                if (f.size < 7 || f[0] != "root" || f[6] == shell) line
                else {
                    changed = true
                    (f.subList(0, 6) + shell).joinToString(":")
                }
            }
            if (changed) {
                passwd.writeText(updated.joinToString("\n") + "\n")
                Log.i(TAG, "root login shell set to $shell")
            }
            // `su` / `chsh` が「正規のシェル」として認めるよう /etc/shells にも載せる。
            val shells = File(rootfs, "etc/shells")
            val listed = if (shells.isFile) shells.readLines().map { it.trim() } else emptyList()
            if (shell !in listed) {
                shells.parentFile?.mkdirs()
                shells.appendText(shell + "\n")
            }
        }.onFailure { Log.w(TAG, "Failed to set root login shell: ${it.message}") }
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
        if (pathPresent(File(rootfs, rel))) return true
        // usrmerge: /bin/bash → /usr/bin/bash
        if (rel.startsWith("bin/") || rel.startsWith("sbin/")) {
            if (pathPresent(File(rootfs, "usr/$rel"))) return true
        }
        return false
    }

    /**
     * パスが実体 (通常ファイル) または symlink として存在するか。
     *
     * Alpine minirootfs の `/bin/sh -> /bin/busybox` の様な **絶対 symlink** は、
     * File.exists() がリンク先をホストの filesystem root 起点で解決するため
     * (ゲストの rootfs ではなく host:/bin/busybox を見にいく) 常に false になり、
     * resolveShell のフォールバックが全滅して bogus な要求値 (例 /bin/zsh) が
     * そのまま execve され ENOENT で起動失敗していた。ゲスト名前空間ではリンクが
     * 正しく解決されるので、ここでは NOFOLLOW でリンク自体の存在だけを見る。
     */
    private fun pathPresent(f: File): Boolean {
        if (f.exists()) return true
        return try {
            java.nio.file.Files.exists(f.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Android 外部ストレージを proot 内へバインドするための (src, dst) ペアを返す。
     *  - 端末の共有ストレージ全体 (/storage/emulated/0) → /sdcard
     *    (全ファイルアクセス権が無いと中身は EACCES だが、設定で許可すれば読める)
     *  - アプリ専用外部領域 (権限不要・常に読み書き可) → /storage/app
     *  - 外部 SD カード (検出済みボリューム) → 同名 + 最初の1つは /sdcard_ext エイリアス。
     *    設定で「外部ストレージ認識」が ON のときだけ呼び出し側から渡される。
     */
    // ここの "/sdcard" / "/sdcard_ext" は **proot ゲスト側のマウント先パス** (rootfs 内の宛先) で、
    // Android のホストパスではない。ホスト側の実体は Environment.getExternalStorageDirectory() /
    // getExternalFilesDir() から取っており、lint の指摘 (ハードコードするな) は既に満たしている。
    @Suppress("SdCardPath")
    private fun externalStorageBinds(externalVolumes: List<String>): List<Pair<String, String>> {
        val binds = mutableListOf<Pair<String, String>>()
        runCatching {
            val ext = android.os.Environment.getExternalStorageDirectory()
            if (ext != null && ext.exists()) binds += ext.absolutePath to "/sdcard"
        }
        runCatching {
            val appExt = context.getExternalFilesDir(null)
            if (appExt != null) { appExt.mkdirs(); binds += appExt.absolutePath to "/storage/app" }
        }
        for ((i, vol) in externalVolumes.withIndex()) {
            binds += vol to vol
            if (i == 0) binds += vol to "/sdcard_ext"
        }
        return binds
    }

    /**
     * 設定「外部ストレージ認識」の現在値を同期的に読む。
     * proot 起動は I/O を伴うのでブロッキング読み出しで OK (Worker スレッド前提)。
     * 失敗 (DataStore 初期化前など) は false に倒して従来挙動を維持。
     */
    private fun isExternalStorageEnabled(): Boolean = runCatching {
        runBlocking { AppSettings(context).flow.first().externalStorageEnabled }
    }.getOrDefault(false)

    /**
     * 設定「Android ホスト bind (実験的)」の現在値を同期的に読む。
     * [isExternalStorageEnabled] と同じ理由でブロッキング読み出し可。失敗時は false に倒し、
     * 「設定が壊れていれば従来挙動」という安全側に倒す。
     */
    private fun isAndroidHostBindEnabled(): Boolean = runCatching {
        runBlocking { AppSettings(context).flow.first().androidHostBindEnabled }
    }.getOrDefault(false)

    /**
     * 設定「実行エンジン」が z2root を指しているかを同期的に読む ([isExternalStorageEnabled] と同方式)。
     * 失敗時は false に倒して proot 既定へ。chroot は別経路 ([launchChroot]) なのでここでは扱わない。
     */
    private fun isZ2rootEngineSelected(): Boolean = runCatching {
        runBlocking { AppSettings(context).flow.first().executionEngine == AppSettings.ENGINE_Z2ROOT }
    }.getOrDefault(false)

    /**
     * 設定「トレースログ」(開発者用) が ON かを同期的に読む ([isExternalStorageEnabled] と同方式)。
     * 失敗時は false に倒して「壊れていればトレースしない」安全側へ。
     */
    private fun isTraceLogEnabled(): Boolean = runCatching {
        runBlocking { AppSettings(context).flow.first().traceLogEnabled }
    }.getOrDefault(false)

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
     * cwd を OSC 7 でアプリへ通知するシェルフックを rootfs に仕込む (セッション復元の cwd 用)。
     *
     * 多くの distro はプロンプト毎に OSC 7 を出さないため `cd` しても cwd を捕捉できず、
     * セッション復元で作業ディレクトリが戻らない。bash の PROMPT_COMMAND / zsh の precmd で
     * 「ESC ] 7 ; file://host$PWD BEL」を出すフックを足し、各プロンプトで cwd を
     * [com.zerotoship.z2term.emulator.TerminalEmulator] へ届けて [com.zerotoship.z2term.core.SessionStore]
     * に保存させる。履歴設定とは別マーカーにして、既に履歴ブロックを持つ既存 rootfs にも後付けされるようにする。
     */
    private fun ensureOsc7CwdConfig(rootfs: File) {
        val marker = "# >>> z2term osc7 >>>"

        val bashBlock = """
            |$marker
            |if [ -n "${'$'}BASH_VERSION" ]; then
            |  __z2term_osc7() { printf '\033]7;file://%s%s\a' "${'$'}{HOSTNAME:-localhost}" "${'$'}PWD"; }
            |  case ":${'$'}PROMPT_COMMAND:" in
            |    *__z2term_osc7*) ;;
            |    *) PROMPT_COMMAND="__z2term_osc7${'$'}{PROMPT_COMMAND:+; ${'$'}PROMPT_COMMAND}" ;;
            |  esac
            |fi
            |# <<< z2term osc7 <<<
        """.trimMargin()

        val zshBlock = """
            |$marker
            |if [ -n "${'$'}ZSH_VERSION" ]; then
            |  __z2term_osc7() { printf '\033]7;file://%s%s\a' "${'$'}{HOST:-localhost}" "${'$'}PWD"; }
            |  autoload -Uz add-zsh-hook 2>/dev/null && add-zsh-hook precmd __z2term_osc7
            |fi
            |# <<< z2term osc7 <<<
        """.trimMargin()

        appendOnceWithMarker(File(rootfs, "etc/bash.bashrc"), marker, bashBlock)
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
            f.writeText(dropbearBootstrapScript(
                strings = SshdScriptStrings.forLang(LocaleHelper.language(context))
            ))
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
                    terminalPackage = guiTerminal.packageName,
                    // proot 内 z2gui の echo メッセージをアプリ言語設定に追従させる。
                    // launch 毎に書き直されるので、言語切替後の次回起動で反映される。
                    strings = GuiScriptStrings.forLang(LocaleHelper.language(context))
                )
            )
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2gui script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2run` ランチャを配置する (P3 = CUI⇄GUI 連動)。
     * `z2run <gui-app...>` で「:N の Xvnc 確保 → z2term へ OPEN 通知 → app exec」を一気通貫で実行。
     * 端末タブの proot env (`DISPLAY=:N`/`Z2_DISPLAY=N`) と組み合わせて使う前提。launch 毎に
     * 上書きするので内容は常に最新。
     */
    private fun ensureZ2RunScript(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val f = File(dir, "z2run")
            f.writeText(z2runScript(lang = LocaleHelper.language(context)))
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2run script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2version` を配置する。端末から `z2version` でアプリ本体の版数を確認できる
     * (proot/z2root どちらの実行エンジンでも同じ)。launch 毎に書き直すので、表示は常に「今まさに
     * 走っているアプリ」の版数＝APK とゲスト側の版数不一致を即座に切り分けられる。
     */
    private fun ensureVersionScript(rootfs: File, engine: String) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val flavor = if (BuildConfig.IS_FOSS) "FOSS" else "Full"
            val f = File(dir, "z2version")
            // 値は全てビルド定数 (外部入力なし)。`--short` は版数のみを 1 行で返す (スクリプト用)。
            f.writeText(
                """
                #!/bin/sh
                # z2term app version (launch 毎にアプリが再生成)
                if [ "${'$'}1" = "--short" ]; then
                  echo "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                  exit 0
                fi
                echo "z2term ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                echo "flavor : $flavor"
                echo "package: ${BuildConfig.APPLICATION_ID}"
                echo "engine : $engine"
                echo "rootfs : gen ${DistroBundle.ROOTFS_VERSION}"
                # 実行中の OS (ゲスト distro) と kernel。/etc/os-release はゲスト自身のファイルを
                # サブシェルで読むだけ (PRETTY_NAME を取り出す)。uname はゲストから見えるカーネル。
                if [ -r /etc/os-release ]; then
                  echo "os     : ${'$'}(. /etc/os-release 2>/dev/null; echo "${'$'}PRETTY_NAME")"
                fi
                echo "kernel : ${'$'}(uname -srm 2>/dev/null)"
                """.trimIndent() + "\n"
            )
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2version script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2adb` を配置する (セルフ adb)。端末自身の adb デーモン
     * (Android のワイヤレスデバッグ) に `localhost` で繋ぐためのヘルパー。PC・USB・root 不要。
     * `z2adb setup`/`pair`/`connect`/`status` の他は素の adb へ passthrough。launch 毎に上書き。
     */
    private fun ensureZ2AdbScript(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val f = File(dir, "z2adb")
            f.writeText(z2adbScript(lang = LocaleHelper.language(context)))
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2adb script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2help` (独自コマンド早見表) と、その薄いエイリアス `/usr/local/bin/z2term`
     * を配置する。端末から `z2help` / `z2term` で `z2*` コマンド一覧を引ける。launch 毎に上書き。
     * `z2term` は当面 `z2help` のエイリアス (予約コマンド)。将来別用途に使うときは
     * [z2termAliasScript] を差し替える。
     */
    private fun ensureZ2HelpScript(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val help = File(dir, "z2help")
            help.writeText(z2helpScript(lang = LocaleHelper.language(context)))
            help.setReadable(true, false)
            help.setExecutable(true, false)
            val term = File(dir, "z2term")
            term.writeText(z2termAliasScript())
            term.setReadable(true, false)
            term.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2help script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2scan` (自端末/localhost 限定の脆弱性試験) を配置する。内蔵の自己診断
     * (`self`) と、distro 公式パッケージの nmap/lynis/trivy を導入して叩くラッパーから成る。
     * ネットワークスキャンは既定 localhost 固定・外部対象は明示フラグ必須。launch 毎に上書き。
     */
    /**
     * `/usr/local/bin/z2-macro` と、同梱サンプル `/usr/local/share/z2term/macros/` 配下の .sh を配置する。
     * サンプルは**共有領域に置くだけ**で、HOME (`~/.z2term/macros/`) へは `z2-macro install`
     * が明示的にコピーしたときだけ入る (ユーザーが編集したものを launch 毎に上書きしないため)。
     */
    private fun ensureZ2MacroScripts(rootfs: File) {
        runCatching {
            val lang = LocaleHelper.language(context)
            val bin = File(rootfs, "usr/local/bin").apply { mkdirs() }
            File(bin, "z2-macro").apply {
                writeText(z2MacroScript(lang = lang))
                setReadable(true, false)
                setExecutable(true, false)
            }
            val samples = File(rootfs, "usr/local/share/z2term/macros").apply { mkdirs() }
            z2MacroSamples(lang = lang).forEach { (name, body) ->
                File(samples, name).apply {
                    writeText(body)
                    setReadable(true, false)
                    setExecutable(true, false)
                }
            }
        }.onFailure { Log.w(TAG, "z2-macro scripts 配置失敗", it) }
    }

    private fun ensureZ2ScanScript(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val f = File(dir, "z2scan")
            f.writeText(z2scanScript(lang = LocaleHelper.language(context)))
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2scan script 配置失敗", it) }
    }

    /**
     * `/usr/local/bin/z2doctor` を配置する。「動きません」の切り分け診断 (0.8.230)。
     * 名前の近い `z2scan self` は「危ない設定を探す」別物なので、用途を混ぜないこと。
     */
    private fun ensureZ2DoctorScript(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            val f = File(dir, "z2doctor")
            f.writeText(z2doctorScript(lang = LocaleHelper.language(context)))
            f.setReadable(true, false)
            f.setExecutable(true, false)
        }.onFailure { Log.w(TAG, "z2doctor script 配置失敗", it) }
    }

    /**
     * 廃止した「GUI 自動連動」(preexec フック + `/usr/local/bin/z2-autogui`) を rootfs から取り除く。
     *
     * **なぜ廃止したか**: GUI アプリかどうかを「libX11 等にリンクしているか」で判定していたが、
     * **クリップボード連携のために X を張るだけの CUI アプリ (テキストエディタ等) が必ず引っかかる**。
     * 「GUI アプリを起動したら GUI タブを開く」つもりの仕掛けが、CUI を使っているだけの人の画面を
     * 奪う。判定を賢くしても同じ取りこぼしが別の形で出るだけなので、**仕掛けごと畳んだ**。
     * GUI を開く道は「タブを自分で開く」か「`z2run <アプリ>` と明示的に打つ」の 2 つに絞る。
     * 設定での ON/OFF も足さない — 誤爆する機能を選べるようにしても選ぶ理由が無い。
     *
     * 入れるのをやめるだけでは**既に rootfs へ書き込んだ分が残り続ける**ので、能動的に消す。
     * マーカー行ごと取り除くので、ユーザーが自分で書いた行は触らない。
     */
    private fun removeAutoGuiHook(rootfs: File) {
        val begin = "# >>> z2term autogui >>>"
        val end = "# <<< z2term autogui <<<"
        removeBlockWithMarker(File(rootfs, "etc/bash.bashrc"), begin, end)
        removeBlockWithMarker(File(rootfs, "etc/zsh/zshrc"), begin, end)
        runCatching { File(rootfs, "usr/local/bin/z2-autogui").delete() }
    }

    /**
     * [begin] 行から [end] 行までを (両端を含めて) ファイルから取り除く。マーカーが無ければ何もしない。
     * 書き込みは中身が変わったときだけ (毎 launch で無駄に触らない)。失敗は握り潰す。
     */
    private fun removeBlockWithMarker(file: File, begin: String, end: String) {
        runCatching {
            if (!file.exists()) return
            val lines = file.readLines()
            if (lines.none { it.trim() == begin }) return
            val kept = mutableListOf<String>()
            var dropping = false
            for (line in lines) {
                when {
                    line.trim() == begin -> dropping = true
                    line.trim() == end -> dropping = false
                    !dropping -> kept.add(line)
                }
            }
            file.writeText(kept.joinToString("\n").trimEnd('\n') + "\n")
        }.onFailure { Log.w(TAG, "autogui フック除去失敗: ${file.absolutePath}", it) }
    }

    /**
     * mpv の既定設定 `/etc/mpv/mpv.conf` を配置する (GUI 動画対策)。GUI は Xvnc = GPU 無しの
     * ソフトウェア画面なので、mpv 既定の gpu 出力 / ハードデコードは失敗し、映像が化ける・半分
     * しか出ない。出力を x11 (ソフト RGB)・デコードを CPU に倒すと正常に再生できる
     * (SMPlayer も mpv バックエンド経由でこの既定に従う)。
     * ユーザーが自分の設定 (`~/.config/mpv/mpv.conf` が優先) や独自の `/etc/mpv/mpv.conf` を
     * 置いている場合は尊重し、**既存ファイルがあるときは触らない**。
     */
    private fun ensureMpvConfig(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "etc/mpv").apply { mkdirs() }
            val f = File(dir, "mpv.conf")
            if (f.exists()) return
            f.writeText(
                """
                # z2term default: GUI は Xvnc (GPU 無し) のため映像はソフトウェア出力にする。
                # 個人設定 (~/.config/mpv/mpv.conf) があればそちらが優先される。
                vo=x11
                hwdec=no
                """.trimIndent() + "\n"
            )
            f.setReadable(true, false)
        }.onFailure { Log.w(TAG, "mpv.conf 配置失敗", it) }
    }

    /**
     * `/etc/machine-id` を用意する (D-Bus セッションバスの前提)。
     *
     * ディストリの rootfs には空の `/etc/machine-id` が入っていることがあり、その状態だと
     * dbus が "Invalid machine ID" でバスを起動できず、D-Bus を要求する GUI アプリ
     * (アクセシビリティバス経由のものを含む) が警告や機能欠落を起こす。
     * 中身があるときは触らない (端末を跨いで ID が変わらないようにする)。
     */
    private fun ensureMachineId(rootfs: File) {
        runCatching {
            val f = File(rootfs, "etc/machine-id")
            if (f.isFile && f.length() > 0L) return
            f.parentFile?.mkdirs()
            // rootfs 側は 0400 で置かれていることがあるので、書く前に権限を戻す。
            f.setWritable(true, true)
            // systemd と同じ形式: ハイフン無しの 32 桁 hex。
            f.writeText(UUID.randomUUID().toString().replace("-", "") + "\n")
            f.setReadable(true, false)
        }.onFailure { Log.w(TAG, "machine-id 生成失敗", it) }
    }

    /**
     * Android API ブリッジのヘルパー群 (`z2api` + `z2-notify`/`z2-clip`/…) を `/usr/local/bin`
     * へ配置する。端末から `z2-notify "done"` 等で Android 機能を呼べる (Termux:API 相当)。
     * 受け手は [com.zerotoship.z2term.service.Z2ApiBridge]。launch 毎に上書きするので常に最新。
     */
    private fun ensureZ2ApiScripts(rootfs: File) {
        runCatching {
            val dir = File(rootfs, "usr/local/bin").apply { mkdirs() }
            // 端末に出る文言はアプリの言語設定に合わせる (z2help と同じ扱い)。
            z2ApiScripts(lang = LocaleHelper.language(context)).forEach { (name, body) ->
                val f = File(dir, name)
                f.writeText(body)
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }.onFailure { Log.w(TAG, "z2api scripts 配置失敗", it) }
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

    /**
     * 実行エンジンのバイナリが配置されているか確認 (起動可否ゲート)。
     * foss は proot を同梱しないため z2root の有無で判定する。full は proot を見る
     * (full でも z2root は別経路で使えるが、同梱保証されるのは proot 側)。
     */
    fun isProotAvailable(): Boolean =
        if (BuildConfig.IS_FOSS) z2rootBinary.exists() else prootBinary.exists()

    /**
     * [launch] が**実際に**使うエンジン名を返す ([AppSettings.ENGINE_PROOT] か [ENGINE_Z2ROOT])。
     * 設定値そのものではなく、`isZ2rootEngineSelected() && z2rootBinary.exists()` の
     * 実起動判定を反映する (設定で z2root でも `libz2root.so` 未同梱なら proot に倒れる)。
     * chroot は別経路 ([launchChroot]) なのでここでは扱わない (呼び出し側が判定する)。
     * 設定画面で「いま本当に動いているエンジン」を信頼できる形で出すために使う。
     */
    fun resolveLaunchEngine(): String =
        if ((BuildConfig.IS_FOSS || isZ2rootEngineSelected()) && z2rootBinary.exists()) AppSettings.ENGINE_Z2ROOT
        else AppSettings.ENGINE_PROOT

    companion object {
        private const val TAG = "ProotLauncher"

        /**
         * マクロ置き場 (`z2-macro install` の展開先)。**PATH の末尾**に足して、入れたマクロを
         * 名前で打てるようにする。⚠ `HOME=/root` 固定なのでここも実パスで持つ (env の値は
         * 展開されないため `$HOME` とは書けない)。
         */
        private const val MACRO_DIR = "/root/.z2term/macros"

        /** SHELL に採用してよい既知のシェル basename (これ以外は実体シェルへ振り替える)。 */
        private val KNOWN_SHELLS = setOf("sh", "bash", "ash", "dash", "zsh", "ksh", "mksh")
    }
}
