package com.zerotoship.z2term.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal object QrShareManager {
    enum class Phase { IDLE, PREPARING, CONNECTING, READY, STOPPING, STOPPED, ERROR }
    data class State(val id: String = "", val phase: Phase = Phase.IDLE, val name: String = "",
        val size: Long = 0, val url: String = "", val expires: Long = 0, val sent: Long = 0,
        val error: String = "") {
        val active get() = phase in listOf(Phase.PREPARING, Phase.CONNECTING, Phase.READY, Phase.STOPPING)
    }
    private val mutable = MutableStateFlow(State())
    private var serviceId: String? = null
    val state = mutable.asStateFlow()

    @Synchronized fun start(context: Context, uri: Uri, filename: String): String {
        check(!mutable.value.active && serviceId == null) { context.getString(R.string.qr_share_busy) }
        val id = UUID.randomUUID().toString()
        mutable.value = State(id, Phase.PREPARING, filename.take(255))
        serviceId = id
        try {
            ContextCompat.startForegroundService(context, Intent(context, QrShareService::class.java)
                .setAction(QrShareService.START).setData(uri).putExtra("id", id))
        } catch (e: Exception) {
            serviceId = null
            mutable.value = mutable.value.copy(phase = Phase.ERROR, error = context.getString(R.string.qr_share_start_failed))
            throw e
        }
        return id
    }

    @Synchronized fun update(id: String, change: (State) -> State) {
        if (mutable.value.id == id && mutable.value.active) {
            val next = change(mutable.value)
            if (mutable.value.phase != Phase.STOPPING || next.phase == Phase.STOPPED) mutable.value = next
        }
    }

    @Synchronized fun released(id: String) {
        if (serviceId == id) serviceId = null
        update(id) { it.copy(phase = Phase.STOPPED, url = "") }
    }

    @Synchronized fun stop(context: Context) {
        val current = mutable.value
        update(current.id) { it.copy(phase = Phase.STOPPING, url = "") }
        if (!context.stopService(Intent(context, QrShareService::class.java))) {
            released(current.id)
        }
    }

    /** The CLI supplies its HOME so /root and native Android HOME address the same files. */
    @android.annotation.SuppressLint("SdCardPath") // Parse guest aliases; resolve their real destination through Environment.
    fun cliFile(context: Context, path: String, guestHome: String, distro: String): File {
        require(path.startsWith('/') && guestHome.startsWith('/')) { "Absolute file path and HOME required" }
        val shared = File(context.filesDir, "shared_home").canonicalFile
        val external = android.os.Environment.getExternalStorageDirectory().canonicalFile
        val guest = guestHome.trimEnd('/')
        val overlay = if (distro.isNotEmpty()) {
            require(com.zerotoship.z2term.distro.DistroSpec.byId(distro) != null)
            File(context.filesDir, "home_overlay/$distro").canonicalFile
        } else null
        val raw = when {
            path.startsWith("$guest/") -> {
                require(if (overlay != null) guest == "/root" else File(guest).canonicalFile == shared) {
                    context.getString(R.string.qr_share_path_scope)
                }
                val relative = path.removePrefix("$guest/")
                require(relative.split('/').none { it == ".." })
                if (overlay != null) com.zerotoship.z2term.proot.ProotLauncher(context).homeFileForSharing(distro, relative)
                else File(shared, relative)
            }
            path.startsWith("/sdcard/") -> File(external, path.removePrefix("/sdcard/"))
            path.startsWith("/storage/shared/") -> File(external, path.removePrefix("/storage/shared/"))
            else -> File(path)
        }.canonicalFile
        require(raw.path.startsWith(shared.path + "/") || raw.path.startsWith(external.path + "/") ||
            overlay != null && raw.path.startsWith(overlay.path + "/")) {
            context.getString(R.string.qr_share_path_scope)
        }
        require(raw.isFile && raw.canRead()) { context.getString(R.string.qr_share_file_failed) }
        return raw
    }
}
