package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/** One screenshot for all automatic bars. Images are released after sampling, never saved. */
internal class EdgeHandleContrast(
    private val targets: List<Target>,
    private val canSample: () -> Boolean,
) {
    class Target(val area: () -> Rect?, val paint: (Boolean) -> Unit) {
        var black: Boolean? = null
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var disposed = false
    private var inFlight = false
    private val tick = Runnable { capture() }

    fun refresh() {
        main.removeCallbacks(tick)
        if (!inFlight) schedule(250)
    }

    fun dispose() {
        disposed = true
        main.removeCallbacks(tick)
    }

    private fun schedule(delay: Long) {
        if (!disposed && targets.isNotEmpty() && service() != null) main.postDelayed(tick, delay)
    }

    private fun service(): AndroidActions? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            AndroidActions.contrastService()?.takeIf {
                (it.serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
            }
        }.getOrNull()
    }

    private fun capture() {
        if (disposed || inFlight || Build.VERSION.SDK_INT < 30) return
        val sourceService = service() ?: return
        if (!canSample()) { schedule(1000); return }
        val regions = targets.mapNotNull { target -> target.area()?.let { target to Rect(it) } }
        if (regions.isEmpty()) { schedule(1000); return }
        inFlight = true
        // 取得の時刻を残す。z2-shot はこれを見て撮影間隔が空くまで待つ (RegionShot)。
        lastRequestAt = SystemClock.uptimeMillis()
        capture(sourceService, regions)
    }

    @RequiresApi(30)
    private fun capture(sourceService: AndroidActions, regions: List<Pair<Target, Rect>>) {
        fun complete(samples: List<Double?>?, delay: Long = 1000) {
            main.post {
                inFlight = false
                if (disposed) return@post
                if (sourceService === service() && canSample()) {
                    regions.forEachIndexed { index, (target, area) ->
                        val luminance = samples?.getOrNull(index)
                        // A moved or detached view invalidates an outstanding sample.
                        if (luminance != null && target.area() == area) {
                            val black = EdgeBarContrast.useBlack(luminance, target.black)
                            if (black != target.black) { target.black = black; target.paint(black) }
                        }
                    }
                }
                schedule(delay)
            }
        }
        try {
            sourceService.takeScreenshot(Display.DEFAULT_DISPLAY, Dispatchers.Default.asExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        var hardware: Bitmap? = null
                        var pixels: Bitmap? = null
                        val samples = try {
                            if (disposed) null else {
                                hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                // Copy once on the worker, not once per bar or on the UI thread.
                                val image = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                                pixels = image
                                image?.let { regions.map { (_, area) -> sample(it, area) } }
                            }
                        } catch (_: Exception) { null }
                        finally { pixels?.recycle(); hardware?.recycle(); buffer.close() }
                        complete(samples, if (samples == null) 5000 else 1000)
                    }

                    override fun onFailure(errorCode: Int) {
                        complete(null, if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) 1000 else 5000)
                    }
                })
        } catch (_: Exception) { complete(null, 5000) }
    }

    internal companion object {
        /**
         * 直近に画面取得を要求した時刻 ([SystemClock.uptimeMillis])。
         *
         * ⭐ **他の取得要求と共有する。** Android は画面取得の間隔に下限があり、続けて撮ると
         * `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` で断られる。バーの配色は約1秒ごとに
         * 撮っているので、`z2-shot` はここを見て必要なぶん待つ。
         */
        @Volatile var lastRequestAt = 0L
        const val MIN_INTERVAL_MS = 1000L
    }

    private fun sample(image: Bitmap, requested: Rect): Double? {
        val area = Rect(requested)
        if (!area.intersect(0, 0, image.width, image.height)) return null
        var total = 0.0
        var count = 0
        // Sample only the inward strip, keeping cost independent of the bar's length.
        for (row in 0 until 9) for (column in 0 until 3) {
            val x = area.left + ((column + 0.5f) * area.width() / 3).toInt().coerceAtMost(area.width() - 1)
            val y = area.top + ((row + 0.5f) * area.height() / 9).toInt().coerceAtMost(area.height() - 1)
            val color = image.getColor(x, y)
            if (color.alpha() > 0.9f) { total += color.luminance(); count++ }
        }
        return if (count > 0) total / count else null
    }
}
