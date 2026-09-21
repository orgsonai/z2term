package com.zerotoship.z2term.gui.direct

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.gui.rfb.RfbClient
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/** Pixels come from Xvfb's shared framebuffer; the existing RFB path carries input and clipboard only. */
internal class DirectDesktopClient(private val source: () -> File, private val port: Int) : RemoteDesktopClient {
    private var control = RfbClient(port = port, inputOnly = true)
    override val width get() = format?.width ?: 0
    override val height get() = format?.height ?: 0
    override val desktopName = "X11"
    @Volatile override var frame: Bitmap? = null
        private set
    override val frameLock = Any()
    override val redraw = MutableStateFlow(0)
    override var onRemoteClipboardText: ((String) -> Unit)?
        get() = control.onRemoteClipboardText
        set(value) { control.onRemoteClipboardText = value }
    override val drawsCursorInFrame = true
    private var format: XwdFrame? = null
    private var file: RandomAccessFile? = null
    private var raw = ByteArray(0)
    private var pixels = IntArray(0)
    private var previous = IntArray(0)
    private val viewers = ConcurrentHashMap.newKeySet<Any>()
    @Volatile private var closed = false
    override fun setViewing(owner: Any, viewing: Boolean) { if (viewing) viewers.add(owner) else viewers.remove(owner) }

    override fun connect(timeoutMs: Int) {
        file?.close(); file = null
        val clipboard = control.onRemoteClipboardText
        control.close()
        control = RfbClient(port = port, inputOnly = true).also { it.onRemoteClipboardText = clipboard }
        closed = false
        // The input server starts after Xvfb has finished creating its framebuffer.
        // A refused connection is retried by GuiSession while packages/server are starting.
        control.connect(timeoutMs)
        var f: RandomAccessFile? = null
        try {
            f = RandomAccessFile(source(), "r")
            val header = ByteArray(100).also { f.readFully(it) }
            val layout = XwdFrame.parse(header, f.length())
            check(control.width == layout.width && control.height == layout.height) { "Input and framebuffer dimensions differ" }
            format = layout; file = f
            raw = ByteArray(layout.bytes)
            pixels = IntArray(layout.width * layout.height)
            previous = IntArray(pixels.size)
            synchronized(frameLock) { frame = createBitmap(layout.width, layout.height) }
            update(force = true)
        } catch (e: Exception) { runCatching { f?.close() }; file = null; control.close(); throw e }
    }

    override fun run() {
        val receiver = Thread({ control.run() }, "direct-input").apply { isDaemon = true; start() }
        try {
            while (!closed && control.connected.value) {
                if (viewers.isNotEmpty()) update()
                Thread.sleep(if (viewers.isEmpty()) 250 else 50)
            }
        } finally { control.close(); runCatching { file?.close() }; file = null; receiver.join(1000) }
    }
    private fun update(force: Boolean = false) {
        val layout = format ?: return
        val f = file ?: return
        f.seek(layout.offset); f.readFully(raw)
        ByteBuffer.wrap(raw).order(layout.order).asIntBuffer().get(pixels)
        for (i in pixels.indices) pixels[i] = pixels[i] or 0xFF000000.toInt()
        if (force || !pixels.contentEquals(previous)) {
            synchronized(frameLock) { frame?.setPixels(pixels, 0, layout.width, 0, 0, layout.width, layout.height) }
            val swap = previous; previous = pixels; pixels = swap
            redraw.value++
        }
    }
    override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = control.sendPointerEvent(buttonMask, x, y)
    override fun sendKeyEvent(keysym: Int, down: Boolean) = control.sendKeyEvent(keysym, down)
    override fun sendClipboardText(text: String) = control.sendClipboardText(text)
    override fun close() {
        closed = true; control.close()
        runCatching { file?.close() }; file = null
    }
}
