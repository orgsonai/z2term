package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomKaomojiStoreTest {

    @Test
    fun `行頭の空白は残し、行末の空白と前後の空行は落とす`() {
        val raw = "\n\n  /\\_/\\  \n ( o.o )\t\n\n"
        assertEquals("  /\\_/\\\n ( o.o )", CustomKaomojiStore.normalize(raw))
    }

    @Test
    fun `改行は LF にそろえる`() {
        assertEquals("a\nb\nc", CustomKaomojiStore.normalize("a\r\nb\rc"))
    }

    @Test
    fun `途中の空行は絵の一部として残す`() {
        assertEquals("a\n\nb", CustomKaomojiStore.normalize("a\n\nb"))
    }

    @Test
    fun `途中のタブは空白 4 つにする`() {
        assertEquals("a    b", CustomKaomojiStore.normalize("a\tb"))
    }

    @Test
    fun `空白だけなら空になる`() {
        assertTrue(CustomKaomojiStore.normalize(" \n\t\n ").isEmpty())
    }

    @Test
    fun `同梱の複数行 AA は足せる大きさに収まっている`() {
        val multi = KaomojiCatalog.ALL.last().items
        assertTrue(multi.size >= 30)
        multi.forEach {
            assertEquals(it, CustomKaomojiStore.normalize(it))
            assertTrue(it.count { c -> c == '\n' } < CustomKaomojiStore.MAX_LINES)
        }
    }
}
