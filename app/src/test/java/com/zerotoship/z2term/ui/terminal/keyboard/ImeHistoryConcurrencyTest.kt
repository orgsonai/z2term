package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * 学習履歴 ([ImeHistoryStore]) を「記録しながら参照」しても落ちないことの回帰テスト。
 *
 * 記録は IO スレッドのコルーチン、参照は変換のたびに UI スレッドから走るので、可変の
 * `HashMap` を共有していた頃は `ConcurrentModificationException` でアプリごと落ちていた
 * (候補を押して確定 → 続けて残りかなの候補を出す、の並びで踏む)。表を不変にして差し替える
 * 作りになっていれば、どれだけ同時に叩いても例外は出ない。
 *
 * ⚠ 落ちるときだけ落ちる類のテストなので、**失敗しなければ合格**ではなく「一定回数まわして
 * 例外が 1 つも出ないこと」を見る。壊れた実装ならこの規模でほぼ毎回踏む。
 */
class ImeHistoryConcurrencyTest {

    @Test
    fun recordWhileReadingDoesNotThrow() {
        val loadedField = ImeHistoryStore::class.java.getDeclaredField("loaded")
            .apply { isAccessible = true }
        val wasLoaded = loadedField.getBoolean(ImeHistoryStore)
        loadedField.setBoolean(ImeHistoryStore, true)
        val failure = AtomicReference<Throwable?>(null)
        try {
            val stop = java.util.concurrent.atomic.AtomicBoolean(false)
            // 参照側 (UI スレッド相当): 走査を含む読み出しを回し続ける。
            val reader = Thread {
                try {
                    while (!stop.get()) {
                        ImeHistoryStore.predictHistory("あ", limit = 8)
                        ImeHistoryStore.historyFor("あいう", limit = 4)
                        ImeHistoryStore.learnedBlock("あいう")
                        ImeHistoryStore.bigramBonus("前", "後")
                        ImeHistoryStore.approximateCount()
                        ImeHistoryStore.snapshot()
                    }
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }
            reader.start()
            // 記録側: 毎回ちがう読みを足して表の構造 (キー) を変え続ける。
            for (i in 0 until 3000) {
                ImeHistoryStore.record("あ$i", "語$i")
                ImeHistoryStore.recordBigram("語$i", "次$i")
            }
            Thread.sleep(300)
            stop.set(true)
            reader.join(5_000)
        } finally {
            loadedField.setBoolean(ImeHistoryStore, wasLoaded)
        }
        assertNull("参照中に例外が出た: ${failure.get()}", failure.get())
    }
}
