package com.zerotoship.z2term.workspace

import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
    /** Tab groups. Kept while the app runs, like the tabs themselves (tabs are not restored on restart). */
    var groups by mutableStateOf(TabGroups())
        private set
    /** While the group editor is open, empty groups survive [sync] so a new group can be filled. */
    var editorOpen by mutableStateOf(false)
    /** The editor was opened to start broadcasting: its main button starts it for the selected group. */
    var editorStartsBroadcast by mutableStateOf(false)

    /**
     * Applies [change] and keeps each group's members next to each other in the tab order, so
     * `z2-session list` numbers match what the tab bar shows.
     */
    fun update(change: (TabGroups) -> TabGroups) {
        groups = change(groups)
        val ids = SessionManager.sessions.value.map { it.id }
        val ordered = groups.ordered(ids)
        if (ordered != ids) SessionManager.reorder(ordered)
    }

    /** Forgets closed tabs (and empty groups, unless the editor is open). */
    fun sync(available: Set<String>) {
        val pruned = groups.prune(available, keepEmpty = editorOpen)
        if (pruned != groups) update { pruned }
    }

    fun stackedFor(active: String?): TabGroup? = groups.stackedFor(active)
    fun isStacked(active: String?): Boolean = stackedFor(active) != null

    /**
     * Puts [other] next to [active] (the layout dialog): into the active tab's group when it has one,
     * otherwise into a new group of the two. Returns false when that group is full.
     */
    fun pair(active: String, other: String, horizontal: Boolean, max: Int, nameFor: (Int) -> String): Boolean {
        val current = groups.groupOf(active)
        val (base, groupId) = if (current != null) groups to current.id else {
            val (created, id) = groups.create(nameFor)
            (created.add(id, active, max) ?: return false) to id
        }
        val added = base.add(groupId, other, max) ?: return false
        update { added.setHorizontal(groupId, horizontal).maximize(groupId, null) }
        return true
    }

    /** Closes a tab; when it was focused inside a group, focus moves to a neighbouring member, not the first tab. */
    fun closeTab(id: String) {
        val g = groups.groupOf(id)
        if (g != null && SessionManager.activeId.value == id) {
            val i = g.members.indexOf(id)
            (g.members.getOrNull(i + 1) ?: g.members.getOrNull(i - 1))?.let { SessionManager.setActive(it) }
        }
        SessionManager.close(id)
    }

    data class Projection(val session: String, val display: Int)
    var projection by mutableStateOf<Projection?>(null)
    fun projected(id: String) = projection?.session == id
}

@Composable internal fun WorkspaceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sessions by SessionManager.sessions.collectAsState()
    val active by SessionManager.activeId.collectAsState()
    var horizontal by remember { mutableStateOf(Workspace.groups.groupOf(active)?.horizontal ?: false) }
    val maxTabs = rememberGroupMaxTabs()
    val nameFor = groupNameFor()
    val group = Workspace.groups.groupOf(active)
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
            // 向きは枠で囲んだラジオボタン (1.0.81・利用者の指摘)。✓ 付きの文字だけでは、
            // どちらを選んでいるかが目に入りにくかった。中の図が分割の形そのもの。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitChoice(stringResource(R.string.workspace_vertical), horizontal = false,
                    selected = !horizontal, onSelect = { horizontal = false }, modifier = Modifier.weight(1f))
                SplitChoice(stringResource(R.string.workspace_horizontal), horizontal = true,
                    selected = horizontal, onSelect = { horizontal = true }, modifier = Modifier.weight(1f))
            }
            // 見本: いまのタブがどちらに入り、下で押したタブがどちらに入るか (1.0.81・利用者の指摘)。
            // 以前は「ほかのタブ」の名前しか出ず、押すまで何と何が並ぶか分からなかった。
            // ⛔ 選んでから「分割する」で決める 2 手にはしない (利用者の判断: 手数を増やさない)。
            if (sessions.size >= 2) {
                val currentLabel = sessions.firstOrNull { it.id == active }?.label?.collectAsState()?.value.orEmpty()
                SplitPreview(horizontal, currentLabel)
            }
            // 押したタブは、いまのタブのグループへ入る (グループが無ければ 2 枚のグループを作る)。
            val full = group != null && group.members.size >= maxTabs
            sessions.filter { it.id != active && group?.members?.contains(it.id) != true }.forEach { session ->
                val label by session.label.collectAsState()
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !full && active != null, onClick = {
                    val current = active
                    if (current != null && Workspace.pair(current, session.id, horizontal, maxTabs, nameFor)) {
                        SessionManager.setActive(session.id)
                    }
                    onDismiss()
                }) { Text(label) }
            }
            if (sessions.size < 2) Text(stringResource(R.string.workspace_need_two))
            if (full) Text(stringResource(R.string.group_full, maxTabs), color = ZtsTextSecondary)
            // 向きだけ変えたいとき (もう並んでいるグループ)。
            if (group != null && group.stacked && group.horizontal != horizontal) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    Workspace.update { it.setHorizontal(group.id, horizontal) }; onDismiss()
                }) { Text(stringResource(R.string.group_apply_direction)) }
            }
            TextButton(onClick = { Workspace.editorOpen = true; onDismiss() }) {
                Text(stringResource(R.string.group_edit))
            }
            if (group != null) TextButton(onClick = { Workspace.update { it.delete(group.id) }; onDismiss() }) {
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

/** 分割の向きの選択肢 1 つ。枠と丸で選んでいるかを示し、中に分割の図を描く。 */
@Composable private fun SplitChoice(label: String, horizontal: Boolean, selected: Boolean, onSelect: () -> Unit,
        modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) ZtsGreen else ZtsBorder,
                androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = ZtsGreen, unselectedColor = ZtsTextSecondary))
        SplitShape(horizontal, if (selected) ZtsGreen else ZtsTextSecondary, Modifier.size(width = 28.dp, height = 22.dp))
        Text(label, color = if (selected) ZtsTextPrimary else ZtsTextSecondary)
    }
}

/** 2 つに分けた四角。[horizontal] なら左右、そうでなければ上下。 */
@Composable private fun SplitShape(horizontal: Boolean, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val pane = Modifier.border(1.5.dp, color)
    if (horizontal) Row(modifier) { Box(pane.weight(1f).fillMaxHeight()); Box(pane.weight(1f).fillMaxHeight()) }
    else Column(modifier) { Box(pane.weight(1f).fillMaxWidth()); Box(pane.weight(1f).fillMaxWidth()) }
}

/** 選んだ向きで、いまのタブ ([currentLabel]) と下で押すタブがどこに入るかを見せる。 */
@Composable private fun SplitPreview(horizontal: Boolean, currentLabel: String) {
    @Composable fun Pane(title: String, name: String?, current: Boolean, modifier: Modifier) {
        Column(
            modifier = modifier
                .border(if (current) 2.dp else 1.dp, if (current) ZtsGreen else ZtsBorder)
                .padding(6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = ZtsTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            if (name != null) Text(name, color = ZtsTextPrimary, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    val first = stringResource(R.string.workspace_preview_current)
    val second = stringResource(R.string.workspace_preview_other)
    if (horizontal) Row(Modifier.fillMaxWidth().height(64.dp)) {
        Pane(first, currentLabel, true, Modifier.weight(1f).fillMaxHeight())
        Pane(second, null, false, Modifier.weight(1f).fillMaxHeight())
    } else Column(Modifier.fillMaxWidth()) {
        Pane(first, currentLabel, true, Modifier.fillMaxWidth().height(48.dp))
        Pane(second, null, false, Modifier.fillMaxWidth().height(48.dp))
    }
}

/**
 * Panes of the active tab's group, or the active tab alone. Only the focused pane hosts the existing
 * toolbar/keyboard input path; a first tap on another pane only moves the focus there.
 */
@Composable internal fun SessionPanes(active: AppSession, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sessions by SessionManager.sessions.collectAsState()
    LaunchedEffect(active.id) { Workspace.update { it.focus(active.id) } }
    val group = Workspace.stackedFor(active.id)
    if (group == null) { Box(modifier) { content() }; return }
    val panes = group.panes(active.id)
    val horizontal = group.horizontal
    // Gesture handlers outlive a composition; read the group through this so they never act on a stale one.
    val groupId by rememberUpdatedState(group.id)
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Long-press a title and drag it along the stack to move that tab. Nothing moves while dragging:
    // the pane it would land on gets a thick frame, and letting go moves it there.
    val starts = remember { mutableStateMapOf<String, Int>() }
    val extents = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentPanes by rememberUpdatedState(panes)
    fun dropIndex(): Int? {
        val id = draggingId ?: return null
        val from = currentPanes.indexOf(id)
        if (from < 0) return null
        val center = (starts[id] ?: 0) + (extents[id] ?: 0) / 2f + dragOffset
        val to = currentPanes.indexOfFirst { p ->
            val start = starts[p] ?: return@indexOfFirst false
            center >= start && center < start + (extents[p] ?: 0)
        }
        return if (to < 0) (if (dragOffset > 0) currentPanes.size - 1 else 0) else to
    }
    val target = dropIndex()?.let { i -> panes.getOrNull(i) }?.takeIf { it != draggingId }
    // Panes that receive what is typed now. Only shown while it is more than the focused pane,
    // in the warning colour, so sending to several tabs is never mistaken for ordinary typing.
    val sendingTo = (active as? TerminalSession)?.let { Broadcast.targets(it).map { t -> t.id } }.orEmpty()
        .takeIf { it.size > 1 }.orEmpty().toSet()
    val pane: @Composable (String, Modifier) -> Unit = { id, childModifier ->
        val session = sessions.firstOrNull { it.id == id }
        if (session != null) key(id) {
            val label by session.label.collectAsState()
            val dragged = draggingId == id
            val isTarget = target == id
            // 1 枚だけのグループ (1.0.159) は「最大化」ではない。最大化しているのは、2 枚以上のうち 1 枚だけを出しているとき。
            val maximized = group.members.size > 1 && panes.size == 1
            val isActive = id == active.id
            var menuOpen by remember { mutableStateOf(false) }
            Column(childModifier
                .onGloballyPositioned { c ->
                    val p = c.positionInParent()
                    starts[id] = if (horizontal) p.x.toInt() else p.y.toInt()
                    extents[id] = if (horizontal) c.size.width else c.size.height
                }
                // ⭐ 入力先は送り先の警告色より優先して緑の太枠にする (1.0.159)。ブロードキャスト中は
                // 送り先がすべて黄色になり、入力先も黄色だったので、どれに打っているのか見分けられなかった。
                .border(
                    when {
                        isTarget || isActive -> 3.dp
                        id in sendingTo -> 2.dp
                        else -> 1.dp
                    },
                    when {
                        isTarget || dragged || isActive -> ZtsGreen
                        id in sendingTo -> ZtsWarning
                        else -> ZtsBorder
                    })) {
                // Maximize, or restore the whole group when this pane is already alone.
                val toggleMaximized by rememberUpdatedState {
                    Workspace.update { it.maximize(groupId, if (maximized) null else session.id) }
                    SessionManager.setActive(session.id)
                }
                // 入力先の見出しは地の色も変えて、枠の色だけに頼らない (1.0.159)。
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(if (isActive) ZtsGreen.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically) {
                // Broadcast mode: tick the tabs that should receive input (terminal tabs only).
                if (Broadcast.mode && session is TerminalSession) {
                    val checked = session.id in group.checked
                    Box(Modifier.fillMaxHeight().width(40.dp)
                        .toggleable(value = checked, role = Role.Checkbox,
                            onValueChange = { v -> Workspace.update { it.setChecked(session.id, v) } }),
                        contentAlignment = Alignment.Center) {
                        Icon(if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            stringResource(R.string.broadcast_include, label), Modifier.size(18.dp),
                            tint = if (checked) ZtsWarning else ZtsTextSecondary)
                    }
                }
                Box(Modifier.weight(1f)) {
                Text((if (dragged) "⇅ " else "") + label, color = if (id == active.id) ZtsGreen else ZtsTextSecondary,
                    maxLines = 1, modifier = Modifier.fillMaxWidth()
                        .pointerInput(session.id) {
                            detectTapGestures(
                                onTap = { SessionManager.setActive(session.id) },
                                // Double-tap a title to maximize it (or restore the group).
                                onDoubleTap = { toggleMaximized() },
                            )
                        }
                        .pointerInput(session.id, horizontal) {
                            var moved = 0f
                            fun reset() { dragOffset = 0f; draggingId = null; moved = 0f }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { moved = 0f; dragOffset = 0f; draggingId = session.id },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val d = if (horizontal) amount.x else amount.y
                                    moved += kotlin.math.abs(d)
                                    dragOffset += d
                                },
                                onDragEnd = {
                                    val from = currentPanes.indexOf(session.id)
                                    val to = dropIndex()
                                    if (moved < viewConfiguration.touchSlop) menuOpen = true
                                    else if (from >= 0 && to != null && to != from) Workspace.update { it.move(groupId, from, to) }
                                    reset()
                                },
                                onDragCancel = { reset() },
                            )
                        }
                        .padding(6.dp))
                // Long-press and let go without moving: the same menu as for a tab.
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    GroupMemberMenu(session, onDone = { menuOpen = false })
                }
                }
                // The same as the double-tap, without having to know the gesture. As tall as the
                // title and wider than its glyph, so it is easy to hit without growing the title.
                // タブごとのログ (1.0.154)。⚪ = 記録していない / 一時停止、🔴 = 記録中。
                // 押すと 記録中 ⇄ 一時停止。一時停止から戻すと同じファイルの続きに書く。
                // 字はツールバーの ⚪ / 🔴 と同じにして、同じ記録だと分かるようにする。
                // ⭐ 出すのは、そのタブの記録が ON (記録中か一時停止中) のときだけ (1.0.155)。
                // ON / OFF そのものはツールバーの ⚪ (グループ全体) で決める。
                val paneLog = (session as? TerminalSession)?.logState?.collectAsState()?.value
                if (session is TerminalSession && paneLog != null && (paneLog.recording || paneLog.paused)) {
                    val log = paneLog
                    val logLabel = stringResource(if (log.recording) R.string.pane_log_pause else R.string.pane_log_record)
                    Box(Modifier.fillMaxHeight().width(40.dp)
                        .clickable(onClickLabel = logLabel) { session.togglePauseLogging() }
                        .semantics { contentDescription = logLabel },
                        contentAlignment = Alignment.Center) {
                        Text(if (log.recording) "🔴" else "⚪", fontSize = 12.sp)
                    }
                }
                val toggleLabel = stringResource(if (maximized) R.string.group_restore else R.string.workspace_maximize)
                // 1 枚だけのグループでは大きくする相手がいないので出さない。
                if (group.members.size > 1) Box(Modifier.fillMaxHeight().width(44.dp).clickable(
                        onClickLabel = toggleLabel, onClick = { toggleMaximized() }),
                    contentAlignment = Alignment.Center) {
                    Icon(if (maximized) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull, toggleLabel,
                        Modifier.size(16.dp), tint = if (id == active.id) ZtsGreen else ZtsTextSecondary)
                }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (id == active.id) content()
                    else {
                        PassiveSession(session)
                        // A tap still only moves focus; a vertical drag on a terminal pane scrolls its
                        // scrollback without taking focus, so the other pane can be read while typing here.
                        Box(Modifier.fillMaxSize()
                            .then(if (session is TerminalSession && !Workspace.projected(session.id))
                                Modifier.passiveScroll(session) else Modifier)
                            .clickable { SessionManager.setActive(session.id) })
                    }
                }
            }
        }
    }
    // The divider after pane [index]: only its two neighbours trade share.
    val divider: @Composable (Int) -> Unit = { index ->
        Box((if (horizontal) Modifier.width(12.dp).fillMaxHeight() else Modifier.height(12.dp).fillMaxWidth())
            .background(ZtsBgSecondary).draggable(
                state = rememberDraggableState { delta ->
                    val extent = if (horizontal) size.width else size.height
                    if (extent > 0) Workspace.update { it.resize(groupId, index, delta / extent) }
                }, orientation = if (horizontal) Orientation.Horizontal else Orientation.Vertical))
    }
    val weightOf = { id: String -> if (panes.size == 1) 1f else group.weightOf(id).coerceAtLeast(0.01f) }
    if (horizontal) Row(modifier.onSizeChanged { size = it }) {
        panes.forEachIndexed { i, id ->
            if (i > 0) divider(i - 1)
            pane(id, Modifier.weight(weightOf(id)).fillMaxHeight())
        }
    } else Column(modifier.onSizeChanged { size = it }) {
        panes.forEachIndexed { i, id ->
            if (i > 0) divider(i - 1)
            pane(id, Modifier.weight(weightOf(id)).fillMaxWidth())
        }
    }
}

/**
 * Scrollback for a pane that is not focused: drag down for older lines, up for newer, with a fling.
 * Only the scrollback moves — nothing is written to the terminal, so a full-screen program on the
 * alternate screen (which has no scrollback) stays still until the pane is focused.
 */
@Composable private fun Modifier.passiveScroll(session: TerminalSession): Modifier {
    var carry by remember(session.id) { mutableFloatStateOf(0f) }
    fun scrollPixels(delta: Float) {
        val lineHeight = session.cellMetrics.value.lineHeight
        if (lineHeight <= 0f) return
        carry += delta
        val rows = (carry / lineHeight).toInt()
        if (rows != 0) {
            session.scrollBy(rows)
            carry -= rows * lineHeight
        }
    }
    return draggable(
        state = rememberDraggableState { delta -> scrollPixels(delta) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            var last = 0f
            AnimationState(0f, velocity).animateDecay(exponentialDecay()) {
                scrollPixels(value - last)
                last = value
            }
            carry = 0f
        },
    )
}

@Composable private fun PassiveSession(session: AppSession) {
    if (Workspace.projected(session.id)) Text(stringResource(R.string.workspace_projected), Modifier.padding(16.dp))
    else when (session) {
        is TerminalSession -> TerminalRenderer(session, modifier = Modifier.fillMaxSize())
        is GuiSession -> GuiScreen(session, interactive = false, modifier = Modifier.fillMaxSize())
    }
}
