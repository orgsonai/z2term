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
                    else -> onText(typed(heard.candidates[0]))
                }
            }
        }
    }

    /** 英字の語 (全角も)。数字を含んでよい (`MP3`)。 */
    private val LATIN_WORD = Regex("[A-Za-zＡ-Ｚａ-ｚ][A-Za-z0-9Ａ-Ｚａ-ｚ０-９]*")

    /**
     * 打ち込む前の整え (1.0.63)。**全部大文字の英字の語だけ**を半角の小文字にする。
     *
     * 声には大文字・小文字の区別が無く、日本語の認識は文字を 1 つずつ読んだ英字 (「エル エス」) を
     * 大文字で書く (`LS`)。端末のコマンドはほぼ小文字なので、そのままでは `ls` として動かない
     * (実機で確認)。大小が混ざった語 (`Git`) は認識が意図して書いたものなのでそのまま。
     * 引き換えに `README` `PATH` のような大文字で書きたい語も小文字になる (利用者が選んだ決まり)。
     * `z2-listen` の出力は変えない (スクリプトが自分で決める)。
     */
    internal fun typed(text: String): String = LATIN_WORD.replace(text) { m ->
        val word = m.value
        if (word.any { it.isLowerCase() }) word
        else java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFKC).lowercase()
    }
}
