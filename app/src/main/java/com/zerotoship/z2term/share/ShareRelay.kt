package com.zerotoship.z2term.share

import android.content.Context
import android.os.SystemClock
import com.jcraft.jsch.Session
import com.zerotoship.z2term.channel.SshLink
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshSessionFactory
import com.zerotoship.z2term.channel.KnownHostsHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Connect only to a user-configured relay. No provider discovery or remote commands. */
internal class ShareRelay(
    private val context: Context,
    private val server: DirectFileServer,
    private val profile: SshProfile,
    private val config: ShareRelayConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build(),
) : Closeable {
    private val sessions = ArrayList<Session>()
    @Volatile private var link: SshLink? = null
    @Volatile private var closed = false

    @Synchronized private fun own(value: Session) {
        if (closed) { value.disconnect(); throw IOException("Sharing stopped") }
        sessions += value
    }

    suspend fun connect(): String = withContext(Dispatchers.IO) {
        ensureActive()
        require(profile.hasSsh && profile.id == config.profileId)
        try {
            KnownHostsHolder.repository(context).awaitLoaded()
            // Unknown or changed keys must fail without a background trust dialog. Verify the
            // chosen profile from the SSH tab first. Own every hop before starting its connection.
            val opened = SshSessionFactory.create(profile, context, strictHostKeys = true, onOpening = ::own)
            link = opened
            ensureActive()
            check(!closed)
            opened.enableKeepAlive(15_000, 3)
            opened.connect()
            ensureActive()
            check(!closed)
            opened.session.setPortForwardingR("127.0.0.1", config.remotePort, "127.0.0.1", server.localPort)
            val deadline = SystemClock.elapsedRealtime() + 90_000
            while (true) {
                ensureActive()
                requireRunning()
                if (healthy()) break
                if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Relay HTTPS check timed out")
                delay(1500)
            }
            ensureActive()
            requireRunning()
            server.publishViaRelay(config.origin)
            server.url
        } catch (e: Exception) {
            close()
            throw e
        } finally {
            if (closed) link?.close()
        }
    }

    suspend fun monitor(onReachable: (Boolean) -> Unit): Unit = withContext(Dispatchers.IO) {
        var failures = 0
        while (true) {
            delay(15_000)
            requireRunning()
            val reachable = healthy()
            ensureActive()
            onReachable(reachable)
            failures = if (reachable) 0 else failures + 1
            if (failures >= 4) throw IOException("Relay HTTPS connection lost")
        }
    }

    private fun requireRunning() {
        if (closed || link?.isConnected != true) throw IOException("Sharing relay disconnected")
    }

    private fun healthy(): Boolean = try {
        client.newCall(Request.Builder().url(config.origin + server.path + "health")
            .header("Cache-Control", "no-cache").build()).execute().use {
            it.code == 200 && it.peekBody(128).string() == server.token
        }
    } catch (_: IOException) { false }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        link?.close()
        sessions.asReversed().forEach { runCatching { it.disconnect() } }
        sessions.clear()
    }
}
