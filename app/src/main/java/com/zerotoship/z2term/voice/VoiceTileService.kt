package com.zerotoship.z2term.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log

/**
 * クイック設定タイル「声で操作」(1.0.64)。押すと [VoiceCommandActivity] を開いて聞く。
 *
 * `z2-tile` の 12 枠 ([com.zerotoship.z2term.tile.Z2TileService]) とは別の 1 枚にした。枠はコマンドを
 * **裏で**走らせるもので、裏ではマイクが使えない (画面を出す必要がある) ため。
 *
 * ⚠ ロック中は [unlockAndRun] で解除を求める ([VoiceCommandActivity] の理由と同じ)。
 */
class VoiceTileService : TileService() {

    override fun onClick() {
        unlockAndRun {
            val intent = Intent(this, VoiceCommandActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            // パネルを畳んで開く。Android 14 からは PendingIntent を渡す形だけが残っている。
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(
                        PendingIntent.getActivity(
                            this, 0, intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                } else {
                    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(intent)
                }
            }.onFailure { Log.w("VoiceTileService", "open failed: ${it.message}") }
        }
    }
}
