package com.zerotoship.z2term.icon

import com.zerotoship.z2term.proot.z2MacroSamples
import com.zerotoship.z2term.tile.TileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val g = IconStore.gridOf(m)
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

    /** 余白は無視して中央へ置き直す。行を一辺に合わせなくてよい。 */
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
        val max = IconStore.GRIDS.last()
        val wide = "#".repeat(max + 1)
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse(wide) }
        val tall = (0..max).joinToString("\n") { "#" }
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse(tall) }
    }

    /**
     * 一辺は**塗った範囲が収まる最小**を選ぶ。
     *
     * ⚠ 大きい一辺へ勝手に移さないこと。一辺を上げても絵は大きくならないので、24 の絵を 64 の
     * マス目へ入れると**タイルの中で小さくなる**だけになる。
     */
    @Test
    fun gridIsTheSmallestOneThatFits() {
        assertEquals(24, IconStore.gridOf(IconStore.parse("#".repeat(24))))
        assertEquals(48, IconStore.gridOf(IconStore.parse("#".repeat(25))))
        assertEquals(64, IconStore.gridOf(IconStore.parse("#".repeat(49))))
        // 縦だけ大きい絵でも同じ (幅と高さの大きい方で決まる)。
        assertEquals(48, IconStore.gridOf(IconStore.parse((1..30).joinToString("\n") { "#" })))
        // 明示すればそれに従う (小さく描いた絵を細かいマス目のまま直したいとき)。
        assertEquals(64, IconStore.gridOf(IconStore.parse("#", grid = 64)))
    }

    /**
     * [IconStore.zoomText] は**見た目を変えずに一辺だけ上げる** (`z2-icon scale`)。
     *
     * 見た目 = **一辺に対して絵がどれだけを占めるか**。ここが変わると、細かくしたつもりが
     * タイルの中で小さくなった (あるいは太った) ということになる。
     */
    @Test
    fun scalingKeepsTheDrawingAndOnlyRaisesTheGrid() {
        fun span(m: BooleanArray): Int {
            val g = IconStore.gridOf(m)
            val xs = (0 until g).filter { x -> (0 until g).any { y -> m[y * g + x] } }
            return xs.last() - xs.first() + 1
        }
        // マス目いっぱいの絵で見る (余白があると parse が小さい一辺へ落とすため)。
        val small = IconStore.parse("#".repeat(24))
        val big = IconStore.parse(IconStore.zoomText(small, 48), grid = 48)
        assertEquals(24, IconStore.gridOf(small))
        assertEquals(48, IconStore.gridOf(big))
        // 一辺に対する絵の幅の比が変わらない = タイルに出る大きさが変わらない。
        assertEquals(span(small) * 2, span(big))
        // 整数倍でない敷き直しでも比は保たれる (24 -> 64)。
        val finer = IconStore.parse(IconStore.zoomText(small, 64), grid = 64)
        assertEquals(64, IconStore.gridOf(finer))
        assertEquals(64, span(finer))
        // 用意していない一辺は弾く。
        assertThrows(IllegalArgumentException::class.java) { IconStore.zoomText(small, 32) }
    }

    /**
     * 敷き直しは**斜めの階段を均す** (Scale2x)。
     *
     * ⚠ 点をただ 2x2 に太らせるだけだと、細かいマス目へ移しても**階段は同じ大きさのまま残る** —
     * それでは「かくかくして見える」ことが何も変わらない。
     */
    @Test
    fun scalingSmoothsDiagonalSteps() {
        val diag = IconStore.parse(".#\n#.")
        val big = IconStore.parse(IconStore.zoomText(diag, 48), grid = 48)
        // 太らせただけなら 4 倍ちょうど。斜めの隙間が埋まるぶん、それより多くなる。
        assertTrue(
            "${big.count { it }} は ${diag.count { it } * 4} より多くない",
            big.count { it } > diag.count { it } * 4,
        )
    }

    /**
     * ⚠ **平らなところは太るだけ**。角が 4 つ丸まる以外は形が変わらないことを固定する —
     * 均しの条件が緩むと、**描いた絵が勝手に別の形になる**。
     */
    @Test
    fun scalingLeavesFlatAreasAlone() {
        val block = IconStore.parse((1..6).joinToString("\n") { "######" })
        val big = IconStore.parse(IconStore.zoomText(block, 48), grid = 48)
        assertEquals(block.count { it } * 4 - 4, big.count { it })
    }

    /**
     * ⚠ **細かい一辺ほど、出すときも細かいこと**。
     *
     * ここが逆転すると `z2-icon scale` で細かくした人が損をする — 実際 0.8.382 では 64 の絵だけ
     * 均されず、**24 のまま置いた方が滑らかに出る**状態になっていた (利用者の指摘で発覚)。
     */
    @Test
    fun finerGridsNeverComeOutRougher() {
        val smoothed = IconStore.GRIDS.map { IconStore.smoothedGrid(it) }
        assertEquals(IconStore.GRIDS.sorted(), IconStore.GRIDS)
        assertEquals(smoothed.sorted(), smoothed)
        // どの一辺も、2 倍を繰り返して上限へ届くこと (届かない一辺は均されないまま残る)。
        IconStore.GRIDS.forEach { g ->
            val s = IconStore.smoothedGrid(g)
            assertTrue("$g は均しても $s どまり", s * 2 > IconStore.SMOOTH_GRID)
            assertEquals("$g -> $s は 2 のべき倍ではない", 0, s % g)
        }
    }

    /** 1 点も塗られていない絵は弾く (押しても何も見えないアイコンになる)。 */
    @Test
    fun emptyDrawingIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse("....\n....") }
        assertThrows(IllegalArgumentException::class.java) { IconStore.parse("") }
    }

    /** 正規形は一辺の行数 x 桁数。`z2-icon edit` がこれを開く (行数がそのまま一辺)。 */
    @Test
    fun normalisedTextIsAlwaysTheFullGrid() {
        IconStore.GRIDS.forEach { g ->
            val lines = IconStore.toText(IconStore.parse("#", grid = g)).split("\n")
            assertEquals(g, lines.size)
            assertTrue(lines.all { it.length == g })
            assertEquals(g, IconStore.emptyText(g).split("\n").size)
        }
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
        val m = IconStore.parse("#")
        val g = IconStore.gridOf(m)
        val lines = IconStore.preview(m).split("\n")
        assertEquals(g / 2, lines.size)
        assertTrue(lines.all { it.length == g })
        // 1 点だけなら、上半分か下半分のどちらかしか塗られていない。
        assertTrue(IconStore.preview(m).any { it == '▀' || it == '▄' })
    }

    /**
     * プレビューは**入りきるなら畳まない**。
     *
     * ⚠ 48 の絵を機械的に 24 桁へ畳むと、均した斜めが元の階段に戻って見え、
     * 「敷き直しても何も変わらない」ように映る (利用者の報告)。
     */
    @Test
    fun previewKeepsTheGridWhenTheScreenIsWideEnough() {
        val wide = IconStore.parse("#".repeat(48), grid = 48)
        assertEquals(48, IconStore.previewGrid(48, 48))
        assertEquals(48, IconStore.preview(wide, cols = 60).split("\n")[0].length)
        // 狭い画面では畳む (折り返すと形が分からなくなる方が困る)。
        assertEquals(24, IconStore.previewGrid(48, 30))
        assertEquals(24, IconStore.preview(wide, cols = 30).split("\n")[0].length)
        // 幅が分からないとき (0) は既定の 32 桁まで。
        assertEquals(24, IconStore.previewGrid(48, 0))
    }

    /**
     * 細かい絵のプレビューは**桁を畳んで**出す。
     *
     * ⚠ 64 桁のまま出すと携帯の画面幅で折り返し、形を確かめるという目的そのものが果たせない。
     */
    @Test
    fun wideDrawingsAreFoldedToFitTheScreen() {
        IconStore.GRIDS.forEach { g ->
            val lines = IconStore.preview(IconStore.parse("#".repeat(g), grid = g)).split("\n")
            assertTrue("$g マスのプレビューが ${lines[0].length} 桁ある", lines[0].length <= 32)
            assertEquals(lines[0].length / 2, lines.size)
        }
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

    /**
     * **同梱マクロは全部、タイルに置いた時点で絵が付く**こと。
     *
     * `z2-macro install` で入るものが「自分では絵を選べない人が最初に置くもの」なので、
     * ここが 1 本でも抜けると、揃えて並べたタイルの中にひとつだけ既定のアイコンが混ざる。
     */
    @Test
    fun everyBundledMacroGetsAnIcon() {
        val macros = z2MacroSamples("ja").keys
        assertTrue(macros.isNotEmpty())
        macros.forEach { name ->
            val guessed = IconSamples.guess(name)
            assertNotNull("$name に当たる絵が無い", guessed)
            assertNotNull("$name → $guessed という絵が無い", IconSamples.get(guessed!!))
        }
    }

    /** 何を当てるかを固定する。⚠ 語の並び順で結果が変わるので、代表例を押さえておく。 */
    @Test
    fun theGuessFollowsTheNarrowerWordFirst() {
        assertEquals("clock", IconSamples.guess("remind.sh ask"))
        assertEquals("moon", IconSamples.guess("z2-screen keepon 1h"))
        assertEquals("bolt", IconSamples.guess("z2-torch on"))
        assertEquals("sync", IconSamples.guess("backup.sh"))
        // ⚠ battery が alert より、unknown が call より先。狭い意味の語を上に置いてある。
        assertEquals("battery", IconSamples.guess("battery-alert.sh"))
        assertEquals("warning", IconSamples.guess("unknown-call.sh"))
        // 当たらないものは当てない (意味の合わない絵が黙って付く方が分かりにくい)。
        assertNull(IconSamples.guess("ls -la"))
        assertNull(IconSamples.guess("./run"))
    }

    /** ⚠ 短い語で誤爆しないこと (`log` が `login` に当たるような取りこぼしの逆)。 */
    @Test
    fun theGuessDoesNotFireOnUnrelatedWords() {
        assertNull(IconSamples.guess("latest.sh"))
        assertNull(IconSamples.guess("direct.sh"))
        assertNull(IconSamples.guess("login.sh"))
    }

    /** 枠番号は往復する (`z2-icon list` が枠を番号で出すのに使う)。 */
    @Test
    fun slotNumberRoundTrips() {
        assertEquals(7, IconStore.slotOf(IconStore.tileTarget(7)))
        assertNull(IconStore.slotOf(IconStore.TARGET_NOTIFY))
        assertEquals(TileStore.COUNT + 1, IconStore.targets().size)
    }

    /**
     * 自分の絵に付けられる名前 (`z2-icon save`)。
     *
     * ⚠ **数字だけの名前を通さない**こと。一覧は番号でも名前でも選べるので、`3` という名前を
     * 作れてしまうと「3 番」と「3 という名前」のどちらを指すのか決められなくなる。
     * ⚠ 空白を含む名前も通さない — 一覧は TSV なので列がずれる。
     */
    @Test
    fun sampleNamesRejectNumbersAndWhitespace() {
        assertEquals("my-face", IconStore.normalizeSampleName("  my-face "))
        assertEquals("わたしの顔", IconStore.normalizeSampleName("わたしの顔"))
        assertNull(IconStore.normalizeSampleName("3"))
        assertNull(IconStore.normalizeSampleName("my face"))
        assertNull(IconStore.normalizeSampleName("a\tb"))
        assertNull(IconStore.normalizeSampleName(""))
        assertNull(IconStore.normalizeSampleName("x".repeat(25)))
    }

    /**
     * ⚠ **同梱サンプルに同じ絵が 2 つ無い**こと。
     *
     * `z2-icon list` は入っている絵から名前を逆に引く ([IconStore.nameOf])。同じ形の絵が
     * 2 つあると、入れたときと違う名前が出て「別の絵に化けた」ように見える。
     */
    @Test
    fun noTwoSamplesAreTheSameDrawing() {
        val seen = HashMap<String, String>()
        IconSamples.ALL.forEach { (name, art) ->
            val canonical = IconStore.toText(IconStore.parse(art))
            val previous = seen.put(canonical, name)
            assertNull("$previous と $name が同じ絵", previous)
        }
    }
}
