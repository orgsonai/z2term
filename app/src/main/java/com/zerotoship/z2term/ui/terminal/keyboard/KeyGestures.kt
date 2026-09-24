package com.zerotoship.z2term.ui.terminal.keyboard

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 補助バーでは、割り当て済みフリックを優先し、未割り当て方向のドラッグはスクロールへ渡す。 */
internal suspend fun PointerInputScope.detectAccessoryKeyGestures(
    scope: CoroutineScope,
    key: KeyDef,
    onPressedChange: (Boolean) -> Unit,
    onFlickChange: (KeyGesture?) -> Unit,
    onGesture: (KeyGesture) -> Unit,
) {
    var pendingTap: Job? = null
    var hold: Job? = null
    var lastUp = 0L
    val hasDouble = key.actionsFor(KeyGesture.DOUBLE_TAP).isNotEmpty()
    val hasLong = key.actionsFor(KeyGesture.LONG_PRESS).isNotEmpty()
    val doubleTimeout = viewConfiguration.doubleTapTimeoutMillis
    val longTimeout = viewConfiguration.longPressTimeoutMillis
    val slop = viewConfiguration.touchSlop
    try {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
                val secondTap = pendingTap?.isActive == true && down.uptimeMillis - lastUp <= doubleTimeout
                pendingTap?.cancel()
                onPressedChange(true)
                onFlickChange(null)
                var flick: KeyGesture? = null
                var fired = false
                var cancelled = false
                if (hasLong || key.repeatable) {
                    hold = scope.launch {
                        delay(if (hasLong) longTimeout else key.repeatInitialMs)
                        fired = true
                        if (hasLong) onGesture(KeyGesture.LONG_PRESS)
                        else while (isActive) {
                            onGesture(KeyGesture.TAP)
                            delay(key.repeatIntervalMs)
                        }
                    }
                }
                while (true) {
                    // Initial で方向を決め、フリックは親の横スクロールが始まる前に消費する。
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (change.isConsumed) cancelled = true
                    if (!cancelled && !fired && maxOf(abs(dx), abs(dy)) > slop) {
                        hold?.cancel()
                        val direction = if (abs(dx) > abs(dy)) {
                            if (dx < 0) KeyGesture.LEFT else KeyGesture.RIGHT
                        } else if (dy < 0) KeyGesture.UP else KeyGesture.DOWN
                        if (key.actionsFor(direction).isNotEmpty()) {
                            flick = direction
                            onFlickChange(direction)
                            change.consume()
                            if (!key.flickOnRelease) {
                                fired = true
                                onGesture(direction)
                            }
                        } else if (flick == null) cancelled = true
                    }
                    if (cancelled) {
                        hold?.cancel()
                        onPressedChange(false)
                    } else if (flick != null || fired) change.consume()
                    if (!change.pressed) {
                        hold?.cancel()
                        if (!cancelled && !fired) {
                            when {
                                flick != null -> onGesture(flick)
                                secondTap -> onGesture(KeyGesture.DOUBLE_TAP)
                                hasDouble -> {
                                    lastUp = change.uptimeMillis
                                    pendingTap = scope.launch { delay(doubleTimeout); onGesture(KeyGesture.TAP) }
                                }
                                else -> onGesture(KeyGesture.TAP)
                            }
                        }
                        break
                    }
                }
                hold?.cancel()
                onPressedChange(false)
                onFlickChange(null)
            }
        }
    } finally {
        hold?.cancel()
        pendingTap?.cancel()
        onPressedChange(false)
        onFlickChange(null)
    }
}

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
