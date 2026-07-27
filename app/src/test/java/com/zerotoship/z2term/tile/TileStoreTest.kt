package com.zerotoship.z2term.tile

import org.junit.Assert.assertEquals
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
}
