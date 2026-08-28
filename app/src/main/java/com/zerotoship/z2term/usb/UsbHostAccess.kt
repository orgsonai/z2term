package com.zerotoship.z2term.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/** `z2-usb` から使う、列挙と明示許可の小さな公開面。 */
object UsbHostAccess {
    data class Device(
        val index: Int,
        val path: String,
        val vendorId: Int,
        val productId: Int,
        val productName: String,
        val allowed: Boolean
    )

    sealed class AllowResult {
        data class Requested(val device: Device) : AllowResult()
        data class AlreadyAllowed(val device: Device) : AllowResult()
        object NoDevices : AllowResult()
        object NeedSelector : AllowResult()
        data class NotFound(val selector: String) : AllowResult()
    }

    fun devices(context: Context): List<Device> {
        val manager = context.getSystemService(UsbManager::class.java)
        return UsbFdBroker.devices(context).mapIndexed { index, usb -> usb.toInfo(index + 1, manager) }
    }

    fun allow(context: Context, selector: String): AllowResult {
        val manager = context.getSystemService(UsbManager::class.java)
        val raw = UsbFdBroker.devices(context)
        if (raw.isEmpty()) return AllowResult.NoDevices
        val selected = when {
            selector.isBlank() && raw.size == 1 -> raw.first()
            selector.isBlank() -> return AllowResult.NeedSelector
            selector.toIntOrNull()?.let { it in 1..raw.size } == true -> raw[selector.toInt() - 1]
            else -> raw.firstOrNull {
                it.deviceName == selector ||
                    "%04x:%04x".format(it.vendorId, it.productId).equals(selector, ignoreCase = true)
            } ?: return AllowResult.NotFound(selector)
        }
        val info = selected.toInfo(raw.indexOf(selected) + 1, manager)
        if (manager.hasPermission(selected)) return AllowResult.AlreadyAllowed(info)

        val reply = PendingIntent.getBroadcast(
            context,
            selected.deviceId,
            Intent(context, UsbPermissionReceiver::class.java)
                .setAction("${context.packageName}.USB_PERMISSION")
                .setPackage(context.packageName),
            // UsbManager が結果 extras を埋めるため mutable。宛先は自アプリの非公開receiverに固定。
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        manager.requestPermission(selected, reply)
        return AllowResult.Requested(info)
    }

    private fun UsbDevice.toInfo(index: Int, manager: UsbManager): Device = Device(
        index = index,
        path = deviceName,
        vendorId = vendorId,
        productId = productId,
        productName = runCatching { productName }.getOrNull().orEmpty(),
        allowed = manager.hasPermission(this)
    )
}
