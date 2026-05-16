package com.zerotoship.z2term.ui.terminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotoship.z2term.distro.DistroInstaller
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.pty.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Terminal 画面のステート管理。
 *
 * M1 段階のシンプルな実装:
 * - 起動時に Alpine のセットアップを試行（assets にあれば）
 * - 失敗時/未配置時は Android /system/bin/sh フォールバック
 * - 出力は単純な append-only 文字列バッファ
 *   （VT100 完全対応は M2 で）
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    enum class TerminalState {
        IDLE,           // 初期状態
        INSTALLING,     // ディストロ展開中
        STARTING,       // PTY 起動中
        RUNNING,        // 実行中
        EXITED,         // 終了
        ERROR           // エラー
    }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val output: String = "",
        val statusMessage: String = "",
        val mode: String = "" // "alpine" / "android-sh"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val installer = DistroInstaller(application)
    private val launcher = ProotLauncher(application)

    private var ptyProcess: PtyProcess? = null
    private var readJob: Job? = null

    /**
     * ターミナルを起動する。
     *
     * 起動シーケンス:
     * 1. proot が利用可能かチェック
     * 2. Alpine が展開済みかチェック → なければ assets から展開
     * 3. proot 経由で /bin/sh を起動
     * 4. どこかで失敗したら Android /system/bin/sh にフォールバック
     */
    fun startTerminal() {
        if (_uiState.value.state == TerminalState.RUNNING) {
            Log.w(TAG, "Already running")
            return
        }

        viewModelScope.launch {
            try {
                // === ステップ 1: proot 利用可能チェック ===
                if (!launcher.isProotAvailable()) {
                    appendStatus("⚠ PRoot バイナリが見つかりません。Android sh モードで起動します。")
                    fallbackToAndroidSh()
                    return@launch
                }

                // === ステップ 2: Alpine 準備 ===
                if (!launcher.isDistroReady("alpine")) {
                    appendStatus("📦 Alpine Linux を初回展開しています…")
                    _uiState.update { it.copy(state = TerminalState.INSTALLING) }

                    var installError: Throwable? = null
                    withContext(Dispatchers.IO) {
                        installer.installAlpine().collect { progress ->
                            when (progress) {
                                is DistroInstaller.Progress.Started -> {
                                    appendStatus("   展開開始…")
                                }
                                is DistroInstaller.Progress.Extracting -> {
                                    // M1 では出力しすぎないように間引く
                                }
                                is DistroInstaller.Progress.Configuring -> {
                                    appendStatus("   設定中…")
                                }
                                is DistroInstaller.Progress.Completed -> {
                                    appendStatus("✓ Alpine 展開完了")
                                }
                                is DistroInstaller.Progress.Failed -> {
                                    installError = progress.error
                                }
                            }
                        }
                    }

                    if (installError != null) {
                        appendStatus("✗ Alpine 展開失敗: ${installError?.message}")
                        appendStatus("Android sh モードにフォールバックします。")
                        fallbackToAndroidSh()
                        return@launch
                    }
                }

                // === ステップ 3: PRoot で Alpine 起動 ===
                _uiState.update { it.copy(state = TerminalState.STARTING) }
                appendStatus("🚀 Alpine Linux を起動中…\n")

                val pty = launcher.launch(
                    distroId = "alpine",
                    command = "/bin/sh",
                    rows = 24,
                    cols = 80
                )
                ptyProcess = pty
                _uiState.update {
                    it.copy(
                        state = TerminalState.RUNNING,
                        mode = "alpine"
                    )
                }
                startReadLoop(pty)

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start terminal", e)
                appendStatus("✗ 起動失敗: ${e.message}\n")
                appendStatus("Android sh モードにフォールバックします。\n")
                fallbackToAndroidSh()
            }
        }
    }

    private fun fallbackToAndroidSh() {
        try {
            val pty = launcher.launchAndroidSh(rows = 24, cols = 80)
            ptyProcess = pty
            _uiState.update {
                it.copy(
                    state = TerminalState.RUNNING,
                    mode = "android-sh"
                )
            }
            startReadLoop(pty)
        } catch (e: Throwable) {
            Log.e(TAG, "Even Android sh failed", e)
            _uiState.update {
                it.copy(
                    state = TerminalState.ERROR,
                    statusMessage = "致命的エラー: ${e.message}"
                )
            }
        }
    }

    /**
     * PTY からの出力をバックグラウンドで読み続けるループ。
     */
    private fun startReadLoop(pty: PtyProcess) {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (pty.isAlive) {
                    val read = pty.reader.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        _uiState.update { current ->
                            current.copy(output = current.output + text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Read loop ended: ${e.message}")
            } finally {
                _uiState.update {
                    it.copy(state = TerminalState.EXITED)
                }
                appendStatus("\n[プロセス終了 exitCode=${pty.exitCode ?: -1}]\n")
            }
        }
    }

    /**
     * ユーザー入力を PTY に書き込む。
     */
    fun sendInput(text: String) {
        val pty = ptyProcess ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pty.writer.write(text.toByteArray(Charsets.UTF_8))
                pty.writer.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Write failed: ${e.message}")
            }
        }
    }

    /**
     * 特殊キーを送信。
     */
    fun sendSpecialKey(key: SpecialKey) {
        val bytes = when (key) {
            SpecialKey.ENTER -> byteArrayOf(0x0d)
            SpecialKey.TAB -> byteArrayOf(0x09)
            SpecialKey.ESC -> byteArrayOf(0x1b)
            SpecialKey.BACKSPACE -> byteArrayOf(0x7f)
            SpecialKey.UP -> byteArrayOf(0x1b, 0x5b, 0x41)
            SpecialKey.DOWN -> byteArrayOf(0x1b, 0x5b, 0x42)
            SpecialKey.RIGHT -> byteArrayOf(0x1b, 0x5b, 0x43)
            SpecialKey.LEFT -> byteArrayOf(0x1b, 0x5b, 0x44)
            SpecialKey.CTRL_C -> byteArrayOf(0x03)
            SpecialKey.CTRL_D -> byteArrayOf(0x04)
            SpecialKey.CTRL_L -> byteArrayOf(0x0c)
            SpecialKey.CTRL_Z -> byteArrayOf(0x1a)
        }
        val pty = ptyProcess ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pty.writer.write(bytes)
                pty.writer.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Write failed: ${e.message}")
            }
        }
    }

    /** 表示出力をクリア */
    fun clearOutput() {
        _uiState.update { it.copy(output = "") }
    }

    /** ターミナルを再起動 */
    fun restart() {
        ptyProcess?.close()
        ptyProcess = null
        readJob?.cancel()
        _uiState.update { UiState() }
        startTerminal()
    }

    /** 端末サイズ変更を PTY に通知 */
    fun resize(rows: Int, cols: Int) {
        ptyProcess?.resize(rows, cols)
    }

    private fun appendStatus(text: String) {
        _uiState.update { it.copy(output = it.output + text + "\n") }
    }

    override fun onCleared() {
        super.onCleared()
        ptyProcess?.close()
        readJob?.cancel()
    }

    enum class SpecialKey {
        ENTER, TAB, ESC, BACKSPACE,
        UP, DOWN, LEFT, RIGHT,
        CTRL_C, CTRL_D, CTRL_L, CTRL_Z
    }

    companion object {
        private const val TAG = "TerminalViewModel"
    }
}
