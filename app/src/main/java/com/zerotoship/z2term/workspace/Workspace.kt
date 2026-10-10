package com.zerotoship.z2term.workspace

import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    // Long-press a title and drag it toward the other pane to exchange them. Nothing moves while
    // dragging: the other pane's frame thickens once letting go would swap.
    var swapArmed by remember { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    val currentSize by rememberUpdatedState(size)
    val pane: @Composable (String?, Modifier) -> Unit = { id, childModifier ->
        val session = sessions.firstOrNull { it.id == id }
        if (session != null) key(id) {
            val label by session.label.collectAsState()
            var dragged by remember { mutableStateOf(false) }
            val target = swapArmed && !dragged
            Column(childModifier.border(if (target) 3.dp else 1.dp,
                if (id == active.id || target || dragged) ZtsGreen else ZtsBorder)) {
                // Showing only this tab: one place for the title's double-tap and its button.
                val showAlone = {
                    Workspace.layout = Workspace.layout.single(session.id)
                    SessionManager.setActive(session.id)
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Text((if (dragged) "⇅ " else "") + label, color = if (id == active.id) ZtsGreen else ZtsTextSecondary,
                    maxLines = 1, modifier = Modifier.weight(1f)
                        .pointerInput(session.id) {
                            detectTapGestures(
                                onTap = { SessionManager.setActive(session.id) },
                                // Double-tap a title to show only that tab.
                                onDoubleTap = { showAlone() },
                            )
                        }
                        .pointerInput(session.id) {
                            var offset = 0f
                            fun reset() { offset = 0f; dragged = false; swapArmed = false }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset = 0f; dragged = true },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val layout = currentState
                                    offset += if (layout.horizontal) amount.x else amount.y
                                    val extent = if (layout.horizontal) currentSize.width else currentSize.height
                                    // Toward the other pane: down/right from the first, up/left from the second.
                                    val toward = if (session.id == layout.first) offset else -offset
                                    swapArmed = extent > 0 && toward > extent * SWAP_DISTANCE
                                },
                                onDragEnd = {
                                    if (swapArmed) Workspace.layout = Workspace.layout.swap()
                                    reset()
                                },
                                onDragCancel = { reset() },
                            )
                        }
                        .padding(6.dp))
                // The same as the double-tap, without having to know the gesture. As tall as the
                // title and wider than its glyph, so it is easy to hit without growing the title.
                Box(Modifier.fillMaxHeight().width(44.dp).clickable(
                        onClickLabel = stringResource(R.string.workspace_maximize), onClick = showAlone),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.OpenInFull, stringResource(R.string.workspace_maximize),
                        Modifier.size(16.dp), tint = if (id == active.id) ZtsGreen else ZtsTextSecondary)
                }
                }
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

/** Share of the split extent a title must be dragged toward the other pane before letting go swaps them. */
private const val SWAP_DISTANCE = 0.15f

@Composable private fun PassiveSession(session: AppSession) {
    if (Workspace.projected(session.id)) Text(stringResource(R.string.workspace_projected), Modifier.padding(16.dp))
    else when (session) {
        is TerminalSession -> TerminalRenderer(session, modifier = Modifier.fillMaxSize())
        is GuiSession -> GuiScreen(session, interactive = false, modifier = Modifier.fillMaxSize())
    }
}
