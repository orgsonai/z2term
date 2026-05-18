package com.zerotoship.z2term.ui.terminal.input

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.zerotoship.z2term.core.TerminalSession

/**
 * ターミナル入力専用 View。
 *
 * 2 つのモードを持つ:
 *  - [imeEnabled] = false (既定): OS の IME を一切呼ばない。物理キーは
 *    onKeyDown 経由で受け取り PTY へ流す。タップしても IME は表示しない。
 *    UI 側は独自キーボード ([TerminalKeyboard]) を画面下に置く想定。
 *  - [imeEnabled] = true: 旧来の Termux 方式。`BaseInputConnection(fullEditor=true)`
 *    の Editable に IME が preedit を貯め、commitText のタイミングで一括 PTY 送出。
 *    日本語等の IME 入力 (composing 含む) はこちらのモードでしか動かない。
 *
 * モード変更は `imeEnabled` セッターで実行時に切替可能。setter は
 * InputMethodManager.restartInput を呼んで IME の表示状態を即時同期する。
 */
class TerminalInputView(context: Context) : View(context) {

    /** PTY を持つセッション。setter を経由して更新できる。 */
    var session: TerminalSession? = null

    /** SpecialKeyBar の sticky-Ctrl がトグル ON のとき true (KeyMapper に伝える) */
    var ctrlSticky: Boolean = false

    /**
     * OS IME を使うかどうか。false なら独自キーボードのみ。
     * セットすると即座に IME の再接続と表示状態調整が走る。
     */
    var imeEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val imm = context.getSystemService(InputMethodManager::class.java)
            // editor の textEditor 性質が変わったので InputConnection を作り直させる
            imm?.restartInput(this)
            if (!value) {
                // IME を引っ込める
                imm?.hideSoftInputFromWindow(windowToken, 0)
            }
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // KeyEvent を受けるには focus が必須
    }

    override fun onCheckIsTextEditor(): Boolean = imeEnabled

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!imeEnabled) return null
        // TYPE_NULL: 「アプリ自身がテキストを保持・編集しない」と宣言。
        // これで IME は extract UI を出さず、確定文字列だけを commitText で送ってくる。
        outAttrs.inputType = InputType.TYPE_NULL
        // FORCE_ASCII: 日本語 IME がデフォルト「あ」モードのままだと "ls" が
        // 全角「ｌｓ」になりシェルが解釈できない。ターミナル入力では ASCII を
        // 既定にするよう IME に強く要求する (尊重しない IME もある)。
        outAttrs.imeOptions = (
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_FORCE_ASCII or
                EditorInfo.IME_ACTION_NONE
            )
        return TerminalInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 物理キーは imeEnabled の状態に関係なく直接処理する。
        // (BT キーボード接続時など独自キーボード経由で打てないキー用)
        val sess = session ?: return super.onKeyDown(keyCode, event)
        val bytes = AndroidKeyMapper.mapKeyEvent(event, ctrlSticky) { key ->
            sess.emulator.cursorKeyBytes(key)
        } ?: return super.onKeyDown(keyCode, event)
        sess.writeBytes(bytes)
        return true
    }

    /** OS IME を表示する (imeEnabled=true のときのみ動作) */
    fun requestKeyboard() {
        if (!imeEnabled) return
        if (!isFocused) requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * タップ処理。
     * - imeEnabled=true: OS IME を表示
     * - imeEnabled=false: IME は出さず、独自キーボード前提で何もしない
     *   (ただし focus は確保しておくと物理キーが届きやすい)
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (!isFocused) requestFocus()
            if (imeEnabled) requestKeyboard()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/**
 * Termux 方式の InputConnection。
 *
 * super (BaseInputConnection) に Editable を持たせて preedit を貯めさせ、
 * 確定タイミングで一括して PTY に送る。
 */
private class TerminalInputConnection(
    private val targetView: TerminalInputView
) : BaseInputConnection(targetView, /* fullEditor = */ true) {

    private val session: TerminalSession? get() = targetView.session

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        // 変換中: PTY には送らず、内部 Editable に preedit を貯めるだけ。
        return super.setComposingText(text, newCursorPosition)
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        // 確定: super で Editable に書き込み → flush で PTY 送出
        super.commitText(text, newCursorPosition)
        flushEditable()
        return true
    }

    override fun finishComposingText(): Boolean {
        // フォーカスロスなどで variation 確定: 残っている preedit があれば送る
        super.finishComposingText()
        flushEditable()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        super.deleteSurroundingText(beforeLength, afterLength)
        // PTY 側にも対応する BS を送る (preedit を消した分だけ)
        if (beforeLength > 0) {
            val sess = session
            if (sess != null) {
                val bs = ByteArray(beforeLength) { 0x7F }
                sess.writeBytes(bs)
            }
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        // ソフト IME 上の物理キー (Enter/Backspace/矢印 等) が来る経路
        if (event.action == KeyEvent.ACTION_DOWN) {
            val sess = session
            if (sess != null) {
                val bytes = AndroidKeyMapper.mapKeyEvent(event, targetView.ctrlSticky) { key ->
                    sess.emulator.cursorKeyBytes(key)
                }
                if (bytes != null) {
                    sess.writeBytes(bytes)
                    return true
                }
            }
        }
        return super.sendKeyEvent(event)
    }

    private fun flushEditable() {
        val editable = editable ?: return
        if (editable.isEmpty()) return
        // IME が "\n" を Enter として埋め込んでくることがあるので CR に正規化
        val text = editable.toString().replace('\n', '\r')
        session?.writeBytes(text.toByteArray(Charsets.UTF_8))
        editable.clear()
    }
}
