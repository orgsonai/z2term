package com.zerotoship.z2term.core

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.distro.DistroDataDeletion
import com.zerotoship.z2term.distro.DistroOperations
import com.zerotoship.z2term.distro.DistroSpec
import com.zerotoship.z2term.gui.GuiSession
import com.zerotoship.z2term.proot.DistroProcesses
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Outlives the Settings sheet and the terminal that requested deletion. Call from the main thread. */
object DistroDeletion {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _deleting = MutableStateFlow<String?>(null)
    val deleting = _deleting.asStateFlow()

    fun delete(context: Context, id: String, onComplete: (Throwable?) -> Unit) {
        if (_deleting.value != null) { onComplete(DistroOperations.Busy()); return }
        _deleting.value = id
        val app = context.applicationContext
        scope.launch {
            val paused = mutableListOf<TerminalSession>()
            var next: DistroSpec? = null
            val result = runCatching {
                DistroOperations.delete(id).use {
                    val settings = AppSettings(app)
                    val snapshot = settings.flow.first()
                    val launcher = ProotLauncher(app)
                    val data = DistroDataDeletion(app.filesDir, app.cacheDir, id)
                    withContext(Dispatchers.IO) {
                        if (DistroProcesses.hasActiveChroot(id)) throw DistroDataDeletion.Mounted()
                        data.checkUnmounted(File("/proc/self/mountinfo").readText())
                        if (snapshot.rootChrootUnlocked) {
                            data.checkUnmounted(launcher.rootMountInfoForDeletion())
                        }
                    }
                    val sessions = SessionManager.sessions.value.toList()
                    for (terminal in sessions.filterIsInstance<TerminalSession>()) {
                        if (terminal.pauseForDistroDeletion(id)) paused.add(terminal)
                    }
                    sessions.filterIsInstance<GuiSession>()
                        .filter { it.remote == null && (it.distroId ?: snapshot.distroId) == id }
                        .forEach { SessionManager.close(it.id) }
                    withContext(Dispatchers.IO) {
                        ServerDaemonManager.stopForDistro(id)
                        DistroProcesses.stop(id)
                        data.delete()
                    }
                    // Re-read: the user may have selected another OS while deletion was running.
                    val selected = settings.flow.first().distroId
                    val ready = DistroSpec.ALL.filter { it.id != id && launcher.isDistroReady(it.id) }
                    next = ready.firstOrNull { it.id == selected } ?: ready.firstOrNull()
                    if (selected == id && next != null) settings.setDistro(next!!.id)
                    Log.i("DistroDeletion", "Deleted distro=$id; next=${next?.id ?: "android-sh"}")
                }
            }
            // On failure, paused tabs use Android sh so a partly removed rootfs is not reinstalled.
            paused.filter { it in SessionManager.sessions.value }.forEach {
                it.resumeAfterDistroDeletion(if (result.isSuccess) next else null)
            }
            _deleting.value = null
            result.exceptionOrNull()?.let { Log.e("DistroDeletion", "Deletion failed: $id", it) }
            onComplete(result.exceptionOrNull())
        }
    }
}
