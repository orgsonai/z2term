package com.zerotoship.z2term.snippets

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * グループで絞ったまま並べ替えたときの保存 ([SnippetStore.reorderWithin]、0.8.387)。
 *
 * **なぜテストが要るか**: ここを間違えると**出ていないグループのスニペットが消える**。
 * しかも消えたことは、そのグループを開くまで分からない。
 */
class SnippetGroupTest {

    private fun s(id: String, group: String = "") =
        Snippet(id = id, label = "", command = id, groupId = group)

    @Test fun reorder_movesOnlyTheVisibleRows() {
        // 全体 [a1, b1, a2] のうち、グループ a を [a2, a1] に並べ替える。
        val all = listOf(s("a1", "a"), s("b1", "b"), s("a2", "a"))
        val visible = listOf(s("a2", "a"), s("a1", "a"))
        assertEquals(
            listOf("a2", "b1", "a1"),
            SnippetStore.reorderWithin(all, visible).map { it.id }
        )
    }

    @Test fun reorder_keepsEveryoneElse() {
        // ⚠ 絞り込んだ並びをそのまま保存すると b1/b2 が消える。消えないことを固定する。
        val all = listOf(s("a1", "a"), s("b1", "b"), s("a2", "a"), s("b2", "b"))
        val visible = listOf(s("a2", "a"), s("a1", "a"))
        assertEquals(
            listOf("a2", "b1", "a1", "b2"),
            SnippetStore.reorderWithin(all, visible).map { it.id }
        )
    }

    @Test fun reorder_withoutFilterIsJustTheNewOrder() {
        // 「すべて」表示のときは全部が見えている = 並べ替えた順そのもの。
        val all = listOf(s("a"), s("b"), s("c"))
        val visible = listOf(s("c"), s("a"), s("b"))
        assertEquals(
            listOf("c", "a", "b"),
            SnippetStore.reorderWithin(all, visible).map { it.id }
        )
    }

    @Test fun reorder_ignoresRowsThatAreGone() {
        // 並べ替えている最中に別の経路で消えた行が混ざっても、全体は壊れない。
        val all = listOf(s("a1", "a"), s("b1", "b"))
        val visible = listOf(s("gone", "a"), s("a1", "a"))
        assertEquals(
            listOf("a1", "b1"),
            SnippetStore.reorderWithin(all, visible).map { it.id }
        )
    }

    @Test fun snippetsWithoutAGroupAreUngrouped() {
        // 0.8.387 より前に作られたスニペットは groupId を持たない = 未分類 (「すべて」には出る)。
        // ⚠ JSON からの読み出し (`Snippet.fromJson`) は org.json 依存なのでここでは扱わない
        // (JVM のユニットテストでは org.json がスタブで、既定値しか返らない)。
        assertEquals("", Snippet(id = "x", label = "L", command = "ls").groupId)
    }
}
