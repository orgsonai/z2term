package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.zerotoship.z2term.R

/**
 * ホーム画面ウィジェット「状態＋マクロ (マス目)」。0.8.212 の D1 を 1.0.55 で作り直したもの。
 *
 * 上段に**いまの状態** (ssh 接続先 / 常駐サーバー数 / 有効な `z2-when` ルール数 / 電池)、
 * 下段に**マクロ 4 個のマス (2×2)** を並べ、タップで `~/.z2term/macros/<name>.sh` を
 * **アプリを開かずバックグラウンド実行**する。5 個以上並べたいときは一覧型
 * ([MacroListWidgetProvider]) を置く — 土台 ([MacroWidgets]) は共通。
 *
 * ⚠ **クラス名を変えない。** 置き済みのウィジェットはこのクラス名で結び付いているので、
 * 変えるとホーム画面から消える。
 *
 * **配線**: マス → [PendingIntent] (自分宛のブロードキャスト) → このレシーバ →
 * [MacroWidgets.run] (= `z2-when` のルール実行と同じ [com.zerotoship.z2term.service.HeadlessRun]) →
 * 完了を待たずに再描画。新しい常駐サービスは足さない (電池要因を増やさない)。
 *
 * **更新のきっかけ**は 3 つ:
 *  1. OS の定期更新 (`updatePeriodMillis` = 30 分。これ以上は詰められない)
 *  2. ⟳ ボタンのタップ (その場で読み直す)
 *  3. アプリ側からの [refresh] (常駐サーバーの起動/停止・`z2-when` ルールの変更・マクロの終了時)
 *
 * 読み取りはファイル I/O と設定 DataStore を含むので、描画は必ず [goAsync] + 別スレッドで行う。
 */
class StatusWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RUN_MACRO -> {
                val macro = intent.getStringExtra(EXTRA_MACRO).orEmpty()
                background(context) { ctx ->
                    if (macro.isNotBlank()) MacroWidgets.run(ctx, macro)
                    MacroWidgets.renderEverything(ctx)
                }
            }
            // 実行中のマスをもう一度押したとき。停止は最大 1 秒ブロックするので必ず別スレッドで。
            ACTION_STOP_MACRO -> {
                val macro = intent.getStringExtra(EXTRA_MACRO).orEmpty()
                background(context) { ctx ->
                    if (macro.isNotBlank()) MacroWidgets.stop(ctx, macro)
                    MacroWidgets.renderEverything(ctx)
                }
            }
            // ⟳ タップ / アプリ側からの再描画。⟳ のときだけ「更新中」を先に出す。
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
            // 定期更新 (APPWIDGET_UPDATE) も自前で受けて非同期に描く。onUpdate は使わない。
            AppWidgetManager.ACTION_APPWIDGET_UPDATE ->
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

        /** マスのタップ。 */
        private const val ACTION_RUN_MACRO = "com.zerotoship.z2term.widget.RUN_MACRO"

        /** 実行中のマスのタップ (= 停止)。 */
        private const val ACTION_STOP_MACRO = "com.zerotoship.z2term.widget.STOP_MACRO"

        private const val EXTRA_MACRO = "macro"

        /** true なら**ユーザーが ⟳ を押した**もの (「更新中」を出す合図)。 */
        private const val EXTRA_MANUAL = "manual"

        /** マス 1 つぶんの View の id (先頭から [WidgetStore.MAX_MACROS] 個使う)。 */
        private val TILES = listOf(
            MacroWidgets.Ids(
                R.id.widget_tile_0, R.id.widget_tile_0_name, R.id.widget_tile_0_desc,
                R.id.widget_tile_0_state, R.id.widget_tile_0_timer, R.id.widget_tile_0_icon,
            ),
            MacroWidgets.Ids(
                R.id.widget_tile_1, R.id.widget_tile_1_name, R.id.widget_tile_1_desc,
                R.id.widget_tile_1_state, R.id.widget_tile_1_timer, R.id.widget_tile_1_icon,
            ),
            MacroWidgets.Ids(
                R.id.widget_tile_2, R.id.widget_tile_2_name, R.id.widget_tile_2_desc,
                R.id.widget_tile_2_state, R.id.widget_tile_2_timer, R.id.widget_tile_2_icon,
            ),
            MacroWidgets.Ids(
                R.id.widget_tile_3, R.id.widget_tile_3_name, R.id.widget_tile_3_desc,
                R.id.widget_tile_3_state, R.id.widget_tile_3_timer, R.id.widget_tile_3_icon,
            ),
        )

        /** マスの行 (2 個ずつ)。下の行が丸ごと空なら行ごと隠す。 */
        private val ROWS = intArrayOf(R.id.widget_tiles_row_0, R.id.widget_tiles_row_1)

        /**
         * 置かれているマクロウィジェットを描き直す。常駐サーバーの起動/停止や `z2-when` ルールの
         * 変更など、**アプリ側で状態が変わったとき**に呼ぶ。一覧型 ([MacroListWidgetProvider]) も
         * 同じ状態を出しているので、こちらを呼べば両方に届く。置かれていなければ何もしない。
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            MacroListWidgetProvider.refresh(app)
            if (widgetIds(app).isEmpty()) return
            app.sendBroadcast(
                Intent(app, StatusWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }

        private fun widgetIds(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))

        /**
         * 「いま読み直している」ことだけを先に見せる (⟳ の絵とフッターだけを差し替える)。
         * 読み直しはたいてい一瞬で終わるため、これが無いと**画面上は何も起きていないように見える**。
         */
        private fun showBusy(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val busy = MacroWidgets.busyViews(context, R.layout.widget_status)
            ids.forEach { id -> runCatching { mgr.partiallyUpdateAppWidget(id, busy) } }
        }

        /** 全ウィジェットを今の状態で描き直す (バックグラウンドスレッドから呼ぶこと)。 */
        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val snapshot = MacroWidgets.Snapshot.read(context)
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, id, snapshot)) }
        }

        // --- 描画 ---

        private fun buildViews(context: Context, appWidgetId: Int, s: MacroWidgets.Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            MacroWidgets.bindHeader(
                context, views, appWidgetId, s,
                refresh = broadcast(context, appWidgetId, SLOT_REFRESH, ACTION_REFRESH, null, manual = true),
                config = configIntent(context, appWidgetId),
            )

            val now = System.currentTimeMillis()
            val macros = WidgetStore.macros(context, appWidgetId)
            TILES.forEachIndexed { slot, ids ->
                val name = macros.getOrNull(slot)
                if (name == null) {
                    // 空きマスは場所だけ残す (2×2 の形を崩さない)。押しても何も起きない。
                    views.setViewVisibility(ids.root, View.INVISIBLE)
                    return@forEachIndexed
                }
                val status = WidgetStore.status(context, name, MacroWidgets.isRunning(name), now)
                views.setViewVisibility(ids.root, View.VISIBLE)
                MacroWidgets.bindMacro(
                    context, views, appWidgetId, ids, MacroWidgets.Surface.TILE,
                    name, status, WidgetStore.description(context, name),
                )
                // 実行中は同じマスのタップで止める (RemoteViews に長押しは無いので、モードを
                // 増やさずトグルにするのが自然)。
                views.setOnClickPendingIntent(
                    ids.root,
                    broadcast(
                        context, appWidgetId, slot,
                        if (status.kind == MacroStatus.Kind.RUNNING) ACTION_STOP_MACRO else ACTION_RUN_MACRO,
                        name,
                    )
                )
            }
            ROWS.forEachIndexed { row, rowId ->
                views.setViewVisibility(
                    rowId,
                    if (row == 0 || macros.size > row * 2) View.VISIBLE else View.GONE
                )
            }
            MacroWidgets.bindFooter(context, views, s, hasMacros = macros.isNotEmpty(), now = now)
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

        /**
         * マス 1 つぶんの自分宛ブロードキャスト。requestCode と data の両方を
         * ウィジェット×スロットで一意にする (どちらかが同じだと PendingIntent が使い回され、
         * 別のマスが同じマクロを走らせてしまう)。
         */
        private fun broadcast(
            context: Context,
            appWidgetId: Int,
            slot: Int,
            action: String,
            macro: String?,
            manual: Boolean = false,
        ): PendingIntent {
            val intent = Intent(context, StatusWidgetProvider::class.java)
                .setAction(action)
                .setData("z2term://widget/$appWidgetId/$slot".toUri())
            if (macro != null) intent.putExtra(EXTRA_MACRO, macro)
            if (manual) intent.putExtra(EXTRA_MANUAL, true)
            return PendingIntent.getBroadcast(
                context, appWidgetId * SLOTS_PER_WIDGET + slot, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /** ウィジェット 1 個あたりの PendingIntent スロット数 (マス + ⟳ + ⚙)。 */
        private const val SLOTS_PER_WIDGET = 8

        /** ⟳ が使うスロット番号 (マスと衝突しない値)。 */
        private const val SLOT_REFRESH = 7

        /** ⚙ が使うスロット番号。 */
        private const val SLOT_CONFIG = 6
    }
}
