package com.zerotoship.z2term.gui

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.Z2TERM_VNC_PORT
import com.zerotoship.z2term.pty.PtyProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

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
class GuiSession(private val context: Context) {

    enum class State { IDLE, STARTING, CONNECTING, CONNECTED, ERROR, STOPPED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    val rfb = RfbClient(port = Z2TERM_VNC_PORT)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pty: PtyProcess? = null
    private var rxJob: Job? = null

    fun start(width: Int, height: Int) {
        when (_state.value) {
            State.STARTING, State.CONNECTING, State.CONNECTED -> return
            else -> {}
        }
        _state.value = State.STARTING
        _message.value = "GUI を起動中… (${width}x$height)"
        scope.launch {
            try {
                val p = ProotLauncher(context).launch(
                    distroId = "alpine",
                    command = "/usr/local/bin/z2gui",
                    rows = 24,
                    cols = 80,
                    extraArgs = listOf("start", "${width}x$height"),
                )
                pty = p
                // z2gui の出力はログへ排出（PTY バッファが詰まってブロックしないように）。
                scope.launch { drainPty(p) }

                _message.value = "Xvnc を待機中…"
                if (!waitForPort(Z2TERM_VNC_PORT, timeoutMs = 60_000)) {
                    fail("Xvnc がポート $Z2TERM_VNC_PORT で待ち受けません (タイムアウト)")
                    return@launch
                }

                _state.value = State.CONNECTING
                _message.value = "VNC 接続中…"
                rfb.connect()
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

    private suspend fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
                return true
            } catch (_: Exception) {
                delay(300)
            }
        }
        return false
    }

    fun stop() {
        scope.launch {
            runCatching { rfb.close() }
            runCatching { rxJob?.cancel() }
            // PtyProcess を閉じると proot が終了 → --kill-on-exit で Xvnc/openbox も停止する。
            runCatching { pty?.close() }
            pty = null
            _state.value = State.STOPPED
            _message.value = "停止しました"
        }
    }

    companion object {
        private const val TAG = "GuiSession"
    }
}
