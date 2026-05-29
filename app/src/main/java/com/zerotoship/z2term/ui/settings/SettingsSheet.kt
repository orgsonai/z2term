package com.zerotoship.z2term.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R
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
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.BatteryGuard
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import com.zerotoship.z2term.ui.components.Z2TermDragHandle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 設定シート (ModalBottomSheet)。
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
    onOpenSsh: () -> Unit = {},
    onEditCustomTheme: () -> Unit = {}
) {
    val settings by session.settingsFlow.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // ハンドルタップ等の明示的クローズは常に許可するためのフラグ。
    var forceClose by remember { mutableStateOf(false) }
    // distro 切替でダウンロードが要るとき、確認ダイアログの対象 spec を保持 (M8-6 T7)。
    var pendingDistroSwitch by remember { mutableStateOf<DistroSpec?>(null) }
    // 確認ダイアログがクリーンインストール (rootfs + DLキャッシュ削除) かどうか。
    var pendingCleanInstall by remember { mutableStateOf(false) }
    // 「クリーンインストール」チェック。ON のまま OS を選ぶとその OS を入れ直す (シート内ローカル)。
    var distroCleanArmed by remember { mutableStateOf(false) }
    // IME 学習履歴の管理シート。非 null の間 [ImeHistorySheet] を表示する (キーボードパッチ)。
    var imeHistoryOpen by remember { mutableStateOf(false) }
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
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // スクロール途中の下スワイプ/フリングで誤って閉じるのを防ぐ。
        // 内容が最上部 (scrollState.value == 0) のときだけスワイプ閉じを許可する。
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) forceClose || scrollState.value == 0 else true
        }
    )
    val closeSheet: () -> Unit = {
        forceClose = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgPrimary,
        contentColor = ZtsTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        // ステータスバーの下で留める (シートがステータスバー裏まで伸びるのを防ぐ)
        contentWindowInsets = { WindowInsets.statusBars },
        dragHandle = { Z2TermDragHandle(onClose = closeSheet) }
    ) {
        // 戻るキーはスクロール位置に関わらず常にアニメ付きで閉じる
        // (confirmValueChange でスワイプ閉じを最上部限定にしている分の補完)。
        BackHandler(onBack = closeSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 縦スクロール可能に (項目が画面高を超えても一番下まで到達できる)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsHeader()

            // 言語スイッチ。アプリ内で「日本語/English」を切替える (OS Locale ではなく独自管理)。
                // 変更時は Activity を recreate() してリソース解決をやり直す (文字列・キーボードに即反映)。
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
                steps = 23,  // 8..32 を 1sp 刻み = 24 値 = 23 steps
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
                            // 非同梱 distro は再 DL が走るので確認 ON なら先にダイアログ。
                            if (!spec.bundled && settings.confirmBeforeDownload) {
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
                            // 非同梱 distro が未展開なら初回切替でネットから DL が走る。
                            val needsDownload = spec != null && !spec.bundled && !extracted
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
            val installedOs by produceState(emptyList<InstalledOs>(), osDataRefresh) {
                value = withContext(Dispatchers.IO) { scanInstalledOs(context) }
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
                            sizeLabel = formatStorageSize(os.bytes),
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

            Section(title = stringResource(R.string.settings_section_remote)) {
                ActionButton(
                    label = stringResource(R.string.settings_ssh_sftp_open),
                    onClick = onOpenSsh
                )
                Text(
                    text = stringResource(R.string.settings_ssh_sftp_note),
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            StorageAccessHelper()

            Section(title = stringResource(R.string.settings_section_keyboard_style)) {
                ChipRow(
                    options = KeyboardStyle.ALL.map { it.id },
                    labels = KeyboardStyle.ALL.associate { it.id to stringResource(it.displayNameRes) },
                    selected = settings.keyboardStyleId,
                    onSelect = { session.setKeyboardStyleId(it) }
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
                title = stringResource(R.string.settings_ambiguous_width),
                description = stringResource(R.string.settings_ambiguous_width_desc),
                checked = settings.ambiguousAsWide,
                onChange = { session.setAmbiguousAsWide(it) }
            )

            ToggleField(
                title = stringResource(R.string.settings_keep_alive),
                description = stringResource(R.string.settings_keep_alive_desc),
                checked = settings.keepAliveService,
                onChange = { session.setKeepAliveService(it) }
            )

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
                Text(
                    text = BatteryGuard.PHANTOM_DISABLE_ADB,
                    color = ZtsGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                ActionButton(
                    label = stringResource(R.string.settings_phantom_copy),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "adb", BatteryGuard.PHANTOM_DISABLE_ADB
                            )
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_phantom_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

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

            Spacer(modifier = Modifier.height(4.dp))

            // 端末リセット (画面クリア + 再起動)。画面クリア単体は CTRL+L で行える。
            // ディストロ/GUI のクリーンインストールは各「切替」セクションのチェックへ移動。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = stringResource(R.string.settings_reset_terminal),
                    danger = true,
                    onClick = { session.restart() }
                )
            }

            // 裏機能で解放されたときだけ「実行エンジン」を表示 (proot / chroot)。
            if (settings.rootChrootUnlocked) {
                Section(title = stringResource(R.string.settings_section_engine)) {
                    ChipRow(
                        options = listOf(AppSettings.ENGINE_PROOT, AppSettings.ENGINE_CHROOT),
                        selected = settings.executionEngine,
                        labels = mapOf(
                            AppSettings.ENGINE_PROOT to stringResource(R.string.settings_engine_proot),
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
                }
            }

            AppInfoSection(
                distroId = settings.distroId,
                rootUnlocked = settings.rootChrootUnlocked,
                onUnlock = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_root_unlock_checking),
                        Toast.LENGTH_SHORT
                    ).show()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ProotLauncher(context).probeRootChroot()
                        }
                        val msg = when (result) {
                            is RootProbe.Ok -> {
                                session.setRootChrootUnlocked(true)
                                context.getString(R.string.settings_root_unlock_ok)
                            }
                            is RootProbe.NoRoot ->
                                context.getString(R.string.settings_root_unlock_no_root)
                            is RootProbe.ChrootBlocked ->
                                context.getString(R.string.settings_root_unlock_blocked)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            )
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

    // IME 学習履歴の管理シート (キーボードパッチ)。設定シートと**重ねて**開く。
    if (imeHistoryOpen) {
        ImeHistorySheet(onDismiss = { imeHistoryOpen = false })
    }
}

/**
 * アプリ情報セクション (設定末尾)。
 * バージョン / フレーバー / applicationId / ROOTFS_VERSION / 現在の distro と
 * その os-release を表示する。
 */
@Composable
private fun AppInfoSection(distroId: String, rootUnlocked: Boolean, onUnlock: () -> Unit) {
    val context = LocalContext.current
    // 裏機能: バージョン行を 7 回タップで chroot エンジンを解放 (Android 開発者モードと同作法)。
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
    val versionClick: (() -> Unit)? = if (rootUnlocked) null else {
        {
            tapCount++
            val remaining = 7 - tapCount
            when {
                remaining <= 0 -> { tapCount = 0; onUnlock() }
                remaining in 1..3 -> Toast.makeText(
                    context,
                    context.getString(R.string.settings_root_unlock_countdown, remaining),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    Section(title = stringResource(R.string.settings_section_app_info)) {
        InfoRow(
            stringResource(R.string.appinfo_version),
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = versionClick
        )
        InfoRow(stringResource(R.string.appinfo_flavor), if (BuildConfig.IS_FOSS) "FOSS" else "Full")
        InfoRow(stringResource(R.string.appinfo_package), BuildConfig.APPLICATION_ID)
        InfoRow(stringResource(R.string.appinfo_rootfs_generation), DistroBundle.ROOTFS_VERSION.toString())
        InfoRow(stringResource(R.string.appinfo_distro), osPretty ?: distroId)
    }
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

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_header),
            color = ZtsGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.weight(1f))
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

@Composable
private fun TextField(
    title: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit
) {
    var draft by remember(value) { mutableStateOf(TextFieldValue(value)) }
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

/** ストレージ上に rootfs を持つ OS 1 件分のメタ (削除 UI 用)。 */
private data class InstalledOs(val id: String, val displayName: String, val bytes: Long)

/**
 * filesDir/distros/<id> 配下に展開済みの OS を列挙し、おおよその使用量を付けて返す。
 * symlink は辿らず実ファイルのみ加算する (rootfs 内の循環 symlink で詰まらないように)。
 * ファイル走査があるので IO スレッドから呼ぶこと。
 */
private fun scanInstalledOs(context: Context): List<InstalledOs> {
    val distrosDir = java.io.File(context.filesDir, "distros")
    val dirs = distrosDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
    return dirs.map { dir ->
        InstalledOs(
            id = dir.name,
            displayName = DistroSpec.byId(dir.name)?.displayName ?: dir.name,
            bytes = approxDirSize(dir)
        )
    }.sortedByDescending { it.bytes }
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
