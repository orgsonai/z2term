package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * いまの英字面が [asciiKeyLayout] で表せているか（0.8.403・段階 1b）。
 *
 * ⚠ **これは「モデルが正しい」ではなく「いまの画面と同じか」のテスト**。描画を移す段階
 * （1c）で見た目が動いていないことを、ここが担保する。⚠ ラベルとフリックの表は
 * [AsciiKeys] を [TerminalKeyboard] と共有しているので、片方だけ直しても気付ける。
 */
class AsciiKeyLayoutTest {

    private fun labelsOf(row: KeyRow): List<String> =
        row.slots.map { (it.content as SlotContent.Single).key.label }

    private fun keyOf(layout: KeyLayout, row: Int, col: Int): KeyDef =
        (layout.rows[row].slots[col].content as SlotContent.Single).key

    /** フリック先を「方向 → 送る文字列」で取り出す（比べやすくするため）。 */
    private fun flicksOf(key: KeyDef): Map<KeyGesture, String> =
        KeyGesture.FLICKS.mapNotNull { g ->
            (key.actionsFor(g).firstOrNull() as? KeyAction.Text)?.let { g to it.text }
        }.toMap()

    // ---- 4 方向フリック版（SPACIOUS）: 5 段 ------------------------------------------------

    @Test fun spacious_withFaceKey_matchesTodaysRows() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        assertEquals(5, l.rows.size)
        assertEquals(listOf("ESC") + AsciiKeys.ROW1 + "⌫", labelsOf(l.rows[0]))
        assertEquals(listOf("TAB") + AsciiKeys.ROW2, labelsOf(l.rows[1]))
        assertEquals(listOf("⇧") + AsciiKeys.ROW3 + "⏎", labelsOf(l.rows[2]))
        assertEquals(listOf("CTRL") + AsciiKeys.ROW4, labelsOf(l.rows[3]))
        // 最下段の左端は面の切替キー。⚠ ラベルは「押すと行く面」なので描画側が入れる（空）。
        assertEquals(listOf("", "?#", "ALT", "SPACE", "←", "↓", "↑", "→"), labelsOf(l.rows[4]))
    }

    // ---- シンプル版（COMPACT）: 上にバーが増えて 6 段 --------------------------------------

    @Test fun compact_addsTheTopBar() {
        val l = asciiKeyLayout(compact = true, hasFaceKey = true)
        assertEquals(6, l.rows.size)
        // ⚠ 主行の左 1.4 列が空くので、英字キーはそのぶん広くなる（いまと同じ）。
        assertEquals(listOf("ESC", "TAB", "⇧", "CTRL"), labelsOf(l.rows[0]))
        assertEquals(AsciiKeys.ROW1 + "⌫", labelsOf(l.rows[1]))
        assertEquals(AsciiKeys.ROW2, labelsOf(l.rows[2]))
        assertEquals(AsciiKeys.ROW3 + "⏎", labelsOf(l.rows[3]))
        assertEquals(AsciiKeys.ROW4, labelsOf(l.rows[4]))
    }

    // ---- 面の切替キーが無いとき: ⇧ と CTRL が 1 段ずつ下がる -------------------------------

    @Test fun withoutFaceKey_shiftAndCtrlSlideDown() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = false)
        // Row 3 の左端は貼り付け / 絵文字の入口（旧 META の席）。
        assertEquals("↕", labelsOf(l.rows[2]).first())
        // ⇧ が Row 4 へ、CTRL が最下段へ。
        assertEquals("⇧", labelsOf(l.rows[3]).first())
        assertEquals("CTRL", labelsOf(l.rows[4]).first())
    }

    @Test fun compactWithoutFaceKey_swapsCtrlForThePad() {
        val l = asciiKeyLayout(compact = true, hasFaceKey = false)
        // 上のバーの右端が CTRL → 貼り付けの入口に入れ替わり、CTRL は最下段へ。
        assertEquals(listOf("ESC", "TAB", "⇧", "↕"), labelsOf(l.rows[0]))
        assertEquals("CTRL", labelsOf(l.rows[5]).first())
    }

    // ---- 記号面: ⭐ Row 4 だけ 8 個 ---------------------------------------------------------

    @Test fun symbolFace_hasEightKeysOnRow4() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true, symbols = true)
        assertEquals(8, AsciiKeys.SYM_ROW4.size)
        assertEquals(listOf("CTRL") + AsciiKeys.SYM_ROW4, labelsOf(l.rows[3]))
        // ⭐ 枠の数が変わる = レイヤーでは表せない。だから記号面は別レイアウトになっている。
        val normal = asciiKeyLayout(compact = false, hasFaceKey = true, symbols = false)
        assertEquals(11, normal.rows[3].slots.size)   // CTRL + z..(10 個)
        assertEquals(9, l.rows[3].slots.size)         // CTRL + 記号 8 個
        assertEquals("ABC", labelsOf(l.rows[4])[1])
    }

    @Test fun symbolFace_hasNoFlicks() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true, symbols = true)
        assertTrue(flicksOf(keyOf(l, 1, 1)).isEmpty())
        assertTrue(keyOf(l, 1, 1).layers.isEmpty())
    }

    // ---- フリック ---------------------------------------------------------------------------

    @Test fun spacious_row2_isFourWayPlusUppercase() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        // Row 2 の先頭は TAB なので q は col=1。
        assertEquals(
            mapOf(
                KeyGesture.UP to "!",
                KeyGesture.LEFT to "`",
                KeyGesture.RIGHT to "~",
                KeyGesture.DOWN to "Q",
            ),
            flicksOf(keyOf(l, 1, 1)),
        )
    }

    @Test fun compact_row2_isUpAndUppercaseOnly() {
        val l = asciiKeyLayout(compact = true, hasFaceKey = true, fourWayFlick = false)
        // COMPACT は上バーがあるので Row 2 は rows[2]、左端キーは無いので q は col=0。
        assertEquals(
            mapOf(KeyGesture.UP to "!", KeyGesture.DOWN to "Q"),
            flicksOf(keyOf(l, 2, 0)),
        )
    }

    @Test fun row3AndRow4_flickUpThenUppercase() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        assertEquals(mapOf(KeyGesture.UP to "-", KeyGesture.DOWN to "A"), flicksOf(keyOf(l, 2, 1)))
        assertEquals(mapOf(KeyGesture.UP to "`", KeyGesture.DOWN to "Z"), flicksOf(keyOf(l, 3, 1)))
        // 「,」「.」「/」には大文字が無いので下フリックも付かない。
        val slash = keyOf(l, 3, 10)
        assertEquals("/", slash.label)
        assertEquals(mapOf(KeyGesture.UP to "{"), flicksOf(slash))
    }

    // ---- 幅（いまの weight と一致すること） --------------------------------------------------

    @Test fun widths_matchTodaysWeights() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        // ⚠ プリセットは全部 Fixed。Auto にすると「予算 = 枠の数」で分け直され、
        //    いまの 1.4 + 1.0×10 + 1.4 (合計 12.8) と比率が変わって見た目が動く。
        assertEquals(
            listOf(1.4f) + List(10) { 1f } + 1.4f,
            l.rows[0].weights(),
        )
        assertEquals(
            listOf(1.4f, 1.2f, 1.2f, 4f, 1f, 1f, 1f, 1f),
            l.rows[4].weights(),
        )
    }

    // ---- ⇧ はレイヤー（枠の数が変わらないので姿の差し替えで足りる）---------------------------

    @Test fun shiftLayer_replacesLetterWithItsUppercase() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        val q = keyOf(l, 1, 1)
        val shifted = q.onLayer(KeyLayout.LAYER_SHIFT)
        assertEquals("Q", shifted.label)
        assertEquals(listOf(KeyAction.Text("Q")), shifted.actionsFor(KeyGesture.TAP))
        // フリック先は ⇧ でも変わらない（いまと同じ）。
        assertEquals(flicksOf(q), flicksOf(shifted))
    }

    @Test fun digitsHaveNoShiftLayer() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        assertTrue(keyOf(l, 0, 1).layers.isEmpty())
    }

    // ---- 隠し機能がキーの割り当てとして表現できている -----------------------------------------

    @Test fun escAndBackspaceCarryTheirHiddenFlicks() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        val esc = keyOf(l, 0, 0)
        assertEquals(listOf(KeyAction.App(AppAction.PAD_PASTE)), esc.actionsFor(KeyGesture.UP))
        assertEquals(listOf(KeyAction.App(AppAction.PAD_EMOJI)), esc.actionsFor(KeyGesture.DOWN))

        val bs = keyOf(l, 0, 11)
        assertEquals("⌫", bs.label)
        assertEquals(
            listOf(KeyAction.Chord(mods = setOf(ModKey.CTRL), text = "w")),
            bs.actionsFor(KeyGesture.LEFT),
        )
        assertEquals(
            listOf(KeyAction.Chord(mods = setOf(ModKey.CTRL), text = "u")),
            bs.actionsFor(KeyGesture.RIGHT),
        )
        // ⚠ どちらも印を出さない（隠したままにするのが利用者の判断）。
        assertTrue(!esc.showHint && !bs.showHint)
    }

    // ---- 全プリセットが壊れていない -----------------------------------------------------------

    @Test fun everyPresetValidates() {
        for (compact in listOf(true, false)) {
            for (face in listOf(true, false)) {
                for (sym in listOf(true, false)) {
                    val l = asciiKeyLayout(compact, face, sym)
                    assertEquals(l.id, emptyList<String>(), l.validate())
                }
            }
        }
    }
}
