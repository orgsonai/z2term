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

    private fun widthsOf(row: KeyRow): List<KeyWidth> = row.slots.map { it.width }

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
        assertEquals(listOf("", "?#", "ALT", "space", "←", "↓", "↑", "→"), labelsOf(l.rows[4]))
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

    @Test fun symbolFace_usesTheSameRequestedWidthRules() {
        val normal = asciiKeyLayout(compact = false, hasFaceKey = true)
        val symbols = asciiKeyLayout(compact = false, hasFaceKey = true, symbols = true)

        // 枠数が同じ段と最下段は、通常面と幅指定が完全に同じ。
        for (row in listOf(0, 1, 2, 4)) {
            assertEquals("row $row", widthsOf(normal.rows[row]), widthsOf(symbols.rows[row]))
        }
        // 記号面だけ 8 キーの段も、CTRL=1.4 / 残り Auto という同じ規則を使う。
        assertEquals(
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_SIDE)) +
                List(AsciiKeys.SYM_ROW4.size) { KeyWidth.Auto },
            widthsOf(symbols.rows[3]),
        )
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

    @Test fun widths_matchTheRequestedDefaultLayout() {
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        assertEquals(
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_SIDE)) +
                List(10) { KeyWidth.Auto } + KeyWidth.Fixed(AsciiKeys.W_SIDE),
            widthsOf(l.rows[0]),
        )
        assertEquals(
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_TAB)) + List(10) { KeyWidth.Auto },
            widthsOf(l.rows[1]),
        )
        assertEquals(
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_SHIFT)) +
                List(9) { KeyWidth.Auto } + KeyWidth.Fixed(AsciiKeys.W_ENTER),
            widthsOf(l.rows[2]),
        )
        assertEquals(
            listOf<KeyWidth>(KeyWidth.Fixed(AsciiKeys.W_SIDE)) + List(10) { KeyWidth.Auto },
            widthsOf(l.rows[3]),
        )
        assertEquals(
            listOf<KeyWidth>(
                KeyWidth.Fixed(AsciiKeys.W_FACE),
                KeyWidth.Fixed(AsciiKeys.W_MOD),
                KeyWidth.Fixed(AsciiKeys.W_MOD),
                KeyWidth.Fixed(AsciiKeys.W_SPACE),
            ) + List(4) { KeyWidth.Auto },
            widthsOf(l.rows[4]),
        )
        // 最下段の予算 8 - 固定幅 4.6 = 3.4 を、矢印 4 個で 0.85 ずつ分ける。
        assertEquals(
            listOf(1f, 0.8f, 0.8f, 2f, 0.85f, 0.85f, 0.85f, 0.85f),
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

    @Test fun digitsRepeatButLettersLeaveItToTheFlickKey() {
        // ⚠ いまの部品の都合をモデル側で固定しておく: 数字は BasicKey に連打を任せ (repeatable)、
        // 英字は FlickKey が自前で連打する (repeatable=false)。描画の振り分けがこれを見ている。
        val l = asciiKeyLayout(compact = false, hasFaceKey = true)
        assertTrue(keyOf(l, 0, 1).repeatable)      // 数字
        assertTrue(!keyOf(l, 1, 1).repeatable)     // 英字
        assertEquals(KeyFontRole.MAIN, keyOf(l, 0, 1).fontRole)
        assertEquals(KeyFontRole.MAIN, keyOf(l, 1, 1).fontRole)
        // 機能キーの文字サイズも役割で持つ (ESC / TAB / CTRL / ALT / ?# は小さめ)。
        assertEquals(KeyFontRole.SMALL, keyOf(l, 0, 0).fontRole)
        assertEquals(KeyFontRole.NORMAL, keyOf(l, 4, 4).fontRole)   // 矢印
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
        assertTrue(esc.hintGestures.isEmpty() && bs.hintGestures.isEmpty())
        // ⚠ しきい値を超えた瞬間に発火する（文字キーのように離すまで待たない）。
        assertTrue(!esc.flickOnRelease && !bs.flickOnRelease)
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
