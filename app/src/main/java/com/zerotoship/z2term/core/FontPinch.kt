package com.zerotoship.z2term.core

import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.workspace.Broadcast

/** ピンチ開始時の大きさ [startSp] に倍率 [ratio] を掛ける。0.5sp 刻みで、上限と下限で止まる。 */
internal fun scaledFontSize(startSp: Float, ratio: Float): Float {
    val raw = (startSp * ratio).coerceIn(AppSettings.MIN_FONT_SIZE_SP, AppSettings.MAX_FONT_SIZE_SP)
    return Math.round(raw * 2f) / 2f
}

/**
 * 1 回のピンチで文字サイズを変えるタブを、指を置いた時点で決めて覚える。
 *
 * - ブロードキャスト中: 入力と同じ送り先 ([Broadcast.targets]) だけを変える。チェックを外したタブは変わらない。
 * - それ以外: 今までどおり全タブ。タブごとの大きさを持つタブも同じ倍率で変える。
 *
 * どのタブも自分の開始時の大きさからの相対で変わるので、大きさの差は残る。上限か下限まで動かせば揃う。
 */
internal class FontPinch(private val from: TerminalSession) {
    private val broadcasting = Broadcast.mode
    private val baseStartSp = from.baseFontSizeSp
    private var lastBaseSp = baseStartSp
    private val tabs: Map<TerminalSession, Float> =
        if (broadcasting) Broadcast.targets(from).associateWith { it.currentFontSizeSp }
        else SessionManager.sessions.value.filterIsInstance<TerminalSession>()
            .filter { it.hasTabFontSize }
            .associateWith { it.currentFontSizeSp }

    fun scale(ratio: Float) {
        tabs.forEach { (tab, startSp) -> tab.setTabFontSize(scaledFontSize(startSp, ratio)) }
        if (broadcasting) return
        val baseSp = scaledFontSize(baseStartSp, ratio)
        if (baseSp != lastBaseSp) {
            lastBaseSp = baseSp
            from.setBaseFontSize(baseSp)
        }
    }
}
