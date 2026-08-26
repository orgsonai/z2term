package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存した配列が**そのまま戻ってくるか**（0.8.408・段階 2）。
 *
 * ⚠ **なぜここまで見るか**: 落ちるのはいつも「書けたけど読めない」側で、しかも**次に
 * キーボードを開くまで気付かない**。エディタ（段階 3）を作ってから壊れていると分かると、
 * 原因がエディタなのか保存なのか切り分けられなくなる。先にここを固める。
 *
 * ⚠ このテストは `org.json` に触らない（JVM のユニットテストでは動かないため）。
 * JSON 文字列との橋は [KeyLayoutJson] にあり、ここは [KeyLayoutCodec] の木だけを見る。
 */
class KeyLayoutCodecTest {

    private fun roundTrip(layout: KeyLayout): KeyLayout {
        val back = KeyLayoutCodec.decode(KeyLayoutCodec.encode(layout))
        assertNotNull("読み戻せなかった", back)
        return back!!
    }

    // ---- いまのプリセットが往復すること ---------------------------------------------------

    /**
     * ⭐ **プリセット 8 通りが 1 つ残らず往復する**のが最低ライン。複製の元がこれなので、
     * ここが欠けると「作った瞬間に別物になる配列」ができる。
     */
    @Test fun everyPreset_survivesTheRoundTrip() {
        for (compact in listOf(false, true)) {
            for (hasFaceKey in listOf(false, true)) {
                for (symbols in listOf(false, true)) {
                    val original = asciiKeyLayout(compact, hasFaceKey, symbols)
                    assertEquals("$compact/$hasFaceKey/$symbols", original, roundTrip(original))
                }
            }
        }
    }

    /** パッドを開いている間の下段も同じ形で運べること（0.8.407 で足した行）。 */
    @Test fun padRow_survivesTheRoundTrip() {
        val original = KeyLayout(id = "pad", name = "pad", rows = listOf(asciiPadRow()))
        assertEquals(original, roundTrip(original))
    }

    /** 日本語・数字の対象面と編集用プリセットが欠けずに往復する。 */
    @Test fun twelveKeyFaces_surviveTheRoundTrip() {
        listOf(japaneseKeyLayout(), numberKeyLayout()).forEach { original ->
            assertTrue(original.validate().isEmpty())
            assertEquals(original, roundTrip(original))
            assertEquals(original.faceId, KeyLayoutCodec.encode(original)["face"])
        }
    }

    /** 旧JSONは face 省略 = 英字として読み、保存済み配列との互換を保つ。 */
    @Test fun missingFace_meansAscii() {
        val encoded = KeyLayoutCodec.encode(oneKeyLayout(KeyDef.text("a")))
        assertEquals(null, encoded["face"])
        assertEquals(KeyboardFace.ASCII.id, KeyLayoutCodec.decode(encoded)?.faceId)
    }

    @Test fun symbolChildAndResetBaseline_surviveTheRoundTrip() {
        val main = asciiKeyLayout(compact = true, hasFaceKey = true)
        val symbols = asciiKeyLayout(compact = true, hasFaceKey = true, symbols = true).rows
        val template = main.copy(symbolRows = symbols).asTemplate("mine", "My keys")
        val original = template
            .copy(name = "Saved mistake", rows = main.rows.drop(1))

        val back = roundTrip(original)
        assertEquals(KeyboardStyle.COMPACT.id, back.styleId)
        assertEquals(template.symbolRows, back.symbolRows)
        assertEquals("My keys", back.restoreDefaults().name)
        assertEquals(template.rows, back.restoreDefaults().rows)
        assertEquals(template.symbolRows, back.restoreDefaults().symbolRows)
    }

    @Test fun oldLayoutWithoutStyle_infersSimpleFromSixRows() {
        val old = KeyLayoutCodec.encode(asciiKeyLayout(compact = true, hasFaceKey = true)).toMutableMap()
        old.remove("style")
        assertEquals(KeyboardStyle.COMPACT.id, KeyLayoutCodec.decode(old)?.styleId)
    }

    // ---- 表せることの網羅 ------------------------------------------------------------------

    /** ⭐ 分割は**向き自由で深さ 2**（矢印の田の字）。取り分もそのまま戻ること。 */
    @Test fun nestedSplit_keepsDirectionAndRatios() {
        val arrows = SlotContent.Split(
            SplitDir.VERTICAL,
            listOf(
                SlotPart(
                    SlotContent.Split(
                        SplitDir.HORIZONTAL,
                        listOf(
                            SlotPart(SlotContent.Single(KeyDef.named("⇱", NamedKey.HOME))),
                            SlotPart(SlotContent.Single(KeyDef.named("⇲", NamedKey.END))),
                        )
                    ),
                    ratio = 2f,
                ),
                SlotPart(
                    SlotContent.Split(
                        SplitDir.HORIZONTAL,
                        listOf(
                            SlotPart(SlotContent.Single(KeyDef.named("←", NamedKey.LEFT))),
                            SlotPart(SlotContent.Single(KeyDef.named("→", NamedKey.RIGHT))),
                        )
                    ),
                    ratio = 1f,
                ),
            )
        )
        val original = KeyLayout("split", "分割", listOf(KeyRow(listOf(KeySlot(arrows)))))
        val back = roundTrip(original)
        assertEquals(original, back)
        assertTrue(back.validate().isEmpty())
    }

    /** アクションの種類が 1 つも落ちないこと（列も含む）。 */
    @Test fun everyActionKind_survivesTheRoundTrip() {
        val key = KeyDef(
            label = "全部",
            bindings = mapOf(
                KeyGesture.TAP to listOf(
                    // ⭐ 1 キーに**アクションの列**を書ける（`Ctrl+A` → `d`）。
                    KeyAction.Chord(setOf(ModKey.CTRL), text = "a"),
                    KeyAction.Text("d"),
                ),
                KeyGesture.UP to listOf(KeyAction.Named(NamedKey.PAGE_UP)),
                KeyGesture.DOWN to listOf(KeyAction.Chord(setOf(ModKey.ALT, ModKey.SHIFT), key = NamedKey.F5)),
                KeyGesture.LEFT to listOf(KeyAction.Raw(byteArrayOf(0x1b, 0x5b, 0x41))),
                KeyGesture.RIGHT to listOf(KeyAction.Modifier(ModKey.CTRL)),
                KeyGesture.LONG_PRESS to listOf(KeyAction.Layer("fn", sticky = true)),
                KeyGesture.DOUBLE_TAP to listOf(
                    KeyAction.App(AppAction.SETTINGS),
                    KeyAction.Snippet("s1"),
                    KeyAction.Macro("朝の支度"),
                ),
            ),
        )
        val original = KeyLayout("act", "割り当て", listOf(KeyRow(listOf(KeySlot.of(key)))))
        assertEquals(original, roundTrip(original))
    }

    /** ⚠ 生バイトは 16 進で運ぶ。0x00 や 0xff のような端でも壊れないこと。 */
    @Test fun rawBytes_surviveEveryByteValue() {
        val bytes = ByteArray(256) { it.toByte() }
        val key = KeyDef(bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Raw(bytes))))
        val back = roundTrip(KeyLayout("raw", "raw", listOf(KeyRow(listOf(KeySlot.of(key))))))
        val got = (back.rows[0].slots[0].content as SlotContent.Single)
            .key.actionsFor(KeyGesture.TAP).first() as KeyAction.Raw
        assertTrue(bytes.contentEquals(got.bytes))
    }

    /** キー 1 つが持つ「手触り」のフィールドが全部戻ること（0.8.407 で足した差）。 */
    @Test fun keyFeelFields_surviveTheRoundTrip() {
        val key = KeyDef(
            label = "⌫",
            bindings = mapOf(KeyGesture.TAP to listOf(KeyAction.Named(NamedKey.BACKSPACE))),
            hintGestures = setOf(KeyGesture.UP, KeyGesture.LEFT),
            repeatable = true,
            repeatInitialMs = 500L,
            repeatIntervalMs = 40L,
            pressFeedback = false,
            flickOnRelease = false,
            highlighted = true,
            labelTone = LabelTone.SECONDARY,
            fontRole = KeyFontRole.MAIN,
            layers = mapOf(KeyLayout.LAYER_SHIFT to KeyDef.text("A", "A")),
        )
        assertEquals(key, singleKeyOf(roundTrip(oneKeyLayout(key))))
    }

    // ---- 壊れたものを渡されたとき ------------------------------------------------------------

    /** ⚠ **知らないアクション種別は 1 つ落として読み進む**（レイアウトごと消さない）。 */
    @Test fun unknownAction_isDroppedButTheRestIsKept() {
        val node = mapOf(
            "id" to "x", "name" to "x",
            "rows" to listOf(
                mapOf(
                    "slots" to listOf(
                        mapOf(
                            "key" to mapOf(
                                "label" to "a",
                                "bind" to mapOf(
                                    "tap" to listOf(
                                        mapOf("t" to "teleport", "s" to "?"),   // 知らない種別
                                        mapOf("t" to "text", "s" to "a"),
                                    ),
                                    "wiggle" to listOf(mapOf("t" to "text", "s" to "z")), // 知らない向き
                                ),
                            )
                        )
                    )
                )
            ),
        )
        val layout = KeyLayoutCodec.decode(node)
        assertNotNull(layout)
        val key = singleKeyOf(layout!!)
        assertEquals(listOf(KeyAction.Text("a")), key.actionsFor(KeyGesture.TAP))
        assertEquals(1, key.bindings.size)
    }

    /** 段が 1 つも読めなければ null（呼出し側が「1 件飛ばす」を選べる）。 */
    @Test fun emptyRows_readsAsNull() {
        assertNull(KeyLayoutCodec.decode(mapOf("id" to "x", "name" to "x", "rows" to emptyList<Any>())))
        assertNull(KeyLayoutCodec.decode(mapOf("name" to "id が無い", "rows" to listOf<Any>())))
    }

    /**
     * ⚠ 割った先が 1 つしか読めなかった枠は**捨てる**。片側だけの分割を描くと、
     * 消えた側の面積に何も無い（押しても反応しない）枠ができる。
     */
    @Test fun halfBrokenSplit_isDropped() {
        val node = mapOf(
            "id" to "x", "name" to "x",
            "rows" to listOf(
                mapOf(
                    "slots" to listOf(
                        mapOf("split" to "h", "parts" to listOf(mapOf("key" to mapOf("label" to "←")), mapOf("??" to 1))),
                        mapOf("key" to mapOf("label" to "b")),
                    )
                )
            ),
        )
        val layout = KeyLayoutCodec.decode(node)
        assertNotNull(layout)
        assertEquals(1, layout!!.rows[0].slots.size)
        assertEquals("b", singleKeyOf(layout).label)
    }

    /** 幅が書いていない枠は [KeyWidth.Auto]（既定を書かない方針の裏返し）。 */
    @Test fun missingWidth_meansAuto() {
        val node = mapOf(
            "id" to "x", "name" to "x",
            "rows" to listOf(mapOf("slots" to listOf(mapOf("key" to mapOf("label" to "a"))))),
        )
        assertEquals(KeyWidth.Auto, KeyLayoutCodec.decode(node)!!.rows[0].slots[0].width)
    }

    /** ⚠ 数は Int でも Double でも来る（JSON を通ると `1` は Integer）。 */
    @Test fun numbers_areReadAsEitherIntOrDouble() {
        val node = mapOf(
            "id" to "x", "name" to "x",
            "rows" to listOf(
                mapOf(
                    "slots" to listOf(
                        mapOf("w" to 2, "key" to mapOf("label" to "a", "repeatInitialMs" to 500)),
                        mapOf("w" to 1.4, "key" to mapOf("label" to "b")),
                    )
                )
            ),
        )
        val row = KeyLayoutCodec.decode(node)!!.rows[0]
        assertEquals(KeyWidth.Fixed(2f), row.slots[0].width)
        assertEquals(KeyWidth.Fixed(1.4f), row.slots[1].width)
        assertEquals(500L, (row.slots[0].content as SlotContent.Single).key.repeatInitialMs)
    }

    @Test fun nonFiniteRatios_fallBackToSafeDefaults() {
        val node = mapOf(
            "id" to "x", "name" to "x",
            "rows" to listOf(
                mapOf(
                    "slots" to listOf(
                        mapOf(
                            "w" to Double.MAX_VALUE,
                            "split" to "h",
                            "parts" to listOf(
                                mapOf("r" to Double.MAX_VALUE, "key" to mapOf("label" to "a")),
                                mapOf("r" to Double.NaN, "key" to mapOf("label" to "b")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val slot = KeyLayoutCodec.decode(node)!!.rows[0].slots[0]
        assertEquals(KeyWidth.Auto, slot.width)
        val parts = (slot.content as SlotContent.Split).parts
        assertEquals(listOf(1f, 1f), parts.map { it.ratio })
    }

    /** 書き出したものに既定値が入っていないこと（AI に読み書きさせるので短さが機能のうち）。 */
    @Test fun defaults_areNotWritten() {
        val encoded = KeyLayoutCodec.encode(oneKeyLayout(KeyDef.text("a")))
        @Suppress("UNCHECKED_CAST")
        val slot = ((encoded["rows"] as List<Map<String, Any?>>)[0]["slots"] as List<Map<String, Any?>>)[0]
        @Suppress("UNCHECKED_CAST")
        val key = slot["key"] as Map<String, Any?>
        assertEquals(setOf("key"), slot.keys)              // 幅 Auto は書かない
        assertEquals(setOf("label", "bind", "font"), key.keys)  // repeat / press などは書かない
    }

    // ---- 小道具 ------------------------------------------------------------------------------

    private fun oneKeyLayout(key: KeyDef) =
        KeyLayout("one", "one", listOf(KeyRow(listOf(KeySlot.of(key)))))

    private fun singleKeyOf(layout: KeyLayout): KeyDef =
        (layout.rows[0].slots[0].content as SlotContent.Single).key
}
