package com.zerotoship.z2term.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.zerotoship.z2term.channel.LocalPtyChannel
import com.zerotoship.z2term.channel.ProcessChannel
import com.zerotoship.z2term.channel.SshChannel
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.distro.DistroDownloader
import com.zerotoship.z2term.distro.DistroInstaller
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.emulator.resolveTheme
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    override val id: String = java.util.UUID.randomUUID().toString(),
    initialLabel: String = "session"
) : AppSession {

    /** タブ表示名 (RUNNING になったら mode を反映、それ以前は "session") */
    private val _label = MutableStateFlow(initialLabel)
    override val label: StateFlow<String> = _label.asStateFlow()
    fun setLabel(s: String) { _label.value = s }

    enum class TerminalState { IDLE, INSTALLING, STARTING, RUNNING, EXITED, ERROR }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val mode: String = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // PTY 読込 + emulator 状態更新を 1 本のシリアル executor 上で実行する。
    // Compose 側は emulator buffer を Main で読むため、書き手側の競合を 1 スレッドに
    // 寄せつつ、StateFlow 通知経由でメモリ可視性を確保する。
    private val emulatorExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "z2term-emu-${id.take(8)}").apply { isDaemon = true }
    }
    private val emulatorDispatcher = emulatorExecutor.asCoroutineDispatcher()

    private val installer = DistroInstaller(appContext)
    private val downloader = DistroDownloader(appContext)
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

    private val _cellMetrics = MutableStateFlow(CellMetrics())
    val cellMetrics: StateFlow<CellMetrics> = _cellMetrics.asStateFlow()

    private val _selection = MutableStateFlow<TerminalSelection?>(null)
    val selection: StateFlow<TerminalSelection?> = _selection.asStateFlow()

    fun updateCellMetrics(metrics: CellMetrics) { _cellMetrics.value = metrics }
    fun setSelection(s: TerminalSelection?) { _selection.value = s }
    fun clearSelection() { _selection.value = null }

    /** 現在の選択範囲をクリップボードへコピーし、Toast を発火する。 */
    fun copySelectionToClipboard() {
        val sel = _selection.value ?: return
        val text = emulator.buffer.getRangeText(
            sel.startAbsRow, sel.startCol, sel.endAbsRow, sel.endCol
        )
        if (text.isEmpty()) {
            _toastEvents.tryEmit("選択範囲が空です")
            return
        }
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
        _toastEvents.tryEmit("コピー (${text.length} 文字)")
    }

    /** クリップボードのテキストをペースト (PTY へ送出)。 */
    fun pasteFromClipboard() {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString() ?: return
        if (text.isEmpty()) return
        writeBytes(text.replace('\n', '\r').toByteArray(Charsets.UTF_8))
    }

    private val _toastEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    val settingsFlow: StateFlow<AppSettings.Snapshot> = settings.flow.stateIn(
        scope = scope, started = SharingStarted.Eagerly, initialValue = AppSettings.Snapshot()
    )

    private var channel: ProcessChannel? = null
    private var readJob: Job? = null

    val isRunning: Boolean get() = _uiState.value.state == TerminalState.RUNNING

    init {
        CustomThemeStore.ensureLoaded(appContext)
        scope.launch {
            // 設定 (themeName 等) と ユーザー独自テーマを併せて監視。独自テーマ編集でも
            // 選択中なら即エミュレータへ反映される。
            combine(settingsFlow, CustomThemeStore.theme) { snapshot, custom ->
                snapshot to custom
            }.collect { (snapshot, custom) ->
                val theme = resolveTheme(snapshot.themeName, custom)
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
    fun setKeyboardStyleId(id: String) { scope.launch { settings.setKeyboardStyleId(id) } }
    fun setLoginShell(shell: String) { scope.launch { settings.setLoginShell(shell) } }
    fun setKeyboardMode(mode: String) { scope.launch { settings.setKeyboardMode(mode) } }
    fun setKeepAliveService(enabled: Boolean) { scope.launch { settings.setKeepAliveService(enabled) } }
    fun setConfirmBeforeDownload(enabled: Boolean) { scope.launch { settings.setConfirmBeforeDownload(enabled) } }
    fun setGuiTerminal(id: String) { scope.launch { settings.setGuiTerminal(id) } }

    /** 設定で選ばれているディストロを使って起動。明示的指定があればそれを優先 */
    fun startTerminal(distroOverride: DistroSpec? = null) {
        // IDLE 以外なら起動済み/起動中なので何もしない。
        // SSH と PTY の二重起動レースを防ぐため、STARTING 含めて弾く。
        if (_uiState.value.state != TerminalState.IDLE) return
        _uiState.update { it.copy(state = TerminalState.STARTING) }

        scope.launch {
            val spec = distroOverride
                ?: DistroSpec.byId(settingsFlow.value.distroId)
                ?: DistroSpec.ALPINE
            try {
                if (!launcher.isProotAvailable()) {
                    // M7 同梱方針: PRoot は APK の jniLibs/<abi>/libproot.so に同梱されている
                    // 前提なので、未配置はビルド事故。ユーザーに分かるよう警告を出してから
                    // android-sh に退避する (起動不能で真っ黒画面より UX 良)。
                    Log.w(TAG, "PRoot binary not present; falling back to android-sh")
                    writeBanner("⚠️ PRoot バイナリが見つかりません — scripts/build-proot.sh を実行して再ビルドしてください。Android sh で起動します。")
                    fallbackToAndroidSh()
                    return@launch
                }

                if (!launcher.isDistroReady(spec.id)) {
                    // 初回 / バージョン更新どちらの経路でも同じバナーで案内
                    val rootfsExists = java.io.File(appContext.filesDir, "distros/${spec.id}").exists()
                    val banner = if (rootfsExists)
                        "📦 ${spec.displayName} を更新展開しています…"
                    else
                        "📦 ${spec.displayName} を初回展開しています…"
                    writeBanner(banner)
                    _uiState.update { it.copy(state = TerminalState.INSTALLING) }

                    // 非同梱 distro (Ubuntu/Arch/Kali) で rootfs アーカイブが未取得なら
                    // まずダウンロードする。同梱 (Alpine) は assets から直接展開される。
                    if (!spec.bundled && downloader.resolveLocalArchive(spec, detectAbiId()) == null) {
                        val dlError = downloadDistroArchive(spec)
                        if (dlError != null) {
                            writeBanner("✗ ${spec.displayName} のダウンロード失敗: ${dlError.message}")
                            writeBanner("ネットワークと URL を確認してください。Alpine に戻すには設定からディストロを切替えてください。")
                            _uiState.update { it.copy(state = TerminalState.ERROR) }
                            return@launch
                        }
                    }

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
                val shell = settingsFlow.value.loginShell.ifBlank { spec.defaultShell }
                // launcher 側で、指定シェルが rootfs に無ければ spec.defaultShell → /bin/sh に
                // フォールバックする (Ubuntu base に zsh が無い、等のケース)。
                val pty = launcher.launch(spec.id, shell, rows, cols, spec.defaultShell)
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

    /** SUPPORTED_ABIS の先頭。DistroDownloader/Installer と同じ判定。 */
    private fun detectAbiId(): String =
        android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    /**
     * 非同梱 distro の rootfs アーカイブをダウンロードする。
     * 進捗はバナーに % で出す。成功なら null、失敗なら例外を返す。
     */
    private suspend fun downloadDistroArchive(spec: DistroSpec): Throwable? {
        val abi = detectAbiId()
        val sizeHint = spec.approxDownload?.let { " ($it)" } ?: ""
        writeBanner("⬇️ ${spec.displayName} をダウンロード中$sizeHint…")
        var error: Throwable? = null
        var lastPct = -1
        withContext(Dispatchers.IO) {
            downloader.download(spec, abi).collect { p ->
                when (p) {
                    is DistroDownloader.Progress.Downloading -> {
                        if (p.total > 0) {
                            val pct = (p.received * 100 / p.total).toInt()
                            if (pct >= lastPct + 10) {  // 10% 刻みでバナー更新
                                lastPct = pct
                                writeBanner("   $pct% (${p.received / 1024 / 1024}MB)")
                            }
                        }
                    }
                    is DistroDownloader.Progress.Verifying -> writeBanner("   検証中…")
                    is DistroDownloader.Progress.Completed -> writeBanner("✓ ダウンロード完了")
                    is DistroDownloader.Progress.Failed -> error = p.error
                    else -> Unit
                }
            }
        }
        return error
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
        if (_uiState.value.state != TerminalState.IDLE) return
        _uiState.update { it.copy(state = TerminalState.STARTING) }
        scope.launch {
            writeBanner("🔌 SSH 接続中: ${profile.user}@${profile.host}:${profile.port}…")
            try {
                val (rows, cols) = currentSize()
                val ch = withContext(Dispatchers.IO) {
                    SshChannel.connect(profile, rows, cols, appContext)
                }
                channel = ch
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "ssh") }
                _label.value = "ssh:${profile.name.ifEmpty { profile.host }}"
                // ポート転送が定義されていれば結果をバナーに出す (UX)
                if (ch.forwardSummary.isNotEmpty()) {
                    ch.forwardSummary.forEach { line ->
                        writeBanner("🔀 forward $line")
                    }
                }
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

    /**
     * 現在のセッション (ローカルシェル等) を閉じて SSH 接続へ切り替える。
     *
     * [startSsh] は IDLE でないと早期 return するため、稼働中タブからでも SSH できるよう
     * [restart] と同じ要領で一旦リセット (画面クリア + state=IDLE) してから接続する。
     * 既存のローカルシェルは終了する (別タブを残したい場合は新規タブで接続すること)。
     */
    fun connectSsh(profile: SshProfile) {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        }
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startSsh(profile)
    }

    private fun startReadLoop(ch: ProcessChannel) {
        readJob?.cancel()
        // PTY blocking read は IO で行い、emulator 処理は専用シリアルスレッドに hand off。
        // これで clearOutput / restart など他経路の emulator 操作も同じスレッド上で
        // 直列化でき、UI スレッドとのレースを完全に排除できる。
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            try {
                while (ch.isAlive) {
                    val read = ch.reader.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        // ユーザーが手動スクロール中 (scrollOffset > 0) は、新規行が
                        // 追加されても視点を固定する。emulator が scrollback に
                        // 押し出した行数だけ scrollOffset を増やして相殺。
                        // scrollOffset = 0 (張り付き mode) ではそのまま最新が下端。
                        val deltaScrollback = withContext(emulatorDispatcher) {
                            val before = emulator.buffer.scrollbackSize
                            emulator.processBytes(chunk, chunk.size)
                            emulator.buffer.scrollbackSize - before
                        }
                        if (deltaScrollback > 0) {
                            val current = _scrollOffset.value
                            if (current > 0) {
                                _scrollOffset.value =
                                    (current + deltaScrollback)
                                        .coerceAtMost(emulator.buffer.scrollbackSize)
                            }
                        }
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

    fun writeBytes(bytes: ByteArray) {
        // ユーザー入力時は必ず最下行へジャンプ。
        // スクロールバック中に typing 結果が見えなくなる事故を防ぐ。
        _scrollOffset.value = 0
        // 診断ログ: 実際に PTY へ送るバイト列を hex で残す。
        // `adb logcat -s TerminalSession` で確認可能。
        if (Log.isLoggable(TAG, Log.DEBUG) || true) {
            Log.d(TAG, "writeBytes (${bytes.size}B): ${bytes.joinToString(" ") { "%02X".format(it) }}")
        }
        writeToPty(bytes)
    }

    /** RUNNING になった後、シェルプロンプトが出る頃を見計らって init コマンドを送る */
    private fun scheduleInitCommand(command: String) {
        if (command.isBlank()) return
        scope.launch {
            delay(INIT_DELAY_MS)
            writeBytes((command + "\n").toByteArray(Charsets.UTF_8))
        }
    }

    fun onResize(rows: Int, cols: Int) {
        // emulator buffer の resize は他の processBytes と排他するため emulator スレッドへ。
        scope.launch(emulatorDispatcher) {
            emulator.resize(rows, cols)
        }
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
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(
                0x1B, '['.code.toByte(), '2'.code.toByte(), 'J'.code.toByte(),
                0x1B, '['.code.toByte(), '3'.code.toByte(), 'J'.code.toByte(),
                0x1B, '['.code.toByte(), 'H'.code.toByte()
            ))
            bumpRedraw()
        }
    }

    /**
     * ディストロを切り替えて再起動する。
     *
     * 設定への永続化は非同期なので、startTerminal には spec を直接 override 渡しして
     * settingsFlow の反映待ちレースを避ける。非同梱 distro なら startTerminal 内で
     * ダウンロード → 展開が走る。
     */
    fun switchDistro(id: String) {
        setDistro(id)
        val spec = DistroSpec.byId(id) ?: DistroSpec.ALPINE
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        }
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startTerminal(spec)
    }

    fun restart() {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        }
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startTerminal()
    }

    /**
     * 現在のディストロ rootfs を完全削除して再展開する。
     *
     * 用途:
     *  - APK 更新で同梱 rootfs が新しくなったが、既存展開分が古いまま
     *  - rootfs を壊してしまったので一からやり直したい
     *
     * 注意: 展開先 (filesDir/distros/<id>) の中身は全部消える。
     */
    fun reinstallDistro() {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch {
            val distroId = settingsFlow.value.distroId
            val rootfs = java.io.File(appContext.filesDir, "distros/$distroId")
            if (rootfs.exists()) rootfs.deleteRecursively()
            withContext(emulatorDispatcher) {
                emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
            }
            _uiState.update { UiState() }
            _scrollOffset.value = 0
            startTerminal()
        }
    }

    /**
     * ディストロを「クリーン再インストール」する。
     *
     * [reinstallDistro] は rootfs だけ消すため、ダウンロード済みアーカイブの
     * キャッシュは残る。ダウンロードが途中で失敗して壊れた .tgz がキャッシュに
     * 残ると、再展開でもそれを使い続けて失敗を繰り返す。
     *
     * これは rootfs に加えて **ダウンロードキャッシュも削除** し、非同梱 distro なら
     * 必ず再ダウンロードからやり直す。ダウンロード失敗で詰まった状態を、アプリを
     * 削除せずに復旧するための手段。
     */
    fun cleanReinstallDistro() {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch {
            val distroId = settingsFlow.value.distroId
            val rootfs = java.io.File(appContext.filesDir, "distros/$distroId")
            if (rootfs.exists()) rootfs.deleteRecursively()
            downloader.deleteCachedArchive(distroId, detectAbiId())
            withContext(emulatorDispatcher) {
                emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
            }
            _uiState.update { UiState() }
            _scrollOffset.value = 0
            startTerminal()
        }
    }

    fun emitToast(message: String) { _toastEvents.tryEmit(message) }

    /** セッションを終了 (PTY を閉じてジョブをキャンセル) */
    override fun shutdown() {
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.cancel()
        // 専用スレッドも片付ける (FG service 終了時のリーク防止)
        runCatching { emulatorDispatcher.close() }
        runCatching { emulatorExecutor.shutdownNow() }
    }

    private fun writeToPty(bytes: ByteArray) {
        val pty = channel ?: return
        // PTY 書込みは IO で十分 (emulator buffer に触れないため)
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
        // 先頭マーカーで ANSI 色を付け、起動メッセージを見やすくする。
        // ([..m は SGR、末尾で必ずリセット。CR+LF で改行崩れを防ぐ)
        val color = when {
            text.startsWith("✓") -> "[32m"           // 成功: 緑
            text.startsWith("✗") || text.startsWith("致命") -> "[31m"  // 失敗: 赤
            text.startsWith("⚠") -> "[33m"            // 警告: 黄
            text.startsWith("📦") || text.startsWith("⬇") || text.startsWith("🚀") -> "[36m"  // 進行: シアン
            else -> ""
        }
        val line = if (color.isEmpty()) "$text\r\n" else "$color$text[0m\r\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(bytes, bytes.size)
            bumpRedraw()
        }
    }

    // redraw 通知のコアレッシング:
    // - bumpRedraw が連打されても、Main へ届くのは ~16ms 間隔に間引かれる。
    // - これにより echo 1 文字 × N 回でも recomposition が 60fps 程度に抑えられる。
    private val redrawScheduled = AtomicBoolean(false)
    private fun bumpRedraw() {
        if (redrawScheduled.compareAndSet(false, true)) {
            scope.launch {
                delay(REDRAW_INTERVAL_MS)
                redrawScheduled.set(false)
                _redrawTick.update { it + 1 }
            }
        }
    }

    private fun currentSize(): Pair<Int, Int> = emulator.buffer.rows to emulator.buffer.columns

    companion object {
        private const val TAG = "TerminalSession"
        /** init コマンド送出までの待機 (シェルプロンプト表示待ち) */
        private const val INIT_DELAY_MS = 400L
        /** redraw 通知の最短間隔 (~60fps) */
        private const val REDRAW_INTERVAL_MS = 16L
    }
}
