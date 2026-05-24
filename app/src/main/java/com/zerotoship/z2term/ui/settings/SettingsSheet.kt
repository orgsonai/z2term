package com.zerotoship.z2term.ui.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.distro.DistroBundle
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.emulator.AvailableThemes
import com.zerotoship.z2term.emulator.TerminalTheme
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
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
import kotlinx.coroutines.launch

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
 *  - 端末リセット (current session.clearOutput + restart)
 *  - クリップボード貼り付け
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

            Section(title = "テーマ") {
                val customTheme by CustomThemeStore.theme.collectAsState()
                ThemeChipRow(
                    themes = AvailableThemes + listOfNotNull(customTheme),
                    selectedName = settings.themeName,
                    onSelect = { session.setThemeName(it) }
                )
                ActionButton(
                    label = if (customTheme == null) "独自テーマを作成…" else "独自テーマを編集…",
                    onClick = onEditCustomTheme
                )
                Text(
                    text = "色を自分で選んだテーマを 1 つ作れます。背景/前景/アクセント等が" +
                        "アプリ全体に反映されます。",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Section(title = "フォントファミリー") {
                FontChipRow(
                    options = TerminalFontOptions.ALL,
                    selectedId = settings.fontId,
                    isAvailable = { TerminalFontOptions.isAvailable(context, it) },
                    onSelect = { session.setFontId(it) }
                )
            }

            SliderField(
                title = "フォントサイズ",
                value = settings.fontSizeSp,
                range = AppSettings.MIN_FONT_SIZE_SP..AppSettings.MAX_FONT_SIZE_SP,
                steps = 23,  // 8..32 を 1sp 刻み = 24 値 = 23 steps
                valueLabel = { "${it.toInt()} sp" },
                onChange = { session.setFontSize(it) }
            )

            SliderField(
                title = "スクロールバック行数",
                value = settings.scrollbackLines.toFloat(),
                range = AppSettings.MIN_SCROLLBACK_LINES.toFloat()..AppSettings.MAX_SCROLLBACK_LINES.toFloat(),
                steps = 49,  // 500..50000 を 1000 刻みで 50 値
                valueLabel = { "${it.toInt()} 行" },
                onChange = { session.setScrollbackLines(it.toInt()) }
            )

            Section(title = "ディストロ (切替で現在のセッションを再起動)") {
                ChipRow(
                    options = DistroSpec.ALL.map { it.id },
                    labels = DistroSpec.ALL.associate {
                        it.id to (it.displayName + (it.approxDownload?.let { s -> " ⬇$s" } ?: ""))
                    },
                    selected = settings.distroId,
                    onSelect = { id ->
                        if (id != settings.distroId) {
                            // 切替を保存して override 付きで再起動 (settingsFlow 反映待ちの
                            // race を回避)。非同梱なら起動時に DL → 展開が走る。
                            session.switchDistro(id)
                            onDismiss()
                        }
                    }
                )
                Text(
                    text = "Alpine は同梱。Ubuntu / Arch / Kali は初回切替時に自動ダウンロード (Wi-Fi 推奨)。",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Section(title = "ログインシェル (次回セッション以降に反映)") {
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
                        if (rootfsReady && shellInstalled[shell] == false) "$shell (未インストール)"
                        else shell
                    },
                    selected = settings.loginShell,
                    onSelect = { session.setLoginShell(it) }
                )
                if (rootfsReady && shellInstalled[settings.loginShell] == false) {
                    Text(
                        text = "⚠ ${settings.loginShell} はこのディストロに未インストールです。" +
                            "インストールするまで反映されず、起動時に既定シェル → /bin/sh へ" +
                            "自動フォールバックします (例: apk add zsh / apt install zsh)。",
                        color = ZtsWarning,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "未インストールのシェルは反映されず自動フォールバックします。" +
                            "ディストロ側でインストール後に有効になります。",
                        color = ZtsTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            SshAccessHelper(session = session)

            Section(title = "リモート (端末 → 他ホスト)") {
                ActionButton(
                    label = "SSH / SFTP プロファイル…",
                    onClick = onOpenSsh
                )
                Text(
                    text = "保存した SSH 接続先へシェル接続、または SFTP でファイル転送 (一覧 / DL / UL / 削除 / 名前変更)。",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            StorageAccessHelper()

            Section(title = "独自キーボードスタイル") {
                ChipRow(
                    options = KeyboardStyle.ALL.map { it.id },
                    labels = KeyboardStyle.ALL.associate { it.id to it.displayName },
                    selected = settings.keyboardStyleId,
                    onSelect = { session.setKeyboardStyleId(it) }
                )
            }

            Section(title = "GUI のターミナル (次回 GUI 起動から反映)") {
                ChipRow(
                    options = GuiTerminal.ALL.map { it.id },
                    labels = GuiTerminal.ALL.associate { it.id to it.displayName },
                    selected = settings.guiTerminalId,
                    onSelect = { session.setGuiTerminal(it) }
                )
                Text(
                    text = "🖥 GUI タブで開くターミナル。未導入なら初回 GUI 起動時に自動導入されます" +
                        "（Konsole は KDE 系で初回ダウンロードが大きめ・Wi-Fi 推奨）。",
                    color = ZtsTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            ToggleField(
                title = "全角曖昧文字を 2 セル幅扱い",
                description = "CJK ロケール向け (PowerLine 記号などに有効)",
                checked = settings.ambiguousAsWide,
                onChange = { session.setAmbiguousAsWide(it) }
            )

            ToggleField(
                title = "バックグラウンド常駐",
                description = "ON: アプリを閉じてもセッション維持 (通知が出ます)。OFF: 閉じると終了。",
                checked = settings.keepAliveService,
                onChange = { session.setKeepAliveService(it) }
            )

            TextField(
                title = "起動時 init コマンド",
                placeholder = "例: zsh -l",
                value = settings.initCommand,
                onChange = { session.setInitCommand(it) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "ペースト",
                    onClick = { session.pasteFromClipboard() }
                )
                ActionButton(
                    label = "画面クリア",
                    onClick = { session.clearOutput() }
                )
                ActionButton(
                    label = "端末リセット",
                    danger = true,
                    onClick = { session.restart() }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "ディストロ再展開",
                    danger = true,
                    onClick = { session.reinstallDistro() }
                )
                ActionButton(
                    label = "クリーン再インストール",
                    danger = true,
                    onClick = { session.cleanReinstallDistro() }
                )
            }
            Text(
                text = "再展開: rootfs を消して展開し直す。クリーン再インストール: " +
                    "ダウンロード済みデータも消して最初から取得し直す (DL 失敗で壊れたときの復旧用、" +
                    "非同梱ディストロは再ダウンロードが走るので Wi-Fi 推奨)。",
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            AppInfoSection(distroId = settings.distroId)
        }
    }
}

/**
 * アプリ情報セクション (設定末尾)。
 * バージョン / フレーバー / applicationId / ROOTFS_VERSION / 現在の distro と
 * その os-release を表示する。
 */
@Composable
private fun AppInfoSection(distroId: String) {
    val context = LocalContext.current
    // os-release の PRETTY_NAME を rootfs から 1 度だけ読む (軽量なファイル read)
    val osPretty = remember(distroId) {
        runCatching {
            val f = java.io.File(context.filesDir, "distros/$distroId/etc/os-release")
            if (!f.exists()) return@runCatching null
            f.readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')?.trim('"', ' ')
        }.getOrNull()
    }
    Section(title = "アプリ情報") {
        InfoRow("バージョン", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("フレーバー", if (BuildConfig.IS_FOSS) "FOSS" else "Full")
        InfoRow("パッケージ", BuildConfig.APPLICATION_ID)
        InfoRow("rootfs 世代", DistroBundle.ROOTFS_VERSION.toString())
        InfoRow("ディストロ", osPretty ?: distroId)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            text = "Z2Term 設定",
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
