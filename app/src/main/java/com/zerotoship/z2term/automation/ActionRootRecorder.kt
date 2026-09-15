package com.zerotoship.z2term.automation

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit live-recording mode only. getevent reads the device without grabbing it. */
internal class ActionRootRecorder(private val screen: ActionDefinition.Screen) {
    private val closed = AtomicBoolean(false)
    private val processLock = Any()
    private var process: Process? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun launchProcess(vararg command: String): Process = synchronized(processLock) {
        check(!closed.get()) { "Recording was stopped" }
        ProcessBuilder(*command).redirectErrorStream(true).start().also { process = it }
    }

    fun start(ready: () -> Unit, frame: (ActionInputEvents.Frame) -> Unit, failed: (String) -> Unit) {
        job = scope.launch {
            try {
                val lookup = launchProcess("/system/bin/sh", "-c", "command -v su")
                val binary = lookup.inputStream.bufferedReader().use { it.readText().trim() }
                check(lookup.waitFor() == 0 && binary.startsWith("/") && '\n' !in binary) { "Root access (su) is required" }
                ensureActive()
                val probe = launchProcess(binary, "-c", "exec /system/bin/getevent -lp")
                val description = probe.inputStream.bufferedReader().use { it.readText() }
                check(probe.waitFor() == 0) { "Root access or input-device access was denied" }
                val devices = ActionInputEvents.devices(description)
                check(devices.size == 1) {
                    if (devices.isEmpty()) "No supported direct Type-B touchscreen was found"
                    else "More than one touchscreen is present; live recording cannot choose the display reliably"
                }
                ensureActive()
                val device = devices.single()
                val decoder = ActionInputEvents(device, screen)
                // The stdin watcher also stops getevent if this app process disappears.
                // Keep getevent as a child of the waiting shell; never kill by process name.
                val reader = launchProcess(binary, "-c", readerCommand(device.path))
                reader.inputStream.bufferedReader().use { input ->
                    while (!closed.get()) {
                        val line = input.readLine() ?: break
                        if (line.startsWith("Z2RECORD_PID=")) {
                            check(line.substringAfter('=').toIntOrNull()?.let { it > 1 } == true) { "Could not track the input reader" }
                            withContext(Dispatchers.Main.immediate) { if (!closed.get()) ready() }
                        } else decoder.line(line)?.let { event ->
                            withContext(Dispatchers.Main.immediate) { if (!closed.get()) frame(event) }
                        }
                    }
                }
                if (!closed.get()) error("Input recording ended unexpectedly")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) { if (!closed.get()) failed(e.message ?: "Live recording failed") }
            } finally { stop() }
        }
    }

    fun stop() {
        if (!closed.compareAndSet(false, true)) return
        job?.cancel()
        val owned = synchronized(processLock) { process.also { process = null } }
        CoroutineScope(Dispatchers.IO).launch {
            // EOF releases the watcher's read, which terminates only this session's input reader.
            runCatching { owned?.outputStream?.close() }
            runCatching {
                if (owned != null && !owned.waitFor(2, TimeUnit.SECONDS)) owned.destroy()
            }
            scope.cancel()
        }
    }

    companion object {
        internal fun readerCommand(path: String): String {
            require(path.matches(Regex("/dev/input/event[0-9]+")))
            return """
                /system/bin/getevent -lt $path &
                z2_record_pid=${'$'}!
                (read -r z2_record_stop; kill -TERM "${'$'}z2_record_pid" 2>/dev/null) <&0 &
                z2_record_guard=${'$'}!
                echo Z2RECORD_PID=${'$'}z2_record_pid
                wait "${'$'}z2_record_pid"
                kill -TERM "${'$'}z2_record_guard" 2>/dev/null
                wait "${'$'}z2_record_guard" 2>/dev/null
            """.trimIndent()
        }
    }
}
