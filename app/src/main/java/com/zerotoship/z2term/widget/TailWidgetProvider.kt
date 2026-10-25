package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面ウィジェット「ライブ tail」(0.8.217 の D2。1.0.55 で本文を一覧に作り直した)。
 *
 * **何ができるか**: 選んだファイルの**末尾 / 先頭**をホーム画面に出す。
 * 「ホーム画面で `tail` / `head`」。マクロや `z2-when` が書いたログ・`events.jsonl`・
 * セッションログなど、端末を開かずに「いま何が起きたか」を見るための窓。
 * 末尾 (tail) は増え続けるログ用、先頭 (head) は書き終わったレポートや設定ファイル用。
 *
 * **本文は中でスクロールする** (1.0.55)。1.0.54 までは 1 つの TextView に高さぶんの行だけを
 * 流し込んでいたので、入りきらない行は読めず、長い行は端で切れていた。いまは
 * [TailStore.LINES] 行までを一覧に読み込み、行は折り返し、エラーの行 ([TailReader.isAlert]) は
 * 色を変える。行は [TailLinesService] が作る。
 *
 * **末尾と先頭で一覧を 2 つ持つ**: 末尾側 (`tail_list_end`) は下から積む
 * (`stackFromBottom`) ので、開いた時点で最新の行が見えている。RemoteViews からは
 * 積む向きを切り替えられないため、先頭側 (`tail_list_start`) を別に置き、見せる方だけに中身を渡す。
 *
 * **常駐は増やさない**。更新のきっかけは
 *  1. OS の定期更新 (`updatePeriodMillis` = 30 分。OS 側の下限)
 *  2. ⟳ タップ (その場で読み直す)
 *  3. アプリ側からの [refresh] — マクロやルールの実行が終わったとき
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

        /** true なら**ユーザーが ⟳ を押した**もの (「更新中…」を出す合図)。 */
        private const val EXTRA_MANUAL = "manual"

        /** ウィジェット 1 個あたりの PendingIntent スロット数 (⟳ / ⚙ / 行のひな形)。 */
        private const val SLOTS_PER_WIDGET = 4
        private const val SLOT_REFRESH = 1
        private const val SLOT_CONFIG = 2
        private const val SLOT_LINE = 3

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

        /** 「いま読み直している」ことだけを先に見せる (マクロウィジェットの同名関数と同じ理由)。 */
        private fun showBusy(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val busy = RemoteViews(context.packageName, R.layout.widget_tail).apply {
                setImageViewResource(R.id.tail_refresh, R.drawable.ic_widget_refreshing)
                setTextViewText(R.id.tail_footer, context.getString(R.string.widget_refreshing))
            }
            ids.forEach { id -> runCatching { mgr.partiallyUpdateAppWidget(id, busy) } }
        }

        /**
         * 全ウィジェットを今の中身で描き直す (バックグラウンドスレッドから呼ぶこと)。
         *
         * ⚠ `updateAppWidget` だけでは**一覧の行は変わらない**。必ず
         * `notifyAppWidgetViewDataChanged` で読み直させる。
         *
         * ⚠ 読み直させるのは**見せている側の一覧だけ**。一覧は中身が入った時点で自分を表示に
         * 戻す (空なら隠して空表示を出す) ので、隠した側まで読み直させると、末尾/先頭を
         * 切り替えたあとに 2 つの一覧が両方出てしまう。
         */
        @Suppress("DEPRECATION") // RemoteViewsService 経由の一覧 (MacroListWidgetProvider の注記参照)
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            ids.forEach { id ->
                mgr.updateAppWidget(id, buildViews(context, id))
                mgr.notifyAppWidgetViewDataChanged(id, listFor(TailStore.mode(context, id)))
            }
        }

        /** [mode] のときに見せる一覧 (末尾は下から積む方、先頭は上から積む方)。 */
        private fun listFor(mode: TailStore.Mode): Int =
            if (mode == TailStore.Mode.HEAD) R.id.tail_list_start else R.id.tail_list_end

        @Suppress("DEPRECATION")
        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tail)
            val hms = SimpleDateFormat("HH:mm:ss", Locale.US)
            // ⚠ ⟳ の絵を必ず書き戻す ([showBusy] の「更新中」が残り続けるのを防ぐ)。
            views.setImageViewResource(R.id.tail_refresh, R.drawable.ic_widget_refresh)

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
            // 見出しと、何も無いときの案内をタップしたらアプリを開く (端末で続きを見たくなるのが自然な流れ)。
            views.setOnClickPendingIntent(R.id.tail_title, openAppIntent(context, 0))
            views.setOnClickPendingIntent(R.id.tail_empty, openAppIntent(context, 0))
            // 文字の大きさ。見出しとフッターは固定 (本文だけを設定で動かすのは 0.8.255 から同じ)。
            views.setTextViewTextSize(
                R.id.tail_empty, TypedValue.COMPLEX_UNIT_SP,
                TailStore.textSp(context, appWidgetId).toFloat()
            )

            val rel = TailStore.path(context, appWidgetId)
            val file = TailStore.file(context, appWidgetId)
            val mode = TailStore.mode(context, appWidgetId)
            val shown = listFor(mode)
            val hidden = if (shown == R.id.tail_list_end) R.id.tail_list_start else R.id.tail_list_end

            views.setViewVisibility(hidden, View.GONE)
            when {
                rel == null -> {
                    views.setTextViewText(R.id.tail_title, context.getString(R.string.tail_title_none))
                    showMessage(views, shown, context.getString(R.string.tail_pick_file))
                    views.setTextViewText(R.id.tail_footer, "")
                }
                file == null -> {
                    // 選んだあとにファイルが消えた/まだ作られていない。
                    views.setTextViewText(R.id.tail_title, rel)
                    showMessage(views, shown, context.getString(R.string.tail_missing))
                    views.setTextViewText(R.id.tail_footer, "")
                }
                else -> {
                    views.setTextViewText(R.id.tail_title, rel)
                    views.setViewVisibility(shown, View.VISIBLE)
                    // 中身が空のファイルは一覧の代わりに「（中身なし）」を出す。
                    views.setTextViewText(R.id.tail_empty, context.getString(R.string.tail_empty))
                    views.setEmptyView(shown, R.id.tail_empty)
                    // data をウィジェットごとに変えないと、別のウィジェットが同じ中身を使い回す。
                    views.setRemoteAdapter(
                        shown,
                        Intent(context, TailLinesService::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            .setData("z2term://tail/$appWidgetId/lines/${mode.name.lowercase(Locale.US)}".toUri())
                    )
                    // 行のタップでアプリを開く。fill-in で中身を足すので MUTABLE が要る
                    // (送り先は MainActivity に固定した明示 Intent)。
                    views.setPendingIntentTemplate(shown, openAppIntent(context, appWidgetId * SLOTS_PER_WIDGET + SLOT_LINE, mutable = true))
                    // どちら側を見ているかは本文からは分からないので、フッターに必ず出す。
                    // `tail` / `head` はコマンド名そのものなので訳さない。
                    views.setTextViewText(
                        R.id.tail_footer,
                        context.getString(
                            R.string.tail_footer,
                            mode.name.lowercase(Locale.US),
                            hms.format(Date()),
                            file.length() / 1024
                        )
                    )
                }
            }
            return views
        }

        /** 一覧を隠して、案内の 1 行だけを出す (ファイル未選択・ファイルが無い)。 */
        private fun showMessage(views: RemoteViews, list: Int, text: String) {
            views.setViewVisibility(list, View.GONE)
            views.setViewVisibility(R.id.tail_empty, View.VISIBLE)
            views.setTextViewText(R.id.tail_empty, text)
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

        private fun openAppIntent(context: Context, requestCode: Int, mutable: Boolean = false): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or
                PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getActivity(context, requestCode, intent, flags)
        }
    }
}
