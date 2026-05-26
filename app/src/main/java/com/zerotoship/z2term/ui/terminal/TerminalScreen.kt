package com.zerotoship.z2term.ui.terminal

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerotoship.z2term.core.AppSession
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.GuiScreen
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import kotlinx.coroutines.flow.first
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.ui.settings.SettingsSheet
import com.zerotoship.z2term.ui.sftp.SftpSheet
import com.zerotoship.z2term.ui.snippets.SnippetsSheet
import com.zerotoship.z2term.ui.ssh.SshProfilesSheet
import com.zerotoship.z2term.ui.ssh.HostKeyVerificationDialog
import com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import com.zerotoship.z2term.ui.terminal.keyboard.ComposingState
import com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore
import com.zerotoship.z2term.ui.terminal.keyboard.KanaKanjiConverter
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.emulator.ZtsTheme
import com.zerotoship.z2term.emulator.resolveTheme
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.ui.settings.CustomThemeSheet
import com.zerotoship.z2term.ui.theme.AppColors
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch

/** キーボードモード。CUSTOM=独自キーボード、SYSTEM=OS IME + 特殊キーバー */
enum class KeyboardMode { CUSTOM, SYSTEM }

/**
 * 画面消灯ロックの状態 (M8-6 T9)。端末タブと GUI タブで別々の `remember` を持つと、
 * タブ種別を跨いだ瞬間に新画面側が false で初期化されフラグを落としてしまう。
 * そこで**単一の状態**にして画面跨ぎでも維持する。既定 OFF・プロセス再起動でリセット。
 */
private object ScreenAwake {
    val enabled = mutableStateOf(false)
}

/** ContextWrapper の連鎖を辿って Activity を取り出す (Compose の Context は wrapper のことがある)。 */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 画面消灯ロックを適用する。View 単位の `keepScreenOn` は OEM の省電力で取りこぼすことがあるため、
 * 可能ならウィンドウ直付け (FLAG_KEEP_SCREEN_ON) にする。Activity が取れなければ View にフォールバック。
 */
private fun applyKeepScreenOn(context: Context, fallbackView: View, on: Boolean) {
    val window = context.findActivity()?.window
    if (window != null) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        fallbackView.keepScreenOn = on
    }
}

/**
 * アプリ全体のターミナル画面。
 *
 * 構造:
 *   TopBar           ← セッションラベル / 状態 / 貼付 (長押しで IME 切替)
 *   TabBar           ← 全セッション + 「+」
 *   コンテンツ領域    ← Renderer + InputView + Floating overlays
 *   Toggle bar       ← タップでキーボード表示/非表示 (高さ可変は廃止)
 *   キーボード領域    ← CUSTOM=独自 (style.naturalHeight 固定)、SYSTEM=SpecialKeyBar
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by SessionManager.sessions.collectAsState()
    val activeId by SessionManager.activeId.collectAsState()
    val activeSession = sessions.firstOrNull { it.id == activeId }

    // GUI タブがアクティブなら GUI 画面を描いて終わり (端末 UI は出さない)。
    // タブバーは GuiTabScreen 側にも置くので端末↔GUI の切替はできる。
    if (activeSession is GuiSession) {
        GuiTabScreen(sessions = sessions, activeId = activeId, modifier = modifier)
        return
    }

    val active = activeSession as? TerminalSession
    if (active == null) {
        // セッション未生成時のプレースホルダ (通常 ensureFirst で 1 つ存在する)
        Box(modifier.fillMaxSize().background(ZtsBgPrimary))
        return
    }

    val settings by active.settingsFlow.collectAsState()
    val customTheme by CustomThemeStore.theme.collectAsState()

    // 選択テーマをアプリ全体のカラーパレットへ反映 (TopBar / タブ / 各シート /
    // キーボードまで)。AppColors は global な snapshot state なので、ここを更新すると
    // ルートの Z2TermTheme を含む Zts* 参照 Composable がすべて再コンポーズされ追従する。
    // 独自テーマ編集 (customTheme 変化) でも選択中なら即反映される。
    LaunchedEffect(settings.themeName, customTheme) {
        AppColors.applyFrom(resolveTheme(settings.themeName, customTheme))
    }

    var ctrlSticky by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.CUSTOM) }
    var inputViewRef by remember { mutableStateOf<TerminalInputView?>(null) }
    var keyboardCollapsed by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var snippetsSheetOpen by remember { mutableStateOf(false) }
    var sshSheetOpen by remember { mutableStateOf(false) }
    // SFTP ファイルブラウザ対象のプロファイル (非 null の間シートを表示)
    var sftpProfile by remember { mutableStateOf<SshProfile?>(null) }
    var customThemeEditorOpen by remember { mutableStateOf(false) }
    // 画面消灯ロック (ディスプレイが自動で消えないようにする)。権限不要・フォアグラウンド中のみ
    // 有効・CPU は握らないので WakeLock より安全。状態は端末/GUI 共通の [ScreenAwake] (画面跨ぎで維持)。
    // 既定 OFF (放置でのバッテリ消費を避ける。アプリ再起動でリセット)。
    val keepScreenOn = ScreenAwake.enabled.value
    val rootView = LocalView.current
    LaunchedEffect(keepScreenOn) {
        applyKeepScreenOn(context, rootView, keepScreenOn)
    }

    // かな漢字変換: 入力中ひらがな(composing)と候補を保持。確定で PTY へ送出。
    val composing = remember(active.id) {
        ComposingState(onCommit = { active.writeBytes(it.toByteArray(Charsets.UTF_8)) })
    }
    // 辞書はアプリ起動後にバックグラウンドで 1 度だけ読み込む。
    LaunchedEffect(Unit) { KanaKanjiConverter.ensureLoaded(context) }
    // IME 学習履歴 (確定済み読み→単語) も同タイミングで読み込み、変換候補のランキングに使う。
    LaunchedEffect(Unit) { ImeHistoryStore.ensureLoaded(context) }
    // キーボードモード変更時は変換中バッファを破棄 (OS IME と二重表示を防ぐ)。
    LaunchedEffect(keyboardMode, keyboardCollapsed) { composing.reset() }

    // 起動時に保存されたキーボードモードを 1 度だけ復元 (毎回 OS IME に切替える手間を省く)
    var restoredMode by remember { mutableStateOf(false) }
    LaunchedEffect(settings.keyboardMode) {
        if (!restoredMode) {
            keyboardMode = if (settings.keyboardMode == "system")
                KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
            restoredMode = true
        }
    }

    // 常駐サービスの起動/停止を設定に追従させる。
    // 設定変更で即反映され、ON 復帰時に再度フォアグラウンド化する。
    LaunchedEffect(settings.keepAliveService) {
        if (settings.keepAliveService) {
            TerminalService.start(context)
        } else {
            // セッションは殺さず常駐解除のみ (背景で OS に殺されてもよい挙動)
            TerminalService.detach(context)
        }
    }

    LaunchedEffect(active.id) {
        // IDLE 状態のセッションだけ自動的にローカル PTY を立ち上げる。
        // SSH などで外部から STARTING に進められたセッションは触らない。
        if (active.uiState.value.state == com.zerotoship.z2term.core.TerminalSession.TerminalState.IDLE) {
            active.startTerminal()
        }
    }
    LaunchedEffect(keyboardMode, inputViewRef, active.id) {
        val v = inputViewRef ?: return@LaunchedEffect
        v.session = active
        v.imeEnabled = (keyboardMode == KeyboardMode.SYSTEM)
        if (keyboardMode == KeyboardMode.SYSTEM) v.requestKeyboard()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZtsBgPrimary)
            // safeDrawing = systemBars ∪ ime ∪ displayCutout を 1 つの inset で適用。
            // systemBarsPadding().imePadding() の連鎖は消費順序の都合で 3 ボタンナビ
            // (下部) の inset が効かずキーボード最下段が被ることがあったため統一。
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TopBar(
            session = active,
            keyboardMode = keyboardMode,
            onPaste = { active.pasteFromClipboard() },
            onToggleKeyboardMode = {
                val next = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
                keyboardMode = next
                active.setKeyboardMode(if (next == KeyboardMode.SYSTEM) "system" else "custom")
            },
            onOpenSettings = { settingsOpen = true },
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { ScreenAwake.enabled.value = !ScreenAwake.enabled.value },
            onOpenSnippets = { snippetsSheetOpen = true }
        )

        TabBar(
            sessions = sessions,
            activeId = activeId,
            onSelect = { SessionManager.setActive(it) },
            onClose = { SessionManager.close(it) },
            onNew = { SessionManager.openNew(context) },
            onNewGui = { SessionManager.openNewGui(context) }
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            TerminalRenderer(session = active, composingText = composing.text, modifier = Modifier.fillMaxSize())
            AndroidView(
                factory = { ctx ->
                    TerminalInputView(ctx).also { v ->
                        v.session = active
                        v.imeEnabled = (keyboardMode == KeyboardMode.SYSTEM)
                        inputViewRef = v
                    }
                },
                update = { v ->
                    v.session = active
                    v.ctrlSticky = ctrlSticky
                },
                modifier = Modifier.fillMaxSize()
            )
            ScrollIndicators(session = active, modifier = Modifier.fillMaxSize())
            // 変換候補バー: キーボードの上に浮かせて表示 (キーボード本体の高さは変えない)
            CandidateBar(
                composing = composing,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        KeyboardToggleBar(
            collapsed = keyboardCollapsed,
            onToggle = { keyboardCollapsed = !keyboardCollapsed }
        )

        if (!keyboardCollapsed) {
            when (keyboardMode) {
                KeyboardMode.CUSTOM -> {
                    val style = KeyboardStyle.byId(settings.keyboardStyleId)
                    // 高さはスタイルの naturalHeight 固定。これでキーサイズと領域高さが
                    // 常に一致する (旧: 高さ可変でキーサイズが追従せずズレていた)。
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(style.naturalHeight)
                    ) {
                        TerminalKeyboard(
                            onBytes = { active.writeBytes(it) },
                            onCursorKey = { key -> active.writeBytes(active.emulator.cursorKeyBytes(key)) },
                            composing = composing,
                            style = style
                        )
                    }
                }
                KeyboardMode.SYSTEM -> {
                    // OS IME はシステムが描画するため、こちらは SpecialKeyBar の高さだけ。
                    SpecialKeyBar(
                        session = active,
                        ctrlSticky = ctrlSticky,
                        onCtrlToggle = { ctrlSticky = !ctrlSticky }
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            session = active,
            onDismiss = { settingsOpen = false },
            onOpenSsh = {
                settingsOpen = false
                sshSheetOpen = true
            },
            onEditCustomTheme = { customThemeEditorOpen = true }
        )
    }
    if (customThemeEditorOpen) {
        CustomThemeSheet(
            base = resolveTheme(settings.themeName, customTheme),
            existing = customTheme,
            onSave = { theme ->
                customThemeEditorOpen = false
                scope.launch {
                    CustomThemeStore.save(theme)
                    active.setThemeName(theme.name)
                }
            },
            onDelete = {
                customThemeEditorOpen = false
                scope.launch {
                    CustomThemeStore.save(null)
                    // 独自テーマを選択中だったら既定へ戻す
                    if (settings.themeName == customTheme?.name) {
                        active.setThemeName(ZtsTheme.name)
                    }
                }
            },
            onDismiss = { customThemeEditorOpen = false }
        )
    }
    if (snippetsSheetOpen) {
        SnippetsSheet(
            onDismiss = { snippetsSheetOpen = false },
            onRun = { command ->
                active.writeBytes(command.toByteArray(Charsets.UTF_8))
            }
        )
    }
    if (sshSheetOpen) {
        SshProfilesSheet(
            onDismiss = { sshSheetOpen = false },
            onConnect = { profile -> active.connectSsh(profile) },
            onSftp = { profile -> sftpProfile = profile }
        )
    }
    sftpProfile?.let { profile ->
        SftpSheet(
            profile = profile,
            onDismiss = { sftpProfile = null }
        )
    }
    // ホスト鍵検証はワーカースレッドからブロッキングで呼ばれるため、
    // SSH UI の表示状態に関わらずルートに常駐させる。
    HostKeyVerificationDialog()
}

/**
 * GUI タブの画面。端末画面と同じ TopBar + タブバー + キーボードを持つ。
 *
 *  - **TopBar は残す**。端末専用ボタン (📋貼付 / CMD) は keysym 橋渡しで GUI へタイプ。💡/⌨/⚙ 有効。
 *  - **キーボードは端末と同一仕様** (CUSTOM=独自 / SYSTEM=OS IME + 特殊キーバー)。GUI に**上乗せ**
 *    (オーバーレイ) なので解像度・領域は変わらず、▾ で折りたためば GUI を広く使える。
 *  - **GUI 領域に枠線**を付け、どこからどこが GUI か分かるようにする。枠の内側の実寸を測り、
 *    その px を **表示倍率**で割った解像度で Xvnc を起動する → 中央フィットで左右に黒帯が出ず
 *    **画面幅をフル活用**、かつ倍率で「細かすぎ」を緩和できる。
 *
 * 表示領域の実寸が確定してから Xvnc を起動 (解像度を倍率で決めるため寸法が要る)。タブ切替で離れても
 * [GuiSession] は SessionManager が保持し続けるので動き続ける (停止はタブ × のときのみ)。
 */
@Composable
private fun GuiTabScreen(
    sessions: List<AppSession>,
    activeId: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gui = sessions.firstOrNull { it.id == activeId } as? GuiSession ?: return

    // GUI タブには TerminalSession が無いので、設定は AppSettings を直接購読する。
    val appSettings = remember { AppSettings(context.applicationContext) }
    val settings by appSettings.flow.collectAsState(initial = AppSettings.Snapshot())
    val customTheme by CustomThemeStore.theme.collectAsState()
    LaunchedEffect(settings.themeName, customTheme) {
        AppColors.applyFrom(resolveTheme(settings.themeName, customTheme))
    }
    LaunchedEffect(Unit) { KanaKanjiConverter.ensureLoaded(context) }
    LaunchedEffect(Unit) { ImeHistoryStore.ensureLoaded(context) }

    // 枠線の内側 (= 実際に GUI を描く領域) の実測 px。これを倍率で割って Xvnc 解像度を決める。
    var guiAreaPx by remember(gui.id) { mutableStateOf(IntSize.Zero) }
    // 起動確認待ち (初回 DL or クリーンインストール)。Triple(w, h, clean)。
    var pendingGuiStart by remember(gui.id) { mutableStateOf<Triple<Int, Int, Boolean>?>(null) }

    // キーボードは端末タブと同一仕様 (CUSTOM=独自 / SYSTEM=OS IME + 特殊キーバー)。GUI に上乗せ
    // (オーバーレイ) で出すので解像度は変えない。▾ で折りたたんで GUI を広く使うこともできる。
    var keyboardMode by remember { mutableStateOf(KeyboardMode.CUSTOM) }
    var keyboardCollapsed by remember { mutableStateOf(false) }
    var ctrlSticky by remember { mutableStateOf(false) }
    // 画面消灯ロックは端末タブと共通の単一状態 (画面跨ぎで維持。M8-6 T9)。
    val keepScreenOn = ScreenAwake.enabled.value
    var settingsOpen by remember { mutableStateOf(false) }
    var snippetsSheetOpen by remember { mutableStateOf(false) }

    val rootView = LocalView.current
    LaunchedEffect(keepScreenOn) { applyKeepScreenOn(context, rootView, keepScreenOn) }

    // 起動時に保存済みキーボードモードを 1 度だけ復元 (端末と挙動を揃える)。
    var restoredMode by remember { mutableStateOf(false) }
    LaunchedEffect(settings.keyboardMode) {
        if (!restoredMode) {
            keyboardMode = if (settings.keyboardMode == "system")
                KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
            restoredMode = true
        }
    }

    // かな漢字変換: 確定文字列は keysym で GUI へ送る (端末はバイト送出、GUI は keysym 経路)。
    val composing = remember(gui.id) {
        ComposingState(onCommit = { GuiKeyMapper.sendText(gui.rfb, it) })
    }
    LaunchedEffect(keyboardMode, keyboardCollapsed) { composing.reset() }

    // 表示領域の実寸が確定したら Xvnc を起動する (倍率で解像度を決めるため寸法が要る)。
    // key は gui.id だけ。サイズは snapshotFlow で待つ。
    // ※ guiAreaPx を key にすると、寸法が数フレームで確定する間に suspend 中の本コルーチンが
    //   毎回キャンセルされ、起動もダイアログも走らないまま IDLE で固まる (特にクリーンは
    //   DataStore 書込の suspend が挟まり再現性が高い)。最初の非ゼロ寸法で 1 度だけ起動する。
    LaunchedEffect(gui.id) {
        val size = snapshotFlow { guiAreaPx }.first { it.width > 0 && it.height > 0 }
        // 設定は最新を読む (初期 Snapshot の取りこぼし回避)。
        val snap = appSettings.flow.first()
        val mag = snap.guiMagnification.coerceIn(
            AppSettings.MIN_GUI_MAGNIFICATION, AppSettings.MAX_GUI_MAGNIFICATION
        )
        val w = (size.width / mag).toInt().coerceIn(320, 4096)
        val h = (size.height / mag).toInt().coerceIn(320, 4096)
        val clean = snap.cleanInstallGuiArmed
        // クリーンインストール予約は起動と同時に必ず消化する (チェックを確実に外す)。
        if (clean) appSettings.setCleanInstallGuiArmed(false)
        val installed = guiPackagesInstalled(
            context, snap.distroId, GuiTerminal.byId(snap.guiTerminalId).binary
        )
        // 確認 ON かつ (クリーン or 未導入 = 通信が走る) のときだけダイアログ。
        // 導入済み & 非クリーン or 確認 OFF はそのまま起動 (= 従来挙動)。
        if (snap.confirmBeforeDownload && (clean || !installed)) {
            pendingGuiStart = Triple(w, h, clean)
        } else {
            gui.start(w, h, clean)
        }
    }

    // 設定シートは TerminalSession を要求するので、開いている端末タブを 1 つ借りる。
    // GUI だけのときは ⚙ をグレーアウト (端末タブを開けば設定できる)。
    val terminalForSettings = sessions.firstOrNull { it is TerminalSession } as? TerminalSession

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZtsBgPrimary)
            // OS IME はオーバーレイで出すので解像度に影響させない → systemBars のみ。
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        GuiTopBar(
            session = gui,
            keyboardMode = keyboardMode,
            onPaste = {
                // Android クリップボードのテキストを keysym 橋渡しで GUI へタイプする。
                val cm = context.getSystemService(ClipboardManager::class.java)
                val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrEmpty()) GuiKeyMapper.sendText(gui.rfb, text)
            },
            onOpenSnippets = { snippetsSheetOpen = true },
            onToggleKeyboardMode = {
                val next = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
                keyboardMode = next
                scope.launch {
                    appSettings.setKeyboardMode(if (next == KeyboardMode.SYSTEM) "system" else "custom")
                }
            },
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { ScreenAwake.enabled.value = !ScreenAwake.enabled.value },
            settingsEnabled = terminalForSettings != null,
            onOpenSettings = { settingsOpen = true }
        )

        TabBar(
            sessions = sessions,
            activeId = activeId,
            onSelect = { SessionManager.setActive(it) },
            onClose = { SessionManager.close(it) },
            onNew = { SessionManager.openNew(context) },
            onNewGui = { SessionManager.openNewGui(context) }
        )

        // GUI 領域を枠線で囲って範囲を明示し、内側の実寸 (onSizeChanged) で解像度を決める。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp)
                .border(2.dp, ZtsGreen)
                .padding(2.dp)
                .onSizeChanged { guiAreaPx = it }
        ) {
            GuiScreen(
                session = gui,
                imeVisible = keyboardMode == KeyboardMode.SYSTEM && !keyboardCollapsed,
                ctrlSticky = ctrlSticky,
                onCtrlConsumed = { ctrlSticky = false },
                modifier = Modifier.fillMaxSize()
            )

            // キーボードを GUI に上乗せ (オーバーレイ。解像度は変えない)。▾ で折りたためる。
            // SYSTEM 時は OS IME の上に出すため imePadding。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                CandidateBar(composing = composing)
                KeyboardToggleBar(
                    collapsed = keyboardCollapsed,
                    onToggle = { keyboardCollapsed = !keyboardCollapsed }
                )
                if (!keyboardCollapsed) {
                    when (keyboardMode) {
                        KeyboardMode.CUSTOM -> {
                            val style = KeyboardStyle.byId(settings.keyboardStyleId)
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(style.naturalHeight)
                            ) {
                                TerminalKeyboard(
                                    onBytes = { GuiKeyMapper.sendBytes(gui.rfb, it) },
                                    onCursorKey = { key -> gui.rfb.tapKey(GuiKeyMapper.keysymForCursor(key)) },
                                    composing = composing,
                                    style = style
                                )
                            }
                        }
                        KeyboardMode.SYSTEM -> {
                            GuiSpecialKeyBar(
                                rfb = gui.rfb,
                                ctrlSticky = ctrlSticky,
                                onCtrlToggle = { ctrlSticky = !ctrlSticky }
                            )
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen && terminalForSettings != null) {
        SettingsSheet(
            session = terminalForSettings,
            onDismiss = { settingsOpen = false },
            // GUI からは SSH / 独自テーマ編集の各シートまでは開かない (端末タブで)。
            onOpenSsh = { settingsOpen = false },
            onEditCustomTheme = { }
        )
    }
    if (snippetsSheetOpen) {
        SnippetsSheet(
            onDismiss = { snippetsSheetOpen = false },
            // 端末は writeBytes だが GUI は keysym 橋渡しで送る (M8-6 T1)。
            onRun = { command -> GuiKeyMapper.sendText(gui.rfb, command) }
        )
    }
    // GUI 起動確認 (初回 DL / クリーンインストール)。OK で起動、やめる→タブを閉じる
    // (パッケージ無しでは表示できないため)。
    pendingGuiStart?.let { (w, h, clean) ->
        DownloadConfirmDialog(
            title = if (clean) "GUI をクリーンインストール" else "GUI 一式をダウンロード",
            message = if (clean)
                "GUI 表示用パッケージをキャッシュごと消して入れ直します (数十〜数百MB)。Wi-Fi 推奨。続けますか?"
            else
                "GUI (Linux デスクトップ) の初回起動には表示用パッケージの取得が必要です " +
                    "(数十〜数百MB)。Wi-Fi 推奨。続けますか?",
            confirmLabel = if (clean) "クリーンインストール" else "ダウンロードして起動",
            onConfirm = { pendingGuiStart = null; gui.start(w, h, clean) },
            onCancel = { pendingGuiStart = null; SessionManager.close(gui.id) }
        )
    }
}

/**
 * GUI 一式 (X サーバ + WM + 選択端末) が選択中 distro に導入済みかを、rootfs のバイナリ有無で判定する
 * (M8-6 T7 のダウンロード確認ゲート用)。z2gui の `check` と同じ条件を Android 側から軽量に判定する。
 */
private fun guiPackagesInstalled(context: Context, distroId: String, terminalBinary: String): Boolean {
    val base = java.io.File(context.filesDir, "distros/$distroId")
    fun hasBin(name: String) =
        java.io.File(base, "usr/bin/$name").exists() || java.io.File(base, "bin/$name").exists()
    val xserver = hasBin("Xvnc") || hasBin("Xtigervnc")
    return xserver && hasBin("openbox") && hasBin(terminalBinary)
}

/**
 * GUI タブ用 TopBar。端末の [TopBar] と同じ並び・見た目を保ちつつ、端末専用ボタン
 * (📋貼付 / CMD スニペット) はグレーアウトする。💡画面消灯ロック / ⌨キーボード切替 /
 * ⚙設定 は GUI でも使えるので有効。
 */
@Composable
private fun GuiTopBar(
    session: GuiSession,
    keyboardMode: KeyboardMode,
    onPaste: () -> Unit,
    onOpenSnippets: () -> Unit,
    onToggleKeyboardMode: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val label by session.label.collectAsState()
    val state by session.state.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)  // 端末 TopBar と同じく高さ固定 (折り返しで縦に伸びるのを防ぐ)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = ZtsGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Box(modifier = Modifier.weight(1f))

        // 並びは端末 TopBar と同じ。📋/CMD は keysym 橋渡しで GUI へタイプする (M8-6 T1)。
        TopBarIconButton(label = "📋", onClick = onPaste)    // Android クリップボードを GUI へ貼付
        TopBarIconButton(label = "CMD", onClick = onOpenSnippets) // スニペットを GUI へ送出
        KeepScreenOnButton(active = keepScreenOn, onClick = onToggleKeepScreenOn)
        KeyboardToggleButton(
            imeActive = keyboardMode == KeyboardMode.SYSTEM,
            onClick = onToggleKeyboardMode
        )
        TopBarIconButton(label = "⚙", enabled = settingsEnabled, onClick = onOpenSettings)

        Text(
            text = state.name,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * GUI 用の特殊キーバー (端末 [com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar] の keysym 版)。
 * SYSTEM キーボードモードで OS IME と一緒に出す。送出はバイトでなく X keysym。
 */
@Composable
private fun GuiSpecialKeyBar(
    rfb: RfbClient,
    ctrlSticky: Boolean,
    onCtrlToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuiSpecialKey("ESC") { rfb.tapKey(GuiKeyMapper.XK_Escape) }
        GuiSpecialKey("TAB") { rfb.tapKey(GuiKeyMapper.XK_Tab) }
        GuiSpecialKey("CTRL", active = ctrlSticky, onClick = onCtrlToggle)
        GuiSpecialKey("←") { rfb.tapKey(GuiKeyMapper.XK_Left) }
        GuiSpecialKey("↓") { rfb.tapKey(GuiKeyMapper.XK_Down) }
        GuiSpecialKey("↑") { rfb.tapKey(GuiKeyMapper.XK_Up) }
        GuiSpecialKey("→") { rfb.tapKey(GuiKeyMapper.XK_Right) }
        GuiSpecialKey("⏎") { rfb.tapKey(GuiKeyMapper.XK_Return) }
        GuiSpecialKey("C-C") { GuiKeyMapper.sendCtrlCombo(rfb, 'c'.code) }
        GuiSpecialKey("C-D") { GuiKeyMapper.sendCtrlCombo(rfb, 'd'.code) }
        GuiSpecialKey("C-L") { GuiKeyMapper.sendCtrlCombo(rfb, 'l'.code) }
    }
}

@Composable
private fun GuiSpecialKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) ZtsGreen else ZtsBgCard
    val fg = if (active) Color.Black else ZtsTextPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TopBar(
    session: TerminalSession,
    keyboardMode: KeyboardMode,
    onPaste: () -> Unit,
    onToggleKeyboardMode: () -> Unit,
    onOpenSettings: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    onOpenSnippets: () -> Unit
) {
    val label by session.label.collectAsState()
    val cwd by session.cwd.collectAsState()
    val ui by session.uiState.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 高さを固定。以前はラベル(タブ名)が長いと折り返して Row が縦に伸び、
            // 右端のステータスも押し出されて見えなくなっていた。固定高 + 各テキスト 1 行で防ぐ。
            .height(48.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = ZtsGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            // 長いタブ名で折り返さない/横幅を食い潰さないよう 1 行 + 省略 + 上限幅。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        if (ui.mode.isNotEmpty()) {
            Text(
                text = "[${ui.mode}]",
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (cwd.isNotEmpty()) {
            Text(
                text = cwd,
                color = ZtsTextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1
            )
        }
        Box(modifier = Modifier.weight(1f))

        // 並び (左→右): 貼付 / コマンド一覧 / 画面消灯ロック / キーボード切替 / 設定
        // 貼付ボタン (タップ = クリップボード貼り付けのみ)。
        // 📋 クリップボードアイコン = 貼り付け、の方が直感的なため漢字「貼」から変更。
        TopBarIconButton(label = "📋", onClick = onPaste)
        // コマンド一覧 (スニペット)。"CMD" テキストで明示。
        TopBarIconButton(label = "CMD", onClick = onOpenSnippets)
        // 画面消灯ロック (タップで ON/OFF。ON 中は画面が自動消灯しない)
        KeepScreenOnButton(active = keepScreenOn, onClick = onToggleKeepScreenOn)
        // キーボード切替ボタン (タップ = OS IME ⇄ 独自キーボード)
        KeyboardToggleButton(
            imeActive = keyboardMode == KeyboardMode.SYSTEM,
            onClick = onToggleKeyboardMode
        )
        TopBarIconButton(label = "⚙", onClick = onOpenSettings)

        Text(
            text = ui.state.name,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TopBarIconButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // 無効時はグレーアウト (GUI タブで端末専用ボタンを残しつつ押せなくする)。
    val bg = if (enabled) ZtsBgCard else ZtsBgCard.copy(alpha = 0.35f)
    val border = if (enabled) ZtsBorder else ZtsBorder.copy(alpha = 0.35f)
    val fg = if (enabled) ZtsTextPrimary else ZtsTextSecondary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 画面消灯ロックボタン (タップで ON/OFF をトグル)。
 * ON の間は画面が自動で消灯しない (`View.keepScreenOn`)。ON 中は緑でハイライト。
 * 表示は ON=💡 (点灯) / OFF=🔅 (暗) の電球で状態を示す。
 */
@Composable
private fun KeepScreenOnButton(
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) ZtsGreen else ZtsBgCard
    val fg = if (active) Color.Black else ZtsTextPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (active) "💡" else "🔅",
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * キーボード切替ボタン (タップのみで OS IME ⇄ 独自キーボードをトグル)。
 * IME (SYSTEM) が有効な間は緑でハイライト。表示は「あ」(OS IME へ) / 独自時は枠のみ。
 */
@Composable
private fun KeyboardToggleButton(
    imeActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (imeActive) ZtsGreen else ZtsBgCard
    val fg = if (imeActive) Color.Black else ZtsTextPrimary
    val border = if (imeActive) ZtsGreen else ZtsBorder
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "あ",
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TabBar(
    sessions: List<AppSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onNewGui: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgPrimary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sessions.forEach { sess ->
            TabChip(
                session = sess,
                active = sess.id == activeId,
                canClose = sessions.size > 1,
                onSelect = { onSelect(sess.id) },
                onClose = { onClose(sess.id) }
            )
        }
        // 新規端末タブ
        NewTabButton(label = "+", onClick = onNew)
        // 新規 GUI タブ (Xvnc + RFB)。端末用「+」の隣に並べる。
        NewTabButton(label = "🖥", onClick = onNewGui)
    }
}

@Composable
private fun NewTabButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = ZtsTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(
    session: AppSession,
    active: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val label by session.label.collectAsState()
    val bg = if (active) ZtsBgCard else ZtsBgPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    val fg = if (active) ZtsGreen else ZtsTextSecondary
    // 単タップ=アクティブ化 / ダブルタップ=閉じる。× ボタンは廃止 (誤タップ防止 M8-6 T8)。
    // 最後の 1 枚 (canClose=false) はダブルタップでも閉じない。
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onSelect,
                onDoubleClick = if (canClose) onClose else null
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            // タブ名が長くてもチップが横に伸びすぎないよう上限幅 + 省略。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp)
        )
    }
}

/**
 * ターミナル / キーボード間のトグルバー。
 *
 * タップでキーボードの表示/非表示を切り替えるだけ (高さ可変ドラッグは廃止)。
 * ドラッグ処理を無くしたことで「ウニョウニョ動く」不安定さが解消される。
 * 折り畳み中はハンドルを緑にして「タップで開く」ことを示す。
 */
@Composable
private fun KeyboardToggleBar(
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (collapsed) "▴ キーボード" else "▾",
            color = if (collapsed) ZtsGreen else ZtsBorder,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * かな漢字変換の候補バー。キーボードの上に重ねて浮かせる (端末画面の下端にかぶせる)。
 * これによりキーボード本体の高さ・キーサイズは一切変わらない。
 *
 * 左端: 入力中ひらがな (タップで生のまま確定)。続いて変換/予測候補 (タップで確定)。
 * composing が空のときは何も描かない。
 */
@Composable
private fun CandidateBar(
    composing: ComposingState,
    modifier: Modifier = Modifier
) {
    if (!composing.isActive) return
    val candidates = composing.candidates
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 入力中ひらがな (タップで生のまま確定)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsGreen, RoundedCornerShape(6.dp))
                .clickable { composing.commitRaw() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = composing.text,
                color = ZtsGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        // 変換 / 予測候補 (タップで確定)
        candidates.forEach { cand ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgCard)
                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                    .clickable { composing.commit(cand) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = cand,
                    color = ZtsTextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 選択コピー / 最新位置へ戻るボタンなどのフローティング表示。
 */
@Composable
private fun ScrollIndicators(
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val scrollOffset by session.scrollOffset.collectAsState()
    val selection by session.selection.collectAsState()

    Box(modifier = modifier) {
        if (selection != null) {
            // 「コピー」フローティングボタン (中央下)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ZtsGreen)
                    .clickable {
                        session.copySelectionToClipboard()
                        session.clearSelection()
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "コピー",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else if (scrollOffset > 0) {
            // スクロール位置インジケータ (右上、小、半透明)。今どれだけ遡っているか。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsBgCard.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "↑${scrollOffset}行",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            // 「最新へ↓」薄ボタン (右下)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ZtsBgCard.copy(alpha = 0.82f))
                    .border(1.dp, ZtsGreen.copy(alpha = 0.6f), CircleShape)
                    .clickable { session.jumpToBottom() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓",
                    color = ZtsGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
