package com.zerotoship.z2term.usb

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * USB Host API で取得した fd を SCM_RIGHTS で別プロセスへ渡した後も、usbfs ioctl が
 * 許可されるかを確かめる debug 専用スパイク。完成機能の UI/API には使わない。
 */
@SuppressLint("SetTextI18n")
class UsbHostProbeActivity : Activity() {
    private lateinit var usbManager: UsbManager
    private lateinit var output: TextView
    private var receiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = intent.usbDeviceExtra()
            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) || device == null) {
                show("USB permission denied")
                return
            }
            probe(device)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "USB host fd probe"
        output = TextView(this).apply {
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        setContentView(output)
        usbManager = getSystemService(UsbManager::class.java)
        registerPermissionReceiver()
        startProbe()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun registerPermissionReceiver() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun startProbe() {
        val transportControl = ParcelFileDescriptor.open(
            File("/dev/null"),
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { nativeProbeUsbFd(it.fd) }
        val devices = usbManager.deviceList.values.sortedBy { it.deviceName }
        if (devices.isEmpty()) {
            show(
                "SCM_RIGHTS control (/dev/null):\n$transportControl\n\n" +
                    "No USB host device found. Attach one and relaunch this debug activity."
            )
            return
        }
        val device = devices.first()
        show(
            "SCM_RIGHTS control (/dev/null):\n$transportControl\n\n" +
                "Found ${describe(device)}\nChecking permission…"
        )
        if (usbManager.hasPermission(device)) {
            probe(device)
            return
        }
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            // UsbManager が EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED を結果へ足すため mutable が必要。
            // 宛先は自パッケージに固定し、receiver も NOT_EXPORTED にして外部入力は受けない。
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun probe(device: UsbDevice) {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            show("${describe(device)}\nopenDevice returned null after permission was granted")
            return
        }
        try {
            val result = nativeProbeUsbFd(connection.fileDescriptor)
            show("${describe(device)}\nfd=${connection.fileDescriptor}\n$result")
            Log.i(TAG, "${describe(device)}: $result")
        } finally {
            connection.close()
        }
    }

    private fun show(message: String) {
        output.text = message
        Log.i(TAG, message.replace('\n', ' '))
    }

    private fun describe(device: UsbDevice): String =
        "${device.deviceName} vid=%04x pid=%04x class=%d".format(
            device.vendorId,
            device.productId,
            device.deviceClass
        )

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private external fun nativeProbeUsbFd(fd: Int): String

    companion object {
        private const val TAG = "UsbHostProbe"
        private const val ACTION_USB_PERMISSION =
            "com.zerotoship.z2term.debug2.USB_HOST_PROBE_PERMISSION"

        init {
            System.loadLibrary("z2term")
        }
    }
}
