package com.zerotoship.z2term.edge

import java.io.File

/** Process identity includes start time: a recycled PID must never receive our cleanup signal. */
internal object EdgeTerminalProcesses {
    data class Process(val pid: Int, val parent: Int, val session: Int, val started: Long,
        val hupIgnored: Boolean, val tracer: Int, val zombie: Boolean, val name: String = "")

    fun parse(pid: Int, stat: String, status: String): Process? = runCatching {
        val fields = stat.substring(stat.lastIndexOf(')') + 2).trim().split(Regex("\\s+"))
        val ignored = status.lineSequence().first { it.startsWith("SigIgn:") }.substringAfter(':').trim().toULong(16)
        val tracer = status.lineSequence().first { it.startsWith("TracerPid:") }.substringAfter(':').trim().toInt()
        Process(pid, fields[1].toInt(), fields[3].toInt(), fields[19].toLong(), ignored and 1uL != 0uL,
            tracer, fields[0] == "Z", stat.substringAfter('(').substringBeforeLast(')'))
    }.getOrNull()

    fun read(pid: Int): Process? = runCatching {
        val dir = File("/proc/$pid")
        parse(pid, File(dir, "stat").readText(), File(dir, "status").readText())
    }.getOrNull()

    fun members(session: Int, token: String, engine: Boolean): List<Process> = File("/proc").list().orEmpty().mapNotNull {
        it.toIntOrNull()?.let(::read)?.takeIf { p -> !p.zombie && (p.session == session ||
            engine && p.tracer == session || runCatching {
                File("/proc/${p.pid}/environ").inputStream().use { input ->
                    val bytes = ByteArray(131072)
                    val size = input.read(bytes)
                    size > 0 && String(bytes, 0, size, Charsets.UTF_8).split('\u0000').contains("Z2_EDGE_TERMINAL_ID=$token")
                }
            }.getOrDefault(false)) }
    }

    fun targets(processes: List<Process>, session: Int, protectedPid: Int): List<Process> {
        val tracers = processes.map { it.tracer }.filter { it > 0 }.toSet() + protectedPid
        val keep = processes.filter { it.hupIgnored || it.session != session &&
            (it.name.lowercase() == "screen" || it.name.lowercase().startsWith("tmux")) }.map { it.pid }.toMutableSet()
        // Jobs inside a detached multiplexer belong to its lifetime, including their own PTYs.
        do {
            val count = keep.size
            processes.filter { it.parent in keep }.forEach { keep.add(it.pid) }
        } while (keep.size != count)
        return processes.filter { it.pid !in tracers && it.pid !in keep && !it.zombie }
    }

    fun sameProcess(before: Process, after: Process?): Boolean = after != null &&
        before.pid == after.pid && before.started == after.started && before.session == after.session && !after.zombie
}
