package com.zerotoship.z2term.gui

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.Z2TERM_VNC_PORT
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
    override val id: String = java.util.UUID.randomUUID().toString()
) : com.zerotoship.z2term.core.AppSession {

    enum class State { IDLE, STARTING, CONNECTING, CONNECTED, ERROR, STOPPED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    /** タブ表示名 (常に "GUI")。 */
    private val _label = MutableStateFlow("GUI")
    override val label: StateFlow<String> = _label.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    val rfb = RfbClient(port = Z2TERM_VNC_PORT)

    /** ズーム/パンの表示変換。GuiScreen(描画) と GuiInputView(入力) で共有。タブ切替・回転でも保持。 */
    val viewport = GuiViewport()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pty: PtyProcess? = null
    private var rxJob: Job? = null

    /** 起動した distro。停止 (runGuiStop) でも同じ distro を使うため start で確定させる。 */
    private var distroId: String = "alpine"

    fun start(width: Int, height: Int) {
        when (_state.value) {
            State.STARTING, State.CONNECTING, State.CONNECTED -> return
            else -> {}
        }
        _state.value = State.STARTING
        _message.value = "GUI を起動中… (${width}x$height)"
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
                val p = launcher.launch(
                    distroId = distroId,
                    command = "/usr/local/bin/z2gui",
                    rows = 24,
                    cols = 80,
                    extraArgs = listOf("start", "${width}x$height"),
                    guiTerminal = guiTerminal,
                )
                pty = p
                // z2gui の出力はログへ排出（PTY バッファが詰まってブロックしないように）。
                scope.launch { drainPty(p) }

                _state.value = State.CONNECTING
                _message.value = "Xvnc を待機中…"
                // Xvnc の起動待ちと接続を 1 本化する。捨て socket でポート疎通だけ
                // 確認すると「接続して即切断するクライアント」と見なされ、TigerVNC が
                // 最初のクライアント切断 (1→0) で server shutdown してしまい、本物の
                // 接続前に Xvnc が落ちる。そこで本物の RFB 接続を、接続拒否 (ポート
                // 未起動) の間だけリトライする。拒否された接続は Xvnc に届かないので
                // 安全で、確立後はそのまま持続接続になり 0 クライアントに落ちない。
                if (!connectWithRetry(timeoutMs = 60_000)) {
                    fail("Xvnc に接続できません (タイムアウト)")
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

    private fun drainPty(p: PtyProcess) {
        val buf = ByteArray(4096)
        try {
            while (true) {
                val n = p.reader.read(buf)
                if (n < 0) break
                val s = String(buf, 0, n).trim()
                if (s.isNotEmpty()) Log.d(TAG, "z2gui: $s")
            }
        } catch (_: Exception) {
            // PTY クローズ時に例外 → 無視
        }
    }

    /**
     * Xvnc が立ち上がるまで本物の RFB 接続をリトライする。
     * ポート未起動による接続拒否 ([ConnectException]) のみ再試行し、それ以外の
     * 失敗 (ハンドシェイク異常等) は呼び出し側へ伝播させて ERROR にする。
     * 捨て socket での疎通確認をしないのは、接続→即切断が TigerVNC の
     * last-client-disconnect 挙動で Xvnc を落としてしまうため。
     */
    private suspend fun connectWithRetry(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
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
        )
        val buf = ByteArray(1024)
        try { while (p.reader.read(buf) >= 0) { /* stop_x 完了 (EOF) まで待つ */ } } catch (_: Exception) {}
        runCatching { p.close() }
    }

    companion object {
        private const val TAG = "GuiSession"
    }
}
