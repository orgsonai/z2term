package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DirectShareManager {
    enum class Problem { NONE, NO_PUBLIC_ADDRESS, NETWORK_CHANGED }
    data class State(
        val id: String = "", val active: Boolean = false, val preparing: Boolean = false,
        val name: String = "", val url: String = "", val size: Long = 0,
        val sent: Long = 0, val visited: Boolean = false, val expires: Long = 0,
        val failed: Boolean = false, val stopping: Boolean = false,
        val automatic: Boolean = false, val ipv6: Boolean = false, val problem: Problem = Problem.NONE,
        val folder: Boolean = false, val fileCount: Int = 0,
    )
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()

    @Synchronized fun update(id: String, change: (State) -> State) {
        if (mutable.value.id == id && mutable.value.active) mutable.value = change(mutable.value)
    }

    @Synchronized fun start(context: Context, uri: Uri, name: String, config: DirectShareConfig,
                            certificate: Uri?, password: String, allowHttp: Boolean, folder: Boolean = false) {
        require(config.tls || allowHttp)
        require(!config.tls || certificate != null)
        launch(context, uri, name, config.minutes, false,
            Intent().putExtra("origin", config.origin).putExtra("port", config.port)
                .putExtra("certificate", certificate?.toString()).putExtra("password", password)
                .putExtra("allowHttp", allowHttp), certificate.takeIf { config.tls }, folder)
    }

    @Synchronized fun startAutomatic(context: Context, uri: Uri, minutes: Int, folder: Boolean = false) {
        launch(context, uri, "", minutes, true, Intent(), null, folder)
    }

    private fun launch(context: Context, uri: Uri, name: String, minutes: Int, automatic: Boolean,
                       intent: Intent, certificate: Uri?, folder: Boolean) {
        check(!mutable.value.active && !mutable.value.stopping)
        require(uri.scheme == "content" && minutes in setOf(5, 15, 60))
        require(!folder || android.provider.DocumentsContract.isTreeUri(uri))
        val id = java.util.UUID.randomUUID().toString()
        mutable.value = State(id = id, active = true, preparing = true, name = name, automatic = automatic, folder = folder)
        try {
            ContextCompat.startForegroundService(context, intent.setClass(context, DirectShareService::class.java)
                .setAction(DirectShareService.START).putExtra("id", id).putExtra("file", uri.toString())
                .putExtra("name", name).putExtra("minutes", minutes).putExtra("automatic", automatic).putExtra("folder", folder)
                .apply {
                    data = uri
                    clipData = ClipData.newRawUri("Selected file", uri).apply {
                        if (certificate != null && !folder) addItem(ClipData.Item(certificate))
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (folder) addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                })
            if (folder && certificate != null) {
                // Prefix grants apply to every URI in an Intent. Transfer the certificate separately
                // with its exact read grant; a document grant must never be broadened to a prefix.
                checkNotNull(context.startService(Intent(context, DirectShareService::class.java)
                    .setAction(DirectShareService.CERTIFICATE_GRANT).putExtra("id", id)
                    .setData(certificate).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            }
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(active = false, preparing = false, stopping = true, failed = true)
            if (!context.stopService(Intent(context, DirectShareService::class.java))) finish(id)
            throw e
        }
    }

    @Synchronized fun finish(id: String) {
        if (mutable.value.id == id) mutable.value = mutable.value.copy(active = false, stopping = false, preparing = false, url = "")
    }

    @Synchronized fun stop(context: Context, problem: Problem = Problem.NONE) {
        if (!mutable.value.active) return
        mutable.value = mutable.value.copy(active = false, stopping = true, preparing = false, url = "", problem = problem)
        if (!context.stopService(Intent(context, DirectShareService::class.java))) finish(mutable.value.id)
    }
}
