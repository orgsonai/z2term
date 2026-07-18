package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * [AlarmScheduler] が仕掛けた時刻アラームの受け口。発火したら events.jsonl へ
 * `alarm` イベントを書き、`daily` なら翌日へ再セットする ([AlarmScheduler.onFired])。
 *
 * 処理はファイル 1 行の追記だけで短いが、Doze 中の起床直後で I/O が遅れることがあるため
 * `goAsync()` で完了を宣言してから走らせる (onReceive を抜けた瞬間にプロセスを殺されないように)。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        val appCtx = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                AlarmScheduler.onFired(appCtx, id)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        const val ACTION_FIRE = "com.zerotoship.z2term.ALARM_FIRE"
        const val EXTRA_ID = "id"
    }
}
