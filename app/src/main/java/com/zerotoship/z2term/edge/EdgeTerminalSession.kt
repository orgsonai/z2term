package com.zerotoship.z2term.edge

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One panel item owns one shell until explicit stop or refresh. Detached jobs may retain the engine, never the panel or views. */
internal class EdgeTerminalSession(private val launch: (String) -> Launch) : AutoCloseable {
    data class Launch(val process: PtyProcess, val environment: String, val engine: Boolean)
    enum class Phase { NEW, STARTING, IDLE, RUNNING, ENDED, FAILED }
    data class State(val phase: Phase, val output: String, val truncated: Boolean,
        val environment: String, val exitCode: Int?, val error: String?)

    private val lock = Any()
    private val token = UUID.randomUUID().toString().replace("-", "")
    private var phase = Phase.NEW
    private var process: Launch? = null
    private var shell: EdgeTerminalProcesses.Process? = null
    private var pending: String? = null
    private var environment = ""
    private var exitCode: Int? = null
    private var error: String? = null
    private var closed = false
    private var columns = COLUMNS
    /** The running command was already asked to end; the next request kills it. */
    private var interrupted = false
    private var monitor: ScheduledFuture<*>? = null
    private val protocol = EdgeTerminalProtocol(token) { event ->
        when {
            event.startsWith("ready:") -> {
                val pid = event.substringAfter(':').toIntOrNull()
                shell = pid?.let(EdgeTerminalProcesses::read)
                check(shell != null) { "Cannot identify the terminal shell" }
                if (!closed) {
                    phase = Phase.IDLE
                    pending?.let { send(it) }
                }
            }
            event.startsWith("done:") && !closed -> {
                exitCode = event.substringAfter(':').toIntOrNull()
                phase = Phase.IDLE
                interrupted = false
            }
        }
    }

    fun state(): State = synchronized(lock) {
        State(phase, protocol.text, protocol.truncated, environment, exitCode, error)
    }

    fun clear() = synchronized(lock) { protocol.clear() }

    fun execute(command: String): Boolean = synchronized(lock) {
        require(command.isNotBlank() && command.length <= 16384 &&
            command.none { it == '\n' || it == '\r' || it == '\u0000' }) { "Enter a command on one line (up to 16384 characters)" }
        if (closed || phase !in setOf(Phase.NEW, Phase.IDLE)) return false
        exitCode = null; error = null
        pending = command
        if (phase == Phase.NEW) {
            phase = Phase.STARTING
            Thread(::start, "edge-terminal-reader").apply { isDaemon = true }.start()
        } else send(command)
        true
    }

    /** Called with lock; the dedicated writer must never block the main/UI thread. */
    private fun send(command: String) {
        pending = null
        phase = Phase.RUNNING
        interrupted = false
        write(EdgeTerminalProtocol.commandLine(token, command))
    }

    private fun write(text: String) {
        workers.execute {
            val p = synchronized(lock) { if (closed) null else process?.process }
            if (p != null) runCatching {
                p.writer.write(text.toByteArray(Charsets.UTF_8)); p.writer.flush()
            }.onFailure { fail(it) }
        }
    }

    /**
     * Answers the running command with one line; an empty answer is just Enter.
     * A line that arrives after the command ended is dropped by the shell loop, never run.
     */
    fun answer(text: String): Boolean = synchronized(lock) {
        require(text.none { it == '\n' || it == '\r' || it == '\u0000' } &&
            text.toByteArray(Charsets.UTF_8).size <= ANSWER_BYTES) { "Enter an answer on one line (up to $ANSWER_BYTES bytes)" }
        if (closed || phase != Phase.RUNNING) return false
        write(text + "\n")
        true
    }

    /**
     * Ends only the running command and keeps the shell: the terminal's foreground job gets SIGTERM,
     * then SIGKILL on a second request. The interrupt character is not used, because a shell started
     * with a command string exits when its job dies from SIGINT.
     * Returns false when there is no separate job to signal (a shell builtin is running).
     */
    fun interrupt(): Boolean {
        val (pid, again) = synchronized(lock) {
            if (closed || phase != Phase.RUNNING) return false
            (shell?.pid ?: return false) to interrupted
        }
        val job = EdgeTerminalProcesses.foregroundJob(pid) ?: return false
        synchronized(lock) { interrupted = true }
        return runCatching { Os.kill(-job, if (again) OsConstants.SIGKILL else OsConstants.SIGTERM) }.isSuccess
    }

    /** Matches the terminal width to the result view, so tables and progress lines are not wrapped twice. */
    fun resize(columns: Int) {
        val p = synchronized(lock) {
            if (closed || columns == this.columns) return
            this.columns = columns
            process?.process
        }
        if (p != null) workers.execute { runCatching { p.resize(ROWS, columns) } }
    }

    private fun start() {
        val started = System.nanoTime()
        val launched = try { launch(EdgeTerminalProtocol.script(token)) } catch (e: Exception) { fail(e); return }
        synchronized(lock) {
            process = launched
            environment = launched.environment
            runCatching { launched.process.resize(ROWS, columns) }
            if (closed) workers.execute { terminate(launched) }
            else monitor = clock.scheduleWithFixedDelay({
                val current = synchronized(lock) { shell }
                if (current != null && !EdgeTerminalProcesses.sameProcess(current, EdgeTerminalProcesses.read(current.pid))) close()
                else if (current == null && System.nanoTime() - started > TimeUnit.SECONDS.toNanos(30))
                    fail(IllegalStateException("Terminal startup timed out"))
            }, 250, 250, TimeUnit.MILLISECONDS)
        }
        try {
            val reader = InputStreamReader(launched.process.reader, Charsets.UTF_8)
            val chars = CharArray(4096)
            while (true) {
                val n = reader.read(chars)
                if (n < 0) break
                synchronized(lock) { if (!closed) protocol.append(String(chars, 0, n)) }
            }
        } catch (e: Exception) {
            // Android PTYs report EIO at EOF. A vanished shell is a normal session exit.
            if (synchronized(lock) { shell == null && !closed }) fail(e)
        } finally {
            close()
            // Do not close/kill the tracer: hangup-ignoring and detached jobs can still need it.
            // Keep draining the PTY above, then reap and detach only after all tracees exit.
            runCatching { launched.process.waitFor() }
            launched.process.detach()
        }
    }

    private fun fail(failure: Throwable) {
        synchronized(lock) {
            if (closed) return
            error = failure.message ?: failure.javaClass.simpleName
            phase = Phase.FAILED
        }
        close()
    }

    override fun close() {
        val launched = synchronized(lock) {
            if (closed) return
            closed = true; pending = null
            if (phase != Phase.FAILED) phase = Phase.ENDED
            monitor?.cancel(false); monitor = null
            process
        }
        if (launched != null) workers.execute { terminate(launched) }
    }

    private fun terminate(launched: Launch) {
        val sessionId = launched.process.shellPid
        if (synchronized(lock) { shell == null }) {
            // No user command can have been submitted before the ready handshake.
            // Include the engine in cleanup of a fork-before-first-tracee startup race.
            val root = EdgeTerminalProcesses.read(sessionId) ?: return
            runCatching { Os.kill(sessionId, OsConstants.SIGTERM) }
            Thread.sleep(750)
            if (EdgeTerminalProcesses.sameProcess(root, EdgeTerminalProcesses.read(sessionId)))
                runCatching { Os.kill(sessionId, OsConstants.SIGKILL) }
            return
        }
        val protectedPid = if (launched.engine) sessionId else -1
        val targets = EdgeTerminalProcesses.targets(EdgeTerminalProcesses.members(sessionId, token, launched.engine), sessionId, protectedPid)
        fun signal(p: EdgeTerminalProcesses.Process, signal: Int) {
            val now = EdgeTerminalProcesses.read(p.pid)
            if (EdgeTerminalProcesses.sameProcess(p, now) && now?.hupIgnored == false)
                runCatching { Os.kill(p.pid, signal) }
        }
        // Children first, shell last. Only explicit hangup opt-outs / detached multiplexers survive.
        val shellPid = synchronized(lock) { shell?.pid } ?: sessionId
        targets.sortedBy { it.pid == shellPid }.forEach { signal(it, OsConstants.SIGHUP) }
        Thread.sleep(750)
        targets.forEach { signal(it, OsConstants.SIGTERM) }
        Thread.sleep(250)
        targets.forEach { signal(it, OsConstants.SIGKILL) }
    }

    companion object {
        const val COLUMNS = 80
        private const val ROWS = 24
        /** A canonical-mode line holds 4095 bytes; stay well inside it. */
        private const val ANSWER_BYTES = 2048
        private val workers = Executors.newCachedThreadPool { Thread(it, "edge-terminal-io").apply { isDaemon = true } }
        private val clock = Executors.newSingleThreadScheduledExecutor { Thread(it, "edge-terminal-watch").apply { isDaemon = true } }

        fun create(context: Context): EdgeTerminalSession {
            val app = context.applicationContext
            return EdgeTerminalSession { script ->
                val launcher = ProotLauncher(app)
                if (!launcher.hasAnyDistro()) Launch(launcher.launchAndroidSh(extraArgs = listOf("-i", "-c", script)), "Android sh", false)
                else {
                    val settings = runBlocking { AppSettings(app).flow.first() }
                    val id = settings.distroId
                    check(File(app.filesDir, "distros/$id").isDirectory) { "Local environment is missing: $id" }
                    val spec = DistroSpec.byId(id) ?: DistroSpec.ALPINE
                    val shell = shellFor(launcher.loginShell(id, spec.defaultShell))
                    Launch(launcher.launch(distroId = id, command = shell, fallbackShell = spec.defaultShell,
                        extraArgs = listOf("-i", "-c", script), waitTracees = true),
                        "$id ${shell.substringAfterLast('/')}", true)
                }
            }
        }

        /**
         * The shell a terminal tab starts, so aliases, functions and PATH from its startup file match (1.0.10).
         * The session loop is written in sh syntax; a login shell that cannot read it keeps /bin/sh.
         */
        fun shellFor(loginShell: String): String =
            if (loginShell.substringAfterLast('/') in SH_SYNTAX) loginShell else "/bin/sh"

        private val SH_SYNTAX = setOf("sh", "bash", "ash", "dash", "zsh", "ksh", "mksh")
    }
}
