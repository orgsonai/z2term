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
import com.zerotoship.z2term.clipboard.ClipboardHistoryStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
     * distro を渡す。初回起動 (新規タブ) では null。startTerminal の最初の 1 回だけ
     * 消費し、それ以降の restart 等では通常の設定 (distro) に従う。
     */
    restoreDistroId: String? = null
) : AppSession {

    /** タブ表示名 (RUNNING になったら mode を反映、それ以前は "session") */
    private val _label = MutableStateFlow(initialLabel)
    override val label: StateFlow<String> = _label.asStateFlow()

    /**
     * 名前が**明示的に付けられた**か (`z2-session new <名前>` など)。
     *
     * true の間は、起動時の OS 名 (`spec.id`) でもシェルが出すタイトル (OSC 0/2) でも上書きしない。
     * これが無いと `z2-session new build` で付けた名前が、その直後の起動で OS 名に化けてしまい、
     * 名前を指定した意味が無くなる (実機で確認)。
     */
    private var labelPinned = false

    /**
     * タブ名を設定する。[pinned] = true なら以後この名前を固定し、OS 名やシェルのタイトルで
     * 上書きされないようにする。
     */
    fun setLabel(s: String, pinned: Boolean = false) {
        _label.value = s
        if (pinned) labelPinned = true
    }

    /**
     * このタブが実際に起動した distro の id (proot 起動成功時に確定)。セッション復元の
     * 保存対象。未起動 / android-sh フォールバック中は復元値 (or null) のまま。
     */
    private val _distroId = MutableStateFlow(restoreDistroId)
    val distroId: StateFlow<String?> = _distroId.asStateFlow()

    // 復元値は startTerminal で 1 度だけ消費する (consume 後は null)。
    private var pendingRestoreDistroId: String? = restoreDistroId

    /**
     * このタブが**実際に**起動したエンジン名 (起動成功時に確定)。設定値ではなく実起動結果なので
     * 「設定では z2root だが未同梱で proot に倒れた」「chroot 失敗で proot にフォールバックした」
     * といったケースも正しく反映する。設定画面の信頼できるエンジン表示用 (未起動は null)。
     * 値は [AppSettings.ENGINE_PROOT]/[ENGINE_Z2ROOT]/[ENGINE_CHROOT] か [ENGINE_ANDROID_SH]。
     */
    private val _actualEngine = MutableStateFlow<String?>(null)
    val actualEngine: StateFlow<String?> = _actualEngine.asStateFlow()

    enum class TerminalState { IDLE, INSTALLING, STARTING, RUNNING, EXITED, ERROR }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val mode: String = ""
    )

    /**
     * 端末ログ (ツールバー ⏺) の状態。
     *
     * @param recording 記録中か (ツールバーのボタンが点灯している状態)。
     * @param path      書き込み中のファイルの**端末から見たパス** (`~/z2term-log/....txt`)。
     * @param realPath  同じファイルの実パス (共有・SAF 用)。
     * @param bytes     これまでに書いたサイズ。ローテーションしないので目安として必ず出す。
     */
    data class LogState(
        val recording: Boolean = false,
        val path: String = "",
        val realPath: String = "",
        val bytes: Long = 0L
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
            ClipboardHistoryStore.record(text)
            _toastEvents.tryEmit(appContext.resources.getQuantityString(
                    R.plurals.toast_copy_from_remote, text.length, text.length
                ))
        },
        // 名前を明示的に付けたタブ (labelPinned) は、シェルが出すタイトルでも上書きしない。
        titleSetter = { title -> if (title.isNotBlank() && !labelPinned) _label.value = title.take(20) },
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

    // --- 端末ログ (ツールバー ⏺) ---
    // 記録の ON/OFF は**タブごと**の状態で、永続化しない (アプリを開き直すと必ず OFF)。
    // 画面に出たものがそのままファイルに入る機能なので、意図せず記録が続いている状態を作らない。
    private var logger: SessionLogger? = null
    private val _logState = MutableStateFlow(LogState())
    val logState: StateFlow<LogState> = _logState.asStateFlow()

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
        ClipboardHistoryStore.record(text)
        _toastEvents.tryEmit(appContext.resources.getQuantityString(
                R.plurals.toast_copy, text.length, text.length
            ))
    }

    /** クリップボードのテキストをペースト (PTY へ送出)。 */
    fun pasteFromClipboard() {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString() ?: return
        // 内容は既にクリップボードにある。再セットするとクリップ変更リスナーが「新規コピー」と
        // 誤認して履歴へ積む (= 貼り付けたのにコピーされる) ため、通常の貼り付けでは同期しない。
        pasteText(text, syncClipboard = false)
    }

    /**
     * 任意のテキストをペーストする (クリップボード履歴シートからの選択で使う)。
     * [syncClipboard] が true のときだけシステムクリップボードにも反映する
     * (履歴から選んだ本文を以後の貼り付けと揃えるため)。通常の貼り付けは既にクリップに
     * 入っている内容なので false を渡し、無用なクリップ書き換え (履歴の重複積み) を避ける。
     */
    fun pasteText(text: String, syncClipboard: Boolean = true) {
        if (text.isEmpty()) return
        if (syncClipboard) {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
        }
        val body = text.replace('\n', '\r').toByteArray(Charsets.UTF_8)
        // Bracketed paste (DECSET 2004) が有効なら 200~/201~ で囲んで送る。
        // これで bash/zsh/vim が「貼り付け」と認識し、各行の即時実行や自動インデント連鎖を防ぐ。
        if (emulator.bracketedPasteMode) {
            writeBytes(BRACKET_PASTE_START + body + BRACKET_PASTE_END)
        } else {
            writeBytes(body)
        }
    }

    private val _toastEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    val settingsFlow: StateFlow<AppSettings.Snapshot> = settings.flow.stateIn(
        scope = scope, started = SharingStarted.Eagerly, initialValue = AppSettings.Snapshot()
    )

    private var channel: ProcessChannel? = null
    private var readJob: Job? = null

    val isRunning: Boolean get() = _uiState.value.state == TerminalState.RUNNING

    /**
     * いま PTY の前景プロセスグループがシェル以外か (= 何らかの子プロセスが実行中)。
     *
     * 用途: マウスレポート ON の TUI が exit 時にレポートを切り忘れたまま戻ってきても、
     * シェル前景なら primary 画面のスワイプを wheel 送信ではなく scrollback に倒すための
     * 判定。channel が判定不能 (SSH 等) なら `true` を返し従来挙動を維持する。
     */
    val hasForegroundChild: Boolean
        get() = channel?.hasForegroundChild ?: true

    /**
     * タブ削除確認用の「動作中」判定。RUNNING かつ前景プロセスがシェル以外 (子プロセス実行中)
     * のときだけ true。起動前 / 終了後や、プロンプトでシェルが前景のときは false で即削除に倒す。
     */
    override val isBusy: Boolean
        get() = isRunning && hasForegroundChild

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
                applyKittyExternalTransferSetting(snapshot.kittyExternalFileEnabled)
                bumpRedraw()
            }
        }
    }

    /**
     * 起動中のセッションに紐づく rootfs root (`<filesDir>/distros/<distroId>`)。
     * Kitty graphics の file/temp/shm 経路でゲスト→ホストパス変換に使う。
     * 起動前 / 起動失敗時は null。
     */
    private var activeRootfsRoot: java.io.File? = null

    /**
     * `kittyExternalFileEnabled` (AppSettings の opt-in) と現在の rootfs から、
     * Kitty graphics の外部転送ソースを再構築して emulator に注入する。
     * opt-in OFF / rootfs 未確定 のときは null を入れ、 parser は file/temp/shm を破棄する。
     */
    private fun applyKittyExternalTransferSetting(enabled: Boolean) {
        val root = activeRootfsRoot
        emulator.setKittyExternalTransfer(
            if (enabled && root != null && root.isDirectory) {
                com.zerotoship.z2term.emulator.KittyHostTransferSource(root)
            } else null
        )
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
    fun setKeepScreenOn(enabled: Boolean) { scope.launch { settings.setKeepScreenOn(enabled) } }
    fun setKeyboardToggleBar(enabled: Boolean) { scope.launch { settings.setKeyboardToggleBar(enabled) } }
    fun setToolbarOrder(csv: String) { scope.launch { settings.setToolbarOrder(csv) } }
    fun setToolbarHidden(csv: String) { scope.launch { settings.setToolbarHidden(csv) } }
    fun setSessionLogDir(value: String) { scope.launch { settings.setSessionLogDir(value) } }
    fun setSessionLogNameTemplate(value: String) { scope.launch { settings.setSessionLogNameTemplate(value) } }
    fun setSessionLogTimeFormat(value: String) { scope.launch { settings.setSessionLogTimeFormat(value) } }
    fun setSessionLogIncludeScrollback(value: Boolean) { scope.launch { settings.setSessionLogIncludeScrollback(value) } }
    fun setSessionLogAppend(value: Boolean) { scope.launch { settings.setSessionLogAppend(value) } }
    fun setSessionLogRaw(value: Boolean) { scope.launch { settings.setSessionLogRaw(value) } }
    fun setSessionLogAltScreen(value: Boolean) { scope.launch { settings.setSessionLogAltScreen(value) } }
    fun setConfirmBeforeDownload(enabled: Boolean) { scope.launch { settings.setConfirmBeforeDownload(enabled) } }
    fun setGuiAudioEnabled(enabled: Boolean) { scope.launch { settings.setGuiAudioEnabled(enabled) } }
    fun setGuiTerminal(id: String) { scope.launch { settings.setGuiTerminal(id) } }
    fun setGuiMagnification(value: Float) { scope.launch { settings.setGuiMagnification(value) } }
    fun setCleanInstallGuiArmed(armed: Boolean) { scope.launch { settings.setCleanInstallGuiArmed(armed) } }
    fun setLandscapeKeyboardPosition(value: String) { scope.launch { settings.setLandscapeKeyboardPosition(value) } }
    fun setLandscapeKeyboardWidthDp(value: Float) { scope.launch { settings.setLandscapeKeyboardWidthDp(value) } }
    fun setLandscapeKeyboardHeightDp(value: Float) { scope.launch { settings.setLandscapeKeyboardHeightDp(value) } }
    fun setPortraitKeyboardHeightDp(value: Float) { scope.launch { settings.setPortraitKeyboardHeightDp(value) } }
    fun setEngineSelectorUnlocked(value: Boolean) { scope.launch { settings.setEngineSelectorUnlocked(value) } }
    fun setRootChrootUnlocked(value: Boolean) { scope.launch { settings.setRootChrootUnlocked(value) } }
    fun setExecutionEngine(value: String) { scope.launch { settings.setExecutionEngine(value) } }
    /** すべての設定を初期値へ戻す (「初期化」)。エンジンも既定の z2root に戻る。 */
    fun resetSettings() { scope.launch { settings.resetToDefaults() } }
    fun setExternalStorageEnabled(value: Boolean) { scope.launch { settings.setExternalStorageEnabled(value) } }
    fun setAndroidHostBindEnabled(value: Boolean) { scope.launch { settings.setAndroidHostBindEnabled(value) } }
    fun setTraceLogEnabled(value: Boolean) { scope.launch { settings.setTraceLogEnabled(value) } }
    fun setServerEntries(json: String) { scope.launch { settings.setServerEntries(json) } }
    fun setServersAutostartOnBoot(enabled: Boolean) { scope.launch { settings.setServersAutostartOnBoot(enabled) } }
    fun setServersLowPower(enabled: Boolean) { scope.launch { settings.setServersLowPower(enabled) } }
    fun setNotificationCaptureEnabled(enabled: Boolean) { scope.launch { settings.setNotificationCaptureEnabled(enabled) } }
    fun setNotificationLogEnabled(enabled: Boolean) { scope.launch { settings.setNotificationLogEnabled(enabled) } }
    fun setNotificationLogFormat(template: String) { scope.launch { settings.setNotificationLogFormat(template) } }
    fun setNotificationLogPrepend(enabled: Boolean) { scope.launch { settings.setNotificationLogPrepend(enabled) } }
    fun setSystemEventCaptureEnabled(enabled: Boolean) { scope.launch { settings.setSystemEventCaptureEnabled(enabled) } }
    fun setSystemEventLogFormat(template: String) { scope.launch { settings.setSystemEventLogFormat(template) } }
    fun setSystemEventLogPrepend(enabled: Boolean) { scope.launch { settings.setSystemEventLogPrepend(enabled) } }
    fun setUnlockWatchEnabled(enabled: Boolean) { scope.launch { settings.setUnlockWatchEnabled(enabled) } }
    fun setSmsCaptureEnabled(enabled: Boolean) { scope.launch { settings.setSmsCaptureEnabled(enabled) } }
    fun setSmsLogFormat(template: String) { scope.launch { settings.setSmsLogFormat(template) } }
    fun setSmsLogPrepend(enabled: Boolean) { scope.launch { settings.setSmsLogPrepend(enabled) } }
    fun setKittyExternalFileEnabled(value: Boolean) { scope.launch { settings.setKittyExternalFileEnabled(value) } }
    fun setSgrMouseInputEnabled(value: Boolean) { scope.launch { settings.setSgrMouseInputEnabled(value) } }

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
            // settingsFlow は stateIn(Eagerly) の初期値が既定 Snapshot (distroId=alpine) なので、
            // アプリ更新・端末再起動直後など DataStore の初回 emit がまだ届いていないタイミングで
            // ここを通ると、選択中の OS ではなく既定 alpine で起動してしまうレースがある
            // (「希に alpine が立ち上がる」現象の正体)。確実に永続値を await してから決める。
            val persisted = settings.flow.first()
            val spec = distroOverride
                ?: restoreDistro?.let { DistroSpec.byId(it) }
                ?: DistroSpec.byId(persisted.distroId)
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

                    // 非同梱 distro (Ubuntu/Arch/Kali、および foss の Alpine) で rootfs
                    // アーカイブが未取得ならまずダウンロードする。full の同梱 Alpine は
                    // effectivelyBundled=true なのでスキップし assets から直接展開される。
                    if (!spec.effectivelyBundled && downloader.resolveLocalArchive(spec, detectAbiId()) == null) {
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
                val shell = settingsFlow.value.loginShell.ifBlank { spec.effectiveDefaultShell }
                // launcher 側で、指定シェルが rootfs に無ければ spec.effectiveDefaultShell → /bin/sh に
                // フォールバックする (Ubuntu base に zsh が無い、等のケース)。
                // P3 (CUI⇄GUI 連動): このタブの display 番号を proot env に渡す。
                // exportDisplay=true で `DISPLAY=:N` も付与され、端末内 `z2run <gui-app>` が同じ
                // :N の Xvnc を起動 → 対応する GUI タブが z2term 側で自動的に開く。
                val s = settingsFlow.value
                val useChroot = s.executionEngine == AppSettings.ENGINE_CHROOT && s.rootChrootUnlocked
                // 実際に起動したエンジンを確定して記録する (設定値ではなく実起動結果。
                // 設定画面の信頼できるエンジン表示用)。chroot 失敗時は proot へ倒れるので個別に設定する。
                val engineUsed: String
                val pty = if (useChroot) {
                    // 裏機能: root で実 chroot 起動。失敗時は PRoot へフォールバック。
                    val chrootPty = runCatching {
                        launcher.launchChroot(
                            distroId = spec.id,
                            command = shell,
                            rows = rows,
                            cols = cols,
                            fallbackShell = spec.effectiveDefaultShell,
                            loginShell = shell,
                            display = display,
                        )
                    }.getOrElse { e ->
                        Log.w(TAG, "chroot launch failed, falling back to proot", e)
                        null
                    }
                    if (chrootPty != null) {
                        engineUsed = AppSettings.ENGINE_CHROOT
                        chrootPty
                    } else {
                        engineUsed = launcher.resolveLaunchEngine()
                        launcher.launch(
                            distroId = spec.id,
                            command = shell,
                            rows = rows,
                            cols = cols,
                            fallbackShell = spec.effectiveDefaultShell,
                            loginShell = shell,
                            display = display,
                            exportDisplay = true,
                        )
                    }
                } else {
                    engineUsed = launcher.resolveLaunchEngine()
                    launcher.launch(
                        distroId = spec.id,
                        command = shell,
                        rows = rows,
                        cols = cols,
                        fallbackShell = spec.effectiveDefaultShell,
                        loginShell = shell,
                        display = display,
                        exportDisplay = true,
                    )
                }
                _actualEngine.value = engineUsed
                val ch = LocalPtyChannel(pty)
                channel = ch
                _uiState.update { it.copy(state = TerminalState.RUNNING, mode = spec.id) }
                // 名前を明示的に付けたタブ (labelPinned) は OS 名で上書きしない。
                if (!labelPinned) _label.value = spec.id
                _distroId.value = spec.id
                // Kitty graphics の file/temp/shm 経路用に、 セッションの rootfs root を確定。
                // opt-in 設定が ON ならこのタイミングで transfer source が注入される
                // (combine 内の applyKittyExternalTransferSetting も同じ値を見る)。
                activeRootfsRoot = java.io.File(appContext.filesDir, "distros/${spec.id}")
                applyKittyExternalTransferSetting(settingsFlow.value.kittyExternalFileEnabled)
                startReadLoop(ch)
                scheduleStartupCommands(settingsFlow.value.initCommand)

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start terminal", e)
                writeBanner(appContext.getString(R.string.banner_start_failed, e.message ?: ""))
                writeBanner(appContext.getString(R.string.banner_extraction_fallback))
                fallbackToAndroidSh()
            }
        }
    }

    /**
     * 自動起動 (startTerminal) がそのまま走るとネットワーク DL が発生する場合、その対象
     * spec を返す。確認不要 (確認 OFF / 同梱 / 導入済み / アーカイブ取得済み) なら null。
     * UI 側はこれが非 null のとき先にダウンロード確認ダイアログを出す (foss の初回起動など)。
     * pendingRestoreDistroId は消費せず peek するだけ (実際の消費は startTerminal が行う)。
     */
    fun downloadOnStartSpec(): DistroSpec? {
        if (!settingsFlow.value.confirmBeforeDownload) return null
        val spec = pendingRestoreDistroId?.let { DistroSpec.byId(it) }
            ?: DistroSpec.byId(settingsFlow.value.distroId)
            ?: DistroSpec.ALPINE
        if (spec.effectivelyBundled) return null
        if (launcher.isDistroReady(spec.id)) return null
        if (downloader.resolveLocalArchive(spec, detectAbiId()) != null) return null
        return spec
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
            // 固定 URL の distro (foss Alpine) は SHA-256 を検証する。index 解決の
            // distro は spec.sha256 が null なので従来どおり HTTPS のみ。
            downloader.download(spec, abi, expectedSha256 = spec.sha256(abi), readTimeoutMs = 0).collect { p ->
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
            _actualEngine.value = AppSettings.ENGINE_ANDROID_SH
            _uiState.update { it.copy(state = TerminalState.RUNNING, mode = "android-sh") }
            if (!labelPinned) _label.value = "sh"
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
                if (!labelPinned) _label.value = "ssh:${profile.name.ifEmpty { profile.host }}"
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
                            // 端末ログ (⏺) の分岐点。**タブに出るものは必ずここを通る**唯一の場所。
                            // エミュレータに食わせた「後」に渡すのは、alt screen に入ったかどうかが
                            // この塊を処理した後でないと正しく判定できないため。
                            // 書き込み自体は SessionLogger 側の専用スレッドへ積むだけで、
                            // ここ (描画を直列化しているスレッド) はブロックしない。
                            logger?.let { lg ->
                                if (emulator.buffer.primaryActive || settingsFlow.value.sessionLogAltScreen) {
                                    lg.append(chunk)
                                }
                            }
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
     * 起動直後シーケンス。プロンプトが出る頃に init コマンドを送る。
     */
    private fun scheduleStartupCommands(initCommand: String) {
        if (initCommand.isBlank()) return
        scope.launch {
            delay(INIT_DELAY_MS)
            writeBytes((initCommand + "\n").toByteArray(Charsets.UTF_8))
        }
    }

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
    // ---------------------------------------------------------------- 端末ログ (⏺)

    /** ツールバー ⏺ の短押し。記録していなければ始め、していれば止める。 */
    fun toggleLogging() {
        if (_logState.value.recording) stopLogging() else startLogging()
    }

    /**
     * 端末ログの記録を始める。既に記録中なら何もしない。
     *
     * 保存先は**ホーム配下** (`filesDir/shared_home/<設定のフォルダ>`)。端末からもファイラーからも
     * すぐ触れる場所に置く、というのがこの機能の要 (取り出せないログには意味が無い)。
     * ファイル名は設定のテンプレート (`{date}` / `{tab}`) から作り、追記 OFF のときに同名が
     * 既にあれば `-2` `-3` を足して**上書きしない**。
     */
    fun startLogging() {
        if (_logState.value.recording) return
        val s = settingsFlow.value
        val home = File(appContext.filesDir, "shared_home")
        val relDir = s.sessionLogDir.trim().trim('/').ifBlank { AppSettings.DEFAULT_SESSION_LOG_DIR }
        val dir = File(home, relDir)
        val file = runCatching {
            dir.mkdirs()
            resolveLogFile(dir, s)
        }.getOrElse {
            Log.w(TAG, "log dir failed: ${it.message}")
            _toastEvents.tryEmit(appContext.getString(R.string.toast_log_start_failed))
            return
        }
        val lg = runCatching { SessionLogger(file, append = s.sessionLogAppend, raw = s.sessionLogRaw) }
            .getOrElse {
                Log.w(TAG, "log open failed: ${it.message}")
                _toastEvents.tryEmit(appContext.getString(R.string.toast_log_start_failed))
                return
            }
        logger = lg
        _logState.value = LogState(
            recording = true,
            path = "~/$relDir/${file.name}",
            realPath = file.absolutePath,
            bytes = lg.bytesWritten
        )
        // 「押した時点より前」も残す設定のときは、今の画面 + スクロールバックを先頭に書く。
        // バッファ読み出しはエミュレータのスレッドで行う (描画側と同じ直列化に乗せる)。
        if (s.sessionLogIncludeScrollback) {
            scope.launch(emulatorDispatcher) {
                val text = runCatching { emulator.buffer.getAllText(includeScrollback = true) }.getOrNull()
                if (!text.isNullOrEmpty()) lg.appendText(text.trimEnd('\n') + "\n")
            }
        }
        // サイズ表示を定期的に追いかける (ローテーションしないので現在値を必ず見せる)。
        scope.launch {
            while (_logState.value.recording && logger === lg) {
                delay(LOG_SIZE_POLL_MS)
                if (logger !== lg) break
                _logState.update { it.copy(bytes = lg.bytesWritten) }
            }
        }
    }

    /** 端末ログの記録を止め、書き残しを吐き出してファイルを閉じる。 */
    fun stopLogging() {
        val lg = logger ?: run { _logState.update { it.copy(recording = false) }; return }
        logger = null
        lg.close()
        _logState.update { it.copy(recording = false, bytes = lg.bytesWritten) }
    }

    /**
     * 設定のテンプレートからログファイルを決める。
     * 追記 ON ならそのままの名前、OFF で同名が既にあれば `-2` `-3` … を足す。
     */
    private fun resolveLogFile(dir: File, s: AppSettings.Snapshot): File {
        val stamp = runCatching {
            SimpleDateFormat(s.sessionLogTimeFormat.ifBlank { AppSettings.DEFAULT_SESSION_LOG_TIME }, Locale.US)
                .format(Date())
        }.getOrElse {
            // 書式が壊れていても記録は始められること (設定ミスで機能ごと死なせない)。
            SimpleDateFormat(AppSettings.DEFAULT_SESSION_LOG_TIME, Locale.US).format(Date())
        }
        // タブ名をファイル名に使える形へ。日本語などの Unicode 文字は残し、パス区切りや予約記号・
        // 制御文字・空白だけを _ にする。連続する _ は 1 つに畳み、前後の _/./- は削る。全部落ちたら "term"。
        val tab = _label.value
            .replace(LOG_NAME_UNSAFE, "_")
            .replace(LOG_NAME_UNDERSCORE_RUN, "_")
            .take(32)
            .trim('_', '.', '-')
            .ifBlank { "term" }
        val base = s.sessionLogNameTemplate.ifBlank { AppSettings.DEFAULT_SESSION_LOG_NAME }
            .replace("{date}", stamp)
            .replace("{tab}", tab)
            .replace(LOG_NAME_UNSAFE_PATH, "_")
            .ifBlank { "z2term.txt" }
        val first = File(dir, base)
        if (s.sessionLogAppend || !first.exists()) return first
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        for (n in 2..LOG_NAME_MAX_TRIES) {
            val f = File(dir, "$stem-$n$ext")
            if (!f.exists()) return f
        }
        return File(dir, "$stem-${System.currentTimeMillis()}$ext")
    }

    override fun shutdown() {
        // タブを閉じるときは書き残しを必ず吐き出す (バッファは数百 ms 分だけ残っている)。
        stopLogging()
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
        /** 端末ログの「今のサイズ」表示を更新する間隔 (ローテーションしないので必ず見せる) */
        private const val LOG_SIZE_POLL_MS = 1000L
        /** ログのファイル名に使えない文字 (タブ名から作る部分に適用)。 */
        // ファイル名に使えない文字だけを _ にする (パス区切り・予約記号・制御文字・空白)。
        // 日本語などの Unicode 文字はそのまま残す。以前は「ASCII 英数字以外を全部 _」にしていたため、
        // 日本語タイトルのタブが下線だらけのファイル名 (例: 2026-07-24_0941-____.txt) になっていた。
        private val LOG_NAME_UNSAFE = Regex("""[/\\:*?"<>|\x00-\x1F\s]""")

        /** 連続する下線を 1 つに畳む (置換で _ が並んだときに詰める)。 */
        private val LOG_NAME_UNDERSCORE_RUN = Regex("_+")
        /** テンプレート展開後に残ってはいけない文字 (パス区切り等)。 */
        private val LOG_NAME_UNSAFE_PATH = Regex("""[/\\:*?"<>| ]""")
        /** 同名ファイルを避ける連番の上限 (これを超えたら時刻を付けて逃がす)。 */
        private const val LOG_NAME_MAX_TRIES = 99
        /** Bracketed paste 開始シーケンス ESC [ 200 ~ */
        private val BRACKET_PASTE_START = byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), '~'.code.toByte())
        /** Bracketed paste 終了シーケンス ESC [ 201 ~ */
        private val BRACKET_PASTE_END = byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), '~'.code.toByte())
    }
}
