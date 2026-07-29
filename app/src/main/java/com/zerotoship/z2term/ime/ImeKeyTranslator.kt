package com.zerotoship.z2term.ime

/**
 * 内蔵キーボードが出すバイト列を、**テキスト欄で意味のある動作**へ翻訳する
 * (Android 非依存・テスト用)。
 *
 * 内蔵キーボードは端末へ送るために作られていて、出てくるのは「端末が読むバイト列」
 * (`TerminalKeyboard` の `onBytes`)。入力メソッド ([Z2ImeService]) はそれを
 * `InputConnection` の操作へ読み替える必要がある。
 *
 * ⚠ **端末専用のものは捨てる**。ESC・Ctrl+文字 のような制御コードはテキスト欄に入れても
 * 文字化けした 1 文字が混ざるだけで、押した本人には何が起きたか分からない。⚠ ただし
 * **⌫ のフリック (Ctrl+W / Ctrl+U) だけは削除操作として活かす** — 端末で使い慣れた指の動きが
 * 入力欄でも同じように効かないと、同じキーボードに見えなくなる。
 */
internal sealed interface ImeKeyAction {
    /** そのまま文字を入れる。 */
    data class Insert(val text: String) : ImeKeyAction

    /** キャレット直前の 1 文字を消す (⌫)。 */
    data object DeleteBack : ImeKeyAction

    /** 直前の 1 単語を消す (⌫ の左フリック = Ctrl+W)。 */
    data object DeleteWordBack : ImeKeyAction

    /** 行頭まで消す (⌫ の右フリック = Ctrl+U)。 */
    data object DeleteToLineStart : ImeKeyAction

    /** 改行 / 確定 (⏎)。複数行の欄では改行、1 行の欄では次へ進む。 */
    data object Newline : ImeKeyAction

    /** タブ (次の入力欄へ移る)。 */
    data object Tab : ImeKeyAction
}

internal object ImeKeyTranslator {

    private const val BS = '\u0008'        // ⌫ (端末は 0x7F を出すが、両方受ける)
    private const val TAB = '\u0009'
    private const val LF = '\u000A'
    private const val CR = '\u000D'        // ⏎
    private const val CTRL_U = '\u0015'    // ⌫ の右フリック (行頭まで削除)
    private const val CTRL_W = '\u0017'    // ⌫ の左フリック (単語削除)
    private const val ESC = '\u001B'
    private const val DEL = '\u007F'       // ⌫ のタップ

    /**
     * [bytes] を動作の並びへ変換する。
     *
     * ⚠ **バイトではなく文字単位で見る** — かなや絵文字は複数バイトなので、バイトのまま
     * 制御コードと突き合わせると多バイト文字の途中を制御コードと読み違える。
     * 連続する文字は 1 つの [ImeKeyAction.Insert] にまとめる (`InputConnection` の往復を減らす)。
     */
    fun translate(bytes: ByteArray): List<ImeKeyAction> {
        val out = mutableListOf<ImeKeyAction>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += ImeKeyAction.Insert(buf.toString())
                buf.clear()
            }
        }
        for (ch in String(bytes, Charsets.UTF_8)) {
            when (ch) {
                DEL, BS -> { flush(); out += ImeKeyAction.DeleteBack }
                CTRL_W -> { flush(); out += ImeKeyAction.DeleteWordBack }
                CTRL_U -> { flush(); out += ImeKeyAction.DeleteToLineStart }
                CR, LF -> { flush(); out += ImeKeyAction.Newline }
                TAB -> { flush(); out += ImeKeyAction.Tab }
                // ESC 単独 (ESC キー) も、ALT が付ける ESC 前置も、テキスト欄では捨てる。
                // 前置の場合は続く文字がそのまま入る = ALT を押し忘れたのと同じ結果になる。
                ESC -> flush()
                else -> if (ch.code >= 0x20) buf.append(ch) else flush()  // 残る制御コードは捨てる
            }
        }
        flush()
        return out
    }

    /**
     * [text] の末尾から「1 単語ぶん」の長さを返す (Ctrl+W)。
     *
     * 末尾の空白をまず飛ばし、そこから空白に当たるまでを 1 単語とする (`readline` の `unix-word-rubout`
     * と同じ数え方)。⚠ 何も消せないときは 0 を返す — 呼び手が 0 を渡して `deleteSurroundingText` を
     * 呼ぶと、機種によっては 1 文字消える実装があるため、0 のときは呼ばないで済むようにする。
     */
    fun wordBackLength(text: CharSequence): Int {
        var i = text.length
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return text.length - i
    }

    /**
     * [text] の末尾から「行頭まで」の長さを返す (Ctrl+U)。改行は消さない。
     */
    fun toLineStartLength(text: CharSequence): Int {
        var i = text.length
        while (i > 0 && text[i - 1] != '\n') i--
        return text.length - i
    }
}
