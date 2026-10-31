package com.zerotoship.z2term.service

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * `z2-say` で話している間だけ、**音量ボタンの 2 度押しで読み上げを止める** (1.0.67)。
 * 長い読み上げを途中で止めたいという利用者の要望から。
 *
 * 音量ボタンを受け取るのに、話している間だけ「再生中」の [MediaSession] を作り、音量の受け口を
 * 自前 ([VolumeProvider]) にする。Android は再生中のセッションへ音量ボタンを渡すので、画面が消えていても
 * 届き、ユーザー補助の権限も要らない。
 * - 1 回押し: 今までどおり音量を 1 段変える (自分で [AudioManager.adjustStreamVolume] する)。
 * - [DOUBLE_MS] 以内に同じボタンの 2 回目: 1 回目の前の音量に戻し、読み上げを止める。
 * ⚠ 話し終わったら必ずセッションを放す ([stop])。残すと音量ボタンをずっと取り続ける。
 * ⚠ ほかのアプリが音楽を流していると、そちらに音量ボタンを取られることがある (Android が決める)。
 */
internal object SpeechVolumeKeys {
    private const val TAG = "SpeechVolumeKeys"
    // 同じボタンを素早く 2 回 (利用者の選択)。⚠ 広げない: 音量を何段か続けて変える押し方も間は 0.4〜0.7 秒
    // (実機の記録) で、0.8 秒にしていたときはそれでも止まった。
    private const val DOUBLE_MS = 300L
    // TTS の既定の出力先。読み上げの音量 = この音量。
    private const val STREAM = AudioManager.STREAM_MUSIC

    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var lastPress = 0L
    private var pressed = false
    private var lastDirection = 0
    private var volumeBefore = 0

    fun start(context: Context) = main.post {
        if (session != null) return@post
        val appCtx = context.applicationContext
        val audio = appCtx.getSystemService(AudioManager::class.java) ?: return@post
        session = runCatching {
            MediaSession(appCtx, "z2term-say").apply {
                // ⚠ 受け口 (Callback) が無いと、音量ボタンは VolumeProvider まで届かずに捨てられる
                // (MediaSession は届いた操作を Callback の Handler に載せて配る)。音量の表示だけが
                // Android 側の見込みの値で動き、止まらなかった (1.0.71 まで)。中身は空でよい。
                setCallback(object : MediaSession.Callback() {}, main)
                setPlaybackToRemote(object : VolumeProvider(
                    VOLUME_CONTROL_RELATIVE, audio.getStreamMaxVolume(STREAM), audio.getStreamVolume(STREAM)
                ) {
                    override fun onAdjustVolume(direction: Int) {
                        val now = SystemClock.uptimeMillis()
                        Log.i(TAG, "volume key dir=$direction since=${if (lastPress == 0L) -1 else now - lastPress}ms")
                        // ⚠ 0 (変えない) は押下ではない。1 回押すと ±1 の直後 (実機で 17ms 後) に 0 が続けて届く。
                        // 1.0.71〜1.0.72 はこれも数えていて、1 回押しただけで 2 度押しになって止まった。
                        if (direction == 0) return
                        if (pressed && direction == lastDirection && now - lastPress <= DOUBLE_MS) {
                            audio.setStreamVolume(STREAM, volumeBefore, 0)
                            pressed = false
                            Z2ApiBridge.stopSpeaking(appCtx)
                            return
                        }
                        lastPress = now
                        pressed = true
                        lastDirection = direction
                        volumeBefore = audio.getStreamVolume(STREAM)
                        audio.adjustStreamVolume(STREAM, direction, 0)
                        currentVolume = audio.getStreamVolume(STREAM)
                    }
                })
                setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
                isActive = true
            }
        }.getOrNull()
    }

    fun stop() = main.post {
        session?.let { runCatching { it.isActive = false; it.release() } }
        session = null
        pressed = false
    }
}
