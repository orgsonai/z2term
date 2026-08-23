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
 *
 * 0.8.264 から z2-when の `boot` トリガーの受け口も兼ねる。`BOOT_COMPLETED` は**暗黙
 * ブロードキャスト制限の例外**なので manifest 宣言のここへ確実に届く＝検知 OFF でも動く。
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

        // 定期バックアップ (0.8.386) の予約も貼り直す。⚠ 貼り直さないと「設定は ON のまま
        // バックアップだけ増えない」状態が、次に機種変するまで気付かれない。
        runCatching { runBlocking { com.zerotoship.z2term.backup.AutoBackup.schedule(context) } }
            .onFailure { Log.w("BootReceiver", "auto backup reschedule failed", it) }

        // z2-screen keepon が掛かったまま再起動した場合の後始末。期限を過ぎていればその場で
        // 書き戻し、まだなら予約を貼り直す。放っておくと「消灯しない」が永久に残る。
        runCatching { ScreenTimeout.restoreOrReschedule(context) }
            .onFailure { Log.w("BootReceiver", "screen timeout restore failed", it) }

        // z2-when の `boot` トリガー (0.8.264)。ロック解除前 (LOCKED_BOOT_COMPLETED) は
        // 資格情報で暗号化された領域がまだ開いていない＝ルールファイルもエンジンも読めないので、
        // 通常の BOOT_COMPLETED まで待つ。ここで走らせても黙って失敗するだけになる。
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // events.jsonl へ `boot` を残しつつ `event:boot` も実行する (EventEmitter が両方やる)。
            // 検知の ON/OFF に依存しない側のイベントなので、そちらの経路に乗せるのが正しい。
            runCatching { EventEmitter.emit(context, "boot") }
                .onFailure { Log.w("BootReceiver", "boot event failed", it) }
            // ルール実行はエンジン (proot/z2root) の起動を伴うので受信スレッドを塞がない。
            // ⚠ ただの Thread では **onReceive を抜けた瞬間にプロセスごと止められうる**ので
            // goAsync() で「まだ処理中」と OS に伝える。下の常駐サービス起動と違い、
            // z2-when のルール実行はサービスを持たない一度きりの実行なので、これが唯一の生命線。
            val pending = goAsync()
            val app = context.applicationContext
            Thread({
                try {
                    WhenManager.onBoot(app)
                } catch (t: Throwable) {
                    Log.w("BootReceiver", "boot rule failed", t)
                } finally {
                    pending.finish()
                }
            }, "when-boot").start()
        }

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
