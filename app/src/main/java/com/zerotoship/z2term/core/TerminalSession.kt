package com.zerotoship.z2term.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.LocalPtyChannel
import com.zerotoship.z2term.channel.ProcessChannel
import com.zerotoship.z2term.backup.AutoBackup
import com.zerotoship.z2term.channel.SshChannel
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.service.ExitReasons
import com.zerotoship.z2term.service.NetGuard
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
import kotlinx.coroutines.flow.SharedFlow
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
     * 値は [AppSettings.ENGINE_Z2ROOT]/[ENGINE_CHROOT] か [ENGINE_ANDROID_SH]。
     */
    private val _actualEngine = MutableStateFlow<String?>(null)
    val actualEngine: StateFlow<String?> = _actualEngine.asStateFlow()

    enum class TerminalState { IDLE, INSTALLING, STARTING, RUNNING, EXITED, ERROR }

    data class UiState(
        val state: TerminalState = TerminalState.IDLE,
        val mode: String = ""
    )

    /**
     * 端末ログ (ツールバー ⚪) の状態。
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

    // --- 繋ぎっぱなしの相手 (z2-session attach) ------------------------------
    //
    // PTY から読んだ生バイトを、画面へ流すのと同時にここへも流す。⚠ **端末ログ (C1) と
    // 同じ塊・同じ場所**で複製する: 別の所で作り直すと「画面には出たのに繋いだ先には来ない」
    // 類のズレができる。
    //
    // CopyOnWriteArrayList: 読む側 (PTY 読み取りスレッド) が圧倒的に多く、足し引きは
    // 繋ぐ/抜けるの瞬間だけ。ロックを取らずに回せる形にしておく。
    private val attachSinks = java.util.concurrent.CopyOnWriteArrayList<(ByteArray) -> Unit>()

    private val _attachedCount = MutableStateFlow(0)

    /** 今このタブに外から繋がっている数 (`z2-session list` の `@` 印)。 */
    val attachedCount: StateFlow<Int> = _attachedCount.asStateFlow()

    /**
     * 画面 (スマホ) 側が要求している広さ。⚠ **繋いでいる間は PTY へ渡さずここに覚えるだけ**で、
     * 抜けたときにこれを流し直して元へ戻す。
     *
     * 覚えておかないと戻せない: 画面側の resize は `LaunchedEffect(session.id, rows, cols)` が
     * 駆動していて、**行×列が変わらない限り二度と走らない** (`TerminalRenderer.kt`)。
     * つまり抜けた瞬間に誰も「スマホの広さ」を教え直してくれない。
     */
    @Volatile private var screenRows = 0
    @Volatile private var screenCols = 0

    /**
     * 繋ぎっぱなしの相手を 1 つ足す。**戻り値を呼ぶと外れる。**
     *
     * ⚠ 外し方を戻り値で渡すのは、繋いだ側が異常終了したときに確実に外させるため。
     * 一覧を持たせて id で消す形にすると、消し忘れたぶんだけ PTY の出力が行き場を失う。
     */
    fun addAttachSink(sink: (ByteArray) -> Unit): () -> Unit {
        attachSinks.add(sink)
        _attachedCount.value = attachSinks.size
        return {
            if (attachSinks.remove(sink)) {
                _attachedCount.value = attachSinks.size
                if (attachSinks.isEmpty()) restoreScreenSize()
            }
        }
    }

    /**
     * 繋いだ側の広さに合わせる (ユーザー判断・2026-08-20)。
     *
     * ⚠ **この間スマホ側のタブは折り返しが合わず崩れて見える**。承知のうえの選択で、
     * 最後の 1 人が抜ければ [restoreScreenSize] で戻る。
     */
    fun setAttachSize(rows: Int, cols: Int) {
        if (rows <= 0 || cols <= 0) return
        applySize(rows, cols)
    }

    /** 誰も繋いでいない状態へ戻す。スマホ側の広さを流し直す。 */
    private fun restoreScreenSize() {
        if (screenRows > 0 && screenCols > 0) applySize(screenRows, screenCols)
    }

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
        cwdSetter = { path -> _cwd.value = path },
        // DA2 / XTVERSION で名乗る版数。エミュレータ側は Android に依存しないので here で渡す。
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _redrawTick = MutableStateFlow(0)
    val redrawTick: StateFlow<Int> = _redrawTick.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    private val _cellMetrics = MutableStateFlow(CellMetrics())
    val cellMetrics: StateFlow<CellMetrics> = _cellMetrics.asStateFlow()

    // --- 端末ログ (ツールバー ⚪) ---
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

    /**
     * いまクリップボードにあるテキスト (無ければ null)。
     *
     * 貼る前に中身を見たい呼び元 (複数行の確認・0.8.232) のために、読み取りだけを切り出す。
     * 読むだけなのでクリップボードは書き換えない。
     */
    fun clipboardText(): String? {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
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

    /**
     * つまずきの言い換え ([TerminalHints]) の通知。UI が 1 行バーとして出す。
     * 出力そのものには手を触れないので、購読しなければ何も起きない。
     */
    private val _hintEvents = MutableSharedFlow<TerminalHints.Hint>(replay = 0, extraBufferCapacity = 2)
    val hintEvents: SharedFlow<TerminalHints.Hint> = _hintEvents.asSharedFlow()

    /** チャンクの境目で 1 行が割れても拾えるよう、直前の末尾を少しだけ持ち越す。 */
    private var hintCarry = ""

    /** 同じヒントを出した時刻 (連発を抑える。お節介にしないため)。 */
    private val hintLastShown = HashMap<TerminalHints.Hint, Long>()

    /**
     * 出力チャンクから既知のつまずきを探し、当たれば [hintEvents] へ流す。
     * 制御コードはそのままデコードして見る (エラーメッセージ自体は平文で連続して出る)。
     */
    private fun scanForHint(chunk: ByteArray) {
        val text = hintCarry + String(chunk, Charsets.UTF_8)
        hintCarry = text.takeLast(TerminalHints.CARRY_CHARS)
        val hint = TerminalHints.detect(text) ?: return
        val now = System.currentTimeMillis()
        val prev = hintLastShown[hint]
        if (prev != null && now - prev < TerminalHints.REPEAT_SUPPRESS_MS) return
        hintLastShown[hint] = now
        _hintEvents.tryEmit(hint)
    }

    private val _toastEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    val settingsFlow: StateFlow<AppSettings.Snapshot> = settings.flow.stateIn(
        scope = scope, started = SharingStarted.Eagerly, initialValue = AppSettings.Snapshot()
    )

    private var channel: ProcessChannel? = null
    private var readJob: Job? = null

    /**
     * いま SSH で繋いでいる相手 (0.8.388)。通信量の上限で**切るかどうかの判断にだけ**使う —
     * 家の中への接続はモバイル通信を使わないので切らない ([NetGuard.isLocalTarget])。
     */
    @Volatile private var sshHost: String? = null

    /**
     * **自分で畳んだのか、外から殺されたのか**の区別 (0.8.378)。
     *
     * タブを閉じる / 再起動 / distro 切替 / SSH へ切替は、どれも PTY のプロセスを
     * シグナルで終わらせる = 外から殺されたときと**終了コードが見分けられない**。
     * ここが false のまま終わったものだけを異常終了として記録する。
     */
    @Volatile private var selfClosed = false

    /**
     * 意図してチャネルを畳む。⚠ **畳むときは必ずここを通すこと** — 直接
     * `channel?.close()` を書くと [selfClosed] が立たず、利用者がタブを閉じただけで
     * 「外から殺された」と記録される。
     */
    private fun closeChannel() {
        selfClosed = true
        channel?.close()
        channel = null
        sshHost = null
        readJob?.cancel()
    }

    /**
     * 通信量の上限に達したので外向きの SSH を畳む (0.8.388・[NetGuard.enforce] から)。
     *
     * ⚠ **家の中への接続は畳まない**。モバイル通信を 1 バイトも使っていない接続を切るのは、
     * 使いすぎを止めることと関係がない (利用者の要望でもある)。SSH でないタブも触らない。
     * ⚠ 畳む前に**理由をその画面へ書く** — 黙って切れると、通信が悪いのか壊れたのか
     * 分からないまま繋ぎ直しを繰り返すことになる。
     */
    fun disconnectForNetLimit() {
        val host = sshHost ?: return
        if (NetGuard.isLocalTarget(host)) return
        scope.launch {
            writeBanner(appContext.getString(R.string.net_limit_disconnected))
            closeChannel()
        }
    }

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

    /** 前景プロセスを実際に見られるチャネル (ローカル PTY) のときだけ、印の表示に使ってよい。 */
    override val busyKnown: Boolean
        get() = isRunning && channel?.supportsForegroundChild == true

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
    fun setKeyboardNumberFace(enabled: Boolean) { scope.launch { settings.setKeyboardNumberFace(enabled) } }
    fun setKeyboardFaceOrder(orderId: String) { scope.launch { settings.setKeyboardFaceOrder(orderId) } }
    fun setKeyboardFaceEnabledIds(ids: String) { scope.launch { settings.setKeyboardFaceEnabledIds(ids) } }
    /** 自分で作ったキー配列の束を丸ごと保存する (0.8.408)。⚠ 束は常に全体を渡す。 */
    fun setKeyboardLayoutsJson(json: String) { scope.launch { settings.setKeyboardLayoutsJson(json) } }
    /** いま使うキー配列を選ぶ (0.8.408)。**空文字 = 既定のプリセット**。 */
    fun setKeyboardLayoutActiveId(id: String) { scope.launch { settings.setKeyboardLayoutActiveId(id) } }
    fun setLoginShell(shell: String) { scope.launch { settings.setLoginShell(shell) } }
    fun setKeyboardMode(mode: String) { scope.launch { settings.setKeyboardMode(mode) } }
    fun setKeepAliveService(enabled: Boolean) { scope.launch { settings.setKeepAliveService(enabled) } }
    /** 初回ガイド (最初の 3 枚) を出し終えたことを覚える。 */
    fun setIntroDone(done: Boolean) { scope.launch { settings.setIntroDone(done) } }
    fun setTerminalHintsEnabled(enabled: Boolean) { scope.launch { settings.setTerminalHintsEnabled(enabled) } }
    fun setKeepScreenOn(enabled: Boolean) { scope.launch { settings.setKeepScreenOn(enabled) } }
    fun setScreenBrightness(level: Float?) { scope.launch { settings.setScreenBrightness(level) } }
    fun setKeyboardToggleBar(enabled: Boolean) { scope.launch { settings.setKeyboardToggleBar(enabled) } }
    fun setSpecialKeyBar(enabled: Boolean) { scope.launch { settings.setSpecialKeyBar(enabled) } }
    fun setToolbarOrder(csv: String) { scope.launch { settings.setToolbarOrder(csv) } }
    fun setToolbarHidden(csv: String) { scope.launch { settings.setToolbarHidden(csv) } }
    fun setUpdateDownloadDir(value: String) { scope.launch { settings.setUpdateDownloadDir(value) } }
    fun setUpdateKeepApk(value: Boolean) { scope.launch { settings.setUpdateKeepApk(value) } }
    // 定期バックアップ (0.8.386)。⚠ **設定を書いたらその場で予約を貼り直す** — 書いただけでは
    // 次の書き出しは今までの時刻のまま動く (または止まったまま動かない)。
    fun setAutoBackupEnabled(value: Boolean) {
        scope.launch { settings.setAutoBackupEnabled(value); AutoBackup.schedule(appContext) }
    }
    fun setAutoBackupFolder(treeUri: String) {
        scope.launch { settings.setAutoBackupFolder(treeUri); AutoBackup.schedule(appContext) }
    }
    // 通信量の上限 (0.8.388)。⚠ ON/OFF のときだけ見張りを置き直す (間隔は固定なので、
    // 上限や締め日を変えても予約そのものは変わらない)。
    fun setNetLimitEnabled(value: Boolean) {
        scope.launch {
            settings.setNetLimitEnabled(value)
            withContext(Dispatchers.IO) { NetGuard.schedule(appContext) }
        }
    }
    fun setNetLimitMb(value: Int) { scope.launch { settings.setNetLimitMb(value) } }
    fun setNetLimitResetDay(value: Int) { scope.launch { settings.setNetLimitResetDay(value) } }
    fun setNetLimitWifiExempt(value: Boolean) { scope.launch { settings.setNetLimitWifiExempt(value) } }
    fun setAutoBackupSchedule(
        interval: String,
        dayOfWeek: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        keep: Int
    ) {
        scope.launch {
            settings.setAutoBackupSchedule(interval, dayOfWeek, dayOfMonth, hour, minute, keep)
            AutoBackup.schedule(appContext)
        }
    }
    fun setSessionLogDir(value: String) { scope.launch { settings.setSessionLogDir(value) } }
    fun setSessionLogNameTemplate(value: String) { scope.launch { settings.setSessionLogNameTemplate(value) } }
    fun setSessionLogTimeFormat(value: String) { scope.launch { settings.setSessionLogTimeFormat(value) } }
    fun setSessionLogIncludeScrollback(value: Boolean) { scope.launch { settings.setSessionLogIncludeScrollback(value) } }
    fun setSessionLogAppend(value: Boolean) { scope.launch { settings.setSessionLogAppend(value) } }
    fun setSessionLogRaw(value: Boolean) { scope.launch { settings.setSessionLogRaw(value) } }
    fun setSessionLogAltScreen(value: Boolean) { scope.launch { settings.setSessionLogAltScreen(value) } }
    fun setSessionLogAutoStart(value: Boolean) { scope.launch { settings.setSessionLogAutoStart(value) } }
    fun setSessionLogMaskSecrets(value: Boolean) { scope.launch { settings.setSessionLogMaskSecrets(value) } }
    fun setSessionLogTimestamp(value: Boolean) { scope.launch { settings.setSessionLogTimestamp(value) } }
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
                if (!launcher.isEngineAvailable()) {
                    Log.w(TAG, "z2root binary not present; falling back to android-sh")
                    writeBanner(appContext.getString(R.string.banner_z2root_missing))
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

                    // rootfs アーカイブが未取得なら先にダウンロードする。
                    if (downloader.resolveLocalArchive(spec, detectAbiId()) == null) {
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
                val s = settingsFlow.value
                val useChroot = s.executionEngine == AppSettings.ENGINE_CHROOT && s.rootChrootUnlocked
                // 実際に起動したエンジンを確定して記録する (設定値ではなく実起動結果。
                // 設定画面の信頼できるエンジン表示用)。chroot 失敗時は z2root へ戻す。
                val engineUsed: String
                val pty = if (useChroot) {
                    // 裏機能: root で実 chroot 起動。失敗時は z2root へフォールバック。
                    val chrootPty = runCatching {
                        launcher.launchChroot(
                            distroId = spec.id,
                            command = shell,
                            rows = rows,
                            cols = cols,
                            fallbackShell = spec.defaultShell,
                            loginShell = shell,
                            display = display,
                        )
                    }.getOrElse { e ->
                        Log.w(TAG, "chroot launch failed, falling back to z2root", e)
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
                            fallbackShell = spec.defaultShell,
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
                        fallbackShell = spec.defaultShell,
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
                // ⚠ **鍵束の用意は初期化コマンドより先**に流す (0.8.316)。Arch は鍵束が無いまま
                // では pacman が何も入れられない ([ProotLauncher.needsPacmanKeyring]) ので、
                // 利用者の初期化コマンドがパッケージ導入だった場合、順番が逆だと必ず失敗する。
                scheduleStartupCommands(
                    pacmanKeyringCommandOrNull(spec.id),
                    settingsFlow.value.initCommand
                )

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start terminal", e)
                writeBanner(appContext.getString(R.string.banner_start_failed, e.message ?: ""))
                writeBanner(appContext.getString(R.string.banner_extraction_fallback))
                fallbackToAndroidSh()
            }
        }
    }

    /**
     * 自動起動でどうするか (0.8.314)。UI (タブを開いた直後) はこれを見て 3 通りに分かれる。
     */
    sealed interface StartupPlan {
        /** そのまま [startTerminal] してよい (同梱 / 導入済み / 確認 OFF)。 */
        data object Start : StartupPlan

        /**
         * **OS が 1 つも入っていない**。ダウンロードの催促ではなく、⚙設定 から選んで
         * もらう案内を出す ([com.zerotoship.z2term.ui.terminal.NoOsNotice])。
         */
        data object NeedOsInstall : StartupPlan

        /** 選んでいる OS がまだ無いので、先にダウンロード確認ダイアログを出す。 */
        data class ConfirmDownload(val spec: DistroSpec) : StartupPlan
    }

    /**
     * 自動起動 (startTerminal) をそのまま走らせてよいかを決める。
     *
     * ⚠ **永続値を await してから決める** (0.8.314)。以前は `settingsFlow.value` を見ていたが、
     * これは `stateIn(Eagerly)` の初期値 = 既定 Snapshot (`distroId=alpine`) なので、DataStore の
     * 初回 emit が届く前にここを通ると**選んでいる OS ではなく alpine の判定になる**。
     * 「Arch で使っているのに、新しいタブを開くとタイミングによって Alpine のダウンロードを
     * 催促される」の正体がこれ ([startTerminal] は既に同じ理由で await している)。
     *
     * pendingRestoreDistroId は消費せず peek するだけ (実際の消費は [startTerminal] が行う)。
     */
    suspend fun startupPlan(): StartupPlan {
        val persisted = settings.flow.first()
        val spec = pendingRestoreDistroId?.let { DistroSpec.byId(it) }
            ?: DistroSpec.byId(persisted.distroId)
            ?: DistroSpec.ALPINE
        // 入れる必要が無い (展開済み / アーカイブ取得済み) ならそのまま起動。
        if (launcher.isDistroReady(spec.id)) return StartupPlan.Start
        if (downloader.resolveLocalArchive(spec, detectAbiId()) != null) return StartupPlan.Start
        // ⚠ **OS が 1 つも無いときは、確認 ON/OFF に関わらず勝手に入れない。** どれから始めるかは
        // 利用者が選ぶこと (初回起動で既定の 1 本を押し付けない)。
        if (!launcher.hasAnyDistro()) return StartupPlan.NeedOsInstall
        if (!persisted.confirmBeforeDownload) return StartupPlan.Start
        return StartupPlan.ConfirmDownload(spec)
    }

    /**
     * **OS が 1 つでも入っているか** (0.8.339)。[StartupPlan.NeedOsInstall] と同じ判定を、
     * 起動の筋道と関係なく UI から聞けるようにしたもの。
     *
     * UI 側はこれを見て「まっさらな人」と「もう入れた人」を分ける:
     *  - まっさらな間は**はじめの案内を出さない** (押しても走る先が無く、案内だけ使い切ってしまう)。
     *  - まっさらな間だけ ⚙設定 の上部に案内を固定する
     *    ([com.zerotoship.z2term.ui.terminal.NoOsSettingsNotice]・0.8.342)。
     *
     * ディレクトリを見に行くので IO へ逃がす。
     */
    suspend fun hasAnyDistro(): Boolean = withContext(Dispatchers.IO) { launcher.hasAnyDistro() }

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
            // 固定 URL の distro (Alpine) は SHA-256 を検証する。index 解決の
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
                sshHost = profile.host
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
        closeChannel()
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        }
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startSsh(profile)
    }

    private fun startReadLoop(ch: ProcessChannel) {
        readJob?.cancel()
        // 新しいチャネルの分は「まだ自分では畳んでいない」から始める ([closeChannel])。
        selfClosed = false
        // ⚪ の自動開始 (0.8.243)。**チャネルが繋がった直後 = タブに何か出る直前**のここ 1 か所で
        // 判定する。ローカル / android-sh フォールバック / SSH のどの経路も必ず通るので、
        // 起動経路を足したときに付け忘れが起きない。
        //
        // 設定は settingsFlow ではなく DataStore から読み直す — アプリの起動直後は初回 emit が
        // 間に合わず、settingsFlow がまだ既定値 (自動開始 OFF) のことがあり、**いちばん録りたい
        // 1 本目のタブだけ録れない**という形で外れる。読み直した snapshot は startLogging へ
        // そのまま渡す (保存先・ファイル名も同じ理由で既定に落ちるため)。
        if (logger == null) {
            scope.launch {
                val snap = settings.flow.first()
                if (snap.sessionLogAutoStart) startLogging(snap)
            }
        }
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
                            // 端末ログ (⚪) の分岐点。**タブに出るものは必ずここを通る**唯一の場所。
                            // エミュレータに食わせた「後」に渡すのは、alt screen に入ったかどうかが
                            // この塊を処理した後でないと正しく判定できないため。
                            // 書き込み自体は SessionLogger 側の専用スレッドへ積むだけで、
                            // ここ (描画を直列化しているスレッド) はブロックしない。
                            logger?.let { lg ->
                                if (emulator.buffer.primaryActive || settingsFlow.value.sessionLogAltScreen) {
                                    lg.append(chunk)
                                }
                            }
                            // 繋ぎっぱなしの相手へも同じ塊を流す (z2-session attach)。
                            // ⚠ ログと違い **alt screen でも必ず流す** — 相手は画面を再現している
                            // ので、間引くと vi 等に入った瞬間に表示が固まる。
                            // ⚠ 1 人が詰まっても他へ流し続ける (相手の都合で PTY を止めない)。
                            if (attachSinks.isNotEmpty()) {
                                attachSinks.forEach { sink -> runCatching { sink(chunk) } }
                            }
                            // つまずきの言い換え (0.8.237)。**出力は一切書き換えず**、
                            // 既知のパターンに当たったことだけを UI へ知らせる。
                            // alt screen (vim/less 等) の最中は見ない — 全画面アプリの描画に
                            // たまたま含まれる文字列で誤爆するため。
                            if (settingsFlow.value.terminalHintsEnabled && emulator.buffer.primaryActive) {
                                scanForHint(chunk)
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
                val code = ch.exitCode ?: -1
                _uiState.update { it.copy(state = TerminalState.EXITED) }
                // タブの木が**外から**殺されたときは、理由をその場で残す (0.8.378)。
                // ⚠ アプリのプロセスが死ぬ場合と違って OS の ApplicationExitInfo には残らない
                //   ので、ここで書かないと後から辿る手段が無い ([ExitReasons] の KDoc 参照)。
                val killed = !selfClosed && ExitReasons.isKilledBySignal(code)
                val availMb =
                    if (killed) ExitReasons.recordTabKill(appContext, label.value, code) else -1L
                writeBanner(
                    if (killed && availMb >= 0) appContext.getString(
                        R.string.banner_process_killed,
                        ExitReasons.signalLabel(code - 128), code, availMb
                    ) else appContext.getString(R.string.banner_process_exited, code)
                )
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
     * 起動直後シーケンス。プロンプトが出る頃に、渡された順にコマンドを送る。
     *
     * ⚠ **1 本のコルーチンでまとめて送る** (0.8.316)。呼び分けて 2 回起こすと、どちらが先に
     * 書かれるか決まらない。鍵束の用意 → 利用者の初期化コマンド、のように**順番に意味がある**
     * ものが混ざるので、順序は呼び出し側の並びで固定する。
     */
    private fun scheduleStartupCommands(vararg commands: String?) {
        val queued = commands.filterNot { it.isNullOrBlank() }.filterNotNull()
        if (queued.isEmpty()) return
        scope.launch {
            delay(INIT_DELAY_MS)
            for (c in queued) writeBytes((c + "\n").toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * この distro が pacman を使うのに鍵束が未初期化なら、直す 1 行を返す (0.8.316)。
     *
     * Arch (Arch Linux ARM) の rootfs は `/etc/pacman.d/gnupg` を持たずに来るのに
     * `SigLevel = Required` なので、**放っておくと `pacman -S` が何をしても失敗する**
     * (GUI の導入も `sshd` = dropbear も同じ所で落ちる)。systemd が動かない環境では
     * 誰も初期化しないので、端末が立ち上がったところで 1 回だけ流して直す。
     *
     * 画面の上で走らせるのは**黙って待たせないため**。数十秒かかることがあり、
     * バナーだけでは「固まった」と区別が付かない。止めたければ Ctrl-C で止められる
     * (中身は冪等なので、次に開いたときにやり直す)。
     */
    private fun pacmanKeyringCommandOrNull(distroId: String): String? =
        if (launcher.needsPacmanKeyring(distroId)) "z2-pacman-keyring" else null

    /**
     * 画面 (スマホ) 側から要求される広さ。
     *
     * ⚠ **外から繋がっている間は PTY へ渡さない** — 繋いだ側 (PC) の広さに合わせると決めてある
     * ([setAttachSize])。ここで上書きすると、タブを表示した瞬間にスマホの幅へ戻ってしまい、
     * 繋いでいる側の画面が突然狭くなる。値は覚えておいて、最後の 1 人が抜けたときに流し直す。
     */
    fun onResize(rows: Int, cols: Int) {
        screenRows = rows
        screenCols = cols
        if (attachSinks.isNotEmpty()) return
        applySize(rows, cols)
    }

    private fun applySize(rows: Int, cols: Int) {
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
        closeChannel()
        scope.launch(emulatorDispatcher) {
            emulator.processBytes(byteArrayOf(0x1B, 'c'.code.toByte()))
        }
        _uiState.update { UiState() }
        _scrollOffset.value = 0
        startTerminal(spec)
    }

    fun restart() {
        closeChannel()
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
        closeChannel()
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
    // ---------------------------------------------------------------- 端末ログ (⚪)

    /** ツールバー ⚪ の短押し。記録していなければ始め、していれば止める。 */
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
     *
     * @param snapshot 使う設定。省略時は [settingsFlow] の現在値 (= ユーザーが ⚪ を押した場合)。
     *   **自動開始の経路は必ず読み直した値を渡すこと** — 起動直後は [settingsFlow] がまだ既定値で、
     *   保存先やファイル名を変えている人だけ「1 本目のタブが既定の場所に落ちる」ことになる。
     */
    fun startLogging(snapshot: AppSettings.Snapshot? = null) {
        if (_logState.value.recording) return
        val s = snapshot ?: settingsFlow.value
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
        val lg = runCatching {
            SessionLogger(
                file,
                append = s.sessionLogAppend,
                raw = s.sessionLogRaw,
                mask = s.sessionLogMaskSecrets,
                timestamp = s.sessionLogTimestamp
            )
        }
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
        closeChannel()
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
