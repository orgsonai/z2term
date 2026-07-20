package com.zerotoship.z2term.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.distro.DistroBundle
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.legal.LicensesDialog
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.proot.GuiTerminal
import android.widget.Toast
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.RootProbe
import android.app.admin.DevicePolicyManager
import com.zerotoship.z2term.service.NotificationLogService
import com.zerotoship.z2term.service.PasswordWatchAdmin
import com.zerotoship.z2term.service.SystemEventService
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.SettingsGroup
import com.zerotoship.z2term.settings.SettingsGroupStore
import com.zerotoship.z2term.settings.BatteryGuard
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.settings.RootfsCacheCleaner
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.theme.TerminalFontOption
import com.zerotoship.z2term.ui.theme.TerminalFontOptions
import com.zerotoship.z2term.ui.theme.rememberTerminalFontFamily
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsError
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.ui.theme.ZtsWarning
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    session: TerminalSession,
    onDismiss: () -> Unit,
    onEditCustomTheme: () -> Unit = {}
) {
    val settings by session.settingsFlow.collectAsState()
    // このタブが実際に起動したエンジン (設定値ではなく実起動結果。信頼できるエンジン表示用)。
    val actualEngine by session.actualEngine.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // distro 切替でダウンロードが要るとき、確認ダイアログの対象 spec を保持 (M8-6 T7)。
    var pendingDistroSwitch by remember { mutableStateOf<DistroSpec?>(null) }
    // 確認ダイアログがクリーンインストール (rootfs + DLキャッシュ削除) かどうか。
    var pendingCleanInstall by remember { mutableStateOf(false) }
    // 「クリーンインストール」チェック。ON のまま OS を選ぶとその OS を入れ直す (シート内ローカル)。
    var distroCleanArmed by remember { mutableStateOf(false) }
    // IME 学習履歴の管理シート。非 null の間 [ImeHistorySheet] を表示する (キーボードパッチ)。
    var imeHistoryOpen by remember { mutableStateOf(false) }
    var serversOpen by remember { mutableStateOf(false) }
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
                    ToggleField(
                        title = stringResource(R.string.settings_keyboard_toggle_bar),
                        description = stringResource(R.string.settings_keyboard_toggle_bar_desc),
                        checked = settings.keyboardToggleBar,
                        onChange = { session.setKeyboardToggleBar(it) }
                    )
                }
            }

            SettingsGroupSection(SettingsGroup.INPUT) {
                // IME 学習履歴 (キーボードパッチ): 件数表示 + 管理ボタン (シートを開く)
                Section(title = stringResource(R.string.settings_section_ime_history)) {
                    val historyVersion by com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore.versionFlow.collectAsState()
                    // approximateCount は version 変化のたびに再評価される (collectAsState 経由)
                    val count = remember(historyVersion) {
                        com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore.approximateCount()
                    }
                    Text(
                        text = stringResource(R.string.settings_ime_history_count, count),
                        color = ZtsTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_ime_history_open),
                        onClick = { imeHistoryOpen = true }
                    )
                }

                // 言語スイッチ。アプリ内で「日本語/English」を切替える (OS Locale ではなく独自管理)。
                // 一度決めれば滅多に変えないため下部に配置。変更時は Activity を recreate() する。
                Section(title = stringResource(R.string.settings_section_language)) {
                    val currentLang = remember { mutableStateOf(LocaleHelper.language(context)) }
                    ChipRow(
                        options = listOf(LocaleHelper.LANG_JA, LocaleHelper.LANG_EN),
                        labels = mapOf(
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

            SettingsGroupSection(SettingsGroup.LINUX) {
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
                            if (distroCleanArmed && spec != null) {
                                // クリーンインストール: rootfs + DL キャッシュを消して入れ直す。
                                // 非同梱 distro は再 DL が走るので確認 ON なら先にダイアログ
                                // (foss の Alpine も effectivelyBundled=false で DL 対象)。
                                if (!spec.effectivelyBundled && settings.confirmBeforeDownload) {
                                    pendingDistroSwitch = spec
                                    pendingCleanInstall = true
                                } else {
                                    distroCleanArmed = false
                                    session.cleanInstallDistro(id)
                                    onDismiss()
                                }
                            } else if (id != settings.distroId) {
                                val extracted = java.io.File(
                                    context.filesDir, "distros/$id/bin"
                                ).exists()
                                // 非同梱 distro が未展開なら初回切替でネットから DL が走る
                                // (foss の Alpine も effectivelyBundled=false で DL 対象)。
                                val needsDownload = spec != null && !spec.effectivelyBundled && !extracted
                                if (needsDownload && settings.confirmBeforeDownload) {
                                    pendingDistroSwitch = spec   // 確認ダイアログを出す
                                    pendingCleanInstall = false
                                } else {
                                    // 切替を保存して override 付きで再起動 (settingsFlow 反映待ちの
                                    // race を回避)。同梱/展開済みなら DL は走らない。
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
                        onSelect = { session.setGuiTerminal(it) }
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

                // 裏機能で解放されたときだけ「実行エンジン」を表示。proot / z2root は非 root で選べ、
                // chroot は root セルフテスト成功 (rootChrootUnlocked) のときだけ選択肢に出す。
                if (settings.engineSelectorUnlocked) {
                    Section(title = stringResource(R.string.settings_section_engine)) {
                        val engineOptions = buildList {
                            // foss は proot prebuilt を同梱せず常に z2root 実走なので、選べても無意味な
                            // PRoot チップは出さない (full のみ proot を選択肢に出す)。
                            if (!BuildConfig.IS_FOSS) add(AppSettings.ENGINE_PROOT)
                            add(AppSettings.ENGINE_Z2ROOT)
                            if (settings.rootChrootUnlocked) add(AppSettings.ENGINE_CHROOT)
                        }
                        // foss で既定値が proot のままだとどのチップも選択表示されないため、
                        // 表示上は z2root を選択済みとして扱う (実走エンジンと一致)。
                        val selectedEngine =
                            if (BuildConfig.IS_FOSS && settings.executionEngine == AppSettings.ENGINE_PROOT)
                                AppSettings.ENGINE_Z2ROOT
                            else settings.executionEngine
                        ChipRow(
                            options = engineOptions,
                            selected = selectedEngine,
                            labels = mapOf(
                                AppSettings.ENGINE_PROOT to stringResource(R.string.settings_engine_proot),
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
                            AppSettings.ENGINE_PROOT -> stringResource(R.string.settings_engine_proot)
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

            SettingsGroupSection(SettingsGroup.ABOUT) {
                AppInfoSection(
                    distroId = settings.distroId,
                    engineUnlocked = settings.engineSelectorUnlocked,
                    // 設定の初期化はアプリ情報とライセンスの間に置く (設定の一番下・要望)。
                    onResetSettings = { pendingReset = true },
                    onToggle = {
                        // 7タップでエンジン選択 (proot / z2root) の表示をトグルする (root 不要)。
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
    if (serversOpen) {
        ServersSheet(session = session, onDismiss = { serversOpen = false })
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
                    context.getString(R.string.settings_root_unlock_countdown, remaining),
                    Toast.LENGTH_SHORT
                ).also { it.show() }
            }
        }
    }
    Section(title = stringResource(R.string.settings_section_app_info)) {
        InfoRow(
            stringResource(R.string.appinfo_version),
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            // クールダウン中は onClick=null で行を非タップ化 (ripple も出ない＝押せないことが分かる)。
            onClick = if (inCooldown) null else versionClick
        )
        InfoRow(stringResource(R.string.appinfo_flavor), if (BuildConfig.IS_FOSS) "FOSS" else "Full")
        InfoRow(stringResource(R.string.appinfo_package), BuildConfig.APPLICATION_ID)
        InfoRow(stringResource(R.string.appinfo_rootfs_generation), DistroBundle.ROOTFS_VERSION.toString())
        InfoRow(stringResource(R.string.appinfo_distro), osPretty ?: distroId)
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
private fun SettingsGroupSection(group: SettingsGroup, content: @Composable () -> Unit) {
    val openState by SettingsGroupStore.openState.collectAsState()
    val open = openState[group.id] ?: group.defaultOpen
    // 見出しがタップできる場所だと分かるように、カードと同じ枠 + 背景を付ける。
    // 開いている間はアクセント寄りの枠にして、開閉状態も枠だけで読めるようにする。
    val headerBg = if (open) ZtsGreen.copy(alpha = 0.10f) else ZtsBgCard
    val headerBorder = if (open) ZtsGreen.copy(alpha = 0.55f) else ZtsBorder
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(headerBg)
                .border(1.dp, headerBorder, RoundedCornerShape(6.dp))
                .clickable { SettingsGroupStore.setOpen(group, !open) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (open) "▾" else "▸",
                color = ZtsGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(group.titleRes),
                color = ZtsGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
        if (open) content()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
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

@Composable
private fun ToggleField(
    title: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
            onCheckedChange = onChange,
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
