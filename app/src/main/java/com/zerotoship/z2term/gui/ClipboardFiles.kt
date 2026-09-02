package com.zerotoship.z2term.gui

/**
 * クリップボード経由のファイル受け渡しで、プロトコル実装と Android 側を分ける境界。
 *
 * ⚠ **どちらの向きも「1 件ずつ・要求された分だけ」** に見える形にしてある。ファイル全体を
 * メモリに載せると、相手が数百 MB をコピーしただけで落ちる。
 */
object ClipboardFiles {
    /** 送受信の 1 件。`size` が分からない相手のために負値も許す (その場合は最後まで読む)。 */
    data class Entry(val name: String, val size: Long)

    /**
     * 相手へ渡すファイル。Android のクリップボードにある実体を読む側が実装する。
     *
     * ⚠ [read] は**相手の要求どおりの位置と長さ**で呼ばれる。前から順とは限らない。
     */
    interface Source {
        val entries: List<Entry>

        /** 読めなければ null。⚠ null は「転送失敗」として相手に伝わる。 */
        fun read(index: Int, position: Long, length: Int): ByteArray?

        /** 相手が読み終えた (あるいは諦めた) とき。開いた実体を閉じる。 */
        fun close() {}
    }

    /**
     * 相手から届いたファイルの置き場。
     *
     * ⛔ **実装側で I/O を受信スレッドに載せない。** ここは RDP の受信ループから呼ばれるので、
     * 書き込みで待つと**画面と入力まで止まる**。⇒ 実装は別スレッドへ渡すこと。
     */
    interface Sink {
        /** 受け取り始める。false を返すと、その 1 件は取り寄せずに飛ばす。 */
        fun begin(entry: Entry): Boolean
        fun write(data: ByteArray)
        /** [complete] が false なら途中で切れた。書きかけを消すのは実装側の責任。 */
        fun finish(complete: Boolean)
    }
}
