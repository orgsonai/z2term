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
 * - 1 回押し: [DOUBLE_MS] 待ってから音量を 1 段変える (自分で [AudioManager.adjustStreamVolume] する)。
 * - [DOUBLE_MS] 以内に同じボタンの 2 回目: 1 回目の分は変えずに、読み上げを止める。
 *   ⚠ 1 回目ですぐ変えて後で戻すと、止めるたびに音量が一瞬動く (利用者の指摘、1.0.74 で待つ形に)。
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
    private var lastDirection = 0
    /** 2 回目を待っている 1 回目の音量の変更。 */
    private var pending: Runnable? = null

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
                        if (pending != null && direction == lastDirection && now - lastPress <= DOUBLE_MS) {
                            main.removeCallbacks(pending!!)
                            pending = null
                            Z2ApiBridge.stopSpeaking(appCtx)
                            return
                        }
                        // 待っている 1 回目があれば先に効かせる (向きが違う・間が空いた = 音量を変える押し方)。
                        pending?.let { main.removeCallbacks(it); it.run() }
                        lastPress = now
                        lastDirection = direction
                        val provider = this
                        pending = Runnable {
                            pending = null
                            audio.adjustStreamVolume(STREAM, direction, 0)
                            provider.currentVolume = audio.getStreamVolume(STREAM)
                        }.also { main.postDelayed(it, DOUBLE_MS) }
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
        // 待っていた 1 回目は効かせる (話し終わる直前に押した音量の変更を捨てない)。
        pending?.let { main.removeCallbacks(it); it.run() }
        pending = null
    }
}
