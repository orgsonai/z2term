package com.zerotoship.z2term.channel

import org.json.JSONObject

/** パリティ。並びは画面の選択肢の順。 */
enum class SerialParity(val letter: Char) {
    NONE('N'),
    ODD('O'),
    EVEN('E'),
    MARK('M'),
    SPACE('S'),
}

/** ストップビット。1.5 は 5 データビットのときだけ意味を持つ (チップが断れば接続時に知らせる)。 */
enum class SerialStopBits(val label: String) {
    ONE("1"),
    ONE_HALF("1.5"),
    TWO("2"),
}

/**
 * フロー制御。既定は [NONE]。
 *
 * ⚠ 対応はチップごとに違う (例: CH34x の RTS/CTS は 3.12.0 から)。対応していないものを
 * 選んだときは、繋ぐときに画面へ知らせて **フロー制御なしで続ける** — 黙って捨てると
 * 「設定したのに文字が欠ける」の原因が見えなくなる。
 */
enum class SerialFlow {
    NONE,
    RTS_CTS,
    DTR_DSR,
    XON_XOFF,
}

/**
 * 接続先の種類「シリアル」([ConnectionProtocol.SERIAL]) の設定。
 *
 * **機器の覚え方**: 最初に繋いだ機器の VID / PID と個体番号 (シリアル番号) を保存し、
 * 次からはそれに当たる機器を選ぶ。2 本以上挿していても選び直さずに済むようにするため。
 * [vendorId] と [productId] が 0 なら「まだ覚えていない」(挿してある 1 台を使い、覚える)。
 *
 * ⚠ 個体番号は USB の許可が出るまで読めない (Android 10 以降)。同じ VID / PID の機器が
 * 2 台あるときは、許可済みのものの個体番号で見分ける ([SerialDevices.pick])。
 */
data class SerialConfig(
    val baudRate: Int = DEFAULT_BAUD,
    val dataBits: Int = 8,
    val parity: SerialParity = SerialParity.NONE,
    val stopBits: SerialStopBits = SerialStopBits.ONE,
    val flow: SerialFlow = SerialFlow.NONE,
    val vendorId: Int = 0,
    val productId: Int = 0,
    /** 機器の個体番号。持たない機器 (安い変換チップに多い) は空。 */
    val serialNumber: String = "",
    /** 1 つの機器に口が複数ある変換チップ (FT2232H など) の何番目か。0 始まり。 */
    val portIndex: Int = 0,
) {
    val hasDevice: Boolean get() = vendorId != 0 || productId != 0

    /** 一覧に出す 1 行 (`115200 8N1` のような定番の書き方)。 */
    fun describe(): String = buildString {
        append(baudRate).append(' ').append(dataBits).append(parity.letter).append(stopBits.label)
        when (flow) {
            SerialFlow.NONE -> Unit
            SerialFlow.RTS_CTS -> append(" RTS/CTS")
            SerialFlow.DTR_DSR -> append(" DTR/DSR")
            SerialFlow.XON_XOFF -> append(" XON/XOFF")
        }
    }

    /** 覚えている機器を `1a86:7523` の形で。覚えていなければ空。 */
    fun deviceLabel(): String =
        if (!hasDevice) "" else "%04x:%04x".format(vendorId, productId) +
            (if (portIndex > 0) " #${portIndex + 1}" else "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("baudRate", baudRate)
        put("dataBits", dataBits)
        put("parity", parity.name)
        put("stopBits", stopBits.name)
        put("flow", flow.name)
        put("vendorId", vendorId)
        put("productId", productId)
        put("serialNumber", serialNumber)
        put("portIndex", portIndex)
    }

    companion object {
        const val DEFAULT_BAUD = 115_200

        /** 画面に並べる定番の速さ。これ以外も数字で入れられる。 */
        val COMMON_BAUDS = listOf(9_600, 19_200, 38_400, 57_600, 115_200, 230_400, 460_800, 921_600)

        val DATA_BITS = listOf(5, 6, 7, 8)

        /** 読めない値は既定へ落とす (壊れた 1 項目で接続先ごと読めなくしない)。 */
        fun fromJson(o: JSONObject?): SerialConfig {
            if (o == null) return SerialConfig()
            return SerialConfig(
                baudRate = o.optInt("baudRate", DEFAULT_BAUD).takeIf { it > 0 } ?: DEFAULT_BAUD,
                dataBits = o.optInt("dataBits", 8).takeIf { it in DATA_BITS } ?: 8,
                parity = runCatching { SerialParity.valueOf(o.optString("parity")) }.getOrDefault(SerialParity.NONE),
                stopBits = runCatching { SerialStopBits.valueOf(o.optString("stopBits")) }.getOrDefault(SerialStopBits.ONE),
                flow = runCatching { SerialFlow.valueOf(o.optString("flow")) }.getOrDefault(SerialFlow.NONE),
                vendorId = o.optInt("vendorId", 0),
                productId = o.optInt("productId", 0),
                serialNumber = o.optString("serialNumber"),
                portIndex = o.optInt("portIndex", 0).coerceAtLeast(0),
            )
        }
    }
}
