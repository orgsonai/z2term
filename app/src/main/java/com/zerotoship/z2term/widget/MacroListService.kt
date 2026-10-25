package com.zerotoship.z2term.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.zerotoship.z2term.R

/**
 * 一覧型マクロウィジェット ([MacroListWidgetProvider]) の行を作るサービス。
 *
 * ランチャーが一覧を描くときに bind してくるだけで、常駐はしない。
 * 行の読み直しは [MacroListWidgetProvider.renderAll] の `notifyAppWidgetViewDataChanged` で起き、
 * そのとき [Factory.onDataSetChanged] がバインダースレッドで呼ばれる (ファイルを読んでよい)。
 */
class MacroListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )

    private class Row(val name: String, val status: MacroStatus, val description: String)

    private class Factory(
        private val context: Context,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        @Volatile
        private var rows: List<Row> = emptyList()

        private val ids = MacroWidgets.Ids(
            R.id.item_root, R.id.item_name, R.id.item_desc,
            R.id.item_state, R.id.item_timer, R.id.item_icon,
        )

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val now = System.currentTimeMillis()
            rows = WidgetStore.macros(context, appWidgetId, WidgetStore.MAX_LIST_MACROS).map { name ->
                Row(
                    name = name,
                    status = WidgetStore.status(context, name, MacroWidgets.isRunning(name), now),
                    description = WidgetStore.description(context, name),
                )
            }
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_macro_item)
            val row = rows.getOrNull(position) ?: return views
            MacroWidgets.bindMacro(
                context, views, appWidgetId, ids, MacroWidgets.Surface.ROW,
                row.name, row.status, row.description,
            )
            views.setOnClickFillInIntent(
                R.id.item_root,
                Intent()
                    .putExtra(MacroListWidgetProvider.EXTRA_MACRO, row.name)
                    .putExtra(MacroListWidgetProvider.EXTRA_STOP, row.status.kind == MacroStatus.Kind.RUNNING)
            )
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            rows.getOrNull(position)?.name?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
