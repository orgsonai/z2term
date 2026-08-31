package com.zerotoship.z2term.gui.rdp

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.net.HostAddress
import java.io.EOFException
import java.net.SocketException
import java.security.cert.X509Certificate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TLS/NLA/MCS/Activationの上でclassic Bitmap Updateだけを描画するRDPクライアント。
 *
 * 接続先画面・入力・clipboard・resizeは次段階なので、この実装は現時点では内部APIである。
 */
internal class RdpClient(
    private val host: String,
    private val port: Int = 3389,
    private val credentials: CredSspNtlm.Credentials,
    private val settings: RdpMcs.ClientSettings = RdpMcs.ClientSettings(),
    private val certificateVerifier: (X509Certificate) -> Boolean,
) : RemoteDesktopClient {
    @Volatile
    override var width: Int = settings.width
        private set
    @Volatile
    override var height: Int = settings.height
        private set
    override val desktopName: String = HostAddress.hostPort(host, port)

    @Volatile
    override var frame: Bitmap? = null
        private set
    override val frameLock = Any()

    private val _redraw = MutableStateFlow(0)
    override val redraw: StateFlow<Int> = _redraw.asStateFlow()
    override var onRemoteClipboardText: ((String) -> Unit)? = null

    @Volatile private var closed = false
    @Volatile private var transport: RdpTlsTransport? = null
    private var session: RdpMcs.Session? = null
    private var active: RdpActivation.ActiveSession? = null
    private var pixels = IntArray(0)

    override fun connect(timeoutMs: Int) {
        closeTransport()
        closed = false
        val candidate = RdpTlsTransport.connect(
            host = HostAddress.normalize(host),
            port = port,
            timeoutMs = timeoutMs,
            certificateVerifier = certificateVerifier,
        )
        try {
            candidate.authenticate(credentials)
            Log.i(TAG, "RDP: NLA authenticated")
            val connected = candidate.connectMcs(settings)
            Log.i(TAG, "RDP: MCS connected (user=${connected.userChannelId} io=${connected.ioChannelId})")
            val activated = candidate.activate(connected, credentials, settings)
            Log.i(TAG, "RDP: activated (server caps=${activated.serverCapabilities.sorted()})")
            candidate.finalizeConnection(connected, activated)
            Log.i(TAG, "RDP: connection finalized")
            val count = width.toLong() * height.toLong()
            require(count in 1..MAX_FRAME_PIXELS.toLong()) { "RDP framebuffer is too large: ${width}x$height" }
            synchronized(frameLock) {
                pixels = IntArray(count.toInt())
                frame = createBitmap(width, height)
            }
            session = connected
            active = activated
            transport = candidate
            // 相手が自分から描き始めるとは限らないので、こちらから 1 度だけ全画面を要求する。
            candidate.requestRefresh(connected, activated, width, height)
            Log.i(TAG, "RDP connected: ${width}x$height '$desktopName'")
        } catch (e: Exception) {
            candidate.close()
            throw e
        }
    }

    override fun run() {
        val connected = transport ?: return
        val mcs = session ?: return
        val activated = active ?: return
        try {
            while (!closed) {
                val update = connected.readBitmapUpdate(mcs, activated) ?: continue
                val dirty = RdpBitmap.applyUpdate(update, pixels, width, height) ?: continue
                val bitmap = frame ?: continue
                synchronized(frameLock) {
                    bitmap.setPixels(
                        pixels,
                        dirty.top * width + dirty.left,
                        width,
                        dirty.left,
                        dirty.top,
                        dirty.width,
                        dirty.height,
                    )
                }
                _redraw.value = _redraw.value + 1
            }
        } catch (e: Exception) {
            if (!closed && e !is EOFException && e !is SocketException) {
                Log.w(TAG, "RDP receive loop ended", e)
            }
        } finally {
            closeTransport()
        }
    }

    // 今回は受信・描画だけ。未実装機能をwireへ送らない。
    override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = Unit
    override fun sendKeyEvent(keysym: Int, down: Boolean) = Unit

    override fun close() {
        closed = true
        closeTransport()
    }

    private fun closeTransport() {
        val current = transport
        transport = null
        session = null
        active = null
        runCatching { current?.close() }
    }

    companion object {
        private const val TAG = "RdpClient"
        private const val MAX_FRAME_PIXELS = 16 * 1024 * 1024
    }
}
