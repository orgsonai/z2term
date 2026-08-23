package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 通信量の見張り ([NetGuard]) の受け口 (0.8.388)。15 分ごとに起きて、超えていれば
 * 外向きの SSH を切って 1 回だけ知らせる。
 *
 * 使用量の問い合わせと DataStore の読み書きがあるので `goAsync()` で別スレッドへ回す
 * (`onReceive` を抜けた瞬間に止められると、切るところまで行かずに終わる)。
 */
class NetGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NetGuard.ACTION_CHECK) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                NetGuard.enforce(app)
            } catch (t: Throwable) {
                Log.w("NetGuardReceiver", "check failed", t)
            } finally {
                pending.finish()
            }
        }, "net-guard").apply { isDaemon = true }.start()
    }
}
