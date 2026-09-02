package com.zerotoship.z2term.gui.rdp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [MS-RDPEA] の `rdpsnd` static virtual channel。**相手の音をこちらのスピーカーで鳴らす。**
 *
 * ⭐ **宣言するフォーマットが、相手の送ってくる形式を決める** (RemoteFX のときと同じ)。ここでは
 * 相手が挙げた中から **16bit PCM だけ**を選び、しかも **1 つだけ**返す。1 つに絞れば以後の
 * `wFormatNo` は 0 に決まり、途中で形式が変わることもない。⇒ 展開器を持たずに済む。
 * ⚠ 相手が PCM を 1 つも挙げなければ音は出ない (接続は壊さない)。ADPCM や AAC の展開が要るか
 * どうかは、**実際に何が挙がったかをログで見てから**決める。
 *
 * 音の実データは 2 つの形で届く:
 * - **WaveInfo + 続きの生データ** (RDP 5 以来の形)。⚠⚠ **続きには PDU ヘッダが無い。**
 *   WaveInfo の直後の 1 通だけは「先頭 4 バイトを捨てた残り全部が音」として読む。
 * - **Wave2** (RDP 8)。1 通に全部入っているので素直。
 *
 * どちらも**受け取ったら Wave Confirm を返す**。返さないと相手は次を送らず、数百 ms で音が止まる。
 */
internal class RdpSound(
    private val sendMessage: (ByteArray) -> Unit,
    /** 使う形式が決まったときに 1 度だけ。ここで再生器を用意する。 */
    private val onFormat: (sampleRate: Int, channels: Int, bitsPerSample: Int) -> Unit,
    /** 鳴らす PCM。受信スレッドから呼ばれる。 */
    private val onSamples: (ByteArray) -> Unit,
) {
    /** 相手が挙げた形式のうち、こちらが選んだもの。null なら音を鳴らさない。 */
    private var chosen: Format? = null

    /**
     * 直前が WaveInfo だったときの「続きを待っている」状態。
     *
     * ⚠⚠ **ここが rdpsnd で一番間違えやすいところ。** 続きのメッセージは SNDPROLOG を持たないので、
     * この状態を持っていないと**音のデータを PDU として読もうとして壊れる**。
     */
    private var pendingWave: PendingWave? = null

    private val reassembler = RdpChannelReassembler("RDPSND", MAX_MESSAGE_BYTES) { handle(it) }

    private data class Format(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val blockAlign: Int,
    )

    /** ⚠ data class にしない (中身の ByteArray は equals で比べる意味が無い)。 */
    private class PendingWave(val timeStamp: Int, val blockNo: Int, val head: ByteArray)

    @Synchronized
    fun acceptChannelChunk(payload: ByteArray) = reassembler.accept(payload)

    private fun handle(message: ByteArray) {
        // ⚠ WaveInfo の続きは**ヘッダを持たない**ので、PDU として読む前にこちらを先に見る。
        pendingWave?.let { pending ->
            pendingWave = null
            if (message.size <= WAVE_CONTINUATION_PAD) return
            val body = ByteArrayOutputStream(pending.head.size + message.size - WAVE_CONTINUATION_PAD)
            body.write(pending.head)
            body.write(message, WAVE_CONTINUATION_PAD, message.size - WAVE_CONTINUATION_PAD)
            play(body.toByteArray())
            send(waveConfirm(pending.timeStamp, pending.blockNo))
            return
        }
        if (message.size < PROLOG_SIZE) throw IOException("truncated RDPSND PDU")
        val msgType = message[0].toInt() and 0xFF
        val bodySize = le16(message, 2)
        if (bodySize > message.size - PROLOG_SIZE) throw IOException("invalid RDPSND body size: $bodySize")
        val body = message.copyOfRange(PROLOG_SIZE, PROLOG_SIZE + bodySize)
        when (msgType) {
            MSG_SERVER_FORMATS -> serverFormats(body)
            MSG_TRAINING -> training(body)
            MSG_WAVE -> waveInfo(body)
            MSG_WAVE2 -> wave2(body)
            MSG_CLOSE -> Unit // 相手が再生を止めた。こちらは書き込みが途切れるだけでよい。
            // ⛔ 知らない PDU で例外を投げない (長さで区切られているので読み飛ばせる)。
            else -> Unit
        }
    }

    /**
     * Server Audio Formats and Version PDU。相手が鳴らせる形式の一覧が来る。
     *
     * ⭐ **返した形式のリストが、以後 `wFormatNo` が指す表になる。** 1 つだけ返すので 0 番だけになる。
     */
    private fun serverFormats(body: ByteArray) {
        if (body.size < SERVER_FORMATS_HEAD) throw IOException("truncated RDPSND server formats")
        val count = le16(body, 14)
        var offset = SERVER_FORMATS_HEAD
        val candidates = mutableListOf<Format>()
        repeat(count) {
            if (offset + FORMAT_SIZE > body.size) return@repeat
            val tag = le16(body, offset)
            val channels = le16(body, offset + 2)
            val sampleRate = le32(body, offset + 4)
            val blockAlign = le16(body, offset + 12)
            val bits = le16(body, offset + 14)
            val extra = le16(body, offset + 16)
            if (tag == WAVE_FORMAT_PCM && bits == 16 && channels in 1..2 && sampleRate in 8000..192000) {
                candidates += Format(sampleRate, channels, bits, blockAlign)
            }
            offset += FORMAT_SIZE + extra
        }
        // 端末で鳴らしやすい順に選ぶ: ステレオ・高いサンプルレートを優先する。
        val pick = candidates.maxWithOrNull(compareBy({ it.channels }, { it.sampleRate }))
        Log.i(TAG, "RDPSND: server offered $count formats, ${candidates.size} usable, picked ${pick?.sampleRate}Hz ${pick?.channels}ch")
        chosen = pick
        send(clientFormats(pick))
        // Quality Mode は形式の返答の後に送る ([MS-RDPEA] 2.2.2.4 は RDP 6 以降で必須)。
        send(qualityMode())
        pick?.let { onFormat(it.sampleRate, it.channels, it.bitsPerSample) }
    }

    /** 相手が疎通を測るために送ってくる。⚠ 中身は返さず、同じ wTimeStamp / wPackSize だけ返す。 */
    private fun training(body: ByteArray) {
        if (body.size < 4) throw IOException("truncated RDPSND training")
        val timeStamp = le16(body, 0)
        val packSize = le16(body, 2)
        send(prolog(MSG_TRAINING, le16Bytes(timeStamp) + le16Bytes(packSize)))
    }

    /** WaveInfo。⚠ **本体は次の 1 通に続く**ので、ここでは先頭 4 バイトだけを預かる。 */
    private fun waveInfo(body: ByteArray) {
        if (body.size < WAVE_INFO_SIZE) throw IOException("truncated RDPSND WaveInfo")
        val timeStamp = le16(body, 0)
        val formatNo = le16(body, 2)
        val blockNo = body[4].toInt() and 0xFF
        val head = body.copyOfRange(8, WAVE_INFO_SIZE)
        if (chosen == null || formatNo != 0) {
            // 選んでいない形式で来た。⚠ 黙って捨てると相手は次を送らないので、確認だけは返す。
            send(waveConfirm(timeStamp, blockNo))
            return
        }
        pendingWave = PendingWave(timeStamp, blockNo, head)
    }

    /** Wave2 (RDP 8)。1 通に全部入っている。 */
    private fun wave2(body: ByteArray) {
        if (body.size < WAVE2_HEAD) throw IOException("truncated RDPSND Wave2")
        val timeStamp = le16(body, 0)
        val formatNo = le16(body, 2)
        val blockNo = body[4].toInt() and 0xFF
        if (chosen != null && formatNo == 0) play(body.copyOfRange(WAVE2_HEAD, body.size))
        send(waveConfirm(timeStamp, blockNo))
    }

    private fun play(samples: ByteArray) {
        if (samples.isEmpty()) return
        onSamples(samples)
    }

    private fun clientFormats(format: Format?): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(le32Bytes(0)) // dwFlags。TS_RDPSND_* の付加機能は使わない。
            write(le32Bytes(VOLUME_FULL))
            write(le32Bytes(0)) // dwPitch は使われない。
            write(le16Bytes(0)) // wDGramPort = 0: UDP は使わない (仮想チャネルだけで受ける)。
            write(le16Bytes(if (format == null) 0 else 1))
            write(byteArrayOf(0)) // cLastBlockConfirmed
            write(le16Bytes(CLIENT_VERSION))
            write(byteArrayOf(0)) // bPad
            if (format != null) write(waveFormatEx(format))
        }.toByteArray()
        return prolog(MSG_CLIENT_FORMATS, body)
    }

    private fun waveFormatEx(format: Format): ByteArray {
        val bytesPerFrame = format.channels * format.bitsPerSample / 8
        return ByteArrayOutputStream().apply {
            write(le16Bytes(WAVE_FORMAT_PCM))
            write(le16Bytes(format.channels))
            write(le32Bytes(format.sampleRate))
            write(le32Bytes(format.sampleRate * bytesPerFrame))
            write(le16Bytes(if (format.blockAlign > 0) format.blockAlign else bytesPerFrame))
            write(le16Bytes(format.bitsPerSample))
            write(le16Bytes(0)) // cbSize。PCM に追加情報は無い。
        }.toByteArray()
    }

    private fun qualityMode(): ByteArray =
        prolog(MSG_QUALITY_MODE, le16Bytes(QUALITY_HIGH) + le16Bytes(0))

    private fun waveConfirm(timeStamp: Int, blockNo: Int): ByteArray =
        prolog(MSG_WAVE_CONFIRM, le16Bytes(timeStamp) + byteArrayOf(blockNo.toByte(), 0))

    private fun prolog(msgType: Int, body: ByteArray): ByteArray =
        byteArrayOf(msgType.toByte(), 0) + le16Bytes(body.size) + body

    private fun send(message: ByteArray) = sendMessage(message)

    companion object {
        private const val TAG = "RdpSound"

        const val CHANNEL_NAME = "rdpsnd"

        private const val PROLOG_SIZE = 4
        private const val MSG_CLOSE = 0x01
        private const val MSG_WAVE = 0x02
        private const val MSG_WAVE_CONFIRM = 0x05
        private const val MSG_TRAINING = 0x06
        private const val MSG_SERVER_FORMATS = 0x07
        private const val MSG_CLIENT_FORMATS = 0x07
        private const val MSG_QUALITY_MODE = 0x0C
        private const val MSG_WAVE2 = 0x0D

        /** Server Audio Formats の固定部 (dwFlags 〜 bPad)。 */
        private const val SERVER_FORMATS_HEAD = 20
        private const val FORMAT_SIZE = 18
        private const val WAVE_FORMAT_PCM = 0x0001
        /** WaveInfo の固定部。末尾 4 バイトは音データの先頭。 */
        private const val WAVE_INFO_SIZE = 12
        /** Wave2 の固定部 (wTimeStamp 〜 dwAudioTimeStamp)。 */
        private const val WAVE2_HEAD = 12
        /** WaveInfo の続きの先頭にある捨てる 4 バイト。 */
        private const val WAVE_CONTINUATION_PAD = 4
        /** RDP 8.0。⭐ これを宣言すると Wave2 で届き、分割の組み立てが要らなくなる。 */
        private const val CLIENT_VERSION = 0x0008
        private const val QUALITY_HIGH = 0x0002
        /** dwVolume は左右 16bit ずつ。両方いっぱいにする。 */
        private const val VOLUME_FULL = -1
        private const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024

        private fun le16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun le32(data: ByteArray, offset: Int): Int =
            le16(data, offset) or (le16(data, offset + 2) shl 16)

        private fun le16Bytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())

        private fun le32Bytes(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
    }
}
