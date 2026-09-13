package com.zerotoship.z2term.proot

import com.zerotoship.z2term.pty.PtyProcess
import java.lang.ref.WeakReference

/** Includes headless engines whose foreground script has exited but whose daemons still run. */
internal object DistroProcesses {
    private data class Entry(val process: WeakReference<PtyProcess>, val chroot: Boolean)
    private val processes = mutableMapOf<String, MutableList<Entry>>()

    @Synchronized
    fun register(distro: String, process: PtyProcess, chroot: Boolean = false): PtyProcess {
        val entries = processes.getOrPut(distro) { mutableListOf() }
        entries.removeAll { it.process.get()?.isAlive != true }
        entries.add(Entry(WeakReference(process), chroot))
        return process
    }

    @Synchronized
    fun hasActiveChroot(distro: String): Boolean = processes[distro].orEmpty()
        .any { it.chroot && it.process.get()?.isAlive == true }

    /** Call while holding the distro deletion lease, so no new engine can appear. */
    fun stop(distro: String) {
        val entries = synchronized(this) { processes.remove(distro).orEmpty().mapNotNull { it.process.get() } }
        entries.forEach { it.close() }
    }
}
