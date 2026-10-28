package com.zerotoship.z2term.ui.terminal

import android.content.Context
import android.widget.Toast
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.service.ListenWords
import com.zerotoship.z2term.service.SpeechListen
import com.zerotoship.z2term.settings.LocaleHelper

/**
 * キーバーの 🎤 (`voice_input`・1.0.61)。話した内容を、押したタブへ**打ち込むだけ**にする。
 *
 * ⚠ **Enter は押さない**。AI へのプロンプトもコマンドも、打ち込まれた文字を見てから送る。
 * 送るのは、発話**全体**が操作語だったときだけ (「改行」「送信」→ Enter、「コントロールC」→ Ctrl+C)。
 * 文の中の操作語では動かさない ([ListenWords.exact])。同じ読みの別の漢字 (開業) でも、
 * 認識の候補のどれかが操作語と丸ごと一致すれば操作として扱う ([ListenWords] の理由と同じ)。
 *
 * 認識は `z2-listen` と同じ [SpeechListen] (端末の中だけ・z2term が画面に出ている間だけ)。
 */
internal object VoiceInput {
    /** 発話全体がこれなら Enter。 */
    private val ENTER_WORDS = listOf("改行", "送信", "エンター", "enter", "send", "new line")
    /** 発話全体がこれなら Ctrl+C。 */
    private val INTERRUPT_WORDS = listOf("コントロールC", "コントロールシー", "中断", "control c", "ctrl c")

    fun start(context: Context, onText: (String) -> Unit, onEnter: () -> Unit, onInterrupt: () -> Unit) {
        val m = Z2ApiMsg(lang = LocaleHelper.language(context), d = "$")
        // 聞いている間はキーが光る ([SpeechListen.listening])。話し始めの合図だけ短く出す。
        if (!SpeechListen.listening.value) Toast.makeText(context, m.listenPrompt, Toast.LENGTH_SHORT).show()
        SpeechListen.listen(context, lang = "", bias = emptyList()) { heard ->
            when (heard) {
                is SpeechListen.Heard.Failed -> Toast.makeText(context, heard.message, Toast.LENGTH_LONG).show()
                is SpeechListen.Heard.Text -> when {
                    ListenWords.exact(ENTER_WORDS, heard.candidates) != null -> onEnter()
                    ListenWords.exact(INTERRUPT_WORDS, heard.candidates) != null -> onInterrupt()
                    else -> onText(heard.candidates[0])
                }
            }
        }
    }
}
