package com.zerotoship.z2term.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/**
 * GUI 音声ブリッジ (オプトイン)。
 *
 * GUI セッション (proot 内) で PulseAudio を起動すると、null-sink (z2sink) の monitor を
 * `module-simple-protocol-tcp` が `127.0.0.1:<port>` へ生 PCM (s16le / 48000Hz / 2ch) で流す。
 * proot は Android とネットワーク名前空間を共有するので、ここから 127.0.0.1 へ TCP 接続して
 * PCM を読み、[AudioTrack] (streaming) で端末スピーカーへ再生する。
 *
 * - 録音権限は不要 (自前で鳴らすだけ)。
 * - PulseAudio (proot 側) の起動より先にこちらが繋ぎにいくことがあるので、接続拒否の間は
 *   リトライし続ける ([RETRY_DELAY_MS])。[stop] を呼ぶまで生き続ける。
 * - フォーマット (s16le/48k/2ch) は z2gui スクリプトの module 設定と一致させること。
 *
 * 使い方: GUI が CONNECTED になり、かつ設定「GUI 音声」が ON のときだけ
 * [com.zerotoship.z2term.gui.GuiSession] が `AudioBridge(6000 + display).start()` する。
 */
class AudioBridge(private val port: Int) {

    @Volatile private var running = false
    private var worker: Thread? = null
    @Volatile private var track: AudioTrack? = null

    fun start() {
        if (running) return
        running = true
        worker = Thread({ loop() }, "z2-audio-$port").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "AudioBridge started (port=$port)")
    }

    fun stop() {
        if (!running) return
        running = false
        worker?.interrupt()
        worker = null
        releaseTrack()
        Log.i(TAG, "AudioBridge stopped (port=$port)")
    }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        // 余裕を持たせて再生バッファ詰まりを避ける (目安: getMinBufferSize * 4)。
        val bufSize = (if (minBuf > 0) minBuf * 4 else SAMPLE_RATE * BYTES_PER_FRAME).coerceAtLeast(8192)
        Log.i(TAG, "loop start minBuf=$minBuf bufSize=$bufSize")
        while (running) {
            var socket: Socket? = null
            try {
                // 注意: Socket().apply { connect(InetSocketAddress("127.0.0.1", port)) } は不可。
                // apply 内では this=Socket となり `port` が Socket.getPort()(未接続=0) に解決され、
                // 127.0.0.1:0 へ繋ぎにいって ECONNREFUSED になる。フィールドの port を明示参照する。
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                s.tcpNoDelay = true
                socket = s
                val input = s.getInputStream()
                val t = buildTrack(bufSize)
                track = t
                t.play()
                Log.i(TAG, "connected 127.0.0.1:$port trackState=${t.state} playState=${t.playState}")
                val buf = ByteArray(bufSize)
                var total = 0L
                var logged = false
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) { Log.w(TAG, "EOF from PulseAudio (total=$total)"); break }
                    var off = 0
                    while (off < n && running) {
                        val w = t.write(buf, off, n - off)
                        if (w <= 0) { Log.w(TAG, "AudioTrack.write rc=$w"); break }
                        off += w
                    }
                    total += n
                    if (!logged && total > 0) { Log.i(TAG, "first audio written ($total bytes)"); logged = true }
                }
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                // 接続拒否 (PulseAudio 未起動) / リセット等 → running の間はリトライ。
                Log.w(TAG, "connect/read error on :$port — ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                runCatching { socket?.close() }
                releaseTrack()
            }
            if (running) {
                try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { break }
            }
        }
        Log.i(TAG, "loop end (running=$running)")
    }

    private fun buildTrack(bufSize: Int): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(ENCODING)
            .setChannelMask(CHANNEL_CONFIG)
            .build()
        return AudioTrack(
            attrs, format, bufSize,
            AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun releaseTrack() {
        val t = track ?: return
        track = null
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.release() }
    }

    companion object {
        private const val TAG = "AudioBridge"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** s16le 2ch = 1 フレーム 4 バイト。 */
        private const val BYTES_PER_FRAME = 4
        private const val CONNECT_TIMEOUT_MS = 1500
        private const val RETRY_DELAY_MS = 700L

        /** GUI 音声ブリッジの TCP ポート = 6000 + ディスプレイ番号 (RFB=5900+display と衝突しない)。 */
        fun portForDisplay(display: Int): Int = 6000 + display
    }
}
