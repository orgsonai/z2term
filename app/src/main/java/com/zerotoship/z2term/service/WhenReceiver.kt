package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * `z2-when` (A6) の充電/電池/時刻トリガーを受ける manifest レシーバ。
 *
 * `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` は Android 8+ の暗黙
 * ブロードキャスト制限の**例外**なので、**アプリを開いていなくても・専用の常駐サービス無しでも**
 * manifest 宣言のレシーバで受け取れる (§10-1 の「常駐を増やさない」設計の要)。
 *
 *  - 電源接続/切断 → `charge:start`/`charge:stop` を実行し、電池しきい値も評価。
 *  - 電池 低下/回復 → 電池しきい値を評価。
 *  - 端末起動 / アプリ更新 → 時刻トリガーを貼り直す ([WhenManager.reload])。
 *  - [ACTION_TIME_FIRE] → 時刻トリガーの発火 ([AlarmManager] から届く明示 Intent)。
 */
class WhenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                runAsync(this) { WhenManager.onCharge(app, started = true, level = batteryLevel(app)) }
            Intent.ACTION_POWER_DISCONNECTED ->
                runAsync(this) { WhenManager.onCharge(app, started = false, level = batteryLevel(app)) }
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY ->
                runAsync(this) { WhenManager.onBatteryChanged(app, batteryLevel(app)) }
            ACTION_TIME_FIRE -> {
                val id = intent.getStringExtra(EXTRA_RULE_ID) ?: return
                runAsync(this) { WhenManager.onTimeFire(app, id) }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                runAsync(this) { WhenManager.reload(app) }
        }
    }

    /** 現在の電池残量% (取れなければ -1)。スティッキーな `ACTION_BATTERY_CHANGED` から読む。 */
    private fun batteryLevel(context: Context): Int = runCatching {
        val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
    }.getOrDefault(-1)

    companion object {
        private const val TAG = "WhenReceiver"

        /** 時刻トリガーの発火 (AlarmManager → このレシーバへの明示 Intent)。 */
        const val ACTION_TIME_FIRE = "com.zerotoship.z2term.WHEN_TIME_FIRE"
        const val EXTRA_RULE_ID = "rule_id"

        /**
         * ルール実行やエンジン起動はブロードキャスト受信スレッドで完結しない (数秒かかる) ので、
         * [goAsync] で受信を延命しつつ別スレッドで処理する。10 秒以内に finish しないと ANR に
         * 準ずるため、重い実行部分 ([WhenManager.runRule]) 自体は内部で更にスレッドへ逃がしている。
         */
        private fun runAsync(receiver: BroadcastReceiver, block: () -> Unit) {
            val pending = receiver.goAsync()
            Thread {
                try {
                    block()
                } catch (e: Exception) {
                    Log.w(TAG, "when handling failed", e)
                } finally {
                    pending.finish()
                }
            }.apply { isDaemon = true; name = "when-receiver" }.start()
        }
    }
}
