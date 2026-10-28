package com.zerotoship.z2term.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.settings.LocaleHelper
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * `z2-listen` — 話した内容を**端末の中だけで**文字にして返す (1.0.59)。
 *
 * ⛔ **声をネットへ出さない**。使うのは [SpeechRecognizer.createOnDeviceSpeechRecognizer] だけで、
 * 普通の [SpeechRecognizer.createSpeechRecognizer] (端末によってはサーバーで認識する) へは落とさない。
 * 「なるべく端末内で」の指定 (`EXTRA_PREFER_OFFLINE`) はお願いでしかなく保証にならないので、
 * 端末内を指定できない Android 11 以前は「使えない」と返す。認識エンジンも同梱しない (依存なし)。
 *
 * ⚠ **z2term が画面に出ている間だけ**聞ける。Android 11 以降、裏にいるアプリにはマイクが
 * 渡らない。裏で聞くにはマイク用の常駐サービスが要り、通知とマイク表示が出っぱなしになるので作らない。
 *
 * 入口は 2 つ: 端末の `z2-listen` ([start]) と、キーバーの 🎤 ([listen]・[VoiceInput])。
 *
 * `z2-listen` への応答は常に OK で、本文の先頭の語が結果: `0 <文字>` / `1 <理由>` (無音・取消・一致なし) /
 * `2 <理由>` (使えない)。z2api の ERR は終了コード 1 にしかならず、
 * 「聞き取れなかった」と「この端末では使えない」を分けられないため。
 */
internal object SpeechListen {
    /** 許可を尋ねる間も含めた上限。端末側 (`z2-listen`) の待ちはこれより少し長い。 */
    private const val LIMIT_MS = 90_000L
    /** 候補をいくつ受け取るか。決まった言葉を待つとき、1 位以外も見るため ([ListenWords.match])。 */
    private const val MAX_RESULTS = 5

    private val main = Handler(Looper.getMainLooper())

    /** 聞き取りの最中か。マイクは 1 つなので同時に 1 本だけ。キーバーの 🎤 はこれで光る。 */
    private val listeningState = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningState

    /** 聞き取りの結果。 */
    sealed interface Heard {
        /** 認識の候補 (よさそうな順・1 つ以上)。 */
        data class Text(val candidates: List<String>) : Heard
        /** 聞けなかった。[code] は `z2-listen` の終了コード (1 = 無音・取消 / 2 = 使えない)。 */
        data class Failed(val code: Int, val message: String) : Heard
    }

    /**
     * `z2-listen` の入口。
     * @param args 0=待つ言葉 (カンマ区切り・空なら何でも), 1=言語タグ (空なら端末の言語)
     * @param reply 応答の本文を受け取る。1 回だけ呼ぶ (Main スレッドから)。
     */
    fun start(context: Context, args: List<String>, reply: (String) -> Unit) {
        val words = args.getOrNull(0).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        listen(context, args.getOrNull(1).orEmpty().trim(), bias = words) { heard ->
            reply(
                when (heard) {
                    is Heard.Failed -> "${heard.code} ${heard.message}"
                    is Heard.Text -> {
                        val text = if (words.isEmpty()) heard.candidates[0] else ListenWords.match(words, heard.candidates)
                        if (text != null) "0 $text" else "1 ${msg(context).listenNoMatch(heard.candidates[0])}"
                    }
                }
            )
        }
    }

    /**
     * 1 回聞いて [onHeard] へ返す (Main スレッドから 1 回だけ)。
     * @param lang 言語タグ (空なら端末の言語)
     * @param bias 聞き取りを寄せる言葉 (Android 13〜。効き方は認識エンジン次第)
     */
    fun listen(context: Context, lang: String, bias: List<String>, onHeard: (Heard) -> Unit) {
        // SpeechRecognizer は Main で作り、コールバックも Main で受ける。
        main.post { begin(context.applicationContext, lang, bias, onHeard) }
    }

    private fun msg(context: Context) = Z2ApiMsg(lang = LocaleHelper.language(context), d = "$")

    private fun begin(context: Context, lang: String, bias: List<String>, onHeard: (Heard) -> Unit) {
        val m = msg(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { onHeard(Heard.Failed(2, m.listenNeedsAndroid12)); return }
        // マイクは 1 つ。2 本目は待たせずに断る (後ろで勝手に聞き始めると、誰の声か分からなくなる)。
        if (!listeningState.compareAndSet(false, true)) { onHeard(Heard.Failed(2, m.listenBusy)); return }
        Request(context, m, lang, bias, onHeard).run()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class Request(
        private val context: Context,
        private val m: Z2ApiMsg,
        private val lang: String,
        private val bias: List<String>,
        private val onHeard: (Heard) -> Unit,
    ) : RecognitionListener {
        private var recognizer: SpeechRecognizer? = null
        private var done = false
        private val timeout = Runnable { fail(1, m.listenTimedOut) }

        fun run() {
            main.postDelayed(timeout, LIMIT_MS)
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return fail(2, m.listenNoOnDevice)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) return listen()
            // 初回だけ。前面に居れば Android の許可の画面を出し、許されたらそのまま聞く。
            val asked = MainActivity.requestMicPermission { granted ->
                if (!done) { if (granted) listen() else fail(2, m.listenNoPermission) }
            }
            if (!asked) fail(2, m.listenNoPermission)
        }

        private fun listen() {
            if (!MainActivity.isShown()) return fail(2, m.listenNotShown)
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                // ⚠ 途中経過も受け取る。付けた版 (1.0.60) では端末内の認識が候補を 4 つ返し
                // (「開業, 改行, 会場, 開錠」)、-w が 2 位以下の「改行」に当たった。付けていなかった
                // 1.0.59 では 3 回とも外れていた (1 位は 開業 / 海洋 / 海峡。候補の数は記録していない)。
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (lang.isNotEmpty()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            // 待つ言葉へ聞き取りを寄せる (Android 13〜)。効き方は認識エンジン次第なので、
            // 判定は [ListenWords.match] が候補全体を見て行う (これが無くても外さないための本体)。
            if (bias.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias))
            }
            r.startListening(intent)
        }

        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                .filter { it.isNotBlank() }
            if (heard.isEmpty()) return fail(1, m.listenNoSpeech)
            finish(Heard.Text(heard))
        }

        override fun onError(error: Int) = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> fail(1, m.listenNoSpeech)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(2, m.listenNoPermission)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> fail(2, m.listenNoLanguage)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> fail(2, m.listenBusy)
            // マイクを取れなかった (z2term が裏に回った・別のアプリが録音中)。
            SpeechRecognizer.ERROR_AUDIO -> fail(2, m.listenNoMic)
            else -> fail(2, m.listenFailed(error))
        }

        private fun fail(code: Int, message: String) = finish(Heard.Failed(code, message))

        /** 結果を 1 回だけ返し、マイクを放す。 */
        private fun finish(heard: Heard) {
            if (done) return
            done = true
            main.removeCallbacks(timeout)
            recognizer?.let { r ->
                runCatching { r.cancel() }
                runCatching { r.destroy() }
            }
            recognizer = null
            listeningState.value = false
            onHeard(heard)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

/** `z2-listen -w` とキーバーの 🎤 の判定。Android に触らない純ロジック (ユニットテストで固定する)。 */
internal object ListenWords {

    /**
     * 候補 ([heard]、よさそうな順) から待つ言葉 ([words]) を探し、当たった言葉を書かれたとおりに返す。
     *
     * ⚠ **1 位だけで決めない**。同じ読みの言葉 (改行 / 開業) は 1 位に別の漢字が来ることがあり、
     * 待つ言葉は 2 位以下に居る (実機の聞き取り試験で「改行」が「開業」になった)。
     * 1. どれかの候補と丸ごと一致する言葉 (候補の順に見る)
     * 2. 無ければ、候補に含まれている言葉のうち最も長いもの (「次の曲お願い」→ 次の曲)
     */
    fun match(words: List<String>, heard: List<String>): String? {
        exact(words, heard)?.let { return it }
        val keys = words.map { it to norm(it) }.filter { it.second.isNotEmpty() }
        val said = heard.map(::norm)
        return keys.filter { k -> said.any { it.contains(k.second) } }.maxByOrNull { it.second.length }?.first
    }

    /**
     * どれかの候補と**丸ごと**一致する言葉 (候補の順に見る)。含まれているだけでは当てない —
     * キーバーの 🎤 の操作語 ([com.zerotoship.z2term.ui.terminal.VoiceInput]) は、
     * 「ファイルを送信して」のような文の中の「送信」で Enter を押してはいけないため。
     */
    fun exact(words: List<String>, heard: List<String>): String? {
        val keys = words.map { it to norm(it) }.filter { it.second.isNotEmpty() }
        for (s in heard.map(::norm)) keys.firstOrNull { it.second == s }?.let { return it.first }
        return null
    }

    /** 全角/半角・大文字/小文字・空白・句読点の違いでは外さない。 */
    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
            .filterNot { it.isWhitespace() || it in "。、.,!?・" }
}
