package com.zerotoship.z2term.edge

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.HeadlessRun
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** All views and live values belong to the main thread. The text definitions remain authoritative. */
object EdgeRuntime {
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var runner: EdgeRunner? = null
    private var panels = emptyList<EdgeStore.Panel>()
    private val handles = linkedMapOf<String, TextView>()
    private val values = mutableMapOf<String, String>()
    private val badges = mutableMapOf<String, String>()
    private val renderers = mutableMapOf<String, (String) -> Unit>()
    private val revisions = mutableMapOf<String, Int>()
    private val scheduled = mutableListOf<Runnable>()
    private val retries = mutableMapOf<String, Runnable>()
    private var panelView: View? = null
    private var openId: String? = null
    private var receiver: BroadcastReceiver? = null
    private var generation = 0
    private var serviceRequested = false
    private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(128)

    fun store(context: Context) = EdgeStore(File(context.filesDir, "shared_home/.z2term/edge"))

    fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        main.post(task)
        return try { task.get(4, TimeUnit.SECONDS) } catch (e: Exception) {
            task.cancel(false)
            throw (e.cause as? Exception ?: e)
        }
    }

    private fun ui(): Context = ContextThemeWrapper(app!!, android.R.style.Theme_DeviceDefault_DayNight)
    private fun wm() = app!!.getSystemService(WindowManager::class.java)
    private fun dp(n: Int) = (n * app!!.resources.displayMetrics.density).toInt()
    private fun unlocked(): Boolean = app?.let {
        !it.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            it.getSystemService(PowerManager::class.java).isInteractive
    } == true
    private fun colors(): Pair<Int, Int> = if (app!!.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        Color.rgb(29, 31, 33) to Color.rgb(237, 238, 239) else Color.rgb(250, 250, 250) to Color.rgb(25, 27, 29)
    private fun background(round: Boolean = false) = GradientDrawable().apply {
        setColor(colors().first)
        setStroke(dp(1).coerceAtLeast(1), Color.GRAY)
        if (round) cornerRadius = dp(96).toFloat()
    }
    private fun text(label: String, size: Float = 16f) = TextView(ui()).apply {
        text = label; textSize = size; setTextColor(colors().second)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    fun restore(context: Context) = onMain {
        if (!Settings.canDrawOverlays(context)) {
            if (app != null) destroy()
            context.stopService(Intent(context, EdgeService::class.java))
            return@onMain
        }
        if (store(context).enabled() && Settings.canDrawOverlays(context)) {
            if (app == null) initialize(context)
            if (panels.isEmpty()) reload(context)
            if (handles.isEmpty() && unlocked()) showHandles()
            try { startService() } catch (e: Exception) { destroy(); throw e }
        }
    }

    private fun initialize(context: Context) {
        if (app != null) return
        app = context.applicationContext
        runner = EdgeRunner(app!!)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> { close(); removeHandles() }
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> if (unlocked()) showHandles()
                    Intent.ACTION_CONFIGURATION_CHANGED -> {
                        val previous = openId
                        close(); removeHandles(); iconCache.evictAll()
                        if (unlocked()) { showHandles(); if (previous != null) open(previous) }
                    }
                }
                }.onFailure { fail(it); destroy() }
            }
        }
        ContextCompat.registerReceiver(app!!, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun on(context: Context) = onMain {
        require(Settings.canDrawOverlays(context)) { context.getString(R.string.edge_overlay_help) }
        val loaded = store(context).panels()
        require(loaded.isNotEmpty()) { "Create a panel first: z2-edge handle main button" }
        require(unlockedContext(context)) { "Unlock the screen before enabling the panel" }
        initialize(context)
        store(context).enable(true)
        try { reload(context); startService() } catch (e: Exception) {
            store(context).enable(false); destroy(); throw e
        }
    }

    private fun unlockedContext(context: Context) = !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        context.getSystemService(PowerManager::class.java).isInteractive

    private fun startService() {
        if (serviceRequested) return
        // Android 15: show the overlay before requesting the foreground-service exemption.
        ContextCompat.startForegroundService(app!!, Intent(app, EdgeService::class.java))
        serviceRequested = true
    }

    fun off(context: Context) = onMain {
        store(context).enable(false)
        destroy()
        context.stopService(Intent(context, EdgeService::class.java))
        Unit
    }

    fun destroy() = onMain {
        close(); removeHandles()
        receiver?.let { r -> app?.let { runCatching { it.unregisterReceiver(r) } } }
        receiver = null
        runner?.cancelAll(); runner = null
        panels = emptyList(); values.clear(); badges.clear(); revisions.clear(); iconCache.evictAll()
        app = null; serviceRequested = false
    }

    fun reload(context: Context) = onMain {
        val loaded = store(context).panels() // Reject invalid definitions before disturbing visible state.
        if (!store(context).enabled()) return@onMain
        require(Settings.canDrawOverlays(context)) { context.getString(R.string.edge_overlay_help) }
        initialize(context)
        val previous = openId
        close(); removeHandles(); iconCache.evictAll()
        panels = loaded
        val targets = panels.flatMap { p -> p.items.map { "${p.id}:${it.id}" } }.toSet()
        values.keys.retainAll(targets); revisions.keys.retainAll(targets)
        badges.keys.retainAll(panels.map { it.id }.toSet())
        if (unlocked()) {
            showHandles()
            if (panels.any { it.id == previous }) open(previous!!)
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        wm().defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun params(width: Int, height: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            (if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }

    private fun removeHandles() {
        handles.values.forEach { runCatching { wm().removeView(it) } }
        handles.clear()
    }

    private fun showHandles() {
        if (!unlocked()) return
        removeHandles()
        panels.filter { it.handle != "off" }.forEach { panel ->
            val (width, height) = screenSize()
            val f = panel.fields
            val button = panel.handle == "button"
            val size = dp(f["size"]?.toIntOrNull() ?: 48)
            val w = if (button) size else dp(24)
            val h = if (button) size else (height * (f["length"]?.toFloatOrNull() ?: 25f) / 100).toInt().coerceAtLeast(dp(48))
            val p = params(w.coerceAtMost(width), h.coerceAtMost(height))
            val right = f["side"] != "left"
            p.x = if (button) ((width - w).coerceAtLeast(0) * (f["x"]?.toFloatOrNull() ?: 85f) / 100).toInt()
                else if (right) (width - w).coerceAtLeast(0) else 0
            p.y = ((height - h).coerceAtLeast(0) * (f[if (button) "y" else "offset"]?.toFloatOrNull() ?: 30f) / 100).toInt()
            val view = text(badges[panel.id] ?: if (button) "≡" else "│", 16f).apply {
                background = background(button); gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0); maxLines = 2
                contentDescription = f["label"] ?: panel.id
                isClickable = true
                setOnClickListener { activateHandle(panel) }
            }
            var startX = 0f; var startY = 0f; var originalX = 0; var originalY = 0; var moved = false
            val slop = ViewConfiguration.get(app!!).scaledTouchSlop
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; originalX = p.x; originalY = p.y; moved = false; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX; val dy = event.rawY - startY
                        if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                        if (button && moved) {
                            p.x = (originalX + dx.toInt()).coerceIn(0, (width - w).coerceAtLeast(0))
                            p.y = (originalY + dy.toInt()).coerceIn(0, (height - h).coerceAtLeast(0))
                            runCatching { wm().updateViewLayout(view, p) }.onFailure { fail(it) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (button && moved) {
                            runCatching {
                                store(app!!).setPanel(panel.id, mapOf(
                                    "x" to (p.x * 100f / (width - w).coerceAtLeast(1)).toString(),
                                    "y" to (p.y * 100f / (height - h).coerceAtLeast(1)).toString()))
                                panels = store(app!!).panels()
                            }.onFailure { fail(it) }
                        } else if (!moved || (!button && (event.rawX - startX) * (if (right) -1 else 1) > slop)) view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { if (button && moved) {
                        p.x = originalX; p.y = originalY
                        runCatching { wm().updateViewLayout(view, p) }
                    }; true }
                    else -> false
                }
            }
            wm().addView(view, p)
            handles[panel.id] = view
        }
    }

    private fun activateHandle(panel: EdgeStore.Panel) {
        if (!unlocked()) return
        runCatching {
            val command = panel.fields["run"].orEmpty()
            if (command.isNotBlank()) {
                close()
                val accepted = runner!!.run("handle:${panel.id}", command, 30) { if (it.error != null) fail(IllegalStateException(it.error)) }
                if (!accepted) fail(IllegalStateException(app!!.getString(R.string.edge_busy)))
            } else if (openId == panel.id) close() else open(panel.id)
        }.onFailure { fail(it) }
    }

    fun open(id: String) = onMain {
        require(app != null && store(app!!).enabled()) { "Enable the panel first: z2-edge on" }
        require(unlocked()) { "Unlock the screen before opening the panel" }
        val panel = panels.firstOrNull { it.id == id } ?: throw IllegalArgumentException("No panel: $id")
        close()
        openId = id
        val body = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL; background = background() }
        val header = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(panel.fields["label"] ?: panel.id).apply { setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(ui()).apply { text = app!!.getString(R.string.edge_close); setOnClickListener { close() } })
        body.addView(header)
        val rows = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ui()).apply { isFillViewport = false; addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (panel.items.isEmpty()) rows.addView(text(app!!.getString(R.string.edge_empty)))
        panel.items.forEach { item -> addItem(rows, panel.id, item) }
        val (width, height) = screenSize()
        val p = params(dp(360).coerceAtMost((width - dp(32)).coerceAtLeast(1)), (height * 0.72f).toInt(), focus = true)
        p.x = if (panel.fields["side"] == "left") dp(24) else (width - p.width - dp(24)).coerceAtLeast(0)
        p.y = dp(24)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        body.isFocusableInTouchMode = true
        body.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { close(); true } else false
        }
        body.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { close(); true } else false }
        try { wm().addView(body, p); panelView = body; body.requestFocus() }
        catch (e: Exception) { close(); throw e }
        panel.items.filter { it.type in setOf("text", "toggle", "list") }.forEach { item ->
            refresh(panel.id, item)
            if (item.every > 0) {
                val gen = generation
                val task = object : Runnable {
                    override fun run() {
                        if (generation != gen || openId != id || !unlocked()) return
                        refresh(id, item)
                        main.postDelayed(this, item.every * 1000)
                    }
                }
                scheduled.add(task); main.postDelayed(task, item.every * 1000)
            }
        }
    }

    fun close() = onMain {
        generation++
        scheduled.forEach { main.removeCallbacks(it) }; scheduled.clear()
        retries.values.forEach { main.removeCallbacks(it) }; retries.clear()
        // Stop hidden data producers. Explicit actions can finish (including out=notify).
        runner?.cancelReads()
        panelView?.let { runCatching { wm().removeView(it) } }
        panelView = null; openId = null; renderers.clear()
    }

    private fun addItem(rows: LinearLayout, panelId: String, item: EdgeStore.Item) {
        val target = "$panelId:${item.id}"
        val row = LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val title = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
        val pkg = packageFrom(item.command)
        val label = item.fields["label"] ?: pkg?.let { runCatching {
            val pm = app!!.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrNull() } ?: item.id
        addIcon(title, item.fields["icon"] ?: pkg?.let { "@app:$it" })
        title.addView(text(label).apply { setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(title)
        val result = text(values[target].orEmpty(), 14f).apply { setTextIsSelectable(true) }
        val status = text("", 12f).apply { visibility = View.GONE }
        renderers["$target:status"] = { status.text = it; status.visibility = if (it.isBlank()) View.GONE else View.VISIBLE }
        when (item.type) {
            "run" -> title.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_run)
                setOnClickListener { execute(panelId, item, item.command) }
            })
            "text" -> title.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_refresh)
                setOnClickListener { refresh(panelId, item) }
            })
            "toggle" -> {
                val toggle = Switch(ui()).apply { contentDescription = label; showText = true
                    textOn = "ON"; textOff = "OFF"; isChecked = values[target]?.trim() in setOf("on", "true", "1") }
                toggle.setOnClickListener {
                    // Render observed state, never assume a command succeeded.
                    toggle.isChecked = values[target]?.trim() in setOf("on", "true", "1")
                    execute(panelId, item, item.command, after = { refresh(panelId, item) })
                }
                title.addView(toggle)
                renderers[target] = { toggle.isChecked = it.trim() in setOf("on", "true", "1") }
            }
            "list" -> {
                val list = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
                row.addView(list)
                val render: (String) -> Unit = { output ->
                    list.removeAllViews()
                    output.lineSequence().filter { it.isNotBlank() }.take(100).forEach { line ->
                        val labelText = line.substringBefore('\t')
                        val value = if ('\t' in line) line.substringAfter('\t') else line
                        list.addView(Button(ui()).apply {
                            text = labelText; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setOnClickListener { execute(panelId, item, item.fields["on-select"].orEmpty(), value = value) }
                        })
                    }
                }
                renderers[target] = render
                render(values[target].orEmpty())
            }
            "input" -> {
                val entry = EditText(ui()).apply {
                    hint = label; contentDescription = label; minLines = 2; maxLines = 5
                    filters = arrayOf(android.text.InputFilter.LengthFilter(65536))
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
                row.addView(entry)
                title.addView(Button(ui()).apply {
                    text = app!!.getString(R.string.edge_send)
                    setOnClickListener { execute(panelId, item, item.command, input = entry.text.toString()) }
                })
            }
        }
        if (item.type != "toggle" && item.type != "list") {
            row.addView(result)
            renderers[target] = { result.text = it }
        }
        row.addView(status)
        rows.addView(row)
        rows.addView(View(ui()).apply { setBackgroundColor(Color.GRAY) }, LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)))
    }

    private fun refresh(panel: String, item: EdgeStore.Item) {
        val command = if (item.type == "toggle") item.fields["state"].orEmpty() else item.command
        if (command.isBlank()) return // push-only item
        val target = "$panel:${item.id}"
        if (runner?.isRunning("read:$target") == true || runner?.isRunning("action:$target") == true || target in retries) return
        if (!execute(panel, item, command, refresh = true)) {
            val gen = generation
            val task = Runnable {
                retries.remove(target)
                if (generation == gen && openId == panel && unlocked()) refresh(panel, item)
            }
            retries[target] = task
            main.postDelayed(task, 500)
        }
    }

    private fun execute(panel: String, item: EdgeStore.Item, command: String, input: String? = null,
                        value: String? = null, refresh: Boolean = false, after: (() -> Unit)? = null): Boolean {
        if (!unlocked()) return false
        val target = "$panel:${item.id}"
        if (command.isBlank()) { renderers["$target:status"]?.invoke(app!!.getString(R.string.edge_no_command)); return false }
        if (!refresh) runner?.cancelRead(target)
        if (!refresh && item.type == "run" && item.fields["out"].orEmpty() in setOf("", "none")) close()
        val gen = generation
        val revision = revisions[target] ?: 0
        val owner = runner!!
        val accepted = owner.run((if (refresh) "read:" else "action:") + target, command, item.timeout, input, value) { result ->
            if (app == null || runner !== owner) return@run
            val visible = gen == generation && openId == panel
            if (refresh && item.type == "toggle" && result.error == null &&
                result.output.trim() !in setOf("on", "off", "true", "false", "1", "0")) {
                if (visible) renderers["$target:status"]?.invoke("state must return on/off, true/false or 1/0")
                return@run
            }
            if (visible) renderers["$target:status"]?.invoke(result.error.orEmpty())
            else if (!refresh && result.error != null) fail(IllegalStateException(result.error))
            if (result.error == null) {
                if (refresh || item.fields["out"] == "panel") {
                    val sameItem = panels.firstOrNull { it.id == panel }?.items?.contains(item) == true
                    if (sameItem && (!refresh || visible) && (revisions[target] ?: 0) == revision) put(target, result.output)
                } else when (item.fields["out"]) {
                    "toast" -> Toast.makeText(app, result.output.take(1000), Toast.LENGTH_LONG).show()
                    "notify" -> runner!!.run("notify:$target", "z2-notify ${HeadlessRun.shSingleQuote(result.output.take(8000))}", 10) { }
                }
                if (visible) after?.invoke()
            }
        }
        renderers["$target:status"]?.invoke(app!!.getString(if (accepted) R.string.edge_updating else R.string.edge_busy))
        return accepted
    }

    private fun put(target: String, text: String) {
        values[target] = text.take(65536)
        renderers[target]?.invoke(values[target].orEmpty())
    }

    fun push(context: Context, target: String, text: String, state: Boolean = false) = onMain {
        val item = store(context).item(target)
        require(store(context).enabled() && app != null) { "Enable the panel first: z2-edge on" }
        require(text.toByteArray().size <= 65536) { "Value exceeds 64 KiB" }
        if (state) require(item.type == "toggle" && text in setOf("on", "off")) { "state requires a toggle and on|off" }
        if (item.type == "toggle") require(text.trim() in setOf("on", "off", "true", "false", "1", "0")) { "Invalid toggle state" }
        revisions[target] = (revisions[target] ?: 0) + 1
        put(target, text)
    }

    fun badge(context: Context, id: String, value: String) = onMain {
        store(context).panel(id)
        require(store(context).enabled() && app != null) { "Enable the panel first: z2-edge on" }
        require(value.length <= 16) { "Badge: at most 16 characters" }
        badges[id] = value
        handles[id]?.text = value.ifBlank { if (panels.first { it.id == id }.handle == "button") "≡" else "│" }
    }

    private fun addIcon(row: LinearLayout, value: String?) {
        if (value.isNullOrBlank()) return
        if (!value.startsWith('@')) { row.addView(text(value)); return }
        val drawable = iconCache.get(value) ?: runCatching {
            when {
                value.startsWith("@app:") -> app!!.packageManager.getApplicationIcon(value.removePrefix("@app:"))
                value.startsWith("@file:") -> {
                    val path = value.removePrefix("@file:")
                    val file = when {
                        path.startsWith("~/") -> File(app!!.filesDir, "shared_home/${path.removePrefix("~/")}")
                        path.startsWith("/root/") -> File(app!!.filesDir, "shared_home/${path.removePrefix("/root/")}")
                        path.startsWith("/sdcard/") || path.startsWith("/storage/") -> File(path)
                        else -> throw IllegalArgumentException("Icon path must be ~/…, /root/… or /sdcard/…")
                    }
                    require(file.length() <= 2 * 1024 * 1024) { "Icon too large" }
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, opts)
                    require(opts.outWidth in 1..4096 && opts.outHeight in 1..4096) { "Invalid icon size" }
                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = (maxOf(opts.outWidth, opts.outHeight) / 192).coerceAtLeast(1)
                    android.graphics.BitmapFactory.decodeFile(file.path, opts)?.let { android.graphics.drawable.BitmapDrawable(app!!.resources, it) }
                }
                value.startsWith("@z2:") -> {
                    val art = com.zerotoship.z2term.icon.IconStore.findSample(app!!, value.removePrefix("@z2:"))
                        ?: throw IllegalArgumentException("No such icon")
                    val mask = com.zerotoship.z2term.icon.IconStore.parse(art)
                    val size = com.zerotoship.z2term.icon.IconStore.gridOf(mask)
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    mask.forEachIndexed { index, on -> if (on) bitmap.setPixel(index % size, index / size, colors().second) }
                    android.graphics.drawable.BitmapDrawable(app!!.resources, bitmap).apply { isFilterBitmap = false }
                }
                else -> null
            }
        }.getOrNull()?.also { iconCache.put(value, it) }
        if (drawable == null) row.addView(text("?")) else row.addView(ImageView(ui()).apply {
            setImageDrawable(drawable); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
    }

    /** Only simple, unambiguous launch commands get automatic icons. Never evaluate shell text. */
    internal fun packageFrom(command: String): String? = Regex(
        "^z2-intent\\s+(?:-p|--package)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)(?:\\s+--window\\s+full)?\\s*$"
    ).matchEntire(command.trim())?.groupValues?.get(1)

    private fun fail(error: Throwable) { app?.let { Toast.makeText(it, error.message ?: "Error", Toast.LENGTH_LONG).show() } }
}
