package com.zerotoship.z2term.gui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GUI タブの表示変換 (ズーム/パン)。[GuiInputView] (ジェスチャで更新) と
 * [GuiScreen] (Canvas 描画で参照) が共有する単一の真実。
 *
 * - [scale] = 1.0 が「画面にフィット」。それ以上で拡大 (リモート画面の一部を拡大表示)。
 * - [panX]/[panY] はフィット中央からの画面 px オフセット。
 * - 値の範囲クランプ (画面外へ飛ばさない) は寸法を知る [GuiInputView] 側で行い、
 *   ここには確定値を格納するだけ。
 * - 変更は [rev] のインクリメントで Compose 再描画を促す (framebuffer 更新が無くても
 *   ズーム/パンだけで描き直せるように)。
 *
 * 回転は「framebuffer はそのまま、新しい画面サイズへ再フィット + ズーム/パンで見る」
 * 方式 (固定+ズーム)。[GuiSession] が保持するのでタブ切替・端末回転でも値は保たれる。
 */
class GuiViewport {
    @Volatile var scale: Float = 1f
        private set
    @Volatile var panX: Float = 0f
        private set
    @Volatile var panY: Float = 0f
        private set

    private val _rev = MutableStateFlow(0)
    /** ズーム/パン変更の度にインクリメント。Compose 側はこれを collect して再描画する。 */
    val rev: StateFlow<Int> = _rev.asStateFlow()

    fun apply(scale: Float, panX: Float, panY: Float) {
        this.scale = scale
        this.panX = panX
        this.panY = panY
        _rev.value = _rev.value + 1
    }

    /** 等倍 (フィット) に戻す。 */
    fun reset() = apply(1f, 0f, 0f)
}
