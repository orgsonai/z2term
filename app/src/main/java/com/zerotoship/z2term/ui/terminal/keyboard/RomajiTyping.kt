package com.zerotoship.z2term.ui.terminal.keyboard

import com.zerotoship.z2term.ime.RomajiKana

/**
 * ローマ字面 ([KeyboardFace.ROMAJI]) の 1 打鍵を、変換中の文字列への編集に直す (1.0.3)。
 *
 * ⭐ **打ちかけのローマ字は変換中の文字列にそのまま置く** (`k` と打てば下線つきの `k` が
 * 見える)。別の変数には持たない — 持つと ⌫・カーソル移動・候補のタップのたびに文字列と
 * ずれる。打ちかけがどこからかは、毎回カーソルの手前から読み直す
 * ([RomajiKana.pendingSuffix])。
 */
object RomajiTyping {

    /** カーソルの手前 [delete] 文字を消して [insert] を入れる。 */
    data class Edit(val delete: Int, val insert: String) {
        /** まだかなに育つ打ちかけを含むか。含むなら変換中に置かないと次の打鍵とつながらない。 */
        val hasPending: Boolean get() = RomajiKana.pendingSuffix(insert).isNotEmpty()

        /** かなを含むか。含まないもの (数字・記号だけ) は、変換中でなければそのまま送る。 */
        val hasKana: Boolean get() = insert.any(::isKana)
    }

    /** [typed] を打ったときの編集。[beforeCursor] は変換中の文字列のカーソルより前。 */
    fun type(beforeCursor: String, typed: Char): Edit {
        val pending = RomajiKana.pendingSuffix(beforeCursor)
        val result = RomajiKana.feed(pending, typed)
        return Edit(delete = pending.length, insert = result.kana + result.pending)
    }

    /**
     * 変換・確定の直前に、宙に浮いた打ちかけをかなへ落とす (`かn` → かん)。変えるものが
     * 無ければ null。⚠ 落とせない打ちかけ (`ky` など) は打った字のまま残す。
     */
    fun flush(beforeCursor: String): Edit? {
        val pending = RomajiKana.pendingSuffix(beforeCursor)
        val flushed = RomajiKana.flush(pending)
        return if (flushed == pending) null else Edit(delete = pending.length, insert = flushed)
    }

    fun isKana(ch: Char): Boolean = ch in '぀'..'ゟ' || ch == 'ー'
}
