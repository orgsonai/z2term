package com.zerotoship.z2term.edge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.snippets.ShellHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The view owns no persisted command, output, directory or shell state. */
@android.annotation.SuppressLint("ViewConstructor") // Created only in code, with the saved item label.
internal class EdgeTerminalUi(context: Context, label: String, fillSpace: Boolean = false,
    close: (() -> Unit)? = null) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var session: EdgeTerminalSession? = null
    private var disposed = false
    private val history = EdgeTerminalHistory()
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyRequest: Job? = null
    private val historyMoves = mutableListOf<Boolean>()
    private val entry = EdgeSettingsUi.field(context).apply {
        id = R.id.edge_terminal_input
        hint = context.getString(R.string.edge_terminal_command)
        contentDescription = hint
        tooltipText = context.getString(R.string.edge_terminal_help)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        typeface = Typeface.MONOSPACE
        filters = arrayOf(InputFilter.LengthFilter(16384))
    }
    private val output = TextView(context).apply {
        id = R.id.edge_terminal_output
        setTextColor(EdgeEditorUi.foreground(context)); textSize = 14f; typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        contentDescription = context.getString(R.string.edge_terminal_result)
    }
    private val scroll = EdgeResultScrollView(context).apply {
        tag = "edge-terminal-scroll"
        background = EdgeSettingsUi.frame(context)
        addView(output, LayoutParams(-1, -2))
    }
    private var snippets: EdgeSnippetUi? = null
    private val resultArea = FrameLayout(context).apply {
        addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }
    private val status = EdgeSettingsUi.caption(context, context.getString(R.string.edge_terminal_ready))
    private val run = EdgeSettingsUi.button(context, context.getString(R.string.edge_run)) { execute() }
    private val stop = EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_stop)) {
        session?.close(); render()
    }
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            render(); handler.postDelayed(this, 100)
        }
    }

    init {
        orientation = VERTICAL
        contentDescription = label.ifBlank { context.getString(R.string.edge_terminal) }
        addView(resultArea, if (fillSpace) LayoutParams(-1, 0, 1f) else LayoutParams(-1, dp(220)))
        addView(status, LayoutParams(-1, -2))
        val actions = LinearLayout(context)
        actions.addView(stop, LayoutParams(0, -2, 1f))
        actions.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_copy)) {
            if (output.text.isNotEmpty()) context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(label, output.text))
        }, LayoutParams(0, -2, 1f))
        actions.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_terminal_clear)) {
            session?.clear(); render()
        }, LayoutParams(0, -2, 1f))
        actions.addView(EdgeEditorUi.button(context, "≡") { toggleSnippets() }.apply {
            contentDescription = context.getString(R.string.snippets_title)
            tooltipText = contentDescription
            tag = "edge-snippets-button"
        }, LayoutParams(dp(40), dp(48)))
        close?.let { actions.addView(EdgePanelControls.close(context, it)) }
        for (i in 0 until actions.childCount - (if (close != null) 1 else 0)) (actions.getChildAt(i) as TextView).apply {
            setSingleLine(true)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        addView(actions)
        addView(LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(entry, LayoutParams(0, -2, 1f))
            addView(run, LayoutParams(-2, -2))
        }, LayoutParams(-1, -2))
        entry.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                execute(); true
            } else false
        }
        entry.setOnKeyListener { _, code, event ->
            if (code !in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN) ||
                !event.hasNoModifiers() || BaseInputConnection.getComposingSpanStart(entry.text) >= 0) false
            else when (event.action) {
                KeyEvent.ACTION_DOWN -> { recall(code == KeyEvent.KEYCODE_DPAD_UP); true }
                KeyEvent.ACTION_UP -> true
                else -> false
            }
        }
        render(); handler.post(poll)
    }

    private fun dp(n: Int) = EdgeEditorUi.dp(context, n)

    private fun showResults() {
        scroll.visibility = VISIBLE
        snippets?.visibility = GONE
    }

    private fun toggleSnippets() {
        if (disposed) return
        if (snippets?.visibility == VISIBLE) { showResults(); return }
        if (snippets == null) {
            snippets = EdgeSnippetUi(context, { entry.text.toString() }, insert = { command ->
                if (command.isBlank() || command.length > 16384 || command.any { it == '\n' || it == '\r' || it == '\u0000' }) {
                    Toast.makeText(context, R.string.edge_terminal_one_line_required, Toast.LENGTH_LONG).show()
                    false
                } else {
                    historyRequest?.cancel(); historyMoves.clear()
                    history.load(emptyList())
                    entry.setText(command); entry.setSelection(entry.length())
                    true
                }
            }, close = { showResults() })
            resultArea.addView(snippets, FrameLayout.LayoutParams(-1, -1))
        }
        scroll.visibility = GONE
        snippets!!.visibility = VISIBLE
    }

    private fun recall(older: Boolean) {
        if (disposed) return
        fun move(back: Boolean) {
            val current = entry.text.toString()
            val command = if (back) history.older(current) else history.newer(current)
            command?.let { entry.setText(it); entry.setSelection(entry.length()) }
        }
        if (history.browsing) { move(older); return }
        if (historyRequest?.isActive == true) { historyMoves.add(older); return }
        if (!older) return
        val draft = entry.text.toString()
        historyMoves.add(true)
        historyRequest = historyScope.launch {
            val commands = withContext(Dispatchers.IO) {
                ShellHistory.load(context.applicationContext).map { it.command }
            }
            // Typing or executing while the file was being read takes priority over recall.
            if (!disposed && entry.text.toString() == draft) {
                history.load(commands)
                historyMoves.forEach(::move)
            }
            historyMoves.clear()
        }
    }

    private fun execute() {
        if (disposed || entry.text.isNullOrBlank()) return
        runCatching {
            if (session == null || session!!.state().phase in setOf(EdgeTerminalSession.Phase.ENDED, EdgeTerminalSession.Phase.FAILED))
                session = EdgeTerminalSession.create(context)
            val command = entry.text.toString()
            if (session!!.execute(command)) {
                historyRequest?.cancel(); historyMoves.clear()
                history.accepted(command)
                entry.setText("")
                showResults()
            }
            render()
        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
    }

    private fun render() {
        val state = session?.state()
        val text = state?.output.orEmpty()
        if (output.text.toString() != text) {
            val atEnd = !scroll.canScrollVertically(1)
            output.text = text
            if (atEnd) scroll.post { if (!disposed) scroll.scrollTo(0, (output.bottom - scroll.height + scroll.paddingBottom).coerceAtLeast(0)) }
        }
        val phase = state?.phase ?: EdgeTerminalSession.Phase.NEW
        status.visibility = if (phase == EdgeTerminalSession.Phase.NEW) GONE else VISIBLE
        run.isEnabled = phase in setOf(EdgeTerminalSession.Phase.NEW, EdgeTerminalSession.Phase.IDLE,
            EdgeTerminalSession.Phase.ENDED, EdgeTerminalSession.Phase.FAILED)
        stop.isEnabled = phase in setOf(EdgeTerminalSession.Phase.STARTING, EdgeTerminalSession.Phase.RUNNING, EdgeTerminalSession.Phase.IDLE)
        status.text = listOfNotNull(state?.environment?.takeIf { it.isNotBlank() }, when (phase) {
            EdgeTerminalSession.Phase.NEW -> context.getString(R.string.edge_terminal_ready)
            EdgeTerminalSession.Phase.STARTING -> context.getString(R.string.edge_terminal_starting)
            EdgeTerminalSession.Phase.RUNNING -> context.getString(R.string.edge_terminal_running)
            EdgeTerminalSession.Phase.IDLE -> state?.exitCode?.let { context.getString(R.string.edge_terminal_exit, it) }
                ?: context.getString(R.string.edge_terminal_ready)
            EdgeTerminalSession.Phase.ENDED -> context.getString(R.string.edge_terminal_ended)
            EdgeTerminalSession.Phase.FAILED -> state?.error
        }, if (state?.truncated == true) context.getString(R.string.edge_terminal_truncated) else null).joinToString(" · ")
    }

    fun dispose() {
        disposed = true
        historyScope.cancel(); historyMoves.clear()
        snippets?.dispose()
        handler.removeCallbacks(poll)
        session?.close(); session = null
    }
}
