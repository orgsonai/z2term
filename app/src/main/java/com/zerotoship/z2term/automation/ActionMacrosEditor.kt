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
    val view = FrameLayout(this)
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
    private var apps = emptyList<AppCatalog.LaunchableApp>()
    private lateinit var body: LinearLayout
    private var statusView: TextView? = null
    private var stopButton: TextView? = null
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
            if (result?.selector != null) {
                val words = stepDraft.orEmpty().trim().split(Regex("\\s+"), limit = 3)
                val prefix = if (words.firstOrNull() == "wait-ui" && words.size >= 2) "wait-ui " + words[1]
                    else words.firstOrNull() ?: "click"
                stepDraft = prefix + " " + result.selector
            } else if (result?.points != null) {
                val words = stepDraft.orEmpty().trim().split(Regex("\\s+")).toMutableList()
                if (words.size >= 4) {
                    val p = result.points
                    fun coordinate(value: Float, extent: Int): String {
                        val converted = if (words[1] == "percent") value * 100 / (extent - 1).coerceAtLeast(1) else value
                        return java.math.BigDecimal(converted.toString()).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    }
                    words[2] = coordinate(p[0], result.screen.width); words[3] = coordinate(p[1], result.screen.height)
                    if (words[0] == "swipe" && words.size == 7) {
                        words[4] = coordinate(p[2], result.screen.width); words[5] = coordinate(p[3], result.screen.height)
                    }
                    stepDraft = words.joinToString(" "); pickedScreen = result.screen.toString()
                }
            } else message(result?.error ?: getString(R.string.action_pick_cancelled))
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
        // A lock screen has no editor scroll position to save over the retained draft.
        renderedEditing = editing && AppLock.state.value == AppLock.State.UNLOCKED
        // The body of every command-sheet tab: 16dp sides, 10dp above, 24dp below, 10dp between items.
        body = column().apply { setPadding(dp(ActionUi.GUTTER), dp(10), dp(ActionUi.GUTTER), dp(24)) }
        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ActionUi.groundColor)
            addView(body)
            scrollView = this
        }
        view.removeAllViews()
        view.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        statusView = null; stopButton = null
        val unlocked = AppLock.state.value == AppLock.State.UNLOCKED
        header(unlocked)
        if (!unlocked) {
            stepDialog?.dismiss(); dialogs.toList().forEach { it.dismiss() }
            if (AppLock.state.value == AppLock.State.LOCKED) add(pill(R.string.lock_prompt_title, accent = true) { promptUnlock() })
        } else if (editing) renderEditor() else renderList()
        if (renderedEditing) scrollView?.post { scrollView?.scrollTo(0, editorScrollY) }
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
    private fun refresh() = io({ store.names() }) { names = it; if (!editing) render() }
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
        }.also { running.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) }) }
        add(running)
        updateStatus()
        // Nothing here runs without it, so the permission stays above the list rather than under it.
        add(pill(R.string.action_edit_permission) {
            runCatching { AndroidActions.command(this, listOf("permission")) }.onFailure { message(it.message.orEmpty()) }
        })
        pills(act(R.string.action_edit_history) { showHistory() }, act(R.string.action_edit_refresh) { refresh() })
        if (names.isEmpty()) add(note(R.string.action_edit_empty))
        names.forEach { savedName ->
            // One bordered row per macro; the actions are glyphs at its right edge, as in the rules tab.
            val row = ActionUi.card(this, vertical = false).apply { setPadding(dp(10), dp(2), dp(2), dp(2)) }
            // A saved name is an identifier, so it is set like one.
            row.addView(ActionUi.label(this, savedName, 13f, ActionUi.textColor, ActionUi.medium).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, dp(8))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            fun icon(glyph: String, label: Int, danger: Boolean = false, size: Float = 15f, action: () -> Unit) =
                row.addView(ActionUi.iconCell(this, glyph, getString(label), danger, size, action))
            icon("▶", R.string.action_edit_run) { runSaved(savedName) }
            // No glyph for duplicate reads reliably in every font, so it keeps its word.
            icon(getString(R.string.action_edit_duplicate), R.string.action_edit_duplicate, size = 12f) {
                io({ store.readText(savedName) }) { if (!editing) openEditor("", null, it) }
            }
            icon("✎", R.string.action_edit_title) {
                io({ store.readText(savedName) }) { if (!editing) openEditor(savedName, it) }
            }
            icon("✕", R.string.action_edit_delete, danger = true) { deleteSaved(savedName) }
            add(row)
        }
        add(note(R.string.action_edit_intro))
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
        if (textMode) pills(
            act(R.string.action_edit_add_repeat) { addControl(false) },
            act(R.string.action_edit_add_branch) { addControl(true) })
        if (!textMode && ActionEditorDocument(raw).hasBlocks) add(note(R.string.action_edit_blocks_hint))
        // A structure error stops the form from opening at all, so it is stated in the danger colour.
        structure.exceptionOrNull()?.let {
            add(ActionUi.note(this, getString(R.string.action_block_invalid, it.message.orEmpty()),
                ActionUi.dangerColor, ActionUi.dangerColor))
        }
        if (textMode) {
            add(ActionUi.field(this, lines = 12).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                filters = arrayOf(InputFilter.LengthFilter(ActionDefinition.MAX_BYTES))
                contentDescription = getString(R.string.action_edit_text)
                setText(raw); doAfterTextChanged { raw = it.toString() }
            })
        } else {
            val document = ActionEditorDocument(raw)
            add(setting(R.string.action_edit_timeout, document.header("timeout") ?: "30") {
                editHeader("timeout", R.string.action_edit_timeout, document.header("timeout") ?: "30")
            })
            add(setting(R.string.action_edit_screen, document.header("screen") ?: "—") {
                editHeader("screen", R.string.action_edit_screen, document.header("screen") ?: "current")
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
    private fun editHeader(key: String, title: Int, initial: String) {
        val field = ActionUi.field(this).apply {
            setText(initial)
            contentDescription = getString(title)
            if (key == "screen") hint = getString(R.string.action_edit_screen_hint)
        }
        showManaged(ActionUi.modal(this, getString(title), content = field, confirm = getString(R.string.action_edit_apply)) { dialog ->
            try {
                raw = if (key == "screen" && field.text.isBlank()) ActionEditorDocument(raw).withoutScreen()
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
                val source = if (stepLine < 0 && verb in listOf("repeat", "if")) sourceLine + "\n  wait 800\nend" else sourceLine
                var updated = if (stepLine < 0) ActionBlockDocument(raw).insert(insertionGroup().start, source)
                    else ActionEditorDocument(raw).replace(stepLine, source)
                if (verb in listOf("call", "repeat", "if", "click", "long-click", "wait-ui"))
                    updated = ActionEditorDocument(updated).withHeader("version", "2")
                if (verb in listOf("tap", "long-press", "swipe", "scroll") && ActionEditorDocument(updated).header("screen") == null)
                    updated = ActionEditorDocument(updated).withHeader("screen", geometry.toString())
                pickedScreen?.let { screen -> updated = ActionEditorDocument(updated).withHeader("screen", screen) }
                raw = updated; stepDraft = null; pickedScreen = null; render()
            },
            cancel = { stepDraft = null; pickedScreen = null },
            pickElement = { line ->
                val target = ActionEditorDocument(raw).targetBefore(if (stepLine < 0) insertionGroup().end else stepLine)
                    ?: error(getString(R.string.action_pick_target))
                val request = UUID.randomUUID().toString()
                AndroidActions.pickUiElements(request, target)
                ActionCoordinatePicker.returnTo(request, activity)
                stepDraft = line; pickRequest = request
            },
            pick = { line ->
                val document = ActionEditorDocument(raw)
                val screen = ActionRuntime.screen(this)
                val savedScreen = document.header("screen")
                check(savedScreen == null || savedScreen == "current" || savedScreen == screen.toString()) { getString(R.string.action_pick_screen) }
                val request = UUID.randomUUID().toString()
                ActionEditorDocument.singleLine(line)
                // Validate the form values without requiring an execution target in the draft.
                ActionDefinition.parse("version=1\nscreen=$screen\ntarget org.example.target\n$line")
                AndroidActions.pickCoordinates(request, line.trim().split(Regex("\\s+")).first() == "swipe")
                ActionCoordinatePicker.returnTo(request, activity)
                stepDraft = line; pickRequest = request
                try {
                    // The embedded editor belongs to the tools task; the standalone host has its own.
                    if (!embedded) com.zerotoship.z2term.MainActivity.moveToBackground()
                    check(activity.moveTaskToBack(true)) { getString(R.string.action_pick_background_failed) }
                } catch (e: Exception) {
                    ActionCoordinatePicker.cancel(); ActionCoordinatePicker.consume(request); pickRequest = null
                    throw e
                }
            })
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
        try { ActionRuntime.start(this, savedName); updateStatus() }
        catch (e: Exception) { failure(e) }
        finally { busy = false }
    }
    private fun updateStatus() {
        if (statusView == null) return
        val status = ActionRuntime.status()
        statusView?.text = formatStatus(status)
        val running = status.optString("state") in listOf("queued", "running")
        stopButton?.isEnabled = running
        stopButton?.tag = status.optString("id").takeIf { running }
    }
    private fun formatStatus(status: JSONObject): String {
        val state = when (status.optString("state")) {
            "queued" -> R.string.action_state_queued
            "running", "step" -> R.string.action_state_running
            "branch" -> if (status.optString("detail") == "true") R.string.action_branch_true else R.string.action_branch_false
            "completed" -> R.string.action_state_completed
            "cancelled" -> R.string.action_state_cancelled
            "failed" -> R.string.action_state_failed
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
