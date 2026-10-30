package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 読み上げ中の通知 (`z2-say`) の「止める」ボタンの受け口。話している途中の文も、
 * 溜まっている続きも捨てる ([Z2ApiBridge.stopSpeaking])。
 */
class SpeechStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        Z2ApiBridge.stopSpeaking(context)
    }

    companion object {
        const val ACTION_STOP = "com.zerotoship.z2term.SPEECH_STOP"
    }
}
