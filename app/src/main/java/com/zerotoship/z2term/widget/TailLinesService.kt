package com.zerotoship.z2term.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.zerotoship.z2term.R

/**
 * ライブ tail ウィジェット ([TailWidgetProvider]) の本文の行を作るサービス。
 *
 * ランチャーが一覧を描くときに bind してくるだけで、常駐はしない。読み直しは
 * [TailWidgetProvider.renderAll] の `notifyAppWidgetViewDataChanged` で起き、そのとき
 * [Factory.onDataSetChanged] がバインダースレッドで呼ばれる (ファイルを読んでよい)。
 */
class TailLinesService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )

    private class Factory(
        private val context: Context,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        @Volatile
        private var lines: List<String> = emptyList()

        @Volatile
        private var textSp = TailStore.DEFAULT_TEXT_SP.toFloat()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            textSp = TailStore.textSp(context, appWidgetId).toFloat()
            val file = TailStore.file(context, appWidgetId)
            lines = if (file == null) emptyList()
            else TailReader.read(file, TailStore.LINES, TailStore.mode(context, appWidgetId))
        }

        override fun onDestroy() {
            lines = emptyList()
        }

        override fun getCount(): Int = lines.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tail_item)
            val line = lines.getOrNull(position) ?: return views
            // 空行も 1 行として高さを持たせる (詰めると段落の区切りが消える)。
            views.setTextViewText(R.id.tail_line, line.ifEmpty { " " })
            views.setTextViewTextSize(R.id.tail_line, TypedValue.COMPLEX_UNIT_SP, textSp)
            MacroWidgets.textColor(
                context, views, R.id.tail_line,
                if (TailReader.isAlert(line)) R.color.widget_log_alert else R.color.widget_log_text
            )
            views.setOnClickFillInIntent(R.id.tail_line, Intent())
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        // 行は読み直すたびにずれる (末尾に足される) ので、位置を id にする。
        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
