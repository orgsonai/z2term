package com.zerotoship.z2term.gui.rdp

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.net.HostAddress
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TLS/NLA/MCS/Activationの上でclassic Bitmap UpdateとRDP Graphics Pipelineを描画する。
 *
 * 画面更新、ポインター・キー入力、CLIPRDRテキスト共有に対応する。resizeはまだ送らない。
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
    private var cliprdr: RdpCliprdr? = null
    private var cliprdrChannelId: Int? = null
    private var dynamicChannel: RdpDynamicChannel? = null
    private var drdynvcChannelId: Int? = null
    @Volatile private var displayControl: RdpDisplayControl? = null
    private var pixels = IntArray(0)
    private val rdpInput = RdpInput()
    /** UI thread で network I/O をせず、入力と clipboard の送信順を保つ。 */
    private val sender = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rdp-sender").apply { isDaemon = true }
    }

    override fun connect(timeoutMs: Int) {
        closeTransport()
        closed = false
        rdpInput.reset()
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
            Log.i(
                TAG,
                "RDP: MCS connected (user=${connected.userChannelId} io=${connected.ioChannelId} " +
                    "channels=${connected.staticChannels})",
            )
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
            connected.staticChannels["cliprdr"]?.let { channelId ->
                cliprdrChannelId = channelId
                cliprdr = RdpCliprdr(
                    sendMessage = { message ->
                        candidate.sendVirtualChannel(connected, channelId, message)
                    },
                    onRemoteText = { text -> onRemoteClipboardText?.invoke(text) },
                ).also { it.start() }
            }
            connected.staticChannels["drdynvc"]?.let { channelId ->
                lateinit var dynamic: RdpDynamicChannel
                val graphics = RdpGfx(
                    send = { message -> dynamic.sendGraphics(message) },
                    onFrame = { frameWidth, frameHeight, framePixels, dirty ->
                        publishGraphicsFrame(frameWidth, frameHeight, framePixels, dirty)
                    },
                )
                val display = RdpDisplayControl(send = { message -> dynamic.sendDisplayControl(message) })
                dynamic = RdpDynamicChannel(
                    sendStatic = { message -> candidate.sendVirtualChannel(connected, channelId, message) },
                    graphics = graphics,
                    displayControl = display,
                )
                drdynvcChannelId = channelId
                dynamicChannel = dynamic
                displayControl = display
            }
            // 相手が自分から描き始めるとは限らないので、こちらから 1 度だけ全画面を要求する。
            candidate.requestRefresh(connected, activated, width, height)
            Log.i(TAG, "RDP connected: ${width}x$height '$desktopName'")
        } catch (e: Exception) {
            Log.e(TAG, "RDP connect failed", e)
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
                val incoming = connected.readChannelData()
                if (incoming.channelId == cliprdrChannelId) {
                    cliprdr?.acceptChannelChunk(incoming.payload)
                    continue
                }
                if (incoming.channelId == drdynvcChannelId) {
                    dynamicChannel?.acceptStaticChunk(incoming.payload)
                    continue
                }
                if (incoming.channelId != mcs.ioChannelId) continue
                val update = RdpActivation.bitmapUpdatePayload(incoming.payload, activated) ?: continue
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

    override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) {
        if (closed) return
        val px = x.coerceIn(0, (width - 1).coerceAtLeast(0))
        val py = y.coerceIn(0, (height - 1).coerceAtLeast(0))
        submitWrite {
            val current = transport ?: return@submitWrite
            val mcs = session ?: return@submitWrite
            val activated = active ?: return@submitWrite
            current.sendInputEvents(
                mcs,
                activated,
                rdpInput.pointerEvents(buttonMask, px, py),
            )
        }
    }

    override fun sendKeyEvent(keysym: Int, down: Boolean) {
        if (closed || keysym == 0) return
        submitWrite {
            val current = transport ?: return@submitWrite
            val mcs = session ?: return@submitWrite
            val activated = active ?: return@submitWrite
            current.sendInputEvents(mcs, activated, rdpInput.keyEvents(keysym, down))
        }
    }

    override fun sendClipboardText(text: String) {
        if (closed) return
        submitWrite { cliprdr?.announceLocalText(text) }
    }

    /**
     * ⭐ **RDP のデスクトップはこちらのもの。** 接続のたびに新しいセッションを作らせるので、
     * 端末を回したら作り直させてよい (→ [RemoteDesktopClient.ownsDesktopSize])。
     */
    override val ownsDesktopSize: Boolean = true

    override fun requestDesktopSize(width: Int, height: Int) {
        if (closed || width <= 0 || height <= 0) return
        val display = displayControl ?: return
        // ⚠ **接続時と同じ丸め方を通す** ([RdpTarget.fitDesktopSize]: 横長・4 の倍数・640〜4096)。
        //   ここだけ別の決め方をすると、回すたびに端の帯の出方が変わる。
        val (fitWidth, fitHeight) = RdpTarget.fitDesktopSize(width, height)
        submitWrite { display.requestSize(fitWidth, fitHeight) }
    }

    override fun close() {
        closed = true
        runCatching { sender.shutdownNow() }
        closeTransport()
    }

    private fun submitWrite(block: () -> Unit) {
        if (closed) return
        try {
            sender.execute {
                if (closed) return@execute
                try {
                    block()
                } catch (e: IOException) {
                    if (!closed) Log.w(TAG, "RDP input send failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
            // close 後。無視。
        }
    }

    private fun closeTransport() {
        val current = transport
        transport = null
        session = null
        active = null
        cliprdr = null
        cliprdrChannelId = null
        dynamicChannel = null
        drdynvcChannelId = null
        runCatching { current?.close() }
    }

    private fun publishGraphicsFrame(
        frameWidth: Int,
        frameHeight: Int,
        framePixels: IntArray,
        dirty: android.graphics.Rect,
    ) {
        if (frameWidth <= 0 || frameHeight <= 0 || framePixels.size != frameWidth * frameHeight) return
        synchronized(frameLock) {
            if (width != frameWidth || height != frameHeight || frame == null) {
                width = frameWidth
                height = frameHeight
                pixels = IntArray(framePixels.size)
                frame = createBitmap(frameWidth, frameHeight)
                Log.i(TAG, "RDPGFX framebuffer: ${frameWidth}x$frameHeight")
            }
            framePixels.copyInto(pixels)
            frame?.setPixels(
                pixels,
                dirty.top * frameWidth + dirty.left,
                frameWidth,
                dirty.left,
                dirty.top,
                dirty.width(),
                dirty.height(),
            )
        }
        _redraw.value = _redraw.value + 1
    }

    companion object {
        private const val TAG = "RdpClient"
        private const val MAX_FRAME_PIXELS = 16 * 1024 * 1024
    }
}
