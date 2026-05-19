package com.zerotoship.z2term.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.ui.settings.SettingsSheet
import com.zerotoship.z2term.ui.ssh.HostKeyVerificationDialog
import com.zerotoship.z2term.ui.ssh.SshProfilesSheet
import com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/** キーボードモード。CUSTOM=独自キーボード、SYSTEM=OS IME + 特殊キーバー */
enum class KeyboardMode { CUSTOM, SYSTEM }

/** スプリッタを介してキーボード領域の高さを保持する状態 */
private const val DEFAULT_KEYBOARD_HEIGHT_DP = 240f
private const val MIN_KEYBOARD_HEIGHT_DP = 80f
// spacious 4 フリックは naturalHeight 320dp 必要。余裕を持って 520dp まで許容。
private const val MAX_KEYBOARD_HEIGHT_DP = 520f

/**
 * アプリ全体のターミナル画面。
 *
 * 構造:
 *   TopBar           ← セッションラベル / 状態 / IME 切替
 *   TabBar           ← 全セッション + 「+」
 *   コンテンツ領域    ← Renderer + InputView + Floating overlays
 *   Splitter         ← クリックでキーボード折り畳み、ドラッグで高さ調整
 *   キーボード領域    ← CUSTOM=独自、SYSTEM=SpecialKeyBar
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sessions by SessionManager.sessions.collectAsState()
    val activeId by SessionManager.activeId.collectAsState()
    val active = sessions.firstOrNull { it.id == activeId }

    if (active == null) {
        // セッション未生成時のプレースホルダ (通常 ensureFirst で 1 つ存在する)
        Box(modifier.fillMaxSize().background(ZtsBgPrimary))
        return
    }

    var ctrlSticky by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.CUSTOM) }
    var inputViewRef by remember { mutableStateOf<TerminalInputView?>(null) }
    var keyboardCollapsed by remember { mutableStateOf(false) }
    var keyboardHeightDp by remember { mutableStateOf(DEFAULT_KEYBOARD_HEIGHT_DP) }
    var settingsOpen by remember { mutableStateOf(false) }
    var sshSheetOpen by remember { mutableStateOf(false) }

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
            .systemBarsPadding()
            .imePadding()
    ) {
        TopBar(
            session = active,
            keyboardMode = keyboardMode,
            onToggleKeyboardMode = {
                keyboardMode = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
            },
            onOpenSettings = { settingsOpen = true },
            onOpenSsh = { sshSheetOpen = true }
        )

        TabBar(
            sessions = sessions,
            activeId = activeId,
            onSelect = { SessionManager.setActive(it) },
            onClose = { SessionManager.close(it) },
            onNew = { SessionManager.openNew(context) }
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            TerminalRenderer(session = active, modifier = Modifier.fillMaxSize())
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
        }

        SplitterBar(
            collapsed = keyboardCollapsed,
            onToggleCollapse = { keyboardCollapsed = !keyboardCollapsed },
            onDragDeltaDp = { delta ->
                if (keyboardCollapsed && delta < -4f) {
                    // 折り畳み中に上ドラッグで開く
                    keyboardCollapsed = false
                }
                if (!keyboardCollapsed) {
                    // delta は dp 単位、上ドラッグで高さ増、下ドラッグで減
                    keyboardHeightDp = (keyboardHeightDp - delta)
                        .coerceIn(MIN_KEYBOARD_HEIGHT_DP, MAX_KEYBOARD_HEIGHT_DP)
                }
            }
        )

        if (!keyboardCollapsed) {
            when (keyboardMode) {
                KeyboardMode.CUSTOM -> {
                    val settings by active.settingsFlow.collectAsState()
                    val style = KeyboardStyle.byId(settings.keyboardStyleId)
                    // スタイル切替で必要な naturalHeight 未満なら自動で広げる
                    LaunchedEffect(style.id) {
                        val needed = style.naturalHeight.value
                        if (keyboardHeightDp < needed) keyboardHeightDp = needed
                    }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(keyboardHeightDp.dp)
                    ) {
                        TerminalKeyboard(
                            onBytes = { active.writeBytes(it) },
                            onCursorKey = { key -> active.writeBytes(active.emulator.cursorKeyBytes(key)) },
                            onRequestSystemKeyboard = { keyboardMode = KeyboardMode.SYSTEM },
                            style = style
                        )
                    }
                }
                KeyboardMode.SYSTEM -> {
                    // OS IME はシステムが描画するため、こちらは SpecialKeyBar の高さだけ。
                    // 240dp 固定 Box にすると上に空白が出てしまうので wrap-content。
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
            onDismiss = { settingsOpen = false }
        )
    }
    if (sshSheetOpen) {
        SshProfilesSheet(
            onDismiss = { sshSheetOpen = false },
            onConnect = { profile ->
                val newSession = SessionManager.openNew(context)
                newSession.startSsh(profile)
            }
        )
    }
    // ホスト鍵検証はワーカースレッドからブロッキングで呼ばれるため、
    // SSH UI の表示状態に関わらずルートに常駐させる。
    HostKeyVerificationDialog()
}

@Composable
private fun TopBar(
    session: TerminalSession,
    keyboardMode: KeyboardMode,
    onToggleKeyboardMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSsh: () -> Unit
) {
    val label by session.label.collectAsState()
    val cwd by session.cwd.collectAsState()
    val ui by session.uiState.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = ZtsGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
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

        TopBarIconButton(label = "🔌", onClick = onOpenSsh)
        TopBarIconButton(label = "⚙", onClick = onOpenSettings)
        KeyboardToggleButton(keyboardMode, onToggleKeyboardMode)

        Text(
            text = ui.state.name,
            color = ZtsTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TopBarIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = ZtsTextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun KeyboardToggleButton(
    mode: KeyboardMode,
    onClick: () -> Unit
) {
    val label = if (mode == KeyboardMode.CUSTOM) "Aあ" else "z2"
    val bg = if (mode == KeyboardMode.SYSTEM) ZtsGreen else ZtsBgCard
    val fg = if (mode == KeyboardMode.SYSTEM) Color.Black else ZtsTextPrimary
    val border = if (mode == KeyboardMode.SYSTEM) ZtsGreen else ZtsBorder
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
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TabBar(
    sessions: List<TerminalSession>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "+",
                color = ZtsTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TabChip(
    session: TerminalSession,
    active: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val label by session.label.collectAsState()
    val bg = if (active) ZtsBgCard else ZtsBgPrimary
    val border = if (active) ZtsGreen else ZtsBorder
    val fg = if (active) ZtsGreen else ZtsTextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ラベル部分のみクリックでアクティブ化 (× と衝突しないよう独立 clickable)
        Box(
            modifier = Modifier
                .clickable(onClick = onSelect)
                .padding(
                    start = 10.dp,
                    end = if (canClose) 4.dp else 10.dp,
                    top = 5.dp,
                    bottom = 5.dp
                )
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        if (canClose) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    color = ZtsTextSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * ターミナル / キーボード間のスプリッタバー。
 *  - クリック (タップ): キーボード折り畳みトグル
 *  - ドラッグ: キーボード高さを変更 (折り畳み中に上ドラッグで開く)
 */
@Composable
private fun SplitterBar(
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onDragDeltaDp: (Float) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.position.y - lastY
                        if (!dragged) {
                            val totalDy = change.position.y - down.position.y
                            if (kotlin.math.abs(totalDy) > slop) {
                                dragged = true
                            }
                        }
                        if (dragged && dy != 0f) {
                            val deltaDp = with(density) { dy.toDp().value }
                            onDragDeltaDp(deltaDp)
                            change.consume()
                            lastY = change.position.y
                        }
                        if (!change.pressed) {
                            if (!dragged) onToggleCollapse()
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // ハンドルを示すバー
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (collapsed) ZtsGreen else ZtsBorder)
        )
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
