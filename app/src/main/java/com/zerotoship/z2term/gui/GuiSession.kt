package com.zerotoship.z2term.gui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.Z2TERM_VNC_DISPLAY
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.ConnectException

/**
 * Linux GUI セッションのライフサイクル (M8-2: 表示のみ)。
 *
 *  1. PRoot で `z2gui start <W>x<H>` を起動（[ProotLauncher] 流用）。z2gui は内部で apk add 済み確認 →
 *     Xvnc + openbox を起動し、`wait` でブロックし続ける（proot が生き続ける）。
 *  2. RFB ポート (127.0.0.1:5901) が開くのを待つ。
 *  3. [RfbClient] で接続し、受信ループを IO で回す → [RfbClient.frame] が更新され GuiScreen が描画。
 *  4. [stop] で PtyProcess を閉じる → proot 終了 → `--kill-on-exit` で Xvnc も停止。
 *
 * 入力（ポインタ/キー）は M8-3、タブ統合・ズーム/パン等は M8-4。
 */
class GuiSession(
    private val context: Context,
    /**
     * この GUI セッションが使う仮想 X ディスプレイ番号 (`:N`)。RFB ポートは `5900 + display`。
     * [SessionManager.openNewGui] が空き番号を払い出して渡すので、複数 GUI タブが
     * **別ポート・別画面**で並走できる (既定 1 = 従来互換)。
     * P3 (CUI⇄GUI 連動) では同番号の端末タブとペアになり、`z2run` 経由で開いた GUI タブもここに入る。
     */
    override val display: Int = Z2TERM_VNC_DISPLAY,
    override val id: String = java.util.UUID.randomUUID().toString()
) : com.zerotoship.z2term.core.AppSession {

    enum class State { IDLE, STARTING, CONNECTING, CONNECTED, ERROR, STOPPED }

    /** RFB ポート = 5900 + ディスプレイ番号 (VNC 標準慣例。:1→5901, :2→5902 …)。 */
    private val rfbPort: Int = 5900 + display

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    /** タブ表示名。ディスプレイ番号付きで複数 GUI タブを区別できるようにする (例 "GUI:2")。 */
    private val _label = MutableStateFlow(if (display == Z2TERM_VNC_DISPLAY) "GUI" else "GUI:$display")
    override val label: StateFlow<String> = _label.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    val rfb = RfbClient(port = rfbPort).also { client ->
        // GUI (xterm 等) で選択/コピーしたテキストを Android クリップボードへ反映 (M8-6 T6)。
        client.onServerCutText = { text -> copyToAndroidClipboard(text) }
    }

    /** ズーム/パンの表示変換。GuiScreen(描画) と GuiInputView(入力) で共有。タブ切替・回転でも保持。 */
    val viewport = GuiViewport()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pty: PtyProcess? = null
    private var rxJob: Job? = null

    /**
     * z2gui (proot) の PTY が閉じた = z2gui が終了した (パッケージ導入失敗で exit 等)。
     * Xvnc がもう立たない状態なので、接続待ちを最後まで粘らず即打ち切るために監視する。
     */
    @Volatile
    private var ptyClosed = false

    /** 起動した distro。停止 (runGuiStop) でも同じ distro を使うため start で確定させる。 */
    private var distroId: String = "alpine"

    /**
     * GUI を起動する。
     *
     * @param clean true なら z2gui に `clean` を渡し、GUI パッケージをキャッシュごと
     *   入れ直す (ダウンロード/解凍失敗で詰まった状態からの救済)。
     */
    fun start(width: Int, height: Int, clean: Boolean = false) {
        when (_state.value) {
            State.STARTING, State.CONNECTING, State.CONNECTED -> return
            else -> {}
        }
        _state.value = State.STARTING
        _message.value = if (clean) "GUI をクリーンインストール中… (${width}x$height)"
                         else "GUI を起動中… (${width}x$height)"
        scope.launch {
            try {
                // 選択中の OS とターミナルで起動する (HANDOFF「選択中のOSで立ち上げ」要望)。
                val snap = AppSettings(context).flow.first()
                distroId = snap.distroId
                val guiTerminal = GuiTerminal.byId(snap.guiTerminalId)
                // rootfs が未展開だと launch が例外になるので、先に分かりやすく案内する。
                // (未展開 distro をここで勝手にダウンロードはしない。端末タブで起動して導入させる。)
                val launcher = ProotLauncher(context)
                if (!launcher.isDistroReady(distroId)) {
                    fail("「$distroId」がまだ展開されていません。先に端末タブでこの OS を起動してください。")
                    return@launch
                }
                // start [WxH] [clean]: clean フラグが立っていれば 3 番目の引数で渡す。
                val startArgs = mutableListOf("start", "${width}x$height")
                if (clean) startArgs.add("clean")
                val p = launcher.launch(
                    distroId = distroId,
                    command = "/usr/local/bin/z2gui",
                    rows = 24,
                    cols = 80,
                    extraArgs = startArgs,
                    guiTerminal = guiTerminal,
                    display = display,  // z2gui へ Z2_DISPLAY/Z2_RFBPORT として渡す (このタブ専用の :N)
                )
                pty = p
                // z2gui の出力はログへ排出（PTY バッファが詰まってブロックしないように）。
                scope.launch { drainPty(p) }

                _state.value = State.CONNECTING
                _message.value = "GUI を準備中… (初回はパッケージ取得で数分かかることがあります)"
                // Xvnc の起動待ちと接続を 1 本化する。捨て socket でポート疎通だけ
                // 確認すると「接続して即切断するクライアント」と見なされ、TigerVNC が
                // 最初のクライアント切断 (1→0) で server shutdown してしまい、本物の
                // 接続前に Xvnc が落ちる。そこで本物の RFB 接続を、接続拒否 (ポート
                // 未起動) の間だけリトライする。拒否された接続は Xvnc に届かないので
                // 安全で、確立後はそのまま持続接続になり 0 クライアントに落ちない。
                //
                // VNC ポートが開くまで本物の RFB 接続を無期限にリトライする (タイムアウトなし)。
                // 初回のパッケージ導入 (apk/apt/pacman) は Arch/Konsole 等だと数分以上かかるが、
                // 途中で打ち切らず最後まで待つ。z2gui が途中終了 (導入失敗) した場合は
                // connectWithRetry が PTY クローズを検知して即 false を返すので無限ループにはならない。
                // 途中で止めたいときは GUI タブの ✕ (stop) で手動キャンセルできる。
                if (!connectWithRetry()) {
                    fail("GUI の起動に失敗しました (z2gui が終了)。端末タブで z2gui を実行してログを確認してください。")
                    return@launch
                }
                _state.value = State.CONNECTED
                _message.value = "${rfb.width}x${rfb.height}  ${rfb.desktopName}"
                rxJob = scope.launch { rfb.run() }
            } catch (e: Exception) {
                fail("起動失敗: ${e.message}")
            }
        }
    }

    private fun fail(msg: String) {
        Log.w(TAG, msg)
        _message.value = msg
        _state.value = State.ERROR
    }

    /** ServerCutText を Android クリップボードへ。RFB 受信スレッドから呼ばれるのでメインに渡す。 */
    private fun copyToAndroidClipboard(text: String) {
        if (text.isEmpty()) return
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("z2term GUI", text))
            }
        }
    }

    private fun drainPty(p: PtyProcess) {
        val buf = ByteArray(4096)
        try {
            while (true) {
                val n = p.reader.read(buf)
                if (n < 0) break
                val s = String(buf, 0, n).trim()
                if (s.isNotEmpty()) {
                    Log.d(TAG, "z2gui: $s")
                    // 接続が確立する前 (パッケージ取得・Xvnc 起動中) は、z2gui の最新出力を
                    // そのまま画面に出して「今なにをしているか」を見えるようにする (進捗表示)。
                    // apk/apt/pacman の取得・展開ログがここに流れる。
                    if (_state.value == State.STARTING || _state.value == State.CONNECTING) {
                        sanitizeProgressLine(s)?.let { _message.value = it }
                    }
                }
            }
        } catch (_: Exception) {
            // PTY クローズ時に例外 → 無視
        } finally {
            // EOF/例外いずれも z2gui (proot) の終了。これ以上 Xvnc は立たないので
            // 接続待ち (connectWithRetry) を即打ち切らせる。
            ptyClosed = true
        }
    }

    /**
     * PTY 出力チャンクを「画面上部の進捗表示として 1 行で見せられる文字列」へ整形する。
     * apk/apt/pacman の出力には ANSI 色制御・CR で同一行上書き・退避用制御文字が混ざり、
     * Compose の `Text` にそのまま流すと「`[33mFetched`」のような未解釈エスケープが
     * バラバラと並んでバグって見えるため、ここで剥がして人間が読める素の 1 行にする。
     *
     * - ANSI CSI (`ESC [ ... letter`) と OSC (`ESC ] ... BEL` または `ESC \`) を除去。
     * - 各「論理行」を `\r` で区切られた最終セグメントだけ採用 (進捗バーの最新状態)。
     * - 非表示制御文字 (TAB/SPACE 以外の `< 0x20` と DEL) を削除。
     * - `(x/y) Installing pkg` のようなパッケージ進行行があれば最優先で採用。
     * - それ以外は最後の非空行を最大 [MAX_PROGRESS_CHARS] 字まで。
     * 何も拾えなければ null (= 既存表示維持)。
     */
    private fun sanitizeProgressLine(raw: String): String? {
        // 1) ANSI CSI / OSC をまとめて剥がす。OSC は BEL または ESC\\ で終端。
        val noAnsi = ANSI_REGEX.replace(raw, "")
        // 2) 物理改行 `\n` で分割した各論理行について、`\r` で上書きされた末尾のみ残す。
        val logicalLines = noAnsi.split('\n').asSequence()
            .map { line ->
                val lastCr = line.lastIndexOf('\r')
                if (lastCr >= 0) line.substring(lastCr + 1) else line
            }
            // 3) 残った制御文字 (TAB/SPACE 以外) を削除して trim。
            .map { CONTROL_REGEX.replace(it, "").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (logicalLines.isEmpty()) return null
        // 4) パッケージ進行行 (`(1/9) Installing ...` 等) を優先。最後にヒットしたものを採用。
        val pkgProgress = logicalLines.lastOrNull { PKG_PROGRESS_REGEX.containsMatchIn(it) }
        val picked = pkgProgress ?: logicalLines.last()
        return picked.take(MAX_PROGRESS_CHARS)
    }

    /**
     * Xvnc が立ち上がるまで本物の RFB 接続を無期限にリトライする (タイムアウトなし)。
     * ポート未起動による接続拒否 ([ConnectException]) のみ再試行し、それ以外の
     * 失敗 (ハンドシェイク異常等) は呼び出し側へ伝播させて ERROR にする。
     * 捨て socket での疎通確認をしないのは、接続→即切断が TigerVNC の
     * last-client-disconnect 挙動で Xvnc を落としてしまうため。
     *
     * z2gui の終了 (ptyClosed) は即座に検知して false を返すので、Xvnc が二度と
     * 立たない状況でも無限に待ち続けることはない (停止できなくなる事はない)。
     */
    private suspend fun connectWithRetry(): Boolean {
        // z2gui (proot) が終了したら Xvnc はもう立たない → 待たずに失敗扱い。
        while (!ptyClosed) {
            try {
                rfb.connect()
                return true
            } catch (_: ConnectException) {
                delay(300) // ポート未起動 (接続拒否) → 少し待って再試行
            }
        }
        return false
    }

    /** [com.zerotoship.z2term.core.AppSession] 実装。タブクローズ時に呼ばれる。 */
    override fun shutdown() = stop()

    fun stop() {
        scope.launch {
            runCatching { rfb.close() }
            runCatching { rxJob?.cancel() }
            // Xvnc は proot の ptrace 対象。pty.close() は proot に SIGHUP を送るだけで、
            // シグナルで proot が死ぬとカーネルがトレースを外すため --kill-on-exit が
            // 効かず Xvnc が生き残る (5901 リーク)。さらに z2gui は GUI を setsid で
            // 切り離している。確実に止めるため、別 proot で `z2gui stop` を流して
            // Xvnc/WM を明示的に kill してから PTY を閉じる。
            runCatching { runGuiStop() }
            runCatching { pty?.close() }
            pty = null
            _state.value = State.STOPPED
            _message.value = "停止しました"
        }
    }

    /**
     * 別 proot で `z2gui stop` を実行し、最初の proot が立てた Xvnc/openbox/xterm を停止する。
     * `/proc` は proot に実体バインドされ全 proot が同一 Android uid なので、別インスタンスからでも
     * pid を走査して kill できる (GuiScript の stop_x)。EOF まで読んで完了を待つ。
     */
    private fun runGuiStop() {
        val p = ProotLauncher(context).launch(
            distroId = distroId,
            command = "/usr/local/bin/z2gui",
            extraArgs = listOf("stop"),
            display = display,  // このタブの :N だけを停止 (他の GUI タブを巻き込まない)
        )
        val buf = ByteArray(1024)
        try { while (p.reader.read(buf) >= 0) { /* stop_x 完了 (EOF) まで待つ */ } } catch (_: Exception) {}
        runCatching { p.close() }
    }

    companion object {
        private const val TAG = "GuiSession"
        private const val MAX_PROGRESS_CHARS = 160
        // ESC [ ... <letter> (CSI: SGR/カーソル等) と ESC ] ... (BEL|ESC\) (OSC: タイトル等) を剥がす。
        private val ANSI_REGEX = Regex("\\[[0-?]*[ -/]*[@-~]|\\][^]*(|\\\\)")
        // 表示可能でない C0 制御 (0x00-0x1F のうち TAB/SPACE 以外) と DEL を消す。
        private val CONTROL_REGEX = Regex("[ --]")
        // apk/apt/pacman 共通の進行表現。 "(1/9) Installing ..." "[1/9]" "Get:1 ..." 等。
        private val PKG_PROGRESS_REGEX = Regex("""[(\[]\s*\d+\s*/\s*\d+\s*[)\]]|^Get:\d+\s""")
    }
}
