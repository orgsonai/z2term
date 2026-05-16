package com.zerotoship.z2term.ui.terminal

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotoship.z2term.distro.DistroInstaller
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.emulator.ZtsTheme
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Terminal 画面のステート管理 (M2)。
 *
 * - TerminalEmulator がバッファ・カーソル・属性を保持
 * - PTY 出力は emulator.processBytes() で即座に反映
 * - UI には `redrawTick` を増分して recomposition を促す
 * - 状態文字列 (起動中など) は emulator に直接 "[setup] ..." として流し込む
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    enum class TerminalState {
        IDLE, INSTALLING, STARTING, RUNNING, EXITED, ERROR
    }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val mode: String = ""  // "alpine" / "android-sh"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _redrawTick = MutableStateFlow(0)
    val redrawTick: StateFlow<Int> = _redrawTick.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    /** 短期通知 (Toast) 用イベント */
    private val _toastEvents = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 4
    )
    val toastEvents = _toastEvents.asSharedFlow()

    private val installer = DistroInstaller(application)
    private val launcher = ProotLauncher(application)
    private val settings = AppSettings(application)

    val settingsFlow: StateFlow<AppSettings.Snapshot> = settings.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings.Snapshot()
    )

    private val emulator = TerminalEmulator(
        output = { bytes -> writeToPty(bytes) },
        initialRows = 24,
        initialColumns = 80
    )
    val emulatorRef: TerminalEmulator get() = emulator

    private var ptyProcess: PtyProcess? = null
    private var readJob: Job? = null

    init {
        // 設定変更を監視: テーマ + スクロールバック容量をエミュレータに反映
        viewModelScope.launch {
            settingsFlow.collect { snapshot ->
                val theme = AvailableThemes.firstOrNull { it.name == snapshot.themeName } ?: ZtsTheme
                emulator.colors.applyTheme(theme)
                emulator.buffer.scrollbackCapacity = snapshot.scrollbackLines
                bumpRedraw()
            }
        }
    }

    fun updateTheme(name: String) {
        viewModelScope.launch { settings.setTheme(name) }
    }

    fun updateFontSize(sp: Float) {
        viewModelScope.launch { settings.setFontSize(sp) }
    }

    fun updateScrollbackLines(lines: Int) {
        viewModelScope.launch { settings.setScrollbackLines(lines) }
    }

    fun startTerminal() {
        if (_uiState.value.state == TerminalState.RUNNING) {
            Log.w(TAG, "Already running")
            return
        }

        viewModelScope.launch {
            try {
                if (!launcher.isProotAvailable()) {
                    writeBanner("⚠ PRoot バイナリが見つかりません。Android sh モードで起動します。")
                    fallbackToAndroidSh()
                    return@launch
                }

                if (!launcher.isDistroReady("alpine")) {
                    writeBanner("📦 Alpine Linux を初回展開しています…")
                    _uiState.update { it.copy(state = TerminalState.INSTALLING) }

                    var installError: Throwable? = null
                    withContext(Dispatchers.IO) {
                        installer.installAlpine().collect { progress ->
                            when (progress) {
                                is DistroInstaller.Progress.Started -> writeBanner("   展開開始…")
                                is DistroInstaller.Progress.Extracting -> Unit
                                is DistroInstaller.Progress.Configuring -> writeBanner("   設定中…")
                                is DistroInstaller.Progress.Completed -> writeBanner("✓ Alpine 展開完了")
                                is DistroInstaller.Progress.Failed -> installError = progress.error
                            }
                        }
                    }

                    if (installError != null) {
                        writeBanner("✗ Alpine 展開失敗: ${installError?.message}")
                        writeBanner("Android sh モードにフォールバックします。")
                        fallbackToAndroidSh()
                        return@launch
                    }
                }

                _uiState.update { it.copy(state = TerminalState.STARTING) }
                writeBanner("🚀 Alpine Linux を起動中…")

                val (rows, cols) = currentSize()
                val pty = launcher.launch(
                    distroId = "alpine",
                    command = "/bin/sh",
                    rows = rows,
                    cols = cols
                )
                ptyProcess = pty
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "alpine") }
                startReadLoop(pty)

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start terminal", e)
                writeBanner("✗ 起動失敗: ${e.message}")
                writeBanner("Android sh モードにフォールバックします。")
                fallbackToAndroidSh()
            }
        }
    }

    private fun fallbackToAndroidSh() {
        try {
            val (rows, cols) = currentSize()
            val pty = launcher.launchAndroidSh(rows = rows, cols = cols)
            ptyProcess = pty
            _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "android-sh") }
            startReadLoop(pty)
        } catch (e: Throwable) {
            Log.e(TAG, "Even Android sh failed", e)
            writeBanner("致命的エラー: ${e.message}")
            _uiState.update { it.copy(state = TerminalState.ERROR) }
        }
    }

    private fun startReadLoop(pty: PtyProcess) {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (pty.isAlive) {
                    val read = pty.reader.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        emulator.processBytes(buffer, read)
                        bumpRedraw()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Read loop ended: ${e.message}")
            } finally {
                _uiState.update { it.copy(state = TerminalState.EXITED) }
                writeBanner("[プロセス終了 exitCode=${pty.exitCode ?: -1}]")
            }
        }
    }

    /** ユーザー入力テキストを PTY へ送信 */
    fun sendInput(text: String) {
        writeToPty(text.toByteArray(Charsets.UTF_8))
    }

    /** 特殊キーを送信 */
    fun sendSpecialKey(key: SpecialKey) {
        val bytes = when (key) {
            SpecialKey.ENTER -> byteArrayOf(0x0d)
            SpecialKey.TAB -> byteArrayOf(0x09)
            SpecialKey.ESC -> byteArrayOf(0x1b)
            SpecialKey.BACKSPACE -> byteArrayOf(0x7f)
            SpecialKey.UP -> emulator.cursorKeyBytes(TerminalEmulator.CursorKey.UP)
            SpecialKey.DOWN -> emulator.cursorKeyBytes(TerminalEmulator.CursorKey.DOWN)
            SpecialKey.RIGHT -> emulator.cursorKeyBytes(TerminalEmulator.CursorKey.RIGHT)
            SpecialKey.LEFT -> emulator.cursorKeyBytes(TerminalEmulator.CursorKey.LEFT)
            SpecialKey.CTRL_A -> byteArrayOf(0x01)
            SpecialKey.CTRL_C -> byteArrayOf(0x03)
            SpecialKey.CTRL_D -> byteArrayOf(0x04)
            SpecialKey.CTRL_E -> byteArrayOf(0x05)
            SpecialKey.CTRL_K -> byteArrayOf(0x0b)
            SpecialKey.CTRL_L -> byteArrayOf(0x0c)
            SpecialKey.CTRL_R -> byteArrayOf(0x12)
            SpecialKey.CTRL_U -> byteArrayOf(0x15)
            SpecialKey.CTRL_W -> byteArrayOf(0x17)
            SpecialKey.CTRL_Z -> byteArrayOf(0x1a)
            SpecialKey.HOME -> ESC_BRACKET + 'H'.code.toByte()
            SpecialKey.END -> ESC_BRACKET + 'F'.code.toByte()
            SpecialKey.PAGE_UP -> ESC_BRACKET + "5~".toByteArray()
            SpecialKey.PAGE_DOWN -> ESC_BRACKET + "6~".toByteArray()
            SpecialKey.F1 -> ESC_O + 'P'.code.toByte()
            SpecialKey.F2 -> ESC_O + 'Q'.code.toByte()
            SpecialKey.F3 -> ESC_O + 'R'.code.toByte()
            SpecialKey.F4 -> ESC_O + 'S'.code.toByte()
            SpecialKey.F5 -> ESC_BRACKET + "15~".toByteArray()
            SpecialKey.F6 -> ESC_BRACKET + "17~".toByteArray()
            SpecialKey.F7 -> ESC_BRACKET + "18~".toByteArray()
            SpecialKey.F8 -> ESC_BRACKET + "19~".toByteArray()
            SpecialKey.F9 -> ESC_BRACKET + "20~".toByteArray()
            SpecialKey.F10 -> ESC_BRACKET + "21~".toByteArray()
            SpecialKey.F11 -> ESC_BRACKET + "23~".toByteArray()
            SpecialKey.F12 -> ESC_BRACKET + "24~".toByteArray()
        }
        writeToPty(bytes)
    }

    /** クリア (内部バッファ含めて) */
    fun clearOutput() {
        // [2J[H = 画面クリア + カーソルホーム
        emulator.processBytes("[2J[3J[H".toByteArray())
        bumpRedraw()
    }

    fun restart() {
        ptyProcess?.close()
        ptyProcess = null
        readJob?.cancel()
        emulator.processBytes("c".toByteArray()) // RIS: full reset
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startTerminal()
    }

    /** Renderer から呼ばれる: 端末サイズ変更 */
    fun onTerminalResize(rows: Int, cols: Int) {
        ptyProcess?.resize(rows, cols)
        bumpRedraw()
    }

    /** スクロールバック表示位置を変更 */
    fun setScrollOffset(offset: Int) {
        _scrollOffset.value = offset.coerceAtLeast(0)
    }

    fun scrollBy(delta: Int) {
        val newOffset = (_scrollOffset.value + delta).coerceIn(0, emulator.buffer.scrollbackSize)
        _scrollOffset.value = newOffset
    }

    fun jumpToBottom() {
        _scrollOffset.value = 0
    }

    /**
     * バッファ全文 (スクロールバック + スクリーン) をクリップボードにコピー。
     * 結果メッセージは ToastEvent としてフローに流す。
     */
    fun copyAllToClipboard() {
        val text = emulator.buffer.getAllText(includeScrollback = true).trimEnd()
        if (text.isEmpty()) {
            _toastEvents.tryEmit("コピーするテキストがありません")
            return
        }
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
        _toastEvents.tryEmit("${text.length} 文字をコピーしました")
    }

    /** クリップボードのテキストを PTY にペースト */
    fun pasteFromClipboard() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: run {
            _toastEvents.tryEmit("クリップボードが空です")
            return
        }
        if (clip.itemCount == 0) {
            _toastEvents.tryEmit("クリップボードが空です")
            return
        }
        val text = clip.getItemAt(0).coerceToText(getApplication()).toString()
        if (text.isEmpty()) {
            _toastEvents.tryEmit("クリップボードが空です")
            return
        }
        writeToPty(text.toByteArray(Charsets.UTF_8))
    }

    private fun writeToPty(bytes: ByteArray) {
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

    /** バナー (アプリ起動経過) をエミュレータに流す */
    private fun writeBanner(text: String) {
        emulator.processBytes(("$text\r\n").toByteArray(Charsets.UTF_8))
        bumpRedraw()
    }

    private fun bumpRedraw() {
        _redrawTick.update { it + 1 }
    }

    private fun currentSize(): Pair<Int, Int> = emulator.buffer.rows to emulator.buffer.columns

    override fun onCleared() {
        super.onCleared()
        ptyProcess?.close()
        readJob?.cancel()
    }

    enum class SpecialKey {
        ENTER, TAB, ESC, BACKSPACE,
        UP, DOWN, LEFT, RIGHT,
        CTRL_A, CTRL_C, CTRL_D, CTRL_E, CTRL_K, CTRL_L, CTRL_R, CTRL_U, CTRL_W, CTRL_Z,
        HOME, END, PAGE_UP, PAGE_DOWN,
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12
    }

    companion object {
        private const val TAG = "TerminalViewModel"
        private val ESC_BRACKET = byteArrayOf(0x1B, '['.code.toByte())
        private val ESC_O = byteArrayOf(0x1B, 'O'.code.toByte())
    }
}
