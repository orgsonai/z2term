package com.zerotoship.z2term.automation

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zerotoship.z2term.security.AppLock
import kotlinx.coroutines.flow.collect
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.AppCatalog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * Shared manager for the tools tab and the standalone activity; both use the same draft and store.
 *
 * Its look follows the other command-sheet tabs through [ActionUi] (0.8.603): heading row with the
 * actions on the right, bordered rows with glyph actions, pill buttons, hint boxes and app dialogs.
 */
internal class ActionMacrosEditor(
    private val activity: ComponentActivity,
    savedInstanceState: Bundle?,
    private val embedded: Boolean = false,
    private val onClose: () -> Unit
) : ContextThemeWrapper(activity, android.R.style.Theme_DeviceDefault_DayNight) {
    // ⚠ Compose's AndroidView forwards nested scrolling only when this outermost view has it enabled
    // (AndroidViewHolder asks the hosted view). Without it, the drag the editor leaves over at its top was
    // dropped, so pulling the command sheet down to close worked only from its handle (1.0.106).
    val view = FrameLayout(this).apply { isNestedScrollingEnabled = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var disposed = false
    var editing = false
        private set
    private var name = ""
    private var originalName: String? = null
    private var baseline: String? = null
    private var initial = ActionEditorDocument.EMPTY
    private var raw = initial
    private var textMode = false
    private var busy = false
    private var unlockPromptShowing = false
    private var unlockFailed = false
    private var stepLine = -1
    private var stepGroup = -1
    private var stepDraft: String? = null
    private var pickRequest: String? = null
    private var pickedScreen: String? = null
    private var stepDialog: Dialog? = null
    private var names = emptyList<String>()
    /** 呼び出し専用 (`routine=on`、1.0.203)。一覧では下の「ルーチン」に畳む。 */
    private var routines = emptySet<String>()
    private var routinesOpen = false
    /** 各マクロが呼ぶマクロ (1.0.206)。ルーチンは呼ぶマクロの下に木の形で並べる。 */
    private var calls = emptyMap<String, List<String>>()
    /** 開いている木の枝。同じルーチンが別の所から呼ばれても別々に開くよう、道筋 (`a/b/c`) で覚える。 */
    private val expanded = mutableSetOf<String>()
    /** The list shows ↑ ↓ in place of each row's actions while the user arranges it. */
    private var arranging = false
    private var apps = emptyList<AppCatalog.LaunchableApp>()
    private lateinit var body: LinearLayout
    private var statusView: TextView? = null
    private var stopButton: TextView? = null
    private var pauseButton: TextView? = null
    private var statusJob: Job? = null
    private val dialogs = mutableListOf<Dialog>()
    private var scrollView: NestedScrollView? = null
    private var renderedEditing = false
    private var editorScrollY = 0
    private val store get() = ActionRuntime.store(this)

    val scrollY: Int get() = scrollView?.scrollY ?: 0
    private val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> render()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> { statusJob?.cancel(); statusJob = null }
            Lifecycle.Event.ON_STOP -> { unlockFailed = false }
            Lifecycle.Event.ON_DESTROY -> dispose()
            else -> Unit
        }
    }

    init {
        savedInstanceState?.let {
            editorScrollY = it.getInt("editorScrollY")
            editing = it.getBoolean("editing"); name = it.getString("name").orEmpty()
            originalName = it.getString("originalName"); baseline = it.getString("baseline")
            initial = it.getString("initial") ?: ActionEditorDocument.EMPTY
            raw = it.getString("raw") ?: initial; textMode = it.getBoolean("textMode")
            stepLine = it.getInt("stepLine", -1); stepDraft = it.getString("stepDraft")
            stepGroup = it.getInt("stepGroup", -1)
            pickRequest = it.getString("pickRequest"); pickedScreen = it.getString("pickedScreen")
        }
    }

    fun start() {
        render()
        activity.lifecycle.addObserver(observer)
        scope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppLock.state.collect { state ->
                    render()
                    if (!embedded && state == AppLock.State.LOCKED && !unlockFailed) promptUnlock()
                    if (state == AppLock.State.UNLOCKED) restoreStep()
                }
            }
        }
        io({ AppCatalog.launchableApps(this) }) { apps = it }
    }

    private fun promptUnlock() {
        if (unlockPromptShowing) return
        unlockPromptShowing = true; unlockFailed = false
        AppLock.authenticate(activity, getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle)) {
            unlockPromptShowing = false
            if (it) AppLock.unlock() else unlockFailed = true
        }
    }
    private fun resume() {
        if (!editing) refresh()
        // The tools UI can be saved and removed while locked. Consume the result only after unlock.
        if (AppLock.state.value == AppLock.State.UNLOCKED) restoreStep()
        statusJob?.cancel()
        statusJob = scope.launch { while (true) { updateStatus(); delay(500) } }
    }

    private fun restoreStep() {
        pickRequest?.let { request ->
            // Returning manually to the editor ends selection too.
            if (ActionCoordinatePicker.isActive(request)) ActionCoordinatePicker.cancel()
            pickRequest = null
            val result = ActionCoordinatePicker.consume(request)
            if (result?.recorded != null) {
                try {
                    var updated = ActionBlockDocument(raw).insert(insertionGroup().start, result.recorded)
                    updated = ActionEditorDocument(updated).withHeader("version", "2")
                    updated = ActionEditorDocument(updated).withHeader("screen", result.screen.toString())
                    val timeout = ActionEditorDocument(updated).header("timeout")?.toLongOrNull() ?: 30L
                    val needed = ((result.durationMs ?: 0) / 1000 + 10).coerceAtMost(300)
                    if (timeout != 0L && needed > timeout) updated = ActionEditorDocument(updated).withHeader("timeout", needed.toString())
                    raw = updated; stepDraft = null; pickedScreen = null; render()
                } catch (e: Exception) { stepDraft = null; failure(e) }
            } else if (result?.selector != null) {
                val words = stepDraft.orEmpty().trim().split(Regex("\\s+"), limit = 3)
                val prefix = if (words.firstOrNull() == "wait-ui" && words.size >= 2) "wait-ui " + words[1]
                    else words.firstOrNull() ?: "click"
                stepDraft = prefix + " " + result.selector
            } else if (result?.points != null && stepDraft.orEmpty().trim().startsWith("drag ")) {
                // 点を順になぞる: 単位と 1 区間の時間は下書きのまま、点だけを取り直したものに替える。
                val words = stepDraft.orEmpty().trim().split(Regex("\\s+"))
                val unit = words.getOrNull(1) ?: "percent"
                fun coordinate(value: Float, extent: Int): String {
                    val converted = if (unit == "percent") value * 100 / (extent - 1).coerceAtLeast(1) else value
                    return java.math.BigDecimal(converted.toString()).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                }
                val points = result.points.chunked(2).joinToString(" ") { (x, y) ->
                    coordinate(x, result.screen.width) + "," + coordinate(y, result.screen.height)
                }
                stepDraft = "drag $unit ${words.getOrNull(2) ?: "100"} $points"; pickedScreen = result.screen.toString()
            } else if (result?.points != null) {
                val words = stepDraft.orEmpty().trim().split(Regex("\\s+")).toMutableList()
                if (words.size >= 2 && (words[0] in listOf("touch", "swipe-path", "swipe-two-path", "pinch-in", "pinch-out", "swipe-two")) && result.paths != null) {
                    val verb = if (result.paths.size == 2) "swipe-two-path" else "swipe-path"
                    stepDraft = verb + " " + words[1] + " " + result.paths.joinToString(" | ") {
                        ActionGesture.encode(ActionGesture.convert(it, "px", words[1], result.screen))
                    }
                    pickedScreen = result.screen.toString()
                } else if (words.size >= 4) {
                    val p = result.points
                    fun coordinate(value: Float, extent: Int): String {
                        val converted = if (words[1] == "percent") value * 100 / (extent - 1).coerceAtLeast(1) else value
                        return java.math.BigDecimal(converted.toString()).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    }
                    words[2] = coordinate(p[0], result.screen.width); words[3] = coordinate(p[1], result.screen.height)
                    if (words[0] == "swipe" && words.size in 7..8) {
                        words[4] = coordinate(p[2], result.screen.width); words[5] = coordinate(p[3], result.screen.height)
                        result.durationMs?.let { words[6] = it.toString() }
                    }
                    stepDraft = words.joinToString(" "); pickedScreen = result.screen.toString()
                }
            } else {
                if (stepDraft == "@record") stepDraft = null
                message(result?.error ?: getString(R.string.action_pick_cancelled))
            }
        }
        if (editing && stepDraft != null && stepDialog?.isShowing != true) showStep()
    }
    fun saveState(): Bundle = Bundle().also { outState ->
        outState.putInt("editorScrollY", if (renderedEditing && scrollView?.isLaidOut == true) scrollY else editorScrollY)
        outState.putBoolean("editing", editing); outState.putString("name", name)
        outState.putString("originalName", originalName); outState.putString("baseline", baseline)
        outState.putString("initial", initial); outState.putString("raw", raw); outState.putBoolean("textMode", textMode)
        outState.putInt("stepLine", stepLine); outState.putString("stepDraft", stepDraft)
        outState.putInt("stepGroup", stepGroup)
        outState.putString("pickRequest", pickRequest); outState.putString("pickedScreen", pickedScreen)
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        activity.lifecycle.removeObserver(observer)
        scope.cancel()
        stepDialog?.dismiss(); dialogs.toList().forEach { it.dismiss() }
    }
    private fun showManaged(dialog: Dialog) {
        dialogs += dialog
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }

    private fun <T> io(work: () -> T, complete: (T) -> Unit) {
        scope.launch {
            try { complete(withContext(Dispatchers.IO) { work() }) } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
        }
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun failure(error: Exception) = message(if (error is ActionStore.EditConflict)
        getString(R.string.action_edit_conflict) else error.message ?: getString(R.string.action_edit_error))
    private fun dp(value: Int) = ActionUi.dp(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun add(view: View, gap: Int = ActionUi.GAP) = ActionUi.add(body, view, gap)
    private fun note(text: Int) = ActionUi.note(this, getString(text))
    private fun pill(label: Int, accent: Boolean = false, danger: Boolean = false, action: () -> Unit) =
        ActionUi.pill(this, getString(label), accent, danger, action).apply {
            // The form view cannot open a document whose block structure does not parse.
            if (label == R.string.action_edit_form && runCatching { ActionBlockDocument(raw) }.isFailure) isEnabled = false
        }
    private class Choice(val label: Int, val accent: Boolean, val run: () -> Unit)
    private fun act(label: Int, accent: Boolean = false, run: () -> Unit) = Choice(label, accent, run)
    /** One line of equal actions, gutter to gutter. */
    private fun pills(vararg actions: Choice) = add(LinearLayout(this).apply {
        actions.forEachIndexed { index, choice ->
            addView(pill(choice.label, choice.accent, action = choice.run), LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
    })
    private fun render() {
        if (renderedEditing && scrollView?.isLaidOut == true) editorScrollY = scrollView?.scrollY ?: editorScrollY
        // 一覧のまま描き直すとき (ルーチンの開閉・並べ替え・更新) は、見ていた位置を残す。
        // ⚠ 残さないと、押すたびに一覧の先頭へ戻ってしまう (1.0.205)。
        val listY = if (!renderedEditing && !editing && scrollView?.isLaidOut == true) scrollView?.scrollY ?: 0 else -1
        // A lock screen has no editor scroll position to save over the retained draft.
        renderedEditing = editing && AppLock.state.value == AppLock.State.UNLOCKED
        // The body of every command-sheet tab: 16dp sides, 10dp above, 24dp below, 10dp between items.
        body = column().apply { setPadding(dp(ActionUi.GUTTER), dp(10), dp(ActionUi.GUTTER), dp(24)) }
        val scroll = object : NestedScrollView(this) {
            // ⚠ In the command sheet this view sits inside Compose, which hands a finger to an embedded View only
            // after the sheet's own drag has seen it. The sheet took every vertical drag, so the editor never
            // scrolled and its lower part stayed out of reach (1.0.105). Claim the finger on touch-down; scrolling
            // past the top still reaches the sheet through nested scrolling, so pulling it down to close works.
            // ⚠ Measure the finger against the screen, not this view (1.0.107). Pulling down at the top moves the
            // sheet, and this view with it; Compose moves it on the next frame, so nested scrolling reports no
            // offset and the finger seemed to travel back by the sheet's own movement. The sheet then shook
            // between following the finger and backing off.
            private var screenTop = 0f
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    screenTop = ev.rawY - ev.y
                }
                val shift = ev.rawY - screenTop - ev.y
                ev.offsetLocation(0f, shift)
                try { return super.dispatchTouchEvent(ev) } finally { ev.offsetLocation(0f, -shift) }
            }
        }.apply {
            isFillViewport = true
            setBackgroundColor(ActionUi.groundColor)
            addView(body)
            scrollView = this
        }
        view.removeAllViews()
        view.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        statusView = null; stopButton = null; pauseButton = null
        val unlocked = AppLock.state.value == AppLock.State.UNLOCKED
        header(unlocked)
        if (!unlocked) {
            stepDialog?.dismiss(); dialogs.toList().forEach { it.dismiss() }
            if (AppLock.state.value == AppLock.State.LOCKED) add(pill(R.string.lock_prompt_title, accent = true) { promptUnlock() })
        } else if (editing) renderEditor() else renderList()
        if (renderedEditing) scrollView?.post { scrollView?.scrollTo(0, editorScrollY) }
        else if (listY > 0 && !editing) scrollView?.post { scrollView?.scrollTo(0, listY) }
    }
    /** Title on the left, its actions on the right: the heading row of the other tabs. */
    private fun header(unlocked: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        row.addView(ActionUi.heading(this, getString(if (editing) R.string.action_edit_title else R.string.action_macro_title),
            if (editing) 16f else 18f), LinearLayout.LayoutParams(0, -2, 1f))
        fun action(control: View) = row.addView(control, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        if (unlocked && !editing) action(pill(R.string.action_edit_new, accent = true) { openEditor("", null) })
        if (!embedded || editing) action(pill(R.string.action_edit_close) { leave() })
        add(row)
    }
    private fun refresh() = io({ Triple(store.names(), store.routines(), store.calls()) }) { (n, r, c) ->
        names = n; routines = r; calls = c; if (!editing) render()
    }
    private fun renderList() {
        // What is running now, and the only way to stop it: one block, never mixed into the list.
        val running = ActionUi.card(this)
        statusView = ActionUi.label(this, "", 11f, ActionUi.mutedColor).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }.also { running.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        stopButton = pill(R.string.action_macro_stop, danger = true) {
            // Use the displayed ID so a late tap cannot stop a replacement run.
            val id = stopButton?.tag as? String
            if (id != null) runCatching { ActionRuntime.stop(id) }.onFailure { message(it.message.orEmpty()) }
            updateStatus()
        }
        pauseButton = pill(R.string.action_macro_pause) {
            val id = pauseButton?.tag as? String ?: return@pill
            runCatching {
                if (ActionRuntime.status(id).optString("state") == "paused") ActionRuntime.resume(id, background = {
                    // Same as Run: the macro continues on the screen behind z2term.
                    if (!embedded) com.zerotoship.z2term.MainActivity.moveToBackground()
                    activity.moveTaskToBack(true)
                }) else ActionRuntime.pause(id)
            }.onFailure { message(it.message.orEmpty()) }
            updateStatus()
        }
        running.addView(LinearLayout(this).apply {
            addView(pauseButton, LinearLayout.LayoutParams(-2, -2))
            addView(stopButton, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        add(running)
        updateStatus()
        // The run card above cannot be reached once the macro brings its target app forward.
        val overlaySwitch = com.zerotoship.z2term.edge.EdgeSettingsUi.switchOf(this, ActionRunOverlay.enabled(this)).apply {
            contentDescription = getString(R.string.action_overlay_setting)
            setOnCheckedChangeListener { _, value -> ActionRunOverlay.setEnabled(this@ActionMacrosEditor, value) }
        }
        add(ActionUi.card(this, vertical = false).apply {
            foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
            isClickable = true
            setOnClickListener { overlaySwitch.toggle() }
            addView(column().apply {
                addView(ActionUi.label(this@ActionMacrosEditor, getString(R.string.action_overlay_setting), 13f))
                addView(ActionUi.caption(this@ActionMacrosEditor, getString(R.string.action_overlay_setting_desc)).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(overlaySwitch, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        })
        // 終わったあとも帯を残すか (1.0.196、既定は閉じる)。残すと「選んで実行」の帯に替わる。
        val keepSwitch = com.zerotoship.z2term.edge.EdgeSettingsUi.switchOf(this, ActionRunOverlay.keepEnabled(this)).apply {
            contentDescription = getString(R.string.action_overlay_keep_setting)
            setOnCheckedChangeListener { _, value -> ActionRunOverlay.setKeepEnabled(this@ActionMacrosEditor, value) }
        }
        add(ActionUi.card(this, vertical = false).apply {
            foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
            isClickable = true
            setOnClickListener { keepSwitch.toggle() }
            addView(column().apply {
                addView(ActionUi.label(this@ActionMacrosEditor, getString(R.string.action_overlay_keep_setting), 13f))
                addView(ActionUi.caption(this@ActionMacrosEditor, getString(R.string.action_overlay_keep_setting_desc)).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(keepSwitch, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        })
        // AI 操作の「操作の軌跡を表示」と同じ描き方で、マクロの指を見せる (1.0.185)。
        val trailSwitch = com.zerotoship.z2term.edge.EdgeSettingsUi.switchOf(this, ActionRunOverlay.trailEnabled(this)).apply {
            contentDescription = getString(R.string.action_trail_setting)
            setOnCheckedChangeListener { _, value -> ActionRunOverlay.setTrailEnabled(this@ActionMacrosEditor, value) }
        }
        add(ActionUi.card(this, vertical = false).apply {
            foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
            isClickable = true
            setOnClickListener { trailSwitch.toggle() }
            addView(column().apply {
                addView(ActionUi.label(this@ActionMacrosEditor, getString(R.string.action_trail_setting), 13f))
                addView(ActionUi.caption(this@ActionMacrosEditor, getString(R.string.action_trail_setting_desc)).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(trailSwitch, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        })
        // Nothing here runs without it, so the permission stays above the list rather than under it.
        add(pill(R.string.action_edit_permission) {
            runCatching { AndroidActions.command(this, listOf("permission")) }.onFailure { message(it.message.orEmpty()) }
        })
        // Same words as the rules and snippets tabs; nothing to arrange with fewer than two.
        if (names.size < 2) arranging = false
        pills(*listOfNotNull(act(R.string.action_edit_history) { showHistory() }, act(R.string.action_edit_refresh) { refresh() },
            if (names.size >= 2) act(if (arranging) R.string.list_reorder_done else R.string.list_reorder, accent = arranging) {
                arranging = !arranging; render()
            } else null).toTypedArray())
        if (names.isEmpty()) add(note(R.string.action_edit_empty))
        // このマクロが呼ぶルーチン。木の上で自分の祖先に戻るもの (呼び合い) は出さない。
        fun children(savedName: String, path: String): List<String> {
            val ancestors = path.split('/').toSet()
            return calls[savedName].orEmpty().filter { it in routines && it in names && it !in ancestors }
        }
        fun macroRow(savedName: String, section: List<String>, depth: Int = 0, path: String = savedName) {
            val kids = if (arranging) emptyList() else children(savedName, path)
            val open = path in expanded
            // One bordered row per macro; the actions are glyphs at its right edge, as in the rules tab.
            val row = ActionUi.card(this, vertical = false).apply { setPadding(dp(if (kids.isEmpty()) 10 else 2), dp(2), dp(2), dp(2)) }
            // 呼んでいるルーチンがあるときだけ、左端に開閉の印を出す。名前を押しても開閉する (1.0.207。印だけでは小さくて押しにくい)。
            fun toggle() { if (!expanded.remove(path)) expanded += path; render() }
            if (kids.isNotEmpty()) row.addView(ActionUi.iconCell(this, if (open) "▾" else "▸", getString(R.string.action_edit_callees)) { toggle() })
            // A saved name is an identifier, so it is set like one.
            row.addView(ActionUi.label(this, savedName, 13f, ActionUi.textColor, ActionUi.medium).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, dp(8))
                if (kids.isNotEmpty()) {
                    foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
                    isClickable = true
                    contentDescription = savedName + ": " + getString(R.string.action_edit_callees)
                    setOnClickListener { toggle() }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            fun icon(glyph: String, label: Int, danger: Boolean = false, size: Float = 15f, action: () -> Unit) =
                row.addView(ActionUi.iconCell(this, glyph, getString(label), danger, size, action))
            if (arranging) {
                // 並べ替えは同じ区切り (普通のマクロ / ルーチン) の中だけ。
                val at = section.indexOf(savedName)
                // The end rows keep the cell so the arrows stay in one column.
                fun move(glyph: String, label: Int, neighbour: String?) = row.addView(
                    ActionUi.iconCell(this, glyph, getString(label)) { if (neighbour != null) moveSaved(savedName, neighbour) }.apply {
                        if (neighbour == null) { isEnabled = false; alpha = 0.3f }
                    })
                move("↑", R.string.action_edit_up, section.getOrNull(at - 1))
                move("↓", R.string.action_edit_down, section.getOrNull(at + 1))
                add(row)
                return
            }
            icon("▶", R.string.action_edit_run) { runSaved(savedName) }
            // No glyph for duplicate reads reliably in every font, so it keeps its word.
            icon(getString(R.string.action_edit_duplicate), R.string.action_edit_duplicate, size = 12f) {
                io({ store.readText(savedName) }) { if (!editing) openEditor("", null, it) }
            }
            icon("✎", R.string.action_edit_title) {
                io({ store.readText(savedName) }) { if (!editing) openEditor(savedName, it) }
            }
            // ルーチンは直接置かないので、置き場所を選ぶ印も出さない。
            if (savedName !in routines) icon(getString(R.string.action_place), R.string.action_place, size = 12f) {
                ActionMacroPlacement.show(this, savedName, ::showManaged)
            }
            icon("✕", R.string.action_edit_delete, danger = true) { deleteSaved(savedName) }
            add(row)
            if (depth > 0) (row.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.leftMargin = dp(16 * minOf(depth, 4))
            if (open) kids.forEach { macroRow(it, section, depth + 1, "$path/$it") }
        }
        val plain = names.filter { it !in routines }
        val routineNames = names.filter { it in routines }
        plain.forEach { macroRow(it, plain) }
        // 並べ替えの間は木にせず、ルーチンをまとめて並べる (区切りの中だけで動かす)。
        // ふだんは、普通のマクロからたどれないルーチンだけを下にまとめる (1.0.206)。
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque(plain)
        while (queue.isNotEmpty()) calls[queue.removeFirst()].orEmpty().filter { it in routines && reachable.add(it) }.forEach(queue::add)
        val loose = if (arranging) routineNames else routineNames.filter { it !in reachable }
        if (loose.isNotEmpty()) {
            val open = routinesOpen || arranging
            add(ActionUi.label(this, (if (open) "▾ " else "▸ ") +
                getString(if (arranging) R.string.action_routines else R.string.action_routines_unused, loose.size),
                13f, ActionUi.mutedColor, ActionUi.medium).apply {
                setPadding(dp(4), dp(10), dp(4), dp(6))
                foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
                isClickable = true
                setOnClickListener { routinesOpen = !routinesOpen; render() }
            })
            if (open) loose.forEach { macroRow(it, loose) }
        }
        add(note(R.string.action_edit_intro))
    }
    /** Saves at once, so leaving the tab mid-arrangement loses nothing. */
    private fun moveSaved(savedName: String, neighbour: String) {
        val order = names.toMutableList()
        val from = order.indexOf(savedName); val to = order.indexOf(neighbour)
        if (from < 0 || to < 0) return
        order[from] = neighbour; order[to] = savedName
        names = order; render()
        io({ store.reorder(order); store.names() }) { names = it; if (!editing) render() }
    }
    private fun openEditor(savedName: String, saved: String?, source: String = ActionEditorDocument.EMPTY) {
        editorScrollY = 0; renderedEditing = false
        editing = true; name = savedName; originalName = savedName.takeIf { saved != null }
        baseline = saved; initial = saved ?: source; raw = initial; textMode = false
        stepDraft = null; pickedScreen = null; render()
    }
    private fun addControl(branch: Boolean) {
        try {
            raw = ActionEditorDocument(raw).appendControl(branch); textMode = true; render()
        } catch (e: Exception) { message(e.message ?: getString(R.string.action_state_failed)) }
    }
    /** 選んだ行 (選んでいなければカーソルの行) をコメントにする／戻す。選んだ範囲は行ごとに付け直す。 */
    private fun toggleComment(field: android.widget.EditText) {
        val before = field.text.toString()
        val updated = ActionEditorDocument(before).toggleComment(field.selectionStart, field.selectionEnd)
        if (updated == before) return
        // 変わった分だけ前後を残して差し替えるので、取り消しやカーソル位置が全文の置き換えより崩れにくい。
        val head = before.commonPrefixWith(updated).length
        val tail = minOf(before.length, updated.length) - head
        val suffix = before.takeLast(tail).commonSuffixWith(updated.takeLast(tail)).length
        val cursor = field.selectionEnd
        field.text.replace(head, before.length - suffix, updated, head, updated.length - suffix)
        val moved = if (cursor <= head) cursor else cursor + updated.length - before.length
        field.setSelection(moved.coerceIn(0, field.text.length))
    }
    private fun renderEditor() {
        val structure = runCatching { ActionBlockDocument(raw) }
        if (structure.isFailure) textMode = true
        ActionUi.labeled(this, body, getString(R.string.action_edit_name), ActionUi.field(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(64))
            contentDescription = getString(R.string.action_edit_name); setText(name)
            doAfterTextChanged { name = it.toString() }
        })
        pills(act(R.string.action_edit_save, accent = true) { save() },
            act(if (textMode) R.string.action_edit_form else R.string.action_edit_text) { textMode = !textMode; render() },
            act(R.string.action_edit_copy_text) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(name, raw))
                message(getString(R.string.action_edit_copied))
            })
        val sourceField = if (!textMode) null else ActionUi.field(this, lines = 12).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(ActionDefinition.MAX_BYTES))
            contentDescription = getString(R.string.action_edit_text)
            setText(raw); doAfterTextChanged { raw = it.toString() }
            ActionSyntaxColors.attach(this)
        }
        if (sourceField != null) pills(
            act(R.string.action_edit_add_repeat) { addControl(false) },
            act(R.string.action_edit_add_branch) { addControl(true) },
            act(R.string.action_edit_toggle_comment) { toggleComment(sourceField) })
        if (!textMode && ActionEditorDocument(raw).hasBlocks) add(note(R.string.action_edit_blocks_hint))
        // A structure error stops the form from opening at all, so it is stated in the danger colour.
        structure.exceptionOrNull()?.let {
            add(ActionUi.note(this, getString(R.string.action_block_invalid, it.message.orEmpty()),
                ActionUi.dangerColor, ActionUi.dangerColor))
        }
        if (sourceField != null) {
            add(sourceField)
        } else {
            val document = ActionEditorDocument(raw)
            add(setting(R.string.action_edit_timeout, document.header("timeout") ?: "30") {
                editHeader("timeout", R.string.action_edit_timeout, document.header("timeout") ?: "30")
            })
            add(setting(R.string.action_edit_screen, document.header("screen") ?: "—") {
                editHeader("screen", R.string.action_edit_screen, document.header("screen") ?: "current")
            })
            // 強さを 0〜20 で入れる (0 はなし、on は 5)。
            val human = document.header("human") ?: "off"
            add(setting(R.string.action_edit_human, human) {
                editHeader("human", R.string.action_edit_human, human.takeUnless { it == "off" } ?: "0")
            })
            // 何を揺らすか (1.0.203)。人らしい操作が入っているときだけ意味があるので、そのときだけ出す。
            if (human != "off" && human != "0") {
                val parts = runCatching { ActionHumanize.Parts.parse(document.header("jitter")) }.getOrDefault(ActionHumanize.Parts.ALL)
                add(setting(R.string.action_edit_jitter, jitterLabel(parts)) { editJitter(parts) })
            }
            // 呼び出し専用 (1.0.203)。実行の帯やエッジパネルの選択肢に出さない。
            val routineSwitch = com.zerotoship.z2term.edge.EdgeSettingsUi.switchOf(this, document.header("routine") == "on").apply {
                contentDescription = getString(R.string.action_edit_routine)
                setOnCheckedChangeListener { _, value ->
                    try { raw = ActionEditorDocument(raw).withHeader("routine", if (value) "on" else "off"); render() }
                    catch (e: Exception) { failure(e) }
                }
            }
            add(ActionUi.card(this, vertical = false).apply {
                foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
                isClickable = true
                setOnClickListener { routineSwitch.toggle() }
                addView(column().apply {
                    addView(ActionUi.label(this@ActionMacrosEditor, getString(R.string.action_edit_routine), 13f))
                    addView(ActionUi.caption(this@ActionMacrosEditor, getString(R.string.action_edit_routine_desc)).apply {
                        setPadding(0, dp(2), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(routineSwitch, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            })
            // 止めた・失敗した・時間切れのときに流すシェルの命令 (1.0.205)。
            val cleanup = document.header("cleanup")
            add(setting(R.string.action_edit_cleanup, cleanup?.takeIf { it.isNotBlank() } ?: "—") {
                editHeader("cleanup", R.string.action_edit_cleanup, cleanup.orEmpty())
            })
            ActionBlockEditor.render(this, body, structure.getOrThrow(),
                edit = { line, source -> stepLine = line; stepGroup = -1; stepDraft = source; pickedScreen = null; showStep() },
                add = { group, source -> stepLine = -1; stepGroup = group; stepDraft = source; pickedScreen = null; showStep() },
                change = { source -> ActionBlockDocument(source); raw = source; render() },
                show = { showManaged(it) }, failure = { failure(it) })
        }
        add(note(R.string.action_edit_save_hint))
    }
    /**
     * A setting whose value is edited in a dialog: name above, current value below, ✎ at the edge.
     * Baking the value into a button's label made the label change length every time it was set.
     */
    private fun setting(label: Int, value: String, action: () -> Unit): View {
        val row = ActionUi.card(this, vertical = false)
        row.setPadding(dp(10), dp(8), dp(2), dp(8))
        row.foreground = ActionUi.pressable(this, 8)
        row.isClickable = true
        row.contentDescription = getString(label) + ": " + value
        row.setOnClickListener { action() }
        row.addView(column().apply {
            addView(ActionUi.caption(this@ActionMacrosEditor, getString(label)))
            addView(ActionUi.label(this@ActionMacrosEditor, value, 13f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ActionUi.label(this, "✎", 15f, ActionUi.mutedColor).apply { setPadding(dp(8), 0, dp(8), 0) })
        return row
    }
    private fun jitterLabel(parts: ActionHumanize.Parts): String =
        listOfNotNull(getString(R.string.action_jitter_speed).takeIf { parts.speed }, getString(R.string.action_jitter_path).takeIf { parts.path },
            getString(R.string.action_jitter_aim).takeIf { parts.aim }).joinToString(" · ").ifEmpty { getString(R.string.action_jitter_none) }
    /** 速さ・線・到達点を 1 つずつ入り切りする。3 つとも入りなら見出しを書かない (既定と同じ)。 */
    private fun editJitter(initial: ActionHumanize.Parts) {
        var parts = initial
        val content = column()
        fun row(label: Int, detail: Int, checked: Boolean, changed: (Boolean) -> Unit) {
            val switch = com.zerotoship.z2term.edge.EdgeSettingsUi.switchOf(this, checked).apply {
                contentDescription = getString(label)
                setOnCheckedChangeListener { _, value -> changed(value) }
            }
            content.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                foreground = ActionUi.pressable(this@ActionMacrosEditor, 8)
                isClickable = true
                setOnClickListener { switch.toggle() }
                addView(column().apply {
                    addView(ActionUi.label(this@ActionMacrosEditor, getString(label), 13f))
                    addView(ActionUi.caption(this@ActionMacrosEditor, getString(detail)).apply { setPadding(0, dp(2), 0, 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(switch, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        row(R.string.action_jitter_speed, R.string.action_jitter_speed_desc, parts.speed) { parts = parts.copy(speed = it) }
        row(R.string.action_jitter_path, R.string.action_jitter_path_desc, parts.path) { parts = parts.copy(path = it) }
        row(R.string.action_jitter_aim, R.string.action_jitter_aim_desc, parts.aim) { parts = parts.copy(aim = it) }
        content.addView(ActionUi.note(this, getString(R.string.action_jitter_hint)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        showManaged(ActionUi.modal(this, getString(R.string.action_edit_jitter), content = content, confirm = getString(R.string.action_edit_apply)) { dialog ->
            try {
                raw = if (parts == ActionHumanize.Parts.ALL) ActionEditorDocument(raw).withoutHeader("jitter")
                    else ActionEditorDocument(raw).withHeader("jitter", parts.toString())
                render(); dialog.dismiss()
            } catch (e: Exception) { failure(e) }
        })
    }
    private fun editHeader(key: String, title: Int, initial: String) {
        val field = ActionUi.field(this).apply {
            setText(initial)
            contentDescription = getString(title)
            if (key == "screen") hint = getString(R.string.action_edit_screen_hint)
            if (key == "cleanup") {
                hint = getString(R.string.action_edit_cleanup_example)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            if (key == "human") {
                hint = getString(R.string.action_edit_human_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
        }
        // 後片付けの命令は、何を流すのかの説明を欄の下に添える。
        val content = if (key != "cleanup") field else column().apply {
            addView(field, LinearLayout.LayoutParams(-1, -2))
            addView(ActionUi.note(this@ActionMacrosEditor, getString(R.string.action_edit_cleanup_hint)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        showManaged(ActionUi.modal(this, getString(title), content = content, confirm = getString(R.string.action_edit_apply)) { dialog ->
            try {
                raw = if (key == "cleanup" && field.text.isBlank()) ActionEditorDocument(raw).withoutHeader("cleanup")
                    else if (key == "screen" && field.text.isBlank()) ActionEditorDocument(raw).withoutScreen()
                    else ActionEditorDocument(raw).withHeader(key, field.text.toString())
                render(); dialog.dismiss()
            } catch (e: Exception) { failure(e) }
        })
    }
    private fun insertionGroup(): ActionBlockDocument.Group = ActionBlockDocument(raw).let {
        if (stepGroup < 0) it.root else it.group(stepGroup)
    }
    private fun showStep() {
        if (AppLock.state.value != AppLock.State.UNLOCKED) return
        val source = stepDraft ?: return
        if (source == "@record") {
            chooseRecording()
            return
        }
        if (!ActionStepEditor.supports(source)) {
            stepDraft = null; textMode = true; render(); return
        }
        val geometry = runCatching { ActionDefinition.parse(raw, ActionRuntime.screen(this)).screen }.getOrNull()
            ?: ActionRuntime.screen(this)
        stepDialog = ActionStepEditor.show(this, source, geometry, { apps }, macros = { names },
            draft = { stepDraft = it },
            apply = {
                val verb = it.trim().split(Regex("\\s+")).first()
                val sourceLine = it.trimStart()
                val source = if (stepLine < 0 && verb in listOf("repeat", "if", "try")) sourceLine + "\n  wait 800\nend" else sourceLine
                var updated = if (stepLine < 0) ActionBlockDocument(raw).insert(insertionGroup().start, source)
                    else ActionEditorDocument(raw).replace(stepLine, source)
                val tapUi = sourceLine.split(Regex("\\s+")).getOrNull(1) == "ui" && verb == "tap"
                if (tapUi || verb in listOf("call", "repeat", "if", "try", "set", "add", "read-colors", "click", "long-click", "wait-ui", "wait-until", "wait-while", "run-output", "type", "enter", "touch", "swipe-path", "swipe-two-path"))
                    updated = ActionEditorDocument(updated).withHeader("version", "2")
                // 色の条件は画面の点を読むので、座標の手順と同じく画面の寸法が要る。
                val colorCondition = runCatching {
                    val code = ActionDefinition.code(sourceLine)
                    val condition = when {
                        verb == "repeat" -> Regex("\\suntil\\s+(.+)").find(code)?.groupValues?.get(1)
                        verb in ActionDefinition.CONDITION_VERBS -> code.split(Regex("\\s+"), limit = if (verb == "if") 2 else 3).last()
                        else -> null
                    }
                    condition != null && ActionCondition.parse(condition).terms.any { it is ActionCondition.Term.Color }
                }.getOrDefault(false)
                if ((colorCondition || verb == "read-colors") && ActionEditorDocument(updated).header("screen") == null)
                    updated = ActionEditorDocument(updated).withHeader("screen", geometry.toString())
                if (!tapUi && verb in listOf("tap", "long-press", "swipe", "scroll", "double-tap", "pinch-in", "pinch-out", "swipe-two", "touch", "swipe-path", "swipe-two-path", "drag") && ActionEditorDocument(updated).header("screen") == null)
                    updated = ActionEditorDocument(updated).withHeader("screen", geometry.toString())
                pickedScreen?.let { screen -> updated = ActionEditorDocument(updated).withHeader("screen", screen) }
                raw = updated; stepDraft = null; pickedScreen = null; render()
            },
            cancel = { stepDraft = null; pickedScreen = null },
            pickElement = { line ->
                val target = ActionEditorDocument(raw).targetBefore(if (stepLine < 0) insertionGroup().end else stepLine)
                    ?: ActionDefinition.CURRENT_TARGET
                val request = UUID.randomUUID().toString()
                AndroidActions.pickUiElements(request, target)
                ActionCoordinatePicker.returnTo(request, activity)
                stepDraft = line; pickRequest = request
                if (target == ActionDefinition.CURRENT_TARGET) moveBehindForPick(request)
            },
            pick = { line ->
                val document = ActionEditorDocument(raw)
                val screen = ActionRuntime.screen(this)
                val savedScreen = document.header("screen")
                check(savedScreen == null || savedScreen == "current" || savedScreen == screen.toString()) { getString(R.string.action_pick_screen) }
                val request = UUID.randomUUID().toString()
                ActionEditorDocument.singleLine(line)
                // Validate the form values without requiring an execution target in the draft.
                val verb = line.trim().split(Regex("\\s+")).first()
                // drag は点を取る前なので点が足りない。単位と 1 区間の時間だけを確かめる。
                if (verb == "drag") {
                    val words = line.trim().split(Regex("\\s+"))
                    require(words.getOrNull(1) in listOf("px", "percent")) { "Coordinate unit must be px or percent" }
                    require(words.getOrNull(2)?.toLongOrNull() in 20L..1000L) { "Milliseconds per segment must be 20..1000" }
                } else if (verb !in listOf("swipe-path", "swipe-two-path"))
                    ActionDefinition.parse("version=2\nscreen=$screen\ntarget org.example.target\n$line")
                AndroidActions.pickCoordinates(request, verb !in listOf("tap", "long-press", "double-tap"),
                    fingers = if (verb in listOf("pinch-in", "pinch-out", "swipe-two", "swipe-two-path")) 2 else 1,
                    freehand = verb in listOf("swipe-path", "swipe-two-path"), multi = verb == "drag")
                ActionCoordinatePicker.returnTo(request, activity)
                stepDraft = line; pickRequest = request
                moveBehindForPick(request)
            })
    }
    private fun chooseRecording() {
        stepDialog = ActionUi.choices(this, getString(R.string.action_record_title),
            listOf(getString(R.string.action_record_overlay), getString(R.string.action_record_live))) { mode ->
            try {
                val screen = ActionRuntime.screen(this)
                val saved = ActionEditorDocument(raw).header("screen")
                check(saved == null || saved == "current" || saved == screen.toString()) { getString(R.string.action_pick_screen) }
                val request = UUID.randomUUID().toString()
                AndroidActions.recordTouches(request, live = mode == 1)
                ActionCoordinatePicker.returnTo(request, activity)
                pickRequest = request
                moveBehindForPick(request)
            } catch (e: Exception) { stepDraft = null; failure(e) }
        }.apply {
            setOnCancelListener { stepDraft = null }
            show()
        }
    }

    private fun moveBehindForPick(request: String) {
        try {
            // The embedded editor belongs to the tools task; the standalone host has its own.
            if (!embedded) com.zerotoship.z2term.MainActivity.moveToBackground()
            check(activity.moveTaskToBack(true)) { getString(R.string.action_pick_background_failed) }
        } catch (e: Exception) {
            ActionCoordinatePicker.cancel(); ActionCoordinatePicker.consume(request); pickRequest = null
            throw e
        }
    }
    private fun save() {
        if (busy) return
        val savedName = name.trim()
        val source = raw
        val expected = if (savedName == originalName) baseline else null
        val screen = try { ActionRuntime.screen(this) } catch (e: Exception) { failure(e); return }
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.saveEdited(savedName, source, screen, expected) }
                originalName = savedName; baseline = saved.text; initial = saved.text
                if (raw == source && name.trim() == savedName) { editing = false; stepDraft = null; refresh() }
                message(getString(R.string.action_edit_saved))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
            finally { busy = false }
        }
    }
    fun leave() {
        if (!editing) { if (!busy) onClose(); return }
        requestLeave { refresh() }
    }

    /** Tab switches, dismissals and the editor's Back button all protect the same draft. */
    fun requestLeave(after: () -> Unit) {
        if (busy) return
        if (!editing) { after(); return }
        val dirty = raw != initial || (originalName != null && name != originalName) ||
            (originalName == null && (name.isNotEmpty() || raw != ActionEditorDocument.EMPTY)) || stepDraft != null
        fun closeEditor() {
            editing = false; stepDraft = null; pickedScreen = null
            stepDialog?.dismiss()
            after()
        }
        if (!dirty) closeEditor() else showManaged(ActionUi.modal(this, getString(R.string.edge_discard_title),
            message = getString(R.string.edge_discard_message), confirm = getString(R.string.edge_discard),
            confirmColor = ActionUi.dangerColor, dismiss = getString(R.string.edge_keep_editing)) {
            it.dismiss(); closeEditor()
        })
    }
    private fun deleteSaved(savedName: String) = io({ store.readText(savedName) }) { expected ->
        if (editing) return@io
        showManaged(ActionUi.modal(this, getString(R.string.action_edit_delete),
            message = getString(R.string.action_edit_delete_confirm, savedName),
            confirm = getString(R.string.action_edit_delete), confirmColor = ActionUi.dangerColor) {
            it.dismiss(); io({ store.deleteEdited(savedName, expected) }) { refresh() }
        })
    }
    private fun runSaved(savedName: String) {
        if (busy) return
        busy = true
        try {
            ActionRuntime.start(this, savedName, background = {
                if (!embedded) com.zerotoship.z2term.MainActivity.moveToBackground()
                check(activity.moveTaskToBack(true)) { getString(R.string.action_run_background_failed) }
            })
            updateStatus()
        }
        catch (e: Exception) { failure(e) }
        finally { busy = false }
    }
    private fun updateStatus() {
        if (statusView == null) return
        val status = ActionRuntime.status()
        statusView?.text = formatStatus(status)
        val running = status.optString("state") in listOf("queued", "running", "paused")
        stopButton?.isEnabled = running
        stopButton?.tag = status.optString("id").takeIf { running }
        val paused = status.optString("state") == "paused"
        pauseButton?.text = getString(if (paused) R.string.action_macro_resume else R.string.action_macro_pause)
        pauseButton?.isEnabled = running && status.optString("state") != "queued"
        pauseButton?.tag = status.optString("id").takeIf { running }
    }
    private fun formatStatus(status: JSONObject): String {
        val state = when (status.optString("state")) {
            "queued" -> R.string.action_state_queued
            "running", "step", "resumed" -> R.string.action_state_running
            "paused" -> R.string.action_state_paused
            "branch" -> if (status.optString("detail") == "true") R.string.action_branch_true else R.string.action_branch_false
            "completed" -> R.string.action_state_completed
            "cancelled" -> R.string.action_state_cancelled
            "failed" -> R.string.action_state_failed
            "caught" -> R.string.action_state_caught
            "timeout" -> R.string.action_state_timeout
            else -> R.string.action_state_idle
        }
        val result = if (status.has("name") && status.isNull("total")) getString(R.string.action_edit_dynamic_status,
            status.optString("name"), getString(state), status.optInt("step"))
        else if (status.has("name")) getString(R.string.action_edit_status, status.optString("name"),
            getString(state), status.optInt("step"), status.optInt("total")) else getString(state)
        val error = if (status.has("error") && !status.isNull("error")) status.optString("error")
            else if (status.has("detail") && !status.isNull("detail") && status.optString("state") !in listOf("step", "branch")) status.optString("detail") else ""
        val location = if (status.optString("path").isNotEmpty()) "\n" + getString(R.string.action_edit_location,
            status.optString("action_macro"), status.optString("path"), status.optInt("iteration", 1)) else ""
        return result + location + if (error.isNotEmpty()) "\n$error" else ""
    }
    private fun showHistory() {
        val history = runCatching { JSONArray(ActionRuntime.history(this)) }.getOrElse { message(it.message.orEmpty()); return }
        val records = (0 until history.length()).map { history.getJSONObject(it) }
            .filter { it.optString("state") != "step" }.takeLast(32).asReversed()
        val contents = column()
        if (records.isEmpty()) contents.addView(ActionUi.label(this, getString(R.string.action_edit_no_history), 13f, ActionUi.mutedColor))
        records.forEach { record ->
            val entry = column()
            entry.addView(ActionUi.caption(this, DateFormat.getDateTimeInstance().format(Date(record.optLong("time")))))
            entry.addView(ActionUi.label(this, formatStatus(record), 12f).apply { setLineSpacing(dp(2).toFloat(), 1f) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            ActionUi.add(contents, entry)
        }
        showManaged(ActionUi.modal(this, getString(R.string.action_edit_history), content = contents,
            dismiss = getString(android.R.string.ok)))
    }
}
