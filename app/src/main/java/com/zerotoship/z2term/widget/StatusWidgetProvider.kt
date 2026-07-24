package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.SshEndpoint
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面ウィジェット D1「状態＋ランチャー」。
 *
 * 上段に**いまの状態** (ssh 接続先 / 常駐サーバー数 / 有効な `z2-when` ルール数 / 電池)、
 * 下段に**マクロのボタン**を並べ、タップで `~/.z2term/macros/<name>.sh` を
 * **アプリを開かずバックグラウンド実行**する。
 *
 * **配線**: ボタン → [PendingIntent] (自分宛のブロードキャスト) → このレシーバ →
 * [HeadlessRun.launch] (= `z2-when` のルール実行と同じ経路) → 完了を待たずに再描画。
 * 新しい常駐サービスは足さない (電池要因を増やさない・引き継ぎ書 §10-2 の方針)。
 *
 * **更新のきっかけ**は 3 つ:
 *  1. OS の定期更新 (`updatePeriodMillis` = 30 分。これ以上は詰められない)
 *  2. ⟳ ボタンのタップ (その場で読み直す)
 *  3. アプリ側からの [refresh] (常駐サーバーの起動/停止・`z2-when` ルールの変更時)
 *
 * 読み取りはファイル I/O と設定 DataStore を含むので、描画は必ず [goAsync] + 別スレッドで行う。
 */
class StatusWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RUN_MACRO -> {
                val macro = intent.getStringExtra(EXTRA_MACRO).orEmpty()
                background(context) { ctx ->
                    if (macro.isNotBlank()) runMacro(ctx, macro)
                    renderAll(ctx)
                }
            }
            // 実行中のボタンをもう一度押したとき。停止は最大 1 秒ブロックするので必ず別スレッドで。
            ACTION_STOP_MACRO -> {
                val macro = intent.getStringExtra(EXTRA_MACRO).orEmpty()
                background(context) { ctx ->
                    if (macro.isNotBlank()) HeadlessRun.stop(runKey(macro))
                    renderAll(ctx)
                }
            }
            // 定期更新 (APPWIDGET_UPDATE) も自前で受けて非同期に描く。onUpdate は使わない。
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE ->
                background(context) { ctx -> renderAll(ctx) }
            // 削除・無効化などのライフサイクルは AppWidgetProvider の既定処理へ。
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore.clear(context, it) }
    }

    /**
     * ブロードキャスト受信中に別スレッドで [block] を回す。[goAsync] で「まだ処理中」と Android に
     * 伝えるので、レシーバから戻った直後にプロセスを畳まれて描画が飛ぶことがない。
     */
    private fun background(context: Context, block: (Context) -> Unit) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                block(app)
            } catch (e: Exception) {
                Log.w(TAG, "widget work failed", e)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true; name = "widget-status"; start() }
    }

    companion object {

        private const val TAG = "StatusWidget"

        /** ⟳ タップ / アプリ側からの再描画要求。 */
        private const val ACTION_REFRESH = "com.zerotoship.z2term.widget.REFRESH"

        /** マクロボタンのタップ。 */
        private const val ACTION_RUN_MACRO = "com.zerotoship.z2term.widget.RUN_MACRO"

        /** 実行中のマクロボタンのタップ (= 停止)。 */
        private const val ACTION_STOP_MACRO = "com.zerotoship.z2term.widget.STOP_MACRO"

        private const val EXTRA_MACRO = "macro"

        /** [HeadlessRun] に渡す実行キー。ウィジェット発の実行だと分かる形にしておく。 */
        private fun runKey(macro: String) = "widget-$macro"

        /** マクロ実行のまとめログ (`~/.z2term/widget/run.log`)。端末から `tail` して確かめられる。 */
        private const val LOG_REL = ".z2term/widget/run.log"

        /** レイアウトのボタン id (先頭から [WidgetStore.MAX_MACROS] 個使う)。 */
        private val BUTTON_IDS = intArrayOf(
            R.id.widget_btn_0, R.id.widget_btn_1, R.id.widget_btn_2, R.id.widget_btn_3
        )

        /**
         * 置かれているウィジェットを描き直す。常駐サーバーの起動/停止や `z2-when` ルールの
         * 変更など、**アプリ側で状態が変わったとき**に呼ぶ。1 つも置かれていなければ何もしない。
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (widgetIds(app).isEmpty()) return
            app.sendBroadcast(
                Intent(app, StatusWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }

        private fun widgetIds(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))

        /** 全ウィジェットを今の状態で描き直す (バックグラウンドスレッドから呼ぶこと)。 */
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val snapshot = Snapshot.read(context)
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, id, snapshot)) }
        }

        // --- 描画 ---

        private fun buildViews(context: Context, appWidgetId: Int, s: Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            // SimpleDateFormat はスレッド安全でないので共有せず、この描画の中だけで使う。
            val hhmm = SimpleDateFormat("HH:mm", Locale.US)

            // タイトルはアプリを開く。⟳ はその場で読み直す。⚙ は設定画面を直接開く
            // (ウィジェット長押しからしか設定へ行けないのは使いにくい、という実機フィードバック)。
            views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            views.setOnClickPendingIntent(
                R.id.widget_refresh,
                broadcast(context, appWidgetId, SLOT_REFRESH, ACTION_REFRESH, null)
            )
            views.setOnClickPendingIntent(R.id.widget_config, configIntent(context, appWidgetId))

            views.setTextViewText(R.id.widget_ssh, s.sshLine ?: context.getString(R.string.widget_ssh_none))
            // sshd が動いているときだけアクセント色にする (動いていない＝その宛先では入れない)。
            views.setTextColor(
                R.id.widget_ssh,
                ContextCompat.getColor(
                    context,
                    if (s.sshdRunning) R.color.widget_accent else R.color.widget_text_secondary
                )
            )
            views.setTextViewText(
                R.id.widget_stats,
                context.getString(R.string.widget_stats, s.serversRunning, s.rulesEnabled, s.batteryLevel)
            )

            val macros = WidgetStore.macros(context, appWidgetId)
            BUTTON_IDS.forEachIndexed { slot, viewId ->
                val name = macros.getOrNull(slot)
                if (name == null) {
                    views.setViewVisibility(viewId, View.GONE)
                } else {
                    // 実行中は「■ 名前」にして、同じボタンのタップで止められるようにする
                    // (RemoteViews に長押しは無いので、モードを増やさずトグルにするのが自然)。
                    // 2 行目は**そのマクロを最後に開始した時刻**。全体で 1 件しか出していなかった
                    // ときは、複数走らせるとどれがいつのものか分からなかった。
                    val busy = HeadlessRun.isRunning(runKey(name))
                    val startedAt = WidgetStore.runStartAt(context, name)
                    val time =
                        if (startedAt > 0) hhmm.format(Date(startedAt))
                        else context.getString(R.string.widget_btn_time_none)
                    val label = WidgetStore.label(name)
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(
                        viewId,
                        when {
                            busy -> context.getString(R.string.widget_btn_running, label, time)
                            // 一度でも走ったものは ✓ を付ける。すぐ終わるマクロで「■ が消えた」のが
                            // 停止ではなく正常終了だと分かるようにするため。
                            startedAt > 0 -> context.getString(R.string.widget_btn_done, label, time)
                            else -> context.getString(R.string.widget_btn_idle, label, time)
                        }
                    )
                    views.setTextColor(
                        viewId,
                        ContextCompat.getColor(
                            context,
                            if (busy) R.color.widget_accent else R.color.widget_text_primary
                        )
                    )
                    views.setOnClickPendingIntent(
                        viewId,
                        broadcast(
                            context, appWidgetId, slot,
                            if (busy) ACTION_STOP_MACRO else ACTION_RUN_MACRO, name
                        )
                    )
                }
            }

            // フッターは「直近に**終わった**マクロ」。開始時刻はボタン側に出るようになったので、
            // ここは終了を伝える役に回す (すぐ終わるマクロが「勝手に止まった」ように見えないため)。
            val footer = when {
                macros.isEmpty() -> context.getString(R.string.widget_no_macros)
                s.lastFinish != null -> context.getString(
                    R.string.widget_last_finish,
                    WidgetStore.label(s.lastFinish.first),
                    hhmm.format(Date(s.lastFinish.second))
                )
                else -> null
            }
            if (footer == null) {
                views.setViewVisibility(R.id.widget_footer, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                views.setTextViewText(R.id.widget_footer, footer)
            }
            return views
        }

        /**
         * ⚙ から [WidgetConfigActivity] を直接開く PendingIntent。
         *
         * ランチャーの「ウィジェット長押し → 設定」を辿らないと設定へ行けないのは使いにくい、
         * という実機フィードバックへの対応。`EXTRA_APPWIDGET_ID` を渡すので、どのウィジェットの
         * 設定かはランチャー経由のときと同じように決まる (渡さないと即 finish する作りになっている)。
         * requestCode はウィジェットごとに変える (使い回されると別のウィジェットの設定が開く)。
         */
        private fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData("z2term://widget/$appWidgetId/config".toUri())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return PendingIntent.getActivity(
                context, appWidgetId * SLOTS_PER_WIDGET + SLOT_CONFIG, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * ボタン 1 個ぶんの自分宛ブロードキャスト。requestCode と data の両方を
         * ウィジェット×スロットで一意にする (どちらかが同じだと PendingIntent が使い回され、
         * 別のボタンが同じマクロを走らせてしまう)。
         */
        private fun broadcast(
            context: Context,
            appWidgetId: Int,
            slot: Int,
            action: String,
            macro: String?,
        ): PendingIntent {
            val intent = Intent(context, StatusWidgetProvider::class.java)
                .setAction(action)
                .setData("z2term://widget/$appWidgetId/$slot".toUri())
            if (macro != null) intent.putExtra(EXTRA_MACRO, macro)
            return PendingIntent.getBroadcast(
                context, appWidgetId * SLOTS_PER_WIDGET + slot, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // --- マクロ実行 ---

        /**
         * `~/.z2term/macros/<name>` を headless 実行する。マクロ名は実在するファイルからしか
         * 来ないが、シェルへは単一引用符で渡して展開させない (`z2-when` と同じ安全境界)。
         */
        private fun runMacro(context: Context, name: String) {
            if (name !in WidgetStore.availableMacros(context)) {
                Log.w(TAG, "macro not found: $name")
                return
            }
            val q = HeadlessRun.shSingleQuote(name)
            val script = "export Z2_WIDGET_MACRO=$q; cd \"\$HOME\" 2>/dev/null; " +
                "sh \"\$HOME/.z2term/macros/\"$q"
            val app = context.applicationContext
            val ok = HeadlessRun.launch(
                context = context,
                script = script,
                logFile = File(File(context.filesDir, "shared_home"), LOG_REL),
                name = runKey(name),
                header = HeadlessRun.logHeader("widget $name"),
                // 終わったら「■」を「✓」へ戻す (onExit は drain スレッドから呼ばれる)。
                onExit = {
                    runCatching { WidgetStore.setRunFinish(app, name) }
                    runCatching { renderAll(app) }
                    // run.log を見ているライブ tail (D2) があれば、そちらも描き直す。
                    runCatching { TailWidgetProvider.refresh(app) }
                },
            )
            if (ok) WidgetStore.setRunStart(context, name)
        }

        /** ウィジェット 1 個あたりの PendingIntent スロット数 (ボタン + ⟳ + ⚙)。 */
        private const val SLOTS_PER_WIDGET = 8

        /** ⟳ が使うスロット番号 (マクロボタンと衝突しない値)。 */
        private const val SLOT_REFRESH = 7

        /** ⚙ が使うスロット番号。 */
        private const val SLOT_CONFIG = 6

    }

    /**
     * ウィジェットに出す状態のひとまとめ。全ウィジェットで 1 回だけ読んで使い回す
     * (置いてある数だけファイルを読み直さない)。
     */
    internal data class Snapshot(
        val sshLine: String?,
        val sshdRunning: Boolean,
        val serversRunning: Int,
        val rulesEnabled: Int,
        val batteryLevel: Int,
        val lastRun: Pair<String, Long>?,
        /** 直近に**終わった**マクロと時刻。フッターで「止めたのではなく終わった」ことを示す。 */
        val lastFinish: Pair<String, Long>?,
    ) {
        companion object {
            internal fun read(context: Context): Snapshot {
                val distroId = runCatching {
                    runBlocking { AppSettings(context).flow.first() }.distroId
                }.getOrNull().orEmpty()

                // status ファイルは supervisor が落ちても残るので、supervisor が生きているときだけ
                // 数える。常駐サーバーが動いていればフォアグラウンドサービスがこのプロセスを
                // 生かしているので、ここで見る isRunning は実態と一致する (プロセスごと死んでいれば
                // サーバーも死んでいる = 0 が正しい)。
                val running = if (ServerDaemonManager.isRunning) {
                    runCatching { ServerDaemonManager.readStatus(context) }
                        .getOrDefault(emptyList())
                        .filter { it.state == "running" }
                } else {
                    emptyList()
                }

                val batt = runCatching {
                    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }.getOrNull()
                val lvl = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

                return Snapshot(
                    sshLine = if (distroId.isEmpty()) null
                    else SshEndpoint.sshCommand(context, distroId),
                    // コマンド文字列に sshd が現れる常駐サーバーが走っているか (`sshd --lan` 等)。
                    sshdRunning = running.any { it.command?.contains("sshd") == true },
                    serversRunning = running.size,
                    rulesEnabled = runCatching { WhenManager.loadRules(context).count { it.enabled } }
                        .getOrDefault(0),
                    batteryLevel = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1,
                    lastRun = WidgetStore.lastRun(context),
                    lastFinish = WidgetStore.lastFinish(context),
                )
            }
        }
    }
}
