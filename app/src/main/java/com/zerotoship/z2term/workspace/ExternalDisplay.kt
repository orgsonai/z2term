package com.zerotoship.z2term.workspace

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.gui.GuiScreen
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.gui.GuiViewport
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.ui.terminal.TerminalRenderer
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.delay

@Composable internal fun ExternalDisplayEffect() {
    val context = LocalContext.current
    val activity = remember(context) {
        var current = context
        while (current is ContextWrapper && current !is ComponentActivity) current = current.baseContext
        current as? ComponentActivity
    } ?: return
    val projection = Workspace.projection
    val sessions by SessionManager.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == projection?.session }
    var displayRevision by remember { mutableIntStateOf(0) }
    val displays = remember(context) { context.getSystemService(DisplayManager::class.java) }
    DisposableEffect(displays) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { displayRevision++ }
            override fun onDisplayChanged(id: Int) { displayRevision++ }
            override fun onDisplayRemoved(id: Int) {
                if (Workspace.projection?.display == id) Workspace.projection = null
                displayRevision++
            }
        }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displays.unregisterDisplayListener(listener) }
    }
    LaunchedEffect(projection, session) { if (projection != null && session == null) Workspace.projection = null }
    if (projection == null || session == null) return
    DisposableEffect(activity, projection, displayRevision) {
        val display = displays.getDisplay(projection.display)?.takeIf { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
        var window: Presentation? = null
        var compose: ComposeView? = null
        fun hide() { compose?.disposeComposition(); window?.dismiss(); compose = null; window = null }
        fun show() {
            if (window != null || display == null) return
            try {
                val presentation = Presentation(activity, display)
                val view = ComposeView(presentation.context).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setContent { Z2TermTheme { Surface(Modifier.fillMaxSize(), color = ZtsBgPrimary) {
                        val lock by AppLock.state.collectAsState()
                        if (lock == AppLock.State.UNLOCKED) {
                            when (session) {
                                is TerminalSession -> Box(Modifier.fillMaxSize()) {
                                    TerminalRenderer(session, modifier = Modifier.fillMaxSize())
                                    AndroidView(factory = { ctx -> TerminalInputView(ctx).apply {
                                        this.session = session; imeEnabled = true
                                    } }, modifier = Modifier.fillMaxSize())
                                }
                                is GuiSession -> {
                                    var size by remember { mutableStateOf(IntSize.Zero) }
                                    val state by session.state.collectAsState()
                                    LaunchedEffect(size, state) {
                                        if (size.width > 0 && size.height > 0 && state == GuiSession.State.CONNECTED) {
                                            delay(350); session.requestResize(size.width, size.height)
                                        }
                                    }
                                    GuiScreen(session, viewport = remember { GuiViewport() }, manageIme = false,
                                        modifier = Modifier.fillMaxSize().onSizeChanged { size = it })
                                }
                            }
                        }
                    } } }
                }
                presentation.setContentView(view)
                presentation.setOnCancelListener { Workspace.projection = null }
                compose = view; window = presentation
                presentation.show()
            } catch (_: Exception) {
                hide(); Workspace.projection = null
                Toast.makeText(activity, R.string.workspace_display_failed, Toast.LENGTH_LONG).show()
            }
        }
        if (display == null) Workspace.projection = null
        else if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) show()
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> show(); Lifecycle.Event.ON_STOP -> hide(); else -> Unit }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); hide() }
    }
}
