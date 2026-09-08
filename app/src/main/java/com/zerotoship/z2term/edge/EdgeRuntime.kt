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
import android.widget.FrameLayout
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
    private var windowContext: Context? = null
    private var configurationCallback: android.content.ComponentCallbacks? = null
    private val handleCallbacks = mutableListOf<Runnable>()
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
    private var snapPreview: View? = null
    private var openId: String? = null
    private var openRootId: String? = null
    private var editingItems = false
    private data class ItemDrag(val panel: String, val item: String)
    private val selectedTabs = mutableMapOf<String, String>()
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

    private fun ui(): Context = ContextThemeWrapper(windowContext!!, android.R.style.Theme_DeviceDefault_DayNight)
    private fun wm() = windowContext!!.getSystemService(WindowManager::class.java)
    private fun dp(n: Int) = (n * windowContext!!.resources.displayMetrics.density).toInt()
    private fun unlocked(): Boolean = app?.let {
        !it.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            it.getSystemService(PowerManager::class.java).isInteractive
    } == true
    private fun colors(): Pair<Int, Int> = if (windowContext!!.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
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
        windowContext = if (android.os.Build.VERSION.SDK_INT >= 30)
            app!!.createDisplayContext(app!!.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY))
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else app
        configurationCallback = object : android.content.ComponentCallbacks {
            override fun onLowMemory() = Unit
            override fun onConfigurationChanged(newConfig: Configuration) { rebuildWindows() }
        }
        windowContext!!.registerComponentCallbacks(configurationCallback!!)
        runner = EdgeRunner(app!!)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> { close(); removeHandles() }
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> if (unlocked()) showHandles()
                    Intent.ACTION_CONFIGURATION_CHANGED -> if (android.os.Build.VERSION.SDK_INT < 31) rebuildWindows()
                }
                }.onFailure { fail(it); destroy() }
            }
        }
        ContextCompat.registerReceiver(app!!, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun rebuildWindows() {
        runCatching {
            val previous = openRootId
            close(); removeHandles(); iconCache.evictAll()
            if (unlocked()) { showHandles(); if (previous != null) open(previous) }
        }.onFailure { fail(it) }
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
        configurationCallback?.let { windowContext?.unregisterComponentCallbacks(it) }
        configurationCallback = null; windowContext = null
        app = null; serviceRequested = false
    }

    fun reload(context: Context): Unit = onMain {
        val loaded = store(context).panels() // Reject invalid definitions before disturbing visible state.
        if (!store(context).enabled()) return@onMain
        require(Settings.canDrawOverlays(context)) { context.getString(R.string.edge_overlay_help) }
        initialize(context)
        val previous = openRootId
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
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val metrics = wm().currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            return (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1) to
                (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        }
        val metrics = android.util.DisplayMetrics()
        wm().defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun params(width: Int, height: Int, focus: Boolean = false) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            (if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }

    private fun removeSnapPreview() {
        snapPreview?.let { runCatching { wm().removeView(it) } }
        snapPreview = null
    }

    private fun previewSnap(right: Boolean) {
        removeSnapPreview()
        val (width, height) = screenSize()
        val line = View(ui()).apply { setBackgroundColor(colors().second); alpha = 0.35f }
        val p = params(dp(2).coerceAtLeast(1), height).apply {
            x = if (right) width - width.coerceAtMost(dp(2)) else 0
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        snapPreview = line
        wm().addView(line, p)
    }

    private fun removeHandles() {
        removeSnapPreview()
        handleCallbacks.forEach { main.removeCallbacks(it) }; handleCallbacks.clear()
        handles.values.forEach { runCatching { wm().removeView(it) } }
        handles.clear()
    }

    private fun showHandles() {
        if (!unlocked()) return
        removeHandles()
        panels.filter { it.handle != "off" && panels.none { parent -> it.id in parent.tabs } }.forEach { panel ->
            val (width, height) = screenSize()
            val f = panel.fields
            val button = panel.handle == "button"
            val size = dp(if (button) (f["size"]?.toIntOrNull() ?: 48).coerceIn(32, 96)
                else (f["size"]?.toIntOrNull() ?: 6).coerceIn(2, 48))
            val w = (if (button) size else size.coerceAtLeast(dp(24))).coerceAtMost(width)
            val h = (if (button) size else (height * (f["length"]?.toFloatOrNull() ?: 6f) / 100)
                .toInt().coerceAtLeast(dp(8))).coerceAtMost(height)
            val opening = f["open"] ?: if (button) "tap" else "swipe"
            val p = params(w.coerceAtMost(width), h.coerceAtMost(height))
            val right = f["side"] != "left"
            p.x = if (button) ((width - w).coerceAtLeast(0) * (f["x"]?.toFloatOrNull() ?: 85f) / 100).toInt()
                else if (right) (width - w).coerceAtLeast(0) else 0
            p.y = ((height - h).coerceAtLeast(0) * (f[if (button) "y" else "offset"]?.toFloatOrNull() ?: 30f) / 100).toInt()
            var relocating = false
            var previewRight: Boolean? = null
            val view = object : TextView(ui()) {
                override fun draw(canvas: android.graphics.Canvas) {
                    val save = canvas.save()
                    if (!button && !relocating) {
                        if (right) canvas.clipRect((this.width - size).coerceAtLeast(0), 0, this.width, this.height)
                        else canvas.clipRect(0, 0, size.coerceAtMost(this.width), this.height)
                    }
                    super.draw(canvas)
                    canvas.restoreToCount(save)
                }
            }.apply {
                text = badges[panel.id] ?: if (button) "≡" else ""
                textSize = 16f; setTextColor(colors().second)
                background = background(button); gravity = Gravity.CENTER
                alpha = f["alpha"]?.toFloatOrNull() ?: 1f
                setPadding(0, 0, 0, 0); maxLines = 2
                contentDescription = f["label"] ?: panel.id
                isClickable = true
                setOnClickListener { activateHandle(panel) }
            }
            var startX = 0f; var startY = 0f; var originalX = 0; var originalY = 0; var moved = false
            val slop = ViewConfiguration.get(windowContext!!).scaledTouchSlop
            fun releaseAppearance() {
                relocating = false
                view.text = badges[panel.id] ?: if (button) "≡" else ""
                view.alpha = f["alpha"]?.toFloatOrNull() ?: 1f
                view.invalidate()
                removeSnapPreview(); previewRight = null
            }
            val longPress = Runnable {
                relocating = true
                view.alpha = 1f
                if (!button) view.text = "⋮"
                view.invalidate()
                if (!button) {
                    previewRight = p.x + w / 2 >= width / 2
                    runCatching { previewSnap(previewRight!!) }.onFailure { fail(it); removeSnapPreview() }
                }
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            handleCallbacks.add(longPress)
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; originalX = p.x; originalY = p.y; moved = false; relocating = false
                        main.postDelayed(longPress, 300)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX; val dy = event.rawY - startY
                        if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                            moved = true; main.removeCallbacks(longPress)
                        }
                        if (relocating) {
                            p.x = (originalX + dx.toInt()).coerceIn(0, (width - w).coerceAtLeast(0))
                            p.y = (originalY + dy.toInt()).coerceIn(0, (height - h).coerceAtLeast(0))
                            runCatching {
                                wm().updateViewLayout(view, p)
                                val targetRight = p.x + w / 2 >= width / 2
                                if (!button && previewRight != targetRight) {
                                    previewRight = targetRight; previewSnap(targetRight)
                                }
                            }.onFailure { fail(it); removeSnapPreview() }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        main.removeCallbacks(longPress)
                        if (!button && relocating) {
                            runCatching {
                                val side = if (p.x + w / 2 < width / 2) "left" else "right"
                                store(app!!).setPanel(panel.id, mapOf("side" to side,
                                    "offset" to (p.y * 100f / (height - h).coerceAtLeast(1)).toString()))
                                panels = store(app!!).panels()
                                showHandles()
                            }.onFailure { fail(it) }
                        } else if (button && relocating) {
                            runCatching {
                                store(app!!).setPanel(panel.id, mapOf(
                                    "x" to (p.x * 100f / (width - w).coerceAtLeast(1)).toString(),
                                    "y" to (p.y * 100f / (height - h).coerceAtLeast(1)).toString()))
                                panels = store(app!!).panels()
                            }.onFailure { fail(it) }
                        } else {
                            val dx = event.rawX - startX; val dy = event.rawY - startY
                            if (EdgeHandleActivation.opens(opening, right, moved, dx, dy, slop)) view.performClick()
                        }
                        releaseAppearance()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { main.removeCallbacks(longPress); if (moved || relocating) {
                        p.x = originalX; p.y = originalY
                        runCatching { wm().updateViewLayout(view, p) }
                    }; releaseAppearance(); true }
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
            } else if (openRootId == panel.id) close() else open(panel.id)
        }.onFailure { fail(it) }
    }

    fun open(id: String, toggle: Boolean = false, tabId: String? = null): Unit = onMain {
        if (toggle && openRootId == id) { close(); return@onMain }
        require(app != null && store(app!!).enabled()) { "Enable the panel first: z2-edge on" }
        require(unlocked()) { "Unlock the screen before opening the panel" }
        val requested = panels.firstOrNull { it.id == id } ?: throw IllegalArgumentException("No panel: $id")
        val root = panels.firstOrNull { id in it.tabs } ?: requested
        val active = tabId ?: if (root.id != id) id else selectedTabs[root.id]
        val panel = panels.firstOrNull { it.id == active && (it.id == root.id || it.id in root.tabs) } ?: root
        close()
        selectedTabs[root.id] = panel.id
        openRootId = root.id
        openId = panel.id
        try {
            val (width, height) = screenSize()
            val density = windowContext!!.resources.displayMetrics.density
            val panelWidth = EdgeStore.dimensionPixels(root.fields["width"] ?: "360", width, density)
            val panelHeight = EdgeStore.dimensionPixels(root.fields["height"] ?: "72%", height, density)
            val body = object : LinearLayout(ui()) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val limit = minOf(panelHeight, View.MeasureSpec.getSize(heightMeasureSpec))
                    super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
                }
            }.apply { orientation = LinearLayout.VERTICAL; background = background(); isClickable = true }
            val header = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(text(root.fields["label"] ?: root.id).apply { setTypeface(null, Typeface.BOLD) },
                LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_add_app)
                setOnClickListener {
                    val context = app!!
                    close()
                    runCatching { context.startActivity(Intent(context, AppPickerActivity::class.java)
                        .putExtra("panel", panel.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { fail(it) }
                }
            })
            header.addView(Button(ui()).apply { text = app!!.getString(R.string.edge_close); setOnClickListener { close() } })
            body.addView(header)
            val tabs = LinearLayout(ui())
            (listOf(root.id) + root.tabs).forEach { childId ->
                val child = panels.first { it.id == childId }
                tabs.addView(Button(ui()).apply {
                    text = child.fields["label"] ?: child.id
                    isEnabled = childId != panel.id
                    setOnClickListener { runCatching { open(root.id, tabId = childId) }.onFailure { fail(it) } }
                })
            }
            val tabEntry = LinearLayout(ui()).apply { visibility = View.GONE }
            val tabName = EditText(ui()).apply { hint = app!!.getString(R.string.edge_tab_name); setSingleLine(true) }
            tabEntry.addView(tabName, LinearLayout.LayoutParams(0, -2, 1f))
            tabEntry.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_add)
                setOnClickListener { runCatching {
                    val name = tabName.text.toString().trim(); require(name.isNotEmpty()) { "Enter a tab name" }
                    val childId = "tab_" + java.util.UUID.randomUUID().toString().replace("-", "")
                    store(app!!).addTab(root.id, childId, name)
                    selectedTabs[root.id] = childId; reload(app!!)
                }.onFailure { fail(it) } }
            })
            tabs.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_add_tab)
                setOnClickListener { tabEntry.visibility = if (tabEntry.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            })
            body.addView(android.widget.HorizontalScrollView(ui()).apply { addView(tabs) })
            body.addView(tabEntry)
            val tools = LinearLayout(ui())
            tools.addView(Button(ui()).apply {
                text = app!!.getString(if (panel.fields["layout"] == "grid") R.string.edge_layout_list else R.string.edge_layout_grid)
                setOnClickListener { runCatching {
                    store(app!!).setPanel(panel.id, mapOf("layout" to if (panel.fields["layout"] == "grid") "list" else "grid"))
                    reload(app!!)
                }.onFailure { fail(it) } }
            })
            tools.addView(Button(ui()).apply {
                text = app!!.getString(if (editingItems) R.string.edge_done else R.string.edge_edit)
                setOnClickListener { editingItems = !editingItems; open(root.id) }
            })
            body.addView(tools)
            val rows = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(ui()).apply { isFillViewport = false; addView(rows) }
            body.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
            if (panel.items.isEmpty()) rows.addView(text(app!!.getString(R.string.edge_empty)))
            if (panel.fields["layout"] == "grid" && !editingItems) {
                val columns = (panelWidth / dp(72).coerceAtLeast(1)).coerceIn(1, 8)
                panel.items.filter { it.type == "run" }.chunked(columns).forEach { group ->
                    val line = LinearLayout(ui())
                    rows.addView(line)
                    group.forEach { item ->
                        val cell = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
                        line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                        addItem(cell, panel.id, item, iconOnly = true)
                    }
                    repeat(columns - group.size) { line.addView(View(ui()), LinearLayout.LayoutParams(0, 1, 1f)) }
                }
                panel.items.filter { it.type != "run" }.forEach { addItem(rows, panel.id, it) }
            } else panel.items.forEach { item -> addItem(rows, panel.id, item) }
            rows.setOnDragListener { _, event ->
                val drag = event.localState as? ItemDrag
                if (drag?.panel != panel.id) false else {
                    if (event.action == android.view.DragEvent.ACTION_DRAG_LOCATION) {
                        val y = event.y - scroll.scrollY
                        if (y < dp(40)) scroll.smoothScrollBy(0, -dp(24))
                        else if (y > scroll.height - dp(40)) scroll.smoothScrollBy(0, dp(24))
                    }
                    event.action != android.view.DragEvent.ACTION_DROP
                }
            }
            val overlay = object : FrameLayout(ui()) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                        if (event.action == KeyEvent.ACTION_UP) close()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                isFocusableInTouchMode = true
                setOnClickListener { close() }
            }
            val insetX = minOf(dp(24), (width - panelWidth).coerceAtLeast(0) / 2)
            val insetY = minOf(dp(24), (height - panelHeight).coerceAtLeast(0))
            overlay.addView(body, FrameLayout.LayoutParams(panelWidth, -2).apply {
                gravity = Gravity.TOP or if (root.fields["side"] == "left") Gravity.LEFT else Gravity.RIGHT
                leftMargin = insetX; rightMargin = insetX; topMargin = insetY
            })
            val p = params(-1, -1, focus = true).apply {
                flags = flags and (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).inv()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            // Register before adding so every failure path can remove the touch-blocking window.
            panelView = overlay
            wm().addView(overlay, p)
            overlay.requestFocus()
            panel.items.filter { it.type in setOf("text", "toggle", "list") }.forEach { item ->
                refresh(panel.id, item)
                if (item.every > 0) {
                    val gen = generation
                    val task = object : Runnable {
                        override fun run() {
                            if (generation != gen || openId != panel.id || !unlocked()) return
                            refresh(panel.id, item)
                            main.postDelayed(this, item.every * 1000)
                        }
                    }
                    scheduled.add(task); main.postDelayed(task, item.every * 1000)
                }
            }
        } catch (e: Exception) { destroy(); throw e }
    }

    fun close() = onMain {
        generation++
        scheduled.forEach { main.removeCallbacks(it) }; scheduled.clear()
        retries.values.forEach { main.removeCallbacks(it) }; retries.clear()
        // Stop hidden data producers. Explicit actions can finish (including out=notify).
        runner?.cancelReads()
        panelView?.let { runCatching { wm().removeView(it) } }
        panelView = null; openId = null; openRootId = null; renderers.clear()
    }

    private fun addItem(rows: LinearLayout, panelId: String, item: EdgeStore.Item, iconOnly: Boolean = false) {
        val target = "$panelId:${item.id}"
        val row = LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val title = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
        val pkg = packageFrom(item.command)
        val label = item.fields["label"] ?: pkg?.let { runCatching {
            val pm = app!!.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrNull() } ?: item.id
        addIcon(title, item.fields["icon"] ?: pkg?.let { "@app:$it" } ?: if (iconOnly) label.take(1) else null)
        title.minimumHeight = dp(48)
        title.contentDescription = label
        title.tooltipText = label
        if (iconOnly) title.gravity = Gravity.CENTER
        else title.addView(text(label).apply { setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        title.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            it.startDragAndDrop(null, View.DragShadowBuilder(it), ItemDrag(panelId, item.id), 0)
        }
        row.setOnDragListener { _, event ->
            val drag = event.localState as? ItemDrag
            if (drag?.panel != panelId) false else {
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_ENTERED -> row.alpha = 0.5f
                    android.view.DragEvent.ACTION_DRAG_EXITED, android.view.DragEvent.ACTION_DRAG_ENDED -> row.alpha = 1f
                    android.view.DragEvent.ACTION_DROP -> runCatching {
                        store(app!!).moveItem(panelId, drag.item, item.id,
                            after = if (iconOnly) event.x >= row.width / 2 else event.y >= row.height / 2); reload(app!!)
                    }.onFailure { fail(it) }
                }
                true
            }
        }
        row.addView(title)
        if (Regex("^z2-key\\s+(back|recents|shade|quicksettings|screenshot|split)\\s*$")
                .matches(item.command.trim()) && !AndroidActions.connected()) {
            row.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_accessibility_setup)
                setOnClickListener {
                    val context = app!!
                    close()
                    runCatching { AndroidActions.command(context, listOf("permission")) }.onFailure { fail(it) }
                }
            })
        }
        val result = text(values[target].orEmpty(), 14f).apply { setTextIsSelectable(true) }
        val status = text("", 12f).apply { visibility = View.GONE }
        renderers["$target:status"] = { status.text = it; status.visibility = if (it.isBlank()) View.GONE else View.VISIBLE }
        when (item.type) {
            "run" -> title.setOnClickListener { execute(panelId, item, item.command) }
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
            if (iconOnly && result.text.isEmpty()) result.visibility = View.GONE
            renderers[target] = { result.text = it; result.visibility = if (iconOnly && it.isEmpty()) View.GONE else View.VISIBLE }
        }
        row.addView(status)
        if (editingItems) {
            val controls = LinearLayout(ui())
            controls.addView(Button(ui()).apply {
                text = app!!.getString(R.string.edge_delete)
                setOnClickListener { runCatching { store(app!!).removeItem(target); reload(app!!) }.onFailure { fail(it) } }
            })
            if (pkg != null) {
                val choices = AppLaunch.modes
                val mode = Regex("--window\\s+(full|freeform|split|ask)").find(item.command)?.groupValues?.get(1) ?: "full"
                val picker = android.widget.Spinner(ui())
                picker.adapter = android.widget.ArrayAdapter(ui(), android.R.layout.simple_spinner_dropdown_item,
                    listOf(R.string.edge_window_full, R.string.edge_window_freeform, R.string.edge_window_split, R.string.edge_window_ask)
                        .map { app!!.getString(it) })
                picker.setSelection(choices.indexOf(mode))
                picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (choices[position] != mode) runCatching {
                            store(app!!).setItem(target, mapOf("run" to "z2-intent -p $pkg --window ${choices[position]}"))
                            reload(app!!)
                        }.onFailure { fail(it) }
                    }
                }
                controls.addView(picker, LinearLayout.LayoutParams(0, -2, 1f))
            }
            row.addView(controls)
        }
        rows.addView(row)
        if (!iconOnly) rows.addView(View(ui()).apply { setBackgroundColor(Color.GRAY) }, LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)))
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
        handles[id]?.text = value.ifBlank { if (panels.first { it.id == id }.handle == "button") "≡" else "" }
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
        "^z2-intent\\s+(?:-p|--package)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)(?:\\s+--window\\s+(?:full|freeform|split|ask))?\\s*$"
    ).matchEntire(command.trim())?.groupValues?.get(1)

    private fun fail(error: Throwable) { app?.let { Toast.makeText(it, error.message ?: "Error", Toast.LENGTH_LONG).show() } }
}
