package com.zerotoship.z2term.ui.terminal

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.AppSession
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.GuiScreen
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.clipboard.ClipboardHistorySheet
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.ui.settings.SettingsSheet
import com.zerotoship.z2term.ui.sftp.SftpSheet
import com.zerotoship.z2term.ui.snippets.SnippetsSheet
import com.zerotoship.z2term.ui.ssh.HostKeyVerificationDialog
import com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import com.zerotoship.z2term.ui.terminal.keyboard.ComposingState
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore
import com.zerotoship.z2term.ui.terminal.keyboard.KanaKanjiConverter
import com.zerotoship.z2term.ui.terminal.keyboard.KkcConverter
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
import com.zerotoship.z2term.ui.theme.ZtsGreenDim
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** キーボードモード。CUSTOM=独自キーボード、SYSTEM=OS IME + 特殊キーバー */
enum class KeyboardMode { CUSTOM, SYSTEM }

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
 *   キーボード領域    ← CUSTOM=独自 (style.naturalHeight 固定)、SYSTEM=SpecialKeyBar
 *   Toggle bar       ← 最下段。タップでキーボード表示/非表示 (高さ可変は廃止)
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
    // 自動起動前に DL 確認が要る spec (foss 初回など)。非 null の間ダイアログを出す。
    var pendingInitialDownload by remember(active.id) { mutableStateOf<DistroSpec?>(null) }
    var snippetsSheetOpen by remember { mutableStateOf(false) }
    var clipHistoryOpen by remember { mutableStateOf(false) }
    // SFTP ファイルブラウザ対象のプロファイル (非 null の間シートを表示)
    var sftpProfile by remember { mutableStateOf<SshProfile?>(null) }
    var customThemeEditorOpen by remember { mutableStateOf(false) }
    // スクロールバック検索: 検索バーの開閉 / クエリ / ヒット一覧 / 現在ヒット位置。タブ毎にリセット。
    var searchOpen by remember(active.id) { mutableStateOf(false) }
    var searchQuery by remember(active.id) { mutableStateOf("") }
    var searchMatches by remember(active.id) { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember(active.id) { mutableStateOf(0) }
    // クエリ確定 / 検索バー開閉でヒットを再計算し、先頭ヒットへジャンプする。
    // (実行中コマンドで scrollback が伸びると absRow がずれるが、追従は v2。再入力で再計算される)
    LaunchedEffect(searchOpen, searchQuery) {
        if (searchOpen && searchQuery.isNotEmpty()) {
            val ms = SearchEngine.search(active.emulator.buffer, searchQuery)
            searchMatches = ms
            currentMatchIndex = 0
            if (ms.isNotEmpty()) active.scrollToAbsRow(ms[0].absRow)
        } else {
            searchMatches = emptyList()
            currentMatchIndex = 0
        }
    }
    // 画面消灯ロック (ディスプレイが自動で消えないようにする)。権限不要・フォアグラウンド中のみ
    // 有効・CPU は握らないので WakeLock より安全。状態は設定 (keepScreenOn) に永続化し、端末/GUI
    // 共通の単一フローで画面跨ぎでも維持・次回起動時にも復元する。既定 OFF (放置でのバッテリ消費を避ける)。
    val keepScreenOn = settings.keepScreenOn
    val rootView = LocalView.current
    LaunchedEffect(keepScreenOn) {
        applyKeepScreenOn(context, rootView, keepScreenOn)
    }

    // かな漢字変換: 入力中ひらがな(composing)と候補を保持。確定で PTY へ送出。
    // ただし検索バーを開いて独自キーボード使用中は、確定文字を PTY ではなく検索クエリへ流す
    // (システムキーボードとの二重入力を避ける。詳細は onKeyboardBytes 付近)。
    val composing = remember(active.id) {
        ComposingState(onCommit = { text ->
            if (searchOpen && keyboardMode == KeyboardMode.CUSTOM) {
                searchQuery += text
            } else {
                active.writeBytes(text.toByteArray(Charsets.UTF_8))
            }
        })
    }
    // 辞書はアプリ起動後にバックグラウンドで 1 度だけ読み込む。
    LaunchedEffect(Unit) { KanaKanjiConverter.ensureLoaded(context) }
    LaunchedEffect(Unit) { KkcConverter.ensureLoaded(context) }
    // IME 学習履歴 (確定済み読み→単語) も同タイミングで読み込み、変換候補のランキングに使う。
    LaunchedEffect(Unit) { ImeHistoryStore.ensureLoaded(context) }
    // キーボードモード変更時は変換中バッファを破棄 (OS IME と二重表示を防ぐ)。
    LaunchedEffect(keyboardMode, keyboardCollapsed) { composing.reset() }

    // 保存されたキーボードモードに常に追従する。keyboardMode を変えるのはトグル
    // (= setKeyboardMode で settings に永続化) だけなので、settings に追従しても競合しない。
    // 一度きりの復元だと settingsFlow の初期値 (既定=custom) を先に拾って固定され、
    // CUI/GUI の不一致 (GUI で内蔵+SYS 二重) や再起動時に SYS へ戻らない不具合になる。
    LaunchedEffect(settings.keyboardMode) {
        keyboardMode = if (settings.keyboardMode == "system")
            KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
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
            // 確認 ON かつ初回 DL が走る非同梱 distro (foss の Alpine 等) は、先にダウンロード
            // 確認ダイアログを出してからユーザー同意で起動する。それ以外はそのまま起動。
            val dlSpec = active.downloadOnStartSpec()
            if (dlSpec != null) {
                pendingInitialDownload = dlSpec
            } else {
                active.startTerminal()
            }
        }
    }
    LaunchedEffect(keyboardMode, inputViewRef, active.id) {
        val v = inputViewRef ?: return@LaunchedEffect
        v.session = active
        v.imeEnabled = (keyboardMode == KeyboardMode.SYSTEM)
        if (keyboardMode == KeyboardMode.SYSTEM) v.requestKeyboard()
    }
    // 設定シートを開いたら OS ソフトキーボードを隠す (キーボードを出したまま設定に
    // 入ると、シートとキーボードが重なって操作しづらいため)。
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) inputViewRef?.hideKeyboard()
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
            onPasteHistory = { clipHistoryOpen = true },
            onToggleKeyboardMode = {
                val next = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
                keyboardMode = next
                active.setKeyboardMode(if (next == KeyboardMode.SYSTEM) "system" else "custom")
            },
            onToggleKeyboardVisible = { keyboardCollapsed = !keyboardCollapsed },
            onOpenSettings = { settingsOpen = true },
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { active.setKeepScreenOn(!settings.keepScreenOn) },
            keepAlive = settings.keepAliveService,
            onToggleKeepAlive = { active.setKeepAliveService(!settings.keepAliveService) },
            toolbarOrder = settings.toolbarOrder,
            onReorderToolbar = { active.setToolbarOrder(it) },
            onOpenSnippets = { snippetsSheetOpen = true },
            searchActive = searchOpen,
            onToggleSearch = { searchOpen = !searchOpen }
        )

        TabBar(
            sessions = sessions,
            activeId = activeId,
            onSelect = { SessionManager.setActive(it) },
            onClose = { SessionManager.close(it) },
            onNew = { SessionManager.openNew(context) },
            onNewGui = { SessionManager.openLinkedGui(context) }
        )

        // 横画面 + 左/右配置 + 独自キーボード + 折りたたまれていない時のみ、サイド配置に切替。
        // (OS IME=SYSTEM モードは OS が下端に描くので無条件で下配置)
        // 向きは View.OnLayoutChangeListener で実寸を監視して State に流す
        // (configChanges を declare 済の Activity では LocalConfiguration が即座に
        //  再評価されない事例が報告されているため、Compose に確実に届く経路で更新する)。
        val rootView = LocalView.current
        var isLandscape by remember { mutableStateOf(rootView.width > rootView.height) }
        DisposableEffect(rootView) {
            val listener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val landscape = v.width > v.height
                if (landscape != isLandscape) isLandscape = landscape
            }
            rootView.addOnLayoutChangeListener(listener)
            // 初期値の補正 (factory 直後で 0×0 だったケース)
            isLandscape = rootView.width > rootView.height
            onDispose { rootView.removeOnLayoutChangeListener(listener) }
        }
        val landscapePos = settings.landscapeKeyboardPosition
        val isSideKB = isLandscape
            && (landscapePos == AppSettings.LANDSCAPE_KB_LEFT || landscapePos == AppSettings.LANDSCAPE_KB_RIGHT)
            && !keyboardCollapsed
            && keyboardMode == KeyboardMode.CUSTOM
        val baseStyle = KeyboardStyle.byId(settings.keyboardStyleId)
        // キーボード高さは縦/横で別々の設定値を使う (向きが変わると自動で切り替わる)。
        val kbStyle = scaledKeyboardStyle(
            baseStyle,
            if (isLandscape) settings.landscapeKeyboardHeightDp else settings.portraitKeyboardHeightDp
        )

        // 検索バー入力のルーティング:
        //   検索バーを開いて独自(内蔵)キーボード使用中は、キーボード出力を PTY ではなく検索クエリへ流す。
        //   システムキーボード時は OS IME が直接 BasicTextField に入力するので対象外。
        //   これで「検索バー(OS IME) と 画面下(内蔵キーボード) が二重に出る」状態を解消する (要望)。
        val searchTyping = searchOpen && keyboardMode == KeyboardMode.CUSTOM
        fun routeSearchBytes(bytes: ByteArray) {
            for (ch in String(bytes, Charsets.UTF_8)) {
                when (ch) {
                    '\u007F', '\b' -> if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    '\r', '\n' -> if (searchMatches.isNotEmpty()) {
                        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                        active.scrollToAbsRow(searchMatches[currentMatchIndex].absRow)
                    }
                    '\u001B' -> searchOpen = false                       // ESC で検索を閉じる
                    else -> if (ch.code >= 0x20 && ch.code != 0x7F) searchQuery += ch
                }
            }
        }
        val onKeyboardBytes: (ByteArray) -> Unit = { bytes ->
            if (searchTyping) routeSearchBytes(bytes) else active.writeBytes(bytes)
        }
        val onKeyboardCursor: (com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey) -> Unit = { key ->
            // 検索入力中はカーソルキーを PTY へ送らない (シェル側を乱さない)。
            if (!searchTyping) active.writeBytes(active.emulator.cursorKeyBytes(key))
        }

        Row(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            if (isSideKB && landscapePos == AppSettings.LANDSCAPE_KB_LEFT) {
                key(active.id) {
                    SideKeyboardColumn(
                        style = kbStyle,
                        composing = composing,
                        showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                        widthDp = settings.landscapeKeyboardWidthDp,
                        onBytes = onKeyboardBytes,
                        onCursorKey = onKeyboardCursor
                    )
                }
            }
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
            ) {
                TerminalRenderer(
                    session = active,
                    composingText = composing.text,
                    searchMatches = if (searchOpen) searchMatches else emptyList(),
                    currentMatch = if (searchOpen) searchMatches.getOrNull(currentMatchIndex) else null,
                    modifier = Modifier.fillMaxSize()
                )
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
                        // システムキーボードで内蔵 CTRL を 1 文字に適用したら sticky を解除 (ワンショット)。
                        v.onCtrlConsumed = { ctrlSticky = false }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                TerminalScrollbar(session = active, modifier = Modifier.fillMaxSize())
                ScrollIndicators(session = active, modifier = Modifier.fillMaxSize())
                // 変換候補バー: キーボードの上に浮かせて表示 (キーボード本体の高さは変えない)
                CandidateBar(
                    composing = composing,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                // スクロールバック検索バー (端末領域の上端にオーバーレイ)
                if (searchOpen) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        // システムキーボード使用時だけ OS IME を出す。独自キーボード時は
                        // 内蔵キーボードで検索語を入力する (二重キーボード回避・要望)。
                        systemKeyboard = keyboardMode == KeyboardMode.SYSTEM,
                        matchCount = searchMatches.size,
                        currentIndex = currentMatchIndex,
                        onPrev = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex =
                                    (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                active.scrollToAbsRow(searchMatches[currentMatchIndex].absRow)
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                active.scrollToAbsRow(searchMatches[currentMatchIndex].absRow)
                            }
                        },
                        onClose = { searchOpen = false },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
            if (isSideKB && landscapePos == AppSettings.LANDSCAPE_KB_RIGHT) {
                key(active.id) {
                    SideKeyboardColumn(
                        style = kbStyle,
                        composing = composing,
                        showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                        widthDp = settings.landscapeKeyboardWidthDp,
                        onBytes = onKeyboardBytes,
                        onCursorKey = onKeyboardCursor
                    )
                }
            }
        }

        // キーボード表示/非表示バー。設定 (keyboardToggleBar) が ON のときだけ、従来どおり
        // キーボードの「上」に置く。OFF の人は ⌨ ボタンのダブルタップで表示/非表示する。
        if (settings.keyboardToggleBar) {
            KeyboardToggleBar(
                collapsed = keyboardCollapsed,
                onToggle = { keyboardCollapsed = !keyboardCollapsed }
            )
        }

        if (!keyboardCollapsed) {
            when (keyboardMode) {
                KeyboardMode.CUSTOM -> {
                    if (!isSideKB) {
                        // 高さはスタイルの naturalHeight 固定。これでキーサイズと領域高さが
                        // 常に一致する (旧: 高さ可変でキーサイズが追従せずズレていた)。
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(kbStyle.naturalHeight)
                        ) {
                            // タブ切替でキーボードのサブツリーを作り直す。pointerInput など
                            // 内部 remember 状態が前のタブの composing/session を掴んだまま残り、
                            // 入力がターミナルへ届かなくなる退行を防ぐ (あ⇄ABC 手動切替と同じ効果)。
                            key(active.id) {
                                TerminalKeyboard(
                                    onBytes = onKeyboardBytes,
                                    onCursorKey = onKeyboardCursor,
                                    composing = composing,
                                    style = kbStyle,
                                    // English モードでは日本語フリックボタンを隠す。
                                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA
                                )
                            }
                        }
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
            },
            onConnect = { profile -> active.connectSsh(profile) },
            onSftp = { profile -> sftpProfile = profile }
        )
    }
    if (clipHistoryOpen) {
        ClipboardHistorySheet(
            onDismiss = { clipHistoryOpen = false },
            onSelect = { text -> active.pasteText(text) }
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

    // 自動起動前の DL 確認 (foss 初回など)。OK で起動 (DL→展開)、やめる→IDLE のまま。
    pendingInitialDownload?.let { spec ->
        val sizeHint = spec.approxDownload?.let { " ($it)" } ?: ""
        DownloadConfirmDialog(
            title = stringResource(R.string.confirm_download_title, spec.displayName),
            message = stringResource(R.string.confirm_download_msg, spec.displayName, sizeHint),
            onConfirm = { pendingInitialDownload = null; active.startTerminal() },
            onCancel = { pendingInitialDownload = null }
        )
    }
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
    LaunchedEffect(Unit) { KkcConverter.ensureLoaded(context) }
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
    // 画面消灯ロックは設定 (keepScreenOn) に永続化した端末タブ共通の状態 (画面跨ぎで維持・再起動で復元)。
    val keepScreenOn = settings.keepScreenOn
    var settingsOpen by remember { mutableStateOf(false) }
    var snippetsSheetOpen by remember { mutableStateOf(false) }
    var clipHistoryOpen by remember { mutableStateOf(false) }

    val rootView = LocalView.current
    LaunchedEffect(keepScreenOn) { applyKeepScreenOn(context, rootView, keepScreenOn) }

    // 保存済みキーボードモードに常に追従 (端末と同一仕様)。一度きりの復元だと
    // settingsFlow の初期値を先に拾って固定され「GUI で内蔵+SYS 二重表示」になるため。
    LaunchedEffect(settings.keyboardMode) {
        keyboardMode = if (settings.keyboardMode == "system")
            KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
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

    // 回転・分割で GUI 領域の実寸が変わったら、接続後に Xvnc へ解像度を再ネゴする (P-横画面)。
    // 横画面では幅広の解像度を要求し直すので「縦画面を横に引き伸ばした窮屈な表示」を避け、
    // 枠全体を使える。初回 (起動時サイズ) は rfb と同寸なので RfbClient 側で無視される。
    // 連続するレイアウト確定を debounce で 1 回にまとめる。
    val guiState by gui.state.collectAsState()
    LaunchedEffect(gui.id) {
        snapshotFlow {
            val px = guiAreaPx
            if (guiState != GuiSession.State.CONNECTED || px.width <= 0 || px.height <= 0) {
                null
            } else {
                val mag = settings.guiMagnification.coerceIn(
                    AppSettings.MIN_GUI_MAGNIFICATION, AppSettings.MAX_GUI_MAGNIFICATION
                )
                (px.width / mag).toInt().coerceIn(320, 4096) to
                    (px.height / mag).toInt().coerceIn(320, 4096)
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(350)
            .collect { (w, h) -> gui.requestResize(w, h) }
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
            onPasteHistory = { clipHistoryOpen = true },
            onOpenSnippets = { snippetsSheetOpen = true },
            onToggleKeyboardMode = {
                val next = if (keyboardMode == KeyboardMode.CUSTOM)
                    KeyboardMode.SYSTEM else KeyboardMode.CUSTOM
                keyboardMode = next
                scope.launch {
                    appSettings.setKeyboardMode(if (next == KeyboardMode.SYSTEM) "system" else "custom")
                }
            },
            onToggleKeyboardVisible = { keyboardCollapsed = !keyboardCollapsed },
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { scope.launch { appSettings.setKeepScreenOn(!settings.keepScreenOn) } },
            keepAlive = settings.keepAliveService,
            onToggleKeepAlive = { scope.launch { appSettings.setKeepAliveService(!settings.keepAliveService) } },
            toolbarOrder = settings.toolbarOrder,
            onReorderToolbar = { scope.launch { appSettings.setToolbarOrder(it) } },
            settingsEnabled = terminalForSettings != null,
            onOpenSettings = { settingsOpen = true }
        )

        TabBar(
            sessions = sessions,
            activeId = activeId,
            onSelect = { SessionManager.setActive(it) },
            onClose = { SessionManager.close(it) },
            onNew = { SessionManager.openNew(context) },
            onNewGui = { SessionManager.openLinkedGui(context) }
        )

        // 横画面 + 左/右配置 + 独自キーボード + 折りたたまれていない時のみ、サイド配置に切替。
        // (GUI 領域は onSizeChanged で実寸を測って VNC 解像度を決めるので、サイド配置で
        //  Box が縮めば自動的に GUI もその領域に再ネゴしてフィットする)
        // 向きは View.OnLayoutChangeListener 経由で State 化 (§端末タブと同方針)
        val rootViewGui = LocalView.current
        var isLandscapeGui by remember { mutableStateOf(rootViewGui.width > rootViewGui.height) }
        DisposableEffect(rootViewGui) {
            val listener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val landscape = v.width > v.height
                if (landscape != isLandscapeGui) isLandscapeGui = landscape
            }
            rootViewGui.addOnLayoutChangeListener(listener)
            isLandscapeGui = rootViewGui.width > rootViewGui.height
            onDispose { rootViewGui.removeOnLayoutChangeListener(listener) }
        }
        val landscapePosGui = settings.landscapeKeyboardPosition
        val isSideKBGui = isLandscapeGui
            && (landscapePosGui == AppSettings.LANDSCAPE_KB_LEFT || landscapePosGui == AppSettings.LANDSCAPE_KB_RIGHT)
            && !keyboardCollapsed
            && keyboardMode == KeyboardMode.CUSTOM
        val baseStyleGui = KeyboardStyle.byId(settings.keyboardStyleId)
        val kbStyleGui = scaledKeyboardStyle(
            baseStyleGui,
            if (isLandscapeGui) settings.landscapeKeyboardHeightDp else settings.portraitKeyboardHeightDp
        )

        Row(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            if (isSideKBGui && landscapePosGui == AppSettings.LANDSCAPE_KB_LEFT) {
                SideKeyboardColumn(
                    style = kbStyleGui,
                    composing = composing,
                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                    widthDp = settings.landscapeKeyboardWidthDp,
                    onBytes = { GuiKeyMapper.sendBytes(gui.rfb, it) },
                    onCursorKey = { key -> gui.rfb.tapKey(GuiKeyMapper.keysymForCursor(key)) }
                )
            }
            // GUI 領域を枠線で囲って範囲を明示し、内側の実寸 (onSizeChanged) で解像度を決める。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .border(2.dp, ZtsGreen)
                    .padding(2.dp)
                    .onSizeChanged { guiAreaPx = it }
            ) {
                GuiScreen(
                    session = gui,
                    // 設定シートを開いている間は OS IME を隠す (シートと重ならないように)。
                    imeVisible = keyboardMode == KeyboardMode.SYSTEM && !keyboardCollapsed && !settingsOpen,
                    ctrlSticky = ctrlSticky,
                    onCtrlConsumed = { ctrlSticky = false },
                    modifier = Modifier.fillMaxSize()
                )

                // キーボードを GUI に上乗せ (オーバーレイ。解像度は変えない)。▾ で折りたためる。
                // SYSTEM 時は OS IME の上に出すため imePadding。
                // サイド配置 (横画面 左/右) のときはここに本体は出さない (Row の左/右にある)。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    CandidateBar(composing = composing)
                    // キーボードの上のトグルバー。設定 (keyboardToggleBar) が ON のときだけ表示。
                    if (settings.keyboardToggleBar) {
                        KeyboardToggleBar(
                            collapsed = keyboardCollapsed,
                            onToggle = { keyboardCollapsed = !keyboardCollapsed }
                        )
                    }
                    if (!keyboardCollapsed) {
                        when (keyboardMode) {
                            KeyboardMode.CUSTOM -> {
                                if (!isSideKBGui) {
                                    Box(modifier = Modifier
                                        .fillMaxWidth()
                                        .height(kbStyleGui.naturalHeight)
                                    ) {
                                        TerminalKeyboard(
                                            onBytes = { GuiKeyMapper.sendBytes(gui.rfb, it) },
                                            onCursorKey = { key -> gui.rfb.tapKey(GuiKeyMapper.keysymForCursor(key)) },
                                            composing = composing,
                                            style = kbStyleGui,
                                            showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA
                                        )
                                    }
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
            if (isSideKBGui && landscapePosGui == AppSettings.LANDSCAPE_KB_RIGHT) {
                SideKeyboardColumn(
                    style = kbStyleGui,
                    composing = composing,
                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                    widthDp = settings.landscapeKeyboardWidthDp,
                    onBytes = { GuiKeyMapper.sendBytes(gui.rfb, it) },
                    onCursorKey = { key -> gui.rfb.tapKey(GuiKeyMapper.keysymForCursor(key)) }
                )
            }
        }
    }

    if (settingsOpen && terminalForSettings != null) {
        SettingsSheet(
            session = terminalForSettings,
            onDismiss = { settingsOpen = false },
            // GUI からは独自テーマ編集シートまでは開かない (端末タブで)。
            onEditCustomTheme = { }
        )
    }
    if (snippetsSheetOpen) {
        SnippetsSheet(
            onDismiss = { snippetsSheetOpen = false },
            // 端末は writeBytes だが GUI は keysym 橋渡しで送る (M8-6 T1)。
            onRun = { command -> GuiKeyMapper.sendText(gui.rfb, command) },
            // GUI タブからは SSH 接続の概念が無いのでスニペットタブのみ表示する。
            showSshTab = false
        )
    }
    if (clipHistoryOpen) {
        ClipboardHistorySheet(
            onDismiss = { clipHistoryOpen = false },
            // GUI では選んだ本文を keysym 橋渡しでタイプし、システムクリップボードにも反映する。
            onSelect = { text ->
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("z2term", text))
                GuiKeyMapper.sendText(gui.rfb, text)
            }
        )
    }
    // GUI 起動確認 (初回 DL / クリーンインストール)。OK で起動、やめる→タブを閉じる
    // (パッケージ無しでは表示できないため)。
    pendingGuiStart?.let { (w, h, clean) ->
        DownloadConfirmDialog(
            title = if (clean) stringResource(R.string.gui_confirm_clean_install_title)
                    else stringResource(R.string.gui_confirm_download_title),
            message = if (clean) stringResource(R.string.gui_confirm_clean_install_msg)
                      else stringResource(R.string.gui_confirm_download_msg),
            confirmLabel = if (clean) stringResource(R.string.gui_confirm_clean_install_action)
                           else stringResource(R.string.gui_confirm_download_action),
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
    onPasteHistory: () -> Unit,
    onOpenSnippets: () -> Unit,
    onToggleKeyboardMode: () -> Unit,
    onToggleKeyboardVisible: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    keepAlive: Boolean,
    onToggleKeepAlive: () -> Unit,
    toolbarOrder: String,
    onReorderToolbar: (String) -> Unit,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val label by session.label.collectAsState()
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
        // 残り幅をすべて取る Box に収め、右寄せ。低解像度端末でボタン総幅が画面を超えると
        // 横スクロールで全ボタンに到達できる (はみ出して押せなくなるのを防ぐ・要望)。
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // 端末 TopBar と同じ並べ替え可能ツールバー。GUI は検索が無く、📋/📜 は keysym 橋渡しで
            // GUI へタイプする (M8-6 T1)。⚙ は端末セッションが無いと押せない (settingsEnabled)。
            ReorderableToolbar(
                items = listOf(
                    ToolbarItem(TB_PASTE, "📋", stringResource(R.string.tb_paste), onClick = onPaste, onDoubleClick = onPasteHistory),
                    ToolbarItem(TB_SNIPPETS, "📜", stringResource(R.string.tb_snippets), onClick = onOpenSnippets),
                    ToolbarItem(TB_SCREEN_ON, if (keepScreenOn) "💡" else "🔅", stringResource(R.string.tb_screen_on), active = keepScreenOn, onClick = onToggleKeepScreenOn),
                    ToolbarItem(TB_KEEP_ALIVE, if (keepAlive) "🔒" else "🔓", stringResource(R.string.tb_keep_alive), active = keepAlive, onClick = onToggleKeepAlive),
                    ToolbarItem(TB_KEYBOARD, "⌨", stringResource(R.string.tb_keyboard), active = keyboardMode == KeyboardMode.SYSTEM, onClick = onToggleKeyboardMode, onDoubleClick = onToggleKeyboardVisible),
                    ToolbarItem(TB_SETTINGS, "⚙", stringResource(R.string.tb_settings), enabled = settingsEnabled, onClick = onOpenSettings)
                ),
                savedOrder = toolbarOrder,
                onReorder = onReorderToolbar,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        // 状態名 (CONNECTED 等) は表示しない: 幅が狭いと崩れる & 実用上見ないため (要望で削除)。
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
        GuiSpecialKey("^C") { GuiKeyMapper.sendCtrlCombo(rfb, 'c'.code) }
        GuiSpecialKey("^D") { GuiKeyMapper.sendCtrlCombo(rfb, 'd'.code) }
        GuiSpecialKey("^L") { GuiKeyMapper.sendCtrlCombo(rfb, 'l'.code) }
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
    onPasteHistory: () -> Unit,
    onToggleKeyboardMode: () -> Unit,
    onToggleKeyboardVisible: () -> Unit,
    onOpenSettings: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    keepAlive: Boolean,
    onToggleKeepAlive: () -> Unit,
    toolbarOrder: String,
    onReorderToolbar: (String) -> Unit,
    onOpenSnippets: () -> Unit,
    searchActive: Boolean = false,
    onToggleSearch: () -> Unit = {}
) {
    val label by session.label.collectAsState()
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
        // タブ名 (シェルのタイトル等) は出さず、OS 識別子だけを固定字数で表示する (要望)。
        // これでラベルが伸びて右側のボタンを押し出す事故が無くなり、ボタンが必ず収まる。
        val osLabel = ui.mode.ifBlank { label }.take(10)
        Text(
            text = osLabel,
            color = ZtsGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp)
        )
        // 残り幅をすべて取る Box に収め、右寄せ。低解像度端末でボタン総幅が画面を超えると
        // 横スクロールで全ボタンに到達できる (はみ出して押せなくなるのを防ぐ・要望)。
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // 既定の並び (左→右): 貼付 / コマンド一覧 / 画面消灯ロック / 常駐ロック / 検索 / キーボード切替 / 設定。
            // 常駐ロック (バックグラウンド常駐トグル) は画面消灯ロックの右に置く (要望)。
            // 各ボタンは長押しドラッグで並べ替え可・長押し中は簡易説明をポップアップ表示する。
            ReorderableToolbar(
                items = listOf(
                    ToolbarItem(TB_PASTE, "📋", stringResource(R.string.tb_paste), onClick = onPaste, onDoubleClick = onPasteHistory),
                    ToolbarItem(TB_SNIPPETS, "📜", stringResource(R.string.tb_snippets), onClick = onOpenSnippets),
                    ToolbarItem(TB_SCREEN_ON, if (keepScreenOn) "💡" else "🔅", stringResource(R.string.tb_screen_on), active = keepScreenOn, onClick = onToggleKeepScreenOn),
                    ToolbarItem(TB_KEEP_ALIVE, if (keepAlive) "🔒" else "🔓", stringResource(R.string.tb_keep_alive), active = keepAlive, onClick = onToggleKeepAlive),
                    ToolbarItem(TB_SEARCH, "🔍", stringResource(R.string.tb_search), active = searchActive, onClick = onToggleSearch),
                    ToolbarItem(TB_KEYBOARD, "⌨", stringResource(R.string.tb_keyboard), active = keyboardMode == KeyboardMode.SYSTEM, onClick = onToggleKeyboardMode, onDoubleClick = onToggleKeyboardVisible),
                    ToolbarItem(TB_SETTINGS, "⚙", stringResource(R.string.tb_settings), onClick = onOpenSettings)
                ),
                savedOrder = toolbarOrder,
                onReorder = onReorderToolbar,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        // 状態名 (RUNNING 等) は表示しない: 幅が狭いと崩れる & 実用上見ないため (要望で削除)。
    }
}

// ツールバーのアクション id (並び順の永続化キーに使う・[ReorderableToolbar])。
private const val TB_PASTE = "paste"
private const val TB_SNIPPETS = "snippets"
private const val TB_SCREEN_ON = "screen_on"
private const val TB_KEEP_ALIVE = "keep_alive"
private const val TB_SEARCH = "search"
private const val TB_KEYBOARD = "keyboard"
private const val TB_SETTINGS = "settings"

/** ツールバーの 1 ボタン。[id] は並び順の保存キー、[active] は緑ハイライト、[description] は長押し説明。 */
private class ToolbarItem(
    val id: String,
    val icon: String,
    val description: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    // 設定時のみ有効化されるダブルタップ動作 (📋 貼付ボタンでクリップボード履歴を開く等)。
    val onDoubleClick: (() -> Unit)? = null
)

/**
 * 保存済み並び [saved] と現在表示すべき [present] をマージする。
 * 保存順のうち present に在るものを優先採用し、保存に無い (新規追加された) ボタンを
 * present の既定順で末尾に補う。これでボタンの追加・削除があっても並びが壊れない。
 */
private fun mergeToolbarOrder(saved: List<String>, present: List<String>): List<String> {
    val kept = saved.filter { it in present }
    val rest = present.filter { it !in kept }
    return kept + rest
}

/**
 * 並べ替え可能なツールバー (要望)。
 *  - 通常タップ = そのボタンの動作。
 *  - 長押し = つかんで左右ドラッグで並べ替え + 簡易説明ポップアップを表示。
 * 並びは [savedOrder] (カンマ区切り id) から復元し、変更を [onReorder] で永続化する。
 */
@Composable
private fun ReorderableToolbar(
    items: List<ToolbarItem>,
    savedOrder: String,
    onReorder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val present = items.map { it.id }
    val byId = items.associateBy { it.id }
    val order = remember { mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    // 保存順 / ボタン構成が変わったら並びを作り直す。ドラッグ中だけは触らない
    // (確定時は order をローカル更新済 → 直後の savedOrder 反映で同じ並びに収束しちらつかない)。
    LaunchedEffect(savedOrder, present) {
        if (dragging == null) {
            val merged = mergeToolbarOrder(
                savedOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                present
            )
            order.clear(); order.addAll(merged)
        }
    }
    val widths = remember { mutableStateMapOf<String, Int>() }
    var dragOffset by remember { mutableStateOf(0f) }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    // ドラッグ量が隣ボタンの中心を越えたら order を入れ替え、その分 offset を戻して連続移動。
    fun trySwap() {
        val id = dragging ?: return
        val idx = order.indexOf(id)
        if (idx < 0) return
        if (idx < order.size - 1) {
            val w = (widths[order[idx + 1]] ?: 0) + gapPx
            if (w > gapPx && dragOffset > w / 2f) {
                order.add(idx + 1, order.removeAt(idx)); dragOffset -= w; return
            }
        }
        if (idx > 0) {
            val w = (widths[order[idx - 1]] ?: 0) + gapPx
            if (w > gapPx && dragOffset < -w / 2f) {
                order.add(idx - 1, order.removeAt(idx)); dragOffset += w; return
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        order.forEach { id ->
            val item = byId[id] ?: return@forEach
            key(id) {
                val isDrag = dragging == id
                Box(
                    modifier = Modifier
                        .onSizeChanged { widths[id] = it.width }
                        .zIndex(if (isDrag) 1f else 0f)
                        .graphicsLayer { translationX = if (isDrag) dragOffset else 0f }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = id; dragOffset = 0f },
                                onDragEnd = { dragging = null; dragOffset = 0f; onReorder(order.joinToString(",")) },
                                onDragCancel = { dragging = null; dragOffset = 0f; onReorder(order.joinToString(",")) },
                                onDrag = { change, amount -> change.consume(); dragOffset += amount.x; trySwap() }
                            )
                        }
                ) {
                    ToolbarChip(
                        icon = item.icon,
                        active = item.active,
                        enabled = item.enabled,
                        onClick = item.onClick,
                        onDoubleClick = item.onDoubleClick
                    )
                    if (isDrag) ToolbarTooltip(item.description)
                }
            }
        }
    }
}

/** ツールバー 1 ボタンの見た目 (active=緑ハイライト / enabled=false でグレーアウト)。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarChip(
    icon: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null
) {
    val bg = when {
        !enabled -> ZtsBgCard.copy(alpha = 0.35f)
        active -> ZtsGreen
        else -> ZtsBgCard
    }
    val fg = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.4f)
        active -> Color.Black
        else -> ZtsTextPrimary
    }
    val border = when {
        !enabled -> ZtsBorder.copy(alpha = 0.35f)
        active -> ZtsGreen
        else -> ZtsBorder
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .then(
                when {
                    !enabled -> Modifier
                    onDoubleClick != null -> Modifier.combinedClickable(
                        onClick = onClick,
                        onDoubleClick = onDoubleClick
                    )
                    else -> Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, color = fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** 長押し中にボタンの真上へ出す簡易説明ポップアップ。 */
@Composable
private fun ToolbarTooltip(text: String) {
    val density = LocalDensity.current
    val offsetY = with(density) { -(40.dp).roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsGreen)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
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
 * スクロールバック検索バー。端末領域の上端にオーバーレイする。
 * 入力フィールド + 件数 (現在/総数) + ↑(前) + ↓(次) + ✕(閉じる)。
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    systemKeyboard: Boolean,
    matchCount: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    // システムキーボード使用時のみ、開いた直後にフォーカスを当てて OS IME を出す。
    // 独自(内蔵)キーボード時は OS IME を出さず、内蔵キーボードからの入力を受ける
    // (検索バーの OS IME と画面下の内蔵キーボードが二重に出るのを防ぐ・要望)。
    LaunchedEffect(systemKeyboard) {
        if (systemKeyboard) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    color = ZtsTextSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                // 独自キーボード時は readOnly にして OS IME を開かせない (タップしても出ない)。
                // 検索語は内蔵キーボード経由で query に流し込まれる。
                readOnly = !systemKeyboard,
                textStyle = TextStyle(
                    color = ZtsTextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(ZtsGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        Text(
            text = stringResource(
                R.string.search_count,
                if (matchCount == 0) 0 else currentIndex + 1,
                matchCount
            ),
            color = ZtsTextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        TopBarIconButton(label = stringResource(R.string.search_prev), onClick = onPrev)
        TopBarIconButton(label = stringResource(R.string.search_next), onClick = onNext)
        TopBarIconButton(label = stringResource(R.string.search_close), onClick = onClose)
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
    // ドラッグ並べ替え (要望): タブを長押ししてから左右にドラッグすると並びを入れ替えられる。
    // 各タブの実測幅 (id -> px) を覚えておき、ドラッグ中のタブが隣のタブの中心を越えたら
    // SessionManager.moveSession で即スワップし、その分だけ dragOffset を戻して連続移動を続ける。
    val tabWidths = remember { mutableStateMapOf<String, Int>() }
    val draggingId = remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableStateOf(0f) }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }

    // ドラッグ量に応じて隣とスワップ。並びの最新値は SessionManager から読む (クロージャの陳腐化回避)。
    fun trySwap() {
        val id = draggingId.value ?: return
        val live = SessionManager.sessions.value
        val idx = live.indexOfFirst { it.id == id }
        if (idx < 0) return
        if (idx < live.size - 1) {
            val nextW = (tabWidths[live[idx + 1].id] ?: 0) + gapPx
            if (nextW > gapPx && dragOffset.value > nextW / 2f) {
                SessionManager.moveSession(idx, idx + 1)
                dragOffset.value -= nextW
                return
            }
        }
        if (idx > 0) {
            val prevW = (tabWidths[live[idx - 1].id] ?: 0) + gapPx
            if (prevW > gapPx && dragOffset.value < -prevW / 2f) {
                SessionManager.moveSession(idx, idx - 1)
                dragOffset.value += prevW
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZtsBgPrimary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // タブ一覧は横スクロール領域 (残り幅) に収め、新規タブボタン (+ / 🖥) は右端に固定する。
        // これでタブが多くても/タブ名が長くてもボタンが押し出されず必ず表示される (要望)。
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // key(sess.id): 並べ替えで順序が変わっても各タブの合成 (とドラッグ中の
            // pointerInput) を同一視して保持する。これが無いと位置ベースのキーになり、
            // 1 回スワップするたびにドラッグジェスチャが切れて連続移動できない (要望)。
            sessions.forEach { sess ->
                key(sess.id) {
                    val isDragging = draggingId.value == sess.id
                    TabChip(
                        session = sess,
                        active = sess.id == activeId,
                        canClose = sessions.size > 1,
                        dragging = isDragging,
                        dragOffsetX = if (isDragging) dragOffset.value else 0f,
                        onWidth = { tabWidths[sess.id] = it },
                        onSelect = { onSelect(sess.id) },
                        onClose = { onClose(sess.id) },
                        onDragStart = { draggingId.value = sess.id; dragOffset.value = 0f },
                        onDrag = { dx -> dragOffset.value += dx; trySwap() },
                        onDragEnd = { draggingId.value = null; dragOffset.value = 0f }
                    )
                }
            }
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
    dragging: Boolean,
    dragOffsetX: Float,
    onWidth: (Int) -> Unit,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val label by session.label.collectAsState()
    val bg = if (dragging) ZtsBgSecondary else if (active) ZtsBgCard else ZtsBgPrimary
    // ドラッグ中は緑枠で「掴んでいる」ことを示す。
    val border = if (dragging || active) ZtsGreen else ZtsBorder
    val fg = if (active) ZtsGreen else ZtsTextSecondary

    // 長押し中だけ「タブ名 + 実行エンジン」をチップ上に出す (要望)。設定を開かなくても
    // どのタブがどのエンジンで動いているか確認できる。長押し→ドラッグの並べ替えと併用で、
    // 押した瞬間 (onDragStart) に表示し、離した/キャンセルで消す。
    var showInfo by remember { mutableStateOf(false) }
    // 実行エンジン名。端末タブは実際に起動したエンジン、GUI タブは GUI 表記。
    val engineText: String = if (session is TerminalSession) {
        val actual by session.actualEngine.collectAsState()
        when (actual) {
            AppSettings.ENGINE_PROOT -> stringResource(R.string.settings_engine_proot)
            AppSettings.ENGINE_Z2ROOT -> stringResource(R.string.settings_engine_z2root)
            AppSettings.ENGINE_CHROOT -> stringResource(R.string.settings_engine_chroot)
            AppSettings.ENGINE_ANDROID_SH -> stringResource(R.string.settings_engine_android_sh)
            else -> stringResource(R.string.settings_engine_current_starting)
        }
    } else {
        stringResource(R.string.tab_popup_engine_gui)
    }

    // 単タップ=アクティブ化 / ダブルタップ=閉じる。× ボタンは廃止 (誤タップ防止 M8-6 T8)。
    // 最後の 1 枚 (canClose=false) はダブルタップでも閉じない。
    // 長押し→左右ドラッグ=並べ替え (要望)。ドラッグ中のタブは前面 (zIndex) + 平行移動で追従。
    Box(
        modifier = Modifier
            .onSizeChanged { onWidth(it.width) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationX = dragOffsetX }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onSelect,
                onDoubleClick = if (canClose) onClose else null
            )
            .pointerInput(session.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { showInfo = true; onDragStart() },
                    onDrag = { change, amount -> change.consume(); onDrag(amount.x) },
                    onDragEnd = { showInfo = false; onDragEnd() },
                    onDragCancel = { showInfo = false; onDragEnd() }
                )
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            // タブ名は最大固定字数で切り詰める (要望)。チップが伸びて新規タブボタンを
            // 押し出さないよう、字数制限 + 上限幅 + 省略を併用する。
            text = label.take(12),
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp)
        )

        if (showInfo) {
            TabInfoPopup(name = label, engine = engineText)
        }
    }
}

/** タブ長押し中にチップ直下へ出す「タブ名 + 実行エンジン」ポップアップ (要望)。 */
@Composable
private fun TabInfoPopup(name: String, engine: String) {
    val density = LocalDensity.current
    // チップの下端に少し被せて出す量と、画面端からの最小マージン。
    val overlapPx = with(density) { 2.dp.roundToPx() }
    val marginPx = with(density) { 4.dp.roundToPx() }
    // チップ中央真下に出しつつ、ポップアップ全体が必ず画面内に収まるようクランプする (要望)。
    // BottomCenter は端のタブで横にはみ出すため、自前 PositionProvider で coerce する。
    val positionProvider = remember(overlapPx, marginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
                val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
                val x = (anchorCenterX - popupContentSize.width / 2).coerceIn(marginPx, maxX)
                val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
                val y = (anchorBounds.bottom - overlapPx).coerceIn(marginPx, maxY)
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgCard)
                .border(1.dp, ZtsGreen, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = name,
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
            )
            Text(
                text = "${stringResource(R.string.tab_popup_engine_label)}: $engine",
                color = ZtsGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

/**
 * ターミナル / キーボード間のトグルバー。
 *
 * タップ (移動量 24dp 未満) でキーボードの表示/非表示を切り替える。
 * フリック入力中に指がバーに掠めても誤動作しないよう、24dp 超のドラッグは無視する。
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
            .height(22.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .pointerInput(Unit) {
                val slopPx = 24.dp.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startPos.x
                            val dy = change.position.y - startPos.y
                            if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > slopPx) {
                                dragged = true
                            }
                            if (!change.pressed) {
                                if (!dragged) onToggle()
                                break
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (collapsed) stringResource(R.string.keyboard_show_button)
                   else stringResource(R.string.keyboard_hide_button),
            color = if (collapsed) ZtsGreen else ZtsBorder,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * [KeyboardStyle] を縦方向だけ拡縮して総高さを [targetHeightDp] にそろえる (縦画面・横画面共通)。
 * 5 行 × 1 行 = 5*keyHeight + α なので、naturalHeight を [targetHeightDp] にそろえて
 * keyHeight も比例拡縮する。フォントサイズはやや控えめに同方向にスケール (0.85〜1.4 倍に丸め)。
 */
private fun scaledKeyboardStyle(base: KeyboardStyle, targetHeightDp: Float): KeyboardStyle {
    val baseNat = base.naturalHeight.value
    val scale = (targetHeightDp / baseNat).coerceIn(0.6f, 2.5f)
    val fontScale = scale.coerceIn(0.85f, 1.4f)
    return base.copy(
        keyHeight = (base.keyHeight.value * scale).dp,
        keyFontSp = base.keyFontSp * fontScale,
        mainKeyFontSp = base.mainKeyFontSp * fontScale,
        flickHintFontSp = base.flickHintFontSp * fontScale,
        naturalHeight = targetHeightDp.dp
    )
}

/**
 * 横画面時に左右どちらかに出すサイドキーボード列。
 *
 * 幅は [widthDp] (設定 `landscapeKeyboardWidthDp`、既定 420dp、可動 280-700dp) で可変。
 * 1 キー = widthDp/10dp 相当 (例: 420dp → 42dp/key)。Spacious スタイルでさらに高さも増える。
 * 高さは Row 内で fillMaxHeight、キーボード本体は上端に揃え、下に余白が出る分は背景色で塗る。
 */
@Composable
private fun SideKeyboardColumn(
    style: KeyboardStyle,
    composing: ComposingState,
    showJapaneseKeyboard: Boolean,
    widthDp: Float,
    onBytes: (ByteArray) -> Unit,
    onCursorKey: (com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey) -> Unit
) {
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .fillMaxHeight()
            .background(ZtsBgPrimary)
            .border(width = 1.dp, color = ZtsBorder)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(style.naturalHeight)
        ) {
            TerminalKeyboard(
                onBytes = onBytes,
                onCursorKey = onCursorKey,
                composing = composing,
                style = style,
                showJapaneseKeyboard = showJapaneseKeyboard
            )
        }
    }
}

/**
 * かな漢字変換の候補バー。キーボードの上に重ねて浮かせる (端末画面の下端にかぶせる)。
 * これによりキーボード本体の高さ・キーサイズは一切変わらない。
 *
 * 通常モード (スプリット未起動):
 *   左端: 入力中ひらがな全体 (タップで生のまま確定)。続いて変換/予測候補 (タップで確定)。
 * スプリット変換モード (`変換` キー押下後):
 *   左端: フォーカス中のセグメント (緑塗りで強調) + 残りのひらがな (薄色)。
 *   続いて当該セグメントに対する候補 (タップで確定 → 自動で次のセグメントへ)。
 *   ◀ / ▶ キーでフォーカス範囲を縮小 / 拡大できる。
 * composing が空のときは何も描かない。
 */
@Composable
private fun CandidateBar(
    composing: ComposingState,
    modifier: Modifier = Modifier
) {
    if (!composing.isActive) return
    val candidates = composing.candidates
    val isSplit = composing.isSplitMode
    // 候補サイクル選択 index: -1 なら「生かな (head)」が選択中、0+ なら候補配列の index。
    // 変換キー連打でこの index が循環し、選択中のピルが緑塗りでハイライトされる。
    val selIdx = composing.selectedCandidateIndex
    val rawSelected = selIdx == -1
    // 長文の一括予測 (各ブロック第1候補を連結した「文まるごと」候補)。tail があるときのみ出す。
    val full = composing.fullPrediction
    val hasFull = full != null
    //   通し index: 0=head, [full], base+i=候補 i
    //   先頭ピルは「打った生かな全体」を連続表示し、その中で先頭ブロックの境目を示す
    //   (別の tail ラベルは廃止)。◀▶ は先頭ブロック範囲を伸縮し候補が追従する。
    val base = 1 + (if (hasFull) 1 else 0)
    // 上下 2 段の候補バー。各行は独立に左詰め (開始位置は揃わなくてよい) しつつ、両行を含む
    // 内側 Column を **1 つの horizontalScroll** で動かす。これで上下は必ず同じ量だけスクロールし、
    // ズレ (ドリフト) が起きない。列を揃えないので短い候補に無駄な隙間も出ない。
    //   even index = 上段 / odd index = 下段。
    val scrollState = rememberScrollState()
    val itemWidths = remember { mutableStateMapOf<Int, Int>() }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    // 候補サイクルで選択が変わったら、その候補が属する段で手前ピル幅を積んで x を求め、見える位置へ。
    LaunchedEffect(selIdx, candidates.size, itemWidths.size) {
        val target = if (selIdx < 0) 0 else base + selIdx
        if (target in 0 until (base + candidates.size)) {
            var x = 0
            var i = target % 2
            while (i < target) { x += (itemWidths[i] ?: 0) + gapPx; i += 2 }
            scrollState.animateScrollTo((x - gapPx).coerceAtLeast(0))
        }
    }

    // 描画順に通し index を採番してピルを作る (even=上段, odd=下段)。
    val pills = ArrayList<Pair<Int, @Composable () -> Unit>>()
    var nextIdx = 0
    // head ピル。打った生かな**全体**を連続表示する。スプリット中は先頭ブロック (splitHead) を
    // 濃色、残り (splitTail) を薄色にし、境目に caret (地色反転の細バー) を挟んで「今の先頭ブロック
    // の範囲」を示す。◀▶ でこの境目が動き、候補が追従する。タップで生確定 (commitRaw)。
    run {
        val myIdx = nextIdx++
        pills.add(myIdx to {
            Box(
                modifier = Modifier
                    .onSizeChanged { itemWidths[myIdx] = it.width }
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (rawSelected) ZtsGreen else ZtsBgCard)
                    .border(1.dp, ZtsGreen, RoundedCornerShape(6.dp))
                    .clickable { composing.commitRaw() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                val blockColor = if (rawSelected) Color.Black else ZtsGreen
                val tailColor = if (rawSelected) Color.Black.copy(alpha = 0.45f) else ZtsTextSecondary
                Text(
                    text = buildAnnotatedString {
                        if (isSplit) {
                            withStyle(SpanStyle(color = blockColor, fontWeight = FontWeight.Bold)) {
                                append(composing.splitHead)
                            }
                            // 先頭ブロックと残りかなの境目 (◀▶ で動く)。
                            withStyle(SpanStyle(background = blockColor)) { append(" ") }
                            withStyle(SpanStyle(color = tailColor)) { append(composing.splitTail) }
                        } else {
                            withStyle(SpanStyle(color = blockColor, fontWeight = FontWeight.Bold)) {
                                append(composing.text)
                            }
                        }
                    },
                    fontSize = 15.sp, fontFamily = FontFamily.Monospace
                )
            }
        })
    }
    // 長文の一括予測ピル。各ブロック第1候補を連結した「文まるごと」候補。タップで全文確定。
    if (full != null) {
        val myIdx = nextIdx++
        pills.add(myIdx to {
            Box(
                modifier = Modifier
                    .onSizeChanged { itemWidths[myIdx] = it.width }
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZtsGreenDim)
                    .border(1.dp, ZtsGreen, RoundedCornerShape(6.dp))
                    .clickable { composing.commitFull() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = full, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        })
    }
    // 変換 / 予測候補。選択中は緑塗りでハイライト → ⏎ で確定。
    candidates.forEachIndexed { i, cand ->
        val myIdx = base + i
        val selected = (i == selIdx)
        pills.add(myIdx to {
            Box(
                modifier = Modifier
                    .onSizeChanged { itemWidths[myIdx] = it.width }
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) ZtsGreen else ZtsBgCard)
                    .border(1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(6.dp))
                    .clickable { composing.commit(cand) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = cand,
                    color = if (selected) Color.Black else ZtsTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        })
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pills.forEach { (i, content) -> if (i % 2 == 0) content() }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pills.forEach { (i, content) -> if (i % 2 == 1) content() }
            }
        }
    }
}

/**
 * 端末右端の掴めるスクロールバー (要望)。scrollback がある時だけ出す。
 *
 * スクロール位置は [TerminalSession.scrollOffset] が真実 (0=最新/最下端、scrollbackSize=最古/最上端)。
 * つまみの縦位置 thumbTop は「画面より上にある履歴の割合」に比例させる:
 *   frac = (scrollbackSize - scrollOffset) / scrollbackSize   (0=最下端, 1=最上端)
 * つまみを掴んでドラッグしたら、その縦位置を frac に戻して scrollOffset を逆算する。
 * バッファは StateFlow でないため [TerminalSession.redrawTick] と scrollOffset の変化で再評価する。
 */
@Composable
private fun TerminalScrollbar(
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val scrollOffset by session.scrollOffset.collectAsState()
    val redraw by session.redrawTick.collectAsState()
    val selection by session.selection.collectAsState()
    // buffer 値はバッファ更新 (redraw) / スクロールのたびに読み直す。
    val scrollbackSize = remember(redraw, scrollOffset) { session.emulator.buffer.scrollbackSize }
    val rows = remember(redraw) { session.emulator.buffer.rows.coerceAtLeast(1) }
    // 履歴が無い / 選択中 (ハンドル操作と干渉させない) は出さない。
    if (scrollbackSize <= 0 || selection != null) return

    val density = LocalDensity.current
    val barWidth = 8.dp
    val minThumbPx = with(density) { 36.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val trackH = constraints.maxHeight.toFloat()
        if (trackH <= 0f) return@BoxWithConstraints
        val totalRows = (scrollbackSize + rows).toFloat()
        val thumbH = (trackH * rows / totalRows).coerceIn(minThumbPx.coerceAtMost(trackH), trackH)
        val maxThumbTop = (trackH - thumbH).coerceAtLeast(0f)
        val frac = (scrollbackSize - scrollOffset).toFloat() / scrollbackSize
        val thumbTop = (maxThumbTop * frac).coerceIn(0f, maxThumbTop)

        fun applyThumbTop(newTop: Float) {
            if (maxThumbTop <= 0f) return
            val f = (newTop / maxThumbTop).coerceIn(0f, 1f)
            // frac=0 → offset=scrollbackSize(最上端)、frac=1 → offset=0(最下端)。
            val newOffset = (scrollbackSize * (1f - f)).roundToInt()
            session.setScrollOffset(newOffset.coerceIn(0, scrollbackSize))
        }

        // つまみ。掴んで上下ドラッグで scrollback を移動できる。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .padding(end = 2.dp)
                .width(barWidth)
                .height(with(density) { thumbH.toDp() })
                .clip(RoundedCornerShape(4.dp))
                .background(ZtsGreen.copy(alpha = 0.55f))
                .pointerInput(scrollbackSize, trackH, thumbH) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            // ドラッグ中の現在つまみ位置 (= frac から再計算) に移動量を足す。
                            val curTop = (maxThumbTop *
                                ((scrollbackSize - session.scrollOffset.value).toFloat() / scrollbackSize))
                                .coerceIn(0f, maxThumbTop)
                            applyThumbTop(curTop + amount.y)
                        }
                    )
                }
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
                    text = stringResource(R.string.terminal_action_copy),
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
                    text = stringResource(R.string.scroll_offset_indicator, scrollOffset),
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
