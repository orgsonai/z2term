package com.zerotoship.z2term.ui.terminal

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.emulator.TerminalEmulator
import com.zerotoship.z2term.service.TerminalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI 層から TerminalSession を扱うための薄いラッパー ViewModel。
 *
 * セッション本体 (PtyProcess / emulator / IO ループ) は [TerminalSession] が所有し、
 * [SessionManager] のシングルトンとして Activity ライフサイクルを越えて生存する。
 * フォアグラウンドサービス [TerminalService] が起動している間は OS による回収から
 * 守られる。
 *
 * UI ローカルな状態 (選択範囲) のみ ViewModel が保持する。
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val session: TerminalSession = SessionManager.get(application)

    val emulatorRef: TerminalEmulator get() = session.emulator

    val uiState: StateFlow<TerminalSession.UiState> = session.uiState
    val redrawTick: StateFlow<Int> = session.redrawTick
    val scrollOffset: StateFlow<Int> = session.scrollOffset
    val toastEvents = session.toastEvents
    val settingsFlow = session.settingsFlow

    // ───────── 選択範囲 (UI ローカル) ─────────

    data class Selection(
        val anchorRow: Int,
        val anchorCol: Int,
        val focusRow: Int,
        val focusCol: Int
    ) {
        fun normalized(): IntArray {
            val (sr, sc, er, ec) = if (anchorRow < focusRow || (anchorRow == focusRow && anchorCol <= focusCol)) {
                listOf(anchorRow, anchorCol, focusRow, focusCol)
            } else {
                listOf(focusRow, focusCol, anchorRow, anchorCol)
            }
            return intArrayOf(sr, sc, er, ec)
        }
    }

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    fun beginSelection(row: Int, col: Int) { _selection.value = Selection(row, col, row, col) }
    fun updateSelection(row: Int, col: Int) {
        val s = _selection.value ?: return
        _selection.value = s.copy(focusRow = row, focusCol = col)
    }
    fun cancelSelection() { _selection.value = null }
    fun copySelectionToClipboard() {
        val s = _selection.value ?: return
        val n = s.normalized()
        val text = emulatorRef.buffer.getRangeText(n[0], n[1], n[2], n[3]).trimEnd()
        if (text.isEmpty()) {
            session.emitToast("選択範囲が空です")
            _selection.value = null
            return
        }
        setClipboard(text)
        session.emitToast("${text.length} 文字をコピーしました")
        _selection.value = null
    }

    // ───────── セッション操作 (TerminalSession へ委譲) ─────────

    fun startTerminal() {
        // 初回起動時にフォアグラウンドサービスも開始
        TerminalService.start(getApplication())
        session.startTerminal()
    }

    fun restart() = session.restart()
    fun sendInput(text: String) = session.writeBytes(text.toByteArray(Charsets.UTF_8))
    fun sendRawBytes(bytes: ByteArray) = session.writeBytes(bytes)
    fun onTerminalResize(rows: Int, cols: Int) = session.onResize(rows, cols)
    fun setScrollOffset(offset: Int) = session.setScrollOffset(offset)
    fun scrollBy(delta: Int) = session.scrollBy(delta)
    fun jumpToBottom() = session.jumpToBottom()
    fun clearOutput() = session.clearOutput()

    fun updateTheme(name: String) = session.setThemeName(name)
    fun updateFontSize(sp: Float) = session.setFontSize(sp)
    fun updateScrollbackLines(lines: Int) = session.setScrollbackLines(lines)

    fun stopAndExit() {
        TerminalService.stop(getApplication())
    }

    fun sendSpecialKey(key: SpecialKey) {
        val bytes = when (key) {
            SpecialKey.ENTER -> byteArrayOf(0x0d)
            SpecialKey.TAB -> byteArrayOf(0x09)
            SpecialKey.ESC -> byteArrayOf(0x1b)
            SpecialKey.BACKSPACE -> byteArrayOf(0x7f)
            SpecialKey.UP -> emulatorRef.cursorKeyBytes(TerminalEmulator.CursorKey.UP)
            SpecialKey.DOWN -> emulatorRef.cursorKeyBytes(TerminalEmulator.CursorKey.DOWN)
            SpecialKey.RIGHT -> emulatorRef.cursorKeyBytes(TerminalEmulator.CursorKey.RIGHT)
            SpecialKey.LEFT -> emulatorRef.cursorKeyBytes(TerminalEmulator.CursorKey.LEFT)
            SpecialKey.CTRL_A -> byteArrayOf(0x01)
            SpecialKey.CTRL_C -> byteArrayOf(0x03)
            SpecialKey.CTRL_D -> byteArrayOf(0x04)
            SpecialKey.CTRL_E -> byteArrayOf(0x05)
            SpecialKey.CTRL_K -> byteArrayOf(0x0b)
            SpecialKey.CTRL_L -> byteArrayOf(0x0c)
            SpecialKey.CTRL_R -> byteArrayOf(0x12)
            SpecialKey.CTRL_U -> byteArrayOf(0x15)
            SpecialKey.CTRL_W -> byteArrayOf(0x17)
            SpecialKey.CTRL_Z -> byteArrayOf(0x1a)
            SpecialKey.HOME -> ESC_BRACKET + 'H'.code.toByte()
            SpecialKey.END -> ESC_BRACKET + 'F'.code.toByte()
            SpecialKey.PAGE_UP -> ESC_BRACKET + "5~".toByteArray()
            SpecialKey.PAGE_DOWN -> ESC_BRACKET + "6~".toByteArray()
            SpecialKey.F1 -> ESC_O + 'P'.code.toByte()
            SpecialKey.F2 -> ESC_O + 'Q'.code.toByte()
            SpecialKey.F3 -> ESC_O + 'R'.code.toByte()
            SpecialKey.F4 -> ESC_O + 'S'.code.toByte()
            SpecialKey.F5 -> ESC_BRACKET + "15~".toByteArray()
            SpecialKey.F6 -> ESC_BRACKET + "17~".toByteArray()
            SpecialKey.F7 -> ESC_BRACKET + "18~".toByteArray()
            SpecialKey.F8 -> ESC_BRACKET + "19~".toByteArray()
            SpecialKey.F9 -> ESC_BRACKET + "20~".toByteArray()
            SpecialKey.F10 -> ESC_BRACKET + "21~".toByteArray()
            SpecialKey.F11 -> ESC_BRACKET + "23~".toByteArray()
            SpecialKey.F12 -> ESC_BRACKET + "24~".toByteArray()
        }
        session.writeBytes(bytes)
    }

    // ───────── クリップボード ─────────

    fun copyAllToClipboard() {
        val text = emulatorRef.buffer.getAllText(includeScrollback = true).trimEnd()
        if (text.isEmpty()) {
            session.emitToast("コピーするテキストがありません")
            return
        }
        setClipboard(text)
        session.emitToast("${text.length} 文字をコピーしました")
    }

    fun pasteFromClipboard() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: run { session.emitToast("クリップボードが空です"); return }
        if (clip.itemCount == 0) { session.emitToast("クリップボードが空です"); return }
        val text = clip.getItemAt(0).coerceToText(getApplication()).toString()
        if (text.isEmpty()) { session.emitToast("クリップボードが空です"); return }
        session.writeBytes(text.toByteArray(Charsets.UTF_8))
    }

    private fun setClipboard(text: String) {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("z2term", text))
    }

    // ViewModel 破棄 = Activity 破棄。セッションは生かしたままにする。
    // セッションを終了するには stopAndExit() を呼ぶ (通知の停止ボタンと同等)。

    enum class SpecialKey {
        ENTER, TAB, ESC, BACKSPACE,
        UP, DOWN, LEFT, RIGHT,
        CTRL_A, CTRL_C, CTRL_D, CTRL_E, CTRL_K, CTRL_L, CTRL_R, CTRL_U, CTRL_W, CTRL_Z,
        HOME, END, PAGE_UP, PAGE_DOWN,
        F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12
    }

    companion object {
        private val ESC_BRACKET = byteArrayOf(0x1B, '['.code.toByte())
        private val ESC_O = byteArrayOf(0x1B, 'O'.code.toByte())
    }
}
