package com.zerotoship.z2term.qr

/** QR ツールで読み取った内容 1 件 (0.8.603)。同じ内容は 1 件にまとめるので、[text] で区別する。 */
internal data class QrHistoryEntry(val text: String, val time: Long, val pinned: Boolean = false)

/**
 * QR ツールの履歴 (0.8.603・**純関数**・`QrHistoryTest` が固定する)。保存は [QrHistoryStore]。
 *
 * - 残すのは**読み取った内容だけ** (カメラ・画像・他アプリからの共有)。「QRを表示」で作った内容や、
 *   履歴から表示し直した内容は足さない (利用者の指定)。
 * - 並びは新しい順。同じ内容を読み直したら先頭へ上げ、ピン留めは保つ。
 * - ピン留めしていないものは [MAX_UNPINNED] 件まで。古いものから落とす。ピン留めは上限で落とさない。
 * - 「すべて消す」はピン留めを残す (利用者の指定)。
 */
internal object QrHistory {
    const val MAX_UNPINNED = 50

    fun add(entries: List<QrHistoryEntry>, text: String, time: Long): List<QrHistoryEntry> {
        val pinned = entries.any { it.text == text && it.pinned }
        return trim(listOf(QrHistoryEntry(text, time, pinned)) + entries.filterNot { it.text == text })
    }

    fun setPinned(entries: List<QrHistoryEntry>, text: String, pinned: Boolean): List<QrHistoryEntry> =
        trim(entries.map { if (it.text == text) it.copy(pinned = pinned) else it })

    fun remove(entries: List<QrHistoryEntry>, text: String): List<QrHistoryEntry> =
        entries.filterNot { it.text == text }

    fun clearUnpinned(entries: List<QrHistoryEntry>): List<QrHistoryEntry> = entries.filter { it.pinned }

    /** 画面に出す順。ピン留め → それ以外で、どちらも新しい順。 */
    fun ordered(entries: List<QrHistoryEntry>): List<QrHistoryEntry> =
        entries.sortedWith(compareByDescending<QrHistoryEntry> { it.pinned }.thenByDescending { it.time })

    private fun trim(entries: List<QrHistoryEntry>): List<QrHistoryEntry> {
        val kept = entries.filterNot { it.pinned }.sortedByDescending { it.time }
            .take(MAX_UNPINNED).map { it.text }.toSet()
        return entries.filter { it.pinned || it.text in kept }
    }
}
