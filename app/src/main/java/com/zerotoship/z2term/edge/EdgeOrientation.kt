package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * エッジの窓 (ハンドル・パネル・端の目印) を、画面が回っても**端末本体に対して同じ向き・大きさ・位置**に
 * 出す (1.0.80・利用者の要望)。
 *
 * ⛔ **画面の向きに合わせて組み直さない。** 以前は横画面になると、パネルの高さ (既定は画面の高さの 72%)
 * が横画面の低い高さから計算され、横長に潰れた。ハンドルも横画面の辺に付け直されていた。
 * ⇒ 窓の中身は常に**縦持ちの座標 (以下「縦の枠」)** で組み立て、画面が回っているときだけ
 * [PortraitFrame] で中身を逆に回して置く。横画面のほかの表示から見ると 90 度回って見えるが、
 * スマホを縦に持てば縦画面のときとまったく同じに見える。
 *
 * 回転の数え方は [android.view.Surface] の `ROTATION_*` (0..3) と同じ「画面の絵が本来の向きから
 * 時計回りに何回 90° 回っているか」。`1` (ROTATION_90) は本体を反時計回りに倒した状態で、本体の
 * 上辺が画面の左に来る。
 *
 * 他のアプリの画面を扱う窓 (自動操作の軌跡・画面の範囲選び) は画面の座標そのものなので、これを使わない。
 */
internal object EdgeOrientation {
    @Suppress("DEPRECATION")
    fun turns(wm: WindowManager): Int = wm.defaultDisplay.rotation and 3

    /**
     * 画面の向きでの移動量 ([dx], [dy]、`rawX`/`rawY` の差) を、縦の枠での移動量に直す。
     * 画面の点 (x, y) と縦の枠の点 (px, py) の関係は、1 回転なら px = H - y / py = x。
     */
    fun frameDelta(turns: Int, dx: Float, dy: Float): Pair<Float, Float> = when (turns) {
        1 -> -dy to dx
        2 -> -dx to -dy
        3 -> dy to -dx
        else -> dx to dy
    }

    /** [view] が入っている窓の回転。回していない窓・窓の外では 0。 */
    fun turnsOf(view: View): Int {
        var at: View? = view
        while (at != null) {
            if (at is PortraitFrame) return at.turns
            at = at.parent as? View
        }
        return 0
    }

    /** 縦の枠での窓の位置と大きさを、画面 ([areaWidth] × [areaHeight]) での位置と大きさに直す。 */
    fun toScreen(turns: Int, areaWidth: Int, areaHeight: Int, p: WindowManager.LayoutParams,
            into: WindowManager.LayoutParams) {
        into.copyFrom(p)
        // 画面いっぱいの窓 (MATCH_PARENT) は、回しても画面いっぱい。
        if (p.width < 0 || p.height < 0) return
        val (x, y, w, h) = listOf(p.x, p.y, p.width, p.height)
        when (turns) {
            1 -> { into.x = y; into.y = areaHeight - x - w; into.width = h; into.height = w }
            2 -> { into.x = areaWidth - x - w; into.y = areaHeight - y - h }
            3 -> { into.x = areaWidth - y - h; into.y = x; into.width = h; into.height = w }
        }
    }
}

/**
 * 中身 [content] を縦の枠の大きさで並べ、[turns] だけ逆に回して窓いっぱいに置く入れ物 (窓の根)。
 *
 * 触った位置は Android が子の回転を逆にたどって中身へ渡すので、中身は縦の枠の座標で受け取る。
 * ただし `rawX`/`rawY` は画面の座標のままなので、移動量は [EdgeOrientation.frameDelta] で直す。
 * 入力欄の下に来るキーボードなどの「画面の辺からの余白」(insets) も、縦の枠の辺に付け替えて渡す。
 */
@SuppressLint("ViewConstructor")
internal class PortraitFrame(context: Context, val turns: Int, val content: View) : FrameLayout(context) {
    init {
        clipChildren = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val sideways get() = turns % 2 == 1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val (frameWidth, frameHeight) = if (sideways) height to width else width to height
        content.measure(MeasureSpec.makeMeasureSpec(frameWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = content.measuredWidth
        val h = content.measuredHeight
        content.layout(0, 0, w, h)
        // 左上を軸に回してから、画面の窓へ収まるようにずらす (中央を軸にすると、縦横の差が奇数の
        // とき半画素ずれて端に 1 画素の隙間が出る)。
        content.pivotX = 0f
        content.pivotY = 0f
        when (turns) {
            1 -> { content.rotation = -90f; content.translationX = 0f; content.translationY = w.toFloat() }
            2 -> { content.rotation = 180f; content.translationX = w.toFloat(); content.translationY = h.toFloat() }
            3 -> { content.rotation = 90f; content.translationX = h.toFloat(); content.translationY = 0f }
            else -> { content.rotation = 0f; content.translationX = 0f; content.translationY = 0f }
        }
    }

    // 窓の外を触った知らせ (ACTION_OUTSIDE) は中身の範囲に入らないので、そのままでは中身へ届かない。
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) content.dispatchTouchEvent(event)
        else super.dispatchTouchEvent(event)

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (turns == 0 || Build.VERSION.SDK_INT < 30) return super.dispatchApplyWindowInsets(insets)
        content.dispatchApplyWindowInsets(rotated(insets))
        return insets
    }

    private fun rotated(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT < 30) return insets
        fun turn(i: android.graphics.Insets): android.graphics.Insets = when (turns) {
            // 縦の枠の 上 / 左 / 下 / 右 が、画面のどの辺に当たるか。
            1 -> android.graphics.Insets.of(i.bottom, i.left, i.top, i.right)
            2 -> android.graphics.Insets.of(i.right, i.bottom, i.left, i.top)
            3 -> android.graphics.Insets.of(i.top, i.right, i.bottom, i.left)
            else -> i
        }
        val builder = WindowInsets.Builder(insets)
        builder.setInsets(WindowInsets.Type.ime(), turn(insets.getInsets(WindowInsets.Type.ime())))
        for (type in listOf(WindowInsets.Type.systemBars(), WindowInsets.Type.displayCutout())) {
            builder.setInsets(type, turn(insets.getInsets(type)))
            builder.setInsetsIgnoringVisibility(type, turn(insets.getInsetsIgnoringVisibility(type)))
        }
        return builder.build()
    }

    /** 中身の中の [local] (中身の座標) を、画面の座標の四角に直す。 */
    fun contentRectOnScreen(local: Rect): Rect {
        val box = RectF(local)
        val matrix = Matrix(content.matrix)
        matrix.postTranslate(content.left.toFloat(), content.top.toFloat())
        matrix.mapRect(box)
        val origin = IntArray(2).also(::getLocationOnScreen)
        return Rect(box.left.toInt() + origin[0], box.top.toInt() + origin[1],
            box.right.toInt() + origin[0], box.bottom.toInt() + origin[1])
    }
}

/**
 * エッジの窓を縦の枠の座標で出し入れする窓口。呼び出し側は `WindowManager` の代わりにこれを使い、
 * 位置と大きさを縦の枠で渡す。画面が回っていないときは [WindowManager] へそのまま渡す。
 */
internal class EdgeWindows(private val wm: () -> WindowManager, private val area: () -> Pair<Int, Int>) {
    private val roots = HashMap<View, PortraitFrame>()

    fun turns(): Int = EdgeOrientation.turns(wm())

    fun add(view: View, p: WindowManager.LayoutParams) {
        val turns = turns()
        if (turns == 0) { wm().addView(view, p); return }
        val root = PortraitFrame(view.context, turns, view)
        roots[view] = root
        try { wm().addView(root, screenParams(turns, p)) } catch (e: Exception) { roots.remove(view); root.removeView(view); throw e }
    }

    fun update(view: View, p: WindowManager.LayoutParams) {
        val root = roots[view]
        if (root == null) { wm().updateViewLayout(view, p); return }
        wm().updateViewLayout(root, screenParams(root.turns, p))
    }

    fun remove(view: View) {
        val root = roots.remove(view)
        if (root == null) { wm().removeView(view); return }
        try { wm().removeView(root) } finally { root.removeView(view) }
    }

    /** [view] の窓の根 (回していればその入れ物)。 */
    fun rootOf(view: View): View = roots[view] ?: view

    /** [view] の中の [local] (view の座標) を、画面の座標の四角に直す。 */
    fun rectOnScreen(view: View, local: Rect): Rect {
        val root = roots[view]
        if (root != null) return root.contentRectOnScreen(local)
        val location = IntArray(2).also(view::getLocationOnScreen)
        return Rect(local).apply { offset(location[0], location[1]) }
    }

    private fun screenParams(turns: Int, p: WindowManager.LayoutParams): WindowManager.LayoutParams {
        val (width, height) = area()
        return WindowManager.LayoutParams().also { EdgeOrientation.toScreen(turns, width, height, p, it) }
    }
}
