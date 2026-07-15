package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.ServerEntry
import kotlinx.coroutines.flow.first
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

        val settings = runBlocking { AppSettings(context).flow.first() }
        if (!settings.serversAutostartOnBoot) return
        val hasEnabled = ServerEntry.decode(settings.serverEntries).any { it.enabled && it.command.isNotBlank() }
        if (!hasEnabled) return

        Log.i("BootReceiver", "Autostarting server daemon after boot")
        ServerDaemonService.start(context)
    }
}
