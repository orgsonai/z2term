package com.zerotoship.z2term.edge

import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.proot.AndroidShellEnvironment
import com.zerotoship.z2term.pty.PtyProcess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

@RunWith(AndroidJUnit4::class)
class EdgeTerminalSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun session() = EdgeTerminalSession { script ->
        EdgeTerminalSession.Launch(ProotLauncher(context).launchAndroidSh(extraArgs = listOf("-i", "-c", script)), "Android sh", false)
    }
    private fun engineSession() = EdgeTerminalSession { script ->
        val prepared = AndroidShellEnvironment.prepare(context, "")
        val engine = File(context.applicationInfo.nativeLibraryDir, "libz2root.so").absolutePath
        EdgeTerminalSession.Launch(PtyProcess.create(engine,
            arrayOf("z2root", "--kill-on-exit", "--wait-tracees", "-r", "/", "-w", prepared.home.absolutePath,
                "/system/bin/sh", "-i", "-c", script), prepared.env, prepared.home.absolutePath), "engine test", true)
    }
    private fun until(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(30)
        assertTrue(message, condition())
    }
    private fun run(session: EdgeTerminalSession, command: String): EdgeTerminalSession.State {
        assertTrue(session.state().toString(), session.execute(command))
        until("Command did not finish: $command; ${session.state()}") {
            session.state().phase in setOf(EdgeTerminalSession.Phase.IDLE, EdgeTerminalSession.Phase.FAILED, EdgeTerminalSession.Phase.ENDED)
        }
        return session.state().also { assertEquals(it.toString(), EdgeTerminalSession.Phase.IDLE, it.phase) }
    }
    private fun process(output: String, name: String): EdgeTerminalProcesses.Process {
        val pid = Regex("$name=(\\d+)").find(output)?.groupValues?.get(1)?.toInt()
        assertNotNull(output, pid)
        return EdgeTerminalProcesses.read(pid!!)!!
    }
    private fun alive(p: EdgeTerminalProcesses.Process?) = p != null && EdgeTerminalProcesses.sameProcess(p, EdgeTerminalProcesses.read(p.pid))
    private fun kill(p: EdgeTerminalProcesses.Process?) {
        if (p != null && alive(p)) runCatching { Os.kill(p.pid, OsConstants.SIGKILL) }
    }

    @Test fun directoryAndVariablesPersistAndRunsAppendOutputUntilExplicitReset() {
        val first = session()
        val second = session()
        try {
            val home = run(first, "pwd").output.trim()
            assertTrue(home, home.endsWith("/shared_home"))
            first.clear()
            assertEquals(0, run(first, "cd /; export Z2_EDGE_TEST=remember; printf first").exitCode)
            assertEquals("first/\nremember", run(first, "pwd; printf '%s' \"\$Z2_EDGE_TEST\"").output.trim())
            val failed = run(first, "printf broken >&2; false")
            assertEquals("first/\nrememberbroken", failed.output.trim()); assertEquals(1, failed.exitCode)
            first.close()
            assertEquals("$home\nempty", run(second, "pwd; printf '%s' \"\${Z2_EDGE_TEST-empty}\"").output.trim())
        } finally { first.close(); second.close() }
    }

    @Test fun recreatingViewsKeepsRunningShellAndRefreshIsTheOnlyReset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val retained = EdgeTerminalState { session() }
        var view: EdgeTerminalUi? = null
        fun ui(block: (EdgeTerminalUi) -> Unit) = instrumentation.runOnMainSync {
            val current = view ?: EdgeTerminalUi(context, "", retained).also { view = it }
            block(current)
        }
        try {
            ui {
                val input = it.findViewById<android.widget.EditText>(com.zerotoship.z2term.R.id.edge_terminal_input)
                input.setText("cd /; export Z2_EDGE_KEEP=kept; sleep 1; printf HIDDEN")
                input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO)
                input.setText("draft")
                it.dispose(); view = null
            }
            until("Shell must complete while no view exists") { retained.state()?.phase == EdgeTerminalSession.Phase.IDLE }
            ui {
                assertEquals("HIDDEN", retained.state()!!.output)
                assertEquals("draft", it.findViewById<android.widget.EditText>(com.zerotoship.z2term.R.id.edge_terminal_input).text.toString())
                assertTrue(retained.execute("pwd; printf '%s' \"\$Z2_EDGE_KEEP\""))
            }
            until("Second command must finish") { retained.state()?.phase == EdgeTerminalSession.Phase.IDLE }
            assertEquals("HIDDEN/\nkept", retained.state()!!.output)
            ui {
                retained.stop()
                assertFalse(retained.execute("printf should-not-run"))
                retained.refresh()
                assertNull(retained.state())
                assertEquals("", retained.draft)
                assertTrue(retained.execute("printf '%s' \"\${Z2_EDGE_KEEP-empty}\""))
            }
            until("Fresh shell must finish") { retained.state()?.phase == EdgeTerminalSession.Phase.IDLE }
            assertEquals("empty", retained.state()!!.output)
        } finally { instrumentation.runOnMainSync { view?.dispose(); retained.close() } }
    }

    @Test fun closingStopsOrdinaryBackgroundAndForegroundProcesses() {
        val terminal = session()
        var bg: EdgeTerminalProcesses.Process? = null
        var fg: EdgeTerminalProcesses.Process? = null
        try {
            bg = process(run(terminal, "sleep 60 & printf 'BG=%s\\n' \"\$!\"").output, "BG")
            assertTrue(terminal.execute("sleep 60 & printf 'FG=%s\\n' \"\$!\"; wait"))
            until("No foreground output") { terminal.state().output.contains("FG=") }
            fg = process(terminal.state().output, "FG")
            terminal.close()
            until("Background process survived close") { !alive(bg) }
            until("Foreground process survived close") { !alive(fg) }
        } finally { terminal.close(); kill(bg); kill(fg) }
    }

    @Test fun explicitNohupSurvivesCloseWhileOrdinarySiblingEnds() {
        val terminal = session()
        var keep: EdgeTerminalProcesses.Process? = null
        var plain: EdgeTerminalProcesses.Process? = null
        try {
            val out = run(terminal, "nohup sleep 60 </dev/null >/dev/null 2>&1 & printf 'KEEP=%s\\n' \"\$!\"; sleep 60 & printf 'PLAIN=%s\\n' \"\$!\"").output
            keep = process(out, "KEEP"); plain = process(out, "PLAIN")
            until("nohup did not ignore HUP") { EdgeTerminalProcesses.read(requireNotNull(keep).pid)?.hupIgnored == true }
            terminal.close()
            until("Ordinary process survived") { !alive(plain) }
            Thread.sleep(1300)
            assertTrue("nohup was killed", alive(keep))
        } finally { terminal.close(); kill(plain); kill(keep) }
    }

    @Test fun closingDuringStartupCannotLeaveTheLateLaunchedShellRunning() {
        val starting = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val pid = AtomicInteger()
        val terminal = EdgeTerminalSession { script ->
            starting.countDown(); proceed.await(10, TimeUnit.SECONDS)
            val process = ProotLauncher(context).launchAndroidSh(extraArgs = listOf("-i", "-c", script))
            pid.set(process.shellPid)
            EdgeTerminalSession.Launch(process, "Android sh", false)
        }
        try {
            assertTrue(terminal.execute("sleep 60"))
            assertTrue(starting.await(10, TimeUnit.SECONDS))
            terminal.close(); proceed.countDown()
            until("Launch did not finish") { pid.get() != 0 }
            until("Late shell survived close") { EdgeTerminalProcesses.read(pid.get())?.zombie != false }
        } finally { proceed.countDown(); terminal.close() }
    }

    @Test fun enginePreservesNohupWithoutKeepingAnOrdinarySibling() {
        val terminal = engineSession()
        var keep: EdgeTerminalProcesses.Process? = null
        var plain: EdgeTerminalProcesses.Process? = null
        try {
            val out = run(terminal, "nohup sleep 60 </dev/null >/dev/null 2>&1 & printf 'KEEP=%s\\n' \"\$!\"; sleep 60 & printf 'PLAIN=%s\\n' \"\$!\"").output
            keep = process(out, "KEEP"); plain = process(out, "PLAIN")
            assertTrue("Expected traced processes", keep.tracer > 0)
            until("nohup did not ignore HUP") { EdgeTerminalProcesses.read(requireNotNull(keep).pid)?.hupIgnored == true }
            terminal.close()
            until("Ordinary traced job survived close") { !alive(plain) }
            Thread.sleep(1300)
            assertTrue("The engine killed nohup", alive(keep))
        } finally { terminal.close(); kill(plain); kill(keep) }
    }

    @Test fun daemonizedServerIsStillOwnedAfterItChangesItsSession() {
        val terminal = session()
        var daemon: EdgeTerminalProcesses.Process? = null
        try {
            assertTrue(terminal.execute("setsid sh -c 'printf \"DAEMON=%s\\n\" \"\$\$\"; sleep 60' & wait"))
            until("No daemon PID") { terminal.state().output.contains("DAEMON=") }
            daemon = process(terminal.state().output, "DAEMON")
            assertEquals(daemon.pid, daemon.session)
            terminal.close()
            until("Daemonized server survived close") { !alive(daemon) }
        } finally { terminal.close(); kill(daemon) }
    }
}
