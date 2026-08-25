package com.zerotoship.z2term.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.distro.DistroBundle
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.legal.LicensesDialog
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.RootProbe
import com.zerotoship.z2term.service.NotificationLogService
import com.zerotoship.z2term.service.PasswordWatchAdmin
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.ScreenTimeout
import com.zerotoship.z2term.service.SmsLogReceiver
import com.zerotoship.z2term.service.SystemEventService
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.backup.AutoBackup
import com.zerotoship.z2term.service.NetGuard
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.BatteryGuard
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.settings.RootfsCacheCleaner
import com.zerotoship.z2term.settings.SettingsGroup
import com.zerotoship.z2term.settings.SettingsGroupStore
import com.zerotoship.z2term.settings.ShellPrompt
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import com.zerotoship.z2term.ui.components.ResidentActionDialog
import com.zerotoship.z2term.ui.terminal.Guide
import com.zerotoship.z2term.ui.terminal.NoOsSettingsNotice
import com.zerotoship.z2term.ui.terminal.ToolbarButtons
import com.zerotoship.z2term.ui.terminal.guideDesc
import com.zerotoship.z2term.ui.terminal.keyboard.KeyLayoutJson
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardFace
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.UserDictStore
import com.zerotoship.z2term.ui.terminal.keyboard.asTemplate
import com.zerotoship.z2term.ui.terminal.keyboard.asciiKeyLayout
import com.zerotoship.z2term.ui.terminal.keyboard.newKeyLayoutId
import com.zerotoship.z2term.ui.terminal.keyboard.nextActiveAfterRemove
import com.zerotoship.z2term.ui.terminal.keyboard.removeLayout
import com.zerotoship.z2term.ui.terminal.keyboard.renameLayout
import com.zerotoship.z2term.ui.terminal.keyboard.uniqueKeyLayoutName
import com.zerotoship.z2term.ui.terminal.keyboard.upsertLayout
import com.zerotoship.z2term.ui.terminal.stopEverythingAndQuit
import com.zerotoship.z2term.ui.theme.TerminalFontOption
import com.zerotoship.z2term.ui.theme.TerminalFontOptions
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning
import com.zerotoship.z2term.ui.theme.rememberTerminalFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 設定ページ (全画面)。従来は下から重なる ModalBottomSheet だったが、「別ページ」として
 * 全画面に表示する (要望)。戻る矢印 / システムバックで前の画面へ戻る。
 *
 * 公開する設定項目:
 *  - ターミナルテーマ (AvailableThemes)
 *  - フォントファミリー (TerminalFontOptions)
 *  - フォントサイズ (sp、8〜32)
 *  - スクロールバック行数 (500〜50000)
 *  - ディストロ (DistroSpec.ALL)
 *  - 全角曖昧文字を 2 セル扱い (EAW Ambiguous)
 *  - 起動時に流す init コマンド
 *
 * アクションボタン:
 *  - 端末リセット (画面クリア + 再起動)。画面クリア単体は CTRL+L で行える。
 *
 * クリーンインストールは distro / GUI 各「切替」セクションのチェックへ統合した
 * (チェック ON → 対象を選ぶ/GUI を開く で入れ直す。起動・再起動でチェックは外れる)。
 *
 * 値は変更と同時に `session.set*` を呼び DataStore に書き込まれる。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    session: TerminalSession,
    onDismiss: () -> Unit,
    onEditCustomTheme: () -> Unit = {},
    /** メンテナンス →「案内を表示」で選ばれた案内。呼び出し側が端末の上に出す。 */
    onShowGuide: (Guide) -> Unit = {}
) {
    val settings by session.settingsFlow.collectAsState()
    // このタブが実際に起動したエンジン (設定値ではなく実起動結果。信頼できるエンジン表示用)。
    val actualEngine by session.actualEngine.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // distro 切替でダウンロードが要るとき、確認ダイアログの対象 spec を保持 (M8-6 T7)。
    var pendingDistroSwitch by remember { mutableStateOf<DistroSpec?>(null) }
    // Konsole × Alpine は成立しない組み合わせ (0.8.353)。どちら側から踏んだかで文面と
    // 出せる逃げ道が変わるので、踏んだ側を覚えておく ("distro" = Alpine を選ぼうとした /
    // "terminal" = Alpine のまま Konsole を選ぼうとした)。
    var konsoleConflict by remember { mutableStateOf<String?>(null) }
    // 確認ダイアログがクリーンインストール (rootfs + DLキャッシュ削除) かどうか。
    var pendingCleanInstall by remember { mutableStateOf(false) }
    // 「クリーンインストール」チェック。ON のまま OS を選ぶとその OS を入れ直す (シート内ローカル)。
    var distroCleanArmed by remember { mutableStateOf(false) }
    // OS が 1 つも入っていないか (0.8.342)。true の間だけ上部に案内を固定する。
    // ⚠ **端末の状態が変わるたびに見直す**。ここから OS を入れると端末が起動するので、
    // それが「入れ終わった合図」になり、シートを開いたままでも案内が消える。
    val terminalUiState by session.uiState.collectAsState()
    var noOs by remember { mutableStateOf(false) }
    LaunchedEffect(terminalUiState.state, settings.distroId) { noOs = !session.hasAnyDistro() }
    // 上の案内から飛ぶ先 = Linux環境グループの先頭位置 (スクロール領域の先頭からの距離)。
    // スクロール中に測っても一定の値になるよう、そのときのスクロール量を足して持つ。
    var linuxGroupY by remember { mutableStateOf(0) }
    // IME 学習履歴の管理シート。非 null の間 [ImeHistorySheet] を表示する (キーボードパッチ)。
    var imeHistoryOpen by remember { mutableStateOf(false) }
    var serversOpen by remember { mutableStateOf(false) }
    // 自動化ルール (z2-when) の管理シート。📜 の「自動化」タブと同じ中身をここからも開ける。
    var whenRulesOpen by remember { mutableStateOf(false) }
    // 持ち出し / 引き継ぎ (0.8.239)。秘密を含めるときだけ合言葉を要る形にする。
    var backupExportOpen by remember { mutableStateOf(false) }
    var backupImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 常駐サーバーが動いている間は🔒トグルをロックする (ツールバーの keepAliveToolbarItem と同じ扱い)。
    // ボタンを隠している人はここが唯一の🔒操作口なので、出口の終了ダイアログもここに出す (0.8.211)。
    var serversRunning by remember { mutableStateOf(ServerDaemonManager.isRunning) }
    var residentDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { serversRunning = ServerDaemonManager.isRunning; delay(1000) }
    }
    // 画面の向き。キーボード高さスライダーを縦/横で自動切替するために監視する
    // (configChanges 宣言済みの Activity でも確実に届くよう View の実寸で判定)。
    val rootView = LocalView.current
    var isLandscape by remember { mutableStateOf(rootView.width > rootView.height) }
    DisposableEffect(rootView) {
        val listener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val landscape = v.width > v.height
            if (landscape != isLandscape) isLandscape = landscape
        }
        rootView.addOnLayoutChangeListener(listener)
        isLandscape = rootView.width > rootView.height
        onDispose { rootView.removeOnLayoutChangeListener(listener) }
    }
    // OS データ削除: 再スキャン用カウンタ + 削除確認ダイアログ対象 distro id。
    var osDataRefresh by remember { mutableStateOf(0) }
    var pendingOsDelete by remember { mutableStateOf<String?>(null) }
    // キャッシュ削除: 再集計用カウンタ + 削除確認ダイアログの表示フラグ。
    var cacheRefresh by remember { mutableStateOf(0) }
    var pendingCacheClear by remember { mutableStateOf(false) }
    // 設定の初期化 (デフォルトに戻す) 確認ダイアログの表示フラグ。
    var pendingReset by remember { mutableStateOf(false) }
    // 端末リセットの確認。タブが動作中かどうか・タブが何枚あるかに関係なく**常に**確認を挟む
    // (「リセットボタンとしての確認」なので、状態によって出たり出なかったりさせない)。
    var pendingTerminalReset by remember { mutableStateOf(false) }
    // タスクキル相当の完全停止。常駐サーバーの有無に関係なくメンテナンスから使える出口。
    var pendingTaskKill by remember { mutableStateOf(false) }
    // グループ (アコーディオン) の開閉状態を DataStore から読み込む (初回のみ実行)。
    SettingsGroupStore.ensureLoaded(context)
    // 掃除できる rootfs 内キャッシュ + アプリ一時をバックグラウンドで走査 (サイズ降順)。
    // null = 走査前 (…表示)。削除後は cacheRefresh をインクリメントして再走査する。
    val cacheItems by produceState<List<RootfsCacheCleaner.Item>?>(null, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            RootfsCacheCleaner.scan(java.io.File(context.filesDir, "distros"), context.cacheDir)
        }
    }
    val cacheTotal = cacheItems?.sumOf { it.bytes }
    // L1: 電池最適化の除外状態。システム設定から戻った時 (ON_RESUME) に再判定して
    // トグル表示を実態に同期させる (除外の追加/解除はシステム UI 側で行われるため)。
    var batteryIgnoring by remember { mutableStateOf(BatteryGuard.isIgnoring(context)) }
    DisposableEffect(context) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryIgnoring = BatteryGuard.isIgnoring(context)
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    // root セルフテスト実行中フラグ (連打防止 + ボタン表記切替)。
    var rootProbing by remember { mutableStateOf(false) }
    // root セルフテストを (再)実行する共通処理。成功で chroot を解放する。
    // 元の挙動では 7タップ解放の瞬間に 1 度だけ走り、su 許可を拒否すると二度と
    // chroot を選べなくなっていた。明示ボタンからも呼べるようにして、拒否後でも
    // 何度でも再試行できるようにする (explicit=true のときだけ失敗をトーストで通知)。
    val runRootProbe: (Boolean) -> Unit = run@{ explicit ->
        if (rootProbing) return@run
        rootProbing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { ProotLauncher(context).probeRootChroot() }
            rootProbing = false
            when {
                result is RootProbe.Ok -> {
                    session.setRootChrootUnlocked(true)
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_root_unlock_ok),
                        Toast.LENGTH_LONG
                    ).show()
                }
                explicit -> {
                    // 失敗理由を切り分けて伝える。NoRoot = su 未許可/未検出 (許可ダイアログを許可)。
                    // ChrootBlocked = root は取れたが chroot 実行が SELinux/rootfs 等で失敗 (detail を表示)。
                    val msg = when (result) {
                        is RootProbe.ChrootBlocked ->
                            context.getString(R.string.settings_root_chroot_blocked, result.detail)
                        else ->
                            context.getString(R.string.settings_root_unlock_failed)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // 全画面の「別ページ」として表示する。背景はバー裏まで塗りつつ、中身はシステムバー
    // (上=ステータス / 下=ナビゲーション) の内側に収める。戻る矢印 / システムバックで前へ戻る。
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = ZtsBgPrimary,
        contentColor = ZtsTextPrimary
    ) {
        BackHandler(onBack = onDismiss)
        Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(onBack = onDismiss)
        // OS が 1 つも入っていない間は、上部に案内を固定する (0.8.342・利用者の判断)。
        // ⚠ **スクロール領域の外**に置くこと。中に入れると下へスクロールした時点で見えなくなり、
        // 「設定画面まで来たのにどの項目か分からない」という元の詰まりに戻る。
        // 押すと Linux環境 のセクションまで運ぶ (項目が多いので、開くだけでは辿り着けない)。
        if (noOs) {
            NoOsSettingsNotice(
                onGoToDistro = {
                    scope.launch {
                        // ⚠ Linux環境グループは既定で**閉じている** ([SettingsGroup.LINUX])。
                        // 開かずに運ぶと見出しだけ見えて中身が無く、詰まりが解けない。
                        SettingsGroupStore.setOpen(SettingsGroup.LINUX, true)
                        // 開いた分の高さがレイアウトに反映されるまで待つ。反映前に動かすと
                        // スクロール可能量が足りず途中で止まる。待てないときは諦めて動かす。
                        withTimeoutOrNull(300) {
                            snapshotFlow { scrollState.maxValue }.first { it >= linuxGroupY }
                        }
                        scrollState.animateScrollTo(linuxGroupY)
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 上部バーの下を縦スクロール可能に (項目が画面高を超えても一番下まで到達できる)
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsGroupSection(SettingsGroup.DISPLAY) {
                Section(title = stringResource(R.string.settings_section_theme)) {
                    val customTheme by CustomThemeStore.theme.collectAsState()
                    ThemeChipRow(
                        themes = AvailableThemes + listOfNotNull(customTheme),
                        selectedName = settings.themeName,
                        onSelect = { session.setThemeName(it) }
                    )
                    ActionButton(
                        label = if (customTheme == null) stringResource(R.string.settings_create_custom_theme)
                                else stringResource(R.string.settings_edit_custom_theme),
                        onClick = onEditCustomTheme
                    )
                    Text(
                        text = stringResource(R.string.settings_custom_theme_note),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Section(title = stringResource(R.string.settings_section_font_family)) {
                    FontChipRow(
                        options = TerminalFontOptions.ALL,
                        selectedId = settings.fontId,
                        isAvailable = { TerminalFontOptions.isAvailable(context, it) },
                        onSelect = { session.setFontId(it) }
                    )
                }

                SliderField(
                    title = stringResource(R.string.settings_section_font_size),
                    value = settings.fontSizeSp,
                    range = AppSettings.MIN_FONT_SIZE_SP..AppSettings.MAX_FONT_SIZE_SP,
                    steps = 28,  // 4..32 を 1sp 刻み = 29 値 = 28 steps
                    valueLabel = { "${it.toInt()} sp" },
                    onChange = { session.setFontSize(it) }
                )

                SliderField(
                    title = stringResource(R.string.settings_section_scrollback),
                    value = settings.scrollbackLines.toFloat(),
                    range = AppSettings.MIN_SCROLLBACK_LINES.toFloat()..AppSettings.MAX_SCROLLBACK_LINES.toFloat(),
                    steps = 49,  // 500..50000 を 1000 刻みで 50 値
                    valueLabel = { "${it.toInt()} 行" },
                    onChange = { session.setScrollbackLines(it.toInt()) }
                )

                ToggleField(
                    title = stringResource(R.string.settings_ambiguous_width),
                    description = stringResource(R.string.settings_ambiguous_width_desc),
                    checked = settings.ambiguousAsWide,
                    onChange = { session.setAmbiguousAsWide(it) }
                )

                // つまずきの言い換え (0.8.237)。既定 ON だが、お節介と感じたらすぐ切れる場所に置く。
                ToggleField(
                    title = stringResource(R.string.settings_terminal_hints),
                    description = stringResource(R.string.settings_terminal_hints_desc),
                    checked = settings.terminalHintsEnabled,
                    onChange = { session.setTerminalHintsEnabled(it) }
                )

                // ツールバー (端末上部バー) に出すボタンを選ぶ。使わないボタンを消せるので、
                // 機能追加でボタンが増えても各自の画面は増えない (要望)。
                // 並べ替えは今まで通り端末画面でボタンを長押し→左右ドラッグ。
                Section(title = stringResource(R.string.settings_section_toolbar)) {
                    Text(
                        text = stringResource(R.string.settings_toolbar_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ToolbarVisibilityRow(
                        hidden = settings.toolbarHidden,
                        onToggle = { id ->
                            session.setToolbarHidden(ToolbarButtons.toggleHidden(settings.toolbarHidden, id))
                        }
                    )
                    // 隠したボタンのうちトグル系 (🔅 画面消灯ロック / 🔒 常駐) は、
                    // ツールバー以外に切り替える場所が無い。隠しているときだけここに出す。
                    val hiddenIds = ToolbarButtons.parseHidden(settings.toolbarHidden)
                    if (ToolbarButtons.SCREEN_ON in hiddenIds) {
                        ToggleField(
                            title = stringResource(R.string.tb_screen_on),
                            description = stringResource(R.string.settings_toolbar_hidden_toggle_desc),
                            checked = settings.keepScreenOn,
                            onChange = { session.setKeepScreenOn(it) }
                        )
                    }
                    if (ToolbarButtons.KEEP_ALIVE in hiddenIds) {
                        // ツールバーの🔒と同じく、常駐サーバー稼働中はトグル不可にして終了の出口を出す
                        // (0.8.211)。ここを素通しにすると、ボタンを隠している人だけ「常駐に閉じ込められて
                        // セッションを終了できない」状態になっていた。
                        ToggleField(
                            title = stringResource(R.string.tb_keep_alive),
                            description = stringResource(R.string.settings_toolbar_hidden_toggle_desc),
                            // ロック中は**必ず ON 表示**にする。常駐サーバーが動いている間はプロセスが
                            // 生き続けるので、設定値が OFF でも実際には常駐している。ツールバーの🔒も
                            // ロック中は active=true (ON) で薄く見せており、ここだけ OFF のまま薄くなって
                            // 食い違っていた (実機フィードバック 2026-07-25)。
                            checked = settings.keepAliveService || serversRunning,
                            onChange = { session.setKeepAliveService(it) },
                            locked = serversRunning,
                            onLockedTap = { residentDialogOpen = true }
                        )
                    }
                }
            }

            SettingsGroupSection(SettingsGroup.KEYBOARD) {
                // キーボードサイズ (高さ) は頻繁に調整するため表示設定の近く (上部) に置く。
                Section(title = stringResource(R.string.settings_section_keyboard_size)) {
                    // キーボードの高さは縦画面・横画面で別々に保持し、向きに合わせて自動で切り替わる。
                    SliderField(
                        title = stringResource(
                            if (isLandscape) R.string.settings_kb_height_landscape
                            else R.string.settings_kb_height_portrait
                        ),
                        value = if (isLandscape) settings.landscapeKeyboardHeightDp
                                else settings.portraitKeyboardHeightDp,
                        range = if (isLandscape)
                            AppSettings.MIN_LANDSCAPE_KB_HEIGHT_DP..AppSettings.MAX_LANDSCAPE_KB_HEIGHT_DP
                        else
                            AppSettings.MIN_PORTRAIT_KB_HEIGHT_DP..AppSettings.MAX_PORTRAIT_KB_HEIGHT_DP,
                        steps = if (isLandscape) 14 else 12,  // 20dp 刻み
                        valueLabel = { "%.0fdp".format(it) },
                        onChange = {
                            if (isLandscape) session.setLandscapeKeyboardHeightDp(it)
                            else session.setPortraitKeyboardHeightDp(it)
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_kb_height_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // 配置 (左/下/右) とサイドキーボード幅は横画面でのみ意味があるので横画面の時だけ出す。
                    if (isLandscape) {
                        val posOptions = listOf(
                            AppSettings.LANDSCAPE_KB_LEFT,
                            AppSettings.LANDSCAPE_KB_BOTTOM,
                            AppSettings.LANDSCAPE_KB_RIGHT
                        )
                        ChipRow(
                            options = posOptions,
                            labels = mapOf(
                                AppSettings.LANDSCAPE_KB_LEFT to stringResource(R.string.settings_landscape_kb_left),
                                AppSettings.LANDSCAPE_KB_BOTTOM to stringResource(R.string.settings_landscape_kb_bottom),
                                AppSettings.LANDSCAPE_KB_RIGHT to stringResource(R.string.settings_landscape_kb_right)
                            ),
                            selected = settings.landscapeKeyboardPosition,
                            onSelect = { session.setLandscapeKeyboardPosition(it) }
                        )
                        Text(
                            text = stringResource(R.string.settings_landscape_keyboard_desc),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        SliderField(
                            title = stringResource(R.string.settings_landscape_kb_width),
                            value = settings.landscapeKeyboardWidthDp,
                            range = AppSettings.MIN_LANDSCAPE_KB_WIDTH_DP..AppSettings.MAX_LANDSCAPE_KB_WIDTH_DP,
                            steps = 13,  // 280 → 700 を 30dp 刻み (15 段)
                            valueLabel = { "%.0fdp".format(it) },
                            onChange = { session.setLandscapeKeyboardWidthDp(it) }
                        )
                    }
                }

                // キーボードスタイル (配列) も入力系なので表示設定の近くに置く。
                Section(title = stringResource(R.string.settings_section_keyboard_style)) {
                    ChipRow(
                        options = KeyboardStyle.ALL.map { it.id },
                        labels = KeyboardStyle.ALL.associate { it.id to stringResource(it.displayNameRes) },
                        selected = settings.keyboardStyleId,
                        onSelect = { session.setKeyboardStyleId(it) }
                    )
                    // 数字だけの面 (0.8.305)。OFF なら巡回は「あ → A → あ」の従来どおりで、
                    // キーの見た目も切替キーの行き先も 0.8.304 と変わらない。
                    ToggleField(
                        title = stringResource(R.string.settings_keyboard_number_face),
                        description = stringResource(R.string.settings_keyboard_number_face_desc),
                        checked = settings.keyboardNumberFace,
                        onChange = { session.setKeyboardNumberFace(it) }
                    )
                    // 面の切りかえ順。⚠ **面が 3 つあるときだけ**出す — 2 面では「もう片方へ」
                    // しか無く、巡回順という考えそのものが成り立たない (英語では日本語面が
                    // 出ないので、数字面を入れても 2 面のまま)。
                    val kanaFaceAvailable = LocaleHelper.language(context) == LocaleHelper.LANG_JA
                    if (settings.keyboardNumberFace && kanaFaceAvailable) {
                        Text(
                            text = stringResource(R.string.settings_keyboard_face_order),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        ChipRow(
                            options = KeyboardFace.ORDERS.map { KeyboardFace.orderIdOf(it) },
                            labels = KeyboardFace.ORDERS.associate { order ->
                                KeyboardFace.orderIdOf(order) to
                                    order.joinToString(" → ") { it.switchLabel }
                            },
                            selected = settings.keyboardFaceOrder,
                            onSelect = { session.setKeyboardFaceOrder(it) }
                        )
                    }
                    ToggleField(
                        title = stringResource(R.string.settings_keyboard_toggle_bar),
                        description = stringResource(R.string.settings_keyboard_toggle_bar_desc),
                        checked = settings.keyboardToggleBar,
                        onChange = { session.setKeyboardToggleBar(it) }
                    )
                    // OS のキーボードに切り替えたときだけ出る補助キー (ESC/TAB/CTRL/矢印…) の表示。
                    // OS の IME が自前で同じキーを持っている場合は二重になるので消せるようにする (要望)。
                    ToggleField(
                        title = stringResource(R.string.settings_special_key_bar),
                        description = stringResource(R.string.settings_special_key_bar_desc),
                        checked = settings.specialKeyBar,
                        onChange = { session.setSpecialKeyBar(it) }
                    )
                }

                // 自分で作るキー配列 (0.8.408・段階 2)。⚠ キーボードスタイル (高さ・字の
                // 大きさ) は**そのまま残す** — 配列は「並び・幅・割り当て」だけを持ち、
                // 大きさは今までどおり上の設定が全部の面にまとめて効く。
                KeyLayoutSection(settings, session)

                // 内蔵キーボードを OS の入力方法として出す (Z2ImeService)。⚠ 有効化も選択も
                // ユーザーの操作でしか行えない (OS の決まり) ので、ここは 2 つの画面へ送るだけ。
                // 「有効にする」→ OS の入力方法一覧、「切り替える」→ キーボード選択ダイアログ。
                // キーボードの設定なので**キーボードのグループ**に置く (以前は自動化の下にあり、
                // キーボードを探した人が見つけられなかった)。
                Section(title = stringResource(R.string.settings_section_ime)) {
                    Text(
                        text = stringResource(R.string.settings_ime_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_ime_enable),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_ime_pick),
                        onClick = {
                            runCatching {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as android.view.inputmethod.InputMethodManager
                                imm.showInputMethodPicker()
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_ime_note),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // IME 学習履歴 (キーボードパッチ): 件数表示 + 管理ボタン (シートを開く)
                Section(title = stringResource(R.string.settings_section_ime_history)) {
                    val historyVersion by com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore.versionFlow.collectAsState()
                    // approximateCount は version 変化のたびに再評価される (collectAsState 経由)
                    val count = remember(historyVersion) {
                        com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore.approximateCount()
                    }
                    Text(
                        text = pluralStringResource(R.plurals.settings_ime_history_count, count, count),
                        color = ZtsTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_ime_history_open),
                        onClick = { imeHistoryOpen = true }
                    )
                }

                // ユーザー辞書 (SKK 形式のテキストを取り込んで変換候補に混ぜる)。
                // 学習履歴の隣に置く — どちらも「自分の語を IME に覚えさせる」設定なので。
                Section(title = stringResource(R.string.settings_section_user_dict)) {
                    val dictFiles by UserDictStore.files.collectAsState()
                    val dictWords by UserDictStore.wordCount.collectAsState()
                    Text(
                        text = stringResource(R.string.settings_user_dict_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_user_dict_count, dictWords, dictFiles.size
                        ),
                        color = ZtsTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val dictPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) scope.launch {
                            // 取り込みの結果は Toast で返す。⚠ 形式違い/大きすぎは黙って
                            // 無視せず必ず伝える (入ったつもりで変換が変わらないと原因を追えない)。
                            val msg = when (val r = UserDictStore.import(context, uri)) {
                                is UserDictStore.ImportResult.Success ->
                                    context.getString(R.string.settings_user_dict_added, r.name, r.words)
                                UserDictStore.ImportResult.TooLarge ->
                                    context.getString(R.string.settings_user_dict_too_large)
                                UserDictStore.ImportResult.NoEntries ->
                                    context.getString(R.string.settings_user_dict_no_entries)
                                is UserDictStore.ImportResult.Failed ->
                                    context.getString(R.string.settings_user_dict_failed, r.message)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    ActionButton(
                        label = stringResource(R.string.settings_user_dict_add),
                        // ⚠ MIME は絞らない。テキストなのに provider が octet-stream を返すことが
                        // あり、text/* だけだと選べないファイルが出る。
                        onClick = { runCatching { dictPicker.launch(arrayOf("*/*")) } }
                    )
                    dictFiles.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_user_dict_file, f.name, f.words),
                                color = ZtsTextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            ActionButton(
                                label = stringResource(R.string.settings_user_dict_remove),
                                danger = true,
                                onClick = { scope.launch { UserDictStore.remove(context, f.name) } }
                            )
                        }
                    }
                }

                // 言語スイッチ。アプリ内で「端末に合わせる/日本語/English」を切替える
                // (OS Locale ではなく独自管理)。一度決めれば滅多に変えないため下部に配置。
                // 変更時は Activity を recreate() する。
                // ⚠ 選択状態は **languageSetting** (保存値) で持つ。`language` は解決後の ja/en を
                //   返すので、そちらを使うと「端末に合わせる」を選んでも ja か en の側が点いて見える。
                Section(title = stringResource(R.string.settings_section_language)) {
                    val currentLang = remember { mutableStateOf(LocaleHelper.languageSetting(context)) }
                    ChipRow(
                        options = listOf(
                            LocaleHelper.LANG_SYSTEM, LocaleHelper.LANG_JA, LocaleHelper.LANG_EN
                        ),
                        labels = mapOf(
                            LocaleHelper.LANG_SYSTEM to stringResource(R.string.settings_language_system),
                            LocaleHelper.LANG_JA to "日本語",
                            LocaleHelper.LANG_EN to "English"
                        ),
                        selected = currentLang.value,
                        onSelect = { lang ->
                            if (lang != currentLang.value) {
                                LocaleHelper.setLanguage(context, lang)
                                currentLang.value = lang
                                // 反映には Activity の再生成が必要。
                                val activity = context as? android.app.Activity
                                activity?.recreate()
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_section_language_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            SettingsGroupSection(
                SettingsGroup.LINUX,
                // OS 未導入の案内 ([NoOsSettingsNotice]) から飛ぶ先。スクロール量を足して
                // 「先頭からの距離」にする (verticalScroll は子の位置をスクロール分ずらすため)。
                modifier = Modifier.onGloballyPositioned {
                    linuxGroupY = it.positionInParent().y.toInt() + scrollState.value
                }
            ) {
                // バックグラウンド常駐トグルはツールバーの 🔒 ロックアイコンへ移動した (要望)。
                // 設定からは出さない (ツールバーで ON/OFF する)。

                Section(title = stringResource(R.string.settings_section_distro)) {
                    ChipRow(
                        options = DistroSpec.ALL.map { it.id },
                        labels = DistroSpec.ALL.associate {
                            it.id to (it.displayName + (it.approxDownload?.let { s -> " ⬇$s" } ?: ""))
                        },
                        selected = settings.distroId,
                        onSelect = { id ->
                            val spec = DistroSpec.byId(id)
                            // ⚠ 動かないと分かっている組み合わせは成立させない (0.8.353)。
                            // 選べてしまうと「GUI を開いたが真っ黒のまま」になり、理由が
                            // どこにも出ない。ここで止めて、その場で端末を替えられるようにする。
                            if (GuiTerminal.isUnsupported(settings.guiTerminalId, id)) {
                                konsoleConflict = "distro"
                            } else if (distroCleanArmed && spec != null) {
                                // クリーンインストール: rootfs + DL キャッシュを消して入れ直す。
                                // 必ず再 DL が走るので、確認 ON なら先にダイアログを出す。
                                if (settings.confirmBeforeDownload) {
                                    pendingDistroSwitch = spec
                                    pendingCleanInstall = true
                                } else {
                                    distroCleanArmed = false
                                    session.cleanInstallDistro(id)
                                    onDismiss()
                                }
                            } else {
                                val extracted = java.io.File(
                                    context.filesDir, "distros/$id/bin"
                                ).exists()
                                // 未展開なら初回切替でネットから DL が走る。
                                val needsDownload = spec != null && !extracted
                                // ⚠ **既に選ばれている OS でも、入っていなければ押せる** (0.8.314)。
                                // 初回は既定 (Alpine) が選択済みなのに未導入という状態から
                                // 始まるので、`id != 選択中` で弾くと**その OS だけ入れられなかった**
                                // (自動ダウンロードの催促をやめた分、ここが唯一の入口になる)。
                                val alreadyUsable = id == settings.distroId && extracted
                                if (alreadyUsable) {
                                    // 選択中でそのまま使える: 何もしない。
                                } else if (needsDownload && settings.confirmBeforeDownload) {
                                    pendingDistroSwitch = spec   // 確認ダイアログを出す
                                    pendingCleanInstall = false
                                } else {
                                    // 切替を保存して override 付きで再起動 (settingsFlow 反映待ちの
                                    // race を回避)。展開済みなら DL は走らない。
                                    session.switchDistro(id)
                                    onDismiss()
                                }
                            }
                        }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_clean_install),
                        description = stringResource(R.string.settings_clean_install_desc),
                        checked = distroCleanArmed,
                        onChange = { distroCleanArmed = it }
                    )
                    Text(
                        text = stringResource(R.string.settings_distro_note),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // OS データ削除 (ストレージ解放)。rootfs を持つ OS を列挙し、不要なものを削除できる。
                // 使用中の OS は壊れた稼働状態を避けるため削除不可 (入れ直しはクリーンインストールで)。
                //
                // OS 一覧はディレクトリ列挙だけで即座に確定させ (行数=セクション高さを最初から固定)、
                // 重い使用量計算 (rootfs 全走査) だけを後から非同期で埋める。こうしないと、
                // スクロール中に数秒後の集計完了で行数が増え、表示位置が勝手にずれてしまう。
                val installedOs = remember(osDataRefresh) { listInstalledOs(context) }

                val osSizes by produceState(emptyMap<String, Long>(), osDataRefresh, installedOs) {
                    value = withContext(Dispatchers.IO) { computeOsSizes(installedOs, context) }
                }

                Section(title = stringResource(R.string.settings_section_delete_os)) {
                    Text(
                        text = stringResource(R.string.settings_delete_os_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (installedOs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_delete_os_empty),
                            color = ZtsTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        installedOs.forEach { os ->
                            OsDataRow(
                                name = os.displayName,
                                // 集計前は "…"。文字数は変わっても 1 行高は不変なので位置ずれは起きない。
                                sizeLabel = osSizes[os.id]?.let { formatStorageSize(it) } ?: "…",
                                isActive = os.id == settings.distroId,
                                onDelete = { pendingOsDelete = os.id }
                            )
                        }
                    }
                }

                Section(title = stringResource(R.string.settings_section_login_shell)) {
                    // 現ディストロの rootfs に各シェルバイナリが実在するか調べる。
                    // 未インストールのシェルを選んでも反映されず、起動時に既定シェル →
                    // /bin/sh へ自動フォールバックするため、その旨を明示する。
                    // rootfs 未展開 (DL 中など) は判定不能なので警告を出さない。
                    val rootfsReady = remember(settings.distroId) {
                        java.io.File(context.filesDir, "distros/${settings.distroId}/bin").exists()
                    }
                    val shellInstalled = remember(settings.distroId) {
                        AppSettings.AVAILABLE_SHELLS.associateWith { shell ->
                            java.io.File(
                                context.filesDir,
                                "distros/${settings.distroId}/${shell.trimStart('/')}"
                            ).exists()
                        }
                    }
                    ChipRow(
                        options = AppSettings.AVAILABLE_SHELLS,
                        labels = AppSettings.AVAILABLE_SHELLS.associateWith { shell ->
                            if (rootfsReady && shellInstalled[shell] == false)
                                stringResource(R.string.settings_shell_uninstalled_suffix, shell)
                            else shell
                        },
                        selected = settings.loginShell,
                        onSelect = { session.setLoginShell(it) }
                    )
                    if (rootfsReady && shellInstalled[settings.loginShell] == false) {
                        Text(
                            text = stringResource(R.string.settings_shell_warning, settings.loginShell),
                            color = ZtsWarning,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_shell_info),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // ボタンの onClick は Composable ではないので、出す文言は先に解決しておく。
                val appliedPromptOk = stringResource(R.string.settings_prompt_applied)
                val appliedPromptNg = stringResource(R.string.settings_prompt_failed)
                val appliedPromptNoOs = stringResource(R.string.settings_prompt_no_os)
                val removedPromptOk = stringResource(R.string.settings_prompt_removed)
                val removedPromptNone = stringResource(R.string.settings_prompt_removed_none)

                // シェルのプロンプト。サンプルを選ぶ → 中身が出る → その場で直す → rc へ適用。
                // ⚠ アプリの設定として抱え込まず **rootfs の rc ファイルに書く** ([ShellPrompt])。
                //   後から `vi ~/.bashrc` で直せることに意味があるので、真実は常にファイルにある。
                Section(title = stringResource(R.string.settings_section_prompt)) {
                    // ⚠ **rc の置き場は rootfs ではなく共有ホーム**。`ProotLauncher` が
                    //   `HOME=/root` を `filesDir/shared_home` に bind するので、
                    //   `distros/<id>/root/` へ書いても誰も読まない (0.8.364 で踏んだ)。
                    val promptHomeDir = remember { java.io.File(context.filesDir, "shared_home") }
                    // OS が入っているかだけは rootfs 側で見る (無ければ書いても意味が無い)。
                    val rootfsDir = remember(settings.distroId) {
                        java.io.File(context.filesDir, "distros/${settings.distroId}")
                    }
                    // 対象シェルの初期値は「ログインシェル」設定から推測する。別々に選ばせると、
                    // ash で使っているのに bash の rc へ書いて「適用したのに変わらない」になる。
                    var promptShell by remember(settings.loginShell) {
                        mutableStateOf(
                            when {
                                settings.loginShell.endsWith("zsh") -> ShellPrompt.Shell.ZSH
                                settings.loginShell.endsWith("bash") -> ShellPrompt.Shell.BASH
                                else -> ShellPrompt.Shell.SH
                            }
                        )
                    }
                    var promptPreset by remember { mutableStateOf(ShellPrompt.Preset.ARROW) }
                    var promptRightClock by remember { mutableStateOf(false) }
                    var promptDraft by remember { mutableStateOf("") }
                    var promptResult by remember { mutableStateOf<String?>(null) }
                    val presetLabels = mapOf(
                        ShellPrompt.Preset.PLAIN.id to stringResource(R.string.settings_prompt_preset_plain),
                        ShellPrompt.Preset.USER_HOST.id to stringResource(R.string.settings_prompt_preset_user_host),
                        ShellPrompt.Preset.ARROW.id to stringResource(R.string.settings_prompt_preset_arrow),
                        ShellPrompt.Preset.BOX.id to stringResource(R.string.settings_prompt_preset_box),
                        ShellPrompt.Preset.BRACKET.id to stringResource(R.string.settings_prompt_preset_bracket),
                        ShellPrompt.Preset.KALI.id to stringResource(R.string.settings_prompt_preset_kali),
                        ShellPrompt.Preset.BAR.id to stringResource(R.string.settings_prompt_preset_bar)
                    )
                    // ⚠ 既に rc へ書いてあるならそれを出す。サンプルで上書きして見せると、
                    //   前に自分で直した内容が**画面から消えたように見える**。
                    fun refill(shell: ShellPrompt.Shell, preset: ShellPrompt.Preset, keepExisting: Boolean) {
                        promptDraft = (if (keepExisting) ShellPrompt.current(promptHomeDir, shell) else null)
                            ?: ShellPrompt.body(preset, shell, promptRightClock)
                        promptResult = null
                    }
                    LaunchedEffect(promptShell) { refill(promptShell, promptPreset, keepExisting = true) }

                    ChipRow(
                        options = ShellPrompt.Shell.entries.map { it.id },
                        labels = ShellPrompt.Shell.entries.associate { it.id to it.label },
                        selected = promptShell.id,
                        onSelect = { id ->
                            promptShell = ShellPrompt.Shell.of(id)
                            refill(promptShell, promptPreset, keepExisting = true)
                        }
                    )
                    ChipRow(
                        options = ShellPrompt.Preset.entries.map { it.id },
                        labels = presetLabels,
                        selected = promptPreset.id,
                        onSelect = { id ->
                            promptPreset = ShellPrompt.Preset.of(id)
                            // サンプルを選び直したときだけは、そのサンプルで作り直す。
                            refill(promptShell, promptPreset, keepExisting = false)
                        }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_prompt_right_clock),
                        description = stringResource(R.string.settings_prompt_right_clock_desc),
                        checked = promptRightClock,
                        onChange = {
                            promptRightClock = it
                            // 切り替えたら見本を作り直す (ボックスの中身と食い違わせない)。
                            promptDraft = ShellPrompt.body(promptPreset, promptShell, it)
                            promptResult = null
                        }
                    )
                    TextField(
                        title = stringResource(R.string.settings_prompt_body, promptShell.displayPath),
                        placeholder = "PS1=...",
                        value = promptDraft,
                        onChange = { promptDraft = it; promptResult = null }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(label = stringResource(R.string.settings_prompt_apply)) {
                            promptResult = if (!rootfsDir.isDirectory) {
                                appliedPromptNoOs
                            } else if (ShellPrompt.apply(promptHomeDir, promptShell, promptDraft) != null) {
                                appliedPromptOk.format(promptShell.displayPath)
                            } else {
                                appliedPromptNg
                            }
                        }
                        ActionButton(label = stringResource(R.string.settings_prompt_reset)) {
                            val removed = ShellPrompt.clear(promptHomeDir, promptShell)
                            promptDraft = ShellPrompt.body(promptPreset, promptShell, promptRightClock)
                            promptResult = if (removed) {
                                removedPromptOk.format(promptShell.displayPath)
                            } else {
                                removedPromptNone
                            }
                        }
                    }
                    promptResult?.let {
                        Text(
                            text = it,
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_prompt_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                SshAccessHelper(session = session)

                StorageAccessHelper()

                // 外部 SD カードを proot 内へ認識させる ON/OFF + 検出されたパスの表示。
                // ON のときだけ ExternalStorageDetector を呼ぶ (OFF 時はゼロコスト)。
                // マウントの実反映は次のセッション再起動時 (proot 起動引数として渡すため)。
                Section(title = stringResource(R.string.settings_section_external_storage)) {
                    ToggleField(
                        title = stringResource(R.string.settings_external_storage_toggle),
                        description = stringResource(R.string.settings_external_storage_toggle_desc),
                        checked = settings.externalStorageEnabled,
                        onChange = { session.setExternalStorageEnabled(it) }
                    )
                    if (settings.externalStorageEnabled) {
                        val volumes = remember(settings.externalStorageEnabled) {
                            com.zerotoship.z2term.storage.ExternalStorageDetector.detect(context)
                        }
                        if (volumes.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_external_storage_none),
                                color = ZtsTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.settings_external_storage_detected),
                                color = ZtsTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            volumes.forEach { vol ->
                                Text(
                                    text = vol,
                                    color = ZtsTextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.settings_external_storage_disabled),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Section(title = stringResource(R.string.settings_section_gui_terminal)) {
                    ChipRow(
                        options = GuiTerminal.ALL.map { it.id },
                        labels = GuiTerminal.ALL.associate { it.id to it.displayName },
                        selected = settings.guiTerminalId,
                        onSelect = { id ->
                            // 逆向き (Alpine のまま Konsole を選ぶ) も同じく止める。
                            if (GuiTerminal.isUnsupported(id, settings.distroId)) {
                                konsoleConflict = "terminal"
                            } else {
                                session.setGuiTerminal(id)
                            }
                        }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_clean_install),
                        description = stringResource(R.string.settings_gui_clean_install_desc),
                        checked = settings.cleanInstallGuiArmed,
                        onChange = { session.setCleanInstallGuiArmed(it) }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_gui_audio),
                        description = stringResource(R.string.settings_gui_audio_desc),
                        checked = settings.guiAudioEnabled,
                        onChange = { session.setGuiAudioEnabled(it) }
                    )
                    Text(
                        text = stringResource(R.string.settings_gui_terminal_note),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                SliderField(
                    title = stringResource(R.string.settings_gui_magnification),
                    value = settings.guiMagnification,
                    range = AppSettings.MIN_GUI_MAGNIFICATION..AppSettings.MAX_GUI_MAGNIFICATION,
                    steps = 4,  // 0.5 / 1.0 / 1.5 / 2.0 / 2.5 / 3.0 の 6 段階 (内部 4 ステップ)
                    valueLabel = { "%.1f×".format(it) },
                    onChange = { session.setGuiMagnification(it) }
                )

                ToggleField(
                    title = stringResource(R.string.settings_confirm_download),
                    description = stringResource(R.string.settings_confirm_download_desc),
                    checked = settings.confirmBeforeDownload,
                    onChange = { session.setConfirmBeforeDownload(it) }
                )

                TextField(
                    title = stringResource(R.string.settings_init_command),
                    placeholder = stringResource(R.string.settings_init_command_placeholder),
                    value = settings.initCommand,
                    onChange = { session.setInitCommand(it) }
                )
            }

            SettingsGroupSection(SettingsGroup.AUTOMATION) {
                // 通信量の上限 (0.8.388)。使いすぎに気付くのはたいてい絞られてからなので、
                // 自分で決めた量で止まれるようにする。
                NetLimitSection(settings = settings, session = session)

                // L1: バックグラウンドでのプロセス kill 対策。電池最適化の除外トグル +
                // Android 12/13 の phantom process killing 無効化手順 (adb) の案内。
                Section(title = stringResource(R.string.settings_section_process_guard)) {
                    ToggleField(
                        title = stringResource(R.string.settings_battery_opt_toggle),
                        description = stringResource(R.string.settings_battery_opt_toggle_desc),
                        checked = batteryIgnoring,
                        // 実際の除外追加/解除はシステム UI で行う。トグル状態は ON_RESUME で同期。
                        onChange = { wantOn ->
                            if (wantOn) BatteryGuard.requestExemption(context)
                            else BatteryGuard.openOptimizationSettings(context)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_phantom_title),
                        color = ZtsTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.settings_phantom_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // コマンド例の見た目・コピー操作は他セクション (読むコマンド等) と揃える。
                    CopyableCommand(
                        label = stringResource(R.string.settings_cmd_copy_label),
                        command = BatteryGuard.PHANTOM_DISABLE_ADB
                    )
                    // 省電力モード (WakeLock/WifiLock を握らない)。0.8.309 で 📜 サーバータブから
                    // ここへ移した。⚠ **サーバーだけの設定ではない** — 🔒 バックグラウンド常駐にも
                    // 効き、自動化 (z2-when → HeadlessRun) は自前のロックを持たず**常駐側が握って
                    // いるロックへの相乗り**で動くので、反応の速さもこれで変わる。上の 2 つと同じ
                    // 「端末に眠らせるか / 起こしておくか」の設定なので、このセクションに並べる。
                    // ⛔ **`TerminalService.start` の呼び直しを落とさない**。あちらは
                    // onStartCommand でしか省電力を判定しないので (TerminalService.kt:98-106)、
                    // これが無いとトグルしても次の起動までロックが切り替わらない。
                    // 呼び直しは idempotent で、🔒 が OFF なら何も起きない。
                    ToggleField(
                        title = stringResource(R.string.settings_low_power),
                        description = stringResource(R.string.settings_low_power_desc),
                        checked = settings.serversLowPower,
                        onChange = {
                            session.setServersLowPower(it)
                            if (settings.keepAliveService) TerminalService.start(context)
                        }
                    )
                }

                // 常駐サーバー: 任意のサーバー (sshd/http/smb 等) を起動コマンドとして登録し、
                // アプリを開かず自動常駐させる。管理は専用シート (ServersSheet) で行う。
                Section(title = stringResource(R.string.settings_section_servers)) {
                    Text(
                        text = stringResource(R.string.settings_servers_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_open_servers),
                        onClick = { serversOpen = true }
                    )
                }

                // 自動化ルール (z2-when)。一覧・ON/OFF・ログ・▶試す・一時停止は 1 つの画面
                // ([WhenRulesBody]) にまとめ、常駐サーバーと同じくここからも開けるようにする。
                Section(title = stringResource(R.string.settings_section_automation_rules)) {
                    Text(
                        text = stringResource(R.string.settings_automation_rules_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_open_when_rules),
                        onClick = { whenRulesOpen = true }
                    )
                }

                // 画面の自動消灯 (z2-screen): OS 全体の「画面消灯までの時間」を期限つきで延ばす。
                // ⚠ ツールバーの🔅 (アプリを開いている間だけ) とは別物。ここは許可の状態を見せて
                // 許可画面へ送るだけで、掛ける/外すは端末側の z2-screen が担う (時間指定が要るため)。
                Section(title = stringResource(R.string.settings_section_screen_timeout)) {
                    // 許可は OS の設定画面で変わるので remember しない (キャッシュすると、許可して
                    // 戻ってきても「未許可」のままに見える)。AppOps の問い合わせだけなので毎回読む。
                    val allowed = ScreenTimeout.canWrite(context)
                    Text(
                        text = stringResource(R.string.settings_screen_timeout_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (allowed) stringResource(R.string.settings_screen_timeout_granted)
                        else stringResource(R.string.settings_screen_timeout_missing),
                        color = if (allowed) ZtsGreen else ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_screen_timeout_grant),
                        onClick = {
                            runCatching { context.startActivity(ScreenTimeout.manageIntent(context)) }
                        }
                    )
                    CopyableCommand(
                        label = stringResource(R.string.settings_screen_timeout_cmd_label),
                        command = "z2-screen keepon 1h"
                    )
                }

                // 通知検知 (汎用入口): OS の「通知アクセス」許可 + 設定 ON のとき、届いた通知を
                // ~/.z2term/notifications.jsonl へ生ログ追記する。加工・配信はユーザーがターミナル側で自由に。
                Section(title = stringResource(R.string.settings_section_notif)) {
                    val granted = remember(serversOpen, settings.notificationCaptureEnabled) {
                        NotificationManagerCompat.getEnabledListenerPackages(context)
                            .contains(context.packageName)
                    }
                    ToggleField(
                        title = stringResource(R.string.settings_notif_capture),
                        description = stringResource(R.string.settings_notif_capture_desc),
                        checked = settings.notificationCaptureEnabled,
                        onChange = { session.setNotificationCaptureEnabled(it) }
                    )
                    Text(
                        text = if (granted) stringResource(R.string.settings_notif_access_granted)
                        else stringResource(R.string.settings_notif_access_missing),
                        color = if (granted) ZtsGreen else ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_notif_grant),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )

                    // ログ保存の ON/OFF (検知とは独立)。OFF なら検知だけ行いファイルには書かない。
                    ToggleField(
                        title = stringResource(R.string.settings_notif_log),
                        description = stringResource(R.string.settings_notif_log_desc),
                        checked = settings.notificationLogEnabled,
                        onChange = { session.setNotificationLogEnabled(it) }
                    )

                    // 出力フォーマット: プリセットで埋めてから自由に編集できるテンプレート。
                    val fmtPresets = remember {
                        listOf(
                            "jsonl" to "",
                            "readable" to "[{time}] {app}\\n{title}\\n{text}\\n",
                            "line" to "{time} [{app}] {title1}: {text1}",
                            "tsv" to "{time}\\t{app}\\t{title1}\\t{text1}",
                        )
                    }
                    val fmtSelected = fmtPresets.firstOrNull { it.second == settings.notificationLogFormat }?.first ?: ""
                    ChipRow(
                        options = fmtPresets.map { it.first },
                        selected = fmtSelected,
                        labels = mapOf(
                            "jsonl" to "JSONL",
                            "readable" to stringResource(R.string.settings_notif_fmt_readable),
                            "line" to stringResource(R.string.settings_notif_fmt_line),
                            "tsv" to "TSV",
                        ),
                        onSelect = { id ->
                            session.setNotificationLogFormat(fmtPresets.first { it.first == id }.second)
                        }
                    )
                    TextField(
                        title = stringResource(R.string.settings_notif_fmt_title),
                        placeholder = "{time} [{app}] {title1}: {text1}",
                        value = settings.notificationLogFormat,
                        onChange = { session.setNotificationLogFormat(it) }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_log_prepend),
                        description = stringResource(R.string.settings_log_prepend_desc),
                        checked = settings.notificationLogPrepend,
                        onChange = { session.setNotificationLogPrepend(it) }
                    )
                    LogSizeWarning(
                        bytes = remember(serversOpen, settings.notificationLogPrepend) {
                            NotificationLogService.logFile(context).length()
                        },
                        prepend = settings.notificationLogPrepend,
                        path = "~/" + NotificationLogService.LOG_REL
                    )
                    Text(
                        text = stringResource(R.string.settings_notif_fmt_help),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = stringResource(R.string.settings_notif_logpath),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    CopyableCommand(
                        label = stringResource(R.string.settings_cmd_read_label),
                        command = readLogCommand(
                            "~/" + NotificationLogService.LOG_REL,
                            settings.notificationLogPrepend
                        )
                    )
                }

                // SMS 受信検知 (汎用入口): RECEIVE_SMS 許可 + 設定 ON のとき、着信 SMS を ~/.z2term/sms.jsonl へ追記。
                // 通知と違い機微通知の伏せ字 (Android 15+) やロック状態の影響を受けないので OTP を確実に取れる。
                Section(title = stringResource(R.string.settings_section_sms)) {
                    var smsGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                                == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val smsPermLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> smsGranted = granted }
                    ToggleField(
                        title = stringResource(R.string.settings_sms_capture),
                        description = stringResource(R.string.settings_sms_capture_desc),
                        checked = settings.smsCaptureEnabled,
                        onChange = { enabled ->
                            session.setSmsCaptureEnabled(enabled)
                            // ON にした瞬間に許可が無ければ実行時許可を求める (無いと検知しても届かない)。
                            if (enabled && !smsGranted) {
                                smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            }
                        }
                    )
                    Text(
                        text = if (smsGranted) stringResource(R.string.settings_sms_perm_granted)
                        else stringResource(R.string.settings_sms_perm_missing),
                        color = if (smsGranted) ZtsGreen else ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_sms_grant),
                        onClick = { smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS) }
                    )

                    // 出力フォーマット: プリセットで埋めてから自由に編集できるテンプレート。
                    val smsPresets = remember {
                        listOf(
                            "jsonl" to "",
                            "readable" to "[{time}] {from}\\n{body}\\n",
                            "line" to "{time} [{from}] {body1}",
                            "tsv" to "{time}\\t{from}\\t{body1}",
                        )
                    }
                    val smsSelected = smsPresets.firstOrNull { it.second == settings.smsLogFormat }?.first ?: ""
                    ChipRow(
                        options = smsPresets.map { it.first },
                        selected = smsSelected,
                        labels = mapOf(
                            "jsonl" to "JSONL",
                            "readable" to stringResource(R.string.settings_notif_fmt_readable),
                            "line" to stringResource(R.string.settings_notif_fmt_line),
                            "tsv" to "TSV",
                        ),
                        onSelect = { id ->
                            session.setSmsLogFormat(smsPresets.first { it.first == id }.second)
                        }
                    )
                    TextField(
                        title = stringResource(R.string.settings_sms_fmt_title),
                        placeholder = "{time} [{from}] {body1}",
                        value = settings.smsLogFormat,
                        onChange = { session.setSmsLogFormat(it) }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_log_prepend),
                        description = stringResource(R.string.settings_log_prepend_desc),
                        checked = settings.smsLogPrepend,
                        onChange = { session.setSmsLogPrepend(it) }
                    )
                    LogSizeWarning(
                        bytes = remember(serversOpen, settings.smsLogPrepend) {
                            SmsLogReceiver.logFile(context).length()
                        },
                        prepend = settings.smsLogPrepend,
                        path = "~/" + SmsLogReceiver.LOG_REL
                    )
                    Text(
                        text = stringResource(R.string.settings_sms_help),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    CopyableCommand(
                        label = stringResource(R.string.settings_cmd_read_label),
                        command = readLogCommand("~/" + SmsLogReceiver.LOG_REL, settings.smsLogPrepend)
                    )
                }

                // システムイベント検知 (汎用入口): 設定 ON のとき FG サービスが常駐し、画面 ON/OFF・ロック解除・
                // 充電・電池・Wi‑Fi のイベントを ~/.z2term/events.jsonl へ追記する。加工はユーザーがターミナル側で。
                Section(title = stringResource(R.string.settings_section_events)) {
                    ToggleField(
                        title = stringResource(R.string.settings_events_capture),
                        description = stringResource(R.string.settings_events_capture_desc),
                        checked = settings.systemEventCaptureEnabled,
                        onChange = { enabled ->
                            session.setSystemEventCaptureEnabled(enabled)
                            SystemEventService.sync(context, enabled)
                        }
                    )

                    // 出力フォーマット: プリセットで埋めてから自由に編集できるテンプレート。
                    val evtPresets = remember {
                        listOf(
                            "jsonl" to "",
                            "line" to "{time} {event} {level}{ssid}",
                            "tsv" to "{time}\\t{event}\\t{level}\\t{ssid}",
                        )
                    }
                    val evtSelected = evtPresets.firstOrNull { it.second == settings.systemEventLogFormat }?.first ?: ""
                    ChipRow(
                        options = evtPresets.map { it.first },
                        selected = evtSelected,
                        labels = mapOf(
                            "jsonl" to "JSONL",
                            "line" to stringResource(R.string.settings_notif_fmt_line),
                            "tsv" to "TSV",
                        ),
                        onSelect = { id ->
                            session.setSystemEventLogFormat(evtPresets.first { it.first == id }.second)
                        }
                    )
                    TextField(
                        title = stringResource(R.string.settings_notif_fmt_title),
                        placeholder = "{time} {event} {level}{ssid}",
                        value = settings.systemEventLogFormat,
                        onChange = { session.setSystemEventLogFormat(it) }
                    )
                    ToggleField(
                        title = stringResource(R.string.settings_log_prepend),
                        description = stringResource(R.string.settings_log_prepend_desc),
                        checked = settings.systemEventLogPrepend,
                        onChange = { session.setSystemEventLogPrepend(it) }
                    )
                    LogSizeWarning(
                        bytes = remember(serversOpen, settings.systemEventLogPrepend) {
                            SystemEventService.logFile(context).length()
                        },
                        prepend = settings.systemEventLogPrepend,
                        path = "~/" + SystemEventService.LOG_REL
                    )
                    Text(
                        text = stringResource(R.string.settings_events_fmt_help),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.settings_events_logpath),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    CopyableCommand(
                        label = stringResource(R.string.settings_cmd_read_label),
                        command = readLogCommand(
                            "~/" + SystemEventService.LOG_REL,
                            settings.systemEventLogPrepend
                        )
                    )
                }

                // ロック解除の失敗監視 (盗難対策マクロの検知入口): 設定 ON + 端末管理者が有効なとき、
                // ロック解除の失敗/成功を events.jsonl へ unlock_failed / unlock_succeeded として流す。
                // 撮影・送信・警報などのアクションはハードコードせず、ユーザーがマクロで組む。
                Section(title = stringResource(R.string.settings_section_unlock_watch)) {
                    // 端末管理者の有効/無効は OS 側の画面で変わるため、遷移から戻ってきた時点で
                    // 必ず読み直す。remember のキー任せだと戻っても再評価されず、有効化したのに
                    // 「未設定」のまま・ボタンも「有効化」のままで、押すと既に有効な管理者に対して
                    // 追加ダイアログを出そうとして何も起きない (= 壊れて見える) ことになる。
                    var adminActive by remember { mutableStateOf(PasswordWatchAdmin.isActive(context)) }
                    val adminLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        // 戻り値 (RESULT_OK/CANCELED) ではなく DPM に現在の状態を聞き直す。
                        // セキュリティ設定から無効化して戻ってきた場合もこれで追従する。
                        adminActive = PasswordWatchAdmin.isActive(context)
                    }
                    ToggleField(
                        title = stringResource(R.string.settings_unlock_watch),
                        description = stringResource(R.string.settings_unlock_watch_desc),
                        checked = settings.unlockWatchEnabled,
                        onChange = { session.setUnlockWatchEnabled(it) }
                    )
                    Text(
                        text = if (adminActive) stringResource(R.string.settings_unlock_watch_admin_active)
                        else stringResource(R.string.settings_unlock_watch_admin_missing),
                        color = if (adminActive) ZtsGreen else ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // 端末管理者の有効化ダイアログ (EXTRA_DEVICE_ADMIN は ComponentName parcelable なので
                    // z2-intent 等では組めず、アプリ内から起動する)。有効化済みなら管理者一覧を開いて無効化に導く。
                    ActionButton(
                        label = if (adminActive) stringResource(R.string.settings_unlock_watch_admin_manage)
                        else stringResource(R.string.settings_unlock_watch_admin_grant),
                        onClick = {
                            val intent = if (adminActive) {
                                Intent(Settings.ACTION_SECURITY_SETTINGS)
                            } else {
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                        PasswordWatchAdmin.component(context)
                                    )
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        context.getString(R.string.settings_unlock_watch_admin_explain)
                                    )
                            }
                            // FLAG_ACTIVITY_NEW_TASK を付けて startActivity すると別タスクで開き、
                            // 戻っても設定シートに帰ってこない。launcher なら同じタスクで開き、
                            // 戻った時点で上のコールバックが発火して表示も更新される。
                            runCatching { adminLauncher.launch(intent) }.onFailure {
                                // 端末ポリシーで管理者追加が禁止されている等。黙って無反応にしない。
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_unlock_watch_admin_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_unlock_watch_help),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            SettingsGroupSection(SettingsGroup.MAINTENANCE) {
                Spacer(modifier = Modifier.height(4.dp))

                // 持ち出し / 引き継ぎ。機種変・初期化で全部消えるのが今までで、
                // 持ち出せると分かって初めて腰を据えて積み上げられる (提案 19)。
                Section(title = stringResource(R.string.settings_backup)) {
                    Text(
                        text = stringResource(R.string.settings_backup_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val importPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri -> if (uri != null) backupImportUri = uri }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            label = stringResource(R.string.settings_backup_export),
                            onClick = { backupExportOpen = true }
                        )
                        ActionButton(
                            label = stringResource(R.string.settings_backup_import),
                            onClick = { runCatching { importPicker.launch(arrayOf("*/*")) } }
                        )
                    }
                }

                // 決まった日時に自動で 1 本 (0.8.386)。手で押したときにしか残らないのが
                // 持ち出しの弱点だったので、押し忘れても昨日の状態が残る形にする。
                AutoBackupSection(settings = settings, session = session)

                // 初回ガイドをもう一度。復活の導線は「設定の奥に 1 行」に留める
                // (毎回出したい機能ではないので、目立つ場所には置かない)。
                Section(title = stringResource(R.string.settings_intro_again)) {
                    Text(
                        text = stringResource(R.string.settings_intro_again_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_intro_again),
                        onClick = {
                            session.setIntroDone(false)
                            Toast.makeText(
                                context, R.string.settings_intro_again_done, Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                // 案内を表示 (0.8.314)。同梱サンプルのマクロは「入れてから使う」ものなので、
                // 入れる前は名前すら見えない。手順のカードをここから何度でも出せるようにする。
                // ⚠ 以前はリマインドだけスニペットに `remind.sh help` を置いていたが、入れて
                //   いない人が押すと「見つからない」と出るだけだった (利用者の指摘)。
                Section(title = stringResource(R.string.settings_guides)) {
                    Text(
                        text = stringResource(R.string.settings_guides_desc),
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // **名前と説明を必ず並べて出す** (0.8.335・利用者の指摘)。説明文だけを
                    // 横に並べていた頃は「入門: できごとに反応する」がどのマクロの話か読めず、
                    // 名前だけにすると今度は何をするものか分からなかった。どちらか片方では
                    // 足りないので、名前を主・説明を添えた 2 行の行リストにする。
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Guide.ALL.forEach { guide ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ZtsBgCard)
                                    .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                                    .clickable { onShowGuide(guide) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = guide.id,
                                    color = ZtsGreen,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = guideDesc(guide),
                                    color = ZtsTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // 端末リセット (アプリ初回起動時の状態に戻す = 端末タブ 1 つだけにして初期化)。
                // 画面クリア単体は CTRL+L で行える。
                // ディストロ/GUI のクリーンインストールは各「切替」セクションのチェックへ移動。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        label = stringResource(R.string.settings_reset_terminal),
                        danger = true,
                        onClick = { pendingTerminalReset = true }
                    )
                    // キャッシュ削除 (rootfs 内のパッケージ/ビルドキャッシュ + アプリ一時)。
                    // ワンタップでは消さず、何をどれだけ消すか列挙した確認ダイアログを挟む。
                    ActionButton(
                        label = stringResource(
                            R.string.settings_clear_cache_sized,
                            cacheTotal?.let { formatStorageSize(it) } ?: "…"
                        ),
                        onClick = { pendingCacheClear = true }
                    )
                }
                ActionButton(
                    label = stringResource(R.string.settings_task_kill),
                    danger = true,
                    onClick = { pendingTaskKill = true }
                )
            }

            SettingsGroupSection(SettingsGroup.DEVELOPER) {
                // 実験的: Android ホスト bind (/system /apex を proot/chroot 内に晒す)。
                // 端末内で aapt2 等の ARM aarch64 ELF (Android リンカ要求) を動かすための活路。
                // 反映は次のセッション再起動時 (ProotLauncher が起動引数に追加するため)。
                Section(title = stringResource(R.string.settings_section_experimental)) {
                    ToggleField(
                        title = stringResource(R.string.settings_android_host_bind_toggle),
                        description = stringResource(R.string.settings_android_host_bind_toggle_desc),
                        checked = settings.androidHostBindEnabled,
                        onChange = { session.setAndroidHostBindEnabled(it) }
                    )
                    if (settings.androidHostBindEnabled) {
                        Text(
                            text = stringResource(R.string.settings_android_host_bind_warning),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    // Kitty graphics の file/temp/shm 外部ファイル経路 (`t=f`/`t=t`/`t=s`)。
                    // 既定 OFF。 ON のとき rootfs 配下のファイル読込を許可する (TUI 側で
                    // base64 でパスを送る image viewer 系で必要)。 反映は即時 (combine 監視)。
                    ToggleField(
                        title = stringResource(R.string.settings_kitty_external_file_toggle),
                        description = stringResource(R.string.settings_kitty_external_file_toggle_desc),
                        checked = settings.kittyExternalFileEnabled,
                        onChange = { session.setKittyExternalFileEnabled(it) }
                    )
                    if (settings.kittyExternalFileEnabled) {
                        Text(
                            text = stringResource(R.string.settings_kitty_external_file_warning),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    // SGR mouse 入力 (タッチ→マウスイベント変換): 既定 OFF。
                    // ON 時はカレンダー pane / ファイラ / 複数 pane 切替を伴う TUI で
                    // タップ操作が届くようになる代わりに、 1 指でのテキスト選択は封じられる。
                    ToggleField(
                        title = stringResource(R.string.settings_sgr_mouse_input_toggle),
                        description = stringResource(R.string.settings_sgr_mouse_input_toggle_desc),
                        checked = settings.sgrMouseInputEnabled,
                        onChange = { session.setSgrMouseInputEnabled(it) }
                    )
                    if (settings.sgrMouseInputEnabled) {
                        Text(
                            text = stringResource(R.string.settings_sgr_mouse_input_warning),
                            color = ZtsTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 裏機能で解放されたときだけ「実行エンジン」を表示。
                // 非 root は z2root 固定、chroot は root セルフテスト成功時だけ選べる。
                if (settings.engineSelectorUnlocked) {
                    Section(title = stringResource(R.string.settings_section_engine)) {
                        val engineOptions = buildList {
                            add(AppSettings.ENGINE_Z2ROOT)
                            if (settings.rootChrootUnlocked) add(AppSettings.ENGINE_CHROOT)
                        }
                        ChipRow(
                            options = engineOptions,
                            selected = settings.executionEngine,
                            labels = mapOf(
                                AppSettings.ENGINE_Z2ROOT to stringResource(R.string.settings_engine_z2root),
                                AppSettings.ENGINE_CHROOT to stringResource(R.string.settings_engine_chroot),
                            ),
                            onSelect = { session.setExecutionEngine(it) }
                        )
                        Text(
                            text = stringResource(R.string.settings_engine_desc),
                            color = ZtsWarning,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // 実際に動いているエンジン (設定チップは「次に起動する選択値」だが、これは
                        // このタブが本当に起動したエンジン。未同梱で proot に倒れた等もここに出る)。
                        val actualEngineLabel = when (actualEngine) {
                            AppSettings.ENGINE_Z2ROOT -> stringResource(R.string.settings_engine_z2root)
                            AppSettings.ENGINE_CHROOT -> stringResource(R.string.settings_engine_chroot)
                            AppSettings.ENGINE_ANDROID_SH -> stringResource(R.string.settings_engine_android_sh)
                            else -> stringResource(R.string.settings_engine_current_starting)
                        }
                        InfoRow(
                            label = stringResource(R.string.settings_engine_current),
                            value = actualEngineLabel
                        )
                        // chroot 未解放 (root セルフテスト未成功) のときは、再試行ボタンを出す。
                        // su 許可を一度拒否しても、ここから何度でも root 確認をやり直せる
                        // (拒否すると二度と chroot を選べなくなる問題の解消)。
                        if (!settings.rootChrootUnlocked) {
                            Text(
                                text = stringResource(R.string.settings_root_retry_hint),
                                color = ZtsTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            ActionButton(
                                label = if (rootProbing)
                                    stringResource(R.string.settings_root_retry_probing)
                                else
                                    stringResource(R.string.settings_root_retry_button),
                                onClick = { runRootProbe(true) }
                            )
                        }
                        // 開発者用: z2root の syscall トレースログ ON/OFF。既定 OFF。ログは膨大で
                        // すぐ容量を圧迫するため、一般ユーザーは使わないよう警告を添える。
                        // エンジン選択と同じ 7タップ裏機能内に置く (解放済みのときだけ見える)。
                        ToggleField(
                            title = stringResource(R.string.settings_trace_log_toggle),
                            description = stringResource(R.string.settings_trace_log_toggle_desc),
                            checked = settings.traceLogEnabled,
                            onChange = { session.setTraceLogEnabled(it) }
                        )
                        Text(
                            text = stringResource(R.string.settings_trace_log_warning),
                            color = ZtsWarning,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            SettingsGroupSection(SettingsGroup.TIPS) {
                TipsSection()
            }

            SettingsGroupSection(SettingsGroup.ABOUT) {
                AppInfoSection(
                    distroId = settings.distroId,
                    engineUnlocked = settings.engineSelectorUnlocked,
                    updateKeepApk = settings.updateKeepApk,
                    updateDownloadDir = settings.updateDownloadDir,
                    onUpdateKeepApk = { session.setUpdateKeepApk(it) },
                    onUpdateDownloadDir = { session.setUpdateDownloadDir(it) },
                    // 設定の初期化はアプリ情報とライセンスの間に置く (設定の一番下・要望)。
                    onResetSettings = { pendingReset = true },
                    onToggle = {
                        // 7タップで z2root/chroot の診断設定を表示する。
                        if (settings.engineSelectorUnlocked) {
                            // 解除: 選択を隠し、既定 (z2root) へ戻す = 表示前の状態へ復帰。
                            session.setExecutionEngine(AppSettings.ENGINE_Z2ROOT)
                            session.setEngineSelectorUnlocked(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_engine_lock_ok),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // 解放: まずエンジン選択を表示する。
                            session.setEngineSelectorUnlocked(true)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_engine_unlock_ok),
                                Toast.LENGTH_SHORT
                            ).show()
                            // 続けて root セルフテストを試み、成功したときだけ chroot も選択肢に追加する。
                            // 非 root / SELinux で塞がれている場合は追加トーストを出さない (engine selector は解放済み)。
                            // 失敗しても、エンジン選択内の再試行ボタン (runRootProbe(true)) からやり直せる。
                            runRootProbe(false)
                        }
                    }
                )
            }
        }
        }
    }

    // distro 切替 / クリーンインストールの DL 確認 (M8-6 T7)。OK で起動時に DL/展開、シートを閉じる。
    pendingDistroSwitch?.let { spec ->
        val clean = pendingCleanInstall
        val sizeHint = spec.approxDownload?.let { " ($it)" } ?: ""
        DownloadConfirmDialog(
            title = if (clean) stringResource(R.string.confirm_clean_install_title, spec.displayName)
                    else stringResource(R.string.confirm_download_title, spec.displayName),
            message = if (clean)
                stringResource(R.string.confirm_clean_install_msg, spec.displayName, sizeHint)
            else
                stringResource(R.string.confirm_download_msg, spec.displayName, sizeHint),
            confirmLabel = if (clean) stringResource(R.string.action_clean_install)
                           else stringResource(R.string.action_download_and_switch),
            onConfirm = {
                val id = spec.id
                pendingDistroSwitch = null
                pendingCleanInstall = false
                distroCleanArmed = false
                if (clean) session.cleanInstallDistro(id) else session.switchDistro(id)
                onDismiss()
            },
            onCancel = { pendingDistroSwitch = null; pendingCleanInstall = false }
        )
    }

    // Konsole × Alpine の衝突 (0.8.353)。⭐ **断るだけで終わらせない** — Alpine を選ぼうと
    // した側からは「xterm に切り替えて開く」を 1 タップで実行できるようにする
    // (設定を往復させないため。`NoOsNoticeCard` で戻り道を残したのと同じ考え方)。
    konsoleConflict?.let { from ->
        val fromDistro = from == "distro"
        DownloadConfirmDialog(
            title = stringResource(R.string.gui_term_unsupported_title),
            message = if (fromDistro) stringResource(R.string.gui_term_unsupported_switch_msg)
                      else stringResource(R.string.gui_term_unsupported_pick_msg),
            confirmLabel = if (fromDistro) stringResource(R.string.action_switch_to_xterm_and_open)
                           else stringResource(R.string.action_got_it),
            onConfirm = {
                konsoleConflict = null
                if (fromDistro) {
                    // 端末を先に確定させてから distro を切り替える。順序が逆だと
                    // 切替後の初回起動が Konsole のまま走ってしまう。
                    session.setGuiTerminal(GuiTerminal.XTERM.id)
                    session.switchDistro("alpine")
                    onDismiss()
                }
            },
            onCancel = { konsoleConflict = null }
        )
    }

    // OS データ削除の確認。削除すると元に戻せない旨を再確認させる。
    pendingOsDelete?.let { id ->
        val name = DistroSpec.byId(id)?.displayName ?: id
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_delete_os_title, name),
            message = stringResource(R.string.confirm_delete_os_msg, name),
            confirmLabel = stringResource(R.string.action_delete_os),
            onConfirm = {
                session.deleteDistroData(id) { osDataRefresh++ }
                pendingOsDelete = null
            },
            onCancel = { pendingOsDelete = null }
        )
    }

    // キャッシュ削除の確認。何を (項目名) どれだけ (サイズ) 消すかを 1 件ずつ列挙してから消す。
    // 対象は再取得できるキャッシュのみ。パッケージ本体・設定・作業ファイルは消えない旨も明示。
    if (pendingCacheClear) {
        val items = cacheItems.orEmpty()
        val total = items.sumOf { it.bytes }
        val message = if (items.isEmpty()) {
            stringResource(R.string.confirm_clear_cache_empty_msg)
        } else {
            val lines = items.joinToString("\n") { "・${it.label} … ${formatStorageSize(it.bytes)}" }
            stringResource(R.string.confirm_clear_cache_msg, formatStorageSize(total), lines)
        }
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_clear_cache_title),
            message = message,
            confirmLabel = stringResource(R.string.settings_clear_cache),
            onConfirm = {
                pendingCacheClear = false
                if (items.isNotEmpty()) {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { RootfsCacheCleaner.clean(items) }
                        cacheRefresh++
                        val msg = if (freed > 0)
                            context.getString(R.string.settings_clear_cache_done, formatStorageSize(freed))
                        else
                            context.getString(R.string.settings_clear_cache_empty)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCancel = { pendingCacheClear = false }
        )
    }

    // 端末リセットの確認。何が消えて何が残るか (タブは 1 つになる / 設定・rootfs は無事) を
    // 明示してから実行する。実行後はトーストで効いたことを伝える (無言だと分からないため)。
    if (pendingTerminalReset) {
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_reset_terminal_title),
            message = stringResource(R.string.confirm_reset_terminal_msg),
            confirmLabel = stringResource(R.string.action_reset_terminal),
            onConfirm = {
                pendingTerminalReset = false
                SessionManager.resetToInitial(context)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_reset_terminal_done),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCancel = { pendingTerminalReset = false }
        )
    }

    // 端末リセットより強い完全停止。常駐サーバー・イベント検知・全セッション・常駐サービスを
    // 止めてタスクを閉じる。保存済み設定や Linux 側のファイルは消さない。
    if (pendingTaskKill) {
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_task_kill_title),
            message = stringResource(R.string.confirm_task_kill_msg),
            confirmLabel = stringResource(R.string.action_task_kill),
            onConfirm = {
                pendingTaskKill = false
                stopEverythingAndQuit(context)
            },
            onCancel = { pendingTaskKill = false }
        )
    }

    // 設定の初期化の確認。すべての設定が既定値に戻る旨を明示してから実行する。
    // OS 本体 (rootfs) や作業ファイルは消えないこと・元に戻せないことを再確認させる。
    if (pendingReset) {
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_reset_settings_title),
            message = stringResource(R.string.confirm_reset_settings_msg),
            confirmLabel = stringResource(R.string.action_reset_settings),
            onConfirm = {
                pendingReset = false
                session.resetSettings()
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_reset_settings_done),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCancel = { pendingReset = false }
        )
    }

    // IME 学習履歴の管理シート (キーボードパッチ)。設定シートと**重ねて**開く。
    if (imeHistoryOpen) {
        ImeHistorySheet(onDismiss = { imeHistoryOpen = false })
    }

    // 常駐サーバー管理シート。設定シートと**重ねて**開く。
    if (whenRulesOpen) {
        WhenRulesSheet(onDismiss = { whenRulesOpen = false })
    }

    if (backupExportOpen) {
        BackupExportDialog(
            onDismiss = { backupExportOpen = false },
            onDone = { backupExportOpen = false }
        )
    }
    backupImportUri?.let { uri ->
        BackupImportDialog(
            uri = uri,
            onDismiss = { backupImportUri = null },
            onDone = { backupImportUri = null }
        )
    }

    if (serversOpen) {
        ServersSheet(session = session, onDismiss = { serversOpen = false })
    }

    // ロックされた🔒トグルをタップしたときの終了ダイアログ (ツールバー側と同じ出口)。
    if (residentDialogOpen) {
        ResidentActionDialog(
            onResetSession = {
                residentDialogOpen = false
                onDismiss()
                SessionManager.resetToInitial(context)
            },
            onStopAll = { residentDialogOpen = false; stopEverythingAndQuit(context) },
            onCancel = { residentDialogOpen = false }
        )
    }
}

/**
 * アプリ情報セクション (設定末尾)。
 * バージョン / フレーバー / applicationId / ROOTFS_VERSION / 現在の distro と
 * その os-release を表示する。
 */
@Composable
private fun AppInfoSection(
    distroId: String,
    engineUnlocked: Boolean,
    updateKeepApk: Boolean,
    updateDownloadDir: String,
    onUpdateKeepApk: (Boolean) -> Unit,
    onUpdateDownloadDir: (String) -> Unit,
    onResetSettings: () -> Unit,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    // 裏機能: バージョン行を 7 回タップでエンジン選択 (proot / z2root) の表示をトグル
    // (Android 開発者モードと同作法)。解放済みでも 7 タップで隠して既定へ戻せる。
    var tapCount by remember { mutableStateOf(0) }
    // os-release の PRETTY_NAME を rootfs から 1 度だけ読む (軽量なファイル read)
    val osPretty = remember(distroId) {
        runCatching {
            val f = java.io.File(context.filesDir, "distros/$distroId/etc/os-release")
            if (!f.exists()) return@runCatching null
            f.readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')?.trim('"', ' ')
        }.getOrNull()
    }
    // 連打したとき、前のトーストが消えるのを待たず即座に次の文言へ差し替える
    // (cancel しないと Android がトーストをキューイングして表示が大幅に遅延する)。
    var lastToast by remember { mutableStateOf<Toast?>(null) }
    // トグル発火後のクールダウン。7 タップ到達でトグルした直後、連打が続くとすぐ次の 7 タップに
    // 達して逆方向に切り替わってしまうため、発火後 3 秒はバージョン行自体を**タップ不可**にする
    // (従来は反応するのにタップが無視されて不自然だった)。
    var inCooldown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 解放済み・未解放のどちらでも 7 タップで反応させる (解放後に隠せなくなる問題の解消)。
    val versionClick: (() -> Unit) = {
        tapCount++
        val remaining = 7 - tapCount
        when {
            remaining <= 0 -> {
                tapCount = 0
                lastToast?.cancel()
                inCooldown = true
                scope.launch {
                    delay(3000L)
                    inCooldown = false
                }
                onToggle()
            }
            remaining in 1..3 -> {
                lastToast?.cancel()
                lastToast = Toast.makeText(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.settings_root_unlock_countdown, remaining, remaining
                    ),
                    Toast.LENGTH_SHORT
                ).also { it.show() }
            }
        }
    }
    // 手動更新チェックの状態。null = まだ押していない。ボタンを押したときだけ
    // UpdateChecker.check() が走り、それ以外ではネットワークに一切触れない。
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<com.zerotoship.z2term.update.UpdateResult?>(null) }
    // 入れ替え (ダウンロード → OS の確認画面) の進み具合。⚠ **押した後に何も出ない時間を作らない** —
    // 20MB のダウンロードの間ボタンが黙っていると、押せていないのか進んでいるのか分からない。
    var updateWorking by remember { mutableStateOf(false) }
    var updateNote by remember { mutableStateOf<String?>(null) }
    var updateNeedsPermission by remember { mutableStateOf(false) }
    Section(title = stringResource(R.string.settings_section_app_info)) {
        InfoRow(
            stringResource(R.string.appinfo_version),
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            // クールダウン中は onClick=null で行を非タップ化 (ripple も出ない＝押せないことが分かる)。
            onClick = if (inCooldown) null else versionClick
        )
        InfoRow(stringResource(R.string.appinfo_package), BuildConfig.APPLICATION_ID)
        InfoRow(stringResource(R.string.appinfo_rootfs_generation), DistroBundle.ROOTFS_VERSION.toString())
        InfoRow(stringResource(R.string.appinfo_distro), osPretty ?: distroId)

        // 更新を確認: ボタンを押した瞬間だけ GitHub Releases に 1 回問い合わせる。
        // 自動チェックはしない (押すまで通信しない)。DL/インストールもしない —
        // 新版があればリリースページを開くところまで。
        Spacer(Modifier.height(4.dp))
        ActionButton(
            label = if (updateChecking) stringResource(R.string.settings_check_update_checking)
            else stringResource(R.string.settings_check_update),
            onClick = {
                if (updateChecking) return@ActionButton
                updateChecking = true
                updateResult = null
                scope.launch {
                    updateResult = com.zerotoship.z2term.update.UpdateChecker.check()
                    updateChecking = false
                }
            }
        )
        when (val r = updateResult) {
            is com.zerotoship.z2term.update.UpdateResult.Available -> {
                Text(
                    text = stringResource(R.string.settings_update_available, r.latest),
                    color = ZtsGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                // ⚠ APK が付いているリリースのときだけ出す。付いていない (作りかけの) リリースで
                //    押させると、落とすものが無いという分かりにくい断り方になる。
                if (r.apkUrl != null) {
                    ActionButton(
                        label = if (updateWorking) stringResource(R.string.settings_update_working)
                        else stringResource(R.string.settings_update_install),
                        onClick = {
                            if (updateWorking) return@ActionButton
                            updateWorking = true
                            updateNote = null
                            updateNeedsPermission = false
                            scope.launch {
                                val outcome = com.zerotoship.z2term.update.UpdateFlow.run(
                                    context, checkOnly = false
                                )
                                updateNote = when (outcome) {
                                    is com.zerotoship.z2term.update.UpdateFlow.Outcome.Handed ->
                                        context.getString(R.string.settings_update_handed)
                                    com.zerotoship.z2term.update.UpdateFlow.Outcome.NeedPermission -> {
                                        updateNeedsPermission = true
                                        context.getString(R.string.settings_update_need_permission)
                                    }
                                    com.zerotoship.z2term.update.UpdateFlow.Outcome.ManagedByStore ->
                                        context.getString(R.string.settings_update_store)
                                    is com.zerotoship.z2term.update.UpdateFlow.Outcome.NoApk ->
                                        context.getString(R.string.settings_update_no_apk)
                                    is com.zerotoship.z2term.update.UpdateFlow.Outcome.Failed ->
                                        context.getString(R.string.settings_update_failed, outcome.reason)
                                    is com.zerotoship.z2term.update.UpdateFlow.Outcome.UpToDate ->
                                        context.getString(R.string.settings_update_uptodate)
                                    is com.zerotoship.z2term.update.UpdateFlow.Outcome.Found ->
                                        context.getString(R.string.settings_update_available, outcome.latest)
                                }
                                updateWorking = false
                            }
                        }
                    )
                }
                ActionButton(
                    label = stringResource(R.string.settings_update_open_page),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, r.url.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
            is com.zerotoship.z2term.update.UpdateResult.UpToDate -> Text(
                text = stringResource(R.string.settings_update_uptodate),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            is com.zerotoship.z2term.update.UpdateResult.Failed -> Text(
                text = stringResource(R.string.settings_update_failed, r.reason),
                color = ZtsError,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            null -> {}
        }
        updateNote?.let { note ->
            Text(
                text = note,
                color = if (updateNeedsPermission) ZtsError else ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        // 許可が足りないときだけ、その設定画面への入口を出す (普段は出さない)。
        if (updateNeedsPermission) {
            ActionButton(
                label = stringResource(R.string.settings_update_allow),
                onClick = {
                    runCatching {
                        context.startActivity(
                            com.zerotoship.z2term.update.UpdateInstaller.unknownSourcesIntent(context)
                        )
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        ToggleField(
            title = stringResource(R.string.settings_update_keep_apk),
            description = stringResource(R.string.settings_update_keep_apk_desc),
            checked = updateKeepApk,
            onChange = onUpdateKeepApk
        )
        TextField(
            title = stringResource(R.string.settings_update_dir),
            placeholder = stringResource(R.string.settings_update_dir_hint),
            value = updateDownloadDir,
            onChange = onUpdateDownloadDir
        )
        Text(
            text = stringResource(R.string.settings_update_dir_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
    // 設定の初期化 (すべての設定を既定値へ戻す)。ワンタップでは戻さず確認ダイアログを挟む。
    // 普段触らない操作なので、設定の末尾 (ライセンスの直前) に置く。
    ActionButton(
        label = stringResource(R.string.settings_reset_settings),
        danger = true,
        onClick = onResetSettings
    )

    // 設定シート内にずらりと並べると視認性が悪いので、タップで開く Dialog に切り出す。
    var showLicensesDialog by remember { mutableStateOf(false) }
    Section(title = stringResource(R.string.settings_section_licenses)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { showLicensesDialog = true }
                .background(ZtsBgCard)
                .border(width = 1.dp, color = ZtsBorder, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.licenses_open_button),
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "›",
                color = ZtsTextSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = stringResource(R.string.licenses_summary),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    if (showLicensesDialog) {
        LicensesDialog(
            onDismiss = { showLicensesDialog = false },
            textPrimary = ZtsTextPrimary,
            textSecondary = ZtsTextSecondary,
            accent = ZtsGreen,
            border = ZtsBorder,
            background = ZtsBgPrimary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = ZtsTextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * 設定ページの上部バー。左に戻る矢印 (←)、その横にタイトル。タブバーと同じ枠線で
 * 「ページの見出し」であることを示し、下のスクロール領域とは分離する。
 */
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgPrimary)
            .border(width = 1.dp, color = ZtsBorder)
            // バー全体をタップしても戻れるようにする (左上の矢印だけでなく上の設定バー全体・要望)。
            .clickable(onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "←",
                color = ZtsGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = stringResource(R.string.settings_header),
            color = ZtsGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 設定項目のグループ (アコーディオン)。見出しをタップで開閉し、状態は
 * [SettingsGroupStore] で永続化する (アプリを閉じても保持)。
 *
 * 中身は従来の [Section] をそのまま並べるだけ。閉じている間は中身を composition しないので、
 * 重い項目 (OS 使用量の走査など) は開くまで走らない。
 */
@Composable
private fun SettingsGroupSection(
    group: SettingsGroup,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val openState by SettingsGroupStore.openState.collectAsState()
    val open = openState[group.id] ?: group.defaultOpen
    // 見出しだけでなく展開内容まで 1 枚のカードに収め、次のグループとの境界を明示する。
    // 見出しには内容の短い説明も常時出し、初見でも開く前に設定の種類を判断できるようにする。
    val headerBg = if (open) ZtsGreen.copy(alpha = 0.10f) else ZtsBgCard
    val headerBorder = if (open) ZtsGreen.copy(alpha = 0.55f) else ZtsBorder
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgCard)
            .border(1.dp, headerBorder, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .clickable { SettingsGroupStore.setOpen(group, !open) }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(group.titleRes),
                    color = ZtsGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(group.descriptionRes),
                    color = ZtsTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = if (open) "▾" else "▸",
                color = ZtsGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (open) {
            // 区切り線と内側余白を残すことで、どこまでがこの見出しの設定かを目で追える。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(headerBorder)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 通信量の上限 (0.8.388)。今期の使用量・上限・締め日と、いま止めているかどうかを出す。
 *
 * ⚠ **止まる範囲を画面に書き切る**のがこの節の仕事。「ネットワークを遮断する」と読めてしまうと、
 * ほかのアプリが止まらないことも、端末 (Linux) の中から出ていく通信が止まらないことも、
 * 期待外れとしてしか受け取られない。
 */
@Composable
private fun NetLimitSection(settings: AppSettings.Snapshot, session: TerminalSession) {
    val context = LocalContext.current

    // 「使用状況へのアクセス」の許可。⚠ **システム設定でしか変えられない**ので、戻ってきた
    // とき (ON_RESUME) に見直す (電池最適化の除外と同じ扱い)。
    var usageAccess by remember { mutableStateOf(NetGuard.hasUsageAccess(context)) }
    DisposableEffect(context) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageAccess = NetGuard.hasUsageAccess(context)
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    // 使用量は問い合わせが重いので画面を止めない。設定を変えたら・許可が変わったら測り直す。
    val status by produceState<NetGuard.Status?>(
        initialValue = null,
        settings.netLimitEnabled,
        settings.netLimitMb,
        settings.netLimitResetDay,
        settings.netLimitWifiExempt,
        usageAccess
    ) {
        value = withContext(Dispatchers.IO) { runCatching { NetGuard.status(context) }.getOrNull() }
    }

    Section(title = stringResource(R.string.settings_net_limit)) {
        Text(
            text = stringResource(R.string.settings_net_limit_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        ToggleField(
            title = stringResource(R.string.net_limit_enable),
            description = stringResource(R.string.net_limit_enable_desc),
            checked = settings.netLimitEnabled,
            onChange = { session.setNetLimitEnabled(it) }
        )
        if (!settings.netLimitEnabled) return@Section

        // --- 上限 ---
        // つまみは目分量、欄はきっちり。⚠ **つまみだけでは契約の数字に合わせられない**
        // (4.5GB や 100GB はどの段にも無い) ので、打てる欄を必ず添える。
        val steps = NetGuard.LIMIT_STEPS_MB
        SliderField(
            title = stringResource(R.string.net_limit_amount),
            value = NetGuard.stepIndexOf(settings.netLimitMb).toFloat(),
            range = 0f..(steps.size - 1).toFloat(),
            steps = steps.size - 2,
            // ⚠ **つまみの位置ではなく、いまの設定値を出す**。手で打った値が段の上に無いとき、
            // つまみは近い段を指すが、**数字は打ったとおりでなければならない**。
            valueLabel = { NetGuard.formatBytes(settings.netLimitMb * 1024L * 1024L) },
            onChange = { session.setNetLimitMb(steps[it.toInt().coerceIn(steps.indices)]) }
        )
        var mbText by remember { mutableStateOf(settings.netLimitMb.toString()) }
        // つまみを動かした / 別の端末から戻したときは欄も追いつかせる (打っている最中は触らない)。
        LaunchedEffect(settings.netLimitMb) {
            if (mbText.toIntOrNull() != settings.netLimitMb) mbText = settings.netLimitMb.toString()
        }
        TextField(
            title = stringResource(R.string.net_limit_amount_field),
            placeholder = AppSettings.DEFAULT_NET_LIMIT_MB.toString(),
            value = mbText,
            numeric = true,
            onChange = { raw ->
                // 数字だけ通す (単位や記号を打たれても壊れない)。空のままも許す —
                // 消してから打ち直す間に勝手な値が入ると、打ち直せない欄になる。
                val digits = raw.filter { it.isDigit() }.take(7)
                mbText = digits
                digits.toIntOrNull()
                    ?.takeIf { it in NetGuard.TYPED_MIN_MB..NetGuard.TYPED_MAX_MB }
                    ?.let { session.setNetLimitMb(it) }
            }
        )

        // --- 数え直す日 ---
        val dayFormat = stringResource(R.string.net_limit_reset_day_value)
        SliderField(
            title = stringResource(R.string.net_limit_reset_day),
            value = settings.netLimitResetDay.toFloat(),
            range = 1f..28f,
            steps = 26,
            valueLabel = { dayFormat.format(it.toInt()) },
            onChange = { session.setNetLimitResetDay(it.toInt()) }
        )

        ToggleField(
            title = stringResource(R.string.net_limit_wifi_exempt),
            description = stringResource(R.string.net_limit_wifi_exempt_desc),
            checked = settings.netLimitWifiExempt,
            onChange = { session.setNetLimitWifiExempt(it) }
        )

        // --- いまの状況 ---
        val st = status
        when {
            // ⚠ 許可が無い間は**何も止まらない**。ここが一番伝わらないと「設定したのに効かない」
            // で終わるので、理由と行き先を先に出す。
            !usageAccess -> {
                Text(
                    text = stringResource(R.string.net_limit_need_access),
                    color = ZtsError,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                ActionButton(
                    label = stringResource(R.string.net_limit_grant),
                    onClick = { NetGuard.openUsageAccessSettings(context) }
                )
            }
            st == null -> Text(
                text = "…",
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            // 許可はあるのに読めない端末では**止めない**ので、そのことも言う。
            !st.measurable -> Text(
                text = stringResource(R.string.net_limit_unmeasurable),
                color = ZtsError,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            else -> {
                val fmt = remember { SimpleDateFormat("M/d", Locale.getDefault()) }
                Text(
                    text = stringResource(
                        R.string.net_limit_used,
                        NetGuard.formatBytes(st.usedBytes),
                        NetGuard.formatBytes(st.limitBytes),
                        fmt.format(Date(st.periodStart))
                    ),
                    color = if (st.blocking) ZtsError else ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (st.blocking) {
                    Text(
                        text = stringResource(R.string.net_limit_blocked),
                        color = ZtsError,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (st.over) {
                    // 超えてはいるが Wi-Fi なので止めていない、という状態を隠さない。
                    Text(
                        text = stringResource(R.string.net_limit_paused_on_wifi),
                        color = ZtsTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.net_limit_local_note) + "\n" +
                stringResource(R.string.net_limit_shell_note),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 定期バックアップ (0.8.386)。日時と世代数を決めて、選んだフォルダへ自動で積む。
 *
 * **なぜ設定を即保存にしたか**: この画面には「保存」ボタンが無い (設定シート全体の作法)。
 * 途中まで直して閉じても、直したところまでは効く。⚠ 保存のたびに予約を貼り直すのは
 * [TerminalSession] 側の仕事 ([TerminalSession.setAutoBackupSchedule])。
 *
 * ⚠ **秘密は含めない**。理由は [AutoBackup] の KDoc に書いた。ここでは説明文でそう伝えるだけ。
 */
@Composable
private fun AutoBackupSection(settings: AppSettings.Snapshot, session: TerminalSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }

    // 保存先フォルダ。⚠ takePersistableUriPermission を忘れると、**アプリを再起動した時点で
    // 書けなくなる** (その日の夜中に静かに失敗する)。
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            session.setAutoBackupFolder(uri.toString())
        }
    }

    // 1 か所だけ変えて残りは今の値のまま保存する (画面のどの操作も「1 項目を直す」なので)。
    fun save(
        interval: String = settings.autoBackupInterval,
        dayOfWeek: Int = settings.autoBackupDayOfWeek,
        dayOfMonth: Int = settings.autoBackupDayOfMonth,
        hour: Int = settings.autoBackupHour,
        minute: Int = settings.autoBackupMinute,
        keep: Int = settings.autoBackupKeep
    ) = session.setAutoBackupSchedule(interval, dayOfWeek, dayOfMonth, hour, minute, keep)

    Section(title = stringResource(R.string.settings_auto_backup)) {
        Text(
            text = stringResource(R.string.settings_auto_backup_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        ToggleField(
            title = stringResource(R.string.auto_backup_enable),
            description = stringResource(R.string.auto_backup_enable_desc),
            checked = settings.autoBackupEnabled,
            onChange = { session.setAutoBackupEnabled(it) }
        )
        if (!settings.autoBackupEnabled) return@Section

        // --- 保存先 ---
        InfoRow(
            label = stringResource(R.string.auto_backup_folder),
            value = folderLabel(settings.autoBackupFolder)
                ?: stringResource(R.string.auto_backup_folder_none)
        )
        ActionButton(
            label = stringResource(R.string.auto_backup_folder_pick),
            onClick = { runCatching { folderPicker.launch(null) } }
        )

        // --- 間隔 ---
        Text(
            text = stringResource(R.string.auto_backup_interval),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        ChipRow(
            options = listOf(
                AutoBackup.INTERVAL_DAILY, AutoBackup.INTERVAL_WEEKLY, AutoBackup.INTERVAL_MONTHLY
            ),
            selected = settings.autoBackupInterval,
            labels = mapOf(
                AutoBackup.INTERVAL_DAILY to stringResource(R.string.auto_backup_daily),
                AutoBackup.INTERVAL_WEEKLY to stringResource(R.string.auto_backup_weekly),
                AutoBackup.INTERVAL_MONTHLY to stringResource(R.string.auto_backup_monthly)
            ),
            onSelect = { save(interval = it) }
        )
        when (settings.autoBackupInterval) {
            AutoBackup.INTERVAL_WEEKLY -> {
                Text(
                    text = stringResource(R.string.auto_backup_dow),
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                val dowLabels = listOf(
                    R.string.auto_backup_sun, R.string.auto_backup_mon, R.string.auto_backup_tue,
                    R.string.auto_backup_wed, R.string.auto_backup_thu, R.string.auto_backup_fri,
                    R.string.auto_backup_sat
                ).map { stringResource(it) }
                ChipRow(
                    // Calendar.DAY_OF_WEEK と同じ 1=日 … 7=土 で持つ (計算側と数え方を揃える)。
                    options = (1..7).map { it.toString() },
                    selected = settings.autoBackupDayOfWeek.toString(),
                    labels = (1..7).associate { it.toString() to dowLabels[it - 1] },
                    onSelect = { save(dayOfWeek = it.toIntOrNull() ?: Calendar.SUNDAY) }
                )
            }
            AutoBackup.INTERVAL_MONTHLY -> {
                // ⚠ 目盛りの文言はここで先に解いておく。valueLabel は @Composable ではないので
                // 中で stringResource を呼べない。
                val dayFormat = stringResource(R.string.auto_backup_dom_value)
                SliderField(
                    title = stringResource(R.string.auto_backup_dom),
                    value = settings.autoBackupDayOfMonth.toFloat(),
                    range = 1f..28f,
                    steps = 26,
                    valueLabel = { dayFormat.format(it.toInt()) },
                    onChange = { save(dayOfMonth = it.toInt()) }
                )
            }
        }

        // --- 時刻 ---
        // 端末標準の時刻ピッカーを使う。スライダー 2 本より速く、24 時間表示の指定も端末に従う。
        InfoRow(
            label = stringResource(R.string.auto_backup_time),
            value = "%02d:%02d".format(settings.autoBackupHour, settings.autoBackupMinute),
            onClick = {
                runCatching {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m -> save(hour = h, minute = m) },
                        settings.autoBackupHour,
                        settings.autoBackupMinute,
                        android.text.format.DateFormat.is24HourFormat(context)
                    ).show()
                }
            }
        )

        // --- 世代 ---
        val keepFormat = stringResource(R.string.auto_backup_keep_value)
        SliderField(
            title = stringResource(R.string.auto_backup_keep),
            value = settings.autoBackupKeep.toFloat(),
            range = AutoBackup.KEEP_MIN.toFloat()..AutoBackup.KEEP_MAX.toFloat(),
            steps = AutoBackup.KEEP_MAX - AutoBackup.KEEP_MIN - 1,
            valueLabel = { keepFormat.format(it.toInt()) },
            onChange = { save(keep = it.toInt()) }
        )

        // --- 次回と最後 ---
        val fmt = remember { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }
        val nextAt = AutoBackup.nextAt(
            interval = settings.autoBackupInterval,
            dayOfWeek = settings.autoBackupDayOfWeek,
            dayOfMonth = settings.autoBackupDayOfMonth,
            hour = settings.autoBackupHour,
            minute = settings.autoBackupMinute,
            from = System.currentTimeMillis()
        )
        Text(
            text = stringResource(R.string.auto_backup_next, fmt.format(Date(nextAt))),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        val lastResult = settings.autoBackupLastResult
        val failed = lastResult.startsWith("err:")
        Text(
            text = when {
                settings.autoBackupLastAt == 0L -> stringResource(R.string.auto_backup_last_never)
                failed -> stringResource(
                    R.string.auto_backup_last_err,
                    fmt.format(Date(settings.autoBackupLastAt)),
                    stringResource(AutoBackup.reasonRes(lastResult))
                )
                else -> stringResource(
                    R.string.auto_backup_last_ok,
                    fmt.format(Date(settings.autoBackupLastAt)),
                    lastResult.removePrefix("ok:")
                )
            },
            color = if (failed) ZtsError else ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        // 夜中まで待たずに 1 本作って、設定が本当に効いているかその場で確かめられるように。
        ActionButton(
            label = stringResource(
                if (running) R.string.auto_backup_running else R.string.auto_backup_run_now
            ),
            onClick = {
                if (running) return@ActionButton
                running = true
                scope.launch {
                    withContext(Dispatchers.IO) { AutoBackup.runAndRecord(context) }
                    running = false
                }
            }
        )
    }
}

/**
 * 保存先フォルダの見せ方。SAF の tree URI は人に見せる形ではないので、
 * ドキュメント ID (`primary:Download/z2term`) の末尾だけ出す。未選択なら null。
 */
private fun folderLabel(treeUri: String): String? {
    if (treeUri.isEmpty()) return null
    val decoded = Uri.decode(treeUri.substringAfterLast("/"))
    return decoded.substringAfterLast(':').ifEmpty { decoded }
}

/**
 * 自分で作るキー配列 (0.8.408・段階 2)。
 *
 * ⚠ **まだ「複製して切り替える」まで。** キーの中身を編集する画面 (段の増減・幅・割り当て) は
 * 次の段階。ここで先に一覧と切替を出しておくのは、エディタが「一覧から 1 枚を開く」形に
 * なるので、入口が先に要るため。
 *
 * ⚠ 複製すると**幅が [KeyWidth.Auto] へ戻る** ([asTemplate])。プリセットは見た目を動かさない
 * ために幅を全部固定で書いてあり、そのまま複製すると「1 つ広げても他が縮まない」テンプレートに
 * なってしまう。説明文でその 1 点だけを断ってある。
 */
@Composable
private fun KeyLayoutSection(settings: AppSettings.Snapshot, session: TerminalSession) {
    val context = LocalContext.current
    val layouts = remember(settings.keyboardLayoutsJson) {
        KeyLayoutJson.listFromJsonString(settings.keyboardLayoutsJson)
    }
    // ⚠ 束に無い id が選ばれていることは普通に起きる (別の端末の設定を戻した等)。
    // その場合は「既定」を選んでいる扱いにする — 一覧に無いものを選択中に見せない。
    val active = layouts.firstOrNull { it.id == settings.keyboardLayoutActiveId }
    val newName = stringResource(R.string.settings_key_layout_new_name)
    val defaultLabel = stringResource(R.string.settings_key_layout_default)
    // ⚠ 見出しは Composable の外で組む (`stringResource` を lambda の中で呼ばない)。
    val chipLabels = remember(layouts, defaultLabel) {
        LinkedHashMap<String, String>().apply {
            put("", defaultLabel)
            layouts.forEach { put(it.id, it.name.ifBlank { it.id }) }
        }
    }

    Section(title = stringResource(R.string.settings_section_key_layout)) {
        Text(
            text = stringResource(R.string.settings_key_layout_desc),
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        ChipRow(
            options = listOf("") + layouts.map { it.id },
            labels = chipLabels,
            selected = active?.id ?: "",
            onSelect = { session.setKeyboardLayoutActiveId(it) }
        )
        ActionButton(label = stringResource(R.string.settings_key_layout_duplicate)) {
            val style = KeyboardStyle.byId(settings.keyboardStyleId)
            // 複製元は**いま画面に出ているのと同じ英字面**にする。スタイルと面の数で
            // 並びが変わるので、ここで同じ条件を組み直す (TerminalKeyboard と同じ引数)。
            // ⚠ **ここで決まった「面の切替キーの有無」は配列に焼き付く** (プリセットは
            // 毎回決め直している)。英語表示 ∧ 数字面 OFF で複製すると切替キーが無い配列に
            // なり、あとで数字面を ON にしても数字面へ行けない。切替キーを自分で置けるように
            // するのはエディタ (段階 3) の仕事。それまでは「既定」へ戻せば元に戻る。
            val faces = KeyboardFace.available(
                KeyboardFace.orderFrom(settings.keyboardFaceOrder, settings.keyboardNumberFace),
                allowKana = LocaleHelper.language(context) == LocaleHelper.LANG_JA
            )
            val copy = asciiKeyLayout(
                compact = style.id == KeyboardStyle.COMPACT.id,
                hasFaceKey = faces.size > 1,
                symbols = false,
                fourWayFlick = style.fourDirectionFlick
            ).asTemplate(
                id = newKeyLayoutId(layouts),
                name = uniqueKeyLayoutName(layouts, newName)
            )
            session.setKeyboardLayoutsJson(KeyLayoutJson.toJsonString(layouts.upsertLayout(copy)))
            // 作ったらそのまま使う。作っただけで切り替わらないと、押した手応えが無い。
            session.setKeyboardLayoutActiveId(copy.id)
        }
        if (active != null) {
            TextField(
                title = stringResource(R.string.settings_key_layout_name),
                placeholder = newName,
                value = active.name,
                onChange = { name ->
                    session.setKeyboardLayoutsJson(
                        KeyLayoutJson.toJsonString(layouts.renameLayout(active.id, name))
                    )
                }
            )
            Text(
                text = stringResource(R.string.settings_key_layout_symbols_note),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            ActionButton(
                label = stringResource(R.string.settings_key_layout_delete),
                danger = true
            ) {
                // ⚠ **先に選び直してから消す。** 逆にすると、消えた id を選んだままの一瞬が
                // あり、その間キーボードは既定へ落ちて戻る (画面がちらつく)。
                session.setKeyboardLayoutActiveId(
                    nextActiveAfterRemove(settings.keyboardLayoutActiveId, active.id)
                )
                session.setKeyboardLayoutsJson(
                    KeyLayoutJson.toJsonString(layouts.removeLayout(active.id))
                )
            }
        }
    }
}

/**
 * 使い方 (Tips) — **画面に出ていない操作**を並べる読み物 (0.8.399)。
 *
 * ⛔ **持っていない機能を書かない。** 1 つでも「書いてあるのに効かない」があると Tips 全体が
 * 信用されなくなる (「Ctrl+T でスクロール」は z2term の機能ではないので載せていない)。
 * ⛔ ここに設定 (トグル) を混ぜない。読み物として上から読めることに価値がある。
 *
 * 見た目は他の設定セクションと同じ [Section] を使う。Tips だけ別の意匠にすると、
 * 設定の中に別のアプリが挟まったように見える。
 */
@Composable
private fun TipsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TipItem(R.string.tip_toolbar_title, R.string.tip_toolbar_body)
        TipItem(R.string.tip_tab_close_title, R.string.tip_tab_close_body)
        TipItem(R.string.tip_tab_reorder_title, R.string.tip_tab_reorder_body)
        TipItem(R.string.tip_esc_flick_title, R.string.tip_esc_flick_body)
        TipItem(R.string.tip_backspace_flick_title, R.string.tip_backspace_flick_body)
        TipItem(R.string.tip_gui_scroll_title, R.string.tip_gui_scroll_body)
        TipItem(R.string.tip_z2_commands_title, R.string.tip_z2_commands_body)
        TipItem(R.string.tip_macro_title, R.string.tip_macro_body)
    }
}

/** Tips 1 件 = 「操作」の見出し + 「何が起きるか」の本文。 */
@Composable
private fun TipItem(@StringRes titleRes: Int, @StringRes bodyRes: Int) {
    Section(title = stringResource(titleRes)) {
        Text(
            text = stringResource(bodyRes),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZtsGreen.copy(alpha = 0.75f))
            )
            Text(
                text = title,
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        content()
    }
}

@Composable
private fun ThemeChipRow(
    themes: List<TerminalTheme>,
    selectedName: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        themes.forEach { theme ->
            ThemeChip(
                theme = theme,
                selected = theme.name == selectedName,
                onClick = { onSelect(theme.name) }
            )
        }
    }
}

@Composable
private fun ThemeChip(
    theme: TerminalTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) ZtsGreen else ZtsBorder
    val bg = if (selected) ZtsGreen.copy(alpha = 0.12f) else ZtsBgCard
    val fg = if (selected) ZtsGreen else ZtsTextPrimary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = theme.name,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
        // 代表色 6 色 (bg, fg, red, green, blue, yellow)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, ZtsBorder, RoundedCornerShape(3.dp)),
            horizontalArrangement = Arrangement.Start
        ) {
            ThemeSwatch(Color(theme.background))
            ThemeSwatch(Color(theme.foreground))
            ThemeSwatch(Color(theme.red))
            ThemeSwatch(Color(theme.green))
            ThemeSwatch(Color(theme.blue))
            ThemeSwatch(Color(theme.yellow))
        }
    }
}

@Composable
private fun ThemeSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 12.dp)
            .background(color)
    )
}

@Composable
private fun FontChipRow(
    options: List<TerminalFontOption>,
    selectedId: String,
    isAvailable: (TerminalFontOption) -> Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            FontChip(
                option = opt,
                available = isAvailable(opt),
                selected = opt.id == selectedId,
                onClick = { onSelect(opt.id) }
            )
        }
    }
}

@Composable
private fun FontChip(
    option: TerminalFontOption,
    available: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = when {
        selected -> ZtsGreen
        !available -> ZtsBorder
        else -> ZtsBorder
    }
    val bg = if (selected) ZtsGreen.copy(alpha = 0.12f) else ZtsBgCard
    val fg = when {
        !available -> ZtsTextSecondary.copy(alpha = 0.5f)
        selected -> ZtsGreen
        else -> ZtsTextPrimary
    }
    val fontFamily = if (available) rememberTerminalFontFamily(option) else FontFamily.Monospace
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .let { if (available) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = option.displayName + if (!available) " (未配置)" else "",
            color = fg,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
        // 実フォントでサンプル表示。digit と紛らわしい文字 (0/O, 1/l/I) を並べる
        Text(
            text = "Aa Bb 0Oo 1Il",
            color = if (available) ZtsTextPrimary else ZtsTextSecondary.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontFamily = fontFamily
        )
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    labels: Map<String, String> = emptyMap(),
    enabled: Map<String, Boolean> = emptyMap(),
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            val active = (opt == selected)
            val isEnabled = enabled[opt] != false
            val display = labels[opt] ?: opt
            val bg = if (active) ZtsGreen.copy(alpha = 0.18f) else ZtsBgCard
            val border = if (active) ZtsGreen else ZtsBorder
            val fg = when {
                !isEnabled -> ZtsTextSecondary.copy(alpha = 0.5f)
                active -> ZtsGreen
                else -> ZtsTextPrimary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(8.dp))
                    .let { if (isEnabled) it.clickable { onSelect(opt) } else it }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = display,
                    color = fg,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SliderField(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = ZtsTextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(value),
                color = ZtsGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ZtsGreen,
                activeTrackColor = ZtsGreen,
                inactiveTrackColor = ZtsBorder
            )
        )
    }
}

/**
 * ツールバーに出すボタンを選ぶ行 (要望: ボタンが増えても各自で減らせるように)。
 *
 * 実際のツールバーと同じ見た目のチップを並べ、**タップで出す / 隠すを切り替える**。
 * 出しているものは緑で点灯、隠しているものは暗く落として一目で分かるようにする。
 * ⚙ 設定は隠すと設定画面へ戻れなくなるので切り替えられない (押しても変わらない)。
 *
 * 並べ替えはここではやらない。端末画面でボタンを長押し→左右ドラッグ (既存の作法) のまま。
 */
@Composable
private fun ToolbarVisibilityRow(hidden: String, onToggle: (String) -> Unit) {
    val hiddenIds = ToolbarButtons.parseHidden(hidden)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolbarButtons.CATALOG.forEach { spec ->
            val shown = spec.id !in hiddenIds
            val bg = when {
                !spec.canHide -> ZtsBgCard
                shown -> ZtsGreen
                else -> ZtsBgCard.copy(alpha = 0.35f)
            }
            val fg = when {
                !spec.canHide -> ZtsTextSecondary
                shown -> Color.Black
                else -> ZtsTextSecondary.copy(alpha = 0.4f)
            }
            val border = when {
                !spec.canHide -> ZtsBorder
                shown -> ZtsGreen
                else -> ZtsBorder.copy(alpha = 0.35f)
            }
            Column(
                modifier = Modifier.width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(6.dp))
                        .then(
                            if (spec.canHide) Modifier.clickable { onToggle(spec.id) } else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = spec.icon,
                        color = fg,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = stringResource(spec.labelRes),
                    color = if (shown) ZtsTextPrimary else ZtsTextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 隠せないボタンは理由を短く添える (押しても反応しないのを不具合と思わせない)。
                if (!spec.canHide) {
                    Text(
                        text = stringResource(R.string.settings_toolbar_always),
                        color = ZtsTextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleField(
    title: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    /** true の間はトグル不可にして薄く見せ、タップは [onLockedTap] へ回す (ツールバーの dimmed と同じ扱い)。 */
    locked: Boolean = false,
    onLockedTap: (() -> Unit)? = null,
) {
    val dim = if (locked) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (locked && onLockedTap != null) Modifier.clickable { onLockedTap() } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).alpha(dim)) {
            Text(
                text = title,
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            if (description != null) {
                Text(
                    text = description,
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Switch(
            checked = checked,
            // ロック中は null にしてスイッチ自身がタップを食わないようにし、行の clickable へ通す。
            onCheckedChange = if (locked) null else onChange,
            modifier = Modifier.alpha(dim),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ZtsGreen,
                uncheckedThumbColor = ZtsTextSecondary,
                uncheckedTrackColor = ZtsBgCard,
                uncheckedBorderColor = ZtsBorder
            )
        )
    }
}

/** 先頭追記のログがこのサイズを超えたら設定画面に注意を出す。 */
private const val LOG_SIZE_WARN_BYTES = 10L * 1024 * 1024

/**
 * ログを目で追うコマンド例。**追記方向で新着が来る場所が逆になる**ので出すコマンドを変える。
 *
 *  - 末尾追記 (新着が下): `tail -f` でそのまま流れる。
 *  - 先頭追記 (新着が上): 新着はファイル末尾に来ないので `tail -f` は永久に何も出さない。
 *    先頭を定期的に出し直す (`watch` + `head`) 形にする。
 *
 * ここはあくまで「中身を目で見る」用。マクロから読むときは形式・追記方向のどちらにも依存しない
 * 差分読み (同梱サンプル参照) を使うこと。
 */
private fun readLogCommand(path: String, prepend: Boolean): String =
    if (prepend) "watch -n 1 head -n 20 $path" else "tail -f $path"

/**
 * 設定画面に載せる「コマンド例」を **タップでクリップボードにコピー** できる形で見せる共通部品。
 *
 * 設定画面の例をターミナルで打ち直すのは端末では手間なので、例を出す箇所はコピーできるようにする。
 * コピー後はトーストで結果を返す (押しても何も起きないように見えるのを避ける)。
 */
@Composable
private fun CopyableCommand(label: String, command: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = command,
            color = ZtsGreen,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(4.dp))
                .clickable {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    cm?.setPrimaryClip(
                        android.content.ClipData.newPlainText("command", command)
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_cmd_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/**
 * 「新しいものを先頭に」が ON で、かつログが [LOG_SIZE_WARN_BYTES] を超えたときだけ出す注意書き。
 *
 * 先頭追記は 1 行書くたびにファイル全体を読み直して書き戻すため、肥大するほど 1 件あたりのコストが
 * 増え、最後にはメモリ不足で記録できなくなる (末尾追記はサイズの影響を受けない)。サイズ上限による
 * ローテーションを撤廃した代わりに、危険域に入ったことをユーザーが自分で気付けるようにする。
 *
 * [bytes] は呼び出し側が `remember` で取得する (設定を開いた時点のサイズ。毎コンポーズで stat しない)。
 */
@Composable
private fun LogSizeWarning(bytes: Long, prepend: Boolean, path: String) {
    if (!prepend || bytes < LOG_SIZE_WARN_BYTES) return
    val mb = remember(bytes) { String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0) }
    // 周りの補助テキスト (10sp・secondary) と同じ見た目だと注意だと気付けないため、
    // 警告色の枠＋淡い背景で囲み、本文も本文色・12sp で読ませる。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsWarning.copy(alpha = 0.12f))
            .border(1.dp, ZtsWarning, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_log_size_warn, mb),
            color = ZtsWarning,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = stringResource(R.string.settings_log_size_warn_desc),
            color = ZtsTextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        CopyableCommand(
            label = stringResource(R.string.settings_log_size_warn_cmd_label),
            command = ": > $path"
        )
    }
}

@Composable
private fun TextField(
    title: String,
    placeholder: String,
    value: String,
    /** 数字しか入らない欄は数字のキーパッドで開く (0.8.389)。 */
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    // draft はローカルに保持し、外部 (プリセット選択等) で value が変わったときだけ同期する。
    // remember(value) にすると、入力→onChange→DataStore 書込→flow 再emit→value 変化 で
    // 毎キーストロークごとに TextFieldValue が作り直されてカーソルが先頭へ飛ぶ (途中編集不可・
    // 文字が逆順に見える) 不具合になるため、自分の編集による value 変化では作り直さない。
    var draft by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != draft.text) {
            draft = TextFieldValue(value, TextRange(value.length))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (draft.text.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ZtsTextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onChange(it.text)
                },
                textStyle = TextStyle(
                    color = ZtsTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ZtsGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val border = if (danger) ZtsError else ZtsBorder
    val fg = if (danger) ZtsError else ZtsTextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ZtsBgSecondary)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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

/** ストレージ上に rootfs を持つ OS 1 件分のメタ (削除 UI 用)。使用量は別途 [computeOsSizes] で。 */
private data class InstalledOs(val id: String, val displayName: String)

/**
 * filesDir/distros/<id> 配下に展開済みの OS を **ディレクトリ列挙だけ** で列挙する (使用量は付けない)。
 * これは軽いので合成中 (メインスレッド) に呼んでも問題ない。表示順は名前で安定させ、
 * 後から使用量が判明しても行が並べ替わらないようにする (位置ずれ防止)。
 */
private fun listInstalledOs(context: Context): List<InstalledOs> {
    val distrosDir = java.io.File(context.filesDir, "distros")
    val dirs = distrosDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
    return dirs.map { dir ->
        InstalledOs(id = dir.name, displayName = DistroSpec.byId(dir.name)?.displayName ?: dir.name)
    }.sortedBy { it.displayName }
}

/**
 * [listInstalledOs] で得た各 OS の rootfs 使用量 (id → bytes) を集計する。
 * symlink は辿らず実ファイルのみ加算する (rootfs 内の循環 symlink で詰まらないように)。
 * ファイル全走査があるので IO スレッドから呼ぶこと。
 */
private fun computeOsSizes(list: List<InstalledOs>, context: Context): Map<String, Long> {
    val distrosDir = java.io.File(context.filesDir, "distros")
    return list.associate { it.id to approxDirSize(java.io.File(distrosDir, it.id)) }
}

private fun approxDirSize(dir: java.io.File): Long {
    var total = 0L
    dir.walkTopDown()
        // symlink のディレクトリには入らない (循環・二重計上を防ぐ)
        .onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .forEach { f ->
            if (f.isFile && !java.nio.file.Files.isSymbolicLink(f.toPath())) total += f.length()
        }
    return total
}


private fun formatStorageSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}

/** OS データ削除セクションの 1 行: 名前 + 使用量 + 削除ボタン (使用中は削除不可)。 */
@Composable
private fun OsDataRow(
    name: String,
    sizeLabel: String,
    isActive: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = ZtsTextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = sizeLabel,
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (isActive) {
            Text(
                text = stringResource(R.string.settings_delete_os_in_use),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ZtsBgSecondary)
                    .border(1.dp, ZtsError, RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_delete_os_button),
                    color = ZtsError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
