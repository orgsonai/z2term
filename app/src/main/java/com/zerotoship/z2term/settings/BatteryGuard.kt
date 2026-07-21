package com.zerotoship.z2term.settings

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * バックグラウンドでアプリプロセスが kill される頻度を下げる L1 対策ヘルパー。
 *
 * フォアグラウンドサービス + WakeLock でも、Doze / OEM 省電力 / LMK はアプリを
 * kill しうる。電池最適化の除外リストに入れると kill 頻度が下がる。Play 非配布
 * アプリなので `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` の直接要求 (ワンタップ)
 * を使い、失敗時のみ最適化アプリ一覧へフォールバックする。
 *
 * Android 12/13 の phantom process killing (proot が生む多数の子プロセスを kill する)
 * はアプリ内からは無効化できないため、adb コマンドをコピー提示するだけに留める。
 */
object BatteryGuard {

    /** このアプリが電池最適化の除外対象 (= 最適化されない) なら true。 */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 電池最適化の除外を直接要求するシステムダイアログを出す (OFF→ON)。
     * 失敗時は最適化アプリ一覧画面へフォールバック (ユーザーが手動で除外)。
     * Activity コンテキストから呼ぶこと。
     */
    fun requestExemption(context: Context) {
        if (isIgnoring(context)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        if (runCatching { context.startActivity(direct) }.isSuccess) return
        openOptimizationSettings(context)
    }

    /**
     * 電池最適化の設定一覧を開く (ON→OFF 用)。
     *
     * Android は「自アプリを除外リストから外す (= 最適化を再び有効化)」直接 API を
     * 提供しないため、一覧画面へ誘導しユーザーに手動で切り替えてもらう。
     * Activity コンテキストから呼ぶこと。
     */
    fun openOptimizationSettings(context: Context) {
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (runCatching { context.startActivity(list) }.isSuccess) return
        // さらにフォールバック: アプリ詳細設定。
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        }
    }

    /**
     * Android 12/13 の phantom process monitor を無効化する adb コマンド。
     * アプリ内からは設定不可 (要 adb / 開発者向け)。コピー用に提示する。
     * 実行後は端末再起動が必要。Android 14+ では既定で緩和済みのことが多い。
     */
    const val PHANTOM_DISABLE_ADB: String =
        "adb shell settings put global settings_enable_monitor_phantom_procs false"
}
