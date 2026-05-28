package com.zerotoship.z2term.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.zerotoship.z2term.R
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
    initialLabel: String = "session",
    /**
     * このタブが所有する仮想 X ディスプレイ番号 (`:N`)。proot 起動時に環境変数
     * `DISPLAY=:N` / `Z2_DISPLAY=N` が注入され、端末内で `z2run <gui-app>` を実行すると
     * 同じ :N の Xvnc が自動起動・対応する GUI タブが z2term 側で開く (P3 = CUI⇄GUI 連動)。
     * [SessionManager.openNew] が払い出すので、既存の単独 GUI タブ (🖥 ボタン) と被らない。
     */
    override val display: Int = 1,
    /**
     * セッション復元 (v1) 用。アプリ kill 後の再起動でこのタブを復元するとき、保存されていた
     * distro / cwd を渡す。初回起動 (新規タブ) では null。startTerminal の最初の 1 回だけ
     * 消費し、それ以降の restart 等では通常の設定 (distro) に従う。
     */
    restoreDistroId: String? = null,
    restoreCwd: String? = null
) : AppSession {

    /** タブ表示名 (RUNNING になったら mode を反映、それ以前は "session") */
    private val _label = MutableStateFlow(initialLabel)
    override val label: StateFlow<String> = _label.asStateFlow()
    fun setLabel(s: String) { _label.value = s }

    /**
     * このタブが実際に起動した distro の id (proot 起動成功時に確定)。セッション復元の
     * 保存対象。未起動 / android-sh フォールバック中は復元値 (or null) のまま。
     */
    private val _distroId = MutableStateFlow(restoreDistroId)
    val distroId: StateFlow<String?> = _distroId.asStateFlow()

    // 復元値は startTerminal で 1 度だけ消費する (consume 後は null)。
    private var pendingRestoreDistroId: String? = restoreDistroId
    private var pendingRestoreCwd: String? = restoreCwd

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
            _toastEvents.tryEmit(appContext.getString(R.string.toast_copy_from_remote, text.length))
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
            _toastEvents.tryEmit(appContext.getString(R.string.toast_selection_empty))
            return
        }
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
        _toastEvents.tryEmit(appContext.getString(R.string.toast_copy, text.length))
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
    fun setGuiMagnification(value: Float) { scope.launch { settings.setGuiMagnification(value) } }
    fun setCleanInstallGuiArmed(armed: Boolean) { scope.launch { settings.setCleanInstallGuiArmed(armed) } }
    fun setLandscapeKeyboardPosition(value: String) { scope.launch { settings.setLandscapeKeyboardPosition(value) } }
    fun setLandscapeKeyboardWidthDp(value: Float) { scope.launch { settings.setLandscapeKeyboardWidthDp(value) } }
    fun setLandscapeKeyboardHeightDp(value: Float) { scope.launch { settings.setLandscapeKeyboardHeightDp(value) } }
    fun setPortraitKeyboardHeightDp(value: Float) { scope.launch { settings.setPortraitKeyboardHeightDp(value) } }

    /** 設定で選ばれているディストロを使って起動。明示的指定があればそれを優先 */
    fun startTerminal(distroOverride: DistroSpec? = null) {
        // IDLE 以外なら起動済み/起動中なので何もしない。
        // SSH と PTY の二重起動レースを防ぐため、STARTING 含めて弾く。
        if (_uiState.value.state != TerminalState.IDLE) return
        _uiState.update { it.copy(state = TerminalState.STARTING) }

        scope.launch {
            // セッション復元: 明示 override が無い初回のみ、保存されていた distro を優先する。
            val restoreDistro = if (distroOverride == null) pendingRestoreDistroId else null
            pendingRestoreDistroId = null
            val spec = distroOverride
                ?: restoreDistro?.let { DistroSpec.byId(it) }
                ?: DistroSpec.byId(settingsFlow.value.distroId)
                ?: DistroSpec.ALPINE
            try {
                if (!launcher.isProotAvailable()) {
                    // M7 同梱方針: PRoot は APK の jniLibs/<abi>/libproot.so に同梱されている
                    // 前提なので、未配置はビルド事故。ユーザーに分かるよう警告を出してから
                    // android-sh に退避する (起動不能で真っ黒画面より UX 良)。
                    Log.w(TAG, "PRoot binary not present; falling back to android-sh")
                    writeBanner(appContext.getString(R.string.banner_proot_missing))
                    fallbackToAndroidSh()
                    return@launch
                }

                if (!launcher.isDistroReady(spec.id)) {
                    // 初回 / バージョン更新どちらの経路でも同じバナーで案内
                    val rootfsExists = java.io.File(appContext.filesDir, "distros/${spec.id}").exists()
                    val banner = if (rootfsExists)
                        appContext.getString(R.string.banner_extracting_update, spec.displayName)
                    else
                        appContext.getString(R.string.banner_extracting_first, spec.displayName)
                    writeBanner(banner)
                    _uiState.update { it.copy(state = TerminalState.INSTALLING) }

                    // 非同梱 distro (Ubuntu/Arch/Kali) で rootfs アーカイブが未取得なら
                    // まずダウンロードする。同梱 (Alpine) は assets から直接展開される。
                    if (!spec.bundled && downloader.resolveLocalArchive(spec, detectAbiId()) == null) {
                        val dlError = downloadDistroArchive(spec)
                        if (dlError != null) {
                            writeBanner(appContext.getString(R.string.banner_download_failed, spec.displayName, dlError.message))
                            writeBanner(appContext.getString(R.string.banner_check_network))
                            _uiState.update { it.copy(state = TerminalState.ERROR) }
                            return@launch
                        }
                    }

                    var installError: Throwable? = null
                    withContext(Dispatchers.IO) {
                        installer.install(spec).collect { progress ->
                            when (progress) {
                                is DistroInstaller.Progress.Started -> writeBanner(appContext.getString(R.string.banner_extraction_start))
                                is DistroInstaller.Progress.Extracting -> Unit
                                is DistroInstaller.Progress.Configuring -> writeBanner(appContext.getString(R.string.banner_extraction_configuring))
                                is DistroInstaller.Progress.Completed -> writeBanner(appContext.getString(R.string.banner_extraction_complete, spec.displayName))
                                is DistroInstaller.Progress.Failed -> installError = progress.error
                            }
                        }
                    }

                    if (installError != null) {
                        writeBanner(appContext.getString(R.string.banner_extraction_failed, spec.displayName, installError?.message ?: ""))
                        writeBanner(appContext.getString(R.string.banner_extraction_fallback))
                        fallbackToAndroidSh()
                        return@launch
                    }
                }

                _uiState.update { it.copy(state = TerminalState.STARTING) }
                writeBanner(appContext.getString(R.string.banner_distro_starting, spec.displayName))

                val (rows, cols) = currentSize()
                val shell = settingsFlow.value.loginShell.ifBlank { spec.defaultShell }
                // launcher 側で、指定シェルが rootfs に無ければ spec.defaultShell → /bin/sh に
                // フォールバックする (Ubuntu base に zsh が無い、等のケース)。
                // P3 (CUI⇄GUI 連動): このタブの display 番号を proot env に渡す。
                // exportDisplay=true で `DISPLAY=:N` も付与され、端末内 `z2run <gui-app>` が同じ
                // :N の Xvnc を起動 → 対応する GUI タブが z2term 側で自動的に開く。
                val pty = launcher.launch(
                    distroId = spec.id,
                    command = shell,
                    rows = rows,
                    cols = cols,
                    fallbackShell = spec.defaultShell,
                    display = display,
                    exportDisplay = true,
                )
                val ch = LocalPtyChannel(pty)
                channel = ch
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = spec.id) }
                _label.value = spec.id
                _distroId.value = spec.id
                // 復元 cwd は起動成功時に 1 度だけ消費する。
                val cwdToRestore = pendingRestoreCwd
                pendingRestoreCwd = null
                startReadLoop(ch)
                scheduleStartupCommands(cwdToRestore, settingsFlow.value.initCommand)

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start terminal", e)
                writeBanner(appContext.getString(R.string.banner_start_failed, e.message ?: ""))
                writeBanner(appContext.getString(R.string.banner_extraction_fallback))
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
        writeBanner(appContext.getString(R.string.banner_download_start, spec.displayName, sizeHint))
        var error: Throwable? = null
        var lastPct = -1
        // OS rootfs のダウンロードはタイムアウトしない (read timeout 0 = 無期限)。
        // 大物・低速回線でも最後まで待ち、途中打ち切りで最初からやり直す無駄をなくす。
        // 中断したいときは端末リセット (設定 → 端末リセット) でやり直せる。
        withContext(Dispatchers.IO) {
            downloader.download(spec, abi, readTimeoutMs = 0).collect { p ->
                when (p) {
                    is DistroDownloader.Progress.Downloading -> {
                        if (p.total > 0) {
                            val pct = (p.received * 100 / p.total).toInt()
                            if (pct >= lastPct + 10) {  // 10% 刻みでバナー更新
                                lastPct = pct
                                writeBanner(appContext.getString(R.string.banner_download_progress, pct, (p.received / 1024 / 1024).toInt()))
                            }
                        }
                    }
                    is DistroDownloader.Progress.Verifying -> writeBanner(appContext.getString(R.string.banner_download_verifying))
                    is DistroDownloader.Progress.Completed -> writeBanner(appContext.getString(R.string.banner_download_complete))
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
            writeBanner(appContext.getString(R.string.banner_fatal, e.message ?: ""))
            _uiState.update { it.copy(state = TerminalState.ERROR) }
        }
    }

    /** SSH 接続を開始 */
    fun startSsh(profile: SshProfile) {
        if (_uiState.value.state != TerminalState.IDLE) return
        _uiState.update { it.copy(state = TerminalState.STARTING) }
        scope.launch {
            writeBanner(appContext.getString(R.string.banner_ssh_connecting, profile.user, profile.host, profile.port))
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
                writeBanner(appContext.getString(R.string.banner_ssh_failed, e.message ?: ""))
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
                writeBanner(appContext.getString(R.string.banner_process_exited, ch.exitCode ?: -1))
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

    /**
     * セッション復元の起動直後シーケンス。プロンプトが出る頃に `cd <cwd>` (ベストエフォート)
     * → init コマンド の順で 1 本のコルーチンで送る (2 本に分けると順序が保証されないため)。
     */
    private fun scheduleStartupCommands(restoreCwd: String?, initCommand: String) {
        if (restoreCwd.isNullOrBlank() && initCommand.isBlank()) return
        scope.launch {
            delay(INIT_DELAY_MS)
            if (!restoreCwd.isNullOrBlank()) {
                writeBytes(("cd " + singleQuote(restoreCwd) + "\n").toByteArray(Charsets.UTF_8))
            }
            if (initCommand.isNotBlank()) {
                writeBytes((initCommand + "\n").toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** シェルに安全に渡せるよう単一引用符でエスケープする (パスに空白や記号があっても壊れない)。 */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun onResize(rows: Int, cols: Int) {
        // emulator buffer の resize は他の processBytes と排他するため emulator スレッドへ。
        // resize 完了直後に bumpRedrawImmediate で coalesce を飛ばして即 tick を進め、
        // 「リサイズしたのに数フレーム古いバッファが残って見える」状態を消す。
        scope.launch(emulatorDispatcher) {
            emulator.resize(rows, cols)
            bumpRedrawImmediate()
        }
        channel?.resize(rows, cols)
    }

    fun setScrollOffset(offset: Int) { _scrollOffset.value = offset.coerceAtLeast(0) }

    fun scrollBy(delta: Int) {
        val newOffset = (_scrollOffset.value + delta).coerceIn(0, emulator.buffer.scrollbackSize)
        _scrollOffset.value = newOffset
    }

    fun jumpToBottom() { _scrollOffset.value = 0 }

    /**
     * 指定した絶対行 (0 = scrollback 最古) が画面中央付近に来るようスクロールする。
     * スクロールバック検索のヒットへジャンプするのに使う。
     *
     * 描画 (TerminalRenderer) は scrollOffset>0 のとき topAbsRow = scrollbackSize - scrollOffset
     * なので、絶対行を中央 (topAbsRow = absRow - rows/2) に置くには
     *   scrollOffset = scrollbackSize - (absRow - rows/2)
     * とする。0..scrollbackSize にクランプ (0=最新/下端、最大=最古)。
     */
    fun scrollToAbsRow(absRow: Int) {
        val rows = emulator.buffer.rows
        val target = (emulator.buffer.scrollbackSize - (absRow - rows / 2))
            .coerceIn(0, emulator.buffer.scrollbackSize)
        _scrollOffset.value = target
        bumpRedrawImmediate()
    }

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
     * 指定ディストロを「クリーンインストール」して切り替え・再起動する。
     *
     * rootfs (filesDir/distros/<id>) を完全削除し、**ダウンロード済みアーカイブの
     * キャッシュも削除** してから最初から取得・展開し直す。ダウンロード/解凍が途中で
     * 失敗して壊れた状態 (壊れた .tgz が残って毎回失敗する等) を、アプリを削除せずに
     * 復旧するための手段。同梱 (Alpine) は assets から再展開されるのでネット不要、
     * 非同梱 (Ubuntu/Arch/Kali) は必ず再ダウンロードからやり直す。
     *
     * 設定の「ディストロ切替 + クリーンインストール」チェックから呼ばれる。永続化は
     * 非同期なので startTerminal には spec を直接 override 渡しして反映待ちレースを避ける。
     */
    fun cleanInstallDistro(id: String) {
        setDistro(id)
        val spec = DistroSpec.byId(id) ?: DistroSpec.ALPINE
        channel?.close()
        channel = null
        readJob?.cancel()
        scope.launch {
            val rootfs = java.io.File(appContext.filesDir, "distros/$id")
            if (rootfs.exists()) rootfs.deleteRecursively()
            downloader.deleteCachedArchive(id, detectAbiId())
            withContext(emulatorDispatcher) {
                emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
            }
            _uiState.update { UiState() }
            _scrollOffset.value = 0
            startTerminal(spec)
        }
    }

    /**
     * 指定ディストロの rootfs (filesDir/distros/<id>) とダウンロード済みアーカイブを
     * **完全削除**する (再インストールはしない)。不要な OS データを消してストレージを
     * 空けるための手段。使用中の OS の削除は壊れた稼働状態を避けるため UI 側で禁止する。
     *
     * @param onComplete 削除完了後にメインスレッドで 1 度だけ呼ぶ (設定 UI の一覧再読込用)。
     */
    fun deleteDistroData(id: String, onComplete: () -> Unit = {}) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val rootfs = java.io.File(appContext.filesDir, "distros/$id")
                if (rootfs.exists()) rootfs.deleteRecursively()
                runCatching { downloader.deleteCachedArchive(id, detectAbiId()) }
            }
            onComplete()
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
            text.startsWith("✗") || text.startsWith("致命") || text.startsWith("fatal") -> "[31m"  // 失敗: 赤
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

    /**
     * 即時 redraw tick。bumpRedraw の 16ms coalesce をスキップする。
     * リサイズ完了直後など「次フレームに古いバッファを見せたくない」局面で使う。
     * 既にスケジュール済みの coalesce が後追いで 1 tick 余計に発火する可能性は
     * あるが、recomposition 1 回ぶんで無害なため何もしない。
     */
    private fun bumpRedrawImmediate() {
        _redrawTick.update { it + 1 }
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
