package com.zerotoship.z2term.tile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * タイルに出す名前の決めかた ([TileStore.labelFor]) の検証。
 *
 * **タイルは名前が機能そのもの**で、押す前に分かるのはその文字列しかない。ここが崩れると
 * 「4 つ並べたのにどれが何か分からない」になり、クイック設定という置き場の値打ちごと消える。
 * Android 非依存の判断部分だけ切り出して押さえる ([com.zerotoship.z2term.widget.WidgetStore] と同じ作法)。
 */
class TileStoreTest {

    @Test
    fun macroDropsTheExtension() {
        assertEquals("backup", TileStore.labelFor("backup.sh", explicit = ""))
    }

    @Test
    fun commandUsesItsFirstWord() {
        // コマンド全文を出すと必ず切れるので、先頭の語だけを手掛かりにする。
        assertEquals("z2-screen", TileStore.labelFor("z2-screen keepon 1h", explicit = ""))
        assertEquals("sshd", TileStore.labelFor("sshd --lan", explicit = ""))
    }

    @Test
    fun explicitLabelWins() {
        assertEquals("消灯しない", TileStore.labelFor("z2-screen keepon 1h", explicit = "消灯しない"))
    }

    /** 長すぎる名前は切り詰める (クイック設定は狭く、溢れると読めなくなる)。 */
    @Test
    fun longLabelIsShortened()  {
        val label = TileStore.labelFor("x", explicit = "0123456789abcdef")
        assertEquals(TileStore.MAX_LABEL_CHARS, label.length)
        assertEquals("0123456789a…", label)
    }

    /** ちょうど上限の長さは切らない (境界で 1 文字削るのは損なだけ)。 */
    @Test
    fun labelAtTheLimitIsKept() {
        val exact = "a".repeat(TileStore.MAX_LABEL_CHARS)
        assertEquals(exact, TileStore.labelFor("x", explicit = exact))
    }

    /**
     * 割り当ての無い枠はクイック設定の一覧に出さない。ただし**枠 1 だけは常に出す**。
     * ここが逆になると、一度も z2-tile を打っていない人の一覧が z2term で埋まるか (全部出す)、
     * 逆にタイルという置き場があること自体に気付けなくなる (全部隠す)。
     */
    @Test
    fun onlyAssignedSlotsAreListed() {
        assertTrue(TileStore.shouldEnable(1, assigned = false))
        assertTrue(TileStore.shouldEnable(1, assigned = true))
        assertFalse(TileStore.shouldEnable(2, assigned = false))
        assertTrue(TileStore.shouldEnable(2, assigned = true))
        assertFalse(TileStore.shouldEnable(TileStore.COUNT, assigned = false))
    }

    /**
     * `z2-screen keepon <時間>` の枠だけ、緑が「掛かっている間」を指す。
     * ⚠ `keepon off` と `status` を巻き込むと「押すと緑が消えるだけのタイル」になる。
     */
    @Test
    fun onlyKeepOnWithADurationTracksItsState() {
        assertTrue(TileStore.isScreenKeepOn("z2-screen keepon 1h"))
        assertTrue(TileStore.isScreenKeepOn("  z2-screen   keepon   30m  "))
        assertFalse(TileStore.isScreenKeepOn("z2-screen keepon off"))
        assertFalse(TileStore.isScreenKeepOn("z2-screen status"))
        assertFalse(TileStore.isScreenKeepOn("z2-screen"))
        // 別のコマンドが前に付いていたら、それはもう z2-screen の枠ではない (走らせるだけ)。
        assertFalse(TileStore.isScreenKeepOn("sh -c 'z2-screen keepon 1h'"))
    }

    /**
     * 残り時間は**切り上げ**。切り捨てると `keepon 1h` の直後に「残り 59 分」と出て、
     * 頼んだ時間より短く見える。
     */
    @Test
    fun remainingRoundsUp() {
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.HOURS, 1), TileStore.remaining(3600))
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.HOURS, 2), TileStore.remaining(3601))
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.MINUTES, 60), TileStore.remaining(3599))
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.MINUTES, 1), TileStore.remaining(60))
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.SECONDS, 59), TileStore.remaining(59))
        // 期限を過ぎた値で呼ばれても「残り -3 秒」を出さない。
        assertEquals(TileStore.Remaining(TileStore.RemainUnit.SECONDS, 0), TileStore.remaining(-3))
    }
}
