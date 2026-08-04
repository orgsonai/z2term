package com.zerotoship.z2term.ui.terminal

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.SystemClock
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.zerotoship.z2term.core.TerminalHints
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.GuiScreen
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.gui.rfb.RfbClient
import com.zerotoship.z2term.proot.GuiTerminal
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.clipboard.ClipboardHistorySheet
import com.zerotoship.z2term.ui.log.SessionLogSheet
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.components.DownloadConfirmDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.ServerDaemonService
import com.zerotoship.z2term.service.SystemEventService
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.ui.components.ResidentActionDialog
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
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardFace
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.ui.terminal.keyboard.UserDictStore
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
import kotlinx.coroutines.delay
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
 * このアプリの**画面だけ**の明るさを当てる (0.8.234)。
 *
 * 暗い部屋で開くと、いちばん眩しいのが黒地に緑文字の自分のアプリ、という状況になる。
 * OS の明るさを下げに行くと戻すのを忘れるので、**この Window だけ**に効かせる
 * (`WindowManager.LayoutParams.screenBrightness`)。ホームに戻れば OS の明るさに戻る。
 *
 * [level] が null なら `BRIGHTNESS_OVERRIDE_NONE` = **OS に任せる** (既定)。触ったときだけ
 * 効くので**モードは増えない**。下限は [MIN_BRIGHTNESS] — 真っ暗にして「戻す」も押せなく
 * なるのが最悪の結末なので、そこには落ちないようにする。
 */
private fun applyScreenBrightness(context: Context, level: Float?) {
    val window = context.findActivity()?.window ?: return
    window.attributes = window.attributes.apply {
        screenBrightness = level?.coerceIn(MIN_BRIGHTNESS, 1f)
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

/** 明るさの下限。これ以上暗くすると画面が読めず、戻す操作もできなくなる。 */
private const val MIN_BRIGHTNESS = 0.10f

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
    // つまずきの言い換え (0.8.237)。当たったヒントを数秒だけ出す。
    var hint by remember { mutableStateOf<TerminalHints.Hint?>(null) }
    LaunchedEffect(active.id) {
        active.hintEvents.collect { h ->
            hint = h
            // 読める長さだけ出して自分から消える。押して消す操作を強いない。
            delay(6000)
            hint = null
        }
    }

    // 複数行の貼り付けを確認する帯。null の間は出さない (= 1 行の貼り付けでは何も起きない)。
    var pastePreview by remember { mutableStateOf<String?>(null) }
    var keyboardCollapsed by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // 常駐サーバーが稼働中か (🔒 の薄くロック表示・タップ時ダイアログの出し分けに使う)。
    // supervisor の起動/停止は UI 外で起きるので周期ポーリングで追従する (ServersSheet と同方式)。
    var serversRunning by remember { mutableStateOf(ServerDaemonManager.isRunning) }
    var residentDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { serversRunning = ServerDaemonManager.isRunning; delay(1000) }
    }
    // 自動起動前に DL 確認が要る spec (foss 初回など)。非 null の間ダイアログを出す。
    var pendingInitialDownload by remember(active.id) { mutableStateOf<DistroSpec?>(null) }
    var snippetsSheetOpen by remember { mutableStateOf(false) }
    var clipHistoryOpen by remember { mutableStateOf(false) }
    // 端末ログ (⚪): 記録状態はセッションが持ち、詳細設定シートの開閉だけ画面側で持つ。
    var logSheetOpen by remember { mutableStateOf(false) }
    val logState by active.logState.collectAsState()
    // SFTP ファイルブラウザ対象のプロファイル (非 null の間シートを表示)
    var sftpProfile by remember { mutableStateOf<SshProfile?>(null) }
    var customThemeEditorOpen by remember { mutableStateOf(false) }
    // スクロールバック検索: 検索バーの開閉 / クエリ / ヒット一覧 / 現在ヒット位置。タブ毎にリセット。
    var searchOpen by remember(active.id) { mutableStateOf(false) }
    var searchQuery by remember(active.id) { mutableStateOf("") }
    var searchMatches by remember(active.id) { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember(active.id) { mutableStateOf(0) }
    // 検索語のキャレット位置 (文字インデックス)。内蔵キーボード入力・タップ・←→ で動かす。
    // これが無いと「末尾追記と末尾削除」しかできず、途中の打ち間違いを直せなかった (要望)。
    var searchCursor by remember(active.id) { mutableStateOf(0) }
    // キャレット位置に文字列を挿入する。
    fun searchInsert(text: String) {
        val cur = searchCursor.coerceIn(0, searchQuery.length)
        searchQuery = searchQuery.substring(0, cur) + text + searchQuery.substring(cur)
        searchCursor = cur + text.length
    }
    // キャレット直前の 1 文字を削除する (BS)。
    fun searchBackspace() {
        val cur = searchCursor.coerceIn(0, searchQuery.length)
        if (cur <= 0) return
        // サロゲートペア (絵文字など) は 2 code unit まとめて消す。
        val del = if (cur >= 2 && searchQuery[cur - 1].isLowSurrogate() &&
            searchQuery[cur - 2].isHighSurrogate()
        ) 2 else 1
        searchQuery = searchQuery.substring(0, cur - del) + searchQuery.substring(cur)
        searchCursor = cur - del
    }
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

    // この画面だけの明るさ。null = OS に任せる (既定)。**設定に保存する** (0.8.242) —
    // 暗い部屋で使う人は毎回同じ値へ合わせ直すことになっていたため。「戻す」で null に戻せる
    // ので、触らない人にとっては今までどおり OS 任せのままで、モードは増えない。
    //
    // 正本は設定だが、ドラッグ中まで DataStore の往復を待つとつまみが渋るので、いまの値は
    // ローカルに持ち、**指を離したところで 1 回だけ保存**する。保存値が外から変わったとき
    // (起動直後の復元・バックアップの戻し) は下の LaunchedEffect で引き取る。
    var brightness by remember { mutableStateOf(settings.screenBrightness) }
    var brightnessBarOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.screenBrightness) { brightness = settings.screenBrightness }
    LaunchedEffect(brightness) { applyScreenBrightness(context, brightness) }

    // かな漢字変換: 入力中ひらがな(composing)と候補を保持。確定で PTY へ送出。
    // ただし検索バーを開いて独自キーボード使用中は、確定文字を PTY ではなく検索クエリへ流す
    // (システムキーボードとの二重入力を避ける。詳細は onKeyboardBytes 付近)。
    val composing = remember(active.id) {
        ComposingState(onCommit = { text ->
            if (searchOpen && keyboardMode == KeyboardMode.CUSTOM) {
                searchInsert(text)
            } else {
                active.writeBytes(text.toByteArray(Charsets.UTF_8))
            }
        })
    }
    // システム(OS)キーボードの変換中(確定前)テキスト。OS IME は InputConnection.setComposingText で
    // 確定前の文字列を送ってくるので、それを内蔵キーボードの composing.text と同じ描画経路
    // (TerminalRenderer の composingText) に載せて、端末のカーソル位置へインライン表示する。
    var systemComposing by remember(active.id) { mutableStateOf("") }
    // 辞書はアプリ起動後にバックグラウンドで 1 度だけ読み込む。
    LaunchedEffect(Unit) { KanaKanjiConverter.ensureLoaded(context) }
    LaunchedEffect(Unit) { KkcConverter.ensureLoaded(context) }
    // IME 学習履歴 (確定済み読み→単語) も同タイミングで読み込み、変換候補のランキングに使う。
    LaunchedEffect(Unit) { ImeHistoryStore.ensureLoaded(context) }
    // 取り込んだユーザー辞書 (SKK 形式) も同じタイミングで読む。無ければ何もしない。
    LaunchedEffect(Unit) { UserDictStore.ensureLoaded(context) }
    // キーボードモード変更時は変換中バッファを破棄 (OS IME と二重表示を防ぐ)。
    // 検索バーの開閉でも捨てる — 確定先が端末と検索語で入れ替わるので、跨いで持ち越すと
    // 「端末へ打っていたかな」が検索語に紛れ込む (0.8.275)。
    LaunchedEffect(keyboardMode, keyboardCollapsed, searchOpen) { composing.reset(); systemComposing = "" }

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
            onPaste = {
                // 1 行なら今までどおり即挿入。**改行を含むときだけ**貼る前に見せる (0.8.232)。
                // ここを 1 行にも広げると、一番よく押すボタンが 2 タップになって台無しになる。
                val text = active.clipboardText()
                when {
                    text.isNullOrEmpty() -> Unit
                    text.contains('\n') -> pastePreview = text
                    else -> active.pasteText(text, syncClipboard = false)
                }
            },
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
            // ダブルタップ = この画面だけの明るさ (単タップは今までどおり画面消灯ロック)。
            // 📋 や ⌨ と同じ「単タップ=動作 / ダブルタップ=詳細」の作法に揃える。
            onOpenBrightness = { brightnessBarOpen = true },
            keepAlive = settings.keepAliveService,
            onToggleKeepAlive = { active.setKeepAliveService(!settings.keepAliveService) },
            residentLocked = serversRunning,
            onLockedKeepAliveTap = { residentDialogOpen = true },
            toolbarOrder = settings.toolbarOrder,
            toolbarHidden = settings.toolbarHidden,
            onReorderToolbar = { active.setToolbarOrder(it) },
            onOpenSnippets = { snippetsSheetOpen = true },
            logRecording = logState.recording,
            onToggleLog = { active.toggleLogging() },
            onOpenLogSettings = { logSheetOpen = true },
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
        // 面 (かな / 英字 / 数字) の巡回順。設定の順序から、数字面が OFF ならそれを外す。
        // 日本語面を外すかどうかは TerminalKeyboard 側 (showJapaneseKeyboard) が決める。
        val faceOrder = KeyboardFace.orderFrom(settings.keyboardFaceOrder, settings.keyboardNumberFace)

        // 検索バー入力のルーティング:
        //   検索バーを開いて独自(内蔵)キーボード使用中は、キーボード出力を PTY ではなく検索クエリへ流す。
        //   システムキーボード時は OS IME が直接 BasicTextField に入力するので対象外。
        //   これで「検索バー(OS IME) と 画面下(内蔵キーボード) が二重に出る」状態を解消する (要望)。
        val searchTyping = searchOpen && keyboardMode == KeyboardMode.CUSTOM
        fun routeSearchBytes(bytes: ByteArray) {
            for (ch in String(bytes, Charsets.UTF_8)) {
                when (ch) {
                    '\u007F', '\b' -> searchBackspace()                  // キャレット直前を削除
                    '\r', '\n' -> if (searchMatches.isNotEmpty()) {
                        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                        active.scrollToAbsRow(searchMatches[currentMatchIndex].absRow)
                    }
                    '\u001B' -> searchOpen = false                       // ESC で検索を閉じる
                    else -> if (ch.code >= 0x20 && ch.code != 0x7F) searchInsert(ch.toString())
                }
            }
        }
        val onKeyboardBytes: (ByteArray) -> Unit = { bytes ->
            if (searchTyping) routeSearchBytes(bytes) else active.writeBytes(bytes)
        }
        val onKeyboardCursor: (com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey) -> Unit = { key ->
            // 検索入力中はカーソルキーを PTY へ送らず、検索語のキャレット移動に使う
            // (シェル側を乱さず、途中の打ち間違いをその場で直せるようにする)。
            if (searchTyping) {
                val len = searchQuery.length
                when (key) {
                    com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.LEFT ->
                        searchCursor = (searchCursor - 1).coerceIn(0, len)
                    com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.RIGHT ->
                        searchCursor = (searchCursor + 1).coerceIn(0, len)
                    com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.UP -> searchCursor = 0
                    com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.DOWN -> searchCursor = len
                }
            } else {
                active.writeBytes(active.emulator.cursorKeyBytes(key))
            }
        }

        // 初回だけ「最初の 3 枚」を出す (0.8.231)。触ったら消え、3 枚とも消えるか ✕ で二度と出ない。
        // 実行はしない — タップで入力行に入るだけで、⏎ は人が押す (共有受け取りと同じ作法)。
        if (!settings.introDone) {
            IntroCards(
                onInsert = { cmd -> active.writeBytes(cmd.toByteArray(Charsets.UTF_8)) },
                onFinish = { active.setIntroDone(true) }
            )
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
                        faceOrder = faceOrder,
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
                    // 内蔵キーボードは composing.text、OS キーボードは OS IME からの変換中テキスト。
                    composingText = if (keyboardMode == KeyboardMode.SYSTEM) systemComposing else composing.text,
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
                        // OS IME の変換中テキストをインライン表示へ流す。
                        v.onComposingChanged = { systemComposing = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                TerminalScrollbar(
                    session = active,
                    // 検索中だけ地図になる。検索していないときは今までどおり何も足さない。
                    matchRows = if (searchOpen) searchMatches.map { it.absRow } else emptyList(),
                    onSeek = { absRow -> active.scrollToAbsRow(absRow) },
                    modifier = Modifier.fillMaxSize()
                )
                ScrollIndicators(session = active, modifier = Modifier.fillMaxSize())
                // つまずきの言い換え: 端末の**下端**に 1 行だけ。上端の帯 (検索・貼り付け・明るさ) とは
                // 別の場所に置き、出力の邪魔をしない。タップで消える。
                hint?.let { h ->
                    HintBar(
                        hint = h,
                        onDismiss = { hint = null },
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
                // 変換候補バー: キーボードの上に浮かせて表示 (キーボード本体の高さは変えない)
                CandidateBar(
                    composing = composing,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                // 画面の明るさ (🔅 のダブルタップで開く)。同じ場所に出る帯の 3 つ目。
                if (brightnessBarOpen) {
                    BrightnessBar(
                        level = brightness,
                        onChange = { brightness = it },
                        onCommit = { active.setScreenBrightness(brightness) },
                        onReset = {
                            brightness = null
                            active.setScreenBrightness(null)
                        },
                        onClose = { brightnessBarOpen = false }
                    )
                }
                // 複数行の貼り付け確認 (端末領域の上端にオーバーレイ・検索バーと同じ置き方)。
                pastePreview?.let { text ->
                    PastePreviewBar(
                        text = text,
                        onPaste = {
                            active.pasteText(text, syncClipboard = false)
                            pastePreview = null
                        },
                        onDismiss = { pastePreview = null }
                    )
                }
                // スクロールバック検索バー (端末領域の上端にオーバーレイ)
                if (searchOpen) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it; searchCursor = it.length },
                        cursor = searchCursor.coerceIn(0, searchQuery.length),
                        onCursorChange = { searchCursor = it.coerceIn(0, searchQuery.length) },
                        // システムキーボード使用時だけ OS IME を出す。独自キーボード時は
                        // 内蔵キーボードで検索語を入力する (二重キーボード回避・要望)。
                        systemKeyboard = keyboardMode == KeyboardMode.SYSTEM,
                        // 変換中 (確定前) のかなをキャレット位置へ下線付きで見せる。端末側の
                        // プリエディット表示と同じ経路・同じ見た目にする (0.8.275)。
                        composingText = if (keyboardMode == KeyboardMode.SYSTEM) "" else composing.text,
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
                        faceOrder = faceOrder,
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
                                    // English モードでは日本語面を巡回から外す。
                                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                                    faceOrder = faceOrder
                                )
                            }
                        }
                    }
                }
                KeyboardMode.SYSTEM -> {
                    // OS IME はシステムが描画するため、こちらは SpecialKeyBar の高さだけ。
                    // 設定 (specialKeyBar) が OFF なら補助キーごと出さない。
                    if (settings.specialKeyBar) {
                        SpecialKeyBar(
                            session = active,
                            ctrlSticky = ctrlSticky,
                            onCtrlToggle = { ctrlSticky = !ctrlSticky }
                        )
                    }
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
            onSftp = { profile -> sftpProfile = profile },
            // 常駐サーバーの管理をここからも行えるようにする (設定シートを経由しなくてよい)。
            serverSession = active
        )
    }
    if (clipHistoryOpen) {
        ClipboardHistorySheet(
            onDismiss = { clipHistoryOpen = false },
            // 履歴から選んで貼るだけ。システムクリップボードへ書き戻すと「貼り付けたのに
            // コピーされた (履歴が積み替わる)」挙動になるため同期しない。
            // **改行を含むものは 📋 と同じく貼る前に見せる** (0.8.249)。危ないのは
            // 「入るとそのまま実行される」ことで、どの入口から来たかは関係ない。
            // シートは選択と同時に閉じるので、確認バーはその後ろに出る。
            onSelect = { text ->
                if (text.contains('\n')) pastePreview = text
                else active.pasteText(text, syncClipboard = false)
            }
        )
    }
    if (logSheetOpen) {
        SessionLogSheet(
            session = active,
            onDismiss = { logSheetOpen = false }
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

    // 常駐サーバー稼働中に🔒をタップしたときの終了ダイアログ (常駐に閉じ込められないための出口)。
    if (residentDialogOpen) {
        ResidentActionDialog(
            onResetSession = { residentDialogOpen = false; SessionManager.resetToInitial(context) },
            onStopAll = { residentDialogOpen = false; stopEverythingAndQuit(context) },
            onCancel = { residentDialogOpen = false }
        )
    }
}

/**
 * 常駐サーバー・セッション・常駐サービスをすべて止めてアプリを閉じる (タスクキル相当)。
 *
 * 常駐サーバーが動いていると最近履歴からのスワイプではプロセスが死なないため、明示的な出口として
 * ここで全部落とす。順に: 常駐サーバー停止 → **システムイベント検知停止** → 全セッション終了 →
 * セッション常駐 FG 停止 → タスク終了。
 *
 * ⚠ **フォアグラウンドサービスは 1 つでも残っているとプロセスが死なない。** システムイベント検知
 * ([SystemEventService]) を落とし忘れていたため、「全部停止して終了」を押してもアプリが閉じない
 * ままだった (実機フィードバック 2026-07-25)。FG サービスを増やしたら**必ずここへ足す**こと。
 * 設定 (`systemEventCaptureEnabled`) は触らないので、次にアプリを開けば検知は再開する。
 */
internal fun stopEverythingAndQuit(context: Context) {
    ServerDaemonService.stop(context)
    SystemEventService.stop(context)
    SessionManager.shutdown()
    TerminalService.stop(context)
    context.findActivity()?.finishAndRemoveTask()
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
    LaunchedEffect(Unit) { UserDictStore.ensureLoaded(context) }

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
    // 端末タブと同じく常駐サーバー稼働中は🔒を薄くロックし、タップで終了ダイアログを出す。
    var serversRunning by remember { mutableStateOf(ServerDaemonManager.isRunning) }
    var residentDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { serversRunning = ServerDaemonManager.isRunning; delay(1000) }
    }

    val rootView = LocalView.current
    LaunchedEffect(keepScreenOn) { applyKeepScreenOn(context, rootView, keepScreenOn) }

    // 明るさは Window に効く設定なので端末タブと共通。GUI タブを開いたまま起動した場合でも
    // 保存値が当たるようにここでも適用する (帯を出す口は端末タブの 🔅 ダブルタップのまま)。
    LaunchedEffect(settings.screenBrightness) {
        applyScreenBrightness(context, settings.screenBrightness)
    }

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
            residentLocked = serversRunning,
            onLockedKeepAliveTap = { residentDialogOpen = true },
            toolbarOrder = settings.toolbarOrder,
            toolbarHidden = settings.toolbarHidden,
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
        // 面 (かな / 英字 / 数字) の巡回順。設定の順序から、数字面が OFF ならそれを外す。
        // 日本語面を外すかどうかは TerminalKeyboard 側 (showJapaneseKeyboard) が決める。
        val faceOrder = KeyboardFace.orderFrom(settings.keyboardFaceOrder, settings.keyboardNumberFace)

        Row(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            if (isSideKBGui && landscapePosGui == AppSettings.LANDSCAPE_KB_LEFT) {
                SideKeyboardColumn(
                    style = kbStyleGui,
                    composing = composing,
                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                    faceOrder = faceOrder,
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
                                            showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                                            faceOrder = faceOrder
                                        )
                                    }
                                }
                            }
                            KeyboardMode.SYSTEM -> {
                                // 端末側と同じ設定で GUI の補助キーバーも出し入れする。
                                if (settings.specialKeyBar) {
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
            if (isSideKBGui && landscapePosGui == AppSettings.LANDSCAPE_KB_RIGHT) {
                SideKeyboardColumn(
                    style = kbStyleGui,
                    composing = composing,
                    showJapaneseKeyboard = LocaleHelper.language(context) == LocaleHelper.LANG_JA,
                    faceOrder = faceOrder,
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
            // GUI タブからは SSH 接続の概念が無いので SSH タブは出さない。
            showSshTab = false,
            // 常駐サーバーの管理は端末タブが 1 つでもあれば GUI からも行える。
            serverSession = terminalForSettings
        )
    }
    if (clipHistoryOpen) {
        ClipboardHistorySheet(
            onDismiss = { clipHistoryOpen = false },
            // GUI では選んだ本文を keysym 橋渡しでタイプするだけ。システムクリップボードへは
            // 書き戻さない (書き戻すと「貼り付けたのにコピーされた」= 履歴が積み替わるため)。
            onSelect = { text ->
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

    // 端末タブと同じ常駐終了ダイアログ (常駐サーバー稼働中に🔒をタップしたとき)。
    if (residentDialogOpen) {
        ResidentActionDialog(
            onResetSession = { residentDialogOpen = false; SessionManager.resetToInitial(context) },
            onStopAll = { residentDialogOpen = false; stopEverythingAndQuit(context) },
            onCancel = { residentDialogOpen = false }
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
    residentLocked: Boolean,
    onLockedKeepAliveTap: () -> Unit,
    toolbarOrder: String,
    toolbarHidden: String,
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
                    ToolbarItem(ToolbarButtons.PASTE, "📋", stringResource(R.string.tb_paste), onClick = onPaste, onDoubleClick = onPasteHistory),
                    ToolbarItem(ToolbarButtons.SNIPPETS, "📜", stringResource(R.string.tb_snippets), onClick = onOpenSnippets),
                    ToolbarItem(ToolbarButtons.SCREEN_ON, if (keepScreenOn) "💡" else "🔅", stringResource(R.string.tb_screen_on), active = keepScreenOn, onClick = onToggleKeepScreenOn),
                    keepAliveToolbarItem(residentLocked, keepAlive, onToggleKeepAlive, onLockedKeepAliveTap),
                    ToolbarItem(ToolbarButtons.KEYBOARD, "⌨", stringResource(R.string.tb_keyboard), active = keyboardMode == KeyboardMode.SYSTEM, onClick = onToggleKeyboardMode, onDoubleClick = onToggleKeyboardVisible)
                ),
                hidden = toolbarHidden,
                savedOrder = toolbarOrder,
                onReorder = onReorderToolbar,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        // ⚙ 設定は並べ替えにも非表示指定にも入れず、常に右端に固定する (要望)。
        ToolbarChip(
            icon = "⚙",
            active = false,
            enabled = settingsEnabled,
            onClick = onOpenSettings
        )
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
    onOpenBrightness: () -> Unit,
    keepAlive: Boolean,
    onToggleKeepAlive: () -> Unit,
    residentLocked: Boolean,
    onLockedKeepAliveTap: () -> Unit,
    toolbarOrder: String,
    toolbarHidden: String,
    onReorderToolbar: (String) -> Unit,
    onOpenSnippets: () -> Unit,
    logRecording: Boolean,
    onToggleLog: () -> Unit,
    onOpenLogSettings: () -> Unit,
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
            // 既定の並び (左→右): 貼付 / コマンド一覧 / 画面消灯ロック / 常駐ロック / 検索 / キーボード切替。
            // 常駐ロック (バックグラウンド常駐トグル) は画面消灯ロックの右に置く (要望)。
            // 各ボタンは長押しドラッグで並べ替え可・長押し中は簡易説明をポップアップ表示する。
            // 設定で隠したボタンは [hidden] で除かれる (設定 › ツールバー)。
            ReorderableToolbar(
                items = listOf(
                    ToolbarItem(ToolbarButtons.PASTE, "📋", stringResource(R.string.tb_paste), onClick = onPaste, onDoubleClick = onPasteHistory),
                    ToolbarItem(ToolbarButtons.SNIPPETS, "📜", stringResource(R.string.tb_snippets), onClick = onOpenSnippets),
                    ToolbarItem(ToolbarButtons.SCREEN_ON, if (keepScreenOn) "💡" else "🔅", stringResource(R.string.tb_screen_on), active = keepScreenOn, onClick = onToggleKeepScreenOn, onDoubleClick = onOpenBrightness),
                    keepAliveToolbarItem(residentLocked, keepAlive, onToggleKeepAlive, onLockedKeepAliveTap),
                    ToolbarItem(ToolbarButtons.SEARCH, "🔍", stringResource(R.string.tb_search), active = searchActive, onClick = onToggleSearch),
                    ToolbarItem(ToolbarButtons.KEYBOARD, "⌨", stringResource(R.string.tb_keyboard), active = keyboardMode == KeyboardMode.SYSTEM, onClick = onToggleKeyboardMode, onDoubleClick = onToggleKeyboardVisible),
                    // 端末ログ: 短押し=記録の開始/停止、ダブルタップ=詳細設定。記録中は 🔴、停止中は ⚪
                    // (録画ボタンの慣習で状態が一目で分かる。active の緑ハイライトも併せて点く)。
                    ToolbarItem(ToolbarButtons.LOG, if (logRecording) "🔴" else "⚪", stringResource(R.string.tb_log), active = logRecording, onClick = onToggleLog, onDoubleClick = onOpenLogSettings)
                ),
                hidden = toolbarHidden,
                savedOrder = toolbarOrder,
                onReorder = onReorderToolbar,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        // ⚙ 設定は並べ替えにも非表示指定にも入れず、常に右端に固定する (要望)。
        // 他のボタンをどう並べ替えても・どれだけ隠しても、設定の位置だけは動かない。
        ToolbarChip(
            icon = "⚙",
            active = false,
            enabled = true,
            onClick = onOpenSettings
        )
        // 状態名 (RUNNING 等) は表示しない: 幅が狭いと崩れる & 実用上見ないため (要望で削除)。
    }
}

// ツールバーのアクション id (並び順・非表示指定の永続化キー) は [ToolbarButtons] に集約。
// 設定シートの「ツールバー」セクションと同じ定義を共有するため。

/** ツールバーの 1 ボタン。[id] は並び順の保存キー、[active] は緑ハイライト、[description] は長押し説明。 */
private class ToolbarItem(
    val id: String,
    val icon: String,
    val description: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    // 押せるが薄く見せる (例: 常駐サーバー稼働中の🔒 = 解除不可のロック表示)。enabled=false と違い
    // タップ自体は受け付ける (タップで代替アクションのダイアログを開くため)。
    val dimmed: Boolean = false,
    val onClick: () -> Unit,
    // 設定時のみ有効化されるダブルタップ動作 (📋 貼付ボタンでクリップボード履歴を開く等)。
    val onDoubleClick: (() -> Unit)? = null
)

/**
 * 🔒 バックグラウンド常駐トグルのツールバー項目を作る (端末 / GUI 共通)。
 *
 * 常駐サーバー稼働中 ([residentLocked]=true) はプロセスが生き続けるため、🔒 を OFF にしても
 * セッションは消えない (最近履歴からのスワイプも効かない)。そこで ON 表示のまま薄くロックし、
 * タップではトグルせず終了ダイアログ ([onLockedTap]) を開く。非稼働時は通常のトグル。
 */
@Composable
private fun keepAliveToolbarItem(
    residentLocked: Boolean,
    keepAlive: Boolean,
    onToggle: () -> Unit,
    onLockedTap: () -> Unit,
): ToolbarItem = if (residentLocked) {
    ToolbarItem(
        ToolbarButtons.KEEP_ALIVE, "🔒",
        stringResource(R.string.tb_keep_alive_locked),
        active = true, dimmed = true, onClick = onLockedTap
    )
} else {
    ToolbarItem(
        ToolbarButtons.KEEP_ALIVE, if (keepAlive) "🔒" else "🔓",
        stringResource(R.string.tb_keep_alive),
        active = keepAlive, onClick = onToggle
    )
}

/**
 * 並べ替え可能なツールバー (要望)。
 *  - 通常タップ = そのボタンの動作。
 *  - 長押し = つかんで左右ドラッグで並べ替え + 簡易説明ポップアップを表示。
 * 並びは [savedOrder] (カンマ区切り id) から復元し、変更を [onReorder] で永続化する。
 * [hidden] (カンマ区切り id) に入っているボタンは描かない (設定 › ツールバーで指定)。
 * 隠したボタンの id は [savedOrder] に残したままにして、出し直したときに元の位置へ戻す。
 */
@Composable
private fun ReorderableToolbar(
    items: List<ToolbarItem>,
    savedOrder: String,
    onReorder: (String) -> Unit,
    modifier: Modifier = Modifier,
    hidden: String = ""
) {
    val hiddenIds = remember(hidden) { ToolbarButtons.parseHidden(hidden) }
    val shown = items.filter { it.id !in hiddenIds }
    val present = shown.map { it.id }
    val byId = shown.associateBy { it.id }
    val order = remember { mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    // 保存順 / ボタン構成が変わったら並びを作り直す。ドラッグ中だけは触らない
    // (確定時は order をローカル更新済 → 直後の savedOrder 反映で同じ並びに収束しちらつかない)。
    LaunchedEffect(savedOrder, present) {
        if (dragging == null) {
            val savedIds = ToolbarButtons.parseOrder(savedOrder)
            // 壊れた保存値 (同じ id が二重) を見つけたら、その場で正規化して書き戻す。
            // 表示は mergeOrder が畳むので直るが、保存値を直さないと壊れたまま残り、
            // 次に隠す/出すを切り替えたときにまた表面化する。書き戻すと savedOrder が
            // 更新されてこの LaunchedEffect が 1 回だけ回り直し、以後は何もしない。
            if (savedIds.size != savedIds.distinct().size) {
                onReorder(savedIds.distinct().joinToString(","))
            }
            order.clear(); order.addAll(ToolbarButtons.mergeOrder(savedIds, present))
        }
    }
    val widths = remember { mutableStateMapOf<String, Int>() }
    var dragOffset by remember { mutableStateOf(0f) }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    // 保存する並びは「隠しているボタンも含めた全体」にする。表示中のボタンだけを保存すると
    // 隠した id が保存値から消え、出し直したときに末尾へ飛んでしまうため。
    // 全体の並びの「表示されている位置」だけを、今の表示順で埋め直す。
    val allIds = items.map { it.id }
    fun persistOrder(shownOrder: List<String>): String =
        ToolbarButtons.normalizeOrder(savedOrder, allIds, hiddenIds, shownOrder)

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
                                onDragEnd = { dragging = null; dragOffset = 0f; onReorder(persistOrder(order)) },
                                onDragCancel = { dragging = null; dragOffset = 0f; onReorder(persistOrder(order)) },
                                onDrag = { change, amount -> change.consume(); dragOffset += amount.x; trySwap() }
                            )
                        }
                ) {
                    ToolbarChip(
                        icon = item.icon,
                        active = item.active,
                        enabled = item.enabled,
                        dimmed = item.dimmed,
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
    dimmed: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null
) {
    // dimmed = 押せるが薄く (常駐ロック等)。enabled=false のグレーアウトとは別で、色は活かしたまま
    // 半透明にして「有効だが今は解除できない」を伝える。
    val dim = if (dimmed) 0.4f else 1f
    val bg = when {
        !enabled -> ZtsBgCard.copy(alpha = 0.35f)
        active -> ZtsGreen.copy(alpha = dim)
        else -> ZtsBgCard.copy(alpha = dim)
    }
    val fg = when {
        !enabled -> ZtsTextSecondary.copy(alpha = 0.4f)
        active -> Color.Black.copy(alpha = dim)
        else -> ZtsTextPrimary.copy(alpha = dim)
    }
    val border = when {
        !enabled -> ZtsBorder.copy(alpha = 0.35f)
        active -> ZtsGreen.copy(alpha = dim)
        else -> ZtsBorder.copy(alpha = dim)
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
 * つまずきの言い換えを 1 行だけ出す帯 (0.8.237)。
 *
 * ⚠ **端末の出力そのものは書き換えない。** ここは別の場所に 1 行足しているだけで、
 * スクロールバックにも端末ログにも残らない。書き換えは端末アプリとしての信用に直結する。
 */
@Composable
private fun HintBar(
    hint: TerminalHints.Hint,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val res = when (hint) {
        TerminalHints.Hint.PING -> R.string.hint_ping
        TerminalHints.Hint.LOW_PORT -> R.string.hint_low_port
        TerminalHints.Hint.SSHD_PATH -> R.string.hint_sshd_path
        TerminalHints.Hint.STORAGE -> R.string.hint_storage
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "→", color = ZtsGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = stringResource(res),
            color = ZtsTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 複数行の貼り付けを、貼る前に見せる帯 (0.8.232)。
 *
 * 📋 は押した瞬間に入るので、コピー元がコードのかたまりだと**何行入ったのか分からないまま**
 * ⏎ を押すことになる。**改行を含むときだけ**この帯を出す — 1 行の貼り付けは今までどおり
 * 即挿入で、そこを広げると一番よく押すボタンが 2 タップになって台無しになる。
 *
 * 貼っても**実行はしない** (入力行に入るだけ)。共有の受け取り (B1) と同じ作法。
 * 寸法と置き方は [SearchBar] に揃えてある (同じ場所に出る帯が 2 種類あるので)。
 */
@Composable
private fun PastePreviewBar(
    text: String,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.lines() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            // 見落とし対策 (0.8.255): 帯そのものをアクセント色で塗る。
            // 以前は背景も枠も周囲と同系の暗色で、**出ていることに気付けず貼らずに進んでしまう**
            // という報告があった。薄く敷くだけ (12%) では**端末の文字が透けて帯に見えない**と
            // 再度指摘されたので、ほぼ不透明まで上げた。0.8.259 で**透明度 20% (= 不透明度
            // 80%)** に落ち着かせた。⚠ ここを薄くしたぶん前景は濃くすること — 地が透ける
            // ほど下の端末文字と混ざって、薄い前景から先に読めなくなる。
            // 出る場所は変えない (SearchBar と同じ位置) — 動かすと「どこに出るか」の
            // 学習が無駄になるので、強めるのは色と押しやすさだけにする。
            .background(ZtsGreen.copy(alpha = 0.8f))
            .border(width = 2.dp, color = ZtsGreen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 何の帯かを一目で分からせる。ツールバーの 📋 と同じ絵文字にして結び付ける。
        Text(text = "📋", fontSize = 14.sp, maxLines = 1)
        // 行数を先頭に置く。ここでいちばん効く情報は「何行入るか」。
        Text(
            text = pluralStringResource(R.plurals.paste_preview_lines, lines.size, lines.size),
            color = ZtsBgPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        // 中身は最大 2 行だけ覗かせる (全文を見せる場所ではない)。
        Text(
            text = lines.take(2).joinToString(" ⏎ ") { it.trim() },
            // 緑地なので二次情報も暗色。⚠ ここは**細字にも薄くもしない** (0.8.259) —
            // 11sp の等幅で細いままだと「何を貼るのか」が読めず、帯を出した意味が消える。
            // 主役 (行数と「貼る」) との差は**文字の大きさ**だけで付ける。
            color = ZtsBgPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 「貼る」は**塗りつぶし**にする。文字だけだとラベルに見えて押せると分からず、
        // 帯を出した意味が無くなる (実機報告)。ここは画面で唯一の主ボタンなので迷わせない。
        // ⚠ 帯が緑地なので、ボタンは**暗い地に緑文字**で抜く (緑地に緑ボタンでは溶ける)。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ZtsBgPrimary)
                .clickable(onClick = onPaste)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.paste_preview_do),
                color = ZtsGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // 緑地なので暗色。ただし主役の「貼る」より弱く見せたいので薄める。
            Text(
                text = "✕",
                color = ZtsBgPrimary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * この画面だけの明るさを決める帯 (0.8.234)。🔅 のダブルタップで開く。
 *
 * 中身は**スライダー 1 本 +「戻す」+ ✕** だけ。設定画面へは行かせない — 眩しいのは
 * 「いま」なので、その場で終わる操作にする。既定は「OS に任せる」で、触ったときだけ
 * 効くから**モードが増えない**。決めた値は設定に残る (0.8.242)。
 *
 * [onChange] はドラッグ中に何度も呼ばれる**表示用**、[onCommit] は指を離したときの
 * **保存用**。分けないと、つまみを動かすたびに DataStore へ書きに行くことになる。
 */
@Composable
private fun BrightnessBar(
    level: Float?,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ZtsBgSecondary)
            .border(width = 1.dp, color = ZtsBorder)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "◐", color = ZtsGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = level ?: 1f,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = MIN_BRIGHTNESS..1f,
            colors = SliderDefaults.colors(
                thumbColor = ZtsGreen,
                activeTrackColor = ZtsGreen,
                inactiveTrackColor = ZtsBorder
            ),
            modifier = Modifier.weight(1f)
        )
        // 「戻す」は常に出しておく。暗くしすぎたときの出口が無いと怖くて触れない。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.brightness_reset),
                color = ZtsTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(text = "✕", color = ZtsTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * スクロールバック検索バー。端末領域の上端にオーバーレイする。
 * 入力フィールド + 件数 (現在/総数) + ↑(前) + ↓(次) + ✕(閉じる)。
 *
 * 内蔵キーボード時は OS IME を出さない代わりに、キャレット (点滅する縦棒) を自前で描く。
 * タップした位置にキャレットが移動し、そこへ挿入 / そこから削除できる ([cursor]/[onCursorChange])。
 *
 * [composingText] は内蔵キーボードの**変換中 (確定前) のかな**。確定しないと検索語に入らないので、
 * これを出さないと**打っている最中は画面がまったく変わらない** — 端末では下線付きで見えるものが
 * 検索バーだけ見えず、「内蔵キーボードで日本語が打てない」ようにしか見えなかった (0.8.275)。
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    cursor: Int,
    onCursorChange: (Int) -> Unit,
    systemKeyboard: Boolean,
    composingText: String,
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
            if (systemKeyboard) {
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
            } else {
                // 独自キーボード時。BasicTextField(readOnly) だとキャレットが一切出ず、
                // 末尾の追記/削除しかできなかったため、表示とキャレットを自前で描く。
                SearchQueryField(
                    query = query,
                    cursor = cursor,
                    composingText = composingText,
                    onCursorChange = onCursorChange
                )
            }
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

/**
 * 内蔵キーボード用の検索語表示。OS IME を出さずにキャレットを描く。
 *  - タップした文字位置へキャレットを移動 ([TextLayoutResult.getOffsetForPosition])
 *  - 点滅する縦棒でキャレット位置を示す
 *  - 語が幅を超えたら、キャレットが常に見えるよう横スクロールする
 *  - [composingText] (変換中のかな) はキャレット位置に**下線付き**で挟んで見せる
 */
@Composable
private fun SearchQueryField(
    query: String,
    cursor: Int,
    composingText: String,
    onCursorChange: (Int) -> Unit
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 入力/移動の直後は必ず点灯させたいので、query と cursor をキーにして点滅を作り直す。
    // 変換中も打鍵のたびに点け直したいので composingText もキーに含める。
    val caretOn by produceState(true, query, cursor, composingText) {
        while (true) {
            value = true
            delay(600)
            value = false
            delay(400)
        }
    }
    val at = cursor.coerceIn(0, query.length)
    // 実際に描く文字列 = 確定済みのキャレット前 + 変換中 + 確定済みのキャレット後。
    // キャレットは変換中の**後ろ**に置く (次の打鍵が入る位置なので、端末側と同じ)。
    val shown = remember(query, at, composingText) {
        if (composingText.isEmpty()) AnnotatedString(query) else buildAnnotatedString {
            append(query.substring(0, at))
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(composingText) }
            append(query.substring(at))
        }
    }
    val idx = at + composingText.length
    // 位置は必ず「そのレイアウトが実際に持つ文字列」の長さでクランプする。
    // query の更新 (状態) とレイアウト結果の更新 (次フレーム) には 1 フレームのずれがあり、
    // query.length で丸めると、空レイアウトに対して idx>0 を問い合わせて
    // IllegalArgumentException: offset(n) is out of bounds で落ちる (実機で確認)。
    val caretX = layout?.let { it.getHorizontalPosition(idx.coerceAtMost(it.layoutInput.text.length), true) } ?: 0f
    val caretH = layout?.let {
        if (it.lineCount > 0) it.getLineBottom(0) - it.getLineTop(0) else 0f
    } ?: 0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(query, composingText) {
                detectTapGestures { pos ->
                    // 変換中はキャレットを動かさない。描いている文字列に確定前のかなが
                    // 挟まっているので、タップ位置から検索語側の位置を出しても合わない。
                    if (composingText.isNotEmpty()) return@detectTapGestures
                    val l = layout ?: return@detectTapGestures
                    onCursorChange(l.getOffsetForPosition(Offset(pos.x + scrollState.value, pos.y)))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val viewport = constraints.maxWidth.toFloat()
        // キャレットが画面外に出たら、見える位置まで寄せる。
        LaunchedEffect(caretX, viewport, query) {
            val cur = scrollState.value.toFloat()
            if (caretX < cur) {
                scrollState.scrollTo(caretX.roundToInt().coerceAtLeast(0))
            } else if (viewport > 0f && caretX > cur + viewport - 8f) {
                scrollState.scrollTo((caretX - viewport + 8f).roundToInt().coerceAtLeast(0))
            }
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Text(
                text = shown,
                color = ZtsTextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { layout = it },
                // 末尾のキャレット (x = テキスト幅) を置く余白。horizontalScroll は
                // 内容幅でクリップするので、この余白が無いと文字を打った瞬間に
                // キャレットがはみ出して消える。
                modifier = Modifier.padding(end = 3.dp)
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(caretX.roundToInt(), 0) }
                    .width(2.dp)
                    .height(with(density) { (if (caretH > 0f) caretH else 0f).toDp() }
                        .coerceAtLeast(18.dp))
                    .background(if (caretOn) ZtsGreen else Color.Transparent)
            )
        }
        if (query.isEmpty() && composingText.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hint),
                color = ZtsTextSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
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

    // タップ直前にどのタブを見ていたか。単タップで**待たずに**切り替えるようにした結果
    // (下の TabChip 参照)、ダブルタップで閉じるときには既にそのタブへ移っている。
    // 閉じたタブがアクティブだと SessionManager は左端のタブを選ぶので、そのままだと
    // 「別のタブを消しただけなのに関係ない所へ飛ばされる」。元居たタブへ戻してから閉じる。
    var activeBeforeTap by remember { mutableStateOf<String?>(null) }

    // 動作中の判定は tcgetpgrp を伴うので、タブごとに回さず**ここで 1 回だけ**まとめて見る
    // (1 秒 × タブ数の syscall を避ける)。判定できないタブ (SSH 等) は最初から対象外
    // — hasForegroundChild は判定不能なとき true を返すので、素直に使うと嘘の印が点く。
    var busyIds by remember { mutableStateOf(emptySet<String>()) }
    // 「見ていない間に終わった」タブ。開いたら消えるので、アクティブなタブは常に外す。
    var endedIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(sessions, activeId) {
        while (true) {
            val now = sessions.filter { it.busyKnown && it.isBusy }.map { it.id }.toSet()
            endedIds = nextEndedIds(endedIds, busyIds, now, activeId)
            busyIds = now
            delay(1000)
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
                        // アクティブなタブには印を出さない (見ているものに状態表示は要らない)。
                        mark = when {
                            sess.id == activeId -> TabMark.NONE
                            sess.id in busyIds -> TabMark.BUSY
                            sess.id in endedIds -> TabMark.ENDED
                            else -> TabMark.NONE
                        },
                        canClose = sessions.size > 1,
                        dragging = isDragging,
                        dragOffsetX = if (isDragging) dragOffset.value else 0f,
                        onWidth = { tabWidths[sess.id] = it },
                        onSelect = { activeBeforeTap = activeId; onSelect(sess.id) },
                        onClose = {
                            activeBeforeTap
                                ?.takeIf { prev -> prev != sess.id && sessions.any { it.id == prev } }
                                ?.let { prev -> onSelect(prev) }
                            onClose(sess.id)
                        },
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

/**
 * 「見ていない間に終わった」タブ ([TabMark.ENDED]) の次の集合を決める。
 *
 * 画面では確かめにくいわりに間違えやすいので純関数にしてある ([TabMarkTest])。規則は 3 つ:
 *  - `prevBusy` にあって `nowBusy` に無い = **終わった**ので足す
 *  - いま見ているタブ ([activeId]) は外す (開いた時点で印の役目は終わり)
 *  - また動き出したタブは外す (`✓` のまま動作中になると嘘になる)
 */
internal fun nextEndedIds(
    prevEnded: Set<String>,
    prevBusy: Set<String>,
    nowBusy: Set<String>,
    activeId: String?,
): Set<String> = (prevEnded + (prevBusy - nowBusy)).filterNot { it == activeId || it in nowBusy }.toSet()

/**
 * タブに出す状態の印 (0.8.229)。
 *
 * 判定 ([AppSession.isBusy]) は閉じる確認のために**もう計算されていた**のに、タブからは
 * 何も見えず「切り替えて確かめる」往復が要っていた。持っている情報を出すだけの追加。
 */
private enum class TabMark {
    /** 何も出さない (アクティブなタブ / 判定できないタブ / 静かなタブ)。 */
    NONE,

    /** いま子プロセスが動いている。 */
    BUSY,

    /** 見ていない間に終わった (そのタブを開くと消える)。 */
    ENDED,
}

@Composable
private fun TabChip(
    session: AppSession,
    active: Boolean,
    mark: TabMark,
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
    // 動作中タブをダブルタップで閉じようとしたときに出す削除確認ダイアログの表示フラグ (要望)。
    var showCloseConfirm by remember { mutableStateOf(false) }
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
    // 動作中 (前景に子プロセスが居る) のタブは即削除せず確認ダイアログを挟む (作業中の誤タップ防止・要望)。
    //
    // ⚠ **`combinedClickable` に `onDoubleClick` を渡さない** (0.8.245)。渡すと Compose は
    // 「2 回目が来ないこと」を確かめるまで `onClick` を出さないので、ダブルタップの猶予
    // (`doubleTapTimeoutMillis` = 端末の設定。多くは 300ms) が**そのままタブ切替の待ち時間**に
    // なる。描画が重いわけではなく、押しても何も起きない時間が毎回挟まっていた (実機で指摘)。
    // 2 回目の判定は自前で持ち、**1 回目は待たずに切り替える**。
    val doubleTapWindowMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val onTap: () -> Unit = {
        // 時計は単調増加のものを使う (壁時計だと時刻合わせで飛んで誤判定する)。
        val now = SystemClock.uptimeMillis()
        val isSecondTap = canClose && lastTapAt != 0L && now - lastTapAt <= doubleTapWindowMs
        // 2 回目として使ったらリセット。3 回目が続けてまた「2 回目」になるのを防ぐ。
        lastTapAt = if (isSecondTap) 0L else now
        when {
            !isSecondTap -> onSelect()
            session.isBusy -> showCloseConfirm = true
            else -> onClose()
        }
    }
    // 長押し→左右ドラッグ=並べ替え (要望)。ドラッグ中のタブは前面 (zIndex) + 平行移動で追従。
    Box(
        modifier = Modifier
            .onSizeChanged { onWidth(it.width) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationX = dragOffsetX }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            when (mark) {
                // 動作中。4dp の塗り四角だけで、**点滅させない** (暗所で目障りになるうえ、
                // ターミナルの静かな見た目を壊す)。
                TabMark.BUSY -> Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ZtsGreen)
                )
                // 見ていない間に終わった。そのタブを開けば消える (= 見たら役目が終わる印)。
                TabMark.ENDED -> Text(
                    text = "✓",
                    color = ZtsTextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp)
                )
                TabMark.NONE -> Unit
            }
        }

        if (showInfo) {
            TabInfoPopup(name = label, engine = engineText)
        }
    }

    if (showCloseConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_close_busy_title),
            message = stringResource(R.string.confirm_close_busy_msg),
            confirmLabel = stringResource(R.string.confirm_close_busy_action),
            onConfirm = { showCloseConfirm = false; onClose() },
            onCancel = { showCloseConfirm = false }
        )
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
 *
 * ⚠ **入力メソッド ([com.zerotoship.z2term.ime.Z2ImeService]) からも使う**ので internal。
 * 高さの設定は端末とアプリ内の入力欄で同じものが効かないと、切り替えるたびに背丈が変わる。
 */
internal fun scaledKeyboardStyle(base: KeyboardStyle, targetHeightDp: Float): KeyboardStyle {
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
    faceOrder: List<KeyboardFace>,
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
                showJapaneseKeyboard = showJapaneseKeyboard,
                faceOrder = faceOrder
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
 *
 * ⚠ **入力メソッド ([com.zerotoship.z2term.ime.Z2ImeService]) からも使う**ので internal。
 * 端末とアプリ内の入力欄で候補の見た目・操作が違うと、同じキーボードに見えなくなる。
 */
/**
 * 候補バーの固定高さ (上下 2 段ぶん)。
 *
 * ⚠ 入力メソッド ([com.zerotoship.z2term.ime.Z2ImeService]) はこの高さの**席を常に確保**して
 * 入力ビューの高さを動かさない。高さが変わると入力メソッドの窓がリサイズされ、新しい窓枠が
 * タップを配る側へ伝わるまでの数フレームだけ古い窓枠で座標が決まる = この高さぶん上のキーが
 * 反応するため。席は透明で、使っていない間は insets からも外れる (`onComputeInsets`) ので
 * 画面には出ない。実際の高さと確保する高さがズレないよう、定数は 1 か所に置いて共有する。
 */
internal val CandidateBarHeight = 76.dp

@Composable
internal fun CandidateBar(
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
            .height(CandidateBarHeight)
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
    matchRows: List<Int>,
    onSeek: (Int) -> Unit,
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
    val hitWidth = 32.dp          // 見た目より広いタッチ領域 (細いつまみを掴みやすくする)
    val hitPadY = 10.dp           // 上下にも当たり判定を広げる
    val minThumbPx = with(density) { 44.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val trackH = constraints.maxHeight.toFloat()
        if (trackH <= 0f) return@BoxWithConstraints
        val totalRows = (scrollbackSize + rows).toFloat()
        val thumbH = (trackH * rows / totalRows).coerceIn(minThumbPx.coerceAtMost(trackH), trackH)
        val maxThumbTop = (trackH - thumbH).coerceAtLeast(0f)
        val frac = (scrollbackSize - scrollOffset).toFloat() / scrollbackSize
        val stateTop = (maxThumbTop * frac).coerceIn(0f, maxThumbTop)

        // ドラッグ中はここに指の位置を直接入れ、つまみの描画に使う。
        // scrollOffset(StateFlow) → recomposition の往復を待たずに追従するので、
        // 指とつまみがずれない (「掴んだ後もたつく」対策)。
        var dragTop by remember { mutableStateOf<Float?>(null) }
        val thumbTop = dragTop ?: stateTop

        // pointerInput は張り替えると進行中のジェスチャが捨てられる。以前は
        // scrollbackSize を key にしていたため、端末に出力があるたび (= scrollback が
        // 伸びるたび) 検出器が作り直され、掴んだ指が外れていた。
        // key は Unit にし、変化する値は rememberUpdatedState 経由で読む。
        val metrics = rememberUpdatedState(Triple(scrollbackSize, maxThumbTop, thumbH))

        // 検索ヒットの目盛り (0.8.233)。件数は「3 / 17」と出ていても、**17 件が上に固まって
        // いるのか散っているのか**が分からず ∨ を連打することになっていた。位置を出すだけで
        // 「あと何回押すか」が読める。検索していないときは matchRows が空なので何も描かない。
        if (matchRows.isNotEmpty()) {
            val tickH = with(density) { 2.dp.toPx() }
            // 同じ画素行に何本も描いても情報は増えないので間引く。grep 的な検索で
            // 数百件ヒットしても、帯にならず「濃さ」で分かる程度に留める。
            val ticks = remember(matchRows, trackH, totalRows) {
                matchRows.asSequence()
                    .map { abs -> (trackH * abs / totalRows).coerceIn(0f, trackH - tickH) }
                    .map { y -> (y / tickH).toInt() to y }
                    .distinctBy { it.first }
                    .map { it.second }
                    .toList()
            }
            ticks.forEach { y ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (y - with(density) { 5.dp.toPx() }).roundToInt()) }
                        // 当たり判定はつまみと同じ幅・高さ 12dp。細い線を狙わせない。
                        .width(hitWidth)
                        .height(12.dp)
                        .clickable {
                            val abs = (y / trackH * totalRows).roundToInt()
                            onSeek(abs)
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .width(barWidth)
                            .height(with(density) { tickH.toDp() })
                            .background(ZtsGreen.copy(alpha = 0.75f))
                    )
                }
            }
        }

        // つまみ。掴んで上下ドラッグで scrollback を移動できる。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (thumbTop - with(density) { hitPadY.toPx() }).roundToInt()) }
                .width(hitWidth)
                .height(with(density) { thumbH.toDp() } + hitPadY * 2)
                .pointerInput(Unit) {
                    // Initial パスで受けて即 consume する自前ループ。
                    //  - タッチスロープを待たないので down の瞬間から掴める
                    //    (detectDragGestures は数十 px 動かすまで反応しない)
                    //  - Initial で consume するため、下に重なっている端末 View
                    //    (AndroidView = TerminalInputView) にイベントが渡らない。
                    //    Main パスで渡ると View 側が「自分が処理した」として change を
                    //    consume し、drag ループが「他に取られた」と判断して即中断する
                    //    (= つまみを掴んでも動かない)。
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                                ?: continue
                            down.consume()
                            val (sbSize, maxTop, _) = metrics.value
                            if (sbSize <= 0 || maxTop <= 0f) continue
                            var top = (maxTop *
                                ((sbSize - session.scrollOffset.value).toFloat() / sbSize))
                                .coerceIn(0f, maxTop)
                            dragTop = top
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.firstOrNull { it.id == down.id } ?: break
                                // ⚠ 移動量は consume する前に読むこと。
                                // positionChange() は consume 済みの change に対して
                                // Offset.Zero を返すため、先に consume すると移動量が
                                // 常に 0 になりつまみが 1px も動かない。
                                val dy = change.positionChange().y
                                change.consume()
                                if (!change.pressed) break          // 指を離した
                                val (size, mt, _) = metrics.value
                                if (size <= 0 || mt <= 0f) continue
                                top = (top + dy).coerceIn(0f, mt)
                                dragTop = top
                                // frac=0 → offset=scrollbackSize(最上端)、frac=1 → offset=0(最下端)。
                                val f = (top / mt).coerceIn(0f, 1f)
                                session.setScrollOffset(
                                    (size * (1f - f)).roundToInt().coerceIn(0, size)
                                )
                            }
                            dragTop = null
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .width(barWidth)
                    .height(with(density) { thumbH.toDp() })
                    .clip(RoundedCornerShape(4.dp))
                    .background(ZtsGreen.copy(alpha = if (dragTop != null) 0.9f else 0.55f))
            )
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
                    text = pluralStringResource(R.plurals.scroll_offset_indicator, scrollOffset, scrollOffset),
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
