package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * `z2-screen keepon <時間>` の**期限**の受け口。発火したら自動画面消灯を元の値へ書き戻す
 * ([ScreenTimeout.onExpired])。
 *
 * [AlarmScheduler] と受け口を分けているのは、こちらが「掛けた設定を必ず戻す」後始末で、
 * 取りこぼすと電池が減り続けるため。z2-alarm のイベント配信と経路を混ぜない。
 *
 * 設定の書き換えは短いが、Doze からの起床直後は I/O が遅れることがあるので `goAsync()` で
 * 完了を宣言してから走らせる ([AlarmReceiver] と同じ理由)。
 */
class ScreenTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXPIRE) return
        val appCtx = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                ScreenTimeout.onExpired(appCtx)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        const val ACTION_EXPIRE = "com.zerotoship.z2term.SCREEN_TIMEOUT_EXPIRE"
    }
}
