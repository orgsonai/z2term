package com.zerotoship.z2term.workspace

import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.AppSession
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.gui.GuiScreen
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.ui.terminal.TerminalRenderer
import com.zerotoship.z2term.ui.theme.*

internal object Workspace {
    var layout by mutableStateOf(SessionLayout())
    data class Projection(val session: String, val display: Int)
    var projection by mutableStateOf<Projection?>(null)
    fun projected(id: String) = projection?.session == id
}

@Composable internal fun WorkspaceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sessions by SessionManager.sessions.collectAsState()
    val active by SessionManager.activeId.collectAsState()
    var horizontal by remember { mutableStateOf(Workspace.layout.horizontal) }
    val displays = remember { context.getSystemService(DisplayManager::class.java) }
    var available by remember { mutableStateOf(displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()) }
    DisposableEffect(displays) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { refresh() }
            override fun onDisplayRemoved(id: Int) { refresh() }
            override fun onDisplayChanged(id: Int) { refresh() }
            fun refresh() { available = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList() }
        }
        displays.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { displays.unregisterDisplayListener(listener) }
    }
    AlertDialog(onDismissRequest = { onDismiss() }, title = { Text(stringResource(R.string.workspace_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workspace_hint))
            Row {
                TextButton(onClick = { horizontal = false }) { Text((if (!horizontal) "✓ " else "") + stringResource(R.string.workspace_vertical)) }
                TextButton(onClick = { horizontal = true }) { Text((if (horizontal) "✓ " else "") + stringResource(R.string.workspace_horizontal)) }
            }
            sessions.filter { it.id != active }.forEach { session ->
                val label by session.label.collectAsState()
                OutlinedButton(onClick = {
                    Workspace.layout = SessionLayout(active, session.id, 1, horizontal)
                    SessionManager.setActive(session.id); onDismiss()
                }) { Text(label) }
            }
            if (sessions.size < 2) Text(stringResource(R.string.workspace_need_two))
            TextButton(onClick = { Workspace.layout = SessionLayout(first = active); onDismiss() }) {
                Text(stringResource(R.string.workspace_single))
            }
            HorizontalDivider()
            Text(stringResource(R.string.workspace_external_hint))
            available.filter { it.displayId != Display.DEFAULT_DISPLAY }.forEach { display ->
                OutlinedButton(onClick = {
                    active?.let { Workspace.projection = Workspace.Projection(it, display.displayId) }; onDismiss()
                }) { Text(display.name) }
            }
            if (available.isEmpty()) Text(stringResource(R.string.workspace_no_display))
            if (Workspace.projection != null) TextButton(onClick = { Workspace.projection = null; onDismiss() }) {
                Text(stringResource(R.string.workspace_return))
            }
        } }, confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(android.R.string.ok)) } })
}

/** Only the focused pane hosts the existing toolbar/keyboard input path. A first tap focuses the other pane. */
@Composable internal fun SessionPanes(active: AppSession, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sessions by SessionManager.sessions.collectAsState()
    val state = Workspace.layout.select(active.id, sessions.map { it.id }.toSet())
    LaunchedEffect(state) { Workspace.layout = state }
    if (state.second == null) { Box(modifier) { content() }; return }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pane: @Composable (String?, Modifier) -> Unit = { id, childModifier ->
        val session = sessions.firstOrNull { it.id == id }
        if (session != null) key(id) {
            val label by session.label.collectAsState()
            Column(childModifier.border(1.dp, if (id == active.id) ZtsGreen else ZtsBorder)) {
                Text(label, color = if (id == active.id) ZtsGreen else ZtsTextSecondary,
                    maxLines = 1, modifier = Modifier.fillMaxWidth().clickable { SessionManager.setActive(session.id) }.padding(6.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (id == active.id) content()
                    else {
                        PassiveSession(session)
                        Box(Modifier.fillMaxSize().clickable { SessionManager.setActive(session.id) })
                    }
                }
            }
        }
    }
    val divider: @Composable () -> Unit = {
        Box((if (state.horizontal) Modifier.width(12.dp).fillMaxHeight() else Modifier.height(12.dp).fillMaxWidth())
            .background(ZtsBgSecondary).draggable(
                state = rememberDraggableState { delta ->
                    val extent = if (state.horizontal) size.width else size.height
                    if (extent > 0) Workspace.layout = Workspace.layout.resize(Workspace.layout.ratio + delta / extent)
                }, orientation = if (state.horizontal) Orientation.Horizontal else Orientation.Vertical))
    }
    if (state.horizontal) Row(modifier.onSizeChanged { size = it }) {
        pane(state.first, Modifier.weight(state.ratio).fillMaxHeight()); divider()
        pane(state.second, Modifier.weight(1 - state.ratio).fillMaxHeight())
    } else Column(modifier.onSizeChanged { size = it }) {
        pane(state.first, Modifier.weight(state.ratio).fillMaxWidth()); divider()
        pane(state.second, Modifier.weight(1 - state.ratio).fillMaxWidth())
    }
}

@Composable private fun PassiveSession(session: AppSession) {
    if (Workspace.projected(session.id)) Text(stringResource(R.string.workspace_projected), Modifier.padding(16.dp))
    else when (session) {
        is TerminalSession -> TerminalRenderer(session, modifier = Modifier.fillMaxSize())
        is GuiSession -> GuiScreen(session, interactive = false, modifier = Modifier.fillMaxSize())
    }
}
