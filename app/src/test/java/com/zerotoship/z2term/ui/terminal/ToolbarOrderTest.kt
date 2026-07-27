package com.zerotoship.z2term.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ツールバーの並び順の読み書き ([ToolbarButtons.mergeOrder] / [ToolbarButtons.normalizeOrder])。
 *
 * **ここが崩れると「同じボタンが 2 個出る」** という、見た目で気付くまで分からない不具合になる
 * (0.8.212 で修正)。保存値は DataStore に残り続けるので、一度壊れると再起動しても直らない。
 * 「全 id がちょうど 1 回ずつ」を両方向で押さえる。
 */
class ToolbarOrderTest {

    /** 端末タブのボタン (既定順)。 */
    private val all = listOf("paste", "snippets", "screen_on", "keep_alive", "search", "keyboard", "log")

    // --- 読む側 ---

    @Test
    fun emptySavedOrderFallsBackToDefault() {
        assertEquals(all, ToolbarButtons.mergeOrder(emptyList(), all))
    }

    @Test
    fun savedOrderIsKeptAndNewButtonsAppended() {
        // 保存後に増えたボタン (log) は末尾へ。並べ替え済みの並びは崩さない。
        val saved = listOf("keyboard", "paste", "snippets", "screen_on", "keep_alive", "search")
        assertEquals(saved + "log", ToolbarButtons.mergeOrder(saved, all))
    }

    @Test
    fun duplicatedSavedOrderIsCollapsed() {
        // 壊れた保存値。畳まないと paste と search が 2 個ずつ描かれる (報告された症状)。
        val saved = listOf("paste", "search", "paste", "snippets", "search")
        val merged = ToolbarButtons.mergeOrder(saved, all)
        assertEquals(merged.distinct(), merged)
        assertEquals(all.sorted(), merged.sorted())
        assertEquals(listOf("paste", "search", "snippets"), merged.take(3))
    }

    // --- 書く側 ---

    @Test
    fun reorderKeepsHiddenButtonsInPlace() {
        // log を隠した状態で表示中の 6 個を並べ替えても、log は保存値の位置に残る。
        val shown = listOf("keyboard", "paste", "snippets", "screen_on", "keep_alive", "search")
        val out = ToolbarButtons.normalizeOrder(
            savedCsv = all.joinToString(","),
            allIds = all,
            hiddenIds = setOf("log"),
            shownOrder = shown,
        )
        assertEquals("keyboard,paste,snippets,screen_on,keep_alive,search,log", out)
    }

    @Test
    fun staleShownOrderDoesNotDuplicate() {
        // 「隠す」を切り替えた直後にドラッグを確定すると、hidden の変更が反映される前の
        // 古い並び (隠したはずの log を含む 7 個) が渡ってくることがある。
        // 旧実装はこれをそのまま位置へ流し込み、log が 2 か所に入った保存値を書いていた。
        val stale = listOf("paste", "snippets", "log", "screen_on", "keep_alive", "search", "keyboard")
        val out = ToolbarButtons.normalizeOrder(
            savedCsv = all.joinToString(","),
            allIds = all,
            hiddenIds = setOf("log"),
            shownOrder = stale,
        )
        val ids = out.split(',')
        assertEquals(ids.distinct(), ids)
        assertEquals(all.sorted(), ids.sorted())
    }

    @Test
    fun alreadyBrokenSavedValueHealsOnNextWrite() {
        // 既に壊れている保存値の上に書いても、出てくるのは正常な値。
        val broken = "paste,paste,snippets,search,search,screen_on,keep_alive,keyboard,log"
        val out = ToolbarButtons.normalizeOrder(
            savedCsv = broken,
            allIds = all,
            hiddenIds = emptySet(),
            shownOrder = all,
        )
        val ids = out.split(',')
        assertEquals(ids.distinct(), ids)
        assertEquals(all.sorted(), ids.sorted())
    }

    @Test
    fun shorterShownOrderStillWritesEveryButtonOnce() {
        // 表示順が何らかの理由で足りない (作り直し途中など) 場合でも、欠落・重複を書かない。
        val out = ToolbarButtons.normalizeOrder(
            savedCsv = all.joinToString(","),
            allIds = all,
            hiddenIds = emptySet(),
            shownOrder = listOf("keyboard"),
        )
        val ids = out.split(',')
        assertEquals(ids.distinct(), ids)
        assertEquals(all.sorted(), ids.sorted())
    }

    @Test
    fun guiTabDoesNotInventButtonsItDoesNotHave() {
        // GUI タブは 🔍/⚪ を持たない。その状態で並べ替えても重複を作らない。
        val guiIds = listOf("paste", "snippets", "screen_on", "keep_alive", "keyboard")
        val out = ToolbarButtons.normalizeOrder(
            savedCsv = all.joinToString(","),
            allIds = guiIds,
            hiddenIds = emptySet(),
            shownOrder = listOf("keyboard", "paste", "snippets", "screen_on", "keep_alive"),
        )
        val ids = out.split(',')
        assertEquals(ids.distinct(), ids)
        assertEquals(guiIds.sorted(), ids.sorted())
    }
}
