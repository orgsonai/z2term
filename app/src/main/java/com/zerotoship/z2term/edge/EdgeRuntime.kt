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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
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
import com.zerotoship.z2term.service.ScreenTimeout
import com.zerotoship.z2term.service.TorchState
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
    private var handleContrast: EdgeHandleContrast? = null
    private val values = mutableMapOf<String, String>()
    private val badges = mutableMapOf<String, String>()
    private val renderers = mutableMapOf<String, (String) -> Unit>()
    private val revisions = mutableMapOf<String, Int>()
    private val buttonRuns = EdgeButtonRuns()
    private val nativeStateListener: () -> Unit = {
        main.post {
            if (app != null) panels.forEach { panel -> panel.items.filter { it.isStateButton }.forEach { item ->
                if (buttonSource(panel.id, item) in setOf("torch", "screen")) renderButton(panel.id, item)
            } }
        }
        Unit
    }
    private val scheduled = mutableListOf<Runnable>()
    private val retries = mutableMapOf<String, Runnable>()
    private var panelView: EdgePanelWindow? = null
    private var editorSession: EdgeEditorSession? = null
    private var snapPreview: View? = null
    private var openId: String? = null
    private var openRootId: String? = null
    private val notes = mutableMapOf<String, EdgeNote>()
    private val terminals = mutableListOf<EdgeTerminalUi>()
    @android.annotation.SuppressLint("StaticFieldLeak") // Window-scoped; disposed and cleared by clearPanel.
    private var macroForm: EdgeMacroUi? = null
    private var editingItems = false
    private var editorPage = 0
    private var cancelAppearance: (() -> Unit)? = null
    private var wakeRestore: Runnable? = null
    private var pendingScrollStart: Runnable? = null
    private var pendingActionStart: Runnable? = null
    private val actionSequence = EdgeActionSequence()
    private var actionDeadline: Runnable? = null
    private var lastScrollSpeed = 600f
    private var scrollSessionAvailable = false
    private data class ItemDrag(val panel: String, val item: String)
    private val selectedTabs = mutableMapOf<String, String>()
    private var receiver: BroadcastReceiver? = null
    private var generation = 0
    private var serviceRequested = false
    private var actionsSuspended = false
    private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(128)

    fun store(context: Context) = EdgeStore(File(context.filesDir, "shared_home/.z2term/edge"))

    /** Release overlay input without cancelling the shell command which invoked the macro. */
    internal fun suspendForActions(suspended: Boolean): Unit = onMain {
        if (suspended) check(editorSession?.hasUnsavedChanges() != true) { "Save or cancel panel edits before running a macro" }
        actionsSuspended = suspended
        if (suspended) { close(); stopHandleScroll() }
        handles.values.forEach { it.visibility = if (suspended || !unlocked()) View.GONE else View.VISIBLE }
        if (!suspended && app != null && unlocked() && handles.isEmpty()) showHandles()
    }

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
    private fun cancelWakeRestore() {
        wakeRestore?.let { main.removeCallbacks(it) }
        wakeRestore = null
    }

    /** Screen broadcasts can precede the keyguard/window transition. Retry until it settles. */
    private fun restoreAfterWake() {
        cancelWakeRestore()
        var failures = 0
        var nonInteractiveChecks = 0
        val task = object : Runnable {
            override fun run() {
                if (wakeRestore !== this) return
                val context = app ?: return cancelWakeRestore()
                if (!store(context).enabled() || !Settings.canDrawOverlays(context)) {
                    cancelWakeRestore()
                    return
                }
                if (!context.getSystemService(PowerManager::class.java).isInteractive) {
                    // Allow a short broadcast/state race; SCREEN_OFF cancels immediately.
                    if (++nonInteractiveChecks < 5) main.postDelayed(this, 250)
                    else cancelWakeRestore()
                    return
                }
                nonInteractiveChecks = 0
                if (!unlocked()) {
                    main.postDelayed(this, 1000)
                    return
                }
                try {
                    showHandles()
                    cancelWakeRestore()
                } catch (e: Exception) {
                    removeHandles()
                    if (++failures < 5) main.postDelayed(this, 500)
                    else { cancelWakeRestore(); fail(e) }
                }
            }
        }
        wakeRestore = task
        main.post(task)
    }

    private fun colors(): Pair<Int, Int> = EdgeEditorUi.surface(ui()) to EdgeEditorUi.foreground(ui())
    private fun background(round: Boolean = false) = GradientDrawable().apply {
        setColor(colors().first)
        setStroke(dp(1).coerceAtLeast(1), EdgeEditorUi.line(ui()))
        cornerRadius = dp(6).toFloat()
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
            if (handles.isEmpty()) restoreAfterWake()
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
        runCatching { TorchState.start(app!!) }
        TorchState.addListener(nativeStateListener)
        ScreenTimeout.addListener(nativeStateListener)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        cancelWakeRestore()
                        try { close() } finally { removeHandles() }
                    }
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> restoreAfterWake()
                    Intent.ACTION_CONFIGURATION_CHANGED -> if (android.os.Build.VERSION.SDK_INT < 31) rebuildWindows()
                }
                }.onFailure { fail(it) }
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
            val wasEditing = editingItems
            close(); removeHandles(); iconCache.evictAll()
            if (unlocked()) { showHandles(); if (previous != null) open(previous, settings = wasEditing) }
        }.onFailure { fail(it) }
    }

    fun on(context: Context) = onMain {
        require(Settings.canDrawOverlays(context)) { context.getString(R.string.edge_overlay_help) }
        require(unlockedContext(context)) { "Unlock the screen before enabling the panel" }
        // No panel is created here (0.8.603, the user's decision): the edge-panel guide creates the sample.
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
        cancelWakeRestore()
        close(); removeHandles()
        receiver?.let { r -> app?.let { runCatching { it.unregisterReceiver(r) } } }
        receiver = null
        runner?.cancelAll(); runner = null
        buttonRuns.clear()
        TorchState.removeListener(nativeStateListener)
        ScreenTimeout.removeListener(nativeStateListener)
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
        val wasEditing = editingItems
        clearPanel(keepWindow = unlocked() && loaded.any { it.id == previous })
        removeHandles(); iconCache.evictAll()
        panels = loaded
        val targets = panels.flatMap { p -> p.items.map { "${p.id}:${it.id}" } }.toSet()
        values.keys.retainAll(targets); revisions.keys.retainAll(targets)
        badges.keys.retainAll(panels.map { it.id }.toSet())
        if (unlocked()) {
            showHandles()
            if (panels.any { it.id == previous }) open(previous!!, settings = wasEditing)
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
        handleContrast?.dispose(); handleContrast = null
        cancelHandleActions()
        scrollSessionAvailable = false
        stopHandleScroll()
        removeSnapPreview()
        handleCallbacks.forEach { main.removeCallbacks(it) }; handleCallbacks.clear()
        handles.values.forEach { runCatching { wm().removeView(it) } }
        handles.clear()
    }

    private fun showHandles() {
        if (!unlocked() || actionsSuspended) return
        removeHandles()
        val contrastTargets = mutableListOf<EdgeHandleContrast.Target>()
        panels.filter { it.handle != "off" && panels.none { parent -> it.id in parent.tabs } }.forEach { panel ->
            val (width, height) = screenSize()
            val f = panel.fields
            val button = panel.handle == "button"
            val size = dp(if (button) (f["size"]?.toIntOrNull() ?: 48).coerceIn(32, 96)
                else (f["size"]?.toIntOrNull() ?: 6).coerceIn(2, 48))
            val w = (if (button) size else size.coerceAtLeast(dp(24))).coerceAtMost(width)
            val h = (if (button) size else (height * (f["length"]?.toFloatOrNull() ?: 6f) / 100)
                .toInt().coerceAtLeast(dp(8))).coerceAtMost(height)
            val opening = f["open"]?.takeIf { it.isNotEmpty() } ?: if (button) "tap" else "swipe"
            val p = params(w.coerceAtMost(width), h.coerceAtMost(height))
            val right = f["side"] != "left"
            p.x = if (button) ((width - w).coerceAtLeast(0) * (f["x"]?.toFloatOrNull() ?: 85f) / 100).toInt()
                else if (right) (width - w).coerceAtLeast(0) else 0
            p.y = ((height - h).coerceAtLeast(0) * (f[if (button) "y" else "offset"]?.toFloatOrNull() ?: 30f) / 100).toInt()
            var relocating = false
            var previewRight: Boolean? = null
            val barBackground = background(round = true)
            var blackBar = f["bar-color"] == "black"
            val view = object : TextView(ui()) {
                override fun onDraw(canvas: android.graphics.Canvas) {
                    if (!button) {
                        // Keep the complete shape independent of the wider touch target.
                        val visibleWidth = if (relocating) this.width else size.coerceAtMost(this.width)
                        val left = if (right && !relocating) this.width - visibleWidth else 0
                        barBackground.setColor(if (blackBar) Color.BLACK else Color.WHITE)
                        barBackground.setStroke(minOf(dp(1).coerceAtLeast(1), (visibleWidth / 4).coerceAtLeast(1)),
                            if (blackBar) Color.WHITE else Color.BLACK)
                        barBackground.cornerRadius = minOf(visibleWidth, this.height) / 2f
                        barBackground.setBounds(left, 0, left + visibleWidth, this.height)
                        barBackground.draw(canvas)
                        val save = canvas.save()
                        canvas.clipRect(left, 0, left + visibleWidth, this.height)
                        canvas.translate(left + (visibleWidth - this.width) / 2f, 0f)
                        super.onDraw(canvas)
                        canvas.restoreToCount(save)
                    } else super.onDraw(canvas)
                }
            }.apply {
                text = badges[panel.id] ?: if (button) "≡" else ""
                textSize = 16f
                setTextColor(if (button) colors().second else if (blackBar) Color.WHITE else Color.BLACK)
                background = if (button) background(true) else null; gravity = Gravity.CENTER
                alpha = f["alpha"]?.toFloatOrNull() ?: 1f
                setPadding(0, 0, 0, 0); maxLines = 2
                contentDescription = f["label"] ?: panel.id
                isClickable = true
                setOnClickListener { runHandleActions(panel, EdgeActions.Trigger.TAP, this, 0f) }
            }
            val dragMoves = button && opening == "tap"
            var startX = 0f; var startY = 0f; var originalX = 0; var originalY = 0; var moved = false
            val slop = ViewConfiguration.get(windowContext!!).scaledTouchSlop
            val gesture = EdgeHandleGesture(slop.toFloat())
            var cancelled = false
            var stopTouch = false
            var pendingTap = false
            var secondTap = false
            var tapX = 0f; var tapY = 0f
            val singleTap = Runnable {
                pendingTap = false
                view.performClick()
            }
            handleCallbacks.add(singleTap)
            val hasDoubleTap = EdgeActions.binding(f, EdgeActions.Trigger.DOUBLE_TAP).isNotEmpty()
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
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (pendingActionStart != null) cancelHandleActions()
                    pendingScrollStart?.let { stopHandleScroll() }
                    AndroidActions.outsideTouch(event)
                    return@setOnTouchListener true
                }
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    cancelled = true
                    pendingTap = false
                    main.removeCallbacks(singleTap)
                    main.removeCallbacks(longPress)
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; originalX = p.x; originalY = p.y; moved = false; relocating = false
                        gesture.reset(); cancelled = false
                        scrollSessionAvailable = scrollSessionAvailable && AndroidActions.recentlyAutoScrolling()
                        cancelHandleActions()
                        stopTouch = stopHandleScroll()
                        secondTap = pendingTap && kotlin.math.abs(event.rawX - tapX) <= slop * 2 &&
                            kotlin.math.abs(event.rawY - tapY) <= slop * 2
                        if (pendingTap) {
                            main.removeCallbacks(singleTap)
                            if (!secondTap) singleTap.run()
                            pendingTap = false
                        }
                        if (!stopTouch) main.postDelayed(longPress, 300)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (cancelled) return@setOnTouchListener true
                        val dx = event.rawX - startX; val dy = event.rawY - startY
                        if (!relocating) gesture.move(dx, dy, right)
                        if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                            moved = true; main.removeCallbacks(longPress)
                            // A tap-only handle has no swipe to protect, so dragging moves it at once.
                            if (dragMoves && !relocating && !stopTouch &&
                                    EdgeActions.binding(f, actionTrigger(gesture.kind)).isEmpty()) {
                                relocating = true; view.alpha = 1f; view.invalidate()
                            }
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
                        if (cancelled || (stopTouch && !moved)) {
                            scrollSessionAvailable = false
                            p.x = originalX; p.y = originalY
                            runCatching { wm().updateViewLayout(view, p) }
                            releaseAppearance()
                            return@setOnTouchListener true
                        }
                        if (relocating && !moved) {
                            releaseAppearance()
                            leaveEditor { open(panel.id, settings = true) }
                        } else if (!button && relocating) {
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
                            val kind = gesture.move(dx, dy, right)
                            if (kind == EdgeHandleGesture.Kind.TAP && hasDoubleTap && !editingItems && cancelAppearance == null) {
                                if (secondTap) runHandleActions(panel, EdgeActions.Trigger.DOUBLE_TAP, view, 0f)
                                else {
                                    tapX = event.rawX; tapY = event.rawY; pendingTap = true
                                    main.postDelayed(singleTap, ViewConfiguration.getDoubleTapTimeout().toLong())
                                }
                            } else if (kind == EdgeHandleGesture.Kind.TAP) view.performClick()
                            else {
                                val density = windowContext!!.resources.displayMetrics.density
                                val displacement = if (kind in setOf(EdgeHandleGesture.Kind.INWARD, EdgeHandleGesture.Kind.OUTWARD))
                                    -kotlin.math.abs(dx) / density else dy / density
                                val speedDistance = if (kotlin.math.abs(displacement) > slop / density)
                                    displacement - kotlin.math.sign(displacement) * slop / density else 0f
                                runHandleActions(panel, actionTrigger(kind), view, speedDistance)
                            }
                        }
                        releaseAppearance()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { main.removeCallbacks(longPress)
                        main.removeCallbacks(singleTap); pendingTap = false
                        if (moved || relocating) {
                        p.x = originalX; p.y = originalY
                        runCatching { wm().updateViewLayout(view, p) }
                    }; releaseAppearance(); true }
                    else -> false
                }
            }
            wm().addView(view, p)
            handles[panel.id] = view
            if (!button && f["bar-color"].orEmpty() in setOf("", "auto")) {
                contrastTargets.add(EdgeHandleContrast.Target(area = {
                    if (!view.isAttachedToWindow || !view.isShown || relocating || view.width == 0 || view.height == 0) null
                    else {
                        val location = IntArray(2).also(view::getLocationOnScreen)
                        val visibleWidth = size.coerceAtMost(view.width)
                        val gap = dp(2).coerceAtLeast(1)
                        val strip = dp(4).coerceAtLeast(1)
                        // Exclude the bar itself so sampling cannot alternate its own colour.
                        val x = if (right) location[0] + view.width - visibleWidth - gap - strip
                            else location[0] + visibleWidth + gap
                        android.graphics.Rect(x, location[1], x + strip, location[1] + view.height)
                    }
                }, paint = { black ->
                    blackBar = black
                    view.setTextColor(if (black) Color.WHITE else Color.BLACK)
                    view.invalidate()
                }))
            }
        }
        if (contrastTargets.isNotEmpty()) {
            handleContrast = EdgeHandleContrast(contrastTargets) {
                unlocked() && !actionsSuspended && panelView == null
            }.also { it.refresh() }
        }
    }

    internal fun refreshHandleContrast(): Unit = onMain { handleContrast?.refresh(); Unit }

    private fun leaveEditor(action: () -> Unit) {
        val guarded = { runCatching(action).onFailure { fail(it) }; Unit }
        val session = editorSession
        if (session != null) session.leave(action = guarded) else guarded()
    }

    private fun actionTrigger(kind: EdgeHandleGesture.Kind): EdgeActions.Trigger = when (kind) {
        EdgeHandleGesture.Kind.TAP -> EdgeActions.Trigger.TAP
        EdgeHandleGesture.Kind.UP -> EdgeActions.Trigger.UP
        EdgeHandleGesture.Kind.DOWN -> EdgeActions.Trigger.DOWN
        EdgeHandleGesture.Kind.INWARD -> EdgeActions.Trigger.INWARD
        EdgeHandleGesture.Kind.OUTWARD -> EdgeActions.Trigger.OUTWARD
    }

    private fun cancelHandleActions() {
        pendingActionStart?.let(main::removeCallbacks); pendingActionStart = null
        actionDeadline?.let(main::removeCallbacks); actionDeadline = null
        actionSequence.cancel()
    }

    private fun runHandleActions(panel: EdgeStore.Panel, trigger: EdgeActions.Trigger, view: View, displacement: Float) {
        if (!unlocked() || actionsSuspended || editingItems || cancelAppearance != null) return
        val actions = EdgeActions.binding(panel.fields, trigger)
        if (actions.isEmpty()) return
        cancelHandleActions()
        val task = Runnable {
            pendingActionStart = null
            if (!unlocked() || view !in handles.values) return@Runnable
            val deadline = Runnable {
                if (actionSequence.running) {
                    cancelHandleActions()
                    stopHandleScroll(); scrollSessionAvailable = false
                    fail(IllegalStateException(app!!.getString(R.string.edge_action_timeout)))
                }
            }
            actionDeadline = deadline
            main.postDelayed(deadline, 180000)
            actionSequence.start(actions, { action, done ->
                check(unlocked()) { "Unlock the screen before running actions" }
                executeHandleAction(panel, trigger, view, displacement, action, done)
            }, { error ->
                stopHandleScroll(); scrollSessionAvailable = false
                fail(IllegalStateException(error))
            })
        }
        pendingActionStart = task
        main.postDelayed(task, 80)
    }

    private fun executeHandleAction(panel: EdgeStore.Panel, trigger: EdgeActions.Trigger, view: View,
        displacement: Float, action: EdgeActions.Action, done: (String?) -> Unit): (() -> Unit)? {
        check(!actionsSuspended) { "An action macro is running" }
        val type = action.type
        if (type !in setOf(EdgeActions.Type.PANEL, EdgeActions.Type.WAIT)) close()
        when (type) {
            EdgeActions.Type.PANEL -> { open(panel.id, toggle = true); done(null) }
            EdgeActions.Type.WAIT -> {
                val wait = Runnable { done(null) }
                main.postDelayed(wait, action.argument.toLong())
                return { main.removeCallbacks(wait) }
            }
            EdgeActions.Type.COMMAND -> {
                val key = "handle:${panel.id}"
                check(runner!!.run(key, action.argument, 30) { done(it.error) }) {
                    app!!.getString(R.string.edge_busy)
                }
                return { runner?.cancelJob(key) }
            }
            EdgeActions.Type.LAUNCH -> {
                val intent = app!!.packageManager.getLaunchIntentForPackage(action.argument)
                    ?: error(app!!.getString(R.string.edge_action_no_app))
                app!!.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); done(null)
            }
            EdgeActions.Type.BACK, EdgeActions.Type.HOME, EdgeActions.Type.RECENTS, EdgeActions.Type.SHADE -> {
                AndroidActions.command(app!!, listOf(type.id)); done(null)
            }
            EdgeActions.Type.SCROLL_STOP -> { stopHandleScroll(); scrollSessionAvailable = false; done(null) }
            else -> {
                if (type == EdgeActions.Type.SCROLL_VARIABLE && displacement == 0f &&
                    trigger !in setOf(EdgeActions.Trigger.TAP, EdgeActions.Trigger.DOUBLE_TAP)) {
                    done(null); return null
                }
                val maximum = panel.fields["gesture-speed"]?.toIntOrNull() ?: 600
                val sign = if (trigger == EdgeActions.Trigger.DOWN) 1f else -1f
                val speed = when (type) {
                    EdgeActions.Type.SCROLL_FASTER -> lastScrollSpeed * 1.5f
                    EdgeActions.Type.SCROLL_SLOWER -> lastScrollSpeed / 1.5f
                    EdgeActions.Type.SCROLL_REVERSE -> -lastScrollSpeed
                    EdgeActions.Type.SCROLL_VARIABLE -> if (displacement != 0f)
                        EdgeHandleGesture.scrollSpeed(displacement, 0f, maximum, true,
                            panel.fields["gesture-range"]?.toFloatOrNull() ?: 160f) else sign * maximum
                    EdgeActions.Type.SWIPE_DOWN -> maximum.toFloat()
                    EdgeActions.Type.SWIPE_UP -> -maximum.toFloat()
                    else -> sign * maximum
                }
                if (type in setOf(EdgeActions.Type.SCROLL_FASTER, EdgeActions.Type.SCROLL_SLOWER,
                        EdgeActions.Type.SCROLL_REVERSE) && !scrollSessionAvailable) { done(null); return null }
                val bounded = kotlin.math.sign(speed) * kotlin.math.abs(speed).coerceIn(2.5f, 40000f)
                startHandleScroll(view, bounded, panel.fields,
                    once = type in setOf(EdgeActions.Type.SWIPE_UP, EdgeActions.Type.SWIPE_DOWN),
                    requirePreviousTarget = type in setOf(EdgeActions.Type.SCROLL_FASTER,
                        EdgeActions.Type.SCROLL_SLOWER, EdgeActions.Type.SCROLL_REVERSE), completed = done)
                return { stopHandleScroll() }
            }
        }
        return null
    }

    private fun stopHandleScroll(): Boolean {
        val pending = pendingScrollStart != null
        pendingScrollStart?.let(main::removeCallbacks)
        pendingScrollStart = null
        return AndroidActions.stopAutoScroll() || pending
    }

    private fun startHandleScroll(view: View, speed: Float, fields: Map<String, String> = emptyMap(),
        once: Boolean = false, requirePreviousTarget: Boolean = false, completed: (String?) -> Unit = {}) {
        stopHandleScroll()
        val task = Runnable {
            pendingScrollStart = null
            if (!unlocked() || view !in handles.values || editingItems || cancelAppearance != null) {
                completed("Scroll target is no longer available"); return@Runnable
            }
            runCatching {
                check(AndroidActions.connected()) { app!!.getString(R.string.edge_accessibility_help) }
                val (width, height) = screenSize()
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val layout = view.layoutParams as WindowManager.LayoutParams
                val originX = location[0] - layout.x
                val originY = location[1] - layout.y
                AndroidActions.startAutoScroll(speed,
                    android.graphics.Rect(originX, originY, originX + width, originY + height),
                    fields["scroll-x"]?.toFloatOrNull() ?: 50f,
                    fields["scroll-y"]?.toFloatOrNull() ?: 50f, once, requirePreviousTarget) { error ->
                    if (once) completed(error)
                    else if (error != null) fail(IllegalStateException(error))
                }
                lastScrollSpeed = speed
                scrollSessionAvailable = !once
                if (!once) completed(null)
                if (!once) Toast.makeText(ui(), app!!.getString(R.string.edge_scroll_started,
                    kotlin.math.abs(speed).toInt()), Toast.LENGTH_LONG).show()
            }.onFailure { completed(it.message ?: "Scroll failed") }
        }
        pendingScrollStart = task
        // Release the physical touch before dispatchGesture, which otherwise cancels it.
        main.postDelayed(task, 80)
    }

    fun open(id: String, toggle: Boolean = false, tabId: String? = null, settings: Boolean = false,
        page: Int = editorPage): Unit = onMain {
        check(!actionsSuspended) { "An action macro is running; stop it before opening the panel" }
        if (toggle && openRootId == id) {
            if (terminals.isEmpty() && macroForm == null) close()
            return@onMain
        }
        require(app != null && store(app!!).enabled()) { "Enable the panel first: z2-edge on" }
        require(unlocked()) { "Unlock the screen before opening the panel" }
        stopHandleScroll()
        val requested = panels.firstOrNull { it.id == id } ?: throw IllegalArgumentException("No panel: $id")
        val root = panels.firstOrNull { id in it.tabs } ?: requested
        val active = tabId ?: if (root.id != id) id else selectedTabs[root.id]
        val panel = panels.firstOrNull { it.id == active && (it.id == root.id || it.id in root.tabs) } ?: root
        val terminalMode = !settings && panel.items.any { it.type in EdgePanelLayout.interactiveTypes }
        val boundedWindow = !settings && EdgePanelLayout.bounded(root, panels)
        val showTabBar = settings || root.fields["tabbar"] == "on" ||
            (root.fields["tabbar"] == "auto" && root.tabs.isNotEmpty())
        val hasNavigation = showTabBar || root.tabs.isNotEmpty()
        val wantsClose = !settings && (boundedWindow || root.fields["close"] == "on")
        val panelTitle = root.fields["label"].orEmpty().takeIf { root.fields["title"] == "on" }.orEmpty()
        val inlineClose = wantsClose && !hasNavigation && panelTitle.isBlank() &&
            panel.items.any { it.type in setOf("terminal", "macro", "result") }
        val closeAction: () -> Unit = { editorSession?.leave { close() } ?: close() }
        val existingWindow = panelView
        val previousTabScroll = if (openRootId == root.id && editingItems == settings)
            existingWindow?.findViewWithTag<android.widget.HorizontalScrollView>("edge-tabs")?.scrollX ?: 0 else 0
        clearPanel(keepWindow = existingWindow != null)
        val session = EdgeEditorSession(ui())
        editorSession = session
        editingItems = settings
        editorPage = page
        selectedTabs[root.id] = panel.id
        openRootId = root.id
        openId = panel.id
        if (!settings && panel.items.any { it.type in setOf("macro", "argument", "result") })
            macroForm = EdgeMacroUi(ui(), panel, runner!!,
                closeAction.takeIf { inlineClose && panel.items.none { item -> item.type == "terminal" } })
        try {
            val (width, height) = screenSize()
            val density = windowContext!!.resources.displayMetrics.density
            val panelWidth = if (settings) width else EdgeStore.dimensionPixels(root.fields["width"] ?: "360", width, density)
                .coerceAtLeast(if (root.fields["add"] == "on" || root.fields["settings"] == "on") minOf(width, dp(48)) else 1)
            val panelHeight = if (settings) height else EdgeStore.dimensionPixels(root.fields["height"] ?: "72%", height, density)
            val body = object : LinearLayout(ui()) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val limit = minOf(panelHeight, View.MeasureSpec.getSize(heightMeasureSpec))
                    super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, if (settings || boundedWindow || root.tabs.isNotEmpty() || root.fields["fit"] == "fixed") View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST))
                }
            }.apply {
                orientation = LinearLayout.VERTICAL; isClickable = true
                background = if (settings) ColorDrawable(EdgeSettingsUi.canvas(ui())) else background()
                minimumHeight = dp(48)
                if (!settings) setOnLongClickListener {
                    runCatching { open(root.id, tabId = panel.id, settings = true) }.onFailure { fail(it) }; true
                }
            }
            if (settings) {
                val bar = LinearLayout(ui()).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(EdgeSettingsUi.GUTTER), dp(10), dp(10), dp(10))
                }
                bar.addView(EdgeSettingsUi.title(ui(),
                    app!!.getString(R.string.edge_edit_panel, root.fields["label"] ?: root.id)),
                    LinearLayout.LayoutParams(0, -2, 1f))
                bar.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_done)) {
                    session.leave { open(root.id, tabId = panel.id) }
                }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
                body.addView(bar)
                // The page is the first decision on this screen, so it sits on the rule under the title.
                val navigation = LinearLayout(ui())
                listOf(R.string.edge_page_items, R.string.edge_page_appearance, R.string.edge_page_manage).forEachIndexed { index, label ->
                    navigation.addView(EdgeSettingsUi.pageTab(ui(), app!!.getString(label), page == index) {
                        if (page != index) session.leave { open(root.id, tabId = panel.id, settings = true, page = index) }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
                body.addView(FrameLayout(ui()).apply {
                    addView(EdgeSettingsUi.hairline(ui()),
                        FrameLayout.LayoutParams(-1, dp(1).coerceAtLeast(1), Gravity.BOTTOM))
                    addView(navigation, FrameLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(-1, -2))
            } else if (panelTitle.isNotBlank()) {
                val header = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
                header.addView(text(panelTitle).apply { setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
                if (wantsClose && !hasNavigation) header.addView(EdgePanelControls.close(ui(), closeAction))
                body.addView(header)
            }
            val tabs = LinearLayout(ui()).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (settings) setPadding(dp(EdgeSettingsUi.GUTTER), dp(10), dp(EdgeSettingsUi.GUTTER), dp(10))
            }
            (listOf(root.id) + root.tabs).forEach { childId ->
                val child = panels.first { it.id == childId }
                val name = child.fields["label"] ?: child.id
                val select = {
                    if (childId != panel.id) session.leave { runCatching { open(root.id, tabId = childId, settings = settings) }.onFailure { fail(it) } }
                }
                tabs.addView(
                    if (settings) EdgeSettingsUi.chip(ui(), name, childId == panel.id, select)
                    else EdgeEditorUi.button(ui(), name, childId == panel.id, select).apply {
                        // Selection changes colour, not text metrics or neighbouring tab positions.
                        setTypeface(null, Typeface.NORMAL)
                        setSingleLine(true)
                    },
                    LinearLayout.LayoutParams(-2, -2).apply { if (settings) rightMargin = dp(8) })
            }
            val tabEntry = LinearLayout(ui()).apply {
                visibility = View.GONE; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(EdgeSettingsUi.GUTTER), 0, dp(EdgeSettingsUi.GUTTER), dp(12))
            }
            val tabName = EdgeSettingsUi.field(ui()).apply { hint = app!!.getString(R.string.edge_tab_name) }
            tabEntry.addView(tabName, LinearLayout.LayoutParams(0, -2, 1f))
            tabEntry.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add),
                EdgeSettingsUi.Kind.PRIMARY) {
                session.leave(except = tabName) { runCatching {
                    val name = tabName.text.toString().trim(); require(name.isNotEmpty()) { "Enter a tab name" }
                    val childId = "tab_" + java.util.UUID.randomUUID().toString().replace("-", "")
                    store(app!!).addTab(root.id, childId, name)
                    selectedTabs[root.id] = childId; reload(app!!)
                }.onFailure { fail(it) } }
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            if (settings && page == 2) tabs.addView(EdgeSettingsUi.button(ui(),
                app!!.getString(R.string.edge_add_tab)) {
                tabEntry.visibility = if (tabEntry.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }.apply {
                textSize = 13f; minHeight = dp(36); minimumHeight = dp(36); setPadding(dp(12), 0, dp(12), 0)
            })
            val navigation = LinearLayout(ui()).apply {
                gravity = Gravity.CENTER_VERTICAL
                tag = "edge-navigation"
            }
            if (showTabBar) {
                navigation.addView(android.widget.HorizontalScrollView(ui()).apply {
                    tag = "edge-tabs"
                    isHorizontalScrollBarEnabled = false; addView(tabs)
                    var restored = false
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        if (!restored) { restored = true; scrollTo(previousTabScroll, 0) }
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            } else if (root.tabs.isNotEmpty()) {
                val choices = (listOf(root.id) + root.tabs).map { id -> panels.first { it.id == id } }
                navigation.addView(android.widget.Spinner(ui()).apply {
                    contentDescription = app!!.getString(R.string.edge_select_tab)
                    adapter = android.widget.ArrayAdapter(ui(), android.R.layout.simple_spinner_dropdown_item,
                        choices.map { it.fields["label"] ?: it.id })
                    setSelection(choices.indexOfFirst { it.id == panel.id })
                    minimumHeight = dp(48)
                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val selected = choices[position].id
                            if (selected != panel.id) runCatching { open(root.id, tabId = selected) }.onFailure { fail(it) }
                        }
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (hasNavigation) {
                if (wantsClose) navigation.addView(EdgePanelControls.close(ui(), closeAction))
                body.addView(navigation)
                if (settings) body.addView(EdgeSettingsUi.hairline(ui()))
            }
            if (settings) {
                session.track(tabName) { tabName.text.isNotEmpty() }
                body.addView(tabEntry)
            }
            val tools = EdgeToolRow(ui(), compact = !settings).apply {
                gravity = if (settings) Gravity.START else Gravity.END
                if (settings) setPadding(dp(EdgeSettingsUi.GUTTER), dp(12), dp(EdgeSettingsUi.GUTTER), dp(2))
            }
            if (settings || root.fields["add"] == "on") {
                // + picks an app and nothing else. Sending it through settings first made it a
                // second way into the same screen as the gear, so neither button said what it did.
                val pick = {
                    session.leave {
                        val context = app!!
                        close()
                        runCatching { context.startActivity(Intent(context, AppPickerActivity::class.java)
                            .putExtra("panel", panel.id).putExtra("editPanel", settings)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { fail(it) }
                    }
                }
                tools.addView(if (settings)
                    EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add_app), action = pick).apply {
                        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
                    }
                else EdgeEditorUi.button(ui(), "＋", action = pick).apply {
                    // The two glyph tools sit side by side: + is the accented action, the gear the
                    // quiet way out. Weight and colour carry that, since neither can carry a label.
                    contentDescription = app!!.getString(R.string.edge_pick_app)
                    textSize = 17f; setTypeface(null, Typeface.BOLD)
                    setTextColor(EdgeEditorUi.accent(ui()))
                    minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(48))
                })
            }
            if (!settings && root.fields["settings"] == "on") tools.addView(EdgeEditorUi.button(ui(), "") {}.apply {
                text = "⚙"; contentDescription = app!!.getString(R.string.edge_settings)
                textSize = 15f; setTextColor(EdgeEditorUi.muted(ui()))
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(dp(32), dp(48))
                setOnClickListener { open(root.id, tabId = panel.id, settings = true) }
            })
            if (settings) tools.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add_note)) {
                session.leave { runCatching {
                    val noteId = "note_" + java.util.UUID.randomUUID().toString().replace("-", "")
                    store(app!!).setItem("${panel.id}:$noteId", mapOf("type" to "note", "label" to app!!.getString(R.string.edge_note)))
                    reload(app!!)
                }.onFailure { fail(it) } }
            })
            if (settings) tools.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add_terminal)) {
                session.leave { runCatching {
                    val terminalId = "terminal_" + java.util.UUID.randomUUID().toString().replace("-", "")
                    store(app!!).setItem("${panel.id}:$terminalId", mapOf("type" to "terminal", "label" to app!!.getString(R.string.edge_terminal)))
                    reload(app!!)
                }.onFailure { fail(it) } }
            })
            if (settings) tools.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add_macro_form)) {
                session.leave { runCatching {
                    store(app!!).addMacroForm(panel.id, listOf(R.string.edge_type_argument, R.string.edge_type_macro,
                        R.string.edge_type_result).map { app!!.getString(it) })
                    reload(app!!)
                }.onFailure { fail(it) } }
            })
            if (settings) tools.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_add_translation)) {
                session.leave { runCatching {
                    EdgeTranslationTemplate.add(ui(), store(app!!), panel.id)
                    Toast.makeText(ui(), R.string.edge_translation_setup, Toast.LENGTH_LONG).show()
                    reload(app!!)
                }.onFailure { fail(it) } }
            })
            if (!settings && wantsClose && !hasNavigation && panelTitle.isBlank() && !inlineClose)
                tools.addView(EdgePanelControls.close(ui(), closeAction))
            if (!settings && hasNavigation && tools.childCount > 0)
                navigation.addView(tools, (navigation.childCount - if (wantsClose) 1 else 0).coerceAtLeast(0))
            val terminalOnly = !settings && panel.items.size == 1 && panel.items[0].type == "terminal"
            val closeTerminal = panel.items.firstOrNull { it.type == "terminal" }?.id
            fun terminalClose(item: EdgeStore.Item): (() -> Unit)? =
                closeAction.takeIf { inlineClose && item.id == closeTerminal }
            val rows = LinearLayout(ui()).apply {
                orientation = LinearLayout.VERTICAL
                if (settings) setPadding(0, 0, 0, dp(24))
            }
            val scroll = ScrollView(ui()).apply { isFillViewport = false; tag = "edge-items-scroll" }
            if (!terminalOnly) scroll.addView(rows)
            if (!settings) scroll.setOnLongClickListener {
                runCatching { open(root.id, tabId = panel.id, settings = true) }.onFailure { fail(it) }
                true
            }
            body.addView(if (terminalOnly) rows else scroll,
                LinearLayout.LayoutParams(-1, if (settings || terminalOnly) 0 else -2, 1f))
            if (!settings) rows.setOnLongClickListener {
                runCatching { open(root.id, tabId = panel.id, settings = true) }.onFailure { fail(it) }; true
            }
            if (settings && page == 0) {
                rows.addView(tools)
                rows.addView(EdgeItemEditor.create(ui(), panel.id, null, store(app!!),
                    beforeSave = { saveNotes() }, saved = { reload(app!!) }, session = session))
            }
            if (settings && page == 1) {
                scroll.visibility = View.GONE
                body.addView(EdgeAppearanceEditor.create(ui(), root, store(app!!), width, height, preview = { draft ->
                val fields = root.fields + draft
                val original = panels
                cancelAppearance = { if (unlocked()) showHandles() }
                try {
                    panels = original.map { if (it.id == root.id) it.copy(fields = fields) else it }
                    showHandles()
                    // A preview must not persist position changes through handle dragging.
                    handles.values.forEach { it.setOnTouchListener { _, _ -> true } }
                } finally { panels = original }
                }, finish = { reload(app!!) }, session = session), LinearLayout.LayoutParams(-1, 0, 1f))
            }
            if (settings && page == 2) rows.addView(EdgePanelEditor.create(ui(), root, panel, store(app!!), session, remove = { id ->
                close() // Flush notes before removing their owning directory.
                store(app!!).removePanel(id)
                val remaining = store(app!!).panels()
                if (remaining.isEmpty()) off(app!!)
                else {
                    reload(app!!)
                    open(remaining.first { candidate -> remaining.none { candidate.id in it.tabs } }.id, settings = true)
                }
            }) { id ->
                close()
                reload(app!!)
                open(id, settings = true)
            })
            val iconSize = root.fields["icon-size"]?.toIntOrNull() ?: 40
            val flow = root.fields["flow"]?.takeIf { it.isNotEmpty() } ?: if (panel.fields["layout"] == "grid") "grid" else "vertical"
            val labels = root.fields["labels"].orEmpty()
            val iconOnly = labels == "off" || (labels.isEmpty() && panel.fields["layout"] == "grid")
            if (settings) {
                if (page == 0) {
                    panel.items.forEach { addSettingsItem(rows, panel.id, it) }
                    rows.addView(EdgeSettingsUi.hairline(ui()))
                    // The list is what the page is for; its explanation waits at the foot.
                    fun footnote(message: Int) = rows.addView(EdgeSettingsUi.note(ui(), app!!.getString(message)),
                        LinearLayout.LayoutParams(-1, -2).apply {
                            setMargins(dp(EdgeSettingsUi.GUTTER), dp(14), dp(EdgeSettingsUi.GUTTER), 0)
                        })
                    if (panel.items.isEmpty()) footnote(R.string.edge_empty)
                    footnote(R.string.edge_items_help)
                }
            } else if (terminalOnly) {
                if (tools.parent == null && tools.childCount > 0) rows.addView(tools)
                addItem(rows, panel.id, panel.items[0], fillSpace = true,
                    inlineClose = terminalClose(panel.items[0]))
            } else if (flow == "grid") {
                val columns = (root.fields["columns"]?.toIntOrNull()
                    ?: (panelWidth / dp(iconSize + 24).coerceAtLeast(1))).coerceIn(1, 16)
                val legacyGrid = root.fields["flow"].isNullOrEmpty()
                val gridItems = if (legacyGrid) panel.items.filter { it.type == "run" } else panel.items
                gridItems.chunked(columns).forEach { group ->
                    val line = LinearLayout(ui())
                    rows.addView(line)
                    group.forEach { item ->
                        val cell = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
                        line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                        addItem(cell, panel.id, item, iconOnly, iconSize, horizontalOrder = true, inlineClose = terminalClose(item))
                    }
                    repeat(columns - group.size) { line.addView(View(ui()), LinearLayout.LayoutParams(0, 1, 1f)) }
                }
                if (legacyGrid) panel.items.filter { it.type != "run" }.forEach { addItem(rows, panel.id, it, labels == "off", iconSize, inlineClose = terminalClose(it)) }
            } else if (flow == "horizontal") {
                val line = LinearLayout(ui())
                rows.addView(android.widget.HorizontalScrollView(ui()).apply { addView(line) })
                panel.items.forEach { item ->
                    val cell = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
                    line.addView(cell, LinearLayout.LayoutParams(if (item.type == "run" && iconOnly) dp(iconSize + 24) else dp(240), -2))
                    addItem(cell, panel.id, item, iconOnly, iconSize, horizontalOrder = true, inlineClose = terminalClose(item))
                }
            } else panel.items.forEach { addItem(rows, panel.id, it, iconOnly, iconSize,
                inlineClose = terminalClose(it)) }
            if (!settings && tools.parent == null && tools.childCount > 0) rows.addView(tools)
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
            val overlay = (existingWindow ?: EdgePanelWindow(ui())).apply {
                outside = { if (!terminalMode) session.leave { close() } }
                back = { if (!terminalMode) session.leave { if (settings) open(root.id, tabId = panel.id) else close() } }
                swipeArea = if (settings || root.tabs.isEmpty()) null else body
                horizontalTabSwipe = flow != "horizontal"
                changeTab = { forward ->
                    val ids = listOf(root.id) + root.tabs
                    val index = ids.indexOf(panel.id) + if (forward) 1 else -1
                    if (index in ids.indices) session.leave {
                        runCatching { open(root.id, tabId = ids[index]) }.onFailure { fail(it) }
                    }
                }
                setOnClickListener { if (!terminalMode) session.leave { close() } }
                setOnLongClickListener(if (settings) null else View.OnLongClickListener {
                    runCatching { open(root.id, tabId = panel.id, settings = true) }.onFailure { fail(it) }
                    true
                })
                contentAlignment?.let { removeOnLayoutChangeListener(it) }
                removeAllViews()
            }
            overlay.addView(body, FrameLayout.LayoutParams(if (settings || boundedWindow) -1 else panelWidth, if (boundedWindow) -1 else -2).apply {
                val position = if (settings || boundedWindow) 0f to 0f else EdgePanelPosition.fractions(root.fields)
                gravity = Gravity.TOP or Gravity.LEFT
                // Use measured content size, including changes when the keyboard appears.
                val align = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    body.translationX = (overlay.width - body.width).coerceAtLeast(0) * position.first
                    body.translationY = (overlay.height - overlay.paddingBottom - body.height).coerceAtLeast(0) * position.second
                }
                body.addOnLayoutChangeListener(align)
                overlay.contentAlignment = align
                overlay.addOnLayoutChangeListener(align)
            })
            val p = params(if (boundedWindow) panelWidth else -1, if (boundedWindow) panelHeight else -1, focus = true).apply {
                if (boundedWindow) {
                    val position = EdgePanelPosition.fractions(root.fields)
                    x = ((width - panelWidth).coerceAtLeast(0) * position.first).toInt()
                    y = ((height - panelHeight).coerceAtLeast(0) * position.second).toInt()
                } else flags = flags and (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).inv()
                softInputMode = if (android.os.Build.VERSION.SDK_INT >= 30)
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                if (android.os.Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(
                    android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            }
            // Register before adding so every failure path can remove the touch-blocking window.
            panelView = overlay
            // Keep the window frame and navigation anchored. IME overlap consumes content
            // space at the bottom, without moving or repeatedly resizing the window itself.
            if (existingWindow == null) overlay.setPadding(0, 0, 0, 0)
            if (existingWindow == null) wm().addView(overlay, p) else wm().updateViewLayout(overlay, p)
            overlay.requestFocus()
            overlay.requestApplyInsets()
            if (!settings && panel.items.any { it.type == "note" }) {
                val autosave = object : Runnable {
                    override fun run() {
                        if (openId != panel.id) return
                        saveNotes(); main.postDelayed(this, 10000)
                    }
                }
                scheduled.add(autosave); main.postDelayed(autosave, 10000)
            }
            panel.items.filter { !settings && (it.type in setOf("text", "toggle", "list") || it.isStateButton) }.forEach { item ->
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

    private fun saveNotes() {
        notes.values.forEach { note ->
            if (note.needsSave) runCatching { note.save() }.onFailure { error ->
                if (!note.dirty) fail(error) else runCatching {
                    val backup = note.recover(File(store(app!!).root, ".recovery"))
                    fail(IllegalStateException("${error.message}. Saved: ~/.z2term/edge/.recovery/${backup.name}"))
                }.onFailure { fail(it) }
            }
        }
    }

    fun close() = onMain { clearPanel(keepWindow = false) }

    private fun clearPanel(keepWindow: Boolean) {
        editorSession?.dispose()
        editorSession = null
        panelView?.hideKeyboard()
        val cancel = cancelAppearance
        cancelAppearance = null
        runCatching { cancel?.invoke() }.onFailure { fail(it) }
        saveNotes()
        notes.entries.removeAll { !it.value.needsSave }
        terminals.forEach { it.dispose() }; terminals.clear()
        macroForm?.dispose(); macroForm = null
        generation++
        scheduled.forEach { main.removeCallbacks(it) }; scheduled.clear()
        retries.values.forEach { main.removeCallbacks(it) }; retries.clear()
        // Stop hidden data producers. Explicit actions can finish (including out=notify).
        runner?.cancelReads()
        if (!keepWindow) {
            panelView?.let { runCatching { wm().removeView(it) } }
            panelView = null
        }
        openId = null; openRootId = null; editingItems = false; renderers.clear()
    }

    private fun addSettingsItem(rows: LinearLayout, panelId: String, item: EdgeStore.Item) {
        val pkg = packageFrom(item.command)
        val row = LinearLayout(ui()).apply { orientation = LinearLayout.VERTICAL }
        rows.addView(EdgeSettingsUi.hairline(ui()))
        val heading = LinearLayout(ui()).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(EdgeSettingsUi.GUTTER), dp(6), dp(6), dp(6))
            background = EdgeSettingsUi.ripple(ui(), null)
        }
        addIcon(heading, item.fields["icon"] ?: pkg?.let { "@app:$it" } ?: "≡", 32)
        val name = item.fields["label"]?.takeIf { it.isNotBlank() }
            ?: app!!.getString(EdgeSettingsUi.typeLabel(item.type))
        // Name over kind: two items called the same still read apart.
        heading.addView(LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            addView(EdgeSettingsUi.body(ui(), name).apply {
                setTypeface(null, Typeface.BOLD); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(EdgeSettingsUi.caption(ui(), app!!.getString(EdgeSettingsUi.typeLabel(item.type)))
                .apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(heading)
        addItemEditControls(row, heading, panelId, item)
        row.setOnDragListener { _, event ->
            val drag = event.localState as? ItemDrag
            if (drag?.panel != panelId) false else {
                if (event.action == android.view.DragEvent.ACTION_DROP) editorSession?.leave { runCatching {
                    store(app!!).moveItem(panelId, drag.item, item.id, event.y >= row.height / 2)
                    reload(app!!)
                }.onFailure { fail(it) } }
                true
            }
        }
        rows.addView(row)
    }

    private fun addItemEditControls(row: LinearLayout, heading: LinearLayout, panelId: String, item: EdgeStore.Item) {
        val items = panels.first { it.id == panelId }.items
        val index = items.indexOfFirst { it.id == item.id }
        fun removeItem() {
            val scrollY = panelView?.findViewWithTag<ScrollView>("edge-items-scroll")?.scrollY ?: 0
            store(app!!).removeItem("$panelId:${item.id}")
            reload(app!!)
            panelView?.findViewWithTag<ScrollView>("edge-items-scroll")?.let { scroll ->
                scroll.post { scroll.scrollTo(0, scrollY) }
            }
        }
        val details = LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setBackgroundColor(EdgeSettingsUi.surface(ui()))
        }
        fun edit() {
            if (details.visibility == View.VISIBLE) return
            details.removeAllViews()
            details.addView(EdgeItemEditor.create(ui(), panelId, item, store(app!!),
                beforeSave = { saveNotes() }, saved = { reload(app!!) }, expanded = true,
                cancelled = { details.removeAllViews(); details.visibility = View.GONE },
                onDelete = ::removeItem, session = editorSession!!))
            details.visibility = View.VISIBLE
        }
        heading.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_edit), action = ::edit))
        val confirmDelete = LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            tag = "edge-delete-confirm:${item.id}"
            setPadding(dp(EdgeSettingsUi.GUTTER), dp(8), dp(EdgeSettingsUi.GUTTER), dp(8))
        }
        val itemName = item.fields["label"]?.takeIf { it.isNotBlank() }
            ?: app!!.getString(EdgeSettingsUi.typeLabel(item.type))
        confirmDelete.addView(EdgeSettingsUi.body(ui(), app!!.getString(R.string.edge_delete_item_warning, itemName)))
        val choices = EdgeSettingsUi.row(ui())
        choices.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_delete), EdgeSettingsUi.Kind.DANGER) {
            editorSession?.leave { runCatching(::removeItem).onFailure { fail(it) } }
        }.apply { tag = "edge-delete-accept:${item.id}" }, LinearLayout.LayoutParams(0, -2, 1f))
        choices.addView(EdgeSettingsUi.button(ui(), app!!.getString(android.R.string.cancel)) {
            confirmDelete.visibility = View.GONE
        }.apply { tag = "edge-delete-cancel:${item.id}" }, LinearLayout.LayoutParams(0, -2, 1f))
        confirmDelete.addView(choices)
        heading.addView(EdgeSettingsUi.button(ui(), app!!.getString(R.string.edge_delete), EdgeSettingsUi.Kind.DANGER) {
            confirmDelete.visibility = if (confirmDelete.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply {
            tag = "edge-delete:${item.id}"
            contentDescription = "${app!!.getString(R.string.edge_delete)}: $itemName"
            setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        listOf(-1 to R.string.edge_move_up, 1 to R.string.edge_move_down).forEach { (delta, label) ->
            heading.addView(EdgeSettingsUi.iconButton(ui(), if (delta < 0) "↑" else "↓") {
                editorSession?.leave { runCatching {
                    val current = store(app!!).panel(panelId).items
                    val from = current.indexOfFirst { it.id == item.id }
                    val to = from + delta
                    if (from >= 0 && to in current.indices) {
                        store(app!!).moveItem(panelId, item.id, current[to].id, after = delta > 0)
                        reload(app!!)
                    }
                }.onFailure { fail(it) } }
            }.apply {
                contentDescription = app!!.getString(label, item.fields["label"] ?: item.id)
                isEnabled = index + delta in items.indices
                alpha = if (isEnabled) 1f else 0.3f
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        row.addView(confirmDelete)
        row.addView(details)
        heading.setOnClickListener { edit() }
        heading.setOnLongClickListener { it.startDragAndDrop(null, View.DragShadowBuilder(it), ItemDrag(panelId, item.id), 0) }
    }

    private fun enableMenuDrop(row: View, panelId: String, itemId: String, horizontal: Boolean) {
        row.setOnDragListener { _, event ->
            val drag = event.localState as? ItemDrag
            if (drag?.panel != panelId) false else {
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_ENTERED -> if (drag.item != itemId) {
                        row.foreground = GradientDrawable().apply {
                            setColor(Color.TRANSPARENT)
                            setStroke(dp(2).coerceAtLeast(1), EdgeEditorUi.accent(ui()))
                        }
                    }
                    android.view.DragEvent.ACTION_DRAG_LOCATION -> {
                        val source = IntArray(2)
                        row.getLocationInWindow(source)
                        var parent = row.parent
                        while (parent is View) {
                            val location = IntArray(2)
                            parent.getLocationInWindow(location)
                            if (parent is ScrollView) {
                                val y = source[1] + event.y - location[1]
                                if (y < dp(40)) parent.smoothScrollBy(0, -dp(24))
                                else if (y > parent.height - dp(40)) parent.smoothScrollBy(0, dp(24))
                            } else if (parent is android.widget.HorizontalScrollView) {
                                val x = source[0] + event.x - location[0]
                                if (x < dp(40)) parent.smoothScrollBy(-dp(24), 0)
                                else if (x > parent.width - dp(40)) parent.smoothScrollBy(dp(24), 0)
                            }
                            parent = parent.parent
                        }
                    }
                    android.view.DragEvent.ACTION_DROP -> if (drag.item != itemId) runCatching {
                        val after = if (horizontal) event.x >= row.width / 2 else event.y >= row.height / 2
                        store(app!!).moveItem(panelId, drag.item, itemId, after)
                        reload(app!!)
                    }.onFailure { fail(it) }
                    android.view.DragEvent.ACTION_DRAG_EXITED, android.view.DragEvent.ACTION_DRAG_ENDED -> row.foreground = null
                }
                true
            }
        }
    }

    private fun addItem(rows: LinearLayout, panelId: String, item: EdgeStore.Item, iconOnly: Boolean = false, iconSize: Int = 40, horizontalOrder: Boolean = false,
        fillSpace: Boolean = false, inlineClose: (() -> Unit)? = null) {
        val target = "$panelId:${item.id}"
        val row = LinearLayout(ui()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val title = LinearLayout(ui()).apply { gravity = Gravity.CENTER_VERTICAL }
        val pkg = packageFrom(item.command)
        val label = EdgePanelLayout.label(item, pkg?.let { runCatching {
            val pm = app!!.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrNull() })
        val showTitle = label.isNotBlank()
        if (showTitle || item.type == "run") addIcon(title, item.fields["icon"] ?: pkg?.let { "@app:$it" } ?: if (iconOnly) label.take(1) else null, iconSize)
        title.minimumHeight = dp(48)
        title.contentDescription = label
        title.tooltipText = label
        if (iconOnly) title.gravity = Gravity.CENTER
        else if (showTitle) title.addView(text(label, 15f), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(title)
        enableMenuDrop(row, panelId, item.id, horizontalOrder)
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
            "argument" -> macroForm?.addArgument(row, item)
            "result" -> macroForm?.addResult(row, item)
            "macro" -> macroForm?.addAction(row, item)
            "run" -> {
                if (item.isStateButton) {
                    val render: (String) -> Unit = { value ->
                        val on = EdgeStore.parseButtonState(value)
                        val state = if (on == true) "ON" else if (on == false) "OFF" else "—"
                        title.isSelected = on == true
                        title.background = GradientDrawable().apply {
                            val accent = EdgeEditorUi.accent(ui())
                            setColor(if (on == true) (accent and 0x00ffffff) or 0x30000000 else Color.TRANSPARENT)
                            setStroke(dp(if (on == true) 2 else 1).coerceAtLeast(1),
                                if (on == true) accent else (EdgeEditorUi.foreground(ui()) and 0x00ffffff) or 0x60000000)
                        }
                        // State is conveyed visually by the existing button, and spoken by accessibility.
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            title.contentDescription = label
                            title.stateDescription = state
                        } else title.contentDescription = "$label, $state"
                    }
                    renderers["$target:button"] = render
                    renderButton(panelId, item)
                }
                title.setOnClickListener {
                    val source = buttonSource(panelId, item)
                    val on = item.isStateButton && buttonState(panelId, item) == true
                    if (on && source == "process") {
                        val owner = runner
                        owner?.stopAction("action:$target") {
                            if (app != null && runner === owner &&
                                runCatching { store(app!!).item(target) }.getOrNull() == item) {
                                item.fields["off"]?.takeIf { it.isNotBlank() }?.let { execute(panelId, item, it) }
                            }
                        }
                    } else execute(panelId, item, EdgeButtonSource.action(item, on, source))
                }
                title.setOnLongClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    it.startDragAndDrop(null, View.DragShadowBuilder(it), ItemDrag(panelId, item.id), 0)
                }
            }
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
            "terminal" -> {
                val terminal = EdgeTerminalUi(ui(), label, fillSpace, inlineClose)
                terminals.add(terminal)
                row.addView(terminal, if (fillSpace) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
            }
            "note" -> {
                runCatching {
                    val noteStore = store(app!!)
                    val file = noteStore.noteFile(panelId, item)
                    val note = notes.getOrPut(file.path) { EdgeNote(file, noteStore.noteHistoryFile(file)) }
                    val ruled = item.fields["note-lines"] == "on"
                    var fontSize = item.fields["note-size"]?.toIntOrNull() ?: 16
                    val noteColor = EdgeNoteColor.parse(item.fields["note-color"])
                    val noteBackground = EdgeNoteColor.parse(item.fields["note-background"])
                    val preview = EdgeNoteUi.preview(ui(), ruled, noteColor, noteBackground).apply {
                        text = note.text.ifEmpty { app!!.getString(R.string.edge_note_empty) }
                        textSize = fontSize.toFloat()
                    }
                    val editor = EdgeNoteUi.editor(ui(), ruled, noteColor, noteBackground).apply {
                        setText(note.text); minLines = 3; maxLines = 12
                        textSize = fontSize.toFloat()
                        contentDescription = label.ifBlank { app!!.getString(R.string.edge_note) }
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        visibility = View.GONE
                        filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
                            val proposed = dest.substring(0, dstart) + source.subSequence(start, end) + dest.substring(dend)
                            if (proposed.toByteArray(Charsets.UTF_8).size <= EdgeNote.LIMIT) null else dest.subSequence(dstart, dend)
                        })
                    }
                    val history = EdgeToolRow(ui()).apply { visibility = View.GONE }
                    val undo = EdgeNoteUi.button(ui(), R.drawable.ic_edge_undo, R.string.edge_undo)
                    val redo = EdgeNoteUi.button(ui(), R.drawable.ic_edge_redo, R.string.edge_redo)
                    val smaller = EdgeNoteUi.button(ui(), R.drawable.ic_edge_text_smaller, R.string.edge_note_smaller)
                    val larger = EdgeNoteUi.button(ui(), R.drawable.ic_edge_text_larger, R.string.edge_note_larger)
                    fun updateSize() {
                        preview.textSize = fontSize.toFloat(); editor.textSize = fontSize.toFloat()
                        smaller.isEnabled = fontSize > 10; larger.isEnabled = fontSize < 32
                    }
                    fun resize(delta: Int) {
                        runCatching {
                            val size = (fontSize + delta).coerceIn(10, 32)
                            noteStore.setItem(target, mapOf("note-size" to size.toString()))
                            val updated = noteStore.panel(panelId)
                            panels = panels.map { if (it.id == panelId) updated else it }
                            fontSize = size; updateSize()
                        }.onFailure { fail(it) }
                    }
                    smaller.setOnClickListener { resize(-1) }
                    larger.setOnClickListener { resize(1) }
                    fun updateHistory() { undo.isEnabled = note.canUndo; redo.isEnabled = note.canRedo }
                    var restoring = false
                    fun restore(value: String) {
                        restoring = true; editor.setText(value); editor.setSelection(editor.length()); restoring = false
                        updateHistory()
                    }
                    undo.setOnClickListener { restore(note.undo()) }
                    redo.setOnClickListener { restore(note.redo()) }
                    history.addView(undo); history.addView(redo)
                    history.addView(smaller); history.addView(larger)
                    editor.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (!restoring) { note.edit(s.toString()); updateHistory() }
                        }
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                    preview.setOnClickListener {
                        preview.visibility = View.GONE; editor.visibility = View.VISIBLE; history.visibility = View.VISIBLE
                        editor.requestFocus()
                        editor.post { app?.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            ?.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
                    }
                    updateHistory(); updateSize()
                    row.addView(preview); row.addView(editor); row.addView(history)
                }.onFailure { row.addView(text(it.message ?: "Cannot open note")) }
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
        // An empty heading only wastes height; run items keep it as their tap target.
        if (title.childCount == 0 && item.type != "run") row.removeView(title)
        if (item.type !in setOf("toggle", "list", "note", "terminal", "macro", "argument", "result")) {
            row.addView(result)
            if (result.text.isEmpty()) result.visibility = View.GONE
            renderers[target] = { result.text = it; result.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE }
        }
        row.addView(status)
        rows.addView(row, if (fillSpace) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        if (!iconOnly && !fillSpace) rows.addView(EdgeEditorUi.divider(ui()).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)).apply {
                marginStart = dp(12); marginEnd = dp(12)
            }
        })
    }

    private fun refresh(panel: String, item: EdgeStore.Item) {
        if (buttonSource(panel, item) != null) { renderButton(panel, item); return }
        val command = if (item.type == "toggle" || item.isStateButton) item.fields["state"].orEmpty() else item.command
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
        if (!refresh && item.type == "run" && !item.isStateButton && item.fields["out"].orEmpty() in setOf("", "none") && input == null && value == null) {
            val macro = com.zerotoship.z2term.automation.ActionMacroReference.name(command)
            if (macro != null) {
                close()
                return try {
                    com.zerotoship.z2term.automation.ActionRuntime.start(app!!, macro, waitForForeground = true)
                    after?.invoke()
                    true
                } catch (e: Exception) {
                    android.widget.Toast.makeText(app!!, e.message, android.widget.Toast.LENGTH_LONG).show()
                    false
                }
            }
        }
        if (!refresh) runner?.cancelRead(target)
        if (!refresh && item.type == "run" && !item.isStateButton && item.fields["out"].orEmpty() in setOf("", "none")) close()
        val gen = generation
        val revision = revisions[target] ?: 0
        val nextButtonState = if (item.isStateButton && !refresh) store(app!!).buttonState(panel, item) != true else false
        val owner = runner!!
        val token = if (item.isStateButton && !refresh) java.util.UUID.randomUUID().toString() else null
        val script = (token?.let { "export Z2_EDGE_RUN=${HeadlessRun.shSingleQuote(it)}; " } ?: "") +
            EdgeMacroCommand.resolve(command, com.zerotoship.z2term.widget.WidgetStore.availableMacros(app!!))
        val accepted = owner.run((if (refresh) "read:" else "action:") + target, script, item.timeout, input, value) { result ->
            if (token != null) buttonRuns.finish(token)
            if (app == null || runner !== owner) return@run
            val source = buttonSource(panel, item)
            if (source != null) renderButton(panel, item)
            val visible = gen == generation && openId == panel
            if (refresh && (item.type == "toggle" || item.isStateButton) && result.error == null &&
                result.output.trim() !in setOf("on", "off", "true", "false", "1", "0")) {
                if (visible) renderers["$target:status"]?.invoke("state must return on/off, true/false or 1/0")
                return@run
            }
            if (visible) renderers["$target:status"]?.invoke(result.error.orEmpty())
            else if (!refresh && result.error != null) fail(IllegalStateException(result.error))
            if (result.error == null) {
                if (item.isStateButton && source == null && (revisions[target] ?: 0) == revision) {
                    if (refresh) {
                        if (visible) putButtonState(panel, item, EdgeStore.parseButtonState(result.output)!!)
                    } else if (item.fields["state"].isNullOrBlank()) {
                        putButtonState(panel, item, nextButtonState)
                    } else if (visible) {
                        refresh(panel, item)
                    }
                }
                if (refresh && item.isStateButton) {
                    // The query result is a state, not command output for the text area.
                } else if (refresh || item.fields["out"] == "panel") {
                    val sameItem = panels.firstOrNull { it.id == panel }?.items?.contains(item) == true
                    if (sameItem && (!refresh || visible) && (revisions[target] ?: 0) == revision) put(target, result.output)
                } else when (item.fields["out"]) {
                    "toast" -> Toast.makeText(app, result.output.take(1000), Toast.LENGTH_LONG).show()
                    "notify" -> runner!!.run("notify:$target", "z2-notify ${HeadlessRun.shSingleQuote(result.output.take(8000))}", 10) { }
                }
                if (visible) after?.invoke()
            }
        }
        if (accepted && token != null) buttonRuns.start(token, EdgeButtonRuns.Run(panel, item, revision))
        if (accepted && item.isStateButton) renderButton(panel, item)
        renderers["$target:status"]?.invoke(app!!.getString(if (accepted) R.string.edge_updating else R.string.edge_busy))
        return accepted
    }

    private fun buttonSource(panel: String, item: EdgeStore.Item): String? = EdgeButtonSource.resolve(
        item, store(app!!).buttonSource(panel, item), com.zerotoship.z2term.widget.WidgetStore.availableMacros(app!!))

    private fun buttonState(panel: String, item: EdgeStore.Item): Boolean? = when (buttonSource(panel, item)) {
        "torch" -> TorchState.current()
        "screen" -> ScreenTimeout.keepOnUntil(app!!) != null
        "process" -> buttonRuns.running(panel, item)
        else -> store(app!!).buttonState(panel, item) ?: if (item.fields["state"].isNullOrBlank()) false else null
    }

    private fun renderButton(panel: String, item: EdgeStore.Item) {
        if (panels.firstOrNull { it.id == panel }?.items?.contains(item) != true) return
        renderers["$panel:${item.id}:button"]?.invoke(buttonState(panel, item)?.let { if (it) "on" else "off" } ?: "")
    }

    /** A macro can reveal its state source while making an API call; never inspect shell text. */
    fun observeButtonSource(token: String, source: String): Unit = onMain {
        val run = buttonRuns.get(token) ?: return@onMain
        val context = app ?: return@onMain
        val target = "${run.panel}:${run.item.id}"
        if (!run.item.fields["state"].isNullOrBlank() ||
            run.item.fields["button-source"].orEmpty() !in setOf("", "auto") ||
            (revisions[target] ?: 0) != run.revision) return@onMain
        val on = when (source) {
            "torch" -> TorchState.current() == true
            "screen" -> ScreenTimeout.keepOnUntil(context) != null
            else -> return@onMain
        }
        if (store(context).saveButtonState(run.panel, run.item, on, source)) renderButton(run.panel, run.item)
    }

    private fun put(target: String, text: String) {
        values[target] = text.take(65536)
        renderers[target]?.invoke(values[target].orEmpty())
    }

    private fun putButtonState(panel: String, item: EdgeStore.Item, on: Boolean) {
        runCatching {
            if (store(app!!).saveButtonState(panel, item, on)) {
                renderButton(panel, item)
            }
        }.onFailure { fail(it) }
    }

    fun push(context: Context, target: String, text: String, state: Boolean = false) = onMain {
        val item = store(context).item(target)
        require(item.type !in setOf("note", "terminal")) { "push is for live values, not notes or terminal sessions" }
        require(store(context).enabled() && app != null) { "Enable the panel first: z2-edge on" }
        require(text.toByteArray().size <= 65536) { "Value exceeds 64 KiB" }
        if (state) require((item.type == "toggle" || item.isStateButton) && text in setOf("on", "off")) { "state requires a toggle or state button and on|off" }
        if (item.type == "toggle") require(text.trim() in setOf("on", "off", "true", "false", "1", "0")) { "Invalid toggle state" }
        revisions[target] = (revisions[target] ?: 0) + 1
        if (state && item.isStateButton) putButtonState(target.substringBefore(':'), item, text == "on")
        else put(target, text)
    }

    fun badge(context: Context, id: String, value: String) = onMain {
        store(context).panel(id)
        require(store(context).enabled() && app != null) { "Enable the panel first: z2-edge on" }
        require(value.length <= 16) { "Badge: at most 16 characters" }
        badges[id] = value
        handles[id]?.text = value.ifBlank { if (panels.first { it.id == id }.handle == "button") "≡" else "" }
    }

    private fun addIcon(row: LinearLayout, value: String?, iconSize: Int = 40) {
        if (value.isNullOrBlank()) return
        if (!value.startsWith('@')) {
            row.addView(text(value, iconSize * 0.65f).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, 0) },
                LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
            return
        }
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
        }, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
    }

    /** Only simple, unambiguous launch commands get automatic icons. Never evaluate shell text. */
    internal fun packageFrom(command: String): String? = AppLaunchCommand.packageFrom(command)

    private fun fail(error: Throwable) { app?.let { Toast.makeText(it, error.message ?: "Error", Toast.LENGTH_LONG).show() } }
}
