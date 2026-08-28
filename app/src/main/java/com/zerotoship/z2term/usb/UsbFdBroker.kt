package com.zerotoship.z2term.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * Android USB Host API が開いた usbfs fd を、同じアプリ UID で動く Linux プロセスへ渡す。
 *
 * `/dev/bus/usb/...` はアプリ UID から直接 open できない。一方 [UsbManager.openDevice] が
 * 返す fd は通常の usbfs fd なので、abstract Unix socket の SCM_RIGHTS で渡せば、受信側は
 * libusb 等を変更せず ioctl を使える。受信側は LD_PRELOAD シム `libz2usb.so`。
 *
 * プロトコルは 1 接続 1 要求: `OPEN /dev/bus/usb/BBB/DDD\n`。応答の 1 byte は errno
 * (0=成功) で、成功時だけ同じ sendmsg に fd を添える。
 */
object UsbFdBroker {
    private const val TAG = "UsbFdBroker"
    private const val SOCKET_PREFIX = "z2term-usb-v1-"
    private const val MAX_REQUEST = 512

    private val acceptExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "z2usb-accept").apply { isDaemon = true }
    }
    private val clientExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "z2usb-client").apply { isDaemon = true }
    }

    @Volatile private var server: LocalServerSocket? = null

    /** z2root へ `Z2USB_SOCKET` として渡す abstract socket 名。 */
    fun socketName(): String = "$SOCKET_PREFIX${Process.myUid()}"

    /** Application.onCreate から呼ぶ。二重起動しても何もしない。 */
    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val opened = try {
            LocalServerSocket(socketName())
        } catch (e: Exception) {
            Log.e(TAG, "cannot listen on @${socketName()}", e)
            return
        }
        server = opened
        acceptExecutor.execute {
            Log.i(TAG, "listening on @${socketName()}")
            while (server === opened) {
                try {
                    val client = opened.accept()
                    clientExecutor.execute { handle(app, client) }
                } catch (e: Exception) {
                    if (server === opened) Log.w(TAG, "accept failed", e)
                    break
                }
            }
        }
    }

    private fun handle(context: Context, client: LocalSocket) {
        client.use { socket ->
            try {
                val peer = socket.peerCredentials
                if (peer.uid != Process.myUid()) {
                    Log.w(TAG, "denied peer uid=${peer.uid}")
                    replyError(socket, ERR_PERMISSION)
                    return
                }

                val request = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                    .readLine()
                    ?.take(MAX_REQUEST)
                    .orEmpty()
                if (!request.startsWith("OPEN ")) {
                    replyError(socket, ERR_INVALID)
                    return
                }
                val path = request.removePrefix("OPEN ")
                if (!isUsbfsPath(path)) {
                    replyError(socket, ERR_INVALID)
                    return
                }

                val manager = context.getSystemService(UsbManager::class.java)
                val device = manager.deviceList.values.firstOrNull { it.deviceName == path }
                if (device == null) {
                    replyError(socket, ERR_NO_DEVICE)
                    return
                }
                if (!manager.hasPermission(device)) {
                    replyError(socket, ERR_PERMISSION)
                    return
                }

                val connection = manager.openDevice(device)
                if (connection == null) {
                    replyError(socket, ERR_IO)
                    return
                }
                try {
                    // fromFd は dup を作る。送信完了後にこちらを閉じても、受信側へ渡った fd は残る。
                    ParcelFileDescriptor.fromFd(connection.fileDescriptor).use { duplicate ->
                        socket.setFileDescriptorsForSend(arrayOf(duplicate.fileDescriptor))
                        socket.outputStream.write(0)
                        socket.outputStream.flush()
                        socket.setFileDescriptorsForSend(null)
                    }
                } finally {
                    connection.close()
                }
                Log.i(TAG, "passed $path to pid=${peer.pid}")
            } catch (e: Exception) {
                Log.w(TAG, "client failed", e)
                runCatching { replyError(socket, ERR_IO) }
            }
        }
    }

    private fun replyError(socket: LocalSocket, errno: Int) {
        socket.outputStream.write(errno)
        socket.outputStream.flush()
    }

    internal fun isUsbfsPath(path: String): Boolean =
        USBFS_PATH.matches(path)

    internal fun devices(context: Context): List<UsbDevice> =
        context.getSystemService(UsbManager::class.java).deviceList.values
            .sortedBy { it.deviceName }

    private val USBFS_PATH = Regex("^/dev/bus/usb/[0-9]{3}/[0-9]{3}$")

    // Linux errno。シムはこの 1 byte をそのまま errno にする。
    private const val ERR_PERMISSION = 13 // EACCES
    private const val ERR_NO_DEVICE = 19  // ENODEV
    private const val ERR_INVALID = 22    // EINVAL
    private const val ERR_IO = 5          // EIO
}
