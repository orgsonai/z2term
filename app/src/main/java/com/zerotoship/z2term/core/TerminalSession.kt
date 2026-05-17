package com.zerotoship.z2term.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.zerotoship.z2term.channel.LocalPtyChannel
import com.zerotoship.z2term.channel.ProcessChannel
import com.zerotoship.z2term.channel.SshChannel
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.distro.DistroInstaller
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.emulator.ZtsTheme
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * Terminal セッション本体。
 *
 * UI (ViewModel) と切り離されたライフサイクルで生存する。
 * フォアグラウンドサービスから参照されることで、Activity が破棄されても
 * PTY プロセスとエミュレータ状態を維持できる。
 *
 * M4 からは [SessionManager] が複数の [TerminalSession] を持ち、UI 側で
 * タブとして切替可能。`id` は不変、`label` は表示用のラベル (PTY モードが
 * 確定したタイミングで自動更新するが、ユーザー編集も可)。
 */
class TerminalSession(
    private val appContext: Context,
    val id: String = java.util.UUID.randomUUID().toString(),
    initialLabel: String = "session"
) {

    /** タブ表示名 (RUNNING になったら mode を反映、それ以前は "session") */
    private val _label = MutableStateFlow(initialLabel)
    val label: StateFlow<String> = _label.asStateFlow()
    fun setLabel(s: String) { _label.value = s }

    enum class TerminalState { IDLE, INSTALLING, STARTING, RUNNING, EXITED, ERROR }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val mode: String = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val installer = DistroInstaller(appContext)
    private val launcher = ProotLauncher(appContext)
    private val settings = AppSettings(appContext)

    private val _cwd = MutableStateFlow("")
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    val emulator = TerminalEmulator(
        output = { bytes -> writeToPty(bytes) },
        initialRows = 24,
        initialColumns = 80,
        clipboardWriter = { text ->
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
            _toastEvents.tryEmit("リモートからコピー (${text.length} 文字)")
        },
        titleSetter = { title -> if (title.isNotBlank()) _label.value = title.take(20) },
        cwdSetter = { path -> _cwd.value = path }
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _redrawTick = MutableStateFlow(0)
    val redrawTick: StateFlow<Int> = _redrawTick.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    val settingsFlow: StateFlow<AppSettings.Snapshot> = settings.flow.stateIn(
        scope = scope, started = SharingStarted.Eagerly, initialValue = AppSettings.Snapshot()
    )

    private var channel: ProcessChannel? = null
    private var readJob: Job? = null

    val isRunning: Boolean get() = _uiState.value.state == TerminalState.RUNNING

    init {
        scope.launch {
            settingsFlow.collect { snapshot ->
                val theme = AvailableThemes.firstOrNull { it.name == snapshot.themeName } ?: ZtsTheme
                emulator.colors.applyTheme(theme)
                emulator.buffer.scrollbackCapacity = snapshot.scrollbackLines
                emulator.ambiguousAsWide = snapshot.ambiguousAsWide
                bumpRedraw()
            }
        }
    }

    fun setThemeName(name: String) { scope.launch { settings.setTheme(name) } }
    fun setFontSize(sp: Float) { scope.launch { settings.setFontSize(sp) } }
    fun setScrollbackLines(lines: Int) { scope.launch { settings.setScrollbackLines(lines) } }
    fun setDistro(id: String) { scope.launch { settings.setDistro(id) } }
    fun setFontId(id: String) { scope.launch { settings.setFontId(id) } }
    fun setAmbiguousAsWide(v: Boolean) { scope.launch { settings.setAmbiguousAsWide(v) } }
    fun setInitCommand(value: String) { scope.launch { settings.setInitCommand(value) } }

    /** 設定で選ばれているディストロを使って起動。明示的指定があればそれを優先 */
    fun startTerminal(distroOverride: DistroSpec? = null) {
        if (_uiState.value.state == TerminalState.RUNNING) return

        scope.launch {
            val spec = distroOverride
                ?: DistroSpec.byId(settingsFlow.value.distroId)
                ?: DistroSpec.ALPINE
            try {
                if (!launcher.isProotAvailable()) {
                    writeBanner("⚠ PRoot バイナリが見つかりません。Android sh モードで起動します。")
                    fallbackToAndroidSh()
                    return@launch
                }

                if (!launcher.isDistroReady(spec.id)) {
                    writeBanner("📦 ${spec.displayName} を初回展開しています…")
                    _uiState.update { it.copy(state = TerminalState.INSTALLING) }

                    var installError: Throwable? = null
                    withContext(Dispatchers.IO) {
                        installer.install(spec).collect { progress ->
                            when (progress) {
                                is DistroInstaller.Progress.Started -> writeBanner("   展開開始…")
                                is DistroInstaller.Progress.Extracting -> Unit
                                is DistroInstaller.Progress.Configuring -> writeBanner("   設定中…")
                                is DistroInstaller.Progress.Completed -> writeBanner("✓ ${spec.displayName} 展開完了")
                                is DistroInstaller.Progress.Failed -> installError = progress.error
                            }
                        }
                    }

                    if (installError != null) {
                        writeBanner("✗ ${spec.displayName} 展開失敗: ${installError?.message}")
                        writeBanner("Android sh モードにフォールバックします。")
                        fallbackToAndroidSh()
                        return@launch
                    }
                }

                _uiState.update { it.copy(state = TerminalState.STARTING) }
                writeBanner("🚀 ${spec.displayName} を起動中…")

                val (rows, cols) = currentSize()
                val pty = launcher.launch(spec.id, "/bin/sh", rows, cols)
                val ch = LocalPtyChannel(pty)
                channel = ch
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = spec.id) }
                _label.value = spec.id
                startReadLoop(ch)
                scheduleInitCommand(settingsFlow.value.initCommand)

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
            val pty = launcher.launchAndroidSh(rows, cols)
            val ch = LocalPtyChannel(pty)
            channel = ch
            _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "android-sh") }
            _label.value = "sh"
            startReadLoop(ch)
            scheduleInitCommand(settingsFlow.value.initCommand)
        } catch (e: Throwable) {
            Log.e(TAG, "Even Android sh failed", e)
            writeBanner("致命的エラー: ${e.message}")
            _uiState.update { it.copy(state = TerminalState.ERROR) }
        }
    }

    /** SSH 接続を開始 */
    fun startSsh(profile: SshProfile) {
        if (_uiState.value.state == TerminalState.RUNNING) return
        scope.launch {
            writeBanner("🔌 SSH 接続中: ${profile.user}@${profile.host}:${profile.port}…")
            _uiState.update { it.copy(state = TerminalState.STARTING) }
            try {
                val (rows, cols) = currentSize()
                val ch = withContext(Dispatchers.IO) {
                    SshChannel.connect(profile, rows, cols, appContext)
                }
                channel = ch
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "ssh") }
                _label.value = "ssh:${profile.name.ifEmpty { profile.host }}"
                startReadLoop(ch)
                val cmd = profile.initCommand.ifEmpty { settingsFlow.value.initCommand }
                scheduleInitCommand(cmd)
            } catch (e: Throwable) {
                Log.e(TAG, "SSH connect failed", e)
                writeBanner("✗ SSH 接続失敗: ${e.message}")
                _uiState.update { it.copy(state = TerminalState.ERROR) }
            }
        }
    }

    private fun startReadLoop(ch: ProcessChannel) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (ch.isAlive) {
                    val read = ch.reader.read(buffer)
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
                writeBanner("[プロセス終了 exitCode=${ch.exitCode ?: -1}]")
            }
        }
    }

    fun writeBytes(bytes: ByteArray) = writeToPty(bytes)

    /** RUNNING になった後、シェルプロンプトが出る頃を見計らって init コマンドを送る */
    private fun scheduleInitCommand(command: String) {
        if (command.isBlank()) return
        scope.launch {
            delay(INIT_DELAY_MS)
            writeBytes((command + "\n").toByteArray(Charsets.UTF_8))
        }
    }

    fun onResize(rows: Int, cols: Int) {
        channel?.resize(rows, cols)
        bumpRedraw()
    }

    fun setScrollOffset(offset: Int) { _scrollOffset.value = offset.coerceAtLeast(0) }

    fun scrollBy(delta: Int) {
        val newOffset = (_scrollOffset.value + delta).coerceIn(0, emulator.buffer.scrollbackSize)
        _scrollOffset.value = newOffset
    }

    fun jumpToBottom() { _scrollOffset.value = 0 }

    fun clearOutput() {
        emulator.processBytes(byteArrayOf(
            0x1B, '['.code.toByte(), '2'.code.toByte(), 'J'.code.toByte(),
            0x1B, '['.code.toByte(), '3'.code.toByte(), 'J'.code.toByte(),
            0x1B, '['.code.toByte(), 'H'.code.toByte()
        ))
        bumpRedraw()
    }

    fun restart() {
        channel?.close()
        channel = null
        readJob?.cancel()
        emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startTerminal()
    }

    fun emitToast(message: String) { _toastEvents.tryEmit(message) }

    /** セッションを終了 (PTY を閉じてジョブをキャンセル) */
    fun shutdown() {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.cancel()
    }

    private fun writeToPty(bytes: ByteArray) {
        val pty = channel ?: return
        scope.launch(Dispatchers.IO) {
            try {
                pty.writer.write(bytes)
                pty.writer.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Write failed: ${e.message}")
            }
        }
    }

    private fun writeBanner(text: String) {
        emulator.processBytes(("$text\r\n").toByteArray(Charsets.UTF_8))
        bumpRedraw()
    }

    private fun bumpRedraw() { _redrawTick.update { it + 1 } }

    private fun currentSize(): Pair<Int, Int> = emulator.buffer.rows to emulator.buffer.columns

    companion object {
        private const val TAG = "TerminalSession"
        /** init コマンド送出までの待機 (シェルプロンプト表示待ち) */
        private const val INIT_DELAY_MS = 400L
    }
}
