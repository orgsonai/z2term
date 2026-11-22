package com.zerotoship.z2term.channel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.zerotoship.z2term.usb.SerialDevices
import com.zerotoship.z2term.usb.UsbHostAccess
import com.zerotoship.z2term.usb.UsbPermissionReceiver
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * USB シリアルのタブ ([ConnectionProtocol.SERIAL])。usb-serial-for-android で口を開く。
 *
 * **タブの寿命とケーブルの寿命を分けている**のが要点。ケーブルを抜いてもチャネルは生きたまま
 * (画面の文字も残る) で [Event.Lost] を知らせ、同じ機器が挿し直されたら Android の許可画面を
 * 出して、許可が出たらつなぎ直す。Android は抜いた時点で USB の許可を取り消すので、
 * 挿し直すたびに許可画面が出るのは仕様 (利用者と決めた動き)。
 *
 * 状態の変化はすべて 1 本の専用スレッド [ctl] で順に処理する — 抜き差しの知らせ・許可の結果・
 * 読み取りの失敗は別々のスレッドから来るので、同時に触ると同じ口を 2 回開け閉めする。
 */
class SerialChannel(
    context: Context,
    initial: SerialConfig,
    /** 状態の変化。タブの画面に 1 行出すために使う ([ctl] スレッドから呼ばれる)。 */
    private val onEvent: (Event) -> Unit,
    /** 機器を覚えた・個体番号が読めたとき。接続先へ保存する。 */
    private val onLearned: (SerialConfig) -> Unit,
) : SerialLink {

    sealed class Event {
        /** 当たる機器が挿されていない。挿されるのを待つ。 */
        object Waiting : Event()
        /** 当たる機器が 2 つ以上あり、どれか決められない。 */
        data class Ambiguous(val count: Int) : Event()
        object PermissionAsked : Event()
        object PermissionDenied : Event()
        data class Connected(val device: String, val settings: String) : Event()
        /** 選んだフロー制御にチップが対応していない。フロー制御なしで続けている。 */
        data class FlowUnsupported(val flow: SerialFlow) : Event()
        object Lost : Event()
        data class OpenFailed(val message: String) : Event()
    }

    private val app = context.applicationContext
    private val manager = app.getSystemService(UsbManager::class.java)
    private val ctl = Executors.newSingleThreadExecutor { r -> Thread(r, "serial-ctl").apply { isDaemon = true } }

    @Volatile private var config = initial
    @Volatile private var closed = false

    /** 受け取った分の控え。AI の `serial_send` / `serial_read` が読む (1.0.208)。 */
    override val transcript = SerialTranscript()

    /** 口が開いているか。AI へ「つながっていない」を返すために、ほかのスレッドからも読む。 */
    override val connected: Boolean get() = port != null

    /** つながっている機器の名前 (つながっていなければ null)。 */
    @Volatile override var deviceLabel: String? = null
        private set

    // ↓ 書くのは ctl スレッドだけ (port は connected のために @Volatile)
    @Volatile private var port: UsbSerialPort? = null
    private var device: UsbDevice? = null
    private var io: SerialInputOutputManager? = null
    /** 許可画面を出して答えを待っている機器 (同じ機器へ 2 回出さない)。 */
    private var asking: UsbDevice? = null
    /** 同じ知らせを続けて出さない (挿されていない間に Waiting を何度も出さない)。 */
    private var lastEvent: Event? = null

    private val incoming = LinkedBlockingQueue<ByteArray>()

    override val reader: InputStream = object : InputStream() {
        private var chunk: ByteArray = ByteArray(0)
        private var pos = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= chunk.size) {
                val next = incoming.take()
                if (next === EOF) {
                    incoming.offer(EOF) // 後から読んでも終わりが分かるように戻しておく
                    return -1
                }
                chunk = next
                pos = 0
            }
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n)
            pos += n
            return n
        }
    }

    override val writer: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            // ⚠ 抜けている間に打った分は捨てる。溜めて挿し直したときに流すと、相手の
            //   ログインや設定の途中へ古い入力が混ざる。
            val p = port ?: return
            try {
                p.write(b.copyOfRange(off, off + len), WRITE_TIMEOUT_MS)
            } catch (e: IOException) {
                Log.w(TAG, "write failed: ${e.message}")
            }
        }
    }

    override val isAlive: Boolean get() = !closed
    override val exitCode: Int? get() = if (closed) 0 else null

    /** シリアルには画面の大きさを伝える仕組みが無い (相手側で `stty` 等を使う)。 */
    override fun resize(rows: Int, cols: Int) = Unit

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dev = intent.usbDevice() ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> post {
                    if (device?.deviceName == dev.deviceName) lose()
                    if (asking?.deviceName == dev.deviceName) asking = null
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> post { if (port == null) tryOpen() }
            }
        }
    }

    private val permissionListener: (UsbDevice?, Boolean) -> Unit = { dev, granted ->
        post {
            val waited = asking
            if (waited != null && (dev == null || dev.deviceName == waited.deviceName)) {
                asking = null
                if (granted) tryOpen() else emit(Event.PermissionDenied)
            }
        }
    }

    /** 監視を始め、最初の接続を試みる。 */
    fun start() {
        UsbPermissionReceiver.listeners.add(permissionListener)
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(usbReceiver, filter)
        }
        post { tryOpen() }
    }

    override fun close() {
        if (closed) return
        closed = true
        UsbPermissionReceiver.listeners.remove(permissionListener)
        runCatching { app.unregisterReceiver(usbReceiver) }
        runCatching { ctl.execute { closePort() } }
        ctl.shutdown()
        // 画面のスレッドから呼ばれることがあるので、口が閉じるのを長くは待たない。
        runCatching { ctl.awaitTermination(300, TimeUnit.MILLISECONDS) }
        incoming.offer(EOF)
    }

    private fun post(block: () -> Unit) {
        if (closed) return
        runCatching { ctl.execute { if (!closed) block() } }
    }

    private fun emit(event: Event) {
        if (event == lastEvent) return
        lastEvent = event
        runCatching { onEvent(event) }
    }

    private fun tryOpen() {
        if (port != null || asking != null) return
        val candidate = when (val pick = SerialDevices.pick(manager, config)) {
            SerialDevices.Pick.None -> return emit(Event.Waiting)
            is SerialDevices.Pick.Ambiguous -> return emit(Event.Ambiguous(pick.candidates.size))
            is SerialDevices.Pick.Found -> pick.candidate
        }
        val dev = candidate.driver.device
        if (!manager.hasPermission(dev)) {
            asking = dev
            emit(Event.PermissionAsked)
            UsbHostAccess.requestPermission(app, dev)
            return
        }
        val connection = manager.openDevice(dev)
            ?: return emit(Event.OpenFailed("openDevice returned null"))
        val p = candidate.port
        try {
            p.open(connection)
            p.setParameters(config.baudRate, config.dataBits, stopBitsOf(config.stopBits), parityOf(config.parity))
            val flowOk = applyFlow(p, config.flow)
            // 多くのマイコンボード (CDC-ACM) は DTR が立つまで送ってこない。
            runCatching { p.dtr = true }
            runCatching { p.rts = true }
            port = p
            device = dev
            io = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    if (data.isNotEmpty()) {
                        transcript.append(data)
                        incoming.offer(data)
                    }
                }

                override fun onRunError(e: Exception) {
                    Log.w(TAG, "io stopped: ${e.message}")
                    post { if (port === p) lose() }
                }
            }).also { it.start() }
            deviceLabel = candidate.label()
            emit(Event.Connected(candidate.label(), config.describe()))
            if (!flowOk) emit(Event.FlowUnsupported(config.flow))
            learn(candidate)
        } catch (e: Exception) {
            Log.w(TAG, "open failed", e)
            runCatching { p.close() }
            runCatching { connection.close() }
            port = null
            device = null
            emit(Event.OpenFailed(e.message ?: e.javaClass.simpleName))
        }
    }

    /** 初めて繋いだ機器・まだ個体番号を知らない機器は、その場で覚える。 */
    private fun learn(candidate: SerialDevices.Candidate) {
        val serial = SerialDevices.serialOf(candidate)
        val learned = if (!config.hasDevice) candidate.remember(config).copy(serialNumber = serial)
        else if (config.serialNumber.isEmpty() && serial.isNotEmpty()) config.copy(serialNumber = serial)
        else return
        config = learned
        runCatching { onLearned(learned) }
    }

    private fun lose() {
        if (port == null) return
        closePort()
        emit(Event.Lost)
    }

    private fun closePort() {
        runCatching { io?.stop() }
        io = null
        runCatching { port?.close() }
        port = null
        device = null
        deviceLabel = null
    }

    private fun applyFlow(p: UsbSerialPort, flow: SerialFlow): Boolean {
        val wanted = when (flow) {
            SerialFlow.NONE -> UsbSerialPort.FlowControl.NONE
            SerialFlow.RTS_CTS -> UsbSerialPort.FlowControl.RTS_CTS
            SerialFlow.DTR_DSR -> UsbSerialPort.FlowControl.DTR_DSR
            SerialFlow.XON_XOFF -> UsbSerialPort.FlowControl.XON_XOFF
        }
        if (wanted == UsbSerialPort.FlowControl.NONE) return true
        if (wanted !in p.supportedFlowControl) return false
        return runCatching { p.flowControl = wanted }.isSuccess
    }

    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "SerialChannel"
        private const val WRITE_TIMEOUT_MS = 2_000
        private val EOF = ByteArray(0)

        private fun parityOf(p: SerialParity): Int = when (p) {
            SerialParity.NONE -> UsbSerialPort.PARITY_NONE
            SerialParity.ODD -> UsbSerialPort.PARITY_ODD
            SerialParity.EVEN -> UsbSerialPort.PARITY_EVEN
            SerialParity.MARK -> UsbSerialPort.PARITY_MARK
            SerialParity.SPACE -> UsbSerialPort.PARITY_SPACE
        }

        private fun stopBitsOf(s: SerialStopBits): Int = when (s) {
            SerialStopBits.ONE -> UsbSerialPort.STOPBITS_1
            SerialStopBits.ONE_HALF -> UsbSerialPort.STOPBITS_1_5
            SerialStopBits.TWO -> UsbSerialPort.STOPBITS_2
        }
    }
}
