package com.zerotoship.z2term.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.AppSession
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.components.ConfirmDialog
import com.zerotoship.z2term.ui.terminal.NewTabOsDialog
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A dialog opened from a tab, group or pane menu. Kept outside the menu, which closes first. */
internal sealed interface GroupDialog {
    data class RenameTab(val sessionId: String) : GroupDialog
    data class RenameGroup(val groupId: String) : GroupDialog
    data class ChooseGroup(val sessionId: String) : GroupDialog
    data class CloseTab(val sessionId: String) : GroupDialog
}

internal object GroupUiState {
    var dialog by mutableStateOf<GroupDialog?>(null)
}

/** The most tabs one group may hold: the setting, capped by what fits on this screen. */
@Composable internal fun rememberGroupMaxTabs(): Int {
    val context = LocalContext.current
    val settings by remember { AppSettings(context.applicationContext) }.flow.collectAsState(initial = AppSettings.lastKnown)
    return minOf(settings.groupMaxTabs, groupDeviceLimit(settings))
}

/** How many tabs a group can hold on this screen (the upper end of the setting). */
@Composable internal fun groupDeviceLimit(settings: AppSettings.Snapshot): Int {
    val conf = LocalConfiguration.current
    return groupMaxTabsFor(
        longSideDp = maxOf(conf.screenWidthDp, conf.screenHeightDp).toFloat(),
        keyboardDp = settings.portraitKeyboardHeightDp,
        fontSp = settings.fontSizeSp,
        fontScale = conf.fontScale,
        min = AppSettings.MIN_GROUP_MAX_TABS,
        max = AppSettings.MAX_GROUP_MAX_TABS,
    )
}

/** Default names for new groups ("Group 1", "Group 2", …). */
@Composable internal fun groupNameFor(): (Int) -> String {
    val context = LocalContext.current
    return { n -> context.getString(R.string.group_default_name, n) }
}

private const val TAB_NAME_MAX = 20

internal fun renameSession(session: AppSession, name: String) {
    val trimmed = name.trim().take(TAB_NAME_MAX)
    if (trimmed.isEmpty()) return
    when (session) {
        // pinned: the OS name or the shell's title must not overwrite a name the user chose.
        is TerminalSession -> session.setLabel(trimmed, pinned = true)
        is GuiSession -> session.setLabel(trimmed)
    }
}

/** Shortens a long name in the middle, so both its start and its end stay readable. */
internal fun middleEllipsis(name: String, max: Int): String {
    if (name.length <= max || max < 3) return name
    val head = (max - 1) / 2
    return name.take(head) + "…" + name.takeLast(max - 1 - head)
}

@Composable private fun MenuItem(text: Int, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = onClick)

/** Menu of a plain tab in the tab bar (long-press and let go). */
@Composable internal fun TabMenu(session: AppSession, onDone: () -> Unit) {
    MenuItem(R.string.group_rename_tab) { onDone(); GroupUiState.dialog = GroupDialog.RenameTab(session.id) }
    if (Workspace.groups.groupOf(session.id) == null) {
        MenuItem(R.string.group_add_to) { onDone(); GroupUiState.dialog = GroupDialog.ChooseGroup(session.id) }
    } else {
        MenuItem(R.string.group_leave) { onDone(); Workspace.update { it.removeMember(session.id) } }
    }
    MenuItem(R.string.group_edit) { onDone(); Workspace.editorOpen = true }
}

/** Menu of a pane title inside a group. */
@Composable internal fun GroupMemberMenu(session: AppSession, onDone: () -> Unit) {
    MenuItem(R.string.group_rename_tab) { onDone(); GroupUiState.dialog = GroupDialog.RenameTab(session.id) }
    MenuItem(R.string.group_leave) { onDone(); Workspace.update { it.removeMember(session.id) } }
    MenuItem(R.string.group_close_tab) {
        onDone()
        if (session.isBusy) GroupUiState.dialog = GroupDialog.CloseTab(session.id) else Workspace.closeTab(session.id)
    }
    MenuItem(R.string.group_edit) { onDone(); Workspace.editorOpen = true }
}

/** Menu of a group entry in the tab bar. */
@Composable internal fun GroupMenu(group: TabGroup, onDone: () -> Unit) {
    MenuItem(R.string.group_rename_group) { onDone(); GroupUiState.dialog = GroupDialog.RenameGroup(group.id) }
    MenuItem(if (group.horizontal) R.string.group_arrange_vertical else R.string.group_arrange_horizontal) {
        onDone(); Workspace.update { it.setHorizontal(group.id, !group.horizontal) }
    }
    MenuItem(R.string.group_ungroup) { onDone(); Workspace.update { it.delete(group.id) } }
    MenuItem(R.string.group_edit) { onDone(); Workspace.editorOpen = true }
}

/** Hosts the dialogs opened from the menus and the group editor. Placed once, next to the tab bar. */
@Composable internal fun GroupDialogsHost() {
    val sessions by SessionManager.sessions.collectAsState()
    LaunchedEffect(sessions) { Workspace.sync(sessions.map { it.id }.toSet()) }
    val dismiss = { GroupUiState.dialog = null }
    when (val d = GroupUiState.dialog) {
        is GroupDialog.RenameTab -> sessions.firstOrNull { it.id == d.sessionId }?.let { s ->
            val label by s.label.collectAsState()
            NameDialog(stringResource(R.string.group_name_title_tab), label, TAB_NAME_MAX,
                onDone = { renameSession(s, it); dismiss() }, onDismiss = dismiss)
        }
        is GroupDialog.RenameGroup -> Workspace.groups.group(d.groupId)?.let { g ->
            NameDialog(stringResource(R.string.group_name_title_group), g.name, GROUP_NAME_MAX,
                onDone = { name -> Workspace.update { it.rename(g.id, name.take(GROUP_NAME_MAX)) }; dismiss() }, onDismiss = dismiss)
        }
        is GroupDialog.ChooseGroup -> ChooseGroupDialog(d.sessionId, dismiss)
        is GroupDialog.CloseTab -> ConfirmDialog(
            title = stringResource(R.string.confirm_close_busy_title),
            message = stringResource(R.string.confirm_close_busy_msg),
            confirmLabel = stringResource(R.string.confirm_close_busy_action),
            onConfirm = { dismiss(); Workspace.closeTab(d.sessionId) },
            onCancel = dismiss,
        )
        null -> Unit
    }
    if (Workspace.editorOpen) GroupEditorDialog(onDismiss = {
        Workspace.editorOpen = false
        Workspace.editorStartsBroadcast = false
        Workspace.sync(SessionManager.sessions.value.map { it.id }.toSet())
    })
}

private const val GROUP_NAME_MAX = 40

@Composable private fun NameDialog(title: String, initial: String, max: Int, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it.take(max) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onDone(value) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Puts one tab into an existing group or a new one. */
@Composable private fun ChooseGroupDialog(sessionId: String, onDismiss: () -> Unit) {
    val maxTabs = rememberGroupMaxTabs()
    val nameFor = groupNameFor()
    fun join(groupId: String?) {
        Workspace.update { st ->
            val (base, id) = if (groupId != null) st to groupId else st.create(nameFor)
            base.add(id, sessionId, maxTabs) ?: st
        }
        // Show the group the tab has joined.
        SessionManager.setActive(sessionId)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        title = { Text(stringResource(R.string.group_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Workspace.groups.groups.filter { it.members.isNotEmpty() }.forEach { g ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = g.members.size < maxTabs,
                        onClick = { join(g.id) }) { Text("${g.name}  ${g.members.size}/$maxTabs", maxLines = 1) }
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { join(null) }) {
                    Text(stringResource(R.string.group_new))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * Groups on the left, tabs in no group on the right. Every group is shown open, with the selected
 * one framed in green, so the whole arrangement is visible at once. Changes apply immediately.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun GroupEditorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by SessionManager.sessions.collectAsState()
    val activeId by SessionManager.activeId.collectAsState()
    val groups = Workspace.groups
    val maxTabs = rememberGroupMaxTabs()
    val nameFor = groupNameFor()
    val appSettings by remember { AppSettings(context.applicationContext) }.flow.collectAsState(initial = AppSettings.lastKnown)
    val startsBroadcast = Workspace.editorStartsBroadcast
    var selected by remember { mutableStateOf(groups.groupOf(activeId)?.id ?: groups.groups.firstOrNull()?.id) }
    val selectedGroup = groups.group(selected)
    var osPickerOpen by remember { mutableStateOf(false) }

    // A new tab is started right away (like `z2-session new`): tabs that are not on screen do not
    // start by themselves, and a group of tabs that never started would show empty panes.
    fun openTab(distroId: String?) {
        val previous = SessionManager.activeId.value
        val s = SessionManager.openNew(context, distroId)
        previous?.let { SessionManager.setActive(it) }
        scope.launch {
            val plan = s.startupPlan()
            if (plan is TerminalSession.StartupPlan.Start || plan is TerminalSession.StartupPlan.NeedOsInstall) s.startTerminal()
        }
    }
    fun addToSelected(sessionId: String) {
        Workspace.update { st ->
            var base = st
            var id = selected
            if (id == null || base.group(id) == null) {
                val (created, newId) = base.create(nameFor)
                base = created; id = newId
            }
            selected = id
            base.add(id, sessionId, maxTabs) ?: st
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ZtsBgPrimary, contentColor = ZtsTextPrimary) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Text(stringResource(R.string.group_editor_title), fontSize = 16.sp, color = ZtsTextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                if (startsBroadcast) Text(stringResource(R.string.broadcast_editor_hint), fontSize = 12.sp, lineHeight = 17.sp,
                    color = ZtsWarning, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
                Text(stringResource(R.string.group_editor_hint), fontSize = 12.sp, lineHeight = 17.sp, color = ZtsTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                HorizontalDivider(color = ZtsBorder)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColumnHeader(R.string.group_editor_groups)
                        EditorAction(stringResource(R.string.group_new), enabled = true, onClick = {
                            Workspace.update { st -> st.create(nameFor).also { selected = it.second }.first }
                        })
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.groups.forEach { g ->
                                key(g.id) {
                                    GroupBox(g, selected = g.id == selected, maxTabs = maxTabs, sessions = sessions,
                                        onSelect = { selected = g.id })
                                }
                            }
                        }
                    }
                    VerticalDivider(color = ZtsBorder)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColumnHeader(R.string.group_editor_tabs)
                        EditorAction(stringResource(R.string.group_new_tab), enabled = true,
                            onClick = { openTab(null) }, onLongClick = { osPickerOpen = true })
                        val canAdd = selectedGroup == null || selectedGroup.members.size < maxTabs
                        val free = sessions.filter { groups.groupOf(it.id) == null }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            free.forEach { s ->
                                key(s.id) {
                                    val label by s.label.collectAsState()
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp, color = if (canAdd) ZtsTextPrimary else ZtsTextTertiary,
                                        modifier = Modifier.fillMaxWidth()
                                            .border(1.dp, ZtsBorder, RoundedCornerShape(4.dp))
                                            .combinedClickable(enabled = true,
                                                onClick = { if (canAdd) addToSelected(s.id) },
                                                onLongClick = { GroupUiState.dialog = GroupDialog.RenameTab(s.id) })
                                            .padding(horizontal = 8.dp, vertical = 10.dp))
                                }
                            }
                            if (free.isEmpty()) Text(stringResource(R.string.group_editor_all_grouped), fontSize = 12.sp, color = ZtsTextSecondary)
                            if (!canAdd) Text(stringResource(R.string.group_full, maxTabs), fontSize = 12.sp, color = ZtsTextSecondary)
                        }
                    }
                }
                HorizontalDivider(color = ZtsBorder)
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.group_editor_close)) }
                    // Opened from 📡: start broadcasting to the selected group (all its terminal tabs
                    // ticked; ON or OFF as set in Settings). Otherwise just show the group.
                    val terminalCount = selectedGroup?.members?.count { id -> sessions.any { it.id == id && it is TerminalSession } } ?: 0
                    Button(enabled = if (startsBroadcast) terminalCount >= 2 else selectedGroup?.members?.isNotEmpty() == true, onClick = {
                        selectedGroup?.let { g ->
                            if (startsBroadcast) Broadcast.start(g.id, appSettings.broadcastStartOn)
                            g.entry()?.let { SessionManager.setActive(it) }
                        }
                        onDismiss()
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZtsGreen)) {
                        Text(stringResource(if (startsBroadcast) R.string.broadcast_start else R.string.group_editor_show))
                    }
                }
            }
        }
    }
    if (osPickerOpen) NewTabOsDialog(onPick = { osPickerOpen = false; openTab(it) }, onDismiss = { osPickerOpen = false })
}

@Composable private fun ColumnHeader(text: Int) =
    Text(stringResource(text), fontSize = 12.sp, color = ZtsTextSecondary)

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun EditorAction(label: String, enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Text(label, color = if (enabled) ZtsGreen else ZtsTextTertiary, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, ZtsBorder, RoundedCornerShape(4.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp))
}

/**
 * One group in the editor. Tap the title or the frame to select it; tap a tab to send it back to the
 * right; long-press a tab and drag to reorder, or let go without moving to rename it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun GroupBox(group: TabGroup, selected: Boolean, maxTabs: Int, sessions: List<AppSession>, onSelect: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Same as reordering tabs in the tab bar: swap with the neighbour once past its middle.
    fun trySwap() {
        val id = draggingId ?: return
        val live = Workspace.groups.group(group.id)?.members ?: return
        val i = live.indexOf(id)
        if (i < 0) return
        val next = live.getOrNull(i + 1)?.let { heights[it] }
        if (next != null && dragOffset > next / 2f) {
            Workspace.update { it.move(group.id, i, i + 1) }; dragOffset -= next; return
        }
        val prev = live.getOrNull(i - 1)?.let { heights[it] }
        if (prev != null && dragOffset < -prev / 2f) {
            Workspace.update { it.move(group.id, i, i - 1) }; dragOffset += prev
        }
    }
    Column(Modifier.fillMaxWidth()
        .border(if (selected) 2.dp else 1.dp, if (selected) ZtsGreen else ZtsBorder, RoundedCornerShape(4.dp))
        .clickable(onClick = onSelect)
        .padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            Text("${group.name}  ${group.members.size}/$maxTabs", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) ZtsGreen else ZtsTextSecondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true })
                    .padding(vertical = 6.dp))
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(R.string.group_rename_group) { menuOpen = false; GroupUiState.dialog = GroupDialog.RenameGroup(group.id) }
                MenuItem(R.string.group_delete) { menuOpen = false; Workspace.update { it.delete(group.id) } }
            }
        }
        if (group.members.isEmpty()) Text(stringResource(R.string.group_editor_empty), fontSize = 12.sp, color = ZtsTextTertiary,
            modifier = Modifier.padding(vertical = 6.dp))
        group.members.forEachIndexed { index, id ->
            val session = sessions.firstOrNull { it.id == id } ?: return@forEachIndexed
            key(id) {
                val label by session.label.collectAsState()
                val dragging = draggingId == id
                Text("${index + 1}  $label", maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, color = ZtsTextPrimary,
                    modifier = Modifier.fillMaxWidth()
                        .onSizeChanged { heights[id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .border(1.dp, if (dragging) ZtsGreen else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { Workspace.update { it.removeMember(id) } }
                        .pointerInput(id) {
                            var moved = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = id; dragOffset = 0f; moved = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    moved += abs(amount.y)
                                    dragOffset += amount.y
                                    trySwap()
                                },
                                onDragEnd = {
                                    if (moved < viewConfiguration.touchSlop) GroupUiState.dialog = GroupDialog.RenameTab(id)
                                    draggingId = null; dragOffset = 0f
                                },
                                onDragCancel = { draggingId = null; dragOffset = 0f },
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp))
            }
        }
    }
}

/**
 * The broadcast switch, always shown in the tab bar while in broadcast mode. ON shows how many tabs
 * receive what is typed now. Long-press opens the group editor to rearrange.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun BroadcastSwitch(targets: Int) {
    val on = Broadcast.on
    val label = if (on) stringResource(R.string.broadcast_on, targets) else stringResource(R.string.broadcast_off)
    Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
        color = if (on) ZtsBgPrimary else ZtsWarning,
        modifier = Modifier
            .border(1.dp, ZtsWarning, RoundedCornerShape(6.dp))
            .then(if (on) Modifier.background(ZtsWarning, RoundedCornerShape(6.dp)) else Modifier)
            .combinedClickable(
                onClickLabel = stringResource(R.string.broadcast_toggle),
                onClick = { Broadcast.on = !on },
                onLongClickLabel = stringResource(R.string.group_edit),
                onLongClick = { Workspace.editorOpen = true },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp))
}

/**
 * The hidden button revealed by sliding left on + / 🖥: 📡 to start (opens the group editor),
 * or "stop" while in broadcast mode.
 */
@Composable internal fun BroadcastEntryButton(onDone: () -> Unit) {
    val stopping = Broadcast.mode
    Text(stringResource(if (stopping) R.string.broadcast_stop else R.string.broadcast_entry), fontSize = 12.sp,
        fontFamily = FontFamily.Monospace, maxLines = 1, color = ZtsWarning,
        modifier = Modifier
            .border(1.dp, ZtsWarning, RoundedCornerShape(6.dp))
            .clickable {
                if (stopping) Broadcast.stop()
                else { Workspace.editorStartsBroadcast = true; Workspace.editorOpen = true }
                onDone()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp))
}
