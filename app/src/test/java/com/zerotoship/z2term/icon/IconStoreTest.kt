package com.zerotoship.z2term.icon

import com.zerotoship.z2term.tile.TileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 書いたドット絵の読み取り ([IconStore.parse]) の検証。
 *
 * **描いたものと出るものが一致すること**がこの機能の値打ちのほぼ全部で、ずれても端末の上では
 * 気付けない (出来上がりはステータスバーの 24px なので、1 点ずれても「そういう絵」に見える)。
 * Android 非依存の判断部分だけ切り出して押さえる ([TileStore] と同じ作法)。
 */
class IconStoreTest {

    /** 塗り 1 点。中央へ寄るので、書いた位置ではなく真ん中に入る。 */
    @Test
    fun singleDotLandsInTheMiddle() {
        val m = IconStore.parse("#")
        val g = IconStore.GRID
        assertEquals(1, m.count { it })
        assertTrue(m[(g / 2) * g + g / 2])
    }

    /** 空きマスに使える字と塗りに使える字。**塗りの字は決め打ちにしない**。 */
    @Test
    fun anythingThatIsNotABlankCharacterIsInk() {
        assertEquals(3, IconStore.parse("#*X").count { it })
        // 空きマスの字だけを並べても「1 点も塗られていない」で弾かれる。
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse(". 0-_") }
    }

    /** 余白は無視して中央へ置き直す。行を [IconStore.GRID] に合わせなくてよい。 */
    @Test
    fun surroundingBlanksDoNotMoveTheDrawing() {
        val bare = IconStore.parse("##\n##")
        val padded = IconStore.parse("\n\n\n......\n...##..\n...##..\n......\n\n")
        assertEquals(IconStore.toText(bare), IconStore.toText(padded))
    }

    /**
     * ⚠ 大きすぎる絵は**弾く**。黙って切り詰めると、描いた本人にだけ端の欠けたアイコンが届く。
     */
    @Test
    fun tooBigIsRejected() {
        val wide = "#".repeat(IconStore.GRID + 1)
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse(wide) }
        val tall = (0..IconStore.GRID).joinToString("\n") { "#" }
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse(tall) }
    }

    /** 1 点も塗られていない絵は弾く (押しても何も見えないアイコンになる)。 */
    @Test
    fun emptyDrawingIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse("....\n....") }
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse("") }
    }

    /** 正規形は [IconStore.GRID] 行 x [IconStore.GRID] 桁。`z2-icon edit` がこれを開く。 */
    @Test
    fun normalisedTextIsAlwaysTheFullGrid() {
        val lines = IconStore.toText(IconStore.parse("#")).split("\n")
        assertEquals(IconStore.GRID, lines.size)
        assertTrue(lines.all { it.length == IconStore.GRID })
        assertEquals(IconStore.GRID, IconStore.emptyText().split("\n").size)
    }

    /** 正規形を読み直しても同じ絵になる (`edit` は書き出した絵をそのまま読み戻す)。 */
    @Test
    fun normalisedTextSurvivesARoundTrip() {
        val once = IconStore.toText(IconStore.parse("#.#\n.#.\n#.#"))
        assertEquals(once, IconStore.toText(IconStore.parse(once)))
    }

    /** プレビューは上下 2 行を 1 文字に畳む (端末の文字が縦長なので、そのままだと間延びする)。 */
    @Test
    fun previewFoldsTwoRowsIntoOne() {
        val lines = IconStore.preview(IconStore.parse("#")).split("\n")
        assertEquals(IconStore.GRID / 2, lines.size)
        assertTrue(lines.all { it.length == IconStore.GRID })
        // 1 点だけなら、上半分か下半分のどちらかしか塗られていない。
        assertTrue(IconStore.preview(IconStore.parse("#")).any { it == '▀' || it == '▄' })
    }

    /** 対象は `notify` と枠番号。`z2-tile` が枠を番号で呼ぶので、こちらも番号で受ける。 */
    @Test
    fun targetsAreNotifyAndSlotNumbers() {
        assertEquals(IconStore.TARGET_NOTIFY, IconStore.normalizeTarget("notify"))
        assertEquals(IconStore.tileTarget(3), IconStore.normalizeTarget("3"))
        // `tile3` と書かれても通す (打ち間違いにしない)。
        assertEquals(IconStore.tileTarget(3), IconStore.normalizeTarget("tile3"))
        assertNull(IconStore.normalizeTarget("0"))
        assertNull(IconStore.normalizeTarget("${TileStore.COUNT + 1}"))
        assertNull(IconStore.normalizeTarget("tail"))
    }

    /**
     * 同梱サンプルは**全部そのまま読める**こと。
     *
     * 壊れた絵を配ると、選んだ人には `z2-icon pick` が理由の分からないエラーで終わるだけになる
     * (自分で描いた絵なら直せるが、同梱のものは直しようがない)。
     */
    @Test
    fun everySampleIsAValidDrawing() {
        assertTrue(IconSamples.names().isNotEmpty())
        IconSamples.ALL.forEach { (name, art) ->
            val m = IconStore.parse(art)  // 読めなければここで落ちる
            assertTrue("$name に塗りが無い", m.any { it })
        }
    }

    /** 枠番号は往復する (`z2-icon list` が枠を番号で出すのに使う)。 */
    @Test
    fun slotNumberRoundTrips() {
        assertEquals(7, IconStore.slotOf(IconStore.tileTarget(7)))
        assertNull(IconStore.slotOf(IconStore.TARGET_NOTIFY))
        assertEquals(TileStore.COUNT + 1, IconStore.targets().size)
    }
}
