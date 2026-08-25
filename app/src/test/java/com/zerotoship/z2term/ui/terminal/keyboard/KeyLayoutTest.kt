package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * カスタムキーボードのレイアウト定義 ([KeyLayout]) の検証（0.8.402・段階 1a）。
 *
 * ここで固めるのは**利用者が言葉で出した要件そのもの**:
 *  - 幅を 1 つ変えたら残りが均等に再配分され、**一度変えたキーは動かない**
 *  - 上下左右キーのように**枠を割れる**（向きは自由・深さ 2 まで）
 *  - **戻る手段の無い配列は保存させない**
 */
class KeyLayoutTest {

    private fun row(vararg slots: KeySlot) = KeyRow(slots.toList())
    private fun key(label: String) = KeyDef.text(label)
    private fun slot(label: String, width: KeyWidth = KeyWidth.Auto) = KeySlot.of(key(label), width)

    // ---- 横幅の再配分（要望の本文どおり） -------------------------------------------------

    @Test fun allAuto_sharesEvenly() {
        val r = row(slot("a"), slot("b"), slot("c"), slot("d"), slot("e"))
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f), r.weights())
    }

    @Test fun oneFixed_redistributesTheRest() {
        // 5 枠のうち 1 つを 2 倍に固定 → 残り 3.0 を 4 つで割って 0.75 ずつ。
        val r = row(slot("a", KeyWidth.Fixed(2f)), slot("b"), slot("c"), slot("d"), slot("e"))
        assertEquals(listOf(2f, 0.75f, 0.75f, 0.75f, 0.75f), r.weights())
    }

    @Test fun secondFixed_doesNotMoveTheFirst() {
        // ⭐ 「一度変更したキーのサイズは変わらずに、別のキーを変えると再度均等に再分配」
        val r = row(
            slot("a", KeyWidth.Fixed(2f)),
            slot("b", KeyWidth.Fixed(1.5f)),
            slot("c"), slot("d"), slot("e"),
        )
        // 予算 5.0 − 固定 3.5 = 1.5 を 3 つで割る。固定した 2 つは動かない。
        assertEquals(listOf(2f, 1.5f, 0.5f, 0.5f, 0.5f), r.weights())
    }

    @Test fun fixedOverBudget_keepsAutoPressable() {
        // 固定が予算を食い尽くしても Auto を 0 にしない（0 だと押せないキーが居座る）。
        val r = row(slot("a", KeyWidth.Fixed(4f)), slot("b", KeyWidth.Fixed(4f)), slot("c"))
        assertEquals(KeyRow.MIN_WEIGHT, r.weights()[2], 0.0001f)
    }

    // ---- 枠の分割（「上下左右キーは 1 キーの半分」への答え） -------------------------------

    /** 矢印 4 つを 1 枠に田の字で置く（縦 2 分割 → 各段を横 2 分割 = 深さ 2）。 */
    private fun arrowPad(): KeySlot {
        fun pair(l: String, r: String) = SlotPart(
            SlotContent.Split(
                SplitDir.HORIZONTAL,
                listOf(SlotPart(SlotContent.Single(key(l))), SlotPart(SlotContent.Single(key(r)))),
            )
        )
        return KeySlot(SlotContent.Split(SplitDir.VERTICAL, listOf(pair("⇱", "⇲"), pair("←", "→"))))
    }

    @Test fun splitDepthTwo_isAllowed() {
        val layout = KeyLayout("test", "test", listOf(row(arrowPad(), faceSlot())))
        assertEquals(emptyList<String>(), layout.validate())
        // 分割の中のキーもちゃんと数える。
        assertEquals(listOf("⇱", "⇲", "←", "→", "ABC"), layout.allKeys().map { it.label })
    }

    @Test fun splitDepthThree_isRejected() {
        val inner = SlotContent.Split(
            SplitDir.HORIZONTAL,
            listOf(SlotPart(SlotContent.Single(key("x"))), SlotPart(SlotContent.Single(key("y")))),
        )
        val middle = SlotContent.Split(SplitDir.VERTICAL, listOf(SlotPart(inner), SlotPart(inner)))
        val outer = SlotContent.Split(SplitDir.HORIZONTAL, listOf(SlotPart(middle), SlotPart(middle)))
        val layout = KeyLayout("test", "test", listOf(row(KeySlot(outer), faceSlot())))
        assertTrue(layout.validate().any { it.contains("deeper than") })
    }

    @Test fun splitIntoOne_isRejected() {
        val bad = SlotContent.Split(SplitDir.VERTICAL, listOf(SlotPart(SlotContent.Single(key("x")))))
        val layout = KeyLayout("test", "test", listOf(row(KeySlot(bad), faceSlot())))
        assertTrue(layout.validate().any { it.contains("splits into 1") })
    }

    // ---- 逃げ場（戻る手段） ---------------------------------------------------------------

    private fun faceSlot() = KeySlot.of(
        KeyDef(
            label = "ABC",
            bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.App(AppAction.NEXT_FACE))),
        )
    )

    @Test fun layoutWithoutAWayBack_isRejected() {
        val layout = KeyLayout("test", "test", listOf(row(slot("a"), slot("b"))))
        assertFalse(layout.hasEscapeHatch())
        assertTrue(layout.validate().any { it.contains("no way back") })
    }

    @Test fun wayBackOnALayer_counts() {
        // レイヤーの中にしか無くても「戻れる」ことに変わりはない。
        val onFn = KeyDef(
            label = "⚙",
            bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.App(AppAction.SETTINGS))),
        )
        val layout = KeyLayout(
            "test", "test",
            listOf(row(KeySlot.of(KeyDef(label = "a", layers = mapOf("fn" to onFn))), slot("b"))),
        )
        assertTrue(layout.hasEscapeHatch())
    }

    // ---- キー 1 つの割り当て ---------------------------------------------------------------

    @Test fun bindings_holdActionSequences() {
        // ⭐ 1 キーに 2 手（Ctrl+A → d）。単発は要素 1 個の列というだけ。
        val detach = KeyDef(
            label = "det",
            bindings = mapOf(
                KeyGesture.TAP to listOf(
                    KeyAction.Chord(mods = setOf(ModKey.CTRL), text = "a"),
                    KeyAction.Text("d"),
                )
            ),
        )
        assertEquals(2, detach.actionsFor(KeyGesture.TAP).size)
        assertTrue(detach.actionsFor(KeyGesture.LONG_PRESS).isEmpty())
    }

    @Test fun layerReplacesTheKeyWholesale() {
        val shifted = KeyDef.text("A", "A")
        val a = KeyDef.text("a").copy(layers = mapOf(KeyLayout.LAYER_SHIFT to shifted))
        assertEquals("A", a.onLayer(KeyLayout.LAYER_SHIFT).label)
        assertEquals("a", a.onLayer(null).label)
        assertEquals("a", a.onLayer("no-such-layer").label)
    }
}
