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
 * - [DOUBLE_MS] 以内の 2 回目: 1 回目の前の音量に戻し、読み上げを止める。
 * ⚠ 話し終わったら必ずセッションを放す ([stop])。残すと音量ボタンをずっと取り続ける。
 * ⚠ ほかのアプリが音楽を流していると、そちらに音量ボタンを取られることがある (Android が決める)。
 */
internal object SpeechVolumeKeys {
    // 指で 2 回押すと間は 0.6〜0.7 秒ほどになる (実機の記録で 0.67 秒)。0.4 秒では届かなかった。
    private const val TAG = "SpeechVolumeKeys"
    private const val DOUBLE_MS = 800L
    // TTS の既定の出力先。読み上げの音量 = この音量。
    private const val STREAM = AudioManager.STREAM_MUSIC

    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var lastPress = 0L
    private var pressed = false
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
                        // ⚠ 0 (変えない) も 1 回の押下として数える。音量の表示が出ていないときの 1 回目は、
                        // Android が「表示を出すだけ」にして 0 で渡してくる。捨てると 2 回押しても 1 回に見える。
                        val now = SystemClock.uptimeMillis()
                        Log.i(TAG, "volume key dir=$direction since=${if (lastPress == 0L) -1 else now - lastPress}ms")
                        if (pressed && now - lastPress <= DOUBLE_MS) {
                            audio.setStreamVolume(STREAM, volumeBefore, 0)
                            pressed = false
                            Z2ApiBridge.stopSpeaking(appCtx)
                            return
                        }
                        lastPress = now
                        pressed = true
                        volumeBefore = audio.getStreamVolume(STREAM)
                        if (direction != 0) {
                            audio.adjustStreamVolume(STREAM, direction, 0)
                            currentVolume = audio.getStreamVolume(STREAM)
                        }
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
