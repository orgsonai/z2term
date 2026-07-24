package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.SshEndpoint
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * ホーム画面ウィジェット D3「SSH 接続 QR」(§10-2)。
 *
 * **何ができるか**: いまこの端末に入るための `ssh://root@<IP>:<ポート>` を QR で出す。
 * 隣の PC のカメラで読めば、IP を目で写して打ち直す手間が要らない。Wi-Fi が変わって IP が
 * 動いても、⟳ か次の定期更新で追従する。
 *
 * **QR は自前実装** ([QrEncoder])。外部ライブラリは足していない。
 *
 * **常駐は増やさない** (D1/D2 と同じ)。更新は OS 定期 (30 分) と ⟳ タップ、
 * それに常駐サーバーの起動/停止で呼ばれる [refresh]。
 */
class QrWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                WidgetFeedback.tick(context)
                background(context) { ctx ->
                    showBusy(ctx)
                    Thread.sleep(WidgetFeedback.BUSY_HOLD_MS)
                    renderAll(ctx)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE ->
                background(context) { ctx -> renderAll(ctx) }
            else -> super.onReceive(context, intent)
        }
    }

    private fun background(context: Context, block: (Context) -> Unit) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                block(app)
            } catch (e: Exception) {
                Log.w(TAG, "qr widget work failed", e)
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true; name = "widget-qr"; start() }
    }

    companion object {

        private const val TAG = "QrWidget"

        private const val ACTION_REFRESH = "com.zerotoship.z2term.widget.QR_REFRESH"

        /** QR ビットマップの 1 辺 (px)。RemoteViews は大きな Bitmap を運べないので控えめに。 */
        private const val BITMAP_PX = 360

        /** QR の周りに置く余白 (モジュール数)。規格は 4 モジュール以上を求める。 */
        private const val QUIET_MODULES = 4

        /** 置かれている QR ウィジェットを描き直す。 */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (widgetIds(app).isEmpty()) return
            app.sendBroadcast(Intent(app, QrWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }

        private fun widgetIds(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, QrWidgetProvider::class.java))
        }.getOrDefault(IntArray(0))

        private fun showBusy(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val busy = RemoteViews(context.packageName, R.layout.widget_qr).apply {
                setTextViewText(R.id.qr_refresh, context.getString(R.string.widget_busy_glyph))
                setTextViewText(R.id.qr_caption, context.getString(R.string.widget_refreshing))
            }
            ids.forEach { id -> runCatching { mgr.partiallyUpdateAppWidget(id, busy) } }
        }

        internal fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> mgr.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_qr)
            views.setOnClickPendingIntent(
                R.id.qr_refresh,
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, QrWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                        .setData("z2term://qr/refresh".toUri()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            views.setOnClickPendingIntent(R.id.qr_image, openAppIntent(context))

            val distroId = runCatching {
                runBlocking { AppSettings(context).flow.first() }.distroId
            }.getOrNull().orEmpty()
            val uri = if (distroId.isEmpty()) null else SshEndpoint.sshUri(context, distroId)
            val matrix = uri?.let { QrEncoder.encode(it) }

            if (matrix == null) {
                views.setViewVisibility(R.id.qr_image, View.GONE)
                views.setTextViewText(R.id.qr_caption, context.getString(R.string.qr_no_address))
            } else {
                views.setViewVisibility(R.id.qr_image, View.VISIBLE)
                views.setImageViewBitmap(R.id.qr_image, toBitmap(matrix))
                // sshd が動いていなければ「QR は出るが今は入れない」ので、そのことを書いておく。
                val running = ServerDaemonManager.isRunning &&
                    runCatching { ServerDaemonManager.readStatus(context) }.getOrDefault(emptyList())
                        .any { it.state == "running" && it.command?.contains("sshd") == true }
                views.setTextViewText(
                    R.id.qr_caption,
                    if (running) uri else context.getString(R.string.qr_sshd_stopped, uri)
                )
            }
            return views
        }

        /**
         * QR を白黒のビットマップにする。余白 ([QUIET_MODULES]) を含めて 1 辺 [BITMAP_PX] に収め、
         * **1 モジュールが整数ピクセルになる倍率**に丸める (半端だと読み取り機が滲みで迷う)。
         */
        private fun toBitmap(m: QrEncoder.Matrix): Bitmap {
            val modules = m.size + QUIET_MODULES * 2
            val scale = (BITMAP_PX / modules).coerceAtLeast(1)
            val px = modules * scale
            val pixels = IntArray(px * px) { Color.WHITE }
            for (y in 0 until m.size) {
                for (x in 0 until m.size) {
                    if (!m.isDark(x, y)) continue
                    val left = (x + QUIET_MODULES) * scale
                    val top = (y + QUIET_MODULES) * scale
                    for (dy in 0 until scale) {
                        val row = (top + dy) * px
                        for (dx in 0 until scale) pixels[row + left + dx] = Color.BLACK
                    }
                }
            }
            return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
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
