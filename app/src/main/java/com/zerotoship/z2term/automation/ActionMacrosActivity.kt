package com.zerotoship.z2term.automation

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.collect
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.AppCatalog
import com.zerotoship.z2term.edge.EdgeSettingsUi
import com.zerotoship.z2term.settings.LocaleHelper
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** GUI and CLI share files, validation and execution; drafts belong to this activity. */
class ActionMacrosActivity : ComponentActivity() {
    private var editing = false
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
    private var stepDialog: AlertDialog? = null
    private var names = emptyList<String>()
    private var apps = emptyList<AppCatalog.LaunchableApp>()
    private lateinit var body: LinearLayout
    private var statusView: TextView? = null
    private var stopButton: Button? = null
    private var statusJob: Job? = null
    private val dialogs = mutableListOf<AlertDialog>()
    private var scrollView: ScrollView? = null
    private var renderedEditing = false
    private var editorScrollY = 0
    private val store get() = ActionRuntime.store(this)

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leave() }
        })
        render()
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppLock.state.collect { state ->
                    render()
                    if (state == AppLock.State.LOCKED && !unlockFailed) promptUnlock()
                    if (state == AppLock.State.UNLOCKED && stepDraft != null && pickRequest == null &&
                        stepDialog?.isShowing != true) showStep()
                }
            }
        }
        io({ AppCatalog.launchableApps(this) }) { apps = it }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onEnterForeground()
        render()
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) { AppLock.onLeaveForeground(); unlockFailed = false }
    }
    private fun promptUnlock() {
        if (unlockPromptShowing) return
        unlockPromptShowing = true; unlockFailed = false
        AppLock.authenticate(this, getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle)) {
            unlockPromptShowing = false
            if (it) AppLock.unlock() else unlockFailed = true
        }
    }
    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!editing) refresh()
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
        statusJob = lifecycleScope.launch { while (true) { updateStatus(); delay(500) } }
    }
    override fun onPause() {
        statusJob?.cancel(); statusJob = null
        if (AppLock.isEnabledNow()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("editorScrollY", scrollView?.scrollY ?: editorScrollY)
        outState.putBoolean("editing", editing); outState.putString("name", name)
        outState.putString("originalName", originalName); outState.putString("baseline", baseline)
        outState.putString("initial", initial); outState.putString("raw", raw); outState.putBoolean("textMode", textMode)
        outState.putInt("stepLine", stepLine); outState.putString("stepDraft", stepDraft)
        outState.putInt("stepGroup", stepGroup)
        outState.putString("pickRequest", pickRequest); outState.putString("pickedScreen", pickedScreen)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        stepDialog?.dismiss(); dialogs.toList().forEach { it.dismiss() }
        super.onDestroy()
    }
    private fun showManaged(dialog: AlertDialog) {
        dialogs += dialog
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }
    private fun AlertDialog.Builder.showManaged() { showManaged(create()) }

    private fun <T> io(work: () -> T, complete: (T) -> Unit) {
        lifecycleScope.launch {
            try { complete(withContext(Dispatchers.IO) { work() }) } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
        }
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun failure(error: Exception) = message(if (error is ActionStore.EditConflict)
        getString(R.string.action_edit_conflict) else error.message ?: getString(R.string.action_edit_error))
    private fun dp(value: Int) = EdgeSettingsUi.dp(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun note(text: String) = EdgeSettingsUi.note(this, text)
    private fun button(label: Int, kind: EdgeSettingsUi.Kind = EdgeSettingsUi.Kind.OUTLINE, action: () -> Unit) =
        EdgeSettingsUi.button(this, getString(label), kind, action).apply {
            // The form view cannot open a document whose block structure does not parse.
            if (label == R.string.action_edit_form && runCatching { ActionBlockDocument(raw) }.isFailure) isEnabled = false
        }
    private class Choice(val label: Int, val kind: EdgeSettingsUi.Kind, val run: () -> Unit)
    private fun act(label: Int, kind: EdgeSettingsUi.Kind = EdgeSettingsUi.Kind.OUTLINE, run: () -> Unit) =
        Choice(label, kind, run)
    /** One line of equal actions, gutter to gutter. */
    private fun buttons(parent: LinearLayout, vararg actions: Choice) {
        parent.addView(EdgeSettingsUi.row(this).apply {
            setPadding(dp(EdgeSettingsUi.GUTTER), dp(6), dp(EdgeSettingsUi.GUTTER), dp(6))
            actions.forEachIndexed { index, choice ->
                addView(button(choice.label, choice.kind, choice.run), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                })
            }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun gutter(view: android.view.View, top: Int = 8, bottom: Int = 8) =
        body.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(EdgeSettingsUi.GUTTER), dp(top), dp(EdgeSettingsUi.GUTTER), dp(bottom))
        })
    private fun render() {
        if (editing && renderedEditing && scrollView?.isLaidOut == true) editorScrollY = scrollView?.scrollY ?: editorScrollY
        renderedEditing = editing
        val root = column().apply { setBackgroundColor(EdgeSettingsUi.canvas(this@ActionMacrosActivity)) }
        val toolbar = EdgeSettingsUi.row(this).apply {
            setPadding(dp(EdgeSettingsUi.GUTTER), dp(10), dp(10), dp(10))
        }
        toolbar.addView(EdgeSettingsUi.title(this,
            getString(if (editing) R.string.action_edit_title else R.string.action_macro_title)),
            LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button(R.string.action_edit_close) { leave() },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        root.addView(toolbar); root.addView(EdgeSettingsUi.hairline(this))
        body = column()
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body); scrollView = this }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
        statusView = null; stopButton = null
        if (AppLock.state.value != AppLock.State.UNLOCKED) {
            stepDialog?.dismiss(); dialogs.toList().forEach { it.dismiss() }
            if (AppLock.state.value == AppLock.State.LOCKED)
                gutter(button(R.string.lock_prompt_title, EdgeSettingsUi.Kind.PRIMARY) { promptUnlock() }, top = 16)
        } else if (editing) renderEditor() else renderList()
        if (editing) scrollView?.post { scrollView?.scrollTo(0, editorScrollY) }
    }
    /**
     * Platform dialogs keep their own chrome, but their content is ours: give it this app's ground
     * so a light system dialog never wraps text coloured for a dark theme (or the other way round).
     */
    private fun dialogBody(content: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(EdgeSettingsUi.canvas(this@ActionMacrosActivity))
        setPadding(dp(EdgeSettingsUi.GUTTER), dp(12), dp(EdgeSettingsUi.GUTTER), dp(12))
        addView(content, LinearLayout.LayoutParams(-1, -2))
    }
    private fun refresh() = io({ store.names() }) { names = it; if (!editing) render() }
    private fun renderList() {
        buttons(body, act(R.string.action_edit_new, EdgeSettingsUi.Kind.PRIMARY) { openEditor("", null) },
            act(R.string.action_edit_history) { showHistory() },
            act(R.string.action_edit_refresh, EdgeSettingsUi.Kind.QUIET) { refresh() })
        // What is running now, and the only way to stop it: one block, never mixed into the list.
        val running = EdgeSettingsUi.column(this).apply {
            background = EdgeSettingsUi.frame(this@ActionMacrosActivity)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        statusView = TextView(this).apply {
            textSize = 13f; setTextColor(EdgeSettingsUi.muted(this@ActionMacrosActivity))
            setLineSpacing(dp(3).toFloat(), 1f)
        }.also { running.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        stopButton = button(R.string.action_macro_stop, EdgeSettingsUi.Kind.DANGER) {
            // Use the displayed ID so a late tap cannot stop a replacement run.
            val id = stopButton?.tag as? String
            if (id != null) runCatching { ActionRuntime.stop(id) }.onFailure { message(it.message.orEmpty()) }
            updateStatus()
        }.also { running.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) }) }
        gutter(running, top = 6)
        updateStatus()
        // Nothing here runs without it, so the permission stays above the list rather than under it.
        gutter(button(R.string.action_edit_permission) {
            runCatching { AndroidActions.command(this, listOf("permission")) }.onFailure { message(it.message.orEmpty()) }
        }, top = 4, bottom = 12)
        if (names.isEmpty()) gutter(note(getString(R.string.action_edit_empty)), top = 4, bottom = 12)
        names.forEach { savedName ->
            body.addView(EdgeSettingsUi.hairline(this))
            val row = EdgeSettingsUi.row(this).apply {
                minimumHeight = dp(58)
                setPadding(dp(EdgeSettingsUi.GUTTER), dp(6), dp(6), dp(6))
                background = EdgeSettingsUi.ripple(this@ActionMacrosActivity, null)
                isClickable = true
                contentDescription = savedName
                setOnClickListener { io({ store.readText(savedName) }) { if (!editing) openEditor(savedName, it) } }
            }
            // A saved name is an identifier, so it is set like one; the row itself opens the editor.
            row.addView(EdgeSettingsUi.mono(this, savedName).apply {
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                textSize = 15f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            fun compact(label: Int, kind: EdgeSettingsUi.Kind, action: () -> Unit) =
                row.addView(button(label, kind, action).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) },
                    LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) })
            compact(R.string.action_edit_run, EdgeSettingsUi.Kind.OUTLINE) { runSaved(savedName) }
            compact(R.string.action_edit_duplicate, EdgeSettingsUi.Kind.QUIET) {
                io({ store.readText(savedName) }) { if (!editing) openEditor("", null, it) }
            }
            compact(R.string.action_edit_delete, EdgeSettingsUi.Kind.QUIET) { deleteSaved(savedName) }
            body.addView(row)
        }
        body.addView(EdgeSettingsUi.hairline(this))
        gutter(note(getString(R.string.action_edit_intro)), top = 14, bottom = 16)
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
        val head = EdgeSettingsUi.column(this, gutter = true)
        head.addView(EdgeSettingsUi.caption(this, getString(R.string.action_edit_name)))
        head.addView(EdgeSettingsUi.field(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(64))
            typeface = android.graphics.Typeface.MONOSPACE
            contentDescription = getString(R.string.action_edit_name); setText(name)
            doAfterTextChanged { name = it.toString() }
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(head)
        buttons(body, act(R.string.action_edit_save, EdgeSettingsUi.Kind.PRIMARY) { save() },
            act(if (textMode) R.string.action_edit_form else R.string.action_edit_text) { textMode = !textMode; render() },
            act(R.string.action_edit_copy_text, EdgeSettingsUi.Kind.QUIET) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(name, raw))
                message(getString(R.string.action_edit_copied))
            })
        if (textMode) buttons(body,
            act(R.string.action_edit_add_repeat) { addControl(false) },
            act(R.string.action_edit_add_branch) { addControl(true) })
        if (!textMode && ActionEditorDocument(raw).hasBlocks)
            gutter(note(getString(R.string.action_edit_blocks_hint)), top = 4, bottom = 4)
        // A structure error stops the form from opening at all, so it is stated in the danger colour.
        structure.exceptionOrNull()?.let {
            gutter(note(getString(R.string.action_block_invalid, it.message.orEmpty())).apply {
                setTextColor(EdgeSettingsUi.danger(this@ActionMacrosActivity))
                background = EdgeSettingsUi.frame(this@ActionMacrosActivity,
                    stroke = EdgeSettingsUi.danger(this@ActionMacrosActivity))
            }, top = 4, bottom = 4)
        }
        if (textMode) {
            gutter(EdgeSettingsUi.field(this, lines = 12).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                filters = arrayOf(InputFilter.LengthFilter(ActionDefinition.MAX_BYTES))
                typeface = android.graphics.Typeface.MONOSPACE; textSize = 13.5f
                contentDescription = getString(R.string.action_edit_text)
                setText(raw); doAfterTextChanged { raw = it.toString() }
            }, top = 6, bottom = 6)
        } else {
            val document = ActionEditorDocument(raw)
            body.addView(EdgeSettingsUi.hairline(this))
            body.addView(EdgeSettingsUi.valueRow(this, getString(R.string.action_edit_timeout),
                document.header("timeout") ?: "30") {
                editHeader("timeout", R.string.action_edit_timeout, document.header("timeout") ?: "30")
            })
            body.addView(EdgeSettingsUi.hairline(this))
            body.addView(EdgeSettingsUi.valueRow(this, getString(R.string.action_edit_screen),
                document.header("screen") ?: "—") {
                editHeader("screen", R.string.action_edit_screen, document.header("screen") ?: "current")
            })
            ActionBlockEditor.render(this, body, structure.getOrThrow(),
                edit = { line, source -> stepLine = line; stepGroup = -1; stepDraft = source; pickedScreen = null; showStep() },
                add = { group, source -> stepLine = -1; stepGroup = group; stepDraft = source; pickedScreen = null; showStep() },
                change = { source -> ActionBlockDocument(source); raw = source; render() },
                show = { showManaged(it) }, failure = { failure(it) })
        }
        body.addView(EdgeSettingsUi.hairline(this))
        gutter(note(getString(R.string.action_edit_save_hint)), top = 14, bottom = 16)
    }
    private fun editHeader(key: String, title: Int, initial: String) {
        val field = EdgeSettingsUi.field(this).apply {
            setText(initial)
            contentDescription = getString(title)
            if (key == "screen") hint = getString(R.string.action_edit_screen_hint)
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(dialogBody(field))
            .setPositiveButton(R.string.action_edit_apply, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { raw = if (key == "screen" && field.text.isBlank()) ActionEditorDocument(raw).withoutScreen()
                    else ActionEditorDocument(raw).withHeader(key, field.text.toString()); render(); dialog.dismiss() } catch (e: Exception) { failure(e) }
        } }
        showManaged(dialog)
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
                stepDraft = line; pickRequest = request
            },
            pick = { line ->
                val document = ActionEditorDocument(raw)
                val target = document.targetBefore(if (stepLine < 0) insertionGroup().end else stepLine) ?: error(getString(R.string.action_pick_target))
                val screen = ActionRuntime.screen(this)
                val savedScreen = document.header("screen")
                check(savedScreen == null || savedScreen == "current" || savedScreen == screen.toString()) { getString(R.string.action_pick_screen) }
                val request = UUID.randomUUID().toString()
                ActionEditorDocument.singleLine(line)
                ActionDefinition.parse("version=1\nscreen=$screen\ntarget $target\n$line")
                AndroidActions.pickCoordinates(request, target, line.trim().split(Regex("\\s+")).first() == "swipe")
                stepDraft = line; pickRequest = request
            })
    }
    private fun save() {
        if (busy) return
        val savedName = name.trim()
        val source = raw
        val expected = if (savedName == originalName) baseline else null
        val screen = try { ActionRuntime.screen(this) } catch (e: Exception) { failure(e); return }
        busy = true
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.saveEdited(savedName, source, screen, expected) }
                originalName = savedName; baseline = saved.text; initial = saved.text
                if (raw == source && name.trim() == savedName) { editing = false; stepDraft = null; refresh() }
                message(getString(R.string.action_edit_saved))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
            finally { busy = false }
        }
    }
    private fun leave() {
        if (busy) return
        if (!editing) { finish(); return }
        val dirty = raw != initial || (originalName != null && name != originalName) ||
            (originalName == null && (name.isNotEmpty() || raw != ActionEditorDocument.EMPTY))
        fun closeEditor() { editing = false; stepDraft = null; pickedScreen = null; refresh() }
        if (!dirty) closeEditor() else AlertDialog.Builder(this).setTitle(R.string.edge_discard_title)
            .setMessage(R.string.edge_discard_message).setPositiveButton(R.string.edge_discard) { _, _ -> closeEditor() }
            .setNegativeButton(R.string.edge_keep_editing, null).showManaged()
    }
    private fun deleteSaved(savedName: String) = io({ store.readText(savedName) }) { expected ->
        if (editing) return@io
        AlertDialog.Builder(this).setTitle(R.string.action_edit_delete)
            .setMessage(getString(R.string.action_edit_delete_confirm, savedName))
            .setPositiveButton(R.string.action_edit_delete) { _, _ -> io({ store.deleteEdited(savedName, expected) }) { refresh() } }
            .setNegativeButton(android.R.string.cancel, null).showManaged()
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
        if (records.isEmpty()) contents.addView(EdgeSettingsUi.body(this, getString(R.string.action_edit_no_history)))
        records.forEachIndexed { index, record ->
            if (index > 0) contents.addView(EdgeSettingsUi.hairline(this))
            val entry = EdgeSettingsUi.column(this).apply { setPadding(0, dp(10), 0, dp(10)) }
            entry.addView(EdgeSettingsUi.caption(this,
                DateFormat.getDateTimeInstance().format(Date(record.optLong("time")))))
            entry.addView(EdgeSettingsUi.body(this, formatStatus(record)).apply {
                textSize = 13f; setLineSpacing(dp(3).toFloat(), 1f)
            })
            contents.addView(entry)
        }
        AlertDialog.Builder(this).setTitle(R.string.action_edit_history)
            .setView(ScrollView(this).apply { addView(dialogBody(contents)) })
            .setPositiveButton(android.R.string.ok, null).showManaged()
    }
}
