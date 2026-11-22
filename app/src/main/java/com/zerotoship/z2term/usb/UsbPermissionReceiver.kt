package com.zerotoship.z2term.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/** `z2-usb allow` が出したAndroid USB許可画面の結果を受け取る。 */
class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.usbDeviceExtra()
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        Log.i(TAG, "permission ${if (granted) "granted" else "denied"}: ${device?.deviceName}")
        // シリアルのタブは「許可が出たら繋ぐ・断られたら知らせる」ので、結果を待っている。
        listeners.forEach { runCatching { it(device, granted) } }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "UsbPermission"

        /** 許可画面の結果を待つもの (シリアルのタブ)。待つ側が閉じるときに外す。 */
        val listeners = java.util.concurrent.CopyOnWriteArraySet<(UsbDevice?, Boolean) -> Unit>()
    }
}
