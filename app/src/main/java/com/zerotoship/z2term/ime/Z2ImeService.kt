package com.zerotoship.z2term.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.emulator.resolveTheme
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.terminal.CandidateBar
import com.zerotoship.z2term.ui.terminal.CandidateBarHeight
import com.zerotoship.z2term.ui.terminal.scaledKeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.ComposingState
import com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore
import com.zerotoship.z2term.ui.terminal.keyboard.KanaKanjiConverter
import com.zerotoship.z2term.ui.terminal.keyboard.KeyboardStyle
import com.zerotoship.z2term.ui.terminal.keyboard.KkcConverter
import com.zerotoship.z2term.ui.terminal.keyboard.TerminalKeyboard
import com.zerotoship.z2term.ui.terminal.keyboard.UserDictStore
import com.zerotoship.z2term.ui.theme.AppColors
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import kotlinx.coroutines.launch

/**
 * 内蔵キーボードを **OS の入力メソッド (IME)** として提供するサービス。
 *
 * **なぜ要るか**: 内蔵キーボードは `TerminalKeyboard(onBytes = …)` という形で**端末へバイトを送る
 * 専用部品**として作られていて、アプリ内の入力欄 (スニペット・SSH プロファイル・SFTP・設定…) は
 * すべて OS の IME が相手だった。⚠ 端末では自前のかな漢字変換が使えるのに、同じアプリの中の
 * 入力欄では別のキーボードに切り替わる — 「アプリ内では内蔵キーボードで打てない」という状態。
 *
 * 入力欄ごとに自前描画へ差し替える手 (検索バーがそれ) もあるが、⚠ **それは文字選択・コピペ・
 * カーソル移動・オートフィルまで全部作り直す**ことになり、20 箇所ぶん品質を保てない。IME にすれば
 * **OS のキーボード切替がそのまま「内蔵 / システム」の切替**になり、アプリ内の全入力欄はもちろん
 * 他アプリでも同じキーボードで打てる。
 *
 * **同じ部品を使う**: 描画は端末と同じ [TerminalKeyboard] + [CandidateBar]、変換も同じ
 * [ComposingState] / [KkcConverter]。⚠ 見た目や候補の出方をここで作り分けない — 同じキーボードに
 * 見えなくなるうえ、直す場所が 2 つになる。違うのは**出口だけ** ([ImeKeyTranslator] が端末向けの
 * バイト列を `InputConnection` の操作へ読み替える)。
 *
 * ⚠ **`InputMethodService` は `LifecycleOwner` ではない**ので、`ComposeView` が要求する 3 つの
 * オーナー (lifecycle / ViewModelStore / SavedStateRegistry) を自前で用意して view tree に載せる。
 * ⚠ しかも **`ComposeView` 自身に付けるだけでは足りない** — Compose は窓の**根**から
 * `LifecycleOwner` を探すため ([AbstractComposeView] → windowRecomposer)、入力メソッドの窓
 * (`Dialog`) の decorView にも同じオーナーを載せないと、キーボードが出た瞬間に
 * `ViewTreeLifecycleOwner not found` でアプリごと落ちる ([attachOwners])。
 */
class Z2ImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var appSettings: AppSettings

    /**
     * 変換中の状態。**サービスが持つ** — 入力ビューは設定変更などで作り直されるので、
     * Compose 側に持たせると打っている途中のかなが消える。
     *
     * 確定文字はここから直接 `InputConnection` へ渡す。⚠ `commitText` は composing 領域を
     * 置き換える仕様なので、先に `setComposingText` を出していても二重に入らない。
     */
    private val composing = ComposingState(onCommit = { text ->
        currentInputConnection?.commitText(text, 1)
    })

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        appSettings = AppSettings(this)
        // 独自テーマは端末画面が読み込む作りなので、キーボードだけ先に使われたときのために
        // ここでも読む (どちらから呼んでも 1 度しか読まない)。
        CustomThemeStore.ensureLoaded(this)
        // 辞書は端末側と同じものを 1 度だけ読む (どちらから先に使っても二重に読まない)。
        lifecycleScope.launch {
            KanaKanjiConverter.ensureLoaded(this@Z2ImeService)
            KkcConverter.ensureLoaded(this@Z2ImeService)
            ImeHistoryStore.ensureLoaded(this@Z2ImeService)
            UserDictStore.ensureLoaded(this@Z2ImeService)
        }
    }

    override fun onCreateInputView(): View {
        // ⚠ 窓の decorView が先。ComposeView だけに付けても Compose は見つけられない (下記)。
        attachOwners(window?.window?.decorView)
        val view = ComposeView(this)
        attachOwners(view)
        // ⚠ Android 15 (targetSdk 35) は**入力メソッドの窓も画面の端まで**広げる。何もしないと
        // キーボードの最下段が 3 ボタンナビゲーションバーの裏に潜り、← ↓ ↑ → や ⏎ が押せない
        // (バーの側が反応して「戻る」等になる)。バーのぶんだけ下に余白を作って持ち上げる。
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            navBarInsetPx.intValue = insets.tappableBottom()
            insets
        }
        view.setContent { KeyboardContent() }
        return view
    }

    /**
     * 下端に空けるナビゲーションバーぶんの余白 (px)。
     *
     * ⚠ [WindowInsetsCompat.Type.navigationBars] ではなく **tappableElement** を見る —
     * ジェスチャー操作の端末では「バー」は細いハンドルだけでタップを奪わないので 0 が返り、
     * 余計な隙間が空かない。3 ボタン操作のときだけバーの高さぶん持ち上がる。
     */
    private val navBarInsetPx = mutableIntStateOf(0)

    private fun WindowInsetsCompat.tappableBottom(): Int =
        getInsets(WindowInsetsCompat.Type.tappableElement()).bottom

    /**
     * 窓から今のナビゲーションバー高さを読み直す。
     * リスナー ([onCreateInputView]) が呼ばれないまま入力ビューが出る経路 (窓の作り直し・
     * 操作方法の変更直後) の取りこぼしを埋める。
     */
    private fun refreshNavBarInset() {
        val raw = window?.window?.decorView?.rootWindowInsets ?: return
        navBarInsetPx.intValue = WindowInsetsCompat.toWindowInsetsCompat(raw).tappableBottom()
    }

    /**
     * `ComposeView` が要求する 3 つのオーナーを [view] に載せる。
     *
     * ⚠ **`ComposeView` 自身に付けるだけでは動かない。** Compose は composition を作るとき
     * `AbstractComposeView.resolveParentCompositionContext()` → `windowRecomposer` と辿り、
     * **窓の根 (`contentChild`) から** `findViewTreeLifecycleOwner()` を呼ぶ。入力メソッドの窓は
     * `Dialog` (`SoftInputWindow`) なので根は decorView 配下の `parentPanel` になり、その上に
     * オーナーが無いと `IllegalStateException: ViewTreeLifecycleOwner not found` が
     * **メインスレッドの未捕捉例外**として上がる = キーボードが出た瞬間にアプリごと落ちる。
     * 端末セッションも一緒に死ぬので、キーボード切替で選んだだけで作業中の端末が消えていた。
     */
    private fun attachOwners(view: View?) {
        view ?: return
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 画面回転や設定変更で窓ごと作り直されることがある。作り直された decorView には
        // オーナーが付いていないので、出すたびに載せ直す (同じ値の付け直しなので無害)。
        attachOwners(window?.window?.decorView)
        refreshNavBarInset()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        // ⚠ 入力欄が変わったら変換中を捨てる。持ち越すと、前の欄へ打っていたかなが
        // 次の欄に確定されて入る (端末と検索バーで同じ事故を踏んだ)。
        composing.reset()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        composing.reset()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    @Composable
    private fun KeyboardContent() {
        val settings by appSettings.flow.collectAsState(initial = AppSettings.Snapshot())
        val customTheme by CustomThemeStore.theme.collectAsState()
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val style = scaledKeyboardStyle(
            KeyboardStyle.byId(settings.keyboardStyleId),
            if (isLandscape) settings.landscapeKeyboardHeightDp else settings.portraitKeyboardHeightDp
        )
        // 配色もアプリで選んだテーマに揃える。⚠ AppColors を更新するのは端末画面なので、
        // アプリを開かずキーボードだけ使うときは既定色のままになる。ここでも当てておく。
        LaunchedEffect(settings.themeName, customTheme) {
            AppColors.applyFrom(resolveTheme(settings.themeName, customTheme))
        }
        // 変換中のかなは**下線付きのプリエディット**として相手の入力欄に見せる。端末では
        // 自前で描いているものを、ここでは OS の仕組み (setComposingText) に任せる。
        MirrorComposingText()
        // 3 ボタンナビゲーションバーのぶんだけ下に余白を足す (背景の内側なので色は続いて見える)。
        val navBarPadding = with(LocalDensity.current) { navBarInsetPx.intValue.toDp() }
        val isJa = LocaleHelper.language(this@Z2ImeService) == LocaleHelper.LANG_JA
        Z2TermTheme {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 候補バーの**席**。中身の有無にかかわらず高さを [CandidateBarHeight] で固定する。
                //
                // ⚠ ここを可変にしてはいけない。入力ビューの高さが変わると入力メソッドの窓が
                // リサイズされ、新しい窓枠がタップを配る側 (システム) に伝わるまでの数フレーム、
                // タップは**古い窓枠**を基準に座標へ直される = 実際に触った位置より候補バーの
                // 高さぶん上のキーが反応する。窓の高さを動かさなければ、この過渡期そのものが
                // 存在しない。候補バーが出てしまえばズレないのも同じ理由 (窓枠が伝わり終えている)。
                //
                // ⚠ 席は**塗らない** (背景を付けない)。候補バーが出ていない間ここは透けて下の
                // アプリが見え、[onComputeInsets] で insets からも外すので、席を確保している
                // ことは画面にも相手アプリのレイアウトにも一切現れない。
                Box(modifier = Modifier.fillMaxWidth().height(CandidateBarHeight)) {
                    CandidateBar(composing = composing)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZtsBgSecondary)
                        .padding(bottom = navBarPadding)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().height(style.naturalHeight)) {
                        TerminalKeyboard(
                            onBytes = ::sendBytes,
                            onCursorKey = ::sendCursorKey,
                            composing = composing,
                            style = style,
                            showJapaneseKeyboard = isJa
                        )
                    }
                }
            }
        }
    }

    /**
     * 入力メソッドが**画面のどこを占めているか**をシステムへ伝える。
     *
     * 入力ビューの上端には候補バーの席 ([CandidateBarHeight]) が常にあるが、候補バーが出て
     * いない間そこは**透明な空き地**でしかない。既定の実装は入力ビューの上端をそのまま伝える
     * ので、放っておくと空き地のぶんだけ相手アプリが押し上げられ、キーボードの上に使っていない
     * 帯が居座って見える。席のぶんを差し引いて「キーボードの上端から下だけが入力メソッドだ」と
     * 伝えれば、席を確保していることは相手アプリからは見えない。
     *
     * - `contentTopInsets`: 相手アプリがレイアウトを避ける線。
     * - `visibleTopInsets`: 入力メソッドが実際に見えている線。この上のタップは相手アプリへ通る
     *   (既定の `touchableInsets` = `TOUCHABLE_INSETS_VISIBLE`)。空き地を押しても下のアプリが
     *   反応するので、透明な席がタップを食べてしまうことはない。
     *
     * ⚠ ここで返す値は**窓の大きさではない**。insets が変わっても入力メソッドの窓は 1px も
     * 動かない — だから候補バーの出し入れでタップがズレない。それがこの作りの目的。
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // 候補バーが出ていれば席は「中身入り」= 入力メソッドの一部。出ていなければ空き地。
        if (composing.isActive) return
        val reserved = (CandidateBarHeight.value * resources.displayMetrics.density).toInt()
        outInsets.contentTopInsets += reserved
        outInsets.visibleTopInsets += reserved
    }

    /** [composing] の変化を `setComposingText` / `finishComposingText` へ流す。 */
    @Composable
    private fun MirrorComposingText() {
        LaunchedEffect(Unit) {
            snapshotFlow { composing.text }.collect { text ->
                val ic = currentInputConnection ?: return@collect
                if (text.isEmpty()) ic.finishComposingText() else ic.setComposingText(text, 1)
            }
        }
    }

    /** 内蔵キーボードのバイト列を入力欄の操作へ読み替えて流す。 */
    private fun sendBytes(bytes: ByteArray) {
        val ic = currentInputConnection ?: return
        for (action in ImeKeyTranslator.translate(bytes)) {
            when (action) {
                is ImeKeyAction.Insert -> ic.commitText(action.text, 1)
                // ⚠ 削除は deleteSurroundingText ではなくキーイベントで送る — 範囲選択中は
                // 前者では消えず、「選んでから ⌫」が効かない入力欄になる。
                ImeKeyAction.DeleteBack -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                ImeKeyAction.DeleteWordBack -> deleteBefore(ic) { ImeKeyTranslator.wordBackLength(it) }
                ImeKeyAction.DeleteToLineStart -> deleteBefore(ic) { ImeKeyTranslator.toLineStartLength(it) }
                ImeKeyAction.Newline -> sendNewline(ic)
                ImeKeyAction.Tab -> sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            }
        }
    }

    /** キャレット前のテキストを見て、[length] が返す長さだけ消す。 */
    private inline fun deleteBefore(ic: InputConnection, length: (CharSequence) -> Int) {
        val before = ic.getTextBeforeCursor(MAX_LOOKBACK, 0) ?: return
        val n = length(before)
        if (n > 0) ic.deleteSurroundingText(n, 0)
    }

    /**
     * ⏎ の行き先を決める。
     *
     * 1 行の入力欄で改行を入れても何も起きないので、**その欄が求めている動作** (検索・完了・次へ…)
     * を実行する。複数行の欄では素直に改行を入れる。
     */
    private fun sendNewline(ic: InputConnection) {
        val info = currentInputEditorInfo
        val multiline = (info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (!multiline && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    /** 矢印キー。入力欄ではキャレット移動になる。 */
    private fun sendCursorKey(key: TerminalEmulator.CursorKey) {
        sendDownUpKeyEvents(
            when (key) {
                TerminalEmulator.CursorKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                TerminalEmulator.CursorKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                TerminalEmulator.CursorKey.UP -> KeyEvent.KEYCODE_DPAD_UP
                TerminalEmulator.CursorKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            }
        )
    }

    private companion object {
        /** Ctrl+W / Ctrl+U で遡って見る文字数 (1 行ぶんあれば足りる)。 */
        const val MAX_LOOKBACK = 1000
    }
}
