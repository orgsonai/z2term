package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 連打 (キーリピート) のタイミング既定値。 */
internal const val KEY_REPEAT_INITIAL_MS = 400L
internal const val KEY_REPEAT_INTERVAL_MS = 55L

/**
 * タップ + 長押し連打を検出する共通ジェスチャ。
 *
 *  - 短いタップ (連打が始まる前に離す) → onTap() 1 回
 *  - 押しっぱなし → 初回ディレイ後に onTap() を一定間隔で連打
 *
 * フリックを併用するキー (英字/かな) では使わず、FlickKey 側で独自に統合する。
 * 数字・矢印・space・⌫ などフリックの無いキー用。
 */
internal suspend fun PointerInputScope.detectTapWithRepeat(
    scope: CoroutineScope,
    onPressedChange: (Boolean) -> Unit = {},
    onTap: () -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressedChange(true)
            var repeated = false
            val repeatJob = scope.launch {
                delay(KEY_REPEAT_INITIAL_MS)
                repeated = true
                while (isActive) {
                    onTap()
                    delay(KEY_REPEAT_INTERVAL_MS)
                }
            }
            // 指が離れるまで待つ
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
            }
            repeatJob.cancel()
            onPressedChange(false)
            if (!repeated) onTap()
        }
    }
}
