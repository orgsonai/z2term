package com.zerotoship.z2term.gui.rdp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * RDP から届いた PCM を端末のスピーカーで鳴らす。
 *
 * ⛔⛔ **受信スレッドで `AudioTrack.write` を呼ばない。** これはバッファが空くまで待つので、
 * 受信ループから直に呼ぶと**音が詰まった瞬間に画面と入力まで止まる**。⇒ 専用スレッドと
 * 有限のキューを挟み、**溢れたら古い音を捨てる**(音が飛ぶのは許せるが、画面が止まるのは許せない)。
 *
 * ⚠ `service/AudioBridge` と似ているが別物: あちらは proot の PulseAudio から TCP で受ける
 * 固定形式 (48kHz/2ch) の口で、こちらは**相手が選んだ形式**を [open] で受け取る。
 */
internal class RdpAudioSink {
    private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CHUNKS)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false
    private var worker: Thread? = null
    private var dropped = 0

    /** 形式が決まったら呼ぶ。2 度目以降は作り直す (相手が形式を変えたとき)。 */
    @Synchronized
    fun open(sampleRate: Int, channels: Int) {
        close()
        val mask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, mask, ENCODING)
        // 詰まりにくいよう余裕を持たせる (AudioBridge と同じ目安)。
        val bufSize = (if (minBuf > 0) minBuf * 4 else sampleRate * channels * 2).coerceAtLeast(8192)
        val created = runCatching { buildTrack(sampleRate, mask, bufSize) }.getOrElse {
            Log.w(TAG, "RDPSND: AudioTrack を用意できない", it)
            return
        }
        track = created
        running = true
        worker = Thread({ drain(created) }, "rdp-audio").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "RDPSND: playing ${sampleRate}Hz ${channels}ch (buffer=$bufSize)")
    }

    /** 受信スレッドから呼ばれる。⚠ ここでブロックしないこと。 */
    fun write(samples: ByteArray) {
        if (!running) return
        if (queue.offer(samples)) return
        // 溢れた。⭐ 古いものを 1 つ捨てて入れ直す (最新の音を優先する)。
        queue.poll()
        if (!queue.offer(samples)) return
        dropped++
        if (dropped == 1 || dropped % DROP_REPORT == 0) {
            Log.i(TAG, "RDPSND: audio buffer overrun (dropped=$dropped)")
        }
    }

    @Synchronized
    fun close() {
        running = false
        worker?.interrupt()
        worker = null
        queue.clear()
        val current = track ?: return
        track = null
        runCatching {
            current.pause()
            current.flush()
            current.stop()
        }
        runCatching { current.release() }
    }

    private fun drain(target: AudioTrack) {
        runCatching { target.play() }
        try {
            while (running) {
                val chunk = queue.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                var offset = 0
                while (offset < chunk.size && running) {
                    val written = target.write(chunk, offset, chunk.size - offset)
                    if (written <= 0) {
                        Log.w(TAG, "RDPSND: AudioTrack.write rc=$written")
                        return
                    }
                    offset += written
                }
            }
        } catch (_: InterruptedException) {
            // close() から。そのまま畳む。
        } catch (e: Exception) {
            Log.w(TAG, "RDPSND: playback stopped", e)
        }
    }

    private fun buildTrack(sampleRate: Int, channelMask: Int, bufSize: Int): AudioTrack =
        AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )

    private companion object {
        const val TAG = "RdpAudioSink"
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** 貯める塊の数。⚠ 増やすと遅れ、減らすと途切れる。 */
        const val QUEUE_CHUNKS = 24
        const val POLL_MS = 200L
        const val DROP_REPORT = 100
    }
}
