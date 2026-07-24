package com.zerotoship.z2term.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * ウィジェットのボタンを押したときの手応え。
 *
 * **なぜ要るか**: ホーム画面のウィジェットには押下エフェクトが無く、⟳ のように「読み直すだけで
 * 見た目がほとんど変わらない」ボタンは**押せたのかどうか分からない**（実機フィードバック 2026-07-24）。
 * 短い振動を返すことで、結果が変わらなくても「受け付けた」ことは必ず伝わる。
 *
 * `Z2ApiBridge` にも同じ趣旨の振動処理があるが、あちらは `z2-vibrate` 用の private 実装で
 * 長さもユーザー指定。ここは固定長の「コッ」だけなので、共有せず小さく持つ。
 */
internal object WidgetFeedback {

    /** ボタンを押した合図の長さ。長いと不快なので短く。 */
    private const val TICK_MS = 20L

    /** 短く 1 回振動させる。振動できない端末・設定では何もしない。 */
    fun tick(context: Context) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /**
     * 「更新中」を見せておく最低時間。
     *
     * 読み直しはたいてい数十ミリ秒で終わるので、そのまま描き替えると**表示が変わったことに
     * 気付けない**。押した合図として一瞬だけ残す。
     */
    const val BUSY_HOLD_MS = 250L
}
