package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.ServerEntry
import kotlinx.coroutines.flow.first
import com.zerotoship.z2term.channel.SshProfileStore
import kotlinx.coroutines.runBlocking

/**
 * 端末起動 (BOOT_COMPLETED) を受けて、設定「起動時に自動で常駐」が ON かつ enabled な
 * サーバーがあれば [ServerDaemonService] を起動する。**アプリを一度も開かずにサーバーを常駐**
 * させるための入口。条件を満たさなければ何もしない。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        // AlarmManager の予約は再起動で消えるので、保存済みの時刻トリガーを貼り直す
        // (設定に依存しない。仕掛けた本人が消すまで生き続けるのが期待挙動)。
        runCatching { AlarmScheduler.rescheduleAll(context) }
            .onFailure { Log.w("BootReceiver", "alarm reschedule failed", it) }

        val settings = runBlocking { AppSettings(context).flow.first() }

        // システムイベント検知が ON なら、アプリを開かずに常駐 FG サービスを起動する。
        if (settings.systemEventCaptureEnabled) {
            Log.i("BootReceiver", "Autostarting system event service after boot")
            SystemEventService.start(context)
        }

        if (!settings.serversAutostartOnBoot) return
        val hasEnabled = ServerEntry.decode(settings.serverEntries).any { it.enabled && it.command.isNotBlank() }
        // 常駐トンネル (A2) も同じサービスにぶら下がるので、サーバーが 0 でもトンネルがあれば起こす。
        val hasTunnel = runCatching {
            runBlocking { SshProfileStore(context).profiles.first() }
                .any { it.residentTunnel && it.forwards.isNotEmpty() }
        }.getOrDefault(false)
        if (!hasEnabled && !hasTunnel) return

        Log.i("BootReceiver", "Autostarting server daemon after boot")
        ServerDaemonService.start(context)
    }
}
