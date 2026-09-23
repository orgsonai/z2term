package com.zerotoship.z2term.viewer

import android.content.Context
import android.content.Intent
import android.app.AlertDialog
import android.app.Dialog
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.compose.ui.graphics.toArgb
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeRunner
import com.zerotoship.z2term.edge.EdgeToolRow
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.ui.theme.AppColors
import androidx.core.view.isEmpty

/** Offline HTML and separately registered controls. HTML never supplies shell code. */
@android.annotation.SuppressLint("ViewConstructor", "ClickableViewAccessibility") // Programmatic view; the listener defers clicks to WebView.
internal class ViewerPane(context: Context, private val key: String, private val target: String = "",
    private val producer: String = "", private val every: Long = 0, private val timeout: Long = 30,
    private val expand: (() -> Unit)? = null, private val external: (() -> Unit)? = null,
    private val runner: EdgeRunner = EdgeRunner(context.applicationContext),
    private val options: ViewerOptions = ViewerOptions()) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val status = TextView(context)
    private val tools = EdgeToolRow(context)
    private val formScroll = ScrollView(context)
    private var form: ViewerForm? = null
    private var dialog: Dialog? = null
    private var pending: ViewerPage? = null
    private var mutation = false
    val hasDraft: Boolean get() = form != null
    private val web = WebView(context)
    private val refreshButton = button(R.string.edge_refresh) { refresh() }
    private var page: ViewerPage? = null
    private var disposed = false
    private var busy = false
    private var retryScheduled = false
    private val refreshInterval = options.interval(every)
    private var restoreY = positions[key] ?: 0
    private val token = ViewerStore.attach(key) { if (hasDraft || mutation || dialog != null) pending = it else render(it) }
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            if (!busy && !hasDraft && dialog == null && isShown) refresh(automatic = true)
            if (refreshInterval > 0) main.postDelayed(this, refreshInterval * 1000)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(AppColors.bgPrimary.toArgb())
        addView(tools, LayoutParams(-1, -2))
        updateTools()
        status.setTextColor(AppColors.textSecondary.toArgb())
        status.setPadding(dp(12), dp(6), dp(12), dp(6)); status.visibility = GONE
        addView(status, LayoutParams(-1, -2))
        web.setBackgroundColor(AppColors.bgPrimary.toArgb())
        web.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            blockNetworkLoads = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (disposed || request == null || !request.isForMainFrame || !request.hasGesture()) return true
                val uri = request.url
                if (uri.scheme == "z2-action") {
                    page?.controls?.actions?.firstOrNull { it.id == uri.schemeSpecificPart }?.let { action(it) }
                } else if (uri.scheme in setOf("http", "https")) {
                    val app = context.applicationContext
                    runCatching {
                        external?.invoke()
                        app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { Toast.makeText(app, R.string.viewer_no_browser, Toast.LENGTH_LONG).show() }
                }
                return true
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!disposed) web.post { if (!disposed) web.scrollTo(0, restoreY) }
            }
        }
        web.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) web.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        addView(web, LayoutParams(-1, 0, 1f))
        formScroll.visibility = GONE
        addView(formScroll, LayoutParams(-1, 0, 1f))
        readSnapshot()
        if (options.automatic && producer.isNotBlank()) post { if (!disposed) refresh(automatic = true) }
        if (refreshInterval > 0) main.postDelayed(poll, refreshInterval * 1000)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun button(label: Int, click: () -> Unit) = Button(context).apply {
        setText(label); isAllCaps = false; minHeight = dp(48)
        setTextColor(AppColors.textPrimary.toArgb()); setOnClickListener { click() }
    }
    private fun message(value: String) { status.text = value; status.visibility = if (value.isBlank()) GONE else VISIBLE }

    private fun updateTools() {
        tools.removeAllViews()
        refreshButton.isEnabled = !busy && !hasDraft
        if (options.showRefresh) tools.addView(refreshButton)
        page?.controls?.actions?.filter { it.toolbar }?.forEach { a ->
            tools.addView(Button(context).apply {
                text = a.label; isAllCaps = false; minHeight = dp(48); setTextColor(AppColors.textPrimary.toArgb())
                isEnabled = !busy && !hasDraft; setOnClickListener { action(a) }
            })
        }
        if (expand != null && options.showExpand) tools.addView(button(R.string.viewer_expand) { expand.invoke() }.apply { isEnabled = !busy && !hasDraft })
        tools.visibility = if (tools.isEmpty()) GONE else VISIBLE
    }

    private fun readSnapshot() = runCatching { ViewerStore.read(context, key) }.onSuccess {
        if (it != null) render(it) else message(context.getString(
            if (target.isNotEmpty() && !options.automatic) {
                if (options.showRefresh) R.string.viewer_empty_manual else R.string.viewer_empty_hidden
            } else R.string.viewer_empty))
    }.onFailure { message(it.message.orEmpty()) }

    private fun render(next: ViewerPage) {
        if (disposed) return
        if (page != null) restoreY = web.scrollY
        val changed = page?.html != next.html
        page = next
        if (changed) web.loadDataWithBaseURL(null, themed(next.html), "text/html", "UTF-8", null)
        updateTools()
        if (!busy) message("")
    }

    fun refresh(automatic: Boolean = false) {
        if (disposed || busy || hasDraft || retryScheduled) return
        if (automatic && !options.automatic) return
        val controls = page?.controls
        if (!automatic && controls != null && controls.refresh.isNotEmpty()) invoke(controls, controls.refresh, read = true)
        else if (producer.isNotBlank()) run(producer, null, read = true)
        else readSnapshot()
    }

    private fun invoke(controls: ViewerControls, args: List<String>, read: Boolean) {
        run("sh \"\$HOME/.z2term/macros/${controls.handler}\"", args, read)
    }

    private fun run(command: String, args: List<String>?, read: Boolean) {
        if (disposed || busy) return
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { form?.enable(true); return }
        val env = "export Z2_VIEW_SESSION=${HeadlessRun.shSingleQuote(token)} " +
            "Z2_VIEW_TARGET=${HeadlessRun.shSingleQuote(target)}; "
        busy = true; mutation = !read; updateTools()
        message(context.getString(R.string.edge_updating))
        val accepted = runner.run(if (read) "read:$token" else "action:$token", env + command, timeout, arguments = args) { result ->
            if (!disposed) {
                busy = false; mutation = false
                if (result.error == null) {
                    closeForm()
                    pending?.let { pending = null; render(it) }
                }
                form?.enable(true); updateTools()
                message(result.error.orEmpty())
            } else if (!read && result.error != null) {
                Toast.makeText(context.applicationContext, result.error, Toast.LENGTH_LONG).show()
            }
        }
        if (!accepted) {
            busy = false; mutation = false; form?.enable(true); updateTools(); message(context.getString(R.string.edge_busy))
            if (read && !retryScheduled) {
                retryScheduled = true
                main.postDelayed({
                    retryScheduled = false
                    if (!disposed && !busy && !hasDraft) run(command, args, read = true)
                }, 500)
            }
        }
    }

    private fun action(action: ViewerControls.Action) {
        if (disposed || busy || hasDraft) return
        val controls = page?.controls ?: return
        if (action.fields.isNotEmpty()) {
            restoreY = web.scrollY; web.visibility = GONE
            form = ViewerForm(context, action, ::showDialog,
                cancel = { if (!busy) { closeForm(); pending?.let { pending = null; render(it) }; updateTools() } },
                submit = { args -> confirmAction(controls, action, action.args + args) }, error = ::message)
            formScroll.removeAllViews(); formScroll.addView(form); formScroll.visibility = VISIBLE
            updateTools()
        } else confirmAction(controls, action, action.args)
    }

    private fun confirmAction(controls: ViewerControls, action: ViewerControls.Action, args: List<String>) {
        fun submit() {
            if (page?.controls === controls) { form?.enable(false); invoke(controls, args, read = false) }
        }
        if (action.confirmation.isNotEmpty()) {
            showDialog(AlertDialog.Builder(context).setTitle(action.label).setMessage(action.confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(action.label) { _, _ -> submit() }.create())
        } else submit()
    }

    private fun closeForm() {
        if (form == null) return
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        form = null; formScroll.removeAllViews(); formScroll.visibility = GONE; web.visibility = VISIBLE
    }

    private fun showDialog(next: Dialog) {
        dialog?.dismiss()
        if (target.isNotEmpty()) next.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog = next
        next.setOnDismissListener {
            if (dialog === next) dialog = null
            if (!disposed && !hasDraft && !busy) pending?.let { pending = null; render(it) }
        }
        next.show()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        positions[key] = if (hasDraft) restoreY else web.scrollY
        if (positions.size > 100) positions.remove(positions.keys.first())
        ViewerStore.detach(token); main.removeCallbacksAndMessages(null); runner.cancelRead(token)
        dialog?.dismiss(); dialog = null
        removeView(web); web.stopLoading(); web.destroy()
    }

    private fun themed(html: String): String {
        fun hex(color: androidx.compose.ui.graphics.Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
        val style = "<style>:root{--z2-bg:%s;--z2-bg2:%s;--z2-fg:%s;--z2-dim:%s;--z2-line:%s;--z2-accent:%s}</style>".format(
            hex(AppColors.bgPrimary), hex(AppColors.bgSecondary), hex(AppColors.textPrimary), hex(AppColors.textSecondary), hex(AppColors.border), hex(AppColors.accent))
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1) else style + html
    }

    companion object { private val positions = linkedMapOf<String, Int>() }
}
