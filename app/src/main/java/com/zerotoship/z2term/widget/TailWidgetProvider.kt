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
            // ⟳ タップ。押したことが分かるよう「更新中…」を先に出してから読み直す。
            // マクロ完了などアプリ側からの refresh も同じ経路を通るが、そちらは
            // ユーザーが押したわけではないので「更新中…」は出さない。
            ACTION_REFRESH -> {
                val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
                background(context) { ctx ->
                    if (manual) {
                        showBusy(ctx)
                        Thread.sleep(WidgetFeedback.BUSY_HOLD_MS)
                    }
                    renderAll(ctx)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE ->
                background(context) { ctx -> renderAll(ctx) }
            else -> super.onReceive(context, intent)
        }
    }

    /** ウィジェットの大きさが変わったら、入る行数が変わるので描き直す。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        background(context) { ctx -> renderAll(ctx) }
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

        /** true なら**ユーザーが ⟳ を押した**もの (振動と「更新中…」を出す合図)。 */
        private const val EXTRA_MANUAL = "manual"

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

        /** 「いま読み直している」ことだけを先に見せる (D1 の同名関数と同じ理由)。 */
        private fun showBusy(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val busy = RemoteViews(context.packageName, R.layout.widget_tail).apply {
                setTextViewText(R.id.tail_refresh, context.getString(R.string.widget_busy_glyph))
                setTextViewText(R.id.tail_footer, context.getString(R.string.widget_refreshing))
            }
            ids.forEach { id -> runCatching { mgr.partiallyUpdateAppWidget(id, busy) } }
        }

        /** 全ウィジェットを今の中身で描き直す (バックグラウンドスレッドから呼ぶこと)。 */
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, mgr, id)) }
        }

        /**
         * いまのウィジェットの高さに入る行数。
         *
         * 行数を固定にしていた 0.8.219 までは、**ウィジェットを縦に伸ばすと下に隙間ができた**
         * (実機フィードバック)。`OPTION_APPWIDGET_MIN_HEIGHT` は「そのウィジェットが確実に持てる
         * 高さ (dp)」なので、そこからヘッダー・フッター・余白を引いた残りを 1 行の高さで割る。
         * 取れないときだけ [TailStore.DEFAULT_LINES]。
         */
        private fun linesFor(mgr: AppWidgetManager, appWidgetId: Int): Int {
            val h = runCatching {
                mgr.getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            }.getOrDefault(0)
            if (h <= 0) return TailStore.DEFAULT_LINES
            // 内訳: 上下 padding 20 + ヘッダー 40 + 本文の上 margin 6 + 本文 padding 12
            //      + フッター (margin 5 + 文字 13)。
            val body = h - (20 + 40 + 6 + 12 + 18)
            // 本文は 10sp・等幅。行送りは概ね 13dp。
            return (body / 13).coerceIn(TailStore.MIN_LINES, TailStore.MAX_LINES)
        }

        private fun buildViews(context: Context, mgr: AppWidgetManager, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tail)
            val hhmm = SimpleDateFormat("HH:mm:ss", Locale.US)
            // ⚠ ⟳ の文字を必ず書き戻す ([showBusy] の ⏳ が残り続けるのを防ぐ)。
            views.setTextViewText(R.id.tail_refresh, context.getString(R.string.widget_refresh_glyph))

            views.setOnClickPendingIntent(
                R.id.tail_refresh,
                PendingIntent.getBroadcast(
                    context, appWidgetId * SLOTS_PER_WIDGET + SLOT_REFRESH,
                    Intent(context, TailWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                        .setData("z2term://tail/$appWidgetId/refresh".toUri())
                        .putExtra(EXTRA_MANUAL, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            views.setOnClickPendingIntent(R.id.tail_config, configIntent(context, appWidgetId))
            // 本文をタップしたらアプリを開く (端末で続きを見たくなるのが自然な流れ)。
            views.setOnClickPendingIntent(R.id.tail_body, openAppIntent(context))

            val rel = TailStore.path(context, appWidgetId)
            val file = TailStore.file(context, appWidgetId)
            val lines = linesFor(mgr, appWidgetId)

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
