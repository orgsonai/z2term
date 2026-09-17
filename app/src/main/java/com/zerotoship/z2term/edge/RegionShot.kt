package com.zerotoship.z2term.edge

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.zerotoship.z2term.R
import com.zerotoship.z2term.share.OutgoingShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 撮りたいところだけを撮る。
 *
 * 流れは **パネルと取っ手を隠す → 描画の反映を待つ → 画面を 1 枚取る → その静止画の上を囲む
 * → 指を離した時点で確定 → プレビュー → 保存 / 共有**。
 *
 * ⭐ **静止画の上で囲む**のが肝。生きている画面の上で囲むと、選択中のスクロールや再描画で
 * 対象がずれるうえ、囲むための重ね表示そのものが写り込む。先に 1 枚取ってしまえば、以降の
 * 操作は画像の上だけで完結し、**撮影物へ自分の UI が入る余地が無い**。
 *
 * ⚠ **隠した直後に撮らない。** ビューを `GONE` にしても画面から消えるのは次の合成のあとで、
 * すぐ撮ると消える前のパネルが写る。[waitFrames] で数フレーム空ける。
 *
 * ⚠ **座標は画像の画素で持つ。** 画面とオーバーレイの寸法は切り欠き・回転で一致しないことが
 * あるため、囲みは常に画像の画素座標で組み立て、表示するときだけ引き伸ばす。こうしないと
 * 「見えていた位置」と「保存された位置」がずれる。
 */
internal object RegionShot {
    /** 端末の画像フォルダ。ギャラリーから普通の画像として見える場所へ置く。 */
    private const val FOLDER = "Pictures/z2term"

    /** 囲み方。どれを選んでも以降の流れは同じで、変わるのは線の引き方だけ。 */
    enum class Shape { FREE, RECT, OVAL }

    private val main = Handler(Looper.getMainLooper())
    private var session: Session? = null

    val active: Boolean get() = session != null

    /** @param shape 省略時は自由囲み。`free` / `rect` / `oval`。 */
    fun start(context: Context, shape: String) {
        check(session == null) { context.getString(R.string.shot_busy) }
        // ⚠ `check(SDK_INT >= 30)` ではなくこの形で書く — lint は早期離脱だけを API の守りとして読む。
        if (Build.VERSION.SDK_INT < 30) error(context.getString(R.string.shot_needs_android11))
        val service = AndroidActions.contrastService()
            ?: error(context.getString(R.string.edge_accessibility_help))
        val initial = when (shape.trim().lowercase()) {
            "", "free", "freehand" -> Shape.FREE
            "rect", "rectangle", "box" -> Shape.RECT
            "oval", "circle", "ellipse" -> Shape.OVAL
            else -> throw IllegalArgumentException("z2-shot: free|rect|oval")
        }
        Session(service, initial).also { session = it }.begin()
    }

    /**
     * ユーザー補助が切れた・割り込まれたときの後始末。表示を必ず元へ戻す。
     *
     * Android 11 未満では [start] が先に断るので session は生まれない。その事実をここにも
     * 書いておく（書かないと「30 でしか無いものを 29 から呼んでいる」ことになる）。
     */
    fun cancel() {
        if (Build.VERSION.SDK_INT < 30) return
        main.post { session?.finish(null) }
    }

    /**
     * [count] フレームぶん待ってから [action] を呼ぶ。固定のミリ秒で当てずっぽうに待つのではなく、
     * 実際の描画の区切りを数える。
     */
    private fun waitFrames(count: Int, action: () -> Unit) {
        if (count <= 0) { action(); return }
        Choreographer.getInstance().postFrameCallback { waitFrames(count - 1, action) }
    }

    @RequiresApi(30)
    private class Session(val service: AccessibilityService, var shape: Shape) {
        private val wm = service.getSystemService(WindowManager::class.java)
        private var window: View? = null
        /** 取得した画面。プレビューを作り直せるよう、終わるまで持っておく。 */
        private var shot: Bitmap? = null
        /** 切り出した結果。保存・共有はこれを書き出す。 */
        private var cropped: Bitmap? = null
        /** 確定した囲み。**画像の画素座標**。 */
        private var region: Path? = null
        private var transparent = true
        private var preview = false

        fun begin() {
            // パネルと取っ手を両方しまう。片方だけでは写り込みが残る。
            EdgeRuntime.suspendForActions(true)
            waitFrames(3) { capture() }
        }

        private fun capture() {
            if (session !== this) return
            runCatching {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, Dispatchers.Default.asExecutor(),
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            var hardware: Bitmap? = null
                            val image = try {
                                hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                // ハードウェア面のままでは画素を読めないので 1 度だけ複製する。
                                hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (_: Exception) { null } finally {
                                hardware?.recycle(); buffer.close()
                            }
                            main.post {
                                if (session !== this@Session) { image?.recycle(); return@post }
                                if (image == null) finish(service.getString(R.string.shot_capture_failed))
                                else { shot = image; render() }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            main.post { finish(service.getString(R.string.shot_capture_failed)) }
                        }
                    })
            }.onFailure { finish(service.getString(R.string.shot_capture_failed)) }
        }

        // ── 切り出し ──────────────────────────────────────────────

        /**
         * 囲みの外接矩形で切り出す。透過なら囲みの形だけを残す。
         *
         * ⭐ **`clipPath` ではなくアルファのマスクを重ねる。** クリップは縁が階段状になり、
         * 円や斜めの囲みで目に見えて汚くなる。
         */
        private fun crop(): Bitmap? {
            val src = shot ?: return null
            val path = region ?: return null
            val bounds = RectF().also { path.computeBounds(it, true) }
            val area = Rect().also { bounds.roundOut(it) }
            if (!area.intersect(0, 0, src.width, src.height)) return null
            if (area.width() < 8 || area.height() < 8) return null
            val out = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(src, -area.left.toFloat(), -area.top.toFloat(), null)
            if (transparent) {
                val mask = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ALPHA_8)
                Canvas(mask).drawPath(
                    Path(path).apply { offset(-area.left.toFloat(), -area.top.toFloat()) },
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                canvas.drawBitmap(mask, 0f, 0f,
                    Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
                mask.recycle()
            }
            return out
        }

        private fun confirm(path: Path) {
            region = path
            val image = crop()
            if (image == null) {
                region = null
                Toast.makeText(service, R.string.shot_too_small, Toast.LENGTH_SHORT).show()
                render()
                return
            }
            cropped?.recycle()
            cropped = image
            preview = true
            render()
        }

        /** 透明の切り替えは囲みを保ったまま作り直す。囲み直させない。 */
        private fun rebuild() {
            val image = crop() ?: return
            cropped?.recycle()
            cropped = image
            render()
        }

        // ── 書き出し ──────────────────────────────────────────────

        private fun name() =
            "z2term-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"

        private fun save() {
            val image = cropped ?: return
            val fileName = name()
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
                    // ⚠ 書き終わるまで他のアプリに見せない。途中の画像を開かせないため。
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = service.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("insert failed")
                try {
                    resolver.openOutputStream(uri).use { out ->
                        check(image.compress(Bitmap.CompressFormat.PNG, 100, requireNotNull(out)))
                    }
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }.onSuccess {
                finish(service.getString(R.string.shot_saved, fileName, FOLDER))
            }.onFailure {
                Toast.makeText(service, R.string.shot_save_failed, Toast.LENGTH_LONG).show()
            }
        }

        private fun share() {
            val image = cropped ?: return
            runCatching {
                // 0.8.608 の共有と同じ置き場。共有専用のキャッシュなので端末の画像には混ざらない。
                val dir = File(service.cacheDir, "shared-files/shot-${System.currentTimeMillis()}")
                    .apply { mkdirs() }
                val file = File(dir, name())
                file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                service.startActivity(
                    Intent.createChooser(OutgoingShare.intent(service, listOf(file)), null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onSuccess { finish(null) }.onFailure {
                Toast.makeText(service, R.string.shot_save_failed, Toast.LENGTH_LONG).show()
            }
        }

        // ── 画面 ─────────────────────────────────────────────────

        private fun render() {
            window?.let { runCatching { wm.removeViewImmediate(it) } }
            window = null
            val content = FrameLayout(service)
            content.addView(if (preview) PreviewView() else SelectView(),
                FrameLayout.LayoutParams(-1, -1))
            content.addView(controls(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                val side = EdgeSettingsUi.dp(service, 12)
                leftMargin = side; rightMargin = side; bottomMargin = EdgeSettingsUi.dp(service, 36)
            })
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            runCatching { wm.addView(content, params) }
                .onSuccess { window = content }
                .onFailure { finish(it.message) }
        }

        private fun controls(): View {
            val column = EdgeSettingsUi.column(service).apply {
                background = EdgeSettingsUi.frame(service, fill = EdgeSettingsUi.canvas(service))
                setPadding(EdgeSettingsUi.dp(service, 12), EdgeSettingsUi.dp(service, 10),
                    EdgeSettingsUi.dp(service, 12), EdgeSettingsUi.dp(service, 10))
            }
            if (!preview) column.addView(EdgeSettingsUi.body(service, service.getString(
                if (shape == Shape.FREE) R.string.shot_hint_free else R.string.shot_hint_box
            )).apply { textSize = 13f }, LinearLayout.LayoutParams(-1, -2))
            val row = EdgeSettingsUi.row(service)
            column.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = EdgeSettingsUi.dp(service, 10)
            })
            fun button(label: String, kind: EdgeSettingsUi.Kind, action: () -> Unit) {
                row.addView(EdgeSettingsUi.button(service, label, kind, action),
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (row.childCount > 0) leftMargin = EdgeSettingsUi.dp(service, 8)
                    })
            }
            if (preview) {
                button(service.getString(if (transparent) R.string.shot_bg_transparent
                    else R.string.shot_bg_keep), EdgeSettingsUi.Kind.OUTLINE) {
                    transparent = !transparent; rebuild()
                }
                button(service.getString(R.string.shot_save), EdgeSettingsUi.Kind.PRIMARY) { save() }
                button(service.getString(R.string.shot_share), EdgeSettingsUi.Kind.PRIMARY) { share() }
                button(service.getString(R.string.shot_redo), EdgeSettingsUi.Kind.OUTLINE) {
                    preview = false; region = null
                    cropped?.recycle(); cropped = null
                    render()
                }
            } else {
                // 形は並べて置く。選び直しても囲みの流れは変わらない。
                listOf(Shape.FREE to R.string.shot_shape_free, Shape.RECT to R.string.shot_shape_rect,
                    Shape.OVAL to R.string.shot_shape_oval).forEach { (value, label) ->
                    button(service.getString(label),
                        if (shape == value) EdgeSettingsUi.Kind.PRIMARY else EdgeSettingsUi.Kind.OUTLINE) {
                        shape = value; render()
                    }
                }
            }
            button(service.getString(android.R.string.cancel), EdgeSettingsUi.Kind.QUIET) { finish(null) }
            return column
        }

        /** 静止画の上で囲む。タッチは下のアプリへ渡さない。 */
        private inner class SelectView : View(service) {
            private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = EdgeSettingsUi.accent(service)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            private val points = mutableListOf<Float>()
            private var anchorX = 0f
            private var anchorY = 0f
            private var currentX = 0f
            private var currentY = 0f
            private var drawing = false

            /** 表示は引き伸ばし、囲みは画像の画素で持つ。両者の橋渡しはここだけ。 */
            private fun scaleX() = (shot?.width ?: 1).toFloat() / width.coerceAtLeast(1)
            private fun scaleY() = (shot?.height ?: 1).toFloat() / height.coerceAtLeast(1)

            private fun build(): Path? {
                val path = Path()
                when (shape) {
                    Shape.FREE -> {
                        if (points.size < 6) return null
                        path.moveTo(points[0], points[1])
                        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
                        // 閉じていない線は始点と終点を結んで閉じる。囲みとして扱うため。
                        path.close()
                    }
                    Shape.RECT -> path.addRect(RectF(minOf(anchorX, currentX), minOf(anchorY, currentY),
                        maxOf(anchorX, currentX), maxOf(anchorY, currentY)), Path.Direction.CW)
                    Shape.OVAL -> path.addOval(RectF(minOf(anchorX, currentX), minOf(anchorY, currentY),
                        maxOf(anchorX, currentX), maxOf(anchorY, currentY)), Path.Direction.CW)
                }
                return path
            }

            override fun onDraw(canvas: Canvas) {
                val image = shot ?: return
                canvas.drawBitmap(image, null,
                    RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
                val path = (if (drawing) build() else null) ?: return
                canvas.save()
                canvas.scale(1f / scaleX(), 1f / scaleY())
                // 引き伸ばしで線まで太らないよう、拡大率のぶんだけ細くしておく。
                line.strokeWidth = 4f * scaleX()
                canvas.drawPath(path, line)
                canvas.restore()
            }

            override fun performClick(): Boolean { super.performClick(); return true }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x * scaleX()
                val y = event.y * scaleY()
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        drawing = true
                        points.clear(); points.add(x); points.add(y)
                        anchorX = x; anchorY = y; currentX = x; currentY = y
                    }
                    MotionEvent.ACTION_MOVE -> if (drawing) {
                        points.add(x); points.add(y); currentX = x; currentY = y
                    }
                    MotionEvent.ACTION_UP -> if (drawing) {
                        drawing = false
                        points.add(x); points.add(y); currentX = x; currentY = y
                        performClick()
                        build()?.let { confirm(it) } ?: run {
                            Toast.makeText(service, R.string.shot_too_small, Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> { drawing = false; points.clear() }
                }
                invalidate()
                return true
            }
        }

        /** 切り出した結果だけを見せる。市松はここだけの表示で、画像には焼き込まない。 */
        private inner class PreviewView : View(service) {
            private val light = Paint().apply { color = Color.parseColor("#FF3A3A3A") }
            private val dark = Paint().apply { color = Color.parseColor("#FF2A2A2A") }
            private val shade = Paint().apply { color = Color.parseColor("#CC000000") }

            override fun onDraw(canvas: Canvas) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
                val image = cropped ?: return
                val margin = EdgeSettingsUi.dp(service, 24).toFloat()
                val room = RectF(margin, margin, width - margin,
                    height - margin - EdgeSettingsUi.dp(service, 140))
                if (room.width() <= 0 || room.height() <= 0) return
                val scale = minOf(room.width() / image.width, room.height() / image.height, 1f)
                val w = image.width * scale
                val h = image.height * scale
                val target = RectF(room.centerX() - w / 2, room.centerY() - h / 2,
                    room.centerX() + w / 2, room.centerY() + h / 2)
                if (transparent) {
                    val step = EdgeSettingsUi.dp(service, 8)
                    canvas.save()
                    canvas.clipRect(target)
                    var row = 0
                    var top = target.top
                    while (top < target.bottom) {
                        var column = 0
                        var left = target.left
                        while (left < target.right) {
                            canvas.drawRect(left, top, left + step, top + step,
                                if ((row + column) % 2 == 0) light else dark)
                            left += step; column++
                        }
                        top += step; row++
                    }
                    canvas.restore()
                }
                canvas.drawBitmap(image, null, target, null)
            }
        }

        // ── 後始末 ────────────────────────────────────────────────

        /** どの経路でも必ずここを通す。通さないとパネルが消えたままになる。 */
        fun finish(message: String?) {
            if (session !== this) return
            session = null
            window?.let { runCatching { wm.removeViewImmediate(it) } }
            window = null
            shot?.recycle(); shot = null
            cropped?.recycle(); cropped = null
            region = null
            runCatching { EdgeRuntime.suspendForActions(false) }
            message?.let { Toast.makeText(service, it, Toast.LENGTH_LONG).show() }
        }
    }
}
