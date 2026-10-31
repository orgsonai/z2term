package com.zerotoship.z2term.voice

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.EventEmitter
import com.zerotoship.z2term.service.SpeechListen
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.service.WhenTriggerMatch
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * 「声で操作」(1.0.64)。クイック設定タイル ([VoiceTileService]) と、端末の「デジタル アシスタント」
 * (`ACTION_ASSIST`・電源ボタン長押しなど) から開く**小さな聞き取りの画面**。
 *
 * 聞いた内容は `events.jsonl` へ `voice` イベントとして書き (`action` = 1 位の候補、
 * `name` = 候補全部を `|` でつないだもの)、`z2-when event:voice` のルールで受ける。
 * **何をするかはアプリに持たない** — 曲送り・タイマー・電話・AI への質問はマクロの仕事
 * (`z2-when` と同じ分担。アプリは「聞いて合図を出す」まで)。
 *
 * ⚠ **画面を出すのは、マイクが画面に出ているアプリにしか渡らないため** (Android 11〜)。
 * 裏で聞くにはマイク用の常駐サービスが要り、通知とマイク表示が出っぱなしになるので作らない。
 * **ロック中も開く** (1.0.74・`showWhenLocked`)。ただしロック中に聞いた内容は別のイベント
 * [EVENT_LOCKED] (`voice_locked`) にする。自分で作った `event:voice` のルールが、拾った人の声で
 * 動かないように — ロック中に何を許すかは、`voice_locked` を受けると決めたルール (同梱の voice.sh は
 * 曲・音量・時刻・天気などだけ) が決める。アプリは何も許さない・何も実行しない、は変わらない。
 */
/** 聞き取りの画面の状態。 */
private sealed interface VoiceUi {
    data object Listening : VoiceUi
    /** 聞いた言葉。[hasRule] が false なら、受けるルールが無いことも伝える。 */
    data class Heard(val text: String, val hasRule: Boolean, val locked: Boolean = false) : VoiceUi
    data class Failed(val message: String) : VoiceUi
}

class VoiceCommandActivity : ComponentActivity() {

    private var ui by mutableStateOf<VoiceUi>(VoiceUi.Listening)
    private var started = false
    /** この画面がいま聞いているか。閉じるとき、聞いている最中のものだけを取り消す。 */
    private var listening = false
    private val main = Handler(Looper.getMainLooper())

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else show(VoiceUi.Failed(getString(R.string.voice_no_mic)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setContent {
            VoicePanel(
                ui = ui,
                onCancel = { finish() },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        visible.incrementAndGet()
    }

    override fun onResume() {
        super.onResume()
        // ⚠ 聞き始めるのは**画面に出てから**。ロック中に呼ばれると、解除されるまでここへ来ない。
        if (started) return
        started = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onStop() {
        super.onStop()
        visible.decrementAndGet()
        // 画面から外れたら終わり (戻ってきても、さっきの続きは聞かない)。
        // ⚠ 取り消すのは自分が聞いている最中だけ。マクロが続きを聞くために開いた次の画面
        // (「ナビ」→ 行き先) が前に出ると、聞き終えたこの画面が止まる。ここで取り消すと、
        // 次の画面が聞き始めたばかりのものまで止めてしまう (マイクは 1 つで、取り消しは共通)。
        if (!isChangingConfigurations) {
            if (listening) SpeechListen.cancel()
            finish()
        }
    }

    private fun listen() {
        listening = true
        SpeechListen.listen(this, lang = "", bias = emptyList()) { heard ->
            listening = false
            when (heard) {
                is SpeechListen.Heard.Failed ->
                    if (heard.message.isNotEmpty()) show(VoiceUi.Failed(heard.message)) else finish()
                is SpeechListen.Heard.Text -> {
                    val app = applicationContext
                    val text = heard.candidates[0]
                    // 聞き終えた時点で判断する (聞いている間に解除されたら、ふつうの voice)。
                    val event = if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) EVENT_LOCKED else EVENT
                    // 記録とルールの読み込みはファイル I/O なので Main から外す。
                    Thread {
                        EventEmitter.emit(app, event = event, name = heard.candidates.joinToString("|"), action = text)
                        val hasRule = hasRule(app, event)
                        main.post { show(VoiceUi.Heard(text, hasRule, locked = event == EVENT_LOCKED)) }
                    }.apply { isDaemon = true; name = "voice-event" }.start()
                }
            }
        }
    }

    /** 結果を少しだけ見せて閉じる (何が聞こえたかが分からないと、外れたときに直しようがない)。 */
    private fun show(next: VoiceUi) {
        if (isFinishing) return
        ui = next
        val keep = if (next is VoiceUi.Heard && next.hasRule) HEARD_MS else MESSAGE_MS
        main.postDelayed({ finish() }, keep)
    }

    companion object {
        const val EVENT = "voice"
        /** ロック中に聞いた内容。[EVENT] のルールには届かない (受けるルールを別に登録する)。 */
        const val EVENT_LOCKED = "voice_locked"
        private const val HEARD_MS = 1_500L
        private const val MESSAGE_MS = 4_000L

        /**
         * 画面に出ているか。[SpeechListen] がマイクを使ってよいかの判断に使う。
         * 出ている数で数える — 次の画面が出てから前の画面が止まるので、真偽 1 つだと前の画面が消してしまう。
         */
        private val visible = java.util.concurrent.atomic.AtomicInteger()
        val shown: Boolean get() = visible.get() > 0

        private fun hasRule(context: Context, event: String): Boolean = runCatching {
            WhenManager.loadRules(context).any {
                it.enabled && it.kind == "event" && WhenTriggerMatch.event(it.spec, event)
            }
        }.getOrDefault(true)
    }
}

@androidx.compose.runtime.Composable
private fun VoicePanel(ui: VoiceUi, onCancel: () -> Unit) {
    val listening = ui is VoiceUi.Listening
    val title = when (ui) {
        VoiceUi.Listening -> stringResource(R.string.voice_listening)
        is VoiceUi.Heard -> stringResource(R.string.voice_heard, ui.text)
        is VoiceUi.Failed -> ui.message
    }
    val body = if (ui is VoiceUi.Heard && !ui.hasRule)
        stringResource(if (ui.locked) R.string.voice_no_locked_rule else R.string.voice_no_rule) else null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .background(ZtsBgCard, RoundedCornerShape(8.dp))
                .border(1.dp, if (listening) ZtsGreen else ZtsBorder, RoundedCornerShape(8.dp))
                // パネルの中を押しても閉じない (外側だけが「やめる」)。
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = ZtsTextPrimary, fontSize = 17.sp)
            if (body != null) Text(body, color = ZtsTextSecondary, fontSize = 13.sp)
            if (listening) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.voice_cancel), color = ZtsGreen)
                }
            }
        }
    }
}
