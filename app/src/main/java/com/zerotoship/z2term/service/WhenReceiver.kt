package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * `z2-when` (A6) の時刻トリガーと、端末起動/アプリ更新後の復帰を受ける manifest レシーバ。
 *
 *  - 端末起動 / アプリ更新 → 時刻トリガーを貼り直す ([WhenManager.reload])。
 *  - [ACTION_TIME_FIRE] → 時刻トリガーの発火 ([AlarmManager] から届く**明示** Intent)。
 *
 * ⚠ **充電/電池トリガー (`charge:*` / `battery:*`) はここでは受けない。**
 * `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` は Android 8+ の暗黙
 * ブロードキャスト制限の**例外ではない**ため、manifest 宣言のレシーバには**永久に届かない**
 * (公式の broadcast-exceptions 一覧に電源・電池系は 1 つも無い)。0.8.205 で「例外だから常駐なしで
 * 受けられる」と誤って設計し、**0.8.213 まで `charge:*` が一度も発火しなかった** (2026-07-24 の
 * 実機検証で判明)。受け口は [SystemEventService] の動的レシーバへ移した (0.8.214)。
 * ここで動いていたのは明示 Intent の [ACTION_TIME_FIRE] だけで、時刻トリガーの e2e が通っていたため
 * 長く気付けなかった。**電源/電池の action をこの manifest filter に戻さないこと。**
 */
class WhenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
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
