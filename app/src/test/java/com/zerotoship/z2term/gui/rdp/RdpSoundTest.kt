package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpSoundTest {
    private val sent = mutableListOf<ByteArray>()
    private val played = mutableListOf<ByteArray>()
    private var opened: Triple<Int, Int, Int>? = null

    private fun sound() = RdpSound(
        sendMessage = sent::add,
        onFormat = { rate, channels, bits -> opened = Triple(rate, channels, bits) },
        onSamples = played::add,
    )

    /**
     * ⭐ 相手が挙げた PCM のうち 1 つだけを返す。返した表が以後の wFormatNo になるので、
     * 1 つに絞れば 0 番だけになる。
     */
    @Test
    fun onlyOnePcmFormatIsAnnouncedBack() {
        val rdpsnd = sound()

        rdpsnd.acceptChannelChunk(chunk(serverFormats(
            format(tag = 0x0011, channels = 1, rate = 22050, bits = 4), // ADPCM: 受け取れない
            format(tag = 1, channels = 1, rate = 22050, bits = 16),
            format(tag = 1, channels = 2, rate = 48000, bits = 16),
        )))

        val reply = sent.first()
        assertEquals(0x07, reply[0].toInt()) // MSG_SNDC_FORMATS
        assertEquals(1, le16(reply, 4 + 14)) // wNumberOfFormats = 1
        assertEquals(0x0008, le16(reply, 4 + 17)) // wVersion = RDP 8.0 (Wave2 で受け取る)
        val fmt = 4 + 20
        assertEquals(1, le16(reply, fmt)) // WAVE_FORMAT_PCM
        assertEquals(2, le16(reply, fmt + 2)) // ステレオを選ぶ
        assertEquals(48000, le32(reply, fmt + 4)) // 高いほうを選ぶ
        assertEquals(192000, le32(reply, fmt + 8)) // nAvgBytesPerSec
        assertEquals(16, le16(reply, fmt + 14))
        assertEquals(Triple(48000, 2, 16), opened)

        // Quality Mode も続けて送る。
        assertEquals(0x0C, sent[1][0].toInt())
    }

    @Test
    fun withoutPcmNothingIsPlayed() {
        val rdpsnd = sound()

        rdpsnd.acceptChannelChunk(chunk(serverFormats(
            format(tag = 0x0011, channels = 2, rate = 44100, bits = 4),
        )))

        assertEquals(0, le16(sent.first(), 4 + 14)) // wNumberOfFormats = 0
        assertNull("鳴らす形式が無いので再生器を開かない", opened)

        rdpsnd.acceptChannelChunk(chunk(wave2(timeStamp = 5, blockNo = 3, samples = byteArrayOf(1, 2))))
        assertTrue("音は鳴らさない", played.isEmpty())
        // ⚠ それでも確認は返す (返さないと相手は次を送らない)。
        assertEquals(0x05, sent.last()[0].toInt())
    }

    @Test
    fun wave2IsPlayedAndConfirmed() {
        val rdpsnd = sound()
        rdpsnd.acceptChannelChunk(chunk(serverFormats(format(1, 2, 48000, 16))))
        sent.clear()

        rdpsnd.acceptChannelChunk(chunk(wave2(timeStamp = 0x1234, blockNo = 7, samples = byteArrayOf(9, 8, 7, 6))))

        assertArrayEquals(byteArrayOf(9, 8, 7, 6), played.single())
        val confirm = sent.single()
        assertEquals(0x05, confirm[0].toInt()) // MSG_SNDC_WAVECONFIRM
        assertEquals(0x1234, le16(confirm, 4))
        assertEquals(7, confirm[6].toInt())
    }

    /**
     * ⚠⚠ ここが rdpsnd で一番間違えやすいところ: WaveInfo の続きは **PDU ヘッダを持たない**。
     * 先頭 4 バイトを捨てた残りが音で、WaveInfo が預かった 4 バイトがその前に付く。
     */
    @Test
    fun waveInfoIsJoinedWithTheHeaderlessContinuation() {
        val rdpsnd = sound()
        rdpsnd.acceptChannelChunk(chunk(serverFormats(format(1, 2, 48000, 16))))
        sent.clear()

        rdpsnd.acceptChannelChunk(chunk(waveInfo(timeStamp = 0x2222, blockNo = 4, head = byteArrayOf(1, 2, 3, 4))))
        assertTrue("本体が来るまでは鳴らさない", played.isEmpty())
        assertTrue("確認もまだ返さない", sent.isEmpty())

        rdpsnd.acceptChannelChunk(chunk(byteArrayOf(0, 0, 0, 0) + byteArrayOf(5, 6, 7, 8)))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), played.single())
        assertEquals(0x2222, le16(sent.single(), 4))
        assertEquals(4, sent.single()[6].toInt())
    }

    /** 続きを受け取った後は、次のメッセージを普通の PDU として読む。 */
    @Test
    fun normalPdusResumeAfterTheContinuation() {
        val rdpsnd = sound()
        rdpsnd.acceptChannelChunk(chunk(serverFormats(format(1, 2, 48000, 16))))
        rdpsnd.acceptChannelChunk(chunk(waveInfo(0x10, 1, byteArrayOf(1, 1, 1, 1))))
        rdpsnd.acceptChannelChunk(chunk(byteArrayOf(0, 0, 0, 0, 2, 2)))
        sent.clear()

        rdpsnd.acceptChannelChunk(chunk(prolog(0x06, le16Bytes(0x77) + le16Bytes(0x40))))

        val confirm = sent.single()
        assertEquals(0x06, confirm[0].toInt()) // Training を返す
        assertEquals(0x77, le16(confirm, 4))
        assertEquals(0x40, le16(confirm, 6))
    }

    @Test
    fun unknownPduIsSkipped() {
        val rdpsnd = sound()
        rdpsnd.acceptChannelChunk(chunk(prolog(0x7F, byteArrayOf(1, 2, 3))))
        assertTrue(sent.isEmpty())
    }

    /**
     * ⚠ 1 回のチャネル書き込みに PDU が 2 つ入っていることがある (CLIPRDR で実測)。
     * 先頭だけ読んで残りを捨てると、Training を取りこぼして相手が音を送り始めない。
     */
    @Test
    fun formatsAndTrainingInOneChunkAreBothAnswered() {
        val rdpsnd = sound()

        rdpsnd.acceptChannelChunk(
            chunk(
                serverFormats(format(tag = 1, channels = 2, rate = 48000, bits = 16)) +
                    prolog(0x06, le16Bytes(0x1234) + le16Bytes(1024)),
            ),
        )

        // 形式の返答 + Quality Mode + Training の返答。
        assertEquals(0x07, sent[0][0].toInt())
        assertEquals(0x0C, sent[1][0].toInt()) // Quality Mode
        val training = sent[2]
        assertEquals(0x06, training[0].toInt())
        assertEquals(0x1234, le16(training, 4)) // 同じ wTimeStamp を返す
        assertEquals(Triple(48000, 2, 16), opened)
    }

    /** ⛔ WaveInfo の続きは PDU ヘッダを持たない。同じ通の残りを PDU として読まない。 */
    @Test
    fun theBytesAfterWaveInfoAreNotReadAsAPdu() {
        val rdpsnd = sound()
        rdpsnd.acceptChannelChunk(chunk(serverFormats(format(tag = 1, channels = 2, rate = 48000, bits = 16))))
        sent.clear()

        // WaveInfo (本体の先頭 4 バイト入り) の後ろに、PDU に見えるバイトが続いていても読まない。
        val waveInfo = prolog(0x02, le16Bytes(7) + le16Bytes(0) + byteArrayOf(3, 0, 0, 0) + byteArrayOf(1, 2, 3, 4))
        rdpsnd.acceptChannelChunk(chunk(waveInfo + byteArrayOf(0x07, 0, 0, 0)))

        assertTrue("WaveInfo だけで確認は返さない", sent.isEmpty())
        assertTrue("音もまだ鳴らさない", played.isEmpty())
    }

    private fun serverFormats(vararg formats: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(le32Bytes(0)) // dwFlags
            write(le32Bytes(0)) // dwVolume
            write(le32Bytes(0)) // dwPitch
            write(le16Bytes(0)) // wDGramPort
            write(le16Bytes(formats.size))
            write(byteArrayOf(0)) // cLastBlockConfirmed
            write(le16Bytes(6)) // wVersion
            write(byteArrayOf(0)) // bPad
            formats.forEach { write(it) }
        }.toByteArray()
        return prolog(0x07, body)
    }

    private fun format(tag: Int, channels: Int, rate: Int, bits: Int): ByteArray {
        val blockAlign = channels * bits / 8
        return ByteArrayOutputStream().apply {
            write(le16Bytes(tag))
            write(le16Bytes(channels))
            write(le32Bytes(rate))
            write(le32Bytes(rate * blockAlign))
            write(le16Bytes(blockAlign))
            write(le16Bytes(bits))
            write(le16Bytes(0)) // cbSize
        }.toByteArray()
    }

    private fun wave2(timeStamp: Int, blockNo: Int, samples: ByteArray): ByteArray =
        prolog(0x0D, ByteArrayOutputStream().apply {
            write(le16Bytes(timeStamp))
            write(le16Bytes(0)) // wFormatNo
            write(byteArrayOf(blockNo.toByte(), 0, 0, 0))
            write(le32Bytes(0)) // dwAudioTimeStamp
            write(samples)
        }.toByteArray())

    private fun waveInfo(timeStamp: Int, blockNo: Int, head: ByteArray): ByteArray =
        prolog(0x02, ByteArrayOutputStream().apply {
            write(le16Bytes(timeStamp))
            write(le16Bytes(0)) // wFormatNo
            write(byteArrayOf(blockNo.toByte(), 0, 0, 0))
            write(head)
        }.toByteArray())

    private fun prolog(msgType: Int, body: ByteArray): ByteArray =
        byteArrayOf(msgType.toByte(), 0) + le16Bytes(body.size) + body

    private fun chunk(body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(le32Bytes(body.size))
        write(le32Bytes(3)) // FIRST | LAST
        write(body)
    }.toByteArray()

    private fun le16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(data: ByteArray, offset: Int): Int =
        le16(data, offset) or (le16(data, offset + 2) shl 16)

    private fun le16Bytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())

    private fun le32Bytes(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
}
