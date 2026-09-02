package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * static virtual channel の Channel PDU Header ([MS-RDPBCGR] 2.2.6.1) を剥がし、
 * `CHANNEL_FLAG_FIRST` 〜 `CHANNEL_FLAG_LAST` に分かれて届く 1 通を組み立て直す。
 *
 * cliprdr と rdpsnd で同じ手順なのでここに置く。⚠ **DVC (drdynvc) の分割はこれとは別階層**で、
 * static の復元をした中身をさらに DVC 側で復元する (`RdpDynamicChannel`)。
 *
 * @param name 例外メッセージに出すチャネル名。どのチャネルで壊れたかが分からないと切り分けられない。
 * @param maxMessageBytes 1 通の上限。相手の言う長さを信じて確保しないための歯止め。
 */
internal class RdpChannelReassembler(
    private val name: String,
    private val maxMessageBytes: Int,
    private val onMessage: (ByteArray) -> Unit,
) {
    private var length = 0
    private var fragments = ByteArrayOutputStream()

    fun accept(payload: ByteArray) {
        if (payload.size < HEADER_SIZE) throw IOException("truncated $name channel header")
        val totalLength = le32(payload, 0)
        val flags = le32(payload, 4)
        if (totalLength !in 0..maxMessageBytes) throw IOException("invalid $name length: $totalLength")
        if (flags and CHANNEL_FLAG_FIRST != 0) {
            length = totalLength
            fragments = ByteArrayOutputStream(totalLength)
        }
        if (length == 0 && totalLength != 0) throw IOException("$name continuation without first chunk")
        fragments.write(payload, HEADER_SIZE, payload.size - HEADER_SIZE)
        if (fragments.size() > length) throw IOException("$name message exceeds declared length")
        if (flags and CHANNEL_FLAG_LAST == 0) return
        if (fragments.size() != length) throw IOException("incomplete $name message")
        val message = fragments.toByteArray()
        length = 0
        fragments.reset()
        // ⚠ 中身の無い通は捨てる。相手が区切りとして送ってくることがあり、投げると
        //    チャネルごと落ちる (未対応のものが来ても落とさない、と同じ考え方)。
        if (message.isNotEmpty()) onMessage(message)
    }

    /** チャネルを開き直すとき。途中まで届いていた通は捨てる。 */
    fun reset() {
        length = 0
        fragments.reset()
    }

    private companion object {
        const val HEADER_SIZE = 8
        const val CHANNEL_FLAG_FIRST = 0x00000001
        const val CHANNEL_FLAG_LAST = 0x00000002

        fun le32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
