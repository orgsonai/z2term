package com.zerotoship.z2term.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession

/**
 * Broadcast: the user's input goes to every ticked tab of the focused tab's group.
 *
 * Every place that writes the user's typing, pasting or snippets into a terminal goes through
 * here, so "what reaches which tab" is decided in one place ([broadcastTargets]).
 * ⚠ Input is handed over as text or as a key, never as bytes made for the focused tab:
 * cursor keys depend on each terminal's mode (DECCKM) and bracketed paste is per terminal,
 * so each target builds its own bytes. Mouse, scrolling, the search box, `z2-session send/key`
 * and automation rules do not come through here and are never broadcast.
 */
internal object Broadcast {
    /** Broadcast mode: pane titles show tick boxes and the ON/OFF switch stays in the tab bar. */
    var mode by mutableStateOf(false)
        private set
    /** Whether input is sent to the ticked tabs while in [mode]. */
    var on by mutableStateOf(false)

    /** Enters broadcast mode for [groupId] with all its terminal tabs ticked; [startOn] comes from the setting. */
    fun start(groupId: String, startOn: Boolean) {
        val sessions = SessionManager.sessions.value
        val group = Workspace.groups.group(groupId) ?: return
        val terminals = group.members.filter { id -> sessions.any { it.id == id && it is TerminalSession } }
        Workspace.update { it.checkOnly(groupId, terminals).maximize(groupId, null) }
        on = startOn
        mode = true
    }

    fun stop() {
        on = false
        mode = false
    }

    /** Tabs that receive input typed into [from]: [from] alone unless broadcasting from a ticked tab. */
    fun targets(from: TerminalSession): List<TerminalSession> {
        if (!mode || !on) return listOf(from)
        val sessions = SessionManager.sessions.value
        fun terminal(id: String) = sessions.firstOrNull { it.id == id } as? TerminalSession
        // A tab that has not started has no shell to type into; it is left out rather than queued.
        val ids = broadcastTargets(Workspace.groups, on = true, from = from.id) { id -> terminal(id)?.isRunning == true }
        return ids.mapNotNull { if (it == from.id) from else terminal(it) }
    }

    /** Text or control codes that mean the same on every terminal. */
    fun write(from: TerminalSession, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        targets(from).forEach { it.writeBytes(bytes) }
    }

    /** Input whose bytes depend on the receiving terminal (cursor keys). [build] returns null to send nothing. */
    fun writeKey(from: TerminalSession, build: (TerminalSession) -> ByteArray?) {
        targets(from).forEach { t -> build(t)?.let { if (it.isNotEmpty()) t.writeBytes(it) } }
    }

    /** Pastes into every target; each wraps it in its own bracketed-paste markers when enabled. */
    fun paste(from: TerminalSession, text: String) {
        targets(from).forEach { it.pasteText(text, syncClipboard = false) }
    }
}
