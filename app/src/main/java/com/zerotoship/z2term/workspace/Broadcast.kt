package com.zerotoship.z2term.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession

/**
 * Broadcast: the user's input goes to every ticked tab of every group (1.0.156). Groups only split
 * the tabs that do not fit on one screen, so a tick means the same in any group.
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

    /**
     * Enters broadcast mode with the terminal tabs of **every** group ticked (1.0.156: broadcast is
     * not per group). [groupId] is the group to show; [startOn] comes from the setting.
     */
    fun start(groupId: String, startOn: Boolean) {
        val sessions = SessionManager.sessions.value
        if (Workspace.groups.group(groupId) == null) return
        Workspace.update { st ->
            st.groups.fold(st) { acc, g ->
                acc.checkOnly(g.id, g.members.filter { id -> sessions.any { it.id == id && it is TerminalSession } })
            }.maximize(groupId, null)
        }
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

    /**
     * Bytes already encoded for [from] (input from `z2-session attach`, 1.0.161). [from] gets them as
     * they are; every other target gets them with plain cursor keys rewritten to its own DECCKM form
     * ([retargetCursorKeys]), since the attached terminal encoded them for [from]'s mode.
     */
    fun writeEncoded(from: TerminalSession, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        targets(from).forEach { t ->
            t.writeBytes(if (t === from) bytes else retargetCursorKeys(bytes, t.emulator::cursorKeyBytes))
        }
    }

    /** Pastes into every target; each wraps it in its own bracketed-paste markers when enabled. */
    fun paste(from: TerminalSession, text: String) {
        targets(from).forEach { it.pasteText(text, syncClipboard = false) }
    }
}

/**
 * Rewrites unmodified cursor keys (`ESC [ A`…`D` and `ESC O A`…`D`) with [cursorBytes], which encodes them
 * for the receiving terminal's mode. Everything else (text, control codes, modified keys such as
 * `ESC [ 1 ; 5 A`, which do not depend on the mode) passes through unchanged.
 */
internal fun retargetCursorKeys(bytes: ByteArray, cursorBytes: (com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey) -> ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream(bytes.size)
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i]
        if (b == 0x1B.toByte() && i + 2 < bytes.size && (bytes[i + 1] == '['.code.toByte() || bytes[i + 1] == 'O'.code.toByte())) {
            val key = when (bytes[i + 2].toInt().toChar()) {
                'A' -> com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.UP
                'B' -> com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.DOWN
                'C' -> com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.RIGHT
                'D' -> com.zerotoship.z2term.emulator.TerminalEmulator.CursorKey.LEFT
                else -> null
            }
            if (key != null) { out.write(cursorBytes(key)); i += 3; continue }
        }
        out.write(b.toInt())
        i++
    }
    return out.toByteArray()
}
