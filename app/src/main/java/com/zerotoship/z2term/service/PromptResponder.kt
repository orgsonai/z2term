package com.zerotoship.z2term.service

import com.zerotoship.z2term.settings.PromptAnswer

/**
 * 画面なしの実行で、コマンドが聞いてきたことへ決めておいた返事を送る (1.0.12)。
 *
 * [onOutput] は出力を読むスレッドから、[tick] は一定間隔の見張りから呼ぶ。端末そのものには
 * 触らない ([echoOff] / [send] / [note] / [abort] を受け取る) ので、端末なしで試験できる。
 *
 * 送る条件:
 *  - 隠さない返事 … 最後の行が文言で終わったまま、出力が [QUIET_TICKS] 回ぶん止まったとき。
 *    同じ質問には来るたびに答える (項目ごとに確認するコマンドがある)。
 *  - 隠す返事 … 上に加えて、端末が打った文字を表示しない状態のとき。**1 回の実行で 1 回だけ**。
 *    もう一度聞かれたら受け付けられなかったということなので、送り直さずに止める
 *    (間違った内容を繰り返し送らない。待ち続けて実行が終わらなくなるのも防ぐ)。
 */
internal class PromptResponder(
    private val answers: List<PromptAnswer>,
    private val echoOff: () -> Boolean,
    private val send: (String) -> Unit,
    private val note: (String) -> Unit,
    private val abort: () -> Unit,
) {
    private val tail = PromptTail()
    private var pending: PromptAnswer? = null
    private var waited = 0
    private val sentSecrets = HashSet<String>()
    private var sentCount = 0

    @Synchronized
    fun onOutput(buf: ByteArray, n: Int) {
        tail.append(buf, n)
        val match = PromptTail.match(tail.lastLine(), answers)
        waited = 0
        if (match != null && match.secret && match.prompt in sentSecrets) {
            pending = null
            note("(「${match.prompt}」をもう一度聞かれました。保存した内容が受け付けられなかったようなので止めます)")
            abort()
            return
        }
        pending = match
    }

    @Synchronized
    fun tick() {
        val answer = pending ?: return
        waited++
        if (answer.secret) {
            if (echoOff()) {
                pending = null
                sentSecrets += answer.prompt
                send(answer.text + ENTER)
                note("(「${answer.prompt}」に保存した内容を送りました)")
            } else if (waited >= SECRET_WAIT_TICKS) {
                pending = null
                note("(「${answer.prompt}」は入力を隠す状態にならなかったので送っていません)")
            }
            return
        }
        if (waited < QUIET_TICKS) return
        pending = null
        if (sentCount >= MAX_ANSWERS) {
            note("(同じ実行で $MAX_ANSWERS 回答えても終わらないので止めます)")
            abort()
            return
        }
        sentCount++
        send(answer.text + ENTER)
    }

    companion object {
        /** [tick] の間隔 (ミリ秒)。 */
        const val TICK_MS = 100L
        /** 出力が止まってから答えるまでの回数。行の途中で送らないための間。 */
        const val QUIET_TICKS = 2
        /** 隠す返事で、端末が隠す状態になるのを待つ回数 (5 秒)。 */
        const val SECRET_WAIT_TICKS = 50
        /** 1 回の実行で答える上限。答えても同じ質問が返り続けるコマンドを止める。 */
        const val MAX_ANSWERS = 50
        /** 端末の Enter キーが送るもの。 */
        const val ENTER = "\r"
    }
}

/** 出力の末尾だけを持ち、最後の行を取り出す。 */
internal class PromptTail(private val limit: Int = 1024) {
    private var bytes = ByteArray(0)

    fun append(buf: ByteArray, n: Int) {
        val joined = bytes + buf.copyOfRange(0, n)
        bytes = if (joined.size > limit) joined.copyOfRange(joined.size - limit, joined.size) else joined
    }

    /** 最後の改行より後ろ。色などの制御列と CR は落とす。 */
    fun lastLine(): String =
        String(bytes, Charsets.UTF_8).replace(ESCAPES, "").substringAfterLast('\n').replace("\r", "")

    companion object {
        private val ESCAPES = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")

        /** [line] が文言で終わる返事。複数合えば文言の長いもの (より具体的なもの)。大小文字は区別しない。 */
        fun match(line: String, answers: List<PromptAnswer>): PromptAnswer? {
            val end = line.trimEnd()
            if (end.isEmpty()) return null
            return answers
                .filter { it.prompt.isNotBlank() && end.endsWith(it.prompt.trim(), ignoreCase = true) }
                .maxByOrNull { it.prompt.trim().length }
        }
    }
}
