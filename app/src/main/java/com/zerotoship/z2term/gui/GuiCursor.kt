package com.zerotoship.z2term.gui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GUI タブの仮想カーソル。
 *
 * [GuiInputView] が位置を更新し、[GuiScreen] が同じ状態を描画する単一の真実。
 * View ではなく [GuiSession] が保持するため、タブ切替や RFB 再接続で入力 View が
 * 作り直されても位置を失わない。
 *
 * [Visual] はカーソルの位置と形を分離するための口。既定は自前の矢印だが、RFB の
 * RichCursor / XCursor を受信する実装ではこの値をサーバ由来の形へ差し替える。
 */
class GuiCursor {
    /** RELATIVE=トラックパッド式（既定）、ABSOLUTE=触れた位置へ直接移動。 */
    enum class Mode { RELATIVE, ABSOLUTE }

    sealed interface Visual {
        data object Arrow : Visual
    }

    data class Snapshot(
        val initialized: Boolean,
        val x: Float,
        val y: Float,
        val pressed: Boolean,
        val mode: Mode,
        val visual: Visual,
    )

    private var initialized = false
    private var x = 0f
    private var y = 0f
    private var pressed = false
    private var mode = Mode.RELATIVE
    private var visual: Visual = Visual.Arrow

    private val _rev = MutableStateFlow(0)
    /** 位置・押下状態・形の変更ごとに増え、Compose のカーソル再描画を促す。 */
    val rev: StateFlow<Int> = _rev.asStateFlow()

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(initialized, x, y, pressed, mode, visual)

    /**
     * framebuffer 寸法に合わせて初期化またはクランプし、現在位置を返す。
     * 初回だけ中央へ置き、以後は寸法が変わっても可能な限り以前の位置を保つ。
     */
    @Synchronized
    fun fitTo(width: Int, height: Int): Snapshot? {
        if (width <= 0 || height <= 0) return null
        val oldInitialized = initialized
        val oldX = x
        val oldY = y
        if (!initialized) {
            x = width / 2f
            y = height / 2f
            initialized = true
        } else {
            x = x.coerceIn(0f, (width - 1).toFloat())
            y = y.coerceIn(0f, (height - 1).toFloat())
        }
        if (initialized != oldInitialized || x != oldX || y != oldY) bump()
        return Snapshot(initialized, x, y, pressed, mode, visual)
    }

    /** framebuffer 座標で相対移動し、範囲内へクランプする。 */
    @Synchronized
    fun moveBy(dx: Float, dy: Float, width: Int, height: Int): Snapshot? {
        fitTo(width, height) ?: return null
        val oldX = x
        val oldY = y
        x = (x + dx).coerceIn(0f, (width - 1).toFloat())
        y = (y + dy).coerceIn(0f, (height - 1).toFloat())
        if (x != oldX || y != oldY) bump()
        return Snapshot(initialized, x, y, pressed, mode, visual)
    }

    /** framebuffer の絶対座標へ移動する。絶対座標モードのタッチ入力で使う。 */
    @Synchronized
    fun moveTo(x: Float, y: Float, width: Int, height: Int): Snapshot? {
        fitTo(width, height) ?: return null
        val oldX = this.x
        val oldY = this.y
        this.x = x.coerceIn(0f, (width - 1).toFloat())
        this.y = y.coerceIn(0f, (height - 1).toFloat())
        if (this.x != oldX || this.y != oldY) bump()
        return Snapshot(initialized, this.x, this.y, pressed, mode, visual)
    }

    /** 左ボタン保持中の見た目を描画側へ共有する。 */
    @Synchronized
    fun setPressed(value: Boolean) {
        if (pressed == value) return
        pressed = value
        bump()
    }

    @Synchronized
    fun toggleMode(): Mode {
        mode = if (mode == Mode.RELATIVE) Mode.ABSOLUTE else Mode.RELATIVE
        bump()
        return mode
    }

    @Synchronized
    fun setVisual(value: Visual) {
        if (visual == value) return
        visual = value
        bump()
    }

    private fun bump() {
        _rev.value = _rev.value + 1
    }
}
