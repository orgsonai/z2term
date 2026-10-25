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
import com.zerotoship.z2term.R

/**
 * ホーム画面ウィジェット「状態＋マクロ (一覧)」(1.0.55)。
 *
 * マス目型 ([StatusWidgetProvider]) と同じ上段に、マクロを **1 行 1 個の一覧**で並べる。
 * 一覧はウィジェットの中でスクロールするので、マス目型の 4 個を超えて
 * [WidgetStore.MAX_LIST_MACROS] 個まで置ける。行のタップで実行、実行中の行のタップで停止。
 *
 * 一覧の中身は [MacroListService] が 1 行ずつ作る (RemoteViews はリストの行を動的に
 * 生やせないので、コレクション用のサービスに任せる)。行が変わるのは [renderAll] で
 * `notifyAppWidgetViewDataChanged` を呼んだときだけで、自分から見に行くことはしない
 * (常駐もポーリングも足さない)。
 */
class MacroListWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 行のタップ。行ごとの fill-in Intent が [EXTRA_MACRO] と [EXTRA_STOP] を足してくる。
            ACTION_ITEM -> {
                val macro = intent.getStringExtra(EXTRA_MACRO).orEmpty()
                val stop = intent.getBooleanExtra(EXTRA_STOP, false)
                background(context) { ctx ->
                    if (macro.isNotBlank()) {
                        if (stop) MacroWidgets.stop(ctx, macro) else MacroWidgets.run(ctx, macro)
                    }
                    MacroWidgets.renderEverything(ctx)
                }
            }
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
        appWidgetIds.forEach { WidgetStore.clear(context, it) }
    }

    private fun background(context: Context, block: (Context) -> Unit) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                block(app)
            } catch (e: Exception) {
                Log.w(TAG, "list widget work failed", e)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true; name = "widget-list"; start() }
    }

    companion object {

        private const val TAG = "MacroListWidget"

        private const val ACTION_REFRESH = "com.zerotoship.z2term.widget.LIST_REFRESH"

        /** 一覧の行のタップ (実行も停止もこれ 1 つ。どちらかは [EXTRA_STOP] で分ける)。 */
        private const val ACTION_ITEM = "com.zerotoship.z2term.widget.LIST_ITEM"

        internal const val EXTRA_MACRO = "macro"

        /** true なら実行中の行を押した (= 停止)。 */
        internal const val EXTRA_STOP = "stop"

        private const val EXTRA_MANUAL = "manual"

        /** ウィジェット 1 個あたりの PendingIntent スロット数 (行のひな形 + ⟳ + ⚙)。 */
        private const val SLOTS_PER_WIDGET = 4
        private const val SLOT_ITEM = 0
        private const val SLOT_REFRESH = 1
        private const val SLOT_CONFIG = 2

        /**
         * requestCode をマス目型・ライブ tail と重ねないための下駄。PendingIntent は
         * 送り先のクラスと data でも区別されるが、念のため番号の帯も分けておく。
         */
        private const val REQUEST_BASE = 1_000_000

        /** 置かれている一覧型を描き直す。1 つも置かれていなければ何もしない。 */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (widgetIds(app).isEmpty()) return
            app.sendBroadcast(
                Intent(app, MacroListWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }

        private fun widgetIds(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, MacroListWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))

        private fun showBusy(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val busy = MacroWidgets.busyViews(context, R.layout.widget_list)
            ids.forEach { id -> runCatching { mgr.partiallyUpdateAppWidget(id, busy) } }
        }

        /**
         * 全ウィジェットを描き直し、一覧の行も読み直させる (バックグラウンドスレッドから呼ぶこと)。
         *
         * ⚠ `updateAppWidget` だけでは**行は変わらない** (同じ Intent の一覧は前の中身を使い回す)。
         * 状態が変わったら必ず `notifyAppWidgetViewDataChanged` も呼ぶ。
         */
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val snapshot = MacroWidgets.Snapshot.read(context)
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, id, snapshot)) }
            @Suppress("DEPRECATION") // RemoteViewsService 経由の一覧 (minSdk 29 のため。下の注記参照)
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        @Suppress("DEPRECATION")
        private fun buildViews(context: Context, appWidgetId: Int, s: MacroWidgets.Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_list)
            MacroWidgets.bindHeader(
                context, views, appWidgetId, s,
                refresh = refreshIntent(context, appWidgetId),
                config = configIntent(context, appWidgetId),
            )

            // 一覧の中身はサービスが作る。data をウィジェットごとに変えないと、2 個目以降の
            // ウィジェットが 1 個目と同じ中身 (同じ factory) を使い回す。
            // ⚠ setRemoteAdapter(Int, Intent) は API 35 で非推奨 (後継の RemoteCollectionItems は
            // API 31 から)。minSdk 29 を切らない限りこちらを使う — 両方を持つと描き方が 2 通りになる。
            val adapter = Intent(context, MacroListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData("z2term://list/$appWidgetId/items".toUri())
            views.setRemoteAdapter(R.id.widget_list, adapter)
            views.setEmptyView(R.id.widget_list, R.id.widget_list_empty)
            views.setTextViewText(R.id.widget_list_empty, context.getString(R.string.widget_no_macros))

            // 行のタップのひな形。行ごとの fill-in が macro / stop を足すので MUTABLE が要る
            // (送り先はこのクラスに固定した明示 Intent なので、中身を書き換えられても他所へは飛ばない)。
            val template = Intent(context, MacroListWidgetProvider::class.java)
                .setAction(ACTION_ITEM)
                .setData("z2term://list/$appWidgetId/item".toUri())
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getBroadcast(
                    context, REQUEST_BASE + appWidgetId * SLOTS_PER_WIDGET + SLOT_ITEM, template,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            val macros = WidgetStore.macros(context, appWidgetId, WidgetStore.MAX_LIST_MACROS)
            MacroWidgets.bindFooter(
                context, views, s, hasMacros = macros.isNotEmpty(), now = System.currentTimeMillis()
            )
            return views
        }

        private fun refreshIntent(context: Context, appWidgetId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_BASE + appWidgetId * SLOTS_PER_WIDGET + SLOT_REFRESH,
                Intent(context, MacroListWidgetProvider::class.java)
                    .setAction(ACTION_REFRESH)
                    .setData("z2term://list/$appWidgetId/refresh".toUri())
                    .putExtra(EXTRA_MANUAL, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** ⚙ から設定画面を開く。設定画面はマス目型と共通 (どちらかは appWidgetId から判別する)。 */
        private fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData("z2term://list/$appWidgetId/config".toUri())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return PendingIntent.getActivity(
                context, REQUEST_BASE + appWidgetId * SLOTS_PER_WIDGET + SLOT_CONFIG, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
