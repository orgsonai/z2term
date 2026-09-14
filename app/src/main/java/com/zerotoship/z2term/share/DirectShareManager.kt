package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DirectShareManager {
    enum class Problem { NONE, NETWORK_CHANGED, RELAY_UNAVAILABLE }
    data class State(
        val id: String = "", val active: Boolean = false, val preparing: Boolean = false,
        val name: String = "", val url: String = "", val size: Long = 0,
        val sent: Long = 0, val visited: Boolean = false, val expires: Long = 0,
        val failed: Boolean = false, val stopping: Boolean = false,
        val problem: Problem = Problem.NONE, val folder: Boolean = false,
        val fileCount: Int = 0, val connecting: Boolean = false,
    )
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()

    @Synchronized fun update(id: String, change: (State) -> State) {
        if (mutable.value.id == id && mutable.value.active) mutable.value = change(mutable.value)
    }

    @Synchronized fun start(context: Context, uri: Uri, config: ShareRelayConfig, folder: Boolean = false) {
        check(!mutable.value.active && !mutable.value.stopping)
        require(uri.scheme == "content")
        require(!folder || android.provider.DocumentsContract.isTreeUri(uri))
        val id = java.util.UUID.randomUUID().toString()
        mutable.value = State(id = id, active = true, preparing = true, folder = folder)
        try {
            // Only profile IDs and non-secret relay settings cross the Intent boundary.
            ContextCompat.startForegroundService(context, Intent(context, DirectShareService::class.java)
                .setAction(DirectShareService.START).putExtra("id", id).putExtra("file", uri.toString())
                .putExtra("profile", config.profileId).putExtra("origin", config.origin)
                .putExtra("remotePort", config.remotePort).putExtra("minutes", config.minutes).putExtra("folder", folder)
                .apply {
                    data = uri
                    clipData = ClipData.newRawUri("Selected file", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (folder) addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                })
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(active = false, preparing = false, stopping = true, failed = true)
            if (!context.stopService(Intent(context, DirectShareService::class.java))) finish(id)
            throw e
        }
    }

    @Synchronized fun finish(id: String) {
        if (mutable.value.id == id) mutable.value = mutable.value.copy(active = false, stopping = false, preparing = false, connecting = false, url = "")
    }

    @Synchronized fun stop(context: Context, problem: Problem = Problem.NONE) {
        if (!mutable.value.active) return
        mutable.value = mutable.value.copy(active = false, stopping = true, preparing = false, connecting = false, url = "", problem = problem)
        if (!context.stopService(Intent(context, DirectShareService::class.java))) finish(mutable.value.id)
    }
}
