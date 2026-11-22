package com.zerotoship.z2term.usb

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.zerotoship.z2term.channel.SerialConfig

/**
 * 挿してある USB シリアル機器の列挙と、接続先が覚えた機器の選び出し。
 *
 * 見分けられるチップは usb-serial-for-android の既定の表に従う
 * (FTDI / CP210x / CH34x / PL2303 / CDC-ACM ほか)。
 */
object SerialDevices {

    /** 繋げる口 1 つ。口が複数ある変換チップは口ごとに 1 件。 */
    data class Candidate(val driver: UsbSerialDriver, val portIndex: Int) {
        val port: UsbSerialPort get() = driver.ports[portIndex]
        val vendorId: Int get() = driver.device.vendorId
        val productId: Int get() = driver.device.productId

        /** 一覧に出す名前 (`CP2102 USB to UART · 10c4:ea60`)。製品名を持たない機器は ID だけ。 */
        fun label(): String {
            val product = runCatching { driver.device.productName }.getOrNull()?.trim().orEmpty()
            val ids = "%04x:%04x".format(vendorId, productId)
            val port = if (driver.ports.size > 1) " #${portIndex + 1}" else ""
            return if (product.isEmpty()) "$ids$port" else "$product · $ids$port"
        }

        /** この口を覚えた形 (個体番号は許可が出てからでないと読めないので、ここでは入れない)。 */
        fun remember(config: SerialConfig): SerialConfig =
            config.copy(vendorId = vendorId, productId = productId, portIndex = portIndex, serialNumber = "")
    }

    sealed class Pick {
        data class Found(val candidate: Candidate) : Pick()
        /** 当たる機器が挿されていない。 */
        object None : Pick()
        /** 当たるものが 2 つ以上あって決められない。 */
        data class Ambiguous(val candidates: List<Candidate>) : Pick()
    }

    fun candidates(manager: UsbManager): List<Candidate> =
        runCatching { UsbSerialProber.getDefaultProber().findAllDrivers(manager) }
            .getOrDefault(emptyList())
            .flatMap { driver -> driver.ports.indices.map { Candidate(driver, it) } }

    /**
     * [config] に当たる口を選ぶ。
     *
     * - 機器を覚えていない: 挿してある口が 1 つならそれ。2 つ以上なら決めない (画面で選んでもらう)。
     * - 覚えている: VID / PID / 口の番号で絞る。同じものが複数あれば、許可済みの機器の個体番号で見分ける。
     */
    fun pick(manager: UsbManager, config: SerialConfig): Pick {
        val all = candidates(manager)
        if (!config.hasDevice) {
            return when (all.size) {
                0 -> Pick.None
                1 -> Pick.Found(all.first())
                else -> Pick.Ambiguous(all)
            }
        }
        val same = all.filter {
            it.vendorId == config.vendorId && it.productId == config.productId && it.portIndex == config.portIndex
        }
        if (same.size <= 1) return same.firstOrNull()?.let { Pick.Found(it) } ?: Pick.None
        if (config.serialNumber.isNotEmpty()) {
            same.firstOrNull {
                manager.hasPermission(it.driver.device) && serialOf(it) == config.serialNumber
            }?.let { return Pick.Found(it) }
        }
        return Pick.Ambiguous(same)
    }

    /** 個体番号。許可が無いと SecurityException になるので、読めなければ空。 */
    fun serialOf(candidate: Candidate): String =
        runCatching { candidate.driver.device.serialNumber }.getOrNull()?.trim().orEmpty()
}
