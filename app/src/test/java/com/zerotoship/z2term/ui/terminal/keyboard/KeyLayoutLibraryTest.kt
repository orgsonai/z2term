package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配列を複製する / 束を出し入れする（0.8.408・段階 2）。
 *
 * ⚠ 一番大事なのは [asTemplate] の**幅の読み替え**。ここを忘れると「幅を変えても他が
 * 再配分されない」テンプレートができ、要望（1 つ広げたら残りが均等に縮む）が最初から
 * 成立しない配列を利用者が作ってしまう。しかも**エディタで幅をいじるまで気付かない**。
 */
class KeyLayoutLibraryTest {

    private fun widthsOf(row: KeyRow): List<KeyWidth> = row.slots.map { it.width }

    // ---- 複製（テンプレート化） ------------------------------------------------------------

    /** ⭐ `Fixed(1.0)` だけが [KeyWidth.Auto] へ戻り、**意図して広げた幅は残る**。 */
    @Test fun asTemplate_turnsOnlyEvenWidthsBackToAuto() {
        val preset = asciiKeyLayout(compact = false, hasFaceKey = true)
        val copy = preset.asTemplate(id = "mine", name = "じぶんの英字")

        // Row 1 は ESC(1.4) + 数字 10 個(1.0) + ⌫(1.4)。
        val expected: List<KeyWidth> =
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_SIDE)) +
                List(10) { KeyWidth.Auto } +
                KeyWidth.Fixed(AsciiKeys.W_SIDE)
        assertEquals(expected, widthsOf(copy.rows[0]))
        // 最下段のスペース(4.0) と `?#`(1.2) もそのまま。
        assertTrue(widthsOf(copy.rows[4]).contains(KeyWidth.Fixed(AsciiKeys.W_SPACE)))
        assertTrue(widthsOf(copy.rows[4]).contains(KeyWidth.Fixed(AsciiKeys.W_MOD)))
    }

    /** ⚠ 中身（キーの割り当て）は 1 つも変えない。変えるのは id / 名前 / 幅の書き方だけ。 */
    @Test fun asTemplate_keepsEveryKeyAsItWas() {
        val preset = asciiKeyLayout(compact = true, hasFaceKey = false)
        val copy = preset.asTemplate("mine", "じぶんの英字")
        assertEquals("mine", copy.id)
        assertEquals("じぶんの英字", copy.name)
        assertEquals(preset.allKeys(), copy.allKeys())
        assertTrue(copy.validate().isEmpty())
    }

    /**
     * ⭐ 複製したあと**再配分が実際に効く**こと。ここが要望の本体。
     * 1 つ広げると、他の [KeyWidth.Auto] が均等に縮む。
     */
    @Test fun afterTemplating_wideningOneKeyShrinksTheOthersEvenly() {
        val copy = asciiKeyLayout(compact = false, hasFaceKey = true).asTemplate("mine", "mine")
        val row = copy.rows[0]
        val before = row.weights()

        // 3 番目の枠（数字の 2）を 2 倍幅にする。
        val widened = KeyRow(
            row.slots.mapIndexed { i, s -> if (i == 2) s.copy(width = KeyWidth.Fixed(2f)) else s }
        )
        val after = widened.weights()

        assertEquals(2f, after[2], 0.001f)
        // 広げた 1 つ以外の Auto は**全部同じだけ**縮む。
        val autoIndices = (1..10).filter { it != 2 }
        val shrunk = autoIndices.map { after[it] }
        assertTrue("Auto がばらついた: $shrunk", shrunk.all { kotlin.math.abs(it - shrunk[0]) < 0.001f })
        assertTrue("縮んでいない", shrunk[0] < before[1])
        // 固定した端のキーは動かない。
        assertEquals(before[0], after[0], 0.001f)
        assertEquals(before[11], after[11], 0.001f)
    }

    /** 押しつぶされても最低幅は残す（押せないキーが段に居座らない）。 */
    @Test fun weights_neverCollapseToZero() {
        val row = KeyRow(
            listOf(
                KeySlot.of(KeyDef.text("a"), KeyWidth.Fixed(5f)),
                KeySlot.of(KeyDef.text("b")),
            )
        )
        assertEquals(KeyRow.MIN_WEIGHT, row.weights()[1], 0.0001f)
    }

    // ---- id と名前 ----------------------------------------------------------------------

    @Test fun newId_skipsTheOnesAlreadyUsed() {
        val existing = listOf(layout("layout1"), layout("layout3"))
        assertEquals("layout2", newKeyLayoutId(existing))
        assertEquals("layout1", newKeyLayoutId(emptyList()))
    }

    @Test fun uniqueName_addsANumberOnlyWhenItWouldCollide() {
        val existing = listOf(layout("a", "じぶんの英字"), layout("b", "じぶんの英字 2"))
        assertEquals("じぶんの英字 3", uniqueKeyLayoutName(existing, "じぶんの英字"))
        assertEquals("べつの", uniqueKeyLayoutName(existing, "べつの"))
    }

    // ---- 束の出し入れ --------------------------------------------------------------------

    @Test fun upsert_replacesTheSameIdAndKeepsTheOrder() {
        val before = listOf(layout("a", "A"), layout("b", "B"))
        val after = before.upsertLayout(layout("a", "AA"))
        assertEquals(listOf("a", "b"), after.map { it.id })
        assertEquals("AA", after[0].name)
    }

    @Test fun upsert_appendsWhenTheIdIsNew() {
        val after = listOf(layout("a")).upsertLayout(layout("b"))
        assertEquals(listOf("a", "b"), after.map { it.id })
    }

    /** ⚠ 消したつもりで**別の 1 件も消える**のが一番困る。 */
    @Test fun remove_takesExactlyOne() {
        val before = listOf(layout("a"), layout("b"), layout("c"))
        assertEquals(listOf("a", "c"), before.removeLayout("b").map { it.id })
        assertEquals(before, before.removeLayout("zzz"))
    }

    @Test fun rename_touchesOnlyTheNameOfTheOne() {
        val before = listOf(layout("a", "A"), layout("b", "B"))
        val after = before.renameLayout("b", "BB")
        assertEquals(listOf("A", "BB"), after.map { it.name })
        assertEquals(before[0], after[0])
    }

    /** ⚠ **使っていた 1 件を消したときだけ**既定へ戻す。関係ない削除で配列を変えない。 */
    @Test fun activeAfterRemove_onlyChangesWhenTheActiveOneIsGone() {
        assertEquals("", nextActiveAfterRemove(activeId = "a", removedId = "a"))
        assertEquals("a", nextActiveAfterRemove(activeId = "a", removedId = "b"))
        assertEquals("", nextActiveAfterRemove(activeId = "", removedId = "b"))
    }

    // ---- 小道具 --------------------------------------------------------------------------

    private fun layout(id: String, name: String = id) =
        KeyLayout(id, name, listOf(KeyRow(listOf(KeySlot.of(KeyDef.text("a"))))))
}
