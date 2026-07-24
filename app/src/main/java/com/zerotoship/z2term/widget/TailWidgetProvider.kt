package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面ウィジェット D2「ライブ tail」(§10-2)。
 *
 * **何ができるか**: 選んだファイルの**末尾 N 行**をホーム画面に出す。「ホーム画面で `tail`」。
 * マクロや `z2-when` が書いたログ・`events.jsonl`・セッションログなど、端末を開かずに
 * 「いま何が起きたか」を見るための窓。
 *
 * **常駐は増やさない** (D1 と同じ方針。§10-1)。更新のきっかけは
 *  1. OS の定期更新 (`updatePeriodMillis` = 30 分。OS 側の下限)
 *  2. ⟳ タップ (その場で読み直す)
 *  3. アプリ側からの [refresh] — マクロやルールの実行が終わったとき ([refreshAll])
 *
 * 読み取りはファイル I/O なので、描画は必ず [goAsync] + 別スレッドで行う。
 */
class TailWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE ->
                background(context) { ctx -> renderAll(ctx) }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { TailStore.clear(context, it) }
    }

    private fun background(context: Context, block: (Context) -> Unit) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                block(app)
            } catch (e: Exception) {
                Log.w(TAG, "tail widget work failed", e)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true; name = "widget-tail"; start() }
    }

    companion object {

        private const val TAG = "TailWidget"

        private const val ACTION_REFRESH = "com.zerotoship.z2term.widget.TAIL_REFRESH"

        /** ウィジェット 1 個あたりの PendingIntent スロット数 (⟳ と ⚙)。 */
        private const val SLOTS_PER_WIDGET = 4
        private const val SLOT_REFRESH = 1
        private const val SLOT_CONFIG = 2

        /** 置かれているライブ tail を描き直す。1 つも置かれていなければ何もしない。 */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (widgetIds(app).isEmpty()) return
            app.sendBroadcast(
                Intent(app, TailWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }

        private fun widgetIds(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TailWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))

        /** 全ウィジェットを今の中身で描き直す (バックグラウンドスレッドから呼ぶこと)。 */
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tail)
            val hhmm = SimpleDateFormat("HH:mm:ss", Locale.US)

            views.setOnClickPendingIntent(
                R.id.tail_refresh,
                PendingIntent.getBroadcast(
                    context, appWidgetId * SLOTS_PER_WIDGET + SLOT_REFRESH,
                    Intent(context, TailWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                        .setData("z2term://tail/$appWidgetId/refresh".toUri()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            views.setOnClickPendingIntent(R.id.tail_config, configIntent(context, appWidgetId))
            // 本文をタップしたらアプリを開く (端末で続きを見たくなるのが自然な流れ)。
            views.setOnClickPendingIntent(R.id.tail_body, openAppIntent(context))

            val rel = TailStore.path(context, appWidgetId)
            val file = TailStore.file(context, appWidgetId)
            val lines = TailStore.lines(context, appWidgetId)

            when {
                rel == null -> {
                    views.setTextViewText(R.id.tail_title, context.getString(R.string.tail_title_none))
                    views.setTextViewText(R.id.tail_body, context.getString(R.string.tail_pick_file))
                    views.setTextViewText(R.id.tail_footer, "")
                }
                file == null -> {
                    // 選んだあとにファイルが消えた/まだ作られていない。
                    views.setTextViewText(R.id.tail_title, rel)
                    views.setTextViewText(R.id.tail_body, context.getString(R.string.tail_missing))
                    views.setTextViewText(R.id.tail_footer, "")
                }
                else -> {
                    val tail = TailReader.read(file, lines)
                    views.setTextViewText(R.id.tail_title, rel)
                    views.setTextViewText(
                        R.id.tail_body,
                        if (tail.isEmpty()) context.getString(R.string.tail_empty)
                        else tail.joinToString("\n")
                    )
                    views.setTextViewText(
                        R.id.tail_footer,
                        context.getString(
                            R.string.tail_footer,
                            hhmm.format(Date()),
                            file.length() / 1024
                        )
                    )
                }
            }
            return views
        }

        private fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, TailConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData("z2term://tail/$appWidgetId/config".toUri())
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
    }
}
